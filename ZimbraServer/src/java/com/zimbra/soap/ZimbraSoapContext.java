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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on May 29, 2004
 */
package com.zimbra.soap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import org.dom4j.QName;
import org.mortbay.util.ajax.Continuation;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.session.Session;
import com.zimbra.cs.session.SoapSession;
import com.zimbra.cs.session.SessionCache;
import com.zimbra.cs.session.SoapSession.PushChannel;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.HeaderConstants;
import com.zimbra.common.soap.SoapProtocol;

/**
 * @author schemers
 * 
 * This class models the soap context (the data from the soap envelope)  
 * for a single request 
 * 
 */
public class ZimbraSoapContext {

    final class SessionInfo {
        String sessionId;
        int sequence;
        boolean created;

        SessionInfo(String id, int seqNo) {
            this(id, seqNo, false);
        }
        SessionInfo(String id, int seqNo, boolean newSession) {
            sessionId = id;
            sequence = seqNo;
            created = newSession;
        }

        public String toString()  { return sessionId; }


        private class SoapPushChannel implements SoapSession.PushChannel {
            public void close() {  
                signalNotification(); // don't allow there to be more than one NoOp hanging on a particular account
            }
            public int getLastKnownSeqNo()            { return sequence; }
            public ZimbraSoapContext getSoapContext() { return ZimbraSoapContext.this; }
            public void notificationsReady()          { signalNotification(); }
        }

        public PushChannel getPushChannel() {
            return new SoapPushChannel();
        }
    }

    private static Log sLog = LogFactory.getLog(ZimbraSoapContext.class);

    private static final int MAX_HOP_COUNT = 5;

    private String    mRawAuthToken;
    private AuthToken mAuthToken;
    private String    mAuthTokenAccountId;
    private String    mRequestedAccountId;

    private SoapProtocol mRequestProtocol;
    private SoapProtocol mResponseProtocol;

    private boolean mChangeConstraintType = OperationContext.CHECK_MODIFIED;
    private int     mMaximumChangeId = -1;

    private List<SessionInfo> mSessionInfo = new ArrayList<SessionInfo>();
    private boolean mSessionSuppressed; // don't create a new session for this request
    private boolean mHaltNotifications; // if true, then no notifications are sent to this context
    private boolean mUnqualifiedItemIds;
    private boolean mWaitForNotifications;
    private Continuation mContinuation; // used for blocking requests

    private ProxyTarget mProxyTarget;
    private boolean     mIsProxyRequest;
    private int         mHopCount;
    private boolean     mMountpointTraversed;

    private String      mUserAgent;
    private String      mRequestIP;

    //zdsync: for parsing locally constructed soap requests
    public ZimbraSoapContext(AuthToken authToken, String accountId, SoapProtocol reqProtocol, SoapProtocol respProtocol) throws ServiceException {
    	mAuthToken = authToken;
    	try {
    		mRawAuthToken = authToken.getEncoded();
    	} catch (AuthTokenException x) {
    		throw ServiceException.FAILURE("AuthTokenExcepiton", x);
    	}
    	mAuthTokenAccountId = authToken.getAccountId();
    	mRequestedAccountId = accountId;
    	mRequestProtocol = reqProtocol;
    	mResponseProtocol = respProtocol;
    	
    	mSessionSuppressed = true;
    }
    
    /** Creates a <code>ZimbraSoapContext</code> from another existing
     *  <code>ZimbraSoapContext</code> for use in proxying. */
    public ZimbraSoapContext(ZimbraSoapContext lc, String targetAccountId) throws ServiceException {
        mRawAuthToken = lc.mRawAuthToken;
        mAuthToken = lc.mAuthToken;
        mAuthTokenAccountId = lc.mAuthTokenAccountId;
        mRequestedAccountId = targetAccountId;

        mRequestProtocol = lc.mRequestProtocol;
        mResponseProtocol = lc.mResponseProtocol;

        mSessionSuppressed = true;
        mUnqualifiedItemIds = lc.mUnqualifiedItemIds;

        mHopCount = lc.mHopCount + 1;
        if (mHopCount > MAX_HOP_COUNT)
            throw ServiceException.TOO_MANY_HOPS();
        mMountpointTraversed = lc.mMountpointTraversed;
    }

