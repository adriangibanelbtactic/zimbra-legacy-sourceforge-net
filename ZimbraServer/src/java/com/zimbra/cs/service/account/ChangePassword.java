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
 * Created on Sep 3, 2004
 */
package com.zimbra.cs.service.account;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.Provisioning.DomainBy;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author dkarp
 */
public class ChangePassword extends AccountDocumentHandler {

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();
        
        String name = request.getAttribute(AccountService.E_ACCOUNT);
        
        Element virtualHostEl = request.getOptionalElement(AccountService.E_VIRTUAL_HOST);
        String virtualHost = virtualHostEl == null ? null : virtualHostEl.getText().toLowerCase();
        
        if (virtualHost != null && name.indexOf('@') == -1) {
            Domain d = prov.get(DomainBy.virtualHostname, virtualHost);
            if (d != null)
                name = name + "@" + d.getName();
        }
        
        Account acct = prov.get(AccountBy.name, name);
        if (acct == null)
            throw AccountServiceException.AUTH_FAILED(name);
		String oldPassword = request.getAttribute(AccountService.E_OLD_PASSWORD);
		String newPassword = request.getAttribute(AccountService.E_PASSWORD);
		prov.changePassword(acct, oldPassword, newPassword);

        Element response = lc.createElement(AccountService.CHANGE_PASSWORD_RESPONSE);
        return response;
	}

    public boolean needsAuth(Map<String, Object> context) {
        // This command can be sent before authenticating, so this method
        // should return false.  The Account.changePassword() method called
        // from handle() will internally make sure the old password provided
        // matches the current password of the account.
        //
        // The user identity in the auth token, if any, is ignored.
        return false;
    }
}
