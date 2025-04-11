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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.formatter.ContactCSV;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class ExportContacts extends MailDocumentHandler  {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.A_FOLDER };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return true; }

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);

        String folder = request.getAttribute(MailConstants.A_FOLDER, null);
        ItemId iidFolder = folder == null ? null : new ItemId(folder, zsc);

        String ct = request.getAttribute(MailConstants.A_CONTENT_TYPE);
        if (!ct.equals("csv"))
            throw ServiceException.INVALID_REQUEST("unsupported content type: " + ct, null);
        
        String format = request.getAttribute(MailConstants.A_CSVFORMAT, null);
        
        List<Contact> contacts = mbox.getContactList(octxt, iidFolder != null ? iidFolder.getId() : -1);
        
        StringBuffer sb = new StringBuffer();
        if (contacts == null)
        	contacts = new ArrayList<Contact>();
        
        try {
            ContactCSV.toCSV(format, contacts.iterator(), sb);
        } catch (ContactCSV.ParseException e) {
            throw MailServiceException.UNABLE_TO_EXPORT_CONTACTS(e.getMessage(), e);
        }

        Element response = zsc.createElement(MailConstants.EXPORT_CONTACTS_RESPONSE);
        Element content = response.addElement(MailConstants.E_CONTENT);
        content.setText(sb.toString());

        return response;
    }
}
