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

import java.util.HashMap;
import java.util.Map;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;

public abstract class ZimbraCustomAuth {
    
    private static Map<String, ZimbraCustomAuth> mHandlers;

    /*
     * Register a custom auth handler.
     * It should be invoked from the init() method of ZimbraExtension.
     */
    public synchronized static void register(String handlerName, ZimbraCustomAuth handler) {
        
        if (mHandlers == null)
            mHandlers = new HashMap<String, ZimbraCustomAuth>();
        else {
            //  sanity check
            ZimbraCustomAuth obj = mHandlers.get(handlerName);
            if (obj != null) {
                ZimbraLog.account.warn("handler name " + handlerName + " is already registered, " +
                                       "registering of " + obj.getClass().getCanonicalName() + " is ignored");
                return;
            }    
        }
        mHandlers.put(handlerName, handler);
    }
    
    public synchronized static ZimbraCustomAuth getHandler(String handlerName) {
        if (mHandlers == null)
            return null;
        else    
            return mHandlers.get(handlerName);
    }
    
    /*
     * Method invoked by the framework to handle authentication requests.
     * A custom auth implementation must implement this abstract method.
     * 
     * Implementor must ensure thread safety during the life span in this method.
     * 
     * @param account: The account object of the principle to be authenticated
     *                 all attributes of the account can be retrieved from this object.
     *                   
     * @param password: Clear-text password, usually entered by user and colected by an UI. 
     * 
     * @return Returning from this function indicating the authentication has succeeded. 
     *  
     * @throws Exception.  If authentication failed, an Exception should be thrown.
     */
    public abstract void authenticate(Account acct, String password) throws Exception;
}
