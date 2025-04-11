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
 * Created on Oct 15, 2004
 */
package com.zimbra.cs.mailbox;

import com.zimbra.common.service.ServiceException;


/**
 * @author dkarp
 */
public class Flag extends Tag {

    /** Array mapping each <code>Flag</code> to a character that represents
     *  it in a string encoding.  The <code>Flag</code>'s position in this
     *  array is the same as its "index": <code>-1 - FLAG_ID</code>. */
    static char[] FLAG_REP = new char[31];
        static {
            for (int i = 0; i < 31; i++)  FLAG_REP[i] = 'X';
        }

    public static final int ID_FLAG_FROM_ME = -1;
    public static final int BITMASK_FROM_ME = 1 << getIndex(ID_FLAG_FROM_ME);
        static { FLAG_REP[getIndex(ID_FLAG_FROM_ME)] = 's'; }

    public static final int ID_FLAG_ATTACHED = -2; 
    public static final int BITMASK_ATTACHED = 1 << getIndex(ID_FLAG_ATTACHED);  // 2
        static { FLAG_REP[getIndex(ID_FLAG_ATTACHED)] = 'a'; }

    public static final int ID_FLAG_REPLIED = -3;
    public static final int BITMASK_REPLIED = 1 << getIndex(ID_FLAG_REPLIED);  // 4
        static { FLAG_REP[getIndex(ID_FLAG_REPLIED)] = 'r'; }

    public static final int ID_FLAG_FORWARDED = -4;
    public static final int BITMASK_FORWARDED = 1 << getIndex(ID_FLAG_FORWARDED); // 8
        static { FLAG_REP[getIndex(ID_FLAG_FORWARDED)] = 'w'; }

    public static final int ID_FLAG_COPIED = -5;
    public static final int BITMASK_COPIED = 1 << getIndex(ID_FLAG_COPIED);  // 16
        static { FLAG_REP[getIndex(ID_FLAG_COPIED)] = '2'; }

    public static final int ID_FLAG_FLAGGED = -6;
    public static final int BITMASK_FLAGGED = 1 << getIndex(ID_FLAG_FLAGGED); // 32
        static { FLAG_REP[getIndex(ID_FLAG_FLAGGED)] = 'f'; }

    public static final int ID_FLAG_DRAFT = -7;
    public static final int BITMASK_DRAFT = 1 << getIndex(ID_FLAG_DRAFT); // 64
        static { FLAG_REP[getIndex(ID_FLAG_DRAFT)] = 'd'; }

    public static final int ID_FLAG_DELETED = -8;
    public static final int BITMASK_DELETED = 1 << getIndex(ID_FLAG_DELETED); // 128
        static { FLAG_REP[getIndex(ID_FLAG_DELETED)] = 'x'; }

    public static final int ID_FLAG_NOTIFIED = -9;
    public static final int BITMASK_NOTIFIED = 1 << getIndex(ID_FLAG_NOTIFIED); // 256
        static { FLAG_REP[getIndex(ID_FLAG_NOTIFIED)] = 'n'; }

    /**
     * The outside world (callers of {@link Mailbox} methods treat
     * <code>FLAG_UNREAD</code> like the other flags.  Internally,
     * we break it out into a separate <code>unreadCount</code> 
     * variable.  It's also persisted in a separate indexed column, for fast
     * lookups of unread <code>MailItem</code>s.
     */
    public static final int ID_FLAG_UNREAD = -10;
    public static final int BITMASK_UNREAD = 1 << getIndex(ID_FLAG_UNREAD); // 512
        static { FLAG_REP[getIndex(ID_FLAG_UNREAD)] = 'u'; }

    public static final int ID_FLAG_SUBSCRIBED = -20;
    public static final int BITMASK_SUBSCRIBED = 1 << getIndex(ID_FLAG_SUBSCRIBED); // 524288
        static { FLAG_REP[getIndex(ID_FLAG_SUBSCRIBED)] = '*'; }

    public static final int ID_FLAG_EXCLUDE_FREEBUSY = -21;
    public static final int BITMASK_EXCLUDE_FREEBUSY = 1 << getIndex(ID_FLAG_EXCLUDE_FREEBUSY); // 1048576
         static { FLAG_REP[getIndex(ID_FLAG_EXCLUDE_FREEBUSY)] = 'b'; }

