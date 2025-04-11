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


function DwtText(parent, className, posStyle) {

	if (arguments.length == 0) return;
	className = className || "DwtText";
	DwtControl.call(this, parent, className, posStyle);
}

DwtText.prototype = new DwtControl;
DwtText.prototype.constructor = DwtText;

DwtText.prototype.toString = 
function() {
	return "DwtText";
}

DwtText.prototype.setText =
function(text) {
	if (!this._textNode) {
		 this._textNode = document.createTextNode(text);
		 this.getHtmlElement().appendChild(this._textNode);
	} else {
		this._textNode.data = text;
	}
}

DwtText.prototype.getText =
function() {
	return this._textNode.data;
}

DwtText.prototype.getTextNode =
function() {
	return this._textNode;
}
