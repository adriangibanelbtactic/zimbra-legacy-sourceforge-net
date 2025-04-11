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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

function Com_Zimbra_Date() {
}

Com_Zimbra_Date.prototype = new ZmZimletBase();
Com_Zimbra_Date.prototype.constructor = Com_Zimbra_Date;

Com_Zimbra_Date.prototype.init =
function() {
	Com_Zimbra_Date.prototype._appCtxt = this._appCtxt;
	Com_Zimbra_Date.prototype._zimletContext = this._zimletContext;
	Com_Zimbra_Date.prototype._className = "Object";
	var pri = this._zimletContext.priority;
	if (this._appCtxt.get(ZmSetting.CALENDAR_ENABLED)) {
		ZmObjectManager.registerHandler("ZmDate1ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate2ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate3ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate4ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate5ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate6ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate7ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate8ObjectHandler", ZmObjectManager.DATE, pri);
		ZmObjectManager.registerHandler("ZmDate9ObjectHandler", ZmObjectManager.DATE, pri);	
// don't register this one by default, though it is used by the assistant.	
//ZmObjectManager.registerHandler("ZmDate10ObjectHandler", ZmObjectManager.DATE, pri);
	}
};

Com_Zimbra_Date.prototype.TYPE = ZmObjectManager.DATE;
Com_Zimbra_Date.prototype.getActionMenu =
	function(obj, span, context) {
		if (this._zimletContext._contentActionMenu instanceof AjxCallback) {
			this._zimletContext._contentActionMenu = this._zimletContext._contentActionMenu.run();
		}
		// Set some global context since the parent Zimlet (Com_Zimbra_Date) will be called for
		// right click menu options, even though the getActionMenu will get called on the sub-classes.
		Com_Zimbra_Date._actionObject = obj;
		Com_Zimbra_Date._actionSpan = span;
		Com_Zimbra_Date._actionContext = context;
		return this._zimletContext._contentActionMenu;
	};


var $RE_DOW = "(Mon(?:d(?:ay?)?)?|Tue(?:s(?:d(?:ay?)?)?)?|Wed(?:n(?:e(?:s(?:d(?:ay?)?)?)?)?)?|Thu(?:r(?:s(?:d(?:ay?)?)?)?)?|Fri(?:d(?:ay?)?)?|Sat(?:u(?:r(?:d(?:ay?)?)?)?)?|Sun(?:d(?:ay?)?)?)";
var $RE_DOW_FULL = "(Mon|Tues|Wednes|Thurs|Fri|Satur|Sun)day";

Com_Zimbra_Date.DOW = {	su: 0, mo: 1, tu: 2, we: 3, th: 4, fr: 5, sa: 6};

var $RE_DOM = "(\\d{1,2})(?:st|nd|rd|th)?";

// needs to be kept in sync with Com_Zimbra_Date.MONTH
var $RE_MONTH = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)";

Com_Zimbra_Date.MONTH = {
	january: 0, jan: 0, february: 1, feb: 1, march: 2, mar: 2, april: 3, apr: 3, may: 4, june: 5, jun: 5,
	july: 6, jul: 6, august: 7, aug: 7, september: 8, sept: 8, sep: 8, october: 9, oct: 9, november: 10, nov: 10,
	december: 11, dec: 11
};

var $RE_TODAY_TOMORROW_YESTERDAY = "(today|tomorrow|yesterday)";

var $RE_NEXT_THIS_LAST = "(next|this|last)";

var $RE_COMMA_OR_SP = "(?:\\s+|\\s*,\\s*)";

var $RE_DASH = "(?:-)";

var $RE_SLASH = "(?:\\/)";

var $RE_SP = "\\s+";

var $RE_YEAR4 = "(\\d{4})";

var $RE_MM = "(\\d{1,2})";

var $RE_DD = "(\\d{1,2})";

var $RE_YEAR42 = "(\\d{4}|\\d{2})";

var $RE_OP_TIME = "(?:\\s+\\d{1,2}:\\d{2}:\\d{2})?";

