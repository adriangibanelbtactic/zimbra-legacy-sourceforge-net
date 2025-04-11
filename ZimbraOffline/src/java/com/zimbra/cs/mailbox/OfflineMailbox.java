/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * The Original Code is: Zimbra Network
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.mailbox;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.db.DbOfflineMailbox;
import com.zimbra.cs.mailbox.MailItem.PendingDelete;
import com.zimbra.cs.redolog.op.RedoableOp;
import com.zimbra.cs.service.account.AccountService;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.cs.session.PendingModifications;
import com.zimbra.cs.session.PendingModifications.Change;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.soap.Element;
import com.zimbra.soap.SoapHttpTransport;
import com.zimbra.soap.SoapProtocol;

public class OfflineMailbox extends Mailbox {

    public enum SyncState {
        BLANK, INITIAL, DELTA, PUSH, SYNC
    }

    public static class OfflineContext extends OperationContext {
        public OfflineContext()                 { super((RedoableOp) null); }
        public OfflineContext(RedoableOp redo)  { super(redo); }
    }

    public static final int ID_FOLDER_OUTBOX = 254;
    public static final int FIRST_OFFLINE_ITEM_ID = 2 << 29;

    private String mPassword;
    private String mBaseUrl;
    private String mSoapUrl;
    private String mRemoteId;
    private String mAuthToken;
    private long mAuthExpires;

    private SyncState mSyncState = SyncState.BLANK;
    private int mSyncToken = -1;

    private Map<Integer,Integer> mRenumbers = new HashMap<Integer,Integer>();

    OfflineMailbox(MailboxData data, String remoteId, String password, String url) throws ServiceException {
        super(data);
        mRemoteId = remoteId;
        mPassword = password;
        mBaseUrl = url;
        mSoapUrl = url + ZimbraServlet.USER_SERVICE_URI;

        Metadata config = getConfig(null, "offline");
        if (config != null && config.containsKey("state")) {
            try {
                mSyncToken = (int) config.getLong("token", -1);
                mSyncState = SyncState.valueOf(config.get("state"));
            } catch (Exception e) {
                ZimbraLog.mailbox.warn("unknown sync state value: " + config.get("state", null));
                mSyncState = SyncState.INITIAL;
            }
        }
    }

    @Override
    public MailSender getMailSender() {
        return new OfflineMailSender();
    }


    public SyncState getSyncState() {
        return mSyncState;
    }

    public int getSyncToken() {
        return mSyncToken;
    }

    void setSyncState(SyncState state) throws ServiceException {
        setSyncState(state, mSyncToken);
    }
    
    void setSyncState(SyncState state, int token) throws ServiceException {
        if (state == null)
            throw ServiceException.FAILURE("null SyncState passed to setSyncState", null);

        int newToken = token > 0 ? token : mSyncToken;
        Metadata config = new Metadata().put("state", state);
        if (newToken > 0)
            config.put("token", newToken);

        setConfig(null, "offline", config);
        mSyncState = state;
        mSyncToken = newToken;
    }


    public String getRemoteId() {
        return mRemoteId;
    }

    String getAuthToken() throws ServiceException {
        return getAuthToken(false);
    }

    String getAuthToken(boolean force) throws ServiceException {
        if (force || mAuthToken == null || mAuthExpires < System.currentTimeMillis()) {
            Element request = new Element.XMLElement(AccountService.AUTH_REQUEST);
            request.addElement(AccountService.E_ACCOUNT).addAttribute(AccountService.A_BY, "id").setText(getRemoteId());
            request.addElement(AccountService.E_PASSWORD).setText(mPassword);

            Element response = sendRequest(request, false);
            mAuthToken = response.getAttribute(AccountService.E_AUTH_TOKEN);
            mAuthExpires = System.currentTimeMillis() + response.getAttributeLong(AccountService.E_LIFETIME);
        }
        return mAuthToken;
    }

    public String getBaseUri() {
        return mBaseUrl;
    }

    public String getSoapUri() {
        return mSoapUrl;
    }


    @Override
    synchronized void initialize() throws ServiceException {
        super.initialize();

        // create a system outbox folder
        Folder userRoot = getFolderById(ID_FOLDER_USER_ROOT);
        Folder.create(ID_FOLDER_OUTBOX, this, userRoot, "Outbox", Folder.FOLDER_IS_IMMUTABLE, MailItem.TYPE_MESSAGE, 0, MailItem.DEFAULT_COLOR, null);
    }

