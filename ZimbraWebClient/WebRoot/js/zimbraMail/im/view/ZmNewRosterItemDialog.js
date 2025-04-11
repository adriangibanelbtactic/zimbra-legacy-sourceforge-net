/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.2
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.2 ("License"); you may not use this file except in
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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

ZmNewRosterItemDialog = function(parent, appCtxt) {
	ZmQuickAddDialog.call(this, parent, null, null);
	this._appCtxt = appCtxt;
	this.setContent(this._contentHtml());


	var options = [];
	var roster = AjxDispatcher.run("GetRoster");
	var gws = roster.getGateways();
	for (var i = 0; i < gws.length; i++) {
		var gw = gws[i];
		options.push(new DwtSelectOption(gw.type, i == 0, gw.type));
	}
	var sel = new DwtSelect(this, options);
	sel.reparentHtmlElement(this._serviceTypeId);
	this._serviceTypeSelect = sel;

	this.setTitle(ZmMsg.createNewRosterItem);
//	this.setTabOrder([this._addressFieldId, this._nameFieldId, this._groupsFieldId]);
	this._initAddressAutocomplete();
	this._initGroupAutocomplete();
    	this.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(this, this._okButtonListener));
}

ZmNewRosterItemDialog._OVERVIEW_ID = "ZmNewRosterItemDialog";

ZmNewRosterItemDialog.prototype = new ZmQuickAddDialog;
ZmNewRosterItemDialog.prototype.constructor = ZmNewRosterItemDialog;

ZmNewRosterItemDialog.prototype.toString =
function() {
	return "ZmNewRosterItemDialog";
};

ZmNewRosterItemDialog.prototype._contentHtml =
function() {
	this._addressFieldId = Dwt.getNextId();
	this._nameFieldId = Dwt.getNextId();
	this._groupsFieldId = Dwt.getNextId();

	var html = new AjxBuffer();
	//	html.append("<table cellpadding='0' cellspacing='5' border='0'>");
	html.append("<table border='0' width=325>");

	this._serviceTypeId = Dwt.getNextId();

	html.append("<tr valign='center'><td class='ZmChatDialogField'>", ZmMsg.imGateway, "</td>");
	html.append("<td id='" + this._serviceTypeId + "'>");
	html.append("</td></tr>");

	html.append("<tr valign='center'><td class='ZmChatDialogField'>", ZmMsg.imAddressLabel, "</td>");
	html.append("<td>");
	html.append(Dwt.CARET_HACK_BEGIN);
	html.append("<input autocomplete=OFF type='text' style='width:100%; height:22px' id='", this._addressFieldId, "' />");
	html.append(Dwt.CARET_HACK_END);
	html.append("</td></tr>");

	html.append("<tr valign='center'><td class='ZmChatDialogField'>", ZmMsg.imNameLabel, "</td>");
	html.append("<td>");
	html.append(Dwt.CARET_HACK_BEGIN);
	html.append("<input autocomplete=OFF type='text' style='width:100%; height:22px' id='", this._nameFieldId, "' />");
	html.append(Dwt.CARET_HACK_END);
	html.append("</td></tr>");

	html.append("<tr valign='center'><td class='ZmChatDialogField'>", ZmMsg.imGroupsLabel, "</td>");
	html.append("<td>");
	html.append(Dwt.CARET_HACK_BEGIN);
	html.append("<input autocomplete=OFF type='text' style='width:100%; height:22px' id='", this._groupsFieldId, "' />");
	html.append(Dwt.CARET_HACK_END);
	html.append("</td></tr>");
	html.append("</table>");

	return html.toString();
};

ZmNewRosterItemDialog.prototype.popup =
function(loc) {
	DwtDialog.prototype.popup.call(this, loc);
	document.getElementById(this._addressFieldId).focus();
};


ZmNewRosterItemDialog.prototype._okButtonListener =
function(ev) {
	var results = this._getRosterItemData();
	if (results)
		DwtDialog.prototype._buttonListener.call(this, ev, results);
};

