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
 * Created on 2005. 7. 11.
 */
package com.zimbra.cs.account;

import com.zimbra.cs.mailbox.calendar.ICalTimeZone;

/**
 * @author jhahm
 */
public interface WellKnownTimeZone extends NamedEntry {

    public ICalTimeZone toTimeZone();

    public String getStandardDtStart();

    public String getStandardOffset();

    public String getStandardRecurrenceRule();

    public String getDaylightDtStart();

    public String getDaylightOffset();

    public String getDaylightRecurrenceRule();
}
