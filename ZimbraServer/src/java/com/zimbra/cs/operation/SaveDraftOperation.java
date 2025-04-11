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
package com.zimbra.cs.operation;

import java.io.IOException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.session.Session;

public class SaveDraftOperation extends Operation {

    private static int LOAD = 3;
    static {
        Operation.Config c = loadConfig(SaveDraftOperation.class);
        if (c != null)
            LOAD = c.mLoad;
    }

    private ParsedMessage mPm;
    private int mId;
    private int mOrigId;
    private String mReplyType;
    private String mIdentityId;

    private Message mMsg;

    public SaveDraftOperation(Session session, OperationContext oc, Mailbox mbox, Requester req,
                ParsedMessage pm, int id, int origId, String replyType, String identityId) {
        super(session, oc, mbox, req, LOAD);
        mPm = pm;
        mId = id;
        mOrigId = origId;
        mReplyType = replyType;
        mIdentityId = identityId;
    }


    protected void callback() throws ServiceException {
        try {
            mMsg = getMailbox().saveDraft(getOpCtxt(), mPm, mId, mOrigId, mReplyType, mIdentityId);
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException while saving draft", e);
        }
    }

    public Message getMsg() { return mMsg; }

}
