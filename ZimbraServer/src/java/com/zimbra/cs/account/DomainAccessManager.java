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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.account;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.EmailUtil;
import com.zimbra.common.util.ZimbraLog;

public class DomainAccessManager extends AccessManager {

    public boolean isDomainAdminOnly(AuthToken at) {
        return at.isDomainAdmin() && !at.isAdmin();
    }

    public boolean canAccessAccount(AuthToken at, Account target) throws ServiceException {
        if (at.isAdmin()) return true;
        if (!at.isDomainAdmin()) return false;
        // don't allow a domain-only admin to access a global admin's account
        if (target.getBooleanAttr(Provisioning.A_zimbraIsAdminAccount, false)) return false;
        Provisioning prov = Provisioning.getInstance();
        return getDomain(at).getId().equals(prov.getDomain(target).getId());
    }

    /** Returns whether the specified account's credentials are sufficient
     *  to perform operations on the target account.  This occurs when the
     *  credentials belong to an admin or when the credentials are for an
     *  appropriate domain admin.  <i>Note: This method checks only for admin
     *  access, and passing the same account for <code>credentials</code> and
     *  <code>target</code> will not succeed for non-admin accounts.</i>
     * @param credentials  The authenticated account performing the action. 
     * @param target       The target account for the proposed action. */
    public boolean canAccessAccount(Account credentials, Account target) {
        // admin auth account will always succeed
        if (credentials.getBooleanAttr(Provisioning.A_zimbraIsAdminAccount, false))
            return true;
        // don't allow a domain-only admin to access a global admin's account
        if (target.getBooleanAttr(Provisioning.A_zimbraIsAdminAccount, false))
            return false;
        // domain admins succeed if the target is in the same domain
        if (target.getDomainName() != null && target.getDomainName().equals(credentials.getDomainName()))
            return credentials.getBooleanAttr(Provisioning.A_zimbraIsDomainAdminAccount, false);
        // everyone else is out of luck
        return false;
    }

    public boolean canAccessDomain(AuthToken at, String domainName) throws ServiceException {
        if (at.isAdmin()) return true;
        if (!at.isDomainAdmin()) return false;
        return getDomain(at).getName().equalsIgnoreCase(domainName);
    }

    public boolean canAccessDomain(AuthToken at, Domain domain) throws ServiceException {
        return canAccessDomain(at, domain.getName());
    }

    public boolean canAccessEmail(AuthToken at, String email) throws ServiceException {
        String parts[] = EmailUtil.getLocalPartAndDomain(email);
        if (parts == null)
            throw ServiceException.INVALID_REQUEST("must be valid email address: "+email, null);
        return canAccessDomain(at, parts[1]);
    }

    public boolean canModifyMailQuota(AuthToken at, Account targetAccount, long quota) throws ServiceException {
        if (!canAccessAccount(at,  targetAccount))
            return false;

        if (at.isAdmin()) return true;
        
        Account adminAccount = Provisioning.getInstance().get(Provisioning.AccountBy.id,  at.getAccountId());
        if (adminAccount == null) return false;

        // 0 is unlimited
        long maxQuota = adminAccount.getLongAttr(Provisioning.A_zimbraDomainAdminMaxMailQuota, -1);

        // return true if they can set quotas to anything
        if (maxQuota == 0)
            return true;

        if (    (maxQuota == -1) ||    // they don't permsission to change any quotas
                (quota == 0) ||        // they don't have permission to assign unlimited quota
                (quota > maxQuota)     // the quota they are tying to assign is too big
                ) {
            ZimbraLog.account.warn(String.format("invalid attempt to change quota: admin(%s) account(%s) quota(%d) max(%d)",
                    adminAccount.getName(), targetAccount.getName(), quota, maxQuota));
            return false;
        } else {
            return true;    
        }

    }
}
