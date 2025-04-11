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
package org.jivesoftware.wildfire.sasl;

import org.jivesoftware.util.JiveGlobals;

/**
 * This policy will authorize any principal who:
 *
 *  <li> Username of principal matches exactly the username of the JID </li>
 *  <li> The user principal's realm matches exactly the realm of the server.</li>
 * Note that the realm may not match the servername, and in fact for this 
 * policy to be useful it will not match the servername. RFC3920 Section 
 * 6.1, item 7 states that if the principal (authorization entity) is the
 * same as the JID (initiating entity), its MUST NOT provide an authorization
 * identity. In practice however, GSSAPI will provide both. (Note: Ive 
 * not done extensive testing on this)
 *
 * @author Jay Kline
 */
public class StrictAuthorizationPolicy extends AbstractAuthorizationPolicy implements AuthorizationProvider {

    /**
     * Returns true if the principal is explicity authorized to the JID
     *
     * @param username The username requested.
     * @param principal The principal requesting the username.
     * @return true is the user is authorized to be principal
     */
    public boolean authorize(String username, String principal) {
        return (principal.equals(username+"@"+JiveGlobals.getXMLProperty("sasl.realm")));
    }
    
    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "Strict Policy";
    }
    
    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public String description() {
        return "This policy will authorize any principal whos username matches exactly the username of the JID and whos realm matches exactly the realm of the server.";
    }
}