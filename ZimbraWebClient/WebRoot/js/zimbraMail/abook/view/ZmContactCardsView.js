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

ZmContactCardsView = function(parent, className, posStyle, controller, dropTgt) {

	className = className ? className : "ZmContactCardsView";
	posStyle = posStyle ? posStyle : Dwt.ABSOLUTE_STYLE;
	ZmContactsBaseView.call(this, parent, className, posStyle, ZmController.CONTACT_CARDS_VIEW, controller, null, dropTgt);

	this._setMouseEventHdlrs(); // needed by object manager
	// this manages all the detected objects within the view
	this._objectManager = new ZmObjectManager(this, this.shell.getData(ZmAppCtxt.LABEL));

	// find out if the user's locale has a alphabet defined
	if (ZmMsg.alphabet && ZmMsg.alphabet.length>0) {
		this._alphabetBar = new ZmContactAlphabetBar(this, this._appCtxt);
	}

	this.addControlListener(new AjxListener(this, this._controlListener));

	this._addrbookTree = this._appCtxt.getFolderTree();
	this._addrbookTree.addChangeListener(new AjxListener(this, this._addrbookTreeListener));

	this._htmlAttrs = this._getHtmlAttributes();
	this._dndAttrs = this._getHtmlAttributes(true);

	this._initialResized = false;
};

ZmContactCardsView.prototype = new ZmContactsBaseView;
ZmContactCardsView.prototype.constructor = ZmContactCardsView;

ZmContactCardsView.CARD_NAME = Dwt.getNextId();
ZmContactCardsView.CARD_TABLE_ID = Dwt.getNextId();

// Public methods

ZmContactCardsView.prototype.toString = 
function() {
	return "ZmContactCardsView";
};

ZmContactCardsView.prototype.paginate = 
function(contacts, bPageForward) {
	ZmContactsBaseView.prototype.paginate.call(this, contacts, bPageForward);
	this._layout();
	this.setSelection(contacts.getVector().get(this.getOffset()));
};

ZmContactCardsView.prototype.replenish = 
function(list) {
	ZmContactsBaseView.prototype.replenish.call(this, list);
	this._layout();
};

// lets just try to optimally layout all the cards by not letting base class do its thing
ZmContactCardsView.prototype.setUI =
function(defaultColumnSort) {
	// do nothing
};

ZmContactCardsView.prototype.set = 
function(contacts, sortField, folderId) {
	if (this._objectManager)
		this._objectManager.reset();

	// XXX: optimize later - switch view always forces layout unnecessarily
	ZmContactsBaseView.prototype.set.call(this, contacts, sortField, this._controller.getFolderId());

	if (this._initialResized)
		this._layout();

	// disable alphabet bar for gal searches
	this._alphabetBar.enable(!contacts.isGal);
};

ZmContactCardsView.prototype.getAlphabetBar =
function() {
	return this._alphabetBar;
};


// Private / protected methods

ZmContactCardsView.prototype._generateObject =
function(data, type) {
	return this._objectManager.findObjects(data, true, type);
};

ZmContactCardsView.prototype._createItemHtml =
function(contact, params) {

	var isDnd = !!(params && params.isDragProxy);
	var attrs = isDnd ? this._dndAttrs : this._htmlAttrs;
	var color = contact.addrbook
		? contact.addrbook.color
		: ZmOrganizer.DEFAULT_COLOR[ZmOrganizer.ADDRBOOK];
	var tagCellId;
	var tagIcon;
	if (this._appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		tagCellId = this._getFieldId(contact, ZmItem.F_TAG_CELL);
		var fieldId = this._getFieldId(contact, ZmItem.F_TAG);
		tagIcon = AjxImg.getImageHtml(contact.getTagImageInfo(), null, ["id='", fieldId, "'"].join(""));
	}
	var groupMembers = contact.isGroup() ? contact.getGroupMembers().good.getArray() : null;

	var subs = {
		id: this._getItemId(contact),
		attrs: attrs,
		width: this._cardWidth,
		itemIndex: AjxCore.assignId(contact),
		headerColor: (ZmOrganizer.COLOR_TEXT[color] + "Bg"),
		tagCellId: tagCellId,
		tagIcon: tagIcon,
		groupMembers: groupMembers,
		isDnd: isDnd,
		view: this,
		contact: contact
	};
	var html = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#CardBase", subs);

	return isDnd
		? Dwt.parseHtmlFragment(html)
		: html;
};

