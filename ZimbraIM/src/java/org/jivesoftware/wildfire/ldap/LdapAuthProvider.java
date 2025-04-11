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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.ldap;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.auth.AuthProvider;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

import javax.naming.CommunicationException;

/**
 * Implementation of auth provider interface for LDAP authentication service plug-in.
 * Only plaintext authentication is currently supported.<p>
 *
 * Optionally, an authentication cache can be enabled. When enabled, a hashed version
 * of the user's password is cached for a variable length of time (2 hours by default).
 * This can decrease load on the directory and preserve some level of service even
 * when the directory becomes unavailable for a period of time.<ul>
 *
 *  <li><tt>ldap.authCache.enabled</tt> -- true to enable the auth cache.</li>
 *  <li><tt>ldap.authCache.size</tt> -- size in bytes of the auth cache. If property is
 *      not set, the default value is 524288 (512 K).
 *  <li><tt>ldap.authCache.maxLifetime</tt> -- maximum amount of time a hashed password
 *      can be cached in milleseconds. If property is not set, the default value is
 *      7200000 (2 hours).
 * </tt>
 *
 * @author Matt Tucker
 */
public class LdapAuthProvider implements AuthProvider {

    private LdapManager manager;
    private Cache authCache = null;

    public LdapAuthProvider() {
        manager = LdapManager.getInstance();
        if (JiveGlobals.getXMLProperty("ldap.authCache.enabled", false)) {
            int maxSize = JiveGlobals.getXMLProperty("ldap.authCache.size", 512*1024);
            long maxLifetime = (long)JiveGlobals.getXMLProperty("ldap.authCache.maxLifetime",
                    (int)JiveConstants.HOUR * 2);
            String cacheName = "LDAP Authentication";
            CacheManager.initializeCache(cacheName, "ldap", maxSize, maxLifetime);
            authCache = CacheManager.getCache(cacheName);
        }
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null || "".equals(password.trim())) {
            throw new UnauthorizedException();
        }

        // Un-escape username.
        username = JID.unescapeNode(username);

        // If cache is enabled, see if the auth is in cache.
        if (authCache != null && authCache.containsKey(username)) {
            String hash = (String)authCache.get(username);
            if (StringUtils.hash(password).equals(hash)) {
                return;
            }
        }

        String userDN;
        try {
            // The username by itself won't help us much with LDAP since we
            // need a fully qualified dn. We could make the assumption that
            // the baseDN would always be the location of user profiles. For
            // example if the baseDN was set to "ou=People, o=jivesoftare, o=com"
            // then we would be able to directly load users from that node
            // of the LDAP tree. However, it's a poor assumption that only a
            // flat structure will be used. Therefore, we search all sub-trees
            // of the baseDN for the username (assuming the user has not disabled
            // sub-tree searching). So, if the baseDN is set to
            // "o=jivesoftware, o=com" then a search will include the "People"
            // node as well all the others under the base.
            userDN = manager.findUserDN(username);

            // See if the user authenticates.
            if (!manager.checkAuthentication(userDN, password)) {
                throw new UnauthorizedException("Username and password don't match");
            }
        }
        catch (CommunicationException e) {
            // Log error here since it will be wrapped with an UnauthorizedException that
            // is never logged
            Log.error("Error connecting to LDAP server", e);
            throw new UnauthorizedException(e);
        }
        catch (Exception e) {
            throw new UnauthorizedException(e);
        }

        // If cache is enabled, add the item to cache.
        if (authCache != null) {
            authCache.put(username, StringUtils.hash(password));
        }
    }

    public void authenticate(String username, String token, String digest) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Digest authentication not currently supported.");
    }

    public String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}