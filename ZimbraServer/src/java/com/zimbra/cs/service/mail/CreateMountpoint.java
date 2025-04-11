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
 * Created on Sep 23, 2005
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.cs.session.Session;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author dkarp
 */
public class CreateMountpoint extends MailDocumentHandler {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.E_MOUNT, MailConstants.A_FOLDER };
    private static final String[] RESPONSE_ITEM_PATH = new String[] { };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return true; }
    protected String[] getResponseItemPath()  { return RESPONSE_ITEM_PATH; }

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(lc);
        Mailbox.OperationContext octxt = lc.getOperationContext();
        ItemIdFormatter ifmt = new ItemIdFormatter(lc);

        Element t = request.getElement(MailConstants.E_MOUNT);
        String name = t.getAttribute(MailConstants.A_NAME);
        String view = t.getAttribute(MailConstants.A_DEFAULT_VIEW, null);
        String flags = t.getAttribute(MailConstants.A_FLAGS, null);
        byte color = (byte) t.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
        ItemId iidParent = new ItemId(t.getAttribute(MailConstants.A_FOLDER), lc);
        boolean fetchIfExists = t.getAttributeBool(MailConstants.A_FETCH_IF_EXISTS, false);

        Account target = null;
        String ownerId = t.getAttribute(MailConstants.A_ZIMBRA_ID, null);
        if (ownerId == null) {
            String ownerName = t.getAttribute(MailConstants.A_OWNER_NAME);
            target = Provisioning.getInstance().get(AccountBy.name, ownerName);
            if (target == null)
                throw AccountServiceException.NO_SUCH_ACCOUNT(ownerName);
            ownerId = target.getId();
        }

        int remoteId  = (int) t.getAttributeLong(MailConstants.A_REMOTE_ID, -1);
        if (remoteId == -1) {
            String remotePath = t.getAttribute(MailConstants.A_PATH);
            remoteId = resolveRemotePath(lc, context, ownerId, remotePath).getId();
            if (remoteId == -1)
                throw MailServiceException.NO_SUCH_FOLDER(remotePath);
        }

        Mountpoint mpt;
        try {
            mpt = mbox.createMountpoint(octxt, iidParent.getId(), name, ownerId, remoteId, 
                MailItem.getTypeForName(view), Flag.flagsToBitmask(flags), color);
        } catch (ServiceException se) {
            if (se.getCode() == MailServiceException.ALREADY_EXISTS && fetchIfExists) {
                Folder folder = mbox.getFolderByName(octxt, iidParent.getId(), name);
                if (folder instanceof Mountpoint)
                    mpt = (Mountpoint) folder;
                else
                    throw se;
            } else
                throw se;
        }

        Element response = lc.createElement(MailConstants.CREATE_MOUNTPOINT_RESPONSE);
        if (mpt != null)
            ToXML.encodeMountpoint(response, ifmt, mpt);
        return response;
    }

    private ItemId resolveRemotePath(ZimbraSoapContext lc, Map<String, Object> context, String ownerId, String remotePath) throws ServiceException {
        Element request = lc.createElement(MailConstants.GET_FOLDER_REQUEST);
        request.addElement(MailConstants.E_FOLDER).addAttribute(MailConstants.A_PATH, remotePath);

        Element response = proxyRequest(request, context, ownerId);
        String id = response.getElement(MailConstants.E_FOLDER).getAttribute(MailConstants.A_ID);
        return new ItemId(id, lc);
    }
}
