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
package com.zimbra.cs.dav.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.Element;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavElements;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.calendar.CalendarMailSender;
import com.zimbra.cs.mailbox.calendar.FreeBusy;
import com.zimbra.cs.mailbox.calendar.ParsedDateTime;
import com.zimbra.cs.mailbox.calendar.ParsedDuration;
import com.zimbra.cs.mailbox.calendar.ZCalendar;
import com.zimbra.cs.mailbox.calendar.ZOrganizer;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ICalTok;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZComponent;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZProperty;
import com.zimbra.cs.util.AccountUtil;

public class ScheduleOutbox extends CalendarCollection {
	public ScheduleOutbox(DavContext ctxt, Folder f) throws DavException, ServiceException {
		super(ctxt, f);
		addResourceType(DavElements.E_SCHEDULE_OUTBOX);
	}
	
	public void handlePost(DavContext ctxt) throws DavException, IOException, ServiceException {
		String      originator = ctxt.getRequest().getHeader(DavProtocol.HEADER_ORIGINATOR);
		Enumeration recipients = ctxt.getRequest().getHeaders(DavProtocol.HEADER_RECIPIENT);

		byte[] msg = ByteUtil.getContent(ctxt.getRequest().getInputStream(), 0);
		if (ZimbraLog.dav.isDebugEnabled())
			ZimbraLog.dav.debug(new String(msg, "UTF-8"));

		ZCalendar.ZVCalendar vcalendar = ZCalendar.ZCalendarBuilder.build(new InputStreamReader(new ByteArrayInputStream(msg)));
		Iterator<ZComponent> iter = vcalendar.getComponentIterator();
		ZComponent req = null;
		while (iter.hasNext()) {
			req = iter.next();
			if (req.getTok() != ICalTok.VTIMEZONE)
				break;
			req = null;
		}
		if (req == null)
			throw new DavException("empty request", HttpServletResponse.SC_BAD_REQUEST);
		ZProperty organizerProp = req.getProperty(ICalTok.ORGANIZER);
		if (organizerProp != null) {
			ZOrganizer organizer = new ZOrganizer(organizerProp);
			String organizerStr = this.getAddressFromPrincipalURL(organizer.getAddress());
			if (!organizerStr.equals(ctxt.getAuthAccount().getName()))
				throw new DavException("the requestor is not the organizer", HttpServletResponse.SC_FORBIDDEN);
		}
		ZimbraLog.dav.debug("originator: "+originator);
		Element scheduleResponse = ctxt.getDavResponse().getTop(DavElements.E_SCHEDULE_RESPONSE);
		while (recipients.hasMoreElements()) {
			String rcpt = (String) recipients.nextElement();
			//rcpt = this.getAddressFromPrincipalURL(rcpt);
			ZimbraLog.dav.debug("recipient email: "+rcpt);
			Element resp = scheduleResponse.addElement(DavElements.E_CALDAV_RESPONSE);
			switch (req.getTok()) {
			case VFREEBUSY:
				handleFreebusyRequest(ctxt, req, originator, rcpt, resp);
				break;
			case VEVENT:
				handleEventRequest(ctxt, vcalendar, req, originator, rcpt, resp);
				break;
			default:
				throw new DavException("unrecognized request: "+req.getTok(), HttpServletResponse.SC_BAD_REQUEST);
			}
		}
	}
	
	private void handleFreebusyRequest(DavContext ctxt, ZComponent vfreebusy, String originator, String rcpt, Element resp) throws DavException, ServiceException, IOException {
		ZProperty dtstartProp = vfreebusy.getProperty(ICalTok.DTSTART);
		ZProperty dtendProp = vfreebusy.getProperty(ICalTok.DTEND);
		ZProperty durationProp = vfreebusy.getProperty(ICalTok.DURATION);
		if (dtstartProp == null || dtendProp == null && durationProp == null)
			throw new DavException("missing dtstart or dtend/duration in the schedule request", HttpServletResponse.SC_BAD_REQUEST, null);
		long start, end;
		try {
			ParsedDateTime startTime = ParsedDateTime.parseUtcOnly(dtstartProp.getValue());
			start = startTime.getUtcTime();
			if (dtendProp != null) {
				end = ParsedDateTime.parseUtcOnly(dtendProp.getValue()).getUtcTime();
			} else {
				ParsedDuration dur = ParsedDuration.parse(durationProp.getValue());
				end = start + dur.getDurationAsMsecs(new Date(start));
			}
		} catch (ParseException pe) {
			throw new DavException("can't parse date", HttpServletResponse.SC_BAD_REQUEST, pe);
		}

		ZimbraLog.dav.debug("start: "+new Date(start)+", end: "+new Date(end));
		Provisioning prov = Provisioning.getInstance();
		rcpt = this.getAddressFromPrincipalURL(rcpt);
		Account rcptAcct = prov.get(Provisioning.AccountBy.name, rcpt);
		if (rcptAcct == null)
			throw new DavException("not on local server: "+rcpt, HttpServletResponse.SC_BAD_REQUEST);
		if (Provisioning.onLocalServer(rcptAcct)) {
			Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(rcptAcct);
			FreeBusy fb = mbox.getFreeBusy(start, end);
			String fbMsg = fb.toVCalendar(FreeBusy.Method.REPLY, originator, rcpt, null);
			resp.addElement(DavElements.E_RECIPIENT).setText(rcpt);
			resp.addElement(DavElements.E_REQUEST_STATUS).setText("2.0;Success");
			resp.addElement(DavElements.E_CALENDAR_DATA).setText(fbMsg);
		} else {
			// XXX get the freebusy information from remote server
		}
	}
	
	private void handleEventRequest(DavContext ctxt, ZCalendar.ZVCalendar cal, ZComponent req, String originator, String rcpt, Element resp) throws DavException, ServiceException {
        Address from, to;
        try {
            from = AccountUtil.getFriendlyEmailAddress(ctxt.getAuthAccount());
            if (rcpt.toLowerCase().startsWith("mailto:"))
            	rcpt = rcpt.substring(7);
            to = new InternetAddress(rcpt);
        } catch (UnsupportedEncodingException e) {
			resp.addElement(DavElements.E_RECIPIENT).setText(rcpt);
			resp.addElement(DavElements.E_REQUEST_STATUS).setText("3.1;ORGANIZER");
			return;
        } catch (AddressException e) {
			resp.addElement(DavElements.E_RECIPIENT).setText(rcpt);
			resp.addElement(DavElements.E_REQUEST_STATUS).setText("3.7;"+rcpt);
			return;
        }
        ArrayList<Address> recipients = new java.util.ArrayList<Address>();
        recipients.add(to);
        String subject, uid, text;

        subject = "Meeting request: " + req.getPropVal(ICalTok.SUMMARY, "");
        text = req.getPropVal(ICalTok.DESCRIPTION, "");
        uid = req.getPropVal(ICalTok.UID, null);
        if (uid == null) {
			resp.addElement(DavElements.E_RECIPIENT).setText(rcpt);
			resp.addElement(DavElements.E_REQUEST_STATUS).setText("3.1;UID");
			return;
        }
        try {
        	MimeMessage mm = CalendarMailSender.createCalendarMessage(from, from, recipients, subject, text, uid, cal);
        	Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(ctxt.getAuthAccount());
        	mbox.getMailSender().sendMimeMessage(ctxt.getOperationContext(), mbox, true, mm, null, null, 0, null, null, true, false);
        } catch (ServiceException e) {
			resp.addElement(DavElements.E_RECIPIENT).setText(rcpt);
			resp.addElement(DavElements.E_REQUEST_STATUS).setText("5.1");
        }
	}
}
