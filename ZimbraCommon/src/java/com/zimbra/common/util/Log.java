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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

/**
 * Wrapper around Log4j that supports <code>printf</code> functionality
 * via {@link String#format}.
 * 
 * @author bburtin
 *
 */
public class Log {

    private Map<String, Logger> mAccountLoggers = new ConcurrentHashMap<String, Logger>();
    
    private static final Map<Level, org.apache.log4j.Level> ZIMBRA_TO_LOG4J =
        new HashMap<Level, org.apache.log4j.Level>();
    
    static {
        ZIMBRA_TO_LOG4J.put(Level.error, org.apache.log4j.Level.ERROR);
        ZIMBRA_TO_LOG4J.put(Level.warn, org.apache.log4j.Level.WARN);
        ZIMBRA_TO_LOG4J.put(Level.info, org.apache.log4j.Level.INFO);
        ZIMBRA_TO_LOG4J.put(Level.debug, org.apache.log4j.Level.DEBUG);
    }
    
    public enum Level {
        error, warn, info, debug;
    };
    
    private Logger mLogger;
    
    Log(Logger logger) {
        if (logger == null) {
            throw new IllegalStateException("logger cannot be null");
        }
        mLogger = logger;
    }
    
    /**
     * Adds an account-level logger whose log level may be different than
     * that of the main log category.
     * 
     * @param category the main log category name
     * @param accountName the account name
     * @param level the log level that applies only to the given account
     */
    public static void addAccountLogger(String category, String accountName, Level level) {
        if (category == null || accountName == null) {
            return;
        }
        
        // Look up main Zimbra logger
        Log mainLog = LogFactory.getLog(category);
        if (mainLog == null) {
            return;
        }
        
        // If account logger already exists, set the level
        Logger logger = mainLog.mAccountLoggers.get(accountName);
        if (logger != null) {
            logger.setLevel(ZIMBRA_TO_LOG4J.get(level));
            return;
        }
        
        // Create new account logger
        String accountCategory = getAccountCategory(category, accountName);
        logger = Logger.getLogger(accountCategory);
        logger.setLevel(ZIMBRA_TO_LOG4J.get(level));
        mainLog.mAccountLoggers.put(accountName, logger);
    }
    
    /**
     *  Deletes an account-level logger.
     */
    public static void deleteAccountLogger(String category, String accountName) {
        if (category == null || accountName == null) {
            return;
        }
        
        // Look up main Zimbra logger
        Log mainLog = LogFactory.getLog(category);
        if (mainLog == null) {
            return;
        }
        mainLog.mAccountLoggers.remove(accountName);
    }
    
    private static String getAccountCategory(String category, String accountName) {
        return String.format("%s.%s", accountName, category);    }
    
    public boolean isDebugEnabled() {
        return getLogger().isDebugEnabled();
    }
    
    public boolean isInfoEnabled() {
        return getLogger().isInfoEnabled();
    }
    
    public boolean isWarnEnabled() {
        return getLogger().isEnabledFor(Priority.WARN);
    }
    
    public boolean isErrorEnabled() {
        return getLogger().isEnabledFor(Priority.ERROR);
    }

    public boolean isFatalEnabled() {
        return getLogger().isEnabledFor(Priority.FATAL);
    }
    

    public void debug(Object o) {
        getLogger().debug(o);
    }

    public void debug(Object o, Throwable t) {
        getLogger().debug(o, t);
    }

    public void debug(String format, Object ... objects) {
        if (isDebugEnabled()) {
            getLogger().debug(String.format(format, objects));
        }
    }

    public void debug(String format, Object o, Throwable t) {
        if (isDebugEnabled()) {
            getLogger().debug(String.format(format, o), t);
        }
    }

    public void debug(String format, Object o1, Object o2, Throwable t) {
        if (isDebugEnabled()) {
            getLogger().debug(String.format(format, o1, o2), t);
        }
    }

    public void debug(String format, Object o1, Object o2, Object o3, Throwable t) {
        if (isDebugEnabled()) {
            getLogger().debug(String.format(format, o1, o2, o3), t);
        }
    }
    