    @Override
    int getInitialItemId() {
        // locally-generated items must be differentiable from authentic, server-blessed ones
        return FIRST_OFFLINE_ITEM_ID;
    }

    @Override
    boolean isTrackingSync() {
        return !(getOperationContext() instanceof OfflineContext);
    }

    @Override
    public boolean isTrackingImap() {
        return false;
    }


    MailItem getItemById(int id, byte type) throws ServiceException {
        Integer renumbered = mRenumbers.get(id < -FIRST_USER_ID ? -id : id);
        return super.getItemById(renumbered == null ? id : (id < 0 ? -renumbered : renumbered), type);
    }

    MailItem[] getItemById(int[] ids, byte type) throws ServiceException {
        int renumbered[] = new int[ids.length], i = 0;
        for (int id : ids) {
            Integer newId = mRenumbers.get(id < -FIRST_USER_ID ? -id : id);
            renumbered[i++] = newId == null ? id : (id < 0 ? -newId : newId);
        }
        return super.getItemById(renumbered, type);
    }

    public synchronized void setConversationId(OperationContext octxt, int msgId, int convId) throws ServiceException {
        // we're not allowing any magic -- we are being completely literal about the target conv id
        if (convId <= 0 && convId != -msgId)
            throw MailServiceException.NO_SUCH_CONV(convId);

        boolean success = false;
        try {
            beginTransaction("setConversationId", octxt);

            Message msg = getMessageById(msgId);
            if (convId == msg.getConversationId()) {
                success = true;
                return;
            }

            Conversation oldConv = (Conversation) msg.getParent();

            try {
                Conversation newConv;
                if (convId <= 0) {
                    // moving from a real conv to a virtual one
                    newConv = VirtualConversation.create(this, msg);
                } else {
                    // moving to an existing real conversation
                    newConv = getConversationById(convId);
                    newConv.addChild(msg);
                }
                DbMailItem.setParent(newConv, msg);
                msg.markItemModified(Change.MODIFIED_PARENT);
                msg.mData.parentId = convId;
                msg.mData.metadataChanged(this);
            } catch (MailServiceException.NoSuchItemException nsie) {
                // real conversation didn't exist; create it!
                createConversation(new Message[] {msg}, convId);
            }

            // and now we can update (and possibly delete) the old conversation
            oldConv.removeChild(msg);

            success = true;
        } finally {
            endTransaction(success);
        }
    }

    public synchronized void renumberItem(OperationContext octxt, int id, byte type, int newId) throws ServiceException {
        renumberItem(octxt, id, type, newId, -1);
    }

    public synchronized void renumberItem(OperationContext octxt, int id, byte type, int newId, int mod_content) throws ServiceException {
        if (id == newId)
            return;
        else if (id <= 0 || newId <= 0)
            throw ServiceException.FAILURE("invalid item id when renumbering (" + id + " => " + newId + ")", null);

        boolean success = false;
        try {
            beginTransaction("renumberItem", octxt);
            MailItem item = getItemById(id, type);

            if (mod_content < 0)
                mod_content = item.getSavedSequence();

            // FIXME: need to support tag renumbering (which requires updating bitmasks for tagged items)
            if (item instanceof Tag)
                throw ServiceException.FAILURE("cannot support tag renumbering at this point", null);

            // changing a message's item id needs to purge its Conversation (virtual or real)
            if (item instanceof Message)
                uncacheItem(item.getParentId());

            // mark old blob as disposable, but don't reindex item because INDEX_ID should still be correct
            MailboxBlob mblob = item.getBlob();
            if (mblob != null) {
                // register old blob for post-commit deletion
                PendingDelete info = new PendingDelete();
                info.blobs.add(mblob);
                item.mBlob = null;
                markOtherItemDirty(info);

                // copy blob to new id (note that item.getSavedSequence() may change again later)
                try {
                    MailboxBlob newBlob = StoreManager.getInstance().link(mblob.getBlob(), this, newId, mod_content, item.getVolumeId());
                    markOtherItemDirty(newBlob);
                } catch (IOException ioe) {
                    throw ServiceException.FAILURE("could not link blob for renumbered item (" + id + " => " + newId + ")", ioe);
                }
            }

            // update the id in the database and in memory
            markItemDeleted(id);
            DbOfflineMailbox.renumberItem(item, newId, mod_content);
            item.mId = item.mData.id = newId;
            item.mData.modContent = mod_content;
            item.markItemCreated();

            // remove the old item from the cache, as it's gone now...
            uncacheItem(id);
            if (item instanceof Folder) {
                // old items have the wrong folder id, which sucks
                purge(MailItem.TYPE_MESSAGE);
                purge(MailItem.TYPE_FOLDER);
            } else if (item instanceof Tag) {
                // old items have the wrong tag bitmask, which also sucks
                purge(MailItem.TYPE_MESSAGE);
                purge(MailItem.TYPE_TAG);
            }

            success = true;
        } finally {
            endTransaction(success);
        }

        mRenumbers.put(id, newId);
    }

