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

package com.zimbra.cs.account.ldap;

import com.zimbra.cs.account.Identity;
import com.zimbra.cs.account.Provisioning;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

/**
 * @author schemers
 */
 class LdapIdentity extends Identity implements LdapEntry {

    private String mDn;

    LdapIdentity(String dn, Attributes attrs) throws NamingException {
        super(
                LdapUtil.getAttrString(attrs, Provisioning.A_zimbraPrefIdentityName),
                LdapUtil.getAttrString(attrs, Provisioning.A_zimbraPrefIdentityId),
                LdapUtil.getAttrs(attrs));
        mDn = dn;
    }

    public String getDN() {
        return mDn;
    }

}
