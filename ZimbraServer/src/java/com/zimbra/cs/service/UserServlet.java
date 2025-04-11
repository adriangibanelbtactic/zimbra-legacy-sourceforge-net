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

package com.zimbra.cs.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.operation.GetFolderOperation;
import com.zimbra.cs.operation.GetItemOperation;
import com.zimbra.cs.operation.Operation.Requester;
import com.zimbra.cs.service.formatter.*;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.DateUtil;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.ZimbraLog;

/**
 * 
 * <pre>
 *   http://server/service/user/[&tilde;][{username}]/[{folder}]?[{query-params}]
 *          fmt={ics, csv, etc}
 *          id={item-id}
 *          imap_id={item-imap-id}
 *          part={mime-part}
 *          query={search-query}
 *          types={types} // when searching
 *          auth={auth-types}
 *          start={time}
 *          end={time}
 *          sync="1"
 *          
 *             {types}   = comma-separated list.  Legal values are:
 *                         conversation|message|contact|appointment|note
 *                         (default is &quot;conversation&quot;)
 *                          
 *             {auth-types} = comma-separated list. Legal values are:
 *                            co     cookie
 *                            ba     basic auth
 *                            nsc    do not set a cookie when using basic auth
 *                            (default is &quot;co,ba&quot;, i.e. check both)
 *                            
 *            {time} = (time in milliseconds) |
 *                     YYYY/dd/mm |
 *                     mm/dd/YYYY |
 *                     [-]nnnn{minute,hour,day,week,month,year}  // relative
 * </pre>
 */

public class UserServlet extends ZimbraServlet {
    private static final long serialVersionUID = -5313094316561384586L;

    public static final String SERVLET_PATH = "/home";

    public static final String QP_FMT = "fmt"; // format query param

    public static final String QP_ID = "id"; // id query param

    public static final String QP_IMAP_ID = "imap_id"; // IMAP id query param

    public static final String QP_PART = "part"; // part query param
    
    public static final String QP_BODY = "body"; // body query param
    
    public static final String BODY_TEXT = "text"; // return text body

    public static final String BODY_HTML = "html"; // return html body if possible

    public static final String QP_QUERY = "query"; // query query param

    public static final String QP_VIEW = "view"; // view query param    

    public static final String QP_TYPES = "types"; // types

    public static final String QP_START = "start"; // start time

    public static final String QP_END = "end"; // end time
    
    public static final String QP_OFFSET = "offset"; // offset into results
    
    public static final String QP_LIMIT = "limit"; // offset into results

    public static final String QP_AUTH = "auth"; // auth types

    public static final String QP_DISP = "disp"; // disposition (a = attachment, i = inline)

    public static final String AUTH_COOKIE = "co"; // auth by cookie

    public static final String AUTH_BASIC = "ba"; // basic auth

    public static final String AUTH_NO_SET_COOKIE = "nsc"; // don't set auth token cookie after basic auth

    public static final String AUTH_DEFAULT = "co,ba"; // both

    private HashMap<String, Formatter> mFormatters;
    private HashMap<String, Formatter> mDefaultFormatters;

    protected static final String MSGPAGE_BLOCK = "errorpage.attachment.blocked";
    private String mBlockPage = null;

    /** Default maximum upload size for PUT/POST write ops: 10MB. */
    private static final long DEFAULT_MAX_SIZE = 10 * 1024 * 1024;

    /** Returns the REST URL for the account. */
    public static String getRestUrl(Account acct) throws ServiceException {
    	return getServiceUrl(acct, UserServlet.SERVLET_PATH);
    }

    public UserServlet() {
        mFormatters = new HashMap<String, Formatter>();
        addFormatter(new CsvFormatter());
        addFormatter(new VcfFormatter());
        addFormatter(new IcsFormatter());
        addFormatter(new RssFormatter());
        addFormatter(new AtomFormatter());
        addFormatter(new NativeFormatter());
        addFormatter(new ZipFormatter());
        addFormatter(new IfbFormatter());
        addFormatter(new SyncFormatter());
        addFormatter(new WikiFormatter());

        mDefaultFormatters = new HashMap<String, Formatter>();
        for (Formatter fmt : mFormatters.values())
            for (String mimeType : fmt.getDefaultMimeTypes())
                mDefaultFormatters.put(mimeType, fmt);
    }

