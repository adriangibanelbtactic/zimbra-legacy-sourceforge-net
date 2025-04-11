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

ZmCalColView = function(parent, posStyle, controller, dropTgt, view, numDays, scheduleMode) {
	if (arguments.length == 0) return;

	if (numDays == null) numDays = 1;
	if (view == null) view = ZmController.CAL_DAY_VIEW;
	var className = "calendar_view";
	// set before call to parent	
	this._scheduleMode = scheduleMode;
	this.setNumDays(numDays);
	this._daySepWidth = scheduleMode ? 2 : 1; // width of separator between days
	this._columns = [];
	this._unionBusyDivIds = new Array(); //  div ids for layingout union
	//this._numDays = numDays;
	ZmCalBaseView.call(this, parent, className, posStyle, controller, view);
	this.setScrollStyle(DwtControl.CLIP);	
	this._needFirstLayout = true;
	if (AjxEnv.isNav) {
		var padding = new AjxBuffer();
		for (var i=0; i < 64; i++) padding.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
		this._padding = padding.toString();
	} else {
		this._padding = "";
	}
}

ZmCalColView.prototype = new ZmCalBaseView;
ZmCalColView.prototype.constructor = ZmCalColView;

ZmCalColView.DRAG_THRESHOLD = 4;

// min width before we'll turn on horizontal scrollbars
ZmCalColView.MIN_COLUMN_WIDTH = 120; 
// max number of all day appts before we turn on vertical scrollbars
ZmCalColView.MAX_ALLDAY_APPTS = 4;

ZmCalColView._OPACITY_APPT_NORMAL = 100;
ZmCalColView._OPACITY_APPT_DECLINED = 20;
ZmCalColView._OPACITY_APPT_TENTATIVE = 60;
ZmCalColView._OPACITY_APPT_DND = 70;

ZmCalColView._HOURS_DIV_WIDTH = 40; // width of div holding hours text (1:00am, etc)
ZmCalColView._UNION_DIV_WIDTH = 30; // width of div holding union in sched view

ZmCalColView._ALL_DAY_SEP_HEIGHT = 5; // height of separator between all day appts and body

ZmCalColView._SCROLLBAR_WIDTH = 22;

ZmCalColView._SCROLL_PRESSURE_FUDGE = 10; // pixels for scroll pressure around top/bottom

ZmCalColView._DAY_HEADING_HEIGHT = 20;
ZmCalColView._ALL_DAY_APPT_HEIGHT = 20;
ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD = 3; // space between all day appt rows
ZmCalColView._APPT_X_FUDGE = 0; // due to border stuff
ZmCalColView._APPT_Y_FUDGE = -1; // ditto
ZmCalColView._APPT_WIDTH_FUDGE = (AjxEnv.isIE ? 0 : -3); // due to border stuff
ZmCalColView._APPT_HEIGHT_FUDGE = (AjxEnv.isIE ? 0 : -4); // ditto

ZmCalColView._HOUR_HEIGHT = 42;
ZmCalColView._HALF_HOUR_HEIGHT = ZmCalColView._HOUR_HEIGHT/2;
ZmCalColView._15_MINUTE_HEIGHT = ZmCalColView._HOUR_HEIGHT/4;
ZmCalColView._DAY_HEIGHT = ZmCalColView._HOUR_HEIGHT*24;

ZmCalColView.prototype.toString = 
function() {
	return "ZmCalColView";
}

ZmCalColView.prototype.getRollField =
function(isDouble) {
	switch(this.view) {
		case ZmController.CAL_WORK_WEEK_VIEW:	
		case ZmController.CAL_WEEK_VIEW:
			return isDouble ? AjxDateUtil.MONTH : AjxDateUtil.WEEK;
			break;
		case ZmController.CAL_DAY_VIEW:
		case ZmController.CAL_SCHEDULE_VIEW:
		default:
			return isDouble ? AjxDateUtil.WEEK : AjxDateUtil.DAY;
			break;		
	}
}

ZmCalColView.prototype.getPrintHtml = 
function() {
	var html = new Array();
	var idx = 0;
	
	var timeRange = this.getTimeRange();
	var startDate = new Date(timeRange.start);
	var endDate = new Date(timeRange.end);

	// print the common header for calendar by calling base class
	html[idx++] = ZmCalBaseView.prototype.getPrintHtml.call(this);
	html[idx++] = "<div style='width:100%'>";
	html[idx++] = "<table width=100% border=0 cellpadding=1 cellspacing=1 style='border:2px solid black'>";

	var list = this.getList();
	var numAppts = list ? list.size() : 0;
	var nextDay = new Date(startDate);
	var numDays = this.getNumDays();

	// single day print out requires all details for each appointment
	if (numDays == 1) {
		this._loadDetailsForAppts(list, numAppts);
	}
	
	var columnFormatter = DwtCalendar.getDateFormatter();
	var timeFormatter = AjxDateFormat.getTimeInstance(AjxDateFormat.SHORT);
	for (var i = 0; i < numDays; i++) {
		html[idx++] = "<tr><td width=100%>";
		if (numDays > 1) {
			// XXX: set the styles inline so we force the printer to acknowledge them!
			var style = "background-color:#EEEEEE; text-align:center; font-family:Arial; font-size:14px; font-weight:bold; border:1px solid #EEEEEE;";
			html[idx++] = "<div style='" + style + "'>";
			html[idx++] = columnFormatter.format(nextDay);
			html[idx++] = "</div>";
		}
		
		// print out all the appointments for this day
		var inTable = false;
		for (var j = 0; j < numAppts; j++) {
			var appt = list.get(j);
			if (appt.startDate.getDate() == nextDay.getDate() || numDays == 1) {
				var loc = appt.getLocation();
				var status = appt.getParticipantStatusStr();
				if (appt.isAllDayEvent()) {
					// XXX: this is bad HTML but the browsers do the right thing and help us out
					html[idx++] = "<table border=0 cellpadding=2 cellspacing=2 width=100% style='border:1px solid black'>";
					html[idx++] = "<tr><td style='font-family:Arial; font-size:13px; width:100%;'>";
					html[idx++] = "<b>" + appt.getName() + "</b>";
					if (loc)
						html[idx++] = " (" + loc + ")";
					html[idx++] = " [" + status + "]";
					// print more detail if we're printing a single day
					if (numDays == 1) {
						html[idx++] = this._printApptDetails(appt);
					}
					html[idx++] = "</td></tr></table>";
				} else {
					if (!inTable) {
						inTable = true;
						html[idx++] = "<table border=0>";
					}
					var startTime = timeFormatter.format(appt.startDate);
					var endTime = timeFormatter.format(appt.endDate);
					style = "font-family:Arial; font-size:13px; vertical-align:top;"
					html[idx++] = "<tr>";
					html[idx++] = "<td align=right style='" + style + "'><b>" + startTime + "</b></td>";
					html[idx++] = "<td valign=top> - </td>";
					html[idx++] = "<td align=right style='" + style + "'><b>" + endTime + "</b></td>";
					html[idx++] = "<td style='" + style + "'>";
					html[idx++] = appt.getName();
					if (loc) {
						html[idx++] = " (" + loc + ")";
					}
					html[idx++] = " [" + status + "]";
					html[idx++] = "</td></tr>";
					
					if (numDays == 1) {
						html[idx++] = "<tr><td></td><td></td><td></td><td>";
						html[idx++] = this._printApptDetails(appt);
						html[idx++] = "</td></tr>";
					}
					
					// spacer
					html[idx++] = "<tr><td><br></td></tr>";
				}
			}
		}
		if (inTable)
			html[idx++] = "</table>";

		html[idx++] = "<br><br></td></tr>";

		nextDay.setDate(nextDay.getDate() + 1);
	}
	html[idx++] = "</table>";
	html[idx++] = "</div>";

	return html.join("");
};

// Helper function that collects all appointments that dont have details loaded
// and makes batch request to go fetch them. Used for printing.
ZmCalColView.prototype._loadDetailsForAppts = 
function(list, numAppts) {
	var makeBatchReq = false;
	var needToLoad = new Object();
	var apptHash = new Object();

	// collect all appointments that dont have details loaded yet
	for (var i = 0; i < numAppts; i++) {
		var appt = list.get(i);
		if (appt.message == null) {
			appt.message = new ZmMailMsg(this._appCtxt, appt.invId);
			needToLoad[appt.invId] = appt.message;
			apptHash[appt.invId] = appt;
			makeBatchReq = true;
		}
	}

	if (makeBatchReq) {
		// set up batch request call
		var soapDoc = AjxSoapDoc.create("BatchRequest", "urn:zimbra");
		soapDoc.setMethodAttribute("onerror", "continue");

		for (var i in needToLoad) {
			var msgRequest = soapDoc.set("GetMsgRequest", null, null, "urn:zimbraMail");

			var doc = soapDoc.getDoc();
			var msgNode = doc.createElement("m");
			msgNode.setAttribute("requestId", i);
	
			msgRequest.appendChild(msgNode);
		}

		var command = new ZmCsfeCommand();
		var resp = command.invoke({soapDoc: soapDoc}).Body.BatchResponse.GetMsgResponse;

		for (var i = 0; i < resp.length; i++) {
			var msgNode = resp[i].m[0];
			var msg = needToLoad[msgNode.requestId];
			if (msg) {
				msg._loadFromDom(msgNode);
				// parse ZmMailMsg into ZmAppt
				var appt = apptHash[msgNode.requestId];
				if (appt)
					appt.setFromMessage(msg);
			}
		}
	}
};

ZmCalColView.prototype._printApptDetails = 
function(appt) {
	var html = new Array();
	var idx = 0;
	var style= "font-family:Arial; font-size:12px; vertical-align:top;";
	
	html[idx++] = "<table border=0 width=100%>";
	
	var organizer = appt.getOrganizer();
	var attendees = appt.getAttendeesText();

	if (organizer && attendees) {
		html[idx++] = "<tr><td width=1% style='" + style + "'><u>" + ZmMsg.organizer + ":</u></td>";
		html[idx++] = "<td style='" + style + "'>" + appt.getOrganizer() + "</td></tr>";
		html[idx++] = "<tr><td width=1% style='" + style + "'><u>" + ZmMsg.attendees + ":</u></td>";
		html[idx++] = "<td style='" + style + "'>" + attendees + "</td></tr>";
	}
	
	var attachments = appt.getAttachments();
	if (attachments) {
		html[idx++] = "<tr>";
		html[idx++] = "<td width=1% style='" + style + "'><u>" + ZmMsg.attachments + ":</u></td>";
		html[idx++] = "<td style='" + style + "'>";
		for (var i = 0; i < attachments.length; i++) {
			 html[idx++] = attachments[i].filename;
			 if (i != attachments.length-1)
			 	html[idx++] = ", ";
		}
		html[idx++] = "</td></tr>";
	}
	
	var notes = appt.getNotesPart();
	if (notes) {
		style= "font-family:Arial; font-size:11px; vertical-align:top; margin-top:3px";
		html[idx++] = "<tr><td colspan=2 style='" + style + "'>" + AjxStringUtil.nl2br(notes) + "</td></tr>";
	}

	html[idx++] = "</table>";
	
	return html.join("");
};

ZmCalColView.prototype._getDateHdrForPrintView = 
function() {
	var header = "";
	var timeRange = this.getTimeRange();
	var startDate = new Date(timeRange.start);

	if (this.getNumDays() > 1) {
		var formatter = AjxDateFormat.getDateInstance(AjxDateFormat.LONG);
		var endDate = new Date(timeRange.end - AjxDateUtil.MSEC_PER_DAY);
		var startWeek = formatter.format(startDate);
		var endWeek = formatter.format(endDate);
		header = startWeek + " - " + endWeek;
	} else {
		var formatter = AjxDateFormat.getDateInstance(AjxDateFormat.FULL);
		header = [
			formatter.format(startDate), 
			"<br><font size=-1>", AjxDateUtil._getWeekday(startDate), "</font>"
		].join("");
	}

	return header;
};

ZmCalColView.prototype._dateUpdate =
function(rangeChanged) {
	this._selectDay(this._date);
	this._clearSelectedTime();
	this._updateSelectedTime();
}

