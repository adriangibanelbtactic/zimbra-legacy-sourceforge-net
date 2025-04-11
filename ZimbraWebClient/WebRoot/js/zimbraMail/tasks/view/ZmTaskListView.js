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

ZmTaskListView = function(parent, controller, dropTgt) {
	if (arguments.length == 0) return;

	var headerList = this._getHeaderList(parent);
	ZmListView.call(this, parent, null, Dwt.ABSOLUTE_STYLE, ZmController.TASKLIST_VIEW, ZmItem.TASK, controller, headerList, dropTgt);
};

ZmTaskListView.prototype = new ZmListView;
ZmTaskListView.prototype.constructor = ZmTaskListView;


// Consts
ZmTaskListView.COL_WIDTH_STATUS				= 145;
ZmTaskListView.KEY_ID						= "_keyId";
ZmTaskListView.TASKLIST_REPLENISH_THRESHOLD = 0;


// Public Methods
ZmTaskListView.prototype.toString =
function() {
	return "ZmTaskListView";
};

ZmTaskListView.prototype.setSize =
function(width, height) {
	ZmListView.prototype.setSize.call(this, width, height);
	this._resetColWidth();
};

ZmTaskListView.prototype.setBounds =
function(x, y, width, height) {
	ZmListView.prototype.setBounds.call(this, x, y, width, height);
	this._resetColWidth();
};

ZmTaskListView.prototype.getReplenishThreshold =
function() {
	return ZmTaskListView.TASKLIST_REPLENISH_THRESHOLD;
};

ZmTaskListView.prototype.saveNewTask =
function(keepFocus) {
	if (this._newTaskInputEl && Dwt.getVisibility(this._newTaskInputEl)) {
		var name = AjxStringUtil.trim(this._newTaskInputEl.value);
		if (name != "") {
			var respCallback = new AjxCallback(this, this._saveNewTaskResponse, [keepFocus]);
			this._controller.quickSave(name, respCallback);
		} else {
			this._saveNewTaskResponse(keepFocus);
		}
	}
};

ZmTaskListView.prototype._saveNewTaskResponse =
function(keepFocus) {
	if (keepFocus) {
		this._newTaskInputEl.value = "";
		this._newTaskInputEl.focus();
	} else {
		Dwt.setVisibility(this._newTaskInputEl, false);
	}
};

ZmTaskListView.prototype.discardNewTask =
function() {
	if (this._newTaskInputEl && Dwt.getVisibility(this._newTaskInputEl)) {
		this._newTaskInputEl.value = "";
		Dwt.setVisibility(this._newTaskInputEl, false);
		this.focus();
	}
};


// Private Methods

ZmTaskListView.prototype._renderList =
function(list, noResultsOk) {
	var div = document.createElement("DIV");
	div.id = "_newTaskBannerId";

	var htmlArr = [];
	var idx = 0;

	htmlArr[idx++] = "<table cellpadding=0 cellspacing=0 border=0 width=100% class='newTaskBannerSep'><tr>";
	for (var i = 0; i < this._headerList.length; i++) {
		if (!this._headerList[i]._visible) { continue; }

		var field = DwtListHeaderItem.getHeaderField(this._headerList[i]._id);
		var width = this._headerList[i]._width;

		if (field == ZmItem.F_SUBJECT) {
			htmlArr[idx++] = "<td><div class='newTaskBanner' onclick='ZmTaskListView._handleOnClick(this)'>";
			htmlArr[idx++] = ZmMsg.createNewTaskHint;
			htmlArr[idx++] = "</div></td>";
		} else {
			htmlArr[idx++] = "<td width=";
			htmlArr[idx++] = width;
			htmlArr[idx++] = ">&nbsp;</td>";
		}
	}
	htmlArr[idx++] = "</tr></table>";
	div.innerHTML = htmlArr.join("");
	this._addRow(div);

	ZmListView.prototype._renderList.call(this, list, noResultsOk);
};

