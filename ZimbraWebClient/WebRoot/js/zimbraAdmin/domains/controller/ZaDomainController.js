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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

 /**
* @class ZaDomainController controls display of a single Domain
* @contructor ZaDomainController
* @param appCtxt
* @param container
* @param abApp
**/

ZaDomainController = function(appCtxt, container,app) {
	ZaXFormViewController.call(this, appCtxt, container, app, "ZaDomainController");
	this._UICreated = false;
	this._helpURL = location.pathname + "adminhelp/html/WebHelp/managing_domains/managing_domains.htm";	
	this._toolbarOperations = new Array();			
	this.deleteMsg = ZaMsg.Q_DELETE_DOMAIN;	
	this.objType = ZaEvent.S_DOMAIN;
	this.tabConstructor = ZaDomainXFormView;				
}

ZaDomainController.prototype = new ZaXFormViewController();
ZaDomainController.prototype.constructor = ZaDomainController;

ZaController.initToolbarMethods["ZaDomainController"] = new Array();
ZaController.setViewMethods["ZaDomainController"] = new Array();
/**
*	@method show
*	@param entry - isntance of ZaDomain class
*/

ZaDomainController.prototype.show = 
function(entry) {
	if (! this.selectExistingTabByItemId(entry.id)){
		this._setView(entry, true);
	}
}

/**
* @method initToolbarMethod
* This method creates ZaOperation objects 
* All the ZaOperation objects are added to this._toolbarOperations array which is then used to 
* create the toolbar for this view.
* Each ZaOperation object defines one toolbar button.
* Help button is always the last button in the toolbar
**/
ZaDomainController.initToolbarMethod = 
function () {
	this._toolbarOperations.push(new ZaOperation(ZaOperation.SAVE, ZaMsg.TBB_Save, ZaMsg.DTBB_Save_tt, "Save", "SaveDis", new AjxListener(this, this.saveButtonListener)));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.CLOSE, ZaMsg.TBB_Close, ZaMsg.DTBB_Close_tt, "Close", "CloseDis", new AjxListener(this, this.closeButtonListener)));    	
	this._toolbarOperations.push(new ZaOperation(ZaOperation.SEP));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.NEW, ZaMsg.TBB_New, ZaMsg.DTBB_New_tt, "Domain", "DomainDis", new AjxListener(this, this._newButtonListener)));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.DELETE, ZaMsg.TBB_Delete, ZaMsg.DTBB_Delete_tt, "Delete", "DeleteDis", new AjxListener(this, this.deleteButtonListener)));    	    	
	this._toolbarOperations.push(new ZaOperation(ZaOperation.SEP));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.GAL_WIZARD, ZaMsg.DTBB_GAlConfigWiz, ZaMsg.DTBB_GAlConfigWiz_tt, "GALWizard", "GALWizardDis", new AjxListener(this, ZaDomainController.prototype._galWizButtonListener)));   		
	this._toolbarOperations.push(new ZaOperation(ZaOperation.AUTH_WIZARD, ZaMsg.DTBB_AuthConfigWiz, ZaMsg.DTBB_AuthConfigWiz_tt, "AuthWizard", "AuthWizardDis", new AjxListener(this, ZaDomainController.prototype._authWizButtonListener)));   		   		
	this._toolbarOperations.push(new ZaOperation(ZaOperation.INIT_NOTEBOOK, ZaMsg.DTBB_InitNotebook, ZaMsg.DTBB_InitNotebook_tt, "NewNotebook", "NewNotebookDis", new AjxListener(this, ZaDomainController.prototype._initNotebookButtonListener)));   		   		   		

}
ZaController.initToolbarMethods["ZaDomainController"].push(ZaDomainController.initToolbarMethod);