ZmCalColView.prototype._selectDay =
function(date) {
	if (this._numDays == 1 || this._scheduleMode) return;
	var day = this._getDayForDate(date);
	if (day != null) {
		var col = this._columns[day.index];
		if (this._selectedDay) {
	 		var te = document.getElementById(this._selectedCol.titleId);
	 		te.className = this._selectedDay.isToday ? 'calendar_heading_day_today' : 'calendar_heading_day';
		}
		this._selectedDay = day;
		this._selectedCol = col;
		var te = document.getElementById(col.titleId);
 		te.className = day.isToday ? 'calendar_heading_day_today-selected' : 'calendar_heading_day-selected';
	}
}

ZmCalColView.prototype._clearSelectedTime =
function() {
	var e = document.getElementById(this._timeSelectionDivId);
	if (e) Dwt.setVisible(e, false);
}

ZmCalColView.prototype._updateSelectedTime =
function() {
	var t = this._date.getTime();
	if (t < this._timeRangeStart || t >= this._timeRangeEnd)
		return;

	var e = document.getElementById(this._timeSelectionDivId);
	if (!e) return;

	var bounds = this._getBoundsForDate(this._date,  AjxDateUtil.MSEC_PER_HALF_HOUR);
	if (bounds == null) return;
	var snap = this._snapXY(bounds.x, bounds.y, 30);
	if (snap == null) return;

	Dwt.setLocation(e, snap.x, snap.y);
	Dwt.setSize(e, bounds.width, bounds.height);
	Dwt.setOpacity(e, 40);
	Dwt.setVisible(e, true);
}

ZmCalColView.prototype._removeNode =
function(id) {
	var node = document.getElementById(id);
	if (node) node.parentNode.removeChild(node);
}

ZmCalColView.prototype._updateUnionDataHash =
function(index, folderId) {
	var hash = this._unionBusyData[index];
	if (!hash) hash = this._unionBusyData[index] = {};
	hash[folderId] = 1;	
}

ZmCalColView.prototype._updateUnionData =
function(appt) {
	if (appt.isAllDayEvent()) {
		this._updateUnionDataHash(48, appt.folderId);
	} else {
		var em = appt.endDate.getMinutes();
		var eh = appt.endDate.getHours();
		var startIndex = (appt.startDate.getHours()*2) + (appt.startDate.getMinutes() < 30 ? 0 : 1);
		var endIndex = ((eh ? eh : 24) *2) + (em == 0 ? 0 : (em <= 30 ? 1 : 2));
		if (startIndex == endIndex) endIndex++;
		for (var i=startIndex; i < endIndex; i++) {
			this._updateUnionDataHash(i, appt.folderId);
		}
	}
}

ZmCalColView.prototype.addAppt = 
function(appt) {
	ZmCalBaseView.prototype.addAppt.call(this, appt);
	if (this._scheduleMode) {
		this._updateUnionData(appt);
	}
}

ZmCalColView.prototype._resetCalendarData =
function() {
	// TODO: optimize: if calendar list is same, skip!

	// remove existing
	// TODO: optimize, add/remove depending on new calendar length
	if (this._numCalendars > 0) {
		for (var i =0; i < this._numCalendars; i++) {
			var col = this._columns[i];
			this._removeNode(col.titleId);
			this._removeNode(col.headingDaySepDivId);
			this._removeNode(col.daySepDivId);			
		}
	}

	this._calendars = this._controller.getCheckedCalendars();
	this._calendars.sort(ZmCalendar.sortCompare);
	this._folderIdToColIndex = {};
	this._columns = [];
	this._numCalendars = this._calendars.length;

	this._unionBusyData = new Array(); //  0-47, one slot per half hour, 48 all day
	this._unionBusyDataToolTip = new Array(); // tool tips

	var titleParentEl = document.getElementById(this._allDayHeadingDivId);
	var headingParentEl = document.getElementById(this._allDayScrollDivId);
	var dayParentEl = document.getElementById(this._apptBodyDivId);

	for (var i =0; i < this._numCalendars; i++) {
		var col = this._columns[i] = {
			index: i,
			dayIndex: 0,
			cal: this._calendars[i],
			titleId: Dwt.getNextId(),
			headingDaySepDivId: Dwt.getNextId(),
			daySepDivId: Dwt.getNextId(),
			apptX: 0, // computed in layout
			apptWidth: 0,// computed in layout
			allDayX: 0, // computed in layout
			allDayWidth: 0// computed in layout			
		};
		this._folderIdToColIndex[this._calendars[i].id] = col;

		var div = document.createElement("div");
		div.style.position = 'absolute';
		div.className = "calendar_heading_day";
		div.id = col.titleId;
		div.innerHTML = AjxStringUtil.htmlEncode(this._calendars[i].getName());
		titleParentEl.appendChild(div);

		div = document.createElement("div");
		div.className = "calendar_day_separator";
		div.style.position = 'absolute';		
		div.id = col.headingDaySepDivId;
		headingParentEl.appendChild(div);						

		div = document.createElement("div");
		div.className = "calendar_day_separator";
		div.style.position = 'absolute';		
		div.id = col.daySepDivId;
		dayParentEl.appendChild(div);						
	}
}

ZmCalColView.prototype._preSet = 
function() {
	if (this._scheduleMode) this._resetCalendarData(); // cal must be first
	this._resetAllDayData();
}

ZmCalColView.prototype._postSet = 
function() {
	this._computeApptLayout();
	this._computeAllDayApptLayout();
	if (!this._needFirstLayout)
		this._layoutAppts();
	this._layout();
	this._scrollTo8AM();
}

ZmCalColView._inSyncScroll = false;

ZmCalColView.prototype._syncScroll =
function(resetLeft) {
	if (ZmCalColView._inSyncScroll) return
	else ZmCalColView._inSyncScroll = true;
	
	try {
		var bodyElement = document.getElementById(this._bodyDivId);
		var hourElement = document.getElementById(this._hoursScrollDivId);
		var alldayElement = document.getElementById(this._allDayScrollDivId);
		var unionGridScrollElement = document.getElementById(this._unionGridScrollDivId);
		var alldayApptElement = document.getElementById(this._allDayApptScrollDivId);
		hourElement.scrollTop = bodyElement.scrollTop;
		if (resetLeft) bodyElement.scrollLeft = 0;
		alldayElement.scrollLeft = bodyElement.scrollLeft;
		alldayApptElement.scrollLeft = bodyElement.scrollLeft;	
		if (unionGridScrollElement) unionGridScrollElement.scrollTop = bodyElement.scrollTop;
	} finally {
		 ZmCalColView._inSyncScroll = false;
	}
}

ZmCalColView.prototype._horizontalScrollbar =
function(enable) {
	var bodyElement = document.getElementById(this._bodyDivId);
	bodyElement.className = enable ? "calendar_body_hscroll" : "calendar_body";
	if (enable != this._horzEnabled) {
		this._horzEnabled = enable;
		this._syncScroll(true);
	}
}

ZmCalColView.prototype._allDayVerticalScrollbar =
function(enable) {
	var el = document.getElementById(this._allDayApptScrollDivId);
	el.className = enable ? "calendar_allday_appt_vert" : "calendar_allday_appt";
	if (enable != this._vertEnabled) {
		this._vertEnabled = enable;
		this._syncScroll(true);	
	}
}

ZmCalColView.prototype._allDayScrollToBottom =
function() {
	var el = document.getElementById(this._allDayApptScrollDivId);
	el.scrollTop = this._allDayFullDivHeight;
}

ZmCalColView.prototype._scrollTo8AM =
function() {
	if (!this._autoScrollDisabled) {
		var bodyElement = document.getElementById(this._bodyDivId);
		bodyElement.scrollTop = ZmCalColView._HOUR_HEIGHT*8 - 10;
		this._syncScroll();
	} else {
		this._autoScrollDisabled = false;
	}
}

ZmCalColView.prototype._updateTitle =
function() {
	var numDays = this.getNumDays();
	var dayFormatter = DwtCalendar.getDayFormatter();

	if (numDays == 1) {
		var colFormatter = DwtCalendar.getDateFormatter();
		var date = this._date;
		this._title = this._scheduleMode 
			? colFormatter.format(date)
			: dayFormatter.format(date);
	} else {
		var first = this._days[0].date;
		var last = this._days[numDays-1].date;
		this._title = [
			dayFormatter.format(first), " - ", dayFormatter.format(last)
		].join("");
	}				 
}

ZmCalColView.prototype._dayTitle =
function(date) {
	var formatter = this.getNumDays() == 1
				? DwtCalendar.getDateLongFormatter()
				: DwtCalendar.getDateFormatter();
	return formatter.format(date);
};

ZmCalColView.prototype._updateDays =
function() {
	var d = new Date(this._date.getTime());
	d.setHours(0,0,0,0);	
	var dow;
			
	switch(this.view) {
		case ZmController.CAL_WORK_WEEK_VIEW:	
			dow = d.getDay();
			if (dow == 0)
				d.setDate(d.getDate()+1);
			else if (dow != 1)
				d.setDate(d.getDate()-(dow-1));
			break;				
		case ZmController.CAL_WEEK_VIEW:
			var fdow = this.firstDayOfWeek();
			dow = d.getDay();
			if (dow != fdow) {
				d.setDate(d.getDate()-((dow+(7-fdow))%7));
			}
			break;
		case ZmController.CAL_DAY_VIEW:
		default:
			/* nothing */
			break;		
	}

	this._dateToDayIndex = new Object();
	
	var today = new Date();
	today.setHours(0,0,0,0);

	var numDays = this.getNumDays();
	var lastDay = numDays - 1;

	for (var i=0; i < numDays; i++) {
		var day = this._days[i] = {};
		day.index = i;
		day.date = new Date(d);
		day.endDate = new Date(d);
		day.endDate.setHours(23,59,59,999);
		day.isToday = day.date.getTime() == today.getTime();
		this._dateToDayIndex[this._dayKey(day.date)] = day;
		if (!this._scheduleMode) {
	 		var te = document.getElementById(this._columns[i].titleId);
			te.innerHTML = this._dayTitle(d);
			te._type = ZmCalBaseView.TYPE_DAY_HEADER;
			te._dayIndex = i;
			te.className = day.isToday ? 'calendar_heading_day_today' : 'calendar_heading_day';
		}
		d.setDate(d.getDate()+1);		
	}
	var te = document.getElementById(this._headerYearId);
	te.innerHTML = this._days[0].date.getFullYear();
}

ZmCalColView.prototype._resetAllDayData =
function() {
	this._allDayAppts = {};
	this._allDayApptsList = [];
	this._allDayApptsRowLayouts = [];
	this._addAllDayApptRowLayout();
}

/**
 * we don't want allday appts that span days to be fanned out
 */
ZmCalColView.prototype._fanoutAllDay =
function(appt) {
	return false;
}

ZmCalColView.prototype._getDivForAppt =
function(appt) {
	return document.getElementById(appt.isAllDayEvent() ? this._allDayDivId : this._apptBodyDivId);		 
}

ZmCalColView._setApptOpacity =
function(appt, div) {
	switch (appt.ptst) {
		case ZmCalItem.PSTATUS_DECLINED:	Dwt.setOpacity(div, ZmCalColView._OPACITY_APPT_DECLINED); break;
		case ZmCalItem.PSTATUS_TENTATIVE:	Dwt.setOpacity(div, ZmCalColView._OPACITY_APPT_TENTATIVE); break;
		default:							Dwt.setOpacity(div, ZmCalColView._OPACITY_APPT_NORMAL); break;
	}
}

// for the new appt when drag selecting time grid
ZmCalColView.prototype._populateNewApptHtml =
function(div, allDay, folderId) {
	if (folderId == null) folderId = this._controller.getDefaultCalendarFolderId();
	var color = ZmCalendarApp.COLORS[this._controller.getCalendarColor(folderId)];
	var prop = allDay ? "_newAllDayApptColor" : "_newApptColor";
	if (this[prop] && this[prop] == color) return div;
	else this[prop] = color;
	div.style.position = 'absolute';
	Dwt.setSize(div, 10, 10);// will be resized
	div.className = 	"appt-" + DwtCssStyle.SELECTED;
	Dwt.setOpacity(div, ZmCalColView._OPACITY_APPT_DND);
	var subs = {
		id: div.id,
		newState: "",
		headerColor: color + "Light",
		bodyColor: color + "Bg",
		body_style: "",
		name: AjxStringUtil.htmlEncode(ZmMsg.newAppt),
		starttime: "",
		endtime: "",
		location: "",
		status: ""
	};

    var template = allDay ? "calendar_appt_allday" : "calendar_appt";
    div.innerHTML = AjxTemplate.expand("zimbraMail.calendar.templates.Calendar#"+template, subs);
	return div;
}

