/*
 * ***** Begin LICENSE BLOCK *****
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
package com.zimbra.cs.ozserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import com.zimbra.cs.util.ZimbraLog;

import EDU.oswego.cs.dl.util.concurrent.SynchronizedInt;

public class OzConnection {

    private OzConnectionHandler mConnectionHandler;

    private Log mLog;

    private OzServer mServer;
    
    private SocketChannel mChannel;
    
    private OzMatcher mMatcher;

    private SelectionKey mSelectionKey; 

    private static SynchronizedInt mIdCounter = new SynchronizedInt(0);
    
    private final int mId;

    private final String mIdString;
    
    private final String mRemoteAddress;

    /* guards mReadBuffer and mClosed */
    private final Object mReadBufferLock = new Object();
    
    private ByteBuffer mReadBuffer;

    private boolean mClosed;
    
    OzConnection(OzServer server, SocketChannel channel) throws IOException {
        mId = mIdCounter.increment();
        mIdString = Integer.toString(mId);
        mRemoteAddress = channel.socket().getInetAddress().getHostAddress();
        mChannel = channel;
        mServer = server;
        mClosed = false;
        try {
            addToNDC();
            mLog = mServer.getLog();
            mLog.info("connected");
            mSelectionKey = channel.register(server.getSelector(), 0, this); 
            mConnectionHandler = server.newConnectionHandler(this);
            try {
                /* TODO - this should be a task */
                mConnectionHandler.handleConnect();
            } catch (Throwable t) {
                mLog.warn("exception occurrent handling connect; will close connection", t);
                closeNow();
                return;
            }
            enableReadInterest();
        } finally {
            clearFromNDC();
        }
    }

    public void closeNow() {
        Runnable closeTask = new Runnable() {
            public void run() {
                try {
                    addToNDC();
                    run0();
                } finally {
                    clearFromNDC();
                }
            }
            
            private void run0() {
                mLog.info("closing");
                try {
                    if (mChannel.isOpen()) {
                        mChannel.close();
                    }
                    mSelectionKey.cancel();
                } catch (IOException ioe) {
                    mLog.warn("exception closing channel, ignoring and continuing", ioe);
                } finally {
                    synchronized (mReadBufferLock) { 
                        if (mClosed && mLog.isDebugEnabled()) {
                            mLog.debug("duplicate close detected");
                        }
                        if (mReadBuffer != null) {
                            if (mClosed) {
                                mLog.warn("duplicate close and earlier close leaked buffer");
                            }
                            mServer.getBufferPool().recycle(mReadBuffer);
                            mLog.info("recycled buffer " + OzUtil.intToHexString(mReadBuffer.hashCode(), 0, ' '));
                            mReadBuffer = null;
                        }
                        mClosed = true;
                    }
                }
            }
        };
        
        mServer.executeInServerThread(closeTask);
    }

    void addToNDC() {
        ZimbraLog.addIpToContext(getRemoteAddress());
        ZimbraLog.addConnectionIdToContext(getIdString());
    }

    void clearFromNDC() {
        ZimbraLog.clearContext();
    }
    
    /* Caller must lock key! */
    static void logKey(Log log, SelectionKey selectionKey, String where) {
        if (selectionKey.isValid()) {
            log.debug(where +
                    " iops=" + selectionKey.interestOps() + 
                    " rops=" + selectionKey.readyOps() + 
                    " key=" + Integer.toHexString(selectionKey.hashCode()));
        } else {
            log.warn(where + " invalid key=" + Integer.toHexString(selectionKey.hashCode()));
        }
    }

    private void enableReadInterest() {
        synchronized (mSelectionKey) {
            int iops = mSelectionKey.interestOps();
            mSelectionKey.interestOps(iops | SelectionKey.OP_READ);
            logKey(mLog, mSelectionKey, "enabled read interest");
        }
        mSelectionKey.selector().wakeup();
    }
    
    private void disableReadInterest() {
        synchronized (mSelectionKey) {
            int iops = mSelectionKey.interestOps();
            mSelectionKey.interestOps(iops & (~SelectionKey.OP_READ));
            logKey(mLog, mSelectionKey, "disabled read interest");
        }
    }   
    
    private void enableWriteInterest() {
        synchronized (mSelectionKey) {
            int iops = mSelectionKey.interestOps();
            mSelectionKey.interestOps(iops | SelectionKey.OP_WRITE);
            logKey(mLog, mSelectionKey, "enabled write interest"); 
        }
        mSelectionKey.selector().wakeup();
    }
    
    private void disableWriteInterest() {
        synchronized (mSelectionKey) {
            int iops = mSelectionKey.interestOps();
            mSelectionKey.interestOps(iops & (~SelectionKey.OP_WRITE));
            logKey(mLog, mSelectionKey, "disabled write interest"); 
        }
    }
    
    OzConnectionHandler getConnectionHandler() {
        return mConnectionHandler;
    }
    

    
    public void setMatcher(OzMatcher matcher) {
        mMatcher = matcher;
    }

    private HandleDisconnectTask mHandleDisconnectTask = new HandleDisconnectTask();
    
    private class HandleDisconnectTask implements Runnable {
    	public void run() {
    	    boolean closed = false;
            try {
                try {
                    addToNDC();
                    mConnectionHandler.handleDisconnect();
                    closeNow();
                    closed = true;
                } finally {
                    clearFromNDC();
                }
            } catch (Throwable t) {
                mLog.warn("exception occurred while handling disconnect", t);
                if (!closed) {
                    /*
                     * Not catching any Throwables from closeNow()
                     * intentionally. closeNow() runs in server thread, so any
                     * exception it raises are ignored in the main server loop.
                     * If closeNow() failure is not being able to create the
                     * task object or to add that task object to the queue, that
                     * means the server is fubar - so don't bother to be
                     * graceful in a worker thread.
                     */
                    closeNow();
                }
            }
        }
    }
    
    private HandleReadTask mHandleReadTask = new HandleReadTask();
    
    private class HandleReadTask implements Runnable {
        public void run() {
            try {
                addToNDC();
                run0();
            } catch (Throwable t) {
                if (mChannel.isOpen()) {
                    mLog.warn("exception occured handling read; will close connection", t);
                } else {
                    // someone else may have closed the channel, eg, a shutdown or
                    // client went away, etc. Consider this case to be normal.
                    if (mLog.isDebugEnabled()) {
                        mLog.debug("ignorable (" + t.getClass().getName() + ") when reading, connection already closed", t); 
                    }
                }
                closeNow();
            } finally {
                clearFromNDC();
            }
        }
        
        private void run0() throws IOException {
            synchronized (mReadBufferLock) {
                if (mReadBuffer == null) {
                    if (mClosed) {
                        mLog.info("connection ready to read, but already closed");
                        return;
                    }
                    mReadBuffer = mServer.getBufferPool().get();
                    mLog.info("obtained buffer " + OzUtil.intToHexString(mReadBuffer.hashCode(), 0, ' '));
                }
                processReadBufferLocked();
            }
        }
    }

    private void processReadBufferLocked() throws IOException {
    	boolean trace = mLog.isTraceEnabled(); 
        
        if (trace) mLog.trace(OzUtil.byteBufferDebugDump("read buffer at start", mReadBuffer, true));
        
        int bytesRead = mChannel.read(mReadBuffer);
        
        if (trace) mLog.trace(OzUtil.byteBufferDebugDump("read buffer after read", mReadBuffer, true));
        
        assert(bytesRead == mReadBuffer.position());
        
        if (mServer.getSnooper().snoopReads()) {
            mServer.getSnooper().read(this, bytesRead, mReadBuffer);
        }
        
        if (bytesRead == -1) {
            if (mLog.isDebugEnabled()) mLog.debug("read detected that client closed connection");
            mServer.execute(mHandleDisconnectTask);
            return;
        }
        
        if (bytesRead == 0) {
            mLog.warn("got 0 bytes on supposedly read ready channel");
            return;
        }

        int handledBytes = 0;
        
        // Create a copy which we can trash.  (duplicate() does not
        // copy underlying buffer)
        ByteBuffer matchBuffer = mReadBuffer.duplicate();
        matchBuffer.flip();
        
        // We may have read more than one PDU 
        while (matchBuffer.position() < matchBuffer.limit()) {
            int matchStart = matchBuffer.position();
            
            if (trace) {
                mLog.trace(OzUtil.byteBufferDebugDump("matching", matchBuffer, false));
            }
            
            int matchEnd = mMatcher.match(matchBuffer);
            
            if (mLog.isDebugEnabled()) {
            	mLog.debug("match returned " + matchEnd);
            }
            
            if (matchEnd == -1) {
                break;
            }
            
            ByteBuffer pdu = mReadBuffer.duplicate();
            pdu.position(matchStart);
            pdu.limit(matchBuffer.position());
            mMatcher.trim(pdu);
            if (mServer.getSnooper().snoopInputs()) {
                mServer.getSnooper().input(this, pdu, true);
            }
            mConnectionHandler.handleInput(pdu, true);
            handledBytes = matchEnd;
        }
        
        if (handledBytes == 0) {
            if (mLog.isDebugEnabled()) mLog.debug("no bytes handled and no match");
            if (mReadBuffer.hasRemaining()) {
                if (mLog.isDebugEnabled()) mLog.debug("no bytes handled, no match, but there is room in buffer");
            } else {
                if (mLog.isDebugEnabled()) mLog.debug("no bytes handled, no match, and buffer is full, overflowing");
                assert(mReadBuffer.limit() == mReadBuffer.position());
                assert(mReadBuffer.limit() == mReadBuffer.capacity());
                mReadBuffer.flip();
                if (mServer.getSnooper().snoopInputs()) {
                    mServer.getSnooper().input(this, mReadBuffer, false);
                }
                mConnectionHandler.handleInput(mReadBuffer, false);
                mReadBuffer.clear();
            }
        } else {
            if (mReadBuffer.position() == handledBytes) {
                if (mLog.isDebugEnabled()) mLog.debug("handled all bytes in input, clearing buffer (no compacting)");
                mReadBuffer.clear();
            } else {
                if (mLog.isDebugEnabled()) mLog.debug("not all handled, compacting "  + (mReadBuffer.position() - handledBytes) + " bytes");
                mReadBuffer.position(handledBytes);
                mReadBuffer.compact();
            }
        }
        enableReadInterest();
        return;
    }

    void doRead() throws IOException {
        // This method runs in the server thread.  Note that we disable
        // read interest here, and not in the worker thread, so that
        // we don't get another ready notification before the worker
        // will get a chance to run and disable read interest.
        disableReadInterest();
        mServer.execute(mHandleReadTask);
    }

    public int getId() {
        return mId;
    }

    public String getIdString() {
        return mIdString;
    }
    
    public String getRemoteAddress() {
        return mRemoteAddress;
    }
    
    /**
     * Buffers that need to written out, when write side of the connection is ready.
     */
    List mWriteBuffers = new LinkedList();

    /**
     * Always looked at with mWriteBuffers locked.
     */
    private boolean mCloseAfterWrite = false;

    public void writeAscii(String data, boolean addCRLF) throws IOException {
        writeAscii(data, addCRLF, true);
    }
    
    public void writeAscii(String data, boolean addCRLF, boolean flush) throws IOException {
        byte[] bdata = new byte[data.length() + (addCRLF ? 2 : 0)];
        int n = data.length();
        for (int i = 0; i < n; i++) {
            int ch = data.charAt(i);
            if (ch > 127) {
                throw new IllegalArgumentException("expecting ASCII got " + ch + " at " + i + " in '" + data + "'");
            }
            bdata[i] = (byte)ch;
        }
        if (addCRLF) {
            bdata[n] = OzByteArrayMatcher.CR;
            bdata[n+1] = OzByteArrayMatcher.LF;
        }
        write(ByteBuffer.wrap(bdata));
    }
    
    public void write(ByteBuffer data) throws IOException {
        write(data, true);
    }
    
    public void write(ByteBuffer data, boolean flush) throws IOException {
        // TODO even if flush has not been requested, we should flush if we
        // have too much data!
        synchronized (mWriteBuffers) {
            mWriteBuffers.add(data);
            if (flush) {
                enableWriteInterest();
            }
        }
    }

    /**
     * This method runs in the server thread.
     * 
     * TODO two possible problems - too many write buffers
     * and underlying tcp buffer being too big
     */
    void doWrite() throws IOException {
        // It is possible that more than one buffer can be written
        // so we call channel write until we fail to write fully
        while (true) {
            synchronized (mWriteBuffers) {
                if (mWriteBuffers.isEmpty()) { 
                    // nothing more to write
                	disableWriteInterest();
                	if (mCloseAfterWrite) {
                		closeNow();
                	}
                	return;
                }

                ByteBuffer data = (ByteBuffer)mWriteBuffers.remove(0);

                assert(data != null);
                assert(data.hasRemaining());
                
                if (mServer.getSnooper().snoopWrites()) {
                    mServer.getSnooper().write(this, data);
                }
                int wrote = mChannel.write(data);
                if  (mServer.getSnooper().snoopWrites()) {
                    mServer.getSnooper().wrote(this, wrote);
                }
                
                if (data.hasRemaining()) {
                    // If not all data was written, stop - we will write again
                    // when we get called at a later time when available for
                    // write. Put the buffer back in the list so whatever is
                    // remaining can be written later.  Note that we do not
                    // clear write interest here.
                    if (mLog.isDebugEnabled()) mLog.debug("incomplete write, adding buffer back");
                    mWriteBuffers.add(0, data); 
                    break;
                }
            }
        }
    }

    /**
     * Close after all pending writes.
     */
    public void close() {
        disableReadInterest();
        synchronized (mWriteBuffers) {
            mCloseAfterWrite = true;
            if (mWriteBuffers.isEmpty()) {
                disableWriteInterest();
                closeNow();
            }
        }
    }

    private Map mProperties = new HashMap();
    
    public String getProperty(String key, String defaultValue) {
        synchronized (mProperties) {
            String result = (String)mProperties.get(key);
            if (result == null) {
                result = mServer.getProperty(key, defaultValue);
            }
            return result;
        }
    }
    
    public void setProperty(String key, String value) {
        synchronized (mProperties) {
            mProperties.put(key, value);
        }
    }
}