    private void addFormatter(Formatter f) {
        mFormatters.put(f.getType(), f);
    }

    public Formatter getFormatter(String type) {
        return mFormatters.get(type);
    }

    private Mailbox getTargetMailbox(Context context) throws ServiceException {

        // treat the non-existing target account the same as insufficient permission
        // to access existing item in order to prevent account harvesting.
        Mailbox mbox = null;
        try {
            mbox = context.targetMailbox = MailboxManager.getInstance().getMailboxByAccount(context.targetAccount);
        } catch (Exception e) {
            // ignore IllegalArgumentException or ServiceException being thrown,
            // as we'll throw PERM_DENIED for any error.
        } finally {
            if (mbox == null)
                throw ServiceException.PERM_DENIED("you do not have sufficient permissions");
        }
        return mbox;
    }
    
    private void getAccount(Context context) throws IOException, ServletException {
        try {
            boolean isAdminRequest = isAdminRequest(context);
            // check cookie first
            if (context.cookieAuthAllowed()) {
                context.authAccount = cookieAuthRequest(context.req, context.resp, true);
                if (context.authAccount != null) {
                    context.cookieAuthHappened = true;
                    try {
                        AuthToken at = isAdminRequest ? getAdminAuthTokenFromCookie(context.req, context.resp, true) : getAuthTokenFromCookie(context.req, context.resp, true);
                        context.authTokenCookie = at.getEncoded();
                    } catch (AuthTokenException e) {
                    }
                    return;
                }
            }

            // fallback to basic auth
            if (context.basicAuthAllowed()) {
                context.authAccount = basicAuthRequest(context.req, context.resp, false);
                if (context.authAccount != null) {
                    context.basicAuthHappened = true;
                    // send cookie back if need be. 
                    if (!context.noSetCookie()) {
                        try {
                            context.authTokenCookie = new AuthToken(context.authAccount, isAdminRequest).getEncoded();
                            context.resp.addCookie(new Cookie(isAdminRequest ? COOKIE_ZM_ADMIN_AUTH_TOKEN : COOKIE_ZM_AUTH_TOKEN, context.authTokenCookie));
                        } catch (AuthTokenException e) {
                        }
                    }
                }
                // always return
                return;
            }

            // there is no credential at this point.  assume anonymous public access and continue.
        } catch (ServiceException e) {
            throw new ServletException(e);
        }
    }

    private boolean isAdminRequest(Context context) throws ServiceException {
        int adminPort = Provisioning.getInstance().getLocalServer().getIntAttr(Provisioning.A_zimbraAdminPort, -1);
        return (context.req.getLocalPort() == adminPort);
    }

    private void sendError(Context ctxt, HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        if (ctxt == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        } else if (!ctxt.cookieAuthHappened && ctxt.basicAuthAllowed() && !ctxt.basicAuthHappened) {
            resp.addHeader(WWW_AUTHENTICATE_HEADER, getRealmHeader());
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
        } else {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        Context context = null;
        ZimbraLog.clearContext();
        try {
            context = new Context(req, resp, this);
            if (!checkAuthentication(req, resp, context)) {
                sendError(context, req, resp, "must authenticate");
                return;
            }

            if (isProxyRequest(req, resp, context))
                return;

            // at this point context.authAccount is set either from the Cookie,
            // or from basic auth.  if there was no credential in either the Cookie
            // or basic auth, authAccount is set to anonymous account.
            if (context.authAccount != null)
                ZimbraLog.addAccountNameToContext(context.authAccount.getName());
            ZimbraLog.addIpToContext(context.req.getRemoteAddr());

            doAuthGet(req, resp, context);

        } catch (NoSuchItemException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "no such item");
        } catch (ServiceException se) {
            if (se.getCode() == ServiceException.PERM_DENIED)
                sendError(context, req, resp, se.getMessage());
            else
                throw new ServletException(se);
        } catch (UserServletException e) {
            // add check for ServiceException root cause?
            resp.sendError(e.getHttpStatusCode(), e.getMessage());
        } finally {
            ZimbraLog.clearContext();
        }
    }