ZmCalColView.prototype._createItemHtml =
function(appt) {
	//DBG.println("---- createItem ---- "+appt);
	if (appt.isAllDayEvent()) {
		var dataId = appt.getUniqueId();
		var startTime = Math.max(appt.getStartTime(), this._timeRangeStart);
		var data = this._allDayAppts[dataId] = {
			appt: appt,
			startTime: startTime
		};
		this._allDayApptsList.push(appt);
	}
	
	// set up DIV
	var div = document.createElement("div");	

	div.style.position = 'absolute';
	div.style.cursor = 'default';
	Dwt.setSize(div, 10, 10);
	div[DwtListView._STYLE_CLASS] = "appt";	
	div[DwtListView._SELECTED_STYLE_CLASS] = div[DwtListView._STYLE_CLASS] + '-' + DwtCssStyle.SELECTED;
	div.className = div[DwtListView._STYLE_CLASS];

	ZmCalColView._setApptOpacity(appt, div);

	this.associateItemWithElement(appt, div, ZmCalBaseView.TYPE_APPT);

	var isNew = appt.ptst == ZmCalItem.PSTATUS_NEEDS_ACTION;
	var isAccepted = appt.ptst == ZmCalItem.PSTATUS_ACCEPT;
	var id = this._getItemId(appt);
	var color = ZmCalendarApp.COLORS[this._controller.getCalendarColor(appt.folderId)];
	var location = appt.getLocation() ? "<i>"+AjxStringUtil.htmlEncode(appt.getLocation())+"</i>" : "";
	var tree = this._appCtxt.getFolderTree();
	var calendar = tree.getById(appt.folderId);
	var isRemote = Boolean(calendar.url);

	var is30 = (appt._orig.getDuration() <= AjxDateUtil.MSEC_PER_HALF_HOUR);

	var subs = {
		id: id,
		body_style: "",
		newState: isNew ? "_new" : "",
		headerColor: color + (isNew ? "Dark" : "Light"),
		bodyColor: color + (isNew ? "" : "Bg"),
		name: AjxStringUtil.htmlEncode(appt.getName()) + (is30 ? this._padding : ""),
		starttime: appt.getDurationText(true, true),
		endtime: ((!appt._fanoutLast && (appt._fanoutFirst || (appt._fanoutNum > 0))) ? "" : ZmCalItem._getTTHour(appt.endDate))+this._padding,
		location: location,
		status: (appt.isOrganizer() ? "" : appt.getParticipantStatusStr()),
		icon: ((appt.isPrivate()) ? "ReadOnly" : null)
	};	
	
	var template;
	if (appt.isAllDayEvent()) {
		template = "calendar_appt_allday";
		var bs = "";
		if (!this.isStartInView(appt._orig)) bs = "border-left:none;";
		if (!this.isEndInView(appt._orig)) bs += "border-right:none;";
		if (bs != "") subs.body_style = "style='"+bs+"'";
	} else if (is30) {
		template = "calendar_appt_30";
	} else if (appt._fanoutNum > 0) {
		template = "calendar_appt_bottom_only";
	} else {
		template = "calendar_appt";
	}

	div.innerHTML = AjxTemplate.expand("zimbraMail.calendar.templates.Calendar#"+template, subs);

	// if (we can edit this appt) then create sash....
	if (!appt.isReadOnly() && !appt.isAllDayEvent() && !isRemote) {
		if (appt._fanoutLast || (!appt._fanoutFirst && (!appt._fanoutNum))) {
			var bottom = document.createElement("div");
			//sash.id = id+"_sash";
			bottom.className = 'appt_bottom_sash';
			bottom._type = ZmCalBaseView.TYPE_APPT_BOTTOM_SASH;
			div.appendChild(bottom);
		}

		if (appt._fanoutFirst || (!appt._fanoutLast && (!appt._fanoutNum))) {
			var top = document.createElement("div");
			top.className = 'appt_top_sash';
			top._type = ZmCalBaseView.TYPE_APPT_TOP_SASH;
			div.appendChild(top);		
		}
	}
	return div;
}

// TODO: i18n
ZmCalColView.prototype._createHoursHtml =
function(html) {
	html.append("<div style='position:absolute; top:-8; width:", ZmCalColView._HOURS_DIV_WIDTH, "px;' id='", this._bodyHourDivId, "'>");
	html.append("<table class=calendar_grid_day_table>");
	var formatter = DwtCalendar.getHourFormatter();
	var date = new Date();
	date.setHours(0, 0, 0, 0);
	for (var h=0; h < 25; h++) {
		html.append("<tr><td class=calendar_grid_body_time_td style='height:",
		ZmCalColView._HOUR_HEIGHT ,"px; width:", ZmCalColView._HOURS_DIV_WIDTH, "px'><div class=calendar_grid_body_time_text>");
		date.setHours(h);
		html.append(h > 0 && h < 24 ? AjxStringUtil.htmlEncode(formatter.format([h, date])) : "&nbsp;");
		html.append("</div></td></tr>");	
	}
	html.append("</table>", "</div>");	
}

