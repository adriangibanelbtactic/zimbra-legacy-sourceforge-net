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

function ZmMailMsgListView(parent, className, posStyle, mode, controller, dropTgt) {
	this._mode = mode;
	var headerList = this._getHeaderList(parent);
	ZmMailListView.call(this, parent, className, posStyle, mode, ZmItem.MSG, controller, headerList, dropTgt);
};

ZmMailMsgListView.prototype = new ZmMailListView;
ZmMailMsgListView.prototype.constructor = ZmMailMsgListView;


// Consts

ZmMailMsgListView.MSGLIST_REPLENISH_THRESHOLD 	= 0;
ZmMailMsgListView.MLV_COLWIDTH_ICON 			= 20;
ZmMailMsgListView.MLV_COLWIDTH_FROM 			= 105;
ZmMailMsgListView.MLV_COLWIDTH_FOLDER 			= 47;
ZmMailMsgListView.MLV_COLWIDTH_SIZE 			= 45;
ZmMailMsgListView.MLV_COLWIDTH_DATE 			= 75;


// Public methods

ZmMailMsgListView.prototype.toString = 
function() {
	return "ZmMailMsgListView";
};

ZmMailMsgListView.prototype.createHeaderHtml = 
function(defaultColumnSort) {

	ZmMailListView.prototype.createHeaderHtml.call(this, defaultColumnSort);
	
	// Show "From" or "To" depending on which folder we're looking at
	if (this._mode == ZmController.TRAD_VIEW) {
		var isFolder = this._isSentOrDraftsFolder();

		// set the from column name based on query string
		var fromColIdx = this.getColIndexForId(ZmListView.FIELD_PREFIX[ZmItem.F_FROM]);
		var fromColSpan = Dwt.getDomObj(this.getDocument(), DwtListView.HEADERITEM_LABEL + this._headerList[fromColIdx]._id);
		if (fromColSpan)
			fromColSpan.innerHTML = "&nbsp;" + (isFolder.sent || isFolder.drafts ? ZmMsg.to : ZmMsg.from);

		// set the received column name based on query string
		var recdColIdx = this.getColIndexForId(ZmListView.FIELD_PREFIX[ZmItem.F_DATE]);
		var recdColSpan = Dwt.getDomObj(this.getDocument(), DwtListView.HEADERITEM_LABEL + this._headerList[recdColIdx]._id);
		if (recdColSpan) {
			var html = "&nbsp;";
			if (isFolder.sent) {
				html += ZmMsg.sent;
			} else if (isFolder.drafts) {
				html += ZmMsg.lastSaved;
			} else {
				html += ZmMsg.received;
			}
			recdColSpan.innerHTML = html;
		}
	}
};

ZmMailMsgListView.prototype.markUIAsRead = 
function(items, on) {
	var doc = this.getDocument();
	for (var i = 0; i < items.length; i++) {
		var item = items[i];
		var row = Dwt.getDomObj(doc, this._getFieldId(item, ZmItem.F_ITEM_ROW));
		if (row) {
			var className =  on ? "" : "Unread";
			// don't worry about unread/read trash if in trad. view
			if (this._mode != ZmController.TRAD_VIEW) {
				var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(item.folderId);
				if (folder && folder.isInTrash())
					className = (className ? (className + " ") : "") + "Trash";
			}
			if (item.isSent)
				className = (className ? (className + " ") : "") + "Sent";

			row.className = className;
		}
		var img = Dwt.getDomObj(doc, this._getFieldId(item, ZmItem.F_STATUS));
		if (img && img.parentNode) {
			if (on) {
				var imageInfo;
				if (item.isInvite())
					imageInfo = "Appointment";
				else if (item.isDraft)
					imageInfo = "MsgStatusDraft";
				else if (item.isReplied)
					imageInfo = "MsgStatusReply";
				else if (item.isForwarded)
					imageInfo = "MsgStatusForward";
				else if (item.isSent)
					imageInfo = "MsgStatusSent";
				else
					imageInfo = "MsgStatusRead";
			} else {
				imageInfo = item.isInvite() ? "Appointment" : "MsgStatusUnread";
			}
			AjxImg.setImage(img.parentNode, imageInfo);
		}
	}
};

