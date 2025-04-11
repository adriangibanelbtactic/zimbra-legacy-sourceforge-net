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

ZmCallListView = function(parent, controller, dropTgt) {
	if (arguments.length == 0) return;
	var headerList = this._getHeaderList();
	ZmVoiceListView.call(this, parent, null, Dwt.ABSOLUTE_STYLE, ZmController.CALLLIST_VIEW, ZmItem.CALL, controller, headerList, dropTgt);
}
ZmCallListView.prototype = new ZmVoiceListView;
ZmCallListView.prototype.constructor = ZmCallListView;

ZmCallListView.prototype.toString = function() {
	return "ZmCallListView";
};

ZmCallListView.FROM_WIDTH = 190;
ZmCallListView.DURATION_WIDTH = 120;
ZmCallListView.DATE_WIDTH = null; // Auto

ZmCallListView.F_DURATION = "du";

ZmCallListView.prototype.createHeaderHtml = 
function(defaultColumnSort) {
	ZmVoiceListView.prototype.createHeaderHtml.call(this, defaultColumnSort);
	var isPlaced = this._getCallType() == ZmVoiceFolder.PLACED_CALL;
	this._setColumnHeader(ZmVoiceListView.F_CALLER, isPlaced ? ZmMsg.to : ZmMsg.from);
	this._setColumnHeader(ZmVoiceListView.F_DATE, isPlaced ? ZmMsg.placed : ZmMsg.received);
};

ZmCallListView.prototype._setColumnHeader = 
function(fieldId, label) {
	var index = this.getColIndexForId(fieldId);
	var fromColSpan = document.getElementById(DwtListView.HEADERITEM_LABEL + this._headerList[index]._id);
	if (fromColSpan) fromColSpan.innerHTML = "&nbsp;" + label;
	if (this._colHeaderActionMenu) this._colHeaderActionMenu.getItem(index).setText(label);
};

ZmCallListView.prototype._getHeaderList =
function() {

	var headerList = [];
	headerList.push(new DwtListHeaderItem(ZmVoiceListView.F_CALLER, ZmMsg.from, null, ZmCallListView.FROM_WIDTH, null, true));
	headerList.push(new DwtListHeaderItem(ZmCallListView.F_DURATION, ZmMsg.duration, null, ZmCallListView.DURATION_WIDTH, ZmCallListView.F_DURATION, true));
	headerList.push(new DwtListHeaderItem(ZmVoiceListView.F_DATE, ZmMsg.received, null, ZmCallListView.DATE_WIDTH, ZmVoiceListView.F_DATE, true));

	return headerList;
};

ZmCallListView.prototype.getPrintHtml =
function() {
	var buffer = [];
	var rowArgs = {};
	for(var i = 0, count = this._list.size(); i < count; i++) {
		var item = this._list.get(i);
		rowArgs.caller = this._getCallerHtml(item);
		rowArgs.duration = AjxDateUtil.computeDuration(item.duration);
		rowArgs.date = AjxDateUtil.simpleComputeDateStr(item.date);
		AjxTemplate.expand("zimbraMail.voicemail.templates.Voicemail#ZmCallListPrintViewRow", rowArgs, buffer);
	}
	
	var isPlaced = this._getCallType() == ZmVoiceFolder.PLACED_CALL;
	var args = {
		name: this._folder.getName(false, 0, true),
		callerHeader: isPlaced ? ZmMsg.to : ZmMsg.from,
		dateHeader: isPlaced ? ZmMsg.placed : ZmMsg.received,
		rows: buffer.join("")
	}
	return  AjxTemplate.expand("zimbraMail.voicemail.templates.Voicemail#ZmCallListPrintView", args);
};

ZmCallListView.prototype._getCellContents =
function(htmlArr, idx, voicemail, field, colIdx, params) {
	if (field == ZmCallListView.F_DURATION) {
		htmlArr[idx++] = AjxDateUtil.computeDuration(voicemail.duration);
	} else {
		idx = ZmVoiceListView.prototype._getCellContents.apply(this, arguments);
	}
	
	return idx;
};

ZmCallListView.prototype._sortColumn =
function(columnItem, bSortAsc) {
	var sortBy;
	switch (columnItem._sortable) {
		case ZmCallListView.F_DURATION: sortBy = bSortAsc ? ZmSearch.DURATION_ASC : ZmSearch.DURATION_DESC; break;
		case ZmVoiceListView.F_DATE: sortBy = bSortAsc ? ZmSearch.DATE_ASC : ZmSearch.DATE_DESC; break;
		default: break;
	}
	this._appCtxt.getApp(ZmApp.VOICE).search(this._controller._folder, null, sortBy)
};

ZmCallListView.prototype._getHeaderTooltip =
function(prefix) {
	if (prefix == ZmVoiceListView.F_CALLER) {
		var isPlaced = this._getCallType() == ZmVoiceFolder.PLACED_CALL;
		return isPlaced ? ZmMsg.to : ZmMsg.from;
	} else if (prefix == ZmCallListView.F_DURATION) {
		return ZmMsg.sortByDuration;
	} else if (prefix == ZmVoiceListView.F_DATE) {
		return ZmMsg.sortByReceived;
	}
	return null;
};

ZmCallListView.prototype._getItemTooltip =
function(call) {
	var location;
	var party = this.getCallingParty(call);
	if (party.city && party.state && party.country) {
		if (!this._locationFormatterCityStateCountry) {
			this._locationFormatterCityStateCountry = new AjxMessageFormat(ZmMsg.locationFormatCityStateCountry);
		}
		location = this._locationFormatterCityStateCountry.format([party.city, party.state, party.country]);
	} else 	if (party.city && party.country) {
		if (!this._locationFormatterCityCountry) {
			this._locationFormatterCityCountry = new AjxMessageFormat(ZmMsg.locationFormatCityCountry);
		}
		location = this._locationFormatterCityCountry.format([party.city, party.country]);
	} else {
		location = ZmMsg.unknown;
	}
	var callerLabel = (this._getCallType() == ZmVoiceFolder.PLACED_CALL) ? ZmMsg.toLabel : ZmMsg.fromLabel;
	var data = { 
		image: "Img" + this._controller._folder.getIcon(), 
		callerLabel: callerLabel, 
		caller: this._getCallerHtml(call), 
		duration: AjxDateUtil.computeDuration(call.duration),
		date: AjxDateUtil.computeDateTimeString(call.date),
		location: location
	};
	var html = AjxTemplate.expand("zimbraMail.voicemail.templates.Voicemail#CallTooltip", data);
	return html;
};

