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
package com.zimbra.cs.taglib.tag.contact;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;

import javax.servlet.jsp.JspTagException;
import java.util.HashMap;
import java.util.Map;

public class ContactOpTag extends ZimbraSimpleTag {

    protected boolean mForce;
    protected Map<String, String> mAttrs = new HashMap<String,String>();

    public void setForce(boolean force) { mForce = force; }

    public void addAttr(String name, String value) throws JspTagException {
        if (!mForce) {
            try {
                Contact.Attr.fromString(name); // make sure it is a known attr name
            } catch (ServiceException e) {
                throw new JspTagException(e);
            }
        }
        mAttrs.put(name, value);
    }

    protected boolean allFieldsEmpty() {
        for (Map.Entry<String,String> entry : mAttrs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().trim().length() > 0 && !entry.getKey().equalsIgnoreCase(Contact.A_fileAs))
                return false;
        }
        return true;
    }
}
