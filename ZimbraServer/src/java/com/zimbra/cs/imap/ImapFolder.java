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
 * Created on Apr 30, 2005
 */
package com.zimbra.cs.imap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import com.zimbra.cs.imap.ImapCredentials.ActivatedExtension;
import com.zimbra.cs.imap.ImapFlagCache.ImapFlag;
import com.zimbra.cs.imap.ImapMessage.ImapMessageSet;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.SearchFolder;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.service.mail.Search;
import com.zimbra.cs.session.PendingModifications;
import com.zimbra.cs.session.Session;
import com.zimbra.cs.session.PendingModifications.Change;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.ZimbraLog;

class ImapFolder extends Session implements Iterable<ImapMessage> {
    public static final long IMAP_IDLE_TIMEOUT_MSEC = 30 * Constants.MILLIS_PER_MINUTE;

    static final byte SELECT_READONLY  = 0x01;
    static final byte SELECT_CONDSTORE = 0x02;

    private int      mFolderId;
    private ImapPath mPath;
    private boolean  mWritable;
    private String   mQuery;

    private List<ImapMessage>         mSequence;
    private Map<Integer, ImapMessage> mMessageIds;

    private int mUIDValidityValue;
    private int mInitialUIDNEXT = -1;
    private int mInitialMODSEQ = -1;
    private int mInitialFirstUnread = -1;
    private int mLastSize = 0;

    private ImapFlagCache mFlags;
    private ImapFlagCache mTags;

    private ImapCredentials mCredentials;
    private ImapHandler     mHandler;
    private boolean         mNotificationsSuspended;
    private ImapMessageSet  mSavedSearchResults;
    private Map<Integer, DirtyMessage> mDirtyMessages = new TreeMap<Integer, DirtyMessage>();

    /** Initializes an ImapFolder from a {@link Folder}, specified by path.
     *  Search folders are treated as folders containing all messages matching
     *  the search.
     * @param name     The target folder's path.
     * @param params   Optional SELECT parameters (e.g. READONLY).
     * @param handler  The authenticated user's current IMAP session.
     * @see #loadVirtualFolder(SearchFolder, OperationContext) */
    ImapFolder(ImapPath path, byte params, ImapHandler handler, ImapCredentials creds) throws ServiceException {
        super(creds.getAccountId(), path.getOwnerAccount().getId(), Session.Type.IMAP);

        mHandler = handler;
        mCredentials = creds;
        mPath = path;
        if (!mPath.isSelectable())
            throw ServiceException.PERM_DENIED("cannot select folder: " + mPath);
        mWritable = (params & SELECT_READONLY) == 0 && mPath.isWritable();
        if ((params & SELECT_CONDSTORE) != 0)
            mCredentials.activateExtension(ActivatedExtension.CONDSTORE);
    }

    @Override
    public Session register() throws ServiceException {
        super.register();

        mMailbox.beginTrackingImap();

        OperationContext octxt = mCredentials.getContext();
        Folder folder = mMailbox.getFolderByPath(octxt, mPath.asZimbraPath());
        mFolderId = folder.getId();

        mUIDValidityValue = getUIDValidity(folder);
        mInitialUIDNEXT = folder.getImapUIDNEXT();
        mInitialMODSEQ = folder.getImapMODSEQ();

        // load the folder's contents
        loadFolder(octxt, folder);

        // initialize the flag and tag caches
        mFlags = ImapFlagCache.getSystemFlags(mMailbox);
        mTags = new ImapFlagCache(mMailbox, mCredentials.getContext());
        return this;
    }

