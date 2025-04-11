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
 * Created on Oct 29, 2004
 */
package com.zimbra.cs.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.db.DbMailItem.SearchResult;
import com.zimbra.cs.db.DbPool.Connection;
import com.zimbra.cs.index.LuceneQueryOperation.LuceneResultsChunk;
import com.zimbra.cs.index.LuceneQueryOperation.LuceneResultsChunk.ScoredLuceneHit;
import com.zimbra.cs.index.MailboxIndex.SortBy;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.service.util.ItemId;


/************************************************************************
 * 
 * DBQueryOperation
 * 
 ***********************************************************************/
class DBQueryOperation extends QueryOperation
{
    protected static Log mLog = LogFactory.getLog(DBQueryOperation.class);
    
    private int mSizeEstimate = -1;

    protected IConstraints mConstraints = new DbLeafNode();
    protected int mCurHitsOffset = 0; // this is the logical offset of the end of the mDBHits buffer 
    protected int mOffset = 0; // this is the offset IN THE DATABASE when we're doing a DB-FIRST iteration 

    /**
     * this gets set to FALSE if we have any real work to do this lets 
     * us optimize away queries that might match "everything"
     */
    protected boolean mAllResultsQuery = true;

    protected Collection <SearchResult> mDBHits;
    protected List<ZimbraHit>mNextHits = new ArrayList<ZimbraHit>();
    protected Iterator mDBHitsIter;
    protected boolean atStart = true; // don't re-fill buffer twice if they call hasNext() then reset() w/o actually getting next
    protected int mHitsPerChunk = 100;
    protected static final int MAX_HITS_PER_CHUNK = 2000;

    /**
     * This number *must* be smaller than the value of LuceneQuery.maxClauseCount 
     * (LuceneQuery.getMaxClauseCount())
     */
    protected static final int MAX_DBFIRST_RESULTS = 200;

    /**
     * TRUE if we know there are no more hits to get for mDBHitsIter 
     *   -- ie there is no need to call getChunk() anymore
     */
    protected boolean mEndOfHits = false;

    protected HashSet<Byte>mTypes = new HashSet<Byte>();
    protected HashSet<Byte>mExcludeTypes = new HashSet<Byte>();

    /**
     * An attached Lucene constraint
     */
    protected LuceneQueryOperation mLuceneOp = null;

    /**
     * The current "chunk" of lucene results we are working through -- we need to keep it around
     * so that we can look up the scores of hits that match the DB
     */
    protected LuceneQueryOperation.LuceneResultsChunk mLuceneChunk = null;

    /**
     * If set, then this is the AccountId of the owner of a folder
     * we are searching.  We track it at the toplevel here b/c we need
     * to make sure that we handle unions (don't combine) and intersections
     * (always empty set) correctly
     */
    protected QueryTarget mQueryTarget = QueryTarget.UNSPECIFIED;

    private DbMailItem.SearchResult.ExtraData mExtra = null;
    private QueryExecuteMode mExecuteMode = null; 

    private static enum QueryExecuteMode {
        NO_RESULTS,
        NO_LUCENE,
        DB_FIRST,
        LUCENE_FIRST;
    }

    protected DBQueryOperation() { }

    static DBQueryOperation Create() { return new DBQueryOperation(); }


