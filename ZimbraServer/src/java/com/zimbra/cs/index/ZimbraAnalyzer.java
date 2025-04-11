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
 * Created on Apr 26, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.zimbra.cs.index;

import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import com.zimbra.common.service.ServiceException;

/***
 * 
 * @author tim
 * 
 * Global analyzer wrapper for Zimbra Indexer.  You DO NOT need to instantiate multiple 
 * copies of this class -- just call ZimbraAnalyzer.getInstance() whenever you need an 
 * instance of this class. 
 *
 */
public class ZimbraAnalyzer extends StandardAnalyzer 
{
    /***
     * Returns the Analyzer to be used 
     * 
     * @param name
     * @return
     * @throws ServiceException
     */
    public static Analyzer getAnalyzer(String name) {
        Analyzer toRet = sAnalyzerMap.get(name);
        if (toRet == null)
            return getDefaultAnalyzer();
        return toRet;
    }
    
    //
    // We maintain a single global instance for our default analyzer, 
    // since it is completely thread safe
    private static ZimbraAnalyzer sInstance = new ZimbraAnalyzer();
    public static Analyzer getDefaultAnalyzer() { return sInstance; }
    

    ///////////////////////
    //
    // Extension Analyzer Map
    //
    // Extension analyzers must call registerAnalyzer() on startup.  
    //
    private static HashMap<String, Analyzer> sAnalyzerMap = new HashMap<String, Analyzer>();
    
    
    /**
     * @param name is a unique name identifying the Analyzer, it is referenced by Account or COS settings in LDAP.
     * @param analyzer is a Lucene analyzer instance which can be used by accounts that are so 
     * configured. 
     * @throws ServiceException
     * 
     * A custom Lucene Analyzer is registered with this API, usually by a Zimbra Extension.  
     * Accounts are configured to use a particular analyzer by setting the "zimbraTextAnalyzer" key
     * in the Account or COS setting.
     * 
     * The custom analyzer is assumed to be a stateless single instance (although it can and probably should
     * return a new TokenStream instance from it's APIs)
     * 
     */
    public static void registerAnalyzer(String name, Analyzer analyzer) throws ServiceException {
        if (sAnalyzerMap.containsKey(name)) 
            throw ServiceException.FAILURE("Cannot register analyzer: "+name+" because there is one already registered with that name.", null);

        sAnalyzerMap.put(name, analyzer); 
    }
    
    
    /**
     * @param name
     * 
     * Remove a previously-registered custom Analyzer from the system.
     */
    public static void unregisterAnalyzer(String name) { sAnalyzerMap.remove(name); }


    
    
    ///////////////////////////
    //
    //
    //
    public static String getAllTokensConcatenated(String fieldName, String text) {
        Reader reader = new StringReader(text);
        return getAllTokensConcatenated(fieldName, reader);
    }

    public static String getAllTokensConcatenated(String fieldName, Reader reader) {

        StringBuffer toReturn = new StringBuffer();

        TokenStream stream = ZimbraAnalyzer.sInstance.tokenStream(fieldName, reader);

        try {

            for (Token cur = stream.next(); cur != null; cur = stream.next()) 
            {
                toReturn.append(cur.termText());
                toReturn.append(" ");
            }
        } catch(IOException e) {
            e.printStackTrace(); //otherwise eat it
        }

        return toReturn.toString();
    }


    protected ZimbraAnalyzer() {
        super();
    }
    
    /**
     * "Tokenizer" which returns the entire input as a single token.  Used for KEYWORD fields.
     */
    protected static class DontTokenizer extends CharTokenizer {
        DontTokenizer(Reader input) { super(input); }
        protected boolean isTokenChar(char c) { return true; }
    }

