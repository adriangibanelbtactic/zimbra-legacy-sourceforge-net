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
 * Created on Sep 23, 2004
 *
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.account.ldap;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.DateUtil;
import com.zimbra.common.util.EmailUtil;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Account.CalendarUserType;
import com.zimbra.cs.account.soap.SoapProvisioning;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Alias;
import com.zimbra.cs.account.AttributeClass;
import com.zimbra.cs.account.AttributeManager;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.DomainCache;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.EntrySearchFilter;
import com.zimbra.cs.account.GalContact;
import com.zimbra.cs.account.Identity;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.NamedEntryCache;
import com.zimbra.cs.account.PreAuthKey;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Signature;
import com.zimbra.cs.account.Zimlet;
import com.zimbra.cs.httpclient.URLUtil;
import com.zimbra.cs.mime.MimeTypeInfo;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.cs.util.Zimbra;
import com.zimbra.cs.zimlet.ZimletException;
import com.zimbra.cs.zimlet.ZimletUtil;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.naming.AuthenticationException;
import javax.naming.AuthenticationNotSupportedException;
import javax.naming.ContextNotEmptyException;
import javax.naming.InvalidNameException;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.InvalidAttributesException;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.SchemaViolationException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * @author schemers
 */
public class LdapProvisioning extends Provisioning {

    // object classes
    public static final String C_zimbraAccount = "zimbraAccount";
    public static final String C_amavisAccount = "amavisAccount";
    public static final String C_zimbraCOS = "zimbraCOS";
    public static final String C_zimbraDomain = "zimbraDomain";
    public static final String C_zimbraMailList = "zimbraDistributionList";
    public static final String C_zimbraMailRecipient = "zimbraMailRecipient";
    public static final String C_zimbraServer = "zimbraServer";
    public static final String C_zimbraCalendarResource = "zimbraCalendarResource";
    public static final String C_zimbraAlias = "zimbraAlias";

    private static final long ONE_DAY_IN_MILLIS = 1000*60*60*24;

    private static final SearchControls sObjectSC = new SearchControls(SearchControls.OBJECT_SCOPE, 0, 0, null, false, false);

