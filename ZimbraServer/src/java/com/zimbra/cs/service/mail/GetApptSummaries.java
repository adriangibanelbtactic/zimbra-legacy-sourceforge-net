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

/*
 * Created on Feb 17, 2005
 */
package com.zimbra.cs.service.mail;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Appointment;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.InviteInfo;
import com.zimbra.cs.mailbox.calendar.ParsedDuration;
import com.zimbra.cs.stats.StopWatch;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraContext;
import com.zimbra.soap.WriteOpDocumentHandler;


/**
 * @author tim
 */
public class GetApptSummaries extends WriteOpDocumentHandler {

    private static Log mLog = LogFactory.getLog(GetApptSummaries.class);
    private static StopWatch sWatch = StopWatch.getInstance("GetApptSummaries");

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailService.A_FOLDER };
    private static final String[] RESPONSE_ITEM_PATH = new String[] { };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return true; }
    protected String[] getResponseItemPath()  { return RESPONSE_ITEM_PATH; }

    private static final String DEFAULT_FOLDER = "" + Mailbox.ID_AUTO_INCREMENT;
    
    private static final long MSEC_PER_DAY = 1000*60*60*24;
    private static final long MAX_PERIOD_SIZE_IN_DAYS = 200; 

    public Element handle(Element request, Map context) throws ServiceException {
        long startTime = sWatch.start();
        try {
            ZimbraContext lc = getZimbraContext(context);
            Mailbox mbox = getRequestedMailbox(lc);
            Account acct = getRequestedAccount(lc);

            long rangeStart = request.getAttributeLong(MailService.A_APPT_START_TIME);
            long rangeEnd = request.getAttributeLong(MailService.A_APPT_END_TIME);
            
            if (rangeEnd < rangeStart) {
                throw ServiceException.INVALID_REQUEST("End time must be after Start time", null);
            }
            
            long days = (rangeEnd-rangeStart)/MSEC_PER_DAY;
            if (days > MAX_PERIOD_SIZE_IN_DAYS) {
                throw ServiceException.INVALID_REQUEST("Requested range is too large (Maximum "+MAX_PERIOD_SIZE_IN_DAYS+" days)", null);
            }
            
            
            ItemId iidFolder = new ItemId(request.getAttribute(MailService.A_FOLDER, DEFAULT_FOLDER), lc);

            Collection appointments = mbox.getAppointmentsForRange(lc.getOperationContext(), rangeStart, rangeEnd, iidFolder.getId(), null);

            Element response = lc.createElement(MailService.GET_APPT_SUMMARIES_RESPONSE);
            for (Iterator aptIter = appointments.iterator(); aptIter.hasNext(); ) {
                Appointment appointment = (Appointment) aptIter.next();
                try {
                    Element apptElt = lc.createElement(MailService.E_APPOINTMENT);
                    apptElt.addAttribute("x_uid", appointment.getUid());
                    
                    Invite defaultInvite = appointment.getDefaultInvite();
                    
                    if (defaultInvite == null) {
                        mLog.info("Could not load defaultinfo for appointment with id="+appointment.getId()+" SKIPPING");
                        continue; // 
                    }
                    
                    ParsedDuration defDuration = defaultInvite.getEffectiveDuration();
                    if (defDuration == null) {
                        mLog.info("Could not load effective default duration for appointment id="+appointment.getId()+" SKIPPING");
                        continue;
                    }
                    
                    long defDurationMsecs = defDuration.getDurationAsMsecs(defaultInvite.getStartTime().getDate());
                    
                    boolean defIsOrg = defaultInvite.thisAcctIsOrganizer(acct);
                    
                    String defaultFba = appointment.getEffectiveFreeBusyActual(acct, defaultInvite, null);
                    String defaultPtSt = appointment.getEffectivePartStat(acct, defaultInvite, null); 
                    
                    // add all the instances:
                    boolean someInRange = false;
                    Collection instances = appointment.expandInstances(rangeStart, rangeEnd); 
                    for (Iterator instIter = instances.iterator(); instIter.hasNext(); ) {
                        Appointment.Instance inst = (Appointment.Instance) instIter.next();
                        try {
                            InviteInfo invId = inst.getInviteInfo();
                            Invite inv = appointment.getInvite(invId.getMsgId(), invId.getComponentId());
                            
                            // figure out which fields are different from the default and put their data here...
                            ParsedDuration invDuration = inv.getEffectiveDuration();
                            long instStart = inst.getStart();
                            
                            if (instStart < rangeEnd && (invDuration.addToTime(instStart))>rangeStart) {
                                someInRange = true;
                            } else {
                                continue;
                            }
                            
                            
                            Element instElt = apptElt.addElement(MailService.E_INSTANCE);
                            
                            instElt.addAttribute(MailService.A_APPT_START_TIME, instStart);
                            
                            String instFba = appointment.getEffectiveFreeBusyActual(acct, inv, inst);
                            String instPtSt = appointment.getEffectivePartStat(acct, inv, inst);
                            if (!defaultFba.equals(instFba)) {
                                instElt.addAttribute(MailService.A_APPT_FREEBUSY_ACTUAL, instFba); 
                            }
                            
                            if (inst.isException()) {
                                instElt.addAttribute(MailService.A_APPT_IS_EXCEPTION, true);
                                
                                // testing temp removeme TODO
                                instElt.addAttribute("x_recurid", inv.getRecurId().toString());
                                
                                if ((defaultInvite.getMailItemId() != invId.getMsgId()) ||
                                        (defaultInvite.getComponentNum() != invId.getComponentId())) 
                                {
                                    instElt.addAttribute(MailService.A_APPT_INV_ID, lc.formatItemId(appointment, inst.getMailItemId()));

                                    instElt.addAttribute(MailService.A_APPT_COMPONENT_NUM, inst.getComponentNum());

                                    // fragment has already been sanitized...
                                    String frag = inv.getFragment();
                                    if (!frag.equals("")) {
                                        instElt.addAttribute(MailService.E_FRAG, frag, Element.DISP_CONTENT);
                                    }
                                }
                                
                                boolean thisInvIsOrg = inv.thisAcctIsOrganizer(acct);
                                if (thisInvIsOrg!= defIsOrg) {
                                    instElt.addAttribute(MailService.A_APPT_ISORG, thisInvIsOrg);
                                }
                                
                                
                                if (defDurationMsecs != inst.getEnd()-inst.getStart()) {
                                    instElt.addAttribute(MailService.A_APPT_DURATION, inst.getEnd()-inst.getStart());
                                }
                                
                                if (!defaultInvite.getStatus().equals(inv.getStatus())) {
                                    instElt.addAttribute(MailService.A_APPT_STATUS, inv.getStatus());
                                }

                                if (!defaultPtSt.equals(instPtSt)) {
                                    instElt.addAttribute(MailService.A_APPT_PARTSTAT, instPtSt); 
                                }

                                if (!defaultInvite.getFreeBusy().equals(inv.getFreeBusy())) {
                                    instElt.addAttribute(MailService.A_APPT_FREEBUSY, inv.getFreeBusy());
                                }
                                
                                if (!defaultInvite.getTransparency().equals(inv.getTransparency())) {
                                    instElt.addAttribute(MailService.A_APPT_TRANSPARENCY, inv.getTransparency());
                                }
                                
                                if (!defaultInvite.getName().equals(inv.getName())) {
                                    instElt.addAttribute(MailService.A_NAME, inv.getName());
                                }
                                
                                if (!defaultInvite.getLocation().equals(inv.getLocation())) {
                                    instElt.addAttribute(MailService.A_APPT_LOCATION, inv.getLocation());
                                }
                                
                                if (defaultInvite.isAllDayEvent() != inv.isAllDayEvent()) {
                                    instElt.addAttribute(MailService.A_APPT_ALLDAY, inv.isAllDayEvent());
                                }
                                if (defaultInvite.hasOtherAttendees() != inv.hasOtherAttendees()) {
                                    instElt.addAttribute(MailService.A_APPT_OTHER_ATTENDEES, inv.hasOtherAttendees());
                                }
                                if (defaultInvite.hasAlarm() != inv.hasAlarm()) {
                                    instElt.addAttribute(MailService.A_APPT_ALARM, inv.hasAlarm());
                                }
                                if (defaultInvite.isRecurrence() != inv.isRecurrence()) {
                                    instElt.addAttribute(MailService.A_APPT_RECUR, inv.isRecurrence());
                                }
                            }
                        } catch (MailServiceException.NoSuchItemException e) {
                            mLog.info("Error could not get instance "+inst.getMailItemId()+"-"+inst.getComponentNum()+
                                    " for appt "+appointment.getId(), e);
                        }
                    } // iterate all the instances
                    
                    
                    if (someInRange) { // if we found any appointments at all, we have to encode the "Default" data here
                        apptElt.addAttribute(MailService.A_APPT_STATUS, defaultInvite.getStatus());
                        apptElt.addAttribute(MailService.A_APPT_PARTSTAT, defaultPtSt);
                        apptElt.addAttribute(MailService.A_APPT_FREEBUSY, defaultInvite.getFreeBusy());
                        apptElt.addAttribute(MailService.A_APPT_FREEBUSY_ACTUAL, defaultFba);
                        apptElt.addAttribute(MailService.A_APPT_TRANSPARENCY, defaultInvite.getTransparency());
                        apptElt.addAttribute(MailService.A_APPT_ISORG, defIsOrg);
                        
                        apptElt.addAttribute(MailService.A_APPT_DURATION, defDurationMsecs);
                        apptElt.addAttribute(MailService.A_NAME, defaultInvite.getName());
                        apptElt.addAttribute(MailService.A_APPT_LOCATION, defaultInvite.getLocation());

                        apptElt.addAttribute(MailService.A_ID, lc.formatItemId(appointment));
                        apptElt.addAttribute(MailService.A_FOLDER, lc.formatItemId(appointment.getFolderId()));

                        apptElt.addAttribute(MailService.A_APPT_INV_ID, lc.formatItemId(appointment, defaultInvite.getMailItemId()));

                        apptElt.addAttribute(MailService.A_APPT_COMPONENT_NUM, defaultInvite.getComponentNum());
                        
                        if (defaultInvite.isAllDayEvent()) {
                            apptElt.addAttribute(MailService.A_APPT_ALLDAY, defaultInvite.isAllDayEvent());
                        }
                        if (defaultInvite.hasOtherAttendees()) {
                            apptElt.addAttribute(MailService.A_APPT_OTHER_ATTENDEES, defaultInvite.hasOtherAttendees());
                        }
                        if (defaultInvite.hasAlarm()) {
                            apptElt.addAttribute(MailService.A_APPT_ALARM, defaultInvite.hasAlarm());
                        }
                        if (defaultInvite.isRecurrence()) {
                            apptElt.addAttribute(MailService.A_APPT_RECUR, defaultInvite.isRecurrence());
                        }
                        
                        { 
                            // fragment has already been sanitized...
                            String fragment = defaultInvite.getFragment();
                            if (!fragment.equals("")) {
                                apptElt.addAttribute(MailService.E_FRAG, fragment, Element.DISP_CONTENT);
                            }
                        }
                        
                        response.addElement(apptElt);
                    }
                    
                } catch(MailServiceException.NoSuchItemException e) {
                    mLog.info("Error could not get default invite for Appt: "+ appointment.getId(), e);
                } catch (RuntimeException e) {
                    mLog.info("Caught Exception "+e+ " while getting summary info for Appt: "+appointment.getId(), e);
                }
            }
            
            return response;
            
        } finally {
            sWatch.stop(startTime);
        }
    }
}
