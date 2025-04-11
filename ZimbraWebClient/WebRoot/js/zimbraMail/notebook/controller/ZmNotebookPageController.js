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

ZmNotebookPageController = function(appCtxt, container, app) {
	ZmNotebookController.call(this, appCtxt, container, app);

	this._listeners[ZmOperation.PAGE_BACK] = new AjxListener(this, this._pageBackListener);
	this._listeners[ZmOperation.PAGE_FORWARD] = new AjxListener(this, this._pageForwardListener);

	this._history = [];
}
ZmNotebookPageController.prototype = new ZmNotebookController;
ZmNotebookPageController.prototype.constructor = ZmNotebookPageController;

ZmNotebookPageController.prototype.toString = function() {
	return "ZmNotebookPageController";
};

// Data

ZmNotebookPageController.prototype._object;
ZmNotebookPageController.prototype._folderId;

ZmNotebookPageController.prototype._place = -1;
ZmNotebookPageController.prototype._history;

//
// Public methods
//

ZmNotebookPageController.prototype.getKeyMapName =
function() {
	return "ZmNotebookPageController";
};

ZmNotebookPageController.prototype.handleKeyAction =
function(actionCode) {
	DBG.println(AjxDebug.DBG3, "ZmNotebookPageController.handleKeyAction");
	
	switch (actionCode) {
		case ZmKeyMap.EDIT:
			if (this._object && !this._object.isReadOnly()) {
				this._editListener();
			}
			break;
		case ZmKeyMap.DEL:
			if (this._object && !this._object.isReadOnly()) {
				return ZmListController.prototype.handleKeyAction.call(this, actionCode);
			}
			break;
		case ZmKeyMap.REFRESH:
			this._refreshListener();
			break;
		default:
			return ZmListController.prototype.handleKeyAction.call(this, actionCode);
			break;
	}
	return true;
};

// page

ZmNotebookPageController.prototype.gotoPage = function(pageRef) {
	var cache = this._app.getNotebookCache();
	//var page = cache.getPageByName(pageRef.folderId, pageRef.name);
	var params = null;//ctry
	if(pageRef.name=="_Index"){
		params = {id:pageRef.folderId};
	}else{
		params={folderId:pageRef.folderId,name:pageRef.name};
	}
	var page = cache.getItemInfo(params);
	this._object = page;
	this._setViewContents(this._currentView);
	this._resetOperations(this._toolbar[this._currentView]);
};

ZmNotebookPageController.prototype.setPage = function(page) {
	var cache = this._app.getNotebookCache();
	this._object = page;
	this._resetOperations(this._toolbar[this._currentView]);
};

ZmNotebookPageController.prototype.getPage = function() {
	return this._object;
};

// view management

ZmNotebookPageController.prototype.showLink = function(link) {
	var cache = this._app.getNotebookCache();
	try {
		var page = cache.getPageByLink(link);
		if (page) {
			this.show(page);
		}
	}
	catch (e) {
		if (!this._formatter) {
			this._formatter = new AjxMessageFormat(ZmMsg.pageNotFound);
		}
		this.popupErrorDialog(this._formatter.format(link), null, null, true);
	}
};

ZmNotebookPageController.prototype.show = function(pageOrFolderId, force, fromSearch) {
	if (/*force ||*/ !(pageOrFolderId instanceof ZmPage)) {
		this._showIndex(pageOrFolderId || ZmNotebookItem.DEFAULT_FOLDER);
		return;
	}

	// save state
	this._fromSearch = fromSearch;

	var shownPage = this._object;
	var currentPage = pageOrFolderId;
	this._object = currentPage;

	// switch view
	var view = this._currentView;
	if (!view) {
		view = this._defaultView();
		force = true;
	}
	this.switchView(view, force);

	// are we already showing this note?
	if (shownPage && shownPage.name == currentPage.name &&
		shownPage.folderId == currentPage.folderId) {
		return;
	}

	if(!this._currentView._USE_IFRAME){		
	// update history
	this._folderId = null;
	if (this._object) {
		this._folderId = this._object.folderId;
		for (var i = this._place + 1; i < this._history.length; i++) {
			this._history[i] = null;
		}
		this._history.length = ++this._place;
		var pageRef = { folderId: this._object.folderId, name: this._object.name };
		this._history[this._place] = pageRef;
	}
	this._enableNaviButtons();

	// REVISIT: Need to do proper list management! For now we fake
	//          a list of a single item so that operations like
	//          tagging and delete work.
	this._list = new ZmList(ZmItem.PAGE, this._appCtxt);
	if (this._object) {
		this._list.add(this._object);
	}
	}

	// show this page
	this._setViewContents(this._currentView);
};

//
// Protected methods
//

// initialization

