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
* Creates a new, empty list controller. It must be initialized before it can be used.
* @constructor
* @class
* This class is a base class for any controller that manages lists of items (eg mail or
* contacts). It consolidates handling of list functionality (eg selection) and of common
* operations such as tagging and deletion. Operations may be accessed by the user through
* either the toolbar or an action menu. The public method show() gets everything going,
* and then the controller just handles events.
*
* <p>Support is also present for handling multiple views (eg contacts).</p>
*
* <p>Controllers for single items may extend this class, since the functionality needed is 
* virtually the same. An item can be thought of as the degenerate form of a list.</p>
*
* @author Conrad Damon
* @param appCtxt	app context
* @param container	containing shell
* @param app		containing app
*/
function ZmListController(appCtxt, container, app) {

	if (arguments.length == 0) return;
	ZmController.call(this, appCtxt, container, app);

	this._toolbar = {};		// ZmButtonToolbar (one per view)
	this._navToolBar = {};	// ZmNavToolBar (one per view)
	this._listView = {};	// ZmListView (one per view)
	this._tabGroups = {};	// DwtTabGroup (one per view)
	this._list = null;				// ZmList (the data)
	this._actionMenu = null; 		// ZmActionMenu
	this._actionEv = null;
	
	this._tagList = this._appCtxt.getTree(ZmOrganizer.TAG);
	if (this._tagList) {
		this._tagChangeLstnr = new AjxListener(this, this._tagChangeListener);
		this._tagList.addChangeListener(this._tagChangeLstnr);
	}
	this._creatingTag = false;
	this._activeSearch = null;

	// create a listener for each operation
	this._listeners = {};
	this._listeners[ZmOperation.NEW_MENU] = new AjxListener(this, this._newListener);
	this._listeners[ZmOperation.TAG_MENU] = new AjxListener(this, this._tagButtonListener);
	this._listeners[ZmOperation.TAG] = new AjxListener(this, this._tagListener);
	this._listeners[ZmOperation.PRINT] = new AjxListener(this, this._printListener);
	this._listeners[ZmOperation.DELETE]  = new AjxListener(this, this._deleteListener);
	this._listeners[ZmOperation.CLOSE] = new AjxListener(this, this._backListener);
	this._listeners[ZmOperation.MOVE]  = new AjxListener(this, this._moveListener);
	this._listeners[ZmOperation.SEARCH] = new AjxListener(this, this._participantSearchListener);
	this._listeners[ZmOperation.BROWSE] = new AjxListener(this, this._participantBrowseListener);
	this._listeners[ZmOperation.NEW_MESSAGE] = new AjxListener(this, this._participantComposeListener);
	this._listeners[ZmOperation.IM] = new AjxListener(this, this._participantImListener);
	this._listeners[ZmOperation.CONTACT] = new AjxListener(this, this._participantContactListener);
	this._listeners[ZmOperation.VIEW] = new AjxListener(this, this._viewButtonListener);

	this._popdownListener = new AjxListener(this, this._popdownActionListener);

	this._dropTgt = new DwtDropTarget(ZmTag);
	this._dropTgt.markAsMultiple();
	this._dropTgt.addDropListener(new AjxListener(this, this._dropListener));
};

ZmListController.prototype = new ZmController;
ZmListController.prototype.constructor = ZmListController;

// convert key mapping to operation
ZmListController.ACTION_CODE_TO_OP = {};
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_APPT]			= ZmOperation.NEW_APPT;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_CALENDAR]		= ZmOperation.NEW_CALENDAR;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_CONTACT]		= ZmOperation.NEW_CONTACT;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_FOLDER]			= ZmOperation.NEW_FOLDER;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_MESSAGE]		= ZmOperation.NEW_MESSAGE;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_MESSAGE_WIN]	= ZmOperation.NEW_MESSAGE;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_PAGE]			= ZmOperation.NEW_PAGE;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_TAG]			= ZmOperation.NEW_TAG;
ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_NOTEBOOK]		= ZmOperation.NEW_NOTEBOOK;

// abstract public methods

// public methods

ZmListController.prototype.toString = 
function() {
	return "ZmListController";
};

/**
* Performs some setup for displaying the given search results in a list view. Subclasses will need
* to do the actual display work, typically by calling the list view's set() method.
*
* @param searchResults		a ZmSearchResult
* @param view				view type to use
*/
ZmListController.prototype.show	=
function(searchResults, view) {
	this._currentView = view ? view : this._defaultView();
	this._activeSearch = searchResults;
	// save current search for use by replenishment
	if (searchResults)
		this._currentSearch = searchResults.search;
	this.currentPage = 1;
	this.maxPage = 1;
	this.pageIsDirty = {};
};

ZmListController.prototype.getSearchString = 
function() {
	return this._currentSearch.query;
};

ZmListController.prototype.getCurrentView =
function() {
	return this._listView[this._currentView];
};

ZmListController.prototype.getList =
function() {
	return this._list;
};

ZmListController.prototype.setList =
function(newList) {
	if (newList != this._list && (newList instanceof ZmList)) {
		// dtor current list if necessary
		if (this._list)
			this._list.clear();
		this._list = newList;
	}
};

