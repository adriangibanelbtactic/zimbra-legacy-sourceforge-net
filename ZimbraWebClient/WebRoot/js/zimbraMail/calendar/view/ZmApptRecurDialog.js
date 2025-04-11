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
* Creates a new appointment recurrence dialog. The view displays itself on construction.
* @constructor
* @class
* This class provides a dialog for creating/editing recurrences for an appointment
*
* @author Parag Shah
* @param parent			the element that created this view
* @param appCtxt 		the singleton app context
* @param className 		optional class name for this view
*/
ZmApptRecurDialog = function(parent, appCtxt, className) {

	DwtDialog.call(this, parent, className, ZmMsg.customRepeat);
	this._appCtxt = appCtxt;
	// set html content once (hence, in ctor)
	this.setContent(this._setHtml());
	this._createRepeatSections();
	this._createDwtObjects();
	this._cacheFields();
	this._addEventHandlers();

	this.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(this, this._okListener));
	this.addSelectionListener(DwtDialog.CANCEL_BUTTON, new AjxListener(this, this._cancelListener));
};

ZmApptRecurDialog.prototype = new DwtDialog;
ZmApptRecurDialog.prototype.constructor = ZmApptRecurDialog;


// Consts

ZmApptRecurDialog.REPEAT_OPTIONS = [
	{ label: ZmMsg.none, 			value: "NON", 	selected: true 	},
	{ label: ZmMsg.daily, 			value: "DAI", 	selected: false },
	{ label: ZmMsg.weekly, 			value: "WEE", 	selected: false },
	{ label: ZmMsg.monthly, 		value: "MON", 	selected: false },
	{ label: ZmMsg.yearly, 			value: "YEA", 	selected: false }];


// Public methods

ZmApptRecurDialog.prototype.toString = 
function() {
	return "ZmApptRecurDialog";
};

ZmApptRecurDialog.prototype.initialize = 
function(startDate, endDate, repeatType, appt) {
	this._startDate = new Date(startDate.getTime());
	this._endDate = new Date(endDate.getTime());
	
	// based on repeat type, setup the repeat type values
	var repeatType = repeatType || "DAI";
	this._repeatSelect.setSelectedValue(repeatType);
	this._setRepeatSection(repeatType);

	// dont bother initializing if user is still mucking around
	if (this._saveState)
		return;

	var startDay = this._startDate.getDay();
	var startDate = this._startDate.getDate();
	var startMonth = this._startDate.getMonth();

	// reset time based fields
	this._endByField.setValue(AjxDateUtil.simpleComputeDateStr(this._startDate));
	this._weeklySelect.setSelected(startDay);
	this._weeklyCheckboxes[startDay].checked = true;
	this._monthlyDayField.setValue(startDate);
	this._monthlyWeekdaySelect.setSelected(startDay);
	this._yearlyDayField.setValue(startDate);
	this._yearlyMonthSelect.setSelected(startMonth);
	this._yearlyWeekdaySelect.setSelected(startDay);
	this._yearlyMonthSelectEx.setSelected(startMonth);

	// if given appt object, means user is editing existing appointment's recur rules
	if (appt) {
		this._populateForEdit(appt);
	}
};

ZmApptRecurDialog.prototype.getSelectedRepeatValue = 
function() {
	return this._repeatSelect.getValue();
};

ZmApptRecurDialog.prototype.setRepeatEndValues = 
function(appt) {
	var recur = appt._recurrence;
	recur.repeatEndType = this._getRadioOptionValue(this._repeatEndName);

	// add any details for the select option
	if (recur.repeatEndType == "A")
		recur.repeatEndCount = this._endIntervalField.getValue();
	else if (recur.repeatEndType == "D")
		recur.repeatEndDate = AjxDateUtil.simpleParseDateStr(this._endByField.getValue());
};

ZmApptRecurDialog.prototype.setCustomDailyValues = 
function(appt) {
	var recur = appt._recurrence;
	var value = this._getRadioOptionValue(this._dailyRadioName);

	if (value == "2") {
		recur.repeatCustom = "1";
		recur.repeatWeekday = true;
	} else {
		recur.repeatCustomCount = value == "3" ? (Number(this._dailyField.getValue())) : 1;
	}
};

ZmApptRecurDialog.prototype.setCustomWeeklyValues = 
function(appt) {
	var recur = appt._recurrence;
	recur.repeatWeeklyDays = []
	recur.repeatCustom = "1";

	var value = this._getRadioOptionValue(this._weeklyRadioName);
	
	if (value == "1") {
		recur.repeatCustomCount = 1;
		recur.repeatWeeklyDays.push(ZmCalItem.SERVER_WEEK_DAYS[this._weeklySelect.getValue()]);
	} else {
		recur.repeatCustomCount = Number(this._weeklyField.getValue());
		for (var i = 0; i < this._weeklyCheckboxes.length; i++) {
			if (this._weeklyCheckboxes[i].checked)
				recur.repeatWeeklyDays.push(ZmCalItem.SERVER_WEEK_DAYS[i]);
		}
	}
};

ZmApptRecurDialog.prototype.setCustomMonthlyValues = 
function(appt) {
	var recur = appt._recurrence;
	recur.repeatCustom = "1";

	var value = this._getRadioOptionValue(this._monthlyRadioName);
	
	if (value == "1") {
		recur.repeatCustomType = "S";
		recur.repeatCustomCount = this._monthlyMonthField.getValue();
		recur.repeatMonthlyDayList = [this._monthlyDayField.getValue()];
	} else {
		recur.repeatCustomType = "O";
		recur.repeatCustomCount = this._monthlyMonthFieldEx.getValue();
		recur.repeatCustomOrdinal = this._monthlyDaySelect.getValue();
		recur.repeatCustomDayOfWeek = ZmCalItem.SERVER_WEEK_DAYS[this._monthlyWeekdaySelect.getValue()];
	}
};