    private void loadFolder(OperationContext octxt, Folder folder) throws ServiceException {
        boolean debug = ZimbraLog.imap.isDebugEnabled();
        if (debug)  ZimbraLog.imap.debug("  ** loading folder: " + mPath);

        if (folder instanceof SearchFolder) {
            String types = ((SearchFolder) folder).getReturnTypes().toLowerCase();
            if (types.equals(""))
                types = Search.DEFAULT_SEARCH_TYPES;
            if (types.indexOf("conversation") != -1 || types.indexOf("message") != -1)
                mQuery = ((SearchFolder) folder).getQuery();
            else
                mQuery = "item:none";
        }

        // fetch visible items from database
        List<ImapMessage> i4list = null;
        if (isVirtual())
            i4list = loadVirtualFolder(octxt, (SearchFolder) folder);
        synchronized (mMailbox) {
            if (i4list == null)
                i4list = mMailbox.openImapFolder(octxt, folder.getId());
            Collections.sort(i4list);
    
            // check messages for imapUid <= 0 and assign new IMAP IDs if necessary
            List<ImapMessage> unnumbered = new ArrayList<ImapMessage>();
            List<Integer> renumber = new ArrayList<Integer>();
            while (!i4list.isEmpty() && i4list.get(0).imapUid <= 0) {
                ImapMessage i4msg = i4list.remove(0);
                unnumbered.add(i4msg);  renumber.add(i4msg.msgId);
            }
            if (!renumber.isEmpty()) {
                List<Integer> newIds = mMailbox.resetImapUid(octxt, renumber);
                for (int i = 0; i < newIds.size(); i++)
                    unnumbered.get(i).imapUid = newIds.get(i);
                i4list.addAll(unnumbered);
            }
    
            // and create our lists and hashes of items
            mSequence = new ArrayList<ImapMessage>();
            StringBuilder added = debug ? new StringBuilder("  ** added: ") : null;
            for (ImapMessage i4msg : i4list) {
                cache(i4msg);
                if (mInitialFirstUnread == -1 && (i4msg.flags & Flag.BITMASK_UNREAD) != 0)
                    mInitialFirstUnread = i4msg.sequence;
                if (debug)  added.append(' ').append(i4msg.msgId);
            }
            if (debug)  ZimbraLog.imap.debug(added);
    
            mLastSize = mSequence.size();
        }
    }

