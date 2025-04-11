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

package com.zimbra.cs.taglib.tag.voice;

import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;
import com.zimbra.cs.taglib.bean.ZCallFeaturesBean;
import com.zimbra.cs.taglib.bean.ZVoiceMailPrefsBean;
import com.zimbra.cs.taglib.bean.ZCallForwardingBean;
import com.zimbra.cs.taglib.bean.ZSelectiveCallForwardingBean;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZPhoneAccount;
import com.zimbra.cs.zclient.ZCallFeatures;
import com.zimbra.common.service.ServiceException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.JspTagException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class CreateCallFeaturesTag extends ZimbraSimpleTag {

    private String mVar;
    private String mPhone;
    private boolean mEmailNotificationActive;
    private String mEmailNotificationAddress;
    private boolean mCallForwardingActive;
    private String mCallForwardingForwardTo;
    private boolean mSelectiveCallForwardingActive;
    private String mSelectiveCallForwardingForwardTo;
    private List<String> mSelectiveCallForwardingForwardFrom;

    public void setVar(String var) { mVar = var; }
    public void setPhone(String phone) { mPhone = phone; }
    public void setEmailnotificationactive(String active) { mEmailNotificationActive = booleanValue(active); }
    public void setEmailnotificationaddress(String address) { mEmailNotificationAddress = address.trim(); }
    public void setCallforwardingactive(String active) { mCallForwardingActive = booleanValue(active); }
    public void setCallforwardingforwardto(String number) { mCallForwardingForwardTo = number.trim(); }
    public void setSelectivecallforwardingactive(String active) { mSelectiveCallForwardingActive = booleanValue(active); }
    public void setSelectivecallforwardingforwardto(String number) { mSelectiveCallForwardingForwardTo = number.trim(); }
    public void setSelectivecallforwardingforwardfrom(String[] numbers) {
        mSelectiveCallForwardingForwardFrom = new ArrayList<String>(numbers.length);
        for (String number : numbers) {
            mSelectiveCallForwardingForwardFrom.add( number.trim());
        }
    }

    public void doTag() throws JspException, IOException {
        try {
            ZMailbox mailbox = getMailbox();
            ZPhoneAccount account = mailbox.getPhoneAccount(mPhone);
            ZCallFeaturesBean newFeatures = new ZCallFeaturesBean(new ZCallFeatures(mailbox, account.getPhone()), true);

            String address = mEmailNotificationActive ? mEmailNotificationAddress : "";
            newFeatures.getVoiceMailPrefs().setEmailNotificationAddress(address);

            ZCallForwardingBean newCallForwarding = newFeatures.getCallForwardingAll();
            newCallForwarding.setIsActive(mCallForwardingActive);
            newCallForwarding.setForwardTo(mCallForwardingForwardTo);

            ZSelectiveCallForwardingBean newSelectiveCallForwarding = newFeatures.getSelectiveCallForwarding();
            newSelectiveCallForwarding.setIsActive(mSelectiveCallForwardingActive);
            newSelectiveCallForwarding.setForwardTo(mSelectiveCallForwardingForwardTo);
            newSelectiveCallForwarding.setForwardFrom(mSelectiveCallForwardingForwardFrom);

            getJspContext().setAttribute(mVar, newFeatures, PageContext.PAGE_SCOPE);
        } catch (ServiceException e) {
            throw new JspTagException(e);
        }
    }

    private boolean booleanValue(String value) {
        return "TRUE".equals(value);
    }
}
