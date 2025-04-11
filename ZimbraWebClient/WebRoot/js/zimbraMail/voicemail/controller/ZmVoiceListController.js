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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

ZmVoiceListController = function(appCtxt, container, app) {
	if (arguments.length == 0) return;
	ZmListController.call(this, appCtxt, container, app);
	this._listeners[ZmOperation.VOICE_CALL] = new AjxListener(this, this._callListener);

	this._folder = null;
}
ZmVoiceListController.prototype = new ZmListController;
ZmVoiceListController.prototype.constructor = ZmVoiceListController;

ZmVoiceListController.prototype.toString =
function() {
	return "ZmVoiceListController";
};

/**
* Displays the given search results.
*
* @param search		search results (which should contain a list of conversations)
* @param folder		The folder being shown
*/
ZmVoiceListController.prototype.show =
function(searchResult, folder) {
	this._folder = folder;
	ZmListController.prototype.show.call(this, searchResult);
	this._list = searchResult.getResults(folder.getSearchType());
	if (this._list)
		this._list.setHasMore(searchResult.getAttribute("more"));	
	this._setup(this._currentView);

	var elements = new Object();
	elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar[this._currentView];
	elements[ZmAppViewMgr.C_APP_CONTENT] = this._listView[this._currentView];
	this._setView(this._currentView, elements, true);
};

ZmVoiceListController.prototype.getFolder =
function(searchResult, folder) {
	return this._folder;
};

ZmVoiceListController.prototype._setViewContents =
function(viewId) {
	var view = this._listView[viewId];
	view.setFolder(this._folder);	
	view.set(this._list, ZmItem.F_DATE);
};

ZmVoiceListController.prototype._participantOps =
function() {
	return [ZmOperation.CONTACT];
};

ZmVoiceListController.prototype._initializeToolBar =
function(view) {
	if (!this._toolbar[view]) {
		ZmListController.prototype._initializeToolBar.call(this, view);
		this._toolbar[view].addFiller();
		var tb = new ZmNavToolBar(this._toolbar[view], DwtControl.STATIC_STYLE, null, ZmNavToolBar.SINGLE_ARROWS, true);
		this._setNavToolBar(tb, view);
	};
};

ZmVoiceListController.prototype._getView = 
function() {
	return this._listView[this._currentView];
};

ZmVoiceListController.prototype._getToolbar = 
function() {
	return this._toolbar[this._currentView]
};

ZmVoiceListController.prototype._getMoreSearchParams =
function(params) {
	params.soapInfo = ZmVoiceApp.SOAP_INFO;
};

ZmVoiceListController.prototype._createNewContact =
function(ev) {
	var item = ev.item;
	var contact = new ZmContact(this._appCtxt);
	contact.initFromPhone(this._getView().getCallingParty(item).getDisplay());
	return contact;
};

ZmVoiceListController.prototype._callListener =
function(ev) {
	// Point an iframe at the callto url, which will launch the client's callto application.
	var view = this._getView()
	var item = view.getSelection()[0];
	var phone = view.getCallingParty(item);
	if (!this._callControl) {
		this._callControl = new DwtControl(this._appCtxt.getShell());
		this._callControl.setVisible(false);
	}
	var iframeHtml = ["<iframe src='", phone.getCallUrl(),"'></iframe>"].join("");
	this._callControl.getHtmlElement().innerHTML = iframeHtml;
};

ZmVoiceListController.prototype._listActionListener =
function(ev) {
	ZmListController.prototype._listActionListener.call(this, ev);

	var view = ev.dwtObj;
	var isParticipant = ev.field == ZmItem.F_PARTICIPANT;
	var actionMenu = this.getActionMenu();
	var item = ev.item;
	
	// Update the add/edit contact item.
	if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
		var contact = item.participants ? item.participants.getArray()[0] : null;
		var newOp = contact ? ZmOperation.EDIT_CONTACT : ZmOperation.NEW_CONTACT;
		var newText = contact? null : ZmMsg.AB_ADD_CONTACT;
		ZmOperation.setOperation(actionMenu, ZmOperation.CONTACT, newOp, newText);
		var contacts = AjxDispatcher.run("GetContacts");
		this._actionEv.contact = contact;
		this._setContactText(contact != null);
	}

	// Update the call item to show the number it'll call.
	var callItem = actionMenu.getMenuItem(ZmOperation.VOICE_CALL);
	if (callItem.getEnabled()) {
		var phone = view.getCallingParty(item);
		var text = AjxMessageFormat.format(ZmMsg.callNumber, phone.getDisplay());
		callItem.setText(text);
	} else {
		callItem.setText(ZmMsg.call);
	}
	
	actionMenu.popup(0, ev.docX, ev.docY);
};

