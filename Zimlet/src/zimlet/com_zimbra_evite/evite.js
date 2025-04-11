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
 * The Original Code is: Zimbra Collaboration Suite.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function Com_zimbra_evite() {
}

Com_zimbra_evite.prototype = new ZmZimletBase();
Com_zimbra_evite.prototype.constructor = Com_zimbra_evite;

Com_zimbra_evite.prototype.init =
function() {
	this.login();
	this.listFolders();
};

Com_zimbra_evite.prototype.login = 
function(callback) {
	if (callback == null) {
		callback = false;
	}
	var user = this.getUserProperty("user");
	var passwd = this.getUserProperty("passwd");
	if (user && passwd) {
		this.eviteAuth(user, passwd, callback);
	} else if (callback) {
		this.createPropertyEditor(new AjxCallback(this, this.login, [ callback ]));
	}
};

Com_zimbra_evite.prototype.menuItemSelected = 
function(itemId) {
	switch (itemId) {
	    case "sync":
		this.myEvite();
		break;
	    case "pref":
		this.createPropertyEditor();
		break;
	}
};

Com_zimbra_evite.prototype.getRandomNumber =
function() {
	return Math.round((Math.random()*1000)+1);
};

Com_zimbra_evite.prototype.eviteAuth =
function(user, passwd, callback) {
	var authUrl = this.getConfig('authUrl')+'?email='+user+'&pass='+passwd+'&src=zimbra&rndm='+this.getRandomNumber();
	var url = ZmZimletBase.PROXY + AjxStringUtil.urlEncode(authUrl);
	AjxRpc.invoke(null, url, null, new AjxCallback(this, this.authCallback, [ callback ]), true);
};

Com_zimbra_evite.prototype.myEvite =
function() {
	if (!this.userID) {
		this.login(new AjxCallback(this, this.myEvite));
		return;
	}
	var myUrl = this.getConfig('myUrl')+'?userID='+this.userID+'&src=zimbra&rndm='+this.getRandomNumber();
	var url = ZmZimletBase.PROXY + AjxStringUtil.urlEncode(myUrl);
	AjxRpc.invoke(null, url, null, new AjxCallback(this, this.myCallback), true);
};

Com_zimbra_evite.prototype.findChild =
function(elem, nodeName) {
	return this.findSibling(elem.firstChild, nodeName);
};

Com_zimbra_evite.prototype.findSibling =
function(elem, nodeName) {
	var child;
	for (child = elem; child != null; child = child.nextSibling){
		if (child.nodeName == nodeName) {
			return child;
		}
	}
	throw new AjxException("Cannot find node "+nodeName, AjxException.INVALID_PARAM, "findSibling");
};

Com_zimbra_evite.prototype.authCallback =
function(callback, result) {
	var elem;
	if (!result.success) {
		return;
	}
	
	try {
		if (!result.xml || !result.xml.documentElement) {
			var doc = AjxXmlDoc.createFromXml(result.text);
			elem = doc.getDoc();
		} else {
			elem = result.xml;
		}
		if (elem != null) {
			elem = this.findChild(elem, 'EviteUserInfo');
			elem = this.findChild(elem, 'eviteAuth');
			elem = this.findChild(elem, 'userID');
		}
	} catch (ex) {
		DBG.println(AjxDebug.DBG1, ex.dump());
		return;
	}
	
	this.userID = elem.firstChild.data;
	if (callback) callback.run();
};

