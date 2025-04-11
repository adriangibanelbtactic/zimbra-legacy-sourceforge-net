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

function ZmNewFolderDialog(parent, msgDialog, className) {

	ZmDialog.call(this, parent, msgDialog, className, ZmMsg.createNewFolder);

	this.setContent(this._contentHtml());
	this._setNameField(this._nameFieldId);
	this._setupRemoteCheckboxField();
	var omit = new Object();
	omit[ZmFolder.ID_SPAM] = true;
	omit[ZmFolder.ID_DRAFTS] = true;
	
	this._setOverview(ZmNewFolderDialog._OVERVIEW_ID, this._folderTreeCellId, [ZmOrganizer.FOLDER], omit);
	this._folderTreeView = this._treeView[ZmOrganizer.FOLDER];
	this._folderTree = this._appCtxt.getTree(ZmOrganizer.FOLDER);
}

ZmNewFolderDialog._OVERVIEW_ID = "ZmNewFolderDialog";

ZmNewFolderDialog._feedEnabled = true;

ZmNewFolderDialog.prototype = new ZmDialog;
ZmNewFolderDialog.prototype.constructor = ZmNewFolderDialog;

ZmNewFolderDialog.prototype.toString = 
function() {
	return "ZmNewFolderDialog";
}

ZmNewFolderDialog.prototype.popup =
function(folder, loc) {
	folder = folder ? folder : this._folderTree.root;
	this._folderTreeView.setSelected(folder);
	if (folder.id == ZmOrganizer.ID_ROOT) {
		var ti = this._folderTreeView.getTreeItemById(folder.id);
		ti.setExpanded(true);
	}

	//var feedEnabled = this._appCtxt.get(ZmSetting.FEED_ENABLED);
	var feedEnabled = ZmNewFolderDialog._feedEnabled;
	if (this._feedEnabled != feedEnabled) {
		this._feedEnabled = ZmNewFolderDialog._feedEnabled;
		var cbField= Dwt.getDomObj(this._doc, this._checkboxRowId);
		cbField.style.display = this._feedEnabled ? (AjxEnv.isIE ? "block" : "table-row") : "none";
	}
	
	ZmDialog.prototype.popup.call(this, loc);
}

ZmNewFolderDialog.prototype._contentHtml = 
function() {
	this._nameFieldId = Dwt.getNextId();
	this._remoteCheckboxFieldId = Dwt.getNextId();	
	this._urlFieldId = Dwt.getNextId();		
	this._checkboxRowId = Dwt.getNextId();
	this._folderTreeCellId = Dwt.getNextId();	
	var html = new Array();
	var idx = 0;
	html[idx++] = "<table cellpadding='0' cellspacing='5' border='0'>";
	html[idx++] = "<tr valign='center'><td class='Label' Xstyle='padding: 0px 0px 5px 0px;'>" + ZmMsg.nameLabel + "</td>";
	html[idx++] = "<td><input autocomplete=OFF type='text' class='Field' id='" + this._nameFieldId + "' /></td></tr>";
	
	html[idx++] = "<tr id='"+this._checkboxRowId+"' style='display:none;'><td colspan=2>";
	html[idx++] = "<table cellpadding='0' cellspacing='5' border='0'>";
	html[idx++] = "<tr valign='center'><td class='Label'><input type='checkbox' id='" + this._remoteCheckboxFieldId + "'/></td><td>"+ZmMsg.subscribeToFeed+"</td></tr>";
	html[idx++] = "</table>";	
	html[idx++] = "</td></tr>";
	
	html[idx++] = "<tr style='display:none;' id='"+this._remoteCheckboxFieldId+"URLrow' valign='center'><td class='Label' Xstyle='padding: 0px 0px 5px 0px;'>" + ZmMsg.urlLabel + "</td>";
	html[idx++] = "<td><input autocomplete=OFF type='text' class='Field' id='" +this._remoteCheckboxFieldId+"URLfield'/></td></tr>";

	html[idx++] = "<tr><td class='Label' colspan=2>" + ZmMsg.newFolderParent + ":</td></tr>";
	html[idx++] = "<tr><td colspan=2 id='" + this._folderTreeCellId + "'/></tr>";
	html[idx++] = "</table>";
	
	return html.join("");
}

ZmNewFolderDialog.prototype._okButtonListener =
function(ev) {
	var results = this._getFolderData();
	if (results)
		DwtDialog.prototype._buttonListener.call(this, ev, results);
}

ZmNewFolderDialog.prototype._getFolderData =
function() {
	// check name for presence and validity
	var name = AjxStringUtil.trim(this._nameField.value);
	var msg = ZmFolder.checkName(name);

	// make sure a parent was selected
	var parentFolder = this._folderTreeView.getSelected();
	if (!msg && !parentFolder)
		msg = ZmMsg.folderNameNoLocation;

	// make sure parent doesn't already have a child by this name
	if (!msg && parentFolder.hasChild(name))
		msg = ZmMsg.folderOrSearchNameExists;

	// if we're creating a top-level folder, check for conflict with top-level search
	if (!msg && (parentFolder.id == ZmOrganizer.ID_ROOT)) {
		var searchTree = this._appCtxt.getTree(ZmOrganizer.SEARCH);
		if (searchTree && searchTree.root.hasChild(name))
			msg = ZmMsg.folderOrSearchNameExists;
	}

	var url = this._remoteCheckboxField.checked ? this._urlField.value : null;
	if (!msg && (this._remoteCheckboxField.checked)) {
		if (!url.match(/^[a-zA-Z]+:\/\/.*$/i)) {
			msg = ZmMsg.errorUrlMissing;
		}			
	}

	return (msg ? this._showError(msg) : [parentFolder, name, url]);
}

ZmNewFolderDialog.prototype._enterListener =
function(ev) {
	var results = this._getFolderData();
	if (results)
		this._runEnterCallback(results);
}

ZmNewFolderDialog.prototype._setupRemoteCheckboxField =
function() {
	this._remoteCheckboxField = Dwt.getDomObj(this._doc, this._remoteCheckboxFieldId);
	this._urlField = Dwt.getDomObj(this._doc, this._remoteCheckboxFieldId+"URLfield");	
	Dwt.setHandler(this._remoteCheckboxField, DwtEvent.ONCLICK, this._handleCheckbox);	
}


ZmNewFolderDialog.prototype.reset =
function() {
	ZmDialog.prototype.reset.call(this);
	if (this._urlField)
		this._urlField.value = "";
	if (this._remoteCheckboxField) {
		this._remoteCheckboxField.checked = false;
		var urlRow = Dwt.getDomObj(document, this._remoteCheckboxField.id+"URLrow");		
		urlRow.style.display = "none";
	}
}

ZmNewFolderDialog.prototype._handleCheckbox =
function(event) {
	event = event || window.event;
	var target = DwtUiEvent.getTarget(event);
	var urlRow = Dwt.getDomObj(document, target.id+"URLrow");
	var urlField= Dwt.getDomObj(document, target.id+"URLfield");	
	urlRow.style.display = target.checked ? (AjxEnv.isIE ? "block" : "table-row") : "none";
	if (target.checked) {
		urlField.focus();
	}
}
