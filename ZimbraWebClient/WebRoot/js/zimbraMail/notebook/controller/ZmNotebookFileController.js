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
* @param appCtxt		app context
* @param container		containing shell
* @param app			containing app
*/
ZmNotebookFileController = function(appCtxt, container, app) {
	ZmNotebookController.call(this, appCtxt, container, app);

	this._dragSrc = new DwtDragSource(Dwt.DND_DROP_MOVE);
	this._dragSrc.addDragListener(new AjxListener(this, this._dragListener));

	this._listeners[ZmOperation.UNDELETE] = new AjxListener(this, this._undeleteListener);
}

ZmNotebookFileController.prototype = new ZmNotebookController;
ZmNotebookFileController.prototype.constructor = ZmNotebookFileController;

ZmNotebookFileController.prototype.toString = function() {
	return "ZmNotebookFileController";
};

// Public methods

ZmNotebookFileController.prototype.show =
function(searchResults, fromUserSearch) {
	ZmListController.prototype.show.call(this, searchResults);

	this._fromSearch = fromUserSearch;
	this._currentView = ZmController.NOTEBOOK_FILE_VIEW;
	this._setup(this._currentView);

	this._list = searchResults.getResults(ZmItem.MIXED);
	if (this._activeSearch) {
		if (this._list)
			this._list.setHasMore(this._activeSearch.getAttribute("more"));

		var newOffset = parseInt(this._activeSearch.getAttribute("offset"));
		if (this._listView[this._currentView])
			this._listView[this._currentView].setOffset(newOffset);
	}

	var elements = new Object();
	elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar[this._currentView];
	elements[ZmAppViewMgr.C_APP_CONTENT] = this._listView[this._currentView];
	this._setView(this._currentView, elements, true);
	this._resetNavToolBarButtons(this._currentView);

	// always set the selection to the first item in the list
	var list = this._listView[this._currentView].getList();
	if (list && list.size() > 0) {
		this._listView[this._currentView].setSelection(list.get(0));
	}
};

// Resets the available options on a toolbar or action menu.
ZmNotebookFileController.prototype._resetOperations =
function(parent, num) {
	ZmListController.prototype._resetOperations.call(this, parent, num);

	var toolbar = this._toolbar[this._currentView];
	var buttonIds = [ ZmOperation.SEND, ZmOperation.DETACH ];
	toolbar.enable(buttonIds, true);
};


// Private and protected methods

ZmNotebookFileController.prototype._getToolBarOps =
function() {
	var list = [ZmOperation.NEW_MENU, ZmOperation.EDIT, ZmOperation.SEP];

	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		list.push(ZmOperation.TAG_MENU, ZmOperation.SEP);
	}

	list.push(ZmOperation.DELETE,
				ZmOperation.FILLER,
				ZmOperation.SEND_PAGE,
				ZmOperation.SEP,
				ZmOperation.DETACH);
	return list;
};

ZmNotebookFileController.prototype._initializeToolBar =
function(view) {
	if (this._toolbar[view]) return;

	ZmNotebookController.prototype._initializeToolBar.call(this, view);
	this._toolbar[view].addFiller();

	var tb = new ZmNavToolBar(this._toolbar[view], DwtControl.STATIC_STYLE, null, ZmNavToolBar.SINGLE_ARROWS, true);
	this._setNavToolBar(tb, view);

	this._setNewButtonProps(view, ZmMsg.compose, "NewPage", "NewPageDis", ZmOperation.NEW_PAGE);

	var toolbar = this._toolbar[view];
	var button = toolbar.getButton(ZmOperation.EDIT);
	button.setEnabled(false);
};

ZmNotebookFileController.prototype._initializeActionMenu =
function() {
	ZmListController.prototype._initializeActionMenu.call(this);

	// based on current search, show/hide undelete menu option
	var showUndelete = false;
	var folderId = this._getSearchFolderId();
	if (folderId) {
		var folder = this._appCtxt.getById(folderId);
		showUndelete = folder && folder.isInTrash();
	}
	var actionMenu = this._actionMenu;
	var mi = actionMenu.getMenuItem(ZmOperation.UNDELETE);
	mi.setVisible(showUndelete);
};

