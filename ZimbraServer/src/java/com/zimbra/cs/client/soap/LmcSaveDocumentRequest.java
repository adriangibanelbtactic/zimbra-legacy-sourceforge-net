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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */


package com.zimbra.cs.client.soap;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.soap.DomUtil;
import com.zimbra.cs.client.*;

public class LmcSaveDocumentRequest extends LmcSendMsgRequest {

	private LmcDocument mDoc;
    
    public void setDocument(LmcDocument doc) { mDoc = doc; }
    
    public LmcDocument getDocument() { return mDoc; }
    
	protected Element getRequestXML() {
		Element request = DocumentHelper.createElement(MailService.SAVE_DOCUMENT_REQUEST);
        Element doc = DomUtil.add(request, MailService.E_DOC, "");
        LmcSoapRequest.addAttrNotNull(doc, MailService.A_NAME, mDoc.getName());
        LmcSoapRequest.addAttrNotNull(doc, MailService.A_CONTENT_TYPE, mDoc.getContentType());
        LmcSoapRequest.addAttrNotNull(doc, MailService.A_FOLDER, mDoc.getFolder());
        Element upload = DomUtil.add(doc, MailService.E_UPLOAD, "");
        LmcSoapRequest.addAttrNotNull(upload, MailService.A_ID, mDoc.getAttachmentId());
        return request;
    }

	protected LmcSoapResponse parseResponseXML(Element responseXML) throws ServiceException {
		
        LmcSaveDocumentResponse response = new LmcSaveDocumentResponse();
        LmcDocument doc = parseDocument(DomUtil.get(responseXML, MailService.E_DOC));
        response.setDocument(doc);
        return response;
	}

}
