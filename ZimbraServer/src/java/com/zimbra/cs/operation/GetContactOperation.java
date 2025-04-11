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
package com.zimbra.cs.operation;

import java.util.ArrayList;
import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.session.Session;

public class GetContactOperation extends Operation {
	
	private static int LOAD = 1;
	private static int MAX = 20;
	private static int SCALE = 100;
	static {
		Operation.Config c = loadConfig(GetContactListOperation.class);
		if (c != null) {
			LOAD = c.mLoad;
			if (c.mScale > 0)
				SCALE = c.mScale;
			if (c.mMaxLoad > 0)
				MAX = c.mMaxLoad;
		}
	}
	
	static void init(int load, int scale, int maxload) {
		LOAD = load;
		if (scale > 0)
			SCALE = scale;
		if (maxload > 0)
			MAX = maxload;
	}
	
	private List<Integer> mIds;
	private List<Contact> mContacts;

	public GetContactOperation(Session session, OperationContext oc, Mailbox mbox, Requester req,
				List<Integer> ids) {
		super(session, oc, mbox, req, Math.min(1 + (LOAD * (ids.size() / SCALE)), MAX));
		
		mIds = ids;
	}
	
	public String toString() {
		StringBuilder toRet = new StringBuilder("GetContactOperation(");
		for (int id : mIds) {
			toRet.append(id).append(",");
		}
		toRet.append(")");
		return toRet.toString();
	}

	protected void callback() throws ServiceException {
		Mailbox mbox = getMailbox();
		mContacts = new ArrayList<Contact>();
		synchronized(mbox) {
			for (int id : mIds) {
				Contact con = mbox.getContactById(getOpCtxt(), id);
				mContacts.add(con);
			}
		}
	}
	
	public List<Contact> getResults() { return mContacts; } 
}
