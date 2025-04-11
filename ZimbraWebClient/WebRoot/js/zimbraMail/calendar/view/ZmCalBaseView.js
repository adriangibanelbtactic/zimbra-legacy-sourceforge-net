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

function ZmCalBaseView(parent, className, posStyle, controller, view) {
	if (arguments.length == 0) return;

	DwtComposite.call(this, parent, className, posStyle, view);

	// BEGIN LIST-RELATED
	this._setMouseEventHdlrs();
	this.setCursor("default");
	
	this._listenerMouseOver = new AjxListener(this, ZmCalBaseView.prototype._mouseOverListener);
	this._listenerMouseOut = new AjxListener(this, ZmCalBaseView.prototype._mouseOutListener);
	this._listenerMouseDown = new AjxListener(this, ZmCalBaseView.prototype._mouseDownListener);
	this._listenerMouseUp = new AjxListener(this, ZmCalBaseView.prototype._mouseUpListener);
	this._listenerMouseMove = new AjxListener(this, ZmCalBaseView.prototype._mouseMoveListener);
	this._listenerDoubleClick = new AjxListener(this, ZmCalBaseView.prototype._doubleClickListener);
	this.addListener(DwtEvent.ONMOUSEOVER, this._listenerMouseOver);
	this.addListener(DwtEvent.ONMOUSEOUT, this._listenerMouseOut);
	this.addListener(DwtEvent.ONMOUSEDOWN, this._listenerMouseDown);
	this.addListener(DwtEvent.ONMOUSEUP, this._listenerMouseUp);
	this.addListener(DwtEvent.ONMOUSEMOVE, this._listenerMouseMove);
	this.addListener(DwtEvent.ONDBLCLICK, this._listenerDoubleClick);
	
	this._controller = controller;
	this.view = view;	
	this._evtMgr = new AjxEventMgr();	 
	this._selectedItems = new AjxVector();
	this._selEv = new DwtSelectionEvent(true);
	this._actionEv = new DwtListViewActionEvent(true);	
	
	this._appCtxt = this.shell.getData(ZmAppCtxt.LABEL);

	// END LIST-RELATED
		
	this._timeRangeStart = 0;
	this._timeRangeEnd = 0;
	this.addControlListener(new AjxListener(this, this._controlListener));	
	this._createHtml();
	this._needsRefresh = true;
}

ZmCalBaseView.prototype = new DwtComposite;
ZmCalBaseView.prototype.constructor = ZmCalBaseView;

ZmCalBaseView.TIME_SELECTION = "ZmCalTimeSelection";
ZmCalBaseView.VIEW_ACTION = "ZmCalViewAction";

ZmCalBaseView.TYPE_APPTS_DAYGRID = 1; // grid holding days, for example
ZmCalBaseView.TYPE_APPT = 2; // an appt
ZmCalBaseView.TYPE_HOURS_COL = 3; // hours on lefthand side
ZmCalBaseView.TYPE_APPT_BOTTOM_SASH = 4; // a sash for appt duration
ZmCalBaseView.TYPE_APPT_TOP_SASH = 5; // a sash for appt duration
ZmCalBaseView.TYPE_DAY_HEADER = 6; // over date header for a day
ZmCalBaseView.TYPE_MONTH_DAY = 7; // over a day in month view
ZmCalBaseView.TYPE_ALL_DAY = 8; // all day div area in day view
ZmCalBaseView.TYPE_SCHED_FREEBUSY = 9; // free/busy union

ZmCalBaseView.COLORS = [];
// these need to match CSS rules
ZmCalBaseView.COLORS[ZmOrganizer.C_ORANGE]	= "Orange";
ZmCalBaseView.COLORS[ZmOrganizer.C_BLUE]	= "Blue";
ZmCalBaseView.COLORS[ZmOrganizer.C_CYAN]	= "Cyan";
ZmCalBaseView.COLORS[ZmOrganizer.C_GREEN]	= "Green";
ZmCalBaseView.COLORS[ZmOrganizer.C_PURPLE]	= "Purple";
ZmCalBaseView.COLORS[ZmOrganizer.C_RED]	= "Red";
ZmCalBaseView.COLORS[ZmOrganizer.C_YELLOW]	= "Yellow";
ZmCalBaseView.COLORS[ZmOrganizer.C_PINK]	= "Pink";
ZmCalBaseView.COLORS[ZmOrganizer.C_GRAY]	= "Gray";

