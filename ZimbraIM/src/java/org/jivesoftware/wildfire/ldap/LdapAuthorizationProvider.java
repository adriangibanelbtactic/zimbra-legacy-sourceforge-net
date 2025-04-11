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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.sasl.AbstractAuthorizationProvider;
import org.jivesoftware.wildfire.sasl.AuthorizationProvider;
import org.xmpp.packet.JID;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

/**
 * Provider for authorization using LDAP. Checks if the authenticated 
 * principal is in the user's LDAP object using the authorizeField 
 * from the <tt>wildfire.xml</tt> file. An entry in that file would 
 * look like the following:
 *
 * <pre>
 *   &lt;ldap&gt;
 *     &lt;authorizeField&gt; k5login &lt;/authorizeField&gt;
 *   &lt;/ldap&gt;</pre>
 *
 * This implementation requires that LDAP be configured, obviously.
 *
 * @author Jay Kline
 */
public class LdapAuthorizationProvider extends AbstractAuthorizationProvider implements AuthorizationProvider  {

    private LdapManager manager;
    private String usernameField;
    private String authorizeField;

    public LdapAuthorizationProvider() {
        manager = LdapManager.getInstance();
        usernameField = manager.getUsernameField();
        authorizeField = JiveGlobals.getXMLProperty("ldap.authorizeField", "k5login");
    }
    
    /**
     * Returns if the principal is explicity authorized to the JID, throws 
     * an UnauthorizedException otherwise
     *
     * @param username The username requested.import org.jivesoftware.wildfire.ldap.*;
     * @param principal The principal requesting the username.
     *
     */
    public boolean authorize(String username, String principal) {
        return getAuthorized(username).contains(principal);
    }
    
    /**
     * Returns a String Collection of principals that are authorized to use
     * the named user.
     *
     * @param username the username.
     * @return A String Collection of principals that are authorized.
     */
    public Collection<String> getAuthorized(String username) {
        // Un-escape Node
        username = JID.unescapeNode(username);

        Collection<String> authorized = new ArrayList<String>();
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                usernameField,
                authorizeField
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            Attribute authorizeField_a = attrs.get(manager.getNameField());
            if (authorizeField_a != null) {
                for(Enumeration e = authorizeField_a.getAll(); e.hasMoreElements();) {
                    authorized.add((String)e.nextElement());
                }
            }
            
            return authorized;
        }
        catch (Exception e) {
            // Ignore.
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return authorized;
    }
    
    /**
     * Returns false, this implementation is not writeable.
     *
     * @return False.
     */
    public boolean isWritable() {
        return false;
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principal The principal authorized to use the named user.
     * @throws UnsupportedOperationException If this AuthorizationProvider cannot be updated.
     */
    public void addAuthorized(String username, String principal) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principals The Collection of principals authorized to use the named user.
     */
    public void addAuthorized(String username, Collection<String> principals) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principals The Collection of principals authorized to use the named user.
     * @throws UnsupportedOperationException If this AuthorizationProvider cannot be updated.
     */
    public void setAuthorized(String username, Collection<String> principals) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "LDAP Authorization Provider";
    }
    
    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public String description() {
        return "Provider for authorization using LDAP. Checks if the authenticated principal is in the user's LDAP object using the authorizeField property.";
    } 
}