/**
*	@method setViewMethod 
*	@param entry - isntance of ZaDomain class
*/
ZaDomainController.setViewMethod =
function(entry) {
	entry.load("name", entry.attrs[ZaDomain.A_domainName]);
	if(!this._UICreated) {
		this._createUI();
	} 
	this._app.pushView(this.getContentViewId());
	this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);  		
	if(!entry.id) {
		this._toolbar.getButton(ZaOperation.DELETE).setEnabled(false);  			
	} else {
		this._toolbar.getButton(ZaOperation.DELETE).setEnabled(true);  				
	}	
	this._view.setDirty(false);
	if(entry.attrs[ZaDomain.A_zimbraNotebookAccount])
		this._toolbar.getButton(ZaOperation.INIT_NOTEBOOK).setEnabled(false);
	else
		this._toolbar.getButton(ZaOperation.INIT_NOTEBOOK).setEnabled(true);	
	this._view.setObject(entry); 	//setObject is delayed to be called after pushView in order to avoid jumping of the view	
	this._currentObject = entry;
}
ZaController.setViewMethods["ZaDomainController"].push(ZaDomainController.setViewMethod);

/**
* @method _createUI
**/
ZaDomainController.prototype._createUI =
function () {
	this._contentView = this._view = new this.tabConstructor(this._container, this._app);

	this._initToolbar();
	//always add Help button at the end of the toolbar
	this._toolbarOperations.push(new ZaOperation(ZaOperation.NONE));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));							
	this._toolbar = new ZaToolBar(this._container, this._toolbarOperations);		
	
	var elements = new Object();
	elements[ZaAppViewMgr.C_APP_CONTENT] = this._view;
	elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;	
    var tabParams = {
		openInNewTab: true,
		tabId: this.getContentViewId()
	}  		
    this._app.createView(this.getContentViewId(), elements, tabParams) ;
	this._UICreated = true;
	this._app._controllers[this.getContentViewId ()] = this ;
}
/**
*	@method _setView 
*	@param entry - isntance of ZaDomain class
*/
/*
ZaDomainController.prototype._setView =
function(entry, openInNewTab) {
	try {
		entry.load("name", entry.attrs[ZaDomain.A_domainName]);
		if(!this._UICreated) {
			this._contentView = this._view = new ZaDomainXFormView(this._container, this._app);
	   		this._ops = new Array();
	   		this._ops.push(new ZaOperation(ZaOperation.SAVE, ZaMsg.TBB_Save, ZaMsg.DTBB_Save_tt, "Save", "SaveDis", new AjxListener(this, this.saveButtonListener)));
	   		this._ops.push(new ZaOperation(ZaOperation.CLOSE, ZaMsg.TBB_Close, ZaMsg.DTBB_Close_tt, "Close", "CloseDis", new AjxListener(this, this.closeButtonListener)));    	
			this._ops.push(new ZaOperation(ZaOperation.SEP));
	   		this._ops.push(new ZaOperation(ZaOperation.NEW, ZaMsg.TBB_New, ZaMsg.DTBB_New_tt, "Domain", "DomainDis", new AjxListener(this, this._newButtonListener)));
	  		this._ops.push(new ZaOperation(ZaOperation.DELETE, ZaMsg.TBB_Delete, ZaMsg.DTBB_Delete_tt, "Delete", "DeleteDis", new AjxListener(this, this.deleteButtonListener)));    	    	
			this._ops.push(new ZaOperation(ZaOperation.SEP));
	   		this._ops.push(new ZaOperation(ZaOperation.GAL_WIZARD, ZaMsg.DTBB_GAlConfigWiz, ZaMsg.DTBB_GAlConfigWiz_tt, "GALWizard", "GALWizardDis", new AjxListener(this, ZaDomainController.prototype._galWizButtonListener)));   		
	   		this._ops.push(new ZaOperation(ZaOperation.AUTH_WIZARD, ZaMsg.DTBB_AuthConfigWiz, ZaMsg.DTBB_AuthConfigWiz_tt, "AuthWizard", "AuthWizardDis", new AjxListener(this, ZaDomainController.prototype._authWizButtonListener)));   		   		
	   		this._ops.push(new ZaOperation(ZaOperation.INIT_NOTEBOOK, ZaMsg.DTBB_InitNotebook, ZaMsg.DTBB_InitNotebook_tt, "NewNotebook", "NewNotebookDis", new AjxListener(this, ZaDomainController.prototype._initNotebookButtonListener)));   		   		   		
			this._ops.push(new ZaOperation(ZaOperation.NONE));
			this._ops.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));							
			this._toolbar = new ZaToolBar(this._container, this._ops);
			var elements = new Object();
			elements[ZaAppViewMgr.C_APP_CONTENT] = this._view;
			elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;		
		    //this._app.createView(ZaZimbraAdmin._DOMAIN_VIEW, elements);
		    var tabParams = {
				openInNewTab: true,
				tabId: this.getContentViewId()
			}
			this._app.createView(this.getContentViewId(), elements, tabParams) ;
			this._UICreated = true;
			this._app._controllers[this.getContentViewId ()] = this ;
		} 
		//this._app.pushView(ZaZimbraAdmin._DOMAIN_VIEW);
		this._app.pushView(this.getContentViewId());
		this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);  		
		if(!entry.id) {
			this._toolbar.getButton(ZaOperation.DELETE).setEnabled(false);  			
		} else {
			this._toolbar.getButton(ZaOperation.DELETE).setEnabled(true);  				
		}
		this._view.setDirty(false);
		entry[ZaModel.currentTab] = "1";
		if(entry.attrs[ZaDomain.A_zimbraNotebookAccount])
			this._toolbar.getButton(ZaOperation.INIT_NOTEBOOK).setEnabled(false);
		else
			this._toolbar.getButton(ZaOperation.INIT_NOTEBOOK).setEnabled(true);
			
		this._view.setObject(entry); 	//setObject is delayed to be called after pushView in order to avoid jumping of the view	
		this._currentObject = entry;
	} catch (ex) {
		this._handleException(ex, "ZaDomainController.prototype._setView", null, false);	
	}
}*/