ZmTaskListView.prototype._resetListView =
function() {
	// explicitly remove each child (setting innerHTML causes mem leak)
	var cDiv;
	while (this._parentEl.hasChildNodes()) {
		if (this._parentEl.lastChild.id == "_newTaskBannerId")
			break;
		cDiv = this._parentEl.removeChild(this._parentEl.lastChild);
		AjxCore.unassignId(Dwt.getAttr(cDiv, "_itemIndex"));
	}
	this._selectedItems.removeAll();
	this._rightSelItems = null;
};

ZmTaskListView.prototype._getCellId =
function(item, field) {
	return (field == ZmItem.F_COMPLETED || field == ZmItem.F_PRIORITY) ? this._getFieldId(item, field) : null;
};

ZmTaskListView.prototype._getCellContents =
function(htmlArr, idx, task, field, colIdx, params) {

	if (field == ZmItem.F_COMPLETED) {
		var cboxIcon = "TaskCheckbox";
		if (task.isComplete()) {
			cboxIcon = "TaskCheckboxCompleted";
		} else if (task.isPastDue()) {
			cboxIcon = "TaskCheckboxOverdue";
		}

		// complete checkbox
		htmlArr[idx++] = AjxImg.getImageHtml(cboxIcon, null, ["id='", params.fieldId, "'"].join(""));

	} else if (field == ZmItem.F_PRIORITY) {
		htmlArr[idx++] = "<center>";
		htmlArr[idx++] = ZmCalItem.getImageForPriority(task, params.fieldId);
		htmlArr[idx++] = "</center>";

	} else if (params.isMixedView && (field == ZmItem.F_FROM)) {
		htmlArr[idx++] = task.organizer || "&nbsp";
		
	} else if (field == ZmItem.F_SUBJECT) {
		htmlArr[idx++] = AjxEnv.isSafari ? "<div style='overflow:hidden'>" : "";
		if (params.isMixedView) {
			htmlArr[idx++] = task.name ? AjxStringUtil.htmlEncode(task.name, true) : AjxStringUtil.htmlEncode(ZmMsg.noSubject);
		} else {
			htmlArr[idx++] = AjxStringUtil.htmlEncode(task.getName(), true);
		}
		htmlArr[idx++] = AjxEnv.isSafari ? "</div>" : "";

	} else if (field == ZmItem.F_STATUS) {
		htmlArr[idx++] = ZmCalItem.getLabelForStatus(task.status);

	} else if (field == ZmItem.F_PCOMPLETE) {	// percent complete
		htmlArr[idx++] = task.pComplete || 0;
		htmlArr[idx++] = "%";

	} else if (field == ZmItem.F_DATE) {
		if (params.isMixedView) {
			idx = ZmListView.prototype._getCellContents.apply(this, arguments);
		} else {
			// date - dont call base class since we *always* want to show date (not time)
			htmlArr[idx++] = AjxDateUtil.simpleComputeDateStr(new Date(task.date));
		}
	} else {
		idx = ZmListView.prototype._getCellContents.apply(this, arguments);
	}
	
	return idx;
};

ZmTaskListView.prototype._getActionMenuForColHeader =
function() {
	if (!this._colHeaderActionMenu) {
		// create a action menu for the header list
		this._colHeaderActionMenu = new ZmPopupMenu(this);
		var actionListener = new AjxListener(this, this._colHeaderActionListener);
		for (var i = 0; i < this._headerList.length; i++) {
			var hCol = this._headerList[i];

			if (hCol._id.indexOf(ZmItem.F_SUBJECT) == 0)
				continue;

			var mi = this._colHeaderActionMenu.createMenuItem(hCol._id, {text:hCol._name, style:DwtMenuItem.CHECK_STYLE});
			mi.setData(ZmTaskListView.KEY_ID, hCol._id);
			mi.setChecked(true, true);
			this._colHeaderActionMenu.addSelectionListener(hCol._id, actionListener);
		}
	}
	return this._colHeaderActionMenu;
};

