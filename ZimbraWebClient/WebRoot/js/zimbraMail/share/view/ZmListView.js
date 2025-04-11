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

function ZmListView(parent, className, posStyle, view, type, controller, headerList, dropTgt) {

	if (arguments.length == 0) return;
	DwtListView.call(this, parent, className, posStyle, headerList);

	this.view = view;
	this.type = type;
	this._controller = controller;
	this.setDropTarget(dropTgt);

	// create listeners for changes to the list model, and to tags
	this._listChangeListener = new AjxListener(this, this._changeListener);
	this._appCtxt = this.shell.getData(ZmAppCtxt.LABEL);
	var tagList = this._appCtxt.getTree(ZmOrganizer.TAG);
	if (tagList)
		tagList.addChangeListener(new AjxListener(this, this._tagChangeListener));

	this._csfeMsgFetchSvc = location.protocol + "//" + this.getDocument().domain + this._appCtxt.get(ZmSetting.CSFE_MSG_FETCHER_URI);
}

ZmListView.prototype = new DwtListView;
ZmListView.prototype.constructor = ZmListView;

ZmListView.prototype.toString = 
function() {
	return "ZmListView";
}

ZmListView.FIELD_PREFIX = new Object();
ZmListView.FIELD_PREFIX[ZmItem.F_ITEM_ROW]		= "a";
ZmListView.FIELD_PREFIX[ZmItem.F_ICON]			= "b";
ZmListView.FIELD_PREFIX[ZmItem.F_FLAG]			= "c";
ZmListView.FIELD_PREFIX[ZmItem.F_ATTACHMENT]	= "d";
ZmListView.FIELD_PREFIX[ZmItem.F_TAG]			= "e";
ZmListView.FIELD_PREFIX[ZmItem.F_PARTICIPANT]	= "f";
ZmListView.FIELD_PREFIX[ZmItem.F_FROM]			= "g";
ZmListView.FIELD_PREFIX[ZmItem.F_FRAGMENT]		= "h";
ZmListView.FIELD_PREFIX[ZmItem.F_SUBJECT]		= "i";
ZmListView.FIELD_PREFIX[ZmItem.F_COUNT]			= "j";
ZmListView.FIELD_PREFIX[ZmItem.F_DATE]			= "k";
ZmListView.FIELD_PREFIX[ZmItem.F_STATUS]		= "l";
ZmListView.FIELD_PREFIX[ZmItem.F_FOLDER]		= "m";
ZmListView.FIELD_PREFIX[ZmItem.F_COMPANY]		= "n";
ZmListView.FIELD_PREFIX[ZmItem.F_EMAIL]			= "o";
ZmListView.FIELD_PREFIX[ZmItem.F_PHONE_BUS]		= "p";
ZmListView.FIELD_PREFIX[ZmItem.F_PHONE_MOBILE]	= "q";
ZmListView.FIELD_PREFIX[ZmItem.F_FREE_BUSY]		= "r";
ZmListView.FIELD_PREFIX[ZmItem.F_ITEM_TYPE]		= "s";
ZmListView.FIELD_PREFIX[ZmItem.F_TAG_CELL]		= "t";
ZmListView.FIELD_PREFIX[ZmItem.F_SIZE]			= "u";

ZmListView.ITEM_FLAG_CLICKED = DwtListView._LAST_REASON + 1;

// Filler to add into an empty area so that Firefox gets mouseMove events
ZmListView._fillerString = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";

ZmListView.prototype.getController =
function() {
	return this._controller;
}

ZmListView.prototype.set =
function(list, sortField) {
	var subList;
	if (list instanceof ZmList) {
		list.addChangeListener(this._listChangeListener);
		subList = list.getSubList(this.getOffset(), this.getLimit());
	} else {
		subList = list;
	}
	DwtListView.prototype.set.call(this, subList, sortField);
}

ZmListView.prototype.setUI = 
function(defaultColumnSort) {
	DwtListView.prototype.setUI.call(this, defaultColumnSort);
	this._resetColWidth();	// reset column width in case scrollbar is set
}