Com_zimbra_evite.prototype.myCallback =
function(result) {
	var events, event, elem, appts;
	if (!result.success) {
		return;
	}
	elem = result.xml;
	try {
		appts = this.fetchEviteAppts();
		elem = this.findChild(elem, 'invitations');
		events = this.findChild(elem, 'events');
		for (event = events.firstChild; event != null; event = event.nextSibling) {
			if (event.nodeName != 'event') continue;
			var title, url, evdate, evtime;
			title = "evite: "+this.findChild(event, 'title').firstChild.data;
			url = this.findChild(event, 'url').firstChild.data;
			evdate = this.findChild(event, 'eventDate').firstChild.data;
			evtime = this.findChild(event, 'eventTime').firstChild.data;
			//elem = this.findChild(event, 'organizer');
			//elem = this.findChild(event, 'newRSVPs');
			//elem = this.findChild(event, 'timeType');
			var found = false;
			for (var i = 0; i < appts.size(); i++) {
				var appt = appts.get(i);
				var name = appt.getName();
				var startDate = AjxDateUtil.getTimeStr(appt.getStartDate(), "%Y%n%d");
				if (name == title) {  // XXX and check the date to really make sure.
					found = true;
					break;
				}
			}
			if (!found) {
				this.createAppt(title, url, evdate, evtime);
			}
		}
	} catch (ex) {
		DBG.println(AjxDebug.DBG1, ex.dump());
		return;
	}
};

Com_zimbra_evite.prototype.getUsername =
function() {
	return this.xmlObj()._appCtxt.get(ZmSetting.USERNAME);
};

Com_zimbra_evite.prototype.createAppt =
function(title, url, date, time) {
	if (!this.userID || !this.eviteFolderID) {
		DBG.println(AjxDebug.DBG1, "evite zimlet has not been initialized.");
		return;
	}
	var soapDoc = AjxSoapDoc.create("CreateAppointmentRequest", "urn:zimbraMail");
	var m = soapDoc.set("m");
	m.setAttribute("l", this.eviteFolderID);
	var node = soapDoc.set("inv", null, m);
	node.setAttribute("method", "REQUEST");
	node.setAttribute("type", "event");
	node.setAttribute("fb", "B");
	node.setAttribute("transp", "O");
	node.setAttribute("status", "CONF");
	node.setAttribute("allDay", "1");
	node.setAttribute("name", title);
	node.setAttribute("loc", "");

	var subnode = soapDoc.set("s", null, node);
	//subnode.setAttribute("tz", "GMT-08.00) Pacific Time (US & Canada) / Tijuana");
	subnode.setAttribute("d", date);
	subnode = soapDoc.set("e", null, node);
	//subnode.setAttribute("tz", "GMT-08.00) Pacific Time (US & Canada) / Tijuana");
	subnode.setAttribute("d", date);
	subnode = soapDoc.set("or", null, node);
	subnode.setAttribute("a", this.getUsername());
	
	node = soapDoc.set("mp", null, m);
	node.setAttribute("ct", "text/plain");
	subnode = soapDoc.set("content", url, node);

	node = soapDoc.set("su", title, m);

	var command = new ZmCsfeCommand();
	var resp = command.invoke({soapDoc: soapDoc});
};

Com_zimbra_evite.prototype.listFolders =
function() {
	var soapDoc = AjxSoapDoc.create("GetFolderRequest", "urn:zimbraMail");
	var command = new ZmCsfeCommand();
	var top = command.invoke({soapDoc: soapDoc}).Body.GetFolderResponse.folder[0];

	var folders = top.folder;
	if (folders) {
		for (var i = 0; i < folders.length; i++) {
			var f = folders[i];
			if (f && f.name == 'evite' && f.view == ZmOrganizer.VIEWS[ZmOrganizer.CALENDAR]) {
				this.eviteFolderID = f.id;
				return;
			}
		}
	}
	
	this.createEviteFolder(top.id);
};

Com_zimbra_evite.prototype.createEviteFolder =
function(parent) {
	var soapDoc = AjxSoapDoc.create("CreateFolderRequest", "urn:zimbraMail");
	var folderNode = soapDoc.set("folder");
	folderNode.setAttribute("name", "evite");
	folderNode.setAttribute("l", parent);
	folderNode.setAttribute("view", ZmOrganizer.VIEWS[ZmOrganizer.CALENDAR]);
	var command = new ZmCsfeCommand();
	var resp = command.invoke({soapDoc: soapDoc});
	var id = resp.Body.CreateFolderResponse.folder[0].id;
	if (!id) {
		throw new AjxException("Cannot create evite folder ", AjxException.INTERNAL_ERROR, "createEviteFolder");
	}
	this.eviteFolderID = id;
	
	soapDoc = AjxSoapDoc.create("FolderActionRequest", "urn:zimbraMail");
	var actionNode = soapDoc.set("action");
	actionNode.setAttribute("op", "color");
	actionNode.setAttribute("id", id);
	actionNode.setAttribute("color", "6");
	command = new ZmCsfeCommand();
	resp = command.invoke({soapDoc: soapDoc});
};

