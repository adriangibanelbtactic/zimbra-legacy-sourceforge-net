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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.dom4j.Element;
import org.dom4j.QName;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.DateUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavElements;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;
import com.zimbra.cs.dav.DavProtocol.Compliance;
import com.zimbra.cs.dav.property.ResourceProperty;

/**
 * DavResource is an object identified by a URL in the hierarchy of HTTP URL
 * namespace as described in section 5 of RFC2518.
 * 
 * @author jylee
 *
 */
public abstract class DavResource {
	protected String mUri;
	protected String mOwner;
	protected Map<QName,ResourceProperty> mProps;
	protected Collection<Compliance> mDavCompliance;
	
	public DavResource(String uri, Account acct) throws ServiceException {
		this(uri, getOwner(acct));
	}
	
	public DavResource(String uri, String owner) {
		mOwner = owner;
		mProps = new HashMap<QName,ResourceProperty>();
		mUri = uri;
		mDavCompliance = new HashSet<Compliance>();
		mDavCompliance.add(Compliance.one);
		mDavCompliance.add(Compliance.two);
		//mDavCompliance.add(Compliance.three);
		//mDavCompliance.add(Compliance.access_control);
		//mDavCompliance.add(Compliance.update);
		//mDavCompliance.add(Compliance.binding);
		
		ResourceProperty rs = new ResourceProperty(DavElements.E_RESOURCETYPE);
		if (isCollection())
			rs.addChild(DavElements.E_COLLECTION);
		addProperty(rs);
		ResourceProperty href = new ResourceProperty(DavElements.E_HREF);
		href.setProtected(true);
		try {
			href.setStringValue(UrlNamespace.getResourceUrl(this));
		} catch (DavException e) {
			ZimbraLog.dav.error("can't generate href", e);
		}
		addProperty(href);
		if (hasEtag())
			setProperty(DavElements.E_GETETAG, getEtag(), true);
	}
	
	protected static String getOwner(Account acct) throws ServiceException {
		String owner = acct.getName();
		Provisioning prov = Provisioning.getInstance();
        Config config = prov.getConfig();
        String defaultDomain = config.getAttr(Provisioning.A_zimbraDefaultDomainName, null);
        if (defaultDomain != null && defaultDomain.equalsIgnoreCase(acct.getDomainName()))
        	owner = owner.substring(0, owner.indexOf('@'));
        return owner;
	}
	
	public boolean equals(Object another) {
		if (another instanceof DavResource) {
			DavResource that = (DavResource) another;
			return this.mUri.equals(that.mUri) && this.mOwner.equals(that.mOwner);
		}
		return false;
	}
	
	public Collection<Compliance> getComplianceList() {
		return mDavCompliance;
	}
	
	public ResourceProperty getProperty(String propName) {
		return getProperty(QName.get(propName, DavElements.WEBDAV_NS));
	}
	
	public ResourceProperty getProperty(QName prop) {
		return mProps.get(prop);
	}

	public Set<QName> getAllPropertyNames() {
		HashSet<QName> ret = new HashSet<QName>();
		for (QName key : mProps.keySet())
			if (!mProps.get(key).isProtected())
				ret.add(key);
		
		return ret;
	}

	public String getUri() {
		return mUri;
	}

	public String getOwner() {
		return mOwner;
	}
	
	public boolean hasContent() {
		try {
			return (getContentLength() > 0);
		} catch (NumberFormatException e) {
		}
		return false;
	}
	
	public String getContentType() {
		ResourceProperty prop = getProperty(DavElements.E_GETCONTENTTYPE);
		if (prop != null)
			return prop.getStringValue();
		return null;
	}
	
	public int getContentLength() {
		ResourceProperty prop = getProperty(DavElements.E_GETCONTENTLENGTH);
		if (prop != null)
			return Integer.parseInt(prop.getStringValue());
		return 0;
	}
	
	protected void setCreationDate(long ts) {
		setProperty(DavElements.P_CREATIONDATE, DateUtil.toISO8601(new Date(ts)));
	}
	
	protected void setLastModifiedDate(long ts) {
		setProperty(DavElements.P_GETLASTMODIFIED, DateUtil.toRFC822Date(new Date(ts)));
	}
	
	protected void addProperty(ResourceProperty prop) {
		mProps.put(prop.getName(), prop);
	}
	
	protected void addProperties(Set<ResourceProperty> props) {
		for (ResourceProperty p : props)
			mProps.put(p.getName(), p);
	}
	
	protected void setProperty(String key, String val) {
		setProperty(QName.get(key, DavElements.WEBDAV_NS), val);
	}
	
	protected void setProperty(QName key, String val) {
		setProperty(key, val, false);
	}
	
	protected void setProperty(QName key, String val, boolean isProtected) {
		ResourceProperty prop = mProps.get(key);
		if (prop == null) {
			prop = new ResourceProperty(key);
			mProps.put(key, prop);
		}
		prop.setProtected(isProtected);
		prop.setStringValue(val);
	}
	
	/*
	 * whether the resource is access controlled as in RFC3744.
	 */
	public boolean isAccessControlled() {
		return true;
	}
	
	public abstract InputStream getContent() throws IOException, DavException;
	
	public abstract boolean isCollection();
	
	public abstract void delete(DavContext ctxt) throws DavException;
	
	public Collection<DavResource> getChildren(DavContext ctxt) throws DavException {
		return Collections.emptyList();
	}
	
	public boolean hasEtag() {
		return false;
	}
	public String getEtag() {
		return null;
	}
	
	public void patchProperties(DavContext ctxt, Collection<Element> set, Collection<QName> remove) throws DavException {
		throw new DavException("PROPPATCH not supported on "+getUri(), DavProtocol.STATUS_FAILED_DEPENDENCY, null);
	}
}