ZmApptRecurDialog.prototype.setCustomYearlyValues = 
function(appt) {
	appt._recurrence.repeatCustom = "1";

	var value = this._getRadioOptionValue(this._yearlyRadioName);

	if (value == "1") {
		appt._recurrence.repeatCustomType = "S";
		appt._recurrence.repeatCustomMonthDay = this._yearlyDayField.getValue();
		appt._recurrence.repeatYearlyMonthsList = this._yearlyMonthSelect.getValue() + 1;
	} else {
		appt._recurrence.repeatCustomType = "O";
		appt._recurrence.repeatCustomOrdinal = this._yearlyDaySelect.getValue();
		appt._recurrence.repeatCustomDayOfWeek = ZmCalItem.SERVER_WEEK_DAYS[this._yearlyWeekdaySelect.getValue()];
		appt._recurrence.repeatYearlyMonthsList = this._yearlyMonthSelectEx.getValue() + 1;
	}
};

ZmApptRecurDialog.prototype.addSelectionListener = 
function(buttonId, listener) {
	this._button[buttonId].addSelectionListener(listener);
};

ZmApptRecurDialog.prototype.clearState = 
function() {
	this._saveState = false;
	this._cleanup();
};

ZmApptRecurDialog.prototype.isValid = 
function() {
	var valid = true;

	// ONLY for the selected options, check if their fields are valid
	var repeatValue = this._repeatSelect.getValue();

	if (repeatValue == "DAI") {
		if (this._dailyFieldRadio.checked)
			valid = this._dailyField.isValid();
		if (!valid)
			this._dailyField.blur();
	} else if (repeatValue == "WEE") {
		if (this._weeklyFieldRadio.checked) {
			valid = this._weeklyField.isValid();
			if (valid) {
				valid = false;
				for (var i=0; i<this._weeklyCheckboxes.length; i++) {
					if (this._weeklyCheckboxes[i].checked) {
						valid = true;
						break;
					}
				}
			}
			// weekly section is special - force a focus if valid to clear out error
			this._weeklyField.focus();
			this._weeklyField.blur();
		}
	} else if (repeatValue == "MON") {
		if (this._monthlyDefaultRadio.checked) {
			valid = this._monthlyMonthField.isValid() && this._monthlyDayField.isValid();
			if (!valid) {
				this._monthlyMonthField.blur();
				this._monthlyDayField.blur();
			}
		} else {
			valid = this._monthlyMonthFieldEx.isValid();
			if (!valid)
				this._monthlyMonthFieldEx.blur();
		}
	} else if (repeatValue == "YEA") {
		if (this._yearlyDefaultRadio.checked)
			valid = this._yearlyDayField.isValid();
		if (!valid)
			this._yearlyDayField.blur();
	}

	// check end section
	if (valid) {
		if (this._endAfterRadio.checked) {
			valid = this._endIntervalField.isValid();
			if (!valid)
				this._endIntervalField.blur();
		} else if (this._endByRadio.checked) {
			valid = this._endByField.isValid();
			if (!valid)
				this._endByField.blur();
		}
	}

	return valid;
};


// Private / protected methods
 
ZmApptRecurDialog.prototype._setHtml = 
function() {
	this._repeatSelectId = Dwt.getNextId();
	this._repeatSectionId = Dwt.getNextId();
	this._repeatEndDivId = Dwt.getNextId();

	var html = new Array();
	var i = 0;
	
	html[i++] = "<table border=0 cellpadding=2 cellspacing=2 width=450>";
	html[i++] = "<tr><td><fieldset";
	if (AjxEnv.isMozilla)
		html[i++] = " style='border:1px dotted #555555'";
	html[i++] = "><legend style='color:#555555'>";
	html[i++] = ZmMsg.repeat;
	html[i++] = "</legend><div style='height:100px'>";
	html[i++] = "<div id='";
	html[i++] = this._repeatSelectId;
	html[i++] = "'></div><div id='";
	html[i++] = this._repeatSectionId;
	html[i++] = "'></div>";
	html[i++] = "</div></fieldset></td></tr>";
	html[i++] = "<tr><td><div id='";
	html[i++] = this._repeatEndDivId;
	html[i++] = "'><fieldset";
	if (AjxEnv.isMozilla)
		html[i++] = " style='border:1px dotted #555555'";
	html[i++] = "><legend style='color:#555555'>";
	html[i++] = ZmMsg.end;
	html[i++] = "</legend>";
	html[i++] = this._getEndHtml();
	html[i++] = "</fieldset></div></td></tr>";
	html[i++] = "</table>";
	
	return html.join("");
};

