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

import org.jivesoftware.util.log.ErrorAware;
import org.jivesoftware.util.log.ErrorHandler;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.LogTarget;

/**
 * Abstract target.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public abstract class AbstractTarget implements LogTarget, ErrorAware {

    ///ErrorHandler used by target to delegate Error handling
    private ErrorHandler m_errorHandler;

    ///Flag indicating that log session is finished (aka target has been closed)
    private boolean m_isOpen;

    /**
     * Provide component with ErrorHandler.
     *
     * @param errorHandler the errorHandler
     */
    public synchronized void setErrorHandler(final ErrorHandler errorHandler) {
        m_errorHandler = errorHandler;
    }

    protected synchronized boolean isOpen() {
        return m_isOpen;
    }

    /**
     * Startup log session.
     */
    protected synchronized void open() {
        if (!isOpen()) {
            m_isOpen = true;
        }
    }

    /**
     * Process a log event, via formatting and outputting it.
     *
     * @param event the log event
     */
    public synchronized void processEvent(final LogEvent event) {
        if (!isOpen()) {
            getErrorHandler().error("Writing event to closed stream.", null, event);
            return;
        }

        try {
            doProcessEvent(event);
        }
        catch (final Throwable throwable) {
            getErrorHandler().error("Unknown error writing event.", throwable, event);
        }
    }

    /**
     * Process a log event, via formatting and outputting it.
     * This should be overidden by subclasses.
     *
     * @param event the log event
     */
    protected abstract void doProcessEvent(LogEvent event)
            throws Exception;

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        if (isOpen()) {
            m_isOpen = false;
        }
    }

    /**
     * Helper method to retrieve ErrorHandler for subclasses.
     *
     * @return the ErrorHandler
     */
    protected final ErrorHandler getErrorHandler() {
        return m_errorHandler;
    }

    /**
     * Helper method to send error messages to error handler.
     *
     * @param message   the error message
     * @param throwable the exception if any
     * @deprecated Use getErrorHandler().error(...) directly
     */
    protected final void error(final String message, final Throwable throwable) {
        getErrorHandler().error(message, throwable, null);
    }
}
