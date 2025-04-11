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
package com.zimbra.cs.zclient;

import com.zimbra.common.service.ServiceException;
import com.zimbra.soap.Element;
import com.zimbra.cs.service.mail.MailService;

import java.util.ArrayList;
import java.util.List;

public class ZFilterRules {

    private List<ZFilterRule> mRules;

    public List<ZFilterRule> getRules() {
        return mRules;
    }

    public ZFilterRules(List<ZFilterRule> rules) {
        mRules = rules;
    }

    public ZFilterRules(ZFilterRules rules) {
        mRules = new ArrayList<ZFilterRule>();
        mRules.addAll(rules.getRules());
    }

    public ZFilterRules(Element e) throws ServiceException {
        mRules = new ArrayList<ZFilterRule>();
        for (Element ruleEl : e.listElements(MailService.E_RULE)) {
            mRules.add(new ZFilterRule(ruleEl));
        }
    }

    Element toElement(Element parent) {
        Element r = parent.addElement(MailService.E_RULES);
        for (ZFilterRule rule : mRules) {
            rule.toElement(r);
        }
        return r;
    }

    public String toString() {
        ZSoapSB sb = new ZSoapSB();
        sb.beginStruct();
        sb.add("rules", mRules, false, true);
        sb.endStruct();
        return sb.toString();
    }
}
