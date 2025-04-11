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
package com.zimbra.cs.ozserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import com.zimbra.common.util.Log;

import EDU.oswego.cs.dl.util.concurrent.BoundedLinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import EDU.oswego.cs.dl.util.concurrent.ThreadFactory;

import com.zimbra.cs.ozserver.OzConnection.ServerTask;
import com.zimbra.cs.ozserver.OzConnection.TeardownReason;
import com.zimbra.cs.util.Zimbra;

public class OzServer {
    
    private Log mLog;

    private Selector mSelector;
    
    private ServerSocket mServerSocket;
    
    private ServerSocketChannel mServerSocketChannel;
    
    String mServerName;
    
    private Thread mServerThread;
    
    private OzConnectionHandlerFactory mConnectionHandlerFactory;
    
    private int mReadBufferSizeHint;
    
    private SSLContext mSSLContext;

    private boolean mDebugLogging; 
    
    public OzServer(String name, int readBufferSizeHint, ServerSocket serverSocket,
                    OzConnectionHandlerFactory connectionHandlerFactory, boolean debugLoggging, Log log)
        throws IOException
    {
        mLog = log;
        mDebugLogging = debugLoggging;
        
        mServerSocket = serverSocket;
        mServerSocketChannel = serverSocket.getChannel();
        mServerSocketChannel.configureBlocking(false);

        mServerName = name + "-" + mServerSocket.getLocalPort();
        mReadBufferSizeHint = readBufferSizeHint;
        
        mConnectionHandlerFactory = connectionHandlerFactory;
        
        mSelector = Selector.open();
        mServerSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT);
        
