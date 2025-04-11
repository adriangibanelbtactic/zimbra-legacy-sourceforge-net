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

import org.jivesoftware.util.log.FilterTarget;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.LogTarget;

/**
 * Abstract implementation of FilterTarget.
 * A concrete implementation has to implement filter method.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public abstract class AbstractFilterTarget
        implements FilterTarget, LogTarget {
    //Log targets in filter chain
    private LogTarget m_targets[];

    /**
     * Add a new target to output chain.
     *
     * @param target the target
     */
    public void addTarget(final LogTarget target) {
        if (null == m_targets) {
            m_targets = new LogTarget[]{target};
        }
        else {
            final LogTarget oldTargets[] = m_targets;
            m_targets = new LogTarget[oldTargets.length + 1];
            System.arraycopy(oldTargets, 0, m_targets, 0, oldTargets.length);
            m_targets[m_targets.length - 1] = target;
        }
    }

    /**
     * Filter the log event.
     *
     * @param event the event
     * @return return true to discard event, false otherwise
     */
    protected abstract boolean filter(LogEvent event);

    /**
     * Process a log event
     *
     * @param event the log event
     */
    public void processEvent(final LogEvent event) {
        if (null == m_targets || filter(event))
            return;
        else {
            for (int i = 0; i < m_targets.length; i++) {
                m_targets[i].processEvent(event);
            }
        }
    }
}
