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

function ZmCalendarTreeController(appCtxt, type, dropTgt) {
	
	type = type ? type : ZmOrganizer.CALENDAR;
	dropTgt = dropTgt ? dropTgt : new DwtDropTarget(ZmAppt);
	
	ZmTreeController.call(this, appCtxt, type, dropTgt);

	this._listeners[ZmOperation.NEW_CALENDAR] = new AjxListener(this, this._newCalListener);
	this._listeners[ZmOperation.CHECK_ALL] = new AjxListener(this, this._checkAllListener);
	this._listeners[ZmOperation.CLEAR_ALL] = new AjxListener(this, this._clearAllListener);

	this._listeners[ZmOperation.SHARE_CALENDAR] = new AjxListener(this, this._shareCalListener);
	this._listeners[ZmOperation.MOUNT_CALENDAR] = new AjxListener(this, this._mountCalListener);
	this._listeners[ZmOperation.EDIT_PROPS] = new AjxListener(this, this._editPropsListener);
	this._listeners[ZmOperation.DELETE] = new AjxListener(this, this._deleteListener);

	this._eventMgrs = {};
};

ZmCalendarTreeController.prototype = new ZmTreeController;
ZmCalendarTreeController.prototype.constructor = ZmCalendarTreeController;

ZmCalendarTreeController.prototype.toString = function() {
	return "ZmCalendarTreeController";
};

// Constants

ZmCalendarTreeController.COLOR_CLASSNAMES = [
	// NOTE: We use Gray instead of GrayBg so that it doesn't blend into background
	"OrangeBg", "BlueBg", "CyanBg", "GreenBg", "PurpleBg", "RedBg", "YellowBg", "PinkBg", "Gray"
];

// Public methods

ZmCalendarTreeController.prototype.getCheckedCalendars =
function(overviewId) {
	var calendars = [];
	var items = this._getItems(overviewId);
	for (var i = 0; i < items.length; i++) {
		var item = items[i];
		if (item.getChecked()) {
			var calendar = item.getData(Dwt.KEY_OBJECT);
			calendars.push(calendar);
		}
	}

	return calendars;
};

ZmCalendarTreeController.prototype.addSelectionListener =
function(overviewId, listener) {
	// Each overview gets its own event manager
	if (!this._eventMgrs[overviewId]) {
		this._eventMgrs[overviewId] = new AjxEventMgr;
		// Each event manager has its own selection event to avoid
		// multi-threaded collisions
		this._eventMgrs[overviewId]._selEv = new DwtSelectionEvent(true);
	}
	this._eventMgrs[overviewId].addListener(DwtEvent.SELECTION, listener);
};

ZmCalendarTreeController.prototype.removeSelectionListener =
function(overviewId, listener) {
	if (this._eventMgrs[overviewId]) {
		this._eventMgrs[overviewId].removeListener(DwtEvent.SELECTION, listener);
	}
};

// Protected methods

ZmCalendarTreeController.prototype.show = 
function(overviewId, showUnread, omit, forceCreate) {
	var firstTime = (!this._treeView[overviewId] || forceCreate);

	ZmTreeController.prototype.show.call(this, overviewId, showUnread, omit, forceCreate);
	
	if (firstTime) {
		var treeView = this.getTreeView(overviewId);
		var root = treeView.getTreeItemById(ZmOrganizer.ID_ROOT);
		root.showCheckBox(false);
		var items = root.getItems();
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			var object = item.getData(Dwt.KEY_OBJECT);
			this._setTreeItemColor(item, object.color);
			if (object.id == ZmCalendar.ID_CALENDAR) {
				item.setChecked(true);
			}
		}
	}
};

ZmCalendarTreeController.prototype.resetOperations = 
function(actionMenu, type, id) {
	if (actionMenu) {
		var overviewController = this._appCtxt.getOverviewController();
		var treeData = overviewController.getTreeData(ZmOrganizer.CALENDAR);
		var calendar = treeData.getById(id);
		actionMenu.enable(ZmOperation.SHARE_CALENDAR, !calendar.link);
		actionMenu.enable(ZmOperation.DELETE, id != ZmOrganizer.ID_CALENDAR);
		actionMenu.enable(ZmOperation.SYNC, calendar.isFeed());
		if (id == ZmOrganizer.ID_ROOT) {
			var items = this._getItems(this._actionedOverviewId);
			var foundChecked = false;
			var foundUnchecked = false;
			for (var i = 0; i < items.length; i++) {
				items[i].getChecked() ? foundChecked = true : foundUnchecked = true;
			}
			actionMenu.enable(ZmOperation.CHECK_ALL, foundUnchecked);
			actionMenu.enable(ZmOperation.CLEAR_ALL, foundChecked);
		}
	}
};

// Returns a list of desired header action menu operations
ZmCalendarTreeController.prototype._getHeaderActionMenuOps =
function() {
	return [ZmOperation.NEW_CALENDAR, ZmOperation.CHECK_ALL, ZmOperation.CLEAR_ALL];
};

// Returns a list of desired action menu operations
ZmCalendarTreeController.prototype._getActionMenuOps =
function() {
	return [ZmOperation.SHARE_CALENDAR, ZmOperation.DELETE, ZmOperation.EDIT_PROPS, ZmOperation.SYNC];
};