ZmCalColView.prototype._createHtml =
function(abook) {
	this._days = new Object();
	this._columns = new Array();
	this._hours = new Object();

	this._layouts = new Array();
	this._allDayAppts = new Array();

	var html = new AjxBuffer();

	this._headerYearId = Dwt.getNextId();
	this._yearHeadingDivId = Dwt.getNextId();		
	this._yearAllDayDivId = Dwt.getNextId();
	this._leftAllDaySepDivId = Dwt.getNextId();	
	this._leftApptSepDivId = Dwt.getNextId();		

	this._allDayScrollDivId = Dwt.getNextId();
	this._allDayHeadingDivId = Dwt.getNextId();
	this._allDayApptScrollDivId = Dwt.getNextId();
	this._allDayDivId = Dwt.getNextId();
	this._hoursScrollDivId = Dwt.getNextId();
	this._bodyHourDivId = Dwt.getNextId();
	this._allDaySepDivId = Dwt.getNextId();
	this._bodyDivId = Dwt.getNextId();
	this._apptBodyDivId = Dwt.getNextId();
	this._newApptDivId = Dwt.getNextId();
	this._newAllDayApptDivId = Dwt.getNextId();	
	this._timeSelectionDivId = Dwt.getNextId();

	if (this._scheduleMode) {
		this._unionHeadingDivId = Dwt.getNextId();
		this._unionAllDayDivId = Dwt.getNextId();		
		this._unionHeadingSepDivId = Dwt.getNextId();
		this._unionGridScrollDivId = Dwt.getNextId();
		this._unionGridDivId = Dwt.getNextId();
		this._unionGridSepDivId = Dwt.getNextId();
	}		

	this._allDayRows = new Array();

	var numDays = this.getNumDays();
	if (!this._scheduleMode) {
		for (var i =0; i < numDays; i++) {
			this._columns[i] = {
				index: i,
				dayIndex: i,
				titleId: Dwt.getNextId(),
				headingDaySepDivId: Dwt.getNextId(),
				daySepDivId: Dwt.getNextId(),
				apptX: 0, // computed in layout
				apptWidth: 0,// computed in layout
				allDayX: 0, // computed in layout
				allDayWidth: 0// computed in layout
			};
		}
	}

	// year heading	
	html.append("<div id='", this._yearHeadingDivId, "' class=calendar_heading style='position:absolute'>");
	html.append("<div id='", this._headerYearId, 
		"' class=calendar_heading_year_text style='position:absolute; width:", ZmCalColView._HOURS_DIV_WIDTH,"px;'></div>");
	html.append("</div>");

	// div under year
	html.append("<div id='", this._yearAllDayDivId, "' style='position:absolute'></div>");
	
	// sep between year and headings
	html.append("<div id='", this._leftAllDaySepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");

	if (this._scheduleMode) {
		// "All" heading
		html.append("<div id='", this._unionHeadingDivId, "' class=calendar_heading style='position:absolute'>");
		html.append("<div class=calendar_heading_year_text style='position:absolute; width:", ZmCalColView._UNION_DIV_WIDTH,"px;'>",ZmMsg.all,"</div>");
		html.append("</div>");

		// div in all day space
		html.append("<div id='", this._unionAllDayDivId, "' style='position:absolute'></div>");
	
		// sep between year and headings
		html.append("<div id='", this._unionHeadingSepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");
	}

	// all day scroll	=============
	html.append("<div id='", this._allDayScrollDivId, "' style='position:absolute; overflow:hidden;'>");
	
	// all day headings
	html.append("<div id='", this._allDayHeadingDivId, "' class=calendar_heading style='position:absolute'>");
	if (!this._scheduleMode) {
		for (var i =0; i < this.getNumDays(); i++) {
			html.append("<div id='", this._columns[i].titleId, "' class=calendar_heading_day style='position:absolute;'></div>");
		}
	}
	html.append("</div>");
	
	// divs to separate day headings
	if (!this._scheduleMode) {	
		for (var i =0; i < numDays; i++) {
			html.append("<div id='", this._columns[i].headingDaySepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");
		}
	}
	html.append("</div>");
	// end of all day scroll ===========
	
	// div holding all day appts
	html.append("<div id='", this._allDayApptScrollDivId, "' class='calendar_allday_appt' style='position:absolute'>");	
	html.append("<div id='", this._allDayDivId, "' style='position:absolute'>");
	html.append("<div id='", this._newAllDayApptDivId, "' class='appt-Selected' style='position:absolute; display:none;'></div>");	
	html.append("</div>");
	html.append("</div>");
	
	// sep betwen all day and normal appts	
	html.append("<div id='", this._allDaySepDivId, "' class=calendar_header_allday_separator style='overflow:hidden;position:absolute;'></div>");

	// div to hold hours
	html.append("<div id='", this._hoursScrollDivId, "' class=calendar_hour_scroll style='position:absolute;'>");
	this._createHoursHtml(html);	
	html.append("</div>");

	// sep between hours and grid
	html.append("<div id='", this._leftApptSepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");

	// union grid
	if (this._scheduleMode) {
		html.append("<div id='", this._unionGridScrollDivId, "' class=calendar_union_scroll style='position:absolute'>");
		html.append("<div id='", this._unionGridDivId, "' class='ImgCalendarDayGrid__BG' style='width:100%; height:1008px; position:absolute;'>");	
		html.append("</div></div>");
		// sep between union grid and appt grid
		html.append("<div id='", this._unionGridSepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");
	}
	
	// grid body
	html.append("<div id='", this._bodyDivId, "' class=calendar_body style='position:absolute",
								AjxEnv.isSafari ? "; overflow:auto" : "", "'>");
	html.append("<div id='", this._apptBodyDivId, "' class='ImgCalendarDayGrid__BG' style='width:100%; height:1008px; position:absolute;'>");	
	html.append("<div id='", this._timeSelectionDivId, "' class='calendar_time_selection' style='position:absolute; display:none;'></div>");
	html.append("<div id='", this._newApptDivId, "' class='appt-Selected' style='position:absolute; display:none;'></div>");
	if (!this._scheduleMode) {	
		for (var i =0; i < numDays; i++) {
		  html.append("<div id='", this._columns[i].daySepDivId, "' class='calendar_day_separator' style='position:absolute'></div>");
		}
	}
	html.append("</div>");
	html.append("</div>");

	this.getHtmlElement().innerHTML = html.toString();
	
	var myView = this;
	document.getElementById(this._bodyDivId).onscroll = function() {
		myView._syncScroll();
	};

	document.getElementById(this._allDayApptScrollDivId).onscroll = function() {
		myView._syncScroll();
	};
	
	document.getElementById(this._apptBodyDivId)._type = ZmCalBaseView.TYPE_APPTS_DAYGRID;
	document.getElementById(this._bodyHourDivId)._type = ZmCalBaseView.TYPE_HOURS_COL;
	document.getElementById(this._allDayDivId)._type = ZmCalBaseView.TYPE_ALL_DAY;
	this._scrollTo8AM();
}

ZmCalColView.prototype._computeMaxCols =
function(layout, max) {
	//DBG.println("compute max cols for "+layout.appt.id+" col="+layout.col);
	if (layout.maxDone) return layout.maxcol;
	layout.maxcol = Math.max(layout.col, layout.maxcol, max);
	if (layout.right) {
		for (var r in layout.right) {
			layout.maxcol = Math.max(layout.col, this._computeMaxCols(layout.right[r], layout.maxcol));
		}
	}
	//DBG.println("max cols for "+layout.appt.id+" was: "+layout.maxcol);
	layout.maxDone = true;
	return layout.maxcol;	
}

/*
 * compute appt layout for appts that aren't all day
 */
ZmCalColView.prototype._computeApptLayout =
function() {
//	DBG.println("_computeApptLayout");
//	DBG.timePt("_computeApptLayout: start", true);
	var layouts = this._layouts = new Array();
	var list = this.getList();
	if (!list) return;
	
	var size = list.size();
	if (size == 0) return;

	var overlap = null;
	var overlappingCol = null;

	for (var i=0; i < size; i++) {
		var ao = list.get(i);

		if (ao.isAllDayEvent()) {
			continue;
		}

		var newLayout = { appt: ao, col: 0, maxcol: -1};

		overlap = null;
		overlappingCol = null;
		
		// look for overlapping appts
		for (var j=0; j < layouts.length; j++) {
			var layout = layouts[j];
			if (ao.isOverlapping(layout.appt, this._scheduleMode)) {
				if (overlap == null) {
					overlap = [];
					overlappingCol = [];
				}
				overlap.push(layout);
				overlappingCol[layout.col] = true;
				// while we overlap, update our col
				while (overlappingCol[newLayout.col]) {
					newLayout.col++;
				}
			}
		}

		// figure out who is on our right
		if (overlap != null) {
			for (var c in overlap) {
				var l = overlap[c];
				if (newLayout.col < l.col) {
					if (!newLayout.right) newLayout.right = [l];
					else newLayout.right.push(l);
				} else {
					if (!l.right) l.right = [newLayout];
					else l.right.push(newLayout);
				}
			}
		}
		layouts.push(newLayout);
	}
	
	// compute maxcols
	for (var i=0; i < layouts.length; i++) {
		this._computeMaxCols(layouts[i], -1);
//		DBG.timePt("_computeApptLayout: computeMaxCol "+i, false);				
	}
	//DBG.timePt("_computeApptLayout: end", false);	
}

/*
 * add a new all day appt row layout slot and return it
 */
ZmCalColView.prototype._addAllDayApptRowLayout =
function() {
	var data = [];
	var num = this._columns.length;
	for (var i=0; i < num; i++) {
		// free is set to true if slot is available, false otherwise
		// appt is set to the _allDayAppts data in the first slot only (if appt spans days)
		data[i] = { free: true, data: null };
	}
	this._allDayApptsRowLayouts.push(data);
	return data;
}

/**
 * take the appt data in reserve the slots
 */
ZmCalColView.prototype._fillAllDaySlot = 
function(row, colIndex, data) {
	for (var j=0; j < data.numDays; j++) {
		var col = colIndex + j;
		if (col == row.length) break;
		row[col].data = j==0 ? data : null;
		row[col].free = false;
	}
}

/**
 * find a slot and fill it in, adding new rows if needed
 */
ZmCalColView.prototype._findAllDaySlot = 
function(colIndex, data) {
	if (data.appt) {
		var appt = data.appt;
		var startTime = appt.getStartTime();
		var endTime = appt.getEndTime();
		data.numDays = 1;
        if (this.view != ZmController.CAL_SCHEDULE_VIEW) {
            if (startTime != endTime) {
                data.numDays = Math.floor((endTime-startTime) / AjxDateUtil.MSEC_PER_DAY);
            }
            if (startTime < data.startTime) {
                data.numDays -= Math.floor(data.startTime - startTime) / AjxDateUtil.MSEC_PER_DAY;
            }
        }
    }
	var rows = this._allDayApptsRowLayouts;
	var row = null;
	for (var i=0; i < rows.length; i++) {
		row = rows[i];
		for (var j=0; j < data.numDays; j++) {
			var col = colIndex + j;
			if (col == row.length) break;
			if (!row[col].free) {
				row = null;
				break;
			}
		}
		if (row != null)	break;
	}
	if (row == null)
		row = this._addAllDayApptRowLayout();

	this._fillAllDaySlot(row, colIndex, data);	
}

/*
 * compute layout info for all day appts
 */
ZmCalColView.prototype._computeAllDayApptLayout =
function() {
	var adlist = this._allDayApptsList;
	adlist.sort(ZmCalItem.compareByTimeAndDuration);
	
	for (var i=0; i < adlist.length; i++) {
		var appt = adlist[i];
		var data = this._allDayAppts[appt.getUniqueId()];
		if (data) {
			var col = this._scheduleMode ? this._getColForFolderId(data.appt.folderId) : this._getDayForDate(new Date(data.startTime));
			if (col)	 this._findAllDaySlot(col.index, data);			
		}
	}
}

ZmCalColView.prototype._layoutAllDayAppts =
function() {
	var rows = this._allDayApptsRowLayouts;
	if (!rows) return;
	
	var rowY = ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD + 2;
	for (var i=0; i < rows.length; i++) {
		var row = rows[i];
		var num = this._scheduleMode ? this._numCalendars : this.getNumDays();
		for (var j=0; j < num; j++) {
			var slot = row[j];
			if (slot.data) {
				var appt = slot.data.appt;
				var div = document.getElementById(this._getItemId(appt));
				if (this._scheduleMode) {
					var cal = this._getColForFolderId(appt.folderId);
					this._positionAppt(div, cal.allDayX+0, rowY);
					this._sizeAppt(div, cal.allDayWidth * slot.data.numDays - this._daySepWidth - 1,
								 ZmCalColView._ALL_DAY_APPT_HEIGHT);
				 } else {
					this._positionAppt(div, this._columns[j].allDayX+0, rowY);
					this._sizeAppt(div, this._columns[j].allDayWidth * slot.data.numDays - this._daySepWidth - 1,
								 ZmCalColView._ALL_DAY_APPT_HEIGHT);
				 }
				 
			}
		}
		rowY += ZmCalColView._ALL_DAY_APPT_HEIGHT + ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD;
	}
}

ZmCalColView._getApptWidthPercent = 
function(numCols) {
	switch(numCols) {
		case 1: return 1;
		case 2: return 0.8;
		case 3: return 0.6;
		case 4: return 0.4;
		default: return 0.4;
	}
}

ZmCalColView.prototype._positionAppt =
function(apptDiv, x, y) {
	// position overall div
	Dwt.setLocation(apptDiv, x + ZmCalColView._APPT_X_FUDGE, y + ZmCalColView._APPT_Y_FUDGE);
}

ZmCalColView.prototype._sizeAppt =
function(apptDiv, w, h) {
	// set outer as well as inner
	var fw =	 w + ZmCalColView._APPT_WIDTH_FUDGE; // no fudge for you
	var fh = h;
	Dwt.setSize(	apptDiv,	fw >= 0 ? fw : 0, fh >= 0 ? fh : 0);

	// get the inner div that should be sized and set its width/height
	var apptBodyDiv = document.getElementById(apptDiv.id + "_body");
	fw = w + ZmCalColView._APPT_WIDTH_FUDGE;
	fh = h + ZmCalColView._APPT_HEIGHT_FUDGE;
	Dwt.setSize(	apptBodyDiv, fw >= 0 ? fw : 0, fh >= 0 ? fh : 0);
}

ZmCalColView.prototype._layoutAppt =
function(ao, apptDiv, x, y, w, h) {
	// record to restore after dnd/sash
	if (ao) ao._layout = {x: x, y: y, w: w, h: h};
	this._positionAppt(apptDiv, x, y);
	this._sizeAppt(apptDiv, w, h);
}

ZmCalColView.prototype._layoutAppts =
function() {

	// for starting x and width	
	var data = this._hours[0];

	for (var i=0; i < this._layouts.length; i++) {
		var layout = this._layouts[i];
		var apptDiv = document.getElementById(this._getItemId(layout.appt));
		if (apptDiv) {
			layout.bounds = this._getBoundsForAppt(layout.appt);
			var w = Math.floor(layout.bounds.width*ZmCalColView._getApptWidthPercent(layout.maxcol+1));
			var xinc = layout.maxcol ? ((layout.bounds.width - w) / layout.maxcol) : 0; // n-1
			var x = xinc * layout.col + (layout.bounds.x);
			this._layoutAppt(layout.appt, apptDiv, x, layout.bounds.y, w, layout.bounds.height);
		}
	}
}

ZmCalColView.prototype._getDayForDate =
function(d) 
{
	return this._dateToDayIndex[this._dayKey(d)];
}	

ZmCalColView.prototype._getColForFolderId =
function(folderId) 
{
	return this._folderIdToColIndex[folderId];
}	

ZmCalColView.prototype._getColFromX =
function(x) {
	var num = this._columns.length;
	for (var i =0; i < num; i++) {
		var col = this._columns[i];
		if (x >= col.apptX && x <= col.apptX+col.apptWidth) return col;
	}		
	return null;
}

ZmCalColView.prototype._getLocationForDate =
function(d) {
	var h = d.getHours();
	var m = d.getMinutes();
	var day = this._getDayForDate(d);
	if (day == null) return null;
	return new DwtPoint(day.apptX, Math.floor(((h+m/60) * ZmCalColView._HOUR_HEIGHT))+1);
}

ZmCalColView.prototype._getBoundsForAppt =
function(appt) {
	var sd = appt.startDate;
	var endOfDay = new Date(sd);
	endOfDay.setHours(23,59,59,999);
	var et = Math.min(appt.getEndTime(), endOfDay.getTime());
	if (this._scheduleMode)
		return this._getBoundsForCalendar(sd, et - sd.getTime(),
										  ZmOrganizer.normalizeId(appt.folderId));
	else
		return this._getBoundsForDate(sd, et - sd.getTime());
}

ZmCalColView.prototype._getBoundsForDate =
function(d, duration, col) {
	var durationMinutes = duration / 1000 / 60;
	durationMinutes = Math.max(durationMinutes, 22);
	var h = d.getHours();
	var m = d.getMinutes();
	if (col == null && !this._scheduleMode) {
		var day = this._getDayForDate(d);
		col = day ? this._columns[day.index] : null;
	}
	if (col == null) return null;
	return new DwtRectangle(col.apptX, ((h+m/60) * ZmCalColView._HOUR_HEIGHT), 
					col.apptWidth, (ZmCalColView._HOUR_HEIGHT / 60) * durationMinutes);
}

ZmCalColView.prototype._getBoundsForCalendar =
function(d, duration, folderId) {
	var durationMinutes = duration / 1000 / 60;
	durationMinutes = Math.max(durationMinutes, 22);
	var h = d.getHours();
	var m = d.getMinutes();
	var col= this._getColForFolderId(folderId);
	if (col == null) return null;
	return new DwtRectangle(col.apptX, ((h+m/60) * ZmCalColView._HOUR_HEIGHT), 
					col.apptWidth, (ZmCalColView._HOUR_HEIGHT / 60) * durationMinutes);
}

ZmCalColView.prototype._getBoundsForAllDayDate =
function(startSnap, endSnap) {
	if (startSnap == null || endSnap == null) return null;
	return new DwtRectangle(startSnap.col.allDayX, 0, 
			(endSnap.col.allDayX + endSnap.col.allDayWidth) - startSnap.col.allDayX - this._daySepWidth-1, 
			ZmCalColView._ALL_DAY_APPT_HEIGHT);
}

// snapXY coord to specified minute boundary (15,30)
// return x, y, col 
ZmCalColView.prototype._snapXY =
function(x, y, snapMinutes, roundUp) {
	// snap it to grid
	var col = this._getColFromX(x);
	if (col == null) return null;
	x = col.apptX;
	var height = (snapMinutes/60) * ZmCalColView._HOUR_HEIGHT;
	y = Math.floor(y/height) * height;
	if (roundUp) y += height;
	return {x:x, y:y, col:col};	
}

// snapXY coord to specified minute boundary (15,30)
// return x, y, col 
ZmCalColView.prototype._snapAllDayXY =
function(x, y) {
	// snap it to grid
	var col = this._getColFromX(x);	
	if (col == null) return null;
	x = col.allDayX;
	return {x:x, y:0, col:col};
}

ZmCalColView.prototype._getDateFromXY =
function(x, y, snapMinutes, roundUp) {
	var col = this._getColFromX(x);
	if (col == null) return null;
	var minutes = Math.floor((y / ZmCalColView._HOUR_HEIGHT) * 60);
	if (snapMinutes != null && snapMinutes > 1)	{
		minutes = Math.floor(minutes/snapMinutes) * snapMinutes;
		if (roundUp) minutes += snapMinutes;
	}
	var day = this._days[col.dayIndex];
	if (day == null) return null;	
	return new Date(day.date.getTime() + (minutes * 60 * 1000));
}

ZmCalColView.prototype._getAllDayDateFromXY =
function(x, y) {
	var col = this._getColFromX(x);
	if (col == null) return null;
	var day = this._days[col.dayIndex];
	if (day == null) return null;
	return new Date(day.date.getTime());
}

// helper function to minimize code and catch errors
ZmCalColView.prototype._setBounds =
function(id, x, y, w, h) {
	var el = document.getElementById(id);
	if (el == null) {
		DBG.println("ZmCalColView._setBounds null element for id: "+id);
	} else {
		Dwt.setBounds(el, x, y, w, h);
	}
}

ZmCalColView.prototype._calcColWidth = 
function(bodyWidth, numCols, horzScroll) {
//	var sbwfudge = (AjxEnv.isIE ? 1 : 0) + (horzScroll ? 0 : ZmCalColView._SCROLLBAR_WIDTH);
	var sbwfudge = 0;
	return dayWidth = Math.floor((bodyWidth-sbwfudge)/numCols) - (this._daySepWidth == 1 ? 0 : 1);
}

ZmCalColView.prototype._calcMinBodyWidth = 
function(width, numCols) {
	//return minWidth = (ZmCalColView.MIN_COLUMN_WIDTH * numCols) + (this._daySepWidth == 1 ? 0 : 1);
	return minWidth = (ZmCalColView.MIN_COLUMN_WIDTH  + (this._daySepWidth == 1 ? 0 : 1)) * numCols;	
}

ZmCalColView.prototype._layout =
function() {
	DBG.println(AjxDebug.DBG2, "ZmCalColView in layout!");
	this._updateDays();

	var numCols = this._columns.length;

	var sz = this.getSize();
	var width = sz.x;
	var height = sz.y;

	if (width == 0 || height == 0)
		return;

	this._needFirstLayout = false;

	var hoursWidth = ZmCalColView._HOURS_DIV_WIDTH;
	
	var bodyX = hoursWidth + this._daySepWidth;
	var unionX = bodyX;
	if (this._scheduleMode) {
		bodyX += ZmCalColView._UNION_DIV_WIDTH + this._daySepWidth;
	}

	// compute height for hours/grid
	this._bodyDivWidth = width - bodyX;

	// size appts divs
	this._apptBodyDivHeight = ZmCalColView._DAY_HEIGHT + 1; // extra for midnight to show up
	this._apptBodyDivWidth = Math.max(this._bodyDivWidth, this._calcMinBodyWidth(this._bodyDivWidth, numCols));
	var needHorzScroll = this._apptBodyDivWidth > this._bodyDivWidth || AjxEnv.isSafari;
	

	this._horizontalScrollbar(needHorzScroll);
	var sbwfudge = AjxEnv.isIE ? 1 : 0;
	var dayWidth = this._calcColWidth(this._apptBodyDivWidth - ZmCalColView._SCROLLBAR_WIDTH, numCols);

	if (needHorzScroll) this._apptBodyDivWidth -= 18;
	var scrollFudge = needHorzScroll ? 20 : 0; // need all day to be a little wider then grid

	// year heading
	this._setBounds(this._yearHeadingDivId, 0, 0, hoursWidth, Dwt.DEFAULT);	
		
	// column headings
	var allDayHeadingDiv = document.getElementById(this._allDayHeadingDivId);
	Dwt.setBounds(allDayHeadingDiv, 0, 0, this._apptBodyDivWidth + scrollFudge, Dwt.DEFAULT);
	var allDayHeadingDivHeight = Dwt.getSize(allDayHeadingDiv).y;
	
	// div for all day appts
	//var allDayDiv = document.getElementById(this._allDayDivId);
	var numRows = this._allDayApptsRowLayouts ? (this._allDayApptsRowLayouts.length) : 1;	
	if (this._allDayApptsList && this._allDayApptsList.length > 0) numRows++;
	this._allDayFullDivHeight = (ZmCalColView._ALL_DAY_APPT_HEIGHT+ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD) * numRows + ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD;	
	
	this._allDayDivHeight = numRows <= ZmCalColView.MAX_ALLDAY_APPTS ? this._allDayFullDivHeight : 
		(ZmCalColView._ALL_DAY_APPT_HEIGHT+ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD) * ZmCalColView.MAX_ALLDAY_APPTS + ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD;
	var allDayDivY = allDayHeadingDivHeight;

	this._setBounds(this._allDayApptScrollDivId, bodyX, allDayDivY, this._bodyDivWidth, this._allDayDivHeight);
	this._setBounds(this._allDayDivId, 0, 0, this._apptBodyDivWidth + scrollFudge, this._allDayFullDivHeight);

	this._allDayVerticalScrollbar(this._allDayDivHeight != this._allDayFullDivHeight);
	
	// div under year
	this._setBounds(this._yearAllDayDivId, 0, allDayDivY, hoursWidth, this._allDayDivHeight);	

	// all day scroll
	var allDayScrollHeight = allDayDivY + this._allDayDivHeight;
	this._setBounds(this._allDayScrollDivId, bodyX, 0, this._bodyDivWidth, allDayScrollHeight);	

	// vert sep between year and all day headings
	this._setBounds(this._leftAllDaySepDivId, hoursWidth, 0, this._daySepWidth, allDayScrollHeight);		
	
	// horiz separator between all day appts and grid
	this._setBounds(this._allDaySepDivId, 0, allDayScrollHeight, width, ZmCalColView._ALL_DAY_SEP_HEIGHT);	

	var bodyY = allDayScrollHeight + ZmCalColView._ALL_DAY_SEP_HEIGHT +  (AjxEnv.isIE ? 0 : 2);

	this._bodyDivHeight = height - bodyY;

	// hours
	this._setBounds(this._hoursScrollDivId, 0, bodyY, hoursWidth, this._bodyDivHeight);	

	// vert sep between hours and grid
	this._setBounds(this._leftApptSepDivId, hoursWidth, bodyY, this._daySepWidth, ZmCalColView._DAY_HEIGHT);	

	// div for scrolling grid	
	this._setBounds(this._bodyDivId, bodyX, bodyY, this._bodyDivWidth, this._bodyDivHeight);	

	this._setBounds(this._apptBodyDivId, 0, -1, this._apptBodyDivWidth, this._apptBodyDivHeight);

	if (this._scheduleMode) {
		//heading
		this._setBounds(this._unionHeadingDivId, unionX, 0, ZmCalColView._UNION_DIV_WIDTH, Dwt.DEFAULT);
				
		//div under heading
		this._setBounds(this._unionAllDayDivId, unionX, allDayDivY, ZmCalColView._UNION_DIV_WIDTH, this._allDayDivHeight);		

		// sep in all day area
		var unionSepX = unionX + ZmCalColView._UNION_DIV_WIDTH;
		this._setBounds(this._unionHeadingSepDivId, unionSepX, 0, this._daySepWidth, allDayScrollHeight);		

		// div for scrolling union
		this._setBounds(this._unionGridScrollDivId, unionX, bodyY, ZmCalColView._UNION_DIV_WIDTH, this._bodyDivHeight);		
		this._setBounds(this._unionGridDivId, 0, -1, ZmCalColView._UNION_DIV_WIDTH, this._apptBodyDivHeight+ZmCalColView._HOUR_HEIGHT);		

		// sep in grid area
		this._setBounds(this._unionGridSepDivId, unionSepX, bodyY, this._daySepWidth, this._apptBodyDivHeight);		
	}

	var currentX = 0;
	
	for (var i = 0; i < numCols; i++) {
		var col = this._columns[i];
			
		// position day heading
		var day = this._days[col.dayIndex];
		//this._setBounds(col.titleId, currentX+1, Dwt.DEFAULT, dayWidth-2, ZmCalColView._DAY_HEADING_HEIGHT-2);
		this._setBounds(col.titleId, currentX+1, Dwt.DEFAULT, dayWidth, ZmCalColView._DAY_HEADING_HEIGHT);
		col.apptX = currentX + 2 ; //ZZZ
		col.apptWidth = dayWidth - this._daySepWidth - 3;  //ZZZZ
		col.allDayX = col.apptX;
		col.allDayWidth = dayWidth; // doesn't include sep
		currentX += dayWidth;

		this._setBounds(col.headingDaySepDivId, currentX, 0, this._daySepWidth, allDayHeadingDivHeight + this._allDayDivHeight);
		this._setBounds(col.daySepDivId, currentX, 0, this._daySepWidth, this._apptBodyDivHeight);
		currentX += this._daySepWidth;
	}	

	this._layoutAllDayAppts();
	this._layoutAppts();

	this._apptBodyDivOffset = Dwt.toWindow(document.getElementById(this._apptBodyDivId), 0, 0, null, true);

	if (this._scheduleMode) {
		this._layoutUnionData();
	}
}

ZmCalColView.prototype._getUnionToolTip =
function(i) {
	// cache it...
	var tooltip = this._unionBusyDataToolTip[i];
	if (tooltip) return tooltip;
	
	var data = this._unionBusyData[i];
	if (!data instanceof Object) return null;
	
	var html = new AjxBuffer();
	html.append("<table cellpadding=2 cellspacing=0 border=0>");
	var checkedCals = this._controller.getCheckedCalendarFolderIds();
	for (var i=0; i < checkedCals.length; i++) {
		var fid = checkedCals[i];
		if (data[fid]) {
			var cal = this._controller.getCalendar(fid);
			if (cal) {
				var color = ZmCalendarApp.COLORS[cal.color];
				html.append("<tr valign='center' class='", color, "Bg'><td>", AjxImg.getImageHtml(cal.getIcon()), "</td>");
				html.append("<td>", AjxStringUtil.htmlEncode(cal.getName()), "</td></tr>");
			}
		}
	}
	html.append("</table>");
	tooltip = this._unionBusyDataToolTip[i] = html.toString();
	return tooltip;
}
		
ZmCalColView.prototype._layoutUnionDataDiv =
function(gridEl, allDayEl, i, data, numCols) {
	var enable = data instanceof Object;
	var id = this._unionBusyDivIds[i];
	var divEl = null;

//	DBG.println(i + ": "+enable);	
	if (id == null) {
//		DBG.println(i + ": done ID is null");
		if (!enable) return;
		id = this._unionBusyDivIds[i] = Dwt.getNextId();
		var divEl = document.createElement("div");
		divEl.style.position = 'absolute';
		divEl.className = "calendar_sched_union_div";
		divEl.id = id;
		divEl._type = ZmCalBaseView.TYPE_SCHED_FREEBUSY;
		divEl._index = i;

		Dwt.setOpacity(divEl, 40);

		if (i == 48) {
			//have to resize every layout, since all day div height might change
			allDayEl.appendChild(divEl);
		} else {
			// position/size once right here!		
			Dwt.setBounds(divEl, 2, ZmCalColView._HALF_HOUR_HEIGHT*i+1, ZmCalColView._UNION_DIV_WIDTH-4 , ZmCalColView._HALF_HOUR_HEIGHT-2);
			gridEl.appendChild(divEl);			
		}

	} else {
		divEl =  document.getElementById(id);
	}
	// have to relayout each time
	if (i == 48)	Dwt.setBounds(divEl, 1, 1, ZmCalColView._UNION_DIV_WIDTH-2, this._allDayDivHeight-2);


	var num = 0;
	for (var key in data) num++;
	//divEl.innerHTML = num;
		
	Dwt.setOpacity(divEl, 20 + (60 * (num/numCols)));
	Dwt.setVisibility(divEl, enable);
}

ZmCalColView.prototype._layoutUnionData =
function() {
	if (!this._unionBusyData) return;
	var gridEl = document.getElementById(this._unionGridDivId);
	var allDayEl = document.getElementById(this._unionAllDayDivId);	
	var numCols = this._columns.length;
	for (var i=0; i < 49; i++) {
		this._layoutUnionDataDiv(gridEl, allDayEl, i, this._unionBusyData[i], numCols);
	}
}

ZmCalColView.prototype._handleApptScrollRegion =
function(docX, docY, incr) {
	var offset = 0;
	var upper = docY < this._apptBodyDivOffset.y;
	var lower = docY > this._apptBodyDivOffset.y+this._bodyDivHeight;
	
	if (upper || lower) {
		var div = document.getElementById(this._bodyDivId);
		var sTop = div.scrollTop;
		if (upper && sTop > 0) {
			offset = -(sTop > incr ? incr : sTop);
		} else if (lower) {
			var sVisibleTop = this._apptBodyDivHeight - this._bodyDivHeight;
			if (sTop < sVisibleTop) {
				var spaceLeft = sVisibleTop - sTop;
				offset = spaceLeft  > incr ?incr : spaceLeft;
			}
		}
		if (offset != 0)	{
		    div.scrollTop += offset;
    		    this._syncScroll();		    
	    }
	}
	return offset;
}

ZmCalColView.prototype._controlListener =
function(ev) {
	if (ev.newWidth == Dwt.DEFAULT && ev.newHeight == Dwt.DEFAULT) return;
	try {	
		if ((ev.oldWidth != ev.newWidth) || (ev.oldHeight != ev.newHeight)) {
			this._layout();
		}
	} catch(ex) {
		DBG.dumpObj(ex);
	}
}

ZmCalColView.prototype._apptSelected =
function() {
	//
}

ZmCalColView._ondblclickHandler =
function (ev){
	ev = DwtUiEvent.getEvent(ev);
	ev._isDblClick = true;
	ZmCalColView._onclickHandler(ev);
};

ZmCalColView.prototype._mouseOverAction =
function(ev, div) {
	ZmCalBaseView.prototype._mouseOverAction.call(this, ev, div);
	if (div._type == ZmCalBaseView.TYPE_DAY_HEADER) {
		div.style.textDecoration = "underline";
	} else if (div._type == ZmCalBaseView.TYPE_SCHED_FREEBUSY) {
		this.setToolTipContent(this._getUnionToolTip(div._index));
	}
}

ZmCalColView.prototype._mouseOutAction =
function(ev, div) {
	ZmCalBaseView.prototype._mouseOutAction.call(this, ev, div);
	if (div._type == ZmCalBaseView.TYPE_DAY_HEADER) {
		div.style.textDecoration = "none";
	} else if (div._type == ZmCalBaseView.TYPE_SCHED_FREEBUSY) {
		this.setToolTipContent(null);
	}
}

ZmCalColView.prototype._mouseUpAction =
function(ev, div) {
	ZmCalBaseView.prototype._mouseUpAction.call(this, ev, div);
	if (div._type == ZmCalBaseView.TYPE_DAY_HEADER && !this._scheduleMode) {
		var date = this._days[div._dayIndex].date;
		var cc = this._appCtxt.getCurrentController();

		if (this.getNumDays() > 1) {
			cc.setDate(date);
			cc.show(ZmController.CAL_DAY_VIEW);
		} else {
			// TODO: use pref for work week
			if (date.getDay() > 0 && date.getDay() < 6)
				cc.show(ZmController.CAL_WORK_WEEK_VIEW);
			else
				cc.show(ZmController.CAL_WEEK_VIEW);			
		}
	}	
}

ZmCalColView.prototype._doubleClickAction =
function(ev, div) {
	ZmCalBaseView.prototype._doubleClickAction.call(this, ev, div);
	if (div._type == ZmCalBaseView.TYPE_APPTS_DAYGRID || div._type == ZmCalBaseView.TYPE_ALL_DAY) {
		this._timeSelectionAction(ev, div, true);
	}
}

ZmCalColView.prototype._timeSelectionAction =
function(ev, div, dblclick) {
	
	var date;
	var 	duration = AjxDateUtil.MSEC_PER_HALF_HOUR;
	var isAllDay = false;
	var gridLoc;
	var date;
	switch (div._type) {
		case ZmCalBaseView.TYPE_APPTS_DAYGRID:
			gridLoc = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, div, true);
			date = this._getDateFromXY(gridLoc.x, gridLoc.y, 30);
			break;
		case ZmCalBaseView.TYPE_ALL_DAY:
			gridLoc = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, div, true);
			date = this._getAllDayDateFromXY(gridLoc.x, gridLoc.y);
			isAllDay = true;
			break;			
		default:
			return;
	}

	if (date == null) return false;
	var col = this._getColFromX(gridLoc.x);
	var folderId = col ? (col.cal ? col.cal.id : null) : null;

	this._timeSelectionEvent(date, duration, dblclick, isAllDay, folderId, ev.shiftKey);
}

ZmCalColView.prototype._mouseDownAction = 
function(ev, div) {
	//ZmCalBaseView.prototype._mouseDownAction.call(this, ev, div);
	switch (div._type) {
		case	 ZmCalBaseView.TYPE_APPT_BOTTOM_SASH:
		case	 ZmCalBaseView.TYPE_APPT_TOP_SASH:
			//DBG.println("_mouseDownAction for SASH!");
			this.setToolTipContent(null);			
			return this._sashMouseDownAction(ev, div);
			break;
		case ZmCalBaseView.TYPE_APPT:
			this.setToolTipContent(null);		
			return this._apptMouseDownAction(ev, div);
			break;
		case ZmCalBaseView.TYPE_HOURS_COL:
			if (ev.button == DwtMouseEvent.LEFT) {
				var gridLoc = AjxEnv.isIE ? Dwt.toWindow(ev.target, ev.elementX, ev.elementY, div, true) : {x: ev.elementX, y: ev.elementY};
				var fakeLoc = this._getLocationForDate(this.getDate());
				if (fakeLoc) {
					gridLoc.x = fakeLoc.x;
					var gridDiv = document.getElementById(this._apptBodyDivId);
					return this._gridMouseDownAction(ev, gridDiv, gridLoc);
				}
			} else if (ev.button == DwtMouseEvent.RIGHT) {
				DwtUiEvent.copy(this._actionEv, ev);
				this._actionEv.item = this;
				this._evtMgr.notifyListeners(ZmCalBaseView.VIEW_ACTION, this._actionEv);	
			}
			break;
		case ZmCalBaseView.TYPE_APPTS_DAYGRID:
			this._timeSelectionAction(ev, div, false);
			if (ev.button == DwtMouseEvent.LEFT) {
				// save grid location here, since timeSelection might move the time selection div
				var gridLoc = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, div, true);
				return this._gridMouseDownAction(ev, div, gridLoc);
			} else if (ev.button == DwtMouseEvent.RIGHT) {
				DwtUiEvent.copy(this._actionEv, ev);
				this._actionEv.item = this;
				this._evtMgr.notifyListeners(ZmCalBaseView.VIEW_ACTION, this._actionEv);
			}
			break;
		case ZmCalBaseView.TYPE_ALL_DAY:
			this._timeSelectionAction(ev, div, false);
			if (ev.button == DwtMouseEvent.LEFT) {
				var gridLoc = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, div, true);
				return this._gridMouseDownAction(ev, div, gridLoc, true);				
			} else if (ev.button == DwtMouseEvent.RIGHT) {
				DwtUiEvent.copy(this._actionEv, ev);
				this._actionEv.item = this;
				this._evtMgr.notifyListeners(ZmCalBaseView.VIEW_ACTION, this._actionEv);
			}
			break;
			
	}
	return false;
}

// BEGIN APPT ACTION HANDLERS

ZmCalColView.prototype._apptMouseDownAction =
function(ev, apptEl) {
	if (ev.button != DwtMouseEvent.LEFT)
		return false;

	var appt = AjxCore.objectWithId(apptEl._itemIndex);
	var tree = this._appCtxt.getFolderTree();
	var calendar = tree.getById(appt.folderId);
	var isRemote = Boolean(calendar.url);
	if (appt.isReadOnly() || appt.isAllDayEvent() || (appt._fanoutNum > 0) || isRemote) return false;
	
	var apptOffset = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, apptEl, true);

	var data = { 
		dndStarted: false,
		appt: appt, 
		view: this,
		apptEl: apptEl, 
		apptOffset: apptOffset,
		docX: ev.docX,
		docY: ev.docY
	};

	var capture = new DwtMouseEventCapture	(data, null,
			ZmCalColView._emptyHdlr, // mouse over
			ZmCalColView._emptyHdlr, // mouse down (already handled by action)
			ZmCalColView._apptMouseMoveHdlr, 
			ZmCalColView._apptMouseUpHdlr, 
			ZmCalColView._emptyHdlr, // mouse out
			true);
			
	capture.capture();
	return false;	
}

