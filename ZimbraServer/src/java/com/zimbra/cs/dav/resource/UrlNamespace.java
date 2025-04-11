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
package com.zimbra.cs.dav.resource;

import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletResponse;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.service.DavServlet;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;

/**
 * UrlNamespace provides a mapping from a URL to a DavResource.
 * 
 * @author jylee
 *
 */
public class UrlNamespace {
	public static final String ATTACHMENTS_PREFIX = "/attachments";
	
	/* Returns Collection at the specified URL. */
	public static Collection getCollectionAtUrl(DavContext ctxt, String url) throws DavException {
		if (url.startsWith("http")) {
			int index = url.indexOf(DavServlet.DAV_PATH);
			if (index == -1 || url.endsWith(DavServlet.DAV_PATH))
				throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
			index += DavServlet.DAV_PATH.length() + 1;
			url = url.substring(index);
			
			// skip user.
			index = url.indexOf('/');
			if (index == -1)
				throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
			url = url.substring(index);
		}
		String path = url;
		int lastPos = path.length() - 1;
		if (path.endsWith("/"))
			lastPos--;
		int index = path.lastIndexOf('/', lastPos);
		if (index == -1)
			path = "/";
		else
			path = path.substring(0, index);
		DavResource rsc = getResourceAt(ctxt, ctxt.getUser(), path);
		if (rsc instanceof Collection)
			return (Collection)rsc;
		throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
	}

	/* Returns DavResource at the specified URL. */
	public static DavResource getResourceAtUrl(DavContext ctxt, String url) throws DavException {
		int index = url.indexOf(DavServlet.DAV_PATH);
		if (index == -1 || url.endsWith(DavServlet.DAV_PATH))
			throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
		index += DavServlet.DAV_PATH.length() + 1;
		int delim = url.indexOf("/", index);
		if (delim == -1)
			throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
		String user = url.substring(index, delim);
		String path = url.substring(delim);
		return getResourceAt(ctxt, user, path);
	}

	/* Returns DavResource in the user's mailbox at the specified path. */
	public static DavResource getResourceAt(DavContext ctxt, String user, String path) throws DavException {
		if (path == null)
			throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
		
		String target = path.toLowerCase();
		DavResource resource = null;
		
		if (target.startsWith(ATTACHMENTS_PREFIX)) {
			resource = getPhantomResource(ctxt, user);
		} else {
			MailItem item = getMailItem(ctxt, user, target);
			resource = getResourceFromMailItem(ctxt, item);
		}
		
		if (resource == null)
			throw new DavException("no DAV resource for "+target, HttpServletResponse.SC_NOT_FOUND, null);
		
		return resource;
	}

	/* Returns DavResource identified by MailItem id .*/
	public static DavResource getResourceByItemId(DavContext ctxt, String user, int id) throws ServiceException, DavException {
		MailItem item = getMailItemById(ctxt, user, id);
		return getResourceFromMailItem(ctxt, item);
	}
	
	/* Returns the root URL of the user. */
	public static String getHomeUrl(String user) throws ServiceException, DavException {
		return getResourceUrl(user, "");
	}
	
	public static final String ACL_USER   = "/acl/user";
	public static final String ACL_GROUP  = "/acl/group";
	public static final String ACL_COS    = "/acl/cos";
	public static final String ACL_DOMAIN = "/acl/domain";
	
