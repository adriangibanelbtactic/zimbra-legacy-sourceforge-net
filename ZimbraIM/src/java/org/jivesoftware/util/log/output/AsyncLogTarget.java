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
import java.util.LinkedList;

/**
 * An asynchronous LogTarget that sends entries on in another thread.
 * It is the responsibility of the user of this class to start
 * the thread etc.
 * <p/>
 * <pre>
 * LogTarget mySlowTarget = ...;
 * AsyncLogTarget asyncTarget = new AsyncLogTarget( mySlowTarget );
 * Thread thread = new Thread( asyncTarget );
 * thread.setPriority( Thread.MIN_PRIORITY );
 * thread.start();
 * <p/>
 * logger.setLogTargets( new LogTarget[] { asyncTarget } );
 * </pre>
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class AsyncLogTarget extends AbstractTarget implements Runnable {

    private final LinkedList m_list;
    private final int m_queueSize;
    private final LogTarget m_logTarget;

    public AsyncLogTarget(final LogTarget logTarget) {
        this(logTarget, 15);
    }

    public AsyncLogTarget(final LogTarget logTarget, final int queueSize) {
        m_logTarget = logTarget;
        m_list = new LinkedList();
        m_queueSize = queueSize;
        open();
    }

    /**
     * Provide component with ErrorHandler.
     *
     * @param errorHandler the errorHandler
     */
    public synchronized void setErrorHandler(final ErrorHandler errorHandler) {
        super.setErrorHandler(errorHandler);

        if (m_logTarget instanceof ErrorAware) {
            ((ErrorAware)m_logTarget).setErrorHandler(errorHandler);
        }
    }

    /**
     * Process a log event by adding it to queue.
     *
     * @param event the log event
     */
    public void doProcessEvent(final LogEvent event) {
        synchronized (m_list) {
            final int size = m_list.size();
            while (m_queueSize <= size) {
                try {
                    m_list.wait();
                }
                catch (final InterruptedException ie) {
                    //This really should not occur ...
                    //Maybe we should log it though for
                    //now lets ignore it
                }
            }

            m_list.addFirst(event);

            if (size == 0) {
                //tell the "server" thread to wake up
                //if it is waiting for a queue to contain some items
                m_list.notify();
            }
        }
    }

    public void run() {
        //set this variable when thread is interupted
        //so we know we can shutdown thread soon.
        boolean interupted = false;

        while (true) {
            LogEvent event = null;

            synchronized (m_list) {
                while (null == event) {
                    final int size = m_list.size();

                    if (size > 0) {
                        event = (LogEvent)m_list.removeLast();

                        if (size == m_queueSize) {
                            //tell the "client" thread to wake up
                            //if it is waiting for a queue position to open up
                            m_list.notify();
                        }

                    }
                    else if (interupted || Thread.interrupted()) {
                        //ie there is nothing in queue and thread is interrupted
                        //thus we stop thread
                        return;
                    }
                    else {
                        try {
                            m_list.wait();
                        }
                        catch (final InterruptedException ie) {
                            //Ignore this and let it be dealt in next loop
                            //Need to set variable as the exception throw cleared status
                            interupted = true;
                        }
                    }
                }
            }


            try {
                //actually process an event
                m_logTarget.processEvent(event);
            }
            catch (final Throwable throwable) {
                getErrorHandler().error("Unknown error writing event.", throwable, event);
            }
        }
    }
}
