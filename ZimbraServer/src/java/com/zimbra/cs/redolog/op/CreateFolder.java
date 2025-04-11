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
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

public class CreateFolder extends RedoableOp {

    private String mName;
    private int mParentId;
    private byte mDefaultView;
    private int mFlags;
    private byte mColor;
    private String mUrl;
    private int mFolderId;

    public CreateFolder() { }

    public CreateFolder(int mailboxId, String name, int parentId, byte view, int flags, byte color, String url) {
        setMailboxId(mailboxId);
        mName = name == null ? "" : name;
        mParentId = parentId;
        mDefaultView = view;
        mFlags = flags;
        mColor = color;
        mUrl = url == null ? "" : url;
    }

    public int getFolderId() {
        return mFolderId;
    }

    public void setFolderId(int folderId) {
        mFolderId = folderId;
    }

    @Override
    public int getOpCode() {
        return OP_CREATE_FOLDER;
    }

    @Override
    protected String getPrintableData() {
        StringBuilder sb = new StringBuilder("name=").append(mName);
        sb.append(", parent=").append(mParentId).append(", view=").append(mDefaultView);
        sb.append(", flags=").append(mFlags).append(", color=").append(mColor);
        sb.append(", url=").append(mUrl).append(", id=").append(mFolderId);
        return sb.toString();
    }

    @Override
    protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeUTF(mName);
        out.writeInt(mParentId);
        out.writeByte(mDefaultView);
        out.writeInt(mFlags);
        out.writeByte(mColor);
        out.writeUTF(mUrl);
        out.writeInt(mFolderId);
    }

    @Override
    protected void deserializeData(RedoLogInput in) throws IOException {
        mName = in.readUTF();
        mParentId = in.readInt();
        mDefaultView = in.readByte();
        mFlags = in.readInt();
        mColor = in.readByte();
        mUrl = in.readUTF();
        mFolderId = in.readInt();
    }

    @Override
    public void redo() throws Exception {
        int mboxId = getMailboxId();
        Mailbox mailbox = MailboxManager.getInstance().getMailboxById(mboxId);
        try {
            mailbox.createFolder(getOperationContext(), mName, mParentId, mDefaultView, mFlags, mColor, mUrl);
        } catch (MailServiceException e) {
            String code = e.getCode();
            if (code.equals(MailServiceException.ALREADY_EXISTS)) {
                if (mLog.isInfoEnabled())
                    mLog.info("Folder " + mName + " already exists in mailbox " + mboxId);
            } else {
                throw e;
            }
        }
    }
}
