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
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraContext;
import com.zimbra.soap.WriteOpDocumentHandler;

/**
 * @author schemers
 */
public class ModifyContact extends WriteOpDocumentHandler  {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailService.E_CONTACT, MailService.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return false; }
    
    public Element handle(Element request, Map context) throws ServiceException {
        ZimbraContext lc = getZimbraContext(context);
        Mailbox mbox = getRequestedMailbox(lc);
        OperationContext octxt = lc.getOperationContext();

        boolean replace = request.getAttributeBool(MailService.A_REPLACE, false);

        Element cn = request.getElement(MailService.E_CONTACT);

        ItemId iid = new ItemId(cn.getAttribute(MailService.A_ID), lc);

        HashMap attrs = new HashMap();
        for (Iterator it = cn.elementIterator(MailService.E_ATTRIBUTE); it.hasNext(); ) {
            Element e = (Element) it.next();
            String name = e.getAttribute(MailService.A_ATTRIBUTE_NAME);
            attrs.put(name, e.getText());
        }

        mbox.modifyContact(octxt, iid.getId(), attrs, replace);

        Contact con = mbox.getContactById(octxt, iid.getId());
        Element response = lc.createElement(MailService.MODIFY_CONTACT_RESPONSE);
        if (con != null)
        	ToXML.encodeContact(response, lc, con, null, true, null);
        return response;
    }
}
