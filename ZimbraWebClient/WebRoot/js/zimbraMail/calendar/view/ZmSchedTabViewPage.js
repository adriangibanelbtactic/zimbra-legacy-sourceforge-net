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

/**
* Creates a new tab view that can be used to overload DwtTabView base class methods
* @constructor
* @class
*
* @author Parag Shah
* @param parent			the element that created this view
* @param appCtxt 		app context
*/
function ZmSchedTabViewPage(parent, appCtxt, apptTab, controller) {
	DwtTabViewPage.call(this, parent);

	this._appCtxt = appCtxt;
	this._apptTab = apptTab;
	this._controller = controller;

	this.setScrollStyle(Dwt.SCROLL);
	this._doc = this.getDocument();
	this._rendered = false;
	this._attendees = new Object();
	this._schedTable = new Array();
	this._allAttendees = new Array(48);
	this._allAttendeeSlot = null;

	this._fbCallback = new AjxCallback(this, this._handleResponseFreeBusy);
};

ZmSchedTabViewPage.prototype = new DwtTabViewPage;
ZmSchedTabViewPage.prototype.constructor = ZmSchedTabViewPage;


// Consts

ZmSchedTabViewPage.FREEBUSY_ROW_HEIGHT		= 25;
ZmSchedTabViewPage.FREEBUSY_ATTENDEE_WIDTH	= 150;
ZmSchedTabViewPage.FREEBUSY_NEXTPREV_WIDTH	= 21;
ZmSchedTabViewPage.FREEBUSY_INIT_ATTENDEES	= 12;

ZmSchedTabViewPage.STATUS_FREE				= 1;
ZmSchedTabViewPage.STATUS_BUSY				= 2;
ZmSchedTabViewPage.STATUS_TENTATIVE			= 3;
ZmSchedTabViewPage.STATUS_OUT				= 4;
ZmSchedTabViewPage.STATUS_UNKNOWN			= 5;


// Public methods

ZmSchedTabViewPage.prototype.toString = 
function() {
	return "ZmSchedTabViewPage";
};

ZmSchedTabViewPage.prototype.showMe = 
function() {
	this.setZIndex(DwtTabView.Z_ACTIVE_TAB); // XXX: is this necessary?

	if (!this._rendered)
		this._initialize();

	var pSize = this.parent.getSize();
	this.resize(pSize.x, pSize.y);

	// set the free/busy view w/ fresh data
	this.set(this._apptTab.getDateInfo(), this._apptTab.getAttendees());

	this.parent.tabSwitched(this._tabKey);
};

ZmSchedTabViewPage.prototype.initialize = 
function(appt, mode) {
	this._appt = appt;
	this._mode = mode;
};

ZmSchedTabViewPage.prototype.set =
function(dateInfo, attendees) {
	this._startDateField.value = dateInfo.startDate;
	this._endDateField.value = dateInfo.endDate;
	if (dateInfo.startTime && dateInfo.endTime) {
		this._allDayCheckbox.checked = false;
		this._showTimeFields(true);
		this._startTimeSelect.setSelectedValue(dateInfo.startTime);
		this._endTimeSelect.setSelectedValue(dateInfo.endTime);
	} else {
		this._allDayCheckbox.checked = true;
		this._showTimeFields(false);
	}
	this._resetFullDateField();

	this._setAttendees(attendees);
};

ZmSchedTabViewPage.prototype.cleanup = 
function() {
	if (!this._rendered) return;

	// XXX: optimize later - cache objects instead of iterating DOM!
	for (var i = 0; i < this._schedTable.length; i++) {
		var sched = this._schedTable[i];

		// set all visible input elements to invisible
		var inputEl = Dwt.getDomObj(this._doc, sched.dwtInputId);
		// re-enable the first row (which is the "read-only" organizer row)
		if (i == 0)
			inputEl.disabled = false;
		this._cleanRow(inputEl, sched);
	}

	// cleanup all attendees row
	var allAttCells = this._allAttendeeSlot._coloredCells;
	while (allAttCells.length > 0) {
		allAttCells[0].style.backgroundColor = "";
		allAttCells.shift();
	}

	for (var i in this._attendees)
		delete this._attendees[i];

	this._resetAttendeeCount();
};

ZmSchedTabViewPage.prototype.isDirty =
function() {
	return false;
};

ZmSchedTabViewPage.prototype.isValid = 
function() {
	return true;
};

ZmSchedTabViewPage.prototype.enableInputs = 
function() {
	// TODO
	DBG.println("TODO: enable inputs for schedule tab view");
};

