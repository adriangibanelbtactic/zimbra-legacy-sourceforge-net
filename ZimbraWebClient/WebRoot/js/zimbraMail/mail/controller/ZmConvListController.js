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
 * Creates a new, empty conversation list controller.
 * @constructor
 * @class
 * This class manages the conversations mail view. Conversations are listed, and any
 * conversation with more than one message is expandable. Expanding a conversation
 * shows its messages in the list just below it.
 *
 * @author Conrad Damon
 *
 * @param appCtxt	app context
 * @param container	containing shell
 * @param mailApp	containing app
 */
ZmConvListController = function(appCtxt, container, mailApp) {
	ZmDoublePaneController.call(this, appCtxt, container, mailApp);
	this._msgControllerMode = ZmController.CONVLIST_VIEW;
};

ZmConvListController.prototype = new ZmDoublePaneController;
ZmConvListController.prototype.constructor = ZmConvListController;

ZmMailListController.GROUP_BY_ITEM[ZmController.CONVLIST_VIEW]	= ZmItem.CONV;
ZmMailListController.GROUP_BY_SETTING[ZmController.CONVLIST_VIEW]	= ZmSetting.GROUP_BY_CONV;

// view menu
ZmMailListController.GROUP_BY_ICON[ZmController.CONVLIST_VIEW]			= "ConversationView";
ZmMailListController.GROUP_BY_MSG_KEY[ZmController.CONVLIST_VIEW]		= "byConversation";
ZmMailListController.GROUP_BY_VIEWS.push(ZmController.CONVLIST_VIEW);

// Public methods

ZmConvListController.prototype.toString = 
function() {
	return "ZmConvListController";
};

/**
* Displays the given conversation in a two-pane view. The view is actually
* created in _loadItem(), since it is a scheduled method and must execute
* last.
*
* @param search		[ZmSearchResult]	the current search results
*/
ZmConvListController.prototype.show =
function(search) {
	this._list = search.getResults(ZmItem.CONV);

	// call base class
	ZmDoublePaneController.prototype.show.call(this, search, this._list);
	this._appCtxt.set(ZmSetting.GROUP_MAIL_BY, ZmSetting.GROUP_BY_CONV);
//	this._resetNavToolBarButtons(ZmController.CONVLIST_VIEW);
};

ZmConvListController.prototype.getKeyMapName =
function() {
	return "ZmConvListController";
};

ZmConvListController.prototype.handleKeyAction =
function(actionCode) {
	DBG.println(AjxDebug.DBG3, "ZmConvListController.handleKeyAction");
	
	var mlv = this._mailListView;
	switch (actionCode) {
		case ZmKeyMap.EXPAND:
			if (mlv.getSelectionCount() != 1) { return false; }
			var item = mlv.getItemFromElement(mlv._kbAnchor);
			if (!item) { return false; }
			if (item.type == ZmItem.CONV && item.numMsgs == 1) {
				return DwtListView.prototype.handleKeyAction.call(mlv, DwtKeyMap.DBLCLICK);
			} else {
				mlv._expandItem(item);
			}
			break;

		// need to invoke DwtListView method directly since our list view no-ops DBLCLICK
		case DwtKeyMap.DBLCLICK:
			return DwtListView.prototype.handleKeyAction.apply(mlv, arguments);

		default:
			return ZmMailListController.prototype.handleKeyAction.call(this, actionCode);
	}
	return true;
};

// Private methods

ZmConvListController.prototype._createDoublePaneView = 
function() {
	return new ZmConvDoublePaneView(this._container, null, Dwt.ABSOLUTE_STYLE, this, this._dropTgt);
};

ZmConvListController.prototype._getToolBarOps =
function() {
	var list = this._standardToolBarOps();
	list.push(ZmOperation.SEP);
	list = list.concat(this._msgOps());
	list.push(ZmOperation.SEP,
				ZmOperation.SPAM,
				ZmOperation.SEP,
				ZmOperation.TAG_MENU,
				ZmOperation.SEP,
				ZmOperation.VIEW_MENU);
	return list;
};

ZmConvListController.prototype._getViewType =
function() {
	return ZmController.CONVLIST_VIEW;
};

ZmConvListController.prototype._getItemType =
function() {
	return ZmItem.CONV;
};

ZmConvListController.prototype._initializeTabGroup =
function(view) {
	if (this._tabGroups[view]) return;

	ZmListController.prototype._initializeTabGroup.apply(this, arguments);
	if (!AjxEnv.isIE) {
		this._tabGroups[view].addMember(this.getReferenceView().getMsgView());
	}
};

ZmConvListController.prototype._setViewContents =
function(view) {
	this._mailListView._resetExpansion();
	ZmDoublePaneController.prototype._setViewContents.apply(this, arguments);
};

ZmConvListController.prototype._paginate = 
function(view, bPageForward, convIdx) {
	view = view ? view : this._currentView;
	return ZmDoublePaneController.prototype._paginate.call(this, view, bPageForward, convIdx);
};