    public TokenStream tokenStream(String fieldName, Reader reader) {
        if (fieldName.equals(LuceneFields.L_H_MESSAGE_ID))
            return new DontTokenizer(reader);
        
        if (fieldName.equals(LuceneFields.L_ATTACHMENTS) ||
                    fieldName.equals(LuceneFields.L_MIMETYPE)) 
        {
            return new MimeTypeTokenFilter(CommaSeparatedTokenStream(reader));
        } else if (fieldName.equals(LuceneFields.L_SIZE)) {
            return new SizeTokenFilter(new NumberTokenStream(reader));
        } else if (fieldName.equals(LuceneFields.L_H_FROM) 
                    || fieldName.equals(LuceneFields.L_H_TO) 
                    || fieldName.equals(LuceneFields.L_H_CC)
                    || fieldName.equals(LuceneFields.L_H_X_ENV_FROM)
                    || fieldName.equals(LuceneFields.L_H_X_ENV_TO)
                    ) 
        {
            return new AddressTokenFilter(new AddrCharTokenizer(reader));
        } else if (fieldName.equals(LuceneFields.L_FILENAME)) {
            return new FilenameTokenFilter(CommaSeparatedTokenStream(reader));
        } else {
            return super.tokenStream(fieldName, reader);
        }
    }

    /**
     * numbers separated by ' ' or '\t'
     */
    protected static class NumberTokenStream extends Tokenizer {
        protected Reader mReader;
        protected int mEndPos = 0;

        NumberTokenStream(Reader reader) {
            super(reader);
        }

        public org.apache.lucene.analysis.Token next() throws IOException
        {
            int startPos = mEndPos;
            StringBuffer buf = new StringBuffer(10);

            while(true) {
                int c = input.read();
                mEndPos++;
                switch(c) {
                    case -1:
                        if (buf.length() == 0) {
                            return null;
                        }
                        // no break!
                    case ' ':
                    case '\t':
                        if (buf.length() != 0) {
                            String toRet = buf.toString();
                            return new org.apache.lucene.analysis.Token(toRet, startPos, mEndPos-1);
                        }
                        break;
                    case '0': case '1': case '2': case '3': case '4': 
                    case '5': case '6': case '7': case '8': case '9': 
                        buf.append((char)c);
                        break;
                    default:
                        // ignore char
                } // switch
            } // while
        } // next()
    } // class

    /**
     * 
     * NumberTokenStream converted into ascii-sortable (base-36 ascii encoded) 
     * numbers
     *
     */
    public static class SizeTokenFilter extends TokenFilter {

        SizeTokenFilter(TokenStream in) {
            super(in);
        }
        SizeTokenFilter(TokenFilter in) {
            super(in);
        }

        public static String EncodeSize(String size) 
        {
            return size;
//          try {
//          return EncodeSize(Long.parseLong(size));
//          } catch(Exception e) {
//          // skip this token then...
//          return null;
//          }
        }
        public static String EncodeSize(long lsize) {
//          String encoded;
//          try {
//          encoded = Long.toString(lsize, Character.MAX_RADIX);
//          } catch(Exception e) {
//          // skip this token then...
//          return null;
//          }

//          StringBuffer toRet = new StringBuffer(10);
//          for (int i = encoded.length(); i < 10; i++) { 
//          toRet.append('0');
//          }
//          toRet.append(encoded);
//          return toRet.toString();
            return Integer.toString((int)lsize);
        }

        public static long DecodeSize(String size) {
//          return Long.parseLong(size, Character.MAX_RADIX);
            return Integer.parseInt(size);
        }

        public org.apache.lucene.analysis.Token next() throws IOException 
        {
            org.apache.lucene.analysis.Token t = input.next();
            if (t == null) {
                return null;
            }

            String sizeFieldStr = EncodeSize(t.termText());
            if (sizeFieldStr == null) {
                return next();
            }
            return new org.apache.lucene.analysis.Token(
                        sizeFieldStr.toString(), 
                        t.startOffset(),
                        t.endOffset(),
                        t.type());
        }
    }

    /**
     * comma-separated values, typically for content type list
     * @param reader
     * @return TokenStream
     */
    private TokenStream CommaSeparatedTokenStream(Reader reader) {
        return new CharTokenizer(reader) {
            protected boolean isTokenChar(char c) {
                return c != ',';
            }
            protected char normalize(char c) {
                return Character.toLowerCase(c);
            }
        };
    }


