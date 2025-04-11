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
/*
 * Created on Sep 23, 2005
 */
package com.zimbra.cs.redolog.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * @author dkarp
 */
public class CreateMountpoint extends RedoableOp {

    private int mId;
    private int mFolderId;
    private String mName;
    private String mOwnerId;
    private int mRemoteId;
    private byte mHint;

    public CreateMountpoint() {
        mId = UNKNOWN_ID;
    }

    public CreateMountpoint(int mailboxId, int folderId, String name, String ownerId, int remoteId, byte hint) {
        setMailboxId(mailboxId);
        mId = UNKNOWN_ID;
        mFolderId = folderId;
        mName = name != null ? name : "";
        mOwnerId = ownerId;
        mRemoteId = remoteId;
        mHint = hint;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public int getOpCode() {
        return OP_CREATE_LINK;
    }

    protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=").append(mId);
        sb.append(", name=").append(mName).append(", folder=").append(mFolderId);
        sb.append(", owner=").append(mOwnerId).append(", remote=").append(mRemoteId);
        sb.append(", hint=").append(mHint);
        return sb.toString();
    }

    protected void serializeData(DataOutput out) throws IOException {
        out.writeInt(mId);
        writeUTF8(out, mName);
        writeUTF8(out, mOwnerId);
        out.writeInt(mRemoteId);
        out.writeInt(mFolderId);
        out.writeByte(mHint);
    }

    protected void deserializeData(DataInput in) throws IOException {
        mId = in.readInt();
        mName = readUTF8(in);
        mOwnerId = readUTF8(in);
        mRemoteId = in.readInt();
        mFolderId = in.readInt();
        mHint = in.readByte();
    }

    public void redo() throws Exception {
        int mboxId = getMailboxId();
        Mailbox mailbox = Mailbox.getMailboxById(mboxId);
        try {
            mailbox.createMountpoint(getOperationContext(), mFolderId, mName, mOwnerId, mRemoteId, mHint);
        } catch (MailServiceException e) {
            if (e.getCode() == MailServiceException.ALREADY_EXISTS) {
                if (mLog.isInfoEnabled())
                    mLog.info("Mount " + mId + " already exists in mailbox " + mboxId);
            } else
                throw e;
        }
    }
}