ZmCalColView.prototype._getApptDragProxy =
function(data) {
	// set icon
	var icon = null;
	if (this._apptDragProxyDivId == null) {
		icon = document.createElement("div");
		icon.id = this._apptDragProxyDivId = Dwt.getNextId();
		Dwt.setPosition(icon, Dwt.ABSOLUTE_STYLE);
		this.shell.getHtmlElement().appendChild(icon);
		Dwt.setZIndex(icon, Dwt.Z_DND);
	} else {
		icon = document.getElementById(this._apptDragProxyDivId);
	}
	icon.className = DwtCssStyle.NOT_DROPPABLE;

	var appt = data.appt;
	var formatter = AjxDateFormat.getDateInstance(AjxDateFormat.SHORT);
	var shortDate = formatter.format(appt.startDate);

	// include duration
	var dur = appt.getShortStartHour();
	var color = ZmCalendarApp.COLORS[this._controller.getCalendarColor(appt.folderId)];

	var html = []
	var i = 0;
	html[i++] = "<div class='";
	html[i++] = color;
	html[i++] = appt.ptst == ZmCalItem.PSTATUS_NEEDS_ACTION ? "" : "Bg";
	html[i++] = "'><table><tr><td rowspan=2>";
	html[i++] = AjxImg.getImageHtml("Appointment");
	html[i++] = "</td><td><b>";
	html[i++] = shortDate;
	html[i++] = " ";
	html[i++] = dur;
	html[i++] = "</td></tr><tr><td><b>"
	html[i++] = AjxStringUtil.htmlEncode(appt.getName());
	html[i++] = "</b></td></tr></table></div>";
	icon.innerHTML = html.join("");

	return icon;
}

