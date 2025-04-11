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
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.SearchParams.ExpandResults;
import com.zimbra.cs.mailbox.Conversation;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.operation.GetConversationByIdOperation;
import com.zimbra.cs.operation.Operation.Requester;
import com.zimbra.cs.session.Session;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class GetConv extends MailDocumentHandler  {
    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        Mailbox.OperationContext octxt = zsc.getOperationContext();
        Session session = getSession(context);
        
        Element econv = request.getElement(MailService.E_CONV);
        int id = (int) econv.getAttributeLong(MailService.A_ID);

        SearchParams params = new SearchParams();
        params.setFetchFirst(ExpandResults.get(econv.getAttribute(MailService.A_FETCH, null)));
        if (params.getFetchFirst() != ExpandResults.NONE) {
            params.setWantHtml(econv.getAttributeBool(MailService.A_WANT_HTML, false));
//            params.setMarkRead(econv.getAttributeBool(MailService.A_MARK_READ, false));
        }

        GetConversationByIdOperation op = new GetConversationByIdOperation(session, octxt, mbox, Requester.SOAP, id);
        op.schedule();
        Conversation conv = op.getResult();

        Element response = zsc.createElement(MailService.GET_CONV_RESPONSE);
        if (conv != null)
        	ToXML.encodeConversation(response, zsc, conv, params);
        else
            throw MailServiceException.NO_SUCH_CONV(id);
        return response;
    }
}
