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

/*
 * Created on Aug 13, 2004
 */
package com.zimbra.cs.db;

import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.cs.db.DbPool.Connection;
import com.zimbra.cs.imap.ImapMessage;
import com.zimbra.cs.localconfig.DebugConfig;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.mailbox.MailItem.PendingDelete;
import com.zimbra.cs.mailbox.MailItem.UnderlyingData;
import com.zimbra.cs.pop3.Pop3Message;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.ListUtil;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.TimeoutMap;
import com.zimbra.common.util.ZimbraLog;


/**
 * @author dkarp
 */
public class DbMailItem {

    public static final String TABLE_MAIL_ITEM = "mail_item";
    public static final String TABLE_APPOINTMENT = "appointment";
    public static final String TABLE_OPEN_CONVERSATION = "open_conversation";
    public static final String TABLE_TOMBSTONE = "tombstone";
    
    private static Log sLog = LogFactory.getLog(DbMailItem.class);

    /** Maps the mailbox id to the set of all tag combinations stored for all
     *  items in the mailbox.  Enables fast database lookup by tag. */
    private static final Map<Integer, TagsetCache> sTagsetCache =
        new TimeoutMap<Integer, TagsetCache>(120 * Constants.MILLIS_PER_MINUTE);

    /** Maps the mailbox id to the set of all flag combinations stored for all
     *  items in the mailbox.  Enables fast database lookup by flag. */
    private static final Map<Integer, TagsetCache> sFlagsetCache =
        new TimeoutMap<Integer, TagsetCache>(120 * Constants.MILLIS_PER_MINUTE);

    public static final int MAX_SENDER_LENGTH = 128;
    public static final int MAX_TEXT_LENGTH   = 65534;

    static final String IN_THIS_MAILBOX_AND = (!DebugConfig.disableMailboxGroup ? "mailbox_id = ? AND " : "");


    public static void create(Mailbox mbox, UnderlyingData data) throws ServiceException {
        if (data == null || data.id <= 0 || data.folderId <= 0 || data.parentId == 0 || data.indexId == 0)
            throw ServiceException.FAILURE("invalid data for DB item create", null);

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("INSERT INTO " + getMailItemTableName(mbox) +
                        "(" + (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") +
                        " id, type, parent_id, folder_id, index_id, imap_id, date, size, volume_id, blob_digest," +
                        " unread, flags, tags, sender, subject, name, metadata, mod_metadata, change_date, mod_content) " +
                        "VALUES (" + (!DebugConfig.disableMailboxGroup ? "?, " : "") +
            " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, data.id);
            stmt.setByte(pos++, data.type);
            if (data.parentId <= 0)
                // Messages in virtual conversations are stored with a null parent_id
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, data.parentId);
            stmt.setInt(pos++, data.folderId);
            if (data.indexId <= 0)
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, data.indexId);
            if (data.imapId <= 0)
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, data.imapId);
            stmt.setInt(pos++, data.date);
            stmt.setInt(pos++, data.size);
            if (data.volumeId >= 0)
                stmt.setShort(pos++, data.volumeId);
            else
                stmt.setNull(pos++, Types.TINYINT);
            stmt.setString(pos++, data.blobDigest);
            if (data.type == MailItem.TYPE_MESSAGE || data.type == MailItem.TYPE_FOLDER)
                stmt.setInt(pos++, data.unreadCount);
            else
                stmt.setNull(pos++, java.sql.Types.BOOLEAN);
            stmt.setInt(pos++, data.flags);
            stmt.setLong(pos++, data.tags);
            stmt.setString(pos++, checkSenderLength(data.sender));
            stmt.setString(pos++, data.subject);
            stmt.setString(pos++, data.name);
            stmt.setString(pos++, checkTextLength(data.metadata));
            stmt.setInt(pos++, data.modMetadata);
            stmt.setInt(pos++, data.dateChanged);
            stmt.setInt(pos++, data.modContent);
            int num = stmt.executeUpdate();
            if (num != 1)
                throw ServiceException.FAILURE("failed to create object", null);

