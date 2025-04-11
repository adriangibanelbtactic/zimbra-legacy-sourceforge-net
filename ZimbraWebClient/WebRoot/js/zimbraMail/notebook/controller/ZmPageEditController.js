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

ZmPageEditController = function(appCtxt, container, app) {
	ZmListController.call(this, appCtxt, container, app);

	ZmPageEditController.RADIO_GROUP = {};
	ZmPageEditController.RADIO_GROUP[ZmOperation.FORMAT_HTML_SOURCE]	= 1;
	ZmPageEditController.RADIO_GROUP[ZmOperation.FORMAT_MEDIA_WIKI]		= 1;
	ZmPageEditController.RADIO_GROUP[ZmOperation.FORMAT_RICH_TEXT]		= 1;
	ZmPageEditController.RADIO_GROUP[ZmOperation.FORMAT_TWIKI]			= 1;

	this._listeners[ZmOperation.SAVE] = new AjxListener(this, this._saveListener);
	this._listeners[ZmOperation.CLOSE] = new AjxListener(this, this._closeListener);
	//this._listeners[ZmOperation.ATTACHMENT] = new AjxListener(this, this._addDocsListener);
	this._listeners[ZmOperation.SPELL_CHECK] = new AjxListener(this, this._spellCheckListener);
	this._listeners[ZmOperation.COMPOSE_FORMAT] = new AjxListener(this, this._formatListener);
}
ZmPageEditController.prototype = new ZmListController;
ZmPageEditController.prototype.constructor = ZmPageEditController;

ZmPageEditController.prototype.toString =
function() {
	return "ZmPageEditController";
};

// Data

ZmPageEditController.prototype._page;
ZmPageEditController.prototype._wikletParamDialog;
ZmPageEditController.prototype._uploadCallback;

ZmPageEditController.prototype._pageEditView;
ZmPageEditController.prototype._popViewWhenSaved;

// Public methods

ZmPageEditController.prototype.show =
function(page) {
	// NOTE: Need to protect against changes happening behind our backs
	this._page = AjxUtil.createProxy(page);
	this._page.version = page.version;

	var elements;
	if (!this._currentView) {
		this._currentView = this._defaultView();
		this._setup(this._currentView);

		elements = new Object();
		elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar[this._currentView];
		elements[ZmAppViewMgr.C_APP_CONTENT] = this._listView[this._currentView];
	}

	this._resetOperations(this._toolbar[this._currentView], 1); // enable all buttons
	this._setView(this._currentView, elements, false, false, false, true);
};

ZmPageEditController.prototype.getPage =
function() {
	return this._page;
};

// Protected methods

ZmPageEditController.prototype._getToolBarOps =
function() {
	var list = [];
	list.push(
		ZmOperation.SAVE, ZmOperation.CLOSE,
		ZmOperation.SEP
	);
	/***
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		list.push(
			ZmOperation.TAG_MENU,
			ZmOperation.SEP
		);
	}
	if (this._appCtxt.get(ZmSetting.PRINT_ENABLED))
		list.push(ZmOperation.PRINT);
	list.push(
		ZmOperation.DELETE,
		ZmOperation.SEP
	);
	/***/
	list.push(
		//ZmOperation.ATTACHMENT,
		ZmOperation.SPELL_CHECK,
		ZmOperation.FILLER
	);

	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED))
		list.push(ZmOperation.COMPOSE_FORMAT);

	return list;
};

