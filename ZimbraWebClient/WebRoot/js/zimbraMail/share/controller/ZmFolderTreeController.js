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

/**
* Creates a folder tree controller.
* @constructor
* @class
* This class controls a tree display of folders.
*
* @author Conrad Damon
* @param appCtxt	[ZmAppCtxt]		app context
* @param type		[constant]*		type of organizer we are displaying/controlling (folder or search)
* @param dropTgt	[DwtDropTgt]	drop target for this type
*/
function ZmFolderTreeController(appCtxt, type, dropTgt) {

	if (arguments.length == 0) return;

	type = type ? type : ZmOrganizer.FOLDER;
	dropTgt = dropTgt ? dropTgt : new DwtDropTarget(ZmFolder, ZmSearchFolder, ZmConv, ZmMailMsg, ZmContact);
	ZmTreeController.call(this, appCtxt, type, dropTgt);

	this._listeners[ZmOperation.NEW_FOLDER] = new AjxListener(this, this._newListener);
	this._listeners[ZmOperation.RENAME_FOLDER] = new AjxListener(this, this._renameListener);
};

ZmFolderTreeController.prototype = new ZmTreeController;
ZmFolderTreeController.prototype.constructor = ZmFolderTreeController;

// Public methods

ZmFolderTreeController.prototype.toString = 
function() {
	return "ZmFolderTreeController";
};

/**
* Enables/disables operations based on context.
*
* @param parent		[DwtControl]	the widget that contains the operations
* @param id			[int]			ID of the currently selected/activated organizer
*/
ZmFolderTreeController.prototype.resetOperations = 
function(parent, type, id) {
	var deleteText = ZmMsg.del;
	var folder = this._dataTree.getById(id);
	// user folder or Folders header
	if (id == ZmOrganizer.ID_ROOT || (!folder.isSystem())) {
		parent.enableAll(true);
		parent.enable(ZmOperation.SYNC, folder.isFeed());
	// system folder
	} else {
		parent.enableAll(false);
		// can't create folders under Drafts or Junk
		if (id == ZmFolder.ID_INBOX || id == ZmFolder.ID_SENT || id == ZmFolder.ID_TRASH)
			parent.enable(ZmOperation.NEW_FOLDER, true);
		// "Delete" for Junk and Trash is "Empty"
		if (id == ZmFolder.ID_SPAM || id == ZmFolder.ID_TRASH) {
			deleteText = (id == ZmFolder.ID_SPAM) ? ZmMsg.emptyJunk : ZmMsg.emptyTrash;
			parent.enable(ZmOperation.DELETE, true);
		}
	}
	parent.enable(ZmOperation.EXPAND_ALL, (folder.size() > 0));
	if (id != ZmOrganizer.ID_ROOT)
		parent.enable(ZmOperation.MARK_ALL_READ, (folder.numUnread > 0));

	var op = parent.getOp(ZmOperation.DELETE);
	if (op)
		op.setText(deleteText);
};

// Private methods

/*
* Returns ops available for "Folders" container.
*/
ZmFolderTreeController.prototype._getHeaderActionMenuOps =
function() {
	return [ZmOperation.NEW_FOLDER, ZmOperation.EXPAND_ALL];
};

/*
* Returns ops available for folder items.
*/
ZmFolderTreeController.prototype._getActionMenuOps =
function() {
	var list = new Array();
	list.push(ZmOperation.NEW_FOLDER,
			  ZmOperation.MARK_ALL_READ,
			  ZmOperation.DELETE,
			  ZmOperation.RENAME_FOLDER,
			  ZmOperation.MOVE,
			  ZmOperation.EXPAND_ALL,
			  ZmOperation.SYNC);
	return list;
};

/*
* Returns a "New Folder" dialog.
*/
ZmFolderTreeController.prototype._getNewDialog =
function() {
	return this._appCtxt.getNewFolderDialog();
};

/*
* Returns a "Rename Folder" dialog.
*/
ZmFolderTreeController.prototype._getRenameDialog =
function() {
	return this._appCtxt.getRenameFolderDialog();
};

/*
* Called when a left click occurs (by the tree view listener). The folder that
* was clicked may be a search, since those can appear in the folder tree. The
* appropriate search will be performed.
*
* @param folder		ZmOrganizer		folder or search that was clicked
*/
ZmFolderTreeController.prototype._itemClicked =
function(folder) {
	if (folder.type == ZmOrganizer.SEARCH) {
		// if the clicked item is a search (within the folder tree), hand
		// it off to the search tree controller
		var stc = this._opc.getTreeController(ZmOrganizer.SEARCH);
		stc._itemClicked(folder);
	} else {
		var searchController = this._appCtxt.getSearchController();
		var types = searchController.getTypes(ZmSearchToolBar.FOR_ANY_MI);
		searchController.search({query: folder.createQuery(), types: types});
	}
};

// Actions

/*
* Creates a new organizer and adds it to the tree of that type.
*
* @param parent		[ZmFolder]		parent of the new organizer
* @param name		[string]		name of the new organizer
* @param search		[ZmSearch]		search object (saved search creation only)
*/
ZmFolderTreeController.prototype._doCreate =
function(parent, name, search, url) {
	parent.create(name, search, url);
};

// Listeners