    public static abstract class MultiTokenFilter extends TokenFilter{
        MultiTokenFilter(TokenStream in) {
            super(in);
        }
        MultiTokenFilter(TokenFilter in) {
            super(in);
        }


        // returns the next split point
        abstract protected int getNextSplit(String s);

        protected int mMaxSplits = 1;
        protected boolean mIncludeSeparatorChar = false;
        protected boolean mNoLastToken = false;
        org.apache.lucene.analysis.Token mCurToken = null;
        protected int mNextSplitPos;
        protected int mNumSplits;
        public static boolean sPrintNewTokens = false;

        public org.apache.lucene.analysis.Token nextInternal() throws IOException 
        {
            if (mCurToken == null) {
                // Step 1: get a new token, and insert the full token (unsplit) into
                //         into the index
                org.apache.lucene.analysis.Token newTok = input.next();
                if (newTok == null) {
                    return null;
                }
                if (sPrintNewTokens == true) {
                    System.out.println("\nnew token: "+newTok.toString());
                }


                // Does it have any sub-parts that need to be added separately?  
                // If so, then save them as internal state: we'll add them in a bit.
                String termText = newTok.termText();
                mNextSplitPos = getNextSplit(termText);
                if (mNextSplitPos <= 0) {
                    // no sub-tokens
                    return newTok;
                }

                // Now, Insert the full string as a token...we might continue down below
                // (other parts) if there is more to add...
                mNumSplits = 0;
                mCurToken = new org.apache.lucene.analysis.Token(
                            termText, newTok.startOffset(), 
                            newTok.endOffset(), newTok.type());

                return newTok;
            }
            
            // once we get here, we know that the full text has been inserted 
            // once as a single token, now we need to insert all the split tokens 
            return nextSplit();
        }

        /**
         * At this point, a token has been extraced from input, and the full token
         * has been returned to the stream.  Now we want to return all the "split" 
         * forms of the token.
         * 
         * On the first call to this API for this token, mNextSplitPos is set to the value
         * of getNextSplit(full_token_text)....then this API is called repeatedly until
         * mCurToken is cleared.
         */
        public org.apache.lucene.analysis.Token nextSplit()
        {
            if (mNextSplitPos>0 && mNumSplits < mMaxSplits) {
                // split another piece, save our state, and return...
                mNumSplits++;
                String termText = mCurToken.termText();
                String stringToRet = termText.substring(0,mNextSplitPos);

                org.apache.lucene.analysis.Token tokenToReturn = 
                    new org.apache.lucene.analysis.Token(stringToRet, 
                                mCurToken.startOffset(),
                                mCurToken.startOffset()+mNextSplitPos,
                                mCurToken.type());

                if (!mIncludeSeparatorChar) {
                    mNextSplitPos++;
                }
                String secondPart = termText.substring(mNextSplitPos);
                if (mNumSplits < mMaxSplits) {
                    mNextSplitPos = getNextSplit(secondPart);
                }

                if (mNoLastToken == true) {
                    mCurToken = null;
                } else {
                    mCurToken = new org.apache.lucene.analysis.Token(secondPart, 
                                mCurToken.startOffset()+mNextSplitPos, 
                                mCurToken.endOffset(),
                                mCurToken.type());
                }

                return tokenToReturn;
            }

            // if we get here, then we've either split as many times as we're allowed, 
            // OR we've run out of places to split..


            // no more splitting, just return what's left...
            org.apache.lucene.analysis.Token toRet = mCurToken;
            mCurToken = null;
            return toRet;
        }


        public org.apache.lucene.analysis.Token next() throws IOException {
            org.apache.lucene.analysis.Token t;
            do {
                t = nextInternal();
            } while (t != null &&
                        t.termText().length() <= 1); 
            return t;
        }
    }

