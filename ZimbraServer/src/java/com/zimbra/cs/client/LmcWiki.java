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
package com.zimbra.cs.client;

public class LmcWiki extends LmcDocument {
	private String mWikiWord;
	private String mContents;
	
	public void setWikiWord(String w) { mWikiWord = w; setName(w); }
	public void setContents(String c) { mContents = c; }
	
	public String getWikiWord() { return mWikiWord; }
	public String getContents() { return mContents; }
	
	public String toString() {
		return "Wiki id=" + mId + " rev=" + mRev + " wikiword=" + mWikiWord +
		" folder=" + mFolder + " lastEditor=" + mLastEditedBy + 
		" lastModifiedDate=" + mLastModifiedDate;
	}
}
