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
* Creates a menu item. Menu items can be part of a radio group, or can be checked style menu items
*
* @constructor
* @class
*
* @author Ross Dargahi
* @param parent			the parent widget
* @param style 			menu item's style
* @param radioGroupId 	radio group that the menu item is part of
* @param index 			position in menu
* @param className		a CSS class
* @param posStyle		positioning style
*/
DwtMenuItem = function(parent, style, radioGroupId, index, className, posStyle) {
    if (arguments.length == 0) return;

    // check parameters
    if (!(parent instanceof DwtMenu))
		throw new DwtException("Parent must be a DwtMenu object", DwtException.INVALIDPARENT, "DwtMenuItem");
		
	style = style != null ? style : DwtMenuItem.NO_STYLE;
	if (parent._style == DwtMenu.BAR_STYLE && style != DwtMenuItem.PUSH_STYLE)
		throw new DwtException("DwtMenuItemInit: invalid style", DwtException.INVALID_PARAM, "DwtMenuItem"); 

    // call super constructor
    style &= ~DwtLabel.IMAGE_RIGHT; // remove image right style
    style |= DwtButton.ALWAYS_FLAT | DwtLabel.IMAGE_LEFT; // set default styles
    className = style & DwtMenuItem.SEPARATOR_STYLE ? "ZMenuItemSeparator" : (className || "ZMenuItem");
    DwtButton.call(this, parent, style, className, posStyle);

    if (parent._menuHasCheckedItems()) {
        this._addCheckCell();
    }
    if (parent._menuHasItemsWithIcons()) {
        this._addIconCell();
    }

    this.setDropDownImages("Cascade", "Cascade", "Cascade", "Cascade");
    this._radioGroupId = radioGroupId;

    // add this item at the specified index
    if (parent._addItem) {
		parent._addItem(this, index);
    }

    // add listeners
    this._subMenuMouseOverListener = new AjxListener(this, this.__handleSubMenuMouseOver);
    this.addSelectionListener(new AjxListener(this, this.__handleItemSelect));
}

DwtMenuItem.prototype = new DwtButton;
DwtMenuItem.prototype.constructor = DwtMenuItem;

DwtMenuItem.prototype.toString = 
function() {
	return "DwtMenuItem";
}

//
// Constants
//

DwtMenuItem.CHECKED = 1;
DwtMenuItem.UNCHECKED = 2;

DwtMenuItem.NO_STYLE = 0;
DwtMenuItem.CHECK_STYLE = DwtButton._LAST_STYLE * 2;
DwtMenuItem.RADIO_STYLE = DwtButton._LAST_STYLE * 4;
DwtMenuItem.SEPARATOR_STYLE = DwtButton._LAST_STYLE * 8;
DwtMenuItem.CASCADE_STYLE = DwtButton._LAST_STYLE * 16;
DwtMenuItem.PUSH_STYLE = DwtButton._LAST_STYLE * 32;
DwtMenuItem.SELECT_STYLE = DwtButton._LAST_STYLE * 64;

DwtMenuItem._LAST_STYLE = DwtMenuItem.SELECT_STYLE; 

DwtMenuItem._MENU_POPUP_DELAY = 250;
DwtMenuItem._MENU_POPDOWN_DELAY = 250

//
// Data
//

DwtMenuItem.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZMenuItem";

DwtMenuItem.prototype.SEPARATOR_TEMPLATE = "ajax.dwt.templates.Widgets#ZMenuItemSeparator";

DwtMenuItem.prototype.BLANK_CHECK_TEMPLATE = "ajax.dwt.templates.Widgets#ZMenuItemBlankCheck";
DwtMenuItem.prototype.BLANK_ICON_TEMPLATE = "ajax.dwt.templates.Widgets#ZMenuItemBlankIcon";
DwtMenuItem.prototype.BLANK_CASCADE_TEMPLATE = "ajax.dwt.templates.Widgets#ZMenuItemBlankCascade";

