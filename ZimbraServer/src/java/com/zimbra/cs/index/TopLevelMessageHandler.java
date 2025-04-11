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
 * Created on 2004. 5. 27.
 */
package com.zimbra.cs.index;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.mime.MPartInfo;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.mime.MimeHandler;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.object.ObjectHandlerException;


/**
 * @author jhahm
 *
 * Class that creates a Lucene document for top-level MIME message.
 * All textual data from MIME parts are aggregated into this single
 * Lucene document
 */
public final class TopLevelMessageHandler {

    private static Log mLog = LogFactory.getLog(TopLevelMessageHandler.class);
    
    private List<MPartInfo> mMessageParts;
    private Document     mDocument;
    private StringBuilder mContent;
    private String       mBodyContent;
    private String       mFragment;
    private boolean      mHasCalendar;

    public TopLevelMessageHandler(List<MPartInfo> parts) {
        mMessageParts = parts;
        mDocument = new Document();
        mContent = new StringBuilder();
    }

    public void addContent(String text) {
        addContent(text, false);
    }

    public void addContent(String text, boolean isMainBody) {
        if (mContent.length() > 0)
            mContent.append(' ');
        mContent.append(text);

        if (isMainBody)
            mBodyContent = (mBodyContent == null ? text : mBodyContent + ' ' + text);
    }

    public List<MPartInfo> getMessageParts() {
        return mMessageParts;
    }

    public void hasCalendarPart(boolean hasCal) {
        mHasCalendar = hasCal;
    }

    public String getFragment() {
        if (mFragment == null) {
            String remainder = (mBodyContent == null ? mContent.toString() : mBodyContent).trim();
            mFragment = Fragment.getFragment(remainder, mHasCalendar);
        }
        return mFragment;
    }

