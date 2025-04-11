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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.net;

import org.jivesoftware.util.JiveGlobals;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool to be used for processing incoming packets when using non-blocking
 * connections.
 *
 * // TODO Change thead pool configuration. Would be nice to have something that can be
 * // TODO dynamically adjusted to demand and circumstances.
 *
 * @author Daniele Piras
 */
class IOExecutor {

    // SingleTon ...
    protected static IOExecutor instance = new IOExecutor();

    // Pool obj
    protected ThreadPoolExecutor executeMsgPool;

    // Internal queue for the pool
    protected LinkedBlockingQueue<Runnable> executeQueue;


    /*
    * Simple constructor that initialize the main executor structure.
    *
    */
    protected IOExecutor() {
        // Read poolsize parameter...
        int poolSize = JiveGlobals.getIntProperty("tiscali.pool.size", 15);
        // Create queue for executor
        executeQueue = new LinkedBlockingQueue<Runnable>();
        // Create executor
        executeMsgPool =
                new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, executeQueue);
    }

    public static void execute(Runnable task) {
        instance.executeMsgPool.execute(task);
    }

}
