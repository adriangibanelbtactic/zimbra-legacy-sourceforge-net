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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.common.localconfig;

import java.util.HashMap;
import java.util.Map;

public class KnownKey {

    private static final Map<String, KnownKey> mKnownKeys = new HashMap<String, KnownKey>();

    static {
        // Since all the known keys are actually defined
        // in another class, we need to make sure that
        // class' static initializer is run.
        LC.init();
    }

    static String[] getAll() {
        return (String[])mKnownKeys.keySet().toArray(new String[0]);
    }
    
    static boolean isKnown(String key) {
        return mKnownKeys.containsKey(key);
    }
    
    static String getDoc(String key) {
        KnownKey kk = mKnownKeys.get(key);
        if (kk == null) {
            return null;
        }
        return kk.mDoc;
    }
    
    static String getDefaultValue(String key) {
        KnownKey kk = mKnownKeys.get(key);
        if (kk == null) {
            return null;
        }
        return kk.mDefaultValue;
    }
    
    static void expandAll(LocalConfig lc) throws ConfigException {
        String[] keys = KnownKey.getAll();
        for (String key : keys) {
        	KnownKey kk = mKnownKeys.get(key);
        	kk.expand(lc);
        }
    }
    
    static String getValue(String key) throws ConfigException {
        KnownKey kk = mKnownKeys.get(key);
        if (kk == null) {
            return null;
        }
        if (kk.mValue == null) {
        	kk.expand(LocalConfig.getInstance());
        }
        return kk.mValue;
    }
    
    static boolean needForceToEdit(String key) {
        KnownKey kk = mKnownKeys.get(key);
        if (kk == null) {
            return false;
        }
        return kk.mForceToEdit;
    }
    
    /*
     * Instance stuff.
     */
    
    private final String mKey;
    private String mDoc;
    private String mDefaultValue;
    private String mValue; //cached value after expansion
    private boolean mForceToEdit;
    
    /**
     * The only public method here.  If you have a KnownKey object, this
     * is a shortcut to get it's value.
     * 
     * @see LC.get()
     */
    public String value() {
        return LC.get(mKey);
    }

    public boolean booleanValue() {
        String s = LC.get(mKey);
        if (s == null || s.length() == 0) {
            throw new IllegalStateException("'" + mKey + "' is not defined in LocalConfig");
        }
        return Boolean.valueOf(s).booleanValue();
    }
    
    public int intValue() {
        String s = LC.get(mKey);
        if (s == null || s.length() == 0) {
            throw new IllegalStateException("'" + mKey + "' is not defined in LocalConfig");
        }
        return Integer.parseInt(s);
    }
    
    /**
     * Returns the value of this KnownKey as an int, but forces it to be within
     * the range of minValue <= RETURN <= maxValue
     * 
     * @param minValue
     * @param maxValue
     */
    public int intValueWithinRange(int minValue, int maxValue) {
        int toRet = intValue();
        if (toRet < minValue) 
            toRet = minValue;
        if (toRet > maxValue)
            toRet = maxValue;
        return toRet;
    }
    
    public long longValue() {
        String s = LC.get(mKey);
        if (s == null || s.length() == 0) {
            throw new IllegalStateException("'" + mKey + "' is not defined in LocalConfig");
        }
        return Long.parseLong(s);
    }
    
    /**
     * Returns the value of this KnownKey as a long, but forces it to be within
     * the range of minValue <= RETURN <= maxValue
     * 
     * @param minValue
     * @param maxValue
     */
    public long longValueWithinRange(long minValue, long maxValue) {
        long toRet = longValue();
        if (toRet < minValue) 
            toRet = minValue;
        if (toRet > maxValue)
            toRet = maxValue;
        return toRet;
    }
    
    public String key() {
    	return mKey;   
    }

    public KnownKey(String key, String defaultValue, String doc) {
        mKey = key;
        if (mKnownKeys.containsKey(key)) {
            Logging.warn("programming error - known key added more than once: " + key);
        }
        setDefault(defaultValue);
        mDoc = doc;
        mKnownKeys.put(key, this);
    }

    public KnownKey(String key) 
    {
        mKey = key;
        if (mKnownKeys.containsKey(key)) {
            Logging.warn("programming error - known key added more than once: " + key);
        }
        mKnownKeys.put(key, this);
    }
    
    public void setDoc(String doc) {
        mDoc = doc;
    }
    
    public void setDefault(String defaultValue) {
        mDefaultValue = defaultValue;
        mValue = null;
    }
    
    public void setForceToEdit(boolean value) {
        mForceToEdit = value;
    }
    
    private void expand(LocalConfig lc) throws ConfigException {
    	try {
    		mValue = lc.expand(mKey, mDefaultValue);
    	} catch (ConfigException x) {
    		Logging.error("Can't expand config key " + mKey + "=" + mDefaultValue, x);
    		throw x;
    	}
    }
}
