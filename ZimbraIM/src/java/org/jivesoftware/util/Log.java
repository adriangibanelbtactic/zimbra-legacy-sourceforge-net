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
package org.jivesoftware.util;

import com.zimbra.common.util.ZimbraLog;

/**
 */
public class Log {

    /**
     * This method is used to initialize the Log class. For normal operations this method
     * should <b>never</b> be called, rather it's only publically available so that the class
     * can be reset by the setup process once the home directory has been specified.
     */
    public static void initLog() {
    }

    public static void setProductName(String productName) {
    }

    public static boolean isErrorEnabled() {
        return ZimbraLog.im.isErrorEnabled();
    }

    public static boolean isFatalEnabled() {
        return ZimbraLog.im.isFatalEnabled();
    }

    public static boolean isDebugEnabled() {
        return ZimbraLog.im.isDebugEnabled();
    }

    public static boolean isInfoEnabled() {
        return ZimbraLog.im.isInfoEnabled();
    }

    public static boolean isWarnEnabled() {
        return ZimbraLog.im.isWarnEnabled();
    }

    public static void debug(String s) {
        ZimbraLog.im.debug(s);
    }

    public static void debug(Throwable throwable) {
        ZimbraLog.im.debug(throwable);
    }

    public static void debug(String s, Throwable throwable) {
        ZimbraLog.im.debug(s, throwable);
    }

    public static void info(String s) {
        ZimbraLog.im.info(s);
    }

    public static void info(Throwable throwable) {
        ZimbraLog.im.info(throwable);
    }

    public static void info(String s, Throwable throwable) {
        ZimbraLog.im.info(s, throwable);
    }

    public static void warn(String s) {
        ZimbraLog.im.warn(s);
    }

    public static void warn(Throwable throwable) {
        ZimbraLog.im.warn(throwable);
    }

    public static void warn(String s, Throwable throwable) {
        ZimbraLog.im.warn(s,throwable);
    }

    public static void error(String s) {
        ZimbraLog.im.error(s);
    }

    public static void error(Throwable throwable) {
        ZimbraLog.im.error(throwable);
    }

    public static void error(String s, Throwable throwable) {
        ZimbraLog.im.error(s,throwable);
    }

    public static void fatal(String s) {
        ZimbraLog.im.fatal(s);
    }

    public static void fatal(Throwable throwable) {
        ZimbraLog.im.fatal(throwable);
    }

    public static void fatal(String s, Throwable throwable) {
        ZimbraLog.im.fatal(s,throwable);
    }
}