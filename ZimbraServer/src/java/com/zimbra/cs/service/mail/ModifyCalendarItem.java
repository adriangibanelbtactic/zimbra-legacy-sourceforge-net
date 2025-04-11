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

package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.MessagingException;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.ZAttendee;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.common.service.ServiceException;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;


public class ModifyCalendarItem extends CalendarRequest {

    private static Log sLog = LogFactory.getLog(ModifyCalendarItem.class);

    private static final String[] TARGET_PATH = new String[] { MailService.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return false; }

    // very simple: generate a new UID and send a REQUEST
    protected class ModifyCalendarItemParser extends ParseMimeMessage.InviteParser {
        protected Mailbox mmbox;
        protected Invite mInv;
        
        ModifyCalendarItemParser(Mailbox mbox, Invite inv) {
            mmbox = mbox;
            mInv = inv;
        }
        
        public ParseMimeMessage.InviteParserResult parseInviteElement(ZimbraSoapContext lc, Account account, Element inviteElem)
        throws ServiceException {
            List<ZAttendee> atsToCancel = new ArrayList<ZAttendee>();

            ParseMimeMessage.InviteParserResult toRet =
                CalendarUtils.parseInviteForModify(account, getItemType(), inviteElem, mInv, atsToCancel, !mInv.hasRecurId());

            // send cancellations to any invitees who have been removed...
            if (atsToCancel.size() > 0)
                updateRemovedInvitees(lc, account, mmbox, mInv.getCalendarItem(), mInv, atsToCancel);
            
            return toRet;
        }
    };

    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Account acct = getRequestedAccount(zsc);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = zsc.getOperationContext();
        
        ItemId iid = new ItemId(request.getAttribute(MailService.A_ID), zsc);
        int compNum = (int) request.getAttributeLong(MailService.E_INVITE_COMPONENT, 0);
        sLog.info("<ModifyCalendarItem id=" + iid + " comp=" + compNum + ">");
        
        synchronized(mbox) {
            CalendarItem calItem = mbox.getCalendarItemById(octxt, iid.getId());
            if (calItem == null) {
                throw MailServiceException.NO_SUCH_CALITEM(iid.toString(), "Could not find calendar item");
            }
            Invite inv = calItem.getInvite(iid.getSubpartId(), compNum);
            if (inv == null) {
                throw MailServiceException.INVITE_OUT_OF_DATE(iid.toString());
            }
            
            // response
            Element response = getResponseElement(zsc);
            
            return modifyCalendarItem(zsc, octxt, request, acct, mbox, calItem, inv, response);
        } // synchronized on mailbox                
    }

    private Element modifyCalendarItem(ZimbraSoapContext zsc, OperationContext octxt, Element request, Account acct, Mailbox mbox,
            CalendarItem calItem, Invite inv, Element response) throws ServiceException
    {
        // <M>
        Element msgElem = request.getElement(MailService.E_MSG);
        
        ModifyCalendarItemParser parser = new ModifyCalendarItemParser(mbox, inv);
        
        CalSendData dat = handleMsgElement(zsc, msgElem, acct, mbox, parser);
        
        // If we are sending this update to other people, then we MUST be the organizer!
        if (!inv.thisAcctIsOrganizer(acct)) {
            try {
                Address[] rcpts = dat.mMm.getAllRecipients();
                if (rcpts != null && rcpts.length > 0) {
                    throw MailServiceException.MUST_BE_ORGANIZER("ModifyCalendarItem");
                }
            } catch (MessagingException e) {
                throw ServiceException.FAILURE("Checking recipients of outgoing msg ", e);
            }
        }

        sendCalendarMessage(zsc, octxt, calItem.getFolderId(), acct, mbox, dat, response, false);

        return response;        
    }
}
