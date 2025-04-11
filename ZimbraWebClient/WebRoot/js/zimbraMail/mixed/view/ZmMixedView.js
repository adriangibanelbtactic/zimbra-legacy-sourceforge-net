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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

ZmMixedView = function(parent, className, posStyle, controller, dropTgt) {

	var headerList = this._getHeaderList(parent);
	ZmListView.call(this, parent, className, posStyle, ZmController.MIXED_VIEW, ZmItem.MIXED, controller, headerList, dropTgt);
};

ZmMixedView.prototype = new ZmListView;
ZmMixedView.prototype.constructor = ZmMixedView;

// Consts
ZmMixedView.COLWIDTH_ICON 			= 19;
ZmMixedView.COLWIDTH_FROM 			= 145;
ZmMixedView.COLWIDTH_DATE 			= 60;

// support functions for _createItemHtml
ZmMixedView.LIST_VIEW_FUNCS = ["_addParams", "_getDiv", "_getDivClass", "_getTable",
							   "_getRow", "_getRowClass", "_getRowId", "_getCell", "_getCellId",
							   "_getCellClass", "_getCellAttrText", "_getCellContents",
							   "_getFieldId"];

ZmMixedView.prototype.toString = 
function() {
	return "ZmMixedView";
};

ZmMixedView.prototype.set =
function(list, sortField) {
	ZmListView.prototype.set.call(this, list, sortField);

	// The mixed list of items doesn't handle notifications.
	// We need to add listeners to each of the lists that 
	// owns items in the mixed array...
	var items = list.getArray();
	var owners = new Object();
	for (var i = 0; i < items.length; i++) {
		var list = items[i].list;
		if (list) {
			owners[list.type] = list;
		}
	}
	for (var type in owners) {
		owners[type].addChangeListener(this._listChangeListener);
	}
};

ZmMixedView.prototype._getHeaderList =
function(parent) {
	var shell = (parent instanceof DwtShell) ? parent : parent.shell;
	var appCtxt = shell.getData(ZmAppCtxt.LABEL); // this._appCtxt not set until parent constructor is called

	var hList = [];

	if (appCtxt.get(ZmSetting.SHOW_SELECTION_CHECKBOX)) {
		hList.push(new DwtListHeaderItem(ZmItem.F_SELECTION, null, "TaskCheckbox", ZmMixedView.COLWIDTH_ICON));
	}

	if (appCtxt.get(ZmSetting.FLAGGING_ENABLED)) {
		hList.push(new DwtListHeaderItem(ZmItem.F_FLAG, null, "FlagRed", ZmMixedView.COLWIDTH_ICON));
	}

	if (appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		hList.push(new DwtListHeaderItem(ZmItem.F_TAG, null, "MiniTag", ZmMixedView.COLWIDTH_ICON));
	}

	hList.push(new DwtListHeaderItem(ZmItem.F_TYPE, null, "Globe", ZmMixedView.COLWIDTH_ICON));
	hList.push(new DwtListHeaderItem(ZmItem.F_FROM, ZmMsg.from, null, ZmMixedView.COLWIDTH_FROM, null, true));
	hList.push(new DwtListHeaderItem(ZmItem.F_ATTACHMENT, null, "Attachment", ZmMixedView.COLWIDTH_ICON));
	hList.push(new DwtListHeaderItem(ZmItem.F_SUBJECT, ZmMsg.subject, null, null, null, true));
	hList.push(new DwtListHeaderItem(ZmItem.F_DATE, ZmMsg.date, null, ZmMixedView.COLWIDTH_DATE));

	return hList;
};

/**
 * Let the main view for the given item handle creating the HTML for it.
 * We also need to make sure any functions called by DwtListView._createItemHtml
 * come from the right class. Kinda hacky, but it works.
 */
