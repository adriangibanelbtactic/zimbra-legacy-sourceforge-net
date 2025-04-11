/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.imap;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.net.Socket;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AccessManager;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.imap.ImapFlagCache.ImapFlag;
import com.zimbra.cs.imap.ImapMessage.ImapMessageSet;
import com.zimbra.cs.imap.ImapSession.EnabledHack;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.queryparser.ParseException;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.SearchFolder;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.operation.AlterTagOperation;
import com.zimbra.cs.operation.CreateFolderOperation;
import com.zimbra.cs.operation.CreateTagOperation;
import com.zimbra.cs.operation.DeleteOperation;
import com.zimbra.cs.operation.GetFolderOperation;
import com.zimbra.cs.operation.GetItemOperation;
import com.zimbra.cs.operation.SearchOperation;
import com.zimbra.cs.operation.SetTagsOperation;
import com.zimbra.cs.operation.Operation.Requester;
import com.zimbra.cs.service.util.ThreadLocalData;
import com.zimbra.cs.session.SessionCache;
import com.zimbra.cs.stats.StatsFile;
import com.zimbra.cs.stats.ZimbraPerf;
import com.zimbra.cs.tcpserver.ProtocolHandler;
import com.zimbra.cs.tcpserver.TcpServerInputStream;
import com.zimbra.cs.util.BuildInfo;
import com.zimbra.cs.util.Config;

/**
 * @author dkarp
 */
public class ImapHandler extends ProtocolHandler implements ImapSessionHandler {

    private abstract class Authenticator {
        String mTag;
        boolean mContinue = true;
        Authenticator(String tag)  { mTag = tag; }
        abstract boolean handle(byte[] data) throws IOException;
    }
    private class AuthPlain extends Authenticator {
        AuthPlain(String tag)  { super(tag); }
        boolean handle(byte[] response) throws IOException {
            // RFC 2595 6: "Non-US-ASCII characters are permitted as long as they are
            //              represented in UTF-8 [UTF-8]."
            String message = new String(response, "utf-8");

            // RFC 2595 6: "The client sends the authorization identity (identity to
            //              login as), followed by a US-ASCII NUL character, followed by the
            //              authentication identity (identity whose password will be used),
            //              followed by a US-ASCII NUL character, followed by the clear-text
            //              password.  The client may leave the authorization identity empty to
            //              indicate that it is the same as the authentication identity."
            int nul1 = message.indexOf('\0'), nul2 = message.indexOf('\0', nul1 + 1);
            if (nul1 == -1 || nul2 == -1) {
                sendNO(mTag, "malformed authentication message");
                return true;
            }
            String authorizeId = message.substring(0, nul1);
            String authenticateId = message.substring(nul1 + 1, nul2);
            String password = message.substring(nul2 + 1);
            if (authorizeId.equals(""))
                authorizeId = authenticateId;

            mContinue = login(authorizeId, authenticateId, password, "AUTHENTICATE", mTag);
            return true;
        }
    }

    private static final long MAXIMUM_IDLE_PROCESSING_MILLIS = 15 * Constants.MILLIS_PER_SECOND;

    static final char[] LINE_SEPARATOR       = { '\r', '\n' };
    static final byte[] LINE_SEPARATOR_BYTES = { '\r', '\n' };

    private TcpServerInputStream mInputStream;
    private OutputStream         mOutputStream;

    private DateFormat mTimeFormat   = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", Locale.US);
    private DateFormat mDateFormat   = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
    private DateFormat mZimbraFormat = DateFormat.getDateInstance(DateFormat.SHORT);

    private String        mRemoteAddress;
    private ImapServer    mServer;
    private Authenticator mAuthenticator;
    private ImapSession   mSession;
    private Mailbox       mMailbox;
    private ImapRequest   mIncompleteRequest = null;
    private String        mLastCommand;
    private boolean       mStartedTLS;
    private boolean       mGoodbyeSent;

    public ImapHandler(ImapServer server) {
        super(server);
        mServer = server;
    }

    public void dumpState(Writer w) {
    	try {
    		w.write("\n\tImapHandler(Thread-Per-Connection) " + this);
    	} catch(IOException e) {};
    }

    public DateFormat getTimeFormat()   { return mTimeFormat; }
    public DateFormat getDateFormat()   { return mDateFormat; }
    public DateFormat getZimbraFormat() { return mZimbraFormat; }

    protected boolean setupConnection(Socket connection) throws IOException {
        mRemoteAddress = connection.getInetAddress().getHostAddress();
        INFO("connected");

        mInputStream = new TcpServerInputStream(connection.getInputStream());
        mOutputStream = new BufferedOutputStream(connection.getOutputStream());
        mStartedTLS = mServer.isConnectionSSL();

        if (!Config.userServicesEnabled()) {
            ZimbraLog.imap.debug("dropping connection because user services are disabled");
            dropConnection();
            return false;
        }

        sendUntagged(ImapServer.getBanner(), true);
        return true;
    }

    protected boolean authenticate() {
        // we auth with the LOGIN command (and more to come)
        return true;
    }

    protected void setIdle(boolean idle) {
        super.setIdle(idle);
        if (mSession != null)
            mSession.updateAccessTime();
    }

    private static final boolean STOP_PROCESSING = false, CONTINUE_PROCESSING = true;
    
    private static StatsFile STATS_FILE =
        new StatsFile("perf_imap", new String[] { "command" }, true);
    
    protected boolean processCommand() throws IOException {
        ImapRequest req = null;
        boolean keepGoing = CONTINUE_PROCESSING;
        ZimbraLog.clearContext();
        if (ZimbraPerf.isPerfEnabled())
            ThreadLocalData.reset();

        try {
            // FIXME: throw an exception instead?
            if (mInputStream == null)
                return STOP_PROCESSING;

            if (mSession != null)
                ZimbraLog.addAccountNameToContext(mSession.getUsername());
            ZimbraLog.addIpToContext(mRemoteAddress);

            req = mIncompleteRequest;
            if (req == null)
                req = new ImapRequest(mInputStream, this, mSession);
            req.continuation();

            long start = ZimbraPerf.STOPWATCH_IMAP.start();

            // check account status before executing command
            if (mMailbox != null)
                try {
                    Account account = mMailbox.getAccount();
                    if (account == null || !account.getAccountStatus().equals(Provisioning.ACCOUNT_STATUS_ACTIVE)) {
                        ZimbraLog.imap.warn("account missing or not active; dropping connection");
                        return STOP_PROCESSING;
                    }
                } catch (ServiceException e) {
                    ZimbraLog.imap.warn("error checking account status; dropping connection", e);
                    return STOP_PROCESSING;
                }

            if (mAuthenticator != null)
                keepGoing = continueAuthentication(req);
            else
                keepGoing = executeRequest(req, mSession);
            setIdle(false);
            mIncompleteRequest = null;

            if (ZimbraPerf.isPerfEnabled())
                ZimbraPerf.writeStats(STATS_FILE, mLastCommand);
            ZimbraPerf.STOPWATCH_IMAP.stop(start);
        } catch (ImapContinuationException ice) {
            mIncompleteRequest = req.rewind();
            if (ice.sendContinuation)
                sendContinuation();
        } catch (ImapParseException ipe) {
            mIncompleteRequest = null;
            if (ipe.mTag == null)
                sendUntagged("BAD " + ipe.getMessage(), true);
            else if (ipe.mCode != null)
                sendNO(ipe.mTag, '[' + ipe.mCode + "] " + ipe.getMessage());
            else if (ipe.mNO)
                sendNO(ipe.mTag, ipe.getMessage());
            else
                sendBAD(ipe.mTag, ipe.getMessage());
        } catch (ImapTerminatedException ite) {
            mIncompleteRequest = null;
            keepGoing = STOP_PROCESSING;
        } catch (ImapException ie) {
            ZimbraLog.imap.error("unexpected (and uncaught) IMAP exception type", ie);
            mIncompleteRequest = null;
            keepGoing = STOP_PROCESSING;
        } finally {
            ZimbraLog.clearContext();
        }

        return keepGoing;
    }

    private void checkEOF(String tag, ImapRequest req) throws ImapParseException {
        if (!req.eof())
            throw new ImapParseException(tag, "excess characters at end of command");
    }

    private boolean continueAuthentication(ImapRequest req) throws ImapParseException, IOException {
        String tag = mAuthenticator.mTag;
        boolean authFinished = true;

        try {
            // use the tag from the original AUTHENTICATE command
            req.setTag(tag);
    
            // 6.2.2: "If the client wishes to cancel an authentication exchange, it issues a line
            //         consisting of a single "*".  If the server receives such a response, it MUST
            //         reject the AUTHENTICATE command by sending a tagged BAD response."
            if (req.peekChar() == '*') {
                req.skipChar('*');
                if (req.eof())
                    sendBAD(tag, "AUTHENTICATE aborted");
                else
                    sendBAD(tag, "AUTHENTICATE failed; invalid base64 input");
                return CONTINUE_PROCESSING;
            }

            byte[] response = req.readBase64(false);
            checkEOF(tag, req);
            authFinished = mAuthenticator.handle(response);
            return mAuthenticator.mContinue;
        } finally {
            if (authFinished)
                mAuthenticator = null;
        }
    }

