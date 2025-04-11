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

import com.zimbra.common.calendar.TZIDMapper;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.zclient.ZDateTime;
import com.zimbra.cs.zclient.ZEmailAddress;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZIdentity;
import com.zimbra.cs.zclient.ZInvite;
import com.zimbra.cs.zclient.ZInvite.ZAttendee;
import com.zimbra.cs.zclient.ZInvite.ZByDayWeekDay;
import com.zimbra.cs.zclient.ZInvite.ZComponent;
import com.zimbra.cs.zclient.ZInvite.ZFreeBusyStatus;
import com.zimbra.cs.zclient.ZInvite.ZOrganizer;
import com.zimbra.cs.zclient.ZInvite.ZParticipantStatus;
import com.zimbra.cs.zclient.ZInvite.ZRole;
import com.zimbra.cs.zclient.ZInvite.ZStatus;
import com.zimbra.cs.zclient.ZInvite.ZTransparency;
import com.zimbra.cs.zclient.ZInvite.ZWeekDay;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMailbox.ReplyVerb;
import com.zimbra.cs.zclient.ZMailbox.ZOutgoingMessage;
import com.zimbra.cs.zclient.ZMailbox.ZOutgoingMessage.AttachedMessagePart;
import com.zimbra.cs.zclient.ZMailbox.ZOutgoingMessage.MessagePart;
import com.zimbra.cs.zclient.ZSimpleRecurrence;
import com.zimbra.cs.zclient.ZSimpleRecurrence.ZSimpleRecurrenceEnd;
import com.zimbra.cs.zclient.ZSimpleRecurrence.ZSimpleRecurrenceType;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.PartSource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.jstl.fmt.LocaleSupport;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class ZMessageComposeBean {

    public static final String NOTES_SEPARATOR = "*~*~*~*~*~*~*~*~*~*";

    public static class MessageAttachment {
        private String mId;
        private String mSubject;

        public MessageAttachment(String id, String subject) {
            mId = id;
            mSubject = subject;
        }

        public String getId() { return mId; }
        public String getSubject() { return mSubject; }
    }
    
    public static String RT_BASIC = "BASIC";
    
    public static String CRLF = "\r\n";

    public enum Action { NEW, REPLY, REPLY_ALL, FORWARD, RESEND, DRAFT, APPT_NEW, APPT_EDIT, APPT_CANCEL, INVITE_ACCEPT, INVITE_DECLINE, INVITE_TENTATIVE }

    private String mAttendees;
    private String mApptFolderId;
    private String mLocation;
    private String mTimeZone;
    private String mFreeBusyStatus;
    private boolean mAllDay;
    private String mStartDate;
    private long mStartHour;
    private long mStartMinute;
    private String mEndDate;
    private long mEndHour;
    private long mEndMinute;
    private String mInviteReplyVerb;
    private long mInviteReplyInst;
    private boolean mInviteReplyAllDay;

    private String mSendUID;

    private String mInviteId;
    private String mExceptionInviteId;
    private boolean mUseInstance;
    private long mInstanceStartTime;
    private long mInstanceDuration;
    private String mCompNum;
    private String mInstanceCompNum;

    private String mRepeatBasicType;
    private String mRepeatType;
    private int mRepeatDailyInterval;
    private int mRepeatWeeklyByDay;
    private int mRepeatWeeklyInterval;
    private boolean mRepeatWeeklySun;
    private boolean mRepeatWeeklyMon;
    private boolean mRepeatWeeklyTue;
    private boolean mRepeatWeeklyWed;
    private boolean mRepeatWeeklyThu;
    private boolean mRepeatWeeklyFri;
    private boolean mRepeatWeeklySat;
    private int mRepeatMonthlyInterval;
    private int mRepeatMonthlyMonthDay;
    private int mRepeatMonthlyRelativeInterval;
    private int mRepeatMonthlyRelativeOrd;
    private int mRepeatMonthlyRelativeDay;
    private int mRepeatYearlyMonthDay;
    private int mRepeatYearlyMonth;
    private int mRepeatYearlyRelativeOrd;
    private int mRepeatYearlyRelativeDay;
    private int mRepeatYearlyRelativeMonth;
    private String mRepeatEndType;
    private int mRepeatEndCount;
    private String mRepeatEndDate;

    // format to parse start/endDate
    private String mDateFormat;
    private String mTo;
    private String mCc;
    private String mBcc;
    private String mFrom;
    private String mReplyTo;
    private String mReplyType;
    private String mSubject;
    private String mContentType = "text/plain";
    private String mContent;
    private String mHtmlContent;
    private String mMessageId; // zimbra internal message id of message for reply/forward
    private String mInReplyTo; // original message-id header
    private String mDraftId; // id of draft we are editting
    private List<MessageAttachment> mMessageAttachments;
    private Map<String,Boolean> mCheckedAttachmentNames = new HashMap<String, Boolean>();
    private List<ZMimePartBean> mOriginalAttachments;
    private List<FileItem> mFileItems = new ArrayList<FileItem>();

    public ZMessageComposeBean(PageContext pageContext) {
        mMessageAttachments = new ArrayList<MessageAttachment>();
        mOriginalAttachments = new ArrayList<ZMimePartBean>();
        mDateFormat = LocaleSupport.getLocalizedMessage(pageContext, "CAL_APPT_EDIT_DATE_FORMAT");
    }

    public void setInviteReplyVerb(String verb) { mInviteReplyVerb = verb; }
    public String getInviteReplyVerb() { return mInviteReplyVerb; }

    public void setInviteReplyInst(long inst) { mInviteReplyInst = inst; }
    public long getInviteReplyInst() { return mInviteReplyInst; }

    public void setInviteReplyAllDay(boolean allDay) { mInviteReplyAllDay = allDay; }
    public boolean getInviteReplyAllDay() { return mInviteReplyAllDay; }

    public void setTo(String to) { mTo = to; }
    public String getTo() { return mTo; }

    public void setContent(String content) { mContent = content; }
    public String getContent() { return mContent; }

    public void setHtmlContent(String htmlContent) { mHtmlContent = htmlContent; }
    public String getHtmlContent() { return mHtmlContent; }

    public void setContenttype(String contentType) { mContentType = contentType; }
    public String getContentType() { return mContentType; }

    public void setReplyType(String replyType) { mReplyType = replyType; }
    public String getReplyType() { return mReplyType; }

    public void setSubject(String subject) { mSubject = subject; }
    public String getSubject() { return mSubject; }

    public void setInReplyTo(String inReplyTo) { mInReplyTo = inReplyTo; }
    public String getInReplyTo() { return mInReplyTo; }

    public void setFrom(String from) { mFrom = from; }
    public String getFrom() { return mFrom; }

    public void setBcc(String bcc) { mBcc = bcc; }
    public String getBcc() { return mBcc; }

    public void setCc(String cc) { mCc = cc; }
    public String getCc() { return mCc; }

    public void setApptFolderId(String id) { mApptFolderId = id; }
    public String getApptFolderId() { return mApptFolderId; }

    public void setAttendees(String attendees) { mAttendees = attendees; }
    public String getAttendees() { return mAttendees; }

    public void setLocation(String location) { mLocation = location; }
    public String getLocation() { return mLocation; }

    public void setTimeZone(String timeZone) { mTimeZone = timeZone; }
    public String getTimeZone() { return mTimeZone; }

    public void setFreeBusyStatus(String freeBusyStatus) { mFreeBusyStatus = freeBusyStatus; }
    public String getFreeBusyStatus() { return mFreeBusyStatus; }

    public void setAllDay(boolean allDay) { mAllDay = allDay; }
    public boolean getAllDay() { return mAllDay; }

    public void setStartDate(String startDate) { mStartDate = startDate;}
    public String getStartDate() { return mStartDate; }

    public void setStartHour(long startHour) { mStartHour = startHour; }
    public long getStartHour() { return mStartHour; }

    public void setStartMinute(long startMinute) { mStartMinute = startMinute; }
    public long getStartMinute() { return mStartMinute; }

    public void setEndDate(String endDate) { mEndDate = endDate; }
    public String getEndDate() { return mEndDate; }

    public void setEndHour(long endHour) { mEndHour = endHour; }
    public long getEndHour() { return mEndHour; }

    public void setEndMinute(long endMinute) { mEndMinute = endMinute; }
    public long getEndMinute() { return mEndMinute; }

    public void setDateFormat(String dateFormat) { mDateFormat = dateFormat; }
    public String getDateFormat() { return mDateFormat; }

    public void setInviteId(String inviteId) { mInviteId = inviteId; }
    public String getInviteId() { return mInviteId; }

    public void setCompNum(String compNum) { mCompNum = compNum; }
    public String getCompNum() { return mCompNum; }

    public void setInstanceCompNum(String instCompNum) { mInstanceCompNum = instCompNum; }
    public String getInstanceCompNum() { return mInstanceCompNum; }
    
    public void setExceptionInviteId(String exceptionInviteId) { mExceptionInviteId = exceptionInviteId; }
    public String getExceptionInviteId() { return mExceptionInviteId; }

    public void setUseInstance(boolean useInstance) { mUseInstance = useInstance; }
    public boolean getUseInstance() { return mUseInstance; }

    public void setInstanceStartTime(long startTime) { mInstanceStartTime = startTime; }
    public long getInstanceStartTime() { return mInstanceStartTime; }

    public void setInstanceDuration(long duration) { mInstanceDuration = duration; }
    public long getInstanceDuration() { return mInstanceDuration; }

    public void setReplyTo(String replyTo) { mReplyTo = replyTo; }
    public String getReplyTo() { return mReplyTo; }

    public void setMessageId(String id) { mMessageId = id; }
    public String getMessageId() { return mMessageId; }

    public void setDraftId(String id) { mDraftId = id; }
    public String getDraftId() { return mDraftId; }

    public Map<String,Boolean> getCheckedAttachmentNames() { return mCheckedAttachmentNames; }
    public void setCheckedAttachmentName(String name) { mCheckedAttachmentNames.put(name,  true); }

    public List<FileItem> getFileItems() { return mFileItems; }
    public void addFileItem(FileItem item) { mFileItems.add(item); }
    public boolean getHasFileItems() { return !mFileItems.isEmpty(); }

    public void setOrignalAttachments(List<ZMimePartBean> attachments) { mOriginalAttachments = attachments; }
    public List<ZMimePartBean> getOriginalAttachments() { return mOriginalAttachments; }

    public void setMessageAttachments(List<MessageAttachment> attachments) { mMessageAttachments = attachments; }
    public List<MessageAttachment> getMessageAttachments() { return mMessageAttachments; }

    public String getRepeatBasicType() { return mRepeatBasicType; }
    public void setRepeatBasicType(String repeatBasicType) { mRepeatBasicType = repeatBasicType; }

    public String getRepeatType() { return mRepeatType;}
    public void setRepeatType(String repeatType) { mRepeatType = repeatType; }

    public int getRepeatDailyInterval() { return mRepeatDailyInterval; }
    public void setRepeatDailyInterval(int repeatDailyInterval) { mRepeatDailyInterval = repeatDailyInterval; }

    public int getRepeatWeeklyByDay() { return mRepeatWeeklyByDay; }
    public void setRepeatWeeklyByDay(int repeatWeeklyByDay) { mRepeatWeeklyByDay = repeatWeeklyByDay; }

    public int getRepeatWeeklyInterval() { return mRepeatWeeklyInterval; }
    public void setRepeatWeeklyInterval(int repeatWeeklyInterval) { mRepeatWeeklyInterval = repeatWeeklyInterval; }

    public boolean isRepeatWeeklySun() { return mRepeatWeeklySun; }
    public void setRepeatWeeklySun(boolean repeatWeeklySun) { mRepeatWeeklySun = repeatWeeklySun; }

    public boolean isRepeatWeeklyMon() { return mRepeatWeeklyMon; }
    public void setRepeatWeeklyMon(boolean repeatWeeklyMon) { mRepeatWeeklyMon = repeatWeeklyMon; }

    public boolean isRepeatWeeklyTue() { return mRepeatWeeklyTue; }
    public void setRepeatWeeklyTue(boolean repeatWeeklyTue) { mRepeatWeeklyTue = repeatWeeklyTue; }

    public boolean isRepeatWeeklyWed() { return mRepeatWeeklyWed; }
    public void setRepeatWeeklyWed(boolean repeatWeeklyWed) { mRepeatWeeklyWed = repeatWeeklyWed; }

    public boolean isRepeatWeeklyThu() { return mRepeatWeeklyThu; }
    public void setRepeatWeeklyThu(boolean repeatWeeklyThu) { mRepeatWeeklyThu = repeatWeeklyThu; }

    public boolean isRepeatWeeklyFri() { return mRepeatWeeklyFri; }
    public void setRepeatWeeklyFri(boolean repeatWeeklyFri) { mRepeatWeeklyFri = repeatWeeklyFri; }

    public boolean isRepeatWeeklySat() { return mRepeatWeeklySat; }
    public void setRepeatWeeklySat(boolean repeatWeeklySat) { mRepeatWeeklySat = repeatWeeklySat; }

    public int getRepeatMonthlyInterval() { return mRepeatMonthlyInterval; }
    public void setRepeatMonthlyInterval(int repeatMonthlyInterval) { mRepeatMonthlyInterval = repeatMonthlyInterval; }

    public int getRepeatMonthlyMonthDay() { return mRepeatMonthlyMonthDay; }
    public void setRepeatMonthlyMonthDay(int repeatMonthlyMonthDay) { mRepeatMonthlyMonthDay = repeatMonthlyMonthDay; }

    public int getRepeatMonthlyRelativeInterval() { return mRepeatMonthlyRelativeInterval; }
    public void setRepeatMonthlyRelativeInterval(int repeatMonthlyRelativeInterval) { mRepeatMonthlyRelativeInterval = repeatMonthlyRelativeInterval; }

    public int getRepeatMonthlyRelativeOrd() { return mRepeatMonthlyRelativeOrd; }
    public void setRepeatMonthlyRelativeOrd(int repeatMonthlyRelativeOrd) { mRepeatMonthlyRelativeOrd = repeatMonthlyRelativeOrd; }

    public int getRepeatMonthlyRelativeDay() { return mRepeatMonthlyRelativeDay; }
    public void setRepeatMonthlyRelativeDay(int repeatMonthlyRelativeDay) { mRepeatMonthlyRelativeDay = repeatMonthlyRelativeDay; }

    public int getRepeatYearlyMonthDay() { return mRepeatYearlyMonthDay; }
    public void setRepeatYearlyMonthDay(int repeatYearlyMonthDay) { mRepeatYearlyMonthDay = repeatYearlyMonthDay; }

    public int getRepeatYearlyMonth() { return mRepeatYearlyMonth; }
    public void setRepeatYearlyMonth(int repeatYearlyMonth) { mRepeatYearlyMonth = repeatYearlyMonth; }

    public int getRepeatYearlyRelativeOrd() { return mRepeatYearlyRelativeOrd; }
    public void setRepeatYearlyRelativeOrd(int repeatYearlyRelativeOrd) { mRepeatYearlyRelativeOrd = repeatYearlyRelativeOrd; }

    public int getRepeatYearlyRelativeDay() { return mRepeatYearlyRelativeDay; }
    public void setRepeatYearlyRelativeDay(int repeatYearlyRelativeDay) { mRepeatYearlyRelativeDay = repeatYearlyRelativeDay; }

    public int getRepeatYearlyRelativeMonth() { return mRepeatYearlyRelativeMonth; }
    public void setRepeatYearlyRelativeMonth(int repeatYearlyRelativeMonth) { mRepeatYearlyRelativeMonth = repeatYearlyRelativeMonth; }

    public String getRepeatEndType() { return mRepeatEndType; }
    public void setRepeatEndType(String repeatEndType) { mRepeatEndType = repeatEndType; }

    public int getRepeatEndCount() { return mRepeatEndCount; }
    public void setRepeatEndCount(int repeatEndCount) { mRepeatEndCount = repeatEndCount; }

    public String getRepeatEndDate() { return mRepeatEndDate; }
    public void setRepeatEndDate(String repeatEndDate) { mRepeatEndDate = repeatEndDate; }

    public String getSendUID() { return mSendUID; }
    public void setSendUID(String uid) { mSendUID = uid; }

    public String paramInit(HttpServletRequest req, String name, String defaultValue) {
        String value = req.getParameter(name);
        return (value == null || value.length()==0) ? defaultValue : value;
    }

    public static class AppointmentOptions {

        private Calendar mDate;
        private String mInviteId;
        private String mExceptionInviteId;
        private boolean mUseInstance;
        private long mInstanceStartTime;
        private long mInstanceDuration;

        public Calendar getDate() {
            return mDate;
        }

        public void setDate(Calendar date) {
            mDate = date;
        }

        public String getInviteId() {
            return mInviteId;
        }

        public void setInviteId(String inviteId) {
            mInviteId = inviteId;
        }

        public String getExceptionInviteId() {
            return mExceptionInviteId;
        }

        public void setExceptionInviteId(String exceptionInviteId) {
            mExceptionInviteId = exceptionInviteId;
        }

        public boolean isUseInstance() {
            return mUseInstance;
        }

        public void setUseInstance(boolean useInstance) {
            mUseInstance = useInstance;
        }

        public long getInstanceStartTime() {
            return mInstanceStartTime;
        }

        public void setInstanceStartTime(long instanceStartTime) {
            mInstanceStartTime = instanceStartTime;
        }

        public long getInstanceDuration() {
            return mInstanceDuration;
        }

        public void setInstanceDuration(long instanceDuration) {
            mInstanceDuration = instanceDuration;
        }


    }

    /**
     * construct a message compose bean based on action and state.
     * @param action what type of compose we are doing, must not be null.
     * @param msg Message for reply/replyAll/forward
     * @param mailbox mailbox object
     * @param pc the JSP PageContext for localization information
     * @throws com.zimbra.common.service.ServiceException on error
     * @param options appointment options
     */
    public ZMessageComposeBean(Action action, ZMessageBean msg, ZMailbox mailbox, PageContext pc,
                               AppointmentOptions options) throws ServiceException {
        HttpServletRequest req = (HttpServletRequest) pc.getRequest();

        setDateFormat(LocaleSupport.getLocalizedMessage(pc, "CAL_APPT_EDIT_DATE_FORMAT"));

        Set<String> emailAddresses = mailbox.getAccountInfo(false).getEmailAddresses();
        List<ZIdentity> identities = mailbox.getAccountInfo(false).getIdentities();

        mSendUID = UUID.randomUUID().toString();
        
        if (msg != null) {
            setMessageId(msg.getId());
        }

        // compute identity
        ZIdentity identity = action == Action.NEW ?
                defaultIdentity(identities) :
                computeIdentity(msg, identities);

        switch (action) {
            case INVITE_ACCEPT:
            case INVITE_DECLINE:
            case INVITE_TENTATIVE:
            case REPLY:
            case REPLY_ALL:
                if (msg == null) break;
                setSubject(getReplySubject(msg.getSubject(), pc)); // Subject:
                List<ZEmailAddress> toAddressList = new ArrayList<ZEmailAddress>();
                Set<String> toAddressSet = new HashSet<String>();
                setTo(getToAddress(msg.getEmailAddresses(), toAddressList, toAddressSet, emailAddresses)); // To:
                if (action == Action.REPLY_ALL) {
                    setCc(getCcAddress(msg.getEmailAddresses(), toAddressSet, emailAddresses));   // Cc:
                    if (mTo == null || mTo.length() == 0) {
                        mTo = mCc;
                        mCc= null;
                    }

                }
                setInReplyTo(msg.getMessageIdHeader()); // original message-id header
                setReplyType("r");
                break;
            case FORWARD:
                if (msg == null) break;
                setSubject(getForwardSubject(msg.getSubject(), pc)); // Subject:
                setReplyType("w");
                break;
            case RESEND:
                if (msg == null) break;
                setSubject(msg.getSubject());
                setTo(msg.getDisplayTo());
                setCc(msg.getDisplayCc());
                addAttachments(msg, true);
                break;
            case DRAFT:
                if (msg == null) break;
                setSubject(msg.getSubject());
                setTo(msg.getDisplayTo());
                setCc(msg.getDisplayCc());
                setBcc(msg.getDisplayBcc());
                addAttachments(msg, true);
                if (msg.getInReplyTo() != null)
                    setInReplyTo(msg.getInReplyTo());
                if (msg.getReplyType() != null)
                    setReplyType(msg.getReplyType());
                break;
            case APPT_NEW:
                doNewAppt(mailbox, pc, options);
                if (req.getParameter("body") != null)
                    setContent(req.getParameter("body"));
                // RETURN!
                return;
            case APPT_EDIT:
                doEditAppt(msg, mailbox, pc, options);
                addAttachments(msg, true);
                if (msg != null) {
                    ZMimePartBean body = msg.getBody();
                    if (body != null) {
                        String bodyContent = body.getContent();
                        int i = bodyContent.lastIndexOf(NOTES_SEPARATOR);
                        if (i > 0) {
                            i += NOTES_SEPARATOR.length();
                            int n = 0;
                            while (n < 4 && i < bodyContent.length() && (bodyContent.charAt(i) == '\r' || bodyContent.charAt(i) == '\n')) {
                                i++;
                                n++;
                            }
                            bodyContent = bodyContent.substring(i);
                        }
                        setContent(bodyContent);
                    }
                }
                // RETURN!
                return;
            case NEW:
                setSubject(req.getParameter("subject"));
                setTo(req.getParameter("to"));
                setCc(req.getParameter("cc"));
                setBcc(req.getParameter("bcc"));
                break;
            default:
                break;
        }

        // setup body for invite reply
        if (action == Action.INVITE_ACCEPT || action == Action.INVITE_DECLINE || action == Action.INVITE_TENTATIVE) {
            switch (action) {
                case INVITE_ACCEPT:
                    setInviteReplyVerb(ReplyVerb.ACCEPT.name());
                    setContent(LocaleSupport.getLocalizedMessage(pc, "defaultInviteReplyAcceptMessage"));
                    break;
                case INVITE_DECLINE:
                    setInviteReplyVerb(ReplyVerb.DECLINE.name());
                    setContent(LocaleSupport.getLocalizedMessage(pc, "defaultInviteReplyDeclineMessage"));
                    break;
                case INVITE_TENTATIVE:
                    setInviteReplyVerb(ReplyVerb.TENTATIVE.name());
                    setContent(LocaleSupport.getLocalizedMessage(pc, "defaultInviteReplyTentativeMessage"));
                    break;
            }
            setInviteReplyInst(getParamLong(req.getParameter("inviteReplyInst"), 0));
            setInviteReplyAllDay("1".equals(req.getParameter("inviteReplyAllDay")));
            setCompNum(req.getParameter("compNum"));
            setInstanceCompNum(req.getParameter("instCompNum"));
        }

        if (identity == null)
            return;

        // Reply-to:
        if (identity.getReplyToEnabled()) {
            setReplyTo(identity.getReplyToEmailAddress().getFullAddress());
        }

        // from
        setFrom(identity.getFromEmailAddress().getFullAddress());

        if (action == Action.RESEND || action == Action.DRAFT) {
            if (msg != null)
                setContent(msg.getBody().getContent());
            return;
        }

        // signature
        String signature = identity.getSignatureEnabled() ? identity.getSignature() : null;
        boolean signatureTop = identity.getSignatureStyleTop();

        // see if we need to use default identity for the rest
        ZIdentity includeIdentity = (!identity.isDefault() && identity.getUseDefaultIdentitySettings()) ?
                defaultIdentity(identities) :
                identity;

        StringBuilder content = new StringBuilder();

        if (getContent() != null) {
            content.append(getContent());
        }

        if (signatureTop && signature != null && signature.length() > 0)
            content.append("\n\n\n").append(signature);

        if (action == Action.REPLY || action == Action.REPLY_ALL ||
                action == Action.INVITE_ACCEPT || action == Action.INVITE_DECLINE || action == Action.INVITE_TENTATIVE)
            replyInclude(msg, content, includeIdentity, pc);
        else if (action == Action.FORWARD)
            forwardInclude(msg, content, includeIdentity, pc);
        else if (action == Action.NEW && req.getParameter("body") != null)
            content.append(req.getParameter("body"));

        if (!signatureTop && signature != null && signature.length() > 0) {
            if (content.length() == 0)
                content.append("\n\n\n");
            content.append(signature);
        }

        setContent(content.toString());
    }

    @SuppressWarnings({"EmptyCatchBlock"})
    public long getParamLong(String value, long defaultValue) {
        if (value != null)
            try { return Long.parseLong(value); } catch (NumberFormatException e) {}
        return defaultValue;
    }

    public ZSimpleRecurrence getSimpleRecurrence() throws ServiceException {
        ZSimpleRecurrence repeat = new ZSimpleRecurrence(null);

        if (getRepeatType() == null || getRepeatType().length() == 0)
            return repeat;
        
        boolean basic = RT_BASIC.equals(getRepeatType());

        ZSimpleRecurrenceType type =
                ZSimpleRecurrenceType.fromString(basic ? getRepeatBasicType() : getRepeatType());
        repeat.setType(type);

        if (basic) {
            repeat.setEnd(ZSimpleRecurrenceEnd.NEVER);
            return repeat;
        }

        repeat.setEnd(ZSimpleRecurrenceEnd.fromString(getRepeatEndType()));

        switch(repeat.getEnd()) {
            case NEVER:
                break;
            case COUNT:
                repeat.setCount(getRepeatEndCount());
                break;
            case UNTIL:
                repeat.setUntilDate(new ZDateTime(getRepeatEndDateTime(), mTimeZone));
                break;
        }

        switch(type) {
            case COMPLEX:
                break;
            case NONE:
                break;
            case DAILY:
                break;
            case DAILY_INTERVAL:
                repeat.setDailyInterval(getRepeatDailyInterval());
                break;
            case DAILY_WEEKDAY:
                break;
            case WEEKLY:
                break;
            case WEEKLY_BY_DAY:
                repeat.setWeeklyByDay(ZWeekDay.fromOrdinal(getRepeatWeeklyByDay()));
                break;
            case WEEKLY_CUSTOM:
                repeat.setWeeklyInterval(getRepeatWeeklyInterval());
                List<ZWeekDay> weeklyDays = new ArrayList<ZWeekDay>();
                if (isRepeatWeeklySun()) weeklyDays.add(ZWeekDay.SU);
                if (isRepeatWeeklyMon()) weeklyDays.add(ZWeekDay.MO);
                if (isRepeatWeeklyTue()) weeklyDays.add(ZWeekDay.TU);
                if (isRepeatWeeklyWed()) weeklyDays.add(ZWeekDay.WE);
                if (isRepeatWeeklyThu()) weeklyDays.add(ZWeekDay.TH);
                if (isRepeatWeeklyFri()) weeklyDays.add(ZWeekDay.FR);
                if (isRepeatWeeklySat()) weeklyDays.add(ZWeekDay.SA);
                repeat.setWeeklyIntervalDays(weeklyDays);
                break;
            case MONTHLY:
                break;
            case MONTHLY_BY_MONTH_DAY:
                repeat.setMonthlyInterval(getRepeatMonthlyInterval());
                repeat.setMonthlyMonthDay(getRepeatMonthlyMonthDay());
                break;
            case MONTHLY_RELATIVE:
                repeat.setMonthlyInterval(getRepeatMonthlyInterval());
                ZByDayWeekDay mday = new ZByDayWeekDay();
                mday.setDay(ZWeekDay.fromOrdinal(getRepeatWeeklyByDay()));
                mday.setWeekOrd(getRepeatMonthlyRelativeOrd());
                repeat.setMonthlyRelativeDay(mday);
                break;
            case YEARLY:
                break;
            case YEARLY_BY_DATE:
                repeat.setYearlyByDateMonth(getRepeatYearlyMonth());
                repeat.setYearlyByDateMonthDay(getRepeatYearlyMonthDay());
                break;
            case YEARLY_RELATIVE:
                repeat.setYearlyRelativeMonth(getRepeatYearlyRelativeMonth());
                ZByDayWeekDay yday = new ZByDayWeekDay();
                yday.setDay(ZWeekDay.fromOrdinal(getRepeatYearlyRelativeDay()));
                yday.setWeekOrd(getRepeatYearlyRelativeOrd());
                repeat.setYearlyRelativeDay(yday);
                break;
        }
        return repeat;
    }

    public void initRepeat(ZSimpleRecurrence repeat, Date date, PageContext pc, ZMailbox mailbox) throws ServiceException {

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        setRepeatDailyInterval(repeat.getDailyInterval() > 0 ? repeat.getDailyInterval() :  1);

        setRepeatWeeklyByDay(repeat.getWeeklyByDay() != null ? repeat.getWeeklyByDay().getOrdinal() : cal.get(Calendar.DAY_OF_WEEK)-1);

        setRepeatWeeklyInterval(repeat.getWeeklyInterval() > 0 ? repeat.getWeeklyInterval() : 1);

        if (repeat.getWeeklyIntervalDays() != null) {
            for (ZWeekDay day : repeat.getWeeklyIntervalDays()) {
                switch(day) {
                    case SU: setRepeatWeeklySun(true); break;
                    case MO: setRepeatWeeklyMon(true); break;
                    case TU: setRepeatWeeklyTue(true); break;
                    case WE: setRepeatWeeklyWed(true); break;
                    case TH: setRepeatWeeklyThu(true); break;
                    case FR: setRepeatWeeklyFri(true); break;
                    case SA: setRepeatWeeklySat(true); break;
                }
            }
        }

        setRepeatMonthlyMonthDay(repeat.getMonthlyMonthDay() > 0 ? repeat.getMonthlyMonthDay() : cal.get(Calendar.DAY_OF_MONTH));
        setRepeatMonthlyInterval(repeat.getMonthlyInterval() > 0 ? repeat.getMonthlyInterval() : 1);

        int dom = cal.get(Calendar.DAY_OF_MONTH);

        int dayOrd = ((dom-1)/7) + 1;
        if (dayOrd > 4) dayOrd = -1;

        ZByDayWeekDay day = repeat.getMonthlyRelativeDay();
        setRepeatMonthlyRelativeOrd(day == null ? dayOrd : day.getWeekOrd());
        setRepeatMonthlyRelativeDay(day == null ? cal.get(Calendar.DAY_OF_WEEK)-1 : day.getDay().getOrdinal());
        setRepeatMonthlyRelativeInterval(repeat.getMonthlyInterval() > 0 ? repeat.getMonthlyInterval() : 1);

        setRepeatYearlyMonth(repeat.getYearlyByDateMonth() != 0 ? repeat.getYearlyByDateMonth() : cal.get(Calendar.MONTH)+1);
        setRepeatYearlyMonthDay(repeat.getYearlyByDateMonthDay() != 0 ? repeat.getYearlyByDateMonthDay() :  cal.get(Calendar.DAY_OF_MONTH));

        ZByDayWeekDay relDay = repeat.getYearlyRelativeDay();
        setRepeatYearlyRelativeOrd(relDay == null ? dayOrd : relDay.getWeekOrd());
        setRepeatYearlyRelativeDay(relDay == null ? cal.get(Calendar.DAY_OF_WEEK)-1 : relDay.getDay().getOrdinal());
        setRepeatYearlyRelativeMonth(repeat.getYearlyRelativeMonth() > 0 ? repeat.getYearlyRelativeMonth() :  cal.get(Calendar.MONTH)+1);

        setRepeatEndCount((int)(repeat.getCount() > 0 ? repeat.getCount() : 1));
        
        Date endDate = repeat.getUntilDate() != null ? repeat.getUntilDate().getDate() : date;
        DateFormat df = new SimpleDateFormat(LocaleSupport.getLocalizedMessage(pc, "CAL_APPT_EDIT_DATE_FORMAT"));
        df.setTimeZone(mailbox.getPrefs().getTimeZone());
        setRepeatEndDate(df.format(endDate));
        setRepeatEndType(repeat.getEnd().name());

        setRepeatType(repeat.getType().name());
        setRepeatBasicType(ZSimpleRecurrenceType.DAILY.name());

        if (repeat.getType().isNone()) return;

        ZSimpleRecurrenceType type = repeat.getType();

        if ((type.isDaily() && repeat.getEnd() == ZSimpleRecurrenceEnd.NEVER) ||
                type.isWeekly() || type.isMonthly() || type.isYearly()) {
            setRepeatType(RT_BASIC);
            setRepeatBasicType(type.name());
        } else {
            setRepeatType(type.name());
        }

    }

    private void doNewAppt(ZMailbox mailbox, PageContext pc, AppointmentOptions options) throws ServiceException {
        HttpServletRequest req = (HttpServletRequest) pc.getRequest();
        
        setSubject(req.getParameter(ZComposeUploaderBean.F_subject));
        setLocation(req.getParameter(ZComposeUploaderBean.F_location));
        setAllDay("1".equals(req.getParameter(ZComposeUploaderBean.F_allDay)));
        setAttendees(req.getParameter(ZComposeUploaderBean.F_attendees));
        setFreeBusyStatus(paramInit(req, ZComposeUploaderBean.F_freeBusyStatus, ZInvite.ZFreeBusyStatus.B.name()));
        setTimeZone(paramInit(req, ZComposeUploaderBean.F_timeZone, mailbox.getPrefs().getTimeZoneWindowsId()));
        setApptFolderId(ZFolder.ID_CALENDAR);
        Calendar calendar = options.getDate() != null ? options.getDate() : BeanUtils.getCalendar(System.currentTimeMillis(), mailbox.getPrefs().getTimeZone());
        if (options.getDate() != null) {
            Calendar now = BeanUtils.getCalendar(System.currentTimeMillis(), mailbox.getPrefs().getTimeZone());
            // start hour to current hour instead of 12:00 AM
            calendar.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        }
        DateFormat df = new SimpleDateFormat(LocaleSupport.getLocalizedMessage(pc, "CAL_APPT_EDIT_DATE_FORMAT"));
        df.setTimeZone(mailbox.getPrefs().getTimeZone());
        String dateStr = df.format(calendar.getTime());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        setStartDate(paramInit(req, ZComposeUploaderBean.F_startDate, dateStr));
        setStartHour(Long.parseLong(paramInit(req, ZComposeUploaderBean.F_startHour, Integer.toString(hour))));
        setStartMinute(Long.parseLong(paramInit(req, ZComposeUploaderBean.F_startMinute, "0")));

        // add one hour to current time
        calendar.add(Calendar.HOUR_OF_DAY, 1);

        dateStr = df.format(calendar.getTime());
        hour = calendar.get(Calendar.HOUR_OF_DAY);

        setEndDate(paramInit(req, ZComposeUploaderBean.F_endDate, dateStr));
        setEndHour(Long.parseLong(paramInit(req, ZComposeUploaderBean.F_endHour, Integer.toString(hour))));
        setEndMinute(Long.parseLong(paramInit(req, ZComposeUploaderBean.F_endMinute, "0")));

        initRepeat(new ZSimpleRecurrence(null), calendar.getTime(), pc, mailbox);
    }

    private void doEditAppt(ZMessageBean msg, ZMailbox mailbox, PageContext pc, AppointmentOptions options) throws ServiceException {
        HttpServletRequest req = (HttpServletRequest) pc.getRequest();
        ZComponent appt = msg.getInvite().getComponent();

        setUseInstance(options.isUseInstance());
        setInviteId(options.getInviteId());
        setExceptionInviteId(options.getExceptionInviteId());
        setInstanceDuration(options.getInstanceDuration());
        setInstanceStartTime(options.getInstanceStartTime());
        setApptFolderId(msg.getFolderId());

        setSubject(appt.getName());
        setLocation(appt.getLocation());

        setAllDay(appt.isAllDay());

        if (!appt.getAttendees().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ZAttendee attendee : appt.getAttendees()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(attendee.getEmailAddress().getFullAddress());
            }
            setAttendees(sb.toString());
        }

        setFreeBusyStatus(appt.getFreeBusyStatus().name());
        String tz = appt.getStart().getTimeZoneId();
        setTimeZone(appt.isAllDay() ? mailbox.getPrefs().getTimeZoneId() : tz == null ? tz : TZIDMapper.canonicalize(tz)); //paramInit(req, ZComposeUploaderBean.F_timeZone, mailbox.getPrefs().getTimeZoneWindowsId()));

        TimeZone apptTz = TimeZone.getTimeZone((TZIDMapper.toJava(getTimeZone())));

        if (appt.isAllDay()) {
            ZDateTime st = appt.getStart();
            if (st != null && st.getHasNoTimeZone()) st.setTimeZoneId(apptTz.getID());
            ZDateTime et = appt.getEnd();
            if (et != null && et.getHasNoTimeZone()) et.setTimeZoneId(apptTz.getID());
        }

        Date startDate = getUseInstance() ? new Date(getInstanceStartTime()) : appt.getStart().getDate();

        Calendar startCalendar = Calendar.getInstance(apptTz);
        startCalendar.setTime(startDate);

        DateFormat df = new SimpleDateFormat(LocaleSupport.getLocalizedMessage(pc, "CAL_APPT_EDIT_DATE_FORMAT"));
        df.setTimeZone(apptTz);

        setStartDate(paramInit(req, ZComposeUploaderBean.F_startDate, df.format(startDate)));

        if (appt.isAllDay()) {
            setStartHour(0);
            setStartMinute(0);
        } else {
            setStartHour(startCalendar.get(Calendar.HOUR_OF_DAY));
            setStartMinute(startCalendar.get(Calendar.MINUTE));
        }

        Date endDate = getUseInstance() ? new Date(getInstanceStartTime() + getInstanceDuration()) : appt.getComputedEndDate();
        Calendar endCalendar = Calendar.getInstance(apptTz);
        endCalendar.setTime(endDate);

        setEndDate(paramInit(req, ZComposeUploaderBean.F_endDate, df.format(endDate)));

        if (appt.isAllDay()) {
            setEndHour(0);
            setEndMinute(0);
        } else {
            setEndHour(endCalendar.get(Calendar.HOUR_OF_DAY));
            setEndMinute(endCalendar.get(Calendar.MINUTE));
        }

        initRepeat(appt.getSimpleRecurrence(), startDate, pc, mailbox);
    }

    private String getQuotedHeaders(ZMessageBean msg, PageContext pc) {
        StringBuilder headers = new StringBuilder();
        //from, to, cc, date, subject
        String fromHdr = msg.getDisplayFrom();
        if (fromHdr != null)
            headers.append(LocaleSupport.getLocalizedMessage(pc, "ZM_HEADER_FROM")).append(": ").append(fromHdr).append(CRLF);
        String toHdr = msg.getDisplayTo();
        if (toHdr != null)
            headers.append(LocaleSupport.getLocalizedMessage(pc, "ZM_HEADER_TO")).append(": ").append(toHdr).append(CRLF);
         String ccHdr = msg.getDisplayCc();
        if (ccHdr != null)
            headers.append(LocaleSupport.getLocalizedMessage(pc, "ZM_HEADER_CC")).append(": ").append(ccHdr).append(CRLF);

        headers.append(LocaleSupport.getLocalizedMessage(pc, "ZM_HEADER_SENT")).append(": ").append(msg.getDisplaySentDate()).append(CRLF);

        String subjectHdr = msg.getSubject();
        if (subjectHdr != null)
            headers.append(LocaleSupport.getLocalizedMessage(pc, "ZM_HEADER_SUBJECT")).append(": ").append(subjectHdr).append(CRLF);
        return headers.toString();
    }

    private void forwardInclude(ZMessageBean msg, StringBuilder content, ZIdentity identity, PageContext pc) {
        if (identity.getForwardIncludeAsAttachment()) {
            mMessageAttachments = new ArrayList<MessageAttachment>();
            mMessageAttachments.add(new MessageAttachment(msg.getId(), msg.getSubject()));
        } else if (identity.getForwardIncludeBody()) {
            content.append(CRLF).append(CRLF).append(LocaleSupport.getLocalizedMessage(pc, "ZM_forwardedMessage")).append(CRLF);
            content.append(getQuotedHeaders(msg, pc)).append(CRLF);
            content.append(msg.getBody().getContent());
            content.append(CRLF);
            addAttachments(msg, true);
        } else if (identity.getForwardIncludeBodyWithPrefx()) {
            content.append(CRLF).append(CRLF).append(LocaleSupport.getLocalizedMessage(pc, "ZM_forwardPrefix", new Object[] {msg.getDisplayFrom()})).append(CRLF);
            content.append(getQuotedBody(msg, identity));
            content.append(CRLF);
            addAttachments(msg, true);
        }
    }

    private void replyInclude(ZMessageBean msg, StringBuilder content, ZIdentity identity, PageContext pc) {
        if (identity.getReplyIncludeNone()) {
            // nothing to see, move along
        } else if (identity.getReplyIncludeBody()) {
            content.append(CRLF).append(CRLF).append(LocaleSupport.getLocalizedMessage(pc, "ZM_originalMessage")).append(CRLF);
            content.append(getQuotedHeaders(msg, pc)).append(CRLF);
            content.append(msg.getBody().getContent());
            content.append(CRLF);
            addAttachments(msg, false);
        } else if (identity.getReplyIncludeBodyWithPrefx()) {
            content.append(CRLF).append(CRLF).append(LocaleSupport.getLocalizedMessage(pc, "ZM_replyPrefix", new Object[] {msg.getDisplayFrom()})).append(CRLF);
            content.append(getQuotedBody(msg, identity));
            content.append(CRLF);
            addAttachments(msg, false);
        } else if (identity.getReplyIncludeSmart()) {
            // TODO: duh
        } else if (identity.getReplyIncludeAsAttachment()) {
            mMessageAttachments = new ArrayList<MessageAttachment>();
            mMessageAttachments.add(new MessageAttachment(msg.getId(), msg.getSubject()));
        }
    }

    private void addAttachments(ZMessageBean msg, boolean checked) {
        List<ZMimePartBean> attachments = msg.getAttachments();
        setOrignalAttachments(attachments);
        if (checked) {
            for (ZMimePartBean part : attachments) {
                setCheckedAttachmentName(part.getPartName());
            }
        }
    }

    private String getQuotedBody(ZMessageBean msg, ZIdentity identity) {
        String prefixChar = identity.getForwardReplyPrefixChar();
        prefixChar = (prefixChar == null) ? "> " : prefixChar + " ";
        return BeanUtils.prefixContent(msg.getBody().getContent(), prefixChar);
    }

    private ZIdentity computeIdentity(ZMessageBean msg, List<ZIdentity> identities) {

        if (identities.size() == 1)
            return identities.get(0);

        if (msg == null)
            return defaultIdentity(identities);

        List<ZEmailAddress> addressList = new ArrayList<ZEmailAddress>();
        for (ZEmailAddress address: msg.getEmailAddresses()) {
            if (ZEmailAddress.EMAIL_TYPE_TO.equals(address.getType()) ||
                    ZEmailAddress.EMAIL_TYPE_CC.equals(address.getType())) {
                addressList.add(address);
            }
        }
        
        for (ZIdentity identity: identities) {
            for (ZEmailAddress address : addressList) {
                if (identity.containsAddress(address))
                    return identity;
            }
        }
        
        String folderId = msg.getFolderId();
        
        for (ZIdentity identity: identities) {
            if (identity.containsFolderId(folderId))
                return identity;
        }

        return defaultIdentity(identities);
        
    }

    private ZIdentity defaultIdentity(List<ZIdentity> identities) {
        if (identities.size() == 1)
            return identities.get(0);
        
        for (ZIdentity identity: identities) {
            if (identity.isDefault())
                return identity;
        }
        return identities.get(0);
    }


    private static String getReplySubject(String subject, PageContext pc) {
        String REPLY_PREFIX = LocaleSupport.getLocalizedMessage(pc, "ZM_replySubjectPrefix");                
        if (subject == null) subject = "";
        if ((subject.length() > 3) && subject.substring(0, 3).equalsIgnoreCase(REPLY_PREFIX))
            return subject;
        else
            return REPLY_PREFIX+" "+subject;
    }

    private static String getForwardSubject(String subject, PageContext pc) {
        String FORWARD_PREFIX = LocaleSupport.getLocalizedMessage(pc, "ZM_forwardSubjectPrefix");
        if (subject == null) subject = "";
        if ((subject.length() > 3) && subject.substring(0, 3).equalsIgnoreCase(FORWARD_PREFIX))
            return subject;
        else
            return FORWARD_PREFIX+" "+subject;
    }

    private static String getToAddress(List<ZEmailAddress> emailAddresses, List<ZEmailAddress> toAddressList, Set<String> toAddresses, Set<String> aliases) {
        for (ZEmailAddress address : emailAddresses) {
            if (ZEmailAddress.EMAIL_TYPE_REPLY_TO.equals(address.getType())) {
                if (aliases.contains(address.getAddress().toLowerCase()))
                    return "";
                toAddresses.add(address.getAddress());
                toAddressList.add(address);
                return address.getFullAddress();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ZEmailAddress address : emailAddresses) {
            if (ZEmailAddress.EMAIL_TYPE_FROM.equals(address.getType())) {
                if (!aliases.contains(address.getAddress().toLowerCase())) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getFullAddress());
                    toAddressList.add(address);                
                    toAddresses.add(address.getAddress());
                }
            }
        }
        return sb.toString();
    }

    private static String getCcAddress(List<ZEmailAddress> emailAddresses, Set<String> toAddresses, Set<String> aliases) {
        StringBuilder sb = new StringBuilder();
        for (ZEmailAddress address : emailAddresses) {
            if (ZEmailAddress.EMAIL_TYPE_TO.equals(address.getType()) ||
                    ZEmailAddress.EMAIL_TYPE_CC.equals(address.getType())) {
                String a = address.getAddress().toLowerCase();
                if (!toAddresses.contains(a) && !aliases.contains(a)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getFullAddress());
                }
            }
        }
        return sb.toString();
    }

    /*

     <comp status="CONF" fb="B" transp="O" allDay="0" name="test yearly">
    <s tz="(GMT-08.00) Pacific Time (US &amp; Canada)" d="20070308T130000"/>
    <e tz="(GMT-08.00) Pacific Time (US &amp; Canada)" d="20070308T150000"/>
      <or a="user1@slapshot.liquidsys.com"/>
       <recur>
        <add>
         <rule freq="YEA">
            <interval ival="1"/>
         </rule>
        </add>
      </recur>
    </comp></inv>

     */
    public ZInvite toInvite(ZMailbox mailbox, ZMessageBean message) throws ServiceException {
        ZInvite existingInvite = message != null ? message.getInvite() : null;
        ZInvite invite = new ZInvite();
        ZInvite.ZComponent comp = new ZComponent();
        comp.setStatus(ZStatus.CONF);
        comp.setTransparency(ZTransparency.O);
        comp.setFreeBusyStatus(ZFreeBusyStatus.fromString(mFreeBusyStatus));
        if (mTimeZone == null || mTimeZone.length() == 0)
            mTimeZone = mailbox.getPrefs().getTimeZoneWindowsId();
        comp.setStart(new ZDateTime(getApptStartTime(), mTimeZone));
        comp.setEnd(new ZDateTime(getApptEndTime(), mTimeZone));
        if (mLocation != null && mLocation.length() > 0) comp.setLocation(mLocation);
        comp.setName(mSubject);
        comp.setOrganizer(new ZOrganizer(mailbox.getName()));
        comp.setIsAllDay(getAllDay());

        if (mAttendees != null && mAttendees.length() > 0) {
            List<ZEmailAddress> addrs =
                    ZEmailAddress.parseAddresses(mAttendees, ZEmailAddress.EMAIL_TYPE_TO);
            for (ZEmailAddress addr : addrs) {
                ZAttendee attendee = new ZAttendee();
                attendee.setAddress(addr.getAddress());
                attendee.setRole(ZRole.REQ);
                attendee.setParticipantStatus(ZParticipantStatus.NE);
                attendee.setRSVP(true);
                if (addr.getPersonal() != null) attendee.setPersonalName(addr.getPersonal());
                comp.getAttendees().add(attendee);
            }
        }
        invite.getComponents().add(comp);
        ZComponent ecomp = existingInvite != null ? existingInvite.getComponent() : null;


        // don't set recurrence for exceptions
        if (!getUseInstance()) {
            ZSimpleRecurrence repeat = getSimpleRecurrence();
            switch(repeat.getType()) {
                case NONE:
                    break;
                case COMPLEX:
                    // leave it alone!
                    if (ecomp != null)
                        comp.setRecurrence(ecomp.getRecurrence());
                    break;
                default:
                    comp.setRecurrence(repeat.getRecurrence());
                    break;
            }
        }


        if (ecomp != null) {

            comp.setSequenceNumber(ecomp.getSequenceNumber());
            comp.setTransparency(ecomp.getTransparency());
            comp.setStatus(ecomp.getStatus());
        }
        return invite;
    }

    public boolean getIsValidStartTime() {
        try {
            getApptStartTime();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean getIsValidEndTime() {
        try {
            getApptEndTime();
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public boolean getIsValidRepeatEndDateTime() {
        try {
            getRepeatEndDateTime();
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public String getRepeatEndDateTime() throws ZTagLibException {
        return getICalTime(mRepeatEndDate, 0 ,0);
    }

    public String getApptStartTime() throws ZTagLibException {
        return getICalTime(mStartDate, mStartHour, mStartMinute);
    }
    
    public Calendar getApptStartCalendar() {
        try {
            return getCalendar(mStartDate, mStartHour, mStartMinute);
        } catch (ZTagLibException e) {
            return null;
        }
    }

    public Calendar getApptEndCalendar() {
        try {
            return getCalendar(mEndDate, mEndHour, mEndMinute);
        } catch (ZTagLibException e) {
            return null;
        }
    }

    public String getApptEndTime() throws ZTagLibException {
        return getICalTime(mEndDate, mEndHour, mEndMinute);
    }

    private String getICalTime(String dateStr, long hour, long minute) throws ZTagLibException {
            DateFormat icalFmt = new SimpleDateFormat(getAllDay() ? "yyyyMMdd" : "yyyyMMdd'T'HHmmss");
            return icalFmt.format(getCalendar(dateStr, hour, minute).getTime());
    }

    @SuppressWarnings({"EmptyCatchBlock"})
    private Calendar getCalendar(String dateStr, long hour, long minute) throws ZTagLibException {
        try {

            if (dateStr == null)
                throw ZTagLibException.INVALID_APPT_DATE("date field is empty", null);

            DateFormat df = new SimpleDateFormat(mDateFormat);
            df.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date date = df.parse(dateStr, pos);

            if (pos.getIndex() != dateStr.length())
                throw ZTagLibException.INVALID_APPT_DATE("invalid date: "+dateStr, null);

            if (hour < 0 || hour > 23)
                throw  ZTagLibException.INVALID_APPT_DATE("invalid hour: "+hour, null);

            if (minute < 0 || minute > 59)
                throw ZTagLibException.INVALID_APPT_DATE("invalid minute: "+minute, null);


            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, (int)hour);
            cal.set(Calendar.MINUTE, (int)minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            int year = cal.get(Calendar.YEAR);
            if (year < 100)
                cal.set(Calendar.YEAR, 2000+year);
            if (year < 1900)
                throw ZTagLibException.INVALID_APPT_DATE("invalid year: "+year, null);

            return cal;
        } catch (Exception e) {
            throw ZTagLibException.INVALID_APPT_DATE(dateStr, e);
        }
    }

    /**
     * fix up content be pre-pending blurb to content
     * @param mailbox mailbox
     * @param newInvite outgoing invite
     * @param previousInvite previous invite (null if new invite)
     * @param blurbHeaderKey message prop key to use for blurb header
     * @throws ServiceException on error
     * @param pc page context for messages
     */
    public void setInviteBlurb(ZMailbox mailbox, PageContext pc, ZInvite newInvite, ZInvite previousInvite, String blurbHeaderKey) throws ServiceException {

        String content = getContent();
        if (content == null)
            content = "";

        String blurb = generateInviteBlurb(mailbox, pc, newInvite, previousInvite, blurbHeaderKey, false);
        setContent(blurb+content);

        String htmlBlurb = generateInviteBlurb(mailbox, pc, newInvite, previousInvite, blurbHeaderKey, true);
        setHtmlContent("<html><body>"+htmlBlurb+BeanUtils.textToHtml(content)+"</body></html>");

        /*
        StringBuilder sb = new StringBuilder();
        String blurbHeader = LocaleSupport.getLocalizedMessage(pc, blurbHeaderKey);

        if (blurbHeader != null) {
            sb.append(blurbHeader);
            sb.append("\n\n");
        }

The following is a new meeting request:

Subject: da subject
Organizer: "Demo User One" <user1@macpro.local>

Location: da location
Time: 4:30:00 PM - 6:00:00 PM
 Recurrence : The first Thursday of every 1 month(s). No end date. Effective Mar 22, 2007.

Invitees: user2@macpro.local



da body
        
        */

        
    }

    private void addLine(StringBuilder sb, String a, String b, String c, boolean html, String tzId)
    {
        if (html) {
            sb.append("<tr><th valign='top' align='left'>").append(a).append(":</th><td>");
            sb.append(BeanUtils.htmlEncode(b));

            if (tzId != null)
                sb.append(" <span style='color: #686357'>").append(tzId).append("</span>");

            if (c != null) sb.append(' ').append(c);
            sb.append("</td></tr>");
        } else {
            sb.append(a).append(": ").append(b);
            if (tzId != null)
                sb.append(" ").append(tzId);
            if (c != null) sb.append(' ').append(c);
        }
    }

    private static String msg(PageContext pc, String key) {
        return LocaleSupport.getLocalizedMessage(pc, key);
    }

    private String generateInviteBlurb(ZMailbox mailbox, PageContext pc,
                                      ZInvite newInvite, ZInvite previousInvite,
                                      String blurbHeaderKey, boolean html) throws ServiceException
    {
        String mod = msg(pc, "apptModifiedStamp");
        TimeZone tz = mailbox.getPrefs().getTimeZone();
        String tzId = mailbox.getPrefs().getTimeZoneWindowsId();
        
        StringBuilder sb = new StringBuilder();

        ZComponent appt = newInvite.getComponent();
        ZComponent oldAppt = previousInvite == null ? null : previousInvite.getComponent();

        if (html) sb.append("<h3>");
        sb.append(LocaleSupport.getLocalizedMessage(pc, blurbHeaderKey));
        if (html) sb.append("</h3>");
        sb.append("\n\n");

        if (html) sb.append("<p>\n<table border='0'>\n");

        String name = appt.getName();
        String oldName = oldAppt == null ? null : oldAppt.getName();

        addLine(sb,
                msg(pc, "subject"),
                name,
                oldAppt != null && !name.equals(oldName) ? mod : null,
                html,
                null
        );
        sb.append("\n");

        ZOrganizer org = appt.getOrganizer();
        if (org != null) {
            String orgEmail = org.getEmailAddress().getFullAddress();
            String oldOrgEmail = oldAppt == null || oldAppt.getOrganizer() == null ? null :
                    oldAppt.getOrganizer().getEmailAddress().getFullAddress(); 
            addLine(sb,
                    msg(pc, "organizer"),
                    orgEmail,
                    oldAppt != null && !orgEmail.equals(oldOrgEmail) ? mod : null,
                    html,
                    null
            );
            sb.append("\n");
        }

        if (html) sb.append("</table>\n");
        sb.append("\n");

        if (html) sb.append("<p>\n<table border='0'>\n");

        String loc = appt.getLocation();
        String oldLoc = oldAppt == null ? null : oldAppt.getLocation();
        
        if (loc != null) {
            addLine(sb,
                    msg(pc, "location"),
                    loc,
                    oldAppt != null && !loc.equals(oldLoc) ? mod : null,
                    html,
                    null
                    );
            sb.append("\n");
        }

        String timeBlurb = BeanUtils.getApptDateBlurb(pc,
                tz,
                getApptStartCalendar().getTimeInMillis(),
                getApptEndCalendar().getTimeInMillis(),
                getAllDay());

        String oldTimeBlurb = oldAppt == null ? null :
            BeanUtils.getApptDateBlurb(pc,
                    tz,
                    oldAppt.getStart().getDate().getTime(),
                    oldAppt.getComputedEndDate().getTime(),
                    oldAppt.isAllDay());

        boolean diffTime = oldAppt != null && !timeBlurb.equals(oldTimeBlurb);

        addLine(sb,
                msg(pc, "time"),
                timeBlurb,
                oldAppt != null && diffTime ? mod : null,
                html,
                tzId);
        sb.append("\n");
        
        ZSimpleRecurrence repeat = appt.getSimpleRecurrence();
        ZSimpleRecurrence oldRepeat = oldAppt == null ? null : oldAppt.getSimpleRecurrence();

        String repeatStr = BeanUtils.getRepeatBlurb(repeat, pc, tz, appt.getStart().getDate());
        String oldRepeatStr = oldAppt == null ? "" : BeanUtils.getRepeatBlurb(oldRepeat, pc, tz, oldAppt.getStart().getDate());
        boolean diffRepeat = oldAppt != null && !repeatStr.equals(oldRepeatStr);
        if (repeat.getType() != ZSimpleRecurrenceType.NONE || diffRepeat) {
            addLine(sb,
                    msg(pc, "recurrence"),
                    repeatStr,
                    diffRepeat ? mod : null,
                    html,
                    null);
            sb.append("\n");
        }

        if (html) sb.append("</table>\n");

        List<ZAttendee> attendees = appt.getAttendees();
        if (attendees != null && attendees.size() > 0) {
            if (html) sb.append("<p>\n<table border='0'>\n");
            sb.append("\n");
            String alist = getAttendeeList(attendees);
            String oldAlist = oldAppt == null ? null : getAttendeeList(oldAppt.getAttendees());
            addLine(sb,
                    msg(pc, "invitees"),
                    alist,
                    oldAppt != null && !alist.equals(oldAlist) ? mod : null, /// TODO: THIS IS BROKE IF FIRST 10 ARE SAME
                    html, null);
            if (html) sb.append("</table>\n");
        }

        sb.append(html ? "<div>" : "\n\n");
        sb.append(NOTES_SEPARATOR);
        sb.append(html ? "</div><br>" : "\n\n");
        return sb.toString();
    }

    private String getAttendeeList(List<ZAttendee> attendees) {
        if (attendees == null || attendees.size() == 0)
            return "";
        StringBuilder a = new StringBuilder();
        int n = 0;
        for (ZAttendee attendee : attendees) {
            if (a.length() > 0) a.append("; ");
            a.append(attendee.getEmailAddress().getFullAddress());
            if (n++ > 10) break;
        }
        if (attendees.size() > 10) a.append("; ...");
        return a.toString();
    }
    
    public ZOutgoingMessage toOutgoingMessage(ZMailbox mailbox) throws ServiceException {

        List<ZEmailAddress> addrs = new ArrayList<ZEmailAddress>();

        if (mTo != null && mTo.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mTo, ZEmailAddress.EMAIL_TYPE_TO));

        if (mReplyTo != null && mReplyTo.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mReplyTo, ZEmailAddress.EMAIL_TYPE_REPLY_TO));

        if (mCc != null && mCc.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mCc, ZEmailAddress.EMAIL_TYPE_CC));

        if (mFrom != null && mFrom.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mFrom, ZEmailAddress.EMAIL_TYPE_FROM));

        if (mBcc != null && mBcc.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mBcc, ZEmailAddress.EMAIL_TYPE_BCC));

        if (mAttendees != null && mAttendees.length() > 0)
            addrs.addAll(ZEmailAddress.parseAddresses(mAttendees, ZEmailAddress.EMAIL_TYPE_TO));

        ZOutgoingMessage m = new ZOutgoingMessage();
        
        if (mMessageAttachments != null && mMessageAttachments.size() > 0) {
            List<String> messages = new ArrayList<String>();
            for (MessageAttachment ma : mMessageAttachments) {
                messages.add(ma.getId());
            }
            m.setMessageIdsToAttach(messages);
        }

        if (mCheckedAttachmentNames != null && mCheckedAttachmentNames.size() > 0) {
            List<AttachedMessagePart> attachments = new ArrayList<AttachedMessagePart>();
            for (Map.Entry<String,Boolean> entry : mCheckedAttachmentNames.entrySet()) {
                if (entry.getValue()) {
                    String mid = (mDraftId != null && mDraftId.length() > 0) ? mDraftId : mMessageId;
                    if (mid != null && mid.length() > 0) {
                        attachments.add(new AttachedMessagePart(mid, entry.getKey()));
                    }
                }
            }
            m.setMessagePartsToAttach(attachments);
        }

        /*
        if (mOriginalAttachments != null && mOriginalAttachments.size() > 0) {
            List<AttachedMessagePart> attachments = m.getMessagePartsToAttach();
            if (attachments == null) attachments = new ArrayList<AttachedMessagePart>();
            for (ZMimePartBean part : mOriginalAttachments) {
                attachments.add(new AttachedMessagePart(mMessageId, part.getPartName()));
            }
            m.setMessagePartsToAttach(attachments);
        }
        */

        m.setAddresses(addrs);

        m.setSubject(mSubject);

        if (mInReplyTo != null && mInReplyTo.length() > 0)
            m.setInReplyTo(mInReplyTo);

        if (mMessageId != null && mMessageId.length() > 0)
            m.setOriginalMessageId(mMessageId);

        if (mReplyType != null && mReplyType.length() > 0)
            m.setReplyType(mReplyType);

        boolean hasHtml = mHtmlContent != null && mHtmlContent.length() > 0;
        boolean hasText = mContent != null;

        if (hasHtml && hasText) {
            m.setMessagePart(new MessagePart(ZMimePartBean.CT_MULTI_ALT,
                    new MessagePart(ZMimePartBean.CT_TEXT_PLAIN, mContent),
                    new MessagePart(ZMimePartBean.CT_TEXT_HTML, mHtmlContent)
                    ));
        } else if (hasHtml) {
            m.setMessagePart(new MessagePart(ZMimePartBean.CT_TEXT_HTML, mContent != null ? mContent : ""));
        } else {
            m.setMessagePart(new MessagePart(mContentType, mContent != null ? mContent : ""));
        }



        if (getHasFileItems()) {
            Part[] parts = new Part[mFileItems.size()];
            int i=0;
            for (FileItem item : mFileItems) {
                parts[i++] = new FilePart(item.getFieldName(), new UploadPartSource(item), item.getContentType(), "utf-8");
            }
            try {
                m.setAttachmentUploadId(mailbox.uploadAttachments(parts, 1000*60)); //TODO get timeout from config
            } finally {
                for (FileItem item : mFileItems) {
                    try { item.delete(); } catch (Exception e) { /* TODO: need logging infra */ }
                }
            }
        }
        return m;
    }

    public static class UploadPartSource implements PartSource {

        private FileItem mItem;

        public UploadPartSource(FileItem item) { mItem = item; }

        public long getLength() {
            return mItem.getSize();
        }

        public String getFileName() {
            return mItem.getName();
        }

        public InputStream createInputStream() throws IOException {
            return mItem.getInputStream();
        }
    }
}
