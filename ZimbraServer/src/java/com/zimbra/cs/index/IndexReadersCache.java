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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

/**
 * Self-sweeping (with it's own sweeper thread) LRU cache of open index readers 
 */
class IndexReadersCache extends Thread {
    private static Log sLog = LogFactory.getLog(IndexReadersCache.class);

    private final int mMaxOpenReaders;
    private LinkedHashMap<MailboxIndex,RefCountedIndexReader> mOpenIndexReaders;
    private boolean mShutdown;
    private long mSweepIntervalMS;
    private long mMaxReaderOpenTimeMS;
    
    IndexReadersCache(int maxOpenReaders, long maxReaderOpenTime, long sweepIntervalMS) {
        super("IndexReadersCache-Sweeper");
        if (maxReaderOpenTime < 0)
            maxReaderOpenTime = 0;
        if (sweepIntervalMS < 100)
            sweepIntervalMS = 100;
        mMaxReaderOpenTimeMS = maxReaderOpenTime;
        mMaxOpenReaders = maxOpenReaders;
        mOpenIndexReaders = new LinkedHashMap<MailboxIndex,RefCountedIndexReader>(mMaxOpenReaders);
        mShutdown = false;
        mSweepIntervalMS = sweepIntervalMS;
    }
    
    /**
     * Shut down the sweeper thread and clear the cached
     */
    synchronized void signalShutdown() {
        mShutdown = true;
        notify();
    }
    
    /**
     * Put the passed-in IndexReader into the cache, if applicable.  This function
     * will automatically AddRef the IndexReader if it stores it in it's cache
     *  
     * @param idx
     * @param reader
     */
    synchronized void putIndexReader(MailboxIndex idx, RefCountedIndexReader reader) {
        // special case disabled index reader cache:
        if (mMaxOpenReaders <= 0) {
            return;
        }
        // +1 b/c we haven't added the new one yet
        int toRemove = ((mOpenIndexReaders.size()+1) - mMaxOpenReaders); 
        if (toRemove > 0) {
            // remove extra (above our limit) readers
            for (Iterator<Entry<MailboxIndex,RefCountedIndexReader>> iter = mOpenIndexReaders.entrySet().iterator(); toRemove > 0; toRemove--) {
                Entry<MailboxIndex,RefCountedIndexReader> entry = iter.next();
                entry.getValue().release();
                if (sLog.isDebugEnabled())
                    sLog.debug("Releasing index reader for index: "+entry.getKey().toString()+" from cache (too many open)");
                iter.remove();
            }
        }
        assert(toRemove <= 0);
        reader.addRef();
        mOpenIndexReaders.put(idx,reader);
    }
    
    /**
     * Called by the MailboxIndex when it closes the reader itself (e.g. when there is
     * write activity to the index)
     * 
     * @param idx
     */
    synchronized void removeIndexReader(MailboxIndex idx) {
        if (mMaxOpenReaders <= 0)
            return;
        RefCountedIndexReader removed = mOpenIndexReaders.remove(idx);
        if (removed != null) {
            removed.release();
            if (sLog.isDebugEnabled())
                sLog.debug("Closing index reader for index: "+idx.toString()+" (removed)");
        }
    }
    
    /**
     * @param idx
     * @return an ALREADY ADDREFED IndexReader, or NULL if there is not one cached
     */
    synchronized RefCountedIndexReader getIndexReader(MailboxIndex idx) {
        RefCountedIndexReader toRet = mOpenIndexReaders.get(idx);
        if (toRet != null)
            toRet.addRef();
        return toRet;
    }
    
    /**
     * @param idx
     * @return
     */
    synchronized boolean containsKey(MailboxIndex idx) {
        return mOpenIndexReaders.containsKey(idx);
    }
    
    
    /**
     * Sweeper thread entry point
     */
    public void run() {
        if (mMaxOpenReaders <= 0) {
            sLog.info(getName() + " thread disabled (Max open IndexReaders set to 0)");
            return;
        } else {
            sLog.info(getName() + " thread starting");
            
            boolean shutdown = false;
            while (!shutdown) {
                // Sleep until next scheduled wake-up time, or until notified.
                synchronized (this) {
                    long startTime = System.currentTimeMillis();
                    
                    if (!mShutdown) {  // Don't go into wait() if shutting down.  (bug 1962)
                        long now = System.currentTimeMillis();
                        long until = startTime + mSweepIntervalMS;
                        if (until > now) {
                            try {
                                wait(until - now);
                            } catch (InterruptedException e) {}
                        }
                    }
                    shutdown = mShutdown;
                    
                    if (!shutdown) {
                        long now = System.currentTimeMillis();
                        long cutoff = now - mMaxReaderOpenTimeMS; 
                        
                        for (Iterator<Entry<MailboxIndex,RefCountedIndexReader>> iter = mOpenIndexReaders.entrySet().iterator(); iter.hasNext(); ) {
                            Entry<MailboxIndex,RefCountedIndexReader> entry = iter.next();
                            if (entry.getValue().getAccessTime() < cutoff) {
                                if (sLog.isDebugEnabled())
                                    sLog.debug("Releasing cached index reader for index: "+entry.getKey().toString()+" (timed out)");
                                entry.getValue().release();
                                iter.remove();
                            }
                        }
                    } // if (!shutdown)
                } // synchronized(this)
            } // while !shutdown
            
            // Shutdown time: clear the cache now
            synchronized(this) {
                for (RefCountedIndexReader reader: mOpenIndexReaders.values()) {
                    reader.release();
                }
                mOpenIndexReaders.clear();
            }
            sLog.info(getName() + " thread exiting");
        }
    }
}
