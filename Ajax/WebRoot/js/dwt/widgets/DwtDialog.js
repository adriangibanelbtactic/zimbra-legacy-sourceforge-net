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
* This class represents a popup dialog which has at least a title and up to 
* three standard buttons (OK, Cancel). A client or subclass sets the content.
* <p>
* Dialogs always hang off the main shell since their stacking order is managed 
* through z-index.
*
* @author Ross Dargahi
* @author Conrad Damon
*
* @param {DwtShell} parent	parent widget
* @param {string} classname	CSS class name for the instance. Defaults to the this classes
* 		name (optional)
* @param {string} title The title of the dialog (optional)
* @param {array|number} standardButtons. The following are the possible values if
* 		passing in an array (Note that the first button value passed in is bound
* 		to the enter key):<ul>
* 		<li>DwtDialog.CANCEL_BUTTON</li>
* 		<li>DwtDialog.OK_BUTTON</li>
* 		<li>DwtDialog.DISMISS_BUTTON</li>
* 		<li>DwtDialog.NO_BUTTON</li>
* 		<li>DwtDialog.YES_BUTTON</li>
* 		</ul>
* 		The following are passed in as individual items: <ul>
* 		<li>DwtDialog.ALL_BUTTONS - Show all buttons</li>
* 		<li>DwtDialog.NO_BUTTONS - Show no buttons</li></ul>
* 		This parameter defaults to <code>[DwtDialog.OK_BUTTON, DwtDialog.CANCEL_BUTTON]</code> if
* 		no value is provided
* @param {array} extraButtons Array of <i>DwtDialog_ButtonDescriptor</i> objects describing
* 		The extra buttons to add to the dialog
* @param {number} zIndex The z-index to set for this dialog when it is visible. Defaults
* 		to <i>Dwt.Z_DIALOG</i> (optional)
* @param {number} mode The modality of the dialog. One of: DwtBaseDialog.MODAL or 
* 		DwtBaseDialog.MODELESS. Defaults to DwtBaseDialog.MODAL (optional)
* @param {DwtPoint} loc	Location at which to popup the dialog. Defaults to being 
* 		centered (optional)
* 
* @see DwtBaseDialog
* @see DwtDialog_ButtonDescriptor
*/
DwtDialog = function(parent, className, title, standardButtons, extraButtons, zIndex, mode, loc) {

	if (arguments.length == 0) return;
	className = className || "DwtDialog";
	this._title = title || "";

	// standard buttons default to OK / Cancel
	if (!standardButtons) {
		standardButtons = [DwtDialog.OK_BUTTON, DwtDialog.CANCEL_BUTTON];
	} else if (standardButtons == DwtDialog.NO_BUTTONS) {
		standardButtons = null;
	}
	// assemble the list of button IDs, and the list of button descriptors
	this._buttonList = new Array();
	if (standardButtons || extraButtons) {
		this._buttonDesc = new Object();
		if (standardButtons && standardButtons.length) {
			this._enterButtonId = standardButtons[0];
			for (var i = 0; i < standardButtons.length; i++) {
				var buttonId = standardButtons[i];
				this._buttonList.push(buttonId);
				// creating standard button descriptors on file read didn't work, so we create them here
				this._buttonDesc[buttonId] = new DwtDialog_ButtonDescriptor(buttonId, AjxMsg[DwtDialog.MSG_KEY[buttonId]],
																			this.getAlignmentForButton(buttonId));
			}
			// set standard callbacks
			this._resetCallbacks();
		}
		if (extraButtons && extraButtons.length) {
			if (!this._enterButtonId) {
				this._enterButtonId = extraButtons[0];
			}
			for (var i = 0; i < extraButtons.length; i++) {
				var buttonId = extraButtons[i].id;
				this._buttonList.push(buttonId);
				this._buttonDesc[buttonId] = extraButtons[i];
			}
		}
	}

	// get button IDs
	this._buttonElementId = new Object();
	for (var i = 0; i < this._buttonList.length; i++)
		this._buttonElementId[this._buttonList[i]] = Dwt.getNextId();

	DwtBaseDialog.call(this, parent, className, this._title, zIndex, mode, loc);

	// set up buttons
	this._button = new Object();
	for (var i = 0; i < this._buttonList.length; i++) {
		var buttonId = this._buttonList[i];
		var b = this._button[buttonId] = new DwtButton(this);
		b.setText(this._buttonDesc[buttonId].label);
		b.buttonId = buttonId;
		b.addSelectionListener(new AjxListener(this, this._buttonListener));
		this._tabGroup.addMember(b);
		document.getElementById(this._buttonElementId[buttonId]).appendChild(b.getHtmlElement());
	}
}

