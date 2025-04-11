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
* @class ZaGlobalConfigViewController 
* @contructor ZaGlobalConfigViewController
* @param appCtxt
* @param container
* @param app
* @author Greg Solovyev
**/
ZaGlobalConfigViewController = function(appCtxt, container, app) {
	ZaXFormViewController.call(this, appCtxt, container, app,"ZaGlobalConfigViewController");
	this._UICreated = false;
	this._helpURL = location.pathname + "adminhelp/html/WebHelp/managing_global_settings/global_settings.htm";			
	this.objType = ZaEvent.S_GLOBALCONFIG;
	this.tabConstructor = GlobalConfigXFormView;					
}

ZaGlobalConfigViewController.prototype = new ZaXFormViewController();
ZaGlobalConfigViewController.prototype.constructor = ZaGlobalConfigViewController;

//ZaGlobalConfigViewController.STATUS_VIEW = "ZaGlobalConfigViewController.STATUS_VIEW";

/**
* Adds listener to removal of an ZaDomain 
* @param listener
**/
ZaGlobalConfigViewController.prototype.addSettingsChangeListener = 
function(listener) {
	this._evtMgr.addListener(ZaEvent.E_MODIFY, listener);
}

ZaGlobalConfigViewController.prototype.show = 
function(item, openInNewTab) {
	if(!this._UICreated) {
  		this._ops = new Array();
		this._ops.push(new ZaOperation(ZaOperation.SAVE, ZaMsg.TBB_Save, ZaMsg.ALTBB_Save_tt, "Save", "SaveDis", new AjxListener(this, this.saveButtonListener)));
		if (ZaOperation.UPDATELICENSE) {
			this._ops.push(new ZaOperation(ZaOperation.UPDATELICENSE, ZaMsg.TBB_UpdateLicense, ZaMsg.ALTBB_UpdateLicense_tt, "UpdateLicense", "UpdateLicense",
						new AjxListener(this, this.updateLicenseButtonListener)));		   
		}
		this._ops.push(new ZaOperation(ZaOperation.NONE));
		this._ops.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));							
		
		this._toolbar = new ZaToolBar(this._container, this._ops);
	
		this._contentView = this._view = new this.tabConstructor(this._container, this._app);
		var elements = new Object();
		elements[ZaAppViewMgr.C_APP_CONTENT] = this._view;
		elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;	
		var tabParams = {
			openInNewTab: false,
			tabId: this.getContentViewId(),
			tab: this.getMainTab() 
		}				
		//this._app.createView(ZaZimbraAdmin._GLOBAL_SETTINGS,elements);
		this._app.createView(this.getContentViewId(), elements, tabParams) ;
		this._UICreated = true;
		this._app._controllers[this.getContentViewId ()] = this ;
	}
	//this._app.pushView(ZaZimbraAdmin._GLOBAL_SETTINGS);
	this._app.pushView(this.getContentViewId());
	this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);  	
	if (ZaOperation.UPDATELICENSE){
		var updateLicenseButton = this._toolbar.getButton(ZaOperation.UPDATELICENSE) ;
		updateLicenseButton.setEnabled(false);
		 var divEl = updateLicenseButton.getHtmlElement();
		 divEl.style.visibility = "hidden";	
	}
	item.load();
	try {		
		item[ZaModel.currentTab] = "1"
		this._view.setDirty(false);
		this._view.setObject(item);
	} catch (ex) {
		this._handleException(ex, "ZaGlobalConfigViewController.prototype.show", null, false);
	}
	this._currentObject = item;		
	/*
	if (openInNewTab) {//when a ctrl shortcut is pressed
		
	}else{ //open in the main tab
		this.updateMainTab ("GlobalSettings") ;	
	}*/
}

ZaGlobalConfigViewController.prototype.setEnabled = 
function(enable) {
	this._view.setEnabled(enable);
}