ZmPageEditController.prototype._initializeToolBar =
function(view) {
	if (this._toolbar[view]) return;

	ZmListController.prototype._initializeToolBar.call(this, view);

	var toolbar = this._toolbar[view];
	/***
	var button = toolbar.getButton(ZmOperation.ATTACHMENT);
	button.setText(AjxEnv.is800x600orLower ? "" : ZmMsg.addDocuments);
	button.setToolTipContent(ZmMsg.addDocumentsTT);
	/***/

	var spellCheckButton = toolbar.getButton(ZmOperation.SPELL_CHECK);
	spellCheckButton.setAlign(DwtLabel.IMAGE_LEFT | DwtButton.TOGGLE_STYLE);
	if (AjxEnv.is800x600orLower) {
		spellCheckButton.setText("");
	}

	// NOTE: probably cleaner to use ZmActionMenu, which knows about operations
	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		var button = toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
		var menu = new ZmPopupMenu(button);
		var items = [
			{ op: ZmOperation.FORMAT_RICH_TEXT, format: ZmPageEditor.RICH_TEXT },
			{ op: ZmOperation.FORMAT_HTML_SOURCE, format: ZmPageEditor.HTML_SOURCE }
			/*** REVISIT: These will be exposed later.
			{ op: ZmOperation.FORMAT_MEDIA_WIKI, format: ZmPageEditor.MEDIA_WIKI },
			{ op: ZmOperation.FORMAT_TWIKI, format: ZmPageEditor.TWIKI }
			/***/
		];

		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			var op = item.op;

			var icon = ZmOperation.getProp(op, "image");
			var text = ZmMsg[ZmOperation.getProp(op, "textKey")];
			var style = DwtMenuItem.RADIO_STYLE;
			var group = ZmPageEditController.RADIO_GROUP[op];

			var menuItem = menu.createMenuItem(op, {image:icon, text:text, style:style, radioGroupId:group});
			menuItem.setData(ZmOperation.KEY_ID, op);
			menuItem.setData(ZmPageEditor.KEY_FORMAT, item.format);
			menuItem.addSelectionListener(this._listeners[ZmOperation.COMPOSE_FORMAT]);
		}

		button.setMenu(menu);
	}
};

ZmPageEditController.prototype._defaultView =
function() {
	return ZmController.NOTEBOOK_PAGE_EDIT_VIEW;
};

ZmPageEditController.prototype._createNewView =
function(view) {
	if (!this._pageEditView) {
		this._pageEditView = new ZmPageEditView(this._container, this._appCtxt, this);
	}
	return this._pageEditView;
};

ZmPageEditController.prototype._setView =
function(view, elements, isAppView, clear, pushOnly, isTransient) {
	ZmListController.prototype._setView.apply(this, arguments);

	this._format = this._format || ZmPageEditor.DEFAULT;

	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		var toolbar = this._toolbar[view];
		var button = toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
		var menu = button.getMenu();
		menu.checkItem(ZmPageEditor.KEY_FORMAT, this._format, true);
	}
};

ZmPageEditController.prototype._setViewContents =
function(view) {
	this._listView[view].set(this._page);
};

ZmPageEditController.prototype._getTagMenuMsg =
function() {
	return ZmMsg.tagPage;
};

ZmPageEditController.prototype._saveListener =
function(ev) {
	this._doSave(false);
};

ZmPageEditController.INVALID_DOC_NAME_CHARS = "[\\|]";
ZmPageEditController.INVALID_DOC_NAME_RE = new RegExp(ZmPageEditController.INVALID_DOC_NAME_CHARS);

ZmPageEditController.prototype._doSave =
function(popViewWhenSaved) {
	var name = this._pageEditView.getPageName();
	name = name.replace(/^\s+/,"").replace(/\s+$/,"");
	var message;
	if (name == "") {
		message = ZmMsg.errorSavingPageNameRequired;
	} else if (!ZmOrganizer.VALID_NAME_RE.test(name) || ZmPageEditController.INVALID_DOC_NAME_RE.test(name)) {
		message = AjxMessageFormat.format(ZmMsg.errorInvalidName, name);
	}

	// bug: 9406 (short term fix, waiting for backend support)
	var notebook = this._appCtxt.getById(this._page.folderId || ZmOrganizer.ID_NOTEBOOK);
	if (notebook && notebook.getChild(name)) {
		message = AjxMessageFormat.format(ZmMsg.errorInvalidPageOrSectionName, name);
	}

	if (message) {
		var style = DwtMessageDialog.WARNING_STYLE;
		var dialog = this.warngDlg = this._appCtxt.getMsgDialog();
		dialog.setMessage(message, style);
		dialog.popup();
	    dialog.registerCallback(DwtDialog.OK_BUTTON, this._focusPageInput, this);
		this._pageEditView.focus();
		return;
	}

	// set fields on page object
	var content = this._pageEditView.getContent(true);
	this._page.name = name;
	this._page.setContent(content);

	// save
	this._popViewWhenSaved = popViewWhenSaved;
	var saveCallback = new AjxCallback(this, this._saveResponseHandler, [content]);
	var saveErrorCallback = new AjxCallback(this, this._saveErrorResponseHandler, [content]);
	this._page.save(saveCallback, saveErrorCallback);
};

