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
 * Created on Jul 3, 2005
 */
package com.zimbra.cs.mailbox;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;

/**
 * @author dkarp
 */
public class Mountpoint extends Folder {

    private String mOwnerId;
    private int    mRemoteId;

	Mountpoint(Mailbox mbox, UnderlyingData ud) throws ServiceException {
		super(mbox, ud);
	}

    /** Returns the <code>zimbraId</code> of the remote shared item's
     *  mailbox's owner.
     * 
     * @see Mailbox#getAccountId() */
    public String getOwnerId() {
        return mOwnerId;
    }

    /** Returns the numeric item id of the remote shared item pointed to by
     *  this <code>Mountpoint</code>.
     * 
     * @see MailItem#getId() */
    public int getRemoteId() {
        return mRemoteId;
    }

    /** Grants the specified set of rights to the target and persists them
     *  to the database.  <i>Note: This function will always throw the 
     *  <code>service.PERM_DENIED</code> {@link ServiceException} because
     *  <code>Mountpoint</code>s may not be re-shared.</i> */
    void grantAccess(String liquidId, byte type, short rights, boolean inherit) throws ServiceException {
        throw ServiceException.PERM_DENIED("you may not share the mounted folder " + getPath());
    }

    /** Removes the set of rights granted to the specified (id, type) pair
     *  and updates the database accordingly.  <i>Note: This function does
     *  nothing, as you cannot re-share a <code>Mountpoint</code> in the
     *  first place.</i>
     * 
     * @see #grantAccess(String, byte, short, boolean) */
    void revokeAccess(String liquidId) {
        return;
    }

    boolean canHaveChildren() { return false; }
    boolean trackUnread()     { return false; }
    boolean canParent(MailItem child)  { return false; }
    boolean canContain(MailItem child) { return false; }
    boolean canContain(byte type)      { return false; }

    /** Creates a new Mountpoint pointing at a remote item and persists it
     *  to the database.  A real nonnegative item ID must be supplied from
     *  a previous call to {@link Mailbox#getNextItemId(int)}.
     * 
     * @param id        The id for the new mountpoint.
     * @param parent    The folder to place the new mountpoint in.
     * @param name      The new mountpoint's name.
     * @param ownerId   The remote mailbox's owner's <code>zimbraId</code>.
     * @param remoteId  The remote item's numeric id.
     * @param view      The (optional) default object type for the folder.
     * @param flags     Folder flags (e.g. {@link Flag#BITMASK_CHECKED}).
     * @param name      The new mountpoint's color.
     * @perms {@link ACL#RIGHT_INSERT} on the parent folder
     * @throws ServiceException   The following error codes are possible:<ul>
     *    <li><code>mail.CANNOT_CONTAIN</code> - if the target folder
     *        can't contain mountpoints
     *    <li><code>mail.ALREADY_EXISTS</code> - if a folder by that name
     *        already exists in the parent folder
     *    <li><code>mail.INVALID_NAME</code> - if the new folder's name is
     *        invalid
     *    <li><code>mail.INVALID_REQUEST</code> - if the <code>ownerId</code>
     *        or <code>remoteId</code> are invalid
     *    <li><code>service.FAILURE</code> - if there's a database failure
     *    <li><code>service.PERM_DENIED</code> - if you don't have
     *        sufficient permissions</ul>
     * @see #validateFolderName(String)
     * @see #canContain(byte) */
    static Mountpoint create(int id, Folder parent, String name, String ownerId, int remoteId, byte view, int flags, byte color)
    throws ServiceException {
        if (parent == null || ownerId == null || ownerId.length() != 36 || remoteId <= 0)
            throw ServiceException.INVALID_REQUEST("invalid parameters when creating mountpoint", null);
        if (!parent.canAccess(ACL.RIGHT_INSERT))
            throw ServiceException.PERM_DENIED("you do not have sufficient permissions on the parent folder");
        if (parent == null || !parent.canContain(TYPE_MOUNTPOINT))
            throw MailServiceException.CANNOT_CONTAIN();
        validateFolderName(name);
        if (view != TYPE_UNKNOWN)
            validateType(view);
        if (parent.findSubfolder(name) != null)
            throw MailServiceException.ALREADY_EXISTS(name);
        Mailbox mbox = parent.getMailbox();

        UnderlyingData data = new UnderlyingData();
        data.id          = id;
        data.type        = TYPE_MOUNTPOINT;
        data.folderId    = parent.getId();
        data.parentId    = data.folderId;
        data.date        = mbox.getOperationTimestamp();
        data.flags       = flags & Flag.FLAGS_FOLDER;
        data.name        = name;
        data.subject     = name;
        data.metadata    = encodeMetadata(color, view, ownerId, remoteId);
        data.modMetadata = mbox.getOperationChangeID();
        data.modContent  = mbox.getOperationChangeID();
        DbMailItem.create(mbox, data);

        Mountpoint mpt = new Mountpoint(mbox, data);
        mpt.finishCreation(parent);
        return mpt;
    }

    @Override
    void delete(boolean childrenOnly, boolean writeTombstones) throws ServiceException {
        if (!getFolder().canAccess(ACL.RIGHT_DELETE))
            throw ServiceException.PERM_DENIED("you do not have sufficient permissions on the parent folder");
    	deleteSingleFolder(writeTombstones);
    }


    @Override
    void decodeMetadata(Metadata meta) throws ServiceException {
        super.decodeMetadata(meta);
        mOwnerId = meta.get(Metadata.FN_ACCOUNT_ID);
        mRemoteId = (int) meta.getLong(Metadata.FN_REMOTE_ID);
    }

    @Override
    Metadata encodeMetadata(Metadata meta) {
        return encodeMetadata(meta, mColor, mAttributes, mDefaultView, mOwnerId, mRemoteId);
    }

    private static String encodeMetadata(byte color, byte view, String owner, int remoteId) {
        return encodeMetadata(new Metadata(), color, (byte) 0, view, owner, remoteId).toString();
    }

    static Metadata encodeMetadata(Metadata meta, byte color, byte attrs, byte view, String owner, int remoteId) {
        meta.put(Metadata.FN_ACCOUNT_ID, owner);
        meta.put(Metadata.FN_REMOTE_ID, remoteId);
        return Folder.encodeMetadata(meta, color, attrs, view, null, null, 0);
    }
}
