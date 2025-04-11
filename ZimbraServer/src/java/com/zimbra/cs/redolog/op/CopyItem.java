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

/*
 * Created on 2005. 5. 31.
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;


/**
 * @author jhahm
 */
public class CopyItem extends RedoableOp {

    private int mSrcId;
    private int mDestId;
    private byte mType;
    private int mDestFolderId;
    private short mDestVolumeId = -1;

    public CopyItem() {
        mSrcId = UNKNOWN_ID;
        mDestId = UNKNOWN_ID;
        mType = MailItem.TYPE_UNKNOWN;
        mDestFolderId = 0;
    }

    public CopyItem(int mailboxId, int msgId, byte type, int destId) {
        setMailboxId(mailboxId);
        mSrcId = msgId;
        mType = type;
        mDestFolderId = destId;
    }

    /**
     * Sets the ID of the copied item.
     * @param destId
     */
    public void setDestId(int destId) {
    	mDestId = destId;
    }

    public int getDestId() {
    	return mDestId;
    }

    /**
     * Sets the volume ID for the copied blob.
     * @param volId
     */
    public void setDestVolumeId(short volId) {
    	mDestVolumeId = volId;
    }

    public short getDestVolumeId() {
    	return mDestVolumeId;
    }

    public int getOpCode() {
        return OP_COPY_ITEM;
    }

    protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("srcId=");
        sb.append(mSrcId);
        sb.append(", destId=").append(mDestId);
        sb.append(", type=").append(mType);
        sb.append(", destFolder=").append(mDestFolderId);
        sb.append(", destVolumeId=").append(mDestVolumeId);
        return sb.toString();
    }

    protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mSrcId);
        out.writeInt(mDestId);
        out.writeByte(mType);
        out.writeInt(mDestFolderId);
        out.writeShort(mDestVolumeId);
    }

    protected void deserializeData(RedoLogInput in) throws IOException {
        mSrcId = in.readInt();
        mDestId = in.readInt();
        mType = in.readByte();
        mDestFolderId = in.readInt();
        mDestVolumeId = in.readShort();
    }

    public void redo() throws Exception {
        int mboxId = getMailboxId();
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(mboxId);
        try {
            mbox.copy(getOperationContext(), mSrcId, mType, mDestFolderId);
        } catch (MailServiceException e) {
            if (e.getCode() == MailServiceException.ALREADY_EXISTS) {
                mLog.info("Item " + mDestId + " is already in mailbox " + mboxId);
                return;
            } else
                throw e;
        }
    }
}
