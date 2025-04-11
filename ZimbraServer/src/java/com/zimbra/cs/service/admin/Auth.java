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
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.*;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.Provisioning.DomainBy;
import com.zimbra.cs.session.Session;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class Auth extends AdminDocumentHandler {

    /** Returns (or creates) the in-memory {@link Session} object appropriate
     *  for this request.<p>
     * 
     *  Auth commands do not create a session by default, as issues with the 
     *  ordering of operations might cause the new session to be for the old
     *  credentials rather than for the new ones.
     * 
     * @return <code>null</code> in all cases */
    public Session getSession(Map context) {
        return null;
    }

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);

        String name = request.getAttribute(AdminConstants.E_NAME);
		String password = request.getAttribute(AdminConstants.E_PASSWORD);
		Provisioning prov = Provisioning.getInstance();
		Account acct = null;
        boolean isDomainAdmin = false;
        
        Element virtualHostEl = request.getOptionalElement(AccountConstants.E_VIRTUAL_HOST);
        String virtualHost = virtualHostEl == null ? null : virtualHostEl.getText().toLowerCase();
 
        try {
            
            if (name.indexOf("@") == -1) {

                acct = prov.get(AccountBy.adminName, name);
                
                if (acct == null) {
                    if (virtualHost != null) {
                        Domain d = prov.get(DomainBy.virtualHostname, virtualHost);
                        if (d != null)
                            name = name + "@" + d.getName();
                    }                    
                } 
            }

            if (acct == null)
                acct = prov.get(AccountBy.name, name);

            if (acct == null)
                throw AccountServiceException.AUTH_FAILED(name);
        
            ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                    new String[] {"cmd", "AdminAuth","account", name})); 
        
            prov.authAccount(acct, password, "soap");
            
            isDomainAdmin = acct.getBooleanAttr(Provisioning.A_zimbraIsDomainAdminAccount, false);
            boolean isAdmin= acct.getBooleanAttr(Provisioning.A_zimbraIsAdminAccount, false);            
            boolean ok = (isDomainAdmin || isAdmin);
            if (!ok) 
                    throw ServiceException.PERM_DENIED("not an admin account");

        } catch (ServiceException se) {
            ZimbraLog.security.warn(ZimbraLog.encodeAttrs(
                    new String[] {"cmd", "AdminAuth","account", name, "error", se.getMessage()}));    
            throw se;
        }

        Element response = lc.createElement(AdminConstants.AUTH_RESPONSE);
        String token;
        AuthToken at = new AuthToken(acct, true);
        try {
            token = at.getEncoded();
        } catch (AuthTokenException e) {
            throw  ServiceException.FAILURE("unable to encode auth token", e);
        }
        response.addAttribute(AdminConstants.E_AUTH_TOKEN, token, Element.Disposition.CONTENT);
        response.addAttribute(AdminConstants.E_LIFETIME, at.getExpires() - System.currentTimeMillis(), Element.Disposition.CONTENT);
        response.addElement(AdminConstants.E_A).addAttribute(AdminConstants.A_N, Provisioning.A_zimbraIsDomainAdminAccount).setText(isDomainAdmin+"");
        Session session = lc.getNewSession(acct.getId(), Session.Type.ADMIN);
        if (session != null)
            ZimbraSoapContext.encodeSession(response, session, true);
		return response;
	}

    public boolean needsAuth(Map<String, Object> context) {
        // can't require auth on auth request
        return false;
    }

    public boolean needsAdminAuth(Map<String, Object> context) {
        // can't require auth on auth request
        return false;
    }
}