ZmPageEditController.prototype._focusPageInput = function() {
if(this.warngDlg){
	this.warngDlg.popdown();
}
this._pageEditView._pageNameInput.focus();
};

ZmPageEditController.prototype._showCurrentPage = function() {
	if (this._page && this._page.id) {
		this._showPage(this._page.id);
	}
};

ZmPageEditController.prototype._showPage = function(id) {
	var notebookController = this._app.getNotebookController();
	if (notebookController.getCurrentView()) {
		var cache = this._app.getNotebookCache();
		var page = cache.getPageById(id);
		notebookController.gotoPage(page);
	}
};

ZmPageEditController.prototype._saveResponseHandler = function(content, response) {
	var saveResp = response._data && response._data.SaveWikiResponse;
	if (saveResp) {
		var data = saveResp.w[0];
		if (!this._page.id) {
			this._page.set(data);
		}
		else {
			this._page.version = data.ver;
		}
	}

	// Update the cache if the page name changed.
	var cache = this._app.getNotebookCache();
	var cachedPage = cache.getPageById(this._page.id);
	if (cachedPage && (cachedPage.name != this._page.name)) {
		cache.renamePage(cachedPage, this._page.name);
	}

	this._pageEditView.pageSaved(content);
	this._appCtxt.setStatusMsg(ZmMsg.pageSaved);

	var popViewWhenSaved = this._popViewWhenSaved;

	var saveResp = response._data && response._data.SaveWikiResponse;
	var wiki = saveResp && saveResp.w && saveResp.w[0];
	var isRemote = /:/.test(wiki.id);
	if (isRemote) {
		wiki.l = this._page.folderId;
		wiki.name = this._page.name;

		var page = cachedPage;		
		if(page == null){
		page = new ZmPage(this._appCtxt);
		}		
		//page.set(wiki);
		page.name = this._page.name;
		page.version = this._page.version;
		var restUrl = this._page.restUrl;
		var parts = restUrl.split("/");
		if(parts && parts[parts.length-1]!=page.name){
			parts[parts.length-1] =  page.name;
			page.restUrl = parts.join("/");
		}
		cache.putPage(page);
	}
	if (popViewWhenSaved) {
		this._popViewWhenSaved = false;

		// NOTE: Need to let this call stack return and
		//       process the notifications.
		if (!isRemote) {
			var args = [ wiki.id ];
			var action = new AjxTimedAction(this, this._saveResponseHandlerShowNote, args);
			AjxTimedAction.scheduleAction(action, 0);
		}
		// NOTE: We don't get create notifications for remote items,
		//       so we force it to load and display.
		else {
			var args = [ wiki.id ];
			var callback = new AjxCallback(this, this._saveResponseHandlerShowNote, args);
			page.load(null, callback);
		}
	}
};

ZmPageEditController.prototype._saveResponseHandlerShowNote =
function(id) {
	this._showPage(id);
	this._appCtxt.getAppViewMgr().showPendingView(true);
};

ZmPageEditController.prototype._saveErrorResponseHandler =
function(content, response) {
	var code = response.code;
	if (code == ZmCsfeException.MAIL_ALREADY_EXISTS ||
		code == ZmCsfeException.MODIFY_CONFLICT) {
		var data = response.data;
		var conflict = {
			page: this._page,
			id: data["id"][0],
			version: data["ver"][0]
		};

		var dialog = this._appCtxt.getPageConflictDialog();
		var conflictCallback = new AjxCallback(this, this._saveConflictHandler, [content]);
		dialog.popup(conflict, conflictCallback);

		// tell app we've handled the error
		return true;
	}

	// let app handle other kinds of errors
	return false;
};
ZmPageEditController.prototype._saveConflictHandler =
function(content, mineOrTheirs, conflict) {
	if (mineOrTheirs == ZmPageConflictDialog.KEEP_MINE) {
		var page = conflict.page;
		page.id = ZmItem.getItemId(conflict.id);
		page.version = conflict.version;
		var saveCallback = new AjxCallback(this, this._saveResponseHandler, [content]);
		var saveErrorCallback = new AjxCallback(this, this._saveErrorResponseHandler, [content]);
		page.save(saveCallback, saveErrorCallback);
		return;
	}
	if (mineOrTheirs == ZmPageConflictDialog.KEEP_THEIRS) {
		return;
	}
};

