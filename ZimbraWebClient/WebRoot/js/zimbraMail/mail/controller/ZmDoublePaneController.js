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
* Creates a new, empty double pane controller.
* @constructor
* @class
* This class manages the two-pane view. The top pane contains a list view of 
* items, and the bottom pane contains the selected item content.
*
* @author Parag Shah
* @param appCtxt	app context
* @param container	containing shell
* @param mailApp	containing app
*/
function ZmDoublePaneController(appCtxt, container, mailApp) {

	if (arguments.length == 0) return;
	ZmMailListController.call(this, appCtxt, container, mailApp);
	this._readingPaneOn = appCtxt.get(ZmSetting.READING_PANE_ENABLED);

	this._dragSrc = new DwtDragSource(Dwt.DND_DROP_MOVE);
	this._dragSrc.addDragListener(new AjxListener(this, this._dragListener));	
	
	this._listeners[ZmOperation.SHOW_ORIG] = new AjxListener(this, this._showOrigListener);
	this._listeners[ZmOperation.ADD_FILTER_RULE] = new AjxListener(this, this._filterListener);
	
	this._viewMenuCallback = new AjxCallback(this, this._checkViewMenu);
};

ZmDoublePaneController.prototype = new ZmMailListController;
ZmDoublePaneController.prototype.constructor = ZmDoublePaneController;

// Public methods

ZmDoublePaneController.prototype.toString = 
function() {
	return "ZmDoublePaneController";
};

/**
* Displays the given item in a two-pane view. The view is actually
* created in _loadItem(), since it must execute last.
*
* @param search		[ZmSearch]	the current search results
* @param item		[ZmItem]	a generic item
*/
ZmDoublePaneController.prototype.show =
function(search, item) {

	ZmMailListController.prototype.show.call(this, search);
	this.reset();
	this._item = item;
	this._setup(this._currentView);

	// see if we have it cached? Check if conv loaded?
	this._loadItem(item, this._currentView);
	this._checkViewMenu();
};

/**
* Clears the conversation view, which actually just clears the message view.
*/
ZmDoublePaneController.prototype.reset =
function() {
	if (this._doublePaneView)
		this._doublePaneView.reset();
};

/**
* Shows or hides the reading pane.
*
* @param view		the id of the menu item
* @param toggle		flip state of reading pane
*/
ZmDoublePaneController.prototype.switchView = 
function(view, toggle) {
	var appToolbar = this._appCtxt.getCurrentAppToolbar();
	var menu = appToolbar.getViewButton().getMenu();
	var mi = menu.getItemById(ZmOperation.MENUITEM_ID, view);
	if (toggle) {
		mi.setChecked(!mi.getChecked(), true);
	} else {
		if (this._readingPaneOn == mi.getChecked()) return;
	}

	this._readingPaneOn = mi.getChecked();
	this._checkViewMenu();
	this._doublePaneView.toggleView();

	// set msg in msg view if reading pane is being shown
	if (this._readingPaneOn) {
		var currentMsg = this._doublePaneView.getSelection()[0];
		// DONT bother checking if current msg is already being displayed!
		if (currentMsg) {
			if (!currentMsg.isLoaded()) {
				var respCallback = new AjxCallback(this, this._handleResponseSwitchView, currentMsg);
				currentMsg.load(this._appCtxt.get(ZmSetting.VIEW_AS_HTML), false, respCallback);
			} else {
				this._doublePaneView.setMsg(currentMsg);
			}
		}
	}
	this._doublePaneView.getMsgListView()._resetColWidth();
};

ZmDoublePaneController.prototype._handleResponseSwitchView = 
function(currentMsg) {
	this._doublePaneView.setMsg(currentMsg);
};

// called after a delete has occurred. 
// Return value indicates whether view was popped as a result of a delete
ZmDoublePaneController.prototype.handleDelete = 
function() {
	return false;
};


// Private and protected methods

ZmDoublePaneController.prototype._createDoublePaneView = 
function() {
	// overload me
};

