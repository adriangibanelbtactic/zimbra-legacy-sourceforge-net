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
 * strategy for naming log files based on appending revolving suffix.
 * <p/>
 * Heavily odified by Bruce Ritchie (Jive Software) to rotate along
 * the following strategy:
 * <p/>
 * current log file will always be the base File name
 * the next oldest file will be the _1 file
 * the next oldest file will be the _2 file
 * etc.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class RevolvingFileStrategy implements FileStrategy {

    ///max file prefix count
    private int maxCount;

    ///the base file name.
    private String baseFileName;

    public RevolvingFileStrategy(final String baseFileName, final int maxCount) {

        this.baseFileName = baseFileName;
        this.maxCount = maxCount;

        if (-1 == this.maxCount) {
            this.maxCount = 5;
        }
    }

    public File currentFile() {
        return new File(baseFileName);
    }

    /**
     * Calculate the real file name from the base filename.
     *
     * @return File the calculated file name
     */
    public File nextFile() {
        // go through all the possible filenames and delete/rename as necessary
        for (int i = maxCount; i > 0; i--) {
            File test = new File(baseFileName.substring(0, baseFileName.lastIndexOf('.')) +
                    "_" + i + baseFileName.substring(baseFileName.lastIndexOf('.')));

            if (i == maxCount && test.exists()) {
                test.delete();
            }

            if (test.exists()) {
                File r = new File(baseFileName.substring(0, baseFileName.lastIndexOf('.')) +
                        "_" + (i + 1) + baseFileName.substring(baseFileName.lastIndexOf('.')));
                test.renameTo(r);
            }
        }

        // rename the current file
        File current = new File(baseFileName);
        File first = new File(baseFileName.substring(0, baseFileName.lastIndexOf('.')) +
                "_1" + baseFileName.substring(baseFileName.lastIndexOf('.')));
        current.renameTo(first);

        // return the base filename
        return new File(baseFileName);
    }
}

