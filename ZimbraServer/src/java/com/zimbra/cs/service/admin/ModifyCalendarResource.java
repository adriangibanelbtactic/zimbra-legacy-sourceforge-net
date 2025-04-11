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

package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AttributeManager;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.CalendarResourceBy;
import com.zimbra.cs.service.account.ToXML;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author jhahm
 */
public class ModifyCalendarResource extends AdminDocumentHandler {

    private static final String[] TARGET_RESOURCE_PATH = new String[] { AdminConstants.E_ID };
    protected String[] getProxiedResourcePath()  { return TARGET_RESOURCE_PATH; }

    /**
     * must be careful and only allow modifies to
     * calendar resources/attrs domain admin has access to
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        String id = request.getAttribute(AdminConstants.E_ID);
        Map<String, Object> attrs = AdminService.getAttrs(request);

        CalendarResource resource = prov.get(CalendarResourceBy.id, id);
        if (resource == null)
            throw AccountServiceException.NO_SUCH_CALENDAR_RESOURCE(id);

        if (!canAccessAccount(zsc, resource))
            throw ServiceException.PERM_DENIED("cannot access calendar resource account");

        if (isDomainAdminOnly(zsc)) {
            for (String attrName : attrs.keySet()) {
                if (!AttributeManager.getInstance().isDomainAdminModifiable(attrName))
                    throw ServiceException.PERM_DENIED("can not modify attr: " + attrName);
            }
        }

        // pass in true to checkImmutable
        prov.modifyAttrs(resource, attrs, true);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "ModifyCalendarResource", "name",
                              resource.getName()}, attrs));

        Element response = zsc.createElement(AdminConstants.MODIFY_CALENDAR_RESOURCE_RESPONSE);
        ToXML.encodeCalendarResourceOld(response, resource, true);
        return response;
    }
}
