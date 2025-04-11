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

package com.zimbra.cs.account.ldap;

import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.WellKnownTimeZone;
import com.zimbra.cs.mailbox.calendar.ICalTimeZone;
import com.zimbra.cs.service.ServiceException;

/**
 * @author schemers
 */
public class LdapAccount extends LdapNamedEntry implements Account {

    private LdapProvisioning mProv;
    private String mName;
    private String mDomainName;    

    private static final String DATA_COS = "COS";
    
    LdapAccount(String dn, Attributes attrs, LdapProvisioning prov) {
        super(dn, attrs);
        mProv = prov;
        initNameAndDomain();
    }

    public String getId() {
        return super.getAttr(Provisioning.A_zimbraId);
    }

    public String getUid() {
        return super.getAttr(Provisioning.A_uid);
    }
    
    public String getName() {
        return mName;
    }

    /**
     * @return the domain name for this account (foo.com), or null if an admin account. 
     */
    public String getDomainName() {
        return mDomainName;
    }

    /**
     * @return the domain this account, or null if an admin account. 
     * @throws ServiceException
     */
    public Domain getDomain() throws ServiceException {
        return mDomainName == null ? null : mProv.getDomainByName(mDomainName);
    }

    private void initNameAndDomain() {
        String uid = getUid(); 
        mDomainName = LdapUtil.dnToDomain(mDn);
        if (!mDomainName.equals("")) {
            mName =  uid+"@"+mDomainName;
        } else {
            mName = uid;
            mDomainName = null;
        }
    }

    public String getAccountStatus() {
        return super.getAttr(Provisioning.A_zimbraAccountStatus);
    }
     
    public String getAttr(String name) {
        String v = super.getAttr(name);
        if (v != null)
            return v;
        try {
            if (!mProv.getConfig().isInheritedAccountAttr(name))
                return null;
            Cos cos = getCOS();
            if (cos != null)
                return cos.getAttr(name);
            else
                return null;            
        } catch (ServiceException e) {
            return null;
        }
    }

    public String[] getMultiAttr(String name) {
        String v[] = super.getMultiAttr(name);
        if (v.length > 0)
            return v;
        try {
            if (!mProv.getConfig().isInheritedAccountAttr(name))
                return sEmptyMulti;

            Cos cos = getCOS();
            if (cos != null)
                return cos.getMultiAttr(name);
            else
                return sEmptyMulti;
        } catch (ServiceException e) {
            return null;
        }
    }
    
    public String[] getAliases() {
        return getMultiAttr(Provisioning.A_zimbraMailAlias);
    }

    public Cos getCOS() throws ServiceException {
        // CACHE. If we get reloaded from LDAP, cached data is cleared
        Cos cos = (Cos) getCachedData(DATA_COS);
        if (cos == null) {
            String id = super.getAttr(Provisioning.A_zimbraCOSId);
            if (id != null) cos = mProv.getCosById(id); 
            if (cos == null) cos = mProv.getCosByName(Provisioning.DEFAULT_COS_NAME);
            if (cos != null) setCachedData(DATA_COS, cos);
        }
        return cos;
    }

    public Map getPrefs() throws ServiceException {
        HashMap prefs = new HashMap();
        try {
            LdapCos cos = (LdapCos) getCOS();
            // get the COS prefs first
            LdapUtil.getAttrs(cos.mAttrs, prefs, "zimbraPref");
            // and override with the account ones
            LdapUtil.getAttrs(mAttrs, prefs, "zimbraPref");
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to get prefs", e);
        }
        return prefs;
    }
    
    public Map getAttrs() throws ServiceException {
        return getAttrs(false, true);
    }

    public Map getAttrs(boolean prefsOnly, boolean applyCos) throws ServiceException {
        HashMap attrs = new HashMap();
        try {
            // get all the account attrs
            LdapUtil.getAttrs(mAttrs, attrs, prefsOnly ? "zimbraPref" : null);
            
            if (!applyCos)
                return attrs;
            
            // then enumerate through all inheritable attrs and add them if needed
            String[] inheritable = mProv.getConfig().getMultiAttr(Provisioning.A_zimbraCOSInheritedAttr);
            for (int i = 0; i < inheritable.length; i++) {
                if (!prefsOnly || inheritable[i].startsWith("zimbraPref")) {
                    Object value = attrs.get(inheritable);
                    if (value == null)
                        value = getMultiAttr(inheritable[i]);
                    if (value != null)
                        attrs.put(inheritable[i], value);
                }
            }
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to get prefs", e);
        }
        return attrs;
    }

    public boolean isCorrectHost() throws ServiceException{
        String target    = getAttr(Provisioning.A_zimbraMailHost);
        String localhost = mProv.getLocalServer().getAttr(Provisioning.A_zimbraServiceHostname);
        return (target != null && target.equalsIgnoreCase(localhost));
    }

    public Server getServer() throws ServiceException {
        String serverId = getAttr(Provisioning.A_zimbraMailHost);
        return (serverId == null ? null : mProv.getServerByName(serverId));
    }

    private ICalTimeZone mTimeZone;

    public synchronized ICalTimeZone getTimeZone() throws ServiceException {
        String tzId = getAttr(Provisioning.A_zimbraPrefTimeZoneId);
        if (tzId == null) {
        	if (mTimeZone != null)
                return mTimeZone;
            mTimeZone = ICalTimeZone.getUTC();
            return mTimeZone;
        }

        if (mTimeZone != null) {
            if (mTimeZone.getID().equals(tzId))
                return mTimeZone;
            // Else the account's time zone was updated.  Discard the cached
            // ICalTimeZone object.
        }

    	WellKnownTimeZone z = Provisioning.getInstance().getTimeZoneById(tzId);
        if (z != null)
            mTimeZone = z.toTimeZone();
        if (mTimeZone == null)
            mTimeZone = ICalTimeZone.getUTC();
        return mTimeZone;
    }

}
