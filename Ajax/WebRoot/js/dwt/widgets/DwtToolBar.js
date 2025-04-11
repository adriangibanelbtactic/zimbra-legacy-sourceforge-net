/*
 * Copyright (C) 2006, The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


DwtToolBar = function(parent, className, posStyle, cellSpacing, cellPadding, width, style) {

	if (arguments.length == 0) return;
	className = className || "ZToolbar";
	DwtComposite.call(this, parent, className, posStyle);
	
	this._style = style || DwtToolBar.HORIZ_STYLE;
    this._items = [];
    this._createHtml();

    this._numFillers = 0;
	this._curFocusIndex = 0;

	var suffix = (this._style == DwtToolBar.HORIZ_STYLE) ? "horiz" : "vert";
	this._keyMapName = ["DwtToolBar", suffix].join("-");
};

DwtToolBar.prototype = new DwtComposite;
DwtToolBar.prototype.constructor = DwtToolBar;

DwtToolBar.prototype.toString =
function() {
	return "DwtToolBar";
};

//
// Constants
//

DwtToolBar.HORIZ_STYLE	= 1;
DwtToolBar.VERT_STYLE	= 2;

DwtToolBar.ELEMENT		= 1;
DwtToolBar.SPACER		= 2;
DwtToolBar.SEPARATOR	= 3;
DwtToolBar.FILLER		= 4;

//
// Data
//

// main template

DwtToolBar.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbar";

// item templates

DwtToolBar.prototype.ITEM_TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbarItem";
DwtToolBar.prototype.SEPARATOR_TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbarSeparator";
DwtToolBar.prototype.SPACER_TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbarSpacer";
DwtToolBar.prototype.FILLER_TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbarFiller";

// static data

DwtToolBar.__itemCount = 0;

//
// Public methods
//

DwtToolBar.prototype.getItem =
function(index) {
	return this._children.get(index);
};

DwtToolBar.prototype.getItemCount =
function() {
	return this._children.size();
};

DwtToolBar.prototype.getItems =
function() {
	return this._children.getArray();
};

// item creation

DwtToolBar.prototype.addSpacer = function(size, index) {
    var el = this._createSpacerElement();
	this._addItem(DwtToolBar.SPACER, el, index);
	return el;
};

DwtToolBar.prototype.addSeparator = function(className, index) {
	var el = this._createSeparatorElement();
	this._addItem(DwtToolBar.SEPARATOR, el, index);
	return el;
};

DwtToolBar.prototype.addFiller = function(className, index) {
	var el = this._createFillerElement();
	this._addItem(DwtToolBar.FILLER, el, index);
	return el;
};

// DwtComposite methods

DwtToolBar.prototype.addChild =
function(child, index) {
    DwtComposite.prototype.addChild.apply(this, arguments);

    var itemEl = this._createItemElement();
    var htmlEl = child._removedEl ? child._removedEl : child.getHtmlElement();
    itemEl.appendChild(htmlEl);
    
    this._addItem(DwtToolBar.ELEMENT, itemEl, index);
};

// keyboard nav

DwtToolBar.prototype.getKeyMapName =
function() {
    return this._keyMapName;
};

DwtToolBar.prototype.handleKeyAction =
function(actionCode, ev) {

	var item = this.getItem(this._curFocusIndex);
	var numItems = this.getItemCount();
	if (numItems < 2) {
		return true;
	}

	switch (actionCode) {

		case DwtKeyMap.PREV:
			if (this._curFocusIndex > 0) {
				this._moveFocus(true);
			}
			break;

		case DwtKeyMap.NEXT:
			if (this._curFocusIndex < (numItems - 1)) {
				this._moveFocus();
			}
			break;

		default:
			// pass everything else to currently focused item
			if (item) {
				return item.handleKeyAction(actionCode, ev);
			}
	}
	return true;
};

//
// Protected methods
//

// utility

DwtToolBar.prototype._createItemId = function(id) {
    id = id || this._htmlElId;
    var itemId = [ id, "item", ++DwtToolBar.__itemCount ].join("_");
    return itemId;
};

// html creation

DwtToolBar.prototype._createHtml = function() {
    var data = { id: this._htmlElId };
    this._createHtmlFromTemplate(this.TEMPLATE, data);
    this._itemsEl = document.getElementById(data.id+"_items");
    this._prefixEl = document.getElementById(data.id+"_prefix");
    this._suffixEl = document.getElementById(data.id+"_suffix");
};

DwtToolBar.prototype._createItemElement = function(templateId) {
    templateId = templateId || this.ITEM_TEMPLATE;
    var data = { id: this._htmlElId, itemId: this._createItemId() };
    var html = AjxTemplate.expand(templateId, data);
    var fragment = Dwt.toDocumentFragment(html, data.itemId);
    var item = AjxUtil.getFirstElement(fragment);
    return item;
};

DwtToolBar.prototype._createSpacerElement = function(templateId) {
    return this._createItemElement(templateId || this.SPACER_TEMPLATE);
};

DwtToolBar.prototype._createSeparatorElement = function(templateId) {
    return this._createItemElement(templateId || this.SEPARATOR_TEMPLATE);
};

DwtToolBar.prototype._createFillerElement = function(templateId) {
    return this._createItemElement(templateId || this.FILLER_TEMPLATE);
};

// item management

DwtToolBar.prototype._addItem =
function(type, element, index) {

    // get the reference element for insertion
    var count = this._items.length;
    var placeEl = this._items[index] || this._suffixEl;

    // insert item
    this._items.splice(index || count, 0, element);
    this._itemsEl.insertBefore(element, placeEl);

    // append spacer
    // TODO!
};

// transfer focus to the current item
DwtToolBar.prototype._focus =
function(item) {
	DBG.println(AjxDebug.DBG3, "DwtToolBar: FOCUS");
	// make sure the key for expanding a button submenu matches our style
	var kbm = this.shell.getKeyboardMgr();
	if (kbm.isEnabled()) {
		var kmm = kbm.__keyMapMgr;
		if (this._style == DwtToolBar.HORIZ_STYLE) {
			kmm.removeMapping("DwtButton", "ArrowRight");
			kmm.setMapping("DwtButton", "ArrowDown", DwtKeyMap.SUBMENU);
		} else {
			kmm.removeMapping("DwtButton", "ArrowDown");
			kmm.setMapping("DwtButton", "ArrowRight", DwtKeyMap.SUBMENU);
		}
		kmm.reloadMap("DwtButton");
	}

	item = item ? item : this._getFocusItem(this._curFocusIndex);
	if (item) {
		item._hasFocus = true;	// so that focus class is set
		item._focus();
	} else {
		// if current item isn't focusable, find first one that is
		this._moveFocus();
	}
};

// blur the current item
DwtToolBar.prototype._blur =
function(item) {
	DBG.println(AjxDebug.DBG3, "DwtToolBar: BLUR");
	item = item ? item : this._getFocusItem(this._curFocusIndex);
	if (item) {
		item._hasFocus = false;
		item._blur();
	}
};

/**
 * Returns the item at the given index, as long as it can accept focus.
 * For now, we only move focus to simple components like buttons. Also,
 * the item must be enabled and visible.
 * 
 * @param index		[int]		index of item within toolbar
 */
