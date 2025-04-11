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
package org.jivesoftware.database;

import org.jivesoftware.util.JiveBeanInfo;

/**
 * BeanInfo class for the DefaultConnectionProvider class.
 *
 * @author Jive Software
 */
public class DefaultConnectionProviderBeanInfo extends JiveBeanInfo {

    public static final String[] PROPERTY_NAMES = {
        "driver",
        "serverURL",
        "username",
        "password",
        "minConnections",
        "maxConnections",
        "connectionTimeout"
    };

    public DefaultConnectionProviderBeanInfo() {
        super();
    }

    public Class getBeanClass() {
        return org.jivesoftware.database.DefaultConnectionProvider.class;
    }

    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    public String getName() {
        return "DefaultConnectionProvider";
    }
}