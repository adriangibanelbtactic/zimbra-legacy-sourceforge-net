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
* Creates an empty list of mail items.
* @constructor
* @class
* This class represents a list of mail items (conversations, messages, or
* attachments). We retain a handle to the search that generated the list for
* two reasons: so that we can redo the search if necessary, and so that we
* can get the folder ID if this list represents folder contents.
*
* @author Conrad Damon
* @param type		type of mail item (see ZmItem for constants)
* @param appCtxt	global app context
* @param search		the search that generated this list
*/
function ZmMailList(type, appCtxt, search) {

	ZmList.call(this, type, appCtxt, search);

	this.convId = null; // for msg list within a conv

	// mail list can be changed via folder or tag action (eg "Mark All Read")
	var folderTree = appCtxt.getTree(ZmOrganizer.FOLDER);
	if (folderTree) {
		this._folderChangeListener = new AjxListener(this, this._folderTreeChangeListener);
		folderTree.addChangeListener(this._folderChangeListener);
	}
};

ZmMailList.prototype = new ZmList;
ZmMailList.prototype.constructor = ZmMailList;

ZmMailList.prototype.toString = 
function() {
	return "ZmMailList";
};

/**
* Override so that we can specify "tcon" attribute for conv move - we don't want
* to move messages in certain system folders as a side effect. Also, we need to
* update the UI based on the response if we're moving convs, since the 
* notifications only tell us about moved messages.
*
* @param items		[Array]			a list of items to move
* @param folder		[ZmFolder]		destination folder
* @param attrs		[Object]		additional attrs for SOAP command
*/
ZmMailList.prototype.moveItems =
function(items, folder, attrs) {
	if (this.type != ZmItem.CONV) {
		ZmList.prototype.moveItems.call(this, items, folder, attrs);
		return;
	}
	
	var chars = ["-"];
	var searchFolder = this.search ? this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(this.search.folderId) : null;
	var folders = [ZmFolder.ID_TRASH, ZmFolder.ID_SPAM, ZmFolder.ID_SENT];
	for (var i = 0; i < folders.length; i++)
		if (!(searchFolder && searchFolder.isUnder(folders[i])))
			chars.push(ZmFolder.TCON_CODE[folders[i]]);
	var attrs = new Object();
	attrs.tcon = this._getTcon();
	attrs.l = folder.id;
	var respCallback = new AjxCallback(this, this._handleResponseMoveItems, [folder]);
	this._itemAction(items, "move", attrs, respCallback);
};

ZmMailList.prototype._handleResponseMoveItems =
function(args) {
	var folder		= args[0];
	var result		= args[1];

	var movedItems = result.getResponse();	
	if (movedItems && movedItems.length) {
		this.moveLocal(movedItems, folder.id);
		for (var i = 0; i < movedItems.length; i++)
			movedItems[i].moveLocal(folder.id);
		this._eventNotify(ZmEvent.E_MOVE, movedItems);
	}
};

/**
* Marks items as "spam" or "not spam". If they're marked as "not spam", a target folder
* may be provided.
*
* @param items			[Array]			a list of items to move
* @param markAsSpam		[boolean]		if true, mark as "spam"
* @param folder			[ZmFolder]*		destination folder
*/
ZmMailList.prototype.spamItems = 
function(items, markAsSpam, folder) {

	var action = markAsSpam ? "spam" : "!spam";

	var attrs = new Object();
	attrs.tcon = this._getTcon();
	if (folder) attrs.l = folder.id;

	var respCallback = new AjxCallback(this, this._handleResponseSpamItems, [markAsSpam, folder]);
	this._itemAction(items, action, attrs, respCallback);
};

ZmMailList.prototype._handleResponseSpamItems =
function(args) {
	var markAsSpam	= args[0];
	var folder		= args[1];
	var result		= args[2];
	
	var movedItems = result.getResponse();
	if (movedItems && movedItems.length) {
		folderId = markAsSpam ? ZmFolder.ID_SPAM : (folder ? folder.id : ZmFolder.ID_INBOX);
		this.moveLocal(movedItems, folderId);
		for (var i = 0; i < movedItems.length; i++)
			movedItems[i].moveLocal(folderId);
		this._eventNotify(ZmEvent.E_MOVE, movedItems);
	}
};

