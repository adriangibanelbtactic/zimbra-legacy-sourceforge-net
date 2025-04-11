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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Mar 15, 2005
 *
 */
package com.zimbra.cs.index;

import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.index.MailboxIndex.SortBy;

/**
 * @author tim
 *
 * Interface for iterating through ZimbraHits.  This class is the thing
 * that is returned when you do a Search.
 */
public interface ZimbraQueryResults {
    
    /**
     * Resets the iterator to the beginning
     * 
     * @throws ServiceException
     */
    void resetIterator() throws ServiceException;
    
    /**
     * @return The next hit, advancing the iterator.
     * @throws ServiceException
     */
    ZimbraHit getNext() throws ServiceException;
    
    /**
     * @return The next hit without advancing the iterator.
     * @throws ServiceException
     */
    ZimbraHit peekNext() throws ServiceException;
    
	/**
     * @deprecated  call resetIterator() then getNext()
	 * @return
	 * @throws ServiceException
	 */
	ZimbraHit getFirstHit() throws ServiceException;
	
    /**
     * Slightly more efficient in a few cases (DB-only queries), skip to
     * a specific hit offset.
     * 
     * @param hitNo
     * @return
     * @throws ServiceException
     */
    ZimbraHit skipToHit(int hitNo) throws ServiceException;
    
    /**
     * @return TRUE if there is another Hit
     * 
     * @throws ServiceException
     */
    boolean hasNext() throws ServiceException;
    
    /**
     * MUST be called when you are done with this iterator!  If this is not called,
     * file descriptors can be leaked.
     * 
     * @throws ServiceException
     */
    void doneWithSearchResults() throws ServiceException;

    /**
     * @return The Sort used by these results.  Note that in some cases, this might be a
     * different Sort from the one passed into Mailbox.Search() -- if the sort is overridden
     * by a "Sort:" operator in the search string.
     */
    SortBy getSortBy();
    
    /**
     * QueryInfo is returned from the Search subsystem with meta information about the 
     * search, such as information about wildcard expansion, etc.  
     * 
     * @return
     */
    List<QueryInfo> getResultInfo();
    
    /**
     * Two important requirements for this API:
     * 
     *   1) SearchParams.setEstimateSize(true) must have been set on the query's SearchParams, otherwise the
     *       estimate is not calculated (it is not free to calculate so we only do it when requested)
     *       
     *    2) The size is not estimated until the first result is fetched.  That means you must call
     *    getNext() or peekNext() or similar hit-returning API before calling this function.
     *    
     * @return An ESTIMATE (may be wrong, very wrong in some cases) of the size
     * of the result set.
     */
    int estimateResultSize() throws ServiceException;
}
 