//
// Public methods
//
DwtMenuItem.create =
function(parent, imageInfo, text, disImageInfo, enabled, style, radioGroupId, idx, className, posStyle) {
	var mi = new DwtMenuItem(parent, style, radioGroupId, idx, className, posStyle);
	if (imageInfo)
		mi.setImage(imageInfo);
	if (text)
		mi.setText(text);
	if (disImageInfo)
		mi.setDisabledImage(disImageInfo);
	mi.setEnabled(enabled !== false);
	return mi;
}

DwtMenuItem.prototype.getChecked =
function() {
	return this._itemChecked;
}

DwtMenuItem.prototype.setChecked =
function(checked, skipNotify) {
	this._setChecked(checked, null, skipNotify);
    if (this._checkEl) {
        var isCheck = this._style & DwtMenuItem.CHECK_STYLE;
        var isRadio = this._style & DwtMenuItem.RADIO_STYLE;
        if (!isCheck && !isRadio) {
            this._checkEl.innerHTML = AjxTemplate.expand(this.BLANK_CHECK_TEMPLATE, this._htmlElId); 
        }
    }
}

DwtMenuItem.prototype.setImage = function(imageInfo) {
	DwtButton.prototype.setImage.call(this, imageInfo);
    if (imageInfo) {
        this.parent._menuItemHasIcon(this);
    }
    else if (this._leftIconEl) {
        this._leftIconEl.innerHTML = AjxTemplate.expand(this.BLANK_ICON_TEMPLATE, this._htmlElId);
    }
}

//
// Protected methods
//

DwtMenuItem.prototype._createHtml = function(templateId) {
    var defaultTemplate = this._style & DwtMenuItem.SEPARATOR_STYLE ? this.SEPARATOR_TEMPLATE : this.TEMPLATE;
    DwtButton.prototype._createHtml.call(this, templateId || defaultTemplate);
};

DwtMenuItem.prototype._createHtmlFromTemplate = function(templateId, data) {
    DwtButton.prototype._createHtmlFromTemplate.call(this, templateId, data);
    this._checkEl = document.getElementById(data.id+"_check");
};

DwtMenuItem.prototype._setChecked =
function(checked, ev, skipNotify) {
    var isCheck = this._style & DwtMenuItem.CHECK_STYLE;
    var isRadio = this._style & DwtMenuItem.RADIO_STYLE;
    if ((isCheck || isRadio) && this._itemChecked != checked) {
		this._itemChecked = checked;
        this.parent._menuItemHasCheck(this);

        if (this._checkEl) {
            this._checkEl.innerHTML = "";
            var icon = checked ? (isCheck ? "MenuCheck" : "MenuRadio") : "Blank_9";
            AjxImg.setImage(this._checkEl, icon);
            if (checked) {
                // deselect currently selected radio button
                if (isRadio) {
                    this.parent._radioItemSelected(this, skipNotify);
                }

                // follow icon
                var gp = this.parent.parent ? this.parent.parent : null;
                if (gp && (gp instanceof DwtButton) && (gp._followIconStyle == this._style)) {
                    gp.setImage(this._imageInfo);
                }
            }
        }
	}
}

DwtMenuItem.prototype._addIconCell = function() {
    this.setImage(this.getImage());
};

DwtMenuItem.prototype._checkItemAdded = function(className) {
    this._addCheckCell();
};

DwtMenuItem.prototype._addCheckCell = function() {
    if (this._checkEl) {
        this._checkEl.innerHTML = AjxTemplate.expand(this.BLANK_CHECK_TEMPLATE, this._htmlElId);
    }
};

DwtMenuItem.prototype._checkedItemsRemoved = function() {
	if (this._checkEl) {
        this._checkEl.innerHTML = "";
    }
}

DwtMenuItem.prototype._submenuItemAdded =
function() {
	if (this._style & DwtMenuItem.SEPARATOR_STYLE) return;

    if (this._cascCell == null) {
        this._cascCell = this._row.insertCell(-1);
        this._cascCell.noWrap = true;
        this._cascCell.style.width = DwtMenuItem._CASCADE_DIM;
        this._cascCell.style.height = (this._style != DwtMenuItem.SEPARATOR_STYLE) ?  DwtMenuItem._CASCADE_DIM : DwtMenuItem._SEPAARATOR_DIM;
    }
};