    /**
     * Since Trash can be an entire folder hierarchy, when we want to exclude trash from a query,
     * we actually have to walk that hierarchy and figure out all the folders within it.
     * 
     * @param mbox
     * @return List of Folders which are in Trash, including Trash itself
     * @throws ServiceException
     */
    private List /* Folder */ getTrashFolders(Mailbox mbox) throws ServiceException {
        return mbox.getFolderById(null, Mailbox.ID_FOLDER_TRASH).getSubfolderHierarchy(); 
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#ensureSpamTrashSetting(com.zimbra.cs.mailbox.Mailbox)
     */
    QueryOperation ensureSpamTrashSetting(Mailbox mbox, boolean includeTrash, boolean includeSpam) throws ServiceException
    {
        if (!hasSpamTrashSetting()) {
            List<Folder> exclude = new ArrayList<Folder>();
            if (!includeSpam) {
                Folder spam = mbox.getFolderById(null, Mailbox.ID_FOLDER_SPAM);            
                exclude.add(spam);
            }
            
            if (!includeTrash) {
                List trashFolders = getTrashFolders(mbox);
                for (Iterator iter  = trashFolders.iterator(); iter.hasNext();) {
                    Folder cur = (Folder)(iter.next());
                    exclude.add(cur);
                }
            }

            mConstraints.ensureSpamTrashSetting(mbox, exclude);
        }
        return this;
    }

    boolean hasSpamTrashSetting() {
        return mConstraints.hasSpamTrashSetting();
    }
    void forceHasSpamTrashSetting() {
        mConstraints.forceHasSpamTrashSetting();
    }
    boolean hasNoResults() {
        return mConstraints.hasNoResults();
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#hasAllResults()
     */
    boolean hasAllResults() {
        return mAllResultsQuery;
    }

    QueryTargetSet getQueryTargets() {
        QueryTargetSet toRet = new QueryTargetSet(1);
        toRet.add(mQueryTarget);
        return toRet;
    }

    /**
     * A bit weird -- basically we want to AND a new constraint: but since
     * the mConstraints object could potentially be a tree, we need a function
     * to find the right place in the tree to add the new constraint
     * 
     * @return
     */
    DbLeafNode topLevelAndedConstraint() {
        switch (mConstraints.getNodeType()) {
            case LEAF:
                return (DbLeafNode)mConstraints;
            case AND:
                DbAndNode and = (DbAndNode)mConstraints;
                return and.getLeafChild();
            case OR:
                IConstraints top = new DbAndNode();
                mConstraints = top.andIConstraints(mConstraints);
                return ((DbAndNode)mConstraints).getLeafChild();
        }
        assert(false);
        return  null;
    }

    /**
     * In an INTERSECTION, we can gain some efficiencies by using the output of the Lucene op
     * as parameters to our SearchConstraints....we do that by taking over the lucene op
     *(it is removed from the enclosing Intersection) and handling it internally.
     *
     * @param op
     */
    void addLuceneOp(LuceneQueryOperation op) {
        assert(mLuceneOp == null);
        mAllResultsQuery = false;
        mLuceneOp = op;
    }

    void addItemIdClause(Integer itemId, boolean truth) {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addItemIdClause(itemId, truth);
    }

    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addDateClause(long lowestDate, boolean lowestEq, long highestDate, boolean highestEq, boolean truth)  {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addDateClause(lowestDate, lowestEq, highestDate, highestEq, truth);
    }

    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addSizeClause(long lowestSize, long highestSize, boolean truth)  {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addSizeClause(lowestSize, highestSize, truth);
    }
    
    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addRelativeSubject(String lowestSubj, boolean lowerEqual, String highestSubj, boolean higherEqual, boolean truth)  {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addSubjectRelClause(lowestSubj, lowerEqual, highestSubj, higherEqual, truth);
    }
    
    /**
     * @param lowest
     * @param highest
     * @param truth
     * @throws ServiceException
     */
    void addRelativeSender(String lowestSubj, boolean lowerEqual, String highestSubj, boolean higherEqual, boolean truth)  {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addSenderRelClause(lowestSubj, lowerEqual, highestSubj, higherEqual, truth);
    }
    
    

    /**
     * @param convId
     * @param prohibited
     */
    void addConvId(int convId, boolean truth) {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addConvId(convId, truth);
    }

    /**
     * Handles inid: query clause that resolves to a remote folder.
     * 
     * @param ownerId
     * @param folderId
     * @param truth
     */
    void addInIdClause(ItemId itemId, boolean truth)
    {
        if (mQueryTarget != QueryTarget.UNSPECIFIED && !mQueryTarget.toString().equals(itemId.getAccountId()))
            throw new IllegalArgumentException("Cannot addInClause b/c DBQueryOperation already has an incompatible remote target");

        mQueryTarget = new QueryTarget(itemId.getAccountId());
        topLevelAndedConstraint().addInIdClause(itemId, truth);
    }

    /**
     * @param folder
     * @param truth
     */
    void addInClause(Folder folder, boolean truth) 
    {
        mAllResultsQuery = false;

        boolean isRemote = false;

        // is it a mountpoint?
        if (folder instanceof Mountpoint) { 
            Mountpoint mpt = (Mountpoint)folder;

            // check to see if this mountpoint is local, just in case...
            if  (!mpt.getOwnerId().equals(mpt.getMailbox().getAccountId())) 
            {
                // remote!
                isRemote = true;
                addInIdClause(new ItemId(mpt.getOwnerId(), mpt.getRemoteId()), truth);
            }
        }

        if (!isRemote) {
            if (mQueryTarget != QueryTarget.LOCAL && mQueryTarget != QueryTarget.UNSPECIFIED) 
                throw new IllegalArgumentException("Cannot addInClause w/ local target b/c DBQueryOperation already has a remote target");

            mQueryTarget = QueryTarget.LOCAL;
        }

        topLevelAndedConstraint().addInClause(folder, truth);
    }

    void addAnyFolderClause(boolean truth) {
        topLevelAndedConstraint().addAnyFolderClause(truth);

        if (!truth) {
            // if they are weird enough to say "NOT is:anywhere" then we
            // just make it a no-results-query.
            mAllResultsQuery = false;
        }
    }

    /**
     * @param tag
     * @param truth
     */
    void addTagClause(Tag tag, boolean truth) {
        mAllResultsQuery = false;
        topLevelAndedConstraint().addTagClause(tag, truth);
    }

    void addTypeClause(byte type, boolean truth) {
        mAllResultsQuery = false;
        if (truth) {
            if (!mTypes.contains(type))
                mTypes.add(type);
        } else {
            if (!mExcludeTypes.contains(type))
                mExcludeTypes.add(type);
        }
    }

    private static class ScoredDBHit implements Comparable {
        public SearchResult mSr;
        public float mScore;

        ScoredDBHit(SearchResult sr, float score) {
            mSr = sr;
            mScore = score;
        }

        long scoreAsLong() { 
            return (long)(mScore * 10000);
        }

        public int compareTo(Object o) {
            ScoredDBHit other = (ScoredDBHit)o;

            long mys = scoreAsLong();
            long os = other.scoreAsLong();

            if (mys == os) 
                return mSr.id - other.mSr.id;
            else {
                long l = os - mys;
                if (l > 0) 
                    return 1;
                else if (l < 0)
                    return -1;
                else return 0;
            }
        }

        public boolean equals(Object o) {
            return (o==this) || (compareTo(o) == 0);
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#doneWithSearchResults()
     */
    public void doneWithSearchResults() throws ServiceException {
        if (mLuceneOp != null) {
            mLuceneOp.doneWithSearchResults();
        }
    }

    public void resetIterator() {
        if (mLuceneOp != null) {
            mLuceneOp.resetDocNum();
        }
        mNextHits.clear();
        if (!atStart) {
            mOffset = 0;
            mDBHitsIter = null;
            mCurHitsOffset = 0;
            mEndOfHits = false;
            atStart = true;
        } else {
            if (mDBHits != null) {
                mDBHitsIter = mDBHits.iterator();
            }
        }
    }

    /*
     * Return the next hit in our search.  If there are no hits buffered
     * then calculate the next hit and put it into the mNextHits list.
     * 
     *   Step 1: Get the list of DbMailItem.SearchResults chunk-by-chunk 
     *           (50 or 100 or whatever at a time)
     *            
     *   Step 2: As we need them, grab the next SearchResult and build a
     *           real ZimbraHit out of them
     */
    public ZimbraHit peekNext() throws ServiceException
    {
        ZimbraHit toRet = null;
        if (mNextHits.size() > 0) {
            // already have some hits, so our job is easy!
            toRet = mNextHits.get(0);
        } else {
            // we don't have any SearchResults, try to get more

            //
            // Check to see if we need to refil mDBHits
            //
            if ((mDBHitsIter == null || !mDBHitsIter.hasNext()) && !mEndOfHits) {
                if (mExtra == null) {
                    mExtra = DbMailItem.SearchResult.ExtraData.NONE;
                    switch (getResultsSet().getSearchMode()) {
                        case NORMAL:
                            if (isTopLevelQueryOp()) {
                                mExtra = DbMailItem.SearchResult.ExtraData.MAIL_ITEM;
                            } else {
                                mExtra = DbMailItem.SearchResult.ExtraData.NONE;
                            }
                            break;
                        case IMAP:
                            mExtra = DbMailItem.SearchResult.ExtraData.IMAP_MSG;
                            break;
                        case IDS:
                            mExtra = DbMailItem.SearchResult.ExtraData.NONE;
                            break;
                    }
                }

                if (mExecuteMode == null) {
                    if (hasNoResults() || !prepareSearchConstraints()) {
                        mExecuteMode = QueryExecuteMode.NO_RESULTS;
                    } else if (mLuceneOp == null) {
                        mExecuteMode = QueryExecuteMode.NO_LUCENE;
                    } else if (shouldExecuteDbFirst()) {
                        mExecuteMode = QueryExecuteMode.DB_FIRST;
                    } else {
                        mExecuteMode = QueryExecuteMode.LUCENE_FIRST;
                    }
                }

                getNextChunk();
            }

            //
            // at this point, we've fill mDBHits if possible (and initialized it's iterator)
            //
            if (mDBHitsIter != null && mDBHitsIter.hasNext()) {
                SearchResult sr = (SearchResult) mDBHitsIter.next();

                // Sometimes, a single search result might yield more than one Lucene
                // document -- e.g. an RFC822 message with separately-indexed mimeparts.
                // Each of these parts will turn into a separate ZimbraHit at this point,
                // although they might be combined together at a higher level (via a HitGrouper)
                List <Document> docs = null;
                float score = 1.0f;
                if (mLuceneChunk != null) {
                    LuceneResultsChunk.ScoredLuceneHit sh = mLuceneChunk.getScoredHit(sr.indexId);
                    if (sh != null) { 
                        docs = sh.mDocs;
                        score = sh.mScore;
                    } else {
                        // This could conceivably happen if we're doing a db-first query and we got multiple LuceneChunks
                        // from a single MAX_DBFIRST_RESULTS set of db-IDs....In practice, this should never happen
                        // since we only pull IDs out of the DB 200 at a time, and the max hits per LuceneChunk is 2000....
                        // ...but I am leaving this log message here temporarily so that I can know if this edge cases
                        // is really happening.  If it *does* somehow happen it isn't the end of the world: we don't lose hits,
                        // we only lose separate part hits -- the net result would be that a document which had a match in multiple
                        // parts would only be returned as a single hit for the document.
                        mLog.info("Missing ScoredLuceneHit for sr.indexId="+sr.indexId+" sr.id="+sr.id+" type="+sr.type+" part hits may be list");
                        docs = null;
                        score = 1.0f;
                    }
                }

                if (docs == null) {
                    ZimbraHit toAdd = getResultsSet().getZimbraHit(getMailbox(), score, sr, null);
                    mNextHits.add(toAdd);
                } else {
                    for (Document doc : docs) {
                        ZimbraHit toAdd = getResultsSet().getZimbraHit(getMailbox(), score, sr, doc);
                        mNextHits.add(toAdd);
                    }
                }
                toRet = mNextHits.get(0);
            }
        }

        return toRet;
    }

    public ZimbraHit getNext() throws ServiceException {
        atStart = false;
        if (mNextHits.size() == 0) {
            peekNext();
        }
        if (mNextHits.size() == 0) {
            return null;
        }
        ZimbraHit toRet = mNextHits.remove(0);
        return toRet;
    }

    private byte[] convertTypesToDbQueryTypes(byte[] types) 
    {
        // hackery
        int numUsed = 0;
        byte[] tmp = new byte[2*types.length]; // boy I love java - no resizable array holds native types

        for (int i = 0; i < types.length; i++) {
            if (types[i] == 0) {
                types = null;
                break;
            }
            switch(types[i]) {
                case 0:
                    return null;
                case MailItem.TYPE_FOLDER:
                case MailItem.TYPE_SEARCHFOLDER:
                case MailItem.TYPE_TAG:
                    tmp[numUsed] = MailItem.TYPE_UNKNOWN;
                    numUsed++;
                    break;
                case MailItem.TYPE_CONVERSATION:
                    tmp[numUsed] = MailItem.TYPE_MESSAGE;
                    numUsed++;
                    break;
                case MailItem.TYPE_MESSAGE:
                    tmp[numUsed] = MailItem.TYPE_MESSAGE;
                    numUsed++;
                    break;
                case MailItem.TYPE_CONTACT:
                    tmp[numUsed] = MailItem.TYPE_CONTACT;
                    numUsed++;
                    break;
                case MailItem.TYPE_APPOINTMENT:
                    tmp[numUsed] = MailItem.TYPE_APPOINTMENT;
                    numUsed++;
                    break;
                case MailItem.TYPE_TASK:
                    tmp[numUsed] = MailItem.TYPE_TASK;
                    numUsed++;
                    break;
                case MailItem.TYPE_DOCUMENT:
                    tmp[numUsed] = MailItem.TYPE_DOCUMENT;
                    numUsed++;
                    break;
                case MailItem.TYPE_NOTE:
                    tmp[numUsed] = MailItem.TYPE_NOTE;
                    numUsed++;
                    break;
                case MailItem.TYPE_FLAG:
                    tmp[numUsed] = MailItem.TYPE_FLAG;
                    numUsed++;
                    break;
                case MailItem.TYPE_WIKI:
                    tmp[numUsed] = MailItem.TYPE_WIKI;
                    numUsed++;
                    break;
            }
        }

        byte[] toRet = new byte[numUsed];
        System.arraycopy(tmp,0,toRet,0,numUsed);

        return toRet;
    }

    private Set<Byte> getDbQueryTypes() 
    {
        byte[] defTypes = convertTypesToDbQueryTypes(this.getResultsSet().getTypes());
        HashSet<Byte> toRet = new HashSet<Byte>();
        for (Byte b : defTypes)
            toRet.add(b);

        if (mTypes.size() > 0) {
            for (Byte b : mTypes)
                if (!toRet.contains(b))
                    toRet.add(b);
        }
        return toRet;
    }

    /**
     * Build a DbMailIte.SearchConstraints given all of the constraint parameters we have.
     *
     * @return FALSE if the search cannot be run (no results)
     */
    private boolean prepareSearchConstraints() {

        Set<Byte> types = getDbQueryTypes();
        if (types.size() == 0)  {
            mLog.debug("NO RESULTS -- no known types requested");
            return false;
        } else {
            mConstraints.setTypes(types);
            return true;
        }
    }

    private byte getSortOrderForDb() {
        SortBy searchOrder = this.getResultsSet().getSortBy();
        return searchOrder.getDbMailItemSortByte();    	
    }

    private boolean shouldExecuteDbFirst() {
        if (getResultsSet().getSortBy() == SortBy.SCORE_DESCENDING) {
            // we can't sort DB-results by score-order, so we must execute SCORE queries
            // in LUCENE-FIRST order
            return false;
        }
        
        if (mLuceneOp != null && mLuceneOp.shouldExecuteDbFirst()) {
            return true;
        }

        return mConstraints.tryDbFirst(getMailbox());
    }

    private void noLuceneGetNextChunk(Connection conn, Mailbox mbox, byte sort) throws ServiceException {
        if (mParams.getEstimateSize() && mSizeEstimate == -1)
            mSizeEstimate = DbMailItem.countResults(conn, mConstraints, mbox);
        
        DbMailItem.search(mDBHits, conn, mConstraints, mbox, sort, mCurHitsOffset, mHitsPerChunk, mExtra);

        if (mDBHits.size() < mHitsPerChunk) {
            mEndOfHits = true;
        }
        // exponentially expand the chunk size in case we have to go back to the DB
        mHitsPerChunk*=2;
        if (mHitsPerChunk > MAX_HITS_PER_CHUNK) {
            mHitsPerChunk = MAX_HITS_PER_CHUNK;
        }
    }

    private void dbFirstGetNextChunk(Connection conn, Mailbox mbox, byte sort) throws ServiceException {
        if (mLog.isDebugEnabled())
            mLog.debug("Running a DB-FIRST execution");
        
        do {
            //
            // (1) Get the next chunk of results from the DB
            //
            Collection<SearchResult> dbRes = new ArrayList<SearchResult>();
            
            // FIXME TODO could do a better job here
            if (mParams.getEstimateSize() && mSizeEstimate == -1) {
                mSizeEstimate = DbMailItem.countResults(conn, mConstraints, mbox);
            }
            
            DbMailItem.search(dbRes, conn, mConstraints, mbox, sort, mOffset, MAX_DBFIRST_RESULTS, mExtra);
            
            if (dbRes.size() < MAX_DBFIRST_RESULTS) {
                mEndOfHits = true;
            }
            
            if (dbRes.size() > 0) {
                mOffset += dbRes.size();
                
                //
                // (2) for each of the results returned in (1), do a lucene search
                //    for "ORIGINAL-LUCENE-PART AND id:(RESULTS-FROM-1-ABOVE)"
                //
                
                // save the original Lucene query, we'll restore it later
                BooleanQuery originalQuery = mLuceneOp.getCurrentQuery();
                
                try {
                    BooleanQuery idsQuery = new BooleanQuery();
                    
                    //
                    // For each search result, do two things:
                    //    -- remember the indexId in a hash, so we can find the SearchResult later
                    //    -- add that indexId to our new booleanquery 
                    //
                    HashMap<Integer, List<SearchResult>> mailItemToResultsMap = new HashMap<Integer, List<SearchResult>>();
                    
                    for (SearchResult res : dbRes) {
                        List<SearchResult> l = mailItemToResultsMap.get(res.indexId);
                        if (l == null) {
                            l = new LinkedList<SearchResult>();
                            mailItemToResultsMap.put(res.indexId, l);
                        }
                        l.add(res);
                        
                        idsQuery.add(new TermQuery(new Term(LuceneFields.L_MAILBOX_BLOB_ID, Integer.toString(res.indexId))), false, false);
                    }
                    
                    // add the new query to the mLuceneOp's query
                    mLuceneOp.addAndedClause(idsQuery, true);
                    
                    boolean hasMore = true;
                    
                    // we have to get ALL of the lucene hits for these ids.  There can very likely be more
                    // hits from Lucene then there are DB id's, so we just ask for a large number.
                    while(hasMore) {
                        mLuceneChunk = mLuceneOp.getNextResultsChunk(MAX_HITS_PER_CHUNK);
                        
                        Collection<Integer> indexIds = mLuceneChunk.getIndexIds();
                        if (indexIds.size() < MAX_HITS_PER_CHUNK) {
                            hasMore = false;
                        } 
                        for (int indexId : indexIds) {
                            List<SearchResult> l = mailItemToResultsMap.get(indexId);
                            for (SearchResult sr : l) {
                                mDBHits.add(sr);
                            }
                        }
                    }
                } finally {
                    // restore the query
                    mLuceneOp.setCurrentQuery(originalQuery);
                }
            }
                
        } while(mDBHits.size() ==0 && !mEndOfHits);
    }

    private void luceneFirstGetNextChunk(Connection conn, Mailbox mbox, byte sort) throws ServiceException {
        if (mLog.isDebugEnabled())
            mLog.debug("Running a LUCENE-FIRST execution");
        
        // do the Lucene op first, pass results to DB op
        do {
            // DON'T set an sql LIMIT if we're asking for lucene hits!!!  If we did, then we wouldn't be
            // sure that we'd "consumed" all the Lucene-ID's, and therefore we could miss hits!
            mLuceneChunk = mLuceneOp.getNextResultsChunk(mHitsPerChunk);

            // we need to set our index-id's here!
            DbLeafNode sc = topLevelAndedConstraint();
            
            if (mParams.getEstimateSize() && mSizeEstimate==-1) {
                // FIXME TODO should probably be a %age, this is worst-case
                sc.indexIds = new HashSet<Integer>(); 
                System.out.println("LUCENE="+mLuceneOp.countHits()+"  DB="+DbMailItem.countResults(conn, mConstraints, mbox));
                mSizeEstimate = Math.min(DbMailItem.countResults(conn, mConstraints, mbox), mLuceneOp.countHits());
            }
            
            sc.indexIds = mLuceneChunk.getIndexIds();

            // exponentially expand the chunk size in case we have to go back to the DB
            mHitsPerChunk*=2;
            if (mHitsPerChunk > MAX_HITS_PER_CHUNK) {
                mHitsPerChunk = MAX_HITS_PER_CHUNK;
            }

            if (sc.indexIds.size() == 0) {
                // we know we got all the index-id's from lucene.  since we don't have a
                // LIMIT clause, we can be assured that this query will get all the remaining results.
                mEndOfHits = true;
            } else {
                // must not ask for offset,limit here b/c of indexId constraints!,  
                DbMailItem.search(mDBHits, conn, mConstraints, mbox, sort, -1, -1, mExtra);

                if (getSortBy() == SortBy.SCORE_DESCENDING) {
                    // We have to re-sort the chunk by score here b/c the DB doesn't
                    // know about scores
                    ScoredDBHit[] scHits = new ScoredDBHit[mDBHits.size()];
                    int offset = 0;
                    for (SearchResult sr : mDBHits) {
                        ScoredLuceneHit lucScore = mLuceneChunk.getScoredHit(sr.indexId);

                        scHits[offset++] = new ScoredDBHit(sr, lucScore.mScore);
                    }

                    Arrays.sort(scHits);

                    mDBHits = new ArrayList<SearchResult>(scHits.length);
                    for (ScoredDBHit sdbHit : scHits)
                        mDBHits.add(sdbHit.mSr);
                }

            }
        } while (mDBHits.size() == 0 && !mEndOfHits);
        
    }


    /**
     * Use all the search parameters (including the embedded LuceneQueryOperation) to
     * get a chunk of search results and put them into mDBHits
     *
     * On Exit:
     *    If there are more results to be had
     *         mDBHits has entries 
     *         mDBHitsIter is initialized 
     *         mCurHitsOffset is the absolute offset (into the result set) of the last entry in mDBHits +1
     *                               that is, it is the offset of the next hit, when we go to get it.
     *      
     *    If there are NOT any more results 
     *        mDBHits is empty
     *        mDBHitsIter is null
     *        mEndOfHits is set
     * 
     * @throws ServiceException
     */
    private void getNextChunk() throws ServiceException
    {
        assert(!mEndOfHits);
        assert(mDBHitsIter == null || !mDBHitsIter.hasNext());

        if (mExecuteMode == QueryExecuteMode.NO_RESULTS) {
            if (mLog.isDebugEnabled()) {
                mLog.debug(" Returned **NO DB RESULTS (no-results-query-optimization)**");
            }
            mDBHitsIter = null;
            mEndOfHits = true;
        } else {
            Mailbox mbox = getMailbox();
            byte sort = getSortOrderForDb();
            Connection conn = DbPool.getConnection();
            mDBHits = new ArrayList<SearchResult>();
            
            try {
                switch (mExecuteMode) {
                    case NO_RESULTS:
                        assert(false); // notreached
                        break;
                    case NO_LUCENE:
                        noLuceneGetNextChunk(conn, mbox, sort);
                        break;
                    case DB_FIRST:
                        dbFirstGetNextChunk(conn, mbox, sort);
                        break;
                    case LUCENE_FIRST:
                        luceneFirstGetNextChunk(conn, mbox, sort);
                        break;
                }
                
            } finally {
                DbPool.quietClose(conn);
            }
            
            if (mDBHits.size() == 0) {
                mDBHitsIter = null;
                mDBHits = null;
                mEndOfHits = true;
            } else {
                mCurHitsOffset += mDBHits.size();
                mDBHitsIter = mDBHits.iterator();
            }
            
        }
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#prepare(com.zimbra.cs.mailbox.Mailbox, com.zimbra.cs.index.ZimbraQueryResultsImpl, com.zimbra.cs.index.MailboxIndex)
     */
    protected void prepare(Mailbox mbx, ZimbraQueryResultsImpl res, MailboxIndex mbidx, SearchParams params, int chunkSize) throws ServiceException, IOException
    {
        mParams = params;
        if (chunkSize > MAX_HITS_PER_CHUNK) {
            chunkSize = MAX_HITS_PER_CHUNK;
        }

        mHitsPerChunk = chunkSize;

        setupResults(mbx, res);

        if (mLuceneOp != null) {
            mHitsPerChunk *= 2; // enlarge chunk size b/c of join
            mLuceneOp.setDBOperation(this);
            mLuceneOp.prepare(mbx, res, mbidx, mParams, mHitsPerChunk);
        }
    }

    int getOpType() {
        return OP_TYPE_DB;
    }


    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#optimize(com.zimbra.cs.mailbox.Mailbox)
     */
    QueryOperation optimize(Mailbox mbox) {
        return this;
    }

    String toQueryString() {
        StringBuilder ret = new StringBuilder("(");
        if (mLuceneOp != null)
            ret.append(mLuceneOp.toQueryString()).append(" AND ");
        ret.append(mConstraints.toQueryString());
        ret.append(')');
        return ret.toString();
    }

    public String toString()
    {
        boolean atFirst = true;
        StringBuilder retVal = new StringBuilder("<");
        if (mLuceneOp != null) {
            retVal.append(mLuceneOp.toString());
            atFirst = false;
        }
        if (!atFirst)
            retVal.append(" AND ");
        
        retVal.append("DB(");
        if (mAllResultsQuery) {
            retVal.append("ANYWHERE");
        } else if (hasNoResults()) {
            retVal.append("--- NO RESULT ---");
        } else {
            retVal.append(mConstraints.toString());
        }
        retVal.append(")");
        
        retVal.append('>');

        return retVal.toString();
    }

    private DBQueryOperation cloneInternal() {
        try {
            DBQueryOperation toRet = (DBQueryOperation)super.clone();

            assert(mDBHits == null);
            assert(mDBHitsIter == null);
            assert(mLuceneChunk == null);


            toRet.mConstraints = (IConstraints)mConstraints.clone();

            toRet.mTypes = new HashSet<Byte>();  toRet.mTypes.addAll(mTypes);
            toRet.mExcludeTypes = new HashSet<Byte>();  toRet.mExcludeTypes.addAll(mExcludeTypes);

            toRet.mNextHits = new ArrayList<ZimbraHit>();

            return toRet;
        } catch (CloneNotSupportedException e) {
            assert(false);
            return null;
        }
    }

    public Object clone() {
        try {
            DBQueryOperation toRet = cloneInternal();
            if (mLuceneOp != null) 
                toRet.mLuceneOp = (LuceneQueryOperation)mLuceneOp.clone(this);
            return toRet;
        } catch (CloneNotSupportedException e) {
            assert(false);
            return null;
        }
    }

    public Object clone(LuceneQueryOperation caller) {
        DBQueryOperation toRet = cloneInternal();
        toRet.mLuceneOp = caller;
        return toRet;
    }



    /* (non-Javadoc)
     * @see com.zimbra.cs.index.QueryOperation#combineOps(com.zimbra.cs.index.QueryOperation, boolean)  
     */
    protected QueryOperation combineOps(QueryOperation other, boolean union) 
    {
        if (union) {
            if (hasNoResults()) {
                // a query for (other OR nothing) == other
                return other;
            }
            if (other.hasNoResults()) {
                return this;
            }

            if (other instanceof DBQueryOperation) {
                DBQueryOperation dbOther = (DBQueryOperation)other;

                if (mQueryTarget != null && dbOther.mQueryTarget != null) {
                    if (!mQueryTarget.equals(dbOther.mQueryTarget))
                        return null;  // can't OR entries with different targets
                }

                if (mAllResultsQuery)
                    return this;

                dbOther = (DBQueryOperation)other;

                if (dbOther.mAllResultsQuery) // (something OR ALL ) == ALL
                    return dbOther;

                if (mLuceneOp != null || dbOther.mLuceneOp != null){
                    // can't combine
                    return null;
                }

                if (mQueryTarget == null)
                    mQueryTarget = dbOther.mQueryTarget;

                mConstraints = mConstraints.orIConstraints(dbOther.mConstraints);
                return this;
            } else {
                return null;
            }
        } else {
            if (mAllResultsQuery) {
                // we match all results.  (other AND anything) == other

                assert(mLuceneOp == null);
                if (hasSpamTrashSetting()) {
                    other.forceHasSpamTrashSetting();
                }
                return other;
            }

            DBQueryOperation dbOther = null;

            if (other instanceof DBQueryOperation) {
                dbOther = (DBQueryOperation)other;
            } else {
                return null;
            }

            if (dbOther.mAllResultsQuery) {
                if (dbOther.hasSpamTrashSetting())
                    this.forceHasSpamTrashSetting();
                return this;
            }

            if (mQueryTarget != QueryTarget.UNSPECIFIED && dbOther.mQueryTarget != QueryTarget.UNSPECIFIED) {
                if (!mQueryTarget.equals(dbOther.mQueryTarget)) {
                    mLog.debug("ANDing two DBOps with different targets -- this is a no results query!");
                    return new NullQueryOperation();
                }
            }

            if (mQueryTarget == QueryTarget.UNSPECIFIED) 
                mQueryTarget = dbOther.mQueryTarget;

            if (mLuceneOp != null) {
                if (dbOther.mLuceneOp != null) {
                    mLuceneOp.combineOps(dbOther.mLuceneOp, false);
                }
            } else {
                mLuceneOp = dbOther.mLuceneOp;
            }

            if (mAllResultsQuery && dbOther.mAllResultsQuery) {
                mAllResultsQuery = true;
            } else {
                mAllResultsQuery = false;
            }

            mConstraints = mConstraints.andIConstraints(dbOther.mConstraints);

            return this;
        }

    }
    
    List<QueryInfo> mQueryInfo = new ArrayList<QueryInfo>();

    public List<QueryInfo> getResultInfo() {
        List<QueryInfo> toRet = new ArrayList<QueryInfo>();
        toRet.addAll(mQueryInfo);
        
        if (mLuceneOp != null)
            toRet.addAll(mLuceneOp.mQueryInfo);
        
        return toRet;
    }
    
    public int estimateResultSize() throws ServiceException {
        return mSizeEstimate;
    }
    
}