var $RE_OP_DOW = "(?:\\s*" + $RE_DOW + "\\s*)?";

var $RE_OP_YEAR42 = "(?:" + $RE_COMMA_OR_SP + $RE_YEAR42 + ")?";

var $RE_OP_YEAR4 = "(?:" + $RE_COMMA_OR_SP + $RE_YEAR4 + ")?";

Com_Zimbra_Date.prototype.getCurrentDate =
function(date) {
	var d = this[ZmObjectManager.ATTR_CURRENT_DATE];
	return d ? d : new Date();
};

Com_Zimbra_Date.prototype.menuItemSelected = function(itemId) {
	switch (itemId) {
		case "DAYVIEW":
			this._dayViewListener();
			break;
		case "NEWAPPT":
			this._newApptListener();
			break;
		case "SEARCHMAIL":
			this._searchMailListener();
			break;
	}
};

Com_Zimbra_Date.prototype.toolTipPoppedUp =
function(spanElement, contentObjText, matchContext, canvas) {
	var cc = AjxDispatcher.run("GetCalController");
	canvas.innerHTML = cc.getDayToolTipText(matchContext ? matchContext.date : new Date());
};

Com_Zimbra_Date.prototype._getHtmlContent =
function(html, idx, obj, context) {
	html[idx++] = AjxStringUtil.htmlEncode(obj, true);
	return idx;
};

Com_Zimbra_Date.prototype._dayViewListener =
function() {
	var loadCallback = new AjxCallback(this, this._handleLoadDayView);
	AjxDispatcher.require(["CalendarCore", "Calendar"], false, loadCallback, null, true);
};

Com_Zimbra_Date.prototype._handleLoadDayView =
function() {
	var calApp = this._appCtxt.getApp(ZmApp.CALENDAR);
	calApp.activate(true, ZmController.CAL_DAY_VIEW, Com_Zimbra_Date._actionContext.date);
};

Com_Zimbra_Date.prototype._newApptListener =
function() {
	var loadCallback = new AjxCallback(this, this._handleLoadNewAppt);
	AjxDispatcher.require(["CalendarCore", "Calendar"], false, loadCallback, null, true);
};

Com_Zimbra_Date.prototype._handleLoadNewAppt =
function() {
	// TODO support ev.shiftKey
	this._appCtxt.getAppViewMgr().popView(true, ZmController.LOADING_VIEW);	// pop "Loading..." page
	AjxDispatcher.run("GetCalController").newAppointmentHelper(Com_Zimbra_Date._actionContext.date);
};

Com_Zimbra_Date.prototype._searchMailListener =
function() {
	this._appCtxt.getSearchController().dateSearch(Com_Zimbra_Date._actionContext.date);
};

Com_Zimbra_Date.prototype.clicked =
function(spanElement, contentObjText, matchContext, canvas) {
	var calController = AjxDispatcher.run("GetCalController");
	calController.setDate(matchContext.date, 0, calController._miniCalendar.getForceRollOver());
	if (!calController._viewVisible) {
		calController.show(ZmController.CAL_DAY_VIEW);
	}
};

// today/yesterday =======================

function ZmDate1ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate1ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate1ObjectHandler.prototype.constructor = ZmDate1ObjectHandler;
ZmDate1ObjectHandler.prototype.name = "com_zimbra_date1";

ZmDate1ObjectHandler.REGEX = new RegExp("\\b" + $RE_TODAY_TOMORROW_YESTERDAY + "\\b", "ig");

ZmDate1ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate1ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate1ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var when = result[1].toLowerCase();
	if (when == "yesterday") {
		d.setDate(d.getDate() - 1);
	} else if (when == "tomorrow") {
		d.setDate(d.getDate() + 1);
	}
	result.context = {date: d, monthOnly: 0};
	return result;
};

// {next Tuesday}, {last Monday}, etc

function ZmDate2ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate2ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate2ObjectHandler.prototype.constructor = ZmDate2ObjectHandler;
ZmDate2ObjectHandler.prototype.name = "com_zimbra_date2";

