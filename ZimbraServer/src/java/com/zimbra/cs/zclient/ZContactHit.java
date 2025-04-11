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
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.cs.zclient.event.ZModifyContactEvent;
import com.zimbra.cs.zclient.event.ZModifyEvent;
import com.zimbra.soap.Element;

import java.util.Map;

public class ZContactHit implements ZSearchHit {

    private String mId;
    private String mFlags;
    private String mTagIds;
    private String mSortField;
    private String mFileAsStr;
    private String mEmail, mEmail2, mEmail3;
    private String mRevision;
    private String mFolderId;
    private String mType;
    private String mDlist;
    private float mScore;
    private long mMetaDataDate;
        
    public ZContactHit(Element e) throws ServiceException {
        mId = e.getAttribute(MailService.A_ID);
        mFlags = e.getAttribute(MailService.A_FLAGS, null);        
        mTagIds = e.getAttribute(MailService.A_TAGS, null);        
        mSortField = e.getAttribute(MailService.A_SORT_FIELD, null);
        mScore = (float) e.getAttributeDouble(MailService.A_SCORE, 0);
        mFileAsStr = e.getAttribute(MailService.A_FILE_AS_STR, null);
        mRevision = e.getAttribute(MailService.A_REVISION, null);
        mFolderId = e.getAttribute(MailService.A_FOLDER);
        mType = e.getAttribute(MailService.A_CONTACT_TYPE, null);
        mEmail = e.getAttribute(Contact.A_email, null);
        mEmail2 = e.getAttribute(Contact.A_email2, null);
        mEmail3 = e.getAttribute(Contact.A_email3, null);
        mMetaDataDate = e.getAttributeLong(MailService.A_MODIFIED_DATE, 0) * 1000;
        mDlist = e.getAttribute(Contact.A_dlist, null);
    }

    public String toString() {
        ZSoapSB sb = new ZSoapSB();
        sb.beginStruct();
        sb.add("id", mId);
        sb.add("tags", mTagIds);
        sb.add("flags", mFlags);
        sb.add("sortField", mSortField);
        sb.add("type", mType);
        sb.add("score", mScore);
        sb.add("fileAsStr", mFileAsStr);
        sb.add("revision", mRevision);
        sb.add("folderId", mFolderId);
        sb.add(Contact.A_dlist, mDlist);
        sb.add(Contact.A_email, mEmail);
        sb.add(Contact.A_email2, mEmail2);
        sb.add(Contact.A_email3, mEmail3);
        sb.endStruct();
        return sb.toString();
    }

    public boolean isGroup() {
        return Contact.TYPE_GROUP.equalsIgnoreCase(getType());
    }

    public boolean isContact() {
        return !isGroup();
    }

    public String getDlist() {
        return mDlist;
    }
    
    public String getType() {
        return mType;
    }
    
    public String getTagIds() {
        return mTagIds;
    }

    public String getEmail() {
        return mEmail;
    }

    public String getEmail2() {
        return mEmail2;
    }

    public String getEmail3() {
        return mEmail3;
    }

    public String getFileAsStr() {
        return mFileAsStr;
    }

    public String getFolderId() {
        return mFolderId;
    }

    public String getId() {
        return mId;
    }

    public String getRevision() {
        return mRevision;
    }

    public float getScore() {
        return mScore;
    }

    public String getSortField() {
        return mSortField;
    }

    public long getMetaDataChangedDate() {
        return mMetaDataDate;
    }

	public void modifyNotification(ZModifyEvent event) throws ServiceException {
		if (event instanceof ZModifyContactEvent) {
			ZModifyContactEvent cevent = (ZModifyContactEvent) event;
            if (cevent.getId().equals(mId)) {
                mTagIds = cevent.getTagIds(mTagIds);
                mFolderId = cevent.getFolderId(mFolderId);
                mFlags = cevent.getFlags(mFlags);
                mRevision = cevent.getRevision(mRevision);
                mFileAsStr = cevent.getFileAsStr(mFileAsStr);
                mEmail = cevent.getEmail(mEmail);
                mEmail2 = cevent.getEmail(mEmail2);
                mEmail3 = cevent.getEmail(mEmail3);
                //mMetaDataChangedDate = cevent.getMetaDataChangedDate(mMetaDataChangedDate);
                Map<String, String> attrs = cevent.getAttrs(null);
                String dlist = attrs != null ? attrs.get(Contact.A_dlist) : null;
                if (dlist != null) mDlist = dlist;
            }
        }
	}
}
