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
import java.io.OutputStream;

/**
 * A basic target that writes to an OutputStream.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class StreamTarget extends AbstractOutputTarget {
    ///OutputStream we are writing to
    private OutputStream m_outputStream;

    /**
     * Constructor that writes to a stream and uses a particular formatter.
     *
     * @param outputStream the OutputStream to send to
     * @param formatter    the Formatter to use
     */
    public StreamTarget(final OutputStream outputStream, final Formatter formatter) {
        super(formatter);

        if (null != outputStream) {
            setOutputStream(outputStream);
            open();
        }
    }

    /**
     * Set the output stream.
     * Close down old stream and send tail if appropriate.
     *
     * @param outputStream the new OutputStream
     */
    protected synchronized void setOutputStream(final OutputStream outputStream) {
        if (null == outputStream) {
            throw new NullPointerException("outputStream property must not be null");
        }

        m_outputStream = outputStream;
    }

    /**
     * Abstract method that will output event.
     *
     * @param data the data to be output
     */
    protected synchronized void write(final String data) {
        //Cache method local version
        //so that can be replaced in another thread
        final OutputStream outputStream = m_outputStream;

        if (null == outputStream) {
            final String message = "Attempted to send data '" + data + "' to Null OutputStream";
            getErrorHandler().error(message, null, null);
            return;
        }

        try {
            //TODO: We should be able to specify encoding???
            outputStream.write(data.getBytes("UTF-8"));
            outputStream.flush();
        }
        catch (final IOException ioe) {
            final String message = "Error writing data '" + data + "' to OutputStream";
            getErrorHandler().error(message, ioe, null);
        }
    }

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        super.close();
        shutdownStream();
    }

    /**
     * Shutdown output stream.
     */
    protected synchronized void shutdownStream() {
        final OutputStream outputStream = m_outputStream;
        m_outputStream = null;

        try {
            if (null != outputStream) {
                outputStream.close();
            }
        }
        catch (final IOException ioe) {
            getErrorHandler().error("Error closing OutputStream", ioe, null);
        }
    }
}