    private boolean executeRequest(ImapRequest req, ImapSession session) throws IOException, ImapException {
        if (session != null && session.isIdle())
            return doIDLE(null, IDLE_STOP, req.readATOM().equals("DONE") && req.eof());

        String tag = req.readTag();

        boolean byUID = false;
        req.skipSpace();
        String command = mLastCommand = req.readATOM();
        do {
            switch (command.charAt(0)) {
                case 'A':
                    if (command.equals("AUTHENTICATE")) {
                        req.skipSpace();  String mechanism = req.readATOM();
                        byte[] response = null;
                        if (req.peekChar() == ' ' && extensionEnabled("SASL-IR")) {
                            req.skipSpace();  response = req.readBase64(true);
                        }
                        checkEOF(tag, req);
                        return doAUTHENTICATE(tag, mechanism, response);
                    } else if (command.equals("APPEND")) {
                        List<String> flags = null;  Date date = null;
                        req.skipSpace();  String folder = req.readFolder();
                        req.skipSpace();
                        if (req.peekChar() == '(') {
                            flags = req.readFlags();  req.skipSpace();
                        }
                        if (req.peekChar() == '"') {
                            date = req.readDate(mTimeFormat);  req.skipSpace();
                        }
                        if ((req.peekChar() == 'c' || req.peekChar() == 'C') && extensionEnabled("CATENATE")) {
                            List<Object> parts = new LinkedList<Object>();
                            req.skipAtom("CATENATE");  req.skipSpace();  req.skipChar('(');
                            while (req.peekChar() != ')') {
                                if (!parts.isEmpty())
                                    req.skipSpace();
                                String type = req.readATOM();  req.skipSpace();
                                if (type.equals("TEXT"))      parts.add(req.readLiteral());
                                else if (type.equals("URL"))  parts.add(new ImapURL(tag, mSession, req.readAstring()));
                                else throw new ImapParseException(tag, "unknown CATENATE cat-part: " + type);
                            }
                            req.skipChar(')');  checkEOF(tag, req);
                            return doCATENATE(tag, folder, flags, date, parts);
                        } else {
                            byte[] content = req.readLiteral8();
                            checkEOF(tag, req);
                            return doAPPEND(tag, folder, flags, date, content);
                        }
                    }
                    break;
                case 'C':
                    if (command.equals("CAPABILITY")) {
                        checkEOF(tag, req);
                        return doCAPABILITY(tag);
                    } else if (command.equals("COPY")) {
                        req.skipSpace();  String sequence = req.readSequence();
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doCOPY(tag, sequence, folder, byUID);
                    } else if (command.equals("CLOSE")) {
                        checkEOF(tag, req);
                        return doCLOSE(tag);
                    } else if (command.equals("CREATE")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doCREATE(tag, folder);
                    } else if (command.equals("CHECK")) {
                        checkEOF(tag, req);
                        return doCHECK(tag);
                    }
                    break;
                case 'D':
                    if (command.equals("DELETE")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doDELETE(tag, folder);
                    }
                    break;
                case 'E':
                    if (command.equals("EXPUNGE")) {
                        String sequence = null;
                        if (byUID) {
                            req.skipSpace();  sequence = req.readSequence();
                        }
                        checkEOF(tag, req);
                        return doEXPUNGE(tag, byUID, sequence);
                    } else if (command.equals("EXAMINE")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doEXAMINE(tag, folder);
                    }
                    break;
                case 'F':
                    if (command.equals("FETCH")) {
                        List<ImapPartSpecifier> parts = new ArrayList<ImapPartSpecifier>();
                        req.skipSpace();  String sequence = req.readSequence();
                        req.skipSpace();  int attributes = req.readFetch(parts);
                        checkEOF(tag, req);
                        return doFETCH(tag, sequence, attributes, parts, byUID);
                    }
                    break;
                case 'G':
                    if (command.equals("GETQUOTA") && extensionEnabled("QUOTA")) {
                        req.skipSpace();  String qroot = req.readAstring();
                        checkEOF(tag, req);
                        return doGETQUOTA(tag, qroot);
                    } else if (command.equals("GETQUOTAROOT") && extensionEnabled("QUOTA")) {
                        req.skipSpace();  String path = req.readFolder();
                        checkEOF(tag, req);
                        return doGETQUOTAROOT(tag, path);
                    }
                    break;
                case 'I':
                    if (command.equals("ID") && extensionEnabled("ID")) {
                        req.skipSpace();  Map<String, String> params = req.readParameters(true);
                        checkEOF(tag, req);
                        return doID(tag, params);
                    } else if (command.equals("IDLE") && extensionEnabled("IDLE")) {
                        checkEOF(tag, req);
                        return doIDLE(tag, IDLE_START, true);
                    }
                    break;
                case 'L':
                    if (command.equals("LOGIN")) {
                        req.skipSpace();  String user = req.readAstring();
                        req.skipSpace();  String pass = req.readAstring();
                        checkEOF(tag, req);
                        return doLOGIN(tag, user, pass);
                    } else if (command.equals("LOGOUT")) {
                        checkEOF(tag, req);
                        return doLOGOUT(tag);
                    } else if (command.equals("LIST")) {
                        req.skipSpace();  String base = req.readEscapedFolder();
                        req.skipSpace();  String pattern = req.readFolderPattern();
                        checkEOF(tag, req);
                        return doLIST(tag, base, pattern);
                    } else if (command.equals("LSUB")) {
                        req.skipSpace();  String base = req.readEscapedFolder();
                        req.skipSpace();  String pattern = req.readFolderPattern();
                        checkEOF(tag, req);
                        return doLSUB(tag, base, pattern);
                    }
                    break;
                case 'N':
                    if (command.equals("NOOP")) {
                        checkEOF(tag, req);
                        return doNOOP(tag);
                    } else if (command.equals("NAMESPACE") && extensionEnabled("NAMESPACE")) {
                        checkEOF(tag, req);
                        return doNAMESPACE(tag);
                    }
                    break;
                case 'R':
                    if (command.equals("RENAME")) {
                        req.skipSpace();  String folder = req.readFolder();
                        req.skipSpace();  String name = req.readFolder();
                        checkEOF(tag, req);
                        return doRENAME(tag, folder, name);
                    }
                    break;
                case 'S':
                    if (command.equals("STORE")) {
                        byte operation = STORE_REPLACE;  boolean silent = false;
                        req.skipSpace();  String sequence = req.readSequence();
                        req.skipSpace();
                        switch (req.peekChar()) {
                            case '+':  req.skipChar('+');  operation = STORE_ADD;     break;
                            case '-':  req.skipChar('-');  operation = STORE_REMOVE;  break;
                        }
                        String cmd = req.readATOM();
                        if (cmd.equals("FLAGS.SILENT"))  silent = true;
                        else if (!cmd.equals("FLAGS"))   throw new ImapParseException(tag, "invalid store-att-flags");
                        req.skipSpace();  List<String> flags = req.readFlags();
                        checkEOF(tag, req);
                        return doSTORE(tag, sequence, flags, operation, silent, byUID);
                    } else if (command.equals("SEARCH")) {
                        Integer options = null;
                        req.skipSpace();
                        if ("RETURN".equals(req.peekATOM()) && extensionEnabled("ESEARCH")) {
                            options = 0;
                            req.skipAtom("RETURN");  req.skipSpace();  req.skipChar('(');
                            while (req.peekChar() != ')') {
                                if (options != 0)
                                    req.skipSpace();
                                String option = req.readATOM();
                                if (option.equals("MIN"))         options |= RETURN_MIN;
                                else if (option.equals("MAX"))    options |= RETURN_MAX;
                                else if (option.equals("ALL"))    options |= RETURN_ALL;
                                else if (option.equals("COUNT"))  options |= RETURN_COUNT;
                                else if (option.equals("SAVE") && extensionEnabled("X-DRAFT-I04-SEARCHRES"))  options |= RETURN_SAVE;
                                else
                                    throw new ImapParseException(tag, "unknown RETURN option \"" + option + '"');
                            }
                            req.skipChar(')');  req.skipSpace();
                            if (options == 0)
                                options = RETURN_ALL;
                        }
                        ImapSearch i4search = req.readSearch();
                        checkEOF(tag, req);
                        return doSEARCH(tag, i4search, byUID, options);
                    } else if (command.equals("SELECT")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doSELECT(tag, folder);
                    } else if (command.equals("STARTTLS") && extensionEnabled("STARTTLS")) {
                        checkEOF(tag, req);
                        return doSTARTTLS(tag);
                    } else if (command.equals("STATUS")) {
                        int status = 0;
                        req.skipSpace();  String folder = req.readFolder();
                        req.skipSpace();  req.skipChar('(');
                        while (req.peekChar() != ')') {
                            if (status != 0)
                                req.skipSpace();
                            String flag = req.readATOM();
                            if (flag.equals("MESSAGES"))          status |= STATUS_MESSAGES;
                            else if (flag.equals("RECENT"))       status |= STATUS_RECENT;
                            else if (flag.equals("UIDNEXT"))      status |= STATUS_UIDNEXT;
                            else if (flag.equals("UIDVALIDITY"))  status |= STATUS_UIDVALIDITY;
                            else if (flag.equals("UNSEEN"))       status |= STATUS_UNSEEN;
                            else
                                throw new ImapParseException(tag, "unknown STATUS attribute \"" + flag + '"');
                        }
                        req.skipChar(')');
                        checkEOF(tag, req);
                        return doSTATUS(tag, folder, status);
                    } else if (command.equals("SUBSCRIBE")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doSUBSCRIBE(tag, folder);
                    } else if (command.equals("SETQUOTA") && extensionEnabled("QUOTA")) {
                        Map<String, String> limits = new HashMap<String, String>();
                        req.skipSpace();  String qroot = req.readAstring();
                        req.skipSpace();  req.skipChar('(');
                        while (req.peekChar() != ')') {
                            if (!limits.isEmpty())
                                req.skipSpace();
                            String resource = req.readATOM();  req.skipSpace();
                            limits.put(resource, req.readNumber());
                        }
                        req.skipChar(')');
                        checkEOF(tag, req);
                        return doSETQUOTA(tag, qroot, limits);
                    }
                    break;
                case 'U':
                    if (command.equals("UID")) {
                        req.skipSpace();  command = req.readATOM();
                        if (command.equals("FETCH") || command.equals("SEARCH") || command.equals("COPY") || command.equals("STORE") || (command.equals("EXPUNGE") && extensionEnabled("UIDPLUS"))) {
                            byUID = true;
                            continue;
                        }
                        throw new ImapParseException(tag, "command not permitted with UID");
                    } else if (command.equals("UNSUBSCRIBE")) {
                        req.skipSpace();  String folder = req.readFolder();
                        checkEOF(tag, req);
                        return doUNSUBSCRIBE(tag, folder);
                    } else if (command.equals("UNSELECT") && extensionEnabled("UNSELECT")) {
                        checkEOF(tag, req);
                        return doUNSELECT(tag);
                    }
                    break;
            }
        } while (byUID);

        throw new ImapParseException(tag, "command not implemented");
    }

    boolean checkState(String tag, byte required) throws IOException {
        byte state = ImapSession.getState(mSession);
        if (required == ImapSession.STATE_NOT_AUTHENTICATED && state != ImapSession.STATE_NOT_AUTHENTICATED) {
            sendNO(tag, "must be in NOT AUTHENTICATED state");
            return false;
        } else if (required == ImapSession.STATE_AUTHENTICATED && (state == ImapSession.STATE_NOT_AUTHENTICATED || state == ImapSession.STATE_LOGOUT)) {
            sendNO(tag, "must be in AUTHENTICATED or SELECTED state");
            return false;
        } else if (required == ImapSession.STATE_SELECTED && state != ImapSession.STATE_SELECTED) {
            sendNO(tag, "must be in SELECTED state");
            return false;
        } else
            return true;
    }

    boolean canContinue(ServiceException e) {
        return e.getCode() == MailServiceException.MAINTENANCE ? STOP_PROCESSING : CONTINUE_PROCESSING;
    }

    private OperationContext getContext() throws ServiceException {
        if (mSession == null)
            throw ServiceException.AUTH_REQUIRED();
        return mSession.getContext();
    }


    boolean doCAPABILITY(String tag) throws IOException {
        sendCapability();
        sendOK(tag, "CAPABILITY completed");
        return CONTINUE_PROCESSING;
    }

    private static final String[] SUPPORTED_EXTENSIONS = new String[] {
        "BINARY", "CATENATE", "CHILDREN", "ESEARCH", "ID", "IDLE",
        "LITERAL+", "LOGIN-REFERRALS", "NAMESPACE", "QUOTA", "SASL-IR",
        "UIDPLUS", "UNSELECT", "WITHIN", "X-DRAFT-I04-SEARCHRES"
    };

