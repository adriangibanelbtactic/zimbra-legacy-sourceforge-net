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
package org.jivesoftware.wildfire.stats;

/**
 * A statistic being tracked by the server
 */
public interface Statistic {

    /**
     * Returns the name of a stat.
     *
     * @return Returns the name of a stat.
     */
    public String getName();

    /**
     * Returns the type of a stat.
     *
     * @return Returns the type of a stat.
     */
    public Type getStatType();

    /**
     * Returns a description of the stat.
     *
     * @return Returns a description of the stat.
     */
    public String getDescription();

    /**
     * Returns the units that relate to the stat.
     *
     * @return Returns the units that relate to the stat.
     */
    public String getUnits();

    /**
     * @return Returns the sample of data.
     */
    public double sample();

    public enum Type {

        /**
         * Specifies a rate over time.
         * For example, the averave of kb/s in file transfers.
         */
        rate,
        /**
         * Specifies a count at a specific time period. An example would be the
         * number of users in MultiUserChat at this second.
         */
        count
    }
}
