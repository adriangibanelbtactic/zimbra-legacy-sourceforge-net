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


function DwtHtmlEditorStateEvent(init) {
	if (arguments.length == 0) return;
	DwtEvent.call(this, true);
	this.reset();
}

DwtHtmlEditorStateEvent.prototype = new DwtEvent;
DwtHtmlEditorStateEvent.prototype.constructor = DwtHtmlEditorStateEvent;

DwtHtmlEditorStateEvent.prototype.toString = 
function() {
	return "DwtHtmlEditorStateEvent";
}

DwtHtmlEditorStateEvent.prototype.reset =
function() {
	this.isBold = null;
	this.isItalic = null;
	this.isUnderline = null;
	this.isStrikeThru = null;
	this.isSuperscript = null;
	this.isSubscript = null;
	this.isOrderedList = null;
	this.isNumberedList = null;
	this.fontName = null;
	this.fontSize = null;
	this.style = null;
	this.backgroundColor = null;
	this.color = null;
	this.justification = null;
	this.direction = null;
}
