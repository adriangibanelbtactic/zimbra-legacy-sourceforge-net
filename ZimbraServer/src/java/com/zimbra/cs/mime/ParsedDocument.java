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

/*
 * Created on Feb 15, 2006
 */
package com.zimbra.cs.mime;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.DateField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.index.Fragment;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.object.ObjectHandlerException;

public class ParsedDocument {

    private static class DigestingInputStream extends InputStream {
        private InputStream mStream;
        private MessageDigest mDigester;
        private byte[] mDigest;
        private int mSize = 0;

        private DigestingInputStream(InputStream is) {
            mStream = is;
            try {
                mDigester = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                // this should never happen unless the JDK is foobar
                throw new RuntimeException(e);
            }
        }
        public int read() throws IOException {
            int i = mStream.read();
            if (i != -1) {
                mDigester.update(new byte[] { (byte) i }, 0, 1);
                mSize++;
            } else if (mDigest == null)
                mDigest = mDigester.digest();
            return i;
        }
        public int read(byte b[], int off, int len) throws IOException {
            int read = mStream.read(b, off, len);
            if (read != -1) {
                mDigester.update(b, off, read);
                mSize += read;
            } else if (mDigest == null)
                mDigest = mDigester.digest();
            return read;
        }
        private void consumeRemainder() throws IOException {
            if (mDigest == null) {
                byte[] buffer = new byte[8192];
                while (read(buffer) > 0)
                    ;
            }
        }
        byte[] getDigest() throws IOException  { consumeRemainder();  return mDigest; }
        int getTotalSize() throws IOException  { consumeRemainder();  return mSize; }
    }

    private static interface DocumentDataSource extends DataSource {
        public String getDigest() throws IOException;
        public int getSize() throws IOException;
    }

    private class FileDocumentDataSource extends FileDataSource implements DocumentDataSource {
        private DigestingInputStream mStream;