     public static final int ID_FLAG_CHECKED = -22;
     public static final int BITMASK_CHECKED = 1 << getIndex(ID_FLAG_CHECKED); // 2097152
         static { FLAG_REP[getIndex(ID_FLAG_CHECKED)] = '#'; }


    static final String UNREAD_FLAG_ONLY = getAbbreviation(ID_FLAG_UNREAD) + "";

    public static final int FLAG_SYSTEM = BITMASK_FROM_ME | BITMASK_ATTACHED | BITMASK_COPIED | BITMASK_DRAFT;

    public static final int FLAGS_FOLDER  = BITMASK_CHECKED | BITMASK_SUBSCRIBED | BITMASK_EXCLUDE_FREEBUSY;
    public static final int FLAGS_MESSAGE = BITMASK_FROM_ME | BITMASK_REPLIED  | BITMASK_FORWARDED |
                                            BITMASK_DRAFT   | BITMASK_NOTIFIED | BITMASK_UNREAD;
    public static final int FLAGS_GENERIC = BITMASK_ATTACHED | BITMASK_COPIED | BITMASK_FLAGGED | BITMASK_DELETED;

    /** Bitmask of all valid flags <b>except</b> {@link #BITMASK_UNREAD}. */
    public static final int FLAGS_ALL = (FLAGS_FOLDER | FLAGS_MESSAGE | FLAGS_GENERIC) & ~BITMASK_UNREAD;

    public static final byte FLAG_GENERIC         = 0x00;
    public static final byte FLAG_IS_MESSAGE_ONLY = 0x01;
    public static final byte FLAG_IS_FOLDER_ONLY  = 0x02;
   
    private byte mAttributes;

    Flag(Mailbox mbox, UnderlyingData ud) throws ServiceException {
		super(mbox, ud);
		if (mData.type != TYPE_FLAG)
			throw new IllegalArgumentException();
	}

    public byte getIndex() {
        return getIndex(mId);
    }
    public static byte getIndex(int flagId) {
        return (byte) (-flagId - 1);
    }

    public char getAbbreviation() {
        return getAbbreviation(mId);
    }
    public static char getAbbreviation(int flagId) {
        return FLAG_REP[-flagId - 1];
    }


    boolean isFolderOnly() {
        return (mAttributes & FLAG_IS_FOLDER_ONLY) != 0;
    }

    boolean canTag(MailItem item) {
        if ((mAttributes & FLAG_IS_FOLDER_ONLY) != 0 && item instanceof Folder)
            return true;
		if (!item.isTaggable())
			return false;
        if ((mAttributes & FLAG_IS_MESSAGE_ONLY) != 0 && !(item instanceof Message))
            return false;
		return true;
	}


    static void validateFlags(int flags) throws ServiceException {
        if ((flags & ~Flag.FLAGS_ALL) > 0)
            throw ServiceException.FAILURE("invalid value for flags: " + flags, null);
    }

    /** @return the "external" flag bitmask for the given flag string, which includes {@link Flag#BITMASK_UNREAD}. */
    public static int flagsToBitmask(String flags) {
        int bitmask = 0;
        if (flags != null)
            for (int i = 0; i < flags.length(); i++) {
                char c = flags.charAt(i);
                for (int j = 0; j < MailItem.MAX_FLAG_COUNT; j++)
                    if (FLAG_REP[j] == c) {
                        bitmask |= 1 << j;
                        break;
                    }
            }
        return bitmask;
    }

    static String bitmaskToFlags(int bitmask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; bitmask != 0 && i < MAX_FLAG_COUNT - 1; i++)
            if ((bitmask & (1 << i)) != 0) {
                sb.append(FLAG_REP[i]);
                bitmask &= ~(1 << i);
            }
        return sb.toString();
    }


	boolean isMutable()       { return false; }
	boolean trackUnread()     { return false; }


	static Flag instantiate(Mailbox mbox, String name, byte attributes, int id) throws ServiceException {
		UnderlyingData data = new UnderlyingData();
		data.id       = id;
		data.type     = MailItem.TYPE_FLAG;
		data.folderId = Mailbox.ID_FOLDER_TAGS;
		data.name     = name;

		Flag flag = new Flag(mbox, data);
		flag.mAttributes = attributes;
		return flag;
	}

	void decodeMetadata(Metadata meta)     { }
	Metadata encodeMetadata(Metadata meta) { return meta; }
}
