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

//////////////////////////////////////////////////////////////////////////////
// ZmContactSplitView
// - parent for the simple list view and xform view
//////////////////////////////////////////////////////////////////////////////
ZmContactSplitView = function(parent, className, posStyle, controller, dropTgt) {
	if (arguments.length == 0) return;

	className = className || "ZmContactSplitView";
	posStyle = posStyle || Dwt.ABSOLUTE_STYLE;
	DwtComposite.call(this, parent, className, posStyle);

	this._controller = controller;
	this._appCtxt = controller._appCtxt;

	this.setScrollStyle(Dwt.CLIP);

	var folderTree = this._appCtxt.getFolderTree();
	if (folderTree) {
		folderTree.addChangeListener(new AjxListener(this, this._addrbookTreeListener));
	}
	var tagTree = this._appCtxt.getTagTree();
	if (tagTree) {
		tagTree.addChangeListener(new AjxListener(this, this._tagChangeListener));
	}

	this._changeListener = new AjxListener(this, this._contactChangeListener);
	this._objectManager = new ZmObjectManager(null, this._appCtxt);

	this._initialize(controller, dropTgt);
};

ZmContactSplitView.prototype = new DwtComposite;
ZmContactSplitView.prototype.constructor = ZmContactSplitView;


// Consts
ZmContactSplitView.ALPHABET_HEIGHT = 35;

ZmContactSplitView.prototype.toString = 
function() {
	return "ZmContactSplitView";
};

ZmContactSplitView.prototype.getListView = 
function() {
	return this._listPart;
};

ZmContactSplitView.prototype.getController =
function() {
	return this._controller;
};

ZmContactSplitView.prototype.getAlphabetBar =
function() {
	return this._alphabetBar;
};

ZmContactSplitView.prototype.setSize =
function(width, height) {
	DwtComposite.prototype.setSize.call(this, width, height);
	this._sizeChildren(width, height);
};

ZmContactSplitView.prototype.setBounds = 
function(x, y, width, height) {
	DwtComposite.prototype.setBounds.call(this, x, y, width, height);
	this._sizeChildren(width, height);
};

ZmContactSplitView.prototype.getTitle =
function() {
	return [ZmMsg.zimbraTitle, ZmMsg.contacts].join(": ");
};

ZmContactSplitView.prototype.setContact =
function(contact, isGal) {

	if (this._objectManager) {
		this._objectManager.reset();
	}

	if (!isGal) {
		// Remove and re-add listeners for current contact if exists
		if (this._contact)
			this._contact.removeChangeListener(this._changeListener);
		contact.addChangeListener(this._changeListener);
	}

	var oldContact = this._contact;
	this._contact = contact;
	this._tabViewHtml = {};
	this._contactTabView.enable(true);
	// prevent listview from scrolling back up :/
	Dwt.CARET_HACK_ENABLED = false;
	this._contactTabView.switchToTab(1);
	Dwt.CARET_HACK_ENABLED = AjxEnv.isFirefox;

	if (this._contact.isLoaded) {
		this._setContact(contact, isGal, oldContact);
	} else {
		var callback = new AjxCallback(this, this._handleResponseLoad, [isGal, oldContact]);
		var errorCallback = new AjxCallback(this, this._handleErrorLoad);
		this._contact.load(callback, errorCallback);
	}
};

ZmContactSplitView.prototype._handleResponseLoad =
function(isGal, oldContact, resp, contact) {
	if (contact.id == this._contact.id) {
		this._setContact(this._contact, isGal, oldContact);
	}
};

ZmContactSplitView.prototype._handleErrorLoad =
function(ex) {
	this.clear();
	// TODO - maybe display some kind of error?
};

ZmContactSplitView.prototype.clear =
function() {
	// clear the right pane
	if (this._contactTabView.getVisible()) {
		this._tabViewHtml = {};
		var tabIdx = this._contactTabView.getCurrentTab();
		var view = this._contactTabView.getTabView(tabIdx);
		if (view) {
			view.getHtmlElement().innerHTML = "";
		}
		this._contactTabView.enable(false);
	} else {
		var groupDiv = document.getElementById(this._contactBodyId);
		if (groupDiv) {
			groupDiv.innerHTML = "";
		}
	}
	this._setHeaderInfo(true);
};