    public synchronized void deleteEmptyFolder(OperationContext octxt, int folderId) throws ServiceException {
        try {
            Folder folder = getFolderById(octxt, folderId);
            if (folder.getSize() != 0 || folder.hasSubfolders())
                throw OfflineServiceException.FOLDER_NOT_EMPTY(folderId);
        } catch (MailServiceException.NoSuchItemException nsie) {
            ZimbraLog.mailbox.info("folder already deleted, skipping: " + folderId);
            return;
        }
        delete(octxt, folderId, MailItem.TYPE_FOLDER);
    }

    public boolean isPendingDelete(OperationContext octxt, int itemId, byte type) throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("isPendingDelete", octxt);

            boolean result = DbOfflineMailbox.isTombstone(this, itemId, type);
            success = true;
            return result;
        } finally {
            endTransaction(success);
        }
    }

    public synchronized MailItem.TypedIdList getLocalChanges(OperationContext octxt) throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("getLocalChanges", octxt);

            MailItem.TypedIdList result = DbOfflineMailbox.getChangedItems(this);
            success = true;
            return result;
        } finally {
            endTransaction(success);
        }
    }

    public int getChangeMask(OperationContext octxt, int id, byte type) throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("getChangeMask", octxt);

            MailItem item = getItemById(id, type);
            int mask = DbOfflineMailbox.getChangeMask(item);
            success = true;
            return mask;
        } finally {
            endTransaction(success);
        }
    }

    public void setChangeMask(OfflineContext octxt, int id, byte type, int mask) throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("setChangeMask", octxt);

            MailItem item = getItemById(id, type);
            DbOfflineMailbox.setChangeMask(item, mask);
            success = true;
        } finally {
            endTransaction(success);
        }
    }

    public synchronized void clearTombstones(OperationContext octxt, int token) throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("clearTombstones", octxt);
            DbOfflineMailbox.clearTombstones(this, token);
            success = true;
        } finally {
            endTransaction(success);
        }
    }

    public synchronized void syncChangeIds(OperationContext octxt, int itemId, byte type, int date, int mod_content, int change_date, int mod_metadata)
    throws ServiceException {
        if (date < 0 && mod_content < 0 && change_date < 0 && mod_metadata < 0)
            return;

        boolean success = false;
        try {
            beginTransaction("syncChangeIds", octxt);

            MailItem item = getItemById(itemId, type);
            markItemModified(item, Change.INTERNAL_ONLY);

            // resolve the defaulting to find out the real new values
            date = (date < 0 ? (int) (item.getDate() / 1000) : date);
            mod_content = (mod_content < 0 ? item.getSavedSequence() : mod_content);
            change_date = (change_date < 0 ? (int) (item.getChangeDate() / 1000) : change_date);
            mod_metadata = (mod_metadata < 0 ? item.getModifiedSequence() : mod_metadata);

            if (date == item.getDate() && mod_content == item.getSavedSequence() && change_date == item.getChangeDate() && mod_metadata == item.getModifiedSequence()) {
                success = true;
                return;
            }

            // update the database if amything's changed ...
            DbOfflineMailbox.setChangeIds(item, date, mod_content, change_date, mod_metadata);

            // ... update the filename on the item's blob if necessary ...
            boolean blobAffected = mod_content != item.getSavedSequence() && !item.getDigest().equals("");
            if (blobAffected) {
                MailboxBlob mblob = item.getBlob();

                // mark old blob as disposable
                PendingDelete info = new PendingDelete();
                info.blobs.add(mblob);
                item.mBlob = null;
                markOtherItemDirty(info);

                // and link to new blob
                try {
                    MailboxBlob newBlob = StoreManager.getInstance().link(mblob.getBlob(), this, item.getId(), mod_content, item.getVolumeId());
                    markOtherItemDirty(newBlob);
                } catch (IOException ioe) {
                    throw ServiceException.FAILURE("could not link blob for item (" + itemId + ") with new change id (" + item.getSavedSequence() + " => " + mod_content + ")", ioe);
                }
            }

            // ... and update the in-memory item as well
            item.mData.date = date;
            item.mData.modContent = mod_content;
            item.mData.dateChanged = change_date;
            item.mData.modMetadata = mod_metadata;

            success = true;
        } finally {
            endTransaction(success);
        }
    }

    public synchronized void syncMetadata(OperationContext octxt, int itemId, byte type, int folderId, int flags, long tags, byte color)
    throws ServiceException {
        boolean success = false;
        try {
            beginTransaction("syncMetadata", octxt);
            MailItem item = getItemById(itemId, type);
            int change_mask = getChangeMask(octxt, itemId, type);

            if ((change_mask & Change.MODIFIED_FOLDER) != 0 || folderId == ID_AUTO_INCREMENT)
                folderId = item.getFolderId();

            if ((change_mask & Change.MODIFIED_COLOR) != 0 || color == ID_AUTO_INCREMENT)
                color = item.getColor();

            if ((change_mask & Change.MODIFIED_TAGS) != 0 || tags == MailItem.TAG_UNCHANGED)
                tags = item.getTagBitmask();

            if (flags == MailItem.FLAG_UNCHANGED) {
                flags = item.getFlagBitmask();
            } else {
                if ((change_mask & Change.MODIFIED_UNREAD) != 0)
                    flags = (item.isUnread() ? Flag.BITMASK_UNREAD : 0) | (flags & ~Flag.BITMASK_UNREAD);
                if ((change_mask & Change.MODIFIED_FLAGS) != 0)
                    flags = item.getInternalFlagBitmask() | (flags & Flag.BITMASK_UNREAD);
            }

            boolean unread = (flags & Flag.BITMASK_UNREAD) > 0;
            flags &= ~Flag.BITMASK_UNREAD;

            item.move(getFolderById(folderId));
            item.setColor(color);
            item.setTags(flags, tags);
            if (mUnreadFlag.canTag(item))
                item.alterUnread(unread);
            success = true;
        } finally {
            endTransaction(success);
        }
    }


    @Override
    void snapshotCounts() throws ServiceException {
        // do the normal persisting of folder/tag counts
        super.snapshotCounts();

        // no need to push changes brought in via sync back to the server
        if (!isTrackingSync())
            return;

        PendingModifications pms = getPendingModifications();
        if (pms == null || !pms.hasNotifications())
            return;

        if (pms.created != null) {
            for (MailItem item : pms.created.values()) {
                if (item.getId() >= FIRST_USER_ID && PushChanges.PUSH_TYPES_SET.contains(item.getType()))
                    DbOfflineMailbox.updateChangeRecord(item, Change.MODIFIED_CONFLICT);
            }
        }

        if (pms.modified != null) {
            for (Change change : pms.modified.values()) {
                if (!(change.what instanceof MailItem))
                    continue;
                MailItem item = (MailItem) change.what;

                int filter = 0;
                switch (item.getType()) {
                    case MailItem.TYPE_MESSAGE:       filter = PushChanges.MESSAGE_CHANGES;  break;
                    case MailItem.TYPE_CONTACT:       filter = PushChanges.CONTACT_CHANGES;  break;
                    case MailItem.TYPE_FOLDER:        filter = PushChanges.FOLDER_CHANGES;   break;
                    case MailItem.TYPE_SEARCHFOLDER:  filter = PushChanges.SEARCH_CHANGES;   break;
                    case MailItem.TYPE_MOUNTPOINT:    filter = PushChanges.MOUNT_CHANGES;    break;
                    case MailItem.TYPE_TAG:           filter = PushChanges.TAG_CHANGES;      break;
                }

                if ((change.why & filter) != 0)
                    DbOfflineMailbox.updateChangeRecord(item, change.why & filter);
            }
        }
    }


    public static final int SERVER_REQUEST_TIMEOUT_SECS = 6;

    public Element sendRequest(Element request) throws ServiceException {
        return sendRequest(request, true);
    }

    public Element sendRequest(Element request, boolean requiresAuth) throws ServiceException {
        SoapHttpTransport transport = new SoapHttpTransport(mSoapUrl);
        try {
            transport.setRetryCount(1);
            transport.setTimeout(SERVER_REQUEST_TIMEOUT_SECS * 1000);
            if (requiresAuth)
                transport.setAuthToken(getAuthToken());
            transport.setSoapProtocol(SoapProtocol.Soap12);
            return transport.invokeWithoutSession(request.detach());
        } catch (IOException e) {
            throw ServiceException.PROXY_ERROR(e, mSoapUrl);
        } finally {
            transport.shutdown();
        }
    }
}