ZmListController.prototype.handleKeyAction =
function(actionCode) {
	DBG.println(AjxDebug.DBG3, "ZmListController.handleKeyAction");
	var listView = this._listView[this._currentView];

	// check for action code with argument, eg MoveToFolder3
	var origActionCode = actionCode;
	var shortcut = ZmShortcut.parseAction(this._appCtxt, "Global", actionCode);
	if (shortcut) {
		actionCode = shortcut.baseAction;
	}

	switch (actionCode) {

		case DwtKeyMap.DBLCLICK:
			return listView.handleKeyAction(actionCode);

		case ZmKeyMap.DEL:
			this._doDelete(listView.getSelection());
			break;

		case ZmKeyMap.NEXT_PAGE:
			var ntb = this._navToolBar[this._currentView];
			var button = ntb ? ntb.getButton(ZmOperation.PAGE_FORWARD) : null;
			if (button && button.getEnabled()) {
				this._paginate(this._currentView, true);
			}
			break;

		case ZmKeyMap.PREV_PAGE:
			var ntb = this._navToolBar[this._currentView];
			var button = ntb ? ntb.getButton(ZmOperation.PAGE_BACK) : null;
			if (button && button.getEnabled()) {
				this._paginate(this._currentView, false);
			}
			break;

		case ZmKeyMap.NEW: {
			//Find the current app
			switch (this._appCtxt.getAppController().getActiveApp()) {
				case ZmZimbraMail.MAIL_APP:
				case ZmZimbraMail.MIXED_APP:
					this._newListener(null, ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_MESSAGE]);
					break;
				case ZmZimbraMail.CALENDAR_APP:
					this._newListener(null, ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_APPT]);
					break;
				case ZmZimbraMail.CONTACTS_APP:
					this._newListener(null, ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_CONTACT]);
					break;
				case ZmZimbraMail.NOTEBOOK_APP:
					this._newListener(null, ZmListController.ACTION_CODE_TO_OP[ZmKeyMap.NEW_PAGE]);
					break;
			}
			break;
		}
	
		case ZmKeyMap.NEW_CALENDAR:
		case ZmKeyMap.NEW_CONTACT:
		case ZmKeyMap.NEW_FOLDER:
		case ZmKeyMap.NEW_MESSAGE:
		case ZmKeyMap.NEW_APPT:
		case ZmKeyMap.NEW_TAG:
		case ZmKeyMap.NEW_PAGE:
		case ZmKeyMap.NEW_NOTEBOOK:
			this._newListener(null, ZmListController.ACTION_CODE_TO_OP[actionCode]);
			break;

		case ZmKeyMap.NEW_MESSAGE_WIN:
			this._newListener(null, ZmListController.ACTION_CODE_TO_OP[actionCode], true);
			break;

		case ZmKeyMap.PRINT:
		case ZmKeyMap.PRINT_ALL:
			if (this._appCtxt.get(ZmSetting.PRINT_ENABLED)) {
				this._printListener();
			}
			break;
		
		case ZmKeyMap.UNTAG:
			if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
				var items = listView.getSelection();
				if (items && items.length) {
					this._doRemoveAllTags(items);
				}
			}
			break;
			
		case ZmKeyMap.TAG:
			var items = listView.getSelection();
			if (items && items.length) {
				var tag = this._appCtxt.getTree(ZmOrganizer.TAG).getById(shortcut.arg);
				this._doTag(items, tag, true);
			}
			break;
			
		case ZmKeyMap.GOTO_TAG:
			var tag = this._appCtxt.getTree(ZmOrganizer.TAG).getById(shortcut.arg);
			this._appCtxt.getSearchController().search({query: 'tag:"' + tag.name + '"'});
			break;

		case ZmKeyMap.SAVED_SEARCH:
			var searchFolder = this._appCtxt.getTree(ZmOrganizer.SEARCH).getById(shortcut.arg);
			this._appCtxt.getSearchController().redoSearch(searchFolder.search);
			break;
			
		default:
			return ZmController.prototype.handleKeyAction.call(this, origActionCode);
	}
	return true;
};

// abstract protected methods

// Creates the view element
ZmListController.prototype._createNewView	 	= function() {};

// Returns the view ID
ZmListController.prototype._getViewType 		= function() {};

// Populates the view with data
ZmListController.prototype._setViewContents		= function(view) {};

// Returns text for the tag operation
ZmListController.prototype._getTagMenuMsg 		= function(num) {};

// Returns text for the move dialog
ZmListController.prototype._getMoveDialogTitle	= function(num) {};

// Returns a list of desired toolbar operations
ZmListController.prototype._getToolBarOps 		= function() {};

// Returns a list of desired action menu operations
ZmListController.prototype._getActionMenuOps 	= function() {};

// Attempts to process a nav toolbar up/down button click
ZmListController.prototype._paginateDouble 		= function(bDoubleForward) {};

// Returns the type of item in the underlying list
ZmListController.prototype._getItemType			= function() {};

// private and protected methods

// Creates basic elements and sets the toolbar and action menu
ZmListController.prototype._setup =
function(view) {
	this._initialize(view);
	//DBG.timePt("this._initialize");
	this._resetOperations(this._toolbar[view], 0);
	//DBG.timePt("this._resetOperation(toolbar)");
};

// Creates the basic elements: toolbar, list view, and action menu
ZmListController.prototype._initialize =
function(view) {
	this._initializeToolBar(view);
	//DBG.timePt("_initializeToolBar");
	this._initializeListView(view);
	//DBG.timePt("_initializeListView");
	this._initializeTabGroup(view);
	//DBG.timePt("_initializeTabGroup");
};

// Below are functions that return various groups of operations, for cafeteria-style
// operation selection.

ZmListController.prototype._standardToolBarOps =
function() {
	var list = [];
	list.push(ZmOperation.NEW_MENU);
	list.push(ZmOperation.CHECK_MAIL);
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED))
		list.push(ZmOperation.TAG_MENU);
	list.push(ZmOperation.SEP);
	list.push(ZmOperation.DELETE);
	list.push(ZmOperation.MOVE);
	if (this._appCtxt.get(ZmSetting.PRINT_ENABLED))
		list.push(ZmOperation.PRINT);
	return list;
};

ZmListController.prototype._standardActionMenuOps =
function() {
	var list = [];
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED))
		list.push(ZmOperation.TAG_MENU);
	list.push(ZmOperation.DELETE);
	list.push(ZmOperation.MOVE);
	if (this._appCtxt.get(ZmSetting.PRINT_ENABLED))
		list.push(ZmOperation.PRINT);
	return list;
};

ZmListController.prototype._contactOps =
function() {
	var list = [];
	if (this._appCtxt.get(ZmSetting.SEARCH_ENABLED))
		list.push(ZmOperation.SEARCH);
	if (this._appCtxt.get(ZmSetting.BROWSE_ENABLED))
		list.push(ZmOperation.BROWSE);
	list.push(ZmOperation.NEW_MESSAGE);
	/*
	if (this._appCtxt.get(ZmSetting.IM_ENABLED))
		list.push(ZmOperation.IM);
    */
	if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED))
		list.push(ZmOperation.CONTACT);
	return list;
};

