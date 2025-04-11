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


/**
* Creates a Tree Item.
* @constructor
* @class
* This class implements a tree item widget.
*
* @author Ross Dargahi
* @param parent		the parent widget
* @param index		location in siblings (optional)
* @param text 		label text for the tree item (optional);
* @param imageInfo	icon for the tree item (optional)
* @param deferred	if true, then the UI elements of the item are not rendered until needed (i.e. when item becomes visible)
* @param className	CSS class (optional)
* @param posStyle	positioning style (absolute, static, or relative - optional defaults to static)
*/
DwtTreeItem = function(parent, index, text, imageInfo, deferred, className, posStyle) {

	if (parent instanceof DwtTree) {
		this._tree = parent;
	} else if (parent instanceof DwtTreeItem) {
		this._tree = parent._tree;
	} else {
		throw new DwtException("DwtTreeItem parent must be a DwtTree or DwtTreeItem", DwtException.INVALIDPARENT, "DwtTreeItem");
	}

	this._origClassName = className ? className : "DwtTreeItem";
	this._textClassName = this._origClassName + "-Text";
	this._selectedClassName = this._origClassName + "-" + DwtCssStyle.SELECTED;
	this._actionedClassName = this._origClassName + "-" + DwtCssStyle.ACTIONED;
	this._dragOverClassName = this._origClassName + "-DragOver";

	DwtComposite.call(this, parent, null, posStyle, true);

	this._imageInfoParam = imageInfo;
	this._textParam = text;
	this._deferred = (deferred !== false);
	this._itemChecked = false;
	this._initialized = false;
	this._selectionEnabled = true;
	this._actionEnabled = true;

	// disable selection if checkbox style
	if (this._tree._isCheckedStyle()) {
		this.enableSelection(false);
		this._selectedClassName = this._origClassName;
	}

	// if our parent is DwtTree or our parent is initialized and is not deferred
	// type or is expanded, then initialize ourself, else wait
	if (parent instanceof DwtTree ||
		(parent._initialized && (!parent._deferred || parent._expanded)))
	{
		this._initialize(index);
	}
	else
	{
		parent._addDeferredChild(this, index);
		this._index = index;
	}
};

DwtTreeItem.prototype = new DwtComposite;
DwtTreeItem.prototype.constructor = DwtTreeItem;

DwtTreeItem.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZTreeItem";


// Consts

DwtTreeItem._NODECELL_DIM = "16px";


// Public Methods

DwtTreeItem.prototype.toString =
function() {
	return "DwtTreeItem";
};

DwtTreeItem.prototype.getChecked =
function() {
	return this._itemChecked;
};

DwtTreeItem.prototype.setChecked =
function(checked, force) {
	if ((this._itemChecked != checked) || force) {
		this._itemChecked = checked;
		if (this._checkBox != null &&
			(this._checkBoxCell && Dwt.getVisible(this._checkBoxCell)))
		{
			this._checkBox.checked = checked;
			
			// NOTE: This hack is needed because IE actively loses the checked
			//		 state for checkbox elements that are programmatically set
			//		 before being added to the document tree (or even before
			//		 layout, from the looks of things).
			//
			//		 The following code will demonstrate the bug:
			//
			//		 var checkbox = document.createElement("INPUT");
			//		 checkbox.type = "checkbox";
			//		 checkbox.checked = true;
			//		 document.body.appendChild(checkbox);
			//		 alert(checkbox.checked);
			if (this._checkBox._ieHack) {
				if (checked) {
					var checkbox = document.createElement("<INPUT type='checkbox' checked>");
					Dwt.setHandler(checkbox, DwtEvent.ONMOUSEDOWN, DwtTreeItem._checkBoxMouseDownHdlr);
					Dwt.setHandler(checkbox, DwtEvent.ONMOUSEUP, DwtTreeItem._checkBoxMouseUpHdlr);
					this._checkBox.parentNode.replaceChild(checkbox, this._checkBox);
					this._checkBox = checkbox;
				} else {
					this._checkBox._ieHack = false;
				}
			}
		}
	}
};

DwtTreeItem.prototype.getExpanded =
function() {
	return this._expanded;
};

