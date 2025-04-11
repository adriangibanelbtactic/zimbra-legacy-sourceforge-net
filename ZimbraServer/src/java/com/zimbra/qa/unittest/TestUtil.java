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

package com.zimbra.qa.unittest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestResult;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.soap.SoapProvisioning;
import com.zimbra.cs.client.LmcSession;
import com.zimbra.cs.client.soap.LmcAdminAuthRequest;
import com.zimbra.cs.client.soap.LmcAdminAuthResponse;
import com.zimbra.cs.client.soap.LmcAuthRequest;
import com.zimbra.cs.client.soap.LmcAuthResponse;
import com.zimbra.cs.client.soap.LmcSoapClientException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.db.DbResults;
import com.zimbra.cs.db.DbUtil;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.lmtpserver.utils.LmtpClient;
import com.zimbra.cs.localconfig.DebugConfig;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.cs.zclient.ZEmailAddress;
import com.zimbra.cs.zclient.ZGetMessageParams;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMessage;
import com.zimbra.cs.zclient.ZSearchHit;
import com.zimbra.cs.zclient.ZSearchParams;
import com.zimbra.cs.zclient.ZMailbox.ZOutgoingMessage;
import com.zimbra.cs.zclient.ZMailbox.ZOutgoingMessage.MessagePart;
import com.zimbra.soap.SoapFaultException;

/**
 * @author bburtin
 */
public class TestUtil {

    public static Account getAccount(String userName)
    throws ServiceException {
        String address = getAddress(userName);
        Account account = Provisioning.getInstance().get(AccountBy.name, address);
        if (account == null) {
            throw new IllegalArgumentException("Could not find account for '" + address + "'");
        }
        return account;
    }

    public static String getDomain()
    throws ServiceException {
        Config config = Provisioning.getInstance().getConfig();
        String domain = config.getAttr(Provisioning.A_zimbraDefaultDomainName, null);
        assert(domain != null && domain.length() > 0);
        return domain;
    }

    public static Mailbox getMailbox(String userName)
    throws ServiceException {
        Account account = getAccount(userName);
        return MailboxManager.getInstance().getMailboxByAccount(account);
    }

    public static String getAddress(String userName)
    throws ServiceException {
        return userName + "@" + getDomain();
    }

    public static String getSoapUrl() {
        String scheme;
        int port;
        try {
            port = Provisioning.getInstance().getLocalServer().getIntAttr(Provisioning.A_zimbraMailPort, 0);
            if (port > 0) {
                scheme = "http";
            } else {
                port = Provisioning.getInstance().getLocalServer().getIntAttr(Provisioning.A_zimbraMailSSLPort, 0);
                scheme = "https";
            }
        } catch (ServiceException e) {
            ZimbraLog.test.error("Unable to get user SOAP port", e);
            port = 80;
            scheme = "http";
        }
        return scheme + "://localhost:" + port + ZimbraServlet.USER_SERVICE_URI;
    }

    public static String getAdminSoapUrl() {
        int port;
        try {
            port = Provisioning.getInstance().getLocalServer().getIntAttr(Provisioning.A_zimbraAdminPort, 0);
        } catch (ServiceException e) {
            ZimbraLog.test.error("Unable to get admin SOAP port", e);
            port = LC.zimbra_admin_service_port.intValue();
        }
        return "https://localhost:" + port + ZimbraServlet.ADMIN_SERVICE_URI;
    }

    public static LmcSession getSoapSession(String userName)
    throws ServiceException, LmcSoapClientException, IOException, SoapFaultException
    {
        LmcAuthRequest auth = new LmcAuthRequest();
        auth.setUsername(getAddress(userName));
        auth.setPassword("test123");
        LmcAuthResponse authResp = (LmcAuthResponse) auth.invoke(getSoapUrl());
        return authResp.getSession();
    }

    public static LmcSession getAdminSoapSession()
    throws Exception
    {
        // Authenticate
        LmcAdminAuthRequest auth = new LmcAdminAuthRequest();
        auth.setUsername(getAddress("admin"));
        auth.setPassword("test123");
        LmcAdminAuthResponse authResp = (LmcAdminAuthResponse) auth.invoke(getAdminSoapUrl());
        return authResp.getSession();
    }

