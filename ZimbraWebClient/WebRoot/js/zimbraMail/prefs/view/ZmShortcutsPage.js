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
* Creates an empty shortcuts page.
* @constructor
* @class
* This class represents a page that allows the user to specify custom
* keyboard shortcuts. Currently, we limit custom shortcuts to actions
* that involve a folder, tag, or saved search. The user specifies a 
* number to refer to a particular organizer, which binds the shortcut
* to that organizer. For example, the user might assign the folder
* "test" the number 3, and then the shortcut "M,3" would move mail to
* that folder.
* <p>
* Only a single pref (the user's shortcuts gathered together in a string)
* is represented.</p>
*
* @author Conrad Damon
* @param parent				[DwtControl]				the containing widget
* @param appCtxt			[ZmAppCtxt]					the app context
* @param view				[constant]					which page we are
* @param controller			[ZmPrefController]			prefs controller
*/
function ZmShortcutsPage(parent, appCtxt, view, controller) {

	DwtTabViewPage.call(this, parent, "ZmShortcutsPage");
	
	this._appCtxt = appCtxt;
	this._view = view; // which preferences page we are
	this._controller = controller;
	this._title = [ZmMsg.zimbraTitle, ZmMsg.options, ZmPrefView.TAB_NAME[view]].join(": ");
	this._prefId = ZmPrefView.PREFS[ZmPrefView.SHORTCUTS][0]; // our sole pref

	this._organizers = [ZmOrganizer.FOLDER];
	if (appCtxt.get(ZmSetting.SAVED_SEARCHES_ENABLED)) {
		this._organizers.push(ZmOrganizer.SEARCH);
	}
	if (appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		this._organizers.push(ZmOrganizer.TAG);
	}

	this._createHtml();
	
	this._scTabView = new ZmShortcutsPageTabView(this, appCtxt, controller, this._organizers, this._prefId );
	var element = this._scTabView.getHtmlElement();
	element.parentNode.removeChild(element);
	var parent = document.getElementById(this._scTabViewId);
	parent.appendChild(element);

//	controller.getPrefsView().addControlListener(new AjxListener(this, this._controlListener));

	this._rendered = false;
	this._hasRendered = false;
};

ZmShortcutsPage.prototype = new DwtTabViewPage;
ZmShortcutsPage.prototype.constructor = ZmShortcutsPage;

ZmShortcutsPage.prototype.toString =
function () {
    return "ZmShortcutsPage";
};

ZmShortcutsPage.prototype.hasRendered =
function () {
	return this._hasRendered;
};

/**
 * Displays the single pref that belongs to this page, if that hasn't been done
 * already. Note that this is an override of DwtTabViewPage.showMe(), so that it's
 * called only when the tab is selected and the page becomes visible.
 */
ZmShortcutsPage.prototype.showMe =
function() {
	Dwt.setTitle(this._title);
	this._controller._resetOperations(this._controller._toolbar, this._view);
	var dirty = this._controller.isDirty(this._view);
	if (this._hasRendered && !dirty) return;

	this._prefPresent = {};
	this._prefPresent[this._prefId] = true;
	DBG.println(AjxDebug.DBG2, "rendering shortcuts page");

	if (!this._hasRendered){
		this._hasRendered = true;
	} else {
		this._controller.setDirty(this._view, false);
	}

	this._scTabView.show();
	
	// save the current value (for checking later if it changed)
	var pref = this._appCtxt.getSettings().getSetting(this._prefId);
	pref.origValue = this._getPrefValue(this._prefId);
};

ZmShortcutsPage.prototype.getFormValue =
function() {
	var shortcuts = this._scTabView.getShortcuts();
	shortcuts.sort();
	return shortcuts.join("|");
};

ZmShortcutsPage.prototype.getTitle =
function() {
	return this._title;
};

ZmShortcutsPage.prototype.reset = function() {};

/*
 * Returns the value of the specified pref, massaging it if necessary.
 *
 * @param id			[constant]		pref ID
 * @param useDefault	[boolean]		if true, use pref's default value
 * @param convert		[boolean]		if true, convert value to user-visible form
 */
ZmShortcutsPage.prototype._getPrefValue =
function(id, useDefault, convert) {
	var value = null;
	var pref = this._appCtxt.getSettings().getSetting(id);
	return useDefault ? pref.getDefaultValue() : pref.getValue();
};

ZmShortcutsPage.prototype._createHtml =
function() {

	var html = [];
	var i = 0;
	this._headerId = Dwt.getNextId();
	this._scTabViewId = Dwt.getNextId();
	
	html[i++] = "<div class='BigHead' id='";
	html[i++] = this._headerId;
	html[i++] = "'>";
	html[i++] = ZmMsg.keyboardShortcuts;
	html[i++] = "</div>";
	html[i++] = "<div id='";
	html[i++] = this._scTabViewId;
	html[i++] = "'></div>";

	this.getHtmlElement().innerHTML = html.join("");
};

ZmShortcutsPage.prototype._getHeaderHeight =
function() {
	var header = document.getElementById(this._headerId);
	return header ? Dwt.getSize(header).y : 0;
};

ZmShortcutsPage.prototype._controlListener = 
function(ev) {
	if (ev.oldHeight != ev.newHeight) {
		this._scTabView._resetSize(ev.oldHeight - ev.newHeight);
	}
};

/**
 * Creates an empty tab view.
 * @constructor
 * @class
 * This tab view contains up to four tab pages: a list of the current shortcuts,
 * and one page each for creating custom shortcuts for folders, tags, and saved
 * searches.
 * 
 * @param parent			[DwtControl]		the containing widget
 * @param appCtxt			[ZmAppCtxt]			the app context
 * @param controller		[ZmPrefController]	prefs controller
 * @param organizers		[array]				list of organizer types to handle
 * @param prefId			[int]				ID of shortcuts setting
 */
function ZmShortcutsPageTabView(parent, appCtxt, controller, organizers, prefId) {

    DwtTabView.call(this, parent, "ZmPrefView");

	this._parent = parent;
    this._appCtxt = appCtxt;
	this._controller = controller;
	this._organizers = organizers;

	this._scTabView = {};
	this._hasRendered = false;
	this._setting = appCtxt.get(prefId);
};

ZmShortcutsPageTabView.SHORTCUTS_LIST = 0;

ZmShortcutsPageTabView.TAB_NAME = {};
ZmShortcutsPageTabView.TAB_NAME[ZmShortcutsPageTabView.SHORTCUTS_LIST]	= ZmMsg.shortcutList;
ZmShortcutsPageTabView.TAB_NAME[ZmOrganizer.FOLDER]						= ZmMsg.mailShortcuts;
ZmShortcutsPageTabView.TAB_NAME[ZmOrganizer.SEARCH]						= ZmMsg.searchShortcuts;
ZmShortcutsPageTabView.TAB_NAME[ZmOrganizer.TAG]						= ZmMsg.tagShortcuts;

ZmShortcutsPageTabView.prototype = new DwtTabView;
ZmShortcutsPageTabView.prototype.constructor = ZmShortcutsPageTabView;

ZmShortcutsPageTabView.prototype.show =
function() {
	if (this._hasRendered) return;

	// shortcut list
	var view = ZmShortcutsPageTabView.SHORTCUTS_LIST;
	var viewObj = new ZmShortcutsPageTabViewList(this._parent, this._appCtxt, this._controller);
	this._scTabView[view] = viewObj;
	this.addTab(ZmShortcutsPageTabView.TAB_NAME[view], this._scTabView[view]);

	// custom shortcuts
	for (var i = 0; i < this._organizers.length; i++) {
		view = this._organizers[i];
        viewObj = new ZmShortcutsPageTabViewCustom(this._parent, this._appCtxt, view, this._controller, this._setting);
		this._scTabView[view] = viewObj;
		this.addTab(ZmShortcutsPageTabView.TAB_NAME[view], this._scTabView[view]);
	}
	this._resetSize();
	this._hasRendered = true;
};

// Grabs the shortcuts from the pages and returns them.
ZmShortcutsPageTabView.prototype.getShortcuts =
function() {
	var shortcuts = [];
	for (var i = 0; i < this._organizers.length; i++) {
		var tv = this._scTabView[this._organizers[i]];
		shortcuts = shortcuts.concat(tv.getShortcuts());
	}
	return shortcuts;
};

// HACK: set height. Should be done via styles, but I kept winding up with either
// zero height or a scroll bar. Need to fudge for IE.
ZmShortcutsPageTabView.prototype._resetSize =
function(delta) {
	delta = delta || 0;
	var pv = this._controller.getPrefsView();
	var prefsHeight = pv.getSize().y;
	var prefsTabBarHeight = pv._tabBar.getSize().y;
	var headerHeight = this._parent._getHeaderHeight();
	if (!headerHeight) { return; }
	var height = prefsHeight - (prefsTabBarHeight + headerHeight) - delta;
	DBG.println(AjxDebug.DBG2, "shortcuts page resize: " + [delta, prefsHeight, prefsTabBarHeight, headerHeight, height].join(" / "));
	this.setSize(Dwt.DEFAULT, AjxEnv.isIE ? height - 10 : height);
//	this.setBounds(Dwt.DEFAULT, Dwt.DEFAULT, Dwt.DEFAULT, AjxEnv.isIE ? height - 10 : height);
};

/**
 * Creates an empty tab page.
 * @constructor
 * @class
 * This class displays all the keyboard shortcuts that are currently 
 * available.
 *
 * @param parent			[DwtControl]				the containing widget
 * @param appCtxt			[ZmAppCtxt]					the app context
 * @param controller		[ZmPrefController]			prefs controller
 */
function ZmShortcutsPageTabViewList(parent, appCtxt, controller) {

	DwtTabViewPage.call(this, parent, "ZmShortcutsPageTabViewList");
	
	this._appCtxt = appCtxt;
	this._controller = controller;

	this._hasRendered = false;
};

ZmShortcutsPageTabViewList.prototype = new DwtTabViewPage;
ZmShortcutsPageTabViewList.prototype.constructor = ZmShortcutsPageTabViewList;

ZmShortcutsPageTabViewList.prototype.showMe =
function() {
	if (this._hasRendered && !this._dirty) return;
	if (this._dirty) {
		this._getHtmlElement().innerHTML = "";
	}

	this._renderShortcuts();

	if (!this._hasRendered){
		this._hasRendered = true;
	} else {
		this._controller.setDirty(this._view, false);
	}

	if (AjxEnv.isIE) {
		var tvSize = this.parent._scTabView.getSize();
		this.setSize(tvSize.x - 40, Dwt.DEFAULT);
	}
};

ZmShortcutsPageTabViewList.prototype._renderShortcuts =
function() {
	var html = [];
	var i = 0;
	html[i++] = "<div style='padding:10px'>";
	i = this._getKeysHtml(ZmKeys, ZmKeyMap.MAP_NAME, html, i);
	i = this._getKeysHtml(AjxKeys, DwtKeyMap.MAP_NAME, html, i);
	html[i++] = "</div>";
	
	this.getHtmlElement().innerHTML = html.join("");
};

ZmShortcutsPageTabViewList.prototype._getKeysHtml =
function(keys, mapNames, html, i) {
	var kmm = this._appCtxt.getKeyboardMgr().__keyMapMgr;	
	var mapDesc = {};
	var maps = [];
	var actionDesc = {};
	var keySequences = {};
	for (var propName in keys) {
		var propValue = keys[propName];
		if (typeof propValue != "string") { continue; }
		var parts = propName.split(".");
		var map = parts[0];
		var action = (DwtKeyMap.IS_DOC_KEY[parts[1]]) ? null : parts[1];
		var skip = false;
		// make sure shortcut is defined
		if (action) {
			var ks = kmm.getKeySequences(mapNames[map], action);
			skip = !(ks && ks.length);
		}
		if (!skip && (parts[1] == "description" || parts[2] == "description")) {
			if (parts[1] == "description") {
				maps.push(map);
				mapDesc[map] = propValue;
			} else {
				if (!actionDesc[map]) {
					actionDesc[map] = {};
					keySequences[map] = [];
				}
				var scKey = [map, action].join(".");
				var scValue = keys[scKey];
				if (!scValue) { continue; }
				keySequences[map].push(scKey);
				actionDesc[map][scKey] = propValue;
			}
		}
	}
	
	var sortFunc = function(keyA, keyB) {
		var sortPropNameA = [keyA, "sort"].join(".");
		var sortPropNameB = [keyB, "sort"].join(".");
		var sortA = keys[sortPropNameA] ? Number(keys[sortPropNameA]) : 0;
		var sortB = keys[sortPropNameB] ? Number(keys[sortPropNameB]) : 0;
		return (sortA > sortB) ? 1 : (sortA < sortB) ? -1 : 0;
	}
	maps.sort(sortFunc);
	var or = [" ", ZmMsg.or, " "].join("");
	for (var j = 0; j < maps.length; j++) {
		var map = maps[j];
		if (!keySequences[map]) { continue; }
		html[i++] = "<table class='shortcutList' cellspacing=0 cellpadding=0>";
		html[i++] = "<tr><td class='shortcutListHeader' colspan=2><div class='PanelHead'>";
		html[i++] = mapDesc[map];
		html[i++] = "</div></td></tr>";

		keySequences[map].sort(sortFunc);
		for (var k = 0; k < keySequences[map].length; k++) {
			var scKey = keySequences[map][k];
			var desc = actionDesc[map][scKey];
			var ks = keys[scKey];

			html[i++] = "<tr><td class='shortcutKeys'>";
			var keySeq = ks.split(/\s*;\s*/);
			var keySeq1 = [];
			for (var l = 0; l < keySeq.length; l++) {
				 keySeq1.push(ZmShortcutsPageTabViewList._formatKeySequence(keySeq[l]));
			}
			html[i++] = keySeq1.join(or);
			html[i++] = "</span></td>";
			html[i++] = "<td class='shortcutDescription'>";
			html[i++] = desc;
			html[i++] = "</td></tr>";

		}
		html[i++] = "</table>";
	}
	
	return i;
};

// Translates a key sequence into a friendlier, more readable version
ZmShortcutsPageTabViewList._formatKeySequence =
function(ks) {

	var html = [];
	var i = 0;
	html[i++] = "<span class='shortcutKeyCombo'>";

	var keys = ks.split(",");
	for (var j = 0; j < keys.length; j++) {
		var key = keys[j];
		var parts = key.split("+");
		var mod = (parts.length == 2) ? parts[0] : null;
		var modPlus = mod + "+";	// consts in DwtKeyMap add + to end
		var base = mod ? parts[1] : parts[0];
		var newParts = [], done = false;
		if (modPlus == DwtKeyMap.CTRL || modPlus == DwtKeyMap.ALT || modPlus == DwtKeyMap.META) {
			newParts.push(mod.toLowerCase());
		} else if (modPlus == DwtKeyMap.SHIFT) {
			if (/^[A-Z]$/.test(base)) {
				newParts.push(base);
			} else if (ZmKeyMap.SHIFT[base]) {
				newParts.push(ZmKeyMap.SHIFT[base]);
			} else if (ZmKeyMap.ENTITY[base]) {
				newParts.push(mod.toLowerCase());
				newParts.push(ZmKeyMap.ENTITY[base]);
			} else {
				newParts.push(mod.toLowerCase());
				newParts.push(base);
			}
			done = true;
		}
		if (!done) {
			// base can be: printable char, escaped char name (eg "Comma"), or NNN
			if (base == "NNN") {
				newParts.push("[n]");
			} else if (/^[A-Z]$/.test(base)) {
				newParts.push(base.toLowerCase());
			} else if (ZmKeyMap.ENTITY[base]) {
				newParts.push(ZmKeyMap.ENTITY[base]);
			} else {
				newParts.push(base);
			}
		}
		var newParts1 = [];
		for (var k = 0; k < newParts.length; k++) {
			newParts1.push(ZmShortcutsPageTabViewList._formatKey(newParts[k]));
		}
		html[i++] = newParts1.join("+");
	}
	html[i++] = "</span>";

	return html.join("");
};

ZmShortcutsPageTabViewList._formatKey =
function(key) {
	return ["<span class='shortcutKey'>", key, "</span>"].join("");
};

/**
 * Creates an empty tab page.
 * @constructor
 * @class
 * This class allows the user to view and create aliases for folders, tags, and
 * saved searches, for use in keyboard shortcuts. An alias must be numeric. Once
 * created, it provides an argument to the action.
 *
 * @author Conrad Damon
 * @param parent			[DwtControl]				the containing widget
 * @param appCtxt			[ZmAppCtxt]					the app context
 * @param organizer			[constant]					which organizer this page handles
 * @param controller		[ZmPrefController]			prefs controller
 * @param setting			[string]					value of user's custom shortcuts pref
 */
function ZmShortcutsPageTabViewCustom(parent, appCtxt, organizer, controller, setting) {

	DwtTabViewPage.call(this, parent, "ZmShortcutsPageTabViewCustom");
	
	this._appCtxt = appCtxt;
	this._organizer = organizer;
	this._controller = controller;
	this._setting = setting;

	this._dwtObjects = {};
	this._createHtml();
	this._rendered = false;
	this._hasRendered = false;

	this._browseLstnr = new AjxListener(this, this._browseListener);
	this._internalId = AjxCore.assignId(this);
};

ZmShortcutsPageTabViewCustom.DATA	= "_data_";

// Text that describes a type of organizer
ZmShortcutsPageTabViewCustom.ORG_TEXT = {};
ZmShortcutsPageTabViewCustom.ORG_TEXT[ZmOrganizer.FOLDER]	= ZmMsg.mailFolder;
ZmShortcutsPageTabViewCustom.ORG_TEXT[ZmOrganizer.TAG]		= ZmMsg.tag;
ZmShortcutsPageTabViewCustom.ORG_TEXT[ZmOrganizer.SEARCH]	= ZmMsg.search;

// Plural version of above
ZmShortcutsPageTabViewCustom.ORG_TEXT_PLURAL = {};
ZmShortcutsPageTabViewCustom.ORG_TEXT_PLURAL[ZmOrganizer.FOLDER]	= ZmMsg.mailFolders;
ZmShortcutsPageTabViewCustom.ORG_TEXT_PLURAL[ZmOrganizer.TAG]		= ZmMsg.tags;
ZmShortcutsPageTabViewCustom.ORG_TEXT_PLURAL[ZmOrganizer.SEARCH]	= ZmMsg.savedSearches;

// Name of a sample organizer for each type
ZmShortcutsPageTabViewCustom.SAMPLE_ORG = {};
ZmShortcutsPageTabViewCustom.SAMPLE_ORG[ZmOrganizer.FOLDER]	= ZmMsg.projects;
ZmShortcutsPageTabViewCustom.SAMPLE_ORG[ZmOrganizer.TAG]	= ZmMsg.important;
ZmShortcutsPageTabViewCustom.SAMPLE_ORG[ZmOrganizer.SEARCH]	= ZmMsg.unread;

// Customizable shortcuts for each organizer type (full property name)
ZmShortcutsPageTabViewCustom.SAMPLE_SHORTCUTS = {};
ZmShortcutsPageTabViewCustom.SAMPLE_SHORTCUTS[ZmOrganizer.FOLDER]	= ["mail.GoToFolder", "mail.MoveToFolder"];
ZmShortcutsPageTabViewCustom.SAMPLE_SHORTCUTS[ZmOrganizer.TAG]		= ["global.GoToTag", "global.Tag"];
ZmShortcutsPageTabViewCustom.SAMPLE_SHORTCUTS[ZmOrganizer.SEARCH]	= ["global.SavedSearch"];

// List of actions for each organizer type
ZmShortcutsPageTabViewCustom.ORG_ACTION = {};
ZmShortcutsPageTabViewCustom.ORG_ACTION[ZmOrganizer.FOLDER]	= [ZmKeyMap.GOTO_FOLDER, ZmKeyMap.MOVE_TO_FOLDER];
ZmShortcutsPageTabViewCustom.ORG_ACTION[ZmOrganizer.SEARCH]	= [ZmKeyMap.SAVED_SEARCH];
ZmShortcutsPageTabViewCustom.ORG_ACTION[ZmOrganizer.TAG]	= [ZmKeyMap.GOTO_TAG, ZmKeyMap.TAG];

// Reverse map of above
ZmShortcutsPageTabViewCustom.ACTION_ORG = {};
for (var org in ZmShortcutsPageTabViewCustom.ORG_ACTION) {
	var actions = ZmShortcutsPageTabViewCustom.ORG_ACTION[org];
	for (var j = 0; j < actions.length; j++) {
		ZmShortcutsPageTabViewCustom.ACTION_ORG[actions[j]] = org;
	}
}
delete org;

ZmShortcutsPageTabViewCustom.DIALOG_TEXT = {};
ZmShortcutsPageTabViewCustom.DIALOG_TEXT[ZmOrganizer.FOLDER]	= ZmMsg.chooseFolder;
ZmShortcutsPageTabViewCustom.DIALOG_TEXT[ZmOrganizer.SEARCH]	= ZmMsg.chooseSearch;
ZmShortcutsPageTabViewCustom.DIALOG_TEXT[ZmOrganizer.TAG]		= ZmMsg.chooseTag;

ZmShortcutsPageTabViewCustom.ORG_MAP = {};
ZmShortcutsPageTabViewCustom.ORG_MAP[ZmOrganizer.FOLDER]	= "mail";
ZmShortcutsPageTabViewCustom.ORG_MAP[ZmOrganizer.SEARCH]	= "global";
ZmShortcutsPageTabViewCustom.ORG_MAP[ZmOrganizer.TAG]		= "global";

ZmShortcutsPageTabViewCustom.SAMPLE_KEY = 3;

// widths for the table columns (organizer, shortcut, remove link)
ZmShortcutsPageTabViewCustom.COL1_WIDTH = 300;
ZmShortcutsPageTabViewCustom.COL2_WIDTH = 130;
ZmShortcutsPageTabViewCustom.COL3_WIDTH = 60;

ZmShortcutsPageTabViewCustom.prototype = new DwtTabViewPage;
ZmShortcutsPageTabViewCustom.prototype.constructor = ZmShortcutsPageTabViewCustom;

ZmShortcutsPageTabViewCustom.prototype.showMe =
function() {

	if (this._hasRendered && !this._dirty) return;
	if (this._dirty) {
		this._table.tBodies[0].innerHTML = this._getTableHeaderHtml();
	}

	this._button = this._addButton(this._addButtonDivId, ZmMsg.addShortcut, 120, new AjxListener(this, this._buttonListener));
	this._inputs = {};
	this._renderTable();

	if (!this._hasRendered){
		this._hasRendered = true;
	} else {
		this._controller.setDirty(this._organizer, false);
	}

	if (AjxEnv.isIE) {
		var tvSize = this.parent._scTabView.getSize();
		this.setSize(tvSize.x - 40, Dwt.DEFAULT);
	}
};

ZmShortcutsPageTabViewCustom.prototype.getShortcuts =
function() {
	var kbm = this._appCtxt.getKeyboardMgr();
	var kmm = kbm.__keyMapMgr;
	var shortcuts = [], numToData = {}, dataToNum = {};
	for (var id in this._inputs) {
		var row = this._inputs[id];
		var num = row["num"].dwtObj.getValue();
		var data = row["arg"].dwtObj.getData(ZmShortcutsPageTabViewCustom.DATA);
		if (!data && !num) {
			this._removeRow(id);
			continue;	// clean row is ok
		}
		
		// error checking
		var errorStr;
		if (!data) {
			errorStr = AjxMessageFormat.format(ZmMsg.missingShortcutOrg, [ZmOrganizer.TEXT[this._organizer], num]);
		} else if (!num) {
			errorStr = AjxMessageFormat.format(ZmMsg.missingShortcutNumber, [ZmOrganizer.TEXT[this._organizer], data]);
		} else if (!AjxUtil.isNumeric(num)) {
			errorStr = AjxMessageFormat.format(ZmMsg.nonnumericShortcut, [ZmOrganizer.TEXT[this._organizer], data]);
		}
		if (!errorStr && numToData[num]) {
			if (numToData[num] != data) {
				errorStr = AjxMessageFormat.format(ZmMsg.duplicateShortcutNumber, [num]);
			} else {
				continue;
			}
		}
		if (!errorStr && dataToNum[data] && (dataToNum[data] != num)) {
			errorStr = AjxMessageFormat.format(ZmMsg.duplicateShortcutOrg, [data]);
		}
		if (errorStr) {
			throw new AjxException(errorStr);
		}

		numToData[num] = data;
		dataToNum[data] = num;

		var tree = this._appCtxt.getTree(this._organizer);
		var organizer = (this._organizer == ZmOrganizer.FOLDER) ? tree.getByPath(data, true) :
																  tree.getByName(data);
		if (!organizer) { continue; }
		var mapName = ZmShortcutsPageTabViewCustom.ORG_MAP[this._organizer];
		var actions = ZmShortcutsPageTabViewCustom.ORG_ACTION[this._organizer];
		var len = actions.length;
		for (var i = 0; i < len; i++) {
			var left = [mapName, actions[i] + num, organizer.id].join(".");
			var seqs = kmm.getKeySequences(ZmKeyMap.MAP_NAME[mapName], actions[i]);
			for (var j = 0; j < seqs.length; j++) {
				var ks = seqs[j];
				var digits = num.split("");
				var right = ks.replace(/NNN/, digits.join(","));
				var sc = [left, right].join("=");
				shortcuts.push(sc);
			}
		}
	}
	DBG.println(AjxDebug.DBG1, "shortcuts for org type " + this._organizer + ": " + shortcuts.join("|"));

	return shortcuts;
};

ZmShortcutsPageTabViewCustom.prototype._createHtml =
function() {

	this._addButtonDivId = Dwt.getNextId();

	var tableId = Dwt.getNextId();
	var closeLinkId = Dwt.getNextId();
	var helpButtonId = Dwt.getNextId();

	var html = [];
	var i = 0;

	i = this._getInfoBoxHtml(html, i, closeLinkId);

	html[i++] = "<div style='margin:10px;border:1px solid #666666;'>";
	html[i++] = "<table width=100% cellspacing=0 cellpadding=0>";
	html[i++] = "<tr class='PanelHead'><td>";
	html[i++] = ZmShortcutsPageTabView.TAB_NAME[this._organizer];
	html[i++] = "</td>";
	html[i++] = "<td style='width:1%' id='";
	html[i++] = helpButtonId;
	html[i++] = "'></td>";
	html[i++] = "</tr><tr><td colspan=2 height=100% style='vertical-align:top;padding:10px 20px 10px 20px;'>";
	html[i++] = "<table class='shortcutTable' cellspacing=0 cellpadding=4 id='";
	html[i++] = tableId;
	html[i++] = "'><tbody>";

	i = this._getTableHeaderHtml(html, i);
	
	html[i++] = "</table>";
	html[i++] = "<div id='";
	html[i++] = this._addButtonDivId;
	html[i++] = "'></div>";
	html[i++] = "</tbody></table>";
	html[i++] = "</div>";
	
	this.getHtmlElement().innerHTML = html.join("");
	
	this._table = document.getElementById(tableId);

	// Handle the link to close the info box.
	var linkElement = document.getElementById(closeLinkId);
	var linkCallback = AjxCallback.simpleClosure(this._toggleInfoBoxHandler, this);
	Dwt.setHandler(linkElement, DwtEvent.ONCLICK, linkCallback);

	// Create the help button.
	var helpButton = new DwtButton(this, DwtLabel.ALIGN_RIGHT | DwtButton.ALWAYS_FLAT, "DwtToolbarButton");
	helpButton.setImage("Information");
	helpButton.reparentHtmlElement(helpButtonId);
	helpButton.addSelectionListener(new AjxListener(this, this._toggleInfoBoxHandler));
};

ZmShortcutsPageTabViewCustom.prototype._getInfoBoxHtml =
function(html, i, closeLinkId) {

	this._infoBoxId = Dwt.getNextId();

	html[i++] = "<div class='infoBox' id='";
	html[i++] = this._infoBoxId;
	html[i++] = "'>";
    html[i++] = "<table width=100% border='0' cellpadding='0' cellspacing='4'>";
    html[i++] = "<tr valign='top'>";
    html[i++] = "<td class='infoBoxImg'><div class='ImgInformation_32'></div></td>";
    html[i++] = "<td><div class='InfoTitle'><div class='infoTitleClose' id='";
	html[i++] = closeLinkId;
	html[i++] = "'>";
	html[i++] = ZmMsg.close;
	html[i++] = "</div>";
	html[i++] = AjxMessageFormat.format(ZmMsg.aboutShortcuts, [ZmShortcutsPageTabViewCustom.ORG_TEXT[this._organizer]]);
	html[i++] = "</div>";
	html[i++] = AjxMessageFormat.format(ZmMsg.assignShortcuts, [ZmShortcutsPageTabViewCustom.ORG_TEXT_PLURAL[this._organizer]]);
	html[i++] = "<div>";
	var key = ZmShortcutsPageTabViewList._formatKey(ZmShortcutsPageTabViewCustom.SAMPLE_KEY);
	var org = ZmOrganizer.TEXT[this._organizer];
	var exampleOrg = ["<i>", ZmShortcutsPageTabViewCustom.SAMPLE_ORG[this._organizer], "</i>"].join("");
	html[i++] = AjxMessageFormat.format(ZmMsg.exampleShortcutIntro, [key, org, exampleOrg]);
	html[i++] = "<ul>";
	var shortcuts = ZmShortcutsPageTabViewCustom.SAMPLE_SHORTCUTS[this._organizer];
	for (var j = 0; j < shortcuts.length; j++) {
		html[i++] = "<li>";
		var propName = shortcuts[j];
		var value = ZmKeys[propName];
		if (value) {
			var keySeqs = ZmKeys[propName].split(/\s*;\s*/);
			var ks = keySeqs[0];
			var parts = ks.split(",");
			var scText = AjxMessageFormat.format(ZmMsg.shortcutExample, [ZmShortcutsPageTabViewList._formatKeySequence(parts[0]),
												 ZmShortcutsPageTabViewList._formatKey(ZmShortcutsPageTabViewCustom.SAMPLE_KEY)]);
			var examplePropName = [propName, "example"].join(".");
			var exampleText = ZmKeys[examplePropName];
			if (exampleText) {
				var text = AjxMessageFormat.format(exampleText, [exampleOrg]);
				html[i++] = AjxMessageFormat.format(ZmMsg.shortcutTyping, [scText, text]);
			}
		}
		html[i++] = "</li>";
	}
	html[i++] = "</ul>";
	html[i++] = "</div>";
    html[i++] = "</td></tr></table>";
	html[i++] = "</div>";
	
	return i;
};

ZmShortcutsPageTabViewCustom.prototype._toggleInfoBoxHandler =
function() {
	var infoBox = document.getElementById(this._infoBoxId);
	var visible = Dwt.getVisible(infoBox);
	Dwt.setVisible(infoBox, !visible);
};

ZmShortcutsPageTabViewCustom.prototype._getTableHeaderHtml =
function(html, i) {
	var gotArg = (html && html.length);
	html = html ? html : [];
	i = i ? i : 0;
	
	html[i++] = "<tr>";
	html[i++] = "<th width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL1_WIDTH;
	html[i++] = ">";
	html[i++] = ZmOrganizer.TEXT[this._organizer];
	html[i++] = "</th>";
	html[i++] = "<th width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL2_WIDTH;
	html[i++] = "><nobr>";
	html[i++] = ZmMsg.shortcut;
	html[i++] = "</nobr></th>";
	html[i++] = "<th width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL3_WIDTH;
	html[i++] = ">&nbsp;</th>";
	html[i++] = "</tr>";
	
	return gotArg ? i : html.join("");
};

ZmShortcutsPageTabViewCustom.prototype._addButton =
function(parentId, text, width, listener) {
	var button = new DwtButton(this);
	button.setSize(width, Dwt.DEFAULT);
	button.setText(text);
	button.addSelectionListener(listener);
	var element = button.getHtmlElement();
	element.parentNode.removeChild(element);
	var parent = document.getElementById(parentId);
	parent.appendChild(element);
	return button;
};

ZmShortcutsPageTabViewCustom.prototype._buttonListener =
function(ev) {
	var button = ev.item;
	var html = this._getRowHtml();
	var row = Dwt.parseHtmlFragment(html, true);
	this._table.tBodies[0].appendChild(row);
	this._addDwtObjects(row.id);
};

/*
* Displays a table of ID/organizer mappings.
*/
ZmShortcutsPageTabViewCustom.prototype._renderTable =
function() {
	var org = this._organizer;
	var shortcuts = this._setting ? this._setting.split('|') : [];
	var done = {};
	for (var i = 0; i < shortcuts.length; i++) {
		var sc = ZmShortcut.parse(shortcuts[i]);
		if (org != ZmShortcutsPageTabViewCustom.ACTION_ORG[sc.baseAction] || done[sc.num]) { continue; }
		var html = this._getRowHtml(sc);
		var row = Dwt.parseHtmlFragment(html, true);
		this._table.tBodies[0].appendChild(row);
		this._addDwtObjects(row.id);
		done[sc.num] = true;
	}
};

ZmShortcutsPageTabViewCustom.prototype._getRowHtml =
function(shortcut) {

	var org = this._organizer;	// org type
	var rowId = Dwt.getNextId();
	this._inputs[rowId] = {};

	var html = [];
	var i = 0;
	html[i++] = ["<tr id='", rowId, "'>"].join("");

	var button = new DwtButton(this, DwtLabel.ALIGN_CENTER);
	var bWidth = AjxEnv.isIE ? ZmShortcutsPageTabViewCustom.COL1_WIDTH - 5 : ZmShortcutsPageTabViewCustom.COL1_WIDTH;
	var bHeight = AjxEnv.isIE ? 24 : Dwt.DEFAULT;
	button.setSize(bWidth, bHeight);
	var organizer = null, value = "";
	if (shortcut) {
		organizer = this._appCtxt.getTree(org).getById(shortcut.arg);
		value = (org == ZmOrganizer.FOLDER) ? organizer.getPath(false, false, null, true, true) :
											  organizer.getName(false, null, true);
		button.setData(ZmShortcutsPageTabViewCustom.DATA, value);
	}
	button.setText(organizer ? value : ZmMsg.browse);
	var id = Dwt.getNextId();
	this._inputs[rowId]["arg"] = {id: id, dwtObj: button};
	button.addSelectionListener(this._browseLstnr);
	button.setScrollStyle(Dwt.CLIP);
	html[i++] = "<td width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL1_WIDTH;
	html[i++] = " id='";
	html[i++] = id;
	html[i++] = "'></td>";

	id = Dwt.getNextId();
	var value = shortcut ? shortcut.num : null;
	var input = new DwtInputField({parent: this, type: DwtInputField.STRING, initialValue: value});
	Dwt.setSize(input.getInputElement(), '100%', Dwt.DEFAULT);
	this._inputs[rowId]["num"] = {id: id, dwtObj: input};
	html[i++] = "<td width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL2_WIDTH;
	html[i++] = " id='";
	html[i++] = id;
	html[i++] = "'></td>"; 

	var removeId = ["_rem_", rowId].join("");
	this._inputs[rowId]["remove"] = {id: removeId};
	html[i++] = "<td width=";
	html[i++] = ZmShortcutsPageTabViewCustom.COL2_WIDTH;
	html[i++] = "><span id='";
	html[i++] = removeId;
	html[i++] = "' class='removeLink'>";
	html[i++] = ZmMsg.remove;
	html[i++] = "</span></td>";

	html[i++] = "</tr>";

	return html.join("");
};

/*
* Attaches input widgets to the DOM tree based on placeholder IDs.
*
* @param	[string]*	rowId	ID of a single row to add inputs to
*/
ZmShortcutsPageTabViewCustom.prototype._addDwtObjects =
function(rowId) {
	for (var id in this._inputs) {
		if (rowId && (id != rowId)) continue;
		var row = this._inputs[id];
		for (var f in row) {
			var field = row[f];
			if (field.id && field.dwtObj) {
				var el = field.dwtObj.getHtmlElement();
				if (el) {
					el.parentNode.removeChild(el);
					document.getElementById(field.id).appendChild(el);
					el._rowId = id;
				}
			} else if (f == "remove") {
				var el = document.getElementById(field.id);
				el._tabViewId = this._internalId;
				el.onclick = ZmShortcutsPageTabViewCustom._onClick;
			}
		}
	}
};

ZmShortcutsPageTabViewCustom.prototype._removeRow =
function(rowId) {
	var row = document.getElementById(rowId);
	this._table.deleteRow(row.rowIndex);
	delete this._inputs[rowId];
};

ZmShortcutsPageTabViewCustom.prototype._browseListener =
function(ev) {
	var dialog, treeIds;
	var button = ev.item;
	if (this._organizer == ZmOrganizer.TAG) {
		if (!this._tagPicker) {
			this._tagPicker = new ZmPickTagDialog(this._appCtxt.getShell(), this._appCtxt.getMsgDialog());
		}
		dialog = this._tagPicker;
	} else {
		dialog = this._appCtxt.getMoveToDialog();
		treeIds = [this._organizer];
	}
	dialog.reset();
	dialog.setTitle(ZmShortcutsPageTabViewCustom.DIALOG_TEXT[this._organizer]);
	dialog.registerCallback(DwtDialog.OK_BUTTON, this._browseSelectionCallback, this, [ev.item, dialog]);
	dialog.popup(null, null, treeIds, true);
};

/*
* Changes the text of a button to the folder/tag that the user just chose.
*
* @param	[DwtButton]		the browse button
* @param	[ZmOrganizer]	the folder or tag that was chosen
*/
ZmShortcutsPageTabViewCustom.prototype._browseSelectionCallback =
function(button, dialog, organizer) {
	if (organizer) {
		button.setText(organizer.getName(false, null, true));
		var value = (organizer.type == ZmOrganizer.FOLDER) ? organizer.getPath(false, false, null, true, true) :
													 		 organizer.getName(false, null, true);
		button.setData(ZmShortcutsPageTabViewCustom.DATA, value);
	}
	dialog.popdown();
};

ZmShortcutsPageTabViewCustom._onClick =
function(ev) {
	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	var id = element ? element.id : null;

	// if clicked on remove attachment link
	if (id && id.indexOf("_rem_") == 0) {
		var tv = AjxCore.objectWithId(element._tabViewId);
		var rowId = id.substr(5);
		tv._removeRow(rowId);
		return false; // disables following of link
	} else {
		return true;
	}
};
