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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A simple counter that maintains the count of unique values passed into the
 * {@link #increment} and {@link #decrement} methods.
 * 
 * @author bburtin
 */
public class ValueCounter {

    private Map /* <Object, Integer> */ mValues = new HashMap();
    
    public void increment(Object value) {
        increment(value, 1);
    }
    
    public void decrement(Object value) {
        increment(value, -1);
    }
    
    public void increment(Object value, int delta) {
        Integer count = (Integer) mValues.get(value);
        if (count == null) {
            count = new Integer(delta);
        } else {
            count = new Integer(count.intValue() + delta);
        }
        mValues.put(value, count);
    }
    
    public int getCount(Object value) {
        Integer count = (Integer) mValues.get(value);
        if (count == null) {
            return 0;
        }
        return count.intValue();
    }
    
    public Iterator iterator() {
        return mValues.keySet().iterator();
    }
    
    public int size() {
        return mValues.size();
    }
    
    public int getTotal() {
        int total = 0;
        Iterator i = iterator();
        while (i.hasNext()) {
            total = total + getCount(i.next());
        }
        return total;
    }
    
    public void clear() {
        mValues.clear();
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        Iterator i = iterator();
        while (i.hasNext()) {
            if (buf.length() != 0) {
                buf.append(", ");
            }
            Object value = i.next();
            buf.append(value + ": " + getCount(value));
        }
        return buf.toString();
    }
}
