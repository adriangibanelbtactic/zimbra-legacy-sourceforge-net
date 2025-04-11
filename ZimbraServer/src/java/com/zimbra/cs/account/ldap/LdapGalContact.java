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
 * Created on Nov 17, 2004
 */
package com.zimbra.cs.account.ldap;

import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import com.zimbra.cs.account.GalContact;
import com.zimbra.cs.service.ServiceException;

/**
 * @author schemers
 */
public class LdapGalContact implements GalContact {

    private Map mAttrs;
    private String mId;
    
    public LdapGalContact(String dn, Attributes attrs, String[] galList, Map galMap) {
        mId = dn;
        mAttrs = new HashMap();
        for (int i=0; i < galList.length; i++)
            addAttr(attrs, galList[i], (String)galMap.get(galList[i]));        
    }

    private void addAttr(Attributes attrs, String accountAttr, String contactAttr) {
        if (mAttrs.containsKey(accountAttr))
            return;
        try {
            // doesn't handle multi-value attrs
            String val = LdapUtil.getAttrString(attrs, accountAttr);
            if (val != null && !val.equals(""))
                mAttrs.put(contactAttr, val);            
        } catch (NamingException e) {
            // ignore
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.GalContact#getId()
     */
    public String getId() {
        return mId;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.GalContact#getAttrs()
     */
    public Map getAttrs() throws ServiceException {
        return mAttrs;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("LdapGalContact: { ");
        sb.append("id="+mId);
        sb.append("}");
        return sb.toString();
    }
}
