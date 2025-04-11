/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.2
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.2 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimlets
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.service.admin;

import java.io.IOException;
import java.util.Map;

import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.service.FileUploadServlet;
import com.zimbra.cs.service.FileUploadServlet.Upload;
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.cs.zimlet.ZimletException;
import com.zimbra.cs.zimlet.ZimletUtil;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class ConfigureZimlet extends AdminDocumentHandler {

	@Override
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
		ZimbraSoapContext lc = getZimbraSoapContext(context);
        Element content = request.getElement(MailService.E_CONTENT);
        String attachment = content.getAttribute(MailService.A_ATTACHMENT_ID, null);
        Upload up = FileUploadServlet.fetchUpload(lc.getAuthtokenAccountId(), attachment, lc.getRawAuthToken());
        if (up == null)
            throw MailServiceException.NO_SUCH_UPLOAD(attachment);

        Element response = lc.createElement(AdminService.CONFIGURE_ZIMLET_RESPONSE);
		try {
			byte[] blob = ByteUtil.getContent(up.getInputStream(), 0);
			ZimletUtil.installConfig(new String(blob));
		} catch (IOException ioe) {
			throw ServiceException.FAILURE("cannot configure", ioe);
		} catch (ZimletException ze) {
			throw ServiceException.FAILURE("cannot configure", ze);
		} finally {
			FileUploadServlet.deleteUpload(up);
		}
		return response;
	}
}