    private void sendCapability() throws IOException {
        // [IMAP4rev1]        RFC 3501: Internet Message Access Protocol - Version 4rev1
        // [LOGINDISABLED]    RFC 3501: Internet Message Access Protocol - Version 4rev1
        // [STARTTLS]         RFC 3501: Internet Message Access Protocol - Version 4rev1
        // [AUTH=PLAIN]       RFC 2595: Using TLS with IMAP, POP3 and ACAP
        // [BINARY]           RFC 3516: IMAP4 Binary Content Extension
        // [CATENATE]         RFC 4469: Internet Message Access Protocol (IMAP) CATENATE Extension
        // [CHILDREN]         RFC 3348: IMAP4 Child Mailbox Extension
        // [ESEARCH]          RFC 4731: IMAP4 Extension to SEARCH Command for Controlling What Kind of Information Is Returned
        // [ID]               RFC 2971: IMAP4 ID Extension
        // [IDLE]             RFC 2177: IMAP4 IDLE command
        // [LITERAL+]         RFC 2088: IMAP4 non-synchronizing literals
        // [LOGIN-REFERRALS]  RFC 2221: IMAP4 Login Referrals
        // [NAMESPACE]        RFC 2342: IMAP4 Namespace
        // [QUOTA]            RFC 2087: IMAP4 QUOTA extension
        // [SASL-IR]          draft-siemborski-imap-sasl-initial-response-06: IMAP Extension for SASL Initial Client Response
        // [UIDPLUS]          RFC 4315: Internet Message Access Protocol (IMAP) - UIDPLUS extension
        // [UNSELECT]         RFC 3691: IMAP UNSELECT command
        // [WITHIN]           draft-ietf-lemonade-search-within-03: WITHIN Search extension to the IMAP Protocol
        // [X-DRAFT-I04-SEARCHRES]  draft-melnikov-imap-search-res-04: IMAP extension for referencing the last SEARCH result

        boolean authenticated = mSession != null;
        String nologin  = mStartedTLS || authenticated || ImapServer.allowCleartextLogins() ? "" : " LOGINDISABLED";
        String starttls = mStartedTLS || authenticated || !extensionEnabled("STARTTLS")     ? "" : " STARTTLS";
        String plain    = !mStartedTLS || authenticated || !extensionEnabled("AUTH=PLAIN")  ? "" : " AUTH=PLAIN";

        StringBuilder capability = new StringBuilder("CAPABILITY IMAP4rev1" + nologin + starttls + plain);
        for (String extension : SUPPORTED_EXTENSIONS)
            if (extensionEnabled(extension))
                capability.append(' ').append(extension);

        sendUntagged(capability.toString());
    }

    boolean extensionEnabled(String extension) {
        // check whether the extension is explicitly disabled on the server
        if (ImapServer.isExtensionDisabled(mServer, extension))
            return false;
        // check whether one of the extension's prerequeisites is disabled on the server
        if (extension.equalsIgnoreCase("X-DRAFT-I04-SEARCHRES"))
            return extensionEnabled("ESEARCH");
        // everything else is enabled
        return true;
    }

    boolean doNOOP(String tag) throws IOException {
        if (mMailbox != null)
            sendNotifications(true, false);
        sendOK(tag, "NOOP completed");
        return CONTINUE_PROCESSING;
    }

    // RFC 2971 3: "The sole purpose of the ID extension is to enable clients and servers
    //              to exchange information on their implementations for the purposes of
    //              statistical analysis and problem determination.
    boolean doID(String tag, Map<String, String> attrs) throws IOException {
        if (attrs != null)
            ZimbraLog.imap.info("IMAP client identified as: " + attrs);

        sendUntagged("ID (\"NAME\" \"Zimbra\" \"VERSION\" \"" + BuildInfo.VERSION + "\" \"RELEASE\" \"" + BuildInfo.RELEASE + "\")");
        sendOK(tag, "ID completed");
        return CONTINUE_PROCESSING;
    }

    boolean doSTARTTLS(String tag) throws IOException {
        if (!checkState(tag, ImapSession.STATE_NOT_AUTHENTICATED))
            return CONTINUE_PROCESSING;
        else if (mStartedTLS) {
            sendNO(tag, "TLS already started");
            return CONTINUE_PROCESSING;
        }
        sendOK(tag, "Begin TLS negotiation now");

        SSLSocketFactory fac = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket tlsconn = (SSLSocket) fac.createSocket(mConnection, mConnection.getInetAddress().getHostName(), mConnection.getPort(), true);
        tlsconn.setUseClientMode(false);
        tlsconn.startHandshake();
        ZimbraLog.imap.debug("suite: " + tlsconn.getSession().getCipherSuite());
        mInputStream = new TcpServerInputStream(tlsconn.getInputStream());
        mOutputStream = new BufferedOutputStream(tlsconn.getOutputStream());
        mStartedTLS = true;

        return CONTINUE_PROCESSING;
    }

    boolean doLOGOUT(String tag) throws IOException {
        sendUntagged(ImapServer.getGoodbye());
        if (mSession != null)
            mSession.loggedOut();
        mGoodbyeSent = true;
        sendOK(tag, "LOGOUT completed");
        return STOP_PROCESSING;
    }

    boolean doAUTHENTICATE(String tag, String mechanism, byte[] response) throws IOException {
        if (!checkState(tag, ImapSession.STATE_NOT_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        boolean finished = false;

        if (mechanism.equals("PLAIN") && extensionEnabled("AUTH=PLAIN")) {
            // RFC 2595 6: "The PLAIN SASL mechanism MUST NOT be advertised or used
            //              unless a strong encryption layer (such as the provided by TLS)
            //              is active or backwards compatibility dictates otherwise."
            if (!mStartedTLS) {
                sendNO(tag, "cleartext logins disabled");
                return CONTINUE_PROCESSING;
            }
            mAuthenticator = new AuthPlain(tag);
        } else {
            // no other AUTHENTICATE mechanisms are supported yet
            sendNO(tag, "mechanism not supported");
            return CONTINUE_PROCESSING;
        }

        // draft-siemborski-imap-sasl-initial-response:
        //      "This extension adds an optional second argument to the AUTHENTICATE
        //       command that is defined in Section 6.2.2 of [IMAP4].  If this second
        //       argument is present, it represents the contents of the "initial
        //       client response" defined in section 5.1 of [SASL]."
        if (response != null)
            finished = mAuthenticator.handle(response);

        if (finished)
            mAuthenticator = null;
        else
            sendContinuation();
        return CONTINUE_PROCESSING;
    }

    boolean doLOGIN(String tag, String username, String password) throws IOException {
        if (!checkState(tag, ImapSession.STATE_NOT_AUTHENTICATED))
            return CONTINUE_PROCESSING;
        else if (!mStartedTLS && !ImapServer.allowCleartextLogins()) {
            sendNO(tag, "cleartext logins disabled");
            return CONTINUE_PROCESSING;
        }

        return login(username, "", password, "LOGIN", tag);
    }

    boolean login(String username, String authenticateId, String password, String command, String tag) throws IOException {
        // the Windows Mobile 5 hacks are enabled by appending "/wm" to the username
        EnabledHack hack = EnabledHack.NONE;
        if (username.endsWith("/wm")) {
            username = username.substring(0, username.length() - 3);
            hack = EnabledHack.WM5;
        } else if (username.endsWith("/tb")) {
            username = username.substring(0, username.length() - 3);
            hack = EnabledHack.THUNDERBIRD;
        }

        try {
            Account account = authenticate(authenticateId, username, password, command, tag);
            ImapSession session = startSession(account, hack, command, tag);
            if (session == null)
                return CONTINUE_PROCESSING;
        } catch (ServiceException e) {
            if (mSession != null)
                mSession.clearTagCache();
            ZimbraLog.imap.warn(command + " failed", e);
            if (e.getCode() == AccountServiceException.CHANGE_PASSWORD)
                sendNO(tag, "[ALERT] password must be changed before IMAP login permitted");
            else if (e.getCode() == AccountServiceException.MAINTENANCE_MODE)
                sendNO(tag, "[ALERT] account undergoing maintenance; please try again later");
            else
                sendNO(tag, command + " failed");
            return canContinue(e);
        }

        sendCapability();
        sendOK(tag, command + " completed");
        return CONTINUE_PROCESSING;
    }

    private Account authenticate(String authenticateId, String username, String password, String command, String tag) throws ServiceException, IOException {
        Provisioning prov = Provisioning.getInstance();
        Account account = prov.get(AccountBy.name, username);
        Account authacct = authenticateId.equals("") ? account : prov.get(AccountBy.name, authenticateId);
        if (account == null || authacct == null) {
            sendNO(tag, command + " failed");
            return null;
        }
        // authenticate the authentication principal
        prov.authAccount(authacct, password, "imap");
        // authorize as the target user
        if (!account.getId().equals(authacct.getId())) {
            // check domain/global admin if auth credentials != target account
            if (!AccessManager.getInstance().canAccessAccount(authacct, account)) {
                sendNO(tag, command + " failed");
                return null;
            }
        }
        return account;
    }

    private ImapSession startSession(Account account, EnabledHack hack, String command, String tag) throws ServiceException, IOException {
        if (account == null)
            return null;

        // make sure we can actually login via IMAP on this host
        if (!account.getBooleanAttr(Provisioning.A_zimbraImapEnabled, false)) {
            sendNO(tag, "account does not have IMAP access enabled");
            return null;
        } else if (!Provisioning.onLocalServer(account)) { 
            String correctHost = account.getAttr(Provisioning.A_zimbraMailHost);
            ZimbraLog.imap.info(command + " failed; should be on host " + correctHost);
            if (correctHost == null || correctHost.trim().equals("") || !extensionEnabled("LOGIN_REFERRALS"))
                sendNO(tag, command + " failed [wrong host]");
            else
                sendNO(tag, "[REFERRAL imap://" + URLEncoder.encode(account.getName(), "utf-8") + '@' + correctHost + "/] " + command + " failed");
            return null;
        }

        ImapSession session = (ImapSession) SessionCache.getNewSession(account.getId(), SessionCache.SESSION_IMAP);
        if (session == null) {
            sendNO(tag, "AUTHENTICATE failed");
            return null;
        }
        session.enableHack(hack);

        Mailbox mbox = session.getMailbox();
        synchronized (mbox) {
            session.setUsername(account.getName());
            session.cacheFlags(mbox);
            for (Tag ltag : mbox.getTagList(session.getContext()))
                session.cacheTag(ltag);
        }

        // everything's good, so store the session in the handler
        mMailbox = mbox;
        mSession = session;
        mSession.setHandler(this);

        return session;
    }

    boolean doSELECT(String tag, String folderName) throws IOException {
        return selectFolder(tag, "SELECT", folderName);
    }

    boolean doEXAMINE(String tag, String folderName) throws IOException {
        return selectFolder(tag, "EXAMINE", folderName);
    }

    private boolean selectFolder(String tag, String command, String folderName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        // 6.3.1: "The SELECT command automatically deselects any currently selected mailbox 
        //         before attempting the new selection.  Consequently, if a mailbox is selected
        //         and a SELECT command that fails is attempted, no mailbox is selected."
        ImapFolder i4folder = null;
        if (mSession.isSelected())
            i4folder = mSession.deselectFolder();

        boolean writable = command.equals("SELECT");
        try {
        	ImapGetFolderOperation op = new ImapGetFolderOperation(mSession, getContext(), mMailbox, folderName, writable, i4folder);
        	op.schedule();
        	i4folder = op.getResult();
        } catch (ServiceException e) {
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
                ZimbraLog.imap.info(command + " failed: no such folder: " + folderName);
            else
                ZimbraLog.imap.warn(command + " failed", e);
            sendNO(tag, command + " failed");
            return canContinue(e);
        }

        writable = i4folder.isWritable();
        mSession.selectFolder(i4folder);

        // note: not sending back a "* OK [UIDNEXT ....]" response for search folders
        //    6.3.1: "If this is missing, the client can not make any assumptions about the
        //            next unique identifier value."
        // FIXME: hardcoding "* 0 RECENT"
        sendUntagged(i4folder.getSize() + " EXISTS");
        sendUntagged(0 + " RECENT");
        if (i4folder.getFirstUnread() > 0)
        	sendUntagged("OK [UNSEEN " + i4folder.getFirstUnread() + ']');
        sendUntagged("OK [UIDVALIDITY " + i4folder.getUIDValidity() + ']');
        if (!i4folder.isVirtual())
            sendUntagged("OK [UIDNEXT " + i4folder.getInitialUIDNEXT() + ']');
        sendUntagged("FLAGS (" + mSession.getFlagList(false) + ')');
        sendUntagged("OK [PERMANENTFLAGS (" + (writable ? mSession.getFlagList(true) + " \\*" : "") + ")]");
        sendOK(tag, (writable ? "[READ-WRITE] " : "[READ-ONLY] ") + command + " completed");
        return CONTINUE_PROCESSING;
    }

    boolean doCREATE(String tag, String folderName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);
        if (!ImapFolder.isPathCreatable(folderName)) {
            ZimbraLog.imap.info("CREATE failed: hidden folder or parent: %s", folderName);
            sendNO(tag, "CREATE failed");
            return CONTINUE_PROCESSING;
        }

        try {
        	CreateFolderOperation op = new CreateFolderOperation(mSession, getContext(), mMailbox, Requester.IMAP, folderName, MailItem.TYPE_MESSAGE, false);
        	op.schedule();
        } catch (ServiceException e) {
            String cause = "CREATE failed";
            if (e.getCode() == MailServiceException.CANNOT_CONTAIN)
                cause += ": superior mailbox has \\Noinferiors set";
            else if (e.getCode() == MailServiceException.ALREADY_EXISTS)
                cause += ": mailbox already exists";
            else if (e.getCode() == MailServiceException.INVALID_NAME)
                cause += ": invalid mailbox name";
            ZimbraLog.imap.warn(cause, e);
            sendNO(tag, cause);
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, "CREATE completed");
        return CONTINUE_PROCESSING;
    }

    boolean doDELETE(String tag, String folderName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);

        int folderId = 0;
        try {
        	ImapDeleteOperation op = new ImapDeleteOperation(mSession, getContext(), mMailbox, folderName);
        	op.schedule();
        	folderId = op.getFolderId();
        } catch (ImapServiceException e) {
        	ZimbraLog.imap.info("DELETE failed: "+e.toString());
        	sendNO(tag, "DELETE failed");
        	return CONTINUE_PROCESSING;
        } catch (ServiceException e) {
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
                ZimbraLog.imap.info("DELETE failed: no such folder: " + folderName);
            else
                ZimbraLog.imap.warn("DELETE failed", e);
            sendNO(tag, "DELETE failed");
            return canContinue(e);
        }

        if (mSession.isSelected() && folderId == mSession.getFolder().getId())
            mSession.deselectFolder();

        sendNotifications(true, false);
        sendOK(tag, "DELETE completed");
        return CONTINUE_PROCESSING;
    }

