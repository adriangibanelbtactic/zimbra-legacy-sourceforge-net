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
 * This class implements a label, which consists of an image and/or text. It is used
 * both as a concrete class and as the base class for <i>DwtButton</i>. The label's 
 * components are managed within a table. The label can be enabled or disabled, which are reflected in 
 * its display. A disabled label looks greyed out.
 * 
 * <h4>CSS</h4>
 * <i>.className</i> table - The label's table
 * <i>.className</i> .Icon - class name for the icon image cell
 * <i>.className</i> .Text - enabled text cell 
 * <i>.className</i> .DisabledText - disabled text cell
 * 
 * <h4>Keyboard Actions</h4>
 * None
 * 
 * <h4>Events</h4>
 * None
 * 
 * @author Ross Dargahi
 * 
 * @param {DwtComposite} parent Parent widget. Except in the case of <i>DwtShell</i> the
 * 		parent will be a control that has subclassed from <i>DwtComposite</i>
 * @param {Int} style The label style. May be one of: <i>DwtLabel.IMAGE_LEFT</i> 
 * 		or <i>DwtLabel.IMAGE_RIGHT</i> arithimatically or'd (|) with  one of:
 * 		<i>DwtLabel.ALIGN_LEFT</i>, <i>DwtLabel.ALIGN_CENTER</i>, or <i>DwtLabel.ALIGN_LEFT</i>
 * 		The first determines were in the label the icon will appear (if one is set), the second
 * 		determine how the content of the label will be aligned. The default value for
 * 		this parameter is: <code>DwtLabel.IMAGE_LEFT | DwtLabel.ALIGN_CENTER</code>
 * @param {String} className CSS class. If not provided defaults to the class name (optional)
 * @param {String} posStyle Positioning style (absolute, static, or relative). If
 * 		not provided defaults to <i>DwtControl.STATIC_STYLE</i> (optional)
 * @param {int} id An explicit ID to use for the control's HTML element. If not
 * 		specified defaults to an auto-generated id (optional)
 * @param {int} index index at which to add this control among parent's children (optional)
 * 
 * @see DwtButton
 * 
 * @extends DwtControl
 * 
 * @requires DwtControl
 */
DwtLabel = function(parent, style, className, posStyle, id, index) {
	if (arguments.length == 0) return;
	className = className ? className : "DwtLabel";
	DwtControl.call(this, parent, className, posStyle, false, id, index);

	/**The label's style. See the constructor documentation for more info
	 * @type Int*/
	this._style = style ? style : (DwtLabel.IMAGE_LEFT | DwtLabel.ALIGN_CENTER);
	
	/**The label text's background color
	 * @type String*/
	this._textBackground = null;
	
	/**The label text's foreground color
	 * @type String*/
	this._textForeground = null;

    this._createHtml();
    //MOW:  this.setCursor("default");
}

DwtLabel.prototype = new DwtControl;
DwtLabel.prototype.constructor = DwtLabel;

/**
 * This method returns the class name for the control.
 *
 * @return class name
 * @type String
 */
DwtLabel.prototype.toString =
function() {
	return "DwtLabel";
}

//
// Constants
//

// display styles
/** Align image to the left of text, if both present
 * @type Int*/
DwtLabel.IMAGE_LEFT = 1;

/** Align image to the right of text, if both present
 * @type Int*/
DwtLabel.IMAGE_RIGHT = 2;

/** Align the label to the left
 * @type Int*/
DwtLabel.ALIGN_LEFT = 4;

/** Align the label to the right
 * @type Int*/
DwtLabel.ALIGN_RIGHT = 8;

/** Align the label to the center
 * @type Int*/
DwtLabel.ALIGN_CENTER = 16;

/** The last label style. Used by subclasses when adding styles
 * @type Int*/
DwtLabel._LAST_STYLE = 16;

//
// Data
//

DwtLabel.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZLabel";

//
// Public methods
//

/**
 * Sets the enabled/disabled state of the label. A disabled label may have a different
 * image, and greyed out text. This method overrides <code>DwtControl.setEnabled</code>
 *
 * @param {Boolean} enabled True set the label as enabled
 */
