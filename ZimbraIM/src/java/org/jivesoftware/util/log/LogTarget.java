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
package org.jivesoftware.util.log;

/**
 * LogTarget is a class to encapsulate outputting LogEvent's.
 * This provides the base for all output and filter targets.
 * <p/>
 * Warning: If performance becomes a problem then this
 * interface will be rewritten as a abstract class.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public interface LogTarget {
    /**
     * Process a log event.
     * In NO case should this method ever throw an exception/error.
     * The reason is that logging is usually added for debugging/auditing
     * purposes and it would be unnaceptable to have your debugging
     * code cause more errors.
     *
     * @param event the event
     */
    void processEvent(LogEvent event);
}