    boolean doRENAME(String tag, String oldName, String newName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        oldName = ImapFolder.importPath(oldName, mSession);
        newName = ImapFolder.importPath(newName, mSession);

        try {
        	ImapRenameOperation op = new ImapRenameOperation(mSession, getContext(), mMailbox, oldName, newName);
        	op.schedule();
        } catch (ServiceException e) {
        	if (e instanceof ImapServiceException && e.getCode() == ImapServiceException.CANT_RENAME_INBOX) {
        		ZimbraLog.imap.info("RENAME failed: RENAME of INBOX not supported");
        		sendNO(tag, "RENAME failed: RENAME of INBOX not supported");
        		return CONTINUE_PROCESSING;
        	} else if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
        		ZimbraLog.imap.info("RENAME failed: no such folder: " + oldName);
        	else
        		ZimbraLog.imap.warn("RENAME failed", e);
        	sendNO(tag, "RENAME failed");
        	return canContinue(e);
        }

        // note: if ImapFolder contains a pathname, we may need to update mSelectedFolder
        sendNotifications(true, false);
        sendOK(tag, "RENAME completed");
        return CONTINUE_PROCESSING;
    }

    boolean doSUBSCRIBE(String tag, String folderName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);

        try {
        	ImapSubscribeOperation op = new ImapSubscribeOperation(mSession, getContext(), mMailbox, folderName);
        	op.schedule();
        } catch (ImapServiceException e) {
        	if (e.getCode() == ImapServiceException.FOLDER_NOT_VISIBLE) {
        		ZimbraLog.imap.info("SUBSCRIBE failed: "+e.toString());
        		sendNO(tag, "SUBSCRIBE failed");
        		return CONTINUE_PROCESSING;
        	} else 
        		ZimbraLog.imap.warn("SUBSCRIBE failed", e);
        } catch (ServiceException e) {
        	if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
        		ZimbraLog.imap.info("SUBSCRIBE failed: no such folder: " + folderName);
        	else
        		ZimbraLog.imap.warn("SUBSCRIBE failed", e);
        	sendNO(tag, "SUBSCRIBE failed");
        	return canContinue(e);
        }
        