    private static String[] MESSAGE_TEMPLATE_LINES = {
        "From: Jeff Spiccoli <jspiccoli@${DOMAIN}>",
        "To: Test User 1 <user1@${DOMAIN}>",
        "Subject: ${SUBJECT}",
        "Date: Mon, 28 Mar 2005 10:21:10 -0700",
        "X-Zimbra-Received: Mon, 28 Mar 2005 10:21:1${MESSAGE_NUM} -0700",
        "Content-Type: text/plain",
        "",
        "Dude,",
        "",
        "All I need are some tasty waves, a cool buzz, and I'm fine.",
        "",
        "Jeff",
        "",
        "(${SUBJECT} ${MESSAGE_NUM})"
    };

    private static String MESSAGE_TEMPLATE = StringUtil.join("\n", MESSAGE_TEMPLATE_LINES);

    public static Message insertMessage(Mailbox mbox, int messageNum, String subject)
    throws Exception {
        String message = getTestMessage(messageNum, subject);
        ParsedMessage pm = new ParsedMessage(message.getBytes(), System.currentTimeMillis(), false);
        pm.analyze();
        return mbox.addMessage(null, pm, Mailbox.ID_FOLDER_INBOX, false, Flag.BITMASK_UNREAD, null);
    }

    private static String getTestMessage(int messageNum, String subject)
    throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("MESSAGE_NUM", new Integer(messageNum));
        vars.put("SUBJECT", subject);
        vars.put("DOMAIN", getDomain());
        return StringUtil.fillTemplate(MESSAGE_TEMPLATE, vars);
    }

    public static void insertMessageLmtp(int messageNum, String subject, String recipient, String sender)
    throws Exception {
        String message = getTestMessage(messageNum, subject);
        LmtpClient lmtp = new LmtpClient("localhost", 7025);
        List<String> recipients = new ArrayList<String>();
        recipients.add(recipient);
        lmtp.sendMessage(message.getBytes(), recipients, sender, "TestUtil");
        lmtp.close();
    }

    public static void sendMessage(ZMailbox senderMbox, String recipientName, String subject, String body)
    throws Exception {
        sendMessage(senderMbox, recipientName, subject, body, null);
    }
    
    public static void sendMessage(ZMailbox senderMbox, String recipientName, String subject, String body, String attachmentUploadId)
    throws Exception {
        ZOutgoingMessage msg = new ZOutgoingMessage();
        List<ZEmailAddress> addresses = new ArrayList<ZEmailAddress>();
        addresses.add(new ZEmailAddress(TestUtil.getAddress(recipientName),
            null, null, ZEmailAddress.EMAIL_TYPE_TO));
        msg.setAddresses(addresses);
        msg.setSubject(subject);
        List<MessagePart> parts = new ArrayList<MessagePart>();
        parts.add(new MessagePart("text/plain", body));
        msg.setMessageParts(parts);
        msg.setAttachmentUploadId(attachmentUploadId);
        senderMbox.sendMessage(msg, null, false);
    }

    /**
     * Searches a mailbox and returns the id's of all matching items.
     */
    public static List<Integer> search(Mailbox mbox, String query, byte type)
    throws Exception {
        ZimbraLog.test.debug("Running search: '" + query + "', type=" + type);
        byte[] types = new byte[1];
        types[0] = type;

        List<Integer> ids = new ArrayList<Integer>();
        ZimbraQueryResults r = mbox.search(new Mailbox.OperationContext(mbox), query, types, MailboxIndex.SortBy.DATE_DESCENDING, 100);
        while (r.hasNext()) {
            ZimbraHit hit = r.getNext();
            ids.add(new Integer(hit.getItemId()));
        }
        return ids;

    }

    public static List<ZMessage> search(ZMailbox mbox, String query)
    throws Exception {
        List<ZMessage> msgs = new ArrayList<ZMessage>();
        ZSearchParams params = new ZSearchParams(query);
        params.setTypes(ZSearchParams.TYPE_MESSAGE);

        for (ZSearchHit hit : mbox.search(params).getHits()) {
            ZGetMessageParams msgParams = new ZGetMessageParams();
            msgParams.setId(hit.getId());
            msgs.add(mbox.getMessage(msgParams));
        }
        return msgs;
    }
    
    public static ZMessage waitForMessage(ZMailbox mbox, String query)
    throws Exception {
        for (int i = 1; i <= 20; i++) {
            List<ZMessage> msgs = search(mbox, query);
            if (msgs.size() == 1) {
                return msgs.get(0);
            }
            if (msgs.size() > 1) {
                Assert.fail("Unexpected number of messages (" + msgs.size() + ") returned by query '" + query + "'");
            }
            Thread.sleep(500);
        }
        Assert.fail("Message for query '" + query + "' never arrived.  Either the MTA is not running or the test failed.");
        return null;
    }

    /**
     * Returns a folder with the given path, or <code>null</code> if the folder
     * doesn't exist.
     */
    public static Folder getFolderByPath(Mailbox mbox, String path)
    throws Exception {
        Folder folder = null;
        try {
            folder = mbox.getFolderByPath(null, path);
        } catch (MailServiceException e) {
            if (e.getCode() != MailServiceException.NO_SUCH_FOLDER) {
                throw e;
            }
        }
        return folder;
    }

    /**
     * Delete all messages, tags and folders in the user's mailbox
     * whose subjects contain the given substring. 
     */
    public static void deleteTestData(String userName, String subjectSubstring)
    throws Exception {
        deleteTestData(userName, subjectSubstring, MailItem.TYPE_MESSAGE);
        deleteTestData(userName, subjectSubstring, MailItem.TYPE_TAG);
        deleteTestData(userName, subjectSubstring, MailItem.TYPE_FOLDER);
    }

    private static void deleteTestData(String userName, String subjectSubstring, byte type)
    throws Exception {
        Mailbox mbox = TestUtil.getMailbox(userName);
        String nameColumn = type == MailItem.TYPE_MESSAGE ? "subject" : "name";
        String sql =
            "SELECT id " +
            "FROM " + DbMailItem.getMailItemTableName(mbox) +
            " WHERE " +
            (!DebugConfig.disableMailboxGroup ? "mailbox_id = " + mbox.getId() + " AND " : "") +
            "type = " + type + " AND " + nameColumn + " LIKE '%" + subjectSubstring + "%' ";
        DbResults results = DbUtil.executeQuery(sql);
        while (results.next()) {
            int id = results.getInt(1);
            try {
                mbox.getItemById(null, id, type);
                mbox.delete(null, id, type);
                ZimbraLog.test.debug("Deleted item " + id + ", type " + type);
            } catch (NoSuchItemException e) {
                ZimbraLog.test.debug("Unable to delete item " + id + ".  Must have been deleted by parent.");
            }
        }
    }

    /**
     * Runs a test and writes the output to the logfile.
     */
    public static TestResult runTest(Test t) {
        return runTest(t, null);
    }

    /**
     * Runs a test and writes the output to the specified
     * <code>OutputStream</code>.
     */
    public static TestResult runTest(Test t, OutputStream outputStream) {
        ZimbraLog.test.debug("Starting unit test suite");

        long suiteStart = System.currentTimeMillis();
        TestResult result = new TestResult();
        ZimbraTestListener listener = new ZimbraTestListener();
        result.addListener(listener);
        t.run(result);

        double seconds = (double) (System.currentTimeMillis() - suiteStart) / 1000;
        String msg = String.format(
            "Unit test suite finished in %.2f seconds.  %d errors, %d failures.",
            seconds, result.errorCount(), result.failureCount());
        ZimbraLog.test.info(msg);

        if (outputStream != null) {
            try {
                outputStream.write(msg.getBytes());
            } catch (IOException e) {
                ZimbraLog.test.error(e.toString());
            }
        }

        return result;
    }
    
    public static ZMailbox getZMailbox(String username)
    throws Exception {
        ZMailbox.Options options = new ZMailbox.Options();
        options.setAccount(getAddress(username));
        options.setAccountBy(AccountBy.name);
        options.setPassword("test123");
        options.setUri(getSoapUrl());
        return ZMailbox.getMailbox(options);
    }
    
    public static SoapProvisioning getSoapProvisioning()
    throws Exception {
        SoapProvisioning sp = new SoapProvisioning();
        sp.soapSetURI("https://localhost:7071" + ZimbraServlet.ADMIN_SERVICE_URI);
        sp.soapZimbraAdminAuthenticate();
        return sp;
    }
}
