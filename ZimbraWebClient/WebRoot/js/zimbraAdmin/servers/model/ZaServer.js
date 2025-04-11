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
* @class ZaServer
* This class represents zimbraServer objects. ZaServer extends ZaItem
* @author Greg Solovyev
* @contructor ZaServer
* @param app reference to the application instance
**/
function ZaServer(app) {
	ZaItem.call(this, app);
	this.attrs = new Object();
	this.hsm = new Object();
	this.hsm.pollInterval = 500;
	this.id = "";
	this.name="";
}

ZaServer.prototype = new ZaItem;
ZaServer.prototype.constructor = ZaServer;
ZaServer.prototype.toString = function() {
	return this.name;
}

//attribute name constants, this values are taken from zimbra.schema
ZaServer.A_name = "cn";
ZaServer.A_description = "description";
ZaServer.A_notes = "zimbraNotes";
ZaServer.A_Service = "zimbraService";
ZaServer.A_ServiceHostname = "zimbraServiceHostname";
ZaServer.A_zimbraMailPort = "zimbraMailPort";
ZaServer.A_zimbraMailSSLPort = "zimbraMailSSLPort";
// services
ZaServer.A_zimbraServiceInstalled = "zimbraServiceInstalled";
ZaServer.A_zimbraLdapServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_ldap";
ZaServer.A_zimbraMailboxServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_mailbox";
ZaServer.A_zimbraMtaServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_mta";
ZaServer.A_zimbraSnmpServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_snmp";
ZaServer.A_zimbraAntiVirusServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_antivirus";
ZaServer.A_zimbraAntiSpamServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_antispam";
ZaServer.A_zimbraSpellServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_spell";
ZaServer.A_zimbraLoggerServiceInstalled = "_"+ZaServer.A_zimbraServiceInstalled+"_logger";
ZaServer.A_zimbraServiceEnabled = "zimbraServiceEnabled";
ZaServer.A_zimbraLdapServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_ldap";
ZaServer.A_zimbraMailboxServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_mailbox";
ZaServer.A_zimbraMtaServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_mta";
ZaServer.A_zimbraSnmpServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_snmp";
ZaServer.A_zimbraAntiVirusServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_antivirus";
ZaServer.A_zimbraAntiSpamServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_antispam";
ZaServer.A_zimbraSpellServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_spell";
ZaServer.A_zimbraLoggerServiceEnabled = "_"+ZaServer.A_zimbraServiceEnabled+"_logger";
// MTA
ZaServer.A_zimbraMtaAuthEnabled = "zimbraMtaAuthEnabled";
ZaServer.A_zimbraMtaDnsLookupsEnabled = "zimbraMtaDnsLookupsEnabled";
ZaServer.A_zimbraMtaRelayHost = "zimbraMtaRelayHost";
ZaServer.A_zimbraMtaTlsAuthOnly = "zimbraMtaTlsAuthOnly";
//smtp
ZaServer.A_SmtpHostname  = "zimbraSmtpHostname";
ZaServer.A_SmtpPort = "zimbraSmtpPort";
ZaServer.A_SmtpTimeout = "zimbraSmtpTimeout";
//Lmtp
ZaServer.A_LmtpAdvertisedName = "zimbraLmtpAdvertisedName";
ZaServer.A_LmtpBindAddress = "zimbraLmtpBindAddress";
ZaServer.A_LmtpBindPort = "zimbraLmtpBindPort";
ZaServer.A_LmtpNumThreads = "zimbraLmtpNumThreads";
//pop3
ZaServer.A_Pop3NumThreads = "zimbraPop3NumThreads";
ZaServer.A_Pop3AdvertisedName ="zimbraPop3AdvertisedName";
ZaServer.A_Pop3BindAddress = "zimbraPop3BindAddress";
ZaServer.A_Pop3BindPort = "zimbraPop3BindPort";
ZaServer.A_Pop3SSLBindPort = "zimbraPop3SSLBindPort";
ZaServer.A_Pop3SSLServerEnabled = "zimbraPop3SSLServerEnabled";
ZaServer.A_Pop3ServerEnabled = "zimbraPop3ServerEnabled"
ZaServer.A_Pop3CleartextLoginEnabled = "zimbraPop3CleartextLoginEnabled";
//imap
ZaServer.A_zimbraImapNumThreads="zimbraImapNumThreads";
ZaServer.A_ImapBindPort="zimbraImapBindPort";
ZaServer.A_ImapServerEnabled="zimbraImapServerEnabled";
ZaServer.A_ImapSSLBindPort="zimbraImapSSLBindPort";
ZaServer.A_ImapSSLServerEnabled="zimbraImapSSLServerEnabled";
ZaServer.A_ImapCleartextLoginEnabled="zimbraImapCleartextLoginEnabled";