ZaDomainController.prototype.saveButtonListener =
function(ev) {
	try {
		if(this._saveChanges()) {
			this._view.setDirty(false);
			if(this._toolbar)
				this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);		
		}
	} catch (ex) {
		this._handleException(ex, "ZaDomainController.prototype.saveButtonListener", null, false);
	}
	return;
}

ZaDomainController.prototype._saveChanges = 
function () {
	var tmpObj = this._view.getObject();
	var mods = new Object();
	var haveSmth = false;
	var renameNotebookAccount = false;
	if(tmpObj.attrs[ZaDomain.A_notes] != this._currentObject.attrs[ZaDomain.A_notes]) {
		mods[ZaDomain.A_notes] = tmpObj.attrs[ZaDomain.A_notes] ;
		haveSmth = true;
	}
	if(tmpObj.attrs[ZaDomain.A_description] != this._currentObject.attrs[ZaDomain.A_description]) {
		mods[ZaDomain.A_description] = tmpObj.attrs[ZaDomain.A_description] ;
		haveSmth = true;
	}
	if(tmpObj.attrs[ZaDomain.A_domainDefaultCOSId] != this._currentObject.attrs[ZaDomain.A_domainDefaultCOSId]) {
		mods[ZaDomain.A_domainDefaultCOSId] = tmpObj.attrs[ZaDomain.A_domainDefaultCOSId] ;
		haveSmth = true;
	}	
	
	if(tmpObj.attrs[ZaDomain.A_domainMaxAccounts] != this._currentObject.attrs[ZaDomain.A_domainMaxAccounts]) {
		mods[ZaDomain.A_domainMaxAccounts] = tmpObj.attrs[ZaDomain.A_domainMaxAccounts] ;
		haveSmth = true;
	}
	
	if(tmpObj.attrs[ZaDomain.A_zimbraVirtualHostname].join(",").valueOf() !=  this._currentObject.attrs[ZaDomain.A_zimbraVirtualHostname].join(",").valueOf()) {
		mods[ZaDomain.A_zimbraVirtualHostname] = tmpObj.attrs[ZaDomain.A_zimbraVirtualHostname] ;
		haveSmth = true;		
	}
	if(tmpObj.attrs[ZaDomain.A_zimbraNotebookAccount] != this._currentObject.attrs[ZaDomain.A_zimbraNotebookAccount]) {
		mods[ZaDomain.A_zimbraNotebookAccount] = tmpObj.attrs[ZaDomain.A_zimbraNotebookAccount] ;
		haveSmth = true;
		renameNotebookAccount = true;
	}	

	var writeACLs = false;	
	var permsToRevoke = [];
	//check if any notebook permissions are changed
	if(tmpObj[ZaDomain.A_allNotebookACLS]._version > 0) {
		writeACLs = true;	
		var cnt = this._currentObject[ZaDomain.A_allNotebookACLS].length;
		for(var i = 0; i < cnt; i++) {
			if(this._currentObject[ZaDomain.A_allNotebookACLS][i].gt == ZaDomain.A_NotebookUserACLs ||
				this._currentObject[ZaDomain.A_allNotebookACLS][i].gt == ZaDomain.A_NotebookGroupACLs ||
				this._currentObject[ZaDomain.A_allNotebookACLS][i].gt == ZaDomain.A_NotebookDomainACLs)	{
				var cnt2 = tmpObj[ZaDomain.A_allNotebookACLS].length;
				var foundUser = false;
				for(var j = 0; j < cnt2; j++) {
					if(tmpObj[ZaDomain.A_allNotebookACLS][j].name == this._currentObject[ZaDomain.A_allNotebookACLS][i].name) {
						foundUser = true;
						break;
					}
				}
				if(!foundUser && this._currentObject[ZaDomain.A_allNotebookACLS][i].zid) {
					permsToRevoke.push(this._currentObject[ZaDomain.A_allNotebookACLS][i].zid);
				}
			}
		
		}
	}

	
	/*if(this._currentObject.notebookAcls) {
		for(var gt in this._currentObject.notebookAcls) {
			if(!(this._currentObject.notebookAcls[gt] instanceof Array)) {
				for (var a in this._currentObject.notebookAcls[gt]) {
					if(this._currentObject.notebookAcls[gt][a] != tmpObj.notebookAcls[gt][a]) {
						writeACLs = true;
						break;
					}
				}
			} else {
				var cnt1 = this._currentObject.notebookAcls[gt].length;
				var cnt2 = tmpObj.notebookAcls[gt].length;
				if(cnt1 != cnt2) {
					writeACLs = true;
					break;
				} else {
					for (var i=0; i< cnt1; i++) {
						if(typeof (tmpObj.notebookAcls[gt][i]) == "object" && typeof(this._currentObject.notebookAcls[gt][i]) == "object") {
							for (var a in tmpObj.notebookAcls[gt][i]) {
								if(this._currentObject.notebookAcls[gt][i][a] != tmpObj.notebookAcls[gt][i][a])	{
									writeACLs = true;
									break;
								}
								if(writeACLs)
									break;
							}
						}
						if(writeACLs)
							break;
					}
				}
			}
			if(writeACLs)
				break;
		}
	}
	*/	
	if(haveSmth || writeACLs) {
		try { 
			var soapDoc = AjxSoapDoc.create("BatchRequest", "urn:zimbra");
			soapDoc.setMethodAttribute("onerror", "stop");		
			if(renameNotebookAccount) {
				var account = new ZaAccount(this._app);
				account.load(ZaAccount.A_name,this._currentObject.attrs[ZaDomain.A_zimbraNotebookAccount]);
				account.rename(tmpObj.attrs[ZaDomain.A_zimbraNotebookAccount]);
			}
			if(haveSmth) {
				var modifyDomainRequest = soapDoc.set("ModifyDomainRequest");
				modifyDomainRequest.setAttribute("xmlns", "urn:zimbraAdmin");
			
				//var soapDoc = AjxSoapDoc.create("ModifyDomainRequest", "urn:zimbraAdmin", null);
				soapDoc.set("id", this._currentObject.id,modifyDomainRequest);
				for (var aname in mods) {
					//multy value attribute
					if(mods[aname] instanceof Array) {
						var cnt = mods[aname].length;
						if(cnt) {
							for(var ix=0; ix <cnt; ix++) {
								if(mods[aname][ix]) { //if there is an empty element in the array - don't send it
									var attr = soapDoc.set("a", mods[aname][ix],modifyDomainRequest);
									attr.setAttribute("n", aname);
								}
							}
						} else {
							var attr = soapDoc.set("a", "",modifyDomainRequest);
							attr.setAttribute("n", aname);
						}
					} else {		
						var attr = soapDoc.set("a", mods[aname],modifyDomainRequest);
						attr.setAttribute("n", aname);
					}
				}
	
			}
	
			var command = new ZmCsfeCommand();
			var params = new Object();
			
			if(writeACLs) {
				if(permsToRevoke.length>0) {
					ZaDomain.getRevokeACLsrequest(permsToRevoke, soapDoc);
				}
				params.accountName = tmpObj[ZaDomain.A_NotebookAccountName] ? tmpObj[ZaDomain.A_NotebookAccountName] : tmpObj.attrs[ZaDomain.A_zimbraNotebookAccount];					
				ZaDomain.getNotebookACLsRequest	(tmpObj,soapDoc);
				
			}
	
			params.soapDoc = soapDoc;	
			var callback = new AjxCallback(this, this.saveChangesCallback);	
			params.asyncMode = true;
			params.callback = callback;			
			command.invoke(params);
	/*	//if(tmpObj[ZaDomain.A_OverwriteNotebookACLs]) {
			if(writeACLs) {
				var callback = new AjxCallback(this, this.setNotebookAclsCallback);				
				ZaDomain.setNotebookACLs(tmpObj, callback);
			}
			//} 
	*/
			return true;
		} catch (ex) {
			this._handleException(ex,"ZaDomainController.prototype._saveChanges");
		}
	}
}



