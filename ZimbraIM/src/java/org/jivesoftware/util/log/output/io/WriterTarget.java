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
package org.jivesoftware.util.log.output.io;

import org.jivesoftware.util.log.format.Formatter;
import org.jivesoftware.util.log.output.AbstractOutputTarget;
import java.io.IOException;
import java.io.Writer;

/**
 * This target outputs to a writer.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class WriterTarget extends AbstractOutputTarget {

    private Writer m_output;

    /**
     * Construct target with a specific writer and formatter.
     *
     * @param writer    the writer
     * @param formatter the formatter
     */
    public WriterTarget(final Writer writer, final Formatter formatter) {
        super(formatter);

        if (null != writer) {
            setWriter(writer);
            open();
        }
    }

    /**
     * Set the writer.
     * Close down writer and send tail if appropriate.
     *
     * @param writer the new writer
     */
    protected synchronized void setWriter(final Writer writer) {
        if (null == writer) {
            throw new NullPointerException("writer property must not be null");
        }

        m_output = writer;
    }

    /**
     * Concrete implementation of output that writes out to underlying writer.
     *
     * @param data the data to output
     */
    protected void write(final String data) {
        try {
            m_output.write(data);
            m_output.flush();
        }
        catch (final IOException ioe) {
            getErrorHandler().error("Caught an IOException", ioe, null);
        }
    }

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        super.close();
        shutdownWriter();
    }

    /**
     * Shutdown Writer.
     */
    protected synchronized void shutdownWriter() {
        final Writer writer = m_output;
        m_output = null;

        try {
            if (null != writer) {
                writer.close();
            }
        }
        catch (final IOException ioe) {
            getErrorHandler().error("Error closing Writer", ioe, null);
        }
    }
}
