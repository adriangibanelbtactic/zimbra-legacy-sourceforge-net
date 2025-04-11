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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Apr 14, 2005
 *
 */
package com.zimbra.cs.mime;

public interface MimeTypeInfo {
    
    /**
     * Gets the associated MIME types.
     * @return
     */
    public String[] getTypes();
    
    /**
     * Gets the name of the extension where the handler class is defined.
     * If it is part of the core, return null.
     * @return
     */
    public String getExtension();
    
    /**
     * Gets the name of the handler class. If no package is specified, 
     * com.zimbra.cs.mime.handler is assumed. 
     * @return
     */
    public String getHandlerClass();
    
    /**
     * Whether the content is to be indexed for this mime type.
     * @return
     */
    public boolean isIndexingEnabled();
    
    /**
     * Gets the description of the mime type
     * @return
     */
    public String getDescription();
    
    /**
     * Gets a list of file extensions for this mime type.
     * @return
     */
    public String[] getFileExtensions();

    /**
     * Gets the priority.  In the case where multiple <tt>MimeTypeInfo</tt>s
     * match a search, the one with the highest priority wins.
     */
    public int getPriority();
}