ZmDate2ObjectHandler.REGEX = new RegExp("\\b" + $RE_NEXT_THIS_LAST + $RE_SP + $RE_DOW + "\\b", "ig");

ZmDate2ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate2ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate2ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var dow = d.getDay();
	var ndow = Com_Zimbra_Date.DOW[result[2].toLowerCase().substring(0,2)];
	var addDays;

	if (result[1].toLowerCase() == "next") {
		addDays = ndow - dow;
		addDays += 7;
	} else if (result[1].toLowerCase() == "this") {
		addDays = ndow - dow;
	} else { // last
		addDays = (-1 * (dow + 7 - ndow)) % 7;
		if (addDays === 0) {
			addDays = -7;
		}
	}
	d.setDate(d.getDate() + addDays);
	result.context = {date: d, monthOnly: 0};
	return result;
};

// {25th December}, {6th, June}, {6 June 2004}, {25th December, 2005}

function ZmDate3ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate3ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate3ObjectHandler.prototype.constructor = ZmDate3ObjectHandler;
ZmDate3ObjectHandler.prototype.name = "com_zimbra_date3";
ZmDate3ObjectHandler.REGEX = new RegExp("\\b" + $RE_DOM + $RE_COMMA_OR_SP + $RE_MONTH + $RE_OP_YEAR42 + "\\b", "ig");

ZmDate3ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate3ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate3ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var dom = parseInt(result[1], 10);
	var month = Com_Zimbra_Date.MONTH[result[2].toLowerCase()];
	d.setMonth(month, dom);
	if (result[3]) {
		var year = parseInt(result[3], 10);
		if (year < 20) {
			year += 2000;
		} else if (year < 100) {
			year += 1900;
		}
		d.setYear(year);
	}
	result.context = {date: d, monthOnly: 0};
	return result;
};

// {June 6th, 2005}, {June 6}, {May 24 10:11:26 2005},

function ZmDate4ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate4ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate4ObjectHandler.prototype.constructor = ZmDate4ObjectHandler;
ZmDate4ObjectHandler.prototype.name = "com_zimbra_date4";
ZmDate4ObjectHandler.REGEX = new RegExp("\\b" + $RE_MONTH + $RE_SP + $RE_DOM + $RE_OP_TIME + $RE_OP_YEAR4 + "\\b", "ig");

ZmDate4ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate4ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate4ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var month = Com_Zimbra_Date.MONTH[result[1].toLowerCase()];
	var dom = parseInt(result[2], 10);
	d.setMonth(month, dom);
	if (result[4]) {
		var year = parseInt(result[3], 10);
		if (year > 1000) {
			d.setYear(year);
		}		
	} else if (result[3]) {
		var year = parseInt(result[3], 10);
		if (year > 1000) {
			d.setYear(year);
		}
	}
	result.context = {date: d, monthOnly: 0};
	return result;
};

// {12-25-2005}, {06-06-05}, etc

function ZmDate5ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate5ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate5ObjectHandler.prototype.constructor = ZmDate5ObjectHandler;
ZmDate5ObjectHandler.prototype.name = "com_zimbra_date5";
ZmDate5ObjectHandler.REGEX = new RegExp("\\b" + $RE_MM + $RE_DASH + $RE_DD + $RE_DASH + $RE_YEAR42 + "\\b", "ig");

ZmDate5ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate5ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate5ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var month = parseInt(result[1], 10) - 1;
	var dom = parseInt(result[2], 10);
	d.setMonth(month, dom);
	var year = parseInt(result[3], 10);
	if (year < 20) {
		year += 2000;
	} else if (year < 100) {
		year += 1900;
	}
	d.setYear(year);

	result.context = {date: d, monthOnly: 0};
	return result;
};

// {2005-06-24}

function ZmDate6ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate6ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate6ObjectHandler.prototype.constructor = ZmDate6ObjectHandler;
ZmDate6ObjectHandler.prototype.name = "com_zimbra_date6";
ZmDate6ObjectHandler.REGEX = new RegExp("\\b" + $RE_YEAR4 + $RE_DASH + $RE_MM + $RE_DASH + $RE_DD + "\\b", "ig");

