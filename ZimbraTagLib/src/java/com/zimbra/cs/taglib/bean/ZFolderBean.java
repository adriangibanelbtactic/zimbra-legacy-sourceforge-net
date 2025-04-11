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
package com.zimbra.cs.taglib.bean;

import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZFolder.Color;
import com.zimbra.cs.zclient.ZFolder.View;
import com.zimbra.cs.zclient.ZGrant;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMountpoint;
import com.zimbra.cs.zclient.ZSearchFolder;
import com.zimbra.common.soap.VoiceConstants;

import java.util.List;

public class ZFolderBean {

    private ZFolder mFolder;
    
    public ZFolderBean(ZFolder folder) {
        mFolder = folder;
    }

    public ZFolder folderObject() { return mFolder; }

    public ZFolderBean getParent() { return mFolder.getParent() == null ? null : new ZFolderBean(mFolder.getParent()); }

    public String getId() { return mFolder.getId(); }

    /** Returns the folder's name.  Note that this is the folder's
     *  name (e.g. <code>"foo"</code>), not its absolute pathname
     *  (e.g. <code>"/baz/bar/foo"</code>).
     * 
     * @see #getPath()
     * @return name
     * 
     */
    public String getName() { return mFolder.getName(); }

    /** Returns the folder's absolute path.  Paths are UNIX-style with 
     *  <code>'/'</code> as the path delimiter.  Paths are relative to
     *  the user root folder,
     *  which has the path <code>"/"</code>.  So the Inbox's path is
     *  <code>"/Inbox"</code>, etc.
     * @return path
     */
    public String getPath() { return mFolder.getPath(); }

    /** Returns the folder's  path relative to the root
     * @return path
     */
    public String getRootRelativePath() { return mFolder.getRootRelativePath(); }
    
    /**
     * 
     * @return parent id of folder, or null if root folder.
     */
    public String getParentId() { return mFolder.getParentId(); }

    /**
     * @return number of unread items in folder
     */
    public int getUnreadCount() { 
        return getIsDrafts() ? getMessageCount() : mFolder.getUnreadCount();
    }
    
    public boolean getHasUnread() { return getUnreadCount() > 0; }

    /**
     * @return number of unread items in folder
     */
    public int getMessageCount() { return mFolder.getMessageCount(); }

    /**
     * @return size of folder in bytes
     */
    public long getSize() { return mFolder.getSize(); }
    
    /** @return Returns the "hint" as to which view to use to display the folder's
     *  contents.
     */
    public String getDefaultView() { return mFolder.getDefaultView().name(); } 
    
    /**
     *  checked in UI (#), exclude free/(b)usy info, IMAP subscribed (*)
     * @return the flags
     */
    public String getFlags() { return mFolder.getFlags(); }

    public boolean getHasFlags() { return mFolder.hasFlags(); }
    
    public boolean getIsCheckedInUI() { return mFolder.isCheckedInUI(); }

    public boolean getIsExcludedFromFreeBusy() { return mFolder.isExcludedFromFreeBusy(); }

    public boolean getIsIMAPSubscribed() { return mFolder.isIMAPSubscribed(); }

    /**
     * range 0-127; defaults to 0 if not present; client can display only 0-7
     * 
     * @return color
     */
    public String getColor() {
        Color color = mFolder.getColor();
        if (color == Color.defaultColor) {
            color = (getIsContactView() || getIsTaskView()) ? Color.gray :  Color.orange;
        }
        return color.name();
    }

    /**
     * remote URL (RSS, iCal, etc) this folder syncs to
     * 
     * @return remote url
     */
    public String getRemoteURL() { return mFolder.getRemoteURL(); }
    
    /**
     * for remote folders, the access rights the authenticated user has on the folder.
     * 
     * @return effective perms
     */
    public String getEffectivePerm() { return mFolder.getEffectivePerms(); }
    
    /**
     * url to the folder on rest interface for rest-enabled apps (such as wiki and notebook)
     * 
     * @return URL, if returned from server.
     */
    public String getRestURL() { return mFolder.getRestURL(); }

    /**
     * return url with trailing slash removed
     *
     * @return URL, if returned from server.
     */
    public String getRestURLAsFile() {
        String url = mFolder.getRestURL();
        if (url.endsWith("/"))
            url = url.substring(0, url.length()-1);
        return url;
    }
    
    /**
     * @return return grants or empty list if no grants
     */
    public List<ZGrant> getGrants() { return mFolder.getGrants(); }

    /**
     * @return sub folders, or empty list if no sub folders
     */
    public List<ZFolder> getSubFolders() { return mFolder.getSubFolders(); }

    public int getSubFolderCount() { return mFolder.getSubFolders().size(); }

    public boolean getHasChildren() { return !mFolder.getSubFolders().isEmpty(); }
    
