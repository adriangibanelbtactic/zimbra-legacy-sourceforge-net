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

package com.zimbra.cs.util;

/**
 * @author bburtin
 */
public class EmailUtil
{
    /**
     * Splits email address of the form "foo@bar.com" into "foo" and "bar.com".
     * Do NOT use this method in RFC/protocol validation. Use only for simple
     * sanity split into two strings on either side of the '@'. RFC822 allows
     * local-part to contain '@' in quotes etc, and we do not deal with that
     * here (eg.: foo"@"bar@bar.com).
     * 
     * @return a 2-element array. Element 0 is local-part and element 1 is
     *         domain. Returns null if either local-part or domain were not
     *         found.
     */
    public static String[] getLocalPartAndDomain(String address) {
        int at = address.indexOf('@');
        if (at == -1) {
            return null;
        }

        String localPart = address.substring(0, at);
        if (localPart.length() == 0) {
            return null;
        }

        String domain = address.substring(at + 1, address.length());
        if (domain.length() == 0) {
            return null;
        }

        return new String[] { localPart, domain };
    }
}
