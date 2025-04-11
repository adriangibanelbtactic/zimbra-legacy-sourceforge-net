/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.1
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Web Client
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function ZmAuthenticate(appCtxt) {
	if (arguments.length == 0) return;
	this._appCtxt = appCtxt;
};

ZmAuthenticate._isAdmin = false;

ZmAuthenticate.setAdmin =
function(isAdmin) {
	ZmAuthenticate._isAdmin = isAdmin;
};

ZmAuthenticate.prototype.toString = 
function() {
	return "ZmAuthenticate";
};

// XXX: async
ZmAuthenticate.prototype.execute =
function(uname, pword) {
	var command = new ZmCsfeCommand();
	var soapDoc;
	if (!ZmAuthenticate._isAdmin) {
		soapDoc = AjxSoapDoc.create("AuthRequest", "urn:zimbraAccount");
		var el = soapDoc.set("account", uname);
		el.setAttribute("by", "name");
	} else {
		soapDoc = AjxSoapDoc.create("AuthRequest", "urn:zimbraAdmin", null);
		soapDoc.set("name", uname);
	}
	soapDoc.set("password", pword);
	var resp = command.invoke({soapDoc: soapDoc, noAuthToken: true, noSession: true}).Body.AuthResponse;
	this._setAuthToken(resp);
};

ZmAuthenticate.prototype._setAuthToken =
function(resp) {
	var lifetime = !this._appCtxt.isPublicComputer() ? resp.lifetime : null;
	// ignore sessionId so we get a <refresh> block
	ZmCsfeCommand.setAuthToken(resp.authToken, lifetime);
};