/*
* Deletes a folder. If the folder is in Trash, it is hard-deleted. Otherwise, it
* is moved to Trash (soft-delete). If the folder is Trash or Junk, it is emptied.
* A warning dialog will be shown before the Junk folder is emptied.
*
* @param ev		[DwtUiEvent]	the UI event
*/
ZmFolderTreeController.prototype._deleteListener = 
function(ev) {
	var organizer = this._getActionedOrganizer(ev);
	if (organizer.isInTrash()) {
		this._doDelete(organizer);
	} else if (organizer.id == ZmFolder.ID_SPAM) {
		this._pendingActionData = organizer;
		if (!this._deleteShield) {
			this._deleteShield = new DwtMessageDialog(this._shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON]);
			this._deleteShield.registerCallback(DwtDialog.YES_BUTTON, this._deleteShieldYesCallback, this, organizer);
			this._deleteShield.registerCallback(DwtDialog.NO_BUTTON, this._clearDialog, this, this._deleteShield);
		}
		this._deleteShield.setMessage(ZmMsg.confirmEmptyJunk, DwtMessageDialog.WARNING_STYLE);
		this._deleteShield.popup();
    } else {
		this._doMove(organizer, this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(ZmFolder.ID_TRASH));
	}
};

/*
* Don't allow dragging of system folders.
*
* @param ev		[DwtDragEvent]		the drag event
*/
ZmFolderTreeController.prototype._dragListener =
function(ev) {
	if (ev.action == DwtDragEvent.DRAG_START) {
		var folder = ev.srcData = ev.srcControl.getData(Dwt.KEY_OBJECT);
		if (!(folder instanceof ZmFolder) || folder.isSystem())
			ev.operation = Dwt.DND_DROP_NONE;
	}
};

/*
* Handles the potential drop of something onto a folder. When something is dragged over
* a folder, returns true if a drop would be allowed. When something is actually dropped,
* performs the move. If items are being dropped, the source data is not the items
* themselves, but an object with the items (data) and their controller, so they can be
* moved appropriately.
*
* @param ev		[DwtDropEvent]		the drop event
*/
ZmFolderTreeController.prototype._dropListener =
function(ev) {
	if (ev.action == DwtDropEvent.DRAG_ENTER) {
		var srcData = ev.srcData;
		var dropFolder = ev.targetControl.getData(Dwt.KEY_OBJECT);
		if (srcData instanceof ZmFolder) {
			DBG.println(AjxDebug.DBG3, "DRAG_ENTER: " + srcData.name + " on to " + dropFolder.name);
			var dragFolder = srcData; // note that folders cannot be moved as a list
			ev.doIt = dropFolder.mayContain(dragFolder);
		} else if (srcData instanceof ZmTag) {
			ev.doIt = false; // tags cannot be moved
		} else {
			if (!this._dropTgt.isValidTarget(srcData.data)) {
				ev.doIt = false;
			} else {
				ev.doIt = dropFolder.mayContain(srcData.data);
			}
		}
	} else if (ev.action == DwtDropEvent.DRAG_DROP) {
		var dropFolder = ev.targetControl.getData(Dwt.KEY_OBJECT);
		DBG.println(AjxDebug.DBG3, "DRAG_DROP: " + ev.srcData.name + " on to " + dropFolder.name);
		var srcData = ev.srcData;
		if (srcData instanceof ZmFolder) {
			this._doMove(srcData, dropFolder);
		} else {
			var data = srcData.data;
			var ctlr = srcData.controller;
			var items = (data instanceof Array) ? data : [data];
			ctlr._doMove(items, dropFolder);
		}
	}
};

/*
* Handles a search folder being moved from Folders to Searches.
*
* @param ev			[ZmEvent]		a change event
* @param treeView	[ZmTreeView]	a tree view
*/
ZmFolderTreeController.prototype._changeListener =
function(ev, treeView) {
	var organizers = ev.getDetail("organizers");
	if (!organizers && ev.source)
		organizers = [ev.source];

	// handle one organizer at a time
	for (var i = 0; i < organizers.length; i++) {
		var organizer = organizers[i];
		var id = organizer.id;
		var fields = ev.getDetail("fields");
		var node = treeView.getTreeItemById(id);
		var parentNode = organizer.parent ? treeView.getTreeItemById(organizer.parent.id) : null;
		if ((organizer.type == ZmOrganizer.SEARCH && 
			(organizer.parent.tree.type == ZmOrganizer.SEARCH || id == ZmOrganizer.ID_ROOT)) &&
		 	(ev.event == ZmEvent.E_MOVE || (ev.event == ZmEvent.E_MODIFY && (fields && fields[ZmOrganizer.F_PARENT])))) {
			DBG.println(AjxDebug.DBG3, "Moving search from Folders to Searches");
			if (node)
				node.dispose();
			// send a CREATE event to search tree controller to get it to add node
			var newEv = new ZmEvent(ZmEvent.S_SEARCH);
			newEv.set(ZmEvent.E_CREATE, organizer);
			var stc = this._opc.getTreeController(ZmOrganizer.SEARCH);
			var stv = stc.getTreeView(treeView.overviewId);
			stc._changeListener(newEv, stv);
		} else {
			ZmTreeController.prototype._changeListener.call(this, ev, treeView);
		}
	}
};

// Miscellaneous

/*
* Returns a title for moving a folder.
*/
ZmFolderTreeController.prototype._getMoveDialogTitle =
function() {
	return AjxStringUtil.resolve(ZmMsg.moveFolder, this._pendingActionData.name);
};
