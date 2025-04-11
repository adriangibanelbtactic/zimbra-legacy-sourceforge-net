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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.calendar.FreeBusy;
import com.zimbra.cs.mailbox.calendar.IcalXmlStrMap;
import com.zimbra.cs.service.util.ParseMailboxID;
import com.zimbra.soap.Element;
import com.zimbra.soap.SoapFaultException;
import com.zimbra.soap.ZimbraSoapContext;

public class GetFreeBusy extends MailDocumentHandler {

    
//    <GetFreeBusyRequest s="date" e="date" [uid="id,..."]/>
//    <GetFreeBusyResponse>
//      <usr id="id">
//        <f s="date" e="date"/>*
//        <b s="date" e="date"/>*
//        <t s="date" e="date"/>*
//        <o s="date" e="date"/>*
//      </usr>  
//    <GetFreeBusyResponse>
//
//    (f)ree (b)usy (t)entative and (o)ut-of-office
    
    private static Log sLog = LogFactory.getLog(GetFreeBusy.class);
    
    private static final long MSEC_PER_DAY = 1000*60*60*24;
    
    private static final long MAX_PERIOD_SIZE_IN_DAYS = 200;

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zc = getZimbraSoapContext(context);
        
        long rangeStart = request.getAttributeLong(MailService.A_CAL_START_TIME);
        long rangeEnd = request.getAttributeLong(MailService.A_CAL_END_TIME);
        
        if (rangeEnd < rangeStart)
            throw ServiceException.INVALID_REQUEST("End time must be after Start time", null);

        long days = (rangeEnd - rangeStart) / MSEC_PER_DAY;
        if (days > MAX_PERIOD_SIZE_IN_DAYS)
            throw ServiceException.INVALID_REQUEST("Requested range is too large (Maximum "+MAX_PERIOD_SIZE_IN_DAYS+" days)", null);

        Element response = getResponseElement(zc);
        
        String idParam = request.getAttribute(MailService.A_UID);
        
        List<ParseMailboxID> local = new ArrayList<ParseMailboxID>();
        Map<String, StringBuilder> remote = new HashMap<String, StringBuilder>();
        partitionItems(zc, response, rangeStart, rangeEnd, idParam, local, remote);
        proxyRemoteItems(context, zc, response, rangeStart, rangeEnd, remote);
        
        if (!local.isEmpty()) {
            for (ParseMailboxID id : local) {
                try {
                    getForOneMailbox(zc, response, id, rangeStart, rangeEnd);
                } catch (ServiceException e) {
                    addFailureInfo(response, rangeStart, rangeEnd, id.toString(), e);
                }
            }
        }
        return response;
    }

    protected static void proxyRemoteItems(Map<String, Object> context, ZimbraSoapContext zc, Element response, long rangeStart, long rangeEnd, Map<String, StringBuilder> remote) {
        for (Map.Entry<String, StringBuilder> entry : remote.entrySet()) {
            // String server = entry.getKey();
            String paramStr = entry.getValue().toString();
            String[] idStrs = paramStr.split(",");

            try {
                Element req = zc.getRequestProtocol().getFactory().createElement(MailService.GET_FREE_BUSY_REQUEST);
                req.addAttribute(MailService.A_CAL_START_TIME, rangeStart);
                req.addAttribute(MailService.A_CAL_END_TIME, rangeEnd);
                req.addAttribute(MailService.A_UID, paramStr);

                // hack: use the ID of the first user 
                Account acct = Provisioning.getInstance().get(AccountBy.name, idStrs[0]);

                Element remoteResponse = proxyRequest(req, context, acct.getId());
                for (Element thisElt : remoteResponse.listElements())
                    response.addElement(thisElt.detach());
            } catch (SoapFaultException e) {
                for (int i = 0; i < idStrs.length; i++)
                    addFailureInfo(response, rangeStart, rangeEnd, idStrs[i], e);
            } catch (ServiceException e) {
                for (int i = 0; i < idStrs.length; i++)
                    addFailureInfo(response, rangeStart, rangeEnd, idStrs[i], e);
            }
        }
    }
    
    protected static void partitionItems(ZimbraSoapContext zc, Element response, long rangeStart, long rangeEnd,
                                         String idParam, List<ParseMailboxID> local, Map<String, StringBuilder> remote) {
        String[] idStrs = idParam.split(",");
        for (int i = 0; i < idStrs.length; i++) {
            try {
                ParseMailboxID id = ParseMailboxID.parse(idStrs[i]);
                if (id != null) {
                    if (id.isLocal()) {
                        local.add(id);
                    } else {
                        String serverId = id.getServer();
                        
                        assert(serverId != null);
                            
                        StringBuilder remoteStr = remote.get(serverId);
                        if (remoteStr == null) {
                            remoteStr = new StringBuilder(idStrs[i]);
                            remote.put(serverId, remoteStr);
                        } else {
                            remoteStr.append(",").append(idStrs[i]);
                        }
                    }
                }
            } catch (ServiceException e) {
                addFailureInfo(response, rangeStart, rangeEnd, idStrs[i], e);
            }
            
        }
    }

    protected static void addFailureInfo(Element response, long rangeStart, long rangeEnd, String idStr, Exception e) {
        sLog.debug("Could not get FreeBusy data for id " + idStr, e);
        Element usr = response.addElement(MailService.E_FREEBUSY_USER);
        usr.addAttribute(MailService.A_ID, idStr);
        usr.addElement(MailService.E_FREEBUSY_NO_DATA)
           .addAttribute(MailService.A_CAL_START_TIME, rangeStart)
           .addAttribute(MailService.A_CAL_END_TIME, rangeEnd);
    }
    
    protected static void getForOneMailbox(ZimbraSoapContext zc, Element response, ParseMailboxID id, long start, long end)
    throws ServiceException {
        if (id.isLocal()) {
            Element mbxResp = response.addElement(MailService.E_FREEBUSY_USER);
            mbxResp.addAttribute(MailService.A_ID,id.getString());
            
            Mailbox mbox = id.getMailbox();

            FreeBusy fb = mbox.getFreeBusy(start, end);
            
            for (Iterator iter = fb.iterator(); iter.hasNext(); ) {
                FreeBusy.Interval cur = (FreeBusy.Interval)iter.next();
                String status = cur.getStatus();
                Element elt;
                if (status.equals(IcalXmlStrMap.FBTYPE_FREE)) {
                    elt = mbxResp.addElement(MailService.E_FREEBUSY_FREE);
                } else if (status.equals(IcalXmlStrMap.FBTYPE_BUSY)) {
                    elt = mbxResp.addElement(MailService.E_FREEBUSY_BUSY);
                } else if (status.equals(IcalXmlStrMap.FBTYPE_BUSY_TENTATIVE)) {
                    elt = mbxResp.addElement(MailService.E_FREEBUSY_BUSY_TENTATIVE);
                } else if (status.equals(IcalXmlStrMap.FBTYPE_BUSY_UNAVAILABLE)) {
                    elt = mbxResp.addElement(MailService.E_FREEBUSY_BUSY_UNAVAILABLE);
                } else {
                    assert(false);
                    elt = null;
                }
                
                elt.addAttribute(MailService.A_CAL_START_TIME, cur.getStart());
                elt.addAttribute(MailService.A_CAL_END_TIME, cur.getEnd());
            }
        } else {
            throw new IllegalArgumentException("REMOTE MAILBOXES NOT SUPPORTED YET\n");
        }
    }
    
}
