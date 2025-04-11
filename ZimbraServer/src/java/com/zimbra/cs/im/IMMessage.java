/* ***** BEGIN LICENSE BLOCK *****
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

package com.zimbra.cs.im;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.zimbra.common.soap.Element;

/**
 *
 */
public class IMMessage {
    public static enum Lang {
        EN, DEFAULT;
    }
    
    public static class TextPart {
        private Lang mLang;
        private String mText;
        
        public TextPart(Lang lang, String text) {
            mLang = lang;
            mText = text;
        }
        
        public TextPart(String text) {
            mLang = Lang.DEFAULT;
            mText = text;
        }
        
        public Lang getLang() { return mLang; }
        public String getHtmlText() { return mText; }
        
        public String getPlainText() {
            // TODO: strip HTML tags here
            return mText;
        }
        
        @Override
        public String toString() {
            return mText;
        }
    }
    
    @Override
    public String toString() {
        return "MESSAGE("+mDate+"): "+mSubject+" "+mBody;
    }
    
    public void setFrom(IMAddr from) { mFrom = from; }

    public void setTo(IMAddr to) {
	mTo = to;
    }
    
    
    public IMMessage(TextPart subject, TextPart body, boolean isTyping) {
        mSubject = subject;
        mBody = body;
        mDate = new Date();
        mIsTyping = isTyping;
    }
    
    void addSubject(TextPart subj) {
        if (subj.mLang == Lang.DEFAULT) {
            mSubject = subj;
            return;
        }
        
        if (mSubject == null) {
            mSubject = subj;
        }

        if (mLangSubjects == null) {
            mLangSubjects = new HashMap();
        }
        
        mLangSubjects.put(subj.mLang, subj);
    }
    
    void addBody(TextPart body) {
        if (body.mLang == Lang.DEFAULT) {
            mBody = body;
            return;
        }
        
        if (mBody == null) {
            mBody = body;
        }
        
        if (mLangBodies == null) {
            mLangBodies = new HashMap();
        }
        
        mLangBodies.put(body.mLang, body);
    }
    
    public boolean isTyping() { return mIsTyping; }
    
    private TextPart mSubject;
    private TextPart mBody;
    
    private Map<Lang, TextPart> mLangSubjects;
    private Map<Lang, TextPart> mLangBodies;
    private Date mDate;
    private IMAddr mFrom;
    private IMAddr mTo;
    private boolean mIsTyping;
    
    public Element toXml(Element parent) {
        {
            if (mSubject != null) {
                Element e = parent.addElement("subject");
                e.setText(mSubject.getHtmlText());
            }
        }
        {
            if (mBody != null) {
                Element e = parent.addElement("body");
                e.setText(mBody.getHtmlText());
            }
        }
        return parent;
    }

    public IMAddr getFrom() { return mFrom; }
    public IMAddr getTo() { return mTo; }
    public long getTimestamp() { return mDate.getTime(); }
    public Date getDate() { return mDate; }
    
    public TextPart getSubject(Lang lang) {
        if (lang == Lang.DEFAULT
                || (mSubject != null && mSubject.mLang == lang)
                || mLangSubjects==null 
                || !mLangSubjects.containsKey(lang)
        ) {
            return mSubject;
        }
        
        return mLangSubjects.get(lang);
    }
    
    public TextPart getBody() {
        return getBody(Lang.DEFAULT);
    }
    
    public TextPart getBody(Lang lang) {
        if (lang == Lang.DEFAULT
                || (mBody != null && mBody.mLang == lang)
                || mLangBodies ==null 
                || !mLangBodies.containsKey(lang)
        ) {
            return mBody;
        }
        
        return mLangBodies.get(lang);
    }
}
