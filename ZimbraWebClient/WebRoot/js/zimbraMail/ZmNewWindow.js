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

/**
* Creates a controller to run ZmNewWindow. Do not call directly, instead use the run()
* factory method.
* @constructor
* @class
* This class is the controller for a window created outside the main client window. It is
* a very stripped down and specialized version of ZmZimbraMail. The child window is
* single-use; it does not support switching among multiple views.
*
* @author Parag Shah
*
* @param appCtxt	[ZmAppCtxt]		the app context
* @param domain		[string]	current domain
*/
function ZmNewWindow(appCtxt, domain) {

	ZmController.call(this, appCtxt);

	appCtxt.setAppController(this);

	this._settings = appCtxt.getSettings();
	this._shell = appCtxt.getShell();

	// Register keymap and global key action handler w/ shell's keyboard manager
	this._kbMgr = appCtxt.getKeyboardMgr();
	if (this._appCtxt.get(ZmSetting.USE_KEYBOARD_SHORTCUTS)) {
		this._kbMgr.enable(true);
		this._kbMgr.registerKeyMap(new ZmKeyMap(appCtxt));
		this._kbMgr.pushDefaultHandler(this);
	}

	this._apps = {};
	this.startup();
};

ZmNewWindow.prototype = new ZmController;
ZmNewWindow.prototype.constructor = ZmNewWindow;

ZmNewWindow.APP_CLASS = {};
ZmNewWindow.APP_CLASS[ZmZimbraMail.MAIL_APP]			= ZmMailApp;
ZmNewWindow.APP_CLASS[ZmZimbraMail.CONTACTS_APP]		= ZmContactsApp;

ZmNewWindow.prototype.toString = 
function() {
	return "ZmNewWindow";
};

// Public methods

/**
* Sets up ZmNewWindow, and then starts it by calling its constructor. It is assumed that the
* CSFE is on the same host.
*
* @param domain		[string]	the host that we're running on
*/
ZmNewWindow.run =
function(domain) {

	// inherit parent window's debug level but only enable debug window if not already open
	DBG.setDebugLevel(window.opener.DBG._level, true);

	// Create the global app context
	var appCtxt = new ZmAppCtxt();

	if (!window.parentController) {
		window.parentController = window.opener._zimbraMail;
	}

	// set any global references in parent w/in child window
	if (window.parentController) {
		var parentCtxt = window.parentController._appCtxt;
		appCtxt.setSettings(parentCtxt.getSettings());
		appCtxt.setIdentityCollection(parentCtxt.getIdentityCollection());
	}

	var shell = new DwtShell("MainShell", false, ZmNewWindow._confirmExitMethod);
	appCtxt.setShell(shell);

	// Create upload manager (for sending attachments)
	appCtxt.setUploadManager(new AjxPost(appCtxt.getUploadFrameId()));

	// Go!
	new ZmNewWindow(appCtxt, domain);
};

/**
* Allows this child window to inform parent it's going away
*/
ZmNewWindow.unload = 
function(ev) {
	if (window.opener == null || window.parentController == null)
		return;

	// is there a better way to get a ref to the compose controller?
	var shell = AjxCore.objectWithId(window._dwtShell);
	var appCtxt = shell ? shell.getData(ZmAppCtxt.LABEL) : null;
	var mailApp = appCtxt ? appCtxt.getApp(ZmZimbraMail.MAIL_APP) : null;
	if (mailApp) {
		if (window.command == "compose" || window.command == "composeDetach") {
			// compose controller adds listeners to parent window's list so we need to 
			// remove them before closing this window!
			var cc = mailApp.getComposeController();
			if (cc) {
				cc.dispose();
			}
		} else if (window.command == "msgViewDetach") {
			// msg controller (as a ZmListController) adds listener to tag list
			var mc = mailApp.getMsgController();
			if (mc) {
				mc.dispose();
			}
		}
	}

	if (window.parentController) {
		window.parentController.removeChildWindow(window);
	}
};