    /** Creates a <code>ZimbraSoapContext</code> from the <tt>&lt;context></tt>
     *  {@link Element} from the SOAP header.
     *  
     * @param ctxt     The <tt>&lt;context></tt> Element (can be null if not
     *                 present in request)
     * @param context  The engine context, which might contain the auth token
     * @param requestProtocol  The SOAP protocol used for the request */
    public ZimbraSoapContext(Element ctxt, Map context, SoapProtocol requestProtocol) throws ServiceException {
        if (ctxt != null && !ctxt.getQName().equals(HeaderConstants.CONTEXT))
            throw new IllegalArgumentException("expected ctxt, got: " + ctxt.getQualifiedName());

        Provisioning prov = Provisioning.getInstance();

        // figure out if we're explicitly asking for a return format
        mResponseProtocol = mRequestProtocol = requestProtocol;
        Element eFormat = ctxt == null ? null : ctxt.getOptionalElement(HeaderConstants.E_FORMAT);
        if (eFormat != null) {
            String format = eFormat.getAttribute(HeaderConstants.A_TYPE, HeaderConstants.TYPE_XML);
            if (format.equals(HeaderConstants.TYPE_XML) && requestProtocol == SoapProtocol.SoapJS)
                mResponseProtocol = SoapProtocol.Soap12;
            else if (format.equals(HeaderConstants.TYPE_JAVASCRIPT))
                mResponseProtocol = SoapProtocol.SoapJS;
        }

        // find out if we're executing in another user's context
        Account account = null;
        Element eAccount = ctxt == null ? null : ctxt.getOptionalElement(HeaderConstants.E_ACCOUNT);
        if (eAccount != null) {
            String key = eAccount.getAttribute(HeaderConstants.A_BY, null);
            String value = eAccount.getText();

            if (key == null) {
                mRequestedAccountId = null;
            } else if (key.equals(HeaderConstants.BY_NAME)) {
                account = prov.get(AccountBy.name, value);
                if (account == null)
                    throw AccountServiceException.NO_SUCH_ACCOUNT(value);
                mRequestedAccountId = account.getId();
            } else if (key.equals(HeaderConstants.BY_ID)) {
                mRequestedAccountId = value;
            } else {
                throw ServiceException.INVALID_REQUEST("unknown value for by: " + key, null);
            }

            // while we're here, check the hop count to detect loops
            mHopCount = (int) Math.max(eAccount.getAttributeLong(HeaderConstants.A_HOPCOUNT, 0), 0);
            if (mHopCount > MAX_HOP_COUNT)
                throw ServiceException.TOO_MANY_HOPS();
            mMountpointTraversed = eAccount.getAttributeBool(HeaderConstants.A_MOUNTPOINT, false);
        } else {
            mRequestedAccountId = null;
        }

        // check for auth token in engine context if not in header  
        mRawAuthToken = (ctxt == null ? null : ctxt.getAttribute(HeaderConstants.E_AUTH_TOKEN, null));
        if (mRawAuthToken == null)
            mRawAuthToken = (String) context.get(SoapServlet.ZIMBRA_AUTH_TOKEN);

        // parse auth token and check validity
        if (mRawAuthToken != null && !mRawAuthToken.equals("")) {
            try {
                mAuthToken = AuthToken.getAuthToken(mRawAuthToken);
                if (mAuthToken.isExpired())
                    throw ServiceException.AUTH_EXPIRED();
                mAuthTokenAccountId = mAuthToken.getAccountId();
            } catch (AuthTokenException e) {
                // ignore and leave null
                mAuthToken = null;
                if (sLog.isDebugEnabled())
                    sLog.debug("ZimbraContext AuthToken error: " + e.getMessage(), e);
            }
        }

        // constrain operations if we know the max change number the client knows about
        Element change = (ctxt == null ? null : ctxt.getOptionalElement(HeaderConstants.E_CHANGE));
        if (change != null) {
            try {
                String token = change.getAttribute(HeaderConstants.A_CHANGE_ID, "-1");
                int delimiter = token.indexOf('-');

                mMaximumChangeId = Integer.parseInt(delimiter < 1 ? token : token.substring(0, delimiter));
                if (change.getAttribute(HeaderConstants.A_TYPE, HeaderConstants.CHANGE_MODIFIED).equals(HeaderConstants.CHANGE_MODIFIED))
                    mChangeConstraintType = OperationContext.CHECK_MODIFIED;
                else
                    mChangeConstraintType = OperationContext.CHECK_CREATED;
            } catch (NumberFormatException nfe) { }
        }

        // if the caller specifies an execution host or if we're on the wrong host, proxy
        mIsProxyRequest = false;
        String targetServerId = ctxt == null ? null : ctxt.getAttribute(HeaderConstants.E_TARGET_SERVER, null);
        if (targetServerId != null) {
            HttpServletRequest req = (HttpServletRequest) context.get(SoapServlet.SERVLET_REQUEST);
            if (req != null) {
                mProxyTarget = new ProxyTarget(targetServerId, mRawAuthToken, req);
                mIsProxyRequest = !mProxyTarget.isTargetLocal();
            } else {
                sLog.warn("Missing SERVLET_REQUEST key in request context");
            }
        }

        // look for the notification sequence id, for notification reliability
        //   <notify seq="nn">
        int seqNo = -1;
        Element notify = (ctxt == null ? null : ctxt.getOptionalElement(HeaderConstants.E_NOTIFY));
        if (notify != null) 
            seqNo = (int) notify.getAttributeLong(HeaderConstants.A_SEQNO, 0);

        // record session-related info and validate any specified sessions
        //   (don't create new sessions yet)
        if (ctxt != null) {
            mHaltNotifications = ctxt.getOptionalElement(HeaderConstants.E_NO_NOTIFY) != null;
            mSessionSuppressed = ctxt.getOptionalElement(HeaderConstants.E_NO_SESSION) != null;
            // if sessions are enabled, create a SessionInfo to encapsulate (will fetch the Session object during the request)
            if (!mHaltNotifications && !mSessionSuppressed) {
                for (Element session : ctxt.listElements(HeaderConstants.E_SESSION_ID)) {
                    String sessionId = null;
                    if ("".equals(sessionId = session.getTextTrim()))
                        sessionId = session.getAttribute(HeaderConstants.A_ID, null);
                    if (sessionId != null)
                        mSessionInfo.add(new SessionInfo(sessionId, (int) session.getAttributeLong(HeaderConstants.A_SEQNO, seqNo)));
                }
            }
        }

        // temporary hack: don't qualify item ids in reponses, if so requested
        mUnqualifiedItemIds = (ctxt != null && ctxt.getOptionalElement(HeaderConstants.E_NO_QUALIFY) != null);

        // Handle user agent if specified by the client.  The user agent string is formatted
        // as "name/version".
        Element userAgent = (ctxt == null ? null : ctxt.getOptionalElement(HeaderConstants.E_USER_AGENT));
        if (userAgent != null) {
            String name = userAgent.getAttribute(HeaderConstants.A_NAME, null);
            String version = userAgent.getAttribute(HeaderConstants.A_VERSION, null);
            if (!StringUtil.isNullOrEmpty(name)) {
                mUserAgent = name;
                if (!StringUtil.isNullOrEmpty(version))
                    mUserAgent = mUserAgent + "/" + version;
            }
        }

        mRequestIP = (String)context.get(SoapEngine.REQUEST_IP);
    }