            // Track the tags and flags for fast lookup later
            if (areTagsetsLoaded(mbox.getId())) {
                TagsetCache tagsets = getTagsetCache(conn, mbox);
                tagsets.addTagset(data.tags);
            }
            if (areFlagsetsLoaded(mbox.getId())) {
                TagsetCache flagsets = getFlagsetCache(conn, mbox);
                flagsets.addTagset(data.flags);
            }
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(data.id, e);
            else
                throw ServiceException.FAILURE("writing new object of type " + data.type, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void copy(MailItem item, int id, Folder folder, int indexId, int parentId, short volumeId, String metadata)
    throws ServiceException {
        Mailbox mbox = item.getMailbox();
        if (id <= 0 || indexId <= 0 || folder == null || parentId == 0)
            throw ServiceException.FAILURE("invalid data for DB item copy", null);

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            String table = getMailItemTableName(mbox);
            stmt = conn.prepareStatement("INSERT INTO " + table +
                        "(" + (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") +
                        " id, type, parent_id, folder_id, index_id, imap_id, date, size, volume_id, blob_digest," +
                        " unread, flags, tags, sender, subject, name, metadata, mod_metadata, change_date, mod_content) " +
                        "(SELECT " + (!DebugConfig.disableMailboxGroup ? "?, " : "") +
                        " ?, type, ?, ?, ?, ?, date, size, ?, blob_digest, unread," +
                        " flags, tags, sender, subject, name, ?, ?, ?, ? FROM " + table +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?)");
            int mboxId = mbox.getId();
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mboxId);
            stmt.setInt(pos++, id);                            // ID
            if (parentId <= 0)
                stmt.setNull(pos++, Types.INTEGER);            // PARENT_ID null for messages in virtual convs
            else
                stmt.setInt(pos++, parentId);                  //   or, PARENT_ID specified by caller
            stmt.setInt(pos++, folder.getId());                // FOLDER_ID
            stmt.setInt(pos++, indexId);                       // INDEX_ID
            stmt.setInt(pos++, id);                            // IMAP_ID is initially the same as ID
            if (volumeId >= 0)
                stmt.setShort(pos++, volumeId);                // VOLUME_ID specified by caller
            else
                stmt.setNull(pos++, Types.TINYINT);            //   or, no VOLUME_ID
            stmt.setString(pos++, checkTextLength(metadata));  // METADATA
            stmt.setInt(pos++, mbox.getOperationChangeID());   // MOD_METADATA
            stmt.setInt(pos++, mbox.getOperationTimestamp());  // CHANGE_DATE
            stmt.setInt(pos++, mbox.getOperationChangeID());   // MOD_CONTENT
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mboxId);
            stmt.setInt(pos++, item.getId());
            int num = stmt.executeUpdate();
            if (num != 1)
                throw ServiceException.FAILURE("failed to create object", null);
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(id, e);
            else
                throw ServiceException.FAILURE("copying " + MailItem.getNameForType(item.getType()) + ": " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void icopy(MailItem source, UnderlyingData data, boolean shared) throws ServiceException {
        Mailbox mbox = source.getMailbox();
        if (data == null || data.id <= 0 || data.folderId <= 0 || data.parentId == 0 || data.indexId == 0)
            throw ServiceException.FAILURE("invalid data for DB item i-copy", null);

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            String table = getMailItemTableName(mbox);
            String flags = shared ? "flags | " + Flag.BITMASK_COPIED : "flags";
            stmt = conn.prepareStatement("INSERT INTO " + table +
                        "(" + (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") +
                        " id, type, parent_id, folder_id, index_id, imap_id, date, size, volume_id, blob_digest," +
                        " unread, flags, tags, sender, subject, name, metadata, mod_metadata, change_date, mod_content) " +
                        "(SELECT " + (!DebugConfig.disableMailboxGroup ? "?, " : "") +
                        " ?, type, parent_id, ?, ?, ?, date, size, ?, blob_digest," +
                        " unread, " + flags + ", tags, sender, subject, name, metadata, ?, ?, ? FROM " + table +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?)");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, data.id);                       // ID
            stmt.setInt(pos++, data.folderId);                 // FOLDER_ID
            stmt.setInt(pos++, data.indexId);                  // INDEX_ID
            stmt.setInt(pos++, data.imapId);                   // IMAP_ID
            if (data.volumeId >= 0)
                stmt.setShort(pos++, data.volumeId);           // VOLUME_ID
            else
                stmt.setNull(pos++, Types.TINYINT);            //   or, no VOLUME_ID
            stmt.setInt(pos++, mbox.getOperationChangeID());   // MOD_METADATA
            stmt.setInt(pos++, mbox.getOperationTimestamp());  // CHANGE_DATE
            stmt.setInt(pos++, mbox.getOperationChangeID());   // MOD_CONTENT
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, source.getId());
            int num = stmt.executeUpdate();
            if (num != 1)
                throw ServiceException.FAILURE("failed to create object", null);
            stmt.close();

            boolean needsTag = !source.isTagged(mbox.mCopiedFlag);

            if (needsTag)
                getFlagsetCache(conn, mbox).addTagset(source.getInternalFlagBitmask() | Flag.BITMASK_COPIED);

            if (needsTag || source.getParentId() > 0) {
                stmt = conn.prepareStatement("UPDATE " + table +
                            " SET parent_id = NULL, flags = " + flags + ", mod_metadata = ?, change_date = ?" +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
                pos = 1;
                stmt.setInt(pos++, mbox.getOperationChangeID());
                stmt.setInt(pos++, mbox.getOperationTimestamp());
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, source.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (source instanceof Message && source.getParentId() <= 0)
                changeOpenTarget(Mailbox.getHash(((Message) source).getNormalizedSubject()), source, data.id);
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(data.id, e);
            else
                throw ServiceException.FAILURE("i-copying " + MailItem.getNameForType(source.getType()) + ": " + source.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void changeType(MailItem item, byte type) throws ServiceException {
        Connection conn = item.getMailbox().getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET type = ? WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, type);
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, item.getMailboxId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing new type for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void setFolder(MailItem item, Folder folder) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        if (mbox != folder.getMailbox())
            throw MailServiceException.WRONG_MAILBOX();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String imapRenumber = mbox.isTrackingImap() ? ", imap_id = IF(imap_id IS NULL, NULL, 0)" : "";
            int attr = 1;
            if (item instanceof Folder) {
                stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                            " SET parent_id = ?, folder_id = ?, mod_metadata = ?, change_date = ?" +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id = ? AND folder_id != ?");
                stmt.setInt(attr++, folder.getId());
            } else if (item instanceof Conversation && !(item instanceof VirtualConversation)) {
                stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                            " SET folder_id = ?, mod_metadata = ?, change_date = ?" + imapRenumber +
                            " WHERE " + IN_THIS_MAILBOX_AND + "parent_id = ? AND folder_id != ?");
            } else {
                stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                            " SET folder_id = ?, mod_metadata = ?, change_date = ? " + imapRenumber +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id = ? AND folder_id != ?");
            }
            stmt.setInt(attr++, folder.getId());
            stmt.setInt(attr++, mbox.getOperationChangeID());
            stmt.setInt(attr++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(attr++, mbox.getId());
            stmt.setInt(attr++, item instanceof VirtualConversation ? ((VirtualConversation) item).getMessageId() : item.getId());
            stmt.setInt(attr++, folder.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(item.getName(), e);
            else
                throw ServiceException.FAILURE("writing new folder data for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void setFolder(List<Integer> itemIDs, Folder folder) throws ServiceException {
        if (itemIDs == null || itemIDs.isEmpty())
            return;
        Mailbox mbox = folder.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String imapRenumber = mbox.isTrackingImap() ? ", imap_id = IF(imap_id IS NULL, NULL, 0)" : "";
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(folder) +
                        " SET folder_id = ?, mod_metadata = ?, change_date = ?" + imapRenumber +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id IN " + DbUtil.suitableNumberOfVariables(itemIDs));
            int arg = 1;
            stmt.setInt(arg++, folder.getId());
            stmt.setInt(arg++, mbox.getOperationChangeID());
            stmt.setInt(arg++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(arg++, mbox.getId());
            for (int id : itemIDs)
                stmt.setInt(arg++, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS("", e);
            else
                throw ServiceException.FAILURE("writing new folder data for item [" + itemIDs + ']', e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void setParent(MailItem child, MailItem parent) throws ServiceException {
        setParent(new MailItem[] { child }, parent);
    }
    public static void setParent(MailItem[] children, MailItem parent) throws ServiceException {
        if (children == null || children.length == 0)
            return;
        Mailbox mbox = children[0].getMailbox();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(mbox) +
                        " SET parent_id = ?, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id IN " + DbUtil.suitableNumberOfVariables(children));
            int arg = 1;
            if (parent == null || parent instanceof VirtualConversation)
                stmt.setNull(arg++, Types.INTEGER);
            else
                stmt.setInt(arg++, parent.getId());
            stmt.setInt(arg++, mbox.getOperationChangeID());
            stmt.setInt(arg++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(arg++, mbox.getId());
            for (int i = 0; i < children.length; i++)
                stmt.setInt(arg++, children[i].getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("adding children to parent " + (parent == null ? "NULL" : parent.getId() + ""), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void reparentChildren(MailItem oldParent, MailItem newParent) throws ServiceException {
        if (oldParent == newParent)
            return;
        Mailbox mbox = oldParent.getMailbox();
        if (mbox != newParent.getMailbox())
            throw MailServiceException.WRONG_MAILBOX();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String relation = (oldParent instanceof VirtualConversation ? "id = ?" : "parent_id = ?");

            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(oldParent) +
                        " SET parent_id = ?, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + relation);
            int pos = 1;
            if (newParent instanceof VirtualConversation)
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, newParent.getId());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, oldParent instanceof VirtualConversation ? ((VirtualConversation) oldParent).getMessageId() : oldParent.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing new parent for children of item " + oldParent.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void saveMetadata(MailItem item, String metadata) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET date = ?, size = ?, metadata = ?, mod_metadata = ?, change_date = ?, mod_content = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, (int) (item.getDate() / 1000));
            stmt.setInt(pos++, item.getSize());
            stmt.setString(pos++, checkTextLength(metadata));
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setInt(pos++, item.getSavedSequence());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing metadata for mailbox " + item.getMailboxId() + ", item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void persistCounts(MailItem item, String metadata) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET size = ?, unread = ?, metadata = ?, mod_metadata = ?, change_date = ?, mod_content = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, item.getSize());
            stmt.setInt(pos++, item.getUnreadCount());
            stmt.setString(pos++, checkTextLength(metadata));
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setInt(pos++, item.getSavedSequence());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing metadata for mailbox " + item.getMailboxId() + ", item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    // need to kill the Note class sooner rather than later
    public static void saveSubject(Note note) throws ServiceException {
        Mailbox mbox = note.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(note) +
                        " SET date = ?, size = ?, subject = ?, mod_metadata = ?, change_date = ?, mod_content = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, (int) (note.getDate() / 1000));
            stmt.setInt(pos++, note.getSize());
            stmt.setString(pos++, note.getSubject());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, note.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing subject for mailbox " + note.getMailboxId() + ", note " + note.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void saveName(MailItem item) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;

        String name = item.getName().equals("") ? null : item.getName();
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET date = ?, size = ?, name = ?, subject = ?, mod_metadata = ?, change_date = ?, mod_content = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, (int) (item.getDate() / 1000));
            stmt.setInt(pos++, item.getSize());
            stmt.setString(pos++, name);
            stmt.setString(pos++, name);
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(name, e);
            else
                throw ServiceException.FAILURE("writing name for mailbox " + item.getMailboxId() + ", item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void saveData(MailItem item, String sender, String metadata)
    throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;

        String name = item.getName().equals("") ? null : item.getName();

        String subject = item.getSubject();
        if (item instanceof Conversation)
            subject = ((Conversation) item).getNormalizedSubject();
        else if (item instanceof Message)
            subject = ((Message) item).getNormalizedSubject();

        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET type = ?, parent_id = ?, date = ?, size = ?, blob_digest = ?, flags = ?," +
                        "  sender = ?, subject = ?, name = ?, metadata = ?," +
                        "  mod_metadata = ?, change_date = ?, mod_content = ?, volume_id = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setByte(pos++, item.getType());
            if (item.getParentId() <= 0)
                // Messages in virtual conversations are stored with a null parent_id
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, item.getParentId());
            stmt.setInt(pos++, (int) (item.getDate() / 1000));
            stmt.setInt(pos++, item.getSize());
            stmt.setString(pos++, item.getDigest(true));
            stmt.setInt(pos++, item.getInternalFlagBitmask());
            stmt.setString(pos++, checkSenderLength(sender));
            stmt.setString(pos++, subject);
            stmt.setString(pos++, name);
            stmt.setString(pos++, checkTextLength(metadata));
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setInt(pos++, item.getSavedSequence());
            short vol = item.getVolumeId();
            if (vol > 0)
                stmt.setShort(pos++, item.getVolumeId());
            else
                stmt.setNull(pos++, Types.TINYINT);
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();

            // Update the flagset cache.  Assume that the item's in-memory
            // data has already been updated.
            if (areFlagsetsLoaded(mbox.getId())) {
                TagsetCache flagsets = getFlagsetCache(conn, mbox);
                flagsets.addTagset(item.getInternalFlagBitmask());
            }
        } catch (SQLException e) {
            // catch item_id uniqueness constraint violation and return failure
            if (e.getErrorCode() == Db.Error.DUPLICATE_ROW)
                throw MailServiceException.ALREADY_EXISTS(item.getName(), e);
            else
                throw ServiceException.FAILURE("rewriting row data for mailbox " + item.getMailboxId() + ", item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void openConversation(String hash, MailItem item) throws ServiceException {
        Connection conn = item.getMailbox().getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("INSERT INTO " + getConversationTableName(item) +
                        "(" + (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") + "hash, conv_id)" +
                        " VALUES (" + (!DebugConfig.disableMailboxGroup ? "?, " : "") + "?, ?)" +
                        " ON DUPLICATE KEY UPDATE conv_id = ?");
            int mboxId = item.getMailboxId();
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mboxId);
            stmt.setString(pos++, hash);
            stmt.setInt(pos++, item.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
//          return (num == 1);  // This doesn't work.  In the UPDATE case MySQL returns 2 instead of 1. (bug)
//          return num > 0;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing open conversation association for hash " + hash, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void closeConversation(String hash, MailItem item) throws ServiceException {
        Connection conn = item.getMailbox().getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("DELETE FROM " + getConversationTableName(item) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "hash = ? AND conv_id = ?");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, item.getMailboxId());
            stmt.setString(pos++, hash);
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing open conversation association for hash " + hash, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void changeOpenTarget(String hash, MailItem oldTarget, int newTargetId) throws ServiceException {
        Connection conn = oldTarget.getMailbox().getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getConversationTableName(oldTarget) +
                        " SET conv_id = ? WHERE " + IN_THIS_MAILBOX_AND + "hash = ? AND conv_id = ?");
            int pos = 1;
            stmt.setInt(pos++, newTargetId);
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, oldTarget.getMailboxId());
            stmt.setString(pos++, hash);
            stmt.setInt(pos++, oldTarget.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("switching open conversation association for item " + oldTarget.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void saveImapUid(MailItem item) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(mbox) +
                        " SET imap_id = ? WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, item.getImapUid());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("setting IMAP UID for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void alterTag(MailItem item, Tag tag, boolean add) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        if (mbox != tag.getMailbox())
            throw MailServiceException.WRONG_MAILBOX();
        if (tag.getId() == Flag.ID_FLAG_UNREAD)
            throw ServiceException.FAILURE("unread state must be updated with alterUnread()", null);
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String relation, column = (tag instanceof Flag ? "flags" : "tags");
            if (item instanceof VirtualConversation)  relation = "id = ?";
            else if (item instanceof Conversation)    relation = "parent_id = ?";
            else if (item instanceof Folder)          relation = "folder_id = ?";
            else if (item instanceof Flag)            relation = "flags & ?";
            else if (item instanceof Tag)             relation = "tags & ?";
            else                                      relation = "id = ?";

            String precondition = (add ? "NOT (" : "(") + column + " & ?)";
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET " + column + " = " + column + (add ? " | ?" : " & ?") + ", mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + precondition + " AND " + relation);

            int pos = 1;
            stmt.setLong(pos++, add ? tag.getBitmask() : ~tag.getBitmask());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setLong(pos++, tag.getBitmask());
            if (item instanceof Tag)
                stmt.setLong(pos++, ((Tag) item).getBitmask());
            else if (item instanceof VirtualConversation)
                stmt.setInt(pos++, ((VirtualConversation) item).getMessageId());
            else
                stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();

            // Update the flagset or tagset cache.  Assume that the item's in-memory
            // data has already been updated.
            if (tag instanceof Flag && areFlagsetsLoaded(mbox.getId())) {
                TagsetCache flagsets = getFlagsetCache(conn, mbox);
                flagsets.addTagset(item.getInternalFlagBitmask());
            } else if (areTagsetsLoaded(mbox.getId())) {
                TagsetCache tagsets = getTagsetCache(conn, mbox);
                tagsets.addTagset(item.getTagBitmask());
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("updating tag data for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void alterTag(Tag tag, List<Integer> itemIDs, boolean add)
    throws ServiceException {
        if (itemIDs == null || itemIDs.isEmpty())
            return;
        Mailbox mbox = tag.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String column = (tag instanceof Flag ? "flags" : "tags");
            String precondition = (add ? "NOT (" : "(") + column + " & ?)";
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(tag) +
                        " SET " + column + " = " + column + (add ? " | ?" : " & ?") + ", mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + precondition + " AND id IN " + DbUtil.suitableNumberOfVariables(itemIDs));
            int arg = 1;
            stmt.setLong(arg++, add ? tag.getBitmask() : ~tag.getBitmask());
            stmt.setInt(arg++, mbox.getOperationChangeID());
            stmt.setInt(arg++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(arg++, mbox.getId());
            stmt.setLong(arg++, tag.getBitmask());
            for (int id : itemIDs)
                stmt.setInt(arg++, id);
            stmt.executeUpdate();

            // Update the flagset or tagset cache.  Assume that the item's in-memory
            // data has already been updated.
            if (tag instanceof Flag && areFlagsetsLoaded(mbox.getId())) {
                TagsetCache flagsets = getFlagsetCache(conn, mbox);
                flagsets.applyMask(tag.getBitmask(), add);
            } else if (areTagsetsLoaded(mbox.getId())) {
                TagsetCache tagsets = getTagsetCache(conn, mbox);
                tagsets.applyMask(tag.getBitmask(), add);
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("updating tag data for items [" + itemIDs + "]", e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void clearTag(Tag tag) throws ServiceException {
        Mailbox mbox = tag.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(tag) +
                        " SET tags = tags & ?, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "tags & ?");
            int pos = 1;
            stmt.setLong(pos++, ~tag.getBitmask());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setLong(pos++, tag.getBitmask());
            stmt.executeUpdate();

            if (areTagsetsLoaded(mbox.getId())) {
                TagsetCache tagsets = getTagsetCache(conn, mbox);
                tagsets.applyMask(tag.getTagBitmask(), false);
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("clearing all references to tag " + tag.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    /**
     * Sets the <code>unread</code> column for the specified <code>MailItem</code>.
     * If the <code>MailItem</code> is a <code>Conversation</code>, <code>Tag</code>
     * or <code>Folder</code>, sets the <code>unread</code> column for all related items.
     */
    public static void alterUnread(MailItem item, boolean unread)
    throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            String relation;
            if (item instanceof VirtualConversation)  relation = "id = ?";
            else if (item instanceof Conversation)    relation = "parent_id = ?";
            else if (item instanceof Folder)          relation = "folder_id = ?";
            else if (item instanceof Flag)            relation = "flags & ?";
            else if (item instanceof Tag)             relation = "tags & ?";
            else                                      relation = "id = ?";

            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(item) +
                        " SET unread = ?, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "unread = ? AND " + relation + " AND type = " + MailItem.TYPE_MESSAGE);
            int pos = 1;
            stmt.setInt(pos++, unread ? 1 : 0);
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, unread ? 0 : 1);
            if (item instanceof Tag)
                stmt.setLong(pos++, ((Tag) item).getBitmask());
            else if (item instanceof VirtualConversation)
                stmt.setInt(pos++, ((VirtualConversation) item).getMessageId());
            else
                stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("updating unread state for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void alterUnread(Mailbox mbox, List<Integer> itemIDs, boolean unread)
    throws ServiceException {
        if (itemIDs == null || itemIDs.isEmpty())
            return;
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + getMailItemTableName(mbox) +
                        " SET unread = ?, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "unread = ? AND id IN " + DbUtil.suitableNumberOfVariables(itemIDs) + " AND type = " + MailItem.TYPE_MESSAGE);
            int pos = 1;
            stmt.setInt(pos++, unread ? 1 : 0);
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, unread ? 0 : 1);
            for (int id : itemIDs)
                stmt.setInt(pos++, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("updating tag data for items [" + itemIDs + "]", e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    /**
     * Updates all conversations affected by a folder deletion.  For all conversations
     * that have messages in the given folder, updates their message count and nulls out
     * metadata so that the sender list is recalculated the next time the conversation
     * is instantiated.
     * 
     * @param folder the folder that is being deleted
     * @return the ids of any conversation that were purged as a result of this operation
     */
    public static List<Integer> markDeletionTargets(Folder folder) throws ServiceException {
        Mailbox mbox = folder.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String table = getMailItemTableName(folder);
            stmt = conn.prepareStatement("UPDATE " + table + ", " +
                        "(SELECT parent_id pid, COUNT(*) count FROM " + getMailItemTableName(folder) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND parent_id IS NOT NULL GROUP BY parent_id) x" +
                        " SET size = size - count, metadata = NULL, mod_metadata = ?, change_date = ?" +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = pid AND type = " + MailItem.TYPE_CONVERSATION);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folder.getId());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.executeUpdate();

            return getPurgedConversations(mbox);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("marking deletions for conversations crossing folder " + folder.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    /**
     * Updates all affected conversations when a <code>List</code> of <code>MailItem</code>s
     * is deleted.  Updates each conversation's message count and nulls out
     * metadata so that the sender list is recalculated the next time the conversation
     * is instantiated.
     * 
     * @param mbox the mailbox
     * @param ids of the items being deleted
     * @return the ids of any conversation that were purged as a result of this operation
     */
    public static List<Integer> markDeletionTargets(Mailbox mbox, List<Integer> ids) throws ServiceException {
        if (ids == null)
            return null;
        Connection conn = mbox.getOperationConnection();
        String table = getMailItemTableName(mbox);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        for (int i = 0; i < ids.size(); i += DbUtil.IN_CLAUSE_BATCH_SIZE)
            try {
                int count = Math.min(DbUtil.IN_CLAUSE_BATCH_SIZE, ids.size() - i);
                stmt = conn.prepareStatement("UPDATE " + table + ", " +
                            "(SELECT parent_id pid, COUNT(*) count FROM " + getMailItemTableName(mbox) +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id IN" + DbUtil.suitableNumberOfVariables(count) + "AND parent_id IS NOT NULL GROUP BY parent_id) x" +
                            " SET size = size - count, metadata = NULL, mod_metadata = ?, change_date = ?" +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id = pid AND type = " + MailItem.TYPE_CONVERSATION);
                int attr = 1;
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(attr++, mbox.getId());
                for (int index = i; index < i + count; index++)
                    stmt.setInt(attr++, ids.get(index));
                stmt.setInt(attr++, mbox.getOperationChangeID());
                stmt.setInt(attr++, mbox.getOperationTimestamp());
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(attr++, mbox.getId());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw ServiceException.FAILURE("marking deletions for conversations touching items " + ids, e);
            } finally {
                DbPool.closeResults(rs);
                DbPool.closeStatement(stmt);
            }

            return getPurgedConversations(mbox);
    }

    private static List<Integer> getPurgedConversations(Mailbox mbox) throws ServiceException {
        Connection conn = mbox.getOperationConnection();
        List<Integer> purgedConvs = new ArrayList<Integer>();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT id FROM " + getMailItemTableName(mbox) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "type = " + MailItem.TYPE_CONVERSATION + " AND size <= 0");
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(1, mbox.getId());
            rs = stmt.executeQuery();
            while (rs.next())
                purgedConvs.add(rs.getInt(1));
            return purgedConvs;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("getting list of purged conversations", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    /**
     * Deletes the specified <code>MailItem</code> from the <code>mail_item</code>
     * table.  If the object is a <code>Folder</code> or <code>Conversation</code>,
     * deletes any corresponding messages.  Does not delete subfolders.
     */
    public static void delete(MailItem item) throws ServiceException {
        deleteContents(item);
        if (item instanceof VirtualConversation)
            return;

        List<Integer> ids = new ArrayList<Integer>();
        ids.add(item.getId());
        delete(item.getMailbox(), ids);
    }

    /**
     * Deletes <code>MailItem</code>s with the specified ids from the <code>mail_item</code>
     * table.  Assumes that there is no data referencing the specified id's.
     */
    public static void delete(Mailbox mbox, List<Integer> ids) throws ServiceException {
        // trim out any non-persisted items
        if (ids == null || ids.size() == 0)
            return;
        List<Integer> targets = new ArrayList<Integer>();
        for (int id : ids)
            if (id > 0)
                targets.add(id);
        if (targets.size() == 0)
            return;

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        for (int i = 0; i < targets.size(); i += DbUtil.IN_CLAUSE_BATCH_SIZE)
            try {
                int count = Math.min(DbUtil.IN_CLAUSE_BATCH_SIZE, targets.size() - i);
                stmt = conn.prepareStatement("DELETE FROM " + getMailItemTableName(mbox) +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id IN" + DbUtil.suitableNumberOfVariables(count));
                int pos = 1;
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(pos++, mbox.getId());
                for (int index = i; index < i + count; index++)
                    stmt.setInt(pos++, targets.get(index));
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw ServiceException.FAILURE("deleting item(s): " + ids, e);
            } finally {
                DbPool.closeStatement(stmt);
            }
    }

    public static void deleteContents(MailItem item) throws ServiceException {
        String target;
        if (item instanceof VirtualConversation)  target = "id = ?";
        else if (item instanceof Conversation)    target = "parent_id = ?";
        else if (item instanceof SearchFolder)    return;
        else if (item instanceof Folder)          target = "folder_id = ?";
        else                                      return;

        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("DELETE FROM " + getMailItemTableName(item) +
                        " WHERE " + IN_THIS_MAILBOX_AND + target + " AND type NOT IN " + FOLDER_TYPES);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item instanceof VirtualConversation ? ((VirtualConversation) item).getMessageId() : item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("deleting contents for " + MailItem.getNameForType(item) + " " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void writeTombstones(Mailbox mbox, MailItem.TypedIdList tombstones) throws ServiceException {
        if (tombstones == null || tombstones.isEmpty())
            return;

        for (Map.Entry<Byte, List<Integer>> entry : tombstones) {
            byte type = entry.getKey();
            if (type == MailItem.TYPE_CONVERSATION || type == MailItem.TYPE_VIRTUAL_CONVERSATION)
                continue;
            StringBuilder ids = new StringBuilder();
            for (Integer id : entry.getValue()) {
                ids.append(ids.length() == 0 ? "" : ",").append(id);

                // catch overflows of TEXT values; since all chars are ASCII, no need to convert to UTF-8 for length check beforehand
                if (ids.length() > MAX_TEXT_LENGTH - 50) {
                    writeTombstone(mbox, type, ids.toString());
                    ids.setLength(0);
                }
            }

            writeTombstone(mbox, type, ids.toString());
        }
    }

    private static void writeTombstone(Mailbox mbox, byte type, String ids) throws ServiceException {
        if (ids == null || ids.equals(""))
            return;
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("INSERT INTO " + getTombstoneTableName(mbox) +
                        "(" + (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") + "sequence, date, type, ids)" +
                        " VALUES (" + (!DebugConfig.disableMailboxGroup ? "?, " : "") + "?, ?, ?, ?)");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, mbox.getOperationChangeID());
            stmt.setInt(pos++, mbox.getOperationTimestamp());
            stmt.setByte(pos++, type);
            stmt.setString(pos++, ids);
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing tombstones for " + MailItem.getNameForType(type) + "(s): " + ids, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static MailItem.TypedIdList readTombstones(Mailbox mbox, long lastSync) throws ServiceException {
        Connection conn = mbox.getOperationConnection();
        MailItem.TypedIdList tombstones = new MailItem.TypedIdList();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT type, ids FROM " + getTombstoneTableName(mbox) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "sequence > ? AND ids IS NOT NULL" +
            " ORDER BY sequence");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setLong(pos++, lastSync);
            rs = stmt.executeQuery();
            while (rs.next()) {
                byte type = rs.getByte(1);
                String row = rs.getString(2);
                if (row == null || row.equals(""))
                    continue;
                for (String entry : row.split(",")) {
                    try {
                        tombstones.add(type, Integer.parseInt(entry));
                    } catch (NumberFormatException nfe) {
                        ZimbraLog.sync.warn("unparseable TOMBSTONE entry: " + entry);
                    }
                }
            }
            return tombstones;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("reading tombstones since change: " + lastSync, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }


    private static final String FOLDER_TYPES         = "(" + MailItem.TYPE_FOLDER + ',' + MailItem.TYPE_SEARCHFOLDER + ',' + MailItem.TYPE_MOUNTPOINT + ')';
    private static final String FOLDER_AND_TAG_TYPES = "(" + MailItem.TYPE_FOLDER + ',' + MailItem.TYPE_SEARCHFOLDER + ',' + MailItem.TYPE_MOUNTPOINT + ',' + MailItem.TYPE_TAG + ')';
    private static final String NON_SEARCHABLE_TYPES = "(" + MailItem.TYPE_FOLDER + ',' + MailItem.TYPE_SEARCHFOLDER + ',' + MailItem.TYPE_MOUNTPOINT + ',' + MailItem.TYPE_TAG + ',' + MailItem.TYPE_CONVERSATION + ')';
    private static final String DOCUMENT_TYPES       = "(" + MailItem.TYPE_DOCUMENT + ',' + MailItem.TYPE_WIKI + ')';

    private static String typeConstraint(byte type) {
        if (type == MailItem.TYPE_FOLDER)
            return FOLDER_TYPES;
        else if (type == MailItem.TYPE_DOCUMENT)
            return DOCUMENT_TYPES;
        else
            return "(" + type + ')';
    }

    public static final byte SORT_DESCENDING = 0x00;
    public static final byte SORT_ASCENDING  = 0x01;

    public static final byte SORT_BY_DATE    = 0x00;
    public static final byte SORT_BY_SENDER  = 0x02;
    public static final byte SORT_BY_SUBJECT = 0x04;
    public static final byte SORT_BY_ID      = 0x08;
    public static final byte SORT_NONE       = 0x10;
    public static final byte SORT_BY_NAME    = 0x20;

    public static final byte DEFAULT_SORT_ORDER = SORT_BY_DATE | SORT_DESCENDING;

    public static final byte SORT_DIRECTION_MASK = 0x01;
    public static final byte SORT_FIELD_MASK     = 0x4E;

    private static String sortField(byte sort) {
        switch (sort & SORT_FIELD_MASK) {
            case SORT_BY_SENDER:   return "sender";
            case SORT_BY_SUBJECT:  return "subject";
            case SORT_BY_NAME:     return "name";
            case SORT_BY_ID:       return "id";
            case SORT_NONE:        return "NULL";
            case SORT_BY_DATE:
            default:               return "date";
        }
    }

    private static String sortQuery(byte sort) {
        return sortQuery(sort, "");
    }
    private static String sortQuery(byte sort, String prefix) {
        String field = sortField(sort);
        if ("NULL".equalsIgnoreCase(field))
            return "";
        StringBuilder statement = new StringBuilder(" ORDER BY ");
        statement.append(prefix).append(field);
        if ((sort & SORT_DIRECTION_MASK) == SORT_DESCENDING)
            statement.append(" DESC");
        return statement.toString();
    }

    // Indexes on mail_item table
    private static final String MI_I_MBOX_FOLDER_DATE = "i_folder_id_date";
//    private static final String MI_I_MBOX_ID_PKEY     = "PRIMARY";
//    private static final String MI_I_MBOX_PARENT      = "i_parent_id";
//    private static final String MI_I_MBOX_INDEX       = "i_index_id";
//    private static final String MI_I_MBOX_DATE        = "i_date";
//    private static final String MI_I_MBOX_TAGS_DATE   = "i_tags_date";
//    private static final String MI_I_MBOX_FLAGS_DATE  = "i_flags_date";
//    private static final String MI_I_MBOX_TYPE        = "i_type";
//    private static final String MI_I_MBOX_UNREAD      = "i_unread";
//    private static final String MI_I_MBOX_MODMETADATA = "i_mod_metadata";
//    private static final String MI_I_MBOX_FOLDER_NAME = "i_name_folder_id";

    private static final String NO_HINT = "";

    private static String getForceIndexClause(
            DbSearchConstraintsNode node, byte sortInfo, boolean hasLimit) {
        if (LC.search_disable_database_hints.booleanValue()) return NO_HINT;

        int sortBy = sortInfo & SORT_FIELD_MASK;
        String index = null;

        DbSearchConstraintsNode.NodeType ntype = node.getNodeType();
        if (sortBy == SORT_BY_DATE && hasLimit && ntype == DbSearchConstraintsNode.NodeType.LEAF) {
            DbSearchConstraints constraints = node.getSearchConstraints();
            if (constraints.isSimpleSingleFolderMessageQuery()) {
                // Optimization for folder query
                //
                // If looking at a single folder and sorting by date with a limit,
                // force the use of i_folder_id_date index.  Typical example of
                // such a query is the default "in:Inbox" search.
                index = MI_I_MBOX_FOLDER_DATE;
            }
        }

        // Whenever we learn a new case of mysql choosing wrong index, add
        // a case here.

        if (index != null)
            return " FORCE INDEX (" + index + ")";
        else
            return NO_HINT;
    }


    public static Mailbox.MailboxData getFoldersAndTags(Mailbox mbox, Map<Integer, UnderlyingData> folderData, Map<Integer, UnderlyingData> tagData, boolean reload)
    throws ServiceException {
        boolean fetchFolders = folderData != null;
        boolean fetchTags    = tagData != null;
        if (!fetchFolders && !fetchTags && !reload)
            return null;
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String table = getMailItemTableName(mbox, "mi");

            stmt = conn.prepareStatement("SELECT " + DB_FIELDS + " FROM " + table +
                        " WHERE " + IN_THIS_MAILBOX_AND + "type IN " + FOLDER_AND_TAG_TYPES);
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(1, mbox.getId());
            rs = stmt.executeQuery();
            while (rs.next()) {
                UnderlyingData data = constructItem(rs);
                if (fetchFolders && MailItem.isAcceptableType(MailItem.TYPE_FOLDER, data.type))
                    folderData.put(data.id, data);
                else if (fetchTags && MailItem.isAcceptableType(MailItem.TYPE_TAG, data.type))
                    tagData.put(data.id, data);

                rs.getInt(CI_UNREAD);
                reload |= rs.wasNull();
            }

            if (!reload)
                return null;

            rs.close();
            stmt.close();

            // going to recalculate counts, so discard any existing counts...
            if (fetchFolders)
                for (UnderlyingData data : folderData.values())
                    data.size = data.unreadCount = 0;
            if (fetchTags)
                for (UnderlyingData data : tagData.values())
                    data.size = data.unreadCount = 0;

            Mailbox.MailboxData mbd = new Mailbox.MailboxData();
            stmt = conn.prepareStatement("SELECT folder_id, type, tags, COUNT(*), SUM(unread), SUM(size)" +
                        " FROM " + table + " WHERE " + IN_THIS_MAILBOX_AND + "type NOT IN " + NON_SEARCHABLE_TYPES +
            " GROUP BY folder_id, type, tags");
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(1, mbox.getId());
            rs = stmt.executeQuery();
            while (rs.next()) {
                byte type  = rs.getByte(2);
                int count  = rs.getInt(4);
                int unread = rs.getInt(5);
                long size  = rs.getLong(6);
                if (type == MailItem.TYPE_CONTACT)
                    mbd.contacts += count;
                mbd.size += size;

                if (fetchFolders) {
                    UnderlyingData data = folderData.get(rs.getInt(1));
                    assert(data != null);
                    data.unreadCount += unread;
                    data.size += count;
                }

                if (fetchTags) {
                    long tags = rs.getLong(3);
                    for (int i = 0; tags != 0 && i < MailItem.MAX_TAG_COUNT - 1; i++) {
                        if ((tags & (1L << i)) != 0) {
                            UnderlyingData data = tagData.get(i + MailItem.TAG_ID_OFFSET);
                            if (data != null)
                                data.unreadCount += unread;
                            // could track cumulative count if desired...
                            tags &= ~(1L << i);
                        }
                    }
                }
            }
            return mbd;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching folder data for mailbox " + mbox.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static List<UnderlyingData> getByType(Mailbox mbox, byte type, byte sort) throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
        Connection conn = mbox.getOperationConnection();

        ArrayList<UnderlyingData> result = new ArrayList<UnderlyingData>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(mbox, " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "type IN " + typeConstraint(type) + sortQuery(sort));
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(1, mbox.getId());
            rs = stmt.executeQuery();
            while (rs.next())
                result.add(constructItem(rs));

            if (type == MailItem.TYPE_CONVERSATION)
                completeConversations(mbox, result);
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching items of type " + type, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static List<UnderlyingData> getByParent(MailItem parent) throws ServiceException {
        return getByParent(parent, DEFAULT_SORT_ORDER);
    }
    public static List<UnderlyingData> getByParent(MailItem parent, byte sort) throws ServiceException {
        Mailbox mbox = parent.getMailbox();
        Connection conn = mbox.getOperationConnection();
        ArrayList<UnderlyingData> result = new ArrayList<UnderlyingData>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(parent.getMailbox(), " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "parent_id = ? " + sortQuery(sort));
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, parent.getId());
            rs = stmt.executeQuery();

            while (rs.next()) {
                UnderlyingData data = constructItem(rs);
                if (Mailbox.isCachedType(data.type))
                    throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
                result.add(data);
            }
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching children of item " + parent.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static List<UnderlyingData> getUnreadMessages(MailItem relativeTo) throws ServiceException {
        Mailbox mbox = relativeTo.getMailbox();
        Connection conn = mbox.getOperationConnection();
        ArrayList<UnderlyingData> result = new ArrayList<UnderlyingData>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String relation;
            if (relativeTo instanceof VirtualConversation)  relation = " id = ?";
            else if (relativeTo instanceof Conversation)    relation = " parent_id = ?";
            else if (relativeTo instanceof Folder)          relation = " folder_id = ?";
            else if (relativeTo instanceof Flag)            relation = " flags & ?";
            else if (relativeTo instanceof Tag)             relation = " tags & ?";
            else                                            relation = " id = ?";

            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(relativeTo.getMailbox(), " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "unread > 0 AND " + relation + " AND type NOT IN " + NON_SEARCHABLE_TYPES);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            if (relativeTo instanceof Tag)
                stmt.setLong(pos++, ((Tag) relativeTo).getBitmask());
            else if (relativeTo instanceof VirtualConversation)
                stmt.setInt(pos++, ((VirtualConversation) relativeTo).getMessageId());
            else
                stmt.setInt(pos++, relativeTo.getId());
            rs = stmt.executeQuery();

            while (rs.next()) {
                UnderlyingData data = constructItem(rs);
                if (Mailbox.isCachedType(data.type))
                    throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
                result.add(data);
            }
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching unread messages for item " + relativeTo.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static List<UnderlyingData> getByFolder(Folder folder, byte type, byte sort) throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
        Mailbox mbox = folder.getMailbox();
        Connection conn = mbox.getOperationConnection();

        ArrayList<UnderlyingData> result = new ArrayList<UnderlyingData>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(folder.getMailbox(), " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND type IN " + typeConstraint(type) +
                        sortQuery(sort));
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folder.getId());
            rs = stmt.executeQuery();

            while (rs.next())
                result.add(constructItem(rs));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching items in folder " + folder.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static UnderlyingData getById(Mailbox mbox, int id, byte type) throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(mbox, "mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, id);
            rs = stmt.executeQuery();

            if (!rs.next())
                throw MailItem.noSuchItem(id, type);
            UnderlyingData data = constructItem(rs);
            if (!MailItem.isAcceptableType(type, data.type))
                throw MailItem.noSuchItem(id, type);
            if (data.type == MailItem.TYPE_CONVERSATION)
                completeConversation(mbox, data);
            return data;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching item " + id, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static UnderlyingData getByImapId(Mailbox mbox, int imapId, int folderId) throws ServiceException {
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(mbox, "mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND imap_id = ?");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folderId);
            stmt.setInt(pos++, imapId);
            rs = stmt.executeQuery();

            if (!rs.next())
                throw MailServiceException.NO_SUCH_ITEM(imapId);
            UnderlyingData data = constructItem(rs);
            if (data.type == MailItem.TYPE_CONVERSATION)
                throw MailServiceException.NO_SUCH_ITEM(imapId);
            return data;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching item " + imapId, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static List<UnderlyingData> getById(Mailbox mbox, Collection<Integer> ids, byte type) throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);

        List<UnderlyingData> result = new ArrayList<UnderlyingData>();
        if (ids.isEmpty())
            return result;
        List<UnderlyingData> conversations = new ArrayList<UnderlyingData>();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Iterator<Integer> it = ids.iterator();
        for (int i = 0; i < ids.size(); i += DbUtil.IN_CLAUSE_BATCH_SIZE)
            try {
                int count = Math.min(DbUtil.IN_CLAUSE_BATCH_SIZE, ids.size() - i);
                stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                            " FROM " + getMailItemTableName(mbox, "mi") +
                            " WHERE " + IN_THIS_MAILBOX_AND + "id IN " + DbUtil.suitableNumberOfVariables(count));
                int pos = 1;
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(pos++, mbox.getId());
                for (int index = i; index < i + count; index++)
                    stmt.setInt(pos++, it.next());

                rs = stmt.executeQuery();
                while (rs.next()) {
                    UnderlyingData data = constructItem(rs);
                    if (!MailItem.isAcceptableType(type, data.type))
                        throw MailItem.noSuchItem(data.id, type);
                    else if (Mailbox.isCachedType(data.type))
                        throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
                    if (data.type == MailItem.TYPE_CONVERSATION)
                        conversations.add(data);
                    result.add(data);
                }
            } catch (SQLException e) {
                throw ServiceException.FAILURE("fetching items: " + ids, e);
            } finally {
                DbPool.closeResults(rs);
                DbPool.closeStatement(stmt);
            }

            if (!conversations.isEmpty())
                completeConversations(mbox, conversations);
            return result;
    }

    public static UnderlyingData getByName(Mailbox mbox, int folderId, String name, byte type) throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(mbox, "mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND name = ? AND type IN " + typeConstraint(type));
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folderId);
            stmt.setString(pos++, name);
            rs = stmt.executeQuery();

            if (!rs.next())
                throw MailItem.noSuchItem(-1, type);
            UnderlyingData data = constructItem(rs);
            if (!MailItem.isAcceptableType(type, data.type))
                throw MailItem.noSuchItem(data.id, type);
            if (data.type == MailItem.TYPE_CONVERSATION)
                completeConversation(mbox, data);
            return data;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching item by name ('" + name + "' in folder " + folderId + ")", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static UnderlyingData getByHash(Mailbox mbox, String hash) throws ServiceException {
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getMailItemTableName(mbox, "mi") + ", " +
                        getConversationTableName(mbox, "oc") +
                        " WHERE " +
                        (!DebugConfig.disableMailboxGroup ? "oc.mailbox_id = ? AND " : "") +
                        "oc.hash = ? AND " +
                        (!DebugConfig.disableMailboxGroup ? "mi.mailbox_id = oc.mailbox_id AND " : "") +
            "mi.id = oc.conv_id");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setString(pos++, hash);
            rs = stmt.executeQuery();

            if (!rs.next())
                return null;
            UnderlyingData data = constructItem(rs);
            if (data.type == MailItem.TYPE_CONVERSATION)
                completeConversation(mbox, data);
            return data;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching conversation for hash " + hash, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    private static final List<Integer> EMPTY_DATA = Collections.emptyList();
    private static final MailItem.TypedIdList EMPTY_TYPED_ID_LIST = new MailItem.TypedIdList();

    public static Pair<List<Integer>,MailItem.TypedIdList> getModifiedItems(Mailbox mbox, byte type, long lastSync, Set<Integer> visible)
    throws ServiceException {
        if (Mailbox.isCachedType(type))
            throw ServiceException.INVALID_REQUEST("folders and tags must be retrieved from cache", null);

        // figure out what folders are visible and thus also if we can short-circuit this query
        if (visible != null && visible.isEmpty())
            return new Pair<List<Integer>,MailItem.TypedIdList>(EMPTY_DATA, EMPTY_TYPED_ID_LIST);

        Connection conn = mbox.getOperationConnection();
        List<Integer> modified = new ArrayList<Integer>();
        MailItem.TypedIdList missed = new MailItem.TypedIdList();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String typeConstraint = type == MailItem.TYPE_UNKNOWN ? "type NOT IN " + NON_SEARCHABLE_TYPES : "type IN " + typeConstraint(type);
            stmt = conn.prepareStatement("SELECT id, type, folder_id" +
                        " FROM " + getMailItemTableName(mbox) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "mod_metadata > ? AND " + typeConstraint +
                        " ORDER BY mod_metadata, id");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setLong(pos++, lastSync);
            rs = stmt.executeQuery();

            while (rs.next()) {
                if (visible == null || visible.contains(rs.getInt(3)))
                    modified.add(rs.getInt(1));
                else
                    missed.add(rs.getByte(2), rs.getInt(1));
            }

            return new Pair<List<Integer>,MailItem.TypedIdList>(modified, missed);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("getting items modified since " + lastSync, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    private static void completeConversation(Mailbox mbox, UnderlyingData data) throws ServiceException {
        List<UnderlyingData> list = new ArrayList<UnderlyingData>();
        list.add(data);
        completeConversations(mbox, list);
    }
    private static void completeConversations(Mailbox mbox, List<UnderlyingData> convData) throws ServiceException {
        if (convData == null || convData.isEmpty())
            return;
        Map<Integer, UnderlyingData> conversations = new HashMap<Integer, UnderlyingData>();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;

        for (int i = 0; i < convData.size(); i += DbUtil.IN_CLAUSE_BATCH_SIZE)
            try {
                int count = Math.min(DbUtil.IN_CLAUSE_BATCH_SIZE, convData.size() - i);
                String sql = "SELECT parent_id, id, unread, flags, tags" +
                " FROM " + getMailItemTableName(mbox) +
                " WHERE " + IN_THIS_MAILBOX_AND + "parent_id IN " + DbUtil.suitableNumberOfVariables(count) +
                " ORDER BY parent_id";
                stmt = conn.prepareStatement(sql);
                int pos = 1;
                if (!DebugConfig.disableMailboxGroup)
                    stmt.setInt(pos++, mbox.getId());
                for (int index = i; index < i + count; index++) {
                    UnderlyingData data = convData.get(index);
                    assert(data.type == MailItem.TYPE_CONVERSATION);
                    stmt.setInt(pos++, data.id);
                    conversations.put(data.id, data);
                }
                rs = stmt.executeQuery();

                int lastConvId = -1;
                List<Long> inheritedTags = new ArrayList<Long>();
                List<Integer> children = new ArrayList<Integer>();
                int unreadCount = 0;

                while (rs.next()) {
                    int convId = rs.getInt(1);
                    if (convId != lastConvId) {
                        // New conversation.  Update stats for the last one and reset counters.
                        if (lastConvId != -1) {
                            // Update stats for the previous conversation
                            UnderlyingData data = conversations.get(lastConvId);
                            data.children      = children;
                            data.unreadCount   = unreadCount;
                            data.inheritedTags = StringUtil.join(",", inheritedTags);
                        }
                        lastConvId = convId;
                        children = new ArrayList<Integer>();
                        inheritedTags.clear();
                        unreadCount = 0;
                    }

                    // Read next row
                    children.add(rs.getInt(2));
                    if (rs.getBoolean(3))
                        unreadCount++;
                    inheritedTags.add(-rs.getLong(4));
                    inheritedTags.add(rs.getLong(5));
                }

                // Update the last conversation.
                UnderlyingData data = conversations.get(lastConvId);
                if (data != null) {
                    data.children      = children;
                    data.unreadCount   = unreadCount;
                    data.inheritedTags = StringUtil.join(",", inheritedTags);
                } else {
                    // Data error: no messages found
                    StringBuilder msg = new StringBuilder("No messages found for conversations:");
                    for (UnderlyingData ud : convData) {
                        msg.append(' ').append(ud.id);
                    }
                    msg.append(".  lastConvId=").append(lastConvId);
                    sLog.error(msg);
                }
            } catch (SQLException e) {
                throw ServiceException.FAILURE("completing conversation data", e);
            } finally {
                DbPool.closeResults(rs);
                DbPool.closeStatement(stmt);
            }
    }

    private static final String LEAF_NODE_FIELDS = "id, size, type, unread, folder_id," +
    " parent_id IS NULL, blob_digest IS NOT NULL," +
    " mod_content, mod_metadata," +
    " flags & " + Flag.BITMASK_COPIED + ", index_id, volume_id";

    private static final int LEAF_CI_ID           = 1;
    private static final int LEAF_CI_SIZE         = 2;
    private static final int LEAF_CI_TYPE         = 3;
    private static final int LEAF_CI_IS_UNREAD    = 4;
    private static final int LEAF_CI_FOLDER_ID    = 5;
    private static final int LEAF_CI_IS_NOT_CHILD = 6;
    private static final int LEAF_CI_HAS_BLOB     = 7;
    private static final int LEAF_CI_MOD_CONTENT  = 8;
    private static final int LEAF_CI_MOD_METADATA = 9;
    private static final int LEAF_CI_IS_COPIED    = 10;
    private static final int LEAF_CI_INDEX_ID     = 11;
    private static final int LEAF_CI_VOLUME_ID    = 12;

    public static PendingDelete getLeafNodes(Folder folder) throws ServiceException {
        Mailbox mbox = folder.getMailbox();
        PendingDelete info = new PendingDelete();
        int folderId = folder.getId();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + LEAF_NODE_FIELDS +
                        " FROM " + getMailItemTableName(folder) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND type NOT IN " + FOLDER_TYPES);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folderId);
            rs = stmt.executeQuery();

            info.rootId = folderId;
            info.size   = 0;
            accumulateLeafNodes(info, mbox, rs);
            // make sure that the folder is in the list of deleted item ids
            info.itemIds.add(folder.getType(), folderId);

            return info;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching list of items within item " + folder.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static PendingDelete getLeafNodes(Mailbox mbox, List<Folder> folders, int before, boolean globalMessages) throws ServiceException {
        PendingDelete info = new PendingDelete();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String constraint;
            if (globalMessages)
                constraint = "date < ? AND type = " + MailItem.TYPE_MESSAGE;
            else
                constraint = "date < ? AND type NOT IN " + NON_SEARCHABLE_TYPES +
                " AND folder_id IN" + DbUtil.suitableNumberOfVariables(folders);

            stmt = conn.prepareStatement("SELECT " + LEAF_NODE_FIELDS +
                        " FROM " + getMailItemTableName(mbox) +
                        " WHERE " + IN_THIS_MAILBOX_AND + constraint);
            int attr = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(attr++, mbox.getId());
            stmt.setInt(attr++, before);
            if (!globalMessages)
                for (Folder folder : folders)
                    stmt.setInt(attr++, folder.getId());
            rs = stmt.executeQuery();

            info.rootId = 0;
            info.size   = 0;
            return accumulateLeafNodes(info, mbox, rs);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching list of items for purge", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static class LocationCount {
        public int count;
        public long size;
        public LocationCount(int c, long sz)            { count = c;  size = sz; }
        public LocationCount increment(int c, long sz)  { count += c;  size += sz;  return this; }
    }

    private static PendingDelete accumulateLeafNodes(PendingDelete info, Mailbox mbox, ResultSet rs) throws SQLException, ServiceException {
        StoreManager sm = StoreManager.getInstance();

        while (rs.next()) {
            // first check to make sure we don't have a modify conflict
            int revision = rs.getInt(LEAF_CI_MOD_CONTENT);
            int modMetadata = rs.getInt(LEAF_CI_MOD_METADATA);
            if (!mbox.checkItemChangeID(modMetadata, revision)) {
                info.incomplete = true;
                continue;
            }

            int id = rs.getInt(LEAF_CI_ID);
            int size = rs.getInt(LEAF_CI_SIZE);
            byte type = rs.getByte(LEAF_CI_TYPE);
            Integer item = new Integer(id);
            info.itemIds.add(type, item);

            info.size += size;
            if (rs.getBoolean(LEAF_CI_IS_UNREAD))
                info.unreadIds.add(item);
            boolean isMessage = false;
            switch (type) {
                case MailItem.TYPE_CONTACT:  info.contacts++;   break;
                case MailItem.TYPE_MESSAGE:  isMessage = true;  break;
            }
            // detect deleted virtual conversations
            if (isMessage && rs.getBoolean(LEAF_CI_IS_NOT_CHILD))
                info.itemIds.add(MailItem.TYPE_VIRTUAL_CONVERSATION, -id);

            Integer folderId = rs.getInt(LEAF_CI_FOLDER_ID);
            LocationCount count = info.messages.get(folderId);
            if (count == null)
                info.messages.put(folderId, new LocationCount(1, size));
            else
                count.increment(1, size);

            boolean hasBlob = rs.getBoolean(LEAF_CI_HAS_BLOB);
            if (hasBlob) {
                short volumeId = rs.getShort(LEAF_CI_VOLUME_ID);
                try {
                    MailboxBlob mblob = sm.getMailboxBlob(mbox, id, revision, volumeId);
                    if (mblob == null)
                        sLog.error("missing blob for id: " + id + ", change: " + revision);
                    else
                        info.blobs.add(mblob);
                } catch (Exception e1) { }
            }

            Integer indexId = new Integer(rs.getInt(LEAF_CI_INDEX_ID));
            boolean indexed = !rs.wasNull();
            if (indexed) {
                if (info.sharedIndex == null)
                    info.sharedIndex = new HashSet<Integer>();
                boolean shared = rs.getBoolean(LEAF_CI_IS_COPIED);
                if (!shared)  info.indexIds.add(indexId);
                else          info.sharedIndex.add(indexId);
            }
        }

        return info;
    }

    public static void resolveSharedIndex(Mailbox mbox, PendingDelete info) throws ServiceException {
        if (info.sharedIndex == null || info.sharedIndex.isEmpty())
            return;

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT index_id FROM " + getMailItemTableName(mbox) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "index_id IN " + DbUtil.suitableNumberOfVariables(info.sharedIndex));
            int attr = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(attr++, mbox.getId());
            for (int id : info.sharedIndex)
                stmt.setInt(attr++, id);
            rs = stmt.executeQuery();

            while (rs.next())
                info.sharedIndex.remove(rs.getInt(1));
            info.indexIds.addAll(info.sharedIndex);
            info.sharedIndex.clear();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("resolving shared index entries: " + info.rootId, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }


    private static final String IMAP_FIELDS = "mi.id, mi.type, mi.imap_id, mi.unread, mi.flags, mi.tags";
    private static final String IMAP_TYPES = "(" + MailItem.TYPE_MESSAGE + "," + MailItem.TYPE_CONTACT + ")";

    public static List<ImapMessage> loadImapFolder(Folder folder) throws ServiceException {
        Mailbox mbox = folder.getMailbox();
        List<ImapMessage> result = new ArrayList<ImapMessage>();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + IMAP_FIELDS +
                        " FROM " + getMailItemTableName(folder.getMailbox(), " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND type IN " + IMAP_TYPES);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folder.getId());
            rs = stmt.executeQuery();

            while (rs.next()) {
                int flags = rs.getBoolean(4) ? Flag.BITMASK_UNREAD | rs.getInt(5) : rs.getInt(5);
                result.add(new ImapMessage(rs.getInt(1), rs.getByte(2), rs.getInt(3), flags, rs.getLong(6)));
            }
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("loading IMAP folder data: " + folder.getPath(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    private static final String POP3_FIELDS = "mi.id, mi.size, mi.blob_digest";
    private static final String POP3_TYPES = "(" + MailItem.TYPE_MESSAGE + ")";

    public static List<Pop3Message> loadPop3Folder(Folder folder) throws ServiceException {
        Mailbox mbox = folder.getMailbox();
        List<Pop3Message> result = new ArrayList<Pop3Message>();

        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT " + POP3_FIELDS +
                        " FROM " + getMailItemTableName(folder.getMailbox(), " mi") +
                        " WHERE " + IN_THIS_MAILBOX_AND + "folder_id = ? AND type IN " + POP3_TYPES +
                        " AND NOT (flags & " + Flag.BITMASK_DELETED + ")");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, folder.getId());
            rs = stmt.executeQuery();

            while (rs.next())
                result.add(new Pop3Message(rs.getInt(1), rs.getInt(2), rs.getString(3)));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("loading POP3 folder data: " + folder.getPath(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }


    public static final class SearchResult {
        public int    id;
        public int    indexId;
        public byte   type;
        public Object sortkey;
        public UnderlyingData data; // OPTIONAL
        public ImapMessage i4msg; // OPTIONAL

        public enum ExtraData { NONE, MAIL_ITEM, IMAP_MSG };

        public static class SizeEstimate {
            public SizeEstimate() {}
            public SizeEstimate(int initialval) { mSizeEstimate = initialval; }
            public int mSizeEstimate;
        }


        public static SearchResult createResult(ResultSet rs, byte sort) throws SQLException {
            return createResult(rs, sort, ExtraData.NONE);
        }
        public static SearchResult createResult(ResultSet rs, byte sort, ExtraData extra) throws SQLException {
            int sortField = (sort & SORT_FIELD_MASK);

            SearchResult result = new SearchResult();
            result.id      = rs.getInt(1);
            result.indexId = rs.getInt(2);
            result.type    = rs.getByte(3);
            if (sortField == SORT_BY_SUBJECT || sortField == SORT_BY_SENDER || sortField == SORT_BY_NAME)
                result.sortkey = rs.getString(4);
            else
                result.sortkey = new Long(rs.getInt(4) * 1000L);

            if (extra == ExtraData.MAIL_ITEM) {
                result.data = constructItem(rs, 4);
            } else if (extra == ExtraData.IMAP_MSG) {
                int flags = rs.getBoolean(6) ? Flag.BITMASK_UNREAD | rs.getInt(7) : rs.getInt(7);
                result.i4msg = new ImapMessage(result.id, result.type, rs.getInt(5), flags, rs.getLong(8));
            }
            return result;
        }

        public String toString() {
            return sortkey + " => (" + id + "," + type + ")";
        }

        public int hashCode() {
            return id;
        }

        public boolean equals(Object obj) {
            SearchResult other = (SearchResult) obj;
            return other.id == id;
        }
    }

    public static Collection<SearchResult> search(Connection conn, DbSearchConstraints c) throws ServiceException {
        return search(new ArrayList<SearchResult>(), conn, c, SearchResult.ExtraData.NONE);
    }
    public static Collection<SearchResult> search(Connection conn, DbSearchConstraints c, SearchResult.ExtraData extra) throws ServiceException {
        return search(new ArrayList<SearchResult>(), conn, c, extra);
    }
    public static Collection<SearchResult> search(Collection<SearchResult> result, Connection conn, DbSearchConstraints c) throws ServiceException {
        return search(result, conn, c, SearchResult.ExtraData.NONE);
    }
    public static Collection<SearchResult> search(Collection<SearchResult> result, Connection conn, DbSearchConstraints c, SearchResult.ExtraData extra) throws ServiceException {
        return search(result, conn, c, c.mailbox, c.sort, c.offset, c.limit, extra);
    }
    public static int countResults(Connection conn, DbSearchConstraintsNode node, Mailbox mbox) throws ServiceException {
        int mailboxId = mbox.getId();
        // Assemble the search query
        StringBuilder statement = new StringBuilder("SELECT count(*) ");
        statement.append(" FROM " + getMailItemTableName(mbox, "mi"));
        statement.append(" WHERE ");
        if (!DebugConfig.disableMailboxGroup)
            statement.append("mailbox_id = ? AND ");
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            /*
             * SELECT id,date FROM mail_item mi WHERE mi.acccount_id = ? AND type = ? AND tags & ? = ? AND flags & ? = ?
             *    (AND folder_id [NOT] IN (?,?,?)) (AND date > ?) (AND date < ?) (AND mod_metadata > ?) (AND mod_metadata < ?)
             *    ORDER BY date|subject|sender|name (DESC)? LIMIT ?, ?
             */
            encodeConstraint(mbox, node, statement, conn);

            stmt = conn.prepareStatement(statement.toString());
            int param = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(param++, mailboxId);
            param = setSearchVars(stmt, node, param++);

            // FIXME: include COLLATION for sender/subject sort

            if (sLog.isDebugEnabled())
                sLog.debug("SQL: " + statement);

            rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching search metadata", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static Collection<SearchResult> search(
            Collection<SearchResult> result, Connection conn,
            DbSearchConstraintsNode node, 
            Mailbox mbox, byte sort, int offset, int limit,
            SearchResult.ExtraData extra)
    throws ServiceException {
        int mailboxId = mbox.getId();
//      Assemble the search query
        StringBuilder select = new StringBuilder("SELECT id, index_id, type, " + sortField(sort));
        if (extra == SearchResult.ExtraData.MAIL_ITEM)
            select.append(", " + DB_FIELDS);
        else if (extra == SearchResult.ExtraData.IMAP_MSG)
            select.append(", mi.imap_id, mi.unread, mi.flags, mi.tags");
        select.append(" FROM " + getMailItemTableName(mbox, "mi"));

        StringBuilder where = new StringBuilder(" WHERE ");
        if (!DebugConfig.disableMailboxGroup)
            where.append("mailbox_id = ? AND ");

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            /*
             * SELECT id,date FROM mail_item mi WHERE mi.acccount_id = ? AND type = ? AND tags & ? = ? AND flags & ? = ?
             *    (AND folder_id [NOT] IN (?,?,?)) (AND date > ?) (AND date < ?) (AND mod_metadata > ?) (AND mod_metadata < ?)
             *    ORDER BY date|subject|sender|name (DESC)? LIMIT ?, ?
             */
            encodeConstraint(mbox, node, where, conn);

            where.append(sortQuery(sort));
            boolean hasLimit = false;
            if (offset >= 0 && limit >= 0) {
                hasLimit = true;
                where.append(" LIMIT ?, ?");
            }

            String forceIndex = getForceIndexClause(node, sort, hasLimit);
            select.append(forceIndex).append(where);
            stmt = conn.prepareStatement(select.toString());
            int param = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(param++, mailboxId);
            param = setSearchVars(stmt, node, param++);

//          FIXME: include COLLATION for sender/subject sort

            if (offset >= 0 && limit >= 0) {
                stmt.setInt(param++, offset);
                stmt.setInt(param++, limit);
            }

            if (sLog.isDebugEnabled())
                sLog.debug("SQL: " + select);

            rs = stmt.executeQuery();
            while (rs.next())
                result.add(SearchResult.createResult(rs, sort, extra));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching search metadata", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }


    private static void encodeConstraint(
            Mailbox mbox, DbSearchConstraintsNode node,
            StringBuilder statement, Connection conn)
    throws ServiceException {
        DbSearchConstraintsNode.NodeType ntype = node.getNodeType();
        if (ntype == DbSearchConstraintsNode.NodeType.AND || ntype == DbSearchConstraintsNode.NodeType.OR) {
            boolean first = true, and = ntype == DbSearchConstraintsNode.NodeType.AND;
            statement.append('(');
            for (DbSearchConstraintsNode subnode : node.getSubNodes()) {
                if (!first)
                    statement.append(and ? " AND " : " OR ");
                encodeConstraint(mbox, subnode, statement, conn);
                first = false;
            }
            statement.append(')');
            return;
        }

        // we're here, so we must be in a DbSearchConstraints leaf node
        DbSearchConstraints c = node.getSearchConstraints();
        assert(ntype == DbSearchConstraintsNode.NodeType.LEAF && c != null);
        c.checkDates();

        // if there are no possible matches, short-circuit here...
        if (c.automaticEmptySet()) {
            statement.append("FALSE");
            return;
        }

        statement.append('(');

        if (ListUtil.isEmpty(c.types)) {
            statement.append("type NOT IN " + NON_SEARCHABLE_TYPES);
        } else {
            int size = c.types.size();
            if (size == 1)
                statement.append("type = ?");
            else
                statement.append("type IN").append(DbUtil.suitableNumberOfVariables(c.types));
        }

        if (!ListUtil.isEmpty(c.excludeTypes))
            statement.append(" AND type NOT IN").append(DbUtil.suitableNumberOfVariables(c.excludeTypes));

        // Determine the set of matching tags
        TagConstraints tc = TagConstraints.getTagContraints(mbox, c, conn);
        if (tc.noMatches)
            statement.append(" AND FALSE");

        if (c.hasTags != null)
            statement.append(" AND tags ").append(c.hasTags.booleanValue() ? "!= 0" : "= 0");
        if (tc.searchTagsets != null) {
            int size = tc.searchTagsets.size();
            if (size == 1)
                statement.append(" AND tags = ?");
            else
                statement.append(" AND tags IN").append(DbUtil.suitableNumberOfVariables(size));
        }
        if (tc.searchFlagsets != null) {
            int size = tc.searchFlagsets.size();
            if (size == 1)
                statement.append(" AND flags = ?");
            else
                statement.append(" AND flags IN").append(DbUtil.suitableNumberOfVariables(size));
        }
        if (tc.unread != null)
            statement.append(" AND unread = ?");

        Collection<Folder> targetFolders = (!ListUtil.isEmpty(c.folders)) ? c.folders : c.excludeFolders;
        if (!ListUtil.isEmpty(targetFolders)) {
            int size = targetFolders.size();
            if (size == 1) {
                if (targetFolders == c.folders)
                    statement.append(" AND folder_id = ?");
                else
                    statement.append(" AND folder_id != ?");
            } else
                statement.append(" AND folder_id").
                          append(targetFolders == c.folders ? "" : " NOT").append(" IN").
                          append(DbUtil.suitableNumberOfVariables(size));
        }

        if (c.convId > 0)
            statement.append(" AND parent_id = ?");
        else if (!ListUtil.isEmpty(c.prohibitedConvIds))
            statement.append(" AND parent_id NOT IN").append(DbUtil.suitableNumberOfVariables(c.prohibitedConvIds));

        if (!ListUtil.isEmpty(c.itemIds)) {
            int size= c.itemIds.size();
            if (size == 1)
                statement.append(" AND id = ?");
            else
                statement.append(" AND id IN").append(DbUtil.suitableNumberOfVariables(c.itemIds));
        }
        if (!ListUtil.isEmpty(c.prohibitedItemIds))
            statement.append(" AND id NOT IN").append(DbUtil.suitableNumberOfVariables(c.prohibitedItemIds));

        if (!ListUtil.isEmpty(c.indexIds)) {
            int size = c.indexIds.size();
            if (size == 1)
                statement.append(" AND index_id = ?");
            else
                statement.append(" AND index_id IN").append(DbUtil.suitableNumberOfVariables(size));
        }

        if (!ListUtil.isEmpty(c.dates))
            encodeRanges(c.dates, "date", 1, statement);

        if (!ListUtil.isEmpty(c.modified))
            encodeRanges(c.modified, "mod_metadata", 1, statement);

        if (!ListUtil.isEmpty(c.sizes))
            encodeRanges(c.sizes, "size", 0, statement);

        if (!ListUtil.isEmpty(c.subjectRanges))
            encodeStrRanges(c.subjectRanges, "subject", statement);

        if (!ListUtil.isEmpty(c.senderRanges))
            encodeStrRanges(c.senderRanges, "sender", statement);

        statement.append(')');
    }

    private static void encodeRanges(Collection<? extends DbSearchConstraints.NumericRange> ranges, String column, long lowestValue, StringBuilder statement) {
        for (DbSearchConstraints.NumericRange r : ranges) {
            statement.append(r.negated ? " AND NOT (" : " AND (");
            if (r.lowest >= lowestValue)
                if (r.lowestEqual)
                    statement.append(" " + column + " >= ?");
                else
                    statement.append(" " + column + " > ?");
            if (r.highest >= lowestValue) {
                if (r.lowest >= lowestValue)
                    statement.append(" AND");
                if (r.highestEqual)
                    statement.append(" " + column + " <= ?");
                else
                    statement.append(" " + column + " < ?");
            }
            statement.append(')');
        }
    }

    private static void encodeStrRanges(Collection<? extends DbSearchConstraints.StringRange> ranges, String column, StringBuilder statement) {
        for (DbSearchConstraints.StringRange r : ranges) {
            statement.append(r.negated ? " AND NOT (" : " AND (");
            if (r.lowest != null)
                if (r.lowestEqual)
                    statement.append(" " + column + " >= ?");
                else
                    statement.append(" " + column + " > ?");
            if (r.highest != null) {
                if (r.lowest != null)
                    statement.append(" AND");
                if (r.highestEqual)
                    statement.append(" " + column + " <= ?");
                else
                    statement.append(" " + column + " < ?");
            }
            statement.append(')');
        }
    }


    static class TagConstraints {
        Set<Long> searchTagsets;
        Set<Long> searchFlagsets;
        Boolean unread;
        boolean noMatches;

        static TagConstraints getTagContraints(Mailbox mbox, DbSearchConstraints c, Connection conn) throws ServiceException {
            TagConstraints tc = c.tagConstraints = new TagConstraints();
            if (ListUtil.isEmpty(c.tags) && ListUtil.isEmpty(c.excludeTags))
                return tc;

            int setFlagMask = 0;
            long setTagMask = 0;

            if (!ListUtil.isEmpty(c.tags)) {
                for (Tag curTag : c.tags) {
                    if (curTag.getId() == Flag.ID_FLAG_UNREAD) {
                        tc.unread = Boolean.TRUE; 
                    } else if (curTag instanceof Flag) {
                        setFlagMask |= curTag.getBitmask();
                    } else {
                        setTagMask |= curTag.getBitmask();
                    }
                }
            }

            int flagMask = setFlagMask;
            long tagMask = setTagMask;

            if (!ListUtil.isEmpty(c.excludeTags)) {
                for (Tag t : c.excludeTags) {
                    if (t.getId() == Flag.ID_FLAG_UNREAD) {
                        tc.unread = Boolean.FALSE;
                    } else if (t instanceof Flag) {
                        flagMask |= t.getBitmask();
                    } else {
                        tagMask |= t.getBitmask();
                    }
                }
            }

            TagsetCache tcFlags = getFlagsetCache(conn, mbox);
            TagsetCache tcTags  = getTagsetCache(conn, mbox);
            if (setTagMask != 0 || tagMask != 0) {
                // note that tcTags.getMatchingTagsets() returns null when *all* tagsets match
                tc.searchTagsets = tcTags.getMatchingTagsets(tagMask, setTagMask);
                // if no items match the specified tags...
                if (tc.searchTagsets != null && tc.searchTagsets.isEmpty()) {
                    tc.noMatches = true;
                    tc.searchTagsets = null; // otherwise we encode "tags IN()" which MySQL doesn't like
                }
            }

            if (setFlagMask != 0 || flagMask != 0) {
                // note that tcFlags.getMatchingTagsets() returns null when *all* flagsets match
                tc.searchFlagsets = tcFlags.getMatchingTagsets(flagMask, setFlagMask);
                // if no items match the specified flags...
                if (tc.searchFlagsets != null && tc.searchFlagsets.isEmpty()) {
                    tc.noMatches = true;
                    tc.searchFlagsets = null;  // otherwise we encode "flags IN()" which MySQL doesn't like
                }
            }

            return tc;
        }
    }

    private static int setSearchVars(PreparedStatement stmt, DbSearchConstraintsNode node, int param) throws SQLException {
        DbSearchConstraintsNode.NodeType ntype = node.getNodeType();
        if (ntype == DbSearchConstraintsNode.NodeType.AND || ntype == DbSearchConstraintsNode.NodeType.OR) {
            for (DbSearchConstraintsNode subnode : node.getSubNodes())
                param = setSearchVars(stmt, subnode, param);
            return param;
        }

        // we're here, so we must be in a DbSearchConstraints leaf node
        DbSearchConstraints c = node.getSearchConstraints();
        assert(ntype == DbSearchConstraintsNode.NodeType.LEAF && c != null);

        // if there are no possible matches, short-circuit here...
        if (c.automaticEmptySet())
            return param;

        if (!ListUtil.isEmpty(c.types)) {
            for (byte type : c.types)
                stmt.setByte(param++, type);
        }
        if (!ListUtil.isEmpty(c.excludeTypes)) {
            for (byte type : c.excludeTypes)
                stmt.setByte(param++, type); 
        }

        if (c.tagConstraints.searchTagsets != null) {
            for (long tagset : c.tagConstraints.searchTagsets)
                stmt.setLong(param++, tagset);
        }
        if (c.tagConstraints.searchFlagsets != null) {
            for (long flagset : c.tagConstraints.searchFlagsets)
                stmt.setLong(param++, flagset);
        }
        if (c.tagConstraints.unread != null)
            stmt.setInt(param++, c.tagConstraints.unread ? 1 : 0);

        Collection<Folder> targetFolders = (!ListUtil.isEmpty(c.folders)) ? c.folders : c.excludeFolders;
        if (targetFolders != null) {
            for (Folder folder : targetFolders)
                stmt.setInt(param++, folder.getId());
        }

        if (c.convId > 0) {
            stmt.setInt(param++, c.convId);
        } else if (!ListUtil.isEmpty(c.prohibitedConvIds)) {
            for (int id : c.prohibitedConvIds)
                stmt.setInt(param++, id);
        }

        if (!ListUtil.isEmpty(c.itemIds)) {
            for (int id : c.itemIds)
                stmt.setInt(param++, id);
        }
        if (!ListUtil.isEmpty(c.prohibitedItemIds)) {
            for (int id : c.prohibitedItemIds)
                stmt.setInt(param++, id);
        }

        if (!ListUtil.isEmpty(c.indexIds)) {
            for (int id : c.indexIds)
                stmt.setInt(param++, id);
        }

        if (!ListUtil.isEmpty(c.dates)) {
            for (DbSearchConstraints.NumericRange date : c.dates) {
                if (date.lowest > 0)
                    stmt.setInt(param++, (int) Math.min(date.lowest / 1000, Integer.MAX_VALUE));
                if (date.highest > 0)
                    stmt.setInt(param++, (int) Math.min(date.highest / 1000, Integer.MAX_VALUE));
            }
        }

        if (!ListUtil.isEmpty(c.modified)) {
            for (DbSearchConstraints.NumericRange modified : c.modified) {
                if (modified.lowest > 0)
                    stmt.setLong(param++, modified.lowest);
                if (modified.highest > 0)
                    stmt.setLong(param++, modified.highest);
            }
        }

        if (!ListUtil.isEmpty(c.sizes)) {
            for (DbSearchConstraints.NumericRange size : c.sizes) {
                if (size.lowest >= 0)
                    stmt.setInt(param++, (int) size.lowest);
                if (size.highest >= 0)
                    stmt.setInt(param++, (int) size.highest);
            }
        }

        if (!ListUtil.isEmpty(c.subjectRanges)) {
            for (DbSearchConstraints.StringRange cur: c.subjectRanges) {
                if (cur.lowest != null) 
                    stmt.setString(param++, cur.lowest);
                if (cur.highest != null) 
                    stmt.setString(param++, cur.highest);
            }
        }

        if (!ListUtil.isEmpty(c.senderRanges)) {
            for (DbSearchConstraints.StringRange cur: c.senderRanges) {
                if (cur.lowest != null) 
                    stmt.setString(param++, cur.lowest);
                if (cur.highest != null) 
                    stmt.setString(param++, cur.highest);
            }
        }

        return param;
    }

    public static List<SearchResult> listByFolder(Folder folder, byte type) throws ServiceException {
        return listByFolder(folder, type, true);
    }
    public static List<SearchResult> listByFolder(Folder folder, byte type, boolean descending) throws ServiceException {
        Mailbox mbox = folder.getMailbox();
        Connection conn = mbox.getOperationConnection();

        ArrayList<SearchResult> result = new ArrayList<SearchResult>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT id, index_id, type, date FROM " + getMailItemTableName(folder) +
                        " WHERE " + IN_THIS_MAILBOX_AND + "type = ? AND folder_id = ?" +
                        " ORDER BY date" + (descending ? " DESC" : ""));
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setByte(pos++, type);
            stmt.setInt(pos++, folder.getId());
            rs = stmt.executeQuery();
            while (rs.next())
                result.add(SearchResult.createResult(rs, SORT_BY_DATE));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching item list for folder " + folder.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    // these columns are specified by DB_FIELDS, below
    public static final int CI_ID          = 1;
    public static final int CI_TYPE        = 2;
    public static final int CI_PARENT_ID   = 3;
    public static final int CI_FOLDER_ID   = 4;
    public static final int CI_INDEX_ID    = 5;
    public static final int CI_IMAP_ID     = 6;
    public static final int CI_DATE        = 7;
    public static final int CI_SIZE        = 8;
    public static final int CI_VOLUME_ID   = 9;
    public static final int CI_BLOB_DIGEST = 10;
    public static final int CI_UNREAD      = 11;
    public static final int CI_FLAGS       = 12;
    public static final int CI_TAGS        = 13;
//  public static final int CI_SENDER      = 14;
    public static final int CI_SUBJECT     = 14;
    public static final int CI_NAME        = 15;
    public static final int CI_METADATA    = 16;
    public static final int CI_MODIFIED    = 17;
    public static final int CI_MODIFY_DATE = 18;
    public static final int CI_SAVED       = 19;

    private static final String DB_FIELDS = "mi.id, mi.type, mi.parent_id, mi.folder_id, mi.index_id, " +
    "mi.imap_id, mi.date, mi.size, mi.volume_id, mi.blob_digest, " +
    "mi.unread, mi.flags, mi.tags, mi.subject, mi.name, " +
    "mi.metadata, mi.mod_metadata, mi.change_date, mi.mod_content";

    private static UnderlyingData constructItem(ResultSet rs) throws SQLException {
        return constructItem(rs, 0);
    }
    static UnderlyingData constructItem(ResultSet rs, int offset) throws SQLException {
        UnderlyingData data = new UnderlyingData();
        data.id          = rs.getInt(CI_ID + offset);
        data.type        = rs.getByte(CI_TYPE + offset);
        data.parentId    = rs.getInt(CI_PARENT_ID + offset);
        data.folderId    = rs.getInt(CI_FOLDER_ID + offset);
        data.indexId     = rs.getInt(CI_INDEX_ID + offset);
        data.imapId      = rs.getInt(CI_IMAP_ID + offset);
        if (rs.wasNull())
            data.imapId = -1;
        data.date        = rs.getInt(CI_DATE + offset);
        data.size        = rs.getInt(CI_SIZE + offset);
        data.volumeId    = rs.getShort(CI_VOLUME_ID + offset);
        if (rs.wasNull())
            data.volumeId = -1;
        data.blobDigest  = rs.getString(CI_BLOB_DIGEST + offset);
        data.unreadCount = rs.getInt(CI_UNREAD + offset);
        data.flags       = rs.getInt(CI_FLAGS + offset);
        data.tags        = rs.getLong(CI_TAGS + offset);
        data.subject     = rs.getString(CI_SUBJECT + offset);
        data.name        = rs.getString(CI_NAME + offset);
        data.metadata    = rs.getString(CI_METADATA + offset);
        data.modMetadata = rs.getInt(CI_MODIFIED + offset);
        data.modContent  = rs.getInt(CI_SAVED + offset);
        data.dateChanged = rs.getInt(CI_MODIFY_DATE + offset);
        // make sure to handle NULL column values
        if (data.parentId == 0)     data.parentId = -1;
        if (data.indexId == 0)      data.indexId = -1;
        if (data.dateChanged == 0)  data.dateChanged = -1;
        return data;
    }


    //////////////////////////////////////
    // CALENDAR STUFF BELOW HERE!
    //////////////////////////////////////

    private static final String CALENDAR_TYPES =
        "(" + MailItem.TYPE_APPOINTMENT + ", " + MailItem.TYPE_TASK + ")";
    private static final String APPOINTMENT_TYPE =
        "(" + MailItem.TYPE_APPOINTMENT + ")";
    private static final String TASK_TYPE =
        "(" + MailItem.TYPE_TASK + ")";

    public static UnderlyingData getCalendarItem(Mailbox mbox, String uid)
    throws ServiceException {
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String sql = "SELECT " + DB_FIELDS +
            " FROM " + getCalendarItemTableName(mbox, "ci") + ", " +
            getMailItemTableName(mbox, "mi") +
            " WHERE " +
            (!DebugConfig.disableMailboxGroup ? "ci.mailbox_id = ? AND " : "") +
            "ci.uid = ? AND " +
            (!DebugConfig.disableMailboxGroup ? "mi.mailbox_id = ci.mailbox_id AND " : "") +
            "mi.id = ci.item_id AND mi.type IN " + CALENDAR_TYPES +
            " GROUP BY mi.id";
            stmt = conn.prepareStatement(sql);
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setString(pos++, uid);
            rs = stmt.executeQuery();

            if (rs.next())
                return constructItem(rs);
            return null;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching calendar items for mailbox " + mbox.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }


    /**
     * Return all of the Invite records within the range start<=Invites<end.  IE "Give me all the 
     * invites between 7:00 and 9:00 will return you everything from 7:00 to 8:59:59.99
     * @param start
     * @param end
     * @param folderId 
     * @return list of invites
     */
    public static List<UnderlyingData> getCalendarItems(Mailbox mbox, byte type, long start, long end, 
                int folderId, int[] excludeFolderIds) 
                throws ServiceException {
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            boolean folderSpecified = folderId != Mailbox.ID_AUTO_INCREMENT;

            String excludeFolderPart = "";
            if (excludeFolderIds != null) 
                excludeFolderPart = " AND folder_id NOT IN" + DbUtil.suitableNumberOfVariables(excludeFolderIds);

            String typeList;
            if (type == MailItem.TYPE_APPOINTMENT)
                typeList = APPOINTMENT_TYPE;
            else if (type == MailItem.TYPE_TASK)
                typeList = TASK_TYPE;
            else
                typeList = CALENDAR_TYPES;
            stmt = conn.prepareStatement("SELECT " + DB_FIELDS +
                        " FROM " + getCalendarItemTableName(mbox, "ci") + ", " +
                        getMailItemTableName(mbox, "mi") +
                        " WHERE " +
                        (!DebugConfig.disableMailboxGroup ? "ci.mailbox_id = ? AND " : "") +
                        "ci.start_time < ? AND ci.end_time > ? AND " +
                        (!DebugConfig.disableMailboxGroup ? "mi.mailbox_id = ci.mailbox_id AND " : "") +
                        "mi.id = ci.item_id AND mi.type IN " + typeList +
                        (folderSpecified ? " AND folder_id = ?" : "") +
                        excludeFolderPart +
            " GROUP BY mi.id");

            int param = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(param++, mbox.getId());
            stmt.setTimestamp(param++, new Timestamp(end));
            stmt.setTimestamp(param++, new Timestamp(start));
            if (folderSpecified)
                stmt.setInt(param++, folderId);
            if (excludeFolderIds != null) {
                for (int id : excludeFolderIds)
                    stmt.setInt(param++, id);
            }

            rs = stmt.executeQuery();

            List<UnderlyingData> result = new ArrayList<UnderlyingData>();
            while (rs.next())
                result.add(constructItem(rs));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("fetching calendar items for mailbox " + mbox.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static void addToCalendarItemTable(CalendarItem calItem) throws ServiceException {
        Mailbox mbox = calItem.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            Timestamp startTs = new Timestamp(calItem.getStartTime());

            long end = calItem.getEndTime();
            Timestamp endTs;
            if (end <= 0) {
                endTs = new Timestamp(MAX_DATE);
            } else {
                endTs = new Timestamp(end);
            }

            stmt = conn.prepareStatement("INSERT INTO " +
                        getCalendarItemTableName(mbox) +
                        " (" +
                        (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") +
                        "uid, item_id, start_time, end_time)" +
                        " VALUES (" +
                        (!DebugConfig.disableMailboxGroup ? "?, " : "") +
            "?, ?, ?, ?)");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setString(pos++, calItem.getUid());
            stmt.setInt(pos++, calItem.getId());
            stmt.setTimestamp(pos++, startTs);
            stmt.setTimestamp(pos++, endTs);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing invite to calendar item table: UID=" + calItem.getUid(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    private static long MAX_DATE = new GregorianCalendar(9999, 1, 1).getTimeInMillis();

    public static void updateInCalendarItemTable(CalendarItem calItem) throws ServiceException {
        Mailbox mbox = calItem.getMailbox();
        Connection conn = mbox.getOperationConnection();
        PreparedStatement stmt = null;
        try {
            Timestamp startTs = new Timestamp(calItem.getStartTime());

            long end = calItem.getEndTime();
            Timestamp endTs;
            if (end <= 0) {
                endTs = new Timestamp(MAX_DATE);
            } else {
                endTs = new Timestamp(end);
            }

            stmt = conn.prepareStatement("INSERT INTO " +
                        getCalendarItemTableName(mbox) +
                        " (" +
                        (!DebugConfig.disableMailboxGroup ? "mailbox_id, " : "") +
                        "uid, item_id, start_time, end_time)" +
                        " VALUES (" +
                        (!DebugConfig.disableMailboxGroup ? "?, " : "") +
                        "?, ?, ?, ?)" +
            " ON DUPLICATE KEY UPDATE uid = ?, item_id = ?, start_time = ?, end_time = ?");
            int pos = 1;
            if (!DebugConfig.disableMailboxGroup)
                stmt.setInt(pos++, mbox.getId());
            stmt.setString(pos++, calItem.getUid());
            stmt.setInt(pos++, calItem.getId());
            stmt.setTimestamp(pos++, startTs);
            stmt.setTimestamp(pos++, endTs);

            stmt.setString(pos++, calItem.getUid());
            stmt.setInt(pos++, calItem.getId());
            stmt.setTimestamp(pos++, startTs);
            stmt.setTimestamp(pos++, endTs);

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("writing invite to calendar item table" + calItem.getUid(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }


    /** Makes sure that the argument won't overflow the maximum length of a
     *  MySQL VARCHAR(128) column (128 characters) by truncating the string
     *  if necessary.
     * 
     * @param sender  The string to check (can be null).
     * @return The passed-in String, truncated to 128 chars. */
    static String checkSenderLength(String sender) {
        if (sender == null || sender.length() <= MAX_SENDER_LENGTH)
            return sender;
        return sender.substring(0, MAX_SENDER_LENGTH);
    }

    /** Makes sure that the argument won't overflow the maximum length of a
     *  MySQL TEXT column (65536 bytes) after conversion to UTF-8.
     * 
     * @param metadata  The string to check (can be null).
     * @return The passed-in String.
     * @throws ServiceException <code>service.FAILURE</code> is thrown if
     *         the parameter would be silently truncated when inserted. */
    static String checkTextLength(String metadata) throws ServiceException {
        if (metadata == null)
            return null;
        if (StringUtil.isAsciiString(metadata)) {
            if (metadata.length() > MAX_TEXT_LENGTH)
                throw ServiceException.FAILURE("metadata too long", null);
        } else {
            try {
                if (metadata.getBytes("utf-8").length > MAX_TEXT_LENGTH)
                    throw ServiceException.FAILURE("metadata too long", null);
            } catch (UnsupportedEncodingException uee) { }
        }
        return metadata;
    }

    /**
     * Returns the name of the table that stores {@link MailItem} data.  The table name is qualified
     * by the name of the database (e.g. <code>mailbox1.mail_item</code>).
     */
    public static String getMailItemTableName(int mailboxId, int groupId) {
        return String.format("%s.%s", DbMailbox.getDatabaseName(mailboxId, groupId), TABLE_MAIL_ITEM);
    }
    public static String getMailItemTableName(Mailbox mbox) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getMailItemTableName(id, gid);
    }
    public static String getMailItemTableName(Mailbox mbox, String alias) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getMailItemTableName(id, gid) + " " + alias;
    }
    public static String getMailItemTableName(MailItem item) {
        return getMailItemTableName(item.getMailbox());
    }

    /**
     * Returns the name of the table that stores {@link CalendarItem} data.  The table name is qualified
     * by the name of the database (e.g. <code>mailbox1.appointment</code>).
     */
    public static String getCalendarItemTableName(int mailboxId, int groupId) {
        return String.format("%s.%s", DbMailbox.getDatabaseName(mailboxId, groupId), TABLE_APPOINTMENT);
    }
    public static String getCalendarItemTableName(Mailbox mbox) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getCalendarItemTableName(id, gid);
    }
    public static String getCalendarItemTableName(Mailbox mbox, String alias) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getCalendarItemTableName(id, gid) + " " + alias;
    }

    /**
     * Returns the name of the table that maps subject hashes to {@link Conversation} ids.  The table 
     * name is qualified by the name of the database (e.g. <code>mailbox1.open_conversation</code>).
     */
    public static String getConversationTableName(int mailboxId, int groupId) {
        return String.format("%s.%s", DbMailbox.getDatabaseName(mailboxId, groupId), TABLE_OPEN_CONVERSATION);
    }
    public static String getConversationTableName(int mailboxId, int groupId, String alias) {
        return String.format("%s.%s AS %s", DbMailbox.getDatabaseName(mailboxId, groupId), TABLE_OPEN_CONVERSATION, alias);
    }
    public static String getConversationTableName(Mailbox mbox) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getConversationTableName(id, gid);
    }
    public static String getConversationTableName(Mailbox mbox, String alias) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getConversationTableName(id, gid, alias);
    }
    public static String getConversationTableName(MailItem item) {
        return getConversationTableName(item.getMailbox());
    }

    /**
     * Returns the name of the table that stores data on deleted items for the purpose of sync.
     * The table name is qualified by the name of the database (e.g. <code>mailbox1.tombstone</code>).
     */
    public static String getTombstoneTableName(int mailboxId, int groupId) {
        return String.format("%s.%s", DbMailbox.getDatabaseName(mailboxId, groupId), TABLE_TOMBSTONE);
    }
    public static String getTombstoneTableName(Mailbox mbox) {
        int id = mbox.getId();
        int gid = mbox.getSchemaGroupId();
        return getTombstoneTableName(id, gid);
    }

    private static boolean areTagsetsLoaded(int mailboxId) {
        synchronized(sTagsetCache) {
            return sTagsetCache.containsKey(new Integer(mailboxId));
        }
    }

    static TagsetCache getTagsetCache(Connection conn, Mailbox mbox)
    throws ServiceException {
        int mailboxId = mbox.getId();
        Integer id = new Integer(mailboxId);
        TagsetCache tagsets = null;

        synchronized (sTagsetCache) {
            tagsets = sTagsetCache.get(id);
        }

        // All access to a mailbox is synchronized, so we can initialize
        // the tagset cache for a single mailbox outside the
        // synchronized block.
        if (tagsets == null) {
            ZimbraLog.cache.info("Loading tagset cache");
            tagsets = new TagsetCache("Mailbox " + mailboxId + " tags");
            tagsets.addTagsets(DbMailbox.getDistinctTagsets(conn, mbox));

            synchronized (sTagsetCache) {
                sTagsetCache.put(id, tagsets);
            }
        }

        return tagsets;
    }

    private static boolean areFlagsetsLoaded(int mailboxId) {
        synchronized(sFlagsetCache) {
            return sFlagsetCache.containsKey(new Integer(mailboxId));
        }
    }

    static TagsetCache getFlagsetCache(Connection conn, Mailbox mbox)
    throws ServiceException {
        int mailboxId = mbox.getId();
        Integer id = new Integer(mailboxId);
        TagsetCache flagsets = null;

        synchronized (sFlagsetCache) {
            flagsets = sFlagsetCache.get(id);
        }

        // All access to a mailbox is synchronized, so we can initialize
        // the flagset cache for a single mailbox outside the
        // synchronized block.
        if (flagsets == null) {
            ZimbraLog.cache.info("Loading flagset cache");
            flagsets = new TagsetCache("Mailbox " + mailboxId + " flags");
            flagsets.addTagsets(DbMailbox.getDistinctFlagsets(conn, mbox));

            synchronized (sFlagsetCache) {
                sFlagsetCache.put(id, flagsets);
            }
        }

        return flagsets;
    }

    public static void main(String[] args) throws ServiceException {
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(1);

        DbSearchConstraints hasTags = new DbSearchConstraints();
        hasTags.hasTags = true;

        DbSearchConstraints inTrash = new DbSearchConstraints();
        Set<Folder> folders = new HashSet<Folder>();  folders.add(mbox.getFolderById(null, Mailbox.ID_FOLDER_TRASH));
        inTrash.folders = folders;

        DbSearchConstraints isUnread = new DbSearchConstraints();
        Set<Tag> tags = new HashSet<Tag>();  tags.add(mbox.mUnreadFlag);
        isUnread.tags = tags;

        DbSearchConstraintsInnerNode orClause = DbSearchConstraintsInnerNode.OR();
        orClause.addSubNode(hasTags);
        DbSearchConstraintsInnerNode andClause = DbSearchConstraintsInnerNode.AND();
        andClause.addSubNode(inTrash);
        andClause.addSubNode(isUnread);
        orClause.addSubNode(andClause);

        // "is:unread" (first 5 results)
        //System.out.println(search(new ArrayList<SearchResult>(), DbPool.getConnection(), isUnread, 1, DEFAULT_SORT_ORDER, 0, 5, SearchResult.ExtraData.NONE));
        // "has:tags or (in:trash is:unread)" (first 5 results)
        //System.out.println(search(new ArrayList<SearchResult>(), DbPool.getConnection(), orClause, 1, DEFAULT_SORT_ORDER, 0, 5, SearchResult.ExtraData.NONE));
    }
}
