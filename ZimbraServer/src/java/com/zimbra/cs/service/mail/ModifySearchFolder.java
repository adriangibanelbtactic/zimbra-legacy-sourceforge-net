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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.SearchFolder;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.operation.ModifySearchFolderOperation;
import com.zimbra.cs.operation.Operation.Requester;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.session.Session;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class ModifySearchFolder extends MailDocumentHandler  {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailService.E_SEARCH, MailService.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return false; }

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
		ZimbraSoapContext lc = getZimbraSoapContext(context);
		Mailbox mbox = getRequestedMailbox(lc);
		OperationContext octxt = lc.getOperationContext();
		Session session = getSession(context);
		
        Element t = request.getElement(MailService.E_SEARCH);
        ItemId iid = new ItemId(t.getAttribute(MailService.A_ID), lc);
        String query = t.getAttribute(MailService.A_QUERY);
        String types = t.getAttribute(MailService.A_SEARCH_TYPES, null);
        String sort = t.getAttribute(MailService.A_SORTBY, null);
        
        ModifySearchFolderOperation op = new ModifySearchFolderOperation(session, octxt, mbox, Requester.SOAP,
        			iid, query, types, sort);
        op.schedule();
        SearchFolder sf = op.getSf();
        
        Element response = lc.createElement(MailService.MODIFY_SEARCH_FOLDER_RESPONSE);
    	ToXML.encodeSearchFolder(response, lc, sf);
        return response;
	}
}
