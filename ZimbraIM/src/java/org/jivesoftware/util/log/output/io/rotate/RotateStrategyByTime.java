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
package org.jivesoftware.util.log.output.io.rotate;

import java.io.File;

/**
 * rotation stragety based when log writting started.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class RotateStrategyByTime
        implements RotateStrategy {
    ///time interval when rotation is triggered.
    private long m_timeInterval;

    ///time when logging started.
    private long m_startingTime;

    ///rotation count.
    private long m_currentRotation;

    /**
     * Rotate logs by time.
     * By default do log rotation every 24 hours
     */
    public RotateStrategyByTime() {
        this(1000 * 60 * 60 * 24);
    }

    /**
     * Rotate logs by time.
     *
     * @param timeInterval rotate after time-interval [ms] has expired
     */
    public RotateStrategyByTime(final long timeInterval) {
        m_startingTime = System.currentTimeMillis();
        m_currentRotation = 0;
        m_timeInterval = timeInterval;
    }

    /**
     * reset interval history counters.
     */
    public void reset() {
        m_startingTime = System.currentTimeMillis();
        m_currentRotation = 0;
    }

    /**
     * Check if now a log rotation is neccessary.
     * If
     * <code>(current_time - m_startingTime) / m_timeInterval &gt; m_currentRotation </code>
     * rotation is needed.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        final long newRotation =
                (System.currentTimeMillis() - m_startingTime) / m_timeInterval;

        if (newRotation > m_currentRotation) {
            m_currentRotation = newRotation;
            return true;
        }
        else {
            return false;
        }
    }
}