ZmNewWindow._confirmExitMethod =
function(ev) {
	if (window.parentController && (window.command == "compose" || window.command == "composeDetach")) {
		// is there a better way to get a ref to the compose controller?
		var shell = AjxCore.objectWithId(window._dwtShell);
		var appCtxt = shell ? shell.getData(ZmAppCtxt.LABEL) : null;
		var cc = appCtxt ? appCtxt.getApp(ZmZimbraMail.MAIL_APP).getComposeController() : null;
		// only show native confirmation dialog if compose view is dirty
		if (cc && cc._composeView.isDirty()) {
			return ZmMsg.newWinComposeExit;
		}
	}
};

/**
* Presents a view based on a command passed through the window object. Possible commands are:
*
* compose			compose window launched in child window
* composeDetach		compose window detached from client
* msgViewDetach		msg view detached from client
*/
ZmNewWindow.prototype.startup =
function() {

	if (!this._appViewMgr) {
		this._appViewMgr = new ZmAppViewMgr(this._shell, this, true, false);
	}
	
	var rootTg = this._appCtxt.getRootTabGroup();
	var startupFocusItem;

	// get params from parent window b/c of Safari bug #7162
	if (window.parentController) {
		var childWinObj = window.parentController.getChildWindow(window);
		if (childWinObj) {
			window.command = childWinObj.command;
			window.args = childWinObj.args;
		}
	}

	// depending on the command, do the right thing
	if (window.command == "compose" || window.command == "composeDetach") {
		var cc = this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getComposeController();
		cc.isChildWindow = true;
		if (window.command == "compose") {
			// bug fix #4681
			var action = window.args[0];
			var msg = (action == ZmOperation.REPLY_ALL) ? this._deepCopyMsg(window.args[1]) : window.args[1];
			cc._setView(window.args[0], msg, window.args[2], window.args[3], window.args[4], null, window.args[6]);
		} else {
			var op = window.args.action ? window.args.action : ZmOperation.NEW_MESSAGE;
			if (window.args.msg && window.args.msg._mode) {
				switch (window.args.msg._mode) {
					case ZmAppt.MODE_DELETE: 
					case ZmAppt.MODE_DELETE_INSTANCE: 
					case ZmAppt.MODE_DELETE_SERIES: {
						op = ZmOperation.REPLY_CANCEL;
						break;
					}
				}
			}

			if (window.args["action"] == ZmOperation.REPLY_ALL)
				window.args.msg = this._deepCopyMsg(window.args.msg);

			cc._setView(op, window.args.msg, null, null, null, window.args.composeMode, window.args.accountName);
			cc._composeView.setDetach(window.args);

			// bug fix #5887 - get the parent window's compose controller
			var parentCC = window.parentController.getApp(ZmZimbraMail.MAIL_APP).getComposeController();
			if (parentCC) {
				// once everything is set in child window, pop parent window's compose view
				parentCC._composeView.reset(true);
				parentCC._app.popView(true);
			}
		}
		rootTg.addMember(cc.getTabGroup());
		startupFocusItem = cc._composeView.getAddrFields()[0];
	} else if (window.command == "msgViewDetach") {
		var msgController = this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getMsgController();
		msgController.isChildWindow = true;
		msgController.show(window.args.msg);
		rootTg.addMember(msgController.getTabGroup());
		startupFocusItem = msgController.getCurrentView();
	}

	var kbMgr = this._appCtxt.getKeyboardMgr();
	kbMgr.setTabGroup(rootTg);
	kbMgr.grabFocus(startupFocusItem);
};

/**
* Pass server requests to the main controller.
*/
ZmNewWindow.prototype.sendRequest = 
function(params) {
	return window.parentController ? window.parentController.sendRequest(params) : null;
};

/**
* Set status messages via the main controller, so they show up in the client's status area.
*/
ZmNewWindow.prototype.setStatusMsg = 
function(msg, level, detail, delay, transition) {
	if (window.parentController)
		window.parentController.setStatusMsg(msg, level, detail, delay, transition);
};

