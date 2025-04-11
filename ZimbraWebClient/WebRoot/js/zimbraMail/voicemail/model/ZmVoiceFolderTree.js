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
 * Creates an empty voicemail folder tree.
 * @constructor
 * @class
 * This class represents a tree of voicemail folders.
 * 
 * @author Conrad Damon
 * 
 * @param appCtxt	[ZmAppCtxt]		the app context
 */
ZmVoiceFolderTree = function(appCtxt) {
	
	ZmTree.call(this, ZmOrganizer.VOICE, appCtxt);
};

ZmVoiceFolderTree.prototype = new ZmTree;
ZmVoiceFolderTree.prototype.constructor = ZmFolderTree;

// Public Methods

ZmVoiceFolderTree.prototype.toString =
function() {
	return "ZmVoiceFolderTree";
};

/**
 * Loads the folder or the zimlet tree.
 */
ZmVoiceFolderTree.prototype.loadFromJs =
function(rootObj, phone) {
	this.root = ZmVoiceFolderTree.createFromJs(null, rootObj, this, phone);
};

/**
 * Generic function for creating an organizer. Handles any organizer type that comes
 * in the folder list.
 * 
 * @param parent		[ZmFolder]		parent folder
 * @param obj			[object]		JSON with folder data
 * @param tree			[ZmFolderTree]	containing tree
 */
ZmVoiceFolderTree.createFromJs =
function(parent, obj, tree, phone) {
	if (!(obj && obj.id)) return;

	var params = {
		id: obj.id,
		name: obj.name,
		phone: phone,
		callType: obj.name || ZmVoiceFolder.ACCOUNT,
		view: obj.view,
		numUnread: obj.u,
		numTotal: obj.n,
		parent: parent,
		tree: tree
	};
	var folder = new ZmVoiceFolder(params);
	if (parent) {
		parent.children.add(folder);
	}

	if (obj.folder) {
		for (var i = 0, count = obj.folder.length; i < count; i++) {
			ZmVoiceFolderTree.createFromJs(folder, obj.folder[i], tree, phone);
		}
	}

	return folder;
};
