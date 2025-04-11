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
 * Portions created by Zimbra are Copyright (C) 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.mime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.convert.ConversionException;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.ZimbraAnalyzer;
import com.zimbra.cs.localconfig.DebugConfig;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Contact.Attachment;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mime.MimeCompoundHeader.ContentDisposition;
import com.zimbra.cs.object.ObjectHandlerException;
import com.zimbra.cs.util.JMSession;

public class ParsedContact {
    private Map<String, String> mFields;
    private List<Attachment> mAttachments;
    private byte[] mBlob;
    private String mDigest;

    private List<Document> mLuceneDocuments;

    public ParsedContact(Map<String, String> fields) throws ServiceException {
        // prune out the empty and blank fields
        if (fields == null)
            throw ServiceException.INVALID_REQUEST("contact must have fields", null);

        for (Iterator<Map.Entry<String, String>> it = fields.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            String key   = StringUtil.stripControlCharacters(entry.getKey());
            String value = StringUtil.stripControlCharacters(entry.getValue());
            if (key == null || key.trim().equals("") || value == null || value.equals(""))
                it.remove();
        }

        if (fields.isEmpty())
            throw ServiceException.INVALID_REQUEST("contact must have fields", null);

        addNicknameAndTypeIfPDL(fields);

        mFields = fields;
    }

    public ParsedContact(Map<String, String> fields, byte[] blob) throws ServiceException {
        this(fields);

        if (blob != null && blob.length > 0) {
            try {
                mAttachments = parseBlob(blob);
                mBlob = blob;
                mDigest = ByteUtil.getDigest(blob);

                for (Attachment attach : mAttachments)
                    mFields.remove(attach.getName());
                if (fields.isEmpty())
                    throw ServiceException.INVALID_REQUEST("contact must have fields", null);
            } catch (MessagingException me) {
                throw MailServiceException.MESSAGE_PARSE_ERROR(me);
            } catch (IOException ioe) {
                throw MailServiceException.MESSAGE_PARSE_ERROR(ioe);
            }
        }
    }

    public ParsedContact(Map<String, String> fields, List<Attachment> attachments) throws ServiceException {
        this(fields);

        if (attachments != null && !attachments.isEmpty()) {
            try {
                mAttachments = attachments;
                mBlob = generateBlob(attachments);
                mDigest = ByteUtil.getDigest(mBlob);

                for (Attachment attach : mAttachments)
                    mFields.remove(attach.getName());
                if (fields.isEmpty())
                    throw ServiceException.INVALID_REQUEST("contact must have fields", null);
            } catch (MessagingException me) {
                throw MailServiceException.MESSAGE_PARSE_ERROR(me);
            }
        }
    }

    public ParsedContact(Contact con) throws ServiceException {
        this(con.getFields(), con.getContent());
    }

