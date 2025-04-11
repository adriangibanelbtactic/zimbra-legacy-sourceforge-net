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

import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.session.Session;

public class GetContactListOperation extends Operation {
	
	private static int LOAD = 5;
	static {
		Operation.Config c = loadConfig(GetContactListOperation.class);
		if (c != null)
			LOAD = c.mLoad;
	}
	
	private ItemId mIidFolder;
	private byte mSort;
	
	private List<Contact> mList;

	public GetContactListOperation(Session session, OperationContext oc, Mailbox mbox, Requester req, ItemId iidFolder) {
		super(session, oc, mbox, req, LOAD);
		
		mIidFolder = iidFolder;
		mSort = -1;
	}
	
	public GetContactListOperation(Session session, OperationContext oc, Mailbox mbox, Requester req, ItemId iidFolder, byte sort) {
		super(session, oc, mbox, req, LOAD);
		
		mIidFolder = iidFolder;
		mSort = sort;
	}
	
	
	public String toString() {
        StringBuilder toRet = new StringBuilder(super.toString());
        toRet.append(" GetContactList(");
        if (mIidFolder != null)
            toRet.append(mIidFolder.toString()).append(")");
        else 
            toRet.append("-1)");
        return toRet.toString();
	}

	protected void callback() throws ServiceException {
		if (mSort == -1) 		
			mList = getMailbox().getContactList(getOpCtxt(), mIidFolder != null ? mIidFolder.getId() : -1);
		else 
			mList = getMailbox().getContactList(getOpCtxt(), mIidFolder != null ? mIidFolder.getId() : -1, mSort);
	}
	
	public List<Contact> getResults() { return mList; }

}