ZaGlobalConfigViewController.prototype._saveChanges =
function () {
	var tmpObj = this._view.getObject();
	var isNew = false;
	if(tmpObj.attrs == null) {
		//show error msg
		this._errorDialog.setMessage(ZaMsg.ERROR_UNKNOWN, null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();		
		return false;	
	}

	//check values
	if(!AjxUtil.isNonNegativeLong(tmpObj.attrs[ZaGlobalConfig.A_zimbraSmtpPort])) {
		//show error msg
		this._errorDialog.setMessage(ZaMsg.ERROR_INVALID_VALUE + ": " + ZaMsg.NAD_SmtpPort + " ! ", null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();		
		return false;
	}
		
	if(!AjxUtil.isNonNegativeLong(tmpObj.attrs[ZaGlobalConfig.A_zimbraGalMaxResults])) {
		//show error msg
		this._errorDialog.setMessage(ZaMsg.ERROR_INVALID_VALUE + ": " + ZaMsg.NAD_GalMaxResults + " ! ", null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();		
		return false;
	}	
	
	if (tmpObj.attrs[ZaGlobalConfig.A_zimbraDataSourceNumThreads] &&
	 	 !AjxUtil.isPositiveInt(tmpObj.attrs[ZaGlobalConfig.A_zimbraDataSourceNumThreads])) {
			//show error msg
		this._errorDialog.setMessage(ZaMsg.ERROR_INVALID_VALUE + ": " + ZaMsg.NAD_zimbraDataSourceNumThreads + " ! ", null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();		
		return false;
	}	
	
	// update zimbraMtaRestriction
	var restrictions = [];
	for (var i = 0; i < ZaGlobalConfig.MTA_RESTRICTIONS.length; i++) {
		var restriction = ZaGlobalConfig.MTA_RESTRICTIONS[i];
		if (tmpObj.attrs["_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_"+restriction]) {
			restrictions.push(restriction);
		}			
	}
	var dirty = restrictions.length > 0;
	if (tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaRestriction]) {
		var prevRestrictions = AjxUtil.isString(tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaRestriction])
		                     ? [ tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaRestriction] ]
		                     : tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaRestriction];
		dirty = restrictions.length != prevRestrictions.length;
		if (!dirty) {
			for (var i = 0; i < prevRestrictions.length; i++) {
				var restriction = prevRestrictions[i];
				if (!tmpObj.attrs["_"+ZaGlobalConfig.A_zimbraMtaRestriction+"_"+restriction]) {
					dirty = true;
					break;
				}
			}
		}
	}
	if (dirty) {
		tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaRestriction] = restrictions;
	}

	//transfer the fields from the tmpObj to the _currentObject, since _currentObject is an instance of ZaDomain
	var mods = new Object();
	for (var a in tmpObj.attrs) {
		if(a == ZaItem.A_objectClass || a == ZaGlobalConfig.A_zimbraAccountClientAttr || 
		a == ZaGlobalConfig.A_zimbraServerInheritedAttr || a == ZaGlobalConfig.A_zimbraDomainInheritedAttr ||
		a == ZaGlobalConfig.A_zimbraCOSInheritedAttr || a == ZaGlobalConfig.A_zimbraGalLdapAttrMap || 
		a == ZaGlobalConfig.A_zimbraGalLdapFilterDef || /^_/.test(a) || a == ZaGlobalConfig.A_zimbraMtaBlockedExtension || a == ZaGlobalConfig.A_zimbraMtaCommonBlockedExtension)
			continue;

		if (this._currentObject.attrs[a] != tmpObj.attrs[a] ) {
			mods[a] = tmpObj.attrs[a];
		}
	}
	//check if blocked extensions are changed
	var extIds = new Array();
	if((tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] instanceof AjxVector) && tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] && tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].size()) {
		var cnt = tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].size();
		for(var i = 0; i < cnt; i ++) {
			extIds.push(tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].get(i));
		}
		if((cnt > 0 && (!this._currentObject.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] || !this._currentObject.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].length))
		|| (extIds.join("") != this._currentObject.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].join(""))) {
			mods[ZaGlobalConfig.A_zimbraMtaBlockedExtension] = extIds;
		} 
		if(cnt==0)
			mods[ZaGlobalConfig.A_zimbraMtaBlockedExtension] = "";	
	} else if( 
			(!tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] || 
				(tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] instanceof AjxVector && tmpObj.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].size()<1)
			) && (this._currentObject.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension] && this._currentObject.attrs[ZaGlobalConfig.A_zimbraMtaBlockedExtension].length)	) {
		mods[ZaGlobalConfig.A_zimbraMtaBlockedExtension] = "";
	}		
	
	//save the model
	//var changeDetails = new Object();
	this._currentObject.modify(mods);
	
	//if modification took place - fire a Settings Change Event
	//changeDetails["obj"] = this._currentObject;
	//changeDetails["modFields"] = mods;
	this.fireChangeEvent(this._currentObject);
	return true;
}