// called when DND is confirmed after threshold
ZmCalColView.prototype._apptDndBegin =
function(data) {
	var loc = Dwt.getLocation(data.apptEl);
	data.dndObj = {};
	data.apptX = loc.x;
	data.apptY = loc.y;
	data.apptsDiv = document.getElementById(this._apptBodyDivId);
	data.bodyDivEl = document.getElementById(this._bodyDivId);
	data.apptBodyEl = document.getElementById(data.apptEl.id + "_body");	
	data.snap = this._snapXY(data.apptX + data.apptOffset.x, data.apptY, 15); 	// get orig grid snap	
	if (data.snap == null) return false;
	data.startDate = new Date(data.appt.getStartTime());
	data.startTimeEl = document.getElementById(data.apptEl.id +"_st");
	data.endTimeEl = document.getElementById(data.apptEl.id +"_et");

	this.deselectAll();
	this.setSelection(data.appt);
	Dwt.setOpacity(data.apptEl, ZmCalColView._OPACITY_APPT_DND);
	data.dndStarted = true;
	return true;
};

ZmCalColView._apptMouseMoveHdlr =
function(ev) {
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	var data = DwtMouseEventCapture.getTargetObj();

	var deltaX = mouseEv.docX - data.docX;
	var deltaY = mouseEv.docY - data.docY;

	if (!data.dndStarted) {
		var withinThreshold =  (Math.abs(deltaX) < ZmCalColView.DRAG_THRESHOLD && Math.abs(deltaY) < ZmCalColView.DRAG_THRESHOLD);
		if (withinThreshold || !data.view._apptDndBegin(data)) {
			mouseEv._stopPropagation = true;
			mouseEv._returnValue = false;
			mouseEv.setToDhtmlEvent(ev);
			return false;
		}
	}

	var draggedOut = data.view._apptDraggedOut(mouseEv.docX, mouseEv.docY);
    	var obj = data.dndObj;
    	
    if (draggedOut) {
        	// simulate DND
        	if (!data._lastDraggedOut) {
			data._lastDraggedOut = true;
			data.snap.x = null;
			data.snap.y = null;
			data.startDate = new Date(data.appt.getStartTime());
			ZmCalColView._restoreApptLoc(data);
            	if (!data.icon) { 
            	    data.icon = data.view._getApptDragProxy(data);
        	    }
            	Dwt.setVisible(data.icon, true);
        	}
        	Dwt.setLocation(data.icon, mouseEv.docX+5, mouseEv.docY+5);
        	var destDwtObj = mouseEv.dwtObj;
        	var obj = data.dndObj;
        //DBG.println("dwtObj = "+destDwtObj);        	
        	if (destDwtObj && destDwtObj._dropTarget) {
        	    if (destDwtObj != obj._lastDestDwtObj || destDwtObj._dropTarget.hasMultipleTargets()) {
            	    //DBG.println("dwtObj = "+destDwtObj._dropTarget);
        			if (destDwtObj._dropTarget._dragEnter(	Dwt.DND_DROP_MOVE, destDwtObj, {data: data.appt}, mouseEv)) {
	        			//obj._setDragProxyState(true);
	        			data.icon.className = DwtCssStyle.DROPPABLE;
        				obj._dropAllowed = true;
        				destDwtObj._dragEnter(mouseEv);
        			} else {
        				//obj._setDragProxyState(false);
	        			data.icon.className = DwtCssStyle.NOT_DROPPABLE;
        				obj._dropAllowed = false;
        			}
        			//DBG.println(" dropAllowed = "+obj._dropAllowed);
        		} else if (obj._dropAllowed) {
        			destDwtObj._dragOver(mouseEv);
        		}
        	} else {
        		data.icon.className = DwtCssStyle.NOT_DROPPABLE;
        		//obj._setDragProxyState(false);
        	}
        	if (obj._lastDestDwtObj && obj._lastDestDwtObj != destDwtObj && obj._lastDestDwtObj._dropTarget && obj._lastDestDwtObj != obj) {
        		obj._lastDestDwtObj._dragLeave(mouseEv);
        		obj._lastDestDwtObj._dropTarget._dragLeave();
        	}
        	obj._lastDestDwtObj = destDwtObj;
    } else {
        if (data._lastDraggedOut) {
            data._lastDraggedOut = false;
            if (data.icon) Dwt.setVisible(data.icon, false);
            	Dwt.setOpacity(data.apptEl, ZmCalColView._OPACITY_APPT_DND);            
        }
        	obj._lastDestDwtObj = null;
	    var scrollOffset = data.view._handleApptScrollRegion(mouseEv.docX, mouseEv.docY, ZmCalColView._HOUR_HEIGHT);
        	if (scrollOffset != 0) {
        		data.docY -= scrollOffset;	
        		deltaY += scrollOffset;
        	}

        	// snap new location to grid
        	var snap = data.view._snapXY(data.apptX + data.apptOffset.x + deltaX, data.apptY + deltaY, 15);
        	//DBG.println("mouseMove new snap: "+snap.x+","+snap.y+ " data snap: "+data.snap.x+","+data.snap.y);
        	if (snap != null && ((snap.x != data.snap.x || snap.y != data.snap.y))) {
        		var newDate = data.view._getDateFromXY(snap.x, snap.y, 15);
        		if (newDate != null && 
        			(!(data.view._scheduleMode && snap.col != data.snap.col)) && // don't allow col moves in sched view
        			(newDate.getTime() != data.startDate.getTime())) {
        			var bounds = data.view._getBoundsForDate(newDate, data.appt._orig.getDuration(), snap.col);
        			data.view._layoutAppt(null, data.apptEl, bounds.x, bounds.y, bounds.width, bounds.height);
        			data.startDate = newDate;
        			data.snap = snap;
        			if (data.startTimeEl) data.startTimeEl.innerHTML = ZmCalItem._getTTHour(data.startDate);
		        	if (data.endTimeEl) data.endTimeEl.innerHTML = ZmCalItem._getTTHour(new Date(data.startDate.getTime()+data.appt.getDuration()))+data.view._padding;
        		}
        	}
    	}

	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;	
}