ZmContactSplitView.prototype.enableAlphabetBar =
function(enable) {
	this._alphabetBar.enable(enable);
};

ZmContactSplitView.prototype._initialize =
function(controller, dropTgt) {
	this.getHtmlElement().innerHTML = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#SplitView", {id:this._htmlElId});

	// alphabet bar based on *optional* existence in template and msg properties
	var alphaDivId = this._htmlElId + "_alphabetbar";
	var alphaDiv = document.getElementById(alphaDivId);
	if (alphaDiv && ZmMsg.alphabet && ZmMsg.alphabet.length>0) {
		this._alphabetBar = new ZmContactAlphabetBar(this, this._appCtxt);
		this._alphabetBar.reparentHtmlElement(alphaDivId);
	}

	// create listview based on *required* existence in template
	var listviewCellId = this._htmlElId + "_listview";
	var listviewCell = document.getElementById(listviewCellId);
	this._listPart = new ZmContactSimpleView(this, controller, dropTgt);
	this._listPart.reparentHtmlElement(listviewCellId);

	// define well-known Id's
	this._iconCellId	= this._htmlElId + "_icon";
	this._titleCellId	= this._htmlElId + "_title";
	this._tagCellId		= this._htmlElId + "_tags";
	this._headerRowId	= this._htmlElId + "_headerRow";
	this._contactBodyId = this._htmlElId + "_body";

	// create DwtTabGroup for contacts
	this._contactTabView = new DwtTabView(this, null, Dwt.STATIC_STYLE);
	this._contactTabView.addStateChangeListener(new AjxListener(this, this._tabStateChangeListener));
	this._contactTabView.reparentHtmlElement(this._htmlElId + "_tabs");

	// contact groups is not child of DwtTabGroup
	this._contactGroupView = new DwtComposite(this);
	this._contactGroupView.setVisible(false);
	this._contactGroupView.reparentHtmlElement(this._htmlElId + "_tabs");

	// add tabs to DwtTabGroup based on template
	var params = AjxTemplate.getParams("zimbraMail.abook.templates.Contacts#SplitView_tabs");
	var tabStr = params ? params["tabs"] : null;
	this._tabs = tabStr ? tabStr.split(",") : null;

	for (var i = 0; i < this._tabs.length; i++) {
		var tab = this._tabs[i] = AjxStringUtil.trim(this._tabs[i]);
		var idx = this._contactTabView.addTab(ZmMsg[tab]);
		var view = new DwtTabViewPage(this._contactTabView, "ZmContactTabViewPage");
		view._setAllowSelection();
		view.setScrollStyle(Dwt.SCROLL);

		// reset event handlers for each view so object manager can process
		view._setMouseEventHdlrs();
		view.addListener(DwtEvent.ONMOUSEOVER, 	new AjxListener(this._objectManager, this._objectManager._mouseOverListener));
		view.addListener(DwtEvent.ONMOUSEOUT, 	new AjxListener(this._objectManager, this._objectManager._mouseOutListener));
		view.addListener(DwtEvent.ONMOUSEDOWN, 	new AjxListener(this._objectManager, this._objectManager._mouseDownListener));
		view.addListener(DwtEvent.ONMOUSEUP, 	new AjxListener(this._objectManager, this._objectManager._mouseUpListener));
		view.addListener(DwtEvent.ONMOUSEMOVE, 	new AjxListener(this._objectManager, this._objectManager._mouseMoveListener));

		this._contactTabView.setTabView(idx, view);
	}

	this._tabViewHtml = {};
};

ZmContactSplitView.prototype._tabStateChangeListener =
function(ev) {
	this._setContact(this._contact, this._isGalSearch);
};

ZmContactSplitView.prototype._sizeChildren =
function(width, height) {
	var listviewCell = document.getElementById(this._htmlElId + "_listview");
/*
	var bounds = Dwt.getBounds(listviewCell);
	var insets = Dwt.getInsets(listviewCell);
	Dwt.insetBounds(bounds, insets);
*/
	var size = Dwt.getSize(listviewCell);
	this._listPart.setSize(size.x, size.y);
};

ZmContactSplitView.prototype._contactChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_CONTACT ||
		ev.source != this._contact ||
		ev.event == ZmEvent.E_DELETE)
	{
		return;
	}

	var tabIdx = this._contactTabView.getCurrentTab();
	this._tabViewHtml[tabIdx] = false;
	this._setContact(ev.source);
};