ZmApptRecurDialog.prototype._getEndHtml = 
function() {
	this._repeatEndName = Dwt.getNextId();
	this._noEndDateRadioId = Dwt.getNextId();
	this._endByRadioId = Dwt.getNextId();
	this._endAfterRadioId = Dwt.getNextId();
	this._endIntervalFieldId = Dwt.getNextId();
	this._endByFieldId = Dwt.getNextId();
	this._endByButtonId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	// start table
	html[i++] = "<table border=0>";
	// no end date
	html[i++] = "<tr><td width=1%><input checked value='N' type='radio' name='";
	html[i++] = this._repeatEndName;
	html[i++] = "' id='";
	html[i++] = this._noEndDateRadioId;
	html[i++] = "'></td><td colspan=2>";
	html[i++] = ZmMsg.recurEndNone;
	html[i++] = "</td></tr>";
	// end after <num> occurrences
	html[i++] = "<tr><td><input type='radio' value='A' name='";
	html[i++] = this._repeatEndName;
	html[i++] = "' id='";
	html[i++] = this._endAfterRadioId;
	html[i++] = "'></td><td colspan=2><nobr>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='2'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurEndNumber);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		html[i++] = "<td>";
		var segment = segments[s];
		if (segment instanceof AjxMessageFormat.MessageSegment && 
			segment.getIndex() == 0) {
			html[i++] = "<span id='";
			html[i++] = this._endIntervalFieldId;
			html[i++] = "'></span>";
		}
		else {
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end by <date>
	html[i++] = "<tr><td><input type='radio' value='D' name='";
	html[i++] = this._repeatEndName;
	html[i++] = "' id='";
	html[i++] = this._endByRadioId;
	html[i++] = "'></td><td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='0'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurEndByDate);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		if (segment instanceof AjxMessageFormat.MessageSegment && 
			segment.getIndex() == 0) {
			html[i++] = "<td id='";
			html[i++] = this._endByFieldId;
			html[i++] = "'></td><td id='";
			html[i++] = this._endByButtonId;
			html[i++] = "'></td>";
		}
		else {
			html[i++] = "<td style='padding-left:2px;padding-right:2px'>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end table
	html[i++] = "</table>";

	return html.join("");
};

ZmApptRecurDialog.prototype._createRepeatSections = 
function() {
	var sectionDiv = document.getElementById(this._repeatSectionId);
	if (sectionDiv) {
		var div = document.createElement("div");
		div.style.position = "relative";
		div.style.display = "none";
		div.id = this._repeatDailyId = Dwt.getNextId();
		div.innerHTML = this._createRepeatDaily();
		sectionDiv.appendChild(div);
		
		var div = document.createElement("div");
		div.style.position = "relative";
		div.style.display = "none";
		div.id = this._repeatWeeklyId = Dwt.getNextId();
		div.innerHTML = this._createRepeatWeekly();;
		sectionDiv.appendChild(div);
	
		var div = document.createElement("div");
		div.style.position = "relative";
		div.style.display = "none";
		div.id = this._repeatMonthlyId = Dwt.getNextId();
		div.innerHTML = this._createRepeatMonthly();
		sectionDiv.appendChild(div);
	
		var div = document.createElement("div");
		div.style.position = "relative";
		div.style.display = "none";
		div.id = this._repeatYearlyId = Dwt.getNextId();
		div.innerHTML = this._createRepeatYearly();
		sectionDiv.appendChild(div);
	}
};

ZmApptRecurDialog.prototype._createRepeatDaily = 
function() {
	this._dailyRadioName = Dwt.getNextId();
	this._dailyDefaultId = Dwt.getNextId();
	this._dailyFieldRadioId = Dwt.getNextId();
	this._dailyFieldId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	// start table
	html[i++] = "<table border=0>";
	// every day
	html[i++] = "<tr><td><input checked value='1' type='radio' name='";
	html[i++] = this._dailyRadioName;
	html[i++] = "' id='";
	html[i++] = this._dailyDefaultId;
	html[i++] = "'></td>";
	html[i++] = "<td>";
	html[i++] = ZmMsg.recurDailyEveryDay;
	html[i++] = "</td></tr>";
	// every weekday
	html[i++] = "<tr><td><input value='2' type='radio' name='";
	html[i++] = this._dailyRadioName;
	html[i++] = "'></td>";
	html[i++] = "<td>";
	html[i++] = ZmMsg.recurDailyEveryWeekday;
	html[i++] = "</td></tr>";
	// every <num> days
	html[i++] = "<tr><td><input value='3' type='radio' name='";
	html[i++] = this._dailyRadioName;
	html[i++] = "' id='";
	html[i++] = this._dailyFieldRadioId;
	html[i++] = "'></td><td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurDailyEveryNumDays);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		html[i++] = "<td>";
		var segment = segments[s];
		if (segment instanceof AjxMessageFormat.MessageSegment &&
			segment.getIndex() == 0) {
			html[i++] = "<span id='";
			html[i++] = this._dailyFieldId;
			html[i++] = "'></span>";
		}
		else {
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end table
	html[i++] = "</table>";

	return html.join("");
};

ZmApptRecurDialog.prototype._createRepeatWeekly = 
function() {
	this._weeklyRadioName = Dwt.getNextId();
	this._weeklyCheckboxName = Dwt.getNextId();
	this._weeklyDefaultId = Dwt.getNextId();
	this._weeklySelectId = Dwt.getNextId();
	this._weeklyFieldRadioId = Dwt.getNextId();
	this._weeklyFieldId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	// start table
	html[i++] = "<table border=0>";
	// every <weekday>
	html[i++] = "<tr><td><input checked value='1' type='radio' name='";
	html[i++] = this._weeklyRadioName;
	html[i++] = "' id='";
	html[i++] = this._weeklyDefaultId;
	html[i++] = "'></td><td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurWeeklyEveryWeekday);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<td id='";
			html[i++] = this._weeklySelectId;
			html[i++] = "'>";
		}
		else {
			html[i++] = "<td>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// every <num> weeks on <days of week>
	html[i++] = "<tr valign='top'><td><input value='2' type='radio' name='";
	html[i++] = this._weeklyRadioName;
	html[i++] = "' id='";
	html[i++] = this._weeklyFieldRadioId;
	html[i++] = "'></td>";
	html[i++] = "<td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurWeeklyEveryNumWeeksDate);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<td id='";
			html[i++] = this._weeklyFieldId;
			html[i++] = "'>";
		}
		else if (index == 1) {
			html[i++] = "<td>";
			html[i++] = "<table border=0 cellpadding=0 cellspacing=0><tr>";
			for (var j = 0; j < AjxDateUtil.WEEKDAY_MEDIUM.length; j++) {
				if (j > 0) {
					html[i++] = "<td>&nbsp;&nbsp;</td>";
				}
				html[i++] = "<td><input type='checkbox' name='";
				html[i++] = this._weeklyCheckboxName;
				html[i++] = "'></td><td>";
				html[i++] = AjxDateUtil.WEEKDAY_MEDIUM[j];
				html[i++] = "</td>";
			}
			html[i++] = "</tr></table>";
		}
		else if (index == 2) {
			html[i++] = "</td></tr></table>";
			html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
			continue;
		}
		else {
			html[i++] = "<td>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end table
	html[i++] = "</table>";

	return html.join("");
};

ZmApptRecurDialog.prototype._createRepeatMonthly = 
function() {
	this._monthlyRadioName = Dwt.getNextId();
	this._monthlyDefaultId = Dwt.getNextId();
	this._monthlyDayFieldId = Dwt.getNextId();
	this._monthlyMonthFieldId = Dwt.getNextId();
	this._monthlyFieldRadioId = Dwt.getNextId();
	this._monthlyDaySelectId = Dwt.getNextId();
	this._monthlyWeekdaySelectId = Dwt.getNextId();
	this._monthlyMonthFieldExId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	// start table
	html[i++] = "<table border=0>";
	// every <num> months on the <day>
	html[i++] = "<tr><td><input checked value='1' type='radio' name='";
	html[i++] = this._monthlyRadioName;
	html[i++] = "' id='";
	html[i++] = this._monthlyDefaultId;
	html[i++] = "'></td>";
	html[i++] = "<td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurMonthlyEveryNumMonthsDate);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		html[i++] = "<td>";
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<span id='";
			html[i++] = this._monthlyDayFieldId;
			html[i++] = "'></span>";
		}
		else if (index == 1) {
			html[i++] = "<span id='";
			html[i++] = this._monthlyMonthFieldId;
			html[i++] = "'></span>";
		}
		else {
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// every <num> months on the <ordinal> <weekday>
	html[i++] = "<tr><td><input value='2' type='radio' name='";
	html[i++] = this._monthlyRadioName;
	html[i++] = "' id='";
	html[i++] = this._monthlyFieldRadioId;
	html[i++] = "'></td>";
	html[i++] = "<td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurMonthlyEveryNumMonthsNumDay);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<td id='";
			html[i++] = this._monthlyDaySelectId;
			html[i++] = "'>";
		}
		else if (index == 1) {
			html[i++] = "<td id='";
			html[i++] = this._monthlyWeekdaySelectId;
			html[i++] = "'>";
		}
		else if (index == 2) {
			html[i++] = "<td><span id='";
			html[i++] = this._monthlyMonthFieldExId;
			html[i++] = "'></span>";
		}
		else {
			html[i++] = "<td>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end table
	html[i++] = "</table>";

	return html.join("");
};

ZmApptRecurDialog.prototype._createRepeatYearly = 
function() {
	this._yearlyDefaultId = Dwt.getNextId();
	this._yearlyRadioName = Dwt.getNextId();
	this._yearlyMonthSelectId = Dwt.getNextId();
	this._yearlyDayFieldId = Dwt.getNextId();
	this._yearlyDaySelectId = Dwt.getNextId();
	this._yearlyWeekdaySelectId = Dwt.getNextId();
	this._yearlyMonthSelectExId = Dwt.getNextId();
	this._yearlyFieldRadioId = Dwt.getNextId();

	var html = new Array();
	var i = 0;

	// start table
	html[i++] = "<table border=0>";
	// every year on <month> <day>
	html[i++] = "<tr><td><input checked value='1' type='radio' name='";
	html[i++] = this._yearlyRadioName;
	html[i++] = "' id='";
	html[i++] = this._yearlyDefaultId;
	html[i++] = "'></td><td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurYearlyEveryDate);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<td id='";
			html[i++] = this._yearlyMonthSelectId;
			html[i++] = "'>";
		}
		else if (index == 1) {
			html[i++] = "<td><span id='";
			html[i++] = this._yearlyDayFieldId;
			html[i++] = "'></span>";
		}
		else {
			html[i++] = "<td>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// every year on <ordinal> <weekday> of <month>
	html[i++] = "<tr><td><input value='2' type='radio' name='";
	html[i++] = this._yearlyRadioName;
	html[i++] = "' id='";
	html[i++] = this._yearlyFieldRadioId;
	html[i++] = "'></td><td>";
	html[i++] = "<table border='0' cellspacing='0' cellpadding='1'><tr>";
	var formatter = new AjxMessageFormat(ZmMsg.recurYearlyEveryMonthNumDay);
	var segments = formatter.getSegments();
	for (var s = 0; s < segments.length; s++) {
		var segment = segments[s];
		var index = segment instanceof AjxMessageFormat.MessageSegment
				  ? segment.getIndex() : -1;
		if (index == 0) {
			html[i++] = "<td id='";
			html[i++] = this._yearlyDaySelectId;
			html[i++] = "'>";
		}
		else if (index == 1) {
			html[i++] = "<td id='";
			html[i++] = this._yearlyWeekdaySelectId;
			html[i++] = "'>";
		}
		else if (index == 2) {
			html[i++] = "<td id='";
			html[i++] = this._yearlyMonthSelectExId;
			html[i++] = "'>";
		}
		else {
			html[i++] = "<td>";
			html[i++] = segment.toSubPattern();
		}
		html[i++] = "</td>";
	}
	html[i++] = "</tr></table>";
	html[i++] = "</td></tr>";
	// end table
	html[i++] = "</table>";
	
	return html.join("");
};

ZmApptRecurDialog.prototype._createDwtObjects = 
function() {
	// create all DwtSelect's
	this._createSelects();

	// create mini calendar button for end by field
	var dateButtonListener = new AjxListener(this, this._endByButtonListener);
	var dateCalSelectionListener = new AjxListener(this, this._dateCalSelectionListener);
	ZmCalendarApp.createMiniCalButton(this, this._endByButtonId, dateButtonListener, dateCalSelectionListener, this._appCtxt, true);

	// create all DwtInputField's
	this._createInputs();
};

ZmApptRecurDialog.prototype._createSelects = 
function() {
	this._repeatSelect = new DwtSelect(this);
	this._repeatSelect.addChangeListener(new AjxListener(this, this._repeatChangeListener));
	for (var i = 0; i < ZmApptRecurDialog.REPEAT_OPTIONS.length; i++) {
		var option = ZmApptRecurDialog.REPEAT_OPTIONS[i];
		this._repeatSelect.addOption(option.label, option.selected, option.value);
	}
	this._repeatSelect.reparentHtmlElement(this._repeatSelectId);
	delete this._repeatSelectId;

	var selectChangeListener = new AjxListener(this, this._selectChangeListener);
	this._weeklySelect = new DwtSelect(this);
	this._weeklySelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurWeeklyEveryWeekday);
	var dayFormatter = formatter.getFormatsByArgumentIndex()[0];
	var day = new Date();
	day.setDate(day.getDate() - day.getDay());
	for (var i = 0; i < 7; i++) {
		this._weeklySelect.addOption(dayFormatter.format(day), false, i);
		day.setDate(day.getDate() + 1);
	}
	this._weeklySelect.reparentHtmlElement(this._weeklySelectId);
	delete this._weeklySelectId;

	this._monthlyDaySelect = new DwtSelect(this);
	this._monthlyDaySelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurMonthlyEveryNumMonthsNumDay);
	var ordinalFormatter = formatter.getFormatsByArgumentIndex()[0];
	var limits = ordinalFormatter.getLimits();
	var formats = ordinalFormatter.getFormats();
	for (var i = 0; i < limits.length; i++) {
		var index = (i + 1) % limits.length;
		var label = formats[index].format();
		var value = Math.floor(limits[index]);
		this._monthlyDaySelect.addOption(label, false, value);
	}
	this._monthlyDaySelect.reparentHtmlElement(this._monthlyDaySelectId);
	delete this._monthlyDaySelectId;

	this._monthlyWeekdaySelect = new DwtSelect(this);
	this._monthlyWeekdaySelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurMonthlyEveryNumMonthsNumDay);
	var dayFormatter = formatter.getFormatsByArgumentIndex()[1];
	var day = new Date();
	day.setDate(day.getDate() - day.getDay());
	for (var i = 0; i < 7; i++) {
		this._monthlyWeekdaySelect.addOption(dayFormatter.format(day), false, i);
		day.setDate(day.getDate() + 1);
	}
	this._monthlyWeekdaySelect.reparentHtmlElement(this._monthlyWeekdaySelectId);
	delete this._monthlyWeekdaySelectId;

	this._yearlyMonthSelect = new DwtSelect(this);
	this._yearlyMonthSelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurYearlyEveryDate);
	var monthFormatter = formatter.getFormatsByArgumentIndex()[0];
	var month = new Date();
	month.setDate(1);
	for (var i = 0; i < 12; i++) {
		month.setMonth(i);
		this._yearlyMonthSelect.addOption(monthFormatter.format(month), false, i);
	}
	this._yearlyMonthSelect.reparentHtmlElement(this._yearlyMonthSelectId);
	delete this._yearlyMonthSelectId;

	this._yearlyDaySelect = new DwtSelect(this);
	this._yearlyDaySelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurYearlyEveryMonthNumDay);
	var ordinalFormatter = formatter.getFormatsByArgumentIndex()[0];
	var limits = ordinalFormatter.getLimits();
	var formats = ordinalFormatter.getFormats();
	for (var i = 0; i < limits.length; i++) {
		var index = (i + 1) % limits.length;
		var label = formats[index].format();
		var value = Math.floor(limits[index]);
		this._yearlyDaySelect.addOption(label, false, value);
	}
	this._yearlyDaySelect.reparentHtmlElement(this._yearlyDaySelectId);
	delete this._yearlyDaySelectId;

	this._yearlyWeekdaySelect = new DwtSelect(this);
	this._yearlyWeekdaySelect.addChangeListener(selectChangeListener);
	var formatter = new AjxMessageFormat(ZmMsg.recurYearlyEveryMonthNumDay);
	var dayFormatter = formatter.getFormatsByArgumentIndex()[1];
	var day = new Date();
	day.setDate(day.getDate() - day.getDay());
	for (var i = 0; i < 7; i++) {
		this._yearlyWeekdaySelect.addOption(dayFormatter.format(day), false, i);
		day.setDate(day.getDate() + 1);
	}
	this._yearlyWeekdaySelect.reparentHtmlElement(this._yearlyWeekdaySelectId);
	delete this._yearlyWeekdaySelectId;

	this._yearlyMonthSelectEx = new DwtSelect(this);
	this._yearlyMonthSelectEx.addChangeListener(selectChangeListener);
	for (var i = 0; i < AjxDateUtil.MONTH_LONG.length; i++)
		this._yearlyMonthSelectEx.addOption(AjxDateUtil.MONTH_LONG[i], false, i);
	this._yearlyMonthSelectEx.reparentHtmlElement(this._yearlyMonthSelectExId);
	delete this._yearlyMonthSelectExId;
};

