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

package com.zimbra.cs.zclient;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.zclient.event.ZModifyEvent;
import com.zimbra.cs.zclient.event.ZModifyMessageEvent;
import com.zimbra.common.soap.Element;

import java.util.ArrayList;
import java.util.List;

public class ZMessageHit implements ZSearchHit {

    private String mId;
    private String mFlags;
    private String mFragment;
    private String mSubject;
    private String mSortField;
    private String mTags;
    private String mConvId;
    private String mFolderId;
    private float mScore;
    private long mDate;
    private int mSize;
    private boolean mContentMatched;
    private List<String> mMimePartHits;
    private ZEmailAddress mSender;
    private ZMessage mMessage;
    private boolean mIsInvite;

    public ZMessageHit(Element e) throws ServiceException {
        mId = e.getAttribute(MailConstants.A_ID);
        mFolderId = e.getAttribute(MailConstants.A_FOLDER);
        mFlags = e.getAttribute(MailConstants.A_FLAGS, null);
        mDate = e.getAttributeLong(MailConstants.A_DATE);
        mTags = e.getAttribute(MailConstants.A_TAGS, null);
        Element fr = e.getOptionalElement(MailConstants.E_FRAG);
        if (fr != null) mFragment = fr.getText();
        Element sub = e.getOptionalElement(MailConstants.E_SUBJECT);
        if (sub != null) mSubject = sub.getText();
        mSortField = e.getAttribute(MailConstants.A_SORT_FIELD, null);
        mSize = (int) e.getAttributeLong(MailConstants.A_SIZE);
        mConvId = e.getAttribute(MailConstants.A_CONV_ID);
        mScore = (float) e.getAttributeDouble(MailConstants.A_SCORE, 0);
        mContentMatched = e.getAttributeBool(MailConstants.A_CONTENTMATCHED, false);
        mMimePartHits = new ArrayList<String>();
        for (Element hp: e.listElements(MailConstants.E_HIT_MIMEPART)) {
            mMimePartHits.add(hp.getAttribute(MailConstants.A_PART));
        }
        for (Element emailEl : e.listElements(MailConstants.E_EMAIL)) {
            String t = emailEl.getAttribute(MailConstants.A_ADDRESS_TYPE, null);
            if (ZEmailAddress.EMAIL_TYPE_FROM.equals(t)) {
                mSender = new ZEmailAddress(emailEl);
                break;
            }
        }

        Element mp = e.getOptionalElement(MailConstants.E_MIMEPART);
        if (mp != null) {
            mMessage = new ZMessage(e);
        }
        mIsInvite = e.getOptionalElement(MailConstants.E_INVITE) != null;
    }

    public String getId() {
        return mId;
    }

    public boolean getContentMatched() {
        return mContentMatched;
    }

    public boolean getIsInvite() {
        return mIsInvite;
    }

    public String toString() {
        ZSoapSB sb = new ZSoapSB();
        sb.beginStruct();
        sb.add("id", mId);
        sb.add("conversationId", mConvId);
        sb.add("flags", mFlags);
        sb.add("isInvite", mIsInvite);
        sb.add("fragment", mFragment);
        sb.add("subject", mSubject);
        sb.addDate("date", mDate);
        sb.add("size", mSize);
        if (mSender != null) sb.addStruct("sender", mSender.toString());
        sb.add("sortField", mSortField);
        sb.add("score", mScore);
        sb.add("mimePartHits", mMimePartHits, true, false);
        if (mMessage != null) sb.addStruct("message", mMessage.toString());
        sb.endStruct();
        return sb.toString();
    }

    public String getFlags() {
        return mFlags;
    }

    public long getDate() {
        return mDate;
    }

    public String getFragment() {
        return mFragment;
    }

    public String getSortField() {
        return mSortField;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getTagIds() {
        return mTags;
    }
    
    public String getConversationId() {
        return mConvId;
    }

    public List<String> getMimePartHits() {
        return mMimePartHits;
    }

    public ZMessage getMessage() {
        return mMessage;
    }

    public float getScore() {
        return mScore;
    }

    public ZEmailAddress getSender() {
        return mSender;
    }

    public long getSize() {
        return mSize;
    }

    public boolean hasFlags() {
        return mFlags != null && mFlags.length() > 0;
    }

    public boolean hasTags() {
        return mTags != null && mTags.length() > 0;
    }
    public boolean hasAttachment() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.attachment.getFlagChar()) != -1;
    }

    public boolean isDeleted() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.deleted.getFlagChar()) != -1;
    }

    public boolean isDraft() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.draft.getFlagChar()) != -1;
    }

    public boolean isFlagged() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.flagged.getFlagChar()) != -1;
    }

    public boolean isForwarded() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.forwarded.getFlagChar()) != -1;
    }

    public boolean isNotificationSent() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.notificationSent.getFlagChar()) != -1;
    }

    public boolean isRepliedTo() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.replied.getFlagChar()) != -1;
    }

    public boolean isSentByMe() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.sentByMe.getFlagChar()) != -1;
    }

    public boolean isUnread() {
        return hasFlags() && mFlags.indexOf(ZMessage.Flag.unread.getFlagChar()) != -1;
    }

    public String getFolderId() {
        return mFolderId;
    }

	public void modifyNotification(ZModifyEvent event) throws ServiceException {
		if (event instanceof ZModifyMessageEvent) {
			ZModifyMessageEvent mevent = (ZModifyMessageEvent) event;
            mFlags = mevent.getFlags(mFlags);
            mTags = mevent.getTagIds(mTags);
            mFolderId = mevent.getFolderId(mFolderId);
            mConvId = mevent.getConversationId(mConvId);
            /* updated fetched message if we have one */
            if (getMessage() != null)
                getMessage().modifyNotification(event);
        }
	}
}
