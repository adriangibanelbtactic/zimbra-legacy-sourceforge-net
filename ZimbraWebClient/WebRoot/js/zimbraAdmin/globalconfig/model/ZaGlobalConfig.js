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

ZaGlobalConfig = function(app) {
	ZaItem.call(this, app, "ZaGlobalConfig");
	this.attrs = new Object();
//	this.attrsInternal = new Object();	
	this.load();
}

ZaGlobalConfig.prototype = new ZaItem;
ZaGlobalConfig.prototype.constructor = ZaGlobalConfig;
ZaItem.loadMethods["ZaGlobalConfig"] = new Array();

ZaGlobalConfig.MTA_RESTRICTIONS = [
	"reject_invalid_hostname", "reject_non_fqdn_hostname", "reject_non_fqdn_sender",
	"reject_unknown_client", "reject_unknown_hostname", "reject_unknown_sender_domain"
];

//general
ZaGlobalConfig.A_zimbraLastLogonTimestampFrequency = "zimbraLastLogonTimestampFrequency";
ZaGlobalConfig.A_zimbraDefaultDomainName = "zimbraDefaultDomainName";
ZaGlobalConfig.A_zimbraDataSourceNumThreads = "zimbraDataSourceNumThreads" ;
// attachements
ZaGlobalConfig.A_zimbraAttachmentsBlocked = "zimbraAttachmentsBlocked";

ZaGlobalConfig.A_zimbraMtaBlockedExtension = "zimbraMtaBlockedExtension";
ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension = "zimbraMtaCommonBlockedExtension";

// MTA
ZaGlobalConfig.A_zimbraMtaAuthEnabled = "zimbraMtaAuthEnabled";
ZaGlobalConfig.A_zimbraMtaTlsAuthOnly = "zimbraMtaTlsAuthOnly";
ZaGlobalConfig.A_zimbraMtaDnsLookupsEnabled  = "zimbraMtaDnsLookupsEnabled";
ZaGlobalConfig.A_zimbraMtaMaxMessageSize = "zimbraMtaMaxMessageSize";
ZaGlobalConfig.A_zimbraMtaRelayHost = "zimbraMtaRelayHost";
ZaGlobalConfig.A_zimbraMtaMyNetworks = "zimbraMtaMyNetworks";
//ZaGlobalConfig.A_zimbraMtaRelayHostInternal = "__zimbraMtaRelayHost";
//ZaGlobalConfig.A_zimbraMtaRelayPortInternal = "__zimbraMtaRelayPort";
ZaGlobalConfig.A_zimbraComponentAvailable = "zimbraComponentAvailable";
ZaGlobalConfig.A_zimbraSmtpSendAddOriginatingIP = "zimbraSmtpSendAddOriginatingIP";

// --protocol checks
ZaGlobalConfig.A_zimbraMtaRestriction = "zimbraMtaRestriction";
ZaGlobalConfig.A_zimbraMtaRejectInvalidHostname = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_invalid_hostname";
ZaGlobalConfig.A_zimbraMtaRejectNonFqdnHostname = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_non_fqdn_hostname";
ZaGlobalConfig.A_zimbraMtaRejectNonFqdnSender = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_non_fqdn_sender";
// -- dns checks
ZaGlobalConfig.A_zimbraMtaRejectUnknownClient = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_unknown_client";
ZaGlobalConfig.A_zimbraMtaRejectUnknownHostname = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_unknown_hostname";
ZaGlobalConfig.A_zimbraMtaRejectUnknownSenderDomain = "_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_reject_unknown_sender_domain";
//Domain
ZaGlobalConfig.A_zimbraGalLdapFilterDef = "zimbraGalLdapFilterDef";
ZaGlobalConfig.A_zimbraGalMaxResults = "zimbraGalMaxResults";
ZaGlobalConfig.A_zimbraNotebookAccount = "zimbraNotebookAccount";
//Server
ZaGlobalConfig.A_zimbraLmtpNumThreads = "zimbraLmtpNumThreads";
ZaGlobalConfig.A_zimbraLmtpBindPort = "zimbraLmtpBindPort";
ZaGlobalConfig.A_zimbraPop3NumThreads = "zimbraPop3NumThreads";
ZaGlobalConfig.A_zimbraPop3BindPort = "zimbraPop3BindPort";
ZaGlobalConfig.A_zimbraRedologEnabled = "zimbraRedologEnabled";
ZaGlobalConfig.A_zimbraRedologLogPath = "zimbraRedologLogPath";
ZaGlobalConfig.A_zimbraRedologArchiveDir = "zimbraRedologArchiveDir";
ZaGlobalConfig.A_zimbraRedologBacklogDir = "zimbraRedologBacklogDir";
ZaGlobalConfig.A_zimbraRedologRolloverFileSizeKB = "zimbraRedologRolloverFileSizeKB";
ZaGlobalConfig.A_zimbraRedologFsyncIntervalMS = "zimbraRedologFsyncIntervalMS";
//ZaGlobalConfig.A_zimbraFileUploadMaxSize = "zimbraFileUploadMaxSize"

