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

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.session.Session;

public class CreateFolderOperation extends Operation {

    private static int LOAD = 3;
        static {
            Operation.Config c = loadConfig(CreateFolderOperation.class);
            if (c != null)
                LOAD = c.mLoad;
        }


    private String mName;
    private ItemId mIidParent;
    private byte mView;
    private int mFlags;
    private byte mColor;
    private String mUrl;
    private boolean mFetchIfExists;

    private Folder mFolder;
    private boolean mAlreadyExisted;

    public CreateFolderOperation(Session session, OperationContext oc, Mailbox mbox, Requester req,
                String name, ItemId iidParent, String view, String flags, byte color, String url, boolean fetchIfExists) {
        super(session, oc, mbox, req, LOAD);

        mName = name;
        mIidParent = iidParent;
        mView = MailItem.getTypeForName(view);
        mFlags = Flag.flagsToBitmask(flags);
        mColor = color;
        mUrl = url;
        mFetchIfExists = fetchIfExists;
    }

    public CreateFolderOperation(Session session, OperationContext oc, Mailbox mbox, Requester req,
                String name, byte view, boolean fetchIfExists) {
        super(session, oc, mbox, req, LOAD);

        mName = name;
        mIidParent = null;
        mView = view;
        mUrl = null;
        mFetchIfExists = fetchIfExists;
    }

    public String toString() {
        StringBuilder toRet = new StringBuilder("CreateFolder(");

        toRet.append("name=").append(mName);
        toRet.append(" parent=").append(mIidParent);
        toRet.append(" view=").append(mView);
        toRet.append(" color=").append(mColor);
        if (mUrl != null) 
            toRet.append(" url=").append(mUrl);
        toRet.append(")");

        return toRet.toString();
    }

    protected void callback() throws ServiceException {
        try {
            if (mIidParent != null)
                mFolder = getMailbox().createFolder(getOpCtxt(), mName, mIidParent.getId(), mView, mFlags, mColor, mUrl);
            else 
                mFolder = getMailbox().createFolder(getOpCtxt(), mName, (byte) 0, mView);

            if (!mFolder.getUrl().equals("")) {
                try {
                    getMailbox().synchronizeFolder(getOpCtxt(), mFolder.getId());
                } catch (ServiceException e) {
                    // if the synchronization fails, roll back the folder create
                    rollbackFolder(mFolder);
                    throw e;
                } catch (RuntimeException e) {
                    // if the synchronization fails, roll back the folder create
                    rollbackFolder(mFolder);
                    throw ServiceException.FAILURE("could not synchronize with remote feed", e);
                }
            }
        } catch (ServiceException se) {
            if (se.getCode() == MailServiceException.ALREADY_EXISTS && mFetchIfExists) {
                mFolder = getMailbox().getFolderByName(getOpCtxt(), mIidParent.getId(), mName);
                mAlreadyExisted = true;
            } else {
                throw se;
            }
        }
    }

    public Folder getFolder()        { return mFolder; }
    public boolean alreadyExisted()  { return mAlreadyExisted; }

    private void rollbackFolder(Folder folder) {
        try {
            folder.getMailbox().delete(null, folder.getId(), MailItem.TYPE_FOLDER);
        } catch (ServiceException nse) {
            getLog().warn("error ignored while rolling back folder create", nse);
        }
    }
}
