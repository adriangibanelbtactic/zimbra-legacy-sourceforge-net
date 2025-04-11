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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

ZmLinkPropsDialog = function(appCtxt, shell, className) {
	className = className || "ZmLinkPropsDialog";
	DwtDialog.call(this, shell, className, ZmMsg.linkProperties);
	this.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(this, this._handleOkButton));

	this._appCtxt = appCtxt;

	this._cache = AjxDispatcher.run("GetNotebookCache");

	// set view
	this.setView(this._createView());
};

ZmLinkPropsDialog.prototype = new DwtDialog;
ZmLinkPropsDialog.prototype.constructor = ZmLinkPropsDialog;

// Public methods

ZmLinkPropsDialog.prototype.popup =
function(linkInfo, callback) {
	this._linkInfo = linkInfo || {};
	this._callback = callback;

	var isUrlLink = this._linkInfo.url;
	if (this._appCtxt.get(ZmSetting.NOTEBOOK_ENABLED)) {
		var root = this._appCtxt.getById(ZmOrganizer.ID_ROOT);
		var children = root.children.getArray();

		this._notebookSelect.clearOptions();
		this.__addNotebookChildren(children, "");
		this.notebookChangeListener();
		
		this._pageRadioEl.checked = !isUrlLink;
		this._urlRadioEl.checked = isUrlLink;

		this._pageInput.setValue("");
	}

	this._urlInput.setValue(this._linkInfo.url || "http://www.");
	this._titleInput.setValue(this._linkInfo.text || "");

	ZmLinkPropsDialog._setRequiredFields(this, !isUrlLink);

	DwtDialog.prototype.popup.call(this);
	ZmLinkPropsDialog._enableFieldsOnEdit(this);
};

ZmLinkPropsDialog.prototype.popdown =
function() {
	if (this._acPageList) {
		//this._acPageList.reset();
		this._acPageList.show(false);
	}
	DwtDialog.prototype.popdown.call(this);
};

// Tab completion methods

ZmLinkPropsDialog.prototype.getPageDataLoader =
function() {
	return this;
};

ZmLinkPropsDialog.prototype.autocompleteMatch =
function(s) {
	var notebookId = this._notebookSelect.getValue();
	var pages = this._cache.getPagesInFolder(notebookId);

	var m = [];
	var lows = s.toLowerCase();
	for (var p in pages) {
		var lowp = p.toLowerCase();
		var index = lowp.indexOf(lows);
		if (index != -1) {
			var page = pages[p];
			var object = {
				text: [
					p.substr(0,index),
					"<B>",p.substr(index, s.length),"</B>",
					p.substr(index + s.length)
				].join(""),
				name: p,
				data: page
			};
			m.push(object);
		}
	}
	return m;
};

ZmLinkPropsDialog.prototype.isUniqueValue =
function(s) {
	// NOTE: Don't auto fill in single match. This will allow them
	//       to enter something that doesn't exist yet (but perhaps
	//       intend to create later).
	return false;
};

ZmLinkPropsDialog.prototype._getAcPageLoc =
function() {
	var element = this._pageInput.getHtmlElement();
	var viewEl = this.getHtmlElement();
	var location = Dwt.toWindow(element, 0, 0, viewEl);
	var size = Dwt.getSize(element);
	return new DwtPoint((location.x), (location.y + size.y));
};

ZmLinkPropsDialog.prototype._setAcPageCompletion =
function(text, element, matchObj) {
	// NOTE: nothing special to be done
};

ZmLinkPropsDialog.prototype._acKeyUpListener =
function(event) {
	this._pageInput.validate();
	ZmLinkPropsDialog._enableFieldsOnEdit(this);
};

// Protected functions

ZmLinkPropsDialog._handleEdit =
function(event) {
	var target = DwtUiEvent.getTarget(event);
	var dialog = Dwt.getObjectFromElement(target);
	ZmLinkPropsDialog._enableFieldsOnEdit(dialog);
	return true;
};

ZmLinkPropsDialog._handleKeyUp = function(event){
	if (DwtInputField._keyUpHdlr(event)) {
		var target = DwtUiEvent.getTarget(event);
		var inputField = Dwt.getObjectFromElement(target);
		var dialog = inputField.parent.parent;
		return ZmLinkPropsDialog._enableFieldsOnEdit(dialog);
	}
	return false;
};

ZmLinkPropsDialog._handleLinkTo =
function(event) {
	var target = DwtUiEvent.getTarget(event);
	var dialog = Dwt.getObjectFromElement(target);
	var isPage = target == dialog._pageRadioEl;
	ZmLinkPropsDialog._setRequiredFields(dialog, isPage);
	ZmLinkPropsDialog._enableFieldsOnEdit(dialog);
	return true;
};

ZmLinkPropsDialog._handleUrlTest = function(event) {
	var target = DwtUiEvent.getTarget(event);
	var dialog = Dwt.getObjectFromElement(target);

	var winurl = dialog._urlInput.getValue();
	var winname = "_new";
	var winfeatures = [
		"width=",(window.outerWidth || 640),",",
		"height=",(window.outerHeight || 480),",",
		"location,menubar,",
		"resizable,scrollbars,status,toolbar"
	].join("");

	var win = open(winurl, winname, winfeatures);
};

