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

function ZmBasicPicker(parent) {

	ZmPicker.call(this, parent, ZmPicker.BASIC);
	
	this._cbQuery = null;
}

ZmBasicPicker.prototype = new ZmPicker;
ZmBasicPicker.prototype.constructor = ZmBasicPicker;

ZmPicker.CTOR[ZmPicker.BASIC] = ZmBasicPicker;

ZmBasicPicker.prototype.toString = 
function() {
	return "ZmBasicPicker";
}

ZmBasicPicker.prototype._makeRow =
function(text, id) {
    var size = 20;
    var html = new Array(10);
    var i = 0;
    html[i++] = "<tr valign='middle'>";
    html[i++] = "<td align='right' nowrap>" + text + ":</td>";
    html[i++] = "<td align='left' nowrap><input type='text' nowrap size='" + size + "' id='" + id + "'/></td>";
    html[i++] = "</tr>";

	return html.join("");		
}

// TODO: if we really wanted, we could add a prefs listener to update the "also search" checkboxes
ZmBasicPicker.prototype._setupPicker =
function(parent) {
    var picker = new DwtComposite(parent);

    var fromId = Dwt.getNextId();
    var toId = Dwt.getNextId();
    var subjectId = Dwt.getNextId();
    var contentId = Dwt.getNextId();
    var inTrashId = Dwt.getNextId();
    var inSpamId, checked;
    
	var html = new Array(20);
	var i = 0;
	html[i++] = "<table cellpadding='5' cellspacing='0' border='0'>";
	html[i++] = this._makeRow(ZmMsg.from, fromId);
	html[i++] = this._makeRow(ZmMsg.toCc, toId);
	html[i++] = this._makeRow(ZmMsg.subject, subjectId);
	html[i++] = this._makeRow(ZmMsg.content, contentId);
	
	if (this._appCtxt.get(ZmSetting.SPAM_ENABLED)) {
		inSpamId = Dwt.getNextId();
		checked = this._appCtxt.get(ZmSetting.SEARCH_INCLUDES_SPAM) ? " checked" : "";
		html[i++] = "<tr valign='middle'>";
		html[i++] = "<td align='right'><input type='checkbox'" + checked + " id='" + inSpamId + "' /></td>";
		html[i++] = "<td align='left' nowrap>" + ZmMsg.includeJunk + "</td>";
		html[i++] = "</tr>";
	}
	checked = this._appCtxt.get(ZmSetting.SEARCH_INCLUDES_TRASH) ? " checked" : "";
	html[i++] = "<tr valign='middle'>";
	html[i++] = "<td align='right'><input type='checkbox'" + checked + " id='" + inTrashId + "' /></td>";
	html[i++] = "<td align='left' nowrap>" + ZmMsg.includeTrash + "</td>";
	html[i++] = "</tr>";
	html[i++] = "</table>";
	picker.getHtmlElement().innerHTML = html.join("");

	this._from = this._setupField(fromId);
	this._to = this._setupField(toId);
	this._subject= this._setupField(subjectId);
	this._content = this._setupField(contentId);
	if (this._appCtxt.get(ZmSetting.SPAM_ENABLED))
		this._inSpam = this._setupSearch(inSpamId);
	this._inTrash = this._setupSearch(inTrashId);
}

ZmBasicPicker.prototype.setFrom =
function(from) {
	this._from.value = from;
	this._updateQuery();
}

ZmBasicPicker.prototype.setTo =
function(to) {
	this._to.value = to;
	this._updateQuery();
}

ZmBasicPicker.prototype.setSubject =
function(subject) {
	this._subject.value = subject;
	this._updateQuery();
}

ZmBasicPicker.prototype.setContent =
function(content) {
	this._content.value = content;
	this._updateQuery();
}

ZmBasicPicker.prototype._setupField = 
function(id) {
	var f = Dwt.getDomObj(this.getDocument(), id);
	Dwt.setHandler(f, DwtEvent.ONKEYUP, ZmBasicPicker._onChange);
	f._picker = this;
	return f;
}

ZmBasicPicker.prototype._setupSearch = 
function(id) {
	var f = Dwt.getDomObj(this.getDocument(), id);
	Dwt.setHandler(f, DwtEvent.ONCHANGE, ZmBasicPicker._onChange);
	f._picker = this;
	return f;
}

ZmBasicPicker._onChange =
function(ev) {
	var element = DwtUiEvent.getTarget(ev);
	var picker = element._picker;

	var charCode = DwtKeyEvent.getCharCode(ev);
	if (charCode == 13 || charCode == 3) {
		picker.execute();
	    return false;
	} else {
		picker._updateQuery();
		return true;
	}
}

ZmBasicPicker.prototype._updateQuery = 
function() {
	var query1 = new Array();
	var from = AjxStringUtil.trim(this._from.value, true);
	if (from.length)
		query1.push("from:(" + from + ")");
	var to = AjxStringUtil.trim(this._to.value, true);
	if (to.length)
		query1.push("(to:(" + to + ")" + " OR cc:(" + to + "))");
	var subject = AjxStringUtil.trim(this._subject.value, true);
	if (subject.length)
		query1.push("subject:(" + subject + ")");
	var content = AjxStringUtil.trim(this._content.value, true);
	if (content.length)
		query1.push("content:(" + content + ")");
	
	var query2 = new Array();
	var checkSpam = (this._inSpam && this._inSpam.checked);
	var checkTrash = (this._inTrash && this._inTrash.checked);
	if (checkSpam && checkTrash)
		query2.push("is:anywhere");
	else if (checkSpam)
		query2.push("is:anywhere not in:trash");
	else if (checkTrash)
		query2.push("is:anywhere not in:junk");
	var cbQuery = query2.length ? query2.join(" ") : null;

	// if the "search Trash/Spam" checkboxes changed, run the query
	var cbChange = (query1.length && ((this._cbQuery || cbQuery) && (this._cbQuery != cbQuery)));
	var query = query1.concat(query2);
	this.setQuery(query.length ? query.join(" ") : "");
	if (cbChange)
		this.execute();
	this._cbQuery = cbQuery;
}