ZmApptRecurDialog.prototype._createInputs = 
function() {
	// create inputs for end fields
	this._endIntervalField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
												initialValue: "1", size: 3, maxLen: 3,
												errorIconStyle: DwtInputField.ERROR_ICON_NONE, 
												validationStyle: DwtInputField.ONEXIT_VALIDATION, 
												validator: this._positiveIntValidator, 
												validatorCtxtObj: this});
	this._endIntervalField.setDisplay(Dwt.DISPLAY_INLINE);
	this._endIntervalField.reparentHtmlElement(this._endIntervalFieldId);
	delete this._endIntervalFieldId;

	this._endByField = new DwtInputField({parent: this, type: DwtInputField.DATE,
										  size: 10, maxLen: 10,
										  errorIconStyle: DwtInputField.ERROR_ICON_NONE,
										  validationStyle: DwtInputField.ONEXIT_VALIDATION,
										  validator: this._endByDateValidator, 
										  validatorCtxtObj: this});
	this._endByField.setDisplay(Dwt.DISPLAY_INLINE);
	this._endByField.reparentHtmlElement(this._endByFieldId);
	Dwt.setSize(this._endByField.getInputElement(), Dwt.DEFAULT, "22");
	delete this._endByFieldId;

	// create inputs for day fields
	this._dailyField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
										  initialValue: "1", size: 3, maxLen: 2,
										  errorIconStyle: DwtInputField.ERROR_ICON_NONE,
										  validationStyle: DwtInputField.ONEXIT_VALIDATION,
										  validator: this._positiveIntValidator,
										  validatorCtxtObj: this});
	this._dailyField.setDisplay(Dwt.DISPLAY_INLINE);
	this._dailyField.reparentHtmlElement(this._dailyFieldId);
	delete this._dailyFieldId;

	// create inputs for week fields
	this._weeklyField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
										   initialValue: "2", size: 2, maxLen: 2,
										   errorIconStyle: DwtInputField.ERROR_ICON_NONE,
										   validationStyle: DwtInputField.ONEXIT_VALIDATION,
										   validator: this._weeklyValidator,
										   validatorCtxtObj: this});
	this._weeklyField.setDisplay(Dwt.DISPLAY_INLINE);
	this._weeklyField.reparentHtmlElement(this._weeklyFieldId);
	delete this._weeklyFieldId;

	// create inputs for month fields
	this._monthlyDayField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
											   initialValue: "1", size: 2, maxLen: 2,
											   errorIconStyle: DwtInputField.ERROR_ICON_NONE,
											   validationStyle: DwtInputField.ONEXIT_VALIDATION,
											   validatorCtxtObj: this});
	this._monthlyDayField.setDisplay(Dwt.DISPLAY_INLINE);
	this._monthlyDayField.reparentHtmlElement(this._monthlyDayFieldId);
	this._monthlyDayField.setValidNumberRange(1, 31);
	delete this._monthlyDayFieldId;

	this._monthlyMonthField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
											   initialValue: "1", size: 2, maxLen: 2,
											   errorIconStyle: DwtInputField.ERROR_ICON_NONE,
											   validationStyle: DwtInputField.ONEXIT_VALIDATION,
											   validator: this._positiveIntValidator,
											   validatorCtxtObj: this});
	this._monthlyMonthField.setDisplay(Dwt.DISPLAY_INLINE);
	this._monthlyMonthField.reparentHtmlElement(this._monthlyMonthFieldId);
	delete this._monthlyMonthFieldId;

	this._monthlyMonthFieldEx = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
												   initialValue: "1", size: 2, maxLen: 2,
												   errorIconStyle: DwtInputField.ERROR_ICON_NONE,
												   validationStyle: DwtInputField.ONEXIT_VALIDATION,
												   validator: this._positiveIntValidator,
												   validatorCtxtObj: this});
	this._monthlyMonthFieldEx.setDisplay(Dwt.DISPLAY_INLINE);
	this._monthlyMonthFieldEx.reparentHtmlElement(this._monthlyMonthFieldExId);
	delete this._monthlyMonthFieldExId;

	// create inputs for year fields
	this._yearlyDayField = new DwtInputField({parent: this, type: DwtInputField.INTEGER,
											  initialValue: "1", size: 2, maxLen: 2,
											  errorIconStyle: DwtInputField.ERROR_ICON_NONE,
											  validationStyle: DwtInputField.ONEXIT_VALIDATION,
											  validator: this._yearlyDayValidator,
											  validatorCtxtObj: this});
	this._yearlyDayField.setDisplay(Dwt.DISPLAY_INLINE);
	this._yearlyDayField.reparentHtmlElement(this._yearlyDayFieldId);
	delete this._yearlyDayFieldId;
};

