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

import com.zimbra.common.util.StringUtil;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ZSoapSB {
    private StringBuilder mSb;
    private boolean mFirst;
    private boolean mInArray;
    private String mEol = "\n";
    
    public ZSoapSB() {
        mSb = new StringBuilder();
    }
    
    public ZSoapSB(String eol) {
        mSb = new StringBuilder();
        mEol = eol;
    }
    
    public ZSoapSB beginStruct() {
        mSb.append("{").append(mEol);        
        mFirst = true;
        return this;
    }
    
    public ZSoapSB beginStruct(String name) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": {").append(mEol);        
        mFirst = true;
        return this;
    }

    public ZSoapSB endStruct() {
        mSb.append(mEol).append("}");
        mFirst = false;
        return this;
    }
    
    private ZSoapSB beginArray(String name) {
        checkFirst();        
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": [").append(mEol);
        mFirst = true;
        mInArray = true;
        return this;
    }

    private ZSoapSB endArray() {
        mSb.append(mEol).append("]");
        mFirst = false;
        mInArray = false;
        return this;
    }

    void checkFirst() {
        if (mFirst) {
            mFirst = false;
        } else {
            mSb.append(",").append(mEol);
        }
    }
    
    public static String quote(String value) {
        if (value == null) return "null";
        return '"' + StringUtil.jsEncode(value) + '"';
    }
    
    public ZSoapSB add(String name, List<? extends Object> list, boolean encode, boolean showEmpty) {
        if (!showEmpty && (list == null || list.size() == 0)) 
            return this;
        
        beginArray(name);
        for (Object o : list) {
            addArrayElement(o.toString(), encode);
        }
        endArray();
        return this;
    }

    public ZSoapSB add(String name, String[] list, boolean encode, boolean showEmpty) {
        if (!showEmpty && (list == null || list.length == 0)) 
            return this;
        
        beginArray(name);
        for (String s : list) {
            addArrayElement(s, encode);
        }
        endArray();
        return this;
    }
    
    public ZSoapSB add(String name, String value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(quote(value));
        return this;
    }
    
    private ZSoapSB addArrayElement(String value, boolean encode) {
        checkFirst();
        mSb.append(encode ? quote(StringUtil.jsEncode(value)) : value);
        return this;
    }
    
    public ZSoapSB addStruct(String name, String value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(value);        
        return this;
    }

    public ZSoapSB add(String name, boolean value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(value);
        return this;
    }

    public ZSoapSB add(String name, long value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(value);
        return this;        
    }

    public ZSoapSB add(String name, int value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(value);
        return this;        
    }

    public ZSoapSB add(String name, float value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncodeKey(name)).append(": ").append(value);
        return this;        
    }

    public ZSoapSB addDate(String name, long value) {
        checkFirst();
        mSb.append(" ").append(StringUtil.jsEncode(name)).append(": ").append(value + " /* " + new Date(value) + " */");
        return this;        
    }

//    ZSoapSB nl() { mSb.append("\n"); return this; }

    public String toString() {
        return mSb.toString();
    }

    public void add(String name, Map<String, List<String>> attrs) {
        beginStruct(name);
        for (Map.Entry<String, List<String>> entry : attrs.entrySet()) {
            add(entry.getKey(), entry.getValue(), true, true);
        }
        endStruct();
    }
}
