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

package com.zimbra.cs.stats;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.TaskScheduler;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.db.DbUtil;

/**
 * A collection of methods for keeping track of server performance statistics.
 */
public class ZimbraPerf {

    static Log sLog = LogFactory.getLog(ZimbraPerf.class);
    private static final com.zimbra.common.util.Log sZimbraStats = LogFactory.getLog("zimbra.stats");
    private static TaskScheduler<Void> sTaskScheduler = new TaskScheduler<Void>("ZimbraStats", 1, 1);

    public static final String RTS_DB_POOL_SIZE = "db_pool_size";
    public static final String RTS_INNODB_BP_HIT_RATE = "innodb_bp_hit_rate";
    
    public static final String RTS_POP_CONN = "pop_conn";
    public static final String RTS_POP_SSL_CONN = "pop_ssl_conn";
    public static final String RTS_IMAP_CONN = "imap_conn";
    public static final String RTS_IMAP_SSL_CONN = "imap_ssl_conn";

    // Accumulators.  To add a new accumulator, create a static instance here,
    // add it to the CORE_ACCUMULATORS array and if necessary, set options
    // in the static init code below.
    public static Counter COUNTER_LMTP_RCVD_MSGS = new Counter("lmtp_rcvd_msgs");
    public static Counter COUNTER_LMTP_RCVD_BYTES = new Counter("lmtp_rcvd_bytes");
    public static Counter COUNTER_LMTP_RCVD_RCPT = new Counter("lmtp_rcvd_rcpt");
    public static Counter COUNTER_LMTP_DLVD_MSGS = new Counter("lmtp_dlvd_msgs");
    public static Counter COUNTER_LMTP_DLVD_BYTES = new Counter("lmtp_dlvd_bytes");
    public static StopWatch STOPWATCH_DB_CONN = new StopWatch("db_conn");
    public static StopWatch STOPWATCH_LDAP_DC = new StopWatch("ldap_dc");
    public static StopWatch STOPWATCH_MBOX_ADD_MSG = new StopWatch("mbox_add_msg");
    public static StopWatch STOPWATCH_MBOX_GET = new StopWatch("mbox_get");         // Mailbox accessor response time
    public static Counter COUNTER_MBOX_CACHE = new Counter("mbox_cache");           // Mailbox cache hit rate
    public static Counter COUNTER_MBOX_MSG_CACHE = new Counter("mbox_msg_cache"); 
    public static Counter COUNTER_MBOX_ITEM_CACHE = new Counter("mbox_item_cache");
    public static StopWatch STOPWATCH_SOAP = new StopWatch("soap");
    public static StopWatch STOPWATCH_IMAP = new StopWatch("imap");
    public static StopWatch STOPWATCH_POP = new StopWatch("pop");
    public static Counter COUNTER_IDX_WRT = new Counter("idx_wrt");
    public static Counter COUNTER_IDX_WRT_OPENED = new Counter("idx_wrt_opened");
    public static Counter COUNTER_IDX_WRT_OPENED_CACHE_HIT = new Counter("idx_wrt_opened_cache_hit");
    
    private static RealtimeStats sRealtimeStats = 
        new RealtimeStats(new String[] {
            RTS_DB_POOL_SIZE, RTS_INNODB_BP_HIT_RATE,
            RTS_POP_CONN, RTS_POP_SSL_CONN, RTS_IMAP_CONN, RTS_IMAP_SSL_CONN 
            }
        );

    private static CopyOnWriteArrayList<Accumulator> sAccumulators = 
        new CopyOnWriteArrayList<Accumulator>(
                    new Accumulator[] {
                        COUNTER_LMTP_RCVD_MSGS, COUNTER_LMTP_RCVD_BYTES, COUNTER_LMTP_RCVD_RCPT,
                        COUNTER_LMTP_DLVD_MSGS, COUNTER_LMTP_DLVD_BYTES,
                        STOPWATCH_DB_CONN,
                        STOPWATCH_LDAP_DC,
                        STOPWATCH_MBOX_ADD_MSG, STOPWATCH_MBOX_GET, COUNTER_MBOX_CACHE,
                        COUNTER_MBOX_MSG_CACHE, COUNTER_MBOX_ITEM_CACHE,
                        STOPWATCH_SOAP,
                        STOPWATCH_IMAP,
                        STOPWATCH_POP,
                        COUNTER_IDX_WRT,
                        COUNTER_IDX_WRT_OPENED,
                        COUNTER_IDX_WRT_OPENED_CACHE_HIT,        
                        sRealtimeStats
                    }
        );
    