ZmMixedView.prototype._createItemHtml =
function(item, params) {
	params = params || {};
	params.isMixedView = true;
	var listViewClass;
	var funcs = ZmMixedView.LIST_VIEW_FUNCS;
	if (item.type == ZmItem.CONTACT || item.type == ZmItem.GROUP) {
		AjxDispatcher.require(["ContactsCore", "Contacts"]);
		listViewClass = ZmContactSimpleView;
		this._emulateListView(listViewClass, funcs);
	} else if (item.type == ZmItem.CONV) {
		AjxDispatcher.require("Mail");
		funcs = funcs.concat(["_getFragmentSpan", "_getFragmentHtml",
							  "_getParticipantHtml", "_fitParticipants"]);
		listViewClass = ZmConvListView;
		this._emulateListView(listViewClass, funcs);
	} else if (item.type == ZmItem.MSG) {
		AjxDispatcher.require("Mail");
		funcs = funcs.concat(["_getFragmentSpan", "_getFragmentHtml"]);
		listViewClass = ZmMailMsgListView;
		this._emulateListView(listViewClass, funcs);
	} else if (item.type == ZmItem.TASK) {
		AjxDispatcher.require(["TasksCore", "Tasks"]);
		listViewClass = ZmTaskListView;
		this._emulateListView(listViewClass, funcs);
	} else if (item.type == ZmItem.PAGE || item.type == ZmItem.DOCUMENT) {
		AjxDispatcher.require(["NotebookCore", "Notebook"]);
		listViewClass = ZmFileListView;
		this._emulateListView(listViewClass, funcs);
	}
	return listViewClass.prototype._createItemHtml.call(this, item, params);
};

ZmMixedView.prototype._emulateListView =
function(listViewClass, funcs) {
	for (var i = 0; i < funcs.length; i++) {
		var funcName = funcs[i];
		ZmMixedView.prototype[funcName] = listViewClass.prototype[funcName];
	}
};

ZmMixedView.prototype._getHeaderToolTip =
function(field, itemIdx) {
	return (field == ZmItem.F_TYPE) ? ZmMsg.itemType :
									  ZmListView.prototype._getHeaderToolTip.apply(this, arguments);
};

ZmMixedView.prototype._getToolTip =
function(field, item, ev, div, match) {
	var tooltip = null;
	var listViewClass;
	if (field == ZmItem.F_FROM) {
		if (item.type == ZmItem.CONTACT) {
			listViewClass = ZmContactSimpleView;
		} else if (item.type == ZmItem.CONV) {
			listViewClass = ZmConvListView;
			this._emulateListView(listViewClass, ["_getParticipantToolTip"]);
		} else if (item.type == ZmItem.MSG) {
			listViewClass = ZmMailMsgListView;
			this._emulateListView(listViewClass, ["_getParticipantToolTip"]);
		} else {
			listViewClass = ZmListView;
		}
	} else if (field == ZmItem.F_SUBJECT) {
		if (item.type == ZmItem.CONV) {
			listViewClass = ZmConvListView;
		} else if (item.type == ZmItem.MSG) {
			listViewClass = ZmMailMsgListView;
		} else {
			listViewClass = ZmListView;
		}
	} else if (field == ZmItem.F_TYPE) {
		tooltip = ZmMsg[ZmItem.MSG_KEY[item.type]];
	} else {
		listViewClass = ZmListView;
	}

	tooltip = listViewClass ? listViewClass.prototype._getToolTip.apply(this, arguments) : tooltip;
	return tooltip;
};

ZmMixedView.prototype._changeListener =
function(ev) {
	if (this._appCtxt.getAppViewMgr().getCurrentViewId() != this.view)
		return;

	if (ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE) {
		var items = ev.getDetail("items");
		var contactList = AjxDispatcher.run("GetContacts");

		// walk the list of items and if any are contacts,
		for (var i = 0; i < items.length; i++) {
			if ((items[i].type == ZmItem.CONTACT || items[i].type == ZmItem.GROUP) &&
				ev.event == ZmEvent.E_DELETE)
			{
				// and is hard delete, remove from canonical list
				contactList.remove(items[i]);
			}
			// also remove from controller's list
			this._controller.getList().remove(items[i]);
		}
	}

	// call base class last
	ZmListView.prototype._changeListener.call(this, ev);
};
