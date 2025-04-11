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
* Creates an error dialog which basically means it will have a "Report" button
* @constructor
* @class
* A normal DwtMessageDialog w/ a "Report" button that will post user info to the 
* server when clicked.
*/
function ZmErrorDialog(parent, appCtxt, msgs) {
	if (arguments.length === 0) {return;}

	this._appCtxt = appCtxt;
	// go ahead and cache the navigator and subject info now (since it should never change)		
	this._strNav = this._getNavigatorInfo();
	this._subjPfx = this._getSubjectPrefix();

	var reportButton = new DwtDialog_ButtonDescriptor(ZmErrorDialog.REPORT_BUTTON, msgs.report, DwtDialog.ALIGN_LEFT);
	var detailButton = new DwtDialog_ButtonDescriptor(ZmErrorDialog.DETAIL_BUTTON, null, DwtDialog.ALIGN_LEFT);
	DwtMessageDialog.call(this, parent, null, null, [reportButton, detailButton]);

	// setup the detail button
	this._detailCell = Dwt.getDomObj(this.getDocument(), this._detailCellId);
	var detailBtn = this._button[ZmErrorDialog.DETAIL_BUTTON];
	detailBtn.setImage("SelectPullDownArrow");
	// arrow icon is too big so hack it to fit (instead of adding new image)
	Dwt.setSize(detailBtn.getHtmlElement(), 22, (AjxEnv.isIE ? 21 : 19));
	detailBtn.getHtmlElement().style.overflow = "hidden";

	// Set style on report button
	var reportBtn = this._button[ZmErrorDialog.REPORT_BUTTON];
	reportBtn.getHtmlElement().style.width = "100px";

	this.registerCallback(ZmErrorDialog.REPORT_BUTTON, this._reportCallback, this);
	this.registerCallback(ZmErrorDialog.DETAIL_BUTTON, this._showDetail, this);
}

ZmErrorDialog.prototype = new DwtMessageDialog;
ZmErrorDialog.prototype.constructor = ZmErrorDialog;



// Consts

ZmErrorDialog.REPORT_BUTTON = ++DwtDialog.LAST_BUTTON;
ZmErrorDialog.DETAIL_BUTTON = ++DwtDialog.LAST_BUTTON;
ZmErrorDialog.REPORT_URL = "//www.zimbra.com/e/";

// Public methods

ZmErrorDialog.prototype.toString = 
function() {
	return "ZmErrorDialog";
};

ZmErrorDialog.prototype.reset =
function() {
	this.setDetailString();
	DwtMessageDialog.prototype.reset.call(this);
};

ZmErrorDialog.prototype.setMessage =
function(msgStr, detailStr, style, title) {
	DwtMessageDialog.prototype.setMessage.call(this, msgStr, style, title);
	this.setDetailString(detailStr);
};

/**
* Sets the text that shows up when the Detail button is pressed.
*
* @param text	detail text
*/
ZmErrorDialog.prototype.setDetailString = 
function(text) {
	if (!(this._buttonElementId[ZmErrorDialog.DETAIL_BUTTON])) {return;}
	this._detailStr = text;
	if (text) {
		this._button[ZmErrorDialog.DETAIL_BUTTON].setVisible(true);
		if (this._detailCell && this._detailCell.innerHTML !== "") {
			this._detailCell.innerHTML = this._getDetailHtml(); //update detailCell if it is shown
		}
	} else {
		this._button[ZmErrorDialog.DETAIL_BUTTON].setVisible(false);
		if (this._detailCell) {
			this._detailCell.innerHTML = "";
		}
	}
};

ZmErrorDialog.prototype._getContentHtml =
function() {
	this._detailCellId = Dwt.getNextId();
	var html = new Array();
	var idx = 0;

	html[idx++] = DwtMessageDialog.prototype._getContentHtml.call(this);
	html[idx++] = "<div id='" + this._detailCellId + "'></div>";
	
	return html.join("");
};

ZmErrorDialog.prototype._getDetailHtml =
function() {
	return "<div class='vSpace'></div><table cellspacing=0 cellpadding=0 width='100%'>" +
		   "<tr><td><textarea readonly rows='10'>" + this._detailStr + "</textarea></td></tr></table>";
};

ZmErrorDialog.prototype._getNavigatorInfo = 
function() {
	var strNav = new Array();
	var idx = 0;
	
	// Add the url
	strNav[idx++] = "\n\n" + "href: " + location.href + "\n";

	for (var i in navigator) {
		// Skip functions
		if(typeof navigator[i] == "function") {continue;}
		strNav[idx++] = i + ": " + navigator[i] + "\n";
	}
	return strNav.join("");
};

