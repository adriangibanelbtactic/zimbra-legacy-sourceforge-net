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

// This has a bunch of listeners that might potentially be called from
// more than one place.  Should merge this class with
// ZmChatListController and rename to ZmImController or something.

ZmRosterTreeController = function(appCtxt) {
	if (arguments.length === 0) { return; }

	this._imApp = appCtxt.getApp(ZmApp.IM);
	this._confirmDeleteRosterItemFormatter = new AjxMessageFormat(ZmMsg.imConfirmDeleteRosterItem);

	this._appCtxt = appCtxt;
	this._listeners = {};
	this._listeners[ZmOperation.NEW_ROSTER_ITEM] = new AjxListener(this, this._newRosterItemListener);
	this._listeners[ZmOperation.IM_NEW_CHAT] = new AjxListener(this, this._imNewChatListener);
	this._listeners[ZmOperation.IM_NEW_GROUP_CHAT] = new AjxListener(this, this._imNewGroupChatListener);
	this._listeners[ZmOperation.EDIT_PROPS] = new AjxListener(this, this._editRosterItemListener);
	this._listeners[ZmOperation.IM_CREATE_CONTACT] = new AjxListener(this, this._imCreateContactListener);
	this._listeners[ZmOperation.IM_ADD_TO_CONTACT] = new AjxListener(this, this._imAddToContactListener);
	this._listeners[ZmOperation.IM_EDIT_CONTACT] = new AjxListener(this, this._imEditContactListener);
	this._listeners[ZmOperation.IM_GATEWAY_LOGIN] = new AjxListener(this, this._imGatewayLoginListener);
	this._listeners[ZmOperation.IM_TOGGLE_OFFLINE] = new AjxListener(this, this._imToggleOffline);
	this._listeners[ZmOperation.DELETE] = new AjxListener(this, this._deleteListener);
};

ZmRosterTreeController.prototype.toString = function() {
	return "ZmRosterTreeController";
};

ZmRosterTreeController.prototype._deleteListener =
function(ev) {
	var ds = this._deleteShield = this._appCtxt.getYesNoCancelMsgDialog();
	ds.reset();
	ds.registerCallback(DwtDialog.YES_BUTTON, this._deleteShieldYesCallback, this, ev.buddy);
	ds.registerCallback(DwtDialog.NO_BUTTON, this._clearDialog, this, this._deleteShield);
	var msg = this._confirmDeleteRosterItemFormatter.format([ ev.buddy.getAddress() ]);
	ds.setMessage(msg, DwtMessageDialog.WARNING_STYLE);
	ds.popup();
};

ZmRosterTreeController.prototype._deleteShieldYesCallback =
function(buddy) {
	buddy._delete();
	this._clearDialog(this._deleteShield);
};

ZmRosterTreeController.prototype._clearDialog = function(dlg) {
	dlg.popdown();
};

// FIXME: move this in ZmImOverview
ZmRosterTreeController.prototype._imToggleOffline = function(ev) {
	var view = this._imApp.getOverviewPanelContent();
	view.__filterOffline = !view.__filterOffline;
	if (view.__filterOffline) {
		ev.dwtObj.setImage("Check");
		view.addFilter(ZmImOverview.FILTER_OFFLINE_BUDDIES);
	} else {
		ev.dwtObj.setImage(null);
		view.removeFilter(ZmImOverview.FILTER_OFFLINE_BUDDIES);
	}
};

ZmRosterTreeController.prototype._imGatewayLoginListener = function(ev) {
	var dlg = this._appCtxt.getIMGatewayLoginDialog();
	if (!this._registerGatewayCb) {
		this._registerGatewayCb = new AjxCallback(this, this._registerGatewayCallback);
	}
	if (ev && ev.gwType)
		dlg.selectGwType(ev.gwType);
	ZmController.showDialog(dlg, this._registerGatewayCb);
};

ZmRosterTreeController.prototype._registerGatewayCallback = function(service, screenName, password) {
	this._appCtxt.getIMGatewayLoginDialog().popdown();
	AjxDispatcher.run("GetRoster").registerGateway(service, screenName, password);
};

ZmRosterTreeController.prototype._newRosterItemListener =
function(ev) {
	var newDialog = this._appCtxt.getNewRosterItemDialog();
	newDialog.setTitle(ZmMsg.createNewRosterItem);
	if (!this._newRosterItemCb) {
		this._newRosterItemCb = new AjxCallback(this, this._newRosterItemCallback);
	}
	ZmController.showDialog(newDialog, this._newRosterItemCb);
	if (ev.group)
		newDialog.setGroups(ev.group);
	if (ev.name)
		newDialog.setName(ev.name);
	if (ev.address)
		newDialog.setAddress(ev.address);
};