ZmContactSplitView.prototype._addrbookTreeListener =
function(ev, treeView) {
	if (!this._contact)
		return;

	var fields = ev.getDetail("fields");
	if (ev.event == ZmEvent.E_MODIFY && fields && fields[ZmOrganizer.F_COLOR]) {
		var organizers = ev.getDetail("organizers");
		if (!organizers && ev.source)
			organizers = [ev.source];

		for (var i = 0; i < organizers.length; i++) {
			var organizer = organizers[i];
			var folderId = this._contact.isShared()
				? this._appCtxt.getById(this._contact.folderId).id
				: this._contact.folderId;

			if (organizer.id == folderId)
				this._setHeaderInfo();
		}
	}
};

ZmContactSplitView.prototype._generateObject =
function(data, type) {
	return this._objectManager.findObjects(data, true, type);
};

ZmContactSplitView.prototype._setContact =
function(contact, isGal, oldContact) {
	if (!this._tabViewHtml) { return; }

	this._setHeaderInfo();

	var subs = {
		id: this._htmlElId,
		contact: contact
	};

	if (contact.isGroup())
	{
		subs.folderIcon = contact.addrbook.getIcon();
		subs.folderName = contact.addrbook.getName();
		subs.groupMembers = contact.getGroupMembers().good.getArray();

		this._contactTabView.setVisible(false);
		this._contactGroupView.setVisible(true);
		this._contactGroupView.getHtmlElement().innerHTML = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#SplitViewGroup", subs);
	}
	else
	{
		subs.view = this;
		subs.isGal = isGal;

		this._contactTabView.setVisible(true);
		this._contactGroupView.setVisible(false);

		// only render HTML for tab if we haven't already
		var tabIdx = this._contactTabView.getCurrentTab();
		if (!this._tabViewHtml[tabIdx]) {
			subs.tabIdx = tabIdx;
			var tabName = this._tabs[tabIdx-1];
			var template = "zimbraMail.abook.templates.Contacts#SplitView_" + tabName;
			var view = this._contactTabView.getTabView(tabIdx);
			view.getHtmlElement().innerHTML = AjxTemplate.expand(template, subs);

			this._tabViewHtml[tabIdx] = true;

			// notify zimlets that a new contact is being shown.
			if (this._appCtxt.zimletsPresent()) {
				this._appCtxt.getZimletMgr().notifyZimlets("onContactView", contact, this._htmlElId, tabIdx);
			}
		}
	}
};

ZmContactSplitView.prototype._getTagHtml =
function() {
	var html = [];
	var idx = 0;

	// get sorted list of tags for this msg
	var tagsList = this._contact.tags;
	var ta = [];
	for (var i = 0; i < tagsList.length; i++) {
		ta.push(this._appCtxt.getById(tagsList[i]));
	}
	ta.sort(ZmTag.sortCompare);

	for (var j = 0; j < ta.length; j++) {
		var tag = ta[j];
		if (!tag) { continue; }
		var icon = ZmTag.COLOR_MINI_ICON[tag.color];
		var attr = ["id='", this._tagCellId, tag.id, "'"].join("");
		// XXX: set proper class name for link once defined!
		html[idx++] = "<a href='javascript:;' class='' onclick='ZmContactSplitView._tagClicked(";
		html[idx++] = '"';
		html[idx++] = tag.id;
		html[idx++] = '"';
		html[idx++] = "); return false;'>"
		html[idx++] = AjxImg.getImageSpanHtml(icon, null, attr, tag.name);
		html[idx++] = "</a>&nbsp;";
	}
	return html.join("");
};