ZmCalBaseView.prototype.getController =
function() {
	return this._controller;
}

ZmCalBaseView.prototype.firstDayOfWeek =
function() {
	return this._appCtxt.get(ZmSetting.CAL_FIRST_DAY_OF_WEEK) || 0;
}

ZmCalBaseView.prototype.addViewActionListener =
function(listener) {
	this._evtMgr.addListener(ZmCalBaseView.VIEW_ACTION, listener);
}

ZmCalBaseView.prototype.removeViewActionListener =
function(listener) {
	this._evtMgr.removeListener(ZmCalBaseView.VIEW_ACTION, listener);
}

// BEGIIN LIST-RELATED

ZmCalBaseView.prototype.addSelectionListener = 
function(listener) {
	this._evtMgr.addListener(DwtEvent.SELECTION, listener);
}

ZmCalBaseView.prototype.removeSelectionListener = 
function(listener) {
	this._evtMgr.removeListener(DwtEvent.SELECTION, listener);    	
}

ZmCalBaseView.prototype.addActionListener = 
function(listener) {
	this._evtMgr.addListener(DwtEvent.ACTION, listener);
}

ZmCalBaseView.prototype.removeActionListener = 
function(listener) {
	this._evtMgr.removeListener(DwtEvent.ACTION, listener);    	
}

ZmCalBaseView.prototype.getList = 
function() {
	return this._list;
}

ZmCalBaseView.prototype.associateItemWithElement =
function (item, element, type, optionalId) {
	element.id = optionalId ? optionalId : this._getItemId(item);
	element._itemIndex = AjxCore.assignId(item);
	element._type = type;
}

ZmCalBaseView.prototype._getViewPrefix = 
function() { 
	return "V" + this.view + "_";
}

ZmCalBaseView.prototype.deselectAll =
function() {
	var a = this._selectedItems.getArray();
	var sz = this._selectedItems.size();
	for (var i = 0; i < sz; i++) {
		a[i].className = a[i][DwtListView._STYLE_CLASS];
	}
	this._selectedItems.removeAll();
}

ZmCalBaseView.prototype._mouseOverListener = 
function(ev) {
	var div = ev.target;
	div = this._findAncestor(div, "_type");
	if (!div)
		return;
	
	this._mouseOverAction(ev, div);
}

ZmCalBaseView.prototype._mouseOverAction = 
function(ev, div) {
	if (div._type != ZmCalBaseView.TYPE_APPT) return true;

	var item = this.getItemFromElement(div);
	if (item instanceof ZmAppt) {
		this.setToolTipContent(item.getToolTip(this._controller));
	} else {
		this.setToolTipContent(null);
	}
	return true;
}

ZmCalBaseView.prototype._mouseOutListener = 
function(ev) {
	var div = ev.target;
	div = this._findAncestor(div, "_type");
	if (!div)
		return;
	// NOTE: The DwtListView handles the mouse events on the list items
	//		 that have associated tooltip text. Therefore, we must
	//		 explicitly null out the tooltip content whenever we handle
	//		 a mouse out event. This will prevent the tooltip from
	//		 being displayed when we re-enter the listview even though
	//		 we're not over a list item.
	if (div._type == ZmCalBaseView.TYPE_APPT) {
		this.setToolTipContent(null);
	}
	this._mouseOutAction(ev, div);
}

ZmCalBaseView.prototype._mouseOutAction = 
function(ev, div) {
	return true;
}


ZmCalBaseView.prototype._mouseMoveListener = 
function(ev) {
	if (this._clickDiv == null)
		return;
}

ZmCalBaseView.prototype._findAncestor =
function(elem, attr) {
	while (elem && (elem[attr] == void 0)){
		elem = elem.parentNode;
	}
	return elem;
}

