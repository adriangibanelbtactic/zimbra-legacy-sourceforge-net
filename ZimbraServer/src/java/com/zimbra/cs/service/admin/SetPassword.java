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
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.util.ZimbraLog;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraContext;

/**
 * @author schemers
 */
public class SetPassword extends AdminDocumentHandler {

	public Element handle(Element request, Map context) throws ServiceException {

        ZimbraContext lc = getZimbraContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String id = request.getAttribute(AdminService.E_ID);
        String newPassword = request.getAttribute(AdminService.E_NEW_PASSWORD);

	    Account account = prov.getAccountById(id);
        if (account == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(id);

        prov.setPassword(account, newPassword);
        
        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "SetPassword","name", account.getName()}));

	    Element response = lc.createElement(AdminService.SET_PASSWORD_RESPONSE);
	    return response;
	}

}