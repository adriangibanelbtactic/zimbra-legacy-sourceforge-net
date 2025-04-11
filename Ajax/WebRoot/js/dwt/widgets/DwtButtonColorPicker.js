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
 * @constructor
 * @class
 *
 * Integrates DwtButton with a popup DwtColorPicker.  This class is useful to
 * present a color picker button with an integrated drop-down for chosing from
 * a color palette.  You can use addSelectionListener to register a handler
 * that will get called when a new color is selected.  Inspect "ev.detail" to
 * retrieve the color (guaranteed to be in #RRGGBB format).
 *
 * The button also features a DIV that displays the currently selected color.
 * Upon clicking that DIV, the color will be cleared (in this event, ev.detail
 * will be the empty string in your selection listener).  Note you must call
 * showColorDisplay() in order for this DIV to be displayed.
 *
 * All constructor arguments are passed forward to the DwtButton constructor.
 *
 * @extends DwtButton
 * @author Mihai Bazon, <mihai@zimbra.com>
 */
DwtButtonColorPicker = function(parent, style, className, posStyle, id, index, noFillLabel) {
    if (arguments.length == 0) return;
    DwtButton.call(this, parent, style, className, posStyle, DwtButton.ACTION_MOUSEUP, id, index);

	// WARNING: we pass boolean instead of a DwtDialog because (1) we don't
	// have a dialog right now and (2) DwtMenu doesn't seem to make use of
	// this parameter in other ways than to establish the zIndex.  That's
	// unnecessarily complex :-(
	var m = new DwtMenu(this, DwtMenu.COLOR_PICKER_STYLE, null, null, true);
	this.setMenu(m);
	var cp = new DwtColorPicker(m, null, null, noFillLabel);
	cp.addSelectionListener(new AjxListener(this, this._colorPicked));

	// no color initially selected
	this.__color = "";
};

DwtButtonColorPicker.prototype = new DwtButton;
DwtButtonColorPicker.prototype.constructor = DwtButtonColorPicker;

//
// Constants
//

DwtButtonColorPicker._RGB_RE = /rgb\(([0-9]{1,3}),\s*([0-9]{1,3}),\s*([0-9]{1,3})\)/;

DwtButtonColorPicker._hexdigits = [ '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' ];

//
// Data
//

DwtButtonColorPicker.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZButtonColorPicker";

//
// Public methods
//

/// Utility function that converts the given integer to its hexadecimal
/// representation.
///
/// @param n {int} number to convert
/// @param pad {int, optional} number of digits in the final number (zero-padded if required).
DwtButtonColorPicker.toHex =
function(n, pad) {
	var digits = [];
	while (n) {
		var d = DwtButtonColorPicker._hexdigits[n & 15];
		digits.push(d);
		n = n >> 4;
	}
	if (pad != null) {
		pad -= digits.length;
		while (pad-- > 0)
			digits.push('0');
	}
	digits.reverse();
	return digits.join("");
};

/// Call this function to display a DIV that shows the currently selected
/// color.  This DIV also has the ability to clear the current color.
DwtButtonColorPicker.prototype.showColorDisplay =
function(disableMouseOver) {
    if (!this._colorEl) return;

    if (!disableMouseOver) {
		this._colorEl.onmouseover = DwtButtonColorPicker.__colorDisplay_onMouseOver;
		this._colorEl.onmouseout = DwtButtonColorPicker.__colorDisplay_onMouseOut;
		this._colorEl.onmousedown = DwtButtonColorPicker.__colorDisplay_onMouseDown;
	}
};

/// @return currently selected color
DwtButtonColorPicker.prototype.getColor =
function() {
	return this.__color;
};

/// Set the current color.
///
/// @param {string} color The desired color.  Pass the empty string to clear
///                       the selection.
DwtButtonColorPicker.prototype.setColor =
function(color) {
	// let's make sure we keep it in #RRGGBB format
	var rgb = color.match(DwtButtonColorPicker._RGB_RE);
	if (rgb) {
		color = "#" +
			DwtButtonColorPicker.toHex(parseInt(rgb[1]), 2) +
			DwtButtonColorPicker.toHex(parseInt(rgb[2]), 2) +
			DwtButtonColorPicker.toHex(parseInt(rgb[3]), 2);
	}
	this.__color = color;
    var colorEl = this._colorEl;
    if (colorEl)
		colorEl.style.backgroundColor = color;
};

//
// Protected methods
//

DwtButtonColorPicker.prototype._createHtmlFromTemplate = function(templateId, data) {
    DwtButton.prototype._createHtmlFromTemplate.call(this, templateId, data);
    this._colorEl = document.getElementById(data.id+"_color");
};

/// Protected function that is called when a color is chosen from the popup
/// DwtColorPicker.  Sets the current color to the chosen one and calls the
/// DwtButton's selection handlers if any.
DwtButtonColorPicker.prototype._colorPicked =
function(ev) {
	var color = ev.detail;
	this.__color = this.__detail = color;
    var colorEl = this._colorEl;
    if (colorEl) {
		colorEl.style.backgroundColor = color;
	}
	if (this.isListenerRegistered(DwtEvent.SELECTION)) {
		var selEv = DwtShell.selectionEvent;
		// DwtUiEvent.copy(selEv, ev);
		selEv.item = this;
		selEv.detail = color;
		this.notifyListeners(DwtEvent.SELECTION, selEv);
	}
};

//
// Private methods
//

/// When the color display DIV is hovered, we show a small "X" icon to suggest
/// the end user that the selected color can be cleared.
DwtButtonColorPicker.prototype.__colorDisplay_onMouseOver =
function(ev, div) {
	if (!this.getEnabled())
		return;
	Dwt.addClass(div, "ImgDisable");
};

DwtButtonColorPicker.prototype.__colorDisplay_onMouseOut =
function(ev, div) {
	if (!this.getEnabled())
		return;
	Dwt.delClass(div, "ImgDisable");
};

/// Clears the selected color.  This function is called when the color display
/// DIV is clicked.
DwtButtonColorPicker.prototype.__colorDisplay_onMouseDown =
function(ev, div) {
	if (!this.getEnabled())
		return;
	var dwtev = DwtShell.mouseEvent;
	dwtev.setFromDhtmlEvent(ev);
	this.__color = this.__detail = div.style.backgroundColor = "";

 	if (this.isListenerRegistered(DwtEvent.SELECTION)) {
 		var selEv = DwtShell.selectionEvent;
 		// DwtUiEvent.copy(selEv, ev);
 		selEv.item = this;
 		selEv.detail = "";
 		this.notifyListeners(DwtEvent.SELECTION, selEv);
 	}

	dwtev._stopPropagation = true;
	dwtev._returnValue = false;
	dwtev.setToDhtmlEvent(ev);
	return false;
};

// static event dispatchers

DwtButtonColorPicker.__colorDisplay_onMouseOver =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	obj.__colorDisplay_onMouseOver(ev, this);
};

DwtButtonColorPicker.__colorDisplay_onMouseOut =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	obj.__colorDisplay_onMouseOut(ev, this);
};

DwtButtonColorPicker.__colorDisplay_onMouseDown =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	obj.__colorDisplay_onMouseDown(ev, this);
};