ZmDate6ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate6ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate6ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var year = parseInt(result[1], 10);
	var month = parseInt(result[2], 10) - 1;
	var dom = parseInt(result[3], 10);
	d.setMonth(month, dom);
	d.setYear(year);

	result.context = {date: d, monthOnly: 0};
	return result;
};

//{12/25/2005}, {06/06/05}, etc

function ZmDate7ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate7ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate7ObjectHandler.prototype.constructor = ZmDate7ObjectHandler;
ZmDate7ObjectHandler.prototype.name = "com_zimbra_date7";
ZmDate7ObjectHandler.REGEX = new RegExp("\\b" + $RE_MM + $RE_SLASH + $RE_DD + $RE_SLASH + $RE_YEAR42 + "\\b", "ig");

ZmDate7ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate7ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate7ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var month = parseInt(result[1], 10) - 1;
	var dom = parseInt(result[2], 10);
	d.setMonth(month, dom);
	var year = parseInt(result[3], 10);
	if (year < 20) {
		year += 2000;
	} else if (year < 100) {
		year += 1900;
	}
	d.setYear(year);

	result.context = {date: d, monthOnly: 0};
	return result;
};

// {2005/06/24}, {2005/12/25}

function ZmDate8ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate8ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate8ObjectHandler.prototype.constructor = ZmDate8ObjectHandler;
ZmDate8ObjectHandler.prototype.name = "com_zimbra_date8";
ZmDate8ObjectHandler.REGEX = new RegExp("\\b" + $RE_YEAR4 + $RE_SLASH + $RE_MM + $RE_SLASH + $RE_DD + "\\b", "ig");

ZmDate8ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate8ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate8ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}
	var d = new Date(this.getCurrentDate().getTime());
	var year = parseInt(result[1], 10);
	var month = parseInt(result[2], 10) - 1;
	var dom = parseInt(result[3], 10);
	d.setMonth(month, dom);
	d.setYear(year);
	result.context = {date: d, monthOnly: 0};
	return result;
};

// {June 2005}

function ZmDate9ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate9ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate9ObjectHandler.prototype.constructor = ZmDate9ObjectHandler;
ZmDate9ObjectHandler.prototype.name = "com_zimbra_date9";
ZmDate9ObjectHandler.REGEX = new RegExp("\\b" + $RE_MONTH + $RE_SP + $RE_YEAR4 + "\\b", "ig");

ZmDate9ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate9ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate9ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}

	var d = new Date(this.getCurrentDate().getTime());
	var month = Com_Zimbra_Date.MONTH[result[1].toLowerCase()];
	d.setMonth(month, 1);
	var year = result[2] ? parseInt(result[2], 10) : null;
	if (year) d.setYear(year);
	result.context = {date: d, monthOnly: 1};
	return result;
};


// {Tuesday}, {Monday}, etc

function ZmDate10ObjectHandler(appCtxt) {
	Com_Zimbra_Date.call(this, appCtxt);
}

ZmDate10ObjectHandler.prototype = new Com_Zimbra_Date();
ZmDate10ObjectHandler.prototype.constructor = ZmDate10ObjectHandler;
ZmDate10ObjectHandler.prototype.name = "com_zimbra_date10";

ZmDate10ObjectHandler.REGEX = new RegExp("\\b" + $RE_DOW_FULL + "\\b", "ig");

ZmDate10ObjectHandler.prototype.match =
function(line, startIndex) {
	ZmDate10ObjectHandler.REGEX.lastIndex = startIndex;
	var result = ZmDate10ObjectHandler.REGEX.exec(line);
	if (!result) {return null;}
	
	var d = new Date(this.getCurrentDate().getTime());
	var dow = d.getDay();
	var ndow = Com_Zimbra_Date.DOW[result[1].toLowerCase().substring(0,2)];
	var addDays = ndow - dow;
	d.setDate(d.getDate() + addDays);
	result.context = {date: d, monthOnly: 0};
	return result;
};
