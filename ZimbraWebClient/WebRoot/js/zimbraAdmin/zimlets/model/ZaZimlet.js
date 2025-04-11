/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * The Original Code is: Zimbra Network
 * 
 * ***** END LICENSE BLOCK *****
 */
/**
* @class ZaZimlet
* @contructor ZaZimlet
* @param ZaApp app
* this class is a model for managing Zimlets
* @author Greg Solovyev
**/
function ZaZimlet(app) {
	ZaItem.call(this, app,"ZaZimlet");
	this.label = "";
	this[ZaModel.currentStep] = 1;
}
ZaZimlet.prototype = new ZaItem;
ZaZimlet.prototype.constructor = ZaZimlet;
ZaItem.loadMethods["ZaZimlet"] = new Array();
ZaItem.initMethods["ZaZimlet"] = new Array();

ZaZimlet.A_name = "name";
ZaZimlet.A_zimbraZimletEnabled = "zimbraZimletEnabled";
ZaZimlet.A_zimbraZimletPriority = "zimbraZimletPriority";
ZaZimlet.A_zimbraZimletIsExtension = "zimbraZimletIsExtension";
ZaZimlet.A_zimbraZimletKeyword = "zimbraZimletKeyword";
ZaZimlet.A_zimbraZimletVersion = "zimbraZimletVersion";
ZaZimlet.A_zimbraZimletDescription = "zimbraZimletDescription";
ZaZimlet.A_zimbraZimletIndexingEnabled = "zimbraZimletIndexingEnabled";
ZaZimlet.A_zimbraZimletStoreMatched = "zimbraZimletStoreMatched";
ZaZimlet.A_zimbraZimletHandlerClass = "zimbraZimletHandlerClass";
ZaZimlet.A_zimbraZimletHandlerConfig = "zimbraZimletHandlerConfig";
ZaZimlet.A_zimbraZimletContentObject = "zimbraZimletContentObject";
ZaZimlet.A_zimbraZimletPanelItem = "zimbraZimletPanelItem";
ZaZimlet.A_zimbraZimletScript = "zimbraZimletScript";
ZaZimlet.A_zimbraZimletServerIndexRegex = "zimbraZimletServerIndexRegex";
ZaZimlet.A_attachmentId = "attId";
ZaZimlet.A_deployStatus = "deployStatus";
ZaZimlet.A_statusMsg = "statusMsg";
ZaZimlet.EXCLUDE_MAIL = "mail";
ZaZimlet.EXCLUDE_EXTENSIONS = "extension";
ZaZimlet.EXCLUDE_NONE = "none";	
ZaZimlet.STATUS_FAILED = "failed";
ZaZimlet.STATUS_SUCCEEDED = "succeeded";
ZaZimlet.STATUS_PENDING = "pending";
ZaZimlet.ACTION_DEPLOY_ALL = "deployAll";
ZaZimlet.ACTION_DEPLOY_LOCAL = "deployLocal";
ZaZimlet.ACTION_DEPLOY_STATUS = "status";
ZaZimlet.A_progress = "progress";
ZaZimlet.prototype.toString = function() {
	return this.name;
}

ZaZimlet.getAll =
function(app, exclude) {
	var exc = exclude ? exclude : "none";
	var soapDoc = AjxSoapDoc.create("GetAllZimletsRequest", "urn:zimbraAdmin", null);	
	soapDoc.getMethod().setAttribute("exclude", exc);	
	var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	var resp = command.invoke(params).Body.GetAllZimletsResponse;	
	var list = new ZaItemList(ZaZimlet, app);
	list.loadFromJS(resp);	
	return list;
}

ZaZimlet.prototype.enable = function (enabled, callback) {
	var soapDoc = AjxSoapDoc.create("ModifyZimletRequest", "urn:zimbraAdmin", null);
	var zimletEl = soapDoc.set("zimlet", "");
	zimletEl.setAttribute("name", this.name);
	var statusEl = soapDoc.set("status", "",zimletEl);	
	if(enabled)	 {
		statusEl.setAttribute("value","enabled");
		this.attrs[ZaZimlet.A_zimbraZimletEnabled] = "TRUE";
	} else {
		statusEl.setAttribute("value","disabled");
		this.attrs[ZaZimlet.A_zimbraZimletEnabled] = "FALSE";		
	}
	var asynCommand = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	if(callback) {
		params.asyncMode = true;
		params.callback = callback;
	}

	asynCommand.invoke(params);	
	
}

