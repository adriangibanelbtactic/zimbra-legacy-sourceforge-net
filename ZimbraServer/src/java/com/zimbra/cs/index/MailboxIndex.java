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
 * Created on Jul 26, 2004
 */
package com.zimbra.cs.index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import javax.mail.internet.MimeMessage;

import com.zimbra.common.util.Constants;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SingleInstanceLockFactory;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.db.DbSearchConstraints;
import com.zimbra.cs.db.DbPool.Connection;
import com.zimbra.common.localconfig.LC;
import com.zimbra.cs.im.interop.Interop.ServiceName;
import com.zimbra.cs.index.queryparser.ParseException;
import com.zimbra.cs.localconfig.DebugConfig;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.Note;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.mime.ParsedDocument;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.redolog.op.IndexItem;
import com.zimbra.cs.service.im.IMGatewayRegister;
import com.zimbra.cs.stats.ZimbraPerf;
import com.zimbra.cs.store.Volume;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.SoapProtocol;
import com.zimbra.cs.util.JMSession;

/**
 * Encapsulates the Index for one particular mailbox
 */
public final class MailboxIndex 
{
    public static ZimbraQueryResults search(SoapProtocol proto, OperationContext octxt, Mailbox mbox, SearchParams params,
        boolean includeTrashByDefault, boolean includeSpamByDefault) throws IOException, ParseException, ServiceException {
        
        String qs = params.getQueryStr();
        if (qs.startsWith("$im")) {
            String[] words = qs.split(" ");
            if ("$im_reg".equals(words[0])) {
                if (words.length < 4)
                    throw ServiceException.FAILURE("USAGE: \"$im_reg service service_login_name service_login_password\"", null);
                ServiceName service = ServiceName.valueOf(words[1]);
                IMGatewayRegister.register(mbox, octxt,  service, words[2], words[3]);
            } else if ("$im_unreg".equals(words[0])) {
                if (words.length < 2)
                    throw ServiceException.FAILURE("USAGE: \"$im_unreg service service_login_name service_login_password\"", null);
                ServiceName service = ServiceName.valueOf(words[1]);
                IMGatewayRegister.unregister(mbox, octxt,  service);
            } else {
                throw ServiceException.FAILURE("Usage: \"$im_reg service name password\" or \"$im_unreg service\"", null); 
            }
                
            return new EmptyQueryResults(params.getTypes(), params.getSortBy(), params.getMode());
        }
        
        if ((params.getCalItemExpandStart() > 0) || (params.getCalItemExpandEnd() > 0)) {
            qs = '(' + qs + ')';
            if (params.getCalItemExpandStart() > 0) 
                qs = qs + " appt-end:>=" + params.getCalItemExpandStart();
            if (params.getCalItemExpandEnd() > 0)
                qs = qs + " appt-start:<=" + params.getCalItemExpandEnd();
            params.setQueryStr(qs);
        }
        
        // handle special-case Task-only sorts: convert them to a "normal sort"
        //     and then re-sort them at the end
        boolean isTaskSort = false;
        SortBy originalSort = params.getSortBy();
        switch (originalSort) {
            case TASK_DUE_ASCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
            case TASK_DUE_DESCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
            case TASK_STATUS_ASCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
            case TASK_STATUS_DESCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
            case TASK_PERCENT_COMPLETE_ASCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
            case TASK_PERCENT_COMPLETE_DESCENDING:
                isTaskSort = true;
                params.setSortBy(SortBy.DATE_DESCENDING);
                break;
        }
        
        ZimbraQuery zq = new ZimbraQuery(mbox, params, includeTrashByDefault, includeSpamByDefault);
        try {
            zq.executeRemoteOps(proto, octxt);
            ZimbraQueryResults results = zq.execute();
            
            if (isTaskSort) {
                results = new TaskSortingQueryResults(results, originalSort);
            }
            
            return results;
        } catch (IOException e) {
            zq.doneWithQuery();
            throw e;
        } catch (ServiceException e) {
            zq.doneWithQuery();
            throw e;
        } catch (Throwable t) {
            zq.doneWithQuery();
            throw ServiceException.FAILURE("Caught "+t.getMessage(), t);
        }
    }
    
    private static class TermEnumCallback implements TermEnumInterface {
        private Collection mCollection;

        TermEnumCallback(Collection collection) {
            mCollection = collection;
        }
        public void onTerm(Term term, int docFreq) {
            String text = term.text();
            if (text.length() > 1) {
                mCollection.add(text);
            }			
        }
    }
    private static class DomainEnumCallback implements TermEnumInterface {
        private Collection mCollection;

        DomainEnumCallback(Collection collection) {
            mCollection = collection;
        }
        public void onTerm(Term term, int docFreq) {
            String text = term.text();
            if (text.length() > 1 && text.charAt(0) == '@') {
                mCollection.add(text.substring(1));
            }			
        }
    }

    /**
     * @param fieldName - a lucene field (e.g. LuceneFields.L_H_CC)
     * @param collection - Strings which correspond to all of the domain terms stored in a given field.
     * @throws IOException
     */
    public void getDomainsForField(String fieldName, Collection collection) throws IOException
    {
        enumerateTermsForField(new Term(fieldName,""),new DomainEnumCallback(collection));
    }

    /**
     * @param collection - Strings which correspond to all of the attachment types in the index
     * @throws IOException
     */
    public void getAttachments(Collection collection) throws IOException
    {
        enumerateTermsForField(new Term(LuceneFields.L_ATTACHMENTS,""), new TermEnumCallback(collection));
    }

    public void getObjects(Collection collection) throws IOException
    {
        enumerateTermsForField(new Term(LuceneFields.L_OBJECTS,""), new TermEnumCallback(collection));
    }

    void enumerateDocumentsForTerm(Collection collection, String field) throws IOException {
        enumerateTermsForField(new Term(field,""), new TermEnumCallback(collection));
    }

    /**
    Finds and returns the smallest of three integers 
     */
    private static final int min(int a, int b, int c) {
        int t = (a < b) ? a : b;
        return (t < c) ? t : c;
    }

    /**
     * This static array saves us from the time required to create a new array
     * everytime editDistance is called.
     */
    private int e[][] = new int[1][1];

    /**
    Levenshtein distance also known as edit distance is a measure of similiarity
    between two strings where the distance is measured as the number of character 
    deletions, insertions or substitutions required to transform one string to 
    the other string. 
    <p>This method takes in four parameters; two strings and their respective 
    lengths to compute the Levenshtein distance between the two strings.
    The result is returned as an integer.
     */ 
    private final int editDistance(String s, String t, int n, int m) {
        if (e.length <= n || e[0].length <= m) {
            e = new int[Math.max(e.length, n+1)][Math.max(e[0].length, m+1)];
        }
        int d[][] = e; // matrix
        int i; // iterates through s
        int j; // iterates through t
        char s_i; // ith character of s

        if (n == 0) return m;
        if (m == 0) return n;

        // init matrix d
        for (i = 0; i <= n; i++) d[i][0] = i;
        for (j = 0; j <= m; j++) d[0][j] = j;

        // start computing edit distance
        for (i = 1; i <= n; i++) {
            s_i = s.charAt(i - 1);
            for (j = 1; j <= m; j++) {
                if (s_i != t.charAt(j-1))
                    d[i][j] = min(d[i-1][j], d[i][j-1], d[i-1][j-1])+1;
                else d[i][j] = min(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]);
            }
        }