    public Document getDocument(ParsedMessage pm)
    throws MessagingException, ServiceException {

        mDocument.add(new Field(LuceneFields.L_MIMETYPE, "message/rfc822", Field.Store.YES, Field.Index.TOKENIZED));
        mDocument.add(new Field(LuceneFields.L_PARTNAME, LuceneFields.L_PARTNAME_TOP, Field.Store.YES, Field.Index.UN_TOKENIZED));

        String toValue = setHeaderAsField("to", pm, LuceneFields.L_H_TO, Field.Store.NO, Field.Index.TOKENIZED);
        String ccValue = setHeaderAsField("cc", pm, LuceneFields.L_H_CC, Field.Store.NO, Field.Index.TOKENIZED);

        setHeaderAsField("x-envelope-from", pm, LuceneFields.L_H_X_ENV_FROM, Field.Store.NO, Field.Index.TOKENIZED);
        setHeaderAsField("x-envelope-to", pm, LuceneFields.L_H_X_ENV_TO, Field.Store.NO, Field.Index.TOKENIZED);

        String msgId = pm.getHeader("message-id");
        if (msgId.length() > 0) {
            if (msgId.charAt(0) == '<')
                msgId = msgId.substring(1);

            if (msgId.charAt(msgId.length()-1) == '>')
                msgId = msgId.substring(0, msgId.length()-1);

            if (msgId.length() > 0)
                mDocument.add(new Field(LuceneFields.L_H_MESSAGE_ID, msgId, Field.Store.NO, Field.Index.UN_TOKENIZED));
        }

        String from = pm.getSender();
        String subject = pm.getNormalizedSubject();
        String sortFrom = pm.getParsedSender().getSortString();
        if (sortFrom == null)
            sortFrom = "";
        else if (sortFrom.length() > DbMailItem.MAX_SENDER_LENGTH)
            sortFrom = sortFrom.substring(0, DbMailItem.MAX_SENDER_LENGTH);

        mDocument.add(new Field(LuceneFields.L_H_FROM, from, Field.Store.NO, Field.Index.TOKENIZED));
        mDocument.add(new Field(LuceneFields.L_H_SUBJECT, subject, Field.Store.NO, Field.Index.TOKENIZED));

        // calculate the fragment *before* we add non-content data
        mFragment = getFragment();

        // add subject and from to main content for better searching
        addContent(subject);

        // Bug 583: add all of the TOKENIZED versions of the email addresses to our CONTENT field...
        addContent(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_FROM, from));
        addContent(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_TO, toValue));
        addContent(ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_CC, ccValue));

        String text = mContent.toString();

        mDocument.add(new Field(LuceneFields.L_CONTENT, text, Field.Store.NO, Field.Index.TOKENIZED));

        String sizeStr = Integer.toString(pm.getMimeMessage().getSize());
        mDocument.add(new Field(LuceneFields.L_SIZE, sizeStr, Field.Store.YES, Field.Index.NO));

        try {
            MimeHandler.getObjects(text, mDocument);
        } catch (ObjectHandlerException e) {
            String msgid = pm.getMessageID();
            String sbj = pm.getSubject();
            mLog.warn("Unable to recognize searchable objects in message (Message-ID: " +
                msgid + ", Subject: " + sbj + ")", e);
        }

        // Get the list of attachment content types from this message and any
        // TNEF attachments
        Set<String> contentTypes = Mime.getAttachmentList(mMessageParts);

        // Assemble a comma-separated list of attachment content types
        StringBuilder buf = new StringBuilder();
        for (String contentType : contentTypes) {
            if (buf.length() > 0)
                buf.append(',');
            buf.append(contentType);
        }

        String attachments = buf.toString();
        if (attachments.equals(""))
            attachments = LuceneFields.L_ATTACHMENT_NONE;
        else
            attachments = attachments + "," + LuceneFields.L_ATTACHMENT_ANY;
        mDocument.add(new Field(LuceneFields.L_ATTACHMENTS, attachments, Field.Store.NO, Field.Index.TOKENIZED));

        return mDocument;
    }

    private String setHeaderAsField(String headerName, ParsedMessage pm, String fieldName,   
        Field.Store stored, Field.Index indexed)  throws MessagingException  {
        return setHeaderAsField(mDocument, pm, headerName, fieldName, stored, indexed);
    }	

    private String setHeaderAsField(Document d, ParsedMessage pm, String headerName,
        String fieldName, Field.Store stored, Field.Index indexed) throws MessagingException  {
        String value = pm.getMimeMessage().getHeader(headerName, null);

        if (value == null || value.length() == 0)
            return "";
        try {
            value = MimeUtility.decodeText(value);
        } catch (UnsupportedEncodingException e) { }
        d.add(new Field(fieldName, value, stored, indexed));
        d.add(new Field(fieldName, value, stored, indexed));
        return value;
    }

    /**
     * For every attachment, many of the lucene indexed fields from the top level
     * message are also indexed as part of the attachment: this is done so that the
     * attachment will show up if you do things like "type:pdf and from:foo"
     * 
     * "this" --> top level doc
     * @param d subdocument of top level 
     */
    public void setLuceneHeadersFromContainer(Document d, ParsedMessage pm) throws MessagingException {
        setHeaderAsField(d, pm, "to", LuceneFields.L_H_TO, Field.Store.NO, Field.Index.TOKENIZED);
        setHeaderAsField(d, pm, "cc", LuceneFields.L_H_CC, Field.Store.NO, Field.Index.TOKENIZED);

        String subject = pm.getNormalizedSubject();
        String sortFrom = pm.getParsedSender().getSortString();
        if (sortFrom != null && sortFrom.length() > DbMailItem.MAX_SENDER_LENGTH)
            sortFrom = sortFrom.substring(0, DbMailItem.MAX_SENDER_LENGTH);
        String from = pm.getSender();

        if (from != null)
            d.add(new Field(LuceneFields.L_H_FROM, from, Field.Store.NO, Field.Index.TOKENIZED));
        
        if (subject != null) 
            d.add(new Field(LuceneFields.L_H_SUBJECT, subject, Field.Store.NO, Field.Index.TOKENIZED));
    }
}
