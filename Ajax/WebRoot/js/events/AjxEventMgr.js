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


function AjxEventMgr() {
	this._listeners = new Object();
}

AjxEventMgr.prototype.toString = 
function() {
	return "AjxEventMgr";
}

AjxEventMgr.prototype.addListener =
function(eventType, listener) {
	var lv = this._listeners[eventType];
	if (lv == null) {
		lv = this._listeners[eventType] = new AjxVector();
	}         	 
	if (!lv.contains(listener)) {
		if (this._notifyingListeners) {
			lv = this._listeners[eventType] = lv.clone();
		}
		lv.add(listener);
		return true;
	}
	return false;
}

AjxEventMgr.prototype.notifyListeners =
function(eventType, event) {
	this._notifyingListeners = true;
	var lv = this._listeners[eventType];
	if (lv != null) {
		var a = lv.getArray();
		var s = lv.size();
		var retVal = null;
		var c = null;
		for (var i = 0; i < s; i++) {
			c = a[i];
			// listener must be an AjxListener or a function
			if (!(c && (c.handleEvent || (typeof c == "function")))) {
				continue;
			}
			retVal = c.handleEvent ? c.handleEvent(event) : c(event);
			if (retVal === false) {
				break;
			}
		}
	}	
	this._notifyingListeners = false;
	return retVal;
}

AjxEventMgr.prototype.isListenerRegistered =
function(eventType) {
	var lv = this._listeners[eventType];
	return (lv != null && lv.size() > 0);
}

AjxEventMgr.prototype.removeListener = 
function(eventType, listener) {
	var lv = this._listeners[eventType];
	if (lv != null) {
		if (this._notifyingListeners) {
			lv = this._listeners[eventType] = lv.clone();
		}
		lv.remove(listener);
		return true;
	}
	return false;
}

AjxEventMgr.prototype.removeAll = 
function(eventType) {
	var lv = this._listeners[eventType];
	if (lv != null) {
		if (this._notifyingListeners) {
			lv = this._listeners[eventType] = lv.clone();
		}
		lv.removeAll();
		return true;
	}
	return false;
}
