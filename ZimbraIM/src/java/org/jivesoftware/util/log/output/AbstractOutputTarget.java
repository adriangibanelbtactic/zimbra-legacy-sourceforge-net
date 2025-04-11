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
package org.jivesoftware.util.log.output;

import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.format.Formatter;

/**
 * Abstract output target.
 * Any new output target that is writing to a single connected
 * resource should extend this class directly or indirectly.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public abstract class AbstractOutputTarget
        extends AbstractTarget {
    /**
     * Formatter for target.
     */
    private Formatter m_formatter;

    /**
     * Parameterless constructor.
     */
    public AbstractOutputTarget() {
    }

    public AbstractOutputTarget(final Formatter formatter) {
        m_formatter = formatter;
    }

    /**
     * Retrieve the associated formatter.
     *
     * @return the formatter
     * @deprecated Access to formatter is not advised and this method will be removed
     *             in future iterations. It remains only for backwards compatability.
     */
    public synchronized Formatter getFormatter() {
        return m_formatter;
    }

    /**
     * Set the formatter.
     *
     * @param formatter the formatter
     * @deprecated In future this method will become protected access.
     */
    public synchronized void setFormatter(final Formatter formatter) {
        writeTail();
        m_formatter = formatter;
        writeHead();
    }

    /**
     * Abstract method to send data.
     *
     * @param data the data to be output
     */
    protected void write(final String data) {
        output(data);
    }

    /**
     * Abstract method that will output event.
     *
     * @param data the data to be output
     * @deprecated User should overide send() instead of output(). Output exists
     *             for backwards compatability and will be removed in future.
     */
    protected void output(final String data) {
    }

    protected void doProcessEvent(LogEvent event) {
        final String data = format(event);
        write(data);
    }

    /**
     * Startup log session.
     */
    protected synchronized void open() {
        if (!isOpen()) {
            super.open();
            writeHead();
        }
    }

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        if (isOpen()) {
            writeTail();
            super.close();
        }
    }

    /**
     * Helper method to format an event into a string, using the formatter if available.
     *
     * @param event the LogEvent
     * @return the formatted string
     */
    private String format(final LogEvent event) {
        if (null != m_formatter) {
            return m_formatter.format(event);
        }
        else {
            return event.toString();
        }
    }

    /**
     * Helper method to send out log head.
     * The head initiates a session of logging.
     */
    private void writeHead() {
        if (!isOpen()) return;

        final String head = getHead();
        if (null != head) {
            write(head);
        }
    }

    /**
     * Helper method to send out log tail.
     * The tail completes a session of logging.
     */
    private void writeTail() {
        if (!isOpen()) return;

        final String tail = getTail();
        if (null != tail) {
            write(tail);
        }
    }

    /**
     * Helper method to retrieve head for log session.
     * TODO: Extract from formatter
     *
     * @return the head string
     */
    private String getHead() {
        return null;
    }

    /**
     * Helper method to retrieve tail for log session.
     * TODO: Extract from formatter
     *
     * @return the head string
     */
    private String getTail() {
        return null;
    }
}
