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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.zclient;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;

import java.util.List;
import java.util.Map;

public class ZAuthResult {
    private String mAuthToken;
    private long mExpires;
    private long mLifetime;
    private String mRefer;
    private Map<String, List<String>> mAttrs;
    private Map<String, List<String>> mPrefs;

    public ZAuthResult(Element e) throws ServiceException {
        mAuthToken = e.getElement(AccountConstants.E_AUTH_TOKEN).getText();
        mLifetime = e.getAttributeLong(AccountConstants.E_LIFETIME);
        mExpires = System.currentTimeMillis() + mLifetime;
        Element re = e.getOptionalElement(AccountConstants.E_REFERRAL);
        if (re != null) mRefer = re.getText();
        mAttrs = ZGetInfoResult.getMap(e, AccountConstants.E_ATTRS, AccountConstants.E_ATTR);
        mPrefs = ZGetInfoResult.getMap(e, AccountConstants.E_PREFS, AccountConstants.E_PREF);
    }

    public String getAuthToken() {
        return mAuthToken;
    }
    
    public long getExpires() {
        return mExpires;
    }
    
    public long getLifetime() {
        return mLifetime;
    }
    
    public String getRefer() {
        return mRefer;
    }

    public Map<String, List<String>> getAttrs() {
        return mAttrs;
    }

    public Map<String, List<String>> getPrefs() {
        return mPrefs;
    }
}