ZmNewRosterItemDialog.prototype._getRosterItemData =
function() {
	var name = AjxStringUtil.trim(document.getElementById(this._nameFieldId).value);
	var msg = ZmRosterItem.checkName(name);

	var address = AjxStringUtil.trim(document.getElementById(this._addressFieldId).value);
	if (address) address = address.replace(/;$/, "");
	address = AjxDispatcher.run("GetRoster").makeServerAddress(address, this._serviceTypeSelect.getValue());
	if (!msg) msg = ZmRosterItem.checkAddress(address);

	var groups = AjxStringUtil.trim(document.getElementById(this._groupsFieldId).value);
	if (groups) groups = groups.replace(/,$/, "");
	if (!msg) msg = ZmRosterItem.checkGroups(groups);

	return (msg ? this._showError(msg) : [address, name, groups]);
};

ZmNewRosterItemDialog.prototype.reset =
function() {
	ZmDialog.prototype.reset.call(this);
	var field = document.getElementById(this._addressFieldId);
	field.value = "";
	//field.readOnly = false;
	field.disabled = false;
	this._serviceTypeSelect.setEnabled(true);
	document.getElementById(this._nameFieldId).value = "";
	document.getElementById(this._groupsFieldId).value = "";
};

ZmNewRosterItemDialog.prototype.setGroups =
function(newGroups) {
	document.getElementById(this._groupsFieldId).value = newGroups || "";
};

ZmNewRosterItemDialog.prototype.setName =
function(newName) {
	document.getElementById(this._nameFieldId).value = newName || "";
};

ZmNewRosterItemDialog.prototype.setAddress =
function(newAddress, readonly) {
	var field = document.getElementById(this._addressFieldId);
	var a = AjxDispatcher.run("GetRoster").breakDownAddress(newAddress);
	if (a.type) {
		this._serviceTypeSelect.setSelectedValue(a.type);
		newAddress = a.addr;
	}
	field.value = newAddress;
	//if (readonly) field.readOnly = true;
	if (readonly) {
		field.disabled = true;
		this._serviceTypeSelect.setEnabled(false);
	}
};


ZmNewRosterItemDialog.prototype._initAddressAutocomplete =
function() {
	if (this._addressAutocomplete || !this._appCtxt.get(ZmSetting.CONTACTS_ENABLED))
		return;

	var shell = this._appCtxt.getShell();
	var contactsApp = shell ? shell.getData(ZmAppCtxt.LABEL).getApp(ZmApp.CONTACTS) : null;
	var contactsList = contactsApp ? contactsApp.getContactList : null;
	var params = {parent: shell, dataClass: contactsApp, dataLoader: contactsList,
				  matchValue: ZmContactsApp.AC_VALUE_EMAIL};
	this._addressAutocomplete = new ZmAutocompleteListView(params);
	this._addressAutocomplete.handle(document.getElementById(this._addressFieldId));
};

ZmNewRosterItemDialog.prototype._initGroupAutocomplete =
function() {
	if (this._groupAutocomplete) return;

	var shell = this._appCtxt.getShell();
	var imApp = shell ? shell.getData(ZmAppCtxt.LABEL).getApp(ZmApp.IM) : null;
	var groupList = imApp ? imApp.getAutoCompleteGroups : null;
	var params = {parent: shell, dataClass: imApp, dataLoader: groupList,
				  matchValue: "text", separator: ','};
	this._groupAutocomplete = new ZmAutocompleteListView(params);
	this._groupAutocomplete.handle(document.getElementById(this._groupsFieldId));
};

ZmNewRosterItemDialog.prototype._showError =
function(msg, loc) {
	var msgDialog = this._appCtxt.getMsgDialog();
	msgDialog.reset();
	loc = loc ? loc : new DwtPoint(this.getLocation().x + 50, this.getLocation().y + 100);
	msgDialog.setMessage(msg, DwtMessageDialog.CRITICAL_STYLE);
	msgDialog.popup(loc);
	return null;
};