// toolbar: buttons and listeners
ZmListController.prototype._initializeToolBar =
function(view) {
	if (this._toolbar[view]) return;

	var buttons = this._getToolBarOps();
	if (!buttons) return;
	this._toolbar[view] = new ZmButtonToolBar(this._container, buttons, null, Dwt.ABSOLUTE_STYLE, "ZmAppToolBar");
	// remove text for Print, Delete, and Move buttons
	var list = [ZmOperation.PRINT, ZmOperation.DELETE, ZmOperation.MOVE];
	for (var i = 0; i < list.length; i++) {
		var button = this._toolbar[view].getButton(list[i]);
		if (button)
			button.setText(null);
	}
	for (var i = 0; i < buttons.length; i++)
		if (buttons[i] > 0 && this._listeners[buttons[i]])
			this._toolbar[view].addSelectionListener(buttons[i], this._listeners[buttons[i]]);

	var toolbar = this._toolbar[view];
	var button = toolbar.getButton(ZmOperation.NEW_MENU);
	if (button) {
       	var listener = new AjxListener(toolbar, ZmListController._newDropDownListener);
       	button.addDropDownSelectionListener(listener);
       	toolbar._ZmListController_this = this;
       	toolbar._ZmListController_newDropDownListener = listener;
   	}

	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		var tagMenuButton = this._toolbar[view].getButton(ZmOperation.TAG_MENU);
		if (tagMenuButton) {
			tagMenuButton.noMenuBar = true;
			this._setupTagMenu(this._toolbar[view]);
		}
	}
};

// list view and its listeners
ZmListController.prototype._initializeListView =
function(view) {
	if (this._listView[view]) return;

	this._listView[view] = this._createNewView(view);
	this._listView[view].addSelectionListener(new AjxListener(this, this._listSelectionListener));
	this._listView[view].addActionListener(new AjxListener(this, this._listActionListener));
};

// action menu: menu items and listeners
ZmListController.prototype._initializeActionMenu =
function() {
	var menuItems = this._getActionMenuOps();
	if (!menuItems) return;
	this._actionMenu = new ZmActionMenu(this._shell, menuItems);
	for (var i = 0; i < menuItems.length; i++)
		if (menuItems[i] > 0)
			this._actionMenu.addSelectionListener(menuItems[i], this._listeners[menuItems[i]]);
	this._actionMenu.addPopdownListener(this._popdownListener);
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		this._setupTagMenu(this._actionMenu);
	}
};

ZmListController.prototype._initializeTabGroup =
function(view) {
	if (this._tabGroups[view]) return;
	
	this._tabGroups[view] = this._createTabGroup();
	this._tabGroups[view].newParent(this._appCtxt.getRootTabGroup());
	this._tabGroups[view].addMember(this._toolbar[view]);
	this._tabGroups[view].addMember(this._listView[view]);
};

/**
* Creates the desired application view.
*
* @param view			view ID
* @param elements		array of view components
* @param isAppView		this view is a top-level app view
* @param clear			if true, clear the hidden stack of views
* @param pushOnly		don't reset the view's data, just swap the view in
* @param isTransient	this view doesn't go on the hidden stack
*/
ZmListController.prototype._setView =
function(view, elements, isAppView, clear, pushOnly, isTransient) {

	// create the view (if we haven't yet)
	if (!this._appViews[view]) {
		// view management callbacks
		var callbacks = {};

		callbacks[ZmAppViewMgr.CB_PRE_HIDE] =
			this._preHideCallback ? new AjxCallback(this, this._preHideCallback) : null;
		callbacks[ZmAppViewMgr.CB_POST_HIDE] =
			this._postHideCallback ? new AjxCallback(this, this._postHideCallback) : null;
		callbacks[ZmAppViewMgr.CB_PRE_SHOW] =
			this._preShowCallback ? new AjxCallback(this, this._preShowCallback) : null;
		callbacks[ZmAppViewMgr.CB_POST_SHOW] =
			this._postShowCallback ? new AjxCallback(this, this._postShowCallback) : null;

		this._app.createView(view, elements, callbacks, isAppView, isTransient);
		this._appViews[view] = 1;
	}

	// populate the view
	if (!pushOnly)
		this._setViewContents(view);

	// push the view
	 return (clear ? this._app.setView(view) : this._app.pushView(view));
};

// Returns the print view, creating it if necessary.
ZmListController.prototype._getPrintView =
function() {
	if (!this._printView)
		this._printView = new ZmPrintView(this._appCtxt);
	return this._printView;
};

// List listeners

// List selection event - handle flagging if a flag icon was clicked, otherwise reset
// the toolbar based on how many items are selected.
ZmListController.prototype._listSelectionListener =
function(ev) {
	if (ev.field == ZmListView.FIELD_PREFIX[ZmItem.F_FLAG]) {
		this._doFlag([ev.item]);
	} else {
		this._resetOperations(this._toolbar[this._currentView], this._listView[this._currentView].getSelectionCount());
	}
};

// List action event - set the dynamic tag menu, and enable operations in the action menu
// based on the number of selected items. Note that the menu is not actually popped up
// here; that's left up to the subclass, which should override this function.
ZmListController.prototype._listActionListener =
function(ev) {
	this._actionEv = ev;
	var actionMenu = this.getActionMenu();
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED))
		this._setTagMenu(actionMenu);
	this._resetOperations(actionMenu, this._listView[this._currentView].getSelectionCount());
};

ZmListController.prototype._popdownActionListener =
function() {
	if (!this._pendingActionData)
		this._listView[this._currentView].handleActionPopdown();
};

// Operation listeners