ZmCalColView.prototype._apptDraggedOut =
function(docX, docY) {
       //DBG.println(" docX,Y = ("+docX+", "+docY+") abdoX,Y = ("+this._apptBodyDivOffset.x+","+this._apptBodyDivOffset.y+")");
    return (docY < (this._apptBodyDivOffset.y - ZmCalColView._SCROLL_PRESSURE_FUDGE)) ||
        (docY > (this._apptBodyDivOffset.y + this._bodyDivHeight + ZmCalColView._SCROLL_PRESSURE_FUDGE)) ||
        (docX < this._apptBodyDivOffset.x) ||
        (docX > this._apptBodyDivOffset.x + this._bodyDivWidth);
}

ZmCalColView._restoreApptLoc =
function(data) {
	var lo = data.appt._layout;
	data.view._layoutAppt(null, data.apptEl, lo.x, lo.y, lo.w, lo.h);
	if (data.startTimeEl) data.startTimeEl.innerHTML = ZmCalItem._getTTHour(data.appt.startDate);
    	if (data.endTimeEl) data.endTimeEl.innerHTML = ZmCalItem._getTTHour(data.appt.endDate) + data.view._padding;
	ZmCalColView._setApptOpacity(data.appt, data.apptEl);
}

ZmCalColView._apptMouseUpHdlr =
function(ev) {
	//DBG.println("ZmCalColView._apptMouseUpHdlr: "+ev.shiftKey);
	var data = DwtMouseEventCapture.getTargetObj();

	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	

	DwtMouseEventCapture.getCaptureObj().release();


	var draggedOut = data.view._apptDraggedOut(mouseEv.docX, mouseEv.docY);

	if (data.dndStarted) {
        	ZmCalColView._setApptOpacity(data.appt, data.apptEl);
//		data.bodyDivEl.style.cursor = 'auto';
		if (data.startDate.getTime() != data.appt.getStartTime() && !draggedOut) {
            if (data.icon) Dwt.setVisible(data.icon, false);		
			// save before we muck with start/end dates
			var origDuration = data.appt._orig.getDuration();
			data.view._autoScrollDisabled = true;			
			var cc = data.view._appCtxt.getCurrentController();
			var endDate = new Date(data.startDate.getTime() + origDuration);
			var errorCallback = new AjxCallback(null, ZmCalColView._handleError, data);
			var sdOffset = data.startDate ? (data.startDate.getTime() - data.appt.getStartTime()) : null;
			var edOffset = endDate ? (endDate.getTime() - data.appt._orig.getEndTime() ) : null;		
			cc.dndUpdateApptDate(data.appt._orig, sdOffset, edOffset, null, errorCallback, mouseEv);
			//cc.dndUpdateApptDate(data.appt._orig, data.startDate, endDate, null, errorCallback);
		} else {
 //       		ZmCalColView._restoreApptLoc(data);
		}

        if (draggedOut) {
            	var obj = data.dndObj;		
    	        	obj._lastDestDwtObj = null;
        		var destDwtObj = mouseEv.dwtObj;
        		if (destDwtObj != null && destDwtObj._dropTarget != null && obj._dropAllowed && destDwtObj != obj) {
        			destDwtObj._drop(mouseEv);
        			destDwtObj._dropTarget._drop({data: data.appt}, mouseEv);
        			//obj._dragSource._endDrag();
        			//obj._destroyDragProxy(obj._dndProxy);
        			obj._dragging = DwtControl._NO_DRAG;
                 if (data.icon) Dwt.setVisible(data.icon, false);
        		} else {
        			// The following code sets up the drop effect for when an 
        			// item is dropped onto an invalid target. Basically the 
        			// drag icon will spring back to its starting location.
                var bd = data.view._badDrop = { dragEndX: mouseEv.docX, dragEndY: mouseEv.docY, dragStartX: data.docX, dragStartY: data.docY };
                bd.icon = data.icon;
        			if (data.view._badDropAction == null) {
        				data.view._badDropAction = new AjxTimedAction(data.view, data.view._apptBadDropEffect);
        			}
			
		        // Line equation is y = mx + c. Solve for c, and set up d (direction)
			    var m = (bd.dragEndY - bd.dragStartY) / (bd.dragEndX - bd.dragStartX);
			    data.view._badDropAction.args = [m, bd.dragStartY - (m * bd.dragStartX), (bd.dragStartX - bd.dragEndX < 0) ? -1 : 1];
			    AjxTimedAction.scheduleAction(data.view._badDropAction, 0);
		    }
		}
	}

	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	
	return false;	
}

ZmCalColView.prototype._apptBadDropEffect =
function(m, c, d) {
	var usingX = (Math.abs(m) <= 1);
	// Use the bigger delta to control the snap effect
    var bd = this._badDrop;
	var delta = usingX ? bd.dragStartX - bd.dragEndX : bd.dragStartY - bd.dragEndY;
	if (delta * d > 0) {
		if (usingX) {
			bd.dragEndX += (30 * d);
			bd.icon.style.top = m * bd.dragEndX + c;
			bd.icon.style.left = bd.dragEndX;
		} else {
			bd.dragEndY += (30 * d);
			bd.icon.style.top = bd.dragEndY;
			bd.icon.style.left = (bd.dragEndY - c) / m;
		}	
		AjxTimedAction.scheduleAction(this._badDropAction, 0);
 	} else {
 	  Dwt.setVisible(bd.icon, false);
 	  bd.icon = null;
  	}
}

// END APPT ACTION HANDLERS

// BEGIN SASH ACTION HANDLERS

ZmCalColView.prototype._sashMouseDownAction =
function(ev, sash) {
//	DBG.println("ZmCalColView._sashMouseDownHdlr");
	if (ev.button != DwtMouseEvent.LEFT) {
		return false;
	}

	var apptEl = sash.parentNode;
	var apptBodyEl = document.getElementById(apptEl.id + "_body");	

	var appt = AjxCore.objectWithId(apptEl._itemIndex);
	var origHeight = Dwt.getSize(apptBodyEl).y;
	var origLoc = Dwt.getLocation(apptEl);
	var parentOrigHeight = Dwt.getSize(apptEl).y;	
	var isTop = sash._type == ZmCalBaseView.TYPE_APPT_TOP_SASH;
	var data = { 
		sash: sash,
		isTop: isTop,
		appt:appt, 
		view:this,
		apptEl: apptEl, 
		endTimeEl: document.getElementById(apptEl.id +"_et"),
		startTimeEl: document.getElementById(apptEl.id +"_st"),		
		apptBodyEl: apptBodyEl,
		origHeight: origHeight,
		apptX: origLoc.x,
		apptY: origLoc.y,
		parentOrigHeight: parentOrigHeight,		
		startY: ev.docY
	};

	if (isTop) data.startDate = new Date(appt.getStartTime());
	else data.endDate = new Date(appt.getEndTime());
	
	//TODO: only create one of these and change data each time...
	var capture = new DwtMouseEventCapture	(data, null,
			ZmCalColView._emptyHdlr, // mouse over
			ZmCalColView._emptyHdlr, // mouse down (already handled by action)
			ZmCalColView._sashMouseMoveHdlr, 
			ZmCalColView._sashMouseUpHdlr, 
			ZmCalColView._emptyHdlr, // mouse out
			true);
	capture.capture();
	this.deselectAll();
	this.setSelection(data.appt);
	Dwt.setOpacity(apptEl, ZmCalColView._OPACITY_APPT_DND);
	return false;	
}

ZmCalColView._sashMouseMoveHdlr =
function(ev) {
//	DBG.println("ZmCalColView._sashMouseMoveHdlr");
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	var delta = 0;
	var data = DwtMouseEventCapture.getTargetObj();

	if (mouseEv.docY > 0 && mouseEv.docY != data.startY)
		delta = mouseEv.docY - data.startY;

	var draggedOut = data.view._apptDraggedOut(mouseEv.docX, mouseEv.docY);
	
	if (draggedOut) {
        	if (!data._lastDraggedOut) {
			data._lastDraggedOut = true;
	        ZmCalColView._restoreApptLoc(data);
		}	
	} else {
        	if (data._lastDraggedOut) {
			data._lastDraggedOut = false;
			data.lastDelta = 0;
			Dwt.setOpacity(data.apptEl, ZmCalColView._OPACITY_APPT_DND);
		}	
        	var scrollOffset = data.view._handleApptScrollRegion(mouseEv.docX, mouseEv.docY, ZmCalColView._HOUR_HEIGHT);
        	if (scrollOffset != 0) {
        		data.startY -= scrollOffset;	
        	}

        	var delta15 = Math.floor(delta/ZmCalColView._15_MINUTE_HEIGHT);
        	delta = delta15 * ZmCalColView._15_MINUTE_HEIGHT;

        	if (delta != data.lastDelta) {
        		if (data.isTop) {
        			var newY = data.apptY + delta;
        			var newHeight = data.origHeight - delta;
        			if (newHeight >= ZmCalColView._15_MINUTE_HEIGHT) {
        				Dwt.setLocation(data.apptEl, Dwt.DEFAULT, newY);
        				Dwt.setSize(data.apptEl, Dwt.DEFAULT, data.parentOrigHeight - delta);				
        				Dwt.setSize(data.apptBodyEl, Dwt.DEFAULT, Math.floor(newHeight));
        				data.lastDelta = delta;
        				data.startDate.setTime(data.appt.getStartTime() + (delta15 * AjxDateUtil.MSEC_PER_FIFTEEN_MINUTES)); // num msecs in 15 minutes
        				if (data.startTimeEl) data.startTimeEl.innerHTML = ZmCalItem._getTTHour(data.startDate);
    		        	}
        		} else {
        			var newHeight = data.origHeight + delta;
		        	if (newHeight >= ZmCalColView._15_MINUTE_HEIGHT) {
        				var parentNewHeight = data.parentOrigHeight + delta;			
//    	        		DBG.println("delta = " + delta);
        				Dwt.setSize(data.apptEl, Dwt.DEFAULT, parentNewHeight);
        				Dwt.setSize(data.apptBodyEl, Dwt.DEFAULT, newHeight + ZmCalColView._APPT_HEIGHT_FUDGE);

        				data.lastDelta = delta;
        				data.endDate.setTime(data.appt.getEndTime() + (delta15 * AjxDateUtil.MSEC_PER_FIFTEEN_MINUTES)); // num msecs in 15 minutes
        				if (data.endTimeEl) data.endTimeEl.innerHTML = ZmCalItem._getTTHour(data.endDate)+data.view._padding;
        			}
        		}
        	}
    	}

	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;	
}

