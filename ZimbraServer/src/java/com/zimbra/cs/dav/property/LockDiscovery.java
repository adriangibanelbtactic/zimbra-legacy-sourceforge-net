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
package com.zimbra.cs.dav.property;

import java.util.HashSet;

import org.dom4j.Element;

import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavElements;
import com.zimbra.cs.dav.LockMgr;
import com.zimbra.cs.dav.LockMgr.Lock;

/**
 * RFC 2518bis section 15.8
 * 
 * @author jylee
 *
 */
public class LockDiscovery extends ResourceProperty {

	private HashSet<LockMgr.Lock> mLocks;
	
	public LockDiscovery() {
		super(DavElements.E_LOCKDISCOVERY);
		setProtected(true);
		mLocks = new HashSet<LockMgr.Lock>();
	}
	
	public LockDiscovery(LockMgr.Lock lock) {
		this();
		addLock(lock);
	}
	
	public Element toElement(DavContext ctxt, Element parent, boolean nameOnly) {
		Element activelock = super.toElement(ctxt, parent, true);
		if (nameOnly)
			return activelock;
		
		for (Lock l : mLocks) {
			Element lock = activelock.addElement(DavElements.E_ACTIVELOCK);
			Element el = lock.addElement(DavElements.E_LOCKTYPE);
			switch (l.type) {
			case write:
				el.addElement(DavElements.E_WRITE);
			}

			el = lock.addElement(DavElements.E_LOCKSCOPE);
			switch (l.scope) {
			case shared:
				el.addElement(DavElements.E_SHARED);
				break;
			case exclusive:
				el.addElement(DavElements.E_EXCLUSIVE);
				break;
			}

			lock.addElement(DavElements.E_DEPTH).setText(Integer.toString(l.depth));
			lock.addElement(DavElements.E_TIMEOUT).setText(l.getTimeoutStr());
			if (l.owner != null)
				lock.addElement(DavElements.E_OWNER).setText(l.owner);
		}
		return activelock;
	}
	
	public void addLock(LockMgr.Lock l) {
		mLocks.add(l);
	}
}
