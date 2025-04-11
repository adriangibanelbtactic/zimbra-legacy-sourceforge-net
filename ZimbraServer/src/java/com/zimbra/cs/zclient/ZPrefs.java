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

import com.zimbra.common.calendar.TZIDMapper;
import com.zimbra.cs.account.Provisioning;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class ZPrefs {

    private Map<String, List<String>> mPrefs;

    public ZPrefs(Map<String, List<String>> prefs) {
        mPrefs = prefs;
    }

    /**
     * @param name name of pref to get
     * @return null if unset, or first value in list
     */
    public String get(String name) {
        List<String> value = mPrefs.get(name);
        return (value == null || value.isEmpty()) ? null : value.get(0);

    }

    public boolean getBool(String name) {
        return Provisioning.TRUE.equals(get(name));
    }

    public long getLong(String name) {
        String v = get(name);
        try {
            return v == null ? -1 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public Map<String, List<String>> getPrefs() { return mPrefs; }

    public boolean getUseTimeZoneListInCalendar() { return getBool(Provisioning.A_zimbraPrefUseTimeZoneListInCalendar); }

    public boolean getReadingPaneEnabled() { return getBool(Provisioning.A_zimbraPrefReadingPaneEnabled); }

    public boolean getMailSignatureEnabled() { return getBool(Provisioning.A_zimbraPrefMailSignatureEnabled); }

    public boolean getIncludeSpamInSearch() { return getBool(Provisioning.A_zimbraPrefIncludeSpamInSearch); }

    public boolean getIncludeTrashInSearch() { return getBool(Provisioning.A_zimbraPrefIncludeTrashInSearch); }

    public boolean getShowSearchString() { return getBool(Provisioning.A_zimbraPrefShowSearchString); }

    public boolean getShowFragments() { return getBool(Provisioning.A_zimbraPrefShowFragments); }

    public boolean getSaveToSent() { return getBool(Provisioning.A_zimbraPrefSaveToSent); }

    public boolean getOutOfOfficeReplyEnabled() { return getBool(Provisioning.A_zimbraPrefOutOfOfficeReplyEnabled); }

    public boolean getNewMailNotificationsEnabled() { return getBool(Provisioning.A_zimbraPrefNewMailNotificationEnabled); }

    public boolean getMailLocalDeliveryDisabled() { return getBool(Provisioning.A_zimbraPrefMailLocalDeliveryDisabled); }

    public boolean getMessageViewHtmlPreferred() { return getBool(Provisioning.A_zimbraPrefMessageViewHtmlPreferred); }

    public boolean getAutoAddAddressEnabled() { return getBool(Provisioning.A_zimbraPrefAutoAddAddressEnabled); }

    public String getShortcuts() { return get(Provisioning.A_zimbraPrefShortcuts); }

    public boolean getUseKeyboardShortcuts() { return getBool(Provisioning.A_zimbraPrefUseKeyboardShortcuts); }

    public String getSignatureStyle() { return get(Provisioning.A_zimbraPrefMailSignatureStyle); }
    public boolean getSignatureStyleTop() { return "outlook".equals(getSignatureStyle()); }
    public boolean getSignatureStyleBottom() { return "internet".equals(getSignatureStyle()); }

    public String getGroupMailBy() { return get(Provisioning.A_zimbraPrefGroupMailBy); }

    public boolean getGroupByConversation() {
        String gb = getGroupMailBy();
        return "conversation".equals(gb);
    }

    public boolean getGroupByMessage() {
        String gb = getGroupMailBy();
        return gb == null || "message".equals(gb);
    }


    public String getSkin() { return get(Provisioning.A_zimbraPrefSkin); }
    
    public String getDedupeMessagesSentToSelf() { return get(Provisioning.A_zimbraPrefDedupeMessagesSentToSelf); }

    public String getMailInitialSearch() { return get(Provisioning.A_zimbraPrefMailInitialSearch); }

    public String getNewMailNotificationAddress() { return get(Provisioning.A_zimbraPrefNewMailNotificationAddress); }

    public String getMailForwardingAddress() { return get(Provisioning.A_zimbraPrefMailForwardingAddress); }

    public String getOutOfOfficeReply() { return get(Provisioning.A_zimbraPrefOutOfOfficeReply); }

    public String getMailSignature() { return get(Provisioning.A_zimbraPrefMailSignature); }

    public long getMailItemsPerPage() { return getLong(Provisioning.A_zimbraPrefMailItemsPerPage); }

    public long getContactsPerPage() { return getLong(Provisioning.A_zimbraPrefContactsPerPage); }

    public long getCalendarFirstDayOfWeek() { return getLong(Provisioning.A_zimbraPrefCalendarFirstDayOfWeek); }

    public long getCalendarDayHourStart() {
        long hour = getLong(Provisioning.A_zimbraPrefCalendarDayHourStart);
        return hour == -1 ? 8 : hour;
    }

    public long getCalendarDayHourEnd() {
         long hour = getLong(Provisioning.A_zimbraPrefCalendarDayHourEnd);
        return hour == -1 ? 18 : hour;
    }

    public String getCalendarInitialView() { return get(Provisioning.A_zimbraPrefCalendarInitialView); }

    public String getTimeZoneId() { return get(Provisioning.A_zimbraPrefTimeZoneId); }

    public String getTimeZoneWindowsId() { return TZIDMapper.toWindows(get(Provisioning.A_zimbraPrefTimeZoneId)); }

    private TimeZone mCachedTimeZone;
    private String mCachedTimeZoneId;

    public synchronized TimeZone getTimeZone() {
        if (mCachedTimeZone == null || (mCachedTimeZoneId != null && !mCachedTimeZoneId.equals(getTimeZoneId()))) {
            mCachedTimeZoneId = getTimeZoneId();
            mCachedTimeZone  = (mCachedTimeZoneId == null) ? null :
                    TimeZone.getTimeZone(TZIDMapper.toJava(mCachedTimeZoneId));
            if (mCachedTimeZone == null)
                mCachedTimeZone = TimeZone.getDefault();
        }
        return mCachedTimeZone;
    }

    public String getReplyIncludeOriginalText() { return get(Provisioning.A_zimbraPrefReplyIncludeOriginalText); }

    public boolean getReplyIncludeAsAttachment() { return "includeAsAttachment".equals(getReplyIncludeOriginalText()); }
    public boolean getReplyIncludeBody() { return "includeBody".equals(getReplyIncludeOriginalText()); }
    public boolean getReplyIncludeBodyWithPrefx() { return "includeBodyWithPrefix".equals(getReplyIncludeOriginalText()); }
    public boolean getReplyIncludeNone() { return "includeNone".equals(getReplyIncludeOriginalText()); }
    public boolean getReplyIncludeSmart() { return "includeSmart".equals(getReplyIncludeOriginalText()); }
    
    public String getForwardIncludeOriginalText() { return get(Provisioning.A_zimbraPrefForwardIncludeOriginalText); }
    public boolean getForwardIncludeAsAttachment() { return "includeAsAttachment".equals(getForwardIncludeOriginalText()); }
    public boolean getForwardIncludeBody() { return "includeBody".equals(getForwardIncludeOriginalText()); }
    public boolean getForwardIncludeBodyWithPrefx() { return "includeBodyWithPrefix".equals(getForwardIncludeOriginalText()); }
    
    public String getForwardReplyFormat() { return get(Provisioning.A_zimbraPrefForwardReplyFormat); }
    public boolean getForwardReplyTextFormat() { return "text".equals(getForwardReplyFormat()); }
    public boolean getForwardReplyHtmlFormat() { return "html".equals(getForwardReplyFormat()); }
    public boolean getForwardReplySameFormat() { return "same".equals(getForwardReplyFormat()); }

    public String getForwardReplyPrefixChar() { return get(Provisioning.A_zimbraPrefForwardReplyPrefixChar); }
    
}