        // we got the result!
        return d[n][m];
    }

    List<SpellSuggestQueryInfo.Suggestion> suggestSpelling(String field, String token) throws ServiceException {
        LinkedList<SpellSuggestQueryInfo.Suggestion> toRet = null;

        token = token.toLowerCase();

        try {
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();

                Term term = new Term(field, token);
                int freq = iReader.docFreq(term);
                int numDocs = iReader.numDocs();

                if (freq == 0 && numDocs > 0) {
                    toRet = new LinkedList<SpellSuggestQueryInfo.Suggestion>();

//                    float frequency = ((float)freq)/((float)numDocs);
//
//                    int suggestionDistance = Integer.MAX_VALUE;

                    FuzzyTermEnum fuzzyEnum = new FuzzyTermEnum(iReader, term, 0.5f, 1);
                    if (fuzzyEnum != null) {
                        do {
                            Term cur = fuzzyEnum.term();
                            if (cur != null) {
                                String curText = cur.text();
                                int curDiff = editDistance(curText, token, curText.length(), token.length());
                                
                                SpellSuggestQueryInfo.Suggestion sug = new SpellSuggestQueryInfo.Suggestion();
                                sug.mStr = curText;
                                sug.mEditDist = curDiff;
                                sug.mDocs = fuzzyEnum.docFreq();
                                toRet.add(sug);
                            }
                        } while(fuzzyEnum.next());
                    }
                }
            } finally {
                reader.release();
            }
        } catch (IOException e) {
            throw ServiceException.FAILURE("Caught IOException opening index", e);
        }

        return toRet;
    }

    /**
     * @return TRUE if all tokens were expanded or FALSE if no more tokens could be expanded
     */
    boolean expandWildcardToken(Collection<String> toRet, String field, String token, int maxToReturn) throws ServiceException 
    {
        // all lucene text should be in lowercase...
        token = token.toLowerCase();

        try {
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                Term firstTerm = new Term(field, token);

                IndexReader iReader = reader.getReader();

                TermEnum terms = iReader.terms(firstTerm);

                do {
                    Term cur = terms.term();
                    if (cur != null) {
                        if (!cur.field().equals(firstTerm.field())) {
                            break;
                        }

                        String curText = cur.text();

                        if (curText.startsWith(token)) {
                            if (toRet.size() >= maxToReturn) 
                                return false;

                            // we don't care about deletions, they will be filtered later
                            toRet.add(cur.text());
                        } else {
                            if (curText.compareTo(token) > 0)
                                break;
                        }
                    }
                } while (terms.next());

                return true;
            } finally {
                reader.release();
            }
        } catch (IOException e) {
            throw ServiceException.FAILURE("Caught IOException opening index", e);
        }
    }

    /**
     * Force all outstanding index writes to go through.  
     * This API should be called when the system detects that it has free time.
     */
    public void flush() {
        synchronized(getLock()) {
            if (isIndexWriterOpen()) 
                closeIndexWriter();
            else
                sIndexReadersCache.removeIndexReader(this);
        }
    }

    /**
     * @param itemIds array of itemIds to be deleted
     * 
     * @return an array of itemIds which HAVE BEEN PROCESSED.  If returned.length == 
     * itemIds.length then you can assume the operation was completely successful
     * 
     * @throws IOException on index open failure, nothing processed.
     */
    public int[] deleteDocuments(int itemIds[]) throws IOException {
        synchronized(getLock()) {
            openIndexWriter();
            try {
                for (int i = 0; i < itemIds.length; i++) {
                    try {
                        String itemIdStr = Integer.toString(itemIds[i]);
                        Term toDelete = new Term(LuceneFields.L_MAILBOX_BLOB_ID, itemIdStr);
                        mIndexWriter.deleteDocuments(toDelete);
                        // NOTE!  The numDeleted may be < you expect here, the document may
                        // already be deleted and just not be optimized out yet -- some lucene
                        // APIs (e.g. docFreq) will still return the old count until the indexes 
                        // are optimized...
                        if (sLog.isDebugEnabled()) {
                            sLog.debug("Deleted index documents for itemId "+itemIdStr);
                        }
                    } catch (IOException ioe) {
                        sLog.debug("deleteDocuments exception on index "+i+" out of "+itemIds.length+" (id="+itemIds[i]+")");
                        int[] toRet = new int[i];
                        System.arraycopy(itemIds,0,toRet,0,i);
                        return toRet;
                    }
                }
            } finally {
                mIndexWriterMutex.unlock();
            }
            return itemIds; // success
        }
    }

    private void addDocument(IndexItem redoOp, Document doc, int indexId, long receivedDate, 
        MailItem mi, boolean deleteFirst) throws IOException {
        addDocument(redoOp, new Document[] { doc }, indexId, receivedDate, mi, deleteFirst);
    }
    
    private void addDocument(IndexItem redoOp, Document[] docs, int indexId, long receivedDate, 
        MailItem mi, boolean deleteFirst) throws IOException {
        long start = 0;
        synchronized(getLock()) {        
            if (sLog.isDebugEnabled())
                start = System.currentTimeMillis();
            
            openIndexWriter();
            try {
                assert(mIndexWriterMutex.isHeldByCurrentThread());
                assert(mIndexWriter != null);

                for (Document doc : docs) {
                    // doc can be shared by multiple threads if multiple mailboxes
                    // are referenced in a single email
                    synchronized (doc) {
                        doc.removeFields(LuceneFields.L_SORT_SUBJECT);
                        doc.removeFields(LuceneFields.L_SORT_NAME);
                        //                                                                                                  store, index, tokenize
                        doc.add(new Field(LuceneFields.L_SORT_SUBJECT, mi.getSortSubject(), Field.Store.NO, Field.Index.UN_TOKENIZED));
                        doc.add(new Field(LuceneFields.L_SORT_NAME,    mi.getSortSender(), Field.Store.NO, Field.Index.UN_TOKENIZED));
                        
                        doc.removeFields(LuceneFields.L_MAILBOX_BLOB_ID);
                        doc.add(new Field(LuceneFields.L_MAILBOX_BLOB_ID, Integer.toString(indexId), Field.Store.YES, Field.Index.UN_TOKENIZED));
                        
                        // If this doc is shared by mult threads, then the date might just be wrong,
                        // so remove and re-add the date here to make sure the right one gets written!
                        doc.removeFields(LuceneFields.L_SORT_DATE);
                        String dateString = DateField.timeToString(receivedDate);
                        doc.add(new Field(LuceneFields.L_SORT_DATE, dateString, Field.Store.YES, Field.Index.UN_TOKENIZED));
                        
                        if (null == doc.get(LuceneFields.L_ALL)) {
                            doc.add(new Field(LuceneFields.L_ALL, LuceneFields.L_ALL_VALUE, Field.Store.NO, Field.Index.NO_NORMS, Field.TermVector.NO));
                        }
                        
                        if (deleteFirst) {
                            String itemIdStr = Integer.toString(indexId);
                            Term toDelete = new Term(LuceneFields.L_MAILBOX_BLOB_ID, itemIdStr);
                            mIndexWriter.updateDocument(toDelete, doc);
                        } else {
                            mIndexWriter.addDocument(doc);
                        }
                        
                        if (redoOp != null)
                            mUncommittedRedoOps.add(redoOp);
                    }
                }
                
                // tim: this might seem bad, since an index in steady-state-of-writes will never get flushed, 
                // however we also track the number of uncomitted-operations on the index, and will force a 
                // flush if the index has had a lot written to it without a flush.
                updateLastWriteTime();
            } finally {
                mIndexWriterMutex.unlock();
            }

            if (mUncommittedRedoOps.size() > sMaxUncommittedOps) {
                if (sLog.isDebugEnabled()) {
                    sLog.debug("Flushing " + toString() + " because of too many uncommitted redo ops");
                }
                flush();
            }
        }
        
        if (sLog.isDebugEnabled()) {
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            sLog.debug("MailboxIndex.addDocument took " + elapsed + " msec");
        }
    }

    int numDocs() throws IOException 
    {
        synchronized(getLock()) {        
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();
                return iReader.numDocs();
            } finally {
                reader.release();
            }
        }
    }

    void enumerateTermsForField(Term firstTerm, TermEnumInterface callback) throws IOException
    {
        synchronized(getLock()) {        
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();

                TermEnum terms = iReader.terms(firstTerm);
                boolean hasDeletions = iReader.hasDeletions();

                do {
                    Term cur = terms.term();
                    if (cur != null) {
                        if (!cur.field().equals(firstTerm.field())) {
                            break;
                        }

                        // NOTE: the term could exist in docs, but they might all be deleted. Unfortunately this means  
                        // that we need to actually walk the TermDocs enumeration for this document to see if it is
                        // non-empty
                        if ((!hasDeletions) || (iReader.termDocs(cur).next())) {
                            callback.onTerm(cur, terms.docFreq());
                        }
                    }
                } while (terms.next());
            } finally {
                reader.release();
            }
        }
    }

    void enumerateDocuments(DocEnumInterface c) throws IOException {
        synchronized(getLock()) {        
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();
                int maxDoc = iReader.maxDoc();
                c.maxDocNo(maxDoc);
                for (int i = 0; i < maxDoc; i++) {
                    if (!c.onDocument(iReader.document(i), iReader.isDeleted(i))) {
                        return;
                    }
                }
            } finally {
                reader.release();
            }
        }        
    }

    Collection getFieldNames() throws IOException {
        synchronized(getLock()) {        
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();
                return iReader.getFieldNames(FieldOption.ALL);
            } finally {
                reader.release(); 
            }
        }
    }

    Sort getSort(SortBy searchOrder) {
        synchronized(getLock()) {
            if (searchOrder != mLatestSortBy) { 
                switch (searchOrder) {
                    case DATE_DESCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_DATE, SortField.STRING, true));
                        mLatestSortBy = searchOrder;
                        break;
                    case DATE_ASCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_DATE, SortField.STRING, false));
                        mLatestSortBy = searchOrder;
                        break;
                    case SUBJ_DESCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_SUBJECT, SortField.STRING, true));
                        mLatestSortBy = searchOrder;
                        break;
                    case SUBJ_ASCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_SUBJECT, SortField.STRING, false));
                        mLatestSortBy = searchOrder;
                        break;
                    case NAME_DESCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_NAME, SortField.STRING, true));
                        mLatestSortBy = searchOrder;
                        break;
                    case NAME_ASCENDING:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_NAME, SortField.STRING, false));
                        mLatestSortBy = searchOrder;
                        break;
                    case SCORE_DESCENDING:
                        return null;
                    default:
                        mLatestSort = new Sort(new SortField(LuceneFields.L_SORT_DATE, SortField.STRING, true));
                       mLatestSortBy = SortBy.DATE_ASCENDING;
                }
            }
            return mLatestSort;
        }
    }


    public String toString() {
        StringBuffer ret = new StringBuffer("MailboxIndex(");
        ret.append(mMailboxId);
        ret.append(")");
//        ret.append(super.toString());
        return ret.toString();
    }
    
    public MailboxIndex(Mailbox mbox, String root) throws ServiceException {
        int mailboxId = mbox.getId();
        
        mIndexWriter = null;
        mMailboxId = mailboxId;
        mMailbox = mbox;

        Volume indexVol = Volume.getById(mbox.getIndexVolume());
        String idxParentDir = indexVol.getMailboxDir(mailboxId, Volume.TYPE_INDEX);

        // this must be different from the idxParentDir (see the IMPORTANT comment below)
        String idxPath = idxParentDir + File.separatorChar + '0';

        {
            File parentDirFile = new File(idxParentDir);

            // IMPORTANT!  Don't make the actual index directory (mIdxDirectory) yet!  
            //
            // The runtime open-index code checks the existance of the actual index directory:  
            // if it does exist but we cannot open the index, we do *NOT* create it under the 
            // assumption that the index was somehow corrupted and shouldn't be messed-with....on the 
            // other hand if the index dir does NOT exist, then we assume it has never existed (or 
            // was deleted intentionally) and therefore we should just create an index.
            if (!parentDirFile.exists())
                parentDirFile.mkdirs();

            if (!parentDirFile.canRead()) {
                throw ServiceException.FAILURE("Cannot READ index directory (mailbox="+mbox.getId()+ " idxPath="+idxPath+")", null);
            }
            if (!parentDirFile.canWrite()) {
                throw ServiceException.FAILURE("Cannot WRITE index directory (mailbox="+mbox.getId()+ " idxPath="+idxPath+")", null);
            }

            // the Lucene code does not atomically swap the "segments" and "segments.new"
            // files...so it is possible that a previous run of the server crashed exactly in such
            // a way that we have a "segments.new" file but not a "segments" file.  We we will check here 
            // for the special situation that we have a segments.new
            // file but not a segments file...
            File segments = new File(idxPath, "segments");
            if (!segments.exists()) {
                File segments_new = new File(idxPath, "segments.new");
                if (segments_new.exists()) 
                    segments_new.renameTo(segments);
            }
            
            try {
                // must call getDirectory then setLockFactory via 2 calls -- there's the possibility
                // that the directory we're returned is actually a cached FSDirectory (e.g. if the index
                // was deleted and re-created) in which case we should be using the existing LockFactory
                // and not creating a new one
                mIdxDirectory = FSDirectory.getDirectory(idxPath);
                if (mIdxDirectory.getLockFactory() == null || !(mIdxDirectory.getLockFactory() instanceof SingleInstanceLockFactory))
                    mIdxDirectory.setLockFactory(new SingleInstanceLockFactory());
            } catch (IOException e) {
                throw ServiceException.FAILURE("Cannot create FSDirectory at path: "+idxPath, e);
            }
        }
        
        String analyzerName = mbox.getAccount().getAttr(Provisioning.A_zimbraTextAnalyzer, null);
        
        if (analyzerName != null)
            mAnalyzer = ZimbraAnalyzer.getAnalyzer(analyzerName);
        else
            mAnalyzer = ZimbraAnalyzer.getDefaultAnalyzer();
        
        sLog.info("Initialized Index for mailbox " + mailboxId+" directory: "+mIdxDirectory.toString()+" Analyzer="+mAnalyzer.toString());
    }
    
    protected void finalize() throws Throwable {
        try {
            if (mIdxDirectory != null) 
                mIdxDirectory.close();
            mIdxDirectory = null;
        } finally {
            super.finalize();
        }
    }

    private FSDirectory mIdxDirectory = null;
    private Sort mLatestSort = null;
    private SortBy mLatestSortBy = null;
    private int mMailboxId;
    private Mailbox mMailbox;
    private static Log sLog = LogFactory.getLog(MailboxIndex.class);
    private ArrayList<IndexItem>mUncommittedRedoOps = new ArrayList<IndexItem>();

    private static final boolean sNewLockModel;
    static {
        String value = LC.get(LC.debug_mailboxindex_use_new_locking.key());
        if (value != null && !value.equalsIgnoreCase("true"))
            sNewLockModel = false;
        else
            sNewLockModel = true;
    }
    
    private IndexWriter mIndexWriter;
    private ReentrantLock mIndexWriterMutex = new ReentrantLock();
    
    private static LinkedHashMap<MailboxIndex, MailboxIndex> sOpenIndexWriters =
        new LinkedHashMap<MailboxIndex, MailboxIndex>(200, 0.75f, true);
    private static int sReservedWriterSlots = 0;
    private static IndexWritersSweeper sSweeper = null;
    
    /**
     * How often do we walk the list of open IndexWriters looking for idle writers
     * to close.  On very busy systems, the default time might be too long.
     */
    private static long sSweeperFrequencyMs = 30 * Constants.MILLIS_PER_SECOND;

    public static void startup() {
        if (DebugConfig.disableIndexing)
            return;

        // In case startup is called twice in a row without shutdown in between
        if (sSweeper != null && sSweeper.isAlive()) {
            shutdown();
        }
        
        sMaxUncommittedOps = LC.zimbra_index_max_uncommitted_operations.intValue();
        sLRUSize = LC.zimbra_index_lru_size.intValue();
        if (sLRUSize < 10) sLRUSize = 10;
        sIdleWriterFlushTimeMS = 1000 * LC.zimbra_index_idle_flush_time.intValue();
        
        sSweeperFrequencyMs = Constants.MILLIS_PER_SECOND * LC.zimbra_index_sweep_frequency.longValue();
        if (sSweeperFrequencyMs <= 0)
            sSweeperFrequencyMs = Constants.MILLIS_PER_SECOND;
        
        sSweeper = new IndexWritersSweeper();
        sSweeper.start();
        
        sIndexReadersCache = new IndexReadersCache(LC.zimbra_index_reader_lru_size.intValue(), 
            LC.zimbra_index_reader_idle_flush_time.longValue() * 1000, 
            LC.zimbra_index_reader_idle_sweep_frequency.longValue() * 1000);
        sIndexReadersCache.start();
    }

    public static void shutdown() {
        if (DebugConfig.disableIndexing)
            return;

        sSweeper.signalShutdown();
        try {
            sSweeper.join();
        } catch (InterruptedException e) {}
        
        sIndexReadersCache.signalShutdown();
        try {
            sIndexReadersCache.join();
        } catch (InterruptedException e) {}

        flushAllWriters();
    }

    public static void flushAllWriters() {
        if (DebugConfig.disableIndexing)
            return;
        
        List<MailboxIndex> toFlush;
        synchronized(sOpenIndexWriters) {
        	toFlush = new ArrayList<MailboxIndex>(sOpenIndexWriters.size());
        	toFlush.addAll(sOpenIndexWriters.keySet());
        }
        
        for (MailboxIndex idx : toFlush) {
        	idx.closeIndexWriter();	
        }
        
    }

    /**
     * If documents are being constantly added to an index, then it will stay at the front of the LRU cache
     * and will never flush itself to disk: this setting specifies the maximum number of writes we will allow
     * to the index before we force a flush.  Higher values will improve batch-add performance, at the cost
     * of longer-lived transactions in the redolog.
     */
    private static int sMaxUncommittedOps;

    /**
     * How many open indexWriters do we allow?  This value must be >= the # threads in the system, and really 
     * should be a good bit higher than that to lessen thrash.  Ideally this value will never get hit in a
     * "real" system, instead the indexes will be flushed via timeout or # ops -- but this value is here so
     * that the # open file descriptors is controlled.
     */
    static int sLRUSize;

    /**
     * After we add a document to it, how long do we hold an index open for writing before closing it 
     * (and therefore flushing the writes to disk)?
     * 
     * Note that there are other things that might cause us to flush the index to disk -- e.g. if the user
     * does a search on the index, or if the system decides there are too many open IndexWriters (see 
     * sLRUSize) 
     */
    private static long sIdleWriterFlushTimeMS;
    private static IndexReadersCache sIndexReadersCache;

    
    private volatile long mLastWriteTime = 0;
    private Analyzer mAnalyzer = null;
    
    long getLastWriteTime() { return mLastWriteTime; }
    private void updateLastWriteTime() { mLastWriteTime = System.currentTimeMillis(); };

    boolean curThreadHoldsLock() {
        return Thread.holdsLock(getLock());
    }

    /**
     * Load the Analyzer for this index, using the default Zimbra analyzer or a custom user-provided
     * analyzer specified by the key Provisioning.A_zimbraTextAnalyzer
     * 
     * @param mbox
     * @throws ServiceException
     */
    public void initAnalyzer(Mailbox mbox) throws ServiceException {
        // per bug 11052, must always lock the Mailbox before the MailboxIndex, and since
        // mbox.getAccount() is synchronized, we must lock here.
        synchronized (mbox) {
            synchronized (getLock()) {
                String analyzerName = mbox.getAccount().getAttr(Provisioning.A_zimbraTextAnalyzer, null);

                if (analyzerName != null)
                    mAnalyzer = ZimbraAnalyzer.getAnalyzer(analyzerName);
                else
                    mAnalyzer = ZimbraAnalyzer.getDefaultAnalyzer();
            }
        }
    }

    public Analyzer getAnalyzer() {
        synchronized(getLock()) {        
            return mAnalyzer;
        }
    }
    
    private boolean isIndexWriterOpen() {
        mIndexWriterMutex.lock();
        try {
            return mIndexWriter != null;
        } finally {
            mIndexWriterMutex.unlock();
        }
    }

    /**
     * Close the index writer and write commit/abort entries for all
     * pending IndexItem redo operations.
     */
    private void closeIndexWriter() 
    {
        assert(!mIndexWriterMutex.isHeldByCurrentThread());
        int sizeAfter = -1;
        synchronized (sOpenIndexWriters) {
            sReservedWriterSlots++;
            mIndexWriterMutex.lock();
            if (sOpenIndexWriters.remove(this) != null) {
                sizeAfter = sOpenIndexWriters.size();
                ZimbraPerf.COUNTER_IDX_WRT.increment(sizeAfter);                
            }
        }
        
        // only need to do a log output if we actually removed one
        if (sLog.isDebugEnabled() && sizeAfter > -1)
            sLog.debug("closeIndexWriter: map size after close = " + sizeAfter);
        
        try {
            closeIndexWriterAfterRemove();
        } finally {
            assert(mIndexWriterMutex.isHeldByCurrentThread());
            mIndexWriterMutex.unlock();
            synchronized(sOpenIndexWriters) {
                sReservedWriterSlots--;
                assert(mIndexWriter == null);
            }
        }
    }
    
    private void closeIndexWriterAfterRemove() {
        assert(mIndexWriterMutex.isHeldByCurrentThread());

        if (mIndexWriter == null) {
            return;
        }

        if (sLog.isDebugEnabled())
            sLog.debug("Closing IndexWriter " + mIndexWriter + " for " + this);

        IndexWriter writer = mIndexWriter;
        mIndexWriter = null;

        boolean success = true;
        try {
            // Flush all changes to file system before committing redos.
            writer.close();
        } catch (IOException e) {
            success = false;
            sLog.error("Caught Exception " + e + " in MailboxIndex.closeIndexWriter", e);
            // TODO: Is it okay to eat up the exception?
        } finally {
            // Write commit entries to redo log for all IndexItem entries
            // whose changes were written to disk by mIndexWriter.close()
            // above.
            for (Iterator iter = mUncommittedRedoOps.iterator(); iter.hasNext();) {
                IndexItem op = (IndexItem)iter.next();
                if (success) {
                    if (op.commitAllowed())
                        op.commit();
                    else {
                        if (sLog.isDebugEnabled()) {
                            sLog.debug("IndexItem (" + op +
                            ") not allowed to commit yet; attaching to parent operation");
                        }
                        op.attachToParent();
                    }
                } else
                    op.abort();
                iter.remove();
            }
            assert(mUncommittedRedoOps.size() == 0);
        }
    }
    
    /**
     * Check to see if it is OK for us to create an index in the specified 
     * directory.
     * 
     * @param indexDir
     * @return TRUE if the index directory is empty or doesn't exist,
     *             FALSE if the index directory exists and has files in it  
     * @throws IOException
     */
    private boolean indexDirIsEmpty(File indexDir) {
        if (!indexDir.exists()) {
            // dir doesn't even exist yet.  Create the parents and return true
            indexDir.mkdirs();
            return true;
        }
        
        // Empty directory is okay, but a directory with any files
        // implies index corruption.
        File[] files = indexDir.listFiles();
        int numFiles = 0;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String fname = f.getName();
            if (f.isDirectory() && (fname.equals(".") || fname.equals("..")))
                continue;
            numFiles++;
        }
        return (numFiles <= 0);
    }
    
    private void openIndexWriter() throws IOException
    {
        ZimbraPerf.COUNTER_IDX_WRT_OPENED.increment();
        
        assert(Thread.holdsLock(getLock()));
        assert(!mIndexWriterMutex.isHeldByCurrentThread());
        
        // uncache the IndexReader if it is cached
        sIndexReadersCache.removeIndexReader(this);

        //
        // First, get a 'slot' in the cache, closing some other Writer
        // if necessary to do it...
        boolean haveReservedSlot = false;
        boolean mustSleep = false;
        do {
            // Entry cases:
            //       - We might get a slot (or already be in cache) and be done
            //
            //       - We might reserve a slot and go down to close a Writer
            //
            //       - We couldn't find anything to close, so we just did a sleep
            //         and hope to retry again
            //
            //       - We just closed a writer, and we reserved a spot
            //         so we know there will be room this time.
            MailboxIndex toClose;
            synchronized(sOpenIndexWriters) {
                toClose = null;
                mustSleep = false;
                
                if (haveReservedSlot) {
                    sReservedWriterSlots--;
                    haveReservedSlot = false;
                    assert(sOpenIndexWriters.size() + sReservedWriterSlots < sLRUSize);
                }

                if (sOpenIndexWriters.containsKey(this)) {
                    mIndexWriterMutex.lock(); // maintain order in the LinkedHashMap
                    sOpenIndexWriters.remove(this);
                    sOpenIndexWriters.put(this, this);
                } else if (sOpenIndexWriters.size() + sReservedWriterSlots < sLRUSize) {
                    mIndexWriterMutex.lock();
                    sOpenIndexWriters.put(this, this);
                } else {
                    if (!sOpenIndexWriters.isEmpty()) {
                        // find the oldest (first when iterating) entry to remove
                        toClose = sOpenIndexWriters.keySet().iterator().next();
                        sReservedWriterSlots++;
                        haveReservedSlot = true;
                        sOpenIndexWriters.remove(toClose);
                        toClose.mIndexWriterMutex.lock();
                    } else {
                        sLog.info("MI"+this.toString()+"LRU empty and all slots reserved...retrying");
                        mustSleep = true;
                    }
                }
                ZimbraPerf.COUNTER_IDX_WRT.increment(sOpenIndexWriters.size());
            } // synchronized(mOpenIndexWriters)
            
            if (toClose != null) {
                assert(toClose.mIndexWriterMutex.isHeldByCurrentThread());
                assert(!mIndexWriterMutex.isHeldByCurrentThread());
                try {
                    toClose.closeIndexWriterAfterRemove();
                } finally {
                    toClose.mIndexWriterMutex.unlock();
                }
            } else if (mustSleep) {
                assert(!mIndexWriterMutex.isHeldByCurrentThread());
                try { Thread.sleep(100); } catch (Exception e) {};
            }
        } while (haveReservedSlot || mustSleep);
        
        assert(mIndexWriterMutex.isHeldByCurrentThread());
        
        //
        // at this point, we've put ourselves into the writer cache
        // and we have the Write Mutex....so open the file if
        // necessary and return
        //
        try {
            if (mIndexWriter == null) {
                try {
//                  sLog.debug("MI"+this.toString()+" Opening IndexWriter(1) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());
                    mIndexWriter = new IndexWriter(mIdxDirectory, getAnalyzer(), false);
//                  sLog.debug("MI"+this.toString()+" Opened IndexWriter(1) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());

                } catch (IOException e1) {
//                    mLog.debug("****Creating new index in " + mIdxPath + " for mailbox " + mMailboxId);
                    File indexDir  = mIdxDirectory.getFile();
                    if (indexDirIsEmpty(indexDir)) {
//                      sLog.debug("MI"+this.toString()+" Opening IndexWriter(2) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());
                        mIndexWriter = new IndexWriter(mIdxDirectory, getAnalyzer(), true);
//                      sLog.debug("MI"+this.toString()+" Opened IndexWriter(2) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());
                        if (mIndexWriter == null) 
                            throw new IOException("Failed to open IndexWriter in directory "+indexDir.getAbsolutePath());
                    } else {
                        mIndexWriter = null;
                        IOException ioe = new IOException("Could not create index " + mIdxDirectory.toString() + " (directory already exists)");
                        ioe.initCause(e1);
                        throw ioe;
                    }
                }

                ///////////////////////////////////////////////////
                //
                // mergeFactor and minMergeDocs are VERY poorly explained.  Here's the deal:
                //
                // The data is in a tree.  It starts out empty.
                //
                // 1) Whenever docs are added, they are merged into the the smallest node (or a new node) until its 
                //    size reaches "mergeFactor"
                //
                // 2) When we have enough "mergeFactor" sized small nodes so that the total size is "minMergeDocs", then
                //    we combine them into one "minMergeDocs" sized big node.
                //
                // 3) Rule (2) repeats recursively: every time we get "mergeFactor" small nodes, we combine them.
                //
                // 4) This means that every segment (beyond the first "level") is therefore always of size:
                //       minMergeDocs * mergeFactor^N, where N is the # times it has been merged
                //
                // 5) Be careful, (2) implies that we will have (minMergeDocs / mergeFactor) small files!
                //
                // NOTE - usually with lucene, the 1st row of the tree is stored in memory, because you keep 
                // the IndexWriter open for a long time: this dramatically changes the way it performs 
                // because you can make mergeFactor a lot bigger without worrying about the overhead of 
                // re-writing the smallest node over and over again. 
                //
                // In our case, mergeFactor is intentionally chosen to be very small: since we add one document
                // then close the index, if mergeFactor were large it would mean we copied every document
                // (mergeFactor/2) times (start w/ 1 doc, adding the second copies the first b/c it re-writes the
                // file...adding the 3rd copies 1+2....etc)
                //
                // ...in an ideal world, we'd have a separate parameter to control the size of the 1st file and the
                // mergeFactor: then we could have the 1st file be 1 entry and still have a high merge factor.  Doing
                // this we could potentially lower the indexing IO by as much as 70% for our expected usage pattern...  
                // 
                /////////////////////////////////////////////////////


                // tim: these are set based on an expectation of ~25k msgs/mbox and assuming that
                // 11 fragment files are OK.  25k msgs with these settings (mf=3, mmd=33) means that
                // each message gets written 9 times to disk...as opposed to 12.5 times with the default
                // lucene settings of 10 and 100....
                mIndexWriter.setMergeFactor(3);// should be > 1, otherwise segment sizes are effectively limited to
                // minMergeDocs documents: and this is probably bad (too many files!)

                mIndexWriter.setMaxBufferedDocs(33); // we expect 11 index fragment files
            } else {
                ZimbraPerf.COUNTER_IDX_WRT_OPENED_CACHE_HIT.increment();
            }
            // tim: this might seem bad, since an index in steady-state-of-writes will never get flushed, 
            // however we also track the number of uncomitted-operations on the index, and will force a 
            // flush if the index has had a lot written to it without a flush.
            updateLastWriteTime();
        } finally {
            if (mIndexWriter == null) {
                mIndexWriterMutex.unlock();
                assert(!mIndexWriterMutex.isHeldByCurrentThread());
                throw new IOException("Uknown error opening IndexWriter");
            }
        }
    }
    
    /**
     * @return A refcounted IndexReader for this index.  Caller is responsible for 
     *            calling IndexReader.release() on the index before allowing it to go
     *            out of scope (otherwise a RuntimeException will occur)
     * 
     * @throws IOException
     */
    private RefCountedIndexReader getCountedIndexReader() throws IOException
    {
        BooleanQuery.setMaxClauseCount(10000); 

        synchronized(getLock()) {
            RefCountedIndexReader toRet = sIndexReadersCache.getIndexReader(this);
            if (toRet != null)
                return toRet;
            
            IndexReader reader = null;
            try {
                if (isIndexWriterOpen())
                    closeIndexWriter();
                reader = IndexReader.open(mIdxDirectory);
            } catch(IOException e) {
                // Handle the special case of trying to open a not-yet-created
                // index, by opening for write and immediately closing.  Index
                // directory should get initialized as a result.
                File indexDir = mIdxDirectory.getFile();
                if (indexDirIsEmpty(indexDir)) {
                    openIndexWriter();
                    mIndexWriterMutex.unlock();
                    closeIndexWriter();
                    try {
                        reader = IndexReader.open(mIdxDirectory);
                    } catch (IOException e1) {
                        if (reader != null)
                            reader.close();
                        throw e1;
                    }
                } else {
                    if (reader != null)
                        reader.close();
                    throw e;
                }
            }
            
            toRet = new RefCountedIndexReader(reader); // refcount starts at 1 
            sIndexReadersCache.putIndexReader(this, toRet); // addrefs if put in cache
            return toRet; 
        }
    }
    
    /**
     * @return A refcounted RefCountedIndexSearcher for this index.  Caller is responsible for 
     *            calling RefCountedIndexReader.release() on the index before allowing it to go
     *            out of scope (otherwise a RuntimeException will occur)
     * 
     * @throws IOException
     */
    RefCountedIndexSearcher getCountedIndexSearcher() throws IOException
    {
        synchronized(getLock()) {        
            RefCountedIndexSearcher searcher = null;
            RefCountedIndexReader cReader = getCountedIndexReader();
            searcher = new RefCountedIndexSearcher(cReader);
            return searcher;
        }
    }



    /******************************************************************************
     *
     *  Index Search Results
     *  
     ********************************************************************************/
    // What level of result grouping do we want?  ConversationResult, MessageResult, or DocumentResult?
    public static final int FIRST_SEARCH_RETURN_NUM = 1;
    public static final int SEARCH_RETURN_CONVERSATIONS = 1;
    public static final int SEARCH_RETURN_MESSAGES      = 2;
    public static final int SEARCH_RETURN_DOCUMENTS     = 3;
    public static final int LAST_SEARCH_RETURN_NUM = 3;

    public static final String GROUP_BY_CONVERSATION = "conversation";
    public static final String GROUP_BY_MESSAGE      = "message";
    public static final String GROUP_BY_NONE         = "none";

    public static final String SEARCH_FOR_APPOINTMENTS = "appointment";
    public static final String SEARCH_FOR_CONTACTS = "contact";
    public static final String SEARCH_FOR_CONVERSATIONS = "conversation";
    public static final String SEARCH_FOR_DOCUMENTS = "document";
    public static final String SEARCH_FOR_MESSAGES = "message";
    public static final String SEARCH_FOR_NOTES = "note";
    public static final String SEARCH_FOR_TASKS = "task";
    public static final String SEARCH_FOR_WIKI = "wiki";

    public static final String SEARCH_FOR_EVERYTHING = SEARCH_FOR_APPOINTMENTS + ',' + SEARCH_FOR_CONTACTS + ',' +
                                                       SEARCH_FOR_DOCUMENTS + ',' + SEARCH_FOR_MESSAGES + ',' +
                                                       SEARCH_FOR_NOTES + ',' + SEARCH_FOR_TASKS + ',' +
                                                       SEARCH_FOR_WIKI;

    public static enum SortBy {
        DATE_ASCENDING  ("dateAsc",  (byte) (DbMailItem.SORT_BY_DATE | DbMailItem.SORT_ASCENDING)), 
        DATE_DESCENDING ("dateDesc", (byte) (DbMailItem.SORT_BY_DATE | DbMailItem.SORT_DESCENDING)),
        SUBJ_ASCENDING  ("subjAsc",  (byte) (DbMailItem.SORT_BY_SUBJECT | DbMailItem.SORT_ASCENDING)),
        SUBJ_DESCENDING ("subjDesc", (byte) (DbMailItem.SORT_BY_SUBJECT | DbMailItem.SORT_DESCENDING)),
        NAME_ASCENDING  ("nameAsc",  (byte) (DbMailItem.SORT_BY_SENDER | DbMailItem.SORT_ASCENDING)),
        NAME_DESCENDING ("nameDesc", (byte) (DbMailItem.SORT_BY_SENDER | DbMailItem.SORT_DESCENDING)),
        SCORE_DESCENDING("score",    (byte) 0),

        // special TASK-only sorts
        TASK_DUE_ASCENDING("taskDueAsc", (byte)0),
        TASK_DUE_DESCENDING("taskDueDesc", (byte)0),
        TASK_STATUS_ASCENDING("taskStatusAsc", (byte)0),
        TASK_STATUS_DESCENDING("taskStatusDesc", (byte)0),
        TASK_PERCENT_COMPLETE_ASCENDING("taskPercCompletedAsc", (byte)0),
        TASK_PERCENT_COMPLETE_DESCENDING("taskPercCompletedDesc", (byte)0),
        ;

        static HashMap<String, SortBy> sNameMap = new HashMap<String, SortBy>();

        static {
            for (SortBy s : SortBy.values()) 
                sNameMap.put(s.mName.toLowerCase(), s);
        }

        byte mSort;
        String mName;

        SortBy(String str, byte sort) {
            mName = str;
            mSort = sort;
        }

        public String toString() { return mName; }

        public byte getDbMailItemSortByte() {
            return mSort;
        }

        public boolean isDescending() {
            return (mSort & DbMailItem.SORT_ASCENDING) == 0;
        }

        public static SortBy lookup(String str) {
            if (str != null)
                return sNameMap.get(str.toLowerCase());
            else
                return null;
        }
    }

    public static byte[] parseTypesString(String groupBy) throws ServiceException
    {
        String[] strs = groupBy.split("\\s*,\\s*");

        byte[] types = new byte[strs.length]; 
        for (int i = 0; i < strs.length; i++) {
            if (SEARCH_FOR_CONVERSATIONS.equals(strs[i])) {
                types[i] = MailItem.TYPE_CONVERSATION;
            } else if (SEARCH_FOR_MESSAGES.equals(strs[i])) {
                types[i] = MailItem.TYPE_MESSAGE;
            } else if (GROUP_BY_NONE.equals(strs[i])) {
                types[i] = 0;
            } else if (SEARCH_FOR_CONTACTS.equals(strs[i])) {
                types[i] = MailItem.TYPE_CONTACT;
            } else if (SEARCH_FOR_APPOINTMENTS.equals(strs[i])) {
                types[i] = MailItem.TYPE_APPOINTMENT;
            } else if (SEARCH_FOR_TASKS.equals(strs[i])) {
                types[i] = MailItem.TYPE_TASK;
            } else if (SEARCH_FOR_NOTES.equals(strs[i])) {
                types[i] = MailItem.TYPE_NOTE;
            } else if (SEARCH_FOR_WIKI.equals(strs[i])) {
                types[i] = MailItem.TYPE_WIKI;
            } else if (SEARCH_FOR_DOCUMENTS.equals(strs[i])) {
                types[i] = MailItem.TYPE_DOCUMENT;
            } else 
                throw ServiceException.INVALID_REQUEST("unknown groupBy: "+strs[i], null);
        }

        return types;
    }

    protected Spans getSpans(SpanQuery q) throws IOException {
        synchronized(getLock()) {        
            RefCountedIndexReader reader = this.getCountedIndexReader();
            try {
                IndexReader iReader = reader.getReader();
                return q.getSpans(iReader);
            } finally {
                reader.release();
            }
        }
    }


    interface TermEnumInterface {
        abstract void onTerm(Term term, int docFreq); 
    }
    static abstract class DocEnumInterface {
        void maxDocNo(int num) {};
        abstract boolean onDocument(Document doc, boolean isDeleted);
    }

    private static class ChkIndexStage1Callback implements TermEnumInterface 
    {
        HashSet msgsInMailbox = new HashSet(); // hash of all messages in my mailbox
        private MailboxIndex idx = null;
        private ArrayList<Integer> toDelete = new ArrayList<Integer>(); // to be deleted from index
        DbMailItem.SearchResult compareTo = new DbMailItem.SearchResult();  

        ChkIndexStage1Callback(MailboxIndex idx) {
            this.idx = idx;
        }

        void doIndexRepair() throws IOException 
        {
            
            Mailbox mbox = null;
            try {
                mbox = MailboxManager.getInstance().getMailboxById(idx.mMailboxId);
            } catch (ServiceException e) {
                sLog.error("Could not get mailbox: "+idx.mMailboxId+" aborting index repair");
                return;
            }
                
            // delete first -- that way if there were any re-indexes along the way we know we're OK
            if (toDelete.size() > 0) {
                sLog.info("There are "+toDelete.size()+" items to delete");
                int ids[] = new int[toDelete.size()];
                for (int i = 0; i < toDelete.size(); i++) {
                    ids[i] = ((Integer)(toDelete.get(i))).intValue();
                }
                idx.deleteDocuments(ids);
            }
            
            
            // if there any messages left in this list, then they are missing from the index and 
            // we should try to reindex them
            if (msgsInMailbox.size() > 0)
            {
                sLog.info("There are "+msgsInMailbox.size() + " msgs to be re-indexed");
                for (Iterator iter = msgsInMailbox.iterator(); iter.hasNext();) {
                    DbMailItem.SearchResult cur = (DbMailItem.SearchResult)iter.next();

                    try {
                        MailItem item = mbox.getItemById(null, cur.id, cur.type);
                        item.reindex(null, false /* already deleted above */, null);
                    } catch(ServiceException  e) {
                        sLog.info("Couldn't index "+compareTo.id+" caught ServiceException", e);
                    } catch(java.lang.RuntimeException e) {
                        sLog.info("Couldn't index "+compareTo.id+" caught ServiceException", e);
                    }
                }
            }
        }

        public void onTerm(Term term, int docFreq) 
        {
            compareTo.id = Integer.parseInt(term.text());

            if (!msgsInMailbox.contains(compareTo)) {
                sLog.info("In index but not DB: "+compareTo.id);
                toDelete.add(new Integer(compareTo.id));
            } else {
                // remove from the msgsInMailbox hash.  If there are still entries in this
                // table, then it means that there are items in the mailbox, but not in the index
                msgsInMailbox.remove(compareTo);
            }
        }
    }

    private static class ChkIndexStage2Callback 
    {
        public List msgsInMailbox = new LinkedList(); // hash of all messages in my mailbox
        private ListIterator msgsIter;

        private String mSortField;
        DbMailItem.SearchResult mCur = null;


        ChkIndexStage2Callback(MailboxIndex idx, String sortField, boolean reversed) {
            mSortField = sortField;
            this.reversed = reversed;
        }

        boolean beginIterating() {
            msgsIter = msgsInMailbox.listIterator();
            mCur = (DbMailItem.SearchResult)msgsIter.next();
            return (mCur!= null);
        }

        boolean reversed = false;

        long compare(long lhs, long rhs) {
            if (!reversed) {
                return (lhs - rhs);
            } else {
                return (rhs - lhs);
            }
        }

        void onDocument(Document doc) 
        {
            int idxId = Integer.parseInt(doc.get(LuceneFields.L_MAILBOX_BLOB_ID));

            String sortField = doc.get(mSortField);
            String partName = doc.get(LuceneFields.L_PARTNAME);
            String dateStr = doc.get(LuceneFields.L_SORT_DATE);
            long docDate = DateField.stringToTime(dateStr);
            // fix for Bug 311 -- SQL truncates dates when it stores them
            long truncDocDate = (docDate /1000) * 1000;

            retry: do {
                long curMsgDate = ((Long)(mCur.sortkey)).longValue();


                if (mCur.id == idxId) {
                    // next part same doc....good.  keep going..
                    if (curMsgDate != truncDocDate) {
                        sLog.info("WARN  : DB has "+mCur.id+" (sk="+mCur.sortkey+") next and Index has "+idxId+
                                    " "+"mbid="+idxId+" part="+partName+" date="+docDate+" truncDate="+truncDocDate+
                                    " "+mSortField+"="+sortField);

                        sLog.info("\tWARNING: DB-DATE doesn't match TRUNCDATE!");
                    } else {
                        sLog.debug("OK    : DB has "+mCur.id+" (sk="+mCur.sortkey+") next and Index has "+idxId+
                                    " "+"mbid="+idxId+" part="+partName+" date="+docDate+" truncDate="+truncDocDate+
                                    " "+mSortField+"="+sortField);
                    }
                    return;
                } else {
                    if (false) {
//                      if (!msgsIter.hasNext()) {
//                      if (mMissingTerms == null) {
//                      mLog.info("ERROR: end of msgIter while still iterating index");
//                      }
//                      mLog.info("ERROR: DB no results INDEX has mailitem: "+idxId);
//                      return;
                    } else {
                        // 3 possibilities:
                        //    doc < cur
                        //    doc > cur
                        //    doc == cur
//                      if (truncDocDate < curMsgDate) { // case 1
                        if (compare(truncDocDate,curMsgDate) < 0) { // case 1
                            sLog.info("ERROR1: DB has "+mCur.id+" (sk="+mCur.sortkey+") next and Index has "+idxId+
                                        " "+"mbid="+idxId+" part="+partName+" date="+docDate+" truncDate="+truncDocDate+
                                        " "+mSortField+"="+sortField);

                            // move on to next document
                            return;
//                          } else if (truncDocDate > curMsgDate) { // case 2
                        } else if (compare(truncDocDate,curMsgDate)>0) { // case 2
//                          mLog.info("ERROR2: DB has "+mCur.id+" (sk="+mCur.sortkey+") next and Index has "+idxId+
//                          " "+"mbid="+idxId+" part="+partName+" date="+docDate+" truncDate="+truncDocDate+
//                          " "+mSortField+"="+sortField);

                            if (!msgsIter.hasNext()) {
                                sLog.info("ERROR4: DB no results INDEX has mailitem: "+idxId);
                                return;
                            }
                            mCur = (DbMailItem.SearchResult)msgsIter.next();

                            continue; // try again!
                        } else { // same date!
                            // 1st,look backwards for a match
                            if (msgsIter.hasPrevious()) {
                                do {
                                    mCur = (DbMailItem.SearchResult)msgsIter.previous();
                                    if (mCur.id == idxId) {
                                        continue retry;
                                    }
                                    curMsgDate = ((Long)(mCur.sortkey)).longValue();
                                } while(msgsIter.hasPrevious() && curMsgDate == truncDocDate);

                                // Move the iterator fwd one, so it is on the correct time...
                                mCur = (DbMailItem.SearchResult)msgsIter.next();

                            }

                            // now, look fwd.  Sure, we might check some twice here.  Oh well
                            if (msgsIter.hasNext()) {
                                do {
                                    mCur = (DbMailItem.SearchResult)msgsIter.next();
                                    if (mCur.id == idxId) {
                                        continue retry;
                                    }
                                    curMsgDate = ((Long)(mCur.sortkey)).longValue();
                                } while (msgsIter.hasNext() && curMsgDate == truncDocDate);

                                // Move the iterator back one, so it is on the correct time...
                                mCur = (DbMailItem.SearchResult)msgsIter.previous();
                            }


                            sLog.info("ERROR3: DB has "+mCur.id+" (sk="+mCur.sortkey+") next and Index has "+idxId+
                                        " "+"mbid="+idxId+" part="+partName+" date="+docDate+" truncDate="+truncDocDate+
                                        " "+mSortField+"="+sortField);
                            return;
                        } // big if...else
                    } // has a next
                } // else if IDs don't mtch
            } while(true);
        } // func

    } // class

    void chkIndex(boolean repair) throws ServiceException 
    {
        synchronized(getLock()) {        
            flush();

            Connection conn = null;
            conn = DbPool.getConnection();
            Mailbox mbox = MailboxManager.getInstance().getMailboxById(mMailboxId);


            ///////////////////////////////
            //
            // Stage 1 -- look for missing or extra messages and reindex/delete as necessary.
            //
            {
                DbSearchConstraints c = new DbSearchConstraints();

                c.mailbox = mbox;
                c.sort = DbMailItem.SORT_BY_DATE;
                c.types = new HashSet<Byte>();
                c.types.add(MailItem.TYPE_CONTACT); 
                c.types.add(MailItem.TYPE_MESSAGE);
                c.types.add(MailItem.TYPE_NOTE);

                ChkIndexStage1Callback callback = new ChkIndexStage1Callback(this);

                DbMailItem.search(callback.msgsInMailbox, conn, c);
                sLog.info("Verifying (repair="+(repair?"TRUE":"FALSE")+") Index for Mailbox "+this.mMailboxId+" with "+callback.msgsInMailbox.size()+" items.");

                try {
                    this.enumerateTermsForField(new Term(LuceneFields.L_MAILBOX_BLOB_ID, ""), callback);
                } catch (IOException e) {
                    throw ServiceException.FAILURE("Caught IOException while enumerating fields", e);
                }

                sLog.info("Stage 1 Verification complete for Mailbox "+this.mMailboxId);

                if (repair) {
                    try {
                        sLog.info("Attempting Stage 1 Repair for mailbox "+this.mMailboxId);
                        callback.doIndexRepair();
                    } catch (IOException e) {
                        throw ServiceException.FAILURE("Caught IOException while repairing index", e);
                    }
                    flush();
                }
            }

            /////////////////////////////////
            //
            // Stage 2 -- verify SORT_BY_DATE orders match up
            //
            {
                sLog.info("Stage 2 Verify SORT_DATE_ASCENDNIG for Mailbox "+this.mMailboxId);

                // SORT_BY__DATE_ASC
                DbSearchConstraints c = new DbSearchConstraints();

                c.mailbox = mbox;
                c.sort = DbMailItem.SORT_BY_DATE | DbMailItem.SORT_ASCENDING;
                c.types = new HashSet<Byte>();
                c.types.add(MailItem.TYPE_CONTACT); 
                c.types.add(MailItem.TYPE_MESSAGE);
                c.types.add(MailItem.TYPE_NOTE);

                String lucSortField = LuceneFields.L_SORT_DATE;

                ChkIndexStage2Callback callback = new ChkIndexStage2Callback(this, lucSortField, false);

                DbMailItem.search(callback.msgsInMailbox, conn, c);
                RefCountedIndexSearcher searcher = null;
                try {
                    callback.beginIterating();
                    searcher = getCountedIndexSearcher();

                    TermQuery q = new TermQuery(new Term(LuceneFields.L_ALL, LuceneFields.L_ALL_VALUE));
                    Hits luceneHits = searcher.getSearcher().search(q, getSort(SortBy.DATE_ASCENDING));

                    for (int i = 0; i < luceneHits.length(); i++) {
                        callback.onDocument(luceneHits.doc(i));
                    }
                } catch (IOException e) {
                    throw ServiceException.FAILURE("Caught IOException while enumerating fields", e);
                } finally {
                    if (searcher != null) {
                        searcher.release();
                    }
                }

                sLog.info("Stage 2 Verification complete for Mailbox "+this.mMailboxId);

            }

            /////////////////////////////////
            //
            // Stage 3 -- verify SORT_BY_DATE orders match up
            //
            {
                sLog.info("Stage 3 Verify SORT_DATE_DESCENDING for Mailbox "+this.mMailboxId);

                // SORT_BY__DATE_DESC
                DbSearchConstraints c = new DbSearchConstraints();

                c.mailbox = mbox;
                c.sort = DbMailItem.SORT_BY_DATE | DbMailItem.SORT_DESCENDING;

                c.types = new HashSet<Byte>();
                c.types.add(MailItem.TYPE_CONTACT); 
                c.types.add(MailItem.TYPE_MESSAGE);
                c.types.add(MailItem.TYPE_NOTE);


                String lucSortField = LuceneFields.L_SORT_DATE;

                ChkIndexStage2Callback callback = new ChkIndexStage2Callback(this, lucSortField, true);

                DbMailItem.search(callback.msgsInMailbox, conn, c);
                RefCountedIndexSearcher searcher = null;
                try {
                    callback.beginIterating();
                    searcher = getCountedIndexSearcher();

                    TermQuery q = new TermQuery(new Term(LuceneFields.L_ALL, LuceneFields.L_ALL_VALUE));
                    Hits luceneHits = searcher.getSearcher().search(q, getSort(SortBy.DATE_DESCENDING));

                    for (int i = 0; i < luceneHits.length(); i++) {
                        callback.onDocument(luceneHits.doc(i));
                    }
                } catch (IOException e) {
                    throw ServiceException.FAILURE("Caught IOException while enumerating fields", e);
                } finally {
                    if (searcher != null) {
                        searcher.release();
                    }
                }

                sLog.info("Stage 3 Verification complete for Mailbox "+this.mMailboxId);
            }
        }
    }

    public void deleteIndex() throws IOException
    {
        synchronized(getLock()) {        
            IndexWriter writer = null;
            try {
                flush();
                // FIXME maybe: under Windows only, this can fail.  Might need way to forcibly close all open indices???
                //				closeIndexReader();
                if (sLog.isDebugEnabled())
                    sLog.debug("****Deleting index " + mIdxDirectory.toString());

                // can use default analyzer here since it is easier, and since we aren't actually
                // going to do any indexing...
//                sLog.info("MI"+this.toString()+" Opening IndexWriter(3) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());
                writer = new IndexWriter(mIdxDirectory, ZimbraAnalyzer.getDefaultAnalyzer(), true);
//                sLog.info("MI"+this.toString()+" Opened IndexWriter(3) "+ writer+" for "+this+" dir="+mIdxDirectory.toString());
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    protected int countTermOccurences(String fieldName, String term) throws IOException {
        RefCountedIndexReader reader = getCountedIndexReader();
        try {
            TermEnum e = reader.getReader().terms(new Term(fieldName, term));
            return e.docFreq();
        } finally {
            reader.release();
        }
    }

    public AdminInterface getAdminInterface() {
        return new AdminInterface(this); 
    }

    public static class AdminInterface {
        MailboxIndex mIdx;
        private AdminInterface(MailboxIndex idx) {
            mIdx = idx;
        }
        void close() { };

        public synchronized Spans getSpans(SpanQuery q) throws IOException {
            return mIdx.getSpans(q);
        }

        void deleteIndex() {
            try {
                mIdx.deleteIndex();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        public static class TermInfo {
            /* (non-Javadoc)
             * @see java.lang.Object#equals(java.lang.Object)
             */
            public Term mTerm;
            public int mFreq; 

            public static class FreqComparator implements Comparator 
            {
                public int compare(Object o1, Object o2) {
                    TermInfo lhs = (TermInfo)o1;
                    TermInfo rhs = (TermInfo)o2;

                    if (lhs.mFreq != rhs.mFreq) {
                        return lhs.mFreq - rhs.mFreq;
                    } else {
                        return lhs.mTerm.text().compareTo(rhs.mTerm.text());
                    }
                }
            }
        }

        private static class TermEnumCallback implements MailboxIndex.TermEnumInterface {
            private Collection<TermInfo> mCollection;
            
            TermEnumCallback(Collection<TermInfo> collection) {
                mCollection = collection;
            }
            public void onTerm(Term term, int docFreq) {
                if (term != null) {
                    TermInfo info = new TermInfo();
                    info.mTerm = term;
                    info.mFreq = docFreq;
                    mCollection.add(info);
                }
            }
        }

        public void enumerateTerms(Collection<TermInfo>collection, String field) throws IOException {
            TermEnumCallback cb = new TermEnumCallback(collection);
            mIdx.enumerateTermsForField(new Term(field,""), cb);
        }

        public int numDocs() throws IOException {
            return mIdx.numDocs();
        }

        public int countTermOccurences(String fieldName, String term) throws IOException {
            return mIdx.countTermOccurences(fieldName, term);
        }
    }


   
    /**
     * Entry point for Redo-logging system only.  Everybody else should use MailItem.reindex()
     * 
     * @throws ServiceException
     */
    public void redoIndexItem(Mailbox mbox, boolean deleteFirst, int itemId, byte itemType, long timestamp, boolean noRedo)
    throws IOException, ServiceException {
        MailItem item;
        try {
            item = mbox.getItemById(null, itemId, itemType);
        } catch (MailServiceException.NoSuchItemException e) {
            // Because index commits are batched, during mailbox restore
            // it's possible to see the commit record of indexing operation
            // after the delete operation on the item being indexed.
            // (delete followed by edit, for example)
            // We can't distinguish this legitimate case from a case of
            // really missing the item being indexed due to unexpected
            // problem.  So just ignore the NoSuchItemException.
            return;
        }

        IndexItem redo = null;
        if (!noRedo) {
            redo = new IndexItem(mbox.getId(), item.getId(), itemType, deleteFirst);
            redo.start(System.currentTimeMillis());
            redo.log();
            redo.allowCommit();
        }
        switch (itemType) {
            case MailItem.TYPE_APPOINTMENT:
            case MailItem.TYPE_TASK:
                CalendarItem ci = (CalendarItem)item;
                ci.reindex(redo, deleteFirst, null);
                break;
            case MailItem.TYPE_DOCUMENT:
            case MailItem.TYPE_WIKI:
                try {
                    com.zimbra.cs.mailbox.Document document = (com.zimbra.cs.mailbox.Document) item;
                    ParsedDocument pd = new ParsedDocument(document.getBlob(),
                                document.getName(), 
                                document.getContentType(),
                                timestamp,
                                document.getCreator());
                    indexDocument(mbox, redo, deleteFirst, pd, document);
                } catch (IOException ioe) {
                    throw ServiceException.FAILURE("indexDocument caught Exception", ioe);
                }
                break;
            case MailItem.TYPE_CHAT:
            case MailItem.TYPE_MESSAGE:
                Message msg = (Message) item;
                InputStream is = msg.getContentStream();
                MimeMessage mm;
                try {
                    mm = new Mime.FixedMimeMessage(JMSession.getSession(), is);
                    ParsedMessage pm = new ParsedMessage(mm, timestamp, mbox.attachmentsIndexingEnabled());
                    indexMessage(mbox, redo, deleteFirst, pm, msg);
                } catch (Throwable t) {
                    sLog.warn("Skipping indexing; Unable to parse message " + itemId + ": " + t.toString(), t);
                    // Eat up all errors during message analysis.  Throwing
                    // anything here will force server halt during crash
                    // recovery.  Because we can't possibly predict all
                    // data-dependent message parse problems, we opt to live
                    // with unindexed messages rather than P1 support calls.

                    // Write abort record for this item, to prevent repeat calls
                    // to index this unindexable item.
                    if (redo != null)
                        redo.abort();
                } finally {
                    is.close();
                }
                break;
            case MailItem.TYPE_CONTACT:
                indexContact(mbox, redo, deleteFirst, (Contact) item);
                break;
            case MailItem.TYPE_NOTE:
                indexNote(mbox, redo, deleteFirst, (Note) item);
                break;
            default:
                if (redo != null)
                    redo.abort();
                throw ServiceException.FAILURE("Invalid item type for indexing: type=" + itemType, null);
        }
    }
    
    public void indexCalendarItem(Mailbox mbox, IndexItem redo, boolean deleteFirst, 
        CalendarItem item, List<Document> docList, long date) throws ServiceException {
        
        initAnalyzer(mbox);
        synchronized(getLock()) {
            int indexId = item.getIndexId();
            
            try {
                if (docList != null) {
                    Document[] docs = new Document[docList.size()];
                    docs = docList.toArray(docs);
                    addDocument(redo, docs, indexId, date, item, deleteFirst);
                }
            } catch (IOException e) {
                throw ServiceException.FAILURE("indexMessage caught IOException", e);
            }
        }
    }
    
    /**
     * Index a message in the specified mailbox.
     * @param mailboxId
     * @param messageId
     * @param pm
     * @throws ServiceException
     */
    public void indexMessage(Mailbox mbox, IndexItem redo, boolean deleteFirst, ParsedMessage pm, Message msg)
    throws ServiceException {
        initAnalyzer(mbox);
        synchronized(getLock()) {
            int indexId = msg.getIndexId();

            try {
                List<Document> docList = pm.getLuceneDocuments();
                if (docList != null) {
                    Document[] docs = new Document[docList.size()];
                    docs = docList.toArray(docs);
                    addDocument(redo, docs, indexId, pm.getReceivedDate(), msg, deleteFirst);
                }
            } catch (IOException e) {
                throw ServiceException.FAILURE("indexMessage caught IOException", e);
            }
        }
    }

    private static void appendContactField(StringBuilder sb, Contact contact, String fieldName) {
        String s = contact.get(fieldName);
        if (s!= null) {
            sb.append(s).append(' ');
        }
    }

    /**
     * Index a Contact in the specified mailbox.
     * @param deleteFirst if TRUE then we must delete the existing index records before we index
     * @param mailItemId
     * @param contact
     * @throws ServiceException
     */
    public void indexContact(Mailbox mbox, IndexItem redo, boolean deleteFirst, Contact contact) throws ServiceException {
        initAnalyzer(mbox);
        synchronized(getLock()) {        
            if (sLog.isDebugEnabled()) {
                sLog.debug("indexContact("+contact+")");
            }
            try {
                int indexId = contact.getIndexId();
                
                StringBuffer contentText = new StringBuffer();
                Map m = contact.getFields();
                for (Iterator it = m.values().iterator(); it.hasNext(); )
                {
                    String cur = (String)it.next();

                    contentText.append(cur);
                    contentText.append(' ');
                }

                Document doc = new Document();

                StringBuilder searchText = new StringBuilder();
                appendContactField(searchText, contact, Contact.A_company);
                appendContactField(searchText, contact, Contact.A_firstName);
                appendContactField(searchText, contact, Contact.A_lastName);
                appendContactField(searchText, contact, Contact.A_nickname);

                StringBuilder emailStrBuf = new StringBuilder();
                List<String> emailList = contact.getEmailAddresses();
                for (String cur : emailList) {
                    emailStrBuf.append(cur).append(' ');
                }

                String emailStr = emailStrBuf.toString();

                contentText.append(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_TO, emailStr));
                searchText.append(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_TO, emailStr));
                
                /* put the email addresses in the "To" field so they can be more easily searched */
                doc.add(new Field(LuceneFields.L_H_TO, emailStr,  Field.Store.NO, Field.Index.TOKENIZED));
                
                /* put the name in the "From" field since the MailItem table uses 'Sender'*/
                doc.add(new Field(LuceneFields.L_H_FROM, contact.getSender(),  Field.Store.NO, Field.Index.TOKENIZED));
                /* bug 11831 - put contact searchable data in its own field so wildcard search works better  */
                doc.add(new Field(LuceneFields.L_CONTACT_DATA, searchText.toString(), Field.Store.NO, Field.Index.TOKENIZED));
                doc.add(new Field(LuceneFields.L_CONTENT, contentText.toString(), Field.Store.NO, Field.Index.TOKENIZED));
                doc.add(new Field(LuceneFields.L_H_SUBJECT, contact.getSubject(), Field.Store.NO, Field.Index.TOKENIZED));
                doc.add(new Field(LuceneFields.L_PARTNAME, LuceneFields.L_PARTNAME_CONTACT, Field.Store.YES, Field.Index.UN_TOKENIZED));
                
                addDocument(redo, doc, indexId, contact.getDate(), contact, deleteFirst);

            } catch (IOException ioe) {
                throw ServiceException.FAILURE("indexContact caught IOException", ioe);
            }
        }        
    }    


    /**
     * Index a Note in the specified mailbox.
     * 
     * @throws ServiceException
     */
    public void indexNote(Mailbox mbox, IndexItem redo, boolean deleteFirst, Note note)
    throws ServiceException {
        initAnalyzer(mbox);
        synchronized(getLock()) {        
            if (sLog.isDebugEnabled()) {
                sLog.debug("indexNote("+note+")");
            }
            try {
                String toIndex = note.getText();
                int indexId = note.getIndexId(); 

                if (sLog.isDebugEnabled()) {
                    sLog.debug("Note value=\""+toIndex+"\"");
                }

                Document doc = new Document();
                doc.add(new Field(LuceneFields.L_CONTENT, toIndex, Field.Store.NO, Field.Index.TOKENIZED));
                doc.add(new Field(LuceneFields.L_H_SUBJECT, toIndex, Field.Store.NO, Field.Index.TOKENIZED));
                doc.add(new Field(LuceneFields.L_PARTNAME, LuceneFields.L_PARTNAME_NOTE, Field.Store.YES, Field.Index.UN_TOKENIZED));
                addDocument(redo, doc, indexId, note.getDate(), note, deleteFirst);

            } catch (IOException e) {
                throw ServiceException.FAILURE("indexNote caught IOException", e);
            }
        }
    }    

    public void indexDocument(Mailbox mbox, IndexItem redo, boolean deleteFirst, 
        ParsedDocument pd, com.zimbra.cs.mailbox.Document doc)  throws ServiceException {
        initAnalyzer(mbox);
        synchronized(getLock()) {        
            try {
                int indexId = doc.getIndexId();
                addDocument(redo, pd.getDocument(), indexId, pd.getCreatedDate(), doc, deleteFirst);
            } catch (IOException e) {
                throw ServiceException.FAILURE("indexDocument caught Exception", e);
            }
        }
    }
    
    private final Object getLock() {
        if (sNewLockModel)
            return mMailbox;
        else
            return this;
    }
    
    private static final class IndexWritersSweeper extends Thread {
        
        private boolean mShutdown = false;
        
        public IndexWritersSweeper() {
            super("IndexWritersSweeperThread");
        }

        /**
         * Shutdown the sweeper thread
         */
        synchronized void signalShutdown() {
            mShutdown = true;
            notify();
        }
        
        /**
         * Main loop for the Sweeper thread.  This thread does a sweep automatically
         * every (mSweepIntervalMS) ms, or it will run a sweep when woken up
         * bia the wakeupSweeperThread() API
         */
        public void run() {
            sLog.info(getName() + " thread starting");

            boolean shutdown = false;
            long startTime = System.currentTimeMillis();

            while (!shutdown) {
                // Sleep until next scheduled wake-up time, or until notified.
                synchronized (this) {
                    if (!mShutdown) {  // Don't go into wait() if shutting down.  (bug 1962)
                        long now = System.currentTimeMillis();
                        long until = startTime + sSweeperFrequencyMs;
                        if (until > now) {
                            try {
                                wait(until - now);
                            } catch (InterruptedException e) {}
                        }
                    }
                    shutdown = mShutdown;
                }

                startTime = System.currentTimeMillis();
                

                // Flush out index writers that have been idle too long.
                MailboxIndex toRemove = null;
                int removed = 0;
                int sizeAfter = 0;
                int sizeBefore = -1;
                do {
                    try {
                        synchronized (sOpenIndexWriters) {
                            if (sizeBefore == -1)
                                sizeBefore = sOpenIndexWriters.size();
                            toRemove = null;
                            long cutoffTime = startTime - sIdleWriterFlushTimeMS;
                            for (Iterator it = sOpenIndexWriters.entrySet().iterator(); toRemove==null && it.hasNext(); ) {
                                Map.Entry entry = (Map.Entry) it.next();
                                MailboxIndex mi = (MailboxIndex) entry.getKey();
                                if (mi.getLastWriteTime() < cutoffTime) {
                                    removed++;
                                    toRemove = mi;
                                    it.remove();
                                    toRemove.mIndexWriterMutex.lock();
                                    sReservedWriterSlots++;
                                }
                            }
                            sizeAfter = sOpenIndexWriters.size();
                            ZimbraPerf.COUNTER_IDX_WRT.increment(sizeAfter);
                        }
                        if (toRemove != null) {
                            try {
                                toRemove.closeIndexWriterAfterRemove();
                            } finally {
                                toRemove.mIndexWriterMutex.unlock();
                                assert(!toRemove.mIndexWriterMutex.isHeldByCurrentThread());
                                synchronized(sOpenIndexWriters) {
                                    sReservedWriterSlots--;
                                    assert(toRemove.mIndexWriter == null);
                                }
                            }
                        }
                    } finally {
                        if (toRemove != null && toRemove.mIndexWriterMutex.isHeldByCurrentThread()) {
                            sLog.error("Error: sweeper still holding mutex for %s at end of cycle!", toRemove.mIndexWriter);
                            assert(false);
                            toRemove.mIndexWriterMutex.unlock();
                        }
                    }
                    synchronized(this) {
                        if (mShutdown)
                            shutdown = true;
                    }
                } while (!shutdown && toRemove != null);
                
                long elapsed = System.currentTimeMillis() - startTime;
                
                if (removed > 0 || sizeAfter > 0)
                    sLog.info("open index writers sweep: before=" + sizeBefore +
                                ", closed=" + removed +
                                ", after=" + sizeAfter + " (" + elapsed + "ms)");
            }

            sLog.info(getName() + " thread exiting");
        }
    }
}
