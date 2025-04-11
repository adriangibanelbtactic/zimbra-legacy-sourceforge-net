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
package org.jivesoftware.util.log.util;

import org.jivesoftware.util.log.Logger;
import org.jivesoftware.util.log.Priority;

/**
 * Redirect an output stream to a logger.
 * This class is useful to redirect standard output or
 * standard error to a Logger. An example use is
 * <p/>
 * <pre>
 * final OutputStreamLogger outputStream =
 *     new OutputStreamLogger( logger, Priority.DEBUG );
 * final PrintStream output = new PrintStream( outputStream, true );
 * <p/>
 * System.setOut( output );
 * </pre>
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @deprecated Use LoggerOutputStream as this class was misnamed.
 */
public class OutputStreamLogger
        extends LoggerOutputStream {

    /**
     * Construct logger to send to a particular logger at a particular priority.
     *
     * @param logger   the logger to send to
     * @param priority the priority at which to log
     * @deprecated Use LoggerOutputStream as this class was misnamed.
     */
    public OutputStreamLogger(final Logger logger,
                              final Priority priority) {
        super(logger, priority);
    }
}