// Create some new thing, via a dialog. If just the button has been pressed (rather than
// a menu item), the action taken depends on the app.
ZmListController.prototype._newListener =
function(ev, id, newWin) {
	id = id ? id : ev.item.getData(ZmOperation.KEY_ID);
	if (!id || id == ZmOperation.NEW_MENU) {
		id = this._defaultNewId;
	}

	switch (id) {
		// new items
		case ZmOperation.NEW_MESSAGE: {
			var app = this._appCtxt.getApp(ZmZimbraMail.MAIL_APP);
			var controller = app.getComposeController();
			controller.doAction(ZmOperation.NEW_MESSAGE, newWin || this._inNewWindow(ev));
			break;
		}
		case ZmOperation.NEW_CONTACT:
		case ZmOperation.NEW_GROUP: {
			// bug fix #5373
			// - dont allow adding new contacts after searching GAL if contacts disabled
			if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
				var type = id == ZmOperation.NEW_GROUP ? ZmItem.GROUP : null;
				var contact = new ZmContact(this._appCtxt, null, null, type);
				var app = this._appCtxt.getApp(ZmZimbraMail.CONTACTS_APP);
				var controller = app.getContactController();
				controller.show(contact);
			} else if (ev) {
				ev.item.popup();
			}
			break;
		}
		case ZmOperation.NEW_APPT: {
			var app = this._appCtxt.getApp(ZmZimbraMail.CALENDAR_APP);
			var controller = app.getCalController();
			controller.newAppointment(null, null, null, new Date());
			break;
		}
		case ZmOperation.NEW_PAGE: {
			var overviewController = this._appCtxt.getOverviewController();
			var treeController = overviewController.getTreeController(ZmOrganizer.NOTEBOOK);
			var treeView = treeController.getTreeView(ZmZimbraMail._OVERVIEW_ID);

			var notebook = treeView ? treeView.getSelected() : null;
			var page = new ZmPage(this._appCtxt);
			page.folderId = notebook ? notebook.id : ZmNotebookItem.DEFAULT_FOLDER;

			var app = this._appCtxt.getApp(ZmZimbraMail.NOTEBOOK_APP);
			var controller = app.getPageEditController();
			controller.show(page);
			break;
		}
		// new organizers
		case ZmOperation.NEW_FOLDER: {
			var dialog = this._appCtxt.getNewFolderDialog();
			this._showDialog(dialog, this._newFolderCallback);
			break;
		}
		case ZmOperation.NEW_TAG: {
			var dialog = this._appCtxt.getNewTagDialog();
			this._showDialog(dialog, this._newTagCallback, null, null, false);
			break;
		}
		case ZmOperation.NEW_ADDRBOOK: {
			var dialog = this._appCtxt.getNewAddrBookDialog();
			this._showDialog(dialog, this._newAddrBookCallback);
			break;
		}
		case ZmOperation.NEW_CALENDAR: {
			var dialog = this._appCtxt.getNewCalendarDialog();
			this._showDialog(dialog, this._newCalendarCallback);
			break;
		}
		case ZmOperation.NEW_NOTEBOOK: {
			var dialog = this._appCtxt.getNewNotebookDialog();
			this._showDialog(dialog, this._newNotebookCallback);
			break;
		}
	}
};

// Tag button has been pressed. We don't tag anything (since no tag has been selected),
// we just show the dynamic tag menu.
ZmListController.prototype._tagButtonListener =
function(ev) {
	this._setTagMenu(this._toolbar[this._currentView]);
};

// Tag/untag items.
ZmListController.prototype._tagListener =
function(item) {
	if (this._app.getAppViewMgr().getCurrentViewId() == this._getViewType()) {
		var tagEvent = item.getData(ZmTagMenu.KEY_TAG_EVENT);
		var tagAdded = item.getData(ZmTagMenu.KEY_TAG_ADDED);
		var items = this._listView[this._currentView].getSelection();
		if (tagEvent == ZmEvent.E_TAGS && tagAdded) {
			this._doTag(items, item.getData(Dwt.KEY_OBJECT), true);
		} else if (tagEvent == ZmEvent.E_CREATE) {
			this._pendingActionData = this._listView[this._currentView].getSelection();
			var newTagDialog = this._appCtxt.getNewTagDialog();
			this._showDialog(newTagDialog, this._newTagCallback, null, null, true);
			newTagDialog.registerCallback(DwtDialog.CANCEL_BUTTON, this._clearDialog, this, newTagDialog);
		} else if (tagEvent == ZmEvent.E_TAGS && !tagAdded) {
			this._doTag(items, item.getData(Dwt.KEY_OBJECT), false);
		} else if (tagEvent == ZmEvent.E_REMOVE_ALL) {
			// bug fix #607
			this._doRemoveAllTags(items);
		}
	}
};

ZmListController.prototype._printListener =
function(ev) {
	var items = this._listView[this._currentView].getSelection();
	var item = (items instanceof Array) ? items[0] : items;
	this._getPrintView().render(item);
};

ZmListController.prototype._backListener =
function(ev) {
	this._app.popView();
};

// Delete one or more items.
ZmListController.prototype._deleteListener =
function(ev) {
	this._doDelete(this._listView[this._currentView].getSelection());
};

// Move button has been pressed, show the dialog.
ZmListController.prototype._moveListener =
function(ev) {
	this._pendingActionData = this._listView[this._currentView].getSelection();
	var moveToDialog = this._appCtxt.getMoveToDialog();
	this._showDialog(moveToDialog, this._moveCallback, this._pendingActionData);
	moveToDialog.registerCallback(DwtDialog.CANCEL_BUTTON, this._clearDialog, this, moveToDialog);
	moveToDialog.setTitle(this._getMoveDialogTitle(this._pendingActionData.length));
};

// Switch to selected view.
ZmListController.prototype._viewButtonListener =
function(ev) {
	if (ev.detail == DwtMenuItem.CHECKED || ev.detail == DwtMenuItem.UNCHECKED)	{
		this.switchView(ev.item.getData(ZmOperation.MENUITEM_ID));
	}
};

// Navbar listeners

ZmListController.prototype._navBarListener =
function(ev) {
	// skip listener for non-current views
	if (this._appCtxt.getAppViewMgr().getCurrentViewId() != this._getViewType())
		return;

	var op = ev.item.getData(ZmOperation.KEY_ID);

	if (op == ZmOperation.PAGE_BACK || op == ZmOperation.PAGE_FORWARD) {
		this._paginate(this._currentView, (op == ZmOperation.PAGE_FORWARD));
	} else if (op == ZmOperation.PAGE_DBL_BACK || op == ZmOperation.PAGE_DBL_FORW) {
		this._paginateDouble(op == ZmOperation.PAGE_DBL_FORW);
	}
};

// Participant listeners