ZaDomainController.prototype.newDomain = 
function () {
	this._currentObject = new ZaDomain();
	this._showNewDomainWizard();
}

ZaDomainController.prototype._showNewDomainWizard = 
function () {
	try {
		this._newDomainWizard = this._app.dialogs["newDomainWizard"] = new ZaNewDomainXWizard(this._container, this._app);	
		this._newDomainWizard.registerCallback(DwtWizardDialog.FINISH_BUTTON, ZaDomainController.prototype._finishNewButtonListener, this, null);			
		this._newDomainWizard.setObject(this._currentObject);
		this._newDomainWizard.popup();
	} catch (ex) {
			this._handleException(ex, "ZaDomainController.prototype._showNewDomainWizard", null, false);
	}
}

// new button was pressed
ZaDomainController.prototype._newButtonListener =
function(ev) {
	if(this._view.isDirty()) {
		//parameters for the confirmation dialog's callback 
		var args = new Object();		
		args["params"] = null;
		args["obj"] = this._app.getDomainController();
		args["func"] = ZaDomainController.prototype.newDomain;
		//ask if the user wants to save changes		
		this._app.dialogs["confirmMessageDialog"] = this._app.dialogs["confirmMessageDialog"] = new ZaMsgDialog(this._view.shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON, DwtDialog.CANCEL_BUTTON], this._app);								
		this._app.dialogs["confirmMessageDialog"].setMessage(ZaMsg.Q_SAVE_CHANGES, DwtMessageDialog.INFO_STYLE);
		this._app.dialogs["confirmMessageDialog"].registerCallback(DwtDialog.YES_BUTTON, this.saveAndGoAway, this, args);		
		this._app.dialogs["confirmMessageDialog"].registerCallback(DwtDialog.NO_BUTTON, this.discardAndGoAway, this, args);		
		this._app.dialogs["confirmMessageDialog"].popup();
	} else {
		this.newDomain();
	}	
}


