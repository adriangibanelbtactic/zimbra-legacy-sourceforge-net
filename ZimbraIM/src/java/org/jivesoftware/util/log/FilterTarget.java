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
package org.jivesoftware.util.log;



/**
 * A Log target which will do filtering and then pass it
 * onto targets further along in chain.
 * <p/>
 * <p>Filtering can mena that not all LogEvents get passed
 * along chain or that the LogEvents passed alongare modified
 * in some manner.</p>
 *
 * @author <a href="mailto:donaldp@apache.org">Peter Donald</a>
 */
public interface FilterTarget extends LogTarget {

    /**
     * Add a target to output chain.
     *
     * @param target the log target
     */
    void addTarget(LogTarget target);
}