ZmRosterTreeController.prototype._editRosterItemListener =
function(ev) {
	var newDialog = this._appCtxt.getNewRosterItemDialog();
	newDialog.setTitle(ZmMsg.editRosterItem);
	if (!this._newRosterItemCb) {
		this._newRosterItemCb = new AjxCallback(this, this._newRosterItemCallback);
	}
	ZmController.showDialog(newDialog, this._newRosterItemCb);
	var ri = ev.buddy;
	newDialog.setAddress(ri.getAddress(), true);
	newDialog.setName(ri.getName());
	newDialog.setGroups(ri.getGroups());
};


ZmRosterTreeController.prototype._imNewChatListener =
function(ev) {
	var clc = this._imApp.getChatListController();
	if (ev && ev.buddy) {
		clc.chatWithRosterItem(ev.buddy);
	} else {
		// select from GAL
		ZmOneContactPicker.showPicker(
			{ onAutocomplete: AjxCallback.simpleClosure(function(contact, dlg){
				dlg.popdown();
				var addr = contact.getIMAddress();
				var roster = AjxDispatcher.run("GetRoster");
// XXX: we can't look for a suitable address since the server doesn't return the default domain.  ugh.
// 				if (!addr) {
// 					var fields = [ ZmContact.F_email,
// 						       ZmContact.F_email1,
// 						       ZmContact.F_email2 ];
// 					for (var i = 0; i < fields.length; ++i) {
// 						addr = contact.getAttr(fields[i]);
// 						var gwAddr = roster.breakDownAddress(addr);
// 					}
// 				}
				if (!addr)
					addr = contact.getEmail();
				var list = roster.getRosterItemList();
				var item = list.getByAddr(addr);
				if (!item)
					// create a temporary item
					item = new ZmRosterItem(addr, list, this._appCtxt, contact.getAttendeeText(),
								new ZmRosterPresence(ZmRosterPresence.SHOW_UNKNOWN,
										     null,
										     ZmMsg.unknown));
				clc.chatWithRosterItem(item);
			}, this)
			}
		);
	}
};

ZmRosterTreeController.prototype._imNewGroupChatListener =
function(ev) {
	// NOT IMPLEMENTED
// 	var org = this._getActionedOrganizer(ev);
// 	var clc = this._imApp.getChatListController();
// 	clc.chatWithRosterItems(org.getRosterItems(), org.getName()+" "+ZmMsg.imGroupChat);
};

// Create a roster item
ZmRosterTreeController.prototype._newRosterItemCallback =
function(addr, rname, groups) {
	this._appCtxt.getNewRosterItemDialog().popdown();
	this._imApp.getRoster().createRosterItem(addr, rname, groups);
};

ZmRosterTreeController.prototype._imCreateContactListener = function(ev) {
	var item = ev.buddy;
	var contact = new ZmContact(this._appCtxt);
	contact.setAttr(ZmContact.F_imAddress1, item.getAddress());
	AjxDispatcher.run("GetContactController").show(contact, true);
};

ZmRosterTreeController.prototype._imAddToContactListener = function(ev) {
	var item = ev.buddy;
	ZmOneContactPicker.showPicker(
		{
			onAutocomplete: AjxCallback.simpleClosure(function(contact, dlg) {
				dlg.popdown();
				var fields = [ ZmContact.F_imAddress1, ZmContact.F_imAddress2, ZmContact.F_imAddress3 ];
				for (var i = 0; i < fields.length; ++i) {
					var f = fields[i];
					var orig = contact.getAttr(f);
					if (!orig || !/\S/.test(orig)) {
						contact.setAttr(f, item.getAddress());
						AjxDispatcher.run("GetContactController").show(contact, true);
						// reset the attribute now so that
						// ZmContactView thinks it's been
						// modified.  sort of makes sense. ;-)
						contact.setAttr(f, orig);
						break;
					}
				}
				if (i == fields.length) {
					// not set as all IM fields are filed
					// XXX: warn?
				}
			}, this )
		}
	);
};

ZmRosterTreeController.prototype._imEditContactListener = function(ev) {
	var item = ev.buddy;
	AjxDispatcher.run("GetContactController").show(item.getContact(), false);
};
