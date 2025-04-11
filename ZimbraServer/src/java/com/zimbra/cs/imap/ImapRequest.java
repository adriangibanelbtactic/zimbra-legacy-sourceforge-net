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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Apr 30, 2005
 */
package com.zimbra.cs.imap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.imap.ImapSession.ImapFlag;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.tcpserver.TcpServerInputStream;
import com.zimbra.cs.util.ZimbraLog;


class ImapRequest {
    static final boolean[] ATOM_CHARS     = new boolean[128];
    static final boolean[] ASTRING_CHARS  = new boolean[128];
    static final boolean[] TAG_CHARS      = new boolean[128];
    static final boolean[] PATTERN_CHARS  = new boolean[128];
    static final boolean[] FETCH_CHARS    = new boolean[128];
    static final boolean[] NUMBER_CHARS   = new boolean[128];
    static final boolean[] SEQUENCE_CHARS = new boolean[128];
    static final boolean[] SEARCH_CHARS   = new boolean[128];
    static final boolean[] REGEXP_ESCAPED = new boolean[128];
    static {
            for (int i = 0x21; i < 0x7F; i++)
                if (i != '(' && i != ')' && i != '{' && i != '%' && i != '*' && i != '"' && i != '\\')
                    SEARCH_CHARS[i] = FETCH_CHARS[i] = PATTERN_CHARS[i] = ASTRING_CHARS[i] = ATOM_CHARS[i] = TAG_CHARS[i] = true;
            ATOM_CHARS[']'] = false;
            TAG_CHARS['+']  = false;
            PATTERN_CHARS['%'] = PATTERN_CHARS['*'] = true;
            FETCH_CHARS['['] = false;
            SEARCH_CHARS['*'] = true;

            for (int i = '0'; i <= '9'; i++)
                NUMBER_CHARS[i] = SEQUENCE_CHARS[i] = true;
            SEQUENCE_CHARS['*'] = SEQUENCE_CHARS[':'] = SEQUENCE_CHARS[','] = true;

            REGEXP_ESCAPED['('] = REGEXP_ESCAPED[')'] = REGEXP_ESCAPED['.'] = true;
            REGEXP_ESCAPED['['] = REGEXP_ESCAPED[']'] = REGEXP_ESCAPED['|'] = true;
            REGEXP_ESCAPED['^'] = REGEXP_ESCAPED['$'] = REGEXP_ESCAPED['?'] = true;
            REGEXP_ESCAPED['{'] = REGEXP_ESCAPED['}'] = REGEXP_ESCAPED['*'] = true;
            REGEXP_ESCAPED['\\'] = true;
        }

    private TcpServerInputStream mStream;
    private ImapSession mSession;
    private ArrayList mParts = new ArrayList();
    private String mTag;
    private int mIndex, mOffset;
    private int mLiteral = -1;
    private int mSize;
    private boolean mUnlogged;

    ImapRequest(String line)  { mParts.add(line); }
    ImapRequest(TcpServerInputStream tsis, ImapSession session) {
        mStream  = tsis;
        mSession = session;
    }

    ImapRequest rewind()  { mIndex = mOffset = 0;  mTag = null;  return this; }

    private static int DEFAULT_MAX_REQUEST_LENGTH = 10000000;

    private void incrementSize(long increment) throws ImapParseException {
        mSize += increment;

        int maxSize = DEFAULT_MAX_REQUEST_LENGTH;
        try {
            Server server = Provisioning.getInstance().getLocalServer();
            maxSize = server.getIntAttr(Provisioning.A_zimbraFileUploadMaxSize, DEFAULT_MAX_REQUEST_LENGTH);
            if (maxSize <= 0)
                maxSize = DEFAULT_MAX_REQUEST_LENGTH;
        } catch (ServiceException e) { }

        if (mSize > maxSize)
            throw new ImapParseException(mTag, "request too long");
    }

