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

ZmVoicemailListView = function(parent, controller, dropTgt) {
	if (arguments.length == 0) return;
	var headerList = this._getHeaderList();
	ZmVoiceListView.call(this, parent, "DwtListView ZmVoicemailListView", Dwt.ABSOLUTE_STYLE, ZmController.VOICEMAIL_VIEW, ZmItem.VOICEMAIL, controller, headerList, dropTgt);

	this._playing = null; // The voicemail currently loaded in the player.
	this._players = { }; // Map of voicemail.id to sound player
	this._soundChangeListeners = [];
	this._reconnect = null; // Structure to help reconnect a voicemail to the currently
							// playing sound when resorting or redraing the list.
}
ZmVoicemailListView.prototype = new ZmVoiceListView;
ZmVoicemailListView.prototype.constructor = ZmVoicemailListView;

ZmVoicemailListView.prototype.toString = function() {
	return "ZmVoicemailListView";
};

ZmVoicemailListView.FROM_WIDTH = 190;
ZmVoicemailListView.PLAYING_WIDTH = null; // Auto
ZmVoicemailListView.PRIORITY_WIDTH = ZmListView.COL_WIDTH_ICON;
ZmVoicemailListView.DATE_WIDTH = 120;

ZmVoicemailListView.F_PLAYING = "pl";
ZmVoicemailListView.F_PRIORITY = "py";

ZmVoicemailListView.prototype.setPlaying =
function(voicemail) {
	var player = this._players[voicemail.id];
	if (player) {
		player.play();
	}
};	

ZmVoicemailListView.prototype.getPlaying =
function() {
	return this._playing;
};

ZmVoicemailListView.prototype.addSoundChangeListener =
function(listener) {
	this._soundChangeListeners.push(listener);
};

ZmVoicemailListView.prototype.markUIAsRead =
function(items, on) {
	var className = on ? "" : "Unread";
	for (var i = 0; i < items.length; i++) {
		var item = items[i];
		var row = this._getElement(item, ZmItem.F_ITEM_ROW);
		if (row) {
			row.className = className;
		}
	}
};

ZmVoicemailListView.prototype.stopPlaying =
function(compact) {
	if (this._playing) {
		var player = this._players[this._playing.id];
		if (compact) {
			player.setCompact(true);
		}
		player.stop();
	}
};

ZmVoicemailListView.prototype._getHeaderList =
function() {

	var headerList = [];

	headerList.push(new DwtListHeaderItem(ZmVoicemailListView.F_PRIORITY, null, "FlagRed", ZmVoicemailListView.PRIORITY_WIDTH, null, false));
	headerList.push(new DwtListHeaderItem(ZmVoiceListView.F_CALLER, ZmMsg.from, null, ZmVoicemailListView.FROM_WIDTH, null, true));
	headerList.push(new DwtListHeaderItem(ZmVoicemailListView.F_PLAYING, ZmMsg.message, null, ZmVoicemailListView.PLAYING_WIDTH, ZmVoicemailListView.F_PLAYING, true));
	headerList.push(new DwtListHeaderItem(ZmVoiceListView.F_DATE, ZmMsg.received, null, ZmVoicemailListView.DATE_WIDTH, ZmVoiceListView.F_DATE, true));

	return headerList;
};

ZmVoicemailListView.prototype._getCellId =
function(item, field) {
	if (field == ZmVoicemailListView.F_PLAYING) {
		return this._getFieldId(item, field);
	} else {
		return ZmVoiceListView.prototype._getCellId.apply(this, arguments);
	}
};

ZmVoicemailListView.prototype._getCellContents =
function(htmlArr, idx, voicemail, field, colIdx, params) {
	if (field == ZmVoicemailListView.F_PRIORITY) {
		htmlArr[idx++] = this._getPriorityHtml(voicemail);
	} else if (field == ZmVoicemailListView.F_PLAYING) {
		// No-op. This is handled in _addRow()
	} else {
		idx = ZmVoiceListView.prototype._getCellContents.apply(this, arguments);
	}
	return idx;
};

ZmVoicemailListView.prototype.removeItem =
function(item, skipNotify) {
	ZmVoiceListView.prototype.removeItem.call(this, item, skipNotify);
	var player = this._players[item.id];
	if (player) {
		player.dispose();
	}
	if (this._playing == item) {
		this._playing = null;
	}
	delete this._players[item.id];
};

ZmVoicemailListView.prototype.set =
function(list, sortField) {
	ZmVoiceListView.prototype.set.call(this, list, sortField);

	// If we were unable to reconnect the player, dispose it.
	if (this._reconnect) {
		this._reconnect.player.dispose();
		this._reconnect = null;
	}
};

ZmVoicemailListView.prototype.removeAll =
function(skipNotify) {
	this._clearPlayers();
	ZmVoiceListView.prototype.removeAll.call(this, skipNotify);
};