ZmSchedTabViewPage.prototype.resize = 
function(newWidth, newHeight) {
	if (!this._rendered) return;

	if (newWidth) {
		this.setSize(newWidth);
	}
	
	if (newHeight) {
		this.setSize(Dwt.DEFAULT, newHeight - 30);
	}
};


// Private / protected methods

ZmSchedTabViewPage.prototype._initialize = 
function() {
	this._createHTML();
	this._createDwtObjects();
	this._cacheFields();
	this._initAutocomplete();
	this._addEventHandlers();
	this._resetAttendeeCount();

	this._rendered = true;
};

ZmSchedTabViewPage.prototype._createHTML = 
function() {
	var html = new Array();
	var i = 0;

	html[i++] = "<table border=0 width=100%><tr><td>";
	html[i++] = this._getTimeHtml();
	html[i++] = "</td><td class='ZmSchedTabViewPageKey'>";
	html[i++] = this._getKeyHtml();
	html[i++] = "</td></tr></table><p>";
	html[i++] = this._getFreeBusyHtml();

	this.getHtmlElement().innerHTML = html.join("");
};

// XXX: refactor this code since ZmApptTabViewPage uses similar?
ZmSchedTabViewPage.prototype._getTimeHtml = 
function() {
	var html = new Array();
	var i = 0;

	this._startDateFieldId 		= Dwt.getNextId();
	this._startMiniCalBtnId 	= Dwt.getNextId();
	this._startTimeSelectId 	= Dwt.getNextId();
	this._allDayCheckboxId 		= Dwt.getNextId();
	this._endDateFieldId 		= Dwt.getNextId();
	this._endMiniCalBtnId 		= Dwt.getNextId();
	this._endTimeSelectId 		= Dwt.getNextId();

	var html = new Array();
	var i = 0;
	
	html[i++] = "<table border=0>";
	html[i++] = "<tr><td class='ZmApptTabViewPageField'>";
	html[i++] = ZmMsg.meetingStart;
	html[i++] = "</td><td>";
	html[i++] = "<table border=0 cellpadding=0 cellspacing=0><tr><td>";
	html[i++] = "<input style='height:22px;' type='text' size=11 maxlength=10 id='";
	html[i++] = this._startDateFieldId;
	html[i++] = "' value=''></td><td id='";
	html[i++] = this._startMiniCalBtnId;
	html[i++] = "'></td>";
	html[i++] = "</tr></table></td>";
	html[i++] = "<td>@</td><td id='";
	html[i++] = this._startTimeSelectId;
	html[i++] = "'></td><td><input type='checkbox' id='";
	html[i++] = this._allDayCheckboxId;
	html[i++] = "'></td><td><nobr>";
	html[i++] = ZmMsg.allDayEvent;
	html[i++] = "</td><td width=100%></td></tr><tr><td class='ZmApptTabViewPageField'>";
	html[i++] = ZmMsg.meetingEnd;
	html[i++] = "</td><td>";
	html[i++] = "<table border=0 cellpadding=0 cellspacing=0><tr><td>";
	html[i++] = "<input style='height:22px;' type='text' size=11 maxlength=10 id='";
	html[i++] = this._endDateFieldId;
	html[i++] = "' value=''></td><td id='";
	html[i++] = this._endMiniCalBtnId;
	html[i++] = "'></td>";
	html[i++] = "</tr></table></td>";
	html[i++] = "<td>@</td><td id='";
	html[i++] = this._endTimeSelectId;
	html[i++] = "'></td></tr>";
	// XXX: note we're ignoring time zones for now
	html[i++] = "</table>";

	return html.join("");
};

ZmSchedTabViewPage.prototype._getKeyHtml = 
function() {
	var html = new Array();
	var i = 0;

	html[i++] = "<table border=0 cellpadding=0 cellspacing=0 style='border:1px solid black'><tr>";
	html[i++] = "<td style='padding:3px; background-color:#CCCCCC; font-weight:bold'>";
	html[i++] = ZmMsg.key;
	html[i++] = "</td></tr><tr><td style='padding:3px; background-color:#FFFFFF'>";
	html[i++] = "<table border=0 cellpadding=3 cellspacing=3><tr>";
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#ADD6D6'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.selected;
	html[i++] = "</td>"
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#990000'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.busy;
	html[i++] = "</td>"
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#FFCC00'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.outOfOffice;
	html[i++] = "</td>"
	html[i++] = "</tr><tr>";
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#FFFFFF'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.free;
	html[i++] = "</td>"
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#FF3300'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.tentative;
	html[i++] = "</td>"
	html[i++] = "<td><div class='ZmSchedTabViewPageKeySquare' style='background-color:#FFF5CC'></div></td>";
	html[i++] = "<td class='nobreak'>";
	html[i++] = ZmMsg.unknown;
	html[i++] = "</td>"
	html[i++] = "</tr>";
	html[i++] = "</table>";
	html[i++] = "</td></tr></table>";
	
	return html.join("");	
};

