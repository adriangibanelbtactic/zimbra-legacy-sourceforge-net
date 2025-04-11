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

/*
 * Created on Feb 17, 2005
 */
package com.zimbra.cs.service.mail;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Appointment;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.calendar.ICalTimeZone;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.InviteInfo;
import com.zimbra.cs.mailbox.calendar.ParsedDuration;
import com.zimbra.cs.mailbox.calendar.ZOrganizer;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.soap.ZimbraSoapContext;


/**
 * @author tim
 */
public class GetCalendarItemSummaries extends CalendarRequest {

    private static Log mLog = LogFactory.getLog(GetCalendarItemSummaries.class);

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.A_FOLDER };
    private static final String[] RESPONSE_ITEM_PATH = new String[] { };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return true; }
    protected String[] getResponseItemPath()  { return RESPONSE_ITEM_PATH; }

    private static final String DEFAULT_FOLDER = "" + Mailbox.ID_AUTO_INCREMENT;
    
    private static final long MSEC_PER_DAY = 1000*60*60*24;
    private static final long MAX_PERIOD_SIZE_IN_DAYS = 200;
    
    static class EncodeCalendarItemResult {
        Element element;
        int numInstancesExpanded;
    }
    
    /**
     * Encodes a calendar item 
     * 
     * @param parent
     * @param elementName
     *         name of element to add (MailConstants .E_APPOINTMENT or MailConstants.E_TASK)
     * @param rangeStart 
     *         start period to expand instances
     * @param rangeEnd
     *         end period to expand instances
     * @param newFormat
     *         temporary HACK - true: SearchRequest, false: GetAppointmentSummaries        
     * @return
     */
    static EncodeCalendarItemResult encodeCalendarItemInstances(ZimbraSoapContext lc, CalendarItem calItem,
        Account acct, long rangeStart, long rangeEnd, boolean newFormat) throws ServiceException 
    {
        EncodeCalendarItemResult toRet = new EncodeCalendarItemResult();
        ItemIdFormatter ifmt = new ItemIdFormatter(lc);

        try {
            boolean expandRanges = (rangeStart > 0 && rangeEnd > 0 && rangeStart < rangeEnd);
            
            boolean isAppointment = calItem instanceof Appointment;
            
            Element calItemElem = null; // don't initialize until we find at least one valid instance
            
            Invite defaultInvite = calItem.getDefaultInviteOrNull();
            
            if (defaultInvite == null) {
                mLog.info("Could not load defaultinfo for calendar item with id="+calItem.getId()+" SKIPPING");
                return toRet; 
            }

            ParsedDuration defDuration = defaultInvite.getEffectiveDuration();
            long defDurationMsecs = 0;
            if (defDuration != null && defaultInvite.getStartTime() != null)
                defDurationMsecs = defDuration.getDurationAsMsecs(defaultInvite.getStartTime().getDate());
            
            boolean defIsOrg = defaultInvite.isOrganizer();
            
            String defaultFba = null;
            if (calItem instanceof Appointment)
                defaultFba = ((Appointment) calItem).getEffectiveFreeBusyActual(defaultInvite, null);
            
            String defaultPtSt = calItem.getEffectivePartStat(defaultInvite, null);

            // add all the instances:
            int numInRange = 0;
            
            if (expandRanges) {
                Collection instances = calItem.expandInstances(rangeStart, rangeEnd); 
                for (Iterator instIter = instances.iterator(); instIter.hasNext(); ) {
                    CalendarItem.Instance inst = (CalendarItem.Instance) instIter.next();
                    try {
                        InviteInfo invId = inst.getInviteInfo();
                        Invite inv = calItem.getInvite(invId.getMsgId(), invId.getComponentId());
                        
                        // figure out which fields are different from the default and put their data here...
                        ParsedDuration invDuration = inv.getEffectiveDuration();
                        long instStart = inst.getStart();
                        
                        if (inst.isTimeless() ||
                                    (instStart < rangeEnd &&
                                                invDuration != null &&
                                                (invDuration.addToTime(instStart)) > rangeStart)) {
                            numInRange++;
                        } else {
                            continue;
                        }

                        if (calItemElem == null) {
                            calItemElem = lc.createElement(isAppointment ? MailConstants.E_APPOINTMENT : MailConstants.E_TASK);

//                            if (!newFormat) {
                                calItemElem.addAttribute("x_uid", calItem.getUid());
                                
                                    // flags and tags
                                String flags = calItem.getFlagString();
                                if (flags != null && !flags.equals(""))
                                    calItemElem.addAttribute(MailConstants.A_FLAGS, flags);
                                String tags = calItem.getTagString();
                                if (tags != null && !tags.equals(""))
                                    calItemElem.addAttribute(MailConstants.A_TAGS, tags);
//                            }

                            // Organizer
                            if (inv.hasOrganizer()) {
                                ZOrganizer org = inv.getOrganizer();
                                Element orgElt = calItemElem.addUniqueElement(MailConstants.E_CAL_ORGANIZER);
                                String str = org.getAddress();
                                orgElt.addAttribute(MailConstants.A_ADDRESS, str);
//                                orgElt.addAttribute(MailConstants.A_URL, str);  // for backward compatibility
                                if (org.hasCn())
                                    orgElt.addAttribute(MailConstants.A_DISPLAY, org.getCn());
                                if (org.hasSentBy())
                                    orgElt.addAttribute(MailConstants.A_CAL_SENTBY, org.getSentBy());
//                                if (org.hasDir())
//                                    orgElt.addAttribute(MailConstants.A_CAL_DIR, org.getDir());
//                                if (org.hasLanguage())
//                                    orgElt.addAttribute(MailConstants.A_CAL_LANGUAGE, org.getLanguage());
                            }
                        }

                        Element instElt = calItemElem.addElement(MailConstants.E_INSTANCE);

                        if (!inst.isTimeless()) {
                            instElt.addAttribute(MailConstants.A_CAL_START_TIME, instStart);
                            if (inv.getStartTime() != null) {
                                ICalTimeZone instTz = inv.getStartTime().getTimeZone();
                                if (inv.isAllDayEvent()) {
                                    long offset = instTz.getOffset(instStart);
                                    instElt.addAttribute(MailConstants.A_CAL_TZ_OFFSET, offset);
                                }
                            }
                        }

                        if (isAppointment && inv.isEvent()) {
                            String instFba = ((Appointment) calItem).getEffectiveFreeBusyActual(inv, inst);
                            if (instFba != null && !instFba.equals(defaultFba))
                                instElt.addAttribute(MailConstants.A_APPT_FREEBUSY_ACTUAL, instFba);
                        }
                        String instPtSt = calItem.getEffectivePartStat(inv, inst);
                        if (!defaultPtSt.equals(instPtSt)) {
                            instElt.addAttribute(MailConstants.A_CAL_PARTSTAT, instPtSt);
                        }

                        if (inst.isException()) {

                            instElt.addAttribute(MailConstants.A_CAL_IS_EXCEPTION, true);

                            if ((defaultInvite.getMailItemId() != invId.getMsgId()) || (defaultInvite.getComponentNum() != invId.getComponentId())) {
                                instElt.addAttribute(MailConstants.A_CAL_INV_ID, ifmt.formatItemId(calItem, inst.getMailItemId()));

                                instElt.addAttribute(MailConstants.A_CAL_COMPONENT_NUM, inst.getComponentNum());

                                // fragment has already been sanitized...
                                String frag = inv.getFragment();
                                if (!frag.equals("")) {
                                    instElt.addAttribute(MailConstants.E_FRAG, frag, Element.Disposition.CONTENT);
                                }
                            }

                            boolean thisInvIsOrg = inv.isOrganizer();
                            if (thisInvIsOrg!= defIsOrg) {
                                instElt.addAttribute(MailConstants.A_CAL_ISORG, thisInvIsOrg);
                            }


                            if (!inst.isTimeless() && defDurationMsecs != inst.getEnd()-inst.getStart()) {
                                instElt.addAttribute(newFormat ? MailConstants.A_CAL_NEW_DURATION : MailConstants.A_CAL_DURATION, inst.getEnd()-inst.getStart());
                            }

                            if (!defaultInvite.getStatus().equals(inv.getStatus())) {
                                instElt.addAttribute(MailConstants.A_CAL_STATUS, inv.getStatus());
                            }

                            String prio = inv.getPriority();
                            if (prio != null && !prio.equals(defaultInvite.getPriority())) {
                                instElt.addAttribute(MailConstants.A_CAL_PRIORITY, prio);
                            }

                            if (inv.isEvent()) {
                                if (!defaultInvite.getFreeBusy().equals(inv.getFreeBusy())) {
                                    instElt.addAttribute(MailConstants.A_APPT_FREEBUSY, inv.getFreeBusy());
                                }
                                if (!defaultInvite.getTransparency().equals(inv.getTransparency())) {
                                    instElt.addAttribute(MailConstants.A_APPT_TRANSPARENCY, inv.getTransparency());
                                }
                            }

                            if (inv.isTodo()) {
                                String pctComplete = inv.getPercentComplete();
                                if (pctComplete != null && !pctComplete.equals(defaultInvite.getPercentComplete())) {
                                    instElt.addAttribute(MailConstants.A_TASK_PERCENT_COMPLETE, pctComplete);
                                }
                            }

                            if (!defaultInvite.getName().equals(inv.getName())) {
                                instElt.addAttribute(MailConstants.A_NAME, inv.getName());
                            }

                            if (!defaultInvite.getLocation().equals(inv.getLocation())) {
                                instElt.addAttribute(MailConstants.A_CAL_LOCATION, inv.getLocation());
                            }

                            if (defaultInvite.isAllDayEvent() != inv.isAllDayEvent()) {
                                instElt.addAttribute(MailConstants.A_CAL_ALLDAY, inv.isAllDayEvent());
                            }
                            if (defaultInvite.hasOtherAttendees() != inv.hasOtherAttendees()) {
                                instElt.addAttribute(MailConstants.A_CAL_OTHER_ATTENDEES, inv.hasOtherAttendees());
                            }
                            if (defaultInvite.hasAlarm() != inv.hasAlarm()) {
                                instElt.addAttribute(MailConstants.A_CAL_ALARM, inv.hasAlarm());
                            }
                            if (defaultInvite.isRecurrence() != inv.isRecurrence()) {
                                instElt.addAttribute(MailConstants.A_CAL_RECUR, inv.isRecurrence());
                            }
                        } else {
                            // A non-exception instance can have duration that is different from
                            // the default duration due to daylight savings time transitions.
                            if (!inst.isTimeless() && defDurationMsecs != inst.getEnd()-inst.getStart()) {
                                instElt.addAttribute(newFormat ? MailConstants.A_CAL_NEW_DURATION : MailConstants.A_CAL_DURATION, inst.getEnd()-inst.getStart());
                            }
                        }
                    } catch (MailServiceException.NoSuchItemException e) {
                        mLog.info("Error could not get instance "+inst.getMailItemId()+"-"+inst.getComponentNum()+
                            " for appt "+calItem.getId(), e);
                    }
                } // iterate all the instances
            } // if expandRanges

            
            if (!expandRanges || numInRange > 0) { // if we found any calItems at all, we have to encode the "Default" data here
                if (calItemElem == null) {
                    calItemElem = lc.createElement(isAppointment ? MailConstants.E_APPOINTMENT : MailConstants.E_TASK);
                    
//                    if (!newFormat) {
                        calItemElem.addAttribute("x_uid", calItem.getUid());
                        
                        // flags and tags
                        String flags = calItem.getFlagString();
                        if (flags != null && !flags.equals(""))
                            calItemElem.addAttribute(MailConstants.A_FLAGS, flags);
                        String tags = calItem.getTagString();
                        if (tags != null && !tags.equals(""))
                            calItemElem.addAttribute(MailConstants.A_TAGS, tags);
//                    }

                    // Organizer
                    if (defaultInvite.hasOrganizer()) {
                        ZOrganizer org = defaultInvite.getOrganizer();
                        Element orgElt = calItemElem.addUniqueElement(MailConstants.E_CAL_ORGANIZER);
                        String str = org.getAddress();
                        orgElt.addAttribute(MailConstants.A_ADDRESS, str);
//                      orgElt.addAttribute(MailConstants.A_URL, str);  // for backward compatibility
                        if (org.hasCn())
                          orgElt.addAttribute(MailConstants.A_DISPLAY, org.getCn());
                        if (org.hasSentBy())
                            orgElt.addAttribute(MailConstants.A_CAL_SENTBY, org.getSentBy());
//                      if (org.hasDir())
//                      orgElt.addAttribute(MailConstants.A_CAL_DIR, org.getDir());
//                      if (org.hasLanguage())
//                      orgElt.addAttribute(MailConstants.A_CAL_LANGUAGE, org.getLanguage());
                    }
                }
                
                calItemElem.addAttribute(MailConstants.A_CAL_STATUS, defaultInvite.getStatus());
                String defaultPriority = defaultInvite.getPriority();
                if (defaultPriority != null)
                    calItemElem.addAttribute(MailConstants.A_CAL_PRIORITY, defaultPriority);
                calItemElem.addAttribute(MailConstants.A_CAL_PARTSTAT, defaultPtSt);
                if (defaultInvite.isEvent()) {
                    calItemElem.addAttribute(MailConstants.A_APPT_FREEBUSY, defaultInvite.getFreeBusy());
                    calItemElem.addAttribute(MailConstants.A_APPT_FREEBUSY_ACTUAL, defaultFba);
                    calItemElem.addAttribute(MailConstants.A_APPT_TRANSPARENCY, defaultInvite.getTransparency());
                }
                if (defaultInvite.isTodo()) {
                    String pctComplete = defaultInvite.getPercentComplete();
                    if (pctComplete != null)
                        calItemElem.addAttribute(MailConstants.A_TASK_PERCENT_COMPLETE, pctComplete);
                }
                calItemElem.addAttribute(MailConstants.A_CAL_ISORG, defIsOrg);

                if (defaultInvite.getStartTime() != null)
                    calItemElem.addAttribute(newFormat ? MailConstants.A_CAL_NEW_DURATION : MailConstants.A_CAL_DURATION, defDurationMsecs);
                calItemElem.addAttribute(MailConstants.A_NAME, defaultInvite.getName());
                calItemElem.addAttribute(MailConstants.A_CAL_LOCATION, defaultInvite.getLocation());
                
                calItemElem.addAttribute(MailConstants.A_ID, ifmt.formatItemId(calItem));
                calItemElem.addAttribute(MailConstants.A_FOLDER, ifmt.formatItemId(calItem.getFolderId()));
                
                calItemElem.addAttribute(MailConstants.A_CAL_INV_ID, ifmt.formatItemId(calItem, defaultInvite.getMailItemId()));
                
                calItemElem.addAttribute(MailConstants.A_CAL_COMPONENT_NUM, defaultInvite.getComponentNum());
                
                if (defaultInvite.isAllDayEvent()) {
                    calItemElem.addAttribute(MailConstants.A_CAL_ALLDAY, defaultInvite.isAllDayEvent());
                }
                if (defaultInvite.hasOtherAttendees()) {
                    calItemElem.addAttribute(MailConstants.A_CAL_OTHER_ATTENDEES, defaultInvite.hasOtherAttendees());
                }
                if (defaultInvite.hasAlarm()) {
                    calItemElem.addAttribute(MailConstants.A_CAL_ALARM, defaultInvite.hasAlarm());
                }
                if (defaultInvite.isRecurrence()) {
                    calItemElem.addAttribute(MailConstants.A_CAL_RECUR, defaultInvite.isRecurrence());
                }
                
                { 
                    // fragment has already been sanitized...
                    String fragment = defaultInvite.getFragment();
                    if (!fragment.equals("")) {
                        calItemElem.addAttribute(MailConstants.E_FRAG, fragment, Element.Disposition.CONTENT);
                    }
                }
                
                toRet.element = calItemElem;
            }
            toRet.numInstancesExpanded = numInRange;
        } catch(MailServiceException.NoSuchItemException e) {
            mLog.info("Error could not get default invite for calendar item: "+ calItem.getId(), e);
        } catch (RuntimeException e) {
            mLog.info("Caught Exception "+e+ " while getting summary info for calendar item: "+calItem.getId(), e);
        }
        
        return toRet;
    }
    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        Account acct = getRequestedAccount(zsc);
        
        long rangeStart = request.getAttributeLong(MailConstants.A_CAL_START_TIME);
        long rangeEnd = request.getAttributeLong(MailConstants.A_CAL_END_TIME);
        
        if (rangeEnd < rangeStart) {
            throw ServiceException.INVALID_REQUEST("End time must be after Start time", null);
        }
        
        long days = (rangeEnd-rangeStart)/MSEC_PER_DAY;
        if (days > MAX_PERIOD_SIZE_IN_DAYS) {
            throw ServiceException.INVALID_REQUEST("Requested range is too large (Maximum "+MAX_PERIOD_SIZE_IN_DAYS+" days)", null);
        }
        
        
        ItemId iidFolder = new ItemId(request.getAttribute(MailConstants.A_FOLDER, DEFAULT_FOLDER), zsc);
        
        Collection calItems = mbox.getCalendarItemsForRange(getItemType(), getOperationContext(zsc, context), rangeStart, rangeEnd, iidFolder.getId(), null);

        Element response = getResponseElement(zsc);
        
        int totalInstancesExpanded = 0;
        
        for (Iterator aptIter = calItems.iterator(); aptIter.hasNext(); ) {
            CalendarItem calItem = (CalendarItem) aptIter.next();
            
            EncodeCalendarItemResult encoded = encodeCalendarItemInstances(zsc, calItem, acct, rangeStart, rangeEnd, false);
//            assert(encoded.element != null || encoded.numInstancesExpanded==0);
            if (encoded.element != null)
                response.addElement(encoded.element);
            
            totalInstancesExpanded+=encoded.numInstancesExpanded;
        }
        
        return response;
    }
}
