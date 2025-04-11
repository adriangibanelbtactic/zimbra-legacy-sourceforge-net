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
 * Created on Nov 28, 2004
 */
package com.zimbra.cs.session;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import com.zimbra.cs.im.IMNotification;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;


public final class PendingModifications {
    public static final class Change {
        public static final int UNMODIFIED         = 0x00000000;
        public static final int MODIFIED_UNREAD    = 0x00000001;
        public static final int MODIFIED_TAGS      = 0x00000002;
        public static final int MODIFIED_FLAGS     = 0x00000004;
        public static final int MODIFIED_CONFIG    = 0x00000008;
        public static final int MODIFIED_SIZE      = 0x00000010;
        public static final int MODIFIED_DATE      = 0x00000020;
        public static final int MODIFIED_SUBJECT   = 0x00000040;
        public static final int MODIFIED_IMAP_UID  = 0x00000080;
        public static final int MODIFIED_FOLDER    = 0x00000100;
        public static final int MODIFIED_PARENT    = 0x00000200;
        public static final int MODIFIED_CHILDREN  = 0x00000400;
        public static final int MODIFIED_SENDERS   = 0x00000800;
        public static final int MODIFIED_NAME      = 0x00001000;
        public static final int MODIFIED_COLOR     = 0x00002000;
        public static final int MODIFIED_POSITION  = 0x00004000;
        public static final int MODIFIED_QUERY     = 0x00008000;
        public static final int MODIFIED_CONTENT   = 0x00010000;
        public static final int MODIFIED_INVITE    = 0x00020000;
        public static final int MODIFIED_URL       = 0x00040000;
        public static final int MODIFIED_VIEW      = 0x00100000;
        public static final int MODIFIED_ACL       = 0x00200000;
        public static final int MODIFIED_CONFLICT  = 0x00400000;
        public static final int INTERNAL_ONLY      = 0x10000000;
        public static final int ALL_FIELDS         = ~0;

        public Object what;
        public int    why;

        Change(Object thing, int reason)  { what = thing;  why = reason; }
    }


    /** Set of all the MailItem types that are included in this structure
     * The mask is generated from the MailItem type using 
     * @link{MailItem#typeToBitmask} */
    public int changedTypes = 0;

    // The key is MailItemID
    public LinkedHashMap<Integer, MailItem> created;
    public HashMap<Integer, Change> modified;
    public HashMap<Integer, Object> deleted;

    /** IMNotifications are strictly sequential right now */
    public List<IMNotification> imNotifications;

    /** used by the Session object to ensure that notifications are reliably
     * received by the listener */
    private int mSequence;
    public int getSequence() { return mSequence; }

    public PendingModifications() { }
    public PendingModifications(int seqno) { mSequence = seqno; }

    public boolean hasNotifications() {
        return ((imNotifications != null && imNotifications.size() > 0) ||
                (deleted  != null && deleted.size() > 0) ||
                (created  != null && created.size() > 0) ||
                (modified != null && modified.size() > 0));
    }

    public void addIMNotification(IMNotification not) {
        if (imNotifications == null) 
            imNotifications = new LinkedList<IMNotification>();
        imNotifications.add(not);
    }
    
    public void recordCreated(MailItem item) {
//        ZimbraLog.mailbox.debug("--> NOTIFY: created " + item.getId());
        if (created == null)
            created = new LinkedHashMap<Integer, MailItem>();
        changedTypes |= MailItem.typeToBitmask(item.getType());
        created.put(item.getId(), item);
    }

    public void recordDeleted(int id, byte type) {
        if (type != 0) 
            changedTypes |= MailItem.typeToBitmask(type);
        Integer key = new Integer(id);
        delete(key, key);
    }

    public void recordDeleted(List<Integer> ids, int typesMask) {
        changedTypes |= typesMask;
        for (Integer id : ids) 
            delete(id,id);
    }

    public void recordDeleted(MailItem item) {
        changedTypes |= MailItem.typeToBitmask(item.getType());
        delete(new Integer(item.getId()), item);
    }

    private void delete(Integer key, Object value) {
//      ZimbraLog.mailbox.debug("--> NOTIFY: deleted " + key);
      if (created != null)
          if (created.remove(key) != null)
              return;
      if (modified != null)
          modified.remove(key);
      if (deleted == null)
          deleted = new HashMap<Integer, Object>();
      deleted.put(key, value);
    }

    public void recordModified(Mailbox mbox, int reason) {
        recordModified(new Integer(0), mbox, reason);
    }

    public void recordModified(MailItem item, int reason) {
        changedTypes |= MailItem.typeToBitmask(item.getType());
        recordModified(new Integer(item.getId()), item, reason);
    }

    private void recordModified(Integer key, Object item, int reason) {
//        ZimbraLog.mailbox.debug("--> NOTIFY: modified " + key + " (" + reason + ')');
        Change chg = null;
        if (created != null && created.containsKey(key))
            return;
        else if (deleted != null && deleted.containsKey(key))
            return;
        else if (modified == null)
            modified = new HashMap<Integer, Change>();
        else {
            chg = modified.get(key);
            if (chg != null) {
                chg.what = item;
                chg.why |= reason;
            }
        }
        if (chg == null)
            chg = new Change(item, reason);
        modified.put(key, chg);
    }
    
    public void add(PendingModifications other) {
        changedTypes |= other.changedTypes;
        
        // XXX: should constrain to folders, tags, and stuff relevant to the current query?
        if (other.deleted != null) {
            for (Object obj : other.deleted.values())
                if (obj instanceof MailItem)
                    recordDeleted((MailItem) obj);
                else if (obj instanceof Integer)
                    recordDeleted((Integer) obj, (byte) 0);
        }

        if (other.created != null) {
            for (MailItem item : other.created.values())
                recordCreated(item);
        }

        if (other.modified != null) {
            for (Change chg : other.modified.values())
                if (chg.what instanceof MailItem)
                    recordModified((MailItem) chg.what, chg.why);
                else if (chg.what instanceof Mailbox)
                    recordModified((Mailbox) chg.what, chg.why);
        }
        
        if (other.imNotifications != null) {
            for (IMNotification not : other.imNotifications) 
                addIMNotification(not);
        }
    }
    
    public void clear()  { 
        created = null;  
        deleted = null;  
        modified = null;
        changedTypes = 0;
        imNotifications = null;
    }
}
