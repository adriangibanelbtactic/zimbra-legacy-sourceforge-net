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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Aug 30, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.Element;
import com.zimbra.soap.SoapFaultException;
import com.zimbra.soap.ZimbraContext;

/**
 * @author dkarp
 */
public class FolderAction extends ItemAction {

    protected String[] getProxiedIdPath(Element request) {
        String operation = getXPath(request, OPERATION_PATH);
        if (operation != null && FOLDER_OPS.contains(operation.toLowerCase()))
            return TARGET_ITEM_PATH;
        return super.getProxiedIdPath(request);
    }
    protected boolean checkMountpointProxy(Element request) {
        String operation = getXPath(request, OPERATION_PATH);
        // grant/revoke ops get passed through to the referenced resource
        if (OP_GRANT.equalsIgnoreCase(operation) || OP_REVOKE.equalsIgnoreCase(operation))
            return true;
		return super.checkMountpointProxy(request);
	}

    public static final String OP_RENAME   = "rename";
    public static final String OP_EMPTY    = "empty";
    public static final String OP_REFRESH  = "sync";
    public static final String OP_FREEBUSY = "fb";
    public static final String OP_SET_URL  = "url";
    public static final String OP_IMPORT   = "import";
    public static final String OP_GRANT    = "grant";
    public static final String OP_REVOKE   = '!' + OP_GRANT;
    public static final String OP_UNFLAG   = '!' + OP_FLAG;
    public static final String OP_UNTAG    = '!' + OP_TAG;

    private static final Set FOLDER_OPS = new HashSet(Arrays.asList(new String[] {
        OP_RENAME, OP_EMPTY, OP_REFRESH, OP_SET_URL, OP_IMPORT, OP_FREEBUSY, OP_GRANT, OP_REVOKE
    }));

	public Element handle(Element request, Map context) throws ServiceException, SoapFaultException {
        ZimbraContext lc = getZimbraContext(context);

        Element action = request.getElement(MailService.E_ACTION);
        String operation = action.getAttribute(MailService.A_OPERATION).toLowerCase();

        Element response = lc.createElement(MailService.FOLDER_ACTION_RESPONSE);
        Element result = response.addUniqueElement(MailService.E_ACTION);

        if (operation.equals(OP_TAG) || operation.equals(OP_FLAG) || operation.equals(OP_UNTAG) || operation.equals(OP_UNFLAG))
            throw MailServiceException.CANNOT_TAG();
        if (operation.endsWith(OP_UPDATE) || operation.endsWith(OP_SPAM))
            throw ServiceException.INVALID_REQUEST("invalid operation on folder: " + operation, null);
        String successes;
        if (FOLDER_OPS.contains(operation))
            successes = handleFolder(context, request, operation, result);
        else
            successes = handleCommon(context, request, operation, MailItem.TYPE_FOLDER);

        result.addAttribute(MailService.A_ID, successes);
        result.addAttribute(MailService.A_OPERATION, operation);
        return response;
	}

    private String handleFolder(Map context, Element request, String operation, Element result)
    throws ServiceException {
        Element action = request.getElement(MailService.E_ACTION);

        ZimbraContext lc = getZimbraContext(context);
        Mailbox mbox = getRequestedMailbox(lc);
        OperationContext octxt = lc.getOperationContext();
        ItemId iid = new ItemId(action.getAttribute(MailService.A_ID), lc);

        if (operation.equals(OP_EMPTY)) {
            mbox.emptyFolder(octxt, iid.getId(), true);
        } else if (operation.equals(OP_REFRESH)) {
            mbox.synchronizeFolder(octxt, iid.getId());
        } else if (operation.equals(OP_IMPORT)) {
            String url = action.getAttribute(MailService.A_URL);
            mbox.importFeed(octxt, iid.getId(), url, false);
        } else if (operation.equals(OP_FREEBUSY)) {
            boolean fb = action.getAttributeBool(MailService.A_EXCLUDE_FREEBUSY, false);
            mbox.alterTag(octxt, iid.getId(), MailItem.TYPE_FOLDER, Flag.ID_FLAG_EXCLUDE_FREEBUSY, fb);
        } else if (operation.equals(OP_SET_URL)) {
            String url = action.getAttribute(MailService.A_URL, "");
            mbox.setFolderUrl(octxt, iid.getId(), url);
            if (!url.equals(""))
                mbox.synchronizeFolder(octxt, iid.getId());

            if (action.getAttribute(MailService.A_EXCLUDE_FREEBUSY, null) != null) {
                boolean fb = action.getAttributeBool(MailService.A_EXCLUDE_FREEBUSY, false);
                mbox.alterTag(octxt, iid.getId(), MailItem.TYPE_FOLDER, Flag.ID_FLAG_EXCLUDE_FREEBUSY, fb);
            }
        } else if (operation.equals(OP_RENAME)) {
            String name = action.getAttribute(MailService.A_NAME);
            mbox.renameFolder(octxt, iid.getId(), name);
        } else if (operation.equals(OP_REVOKE)) {
            String zid = action.getAttribute(MailService.A_ZIMBRA_ID);
            mbox.revokeAccess(octxt, iid.getId(), zid);
        } else if (operation.equals(OP_GRANT)) {
            Element grant = action.getElement(MailService.E_GRANT);
            boolean inherit = grant.getAttributeBool(MailService.A_INHERIT, false);
            short rights = ACL.stringToRights(grant.getAttribute(MailService.A_RIGHTS));
            byte gtype   = stringToType(grant.getAttribute(MailService.A_GRANT_TYPE));
            String zid   = lookupZimbraId(grant.getAttribute(MailService.A_DISPLAY, null), gtype, lc);
            
            mbox.grantAccess(octxt, iid.getId(), zid, gtype, rights, inherit);
            // kinda hacky -- return the zimbra id of the grantee in the response
            result.addAttribute(MailService.A_ZIMBRA_ID, zid);
        } else
            throw ServiceException.INVALID_REQUEST("unknown operation: " + operation, null);

        return lc.formatItemId(iid);
    }