    /***
     * 
     * @author tim
     *
     * Email address tokenizer.  For example:
     *   "Tim Brennan" <tim@foo.com>
     * Is tokenized as:
     *    Tim
     *    Brennan
     *    tim
     *    tim@foo.com
     *    @foo.com
     */
    public static class AddressTokenFilter extends MultiTokenFilter
    {
        AddressTokenFilter(TokenFilter in) {
            super(in);
            mIncludeSeparatorChar = true;
            mMaxSplits = 4;
        }
        AddressTokenFilter(TokenStream in) {
            super(in);
            mIncludeSeparatorChar = true;
            mMaxSplits = 4;
        }

        protected int getNextSplit(String s) {
            return s.indexOf("@");
        }
        
        LinkedList<org.apache.lucene.analysis.Token> mSplitStrings = null;
        
        public org.apache.lucene.analysis.Token nextSplit()
        {
            if (mSplitStrings == null) {
                mSplitStrings = new LinkedList<org.apache.lucene.analysis.Token>();
                
                String termText = mCurToken.termText();
                // split on the "@"
                String lhs = termText.substring(0, mNextSplitPos);
                
                // yes, we want to include the @!
                String rhs = termText.substring(mNextSplitPos);
                
                // now, split the first part on the "."
                String[] lhsParts = lhs.split("\\.");
                if (lhsParts.length > 1) {
                    int curOffset = mCurToken.startOffset();
                    for (String s : lhsParts) {
                        mSplitStrings.addLast(
                            new org.apache.lucene.analysis.Token(
                                s,
                                curOffset,
                                curOffset+s.length(),
                                mCurToken.type()));
                        
                        curOffset+=s.length()+1;
                    }
                }
                
                mSplitStrings.addLast(
                    new org.apache.lucene.analysis.Token(
                        lhs,
                        mCurToken.startOffset(),
                        mCurToken.startOffset()+mNextSplitPos,
                        mCurToken.type()));
                
                mSplitStrings.addLast(
                    new org.apache.lucene.analysis.Token(
                        rhs,
                        mCurToken.startOffset()+mNextSplitPos,
                        mCurToken.endOffset(),
                        mCurToken.type()));
            }
            
            // split another piece, save our state, and return...
            mNumSplits++;
            org.apache.lucene.analysis.Token toRet = mSplitStrings.removeFirst();
            
            if (mSplitStrings.isEmpty()) {
                mSplitStrings = null;
                mCurToken = null;
            }
            
            return toRet;
        }
    }

    /***
     * 
     * @author tim
     *
     * Filename tokenizer
     *   foo.doc
     * Is tokenized as:
     *    foo.doc
     *    foo
     *    .doc
     */
    public static class FilenameTokenFilter extends MultiTokenFilter
    {
        FilenameTokenFilter(TokenFilter in) {
            super(in);
            mIncludeSeparatorChar = true;
        }
        FilenameTokenFilter(TokenStream in) {
            super(in);
            mIncludeSeparatorChar = true;
        }

        protected int getNextSplit(String s) {
            return s.lastIndexOf(".");
        }
    }


    /**
     * Tokenizer for email addresses.  Skips&Splits at \r\n<>\",\'
     */
    private static class AddrCharTokenizer extends CharTokenizer
    {
        public AddrCharTokenizer(Reader reader) {
            super(reader);
        }
        protected boolean isTokenChar(char c) {
            switch (c) {
                case ' ':
                case '\r':
                case '\n':
                case '<':
                case '>':
                case '\"':
                case ',':
                case '\'':
                case '(':
                case ')':
                case '[':
                case ']':
                    return false;
            }
            return true;
        }
        protected char normalize(char c) {
            return Character.toLowerCase(c);
        }
    }  

    /**
     * 
     * @author tim
     *
     * image/jpeg --> "image/jpeg" and "image" 
     */
    public static class MimeTypeTokenFilter extends MultiTokenFilter
    {

        MimeTypeTokenFilter(TokenFilter in) {
            super(in);
            init();
        }
        MimeTypeTokenFilter(TokenStream in) {
            super(in);
            init();
        }

