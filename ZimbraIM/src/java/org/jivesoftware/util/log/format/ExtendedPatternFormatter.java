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
package org.jivesoftware.util.log.format;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.log.ContextMap;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.util.StackIntrospector;

/**
 * Formatter especially designed for debugging applications.
 * <p/>
 * This formatter extends the standard PatternFormatter to add
 * two new possible expansions. These expansions are %{method}
 * and %{thread}. In both cases the context map is first checked
 * for values with specified key. This is to facilitate passing
 * information about caller/thread when threads change (as in
 * AsyncLogTarget). They then attempt to determine appropriate
 * information dynamically.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @version CVS $Revision: 37 $ $Date: 2004-10-20 23:08:43 -0700 (Wed, 20 Oct 2004) $
 */
public class ExtendedPatternFormatter extends PatternFormatter {
    private final static int TYPE_METHOD = MAX_TYPE + 1;
    private final static int TYPE_THREAD = MAX_TYPE + 2;

    private final static String TYPE_METHOD_STR = "method";
    private final static String TYPE_THREAD_STR = "thread";

    public ExtendedPatternFormatter(final String format) {
        super(format);
    }

    /**
     * Retrieve the type-id for a particular string.
     *
     * @param type the string
     * @return the type-id
     */
    protected int getTypeIdFor(final String type) {
        if (type.equalsIgnoreCase(TYPE_METHOD_STR))
            return TYPE_METHOD;
        else if (type.equalsIgnoreCase(TYPE_THREAD_STR))
            return TYPE_THREAD;
        else {
            return super.getTypeIdFor(type);
        }
    }

    /**
     * Formats a single pattern run (can be extended in subclasses).
     *
     * @param run the pattern run to format.
     * @return the formatted result.
     */
    protected String formatPatternRun(final LogEvent event, final PatternRun run) {
        switch (run.m_type) {
            case TYPE_METHOD:
                return getMethod(event, run.m_format);
            case TYPE_THREAD:
                return getThread(event, run.m_format);
            default:
                return super.formatPatternRun(event, run);
        }
    }

    /**
     * Utility method to format category.
     *
     * @param event
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    private String getMethod(final LogEvent event, final String format) {
        final ContextMap map = event.getContextMap();
        if (null != map) {
            final Object object = map.get("method");
            if (null != object) {
                return object.toString();
            }
        }

//        final String result = StackIntrospector.getCallerMethod(Logger.class);
        final String result = StackIntrospector.getCallerMethod(Log.class);
        if (null == result) {
            return "UnknownMethod";
        }
        return result;
    }

    /**
     * Utility thread to format category.
     *
     * @param event
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    private String getThread(final LogEvent event, final String format) {
        final ContextMap map = event.getContextMap();
        if (null != map) {
            final Object object = map.get("thread");
            if (null != object) {
                return object.toString();
            }
        }

        return Thread.currentThread().getName();
    }
}