ZmNotebookFileController.prototype._getActionMenuOps =
function() {
	var list = this._standardActionMenuOps();
	list.push(ZmOperation.UNDELETE);
	return list;
};

ZmNotebookFileController.prototype._getViewType =
function() {
	return ZmController.NOTEBOOK_FILE_VIEW;
};

ZmNotebookFileController.prototype._getItemType =
function() {
	return ZmItem.PAGE;
};

ZmNotebookFileController.prototype._createNewView =
function(viewType) {
	var parent = this._container;
	var className;
	var posStyle = Dwt.ABSOLUTE_STYLE;
	var mode; // ???
	var controller = this;
	var dropTgt = this._dropTgt;
	var result = new ZmFileListView(parent, className, posStyle, mode, controller, dropTgt);
	result.setDragSource(this._dragSrc);
	return result;
};

ZmNotebookFileController.prototype._getTagMenuMsg =
function(num) {
	return (num == 1) ? ZmMsg.tagItem : ZmMsg.tagItems;
};

ZmNotebookFileController.prototype._getMoveDialogTitle =
function(num) {
	return (num == 1) ? ZmMsg.moveItem : ZmMsg.moveItems;
};

ZmNotebookFileController.prototype._setViewContents =
function(view) {
	this._listView[view].set(this._list);
};


// List listeners

ZmNotebookFileController.prototype._editListener =
function(event) {
	var pageEditController = this._app.getPageEditController();
	var page = this._listView[this._currentView].getSelection()[0];
	pageEditController.show(page);
};
ZmNotebookFileController.prototype._resetOperations =
function(toolbarOrActionMenu, num) {
	if (!toolbarOrActionMenu) { return; }

	ZmNotebookController.prototype._resetOperations.call(this, toolbarOrActionMenu, num);

	var selection = this._listView[this._currentView].getSelection();

	var buttons = [ZmOperation.TAG_MENU, ZmOperation.DELETE];
	toolbarOrActionMenu.enable(buttons, (selection.length > 0));

	var enabled = selection.length == 1 && selection[0].type == ZmItem.PAGE;
	toolbarOrActionMenu.enable([ZmOperation.EDIT], enabled);
};

// Double click displays an item.
ZmNotebookFileController.prototype._listSelectionListener =
function(ev) {
	ZmListController.prototype._listSelectionListener.call(this, ev);
	if (ev.detail == DwtListView.ITEM_DBL_CLICKED) {
		this._doSelectDblClicked(ev.item);
	}
};

ZmNotebookFileController.prototype._doSelectDblClicked =
function(item, fromSearch) {
	item.type = (item instanceof ZmPage) ? ZmItem.PAGE : ZmItem.DOCUMENT;

	if (item.type == ZmItem.PAGE) {
		var controller = this._app.getNotebookController();
		controller.show(item, true, this._fromSearch || fromSearch);
	} else if (item.type == ZmItem.DOCUMENT) {
		var url = item.getRestUrl();
		window.open(url, "_new", "");											// TODO: popup window w/ REST URL
	}
};

ZmNotebookFileController.prototype._listActionListener =
function(ev) {
	ZmListController.prototype._listActionListener.call(this, ev);

	var actionMenu = this.getActionMenu();
	actionMenu.popup(0, ev.docX, ev.docY);
	if (ev.ersatz) {
		// menu popped up via keyboard nav
		actionMenu.setSelectedItem(0);
	}
};

ZmNotebookFileController.prototype._undeleteListener =
function(ev) {
	/* TODO
	var items = this._listView[this._currentView].getSelection();

	// figure out the default for this item should be moved to
	var folder = null;
	if (items[0] instanceof ZmContact) {
		folder = new ZmFolder({id: ZmOrganizer.ID_ADDRBOOK});
	} else if (items[0] instanceof ZmAppt) {
		folder = new ZmFolder({id: ZmOrganizer.ID_CALENDAR});
	} else {
		var folderId = items[0].isDraft ? ZmFolder.ID_DRAFTS : ZmFolder.ID_INBOX;
		folder = this._appCtxt.getById(folderId);
	}

	if (folder)
		this._doMove(items, folder);
	*/
};