ZmListView.prototype.getLimit = 
function() {
	return this._appCtxt.get(ZmSetting.PAGE_SIZE);
};

ZmListView.prototype._changeListener =
function(ev) {
	if (ev.type != this.type)
		return;
	var items = ev.getDetail("items");
	if (ev.event == ZmEvent.E_TAGS || ev.event == ZmEvent.E_REMOVE_ALL) {
		DBG.println(AjxDebug.DBG2, "ZmListView: TAG");
		for (var i = 0; i < items.length; i++)
			this._setTagImg(items[i]);
	} else if (ev.event == ZmEvent.E_FLAGS) { // handle "flagged" and "has attachment" flags
		DBG.println(AjxDebug.DBG2, "ZmListView: FLAGS");
		var flags = ev.getDetail("flags");
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			for (var j = 0; j < flags.length; j++) {
				var flag = flags[j];
				var on = item[ZmItem.FLAG_PROP[flag]];
				if (flag == ZmItem.FLAG_FLAGGED) {
					var img = Dwt.getDomObj(this.getDocument(), this._getFieldId(item, ZmItem.F_FLAG));
					if (img && img.parentNode)
						AjxImg.setImage(img.parentNode, on ? "FlagRed" : "Blank_16");
				} else if (flag == ZmItem.FLAG_ATTACH) {
					var img = Dwt.getDomObj(this.getDocument(), this._getFieldId(item, ZmItem.F_ATTACHMENT));
					if (img && img.parentNode)
						AjxImg.setImage(img.parentNode, on ? "Attachment" : "Blank_16");
				}
			}
		}
	} else if (ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE) {
		DBG.println(AjxDebug.DBG2, "ZmListView: DELETE or MOVE");
		var needReplenish = false;
		for (var i = 0; i < items.length; i++) {
			var row = Dwt.getDomObj(this.getDocument(), this._getItemId(items[i]));
			if (row) {
				this._parentEl.removeChild(row);
				this._selectedItems.remove(row);
				needReplenish = true;
			}
			this._list.remove(items[i]);
		}
		if (needReplenish) {
			var respCallback = new AjxCallback(this, this._handleResponseChangeListener);
			this._controller._checkReplenish(respCallback);
		} else {
			this._setNextSelection();
		}
		this._controller._resetToolbarOperations();		
	} else if (ev.event == ZmEvent.E_MODIFY && (ev.getDetail("action") == "set")) {
		DBG.println(AjxDebug.DBG2, "ZmListView: SET");
	} else if (ev.event == ZmEvent.E_MODIFY) {
		DBG.println(AjxDebug.DBG2, "ZmListView: MODIFY");
	} else if (ev.event == ZmEvent.E_CREATE) {
		DBG.println(AjxDebug.DBG2, "ZmListView: CREATE");
	} else {
		DBG.println(AjxDebug.DBG1, "ZmListView: UNKNOWN event");
	}
}

ZmListView.prototype._handleResponseChangeListener =
function(args) {
	this._setNextSelection();
}

ZmListView.prototype._tagChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_TAG) return;

	var fields = ev.getDetail("fields");
	if (ev.event == ZmEvent.E_MODIFY && (fields && fields[ZmOrganizer.F_COLOR])) {
		var divs = this._getChildren();
		var tag = ev.source;
		for (var i = 0; i < divs.length; i++) {
			var item = this.getItemFromElement(divs[i]);
			if (item && item.tags && (item.tags.length == 1) && (item.tags[0] == tag.id))
				this._setTagImg(item);
		}
	}
}

// returns all child divs for this list view
ZmListView.prototype._getChildren =
function() {
	return this._parentEl.childNodes;
}

// Common routines for createItemHtml()