// smtp
ZaGlobalConfig.A_zimbraSmtpHostname = "zimbraSmtpHostname";
ZaGlobalConfig.A_zimbraSmtpPort = "zimbraSmtpPort";
ZaGlobalConfig.A_zimbraSmtpTimeout = "zimbraSmtpTimeout";
// pop
ZaGlobalConfig.A_zimbraPop3BindPort="zimbraPop3BindPort";
ZaGlobalConfig.A_zimbraPop3ServerEnabled = "zimbraPop3ServerEnabled";
ZaGlobalConfig.A_zimbraPop3SSLBindPort = "zimbraPop3SSLBindPort";
ZaGlobalConfig.A_zimbraPop3SSLServerEnabled = "zimbraPop3SSLServerEnabled";
ZaGlobalConfig.A_zimbraPop3CleartextLoginEnabled = "zimbraPop3CleartextLoginEnabled";
// imap
ZaGlobalConfig.A_zimbraImapBindPort = "zimbraImapBindPort";
ZaGlobalConfig.A_zimbraImapServerEnabled = "zimbraImapServerEnabled";
ZaGlobalConfig.A_zimbraImapSSLBindPort = "zimbraImapSSLBindPort";
ZaGlobalConfig.A_zimbraImapSSLServerEnabled = "zimbraImapSSLServerEnabled";
ZaGlobalConfig.A_zimbraImapCleartextLoginEnabled = "zimbraImapCleartextLoginEnabled";
// anti-spam
ZaGlobalConfig.A_zimbraSpamKillPercent = "zimbraSpamKillPercent";
ZaGlobalConfig.A_zimbraSpamTagPercent = "zimbraSpamTagPercent";
ZaGlobalConfig.A_zimbraSpamSubjectTag = "zimbraSpamSubjectTag";
// anti-virus
ZaGlobalConfig.A_zimbraVirusWarnRecipient = "zimbraVirusWarnRecipient";
ZaGlobalConfig.A_zimbraVirusWarnAdmin = "zimbraVirusWarnAdmin";
ZaGlobalConfig.A_zimbraVirusDefinitionsUpdateFrequency = "zimbraVirusDefinitionsUpdateFrequency";
ZaGlobalConfig.A_zimbraVirusBlockEncryptedArchive = "zimbraVirusBlockEncryptedArchive";
//immutable attrs
ZaGlobalConfig.A_zimbraAccountClientAttr = "zimbraAccountClientAttr";
ZaGlobalConfig.A_zimbraServerInheritedAttr = "zimbraServerInheritedAttr";
ZaGlobalConfig.A_zimbraDomainInheritedAttr = "zimbraDomainInheritedAttr";
ZaGlobalConfig.A_zimbraCOSInheritedAttr = "zimbraCOSInheritedAttr";
ZaGlobalConfig.A_zimbraGalLdapAttrMap = "zimbraGalLdapAttrMap";
ZaGlobalConfig.A_zimbraGalLdapFilterDef = "zimbraGalLdapFilterDef";