// Search based on email address
ZmListController.prototype._participantSearchListener =
function(ev) {
	var name = this._actionEv.address.getAddress();
	this._appCtxt.getSearchController().fromSearch(name);
};

// Browse based on email address
ZmListController.prototype._participantBrowseListener =
function(ev) {
	var name = this._actionEv.address.getAddress();
	this._appCtxt.getSearchController().fromBrowse(name);
};

// Compose message to participant
ZmListController.prototype._participantComposeListener =
function(ev) {
	var name = this._actionEv.address.toString(ZmEmailAddress.SEPARATOR) + ZmEmailAddress.SEPARATOR;
	var cc = this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getComposeController();
	cc.doAction(ZmOperation.NEW_MESSAGE, this._inNewWindow(ev), null, name);
};

// IM the participant (if enabled via config)
ZmListController.prototype._participantImListener =
function(ev) {
	// get the first selected message
	var msg = this._listView[this._currentView].getSelection()[0];
	var screenName = msg._contact._fullName;
	if (!this._newImDialog)
		this._newImDialog = new ZmNewImDialog(this._shell, null, screenName);
	else
		this._newImDialog.setScreenName(screenName);
	this._newImDialog.popup();
};

// If there's a contact for the participant, edit it, otherwise add it.
ZmListController.prototype._participantContactListener =
function(ev) {
	var cc = this._appCtxt.getApp(ZmZimbraMail.CONTACTS_APP).getContactController();
	if (this._actionEv.contact) {
		if (this._actionEv.contact.isLoaded()) {
			cc.show(this._actionEv.contact);
		} else {
			var callback = new AjxCallback(this, this._loadContactCallback);
			this._actionEv.contact.load(callback);
		}
	} else {
		var contact = new ZmContact(this._appCtxt);
		contact.initFromEmail(this._actionEv.address);
		cc.show(contact, true);
	}
};

ZmListController.prototype._loadContactCallback =
function(resp, contact) {
	var cc = this._appCtxt.getApp(ZmZimbraMail.CONTACTS_APP).getContactController();
	cc.show(contact);
};

// Drag and drop listeners

ZmListController.prototype._dragListener =
function(ev) {
	if (ev.action == DwtDragEvent.SET_DATA) {
		ev.srcData = {data: ev.srcControl.getDnDSelection(), controller: this};
	}
};

// The list view as a whole is the drop target, since it's the lowest-level widget. Still, we
// need to find out which item got dropped onto, so we get that from the original UI event
// (a mouseup). The header is within the list view, but not an item, so it's not a valid drop
// target. One drawback of having the list view be the drop target is that we can't exercise
// fine-grained control on what's a valid drop target. If you enter via an item and then drag to
// the header, it will appear to be valid.
ZmListController.prototype._dropListener =
function(ev) {
	var view = this._listView[this._currentView];
	var div = Dwt.getAttr(ev.uiEvent.target, "_itemIndex", true);
	var item = div ? view.getItemFromElement(div) : null

	// only tags can be dropped on us
	if (ev.action == DwtDropEvent.DRAG_ENTER) {
		ev.doIt = (item && this._dropTgt.isValidTarget(ev.srcData));
		DBG.println(AjxDebug.DBG3, "DRAG_ENTER: doIt = " + ev.doIt);
	    view.dragSelect(div);
	} else if (ev.action == DwtDropEvent.DRAG_DROP) {
	    view.dragDeselect(div);
		var items = [item];
		var sel = view.getSelection();
		if (sel.length) {
			var vec = AjxVector.fromArray(sel);
			if (vec.contains(item))
				items = sel;
		}
		var tag = ev.srcData;
		this._doTag(items, tag, true);
	} else if (ev.action == DwtDropEvent.DRAG_LEAVE) {
		view.dragDeselect(div);
	} else if (ev.action == DwtDropEvent.DRAG_OP_CHANGED) {
	}
};

// Dialog callbacks

// Created a new tag, now apply it.
ZmListController.prototype._tagChangeListener =
function(ev) {
	// only process if current view is this view!
	if (this._app.getAppViewMgr().getCurrentViewId() == this._getViewType()) {
		if (ev.type == ZmEvent.S_TAG && ev.event == ZmEvent.E_CREATE && this._creatingTag) {
			var tag = ev.getDetail("organizers")[0];
			this._doTag(this._pendingActionData, tag, true);
			this._creatingTag = false;
			this._pendingActionData = null;
			this._popdownActionListener();
		}
	}
};

// new organizer callbacks

ZmListController.prototype._newFolderCallback =
function(parent, name, color, url) {
	// REVISIT: Do we really want to close the dialog before we
	//          know if the create succeeds or fails?
	var dialog = this._appCtxt.getNewFolderDialog();
	dialog.popdown();

	var overviewController = this._appCtxt.getOverviewController();
	var controller = overviewController.getTreeController(ZmOrganizer.FOLDER);
	controller._doCreate(parent, name, color, url);
};

ZmListController.prototype._newTagCallback =
function(creatingTag, name, color) {
	// REVISIT: Do we really want to close the dialog before we
	//          know if the create succeeds or fails?
	var dialog = this._appCtxt.getNewTagDialog();
	dialog.popdown();

	var overviewController = this._appCtxt.getOverviewController();
	var tagController = overviewController.getTreeController(ZmOrganizer.TAG);
	tagController._doCreate(name, color);

	this._creatingTag = creatingTag;
};

ZmListController.prototype._newAddrBookCallback =
function(parent, name, color) {
	// REVISIT: Do we really want to close the dialog before we
	//          know if the create succeeds or fails?
	var dialog = this._appCtxt.getNewAddrBookDialog();
	dialog.popdown();

	var overviewController = this._appCtxt.getOverviewController();
	var controller = overviewController.getTreeController(ZmOrganizer.ADDRBOOK);
	controller._doCreate(parent, name, color);
};

ZmListController.prototype._newCalendarCallback =
function(parent, name, color, url, excludeFb) {
	// REVISIT: Do we really want to close the dialog before we
	//          know if the create succeeds or fails?
	var dialog = this._appCtxt.getNewCalendarDialog();
	dialog.popdown();

	var overviewController = this._appCtxt.getOverviewController();
	var controller = overviewController.getTreeController(ZmOrganizer.CALENDAR);
	controller._doCreate(parent, name, color, url, excludeFb);
};