ZmPageEditController.prototype._closeListener =
function(ev) {
	this._app.popView();
};

/***
ZmPageEditController.prototype._addDocsListener =
function(ev) {
	var notebook = this._appCtxt.getById(this._page.folderId || ZmNotebookItem.DEFAULT_FOLDER);
	var callback = null;

	var dialog = this._appCtxt.getUploadDialog();
	dialog.popup(notebook, callback);
};
/***/

ZmPageEditController.prototype._spellCheckListener =
function(ev) {
	var toolbar = this._toolbar[this._currentView];
	var spellCheckButton = toolbar.getButton(ZmOperation.SPELL_CHECK);
	var pageEditor = this._pageEditView.getPageEditor();

	if (spellCheckButton.isToggled()) {
		var callback = new AjxCallback(this, this.toggleSpellCheckButton)
		if (!pageEditor.spellCheck(callback))
			this.toggleSpellCheckButton(false);
	} else {
		pageEditor.discardMisspelledWords();
	}
};
ZmPageEditController.prototype.toggleSpellCheckButton =
function(toggled) {
	var toolbar = this._toolbar[this._currentView];
	var spellCheckButton = toolbar.getButton(ZmOperation.SPELL_CHECK);
	spellCheckButton.setToggled((toggled || false));
};

ZmPageEditController.prototype._formatListener = function(ev) {
	// popup menu
	var op = ev.item.getData(ZmOperation.KEY_ID);
	if (op == ZmOperation.COMPOSE_FORMAT) {
		var toolbar = this._toolbar[this._currentView];
		var button = toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
		button.popup();
		return;
	}

	// ignore de-selection
	if (ev.item.getChecked() == false) {
		return;
	}

	// handle selection
	var content = this._pageEditView.getContent();
	this._format = ev.item.getData(ZmPageEditor.KEY_FORMAT);
	this._pageEditView.setFormat(this._format);
	this._pageEditView.setContent(content);
};

ZmPageEditController.prototype._preHideCallback =
function(view, force) {
	ZmController.prototype._preHideCallback.call(this);
	if (force) {
		this._showCurrentPage();
		return true;
	}

	if (!this._pageEditView.isDirty()) {
		var notebookController = this._app.getNotebookController();
		if (notebookController.isIframeEnabled()) {
			notebookController.refreshCurrentPage();
		}else{
			this._showCurrentPage();
		}
		return true;
	}

	var ps = this._popShield = this._appCtxt.getYesNoCancelMsgDialog();
	ps.reset();
	ps.setMessage(ZmMsg.askToSave, DwtMessageDialog.WARNING_STYLE);
	ps.registerCallback(DwtDialog.YES_BUTTON, this._popShieldYesCallback, this);
	ps.registerCallback(DwtDialog.NO_BUTTON, this._popShieldNoCallback, this);
	ps.registerCallback(DwtDialog.CANCEL_BUTTON, this._popShieldDismissCallback, this);
	ps.popup(this._pageEditView._getDialogXY());
	return false;
};

ZmPageEditController.prototype._popShieldYesCallback =
function() {
	this._popShield.popdown();
	this._doSave(true);
};

ZmPageEditController.prototype._popShieldNoCallback =
function() {
	this._popShield.popdown();
	this._app.popView(true);
	this._showCurrentPage();
	this._appCtxt.getAppViewMgr().showPendingView(true);
};

ZmPageEditController.prototype._popShieldDismissCallback =
function() {
	this._popShield.popdown();
	this._appCtxt.getAppViewMgr().showPendingView(false);
};