ZmLinkPropsDialog._enableFieldsOnEdit =
function(dialog) {
	var enabled = false;
	if (dialog._pageRadioEl && dialog._pageRadioEl.checked) {
		enabled = dialog._pageInput.getValue().replace(/^\s+(.*)\s+$/,"$1") != "";
	}
	else {
		enabled = dialog._urlInput.getValue().replace(/^\s+(.*)\s+$/,"$1") != "";
	}
	dialog.setButtonEnabled(DwtDialog.OK_BUTTON, enabled);
};

ZmLinkPropsDialog._setRequiredFields =
function(dialog, isPage) {
	if (dialog._pageInput) {
		dialog._pageInput.setRequired(isPage);
	}
	dialog._urlInput.setRequired(!isPage);
};

// Protected methods

ZmLinkPropsDialog.prototype._handleOkButton =
function(event) {
	this.popdown();
	if (this._callback) {
		var title = this._titleInput.getValue().replace(/^\s+(.*)\s+$/,"$1");
		var link;
		if (this._pageRadioEl && this._pageRadioEl.checked) {
			var notebookId = this._notebookSelect.getValue();
			var notebook = this._appCtxt.getById(notebookId);
			var value = AjxStringUtil.trim(this._pageInput.getValue());
			link = [
				"[[",
					"/",notebook.getSearchPath(),"/",value,
					(title ? "|" : ""),title,
				"]]"
			].join("");
		}
		else {
			var doc = this._linkInfo.document || document;
			link = doc.createElement("A");
			link.href = AjxStringUtil.trim(this._urlInput.getValue());
			link.innerHTML = AjxStringUtil.htmlEncode(title || link.href);
		}

		this._callback.run(link, this._linkInfo.target);
	}
};

