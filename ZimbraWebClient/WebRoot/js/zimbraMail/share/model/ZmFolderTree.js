/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.1
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.1 ("License"); you may not use this file except in
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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function ZmFolderTree(appCtxt, type) {
	
	ZmTree.call(this, type, appCtxt);
	
	this._evt = new ZmEvent(type);
}

ZmFolderTree.prototype = new ZmTree;
ZmFolderTree.prototype.constructor = ZmFolderTree;

ZmFolderTree.prototype.toString = 
function() {
	return "ZmFolderTree";
}

ZmFolderTree.prototype.loadFromJs =
function(rootFolderObj) {
	if (this.type == ZmOrganizer.FOLDER)
		this.root = ZmFolder.createFromJs(null, rootFolderObj, this);
	else if (this.type == ZmOrganizer.SEARCH)
		this.root = ZmSearchFolder.createFromJs(null, rootFolderObj, this);
	else if (this.type == ZmOrganizer.CALENDAR)
		this.root = ZmCalendar.createFromJs(null, rootFolderObj, this);
}

ZmFolderTree.prototype.getByPath =
function(path) {
	return this.root ? this.root.getByPath(path) : null;
}

ZmFolderTree.prototype._sortFolder =
function(folder) {
	var children = folder.children;
	if (children && children.length) {
		children.sort(ZmFolder.sortCompare);
		for (var i = 0; i < children.length; i++)
			this._sortFolder(children[i]);
	}
}
