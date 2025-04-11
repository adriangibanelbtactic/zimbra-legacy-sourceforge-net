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

/**
* Quickly hacked up class to represent a round button that has a background image and a foreground image.
* Should probably be a subclass of DwtButton, since it copied a bunch of the mouse event handling code from
* there. But it doesn't quite fit into being a DwtLabel, with the stacked images and all.
*
* The button has an inner image positioned relative to an outer image, so that it's roughly centered.
*
* - cannot have a menu
* - does not support enabled/disabled
*
* @author Conrad Damon
* @author Greg Solovyev - adapted this class from ZmChicletButton
*/
ZaChicletButton = function(parent, outerClass, innerClass) {

	if (arguments.length == 0) return;
	DwtControl.call(this, parent, outerClass, DwtControl.RELATIVE_STYLE);

	this._innerDiv = document.createElement("div");
	this._innerDiv.className = AjxImg.getClassForImage(innerClass);
	this._innerDiv.style.position = DwtControl.ABSOLUTE_STYLE;
	this.getHtmlElement().appendChild(this._innerDiv);
	
	this._origClassName = outerClass;
	this._activatedClassName = this._origClassName + " " + DwtCssStyle.ACTIVATED;
	this._triggeredClassName = this._origClassName + " " + DwtCssStyle.TRIGGERED;

	// borrowed/modified from DwtButton...
	
	// add custom mouse handlers to standard ones
	this._setMouseEventHdlrs();
	this.addListener(DwtEvent.ONMOUSEOVER, new AjxListener(this, this._mouseOverListener));
	this.addListener(DwtEvent.ONMOUSEOUT, new AjxListener(this, this._mouseOutListener));
	this.addListener(DwtEvent.ONMOUSEDOWN, new AjxListener(this, this._mouseDownListener));
	this.addListener(DwtEvent.ONMOUSEUP, new AjxListener(this, this._mouseUpListener));

	this._mouseOutAction = new AjxTimedAction(this, this._setMouseOutClassName);
	this._mouseOutActionId = -1;
}

ZaChicletButton.prototype = new DwtControl;
ZaChicletButton.prototype.constructor = ZaChicletButton;

ZaChicletButton.prototype.toString =
function() {
	return "ZaChicletButton";
}



ZaChicletButton.prototype.setOuterImage =
function(className) {
	this._outerDiv.className = className;
}

ZaChicletButton.prototype.setInnerImage =
function(className) {
	this._innerDiv.className = AjxImg.getClassForImage(className);
}

ZaChicletButton.prototype.setActivatedImage =
function(className) {
	this._activatedClassName = className;
}

ZaChicletButton.prototype.setTriggeredImage =
function(className) {
	this._triggeredClassName = className;
}

// from DwtButton...

/**
* Adds a listener to be notified when the button is pressed.
*
* @param listener	a listener
*/
ZaChicletButton.prototype.addSelectionListener = 
function(listener) {
	this.addListener(DwtEvent.SELECTION, listener);
}

/**
* Removes a selection listener.
*
* @param listener	the listener to remove
*/
ZaChicletButton.prototype.removeSelectionListener = 
function(listener) { 
	this.removeListener(DwtEvent.SELECTION, listener);
}

/**
* Removes all the selection listeners.
*/
ZaChicletButton.prototype.removeSelectionListeners = 
function() { 
	this.removeAllListeners(DwtEvent.SELECTION);
}

/**
* Returns the button display to normal (not activated or triggered).
*/
ZaChicletButton.prototype.resetClassName = 
function() {
	this.setClassName(this._origClassName);	
}

/**
* Activates/inactivates the button. A button is activated when the mouse is over it.
*
* @param activated		whether the button is activated
*/
ZaChicletButton.prototype.setActivated =
function(activated) {
	if (activated)
		this.setClassName(this._activatedClassName);
	else
		this.setClassName(this._origClassName);
}

// Activates the button.
ZaChicletButton.prototype._mouseOverListener = 
function(ev) {
	if (this._mouseOutActionId != -1) {
		AjxTimedAction.cancelAction(this._mouseOutActionId);
		this._mouseOutActionId = -1;
	}
    this.setClassName(this._activatedClassName);
    ev._stopPropagation = true;
}

// Triggers the button.
ZaChicletButton.prototype._mouseDownListener = 
function(ev) {
	this.trigger();
}

ZaChicletButton.prototype.trigger =
function() {
	this.setClassName(this._triggeredClassName);
	this.isTriggered = true;	
}

// Button has been pressed, notify selection listeners.
ZaChicletButton.prototype._mouseUpListener = 
function(ev) {
    var el = this.getHtmlElement();
	if (this.isTriggered) {
		this.setClassName(this._activatedClassName);
		if (this.isListenerRegistered(DwtEvent.SELECTION)) {
			var selEv = DwtShell.selectionEvent;
			DwtUiEvent.copy(selEv, ev);
			selEv.item = this;
			selEv.detail = 0;
			this.notifyListeners(DwtEvent.SELECTION, selEv);
		}
	}
	el.className = this._origClassName;	
}

ZaChicletButton.prototype._setMouseOutClassName =
function() {
	this._mouseOutActionId = -1;
    this.setClassName(this._origClassName);
    this.isTriggered = false;
}

// Button no longer activated/triggered.
ZaChicletButton.prototype._mouseOutListener = 
function(ev) {
	if (AjxEnv.isIE){
		this._mouseOutActionId = 
 		   AjxTimedAction.scheduleAction(this._mouseOutAction, 6);
	} else {
		this._setMouseOutClassName();
	}
}