DwtDialog.prototype = new DwtBaseDialog;
DwtDialog.prototype.constructor = DwtDialog;

DwtDialog.prototype.toString = function() {
	return "DwtDialog";
};

//
// Constants
//

DwtDialog.ALIGN_LEFT 		= 1;
DwtDialog.ALIGN_RIGHT 		= 2;
DwtDialog.ALIGN_CENTER 		= 3;

// standard buttons, their labels, and their positioning
DwtDialog.CANCEL_BUTTON 	= 1;
DwtDialog.OK_BUTTON 		= 2;
DwtDialog.DISMISS_BUTTON 	= 3;
DwtDialog.NO_BUTTON 		= 4;
DwtDialog.YES_BUTTON 		= 5;
DwtDialog.LAST_BUTTON 		= 5;
DwtDialog.NO_BUTTONS 		= 256;
DwtDialog.ALL_BUTTONS 		= [DwtDialog.CANCEL_BUTTON, DwtDialog.OK_BUTTON, 
							   DwtDialog.DISMISS_BUTTON, DwtDialog.NO_BUTTON, 
							   DwtDialog.YES_BUTTON];

DwtDialog.MSG_KEY = new Object();
DwtDialog.MSG_KEY[DwtDialog.CANCEL_BUTTON] 	= "cancel";
DwtDialog.MSG_KEY[DwtDialog.OK_BUTTON] 		= "ok";
DwtDialog.MSG_KEY[DwtDialog.DISMISS_BUTTON] = "dismiss";
DwtDialog.MSG_KEY[DwtDialog.NO_BUTTON] 		= "no";
DwtDialog.MSG_KEY[DwtDialog.YES_BUTTON] 	= "yes";

DwtDialog.ALIGN = new Object();
DwtDialog.ALIGN[DwtDialog.CANCEL_BUTTON]	= DwtDialog.ALIGN_RIGHT;
DwtDialog.ALIGN[DwtDialog.OK_BUTTON] 		= DwtDialog.ALIGN_RIGHT;
DwtDialog.ALIGN[DwtDialog.DISMISS_BUTTON] 	= DwtDialog.ALIGN_RIGHT;
DwtDialog.ALIGN[DwtDialog.NO_BUTTON] 		= DwtDialog.ALIGN_RIGHT;
DwtDialog.ALIGN[DwtDialog.YES_BUTTON] 		= DwtDialog.ALIGN_RIGHT;

// modes
DwtDialog.MODELESS = DwtBaseDialog.MODELESS;
DwtDialog.MODAL = DwtBaseDialog.MODAL;

//
// Data
//

DwtDialog.prototype.CONTROLS_TEMPLATE = "ajax.dwt.templates.Widgets#DwtDialogControls";

//
// Public methods
//

DwtDialog.prototype.getAlignmentForButton =
function (id) {
	return DwtDialog.ALIGN[id];
};

DwtDialog.prototype.popdown =
function() {
	DwtBaseDialog.prototype.popdown.call(this);
	this.resetButtonStates();
};

/**
* Sets the dialog back to its original state after being constructed, by clearing any
* detail message and resetting the standard button callbacks.
*/
DwtDialog.prototype.reset =
function() {
	this._resetCallbacks();
	this.resetButtonStates();
	DwtBaseDialog.prototype.reset.call(this);
};