ZmApptRecurDialog.prototype._cacheFields = 
function() {
	this._noEndDateRadio = document.getElementById(this._noEndDateRadioId);			delete this._noEndDateRadioId;
	this._endByRadio = document.getElementById(this._endByRadioId); 				delete this._endByRadioId;
	this._endAfterRadio = document.getElementById(this._endAfterRadioId); 			delete this._endAfterRadioId;
	this._repeatSectionDiv = document.getElementById(this._repeatSectionId); 		delete this._repeatSectionId;
	this._repeatEndDiv = document.getElementById(this._repeatEndDivId);				delete this._repeatEndDivId;
	this._repeatDailyDiv = document.getElementById(this._repeatDailyId); 			delete this._repeatDailyId;
	this._repeatWeeklyDiv = document.getElementById(this._repeatWeeklyId); 			delete this._repeatWeeklyId;
	this._repeatMonthlyDiv = document.getElementById(this._repeatMonthlyId); 		delete this._repeatMonthlyId;
	this._repeatYearlyDiv = document.getElementById(this._repeatYearlyId); 			delete this._repeatYearlyId;
	this._dailyDefaultRadio = document.getElementById(this._dailyDefaultId); 		delete this._dailyDefaultId;
	this._dailyFieldRadio = document.getElementById(this._dailyFieldRadioId); 		delete this._dailyFieldRadioId;
	this._weeklyDefaultRadio = document.getElementById(this._weeklyDefaultId); 		delete this._weeklyDefaultId;
	this._weeklyFieldRadio = document.getElementById(this._weeklyFieldRadioId);		delete this._weeklyFieldRadioId;
	this._weeklyCheckboxes = document.getElementsByName(this._weeklyCheckboxName);
	this._monthlyDefaultRadio = document.getElementById(this._monthlyDefaultId); 	delete this._monthlyDefaultId;
	this._monthlyFieldRadio = document.getElementById(this._monthlyFieldRadioId); 	delete this._monthlyFieldRadioId;
	this._yearlyDefaultRadio = document.getElementById(this._yearlyDefaultId); 		delete this._yearlyDefaultId;
	this._yearlyFieldRadio = document.getElementById(this._yearlyFieldRadioId); 	delete this._yearlyFieldRadioId;
};

