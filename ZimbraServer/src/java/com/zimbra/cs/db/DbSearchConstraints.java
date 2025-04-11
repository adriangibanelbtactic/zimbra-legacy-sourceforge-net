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
package com.zimbra.cs.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.common.util.ListUtil;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;

/**
 * @author tim
 *
 * A class which encapsulates all of the constraints we can do on a mailbox search
 * 
 * "required" entries must be set, or you won't get what you want
 * 
 * "optional" entries are ignored if the default value is passed 
 */
public class DbSearchConstraints implements DbSearchConstraintsNode, Cloneable {

    public DbSearchConstraintsNode.NodeType getNodeType() { return DbSearchConstraintsNode.NodeType.LEAF; }
    public Iterable<DbSearchConstraintsNode> getSubNodes() { return null; }
    public DbSearchConstraints getSearchConstraints() { return this; }

    public static class StringRange {
        public boolean negated = false;
        public String lowest = null;  public boolean lowestEqual = false;
        public String highest = null;  public boolean highestEqual = false;
        
        public boolean equals(Object o) {
            DbSearchConstraints.StringRange other = (DbSearchConstraints.StringRange) o;
            return (
                        StringUtil.equal(other.lowest, lowest) && other.lowestEqual == lowestEqual &&
                        StringUtil.equal(other.highest, highest) && other.highestEqual == highestEqual &&
                        other.negated == negated
            );
        }
        
        public String toString() {
            StringBuilder retVal = new StringBuilder();

            if (negated) {
                retVal.append("NOT (");
            }

            if (lowest != null) {
                retVal.append("\">");
                if (lowestEqual)
                    retVal.append('=');
                retVal.append(lowest);
                retVal.append("\" ");
            }

            if (highest != null) {
                retVal.append("\"<");
                if (highestEqual) 
                    retVal.append('=');
                retVal.append(highest);
                retVal.append("\" ");
            }
            if (negated) {
                retVal.append(")");
            }

            return retVal.toString();
        }

        boolean isValid()  { return true; }
    }
    
    public static class NumericRange {
        public boolean negated = false;
        public long lowest = -1;  public boolean lowestEqual = false;
        public long highest = -1;  public boolean highestEqual = false;
        
        public boolean equals(Object o) {
            DbSearchConstraints.NumericRange other = (DbSearchConstraints.NumericRange) o;
            return (
                        (other.negated == negated) && (other.lowest == lowest) && (other.highest == highest) &&
                        (other.lowestEqual == lowestEqual) && (other.highestEqual == highestEqual) 
            );
        }

        boolean isValid()  { return lowest > 0 || highest > 0; }
        
        public String toString() {
            StringBuilder retVal = new StringBuilder();

            if (negated) {
                retVal.append("NOT (");
            }

            if (lowest > -1) {
                retVal.append(">");
                if (lowestEqual)
                    retVal.append('=');
                retVal.append(lowest);
                retVal.append(' ');
            }

            if (highest > -1) {
                retVal.append("<");
                if (highestEqual)
                    retVal.append('=');
                retVal.append(highest);
                retVal.append(' ');
            }
            if (negated) {
                retVal.append(")");
            }

            return retVal.toString();
        }
    }
    
    //
    // these should all be moved OUT of DbSearchConstraints and passed as parameters 
    // to the DbMailItem.search() function!
    //
    public Mailbox mailbox;                          /* required */
    public byte sort;                                /* required */
    public int offset = -1;                 /* optional */
    public int limit = -1;                  /* optional */

    //
    // When we COMBINE the operations, we'll need to track some state values
    // for example "no results" 
    //
    public boolean noResults = false;

    //
    // These are the main constraints 
    //
    public Set<Tag> tags = null; /* optional - SPECIAL CASE -- ALL listed tags must be present.  NULL IS DIFFERENT THAN EMPTY SET!!! */
    public Set<Tag> excludeTags = new HashSet<Tag>(); /* optional - ALL listed tags must be NOT present*/

    public Boolean hasTags = null;                   /* optional */

    public Set<Folder> folders = new HashSet<Folder>();        /* optional - ANY of these folders are OK */
    public Set<Folder> excludeFolders = new HashSet<Folder>(); /* optional - ALL listed folders not allowed */

