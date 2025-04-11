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
 * Strategy that checks condition under which file rotation is needed.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public interface RotateStrategy {
    /**
     * reset cumulative rotation history data.
     * Called after rotation.
     */
    void reset();

    /**
     * Check if a log rotation is neccessary at this time.
     *
     * @param data the serialized version of last message written to the log system
     * @param file the File that we are writing to
     * @return boolean return true if log rotation is neccessary, else false
     */
    boolean isRotationNeeded(String data, File file);
}