ZmApptRecurDialog.prototype._addEventHandlers = 
function() {
	var ardId = AjxCore.assignId(this);

	// add event listeners where necessary
	this._setFocusHandler(this._endIntervalField, ardId);
	this._setFocusHandler(this._endByField, ardId);
	this._setFocusHandler(this._dailyField, ardId);
	this._setFocusHandler(this._weeklyField, ardId);
	this._setFocusHandler(this._monthlyDayField, ardId);
	this._setFocusHandler(this._monthlyMonthField, ardId);
	this._setFocusHandler(this._monthlyMonthFieldEx, ardId);
	this._setFocusHandler(this._yearlyDayField, ardId);
};

ZmApptRecurDialog.prototype._setFocusHandler = 
function(dwtObj, ardId) {
	var inputEl = dwtObj.getInputElement();
	Dwt.setHandler(inputEl, DwtEvent.ONFOCUS, ZmApptRecurDialog._onFocus);
	inputEl._recurDialogId = ardId;
}

ZmApptRecurDialog.prototype._setRepeatSection = 
function(repeatType) {
	Dwt.setVisible(this._repeatSectionDiv, repeatType != "NON");
	Dwt.setVisible(this._repeatEndDiv, repeatType != "NON");

	var newSection = null;
	switch (repeatType) {
		case "DAI": newSection = this._repeatDailyDiv; break;
		case "WEE": newSection = this._repeatWeeklyDiv; break;
		case "MON": newSection = this._repeatMonthlyDiv; break;
		case "YEA": newSection = this._repeatYearlyDiv; break;
	}

	if (newSection) {
		if (this._currentSection)
			Dwt.setVisible(this._currentSection, false);
		Dwt.setVisible(newSection, true);
		this._currentSection = newSection;
	}
};