/**
* Expands or collapses this tree item.
*
* @param expanded		if true, expands this node, otherwise collapses it
* @param recurse		expand children recursively (doesn't apply to collapsing)
*/
DwtTreeItem.prototype.setExpanded =
function(expanded, recurse) {
	// Go up the chain, ensuring that parents are expanded/initialized
	if (expanded) {
		var p = this.parent;
		while (p instanceof DwtTreeItem && !p._expanded) {
			p.setExpanded(true);
			p = p.parent;
		}
		// Realize any deferred children
		this._realizeDeferredChildren();
	}
		
	// If we have children, then allow for expanding/collapsing
	if (this.getNumChildren()) {
		if (expanded && recurse) {
			if (!this._expanded)
				this._expand(expanded);
			var a = this.getChildren();
			for (var i = 0; i < a.length; i++)
				if (a[i] instanceof DwtTreeItem)
					a[i].setExpanded(expanded, recurse);
		} else if (this._expanded != expanded) {
			this._expand(expanded);
		}
	}
};

DwtTreeItem.prototype.getItemCount =
function() {
	return this._children.size();
};

DwtTreeItem.prototype.getItems =
function() {
	return this._children.getArray();
};

DwtTreeItem.prototype.getImage =
function() {
	return this._imageInfo;
};

DwtTreeItem.prototype.setImage =
function(imageInfo) {
	if (this._initialized) {
		AjxImg.setImage(this._imageCell, imageInfo);
		this._imageInfo = imageInfo;
	} else {
		this._imageInfoParam = imageInfo;
	}	
};

DwtTreeItem.prototype.setDndImage =
function(imageInfo) {
	this._dndImageInfo = imageInfo;
};

DwtTreeItem.prototype.getSelected =
function() {
	return this._selected;
};

DwtTreeItem.prototype.getActioned =
function() {
	return this._actioned;
};

DwtTreeItem.prototype.getText =
function() {
	return this._text;
};

DwtTreeItem.prototype.setText =
function(text) {
	if (this._initialized) {
		if (!text)
			text.data = "";
		this._text = this._textCell.innerHTML = text;
	} else {
		this._textParam = text;
	}
};

DwtTreeItem.prototype.setDndText =
function(text) {
	this._dndText = text;
};

DwtTreeItem.prototype.showCheckBox =
function(show) {
	if (this._checkBoxCell) {
		Dwt.setVisible(this._checkBoxCell, show);
	}
};

DwtTreeItem.prototype.showExpansionIcon =
function(show) {
	if (this._nodeCell) {
		Dwt.setVisible(this._nodeCell, show);
	}
};

DwtTreeItem.prototype.enableSelection =
function(enable) {
	this._selectionEnabled = enable;
	this._selectedClassName = enable
		? this._origClassName + "-" + DwtCssStyle.SELECTED
		: this._origClassName;
};

DwtTreeItem.prototype.enableAction =
function(enable) {
	this._actionEnabled = enable;
};

/**
* Adds a separator at the given index. If no index is provided, adds it at the
* end. A separator cannot currently be added as the first item (the child div will
* not have been created).
*
* @param index		position at which to add the separator
*/
DwtTreeItem.prototype.addSeparator =
function(index) {
	this._children.add((new DwtTreeItemSeparator(this)), index);
};

/**
* Makes this tree item, or just part of it, visible or hidden.
*
* @param visible		if true, item (or part of it) becomes visible
* @param itemOnly		apply to this item's DIV only; child items are unaffected
* @param childOnly		apply to this item's child items only
*/
DwtTreeItem.prototype.setVisible =
function(visible, itemOnly, childOnly) {
	if (itemOnly && !childOnly) {
		Dwt.setVisible(this._itemDiv, visible);
	} else if (childOnly && !itemOnly) {
		Dwt.setVisible(this._childDiv, visible);
	} else {
		DwtComposite.prototype.setVisible.call(this, visible);
	}
};

DwtTreeItem.prototype.removeChild =
function(child) {
	if (child._initialized) {
		this._tree._deselect(child);
		this._childDiv.removeChild(child.getHtmlElement());
	}
	this._children.remove(child);

	// if we have no children and we are expanded, then mark us a collapsed.
	// Also if there are no deferred children, then make sure we remove the
	// expand/collapse icon and replace it with a blank16Icon.
	if (this._children.size() == 0) {
		if (this._expanded)
			this._expanded = false;
		
		if (this._initialized) {
			AjxImg.setImage(this._nodeCell, "Blank_16");
			var imgEl = AjxImg.getImageElement(this._nodeCell);
			if (imgEl)
				Dwt.clearHandler(imgEl, DwtEvent.ONMOUSEDOWN);
		}
	}
};

