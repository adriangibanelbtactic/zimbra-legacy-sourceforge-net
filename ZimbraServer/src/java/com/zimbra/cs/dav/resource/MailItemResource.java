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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;
import com.zimbra.cs.dav.property.ResourceProperty;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Metadata;

/**
 * Abstraction of DavResource that maps to MailItem in the mailbox.
 * Supports the dead properties that can be saved for DavResource.
 * 
 * @author jylee
 *
 */
public abstract class MailItemResource extends DavResource {
	protected int  mFolderId;
	protected int  mId;
	protected byte mType;
	protected String mEtag;
	protected Map<QName,Element> mDeadProps;
	
	private static final String CONFIG_KEY = "caldav";
	private static final int PROP_LENGTH_LIMIT = 1024;
	
	public MailItemResource(DavContext ctxt, MailItem item) throws ServiceException {
		this(ctxt, getItemPath(item), item);
	}
	
	public MailItemResource(DavContext ctxt, String path, MailItem item) throws ServiceException {
		super(path, item.getAccount());
		mFolderId = item.getFolderId();
		mId = item.getId();
		mType = item.getType();
		mEtag = "\""+Long.toString(item.getChangeDate())+"\"";
		try {
			mDeadProps = getDeadProps(ctxt, item);
		} catch (Exception e) {
			// somehow couldn't get the dead props.
		}
	}
	
	public MailItemResource(String path, String acct) {
		super(path, acct);
	}
	
	private static String getItemPath(MailItem item) throws ServiceException {
		String path = item.getPath();
		if (item.getType() == MailItem.TYPE_FOLDER && !path.endsWith("/"))
			return path + "/";
		return path;
	}

	public boolean hasEtag() {
		return true;
	}
	
	public String getEtag() {
		return mEtag;
	}
	
	protected static Mailbox getMailbox(DavContext ctxt) throws ServiceException, DavException {
		String user = ctxt.getUser();
		if (user == null)
			throw new DavException("invalid uri", HttpServletResponse.SC_NOT_FOUND, null);
		Provisioning prov = Provisioning.getInstance();
		Account account = prov.get(AccountBy.name, user);
		if (account == null)
			throw new DavException("no such account "+user, HttpServletResponse.SC_NOT_FOUND, null);
		return MailboxManager.getInstance().getMailboxByAccount(account);
	}
	
	/* Deletes this resource. */
	public void delete(DavContext ctxt) throws DavException {
		if (mId == 0) 
			throw new DavException("cannot delete resource", HttpServletResponse.SC_FORBIDDEN, null);
		try {
			Mailbox mbox = getMailbox(ctxt);
			mbox.delete(ctxt.getOperationContext(), mId, mType);
		} catch (ServiceException se) {
			throw new DavException("cannot delete item", HttpServletResponse.SC_FORBIDDEN, se);
		}
	}

	/* Moves this resource to another Collection. */
	public void move(DavContext ctxt, Collection dest) throws DavException {
		if (mFolderId == dest.getId())
			return;
		try {
			Mailbox mbox = getMailbox(ctxt);
			mbox.move(ctxt.getOperationContext(), mId, mType, dest.getId());
		} catch (ServiceException se) {
			throw new DavException("cannot move item", HttpServletResponse.SC_FORBIDDEN, se);
		}
	}

	/* Copies this resource to another Collection. */
	public MailItemResource copy(DavContext ctxt, Collection dest) throws DavException {
		try {
			Mailbox mbox = getMailbox(ctxt);
			MailItem item = mbox.copy(ctxt.getOperationContext(), mId, mType, dest.getId());
			return UrlNamespace.getResourceFromMailItem(ctxt, item);
		} catch (IOException e) {
			throw new DavException("cannot copy item", HttpServletResponse.SC_FORBIDDEN, e);
		} catch (ServiceException se) {
			throw new DavException("cannot copy item", HttpServletResponse.SC_FORBIDDEN, se);
		}
	}

	/* Renames this resource. */
	public void rename(DavContext ctxt, String newName) throws DavException {
		try {
			Mailbox mbox = getMailbox(ctxt);
			if (isCollection()) {
				mbox.renameFolder(ctxt.getOperationContext(), mId, newName);
			} else {
				MailItem item = mbox.getItemById(ctxt.getOperationContext(), mId, mType);
				if (item instanceof Document) {
					Document doc = (Document) item;
					doc.rename(newName);
				}
			}
		} catch (ServiceException se) {
			throw new DavException("cannot rename item", HttpServletResponse.SC_FORBIDDEN, se);
		}
	}
	
	int getId() {
		return mId;
	}
	
	private static Map<QName,Element> getDeadProps(DavContext ctxt, MailItem item) throws DocumentException, IOException, DavException, ServiceException {
		HashMap<QName,Element> props = new HashMap<QName,Element>();
		Mailbox mbox = getMailbox(ctxt);
		Metadata data = mbox.getConfig(ctxt.getOperationContext(), CONFIG_KEY);
		if (data == null)
			return props;
		String configVal = data.get(Integer.toString(item.getId()), null);
		if (configVal == null)
			return props;
		ByteArrayInputStream in = new ByteArrayInputStream(configVal.getBytes("UTF-8"));
		org.dom4j.Document doc = new SAXReader().read(in);
		Element e = doc.getRootElement();
		if (e == null)
			return props;
		for (Object obj : e.elements())
			if (obj instanceof Element) {
				Element elem = (Element) obj;
				elem.detach();
				props.put(elem.getQName(), elem);
			}
		return props;
	}

	/* Modifies the set of dead properties saved for this resource. 
	 * Properties in the parameter 'set' are added to the existing properties.
	 * Properties in 'remove' are removed.
	 */
	public void patchProperties(DavContext ctxt, java.util.Collection<Element> set, java.util.Collection<QName> remove) throws DavException {
		for (QName n : remove)
				mDeadProps.remove(n);
		for (Element e : set)
			mDeadProps.put(e.getQName(), e);
		if (mDeadProps.size() == 0)
			return;
		try {
			org.dom4j.Document doc = org.dom4j.DocumentHelper.createDocument();
			Element top = doc.addElement(CONFIG_KEY);
			for (Map.Entry<QName,Element> entry : mDeadProps.entrySet())
				top.add(entry.getValue().detach());

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			OutputFormat format = OutputFormat.createCompactFormat();
			XMLWriter writer = new XMLWriter(out, format);
			writer.write(doc);
			String configVal = new String(out.toByteArray(), "UTF-8");
			
			if (configVal.length() > PROP_LENGTH_LIMIT)
				throw new DavException("unable to patch properties", DavProtocol.STATUS_INSUFFICIENT_STORAGE, null);

			synchronized (MailItemResource.class) {
				Mailbox mbox = getMailbox(ctxt);
				Metadata data = mbox.getConfig(ctxt.getOperationContext(), CONFIG_KEY);
				if (data == null)
					data = new Metadata();
				data.put(Integer.toString(mId), configVal);
				mbox.setConfig(ctxt.getOperationContext(), CONFIG_KEY, data);
			}
		} catch (IOException ioe) {
			throw new DavException("unable to patch properties", HttpServletResponse.SC_FORBIDDEN, ioe);
		} catch (ServiceException se) {
			throw new DavException("unable to patch properties", HttpServletResponse.SC_FORBIDDEN, se);
		}
	}
	
	public ResourceProperty getProperty(QName prop) {
		ResourceProperty rp = super.getProperty(prop);
		if (rp != null || mDeadProps == null)
			return rp;
		Element e = mDeadProps.get(prop);
		if (e != null)
			rp = new ResourceProperty(e);
		return rp;
	}
}