DwtMenuItem.prototype._submenuItemRemoved = function() {
	if (this._dropDownEl) {
		this._dropDownEl.innerHTML = "";
	}
};

DwtMenuItem.prototype._popupMenu =
function(delay, kbGenerated) {
	var menu = this.getMenu();
	var pp = this.parent.parent;
	var pb = this.getBounds();
	var ws = menu.shell.getSize();
	var s = menu.getSize();
	var x;
	var y;
	var vBorder;
	var hBorder;
	var ppHtmlElement = pp.getHtmlElement();
	if (pp._style == DwtMenu.BAR_STYLE) {
		vBorder = (ppHtmlElement.style.borderLeftWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderLeftWidth);
		x = pb.x + vBorder;
		hBorder = (ppHtmlElement.style.borderTopWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderTopWidth);
		hBorder += (ppHtmlElement.style.borderBottomWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderBottonWidth);
		y = pb.y + pb.height + hBorder;		
		x = ((x + s.x) >= ws.x) ? x - (x + s.x - ws.x): x;
		//y = ((y + s.y) >= ws.y) ? y - (y + s.y - ws.y) : y;
	} else { // Drop Down
		vBorder = (ppHtmlElement.style.borderLeftWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderLeftWidth);
		vBorder += (ppHtmlElement.style.borderRightWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderRightWidth);
		x = pb.x + pb.width + vBorder;
		hBorder = (ppHtmlElement.style.borderTopWidth == "") ? 0 : parseInt(ppHtmlElement.style.borderTopWidth);
		y = pb.y + hBorder;
		x = ((x + s.x) >= ws.x) ? pb.x - s.x - vBorder: x;
		//y = ((y + s.y) >= ws.y) ? y - (y + s.y - ws.y) : y;
	}
	//this.setLocation(x, y);
    menu.addListener(DwtEvent.ONMOUSEOVER, this._subMenuMouseOverListener);
    menu.popup(delay, x, y, kbGenerated);
};

DwtMenuItem.prototype._popdownMenu =
function() {
    var menu = this.getMenu();
    if (menu) {
        menu.popdown();
        menu.removeListener(DwtEvent.ONMOUSEOVER, this._subMenuMouseOverListener);
    }
};

DwtMenuItem.prototype._isMenuPoppedup =
function() {
	var menu = this.getMenu();
	return (menu && menu.isPoppedup()) ? true : false;
}

DwtMenuItem.prototype._mouseOverListener = function(ev) {
    DwtButton.prototype._mouseOverListener.call(this, ev);

    this.parent._popdownSubmenus();
    if (this._menu) {
        this._popupMenu();
    }
};

//
// Private methods
//

DwtMenuItem.prototype.__handleItemSelect = function(event) {
    var style = this._style;
    if (this.isStyle(DwtMenuItem.CHECK_STYLE)) {
        this._setChecked(!this._itemChecked, null, true);
        event.detail = this.getChecked() ? DwtMenuItem.CHECKED : DwtMenuItem.UNCHECKED;
    }
    else if (this.isStyle(DwtMenuItem.RADIO_STYLE)) {
        this._setChecked(true, true);
        this.parent._radioItemSelected(this, true);
        event.detail = this.getChecked() ? DwtMenuItem.CHECKED : DwtMenuItem.UNCHECKED;
    }
    else if (this.isStyle(DwtMenuItem.PUSH_STYLE)) {
        if (this._menu) {
            if (this._isMenuPoppedUp()) {
                DwtMenu.closeActiveMenu();
            }
            else {
                this._popupMenu();
            }
        }
        return;
    }
    if (!this.isStyle(DwtMenuItem.CASCADE_STYLE)) {
        DwtMenu.closeActiveMenu();
    }
};

DwtMenuItem.prototype.__handleSubMenuMouseOver = function(event) {
    this.setDisplayState(DwtControl.HOVER);
};
