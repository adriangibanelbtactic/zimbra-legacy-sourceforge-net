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

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.DistributionListBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class RenameDistributionList extends AdminDocumentHandler {

    /**
     * must be careful and only allow access to domain if domain admin
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext lc = getZimbraSoapContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String id = request.getAttribute(AdminConstants.E_ID);
        String newName = request.getAttribute(AdminConstants.E_NEW_NAME);

	    DistributionList dl = prov.get(DistributionListBy.id, id);
        if (dl == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(id);

        if (!canAccessEmail(lc, dl.getName()))
            throw ServiceException.PERM_DENIED("can not access dl");

        if (!canAccessEmail(lc, newName)) //
            throw ServiceException.PERM_DENIED("can not access email: "+newName);
        
        String oldName = dl.getName();

        prov.renameDistributionList(id, newName);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "RenameDistributionList", "name", oldName, "newName", newName})); 
        
        // get again with new name...

        dl = prov.get(DistributionListBy.id, id);
        if (dl == null)
            throw ServiceException.FAILURE("unable to get distribution list after rename: " + id, null);
	    Element response = lc.createElement(AdminConstants.RENAME_DISTRIBUTION_LIST_RESPONSE);
	    GetDistributionList.doDistributionList(response, dl);
	    return response;

    }
}