ZmListController.prototype._newNotebookCallback =
function(parent, name, color/*, url*/) {
	// REVISIT: Do we really want to close the dialog before we
	//          know if the create succeeds or fails?
	var dialog = this._appCtxt.getNewNotebookDialog();
	dialog.popdown();

	var overviewController = this._appCtxt.getOverviewController();
	var controller = overviewController.getTreeController(ZmOrganizer.NOTEBOOK);
	controller._doCreate(parent, name, color);
};

// Move stuff to a new folder.
ZmListController.prototype._moveCallback =
function(folder) {
	this._doMove(this._pendingActionData, folder, null, true);
	this._clearDialog(this._appCtxt.getMoveToDialog());
};

// Data handling

// Flag/unflag an item
ZmListController.prototype._doFlag =
function(items) {
	var on = !items[0].isFlagged;
	var items1 = [];
	for (var i = 0; i < items.length; i++) {
		if (items[i].isFlagged != on) {
			items1.push(items[i]);
		}
	}
	this._list.flagItems(items1, "flag", on);
};

// Tag/untag items
ZmListController.prototype._doTag =
function(items, tag, doTag) {
	this._list.tagItems(items, tag.id, doTag);
};

// Remove all tags for given items
ZmListController.prototype._doRemoveAllTags =
function(items) {
	this._list.removeAllTags(items);
};

/*
* Deletes one or more items from the list.
*
* @param items			[Array]			list of items to delete
* @param hardDelete		[boolean]*		if true, physically delete items
* @param attrs			[Object]*		additional attrs for SOAP command
*/
ZmListController.prototype._doDelete =
function(items, hardDelete, attrs) {
	this._list.deleteItems(items, hardDelete, attrs);
};

/**
* Moves a list of items to the given folder. Any item already in that folder is excluded.
*
* @param items		[Array]			a list of items to move
* @param folder		[ZmFolder]		destination folder
* @param attrs		[Object]		additional attrs for SOAP command
@ @param force		[boolean]		true if forcing a move request (no copy)
*/
ZmListController.prototype._doMove =
function(items, folder, attrs, force) {
	if (!(items instanceof Array)) items = [items];

	var move = [];
	var copy = [];
	for (var i = 0; i < items.length; i++) {
		var item = items[i];
		if (!item.folderId || item.folderId != folder.id) {
			if (!force && (item.isShared() || item.isReadOnly() || folder.link))
				copy.push(item);
			else
				move.push(item);
		}
	}

	if (move.length)
		this._list.moveItems(move, folder, attrs);

	if (copy.length)
		this._list.copyItems(copy, folder, attrs);
};

// Modify an item
ZmListController.prototype._doModify =
function(item, mods) {
	this._list.modifyItem(item, mods);
};

// Create an item. We need to be passed a list since we may not have one.
ZmListController.prototype._doCreate =
function(list, args) {
	list.create(args);
};

// Miscellaneous

/*
* Adds the same listener to all of the items in a button or menu item's submenu.
* By default, propagates the listener for the given operation.
*
* @param parent		[DwtControl]		parent toolbar or menu
* @param op			[constant]			operation (button or menu item)
* @param listener	[AjxListener]*		listener to propagate
*/
ZmListController.prototype._propagateMenuListeners =
function(parent, op, listener) {
	if (!parent) return;
	listener = listener ? listener : this._listeners[op];
	var opWidget = parent.getOp(op);
	if (opWidget) {
		var menu = opWidget.getMenu();
	    var items = menu.getItems();
		var cnt = menu.getItemCount();
		for (var i = 0; i < cnt; i++)
			items[i].addSelectionListener(listener);
	}
};

// Add listener to tag menu
ZmListController.prototype._setupTagMenu =
function(parent) {
	if (!parent) return;
	var tagMenu = parent.getTagMenu();
	if (tagMenu)
		tagMenu.addSelectionListener(this._listeners[ZmOperation.TAG]);
	if (parent instanceof ZmButtonToolBar) {
		var tagButton = parent.getOp(ZmOperation.TAG_MENU);
		if (tagButton)
			tagButton.addDropDownSelectionListener(this._listeners[ZmOperation.TAG_MENU]);
	}
};

// Dynamically build the tag menu based on selected items and their tags.
ZmListController.prototype._setTagMenu =
function(parent) {
	if (!parent) return;
	var tagOp = parent.getOp(ZmOperation.TAG_MENU);
	if (tagOp) {
		var tagMenu = parent.getTagMenu();
		// dynamically build tag menu add/remove lists
		var items = this._listView[this._currentView].getSelection();
		if (items instanceof ZmItem)
			items = [items];
		tagMenu.set(items, this._tagList);
		if (parent instanceof ZmActionMenu)
			tagOp.setText(this._getTagMenuMsg(items.length));
		else {
			tagMenu.parent.popup();
		}
	}
};

// Set up the New button based on the current app.
ZmListController.prototype._setNewButtonProps =
function(view, toolTip, enabledIconId, disabledIconId, defaultId) {
	var newButton = this._toolbar[view].getButton(ZmOperation.NEW_MENU);
	if (newButton) {
		newButton.setToolTipContent(toolTip);
		newButton.setImage(enabledIconId);
		newButton.setDisabledImage(disabledIconId);
		this._defaultNewId = defaultId;
	}
};

// Sets text to "add" or "edit" based on whether a participant is a contact or not.
ZmListController.prototype._setContactText =
function(isContact) {
	var newOp = isContact ? ZmOperation.EDIT_CONTACT : ZmOperation.NEW_CONTACT;
	var newText = isContact ? null : ZmMsg.AB_ADD_CONTACT;
	ZmOperation.setOperation(this._toolbar[this._currentView], ZmOperation.CONTACT, newOp, ZmMsg.AB_ADD_CONTACT);
	ZmOperation.setOperation(this.getActionMenu(), ZmOperation.CONTACT, newOp, newText);
};