ZmApptRecurDialog.prototype._cleanup = 
function() {
	// dont bother cleaning up if user is still mucking around
	if (this._saveState) return;

	// TODO: 
	// - dont cleanup for section that was picked if user clicks OK
	
	// reset end section
	this._noEndDateRadio.checked = true;
	this._endIntervalField.setValue("1");
	// reset daily section
	this._dailyDefaultRadio.checked = true;
	this._dailyField.setValue("2");
	// reset weekly section
	this._weeklyDefaultRadio.checked = true;
	this._weeklyField.setValue("2");
	for (var i = 0; i < this._weeklyCheckboxes.length; i++)
		this._weeklyCheckboxes[i].checked = false;
	// reset monthly section
	this._monthlyDefaultRadio.checked = true;
	this._monthlyMonthField.setValue("1");
	this._monthlyMonthFieldEx.setValue("1");
	this._monthlyDaySelect.setSelected(0);
	// reset yearly section
	this._yearlyDefaultRadio.checked = true;
	this._yearlyDaySelect.setSelected(0);
};

ZmApptRecurDialog.prototype._getRadioOptionValue = 
function(radioName) {	
	var options = document.getElementsByName(radioName);
	if (options) {
		for (var i = 0; i < options.length; i++) {
			if (options[i].checked)
				return options[i].value;
		}
	}
	return null;
};

/**
 * depending on the repeat type, populates repeat section as necessary
*/
ZmApptRecurDialog.prototype._populateForEdit = 
function(appt) {
	var recur = appt._recurrence;
	if (recur.repeatType == "NON") return;

	if (recur.repeatType == "DAI") {
		var dailyRadioOptions = document.getElementsByName(this._dailyRadioName);
		if (recur.repeatWeekday) {
			dailyRadioOptions[1].checked = true;
		} else if (recur.repeatCustomCount > 1) {
			this._dailyField.setValue(recur.repeatCustomCount);
			dailyRadioOptions[2].checked = true;
		}
	} else if (recur.repeatType == "WEE") {
		var weeklyRadioOptions = document.getElementsByName(this._weeklyRadioName);
		if (recur.repeatCustomCount == 1 && recur.repeatWeeklyDays.length == 1) {
			weeklyRadioOptions[0].checked = true;
			for (var j = 0; j < ZmCalItem.SERVER_WEEK_DAYS.length; j++) {
				if (recur.repeatWeeklyDays[0] == ZmCalItem.SERVER_WEEK_DAYS[j]) {
					this._weeklySelect.setSelectedValue(j);
					break;
				}
			}
		} else {
			weeklyRadioOptions[1].checked = true;
			this._weeklyField.setValue(recur.repeatCustomCount);
			// xxx: minor hack-- uncheck this since we init'd it earlier
			this._weeklyCheckboxes[this._startDate.getDay()].checked = false;
			for (var i = 0; i < recur.repeatWeeklyDays.length; i++) {
				for (var j = 0; j < ZmCalItem.SERVER_WEEK_DAYS.length; j++) {
					if (recur.repeatWeeklyDays[i] == ZmCalItem.SERVER_WEEK_DAYS[j]) {
						this._weeklyCheckboxes[j].checked = true;
						break;
					}
				}
			}
		}
	} else if (recur.repeatType == "MON") {
		var monthlyRadioOptions = document.getElementsByName(this._monthlyRadioName);
		if (recur.repeatMonthlyDayList) {
			monthlyRadioOptions[0].checked = true;
			this._monthlyDayField.setValue(recur.repeatMonthlyDayList[0]);
			this._monthlyMonthField.setValue(recur.repeatCustomCount);
		} else {
			monthlyRadioOptions[1].checked = true;
			this._monthlyDaySelect.setSelectedValue(recur.repeatCustomOrdinal);
			for (var i = 0; i < ZmCalItem.SERVER_WEEK_DAYS.length; i++) {
				if (ZmCalItem.SERVER_WEEK_DAYS[i] == recur.repeatCustomDayOfWeek) {
					this._monthlyWeekdaySelect.setSelectedValue(i);
					break;
				}
			}
			this._monthlyMonthFieldEx.setValue(recur.repeatCustomCount);
		}
	} else if (recur.repeatType == "YEA") {
		var yearlyRadioOptions = document.getElementsByName(this._yearlyRadioName);
		if (recur.repeatCustomType == "S") {
			yearlyRadioOptions[0].checked = true;
			this._yearlyDayField.setValue(recur.repeatCustomMonthDay);
			this._yearlyMonthSelect.setSelectedValue(Number(recur.repeatYearlyMonthsList)-1);
		} else {
			yearlyRadioOptions[1].checked = true;
			this._yearlyDaySelect.setSelectedValue(recur.repeatCustomOrdinal);
			for (var i = 0; i < ZmCalItem.SERVER_WEEK_DAYS.length; i++) {
				if (ZmCalItem.SERVER_WEEK_DAYS[i] == recur.repeatCustomDayOfWeek) {
					this._yearlyWeekdaySelect.setSelectedValue(i);
					break;
				}
			}
			this._yearlyMonthSelectEx.setSelectedValue(Number(recur.repeatYearlyMonthsList)-1);
		}
	}

	// populate recurrence ending rules
	if (recur.repeatEndType != "N") {
		var endRadioOptions = document.getElementsByName(this._repeatEndName);
		if (recur.repeatEndType == "A") {
			endRadioOptions[1].checked = true;
			this._endIntervalField.setValue(recur.repeatEndCount);
		} else {
			endRadioOptions[2].checked = true;
			this._endByField.setValue(AjxDateUtil.simpleComputeDateStr(recur.repeatEndDate));
		}
	}
};


