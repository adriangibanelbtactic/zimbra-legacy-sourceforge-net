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

package com.zimbra.cs.account.ldap;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.GalContact;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.SearchGalResult;
import com.zimbra.cs.stats.ZimbraPerf;
import org.apache.commons.codec.binary.Base64;

import javax.naming.AuthenticationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.InvalidAttributesException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SchemaViolationException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.Rdn;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * @author schemers
 */
public class LdapUtil {
        
    public final static String LDAP_TRUE  = "TRUE";
    public final static String LDAP_FALSE = "FALSE";

    final static String EARLIEST_SYNC_TOKEN = "19700101000000Z";

    private static int SALT_LEN = 4; // to match LDAP SSHA password encoding
    private static String ENCODING = "{SSHA}";

    private static String sLdapURL;
    private static String sLdapMasterURL;    
    
    private static Hashtable<String, String> sEnvMasterAuth;
    private static Hashtable<String, String> sEnvAuth;
    private static String[] sEmptyMulti = new String[0];

    static final SearchControls sSubtreeSC = new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, null, false, false);
    
    static {
        String ldapHost = LC.ldap_host.value();
        String ldapPort = LC.ldap_port.value();
        
        sLdapURL = LC.ldap_url.value().trim();
        if (sLdapURL.length() == 0) {
            sLdapURL = "ldap://" + ldapHost + ":" + ldapPort + "/";
        }
        sLdapMasterURL = LC.ldap_master_url.value().trim();
        if (sLdapMasterURL.length() == 0) sLdapMasterURL = sLdapURL;

        /* See http://java.sun.com/products/jndi/tutorial/ldap/connect/config.html */
        System.setProperty("com.sun.jndi.ldap.connect.pool.debug", LC.ldap_connect_pool_debug.value());
        System.setProperty("com.sun.jndi.ldap.connect.pool.initsize", LC.ldap_connect_pool_initsize.value());
        System.setProperty("com.sun.jndi.ldap.connect.pool.maxsize", LC.ldap_connect_pool_maxsize.value());
        System.setProperty("com.sun.jndi.ldap.connect.pool.prefsize", LC.ldap_connect_pool_prefsize.value());
        System.setProperty("com.sun.jndi.ldap.connect.pool.timeout", LC.ldap_connect_pool_timeout.value());
        System.setProperty("com.sun.jndi.ldap.connect.pool.protocol", "plain ssl");
    }
    
    public static String getLdapURL() {
        return sLdapURL;
    }

    public static void closeContext(Context ctxt) {
        try {
            if (ctxt != null) {
                //ZimbraLog.account.error("closeDirContext", new RuntimeException("------------------- CLOSE"));
                ctxt.close();
            }
        } catch (NamingException e) {
            // TODO log?
            //e.printStackTrace();
        }
    }

    public static void closeEnumContext(NamingEnumeration ctxt) {
        try {
            if (ctxt != null)
                ctxt.close();
        } catch (NamingException e) {
            // TODO log?
            //e.printStackTrace();
        }
    }

