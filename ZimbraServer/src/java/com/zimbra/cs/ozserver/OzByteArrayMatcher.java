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

package com.zimbra.cs.ozserver;

import java.nio.ByteBuffer;

import com.zimbra.common.util.Log;

public class OzByteArrayMatcher implements OzMatcher {
   
    private Log mLog;
    private final boolean mTrace;
    
    public static final byte CR = 13;
    public static final byte LF = 10;
    public static final byte DOT = '.';
    
    public static final byte[] CRLF = new byte[]{ CR, LF };
    public static final byte[] CRLFCRLF = new byte[]{ CR, LF, CR, LF };
    public static final byte[] CRLFDOTCRLF = new byte[] { CR, LF, DOT, CR, LF };

    private final byte[] mMatchSequence;
    private final int mMatchSequenceLength;
    private int mMatched;
    
    public String toString() {
    	StringBuilder toRet = new StringBuilder("OzByteArrayMatcher(");
    	if (mMatchSequence == CRLF)
    		toRet.append("CRLF, ");
    	else if (mMatchSequence == CRLFDOTCRLF) 
    		toRet.append("CRLFDOTCRLF, ");
    	else {
    		toRet.append("'");
    		for (byte b : mMatchSequence) 
    			toRet.append(b);
    		toRet.append("', ");
    	}
    	
    	toRet.append(mMatched);
    	
    	toRet.append(")");
    	return toRet.toString();
    }
    
    
    /** After maxBytes bytes are processed an OzOverflowException is thrown. */ 
    public OzByteArrayMatcher(byte[] endSequence, Log log) {
        mMatchSequence = endSequence;
        mMatchSequenceLength = endSequence.length;
        mMatched = 0;
        mLog = log;
        if (mLog == null) {
        	mTrace = false;
        } else {
        	mTrace = log.isDebugEnabled();
        }
    }
    
    private void trace(String msg, Throwable t) { if (mTrace) mLog.debug(msg, t); }
    private void trace(String msg) { if (mTrace) mLog.debug(msg); }

    public boolean match(ByteBuffer buf) {
        assert(mMatched < mMatchSequenceLength);
        
        int n = buf.remaining();
        
        if (mTrace) trace("new bytes to look at=" + n + ", already matched=" + mMatched);
        
        StringBuilder tsb = null;
        if (mTrace) tsb = new StringBuilder("byte array matcher trace ");
        
        for (int i = 0; i < n; i++) {
            byte b = buf.get();

            if (mTrace) {
                if (b >= 32 && b <=126) tsb.append("'" + (char)b + "'/"); 
                if (mTrace) tsb.append((int)b + " ");
            }

            if (mMatchSequence[mMatched] == b) {
                mMatched++;
                if (mTrace) tsb.append("+" + mMatched + " ");
                if (mMatched == mMatchSequenceLength) {
                    if (mTrace) trace(tsb.toString());
                    return true;
                }
            } else {
                mMatched = 0; // break the match
                if (mMatchSequence[mMatched] == b) { // but now does it match start of sequence?
                    mMatched++;
                    if (mTrace) tsb.append("+" + mMatched + " ");
                    if (mMatched == mMatchSequenceLength) {
                        if (mTrace) trace(tsb.toString());
                        return true;
                    }
                }
            }
        }
        if (mTrace) trace(tsb.toString());
        return false;
    }

    public void reset() {
        mMatched = 0;
    }

    public int trailingTrimLength() {
        assert(matched());
        return mMatched;
    }

    public boolean matched() {
        return mMatched == mMatchSequenceLength;
    }
}