ZmCalBaseView.prototype._mouseDownListener = 
function(ev) {
	var div = ev.target;

	div = this._findAncestor(div, "_type");
	if (!div) return false;

	this._clickDiv = div;
	if (div._type == ZmCalBaseView.TYPE_APPT) {
		if (ev.button == DwtMouseEvent.LEFT || ev.button == DwtMouseEvent.RIGHT)
			this._itemClicked(div, ev);
	}
	return this._mouseDownAction(ev, div);	
}

ZmCalBaseView.prototype._mouseDownAction = 
function(ev, div) {
/*
	if (ev.button == DwtMouseEvent.RIGHT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.ACTION)) {
			this._evtMgr.notifyListeners(DwtEvent.ACTION, this._actionEv);
		}
	}
	*/
	return true;
}

ZmCalBaseView.prototype._mouseUpListener = 
function(ev) {
	var div = ev.target;
	div = this._findAncestor(div, "_type");

	delete this._clickDiv;
	
	if (!div) return true;
	return this._mouseUpAction(ev, div);
}

ZmCalBaseView.prototype._mouseUpAction = 
function(ev, div) {
	if (div._type != ZmCalBaseView.TYPE_APPT) return true;
/*	if (ev.button == DwtMouseEvent.LEFT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.SELECTION)) {
			this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
		}
	}
	*/
	return true;
}

ZmCalBaseView.prototype._doubleClickAction = 
function(ev, div) {return true;}

ZmCalBaseView.prototype._doubleClickListener =
function(ev) {
	var div = ev.target;
	div = this._findAncestor(div, "_type");
	
	if (!div) return;	
		
	if (div._type == ZmCalBaseView.TYPE_APPT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.SELECTION)) {
			DwtUiEvent.copy(this._selEv, ev);
			this._selEv.item = this.getItemFromElement(div);
			this._selEv.detail = DwtListView.ITEM_DBL_CLICKED;
			this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
		}
	}
	return this._doubleClickAction(ev, div);
}

ZmCalBaseView.prototype._itemClicked =
function(clickedEl, ev) {
	var i;
	var a = this._selectedItems.getArray();
	var numSelectedItems = this._selectedItems.size();

	// is this element currently in the selected items list?
	var bContained = this._selectedItems.contains(clickedEl);

	DwtUiEvent.copy(this._selEv, ev);
	this._selEv.item = AjxCore.objectWithId(clickedEl._itemIndex);

	if (ev.shiftKey && bContained) {
		this._selectedItems.remove(clickedEl);
		clickedEl.className = clickedEl[DwtListView._STYLE_CLASS];
		this._selEv.detail = DwtListView.ITEM_DESELECTED;
		this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
	} else if (!bContained) {
		// clear out old left click selection(s)
		for (i = 0; i < numSelectedItems; i++) {
			a[i].className = a[i][DwtListView._STYLE_CLASS];
		}
		this._selectedItems.removeAll();
			
		// save new left click selection
		this._selectedItems.add(clickedEl);
		clickedEl.className = clickedEl[DwtListView._SELECTED_STYLE_CLASS];
		this._selEv.detail = DwtListView.ITEM_SELECTED;
		this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
	}

	if (ev.button == DwtMouseEvent.RIGHT) {
		DwtUiEvent.copy(this._actionEv, ev);
		this._actionEv.item = AjxCore.objectWithId(clickedEl._itemIndex);
		this._evtMgr.notifyListeners(DwtEvent.ACTION, this._actionEv);
	}
}

ZmCalBaseView.prototype.getItemFromElement =
function (element){
	if (element._itemIndex !== void 0){
		switch (element._type){
			case ZmCalBaseView.TYPE_APPT:
			  return AjxCore.objectWithId(element._itemIndex);
			default:
			  return null;
		}
	} else {
		return null;
	}
}

