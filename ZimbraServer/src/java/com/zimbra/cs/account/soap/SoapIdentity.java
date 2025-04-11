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

package com.zimbra.cs.account.soap;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Identity;
import com.zimbra.cs.service.account.AccountService;
import com.zimbra.soap.Element;

import java.util.Map;

class SoapIdentity extends Identity implements SoapEntry {
    
    SoapIdentity(String name, String id, Map<String, Object> attrs) {
        super(name, id, attrs);
    }

    SoapIdentity(Element e) throws ServiceException {
        super(e.getAttribute(AccountService.A_NAME), e.getAttribute(AccountService.A_ID), SoapProvisioning.getAttrs(e, AccountService.A_NAME));
    }
    
    public void modifyAttrs(SoapProvisioning prov, Map<String, ? extends Object> attrs, boolean checkImmutable) throws ServiceException {
        /*
        XMLElement req = new XMLElement(AccountService.MODIFY_IDENTITY_REQUEST);
        Element identity = req.addElement(AccountService.E_IDENTITY);
        identity.addAttribute(AccountService.A_NAME, getName());
        SoapProvisioning.addAttrElements(identity, attrs);
        setAttrs(SoapProvisioning.getAttrs(prov.invoke(req)));
        */
    }

    public void reload(SoapProvisioning prov) throws ServiceException {
        //XMLElement req = new XMLElement(AdminService.GET_ALL_CONFIG_REQUEST);
        //setAttrs(SoapProvisioning.getAttrs(prov.invoke(req)));
    }
}
