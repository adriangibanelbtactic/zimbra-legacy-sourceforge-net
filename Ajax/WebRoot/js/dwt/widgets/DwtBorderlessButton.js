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
* This class represents a button without a border
*
* @param parent	{DwtControl} Parent widget (required)
* @param style	{string} the label style. This is an "or'ed" set of attributes (see DwtLabel)
* @param className {string} CSS class. If not provided defaults to the class name (optional)
* @param posStyle {string} Positioning style (absolute, static, or relative). If
* 		not provided defaults to DwtControl.STATIC_STYLE (optional)
* @param actionTiming {enum} if DwtButton.ACTION_MOUSEUP, then the button is triggered
* 		on mouseup events, else if DwtButton.ACTION_MOUSEDOWN, then the button is
* 		triggered on mousedown events
* @param {int} id An explicit ID to use for the control's HTML element. If not
* 		specified defaults to an auto-generated id (optional)
* @param {int} index index at which to add this control among parent's children (optional)
*
* @extends DwtButton
*/
DwtBorderlessButton = function(parent, style, className, posStyle, actionTiming, id, index) {
	if (arguments.length == 0) return;

	DwtButton.call(this, parent, style, className, posStyle, id, index);
}

DwtBorderlessButton.prototype = new DwtButton;
DwtBorderlessButton.prototype.constructor = DwtBorderlessButton;

DwtBorderlessButton.prototype.toString =
function() {
	return "DwtBorderlessButton";
}

//
// Data
//

DwtBorderlessButton.prototype.TEMPLATE = "ajax.dwt.templates.Widgets#ZBorderlessButton"