/**
 * Sets all buttons back to inactive
 */
DwtDialog.prototype.resetButtonStates =
function() {
	for (b in this._button) {
		this._button[b].setEnabled(true);
		this._button[b].setHovered(false);
	}
};

DwtDialog.prototype.getButton =
function(buttonId) {
	return this._button[buttonId];
};

DwtDialog.prototype.setButtonEnabled = 
function(buttonId, enabled) {
	this._button[buttonId].setEnabled(enabled);
};

DwtDialog.prototype.setButtonVisible = 
function(buttonId, visible) {
	this._button[buttonId].setVisible(visible);
};

DwtDialog.prototype.getButtonEnabled = 
function(buttonId) {
	return this._button[buttonId].getEnabled();
};

/**
* Registers a callback on the given button. Can be passed an AjxCallback,
* or the params needed to create one.
*
* @param button		[constant]				one of the standard dialog buttons
* @param func		[function|AjxCallback]	callback method
* @param obj		[object]				callback obj
* @param args		[array]					callback args
*/
DwtDialog.prototype.registerCallback =
function(buttonId, func, obj, args) {
	var callback = (func instanceof AjxCallback) ? func : new AjxCallback(obj, func, args);
	this._buttonDesc[buttonId].callback = callback;
};

DwtDialog.prototype.unregisterCallback =
function(buttonId) {
	this._buttonDesc[buttonId].callback = null;
};

/**
* Makes the given listener the only listener for the given button.
*
* @param button		one of the standard dialog buttons
* @param listener	a listener
*/
DwtDialog.prototype.setButtonListener =
function(buttonId, listener) {
	this._button[buttonId].removeSelectionListeners();
	this._button[buttonId].addSelectionListener(listener);
}

DwtDialog.prototype.associateEnterWithButton =
function(id) {
	this._enterButtonId = id;
};

DwtDialog.prototype.getKeyMapName = 
function() {
	return "DwtDialog";
};

DwtDialog.prototype.handleKeyAction =
function(actionCode, ev) {
	switch (actionCode) {
		
		case DwtKeyMap.ENTER:
			this.notifyListeners(DwtEvent.ENTER, ev);
			break;
			
		case DwtKeyMap.CANCEL:
			this.popdown();
			break;
			
		default:
			return false;
	}
	return true;
};

//
// Protected methods
//

DwtDialog.prototype._createHtmlFromTemplate = function(templateId, data) {
    DwtBaseDialog.prototype._createHtmlFromTemplate.call(this, templateId, data);

    var focusId = data.id+"_focus";
    if (document.getElementById(focusId)) {
        this._focusElementId = focusId;
    }
    this._buttonsEl = document.getElementById(data.id+"_buttons");
    if (this._buttonsEl) {
        var html = [];
        var idx = 0;
        this._addButtonsHtml(html,idx);
        this._buttonsEl.innerHTML = html.join(""); 
    }
};

// TODO: Get rid of these button template methods!

DwtDialog.prototype._getButtonsContainerStartTemplate =
function () {
	return "<table cellspacing='0' cellpadding='0' border='0' width='100%'><tr>";
};

DwtDialog.prototype._getButtonsAlignStartTemplate =
function () {
	return "<td align=\"{0}\"><table cellspacing='5' cellpadding='0' border='0'><tr>";
};

DwtDialog.prototype._getButtonsAlignEndTemplate =
function () {
	return "</tr></table></td>";
};

DwtDialog.prototype._getButtonsCellTemplate =
function () {
	return "<td id=\"{0}\"></td>";
};

DwtDialog.prototype._getButtonsContainerEndTemplate =
function () {
	return  "</tr></table>";
};