        sendNotifications(true, false);
        sendOK(tag, "SUBSCRIBE completed");
        return CONTINUE_PROCESSING;
    }

    boolean doUNSUBSCRIBE(String tag, String folderName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);

        try {
        	ImapUnsubscribeOperation op = new ImapUnsubscribeOperation(mSession, getContext(), mMailbox, folderName);
        	op.schedule();
        } catch (MailServiceException.NoSuchItemException nsie) {
            ZimbraLog.imap.info("UNSUBSCRIBE failure skipped: no such folder: " + folderName);
        } catch (ServiceException e) {
            ZimbraLog.imap.warn("UNSUBSCRIBE failed", e);
            sendNO(tag, "UNSUBSCRIBE failed");
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, "UNSUBSCRIBE completed");
        return CONTINUE_PROCESSING;
    }

    boolean doLIST(String tag, String referenceName, String mailboxName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        if (mailboxName.equals("")) {
            // 6.3.8: "An empty ("" string) mailbox name argument is a special request to return
            //         the hierarchy delimiter and the root name of the name given in the reference."
            sendUntagged("LIST (\\Noselect) \"/\" \"\"");
            sendOK(tag, "LIST completed");
            return CONTINUE_PROCESSING;
        }

        String pattern = mailboxName;
        if (!mailboxName.startsWith("/")) {
            if (referenceName.endsWith("/"))  pattern = referenceName + mailboxName;
            else                              pattern = referenceName + '/' + mailboxName;
        }
        if (pattern.startsWith("/"))
            pattern = pattern.substring(1);
        List<String> matches = new ArrayList<String>();
        try {
        	ImapListOperation op = new ImapListOperation(mSession, getContext(), mMailbox, pattern, extensionEnabled("CHILDREN"));
        	op.schedule();
        	matches = op.getMatches();
        } catch (ServiceException e) {
            ZimbraLog.imap.warn("LIST failed", e);
            sendNO(tag, "LIST failed");
            return canContinue(e);
        }

        if (matches != null) 
        	for (String match : matches)
        		sendUntagged(match);
        
        sendNotifications(true, false);
        sendOK(tag, "LIST completed");
        return CONTINUE_PROCESSING;
    }

    boolean doLSUB(String tag, String referenceName, String mailboxName) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        String pattern = mailboxName;
        if (!mailboxName.startsWith("/")) {
            if (referenceName.endsWith("/"))  pattern = referenceName + mailboxName;
            else                              pattern = referenceName + '/' + mailboxName;
        }
        if (pattern.startsWith("/"))
            pattern = pattern.substring(1);

        try {
        	ImapLSubOperation op = new ImapLSubOperation(mSession, getContext(), mMailbox, pattern, extensionEnabled("CHILDREN"));
        	op.schedule();
        	Map<String, String> subs = op.getSubs();
        	
        	for (String sub : subs.values())
        		sendUntagged(sub);
        } catch (ServiceException e) {
            ZimbraLog.imap.warn("LSUB failed", e);
            sendNO(tag, "LSUB failed");
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, "LSUB completed");
        return CONTINUE_PROCESSING;
    }

    private static final int STATUS_MESSAGES    = 0x01;
    private static final int STATUS_RECENT      = 0x02;
    private static final int STATUS_UIDNEXT     = 0x04;
    private static final int STATUS_UIDVALIDITY = 0x08;
    private static final int STATUS_UNSEEN      = 0x10;

    boolean doSTATUS(String tag, String folderName, int status) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);

        StringBuilder data = new StringBuilder();
        try {
            GetFolderOperation op = new GetFolderOperation(mSession, getContext(), mMailbox, Requester.IMAP, folderName);
            op.schedule();
            Folder folder = op.getFolder();

            if (!ImapFolder.isFolderVisible(folder, mSession)) {
                ZimbraLog.imap.info("STATUS failed: folder not visible: " + folderName);
                sendNO(tag, "STATUS failed");
                return CONTINUE_PROCESSING;
            }
            if ((status & STATUS_MESSAGES) != 0)
                data.append(data.length() > 0 ? " " : "").append("MESSAGES ").append(folder.getSize());
            // FIXME: hardcoded "RECENT 0"
            if ((status & STATUS_RECENT) != 0)
                data.append(data.length() > 0 ? " " : "").append("RECENT ").append(0);
            // note: we're not supporting UIDNEXT for search folders; see the comments in selectFolder()
            if ((status & STATUS_UIDNEXT) != 0 && !(folder instanceof SearchFolder))
                data.append(data.length() > 0 ? " " : "").append("UIDNEXT ").append(folder.getImapUIDNEXT());
            if ((status & STATUS_UIDVALIDITY) != 0)
                data.append(data.length() > 0 ? " " : "").append("UIDVALIDITY ").append(ImapFolder.getUIDValidity(folder));
            if ((status & STATUS_UNSEEN) != 0)
                data.append(data.length() > 0 ? " " : "").append("UNSEEN ").append(folder.getUnreadCount());
        } catch (ServiceException e) {
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
                ZimbraLog.imap.info("STATUS failed: no such folder: " + folderName);
            else
                ZimbraLog.imap.warn("STATUS failed", e);
            sendNO(tag, "STATUS failed");
            return canContinue(e);
        }

        sendUntagged("STATUS " + ImapFolder.formatPath(folderName, mSession) + " (" + data + ')');
        sendNotifications(true, false);
        sendOK(tag, "STATUS completed");
        return CONTINUE_PROCESSING;
    }

    boolean doCATENATE(String tag, String folderName, List<String> flagNames, Date date, List<Object> parts) throws IOException, ImapParseException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size = 0;
        for (Object part : parts) {
            byte[] buffer;
            if (part instanceof byte[])
                buffer = (byte[]) part;
            else
                buffer = ((ImapURL) part).getContent(mSession, tag);

            size += buffer.length;
            if (size > ImapRequest.getMaxRequestLength())
                throw new ImapParseException(tag, "TOOBIG", "request too long", false);
            baos.write(buffer);
        }
        return doAPPEND(tag, folderName, flagNames, date, baos.toByteArray());
    }

    boolean doAPPEND(String tag, String folderName, List<String> flagNames, Date date, byte[] content) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        // server uses UNIX time, so range-check specified date (is there a better place for this?)
        if (date != null && date.getTime() > Integer.MAX_VALUE * 1000L) {
            ZimbraLog.imap.info("APPEND failed: date out of range");
            sendNO(tag, "APPEND failed: date out of range");
            return CONTINUE_PROCESSING;
        }

        ArrayList<Tag> newTags = new ArrayList<Tag>();
        StringBuilder appendHint = extensionEnabled("UIDPLUS") ? new StringBuilder() : null;
        try {
        	ImapAppendOperation op = new ImapAppendOperation(mSession, getContext(), mMailbox,
        				new FindOrCreateTags(), folderName, flagNames, date, content, newTags, appendHint);
        	op.schedule();
        } catch (ServiceException e) {
            deleteTags(newTags);
            String msg = "APPEND failed";
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER) {
                ZimbraLog.imap.info("APPEND failed: no such folder: " + folderName);
                // 6.3.11: "Unless it is certain that the destination mailbox can not be created,
                //          the server MUST send the response code "[TRYCREATE]" as the prefix
                //          of the text of the tagged NO response."
                if (ImapFolder.isPathCreatable('/' + folderName))
                    msg = "[TRYCREATE] APPEND failed: no such mailbox";
            } else {
                ZimbraLog.imap.warn("APPEND failed", e);
            }
            sendNO(tag, msg);
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, (appendHint == null ? "" : appendHint.toString()) + "APPEND completed");
        return CONTINUE_PROCESSING;
    }

     
    class FindOrCreateTags implements IFindOrCreateTags {
    	public List<ImapFlag> doFindOrCreateTags(List<String> flagNames, List<Tag> newTags) throws ServiceException {
    		return findOrCreateTags(flagNames, newTags);
    	}
    }

    List<ImapFlag> findOrCreateTags(List<String> tagNames, List<Tag> newTags) throws ServiceException {
        if (tagNames == null || tagNames.size() == 0)
            return Collections.emptyList();
        ArrayList<ImapFlag> flags = new ArrayList<ImapFlag>();
        for (String name : tagNames) {
            ImapFlag i4flag = mSession.getFlagByName(name);
            if (i4flag == null) {
                if (name.startsWith("\\"))
                    throw MailServiceException.INVALID_NAME(name);
                try {
                    i4flag = mSession.cacheTag(mMailbox.getTagByName(name));
                } catch (MailServiceException.NoSuchItemException nsie) {
                    if (newTags == null)
                        continue;
                    // notification will update mTags hash
                    CreateTagOperation op = new CreateTagOperation(mSession, getContext(), mMailbox, Requester.IMAP, name, MailItem.DEFAULT_COLOR);
                    op.schedule();
                    Tag ltag = op.getTag();
                    
                    newTags.add(ltag);
                    i4flag = mSession.getFlagByName(name);
                }
            }
            flags.add(i4flag);
        }
        return flags;
    }
    private void deleteTags(List<Tag> ltags) {
        if (mMailbox != null && ltags != null)
            for (Tag ltag : ltags)
                try {
                    // notification will update mTags hash
                    DeleteOperation op = new DeleteOperation(mSession, getContext(), mMailbox, Requester.IMAP, ltag.getId(), MailItem.TYPE_TAG);
                    op.schedule();
                    
                } catch (ServiceException e) {
                    ZimbraLog.imap.warn("failed to delete tag: " + ltag.getName(), e);
                }
    }

    static final boolean IDLE_START = true;
    static final boolean IDLE_STOP  = false;

    // RFC 2177 3: "The IDLE command is sent from the client to the server when the client is
    //              ready to accept unsolicited mailbox update messages.  The server requests
    //              a response to the IDLE command using the continuation ("+") response.  The
    //              IDLE command remains active until the client responds to the continuation,
    //              and as long as an IDLE command is active, the server is now free to send
    //              untagged EXISTS, EXPUNGE, and other messages at any time."
    boolean doIDLE(String tag, boolean begin, boolean success) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        if (begin == IDLE_START) {
            mSession.beginIdle(tag);
            sendNotifications(true, false);
            sendContinuation();
        } else {
            tag = mSession.endIdle();
            if (success)  sendOK(tag, "IDLE completed");
            else          sendBAD(tag, "IDLE stopped without DONE");
        }
        return CONTINUE_PROCESSING;
    }

    boolean doSETQUOTA(String tag, String qroot, Map<String, String> limits) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        // cannot set quota from IMAP at present
        sendNO(tag, "SETQUOTA failed");
        return CONTINUE_PROCESSING;
    }

    boolean doGETQUOTA(String tag, String qroot) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        try {
            long quota = mMailbox.getAccount().getIntAttr(Provisioning.A_zimbraMailQuota, 0);
            if (qroot == null || !qroot.equals("") || quota <= 0) {
                ZimbraLog.imap.info("GETQUOTA failed: unknown quota root: %s", qroot);
                sendNO(tag, "GETQUOTA failed: unknown quota root");
                return CONTINUE_PROCESSING;
            }
            // RFC 2087 3: "STORAGE  Sum of messages' RFC822.SIZE, in units of 1024 octets"
            sendUntagged("QUOTA \"\" (STORAGE " + (mMailbox.getSize() / 1024) + ' ' + (quota / 1024) + ')');
        } catch (ServiceException e) {
            ZimbraLog.imap.warn("GETQUOTA failed", e);
            sendNO(tag, "GETQUOTA failed");
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, "GETQUOTA completed");
        return CONTINUE_PROCESSING;
    }

    boolean doGETQUOTAROOT(String tag, String path) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        path = ImapFolder.importPath(path, mSession);

        try {
            // make sure the folder exists and is visible
            GetFolderOperation op = new GetFolderOperation(mSession, getContext(), mMailbox, Requester.IMAP, path);
            op.schedule();
            Folder folder = op.getFolder();
            
            if (!ImapFolder.isFolderVisible(folder, mSession)) {
                ZimbraLog.imap.info("GETQUOTAROOT failed: folder not visible: " + path);
                sendNO(tag, "GETQUOTAROOT failed");
                return CONTINUE_PROCESSING;
            }

            // see if there's any quota on the account
            long quota = mMailbox.getAccount().getIntAttr(Provisioning.A_zimbraMailQuota, 0);
            sendUntagged("QUOTAROOT " + ImapFolder.formatPath(path, mSession) + (quota > 0 ? " \"\"" : ""));
            if (quota > 0)
                sendUntagged("QUOTA \"\" (STORAGE " + (mMailbox.getSize() / 1024) + ' ' + (quota / 1024) + ')');
        } catch (ServiceException e) {
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER)
                ZimbraLog.imap.info("GETQUOTAROOT failed: no such folder: " + path);
            else
                ZimbraLog.imap.warn("GETQUOTAROOT failed", e);
            sendNO(tag, "GETQUOTAROOT failed");
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, "GETQUOTAROOT completed");
        return CONTINUE_PROCESSING;
    }

    boolean doNAMESPACE(String tag) throws IOException {
        if (!checkState(tag, ImapSession.STATE_AUTHENTICATED))
            return CONTINUE_PROCESSING;

        sendUntagged("NAMESPACE ((\"\" \"/\")) NIL NIL");
        sendNotifications(true, false);
        sendOK(tag, "NAMESPACE completed");
        return CONTINUE_PROCESSING;
    }

    boolean doCHECK(String tag) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        sendNotifications(true, false);
        sendOK(tag, "CHECK completed");
        return CONTINUE_PROCESSING;
    }

    boolean doCLOSE(String tag) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        try {
            // 6.4.2: "The CLOSE command permanently removes all messages that have the \Deleted
            //         flag set from the currently selected mailbox, and returns to the authenticated
            //         state from the selected state.  No untagged EXPUNGE responses are sent.
            //
            //         No messages are removed, and no error is given, if the mailbox is
            //         selected by an EXAMINE command or is otherwise selected read-only."
            ImapFolder i4folder = mSession.getFolder();
            if (i4folder.isWritable())
                expungeMessages(i4folder, null);
        } catch (ServiceException e) {
            ZimbraLog.imap.warn("EXPUNGE failed", e);
            sendNO(tag, "EXPUNGE failed");
            return canContinue(e);
        }

        mSession.deselectFolder();

        sendOK(tag, "CLOSE completed");
        return CONTINUE_PROCESSING;
    }

    // RFC 3691 2: "The UNSELECT command frees server's resources associated with the selected
    //              mailbox and returns the server to the authenticated state.  This command
    //              performs the same actions as CLOSE, except that no messages are permanently
    //              removed from the currently selected mailbox."
    boolean doUNSELECT(String tag) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        mSession.deselectFolder();

        sendOK(tag, "UNSELECT completed");
        return CONTINUE_PROCESSING;
    }

    boolean doEXPUNGE(String tag, boolean byUID, String sequenceSet) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;
        else if (!mSession.getFolder().isWritable()) {
            sendNO(tag, "mailbox selected READ-ONLY");
            return CONTINUE_PROCESSING;
        }

        String command = (byUID ? "UID EXPUNGE" : "EXPUNGE");
        try {
            expungeMessages(mSession.getFolder(), sequenceSet);
        } catch (ServiceException e) {
            ZimbraLog.imap.warn(command + " failed", e);
            sendNO(tag, command + " failed");
            return canContinue(e);
        }

        sendNotifications(true, false);
        sendOK(tag, command + " completed");
        return CONTINUE_PROCESSING;
    }

    void expungeMessages(ImapFolder i4folder, String sequenceSet) throws ServiceException, IOException {
        Set<ImapMessage> i4set;
        synchronized (mMailbox) {
            i4set = (sequenceSet == null ? null : i4folder.getSubsequence(sequenceSet, true));
        }
        List<Integer> ids = new ArrayList<Integer>(DeleteOperation.SUGGESTED_BATCH_SIZE);

        long checkpoint = System.currentTimeMillis();
        for (int i = 1, max = i4folder.getSize(); i <= max; i++) {
            ImapMessage i4msg = i4folder.getBySequence(i);
            if (i4msg != null && !i4msg.isExpunged() && (i4msg.flags & Flag.BITMASK_DELETED) > 0)
                if (i4set == null || i4set.contains(i4msg))
                    ids.add(i4msg.msgId);

            if (ids.size() >= (i == max ? 1 : DeleteOperation.SUGGESTED_BATCH_SIZE)) {
                try {
                    ZimbraLog.imap.debug("  ** deleting: " + ids);
                    new DeleteOperation(mSession, getContext(), mMailbox, Requester.IMAP, ids, MailItem.TYPE_UNKNOWN, null).schedule();
                } catch (MailServiceException.NoSuchItemException e) {
                    // something went wrong, so delete *this* batch one at a time
                    for (int id : ids) {
                        try {
                            ZimbraLog.imap.debug("  ** fallback deleting: " + id);
                            new DeleteOperation(mSession, getContext(), mMailbox, Requester.IMAP, id, MailItem.TYPE_UNKNOWN, null).schedule();
                        } catch (MailServiceException.NoSuchItemException nsie) {
                            i4msg = i4folder.getById(id);
                            if (i4msg != null)
                                i4msg.setExpunged(true);
                        }
                    }
                }
                ids.clear();

                // send a gratuitous untagged response to keep pissy clients from closing the socket from inactivity
                long now = System.currentTimeMillis();
                if (now - checkpoint > MAXIMUM_IDLE_PROCESSING_MILLIS) {
                    sendIdleUntagged();  checkpoint = now;
                }
            }
        }
    }

    private static final int RETURN_MIN   = 0x01;
    private static final int RETURN_MAX   = 0x02;
    private static final int RETURN_ALL   = 0x04;
    private static final int RETURN_COUNT = 0x08;
    private static final int RETURN_SAVE  = 0x10;

    private static final int LARGEST_FOLDER_BATCH = 600;
    static final byte[] ITEM_TYPES = new byte[] { MailItem.TYPE_MESSAGE, MailItem.TYPE_CONTACT };

    boolean doSEARCH(String tag, ImapSearch i4search, boolean byUID, Integer options) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        boolean saveResults = (options != null && (options & RETURN_SAVE) != 0);
        ImapMessageSet hits;

        try {
            synchronized (mMailbox) {
                ImapFolder i4folder = mSession.getFolder();
                if (i4search.canBeRunLocally()) {
                    hits = i4search.evaluate(i4folder);
                } else {
                    String search = i4search.toZimbraSearch(i4folder);
                    if (!i4folder.isVirtual())
                        search = "in:" + i4folder.getQuotedPath() + ' ' + search;
                    else if (i4folder.getSize() <= LARGEST_FOLDER_BATCH)
                        search = ImapSearch.sequenceAsSearchTerm(i4folder, i4folder.getSubsequence("1:*", false), false) + ' ' + search;
                    else
                        search = '(' + i4folder.getQuery() + ") " + search;
                    ZimbraLog.imap.info("[ search is: " + search + " ]");

                    SearchParams params = new SearchParams();
                    params.setQueryStr(search);
                    params.setTypes(ITEM_TYPES);
                    params.setSortBy(MailboxIndex.SortBy.DATE_ASCENDING);
                    params.setChunkSize(2000);
                    SearchOperation op = new SearchOperation(mSession, getContext(), mMailbox, Requester.IMAP, params, false, Mailbox.SearchResultMode.IDS);   
                    op.schedule();
                    ZimbraQueryResults zqr = op.getResults();

                    hits = new ImapMessageSet();
                    try {
                        for (ZimbraHit hit = zqr.getFirstHit(); hit != null; hit = zqr.getNext())
                            hits.add(mSession.getFolder().getById(hit.getItemId()));
                    } finally {
                        zqr.doneWithSearchResults();
                    }
                }

                hits.remove(null);
        	}
        } catch (ServiceException e) {
            Throwable t = e.getCause();
            if (t != null && t instanceof ParseException) {
                ZimbraLog.imap.warn("SEARCH failed (bad query)", e);
                sendNO(tag, "SEARCH failed");
                return CONTINUE_PROCESSING;
            } else {
                ZimbraLog.imap.warn("SEARCH failed", e);
                sendNO(tag, "SEARCH failed");
                return CONTINUE_PROCESSING;
            }
        }

        StringBuilder result = null;
        if (options == null) {
            result = new StringBuilder("SEARCH");
            for (ImapMessage i4msg : hits)
                result.append(' ').append(byUID ? i4msg.imapUid : i4msg.sequence);
        } else if (options != RETURN_SAVE) {
            result = new StringBuilder("ESEARCH (TAG \"").append(tag).append("\")");
            if (!hits.isEmpty() && (options & RETURN_MIN) != 0)
                result.append(" MIN ").append(byUID ? hits.first().imapUid : hits.first().sequence);
            if (!hits.isEmpty() && (options & RETURN_MAX) != 0)
                result.append(" MAX ").append(byUID ? hits.last().imapUid : hits.last().sequence);
            if ((options & RETURN_COUNT) != 0)
                result.append(" COUNT ").append(hits.size());
            if (!saveResults && !hits.isEmpty() && (options & RETURN_ALL) != 0)
                result.append(" ALL ").append(ImapFolder.encodeSubsequence(hits, byUID));
        }

        if (saveResults) {
            if (hits.isEmpty() || options == RETURN_SAVE || (options & (RETURN_COUNT | RETURN_ALL)) != 0) {
                mSession.saveSearchResults(hits);
            } else {
                ImapMessageSet saved = new ImapMessageSet();
                if ((options & RETURN_MIN) != 0)
                    saved.add(hits.first());
                if ((options & RETURN_MAX) != 0)
                    saved.add(hits.last());
                mSession.saveSearchResults(saved);
            }
        }

        if (result != null)
            sendUntagged(result.toString());
        sendNotifications(false, false);
        sendOK(tag, "SEARCH completed");
        return CONTINUE_PROCESSING;
    }

    static final int FETCH_BODY          = 0x0001;
    static final int FETCH_BODYSTRUCTURE = 0x0002;
    static final int FETCH_ENVELOPE      = 0x0004;
    static final int FETCH_FLAGS         = 0x0008;
    static final int FETCH_INTERNALDATE  = 0x0010;
    static final int FETCH_RFC822_SIZE   = 0x0020;
    static final int FETCH_BINARY_SIZE   = 0x0040;
    static final int FETCH_UID           = 0x0080;
    static final int FETCH_MARK_READ     = 0x1000;
    private static final int FETCH_FROM_CACHE = FETCH_FLAGS | FETCH_UID;
    private static final int FETCH_FROM_MIME  = FETCH_BODY | FETCH_BODYSTRUCTURE | FETCH_ENVELOPE;

    static final int FETCH_FAST = FETCH_FLAGS | FETCH_INTERNALDATE | FETCH_RFC822_SIZE;
    static final int FETCH_ALL  = FETCH_FAST  | FETCH_ENVELOPE;
    static final int FETCH_FULL = FETCH_ALL   | FETCH_BODY;


    boolean doFETCH(String tag, String sequenceSet, int attributes, List<ImapPartSpecifier> parts, boolean byUID) throws IOException, ImapParseException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        // 6.4.8: "However, server implementations MUST implicitly include the UID message
        //         data item as part of any FETCH response caused by a UID command, regardless
        //         of whether a UID was specified as a message data item to the FETCH."
        if (byUID)
            attributes |= FETCH_UID;
        String command = (byUID ? "UID FETCH" : "FETCH");
        boolean markRead = mSession.getFolder().isWritable() && (attributes & FETCH_MARK_READ) != 0;

        List<ImapPartSpecifier> fullMessage = new ArrayList<ImapPartSpecifier>();
        if (parts != null && !parts.isEmpty()) {
            for (Iterator<ImapPartSpecifier> it = parts.iterator(); it.hasNext(); ) {
                ImapPartSpecifier pspec = it.next();
                if (pspec.isEntireMessage()) {
                    it.remove();  fullMessage.add(pspec);
                }
            }
        }

        Set<ImapMessage> i4set;
        synchronized (mMailbox) {
            i4set = mSession.getFolder().getSubsequence(sequenceSet, byUID);
        }
        boolean allPresent = byUID || !i4set.contains(null);
        i4set.remove(null);

        for (ImapMessage i4msg : i4set) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteArrayOutputStream baosDebug = ZimbraLog.imap.isDebugEnabled() ? new ByteArrayOutputStream() : null;
	        PrintStream result = new PrintStream(new ByteUtil.TeeOutputStream(baos, baosDebug), false, "utf-8");
        	try {
                boolean markMessage = markRead && (i4msg.flags & Flag.BITMASK_UNREAD) != 0;
                boolean empty = true;
                byte[] raw = null;
                MailItem item = null;
                MimeMessage mm = null;
                if (!fullMessage.isEmpty() || !parts.isEmpty() || (attributes & ~FETCH_FROM_CACHE) != 0) {
                    GetItemOperation op = new GetItemOperation(mSession, getContext(), mMailbox, Requester.IMAP, i4msg.msgId, i4msg.getType());
                    op.schedule();
                    item = op.getItem();
                }

                result.print("* " + i4msg.sequence + " FETCH (");
                if ((attributes & FETCH_UID) != 0) {
                    result.print((empty ? "" : " ") + "UID " + i4msg.imapUid);  empty = false;
                }
                if ((attributes & FETCH_INTERNALDATE) != 0) {
                    result.print((empty ? "" : " ") + "INTERNALDATE \"" + mTimeFormat.format(new Date(item.getDate())) + '"');  empty = false;
                }
                if ((attributes & FETCH_RFC822_SIZE) != 0) {
                    result.print((empty ? "" : " ") + "RFC822.SIZE " + i4msg.getSize(item));  empty = false;
                }
                if ((attributes & FETCH_BINARY_SIZE) != 0) {
                    result.print((empty ? "" : " ") + "BINARY.SIZE[] " + i4msg.getSize(item));  empty = false;
                }

                if (!fullMessage.isEmpty()) {
                    raw = ImapMessage.getContent(item);
                    for (ImapPartSpecifier pspec : fullMessage) {
                        result.print(empty ? "" : " ");  pspec.write(result, baos, raw);  empty = false;
                    }
                }

                if (!parts.isEmpty() || (attributes & FETCH_FROM_MIME) != 0) {
                    mm = ImapMessage.getMimeMessage(item, raw);
                    if ((attributes & FETCH_BODY) != 0) {
                        result.print(empty ? "" : " ");  result.print("BODY ");
                        i4msg.serializeStructure(result, mm, false);  empty = false;
                    }
                    if ((attributes & FETCH_BODYSTRUCTURE) != 0) {
                        result.print(empty ? "" : " ");  result.print("BODYSTRUCTURE ");
                        i4msg.serializeStructure(result, mm, true);  empty = false;
                    }
                    if ((attributes & FETCH_ENVELOPE) != 0) {
                        result.print(empty ? "" : " ");  result.print("ENVELOPE ");
                        i4msg.serializeEnvelope(result, mm);  empty = false;
                    }
                    for (ImapPartSpecifier pspec : parts) {
                        result.print(empty ? "" : " ");  pspec.write(result, baos, mm);  empty = false;
                    }
                }
                    
                // 6.4.5: "The \Seen flag is implicitly set; if this causes the flags to
                //         change, they SHOULD be included as part of the FETCH responses."
                // FIXME: optimize by doing a single mark-read op on multiple messages
                if (markMessage) {
                    AlterTagOperation op = new AlterTagOperation(mSession, getContext(), mMailbox, Requester.IMAP, i4msg.msgId, i4msg.getType(), Flag.ID_FLAG_UNREAD, false);
                    op.schedule();
                }
                if ((attributes & FETCH_FLAGS) != 0 || markMessage) {
                    mSession.getFolder().undirtyMessage(i4msg);
                    result.print(empty ? "" : " ");  result.print(i4msg.getFlags(mSession));  empty = false;
                }
            } catch (ImapPartSpecifier.BinaryDecodingException e) {
                // don't write this response line if we're returning NO
                baos = baosDebug = null;
                throw new ImapParseException(tag, "UNKNOWN-CTE", command + "failed: unknown content-type-encoding", false);
            } catch (ServiceException e) {
                ZimbraLog.imap.warn("ignoring error during " + command + ": ", e);
                continue;
            } catch (MessagingException e) {
                ZimbraLog.imap.warn("ignoring error during " + command + ": ", e);
                continue;
            } finally {
                result.write(')');
                if (mOutputStream != null && baos != null) {
                    baos.write(LINE_SEPARATOR_BYTES);
                    mOutputStream.write(baos.toByteArray());
                }
                if (baosDebug != null)
                    ZimbraLog.imap.debug("  S: " + baosDebug);
            }
        }

        sendNotifications(false, false);
        if (allPresent)
        	sendOK(tag, command + " completed");
        else {
        	// RFC 2180 4.1.2: "The server MAY allow the EXPUNGE of a multi-accessed mailbox,
            //                  and on subsequent FETCH commands return FETCH responses only
		    //                  for non-expunged messages and a tagged NO."
        	sendNO(tag, "some of the requested messages no longer exist");
        }
        return CONTINUE_PROCESSING;
    }

    static final byte STORE_REPLACE = (byte) 0x00;
    static final byte STORE_ADD     = (byte) 0x01;
    static final byte STORE_REMOVE  = (byte) 0x02;

    boolean doSTORE(String tag, String sequenceSet, List<String> flagNames, byte operation, boolean silent, boolean byUID) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;
        else if (!mSession.getFolder().isWritable()) {
            sendNO(tag, "mailbox selected READ-ONLY");
            return CONTINUE_PROCESSING;
        }

        String command = (byUID ? "UID STORE" : "STORE");
        List<Tag> newTags = (operation != STORE_REMOVE ? new ArrayList<Tag>() : null);

        Set<ImapMessage> i4set;
        synchronized (mMailbox) {
            i4set = mSession.getFolder().getSubsequence(sequenceSet, byUID);
        }
        boolean allPresent = byUID || !i4set.contains(null);
        i4set.remove(null);

        ImapFolder i4folder = mSession.getFolder();

        try {
            // get set of relevant tags
            List<ImapFlag> i4flags;
            synchronized (mMailbox) {
                i4flags = findOrCreateTags(flagNames, newTags);
            }

            // if we're doing a STORE FLAGS (i.e. replace), precompute the new set of flags for all the affected messages
            long tags = 0;  int flags = Flag.BITMASK_UNREAD;  short sflags = 0;
            if (operation == STORE_REPLACE) {
                for (ImapFlag i4flag : i4flags) {
                    if (Tag.validateId(i4flag.mId))
                        tags = (i4flag.mPositive ? tags | i4flag.mBitmask : tags & ~i4flag.mBitmask);
                    else if (!i4flag.mPermanent)
                        sflags = (byte) (i4flag.mPositive ? sflags | i4flag.mBitmask : sflags & ~i4flag.mBitmask);
                    else
                        flags = (int) (i4flag.mPositive ? flags | i4flag.mBitmask : flags & ~i4flag.mBitmask);
                }
            }

            long checkpoint = System.currentTimeMillis();

            int i = 0;
            List<ImapMessage> i4list = new ArrayList<ImapMessage>(AlterTagOperation.SUGGESTED_BATCH_SIZE);
            List<Integer> idlist = new ArrayList<Integer>(AlterTagOperation.SUGGESTED_BATCH_SIZE);
            for (ImapMessage msg : i4set) {
                // we're sending 'em off in batches of 100
                i4list.add(msg);  idlist.add(msg.msgId);
                if (++i % AlterTagOperation.SUGGESTED_BATCH_SIZE != 0 && i != i4set.size())
                    continue;

                try {
                    // if it was a STORE [+-]?FLAGS.SILENT, temporarily disable notifications
                    if (silent)
                        i4folder.disableNotifications();

                    if (operation == STORE_REPLACE) {
                        // replace real tags and flags on all messages
                        new SetTagsOperation(mSession, getContext(), mMailbox, Requester.IMAP, idlist, MailItem.TYPE_UNKNOWN, flags, tags, null).schedule();
                        // replace session tags on all messages
                        for (ImapMessage i4msg : i4list)
                            i4msg.setSessionFlags(sflags, i4folder);
                    } else if (!i4flags.isEmpty()) {
                        for (ImapFlag i4flag : i4flags) {
                            boolean add = operation == STORE_ADD ^ !i4flag.mPositive;
                            if (i4flag.mPermanent) {
                                // real tag; do a batch update to the DB
                                new AlterTagOperation(mSession, getContext(), mMailbox, Requester.IMAP, idlist, MailItem.TYPE_UNKNOWN, i4flag.mId, add, null).schedule();
                            } else {
                                // session tag; update one-by-one in memory only
                                for (ImapMessage i4msg : i4list)
                                    i4msg.setSessionFlags((short) (add ? i4msg.sflags | i4flag.mBitmask : i4msg.sflags & ~i4flag.mBitmask), i4folder);
                            }
                        }
                    }
                } finally {
                    // if it was a STORE [+-]?FLAGS.SILENT, reenable notifications
                    i4folder.enableNotifications();
                }

                if (!silent) {
                    for (ImapMessage i4msg : i4list) {
                        i4folder.undirtyMessage(i4msg);
                        StringBuilder ntfn = new StringBuilder();
                        ntfn.append(i4msg.sequence).append(" FETCH (").append(i4msg.getFlags(mSession));
                        // 6.4.8: "However, server implementations MUST implicitly include
                        //         the UID message data item as part of any FETCH response
                        //         caused by a UID command..."
                        if (byUID)
                            ntfn.append(" UID ").append(i4msg.imapUid);
                        sendUntagged(ntfn.append(')').toString());
                    }
                } else {
                    // send a gratuitous untagged response to keep pissy clients from closing the socket from inactivity
                    long now = System.currentTimeMillis();
                    if (now - checkpoint > MAXIMUM_IDLE_PROCESSING_MILLIS) {
                        sendIdleUntagged();  checkpoint = now;
                    }
                }

                i4list.clear();  idlist.clear();
            }
        } catch (ServiceException e) {
            deleteTags(newTags);
            ZimbraLog.imap.warn(command + " failed", e);
            sendNO(tag, command + " failed");
            return canContinue(e);
        }

        sendNotifications(false, false);
        // RFC 2180 4.2.1: "If the ".SILENT" suffix is used, and the STORE completed successfully for
        //                  all the non-expunged messages, the server SHOULD return a tagged OK."
        // RFC 2180 4.2.3: "If the ".SILENT" suffix is not used, and a mixture of expunged and non-
        //                  expunged messages are referenced, the server MAY set the flags and return
        //                  a FETCH response for the non-expunged messages along with a tagged NO."
        if (silent || allPresent)
            sendOK(tag, command + " completed");
        else
            sendNO(tag, command + " completed");
        return CONTINUE_PROCESSING;
    }

    boolean doCOPY(String tag, String sequenceSet, String folderName, boolean byUID) throws IOException {
        if (!checkState(tag, ImapSession.STATE_SELECTED))
            return CONTINUE_PROCESSING;

        folderName = ImapFolder.importPath(folderName, mSession);

        String command = (byUID ? "UID COPY" : "COPY");
        String copyuid = "";
        List<MailItem> copies = new ArrayList<MailItem>();

        try {
            GetFolderOperation gfOp = new GetFolderOperation(mSession, getContext(), mMailbox, Requester.IMAP, folderName);
            gfOp.schedule();
            Folder folder = gfOp.getFolder();
            
            if (!ImapFolder.isFolderVisible(folder, mSession)) {
                ZimbraLog.imap.info(command + " failed: folder is hidden: " + folderName);
                sendNO(tag, command + " failed");
                return CONTINUE_PROCESSING;
            } else if (!ImapFolder.isFolderWritable(folder, mSession)) {
                ZimbraLog.imap.info(command + " failed: folder is READ-ONLY: " + folderName);
                sendNO(tag, command + " failed: target mailbox is READ-ONLY");
                return CONTINUE_PROCESSING;
            }

            Set<ImapMessage> i4set;
            synchronized (mMailbox) {
                i4set = mSession.getFolder().getSubsequence(sequenceSet, byUID);
            }
            // RFC 2180 4.4.1: "The server MAY disallow the COPY of messages in a multi-
            //                  accessed mailbox that contains expunged messages."
            if (!byUID && i4set.contains(null)) {
                sendNO(tag, "COPY rejected because some of the requested messages were expunged");
                return CONTINUE_PROCESSING;
            }
            i4set.remove(null);

            long checkpoint = System.currentTimeMillis();
            List<Integer> srcUIDs = extensionEnabled("UIDPLUS") ? new ArrayList<Integer>() : null;
            List<Integer> copyUIDs = extensionEnabled("UIDPLUS") ? new ArrayList<Integer>() : null;

            int i = 0;
            List<ImapMessage> i4list = new ArrayList<ImapMessage>(ImapCopyOperation.SUGGESTED_BATCH_SIZE);
            for (ImapMessage i4msg : i4set) {
                // we're sending 'em off in batches of 50
                i4list.add(i4msg);
                if (++i % ImapCopyOperation.SUGGESTED_BATCH_SIZE != 0 && i != i4set.size())
                    continue;

                ImapCopyOperation op = new ImapCopyOperation(mSession, getContext(), mMailbox, Requester.IMAP, i4list, folder.getId());
                op.schedule();

                copies.addAll(op.getMessages());
                if (op.getMessages().size() != i4list.size())
                    throw ServiceException.FAILURE("mismatch between original and target count during IMAP COPY", null);
                if (srcUIDs != null) {
                    for (ImapMessage source : i4list)
                        srcUIDs.add(source.imapUid);
                    for (MailItem target : op.getMessages())
                        copyUIDs.add(target.getImapUid());
                }

                // send a gratuitous untagged response to keep pissy clients from closing the socket from inactivity
                long now = System.currentTimeMillis();
                if (now - checkpoint > MAXIMUM_IDLE_PROCESSING_MILLIS) {
                    sendIdleUntagged();  checkpoint = now;
                }

                i4list.clear();
            }

            if (srcUIDs != null && srcUIDs.size() > 0)
                copyuid = "[COPYUID " + ImapFolder.getUIDValidity(folder) + ' ' +
                          ImapFolder.encodeSubsequence(srcUIDs) + ' ' +
                          ImapFolder.encodeSubsequence(copyUIDs) + "] ";
        } catch (IOException e) {
            // 6.4.7: "If the COPY command is unsuccessful for any reason, server implementations
            //         MUST restore the destination mailbox to its state before the COPY attempt."
//            deleteMessages(copies);

            ZimbraLog.imap.warn(command + " failed", e);
            sendNO(tag, command + " failed");
            return CONTINUE_PROCESSING;
        } catch (ServiceException e) {
            // 6.4.7: "If the COPY command is unsuccessful for any reason, server implementations
            //         MUST restore the destination mailbox to its state before the COPY attempt."
//            deleteMessages(copies);

            String rcode = "";
            if (e.getCode() == MailServiceException.NO_SUCH_FOLDER) {
                ZimbraLog.imap.info(command + " failed: no such folder: " + folderName);
                if (ImapFolder.isPathCreatable('/' + folderName))
                    rcode = "[TRYCREATE] ";
            } else {
                ZimbraLog.imap.warn(command + " failed", e);
            }
            sendNO(tag, rcode + command + " failed");
            return canContinue(e);
        }

        // RFC 2180 4.4: "COPY is the only IMAP4 sequence number command that is safe to allow
        //                an EXPUNGE response on.  This is because a client is not permitted
        //                to cascade several COPY commands together."
        sendNotifications(true, false);
    	sendOK(tag, copyuid + command + " completed");
        return CONTINUE_PROCESSING;
    }

    private void deleteMessages(List<MailItem> messages) {
        if (messages != null && !messages.isEmpty()) {
            for (MailItem item : messages) {
                try {
                    DeleteOperation op = new DeleteOperation(mSession, getContext(), mMailbox, Requester.IMAP, item.getId(), item.getType());
                    op.schedule();
                } catch (ServiceException e) {
                    ZimbraLog.imap.warn("could not roll back creation of message", e);
                }
            }
        }
    }


    public void sendNotifications(boolean notifyExpunges, boolean flush) throws IOException {
        if (mSession == null || mSession.getFolder() == null || mMailbox == null)
            return;

        // is this the right thing to synchronize on?
        synchronized (mMailbox) {
            // FIXME: notify untagged NO if close to quota limit

            ImapFolder i4folder = mSession.getFolder();
            boolean removed = false, received = i4folder.checkpointSize();
            if (notifyExpunges) {
                for (Integer index : i4folder.collapseExpunged()) {
                    sendUntagged(index + " EXPUNGE");  removed = true;
                }
            }
            i4folder.checkpointSize();

            // notify of any message flag changes
            for (Iterator<ImapMessage> it = i4folder.dirtyIterator(); it.hasNext(); ) {
                ImapMessage i4msg = it.next();
                if (i4msg.isAdded())
                    i4msg.setAdded(false);
                else
                	sendUntagged(i4msg.sequence + " FETCH (" + i4msg.getFlags(mSession) + ')');
            }
            i4folder.clearDirty();

            // FIXME: not handling RECENT

            if (received || removed)
                sendUntagged(i4folder.getSize() + " EXISTS");

            if (flush)
                flushOutput();
        }
    }

    public void dropConnection() {
        dropConnection(true);
    }
    
    public void dropConnection(boolean sendBanner) {
        if (mSession != null) {
            mSession.setHandler(null);
            SessionCache.clearSession(mSession.getSessionId(), mSession.getAccountId());
            mSession = null;
        }

        try {
            if (mOutputStream != null) {
                if (sendBanner) {
                    if (!mGoodbyeSent)
                         sendUntagged(ImapServer.getGoodbye(), true);
                    mGoodbyeSent = true;
                }
                mOutputStream.close();
                mOutputStream = null;
            }
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }
        } catch (IOException e) {
            INFO("exception while closing connection", e);
        }
    }

    protected void notifyIdleConnection() {
        // we can, and do, drop idle connections after the timeout

        // TODO in the TcpServer case, is this duplicated effort with
        // session timeout code that also drops connections?
        ZimbraLog.imap.debug("dropping connection for inactivity");
        dropConnection();
    }

    void sendIdleUntagged() throws IOException                   { sendUntagged("NOOP", true); }

    void sendOK(String tag, String response) throws IOException  { sendResponse(tag, response.equals("") ? "OK" : "OK " + response, true); }
    void sendNO(String tag, String response) throws IOException  { sendResponse(tag, response.equals("") ? "NO" : "NO " + response, true); }
    void sendBAD(String tag, String response) throws IOException { sendResponse(tag, response.equals("") ? "BAD" : "BAD " + response, true); }
    void sendUntagged(String response) throws IOException        { sendResponse("*", response, false); }
    void sendUntagged(String response, boolean flush) throws IOException { sendResponse("*", response, flush); }
    void sendContinuation() throws IOException                   { sendResponse("+", null, true); }
    void sendContinuation(String response) throws IOException    { sendResponse("+", response, true); }
    void flushOutput() throws IOException                        { mOutputStream.flush(); }
    
    private void sendResponse(String status, String msg, boolean flush) throws IOException {
        String response = status + ' ' + (msg == null ? "" : msg);
        if (ZimbraLog.imap.isDebugEnabled())
            ZimbraLog.imap.debug("  S: " + response);
        else if (status.startsWith("BAD"))
            ZimbraLog.imap.info("  S: " + response);
        sendLine(response, flush);
    }

    private void sendLine(String line, boolean flush) throws IOException {
        // FIXME: throw an exception instead?
        if (mOutputStream == null)
            return;
        mOutputStream.write(line.getBytes());
        mOutputStream.write(LINE_SEPARATOR_BYTES);
        if (flush)
            mOutputStream.flush();
    }


    private void INFO(String message, Throwable e) {
        if (ZimbraLog.imap.isInfoEnabled()) ZimbraLog.imap.info(withClientInfo(message), e); 
    }

    private void INFO(String message) {
        if (ZimbraLog.imap.isInfoEnabled()) ZimbraLog.imap.info(withClientInfo(message));
    }

    private StringBuilder withClientInfo(String message) {
        int length = 64;
        if (message != null) length += message.length();
        return new StringBuilder(length).append("[").append(mRemoteAddress).append("] ").append(message);
    }


    public static void main(String[] args) throws IOException, ImapException {
        List<ImapPartSpecifier> parts = new ArrayList<ImapPartSpecifier>();
        List<String> pieces = new ArrayList<String>();
        ImapHandler handler = new ImapHandler(null);
        handler.mOutputStream = System.out;

        System.out.println("> A001 CAPABILITY");
        handler.doCAPABILITY("A001");

        System.out.println("> A002 LOGIN \"user1@example.zimbra.com\" \"test123\"");
        handler.doLOGIN("A002", "user1@example.zimbra.com", "test123");

        System.out.println("> B002 ID NIL");
        handler.doID("B002", null);

        System.out.println("> A003 LIST \"\" \"\"");
        handler.doLIST("A003", "", "");

        System.out.println("> B003 CREATE \"/test/slap\"");
        handler.doCREATE("B003", "/test/slap");

        System.out.println("> A004 LIST \"/\" \"%\"");
        handler.doLIST("A004", "/", "[^/]*");

        System.out.println("> B004 DELETE \"/test/slap\"");
        handler.doDELETE("B004", "/test/slap");

        System.out.println("> A005 LIST \"/\" \"*\"");
        handler.doLIST("A005", "/", ".*");

        System.out.println("> B005 LIST \"/\" \"inbox\"");
        handler.doLIST("B005", "/", "INBOX");

        System.out.println("> C005 LIST \"/\" \"$NBOX+?\"");
        handler.doLIST("C005", "/", "\\$NBOX\\+\\?");

        System.out.println("> D005 LIST \"/\" \"%/sub()\"");
        handler.doLIST("D005", "/", "[^/]*/SUB\\(\\)");

        System.out.println("> A006 SELECT \"/floo\"");
        handler.doSELECT("A006", "/floo");

        System.out.println("> B006 SELECT \"/INBOX\"");
        handler.doSELECT("B006", "/INBOX");

        System.out.println("> A007 STATUS \"/Sent\" (UNSEEN UIDVALIDITY MESSAGES)");
        handler.doSTATUS("A007", "/Sent", STATUS_UNSEEN | STATUS_UIDVALIDITY | STATUS_MESSAGES);

        System.out.println("> B007 STATUS \"/INBOX\" (UNSEEN UIDVALIDITY MESSAGES)");
        handler.doSTATUS("B007", "/INBOX", STATUS_UNSEEN | STATUS_UIDVALIDITY | STATUS_MESSAGES);

        System.out.println("> A008 FETCH 1:3,*:1234 FULL");
        handler.doFETCH("A008", "1:3,*:1234", FETCH_FULL, parts, false);

        System.out.println("> A009 UID FETCH 444,288,602:593 FULL");
        handler.doFETCH("A009", "444,288,602:593", FETCH_FULL, parts, true);

        System.out.println("> A010 FETCH 7 (ENVELOPE BODY.PEEK[1] BODY[HEADER.FIELDS (DATE SUBJECT)]");
        List<String> headers = new LinkedList<String>();  headers.add("date");  headers.add("subject");
        parts.clear();  parts.add(new ImapPartSpecifier("BODY", "1", ""));  parts.add(new ImapPartSpecifier("BODY", "", "HEADER.FIELDS").setHeaders(headers));
        handler.doFETCH("A010", "7", FETCH_ENVELOPE, parts, false);

        System.out.println("> A011 STORE 1 +FLAGS ($MDNSent)");
        List<String> flags = new ArrayList<String>();  flags.add("$MDNSENT");
        handler.doSTORE("A011", "1", flags, STORE_ADD, false, false);

        ImapRequest req = new ImapRequest("X001 LOGIN user1@example.zimbra.com \"\\\\\\\"test123\\\"\\\\\"", handler);
        pieces.clear();  pieces.add(req.readTag());  req.skipSpace();  pieces.add(req.readATOM());  req.skipSpace();  pieces.add(req.readAstring());  req.skipSpace();  pieces.add(req.readAstring());  assert(req.eof());
        System.out.println(pieces);

        req = new ImapRequest("X002 CREATE ~peter/mail/&U,BTFw-/&ZeVnLIqe-", handler);
        pieces.clear();  pieces.add(req.readTag());  req.skipSpace();  pieces.add(req.readATOM());  req.skipSpace();  pieces.add(req.readFolder());  assert(req.eof());
        System.out.println(pieces);
    }
}
