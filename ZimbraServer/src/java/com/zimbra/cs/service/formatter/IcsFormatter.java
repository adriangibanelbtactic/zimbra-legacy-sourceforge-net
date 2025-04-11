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
package com.zimbra.cs.service.formatter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.common.util.HttpUtil.Browser;
import com.zimbra.cs.account.ldap.LdapUtil;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZCalendarBuilder;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZVCalendar;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.UserServlet.Context;

import javax.mail.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IcsFormatter extends Formatter {

    public String getType() {
        return "ics";
    }

    public String[] getDefaultMimeTypes() {
        return new String[] { Mime.CT_TEXT_CALENDAR, "text/x-vcalendar" };
    }

    public String getDefaultSearchTypes() {
        return MailboxIndex.SEARCH_FOR_APPOINTMENTS;
    }

    public void formatCallback(Context context) throws IOException, ServiceException {
        Iterator<? extends MailItem> iterator = null;
        List<CalendarItem> calItems = new ArrayList<CalendarItem>();
        //ZimbraLog.mailbox.info("start = "+new Date(context.getStartTime()));
        //ZimbraLog.mailbox.info("end = "+new Date(context.getEndTime()));
        try {
            iterator = getMailItems(context, context.getStartTime(), context.getEndTime(), Integer.MAX_VALUE);

            // this is lame
            while (iterator.hasNext()) {
                MailItem item = iterator.next();
                if (item instanceof CalendarItem)
                    calItems.add((CalendarItem) item);
            }
        } finally {
            if (iterator instanceof QueryResultIterator)
                ((QueryResultIterator) iterator).finished();
        }

        // todo: get from folder name
        String filename = context.itemPath;
        if (filename == null || filename.length() == 0)
            filename = "contacts";
        String cd = Part.ATTACHMENT + "; filename=" + HttpUtil.encodeFilename(context.req, filename + ".ics");
        context.resp.addHeader("Content-Disposition", cd);
        context.resp.setCharacterEncoding(Mime.P_CHARSET_UTF8);
        context.resp.setContentType(Mime.CT_TEXT_CALENDAR );

        Browser browser = HttpUtil.guessBrowser(context.req);
        boolean useOutlookCompatMode = Browser.IE.equals(browser);
//        try {
            ZVCalendar cal = context.targetMailbox.getZCalendarForCalendarItems(
                    calItems, useOutlookCompatMode, true,
                    !context.opContext.isDelegatedRequest(context.targetMailbox));
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            OutputStreamWriter wout = new OutputStreamWriter(buf, Mime.P_CHARSET_UTF8);
            cal.toICalendar(wout);
            wout.flush();
            context.resp.getOutputStream().write(buf.toByteArray());
//        } catch (ValidationException e) {
//            throw ServiceException.FAILURE(" mbox:"+context.targetMailbox.getId()+" unable to get calendar "+e, e);
//        }
    }
    
    // get the whole calendar
    public long getDefaultStartTime() {    
        return 0;
    }

    // eventually get this from query param ?end=long|YYYYMMMDDHHMMSS
    public long getDefaultEndTime() {
        return System.currentTimeMillis() + (365 * 100 * Constants.MILLIS_PER_DAY);            
    }

    public boolean canBeBlocked() {
        return false;
    }

    public void saveCallback(byte[] body, Context context, String contentType, Folder folder, String filename) throws ServiceException, IOException {
        // TODO: Modify Formatter.save() API to pass in charset of body, then
        // use that charset in String() constructor.
        Reader reader = new StringReader(new String(body, Mime.P_CHARSET_UTF8));
        List<ZVCalendar> icals = ZCalendarBuilder.buildMulti(reader);
        List<Invite> invites = Invite.createFromCalendar(context.authAccount, null, icals, true);
        for (Invite inv : invites) {
            // handle missing UIDs on remote calendars by generating them as needed
            if (inv.getUid() == null)
                inv.setUid(LdapUtil.generateUUID());
            // and add the invite to the calendar!
            folder.getMailbox().addInvite(context.opContext, inv, folder.getId(), false, null);
        }
    }
}
