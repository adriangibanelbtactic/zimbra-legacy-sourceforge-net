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
package com.zimbra.cs.index;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbSearchConstraints;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.service.util.ItemId;

class DbLeafNode extends DbSearchConstraints implements IConstraints {

    public Object clone() throws CloneNotSupportedException {
        DbLeafNode toRet = (DbLeafNode)super.clone();

        // make sure we cloned folders instead of just copying them!
        assert(toRet.folders != folders);

        return toRet;
    }

    public void ensureSpamTrashSetting(Mailbox mbox, List<Folder> trashSpamFolders) throws ServiceException
    {
        if (!mHasSpamTrashSetting) {
            for (Folder f : trashSpamFolders) {
                excludeFolders.add(f);
            }
            mHasSpamTrashSetting = true;
        }
    }

    public IConstraints andIConstraints(IConstraints other) 
    {
        switch(other.getNodeType()) {
            case AND:
                return other.andIConstraints(this);
            case OR:
                return other.andIConstraints(this);
            case LEAF:
                if (other.hasSpamTrashSetting()) 
                    forceHasSpamTrashSetting();

                if (other.hasNoResults()) {
                    noResults = true;
                }
                andConstraints((DbLeafNode)other);
                return this;
        }
        assert(false);
        return null;
    }

    public IConstraints orIConstraints(IConstraints other)
    {
        if (other.getNodeType() == NodeType.OR) {
            return other.orIConstraints(this);
        } else {
            IConstraints top = new DbOrNode();
            top = top.orIConstraints(this);
            top = top.orIConstraints(other);
            return top;
        }
    }


    public boolean hasSpamTrashSetting() {
        return mHasSpamTrashSetting;
    }
    public void forceHasSpamTrashSetting() {
        mHasSpamTrashSetting = true;
    }

    public boolean hasNoResults() {
        return noResults;
    }

    public boolean tryDbFirst(Mailbox mbox) {
        return (convId != 0 || (tags != null && tags.contains(mbox.mUnreadFlag))); 
    }


    void addItemIdClause(Integer itemId, boolean truth) {
        if (truth) {
            if (!itemIds.contains(itemId)) {
                //
                // {1} AND {-1} AND {1} == no-results 
                //  ... NOT {1} (which could happen if you removed from both arrays on combining!)
                if (prohibitedItemIds== null || !prohibitedItemIds.contains(itemId)) {
                    itemIds.add(itemId);
                }
            }
        } else {
            if (!prohibitedItemIds.contains(itemId)) {
                //
                // {1} AND {-1} AND {1} == no-results 
                //  ... NOT {1} (which could happen if you removed from both arrays on combining!)
                if (itemIds != null && itemIds.contains(itemId)) {
                    itemIds.remove(itemId);
                }
                prohibitedItemIds.add(itemId);
            }
        }
    }

    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addDateClause(long lowest, boolean lowestEqual, long highest, boolean highestEqual, boolean truth)  {
        DbSearchConstraints.NumericRange intv = new DbSearchConstraints.NumericRange();
        intv.lowest = lowest;
        intv.lowestEqual = lowestEqual;
        intv.highest = highest;
        intv.highestEqual = highestEqual;
        intv.negated = !truth;

        dates.add(intv);
    }

    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addSizeClause(long lowestSize, long highestSize, boolean truth)  {
        DbSearchConstraints.NumericRange intv = new DbSearchConstraints.NumericRange();
        intv.lowest = lowestSize;
        intv.highest = highestSize;
        intv.negated = !truth;

        sizes.add(intv);
    }
    
    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addSubjectRelClause(String lowest, boolean lowestEqual, String highest, boolean highestEqual, boolean truth) {
        DbSearchConstraints.StringRange intv = new DbSearchConstraints.StringRange();
        intv.lowest = lowest;
        intv.lowestEqual = lowestEqual;
        intv.highest = highest;
        intv.highestEqual = highestEqual;
        intv.negated = !truth;
        
        subjectRanges.add(intv);
    }
    
    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addSenderRelClause(String lowest, boolean lowestEqual, String highest, boolean highestEqual, boolean truth) {
        DbSearchConstraints.StringRange intv = new DbSearchConstraints.StringRange();
        intv.lowest = lowest;
        intv.lowestEqual = lowestEqual;
        intv.highest = highest;
        intv.highestEqual = highestEqual;
        intv.negated = !truth;
        
        senderRanges.add(intv);
    }
    
    