ZmContactCardsView.prototype._layout =
function() {
	this._resetListView();

	if (this._list instanceof AjxVector && this._list.size())
	{
		var list = this._list.getArray();
		var subs = {
			id: this._htmlElId,
			cardTableId: ZmContactCardsView.CARD_TABLE_ID,
			list: list
		};
		html = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#CardsView", subs);
		this.getHtmlElement().appendChild(Dwt.parseHtmlFragment(html));

		for (var i = 0; i < list.length; i++) {
			var contact = list[i];
			var cid = this._htmlElId + "_contact_" + contact.id;
			var el = document.getElementById(cid);
			if (el) {
				el.innerHTML = this._createItemHtml(contact);
			}
		}
	}
	else
	{
		var subs = {
			id: ZmContactCardsView.CARD_TABLE_ID
		};
		html = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#CardsView-NoResults", subs);
		this.getHtmlElement().appendChild(Dwt.parseHtmlFragment(html));
	}
};

ZmContactCardsView.prototype._getHtmlAttributes =
function(isDnd) {
	var base = "ZmContactCard";

	var attrs = {};
	attrs.name = ZmContactCardsView.CARD_NAME;
	attrs[DwtListView._STYLE_CLASS] = attrs['class'] = isDnd ? ([base, DwtCssStyle.DRAG_PROXY].join("-")) : base;
	attrs[DwtListView._SELECTED_STYLE_CLASS] = [base, DwtCssStyle.SELECTED].join("-");
	attrs[DwtListView._KBFOCUS_CLASS] = [base, DwtCssStyle.FOCUSED].join("-");
	attrs['_type'] = DwtListView.TYPE_LIST_ITEM;

	var attrList = [];
	for (var attr in attrs) {
		attrList.push([attr, "='", attrs[attr], "'"].join(""));
	}

	return (attrList.join(" "));
};

ZmContactCardsView.prototype._setNoResultsHtml =
function() {
	// overload and ignore
};

// overload this protected method so we can keep the alphabet bar around
ZmContactCardsView.prototype._resetListView =
function() {
	var cards = document.getElementsByName(ZmContactCardsView.CARD_NAME);
	var cDiv;

	// explicitly remove each child (setting innerHTML causes mem leak)
	for (var i = 0; i < cards.length; i++) {
		cDiv = cards[0].parentNode.removeChild(cards[0]);
		AjxCore.unassignId(Dwt.getAttr(cDiv, "_itemIndex"));
	}

	var cardTable = document.getElementById(ZmContactCardsView.CARD_TABLE_ID);
	if (cardTable) {
		cardTable.parentNode.removeChild(cardTable);
	}
};

ZmContactCardsView.prototype._modifyContact =
function(ev) {
	// always call base class first to resort list if necessary
	ZmContactsBaseView.prototype._modifyContact.call(this, ev);

	// XXX: opitimize later - always re-layout no matter which field changed
	this._resetListView();
	this._layout();
};

// we overload this base class method since cards view is unconventional
ZmContactCardsView.prototype._getElFromItem = 
function(item) {
	var children = document.getElementsByName(ZmContactCardsView.CARD_NAME);
	var comparisonId = this._getItemId(item);

	for (var i = 0; i < children.length; i++) {
		if (children[i].id == comparisonId)
			return children[i];
	}
	return null;
};

ZmContactCardsView.prototype._setDragProxyState =
function(dropAllowed) {
	if (this._dndImg || !AjxEnv.isLinux) {
		ZmContactsBaseView.prototype._setDragProxyState.call(this, dropAllowed)
	} else if (this._dndProxy) {
		// bug fix #3235 - no opacity for linux
		var addClass = dropAllowed ? DwtCssStyle.DROPPABLE : DwtCssStyle.NOT_DROPPABLE;
		var linuxClass = [addClass, DwtCssStyle.LINUX].join("-");
		this._dndProxy.className = [this._dndProxy._origClassName, linuxClass].join(" ");
	}
};