    public Set<ItemId> remoteFolders = new HashSet<ItemId>();         /* optional */
    public Set<ItemId> excludeRemoteFolders = new HashSet<ItemId>();  /* optional */
    
    public int convId = 0;                          /* optional */
    public Set<Integer> prohibitedConvIds = new HashSet<Integer>();          /* optional */

    public Set<Integer> itemIds = new HashSet<Integer>();                             /* optional - ANY of these itemIDs are OK.*/
    public Set<Integer> prohibitedItemIds = new HashSet<Integer>(); /* optional - ALL of these itemIDs are excluded*/

    public Set<Integer> indexIds = new HashSet<Integer>();                   /* optional - ANY of these indexIDs are OK.  */

    public Set<Byte> types = new HashSet<Byte>();                         /* optional - ANY of these types are OK.  */
    public Set<Byte> excludeTypes = new HashSet<Byte>();                  /* optional - ALL of these types are excluded */

    public Collection<NumericRange> dates = new ArrayList<NumericRange>();    /* optional */
    public Collection<NumericRange> modified = new ArrayList<NumericRange>(); /* optional */
    public Collection<NumericRange> sizes = new ArrayList<NumericRange>();    /* optional */
    public Collection<StringRange> subjectRanges = new ArrayList<StringRange>(); /* optional */
    public Collection<StringRange> senderRanges = new ArrayList<StringRange>(); /* optional */
    

    public boolean isSimpleSingleFolderMessageQuery() {
        boolean typeIsMsg = false;
        if (types.size() == 1) {
            for (Byte type : types) {
                if (type == MailItem.TYPE_MESSAGE)
                    typeIsMsg = true;
            }
        }
        return
            folders.size() == 1 && excludeFolders.isEmpty() &&
            typeIsMsg &&
            (tagConstraints == null ||
             (tagConstraints.searchFlagsets == null &&
              tagConstraints.searchTagsets == null &&
              tagConstraints.unread == null)) &&
            convId == 0 && prohibitedConvIds.isEmpty() &&
            itemIds.isEmpty() && prohibitedItemIds.isEmpty() &&
            indexIds.isEmpty() &&
            dates.isEmpty() &&
            modified.isEmpty() &&
            sizes.isEmpty() &&
            subjectRanges.isEmpty() &&
            senderRanges.isEmpty() &&
            remoteFolders.isEmpty() && excludeRemoteFolders.isEmpty();
    }

    public Object clone() throws CloneNotSupportedException {
        DbSearchConstraints toRet = (DbSearchConstraints)super.clone();

        if (tags != null) {
            toRet.tags = new HashSet<Tag>();          
            toRet.tags.addAll(tags);
        }

        toRet.excludeTags = new HashSet<Tag>();   toRet.excludeTags.addAll(excludeTags);

        toRet.folders = new HashSet<Folder>();        toRet.folders.addAll(folders);
        toRet.excludeFolders = new HashSet<Folder>(); toRet.excludeFolders.addAll(excludeFolders);

        toRet.remoteFolders = new HashSet<ItemId>();         toRet.remoteFolders.addAll(remoteFolders);
        toRet.excludeRemoteFolders = new HashSet<ItemId>();  toRet.excludeRemoteFolders.addAll(excludeRemoteFolders);
        
        toRet.convId = convId;
        toRet.prohibitedConvIds = new HashSet<Integer>(); toRet.prohibitedConvIds.addAll(prohibitedConvIds);

        toRet.itemIds = new HashSet<Integer>();  toRet.itemIds.addAll(itemIds);
        toRet.prohibitedItemIds = new HashSet<Integer>(); toRet.prohibitedItemIds.addAll(prohibitedItemIds);

        toRet.indexIds = new HashSet<Integer>(); toRet.indexIds.addAll(indexIds);

        toRet.types = new HashSet<Byte>(); toRet.types.addAll(types);
        toRet.excludeTypes = new HashSet<Byte>(); toRet.excludeTypes.addAll(excludeTypes);

        toRet.dates = new HashSet<NumericRange>(); toRet.dates.addAll(dates);
        toRet.modified = new HashSet<NumericRange>(); toRet.modified.addAll(modified);
        toRet.sizes = new HashSet<NumericRange>(); toRet.sizes.addAll(sizes);
        toRet.subjectRanges = new HashSet<StringRange>(); toRet.subjectRanges.addAll(subjectRanges);
        toRet.senderRanges = new HashSet<StringRange>(); toRet.senderRanges.addAll(senderRanges);

        return toRet;
    }