// Resets the available options on a toolbar or action menu.
ZmListController.prototype._resetOperations =
function(parent, num) {
	if (!parent) return;
	if (num == 0) {
		parent.enableAll(false);
		parent.enable(ZmOperation.NEW_MENU, true);
	} else if (num == 1) {
		parent.enableAll(true);
	} else if (num > 1) {
		// enable only the tag and delete operations
		parent.enableAll(false);
		parent.enable([ZmOperation.NEW_MENU, ZmOperation.TAG_MENU, ZmOperation.DELETE, ZmOperation.MOVE], true);
	}
};

// Resets the available options on the toolbar
ZmListController.prototype._resetToolbarOperations =
function() {
	this._resetOperations(this._toolbar[this._currentView], this._listView[this._currentView].getSelectionCount());
};

// depending on "Always in New Window" option and whether Shift key is pressed,
// determine whether action should be in new window or not
ZmListController.prototype._inNewWindow =
function(ev) {
	var setting = this._appCtxt.get(ZmSetting.NEW_WINDOW_COMPOSE);
	return !ev ? setting : ((!setting && ev && ev.shiftKey) || (setting && ev && !ev.shiftKey));
};

// Pagination

ZmListController.prototype._cacheList =
function(search, offset) {
	var type = this._getItemType();
	if (this._list) {
		var newList = search.getResults(type).getVector();
		offset = offset ? offset : parseInt(search.getAttribute("offset"));
		this._list.cache(offset, newList);
	} else {
		this._list = search.getResults(type);
	}
};

ZmListController.prototype._search =
function(view, offset, limit, callback, isCurrent, lastId, lastSortVal) {
	var sortBy = this._appCtxt.get(ZmSetting.SORTING_PREF, view);
	var types = this._activeSearch.search.types; // use types from original search
	var sc = this._appCtxt.getSearchController();
	var params = {query: this.getSearchString(), types: types, sortBy: sortBy, offset: offset, limit: limit,
				  lastId: lastId, lastSortVal: lastSortVal};
	// add any additional params...
	this._getMoreSearchParams(params);

	var search = new ZmSearch(this._appCtxt, params);
	if (isCurrent)
		this._currentSearch = search;

	this._appCtxt.getSearchController().redoSearch(search, true, null, callback);
};

/*
* Gets next or previous page of items. The set of items may come from the 
* cached list, or from the server (using the current search as a base).
* <p>
* The loadIndex is the index'd item w/in the list that needs to be loaded - 
* initiated only when user is in CV and pages a conversation that has not 
* been loaded yet.</p>
* <p>
* Note that this method returns a value even though it may make an
* asynchronous SOAP request. That's possible as long as no caller
* depends on the results of that request. Currently, the only caller that
* looks at the return value acts on it only if no request was made.</p>
*
* @param view		[constant]		current view
* @param forward	[boolean]		if true, get next page rather than previous
* @param loadIndex	[int]			index of item to show
*/
ZmListController.prototype._paginate = 
function(view, forward, loadIndex) {
	var offset = this._listView[view].getNewOffset(forward);
	var limit = this._listView[view].getLimit();
	forward ? this.currentPage++ : this.currentPage--;
	this.maxPage = Math.max(this.maxPage, this.currentPage);

	this._listView[view].setOffset(offset);
	
	// see if we're out of items and the server has more
	if ((offset + limit > this._list.size() && this._list.hasMore()) || this.pageIsDirty[this.currentPage]) {
		// figure out how many items we need to fetch
		var delta = (offset + limit) - this._list.size();
		var max = delta < limit && delta > 0 ? delta : limit;
		if (max < limit)
			offset = ((offset + limit) - max) + 1;

		// figure out if this requires cursor-based paging
		var list = this._listView[this._currentView].getList();
		var lastItem = list.getLast();
		var lastSortVal = (lastItem && lastItem.id)
			? lastItem.getSortVal(this._activeSearch.search.sortBy)
			: null;
		var lastId = lastSortVal ? lastItem.id : null;

		// get next page of items from server; note that callback may be overridden
		var respCallback = new AjxCallback(this, this._handleResponsePaginate, [view, false, loadIndex, offset]);
		this._search(view, offset, max, respCallback, true, lastId, lastSortVal);
		return false;
	} else {
		this._resetOperations(this._toolbar[view], 0);
		this._resetNavToolBarButtons(view);
		this._setViewContents(view);
		this._resetSelection();
		return true;
	}
};

/*
* Updates the list and the view after a new page of items has been retrieved.
*
* @param view					[constant]		current view
* @param saveSelection			[boolean]		if true, maintain current selection
* @param loadIndex				[int]			index of item to show
* @param result					[ZmCsfeResult]	result of SOAP request
* @param ignoreResetSelection	[boolean] 		if true, dont reset selection
*/
ZmListController.prototype._handleResponsePaginate =
function(view, saveSelection, loadIndex, offset, result, ignoreResetSelection) {
	var searchResult = result.getResponse();
	
	// update "more" flag
	this._list.setHasMore(searchResult.getAttribute("more"));
	
	// cache search results into internal list
	this._cacheList(searchResult, offset);
	
	this._resetOperations(this._toolbar[view], 0);
	this._resetNavToolBarButtons(view);

	// remember selected index if told to
	var selItem = saveSelection ? this._listView[this._currentView].getSelection()[0] : null;
	var selectedIdx = selItem ? this._listView[this._currentView]._getItemIndex(selItem) : -1;
	
	this._setViewContents(view);
	this.pageIsDirty[this.currentPage] = false;

	// bug fix #5134 - some views may not want to reset the current selection
	if (!ignoreResetSelection) {
		this._resetSelection(selectedIdx);
	}
};

ZmListController.prototype._getMoreSearchParams =
function(params) {
	// overload me if more params are needed for SearchRequest
};

ZmListController.prototype._checkReplenish = 
function(callback) {
	var view = this._listView[this._currentView];
	var list = view.getList();
	// don't bother if the view doesn't really have a list
	var replenishmentDone = false;
	if (list) {
		var replCount = view.getLimit() - view.size();
		if (replCount > view.getReplenishThreshold()) {
			this._replenishList(this._currentView, replCount, callback);
			replenishmentDone = true;
		}
	}
	if (callback && !replenishmentDone)
		callback.run();
};