/**
* Returns a handle to the given app.
*
* @param appName	an app name
*/
ZmNewWindow.prototype.getApp =
function(appName) {
	if (!this._apps[appName])
		this._createApp(appName);
	return this._apps[appName];
};

/**
* Returns a handle to the app view manager.
*/
ZmNewWindow.prototype.getAppViewMgr =
function() {
	return this._appViewMgr;
};

// App view mgr calls this, we don't need it to do anything.
ZmNewWindow.prototype.setActiveApp = function() {};

ZmNewWindow.prototype.isChildWindow =
function() {
	return true;
};


// Private methods

// Creates an app object, which doesn't necessarily do anything just yet.
ZmNewWindow.prototype._createApp =
function(appName) {
	if (this._apps[appName]) return;
	this._apps[appName] = new ZmNewWindow.APP_CLASS[appName](this._appCtxt, this._shell, window.parentController);
};

ZmNewWindow.prototype._deepCopyMsg = 
function(msg) {
	// initialize new ZmSearch if applicable
	var newSearch = null;
	var oldSearch = msg.list.search;

	if (oldSearch) {
		newSearch = new ZmSearch(this._appCtxt);

		for (var i in oldSearch) {
			if ((typeof oldSearch[i] == "object") || (typeof oldSearch[i] == "function"))
				continue;
			newSearch[i] = oldSearch[i];
		}

		// manually add objects since they are no longer recognizable
		newSearch.types = new AjxVector();
		var types = oldSearch.types.getArray();
		for (var i in types) {
			newSearch.types.add(types[i]);
		}
	}

	// initialize new ZmMailList
	var newMailList = new ZmMailList(msg.list.type, this._appCtxt, newSearch);
	for (var i in msg.list) {
		if ((typeof msg.list[i] == "object") || (typeof msg.list[i] == "function"))
			continue;
		newMailList[i] = msg.list[i];
	}

	// finally, initialize new ZmMailMsg
	var newMsg = new ZmMailMsg(this._appCtxt, msg.id, newMailList);

	for (var i in msg) {
		if ((typeof msg[i] == "object") || (typeof msg[i] == "function"))
			continue;
		newMsg[i] = msg[i];
	}

	// manually add any objects since they are no longer recognizable
	for (var i in msg._addrs) {
		var addrs = msg._addrs[i].getArray();
		for (var j in addrs) {
			newMsg._addrs[i].add(addrs[j]);
		}
	}

	if (msg._attachments.length > 0) {
		for (var i in msg._attachments) {
			newMsg._attachments.push(msg._attachments[i]);
		}
	}

	for (var i in msg._bodyParts) {
		newMsg._bodyParts.push(msg._bodyParts[i]);
	}

	if (msg._topPart) {
		newMsg._topPart = new ZmMimePart();
		for (var i in msg._topPart) {
			if ((typeof msg._topPart[i] == "object") || (typeof msg._topPart[i] == "function"))
				continue;
			newMsg._topPart[i] = msg._topPart[i];
		}
		var children = msg._topPart.children.getArray();
		for (var i in children) {
			newMsg._topPart.children.add(children[i]);
		}
	}

	return newMsg;
};

ZmNewWindow.prototype.getKeyMapName =
function() {
	var curView = this._appViewMgr.getCurrentView();
	if (curView && curView.getController) {
		var ctlr = curView.getController();
		if (ctlr && ctlr.getKeyMapName) {
			return ctlr.getKeyMapName();
		}
	}
	return "Global";
};

ZmNewWindow.prototype.handleKeyAction =
function(actionCode, ev) {
	switch (actionCode) {
		default: {
			var curView = this._appViewMgr.getCurrentView();
			if (curView && curView.getController) {
				var ctlr = curView.getController();
				if (ctlr && ctlr.handleKeyAction) {
					return ctlr.handleKeyAction(actionCode, ev);
				}
			} else {
				return false;
			}
			break;
		}
	}
	return true;
};