    private static byte[] generateBlob(List<Attachment> attachments) throws MessagingException {
        MimeMessage mm = new Mime.FixedMimeMessage(JMSession.getSession());
        MimeMultipart multi = new MimeMultipart("mixed");
        int part = 1;
        for (Attachment attach : attachments) {
            InternetHeaders headers = new InternetHeaders();
            ContentDisposition cdisp = new ContentDisposition(Part.ATTACHMENT);
            cdisp.setParameter("filename", attach.getFilename()).setParameter("field", attach.getName());
            headers.addHeader("Content-Disposition", cdisp.toString());
            headers.addHeader("Content-Type", attach.getContentType());
            headers.addHeader("Content-Transfer-Encoding", Mime.ET_8BIT);

            attach.setPartName(Integer.toString(part));
            multi.addBodyPart(new MimeBodyPart(headers, attach.getContent()));
        }
        mm.setContent(multi);
        mm.saveChanges();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mm.writeTo(baos);
        } catch (IOException ioe) {
            // this shouldn't actually happen since the OutputStream doesn't throw IOException
        }
        return baos.toByteArray();
    }

    private static List<Attachment> parseBlob(byte[] blob) throws MessagingException, IOException {
        MimeMessage mm = new Mime.FixedMimeMessage(JMSession.getSession(), new ByteArrayInputStream(blob));
        MimeMultipart multi = (MimeMultipart) mm.getContent();

        List<Attachment> attachments = new ArrayList<Attachment>(multi.getCount());
        for (int i = 0; i < multi.getCount(); i++) {
            MimeBodyPart bp = (MimeBodyPart) multi.getBodyPart(i);
            byte[] content = ByteUtil.getContent(bp.getInputStream(), bp.getSize());
            ContentDisposition cdisp = new ContentDisposition(bp.getHeader("Content-Disposition", null));
            attachments.add(new Attachment(content, bp.getContentType(), cdisp.getParameter("field"), cdisp.getParameter("filename"), Integer.toString(i)));
        }
        return attachments;
    }

    /** This is a workaround for bug 11900 that must go away before Frank GA */
    private static void addNicknameAndTypeIfPDL(Map<String, String> fields) throws ServiceException {
        if (!fields.containsKey(Contact.A_dlist))
            return;
        String fileAs = fields.get(Contact.A_fileAs);
        if (fileAs == null)
            throw ServiceException.INVALID_REQUEST("PDL: no " + Contact.A_fileAs + " present", null);
        String fileAsPrefix = Contact.FA_EXPLICIT + ":";
        if (!fileAs.startsWith(fileAsPrefix) || fileAs.length() <= fileAsPrefix.length())
            throw ServiceException.INVALID_REQUEST("PDL: invalid" + Contact.A_fileAs + ": " + fileAs, null);
        String nickname = fileAs.substring(fileAsPrefix.length());
        fields.put(Contact.A_nickname, nickname);
        fields.put(Contact.A_type, Contact.TYPE_GROUP);
    }


    public Map<String, String> getFields() {
        return mFields;
    }

    public boolean hasAttachment() {
        return mAttachments != null && !mAttachments.isEmpty();
    }

    public List<Attachment> getAttachments() {
        return mAttachments;
    }

    public byte[] getBlob() {
        return mBlob;
    }

    public String getDigest() {
        return mDigest;
    }


    public ParsedContact modify(Map<String, String> fieldDelta, List<Attachment> attachDelta) throws ServiceException {
        if (attachDelta != null && !attachDelta.isEmpty()) {
            for (Attachment attach : attachDelta) {
                // make sure we don't have anything referenced in both fieldDelta and attachDelta
                fieldDelta.remove(attach.getName());

                // add the new attachments to the contact
                removeAttachment(attach.getName());
                if (mAttachments == null)
                    mAttachments = new ArrayList<Attachment>(attachDelta.size());
                mAttachments.add(attach);
            }
        }

        for (Map.Entry<String, String> entry : fieldDelta.entrySet()) {
            String name  = StringUtil.stripControlCharacters(entry.getKey());
            String value = StringUtil.stripControlCharacters(entry.getValue());
            if (name != null && !name.trim().equals("")) {
                // kill any attachment with that field name
                removeAttachment(name);

                // and replace the field appropriately
                if (value == null || value.equals(""))
                    mFields.remove(name);
                else
                    mFields.put(name, value);
            }
        }

        if (mFields.isEmpty())
            throw ServiceException.INVALID_REQUEST("contact must have fields", null);

        addNicknameAndTypeIfPDL(mFields);

        mBlob = null;
        mDigest = null;
        mLuceneDocuments = null;

        if (mAttachments != null) {
            try {
                mBlob = generateBlob(mAttachments);
                mDigest = ByteUtil.getDigest(mBlob);
            } catch (MessagingException me) {
                throw MailServiceException.MESSAGE_PARSE_ERROR(me);
            }
        }

        return this;
    }

    private void removeAttachment(String name) {
        if (mAttachments == null)
            return;

        for (Iterator<Attachment> it = mAttachments.iterator(); it.hasNext(); ) {
            if (it.next().getName().equals(name))
                it.remove();
        }

        if (mAttachments.isEmpty())
            mAttachments = null;
    }


    public ParsedContact analyze(Mailbox mbox) throws ServiceException {
        try {
            analyzeContact(mbox.attachmentsIndexingEnabled());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            ZimbraLog.index.warn("exception while analyzing contact; attachments will be partially indexed", e);
        }
        return this;
    }

    private void analyzeContact(boolean indexAttachments) throws ServiceException {
        if (mLuceneDocuments != null)
            return;

        mLuceneDocuments = new ArrayList<Document>();
        StringBuilder attachContent = new StringBuilder();

        int numParseErrors = 0;
        ServiceException conversionError = null;
        if (mAttachments != null) {
            for (Attachment attach: mAttachments) {
                try {
                    analyzeAttachment(attach, attachContent, indexAttachments);
                } catch (MimeHandlerException e) {
                    numParseErrors++;
                    String part = attach.getPartName();
                    String ctype = attach.getContentType();
                    ZimbraLog.index.warn("Parse error on attachment " + part + " (" + ctype + ")", e);
                    if (conversionError == null && ConversionException.isTemporaryCauseOf(e))
                        conversionError = ServiceException.FAILURE("failed to analyze part", e.getCause());
                } catch (ObjectHandlerException e) {
                    numParseErrors++;
                    String part = attach.getPartName();
                    String ctype = attach.getContentType();
                    ZimbraLog.index.warn("Parse error on attachment " + part + " (" + ctype + ")", e);
                }
            }
        }

        mLuceneDocuments.add(getPrimaryDocument(attachContent));
    }

    private void analyzeAttachment(Attachment attach, StringBuilder contentText, boolean indexAttachments)
    throws MimeHandlerException, ObjectHandlerException, ServiceException {
        String ctype = attach.getContentType();
        MimeHandler handler = MimeHandler.getMimeHandler(ctype);
        assert(handler != null);

        if (handler.isIndexingEnabled()) {
            handler.init(attach);
            handler.setPartName(attach.getPartName());
            handler.setFilename(attach.getFilename());
            handler.setMessageDigest(getDigest());

            if (indexAttachments && !DebugConfig.disableIndexingAttachmentsTogether) {
                // add ALL TEXT from EVERY PART to the toplevel body content.
                // This is necessary for queries with multiple words -- where
                // one word is in the body and one is in a sub-attachment.
                //
                // If attachment indexing is disabled, then we only add the main body and
                // text parts...
                contentText.append(contentText.length() == 0 ? "" : " ").append(handler.getContent());
            }

            if (indexAttachments && !DebugConfig.disableIndexingAttachmentsSeparately) {
                // Each non-text MIME part is also indexed as a separate
                // Lucene document.  This is necessary so that we can tell the
                // client what parts match if a search matched a particular
                // part.
                Document doc = handler.getDocument();
                if (doc != null) {
                    doc.add(new Field(LuceneFields.L_SIZE, Integer.toString(attach.getSize()), Field.Store.YES, Field.Index.NO));
                    mLuceneDocuments.add(doc);
                }
            }
        }
    }

    private static final Set<String> sSearchFields = new HashSet<String>(Arrays.asList(new String[] {
            Contact.A_company, Contact.A_firstName, Contact.A_lastName, Contact.A_nickname
    }));

    private Document getPrimaryDocument(StringBuilder contentText) throws ServiceException {
        StringBuilder searchText = new StringBuilder(), emailText = new StringBuilder();
        for (Map.Entry<String, String> field : mFields.entrySet()) {
            String name = field.getKey(), value = field.getValue();
            contentText.append(' ').append(value);
            if (name.startsWith("email"))
                emailText.append(' ').append(value);
            if (sSearchFields.contains(name))
                searchText.append(' ').append(value);
        }
        String emails = emailText.toString();
        contentText.append(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_TO, emails));
        searchText.append(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_TO, emails));

        Document doc = new Document();
        // put the email addresses in the "To" field so they can be more easily searched
        doc.add(new Field(LuceneFields.L_H_TO, emails, Field.Store.NO, Field.Index.TOKENIZED));
        // put the name in the "From" field since the MailItem table uses 'Sender'
        doc.add(new Field(LuceneFields.L_H_FROM, Contact.getFileAsString(mFields), Field.Store.NO, Field.Index.TOKENIZED));
        // bug 11831 - put contact searchable data in its own field so wildcard search works better
        doc.add(new Field(LuceneFields.L_CONTACT_DATA, searchText.toString(), Field.Store.NO, Field.Index.TOKENIZED));
        doc.add(new Field(LuceneFields.L_CONTENT, contentText.toString(), Field.Store.NO, Field.Index.TOKENIZED));
        doc.add(new Field(LuceneFields.L_H_SUBJECT, "", Field.Store.NO, Field.Index.TOKENIZED));
        doc.add(new Field(LuceneFields.L_PARTNAME, LuceneFields.L_PARTNAME_CONTACT, Field.Store.YES, Field.Index.UN_TOKENIZED));

        return doc;
    }
}