	/* RFC 3744 */
	public static String getAclUrl(String owner, String principal, String type) throws DavException {
		StringBuilder buf = new StringBuilder();
		buf.append(type).append("/").append(principal);
		try {
			Provisioning prov = Provisioning.getInstance();
			Account account = prov.get(AccountBy.name, owner);
			if (account == null)
				throw new DavException("unknown user "+owner, HttpServletResponse.SC_NOT_FOUND, null);
			return DavServlet.getServiceUrl(account, buf.toString());
		} catch (ServiceException e) {
			throw new DavException("cannot create ACL URL for principal "+principal, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
		}
	}

	/* Returns URL to the resource. */
	public static String getResourceUrl(DavResource rs) throws ServiceException, DavException {
		return getResourceUrl(rs.getOwner(), rs.getUri());
	}

	public static String getResourceUrl(String user, String resourcePath) throws ServiceException, DavException {
		return urlEscape(DavServlet.getDavUrl(user) + resourcePath);
	}
	
	private static String urlEscape(String str) {
		// rfc 2396 url escape.
		// currently escaping ' and " only
		if (str.indexOf(' ') == -1 && str.indexOf('\'') == -1 && str.indexOf('"') == -1)
			return str;
		StringBuilder buf = new StringBuilder();
		for (char c : str.toCharArray()) {
			if (c == ' ')
				buf.append("%20");
			else if (c == '"')
				buf.append("%22");
			else if (c == '\'')
				buf.append("%27");
			else buf.append(c);
		}
		return buf.toString();
	}
	
	/* Returns DavResource for the MailItem. */
	public static MailItemResource getResourceFromMailItem(DavContext ctxt, MailItem item) throws DavException {
		MailItemResource resource = null;
		if (item == null)
			return resource;
		byte itemType = item.getType();
		
		try {
			switch (itemType) {
			case MailItem.TYPE_FOLDER :
			case MailItem.TYPE_MOUNTPOINT :
				Folder f = (Folder) item;
				byte viewType = f.getDefaultView();
				if (f.getId() == Mailbox.ID_FOLDER_INBOX)
					resource = new ScheduleInbox(ctxt, f);
				else if (f.getId() == Mailbox.ID_FOLDER_SENT)
					resource = new ScheduleOutbox(ctxt, f);
				else if (viewType == MailItem.TYPE_APPOINTMENT)
					resource = new CalendarCollection(ctxt, f);
				else
					resource = new Collection(ctxt, f);
				break;
			case MailItem.TYPE_WIKI :
			case MailItem.TYPE_DOCUMENT :
				resource = new Notebook(ctxt, (Document)item);
				break;
			case MailItem.TYPE_APPOINTMENT :
			case MailItem.TYPE_TASK :
				resource = new CalendarObject(ctxt, (CalendarItem)item);
				break;
			case MailItem.TYPE_MESSAGE :
				Message msg = (Message)item;
				if (msg.isInvite() && msg.hasCalendarItemInfos())
					resource = new CalendarScheduleMessage(ctxt, msg);
				break;
			}
		} catch (ServiceException e) {
			resource = null;
			ZimbraLog.dav.info("cannot create DavResource", e);
		}
		return resource;
	}
	
	private static DavResource getPhantomResource(DavContext ctxt, String user) throws DavException {
		DavResource resource;
		String target = ctxt.getPath();
		
		ArrayList<String> tokens = new ArrayList<String>();
		StringTokenizer tok = new StringTokenizer(target, "/");
		int numTokens = tok.countTokens();
		while (tok.hasMoreTokens()) {
			tokens.add(tok.nextToken());
		}
		
		//
		// return BrowseWrapper
		//
		// /attachments/
		// /attachments/by-date/
		// /attachments/by-type/
		// /attachments/by-type/image/
		// /attachments/by-sender/
		// /attachments/by-sender/zimbra.com/

		//
		// return SearchWrapper
		//
		// /attachments/by-date/today/
		// /attachments/by-type/image/last-month/
		// /attachments/by-sender/zimbra.com/last-week/
		
		//
		// return AttachmentWrapper
		//
		// /attachments/by-date/today/image.gif
		// /attachments/by-type/image/last-month/image.gif
		// /attachments/by-sender/zimbra.com/last-week/image.gif

		switch (numTokens) {
		case 1:
		case 2:
			resource = new BrowseWrapper(target, user, tokens);
			break;
		case 3:
			if (tokens.get(1).equals(PhantomResource.BY_DATE))
				resource = new SearchWrapper(target, user, tokens);
			else
				resource = new BrowseWrapper(target, user, tokens);
			break;
		case 4:
			if (tokens.get(1).equals(PhantomResource.BY_DATE))
				resource = new Attachment(target, user, tokens, ctxt);
			else
				resource = new SearchWrapper(target, user, tokens);
			break;
		case 5:
			resource = new Attachment(target, user, tokens, ctxt);
			break;
		default:
			resource = null;
		}
		
		return resource;
	}
	
	private static MailItem getMailItemById(DavContext ctxt, String user, int id) throws DavException, ServiceException {
		Provisioning prov = Provisioning.getInstance();
		Account account = prov.get(AccountBy.name, user);
		if (account == null)
			throw new DavException("no such accout "+user, HttpServletResponse.SC_NOT_FOUND, null);
		
		Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account);
		return mbox.getItemById(ctxt.getOperationContext(), id, MailItem.TYPE_UNKNOWN);
	}
	
	private static MailItem getMailItem(DavContext ctxt, String user, String path) throws DavException {
		try {
			Provisioning prov = Provisioning.getInstance();
			Account account = prov.get(AccountBy.name, user);
			if (account == null)
				throw new DavException("no such accout "+user, HttpServletResponse.SC_NOT_FOUND, null);
			
			Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account);
			String id = null;
			int index = path.indexOf('?');
			if (index > 0) {
				Map<String, String> params = HttpUtil.getURIParams(path.substring(index+1));
				path = path.substring(0, index);
				id = params.get("id");
			}
			Mailbox.OperationContext octxt = ctxt.getOperationContext();

			if (id != null)
				return mbox.getItemById(octxt, Integer.parseInt(id), MailItem.TYPE_UNKNOWN);

			index = path.lastIndexOf('/');
			Folder f = null;
			if (index != -1) {
				try {
					f = mbox.getFolderByPath(octxt, path.substring(0, index));
				} catch (MailServiceException.NoSuchItemException e) {
				}
			}
			if (f != null && path.endsWith(CalendarObject.CAL_EXTENSION)) {
				String uid = path.substring(index + 1, path.length() - CalendarObject.CAL_EXTENSION.length());
				return mbox.getCalendarItemByUid(octxt, uid);
			}
			return mbox.getItemByPath(octxt, path);
		} catch (ServiceException e) {
			throw new DavException("cannot get item", HttpServletResponse.SC_NOT_FOUND, e);
		}
	}
	
	public static Account getPrincipal(String principalUrl) throws ServiceException {
		int index = principalUrl.indexOf(DavServlet.DAV_PATH);
		if (index == -1)
			return null;
		String acct = principalUrl.substring(index + DavServlet.DAV_PATH.length() + 1);
		Provisioning prov = Provisioning.getInstance();
		return prov.get(AccountBy.name, acct);
	}
}
