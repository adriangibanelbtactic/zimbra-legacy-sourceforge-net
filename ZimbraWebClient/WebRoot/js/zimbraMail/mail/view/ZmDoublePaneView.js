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

ZmDoublePaneView = function(parent, className, posStyle, mode, controller, dropTgt) {

	if (arguments.length == 0) return;
	DwtComposite.call(this, parent, className, posStyle);

	this._controller = controller;
	this._appCtxt = this.shell.getData(ZmAppCtxt.LABEL);
	this._initHeader();
	this._mailListView = this._createMailListView(mode, controller, dropTgt);
	this._msgSash = new DwtSash(this, DwtSash.VERTICAL_STYLE, "AppSash-vert", ZmDoublePaneView.SASH_THRESHOLD, Dwt.ABSOLUTE_STYLE);
	this._msgView = new ZmMailMsgView(this, null, posStyle, mode, controller);

	if (!controller._readingPaneOn) {
		this._msgView.setVisible(false);
		this._msgSash.setVisible(false);
	}

	this._msgSash.registerCallback(this._sashCallback, this);
};

ZmDoublePaneView.prototype = new DwtComposite;
ZmDoublePaneView.prototype.constructor = ZmDoublePaneView;

ZmDoublePaneView.prototype.toString = 
function() {
	return "ZmDoublePaneView";
};

// consts

ZmDoublePaneView.SASH_THRESHOLD = 5;
ZmDoublePaneView._TAG_IMG = "TI";


// public methods

ZmDoublePaneView.prototype.getController =
function() {
	return this._controller;
};

ZmDoublePaneView.prototype.getTitle =
function() {
	return this._mailListView.getTitle();
};

ZmDoublePaneView.prototype.toggleView = 
function() {
	var bIsVisible = this._isMsgViewVisible();
	
	this._msgView.setVisible(!bIsVisible);
	this._msgSash.setVisible(!bIsVisible);
	
	var sz = this.getSize();
	this._resetSize(sz.x, sz.y);
};

ZmDoublePaneView.prototype._createMailListView =
function(mode, controller, dropTgt) {
	return new ZmMailMsgListView(this, null, Dwt.ABSOLUTE_STYLE, mode, controller, dropTgt);
};

ZmDoublePaneView.prototype.getMailListView =
function() {
	return this._mailListView;
};

ZmDoublePaneView.prototype.getMsgView = 
function() {
	return this._msgView;
};

ZmDoublePaneView.prototype.getSelectionCount = 
function() {
	return this._mailListView.getSelectionCount();
};

ZmDoublePaneView.prototype.getSelection = 
function() {
	return this._mailListView.getSelection();
};

ZmDoublePaneView.prototype.reset =
function() {
	this._msgView.reset();
};

ZmDoublePaneView.prototype.setMsg =
function(msg) {
	this._msgView.set(msg);
};

ZmDoublePaneView.prototype.addInviteReplyListener =
function (listener){
	this._msgView.addInviteReplyListener(listener);
};

ZmDoublePaneView.prototype.addShareListener =
function (listener){
	this._msgView.addShareListener(listener);
};

ZmDoublePaneView.prototype.resetMsg = 
function(newMsg) {
	this._msgView.resetMsg(newMsg);
};

ZmDoublePaneView.prototype.setBounds = 
function(x, y, width, height) {
	DwtComposite.prototype.setBounds.call(this, x, y, width, height);
	this._resetSize(width, height);
};

ZmDoublePaneView.prototype.setItem = 
function(item) {
	// overload me
};

// Private / Protected methods

ZmDoublePaneView.prototype._initHeader = 
function() {
	// overload me if you want a header
	return this;
};

ZmDoublePaneView.prototype._resetSize = 
function(newWidth, newHeight) {
	if (newHeight <= 0) { return; }
	
	if (this._isMsgViewVisible()) {
		var sashHeight = this._msgSash.getSize().y;
		if (!this._sashMoved) {
			var listViewHeight = (newHeight / 2) - DwtListView.HEADERITEM_HEIGHT;
			this._mailListView.resetHeight(listViewHeight);
			this._msgView.setBounds(Dwt.DEFAULT, listViewHeight + sashHeight, Dwt.DEFAULT,
									newHeight - (listViewHeight + sashHeight));
			this._msgSash.setLocation(Dwt.DEFAULT, listViewHeight);
		} else {
			var mvHeight = newHeight - this._msgView.getLocation().y;
			var minHeight = this._msgView.getMinHeight();
			if (mvHeight < minHeight) {
				this._mailListView.resetHeight(newHeight - minHeight);
				this._msgView.setBounds(Dwt.DEFAULT, (newHeight - minHeight) + sashHeight,
										Dwt.DEFAULT, minHeight - sashHeight);
			} else {
				this._msgView.setSize(Dwt.DEFAULT, mvHeight);
			}
			this._msgSash.setLocation(Dwt.DEFAULT, this._msgView.getLocation().y - sashHeight);
		}
	} else {
		this._mailListView.resetHeight(newHeight);
	}
	this._mailListView._resetColWidth();
}

ZmDoublePaneView.prototype._sashCallback =
function(delta) {

	if (!this._sashMoved) {
		this._sashMoved = true;
	}

	if (delta > 0) {
		var newMsgViewHeight = this._msgView.getSize().y - delta;
		var minMsgViewHeight = this._msgView.getMinHeight();
		if (newMsgViewHeight > minMsgViewHeight) {
			// moving sash down
			this._mailListView.resetHeight(this._mailListView.getSize().y + delta);
			this._msgView.setSize(Dwt.DEFAULT, newMsgViewHeight);
			this._msgView.setLocation(Dwt.DEFAULT, this._msgView.getLocation().y + delta);
		} else {
			delta = 0;
		}
	} else {
		var absDelta = Math.abs(delta);
		
		if (!this._minMLVHeight) {
			var list = this._mailListView.getList();
			if (list && list.size()) {
				var item = list.get(0);
				var div = document.getElementById(this._mailListView._getItemId(item));
				this._minMLVHeight = DwtListView.HEADERITEM_HEIGHT + (Dwt.getSize(div).y * 2);
			} else {
				this._minMLVHeight = DwtListView.HEADERITEM_HEIGHT;
			}
		}
		
		if (this._msgSash.getLocation().y - absDelta > this._minMLVHeight) {
			// moving sash up
			this._mailListView.resetHeight(this._mailListView.getSize().y - absDelta);
			this._msgView.setSize(Dwt.DEFAULT, this._msgView.getSize().y + absDelta);
			this._msgView.setLocation(Dwt.DEFAULT, this._msgView.getLocation().y - absDelta);
		} else {
			delta = 0;
		}
	}

	if (delta) {
		this._mailListView._resetColWidth();
	}

	return delta;
};

ZmDoublePaneView.prototype._isMsgViewVisible = 
function() {
	return this._msgView.getVisible();
};