    /** Records in this context that we've traversed a mountpoint to get here.
     *  Enforces the "cannot mount a mountpoint" rule forbidding one link from
     *  pointing to another link.  The rationale for this limitation is that
     *  we want to avoid having the indirectly addressed resource completely
     *  change without the user knowing, and we want to know that the target
     *  of the link is also the owner of the items found therein.  Basically,
     *  things are just simpler this way.
     * 
     * @throws ServiceException   The following error codes are possible:<ul>
     *    <li><tt>service.PERM_DENIED</tt> - if you try to traverse two
     *        mountpoints in the course of a single proxy</ul> */
    public void recordMountpointTraversal() throws ServiceException {
        if (mMountpointTraversed)
            throw ServiceException.PERM_DENIED("cannot mount a mountpoint");
        mMountpointTraversed = true;
    }

    public String toString() {
        String sessionPart = "";
        if (!mSessionSuppressed)
            sessionPart = ", sessions=" + mSessionInfo;
        return "LC(mbox=" + mAuthTokenAccountId + sessionPart + ")";
    }

    /** Returns the account id the request is supposed to operate on.  This
     *  can be explicitly specified in the supplied context; it defaults to
     *  the account id in the auth token. */
    public String getRequestedAccountId() {
        return (mRequestedAccountId != null ? mRequestedAccountId : mAuthTokenAccountId);
    } 