// Creates the conv view, which is not a standard list view (it's a two-pane
// sort of thing).
ZmDoublePaneController.prototype._initialize =
function(view) {
	// set up double pane view (which creates the MLV and MV)
	if (!this._doublePaneView){
		this._doublePaneView = this._createDoublePaneView();
		this._doublePaneView.addInviteReplyListener(this._inviteReplyListener);
		this._doublePaneView.addShareListener(this._shareListener);
	}

	ZmMailListController.prototype._initialize.call(this, view);
	if (!this._appCtxt.get(ZmSetting.CONVERSATIONS_ENABLED)) {
		this._appCtxt.getCurrentAppToolbar().setCallback(ZmController.TRAD_VIEW, this._viewMenuCallback);
	}
};

ZmDoublePaneController.prototype._getToolBarOps =
function() {
	var list = this._standardToolBarOps();
	list.push(ZmOperation.SEP);
	list = list.concat(this._msgOps());
	list.push(ZmOperation.SEP);
	list.push(ZmOperation.SPAM);
	list.push(ZmOperation.DETACH);
	return list;
};

ZmDoublePaneController.prototype._getActionMenuOps =
function() {
	var list = this._flagOps();
	list.push(ZmOperation.SEP);
	list = list.concat(this._msgOps());
	list.push(ZmOperation.SEP);
	list = list.concat(this._standardActionMenuOps());
	list.push(ZmOperation.SEP);
	list.push(ZmOperation.SHOW_ORIG);
	if (this._appCtxt.get(ZmSetting.FILTERS_ENABLED)) {
		list.push(ZmOperation.ADD_FILTER_RULE);
	}
	return list;
};

// Returns the already-created message list view.
ZmDoublePaneController.prototype._createNewView = 
function() {
	var mlv = null;
	if (this._doublePaneView) {
		mlv = this._doublePaneView.getMsgListView();
		mlv.setDragSource(this._dragSrc);
	}
	return mlv;
};

ZmDoublePaneController.prototype.getReferenceView = 
function() {
	return this._doublePaneView;
};

ZmDoublePaneController.prototype._getTagMenuMsg = 
function(num) {
	return (num == 1) ? ZmMsg.tagMessage : ZmMsg.tagMessages;
};

ZmDoublePaneController.prototype._getMoveDialogTitle = 
function(num) {
	return (num == 1) ? ZmMsg.moveMessage : ZmMsg.moveMessages;
};

ZmDoublePaneController.prototype._setViewContents =
function(view) {
	this._doublePaneView.setItem(this._item);
};

ZmDoublePaneController.prototype._setSelectedMsg =
function() {
	var selCnt = this._listView[this._currentView].getSelectionCount();
	if (selCnt == 1) {
		// Check if currently displaying selected element in message view
		var msg = this._listView[this._currentView].getSelection()[0];
		if (!msg.isLoaded()) {
			this._appCtxt.getSearchController().setEnabled(false);
			this._doGetMsg(msg);
		} else {
			this._doublePaneView.setMsg(msg);
			if (msg.isUnread) {
				// msg was cached, then marked unread
				this._list.markRead([msg], true);
			}
		}
	}
};

// Adds a "Reading Pane" checked menu item to a view menu
ZmDoublePaneController.prototype._setupReadingPaneMenuItem =
function(view, menu, checked, itemId) {
	var appToolbar = this._appCtxt.getCurrentAppToolbar();
	var id = itemId || ZmController.READING_PANE_VIEW;
	var menu = menu ? menu : appToolbar.getViewMenu(view);
	if (!menu) {
		// conversations not enabled
		menu = new ZmPopupMenu(appToolbar.getViewButton());
	}
	if (!menu._menuItems[id]) {
		var mi = menu.createMenuItem(id, "SplitPane", ZmMsg.readingPane, null, true, DwtMenuItem.CHECK_STYLE);
		mi.setData(ZmOperation.MENUITEM_ID, id);
		mi.addSelectionListener(this._listeners[ZmOperation.VIEW]);
		mi.setChecked(checked, true);
	}
	appToolbar.setViewMenu(view, menu);
	return menu;
};