    public void info(Object o) {
        getLogger().info(o);
    }

    public void info(Object o, Throwable t) {
        getLogger().info(o, t);
    }

    public void info(String format, Object ... objects) {
        if (isInfoEnabled()) {
            getLogger().info(String.format(format, objects));
        }
    }

    public void info(String format, Object o, Throwable t) {
        if (isInfoEnabled()) {
            getLogger().info(String.format(format, o), t);
        }
    }

    public void info(String format, Object o1, Object o2, Throwable t) {
        if (isInfoEnabled()) {
            getLogger().info(String.format(format, o1, o2), t);
        }
    }

    public void info(String format, Object o1, Object o2, Object o3, Throwable t) {
        if (isInfoEnabled()) {
            getLogger().info(String.format(format, o1, o2, o3), t);
        }
    }
    

    public void warn(Object o) {
        getLogger().warn(o);
    }

    public void warn(Object o, Throwable t) {
        getLogger().warn(o, t);
    }

    public void warn(String format, Object ... objects) {
        if (isWarnEnabled()) {
            getLogger().warn(String.format(format, objects));
        }
    }

    public void warn(String format, Object o, Throwable t) {
        if (isWarnEnabled()) {
            getLogger().warn(String.format(format, o), t);
        }
    }

    public void warn(String format, Object o1, Object o2, Throwable t) {
        if (isWarnEnabled()) {
            getLogger().warn(String.format(format, o1, o2), t);
        }
    }

    public void warn(String format, Object o1, Object o2, Object o3, Throwable t) {
        if (isWarnEnabled()) {
            getLogger().warn(String.format(format, o1, o2, o3), t);
        }
    }
    

    public void error(Object o) {
        getLogger().error(o);
    }

    public void error(Object o, Throwable t) {
        getLogger().error(o, t);
    }

    public void error(String format, Object ... objects) {
        if (isErrorEnabled()) {
            getLogger().error(String.format(format, objects));
        }
    }

    public void error(String format, Object o, Throwable t) {
        if (isErrorEnabled()) {
            getLogger().error(String.format(format, o), t);
        }
    }

    public void error(String format, Object o1, Object o2, Throwable t) {
        if (isErrorEnabled()) {
            getLogger().error(String.format(format, o1, o2), t);
        }
    }

    public void error(String format, Object o1, Object o2, Object o3, Throwable t) {
        if (isErrorEnabled()) {
            getLogger().error(String.format(format, o1, o2, o3), t);
        }
    }
    

    public void fatal(Object o) {
        getLogger().fatal(o);
    }

    public void fatal(Object o, Throwable t) {
        getLogger().fatal(o, t);
    }

    public void fatal(String format, Object ... objects) {
        if (isFatalEnabled()) {
            getLogger().fatal(String.format(format, objects));
        }
    }

    public void fatal(String format, Object o, Throwable t) {
        if (isFatalEnabled()) {
            getLogger().fatal(String.format(format, o), t);
        }
    }

    public void fatal(String format, Object o1, Object o2, Throwable t) {
        if (isFatalEnabled()) {
            getLogger().fatal(String.format(format, o1, o2), t);
        }
    }

    public void fatal(String format, Object o1, Object o2, Object o3, Throwable t) {
        if (isFatalEnabled()) {
            getLogger().fatal(String.format(format, o1, o2, o3), t);
        }
    }
    
    public void setLevel(Level level) {
        mLogger.setLevel(ZIMBRA_TO_LOG4J.get(level));
    }
    
    /**
     * Returns the Log4j logger for this <tt>Log</tt>'s category.  If a custom
     * logger has been defined for an account associated with the current thread,
     * returns that logger instead.
     * 
     * @see #addAccountLogger
     */
    private Logger getLogger() {
        if (mAccountLoggers.size() == 0) {
            return mLogger;
        }
        for (String accountName : ZimbraLog.getAccountNamesForThread()) {
            Logger logger = mAccountLoggers.get(accountName);
            if (logger != null) {
                return logger;
            }
        }
        return mLogger;
    }
}
