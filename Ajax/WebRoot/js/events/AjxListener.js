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
* Creates a new listener.
* @constructor
* @class
* This class represents a listener, which is a function to be called in response to an event.
* A listener is a slightly specialized callback: it has a handleEvent() method, and it doesn't
* return a value.
*
* @author Ross Dargahi
* @param obj	(optional) the object to call the function from
* @param func	the listener function
*/
function AjxListener(obj, method, args) {
	AjxCallback.call(this, obj, method, args);
}

AjxListener.prototype = new AjxCallback();
AjxListener.prototype.constructor = AjxListener;

AjxListener.prototype.toString = 
function() {
	return "AjxListener";
}

/**
* Invoke the listener function.
*
* @param ev		the event object that gets passed to an event handler
*/
AjxListener.prototype.handleEvent =
function(ev) {
	return this.run(ev);
}
