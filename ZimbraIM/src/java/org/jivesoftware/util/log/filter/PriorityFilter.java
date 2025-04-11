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
package org.jivesoftware.util.log.filter;

import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.Priority;

/**
 * Filters log events based on priority.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class PriorityFilter extends AbstractFilterTarget {

    ///Priority to filter against
    private Priority m_priority;

    /**
     * Constructor that sets the priority that is filtered against.
     *
     * @param priority the Priority
     */
    public PriorityFilter(final Priority priority) {
        m_priority = priority;
    }

    /**
     * Set priority used to filter.
     *
     * @param priority the priority to filter on
     */
    public void setPriority(final Priority priority) {
        m_priority = priority;
    }

    /**
     * Filter the log event based on priority.
     * <p/>
     * If LogEvent has a Lower priroity then discard it.
     *
     * @param event the event
     * @return return true to discard event, false otherwise
     */
    protected boolean filter(final LogEvent event) {
        return (!m_priority.isLower(event.getPriority()));
    }
}