    public boolean getIsSearchFolder() { return mFolder instanceof ZSearchFolder; }
    
    public String getQuery() { return getIsSearchFolder() ? ((ZSearchFolder) mFolder).getQuery() : ""; }
    
    //public ZFolder getSubFolderByPath(String path);
    
    public boolean getIsInbox() { return mFolder.getId().equals(ZFolder.ID_INBOX); }
    public boolean getIsChats() { return mFolder.getId().equals(ZFolder.ID_CHATS); }
    public boolean getIsTrash() { return mFolder.getId().equals(ZFolder.ID_TRASH); }
    public boolean getIsSpam() { return mFolder.getId().equals(ZFolder.ID_SPAM); }
    public boolean getIsSent() { return mFolder.getId().equals(ZFolder.ID_SENT); }    
    public boolean getIsDrafts() { return mFolder.getId().equals(ZFolder.ID_DRAFTS); }
    public boolean getIsContacts() { return mFolder.getId().equals(ZFolder.ID_CONTACTS); }
    public boolean getIsCalendar() { return mFolder.getId().equals(ZFolder.ID_CALENDAR); }    
    public boolean getIsNotebook() { return mFolder.getId().equals(ZFolder.ID_NOTEBOOK); }    
    public boolean getIsAutoContacts() { return mFolder.getId().equals(ZFolder.ID_AUTO_CONTACTS); }

    public boolean getIsVoiceMailInbox() { return getIsVoiceView() && VoiceConstants.FNAME_VOICEMAILINBOX.equals(mFolder.getName()); } 
    public boolean getIsMissedCalls() { return getIsVoiceView() && VoiceConstants.FNAME_MISSEDCALLS.equals(mFolder.getName()); }
    public boolean getIsAnsweredCalls() { return getIsVoiceView() && VoiceConstants.FNAME_ANSWEREDCALLS.equals(mFolder.getName()); }
    public boolean getIsPlacedCalls() { return getIsVoiceView() && VoiceConstants.FNAME_PLACEDCALLS.equals(mFolder.getName()); }
    public boolean getIsVoiceMailTrash() { return getIsVoiceView() && VoiceConstants.FNAME_TRASH.equals(mFolder.getName()); }

    public boolean getIsMailView() {
        ZFolder.View view = mFolder.getDefaultView();
        return view == null || view == ZFolder.View.message || view == ZFolder.View.conversation;
    }
    
    public boolean getIsNullView() { return mFolder.getDefaultView() == null; }    
    public boolean getIsMessageView() { return mFolder.getDefaultView() == ZFolder.View.message; }
    public boolean getIsContactView() { return mFolder.getDefaultView() == ZFolder.View.contact; }    
    public boolean getIsConversationView() { return mFolder.getDefaultView() == ZFolder.View.conversation; }        
    public boolean getIsAppointmentView() { return mFolder.getDefaultView() == ZFolder.View.appointment; }
    public boolean getIsWikiView() { return mFolder.getDefaultView() == ZFolder.View.wiki; }
    public boolean getIsTaskView() { return mFolder.getDefaultView() == ZFolder.View.task; }
    public boolean getIsVoiceView() { return mFolder.getDefaultView() == ZFolder.View.voice; }

    public boolean getIsSystemFolder() { return mFolder.isSystemFolder(); }
    
    public boolean getIsMountPoint() { return mFolder instanceof ZMountpoint; }