    DbMailItem.TagConstraints tagConstraints;


    private static abstract class Printer<T> {
        void run(StringBuilder str, Collection<T> collect, String intro) {
            if (!ListUtil.isEmpty(collect)) {
                str.append(intro).append(":(");
                boolean atFirst = true;
                for (T elt: collect) {
                    if (!atFirst) 
                        str.append(", ");
                    printOne(str, elt);
                    atFirst = false;
                }
                str.append(")");
            }
        }

        abstract void printOne(StringBuilder s, T t);
    }
    
    private static class FolderPrinter {
        void run(StringBuilder str, boolean truthiness, Collection<Folder> collect) {
            if (!ListUtil.isEmpty(collect)) {
                str.append("(");
                boolean atFirst = true;
                for (Folder f: collect) {
                    if (!atFirst) 
                        str.append(" ");
                    
                    if (!truthiness)
                        str.append("-");

                    if (f instanceof Mountpoint) {
                        str.append("INID:");
                        Mountpoint mpt = (Mountpoint)f;
                        str.append(mpt.getRemoteId());
                    } else {
                        str.append("IN:").append(f.getName());
                    }
                    atFirst = false;
                }
                str.append(")");
            }
        }
    }
    
    private static class RemoteFolderPrinter {
        void run(StringBuilder str, boolean truthiness, Collection<ItemId> collect) {
            if (!ListUtil.isEmpty(collect)) {
                str.append("(");
                boolean atFirst = true;
                for (ItemId id: collect) {
                    if (!atFirst) 
                        str.append(" ");

                    if (!truthiness)
                    	str.append("-");
                    str.append("INID:\"").append(id.toString()).append("\"");
                    atFirst = false;
                }
                str.append(") ");
            }
        }
    }
    
    static class ObjectPrinter<T extends Object> extends Printer<T> {
        void printOne(StringBuilder s, T t) {
            s.append(t.toString());
        }
    }


    public String toString()
    {
        StringBuilder retVal = new StringBuilder("");

        //
        // all this pain could have been eliminated with a simple preprocessor macro...fucking java...
        //
        Printer<Tag> tp = new Printer<Tag>()         { void printOne(StringBuilder s, Tag t)     { s.append(t.getName()); } };
        Printer<Integer> ip = new Printer<Integer>() { void printOne(StringBuilder s, Integer i) { s.append(i); } };
        Printer<Byte> bp = new Printer<Byte>()       { void printOne(StringBuilder s, Byte b)    { s.append(b); } };
        
        FolderPrinter fp = new FolderPrinter();
        RemoteFolderPrinter rfp = new RemoteFolderPrinter();

        // tags
        tp.run(retVal, tags, "TAG");
        tp.run(retVal, excludeTags, "-TAG");

        // hasTags?
        if (hasTags != null) {
            if (hasTags) {
                retVal.append("HAS_TAG ");
            } else {
                retVal.append("-HAS_TAG ");
            }
        }

        // folders
        fp.run(retVal, true, folders);
        fp.run(retVal, false, excludeFolders);

        // remote folders
        rfp.run(retVal, true, remoteFolders);
        rfp.run(retVal, false, excludeRemoteFolders);
        
        // convId
        if (convId != 0) {
            retVal.append("CONV:(").append(convId).append(") ");
        }
        ip.run(retVal, prohibitedConvIds, "-CONVID");
        ip.run(retVal, prohibitedConvIds, "-CONVID");

        // itemId
        ip.run(retVal, itemIds, "ITEM");
        ip.run(retVal, prohibitedItemIds, "-ITEM");

        // indexId
        ip.run(retVal, indexIds, "INDEXID");

        // type
        bp.run(retVal, types, "TYPE"); 
        bp.run(retVal, excludeTypes, "-TYPE"); 

        if (!dates.isEmpty())
            new ObjectPrinter<NumericRange>().run(retVal, dates, "DATE"); 

        if (!modified.isEmpty())
            new ObjectPrinter<NumericRange>().run(retVal, modified, "MOD") ;

        if (!sizes.isEmpty()) 
            new ObjectPrinter<NumericRange>().run(retVal, sizes, "SIZE");
        
        if (!subjectRanges.isEmpty())
            new ObjectPrinter<StringRange>().run(retVal, subjectRanges, "SUBJECT");
        
        if (!senderRanges.isEmpty())
            new ObjectPrinter<StringRange>().run(retVal, senderRanges, "FROM");

        return retVal.toString();
    }