/**
* Override so that delete of a conv in Trash doesn't hard-delete its msgs in
* other folders.
*
* @param items			[Array]			list of items to delete
* @param hardDelete		[boolean]		whether to force physical removal of items
* @param attrs			[Object]		additional attrs for SOAP command
*/
ZmMailList.prototype.deleteItems =
function(items, folder, attrs) {
	if (this.type == ZmItem.CONV || this._mixedType == ZmItem.CONV) {
		var searchFolder = this.search ? this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(this.search.folderId) : null;
		if (searchFolder && searchFolder.isInTrash()) {
			if (!attrs) attrs = {};
			attrs.tcon = ZmFolder.TCON_CODE[ZmFolder.ID_TRASH];
		}
	}
	ZmList.prototype.deleteItems.call(this, items, folder, attrs);
};

ZmMailList.prototype.markRead =
function(items, on) {
	this.flagItems(items, "read", on);
};

// When a conv or msg is moved to Trash, it is marked read by the server.
ZmMailList.prototype.moveLocal =
function(items, folderId) {
	ZmList.prototype.moveLocal.call(this, items, folderId);
	if (folderId != ZmFolder.ID_TRASH) return;

	var flaggedItems = new Array();
	for (var i = 0; i < items.length; i++) {
		if (items[i].isUnread) {
			items[i].flagLocal(ZmItem.FLAG_UNREAD, false);
			flaggedItems.push(items[i]);
		}
	}
	if (flaggedItems.length)
		this._eventNotify(ZmEvent.E_FLAGS, flaggedItems, {flags: [ZmItem.FLAG_UNREAD]});
};

ZmMailList.prototype.notifyCreate = 
function(convs, msgs) {
	var searchFolder = this.search ? this.search.folderId : null;
	var createdItems = new Array();
	var flaggedItems = new Array();
	var modifiedItems = new Array();
	var fields = new Object();
	if (this.type == ZmItem.CONV && searchFolder) {
		// handle new convs first so we can set their fragments from new msgs
		for (var id in convs) {
			var conv = convs[id];
			if (conv.folders && conv.folders[searchFolder]) {
				var index = this._getSortIndex(conv, this.search.sortBy);
				this.add(conv, index); // add to beginning for now
				conv.list = this;
				createdItems.push(conv);
			}
		}
		for (var id in msgs) {
			var msg = msgs[id];
			var cid = msg.cid;
			var conv = this.getById(cid);
			if (conv) {
				// got a new msg for a conv that has no msg list - happens when virt conv
				// becomes real (on its second msg) - create a msg list
				if (!conv.msgs) {
					conv.msgs = new ZmMailList(ZmItem.MSG, this._appCtxt);
					conv.msgs.addChangeListener(conv._listChangeListener);
				}
				var index = conv.msgs._getSortIndex(msg, conv._sortBy);
				conv.msgs.add(msg, index);
				msg.list = conv.msgs;
				if (!msg.isSent) {
					conv.isUnread = true;
					flaggedItems.push(conv);
				}
				if (conv.fragment != msg.fragment) {
					conv.fragment = msg.fragment;
					fields[ZmItem.F_FRAGMENT] = true;
				}
				modifiedItems.push(conv);
			}
		}
	} else if (this.type == ZmItem.MSG) {
		for (var id in msgs) {
			var msg = msgs[id];
			if (this.convId) { // MLV within conv
				if (msg.cid == this.convId && !this.getById(msg.id)) {
					this.add(msg, 0); // add to top of msg list
					msg.list = this;
					createdItems.push(msg);
				}
			} else { // MLV (traditional)
				if (msg.folderId == searchFolder) {
					this.add(msg, 0); // add to top of msg list
					msg.list = this;
					createdItems.push(msg);
				}
			}
		}
	}
	if (createdItems.length)
		this._eventNotify(ZmEvent.E_CREATE, createdItems);
	if (flaggedItems.length)
		this._eventNotify(ZmEvent.E_FLAGS, flaggedItems, {flags: [ZmItem.FLAG_UNREAD]});
	if (modifiedItems.length)
		this._eventNotify(ZmEvent.E_MODIFY, modifiedItems, {fields: fields});
};

/**
* Convenience method for adding messages to a conv on the fly. The specific use case for
* this is when a virtual conv becomes real. We basically add the new message(s) to the
* old (virtual) conv's message list.
*
* @param msgs		hash of messages to add
*/
ZmMailList.prototype.addMsgs =
function(msgs) {
	var addedMsgs = new Array();
	for (var id in msgs) {
		var msg = msgs[id];
		if (msg.cid == this.convId) {
			this.add(msg, 0);
			msg.list = this;
			addedMsgs.push(msg);
		}
	}
	if (addedMsgs.length)
		this._eventNotify(ZmEvent.E_CREATE, addedMsgs);
};

