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
package com.zimbra.cs.service.wiki;

import java.io.IOException;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;

import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.FileUploadServlet;
import com.zimbra.cs.service.FileUploadServlet.Upload;
import com.zimbra.cs.service.mail.ToXML;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.wiki.Wiki;
import com.zimbra.cs.wiki.Wiki.WikiContext;
import com.zimbra.cs.wiki.WikiPage;
import com.zimbra.soap.ZimbraSoapContext;

public class SaveDocument extends WikiDocumentHandler {
    private static String[] TARGET_DOC_ID_PATH = new String[] { MailConstants.E_DOC, MailConstants.A_ID };
    private static String[] TARGET_DOC_FOLDER_PATH = new String[] { MailConstants.E_DOC, MailConstants.A_FOLDER };
    protected String[] getProxiedIdPath(Element request)     {
    	String id = getXPath(request, TARGET_DOC_ID_PATH);
    	if (id == null)
    		return TARGET_DOC_FOLDER_PATH;
    	return TARGET_DOC_ID_PATH; 
    }

	private static class Doc {
		public byte[] contents;
		public String name;
		public String contentType;
		public Upload up = null;
		public void cleanup() {
			if (up != null) {
                FileUploadServlet.deleteUpload(up);
			}
		}
	}
	
	protected Doc getDocumentDataFromMimePart(OperationContext octxt, Mailbox mbox, String msgid, String part) throws ServiceException {
		Message item = mbox.getMessageById(octxt, Integer.parseInt(msgid));
        MimeMessage mm = item.getMimeMessage();
		Doc doc = new Doc();
		try {
	        MimePart mp = Mime.getMimePart(mm, part);
			doc.contents = ByteUtil.getContent(mp.getInputStream(), 0);
			doc.name = Mime.getFilename(mp);
			doc.contentType = mp.getContentType();
		} catch (Exception e) {
			throw ServiceException.FAILURE("cannot get part "+part+" from message "+msgid, e);
		}
		
		return doc;
	}
	
	protected Doc getDocumentDataFromUpload(ZimbraSoapContext lc, String aid) throws ServiceException {
        Upload up = FileUploadServlet.fetchUpload(lc.getAuthtokenAccountId(), aid, lc.getRawAuthToken());

        Doc doc = new Doc();
        try {
        	doc.up = up;
        	doc.contents = ByteUtil.getContent(up.getInputStream(), 0);
        	doc.name = up.getName();
        	doc.contentType = up.getContentType();
        } catch (IOException ioe) {
       		doc.cleanup();
        	throw ServiceException.FAILURE("cannot get uploaded file", ioe);
        }
        return doc;
	}
	
	@Override
    public boolean isReadOnly() {
        return false;
    }

	@Override
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);

        Element docElem = request.getElement(MailConstants.E_DOC);
        OperationContext octxt = zsc.getOperationContext();

        Doc doc;
        Element attElem = docElem.getOptionalElement(MailConstants.E_UPLOAD);
        if (attElem != null) {
            String aid = attElem.getAttribute(MailConstants.A_ID, null);
            doc = getDocumentDataFromUpload(zsc, aid);
        } else {
        	attElem = docElem.getElement(MailConstants.E_MSG);
            String msgid = attElem.getAttribute(MailConstants.A_ID, null);
            String part = attElem.getAttribute(MailConstants.A_PART, null);
            Mailbox mbox = MailboxManager.getInstance().getMailboxByAccountId(zsc.getRequestedAccountId());
        	doc = getDocumentDataFromMimePart(octxt, mbox, msgid, part);
        }

        
        String name = docElem.getAttribute(MailConstants.A_NAME, doc.name);
        String ctype = docElem.getAttribute(MailConstants.A_CONTENT_TYPE, doc.contentType);
        String id = docElem.getAttribute(MailConstants.A_ID, null);
        int ver = (int)docElem.getAttributeLong(MailConstants.A_VERSION, 0);
        int itemId;
        if (id == null) {
        	itemId = 0;
        } else {
        	ItemId iid = new ItemId(id, zsc);
        	itemId = iid.getId();
        }

        WikiContext ctxt = new WikiContext(octxt, zsc.getRawAuthToken());
        WikiPage page = WikiPage.create(name, getAuthor(zsc), ctype, doc.contents);
        Wiki.addPage(ctxt, page, itemId, ver, getRequestedFolder(request, zsc));
        Document docItem = page.getWikiItem(ctxt);

        Element response = zsc.createElement(MailConstants.SAVE_DOCUMENT_RESPONSE);
        Element m = response.addElement(MailConstants.E_DOC);
        m.addAttribute(MailConstants.A_ID, new ItemIdFormatter(zsc).formatItemId(docItem));
        m.addAttribute(MailConstants.A_VERSION, docItem.getVersion());
        ToXML.encodeRestUrl(m, docItem);
        doc.cleanup();
        return response;
	}
}