// The enclosing div and its styles
ZmListView.prototype._getDiv =
function(item, isDndIcon, isMatched) {
	var	div = this.getDocument().createElement("div");

	var base = "Row";
	div._styleClass = base;
	div._selectedStyleClass = [base, DwtCssStyle.SELECTED].join("-");	// Row-selected
	if (isDndIcon && isMatched) {
		var one = [base, DwtCssStyle.MATCHED, DwtCssStyle.DND].join("-");
		var two = [base, DwtCssStyle.DND].join("-");
		div._styleClass = [one, two].join(" ");							// Row-matched-dnd Row-dnd
	} else if (isMatched) {
		div._styleClass = [base, DwtCssStyle.MATCHED].join("-");		// Row-matched
	} else if (isDndIcon) {
		div._styleClass = [base, DwtCssStyle.DND].join("-");			// Row-dnd
		// bug fix #3654 - yuck
		if (AjxEnv.isMozilla)
			div.style.overflow = "visible";
	}
	if (isDndIcon)
		Dwt.setPosition(div, Dwt.ABSOLUTE_STYLE);

	this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);

	return div;
}

// The table that holds the items
ZmListView.prototype._getTable =
function(htmlArr, idx, isDndIcon) {
	htmlArr[idx++] = "<table cellpadding=0 cellspacing=0 border=0 width=";
	htmlArr[idx++] = !isDndIcon ? "100%>" : (this.getSize().x + ">");
	return idx;
}

// A table row for one item
ZmListView.prototype._getRow =
function(htmlArr, idx, item, className) {
	htmlArr[idx++] = "<tr id='" + this._getFieldId(item, ZmItem.F_ITEM_ROW) + "'";
	htmlArr[idx++] = className ? " class='" + className + "'>" : ">";
	return idx;
}

// A table cell for one field of the item
ZmListView.prototype._getField =
function(htmlArr, idx, item, field, colIdx, now) {
	var fieldId = this._getFieldId(item, field);
	var width = AjxEnv.isIE ? (this._headerList[colIdx]._width + 4): this._headerList[colIdx]._width;
	if (field == ZmItem.F_ITEM_TYPE) {
		htmlArr[idx++] = "<td width=" + width + " class='Icon'>";
		htmlArr[idx++] = AjxImg.getImageHtml(ZmItem.ICON[item.type], null, ["id='", fieldId, "'"].join(""));
		htmlArr[idx++] = "</td>";					
	} else if (field == ZmItem.F_FLAG) {
		var flagImageInfo = item.isFlagged ? "FlagRed" : "Blank_16";
		htmlArr[idx++] = "<td width=" + width + " class='Flag'>";
		htmlArr[idx++] = AjxImg.getImageHtml(flagImageInfo, null, ["id='", fieldId, "'"].join(""));
		htmlArr[idx++] = "</td>";
	} else if (field == ZmItem.F_TAG) {
		if (!this._appCtxt.get(ZmSetting.TAGGING_ENABLED))
			return idx;
		var cellId = this._getFieldId(item, ZmItem.F_TAG_CELL);
		htmlArr[idx++] = ["<td width=", width, " class='Tag' id='", cellId, "'>"].join("");
		htmlArr[idx++] = this._getTagImgHtml(item, fieldId);
		htmlArr[idx++] = "</td>";
	} else if (field == ZmItem.F_ATTACHMENT) {
		var attImageInfo = item.hasAttach ? "Attachment" : "Blank_16";
		htmlArr[idx++] = "<td width=" + width + " class='Attach'>";
		htmlArr[idx++] = AjxImg.getImageHtml(attImageInfo, null, ["id='", fieldId, "'"].join(""));
		htmlArr[idx++] = "</td>";					
	} else if (field == ZmItem.F_DATE) {
		htmlArr[idx++] = "<td id='" + fieldId + "'";
		htmlArr[idx++] = " width=" + width + ">";
		htmlArr[idx++] = AjxDateUtil.computeDateStr(now, item.date);
		if (AjxEnv.isNav)
			htmlArr[idx++] = ZmListView._fillerString;
		htmlArr[idx++] = "</td>";
	}
	
	return idx;
}

// We use HTML to set the tag image, so that we don't need the blank image if there are
// no tags. In that case, we need the filler if it's Firefox.
ZmListView.prototype._getTagImgHtml =
function(item, id) {
	var tagImageInfo = item.getTagImageInfo();
	var idStr = id ? ["id='", id, "'"].join("") : null;
	return AjxImg.getImageHtml(tagImageInfo, null, idStr);
}