// Listeners

ZmApptRecurDialog.prototype._repeatChangeListener =
function(ev) {
	var newValue = ev._args.newValue;
	this._setRepeatSection(newValue);
};

ZmApptRecurDialog.prototype._selectChangeListener = 
function(ev) {
	switch (ev._args.selectObj) {
		case this._weeklySelect:			this._weeklyDefaultRadio.checked = true; break;
		case this._monthlyDaySelect:
		case this._monthlyWeekdaySelect:	this._monthlyFieldRadio.checked = true; break;
		case this._yearlyMonthSelect:
			this._yearlyDefaultRadio.checked = true;
			this._yearlyDayField.validate();
			break;
		case this._yearlyDaySelect:
		case this._yearlyWeekdaySelect:
		case this._yearlyMonthSelectEx: 	this._yearlyFieldRadio.checked = true; break;
	}
};

ZmApptRecurDialog.prototype._endByButtonListener = 
function(ev) {
	var menu = ev.item.getMenu();
	var cal = menu.getItem(0);
	var initDate = this._endByField.isValid()
		? new Date(AjxDateUtil.simpleParseDateStr(this._endByField.getValue()))
		: new Date();
	cal.setDate(initDate, true);
	ev.item.popup();
};

ZmApptRecurDialog.prototype._dateCalSelectionListener = 
function(ev) {
	this._endByField.setValue(AjxDateUtil.simpleComputeDateStr(ev.detail));
	this._endByRadio.checked = true;
};

ZmApptRecurDialog.prototype._okListener = 
function() {
	this._saveState = true;
};

ZmApptRecurDialog.prototype._cancelListener = 
function() {
	this._cleanup();
};


// Callbacks

ZmApptRecurDialog.prototype._positiveIntValidator =
function(value) {
	DwtInputField.validateInteger(value);
	if (parseInt(value) < 1) {
		throw ZmMsg.errorLessThanOne;
	}
	return value;
};

ZmApptRecurDialog.prototype._yearlyDayValidator =
function(value) {
	DwtInputField.validateInteger(value);
	var dpm = AjxDateUtil._daysPerMonth[this._yearlyMonthSelect.getValue()];
	if (value < 1)
		throw AjxMessageFormat.format(AjxMsg.numberLessThanMin, 1);
	if (value > dpm) {
		throw AjxMessageFormat.format(AjxMsg.numberMoreThanMax, dpm);
	}
	return value;
};

ZmApptRecurDialog.prototype._endByDateValidator =
function(value) {
	DwtInputField.validateDate(value);
	var endByDate = AjxDateUtil.simpleParseDateStr(value);
	if (endByDate == null || endByDate.valueOf() < this._startDate.valueOf()) {
		throw ZmMsg.errorEndByDate;
	}
	return value;
};

ZmApptRecurDialog.prototype._weeklyValidator =
function(value) {
	value = this._positiveIntValidator(value);
	// make sure at least one day of the week is selected
	var checked = false;
	for (var i=0; i<this._weeklyCheckboxes.length; i++) {
		if (this._weeklyCheckboxes[i].checked) {
			checked = true;
			break;
		}
	}
	if (!checked) {
		throw ZmMsg.errorNoWeekdayChecked;
	}
	return value;
};


// Static methods

ZmApptRecurDialog._onFocus =
function(ev) {
	ev || (ev = window.event);

	var el = DwtUiEvent.getTarget(ev);
	var ard = AjxCore.objectWithId(el._recurDialogId);
	var dwtObj = Dwt.getObjectFromElement(el);

	switch (dwtObj) {
		case ard._endIntervalField: 	ard._endAfterRadio.checked = true; break;
		case ard._endByField: 			ard._endByRadio.checked = true; break;
		case ard._dailyField: 			ard._dailyFieldRadio.checked = true; break;
		case ard._weeklyField: 			ard._weeklyFieldRadio.checked = true; break;
		case ard._monthlyMonthField:
		case ard._monthlyDayField: 		ard._monthlyDefaultRadio.checked = true; break;
		case ard._monthlyMonthFieldEx: 	ard._monthlyFieldRadio.checked = true; break;
		case ard._yearlyDayField: 		ard._yearlyDefaultRadio.checked = true; break;
	}
};