ZmMailMsgListView.prototype.resetHeight = 
function(newHeight) {
	this.setSize(Dwt.DEFAULT, newHeight);
	Dwt.setSize(this._parentEl, Dwt.DEFAULT, newHeight-DwtListView.HEADERITEM_HEIGHT);
};


ZmMailMsgListView.prototype.getReplenishThreshold = 
function() {
	return ZmMailMsgListView.MSGLIST_REPLENISH_THRESHOLD;
};


// Private / protected methods

ZmMailMsgListView.prototype._createItemHtml =
function(msg, now, isDndIcon, isMixedView) {

	// bug fix #3595 - dont hilite if search was in:<folder name>
	var isMatched = msg.isInHitList() && this._mode == ZmController.CONV_VIEW && this._appCtxt.getCurrentSearch().folderId == null;
	var	div = this._getDiv(msg, isDndIcon, isMatched);
	div.className = div._styleClass;

	var htmlArr = new Array();
	var idx = 0;
	
	// Table
	idx = this._getTable(htmlArr, idx, isDndIcon);

	// Row
	var className = null;
	if (this._mode == ZmController.CONV_VIEW) {
		var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(msg.folderId);
		if (folder != null && folder.isInTrash())
			className = "Trash";
	}
	if (msg.isUnread)
		className = (className ? (className + " ") : "") + "Unread";

	idx = this._getRow(htmlArr, idx, msg, className);

	for (var i = 0; i < this._headerList.length; i++) {
		if (!this._headerList[i]._visible)
			continue;

		var id = this._headerList[i]._id;
		// IE does not obey box model properly so we over compensate :(
		var width = AjxEnv.isIE ? (this._headerList[i]._width + 4) : this._headerList[i]._width;
		
		if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_FLAG]) == 0) {
			// Flags
			idx = this._getField(htmlArr, idx, msg, ZmItem.F_FLAG, i);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_TAG]) == 0) {
			// Tags
			idx = this._getField(htmlArr, idx, msg, ZmItem.F_TAG, i);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_STATUS]) == 0) {
			// Status
			htmlArr[idx++] = "<td width=" + width + "><center>";
			var imageInfo;
			if (msg.isInvite())
				imageInfo = "Appointment";
			else if (msg.isDraft)
				imageInfo = "MsgStatusDraft";
			else if (msg.isReplied)
				imageInfo = "MsgStatusReply";
			else if (msg.isForwarded)
				imageInfo = "MsgStatusForward";
			else if (msg.isSent)
				imageInfo = "MsgStatusSent";
			else
				imageInfo = msg.isUnread ? "MsgStatusUnread" : "MsgStatusRead";
			htmlArr[idx++] = AjxImg.getImageHtml(imageInfo, null, ["id='", this._getFieldId(msg, ZmItem.F_STATUS), "'"].join(""));	
			htmlArr[idx++] = "</center></td>";
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_FROM]) == 0) {
			// Participants
			htmlArr[idx++] = "<td width=" + width + ">";
			if (this._mode == ZmController.TRAD_VIEW && 
				(msg.folderId == ZmFolder.ID_SENT || msg.folderId == ZmFolder.ID_DRAFTS)) 
			{
				var addrs = msg.getAddresses(ZmEmailAddress.TO).getArray();
		
				// default to FROM addresses if no TO: found
				if (addrs == null || addrs.length == 0)
					addrs = msg.getAddresses(ZmEmailAddress.FROM).getArray();
				
				if (addrs && addrs.length) {
					var fieldId = this._getFieldId(msg, ZmItem.F_PARTICIPANT);
					var origLen = addrs.length;
					var partsElided = false; // may need to get this from server...
					var parts = this._fitParticipants(addrs, partsElided, 145);
					for (var j = 0; j < parts.length; j++) {
						if (j == 1 && (partsElided || parts.length < origLen)) {
							htmlArr[idx++] = AjxStringUtil.ELLIPSIS;
						} else if (parts.length > 1 && j > 0) {
							htmlArr[idx++] = ", ";
						}
						var partId = fieldId + "_" + parts[j].index;
						htmlArr[idx++] = "<span style='white-space: nowrap' id='" + partId + "'>";
						htmlArr[idx++] = parts[j].name;
						htmlArr[idx++] = "</span>";
						if (parts.length == 1 && parts.length < origLen)
							htmlArr[idx++] = AjxStringUtil.ELLIPSIS;
					}
				}		
			} else {
				var fromAddr = msg.getAddress(ZmEmailAddress.FROM);
				if (fromAddr) {
			   		htmlArr[idx++] = "<span style='white-space: nowrap' id='" + this._getFieldId(msg, ZmItem.F_FROM) + "'>";
			   		var name = fromAddr.getName() || fromAddr.getDispName();
					htmlArr[idx++] = AjxStringUtil.htmlEncode(name);
					// XXX: IM HACK
					if (this._appCtxt.get(ZmSetting.IM_ENABLED)) {
				   		var contacts = ZmAppCtxt.getFromShell(this.shell).getApp(ZmZimbraMail.CONTACTS_APP).getContactList();
						var contact = contacts.getContactByEmail(fromAddr.getAddress());
						if (contact && contact.hasIMProfile())
							htmlArr[idx++] = AjxImg.getImageHtml(contact.isIMAvailable() ? "ImAvailable" : "ImUnavailable");
					}
			   		htmlArr[idx++] = "</span>";
					if (AjxEnv.isNav)
						htmlArr[idx++] = ZmListView._fillerString;
				}
			}
			htmlArr[idx++] = "</td>";
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_ATTACHMENT]) == 0) {
			// Attachments
			idx = this._getField(htmlArr, idx, msg, ZmItem.F_ATTACHMENT, i);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_SUBJECT]) == 0) {
			// Fragment
			if (this._mode == ZmController.CONV_VIEW) {
				htmlArr[idx++] = "<td id='" + this._getFieldId(msg, ZmItem.F_FRAGMENT) + "'";
				htmlArr[idx++] = AjxEnv.isSafari ? " style='width:auto;'><div style='overflow:hidden'>" : " width=100%>";
				htmlArr[idx++] = AjxStringUtil.htmlEncode(msg.fragment, true);
			} else {
				htmlArr[idx++] = "<td id='" + this._getFieldId(msg, ZmItem.F_SUBJECT) + "'";
				htmlArr[idx++] = AjxEnv.isSafari ? " style='width:auto;'><div style='overflow:hidden'>" : " width=100%>";
				var subj = msg.getSubject() || ZmMsg.noSubject;
				htmlArr[idx++] = AjxStringUtil.htmlEncode(subj);
				if (this._appCtxt.get(ZmSetting.SHOW_FRAGMENTS) && msg.fragment) {
					htmlArr[idx++] = "<span class='ZmConvListFragment'> - ";
					htmlArr[idx++] = AjxStringUtil.htmlEncode(msg.fragment, true);
					htmlArr[idx++] = "</span>";
				}
			}
			htmlArr[idx++] = AjxEnv.isNav ? ZmListView._fillerString : "";
			htmlArr[idx++] = AjxEnv.isSafari ? "</div>" : "";
			htmlArr[idx++] = "</td>";
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_FOLDER]) == 0) {
			// Folder
			htmlArr[idx++] = "<td width=" + width + ">";
			htmlArr[idx++] = "<nobr id='" + this._getFieldId(msg, ZmItem.F_FOLDER) + "'>"; // required for IE bug
			var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(msg.folderId);
			htmlArr[idx++] = folder ? folder.getName() : "";
			htmlArr[idx++] = "</nobr>";
			if (AjxEnv.isNav)
				htmlArr[idx++] = ZmListView._fillerString;
			htmlArr[idx++] = "</td>";
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_SIZE]) == 0) {
			// Size
			htmlArr[idx++] = "<td width=" + this._headerList[i]._width + "><nobr>";
			htmlArr[idx++] = AjxUtil.formatSize(msg.size);
			if (AjxEnv.isNav)
				htmlArr[idx++] = ZmListView._fillerString;
			htmlArr[idx++] = "</td>";
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_DATE]) == 0) {
			// Date
			idx = this._getField(htmlArr, idx, msg, ZmItem.F_DATE, i, now);
		} else if (isMixedView && id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_ICON]) == 0) {
			// Type icon (mixed view only)
			idx = this._getField(htmlArr, idx, msg, ZmItem.F_ITEM_TYPE, i);
		}
	}
	
	htmlArr[idx++] = "</tr></table>";
	
	div.innerHTML = htmlArr.join("");
	return div;
};


