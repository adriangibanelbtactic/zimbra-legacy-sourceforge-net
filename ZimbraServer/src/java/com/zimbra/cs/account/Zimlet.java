/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.2
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.2 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimlets
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

import java.util.Map;

import com.zimbra.cs.zimlet.ZimletHandler;
import com.zimbra.cs.zimlet.ZimletUtil;

public class Zimlet extends NamedEntry {
	protected Zimlet(String name, String id, Map<String, Object> attrs) {
        super(name, id, attrs, null);
    }

    public boolean isEnabled() {
        return getBooleanAttr(Provisioning.A_zimbraZimletEnabled, false);
    }
    
    public String getPriority() {
        return getAttr(Provisioning.A_zimbraZimletPriority);
    }
    
    public boolean isExtension() {
        return getBooleanAttr(Provisioning.A_zimbraZimletIsExtension, false);
    }

    public String getType() {
        return getAttr(Provisioning.A_cn);
    }
    
    public String getDescription() {
        return getAttr(Provisioning.A_zimbraZimletDescription);
    }
    
    public boolean isIndexingEnabled() {
        return getBooleanAttr(Provisioning.A_zimbraZimletIndexingEnabled, false);
    }
    
    public String getHandlerClassName() {
        return getAttr(Provisioning.A_zimbraZimletHandlerClass);
    }
    
    public ZimletHandler getHandler() {
        return ZimletUtil.getHandler(getName());
    }
    
    public String getHandlerConfig() {
        return getAttr(Provisioning.A_zimbraZimletHandlerConfig);
    }

    public String getServerIndexRegex() {
        return getAttr(Provisioning.A_zimbraZimletServerIndexRegex);
    }
}