// we overload _doAction for bug fix #3623
ZmDoublePaneController.prototype._doAction =
function(ev, action, extraBodyText) {
	// first find out if the current message is in HTML
	var msgView = this._doublePaneView.getMsgView();
	if (action != ZmOperation.DRAFT && msgView.hasHtmlBody()) {
		// then find out if the user's preference is Text
		if (this._appCtxt.get(ZmSetting.COMPOSE_AS_FORMAT) == ZmSetting.COMPOSE_TEXT) {
			// then find out if a text part currently exists for the message
			var msg = this._getMsg();
			var textPart = msg.getBodyPart(ZmMimeTable.TEXT_PLAIN);
			if (!textPart) {
				// if not, get the DOM tree for the IFRAME create for the HTML message
				var bodyEl = msgView.getHtmlBodyElement();
				// and run it thru the HTML stripper
				textPart = bodyEl ? AjxStringUtil.convertHtml2Text(bodyEl) : null;
				// set the text part back into the message
				if (textPart)
					msg.setTextPart(textPart);
			}
		}
	}
	// finally, call the base class last
	ZmMailListController.prototype._doAction.call(this, ev, action, extraBodyText);
};

/**
 * Sets the content of the view button if conversations are disabled (in which case
 * "Reading Pane" is the sole menu item).
 * 
 * @param mi	[DwtMenuItem]*	the Reading Pane menu item
 */
ZmDoublePaneController.prototype._checkViewMenu =
function() {
	if (this._appCtxt.get(ZmSetting.CONVERSATIONS_ENABLED)) { return; }
	var appToolbar = this._appCtxt.getCurrentAppToolbar();
	var menu = appToolbar.getViewButton().getMenu();
	var mi = menu.getItemById(ZmOperation.MENUITEM_ID, ZmController.READING_PANE_VIEW);
	var icon = mi.getImage();
	if (icon) {
		appToolbar._viewButton.setImage(icon);
	}
	if (appToolbar._viewLabel) {
		appToolbar._viewButton.setText(this._readingPaneOn ? ZmMsg.readingPaneOn : ZmMsg.readingPaneOff);
	}
};

/*
* Displays a list of messages. If passed a conv, loads its message
* list. If passed a list, simply displays it. The first message will be 
* selected, which will trigger a message load/display.
*
* @param item	[ZmConv or ZmMailList]		conv or list of msgs
* @param view	[constant]					owning view type
*/
ZmDoublePaneController.prototype._loadItem =
function(item, view) {
	if (item instanceof ZmMailItem) { // conv
		DBG.timePt("***** CONV: load", true);
		if (!item.isLoaded()) {
			var respCallback = new AjxCallback(this, this._handleResponseLoadItem, view);
			item.load(this.getSearchString(), null, null, null, null, respCallback);
		} else {
			this._handleResponseLoadItem(view, new ZmCsfeResult(item.msgs));
		}
	} else { // msg list
		this._displayResults(view);
	}
};

ZmDoublePaneController.prototype._displayResults =
function(view) {
	var elements = new Object();
	elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar[view];
	elements[ZmAppViewMgr.C_APP_CONTENT] = this._doublePaneView;
	this._setView(view, elements, this._isTopLevelView());
	this._resetNavToolBarButtons(view);
				
	// always allow derived classes to reset size after loading
	var sz = this._doublePaneView.getSize();
	this._doublePaneView._resetSize(sz.x, sz.y);
};

// Loads and displays the given message. If the message was unread, it gets marked as
// read, and the conversation may be marked as read as well.
ZmDoublePaneController.prototype._doGetMsg =
function(msg) {
	if (msg) {
		var respCallback = new AjxCallback(this, this._handleResponseDoGetMsg, msg);
		msg.load(this._appCtxt.get(ZmSetting.VIEW_AS_HTML), false, respCallback);
	} else {
		DBG.println("XXX: msg not loaded!");
	}
};

ZmDoublePaneController.prototype._resetOperations = 
function(parent, num) {
	ZmMailListController.prototype._resetOperations.call(this, parent, num);
	parent.enable(ZmOperation.SHOW_ORIG, num == 1);
	if (this._appCtxt.get(ZmSetting.FILTERS_ENABLED)) {
		parent.enable(ZmOperation.ADD_FILTER_RULE, num == 1);
	}
};

// top level view means this view is allowed to get shown when user clicks on 
// app icon in app toolbar - overload to not allow this.
ZmDoublePaneController.prototype._isTopLevelView = 
function() {
	return true;
};


// List listeners

