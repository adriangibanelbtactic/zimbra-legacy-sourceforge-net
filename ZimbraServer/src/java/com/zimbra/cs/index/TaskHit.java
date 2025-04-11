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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.index;

import org.apache.lucene.document.Document;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.index.MailboxIndex.SortBy;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Task;
import com.zimbra.cs.mailbox.calendar.Invite;

/**
 * 
 */
public class TaskHit extends CalendarItemHit {
    
    public static TaskHit create(ZimbraQueryResultsImpl results, Mailbox mbx, int mailItemId, Document d, float score, MailItem.UnderlyingData ud) throws ServiceException {
        CalendarItem calItem = null;
        byte type = MailItem.TYPE_TASK;
        if (ud != null) {
            calItem = (Task)mbx.getItemFromUnderlyingData(ud);
            type = ud.type;
        }
        return new TaskHit(results, mbx, mailItemId, d, score, calItem, type);
    }
    public TaskHit(ZimbraQueryResultsImpl results, Mailbox mbx, int mailItemId, Document d, float score, CalendarItem calItem, byte type) throws ServiceException {
        super(results, mbx, mailItemId, d, score, calItem, type);
    }
    
    public long getDueTime() throws ServiceException {
        Task task = (Task)getCalendarItem();
        return task.getEndTime();
    }
    
    public int getCompletionPercentage() throws ServiceException {
        Task task = (Task)getCalendarItem();
        Invite inv = task.getDefaultInviteOrNull();
        if (inv != null) {
            String compPerc = inv.getPercentComplete();
            if (compPerc != null) {
                try {
                    int toRet = Integer.parseInt(compPerc);
                    return toRet;
                } catch (Exception e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    static enum Status {
        NEED(0),
        INPR(1),
        COMP(2),
        CANC(3),
        ;
        
        Status(int sortVal) { mSortVal = sortVal; }
        public int getSortVal() { return mSortVal; }
        private int mSortVal;
    }
    
    public Status getStatus() throws ServiceException {
        Task task = (Task)getCalendarItem();
        Invite inv = task.getDefaultInviteOrNull();
        if (inv != null) { 
            String status = inv.getStatus();
            for (Status s : Status.values()) {
                if (s.name().equalsIgnoreCase(status)) 
                    return s;
            }
        }
        return Status.CANC;
    }
    
    public Object getSortField(SortBy sortOrder) throws ServiceException {
        switch (sortOrder) {
            case TASK_DUE_ASCENDING:
            case TASK_DUE_DESCENDING:
                return getDueTime();
            case TASK_STATUS_ASCENDING:
            case TASK_STATUS_DESCENDING:
                return getStatus();
            case TASK_PERCENT_COMPLETE_ASCENDING:
            case TASK_PERCENT_COMPLETE_DESCENDING:
                return getCompletionPercentage();
            default:
                return super.getSortField(sortOrder);
        }
    }
    
    int compareBySortField(MailboxIndex.SortBy sortOrder, ZimbraHit other) throws ServiceException {
        switch (sortOrder) {
            case TASK_DUE_ASCENDING:
                return compareByDueDate(true, this, other);
            case TASK_DUE_DESCENDING:
                return compareByDueDate(false, this, other);
            case TASK_STATUS_ASCENDING:
                return compareByStatus(true, this, other);
            case TASK_STATUS_DESCENDING:
                return compareByStatus(false, this, other);
            case TASK_PERCENT_COMPLETE_ASCENDING:
                return compareByCompletionPercent(true, this, other);
            case TASK_PERCENT_COMPLETE_DESCENDING:
                return compareByCompletionPercent(false, this, other);
            default:
                return super.compareBySortField(sortOrder, other);
        }
    }
    
    private static long getDueTime(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit)
            return ((ProxiedHit)zh).getElement().getAttributeLong(MailConstants.A_TASK_DUE_DATE);
        else 
            return ((TaskHit)zh).getDueTime();
    }
    
    private static Status getStatus(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit) {
            String s = ((ProxiedHit)zh).getElement().getAttribute(MailConstants.A_CAL_STATUS);
            return Status.valueOf(s);
        } else {
            return ((TaskHit)zh).getStatus();
        }
    }
    
    private static int getCompletionPercentage(ZimbraHit zh) throws ServiceException {
        if (zh instanceof ProxiedHit) 
            return (int)(((ProxiedHit)zh).getElement().getAttributeLong(MailConstants.A_TASK_PERCENT_COMPLETE));
        else
            return ((TaskHit)zh).getCompletionPercentage();
    }
    
    static final int compareByDueDate(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            long left = getDueTime(lhs);
            long right = getDueTime(rhs);
            long result = right - left;
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s", 
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }

    static final int compareByStatus(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            Status left = getStatus(lhs);
            Status right = getStatus(rhs);
            int result = right.getSortVal() - left.getSortVal();
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s", 
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }
    
    static final int compareByCompletionPercent(boolean ascending, ZimbraHit lhs, ZimbraHit rhs) {
        int retVal = 0;
        try {
            int left = getCompletionPercentage(lhs);
            int right = getCompletionPercentage(rhs);
            int result = right - left;
            if (result > 0)
                retVal = 1;
            else if (result < 0)
                retVal = -1;
            else
                retVal = 0;
        } catch (ServiceException e) {
            ZimbraLog.index.info("Caught ServiceException trying to compare TaskHit %s to TaskHit %s", 
                lhs, rhs, e);
        }
        if (ascending)
            return -1 * retVal;
        else
            return retVal;
    }
}
