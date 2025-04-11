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

/*
 * Created on Mar 30, 2005
 *
 * Simple class that encapsulates all of the parameters involved in a Search request.
 * Not used everywhere, need to convert all code to use this....
 * 
 */
package com.zimbra.cs.index;

import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.mail.ToXML.OutputParticipants;


/**
 * @author tim
 * 
 * To initialize, set:
 *   -- query str
 *   -- offset
 *   -- limit
 *   -- typesStr (sets type value)
 *   -- sortByStr (sets sortBy value)
 *
 */
public final class SearchParams 
{
    public int getLimit() { return mLimit; }
    public int getOffset() { return mOffset; }
    public String getQueryStr() { return mQueryStr; }
    public String getTypesStr() { return mGroupByStr; }
    public byte[] getTypes() { return types; }
    public String getSortByStr() { return mSortByStr; }
    public int getSortBy() { return sortBy; }
    public boolean getFetchFirst() { return mFetchFirst; }
    public boolean getMarkRead() { return mMarkRead; }
    public boolean getWantHtml() { return mWantHtml; }
    public OutputParticipants getWantRecipients() { return mRecipients ? OutputParticipants.PUT_RECIPIENTS : OutputParticipants.PUT_SENDERS; }

    public void setQueryStr(String queryStr) { mQueryStr = queryStr; }
    public void setOffset(int offset) { mOffset = offset; }
    public void setLimit(int limit) { mLimit = limit; }
    public void setTypesStr(String groupByStr) throws ServiceException {
        mGroupByStr = groupByStr;
        types = MailboxIndex.parseGroupByString(getTypesStr());
    }
    public void setSortByStr(String sortByStr) throws ServiceException { 
        mSortByStr = sortByStr;
        sortBy = MailboxIndex.parseSortByString(mSortByStr);        
    }
    public void setFetchFirst(boolean fetch) { mFetchFirst = fetch; }
    public void setMarkRead(boolean read) { mMarkRead = read; }
    public void setWantHtml(boolean html) { mWantHtml = html; }
    public void setWantRecipients(boolean recips) { mRecipients = recips; }
    
    public boolean hasCursor() { return mHasCursor; }
    public void setCursor(int prevMailItemId, String prevSort, int prevOffset) {
        mHasCursor = true;
        mPrevMailItemId = prevMailItemId;
        mPrevSortValueStr = prevSort;
        try {
            mPrevSortValueLong = Long.parseLong(prevSort);
        } catch (NumberFormatException e) {
            mPrevSortValueLong = 0;
        }
        mPrevOffset = prevOffset;
    }
    public int getPrevMailItemId() { return mPrevMailItemId; }
    public String getPrevSortValueStr() { return mPrevSortValueStr; }
    public long getPrevSortValueLong() { return mPrevSortValueLong; }
    public int getPrevOffset() { return mPrevOffset; }

    private String mQueryStr;
    private int mOffset;
    private int mLimit;
    private boolean mFetchFirst;
    private boolean mMarkRead;
    private boolean mWantHtml;
    private boolean mRecipients;
    
    
    private boolean mHasCursor = false;
    
    /////////////////////
    // "Cursor" Data -- the three pieces of info below are enough for us to find out place in
    // the previous result set, even if entries have been added or removed from the result
    // set:
    private int mPrevMailItemId; // the mail item ID of the last item in the previous result set
    private String mPrevSortValueStr; // the sort value of the last item in the previous result set
    private long mPrevSortValueLong; // the sort value of the last item in the previous result set
    private int mPrevOffset; // the offset of the last item in the previous result set 
    

    // unparsed -- these need to go away!
    private String mGroupByStr;
    private String mSortByStr;
    
    // parsed:
    private int sortBy; // parsed value of sort by string
    private byte[] types; // types to seach for
}