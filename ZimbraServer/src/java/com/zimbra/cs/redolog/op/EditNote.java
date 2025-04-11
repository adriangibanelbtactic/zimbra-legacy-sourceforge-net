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
 * Created on 2004. 12. 14.
 */
package com.zimbra.cs.redolog.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.zimbra.cs.mailbox.Mailbox;

/**
 * @author jhahm
 */
public class EditNote extends RedoableOp {

    private int mId;
    private String mContent;

    public EditNote() {
        mId = UNKNOWN_ID;
    }

    public EditNote(int mailboxId, int id, String content) {
        setMailboxId(mailboxId);
        mId = id;
        mContent = content != null ? content : "";
    }

    public int getOpCode() {
        return OP_EDIT_NOTE;
    }

    protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=");
        sb.append(mId).append(", content=").append(mContent);
        return sb.toString();
    }

    protected void serializeData(DataOutput out) throws IOException {
        out.writeInt(mId);
        writeUTF8(out, mContent);
    }

    protected void deserializeData(DataInput in) throws IOException {
        mId = in.readInt();
        mContent = readUTF8(in);
    }

    public void redo() throws Exception {
        int mboxId = getMailboxId();
        Mailbox mailbox = Mailbox.getMailboxById(mboxId);
        mailbox.editNote(getOperationContext(), mId, mContent);
    }
}