ZmTaskListView.prototype._mouseOverAction =
function(ev, div) {
	var id = ev.target.id || div.id;
	if (!id) return true;

	// check if we're hovering over a column header
	var type = Dwt.getAttr(div, "_type");
	if (type && type == DwtListView.TYPE_HEADER_ITEM) {
		var itemIdx = Dwt.getAttr(div, "_itemIndex");
		var field = DwtListHeaderItem.getHeaderField(this._headerList[itemIdx]._id);
		if (field == ZmItem.F_COMPLETED) {
			this.setToolTipContent(ZmMsg.status);
		} else if (field == ZmItem.F_PRIORITY) {
			this.setToolTipContent(ZmMsg.priority);
		} else if (field == ZmItem.F_SUBJECT) {
			this.setToolTipContent(ZmMsg.subject);
		} else if (field == ZmItem.F_STATUS) {
			this.setToolTipContent(ZmMsg.status);
		} else if (field == ZmItem.F_PCOMPLETE) {
			this.setToolTipContent(ZmMsg.percentComplete);
		} else if (field == ZmItem.F_DATE) {
			this.setToolTipContent(ZmMsg.dateDue);
		} else {
			return ZmListView.prototype._mouseOverAction.call(this, ev, div);
		}
	} else {
		var m = this._parseId(id);
		if (m && m.field) {
			if (m.field == ZmItem.F_PRIORITY) {
				var item = this.getItemFromElement(div);
				if (item && item.priority != ZmCalItem.PRIORITY_NORMAL)
					this.setToolTipContent(ZmCalItem.getLabelForPriority(item.priority));
				return true;
			} else if (m.field == ZmItem.F_COMPLETED) {
				var item = this.getItemFromElement(div);
				var tt = item && item.isComplete()
					? ZmMsg.clickToMarkNotStarted
					: item.isPastDue()
						? (ZmMsg.taskPastDue + " "  + ZmMsg.clickToMarkCompleted)
						: ZmMsg.clickToMarkCompleted;
				this.setToolTipContent(tt);
				return true;
			} else if (m.field == ZmItem.F_SUBJECT ||
					m.field == ZmItem.F_STATUS ||
					m.field == ZmItem.F_PCOMPLETE)
			{
				// do nothing for now
				// this.setToolTipContent();
				return true;
			}
		}
		return ZmListView.prototype._mouseOverAction.call(this, ev, div);
	}
	return true;
};

ZmTaskListView.prototype._handleNewTaskClick =
function(el) {
	if (!this._newTaskInputEl) {
		this._newTaskInputEl = document.createElement("INPUT");
		this._newTaskInputEl.type = "text";
		this._newTaskInputEl.className = "InlineWidget";
		this._newTaskInputEl.style.position = "absolute";

		Dwt.setHandler(this._newTaskInputEl, DwtEvent.ONBLUR, ZmTaskListView._handleOnBlur);
		Dwt.setHandler(this._newTaskInputEl, DwtEvent.ONKEYPRESS, ZmTaskListView._handleKeyPress);
		this.shell.getHtmlElement().appendChild(this._newTaskInputEl);

		var bounds = Dwt.getBounds(el);
		Dwt.setBounds(this._newTaskInputEl, bounds.x, bounds.y, bounds.width, bounds.height);
	} else {
		this._newTaskInputEl.value = "";
	}
	Dwt.setVisibility(this._newTaskInputEl, true);
	this._newTaskInputEl.focus();
};


ZmTaskListView.prototype._handleColHeaderResize =
function(ev) {
	ZmListView.prototype._handleColHeaderResize(this, ev);
	this._newTaskInputEl = null;
};

