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

ZmChatMultiWindowView = function(parent, className, posStyle, controller) {
	if (arguments.length == 0) return;
	className = className ? className : "ZmChatMultiWindowView";
	posStyle = posStyle ? posStyle : Dwt.ABSOLUTE_STYLE;
	ZmChatBaseView.call(this, parent, className, posStyle, controller, ZmController.IM_CHAT_TAB_VIEW);
	var dropTgt = new DwtDropTarget(["ZmRosterTreeItem", "ZmRosterTreeGroup"]);
	this.setDropTarget(dropTgt);
	dropTgt.addDropListener(new AjxListener(this, this._dropListener));

	this.setScrollStyle(DwtControl.CLIP);
//	this.setScrollStyle(DwtControl.SCROLL);
	this._chatIdToChatWidget = {};
	this._initX = 20;
	this._initY = 20;

	// This is a singleton.  Why on Earth should I jump to 20
	// source files just to figure out how to get a reference to
	// it is beyond me.  I'm a simple guy, so I'll just store this
	// information here and move on.
	ZmChatMultiWindowView._INSTANCE = this;
};

ZmChatMultiWindowView.prototype = new ZmChatBaseView;
ZmChatMultiWindowView.prototype.constructor = ZmChatMultiWindowView;

ZmChatMultiWindowView._INSTANCE = null;

// PUBLIC function
ZmChatMultiWindowView.getInstance = function() {
	return ZmChatMultiWindowView._INSTANCE;
};

ZmChatMultiWindowView.prototype.getWindowManager = function() {
	if (!this._wm)
		this._wm = new ZmChatWindowManager(this);
	return this._wm;
};

ZmChatMultiWindowView.prototype.getShellWindowManager = function() {
	if (!this._shellWm)
		this._shellWm = new ZmChatWindowManager(DwtShell.getShell(window));
	return this._shellWm;
};

ZmChatMultiWindowView.prototype.__createChatWidget = function(chat, win) {
	var activeApp = this._appCtxt.getAppController().getActiveApp();
	if (!win)
		win = this.__useTab;
	this.__useTab = null;
	var wm, sticky;
	if (!win) {
		sticky = activeApp != "IM";
		wm = sticky ? this.getShellWindowManager() : this.getWindowManager();
		if (sticky)
			// reuse windows on global WM, so we don't clutter the display too much
			win = wm.getActiveWindow();
	}
	if (!win) {
		win = new ZmChatWindow(wm, chat, sticky);
		var pos = null;
		if (sticky) {
			// put it in the bottom-right corner
			var s = win.getSize();
			var ws = wm.getSize();
			pos = { x: ws.x - s.x - 16,
				y: ws.y - s.y - 40 };
		}
		wm.manageWindow(win, pos);
	} else {
		win.addTab(chat);
	}
	return win.getCurrentChatWidget();
};

ZmChatMultiWindowView.prototype._postSet = function() {
	// create chat windows for any pending chats
	var list = this.getChatList().getArray();
	for (var i=0; i < list.length; i++) {
    		var chat = list[i];
        	var cw = this.__createChatWidget(chat);
		this._addChatWidget(cw, chat);
	}
};

ZmChatMultiWindowView.prototype._createHtml =
function() {
   // this._content = new DwtComposite(this, "ZmChatMultiWindow", Dwt.RELATIVE_STYLE);
    //this.getHtmlElement().innerHTML = "<div id='"+this._contentId+"'></div>";
};

/**
* change listener for the chat list
*/
ZmChatMultiWindowView.prototype._changeListener = function(ev) {
	if (ev.event == ZmEvent.E_CREATE) {
		var chat = ev._details.items[0];
        	var cw = this.__createChatWidget(chat);
		this._addChatWidget(cw, chat);
		cw.select();
	} else if (ev.event == ZmEvent.E_DELETE) {
		var chat = ev._details.items[0];
		var cw = this._getChatWidgetForChat(chat);
		if (cw) {
			this._removeChatWidget(cw);
			cw.dispose();
		}
	}
};