/**
* @param mods - map of modified attributes
* modifies object's information in the database
**/
ZaZimlet.prototype.modify =
function(mods) {
	/*var soapDoc = AjxSoapDoc.create("ModifyZimletRequest", "urn:zimbraAdmin", null);
	soapDoc.set("id", this.id);
	for (var aname in mods) {
		if (mods[aname] instanceof Array) {
			var array = mods[aname];
			if (array.length > 0) {
				for (var i = 0; i < array.length; i++) {
					var attr = soapDoc.set("a", array[i]);
					attr.setAttribute("n", aname);
				}
			}
			else {
				var attr = soapDoc.set("a");
				attr.setAttribute("n", aname);
			}
		}
		else {
			var attr = soapDoc.set("a", mods[aname]);
			attr.setAttribute("n", aname);
		}
	}
	var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	var resp = command.invoke(params).Body.ModifyZimletResponse;*/		
}

/**
* Returns HTML for a tool tip for this domain.
*/
ZaZimlet.prototype.getToolTip =
function() {
	// update/null if modified
	if (!this._toolTip) {
		var html = new Array(20);
		var idx = 0;
		html[idx++] = "<table cellpadding='0' cellspacing='0' border='0'>";
		html[idx++] = "<tr valign='center'><td colspan='2' align='left'>";
		html[idx++] = "<div style='border-bottom: 1px solid black; white-space:nowrap; overflow:hidden;width:350'>";
		html[idx++] = "<table cellpadding='0' cellspacing='0' border='0' style='width:100%;'>";
		html[idx++] = "<tr valign='center'>";
		html[idx++] = "<td><b>" + AjxStringUtil.htmlEncode(this.name) + "</b></td>";
		html[idx++] = "<td align='right'>";
		html[idx++] = AjxImg.getImageHtml("ZaZimlet");		
		html[idx++] = "</td>";
		html[idx++] = "</table></div></td></tr>";
		html[idx++] = "<tr></tr>";
		idx = this._addAttrRow(ZaItem.A_description, html, idx);		
		idx = this._addAttrRow(ZaItem.A_zimbraId, html, idx);
		html[idx++] = "</table>";
		this._toolTip = html.join("");
	}
	return this._toolTip;
}

ZaZimlet.prototype.remove = 
function() {
	var soapDoc = AjxSoapDoc.create("UndeployZimletRequest", "urn:zimbraAdmin", null);
	soapDoc.getMethod().setAttribute("name", this.name);	
	//soapDoc.set("id", this.id);
	var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	var resp = command.invoke(params);	
}

ZaZimlet.prototype.refresh = 
function() {
	this.load();	
}

ZaZimlet.deploy = function (action,attId, callback) {
	var soapDoc = AjxSoapDoc.create("DeployZimletRequest", "urn:zimbraAdmin", null);
	if(action)
		soapDoc.getMethod().setAttribute("action", action);		
		
	var contentEl = soapDoc.set("content", "");
	if(attId) {
		contentEl.setAttribute("aid", attId);
	}
	var asynCommand = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	if(callback) {
		params.asyncMode = true;
		params.callback = callback;
	}
	asynCommand.invoke(params);	
}

ZaZimlet.loadMethod = 
function(by, val, withConfig) {
	var _val = val ? val : this.name
	var soapDoc = AjxSoapDoc.create("GetZimletRequest", "urn:zimbraAdmin", null);
	var elZimlet = soapDoc.set("zimlet", "");
	elZimlet.setAttribute("name", val);
	var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	params.asyncMode = false;
	resp = command.invoke(params);		
	this.initFromJS(resp.Body.GetZimletResponse.zimlet[0]);
}
ZaItem.loadMethods["ZaZimlet"].push(ZaZimlet.loadMethod);

ZaZimlet.myXModel = { 
	items:[]	
}