DwtLabel.prototype.setEnabled =
function(enabled) {
	if (enabled != this._enabled) {
		DwtControl.prototype.setEnabled.call(this, enabled);
		if (enabled) {
			this.__setImage(this.__imageInfo);
        }
        else if (this.__disabledImageInfo) {
            this.__setImage(this.__disabledImageInfo);
		}
	}
}

/**
* Returns the current Image Info.
*/
DwtLabel.prototype.getImage =
function() {
	return this.__imageInfo;
}

/**
* Sets the main (enabled) image. If the label is currently enabled, its image is updated.
*/
DwtLabel.prototype.setImage =
function(imageInfo) {
	this.__imageInfo = imageInfo;
	if (this._enabled || (!this._enabled && this.__disabledImageInfo))
		this.__setImage(imageInfo);
}

/**
* Returns the disabled image. If the label is currently disabled, its image is updated.
*
* @param imageSrc	the disabled image
*/
DwtLabel.prototype.setDisabledImage =
function(imageInfo) {
	this.__disabledImageInfo = imageInfo;
	if (!this._enabled && imageInfo)
		this.__setImage(imageInfo);
}

/**
* Returns the label text.
*/
DwtLabel.prototype.getText =
function() {
	return (this.__text != null) ? this.__text : null;
}

/**
* Sets the label text, and manages its placement and display.
*
* @param text	the new label text
*/
DwtLabel.prototype.setText =
function(text) {
    if (!this._textEl) return;

    if (text == null || text == "") {
        this.__text = null;
        this._textEl.innerHTML = "";
    }
    else {
		this.__text = text;
        this._textEl.innerHTML = text;
    }
}

DwtLabel.prototype.setTextBackground =
function(color) {
	this._textBackground = color;
    if (this._textEl) {
        this._textEl.style.backgroundColor = color;
    }
}

DwtLabel.prototype.setTextForeground =
function(color) {
	this._textForeground = color;
    if (this._textEl) {
		this._textEl.style.color = color;
    }
}


DwtLabel.prototype.setAlign =
function(alignStyle) {
	this._style = alignStyle;

	// reset dom since alignment style may have changed
    this.__setImage(this.__imageInfo);
}

DwtLabel.prototype.isStyle = function(style) {
    return this._style & style;
};

//
// Protected methods
//

DwtLabel.prototype._createHtml = function(templateId) {
    var data = { id: this._htmlElId };
    this._createHtmlFromTemplate(templateId || this.TEMPLATE, data);
};

DwtLabel.prototype._createHtmlFromTemplate = function(templateId, data) {
    DwtControl.prototype._createHtmlFromTemplate.call(this, templateId, data);
    this._leftIconEl = document.getElementById(data.id+"_left_icon");
    this._textEl = document.getElementById(data.id+"_title");
    this._rightIconEl = document.getElementById(data.id+"_right_icon");
};

//
// Private methods
//

/**Set the label's image, and manage its placement.
 * @private*/
DwtLabel.prototype.__setImage =
function(imageInfo) {
    if (this._leftIconEl) this._leftIconEl.innerHTML = "";
    if (this._rightIconEl) this._rightIconEl.innerHTML = "";

    var right = this._style & DwtLabel.IMAGE_RIGHT;
    var iconEl = right ? this._rightIconEl : this._leftIconEl;

    if (iconEl) AjxImg.setImage(iconEl, imageInfo);
}

/** Handle the alignment style.
 * @private*/
DwtLabel.prototype.__doAlign =
function() {
    if (this._style & DwtLabel.ALIGN_CENTER) {
        var left = this._style & DwtLabel.IMAGE_LEFT;
        var iconEl = left ? this._leftIconEl : this._rightIconEl;
        var textEl = this._textEl;
        if (this.__imageInfo && this.__text) {
            var iconAlign = left ? "right" : "left";
            var textAlign = left ? "left" : "right";

            if (iconEl) iconEl.align = iconAlign;
            if (textEl) textEl.align = textAlign;
        }
        else if (iconEl) {
            iconEl.align = "center";
        }
        else if (textEl) {
            textEl.align = "center";
        }
    }
}