ZmTaskListView.prototype._getHeaderList =
function(parent) {
	var shell = (parent instanceof DwtShell) ? parent : parent.shell;
	var appCtxt = shell.getData(ZmAppCtxt.LABEL);

	var hList = [];

	hList.push(new DwtListHeaderItem(ZmItem.F_COMPLETED, null, "TaskCheckbox", ZmListView.COL_WIDTH_ICON, null, null, null, ZmMsg.completed));
	if (appCtxt.get(ZmSetting.TAGGING_ENABLED)) {
		hList.push(new DwtListHeaderItem(ZmItem.F_TAG, null, "MiniTag", ZmListView.COL_WIDTH_ICON, null, null, null, ZmMsg.tag));
	}
	hList.push(new DwtListHeaderItem(ZmItem.F_PRIORITY, null, "TaskHigh", ZmListView.COL_WIDTH_ICON, null, null, null, ZmMsg.priority));
	hList.push(new DwtListHeaderItem(ZmItem.F_ATTACHMENT, null, "Attachment", ZmListView.COL_WIDTH_ICON, null, null, null, ZmMsg.attachment));
	hList.push(new DwtListHeaderItem(ZmItem.F_SUBJECT, ZmMsg.subject, null, null/*, ZmItem.F_SUBJECT*/));
	hList.push(new DwtListHeaderItem(ZmItem.F_STATUS, ZmMsg.status, null, ZmTaskListView.COL_WIDTH_STATUS));
	hList.push(new DwtListHeaderItem(ZmItem.F_PCOMPLETE, ZmMsg.pComplete, null, ZmListView.COL_WIDTH_DATE));
	hList.push(new DwtListHeaderItem(ZmItem.F_DATE, ZmMsg.dateDue, null, ZmListView.COL_WIDTH_DATE));

	return hList;
};


// Listeners

ZmTaskListView.prototype._changeListener =
function(ev) {
	if ((ev.type != this.type) && (ZmList.MIXED != this.type))
		return;

	var fields = ev.getDetail("fields");
	var items = ev.getDetail("items");

	if (ev.event == ZmEvent.E_CREATE) {
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			if (this._list && this._list.contains(item)) // skip if we already have it
				continue;

			// add new item at the beg. of list view's internal list
			var idx = this._list && this._list.size() > 0 ? 1 : null;
			this.addItem(item, idx);
		}
	} else if (ev.event == ZmEvent.E_MODIFY) {
		var task = items[0];
		var div = this._getElFromItem(task);
		if (div) {
			this._createItemHtml(task, {now:this._now, div:div});
			this.associateItemWithElement(task, div, DwtListView.TYPE_LIST_ITEM);
		}
	}

	ZmListView.prototype._changeListener.call(this, ev);

	if (ev.event == ZmEvent.E_CREATE ||
		ev.event == ZmEvent.E_DELETE ||
		ev.event == ZmEvent.E_MOVE)
	{
		this._resetColWidth();
	}
};

ZmTaskListView.prototype._colHeaderActionListener =
function(ev) {
	var menuItemId = ev.item.getData(ZmTaskListView.KEY_ID);

	for (var i = 0; i < this._headerList.length; i++) {
		var col = this._headerList[i];
		if (col._id == menuItemId) {
			col._visible = !col._visible;
			break;
		}
	}

	this._relayout();
};


// Static Methods

ZmTaskListView._handleOnClick =
function(div) {
	var appCtxt = window.parentController
		? window.parentController._appCtxt
		: window._zimbraMail._appCtxt;

	var tlv = appCtxt.getApp(ZmApp.TASKS).getTaskListController().getCurrentView();
	tlv._handleNewTaskClick(div);
};

ZmTaskListView._handleOnBlur =
function(ev) {
	var appCtxt = window.parentController
		? window.parentController._appCtxt
		: window._zimbraMail._appCtxt;

	var tlv = appCtxt.getApp(ZmApp.TASKS).getTaskListController().getCurrentView();
	tlv.saveNewTask();
};

ZmTaskListView._handleKeyPress =
function(ev) {
	var key = DwtKeyEvent.getCharCode(ev);

	var appCtxt = window.parentController
		? window.parentController._appCtxt
		: window._zimbraMail._appCtxt;

	var tlv = appCtxt.getApp(ZmApp.TASKS).getTaskListController().getCurrentView();

	if (key == DwtKeyEvent.KEY_ENTER) {
		tlv.saveNewTask(true);
	} else if (key == DwtKeyEvent.KEY_ESCAPE) {
		tlv.discardNewTask();
	}
};