ZmSchedTabViewPage.prototype._getFreeBusyHtml =
function() {
	this._navToolbarId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	html[i++] = "<table border=0 cellpadding=2 cellspacing=3 width=100%><tr><td>";
	html[i++] = "<table border=0 cellpadding=0 cellspacing=0 width=100%><tr>";
	html[i++] = "<td id='";
	html[i++] = this._navToolbarId;
	html[i++] = AjxEnv.isIE ? "' width=100%>" : "'>";
	html[i++] = "</td>";
	html[i++] = "<td width=626>";
	
	html[i++] = "<table border=0 cellpadding=0 cellspacing=0><tr>";
	for (var j = 0; j < 2; j++) {
		for (var k = 12; k < 24; k++) {
			var hour = k - 12;
			if (hour == 0) hour = 12;
	
			html[i++] = "<td><div class='ZmSchedTabViewPageCell'>";
			html[i++] = hour;
			html[i++] = "</div></td><td><div class='ZmSchedTabViewPageCell'></div></td>";
		}
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	for (var j = 0; j < ZmSchedTabViewPage.FREEBUSY_INIT_ATTENDEES; j++) {
		// store some meta data about this table row
		var attendee = new Object();
		var dwtId = Dwt.getNextId();
		attendee.dwtDivId = dwtId + "_DIV_";
		attendee.dwtInputId = dwtId + "_INPUT_";
		attendee.dwtTableId = dwtId + "_TABLE_";
		attendee.idx = j;
		attendee._coloredCells = new Array();

		if (j == 0) {
			this._allAttendeeSlot = attendee;
		} else {
			this._schedTable.push(attendee);
		}

		html[i++] = "<tr>";
		html[i++] = "<td><table border=0 width=100% cellpadding=0 cellspacing=0 class='ZmSchedTabViewPageTable'><tr>";
		html[i++] = "<td";
		html[i++] = j == ZmSchedTabViewPage.FREEBUSY_INIT_ATTENDEES-1 ? " style='border-bottom:1px solid #CCCCCC'>" : ">";
		html[i++] = "<div class='ZmSchedTabViewPageName' id='";
		html[i++] = attendee.dwtDivId;
		html[i++] = "'>";
		// make the first row the "All Attendees" row
		if (j == 0) {
			html[i++] = "<table border=0 bgcolor='#FFFFFF' cellpadding=0 cellspacing=0 width=100% height=100%><tr height=100%><td class='ZmSchedTabViewPageAll'>";
			html[i++] = ZmMsg.allAttendees;
			html[i++] = "</td></tr></table>";
		} else {
			html[i++] = "<input type='text' class='ZmSchedTabViewPageInput' maxlength=256 id='";
			html[i++] = attendee.dwtInputId;
			html[i++] = "'>";
		}
		if (j > 0) {
			html[i++] = "&nbsp;&nbsp;";
			html[i++] = ZmMsg.clickHereToAddName;
		}
		html[i++] = "</div></td>";
		html[i++] = "</tr></table>";
		html[i++] = "</td>";
		html[i++] = "<td";
		html[i++] = j == ZmSchedTabViewPage.FREEBUSY_INIT_ATTENDEES-1 ? " style='border-bottom:1px solid #CCCCCC'>" : ">";
		html[i++] = "<table border=0 cellpadding=0 cellspacing=0 class='ZmSchedTabViewPageTable' id='";
		html[i++] = attendee.dwtTableId;
		html[i++] = "'><tr";
		html[i++] = j == 0 ? " style='background-color:#FFFFFF'>" : ">";
		for (var k = 0; k < 48; k++)
			html[i++] = "<td><div class='ZmSchedTabViewPageGrid'></div></td>";
		html[i++] = "</tr></table>";
		html[i++] = "</td></tr>";
	}
	html[i++] = "</table>";
	html[i++] = "</td></tr></table>";

	return html.join("");
};

// XXX: refactor this code since ZmApptTabViewPage uses similar?
ZmSchedTabViewPage.prototype._createDwtObjects = 
function() {
	var timeSelectListener = new AjxListener(this, this._timeChangeListener);
	// create selects for Time section
	this._startTimeSelect = new DwtSelect(this);
	this._startTimeSelect.addChangeListener(timeSelectListener);
	var timeOptions = ZmApptViewHelper.getTimeOptionValues();
	if (timeOptions) {
		for (var i = 0; i < timeOptions.length; i++) {
			var option = timeOptions[i];
			this._startTimeSelect.addOption(option.label, option.selected, option.value);
		}
	}
	var startTimeCell = Dwt.getDomObj(this._doc, this._startTimeSelectId);
	if (startTimeCell)
		startTimeCell.appendChild(this._startTimeSelect.getHtmlElement());
	delete this._startTimeSelectId;

	this._endTimeSelect = new DwtSelect(this);
	this._endTimeSelect.addChangeListener(timeSelectListener);
	if (timeOptions) {
		for (var i = 0; i < timeOptions.length; i++) {
			var option = timeOptions[i];
			this._endTimeSelect.addOption(option.label, option.selected, option.value);
		}
	}
	var endTimeCell = Dwt.getDomObj(this._doc, this._endTimeSelectId);
	if (endTimeCell)
		endTimeCell.appendChild(this._endTimeSelect.getHtmlElement());
	delete this._endTimeSelectId;

	// create down arrow buttons for mini calendar
	var dateButtonListener = new AjxListener(this, this._dateButtonListener);
	this._startDateButton = new DwtButton(this);
	this._startDateButton.setImage("SelectPullDownArrow");
	this._startDateButton.addSelectionListener(dateButtonListener);
	this._startDateButton.setSize(20, 20);
	// reparent
	var startButtonCell = Dwt.getDomObj(this._doc, this._startMiniCalBtnId);
	if (startButtonCell)
		startButtonCell.appendChild(this._startDateButton.getHtmlElement());
	delete this._startMiniCalBtnId;
	
	this._endDateButton = new DwtButton(this);
	this._endDateButton.setImage("SelectPullDownArrow");
	this._endDateButton.addSelectionListener(dateButtonListener);
	this._endDateButton.setSize(20, 20);
	// reparent
	var endButtonCell = Dwt.getDomObj(this._doc, this._endMiniCalBtnId);
	if (endButtonCell)
		endButtonCell.appendChild(this._endDateButton.getHtmlElement());
	delete this._endMiniCalBtnId;

	var navBarListener = new AjxListener(this, this._navBarListener);
	this._navToolbar = new ZmNavToolBar(this, DwtControl.STATIC_STYLE, null, ZmNavToolBar.SINGLE_ARROWS, true);
	this._navToolbar._textButton.getHtmlElement().className = "ZmSchedTabViewPageDate";
	this._navToolbar.addSelectionListener(ZmOperation.PAGE_BACK, navBarListener);
	this._navToolbar.addSelectionListener(ZmOperation.PAGE_FORWARD, navBarListener);
	// reparent
	var navbarCell = Dwt.getDomObj(this._doc, this._navToolbarId);
	if (navbarCell)
		navbarCell.appendChild(this._navToolbar.getHtmlElement());
	delete this._navToolbarId;
};

ZmSchedTabViewPage.prototype._cacheFields = 
function() {
	this._startDateField 	= Dwt.getDomObj(this._doc, this._startDateFieldId); delete this._startDateFieldId;
	this._endDateField 		= Dwt.getDomObj(this._doc, this._endDateFieldId);	delete this._endDateFieldId;
	this._allDayCheckbox 	= Dwt.getDomObj(this._doc, this._allDayCheckboxId);
	this._allAttendeesTable = Dwt.getDomObj(this._doc, this._allAttendeeSlot.dwtTableId); 
};

ZmSchedTabViewPage.prototype._initAutocomplete = 
function() {
	if (this._autocomplete || !this._appCtxt.get(ZmSetting.CONTACTS_ENABLED))
		return;

	var shell = this._appCtxt.getShell();
	var contactsApp = shell ? shell.getData(ZmAppCtxt.LABEL).getApp(ZmZimbraMail.CONTACTS_APP) : null;
	var contactsList = contactsApp ? contactsApp.getContactList : null;
	var locCallback = new AjxCallback(this, this._getAcListLoc, this);
	var params = {parent: shell, dataClass: contactsApp, dataLoader: contactsList,
				  matchValue: ZmContactList.AC_VALUE_EMAIL, locCallback: locCallback, separator: ""};
	this._autocomplete = new ZmAutocompleteListView(params);
};

ZmSchedTabViewPage.prototype._addEventHandlers = 
function() {
	var svpId = AjxCore.assignId(this);

	Dwt.setHandler(this._allDayCheckbox, DwtEvent.ONCLICK, ZmSchedTabViewPage._onClick);
	this._allDayCheckbox._schedViewPageId = svpId;

	// add onchange handlers to the start/end date fields
	Dwt.setHandler(this._startDateField, DwtEvent.ONCHANGE, ZmSchedTabViewPage._onChange);
	Dwt.setHandler(this._endDateField, DwtEvent.ONCHANGE, ZmSchedTabViewPage._onChange);
	this._startDateField._schedViewPageId = this._endDateField._schedViewPageId = svpId;

	for (var i = 0; i < this._schedTable.length; i++) {
		var attendeeDiv = Dwt.getDomObj(this._doc, this._schedTable[i].dwtDivId);
		if (attendeeDiv) {
			Dwt.setHandler(attendeeDiv, DwtEvent.ONCLICK, ZmSchedTabViewPage._onClick);
			attendeeDiv._schedViewPageId = svpId;
		}
		var attendeeInput = Dwt.getDomObj(this._doc, this._schedTable[i].dwtInputId);
		if (attendeeInput) {
			Dwt.setHandler(attendeeInput, DwtEvent.ONCLICK, ZmSchedTabViewPage._onClick);
			Dwt.setHandler(attendeeInput, DwtEvent.ONBLUR, ZmSchedTabViewPage._onBlur);
			attendeeInput._schedViewPageId = svpId;
			attendeeInput._schedTableIdx = i;
			// while we're at it, add auto complete if supported
			if (this._autocomplete)
				this._autocomplete.handle(attendeeInput);
		}
	}
};

ZmSchedTabViewPage.prototype._showTimeFields = 
function(show) {
	Dwt.setVisibility(this._startTimeSelect.getHtmlElement(), show);
	Dwt.setVisibility(this._endTimeSelect.getHtmlElement(), show);
	if (this._supportTimeZones)
		Dwt.setVisibility(this._endTZoneSelect.getHtmlElement(), show);
	// also show/hide the "@" text
	Dwt.setVisibility(this._startTimeSelect.getHtmlElement().parentNode.previousSibling, show);
	Dwt.setVisibility(this._endTimeSelect.getHtmlElement().parentNode.previousSibling, show);
};

ZmSchedTabViewPage.prototype._showAttendeeField =
function(el) {
	if (el.tagName.toLowerCase() == "div") {
		Dwt.setVisible(el.firstChild, true);
		el.firstChild.focus();
	}
};

ZmSchedTabViewPage.prototype._handleAttendeeField = 
function(inputEl) {
	var email = AjxStringUtil.trim(inputEl.value);
	var sched = this._schedTable[inputEl._schedTableIdx];

	if (email && email.length > 0) {
		this._attendees[email] = inputEl._schedTableIdx;
		// go get this attendee's free/busy info if we havent already
		if (sched.uid != email)
			this._updateAttendeesField = true;
			this._controller.getFreeBusyInfo(this._getStartTime(), this._getEndTime(), email, this._fbCallback);
	} else {
		this._cleanRow(inputEl, sched);
		this._colorAllAttendees();
		this._updateAttendees();
	}
};

ZmSchedTabViewPage.prototype._getStartTime = 
function() {
	var sd = this._startDateField.value;
	if (!this._allDayCheckbox.checked)
		sd += " 12:00 AM";
	return ((new Date(sd)).getTime());
};

ZmSchedTabViewPage.prototype._getEndTime = 
function() {
	// XXX: always get start date field value since we dont support multiday yet
	//var ed = this._endDateField.value;
	var ed = this._startDateField.value;
	if (!this._allDayCheckbox.checked)
		ed += " 11:59 PM";
	return ((new Date(ed)).getTime());
};

ZmSchedTabViewPage.prototype._colorAllAttendees =
function() {
	var row = this._allAttendeesTable.rows[0];

	for (var i = 0; i < this._allAttendees.length; i++) {
		if (this._allAttendees[i] > 0) {
			// TODO: opacity...
			row.cells[i].style.backgroundColor = this._getColorForStatus(ZmSchedTabViewPage.STATUS_BUSY);
			this._allAttendeeSlot._coloredCells.push(row.cells[i]);
		}
	}
};

ZmSchedTabViewPage.prototype._updateAttendees = 
function() {
	// get all the emails and update the appointment view
	// XXX: optimize later!!
	var attendeeArr = new Array();
	// always skip the first attendee since that should be the organizer
	for (var i = 1; i < this._schedTable.length; i++) {
		var inputEl = Dwt.getDomObj(this._doc, this._schedTable[i].dwtInputId);
		if (inputEl && inputEl.value.length)
			attendeeArr.push(inputEl.value);
	}
	if (attendeeArr.length) {
		this._origAttendees = attendeeArr.join("; ");
		this._apptTab.updateAttendeesField(this._origAttendees);
	}

	this._updateAttendeesField = false;
};

ZmSchedTabViewPage.prototype._updateFreeBusy = 
function() {
	// update the full date field
	this._resetFullDateField();

	// clear the schedules for existing attendees
	var uids = new Array();
	for (var i = 0; i < this._schedTable.length; i++) {
		var sched = this._schedTable[i];
		if (sched.uid)
			uids.push(sched.uid);
	}

	this._resetAttendeeCount();

	if (uids.length) {
		var emails = uids.join(",");
		this._controller.getFreeBusyInfo(this._getStartTime(), this._getEndTime(), emails, this._fbCallback);
	}
};

ZmSchedTabViewPage.prototype._setAttendees = 
function(attendees) {
	// XXX: optimize later - currently we always update the f/b view :(
	if (this._origAttendees != null)
		this.cleanup();
	this._origAttendees = attendees;

	var attendeeArr = attendees.split(ZmAppt.ATTENDEES_SEPARATOR_REGEX);
	var newAttendeeArr = new Array();
	for (var i = 0; i < attendeeArr.length; i++) {
		var attendee = AjxStringUtil.trim(attendeeArr[i]);
		if (attendee && attendee.length > 0) {
			var inputEl = Dwt.getDomObj(this._doc, this._schedTable[i].dwtInputId);
			if (inputEl) {
				Dwt.setVisible(inputEl, true);
				inputEl.value = attendee;
			}
			this._attendees[attendee] = i;
			newAttendeeArr.push(attendee);

			// always disable the first row since its the organizer
			if (i == 0)
				inputEl.disabled = true;
		}
	}

	if (newAttendeeArr.length > 0) {
		var emails = newAttendeeArr.join(",");
		this._controller.getFreeBusyInfo(this._getStartTime(), this._getEndTime(), emails, this._fbCallback);
	}
};

ZmSchedTabViewPage.prototype._cleanRow = 
function(inputEl, sched) {
	// clear input element value and make invisible
	if (inputEl) {
		inputEl.value = "";
		if (Dwt.getVisible(inputEl))
			Dwt.setVisible(inputEl, false);
	}

	// reset the row color to non-white
	var table = Dwt.getDomObj(this.getDocument(), sched.dwtTableId);
	if (table)
		table.rows[0].style.backgroundColor = "#F4F4F4";

	// remove the bgcolor from the cells that were colored
	this._clearColoredCells(sched);

	sched.uid = null;
};

ZmSchedTabViewPage.prototype._clearColoredCells = 
function(sched) {
	while (sched._coloredCells.length > 0) {
		// decrement cell count in all attendees row
		var idx = sched._coloredCells[0].cellIndex;
		if (this._allAttendees[idx] > 0)
			this._allAttendees[idx] = this._allAttendees[idx] - 1;

		sched._coloredCells[0].style.backgroundColor = "";
		sched._coloredCells.shift();
	}

	var allAttColors = this._allAttendeeSlot._coloredCells;
	while (allAttColors.length > 0) {
		allAttColors[0].style.backgroundColor = "";
		allAttColors.shift();
	}
};

ZmSchedTabViewPage.prototype._resetAttendeeCount = 
function() {
	for (var i = 0; i < this._allAttendees.length; i++)
		this._allAttendees[i] = 0;
};

ZmSchedTabViewPage.prototype._resetFullDateField =
function() {
	var formatter = AjxDateFormat.getDateInstance(AjxDateFormat.LONG);
	this._navToolbar.setText(formatter.format(new Date(this._startDateField.value)));
};

ZmSchedTabViewPage.prototype._handleDateChange = 
function(isStartDate, skipCheck) {
	var needsUpdate = ZmApptViewHelper.handleDateChange(this._startDateField, this._endDateField, isStartDate, skipCheck);
	if (needsUpdate)
		this._updateFreeBusy();
	// finally, update the appt tab view page w/ new date(s)
	this._apptTab.updateDateField(this._startDateField.value, this._endDateField.value);
};

ZmSchedTabViewPage.prototype._getAcListLoc =
function(ev) {
	var inputEl = ev[1].element;
	if (inputEl) {
		var loc = Dwt.getLocation(inputEl);
		var height = Dwt.getSize(inputEl).y;
		return (new DwtPoint(loc.x, loc.y+height));
	}
	return null;
};


// Listeners

// XXX: refactor this code since ZmApptTabViewPage uses similar?
ZmSchedTabViewPage.prototype._dateButtonListener = 
function(ev) {
	// init new DwtCalendar if not already created
	if (!this._dateCalendar) {
		var dateSelListener = new AjxListener(this, this._dateCalSelectionListener);
		var fdow = this._appCtxt.get(ZmSetting.CAL_FIRST_DAY_OF_WEEK) || 0;
		this._dateCalendar = ZmApptViewHelper.createMiniCal(this.shell, dateSelListener, fdow);
	} else {
		// only toggle display if user clicked on same button calendar is being shown for
		if (this._dateCalendar._currButton == ev.dwtObj)
			this._dateCalendar.setVisible(!this._dateCalendar.getVisible());
		else
			this._dateCalendar.setVisible(true);
	}
	// reparent calendar based on which button was clicked
	var buttonLoc = ev.dwtObj.getLocation();
	this._dateCalendar.setLocation(buttonLoc.x, buttonLoc.y+22);
	this._dateCalendar._currButton = ev.dwtObj;

	var calDate = this._dateCalendar._currButton == this._startDateButton
		? new Date(this._startDateField.value)
		: new Date(this._endDateField.value);

	// if date was input by user and its foobar, reset to today's date
	if (isNaN(calDate)) {
		calDate = new Date();
		var field = this._dateCalendar._currButton == this._startDateButton
			? this._startDateField : this._endDateField;
		field.value = AjxDateUtil.simpleComputeDateStr(calDate);
	}

	// always reset the date to current field's date
	this._dateCalendar.setDate(calDate, true);
};

// XXX: refactor this code since ZmApptTabViewPage uses similar?
ZmSchedTabViewPage.prototype._dateCalSelectionListener = 
function(ev) {
	var parentButton = this._dateCalendar._currButton;
	// update the appropriate field w/ the chosen date
	var field = parentButton == this._startDateButton
		? this._startDateField : this._endDateField;
	field.value = AjxDateUtil.simpleComputeDateStr(ev.detail);

	// change the start/end date if they mismatch
	this._handleDateChange(parentButton == this._startDateButton, true);

	this._dateCalendar.setVisible(false);
};

ZmSchedTabViewPage.prototype._navBarListener = 
function(ev) {
	var op = ev.item.getData(ZmOperation.KEY_ID);

	var sd = new Date(this._startDateField.value);
	var ed = new Date(this._endDateField.value);

	var newSd = op == ZmOperation.PAGE_BACK ? sd.getDate()-1 : sd.getDate()+1;
	var newEd = op == ZmOperation.PAGE_BACK ? ed.getDate()-1 : ed.getDate()+1;

	sd.setDate(newSd);
	ed.setDate(newEd);

	this._startDateField.value = AjxDateUtil.simpleComputeDateStr(sd);
	this._endDateField.value = AjxDateUtil.simpleComputeDateStr(ed);

	this._updateFreeBusy();

	// finally, update the appt tab view page w/ new date(s)
	this._apptTab.updateDateField(this._startDateField.value, this._endDateField.value);
};

// XXX: refactor this code since ZmApptTabViewPage uses similar?
ZmSchedTabViewPage.prototype._timeChangeListener =
function(ev) {
	var selectedObj = ev._args.selectObj;

	var sd = new Date(this._startDateField.value);
	var ed = new Date(this._endDateField.value);

	// only attempt to correct the times if dates are equal (otherwise all bets are off)
	if (sd.valueOf() == ed.valueOf()) {
		var numOptions = this._startTimeSelect.size();

		if (selectedObj == this._startTimeSelect) {
			var startIdx = this._startTimeSelect.getIndexForValue(selectedObj.getValue());
			var endIdx = this._endTimeSelect.getIndexForValue(this._endTimeSelect.getValue());
			if (endIdx <= startIdx) {
				var newIdx = startIdx+1;
				if (newIdx == numOptions) {
					newIdx = 0;
					var ed = new Date(this._endDateField.value);
					ed.setDate(ed.getDate()+1);
					this._endDateField.value = AjxDateUtil.simpleComputeDateStr(ed);
				}
				var newIdx = ((startIdx+1) == numOptions) ? 0 : (startIdx+1);
				this._endTimeSelect.setSelected(newIdx);
			}
		} else {
			var startIdx = this._startTimeSelect.getIndexForValue(this._startTimeSelect.getValue());
			var endIdx = this._endTimeSelect.getIndexForValue(selectedObj.getValue());
			if (startIdx > endIdx) {
				var newIdx = endIdx == 0 ? numOptions-1 : endIdx-1;
				this._startTimeSelect.setSelected(newIdx);
				if (newIdx == (numOptions-1)) {
					var sd = new Date(this._startDateField.value);
					sd.setDate(sd.getDate()-1);
					this._startDateField.value = AjxDateUtil.simpleComputeDateStr(sd);
				}
			}
		}
	}

	// always update the appt tab view's time fields
	this._apptTab.updateTimeField(this._startTimeSelect.getValue(), this._endTimeSelect.getValue());
};

ZmSchedTabViewPage.prototype._colorSchedule = 
function(status, slots, table, sched) {
	var row = table.rows[0];
	var bgcolor = this._getColorForStatus(status);

	if (row && bgcolor) {
		// figure out the table cell that needs to be colored
		for (var i = 0; i < slots.length; i++) {
			var sd = new Date(slots[i].s);
			var ed = new Date(slots[i].e);
			var startIdx = sd.getHours() * 2;
			if (sd.getMinutes() == "30")
				startIdx++;
			var endIdx = ed.getHours() * 2;
			if (ed.getMinutes() == "30" || ed.getMinutes() == "59")
				endIdx++;

			for (j = startIdx; j <= endIdx; j++) {
				if (row.cells[j]) {
					if (status != ZmSchedTabViewPage.STATUS_UNKNOWN)
						this._allAttendees[j] = this._allAttendees[j]+1;
					sched._coloredCells.push(row.cells[j]);
					row.cells[j].style.backgroundColor = bgcolor;
				}
			}
		}

		this._colorAllAttendees();
	}
};

ZmSchedTabViewPage.prototype._getColorForStatus = 
function(status) {
	var bgcolor = null;
	switch (status) {
		case ZmSchedTabViewPage.STATUS_FREE: 		bgcolor = "#FFFFFF"; break;
		case ZmSchedTabViewPage.STATUS_BUSY: 		bgcolor = "#990000"; break;
		case ZmSchedTabViewPage.STATUS_TENTATIVE:	bgcolor = "#FF3300"; break;
		case ZmSchedTabViewPage.STATUS_OUT: 		bgcolor = "#FFCC00"; break;
		case ZmSchedTabViewPage.STATUS_UNKNOWN: 	bgcolor = "#FFF5CC"; break;
	}
	return bgcolor;
};


// Callbacks

ZmSchedTabViewPage.prototype._handleResponseFreeBusy =
function(resp) {
	var args = resp.getResponse().GetFreeBusyResponse.usr;

	for (var i = 0; i < args.length; i++) {
		var usr = args[i];

		// first clear out the whole row for this email id
		var sched = this._schedTable[this._attendees[usr.id]];
		var table = sched ? Dwt.getDomObj(this._doc, sched.dwtTableId) : null;
		if (table) {
			table.rows[0].style.backgroundColor = "#FFFFFF";

			this._clearColoredCells(sched);
			sched.uid = usr.id;

			// next, for each free/busy status, color the row for given start/end times
			if (usr.n)
				this._colorSchedule(ZmSchedTabViewPage.STATUS_UNKNOWN, usr.n, table, sched);
			if (usr.t)
				this._colorSchedule(ZmSchedTabViewPage.STATUS_TENTATIVE, usr.t, table, sched);
			if (usr.b)
				this._colorSchedule(ZmSchedTabViewPage.STATUS_BUSY, usr.b, table, sched);
			if (usr.u)
				this._colorSchedule(ZmSchedTabViewPage.STATUS_OUT, usr.u, table, sched);

			// if all we got back was free and nothing else, repaint all attendees row
			if (usr.f && usr.n == null && usr.t == null && usr.b == null && usr.u == null)
				this._colorAllAttendees();
		}
	}

	if (this._updateAttendeesField)
		this._updateAttendees();
};


// Static methods

ZmSchedTabViewPage._onClick = 
function(ev) {
	var el = DwtUiEvent.getTarget(ev);
	var svp = AjxCore.objectWithId(el._schedViewPageId);

	// figure out which object was clicked
	if (el.id == svp._allDayCheckboxId) {
		svp._showTimeFields(el.checked ? false : true);
		svp._apptTab.updateAllDayField(el.checked);
	} else {
		// looks like user clicked on attendee field
		svp._showAttendeeField(el);
	}
};

ZmSchedTabViewPage._onBlur = 
function(ev) {
	var el = DwtUiEvent.getTarget(ev);
	var svp = AjxCore.objectWithId(el._schedViewPageId);

	svp._handleAttendeeField(el);
};

ZmSchedTabViewPage._onChange = 
function(ev) {
	var el = DwtUiEvent.getTarget(ev);
	var svp = AjxCore.objectWithId(el._schedViewPageId);

	svp._handleDateChange(el == svp._startDateField);
};
