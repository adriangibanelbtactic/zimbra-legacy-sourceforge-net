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
 * Created on Nov 12, 2005
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

/**
 * @author dkarp
 */
public class GrantAccess extends RedoableOp {

    private int mFolderId;
    private String mGrantee;
    private byte mGranteeType;
    private short mRights;
    private String mPassword;

    public GrantAccess() {
        mFolderId = UNKNOWN_ID;
        mGrantee = "";
    }

    public GrantAccess(int mailboxId, int folderId, String grantee, byte granteeType, short rights, String password) {
        setMailboxId(mailboxId);
        mFolderId = folderId;
        mGrantee = grantee == null ? "" : grantee;
        mGranteeType = granteeType;
        mRights = rights;
        mPassword = password == null ? "" : password;
    }

    public int getOpCode() {
        return OP_GRANT_ACCESS;
    }

    protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=").append(mFolderId);
        sb.append(", grantee=").append(mGrantee);
        sb.append(", type=").append(mGranteeType);
        sb.append(", rights=").append(ACL.rightsToString(mRights));
        sb.append(", args=").append(mPassword);
        return sb.toString();
    }

    protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mFolderId);
        out.writeUTF(mGrantee);
        out.writeByte(mGranteeType);
        out.writeShort(mRights);
        out.writeBoolean(true);
        if (getVersion().atLeast(1, 2))
        	out.writeUTF(mPassword);
    }

    protected void deserializeData(RedoLogInput in) throws IOException {
        mFolderId = in.readInt();
        mGrantee = in.readUTF();
        mGranteeType = in.readByte();
        mRights = in.readShort();
        in.readBoolean();  // "INHERIT", deprecated as of 02-Apr-2006
        if (getVersion().atLeast(1, 2))
        	mPassword = in.readUTF();
    }

    public void redo() throws ServiceException {
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(getMailboxId());
        mbox.grantAccess(getOperationContext(), mFolderId, mGrantee, mGranteeType, mRights, mPassword);
    }
}