ZmVoicemailListView.prototype._resetList =
function() {
	this._clearPlayers();
	ZmVoiceListView.prototype._resetList.call(this);
};

ZmVoicemailListView.prototype._clearPlayers =
function() {
	if (this._playing) {
		// Save data to be able to reconnect to the player.
		this._reconnect = {
			id: this._playing.id,
			player: this._players[this._playing.id]
		};
		
		// Hide the player
		var hidden;
		if (!this._hiddenDivId) {
			hidden = document.createElement("div");
			this._hiddenDivId = Dwt.getNextId();
			hidden.id = this._hiddenDivId;
			Dwt.setZIndex(hidden, Dwt.Z_HIDDEN);
			this.shell.getHtmlElement().appendChild(hidden);
		} else {
			hidden = document.getElementById(this._hiddenDivId);
		}
		this._reconnect.player.reparentHtmlElement(hidden);
		
		// Remove this offscreen player from our player list.
		delete this._players[this._playing.id];
		this._playing = null;
	}
	for (var i in this._players) {
		this._players[i].dispose();
	}
	this._players = {};
};

ZmVoicemailListView.prototype._addRow =
function(row, index) {
	ZmVoiceListView.prototype._addRow.call(this, row, index);
	var list = this.getList();
	
	if (!list || !list.size()) {
		return;
	}
	if (this._getCallType() != ZmVoiceFolder.VOICEMAIL) {
		return;
	}
	var voicemail = this.getItemFromElement(row);
	var cell = this._getElement(voicemail, ZmVoicemailListView.F_PLAYING);
	
	var player;
	if (this._reconnect && (this._reconnect.id == voicemail.id)) {
		player = this._reconnect.player;
		this._reconnect = null;
		this._playing = voicemail;
	} else {
		player = new ZmSoundPlayer(this, voicemail);
		if (!this._compactListenerObj) {
			this._compactListenerObj = new AjxListener(this, this._compactListener);
		}
		player.addCompactListener(this._compactListenerObj);
		for (var i = 0, count = this._soundChangeListeners.length; i < count; i++) {
			player.addChangeListener(this._soundChangeListeners[i]);
		}
		if (player.isPluginMissing()) {
			if (!this._helpListenerObj) {
				this._helpListenerObj = new AjxListener(this, this._helpListener);
			}
			player.addHelpListener(this._helpListenerObj);
		}
	}
	player.reparentHtmlElement(cell);
	this._players[voicemail.id] = player;
};

ZmVoicemailListView.prototype._helpListener =
function(ev) {
	var dialog = this._appCtxt.getMsgDialog();
	var message = AjxEnv.isIE ? ZmMsg.missingPluginHelpIE : ZmMsg.missingPluginHelp;
	dialog.setMessage(message, DwtMessageDialog.CRITICAL_STYLE);
	dialog.popup();
};

ZmVoicemailListView.prototype._compactListener =
function(ev) {
	if (!ev.isCompact) {
		this.stopPlaying(true);
		this._playing = ev.dwtObj.voicemail;
	} else if (this._playing && (ev.dwtObj == this._players[this._playing.id])){
		this._playing = null;
	}
};

ZmVoicemailListView.prototype._getPriorityHtml =
function(voicemail) {
	return voicemail.isHighPriority ? "<div class='ImgFlagRed'></div>" : "";
};

ZmVoicemailListView.prototype._sortColumn =
function(columnItem, bSortAsc) {
	var sortBy;
	switch (columnItem._sortable) {
		case ZmVoicemailListView.F_PLAYING: sortBy = bSortAsc ? ZmSearch.DURATION_ASC : ZmSearch.DURATION_DESC; break;
		case ZmVoiceListView.F_DATE: sortBy = bSortAsc ? ZmSearch.DATE_ASC : ZmSearch.DATE_DESC; break;
		default: break;
	}
	this._appCtxt.getApp(ZmApp.VOICE).search(this._controller._folder, null, sortBy)
};

ZmVoicemailListView.prototype._getHeaderTooltip =
function(prefix) {
	if (prefix == ZmVoicemailListView.F_PRIORITY) {
		return ZmMsg.priority;
	} else if (prefix == ZmVoiceListView.F_CALLER) {
		return ZmMsg.from;
	} else if (prefix == ZmVoicemailListView.F_PLAYING) {
		return ZmMsg.sortByDuration;
	} else if (prefix == ZmVoiceListView.F_DATE) {
		return ZmMsg.sortByReceived;
	}
	return null;
};

ZmVoicemailListView.prototype._getItemTooltip =
function(voicemail) {
	var data = { 
		caller: this._getCallerHtml(voicemail), 
		duration: AjxDateUtil.computeDuration(voicemail.duration),
		date: AjxDateUtil.computeDateTimeString(voicemail.date)
	};
	var html = AjxTemplate.expand("zimbraMail.voicemail.templates.Voicemail#VoicemailTooltip", data);
	return html;
};
