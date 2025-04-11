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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.service.account;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Identity;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.IdentityBy;
import com.zimbra.soap.DocumentHandler;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.soap.ZimbraSoapContext;

public class DeleteIdentity extends DocumentHandler {
	
    public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Account account = getRequestedAccount(zsc);
        
        canModifyOptions(zsc, account);
        
        Provisioning prov = Provisioning.getInstance();

        Element eIdentity = request.getElement(AccountConstants.E_IDENTITY);

        // identity can be specified by name or by ID
        Identity ident = null;
        String id = eIdentity.getAttribute(AccountConstants.A_ID, null);
        if (id != null)
            ident = prov.get(account, IdentityBy.id, id);
        else
            ident = prov.get(account, IdentityBy.name, eIdentity.getAttribute(AccountConstants.A_NAME));

        if (ident != null)
            Provisioning.getInstance().deleteIdentity(account, ident.getName());

        Element response = zsc.createElement(AccountConstants.DELETE_IDENTITY_RESPONSE);
        return response;
    }
}