ZmListController.prototype._replenishList = 
function(view, replCount, callback) {
	// determine if there are any more items to replenish with
	var idxStart = this._listView[view].getOffset() + this._listView[view].size();
	var totalCount = this._list.size();
	
	if (idxStart < totalCount) {
		// replenish from cache
		var idxEnd = idxStart + replCount;
		if (idxEnd > totalCount)
			idxEnd = totalCount;
		var list = this._list.getVector().getArray();
		var sublist = list.slice(idxStart, idxEnd);
		var subVector = AjxVector.fromArray(sublist);
		this._listView[view].replenish(subVector);
		if (callback) callback.run();
	} else {
		// replenish from server request
		this._getMoreToReplenish(view, replCount, callback);
	}
};

ZmListController.prototype._resetSelection = 
function(idx) {
	var list = this._listView[this._currentView].getList();
	if (list) {
		var selIdx = idx >= 0 ? idx : 0;
		var first = list.get(selIdx);
		this._listView[this._currentView].setSelection(first, false, true);
	}
};

/*
* Requests replCount items from the server to replenish current listview
*
* @param view		[constant]		current view to replenish
* @param replCount 	[int]			number of items to replenish
* @param callback	[AjxCallback]	async callback
*/
ZmListController.prototype._getMoreToReplenish = 
function(view, replCount, callback) {
	if (this._list.hasMore()) {
		DBG.println(AjxDebug.DBG2, "need to replenish: " + replCount);
		var offset = this._list.size();
		var respCallback = new AjxCallback(this, this._handleResponseGetMoreToReplenish, [view, callback]);
		this._search(view, offset, replCount, respCallback);
	} else {
		if (this._listView[view].size() == 0)
			this._listView[view]._setNoResultsHtml();
		if (callback) callback.run();
	}
};

ZmListController.prototype._handleResponseGetMoreToReplenish = 
function(view, callback, result) {
	var searchResult = result.getResponse();
	
	// set updated has more flag
	var more = searchResult.getAttribute("more");
	this._list.setHasMore(more);
	
	// cache search results into internal list
	this._cacheList(searchResult);

	// update view w/ replenished items
	var list = searchResult.getResults(this._getItemType()).getVector();
	this._listView[view].replenish(list);

	// reset forward pagination button only
	this._toolbar[view].enable(ZmOperation.PAGE_FORWARD, more);
	
	if (callback) callback.run(result);
};

ZmListController.prototype._setNavToolBar = 
function(toolbar, view) {
	this._navToolBar[view] = toolbar;
	if (this._navToolBar[view]) {
		var navBarListener = new AjxListener(this, this._navBarListener);
		if (this._navToolBar[view].hasSingleArrows) {
			this._navToolBar[view].addSelectionListener(ZmOperation.PAGE_BACK, navBarListener);
			this._navToolBar[view].addSelectionListener(ZmOperation.PAGE_FORWARD, navBarListener);
		}
		if (this._navToolBar[view].hasDoubleArrows) {
			this._navToolBar[view].addSelectionListener(ZmOperation.PAGE_DBL_BACK, navBarListener);
			this._navToolBar[view].addSelectionListener(ZmOperation.PAGE_DBL_FORW, navBarListener);
		}
	}
};

ZmListController.prototype._resetNavToolBarButtons = 
function(view) {
	if (!this._navToolBar[view]) return;

	if (this._navToolBar[view].hasDoubleArrows)
		this._navToolBar[view].enable([ZmOperation.PAGE_DBL_BACK, ZmOperation.PAGE_DBL_FORW], false);

	if (this._navToolBar[view].hasSingleArrows) {
		var offset = this._listView[view].getOffset();
		this._navToolBar[view].enable(ZmOperation.PAGE_BACK, offset > 0);
	
		// determine also if we have more cached conv to show (in case more is wrong)
		var hasMore = false;
		if (this._list) {
			hasMore = this._list.hasMore();
			if (!hasMore && ((offset + this._listView[view].getLimit()) < this._list.size()))
				hasMore = true;
		}

		this._navToolBar[view].enable(ZmOperation.PAGE_FORWARD, hasMore);
	}
};

ZmListController.prototype.enablePagination =
function(enabled, view) {
	if (!this._navToolBar[view]) return;

	if (enabled) {
		this._resetNavToolBarButtons(view);
	} else {	
		if (this._navToolBar[view].hasDoubleArrows)
			this._navToolBar[view].enable([ZmOperation.PAGE_DBL_BACK, ZmOperation.PAGE_DBL_FORW], false);
		if (this._navToolBar[view].hasSingleArrows)
			this._navToolBar[view].enable([ZmOperation.PAGE_BACK, ZmOperation.PAGE_FORWARD], false);
	}
};

ZmListController.prototype._showListRange = 
function(view) {
	var offset = this._listView[view].getOffset();
	var limit = this._listView[view].getLimit();
	var size = this._list.size();
	var text = "";
	if (size > 0) {
		var start = offset + 1;
		var end = Math.min(offset + limit, size);
		text = start + " - " + end;
	}
	this._navToolBar[view].setText(text);
};

// default callback before a view is shown - enable/disable nav buttons
ZmListController.prototype._preShowCallback =
function(view, viewPushed) {
	this._resetNavToolBarButtons(view);
	return true;
};

/*
* Creates the New menu's drop down menu the first time the drop down arrow is used,
* then removes itself as a listener.
*/
ZmListController._newDropDownListener = 
function(event) {
	var toolbar = this;

	var controller = toolbar._ZmListController_this;
	controller._propagateMenuListeners(toolbar, ZmOperation.NEW_MENU);

	var button = toolbar.getButton(ZmOperation.NEW_MENU);
	var listener = toolbar._ZmListController_newDropDownListener;
	button.removeDropDownSelectionListener(listener);

	delete toolbar._ZmListController_this;
	delete toolbar._ZmListController_newDropDownListener;
};

ZmListController.prototype._getDefaultFocusItem = 
function() {
	return this._listView[this._currentView];
};

ZmListController.prototype.getActionMenu =
function() {
	if (!this._actionMenu) {
		this._initializeActionMenu();
		//DBG.timePt("_initializeActionMenu");
		this._resetOperations(this._actionMenu, 0);
		//DBG.timePt("this._resetOperation(actionMenu)");
	}
	return this._actionMenu;
};
