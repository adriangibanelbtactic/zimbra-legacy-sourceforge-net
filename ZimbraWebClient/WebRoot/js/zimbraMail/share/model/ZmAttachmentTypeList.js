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

function ZmAttachmentTypeList(appCtxt) {
	if (arguments.length == 0) return;
	ZmModel.call(this, true);
	this._appCtxt = appCtxt;
};

ZmAttachmentTypeList.prototype = new ZmModel;
ZmAttachmentTypeList.prototype.constructor = ZmAttachmentTypeList;

ZmAttachmentTypeList.prototype.toString = 
function() {
	return "ZmAttachmentTypeList";
};

ZmAttachmentTypeList.prototype.getAttachments =
function() {
	return this._attachments;
};

ZmAttachmentTypeList.compareEntry = 
function(a,b) {
	if (a.desc.toLowerCase() < b.desc.toLowerCase())
		return -1;
	if (a.desc.toLowerCase() > b.desc.toLowerCase())
		return 1;
	else
		return 0;
};

ZmAttachmentTypeList.prototype.load =
function(callback) {
	this._attachments = new Array();

	var soapDoc = AjxSoapDoc.create("BrowseRequest", "urn:zimbraMail");
	soapDoc.getMethod().setAttribute("browseBy", "attachments");

	var respCallback = new AjxCallback(this, this._handleResponseLoad, [callback]);
	this._appCtxt.getAppController().sendRequest(soapDoc, true, respCallback);

};

ZmAttachmentTypeList.prototype._handleResponseLoad =
function(args) {
	var callback	= args[0];
	var result		= args[1];
	
	var att = result.getResponse().BrowseResponse.bd;
	if (att) {
		for (var i = 0; i < att.length; i++) {
			var type = att[i]._content;
			if (!ZmMimeTable.isIgnored(type) && (type.indexOf("/") != -1 || type == "image"))
				this._attachments.push(ZmMimeTable.getInfo(type, true));
		}
		this._attachments.sort(ZmAttachmentTypeList.compareEntry);
	}
	
	if (callback) callback.run(result);
};