    void continuation() throws IOException, ImapException {
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
        if (line.endsWith("+}")) {
            int openBrace = line.lastIndexOf('{', line.length() - 3);
            if (openBrace > 0)
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

    boolean eof()  { return peekChar() == -1; }
    int peekChar() {
        if (mIndex >= mParts.size())
            return -1;
        Object part = mParts.get(mIndex);
        if (part instanceof String)
            return (mOffset >= ((String) part).length()) ? -1 : ((String) part).charAt(mOffset);
        else
            return (mOffset >= ((byte[]) part).length) ? -1 : ((byte[]) part)[mOffset];
    }

    void skipSpace() throws ImapParseException { skipChar(' '); }
    void skipChar(char c) throws ImapParseException {
        Object part = mParts.get(mIndex);
        if (part instanceof String && mOffset < ((String) part).length() && ((String) part).charAt(mOffset) == c)
            mOffset++;
        else
            throw new ImapParseException(mTag, "end of line or wrong character; expected '" + c + '\'');
    }

    void skipNIL() throws ImapParseException {
        if (!readAtom().equals("NIL"))
            throw new ImapParseException(mTag, "did not find expected NIL");
    }

    private String getCurrentLine() throws ImapParseException {
        Object part = mParts.get(mIndex);
        if (!(part instanceof String))
            throw new ImapParseException(mTag, "should not be inside literal");
        return (String) part;
    }
    private byte[] getCurrentBuffer() throws ImapParseException {
        Object part = mParts.get(mIndex);
        if (!(part instanceof byte[]))
            throw new ImapParseException(mTag, "not inside literal");
        return (byte[]) part;
    }

    private String readContent(boolean[] acceptable) throws ImapParseException {
        String content = getCurrentLine();
        int i;
        for (i = mOffset; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c > 0x7F || !acceptable[c])
                break;
        }
        if (i == mOffset)
            throw new ImapParseException(mTag, "zero-length content");
        String result = content.substring(mOffset, i);
        mOffset = i;
        return result;
    }
    String readTag() throws ImapParseException     { mTag = readContent(TAG_CHARS);  return mTag; }
    String readAtom() throws ImapParseException    { return readContent(ATOM_CHARS).toUpperCase(); }
    String readNumber() throws ImapParseException  { return readContent(NUMBER_CHARS); }

    private static final int LAST_PUNCT = 0, LAST_DIGIT = 1, LAST_STAR = 2;

    private String validateSequence(String value) throws ImapParseException {
        int i, last = LAST_PUNCT;
        boolean colon = false;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 0x7F || !SEQUENCE_CHARS[c])
                throw new ImapParseException(mTag, "illegal character '" + c + "' in sequence");
            else if (c == '*') {
                if (last == LAST_DIGIT)  throw new ImapParseException(mTag, "malformed sequence");
                last = LAST_STAR;
            } else if (c == ',') {
                if (last == LAST_PUNCT)  throw new ImapParseException(mTag, "malformed sequence");
                last = LAST_PUNCT;  colon = false;
            } else if (c == ':') {
                if (colon || last == LAST_PUNCT)  throw new ImapParseException(mTag, "malformed sequence");
                last = LAST_PUNCT;  colon = true;
            } else {
                if (last == LAST_STAR || c == '0' && last == LAST_PUNCT)
                    throw new ImapParseException(mTag, "malformed sequence");
                last = LAST_DIGIT;
            }
        }
        if (last == LAST_PUNCT)
            throw new ImapParseException(mTag, "malformed sequence");
        return value;
    }
    String readSequence() throws ImapParseException {
        return validateSequence(readContent(SEQUENCE_CHARS));
    }

    String readQuoted() throws ImapParseException {
        String content = getCurrentLine();
        StringBuffer result = null;

        skipChar('"');
        int backslash = mOffset - 1;
        boolean escaped = false;
        for (int i = mOffset; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c > 0x7F || c == 0x00 || c == '\r' || c == '\n' || (escaped && c != '\\' && c != '"'))
                throw new ImapParseException(mTag, "illegal character '" + c + "' in quoted string");
            else if (!escaped && c == '\\') {
                if (result == null)
                    result = new StringBuffer();
                result.append(content.substring(backslash + 1, i));
                backslash = i;
                escaped = true;
            } else if (!escaped && c == '"') {
                mOffset = i + 1;
                String range = content.substring(backslash + 1, i);
                return (result == null ? range : result.append(range).toString());
            } else
                escaped = false;
        }
        throw new ImapParseException(mTag, "unexpected end of line in quoted string");
    }

    byte[] readLiteral() throws IOException, ImapException {
        boolean blocking = true;
        skipChar('{');
        long length = Long.parseLong(readNumber());
        if (peekChar() == '+')  { skipChar('+');  blocking = false; }
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
    private String readLiteral(String charset) throws IOException, ImapException {
        try {
            return new String(readLiteral(), charset);
        } catch (UnsupportedEncodingException e) {
            throw new ImapParseException(mTag, "BADCHARSET", "could not convert string to charset \"" + charset + '"');
        }
    }

    String readAstring() throws IOException, ImapException {
        return readAstring(null);
    }
    String readAstring(String charset) throws IOException, ImapException {
        return readAstring(charset, ASTRING_CHARS);
    }
    private String readAstring(String charset, boolean[] acceptable) throws IOException, ImapException {
        int c = peekChar();
        if (c == -1)        throw new ImapParseException(mTag, "unexpected end of line");
        else if (c == '{')  return readLiteral(charset != null ? charset : "utf-8");
        else if (c != '"')  return readContent(acceptable);
        else                return readQuoted();
    }

    private String readString(String charset) throws IOException, ImapException {
        int c = peekChar();
        if (c == -1)        throw new ImapParseException(mTag, "unexpected end of line");
        else if (c == '{')  return readLiteral(charset != null ? charset : "utf-8");
        else                return readQuoted();
    }

    private String readNstring(String charset) throws IOException, ImapException {
        int c = peekChar();
        if (c == -1)        throw new ImapParseException(mTag, "unexpected end of line");
        else if (c == '{')  return readLiteral(charset != null ? charset : "utf-8");
        else if (c != '"')  { skipNIL();  return null; }
        else                return readQuoted();
    }

    String readFolder() throws IOException, ImapException {
        return readFolder(false);
    }
    String readEscapedFolder() throws IOException, ImapException {
        return escapeFolder(readFolder(false), false);
    }
    String readFolderPattern() throws IOException, ImapException {
        return escapeFolder(readFolder(true), true);
    }
    private String readFolder(boolean isPattern) throws IOException, ImapException {
        String raw = readAstring(null, isPattern ? PATTERN_CHARS : ASTRING_CHARS);
        if (raw == null || raw.indexOf("&") == -1)
            return raw;
        try {
            return new String(raw.getBytes("US-ASCII"), "imap-utf-7");
        } catch (Exception e) {
            throw new ImapParseException(mTag, "invalid modified UTF-7: \"" + raw + '"');
        }
    }
    private String escapeFolder(String unescaped, boolean isPattern) {
        if (unescaped == null)
            return null;
        StringBuffer escaped = new StringBuffer();
        for (int i = 0; i < unescaped.length(); i++) {
            char c = unescaped.charAt(i);
            // 6.3.8: "The character "*" is a wildcard, and matches zero or more characters at this position.
            //         The character "%" is similar to "*", but it does not match a hierarchy delimiter."
            if (isPattern && c == '*')                escaped.append(".*");
            else if (isPattern && c == '%')           escaped.append("[^/]*");
            else if (c > 0x7f || !REGEXP_ESCAPED[c])  escaped.append(c);
            else                                      escaped.append('\\').append(c);
        }
        return escaped.toString().toUpperCase();
    }
    
    List readFlags() throws ImapParseException {
        List tags = new ArrayList();
        String content = getCurrentLine();
        boolean parens = (peekChar() == '(');
        if (parens)
            skipChar('(');
        if (!parens || peekChar() != ')')
            while (mOffset < content.length()) {
                if (peekChar() == '\\') {
                    skipChar('\\');
                    String flagName = '\\' + readAtom();
                    if (mSession != null) {
                        ImapFlag i4flag = mSession.getFlagByName(flagName);
                        if (i4flag == null || !i4flag.mListed)
                            throw new ImapParseException(mTag, "non-storable system tag \"" + flagName + '"');
                        tags.add(flagName);
                    }
                } else
                    tags.add(readAtom());
                if (parens && peekChar() == ')')      break;
                else if (mOffset < content.length())  skipSpace();
            }
        if (parens)
            skipChar(')');
        return tags;
    }

    Date readDate(DateFormat format) throws ImapParseException {
        String dateStr = (peekChar() == '"' ? readQuoted() : readContent(ATOM_CHARS));
        try {
            Date date = format.parse(dateStr);
            if (date.getTime() < 0)
                throw new ImapParseException(mTag, "date out of range");
            return date;
        } catch (java.text.ParseException e) {
            throw new ImapParseException(mTag, "invalid date format");
        }
    }

    Map readParameters(boolean nil) throws IOException, ImapException {
        if (peekChar() != '(') {
            if (!nil)
                throw new ImapParseException(mTag, "did not find expected '('");
            skipNIL();  return null;
        }

        Map params = new HashMap();
        skipChar('(');
        do {
            String name = readString("utf-8");
            skipSpace();
            params.put(name, readNstring("utf-8"));
            if (peekChar() == ')')  break;
            skipSpace();
        } while (true);
        return params;
    }

    int readFetch(List parts) throws IOException, ImapException {
        boolean list = peekChar() == '(';
        int attributes = 0;
        if (list)  skipChar('(');
        do {
            String item = readContent(FETCH_CHARS).toUpperCase();
            if (!list && item.equals("ALL"))        attributes = ImapHandler.FETCH_ALL;
            else if (!list && item.equals("FULL"))  attributes = ImapHandler.FETCH_FULL;
            else if (!list && item.equals("FAST"))  attributes = ImapHandler.FETCH_FAST;
            else if (item.equals("BODY") && peekChar() != '[')  attributes |= ImapHandler.FETCH_BODY;
            else if (item.equals("BODYSTRUCTURE"))  attributes |= ImapHandler.FETCH_BODYSTRUCTURE;
            else if (item.equals("ENVELOPE"))       attributes |= ImapHandler.FETCH_ENVELOPE;
            else if (item.equals("FLAGS"))          attributes |= ImapHandler.FETCH_FLAGS;
            else if (item.equals("INTERNALDATE"))   attributes |= ImapHandler.FETCH_INTERNALDATE;
            else if (item.equals("UID"))            attributes |= ImapHandler.FETCH_UID;
            else if (item.equals("RFC822.SIZE"))    attributes |= ImapHandler.FETCH_RFC822_SIZE;
            else if (item.equals("RFC822.HEADER"))  parts.add(new ImapPartSpecifier(item, "", "HEADER"));
            else if (item.equals("RFC822")) {
                attributes |= ImapHandler.FETCH_MARK_READ;
                parts.add(new ImapPartSpecifier(item, "", ""));
            } else if (item.equals("RFC822.TEXT")) {
                attributes |= ImapHandler.FETCH_MARK_READ;
                parts.add(new ImapPartSpecifier(item, "", "TEXT"));
            } else if (item.equals("BODY") || item.equals("BODY.PEEK")) {
                if (item.equals("BODY"))
                    attributes |= ImapHandler.FETCH_MARK_READ;
                String sectionPart = "", sectionText = "";
                int partialStart = -1, partialCount = -1;
                List headers = null;
                boolean done = false;

                skipChar('[');
                while (Character.isDigit((char) peekChar())) {
                    sectionPart += (sectionPart.equals("") ? "" : ".") + readNumber();
                    if (!(done = (peekChar() != '.')))
                        skipChar('.');
                }
                if (!done && peekChar() != ']') {
                    sectionText = readAtom();
                    if (sectionText.equals("HEADER.FIELDS") || sectionText.equals("HEADER.FIELDS.NOT")) {
                        headers = new ArrayList();
                        skipSpace();  skipChar('(');
                        while (peekChar() != ')') {
                            if (!headers.isEmpty())  skipSpace();
                            headers.add(readAstring().toUpperCase());
                        }
                        if (headers.isEmpty())
                            throw new ImapParseException(mTag, "header-list may not be empty");
                        skipChar(')');
                    } else if (sectionText.equals("MIME")) {
                        if (sectionPart.equals(""))
                            throw new ImapParseException(mTag, "\"MIME\" is not a valid section-spec");
                    } else if (!sectionText.equals("HEADER") && !sectionText.equals("TEXT"))
                        throw new ImapParseException(mTag, "unknown section-text \"" + sectionText + '"');
                }
                skipChar(']');
                if (peekChar() == '<') {
                    try {
                        skipChar('<');  partialStart = Integer.parseInt(readNumber());
                        skipChar('.');  partialCount = Integer.parseInt(readNumber());  skipChar('>');
                    } catch (NumberFormatException e) {
                        throw new ImapParseException(mTag, "invalid partial fetch specifier");
                    }
                }
                ImapPartSpecifier pspec = new ImapPartSpecifier("BODY", sectionPart, sectionText, partialStart, partialCount);
                pspec.setHeaders(headers);
                parts.add(pspec);
            } else
                throw new ImapParseException(mTag, "unknown FETCH attribute \"" + item + '"');
            if (list && peekChar() != ')')  skipSpace();
        } while (list && peekChar() != ')');
        if (list)  skipChar(')');
        return attributes;
    }

    private static final Map NEGATED_SEARCH = new HashMap();
        static {
            NEGATED_SEARCH.put("ANSWERED",   "UNANSWERED");
            NEGATED_SEARCH.put("DELETED",    "UNDELETED");
            NEGATED_SEARCH.put("DRAFT",      "UNDRAFT");
            NEGATED_SEARCH.put("FLAGGED",    "UNFLAGGED");
            NEGATED_SEARCH.put("KEYWORD",    "UNKEYWORD");
            NEGATED_SEARCH.put("RECENT",     "OLD");
            NEGATED_SEARCH.put("OLD",        "RECENT");
            NEGATED_SEARCH.put("SEEN",       "UNSEEN");
            NEGATED_SEARCH.put("UNANSWERED", "ANSWERED");
            NEGATED_SEARCH.put("UNDELETED",  "DELETED");
            NEGATED_SEARCH.put("UNDRAFT",    "DRAFT");
            NEGATED_SEARCH.put("UNFLAGGED",  "FLAGGED");
            NEGATED_SEARCH.put("UNKEYWORD",  "KEYWORD");
            NEGATED_SEARCH.put("UNSEEN",     "SEEN");
        }
    private static final Map INDEXED_HEADER = new HashMap();
        static {
            INDEXED_HEADER.put("CC",      "cc:");
            INDEXED_HEADER.put("FROM",    "from:");
            INDEXED_HEADER.put("SUBJECT", "subject:");
            INDEXED_HEADER.put("TO",      "to:");
        }

    private void readAndQuoteString(StringBuffer sb, String charset) throws IOException, ImapException {
        String content = readAstring(charset);
        sb.append('"');
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\')      sb.append("\\\"");
            else if (c == '"')  sb.append("\\\\");
            else                sb.append(c);
        }
        sb.append('"');
    }
    private String readAndReformatDate() throws ImapParseException {
        DateFormat format = (mSession == null ? new SimpleDateFormat("dd-MMM-yyyy", Locale.US) : mSession.getDateFormat());
        Date date = readDate(format);
        return mSession.getZimbraDateFormat().format(date);
    }

    private ImapFlag getFlag(String name) {
        return mSession == null ? null : mSession.getFlagByName(name);
    }
    private void insertFlag(String name, StringBuffer sb, TreeMap insertions) {
        insertFlag(getFlag(name), sb, insertions);
    }
    private void insertFlag(ImapFlag i4flag, StringBuffer sb, TreeMap insertions) {
        insertions.put(new Integer(sb.length()), i4flag);
    }

    private static final boolean SINGLE_CLAUSE = true, MULTIPLE_CLAUSES = false;
    private static final String SUBCLAUSE = "";

    private StringBuffer readSearchClause(StringBuffer search, TreeMap insertions, String charset, boolean single)
    throws IOException, ImapException {
        boolean first = true;
        int nots = 0;
        do {
            if (!first)  { skipSpace(); }
            int c = peekChar();
            // key will be "" iff we're opening a new subclause...
            String key = (c == '(' ? SUBCLAUSE : readContent(SEARCH_CHARS).toUpperCase());

            if (key.equals("NOT"))  { nots++; first = false; continue; }
            else if ((nots % 2) != 0) {
                if (NEGATED_SEARCH.containsKey(key))  key = (String) NEGATED_SEARCH.get(key);
                else                                  search.append('-');
            }
            nots = 0;

            if (key.equals("ALL"))              search.append("item:all");
            else if (key.equals("ANSWERED"))    search.append("is:replied");
            else if (key.equals("DELETED"))     search.append("tag:\\Deleted");
            else if (key.equals("DRAFT"))       search.append("is:draft");
            else if (key.equals("FLAGGED"))     search.append("is:flagged");
            else if (key.equals("RECENT"))      insertFlag("\\RECENT", search, insertions);
            else if (key.equals("NEW"))         { search.append("(is:read "); insertFlag("\\RECENT", search, insertions);
                                                  search.append(')'); }
            else if (key.equals("OLD"))         { search.append('-'); insertFlag("\\RECENT", search, insertions); }
            else if (key.equals("SEEN"))        search.append("is:read");
            else if (key.equals("UNANSWERED"))  search.append("is:unreplied");
            else if (key.equals("UNDELETED"))   search.append("-tag:\\Deleted");
            else if (key.equals("UNDRAFT"))     search.append("-is:draft");
            else if (key.equals("UNFLAGGED"))   search.append("is:unflagged");
            else if (key.equals("UNSEEN"))      search.append("is:unread");
            // XXX: BCC always returns no results because we don't separately index that field
            else if (key.equals("BCC"))         { skipSpace(); search.append("item:none"); readAstring(charset); }
            else if (key.equals("BEFORE"))      { skipSpace(); search.append("before:").append(readAndReformatDate()); }
            else if (key.equals("BODY"))        { skipSpace(); readAndQuoteString(search, charset); }
            else if (key.equals("CC"))          { skipSpace(); search.append("cc:"); readAndQuoteString(search, charset); }
            else if (key.equals("FROM"))        { skipSpace(); search.append("from:"); readAndQuoteString(search, charset); }
            else if (key.equals("HEADER"))      { skipSpace(); String hdr = readAstring().toUpperCase(), prefix = (String) INDEXED_HEADER.get(hdr);
                                                  if (prefix == null)  throw new ImapParseException(mTag, "unindexed header: " + hdr);
                                                  skipSpace(); search.append(prefix); readAndQuoteString(search, charset); }
            else if (key.equals("KEYWORD"))     { skipSpace(); ImapFlag i4flag = getFlag(readAtom());
                                                  if (i4flag != null && !i4flag.mPositive)   search.append('-');
                                                  if (i4flag != null && !i4flag.mPermanent)  insertFlag(i4flag, search, insertions);
                                                  else  search.append(i4flag == null ? "item:none" : "tag:" + i4flag.mName); }
            else if (key.equals("LARGER"))      { skipSpace(); search.append("larger:").append(readNumber()); }
            else if (key.equals("ON"))          { skipSpace(); search.append("date:").append(readAndReformatDate()); }
            // FIXME: SENTBEFORE, SENTON, and SENTSINCE reference INTERNALDATE, not the Date header
            else if (key.equals("SENTBEFORE"))  { skipSpace(); search.append("before:").append(readAndReformatDate()); }
            else if (key.equals("SENTON"))      { skipSpace(); search.append("date:").append(readAndReformatDate()); }
            else if (key.equals("SENTSINCE"))   { skipSpace(); search.append("after:").append(readAndReformatDate()); }
            else if (key.equals("SINCE"))       { skipSpace(); search.append("after:").append(readAndReformatDate()); }
            else if (key.equals("SMALLER"))     { skipSpace(); search.append("smaller:").append(readNumber()); }
            else if (key.equals("SUBJECT"))     { skipSpace(); search.append("subject:"); readAndQuoteString(search, charset); }
            else if (key.equals("TEXT"))        { skipSpace(); readAndQuoteString(search, charset); }
            else if (key.equals("TO"))          { skipSpace(); search.append("to:"); readAndQuoteString(search, charset); }
            else if (key.equals("UID"))         { skipSpace(); insertions.put(new Integer(search.length()), '-' + readSequence()); }
            else if (key.equals("UNKEYWORD"))   { skipSpace(); ImapFlag i4flag = getFlag(readAtom());
                                                  if (i4flag != null && i4flag.mPositive)    search.append('-');
                                                  if (i4flag != null && !i4flag.mPermanent)  insertFlag(i4flag, search, insertions);  
                                                  else  search.append(i4flag == null ? "item:all" : "tag:" + i4flag.mName); }
            else if (key.equals(SUBCLAUSE))     { skipChar('(');  readSearchClause(search, insertions, charset, MULTIPLE_CLAUSES);  skipChar(')'); }
            else if (Character.isDigit(key.charAt(0)) || key.charAt(0) == '*')
                insertions.put(new Integer(search.length()), validateSequence(key));
            else if (key.equals("OR")) {
                search.append("((");      skipSpace();  readSearchClause(search, insertions, charset, SINGLE_CLAUSE);
                search.append(") or (");  skipSpace();  readSearchClause(search, insertions, charset, SINGLE_CLAUSE);
                search.append("))");
            }
            else throw new ImapParseException(mTag, "unknown search tag: " + key);

            search.append(' ');
            first = false;
        } while (peekChar() != -1 && peekChar() != ')' && (nots > 0 || !single));

        if (nots > 0)
            throw new ImapParseException(mTag, "missing search-key after NOT");
        return search;
    }
    String readSearch(TreeMap insertions) throws IOException, ImapException {
        String charset = null;
        StringBuffer search = new StringBuffer();
        int c = peekChar();
        if (c == 'c' || c == 'C') {
            int offset = mOffset, index = mIndex;
            String first = readAtom();
            if (first.equals("CHARSET")) {
                skipSpace();  charset = readAstring();  skipSpace();
                boolean charsetOK = false;
                try {
                    charsetOK = Charset.isSupported(charset);
                } catch (IllegalCharsetNameException icne) { }
                if (!charsetOK)
                    throw new ImapParseException(mTag, "BADCHARSET", "unknown charset: " + charset);
            } else {
                mOffset = offset;  mIndex = index;
            }
        }
        return readSearchClause(search, insertions, charset, MULTIPLE_CLAUSES).toString();
    }
}