        void init() {
            mMaxSplits = 1;
            mNoLastToken = true;    		
        }

        /* (non-Javadoc)
         * @see com.zimbra.cs.index.ZimbraAnalyzer.MultiTokenFilter#getNextSplit(java.lang.String)
         */
        protected int getNextSplit(String s) {
            return s.indexOf("/");
        }
    }

    public static class TestTokenStream extends TokenStream
    {
        int curPos;
        String[] mStringList;

        TestTokenStream(String[] stringlist) {
            mStringList = stringlist;
            curPos = 0;
        }

        public org.apache.lucene.analysis.Token next() {
            curPos++;
            if (curPos > mStringList.length) {
                return null;
            }
            return new org.apache.lucene.analysis.Token(mStringList[curPos-1], 
                0, mStringList[curPos].length());
        }
    }

    
    public static void main(String[] args)
    {
        MultiTokenFilter.sPrintNewTokens = true;
        ZimbraAnalyzer la = new ZimbraAnalyzer();
        String str = "tim@foo.com \"Test Address\" <test.address@mail.nnnn.com>, image/jpeg, text/plain, text/foo/bar, tim (tim@foo.com),bugzilla-daemon@eric.example.zimbra.com, zug zug [zug@gug.com], Foo.gub, \"My Mom\" <mmm@nnnn.com>,asd foo bar aaa/bbb ccc/ddd/eee fff@ggg.com hhh@iiii";
        {
            System.out.print("AddressTokenFilter:\n-------------------------");
            StringReader reader = new StringReader(str);

            TokenStream filter1 = la.tokenStream(LuceneFields.L_H_FROM, reader);
//          int  i = 1;
            Token tok = null;
            do {
                try {
                    tok = filter1.next();
                } catch (IOException e) {}
                if (tok!=null) {
                    System.out.println("    "+tok.toString());
                }
            } while(tok != null);
        }

        {
            String src = "\"Tim Brown\" <first@domain.com>";
            String concat = ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_FROM, src);

            System.out.println("SRC="+src+" OUT=\""+concat+"\"");
        }

        {
            String src = "dharma@dharma.com";
            String concat = ZimbraAnalyzer.getAllTokensConcatenated(LuceneFields.L_H_FROM, src);

            System.out.println("SRC="+src+" OUT=\""+concat+"\"");
        }        


        {
            System.out.print("\nATTACHMENTS: MimeTypeTokenFilter:\n-------------------------");
            StringReader reader = new StringReader(str);
            TokenStream filter1 = la.tokenStream(LuceneFields.L_ATTACHMENTS, reader);

//          int  i = 1;
            Token tok = null;
            do {
                try {
                    tok = filter1.next();
                } catch (IOException e) {}
                if (tok!=null) {
                    System.out.println("    "+tok.termText());
                }
            } while(tok != null);
        }
        {
            System.out.print("\nTYPE: MimeTypeTokenFilter:\n-------------------------");
            StringReader reader = new StringReader(str);
            TokenStream filter1 = la.tokenStream(LuceneFields.L_MIMETYPE, reader);

            //          int  i = 1;
            Token tok = null;
            do {
                try {
                    tok = filter1.next();
                } catch (IOException e) {}
                if (tok!=null) {
                    System.out.println("    "+tok.termText());
                }
            } while(tok != null);
        }

        {
            str = "123 26 1000000 100000000 1,000,000,000 1,000,000,000,000,000";
            System.out.println("\nMimeTypeTokenFilter:\n-------------------------");
            StringReader reader = new StringReader(str);
            TokenStream filter1 = la.tokenStream(LuceneFields.L_SIZE, reader);

//          int  i = 1;
            Token tok = null;
            do {
                try {
                    tok = filter1.next();
                } catch (IOException e) {}
                if (tok!=null) {
                    System.out.println("    "+tok.termText());
                }
            } while(tok != null);
        }

    }
}