    /**
     * @author tim
     * 
     * NOT general-purpose, makes assumptions about when-to-copy and
     * that NULL != empty-set that are not something one would generally want 
     *
     * @param <T>
     */
    static class MySetUtil<T> {
        Set<T> clone(Set<T> s) {
            Set<T> toRet = null;
            if (s != null) {
                toRet = new HashSet<T>();
                toRet.addAll(s);
            }
            return toRet;
        }

        Set<T> intersectIfNonempty(Set<T> lhs, Set<T> rhs) {
            assert(lhs != null && rhs != null);
            
            if (lhs.size() == 0)
                return clone(rhs);
            if (rhs.size() == 0)
                return lhs;
            

            if (rhs == null) {
                return lhs;
            } else if (lhs == null) {
                return clone(rhs);
            } else {
                Set<T> newSet = new HashSet<T>();
                for (T t : rhs) {
                    if (lhs.contains(t))
                        newSet.add(t);
                }
                return newSet;
            }
        }

        Set<T> union(Set<T> lhs, Set<T> rhs) {
            if (rhs == null) {
                return lhs;
            } else if (lhs == null) {
                return clone(rhs);
            } else {
                lhs.addAll(rhs);
                return lhs;
            }
        }
    }

    /**
     * this = this AND other
     * 
     * @param other
     */
    public void andConstraints(DbSearchConstraints other) 
    {
        if (noResults || other.noResults) {
            noResults = true;
            return;
        }

        MySetUtil<Tag> tu = new MySetUtil<Tag>();
        MySetUtil<Folder> fu = new MySetUtil<Folder>();
        MySetUtil<Integer> iu = new MySetUtil<Integer>();
        MySetUtil<Byte> bu = new MySetUtil<Byte>();

        // tags
        tags = tu.union(tags, other.tags);
        excludeTags = tu.union(excludeTags, other.excludeTags);
        
        // bug 2426
        if (tags != null && excludeTags != null)
            for (Tag t : tags) {
                if (excludeTags.contains(t)) {
                    noResults = true;
                    return;
                }
            }
            
        // has tags 
        if (hasTags == null)
            hasTags = other.hasTags;
        else if (other.hasTags != null) {
            if (!hasTags.equals(other.hasTags)) {
                noResults = true;
                ZimbraLog.index.debug("Adding a HAS_NO_TAGS constraint to a HAS_TAGS one, this is a NO_RESULTS result");
                return;
            }
        }

        // folders
        //
        // these we have to intersect:
        //
        //   Folder{A or B or C} AND Folder{B or C or D} --> Folder{IN-BOTH}
        {
            // if both sets are empty going in, then an empty set means 
            // "no constraint"....on the other hand if either set is nonempty
            // going in, then an empty set coming out means "no results".
            // ugly.  Should modify this so folders=null means "no constraint" and
            // folders=[] means "no results".  TODO...
            if (folders.size() >  0 ||  other.folders.size() > 0) {
                folders = fu.intersectIfNonempty(folders, other.folders);
                if (folders.size() == 0)
                    noResults = true;
            }
        }
        
        
        excludeFolders = fu.union(excludeFolders, other.excludeFolders);

        // convId
        if (other.convId != 0) {
            if (convId != 0) { 
                if (convId != other.convId) {
                    ZimbraLog.index.debug("ANDING a constraint with incompatible convIds, this is a NO_RESULTS constraint now");
                    noResults = true;
                }
            } else {
                convId = other.convId;
            }
        }
        prohibitedConvIds = iu.union(prohibitedConvIds, other.prohibitedConvIds);


        // itemId

        // these we have to intersect:
        //
        //   Item{A or B or C} AND Item{B or C or D} --> Item{IN-BOTH}
        {
            boolean prevNonempty = false;
            if (itemIds.size() > 0 || other.itemIds.size() > 0)
                prevNonempty = true;
            itemIds = iu.intersectIfNonempty(itemIds, other.itemIds);
            if (itemIds.size() == 0 && prevNonempty)
                noResults = true;
        }
        

        // these we can just union, since:
        //
        // -Item{A or B} AND -Item{C or D} --> 
        //   (-Item(A) AND -Item(B)) AND (-C AND -D) -->
        //     (A AND B AND C AND D)
        prohibitedItemIds = iu.union(prohibitedItemIds, other.prohibitedItemIds);

        // indexId
        //   IndexId{A or B or C} AND IndexId{B or C or D} --> IndexId{IN-BOTH}
        {
            // see comment above at folders 
            if (indexIds.size() > 0 || other.indexIds.size() > 0) {
                indexIds = iu.intersectIfNonempty(indexIds, other.indexIds);
                if (indexIds.size() == 0)
                    noResults = true;
            }
        }

        // types
        // see comments above
        {
            if (types.size() > 0 || other.types.size() > 0) {
                types = bu.intersectIfNonempty(types, other.types);
                if (types.size() ==  0)
                    noResults = true;
            }
        }
            
        // see comments above
        excludeTypes = bu.union(excludeTypes, other.excludeTypes);

        // dates
        if (other.dates != null) {
            if (dates == null) 
                dates = new ArrayList<NumericRange>();
            dates.addAll(other.dates);
        }

        // modified
        if (other.modified != null) {
            if (modified == null) 
                modified = new ArrayList<NumericRange>();
            modified.addAll(other.modified);
        }

        // sizes
        if (other.sizes!= null) {
            if (sizes == null) 
                sizes = new ArrayList<NumericRange>();
            sizes.addAll(other.sizes);
        }
        
        // sizes
        if (other.subjectRanges!= null) {
            if (subjectRanges== null) 
                subjectRanges = new ArrayList<StringRange>();
            subjectRanges.addAll(other.subjectRanges);
        }
        
        // senderRanges
        if (other.senderRanges!= null) {
            if (senderRanges== null) 
                senderRanges = new ArrayList<StringRange>();
            senderRanges.addAll(other.senderRanges);
        }
    }