// Listeners

ZmMailMsgListView.prototype._changeListener =
function(ev) {
	var items = ev.getDetail("items");
	if ((ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE) && this._mode == ZmController.CONV_VIEW) {
		if (!this._controller.handleDelete()) {
			this._changeTrashStatus(items);
			this._changeFolderName(items);
		}
	} else if (this._mode == ZmController.CONV_VIEW && ev.event == ZmEvent.E_CREATE) {
		var conv = this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getConvController().getConv();
		var msg = items[0].type == ZmItem.MSG ? items[0] : null;
		if (conv && msg && (msg.cid == conv.id)) {
			ZmMailListView.prototype._changeListener.call(this, ev);
		}
	} else if (ev.event == ZmEvent.E_FLAGS) { // handle "replied" and "forwarded" flags
		var flags = ev.getDetail("flags");
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			var img = Dwt.getDomObj(this.getDocument(), this._getFieldId(item, ZmItem.F_STATUS));
			if (img && img.parentNode) {
				for (var j = 0; j < flags.length; j++) {
					var flag = flags[j];
					var on = item[ZmItem.FLAG_PROP[flag]];
					if (flag == ZmItem.FLAG_REPLIED && on)
						AjxImg.setImage(img.parentNode, "MsgStatusReply");
					else if (flag == ZmItem.FLAG_FORWARDED && on)
						AjxImg.setImage(img.parentNode, "MsgStatusForward");
				}
			}
		}
		ZmMailListView.prototype._changeListener.call(this, ev); // handle other flags
	} else {
		ZmMailListView.prototype._changeListener.call(this, ev);
		if (ev.event == ZmEvent.E_CREATE || ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE)	{
			this._resetColWidth();
		}
	}
};

