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
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.cs.zclient.ZMailbox.SearchSortBy;
import com.zimbra.cs.zclient.event.ZModifySearchFolderEvent;
import com.zimbra.cs.zclient.event.ZModifyEvent;
import com.zimbra.soap.Element;

public class ZSearchFolder extends ZFolder {


    private String mQuery;
    private String mTypes;
    private SearchSortBy mSortBy;
    
    public ZSearchFolder(Element e, ZFolder parent) throws ServiceException {
        super(e, parent);
        mQuery = e.getAttribute(MailService.A_QUERY);
        mTypes = e.getAttribute(MailService.A_SEARCH_TYPES, null);
        try {
            mSortBy = SearchSortBy.fromString(e.getAttribute(MailService.A_SORTBY, SearchSortBy.dateDesc.name()));
        } catch (ServiceException se) {
            mSortBy = SearchSortBy.dateDesc;
        }
    }

    public void modifyNotification(ZModifyEvent e) throws ServiceException {
        if (e instanceof ZModifySearchFolderEvent) {
            ZModifySearchFolderEvent sfe = (ZModifySearchFolderEvent) e;
            if (sfe.getId().equals(getId())) {
                mQuery = sfe.getQuery(mQuery);
                mTypes = sfe.getTypes(mTypes);
                mSortBy = sfe.getSortBy(mSortBy);
                super.modifyNotification(e);
            }
        }
    }

    public String toString() {
        ZSoapSB sb = new ZSoapSB();
        sb.beginStruct();
        sb.add("query", mQuery);
        sb.add("types", mTypes);
        sb.add("sortBy", mSortBy.name());
        toStringCommon(sb);
        sb.endStruct();
        return sb.toString();
    }

    public String getQuery() {
        return mQuery;
    }

    public SearchSortBy getSortBy() {
        return mSortBy;
    }

    public String getTypes() {
        return mTypes;
    }
    
}