ZmLinkPropsDialog.prototype._createView =
function() {
	// TODO: only show url link if notebook app is disabled
	var view = new DwtComposite(this);
	var inputParams = {
		parent: view,
		type: DwtInputField.STRING,
		validationStyle: DwtInputField.CONTINUAL_VALIDATION
	}

	// create common DWT controls
	this._urlInput = new DwtInputField(inputParams);
	this._urlInput.setRequired(true);
	var urlInputEl = this._urlInput.getInputElement();
	Dwt.setHandler(urlInputEl, DwtEvent.ONKEYUP, ZmLinkPropsDialog._handleKeyUp);
	this._titleInput = new DwtInputField(inputParams);

	// setup dialog controls for notebook
	if (this._appCtxt.get(ZmSetting.NOTEBOOK_ENABLED)) {
		// create ids
		var typePageId = Dwt.getNextId();
		var typeUrlId = Dwt.getNextId();
		var pagePropsId = Dwt.getNextId();
		var notebookId = Dwt.getNextId();
		var pageId = Dwt.getNextId();
		var pageSelectId = Dwt.getNextId();
		var urlInputId = Dwt.getNextId();
		var urlTestId = Dwt.getNextId();
		var titleInputId = Dwt.getNextId();

		var typeName = this._htmlElId+"_type";

		// link
		var linkHtml = [
			"<table border=0 cellpadding=0 cellspacing=0>",
				"<tr valign=top>",
					"<td rowspan=2>",
						"<input id='",typePageId,"' type=radio name='",typeName,"'>",
					"</td>",
					"<td>",ZmMsg.notebookPageLabel,"</td>",
				"</tr>",
				"<tr>",
					"<td id='",pagePropsId,"'></td>",
				"</tr>",
				"<tr valign=top>",
					"<td rowspan=2>",
						"<input id='",typeUrlId,"' type=radio name='",typeName,"'>",
					"</td>",
					"<td>",ZmMsg.webPageLabel,"</td>",
				"</tr>",
				"<tr>",
					"<td>",
						"<nobr id='",urlInputId,"'></nobr>",
					"</td>",
				"</tr>",
			"</table>"
		].join("");

		// create dwt controls
		this._notebookSelect = new DwtSelect(view);
		var notebookChange = new AjxListener(this,this.notebookChangeListener);
		this._notebookSelect.addChangeListener(notebookChange);
		this._pageSelect = new DwtSelect(view);
		var pageChange = new AjxListener(this,this.pageChangeListener);
		this._pageSelect.addChangeListener(pageChange);
		this._pageInput = new DwtInputField(inputParams);
		this._pageInput.setRequired(true);

		var pageInputEl = this._pageInput.getInputElement();
		pageInputEl.style.width = "15em";

		var inputEl = this._urlInput.getInputElement();
		inputEl.style.width = "25em";

		var inputEl = this._titleInput.getInputElement();
		inputEl.style.width = "20em";

		var linkToGroup = new DwtGrouper(view);
		linkToGroup.setLabel(ZmMsg.linkTo);
		linkToGroup.setContent(linkHtml);

		var pageProps = new DwtPropertySheet(linkToGroup);
		pageProps.addProperty(ZmMsg.notebookLabel, "<div id='"+notebookId+"'></div>");
		pageProps.addProperty(ZmMsg.pageLabel, "<div id='"+pageSelectId+"'></div>");
		pageProps.addProperty(ZmMsg.pageNameLabel, "<div id='"+pageId+"'></div>");

		// insert dwt controls
		var pagePropsEl = document.getElementById(pagePropsId);
		pagePropsEl.appendChild(pageProps.getHtmlElement());

		var notebookEl = document.getElementById(notebookId);
		notebookEl.appendChild(this._notebookSelect.getHtmlElement());

		var pageSelectEl = document.getElementById(pageSelectId);
		pageSelectEl.appendChild(this._pageSelect.getHtmlElement());

		var pageEl = document.getElementById(pageId);
		pageEl.parentNode.replaceChild(this._pageInput.getHtmlElement(), pageEl);

		var table = document.createElement("TABLE");
		table.border = 0;
		table.cellSpacing = 10;
		table.cellpadding = 0;
		var row = table.insertRow(-1);
		var cell = row.insertCell(-1);
		cell.appendChild(this._urlInput.getHtmlElement());
		var cell = row.insertCell(-1);
		cell.innerHTML = [
			"<span ",
				"id='",urlTestId,"' ",
				"onmouseover='this.style.cursor=\"pointer\"' ",
				"onmouseout='this.style.cursor=\"default\"' ",
				"style='color:blue;text-decoration:underline;'",
			">", ZmMsg.testUrl, "</span>"
		].join("");
		var urlTestEl = cell.firstChild;
		Dwt.setHandler(urlTestEl, DwtEvent.ONCLICK, ZmLinkPropsDialog._handleUrlTest);
		Dwt.associateElementWithObject(urlTestEl, this);

		var urlEl = document.getElementById(urlInputId);
		urlEl.parentNode.replaceChild(table, urlEl);

		// save HTML controls
		this._pageRadioEl = document.getElementById(typePageId);
		this._pageRadioEl.checked = true;
		this._urlRadioEl = document.getElementById(typeUrlId);

		var radioEls = [ this._pageRadioEl, this._urlRadioEl ];
		for (var i = 0; i < radioEls.length; i++) {
			Dwt.setHandler(radioEls[i], DwtEvent.ONCLICK, ZmLinkPropsDialog._handleLinkTo);
			Dwt.associateElementWithObject(radioEls[i], this);
		}

		// setup auto-completer
		var dataClass = this;
		var dataLoader = this.getPageDataLoader;
		var locCallback = new AjxCallback(this, this._getNewAutocompleteLocation);
		var compCallback = new AjxCallback(this, this._setAcPageCompletion);

		var params = {
			parent: this,
			dataClass: dataClass,
			dataLoader: dataLoader,
			matchValue: "name",
			separator: "",
			locCallback: new AjxCallback(this, this._getAcPageLoc),
			compCallback: new AjxCallback(this, this._setAcPageCompletion),
			keyUpCallback: new AjxCallback(this, this._acKeyUpListener)
		}
		this._acPageList = new ZmAutocompleteListView(params);
		this._acPageList.handle(pageInputEl);
		//Dwt.setHandler(pageInputEl, DwtEvent.ONKEYUP, ZmLinkPropsDialog._handleKeyUp);

	} // if NOTEBOOK_ENABLED

	// create properties
	var props = new DwtPropertySheet(view);
	if (!this._appCtxt.get(ZmSetting.NOTEBOOK_ENABLED)) {
		props.addProperty(linkUrlLabel, this._urlInput);
	}
	props.addProperty(ZmMsg.linkTitleLabel, this._titleInput);

	return view;
};

// Private methods

ZmLinkPropsDialog.prototype.__addNotebookChildren =
function(children, depth) {
	if (!children) return;

	for (var i = 0; i < children.length; i++) {
		var child = children[i];
		//this._notebookSelect.addOption(depth+child.name, false, child.id);
		if(child instanceof ZmNotebook ){
		this._notebookSelect.addOption(child.getSearchPath(), false, child.id);
		var grandChildren = child.children.getArray();
		this.__addNotebookChildren(grandChildren, depth+"&nbsp;");
		}
	}
};

ZmLinkPropsDialog.prototype.notebookChangeListener =
function(s) {
	var notebookId = this._notebookSelect.getValue();
	this._pageSelect.clearOptions();
	this._pageSelect.setText("Loading...");
	var pages = this._cache.getPagesInFolder(notebookId);
	var isEmpty = true;
	for (var p in pages) {
		this._pageSelect.addOption(p, false, p);
		isEmpty = false;
	}	
	if(isEmpty){
		this._pageSelect.setText(ZmMsg.notAvailable);
		this._pageSelect.disable();
	}else{
		this._pageSelect.enable();
	}
	this.pageChangeListener();
};

ZmLinkPropsDialog.prototype.pageChangeListener =
function(s) {
	var pageName = this._pageSelect.getValue();
	this._pageInput.setValue(pageName);
	ZmLinkPropsDialog._enableFieldsOnEdit(this);
};