// Clicking on a message in the message list loads and displays it.
ZmDoublePaneController.prototype._listSelectionListener =
function(ev) {
	ZmMailListController.prototype._listSelectionListener.call(this, ev);
	
	var currView = this._listView[this._currentView];

	if (ev.detail == DwtListView.ITEM_DBL_CLICKED) {
		var msg = this._getMsg();
		if (msg) {
			if (msg.isDraft) {
				// open draft in compose view
				this._doAction(ev, ZmOperation.DRAFT);
			} else if (!this._readingPaneOn) {
				try {
					this._app.getMsgController().show(msg, currView._mode);

					// if msg is cached, then mark read if unread
					if (msg.isLoaded() && msg.isUnread)
						this._list.markRead([msg], true);
				} catch (ex) {
					this._handleException(ex, this._listSelectionListener, ev, false);
				}
			}
		}
	} else {
		if (this._readingPaneOn) {
			this._setSelectedMsg();
	    } else {
			var msg = currView.getSelection()[0];
			if (msg)
				this._doublePaneView.resetMsg(msg);
	    }
    }
	DBG.timePt("***** CONV: msg selection");
};

ZmDoublePaneController.prototype._listActionListener =
function(ev) {
	ZmMailListController.prototype._listActionListener.call(this, ev);

	if (!this._readingPaneOn) {
		// reset current message
		var msg = this._listView[this._currentView].getSelection()[0];
		if (msg)
			this._doublePaneView.resetMsg(msg);
	}
};

// Check to see if the entire conversation is now read.
ZmDoublePaneController.prototype._markReadListener = 
function(ev) {
	this._list.markRead(this._listView[this._currentView].getSelection(), true);
};

// Check to see if the entire conversation is now unread.
ZmDoublePaneController.prototype._markUnreadListener = 
function(ev) {
	this._list.markRead(this._listView[this._currentView].getSelection(), false);
};

ZmDoublePaneController.prototype._showOrigListener = 
function(ev) {
	var msg = this._listView[this._currentView].getSelection()[0];
	if (msg) {
		var msgFetchUrl = this._appCtxt.getCsfeMsgFetcher() + "id=" + msg.id;
		// create a new window w/ generated msg based on msg id
		window.open(msgFetchUrl, "_blank", "menubar=yes,resizable=yes,scrollbars=yes");
	}
};

ZmDoublePaneController.prototype._filterListener = 
function(ev) {
	var msg = this._listView[this._currentView].getSelection()[0];
	if (!msg) return;
	
	var rule = new ZmFilterRule();
	var from = msg.getAddress(ZmEmailAddress.FROM);
	if (from) rule.addCondition(new ZmCondition(ZmFilterRule.C_FROM, ZmFilterRule.OP_CONTAINS, from.address));
	var to = msg.getAddress(ZmEmailAddress.TO);
	if (to)	rule.addCondition(new ZmCondition(ZmFilterRule.C_TO, ZmFilterRule.OP_CONTAINS, to.address));
	var cc = msg.getAddress(ZmEmailAddress.CC);
	if (cc)	rule.addCondition(new ZmCondition(ZmFilterRule.C_CC, ZmFilterRule.OP_CONTAINS, cc.address));
	var subj = msg.getSubject();
	if (subj) rule.addCondition(new ZmCondition(ZmFilterRule.C_SUBJECT, ZmFilterRule.OP_IS, subj));
	rule.addAction(new ZmAction(ZmFilterRule.A_KEEP));
	var dialog = this._appCtxt.getFilterRuleDialog();
	dialog.popup(rule);
};

ZmDoublePaneController.prototype._dragListener =
function(ev) {
	ZmListController.prototype._dragListener.call(this, ev);
	if (ev.action == DwtDragEvent.DRAG_END)
		this._resetOperations(this._toolbar[this._currentView], this._doublePaneView.getSelection().length);
};


// Callbacks

ZmDoublePaneController.prototype._handleResponseLoadItem =
function(view, result) {
	var response = result.getResponse();
	if (response instanceof ZmList) {
		this._list = response;
		this._activeSearch = response;
	}
	DBG.timePt("***** CONV: render");
	this._displayResults(view);
};

ZmDoublePaneController.prototype._handleResponseDoGetMsg =
function(msg) {
	this._doublePaneView.setMsg(msg);
	this._appCtxt.getSearchController().setEnabled(true);
};
