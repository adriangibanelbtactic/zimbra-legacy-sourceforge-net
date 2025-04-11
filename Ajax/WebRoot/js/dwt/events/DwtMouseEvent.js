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


DwtMouseEvent = function() {
	DwtUiEvent.call(this, true);
	this.reset(true);
}

DwtMouseEvent.prototype = new DwtUiEvent;
DwtMouseEvent.prototype.constructor = DwtMouseEvent;

DwtMouseEvent.prototype.toString = 
function() {
	return "DwtMouseEvent";
}

DwtMouseEvent.NONE = 0;
DwtMouseEvent.LEFT = 1;
DwtMouseEvent.MIDDLE = 2;
DwtMouseEvent.RIGHT = 3;

DwtMouseEvent.prototype.reset =
function(dontCallParent) {
	if (!dontCallParent)
		DwtUiEvent.prototype.reset.call(this);
	this.button = 0;
	this._populated = false;
}

DwtMouseEvent.prototype.setFromDhtmlEvent =
function(ev) {
	ev = DwtUiEvent.prototype.setFromDhtmlEvent.call(this, ev);
	if (ev.offsetX != null) { // IE
		if ((ev.button & 1) != 0)
			this.button = DwtMouseEvent.LEFT;
		else if ((ev.button & 2) != 0)
			this.button = DwtMouseEvent.RIGHT;
		else if ((ev.button & 4) != 0)
			this.button = DwtMouseEvent.MIDDLE;
		else
			this.button = DwtMouseEvent.NONE;
	} else if (ev.layerX != null) { // Mozilla
		if (ev.which == 1)
			this.button = DwtMouseEvent.LEFT;
		else if (ev.which == 2)
			this.button = DwtMouseEvent.MIDDLE;
		else if (ev.which == 3)
			this.button = DwtMouseEvent.RIGHT;
		else
			this.button = DwtMouseEvent.NONE;
	}
	if (AjxEnv.isMac) {
		// if ctrlKey and LEFT mouse, turn into RIGHT mouse with no ctrl key
		if (this.ctrlKey && this.button == DwtMouseEvent.LEFT) {
			this.button = DwtMouseEvent.RIGHT;
			this.ctrlKey = false;
		}
		// allow alt-key to be used for ctrl-select
		if (this.altKey) {
			this.ctrlKey = true;
			this.altKey = false;
		}
	}
}