// Find the tag cell and reset its HTML depending on the item's tags.
ZmListView.prototype._setTagImg =
function(item) {
	var tagCell = Dwt.getDomObj(this.getDocument(), this._getFieldId(item, ZmItem.F_TAG_CELL));
	if (!tagCell) return;
	tagCell.innerHTML = this._getTagImgHtml(item, this._getFieldId(item, ZmItem.F_TAG));
}

// Parse the DOM ID to figure out what got clicked. Most IDs will look something like "V1_a551".
// Item IDs will look like "V1_551". Participant IDs will look like "V1_a551_0".
//
//     V1  - conv list view (number is from view constant in ZmController)
//     _   - separator
//     a   - flag field (see constants above)
//     551 - item ID
//     _   - separator
//     0   - first participant
ZmListView.prototype._parseId =
function(id) {
	var m = id.match(/^V(\d+)_([a-z]?)((DWT)?-?\d+)_?(\d*)$/);
	if (m)
		return {view: m[1], field: m[2], item: m[3], participant: m[5]};
	else
		return null;
}

ZmListView.prototype._mouseOverAction =
function(ev, div) {
	DwtListView.prototype._mouseOverAction.call(this, ev, div);
	var id = ev.target.id || div.id;
	if (!id) return true;
	
	// check if we're hovering over a column header
	if (div._type && div._type == DwtListView.TYPE_HEADER_ITEM) {
		var id = this._headerList[div._itemIndex]._id;
		if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_FLAG]) == 0) {
			this.setToolTipContent(ZmMsg.flag);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_TAG]) == 0) {
			this.setToolTipContent(ZmMsg.tag);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_ATTACHMENT]) == 0) {
			this.setToolTipContent(ZmMsg.attachment);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_SUBJECT]) == 0) {
			if (this._headerList[div._itemIndex]._sortable)
				this.setToolTipContent(ZmMsg.sortBySubject);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_COUNT]) == 0) {
			this.setToolTipContent(ZmMsg.convCountTooltip);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_DATE]) == 0) {
			if (this._headerList[div._itemIndex]._sortable)
				this.setToolTipContent(ZmMsg.sortByReceived);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_STATUS]) == 0) {
			this.setToolTipContent(ZmMsg.messageStatus);
		} else if (id.indexOf(ZmListView.FIELD_PREFIX[ZmItem.F_FROM]) == 0) {
			this.setToolTipContent(ZmMsg.sortByFrom);
		} else {
			this.setToolTipContent(null);
		}
	} else {
		var m = this._parseId(id);
		if (m && m.field) {
			var item = this.getItemFromElement(div);
			if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_TAG]) {
				this._setTagToolTip(div);
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_STATUS]) {
				this._setStatusToolTip(item);
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_PARTICIPANT]) {
				if (item instanceof ZmContact) {	
					var toolTip = item.getToolTip(item.getAttr(ZmContact.F_email));
					this.setToolTipContent(toolTip);
				} else if (item.participants) {
				    this._setParticipantToolTip(item.participants.get(m.participant));
				}
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_FROM]) {
				this._setParticipantToolTip(item.getAddress(ZmEmailAddress.FROM));
			} else if ((m.field == ZmListView.FIELD_PREFIX[ZmItem.F_SUBJECT]) && item.fragment) {
				this.setToolTipContent(AjxStringUtil.htmlEncode(item.fragment));
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_DATE]) {
				this._setDateToolTip(item, div);
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_FOLDER]) {
				var folder = this._appCtxt.getTree(ZmOrganizer.FOLDER).getById(item.folderId);
				if (folder && folder.parent)
					this.setToolTipContent(folder.getPath());
			} else if (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_ITEM_TYPE]) {
				this.setToolTipContent(ZmMsg[ZmItem.MSG_KEY[item.type]]);
			} else {
				this.setToolTipContent(null);
			}
		} else {
			this.setToolTipContent(null);
		}
	}
	return true;
}

ZmListView.prototype._doubleClickAction =
function(ev, div) {
	var id = ev.target.id ? ev.target.id : div.id;
	if (!id) return true;

	var m = this._parseId(id);
	if (m && (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_FLAG])) {
		return false;
	}
	return true;
}

