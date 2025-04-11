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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.common.io;

public abstract class AbstractFileCopierMonitor implements FileCopierCallback {

    private long mRequested;
    private long mCompleted;
    private boolean mWaiting;
    private boolean mDenyFutureOperations;

    protected abstract boolean fileCopierMonitorBegin(Object cbarg);

    protected abstract void fileCopierMonitorEnd(Object cbarg, Throwable err);

    public synchronized boolean fileCopierCallbackBegin(Object cbarg) {
        if (mDenyFutureOperations)
            return false;
        boolean allowed = fileCopierMonitorBegin(cbarg);
        if (allowed)
            mRequested++;
        return allowed;
    }

    public synchronized void fileCopierCallbackEnd(Object cbarg, Throwable err) {
        fileCopierMonitorEnd(cbarg, err);
        mCompleted++;
        if (mWaiting && mCompleted >= mRequested)
            notifyAll();
    }

    public synchronized void waitForCompletion() {
        mWaiting = true;
        if (mCompleted < mRequested) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    public synchronized void denyFutureOperations() {
        mDenyFutureOperations = true;
    }

    public synchronized long getRequested() {
        return mRequested;
    }

    public synchronized long getCompleted() {
        return mCompleted;
    }

    public synchronized long getPending() {
        return mRequested - mCompleted;
    }

    public synchronized boolean isFinished() {
        return mWaiting == true && mCompleted >= mRequested;
    }
}