ZaDomainController.prototype._galWizButtonListener =
function(ev) {
	try {
		this._galWizard = this._app.dialogs["galWizard"] = new ZaGALConfigXWizard(this._container, this._app);	
		this._galWizard.registerCallback(DwtWizardDialog.FINISH_BUTTON, ZaDomainController.prototype._finishGalButtonListener, this, null);			
		this._galWizard.setObject(this._currentObject);
		this._galWizard.popup();
	} catch (ex) {
			this._handleException(ex, "ZaDomainController.prototype._showGalWizard", null, false);
	}
}


ZaDomainController.prototype._authWizButtonListener =
function(ev) {
	try {
		this._authWizard = this._app.dialogs["authWizard"] =  new ZaAuthConfigXWizard(this._container, this._app);	
		this._authWizard.registerCallback(DwtWizardDialog.FINISH_BUTTON, ZaDomainController.prototype._finishAuthButtonListener, this, null);			
		this._authWizard.setObject(this._currentObject);
		this._authWizard.popup();
	} catch (ex) {
			this._handleException(ex, "ZaDomainController.prototype._showAuthWizard", null, false);
	}
}

ZaDomainController.prototype._finishGalButtonListener =
function(ev) {
	try {
		//var changeDetails = new Object();
		ZaDomain.modifyGalSettings(this._galWizard.getObject(),this._currentObject); 
		//if a modification took place - fire an DomainChangeEvent
		//changeDetails["obj"] = this._currentObject;
		this.fireChangeEvent(this._currentObject);
		this._view.setObject(this._currentObject);		
		this._galWizard.popdown();
	} catch (ex) {
		this._handleException(ex, "ZaDomainController.prototype._finishGalButtonListener", null, false);
	}
	return;
}

