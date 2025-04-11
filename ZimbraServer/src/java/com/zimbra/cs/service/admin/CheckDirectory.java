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
 * Portions created by Zimbra are Copyright (C) 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.service.admin;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class CheckDirectory extends AdminDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context)
    throws ServiceException {
    
        ZimbraSoapContext lc = getZimbraSoapContext(context);

        Element response = lc.createElement(AdminConstants.CHECK_DIRECTORY_RESPONSE);

        for (Iterator<Element> iter = request.elementIterator(AdminConstants.E_DIRECTORY);
             iter.hasNext(); ) {
            Element dirReq = iter.next();
            String path = dirReq.getAttribute(AdminConstants.A_PATH);
            boolean create = dirReq.getAttributeBool(AdminConstants.A_CREATE, false);
            File dir = new File(path);
            boolean exists = dir.exists();
            if (!exists && create) {
                dir.mkdirs();
                exists = dir.exists();
            }
            boolean isDirectory = false;
            boolean readable = false;
            boolean writable = false;
            if (exists) {
                isDirectory = dir.isDirectory();
                readable = dir.canRead();
                writable = dir.canWrite();
            }

            Element dirResp = response.addElement(AdminConstants.E_DIRECTORY);
            dirResp.addAttribute(AdminConstants.A_PATH, path);
            dirResp.addAttribute(AdminConstants.A_EXISTS, exists);
            dirResp.addAttribute(AdminConstants.A_IS_DIRECTORY, isDirectory);
            dirResp.addAttribute(AdminConstants.A_READABLE, readable);
            dirResp.addAttribute(AdminConstants.A_WRITABLE, writable);
        }

        return response;
    }
}