DwtTreeItem.prototype._initialize =
function(index, realizeDeferred) {
	// DO NOT MOVE THIS otherwise deferred items will never get initialized
	this._setMouseEventHdlrs();

	var data = {id:this._htmlElId,
				divClassName:this._origClassName,
				isCheckedStyle:this._tree._isCheckedStyle(),
				textClassName:this._textClassName };

	this._createHtmlFromTemplate(this.TEMPLATE, data);

	// add this object's HTML element to the DOM
	this.parent._addItem(this, index, realizeDeferred);

	// cache DOM objects here
	this._itemDiv = document.getElementById(data.id + "_div");
	this._nodeCell = document.getElementById(data.id + "_nodeCell");
	this._checkBoxCell = document.getElementById(data.id + "_checkboxCell");
	this._checkBox = document.getElementById(data.id + "_checkbox");
	this._imageCell = document.getElementById(data.id + "_imageCell");
	this._textCell = document.getElementById(data.id + "_textCell");

	// If we have deferred children, then make sure we set up accordingly
	if (this._nodeCell) {
		this._nodeCell.style.width = this._nodeCell.style.height = DwtTreeItem._NODECELL_DIM;
		if (this._children.size() > 0) {
			AjxImg.setImage(this._nodeCell, "NodeCollapsed");
			var imgEl = AjxImg.getImageElement(this._nodeCell);
			if (imgEl) {
				Dwt.setHandler(imgEl, DwtEvent.ONMOUSEDOWN, DwtTreeItem._nodeIconMouseDownHdlr);
				Dwt.setHandler(imgEl, DwtEvent.ONMOUSEUP, DwtTreeItem._nodeIconMouseUpHdlr);
			}
		} else {
			AjxImg.setImage(this._nodeCell, "Blank_16");
		}
	}

	// initialize checkbox
	if (this._tree._isCheckedStyle() && this._checkBox) {
		if (AjxEnv.isIE) {
			// HACK: See setChecked method for explanation
			this._checkBox._ieHack = true;
		}
		Dwt.setHandler(this._checkBox, DwtEvent.ONMOUSEDOWN, DwtTreeItem._checkBoxMouseDownHdlr);
		Dwt.setHandler(this._checkBox, DwtEvent.ONMOUSEUP, DwtTreeItem._checkBoxMouseUpHdlr);
		this._checkBox.checked = this._itemChecked;
	}

	// initialize icon
	if (this._imageCell && this._imageInfoParam) {
		AjxImg.setImage(this._imageCell, this._imageInfoParam);
		this._imageInfo = this._imageInfoParam;
	}

	// initialize text
	if (this._textCell && this._textParam) {
		this._textCell.innerHTML = this._text = this._textParam;
	}

	this._expanded = this._selected = this._actioned = false;
    this._gotMouseDownLeft = this._gotMouseDownRight = false;

	this.addListener(DwtEvent.ONMOUSEDOWN, new AjxListener(this, this._mouseDownListener));
    this.addListener(DwtEvent.ONMOUSEOUT, new AjxListener(this, this._mouseOutListener));
    this.addListener(DwtEvent.ONMOUSEUP, new AjxListener(this, this._mouseUpListener));
    this.addListener(DwtEvent.ONDBLCLICK, new AjxListener(this, this._doubleClickListener));  

	this._initialized = true;
};

DwtTreeItem.prototype._addDeferredChild =
function(child, index) {
	// If we are initialized, then we need to add a expansion node
	if (this._initialized && this._children.size() == 0) {
		AjxImg.setImage(this._nodeCell, "NodeCollapsed");
		var imgEl = AjxImg.getImageElement(this._nodeCell);
		if (imgEl) {
			Dwt.setHandler(imgEl, DwtEvent.ONMOUSEDOWN, DwtTreeItem._nodeIconMouseDownHdlr);
			Dwt.setHandler(imgEl, DwtEvent.ONMOUSEUP, DwtTreeItem._nodeIconMouseUpHdlr);
		}
	}
	this._children.add(child, index);
};

DwtTreeItem.prototype.addChild =
function(child) { /* do nothing since we add to the DOM our own way */ }