ZaDomainController.prototype._finishAuthButtonListener =
function(ev) {
	try {
		ZaDomain.modifyAuthSettings(this._authWizard.getObject(), this._currentObject);
		//var changeDetails = new Object();
		//if a modification took place - fire an DomainChangeEvent
		//changeDetails["obj"] = this._currentObject;
	
		this.fireChangeEvent(this._currentObject);
		this._view.setObject(this._currentObject);
		this._authWizard.popdown();
	} catch (ex) {
		this._handleException(ex, "ZaDomainController.prototype._finishAuthButtonListener", null, false);
	}
	return;
}

/**
* @param 	ev event object
* This method handles "finish" button click in "New Domain" dialog
**/

ZaDomainController.prototype._finishNewButtonListener =
function(ev) {
	try {
		var domain = ZaDomain.create(this._newDomainWizard.getObject(), this._app);
		if(domain != null) {
			//if creation took place - fire an DomainChangeEvent
			this.fireCreationEvent(domain);
			this._toolbar.getButton(ZaOperation.DELETE).setEnabled(true);	
			this._newDomainWizard.popdown();		
			if(this._newDomainWizard.getObject()[ZaDomain.A_CreateNotebook]=="TRUE") {
				var params = new Object();
			//	if(this._newDomainWizard.getObject()[ZaDomain.A_OverwriteNotebookACLs]) {
					params[ZaDomain.A_OverwriteNotebookACLs] = true;
					params.obj = this._newDomainWizard.getObject();
			//	} else
					params[ZaDomain.A_OverwriteNotebookACLs] = false;
					
				var callback = new AjxCallback(this, this.initNotebookCallback, params);				
				ZaDomain.initNotebook(this._newDomainWizard.getObject(),callback, this) ;
			}
		}
	} catch (ex) {
		if(ex.code == ZmCsfeException.DOMAIN_EXISTS) {
			this.popupErrorDialog(ZaMsg.ERROR_DOMAIN_EXISTS, ex);		
		} else {
			this._handleException(ex, "ZaDomainController.prototype._finishNewButtonListener", null, false);
		}
	}
	return;
}