// TODO: need options for get master or replica connections (write vs. read)
    // and maybe admin vs non-admin access 

    /**
     * 
     * @return
     * @throws NamingException
     */
    private static synchronized Hashtable getDefaultEnv(boolean master) {
        Hashtable<String, String> sEnv = null;
        
        if (master) {
            if (sEnvMasterAuth != null) return sEnvMasterAuth;
            else sEnv = sEnvMasterAuth = new Hashtable<String, String>(); 
        } else {
            if (sEnvAuth != null) return sEnvAuth;
            else sEnv = sEnvAuth = new Hashtable<String, String>();             
        }

        sEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        sEnv.put(Context.PROVIDER_URL, master ? sLdapMasterURL : sLdapURL);
        sEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
        sEnv.put(Context.SECURITY_PRINCIPAL, LC.zimbra_ldap_userdn.value());
        sEnv.put(Context.SECURITY_CREDENTIALS, LC.zimbra_ldap_password.value());
        sEnv.put(Context.REFERRAL, "follow");
            
        // wait at most 10 seconds for a connection
        sEnv.put("com.sun.jndi.ldap.connect.timeout", LC.ldap_connect_timeout.value());
        // enable connection pooling
        sEnv.put("com.sun.jndi.ldap.connect.pool", "true");
        // env.put("java.naming.ldap.derefAliases", "never");
        //
        // default: env.put("java.naming.ldap.version", "3");
        return sEnv;
    }
    
    /**
     * 
     * @return
     * @throws NamingException
     */
    public static DirContext getDirContext() throws ServiceException {
        return getDirContext(false);
    }

    /**
     * 
     * @return
     * @throws NamingException
     */
    public static DirContext getDirContext(boolean master) throws ServiceException {
        try {
            long start = ZimbraPerf.STOPWATCH_LDAP_DC.start();
            DirContext dirContext = new InitialLdapContext(getDefaultEnv(master), null);
            ZimbraPerf.STOPWATCH_LDAP_DC.stop(start);
            //ZimbraLog.account.error("getDirContext", new RuntimeException("------------------- OPEN"));
            return dirContext;
        } catch (NamingException e) {
            throw ServiceException.FAILURE("getDirectContext", e);
        }
    }

    private static String joinURLS(String urls[]) {
        if (urls.length == 1) return urls[0];
        StringBuffer url = new StringBuffer();
        for (int i=0; i < urls.length; i++) {
            if (i > 0) url.append(' ');
            url.append(urls[i]);
        }
        return url.toString();
    }
    
    /**
     * 
     * @return
     * @throws NamingException
     */
    public static DirContext getDirContext(String urls[], String bindDn, String bindPassword)  throws NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, joinURLS(urls));
        if (bindDn == null || bindPassword == null) {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, bindDn);
            env.put(Context.SECURITY_CREDENTIALS, bindPassword);        
        }
        env.put(Context.REFERRAL, "follow");
        // wait at most 10 seconds
        env.put("com.sun.jndi.ldap.connect.timeout", LC.ldap_connect_timeout.value());
        // enable connection pooling
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        return new InitialLdapContext(env, null);
    }

    public static void ldapAuthenticate(String urls[], String principal, String password) throws NamingException {
        if (password == null || password.equals("")) 
            throw new AuthenticationException("empty password");

        Hashtable<String, String> env = new Hashtable<String, String>();
        // wait at most 10 seconds
        env.put("com.sun.jndi.ldap.connect.timeout", LC.ldap_connect_timeout.value());        
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, joinURLS(urls));
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, password);
        DirContext context = null;
        try {
            context = new InitialLdapContext(env, null);
        } finally {
            closeContext(context);
        }
    }

    public static void ldapAuthenticate(String url[], String password, String searchBase, String searchFilter, String searchDn, String searchPassword) throws NamingException {
        if (password == null || password.equals("")) 
            throw new AuthenticationException("empty password");

        DirContext ctxt = null;
        String resultDn = null;;
        String tooMany = null;
        NamingEnumeration ne = null;
        try {
            ctxt = getDirContext(url, searchDn, searchPassword);
            ne = searchDir(ctxt, searchBase, searchFilter, sSubtreeSC);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                if (resultDn == null) {
                    resultDn = sr.getNameInNamespace();
                } else {
                    tooMany = sr.getNameInNamespace();
                    break;
                }
            }
        } finally {
            closeContext(ctxt);
            closeEnumContext(ne);
        }
        
        if (tooMany != null) {
            ZimbraLog.account.warn(String.format("ldapAuthenticate searchFilter returned more then one result: (dn1=%s, dn2=%s, filter=%s)", resultDn, tooMany, searchFilter));
            throw new AuthenticationException("too many results from search filter!");
        } else if (resultDn == null) {
            throw new AuthenticationException("empty search");
        }
        if (ZimbraLog.account.isDebugEnabled()) ZimbraLog.account.debug("search filter matched: "+resultDn);
        ldapAuthenticate(url, resultDn, password); 
    }
    
    public static boolean isSSHA(String encodedPassword) {
        return encodedPassword.startsWith(ENCODING);
    }
    
    public static boolean verifySSHA(String encodedPassword, String password) {
        if (!encodedPassword.startsWith(ENCODING))
            return false;
        byte[] encodedBuff = encodedPassword.substring(ENCODING.length()).getBytes();
        byte[] buff = Base64.decodeBase64(encodedBuff);
        if (buff.length <= SALT_LEN)
            return false;
        int slen = (buff.length == 28) ? 8 : SALT_LEN;
        byte[] salt = new byte[slen];
        System.arraycopy(buff, buff.length-slen, salt, 0, slen);
        String generated = generateSSHA(password, salt);
        return generated.equals(encodedPassword);
    }
    
    public static String generateSSHA(String password, byte[] salt) {
           try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            if (salt == null) {
                salt = new byte[SALT_LEN];
                SecureRandom sr = new SecureRandom();
                sr.nextBytes(salt);
            } 
            md.update(password.getBytes());
            md.update(salt);
            byte[] digest = md.digest();
            byte[] buff = new byte[digest.length + salt.length];
            System.arraycopy(digest, 0, buff, 0, digest.length);
            System.arraycopy(salt, 0, buff, digest.length, salt.length);
            return ENCODING + new String(Base64.encodeBase64(buff));
        } catch (NoSuchAlgorithmException e) {
            // this shouldn't happen unless JDK is foobar
            throw new RuntimeException(e);
        }
    }
    
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static String getAttrString(Attributes attrs, String name) throws NamingException {
        Attribute attr = attrs.get(name);
        if (attr != null) {
            Object o = attr.get();
            if (o instanceof String)
                return (String) o;
            else 
                return new String((byte[])o);
        } else {
            return null;
        }
    }

    public static String[] getMultiAttrString(Attributes attrs, String name) throws NamingException {
        Attribute attr = attrs.get(name);
        if (attr != null) {
            String result[] = new String[attr.size()];
            for (int i=0; i < attr.size(); i++) {
                Object o = attr.get(i);
                if (o instanceof String)
                    result[i] = (String) o;
                else 
                    result[i] = new String((byte[])o);
            }
            return result;
        } else {
            return sEmptyMulti;
        }
    }

    /**
     * Enumerates over the specified attributes and populates the specified map. The key in the map is the
     * attribute ID. For attrs with a single value, the value is a String, and for attrs with multiple values
     * the value is an array of Strings.
     * 
     * @param attrs the attributes to enumerate over
     * @throws NamingException
     */
    static Map<String, Object> getAttrs(Attributes attrs) throws NamingException  {
        Map<String,Object> map = new HashMap<String,Object>();        
        for (NamingEnumeration ne = attrs.getAll(); ne.hasMore(); ) {
            Attribute attr = (Attribute) ne.next();
            if (attr.size() == 1) {
                Object o = attr.get();
                if (o instanceof String)
                    map.put(attr.getID(), o);
                else 
                    map.put(attr.getID(), new String((byte[])o));
            } else {
                String result[] = new String[attr.size()];
                for (int i=0; i < attr.size(); i++) {
                    Object o = attr.get(i);
                    if (o instanceof String)
                        result[i] = (String) o;
                    else 
                        result[i] = new String((byte[])o);
                }
                map.put(attr.getID(), result);
            }
        }
        return map;
    }
    
    /**
     * escape *()\ in specified string to make sure user-supplied string doesn't open a security hole.
     * i.e., if the format string is "(sn=*%s*)", and the user types in "a)(zimbraIsAdminAccount=TRUE)(cn=a",
     * we don't want to search for "(sn=*a)(zimbraIsAdminAccount=TRUE)(cn=a*)".
     * 
     * @param s
     * @return
     */
    public static String escapeSearchFilterArg(String s) {
        if (s == null)
            return null;
        else 
            return s.replaceAll("([\\\\\\*\\(\\)])", "\\\\$0");
    }
    
    public static Attribute addAttr(Attributes attrs, String name, String value) {
        BasicAttribute a = new BasicAttribute(name);
        a.add(value);
        attrs.put(a);
        return a;
    }

    public static void simpleCreate(DirContext ctxt, String dn, Object objectClass, String[] attrs) throws NamingException {
        Attributes battrs = new BasicAttributes(true);
        if (objectClass instanceof String) {
            battrs.put(Provisioning.A_objectClass, objectClass);
        } else if (objectClass instanceof String[]) {
            String[] oclasses = (String[]) objectClass;
            Attribute a = new BasicAttribute(Provisioning.A_objectClass);
            for (int i=0; i < oclasses.length; i++)
                    a.add(oclasses[i]);
            battrs.put(a);
        }
        for (int i=0; i < attrs.length; i += 2)
            battrs.put(attrs[i], attrs[i+1]);
        Name cpName = new CompositeName().add(dn);
        Context newCtxt = ctxt.createSubcontext(cpName, battrs);
        newCtxt.close();
    }

    /**
     * "modify" the entry. If value is null or "", then remove attribute, otherwise replace/add it.
     */
    private static void modifyAttr(ArrayList<ModificationItem> modList, String name, String value, com.zimbra.cs.account.Entry entry) {
        int mod_op = (value == null || value.equals("")) ? DirContext.REMOVE_ATTRIBUTE : DirContext.REPLACE_ATTRIBUTE;
        if (mod_op == DirContext.REMOVE_ATTRIBUTE) {
            // make sure it exists
            if (entry.getAttr(name, false) == null)
                return;
        }
        BasicAttribute ba = new BasicAttribute(name);
        if (mod_op == DirContext.REPLACE_ATTRIBUTE)
            ba.add(value);
        modList.add(new ModificationItem(mod_op, ba));
    }

    /**
     * remove the attr with the specified value
     */
    private static void removeAttr(ArrayList<ModificationItem> modList, String name, String value, com.zimbra.cs.account.Entry entry) {
        if (!contains(entry.getMultiAttr(name, false), value)) return;
        
        BasicAttribute ba = new BasicAttribute(name);
        ba.add(value);
        modList.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, ba));
    }

    private static boolean contains(String[] values, String val) {
        if (values == null) return false;
        for (String s : values) {
            if (s.compareToIgnoreCase(val) == 0) return true;
        }
        return false;
    }
    
    /**
     * remove the attr with the specified value
     */
    private static void removeAttr(ArrayList<ModificationItem> modList, String name, String value[], com.zimbra.cs.account.Entry entry) {
        String[] currentValues = entry.getMultiAttr(name, false);
        if (currentValues == null || currentValues.length == 0) return;

        BasicAttribute ba = null;
        for (int i=0; i < value.length; i++) {
            if (!contains(currentValues, value[i])) continue;
            if (ba == null) ba = new BasicAttribute(name);
            ba.add(value[i]);
        }
        if (ba != null) modList.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, ba));

    }

    /**
     * add an additional attr with the specified value
     */
    private static void addAttr(ArrayList<ModificationItem> modList, String name, String value, com.zimbra.cs.account.Entry entry) {
        if (contains(entry.getMultiAttr(name, false), value)) return;        
        
        BasicAttribute ba = new BasicAttribute(name);
        ba.add(value);
        modList.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, ba));
    }

    
    /**
     * add an additional attr with the specified value
     */
    private static void addAttr(ArrayList<ModificationItem> modList, String name, String value[], com.zimbra.cs.account.Entry entry) {
        String[] currentValues = entry.getMultiAttr(name, false);
        
        BasicAttribute ba = null;
        for (int i=0; i < value.length; i++) {
            if (contains(currentValues, value[i])) continue;
            if (ba == null) ba = new BasicAttribute(name);
            ba.add(value[i]);
        }
        if (ba != null) modList.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, ba));
    }
    
    /**
     * Modifies the specified entry.  <code>attrs</code> is a <code>Map</code> consisting of
     * keys that are <code>String</code>s, and values that are either
     * <ul>
     *   <li><code>null</code>, in which case the attr is removed</li>
     *   <li>a single <code>Object</code>, in which case the attr is modified
     *     based on the object's <code>toString()</code> value</li>
     *   <li>an <code>Object</code> array or <code>Collection</code>,
     *     in which case a multi-valued attr is updated</li>
     * </ul>
     */
    public static void modifyAttrs(DirContext ctxt, String dn, Map attrs, com.zimbra.cs.account.Entry entry) throws NamingException, ServiceException {
        ArrayList<ModificationItem> modlist = new ArrayList<ModificationItem>();
        for (Iterator mit=attrs.entrySet().iterator(); mit.hasNext(); ) {
            Map.Entry me = (Entry) mit.next();
            Object v= me.getValue();
            String key = (String) me.getKey();
            boolean doAdd = key.charAt(0) == '+';
            boolean doRemove = key.charAt(0) == '-';
            
            if (doAdd || doRemove) {
                // make sure there aren't other changes without +/- going on at the same time 
                key = key.substring(1);
                if (attrs.containsKey(key)) 
                    throw ServiceException.INVALID_REQUEST("can't mix +attrName/-attrName with attrName", null);
            }

            // Convert array to List so it can be treated as a Collection
            if (v instanceof Object[]) {
                // Note: Object[] cast is required, so that asList() knows to create a List
                // that contains the contents of the object array, as opposed to a List with one
                // element, which is the entire Object[].  Ick.
                v = Arrays.asList((Object[]) v);
            }
            
            if (v instanceof Collection) {
                Collection c = (Collection) v;
                if (c.size() == 0) {
                    // make sure it exists
                    if (entry.getAttr(key, false) != null) {
                        BasicAttribute ba = new BasicAttribute(key);
                        modlist.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, ba));
                    }
                } else {
                    // Convert values Collection to a String array
                    String[] sa = new String[c.size()];
                    int i = 0;
                    for (Object o : c) {
                        sa[i++] = (o == null ? null : o.toString());
                    }
                    
                    // Add attrs
                    if (doAdd) addAttr(modlist, key, sa, entry);
                    else if (doRemove) removeAttr(modlist, key, sa, entry);
                    else {
                        BasicAttribute ba = new BasicAttribute(key);
                        for (i=0; i < sa.length; i++)
                            ba.add(sa[i]);
                        modlist.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, ba));
                    }
                }
            } else if (v instanceof Map) {
                throw ServiceException.FAILURE("Map is not a supported value type", null);
            } else {
                String s = (v == null ? null : v.toString());
                if (doAdd) addAttr(modlist, key, s, entry);
                else if (doRemove) removeAttr(modlist, key, s, entry);
                else modifyAttr(modlist, key, s, entry);
            }
        }
        ModificationItem[] mods = new ModificationItem[modlist.size()];
        modlist.toArray(mods);
        modifyAttributes(ctxt, dn, mods);
    }

    /**
     * take a map (key = String, value = String | String[]) and populate Attributes.
     * 
     * @param attrs
     */
    public static void mapToAttrs(Map mapAttrs, Attributes attrs) {
        for (Iterator mit=mapAttrs.entrySet().iterator(); mit.hasNext(); ) {
            Map.Entry me = (Entry) mit.next();
            Object v = me.getValue();
            if (v instanceof String)
                attrs.put((String)me.getKey(), (String)v);
            else if (v instanceof String[]) {
                String[] sa = (String[]) v;
                BasicAttribute a = new BasicAttribute((String)me.getKey());
                for (int i=0; i < sa.length; i++)
                        a.add(sa[i]);
                attrs.put(a);
            }
        }
    }

    private static String domainToDN(String parts[], int offset) {
        StringBuffer sb = new StringBuffer(128);
        for (int i=offset; i < parts.length; i++) {
            if (i-offset > 0) sb.append(",");
            sb.append("dc=").append(escapeRDNValue(parts[i]));
        }
        return sb.toString();
    }

    /**
     * Given a domain like foo.com, return the dn: dc=foo,dc=com
     * @param domain
     * @return the dn
     */
    public static String domainToDN(String domain) {
        return domainToDN(domain.split("\\."), 0);
    }


    /**
     * Given an email like blah@foo.com, return the domain dn: dc=foo,dc=com
     * @return the dn
     * @throws ServiceException 
     */
    public static String emailToDomainDN(String email) throws ServiceException {
        int index = email.indexOf('@');
        if (index == -1) 
            throw ServiceException.INVALID_REQUEST("must be an email address: "+email, null);
        String domain = email.substring(index+1);
        return domainToDN(domain.split("\\."), 0);
    }

    /**
     * given a dn like "uid=foo,ou=people,dc=widgets,dc=com", return the string "widgets.com".
     * 
     * @param dn
     * @return
     */
    public static String dnToDomain(String dn) {
        String[] parts = dn.split(",");
        StringBuffer sb = new StringBuffer();

        	for (int i=0; i < parts.length; i++) {
        	    if (parts[i].startsWith("dc=")) {
        	        if (sb.length() > 0)
        	            sb.append(".");
        	        sb.append(unescapeRDNValue(parts[i].substring(3)));
        	    }
        	}
        return sb.toString();
    }

    /**
     * Given a dn like "uid=foo,ou=people,dc=widgets,dc=com", return the string "foo@widgets.com".
     * 
     * @param dn
     * @return
     */
    public static String dnToEmail(String dn) {
        String [] parts = dn.split(",");
        StringBuffer domain = new StringBuffer(dn.length());
        String uid = null;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith("dc=")) {
                if (domain.length() > 0)
                    domain.append(".");
                domain.append(unescapeRDNValue(parts[i].substring(3)));
            } else if (parts[i].startsWith("uid=")) {
                uid = unescapeRDNValue(parts[i].substring(4));
            }
        }
        if (uid == null)
            return null; // TODO should this be an exception
        if (domain.length() == 0)
            return uid;
        return new StringBuffer(uid).append('@').append(domain).toString();
    }
    
    /**
     * Given a dn like "uid=zimbra,cn=admins,cn=zimbra", return the string "zimbra".
     * @param dn
     * @return
     */
    public static String dnToUid(String dn) {
        String [] parts = dn.split(",");
        String uid = null;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith("uid=")) {
                uid = unescapeRDNValue(parts[i].substring(4));
                break;
            }
        }
        return uid;
    }
    
    /**
     * Given a domain like foo.com, return an array of dns that work their way up the tree:
     *    [0] = dc=foo,dc=com
     *    [1] = dc=com
     * 
     * @return the array of DNs
     */
    public static String[] domainToDNs(String[] parts) {
        String dns[] = new String[parts.length];
        for (int i=parts.length-1; i >= 0; i--) {
            dns[i] = domainToDN(parts, i);
        }
        return dns;
    }

    static String[] removeMultiValue(String values[], String value) {
        List<String> list = new ArrayList<String>(Arrays.asList(values));
        boolean updated = list.remove(value);
        if (updated) {
            return list.toArray(new String[list.size()]);
        } else {
            return values;
        }
    }
    
    public static String getBooleanString(boolean b) {
        if (b) {
            return LDAP_TRUE;
        }
        return LDAP_FALSE;
    }

    /*
      * expansions for bind dn string:
      * 
      * %n = username with @ (or without, if no @ was specified)
      * %u = username with @ removed
      * %d = domain as foo.com
      * %D = domain as dc=foo,dc=com
      * 
      * exchange example, where the exchange domian is different than the zimbra one
      * 
      * zimbraAuthMech      ldap
      * zimbraAuthLdapURL   ldap://exch1/
      * zimbraAuthLdapDn    %n@example.zimbra.com
      * 
      * our own LDAP example:
      * 
      * zimbraAuthMech       ldap
      * zimbraAuthLdapURL    ldap://server.example.zimbra.com/
      * zimbraAuthLdapUserDn uid=%u,ou=people,%D
      */
      public static String computeAuthDn(String name, String bindDnRule) {
         if (bindDnRule == null || bindDnRule.equals("") || bindDnRule.equals("%n"))
             return name;
    
         int at = name.indexOf("@");
    
         Map<String, String> vars = new HashMap<String, String>();
         vars.put("n", name);         
    
         if (at  == -1) {
             vars.put("u", name);
         } else {
             vars.put("u", name.substring(0, at));
             String d = name.substring(at+1);
             vars.put("d", d);
             vars.put("D", domainToDN(d));
         }
         
         return LdapProvisioning.expandStr(bindDnRule, vars);
      }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.Provisioning#searchGal(java.lang.String)
     */
    public static SearchGalResult searchLdapGal(
            String url[],
            String bindDn,
            String bindPassword,
            String base,
            String filter, 
            String n,
            int maxResults,
            LdapGalMapRules rules,
            String token) throws NamingException, ServiceException {
    
        SearchGalResult result = new SearchGalResult();
        result.matches = new ArrayList<GalContact>();
    
        if (url == null || url.length == 0 || base == null || filter == null) {
            if (url == null || url.length == 0)
                ZimbraLog.misc.warn("searchLdapGal url is null");
            if (base == null)
                ZimbraLog.misc.warn("searchLdapGal base is null");
            if (filter == null)
                ZimbraLog.misc.warn("searchLdapGal queryExpr is null");
            return result;
        }
    
        if (filter.indexOf("(") == -1) {
            String queryExpr = LdapProvisioning.getFilterDef(filter);
            if (queryExpr != null)
                filter = queryExpr;
        }
                

        Map<String, String> vars = new HashMap<String, String>();
        vars.put("s", n);
        String query = LdapProvisioning.expandStr(filter, vars);
        if (token != null) {
            if (token.equals(""))
                query = query.replaceAll("\\*\\*", "*");
            else  {
                String arg = LdapUtil.escapeSearchFilterArg(token);                
                query = "(&(|(modifyTimeStamp>="+arg+")(createTimeStamp>="+arg+")(whenModified>="+arg+")(whenCreated>="+arg+"))"+query.replaceAll("\\*\\*", "*")+")";                
            }                
        }

        if (ZimbraLog.misc.isDebugEnabled()) {
            ZimbraLog.misc.debug("searchLdapGal query:"+query);
            String attrs[] = rules.getLdapAttrs();
            for (String a: attrs) {
                ZimbraLog.misc.debug("searchLdapGal attr:"+a);
            }
        }
        SearchControls sc = new SearchControls(SearchControls.SUBTREE_SCOPE, maxResults, 0, rules.getLdapAttrs(), false, false);
        result.token = token != null && !token.equals("")? token : EARLIEST_SYNC_TOKEN;
        DirContext ctxt = null;
        NamingEnumeration ne = null;
        try {
            ctxt = getDirContext(url, bindDn, bindPassword);
            ne = searchDir(ctxt, base, query, sc);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                String dn = sr.getNameInNamespace();
                GalContact lgc = new GalContact(dn, rules.apply(sr.getAttributes()));
                String mts = (String) lgc.getAttrs().get("modifyTimeStamp");
                result.token = getLaterTimestamp(result.token, mts);
                String cts = (String) lgc.getAttrs().get("createTimeStamp");
                result.token = LdapUtil.getLaterTimestamp(result.token, cts);
                result.matches.add(lgc);
            }
            ne.close();
            ne = null;
        } catch (SizeLimitExceededException sle) {
            result.hadMore = true;
        } finally {
            closeContext(ctxt);
            closeEnumContext(ne);
        }
        return result;
    }

    /**
     * Return the later (more recent) of two LDAP timestamps.  Timestamp
     * format is YYYYMMDDhhmmssZ. (e.g. 20060315023000Z)
     * @param timeA
     * @param timeB
     * @return later of the two timestamps; a non-null timestamp is considered
     *         later than a null timestamp; null is returned if both timestamps
     *         are null
     */
    public static String getLaterTimestamp(String timeA, String timeB) {
        if (timeA == null) {
            return timeB;
        } else if (timeB == null) {
            return timeA;
        }
        return timeA.compareTo(timeB) > 0 ? timeA : timeB;
    }

    public static void changeActiveDirectoryPassword(String urls[], String email, String oldPassword, String newPassword)
        throws NamingException, ServiceException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
 
        //set security credentials, note using simple cleartext authentication
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");        
        env.put(Context.SECURITY_AUTHENTICATION,"simple");
        env.put(Context.SECURITY_PRINCIPAL, email);
        env.put(Context.SECURITY_CREDENTIALS, oldPassword);
        //specify use of ssl
        //env.put(Context.SECURITY_PROTOCOL,"ssl");
 
        env.put(Context.PROVIDER_URL, joinURLS(urls));
        
        LdapContext ctxt = null;
        NamingEnumeration ne = null;        
        try {
            // Create the initial directory context
            ctxt = new InitialLdapContext(env,null);

            // find the DN
            SearchControls sc = new SearchControls(SearchControls.SUBTREE_SCOPE, 1, 0, null, false, false);
            String query = "(userPrincipalName="+LdapUtil.escapeSearchFilterArg(email)+")";
            String base = emailToDomainDN(email);
            String dn = null;
            ne = searchDir(ctxt, base, query, sc);
            if (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                dn = sr.getNameInNamespace();
            }
            
            if (dn == null) throw AccountServiceException.AUTH_FAILED("authn failed");
        
            System.out.println("DN = "+ dn);
            
            //if (true) return;
            
            //change password is a single ldap modify operation
            //that deletes the old password and adds the new password
            ModificationItem[] mods = new ModificationItem[2];

            //Firstly delete the "unicdodePwd" attribute, using the old password
            //Then add the new password,Passwords must be both Unicode and a quoted string
            String oldQuotedPassword = "\"" + oldPassword + "\"";
            byte[] oldUnicodePassword = oldQuotedPassword.getBytes("UTF-16LE");
            String newQuotedPassword = "\"" + newPassword + "\"";
            byte[] newUnicodePassword = newQuotedPassword.getBytes("UTF-16LE");

            mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("unicodePwd", oldUnicodePassword));
            mods[1] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("unicodePwd", newUnicodePassword));

            // Perform the update
            modifyAttributes(ctxt, dn, mods);
        } catch (UnsupportedEncodingException e) {
            throw ServiceException.FAILURE("encoding exception", e);
        } finally {
            closeContext(ctxt);
            closeEnumContext(ne);            
        }
    }
    

    public static void main(String args[]) throws NamingException, ServiceException {
//        changeActiveDirectoryPassword(new String[] {"ldaps://host/"}, "email", "old", "new");

        System.out.println(verifySSHA("{SSHA}igJikWhEzFPLvXp4TNY1NADGOQPNjjWJ","test123"));
        System.out.println(verifySSHA("{SSHA}t14kg+LsEEtb6/3xj+PPYGHv+496XwslfHaxUQ==","welcome123!"));
        
/*
        Date now = new Date();
        String gts = generalizedTime(now);
        System.out.println(now);
        System.out.println(gts);
        Date pnow = generalizedTime(gts);
        System.out.println(pnow);        
        */
    }

    public static void moveChildren(DirContext ctxt, String oldDn, String newDn) throws ServiceException {
        NamingEnumeration ne = null;        
        try {
            // find children under old DN and move them
            SearchControls sc = new SearchControls(SearchControls.ONELEVEL_SCOPE, 0, 0, null, false, false);
            String query = "(objectclass=*)";
            ne = searchDir(ctxt, oldDn, query, sc);
            NameParser ldapParser = ctxt.getNameParser("");            
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                String oldChildDn = sr.getNameInNamespace();
                Name oldChildName = ldapParser.parse(oldChildDn);
                Name newChildName = ldapParser.parse(newDn).add(oldChildName.get(oldChildName.size()-1));
                ctxt.rename(oldChildName, newChildName);
            }
        } catch (NamingException e) {
            ZimbraLog.account.warn("unable to move children", e);            
        } finally {
            closeEnumContext(ne);            
        }
    }

    public static void deleteChildren(DirContext ctxt, String dn) throws ServiceException {
        NamingEnumeration ne = null;        
        try {
            // find children under old DN and remove them
            SearchControls sc = new SearchControls(SearchControls.ONELEVEL_SCOPE, 0, 0, null, false, false);
            String query = "(objectclass=*)";
            ne = searchDir(ctxt, dn, query, sc);
            while (ne.hasMore()) {
                SearchResult sr = (SearchResult) ne.next();
                LdapUtil.unbindEntry(ctxt, sr.getNameInNamespace());
            }
        } catch (NamingException e) {
            ZimbraLog.account.warn("unable to remove children", e);            
        } finally {
            closeEnumContext(ne);            
        }
    }
 
    public static NamingEnumeration<SearchResult> searchDir(DirContext ctxt, String base, String filter, SearchControls cons) throws NamingException {
    	if (base.length() == 0) {
    		return ctxt.search(base, filter, cons);
    	} else {
    	    Name cpName = new CompositeName().add(base);
    	    return ctxt.search(cpName, filter, cons);
    	}
    }
    
    public static void createEntry(DirContext ctxt, String dn, Attributes attrs, String method)
    throws NameAlreadyBoundException, ServiceException {
        Context newCtxt = null;
        try {
        	Name cpName = new CompositeName().add(dn);
            newCtxt = ctxt.createSubcontext(cpName, attrs);
        } catch (NameAlreadyBoundException e) {            
            throw e;
        } catch (InvalidAttributeIdentifierException e) {
            throw AccountServiceException.INVALID_ATTR_NAME(method+" invalid attr name: "+e.getMessage(), e);
        } catch (InvalidAttributeValueException e) {
            throw AccountServiceException.INVALID_ATTR_VALUE(method+" invalid attr value: "+e.getMessage(), e);
        } catch (InvalidAttributesException e) {
            throw ServiceException.INVALID_REQUEST(method+" invalid set of attributes: "+e.getMessage(), e);
        } catch (InvalidNameException e) {
            throw ServiceException.INVALID_REQUEST(method+" invalid name: "+e.getMessage(), e);
        } catch (SchemaViolationException e) {
            throw ServiceException.INVALID_REQUEST(method+" invalid schema change: "+e.getMessage(), e);            
        } catch (NamingException e) {
            throw ServiceException.FAILURE(method, e);
        } finally {
            LdapUtil.closeContext(newCtxt);
        }
    }
    
    public static void renameEntry(DirContext ctxt, String oldDn, String newDn) throws NamingException {
    	Name oldCpName = new CompositeName().add(oldDn);
    	Name newCpName = new CompositeName().add(newDn);
     	ctxt.rename(oldCpName, newCpName);
    }
    
    public static void unbindEntry(DirContext ctxt, String dn) throws NamingException {
    	Name cpName = new CompositeName().add(dn);
     	ctxt.unbind(cpName);
    }
    
    public static Attributes getAttributes(DirContext ctxt, String dn) throws NamingException {
    	Name cpName = new CompositeName().add(dn);
    	return ctxt.getAttributes(cpName);
    }
    
    public static void modifyAttributes(DirContext ctxt, String dn, ModificationItem[] mods) throws NamingException {
    	Name cpName = new CompositeName().add(dn);
        ctxt.modifyAttributes(cpName, mods);
    }
    
    public static void modifyAttributes(DirContext ctxt, String dn, int mod_op, Attributes attrs) throws NamingException {
       	Name cpName = new CompositeName().add(dn);
        ctxt.modifyAttributes(cpName, mod_op, attrs);
    }
    
    //
    // Escape rdn value defined in:
    // http://www.ietf.org/rfc/rfc2253.txt?number=2253
    //
    public static String escapeRDNValue(String rdn) {
        return (String)Rdn.escapeValue(rdn);
    }
    
    public static String unescapeRDNValue(String rdn) {
        return (String)Rdn.unescapeValue(rdn);
    }

 }
