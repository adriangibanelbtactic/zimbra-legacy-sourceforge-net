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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

/**
* @class ZaResourceController controls display of a single resource
 * @author Charles Cao
 * resource controller 
 */  
ZaResourceController = function(appCtxt, container, app) {
	ZaXFormViewController.call(this, appCtxt, container, app, "ZaResourceController");
	this._UICreated = false;
	this._toolbarOperations = new Array();
	this._helpURL = location.pathname + "adminhelp/html/WebHelp/managing_accounts/managing_resource.htm";	
	this.deleteMsg = ZaMsg.Q_DELETE_RES;
	this.objType = ZaEvent.S_ACCOUNT;	
	this.tabConstructor = ZaResourceXFormView;	
}

ZaResourceController.prototype = new ZaXFormViewController();
ZaResourceController.prototype.constructor = ZaResourceController;

ZaController.initToolbarMethods["ZaResourceController"] = new Array();
ZaController.setViewMethods["ZaResourceController"] = [];

ZaResourceController.prototype.toString = function () {
	return "ZaResourceController";
};

ZaResourceController.prototype.setDirty = function (isDirty) {
	this._toolbar.getButton(ZaOperation.SAVE).setEnabled(isDirty) ;	
}

ZaResourceController.prototype.handleXFormChange = function (ev) {
	if(ev && ev.form.hasErrors()) { 
		this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);
	}	/*
	else if(ev && ev.formItem instanceof Dwt_TabBar_XFormItem) {	
		//do nothing - only switch the tab and it won't change the dirty status of the xform
		//this._view.setDirty (false);	
	}else if (this._UICreated){
		this._view.setDirty (true);
		//this._toolbar.getButton(ZaOperation.SAVE).setEnabled(true);
	}*/
}
ZaResourceController.prototype.show =
function(entry, openInNewTab, skipRefresh) {
	this._setView(entry, openInNewTab, skipRefresh);
}

ZaResourceController.setViewMethod =
function (entry)	{
    if (!this._UICreated) {
		this._createUI();
	} 	
	try {
		//this._app.pushView(ZaZimbraAdmin._RESOURCE_VIEW);
		this._app.pushView(this.getContentViewId());
		if(!entry.id) {
			this._toolbar.getButton(ZaOperation.DELETE).setEnabled(false);  			
		} else {
			this._toolbar.getButton(ZaOperation.DELETE).setEnabled(true);  				
			//get the calendar resource by id
			entry.load("id", entry.id, null);			
		}	
		this._view.setDirty(false);
		entry[ZaModel.currentTab] = "1" ;
	
		this._view.setObject(entry);
		//disable the save button at the beginning of showing the form
		this._toolbar.getButton(ZaOperation.SAVE).setEnabled(false);
		this._currentObject = entry;
	} catch (ex) {
		this._handleException(ex, "ZaResourceController.prototype.show", null, false);
	}	
};
ZaController.setViewMethods["ZaResourceController"].push(ZaResourceController.setViewMethod);

ZaResourceController.initToolbarMethod =
function () {
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.SAVE, ZaMsg.TBB_Save, ZaMsg.ALTBB_Save_tt, "Save", "SaveDis", new AjxListener(this, this.saveButtonListener)));
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.CLOSE, ZaMsg.TBB_Close, ZaMsg.ALTBB_Close_tt, "Close", "CloseDis", new AjxListener(this, this.closeButtonListener)));    	
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.SEP));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.NEW, ZaMsg.TBB_New, ZaMsg.RESTBB_New_tt, "Resource", "ResourceDis", new AjxListener(this, this.newButtonListener)));   			    	
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.DELETE, ZaMsg.TBB_Delete, ZaMsg.RESTBB_Delete_tt,"Delete", "DeleteDis", new AjxListener(this, this.deleteButtonListener)));    	    	
}
ZaController.initToolbarMethods["ZaResourceController"].push(ZaResourceController.initToolbarMethod);