DwtDialog.prototype._addButtonsHtml =
function(html, idx) {
	if (this._buttonList && this._buttonList.length) {
		var leftButtons = new Array();
		var rightButtons = new Array();
		var centerButtons = new Array();
		for (var i = 0; i < this._buttonList.length; i++) {
			var buttonId = this._buttonList[i];
			switch (this._buttonDesc[buttonId].align) {
				case DwtDialog.ALIGN_RIGHT: 	rightButtons.push(buttonId); break;
				case DwtDialog.ALIGN_LEFT: 		leftButtons.push(buttonId); break;
				case DwtDialog.ALIGN_CENTER:	centerButtons.push(buttonId); break;
			}
		}
		html[idx++] = this._getButtonsContainerStartTemplate();
		
		if (leftButtons.length) {
			html[idx++] = AjxMessageFormat.format(
								  this._getButtonsAlignStartTemplate(),
								  ["left"]);
			for (var i = 0; i < leftButtons.length; i++) {
				var buttonId = leftButtons[i];
				var cellTemplate = this._buttonDesc[buttonId].cellTemplate ? 
					this._buttonDesc[buttonId].cellTemplate : this._getButtonsCellTemplate();
		 		html[idx++] = AjxMessageFormat.format(
								  cellTemplate,
								  [this._buttonElementId[buttonId]]);
		 	}
			html[idx++] = this._getButtonsAlignEndTemplate();
		}
		if (centerButtons.length){
			html[idx++] = AjxMessageFormat.format(
								this._getButtonsAlignStartTemplate(),
								["center"]);
			for (var i = 0; i < centerButtons.length; i++) {
				var buttonId = centerButtons[i];
				var cellTemplate = this._buttonDesc[buttonId].cellTemplate ? 
					this._buttonDesc[buttonId].cellTemplate : this._getButtonsCellTemplate();				
		 		html[idx++] = AjxMessageFormat.format(
								cellTemplate,
								[this._buttonElementId[buttonId]]);
		 	}
			html[idx++] = this._getButtonsAlignEndTemplate();
		}
		if (rightButtons.length) {
			html[idx++] = AjxMessageFormat.format(
								this._getButtonsAlignStartTemplate(),
								["right"]);
			for (var i = 0; i < rightButtons.length; i++) {
				var buttonId = rightButtons[i];
				var cellTemplate = this._buttonDesc[buttonId].cellTemplate ? 
					this._buttonDesc[buttonId].cellTemplate : this._getButtonsCellTemplate();				

		 		html[idx++] = AjxMessageFormat.format(cellTemplate,
													[this._buttonElementId[buttonId]]);
		 	}
			html[idx++] = this._getButtonsAlignEndTemplate();
		}
		html[idx++] = this._getButtonsContainerEndTemplate();
	}	
	return idx;
};

// Button listener that checks for callbacks
DwtDialog.prototype._buttonListener =
function(ev, args) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	var buttonId = obj.buttonId;
	this._runCallbackForButtonId(buttonId, args);
};

DwtDialog.prototype._runCallbackForButtonId =
function(id, args) {
	var callback = this._buttonDesc[id].callback;
	if (!callback) return;
	args = (args instanceof Array) ? args : [args];
	callback.run.apply(callback, args);
};

DwtDialog.prototype._runEnterCallback =
function(args) {
	if (this._enterButtonId && this.getButtonEnabled(this._enterButtonId)) {
		this._runCallbackForButtonId(this._enterButtonId, args);
	}
};

// Default callbacks for the standard buttons.
DwtDialog.prototype._resetCallbacks =
function() {
	if (this._buttonDesc) {
		for (var i = 0; i < DwtDialog.ALL_BUTTONS.length; i++) {
			var id = DwtDialog.ALL_BUTTONS[i];
			if (this._buttonDesc[id])
				this._buttonDesc[id].callback = new AjxCallback(this, this.popdown);
		}
	}
};

//
// Classes
//

DwtDialog_ButtonDescriptor = function(id, label, align, callback, cellTemplate) {
	this.id = id;
	this.label = label;
	this.align = align;
	this.callback = callback;
	this.cellTemplate = cellTemplate;
}