DwtToolBar.prototype._getFocusItem =
function(index) {
	var item = this.getItem(index);
	if (!item || (item instanceof DwtToolBar))	{ return null; }
	if (item._noFocus)							{ return null; }
	if (item.getEnabled && !item.getEnabled())	{ return null; }
	if (item.getVisible && !item.getVisible())	{ return null; }
	return item;
};

/**
 * Moves focus to next or previous item that can take focus.
 * 
 * @param back		[boolean]*		if true, move focus to previous item
 */
DwtToolBar.prototype._moveFocus =
function(back) {
	var index = this._curFocusIndex;
	var maxIndex = this.getItemCount() - 1;
	var item = null;
	while (!item && index >= 0 && index <= maxIndex) {
		index = back ? index - 1 : index + 1;
		item = this._getFocusItem(index);
	}
	if (item) {
		this._blur();
		this._curFocusIndex = index;
		this._focus(item);
	}
};

//
// Classes
//

DwtToolBarButton = function(parent, style, className, posStyle, actionTiming, id, index) {
    className = className || "ZToolbarButton";
    DwtButton.apply(this, arguments);
};

DwtToolBarButton.prototype = new DwtButton;
DwtToolBarButton.prototype.constructor = DwtToolBarButton;

// Data
DwtToolBarButton.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZToolbarButton";
