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
package com.zimbra.cs.dav;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;

@SuppressWarnings("serial")
public class DavException extends Exception {
	protected boolean mStatusIsSet;
	protected int mStatus;
	protected Document mErrMsg;
	
	public DavException(String msg, int status) {
		super(msg);
		mStatus = status;
		mStatusIsSet = true;
	}
	
	public DavException(String msg, Throwable cause) {
		super(msg, cause);
		mStatusIsSet = false;
	}
	
	public DavException(String msg, int status, Throwable cause) {
		super(msg, cause);
		mStatus = status;
		mStatusIsSet = true;
	}

	public boolean isStatusSet() {
		return mStatusIsSet;
	}
	
	public int getStatus() {
		return mStatus;
	}
	
	public boolean hasErrorMessage() {
		return (mErrMsg != null);
	}
	
	public Element getErrorMessage() {
		if (mErrMsg == null)
			return null;
		return mErrMsg.getRootElement();
	}
	public void writeErrorMsg(OutputStream out) throws IOException {
		DomUtil.writeDocumentToStream(mErrMsg, out);
	}
	
	protected static class DavExceptionWithErrorMessage extends DavException {
		protected DavExceptionWithErrorMessage(String msg, int status) {
			super(msg, status);
			mErrMsg = org.dom4j.DocumentHelper.createDocument();
			mErrMsg.addElement(DavElements.E_ERROR);
		}
		protected void setError(QName error) {
			mErrMsg.getRootElement().addElement(error);
		}
	}
	public static class CannotModifyProtectedProperty extends DavExceptionWithErrorMessage {
		public CannotModifyProtectedProperty(QName prop) {
			super("property "+prop.getName()+" is protected", HttpServletResponse.SC_FORBIDDEN);
			setError(DavElements.E_CANNOT_MODIFY_PROTECTED_PROPERTY);
		}
	}
}
