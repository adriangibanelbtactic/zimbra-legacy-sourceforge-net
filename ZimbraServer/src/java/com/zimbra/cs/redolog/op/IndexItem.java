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
 * Created on 2004. 7. 21.
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

/**
 * @author jhahm
 */
public class IndexItem extends RedoableOp {

    private int mId;
    private byte mType;
    private boolean mDeleteFirst;
    private boolean mCommitAllowed;
    private boolean mCommitAbortDone;

    public IndexItem() {
        mId = UNKNOWN_ID;
        mType = MailItem.TYPE_UNKNOWN;
        mCommitAllowed = false;
        mCommitAbortDone = false;
    }

    public IndexItem(int mailboxId, int id, byte type, boolean deleteFirst) {
        setMailboxId(mailboxId);
        mId = id;
        mType = type;
        mDeleteFirst = deleteFirst;
        mCommitAllowed = false;
        mCommitAbortDone = false;
    }

    public int getOpCode() {
        return OP_INDEX_ITEM;
    }

    public boolean deferCrashRecovery() {
        return true;
    }

    protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=");
        sb.append(mId).append(", type=").append(mType);
        return sb.toString();
    }

    protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mId);
        out.writeByte(mType);
        if (getVersion().atLeast(1,8))
            out.writeBoolean(mDeleteFirst);
    }

    protected void deserializeData(RedoLogInput in) throws IOException {
        mId = in.readInt();
        mType = in.readByte();
        if (getVersion().atLeast(1,8)) 
            mDeleteFirst = in.readBoolean();
        else
            mDeleteFirst = false;
    }

    public void redo() throws Exception {
        int mboxId = getMailboxId();
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(mboxId);
        synchronized (mbox) { // temp fix for bug 11890
            MailboxIndex mi = mbox.getMailboxIndex();
            if (mi != null)
                mi.redoIndexItem(mbox, mDeleteFirst, mId, mType, getTimestamp(), getUnloggedReplay());
        }
    }

    /**
     * An IndexItem transaction is always created as a sub-transaction
     * of a mail item create/modify transaction.  Delicate ordering of
     * log/commit of IndexItem, log/commit of the parent transaction,
     * and database commit is required for correct handling of crash
     * recovery, i.e. to avoid redoing an Index operation before redoing
     * the creation or value modification of the item being indexed.
     * 
     * In particular, the commit record for IndexItem should never be
     * written to the redo stream before the commit record for the
     * parent transaction.  The Mailbox class manages all aspects of
     * redo logging except IndexItem commit, which is done in batch by
     * indexer for performance reasons.  Typically the natural delay
     * introduced by the batching ensures IndexItem is committed some
     * time after parent transaction commit, but a boundary case exists
     * in which the batch commit occurs before the main thread writes
     * commit record for parent transaction.
     * 
     * To avoid that case, an IndexItem record is created in
     * commit-blocked mode.  Batch index commit thread will skip all
     * IndexItem transactions that are not allowed to commit yet.  The
     * main thread will unblock IndexItem commit after it has written
     * the commit record for the parent transaction.
     * @return
     */
    public synchronized boolean commitAllowed() {
        return mCommitAllowed;
    }

    public synchronized void allowCommit() {
        mCommitAllowed = true;
        if (mAttachedToParent)
            commit();
    }

    /**
     * Unlike other RedoableOp classes, commit/abort on IndexItem can be
     * called by two different threads.  It's necessary to remember if
     * commit/abort has been called already and ignore the subsequent
     * calls.
     */
    public synchronized void commit() {
        // Don't check mCommitAllowed here.  It's the responsibility of
        // the caller.
        if (!mCommitAbortDone) {
            super.commit();
            mCommitAbortDone = true;

            // Prevent any thread calling commitAllowed() from spinning.
            mCommitAllowed = true;
        }
    }

    public synchronized void abort() {
        if (!mCommitAbortDone) {
            super.abort();
            mCommitAbortDone = true;

            // Prevent any thread calling commitAllowed() from spinning.
            mCommitAllowed = true;
        }
    }

    private boolean mAttachedToParent;
    private RedoableOp mParentOp;

    public synchronized void setParentOp(RedoableOp op) {
        mParentOp = op;
    }

    public synchronized void attachToParent() {
        if (!mCommitAllowed && !mAttachedToParent) {
            mAttachedToParent = true;
            if (mParentOp != null)
                mParentOp.addChainedOp(this);
        } else
            commit();
    }
}