ZmCalendarTreeController.prototype.getTreeStyle =
function() {
	return DwtTree.CHECKEDITEM_STYLE;
};

// Method that is run when a tree item is left-clicked
ZmCalendarTreeController.prototype._itemClicked =
function() {
	// TODO
};

// Handles a drop event
ZmCalendarTreeController.prototype._dropListener =
function() {
	// TODO
};

// Listener callbacks

ZmCalendarTreeController.prototype._changeListener =
function(ev, treeView) {
	ZmTreeController.prototype._changeListener.call(this, ev, treeView);

	if (ev.type != this.type) return;
	
	var organizers = ev.getDetail("organizers");
	if (!organizers && ev.source)
		organizers = [ev.source];

	for (var i = 0; i < organizers.length; i++) {
		var organizer = organizers[i];
		var id = organizer.id;
		var node = treeView.getTreeItemById(id);
		if (!node) continue;

		var fields = ev.getDetail("fields");
		// NOTE: ZmTreeController#_changeListener re-inserts the node if the 
		//		 name changes so we need to reset the color in that case, too.
		if (ev.event == ZmEvent.E_CREATE || 
			(ev.event == ZmEvent.E_MODIFY && fields && (fields[ZmOrganizer.F_COLOR] || fields[ZmOrganizer.F_NAME]))) {
			var object = node.getData(Dwt.KEY_OBJECT);
			this._setTreeItemColor(node, object.color);
		}
	}
};

ZmCalendarTreeController.prototype._treeViewListener =
function(ev) {
	// handle item(s) clicked
	if (ev.detail == DwtTree.ITEM_CHECKED) { 
		var overviewId = ev.item.getData(ZmTreeView.KEY_ID);
		var calendar = ev.item.getData(Dwt.KEY_OBJECT);

		// notify listeners of selection
		if (this._eventMgrs[overviewId]) {
			this._eventMgrs[overviewId].notifyListeners(DwtEvent.SELECTION, ev);
		}
		return;
	}

	// default processing
	ZmTreeController.prototype._treeViewListener.call(this, ev);
};

ZmCalendarTreeController.prototype._newCalListener =
function(ev) {
	var overviewController = this._appCtxt.getOverviewController();
	var treeData = overviewController.getTreeData(ZmOrganizer.CALENDAR);
	var folder = treeData.root;

	var newCalDialog = this._appCtxt.getNewCalendarDialog();
	newCalDialog.setParentFolder(folder);
	newCalDialog.popup();
};

ZmCalendarTreeController.prototype._checkAllListener =
function(ev) {
	this._setAllChecked(ev, true);
};

ZmCalendarTreeController.prototype._clearAllListener =
function(ev) {
	this._setAllChecked(ev, false);
};

ZmCalendarTreeController.prototype._shareCalListener =
function(ev) {
	this._pendingActionData = this._getActionedOrganizer(ev);
	
	var calendar = this._pendingActionData;
	var share = null;
	
	var sharePropsDialog = this._appCtxt.getSharePropsDialog();
	sharePropsDialog.setDialogType(ZmSharePropsDialog.NEW);
	sharePropsDialog.setFolder(calendar);
	sharePropsDialog.setShareInfo(share);
	sharePropsDialog.popup();
};

ZmCalendarTreeController.prototype._mountCalListener =
function(ev) {
	alert("TODO: mount calendar dialog");
};

ZmCalendarTreeController.prototype._editPropsListener =
function(ev) {
	this._pendingActionData = this._getActionedOrganizer(ev);

	var folderPropsDialog = this._appCtxt.getFolderPropsDialog();
	var folder = this._pendingActionData;
	folderPropsDialog.setFolder(folder);
	folderPropsDialog.popup();
};

ZmCalendarTreeController.prototype._notifyListeners =
function(overviewId, type, items, detail, srcEv, destEv) {
	if (this._eventMgrs[overviewId] && this._eventMgrs[overviewId].isListenerRegistered(type)) {
		if (srcEv) DwtUiEvent.copy(destEv, srcEv);
		destEv.items = items;
		if (items.length == 1) destEv.item = items[0];
		destEv.detail = detail;
		this._eventMgrs[overviewId].notifyListeners(type, destEv);
	}
};

ZmCalendarTreeController.prototype._getItems =
function(overviewId) {
	var treeView = this.getTreeView(overviewId);
	if (treeView) {
		var root = treeView.getTreeItemById(ZmOrganizer.ID_ROOT);
		if (root)
			return root.getItems();
	}
	return [];
};

ZmCalendarTreeController.prototype._setTreeItemColor =
function(item, color) {
	var element = item.getHtmlElement();
	element.className = ZmCalendarTreeController.COLOR_CLASSNAMES[color];
};

ZmCalendarTreeController.prototype._setAllChecked =
function(ev, checked) {
	var overviewId = this._actionedOverviewId;
	var items = this._getItems(overviewId);
	var checkedItems = [];
	for (var i = 0;  i < items.length; i++) {
		var item = items[i];
		if (item.getChecked() != checked) {
			item.setChecked(checked);
			checkedItems.push(item);
		}
	}

	// notify listeners of selection
	if (checkedItems.length && this._eventMgrs[overviewId]) {
		this._notifyListeners(overviewId, DwtEvent.SELECTION, checkedItems, DwtTree.ITEM_CHECKED,
							  ev, this._eventMgrs[overviewId]._selEv);
	}
};