    static final SearchControls sSubtreeSC = new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, null, false, false);

    private static final Log mLog = LogFactory.getLog(LdapProvisioning.class);
    
    private static LdapConfig sConfig = null;
    
    // private static Pattern sValidCosName = Pattern.compile("^\\w+$");
    private static Pattern sValidCosName = Pattern.compile("[-a-zA-Z0-9\\.]+");
    
    private static final String[] sInvalidAccountCreateModifyAttrs = {
            Provisioning.A_zimbraMailAlias,
            Provisioning.A_zimbraMailDeliveryAddress,
            Provisioning.A_uid
    };

    private static final String[] sMinimalDlAttrs = {
            Provisioning.A_zimbraMailAlias,
            Provisioning.A_zimbraId,
            Provisioning.A_uid
    };

    private static final String FILTER_ACCOUNT_OBJECTCLASS =
        "(objectclass=zimbraAccount)";
    private static final String FILTER_CALENDAR_RESOURCE_OBJECTCLASS =
        "(objectclass=zimbraCalendarResource)";
    private static final String FILTER_DISTRIBUTION_LIST_OBJECTCLASS =
        "(objectclass=zimbraDistributionList)";

    private static NamedEntryCache<Account> sAccountCache =
        new NamedEntryCache<Account>(
                LC.ldap_cache_account_maxsize.intValue(),
                LC.ldap_cache_account_maxage.intValue() * Constants.MILLIS_PER_MINUTE); 

    private static NamedEntryCache<LdapCos> sCosCache =
        new NamedEntryCache<LdapCos>(
                LC.ldap_cache_cos_maxsize.intValue(),
                LC.ldap_cache_cos_maxage.intValue() * Constants.MILLIS_PER_MINUTE); 

    private static DomainCache sDomainCache =
        new DomainCache(
                LC.ldap_cache_domain_maxsize.intValue(),
                LC.ldap_cache_domain_maxage.intValue() * Constants.MILLIS_PER_MINUTE);         

    private static NamedEntryCache<Server> sServerCache =
        new NamedEntryCache<Server>(
                LC.ldap_cache_server_maxsize.intValue(),
                LC.ldap_cache_server_maxage.intValue() * Constants.MILLIS_PER_MINUTE);

    private static NamedEntryCache<LdapZimlet> sZimletCache = 
        new NamedEntryCache<LdapZimlet>(
                LC.ldap_cache_zimlet_maxsize.intValue(),
                LC.ldap_cache_zimlet_maxage.intValue() * Constants.MILLIS_PER_MINUTE);                
    

    private static final int BY_ID = 1;

    private static final int BY_EMAIL = 2;

    private static final int BY_NAME = 3;
    
    protected LdapDIT mDIT;
    public LdapProvisioning() {
        setDIT();
    }
    
    protected void setDIT() {
        mDIT = new LdapDIT(this);
    }
    
    /*
     * Contains parallel arrays of old addrs and new addrs as a result of domain change
     */
    protected static class ReplaceAddressResult {
        ReplaceAddressResult(String oldAddrs[], String newAddrs[]) {
            mOldAddrs = oldAddrs;
            mNewAddrs = newAddrs;
        }
        private String mOldAddrs[];
        private String mNewAddrs[];
        
        public String[] oldAddrs() { return mOldAddrs; }
        public String[] newAddrs() { return mNewAddrs; }
    }

    private static final Random sPoolRandom = new Random();

    private static Pattern sNamePattern = Pattern.compile("([/+])"); 

    public static interface ProvisioningValidator {
    	public void validate(LdapProvisioning prov, String action, Object arg) throws ServiceException;
    }
    
    private static List<ProvisioningValidator> sValidators;
    
    static {
    	sValidators = new ArrayList<ProvisioningValidator>();
    	Validators.init();
    }
    
    public static void register(ProvisioningValidator validator) {
    	synchronized (sValidators) {
    		sValidators.add(validator);
    	}
    }
    
    private void validate(String action, Object arg) throws ServiceException {
    	for (ProvisioningValidator v : sValidators) {
    		v.validate(this, action, arg);
    	}
    }

    public void modifyAttrs(Entry e, Map<String, ? extends Object> attrs, boolean checkImmutable)
            throws ServiceException {
        modifyAttrs(e, attrs, checkImmutable, true);
    }


    /**
     * Modifies this entry.  <code>attrs</code> is a <code>Map</code> consisting of
     * keys that are <code>String</code>s, and values that are either
     * <ul>
     *   <li><code>null</code>, in which case the attr is removed</li>
     *   <li>a single <code>Object</code>, in which case the attr is modified
     *     based on the object's <code>toString()</code> value</li>
     *   <li>an <code>Object</code> array or <code>Collection</code>,
     *     in which case a multi-valued attr is updated</li>
     * </ul>
     */
    public void modifyAttrs(Entry e, Map<String, ? extends Object> attrs, boolean checkImmutable, boolean allowCallback)
            throws ServiceException {
        HashMap context = new HashMap();
        AttributeManager.getInstance().preModify(attrs, e, context, false, checkImmutable, allowCallback);
        modifyAttrsInternal(e, null, attrs);
        AttributeManager.getInstance().postModify(attrs, e, context, false, allowCallback);
    }
    
    /**
     * should only be called internally.
     * 
     * @param initCtxt
     * @param attrs
     * @throws ServiceException
     */
    protected synchronized void modifyAttrsInternal(Entry entry, DirContext initCtxt, Map attrs)
            throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext(true);
            LdapUtil.modifyAttrs(ctxt, ((LdapEntry)entry).getDN(), attrs, entry);
            refreshEntry(entry, ctxt, this);
        } catch (InvalidAttributeIdentifierException e) {
            throw AccountServiceException.INVALID_ATTR_NAME(
                    "invalid attr name: " + e.getMessage(), e);
        } catch (InvalidAttributeValueException e) {
            throw AccountServiceException.INVALID_ATTR_VALUE(
                    "invalid attr value: " + e.getMessage(), e);
        } catch (InvalidAttributesException e) {
            throw ServiceException.INVALID_REQUEST(
                    "invalid set of attributes: " + e.getMessage(), e);
        } catch (SchemaViolationException e) {
            throw ServiceException.INVALID_REQUEST("LDAP schema violation: "
                    + e.getMessage(), e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to modify attrs: "
                    + e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
    }  

    /**
     * reload/refresh the entry.
     */
    public void reload(Entry e) throws ServiceException
    {    
        refreshEntry(e, null, this);
    }

    synchronized void refreshEntry(Entry entry, DirContext initCtxt, LdapProvisioning prov)
    throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            Map<String,Object> defaults = null;
    
            if (entry instanceof Account) {
                Cos cos = prov.getCOS((Account)entry);
                if (cos != null) defaults = cos.getAccountDefaults();
            } else if (entry instanceof Domain) {
                defaults = prov.getConfig().getDomainDefaults();
            } else if (entry instanceof Server) {
                defaults = prov.getConfig().getServerDefaults();            
            }
    
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            String dn = ((LdapEntry)entry).getDN();
            if (defaults == null)
                entry.setAttrs(LdapUtil.getAttrs(LdapUtil.getAttributes(ctxt, dn)));
            else 
                entry.setAttrs(LdapUtil.getAttrs(LdapUtil.getAttributes(ctxt, dn)), defaults);                
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to refresh entry", e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
    }

    /**
     * Status check on LDAP connection.  Search for global config entry.
     */
    public boolean healthCheck() throws ServiceException {
        boolean result = false;
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            Attributes attrs = LdapUtil.getAttributes(ctxt, mDIT.configDN());
            result = attrs != null;
        } catch (NamingException e) {
            mLog.warn("LDAP health check error", e);
        } catch (ServiceException e) {
            mLog.warn("LDAP health check error", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        return result;
    }

    public Config getConfig() throws ServiceException
    {
        // TODO: failure scenarios? fallback to static config file or hard-coded defaults?
        if (sConfig == null) {
            synchronized(LdapProvisioning.class) {
                if (sConfig == null) {
                    DirContext ctxt = null;
                    try {
                        String configDn = mDIT.configDN();
                        ctxt = LdapUtil.getDirContext();
                        Attributes attrs = LdapUtil.getAttributes(ctxt, configDn);
                        sConfig = new LdapConfig(configDn, attrs);
                    } catch (NamingException e) {
                        throw ServiceException.FAILURE("unable to get config", e);
                    } finally {
                        LdapUtil.closeContext(ctxt);
                    }
                }
            }
        }
        return sConfig;
    }

    public synchronized List<MimeTypeInfo> getMimeTypes(String mimeType) throws ServiceException {
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            mimeType = LdapUtil.escapeSearchFilterArg(mimeType);
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.mimeBaseDN(), "(" + Provisioning.A_zimbraMimeType + "=" + mimeType + ")", sSubtreeSC);
            List<MimeTypeInfo> mimeTypes = new ArrayList<MimeTypeInfo>();
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                mimeTypes.add(new LdapMimeType(sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();
            return mimeTypes;
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to get mime types for " + mimeType, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    
    public synchronized List<MimeTypeInfo> getMimeTypesByExtension(String ext) throws ServiceException {
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            ext = LdapUtil.escapeSearchFilterArg(ext);
            List<MimeTypeInfo> mimeTypes = new ArrayList<MimeTypeInfo>();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.mimeBaseDN(), "(" + Provisioning.A_zimbraMimeFileExtension + "=" + ext + ")", sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                mimeTypes.add(new LdapMimeType(sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();
            return mimeTypes;
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to get mime type for file extension " + ext, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        
    }

    public synchronized List<Zimlet> getObjectTypes() throws ServiceException {
    	return listAllZimlets();
    }

    private Account getAccountByQuery(String base, String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, base, query, sSubtreeSC);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                if (ne.hasMore()) {
                    StringBuffer dups = new StringBuffer();
                    dups.append("[" + sr.getNameInNamespace() + "] ");
                    while (ne.hasMore()) {
                        SearchResult dup = (SearchResult) ne.next();
                        dups.append("[" + dup.getNameInNamespace() + "] ");
                    }
                    throw AccountServiceException.MULTIPLE_ACCOUNTS_MATCHED("getAccountByQuery: "+query+" returned multiple entries at "+dups);
                }
                ne.close();
                return makeAccount(sr.getNameInNamespace(), sr.getAttributes(), this);
            }
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;            
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup account via query: "+query+" message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return null;
    }

    private Account getAccountById(String zimbraId, DirContext ctxt) throws ServiceException {
        if (zimbraId == null)
            return null;
        Account a = sAccountCache.getById(zimbraId);
        if (a == null) {
            zimbraId= LdapUtil.escapeSearchFilterArg(zimbraId);
            a = getAccountByQuery(
                    "",
                    "(&(zimbraId=" + zimbraId + ")" +
                    FILTER_ACCOUNT_OBJECTCLASS + ")",
                    ctxt);
            sAccountCache.put(a);
        }
        return a;
    }

    @Override
    public Account get(AccountBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case adminName: 
                return getAdminAccountByName(key);
            case id: 
                return getAccountById(key);
            case foreignPrincipal: 
                return getAccountByForeignPrincipal(key);
            case name: 
                return getAccountByName(key);
            default:
                    return null;
        }
    }
    
    protected Account getAccountById(String zimbraId) throws ServiceException {
        return getAccountById(zimbraId, null);
    }

    private Account getAccountByForeignPrincipal(String foreignPrincipal) throws ServiceException {
        foreignPrincipal = LdapUtil.escapeSearchFilterArg(foreignPrincipal);
        return getAccountByQuery(
                "",
                "(&(zimbraForeignPrincipal=" + foreignPrincipal + ")" +
                FILTER_ACCOUNT_OBJECTCLASS + ")",
                null);
    }

    private Account getAdminAccountByName(String name) throws ServiceException {
        Account a = sAccountCache.getByName(name);
        if (a == null) {
            name = LdapUtil.escapeSearchFilterArg(name);
            a = getAccountByQuery(
                    mDIT.adminBaseDN(),
                    "(&(" + mDIT.accountNamingRdnAttr() + "=" + name + ")" +
                    FILTER_ACCOUNT_OBJECTCLASS + ")",
                    null);
            sAccountCache.put(a);
        }
        return a;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getDomainByName(java.lang.String)
     */
    private Account getAccountByName(String emailAddress) throws ServiceException {
        
        int index = emailAddress.indexOf('@');
        String domain = null;
        if (index == -1) {
             domain = getConfig().getAttr(Provisioning.A_zimbraDefaultDomainName, null);
            if (domain == null)
                throw ServiceException.INVALID_REQUEST("must be valid email address: "+emailAddress, null);
            else
                emailAddress = emailAddress + "@" + domain;            
         }
        
        Account account = sAccountCache.getByName(emailAddress);
        if (account == null) {
            emailAddress = LdapUtil.escapeSearchFilterArg(emailAddress);
            account = getAccountByQuery(
                    "",
                    "(&(|(zimbraMailDeliveryAddress=" + emailAddress +
                    ")(zimbraMailAlias=" + emailAddress + "))" +
                    FILTER_ACCOUNT_OBJECTCLASS + ")",
                    null);
            sAccountCache.put(account);
        }
        return account;
    }
    
    private int guessType(String value) {
        if (value.indexOf("@") != -1)
            return BY_EMAIL;
        else if (value.length() == 36 &&
                value.charAt(8) == '-' &&
                value.charAt(13) == '-' &&
                value.charAt(18) == '-' &&
                value.charAt(23) == '-')
            return BY_ID;
        else return BY_NAME;
    }
  
    private Cos lookupCos(String key, DirContext ctxt) throws ServiceException {
        Cos c = null;
        switch(guessType(key)) {
        case BY_ID:
            c = getCosById(key, ctxt);
            break;
        case BY_NAME:
            c = getCosByName(key, ctxt);
            break;
        }
        if (c == null)
            throw AccountServiceException.NO_SUCH_COS(key);
        else
            return c;
    }
    
    public Account createAccount(String emailAddress, String password, Map<String, Object> acctAttrs) throws ServiceException {
        return createAccount(emailAddress, password, acctAttrs, mDIT.handleSpecialAttrs(acctAttrs), null);
    }
    
    private Account createAccount(String emailAddress,
                                  String password,
                                  Map<String, Object> acctAttrs,
                                  SpecialAttrs specialAttrs,
                                  String[] additionalObjectClasses) throws ServiceException {
        
        validEmailAddress(emailAddress);
        
        String uuid = specialAttrs.getZimbraId();
        String baseDn = specialAttrs.getLdapBaseDn();
        
    	validate("createAccount", emailAddress);
        emailAddress = emailAddress.toLowerCase().trim();

        HashMap attrManagerContext = new HashMap();
        if (acctAttrs == null) {
            acctAttrs = new HashMap<String, Object>();
        }
        AttributeManager.getInstance().preModify(acctAttrs, null, attrManagerContext, true, true);

        String dn = null;
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            String parts[] = emailAddress.split("@");
            if (parts.length != 2)
                throw ServiceException.INVALID_REQUEST("must be valid email address: "+emailAddress, null);
            
            String localPart = parts[0];
            String domain = parts[1];

            Domain d = getDomainByName(domain, ctxt);
            if (d == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(domain);
            String domainType = d.getAttr(Provisioning.A_zimbraDomainType, Provisioning.DOMAIN_TYPE_LOCAL);
            if (!domainType.equals(Provisioning.DOMAIN_TYPE_LOCAL))
                throw ServiceException.INVALID_REQUEST("domain type must be local", null);

            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(acctAttrs, attrs);

            for (int i=0; i < sInvalidAccountCreateModifyAttrs.length; i++) {
                String a = sInvalidAccountCreateModifyAttrs[i];
                if (attrs.get(a) != null)
                    throw ServiceException.INVALID_REQUEST("invalid attribute for CreateAccount: "+a, null);
            }
            
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "organizationalPerson");
            oc.add(C_zimbraAccount);

            String[] extraObjectClasses = getConfig().getMultiAttr(A_zimbraAccountExtraObjectClass);
            for (String eoc : extraObjectClasses) {
                oc.add(eoc);
            }
            oc.add(C_amavisAccount);
            
            if (additionalObjectClasses != null) {
                for (int i = 0; i < additionalObjectClasses.length; i++)
                    oc.add(additionalObjectClasses[i]);
            }
            
            String zimbraIdStr;
            if (uuid == null)
                zimbraIdStr = LdapUtil.generateUUID();
            else
                zimbraIdStr = uuid;
            attrs.put(A_zimbraId, zimbraIdStr);

            // TODO: uncomment when ready
            //attrs.put(A_zimbraVersion, AccountVersion.CURRENT_VERSION+"");
            
            // default account status is active
            if (attrs.get(Provisioning.A_zimbraAccountStatus) == null)
                attrs.put(A_zimbraAccountStatus, Provisioning.ACCOUNT_STATUS_ACTIVE);

            Cos cos = null;
            Attribute cosIdAttr = attrs.get(Provisioning.A_zimbraCOSId);
            String cosId = null;

            if (cosIdAttr != null) {
                cosId = (String) cosIdAttr.get();
                cos = lookupCos(cosId, ctxt);
                if (!cos.getId().equals(cosId)) {
                    cosId = cos.getId();
                }
                attrs.put(Provisioning.A_zimbraCOSId, cosId);
            } else {
                String domainCosId = domain != null ? d.getAttr(Provisioning.A_zimbraDomainDefaultCOSId, null) : null;
                if (domainCosId != null) cos = get(CosBy.id, domainCosId);
                if (cos == null) cos = getCosByName(Provisioning.DEFAULT_COS_NAME, ctxt);
            }

            // if zimbraMailHost is not specified, and we have a COS, see if there is a pool to
            // pick from.
            if (cos != null && attrs.get(Provisioning.A_zimbraMailHost) == null) {
                String mailHostPool[] = cos.getMultiAttr(Provisioning.A_zimbraMailHostPool);
                addMailHost(attrs, mailHostPool, cos.getName());
            }

            // if zimbraMailHost still not specified, default to local server's zimbraServiceHostname if it has 
            // the mailbox service enabled, otherwise look through all servers and pick first with the service enabled.
            // this means every account will always have a mailbox
            if (attrs.get(Provisioning.A_zimbraMailHost) == null) {
                addDefaultMailHost(attrs);
            }

            // set all the mail-related attrs if zimbraMailHost was specified
            if (attrs.get(Provisioning.A_zimbraMailHost) != null) {
                // default mail status is enabled
                if (attrs.get(Provisioning.A_zimbraMailStatus) == null)
                    attrs.put(A_zimbraMailStatus, MAIL_STATUS_ENABLED);

                // default account mail delivery address is email address
                if (attrs.get(Provisioning.A_zimbraMailDeliveryAddress) == null) {
                    attrs.put(A_zimbraMailDeliveryAddress, emailAddress);
                }
            }

            // amivisAccount requires the mail attr, so we always add it            
            attrs.put(A_mail, emailAddress);                

            // required for organizationalPerson class
            if (attrs.get(Provisioning.A_cn) == null) {
                Attribute a = attrs.get(Provisioning.A_displayName); 
                if (a != null) {
                    attrs.put(A_cn, a.get());
                } else {
                    attrs.put(A_cn, localPart);
                }
            }

            // required for organizationalPerson class
            if (attrs.get(Provisioning.A_sn) == null)
                attrs.put(A_sn, localPart);
            
            attrs.put(A_uid, localPart);

            setInitialPassword(cos, attrs, password);
            
            dn = mDIT.accountDNCreate(baseDn, attrs, localPart, domain);
            
            LdapUtil.createEntry(ctxt, dn, attrs, "createAccount");
            Account acct = getAccountById(zimbraIdStr, ctxt);
            AttributeManager.getInstance().postModify(acctAttrs, acct, attrManagerContext, true);

            return acct;
        } catch (NameAlreadyBoundException nabe) {
            String info = "";
            if (baseDn != null)
                info = "(entry["+dn+"] already bound)";
            throw AccountServiceException.ACCOUNT_EXISTS(emailAddress+info);
        } catch (NamingException e) {
           throw ServiceException.FAILURE("unable to create account: "+emailAddress, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private boolean addDefaultMailHost(Attributes attrs, Server server)  throws ServiceException {
        String localMailHost = server.getAttr(Provisioning.A_zimbraServiceHostname);
        boolean hasMailboxService = server.getMultiAttrSet(Provisioning.A_zimbraServiceEnabled).contains("mailbox");
        if (hasMailboxService && localMailHost != null) {
            attrs.put(Provisioning.A_zimbraMailHost, localMailHost);
            int lmtpPort = getLocalServer().getIntAttr(Provisioning.A_zimbraLmtpBindPort, com.zimbra.cs.util.Config.D_LMTP_BIND_PORT);
            String transport = "lmtp:" + localMailHost + ":" + lmtpPort;
            attrs.put(Provisioning.A_zimbraMailTransport, transport);
            return true;
        }
        return false;
    }

    private void addDefaultMailHost(Attributes attrs)  throws ServiceException {
        if (!addDefaultMailHost(attrs, getLocalServer())) {
            for (Server server: getAllServers()) {
                if (addDefaultMailHost(attrs, server)) {
                    return;
                }
            }
        }
    }

    private String addMailHost(Attributes attrs, String[] mailHostPool, String cosName) throws ServiceException {
        if (mailHostPool.length == 0) {
            return null;
        } else if (mailHostPool.length > 1) {
            // copy it, since we are dealing with a cached String[]
            String pool[] = new String[mailHostPool.length];
            System.arraycopy(mailHostPool, 0, pool, 0, mailHostPool.length);
            mailHostPool = pool;
        }

        // shuffule up and deal
        int max = mailHostPool.length;
        while (max > 0) {
            int i = sPoolRandom.nextInt(max);
            String mailHostId = mailHostPool[i];
            Server s = (mailHostId == null) ? null : getServerById(mailHostId);
            if (s != null) {
                String mailHost = s.getAttr(Provisioning.A_zimbraServiceHostname);
                if (mailHost != null) {
                	attrs.put(Provisioning.A_zimbraMailHost, mailHost);
                	int lmtpPort = s.getIntAttr(Provisioning.A_zimbraLmtpBindPort, com.zimbra.cs.util.Config.D_LMTP_BIND_PORT);
                	String transport = "lmtp:" + mailHost + ":" + lmtpPort;
                	attrs.put(Provisioning.A_zimbraMailTransport, transport);
                	return mailHost;
                } else {
                    ZimbraLog.account.warn("cos("+cosName+") mailHostPool server("+s.getName()+") has no service hostname");
                }
            } else {
                ZimbraLog.account.warn("cos("+cosName+") has invalid server in pool: "+mailHostId);
            }
            if (i != max-1) {
                mailHostPool[i] = mailHostPool[max-1];
            }
            max--;
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getAllDomains()
     */
    @SuppressWarnings("unchecked")    
    public List<Account> getAllAdminAccounts() throws ServiceException {
        return (List<Account>)searchAccountsInternal("(|(zimbraIsAdminAccount=TRUE)(zimbraIsDomainAdminAccount=TRUE))", null, null, true, Provisioning.SA_ACCOUNT_FLAG);
    }

    @SuppressWarnings("unchecked")
    public List<NamedEntry> searchAccounts(String query, String returnAttrs[], final String sortAttr, final boolean sortAscending, int flags) throws ServiceException {
        return (List<NamedEntry>) searchAccountsInternal(query, returnAttrs, sortAttr, sortAscending, flags);  
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#searchAccounts(java.lang.String)
     */
    private List<?> searchAccountsInternal(String query, String returnAttrs[], final String sortAttr, final boolean sortAscending, int flags)  
        throws ServiceException
    {
        //flags &= ~Provisioning.SA_DOMAIN_FLAG; // leaving on for now
        return searchObjects(query, returnAttrs, sortAttr, sortAscending, "", flags, 0);
    }
    
    private static String getObjectClassQuery(int flags) {
        boolean accounts = (flags & Provisioning.SA_ACCOUNT_FLAG) != 0; 
        boolean aliases = (flags & Provisioning.SA_ALIAS_FLAG) != 0;
        boolean lists = (flags & Provisioning.SA_DISTRIBUTION_LIST_FLAG) != 0;
        boolean domains = (flags & Provisioning.SA_DOMAIN_FLAG) != 0;
        boolean calendarResources =
            (flags & Provisioning.SA_CALENDAR_RESOURCE_FLAG) != 0;

        int num = (accounts ? 1 : 0) +
                  (aliases ? 1 : 0) +
                  (lists ? 1 : 0) +
                  (domains ? 1 : 0) +                  
                  (calendarResources ? 1 : 0);
        if (num == 0)
            accounts = true;

        // If searching for user accounts/aliases/lists, filter looks like:
        //
        //   (&(objectclass=zimbraAccount)!(objectclass=zimbraCalendarResource))
        //
        // If searching for calendar resources, filter looks like:
        //
        //   (objectclass=zimbraCalendarResource)
        //
        // The !resource condition is there in first case because a calendar
        // resource is also a zimbraAccount.
        //
        StringBuffer oc = new StringBuffer();
        if (!calendarResources) oc.append("(&");
        if (num > 1) oc.append("(|");
        if (accounts) oc.append("(objectclass=zimbraAccount)");
        if (aliases) oc.append("(objectclass=zimbraAlias)");
        if (lists) oc.append("(objectclass=zimbraDistributionList)");
        if (domains) oc.append("(objectclass=zimbraDomain)");        
        if (calendarResources)
            oc.append("(objectclass=zimbraCalendarResource)");
        if (num > 1) oc.append(")");
        if (!calendarResources)
            oc.append("(!(objectclass=zimbraCalendarResource)))");
        return oc.toString();
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#searchAccounts(java.lang.String)
     */
    List<NamedEntry> searchObjects(String query, String returnAttrs[], final String sortAttr, final boolean sortAscending, String base, int flags, int maxResults)
    throws ServiceException {
        final List<NamedEntry> result = new ArrayList<NamedEntry>();
        
        NamedEntry.Visitor visitor = new NamedEntry.Visitor() {
            public void visit(NamedEntry entry) {
                result.add(entry);
            }
        };
        
        searchObjects(query, returnAttrs, base, flags, visitor, maxResults);

        final boolean byName = sortAttr == null || sortAttr.equals("name"); 
        Comparator<NamedEntry> comparator = new Comparator<NamedEntry>() {
            public int compare(NamedEntry oa, NamedEntry ob) {
                NamedEntry a = (NamedEntry) oa;
                NamedEntry b = (NamedEntry) ob;
                int comp = 0;
                if (byName)
                    comp = a.getName().compareToIgnoreCase(b.getName());
                else {
                    String sa = a.getAttr(sortAttr);
                    String sb = b.getAttr(sortAttr);
                    if (sa == null) sa = "";
                    if (sb == null) sb = "";
                    comp = sa.compareToIgnoreCase(sb);
                }
                return sortAscending ? comp : -comp;
            }
        };
        Collections.sort(result, comparator);        
        return result;
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#searchAccounts(java.lang.String)
     */
    void searchObjects(String query, String returnAttrs[], String base, int flags, NamedEntry.Visitor visitor, int maxResults)
        throws ServiceException
    {
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            
            String objectClass = getObjectClassQuery(flags);
            
            if (query == null || query.equals("")) {
                query = objectClass;
            } else {
                if (query.startsWith("(") && query.endsWith(")")) {
                    query = "(&"+query+objectClass+")";                    
                } else {
                    query = "(&("+query+")"+objectClass+")";
                }
            }
            
            returnAttrs = fixReturnAttrs(returnAttrs, flags);

            SearchControls searchControls = 
                new SearchControls(SearchControls.SUBTREE_SCOPE, maxResults, 0, returnAttrs, false, false);

            //Set the page size and initialize the cookie that we pass back in subsequent pages
            int pageSize = 1000; 
            byte[] cookie = null;
 
            LdapContext lctxt = (LdapContext)ctxt; 
 
            // we don't want to ever cache any of these, since they might not have all their attributes

            NamingEnumeration ne = null;

            int total = 0;
            String configBranchBaseDn = mDIT.configBranchBaseDn();
            try {
                do {
                    lctxt.setRequestControls(new Control[]{new PagedResultsControl(pageSize, cookie, Control.CRITICAL)});
                    
                    ne = LdapUtil.searchDir(ctxt, base, query, searchControls);
                    while (ne != null && ne.hasMore()) {
                        if (maxResults > 0 && total++ > maxResults)
                        throw new SizeLimitExceededException("exceeded limit of "+maxResults);
                        SearchResult sr = (SearchResult) ne.nextElement();
                        String dn = sr.getNameInNamespace();
                        // skip admin accounts
                        if (dn.endsWith(configBranchBaseDn)) continue;
                        Attributes attrs = sr.getAttributes();
                        Attribute objectclass = attrs.get("objectclass");
                        if (objectclass == null || objectclass.contains(C_zimbraAccount)) visitor.visit(makeAccount(dn, attrs, this));
                        else if (objectclass.contains(C_zimbraAlias)) visitor.visit(makeAlias(dn, attrs, this));
                        else if (objectclass.contains(C_zimbraMailList)) visitor.visit(makeDistributionList(dn, attrs, this));
                        else if (objectclass.contains(C_zimbraDomain)) visitor.visit(new LdapDomain(dn, attrs, getConfig().getDomainDefaults()));                        
                    }
                    cookie = getCookie(lctxt);
                } while (cookie != null);
            } finally {
                if (ne != null) ne.close();
            }
        } catch (InvalidSearchFilterException e) {
            throw ServiceException.INVALID_REQUEST("invalid search filter "+e.getMessage(), e);
        } catch (NameNotFoundException e) {
            // happens when base doesn't exist
            ZimbraLog.account.warn("unable to list all objects", e);
        } catch (SizeLimitExceededException e) {
            throw AccountServiceException.TOO_MANY_SEARCH_RESULTS("too many search results returned", e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to list all objects", e);
        } catch (IOException e) {
            throw ServiceException.FAILURE("unable to list all objects", e);            
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private byte[] getCookie(LdapContext lctxt) throws NamingException {
        Control[] controls = lctxt.getResponseControls();
        if (controls != null) {
            for (int i = 0; i < controls.length; i++) {
                if (controls[i] instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl prrc =
                        (PagedResultsResponseControl)controls[i];
                    return prrc.getCookie();
                }
            }
        }
        return null;
    }

    /**
     * add "uid" to list of return attrs if not specified, since we need it to construct an Account
     * @param returnAttrs
     * @return
     */
    private String[] fixReturnAttrs(String[] returnAttrs, int flags) {
        if (returnAttrs == null || returnAttrs.length == 0)
            return null;
        
        boolean needUID = true;
        boolean needID = true;
        boolean needCOSId = true;
        boolean needObjectClass = true;        
        boolean needAliasTargetId = (flags & Provisioning.SA_ALIAS_FLAG) != 0;
        boolean needCalendarUserType = true;
        
        for (int i=0; i < returnAttrs.length; i++) {
            if (Provisioning.A_uid.equalsIgnoreCase(returnAttrs[i]))
                needUID = false;
            else if (Provisioning.A_zimbraId.equalsIgnoreCase(returnAttrs[i]))
                needID = false;
            else if (Provisioning.A_zimbraCOSId.equalsIgnoreCase(returnAttrs[i]))
                needCOSId = false;
            else if (Provisioning.A_zimbraAliasTargetId.equalsIgnoreCase(returnAttrs[i]))
                needAliasTargetId = false;
            else if (Provisioning.A_objectClass.equalsIgnoreCase(returnAttrs[i]))
                needObjectClass = false;            
            else if (Provisioning.A_zimbraAccountCalendarUserType.equalsIgnoreCase(returnAttrs[i]))
            	needCalendarUserType = false;
        }
        
        int num = (needUID ? 1 : 0) + (needID ? 1 : 0) + (needCOSId ? 1 : 0) + (needAliasTargetId ? 1 : 0) + (needObjectClass ? 1 :0) + (needCalendarUserType ? 1 : 0);
        
        if (num == 0) return returnAttrs;
       
        String[] result = new String[returnAttrs.length+num];
        int i = 0;
        if (needUID) result[i++] = Provisioning.A_uid;
        if (needID) result[i++] = Provisioning.A_zimbraId;
        if (needCOSId) result[i++] = Provisioning.A_zimbraCOSId;
        if (needAliasTargetId) result[i++] = Provisioning.A_zimbraAliasTargetId;
        if (needObjectClass) result[i++] = Provisioning.A_objectClass;
        if (needCalendarUserType) result[i++] = Provisioning.A_zimbraAccountCalendarUserType;
        System.arraycopy(returnAttrs, 0, result, i, returnAttrs.length);
        return result;
    }

    public void setCOS(Account acct, Cos cos) throws ServiceException {
        HashMap<String, String> attrs = new HashMap<String, String>();
        attrs.put(Provisioning.A_zimbraCOSId, cos.getId());
        modifyAttrs(acct, attrs);
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#modifyAccountStatus(java.lang.String)
     */
    public void modifyAccountStatus(Account acct, String newStatus) throws ServiceException {
        HashMap<String, String> attrs = new HashMap<String, String>();
        attrs.put(Provisioning.A_zimbraAccountStatus, newStatus);
        modifyAttrs(acct, attrs);
    }

    
    static String[] addMultiValue(String values[], String value) {
        List<String> list = new ArrayList<String>(Arrays.asList(values));        
        list.add(value);
        return list.toArray(new String[list.size()]);
    }
    
    String[] addMultiValue(NamedEntry acct, String attr, String value) {
        return addMultiValue(acct.getMultiAttr(attr), value);
    }

    String[] removeMultiValue(NamedEntry acct, String attr, String value) {
        return LdapUtil.removeMultiValue(acct.getMultiAttr(attr), value);
    }

    public void addAlias(Account acct, String alias) throws ServiceException {
        addAliasInternal(acct, alias);
    }
	
    public void removeAlias(Account acct, String alias) throws ServiceException {
        removeAliasInternal(acct, alias, (acct==null)?null:acct.getAliases());
    }
    
    public void addAlias(DistributionList dl, String alias) throws ServiceException {
        addAliasInternal(dl, alias);
    }

    public void removeAlias(DistributionList dl, String alias) throws ServiceException {
        removeAliasInternal(dl, alias, (dl==null)?null:dl.getAliases());
    }
    
    private void addAliasInternal(NamedEntry entry, String alias) throws ServiceException {
    	
        validEmailAddress(alias);
        
        String targetDomainName = null;
        if (entry instanceof Account)
            targetDomainName = ((Account)entry).getDomainName();
        else if (entry instanceof DistributionList)
            targetDomainName = ((DistributionList)entry).getDomainName();
        else
            assert(false);
        
        alias = alias.toLowerCase().trim();
        int loc = alias.indexOf("@"); 
        if (loc == -1)
            throw ServiceException.INVALID_REQUEST("alias must include the domain", null);
        
        String aliasDomain = alias.substring(loc+1);
        String aliasName = alias.substring(0, loc);
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            Domain domain = getDomainByName(aliasDomain, ctxt);
            if (domain == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(aliasDomain);
            
            String aliasDn = mDIT.aliasDN(((LdapEntry)entry).getDN(), targetDomainName, aliasName, aliasDomain);
            // the create and addAttr ideally would be in the same transaction
            
            String aliasUuid = LdapUtil.generateUUID();
            String targetEntryId = entry.getId();
            try {
                LdapUtil.simpleCreate(ctxt, aliasDn, "zimbraAlias",
                    new String[] { Provisioning.A_uid, aliasName, 
                                   Provisioning.A_zimbraId, aliasUuid,
                                   Provisioning.A_zimbraAliasTargetId, targetEntryId} );
            } catch (NameAlreadyBoundException e) {
                /*
                 * check if the alias is a dangling alias.  If so remove the dangling alias
                 * and create a new one.
                 */
                Attributes attrs = LdapUtil.getAttributes(ctxt, aliasDn);
                Alias aliasEntry = makeAlias(aliasDn, attrs, this);
                if (aliasEntry.isDangling()) {
                    // remove the dangling alias 
                    removeAliasInternal(null, alias, null);
                    
                    // try creating teh alias again
                    LdapUtil.simpleCreate(ctxt, aliasDn, "zimbraAlias",
                            new String[] { Provisioning.A_uid, aliasName, 
                                           Provisioning.A_zimbraId, aliasUuid,
                                           Provisioning.A_zimbraAliasTargetId, targetEntryId} );
                } else {
                    // not dangling, rethrow the naming exception
                    throw e;
                }
            }
            
            HashMap<String, String[]> attrs = new HashMap<String, String[]>();
            attrs.put(Provisioning.A_zimbraMailAlias, addMultiValue(entry, Provisioning.A_zimbraMailAlias, alias));
            attrs.put(Provisioning.A_mail, addMultiValue(entry, Provisioning.A_mail, alias));
            // UGH
            modifyAttrsInternal(((NamedEntry) entry), ctxt, attrs);
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.ACCOUNT_EXISTS(alias);
        } catch (InvalidNameException e) {
            throw ServiceException.INVALID_REQUEST("invalid alias name: "+e.getMessage(), e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to create alias: "+e.getMessage(), e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }                
    }
    
    /*
     * if we have a dangling(i.e. targetId of the alias does not exist) alias, entry and aliases would both be null,
     * otherwise, entry and aliases would not be null  
     */
    private void removeAliasInternal(NamedEntry entry, String alias, String[] aliases) throws ServiceException {
        
        if (entry == null)
            ZimbraLog.account.warn("target for alias "+alias+" does not exist, removing dangling alias");
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            
            int loc = alias.indexOf("@"); 
            if (loc == -1)
                throw ServiceException.INVALID_REQUEST("alias must include the domain", null);

            alias = alias.toLowerCase();

            if (entry != null) { 
                boolean found = false;
                for (int i=0; !found && i < aliases.length; i++) {
                    found = aliases[i].equalsIgnoreCase(alias);
                }
            
                if (!found)
                    throw AccountServiceException.NO_SUCH_ALIAS(alias);
            }
            
            String aliasDomain = alias.substring(loc+1);
            String aliasName = alias.substring(0, loc);

            Domain domain = getDomainByName(aliasDomain, ctxt);
            if (domain == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(aliasDomain);
            
            String targetDn = (entry == null)?null:((LdapEntry)entry).getDN();
            String targetDomainName = null;
            if (entry != null) {
                if (entry instanceof Account)
                    targetDomainName = ((Account)entry).getDomainName();
                else if (entry instanceof DistributionList)
                    targetDomainName = ((DistributionList)entry).getDomainName();
                else
                    assert(false);
            }
            String aliasDn = mDIT.aliasDN(targetDn, targetDomainName, aliasName, aliasDomain);
            
            // if the entry exists, remove zimbraMailAlias attr first, then alias
            if (entry != null) {
                try {
                    HashMap<String, String[]> attrs = new HashMap<String, String[]>();
                    attrs.put(Provisioning.A_mail, removeMultiValue(entry, Provisioning.A_mail, alias));
                    attrs.put(Provisioning.A_zimbraMailAlias, removeMultiValue(entry, Provisioning.A_zimbraMailAlias, alias));                
                    modifyAttrsInternal(((NamedEntry)entry), ctxt, attrs);
                } catch (ServiceException e) {
                  ZimbraLog.account.warn("unable to remove zimbraMailAlias/mail attrs: "+alias);
                  // try to remove alias
                }
            }

            // remove address from all DLs
            removeAddressFromAllDistributionLists(alias);

            try {
                Attributes aliasAttrs = LdapUtil.getAttributes(ctxt, aliasDn);
                // make sure aliasedObjectName points to this account/dl(if the entry exists)
                Attribute a = aliasAttrs.get(Provisioning.A_zimbraAliasTargetId);
                if ( a != null && (entry==null || ((String)a.get()).equals(entry.getId())) ) {
                	LdapUtil.unbindEntry(ctxt, aliasDn);
                } else {
                    ZimbraLog.account.warn("unable to remove alias object: "+alias);
                }                
            } catch (NameNotFoundException e) {
                ZimbraLog.account.warn("unable to remove alias object: "+alias);                
            }
        } catch (NamingException e) {
            ZimbraLog.account.error("unable to remove alias: "+alias, e);                
            throw ServiceException.FAILURE("unable to remove alias", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }        
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.zimbra.cs.account.Provisioning#createDomain(java.lang.String,
     *      java.util.Map)
     */
    public Domain createDomain(String name, Map<String, Object> domainAttrs) throws ServiceException {
        name = name.toLowerCase().trim();
        
        validDomainName(name);
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            
            LdapDomain d = (LdapDomain) getDomainByName(name, ctxt);
            if (d != null)
                throw AccountServiceException.DOMAIN_EXISTS(name);
            
            HashMap attrManagerContext = new HashMap();
            
            // Attribute checking can not express "allow setting on
            // creation, but do not allow modifies afterwards"
            String domainType = (String) domainAttrs.get(A_zimbraDomainType);
            if (domainType == null) {
                domainType = DOMAIN_TYPE_LOCAL;
            } else {
                domainAttrs.remove(A_zimbraDomainType); // add back later
            }
            
            AttributeManager.getInstance().preModify(domainAttrs, null, attrManagerContext, true, true);
            
            // Add back attrs we circumvented from attribute checking
            domainAttrs.put(A_zimbraDomainType, domainType);
            
            String parts[] = name.split("\\.");        
            String dns[] = mDIT.domainToDNs(parts);
            createParentDomains(ctxt, parts, dns);
            
            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(domainAttrs, attrs);
            
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "dcObject");
            oc.add("organization");
            oc.add("zimbraDomain");
            
            String zimbraIdStr = LdapUtil.generateUUID();
            attrs.put(A_zimbraId, zimbraIdStr);
            attrs.put(A_zimbraDomainName, name);
            
            String mailStatus = (String) domainAttrs.get(A_zimbraMailStatus);
            if (mailStatus == null)
                attrs.put(A_zimbraMailStatus, MAIL_STATUS_ENABLED);
            
            if (domainType.equalsIgnoreCase(DOMAIN_TYPE_ALIAS)) {
                attrs.put(A_zimbraMailCatchAllAddress, "@" + name);
            }
            
            attrs.put(A_o, name+" domain");
            attrs.put(A_dc, parts[0]);
            
            String dn = dns[0];
            //NOTE: all four of these should be in a transaction...
            try {
                LdapUtil.createEntry(ctxt, dn, attrs, "createDomain");
            } catch (NameAlreadyBoundException e) {
                LdapUtil.modifyAttributes(ctxt, dn, DirContext.REPLACE_ATTRIBUTE, attrs);
            }
            
            String acctBaseDn = mDIT.domainDNToAccountBaseDN(dn);
            if (!acctBaseDn.equals(dn)) {
                /*
                 * create the account base dn entry only if if is not the same as the domain dn
                 *
                 * TODO, the objectclass(organizationalRole) and attrs(ou and cn) for the account 
                 * base dn entry is still hardcoded,  it should be parameterized in LdapDIT 
                 * according the BASE_RDN_ACCOUNT.  This is actually a design decision depending 
                 * on how far we want to allow the DIT to be customized.
                 */
                LdapUtil.simpleCreate(ctxt, mDIT.domainDNToAccountBaseDN(dn),
                                      "organizationalRole",
                                      new String[] { A_ou, "people", A_cn, "people"});
            }
            
            Domain domain = getDomainById(zimbraIdStr, ctxt);
            
            AttributeManager.getInstance().postModify(domainAttrs, domain, attrManagerContext, true);
            return domain;
            
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.DOMAIN_EXISTS(name);
        } catch (NamingException e) {
            //if (e instanceof )
            throw ServiceException.FAILURE("unable to create domain: "+name, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private LdapDomain getDomainByQuery(String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, "", query, sSubtreeSC);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                ne.close();
                return new LdapDomain(sr.getNameInNamespace(), sr.getAttributes(), getConfig().getDomainDefaults());
            }
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup domain via query: "+query+" message:"+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return null;
    }

    @Override
    public Domain get(DomainBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case name: 
                return getDomainByName(key);
            case id: 
                return getDomainById(key);
            case virtualHostname:
                return getDomainByVirtualHostname(key);
            default:
                    return null;
        }
    }
    
    private Domain getDomainById(String zimbraId, DirContext ctxt) throws ServiceException {
        if (zimbraId == null)
            return null;
        LdapDomain domain = (LdapDomain) sDomainCache.getById(zimbraId);
        if (domain == null) {
            zimbraId = LdapUtil.escapeSearchFilterArg(zimbraId);
            domain = getDomainByQuery("(&(zimbraId="+zimbraId+")(objectclass=zimbraDomain))", ctxt);
            sDomainCache.put(domain);
        }
        return domain;
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getDomainById(java.lang.String)
     */
    private Domain getDomainById(String zimbraId) throws ServiceException {
        return getDomainById(zimbraId, null);
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getDomainByName(java.lang.String)
     */
    private Domain getDomainByName(String name) throws ServiceException {
            return getDomainByName(name, null);
    }        
        
   private Domain getDomainByName(String name, DirContext ctxt) throws ServiceException {
        LdapDomain domain = (LdapDomain) sDomainCache.getByName(name);
        if (domain == null) {
            name = LdapUtil.escapeSearchFilterArg(name);
            domain = getDomainByQuery("(&(zimbraDomainName="+name+")(objectclass=zimbraDomain))", ctxt);
            sDomainCache.put(domain);
        }
        return domain;        
    }
   
   private Domain getDomainByVirtualHostname(String virtualHostname) throws ServiceException {
        LdapDomain domain = (LdapDomain) sDomainCache.getByVirtualHostname(virtualHostname);
        if (domain == null) {
            virtualHostname = LdapUtil.escapeSearchFilterArg(virtualHostname);
            domain = getDomainByQuery("(&(zimbraVirtualHostname="+virtualHostname+")(objectclass=zimbraDomain))", null);
            sDomainCache.put(domain);
        }
        return domain;        
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getAllDomains()
     */
    public List<Domain> getAllDomains() throws ServiceException {
        List<Domain> result = new ArrayList<Domain>();
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();

            NamingEnumeration ne = LdapUtil.searchDir(ctxt, "", "(objectclass=zimbraDomain)", sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapDomain(sr.getNameInNamespace(), sr.getAttributes(), getConfig().getDomainDefaults()));
            }
            ne.close();
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to list all domains", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        Collections.sort(result);
        return result;
    }

    private static boolean domainDnExists(DirContext ctxt, String dn) throws NamingException {
        try {
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, dn,"objectclass=dcObject", sObjectSC);
            boolean result = ne.hasMore();
            ne.close();
            return result;
        } catch (InvalidNameException e) {
            return false;                        
        } catch (NameNotFoundException nnfe) {
            return false;
        }
    }

    private static void createParentDomains(DirContext ctxt, String parts[], String dns[]) throws NamingException {
        for (int i=dns.length-1; i > 0; i--) {        
            if (!domainDnExists(ctxt, dns[i])) {
                String dn = dns[i];
                String domain = parts[i];
                // don't create ZimbraDomain objects, since we don't want them to show up in list domains
                LdapUtil.simpleCreate(ctxt, dn, new String[] {"dcObject", "organization"}, 
                        new String[] { A_o, domain+" domain", A_dc, domain });
            }
        }
    }
    
    public void modifyDomainStatus(Domain domain, String newStatus) throws ServiceException {
        HashMap<String, String> attrs = new HashMap<String, String>();
        attrs.put(Provisioning.A_zimbraDomainStatus, newStatus);
        modifyAttrs(domain, attrs);
        
        flushDomainCacheOnAllServers(domain.getId());
    }
    

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#createCos(java.lang.String, java.util.Map)
     */
    public Cos createCos(String name, Map<String, Object> cosAttrs) throws ServiceException {
        name = name.toLowerCase().trim();

        if (!sValidCosName.matcher(name).matches())
            throw ServiceException.INVALID_REQUEST("invalid name: "+name, null);

        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(cosAttrs, null, attrManagerContext, true, true);

        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(cosAttrs, attrs);
            LdapUtil.addAttr(attrs, A_objectClass, "zimbraCOS");
            
            String zimbraIdStr = LdapUtil.generateUUID();
            attrs.put(A_zimbraId, zimbraIdStr);
            attrs.put(A_cn, name);
            String dn = mDIT.cosNametoDN(name);
            LdapUtil.createEntry(ctxt, dn, attrs, "createCos");

            Cos cos = getCosById(zimbraIdStr, ctxt);
            AttributeManager.getInstance().postModify(cosAttrs, cos, attrManagerContext, true);
            return cos;
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.COS_EXISTS(name);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#deleteAccountById(java.lang.String)
     */
    public void renameCos(String zimbraId, String newName) throws ServiceException {
        LdapCos cos = (LdapCos) get(CosBy.id, zimbraId);
        if (cos == null)
            throw AccountServiceException.NO_SUCH_COS(zimbraId);

        if (cos.getName().equals(DEFAULT_COS_NAME))
            throw ServiceException.INVALID_REQUEST("unable to rename default cos", null);

        if (!sValidCosName.matcher(newName).matches())
            throw ServiceException.INVALID_REQUEST("invalid name: "+newName, null);
       
        newName = newName.toLowerCase().trim();
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            String newDn = mDIT.cosNametoDN(newName);
            LdapUtil.renameEntry(ctxt, cos.getDN(), newDn);
            // remove old account from cache
            sCosCache.remove(cos);
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.COS_EXISTS(newName);            
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to rename cos: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private LdapCos getCOSByQuery(String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.cosBaseDN(), query, sSubtreeSC);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                ne.close();
                return new LdapCos(sr.getNameInNamespace(), sr.getAttributes());
            }
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup cos via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return null;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getCOSById(java.lang.String)
     */
    private Cos getCosById(String zimbraId, DirContext ctxt ) throws ServiceException {
        if (zimbraId == null)
            return null;

        LdapCos cos = sCosCache.getById(zimbraId);
        if (cos == null) {
            zimbraId = LdapUtil.escapeSearchFilterArg(zimbraId);
            cos = getCOSByQuery("(&(zimbraId="+zimbraId+")(objectclass=zimbraCOS))", ctxt);
            sCosCache.put(cos);
        }
        return cos;
    }

    @Override
    public Cos get(CosBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case name:
                return getCosByName(key, null);                
            case id:
                return getCosById(key, null);                
            default:
                    return null;
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getCOSByName(java.lang.String)
     */
    private Cos getCosByName(String name, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        LdapCos cos = sCosCache.getByName(name);
        if (cos != null)
            return cos;

        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            String dn = mDIT.cosNametoDN(name);            
            Attributes attrs = LdapUtil.getAttributes(ctxt, dn);
            cos  = new LdapCos(dn, attrs);
            sCosCache.put(cos);            
            return cos;
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup COS by name: "+name+" message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#getAllCOS()
     */
    public List<Cos> getAllCos() throws ServiceException {
        List<Cos> result = new ArrayList<Cos>();
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.cosBaseDN(), "(objectclass=zimbraCOS)", sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapCos(sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to list all COS", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }

        Collections.sort(result);
        return result;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#deleteAccountById(java.lang.String)
     */
    public void deleteAccount(String zimbraId) throws ServiceException {
        Account acc = getAccountById(zimbraId);
        LdapEntry entry = (LdapEntry) getAccountById(zimbraId);
        if (acc == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(zimbraId);

        removeAddressFromAllDistributionLists(acc.getName()); // this doesn't throw any exceptions

        String aliases[] = acc.getAliases();
        if (aliases != null)
            for (int i=0; i < aliases.length; i++)
                removeAlias(acc, aliases[i]); // this also removes each alias from any DLs

        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            
            LdapUtil.deleteChildren(ctxt, entry.getDN());
            LdapUtil.unbindEntry(ctxt, entry.getDN());
            sAccountCache.remove(acc.getName(), acc.getId());
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to purge account: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#deleteAccountById(java.lang.String)
     */
    public void renameAccount(String zimbraId, String newName) throws ServiceException {
        
        validEmailAddress(newName);
        
        DirContext ctxt = null;
        Account acct = getAccountById(zimbraId, ctxt);
        LdapEntry entry = (LdapEntry) acct;
        if (acct == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(zimbraId);
        String oldEmail = acct.getName();
        try {
            ctxt = LdapUtil.getDirContext(true);

            String oldDn = entry.getDN();
            String oldDomain = EmailUtil.getValidDomainPart(oldEmail);
            
            newName = newName.toLowerCase().trim();
            String[] parts = EmailUtil.getLocalPartAndDomain(newName);
            if (parts == null)
                throw ServiceException.INVALID_REQUEST("bad value for newName", null);
            String newLocal = parts[0];
            String newDomain = parts[1];
            
            Domain domain = getDomainByName(newDomain, ctxt);
            if (domain == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(newDomain);
            
            String newDn = mDIT.accountDNRename(oldDn, newLocal, domain.getName());
            boolean dnChanged = (!newDn.equals(oldDn));

            Map<String,Object> newAttrs = acct.getAttrs(false);
            
            newAttrs.put(Provisioning.A_uid, newLocal);
            newAttrs.put(Provisioning.A_zimbraMailDeliveryAddress, newName);
            
            ReplaceAddressResult replacedMails = replaceMailAddresses(acct, Provisioning.A_mail, oldEmail, newName);
            if (replacedMails.newAddrs().length == 0) {
                // Set mail to newName if the account currently does not have a mail
                newAttrs.put(Provisioning.A_mail, newName);
            } else {
                newAttrs.put(Provisioning.A_mail, replacedMails.newAddrs());
            }
            
            boolean domainChanged = !oldDomain.equals(newDomain);
            ReplaceAddressResult replacedAliases = replaceMailAddresses(acct, Provisioning.A_zimbraMailAlias, oldEmail, newName);
            if (replacedAliases.newAddrs().length > 0) {
                newAttrs.put(Provisioning.A_zimbraMailAlias, replacedAliases.newAddrs());
                
                String newDomainDN = mDIT.domainToAccountSearchDN(newDomain);
                
                // check up front if any of renamed aliases already exists in the new domain (if domain also got changed)
                if (domainChanged && addressExists(ctxt, newDomainDN, replacedAliases.newAddrs()))
                    throw AccountServiceException.ACCOUNT_EXISTS(newName);    
            }
 
            Attributes attributes = new BasicAttributes(true);
            LdapUtil.mapToAttrs(newAttrs, attributes);

            if (dnChanged) {
                LdapUtil.createEntry(ctxt, newDn, attributes, "createAccount");         

                // MOVE OVER the account and all identities/sources/signatures etc. doesn't throw an exception, just logs
                LdapUtil.moveChildren(ctxt, oldDn, newDn);
            }
            
            // rename the account and all it's renamed aliases to the new name in all distribution lists
            // doesn't throw exceptions, just logs
            renameAddressesInAllDistributionLists(oldEmail, newName, replacedAliases);
            
            // MOVE OVER ALL aliases
            // doesn't throw exceptions, just logs
            if (domainChanged)
                moveAliases(ctxt, replacedAliases, newDomain, null, oldDn, newDn, oldDomain, newDomain);
            
            // unbind old dn
            if (dnChanged)
                LdapUtil.unbindEntry(ctxt, oldDn);
            else
                modifyAttrs(acct, newAttrs);
            
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.ACCOUNT_EXISTS(newName);            
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to rename account: "+zimbraId, e);
        } finally {
            // prune cache, prune in finally instead of end of the try block because
            // in case exceptions were thrown, the cache might have been updated to the 
            // new values which are not actually committed to the LDAP store.
            sAccountCache.remove(oldEmail, acct.getId());
            LdapUtil.closeContext(ctxt);
        }
    }
    
    public void deleteDomain(String zimbraId) throws ServiceException {
        // TODO: should only allow a domain delete to succeed if there are no people
        // if there aren't, we need to delete the people trees first, then delete the domain.
        DirContext ctxt = null;
        LdapDomain d = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            d = (LdapDomain) getDomainById(zimbraId, ctxt);
            if (d == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(zimbraId);

            String name = d.getName();

            String acctBaseDn = mDIT.domainDNToAccountBaseDN(d.getDN());
            if (!acctBaseDn.equals(d.getDN()))
                LdapUtil.unbindEntry(ctxt, acctBaseDn);
            
            try {
            	LdapUtil.unbindEntry(ctxt, d.getDN());
                sDomainCache.remove(d);                
            } catch (ContextNotEmptyException e) {
                // remove from cache before nuking all attrs
                sDomainCache.remove(d);                
                // assume subdomains exist and turn into plain dc object
                Map<String, String> attrs = new HashMap<String, String>();
                attrs.put("-"+A_objectClass, "zimbraDomain");
                // remove all zimbra attrs
                for (String key : d.getAttrs(false).keySet()) {
                    if (key.startsWith("zimbra")) 
                        attrs.put(key, "");
                }
                // cannot invoke callback here.  If another domain attr is added in a callback, 
                // e.g. zimbraDomainStatus would add zimbraMailStatus, then we will get a LDAP 
                // schema violation naming error(zimbraDomain is removed, thus there cannot be 
                // any zimbraAttrs left) and the modify will fail.    
                modifyAttrs(d, attrs, false, false); 
            }

            String defaultDomain = getConfig().getAttr(A_zimbraDefaultDomainName, null);
            if (name.equalsIgnoreCase(defaultDomain)) {
                try {
                    Map<String, String> attrs = new HashMap<String, String>();
                    attrs.put(A_zimbraDefaultDomainName, "");
                    modifyAttrs(getConfig(), attrs);
                } catch (Exception e) {
                    ZimbraLog.account.warn("unable to remove config attr:"+A_zimbraDefaultDomainName, e); 
                }
            }
        } catch (ContextNotEmptyException e) {
            throw AccountServiceException.DOMAIN_NOT_EMPTY(d.getName());
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to purge domain: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    

    
    public void renameDomain(String zimbraId, String newDomainName) throws ServiceException {
        newDomainName = newDomainName.toLowerCase().trim();
        validDomainName(newDomainName);
        
        DirContext ctxt = null;
        
        try {
            ctxt = LdapUtil.getDirContext(true);
            
            Domain oldDomain = getDomainById(zimbraId, ctxt);
            if (oldDomain == null)
               throw AccountServiceException.NO_SUCH_DOMAIN(zimbraId);
            
            String oldDomainName = oldDomain.getName();
            
            RenameDomain rd = new RenameDomain(ctxt, this, oldDomain, newDomainName);
            rd.execute();
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    
    
    public void deleteCos(String zimbraId) throws ServiceException {
        LdapCos c = (LdapCos) get(CosBy.id, zimbraId);
        if (c == null)
            throw AccountServiceException.NO_SUCH_COS(zimbraId);
        
        if (c.getName().equals(DEFAULT_COS_NAME))
            throw ServiceException.INVALID_REQUEST("unable to delete default cos", null);

        // TODO: should we go through all accounts with this cos and remove the zimbraCOSId attr?
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            LdapUtil.unbindEntry(ctxt, c.getDN());
            sCosCache.remove(c);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to purge cos: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#createServer(java.lang.String, java.util.Map)
     */
    public Server createServer(String name, Map<String, Object> serverAttrs) throws ServiceException {
        name = name.toLowerCase().trim();

        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(serverAttrs, null, attrManagerContext, true, true);

        String authHost = (String)serverAttrs.get(A_zimbraMtaAuthHost);
        if (authHost != null) {
            serverAttrs.put(A_zimbraMtaAuthURL, URLUtil.getMtaAuthURL(authHost));
        }
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(serverAttrs, attrs);
            LdapUtil.addAttr(attrs, A_objectClass, "zimbraServer");

            String zimbraIdStr = LdapUtil.generateUUID();
            attrs.put(A_zimbraId, zimbraIdStr);
            attrs.put(A_cn, name);
            String dn = mDIT.serverNametoDN(name);
            
            String zimbraServerHostname = null;

            Attribute zimbraServiceHostnameAttr = attrs.get(Provisioning.A_zimbraServiceHostname);
            if (zimbraServiceHostnameAttr == null) {
                zimbraServerHostname = name;
                attrs.put(Provisioning.A_zimbraServiceHostname, name);
            } else {
                zimbraServerHostname = (String) zimbraServiceHostnameAttr.get();
            }
            
            LdapUtil.createEntry(ctxt, dn, attrs, "createServer");

            Server server = getServerById(zimbraIdStr, ctxt, true);
            AttributeManager.getInstance().postModify(serverAttrs, server, attrManagerContext, true);
            return server;

        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.SERVER_EXISTS(name);
        } catch (NamingException e) {
            //if (e instanceof )
            throw ServiceException.FAILURE("unable to create server: "+name+" message: "+e.getMessage(), e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private Server getServerByQuery(String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.serverBaseDN(), query, sSubtreeSC);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                ne.close();
                return new LdapServer(sr.getNameInNamespace(), sr.getAttributes(), getConfig().getServerDefaults());
            }
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup server via query: "+query+" message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return null;
    }

    private Server getServerById(String zimbraId, DirContext ctxt, boolean nocache) throws ServiceException {
        if (zimbraId == null)
            return null;
        Server s = null;
        if (!nocache)
            s = sServerCache.getById(zimbraId);
        if (s == null) {
            zimbraId = LdapUtil.escapeSearchFilterArg(zimbraId);
            s = (Server)getServerByQuery("(&(zimbraId="+zimbraId+")(objectclass=zimbraServer))", ctxt); 
            sServerCache.put(s);
        }
        return s;
    }

    @Override
    public Server get(ServerBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case name: 
                return getServerByName(key);
            case id: 
                return getServerById(key);
            case serviceHostname:
                List servers = getAllServers();
                for (Iterator it = servers.iterator(); it.hasNext(); ) {
                    Server s = (Server) it.next();
                    // when replication is enabled, should return server representing current master
                    if (key.equalsIgnoreCase(s.getAttr(Provisioning.A_zimbraServiceHostname, ""))) {
                        return s;
                    }
                }
                return null;
            default:
                    return null;
        }
    }

    private Server getServerById(String zimbraId) throws ServiceException {
        return getServerById(zimbraId, null, false);
    }

    private Server getServerByName(String name) throws ServiceException {
        return getServerByName(name, false);
    }

    private Server getServerByName(String name, boolean nocache) throws ServiceException {
        if (!nocache) {
        	Server s = sServerCache.getByName(name);
            if (s != null)
                return s;
        }
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            String dn = mDIT.serverNametoDN(name);            
            Attributes attrs = LdapUtil.getAttributes(ctxt, dn);
            LdapServer s = new LdapServer(dn, attrs, getConfig().getServerDefaults());
            sServerCache.put(s);            
            return s;
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;            
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup server by name: "+name+" message: "+e.getMessage(), e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    public List<Server> getAllServers() throws ServiceException {
        return getAllServers(null);
    }
    
    public List<Server> getAllServers(String service) throws ServiceException {
        List<Server> result = new ArrayList<Server>();
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            String filter = "(objectclass=zimbraServer)";
            if (service != null) {
                filter = "(&(objectclass=zimbraServer)(zimbraServiceEnabled=" + LdapUtil.escapeSearchFilterArg(service) + "))";
            } else {
                filter = "(objectclass=zimbraServer)";
            }
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.serverBaseDN(), filter, sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                LdapServer s = new LdapServer(sr.getNameInNamespace(), sr.getAttributes(), getConfig().getServerDefaults());
                result.add(s);
            }
            ne.close();
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to list all servers", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        if (result.size() > 0)
            sServerCache.put(result, true);
        Collections.sort(result);
        return result;
    }

    private List<Cos> searchCOS(String query, DirContext initCtxt) throws ServiceException {
        List<Cos> result = new ArrayList<Cos>();
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, mDIT.cosBaseDN(), "(&(objectclass=zimbraCOS)" + query + ")", sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapCos(sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup cos via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return result;
    }

    private void removeServerFromAllCOSes(String server, DirContext initCtxt) {
        List<Cos> coses = null;
        try {
            coses = searchCOS("(" + Provisioning.A_zimbraMailHostPool + "=" + server +")", initCtxt);
            for (Cos cos: coses) {
                Map<String, String> attrs = new HashMap<String, String>();
                attrs.put("-"+Provisioning.A_zimbraMailHostPool, server);
                modifyAttrs(cos, attrs);
                // invalidate cached cos 
                sCosCache.remove((LdapCos)cos);
            }
        } catch (ServiceException se) {
            ZimbraLog.account.warn("unable to remove "+server+" from all COSes ", se);
            return;
        }

     }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#purgeServer(java.lang.String)
     */
    public void deleteServer(String zimbraId) throws ServiceException {
        LdapServer s = (LdapServer) getServerById(zimbraId);
        if (s == null)
            throw AccountServiceException.NO_SUCH_SERVER(zimbraId);

        // TODO: what if accounts still have this server as a mailbox?
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            removeServerFromAllCOSes(zimbraId, ctxt);
            LdapUtil.unbindEntry(ctxt, s.getDN());
            sServerCache.remove(s);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to purge server: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    /*
     *  Distribution lists.
     */

    public DistributionList createDistributionList(String listAddress, Map<String, Object> listAttrs) throws ServiceException {

        validEmailAddress(listAddress);
        
        SpecialAttrs specialAttrs = mDIT.handleSpecialAttrs(listAttrs);
        String baseDn = specialAttrs.getLdapBaseDn();
        
        listAddress = listAddress.toLowerCase().trim();

        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(listAttrs, null, attrManagerContext, true, true);

        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            String parts[] = listAddress.split("@");
            if (parts.length != 2)
                throw ServiceException.INVALID_REQUEST("must be valid list address: " + listAddress, null);

            String localPart = parts[0];
            String domain = parts[1];

            Domain d = getDomainByName(domain, ctxt);
            if (d == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(domain);
            String domainType = d.getAttr(Provisioning.A_zimbraDomainType, Provisioning.DOMAIN_TYPE_LOCAL);
            if (!domainType.equals(Provisioning.DOMAIN_TYPE_LOCAL))
                throw ServiceException.INVALID_REQUEST("domain type must be local", null);

            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(listAttrs, attrs);
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "zimbraDistributionList");
            oc.add("zimbraMailRecipient");

            String zimbraIdStr = LdapUtil.generateUUID();
            attrs.put(A_zimbraId, zimbraIdStr);
            attrs.put(A_zimbraMailAlias, listAddress);
            attrs.put(A_mail, listAddress);

            // by default a distribution list is always created enabled
            if (attrs.get(Provisioning.A_zimbraMailStatus) == null) {
                attrs.put(A_zimbraMailStatus, MAIL_STATUS_ENABLED);
            }
            
            Attribute a = attrs.get(Provisioning.A_displayName);             
            if (a != null) {
                attrs.put(A_cn, a.get());
            }
            
            attrs.put(A_uid, localPart);
            
            String dn = mDIT.distributionListDNCreate(baseDn, attrs, localPart, domain);
            
            LdapUtil.createEntry(ctxt, dn, attrs, "createDistributionList");

            DistributionList dlist = getDistributionListById(zimbraIdStr, ctxt);
            AttributeManager.getInstance().postModify(listAttrs, dlist, attrManagerContext, true);
            return dlist;

        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.DISTRIBUTION_LIST_EXISTS(listAddress);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to create distribution listt: "+listAddress, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    public List<DistributionList> getDistributionLists(DistributionList list, boolean directOnly, Map<String, String> via) throws ServiceException {
        String addrs[] = getAllAddrsForDistributionList(list);
        return getDistributionLists(addrs, directOnly, via, false);
    }

    private DistributionList getDistributionListByQuery(String base, String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, base, query, sSubtreeSC);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                ne.close();
                return makeDistributionList(sr.getNameInNamespace(), sr.getAttributes(), this);
            }
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup distribution list via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return null;
    }

    public void renameDistributionList(String zimbraId, String newEmail) throws ServiceException {
        
        validEmailAddress(newEmail);
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            
            LdapDistributionList dl = (LdapDistributionList) getDistributionListById(zimbraId, ctxt);
            if (dl == null)
                throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(zimbraId);

            String oldEmail = dl.getName();
            String oldDomain = EmailUtil.getValidDomainPart(oldEmail);
            
            newEmail = newEmail.toLowerCase().trim();
            String[] parts = EmailUtil.getLocalPartAndDomain(newEmail);
            if (parts == null)
                throw ServiceException.INVALID_REQUEST("bad value for newName", null);
            String newLocal = parts[0];
            String newDomain = parts[1];

            boolean domainChanged = !oldDomain.equals(newDomain);

            Domain domain = getDomainByName(newDomain, ctxt);
            if (domain == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(newDomain);
    
            Map<String, Object> attrs = new HashMap<String, Object>();
  
            ReplaceAddressResult replacedMails = replaceMailAddresses(dl, Provisioning.A_mail, oldEmail, newEmail);
            if (replacedMails.newAddrs().length == 0) {
                // Set mail to newName if the account currently does not have a mail
            	attrs.put(Provisioning.A_mail, newEmail);
            } else {
            	attrs.put(Provisioning.A_mail, replacedMails.newAddrs());
            }
            
            ReplaceAddressResult replacedAliases = replaceMailAddresses(dl, Provisioning.A_zimbraMailAlias, oldEmail, newEmail);
            if (replacedAliases.newAddrs().length > 0) {
            	attrs.put(Provisioning.A_zimbraMailAlias, replacedAliases.newAddrs());
                
                String newDomainDN = mDIT.domainToAccountSearchDN(newDomain);
                
                // check up front if any of renamed aliases already exists in the new domain (if domain also got changed)
                if (domainChanged && addressExists(ctxt, newDomainDN, replacedAliases.newAddrs()))
                    throw AccountServiceException.DISTRIBUTION_LIST_EXISTS(newEmail);    
            }
 
            // move over the distribution list entry
            String oldDn = dl.getDN();
            String newDn = mDIT.distributionListDNRename(oldDn, newLocal, domain.getName());
            boolean dnChanged = (!oldDn.equals(newDn));
            
            if (dnChanged)
                LdapUtil.renameEntry(ctxt, oldDn, newDn);
            
            dl = (LdapDistributionList) getDistributionListById(zimbraId,ctxt);
            
            // rename the distribution list and all it's renamed aliases to the new name in all distribution lists
            // doesn't throw exceptions, just logs
            renameAddressesInAllDistributionLists(oldEmail, newEmail, replacedAliases); // doesn't throw exceptions, just logs

            // MOVE OVER ALL aliases
            // doesn't throw exceptions, just logs
            if (domainChanged) {
            	String newUid = dl.getAttr(Provisioning.A_uid);
                moveAliases(ctxt, replacedAliases, newDomain, newUid, oldDn, newDn, oldDomain, newDomain);
            }
 
            // this is non-atomic. i.e., rename could succeed and updating A_mail
            // could fail. So catch service exception here and log error            
            try {
                modifyAttrsInternal(dl, ctxt, attrs);
            } catch (ServiceException e) {
                ZimbraLog.account.error("distribution list renamed to " + newLocal +
                        " but failed to move old name's LDAP attributes", e);
                throw ServiceException.FAILURE("unable to rename distribution list: "+zimbraId, e);
            }
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.DISTRIBUTION_LIST_EXISTS(newEmail);            
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to rename distribution list: " + zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public DistributionList get(DistributionListBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case id: 
                return getDistributionListById(key);
            case name: 
                return getDistributionListByName(key);
            default:
                    return null;
        }
    }

    private DistributionList getDistributionListById(String zimbraId, DirContext ctxt) throws ServiceException {
        //zimbraId = LdapUtil.escapeSearchFilterArg(zimbraId);
        return getDistributionListByQuery("","(&(zimbraId="+zimbraId+")" + 
                                          FILTER_DISTRIBUTION_LIST_OBJECTCLASS+ ")", ctxt);
    }

    private DistributionList getDistributionListById(String zimbraId) throws ServiceException {
        return getDistributionListById(zimbraId, null);
    }

    public void deleteDistributionList(String zimbraId) throws ServiceException {
        LdapDistributionList dl = (LdapDistributionList) getDistributionListById(zimbraId);
        if (dl == null)
            throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(zimbraId);
        
        removeAddressFromAllDistributionLists(dl.getName()); // this doesn't throw any exceptions
        
        String aliases[] = dl.getAliases();
        if (aliases != null)
            for (int i=0; i < aliases.length; i++)
                removeAlias(dl, aliases[i]); // this also removes each alias from any DLs
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            LdapUtil.unbindEntry(ctxt, dl.getDN());
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to purge distribution list: "+zimbraId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    private DistributionList getDistributionListByName(String listAddress) throws ServiceException {
        String parts[] = listAddress.split("@");
        
        if (parts.length != 2)
            throw ServiceException.INVALID_REQUEST("must be valid list address: "+listAddress, null);

        String uid = LdapUtil.escapeSearchFilterArg(parts[0]);
        String domain = parts[1];
        return getDistributionListByQuery("", 
                                          "(&(zimbraMailAlias="+listAddress+")" +
                                          FILTER_DISTRIBUTION_LIST_OBJECTCLASS+ ")",
                                          null);
    }
    
    public Server getLocalServer() throws ServiceException {
        String hostname = LC.zimbra_server_hostname.value();
        if (hostname == null) {
            Zimbra.halt("zimbra_server_hostname not specified in localconfig.xml");
        }
        Server local = getServerByName(hostname);
        if (local == null) {
            Zimbra.halt("Could not find an LDAP entry for server '" + hostname + "'");
        }
        return local;
    }

    /**
     * checks to make sure the specified address is a valid email address (addr part only, no personal part) 
     *
     * TODO To change the template for this generated type comment go to
     * Window - Preferences - Java - Code Style - Code Templates
     * @throws ServiceException
     */
    private static void validEmailAddress(String addr) throws ServiceException {
        try {
            InternetAddress ia = new InternetAddress(addr, true);
            // is this even needed?
            // ia.validate();
            if (ia.getPersonal() != null && !ia.getPersonal().equals(""))
                throw ServiceException.INVALID_REQUEST("invalid email address", null);
        } catch (AddressException e) {
            throw ServiceException.INVALID_REQUEST("invalid email address", e);
        }
    }
    
    private static void validDomainName(String domain) throws ServiceException {
        String email = "test" + "@" + domain;
        try {
            validEmailAddress(email);
        } catch (ServiceException e) {
            throw ServiceException.INVALID_REQUEST("invalid domain name " + domain, null);
        }
    }
    
    public static final long TIMESTAMP_WINDOW = Constants.MILLIS_PER_MINUTE * 5; 

    private void checkAccountStatus(Account acct) throws ServiceException {
        reload(acct);
        String accountStatus = acct.getAccountStatus();
        if (accountStatus == null)
            throw AccountServiceException.AUTH_FAILED(acct.getName());
        if (accountStatus.equals(Provisioning.ACCOUNT_STATUS_MAINTENANCE)) 
            throw AccountServiceException.MAINTENANCE_MODE();

        if (!(accountStatus.equals(Provisioning.ACCOUNT_STATUS_ACTIVE) ||
                accountStatus.equals(Provisioning.ACCOUNT_STATUS_LOCKOUT)))
            throw AccountServiceException.AUTH_FAILED(acct.getName());
    }
    
    @Override
    public void preAuthAccount(Account acct, String acctValue, String acctBy, long timestamp, long expires, String preAuth) throws ServiceException {
        checkAccountStatus(acct);
        if (preAuth == null || preAuth.length() == 0)
            throw ServiceException.INVALID_REQUEST("preAuth must not be empty", null);
	
        // see if domain is configured for preauth
        Provisioning prov = Provisioning.getInstance();
        String domainPreAuthKey = prov.getDomain(acct).getAttr(Provisioning.A_zimbraPreAuthKey, null);
        if (domainPreAuthKey == null)
            throw ServiceException.INVALID_REQUEST("domain is not configured for preauth", null);
        
        // see if request is recent
        long now = System.currentTimeMillis();
	    long diff = Math.abs(now-timestamp);
	    if (diff > TIMESTAMP_WINDOW)
	        throw AccountServiceException.AUTH_FAILED(acct.getName()+" (preauth timestamp is too old)");
        
	    // compute expected preAuth
	    HashMap<String,String> params = new HashMap<String,String>();
	    params.put("account", acctValue);
	    params.put("by", acctBy);
	    params.put("timestamp", timestamp+"");
	    params.put("expires", expires+"");
	    String computedPreAuth = PreAuthKey.computePreAuth(params, domainPreAuthKey);
	    if (!computedPreAuth.equalsIgnoreCase(preAuth))
	        throw AccountServiceException.AUTH_FAILED(acct.getName()+" (preauth mismatch)");
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#authAccount(java.lang.String)
     */
    @Override    
    public void authAccount(Account acct, String password, String proto) throws ServiceException {
        try {
            if (password == null || password.equals(""))
                throw AccountServiceException.AUTH_FAILED(acct.getName()+ " (empty password)");
            authAccount(acct, password, true);
            ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                    new String[] {"cmd", "Auth","account", acct.getName(), "protocol", proto}));
        } catch (ServiceException e) {
            ZimbraLog.security.warn(ZimbraLog.encodeAttrs(
                    new String[] {"cmd", "Auth","account", acct.getName(), "protocol", proto, "error", e.getMessage()}));             
            throw e;
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#authAccount(java.lang.String)
     */
    private void authAccount(Account acct, String password, boolean checkPasswordPolicy) throws ServiceException {
        checkAccountStatus(acct);
        
        AuthMechanism authMech = new AuthMechanism(acct);
        verifyPassword(acct, password, authMech);

        if (!checkPasswordPolicy || !authMech.isZimbraAuth())
            return;

        // below this point, the only fault that may be thrown is CHANGE_PASSWORD
        int maxAge = acct.getIntAttr(Provisioning.A_zimbraPasswordMaxAge, 0);
        if (maxAge > 0) {
            Date lastChange = acct.getGeneralizedTimeAttr(Provisioning.A_zimbraPasswordModifiedTime, null);
            if (lastChange != null) {
                long last = lastChange.getTime();
                long curr = System.currentTimeMillis();
                if ((last+(ONE_DAY_IN_MILLIS * maxAge)) < curr)
                    throw AccountServiceException.CHANGE_PASSWORD();
            }
        }

        boolean mustChange = acct.getBooleanAttr(Provisioning.A_zimbraPasswordMustChange, false);
        if (mustChange)
            throw AccountServiceException.CHANGE_PASSWORD();

        // update/check last logon
        Date lastLogon = acct.getGeneralizedTimeAttr(Provisioning.A_zimbraLastLogonTimestamp, null);
        if (lastLogon == null) {
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.put(Provisioning.A_zimbraLastLogonTimestamp, DateUtil.toGeneralizedTime(new Date()));
            try {
                modifyAttrs(acct, attrs);
            } catch (ServiceException e) {
                ZimbraLog.account.warn("updating zimbraLastLogonTimestamp", e);
            }
        } else {
            Config config = Provisioning.getInstance().getConfig();
            long freq = config.getTimeInterval(
                    Provisioning.A_zimbraLastLogonTimestampFrequency,
                    com.zimbra.cs.util.Config.D_ZIMBRA_LAST_LOGON_TIMESTAMP_FREQUENCY);
            long current = System.currentTimeMillis();
            if (current - freq >= lastLogon.getTime()) {
                Map<String, String> attrs = new HashMap<String , String>();
                attrs.put(Provisioning.A_zimbraLastLogonTimestamp, DateUtil.toGeneralizedTime(new Date()));
                try {
                    modifyAttrs(acct, attrs);
                } catch (ServiceException e) {
                    ZimbraLog.account.warn("updating zimbraLastLogonTimestamp", e);
                }
            }
        }
    }

    private void externalLdapAuth(Domain d, String authMech, Account acct, String password) throws ServiceException {
        String url[] = d.getMultiAttr(Provisioning.A_zimbraAuthLdapURL);
        
        if (url == null || url.length == 0) {
            String msg = "attr not set "+Provisioning.A_zimbraAuthLdapURL;
            ZimbraLog.account.fatal(msg);
            throw ServiceException.FAILURE(msg, null);
        }

        try {
            // try explicit externalDn first
            String externalDn = acct.getAttr(Provisioning.A_zimbraAuthLdapExternalDn);

            if (externalDn != null) {
                if (ZimbraLog.account.isDebugEnabled()) ZimbraLog.account.debug("auth with explicit dn of "+externalDn);
                LdapUtil.ldapAuthenticate(url, externalDn, password);
                return;
            }

            String searchFilter = d.getAttr(Provisioning.A_zimbraAuthLdapSearchFilter);
            if (searchFilter != null && !AM_AD.equals(authMech)) {
                String searchPassword = d.getAttr(Provisioning.A_zimbraAuthLdapSearchBindPassword);
                String searchDn = d.getAttr(Provisioning.A_zimbraAuthLdapSearchBindDn);
                String searchBase = d.getAttr(Provisioning.A_zimbraAuthLdapSearchBase);
                if (searchBase == null) searchBase = "";
                searchFilter = LdapUtil.computeAuthDn(acct.getName(), searchFilter);
                if (ZimbraLog.account.isDebugEnabled()) ZimbraLog.account.debug("auth with search filter of "+searchFilter);
                LdapUtil.ldapAuthenticate(url, password, searchBase, searchFilter, searchDn, searchPassword);
                return;
            }
            
            String bindDn = d.getAttr(Provisioning.A_zimbraAuthLdapBindDn);
            if (bindDn != null) {
                String dn = LdapUtil.computeAuthDn(acct.getName(), bindDn);
                if (ZimbraLog.account.isDebugEnabled()) ZimbraLog.account.debug("auth with bind dn template of "+dn);
                LdapUtil.ldapAuthenticate(url, dn, password);
                return;
            }

        } catch (AuthenticationException e) {
            throw AccountServiceException.AUTH_FAILED(acct.getName(), e);
        } catch (AuthenticationNotSupportedException e) {
            throw AccountServiceException.AUTH_FAILED(acct.getName(), e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE(e.getMessage(), e);
        }
        
        String msg = "one of the following attrs must be set "+
                Provisioning.A_zimbraAuthLdapBindDn+", "+Provisioning.A_zimbraAuthLdapSearchFilter;
        ZimbraLog.account.fatal(msg);
        throw ServiceException.FAILURE(msg, null);
    }

    private void verifyPassword(Account acct, String password, AuthMechanism authMech) throws ServiceException {
        
        LdapLockoutPolicy lockoutPolicy = new LdapLockoutPolicy(this, acct);
        try {
            if (lockoutPolicy.isLockedOut())
                throw AccountServiceException.AUTH_FAILED(acct.getName());

            // attempt to verify the password
            verifyPasswordInternal(acct, password, authMech);

            lockoutPolicy.successfulLogin();
        } catch (AccountServiceException e) {
            // TODO: only consider it failed if exception was due to password-mismatch
            lockoutPolicy.failedLogin();
            // re-throw original exception
            throw e;
        }
    }

    /*
     * authAccount does all the status/mustChange checks, this just takes the
     * password and auths the user
     */
    private void verifyPasswordInternal(Account acct, String password, AuthMechanism authMech) throws ServiceException {
        String encodedPassword = acct.getAttr(Provisioning.A_userPassword);
        
        AuthMechanism.AuthMechType authMechType = authMech.getType();
        
        Domain d = Provisioning.getInstance().getDomain(acct);
        
        boolean allowFallback = true;
        if (!authMech.isZimbraAuth())
            allowFallback = 
                d.getBooleanAttr(Provisioning.A_zimbraAuthFallbackToLocal, false) ||
                acct.getBooleanAttr(Provisioning.A_zimbraIsAdminAccount, false) ||
                acct.getBooleanAttr(Provisioning.A_zimbraIsDomainAdminAccount, false); 
        
        if (authMechType == AuthMechanism.AuthMechType.AMT_LDAP) {
            try {
                externalLdapAuth(d, authMech.getHandler(), acct, password);
                return;
            } catch (ServiceException e) {
                if (!allowFallback) 
                    throw e;
                ZimbraLog.account.warn(authMech.getHandler() + " auth for domain " +
                                       d.getName() + " failed, falling back to default mech");
            }
        } else if (authMechType == AuthMechanism.AuthMechType.AMT_CUSTOM) {
            String handlerName = authMech.getHandler();
            ZimbraCustomAuth handler = ZimbraCustomAuth.getHandler(handlerName);
            if (handler == null) {
                String msg = "handler " + handlerName + " for custom auth for domain " + d.getName() + " not found";
                if (!allowFallback) 
                    throw AccountServiceException.AUTH_FAILED(acct.getName(), new Exception(msg));
                ZimbraLog.account.warn(msg + "falling back to default mech");
            } else {
                try {
                    handler.authenticate(acct, password);
                    return;
                } catch (Exception e) {
                    if (!allowFallback) {
                        if (e instanceof ServiceException) {
                            throw (ServiceException)e;
                        } else {   
                            String msg = e.getMessage();
                            if (StringUtil.isNullOrEmpty(msg))
                                msg = "";
                            else
                                msg = " (" + msg + ")";
                            throw AccountServiceException.AUTH_FAILED(acct.getName() + msg , e);
                        }
                    }
                    
                    ZimbraLog.account.warn("custom auth " + authMech.getHandler() + " for domain " +
                                           d.getName() + " failed, falling back to default mech");
                }
            }
        }

        // fall back to zimbra
        if (encodedPassword == null)
            throw AccountServiceException.AUTH_FAILED(acct.getName());

        if (LdapUtil.isSSHA(encodedPassword)) {

            if (LdapUtil.verifySSHA(encodedPassword, password)) {
                return; // good password, RETURN
            }

        } else if (acct instanceof LdapEntry) {
            String[] urls = new String[] { LdapUtil.getLdapURL() };
            try {
                LdapUtil.ldapAuthenticate(urls, ((LdapEntry)acct).getDN(), password);
                return; // good password, RETURN                
            } catch (AuthenticationException e) {
                throw AccountServiceException.AUTH_FAILED(acct.getName(), e);
            } catch (AuthenticationNotSupportedException e) {
                throw AccountServiceException.AUTH_FAILED(acct.getName(), e);
            } catch (NamingException e) {
                throw ServiceException.FAILURE(e.getMessage(), e);
            }
        }
        throw AccountServiceException.AUTH_FAILED(acct.getName());        
    }
 
     /**
       * Takes the specified format string, and replaces any % followed by a single character
       * with the value in the specified vars hash. If the value isn't found in the hash, uses
       * a default value of "".
       * @param fmt the format string
       * @param vars should have a key which is a String, and a value which is also a String.
       * @return the formatted string
       */
      static String expandStr(String fmt, Map vars) {
         if (fmt == null || fmt.equals(""))
             return fmt;
         
         if (fmt.indexOf('%') == -1)
             return fmt;
         
         StringBuffer sb = new StringBuffer(fmt.length()+32);
         for (int i=0; i < fmt.length(); i++) {
             char ch = fmt.charAt(i);
             if (ch == '%') {
                 i++;
                 if (i > fmt.length())
                     return sb.toString();
                 ch = fmt.charAt(i);
                 if (ch != '%') {
                     String val = (String) vars.get(Character.toString(ch));
                     if (val != null)
                         sb.append(val);
                     else
                         sb.append(ch);
                 } else {
                     sb.append(ch);
                 }
             } else {
                 sb.append(ch);
             }
         }
         return sb.toString();
     }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#changePassword(java.lang.String, java.lang.String)
     */
    public void changePassword(Account acct, String currentPassword, String newPassword) throws ServiceException {
        authAccount(acct, currentPassword, false);
        boolean locked = acct.getBooleanAttr(Provisioning.A_zimbraPasswordLocked, false);
        if (locked)
            throw AccountServiceException.PASSWORD_LOCKED();
        setPassword(acct, newPassword, true);        
    }

    /**
     * @param newPassword
     * @throws AccountServiceException
     */
    private void checkHistory(String newPassword, String[] history) throws AccountServiceException {
        if (history == null)
            return;
        for (int i=0; i < history.length; i++) {
            int sepIndex = history[i].indexOf(':');
            if (sepIndex != -1)  {
                String encoded = history[i].substring(sepIndex+1);
                if (LdapUtil.verifySSHA(encoded, newPassword))
                    throw AccountServiceException.PASSWORD_RECENTLY_USED();
            }            
        }
    }



    /**
     * update password history
     * @param history current history
     * @param currentPassword the current encoded-password
     * @param maxHistory number of prev passwords to keep
     * @return new hsitory
     */
    private String[] updateHistory(String history[], String currentPassword, int maxHistory) {
        String[] newHistory = history;
        if (currentPassword == null)
            return null;

        String currentHistory = System.currentTimeMillis() + ":"+currentPassword;
        
        // just add if empty or room
        if (history == null || history.length < maxHistory) {
        
            if (history == null) {
                newHistory = new String[1];
            } else {
                newHistory = new String[history.length+1];
                System.arraycopy(history, 0, newHistory, 0, history.length);
            }
            newHistory[newHistory.length-1] = currentHistory;
            return newHistory;
        }
        
        // remove oldest, add current
        long min = Long.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < history.length; i++) {
            int sepIndex = history[i].indexOf(':');
            if (sepIndex == -1) {
                // nuke it if no separator
                minIndex = i;
                break;
            }
            long val = Long.parseLong(history[i].substring(0, sepIndex));
            if (val < min) {
                min = val;
                minIndex = i;
            }
        }
        if (minIndex == -1)
            minIndex = 0;
        history[minIndex] = currentHistory;
        return history;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#setPassword(java.lang.String)
     */
    public void setPassword(Account acct, String newPassword) throws ServiceException {
        setPassword(acct, newPassword, false);
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#checkPasswordStrength(java.lang.String)
     */
    public void checkPasswordStrength(Account acct, String password) throws ServiceException {
        checkPasswordStrength(password, acct, null, null);
    }

    private int getInt(Account acct, Cos cos, Attributes attrs, String name, int defaultValue) throws NamingException {
        if (acct != null)
            return acct.getIntAttr(name, defaultValue);
        
        String v = LdapUtil.getAttrString(attrs, name);
        if (v == null)
            return cos.getIntAttr(name, defaultValue);
        else {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    
    /**
     * called to check password strength. Should pass in either an Account, or Cos/Attributes (during creation).
     * 
     * @param password
     * @param acct
     * @param cos
     * @param attrs
     * @throws ServiceException
     */
    private void checkPasswordStrength(String password, Account acct, Cos cos, Attributes attrs) throws ServiceException {
        try {
            int minLength = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMinLength, 0);
            if (minLength > 0 && password.length() < minLength)
                throw AccountServiceException.INVALID_PASSWORD("too short");

            int maxLength = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMaxLength, 0);        
            if (maxLength > 0 && password.length() > maxLength)
                throw AccountServiceException.INVALID_PASSWORD("too long");
            
            int minUpperCase = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMinUpperCaseChars, 0);
            int minLowerCase = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMinLowerCaseChars, 0);
            int minPunctuation = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMinPunctuationChars, 0);
            int minNumeric = getInt(acct, cos, attrs, Provisioning.A_zimbraPasswordMinNumericChars, 0);
            
            if (minUpperCase > 0 || minLowerCase > 0 || minPunctuation > 0 || minNumeric > 0) {
                int upper=0, lower=0, punctuation = 0, numeric = 0;
                for (int i=0; i < password.length(); i++) {
                    int ch = password.charAt(i);
                    if (Character.isUpperCase(ch)) upper++;
                    else if (Character.isLowerCase(ch)) lower++;
                    else if (Character.isDigit(ch)) numeric++;
                    else if (isAsciiPunc(ch)) punctuation++;
                }
                
                if (upper < minUpperCase) throw AccountServiceException.INVALID_PASSWORD("not enough upper case characters");
                if (lower < minLowerCase) throw AccountServiceException.INVALID_PASSWORD("not enough lower case characters");
                if (numeric < minNumeric) throw AccountServiceException.INVALID_PASSWORD("not enough numeric characters");
                if (punctuation < minPunctuation) throw AccountServiceException.INVALID_PASSWORD("not enough punctuation characters");                
            }
            
        } catch (NamingException ne) {
            throw ServiceException.FAILURE(ne.getMessage(), ne);
        }
    }

    private boolean isAsciiPunc(int ch) {
        return 
            (ch >= 33 && ch <= 47) || // ! " # $ % & ' ( ) * + , - . /
            (ch >= 58 && ch <= 64) || // : ; < = > ? @ 
            (ch >= 91 && ch <= 96) || // [ \ ] ^ _ ` 
            (ch >=123 && ch <= 126);  // { | } ~ 
    }

    // called by create account
    private void setInitialPassword(Cos cos, Attributes attrs, String newPassword) throws ServiceException, NamingException {
        String userPassword = LdapUtil.getAttrString(attrs, Provisioning.A_userPassword);
        if (userPassword == null && (newPassword == null || "".equals(newPassword))) return;

        if (userPassword == null) {
            checkPasswordStrength(newPassword, null, cos, attrs);
            userPassword = LdapUtil.generateSSHA(newPassword, null);
        }
        attrs.put(Provisioning.A_userPassword, userPassword);
        attrs.put(Provisioning.A_zimbraPasswordModifiedTime, DateUtil.toGeneralizedTime(new Date()));
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Account#setPassword(java.lang.String)
     */
    void setPassword(Account acct, String newPassword, boolean enforcePolicy) throws ServiceException {

        boolean mustChange = acct.getBooleanAttr(Provisioning.A_zimbraPasswordMustChange, false);

        if (enforcePolicy) {
            checkPasswordStrength(newPassword, acct, null, null);
            
            // skip min age checking if mustChange is set
            if (!mustChange) {
                int minAge = acct.getIntAttr(Provisioning.A_zimbraPasswordMinAge, 0);
                if (minAge > 0) {
                    Date lastChange = acct.getGeneralizedTimeAttr(Provisioning.A_zimbraPasswordModifiedTime, null);
                    if (lastChange != null) {
                        long last = lastChange.getTime();
                        long curr = System.currentTimeMillis();
                        if ((last+(ONE_DAY_IN_MILLIS * minAge)) > curr)
                            throw AccountServiceException.PASSWORD_CHANGE_TOO_SOON();
                    }
                }
            }
        }            

        Map<String, Object> attrs = new HashMap<String, Object>();

        int enforceHistory = acct.getIntAttr(Provisioning.A_zimbraPasswordEnforceHistory, 0);
        if (enforceHistory > 0) {
            String[] newHistory = updateHistory(
                    acct.getMultiAttr(Provisioning.A_zimbraPasswordHistory),
                    acct.getAttr(Provisioning.A_userPassword),                    
                    enforceHistory);
            attrs.put(Provisioning.A_zimbraPasswordHistory, newHistory);
            checkHistory(newPassword, newHistory);
        }

        String encodedPassword = LdapUtil.generateSSHA(newPassword, null);

        // unset it so it doesn't take up space...
        if (mustChange)
            attrs.put(Provisioning.A_zimbraPasswordMustChange, "");

        attrs.put(Provisioning.A_userPassword, encodedPassword);
        attrs.put(Provisioning.A_zimbraPasswordModifiedTime, DateUtil.toGeneralizedTime(new Date()));
        
        modifyAttrs(acct, attrs);
    }
    
    public Zimlet getZimlet(String name) throws ServiceException {
    	return getZimlet(name, null, true);
    }

    Zimlet lookupZimlet(String name, DirContext ctxt) throws ServiceException {
    	return getZimlet(name, ctxt, false);
    }
    
    private Zimlet getZimlet(String name, DirContext initCtxt, boolean useCache) throws ServiceException {
    	LdapZimlet zimlet = sZimletCache.getByName(name);
    	if (!useCache || zimlet == null) {
        	DirContext ctxt = initCtxt;
        	try {
        		if (ctxt == null) {
        		    ctxt = LdapUtil.getDirContext();
        		}
        		String dn = mDIT.zimletNameToDN(name);            
        		Attributes attrs = LdapUtil.getAttributes(ctxt, dn);
        		zimlet = new LdapZimlet(dn, attrs);
        		if (useCache) {
        			ZimletUtil.reloadZimlet(name);
        			sZimletCache.put(zimlet);  // put LdapZimlet into the cache after successful ZimletUtil.reloadZimlet()
        		}
        	} catch (NameNotFoundException nnfe) {
        		return null;
        	} catch (NamingException ne) {
        		throw ServiceException.FAILURE("unable to get zimlet: "+name, ne);
        	} catch (ZimletException ze) {
        		throw ServiceException.FAILURE("unable to load zimlet: "+name, ze);
            } finally {
            	if (initCtxt == null) {
            		LdapUtil.closeContext(ctxt);
            	}
        	}
    	}
    	return zimlet;
    }
    
    public List<Zimlet> listAllZimlets() throws ServiceException {
    	List<Zimlet> result = new ArrayList<Zimlet>();
    	DirContext ctxt = null;
    	try {
    		ctxt = LdapUtil.getDirContext();
    		NamingEnumeration ne = LdapUtil.searchDir(ctxt, "", "(objectclass=zimbraZimletEntry)", sSubtreeSC);
    		while (ne.hasMore()) {
    			SearchResult sr = (SearchResult) ne.next();
             result.add(new LdapZimlet(sr.getNameInNamespace(), sr.getAttributes()));
    		}
    		ne.close();
    	} catch (NamingException e) {
    		throw ServiceException.FAILURE("unable to list all zimlets", e);
    	} finally {
    		LdapUtil.closeContext(ctxt);
    	}
    	Collections.sort(result);
    	return result;
    }
    
    public Zimlet createZimlet(String name, Map<String, Object> zimletAttrs) throws ServiceException {
    	name = name.toLowerCase().trim();
    	
    	HashMap attrManagerContext = new HashMap();
    	AttributeManager.getInstance().preModify(zimletAttrs, null, attrManagerContext, true, true);
    	
    	DirContext ctxt = null;
    	try {
    		ctxt = LdapUtil.getDirContext();
    		
    		Attributes attrs = new BasicAttributes(true);
    		String hasKeyword = LdapUtil.LDAP_FALSE;
    		if (zimletAttrs.containsKey(A_zimbraZimletKeyword)) {
    			hasKeyword = Provisioning.TRUE;
    		}
    		LdapUtil.mapToAttrs(zimletAttrs, attrs);
    		LdapUtil.addAttr(attrs, A_objectClass, "zimbraZimletEntry");
    		LdapUtil.addAttr(attrs, A_zimbraZimletEnabled, Provisioning.FALSE);
    		LdapUtil.addAttr(attrs, A_zimbraZimletIndexingEnabled, hasKeyword);
    		
    		String dn = mDIT.zimletNameToDN(name);
    		LdapUtil.createEntry(ctxt, dn, attrs, "createZimlet");
    		
    		Zimlet zimlet = lookupZimlet(name, ctxt);
    		AttributeManager.getInstance().postModify(zimletAttrs, zimlet, attrManagerContext, true);
    		return zimlet;
    	} catch (NameAlreadyBoundException nabe) {
    		throw ServiceException.FAILURE("zimlet already exists: "+name, nabe);
    	} catch (ServiceException se) {
    		throw se;
    	} finally {
    		LdapUtil.closeContext(ctxt);
    	}
    }
    
    public void deleteZimlet(String name) throws ServiceException {
    	DirContext ctxt = null;
    	try {
    		ctxt = LdapUtil.getDirContext();
    		LdapZimlet zimlet = (LdapZimlet)getZimlet(name, ctxt, true);
    		if (zimlet != null) {
    			sZimletCache.remove(zimlet);
    			LdapUtil.unbindEntry(ctxt, zimlet.getDN());
    		}
    	} catch (NamingException e) {
    		throw ServiceException.FAILURE("unable to delete zimlet: "+name, e);
    	} finally {
    		LdapUtil.closeContext(ctxt);
    	}
    }

    public CalendarResource createCalendarResource(String emailAddress,String password, 
                                                   Map<String, Object> calResAttrs)
    throws ServiceException {
        emailAddress = emailAddress.toLowerCase().trim();

        calResAttrs.put(Provisioning.A_zimbraAccountCalendarUserType,
                        Account.CalendarUserType.RESOURCE.toString());

        SpecialAttrs specialAttrs = mDIT.handleSpecialAttrs(calResAttrs);
        
        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().
            preModify(calResAttrs, null, attrManagerContext, true, true);
        createAccount(emailAddress, password, calResAttrs, specialAttrs,
                      new String[] { C_zimbraCalendarResource });
        LdapCalendarResource resource =
            (LdapCalendarResource) getCalendarResourceByName(emailAddress);
        AttributeManager.getInstance().
            postModify(calResAttrs, resource, attrManagerContext, true);
        return resource;
    }

    public void deleteCalendarResource(String zimbraId)
    throws ServiceException {
        deleteAccount(zimbraId);
    }

    public void renameCalendarResource(String zimbraId, String newName)
    throws ServiceException {
        renameAccount(zimbraId, newName);
    }

    @Override
    public CalendarResource get(CalendarResourceBy keyType, String key) throws ServiceException {
        switch(keyType) {
            case id: 
                return getCalendarResourceById(key);
            case foreignPrincipal: 
                return getCalendarResourceByForeignPrincipal(key);
            case name: 
                return getCalendarResourceByName(key);
            default:
                    return null;
        }
    }

    private CalendarResource getCalendarResourceById(String zimbraId)
    throws ServiceException {
        if (zimbraId == null)
            return null;
        LdapCalendarResource resource =
            (LdapCalendarResource) sAccountCache.getById(zimbraId);
        if (resource == null) {
            zimbraId = LdapUtil.escapeSearchFilterArg(zimbraId);
            resource = (LdapCalendarResource) getAccountByQuery(
                "",
                "(&(zimbraId=" + zimbraId + ")" +
                FILTER_CALENDAR_RESOURCE_OBJECTCLASS + ")",
                null);
            sAccountCache.put(resource);
        }
        return resource;
    }

    private CalendarResource getCalendarResourceByName(String emailAddress)
    throws ServiceException {
        int index = emailAddress.indexOf('@');
        String domain = null;
        if (index == -1) {
             domain = getConfig().getAttr(
                     Provisioning.A_zimbraDefaultDomainName, null);
            if (domain == null)
                throw ServiceException.INVALID_REQUEST(
                        "must be valid email address: "+ emailAddress, null);
            else
                emailAddress = emailAddress + "@" + domain;
         }

        LdapCalendarResource resource =
            (LdapCalendarResource) sAccountCache.getByName(emailAddress);
        if (resource == null) {
            emailAddress = LdapUtil.escapeSearchFilterArg(emailAddress);
            resource = (LdapCalendarResource) getAccountByQuery(
                "",
                "(&(|(zimbraMailDeliveryAddress=" + emailAddress +
                ")(zimbraMailAlias=" + emailAddress + "))" +
                FILTER_CALENDAR_RESOURCE_OBJECTCLASS + ")",
                null);
            sAccountCache.put(resource);
        }
        return resource;
    }

    private CalendarResource getCalendarResourceByForeignPrincipal(String foreignPrincipal)
    throws ServiceException {
//        LdapCalendarResource res = null;
        foreignPrincipal = LdapUtil.escapeSearchFilterArg(foreignPrincipal);
        LdapCalendarResource resource =
            (LdapCalendarResource) getAccountByQuery(
                "",
                "(&(zimbraForeignPrincipal=" + foreignPrincipal + ")" +
                FILTER_CALENDAR_RESOURCE_OBJECTCLASS + ")",
                null);
        sAccountCache.put(resource);
        return resource;
    }

    public List<NamedEntry> searchCalendarResources(
        EntrySearchFilter filter,
        String returnAttrs[],
        String sortAttr,
        boolean sortAscending)
    throws ServiceException {
        return searchCalendarResources(filter, returnAttrs,
                                       sortAttr, sortAscending,
                                       "");
    }

    List<NamedEntry> searchCalendarResources(
        EntrySearchFilter filter,
        String returnAttrs[],
        String sortAttr,
        boolean sortAscending,
        String base)
    throws ServiceException {
        String query = LdapEntrySearchFilter.toLdapCalendarResourcesFilter(filter);
        return searchObjects(query, returnAttrs,
                              sortAttr, sortAscending,
                              base,
                              Provisioning.SA_CALENDAR_RESOURCE_FLAG, 0);
    }

    private Account makeAccount(String dn, Attributes attrs, LdapProvisioning prov) throws NamingException, ServiceException {
        Attribute a = attrs.get(Provisioning.A_zimbraAccountCalendarUserType);
        boolean isAccount = (a == null) || a.contains(CalendarUserType.USER.toString());
        
        String emailAddress = LdapUtil.getAttrString(attrs, Provisioning.A_zimbraMailDeliveryAddress);
        if (emailAddress == null)
            emailAddress = mDIT.dnToEmail(dn, attrs);
        
        Account acct = (isAccount) ? new LdapAccount(dn, emailAddress, attrs, null) : new LdapCalendarResource(dn, emailAddress, attrs, null);
        Cos cos = getCOS(acct);
        acct.setDefaults(cos.getAccountDefaults());
        return acct;
    }
    
    private Alias makeAlias(String dn, Attributes attrs, LdapProvisioning prov) throws NamingException, ServiceException {
        String emailAddress = mDIT.dnToEmail(dn, attrs);
        Alias alias = new LdapAlias(dn, emailAddress, attrs);
        return alias;
    }
    
    private DistributionList makeDistributionList(String dn, Attributes attrs, LdapProvisioning prov) throws NamingException, ServiceException {
        String emailAddress = mDIT.dnToEmail(dn, attrs);
        DistributionList dl = new LdapDistributionList(dn, emailAddress, attrs);
        return dl;
    }


    /**
     *  called when an account/dl is renamed
     *  
     */
    protected void renameAddressesInAllDistributionLists(String oldName, String newName, ReplaceAddressResult replacedAliasPairs) {
    	Map<String, String> changedPairs = new HashMap<String, String>();
    	
    	changedPairs.put(oldName, newName);
    	for (int i=0 ; i < replacedAliasPairs.oldAddrs().length; i++) {
    		String oldAddr = replacedAliasPairs.oldAddrs()[i];
    		String newAddr = replacedAliasPairs.newAddrs()[i];
    	    if (!oldAddr.equals(newAddr))
    	    	changedPairs.put(oldAddr, newAddr);
    	}
    	
    	renameAddressesInAllDistributionLists(changedPairs);
    }
    	
    protected void renameAddressesInAllDistributionLists(Map<String, String> changedPairs) {

    	String oldAddrs[] = changedPairs.keySet().toArray(new String[0]);
        String newAddrs[] = changedPairs.values().toArray(new String[0]);
        
        List<DistributionList> lists = null; 
        Map<String, String[]> attrs = null;
        
        try {
            lists = getAllDistributionListsForAddresses(oldAddrs, false);
        } catch (ServiceException se) {
            ZimbraLog.account.warn("unable to rename addr "+oldAddrs.toString()+" in all DLs ", se);
            return;
        }
        
        for (DistributionList list: lists) {
            // should we just call removeMember/addMember? This might be quicker, because calling
            // removeMember/addMember might have to update an entry's zimbraMemberId twice
            if (attrs == null) {
                attrs = new HashMap<String, String[]>();
                attrs.put("-" + Provisioning.A_zimbraMailForwardingAddress, oldAddrs);
                attrs.put("+" + Provisioning.A_zimbraMailForwardingAddress, newAddrs);    
            }
            try {
                modifyAttrs(list, attrs);
                //list.removeMember(oldName)
                //list.addMember(newName);                
            } catch (ServiceException se) {
                // log warning an continue
                ZimbraLog.account.warn("unable to rename "+oldAddrs.toString()+" to "+newAddrs.toString()+" in DL "+list.getName(), se);
            }
        }
    }

    /**
     *  called when an account is being deleted. swallows all exceptions (logs warnings).
     */
    void removeAddressFromAllDistributionLists(String address) {
        String addrs[] = new String[] { address } ;
        removeAddressesFromAllDistributionLists(addrs);
    }
    
    /**
     *  called when an account is being deleted or status being set to closed. swallows all exceptions (logs warnings).
     */
    public void removeAddressesFromAllDistributionLists(String[] addrs) {
        List<DistributionList> lists = null; 
        try {
            lists = getAllDistributionListsForAddresses(addrs, false);
        } catch (ServiceException se) {
            ZimbraLog.account.warn("unable to remove "+addrs.toString()+" from all DLs ", se);
            return;
        }

        for (DistributionList list: lists) { 
            try {
                removeMembers(list, addrs);                
            } catch (ServiceException se) {
                // log warning and continue
                ZimbraLog.account.warn("unable to remove "+addrs.toString()+" from DL "+list.getName(), se);
            }
        }
    }

    static String[] getAllAddrsForDistributionList(DistributionList list) throws ServiceException {
        String aliases[] = list.getAliases();
        String addrs[] = new String[aliases.length+1];
        addrs[0] = list.getName();
        for (int i=0; i < aliases.length; i++)
            addrs[i+1] = aliases[i];
        return addrs;
    }
    
    private List<DistributionList> getAllDistributionListsForAddresses(String addrs[], boolean minimalData) throws ServiceException {
        if (addrs == null || addrs.length == 0)
            return new ArrayList<DistributionList>();
        StringBuilder sb = new StringBuilder();
        if (addrs.length > 1)
            sb.append("(|");
        for (int i=0; i < addrs.length; i++) {
            sb.append(String.format("(%s=%s)", Provisioning.A_zimbraMailForwardingAddress, addrs[i]));    
        }
        if (addrs.length > 1)
            sb.append(")");
        String [] attrs = minimalData ? sMinimalDlAttrs : null;
        
        return (List<DistributionList>) searchAccountsInternal(sb.toString(), attrs, null, true, Provisioning.SA_DISTRIBUTION_LIST_FLAG);
        
    }

    static List<DistributionList> getDistributionLists(String addrs[], boolean directOnly, Map<String, String> via, boolean minimalData)
        throws ServiceException 
    {
        LdapProvisioning prov = (LdapProvisioning) Provisioning.getInstance(); // GROSS
        List<DistributionList> directDLs = prov.getAllDistributionListsForAddresses(addrs, true); 
        HashSet<String> directDLSet = new HashSet<String>();
        HashSet<String> checked = new HashSet<String>();
        List<DistributionList> result = new ArrayList<DistributionList>();        

        Stack<DistributionList> dlsToCheck = new Stack<DistributionList>();
        
        for (DistributionList dl : directDLs) {
            dlsToCheck.push(dl);
            directDLSet.add(dl.getName());
        }

        while (!dlsToCheck.isEmpty()) {
            DistributionList dl = dlsToCheck.pop();
            if (checked.contains(dl.getId())) continue;
            result.add(dl);
            checked.add(dl.getId());
            if (directOnly) continue;
     
            String[] dlAddrs = getAllAddrsForDistributionList(dl);
            List<DistributionList> newLists = prov.getAllDistributionListsForAddresses(dlAddrs, true);

            for (DistributionList newDl: newLists) {
                if (!directDLSet.contains(newDl.getName())) {
                    if (via != null) via.put(newDl.getName(), dl.getName());
                    dlsToCheck.push(newDl);
                }
            }
        }
        Collections.sort(result);
        return result;
    }
    
    public static void main(String args[]) {
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", null));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", ""));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "WTF"));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "%n"));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "%u"));        
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "%d"));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "%D"));                
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "uid=%u,ou=people,%D"));
        System.out.println(LdapUtil.computeAuthDn("schemers@example.zimbra.com", "n(%n)u(%u)d(%d)D(%D)(%%)"));
    }

    private static final String DATA_DL_SET = "DL_SET";

    @Override
    public Set<String> getDistributionLists(Account acct) throws ServiceException {
        Set<String> dls = (Set<String>) acct.getCachedData(DATA_DL_SET);
        if (dls != null) return dls;
     
        dls = new HashSet<String>();
        
        List<DistributionList> lists = getDistributionLists(acct, false, null, true);
        
        for (DistributionList dl : lists) {
            dls.add(dl.getId());
        }
        dls = Collections.unmodifiableSet(dls);
        acct.setCachedData(DATA_DL_SET, dls);
        return dls;
    }

    @Override
    public boolean inDistributionList(Account acct, String zimbraId) throws ServiceException {
        return getDistributionLists(acct).contains(zimbraId);        
    }
    
    public List<DistributionList> getDistributionLists(Account acct, boolean directOnly, Map<String, String> via) throws ServiceException {
        return getDistributionLists(acct, directOnly, via, false);
    }
    
    private List<DistributionList> getDistributionLists(Account acct, boolean directOnly, Map<String, String> via, boolean minimal) throws ServiceException {
        String aliases[] = acct.getAliases();
        String addrs[] = new String[aliases.length+1];
        addrs[0] = acct.getName();
        for (int i=0; i < aliases.length; i++)
            addrs[i+1] = aliases[i];
        return LdapProvisioning.getDistributionLists(addrs, directOnly, via, minimal);
    }
    
    private static final int DEFAULT_GAL_MAX_RESULTS = 100;

    private static final String DATA_GAL_ATTR_MAP = "GAL_ATTRS_MAP";
    private static final String DATA_GAL_ATTR_LIST = "GAL_ATTR_LIST";
    private static final String DATA_GAL_RULES = "GAL_RULES";    

    private static final String GAL_FILTER_ZIMBRA_ACCOUNTS = "zimbraAccounts";
    private static final String GAL_FILTER_ZIMBRA_CALENDAR_RESOURCES = "zimbraResources";
    
    private static final String GAL_FILTER_ZIMBRA_ACCOUNT_AUTO_COMPLETE = "zimbraAccountAutoComplete";
    private static final String GAL_FILTER_ZIMBRA_CALENDAR_RESOURCE_AUTO_COMPLETE = "zimbraResourceAutoComplete";    

    
    @Override
    public List getAllAccounts(Domain d) throws ServiceException {
        return searchAccounts(d, mDIT.filterAccountsByDomain(d, false), null, null, true, Provisioning.SA_ACCOUNT_FLAG);
    }
    
    @Override
    public void getAllAccounts(Domain d, NamedEntry.Visitor visitor) throws ServiceException {
        LdapDomain ld = (LdapDomain) d;
        searchObjects(mDIT.filterAccountsByDomain(d, false), null, mDIT.domainDNToAccountSearchDN(ld.getDN()), Provisioning.SA_ACCOUNT_FLAG, visitor, 0);
    }

    @Override
    public List getAllCalendarResources(Domain d) throws ServiceException {
        return searchAccounts(d, mDIT.filterCalendarResourcesByDomain(d, false), 
                              null, null, true, Provisioning.SA_CALENDAR_RESOURCE_FLAG);
        /*
        return searchCalendarResources(d, 
                LdapEntrySearchFilter.sCalendarResourcesFilter,
                null, null, true);
        */        
    }
    
    @Override
    public void getAllCalendarResources(Domain d, NamedEntry.Visitor visitor)
    throws ServiceException {
        LdapDomain ld = (LdapDomain) d;        
        searchObjects(mDIT.filterCalendarResourcesByDomain(d, false),
                      null, mDIT.domainDNToAccountSearchDN(ld.getDN()),
                      Provisioning.SA_CALENDAR_RESOURCE_FLAG,
                      visitor, 0);
    }

    @Override
    public List getAllDistributionLists(Domain d) throws ServiceException {
        return searchAccounts(d, mDIT.filterDistributionListsByDomain(d, false), 
                              null, null, true, Provisioning.SA_DISTRIBUTION_LIST_FLAG);
    }

    @Override
    public List searchAccounts(Domain d, String query, String returnAttrs[], String sortAttr, boolean sortAscending, int flags) throws ServiceException
    {
        LdapDomain ld = (LdapDomain) d;
        return searchObjects(query, returnAttrs, sortAttr, sortAscending, 
                             mDIT.domainDNToAccountSearchDN(ld.getDN()), flags, 0);
    }

    public List<NamedEntry> searchDirectory(SearchOptions options) throws ServiceException {
        String base = "";
        
        LdapDomain ld = (LdapDomain) options.getDomain();
        if (ld != null) 
            base = mDIT.domainDNToAccountSearchDN(ld.getDN());
        else {
            String bs = options.getBase();
            if (bs != null)
                base = bs;
        }
        
        return searchObjects(options.getQuery(), options.getReturnAttrs(), options.getSortAttr(), options.isSortAscending(), base, options.getFlags(), options.getMaxResults());
    }

    @Override
    public List searchCalendarResources(
        Domain d,
        EntrySearchFilter filter,
        String returnAttrs[],
        String sortAttr,
        boolean sortAscending)
    throws ServiceException {
        LdapDomain ld = (LdapDomain) d;
        return searchCalendarResources(filter, returnAttrs,
                                       sortAttr, sortAscending,
                                       mDIT.domainDNToAccountSearchDN(ld.getDN()));
    }

    @Override
    public SearchGalResult searchGal(Domain d, String n,
                                     Provisioning.GAL_SEARCH_TYPE type,
                                     String token)
    throws ServiceException {
        // escape user-supplied string
        n = LdapUtil.escapeSearchFilterArg(n);

        int maxResults = token != null ? 0 : d.getIntAttr(Provisioning.A_zimbraGalMaxResults, DEFAULT_GAL_MAX_RESULTS);
        if (type == Provisioning.GAL_SEARCH_TYPE.CALENDAR_RESOURCE)
            return searchResourcesGal(d, n, maxResults, token, false);

        String mode = d.getAttr(Provisioning.A_zimbraGalMode);
        SearchGalResult results = null;
        if (mode == null || mode.equals(Provisioning.GM_ZIMBRA)) {
            results = searchZimbraGal(d, n, maxResults, token, false);
        } else if (mode.equals(Provisioning.GM_LDAP)) {
            results = searchLdapGal(d, n, maxResults, token, false);
        } else if (mode.equals(Provisioning.GM_BOTH)) {
            results = searchZimbraGal(d, n, maxResults/2, token, false);
            SearchGalResult ldapResults = searchLdapGal(d, n, maxResults/2, token, false);
            if (ldapResults != null) {
                results.matches.addAll(ldapResults.matches);
                results.token = LdapUtil.getLaterTimestamp(results.token, ldapResults.token);
            }
        } else {
            results = searchZimbraGal(d, n, maxResults, token, false);
        }
        if (results == null) results = new SearchGalResult();
        if (results.matches == null) results.matches = new ArrayList<GalContact>();

        if (type == Provisioning.GAL_SEARCH_TYPE.ALL) {
            SearchGalResult resourceResults = null;
            if (maxResults == 0)
                resourceResults = searchResourcesGal(d, n, 0, token, false);
            else {
                int room = maxResults - results.matches.size();
                if (room > 0)
                    resourceResults = searchResourcesGal(d, n, room, token, false);
            }
            if (resourceResults != null) {
                results.matches.addAll(resourceResults.matches);
                results.token = LdapUtil.getLaterTimestamp(results.token, resourceResults.token);
            }
        }
        
        /*
         *  LDAP doesn't have a > query, just a >= query.  
         *  This causes SyncGal returns extra entries that were updated/created on the same second 
         *  as the prev sync token.  To work around it, we add one second to the result token if the 
         *  token has changed in this sync.        
         */
        boolean gotNewToken = true;
        if ((token != null && token.equals(results.token)) || results.token.equals(LdapUtil.EARLIEST_SYNC_TOKEN))
            gotNewToken = false;
        
        if (gotNewToken) {
            Date parsedToken = DateUtil.parseGeneralizedTime(results.token, false);
            if (parsedToken != null) {
                long ts = parsedToken.getTime();
                ts += 1000;
                results.token = DateUtil.toGeneralizedTime(new Date(ts));
            }
            /*
             * in the rare case when an LDAP implementation does not conform to generalized time and 
             * we cannot parser the token, just leave it alone.
             */
            
        }
        
        return results;
    }
    
    @Override
    public SearchGalResult autoCompleteGal(Domain d, String n, Provisioning.GAL_SEARCH_TYPE type, int max) throws ServiceException 
    {
        // escape user-supplied string
        n = LdapUtil.escapeSearchFilterArg(n);

        int maxResults = Math.min(max, d.getIntAttr(Provisioning.A_zimbraGalMaxResults, DEFAULT_GAL_MAX_RESULTS));
        if (type == Provisioning.GAL_SEARCH_TYPE.CALENDAR_RESOURCE)
            return searchResourcesGal(d, n, maxResults, null, true);

        String mode = d.getAttr(Provisioning.A_zimbraGalMode);
        SearchGalResult results = null;
        if (mode == null || mode.equals(Provisioning.GM_ZIMBRA)) {
            results = searchZimbraGal(d, n, maxResults, null, true);
        } else if (mode.equals(Provisioning.GM_LDAP)) {
            results = searchLdapGal(d, n, maxResults, null, true);
        } else if (mode.equals(Provisioning.GM_BOTH)) {
            results = searchZimbraGal(d, n, maxResults/2, null, true);
            SearchGalResult ldapResults = searchLdapGal(d, n, maxResults/2, null, true);
            if (ldapResults != null) {
                results.matches.addAll(ldapResults.matches);
                results.token = LdapUtil.getLaterTimestamp(results.token, ldapResults.token);
                results.hadMore = results.hadMore || ldapResults.hadMore;
            }
        } else {
            results = searchZimbraGal(d, n, maxResults, null, true);
        }
        if (results == null) results = new SearchGalResult();
        if (results.matches == null) results.matches = new ArrayList<GalContact>();

        if (type == Provisioning.GAL_SEARCH_TYPE.ALL) {
            SearchGalResult resourceResults = null;
            if (maxResults == 0)
                resourceResults = searchResourcesGal(d, n, 0, null, true);
            else {
                int room = maxResults - results.matches.size();
                if (room > 0)
                    resourceResults = searchResourcesGal(d, n, room, null, true);
            }
            if (resourceResults != null) {
                results.matches.addAll(resourceResults.matches);
                results.token = LdapUtil.getLaterTimestamp(results.token, resourceResults.token);
                results.hadMore = results.hadMore || resourceResults.hadMore;                
            }
        }
        Collections.sort(results.matches);
        return results;
    }

    public static String getFilterDef(String name) throws ServiceException {
        String queryExprs[] = Provisioning.getInstance().getConfig().getMultiAttr(Provisioning.A_zimbraGalLdapFilterDef);
        String fname = name+":";
        String queryExpr = null;
        for (int i=0; i < queryExprs.length; i++) {
            if (queryExprs[i].startsWith(fname)) {
                queryExpr = queryExprs[i].substring(fname.length());
            }
        }
        return queryExpr;
    }

    private synchronized LdapGalMapRules getGalRules(Domain d) {
        LdapGalMapRules rules = (LdapGalMapRules) d.getCachedData(DATA_GAL_RULES);
        if (rules == null) {
            String[] attrs = d.getMultiAttr(Provisioning.A_zimbraGalLdapAttrMap);            
            rules = new LdapGalMapRules(attrs);
            d.setCachedData(DATA_GAL_RULES, rules);
        }
        return rules;
    }

    private SearchGalResult searchResourcesGal(Domain d, String n, int maxResults, String token, boolean autoComplete)
    throws ServiceException {
        return searchZimbraWithNamedFilter(d, 
                autoComplete ? GAL_FILTER_ZIMBRA_CALENDAR_RESOURCE_AUTO_COMPLETE : GAL_FILTER_ZIMBRA_CALENDAR_RESOURCES, n, maxResults, token);
    }

    private SearchGalResult searchZimbraGal(Domain d, String n, int maxResults, String token, boolean autoComplete)
    throws ServiceException {
        return searchZimbraWithNamedFilter(d, 
                autoComplete ? GAL_FILTER_ZIMBRA_ACCOUNT_AUTO_COMPLETE : GAL_FILTER_ZIMBRA_ACCOUNTS, n, maxResults, token);
    }

    private SearchGalResult searchZimbraWithNamedFilter(
        Domain d,
        String filterName,
        String n,
        int maxResults,
        String token)
    throws ServiceException {
        String queryExpr = getFilterDef(filterName);
        String query = null;
        if (queryExpr != null) {
            if (token != null) n = "";
    
            Map<String, String> vars = new HashMap<String, String>();
            vars.put("s", n);
            query = LdapProvisioning.expandStr(queryExpr, vars);
            if (token != null) {
                if (token.equals(""))
                    query = query.replaceAll("\\*\\*", "*");
                else {
                    String arg = LdapUtil.escapeSearchFilterArg(token);
                    //query = "(&(modifyTimeStamp>="+arg+")"+query.replaceAll("\\*\\*", "*")+")";
                    query = "(&(|(modifyTimeStamp>="+arg+")(createTimeStamp>="+arg+"))"+query.replaceAll("\\*\\*", "*")+")";
                }
            }
        }
        return searchZimbraWithQuery(d, query, maxResults, token);
    }

    private SearchGalResult searchZimbraWithQuery(Domain d, String query, int maxResults, String token)
        throws ServiceException 
    {
        LdapDomain ld = (LdapDomain) d;
        SearchGalResult result = new SearchGalResult();
        result.matches = new ArrayList<GalContact>();
        if (query == null)
            return result;

        // filter out hidden entries
        query = "(&("+query+")(!(zimbraHideInGal=TRUE)))";

        LdapGalMapRules rules = getGalRules(d);

        SearchControls sc = new SearchControls(SearchControls.SUBTREE_SCOPE, maxResults, 0, rules.getLdapAttrs(), false, false);

        result.token = token != null && !token.equals("")? token : LdapUtil.EARLIEST_SYNC_TOKEN;        
        DirContext ctxt = null;
        NamingEnumeration ne = null;
        try {
            ctxt = LdapUtil.getDirContext(false);
            String searchBase = d.getAttr(Provisioning.A_zimbraGalInternalSearchBase, "DOMAIN");
            if (searchBase.equalsIgnoreCase("DOMAIN"))
                searchBase = mDIT.domainDNToAccountSearchDN(ld.getDN());
            else if (searchBase.equalsIgnoreCase("SUBDOMAINS"))
                searchBase = ld.getDN();
            else if (searchBase.equalsIgnoreCase("ROOT"))
                searchBase = "";

            ne = LdapUtil.searchDir(ctxt, searchBase, query, sc);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                String dn = sr.getNameInNamespace();
                GalContact lgc = new GalContact(dn, rules.apply(sr.getAttributes())); 
                String mts = (String) lgc.getAttrs().get("modifyTimeStamp");
                result.token = LdapUtil.getLaterTimestamp(result.token, mts);
                String cts = (String) lgc.getAttrs().get("createTimeStamp");
                result.token = LdapUtil.getLaterTimestamp(result.token, cts);
                result.matches.add(lgc);
            }
            ne.close();
            ne = null;
        } catch (SizeLimitExceededException sle) {
            result.hadMore = true;
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to search GAL: "+query, e);
        } finally {
            LdapUtil.closeEnumContext(ne);
            LdapUtil.closeContext(ctxt);
        }
        //Collections.sort(result);
        return result;
    }

    private SearchGalResult searchLdapGal(Domain d,
                                          String n,
                                          int maxResults,
                                          String token, boolean autoComplete)
    throws ServiceException {
        String url[] = d.getMultiAttr(Provisioning.A_zimbraGalLdapURL);
        String bindDn = d.getAttr(Provisioning.A_zimbraGalLdapBindDn);
        String bindPassword = d.getAttr(Provisioning.A_zimbraGalLdapBindPassword);
        String searchBase = d.getAttr(Provisioning.A_zimbraGalLdapSearchBase, "");
        LdapGalMapRules rules = getGalRules(d);
        String filter = d.getAttr(autoComplete ? Provisioning.A_zimbraGalAutoCompleteLdapFilter : Provisioning.A_zimbraGalLdapFilter);
        String[] galAttrList = rules.getLdapAttrs();
        try {
            return LdapUtil.searchLdapGal(url, bindDn, bindPassword, searchBase, filter, n, maxResults, rules, token);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to search GAL", e);
        }
    }

    @Override
    public void addMembers(DistributionList list, String[] members) throws ServiceException {
        LdapDistributionList ldl = (LdapDistributionList) list;
        ldl.addMembers(members, this);
    }

    @Override
    public void removeMembers(DistributionList list, String[] members) throws ServiceException {
        LdapDistributionList ldl = (LdapDistributionList) list;
        ldl.removeMembers(members, this);        
    }

    private List<Identity> getIdentitiesByQuery(LdapEntry entry, String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        List<Identity> result = new ArrayList<Identity>();
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            String base = entry.getDN();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, base, query, sSubtreeSC);
            while(ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapIdentity((Account)entry, sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();            
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup identity via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return result;
    }

    private Identity getIdentityByName(LdapEntry entry, String name,  DirContext ctxt) throws ServiceException {
        name = LdapUtil.escapeSearchFilterArg(name);
        List<Identity> result = getIdentitiesByQuery(entry, "(&(zimbraPrefIdentityName="+name+")(objectclass=zimbraIdentity))", ctxt); 
        return result.isEmpty() ? null : result.get(0);
    }

    private String getIdentityDn(LdapEntry entry, String name) {
        return A_zimbraPrefIdentityName + "=" + LdapUtil.escapeRDNValue(name) + "," + entry.getDN();    
    }

    private void validateIdentityAttrs(Map<String, Object> attrs) throws ServiceException {
        Set<String> validAttrs = AttributeManager.getInstance().getLowerCaseAttrsInClass(AttributeClass.identity);
        for (String key : attrs.keySet()) {
            if (!validAttrs.contains(key.toLowerCase())) {
                throw ServiceException.INVALID_REQUEST("unable to modify attr: "+key, null);
            }
        }        
    }
    
    private static final String IDENTITY_LIST_CACHE_KEY = "LdapProvisioning.IDENTITY_CACHE";

    @Override
    public Identity createIdentity(Account account, String identityName, Map<String, Object> identityAttrs) throws ServiceException {
        removeAttrIgnoreCase("objectclass", identityAttrs);        
        validateIdentityAttrs(identityAttrs);

        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());
        
        if (identityName.equalsIgnoreCase(DEFAULT_IDENTITY_NAME))
                throw AccountServiceException.IDENTITY_EXISTS(identityName);
        
        List<Identity> existing = getAllIdentities(account);
        if (existing.size() >= account.getLongAttr(A_zimbraIdentityMaxNumEntries, 20))
            throw AccountServiceException.TOO_MANY_IDENTITIES();
        
        account.setCachedData(IDENTITY_LIST_CACHE_KEY, null);
        
        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(identityAttrs, null, attrManagerContext, true, true);

        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            String dn = getIdentityDn(ldapEntry, identityName);
            
            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(identityAttrs, attrs);
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "zimbraIdentity");

            String identityId = LdapUtil.getAttrString(attrs, A_zimbraPrefIdentityId);
            if (identityId == null) {
                identityId = LdapUtil.generateUUID();
                attrs.put(A_zimbraPrefIdentityId, identityId);
            }
            
            LdapUtil.createEntry(ctxt, dn, attrs, "createIdentity");

            Identity identity = getIdentityByName(ldapEntry, identityName, ctxt);
            AttributeManager.getInstance().postModify(identityAttrs, identity, attrManagerContext, true);

            return identity;
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.IDENTITY_EXISTS(identityName);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to create identity", e);            
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public void modifyIdentity(Account account, String identityName, Map<String, Object> identityAttrs) throws ServiceException {
        removeAttrIgnoreCase("objectclass", identityAttrs);

        validateIdentityAttrs(identityAttrs);

        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));

        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        // clear cache 
        account.setCachedData(IDENTITY_LIST_CACHE_KEY, null);
        
        if (identityName.equalsIgnoreCase(DEFAULT_IDENTITY_NAME)) {
            modifyAttrs(account, identityAttrs);
        } else {
            
            LdapIdentity identity = (LdapIdentity) getIdentityByName(ldapEntry, identityName, null);
            if (identity == null)
                    throw AccountServiceException.NO_SUCH_IDENTITY(identityName);   
        
            String name = (String) identityAttrs.get(A_zimbraPrefIdentityName);
            boolean newName = (name != null && !name.equals(identityName));
            if (newName) identityAttrs.remove(A_zimbraPrefIdentityName);

            modifyAttrs(identity, identityAttrs, true);
            if (newName) {
                // the identity cache could've been loaded again if getAllIdentities were called in pre/poseModify callback, so we clear it again
                account.setCachedData(IDENTITY_LIST_CACHE_KEY, null);
                renameIdentity(ldapEntry, identity, name);
            }
            
        }
    }

    private void renameIdentity(LdapEntry entry, LdapIdentity identity, String newIdentityName) throws ServiceException {
        
        if (identity.getName().equalsIgnoreCase(DEFAULT_IDENTITY_NAME))
            throw ServiceException.INVALID_REQUEST("can't rename default identity", null);        
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            String newDn = getIdentityDn(entry, newIdentityName);            
            LdapUtil.renameEntry(ctxt, identity.getDN(), newDn);
        } catch (InvalidNameException e) {
            throw ServiceException.INVALID_REQUEST("invalid identity name: "+newIdentityName, e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to rename identity: "+newIdentityName, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public void deleteIdentity(Account account, String identityName) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        if (identityName.equalsIgnoreCase(DEFAULT_IDENTITY_NAME))
            throw ServiceException.INVALID_REQUEST("can't delete default identity", null);
        
        account.setCachedData(IDENTITY_LIST_CACHE_KEY, null);
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            Identity identity = getIdentityByName(ldapEntry, identityName, ctxt);
            if (identity == null)
                throw AccountServiceException.NO_SUCH_IDENTITY(identityName);
            String dn = getIdentityDn(ldapEntry, identityName);            
            LdapUtil.unbindEntry(ctxt, dn);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to delete identity: "+identityName, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    
    @Override
    public List<Identity> getAllIdentities(Account account) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        @SuppressWarnings("unchecked")        
        List<Identity> result = (List<Identity>) account.getCachedData(IDENTITY_LIST_CACHE_KEY);
        
        if (result != null) {
            return result;
        }
        
        result = getIdentitiesByQuery(ldapEntry, "(objectclass=zimbraIdentity)", null);
        for (Identity identity: result) {
            // gross hack for 4.5beta. should be able to remove post 4.5
            if (identity.getId() == null) {
                String id = LdapUtil.generateUUID();
                identity.setId(id);
                Map<String, Object> newAttrs = new HashMap<String, Object>();
                newAttrs.put(Provisioning.A_zimbraPrefIdentityId, id);
                try {
                    modifyIdentity(account, identity.getName(), newAttrs);
                } catch (ServiceException se) {
                    ZimbraLog.account.warn("error updating identity: "+account.getName()+" "+identity.getName()+" "+se.getMessage(), se);
                }
            }
        }
        result.add(getDefaultIdentity(account));
        result = Collections.unmodifiableList(result);
        account.setCachedData(IDENTITY_LIST_CACHE_KEY, result);
        return result;
    }

    @Override
    public Identity get(Account account, IdentityBy keyType, String key) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        // this assumes getAllIdentities is cached and number of identities is reasaonble. 
        // might want a per-identity cache (i.e., use "IDENTITY_BY_ID_"+id as a key, etc) 
        switch(keyType) {
            case id: 
                for (Identity identity : getAllIdentities(account)) 
                    if (identity.getId().equals(key)) return identity;
                return null;
            case name: 
                for (Identity identity : getAllIdentities(account)) 
                    if (identity.getName().equalsIgnoreCase(key)) return identity;
                return null;             
            default:
                return null;
        }
    }
    
    private List<Signature> getSignaturesByQuery(Account acct, LdapEntry entry, String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        List<Signature> result = new ArrayList<Signature>();
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            String base = entry.getDN();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, base, query, sSubtreeSC);
            while(ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapSignature(acct, sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();            
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup signature via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return result;
    }

    private Signature getSignatureById(Account acct, LdapEntry entry, String id,  DirContext ctxt) throws ServiceException {
        id = LdapUtil.escapeSearchFilterArg(id);
        List<Signature> result = getSignaturesByQuery(acct, entry, "(&(" + Provisioning.A_zimbraSignatureId + "=" + id +")(objectclass=zimbraSignature))", ctxt); 
        return result.isEmpty() ? null : result.get(0);
    }

    private String getSignatureDn(LdapEntry entry, String name) {
        return A_zimbraSignatureName + "=" + LdapUtil.escapeRDNValue(name) + "," + entry.getDN();    
    }

    private void validateSignatureAttrs(Map<String, Object> attrs) throws ServiceException {
        Set<String> validAttrs = AttributeManager.getInstance().getLowerCaseAttrsInClass(AttributeClass.signature);
        for (String key : attrs.keySet()) {
            if (!validAttrs.contains(key.toLowerCase())) {
                throw ServiceException.INVALID_REQUEST("unable to modify attr: "+key, null);
            }
        }        
    }
    
    private void setDefaultSignature(Account acct, String signatureId) throws ServiceException {
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefDefaultSignatureId, signatureId);
        modifyAttrs(acct, attrs);
    }
    
    
    private static final String SIGNATURE_LIST_CACHE_KEY = "LdapProvisioning.SIGNATURE_CACHE";

    @Override
    public Signature createSignature(Account account, String signatureName, Map<String, Object> signatureAttrs) throws ServiceException {
        removeAttrIgnoreCase("objectclass", signatureAttrs);        
        validateSignatureAttrs(signatureAttrs);

        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());
        
        /*
         * check if the signature name already exists
         * 
         * We check if the signatureName is the same as the signature on the account.  
         * For signatures that are in the signature LDAP entries, JNDI will throw 
         * NameAlreadyBoundException for duplicate names.
         * 
         */ 
        Signature acctSig = LdapSignature.getAccountSignature(this, account);
        if (acctSig != null && signatureName.equals(acctSig.getName()))
            throw AccountServiceException.SIGNATURE_EXISTS(signatureName);
        
        boolean setAsDefault = false;
        List<Signature> existing = getAllSignatures(account);
        int numSigs = existing.size();
        if (numSigs >= account.getLongAttr(A_zimbraSignatureMaxNumEntries, 20))
            throw AccountServiceException.TOO_MANY_SIGNATURES();
        else if (numSigs == 0)
            setAsDefault = true;
        
        account.setCachedData(SIGNATURE_LIST_CACHE_KEY, null);
        
        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(signatureAttrs, null, attrManagerContext, true, true);

        String signatureId = (String)signatureAttrs.get(Provisioning.A_zimbraSignatureId);
        if (signatureId == null) {
            signatureId = LdapUtil.generateUUID();
            signatureAttrs.put(Provisioning.A_zimbraSignatureId, signatureId);
        }
            
        if (acctSig == null) {
            // the slot on the account is not occupied, use it
            signatureAttrs.put(Provisioning.A_zimbraSignatureName, signatureName);
            // pass in setAsDefault as an optimization, since we are updating the account 
            // entry, we can update the default attr in one LDAP write
            LdapSignature.createAccountSignature(this, account, signatureAttrs, setAsDefault);
            return LdapSignature.getAccountSignature(this, account);
        }
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            String dn = getSignatureDn(ldapEntry, signatureName);
            
            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(signatureAttrs, attrs);
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "zimbraSignature");
            
            LdapUtil.createEntry(ctxt, dn, attrs, "createSignature");

            Signature signature = getSignatureById(account, ldapEntry, signatureId, ctxt);
            AttributeManager.getInstance().postModify(signatureAttrs, signature, attrManagerContext, true);

            if (setAsDefault)
                setDefaultSignature(account, signatureId);
                
            return signature;
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.SIGNATURE_EXISTS(signatureName);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to create signature", e);            
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public void modifySignature(Account account, String signatureId, Map<String, Object> signatureAttrs) throws ServiceException {
        removeAttrIgnoreCase("objectclass", signatureAttrs);

        validateSignatureAttrs(signatureAttrs);

        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));

        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        // clear cache 
        account.setCachedData(SIGNATURE_LIST_CACHE_KEY, null);
        
        String newName = (String) signatureAttrs.get(A_zimbraSignatureName);
        
        // do not allow name to be wiped
        if (newName!= null && newName.length()==0)
            throw ServiceException.INVALID_REQUEST("empty signature name is not allowed", null);
        
        if (LdapSignature.isAccountSignature(account, signatureId)) {
            LdapSignature.modifyAccountSignature(this, account, signatureAttrs);
        } else {
            
            LdapSignature signature = (LdapSignature) getSignatureById(account, ldapEntry, signatureId, null);
            if (signature == null)
                throw AccountServiceException.NO_SUCH_SIGNATURE(signatureId);   
        
            boolean nameChanged = (newName != null && !newName.equals(signature.getName()));
            
            if (nameChanged) 
                signatureAttrs.remove(A_zimbraSignatureName);

            modifyAttrs(signature, signatureAttrs, true);
            if (nameChanged) {
                // the signature cache could've been loaded again if getAllSignatures were called in pre/poseModify callback, so we clear it again
                account.setCachedData(SIGNATURE_LIST_CACHE_KEY, null);
                renameSignature(ldapEntry, signature, newName);
            }
            
        }
    }

    private void renameSignature(LdapEntry entry, LdapSignature signature, String newSignatureName) throws ServiceException {
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            String newDn = getSignatureDn(entry, newSignatureName);            
            LdapUtil.renameEntry(ctxt, signature.getDN(), newDn);
        } catch (InvalidNameException e) {
            throw ServiceException.INVALID_REQUEST("invalid signature name: "+newSignatureName, e);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to rename signature: "+newSignatureName, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public void deleteSignature(Account account, String signatureId) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        account.setCachedData(SIGNATURE_LIST_CACHE_KEY, null);
        
        if (LdapSignature.isAccountSignature(account, signatureId)) {
            LdapSignature.deleteAccountSignature(this, account);
            return;
        }
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            Signature signature = getSignatureById(account, ldapEntry, signatureId, ctxt);
            if (signature == null)
                throw AccountServiceException.NO_SUCH_SIGNATURE(signatureId);
            String dn = getSignatureDn(ldapEntry, signature.getName());            
            LdapUtil.unbindEntry(ctxt, dn);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to delete signarure: "+signatureId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }
    
    @Override
    public List<Signature> getAllSignatures(Account account) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        @SuppressWarnings("unchecked")        
        List<Signature> result = (List<Signature>) account.getCachedData(SIGNATURE_LIST_CACHE_KEY);
        
        if (result != null) {
            return result;
        }
        
        result = getSignaturesByQuery(account, ldapEntry, "(objectclass=zimbraSignature)", null);
        for (Signature signature: result) {
            // gross hack for 4.5beta. should be able to remove post 4.5
            if (signature.getId() == null) {
                String id = LdapUtil.generateUUID();
                signature.setId(id);
                Map<String, Object> newAttrs = new HashMap<String, Object>();
                newAttrs.put(Provisioning.A_zimbraSignatureId, id);
                try {
                    modifySignature(account, signature.getName(), newAttrs);
                } catch (ServiceException se) {
                    ZimbraLog.account.warn("error updating signature: "+account.getName()+" "+signature.getName()+" "+se.getMessage(), se);
                }
            }
        }
        
        Signature acctSig = LdapSignature.getAccountSignature(this, account);
        if (acctSig != null)
            result.add(acctSig);
        
        result = Collections.unmodifiableList(result);
        account.setCachedData(SIGNATURE_LIST_CACHE_KEY, result);
        return result;
    }

    @Override
    public Signature get(Account account, SignatureBy keyType, String key) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        // this assumes getAllSignatures is cached and number of signatures is reasaonble. 
        // might want a per-signature cache (i.e., use "SIGNATURE_BY_ID_"+id as a key, etc) 
        switch(keyType) {
            case id: 
                for (Signature signature : getAllSignatures(account)) 
                    if (signature.getId().equals(key)) return signature;
                return null;
            case name: 
                for (Signature signature : getAllSignatures(account)) 
                    if (signature.getName().equalsIgnoreCase(key)) return signature;
                return null;             
            default:
                return null;
        }
    }
    
    private static final String DATA_SOURCE_LIST_CACHE_KEY = "LdapProvisioning.DATA_SOURCE_CACHE";
    
    private List<DataSource> getDataSourcesByQuery(LdapEntry entry, String query, DirContext initCtxt) throws ServiceException {
        DirContext ctxt = initCtxt;
        List<DataSource> result = new ArrayList<DataSource>();
        try {
            if (ctxt == null)
                ctxt = LdapUtil.getDirContext();
            String base = entry.getDN();
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, base, query, sSubtreeSC);
            while(ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                result.add(new LdapDataSource((Account)entry, sr.getNameInNamespace(), sr.getAttributes()));
            }
            ne.close();            
        } catch (NameNotFoundException e) {
            return null;
        } catch (InvalidNameException e) {
            return null;                        
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup data source via query: "+query+ " message: "+e.getMessage(), e);
        } finally {
            if (initCtxt == null)
                LdapUtil.closeContext(ctxt);
        }
        return result;
    }

    private DataSource getDataSourceById(LdapEntry entry, String id,  DirContext ctxt) throws ServiceException {
        id= LdapUtil.escapeSearchFilterArg(id);
        List<DataSource> result = getDataSourcesByQuery(entry, "(&(zimbraDataSourceId="+id+")(objectclass=zimbraDataSource))", ctxt); 
        return result.isEmpty() ? null : result.get(0);
    }

    private DataSource getDataSourceByName(LdapEntry entry, String name,  DirContext ctxt) throws ServiceException {
        name = LdapUtil.escapeSearchFilterArg(name);
        List<DataSource> result = getDataSourcesByQuery(entry, "(&(zimbraDataSourceName="+name+")(objectclass=zimbraDataSource))", ctxt); 
        return result.isEmpty() ? null : result.get(0);
    }    

    private String getDataSourceDn(LdapEntry entry, String name) {
        return A_zimbraDataSourceName + "=" + LdapUtil.escapeRDNValue(name) + "," + entry.getDN();    
    }
    
    protected ReplaceAddressResult replaceMailAddresses(Entry entry, String attrName, String oldAddr, String newAddr) throws ServiceException {
        String oldDomain = EmailUtil.getValidDomainPart(oldAddr);
        String newDomain = EmailUtil.getValidDomainPart(newAddr);    
        
        String oldAddrs[] = entry.getMultiAttr(attrName);
        String newAddrs[] = new String[0];      
         
        for (int i = 0; i < oldAddrs.length; i++) {
            String oldMail = oldAddrs[i];
            if (oldMail.equals(oldAddr)) {
                // exact match, replace the entire old addr with new addr
                newAddrs = addMultiValue(newAddrs, newAddr);
            } else {
                String[] oldParts = EmailUtil.getLocalPartAndDomain(oldMail);
                
                // sanity check, or should we ignore and continue?
                if (oldParts == null)
                    throw ServiceException.FAILURE("bad value for " + attrName + " " + oldMail, null);
                String oldL = oldParts[0];
                String oldD = oldParts[1];
                
                if (oldD.equals(oldDomain)) {
                    // old domain is the same as the domain being renamed, 
                    //   - keep the local part 
                    //   - replace the domain with new domain 
                    String newMail = oldL + "@" + newDomain;
                    newAddrs = addMultiValue(newAddrs, newMail);
                } else {
                    // address is not in the domain being renamed, keep as is
                    newAddrs = addMultiValue(newAddrs, oldMail);
                }
            }
        }
        
        // returns a pair of parallel arrays of old and new addrs
        return new ReplaceAddressResult(oldAddrs, newAddrs);
     }
    
    protected boolean addressExists(DirContext ctxt, String baseDN, String[] addrs) throws ServiceException {
        StringBuilder query = new StringBuilder();
        query.append("(|");
        for (int i=0; i < addrs.length; i++) {
            query.append(String.format("(%s=%s)", Provisioning.A_zimbraMailDeliveryAddress, addrs[i]));
            query.append(String.format("(%s=%s)", Provisioning.A_zimbraMailAlias, addrs[i]));
        }
        query.append(")");
        
        try {
            NamingEnumeration ne = LdapUtil.searchDir(ctxt, baseDN, query.toString(), sSubtreeSC);
            if (ne.hasMore()) 
                return true;
            else
                return false;
        } catch (NameNotFoundException e) {
            return false;
        } catch (InvalidNameException e) {
            throw ServiceException.FAILURE("unable to lookup account via query: "+query.toString()+" message: "+e.getMessage(), e);       
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to lookup account via query: "+query.toString()+" message: "+e.getMessage(), e);
        } finally {
        }
    }
    
    // MOVE OVER ALL aliases. doesn't throw an exception, just logs
    // There could be a race condition that the alias might get taken 
    // in the new domain post the check.  Anyone who calls this API must 
    // pay attention to the warning message
    private void moveAliases(DirContext ctxt, ReplaceAddressResult addrs, String newDomain, String primaryUid, 
                             String targetOldDn, String targetNewDn,
                             String targetOldDomain, String targetNewDomain)  throws ServiceException {
    	
        for (int i=0; i < addrs.newAddrs().length; i++) {
            String oldAddr = addrs.oldAddrs()[i];
            String newAddr = addrs.newAddrs()[i];
            	
            String aliasNewDomain = EmailUtil.getValidDomainPart(newAddr); 
            
            if (aliasNewDomain.equals(newDomain)) {
                String[] oldParts = EmailUtil.getLocalPartAndDomain(oldAddr);
                String oldAliasDN = mDIT.aliasDN(targetOldDn, targetOldDomain, oldParts[0], oldParts[1]);
                String newAliasDN = mDIT.aliasDNRename(targetNewDn, targetNewDomain, newAddr);
                
                if (oldAliasDN.equals(newAliasDN))
                    continue;
                    
                // skip the extra alias that is the same as the primary
                String newAliasParts[] = EmailUtil.getLocalPartAndDomain(newAddr);
                String newAliasLocal = newAliasParts[0];
                if (!(primaryUid != null && newAliasLocal.equals(primaryUid))) {
                	try {
                		LdapUtil.renameEntry(ctxt, oldAliasDN, newAliasDN);
                	} catch (NameAlreadyBoundException nabe) {
                		ZimbraLog.account.warn("unable to move alias from " + oldAliasDN + " to " + newAliasDN, nabe);
                	} catch (NamingException ne) {
                		throw ServiceException.FAILURE("unable to move aliases", null);
                	} finally {
                	}
                }
             } 
        }
    }
    
    
    @Override
    public DataSource createDataSource(Account account, DataSource.Type dsType, String dsName, Map<String, Object> dataSourceAttrs) throws ServiceException {
        return createDataSource(account, dsType, dsName, dataSourceAttrs, false);
    }

    @Override
    public DataSource createDataSource(Account account, DataSource.Type dsType, String dsName, Map<String, Object> dataSourceAttrs, boolean passwdAlreadyEncrypted) throws ServiceException {
        removeAttrIgnoreCase("objectclass", dataSourceAttrs);    
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());
        
        List<DataSource> existing = getAllDataSources(account);
        if (existing.size() >= account.getLongAttr(A_zimbraDataSourceMaxNumEntries, 20))
            throw AccountServiceException.TOO_MANY_DATA_SOURCES();
        
        dataSourceAttrs.put(A_zimbraDataSourceName, dsName); // must be the same

        account.setCachedData(DATA_SOURCE_LIST_CACHE_KEY, null);

        HashMap attrManagerContext = new HashMap();
        AttributeManager.getInstance().preModify(dataSourceAttrs, null, attrManagerContext, true, true);

        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);

            String dn = getDataSourceDn(ldapEntry, dsName);

            Attributes attrs = new BasicAttributes(true);
            LdapUtil.mapToAttrs(dataSourceAttrs, attrs);
            Attribute oc = LdapUtil.addAttr(attrs, A_objectClass, "zimbraDataSource");
            oc.add(LdapDataSource.getObjectClass(dsType));
            
            String dsId = LdapUtil.getAttrString(attrs, A_zimbraDataSourceId);
            if (dsId == null) {
                dsId = LdapUtil.generateUUID();
                attrs.put(A_zimbraDataSourceId, dsId);
            }
            
            String password = LdapUtil.getAttrString(attrs, A_zimbraDataSourcePassword);
            if (password != null) {
                String encrypted = passwdAlreadyEncrypted ? password : DataSource.encryptData(dsId, password);
                attrs.put(A_zimbraDataSourcePassword, encrypted);
            }

            LdapUtil.createEntry(ctxt, dn, attrs, "createDataSource");

            DataSource ds = getDataSourceById(ldapEntry, dsId, ctxt);
            AttributeManager.getInstance().postModify(dataSourceAttrs, ds, attrManagerContext, true);
            return ds;
        } catch (NameAlreadyBoundException nabe) {
            throw AccountServiceException.DATA_SOURCE_EXISTS(dsName);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to create data source", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
    }

    @Override
    public void deleteDataSource(Account account, String dataSourceId) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        account.setCachedData(DATA_SOURCE_LIST_CACHE_KEY, null);
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext(true);
            DataSource dataSource = getDataSourceById(ldapEntry, dataSourceId, ctxt);
            if (dataSource == null)
                throw AccountServiceException.NO_SUCH_DATA_SOURCE(dataSourceId);
            String dn = getDataSourceDn(ldapEntry, dataSource.getName());
            LdapUtil.unbindEntry(ctxt, dn);
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to delete data source: "+dataSourceId, e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }        
    }

    
    @Override
    public List<DataSource> getAllDataSources(Account account) throws ServiceException {
        
        @SuppressWarnings("unchecked")
        List<DataSource> result = (List<DataSource>) account.getCachedData(DATA_SOURCE_LIST_CACHE_KEY);
        
        if (result != null) {
            return result;
        }        
        
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());
        result = getDataSourcesByQuery(ldapEntry, "(objectclass=zimbraDataSource)", null);
        result = Collections.unmodifiableList(result);
        account.setCachedData(DATA_SOURCE_LIST_CACHE_KEY, result);        
        return result;
    }

    public void removeAttrIgnoreCase(String attr, Map<String, Object> attrs) {
        for (String key : attrs.keySet()) {
            if (key.equalsIgnoreCase(attr)) {
                attrs.remove(key);
                return;
            }
        }
    }
    
    @Override
    public void modifyDataSource(Account account, String dataSourceId, Map<String, Object> attrs) throws ServiceException {
        removeAttrIgnoreCase("objectclass", attrs);
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        LdapDataSource ds = (LdapDataSource) getDataSourceById(ldapEntry, dataSourceId, null);
        if (ds == null)
            throw AccountServiceException.NO_SUCH_DATA_SOURCE(dataSourceId);

        account.setCachedData(DATA_SOURCE_LIST_CACHE_KEY, null);
        
        attrs.remove(A_zimbraDataSourceId);
        
        String name = (String) attrs.get(A_zimbraDataSourceName);
        boolean newName = (name != null && !name.equals(ds.getName()));
        if (newName) attrs.remove(A_zimbraDataSourceName);

        String password = (String) attrs.get(A_zimbraDataSourcePassword);
        if (password != null) {
            attrs.put(A_zimbraDataSourcePassword, DataSource.encryptData(ds.getId(), password));
        }
        
        modifyAttrs(ds, attrs, true);
        if (newName) {
            // the datasoruce cache could've been loaded again if getAllDataSources were called in pre/poseModify callback, so we clear it again
            account.setCachedData(DATA_SOURCE_LIST_CACHE_KEY, null);
            DirContext ctxt = null;
            try {
                ctxt = LdapUtil.getDirContext(true);
                String newDn = getDataSourceDn(ldapEntry, name);            
                LdapUtil.renameEntry(ctxt, ds.getDN(), newDn);
            } catch (NamingException e) {
                throw ServiceException.FAILURE("unable to rename datasource: "+newName, e);
            } finally {
                LdapUtil.closeContext(ctxt);
            }
        }
    }

    @Override
    public DataSource get(Account account, DataSourceBy keyType, String key) throws ServiceException {
        LdapEntry ldapEntry = (LdapEntry) (account instanceof LdapEntry ? account : getAccountById(account.getId()));
        if (ldapEntry == null) 
            throw AccountServiceException.NO_SUCH_ACCOUNT(account.getName());

        // this assumes getAllDataSources is cached and number of data sources is reasaonble. 
        // might want a per-data-source cache (i.e., use "DATA_SOURCE_BY_ID_"+id as a key, etc) 
        
        switch(keyType) {
            case id:
                for (DataSource source : getAllDataSources(account)) 
                    if (source.getId().equals(key))
                        return source;
                return null;
                //return getDataSourceById(ldapEntry, key, null);
            case name: 
                for (DataSource source : getAllDataSources(account)) 
                    if (source.getName().equalsIgnoreCase(key))
                        return source;
                return null;
                //return getDataSourceByName(ldapEntry, key, null);
            default:
                return null;
        }
    }
    
    public long countAccounts(String domain) throws ServiceException {
        StringBuilder buf = new StringBuilder();
        buf.append("(&");
        buf.append("(!(zimbraIsSystemResource=TRUE))");
        buf.append("(objectclass=zimbraAccount)(!(objectclass=zimbraCalendarResource))");
        buf.append(")");

        String query = buf.toString();
        int numAccounts = 0;
        
        DirContext ctxt = null;
        try {
            ctxt = LdapUtil.getDirContext();
            
            SearchControls searchControls = 
                new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, new String[] {"zimbraId", "objectclass"}, false, false);

            NamingEnumeration<SearchResult> ne = null;

            try {
                String dn = mDIT.domainToAccountSearchDN(domain);
                ne = ctxt.search(dn, query, searchControls);
                while (ne != null && ne.hasMore()) {
                    SearchResult sr = ne.nextElement();
                    dn = sr.getNameInNamespace();
                    // skip admin accounts
                    if (dn.endsWith("cn=zimbra")) continue;
                    Attributes attrs = sr.getAttributes();
                    Attribute objectclass = attrs.get("objectclass");
                    if (objectclass.contains("zimbraAccount")) 
                        numAccounts++;
                }
            } finally {
                if (ne != null) ne.close();
            }
        } catch (NamingException e) {
            throw ServiceException.FAILURE("unable to count the users", e);
        } finally {
            LdapUtil.closeContext(ctxt);
        }
        return numAccounts;
    }
    
    public String getNamingRdnAttr(Entry entry) throws ServiceException {
        return mDIT.getNamingRdnAttr(entry);
    }
    
    /*
     * only called from TestProvisioning for unittest
     */
    public String getDN(Entry entry) throws ServiceException {
        if (entry instanceof LdapMimeType)
            return ((LdapMimeType)entry).getDN();
        else if (entry instanceof LdapCalendarResource)
            return ((LdapCalendarResource)entry).getDN();
        else if (entry instanceof LdapAccount)
            return ((LdapAccount)entry).getDN();
        else if (entry instanceof LdapAlias)
            return ((LdapAlias)entry).getDN();
        else if (entry instanceof LdapCos)
            return ((LdapCos)entry).getDN();
        else if (entry instanceof LdapDataSource)
            return ((LdapDataSource)entry).getDN();
        else if (entry instanceof LdapDistributionList)
            return ((LdapDistributionList)entry).getDN();
        else if (entry instanceof LdapDomain)
            return ((LdapDomain)entry).getDN();
        else if (entry instanceof LdapIdentity)
            return ((LdapIdentity)entry).getDN();
        else if (entry instanceof LdapSignature)
            return ((LdapSignature)entry).getDN();
        else if (entry instanceof LdapServer)
            return ((LdapServer)entry).getDN();
        else if (entry instanceof LdapZimlet)
            return ((LdapZimlet)entry).getDN();
        else
            throw ServiceException.FAILURE("not a ldap entry", null);
    }
    

    
    static void flushDomainCache(Provisioning prov, String domainId) throws ServiceException {
        CacheEntry[] cacheEntries = new CacheEntry[1];
        cacheEntries[0] = new CacheEntry(CacheEntryBy.id, domainId);
        prov.flushCache(CacheEntryType.domain, cacheEntries);
    }
    
    void flushDomainCacheOnAllServers(String domainId) throws ServiceException {
        SoapProvisioning soapProv = new SoapProvisioning();
        
        for (Server server : getAllServers()) {
            
            String hostname = server.getAttr(Provisioning.A_zimbraServiceHostname);
                                
            int port = server.getIntAttr(Provisioning.A_zimbraMailSSLPort, 0);
            if (port <= 0) {
                ZimbraLog.account.warn("flushDomainCacheOnAllServers: remote server " + server.getName() + " does not have https port enabled, domain cache not flushed on server");
                continue;        
            }
                
            soapProv.soapSetURI(LC.zimbra_admin_service_scheme.value()+hostname+":"+port+ZimbraServlet.ADMIN_SERVICE_URI);
                
            try {
                soapProv.soapZimbraAdminAuthenticate();
                flushDomainCache(soapProv, domainId);
            } catch (ServiceException e) {
                ZimbraLog.account.warn("flushDomainCacheOnAllServers: domain cache not flushed on server " + server.getName(), e);
            }
        }
    }
    
    @Override
    public void flushCache(CacheEntryType type, CacheEntry[] entries) throws ServiceException {
        
        NamedEntryCache cache = null;
        Set<NamedEntry> namedEntries = null;
        
        switch (type) {
        case account:
            cache = sAccountCache;
            if (entries != null) {
                namedEntries = new HashSet<NamedEntry>();
                for (CacheEntry entry : entries) {
                    AccountBy accountBy = (entry.mEntryBy==CacheEntryBy.id)? AccountBy.id : AccountBy.name;
                    Account account = get(accountBy, entry.mEntryIdentity);
                    if (account == null)
                        throw AccountServiceException.NO_SUCH_ACCOUNT(entry.mEntryIdentity);
                    else    
                        namedEntries.add(account);
                }
            }
            break;
        case cos:
            cache = sCosCache;
            if (entries != null) {
                namedEntries = new HashSet<NamedEntry>();
                for (CacheEntry entry : entries) {
                    CosBy cosBy = (entry.mEntryBy==CacheEntryBy.id)? CosBy.id : CosBy.name;
                    Cos cos = get(cosBy, entry.mEntryIdentity);
                    if (cos == null)
                        throw AccountServiceException.NO_SUCH_COS(entry.mEntryIdentity);
                    else    
                        namedEntries.add(cos);
                }
            }
            break;
        case domain:
            if (entries != null) {
                namedEntries = new HashSet<NamedEntry>();
                for (CacheEntry entry : entries) {
                    DomainBy domainBy = (entry.mEntryBy==CacheEntryBy.id)? DomainBy.id : DomainBy.name;
                    Domain domain = get(domainBy, entry.mEntryIdentity);
                    if (domain == null)
                        throw AccountServiceException.NO_SUCH_DOMAIN(entry.mEntryIdentity);
                    else    
                        namedEntries.add(domain);
                }
            }
            
            if (entries == null) {
                sDomainCache.clear();
            } else {    
                for (NamedEntry entry : namedEntries) {
                    sDomainCache.remove((Domain)entry);
                }
            }
            return;
        case server:
            cache = sServerCache;
            if (entries != null) {
                namedEntries = new HashSet<NamedEntry>();
                for (CacheEntry entry : entries) {
                    ServerBy serverBy = (entry.mEntryBy==CacheEntryBy.id)? ServerBy.id : ServerBy.name;
                    Server server = get(serverBy, entry.mEntryIdentity);
                    if (server == null)
                        throw AccountServiceException.NO_SUCH_SERVER(entry.mEntryIdentity);
                    else    
                        namedEntries.add(server);
                }
            }
            break;
        case zimlet:
            cache = sZimletCache;
            if (entries != null) {
                namedEntries = new HashSet<NamedEntry>();
                for (CacheEntry entry : entries) {
                    if (entry.mEntryBy==CacheEntryBy.id)
                        throw ServiceException.INVALID_REQUEST("zimlet by id is not supported "+type, null);
                    Zimlet zimlet = getZimlet(entry.mEntryIdentity);
                    if (zimlet == null)
                        throw AccountServiceException.NO_SUCH_ZIMLET(entry.mEntryIdentity);
                    else    
                        namedEntries.add(zimlet);
                }
            }
            break;
        default:
            throw ServiceException.INVALID_REQUEST("invalid cache type "+type, null);
        }
        
        if (namedEntries == null)
            cache.clear();
        else {
            for (NamedEntry entry : namedEntries) {
                cache.remove(entry);
            }
        }
        
    }

}