//redo log
ZaServer.A_RedologEnabled = "zimbraRedologEnabled";
ZaServer.A_RedologLogPath = "zimbraRedologLogPath";
ZaServer.A_RedologArchiveDir = "zimbraRedologArchiveDir";
ZaServer.A_RedologBacklogDir = "zimbraRedologBacklogDir";
ZaServer.A_RedologRolloverFileSizeKB = "zimbraRedologRolloverFileSizeKB";
ZaServer.A_RedologFsyncIntervalMS = "zimbraRedologFsyncIntervalMS";
//master role settings
ZaServer.A_MasterRedologClientConnections = "zimbraMasterRedologClientConnections";
ZaServer.A_MasterRedologClientTimeoutSec = "zimbraMasterRedologClientTimeoutSec";
ZaServer.A_MasterRedologClientTcpNoDelay = "zimbraMasterRedologClientTcpNoDelay";
//slave role settings
ZaServer.A_zimbraUserServicesEnabled = "zimbraUserServicesEnabled";

//Volume Management
ZaServer.A_RemovedVolumes = "removed_volumes";
ZaServer.A_Volumes = "volumes";
ZaServer.A_VolumeId = "id";
ZaServer.A_VolumeName = "name";
ZaServer.A_VolumeRootPath = "rootpath";
ZaServer.A_VolumeCompressBlobs = "compressBlobs";
ZaServer.A_VolumeCompressionThreshold = "compressionThreshold";
ZaServer.A_VolumeType = "type";
ZaServer.A_CurrentPrimaryMsgVolumeId = "current_pri_msg_volume_id";
ZaServer.A_CurrentSecondaryMsgVolumeId = "current_sec_msg_volume_id";
ZaServer.A_CurrentIndexMsgVolumeId = "current_index_volume_id";
//HSM
ZaServer.A_zimbraHsmAge = "zimbraHsmAge";
ZaServer.A_HSMstartDate = "startDate";
ZaServer.A_HSMendDate = "endDate";
ZaServer.A_HSMrunning = "running";
ZaServer.A_HSMwasAborted = "aborted";
ZaServer.A_HSMaborting = "aborting";
ZaServer.A_HSMerror = "error";
ZaServer.A_HSMnumBlobsMoved = "numBlobsMoved";
ZaServer.A_HSMnumMailboxes = "numMailboxes";
ZaServer.A_HSMtotalMailboxes = "totalMailboxes";
ZaServer.A_HSMthreshold = "threshold";
ZaServer.A_HSMremainingMailboxes = "remainingMailboxes"
// other
ZaServer.A_zimbraIsMonitorHost = "zimbraIsMonitorHost";
ZaServer.A_showVolumeAndHSM = "show_volume_and_hsm"; //this attribute is immutable

ZaServer.STANDALONE = "standalone";
ZaServer.MASTER = "master";
ZaServer.SLAVE = "slave";

ZaServer.PRI_MSG = 1;
ZaServer.SEC_MSG = 2;
ZaServer.INDEX = 10;
ZaServer.volumeTypeChoicesAll = new XFormChoices({1:ZaMsg.NAD_HSM_PrimaryMsg, 2:ZaMsg.NAD_HSM_SecMsg, 10:ZaMsg.NAD_HSM_Index}, XFormChoices.HASH);
ZaServer.volumeTypeChoicesNoHSM = new XFormChoices({1:ZaMsg.NAD_HSM_Msg, 10:ZaMsg.NAD_HSM_Index}, XFormChoices.HASH);
ZaServer.volumeTypeChoicesHSM = new XFormChoices({1:ZaMsg.NAD_HSM_PrimaryMsg, 2:ZaMsg.NAD_HSM_SecMsg}, XFormChoices.HASH);
ZaServer.HSM_StatusChoices = {0:ZaMsg.NAD_HSM_Idle,1:ZaMsg.NAD_HSM_Running};
		