    /**
     * @param convId
     * @param prohibited
     */
    void addConvId(int cid, boolean truth) {
        if (truth) {
            if (prohibitedConvIds.contains(cid)) {
                noResults = true;
            }

            if (convId == 0) {
                convId = cid;
            } else {
                if (DBQueryOperation.mLog.isDebugEnabled()) {
                    DBQueryOperation.mLog.debug("Query requested two conflicting convIDs, this is now a no-results-query");
                }
                convId = Integer.MAX_VALUE;
                noResults = true;
            }
        } else {
            if (convId == cid) {
                noResults = true;
            }
            prohibitedConvIds.add(cid);
        }
    }

    /**
     * For remote folder
     * 
     * @param id
     * @param truth
     */
    void addInIdClause(ItemId id, boolean truth)
    {
        if (truth) {
            if ((remoteFolders.size() > 0 && !remoteFolders.contains(id)) 
                        || excludeRemoteFolders.contains(id)) {
                if (DBQueryOperation.mLog.isDebugEnabled()) {
                    DBQueryOperation.mLog.debug("AND of conflicting remote folders, no-results-query");
                }
                noResults = true;
            }
            remoteFolders.clear();
            remoteFolders.add(id);
            forceHasSpamTrashSetting();
        } else {
            if (remoteFolders.contains(id)) {
                remoteFolders.remove(id);
                if (remoteFolders.size() == 0) {
                    if (DBQueryOperation.mLog.isDebugEnabled()) {
                        DBQueryOperation.mLog.debug("AND of conflicting remote folders, no-results-query");
                    }
                    noResults = true;
                }
            }
            excludeRemoteFolders.add(id);
        }
    }
    
    /**
     * @param folder
     * @param truth
     */
    void addInClause(Folder folder, boolean truth) 
    {
        if (truth) {
            if ((folders.size() > 0 && !folders.contains(folder)) 
                        || excludeFolders.contains(folder)) {
                if (DBQueryOperation.mLog.isDebugEnabled()) {
                    DBQueryOperation.mLog.debug("AND of conflicting folders, no-results-query");
                }
                noResults = true;
            }
            folders.clear();
            folders.add(folder);
            forceHasSpamTrashSetting();
        } else {
            if (folders.contains(folder)) {
                folders.remove(folder);
                if (folders.size() == 0) {
                    if (DBQueryOperation.mLog.isDebugEnabled()) {
                        DBQueryOperation.mLog.debug("AND of conflicting folders, no-results-query");
                    }
                    noResults = true;
                }
            }
            excludeFolders.add(folder);

            int fid = folder.getId();
            if (fid == Mailbox.ID_FOLDER_TRASH || fid == Mailbox.ID_FOLDER_SPAM) {
                forceHasSpamTrashSetting();
            }
        }
    }

    void addAnyFolderClause(boolean truth) {
        // support for "is:anywhere" basically as a way to get around
        // the trash/spam autosetting
        forceHasSpamTrashSetting();

        if (!truth) {
            // if they are weird enough to say "NOT is:anywhere" then we
            // just make it a no-results-query.

            if (DBQueryOperation.mLog.isDebugEnabled()) {
                DBQueryOperation.mLog.debug("addAnyFolderClause(FALSE) called -- changing to no-results-query.");
            }
            noResults = true;
        }
    }

    /**
     * @param tag
     * @param truth
     */
    void addTagClause(Tag tag, boolean truth) {
        if (DBQueryOperation.mLog.isDebugEnabled()) {
            DBQueryOperation.mLog.debug("AddTagClause("+tag+","+truth+")");
        }
        if (truth) {
            if (excludeTags!=null && excludeTags.contains(tag)) {
                if (DBQueryOperation.mLog.isDebugEnabled()) {
                    DBQueryOperation.mLog.debug("TAG and NOT TAG = no results");
                }
                noResults = true;
            }
            if (tags == null) 
                tags = new HashSet<Tag>();
            tags.add(tag);
        } else {
            if (tags != null && tags.contains(tag)) {
                if (DBQueryOperation.mLog.isDebugEnabled()) {
                    DBQueryOperation.mLog.debug("TAG and NOT TAG = no results");
                }
                noResults = true;
            }
            if (excludeTags == null)
                excludeTags = new HashSet<Tag>();
            excludeTags.add(tag);
        }
    }


    public void setTypes(Set<Byte> _types) {
        this.types = _types;
    }

    public String toQueryString() {
        if (noResults)
            return "-is:anywhere";

        return toString();
    }


    public String toString()
    {
        return super.toString();
    }

    /**
     * true if we have a SETTING pertaining to Spam/Trash.  This doesn't 
     * necessarily mean we actually have "in trash" or something, it just 
     * means that we've got something set which means we shouldn't add 
     * the default "not in:trash and not in:junk" thing.
     */
    protected boolean mHasSpamTrashSetting = false;

}