Com_zimbra_evite.prototype.fetchEviteAppts =
function() {
	if (!this.eviteFolderID) {
		DBG.println(AjxDebug.DBG1, "evite zimlet has not been initialized.");
		return;
	}
	// for one month ahead.
	var start = new Date();
	start.setHours(0, 0, 0, 0);
	var calController = this.xmlObj()._appCtxt.getApp(ZmZimbraMail.CALENDAR_APP).getCalController();
	return calController.getApptSummaries(start.getTime(), start.getTime()+AjxDateUtil.MSEC_PER_DAY * 30, true, this.eviteFolderID);
};

Com_zimbra_evite.prototype.buttonListener =
function(ev) {
	//document.createForm.submit();
	this._dialog.popdown();
};

Com_zimbra_evite.prototype.doDrop =
function(obj) {
	if (obj.TYPE == "ZmAppt") {
		DBG.println(AjxDebug.DBG1, "APPT dropped: "+obj.toString());
		var view = new DwtComposite(this.getShell());
		var el = view.getHtmlElement();
		var table = document.createElement("form");
		el.appendChild(table);
		var html = new Array();
		var i = 0;
		html[i++] = "<form name='createForm' method='post' target='http://www.evite.com/app/create/create.do'>";
		html[i++] = "<table>";
		html[i++] = "<tr><td>Title</td><td><input name='eventTitle' type='text' size='20' value='"+obj.subject+"'/></td></tr>";
		html[i++] = "<tr><td>Organizer</td><td><input name='eventHostedBy' type='text' size='20' value='"+this.getUserProperty("user")+"'/></td></tr>";
		html[i++] = "<tr><td>Location</td><td><input name='eventLocation' type='text' size='20' value='"+obj.location+"'/></td></tr>";
		html[i++] = "<tr><td>Start Date</td><td><input name='eventDate' size='10' type='text' value='"+AjxDateUtil.getTimeStr(obj.startDate,"%n/%d/%Y")+"'/></td></tr>";
		html[i++] = "<tr><td>Start Time</td><td><input name='eventHour' size='2' type='text' value='"+AjxDateUtil.getTimeStr(obj.startDate,"%H")+"'/>";
		html[i++] = "<input name='eventMinute' type='text' size='2' value='"+AjxDateUtil.getTimeStr(obj.startDate,"%m")+"'/>";
		html[i++] = "<input name='eventAMPM' type='text' size='2' value='"+AjxDateUtil.getTimeStr(obj.startDate,"%P")+"'/></td></tr>";
		html[i++] = "<tr><td>End Date</td><td><input name='eventEndDate' size='10' type='text' value='"+AjxDateUtil.getTimeStr(obj.endDate,"%n/%d/%Y")+"'/></td></tr>";
		html[i++] = "<tr><td>End Time</td><td><input name='eventEndHour' size='2' type='text' value='"+AjxDateUtil.getTimeStr(obj.endDate,"%H")+"'/>";
		html[i++] = "<input name='eventEndMinute' type='text' size='2' value='"+AjxDateUtil.getTimeStr(obj.endDate,"%m")+"'/>";
		html[i++] = "<input name='eventEndAMPM' type='text' size='2' value='"+AjxDateUtil.getTimeStr(obj.endDate,"%P")+"'/></td></tr>";
		html[i++] = "<tr><td>Notes</td><td><input name='eventNotes' size='20' type='text' value='"+obj.notes+"'/></td></tr>";
		html[i++] = "</table>";
		html[i++] = "</form>";
		table.innerHTML = html.join('');
		this._dialog = this._createDialog({title:"Create Evite Event", view:view});
		this._dialog.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(this, this.buttonListener));
		this._dialog.popup();
	}
};