        /* TODO revisit these thread pool defaults; also make them
         * configurable. */
        mPooledExecutorQueue = new BoundedLinkedQueue(1024);
        mPooledExecutor = new PooledExecutor(mPooledExecutorQueue);
        mPooledExecutor.setMaximumPoolSize(50);
        mPooledExecutor.setMinimumPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        mPooledExecutor.runWhenBlocked();
        mPooledExecutor.setThreadFactory(new OzThreadFactory());
    }

    OzConnectionHandler newConnectionHandler(OzConnection connection) {
        return mConnectionHandlerFactory.newConnectionHandler(connection);
    }
    

    private Object mSelectorGuard = new Object();

    private void wakeupSelector(String reason) {
        if (Thread.currentThread() == mServerThread) {
           if (mLog.isDebugEnabled()) mLog.debug("noop wakeup selector - already in server thread");
        } else {
            synchronized (mSelectorGuard) {
                if (mLog.isDebugEnabled()) mLog.debug("waking up selector for " + reason);
                mSelector.wakeup();
            }
        }
    }

    LinkedList<ServerTask> mServerTaskList = new LinkedList<ServerTask>();
    
    void schedule(ServerTask task) {
        synchronized (mServerTaskList) {
            boolean mustWakeup = mServerTaskList.isEmpty();
            mServerTaskList.add(task);
            if (mustWakeup) {
                wakeupSelector(task.getName());
            }
        }
    }
    
    private void serverLoop() {
        long debugLoopCount = 0;
        long debugNumTasks = 0;
        
        while (true) {
            synchronized (this) {
                if (mShutdownRequested) {
                    break;
                }
            }

            int readyCount = 0;

            try {
                if (mLog.isDebugEnabled()) mLog.debug("entering select");
                synchronized (mSelectorGuard) { }
                readyCount = mSelector.select();
            } catch (IOException ioe) {
                mLog.warn("OzServer IOException in select", ioe);
            }

            debugLoopCount++;
            
            synchronized (mServerTaskList) {
                debugNumTasks = mServerTaskList.size();
                for (Iterator<ServerTask> iter = mServerTaskList.iterator(); iter.hasNext(); iter.remove()) {
                    ServerTask task = iter.next();
                    task.run();
                }
            }
            
            if (mLog.isDebugEnabled()) mLog.debug("select: ready=" + readyCount + "/" + mSelector.keys().size
                    () + " loop=" + debugLoopCount + " tasks=" + debugNumTasks);

            if (readyCount == 0 && debugNumTasks == 0) {
                for (SelectionKey allKey : mSelector.keys()) {
                    Object attach = allKey.attachment();
                    String attachStr = "[no attachment]";
                    if (attach != null) {
                        attachStr = "[cid=" + ((OzConnection)attach).getIdString() + "]";
                    }
                    mLog.info("spurious wakeup [" + debugLoopCount + "] " + attachStr +
                            " interest=" + OzUtil.opsToString(allKey.interestOps()) + 
                            " ready=" + OzUtil.opsToString(allKey.readyOps()) + 
                            " key=" + Integer.toHexString(allKey.hashCode()) +
                            " poolSize=" + mPooledExecutor.getPoolSize() +
                            " queueSize=" + mPooledExecutorQueue.size());
                }
            }

            Iterator<SelectionKey> iter = mSelector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey readyKey = iter.next();
                iter.remove();

                if (!readyKey.isValid()) {
                    if (mLog.isDebugEnabled()) mLog.debug("ignoring invalid key" + readyKey);
                    continue;
                }

                OzConnection readyConnection = null; 
                try {
                    if (readyKey.attachment() != null && readyKey.attachment() instanceof OzConnection) {
                        readyConnection = (OzConnection)readyKey.attachment();
                        readyConnection.addToNDC();
                    }
                    
                    OzUtil.logKey(mLog, readyKey, "ready key");
                    
                    if (readyKey.isAcceptable()) {
                        Socket newSocket = mServerSocket.accept();
                        SocketChannel newChannel = newSocket.getChannel(); 
                        newChannel.configureBlocking(false);
                        readyConnection= new OzConnection(OzServer.this, newChannel);
                    }
                    
                    if (readyKey.isReadable()) {
                        readyConnection.doReadReady();
                    }
                    
                    if (readyKey.isWritable()) {
                        readyConnection.doWriteReady();
                    }
                } catch (Throwable t) {
                    if (t instanceof OutOfMemoryError) {
                        String msg = null;
                        try {
                            msg = "OOME processing selected keys (" + mServerName + ")";
                        } finally {
                            Zimbra.halt(msg);
                        }
                    }
                    if (readyConnection != null) {
                    	mLog.warn("exception occurred handling selecting key, closing connection", t);
                        readyConnection.teardown(TeardownReason.ABRUPT, false);
                    } else {
                    	mLog.warn("ignoring exception occurred while handling a selected key", t);
                    }
                } finally {
                    if (readyConnection != null) {
                        readyConnection.clearFromNDC();
                    }
                }
            } /* end of ready keys loop */

            if (mLog.isDebugEnabled()) mLog.debug("processed " + readyCount + " ready keys");
        }
        
        assert(mShutdownRequested);

        mLog.info("shutting down thread pool");
        mPooledExecutor.shutdownNow();
        try {
            mLog.info("waiting for thread pool to shutdown");
            mPooledExecutor.awaitTerminationAfterShutdown(10*1000);
        } catch (InterruptedException ie) {
            mLog.warn("unexpected exception when waiting for shutdown");
        }
        mLog.info("done waiting for thread pool to shutdown");

        mLog.info("closing all selection keys");
        Set keys = mSelector.keys();
        for (Iterator iter = keys.iterator(); iter.hasNext();) {
            SelectionKey key = (SelectionKey) iter.next();
            try {
                key.channel().close();
            } catch (IOException ioe) {
                mLog.info("exception closing selection key", ioe);
            }
        }

        try {
            mSelector.close();
        } catch (IOException ioe) {
            mLog.warn("unexpected exception when closing selector");
        }
        mLog.info("closed selector");

        synchronized (mShutdownCompleteCondition) {
            mShutdownComplete = true;
            mShutdownCompleteCondition.notify();
        }
    }
    
    private boolean mShutdownRequested;
    
    private boolean mShutdownComplete;
    
    private Object mShutdownCompleteCondition = new Object();
    
    public void shutdown() {
        if (mLog.isDebugEnabled()) mLog.debug("server shutdown requested");
        synchronized (this) {
            mShutdownRequested = true;
        }

        wakeupSelector("shutdown");
        
        synchronized (mShutdownCompleteCondition) {
            while (!mShutdownComplete) {
                try {
                    mShutdownCompleteCondition.wait();
                } catch (InterruptedException ie) {
                    mLog.warn("exception occurred while waiting for shutdown", ie);
                }
            }
        }
    }
    
    public void start() {
        mServerThread = new Thread() {
            public void run() {
                try {
                    mLog.info("starting server loop");
                    serverLoop();
                } catch (Throwable t) {
                    if (t instanceof OutOfMemoryError) {
                        String msg = null;
                        try {
                            msg = "OOME in server loop (" + mServerName + ")";
                        } finally {
                            Zimbra.halt(msg);
                        }
                    }
                    mLog.warn("server loop encountered exception", t);
                    shutdown();
                } finally {
                    mLog.info("ended server loop");
                }
            }
        };        
        mServerThread.setName(mServerName + "-Server");
        mServerThread.start();
    }

    private PooledExecutor mPooledExecutor;

    private BoundedLinkedQueue mPooledExecutorQueue;

    void execute(Runnable task) {
        try {
            mPooledExecutor.execute(task);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void setPoolThreadsMax(int size) {
        mPooledExecutor.setMaximumPoolSize(size);
    }

    public int getPoolThreadsMax() {
        return mPooledExecutor.getMaximumPoolSize();
    }

    private int mPoolThreadsPriority = Thread.NORM_PRIORITY;
    
    public void setPoolThreadsPriority(int priority) {
        mPoolThreadsPriority = priority;
    }
    
    public int getPoolThreadsPriority() {
        return mPoolThreadsPriority;
    }
    
    private boolean mPoolThreadsAreDaemon = true;
    
    public void setPoolThreadsAreDaemon(boolean areDaemon) {
        mPoolThreadsAreDaemon = areDaemon;
    }
    
    public boolean getPoolThreadsAreDaemon() {
        return mPoolThreadsAreDaemon;
    }
    
    private class OzThreadFactory implements ThreadFactory {
        private int mCount = 0;

        public Thread newThread(Runnable runnable) {
            int n;
            synchronized (this) {
                n = ++mCount;
            }
            StringBuffer sb = new StringBuffer(mServerName);
            sb.append("-W").append(n);
            Thread thread = new Thread(runnable, sb.toString());
            thread.setDaemon(mPoolThreadsAreDaemon);
            thread.setPriority(mPoolThreadsPriority);
            return thread;
        }
    }
    
    Selector getSelector() {
        return mSelector;
    }

    int getReadBufferSizeHint() {
        return mReadBufferSizeHint;
    }
   
    Log getLog() {
        return mLog;
    }

    private Map<String, String> mProperties = new HashMap<String, String>();
    
    public String getProperty(String key, String defaultValue) {
        synchronized (mProperties) {
            String result = mProperties.get(key);
            if (result == null) {
                result = defaultValue;
            }
            return result;
        }
    }
    
    public void setProperty(String key, String value) {
        synchronized (mProperties) {
            mProperties.put(key, value);
        }
    }

    public boolean debugLogging() {
        return mDebugLogging;
    }
}


