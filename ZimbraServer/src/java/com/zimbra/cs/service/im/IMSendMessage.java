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
package com.zimbra.cs.service.im;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.IMConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.SoapFaultException;

import com.zimbra.cs.im.IMAddr;
import com.zimbra.cs.im.IMMessage;
import com.zimbra.cs.im.IMPersona;
import com.zimbra.cs.im.IMMessage.TextPart;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.soap.ZimbraSoapContext;

public class IMSendMessage extends IMDocumentHandler {

    public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);

        Element response = lc.createElement(IMConstants.IM_SEND_MESSAGE_RESPONSE);

        Element msgElt = request.getElement(IMConstants.E_MESSAGE);
        
        String threadId = msgElt.getAttribute(IMConstants.A_THREAD_ID, null);
        String addr = msgElt.getAttribute(IMConstants.A_ADDRESS);
        
        String subject = null;
        String body = null;
        
        Element subjElt = msgElt.getOptionalElement(IMConstants.E_SUBJECT);
        if (subjElt != null) {
            subject = subjElt.getText();
        }
        
        Element bodyElt = msgElt.getOptionalElement(IMConstants.E_BODY);
        if (bodyElt != null) {
            body = bodyElt.getText();
        }
        
        IMMessage msg = new IMMessage(subject==null?null:new TextPart(subject),
                body==null?null:new TextPart(body));
                
        OperationContext oc = lc.getOperationContext();
        
        Object lock = super.getLock(lc);
        synchronized(lock) {
            IMPersona persona = super.getRequestedPersona(lc, lock);
            
            persona.sendMessage(oc, new IMAddr(addr), threadId, msg);
        }
        
        response.addAttribute(IMConstants.A_THREAD_ID, threadId);
        
        return response;        
    }
}