    private boolean checkAuthentication(HttpServletRequest req, HttpServletResponse resp, Context context) throws IOException, ServletException, UserServletException {
        // if they specify /~/, we must auth
        if (context.targetAccount == null && context.accountPath.equals("~")) {
            getAccount(context);
            if (context.authAccount == null)
                return false;
            context.targetAccount = context.authAccount;
        }

        // auth is required if we haven't yet authed and they've specified a formatter that requires auth (including write ops).
        boolean authRequired = context.authAccount == null &&
                (context.formatter == null || context.formatter.requiresAuth() || req.getMethod().equalsIgnoreCase("POST"));

        // need this before proxy if we want to support sending cookie from a basic-auth
        if (authRequired) {
            getAccount(context);
            if (context.authAccount == null) {
                context.setAnonymousRequest();
            }
        }

        return true;
    }

    private boolean isProxyRequest(HttpServletRequest req, HttpServletResponse resp, Context context) throws IOException, ServiceException {
        // this should handle both explicit /user/user-on-other-server/ and
        // /user/~/?id={account-id-on-other-server}:id            

        if (context.targetAccount != null && !Provisioning.onLocalServer(context.targetAccount)) {
            try {
                if (context.basicAuthHappened && context.authTokenCookie == null)
                    context.authTokenCookie = new AuthToken(context.authAccount, isAdminRequest(context)).getEncoded();
                Provisioning prov = Provisioning.getInstance();                
                proxyServletRequest(req, resp, prov.getServer(context.targetAccount), context.basicAuthHappened ? context.authTokenCookie : null);
                return true;
            } catch (ServletException e) {
                throw ServiceException.FAILURE("proxy error", e);
            } catch (AuthTokenException e) {
                throw ServiceException.FAILURE("proxy error", e);
            }
        }

        return false;
    }

    private void doAuthGet(HttpServletRequest req, HttpServletResponse resp, Context context)
    throws ServletException, IOException, ServiceException, UserServletException {
        Mailbox mbox = getTargetMailbox(context);
        ZimbraLog.addMboxToContext(mbox.getId());

        if (ZimbraLog.mailbox.isDebugEnabled()) {
            StringBuffer reqURL = context.req.getRequestURL();
            String queryParam = context.req.getQueryString();
            if (queryParam != null) reqURL.append('?').append(queryParam);
            ZimbraLog.mailbox.debug("UserServlet: " + reqURL.toString());
        }

        context.opContext = new OperationContext(context.authAccount, isAdminRequest(context));

        MailItem item = resolveItem(context);

        if (item instanceof Mountpoint) {
            // if the target is a mountpoint, proxy the request on to the resolved target
            proxyOnMountpoint(req, resp, context, (Mountpoint) item);
            return;
        }

        resolveFormatter(context, item);
        if (context.formatter.canBeBlocked()) {
            if (Formatter.checkGlobalOverride(Provisioning.A_zimbraAttachmentsBlocked, context.authAccount)) {
                sendbackBlockMessage(context.req, context.resp);
                return;
            }
        }
        context.formatter.format(context, item);
    }

    /** Adds an item to a folder specified in the URI.  The item content is
     *  provided in the PUT request's body.
     * @see #doPost(HttpServletRequest, HttpServletResponse) */
    public void doPut(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        doPost(req, resp);
    }