    /** Returns the id of the account in the auth token.  Operations should
     *  normally use {@link #getRequestedAccountId}, as that's the context
     *  that the operations is executing in. */
    public String getAuthtokenAccountId() {
        return mAuthTokenAccountId;
    }

    /** Sets the id of the authenticated account.  This should only be called
     *  in the course of some flavor of <tt>Auth</tt> request. */
    void setAuthenticatedAccountId(String accountId) {
        mAuthTokenAccountId = accountId;
    }

    /** Returns whether the authenticated user is the same as the user whose
     *  context the operation is set to execute in. */
    public boolean isDelegatedRequest() {
        return !mAuthTokenAccountId.equalsIgnoreCase(getRequestedAccountId());
    }

    public boolean isNotificationEnabled() {
        return !mHaltNotifications && !mSessionSuppressed;
    }

    /** Returns a list of the {@link SessionInfo} items associated with this
     *  SOAP request.  SessionInfo objects correspond to either:<ul>
     *  <li>sessions specified in a <tt>&lt;sessionId></tt> element in the 
     *      <tt>&lt;context></tt> SOAP header block, or</li>
     *  <li>new sessions created during the course of the SOAP call</li></ul> */
    List<SessionInfo> getReferencedSessions() {
        return mSessionInfo;
    }
    
    /**
     * @return TRUE if any of our referenced sessions are brand-new.  This special-case API
     *         is used to short-circuit blocking handlers so that they return immediately
     *         to send a <refresh> block if one is necessary.
     */
    public boolean hasCreatedSession() {
        for (SessionInfo sinfo : getReferencedSessions()) {
            if (sinfo.created)
                return true;
        }
        return false;
    }

    public boolean beginWaitForNotifications(Continuation continuation) throws ServiceException {
        boolean someBlocked = false;
        boolean someReady = false;
        mWaitForNotifications = true;
        mContinuation = continuation;
        
        // synchronized against 
        for (SessionInfo sinfo : mSessionInfo) {
            Session session = SessionCache.lookup(sinfo.sessionId, mAuthTokenAccountId);
            if (session instanceof SoapSession) {
                SoapSession ss = (SoapSession) session;
                SoapSession.RegisterNotificationResult result = ss.registerNotificationConnection(sinfo.getPushChannel());
                switch (result) {
                    case NO_NOTIFY: break;
                    case DATA_READY: someReady = true; break;
                    case BLOCKING: someBlocked = true; break;
                }
            }
        }

        return (someBlocked && !someReady);
    }

    /**
     * Called by the Session object if a new notification comes in 
     */
    synchronized public void signalNotification() {
        mWaitForNotifications = false;
        mContinuation.resume();
    }
    
    synchronized public boolean waitingForNotifications() {
        return mWaitForNotifications;
    }

    /** Serializes this object for use in a proxied SOAP request.  The
     *  attributes encapsulated by the <code>ZimbraContext</code> -- the
     *  response protocol, the auth token, etc. -- are carried forward.
     *  Notification is expressly declined. */
    Element toProxyCtxt() {
        return toProxyCtxt(mRequestProtocol);
    }