    /**
     * This may only be called BEFORE ZimbraPerf.initialize is called, otherwise the column
     * names will not be output correctly into the logs
     */
    public static void addRealtimeStatName(String name) {
        if (sIsInitialized)
            throw new IllegalStateException("Cannot add stat name after ZimbraPerf.initialize() is called");
        sRealtimeStats.addName(name);
    }
    
    /**
     * This may only be called BEFORE ZimbraPerf.initialize is called, otherwise the column
     * names will not be output correctly into the logs
     */
    public static void addAccumulator(Accumulator toAdd) {
        if (sIsInitialized)
            throw new IllegalStateException("Cannot add stat name after ZimbraPerf.initialize() is called");
        sAccumulators.add(toAdd);
    }
    
    /**
     * The number of statements that were prepared, as reported by
     * {@link DbPool.Connection#prepareStatement}.
     */
    private static volatile int sPrepareCount = 0;

    public static int getPrepareCount() {
        return sPrepareCount;
    }
    
    public static void incrementPrepareCount() {
        sPrepareCount++;
    }
    
    /**
     * Adds the given callback to the list of callbacks that are called
     * during realtime stats collection. 
     */
    public static void addStatsCallback(RealtimeStatsCallback callback) {
        sRealtimeStats.addCallback(callback);
    }
    
    /**
     * Returns the names of the columns for zimbrastats.csv.
     */
    public static List<String> getZimbraStatsColumns() {
        List<String> columns = new ArrayList<String>();
        columns.add("timestamp");
        synchronized (sAccumulators) {
            for (Accumulator a : sAccumulators) {
                for (String column : a.getNames()) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }
    
    private static final long CSV_DUMP_FREQUENCY = Constants.MILLIS_PER_MINUTE;
    private static boolean sIsInitialized = false;

    public synchronized static void initialize() {
        if (sIsInitialized) {
            sLog.warn("Detected a second call to ZimbraPerf.initialize()", new Exception());
            return;
        }
        
        addStatsCallback(new SystemStats());
        
        // Only the average is interesting for these counters
        COUNTER_MBOX_CACHE.setShowAverage(true);
        COUNTER_MBOX_CACHE.setAverageName("mbox_cache");
        COUNTER_MBOX_CACHE.setShowCount(false);
        COUNTER_MBOX_CACHE.setShowTotal(false);
        
        COUNTER_MBOX_MSG_CACHE.setShowAverage(true);
        COUNTER_MBOX_MSG_CACHE.setAverageName("mbox_msg_cache");
        COUNTER_MBOX_MSG_CACHE.setShowCount(false);
        COUNTER_MBOX_MSG_CACHE.setShowTotal(false);
        
        COUNTER_MBOX_ITEM_CACHE.setShowAverage(true);
        COUNTER_MBOX_ITEM_CACHE.setAverageName("mbox_item_cache");
        COUNTER_MBOX_ITEM_CACHE.setShowCount(false);
        COUNTER_MBOX_ITEM_CACHE.setShowTotal(false);
        
        COUNTER_IDX_WRT.setShowAverage(true);
        COUNTER_IDX_WRT.setShowCount(false);
        COUNTER_IDX_WRT.setShowTotal(false);

        sTaskScheduler.schedule("ZimbraStats", new ZimbraStatsDumper(), true, CSV_DUMP_FREQUENCY, 0);
        sIsInitialized = true;
    }

    /**
     * Scheduled task that writes a row to zimbrastats.csv with the latest
     * <tt>Accumulator</tt> data.
     */
    private static final class ZimbraStatsDumper
    implements Callable<Void>
    {
        public Void call() {
            List<Object> data = new ArrayList<Object>();
            String dateString = String.format("%1$tm/%1$td/%1$tY %1$tH:%1$tM:%1$tS", new Date()); 
            data.add(dateString);
            for (Accumulator a : sAccumulators) {
                synchronized (a) {
                    data.addAll(a.getData());
                    a.reset();
                }
            }

            // Clean up nulls 
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i) == null) {
                    data.set(i, "");
                }
            }
            sZimbraStats.info(StringUtil.join(",", data));
            return null;
        }
    }
}
