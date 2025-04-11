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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZVCalendar;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.common.util.HttpUtil.Browser;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;


public class GetICal extends MailDocumentHandler {

    private static final String[] TARGET_OBJ_PATH = new String[] { MailConstants.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_OBJ_PATH; }

    /* (non-Javadoc)
     * @see com.zimbra.soap.DocumentHandler#handle(org.dom4j.Element, java.util.Map)
     */
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbx = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);

        String iidStr = request.getAttribute(MailConstants.A_ID, null);
        long rangeStart = request.getAttributeLong(MailConstants.A_CAL_START_TIME, -1);
        long rangeEnd = request.getAttributeLong(MailConstants.A_CAL_END_TIME, -1);
        
//        int compNum = (int)request.getAttributeLong(MailService.A_CAL_COMPONENT_NUM);
        int compNum = 0;

        Browser browser = HttpUtil.guessBrowser(zsc.getUserAgent());
        boolean useOutlookCompatMode = Browser.IE.equals(browser);
        try {
            try {
                ZVCalendar cal = null;
                ItemId iid = iidStr != null ? new ItemId(iidStr, zsc) : null;
                if (iid != null) {
                    CalendarItem calItem = mbx.getCalendarItemById(octxt, iid.getId());
                    if (calItem == null) {
                        throw MailServiceException.NO_SUCH_CALITEM(iid.toString(), "Could not find calendar item");
                    }
                    Invite inv = calItem.getInvite(iid.getSubpartId(), compNum);
                    if (inv == null) {
                        throw MailServiceException.INVITE_OUT_OF_DATE(iid.toString());
                    }
                    cal = inv.newToICalendar(useOutlookCompatMode);
                } else {
                    cal = mbx.getZCalendarForRange(octxt, rangeStart, rangeEnd, Mailbox.ID_FOLDER_CALENDAR, useOutlookCompatMode);
                }
                
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try {
                    OutputStreamWriter wout = new OutputStreamWriter(buf);
                    cal.toICalendar(wout);
                    wout.flush();
                    
                    Element response = getResponseElement(zsc);
                    
                    Element icalElt = response.addElement(MailConstants.E_CAL_ICAL);

                    if (iid != null)
                        icalElt.addAttribute(MailConstants.A_ID, new ItemIdFormatter(zsc).formatItemId(iid));
                    
                    icalElt.addText(buf.toString());
                    
                    return response;
                } catch (IOException e) {
                    throw ServiceException.FAILURE("IO Exception while outputing Calendar for Invite: "+ iidStr + "-" + compNum, e);
                }
            } catch(MailServiceException.NoSuchItemException e) {
                throw ServiceException.FAILURE("Error could get default invite for Invite: "+ iidStr + "-" + compNum, e);
            }
        } catch(MailServiceException.NoSuchItemException e) {
            throw ServiceException.FAILURE("No Such Invite Message: "+ iidStr, e);
        }
    }
}