ZmCalBaseView.prototype.setSelection =
function(item, skipNotify) {
	var el = this._getElFromItem(item);
	if (el) {
		var i;
		var a = this._selectedItems.getArray();
		var sz = this._selectedItems.size();
		for (i = 0; i < sz; i++) {
			a[i].className = a[i][DwtListView._STYLE_CLASS];
		}
		this._selectedItems.removeAll();
		this._selectedItems.add(el);
		el.className = this.getEnabled() ? el[DwtListView._SELECTED_STYLE_CLASS] :
										   el[DwtListView._SELECTED_DIS_STYLE_CLASS];
		if (!skipNotify && this._evtMgr.isListenerRegistered(DwtEvent.SELECTION)) {
			var selEv = new DwtSelectionEvent(true);
			selEv.button = DwtMouseEvent.LEFT;
			selEv.target = el;
			selEv.item = AjxCore.objectWithId(el._itemIndex);
			selEv.detail = DwtListView.ITEM_SELECTED;
			this._evtMgr.notifyListeners(DwtEvent.SELECTION, selEv);
		}	
	}
}

ZmCalBaseView.prototype.getSelectionCount =
function() {
	return this._selectedItems.size();
}

ZmCalBaseView.prototype.getSelection =
function() {
	var a = new Array();
	var sa = this._selectedItems.getArray();
	var saLen = this._selectedItems.size();
	for (var i = 0; i < saLen; i++)
		a[i] = AjxCore.objectWithId(sa[i]._itemIndex);
	return a;
}

ZmCalBaseView.prototype.getSelectedItems =
function() {
	return this._selectedItems;
}

ZmCalBaseView.prototype.handleActionPopdown = 
function(ev) {
	// clear out old right click selection
}

// END LIST-RELATED

ZmCalBaseView.prototype.setNumDays = 
function (num) {
	this._numDays = num;
};

ZmCalBaseView.prototype.getNumDays = 
function (num) {
	return this._numDays;
};

ZmCalBaseView.prototype.getTitle = 
function() {
	return [ZmMsg.zimbraTitle, this.getCalTitle()].join(": ");
}

ZmCalBaseView.prototype.needsRefresh = 
function() {
	return this._needsRefresh;
}

ZmCalBaseView.prototype.setNeedsRefresh = 
function(refresh) {
	 this._needsRefresh = refresh;
};
ZmCalBaseView.prototype.getNeedsRefresh =
function() {
	return this._needsRefresh;
};

ZmCalBaseView.prototype._getItemId =
function(item) {
	return item ? (this._getViewPrefix()+item.getUniqueId()) : null;
}

ZmCalBaseView.prototype.addTimeSelectionListener = 
function(listener) {
	this.addListener(ZmCalBaseView.TIME_SELECTION, listener);
}

ZmCalBaseView.prototype.removeTimeSelectionListener = 
function(listener) { 
	this.removeListener(ZmCalBaseView.TIME_SELECTION, listener);
}

ZmCalBaseView.prototype.addDateRangeListener = 
function(listener) {
	this.addListener(DwtEvent.DATE_RANGE, listener);
}

ZmCalBaseView.prototype.removeDateRangeListener = 
function(listener) { 
	this.removeListener(DwtEvent.DATE_RANGE, listener);
}

// override. 
ZmCalBaseView.prototype.getRollField =
function(isDouble)
{
	return 0;
}

ZmCalBaseView.prototype.getDate =
function() {
	return this._date;
}

ZmCalBaseView.prototype.getTimeRange =
function() {
	return { start: this._timeRangeStart, end: this._timeRangeEnd };
}

ZmCalBaseView.prototype.isInView =
function(appt) {
	return appt.isInRange(this._timeRangeStart, this._timeRangeEnd);
}

ZmCalBaseView.prototype.isStartInView =
function(appt) {
	return appt.isStartInRange(this._timeRangeStart, this._timeRangeEnd);
}

ZmCalBaseView.prototype.isEndInView =
function(appt) {
	return appt.isEndInRange(this._timeRangeStart, this._timeRangeEnd);
}

ZmCalBaseView.prototype._dayKey =
function(date) {
	var key = date.getFullYear()+"/"+date.getMonth()+"/"+date.getDate();
	return key;
}