ZaServer.myXModel = {
	items: [
		{id:ZaItem.A_zimbraId, type:_STRING_, ref:"attrs/" + ZaItem.A_zimbraId},
		{id:ZaServer.A_name, ref:"attrs/" + ZaServer.A_name, type:_STRING_},
		{id:ZaServer.A_description, ref:"attrs/" +  ZaServer.A_description, type:_STRING_},
		{id:ZaServer.A_notes, ref:"attrs/" +  ZaServer.A_notes, type:_STRING_},		
		{id:ZaServer.A_Service, ref:"attrs/" +  ZaServer.A_Service, type:_STRING_, maxLength: 256 },
		{id:ZaServer.A_ServiceHostname, ref:"attrs/" +  ZaServer.A_ServiceHostname, type:_HOSTNAME_OR_IP_, maxLength: 256 },
		// Services
		{id:ZaServer.A_zimbraLdapServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraLdapServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraMailboxServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraMailboxServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraMtaServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraMtaServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraSnmpServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraSnmpServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraAntiVirusServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraAntiVirusServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraAntiSpamServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraAntiSpamServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraSpellServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraSpellServiceEnabled, type: _ENUM_, choices: [false,true] },
		{id:ZaServer.A_zimbraLoggerServiceEnabled, ref:"attrs/"+ZaServer.A_zimbraLoggerServiceEnabled, type: _ENUM_, choices: [false,true] },
		// MTA
		{id:ZaServer.A_zimbraMtaAuthEnabled, ref:"attrs/" +  ZaServer.A_zimbraMtaAuthEnabled, type: _COS_ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		{id:ZaServer.A_zimbraMtaTlsAuthOnly, ref:"attrs/" +  ZaServer.A_zimbraMtaTlsAuthOnly, type: _COS_ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		{id:ZaServer.A_zimbraMtaRelayHost, ref:"attrs/" +  ZaServer.A_zimbraMtaRelayHost, type: _HOSTNAME_OR_IP_, maxLength: 256 },
		{id:ZaServer.A_zimbraMtaDnsLookupsEnabled, ref:"attrs/" +  ZaServer.A_zimbraMtaDnsLookupsEnabled, type: _COS_ENUM_, choices: ZaModel.BOOLEAN_CHOICES },
		// ...other...
		{id:ZaServer.A_SmtpHostname, ref:"attrs/" +  ZaServer.A_SmtpHostname, type:_COS_HOSTNAME_OR_IP_, maxLength: 256 },
		{id:ZaServer.A_SmtpPort, ref:"attrs/" +  ZaServer.A_SmtpPort, type:_COS_PORT_},
		{id:ZaServer.A_SmtpTimeout, ref:"attrs/" + ZaServer.A_SmtpTimeout, type:_NUMBER_, minInclusive: 0 },
		{id:ZaServer.A_LmtpAdvertisedName, ref:"attrs/" +  ZaServer.A_LmtpAdvertisedName, type:_STRING_, maxLength: 128 },
		{id:ZaServer.A_LmtpBindAddress, ref:"attrs/" +  ZaServer.A_LmtpBindAddress, type:_HOSTNAME_OR_IP_, maxLength: 256 },
		{id:ZaServer.A_LmtpBindPort, ref:"attrs/" +  ZaServer.A_LmtpBindPort, type:_PORT_},		
		{id:ZaServer.A_LmtpNumThreads, ref:"attrs/" +  ZaServer.A_LmtpNumThreads, type:_NUMBER_, minInclusive: 0 },
		{id:ZaServer.A_Pop3NumThreads, ref:"attrs/" +  ZaServer.A_Pop3NumThreads, type:_NUMBER_, minInclusive: 0 },		
		{id:ZaServer.A_zimbraImapNumThreads, ref:"attrs/" +  ZaServer.A_zimbraImapNumThreads, type:_NUMBER_, minInclusive: 0 },		
		{id:ZaServer.A_Pop3AdvertisedName, ref:"attrs/" +  ZaServer.A_Pop3AdvertisedName, type:_STRING_, maxLength: 128 },
		{id:ZaServer.A_Pop3BindAddress, ref:"attrs/" +  ZaServer.A_Pop3BindAddress, type:_HOSTNAME_OR_IP_, maxLength: 128 },
		{id:ZaServer.A_Pop3AdvertisedName, ref:"attrs/" +  ZaServer.A_Pop3AdvertisedName, type:_STRING_, maxLength: 128 },
		{id:ZaServer.A_Pop3BindAddress, ref:"attrs/" +  ZaServer.A_Pop3BindAddress, type:_HOSTNAME_OR_IP_, maxLength: 128 },
		{id:ZaServer.A_Pop3BindPort, ref:"attrs/" +  ZaServer.A_Pop3BindPort, type:_PORT_ },
		{id:ZaServer.A_Pop3SSLBindPort, ref:"attrs/" +  ZaServer.A_Pop3SSLBindPort, type:_PORT_ },
		{id:ZaServer.A_Pop3SSLServerEnabled, ref:"attrs/" + ZaServer.A_Pop3SSLServerEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_Pop3ServerEnabled, ref:"attrs/" + ZaServer.A_Pop3ServerEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_Pop3CleartextLoginEnabled, ref:"attrs/" + ZaServer.A_Pop3CleartextLoginEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_ImapBindPort, ref:"attrs/" + ZaServer.A_ImapBindPort, type:_PORT_ },
		{id:ZaServer.A_ImapServerEnabled, ref:"attrs/" + ZaServer.A_ImapServerEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_ImapSSLBindPort, ref:"attrs/" + ZaServer.A_ImapSSLBindPort, type:_PORT_ },
		{id:ZaServer.A_ImapSSLServerEnabled, ref:"attrs/" + ZaServer.A_ImapSSLServerEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_ImapCleartextLoginEnabled, ref:"attrs/" + ZaServer.A_ImapCleartextLoginEnabled, type:_COS_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_RedologEnabled, ref:"attrs/" + ZaServer.A_RedologEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},		
		{id:ZaServer.A_RedologLogPath, ref:"attrs/" + ZaServer.A_RedologLogPath, type:_STRING_},		
		{id:ZaServer.A_RedologArchiveDir, ref:"attrs/" + ZaServer.A_RedologArchiveDir, type:_STRING_},		
		{id:ZaServer.A_RedologBacklogDir, ref:"attrs/" + ZaServer.A_RedologBacklogDir, type:_STRING_},		
		{id:ZaServer.A_RedologRolloverFileSizeKB, ref:"attrs/" + ZaServer.A_RedologRolloverFileSizeKB, type:_NUMBER_, minInclusive: 0 },
		{id:ZaServer.A_RedologFsyncIntervalMS, ref:"attrs/" + ZaServer.A_RedologFsyncIntervalMS, type:_NUMBER_, minInclusive: 0 },
		{id:ZaServer.A_MasterRedologClientConnections, ref:"attrs/" + ZaServer.A_MasterRedologClientConnections, type:_STRING_},		
		{id:ZaServer.A_MasterRedologClientTimeoutSec, ref:"attrs/" + ZaServer.A_MasterRedologClientTimeoutSec, type:_STRING_},		
		{id:ZaServer.A_MasterRedologClientTcpNoDelay, ref:"attrs/" + ZaServer.A_MasterRedologClientTcpNoDelay, type:_STRING_},		
		{id:ZaServer.A_zimbraUserServicesEnabled, ref:"attrs/" + ZaServer.A_zimbraUserServicesEnabled, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		{id:ZaServer.A_Volumes, type:_LIST_, listItem:
			{type:_OBJECT_,
				items: [
					{id:ZaServer.A_VolumeId, type:_NUMBER_},
					{id:ZaServer.A_VolumeName, type:_STRING_},
					{id:ZaServer.A_VolumeType, type:_ENUM_, choices:[ZaServer.PRI_MSG,ZaServer.SEC_MSG,ZaServer.INDEX],defaultValue:ZaServer.PRI_MSG},
					{id:ZaServer.A_VolumeRootPath, type:_STRING_},
					{id:ZaServer.A_VolumeCompressBlobs, type:_ENUM_, choices:[0,1], defaultValue:1},
					{id:ZaServer.A_VolumeCompressionThreshold, type:_NUMBER_,defaultValue:4096}				
				]
			}
		},
		{id:ZaServer.A_zimbraHsmAge, ref:"attrs/" + ZaServer.A_zimbraHsmAge, type:_COS_MLIFETIME_},
		{id:ZaServer.A_showVolumeAndHSM, ref:ZaServer.A_showVolumeAndHSM, type: _ENUM_, choices: [false,true]},
		{id:ZaServer.A_HSMstartDate, ref:"hsm/" + ZaServer.A_HSMstartDate},						
		{id:ZaServer.A_HSMendDate, ref:"hsm/" + ZaServer.A_HSMendDate},								
		{id:ZaServer.A_HSMrunning, ref:"hsm/" + ZaServer.A_HSMrunning, type:_NUMBER_},						
		{id:ZaServer.A_HSMwasAborted, ref:"hsm/" + ZaServer.A_HSMwasAborted, type:_NUMBER_},				
		{id:ZaServer.A_HSMaborting, ref:"hsm/" + ZaServer.A_HSMaborting, type:_ENUM_, choices:[false,true]},						
		{id:ZaServer.A_HSMerror, ref:"hsm/" + ZaServer.A_HSMerror, type:_STRING_},
		{id:ZaServer.A_HSMnumBlobsMoved, ref:"hsm/" + ZaServer.A_HSMnumBlobsMoved, type:_NUMBER_},
		{id:ZaServer.A_HSMnumMailboxes, ref:"hsm/" + ZaServer.A_HSMnumMailboxes, type:_NUMBER_},
		{id:ZaServer.A_HSMtotalMailboxes, ref:"hsm/" + ZaServer.A_HSMtotalMailboxes, type:_NUMBER_},				
		{id:ZaServer.A_HSMthreshold, ref:"hsm/" + ZaServer.A_HSMthreshold, type:_NUMBER_},
		{id:ZaServer.A_HSMremainingMailboxes, ref:"hsm/" + ZaServer.A_HSMremainingMailboxes, type:_NUMBER_},
		{id:"errorMsg", ref:"hsm/errorMsg", type:_STRING_},
		{id:"progressMsg", ref:"hsm/progressMsg", type:_STRING_}		
	]
};
		
ZaServer.getAll =
function(app) {
	var soapDoc = AjxSoapDoc.create("GetAllServersRequest", "urn:zimbraAdmin", null);	
	var resp = ZmCsfeCommand.invoke(soapDoc, null, null, null, true).firstChild;
	var list = new ZaItemList(ZaServer, app);
	list.loadFromDom(resp);
//	list.sortByName();		
	return list;
}

/**
* @param mods - map of modified attributes
* modifies object's information in the database
**/
ZaServer.prototype.modify =
function(mods) {
	var soapDoc = AjxSoapDoc.create("ModifyServerRequest", "urn:zimbraAdmin", null);
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
	var targetId = null;
	if(this.attrs[ZaServer.A_zimbraMailboxServiceEnabled]) {
		targetId = this.id;
	}
	var resp = ZmCsfeCommand.invoke(soapDoc, false, null, targetId, true).firstChild;
	//update itseld
	this.initFromDom(resp.firstChild);
}

/**
* Returns HTML for a tool tip for this domain.
*/
ZaServer.prototype.getToolTip =
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
		html[idx++] = AjxImg.getImageHtml("Server");		
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

ZaServer.prototype.remove = 
function() {
	var soapDoc = AjxSoapDoc.create("DeleteServerRequest", "urn:zimbraAdmin", null);
	soapDoc.set("id", this.id);
	ZmCsfeCommand.invoke(soapDoc, null, null, null, true);	
}

ZaServer.prototype.refresh = 
function() {
	this.load();	
}

ZaServer.prototype.load = 
function(by, val, withConfig) {
	var _by = by ? by : "id";
	var _val = val ? val : this.id

		
	var soapDoc = AjxSoapDoc.create("GetServerRequest", "urn:zimbraAdmin", null);
	if(withConfig) {
		soapDoc.getMethod().setAttribute("applyConfig", "1");	
	} else {
		soapDoc.getMethod().setAttribute("applyConfig", "0");		
	}
	var elBy = soapDoc.set("server", _val);
	elBy.setAttribute("by", _by);
	var resp = ZmCsfeCommand.invoke(soapDoc, null, null, null, true).firstChild;
	this.initFromDom(resp.firstChild);
	this.cos = this._app.getGlobalConfig();

	if(this.attrs[ZaServer.A_zimbraMailboxServiceEnabled]) {
		this.getMyVolumes();
		this.getCurrentVolumes();
		if(this.cos.attrs[ZaGlobalConfig.A_zimbraComponentAvailable_HSM]) {
			var statusResp = ZaServer.getHSMStatus(this.id);
			if(statusResp instanceof AjxException || statusResp instanceof ZmCsfeException || statusResp instanceof AjxSoapException) {
				this._app.getCurrentController()._handleException(statusResp);
				return;
			}
			ZaServer.parseHSMStatusResponse(statusResp,this);
		}
	}
}

ZaServer.prototype.initFromDom = function(node) {
	ZaItem.prototype.initFromDom.call(this, node);
	
	// convert installed/enabled services to hidden fields for xform binding
	var installed = this.attrs[ZaServer.A_zimbraServiceInstalled];
	if (installed) {
		if (AjxUtil.isString(installed)) {
			installed = [ installed ];
		}
		for (var i = 0; i < installed.length; i++) {
			var service = installed[i];
			this.attrs["_"+ZaServer.A_zimbraServiceInstalled+"_"+service] = true;
			this.attrs["_"+ZaServer.A_zimbraServiceEnabled+"_"+service] = false;
		}
	}
	
	var enabled = this.attrs[ZaServer.A_zimbraServiceEnabled];
	if (enabled) {
		if (AjxUtil.isString(enabled)) {
			enabled = [ enabled ];
		}
		for (var i = 0; i < enabled.length; i++) {
			var service = enabled[i];
			this.attrs["_"+ZaServer.A_zimbraServiceEnabled+"_"+service] = true;
		}
	}
	this[ZaServer.A_ServiceHostname] = this.attrs[ZaServer.A_ServiceHostname]; // a hack for New Account Wizard	
	this[ZaServer.A_showVolumeAndHSM] = this.attrs[ZaServer.A_zimbraMailboxServiceEnabled];

}

ZaServer.prototype.getCurrentVolumes =
function () {
	if(!this.id)
		return;
	var soapDoc = AjxSoapDoc.create("GetCurrentVolumesRequest", "urn:zimbraAdmin", null);
	//find out which server I am on
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;	
	
	var children = respNode.childNodes;
	for (var i=0; i< children.length;  i++) {
		var child = children[i];
		if(child.nodeName == 'volume') {
			if(child.getAttribute(ZaServer.A_VolumeType) == ZaServer.PRI_MSG) {
				this[ZaServer.A_CurrentPrimaryMsgVolumeId] =  child.getAttribute(ZaServer.A_VolumeId);
			} else if (child.getAttribute(ZaServer.A_VolumeType) == ZaServer.SEC_MSG) {
				this[ZaServer.A_CurrentSecondaryMsgVolumeId] =  child.getAttribute(ZaServer.A_VolumeId);			
			} else if (child.getAttribute(ZaServer.A_VolumeType) == ZaServer.INDEX) {
				this[ZaServer.A_CurrentIndexMsgVolumeId] =  child.getAttribute(ZaServer.A_VolumeId);						
			}
		}
	}
}
ZaServer.prototype.getMyVolumes = 
function() {
	this[ZaServer.A_Volumes] = new Array();
	if(!this.id)
		return;
	var soapDoc = AjxSoapDoc.create("GetAllVolumesRequest", "urn:zimbraAdmin", null);
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;	

	var children = respNode.childNodes;
	for (var i=0; i< children.length;  i++) {
		var child = children[i];
		if(child.nodeName == 'volume') {
			var volume = new Object();		
			volume[ZaServer.A_VolumeId] = child.getAttribute(ZaServer.A_VolumeId);
			volume[ZaServer.A_VolumeName] = child.getAttribute(ZaServer.A_VolumeName);
			volume[ZaServer.A_VolumeRootPath] = child.getAttribute(ZaServer.A_VolumeRootPath);			
			volume[ZaServer.A_VolumeCompressBlobs] = child.getAttribute(ZaServer.A_VolumeCompressBlobs);
			volume[ZaServer.A_VolumeCompressionThreshold] = child.getAttribute(ZaServer.A_VolumeCompressionThreshold);			
			volume[ZaServer.A_VolumeType] = child.getAttribute(ZaServer.A_VolumeType);						
			this[ZaServer.A_Volumes].push(volume);
		}
	}
}

ZaServer.prototype.deleteVolume =
function (id) {
	if(!id)
		return false;
		
	var soapDoc = AjxSoapDoc.create("DeleteVolumeRequest", "urn:zimbraAdmin", null);		
	soapDoc.getMethod().setAttribute(ZaServer.A_VolumeId, id);	
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;		
}

ZaServer.prototype.createVolume =
function (volume) {
	if(!volume)
		return false;
	var soapDoc = AjxSoapDoc.create("CreateVolumeRequest", "urn:zimbraAdmin", null);		
	var elVolume = soapDoc.set("volume", null);
	elVolume.setAttribute("type", volume[ZaServer.A_VolumeType]);
	elVolume.setAttribute("name", volume[ZaServer.A_VolumeName]);	
	elVolume.setAttribute("rootpath", volume[ZaServer.A_VolumeRootPath]);		
	elVolume.setAttribute("compressBlobs", volume[ZaServer.A_VolumeCompressBlobs]);		
	elVolume.setAttribute("compressionThreshold", volume[ZaServer.A_VolumeCompressionThreshold]);			
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;		
}

ZaServer.prototype.modifyVolume =
function (volume) {
	if(!volume)
		return false;
	var soapDoc = AjxSoapDoc.create("ModifyVolumeRequest", "urn:zimbraAdmin", null);		
	soapDoc.getMethod().setAttribute(ZaServer.A_VolumeId, volume[ZaServer.A_VolumeId]);	
	var elVolume = soapDoc.set("volume", null);
	elVolume.setAttribute("type", volume[ZaServer.A_VolumeType]);
	elVolume.setAttribute("name", volume[ZaServer.A_VolumeName]);	
	elVolume.setAttribute("rootpath", volume[ZaServer.A_VolumeRootPath]);		
	elVolume.setAttribute("compressBlobs", volume[ZaServer.A_VolumeCompressBlobs]);		
	elVolume.setAttribute("compressionThreshold", volume[ZaServer.A_VolumeCompressionThreshold]);			
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;		
}

ZaServer.prototype.setCurrentVolume = function (id, type) {
	if(!id || !type)
		return false;	
	var soapDoc = AjxSoapDoc.create("SetCurrentVolumeRequest", "urn:zimbraAdmin", null);		
	soapDoc.getMethod().setAttribute(ZaServer.A_VolumeType, type);		
	soapDoc.getMethod().setAttribute(ZaServer.A_VolumeId, id);	
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;			
}
/*
function ZaHSM() {
	this[ZaServer.A_HSMstartDate] = null;
	this[ZaServer.A_HSMendDate] = null;
	this[ZaServer.A_HSMrunning] = false;			
	this[ZaServer.A_HSMwasAborted] = false;
	this[ZaServer.A_HSMaborting] = false;			
	this[ZaServer.A_HSMerror] = null;						
	this[ZaServer.A_HSMnumBlobsMoved] = 0;
	this[ZaServer.A_HSMnumMailboxes] = 0;
	this[ZaServer.A_HSMtotalMailboxes] = 0;
	this[ZaServer.A_HSMthreshold] = 0;	
	this[ZaServer.A_HSMremainingMailboxes] = 0;
	this.serverid = null;
}*/
/*
ZaHSM.myXModel = {
	items: [
		{id:ZaServer.A_HSMstartDate, ref:ZaServer.A_HSMstartDate, type:_NUMBER_},						
		{id:ZaServer.A_HSMendDate, ref:ZaServer.A_HSMendDate, type:_NUMBER_},								
		{id:ZaServer.A_HSMrunning, ref:ZaServer.A_HSMrunning, type:_ENUM_, choices:[false,true]},						
		{id:ZaServer.A_HSMwasAborted, ref:ZaServer.A_HSMwasAborted, type:_ENUM_, choices:[false,true]},				
		{id:ZaServer.A_HSMaborting, ref:ZaServer.A_HSMaborting, type:_ENUM_, choices:[false,true]},						
		{id:ZaServer.A_HSMerror, ref:ZaServer.A_HSMerror, type:_STRING_},
		{id:ZaServer.A_HSMnumBlobsMoved, ref:ZaServer.A_HSMnumBlobsMoved, type:_NUMBER_},
		{id:ZaServer.A_HSMnumMailboxes, ref:ZaServer.A_HSMnumMailboxes, type:_NUMBER_},
		{id:ZaServer.A_HSMtotalMailboxes, ref:ZaServer.A_HSMtotalMailboxes, type:_NUMBER_},				
		{id:ZaServer.A_HSMthreshold, ref:ZaServer.A_HSMthreshold, type:_NUMBER_},
		{id:ZaServer.A_HSMremainingMailboxes, ref:ZaServer.A_HSMremainingMailboxes, type:_NUMBER_},
		{id:"errorMsg", ref:"errorMsg", type:_STRING_},
		{id:"progressMsg", ref:"progressMsg", type:_STRING_}
	]
};*/

/*
ZaServer.prototype.getHSMStatus = function () {
	if(!this.id)
		return;
		
	var soapDoc = AjxSoapDoc.create("GetHsmStatusRequest", "urn:zimbraAdmin", null);
	var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;	

	this.hsm = new Object();		

	this.hsm[ZaServer.A_HSMstartDate] = respNode.getAttribute(ZaServer.A_HSMstartDate);
	this.hsm[ZaServer.A_HSMendDate] = respNode.getAttribute(ZaServer.A_HSMendDate);
	this.hsm[ZaServer.A_HSMrunning] = respNode.getAttribute(ZaServer.A_HSMrunning);			
	this.hsm[ZaServer.A_HSMwasAborted] = respNode.getAttribute(ZaServer.A_HSMwasAborted);
	this.hsm[ZaServer.A_HSMaborting] = respNode.getAttribute(ZaServer.A_HSMaborting);			
	this.hsm[ZaServer.A_HSMerror] = respNode.getAttribute(ZaServer.A_HSMerror);						
	this.hsm[ZaServer.A_HSMnumBlobsMoved] = respNode.getAttribute(ZaServer.A_HSMnumBlobsMoved);
	this.hsm[ZaServer.A_HSMnumMailboxes] = respNode.getAttribute(ZaServer.A_HSMnumMailboxes);
	this.hsm[ZaServer.A_HSMtotalMailboxes] = respNode.getAttribute(ZaServer.A_HSMtotalMailboxes);
	this.hsm[ZaServer.A_HSMthreshold] = respNode.getAttribute(ZaServer.A_HSMthreshold);	
	this.hsm[ZaServer.A_HSMremainingMailboxes] = this.hsm[ZaServer.A_HSMtotalMailboxes] - this.hsm[ZaServer.A_HSMnumMailboxes];
}*/

ZaServer.parseHSMStatusResponse = 
function (arg, respObj) {
	if (!respObj)
		respObj = new ZaReindexMailbox();

	if(!arg) {
		respObj.status = null;
		return;
	}
	if(!respObj.hsm) {
		respObj.hsm = new Object();
		respObj.hsm.pollInterval = 500;
	}	
	if(arg instanceof AjxException || arg instanceof ZmCsfeException || arg instanceof AjxSoapException) {
		respObj.hsm.errorMsg = arg.detail;
		respObj.hsm[ZaServer.A_HSMerror] = String(ZaMsg.FAILED_HSM).replace("{0}", arg.code);
		respObj.hsm[ZaServer.A_HSMrunning] = 0;	
	} else {
		var respNode;
		if(!arg) {
			return;
		}
		if(arg instanceof AjxSoapDoc) {
			if(!arg.getBody() || !arg.getBody().firstChild) {
				return;
			}	
			respNode = arg.getBody().firstChild;
		} else {
			if(!arg.firstChild) {
				return;
			}
			respNode = arg.firstChild;
		}
		if(respNode) {
			respObj.hsm.errorMsg = null;		
			respObj.hsm[ZaServer.A_HSMerror] = null;			
			if(respNode.nodeName == "HsmResponse") {
				respObj.hsm[ZaServer.A_HSMrunning] = 1;
				respObj.hsm[ZaServer.A_HSMwasAborted] = 0;
			} else if (respNode.nodeName == "AbortHsmResponse") {
				var tmpInt = parseInt(respNode.getAttribute(ZaServer.A_HSMwasAborted));			
				respObj.hsm[ZaServer.A_HSMwasAborted] = (tmpInt == NaN) ? 0 : tmpInt; 
				respObj.hsm[ZaServer.A_HSMrunning] = 0;	
			} else {
				respObj.hsm[ZaServer.A_HSMstartDate] = respNode.getAttribute(ZaServer.A_HSMstartDate);
				respObj.hsm[ZaServer.A_HSMendDate] = respNode.getAttribute(ZaServer.A_HSMendDate);
	
				var tmpInt = parseInt(respNode.getAttribute(ZaServer.A_HSMrunning));
				respObj.hsm[ZaServer.A_HSMrunning] = (tmpInt == NaN) ? 0 : tmpInt;
	
				tmpInt = parseInt(respNode.getAttribute(ZaServer.A_HSMwasAborted));			
				respObj.hsm[ZaServer.A_HSMwasAborted] = (tmpInt == NaN) ? 0 : tmpInt; 
	
				respObj.hsm[ZaServer.A_HSMaborting] = respNode.getAttribute(ZaServer.A_HSMaborting);			
				respObj.hsm[ZaServer.A_HSMerror] = respNode.getAttribute(ZaServer.A_HSMerror);						
				respObj.hsm[ZaServer.A_HSMnumBlobsMoved] = respNode.getAttribute(ZaServer.A_HSMnumBlobsMoved);
				respObj.hsm[ZaServer.A_HSMnumMailboxes] = respNode.getAttribute(ZaServer.A_HSMnumMailboxes);
				respObj.hsm[ZaServer.A_HSMtotalMailboxes] = respNode.getAttribute(ZaServer.A_HSMtotalMailboxes);
				respObj.hsm[ZaServer.A_HSMthreshold] = respNode.getAttribute(ZaServer.A_HSMthreshold);	
				respObj.hsm[ZaServer.A_HSMremainingMailboxes] = respObj.hsm[ZaServer.A_HSMtotalMailboxes] - respObj.hsm[ZaServer.A_HSMnumMailboxes];
				if(!respObj.hsm[ZaServer.A_HSMrunning]&& !respObj.hsm[ZaServer.A_HSMnumBlobsMoved]
					&& !respObj.hsm[ZaServer.A_HSMtotalMailboxes] && !respObj.hsm[ZaServer.A_HSMremainingMailboxes]) {
					respObj.hsm.progressMsg = String(ZaMsg.HSM_InfoUnavailable);
				} else {
					respObj.hsm.progressMsg = String(ZaMsg.HSM_ProcessStatus).replace("{0}", respObj.hsm[ZaServer.A_HSMnumBlobsMoved]).replace("{1}",respObj.hsm[ZaServer.A_HSMnumMailboxes]).replace("{2}", respObj.hsm[ZaServer.A_HSMremainingMailboxes]);
				}
/*				if(respObj.hsm[ZaServer.A_HSMnumMailboxes] == 0) {
					respObj.hsm[ZaServer.A_HSMnumMailboxes] = respObj.hsm[ZaServer.A_HSMtotalMailboxes];
				}*/
			}
			if(respObj.hsm[ZaServer.A_HSMrunning] == 0 && respObj.hsm[ZaServer.A_HSMstartDate] && respObj.hsm[ZaServer.A_HSMendDate]) {
				respObj.hsm.progressMsg = String(ZaMsg.HSM_StatusReport).replace("{0}", 
					AjxBuffer.concat(
						AjxDateUtil.simpleComputeDateStr(new Date(parseInt(respObj.hsm[ZaServer.A_HSMstartDate]))),
						"&nbsp;",
						AjxDateUtil.computeTimeString(new Date(parseInt(respObj.hsm[ZaServer.A_HSMstartDate])))
					)
				).replace("{1}",
					AjxBuffer.concat(
						AjxDateUtil.simpleComputeDateStr(new Date(parseInt(respObj.hsm[ZaServer.A_HSMendDate]))),
						"&nbsp;",
						AjxDateUtil.computeTimeString(new Date(parseInt(respObj.hsm[ZaServer.A_HSMendDate])))
					)
				);
				
			}
		}
	}
}

ZaServer.getHSMStatus = 
function (serverid,callback) {
	var soapDoc = AjxSoapDoc.create("GetHsmStatusRequest", "urn:zimbraAdmin", null);
	var resp = null;
	try {
		if(callback) {
			var asynCommand = new ZmCsfeAsynchCommand();
			asynCommand.addInvokeListener(callback);
			asynCommand.invoke(soapDoc, null, null, serverid, true);			
			return asynCommand;
		} else {
			resp = ZmCsfeCommand.invoke(soapDoc, null, null, serverid, true);
		}
	} catch (ex) {
		resp=ex;
	}
	return resp;
}

ZaServer.abortHSMSession = 
function (serverid) {
	var soapDoc = AjxSoapDoc.create("AbortHsmRequest", "urn:zimbraAdmin", null);
	soapDoc.getMethod().setAttribute("action", "cancel");
	
	var resp;
	try {
		resp = ZmCsfeCommand.invoke(soapDoc, null, null, serverid, true);
	} catch (ex) {
		resp = ex;
	}
	return resp;
}

ZaServer.startHSMSession = 
function (serverid) {
	var soapDoc = AjxSoapDoc.create("HsmRequest", "urn:zimbraAdmin", null);
	var resp;
	try {
		resp = ZmCsfeCommand.invoke(soapDoc, null, null, serverid, true);
	} catch (ex) {
		resp = ex;
	}
	return resp;
}
/*
ZaServer.prototype.runHSM = function() {
	if(!this.id)
		return;
	try {
		var soapDoc = AjxSoapDoc.create("HsmRequest", "urn:zimbraAdmin", null);	
		var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;		
	} catch (ex) {
		this._app.getCurrentController()._handleException(ex,"ZaServer.prototype.runHSM",null, false);
	}
}

ZaServer.prototype.abortHSM = function() {
	if(!this.id)
		return;
	try {
		var soapDoc = AjxSoapDoc.create("AbortHsmRequest", "urn:zimbraAdmin", null);	
		var respNode = ZmCsfeCommand.invoke(soapDoc, false, null, this.id, true).firstChild;		
	} catch (ex) {
		this._app.getCurrentController()._handleException(ex,"ZaServer.prototype.runHSM",null, false);
	}
}*/