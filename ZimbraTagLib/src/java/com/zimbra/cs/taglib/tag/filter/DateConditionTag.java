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
package com.zimbra.cs.taglib.tag.filter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;
import com.zimbra.cs.taglib.bean.ZTagLibException;
import com.zimbra.cs.zclient.ZFilterCondition.DateOp;
import com.zimbra.cs.zclient.ZFilterCondition.ZDateCondition;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateConditionTag extends ZimbraSimpleTag {

    private DateOp mOp;
    private String mValue;


    public void setValue(String value) { mValue = value; }
    public void setOp(String op) throws ServiceException { mOp = DateOp.fromString(op); }

    public void doTag() throws JspException {
        try {
            FilterRuleTag rule = (FilterRuleTag) findAncestorWithClass(this, FilterRuleTag.class);
            if (rule == null)
                throw new JspTagException("The dateCondition tag must be used within a filterRule tag");
            if (mValue == null || mValue.equals("")) {
                mValue = new SimpleDateFormat("yyyyMMdd").format(new Date());
            }
            rule.addCondition(new ZDateCondition(mOp, mValue));
        } catch (ServiceException e) {
            throw new JspTagException(ZTagLibException.INVALID_FILTER_DATE(e.getMessage(), e));
        }
    }

}
