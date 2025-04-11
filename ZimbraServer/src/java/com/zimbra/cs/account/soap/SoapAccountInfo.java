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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;


public class SoapAccountInfo {
    
    private Map<String,Object> mAttrs;
    private String mName;
    private List<String> mSoapURL;
    private String mAdminSoapURL;
    
     SoapAccountInfo(Element e) throws ServiceException {
        mAttrs = SoapProvisioning.getAttrs(e);
        mName = e.getElement(AdminConstants.E_NAME).getText();
        mSoapURL = new ArrayList<String>();
        for (Element su : e.listElements(AdminConstants.E_SOAP_URL)) {
            mSoapURL.add(su.getText());
        }
        mAdminSoapURL = e.getElement(AdminConstants.E_ADMIN_SOAP_URL).getText();
    }
    
    public List<String> getSoapURL() { return mSoapURL; }
    public String getAdminSoapURL() { return mAdminSoapURL; }
    
    public String getAttr(String name) {
        Object v = mAttrs.get(name);
        if (v instanceof String) {
            return (String) v;
        } else if (v instanceof String[]) {
            String[] a = (String[]) v;
            return a.length > 0 ? a[0] : null;
        } else {
            return null;
        }
    }

    public String getAttr(String name, String defaultValue) {
        String v = getAttr(name);
        return v == null ? defaultValue : v;
    }

}