ZmMailMsgListView.prototype._changeFolderName = 
function(items) {

	for (var i = 0; i < items.length; i++) {
		var folderCell = Dwt.getDomObj(this.getDocument(), this._getFieldId(items[i], ZmItem.F_FOLDER));
		if (folderCell) {
			var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(items[i].folderId);
			if (folder)
				folderCell.innerHTML = folder.getName();
			if (items[i].folderId == ZmFolder.ID_TRASH)
				this._changeTrashStatus([items[i]]);
		}
	}
};

ZmMailMsgListView.prototype._changeTrashStatus = 
function(items) {
	for (var i = 0; i < items.length; i++) {
		var row = Dwt.getDomObj(this.getDocument(), this._getFieldId(items[i], ZmItem.F_ITEM_ROW));
		if (row) {
			var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(items[i].folderId);
			var className = null;
			if (items[i].isUnread)
				className = "Unread";
			if ((folder != null) && folder.isInTrash())
				className = (className ? (className + " ") : "") + "Trash";
			if (items[i].isSent)
				className = (className ? (className + " ") : "") + "Sent";
			row.className = className;
		}
	}
};

ZmMailMsgListView.prototype._getHeaderList =
function(parent) {

	var headerList = new Array();

	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_FLAG], null, "FlagRed", ZmMailMsgListView.MLV_COLWIDTH_ICON, null, null, null, ZmMsg.flag));

	var shell = (parent instanceof DwtShell) ? parent : parent.shell;
	var appCtxt = shell.getData(ZmAppCtxt.LABEL); // this._appCtxt not set until parent constructor is called
	if (appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_TAG], null, "MiniTag", ZmMailMsgListView.MLV_COLWIDTH_ICON, null, null, null, ZmMsg.tag));
	}

	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_STATUS], null, "MsgStatus", ZmMailMsgListView.MLV_COLWIDTH_ICON, null, null, null, ZmMsg.status));
	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_FROM], ZmMsg.from, null, ZmMailMsgListView.MLV_COLWIDTH_FROM, ZmItem.F_FROM, true));
	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_ATTACHMENT], null, "Attachment", ZmMailMsgListView.MLV_COLWIDTH_ICON, null, null, null, ZmMsg.attachment));

	var sortBy = this._mode == ZmController.CONV_VIEW ? null : ZmItem.F_SUBJECT;
	var colName = this._mode == ZmController.CONV_VIEW ? ZmMsg.fragment : ZmMsg.subject;
	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_SUBJECT], colName, null, null, sortBy));

	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_FOLDER], ZmMsg.folder, null, ZmMailMsgListView.MLV_COLWIDTH_FOLDER, null, true));
	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_SIZE], ZmMsg.size, null, ZmMailMsgListView.MLV_COLWIDTH_SIZE, null, true));
	headerList.push(new DwtListHeaderItem(ZmListView.FIELD_PREFIX[ZmItem.F_DATE], ZmMsg.received, null, ZmMailMsgListView.MLV_COLWIDTH_DATE, ZmItem.F_DATE, true));

	return headerList;
};