ZaResourceController.prototype.newResource = function () {
	try {
		var newResource = new ZaResource(this._app);
		if(!this._app.dialogs["newResourceWizard"])
			this._app.dialogs["newResourceWizard"]= new ZaNewResourceXWizard(this._container, this._app);	

		this._app.dialogs["newResourceWizard"].setObject(newResource);
		this._app.dialogs["newResourceWizard"].popup();
	} catch (ex) {
		this._handleException(ex, "ZaResourceController.prototype.newResource", null, false);
	}
}

// new button was pressed
ZaResourceController.prototype.newButtonListener =
function(ev) {
	if(this._view.isDirty()) {
		//parameters for the confirmation dialog's callback 
		var args = new Object();		
		args["params"] = null;
		args["obj"] = this;
		args["func"] = ZaResourceController.prototype.newResource;
		//ask if the user wants to save changes		
		this._app.dialogs["confirmMessageDialog"] = this._app.dialogs["confirmMessageDialog"] = new ZaMsgDialog(this._view.shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON, DwtDialog.CANCEL_BUTTON], this._app);								
		this._app.dialogs["confirmMessageDialog"].setMessage(ZaMsg.Q_SAVE_CHANGES, DwtMessageDialog.INFO_STYLE);
		this._app.dialogs["confirmMessageDialog"].registerCallback(DwtDialog.YES_BUTTON, this.saveAndGoAway, this, args);		
		this._app.dialogs["confirmMessageDialog"].registerCallback(DwtDialog.NO_BUTTON, this.discardAndGoAway, this, args);		
		this._app.dialogs["confirmMessageDialog"].popup();
	} else {
		this.newResource();
	}	
}

//private and protected methods
ZaResourceController.prototype._createUI = 
function () {
	//create accounts list view
	// create the menu operations/listeners first	
	this._contentView = this._view = new this.tabConstructor(this._container, this._app);

    this._initToolbar();
	//always add Help button at the end of the toolbar    
	this._toolbarOperations.push(new ZaOperation(ZaOperation.NONE));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));		

	this._toolbar = new ZaToolBar(this._container, this._toolbarOperations);    
		
	var elements = new Object();
	elements[ZaAppViewMgr.C_APP_CONTENT] = this._view;
	elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;		
	//this._app.createView(ZaZimbraAdmin._RESOURCE_VIEW, elements);
	var tabParams = {
			openInNewTab: true,
			tabId: this.getContentViewId()
		}
	this._app.createView(this.getContentViewId(), elements, tabParams) ;
	
	this._removeConfirmMessageDialog = new ZaMsgDialog(this._app.getAppCtxt().getShell(), null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON], this._app);			
	this._UICreated = true;
	this._app._controllers[this.getContentViewId ()] = this ;
}


