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
package com.zimbra.cs.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.index.MailboxIndex.SortBy;

/**
 * 
 */
public class TaskSortingQueryResults implements ZimbraQueryResults {

    TaskSortingQueryResults(ZimbraQueryResults results, SortBy desiredSort) throws ServiceException {
        mResults = results;
        mDesiredSort = desiredSort;
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#doneWithSearchResults() */
    public void doneWithSearchResults() throws ServiceException {
        mResults.doneWithSearchResults();
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#estimateResultSize() */
    public int estimateResultSize() throws ServiceException {
        return mResults.estimateResultSize();
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#getFirstHit() */
    public ZimbraHit getFirstHit() throws ServiceException {
        mIterOffset = 0;
        return getNext();
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#getNext() */
    public ZimbraHit getNext() throws ServiceException {
        if (hasNext()) {
            ZimbraHit toRet = peekNext();
            mIterOffset++;
            return toRet;
        } else
            return null;
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#getResultInfo() */
    public List<QueryInfo> getResultInfo() {
        return mResults.getResultInfo();
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#getSortBy() */
    public SortBy getSortBy() {
        return mDesiredSort;
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#hasNext() */
    public boolean hasNext() throws ServiceException {
        List<ZimbraHit> buffer = getHitBuffer();
        return (mIterOffset < buffer.size());
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#peekNext() */
    public ZimbraHit peekNext() throws ServiceException {
        List<ZimbraHit> buffer = getHitBuffer();
        if (hasNext())
            return buffer.get(mIterOffset);
        else
            return null;
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#resetIterator() */
    public void resetIterator() throws ServiceException {
        mIterOffset = 0;
    }

    /* @see com.zimbra.cs.index.ZimbraQueryResults#skipToHit(int) */
    public ZimbraHit skipToHit(int hitNo) throws ServiceException {
        List<ZimbraHit> buffer = getHitBuffer();
        if (hitNo >= buffer.size()) {
            mIterOffset = buffer.size(); 
        } else {
            mIterOffset = hitNo;
        }
        return getNext();
    }
    
    private List<ZimbraHit> getHitBuffer() throws ServiceException {
        if (mHitBuffer == null)
            bufferAllHits();
        return mHitBuffer;
    }
    
    private void bufferAllHits() throws ServiceException {
        assert(mHitBuffer == null);
        mHitBuffer = new ArrayList<ZimbraHit>();

        ZimbraHit cur;
        while ((cur = mResults.getNext()) != null) {
            
            if (!(cur instanceof TaskHit) && !(cur instanceof ProxiedHit)) {
                throw ServiceException.FAILURE("Invalid hit type, can only task-sort Tasks", null);
            }
            mHitBuffer.add(cur);
            if (mHitBuffer.size() >= MAX_BUFFERED_HITS) {
                break;
            }
            
            Comparator<ZimbraHit> comp;
            switch (mDesiredSort) {
                default:
                case TASK_DUE_ASCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByDueDate(true, lhs, rhs);
                        }
                    };
                    break;
                case TASK_DUE_DESCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByDueDate(false, lhs, rhs);
                        }
                    };
                    break;
                case TASK_STATUS_ASCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByStatus(true, lhs, rhs);
                        }
                    };
                    break;
                case TASK_STATUS_DESCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByStatus(false, lhs, rhs);
                        }
                    };
                    break;
                case TASK_PERCENT_COMPLETE_ASCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByCompletionPercent(true, lhs, rhs);
                        }
                    };                        
                    break;
                case TASK_PERCENT_COMPLETE_DESCENDING:
                    comp = new Comparator<ZimbraHit>() {
                        public int compare(ZimbraHit lhs, ZimbraHit rhs) {
                            return TaskHit.compareByCompletionPercent(false, lhs, rhs);
                        }
                    };                        
                    break;
            }
            Collections.sort(mHitBuffer, comp);
        }
    }
    
    static final int MAX_BUFFERED_HITS = 5000;
    
    private ZimbraQueryResults mResults;
    private SortBy mDesiredSort;
    private List<ZimbraHit> mHitBuffer = null;
    private int mIterOffset = 0;
}