//mailproxy
ZaGlobalConfig.A_zimbraImapProxyBindPort="zimbraImapProxyBindPort";
ZaGlobalConfig.A_zimbraImapSSLProxyBindPort="zimbraImapSSLProxyBindPort";
ZaGlobalConfig.A_zimbraPop3ProxyBindPort="zimbraPop3ProxyBindPort";
ZaGlobalConfig.A_zimbraPop3SSLProxyBindPort="zimbraPop3SSLProxyBindPort";

// others
ZaGlobalConfig.A_zimbraInstalledSkin = "zimbraInstalledSkin";
ZaGlobalConfig.A_zimbraNewExtension = "_zimbraNewExtension";

ZaGlobalConfig.A_originalMonitorHost = "_originalMonitorHost";
ZaGlobalConfig.A_currentMonitorHost = "_currentMonitorHost";


ZaGlobalConfig.loadMethod = 
function(by, val, withConfig) {
	if(!ZaSettings.GLOBAL_CONFIG_ENABLED)
		return;
	var soapDoc = AjxSoapDoc.create("GetAllConfigRequest", "urn:zimbraAdmin", null);
	//var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	var reqMgrParams = {
		controller : this._app.getCurrentController(),
		busyMsg : ZaMsg.BUSY_GET_ALL_CONFIG
	}
	var resp = ZaRequestMgr.invoke(params, reqMgrParams).Body.GetAllConfigResponse;
	this.initFromJS(resp);	
}
ZaItem.loadMethods["ZaGlobalConfig"].push(ZaGlobalConfig.loadMethod);

ZaGlobalConfig.prototype.initFromJS = function(obj) {
	ZaItem.prototype.initFromJS.call(this, obj);

	var blocked = this.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension];
	if (blocked == null) {
		blocked = [];
	}
	else if (AjxUtil.isString(blocked)) {
		blocked = [ blocked ];
	}
	
	// convert blocked extension lists to arrays
	var common = this.attrs[ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension];
	if (common == null) {
		common = [];
	}
	else if (AjxUtil.isString(common)) {
		common = [ common ];
	}
	var commonMap = {};
	var unaddedBlockExt = [];
	for (var i = 0; i < common.length; i++) {
		var ext = common[i];
		common[i] = new String(ext);
		common[i].id = "id_"+ext;
		commonMap[ext] = common[i];
		
		if (ZaUtil.findValueInArray(blocked, ext) <= -1) {
			DBG.println(AjxDebug.DBG1, ext + " was added to the blocked list.");
			//common.splice(i,1) ;
			unaddedBlockExt.push(common[i]);
		}
	}
	this.attrs[ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension] = unaddedBlockExt;
		
	for (var i = 0; i < blocked.length; i++) {
		var ext = blocked[i];
		if (commonMap[ext]) {
			blocked[i] = commonMap[ext];
		}
		else {
			blocked[i] = new String(ext);
			blocked[i].id = "id_"+ext;
		}
	}
	this.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] = blocked;

	// convert available components to hidden fields for xform binding
	var components = this.attrs[ZaGlobalConfig.A_zimbraComponentAvailable];
	if (components) {
		if (AjxUtil.isString(components)) {
			components = [ components ];
		}
		for (var i = 0; i < components.length; i++) {
			var component = components[i];
			this.attrs["_"+ZaGlobalConfig.A_zimbraComponentAvailable+"_"+component] = true;
		}
	}
	
	// convert restrictions to hidden fields for xform binding
	var restrictions = this.attrs[ZaGlobalConfig.A_zimbraMtaRestriction];
	if (restrictions) {
		if (AjxUtil.isString(restrictions)) {
			restrictions = [ restrictions ];
		}
		for (var i = 0; i < restrictions.length; i++) {
			var restriction = restrictions[i];
			this.attrs["_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_"+restriction] = true;
		}
	}
	if(this.attrs[ZaGlobalConfig.A_zimbraInstalledSkin] != null && !(this.attrs[ZaGlobalConfig.A_zimbraInstalledSkin] instanceof Array)) {
		this.attrs[ZaGlobalConfig.A_zimbraInstalledSkin] = [this.attrs[ZaGlobalConfig.A_zimbraInstalledSkin]];
	}
}