    public boolean getIsInTrash() {
        if (getId().equals(ZFolder.ID_TRASH))
            return true;

        ZFolder parent = mFolder.getParent();
        while (parent != null) {
            if (parent.getId().equals(ZFolder.ID_TRASH))
                return true;
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * @return owner display name if mountpoint, otherwise null
     */
    public String getOwnerDisplayName() {
        return mFolder instanceof ZMountpoint ?
                ((ZMountpoint) mFolder ).getOwnerDisplayName() : null;
    }

    /**
     * @return owner id if mountpoint, otherwise null
     */
    public String getOwnerId() {
        return mFolder instanceof ZMountpoint ?
                ((ZMountpoint) mFolder ).getOwnerId() : null;
    }

    /**
     * @return remote id if mountpoint, otherwise null
     */
    public String getRemoteId() {
        return mFolder instanceof ZMountpoint ?
                ((ZMountpoint) mFolder ).getRemoteId() : null;
    }

    /**
     * @return canonical remote id if mountpoint, otherwise null
     */
    public String getCanonicalRemoteId() {
        return mFolder instanceof ZMountpoint ?
                ((ZMountpoint) mFolder ).getCanonicalRemoteId() : null;
    }

    public boolean getIsFeed() { return mFolder.getRemoteURL() != null; }

    public String getCanonicalId() { return (mFolder instanceof ZMountpoint) ? ((ZMountpoint)mFolder).getCanonicalRemoteId() : mFolder.getId(); }

    private int mDepth = -1;
    
    public synchronized int getDepth() {
        if (mDepth != -1) return mDepth;
        int depth=0;
        String path = getPath();
        for (int i=1; i < path.length(); i++) {
            if (path.charAt(i) == ZMailbox.PATH_SEPARATOR_CHAR) depth++; 
        }
        return depth;
    }

    public boolean getIsMessageMoveTarget() {
        return getIsConversationMoveTarget();
    }

    public boolean getIsConversationMoveTarget() {
        return (getIsMessageView() || getIsConversationView() || getIsNullView()) &&
                !(getIsDrafts() || getIsMountPoint() || getIsSearchFolder() || getRemoteURL() != null) &&
                !getId().equals(ZFolder.ID_CHATS);
    }

    public boolean getIsMessageFolderMarkReadTarget() {
        return (getIsMessageView() || getIsConversationView() || getIsNullView()) &&
                !(getIsDrafts() || getIsMountPoint() || getIsSearchFolder());
    }

    public boolean getIsMessageFolderRenameTarget() {
        return (getIsMessageView() || getIsConversationView() || getIsNullView()) &&
                !(getIsSystemFolder() || getIsSearchFolder());
    }

    public boolean getIsMessageFolderMoveSource() {
        return (getIsMessageView() || getIsConversationView() || getIsNullView()) &&
                !getIsSystemFolder();
    }

    public boolean getIsMessageFolderDeleteTarget() {
        return (getIsMessageView() || getIsConversationView() || getIsNullView()) &&
                !(getIsSystemFolder() || getIsSearchFolder());
    }

    public boolean getIsContactMoveTarget() {
        return (getIsContactView() || getIsTrash()) &&
                !(getIsDrafts() || getIsMountPoint() || getIsSearchFolder() || getRemoteURL() != null);
    }

    public boolean getIsAppointmentMoveTarget() {
        //TODO: handle perm check on mountpoint!
        return getIsAppointmentView() && !(getIsMountPoint() || getRemoteURL() != null);
    }

    public boolean getIsContactCreateTarget() {
        return (getIsContactView()) &&
                !(getIsDrafts() || getIsMountPoint() || getIsSearchFolder() || getRemoteURL() != null);
    }

    public String getStyleColor() {
        return getStyleColor(mFolder.getColor(), mFolder.getDefaultView());
    }

    public static String getStyleColor(Color color, View view) {
        switch(color) {
            case blue:
                return "Blue";
            case cyan:
                return "Cyan";
            case green:
                return "Green";
            case purple:
                return "Purple";
            case red:
                return "Red";
            case yellow:
                return "Yellow";
            case pink:
                return "Pink";
            case gray:
                return "Gray";
            case orange:
                return "Orange";
            default:
                if (view == View.contact || view == View.task)
                    return "Gray";
                else
                    return "Orange";
        }
    }

    public String getImage() {
        if (getIsSearchFolder()) {
            return "common/SearchFolder.gif";
        } else if (getIsAppointmentView()) {
            if (getIsMountPoint()) {
                return "calendar/SharedCalendarFolder.gif";
            } else {
                return "calendar/CalendarFolder.gif";
            }
        } else if (getIsContactView()) {
            if (getIsMountPoint()) {
                return "contacts/SharedContactsFolder.gif";
            } else if (getIsAutoContacts()) {
                return "contacts/EmailedContacts.gif";
            } else {
                return "contacts/ContactsFolder.gif";
            }
        } else if (getIsSystemFolder()) {
            if (getIsInbox())
                return "mail/Inbox.gif";
            else if (getIsTrash())
                return "common/Trash.gif";
            else if (getIsSpam())
                return "mail/SpamFolder.gif";
            else if (getIsSent())
                return "mail/SentFolder.gif";
            else if (getIsDrafts())
                return "mail/DraftFolder.gif";
            else
                return "common/Folder.gif";
        } else if (getIsMailView() && getIsFeed()) {
            return "mail/RSS.gif";
        } else if (getIsMountPoint()) {
            return "mail/SharedMailFolder.gif";
        } else if (getIsVoiceView()) {
            String name = getName();
            if (VoiceConstants.FNAME_PLACEDCALLS.equals(name)) {
                return "voicemail/PlacedCalls.gif";
            } else if (VoiceConstants.FNAME_ANSWEREDCALLS.equals(name)) {
                return "voicemail/AnsweredCalls.gif";
            } else if (VoiceConstants.FNAME_MISSEDCALLS.equals(name)) {
                return "voicemail/MissedCalls.gif";
            } else if (VoiceConstants.FNAME_VOICEMAILINBOX.equals(name)) {
                return "voicemail/Voicemail.gif";
            } else if (VoiceConstants.FNAME_TRASH.equals(name)) {
                return "common/Trash.gif";
            }
            return null;
        } else {
            return "common/Folder.gif";
        }
    }
}