    /** Serializes this object for use in a proxied SOAP request.  The
     *  attributes encapsulated by the <code>ZimbraContext</code> -- the
     *  response protocol, the auth token, etc. -- are carried forward.
     *  Notification is expressly declined. */
    Element toProxyCtxt(SoapProtocol proto) {
        Element ctxt = proto.getFactory().createElement(HeaderConstants.CONTEXT);
        if (mRawAuthToken != null)
            ctxt.addAttribute(HeaderConstants.E_AUTH_TOKEN, mRawAuthToken, Element.Disposition.CONTENT);
        if (mResponseProtocol != mRequestProtocol)
            ctxt.addElement(HeaderConstants.E_FORMAT).addAttribute(HeaderConstants.A_TYPE, mResponseProtocol == SoapProtocol.SoapJS ? HeaderConstants.TYPE_JAVASCRIPT : HeaderConstants.TYPE_XML);
        Element eAcct = ctxt.addElement(HeaderConstants.E_ACCOUNT).addAttribute(HeaderConstants.A_HOPCOUNT, mHopCount).addAttribute(HeaderConstants.A_MOUNTPOINT, mMountpointTraversed);
        if (mRequestedAccountId != null && !mRequestedAccountId.equalsIgnoreCase(mAuthTokenAccountId))
            eAcct.addAttribute(HeaderConstants.A_BY, HeaderConstants.BY_ID).setText(mRequestedAccountId);
        if (mSessionSuppressed)
            ctxt.addUniqueElement(HeaderConstants.E_NO_SESSION);
        if (mUnqualifiedItemIds)
            ctxt.addUniqueElement(HeaderConstants.E_NO_QUALIFY);
        return ctxt;
    }

    /** Serializes a {@link Session} object to return it to a client.  The serialized
     *  XML representation of a Session is:<p>
     *      <tt>&lt;sessionId [type="admin"] id="12">12&lt;/sessionId></tt>
     * 
     * @param parent   The {@link Element} to add the serialized Session to.
     * @param sessionId TODO
     * @param sessionType TODO
     * @param unique   Whether there can be more than one Session serialized to the
     *                 <tt>parent</tt> Element.
     * @return The created <tt>&lt;sessionId></tt> Element. */
    public static Element encodeSession(Element parent, String sessionId, Session.Type sessionType, boolean unique) {
        String typeStr = (sessionType == Session.Type.ADMIN ? HeaderConstants.SESSION_ADMIN : null);

        Element elt = unique ? parent.addUniqueElement(HeaderConstants.E_SESSION_ID) : parent.addElement(HeaderConstants.E_SESSION_ID);
        elt.addAttribute(HeaderConstants.A_TYPE, typeStr).addAttribute(HeaderConstants.A_ID, sessionId).setText(sessionId);
        return elt;
    }

    public SoapProtocol getRequestProtocol()   { return mRequestProtocol; }
    public SoapProtocol getResponseProtocol()  { return mResponseProtocol; }

    public Element createElement(String name)  { return mResponseProtocol.getFactory().createElement(name); }
    public Element createElement(QName qname)  { return mResponseProtocol.getFactory().createElement(qname); }

    public Element createRequestElement(String name)  { return mRequestProtocol.getFactory().createElement(name); }
    public Element createRequestElement(QName qname)  { return mRequestProtocol.getFactory().createElement(qname); }

    /** Returns the parsed {@link AuthToken} for this SOAP request.  This can
     *  come either from an HTTP cookie attached to the SOAP request or from
     *  an <tt>&lt;authToken></tt> element in the SOAP Header. */
    public AuthToken getAuthToken() {
        return mAuthToken;
    }

    /** Returns the raw, encoded {@link AuthToken} for this SOAP request.
     *  This can come either from an HTTP cookie attached to the SOAP request
     *  or from an <tt>&lt;authToken></tt> element in the SOAP Header. */
    public String getRawAuthToken() {
        return mRawAuthToken;
    }

    public ProxyTarget getProxyTarget() {
        return mProxyTarget;
    }

    public boolean isProxyRequest() {
        return mIsProxyRequest;
    }

    /** Returns the name and version of the client that's making the current
     *  request, in the format "name/version". s*/
    public String getUserAgent() {
        return mUserAgent;
    }

    public String getRequestIP() {
        return mRequestIP;
    }

    boolean getChangeConstraintType() {
        return mChangeConstraintType;
    }

    int getChangeConstraintLimit() {
        return mMaximumChangeId;
    }

    public boolean wantsUnqualifiedIds() {
        return mUnqualifiedItemIds;
    }
}