/**
* saves the changes in the fields, calls modify or create on the current ZaResource
* @return Boolean - indicates if the changes were succesfully saved
**/
ZaResourceController.prototype._saveChanges  =
function () {
	//check if the XForm has any errors
	if(this._view.getMyForm().hasErrors()) {
		var errItems = this._view.getMyForm().getItemsInErrorState();
		var dlgMsg = ZaMsg.CORRECT_ERRORS;
		dlgMsg +=  "<br><ul>";
		var i = 0;
		for(var key in errItems) {
			if(i > 19) {
				dlgMsg += "<li>...</li>";
				break;
			}
			if(key == "size") continue;
			var label = errItems[key].getInheritedProperty("msgName");
			if(!label && errItems[key].getParentItem()) { //this might be a part of a composite
				label = errItems[key].getParentItem().getInheritedProperty("msgName");
			}
			if(label) {
				if(label.substring(label.length-1,1)==":") {
					label = label.substring(0, label.length-1);
				}
			}			
			if(label) {
				dlgMsg += "<li>";
				dlgMsg +=label;			
				dlgMsg += "</li>";
			}
			i++;
		}
		dlgMsg += "</ul>";
		this.popupMsgDialog(dlgMsg, true);
		return false;
	}
	//check if the data is copmlete 
	var tmpObj = this._view.getObject();
	var newName=null;
	
	//Check the data
	if(tmpObj.attrs == null ) {
		//show error msg
		this._errorDialog.setMessage(ZaMsg.ERROR_UNKNOWN, null, DwtMessageDialog.CRITICAL_STYLE, null);
		this._errorDialog.popup();		
		return false;	
	}
	
	ZaResource.prototype.setLdapAttrsFromSchedulePolicy.call(tmpObj);
	
	//check if need to rename
	if(this._currentObject && tmpObj.name != this._currentObject.name) {
		//var emailRegEx = /^([a-zA-Z0-9_\-])+((\.)?([a-zA-Z0-9_\-])+)*@(([a-zA-Z0-9\-])+\.)+([a-zA-Z0-9]{2,4})+$/;
		if(!AjxUtil.EMAIL_FULL_RE.test(tmpObj.name) ) {
			//show error msg
			this._errorDialog.setMessage(ZaMsg.ERROR_ACCOUNT_NAME_INVALID, null, DwtMessageDialog.CRITICAL_STYLE, null);
			this._errorDialog.popup();		
			return false;
		}
		newName = tmpObj.name;
	}

	var mods = new Object();
	
	if(!ZaResource.checkValues(tmpObj, this._app))
		return false;
		
	if(ZaSettings.ACCOUNTS_CHPWD_ENABLED) {
		//change password if new password is provided
		if(tmpObj.attrs[ZaResource.A_password]!=null && tmpObj[ZaResource.A2_confirmPassword]!=null && tmpObj.attrs[ZaResource.A_password].length > 0) {
			try {
				this._currentObject.changePassword(tmpObj.attrs[ZaResource.A_password]);
			} catch (ex) {
				this.popupErrorDialog(ZaMsg.FAILED_SAVE_ACCOUNT, ex, true);
				return false;				
				
			}
		}
	}
			
	var changeDetails = new Object();
	
	//check if need to rename
	if(newName) {
		changeDetails["newName"] = newName;
		try {
			this._currentObject.rename(newName);
		} catch (ex) {
			if(ex.code == ZmCsfeException.ACCT_EXISTS) {
				this.popupErrorDialog(ZaMsg.FAILED_RENAME_ACCOUNT_1, ex, true);
			} else {
				this._handleException(ex, "ZaResourceController.prototype._saveChanges", null, false);	
			} 
			return false;
		}
	}	
	
	//transfer the fields from the tmpObj to the _currentObject
	for (var a in tmpObj.attrs) {
		if(a == ZaResource.A_password || a == ZaItem.A_objectClass ||  a==ZaResource.A_mail || a == ZaItem.A_zimbraId) {
			continue;
		}	
		//check if the value has been modified
		if ((this._currentObject.attrs[a] != tmpObj.attrs[a]) && !(this._currentObject.attrs[a] == undefined && tmpObj.attrs[a] === "")) {
			if(a==ZaResource.A_uid) {
				continue; //skip uid, it is changed throw a separate request
			}
			if(tmpObj.attrs[a] instanceof Array) {
				if(tmpObj.attrs[a].join(",").valueOf() !=  this._currentObject.attrs[a].join(",").valueOf()) {
					mods[a] = tmpObj.attrs[a];
				}
			} else {
				mods[a] = tmpObj.attrs[a];
			}				
		}
	}

	//save changed fields
	try {	
		this._currentObject.modify(mods);
	} catch (ex) {
		if(ex.code == ZmCsfeException.ACCT_EXISTS) {
			this.popupErrorDialog(ZaMsg.FAILED_CREATE_ACCOUNT_1, ex, true);
		} else {
			this._handleException(ex, "ZaResourceController.prototype._saveChanges", null, false);	
		}
		return false;
	}
	
	return true;
};