ZmContactCardsView.prototype._handleResponseLoad =
function(result, contact) {
	var div = document.getElementById(this._getItemId(contact));
	var html = this._createItemHtml(contact);
	var newDiv = Dwt.parseHtmlFragment(html);
	div.innerHTML = newDiv.innerHTML;
};

ZmContactCardsView.prototype._getSiblingElement =
function(element, next){
	var item = this.getItemFromElement(this._kbAnchor);
	if (!item) return element;
	var index = this._list.indexOf(item);
	if ((next && (index >= this._list.size() - 1)) || (!next && index <= 0)) return element;
	index = next ? index + 1 : index - 1;
	var id = this._getItemId(this._list.get(index));
	var el = document.getElementById(id);
	return el ? el : element;
};


// Listeners

ZmContactCardsView.prototype._mouseDownListener = 
function(ev) {
	var div = ev.target;

	// bug fix #4595 - dont process mouse down for objects
	if (div.id.indexOf("OBJ_") == 0) {
		this._dragOp = Dwt.DND_DROP_NONE;
		this._dragging = DwtControl._NO_DRAG;
	} else {
		// otherwise, just call base class
		DwtListView.prototype._mouseDownListener.call(this, ev);
	}
};

ZmContactCardsView.prototype._changeListener =
function(ev) {
	// need custom handling for delete (can't just remove row)
	if (ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE) {
		var items = ev.getDetail("items");
		for (var i = 0; i < items.length; i++) {
			this._list.remove(items[i]);
		}
		this._layout();
	} else {
		ZmContactsBaseView.prototype._changeListener.call(this, ev);
	}
	this.setSelection(this.getList().get(0));
};

ZmContactCardsView.prototype._controlListener =
function(ev) {
	if (ev.newWidth < 0 || ev.oldWidth == ev.newWidth)
		return;

	// calc. width of card based on width of window
	this._cardWidth = Math.round((ev.newWidth / 2) - 40);

	this._initialResized = true;
	this._layout();
};

ZmContactCardsView.prototype._addrbookTreeListener =
function(ev, treeView) {
	var fields = ev.getDetail("fields");
	if (ev.event == ZmEvent.E_MODIFY && fields && fields[ZmOrganizer.F_COLOR]) {
		this._layout();
	}
};

// Static methods

ZmContactCardsView.getPrintHtml =
function(list) {

	var html = [];
	var idx = 0;
	var list = list.getArray();

	html[idx++] = "<table border=0 style='width:6.5in'>";

	for (var i = 0; i < list.length; i++) {
		var contact = list[i];

		// dont include contacts in trash folder
		if (contact.addrbook && contact.addrbook.isInTrash())
			continue;

		// add a new row every 3 columns
		if ((i % 3) == 0)
			html[idx++] = "<tr>";
		html[idx++] = "<td valign=top height=100%>";

		html[idx++] = "<div style='height:100%; width:2.2in; border:1px solid #CCCCCC; overflow-x:hidden'>";
		html[idx++] = contact.isGroup()
			? ZmGroupView.getPrintHtml(contact, true)
			: ZmContactView.getPrintHtml(contact, true);
		html[idx++] = "</div>";

		html[idx++] = "</td>";
		if (((i+1) % 3) == 0)
			html[idx++] = "</tr>";
	}

	if ((i % 3) != 0)
		html[idx++] = "</tr>";

	html[idx++] = "</table>";

	return html.join("");
};

ZmContactCardsView._loadContact =
function(cell, contactId) {
	var appCtxt = window._zimbraMail._appCtxt;
	var contact = appCtxt.cacheGet(contactId);
	if (contact && !contact.isLoaded) {
		var clc = AjxDispatcher.run("GetContactListController");
		var cardsView = clc.getParentView();
		var callback = new AjxCallback(cardsView, cardsView._handleResponseLoad);
		contact.load(callback);
	}

	// this looks weird, but we're just removing the table row
	cell.parentNode.parentNode.removeChild(cell.parentNode);
};

ZmContactCardsView._moreDetailsCallback =
function(contactId) {
	var appCtxt = window.parentController
		? window.parentController._appCtxt
		: window._zimbraMail._appCtxt;

	var contact = AjxDispatcher.run("GetContacts").getById(contactId);
	AjxDispatcher.run("GetContactController").show(contact);
};
