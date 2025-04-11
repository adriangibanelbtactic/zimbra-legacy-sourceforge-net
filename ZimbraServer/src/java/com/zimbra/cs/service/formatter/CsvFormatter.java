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
package com.zimbra.cs.service.formatter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Part;
import javax.servlet.http.HttpServletResponse;

import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.UserServlet;
import com.zimbra.cs.service.UserServletException;
import com.zimbra.cs.service.UserServlet.Context;
import com.zimbra.cs.service.mail.ImportContacts;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.HttpUtil;

public class CsvFormatter extends Formatter {

    public String getType() {
        return "csv";
    }

    public String[] getDefaultMimeTypes() {
        return new String[] { "text/csv", "text/comma-separated-values", Mime.CT_TEXT_PLAIN };
    }

    public String getDefaultSearchTypes() {
        return MailboxIndex.SEARCH_FOR_CONTACTS;
    }

    public void formatCallback(Context context) throws IOException, ServiceException {
        Iterator<? extends MailItem> iterator = null;
        StringBuffer sb = new StringBuffer();
        try {
            iterator = getMailItems(context, -1, -1, Integer.MAX_VALUE);
            String format = context.req.getParameter(UserServlet.QP_CSVFORMAT);
            ContactCSV.toCSV(format, iterator, sb);
        } catch (ContactCSV.ParseException e) {
            throw MailServiceException.UNABLE_TO_IMPORT_CONTACTS("could not generate CSV", e);
        } finally {
            if (iterator instanceof QueryResultIterator)
                ((QueryResultIterator) iterator).finished();
        }

        // todo: get from folder name
        String filename = context.itemPath;
        if (filename == null || filename.length() == 0)
            filename = "contacts";
        String cd = Part.ATTACHMENT + "; filename=" + HttpUtil.encodeFilename(context.req, filename + ".csv");
        context.resp.addHeader("Content-Disposition", cd);
        context.resp.setCharacterEncoding(Mime.P_CHARSET_UTF8);
        context.resp.setContentType("text/csv");
        context.resp.getOutputStream().print(sb.toString());
    }

    public boolean canBeBlocked() {
        return false;
    }

    public void saveCallback(byte[] body, Context context, String contentType, Folder folder, String filename) throws UserServletException, ServiceException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), "UTF-8"));
            String format = context.req.getParameter(UserServlet.QP_CSVFORMAT);
            List<Map<String, String>> contacts = ContactCSV.getContacts(reader, format);
            ItemId iidFolder = new ItemId(folder);
            ImportContacts.ImportCsvContacts(context.opContext, context.targetMailbox, iidFolder, contacts, null);
        } catch (ContactCSV.ParseException e) {
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "could not parse csv file");
        } catch (UnsupportedEncodingException uee) {
            throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "could not parse csv file");
        }
    }
}
