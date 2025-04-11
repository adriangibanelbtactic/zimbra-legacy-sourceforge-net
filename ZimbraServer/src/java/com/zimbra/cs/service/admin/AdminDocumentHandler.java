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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Oct 4, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.session.Session;
import com.zimbra.cs.session.SessionCache;
import com.zimbra.soap.DocumentHandler;
import com.zimbra.soap.Element;
import com.zimbra.soap.SoapFaultException;


/** @author schemers */
public abstract class AdminDocumentHandler extends DocumentHandler {

    public boolean needsAuth(Map context) {
        return true;
    }
    
    public boolean needsAdminAuth(Map context) {
        return true;
    }

    public boolean isAdminCommand() {
        return true;
    }

    protected String[] getProxiedAccountPath()  { return null; }

    protected Element proxyIfNecessary(Element request, Map context) throws ServiceException, SoapFaultException {
        String[] xpath = getProxiedAccountPath();
        if (xpath == null)
            return super.proxyIfNecessary(request, context);
        String acctId = getXPath(request, xpath);
        if (acctId == null)
            return super.proxyIfNecessary(request, context);

        // if there's a remote target acount and we haven't explicitly been told to execute here, proxy.
        if (getZimbraContext(context).getProxyTarget() != null) {
            Account acct = Provisioning.getInstance().getAccountById(acctId);
            if (acct != null && !LOCAL_HOST.equalsIgnoreCase(acct.getAttr(Provisioning.A_zimbraMailHost)))
                return proxyRequest(request, context, acctId);
        }

        return super.proxyIfNecessary(request, context);
    }

    /** Fetches the in-memory {@link Session} object appropriate for this request.
     *  If none already exists, one is created.
     * @return An {@link com.zimbra.cs.session.AdminSession}. */
    public Session getSession(Map context) {
        return getSession(context, SessionCache.SESSION_ADMIN);
    }
}