ZmListView.prototype._mouseUpAction =
function(ev, div) {
	var id = (ev.target.id && ev.target.id.indexOf("AjxImg") == -1) ? ev.target.id : div.id;
	if (!id) return true;

	var m = this._parseId(id);
	if (ev.button == DwtMouseEvent.LEFT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.SELECTION)) {
			this._selEv.field = m ? m.field : null;
			this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
		}
	} else if (ev.button == DwtMouseEvent.RIGHT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.ACTION)) {
			this._actionEv.field = m ? m.field : null;
			if (m && (m.field == ZmListView.FIELD_PREFIX[ZmItem.F_PARTICIPANT])) {
				var item = this.getItemFromElement(div);
				this._actionEv.detail = item.participants.get(m.participant);
			}
			this._evtMgr.notifyListeners(DwtEvent.ACTION, this._actionEv);
		}
	}
	return true;
}

ZmListView.prototype._setParticipantToolTip = 
function(address) {
	if (!address) return;
	
	try {
		var toolTip;
		var addr = address.getAddress();
		if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED) && addr) {
			var contactApp = ZmAppCtxt.getFromShell(this.shell).getApp(ZmZimbraMail.CONTACTS_APP);
			var contacts = contactApp.getContactList();
			var contact = contacts ? contacts.getContactByEmail(addr) : null;
			if (contact)
				toolTip = contact.getToolTip(addr);
		}
		
		if (!toolTip) {
			var addrstr = address.toString();
			if (addrstr)
			    toolTip = "<div style='white-space:nowrap'><b>" + ZmMsg.email + ": </b>" + AjxStringUtil.htmlEncode(addrstr) + "</div>";
	    }
	    
	    if (toolTip) {
			this.setToolTipContent(toolTip);
		}
	} catch (ex) {
		this._appCtxt.getAppController()._handleException(ex, contactApp.getContactList, null, false, contactApp);
	}
}

ZmListView.prototype._setTagToolTip = 
function(div) {
	var item = this.getItemFromElement(div);
	var numTags = item.tags.length;
	if (!numTags) return;
	var tagList = this._appCtxt.getTree(ZmOrganizer.TAG);
	var tags = item.tags;
	var html = new Array();
	var idx = 0;
	for (var i = 0; i < numTags; i++) {
		var tag = tagList.getById(tags[i]);
		html[idx++] = "<table><tr><td>";
		html[idx++] = AjxImg.getImageHtml(ZmTag.COLOR_MINI_ICON[tag.color]);
		html[idx++] = "</td><td valign='middle'>";
		html[idx++] = AjxStringUtil.htmlEncode(tag.name);
		html[idx++] = "</td></tr></table>";
	}
	this.setToolTipContent(html.join(""));
}

ZmListView.prototype._setStatusToolTip = 
function(item) {
	var tooltip = null;
	// check unread first since it has precedence
	if (item.isDraft)
		tooltip = ZmMsg.draft;
	else if (item.isUnread)
		tooltip = ZmMsg.unread;
	else if (item.isReplied)
		tooltip = ZmMsg.replied;
	else if (item.isForwarded)
		tooltip = ZmMsg.forwarded;
	else if (item.isSent)
		tooltip = ZmMsg.sent;
	else
		tooltip = ZmMsg.read;
	
	this.setToolTipContent(tooltip);
}

ZmListView.prototype._setDateToolTip = 
function(item, div) {
	if (!div._dateStr) {
		var date;
		var prefix = "";
		if (item instanceof ZmContact) {
			date = item.modified;
			prefix = "<b>" + ZmMsg.lastModified + ":</b><br>";
		} else {
			date = item.date;
		}
		if (date) {
			div._dateStr = prefix + (new Date(date)).toLocaleString() + " <span style='white-space:nowrap'>(" + AjxDateUtil.computeDateDelta(date) + ")</span>";
		} else {
			div._dateStr = "";
		}
	}

	if (div._dateStr && div._dateStr != "")
		this.setToolTipContent(div._dateStr);
}

