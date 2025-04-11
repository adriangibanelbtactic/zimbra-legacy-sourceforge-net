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
 * Hierarchical Rotation stragety.
 * This object is initialised with several rotation strategy objects.
 * The <code>isRotationNeeded</code> method checks the first rotation
 * strategy object. If a rotation is needed, this result is returned.
 * If not the next rotation strategy object is asked and so on.
 *
 * @author <a href="mailto:cziegeler@apache.org">Carsten Ziegeler</a>
 */
public class OrRotateStrategy
        implements RotateStrategy {
    private RotateStrategy[] m_strategies;

    /**
     * The rotation strategy used. This marker is required for the reset()
     * method.
     */
    private int m_usedRotation = -1;

    /**
     * Constructor
     */
    public OrRotateStrategy(final RotateStrategy[] strategies) {
        this.m_strategies = strategies;
    }

    /**
     * reset.
     */
    public void reset() {
        if (-1 != m_usedRotation) {
            m_strategies[m_usedRotation].reset();
            m_usedRotation = -1;
        }
    }

    /**
     * check if now a log rotation is neccessary.
     * This object is initialised with several rotation strategy objects.
     * The <code>isRotationNeeded</code> method checks the first rotation
     * strategy object. If a rotation is needed, this result is returned.
     * If not the next rotation strategy object is asked and so on.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        m_usedRotation = -1;

        if (null != m_strategies) {
            final int length = m_strategies.length;
            for (int i = 0; i < length; i++) {
                if (true == m_strategies[i].isRotationNeeded(data, file)) {
                    m_usedRotation = i;
                    return true;
                }
            }
        }

        return false;
    }
}