ZmMailList.prototype.remove = 
function(item, bForce) {
	// Don't really remove an item if this is a list of msgs of a conv b/c a
	// msg is always going to be part of a conv unless its a hard delete!
	if (!this.convId || bForce)
		ZmList.prototype.remove.call(this, item);
};

ZmMailList.prototype.clear =
function() {
	// remove listeners for this list from folder tree and tag list
	if (this._folderChangeListener)
		this._appCtxt.getTree(ZmOrganizer.FOLDER).removeChangeListener(this._folderChangeListener);
	if (this._tagChangeListener)
		this._appCtxt.getTree(ZmOrganizer.TAG).removeChangeListener(this._tagChangeListener);

	ZmList.prototype.clear.call(this);
};

/*
* Returns the insertion point for the given item into this list. If we're not sorting by
* date, returns 0 (the item will be inserted at the top of the list).
*
* @param item		[ZmMailItem]	a mail item
* @param sortBy		[constant]		sort order
*/
ZmMailList.prototype._getSortIndex =
function(item, sortBy) {
	if (!sortBy || (sortBy != ZmSearch.DATE_DESC && sortBy != ZmSearch.DATE_DESC))
		return 0;
	
	var itemDate = parseInt(item.date);
	var a = this.getArray();
	for (var i = 0; i < a.length; i++) {
		var date = parseInt(a[i].date);
		if (this.search.sortBy == ZmSearch.DATE_DESC && (itemDate > date))
			return i;
		if (this.search.sortBy == ZmSearch.DATE_ASC && (itemDate < date))
			return i;
	}
	return i;
};

ZmMailList.prototype._getTcon =
function() {
	var chars = ["-"];
	var searchFolder = this.search ? this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(this.search.folderId) : null;
	var folders = [ZmFolder.ID_TRASH, ZmFolder.ID_SPAM, ZmFolder.ID_SENT];
	for (var i = 0; i < folders.length; i++)
		if (!(searchFolder && searchFolder.isUnder(folders[i])))
			chars.push(ZmFolder.TCON_CODE[folders[i]]);

	return chars.join("");
};

ZmMailList.prototype._folderTreeChangeListener = 
function(ev) {
	if (this.size() == 0) return;

	var flag = ev.getDetail("flag");
	var view = this._appCtxt.getCurrentViewId();
	var ctlr = this._appCtxt.getCurrentController();

	if (ev.event == ZmEvent.E_FLAGS && (flag == ZmItem.FLAG_UNREAD)) {
		if (this.type == ZmItem.CONV) {
			if (view == ZmController.CONVLIST_VIEW && ctlr._currentSearch.hasUnreadTerm)
				this._redoSearch(ctlr, view);
			return false;
		} else if (this.type == ZmItem.MSG) {
			if (view == ZmController.TRAD_VIEW && ctlr._currentSearch.hasUnreadTerm) {
				this._redoSearch(ctlr, view);
				return false;
			} else {
				var on = ev.getDetail("state");
				var organizer = ev.getDetail("item");
				var flaggedItems = new Array();
				var list = this.getArray();
				for (var i = 0; i < list.length; i++) {
					var msg = list[i];
					if ((organizer.type == ZmOrganizer.FOLDER && msg.folderId == organizer.id) ||
						(organizer.type == ZmOrganizer.TAG && msg.hasTag(organizer.id))) {
						msg.isUnread = on;
						flaggedItems.push(msg);
					}
				}
				if (flaggedItems.length)
					this._eventNotify(ZmEvent.E_FLAGS, flaggedItems, {flags: [flag]});
			}
		}
	} else if (ev.event == ZmEvent.E_DELETE &&
			   ev.source instanceof ZmFolder && 
			   ev.source.id == ZmFolder.ID_TRASH) 
	{
		// user emptied trash - reset a bunch of stuff w/o having to redo the search
		ctlr.getCurrentView().setOffset(0);
		ctlr._resetNavToolBarButtons(view);
		ctlr._showListRange(view);
	}
};

ZmMailList.prototype._tagTreeChangeListener = 
function(ev) {
	if (this.size() == 0) return;

	var flag = ev.getDetail("flag");
	if (ev.event == ZmEvent.E_FLAGS && (flag == ZmItem.FLAG_UNREAD)) {
		return this._folderTreeChangeListener(ev);
	} else {
		return ZmList.prototype._tagTreeChangeListener.call(this, ev);
	}
};

ZmMailList.prototype._redoSearch = 
function(ctlr, view) {
	var sc = this._appCtxt.getSearchController();
	sc.redoSearch(ctlr._currentSearch);
};