ZmMailMsgListView.prototype._sortColumn = 
function(columnItem, bSortAsc) {

	// call base class to save new sorting pref
	ZmMailListView.prototype._sortColumn.call(this, columnItem, bSortAsc);

	if (this.getList().size() > 1 && this._sortByString) {
		var controller = this._mode == ZmController.CONV_VIEW
			? this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getConvController()
			: this._appCtxt.getApp(ZmZimbraMail.MAIL_APP).getTradController();
		
		var searchString = controller.getSearchString();

		if (this._mode == ZmController.CONV_VIEW) {
			var conv = controller.getConv();
			if (conv) {
				var respCallback = new AjxCallback(this, this._handleResponseSortColumn, [conv, columnItem, controller]);
				conv.load(searchString, this._sortByString, null, null, null, respCallback);
			}
		} else {
			var params = {query: searchString, types: [ZmItem.MSG], sortBy: this._sortByString, limit: this.getLimit()};
			this._appCtxt.getSearchController().search(params);
		}
	}
};

ZmMailMsgListView.prototype._handleResponseSortColumn =
function(args) {
	var conv		= args[0];
	var columnItem	= args[1];
	var controller	= args[2];
	var result		= args[3];
	
	var list = result.getResponse();
	controller.setList(list); // set the new list returned
	this.setOffset(0);
	this.set(conv.msgs, columnItem);
	this.setSelection(conv.getHotMsg(this.getOffset(), this.getLimit()));
};

ZmMailMsgListView.prototype._getDefaultSortbyForCol = 
function(colHeader) {
	// if not date field, sort asc by default
	return colHeader._sortable != ZmItem.F_DATE;
};

ZmMailMsgListView.prototype._getParentForColResize = 
function() {
	return this.parent;
};