ZmNotebookPageController.prototype._getNaviToolBarOps = function() {
	var list = ZmNotebookController.prototype._getNaviToolBarOps.call(this);
	list = list.concat(
		ZmOperation.SEP,
		ZmOperation.PAGE_BACK, ZmOperation.PAGE_FORWARD,
		ZmOperation.CLOSE
	);
	return list;
};
ZmNotebookPageController.prototype._initializeToolBar = function(view) {
	ZmNotebookController.prototype._initializeToolBar.call(this, view);

	var toolbar = this._toolbar[this._currentView];
	var button = toolbar.getButton(ZmOperation.CLOSE);
	button.setVisible(this._fromSearch);

	var button = toolbar.getButton(ZmOperation.PAGE_BACK);
	button.setToolTipContent("");

	var button = toolbar.getButton(ZmOperation.PAGE_FORWARD);
	button.setToolTipContent("");
};

ZmNotebookPageController.prototype._resetOperations =
function(toolbarOrActionMenu, num) {
	if (!toolbarOrActionMenu) return;
	ZmNotebookController.prototype._resetOperations.call(this, toolbarOrActionMenu, num);
	if (toolbarOrActionMenu instanceof ZmToolBar) {
		this._enableNaviButtons();
	}
};

ZmNotebookPageController.prototype._enableNaviButtons = function() {
	var enabled = this._currentView == ZmController.NOTEBOOK_PAGE_VIEW;

	var toolbar = this._toolbar[this._currentView];
	var button = toolbar.getButton(ZmOperation.PAGE_BACK);
	button.setEnabled(enabled && this._place > 0);
	ZmNotebookPageController.__setButtonToolTip(this._appCtxt, button, this._history[this._place - 1], ZmMsg.goBack);

	var button = toolbar.getButton(ZmOperation.PAGE_FORWARD);
	button.setEnabled(enabled && this._place + 1 < this._history.length);
	ZmNotebookPageController.__setButtonToolTip(this._appCtxt, button, this._history[this._place + 1], ZmMsg.goForward);
};

// listeners

ZmNotebookPageController.prototype._pageBackListener = function(event) {
	this.historyLoading = true;
	if (this._place > 0) {
		this.gotoPage(this._history[--this._place]);
	}
};
ZmNotebookPageController.prototype._pageForwardListener = function(event) {
	this.historyLoading = true;
	if (this._place + 1 < this._history.length) {
		this.gotoPage(this._history[++this._place]);
	}
};

ZmNotebookPageController.prototype._dropListener =
function(ev) {
	// only tags can be dropped on us
	if (ev.action == DwtDropEvent.DRAG_ENTER) {
		if(this._object && (this._object.isShared() || this._object.isIndex() )){
		ev.doIt = false;	
		}else{
		ev.doIt = this._dropTgt.isValidTarget(ev.srcData);
		}
	} else if (ev.action == DwtDropEvent.DRAG_DROP) {
		var tag = ev.srcData;
		this._doTag([this._object], tag, true);
	}
};

// notebook page view

ZmNotebookPageController.prototype._showIndex = function(folderId) {
	var cache = this._app.getNotebookCache();
	var params = {id:folderId};	
//	var index = cache.getPageByName(folderId, ZmNotebook.PAGE_INDEX, true);
	var index = cache.getItemInfo(params);
	this.show(index);
};

//
// Private functions
//

ZmNotebookPageController.__setButtonToolTip = function(appCtxt, button, pageRef, defaultValue) {
	var text = pageRef ? pageRef.name : defaultValue;
	if (text == ZmNotebook.PAGE_INDEX) {
		var notebook = appCtxt.getById(pageRef.folderId);
		if (notebook) {
			text = notebook.getName();
		}
		else {
			/*** REVISIT ***/
			// Get the remote notebook name. Or save the remote name in the pageRef.
		}
	}
	button.setToolTipContent(text);
};

ZmNotebookPageController.prototype.updateHistory = function() {
	
	this._folderId = null;
	
	if (this._object) {
		this._folderId = this._object.folderId;
		for (var i = this._place + 1; i < this._history.length; i++) {
			this._history[i] = null;
		}
		this._history.length = ++this._place;
		var pageRef = { folderId: this._object.folderId, name: this._object.name };
		this._history[this._place] = pageRef;
	}
	this._enableNaviButtons();

	// REVISIT: Need to do proper list management! For now we fake
	//          a list of a single item so that operations like
	//          tagging and delete work.
	this._list = new ZmList(ZmItem.PAGE, this._appCtxt);
	if (this._object) {
		this._list.add(this._object);
	}
	
};

ZmNotebookPageController.prototype.refreshCurrentPage = function(){
	if(this._object && this._listView[ZmController.NOTEBOOK_PAGE_VIEW]){
	this._listView[ZmController.NOTEBOOK_PAGE_VIEW].refresh();
	}
};

ZmNotebookPageController.prototype.isIframeEnabled = function(){
	if(this._listView[ZmController.NOTEBOOK_PAGE_VIEW]){
		return this._listView[ZmController.NOTEBOOK_PAGE_VIEW]._USE_IFRAME;
	}else{
		return false;	
	}
};

ZmNotebookPageController.prototype._refreshListener = function(event) {
	if(this.isIframeEnabled()){
		this.refreshCurrentPage();
	}else{	
		ZmNotebookController.prototype._refreshListener.call(this, event);		
	}
};