    /** Fetches the messages contained within a search folder.  When a search
     *  folder is IMAP-visible, it appears in folder listings, is SELECTable
     *  READ-ONLY, and appears to have all matching messages as its contents.
     *  If it is not visible, it will be completely hidden from all IMAP
     *  commands.
     * @param octxt   Encapsulation of the authenticated user.
     * @param search  The search folder being exposed. */
    private List<ImapMessage> loadVirtualFolder(OperationContext octxt, SearchFolder search) throws ServiceException {
        Mailbox mbox = search.getMailbox();
        List<ImapMessage> i4list = new ArrayList<ImapMessage>();
        try {
            ZimbraQueryResults zqr = mbox.search(octxt, mQuery, ImapHandler.ITEM_TYPES, MailboxIndex.SortBy.DATE_ASCENDING, 1000);
            int i = 0, hitIds[] = new int[100];
            Arrays.fill(hitIds, Mailbox.ID_AUTO_INCREMENT);
            try {
                for (ZimbraHit hit = zqr.getFirstHit(); hit != null || i > 0; ) {
                    if (hit == null || i == 100) {
                        for (MailItem item : mbox.getItemById(octxt, hitIds, MailItem.TYPE_MESSAGE))
                            if (item != null)
                                i4list.add(new ImapMessage(item));
                        Arrays.fill(hitIds, Mailbox.ID_AUTO_INCREMENT);
                        i = 0;
                    }
                    if (hit != null) {
                        hitIds[i++] = hit.getItemId();
                        hit = zqr.getNext();
                    }
                }
            } finally {
                zqr.doneWithSearchResults();
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.FAILURE("failure opening searchfolder", e);
        }
        return i4list;
    }

    /** Reinitializes an ImapFolder for the purposes of EXAMINE/SELECTing the
     *  folder that's already EXAMINE/SELECTed.  Does not reload the contents
     *  of the folder from the Mailbox, but instead cleans up the existing data
     *  structures.  Cannot be called on virtual folders, which must be re-read
     *  manually.
     * @param select   Whether the user wants to open the folder for writing.
     * @see #ImapFolder(String, boolean, ImapCredentials) */
    void reopen(byte params) throws ServiceException {
        if (isVirtual())
            throw ServiceException.INVALID_REQUEST("cannot reopen virtual folders", null);

        OperationContext octxt = mCredentials.getContext();
        Folder folder = mMailbox.getFolderByPath(octxt, mPath.asZimbraPath());
        if (!mPath.isSelectable())
            throw ServiceException.PERM_DENIED("cannot select folder: " + mPath);
        if (folder.getId() != mFolderId)
            throw ServiceException.INVALID_REQUEST("folder IDs do not match (was " + mFolderId + ", is " + folder.getId() + ')', null);

        mWritable = (params & SELECT_READONLY) == 0 && mPath.isWritable();
        if ((params & SELECT_CONDSTORE) != 0)
            mCredentials.activateExtension(ActivatedExtension.CONDSTORE);

        mUIDValidityValue = getUIDValidity(folder);
        mInitialUIDNEXT = folder.getImapUIDNEXT();
        mInitialMODSEQ  = folder.getImapMODSEQ();

        mNotificationsSuspended = false;
        mDirtyMessages.clear();
        collapseExpunged();

        mInitialFirstUnread = -1;
        for (ImapMessage i4msg : mSequence) {
            if (mInitialFirstUnread == -1 && (i4msg.flags & Flag.BITMASK_UNREAD) != 0)
                mInitialFirstUnread = i4msg.sequence;
            i4msg.setAdded(false);
        }

        mLastSize = mSequence.size();
        mSavedSearchResults = null;
    }

    @Override
    protected boolean isMailboxListener() {
        return true;
    }

    @Override
    protected boolean isRegisteredInCache() {
        return true;
    }

    @Override
    public void doEncodeState(Element parent) {
        ImapCredentials.EnabledHack[] hacks = mCredentials.getEnabledHacks();
        Element imap = parent.addElement("imap");
        if (mSequence != null)
            imap.addAttribute("size", getSize());
        imap.addAttribute("hack", hacks == null ? null : Arrays.toString(hacks));
        imap.addAttribute("folder", mPath.asImapPath()).addAttribute("query", mQuery);
        imap.addAttribute("writable", isWritable()).addAttribute("dirty", mDirtyMessages.size());
    }

    @Override
    protected long getSessionIdleLifetime() {
        return IMAP_IDLE_TIMEOUT_MSEC;
    }


    ImapHandler getHandler() {
        return mHandler;
    }

    void setHandler(ImapHandler handler) {
        mHandler = handler;
    }

    /** Returns the selected folder's zimbra ID. */
    int getId() {
        return mFolderId;
    }

    /** Returns the number of messages in the folder.  Messages that have been
     *  received or deleted since the client was last notified are still
     *  included in this count. */
    int getSize() {
        return mSequence == null ? 0 : mSequence.size();
    }

    /** Returns the search folder query associated with this IMAP folder, or
     *  <tt>""</tt> if the SELECTed folder is not a search folder. */
    String getQuery() {
        return mQuery == null ? "" : mQuery;
    }

    /** Returns the folder's IMAP UID validity value.
     * @see #getUIDValidity(Folder) */
    int getUIDValidity() {
        return mUIDValidityValue;
    }

    /** Returns an indicator for determining whether a folder has had items
     *  inserted since the last check.  This is <b>only</b> valid immediately
     *  after the folder is initialized and is not updated as messages are
     *  subsequently added.
     * @see Folder#getImapUIDNEXT() */
    int getInitialUIDNEXT() {
        return mInitialUIDNEXT;
    }

    /** Returns an indicator for determining whether a folder has had flags
     *  changed since the last check.  This is <b>only</b> valid immediately
     *  after the folder is initialized and is not updated as messages are
     *  subsequently added.
     * @see Folder#getImapUIDNEXT() */
    int getInitialMODSEQ() {
        return mInitialMODSEQ;
    }

    /** Returns the "sequence number" of the first unread message in the
     *  folder, or -1 if none are unread.  This is <b>only</b> valid
     *  immediately after the folder is initialized and is not updated
     *  as messages are marked read and unread. */
    int getFirstUnread() {
        return mInitialFirstUnread;
    }

    /** Returns the {@link ImapCredentials} with which this ImapFolder was
     *  created. */
    ImapCredentials getCredentials() {
        return mCredentials;
    }

    /** Returns whether this folder is a "virtual" folder (i.e. a search
     *  folder).
     * @see #loadVirtualFolder(SearchFolder, Mailbox, OperationContext) */
    boolean isVirtual() {
        return mQuery != null;
    }

    /** Returns whether this folder was opened for write. */
    boolean isWritable() {
        return mWritable;
    }

    /** Returns whether a given SELECT option is active for this folder. */
    boolean isExtensionActivated(ActivatedExtension ext) {
        switch (ext) {
            case CONDSTORE: return !isVirtual() && mCredentials.isExtensionActivated(ext);
            default:        return false;
        }
    }


    public Iterator<ImapMessage> iterator() {
        return mSequence.iterator();
    }


    ImapPath getPath()              { return mPath; }
    String getQuotedPath()          { return '"' + mPath.asZimbraPath() + '"'; }
    void updatePath(Folder folder)  { mPath = new ImapPath(null, folder.getPath(), mPath.getCredentials()); }

    @Override public String toString()        { return mPath.toString(); }


    /** Returns the UID Validity Value for the {@link Folder}.  This is the
     *  folder's <tt>MOD_CONTENT</tt> change sequence number.
     * @see Folder#getSavedSequence() */
    static int getUIDValidity(Folder folder)    { return Math.max(folder.getSavedSequence(), 1); }
    static int getUIDValidity(ZFolder zfolder)  { return zfolder.getContentSequence(); }


    /** Retrieves the index of the ImapMessage with the given IMAP UID in the
     *  folder's {@link #mSequence} message list.  This retrieval is done via
     *  binary search rather than direct lookup.
     * @return index of the search key, if it is contained in the list;
     *         otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>.  The
     *         <i>insertion point</i> is defined as the point at which the
     *         key would be inserted into the list: the index of the first
     *         element greater than the key, or <tt>list.size()</tt>, if all
     *         elements in the list are less than the specified key.  Note
     *         that this guarantees that the return value will be &gt;= 0 if
     *         and only if the key is found.
     * @see Collections#binarySearch(java.util.List, T) */
    private int uidSearch(int uid) {
        int low = 0, high = getSize() - 1;
        while (low <= high) {
            int mid = (low + high) >> 1;
            int targetUid = mSequence.get(mid).imapUid;
            if (targetUid < uid)       low = mid + 1;
            else if (targetUid > uid)  high = mid - 1;
            else                       return mid;  // key found
        }
        return -(low + 1);  // key not found
    }

    /** Returns the ImapMessage with the given Zimbra item ID from the
     *  folder's {@link #mSequence} message list. */
    ImapMessage getById(int id) {
        if (id <= 0 || getSize() == 0)
            return null;

        if (mMessageIds == null) {
            // leverage the fact that by default, the message's item id and its IMAP uid are identical
            int sequence = uidSearch(id);
            if (sequence >= 0 && sequence < mSequence.size()) {
                ImapMessage i4msg = mSequence.get(sequence);
                if (i4msg != null && i4msg.msgId == id && !i4msg.isExpunged())
                    return checkRemoved(i4msg);
            }
            // lookup miss means we need to generate the item-id-to-imap-message mapping
            mMessageIds = new HashMap<Integer, ImapMessage>(mSequence.size());
            for (ImapMessage i4msg : mSequence)
                if (i4msg != null)
                    mMessageIds.put(i4msg.msgId, i4msg);
        }
        return checkRemoved(mMessageIds.get(new Integer(id)));
    }

    /** Returns the ImapMessage with the given IMAP UID from the folder's
     *  {@link #mSequence} message list. */
    ImapMessage getByImapId(int uid) {
        return uid > 0 ? getBySequence(uidSearch(uid) + 1) : null;
    }

    /** Returns the ImapMessage with the given 1-based sequence number in the
     *  folder's {@link #mSequence} message list. */
    ImapMessage getBySequence(int seq) {
        return checkRemoved(seq > 0 && seq <= getSize() ? (ImapMessage) mSequence.get(seq - 1) : null);
    }

    /** Returns the last ImapMessage in the folder's {@link #mSequence}
     *  message list.  This message corresponds to the "*" IMAP UID. */
    private ImapMessage getLastMessage() {
        return getBySequence(getSize());
    }

    /** Returns the passed-in ImapMessage, or <tt>null</tt> if the message has
     *  already been expunged.*/
    private ImapMessage checkRemoved(ImapMessage i4msg) {
        return (i4msg == null || i4msg.isExpunged() ? null : i4msg);
    }


    /** Adds the message to the folder.  Messages <b>must</b> be added in
     *  increasing IMAP UID order.  Added messages are appended to the end of
     *  the folder's {@link #mSequence} message list and inserted into the
     *  {@link #mMessageIds} hash (if the latter hash has been instantiated).
     * @return the passed-in ImapMessage. */
    ImapMessage cache(ImapMessage i4msg) {
        if (mSequence == null)
            return null;
        // provide the information missing from the DB search
        i4msg.sflags |= mFolderId == Mailbox.ID_FOLDER_SPAM ? (byte) (ImapMessage.FLAG_SPAM | ImapMessage.FLAG_JUNKRECORDED) : 0;
        // update the folder information
        mSequence.add(i4msg);
        setIndex(i4msg, mSequence.size());
        return i4msg;
    }

    private void setIndex(ImapMessage i4msg, int position) {
        i4msg.sequence = position;
        if (mMessageIds != null)
            mMessageIds.put(new Integer(i4msg.msgId), i4msg);
    }

    /** Cleans up all references to an ImapMessage from all the folder's data
     *  structures other than {@link #mSequence}.  The <tt>mSequence</tt>
     *  cleanup must be done separately. */
    private void uncache(ImapMessage i4msg) {
        if (mMessageIds != null)
            mMessageIds.remove(new Integer(i4msg.msgId));
        mDirtyMessages.remove(new Integer(i4msg.imapUid));
    }


    ImapFlag cacheTag(Tag ltag) {
        assert(!(ltag instanceof Flag));
        if (ltag instanceof Flag)
            return null;
        return mTags.cache(new ImapFlag(ltag.getName(), ltag, true));
    }

    ImapFlag getFlagByName(String name) {
        ImapFlag i4flag = mFlags.getByName(name);
        return (i4flag != null ? i4flag : mTags.getByName(name));
    }

    ImapFlag getTagByMask(long mask) {
        return mTags.getByMask(mask);
    }

    List<String> getFlagList(boolean permanentOnly) {
        List <String> names = mFlags.listNames(permanentOnly);
        names.addAll(mTags.listNames(permanentOnly));
        return names;
    }

    ImapFlagCache getTagset() {
        return mTags;
    }

    void clearTagCache() {
        mTags.clear();
    }


    boolean checkpointSize()  { int last = mLastSize;  return last != (mLastSize = getSize()); }


    void disableNotifications()  { mNotificationsSuspended = true; }
    
    void enableNotifications()   { mNotificationsSuspended = false; }


    static final class DirtyMessage {
        ImapMessage i4msg;
        int modseq;
        DirtyMessage(ImapMessage m, int s)  { i4msg = m;  modseq = s; }
    }

    boolean isMessageDirty(ImapMessage i4msg)  { return mDirtyMessages.containsKey(i4msg.imapUid); }

    void dirtyMessage(ImapMessage i4msg, int modseq) {
        if (mNotificationsSuspended || i4msg != getBySequence(i4msg.sequence))
            return;

        DirtyMessage dirty = mDirtyMessages.get(i4msg.imapUid);
        if (dirty == null)
            mDirtyMessages.put(i4msg.imapUid, new DirtyMessage(i4msg, modseq));
        else if (modseq > dirty.modseq)
            dirty.modseq = modseq;
    }

    DirtyMessage undirtyMessage(ImapMessage i4msg) {
        DirtyMessage dirty = mDirtyMessages.remove(i4msg.imapUid);
        if (dirty != null)
            dirty.i4msg.setAdded(false);
        return dirty;
    }

    Iterator<DirtyMessage> dirtyIterator()  { return mDirtyMessages.values().iterator(); }

    /** Empties the folder's list of modified/created messages. */
    void clearDirty()  { mDirtyMessages.clear(); }


    void dirtyTag(int id, int modseq) {
        dirtyTag(id, modseq, false);
    }

    void dirtyTag(int id, int modseq, boolean removeTag) {
        if (getSize() == 0)
            return;
        long mask = 1L << Tag.getIndex(id);
        for (ImapMessage i4msg : mSequence) {
            if (i4msg != null && (i4msg.tags & mask) != 0) {
                dirtyMessage(i4msg, modseq);
                if (removeTag)
                	i4msg.tags &= ~mask;
            }
        }
    }


    void saveSearchResults(ImapMessageSet i4set) {
        i4set.remove(null);
        mSavedSearchResults = i4set;
    }

    ImapMessageSet getSavedSearchResults() {
        if (mSavedSearchResults == null)
            mSavedSearchResults = new ImapMessageSet();
        return mSavedSearchResults;
    }

    void markMessageExpunged(ImapMessage i4msg) {
        if (mSavedSearchResults != null)
            mSavedSearchResults.remove(i4msg);
        i4msg.setExpunged(true);
    }


    ImapMessageSet getAllMessages() {
        ImapMessageSet result = new ImapMessageSet();
        if (getSize() > 0) {
            for (ImapMessage i4msg : mSequence)
                if (i4msg != null)
                    result.add(i4msg);
        }
        return result;
    }

    ImapMessageSet getFlaggedMessages(ImapFlag i4flag) {
        ImapMessageSet result = new ImapMessageSet();
        if (i4flag != null && getSize() > 0) {
            for (ImapMessage i4msg : mSequence)
                if (i4msg != null && i4flag.matches(i4msg))
                    result.add(i4msg);
        }
        return result;
    }


    private int parseId(String id) {
        // valid values will always be positive ints, so force it there...
        return (int) Math.max(-1, Math.min(Integer.MAX_VALUE, Long.parseLong(id)));
    }

    ImapMessageSet getSubsequence(String subseqStr, boolean byUID) {
        ImapMessageSet result = new ImapMessageSet();
        if (subseqStr == null || subseqStr.trim().equals("") || getSize() == 0)
            return result;
        else if (subseqStr.equals("$"))
            return getSavedSearchResults();

        ImapMessage i4msg = getLastMessage();
        int lastID = (i4msg == null ? (byUID ? Integer.MAX_VALUE : getSize()) : (byUID ? i4msg.imapUid : i4msg.sequence));

        for (String subset : subseqStr.split(",")) {
            if (subset.indexOf(':') == -1) {
                // single message -- get it and add it (may be null)
                int id = (subset.equals("*") ? lastID : parseId(subset));
                result.add(byUID ? getByImapId(id) : getBySequence(id));
            } else {
                // colon-delimited range -- get them and add them (may be null)
                String[] range = subset.split(":", 2);
                int lower = (range[0].equals("*") ? lastID : parseId(range[0]));
                int upper = (range[1].equals("*") ? lastID : parseId(range[1]));
                if (lower > upper)  { int tmp = upper; upper = lower; lower = tmp; }
                if (!byUID) {
                    upper = Math.min(lastID, upper);
                    for (int seq = Math.max(0, lower); seq <= upper; seq++)
                        result.add(getBySequence(seq));
                } else {
                    int start = uidSearch(lower), end = uidSearch(upper);
                    if (start < 0)  start = -start - 1;
                    if (end < 0)    end = -end - 2;
                    for (int seq = start; seq <= end; seq++)
                        if ((i4msg = getBySequence(seq + 1)) != null)
                            result.add(i4msg);
                }
            }
        }

        return result;
    }

    static String encodeSubsequence(List<Integer> items) {
        if (items == null || items.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        int start = -1, last = -1;
        boolean done;
        Iterator<Integer> it = items.iterator();
        do {
            done = !it.hasNext();
            int next = done ? -1 : it.next();
            if (last == -1) {
                last = start = next;
            } else if (done || next != last + 1) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(start);
                if (start != last)
                    sb.append(':').append(last);
                last = start = next;
            } else {
                last = next;
            }
        } while (!done);
        return sb.toString();
    }

    static String encodeSubsequence(Collection<ImapMessage> items, boolean byUID) {
        if (items == null || items.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        int start = -1, last = -1;
        boolean done;
        Iterator<ImapMessage> it = items.iterator();
        do {
            done = !it.hasNext();
            int next = done ? -1 : (byUID ? it.next().imapUid : it.next().sequence);
            if (last == -1) {
                last = start = next;
            } else if (done || next != last + 1) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(start);
                if (start != last)
                    sb.append(':').append(last);
                last = start = next;
            } else {
                last = next;
            }
        } while (!done);
        return sb.toString();
    }


    List<Integer> collapseExpunged() {
        if (getSize() == 0)
            return Collections.emptyList();
        // FIXME: need synchronization
        boolean debug = ZimbraLog.imap.isDebugEnabled();
        List<Integer> removed = new ArrayList<Integer>();
        boolean trimmed = false;
        int seq = 1;
        if (debug)  ZimbraLog.imap.debug("  ** iterating (collapseExpunged)");
        for (ListIterator lit = mSequence.listIterator(); lit.hasNext(); seq++) {
            ImapMessage i4msg = (ImapMessage) lit.next();
            if (i4msg.isExpunged()) {
                if (debug)  ZimbraLog.imap.debug("  ** removing: " + i4msg.msgId);
                // uncache() removes pointers to the message from mUIDs;
                //   if the message appears again in mSequence, it *must* be later
                //   and the subsequent call to setIndex() will correctly set the mUIDs mapping
                uncache(i4msg);
                lit.remove();
                removed.add(seq--);
                trimmed = true;
            } else if (trimmed) {
                setIndex(i4msg, seq);
            }
        }
        return removed;
    }


    private static class AddedItems {
        List<ImapMessage> numbered = new ArrayList<ImapMessage>();
        List<ImapMessage> unnumbered = new ArrayList<ImapMessage>();

        boolean isEmpty()  { return numbered.isEmpty() && unnumbered.isEmpty(); }
        void add(MailItem item) {
            (item.getImapUid() > 0 ? numbered : unnumbered).add(new ImapMessage(item));
        }
        void sort()  { Collections.sort(numbered);  Collections.sort(unnumbered); }
    }

    public void notifyPendingChanges(int changeId, PendingModifications pns) {
        if (!pns.hasNotifications())
            return;

        AddedItems added = new AddedItems();
        if (pns.deleted != null)
            if (!handleDeletes(changeId, pns.deleted))
                return;
        if (pns.created != null)
            if (!handleCreates(changeId, pns.created, added))
                return;
        if (pns.modified != null)
            if (!handleModifies(changeId, pns.modified, added))
                return;

        // add new messages to the currently selected mailbox
        if (mSequence != null && added != null && !added.isEmpty()) {
            added.sort();
            boolean debug = ZimbraLog.imap.isDebugEnabled();

            if (!added.numbered.isEmpty()) {
                // if messages have acceptable UIDs, just add 'em
                StringBuilder addlog = debug ? new StringBuilder("  ** adding messages (ntfn):") : null;
                for (ImapMessage i4msg : added.numbered) {
                    cache(i4msg);
                    if (debug)  addlog.append(' ').append(i4msg.msgId);
                    i4msg.setAdded(true);
                    dirtyMessage(i4msg, changeId);
                }
                if (debug)  ZimbraLog.imap.debug(addlog);
            }
            if (!added.unnumbered.isEmpty()) {
                // 2.3.1.1: "Unique identifiers are assigned in a strictly ascending fashion in
                //           the mailbox; as each message is added to the mailbox it is assigned
                //           a higher UID than the message(s) which were added previously."
                List<Integer> renumber = new ArrayList<Integer>();
                StringBuilder chglog = debug ? new StringBuilder("  ** moved; changing imap uid (ntfn):") : null;
                for (ImapMessage i4msg : added.unnumbered) {
                    renumber.add(i4msg.msgId);
                    if (debug)  chglog.append(' ').append(i4msg.msgId);
                }
                try {
                    if (debug)  ZimbraLog.imap.debug(chglog);
                    // notification will take care of adding to mailbox
                    getMailbox().resetImapUid(mCredentials.getContext(), renumber);
                } catch (ServiceException e) {
                    if (debug)  ZimbraLog.imap.debug("  ** moved; imap uid change failed; msg hidden (ntfn): " + renumber);
                }
            }
        }

        if (mHandler != null && mHandler.isIdle()) {
            try {
                mHandler.sendNotifications(true, true);
            } catch (IOException e) {
                // ImapHandler.dropConnection clears our mHandler and calls SessionCache.clearSession,
                //   which calls Session.doCleanup, which calls Mailbox.removeListener
                ZimbraLog.imap.debug("dropping connection due to IOException during IDLE notification", e);
                mHandler.dropConnection(false);
            }
        }
    }

    private boolean handleDeletes(int changeId, Map<Integer, Object> deleted) {
        for (Object obj : deleted.values()) {
            int id = (obj instanceof MailItem ? ((MailItem) obj).getId() : ((Integer) obj).intValue());
            if (Tag.validateId(id)) {
                mTags.uncache(1L << Tag.getIndex(id));
                dirtyTag(id, changeId, true);
            } else if (id <= 0) {
                continue;
            } else if (id == mFolderId) {
                // notify client that mailbox is deselected due to delete?
                // RFC 2180 3.3: "The server MAY allow the DELETE/RENAME of a multi-accessed
                //                mailbox, but disconnect all other clients who have the
                //                mailbox accessed by sending a untagged BYE response."
                mHandler.dropConnection(true);
            } else {
                ImapMessage i4msg = getById(id);
                if (i4msg != null) {
                    markMessageExpunged(i4msg);
                    ZimbraLog.imap.debug("  ** deleted (ntfn): " + i4msg.msgId);
                }
            }
        }
        return true;
    }

    private boolean handleCreates(int changeId, Map<Integer, MailItem> created, AddedItems newItems) {
        for (MailItem item : created.values()) {
            if (item instanceof Tag) {
                cacheTag((Tag) item);
            } else if (item == null || item.getId() <= 0) {
                continue;
            } else if (!(item instanceof Message || item instanceof Contact)) {
                continue;
            } else if (item.getFolderId() == mFolderId) {
                int msgId = item.getId();
                // make sure this message hasn't already been detected in the folder
                if (getById(msgId) != null)
                    continue;
                ImapMessage i4msg = getByImapId(item.getImapUid());
                if (i4msg == null)
                    newItems.add(item);
                ZimbraLog.imap.debug("  ** created (ntfn): " + msgId);
            }
        }
        return true;
    }

    private boolean handleModifies(int changeId, Map<Integer, Change> modified, AddedItems newItems) {
        boolean debug = ZimbraLog.imap.isDebugEnabled();
        for (Change chg : modified.values()) {
            if (chg.what instanceof Tag && (chg.why & Change.MODIFIED_NAME) != 0) {
                Tag ltag = (Tag) chg.what;
                mTags.uncache(ltag.getBitmask());
                cacheTag(ltag);
                dirtyTag(ltag.getId(), changeId);
            } else if (chg.what instanceof Folder && ((Folder) chg.what).getId() == mFolderId) {
                Folder folder = (Folder) chg.what;
                if ((chg.why & Change.MODIFIED_FLAGS) != 0 && (folder.getFlagBitmask() & Flag.BITMASK_DELETED) != 0) {
                    // notify client that mailbox is deselected due to \Noselect?
                    // RFC 2180 3.3: "The server MAY allow the DELETE/RENAME of a multi-accessed
                    //                mailbox, but disconnect all other clients who have the
                    //                mailbox accessed by sending a untagged BYE response."
                    mHandler.dropConnection(true);
                } else if ((chg.why & (Change.MODIFIED_FOLDER | Change.MODIFIED_NAME)) != 0) {
                    updatePath(folder);
                    // FIXME: can we change the folder's UIDVALIDITY?
                    //        if not, how do we persist it for the session?
                    // RFC 2180 3.4: "The server MAY allow the RENAME of a multi-accessed mailbox
                    //                by simply changing the name attribute on the mailbox."
                }
            } else if (chg.what instanceof Message || chg.what instanceof Contact) {
                MailItem item = (MailItem) chg.what;
                boolean inFolder = isVirtual() || (item.getFolderId() == mFolderId);
                if (!inFolder && (chg.why & Change.MODIFIED_FOLDER) == 0)
                    continue;
                ImapMessage i4msg = getById(item.getId());
                if (i4msg == null) {
                    if (inFolder && !isVirtual()) {
                        newItems.add(item);
                        if (debug)  ZimbraLog.imap.debug("  ** moved (ntfn): " + item.getId());
                    }
                } else if (!inFolder && !isVirtual()) {
                    markMessageExpunged(i4msg);
                } else if ((chg.why & (Change.MODIFIED_TAGS | Change.MODIFIED_FLAGS | Change.MODIFIED_UNREAD)) != 0) {
                    i4msg.setPermanentFlags(item.getFlagBitmask(), item.getTagBitmask(), changeId, this);
                } else if ((chg.why & Change.MODIFIED_IMAP_UID) != 0) {
                    // if the IMAP uid changed, need to bump it to the back of the sequence!
                    markMessageExpunged(i4msg);
                    if (!isVirtual())
                        newItems.add(item);
                    if (debug)  ZimbraLog.imap.debug("  ** imap uid changed (ntfn): " + item.getId());
                }
            }
        }
        return true;
    }

    @Override
    protected void cleanup() {
        // XXX: is there a synchronization issue here?
        if (mHandler != null) {
            ZimbraLog.imap.debug("dropping connection because Session is closing");
            mHandler.dropConnection(true);
        }
    }
}