    /** Adds an item to a folder specified in the URI.  The item content is
     *  provided in the POST request's body. */
    public void doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        Context context = null;
        ZimbraLog.clearContext();
        try {
            context = new Context(req, resp, this);
            if (!checkAuthentication(req, resp, context)) {
                sendError(context, req, resp, "must authenticate");
                return;
            }

            if (isProxyRequest(req, resp, context))
                return;

            if (context.authAccount != null)
                ZimbraLog.addAccountNameToContext(context.authAccount.getName());
            ZimbraLog.addIpToContext(context.req.getRemoteAddr());

            Mailbox mbox = getTargetMailbox(context);
            ZimbraLog.addMboxToContext(mbox.getId());

            ZimbraLog.mailbox.info("UserServlet (POST): " + context.req.getRequestURL().toString());

            context.opContext = new OperationContext(context.authAccount, isAdminRequest(context));

            GetItemOperation op;
            if (context.itemId != null)
                op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemId.getId(), MailItem.TYPE_FOLDER);
            else 
                op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemPath, MailItem.TYPE_FOLDER);
            op.schedule();
            Folder folder = op.getFolder();

            if (folder instanceof Mountpoint) {
                // if the target is a mountpoint, proxy the request on to the resolved target
                proxyOnMountpoint(req, resp, context, (Mountpoint) folder);
                return;
            }

            // if no format explicitly specified, try to guess it from the Content-Type header
            if (context.format == null) {
                String ctype = context.req.getContentType();
                if (ctype != null) {
                    if (ctype.indexOf(';') != -1)
                        ctype = ctype.substring(0, ctype.indexOf(';'));
                    Formatter fmt = mDefaultFormatters.get(ctype.trim().toLowerCase());
                    if (fmt != null)
                        context.format = fmt.getType();
                }
            }

            // get the POST body content
            long sizeLimit = Provisioning.getInstance().getLocalServer().getLongAttr(Provisioning.A_zimbraFileUploadMaxSize, DEFAULT_MAX_SIZE);
            byte[] body = ByteUtil.getContent(req.getInputStream(), req.getContentLength(), sizeLimit);

            resolveFormatter(context, folder);
            context.formatter.save(body, context, folder);
        } catch (NoSuchItemException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "no such item");
        } catch (ServiceException se) {
            if (se.getCode() == ServiceException.PERM_DENIED)
                sendError(context, req, resp, se.getMessage());
            else
                throw new ServletException(se);
        } catch (UserServletException e) {
            // add check for ServiceException root cause?
            resp.sendError(e.getHttpStatusCode(), e.getMessage());
        } finally {
            ZimbraLog.clearContext();
        }
    }

    /** Determines the <code>format</code> and <code>formatter<code> for the
     *  request, if not already set. */
    private void resolveFormatter(Context context, MailItem target) throws UserServletException {
        if (context.format == null) {
            context.format = defaultFormat(target, context);
            if (context.format == null)
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "unsupported format");
        }

        if (context.formatter == null)
            context.formatter = mFormatters.get(context.format);
        if (context.formatter == null)
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "unsupported format");

    }

    /*
     * Parses the pathInfo, then returns MailItem corresponding to the resource in pathInfo.
     * 
     * If the formatter does not require authentication, e.g. IfbFormatter, 
     * then the path resolution is skipped and returns null.  That's because
     * IfbFormatter does not internally use the resource identified in the URL.
     * It gets the ifb information directly from the Mailbox.
     * 
     * If the formatter declares that the authentication is not required, it's
     * the formatter's responsibility to make sure the MailItems returned to
     * the clients has gone through the access checks.
     * 
     */
    private MailItem resolveItem(Context context) throws ServiceException, UserServletException {
        if (context.formatter != null && !context.formatter.requiresAuth())
            return null;

        MailItem item = null;
        if (context.itemId != null) {
            GetItemOperation op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemId.getId(), MailItem.TYPE_UNKNOWN);
            op.schedule();
            item = op.getItem();
        } else if (context.imapId > 0) {
            // fetch the folder from the path
            GetFolderOperation gfop = new GetFolderOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemPath);
            gfop.schedule();
            Folder folder = gfop.getFolder();
            // and then fetch the item from the "imap_id" query parameter
            GetItemOperation op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.imapId, folder.getId());
            op.schedule();
            item = op.getItem();
        } else {
            try {
                // first, try the full path
                GetItemOperation op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemPath, MailItem.TYPE_UNKNOWN);
                op.schedule();
                item = op.getItem();
            } catch (ServiceException e) {
                if (!(e instanceof NoSuchItemException) && e.getCode() != ServiceException.PERM_DENIED)
                    throw e;

                // no joy.  Perhaps they asked for something like "calendar.csv" -- where calendar was the folder name
                // Try again, minus the extension

                /*
                 * if path == /foo/bar/baz.html, then
                 * format   -> html
                 * and
                 * path ends up as /foo/bar/baz
                 */
                int dot = context.itemPath.lastIndexOf('.');
                if (dot != -1) {
                    if (context.format == null)
                        context.format = context.itemPath.substring(dot + 1);
                    context.itemPath = context.itemPath.substring(0, dot);
                }

                // try again, w/ the extension removed...
                GetItemOperation op = new GetItemOperation(null, context.opContext, context.targetMailbox, Requester.REST, context.itemPath, MailItem.TYPE_UNKNOWN);
                op.schedule();
                item = op.getItem();
            }
        }
        
        if (item == null && context.getQueryString() == null)
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "item not found");
        
        return item;
    }

    private void proxyOnMountpoint(HttpServletRequest req, HttpServletResponse resp, Context context, Mountpoint mpt)
    throws IOException, ServletException, ServiceException, UserServletException {
        String uri = SERVLET_PATH + "/~/?" + QP_ID + '=' + mpt.getOwnerId() + "%3A" + mpt.getRemoteId();
        if (context.format != null)
            uri += '&' + QP_FMT + '=' + URLEncoder.encode(context.format, "UTF-8");
        for (Map.Entry<String, String> entry : HttpUtil.getURIParams(req).entrySet()) {
            String qp = entry.getKey();
            if (!qp.equals(QP_ID) && !qp.equals(QP_FMT))
                uri += '&' + URLEncoder.encode(qp, "UTF-8") + '=' + URLEncoder.encode(entry.getValue(), "UTF-8");
        }

        try {
            if (context.basicAuthHappened && context.authTokenCookie == null) 
                context.authTokenCookie = new AuthToken(context.authAccount, isAdminRequest(context)).getEncoded();
        } catch (AuthTokenException e) {
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "cannot generate auth token");
        }

        Provisioning prov = Provisioning.getInstance();
        Account targetAccount = prov.get(AccountBy.id, mpt.getOwnerId());
        if (targetAccount == null)
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "referenced account not found");

        proxyServletRequest(req, resp, prov.getServer(targetAccount), uri,
                    context.basicAuthHappened ? context.authTokenCookie : null);
    }

    public static class Context {
        public HttpServletRequest req;
        public HttpServletResponse resp;
        public Map<String, String> params;
        public String format;
        public Formatter formatter;        
        public boolean cookieAuthHappened;
        public boolean basicAuthHappened;
        public String accountPath;
        public String authTokenCookie;
        public String itemPath;
        public ItemId itemId;
        public int imapId = -1;
        public boolean sync;
        public Account authAccount;
        public Account targetAccount;
        public Mailbox targetMailbox;
        public OperationContext opContext;
        private long mStartTime = -2;
        private long mEndTime = -2;

        Context(HttpServletRequest request, HttpServletResponse response, UserServlet servlet)
        throws UserServletException, ServiceException {
            Provisioning prov = Provisioning.getInstance();

            this.req = request;
            this.resp = response;
            this.params = HttpUtil.getURIParams(request);

            String pathInfo = request.getPathInfo().toLowerCase();
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("") || !pathInfo.startsWith("/"))
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "invalid path");
            int pos = pathInfo.indexOf('/', 1);
            if (pos == -1)
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "invalid path");

            String checkInternalDispatch = (String) request.getAttribute("zimbra-internal-dispatch");
            if (checkInternalDispatch != null && checkInternalDispatch.equals("yes")) {
                // XXX extra decoding is necessary if the HttpRequest was
                // re-dispatched internally from SetHeaderFilter class in
                // zimbra app.  it appears org.apache.catalina.core.ApplicationHttpRequest
                // does not perform URL decoding on pathInfo.  refer to
                // bug 8159.
                try {
                    pathInfo = URLDecoder.decode(pathInfo, "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                    ZimbraLog.misc.info("cannot decode pathInfo " + pathInfo);
                }
            }
            this.accountPath = pathInfo.substring(1, pos);

            this.itemPath = pathInfo.substring(pos + 1);
            if (itemPath.equals(""))
            	itemPath = "/";
            this.format = this.params.get(QP_FMT);
            String id = this.params.get(QP_ID);
            try {
                this.itemId = id == null ? null : new ItemId(id, null);
            } catch (ServiceException e) {
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "invalid id requested");
            }
            String imap = this.params.get(QP_IMAP_ID);
            try {
                this.imapId = imap == null ? -1 : Integer.parseInt(imap);
            } catch (NumberFormatException nfe) {
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "invalid imap id requested");
            }

            if (this.format != null) {
                this.format = this.format.toLowerCase();
                this.formatter = servlet.getFormatter(this.format);
                if (this.formatter == null)
                    throw new UserServletException(HttpServletResponse.SC_NOT_IMPLEMENTED, "not implemented yet");
            }                

            // see if we can get target account or not
            if (itemId != null && itemId.getAccountId() != null) {
                targetAccount = prov.get(AccountBy.id, itemId.getAccountId());
                return;
            } else if (accountPath.equals("~")) {
                // can't resolve this yet
                return;
            } else if (accountPath.startsWith("~")) {
                accountPath = accountPath.substring(1);
            }
            targetAccount = prov.get(AccountBy.name, accountPath);                
        }

        public long getStartTime() {
            if (mStartTime == -2) {
                String st = params.get(QP_START);
                long defaultStartTime = formatter.getDefaultStartTime();
                mStartTime = (st != null) ? DateUtil.parseDateSpecifier(st, defaultStartTime) : defaultStartTime;
            }
            return mStartTime;
        }

        public long getEndTime() {
            if (mEndTime == -2) {
                String et = params.get(QP_END);
                long defaultEndTime = formatter.getDefaultEndTime();                
                mEndTime = (et != null) ? DateUtil.parseDateSpecifier(et, defaultEndTime) : defaultEndTime;
            }
            return mEndTime;
        }

        public String getQueryString() {
            return params.get(QP_QUERY);
        }

        public boolean cookieAuthAllowed() {
            return getAuth().indexOf(AUTH_COOKIE) != -1;
        }

        public boolean noSetCookie() {
            return authAccount != null && authAccount instanceof ACL.GuestAccount
            || getAuth().indexOf(AUTH_NO_SET_COOKIE) != -1;
        }

        public boolean basicAuthAllowed() {
            return getAuth().indexOf(AUTH_BASIC) != -1;
        }

        public String getAuth() {
            String a = params.get(QP_AUTH);
            return (a == null || a.length() == 0) ? AUTH_DEFAULT : a;
        }

        public boolean hasPart() {
            String p = getPart();
            return p != null && p.length() > 0;
        }

        public String getPart() {
            return params.get(QP_PART);
        }
        
        public boolean hasBody() {
            String p = getBody();
            return p != null;
        }

        public String getBody() {
            return params.get(QP_BODY);
        }

        public boolean hasView() {
            String v = getView();
            return v != null && v.length() > 0;
        }

        public String getView() {
            return params.get(QP_VIEW);
        }

        public int getOffset() {
            String s = params.get(QP_OFFSET);
            if (s != null) { 
                int offset = Integer.parseInt(s);
                if (offset > 0)
                    return offset;
            }
            return 0;
        }
        
        public int getLimit() {
            String s = params.get(QP_LIMIT);
            if (s != null) {
                int limit = Integer.parseInt(s);
                if (limit > 0)
                    return limit;
            }
            return 50;
        }
        

        public String getTypesString() {
            return params.get(QP_TYPES);
        }
        

        public void setAnonymousRequest() {
            authAccount = ACL.ANONYMOUS_ACCT;
        }

        public boolean isAnonymousRequest() {
            return authAccount.equals(ACL.ANONYMOUS_ACCT);
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("account(" + accountPath + ")\n");
            sb.append("itemPath(" + itemPath + ")\n");
            sb.append("foramt(" + format + ")\n");
            return sb.toString();
        }
    }

    protected String getRealmHeader() {
        return "BASIC realm=\"Zimbra\"";
    }

    private String defaultFormat(MailItem item, Context context) {
        if (context.hasPart())
            return "native";

        byte type = MailItem.TYPE_UNKNOWN;
        if (item instanceof Folder)
            type = ((Folder) item).getDefaultView();
        else if (item != null)
            type = item.getType();

        switch (type) {
            case MailItem.TYPE_APPOINTMENT:
            case MailItem.TYPE_TASK:
                return "ics";
            case MailItem.TYPE_CONTACT:
                return item instanceof Folder? "csv" : "vcf";
            case MailItem.TYPE_WIKI:
            case MailItem.TYPE_DOCUMENT:
                return "wiki";
            default:
                return "native";
        }
    }

    /**
     * @param req
     * @param resp
     * @throws IOException
     * @throws ServletException
     */
    private void sendbackBlockMessage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(mBlockPage);
        if (dispatcher != null) {
            dispatcher.forward(req, resp);
            return;
        }
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "The attachment download has been disabled per security policy.");
    }

    public void init() throws ServletException {
        String name = getServletName();
        ZimbraLog.mailbox.info("Servlet " + name + " starting up");
        super.init();
        mBlockPage = getInitParameter(MSGPAGE_BLOCK);
    }

    public void destroy() {
        String name = getServletName();
        ZimbraLog.mailbox.info("Servlet " + name + " shutting down");
        super.destroy();
    }


    public static byte[] getRemoteContent(String authToken, ItemId iid, Map<String, String> params) throws ServiceException {
        return getRemoteResource(authToken, iid, params).getSecond();
    }

    public static byte[] getRemoteContent(String authToken, Account target, String folder, Map<String,String> params) throws ServiceException {
        return getRemoteResource(authToken, target, folder, params).getSecond();
    }

    public static Pair<Header[], byte[]> getRemoteResource(String authToken, ItemId iid, Map<String, String> params) throws ServiceException {
        Account target = Provisioning.getInstance().get(AccountBy.id, iid.getAccountId());
        Map<String, String> pcopy = new HashMap<String, String>(params);
        pcopy.put(QP_ID, iid.toString());
        return getRemoteResource(authToken, target, null, pcopy);
    }

    public static Pair<Header[], byte[]> getRemoteResource(String authToken, Account target, String folder, Map<String,String> params) throws ServiceException {
        // fetch from remote store
        Provisioning prov = Provisioning.getInstance();
        Server server = (target == null ? prov.getLocalServer() : prov.getServer(target));
        int port = server.getIntAttr(Provisioning.A_zimbraMailPort, 0);
        boolean useHTTP = port > 0;
        if (!useHTTP)
            port = server.getIntAttr(Provisioning.A_zimbraMailSSLPort, 0);
        if (port <= 0)
            throw ServiceException.FAILURE("remote server " + server.getName() + " has neither http nor https port enabled", null);
        String hostname = server.getAttr(Provisioning.A_zimbraServiceHostname);

        if (folder == null) {
            folder = "";
        } else {
            if (folder.endsWith("/"))
                folder = folder.substring(0, folder.length() - 1);
            if (folder.startsWith("/"))
                folder = folder.substring(1);
        }

        StringBuffer url = new StringBuffer(useHTTP ? "http" : "https");
        url.append("://").append(hostname).append(':').append(port).append(SERVLET_PATH);
        url.append('/').append(target == null ? "~" : target.getName());
        url.append('/').append(folder).append("/?");
        url.append(QP_AUTH).append('=').append(AUTH_COOKIE);
        if (params != null) {
            for (Map.Entry<String, String> param : params.entrySet())
                url.append('&').append(param.getKey()).append('=').append(param.getValue());
        }

        return getRemoteResource(authToken, hostname, url.toString());
    }
    
    public static Pair<Header[], byte[]> getRemoteResource(String authToken, String hostname, String url) throws ServiceException {
        // create an HTTP client with the same cookies
        HttpState state = new HttpState();
        state.addCookie(new org.apache.commons.httpclient.Cookie(hostname, COOKIE_ZM_AUTH_TOKEN, authToken, "/", null, false));
        HttpClient client = new HttpClient();
        client.setState(state);
        GetMethod get = new GetMethod(url);
        try {
            int statusCode = client.executeMethod(get);
            if (statusCode == HttpStatus.SC_NOT_FOUND)
                throw MailServiceException.NO_SUCH_ITEM(-1);
            else if (statusCode != HttpStatus.SC_OK)
                throw ServiceException.RESOURCE_UNREACHABLE(get.getStatusText(), null);

            Header[] headers = get.getResponseHeaders();
            return new Pair<Header[], byte[]>(headers, get.getResponseBody());
        } catch (HttpException e) {
            throw ServiceException.RESOURCE_UNREACHABLE(get.getStatusText(), e);
        } catch (IOException e) {
            throw ServiceException.RESOURCE_UNREACHABLE(get.getStatusText(), e);
        } finally {
            get.releaseConnection();
        }
    }
}
