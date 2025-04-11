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

/**
 * Creates an account object containing meta info about the account
 * @constructor
 * @class
 * An account object is created primarily if a user has added subaccounts that
 * he would like to manage (i.e. family mailbox).
 *
 * @author Parag Shah
 *
 * @param appCtxt		[ZmAppCtxt]		the app context
 */
ZmAccount = function(appCtxt) {
	this._appCtxt = appCtxt;
};


// Public methods


ZmAccount.createFromDom =
function(node, appCtxt) {
	var acct = new ZmAccount(appCtxt);
	acct._loadFromDom(node);
	return acct;
};

ZmAccount.prototype.toString =
function() {
	return "ZmAccount";
};

ZmAccount.prototype.load =
function(callback) {
	if (!this.loaded) {
		// create new ZmSetting for this account
		this._settings = new ZmSettings(this._appCtxt);
		this._settings.initialize();

		// for all *loaded* apps, add their app-specific settings
		for (var i = 0; i < ZmApp.APPS.length; i++) {
			var appName = ZmApp.APPS[i];
			var setting = ZmApp.SETTING[appName];
			if (setting && this._appCtxt.get(setting)) {
				var app = this._appCtxt.getApp(appName);
				if (app) {
					app._registerSettings(this._settings);
				}
			}
		}

		// load user settings retrieved from server now
		var respCallback = new AjxCallback(this, this._loadAcctSettings, callback);
		var errorCallback = new AjxCallback(this, this._errorAcctSettings);
		this._settings.loadUserSettings(respCallback, errorCallback, this.name);
	} else {
		if (callback)
			callback.run();
	}
};


// Private methods

ZmAccount.prototype._loadFromDom =
function(node) {
	this.id = node.id;
	this.name = node.name;
	this.visible = node.visible;

	this.loaded = false;
};


// Callbacks

ZmAccount.prototype._loadAcctSettings =
function(callback, result) {
DBG.println("------- Account settings successfully loaded for " + this.name);

	// TODO - load folders/shares/zimlets/tags/saved-searches for this account
	var soapDoc = AjxSoapDoc.create("GetFolderRequest", "urn:zimbraMail");
	var method = soapDoc.getMethod();
	method.setAttribute("visible", "1");

	var respCallback = new AjxCallback(this, this._handleResponseFolder, callback);
	this._appCtxt.getRequestMgr().sendRequest({soapDoc:soapDoc, asyncMode:true, callback:respCallback});
};

ZmAccount.prototype._handleResponseFolder =
function(callback, result) {
	var resp = result.getResponse().GetFolderResponse;
	var folders = resp ? resp.folder[0] : null;
	if (folders) {
/*
		var tree = new ZmFolderTree(this._appCtxt);
		tree.loadFromJs(folders);
//		tree.addChangeListener(this._unreadListener);
		this._appCtxt.setFolderTree(tree, this.name);
*/
	}

	this.loaded = true;

	if (callback) {
		callback.run(this);
	}
};

ZmAccount.prototype._errorAcctSettings =
function(ev) {
DBG.println("------- ERROR loading account settings for " + this.name);
};
