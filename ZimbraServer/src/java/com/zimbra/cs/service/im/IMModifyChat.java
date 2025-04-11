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
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.common.soap.Element;

import com.zimbra.cs.im.IMAddr;
import com.zimbra.cs.im.IMChat;
import com.zimbra.cs.im.IMPersona;
import com.zimbra.soap.ZimbraSoapContext;

public class IMModifyChat extends IMDocumentHandler 
{
    static enum Op {
        CLOSE, ADDUSER;
    }

    public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);

        Element response = lc.createElement(IMConstants.IM_MODIFY_CHAT_RESPONSE);
        
        String threadId = request.getAttribute(IMConstants.A_THREAD_ID);
        
        Object lock = super.getLock(lc);
        
        synchronized(lock) {
            IMPersona persona = super.getRequestedPersona(lc, lock);
            
            IMChat chat = persona.getChat(threadId);
            
            if (chat == null) {
                throw ServiceException.FAILURE("Unknown thread: "+threadId, null);
            } else {
                String opStr = request.getAttribute(IMConstants.A_OPERATION);
                Op op = Op.valueOf(opStr.toUpperCase());
                
                switch(op) {
                    case CLOSE:
                        persona.closeChat(lc.getOperationContext(), chat);
                        break;
                    case ADDUSER:
                    {
                        String newUser = request.getAttribute(IMConstants.A_ADDRESS);
                        String inviteMessage = request.getText();
                        if (inviteMessage == null || inviteMessage.length() == 0)
                            inviteMessage = "Please join my chat";
                        persona.addUserToChat(lc.getOperationContext(), chat, new IMAddr(newUser), inviteMessage);
                    }
                    break;
                }
            }
        }
        return response;        
    }

}