ZmListView.prototype._sortColumn = 
function(columnItem, bSortAsc) { 
	// change the sort preference for this view in the settings
	var sortBy = null;
	switch (columnItem._sortable) {
		case ZmItem.F_PARTICIPANT: 
		case ZmItem.F_FROM:
			sortBy = bSortAsc ? ZmSearch.NAME_ASC : ZmSearch.NAME_DESC; 
			break;
			
		case ZmItem.F_SUBJECT:
		case ZmItem.F_FRAGMENT:
			sortBy = bSortAsc ? ZmSearch.SUBJ_ASC : ZmSearch.SUBJ_DESC; 
			break;
			
		case ZmItem.F_DATE:
			sortBy = bSortAsc ? ZmSearch.DATE_ASC : ZmSearch.DATE_DESC; 
			break;
	}

	if (sortBy) {
		this._sortByString = sortBy;
		this._appCtxt.set(ZmSetting.SORTING_PREF, sortBy, this.view);
	}
}

ZmListView.prototype._getViewPrefix = 
function() { 
	return "V" + this.view + "_";
}

ZmListView.prototype._getFieldId =
function(item, field) {
	return this._getViewPrefix() + ZmListView.FIELD_PREFIX[field] + item.id;
}

ZmListView.prototype._getDnDIcon =
function(dragOp) {
	var dndSelection = this.getDnDSelection();
	if (dndSelection == null)
		return null;

	var icon;
	var div;
	this._dndImg = null;
	if (!(dndSelection instanceof Array) || dndSelection.length == 1) {
		var item = null;
		if (dndSelection instanceof Array) {
			item = dndSelection[0];
		} else {
			item = dndSelection;
		}
		//var idx = this._getItemIndex(item);
		icon = this._createItemHtml(item, new Date(), true);
		icon._origClassName = icon.className;
	} else {
		var doc = this.getDocument();
		// Create multi one
		icon = doc.createElement("div");
		icon.className = "DndIcon";
		Dwt.setPosition(icon, Dwt.ABSOLUTE_STYLE); 
		
		//Dwt.setPosition(this._dndImg, Dwt.ABSOLUTE_STYLE);
		AjxImg.setImage(icon, "DndMultiYes_48");
		this._dndImg = icon;
								
		div = doc.createElement("div");
		Dwt.setPosition(div, Dwt.ABSOLUTE_STYLE);
		div.innerHTML = "<table><tr><td class='MailMultiSelectText'>" 
						+ dndSelection.length + "</td></tr></table>";
		icon.appendChild(div);

		// The size of the Icon is envelopeImg.width + sealImg.width - 20, ditto for height
		Dwt.setSize(icon, 43 + 32 - 16, 36 + 32 - 20);
	}
	
	this.shell.getHtmlElement().appendChild(icon);
	
	// If we have multiple items selected, then we have our cool little dnd icon, so
	// Position the text in the middle of the seal
	if (div) {
		var sz = Dwt.getSize(div);
		Dwt.setLocation(div, 16 + (32 - sz.x) / 2, 19 + (32 - sz.y) / 2);
	}
	
	Dwt.setZIndex(icon, Dwt.Z_DND);
	return icon;
}

ZmListView.prototype._setDnDIconState =
function(dropAllowed) {
	// If we are moving multiple items then set borders & icons, else delegate up
	// to DwtControl.prototype._setDnDIconState()
	if (this._dndImg)
		AjxImg.setImage(this._dndImg, dropAllowed ? "DndMultiYes_48" : "DndMultiNo_48");
	else {
		this._dndIcon.className = (dropAllowed) ? this._dndIcon._origClassName + " DropAllowed" 
												: this._dndIcon._origClassName + " DropNotAllowed";
	}
}

ZmListView.prototype._setNextSelection = 
function() {
	// set the next appropriate selected item
	if (this._firstSelIndex < 0)
		this._firstSelIndex = 0;
	var item = this._list.get(this._firstSelIndex);
	if (item == null) 
		item = this._list.getLast();
	if (item)
		this.setSelection(item, false);
}