ZmCalBaseView.prototype.setDate =
function(date, duration, roll)
{
	this._duration = duration;
	this._date = new Date(date.getTime());
	var d = new Date(date.getTime());
	d.setHours(0, 0, 0, 0);
	var t = d.getTime();
	if (roll || t < this._timeRangeStart || t >= this._timeRangeEnd) {
		this._resetList();
		this._updateRange();		
		this._dateUpdate(true);
		this._updateTitle();
		
		// Notify any listeners
		if (this.isListenerRegistered(DwtEvent.DATE_RANGE)) {
			if (!this._dateRangeEvent)
				this._dateRangeEvent = new DwtDateRangeEvent(true);
			this._dateRangeEvent.item = this;
			this._dateRangeEvent.start = new Date(this._timeRangeStart);
			this._dateRangeEvent.end = new Date(this._timeRangeEnd);
			this.notifyListeners(DwtEvent.DATE_RANGE, this._dateRangeEvent);
		}
	} else {
		this._dateUpdate(false);
	}
}

/*
* override: responsible for updating any view-specific data when the date changes during a setDate call.
*/
ZmCalBaseView.prototype._dateUpdate =
function(rangeChanged) 
{

}

/*
* override: called when an appointment is clicked to see if the view should de-select a selected time range. For example,
* in day view if you have selected the 8:00 AM row and then click on an appt, the 8:00 AM row should be de-selected. If
* you are in month view though and have a day selected, thne day should still be selected if the appt you clicked on is in 
* the same day.
*/
ZmCalBaseView.prototype._apptSelected =
function() {

}

// override
ZmCalBaseView.prototype._updateRange =
function() { 
	this._updateDays();
	this._timeRangeStart = this._days[0].date.getTime();
	this._timeRangeEnd = this._days[this.getNumDays()-1].date.getTime() + AjxDateUtil.MSEC_PER_DAY;
}

// override 
ZmCalBaseView.prototype._updateTitle =
function() { }

ZmCalBaseView.prototype.addAppt = 
function(ao) {
	var item = this._createItemHtml(ao);
	var div = this._getDivForAppt(ao);
	if (div) div.appendChild(item);
}

ZmCalBaseView.prototype.set = 
function(list) {
	this._preSet();
//	ZmListView.prototype.set.call(this, list);
	this._selectedItems.removeAll();
	this._resetList();
	this._list = list;	
	var list = this.getList();
	if (list) {
		var size = list.size();
		if (size != 0) {
			for (var i=0; i < size; i++) {
				var ao = list.get(i);
				this.addAppt(ao);
			}
		}
	}
	this._postSet(list);
}

// override
ZmCalBaseView.prototype._fanoutAllDay =
function(appt) {
	return true;
}

// override
ZmCalBaseView.prototype._postSet =
function(appt) {}

// override
ZmCalBaseView.prototype._preSet =
function(appt) {}

// override
ZmCalBaseView.prototype._getDivForAppt =
function(appt) {}

ZmCalBaseView.prototype._addApptIcons =
function(appt, html, idx) {

	html[idx++] = "<table border=0 cellpadding=0 cellspacing=0 style='display:inline'><tr>";

	if (appt.hasOtherAttendees())
		html[idx++] = "<td>" + AjxImg.getImageHtml("ApptMeeting") + "</td>";

	if (appt.isException())
		html[idx++] = "<td>" + AjxImg.getImageHtml("ApptException") + "</td>";
	else if (appt.isRecurring())
		html[idx++] = "<td>" + AjxImg.getImageHtml("ApptRecur") + "</td>";

	if (appt.hasAlarm())
		html[idx++] = "<td>" + AjxImg.getImageHtml("ApptReminder") + "</td>";
	
	html[idx++] = "</tr></table>";

	return idx;
}

ZmCalBaseView.prototype._getElFromItem = 
function(item) {
	return document.getElementById(this._getItemId(item));
}