ZaDomainController.prototype.initNotebookCallback = 
function (params, resp) {
	if(!resp)
		return;
	if(resp.isException()) {
		this._handleException(resp.getException(), "ZaDomainController.prototype._initNotebookCallback", null, false);
		return;
	} 
//	if(params[ZaDomain.A_OverwriteNotebookACLs] && params.obj!=null) {
		var callback = new AjxCallback(this, this.setNotebookAclsCallback);				
		ZaDomain.setNotebookACLs(params.obj, callback) ;
//	}	
	this._currentObject.refresh();
	this.show(this._currentObject);
}

ZaDomainController.prototype.setNotebookAclsCallback = 
function (resp) {
	if(!resp)
		return;
	if(resp.isException()) {
		this._handleException(resp.getException(), "ZaDomainController.prototype.setNotebookAclsCallback", null, false);
		return;
	} 
}

ZaDomainController.prototype.saveChangesCallback = 
function (resp) {
	if(!resp)
		return;
	if(resp.isException()) {
		this._handleException(resp.getException(), "ZaDomainController.prototype.setNotebookAclsCallback", null, false);
		return;
	} 
	var response;
	if(resp.getResponse)
		response = resp.getResponse().Body.BatchResponse;
	else
		response = resp.Body.BatchResponse;
	
	if(response.Fault) {
		for(var ix in response.Fault) {
			var ex = ZmCsfeCommand.faultToEx(response.Fault[ix], "ZaDomainController.prototype.saveChangesCallback");
			this._handleException(ex, "ZaDomainController.prototype.saveChangesCallback", null, false);
		}
	}
	/*if(response.ModifyDomainResponse && response.ModifyDomainResponse.domain && response.ModifyDomainResponse.domain[0]) {
		this._currentObject.initFromJS(response.ModifyDomainResponse.domain[0]);
	}*/

	this._currentObject.refresh(false);	
	this._view.setObject(this._currentObject);			
	this.fireChangeEvent(this._currentObject);			
}

ZaDomainController.prototype._finishDomainNotebookListener =
function(ev) {
	try {
		var obj = this._initDomainNotebookWiz.getObject();
		if(obj[ZaDomain.A_NotebookAccountPassword] != obj[ZaDomain.A_NotebookAccountPassword2]) {
			this.popupErrorDialog(ZaMsg.ERROR_PASSWORD_MISMATCH);
			return;
		}
		this._initDomainNotebookWiz.popdown();
		var params = new Object();
		params.obj = obj;
			
		var callback = new AjxCallback(this, this.initNotebookCallback, params);
		ZaDomain.initNotebook(this._initDomainNotebookWiz.getObject(),callback, this) ;
	} catch (ex) {
		this._initDomainNotebookWiz.popdown();
		this._handleException(ex, "ZaDomainController.prototype._finishDomainNotebookListener", null, false);
	}
	return;
}

ZaDomainController.prototype._initNotebookButtonListener = 
function (ev) {
	try {
		this._initDomainNotebookWiz = this._app.dialogs["initDomainNotebookWiz"] = new ZaDomainNotebookXWizard(this._container, this._app);	
		this._initDomainNotebookWiz.registerCallback(DwtWizardDialog.FINISH_BUTTON, ZaDomainController.prototype._finishDomainNotebookListener, this, null);			
		this._initDomainNotebookWiz.setObject(this._currentObject);
		this._initDomainNotebookWiz.popup();
	} catch (ex) {
		this._handleException(ex, "ZaDomainController.prototype._initNotebookButtonListener", null, false);
	}	
}

ZaDomainController.prototype._handleException = 
function (ex, method, params, restartOnError, obj) {
	if(ex.code == ZmCsfeException.DOMAIN_NOT_EMPTY) {
		this._errorDialog.setMessage(ZaMsg.ERROR_DOMAIN_NOT_EMPTY, null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();			
	} else if(ex.code == ZmCsfeException.DOMAIN_EXISTS) {
		this._errorDialog.setMessage(ZaMsg.ERROR_DOMAIN_EXISTS, null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup(this._getDialogXY());
	} else {
		ZaController.prototype._handleException.call(this, ex, method, params, restartOnError, obj);				
	}	
}