ZmErrorDialog.prototype._getSubjectPrefix = 
function() {
	var strSubj = new Array();
	var idx = 0;
	
	strSubj[idx++] = "ER: ";
	
	if(AjxEnv.isIE) {
		strSubj[idx++] = "IE ";
	} else if (AjxEnv.isFirefox) {
		strSubj[idx++] = "FF ";
	} else if (AjxEnv.isMozilla) {
		strSubj[idx++] = "MOZ ";
	} else if (AjxEnv.isSafari) {
		strSubj[idx++] = "SAF ";
	} else if (AjxEnv.isOpera) {
		strSubj[idx++] = "OPE ";
	} else {
		strSubj[idx++] = "UKN ";
	}
	
	if(AjxEnv.isWindows) {
		strSubj[idx++] = "WIN ";
	} else if (AjxEnv.isLinux) {
		strSubj[idx++] = "LNX ";
	} else if (AjxEnv.isMac) {
		strSubj[idx++] = "MAC ";
	} else {
		strSubj[idx++] = "UNK ";
	}
	strSubj[idx++] = this._appCtxt.get(ZmSetting.CLIENT_VERSION) + " ";
	return strSubj.join("");
};

ZmErrorDialog.prototype._getUserPrefs = 
function() {
	var currSearch = this._appCtxt.getCurrentSearch();
	var strPrefs = new Array();
	var idx = 0;

	// Add username and current search
	strPrefs[idx++] = "\n\n" + "username: " + this._appCtxt.get(ZmSetting.USERNAME) + "\n";
	if (currSearch) {
		strPrefs[idx++] = "currentSearch: " + currSearch.query + "\n";
	}
	for (var i in ZmSetting.INIT) {
		if (ZmSetting.INIT[i][0]) {
			strPrefs[idx++] = ZmSetting.INIT[i][0] + ": " + ("" + ZmSetting.INIT[i][3]) + "\n";
		}
	}
	return strPrefs.join("");
};

// Callbacks

ZmErrorDialog.prototype._reportCallback =
function() {
	// iframe initialization - recreate iframe if IE and reuse if FF
	if (!this._iframe || AjxEnv.isIE) {
		this._iframe = this.getDocument().createElement("iframe");
		this._iframe.style.width = this._iframe.style.height = 0;
		this._iframe.style.visibility = "hidden";

		var contentDiv = this._getContentDiv();
		contentDiv.appendChild(this._iframe);
	}
	
	var strPrefs = this._getUserPrefs();
	var formId = Dwt.getNextId();

		
	// generate html form for submission via POST
	var html = new Array();
	var idx = 0;
	var subject = this._subjPfx + this._detailStr.substring(0,40);
	var scheme = (location.protocol == 'https:') ? "https:" : "http:";
	html[idx++] = "<html><head></head><body>";
	html[idx++] = "<form id='" + formId + "' method='POST' action='" + scheme + ZmErrorDialog.REPORT_URL + "'>";
	html[idx++] = "<textarea name='details'>" + this._detailStr;
	html[idx++] = "version - " + this._appCtxt.get(ZmSetting.CLIENT_VERSION) + "\n";
	html[idx++] = "release - " + this._appCtxt.get(ZmSetting.CLIENT_RELEASE) + "\n";
	html[idx++] = "date - " + this._appCtxt.get(ZmSetting.CLIENT_DATETIME);
	html[idx++] = "</textarea>";
	html[idx++] = "<textarea name='navigator'>" + this._strNav + "</textarea>";
	html[idx++] = "<textarea name='prefs'>" + strPrefs + "</textarea>";
	html[idx++] = "<textarea name='subject'>" + subject + "</textarea>";
	html[idx++] = "</form>";
	html[idx++] = "</body></html>";

	var idoc = Dwt.getIframeDoc(this._iframe);
	idoc.open();
	idoc.write(html.join(""));
	idoc.close();

	// submit the form!
	var form = idoc.getElementById(formId);
	if (form) {
		form.submit();
	}

	this.popdown();
};

// Displays the detail text
ZmErrorDialog.prototype._showDetail = 
function() {
	if (this._detailCell) {
		if (this._detailCell.innerHTML === "") {
			this._button[ZmErrorDialog.DETAIL_BUTTON].setImage("SelectPullUpArrow");
			this._detailCell.innerHTML = this._getDetailHtml();
		} else {
			this._button[ZmErrorDialog.DETAIL_BUTTON].setImage("SelectPullDownArrow");
			this._detailCell.innerHTML = "";
		}
	}
};
