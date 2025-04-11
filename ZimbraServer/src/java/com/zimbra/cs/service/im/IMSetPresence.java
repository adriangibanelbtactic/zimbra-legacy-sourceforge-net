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
import com.zimbra.cs.im.IMPersona;
import com.zimbra.cs.im.IMPresence;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.soap.ZimbraSoapContext;

public class IMSetPresence extends IMDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        OperationContext octxt = getOperationContext(zsc, context);

        Element response = zsc.createElement(IMConstants.IM_SET_PRESENCE_RESPONSE);

        Element e = request.getElement(IMConstants.E_PRESENCE);

        String showStr = e.getAttribute(IMConstants.A_SHOW, IMPresence.Show.ONLINE.toString());
        String statusStr = null;
        Element status = e.getOptionalElement(IMConstants.E_STATUS);
        if (status != null) {
            statusStr = status.getText();
        }

        IMPresence presence = new IMPresence(IMPresence.Show.valueOf(showStr.toUpperCase()), (byte)1, statusStr);

        Object lock = super.getLock(zsc);
        synchronized (lock) {
            IMPersona persona = super.getRequestedPersona(zsc, context, lock);
            persona.setMyPresence(octxt, presence);
        }

        return response;
    }
}
