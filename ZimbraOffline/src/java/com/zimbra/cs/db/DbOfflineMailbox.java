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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Pair;
import com.zimbra.cs.db.DbPool.Connection; 
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.OfflineMailbox;
import com.zimbra.cs.mailbox.Tag;

public class DbOfflineMailbox {

    public static void renumberItem(MailItem item, int newId, int mod_content) throws ServiceException {
        if (Db.supports(Db.Capability.ON_UPDATE_CASCADE))
            renumberItemCascade(item, newId, mod_content);
        else
            renumberItemManual(item, newId, mod_content);
    }

    public static void renumberItemManual(MailItem item, int newId, int mod_content) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        byte type = item.getType();

        PreparedStatement stmt = null;
        try {
            // first, duplicate the original row with the new ID
            String table = DbMailItem.getMailItemTableName(mbox);
            stmt = conn.prepareStatement("INSERT INTO " + table +
                    "(mailbox_id, id, type, parent_id, folder_id, index_id, imap_id, date, size, volume_id, blob_digest," +
                    " unread, flags, tags, sender, subject, name, metadata, mod_metadata, change_date, mod_content, change_mask) " +
                    "(SELECT mailbox_id, ?, type, parent_id, folder_id, index_id, imap_id, date, size, volume_id, blob_digest," +
                    " unread, flags, tags, sender, subject, name, metadata, mod_metadata, change_date, ?, change_mask" +
                    " FROM " + table + " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?)");
            int pos = 1;
            stmt.setInt(pos++, newId);
            stmt.setInt(pos++, mod_content);
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
            stmt.close();

            // then update all the dependent rows (foreign keys)
            if (item.isTagged(mbox.mVersionedFlag)) {
                // update REVISION.ITEM_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getRevisionTableName(mbox) +
                        " SET item_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "item_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_MESSAGE || type == MailItem.TYPE_CHAT || type == MailItem.TYPE_CONVERSATION) {
                // update OPEN_CONVERSATION.CONV_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getConversationTableName(mbox) +
                        " SET conv_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "conv_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_APPOINTMENT) {
                // update APPOINTMENT.ITEM_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getCalendarItemTableName(mbox) +
                        " SET item_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "item_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_FOLDER || type == MailItem.TYPE_SEARCHFOLDER) {
                // update MAIL_ITEM.FOLDER_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(mbox) +
                        " SET folder_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "folder_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_FOLDER || type == MailItem.TYPE_SEARCHFOLDER || type == MailItem.TYPE_CONVERSATION) {
                // update MAIL_ITEM.PARENT_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(mbox) +
                        " SET parent_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "parent_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            // now we can delete the original row with no foreign key conflicts
            stmt = conn.prepareStatement("DELETE FROM " + DbMailItem.getMailItemTableName(mbox) +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            pos = 1;
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
            stmt.close();

            if (type == MailItem.TYPE_TAG)
                updateTagBitmask(conn, (Tag) item, newId);

        } catch (SQLException e) {
            throw ServiceException.FAILURE("renumbering " + MailItem.getNameForType(type) + " (" + item.getId() + " => " + newId + ")", e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void renumberItemCascade(MailItem item, int newId, int mod_content) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();
        byte type = item.getType();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(item) +
                    " SET id = ?, mod_content = ?" +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, newId);
            stmt.setInt(pos++, mod_content);
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
            stmt.close();

            if (type == MailItem.TYPE_FOLDER || type == MailItem.TYPE_SEARCHFOLDER) {
                // update MAIL_ITEM.FOLDER_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(mbox) +
                        " SET folder_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "folder_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_FOLDER || type == MailItem.TYPE_SEARCHFOLDER || type == MailItem.TYPE_CONVERSATION) {
                // update MAIL_ITEM.PARENT_ID
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(mbox) +
                        " SET parent_id = ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "parent_id = ?");
                pos = 1;
                stmt.setInt(pos++, newId);
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                stmt.executeUpdate();
                stmt.close();
            }

            if (type == MailItem.TYPE_TAG)
                updateTagBitmask(conn, (Tag) item, newId);

        } catch (SQLException e) {
            throw ServiceException.FAILURE("renumbering " + MailItem.getNameForType(type) + " (" + item.getId() + " => " + newId + ")", e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    // handle reworking tag bitmasks for other mail items
    private static void updateTagBitmask(Connection conn, Tag tag, int newId) throws SQLException, ServiceException {
        Mailbox mbox = tag.getMailbox();
        long newMask = 1L << Tag.getIndex(newId);

        PreparedStatement stmt = null;
        try {
            if (Db.supports(Db.Capability.BITWISE_OPERATIONS)) {
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(tag) +
                        " SET tags = (tags & ?) | ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "tags & ?");
                int pos = 1;
                stmt.setLong(pos++, ~tag.getBitmask());
                stmt.setLong(pos++, newMask);
                stmt.setInt(pos++, mbox.getId());
                stmt.setLong(pos++, tag.getBitmask());
                stmt.executeUpdate();
            } else {
                // first, add the new mask
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(tag) +
                        " SET tags = tags + ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + Db.bitmaskAND("tags") + " AND NOT " + Db.bitmaskAND("tags"));
                int pos = 1;
                stmt.setLong(pos++, newMask);
                stmt.setInt(pos++, mbox.getId());
                stmt.setLong(pos++, tag.getBitmask());
                stmt.setLong(pos++, newMask);
                stmt.executeUpdate();
                stmt.close();

                // then, remove the old mask
                stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(tag) +
                        " SET tags = tags - ?" +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + Db.bitmaskAND("tags"));
                pos = 1;
                stmt.setLong(pos++, tag.getBitmask());
                stmt.setInt(pos++, mbox.getId());
                stmt.setLong(pos++, tag.getBitmask());
                stmt.executeUpdate();
            }
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void setChangeIds(MailItem item, int date, int mod_content, int change_date, int mod_metadata) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(item) +
                    " SET date = ?, mod_content = ?, change_date = ?, mod_metadata = ?" +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, date);
            stmt.setInt(pos++, mod_content);
            stmt.setInt(pos++, change_date);
            stmt.setInt(pos++, mod_metadata);
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("setting content change IDs for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static MailItem.TypedIdList getChangedItems(OfflineMailbox ombx) throws ServiceException {
        Connection conn = ombx.getOperationConnection();

        MailItem.TypedIdList result = new MailItem.TypedIdList();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT id, type" +
                    " FROM " + DbMailItem.getMailItemTableName(ombx) +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "change_mask IS NOT NULL");
            int pos = 1;
            stmt.setInt(pos++, ombx.getId());

            rs = stmt.executeQuery();
            while (rs.next())
                result.add(rs.getByte(2), rs.getInt(1));
            return result;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("getting changed item ids for ombx " + ombx.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static int getChangeMask(MailItem item) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT change_mask" +
                    " FROM " + DbMailItem.getMailItemTableName(mbox) +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());

            rs = stmt.executeQuery();
            if (!rs.next())
                throw MailItem.noSuchItem(item.getId(), item.getType());
            return rs.getInt(1);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("getting change record for item " + item.getId(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static void setChangeMask(MailItem item, int mask) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(mbox) +
                    " SET change_mask = ?" +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            if (mask == 0)
                stmt.setNull(pos++, Types.INTEGER);
            else
                stmt.setInt(pos++, mask);
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("setting change bitmask for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void updateChangeRecord(MailItem item, int mask) throws ServiceException {
        Mailbox mbox = item.getMailbox();
        Connection conn = mbox.getOperationConnection();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (!Db.supports(Db.Capability.BITWISE_OPERATIONS)) {
                stmt = conn.prepareStatement("SELECT change_mask FROM " + DbMailItem.getMailItemTableName(item) +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
                int pos = 1;
                stmt.setInt(pos++, mbox.getId());
                stmt.setInt(pos++, item.getId());
                rs = stmt.executeQuery();
                if (rs.next())
                    mask |= rs.getInt(1);
                rs.close();
                stmt.close();
            }

            String newMask = (Db.supports(Db.Capability.BITWISE_OPERATIONS) ? "CASE WHEN change_mask IS NULL THEN ? ELSE change_mask | ? END" : "?");
            stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(item) +
                    " SET change_mask = " + newMask +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "id = ?");
            int pos = 1;
            stmt.setInt(pos++, mask);
            if (Db.supports(Db.Capability.BITWISE_OPERATIONS))
                stmt.setInt(pos++, mask);
            stmt.setInt(pos++, mbox.getId());
            stmt.setInt(pos++, item.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("setting change record for item " + item.getId(), e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void clearChangeRecords(OfflineMailbox ombx, List<Integer> ids) throws ServiceException {
        Connection conn = ombx.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("UPDATE " + DbMailItem.getMailItemTableName(ombx) +
                    " SET change_mask = NULL" +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "change IS NOT NULL");
            int pos = 1;
            stmt.setInt(pos++, ombx.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("clearing change records for items " + ids, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static boolean isTombstone(OfflineMailbox ombx, int id, byte type) throws ServiceException {
        Connection conn = ombx.getOperationConnection();
        return !getMatchingTombstones(conn, ombx, id, type).isEmpty();
    }

    private static List<Pair<Integer, String>> getMatchingTombstones(Connection conn, OfflineMailbox ombx, int id, byte type) throws ServiceException {
        List<Pair<Integer, String>> matches = new ArrayList<Pair<Integer, String>>();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (Db.supports(Db.Capability.CLOB_COMPARISON)) {
                // FIXME: oh, this is not pretty
                stmt = conn.prepareStatement("SELECT sequence, ids FROM " + DbMailItem.getTombstoneTableName(ombx) +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "type = ? AND (ids = ? OR ids LIKE ? OR ids LIKE ? OR ids LIKE ?)");
                int pos = 1;
                stmt.setInt(pos++, ombx.getId());
                stmt.setByte(pos++, type);
                stmt.setString(pos++, "" + id);
                stmt.setString(pos++, "%," + id);
                stmt.setString(pos++, id + ",%");
                stmt.setString(pos++, "%," + id + ",%");
                rs = stmt.executeQuery();
                while (rs.next())
                    matches.add(new Pair<Integer, String>(rs.getInt(1), rs.getString(2)));
            } else {
                stmt = conn.prepareStatement("SELECT sequence, ids FROM " + DbMailItem.getTombstoneTableName(ombx) +
                        " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "type = ?");
                int pos = 1;
                stmt.setInt(pos++, ombx.getId());
                stmt.setByte(pos++, type);
                rs = stmt.executeQuery();

                String idStr = Integer.toString(id);
                while (rs.next()) {
                    String ids = rs.getString(2);
                    if (ids.equals(idStr) || ids.startsWith(idStr + ',') || ids.endsWith(',' + idStr) || ids.indexOf(',' + idStr + ',') != -1)
                        matches.add(new Pair<Integer, String>(rs.getInt(1), ids));
                }
            }
            return matches;
        } catch (SQLException e) {
            throw ServiceException.FAILURE("searching TOMBSTONE table for " + MailItem.getNameForType(type) + " " + id, e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }
    }

    public static void removeTombstone(OfflineMailbox ombx, int id, byte type) throws ServiceException {
        Connection conn = ombx.getOperationConnection();
        String itemId = Integer.toString(id);

        PreparedStatement stmt = null;
        try {
            for (Pair<Integer, String> tombstone : getMatchingTombstones(conn, ombx, id, type)) {
                int sequence = tombstone.getFirst();
                String ids = tombstone.getSecond();

                if (ids.equals(itemId)) {
                    stmt = conn.prepareStatement("DELETE FROM " + DbMailItem.getTombstoneTableName(ombx) +
                            " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "sequence = ? AND type = ?");
                    int pos = 1;
                    stmt.setInt(pos++, ombx.getId());
                    stmt.setInt(pos++, sequence);
                    stmt.setByte(pos++, type);
                    stmt.executeUpdate();
                    stmt.close();
                } else {
                    StringBuffer sb = new StringBuffer();
                    for (String deletedId : ids.split(",")) {
                        if (!deletedId.equals(itemId))
                            sb.append(sb.length() == 0 ? "" : ",").append(deletedId);
                    }
                    stmt = conn.prepareStatement("UPDATE " + DbMailItem.getTombstoneTableName(ombx) + " SET ids = ?" +
                            " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "sequence = ? AND type = ?");
                    int pos = 1;
                    stmt.setString(pos++, sb.toString());
                    stmt.setInt(pos++, ombx.getId());
                    stmt.setInt(pos++, sequence);
                    stmt.setByte(pos++, type);
                    stmt.executeUpdate();
                    stmt.close();
                }
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("removing entry from TOMBSTONE table for " + MailItem.getNameForType(type) + " " + id, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }

    public static void clearTombstones(OfflineMailbox ombx, int token) throws ServiceException {
        Connection conn = ombx.getOperationConnection();

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("DELETE FROM " + DbMailItem.getTombstoneTableName(ombx) +
                    " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "sequence <= ?");
            int pos = 1;
            stmt.setInt(pos++, ombx.getId());
            stmt.setInt(pos++, token);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("clearing tombstones up to change " + token, e);
        } finally {
            DbPool.closeStatement(stmt);
        }
    }
}
