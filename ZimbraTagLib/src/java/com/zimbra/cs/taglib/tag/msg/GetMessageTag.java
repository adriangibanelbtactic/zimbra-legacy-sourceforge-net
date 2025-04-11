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
package com.zimbra.cs.taglib.tag.msg;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.taglib.bean.ZMessageBean;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMessage;
import com.zimbra.cs.zclient.ZGetMessageParams;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.JspTagException;
import java.io.IOException;

public class GetMessageTag extends ZimbraSimpleTag {
    
    private String mVar;
    private String mId;
    private boolean mMarkread;
    private boolean mWanthtml;
    private boolean mWantHtmlSet;
    private boolean mNeuterimages;
    private boolean mRaw;
    private String mPart;
    
    public void setVar(String var) { this.mVar = var; }
    
    public void setId(String id) { this.mId = id; }    

    public void setMarkread(boolean markread) { this.mMarkread = markread; }
    public void setWanthtml(boolean wanthtml) {
        this.mWanthtml = wanthtml;
        this.mWantHtmlSet = true;
    }

    public void setRaw(boolean raw) { this.mRaw = raw; }
    public void setNeuterimages(boolean neuter) { this.mNeuterimages = neuter; }
    public void setPart(String part) { this.mPart = part; }
    
    public void doTag() throws JspException, IOException {
        JspContext jctxt = getJspContext();
        try {
            ZMailbox mbox = getMailbox();
            boolean wantHtml = mWantHtmlSet ? mWanthtml : mbox.getPrefs().getMessageViewHtmlPreferred();
            ZGetMessageParams params = new ZGetMessageParams();
            params.setId(mId);
            params.setMarkRead(mMarkread);
            params.setWantHtml(wantHtml);
            params.setNeuterImages(mNeuterimages);
            params.setRawContent(mRaw);
            params.setPart(mPart);
            ZMessage message = mbox.getMessage(params);
            jctxt.setAttribute(mVar, new ZMessageBean(message),  PageContext.PAGE_SCOPE);
        } catch (ServiceException e) {
            throw new JspTagException(e);
        }
    }
}
