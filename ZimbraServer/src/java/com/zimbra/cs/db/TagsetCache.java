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

package com.zimbra.cs.db;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.zimbra.cs.util.StringUtil;

class TagsetCache {
    private Set mTagsets = new HashSet();
    private String mName;
    
    TagsetCache(String name) {
        mName = name;
    }
    
    int size() {
        return mTagsets.size();
    }

    void addTagset(long tagset) {
        addTagset(new Long(tagset));
    }
    
    void addTagset(Long tagset) {
        if (tagset == null) {
            throw new IllegalArgumentException("tagset cannot be null");
        }
        mTagsets.add(tagset);
    }

    void addTagsets(Collection /* Long */ tagsets) {
        // Iterate the collection instead of just calling addAll(), to
        // make sure all the values passed in are Longs.
        Iterator i = tagsets.iterator();
        while (i.hasNext()) {
            addTagset((Long) i.next());
        }
    }

    Set getMatchingTagsets(long mask, long value) {
        Set matches = new HashSet();
        for (Iterator it = mTagsets.iterator(); it.hasNext(); ) {
            Long tagset = (Long) it.next();
            if ((tagset.longValue() & mask) == value)
                matches.add(tagset);
        }
        return matches;
    }

    Set getAllTagsets() {
        return new HashSet(mTagsets);
    }

    /**
     * Applies a bitmask to all the tagsets in the collection, and adds the
     * resulting new tagsets. We do this when we know that the tag changed for one or
     * more items, but we don't have references to the items themselves.
     * <p>
     * The end result is that we add a bunch of new tagsets, some of which
     * may not actually exist for any items.  This is ok, since searches on
     * the bogus tagsets will never return data.  When the cache times out,
     * the bogus tagsets are removed.
     */
    void applyMask(long mask, boolean add) {
        Iterator i = mTagsets.iterator();
        Set newTagsets = new HashSet();
        while (i.hasNext()) {
            long tags = ((Long) i.next()).longValue();
            long newTags = add ? tags | mask : tags & ~mask;
            newTagsets.add(new Long(newTags));
        }
        addTagsets(newTagsets);
    }
    
    public String toString() {
        return "[TagsetCache " + mName + " (" + StringUtil.join(",", mTagsets) + ")]";
    }
}
