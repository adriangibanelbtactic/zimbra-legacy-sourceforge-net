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
 * Portions created by Zimbra are Copyright (C) 2004, 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.client.soap;

import org.dom4j.Element;

import com.zimbra.soap.DomUtil;
import com.zimbra.cs.service.mail.MailService;

public class LmcSearchConvRequest extends LmcSearchRequest {

    private String mConvID;
    
    public void setConvID(String c) { mConvID = c; }
    
    public String getConvID() { return mConvID; }
    
	protected Element getRequestXML() {
        // the request XML is the same as for search, with a conversation ID added
        Element response = createQuery(MailService.SEARCH_CONV_REQUEST);
        DomUtil.addAttr(response, MailService.A_CONV_ID, mConvID);
        return response;
	}


}
