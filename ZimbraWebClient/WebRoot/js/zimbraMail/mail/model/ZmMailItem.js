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
* Creates a mail item.
* @constructor
* @class
* This class represents a mail item, which may be a conversation or a mail
* message.
*
* @param appCtxt	[ZmAppCtxt]		the app context
* @param type		[constant]		type of object (conv or msg)
* @param id			[int]			unique ID
* @param list		[ZmMailList]	list that contains this mail item
*/
function ZmMailItem(appCtxt, type, id, list) {

	if (arguments.length == 0) return;
	ZmItem.call(this, appCtxt, type, id, list);

	this._initializeParticipants();
}

ZmMailItem.prototype = new ZmItem;
ZmMailItem.prototype.constructor = ZmMailItem;

ZmMailItem.prototype.toString = 
function() {
	return "ZmMailItem";
}

ZmMailItem.prototype.clear =
function() {
	this._clearParticipants();	
	ZmItem.prototype.clear.call(this);
}

ZmMailItem.prototype.getFolderId =
function() {
	if ((this.type == ZmItem.CONV) && this.list.search) {
		return this.list.search.folderId;
	} else {
		return this.folderId;
	}
}

ZmMailItem.prototype.notifyModify =
function(obj) {
	var fields = new Object();
	if (obj.e != null) {
		this._clearParticipants();	
		this._initializeParticipants();	
		for (var i = 0; i < obj.e.length; i++)
			this._parseParticipantNode(obj.e[i]);
		fields[ZmItem.F_PARTICIPANT] = true;
		this._notify(ZmEvent.E_MODIFY, {fields : fields});
	}

	ZmItem.prototype.notifyModify.call(this, obj);
}

ZmMailItem.prototype._initializeParticipants =
function() {
	this.participants = new AjxVector();
	this._participantHash = new Object();
	this.participantsElided = false;
}

ZmMailItem.prototype._clearParticipants =
function() {
	if (this.participants) {
		this.participants.removeAll();
		this.participants = null;
		if (this._participantHash) {
			for (var i in this._participantHash)
				this._participantHash[i] = null;
			this._participantHash = null;
		}
		this.participantsElided = false;
	}
}

ZmMailItem.prototype._getFlags =
function() {
	var list = ZmItem.prototype._getFlags.call(this);
	list.push(ZmItem.FLAG_UNREAD, ZmItem.FLAG_REPLIED, ZmItem.FLAG_FORWARDED);
	return list;
}

ZmMailItem.prototype._markReadLocal =
function(on) {
	this.isUnread = !on;
	this._notify(ZmEvent.E_FLAGS, {flags: [ZmItem.FLAG_UNREAD]});
}

ZmMailItem.prototype._parseParticipantNode = 
function(child) {
	var address = null;

	var id = child.id;
	var ref = child.ref;
	if (id) {
		var addr = child.a;
		var type = ZmEmailAddress.fromSoapType[child.t];
		var name = child.p;
		var dispName = child.d;
		address = new ZmEmailAddress(addr, type, name, dispName);
		
		this._participantHash[id] = address;
	} else if (ref) {
		address = this._participantHash[ref];
	} else {
		this.participantsElided = true;
	}

	if (!this.participantsElided) {
		if (address)
			this.participants.add(address);
	} else {
		this.participantsElided = false;
	}
}
