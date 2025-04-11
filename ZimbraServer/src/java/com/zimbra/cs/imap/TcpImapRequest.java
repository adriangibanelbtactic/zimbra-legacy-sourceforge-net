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
package com.zimbra.cs.imap;

import java.io.IOException;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.tcpserver.TcpServerInputStream;

public class TcpImapRequest extends ImapRequest {
    final class ImapTerminatedException extends ImapParseException {
        private static final long serialVersionUID = 6105950126307803418L;
    }

    final class ImapContinuationException extends ImapParseException {
        private static final long serialVersionUID = 7925400980773927177L;
        boolean sendContinuation;
        ImapContinuationException(boolean send)  { super(); sendContinuation = send; }
    }

    private TcpServerInputStream mStream;
    private int mLiteral = -1;
    private int mSize;
    private boolean mUnlogged;

    TcpImapRequest(String line, ImapHandler handler) {
        super(handler);
        mParts.add(line);
    }

    TcpImapRequest(TcpServerInputStream tsis, ImapHandler handler) {
        super(handler);
        mStream  = tsis;
    }

    TcpImapRequest rewind()  { mIndex = mOffset = 0;  mTag = null;  return this; }

    private void incrementSize(long increment) throws ImapParseException {
        mSize += increment;
        if (mSize > getMaxRequestLength())
            throw new ImapParseException(mTag, "request too long");
    }

    void continuation() throws IOException, ImapParseException {
        if (mLiteral >= 0) {
            Object part = mParts.get(mParts.size() - 1);
            byte[] buffer = (part instanceof byte[] ? (byte[]) part : new byte[mLiteral]);
            if (buffer != part)
                mParts.add(buffer);
            int read = mStream.read(buffer, buffer.length - mLiteral, mLiteral);
            if (read == -1)
                throw new ImapTerminatedException();
            if (!mUnlogged && ZimbraLog.imap.isDebugEnabled())
                ZimbraLog.imap.debug("C: {" + read + "}:" + (read > 100 ? "" : new String(buffer, buffer.length - mLiteral, read)));
            mLiteral -= read;
            if (mLiteral > 0)
                throw new ImapContinuationException(false);
            mLiteral = -1;
        }

        String line = mStream.readLine(), logline = line;
        // TcpServerInputStream.readLine() reutrns null on end of stream!
        if (line == null)
            throw new ImapTerminatedException();
        incrementSize(line.length());
        mParts.add(line);

        if (mParts.size() == 1) {
            // check for "LOGIN" command and elide if necessary
            int space = line.indexOf(' ') + 1;
            if (space > 1 && space < line.length() - 7)
                mUnlogged = line.substring(space, space + 6).equalsIgnoreCase("LOGIN ");
            if (mUnlogged)
                logline = line.substring(0, space + 6) + "...";
        }
        if (ZimbraLog.imap.isDebugEnabled())
            ZimbraLog.imap.debug("C: " + logline);

        // if the line ends in a LITERAL+ non-blocking literal, keep reading
        if (line.endsWith("+}") && extensionEnabled("LITERAL+")) {
            int openBrace = line.lastIndexOf('{', line.length() - 3);
            if (openBrace > 0) {
                try {
                    long size = Long.parseLong(line.substring(openBrace + 1, line.length() - 2));
                    incrementSize(size);
                    mLiteral = (int) size;
                    continuation();
                } catch (NumberFormatException nfe) {
                    if (mTag == null && mIndex == 0 && mOffset == 0) {
                        mTag = readTag();  rewind();
                    }
                    throw new ImapParseException(mTag, "malformed nonblocking literal");
                }
            }
        }
    }

    private byte[] getCurrentBuffer() throws ImapParseException {
        Object part = mParts.get(mIndex);
        if (!(part instanceof byte[]))
            throw new ImapParseException(mTag, "not inside literal");
        return (byte[]) part;
    }

    byte[] readLiteral() throws IOException, ImapParseException {
        boolean blocking = true;
        skipChar('{');
        long length = Long.parseLong(readNumber());
        if (peekChar() == '+' && extensionEnabled("LITERAL+")) {
            skipChar('+');  blocking = false;
        }
        skipChar('}');

        if (mIndex == mParts.size() - 1 || (mIndex == mParts.size() - 2 && mLiteral != -1)) {
            if (mLiteral == -1) {
                incrementSize(length);
                mLiteral = (int) length;
            }
            if (!blocking && mStream.available() >= mLiteral)
                continuation();
            else
                throw new ImapContinuationException(blocking && mIndex == mParts.size() - 1);
        }
        mIndex++;
        byte[] result = getCurrentBuffer();
        mIndex++;
        mOffset = 0;
        return result;
    }

}