ZmChatMultiWindowView.prototype.selectChat = function(chat, text) {
	var cw = this._getChatWidgetForChat(chat);
	if (cw)
		cw.select();
	if (text)
		cw.setInputContent(AjxStringUtil.trim(text));
};

ZmChatMultiWindowView.prototype._rosterItemChangeListener = function(chat, item, fields) {
	var cw = this._getChatWidgetForChat(chat);
	if (cw)
		cw._rosterItemChangeListener(item, fields);
};

ZmChatMultiWindowView.prototype._getChatWidgetForChat = function(chat) {
	return this._chatIdToChatWidget[chat.id];
};

ZmChatMultiWindowView.KEY_CHAT = "zcmwv_chat";

ZmChatMultiWindowView.prototype._initialWindowPlacement =
function(chatWindow) {
	if (this._nextInitX || this._nextInitY) {
		// chatWindow.setBounds(this._nextInitX, this._nextInitY, Dwt.DEAFULT, Dwt.DEFAULT);
		var pos = { x: this._nextInitX,
			    y: this._nextInitY };
		delete this._nextInitX;
		delete this._nextInitY;
		return pos;
	}

	// FIXME: for now it seems better to leave DwtResizableWindow
	// handle this--otherwise all windows get positioned at (20, 20)
	return null;

// 	var windows = {};
// 	for (var id in this._chatWindows) {
// 		var cw = this._chatWindows[id];
// 		if (cw === chatWindow)
// 			continue;
// 		var loc = cw.getLocation();
// 		windows[loc.x+","+loc.y] = true;
// 	}

// 	var size = this.getSize();

// 	var initX = 20, initY = 20;
// 	var incr = 20;
// 	var x = initX, y = initY;
// 	while(windows[x+","+y]) {
// 		x += incr;
// 		y += incr;
//         	if ((x > (size.x - 50)) || (y > (size.y - 50))) {
//         		initX += incr;
//         		x = initX;
//         		y = initY;
//     		}
// 	}
// 	// chatWindow.setBounds(x, y, Dwt.DEAFULT, Dwt.DEFAULT);
// 	return { x: x, y: y };
};

ZmChatMultiWindowView.prototype._addChatWidget =
function(chatWidget, chat) {
    	this._chatIdToChatWidget[chat.id] = chatWidget;
	var pos = this._initialWindowPlacement(chatWidget);
	chatWidget.popup(pos);
};

ZmChatMultiWindowView.prototype._removeChatWidget =
function(chatWidget) {
	delete this._chatIdToChatWidget[chatWidget.chat.id];
};

ZmChatMultiWindowView.prototype.endChat =
function(chat) {
	this._controller.endChat(chat);
};

ZmChatMultiWindowView.prototype._dropListener =
function(ev) {
	if (ev.action == DwtDropEvent.DRAG_ENTER) {
		var srcData = ev.srcData;
		if (!( (srcData instanceof ZmRosterTreeItem) ||
			(srcData instanceof ZmRosterTreeGroup) )) {
			ev.doIt = false;
			return;
		}
	} else if (ev.action == DwtDropEvent.DRAG_DROP) {
        	var srcData = ev.srcData;
		var mouseEv = DwtShell.mouseEvent;
            	mouseEv.setFromDhtmlEvent(ev.uiEvent);
		var pos = this.getLocation();
		var newPos = { x: mouseEv.docX - pos.x,
			       y: mouseEv.docY - pos.y };
		this._nextInitX = newPos.x
            	this._nextInitY = newPos.y;
		if (srcData instanceof ZmRosterTreeItem) {
			this._controller.chatWithRosterItem(srcData.getRosterItem());
		}
		if (srcData instanceof ZmRosterTreeGroup) {
			this._controller.chatWithRosterItems(srcData.getRosterItems(), srcData.getName()+" "+ZmMsg.imGroupChat);
		}
	}
};

ZmChatMultiWindowView.prototype.chatInNewTab = function(item, tabs) {
	this.__useTab = tabs;
	this._controller.chatWithRosterItem(item);
};

ZmChatMultiWindowView.prototype.chatWithRosterItem = function(item) {
	this._controller.chatWithRosterItem(item);
};