ZmCalColView._sashMouseUpHdlr =
function(ev) {
//	DBG.println("ZmCalColView._sashMouseUpHdlr");
	var data = DwtMouseEventCapture.getTargetObj();
	ZmCalColView._setApptOpacity(data.appt, data.apptEl);	
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	if (mouseEv.button != DwtMouseEvent.LEFT) {
		DwtUiEvent.setBehaviour(ev, true, false);
		return false;
	}
	
	DwtMouseEventCapture.getCaptureObj().release();

	//data.sash.innerHTML = "";
//	data.sash.className = 'appt_sash';
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);

	var draggedOut = data.view._apptDraggedOut(mouseEv.docX, mouseEv.docY);
    if (draggedOut) {
        ZmCalColView._restoreApptLoc(data);
        return false;
    }

	var needUpdate = false;
	var startDate = null, endDate = null;
	if (data.isTop && data.startDate.getTime() != data.appt.getStartTime()) {
		needUpdate = true;
		startDate = data.startDate;
	} else if (!data.isTop && data.endDate.getTime() != data.appt.getEndTime()) {
		needUpdate = true;
		endDate = data.endDate;
	}
	if (needUpdate) {
		data.view._autoScrollDisabled = true;
		var cc = data.view.getController();
		var errorCallback = new AjxCallback(null, ZmCalColView._handleError, data);
		var sdOffset = startDate ? (startDate.getTime() - data.appt.getStartTime()) : null;
		var edOffset = endDate ? (endDate.getTime() - data.appt.getEndTime()) : null;		
		cc.dndUpdateApptDate(data.appt._orig, sdOffset, edOffset, null, errorCallback, mouseEv);
	}
	
	return false;
}

// END SASH ACTION HANDLERS


// BEGIN GRID ACTION HANDLERS

ZmCalColView.prototype._gridMouseDownAction =
function(ev, gridEl, gridLoc, isAllDay) {
	if (ev.button != DwtMouseEvent.LEFT) {
		return false;
	}

	//DBG.println("gridLoc: "+gridLoc.x+","+gridLoc.y);

	//var gridLoc = Dwt.toWindow(ev.target, ev.elementX, ev.elementY, gridEl);

	var data = { 
		dndStarted: false,
		view: this,
		gridEl: gridEl, 
		gridX: gridLoc.x, // ev.elementX,
		gridY: gridLoc.y,  //ev.elementY,
		docX: ev.docX,
		docY: ev.docY,
		isAllDay: isAllDay
	};

	var capture = new DwtMouseEventCapture	(data, null,
			ZmCalColView._emptyHdlr, // mouse over
			ZmCalColView._emptyHdlr, // mouse down (already handled by action)
			isAllDay? ZmCalColView._gridAllDayMouseMoveHdlr : ZmCalColView._gridMouseMoveHdlr,
			ZmCalColView._gridMouseUpHdlr, 
			ZmCalColView._emptyHdlr, // mouse out
			true);
	capture.capture();
	return false;	
}

// called when DND is confirmed after threshold
ZmCalColView.prototype._gridDndBegin =
function(data) {
	var col = data.view._getColFromX(data.gridX);
	data.folderId = col ? (col.cal ? col.cal.id : null) : null;

	if (data.isAllDay) {
		data.gridEl.style.cursor = 'e-resize';	
		data.newApptDivEl = document.getElementById(data.view._newAllDayApptDivId);
		data.view._populateNewApptHtml(data.newApptDivEl, true, data.folderId);		
		data.apptBodyEl = document.getElementById(data.newApptDivEl.id + "_body");	
		data.view._allDayScrollToBottom();
		//zzzzz
	} else {
		data.gridEl.style.cursor = 's-resize';	
		data.newApptDivEl = document.getElementById(data.view._newApptDivId);
		data.view._populateNewApptHtml(data.newApptDivEl, false, data.folderId);
		data.apptBodyEl = document.getElementById(data.newApptDivEl.id + "_body");
		data.endTimeEl = document.getElementById(data.newApptDivEl.id +"_et");
		data.startTimeEl = document.getElementById(data.newApptDivEl.id +"_st");
	}
	this.deselectAll();
	return true;
}

ZmCalColView._gridMouseMoveHdlr =
function(ev) {

	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	var data = DwtMouseEventCapture.getTargetObj();

	var deltaX = mouseEv.docX - data.docX;
	var deltaY = mouseEv.docY - data.docY;

	if (!data.dndStarted) {
		var withinThreshold =  (Math.abs(deltaX) < ZmCalColView.DRAG_THRESHOLD && Math.abs(deltaY) < ZmCalColView.DRAG_THRESHOLD);
		if (withinThreshold || !data.view._gridDndBegin(data)) {
			mouseEv._stopPropagation = true;
			mouseEv._returnValue = false;
			mouseEv.setToDhtmlEvent(ev);
			return false;
		}
	}

	var scrollOffset = data.view._handleApptScrollRegion(mouseEv.docX, mouseEv.docY, ZmCalColView._HOUR_HEIGHT);
	if (scrollOffset != 0) {
		data.docY -= scrollOffset;	
		deltaY += scrollOffset;
	}

	// snap new location to grid
	var snap = data.view._snapXY(data.gridX + deltaX, data.gridY + deltaY, 30);
	if (snap == null) return false;
	
	var newStart, newEnd;

	if (deltaY >= 0) { // dragging down
		newStart = data.view._snapXY(data.gridX, data.gridY, 30);
		newEnd = data.view._snapXY(data.gridX, data.gridY + deltaY, 30, true);
	} else { // dragging up
		newEnd = data.view._snapXY(data.gridX, data.gridY, 30);
		newStart = data.view._snapXY(data.gridX, data.gridY + deltaY, 30);
	}

	if (newStart == null || newEnd == null) return false;

	if ((data.start == null) || (data.start.y != newStart.y) || (data.end.y != newEnd.y)) {

		if (!data.dndStarted) data.dndStarted = true;

		data.start = newStart;
		data.end = newEnd;
		
		data.startDate = data.view._getDateFromXY(data.start.x, data.start.y, 30, false);
		data.endDate = data.view._getDateFromXY(data.end.x, data.end.y, 30, false);

		var e = data.newApptDivEl;
		if (!e) return;
		var duration = (data.endDate.getTime() - data.startDate.getTime());
		if (duration < AjxDateUtil.MSEC_PER_HALF_HOUR) duration = AjxDateUtil.MSEC_PER_HALF_HOUR;

		var bounds = data.view._getBoundsForDate(data.startDate, duration, newStart.col);
		if (bounds == null) return false;
		data.view._layoutAppt(null, e, newStart.x, newStart.y, bounds.width, bounds.height);
		Dwt.setVisible(e, true);
		if (data.startTimeEl) data.startTimeEl.innerHTML = ZmCalItem._getTTHour(data.startDate);
		if (data.endTimeEl) data.endTimeEl.innerHTML = ZmCalItem._getTTHour(data.endDate);
	}
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;	
}

ZmCalColView._gridMouseUpHdlr =
function(ev) {
//	DBG.println("ZmCalColView._sashMouseUpHdlr");
	var data = DwtMouseEventCapture.getTargetObj();
	//ZmCalColView._setApptOpacity(data.appt, data.apptEl);	
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	

	DwtMouseEventCapture.getCaptureObj().release();
	
	if (data.dndStarted) {
		data.gridEl.style.cursor = 'auto';
		Dwt.setVisible(data.newApptDivEl, false);		
		if (data.isAllDay) {
			data.view._appCtxt.getCurrentController().newAllDayAppointmentHelper(data.startDate, data.endDate, data.folderId, mouseEv.shiftKey);		
		} else {
			var duration = (data.endDate.getTime() - data.startDate.getTime());
			if (duration < AjxDateUtil.MSEC_PER_HALF_HOUR) duration = AjxDateUtil.MSEC_PER_HALF_HOUR;	
			data.view._appCtxt.getCurrentController().newAppointmentHelper(data.startDate, duration, data.folderId, mouseEv.shiftKey);
		}
	}

	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	
	return false;	
}

// END GRID ACTION HANDLERS

// BEGIN ALLDAY GRID ACTION HANDLERS

ZmCalColView._gridAllDayMouseMoveHdlr =
function(ev) {

	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);	
	var data = DwtMouseEventCapture.getTargetObj();

	var deltaX = mouseEv.docX - data.docX;
	var deltaY = mouseEv.docY - data.docY;

	if (!data.dndStarted) {
		var withinThreshold =  (Math.abs(deltaX) < ZmCalColView.DRAG_THRESHOLD && Math.abs(deltaY) < ZmCalColView.DRAG_THRESHOLD);
		if (withinThreshold || !data.view._gridDndBegin(data)) {
			mouseEv._stopPropagation = true;
			mouseEv._returnValue = false;
			mouseEv.setToDhtmlEvent(ev);
			return false;
		}
	}

	// snap new location to grid
	var snap = data.view._snapXY(data.gridX + deltaX, data.gridY + deltaY, 30);
	if (snap == null) return false;
	
	var newStart, newEnd;

	if (deltaX >= 0) { // dragging right
		newStart = data.view._snapAllDayXY(data.gridX, data.gridY);
		newEnd = data.view._snapAllDayXY(data.gridX + deltaX, data.gridY);
	} else { // dragging left
		newEnd = data.view._snapAllDayXY(data.gridX, data.gridY);
		newStart = data.view._snapAllDayXY(data.gridX + deltaX, data.gridY);
	}

	if (newStart == null || newEnd == null) return false;

	if ((data.start == null) || (!data.view._scheduleMode && ((data.start.x != newStart.x) || (data.end.x != newEnd.x)))) {

		if (!data.dndStarted) data.dndStarted = true;

		data.start = newStart;
		data.end = newEnd;
		
		data.startDate = data.view._getAllDayDateFromXY(data.start.x, data.start.y);
		data.endDate = data.view._getAllDayDateFromXY(data.end.x, data.end.y);

		var e = data.newApptDivEl;
		if (!e) return;

		var bounds = data.view._getBoundsForAllDayDate(data.start, data.end);
		if (bounds == null) return false;
		// blank row at the bottom
		var y = data.view._allDayFullDivHeight - (ZmCalColView._ALL_DAY_APPT_HEIGHT+ZmCalColView._ALL_DAY_APPT_HEIGHT_PAD);
		Dwt.setLocation(e, newStart.x, y);
		Dwt.setSize(e, bounds.width, bounds.height);
		Dwt.setSize(data.apptBodyEl, bounds.width, bounds.height);		
		Dwt.setVisible(e, true);
	}
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;	
}

// END ALLDAY GRID ACTION HANDLERS

ZmCalColView._emptyHdlr =
function(ev) {
	var mouseEv = DwtShell.mouseEvent;
	mouseEv.setFromDhtmlEvent(ev);
	mouseEv._stopPropagation = true;
	mouseEv._returnValue = false;
	mouseEv.setToDhtmlEvent(ev);
	return false;
}

ZmCalColView._handleError =
function(data) {
	data.view.getController()._refreshAction(true);
	return false;
}