ZaGlobalConfig.prototype.modify = 
function (mods) {
	var soapDoc = AjxSoapDoc.create("ModifyConfigRequest", "urn:zimbraAdmin", null);
	for (var aname in mods) {
		//multy value attribute
		if(mods[aname] instanceof Array) {
			var cnt = mods[aname].length;
			if(cnt > 0) {
				for(var ix=0; ix <cnt; ix++) {
					if(mods[aname][ix] instanceof String)
						var attr = soapDoc.set("a", mods[aname][ix].toString());
					else if(mods[aname][ix] instanceof Object)
						var attr = soapDoc.set("a", mods[aname][ix].toString());
					else 
						var attr = soapDoc.set("a", mods[aname][ix]);
						
					attr.setAttribute("n", aname);
				}
			} 
			else {
				var attr = soapDoc.set("a");
				attr.setAttribute("n", aname);
			}
		} else {
			//bug fix 10354: ingnore the changed ZaLicense Properties
			if ((typeof ZaLicense == "function") && (ZaSettings.LICENSE_ENABLED)){
				if (ZaUtil.findValueInObjArrByPropertyName (ZaLicense.myXModel.items, aname, "id") > -1 ){
					continue ;
				}
			}
			var attr = soapDoc.set("a", mods[aname]);
			attr.setAttribute("n", aname);
		}
	}
	var command = new ZmCsfeCommand();
	var params = new Object();
	params.soapDoc = soapDoc;	
	command.invoke(params);
	var newConfig = this._app.getGlobalConfig(true);
	if(newConfig.attrs) {
		for (var aname in newConfig.attrs) {
			this.attrs[aname] = newConfig.attrs[aname];
		}
	}
}

// REVISIT: Move to a common location if needed by others
LifetimeNumber_XFormItem = function() {}
XModelItemFactory.createItemType("_LIFETIME_NUMBER_", "lifetime_number", LifetimeNumber_XFormItem, Number_XModelItem);
LifetimeNumber_XFormItem.prototype.validateType = function(value) {
	// strip off lifetime char (e.g. 's', 'h', 'd', ...)
	var number = value.substring(0, value.length - 1);
	this.validateNumber(number);
	return value;
}