ZmCalBaseView.prototype._resetList =
function() {
	var list = this.getList();
	var size = list ? list.size() : 0;
	if (size == 0) return;

	for (var i=0; i < size; i++) {
		var ao = list.get(i);
		var appt = document.getElementById(this._getItemId(ao));
		if (appt) {
			appt.parentNode.removeChild(appt);
			AjxCore.unassignId(appt._itemIndex);
		}
	}
	list.removeAll();
	this.removeAll();	
}

ZmCalBaseView.prototype.removeAll =
function() {
	this._selectedItems.removeAll();
}

ZmCalBaseView.prototype.getCalTitle = 
function() {
	return this._title;
}

//
// Print methods
//

/**
 * Called to generate line summaries for each appointment in the
 * view's range. 
 * Do not use this method if you want full details about a single appointment.
 */
ZmCalBaseView.prototype.getPrintHtml = 
function() {
	var html = new Array();
	var idx = 0;

	// set up the mini calendar
	var fdow = this.firstDayOfWeek(); 
	var miniCal = new DwtCalendar(this, null, null, fdow, null, null, true, true);
	miniCal.setVisible(false);
	// set the working week as per user pref
	var workingWeek = []; 
	for (var i=0; i < 7; i++) { 
		var d = (i+fdow)%7;
		workingWeek[i] = (d > 0 && d < 6); 
	} 
	miniCal.setWorkingWeek(workingWeek); 

	// set the date to current month
	var startDate = this._getStartDate();
	miniCal.setDate(startDate, true);

	html[idx++] = "<div style='width:100%'>";
	html[idx++] = "<table width=100% cellpadding=3 cellspacing=3 bgcolor='#EEEEEE' style='border: 2px solid black; margin-bottom: 2px'><tr>";
	html[idx++] = "<td valign=top width=100% style='font-size:22px; font-weight:bold; white-space:nowrap'>";
	html[idx++] = this._getDateHdrForPrintView();
	html[idx++] = "</td>";
	html[idx++] = "<td><div style='width: 140px;'>";
	html[idx++] = miniCal.getHtmlElement().innerHTML;
	html[idx++] = "</div></td>";
	// spacer
	html[idx++] = "<td width=25>&nbsp;</td>";
	// set the date to the following month
	startDate.setMonth(startDate.getMonth() + 1);
	miniCal.setDate(startDate, true);
	html[idx++] = "<td><div style='width: 140px'>";
	html[idx++] = miniCal.getHtmlElement().innerHTML;
	html[idx++] = "</div></td>";
	html[idx++] = "</tr></table>";
	html[idx++] = "</div>";
	
	// cleanup
	this.getHtmlElement().removeChild(miniCal.getHtmlElement());
	
	return html.join("");
};

// override
ZmCalBaseView.prototype._getDateHdrForPrintView = 
function() {
	return "";
};

ZmCalBaseView.prototype._getStartDate =
function() {
	var timeRange = this.getTimeRange();
	return new Date(timeRange.start);
};

// override
ZmCalBaseView.prototype._createItemHtml =
function(appt) {}

// override
ZmCalBaseView.prototype._createHtml =
function() {}

ZmCalBaseView.prototype._controlListener =
function(ev) {
	if ((ev.oldWidth != ev.newWidth) ||
		(ev.oldHeight != ev.newHeight)) {
		this._layout();
	}
}

/**
* override
*/
ZmCalBaseView.prototype._layout =
function() {}

ZmCalBaseView.prototype._timeSelectionEvent =
function(date, duration, isDblClick, allDay, folderId, shiftKey) {
	if (!this._selectionEvent) this._selectionEvent = new DwtSelectionEvent(true);
	var sev = this._selectionEvent;
	sev._isDblClick = isDblClick;
	sev.item = this;
	sev.detail = date;
	sev.duration = duration;
	sev.isAllDay = allDay;
	sev.folderId = folderId;
	sev.force = false;
	sev.shiftKey = shiftKey;
	this.notifyListeners(ZmCalBaseView.TIME_SELECTION, this._selectionEvent);
	sev._isDblClick = false;
}