ZmConvListController.prototype._resetNavToolBarButtons = 
function(view) {
	ZmDoublePaneController.prototype._resetNavToolBarButtons.call(this, view);
	this._navToolBar[view].setToolTip(ZmOperation.PAGE_BACK, ZmMsg.previous + " " + ZmMsg.page);
	this._navToolBar[view].setToolTip(ZmOperation.PAGE_FORWARD, ZmMsg.next + " " + ZmMsg.page);
};

// no support for showing total items, which are msgs
ZmConvListController.prototype._getNumTotal = function() { return null; }

ZmConvListController.prototype._getMoreSearchParams = 
function(params) {
	// OPTIMIZATION: find out if we need to pre-fetch the first hit message
	params.fetch = this._readingPaneOn;
	params.markRead = true;
};

ZmConvListController.prototype._listSelectionListener =
function(ev) {
	var item = ev.item;
	if (!item) { return; }
	if (ev.field == ZmItem.F_EXPAND && (this._mailListView._expandable[item.id])) {
		this._toggle(item, false);
	} else {
		var handled = ZmDoublePaneController.prototype._listSelectionListener.apply(this, arguments);
		if (!handled) {
			if (ev.detail == DwtListView.ITEM_DBL_CLICKED) {
				var respCallback = new AjxCallback(this, this._handleResponseListSelectionListener, item);
				if (item.type == ZmItem.MSG) {
					AjxDispatcher.run("GetMsgController").show(item, this._msgControllerMode, respCallback);
				} else {
					AjxDispatcher.run("GetConvController").show(this._activeSearch, item, this, respCallback);
				}
			}
		}
	}
};

ZmConvListController.prototype._handleResponseListSelectionListener =
function(item) {
	// make sure correct msg is displayed in msg pane when user returns
	if (this._readingPaneOn) {
		this._setSelectedItem();
	}
};

/**
 * Checks to see if exactly one item (conv or msg) is selected, and if so,
 * that the appropriate msg is displayed in the reading pane. For a conv, that's
 * the first matching msg in the conv.
 */
ZmConvListController.prototype._setSelectedItem =
function() {
	var selCnt = this._listView[this._currentView].getSelectionCount();
	if (selCnt == 1) {
		var item = this._listView[this._currentView].getSelection()[0];
		if (item.type == ZmItem.CONV && !item._loaded) {
			// load conv (SearchConvRequest), which will also give us hit info
			var respCallback = new AjxCallback(this, this._handleResponseSetSelectedItem);
			item.load({query:this.getSearchString(), callback:respCallback, getFirstMsg:this._readingPaneOn});
		} else {
			this._handleResponseSetSelectedItem();
		}
	}
};

ZmConvListController.prototype._handleResponseSetSelectedItem =
function() {
	var msg = this._getSelectedMsg();
	if (!msg) { return; }
	this._displayMsg(msg);
};

ZmConvListController.prototype._getSelectedMsg =
function() {
	var item = this._listView[this._currentView].getSelection()[0];
	if (!item) { return null; }
	return (item.type == ZmItem.CONV) ? item.getHotMsg(0, this._mailListView.getLimit()) : item;
};

ZmConvListController.prototype._toggle =
function(item, getFirstMsg) {
	if (this._mailListView._expanded[item.id]) {
		this._collapse(item);
	} else {
		var conv = item, msg = null, offset = 0;
		if (item.type == ZmItem.MSG) {
			conv = this._appCtxt.getById(item.cid);
			msg = item;
			offset = this._mailListView._msgOffset[item.id];
		}
		this._expand(conv, msg, offset, getFirstMsg);
	}
};

ZmConvListController.prototype._expand =
function(conv, msg, offset, getFirstMsg) {
	offset = offset || 0;
	var respCallback = new AjxCallback(this, this._handleResponseLoadItem, [conv, msg, offset]);
	var pageWasCached = false;
	if (offset) {
		if (this._paginateConv(conv, offset, respCallback)) {
			// page was cached, callback won't be run
			this._handleResponseLoadItem(conv, msg, offset, new ZmCsfeResult(conv.msgs));
		}
	} else if (!conv._loaded) {
		// no msgs have been loaded yet
		var getFirstMsg = (getFirstMsg === false) ? false : this._readingPaneOn;
		conv.load({query:this.getSearchString(), callback:respCallback, getFirstMsg:getFirstMsg});
	} else {
		// re-expanding first page of msgs
		this._handleResponseLoadItem(conv, msg, offset, new ZmCsfeResult(conv.msgs));
	}
};

ZmConvListController.prototype._handleResponseLoadItem =
function(conv, msg, offset, result) {
	if (!result) { return; }
	this._mailListView._expand(conv, msg, offset);
};

/**
 * Adapted from ZmListController::_paginate
 */