ZaGlobalConfig.myXModel = {
	/*getRelayHost: function (model, instance) {
		if(instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost]) {
			var parts = instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost].split(":");
			if(parts && parts.length)
				return parts[0];
			else
				return instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost];
		} else return "";
	},
	getRelayPort: function (model, instance) {
		if(instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost]) {
			var parts = instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost].split(":");
			if(parts && parts.length >1)
				return parts[1];
			else
				return "";
		} else return "";
	},	
	setRelayPort:function(value, instance, parentValue, ref) {
		instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayPortInternal] = value;
		instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost] = instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayHostInternal] + ":" + instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayPortInternal];
	},
	setRelayHost:function(value, instance, parentValue, ref) {
		instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayHostInternal] = value;
		instance.attrs[ZaGlobalConfig.A_zimbraMtaRelayHost] = instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayHostInternal] + ":" + instance.attrsInternal[ZaGlobalConfig.A_zimbraMtaRelayPortInternal];
	},*/
	items:[
	  	// ...other...
		{ id:ZaGlobalConfig.A_zimbraGalMaxResults, ref:"attrs/" + ZaGlobalConfig.A_zimbraGalMaxResults , type:_NUMBER_, minInclusive: 0 },
		{ id:ZaGlobalConfig.A_zimbraDefaultDomainName, ref:"attrs/" + ZaGlobalConfig.A_zimbraDefaultDomainName, type:_STRING_, maxLength: 256},
		{ id:ZaGlobalConfig.A_zimbraDataSourceNumThreads, ref:"attrs/" + ZaGlobalConfig.A_zimbraDataSourceNumThreads , type:_NUMBER_, minInclusive: 1 },
		{ id:ZaGlobalConfig.A_currentMonitorHost, ref: "attrs/"+ZaGlobalConfig.A_currentMonitorHost, type: _STRING_ },
		// attachments
		{ id:ZaGlobalConfig.A_zimbraAttachmentsBlocked, ref:"attrs/" + ZaGlobalConfig.A_zimbraAttachmentsBlocked, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},

		{ id:ZaGlobalConfig.A_zimbraMtaBlockedExtension, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaBlockedExtension, type: _LIST_, dataType: _STRING_ },
		{ id:ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension, type: _LIST_, dataType: _STRING_ },
		// MTA
		{ id:ZaGlobalConfig.A_zimbraMtaAuthEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaAuthEnabled, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		{ id:ZaGlobalConfig.A_zimbraMtaTlsAuthOnly, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaTlsAuthOnly, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		{ id:ZaGlobalConfig.A_zimbraSmtpHostname, ref:"attrs/" + ZaGlobalConfig.A_zimbraSmtpHostname, type:_HOSTNAME_OR_IP_, maxLength: 256 },
		{ id:ZaGlobalConfig.A_zimbraSmtpPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraSmtpPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraMtaMaxMessageSize, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaMaxMessageSize, type: _FILE_SIZE_, units: AjxUtil.SIZE_KILOBYTES, required: true },
		//{ id:ZaGlobalConfig.A_zimbraFileUploadMaxSize, ref:"attrs/" + ZaGlobalConfig.A_zimbraFileUploadMaxSize, type: _FILE_SIZE_, units: AjxUtil.SIZE_KILOBYTES, required: true },		
		{ id:ZaGlobalConfig.A_zimbraMtaRelayHost, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRelayHost, type: _HOSTNAME_OR_IP_, maxLength: 256 },
		{ id:ZaGlobalConfig.A_zimbraMtaMyNetworks, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaMyNetworks, type: _STRING_, maxLength: 256 },
		{ id:ZaGlobalConfig.A_zimbraSmtpSendAddOriginatingIP, ref: "attrs/" + ZaGlobalConfig.A_zimbraSmtpSendAddOriginatingIP, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		
//		{ id:ZaGlobalConfig.A_zimbraMtaRelayHostInternal, setterScope:_MODEL_,getterScope:_MODEL_, getter:"getRelayHost", setter:"setRelayHost", ref:"attrsInternal/" + ZaGlobalConfig.A_zimbraMtaRelayHostInternal, type: _HOSTNAME_OR_IP_, maxLength: 256 },		
//		{ id:ZaGlobalConfig.A_zimbraMtaRelayPortInternal,setterScope:_MODEL_,getterScope:_MODEL_, getter:"getRelayPort", setter:"setRelayPort", ref:"attrsInternal/" + ZaGlobalConfig.A_zimbraMtaRelayPortInternal, type: _PORT_},				
		{ id:ZaGlobalConfig.A_zimbraMtaDnsLookupsEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaDnsLookupsEnabled, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		// -- protocol checks
		{ id:ZaGlobalConfig.A_zimbraMtaRejectInvalidHostname, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectInvalidHostname, type: _ENUM_, choices: [false,true] },
		{ id:ZaGlobalConfig.A_zimbraMtaRejectNonFqdnHostname, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectNonFqdnHostname, type: _ENUM_, choices: [false,true] },
		{ id:ZaGlobalConfig.A_zimbraMtaRejectNonFqdnSender, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectNonFqdnSender, type: _ENUM_, choices: [false,true] },
		// -- dns checks
		{ id:ZaGlobalConfig.A_zimbraMtaRejectUnknownClient, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectUnknownClient, type: _ENUM_, choices: [false,true] },
		{ id:ZaGlobalConfig.A_zimbraMtaRejectUnknownHostname, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectUnknownHostname, type: _ENUM_, choices: [false,true] },
		{ id:ZaGlobalConfig.A_zimbraMtaRejectUnknownSenderDomain, ref:"attrs/" + ZaGlobalConfig.A_zimbraMtaRejectUnknownSenderDomain, type: _ENUM_, choices: [false,true] },
		// smtp
		{ id:ZaGlobalConfig.A_zimbraSmtpTimeout, ref:"attrs/" + ZaGlobalConfig.A_zimbraSmtpTimeout, type:_NUMBER_, minInclusive: 0 },
		// pop
		{ id:ZaGlobalConfig.A_zimbraPop3ServerEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3ServerEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		{ id:ZaGlobalConfig.A_zimbraPop3SSLServerEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3SSLServerEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{ id:ZaGlobalConfig.A_zimbraPop3CleartextLoginEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3CleartextLoginEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},				
		{ id:ZaGlobalConfig.A_zimbraPop3BindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3BindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraPop3SSLBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3SSLBindPort, type:_PORT_ },
		// imap
		{ id:ZaGlobalConfig.A_zimbraImapServerEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapServerEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},						
		{ id:ZaGlobalConfig.A_zimbraImapSSLServerEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapSSLServerEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},								
		{ id:ZaGlobalConfig.A_zimbraImapCleartextLoginEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapCleartextLoginEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},										
		{ id:ZaGlobalConfig.A_zimbraImapBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraImapSSLBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapSSLBindPort, type:_PORT_ },
		// anti-spam
	  	{ id:ZaGlobalConfig.A_zimbraSpamCheckEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraSpamCheckEnabled, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
	  	{ id:ZaGlobalConfig.A_zimbraSpamKillPercent, ref:"attrs/" + ZaGlobalConfig.A_zimbraSpamKillPercent, type: _PERCENT_, fractionDigits: 0 },
	  	{ id:ZaGlobalConfig.A_zimbraSpamTagPercent, ref:"attrs/" + ZaGlobalConfig.A_zimbraSpamTagPercent, type: _PERCENT_, fractionDigits: 0 },
	  	{ id:ZaGlobalConfig.A_zimbraSpamSubjectTag, ref:"attrs/" + ZaGlobalConfig.A_zimbraSpamSubjectTag, type: _STRING_, whiteSpace: 'collapse', maxLength: 32 },
	  	// anti-virus
	  	{ id:ZaGlobalConfig.A_zimbraVirusCheckEnabled, ref:"attrs/" + ZaGlobalConfig.A_zimbraVirusCheckEnabled, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
	  	{ id:ZaGlobalConfig.A_zimbraVirusDefinitionsUpdateFrequency, ref:"attrs/" + ZaGlobalConfig.A_zimbraVirusDefinitionsUpdateFrequency, type: _LIFETIME_NUMBER_, minInclusive: 0, fractionDigits: 0 },
	  	{ id:ZaGlobalConfig.A_zimbraVirusBlockEncryptedArchive, ref:"attrs/" + ZaGlobalConfig.A_zimbraVirusBlockEncryptedArchive, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES},
	  	{ id:ZaGlobalConfig.A_zimbraVirusWarnAdmin, ref:"attrs/" + ZaGlobalConfig.A_zimbraVirusWarnAdmin, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES},
	  	{ id:ZaGlobalConfig.A_zimbraVirusWarnRecipient, ref:"attrs/" + ZaGlobalConfig.A_zimbraVirusWarnRecipient, type: _ENUM_, choices: ZaModel.BOOLEAN_CHOICES},
	  	//proxy
		{ id:ZaGlobalConfig.A_zimbraImapProxyBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapProxyBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraImapSSLProxyBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraImapSSLProxyBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraPop3ProxyBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3ProxyBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraPop3SSLProxyBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraPop3SSLProxyBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraLmtpBindPort, ref:"attrs/" + ZaGlobalConfig.A_zimbraLmtpBindPort, type:_PORT_ },
		{ id:ZaGlobalConfig.A_zimbraLmtpNumThreads, ref:"attrs/" + ZaGlobalConfig.A_zimbraLmtpNumThreads, type:_PORT_ },		
		{ id:ZaGlobalConfig.A_zimbraInstalledSkin, ref:"attrs/" + ZaGlobalConfig.A_zimbraInstalledSkin, type:_LIST_, listItem:{type:_STRING_}}

	]	
}
