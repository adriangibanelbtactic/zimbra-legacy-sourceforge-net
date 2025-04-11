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
package com.zimbra.cs.session;

import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.service.util.SyncToken;

/**
 * A session subclass which is used when an external entity wants to listen on a 
 * particular account for changes to the mailbox, but doesn't care what exactly
 * those changes are: presumably because the caller is going to use some sort
 * of other channel (e.g. IMAP) for synchronizing with the Mailbox.
 */
public class WaitSetSession extends Session {
    SomeAccountsWaitSet mWs = null;
    int mInterestMask;
    int mHighestChangeId;
    SyncToken mSyncToken;

    WaitSetSession(SomeAccountsWaitSet ws, String accountId, int interestMask, SyncToken lastKnownSyncToken) {
        super(accountId, Session.Type.WAITSET);
        mWs = ws;
        mInterestMask = interestMask;
        mSyncToken = lastKnownSyncToken;
    }

    @Override
    protected boolean isMailboxListener() {
        return true;
    }

    @Override
    protected boolean isRegisteredInCache() {
        return false;
    }
    
    void update(int interestMask, SyncToken lastKnownSyncToken) {
        mInterestMask = interestMask;
        mSyncToken = lastKnownSyncToken;
        if (mSyncToken != null) {
            // if the sync token is non-null, then we want
            // to signal IFF the passed-in changeId is after the current
            // synctoken...and we want to cancel the existing signalling
            // if the new synctoken is up to date with the mailbox
            Mailbox mbox = this.getMailbox();
            synchronized(mbox) {
                int mboxHighestChange = mbox.getLastChangeID();
                if (mboxHighestChange > mHighestChangeId)
                    mHighestChangeId = mboxHighestChange;
                if (mSyncToken.after(mHighestChangeId))
                    mWs.unsignalDataReady(this);
                else 
                    mWs.signalDataReady(this);
            }
        }
    }

    @Override
    protected void cleanup() { 
        mWs.cleanupSession(this);
    }

    @Override
    protected long getSessionIdleLifetime() { return 0; }

    @Override
    public void notifyPendingChanges(PendingModifications pns, int changeId, Session source) {
        if (changeId > mHighestChangeId)
            mHighestChangeId = changeId;
        
        if (mSyncToken != null && mSyncToken.after(mHighestChangeId))
            return; // don't signal, sync token stopped us

        if ((mInterestMask & pns.changedTypes) != 0)
            mWs.signalDataReady(this);
    }
}