    boolean automaticEmptySet() {
        // Check for tags and folders that are both included and excluded.
        Set<Integer> s = new HashSet<Integer>();
        addIdsToSet(s, tags);
        addIdsToSet(s, folders);

        // FIXME: Tim removed this assert temporarily while I'm reorganizing the DbQueryObject code...I know
        // about this and will fix it soon.
//      assert(!(setContainsAnyId(s, excludeTags) || setContainsAnyId(s, excludeFolders)));

        if (Boolean.FALSE.equals(hasTags) && tags != null && tags.size() != 0)
            return true;

        // lots more optimizing we could do here...
        if (!ListUtil.isEmpty(dates))
            for (NumericRange r : dates) 
                if (r.lowest < -1 && r.negated)
                    return true;
                else if (r.highest < -1 && !r.negated)
                    return true;

        if (!ListUtil.isEmpty(modified))
            for (NumericRange r : modified)
                if (r.lowest < -1 && r.negated)
                    return true;
                else if (r.highest < -1 && !r.negated)
                    return true;

        return false;
    }

    void checkDates() {
        checkIntervals(dates);
        checkIntervals(modified);
    }
    
    void checkIntervals(Collection<? extends DbSearchConstraints.NumericRange> intervals) {
        if (!ListUtil.isEmpty(intervals)) {
            for (Iterator<? extends NumericRange> iter = intervals.iterator(); iter.hasNext(); ) {
                NumericRange r = iter.next();
                if (!r.isValid())
                    iter.remove();
            }
        }
    }

    private void addIdsToSet(Set<Integer> s, Collection<?> items)
    {
        if (items != null)
            for (Object obj : items)
                s.add(((MailItem)obj).getId());
    }

    private boolean setContainsAnyId(Set<Integer> s, Collection<?> items) {
        if (items != null)
            for (Object obj : items)
                if (s.contains(((MailItem)obj).getId()))
                    return true;
        return false;
    }
}