DwtTreeItem.prototype._addItem =
function(item, index, realizeDeferred) {
	if (!this._children.contains(item))
		this._children.add(item, index);

	if (this._childDiv == null) {
		this._childDiv = document.createElement("div");
		if (this.parent != this._tree) {
			this._childDiv.className = "DwtTreeItemChildDiv";
		} else {
			this._childDiv.className = "DwtTreeItemLevel1ChildDiv";
		}
		this.getHtmlElement().appendChild(this._childDiv);
		if (!this._expanded) 
			this._childDiv.style.display = "none";
	}

	if (realizeDeferred) {
		if (AjxImg.getImageClass(this._nodeCell) == AjxImg.getClassForImage("Blank_16")) {
			if (this._expanded)
				AjxImg.setImage(this._nodeCell, "NodeExpanded");
			else
				AjxImg.setImage(this._nodeCell, "NodeCollapsed");
			var imgEl = AjxImg.getImageElement(this._nodeCell);
			if (imgEl)
				Dwt.setHandler(imgEl, DwtEvent.ONMOUSEDOWN, DwtTreeItem._nodeIconMouseDownHdlr);
		}
	}

	var childDiv = this._childDiv;
	var numChildren = childDiv.childNodes.length;
	if (index == null || index >= numChildren || numChildren == 0) {
		childDiv.appendChild(item.getHtmlElement());
	} else {
		childDiv.insertBefore(item.getHtmlElement(), childDiv.childNodes[index]);
	}
};

DwtTreeItem.prototype._getDnDIcon =
function() {
	var icon = document.createElement("div");
	Dwt.setPosition(icon, Dwt.ABSOLUTE_STYLE); 
	var table = document.createElement("table");
	icon.appendChild(table);
	table.cellSpacing = table.cellPadding = 0;
		
	var row = table.insertRow(0);
	var i = 0;
	
	var c = row.insertCell(i++);
	c.noWrap = true;
	if (this._dndImageInfo) {
		AjxImg.setImage(c, this._dndImageInfo);
	} else if (this._imageInfo) {
		AjxImg.setImage(c, this._imageInfo);
	}
	
	c = row.insertCell(i);
    c.noWrap = true;
    c.className = this._origClassName;
	if (this._dndText) {
    	c.innerHTML = this._dndText;
	} else if (this._text) {
    	c.innerHTML = this._text;
	}
	
	this.shell.getHtmlElement().appendChild(icon);
	Dwt.setZIndex(icon, Dwt.Z_DND);
	return icon;
};

DwtTreeItem.prototype._dragEnter =
function() {
	this._preDragClassName = this._textCell.className;
	this._textCell.className = this._dragOverClassName;
};

DwtTreeItem.prototype._dragHover =
function() {
	if (this.getNumChildren() > 0 && !this.getExpanded())
		this.setExpanded(true);
};

DwtTreeItem.prototype._dragLeave =
function() {
	if (this._preDragClassName)
		this._textCell.className = this._preDragClassName;
};

DwtTreeItem.prototype._drop =
function() {
	if (this._preDragClassName)
		this._textCell.className = this._preDragClassName;
};

DwtTreeItem._nodeIconMouseDownHdlr = 
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	if (mouseEv.button == DwtMouseEvent.LEFT) {
		obj._expand(!obj._expanded, ev);
	} else if (mouseEv.button == DwtMouseEvent.RIGHT) {
		mouseEv.dwtObj._tree._itemActioned(mouseEv.dwtObj, mouseEv);
	}

	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;
};

DwtTreeItem._nodeIconMouseUpHdlr = 
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var mouseEv = DwtShell.mouseEvent;
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;
};

DwtTreeItem.prototype._expand =
function(expand, ev) {
	if (!expand) {
		this._expanded = false;
		this._childDiv.style.display = "none";
		AjxImg.setImage(this._nodeCell, "NodeCollapsed");
		this._tree._itemCollapsed(this, ev);
	} else {
		// The first thing we need to do is initialize any deferred children so that they
		// actually have content
		this._realizeDeferredChildren();
		this._expanded = true;
		this._childDiv.style.display = "block";
		AjxImg.setImage(this._nodeCell, "NodeExpanded");
		this._tree._itemExpanded(this, ev);
	}	
};

