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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.util.log.format;

import org.jivesoftware.util.log.LogEvent;

/**
 * This defines the interface for components that wish to serialize
 * LogEvents into Strings.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public interface Formatter {
    /**
     * Serialize log event into string.
     *
     * @param event the event
     * @return the formatted string
     */
    String format(LogEvent event);
}