ZmContactSplitView.prototype._setHeaderInfo =
function(clear) {
	// set icon
	var iconCell = document.getElementById(this._iconCellId);
	if (iconCell) {
		iconCell.innerHTML = clear ? "" : AjxImg.getImageHtml(this._contact.getIcon());
	}

	// set title
	var titleCell = document.getElementById(this._titleCellId);
	if (titleCell) {
		titleCell.innerHTML = clear ? "" : this._contact.getFileAs();
	}

	// set tags
	var tagCell = document.getElementById(this._tagCellId);
	if (tagCell) {
		tagCell.innerHTML = clear ? "" : this._getTagHtml();
	}

	if (!clear) {
		// set background color of header
		var contactHdrRow = document.getElementById(this._headerRowId);
		if (contactHdrRow) {
			var folderId = this._contact.folderId;
			var folder = folderId ? this._appCtxt.getById(folderId) : null;
			var color = folder ? folder.color : ZmOrganizer.DEFAULT_COLOR[ZmOrganizer.ADDRBOOK];
			var bkgdColor = ZmOrganizer.COLOR_TEXT[color] + "Bg";
			contactHdrRow.className = folder && folder.isInTrash()
				? ("contactHeaderRow Trash " + bkgdColor)
				: ("contactHeaderRow " + bkgdColor);
		}
	}
};

ZmContactSplitView.prototype._tagChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_TAG)
		return;

	var fields = ev.getDetail("fields");
	var changed = fields && (fields[ZmOrganizer.F_COLOR] || fields[ZmOrganizer.F_NAME]);
	if ((ev.event == ZmEvent.E_MODIFY && changed) ||
		ev.event == ZmEvent.E_DELETE ||
		ev.event == ZmEvent.MODIFY)
	{
		var tagCell = document.getElementById(this._tagCellId);
		tagCell.innerHTML = this._getTagHtml();
	}
};

ZmContactSplitView._tagClicked =
function(tagId) {
	var appCtxt = window._zimbraMail._appCtxt;
	var sc = appCtxt ? appCtxt.getSearchController() : null;
	if (sc) {
		var tag = appCtxt.getById(tagId);
		var query = 'tag:"' + tag.name + '"';
		sc.search({query: query});
	}
};

//////////////////////////////////////////////////////////////////////////////
// ZmContactSimpleView
// - a simple contact list view (contains only full name)
//////////////////////////////////////////////////////////////////////////////
ZmContactSimpleView = function(parent, controller, dropTgt) {
	ZmContactsBaseView.call(this, parent, "ZmContactSimpleView", null, ZmController.CONTACT_SIMPLE_VIEW, controller, null, dropTgt);
};

ZmContactSimpleView.prototype = new ZmContactsBaseView;
ZmContactSimpleView.prototype.constructor = ZmContactSimpleView;

ZmContactSimpleView.prototype.toString = 
function() {
	return "ZmContactSimpleView";
};

ZmContactSimpleView.prototype.set =
function(list, defaultColumnSort) {
	ZmContactsBaseView.prototype.set.call(this, list, defaultColumnSort, this._controller.getFolderId());
	if (!(this._list instanceof AjxVector) || this._list.size() == 0) {
		this.parent.clear();
		var view = this._controller._getViewType();
		this._controller._navToolBar[view].setText("");
	}

	this.parent.enableAlphabetBar(!list.isGal);
};

ZmContactSimpleView.prototype._setNoResultsHtml =
function() {
	ZmContactsBaseView.prototype._setNoResultsHtml.call(this);
	this.parent.clear();
};

ZmContactSimpleView.prototype._changeListener =
function(ev) {
	ZmContactsBaseView.prototype._changeListener.call(this, ev);

	// bug fix #14874 - if moved to trash, show strike-thru
	var folderId = this._controller.getFolderId();
	if (!folderId && ev.event == ZmEvent.E_MOVE) {
		var contact = ev._details.items[0];
		var folder = this._appCtxt.getById(contact.folderId);
		var row = this._getElement(contact, ZmItem.F_ITEM_ROW);
		if (row) {
			row.className = folder && folder.isInTrash()
				? "Trash" : "";
		}
	}
};

ZmContactSimpleView.prototype._modifyContact =
function(ev) {
	ZmContactsBaseView.prototype._modifyContact.call(this, ev);

	if (ev.getDetail("fileAsChanged")) {
		var selected = this.getSelection()[0];
		this._layout();
		this.setSelection(selected, true);
	}
};

ZmContactSimpleView.prototype._layout =
function() {
	// explicitly remove each child (setting innerHTML causes mem leak)
	while (this._parentEl.hasChildNodes()) {
		cDiv = this._parentEl.removeChild(this._parentEl.firstChild);
		AjxCore.unassignId(cDiv._itemIndex);
	}

	var size = this._list.size();
	for (var i = 0; i < size; i++) {
		var item = this._list.get(i);
		var div = item ? this._createItemHtml(item, {now:this._now}) : null;
		if (div)
			this._addRow(div);
	}
};