ZmConvListController.prototype._paginateConv =
function(conv, offset, callback) {
	var list = conv.msgs;
	// see if we're out of msgs and the server has more
	var limit = this._appCtxt.get(ZmSetting.PAGE_SIZE);
	if (offset && ((offset + limit > list.size()) && list.hasMore())) {
		// figure out how many items we need to fetch
		var delta = (offset + limit) - list.size();
		var max = delta < limit && delta > 0 ? delta : limit;
		if (max < limit) {
			offset = ((offset + limit) - max) + 1;
		}
		var respCallback = new AjxCallback(this, this._handleResponsePaginateConv, [conv, offset, callback]);
		conv.load({query:this.getSearchString(), offset:offset, callback:respCallback});
		return false;
	} else {
		return true;
	}
};

ZmConvListController.prototype._handleResponsePaginateConv =
function(conv, offset, callback, result) {
	var searchResult = result.getResponse();
	conv.msgs.setHasMore(searchResult.getAttribute("more"));
	var newList = searchResult.getResults(ZmItem.MSG).getVector();
	conv.msgs.cache(offset, newList);
	if (callback) {
		callback.run(result);
	}
};

ZmConvListController.prototype._collapse =
function(item) {
	if (this._mailListView._rowsArePresent(item)) {	
		this._mailListView._collapse(item);
	} else {
		// reset state and expand instead
		this._toggle(item);
	}
};

// Actions
//
// Since a selection might contain both convs and msgs, we need to split them up and
// invoke the action for each type separately.

/**
 * Takes the given list of items (convs and msgs) and splits it into one list of each
 * type. Since an action applied to a conv is also applied to its msgs, we remove any
 * msgs whose owning conv is also in the list.
 */
ZmConvListController.prototype._divvyItems =
function(items) {
	var convs = [], msgs = [];
	var convIds = {};
	for (var i = 0; i < items.length; i++) {
		var item = items[i];
		if (item.type == ZmItem.CONV) {
			convs.push(item);
			convIds[item.id] = true;
		} else {
			msgs.push(item);
		}
	}
	var msgs1 = [];
	for (var i = 0; i < msgs.length; i++) {
		if (!convIds[msgs[i].cid]) {
			msgs1.push(msgs[i]);
		}
	}
	var lists = {};
	lists[ZmItem.MSG] = msgs1;	
	lists[ZmItem.CONV] = convs;
	
	return lists;
};

/**
 * Splits the given items into two lists, one of convs and one of msgs, and
 * applies the given method and args to each.
 *
 * @param items		[array]		list of convs and/or msgs
 * @param method	[string]	name of function to call in parent class
 * @param args		[array]		additional args to pass to function
 */
ZmConvListController.prototype._applyAction =
function(items, method, args) {
	args = args ? args : [];
	var lists = this._divvyItems(items);
	var hasMsgs = false;
	if (lists[ZmItem.MSG] && lists[ZmItem.MSG].length) {
		args.unshift(lists[ZmItem.MSG]);
		ZmDoublePaneController.prototype[method].apply(this, args);
		hasMsgs = true;
	}
	if (lists[ZmItem.CONV] && lists[ZmItem.CONV].length) {
		hasMsgs ? args[0] = lists[ZmItem.CONV] : args.unshift(lists[ZmItem.CONV])
		ZmDoublePaneController.prototype[method].apply(this, args);
	}
};

ZmConvListController.prototype._doFlag =
function(items) {
	var on = !items[0].isFlagged;
	this._applyAction(items, "_doFlag", [on]);
};

ZmConvListController.prototype._doTag =
function(items, tag, doTag) {
	this._applyAction(items, "_doTag", [tag, doTag]);
};

ZmConvListController.prototype._doRemoveAllTags =
function(items) {
	this._applyAction(items, "_doRemoveAllTags");
};

ZmConvListController.prototype._doDelete =
function(items, hardDelete, attrs) {
	this._applyAction(items, "_doDelete", [hardDelete, attrs]);
};

ZmConvListController.prototype._doMove =
function(items, folder, attrs, force) {
	this._applyAction(items, "_doMove", [folder, attrs, force]);
};

ZmConvListController.prototype._doMarkRead =
function(items, on) {
	this._applyAction(items, "_doMarkRead", [on]);
};

ZmConvListController.prototype._doSpam =
function(items, markAsSpam, folder) {
	this._applyAction(items, "_doSpam", [markAsSpam, folder]);
};

// Callbacks

ZmConvListController.prototype._processPrePopView = 
function(view) {
	this._resetNavToolBarButtons(view);
};

ZmConvListController.prototype._handleResponsePaginate = 
function(view, saveSelection, loadIndex, offset, result, ignoreResetSelection) {
	// bug fix #5134 - overload to ignore resetting the selection since it is handled by setView
	ZmListController.prototype._handleResponsePaginate.call(this, view, saveSelection, loadIndex, offset, result, true);
};