        private FileDocumentDataSource(File file) { super(file); }
        public String getContentType() { return mContentType; }
        public String getName()        { return mFilename; }
        public InputStream getInputStream() throws IOException {
            return mStream = new DigestingInputStream(super.getInputStream());
        }
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }
        public String getDigest() throws IOException  { checkStream();  return encodeDigest(mStream.getDigest()); }
        public int getSize() throws IOException       { checkStream();  return mStream.getTotalSize(); }
        private void checkStream() throws IOException  { if (mStream == null) getInputStream(); }
    }

    private class ByteArrayDataSource implements DocumentDataSource {
        private byte[] mRawData;
        private String mMyDigest;

        private ByteArrayDataSource(byte[] rawData, String digest) {
            mRawData = rawData;
            mMyDigest = digest;
        }

        public String getContentType() { return mContentType; }
        public String getName() { return mFilename; }
        public InputStream getInputStream() {
            return new ByteArrayInputStream(mRawData);
        }
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }
        public String getDigest() { return mMyDigest; }
        public int getSize() { return mRawData.length; }
    }

    private String mCreator;
    private String mContentType;
    private String mFilename;
    private int mSize;
    private String mDigest;
    private Document mDocument = null;
    private String mFragment;
    private long mCreatedDate;

    public ParsedDocument(File file, String filename, String ctype, long createdDate, String creator)
    throws ServiceException, IOException {
        DocumentDataSource ds = new FileDocumentDataSource(file);
        init(ds, filename, ctype, createdDate, creator);
    }

    public ParsedDocument(byte[] rawData, String digest, String filename, String ctype, long createdDate, String creator)
    throws ServiceException, IOException {
        DocumentDataSource ds = new ByteArrayDataSource(rawData, digest);
        init(ds, filename, ctype, createdDate, creator);
    }

    private void init(DocumentDataSource ds, String filename, String ctype, long createdDate, String creator)
    throws ServiceException, IOException {
        mFilename = filename;
        mContentType = ctype;
        mCreatedDate = createdDate;
        mCreator = creator;

        try {
            MimeHandler handler = MimeHandler.getMimeHandler(ctype);
            assert(handler != null);

            if (handler.isIndexingEnabled())
                handler.init(ds);
            handler.setFilename(filename);
            handler.setPartName(LuceneFields.L_PARTNAME_TOP);
            mFragment = Fragment.getFragment(handler.getContent(), false);
            handler.setMessageDigest(mDigest = ds.getDigest());
            mDocument = handler.getDocument();
            mDocument.add(Field.Text(LuceneFields.L_SIZE, Integer.toString(mSize = ds.getSize())));
            mDocument.add(new Field(LuceneFields.L_H_SUBJECT, filename, false/*store*/, true/*index*/, true/*tokenize*/));
            mDocument.add(new Field(LuceneFields.L_CONTENT, filename,  false/*store*/, true/*index*/, true/*tokenize*/));
            mDocument.add(new Field(LuceneFields.L_SORT_SUBJECT, filename.toUpperCase(), false/*store*/, true/*index*/, false/*tokenize*/));
            mDocument.add(new Field(LuceneFields.L_SORT_NAME, creator.toUpperCase(), false/*store*/, true/*index*/, false/*tokenize*/));
            mDocument.add(new Field(LuceneFields.L_H_FROM, creator, false/*store*/, true/*index*/, true/*tokenize*/));
            mDocument.add(Field.Text(LuceneFields.L_FILENAME, filename));
            String dateString = DateField.timeToString(createdDate);
            if (dateString == null)
            	throw ServiceException.FAILURE("cannot get a valid date", null);
            try {
            	mDocument.add(Field.Text(LuceneFields.L_DATE, dateString));
            } catch (Exception e) {
            }
        } catch (MimeHandlerException mhe) {
        	throw ServiceException.FAILURE("cannot create ParsedDocument", mhe);
        } catch (ObjectHandlerException ohe) {
        	throw ServiceException.FAILURE("cannot create ParsedDocument", ohe);
        }
    }

    public void setVersion(int v) {
    	mDocument.add(Field.UnIndexed(LuceneFields.L_VERSION, Integer.toString(v)));
    }
    
    public String getFilename()     { return mFilename; }
    public String getContentType()  { return mContentType; }
    public int getSize()            { return mSize; }
    public String getDigest()       { return mDigest; }
    public String getCreator()      { return mCreator; }
    
    public Document getDocument()   {
        return mDocument; 
    }
    
    
    public String getFragment()     { return mFragment; }
    public long getCreatedDate()    { return mCreatedDate; }

    static String encodeDigest(byte[] digest) {
        byte[] encoded = Base64.encodeBase64(digest);
        // Replace '/' with ',' to make the digest filesystem-safe.
        for (int i = 0; i < encoded.length; i++)
            if (encoded[i] == (byte) '/')
                encoded[i] = (byte) ',';
        return new String(encoded);
    }

    public static void main(String[] args) throws Throwable {
        ParsedDocument pd;
        long timer, time;
        String creator = "test@zimbra.com";
        for (int i = 0; i < 5; i++) {
            timer = System.currentTimeMillis();
            pd = new ParsedDocument(new File("C:\\tmp\\todo.txt"), "todo.txt", "text/plain", timer, creator);
            time = (System.currentTimeMillis() - timer);
            System.out.println(pd.getFilename() + " (" + pd.getSize() + "b) {" + time + "us} [" + pd.getDigest() + "]: " + pd.getFragment());

            timer = System.currentTimeMillis();
            pd = new ParsedDocument(new File("C:\\tmp\\SOLTYREI.html"), "SOLTYREI.html", "text/html", timer, creator);
            time = (System.currentTimeMillis() - timer);
            System.out.println(pd.getFilename() + " (" + pd.getSize() + "b) {" + time + "us} [" + pd.getDigest() + "]: " + pd.getFragment());

            timer = System.currentTimeMillis();
            pd = new ParsedDocument(new File("C:\\tmp\\postgresql-8.0-US.pdf"), "postgresql-8.0-US.pdf", "application/pdf", timer, creator);
            time = (System.currentTimeMillis() - timer);
            System.out.println(pd.getFilename() + " (" + pd.getSize() + "b) {" + time + "us} [" + pd.getDigest() + "]: " + pd.getFragment());
        }
    }
}