/**
 * A contact is normally displayed in a list view with no headers, and shows
 * just an icon and name. The mixed list view has headers, and the row can
 * be built in the standard way.
 * 
 * @param contact	[ZmContact]		contact to display
 * @param params	[hash]*			optional params
 */
ZmContactSimpleView.prototype._createItemHtml =
function(contact, params) {

	if (params.isMixedView) {
		return ZmContactsBaseView.prototype._createItemHtml.apply(this, arguments);
	}

	var div = this._getDiv(contact, params);
	
	if (params.isDragProxy) {
		div.style.width = "175px";
		div.style.padding = "4px";
	}
	div.className = div[DwtListView._STYLE_CLASS] = div[DwtListView._STYLE_CLASS] + " SimpleContact";
	div[DwtListView._SELECTED_STYLE_CLASS] += " SimpleContact";
	div.id = this._getItemId(contact);

	var htmlArr = [];
	var idx = 0;

	// table/row
	idx = this._getTable(htmlArr, idx, params);
	idx = this._getRow(htmlArr, idx, contact, params);

	// checkbox selection
	if (this._appCtxt.get(ZmSetting.SHOW_SELECTION_CHECKBOX)) {
		htmlArr[idx++] = "<td style='vertical-align:middle;' width=20><center>";
		idx = this._getImageHtml(htmlArr, idx, "TaskCheckbox", this._getFieldId(contact, ZmItem.F_SELECTION));
		htmlArr[idx++] = "</center></td>";
	}

	// icon
	htmlArr[idx++] = "<td style='vertical-align:middle;' width=20><center>";
	htmlArr[idx++] = AjxImg.getImageHtml(contact.getIcon());
	htmlArr[idx++] = "</center></td>";

	// file as
	htmlArr[idx++] = "<td style='vertical-align:middle;'>&nbsp;";
	htmlArr[idx++] = AjxStringUtil.htmlEncode(contact.getFileAs());
	htmlArr[idx++] = "</td>";

	if (!params.isDragProxy) {
		// if read only, show lock icon in place of the tag column since we dont
		// currently support tags for "read-only" contacts (i.e. shares)
		if (contact.isReadOnly()) {
			htmlArr[idx++] = "<td width=16>";
			htmlArr[idx++] = AjxImg.getImageHtml("ReadOnly");
			htmlArr[idx++] = "</td>";
		} else if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
			// otherwise, show tag if there is one
			htmlArr[idx++] = "<td style='vertical-align:middle;' width=16 class='Tag'>";
			idx = this._getImageHtml(htmlArr, idx, contact.getTagImageInfo(), this._getFieldId(contact, ZmItem.F_TAG));
			htmlArr[idx++] = "</td>";
		}
	}
	htmlArr[idx++] = "</tr></table>";

	div.innerHTML = htmlArr.join("");

	return div;
};

// mixed view
ZmContactSimpleView.prototype._getCellContents =
function(htmlArr, idx, contact, field, colIdx, params) {
	if (field == ZmItem.F_FROM) {
		// Name (fileAs)
		htmlArr[idx++] = AjxStringUtil.htmlEncode(contact.getFileAs());
	} else if (field == ZmItem.F_SUBJECT) {
		// Company
		htmlArr[idx++] = AjxStringUtil.htmlEncode(contact.getCompanyField());
	} else if (field == ZmItem.F_DATE) {
		htmlArr[idx++] = AjxDateUtil.computeDateStr(params.now, contact.modified);
	} else {
		idx = ZmContactsBaseView.prototype._getCellContents.apply(this, arguments);
	}
	
	return idx;
};

ZmContactSimpleView.prototype._getToolTip =
function(field, item, ev) {
	return (item && (field == ZmItem.F_FROM)) ? item.getToolTip(item.getAttr(ZmContact.F_email)) :
												ZmContactsBaseView.prototype._getToolTip.apply(this, arguments);
};

ZmContactSimpleView.prototype._getDateToolTip = 
function(item, div) {
	div._dateStr = div._dateStr || this._getDateToolTipText(item.modified, ["<b>", ZmMsg.lastModified, ":</b><br>"].join(""));
	return div._dateStr;
};
