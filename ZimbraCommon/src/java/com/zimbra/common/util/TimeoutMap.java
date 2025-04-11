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

package com.zimbra.common.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Simple implementation of a <code>HashMap</code> whose elements
 * time out after the specified time period.
 * 
 * @author bburtin
 *
 */
public class TimeoutMap<K, V> implements Map<K, V> {
    
    private long mTimeoutMillis;
    private Map<K, V> mMap = new HashMap<K, V>();
    private Map<Long, K> mTimestamps = new TreeMap<Long, K>();
    private long mLastTimestamp = 0;

    public TimeoutMap(long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Invalid timeout value: " + timeoutMillis);
        }
        mTimeoutMillis = timeoutMillis;
    }
    
    public int size() {
        prune();
        return mMap.size();
    }

    public boolean isEmpty() {
        prune();
        return mMap.isEmpty();
    }

    public boolean containsKey(Object key) {
        prune();
        return mMap.containsKey(key);
    }

    public boolean containsValue(Object value) {
        prune();
        return mMap.containsValue(value);
    }

    public V get(Object key) {
        prune();
        return mMap.get(key);
    }

    public V put(K key, V value) {
        prune();
        mTimestamps.put(getTimestamp(), key);
        return mMap.put(key, value);
    }

    public V remove(Object key) {
        prune();
        return mMap.remove(key);
    }

    public void putAll(Map<? extends K, ? extends V> t) {
        prune();
        for (K key : t.keySet())
            mTimestamps.put(getTimestamp(), key);
        mMap.putAll(t);
    }

    public void clear() {
        mTimestamps.clear();
        mMap.clear();
    }

    public Set<K> keySet() {
        prune();
        return mMap.keySet();
    }

    public Collection<V> values() {
        prune();
        return mMap.values();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        prune();
        return mMap.entrySet();
    }
    
    /**
     * Removes all entries that have timed out.
     */
    private void prune() {
        long now = System.currentTimeMillis();
        Iterator<Long> i = mTimestamps.keySet().iterator();
        while (i.hasNext()) {
            Long timestamp = i.next();
            if (now - timestamp.longValue() > mTimeoutMillis) {
                Object key = mTimestamps.get(timestamp);
                mMap.remove(key);
                i.remove();
            } else {
                // The timestamp map is sorted, so we know all other timestamps
                // are later
                return;
            }
        }
    }
    
    /**
     * Returns the current system timestamp, possibly adjusted by a few milliseconds.
     * Used to ensure that the timestamp TreeMap contains unique values.
     */
    private Long getTimestamp() {
        long now = System.currentTimeMillis();
        if (now <= mLastTimestamp) {
            now = mLastTimestamp + 1;
        }
        mLastTimestamp = now;
        return new Long(mLastTimestamp);
    }
}
