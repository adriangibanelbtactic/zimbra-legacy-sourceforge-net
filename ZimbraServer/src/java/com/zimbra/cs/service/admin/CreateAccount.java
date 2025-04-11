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
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.account.ToXML;
import com.zimbra.soap.ZimbraSoapContext;

import java.util.Map;

/**
 * @author schemers
 */
public class CreateAccount extends AdminDocumentHandler {

    /**
     * must be careful and only create accounts for the domain admin!
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }
    
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext lc = getZimbraSoapContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String name = request.getAttribute(AdminConstants.E_NAME).toLowerCase();
	    String password = request.getAttribute(AdminConstants.E_PASSWORD, null);
	    Map<String, Object> attrs = AdminService.getAttrs(request, true);

        if (!canAccessEmail(lc, name))
            throw ServiceException.PERM_DENIED("can not access account:"+name);

	    Account account = prov.createAccount(name, password, attrs);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "CreateAccount","name", name}, attrs));         

	    Element response = lc.createElement(AdminConstants.CREATE_ACCOUNT_RESPONSE);

        ToXML.encodeAccountOld(response, account);

	    return response;
	}
}