    static byte stringToType(String typeStr) throws ServiceException {
        if (typeStr.equalsIgnoreCase("usr"))  return ACL.GRANTEE_USER;
        if (typeStr.equalsIgnoreCase("grp"))  return ACL.GRANTEE_GROUP;
        if (typeStr.equalsIgnoreCase("cos"))  return ACL.GRANTEE_COS;
        if (typeStr.equalsIgnoreCase("dom"))  return ACL.GRANTEE_DOMAIN;
        if (typeStr.equalsIgnoreCase("all"))  return ACL.GRANTEE_ALL;
        throw ServiceException.INVALID_REQUEST("unknown grantee type: " + typeStr, null);
    }

    static String typeToString(byte type) {
        if (type == ACL.GRANTEE_USER)    return "usr";
        if (type == ACL.GRANTEE_GROUP)   return "grp";
        if (type == ACL.GRANTEE_COS)     return "cos";
        if (type == ACL.GRANTEE_DOMAIN)  return "dom";
        if (type == ACL.GRANTEE_ALL)     return "all";
        return null;
    }

    static String lookupZimbraId(String name, byte type, ZimbraContext lc) throws ServiceException {
        if (type == ACL.GRANTEE_ALL)
            return ACL.GUID_ALL;

        Provisioning prov = Provisioning.getInstance();
        NamedEntry nentry = null;
        if (name != null)
            switch (type) {
                case ACL.GRANTEE_USER:    if (name.indexOf('@') == -1) {
                                              Account authacct = prov.getAccountById(lc.getAuthtokenAccountId());
                                              String authname = (authacct == null ? null : authacct.getName());
                                              if (authacct != null)
                                                  name += authname.substring(authname.indexOf('@'));
                                          }
                                          nentry = prov.getAccountByName(name);  break;
                case ACL.GRANTEE_COS:     nentry = prov.getCosByName(name);      break;
                case ACL.GRANTEE_DOMAIN:  nentry = prov.getDomainByName(name);   break;
                case ACL.GRANTEE_GROUP:   nentry = null;                                               break;
            }

        if (nentry != null)
            return (type == ACL.GRANTEE_ALL ? null : nentry.getId());
        switch (type) {
            case ACL.GRANTEE_USER:    throw AccountServiceException.NO_SUCH_ACCOUNT(name);
            case ACL.GRANTEE_COS:     throw AccountServiceException.NO_SUCH_COS(name);
            case ACL.GRANTEE_DOMAIN:  throw AccountServiceException.NO_SUCH_DOMAIN(name);
            case ACL.GRANTEE_GROUP:   throw AccountServiceException.NO_SUCH_GROUP(name);
            default:  throw ServiceException.FAILURE("LDAP entry not found for " + name + " : " + type, null);
        }
    }

    static String lookupName(String zid, byte type) {
        try {
            NamedEntry nentry = null;
            switch (type) {
                case ACL.GRANTEE_USER:    nentry = Provisioning.getInstance().getAccountById(zid);  break;
                case ACL.GRANTEE_COS:     nentry = Provisioning.getInstance().getCosById(zid);      break;
                case ACL.GRANTEE_DOMAIN:  nentry = Provisioning.getInstance().getDomainById(zid);   break;
                case ACL.GRANTEE_GROUP:   nentry = null;                                            break;
            }
            return (nentry == null ? null : nentry.getName());
        } catch (ServiceException e) {
            return null;
        }
    }
}
