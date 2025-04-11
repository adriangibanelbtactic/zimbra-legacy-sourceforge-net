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
 * Rotation stragety based on size written to log file.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class RotateStrategyBySize
        implements RotateStrategy {
    private long m_maxSize;
    private long m_currentSize;

    /**
     * Rotate logs by size.
     * By default do log rotation after writing approx. 1MB of messages
     */
    public RotateStrategyBySize() {
        this(1024 * 1024);
    }

    /**
     * Rotate logs by size.
     *
     * @param maxSize rotate after writing max_size [byte] of messages
     */
    public RotateStrategyBySize(final long maxSize) {
        m_currentSize = 0;
        m_maxSize = maxSize;
    }

    /**
     * reset log size written so far.
     */
    public void reset() {
        m_currentSize = 0;
    }

    /**
     * Check if now a log rotation is neccessary.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        m_currentSize += data.length();
        if (m_currentSize >= m_maxSize) {
            m_currentSize = 0;
            return true;
        }
        else {
            return false;
        }
    }
}