DwtTreeItem.prototype._realizeDeferredChildren =
function() {
	var a = this._children.getArray();
	for (var i = 0; i < a.length; i++) {
		if (!a[i]._initialized) {
			if (a[i]._isSeparator) {
				var div = a[i].div = document.createElement("div");
				div.className = "vSpace";
				this._childDiv.appendChild(div);
				a[i]._initialized = true;
			} else {
				a[i]._initialize(a[i]._index, true);
			}
		}
	}
};

DwtTreeItem.prototype._isChildOf =
function(item) {
	var test = this.parent;
	while (test && test != this._tree) {
		if (test == item)
			return true;
		test = test.parent;
	}
	return false;
};

DwtTreeItem.prototype._setSelected =
function(selected) {
	if (this._selected != selected) {
		this._selected = selected;
		if (!this._initialized)
			this._initialize();
		if (selected && this._selectionEnabled) {
			this._textCell.className = this._selectedClassName;
			return true;
		} else {
			this._textCell.className = this._textClassName;
			return false;
		}
	}
};

DwtTreeItem.prototype._setActioned =
function(actioned) {
	if (this._actioned != actioned) {
		this._actioned = actioned;
		if (!this._initialized)
			this._initialize();
		if (actioned && this._actionEnabled && !this._selected) {
			this._textCell.className = this._actionedClassName;
			return true;
		} else if (!actioned) {
			this._textCell.className = this._textClassName;
			return false;
		}
	}
};

DwtTreeItem.prototype._mouseDownListener = 
function(ev) {
	if (ev.target == this._childDiv) return;

	if (ev.button == DwtMouseEvent.LEFT && this._selectionEnabled)
		this._gotMouseDownLeft = true;
	else if (ev.button == DwtMouseEvent.RIGHT && this._actionEnabled)
		this._gotMouseDownRight = true;
};

DwtTreeItem.prototype._mouseOutListener = 
function(ev) {
	if (ev.target == this._childDiv) return;

	this._gotMouseDownLeft = false;
	this._gotMouseDownRight = false;
};

DwtTreeItem.prototype._mouseUpListener = 
function(ev) {
	// Ignore any mouse events in the child div i.e. the div which 
	// holds all the items children. In the case of IE, no clicks are
	// reported when clicking in the padding area (note all children
	// are indented using padding-left style); however, mozilla
	// reports mouse events that happen in the padding area
	if (ev.target == this._childDiv) return;

	if (ev.button == DwtMouseEvent.LEFT && this._gotMouseDownLeft)
		this._tree._itemClicked(this, ev);
	else if (ev.button == DwtMouseEvent.RIGHT && this._gotMouseDownRight)
		this._tree._itemActioned(this, ev);
};

DwtTreeItem.prototype._doubleClickListener =
function(ev) {
	// See comment in DwtTreeItem.prototype._mouseDownListener
	if (ev.target == this._childDiv) 
		return;
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);
	if (mouseEv.button == DwtMouseEvent.LEFT || mouseEv.button == DwtMouseEvent.NONE) // NONE for IE bug
		mouseEv.dwtObj._tree._itemDblClicked(mouseEv.dwtObj, mouseEv);
};

DwtTreeItem._checkBoxMouseDownHdlr =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;
};

DwtTreeItem._checkBoxMouseUpHdlr =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);
	if (mouseEv.button == DwtMouseEvent.LEFT) {
		mouseEv.dwtObj._itemChecked = !mouseEv.dwtObj._itemChecked;	
		mouseEv.dwtObj._tree._itemChecked(mouseEv.dwtObj, mouseEv);
	} else if (mouseEv.button == DwtMouseEvent.RIGHT) {
		mouseEv.dwtObj._tree._itemActioned(mouseEv.dwtObj, mouseEv);
	}
};



/**
 * Minimal class for a separator (some vertical space) between other tree items.
 * The functions it has are to handle a dispose() call when the containing tree
 * is disposed.
 * 
 * TODO: At some point we should just make this a DwtControl, or find some other
 * 		 way of keeping it minimal.
 */
DwtTreeItemSeparator = function(parent) {
	this.parent = parent;
	this._isSeparator = true;
	this._initialized = false;
};

DwtTreeItemSeparator.prototype.dispose =
function() {
	DwtComposite.prototype.removeChild.call(this.parent, this);
};

DwtTreeItemSeparator.prototype.isInitialized =
function() {
	return this._initialized;
};

DwtTreeItemSeparator.prototype.getHtmlElement =
function() {
	return this.div;
};
