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
* @class Base class for all Controller classes in ZimbraAdmin UI
* @author Greg Solovyev
* @constructor 
* @see ZaAccountListController
* @see ZaCosListController
* @see ZaDomainListController
* @see ZaXFormViewController
*/
function ZaController(appCtxt, container, app, iKeyName) {

	if (arguments.length == 0) return;
	this._evtMgr = new AjxEventMgr();
	/**
	* The name of the current controller. This name is used as a key in {@link #initToolbarMethods}, {@link #setViewMethods} and {@link #initPopupMenuMethods} maps 
	**/	
	this._iKeyName = iKeyName;
	this._appCtxt = appCtxt;
	this._container = container;
	this._app = app;
	
	this._shell = appCtxt.getShell();
	this._appViews = new Object();   
	this._currentView = null;                            
	
	this._authenticating = false;

	this._loginDialog = appCtxt.getLoginDialog();

	this._loginDialog.registerCallback(this.loginCallback, this);
	this._errorDialog = appCtxt.getErrorDialog();
	this._msgDialog = appCtxt.getMsgDialog();
	if(this._app) {
		this._msgDialog = this._app.dialogs["msgDialog"] = appCtxt.getMsgDialog();
		this._msgDialog.setApp(app);
		this._errorDialog = this._app.dialogs["errorDialog"] = appCtxt.getErrorDialog();
		this._app.dialogs["confirmMessageDialog"] = this._app.dialogs["confirmMessageDialog"] = new ZaMsgDialog(this._shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON, DwtDialog.CANCEL_BUTTON], this._app);					
	    this._errorDialog.registerCallback(DwtDialog.OK_BUTTON, this._errorDialogCallback, this);
    	this._msgDialog.registerCallback(DwtDialog.OK_BUTTON, this._msgDialogCallback, this);    
	}

    this.objType = ZaEvent.S_ACCOUNT;
    this._helpURL = ZaController.helpURL;
}

/**
* A map of funciton references. Functions in this map are called one after another from 
* {@link #_initToolbar} method.
* The functions are called on the current instance of the controller. 
* member of  ZaController
* @see #_initToolbar
**/
ZaController.initToolbarMethods = new Object();
/**
* A map of funciton references. Functions in this map are called one after another from 
* {@link #_initPopupMenu} method.
* The functions are called on the current instance of the controller. 
* member of ZaController
* @see #_initPopupMenu
**/
ZaController.initPopupMenuMethods = new Object();
/**
* A map of funciton references. Functions in this map are called one after another from 
* {@link #_setView} method.
* The functions are called on the current instance of the controller. 
* member of ZaController
* @see #_setView
**/
ZaController.setViewMethods = new Object();

ZaController.helpURL = "/zimbraAdmin/adminhelp/html/WebHelp/administration_console_help.htm";
// Public methods
ZaController.prototype.toString = 
function() {
	return "ZaController";
}


ZaController.prototype.getProgressDialog =
function() {
	if (!this._app.dialogs["progressDialog"])
		this._app.dialogs["progressDialog"] = new ZaXProgressDialog(this._appCtxt.getShell(), this._app, "300px", "300px");
	return this._app.dialogs["progressDialog"];
}

ZaController.prototype.setDirty = 
function (isD) {
	//overwrite this method to disable toolbar buttons, for example, Save button
}

ZaController.prototype.setCurrentView =
function(view) {
	this._currentView = view;
}

ZaController.prototype.setEnabled = 
function(enable) {
	//abstract
//	throw new AjxException("This method is abstract", AjxException.UNIMPLEMENTED_METHOD, "ZaController.prototype.setEnabled");	
}

ZaController.prototype.popupErrorDialog = 
function(msg, ex, noExecReset,style)  {
	style = style ? style : DwtMessageDialog.CRITICAL_STYLE;
	if (!noExecReset)
		this._execFrame = {func: null, args: null, restartOnError: false};
	
	var detailStr = "";
	if(ex != null) {
		if(ex.msg) {
			detailStr += ZaMsg.ERROR_MESSAGE + "  ";
		    detailStr += ex.msg;
		    detailStr += "\n";			    
		}
		if(ex.code) {
			detailStr += ZaMsg.ERROR_CODE + "  ";
		    detailStr += ex.code;
		    detailStr += "\n";			    
		}
		if(ex.method) {
			detailStr += "Method:  ";
		    detailStr += ex.method;
		    detailStr += "\n";			    
		}
		if(ex.detail) {
			detailStr += ZaMsg.ERROR_DETAILS;
		    detailStr += ex.detail;
		    detailStr += "\n";			    
		}
		
		if(!detailStr || detailStr == "") {
			for (var ix in ex) {
				detailStr += ix;
				detailStr += ": ";
				detailStr += ex[ix].toString();
				detailStr += "\n";
			}
		}
	}
	// popup alert
	this._errorDialog.setMessage(msg, detailStr, style, ZaMsg.zimbraAdminTitle);
	
	if (!this._errorDialog.isPoppedUp()) {
		this._errorDialog.popup();
	}		

}

ZaController.prototype.popupMsgDialog = 
function(msg, noExecReset)  {
	if (!noExecReset)
		this._execFrame = {func: null, args: null, restartOnError: false};
	
	// popup alert
	this._msgDialog.setMessage(msg, DwtMessageDialog.INFO_STYLE, ZaMsg.zimbraAdminTitle);
	if (!this._msgDialog.isPoppedUp()) {
		this._msgDialog.popup();
	}
}

ZaController.prototype.getControllerForView =
function(view) {
//	DBG.println(AjxDebug.DBG1, "*** controller not found for view " + view);
	return this._appCtxt.getAppController();
}

/**
* @param nextViewCtrlr - the controller of the next view
* @param func		   - the method to call on the nextViewCtrlr in order to navigate to the next view
* @param params		   - arguments to pass to the method specified in func parameter
* Ovewrite this method in order to check if it is OK to leave the current view or 
* perform any actions before a user navigates away from the view.
**/
ZaController.prototype.switchToNextView = 
function (nextViewCtrlr, func, params) {
	func.call(nextViewCtrlr, params);
}

//Private/protected methods

/**
* This method finds an array of function references in {@link ZaController#setViewMethods} map and calls all the functions for the array.
* {@link #_iKeyName} is used to lookup the array of function references in the map.
* @private
**/
ZaController.prototype._setView =
function(entry) {
	//Instrumentation code start
	if(ZaController.setViewMethods[this._iKeyName]) {
		var methods = ZaController.setViewMethods[this._iKeyName];
		var cnt = methods.length;
		for(var i = 0; i < cnt; i++) {
			if(typeof(methods[i]) == "function") {
				try {
					methods[i].call(this,entry);
				} catch (ex) {
					this._handleException(ex, "ZaController.prototype._setView");
				}
			}
		}
	}	
	//Instrumentation code end	
}

//Listeners for default toolbar buttons (close, save, delete)
/**
* member of ZaController
* @param 	ev event object
* handles the Close button click. Returns to the previous view.
**/ 
ZaController.prototype.closeButtonListener =
function(ev) {
	this._app.popView();
}

ZaController.prototype._helpButtonListener =
function() {
	window.open(this._helpURL);
}
/**
* We do the whole schedule/execute thing to give the shell the opportunity to popup its "busy" 
* overlay so that user input is blocked. For example, if a search takes a while to complete, 
* we don't want the user's clicking on the search button to cause it to re-execute repeatedly 
* when the events arrive from the UI. Since the action is executed via win.setTimeout(), it
* must be a leaf action (scheduled actions are executed after the calling code returns to the
* UI loop). You can't schedule something, and then have subsequent code that depends on the 
* scheduled action. 
* @private
**/
ZaController.prototype._showLoginDialog =
function(bReloginMode) {
	ZaZimbraAdmin._killSplash();
	this._authenticating = true;
	this._loginDialog.setVisible(true, false);
	try {
		var uname = "";
		if(this.auth)
			uname = this.auth.uname;
		this._loginDialog.setFocus(uname,bReloginMode);
	} catch (ex) {
		// something is out of whack... just make the user relogin
		ZaZimbraAdmin.logOff();
	}
}

ZaController.prototype._handleException =
function(ex, method, params, restartOnError, obj) {
	DBG.dumpObj(ex);
	if (ex.code && 
			(ex.code == ZmCsfeException.SVC_AUTH_EXPIRED || 
				ex.code == ZmCsfeException.SVC_AUTH_REQUIRED || 
				ex.code == ZmCsfeException.NO_AUTH_TOKEN
			 )
		) 
	{
		try {
			var bReloginMode = true;
			if (ex.code == ZmCsfeException.SVC_AUTH_EXPIRED) 
			{
				if(this._app) {
					var dlgs = this._app.dialogs;
					for (var dlg in dlgs) {
						dlgs[dlg].popdown();
					}
				}
				this._execFrame = {obj: obj, func: method, args: params, restartOnError: restartOnError};
				this._loginDialog.registerCallback(this.loginCallback, this);
				this._loginDialog.setError(ZaMsg.ERROR_SESSION_EXPIRED);
				this._loginDialog.clearPassword();
			} else {
				this._loginDialog.setError(null);
				bReloginMode = false;
			}
			this._loginDialog.setReloginMode(bReloginMode);
			this._showLoginDialog(bReloginMode);
		} catch (ex) {
			
		}
	} 
	else 
	{
		this._execFrame = {obj: obj, func: method, args: params, restartOnError: restartOnError};
		this._errorDialog.registerCallback(DwtDialog.OK_BUTTON, this._errorDialogCallback, this);
		if(!ex.code) {
			this.popupErrorDialog(ZaMsg.JAVASCRIPT_ERROR + " in method " + method, ex, true);
		
		} else if (ex.code == ZmCsfeException.SOAP_ERROR) {
			this.popupErrorDialog(ZaMsg.SOAP_ERROR, ex, true);
		} else if (ex.code == ZmCsfeException.NETWORK_ERROR) {
			this.popupErrorDialog(ZaMsg.NETWORK_ERROR, ex, true);
		} else if (ex.code ==  ZmCsfeException.SVC_PARSE_ERROR) {
			this.popupErrorDialog(ZaMsg.PARSE_ERROR, ex, true);
		} else if (ex.code ==  ZmCsfeException.SVC_PERM_DENIED) {
			this.popupErrorDialog(ZaMsg.PERMISSION_DENIED, ex, true);
		} else if (ex.code == ZmCsfeException.ACCT_NO_SUCH_ACCOUNT) {
			this.popupErrorDialog(ZaMsg.ERROR_NO_SUCH_ACCOUNT, ex, true);
		} else if (ex.code == ZmCsfeException.NO_SUCH_DISTRIBUTION_LIST) {
			this.popupErrorDialog(ZaMsg.NO_SUCH_DISTRIBUTION_LIST, ex, true);
		} else if(ex.code == ZmCsfeException.ACCT_EXISTS) {
			this.popupErrorDialog(ZaMsg.ERROR_ACCOUNT_EXISTS, ex, true);
		} else if(ex.code == ZmCsfeException.VOLUME_NO_SUCH_PATH) {
			this.popupErrorDialog(ZaMsg.ERROR_INVALID_VOLUME_PATH, ex, true);
		} else if(ex.code == ZmCsfeException.LICENSE_ERROR) {
			this.popupErrorDialog(ZaMsg.ERROR_LICENSE, ex, true);
		} else if (ex.code == ZmCsfeException.SVC_INVALID_REQUEST) {
			this.popupErrorDialog(ZaMsg.ERROR_INVALID_REQUEST, ex, true);
		} else if (ex.code == ZmCsfeException.TOO_MANY_SEARCH_RESULTS) {
			this.popupErrorDialog(ZaMsg.ERROR_TOO_MANY_SEARCH_RESULTS, ex, true);
		} else if (ex.code == ZmCsfeException.NO_SUCH_DOMAIN) {
			this.popupErrorDialog(ZaMsg.ERROR_NO_SUCH_DOMAIN, ex, true);
		}else if (ex.code == ZmCsfeException.CSFE_SVC_ERROR || 
					ex.code == ZmCsfeException.SVC_FAILURE || 
						(typeof(ex.code) == 'string' && ex.code && ex.code.match(/^(service|account|mail)\./))

				   ) {
			this.popupErrorDialog(ZaMsg.SERVER_ERROR, ex, true);
		} else {
			//search for error code
			var gotit = false;
			for(var ix in ZmCsfeException) {
				if(ZmCsfeException[ix] == ex.code) {
					this.popupErrorDialog(ZaMsg.SERVER_ERROR, ex, true);
					gotit = true;
					break;
				}
			}
			if(!gotit)	
				this.popupErrorDialog(ZaMsg.SERVER_ERROR, ex, true);		
		}
	}
}

ZaController.prototype._doAuth = 
function(username, password) {
	ZmCsfeCommand.clearAuthToken();
	try {
		//hide login dialog
		this._hideLoginDialog();
		//show splash screen
		ZaZimbraAdmin.showSplash(this._shell);
		var callback = new AjxCallback(this, this.authCallback);	
		this.auth = new ZaAuthenticate(this._appCtxt);
		this.auth.execute(username, password,callback);
	} catch (ex) {
		this._showLoginDialog();
		if (ex.code == ZmCsfeException.ACCT_AUTH_FAILED 
		   //HC: this code doesn't exist || ex.code == ZmCsfeException.INVALID_REQUEST
		   ) 
		{
			this._loginDialog.setError(ZaMsg.ERROR_AUTH_FAILED);
			return;
		} else if(ex.code == ZmCsfeException.SVC_PERM_DENIED) {
			this._loginDialog.setError(ZaMsg.ERROR_AUTH_NO_ADMIN_RIGHTS);
			return;
		} else if (ex.code == ZmCsfeException.ACCT_CHANGE_PASSWORD) {
			this._loginDialog.disablePasswordField(true);
			this._loginDialog.disableUnameField(true);
			this._loginDialog.showNewPasswordFields();
			this._loginDialog.registerCallback(this.changePwdCallback, this);
		} else {
			this.popupMsgDialog(ZaMsg.SERVER_ERROR, ex); 
		}
	}
}

ZaController.prototype._hideLoginDialog =
function(clear) {
	this._loginDialog.setVisible(false);
	if(clear) {
		this._loginDialog.setError(null);
		this._loginDialog.clearPassword();
//		this._loginDialog.clearKeyHandlers();
	}
}


/**
* This method is called when we receive AuthResponse
**/
ZaController.prototype.authCallback = 
function (resp) {
	//auth request came back
	 ZaController.changePwdCommand = null;
	//if login failed - hide splash screen, show login dialog
	if(resp.isException && resp.isException()) {
		this._showLoginDialog();
		var ex = resp.getException();
		if (ex.code == ZmCsfeException.ACCT_AUTH_FAILED 
		    //HC: this code doesn't exist || ex.code == ZmCsfeException.INVALID_REQUEST 
		   ) 
		{
			this._loginDialog.setError(ZaMsg.ERROR_AUTH_FAILED);
			this._loginDialog.clearPassword();
			return;
		} else if(ex.code == ZmCsfeException.SVC_PERM_DENIED) {
			this._loginDialog.setError(ZaMsg.ERROR_AUTH_NO_ADMIN_RIGHTS);
			this._loginDialog.clearPassword();
			return;
		} else if (ex.code == ZmCsfeException.ACCT_CHANGE_PASSWORD) {
			this._loginDialog.disablePasswordField(true);
			this._loginDialog.disableUnameField(true);
			this._loginDialog.showNewPasswordFields();
			this._loginDialog.registerCallback(this.changePwdCallback, this);
		} else if (ex.code == ZmCsfeException.PASSWORD_RECENTLY_USED ||
			ex.code == ZmCsfeException.PASSWORD_CHANGE_TOO_SOON) {
			var msg = ex.code == ZmCsfeException.ACCT_PASS_RECENTLY_USED ? ZaMsg.errorPassRecentlyUsed : (ZaMsg.errorPassChangeTooSoon);
			this._loginDialog.setError(msg);
			this._loginDialog.clearPassword();
			this._loginDialog.setFocus();
		} else if (ex.code == ZmCsfeException.PASSWORD_LOCKED) {
			// re-enable username and password fields
			this._loginDialog.disablePasswordField(false);
			this._loginDialog.disableUnameField(false);
			this._loginDialog.setError(ZaMsg.errorPassLocked);
		} else {
			this.popupMsgDialog(ZaMsg.SERVER_ERROR, ex); 
		}
	} else {
		//if login succesful hide splash screen, start application
		try {
			var authToken, sessionId;
	 		var response = resp.getResponse();
	 		var body = response.Body;		
	 		
	 		ZmCsfeCommand.setAuthToken(body.AuthResponse.authToken, -1, body.AuthResponse.sessionId.id);
	 		
			//Instrumentation code start
			if(ZaAuthenticate.processResponseMethods) {
				var cnt = ZaAuthenticate.processResponseMethods.length;
				for(var i = 0; i < cnt; i++) {
					if(typeof(ZaAuthenticate.processResponseMethods[i]) == "function") {
						ZaAuthenticate.processResponseMethods[i].call(this,resp);
					}
				}
			}	
			//Instrumentation code end		 		
			this._hideLoginDialog(true);
			this._appCtxt.getAppController().startup();
		} catch (ex) {
			this._handleException(ex, "ZaController.prototype.authCallback");
		}
	}
}
/*********** Login dialog Callbacks */

ZaController.prototype.loginCallback =
function(uname, password) {
	//this._schedule(this._doAuth, {username: uname, password: password});
	this._doAuth(uname,password);
}

ZaController.prototype.changePwdCallback =
function(uname, oldPass, newPass, conPass) {
	if (newPass == null || newPass == "" || conPass == null || conPass == "") {
		this._loginDialog.setError(ZaMsg.enterNewPassword);
		return;
	}
	
	if (newPass != conPass) {
		this._loginDialog.setError(ZaMsg.bothNewPasswordsMustMatch);
		return;
	}

    var soapDoc = AjxSoapDoc.create("ChangePasswordRequest", "urn:zimbraAccount");
    var el = soapDoc.set("account", uname);
    el.setAttribute("by", "name");
    soapDoc.set("oldPassword", oldPass);
    soapDoc.set("password", newPass);
    var resp = null;
    try {
    	if(ZaController.changePwdCommand)
    		return;
    		
		ZaController.changePwdCommand = new ZmCsfeCommand();
		resp = ZaController.changePwdCommand.invoke({soapDoc: soapDoc, noAuthToken: true, noSession: true}).Body.ChangePasswordResponse;
	
		if (resp) {
			ZaZimbraAdmin.showSplash(this._shell);
			var callback = new AjxCallback(this, this.authCallback);	
			this.auth = new ZaAuthenticate(this._appCtxt);
			this.auth.execute(uname, newPass,callback);
		}		
    } catch (ex) {
	    ZaController.changePwdCommand = null;
		//DBG.dumpObj(ex);
		// XXX: for some reason, ZmCsfeException consts are fubar
		if (ex.code == ZmCsfeException.ACCT_PASS_RECENTLY_USED ||
			ex.code == ZmCsfeException.ACCT_PASS_CHANGE_TOO_SOON) {
			var msg = ex.code == ZmCsfeException.ACCT_PASS_RECENTLY_USED
				? ZaMsg.errorPassRecentlyUsed
				: (ZaMsg.errorPassChangeTooSoon);
			this._loginDialog.setError(msg);
			this._loginDialog.setFocus();
		} else if (ex.code == ZmCsfeException.ACCT_PASS_LOCKED)	{
			// re-enable username and password fields
			this._loginDialog.disablePasswordField(false);
			this._loginDialog.disableUnameField(false);
			this._loginDialog.setError(ZaMsg.errorPassLocked);
		} else {
			this._handleException(ex, "ZaController.prototype.changePwdCallback");	
		}
	}
}

/*********** Msg dialog Callbacks */

ZaController.prototype._errorDialogCallback =
function() {
	this._app.dialogs["errorDialog"].popdown();
	if (this._execFrame) {
		if (this._execFrame.restartOnError && !this._authenticating)
			this._execFrame.method.apply(this, this._execFrame.args);
		this._execFrame = null;
	}
}

ZaController.prototype._msgDialogCallback =
function() {
	this._msgDialog.popdown();
	if (this._execFrame) {
		if (this._execFrame.restartOnError && !this._authenticating)
			this._execFrame.method.apply(this, this._execFrame.args);
		this._execFrame = null;
	}	
}

/**
* This method finds an array of function references in {@link ZaController#initToolbarMethods} map and calls all the functions for the array.
* {@link #_iKeyName} is used to lookup the array of function references in the map.
* @private
**/
ZaController.prototype._initToolbar = function () {
	//Instrumentation code start
	if(ZaController.initToolbarMethods[this._iKeyName]) {
		var methods = ZaController.initToolbarMethods[this._iKeyName];
		var cnt = methods.length;
		for(var i = 0; i < cnt; i++) {
			if(typeof(methods[i]) == "function") {
				try {
					methods[i].call(this);
				} catch (ex) {
					this._handleException(ex, "ZaController.prototype._initToolbar");
				}
			}
		}
	}	
	//Instrumentation code end
}

/**
* This method finds an array of function references in {@link ZaController#initPopupMenuMethods} map and calls all the functions for the array.
* {@link #_iKeyName} is used to lookup the array of function references in the map.
* @private
**/
ZaController.prototype._initPopupMenu = function () {
	//Instrumentation code start
	if(ZaController.initPopupMenuMethods[this._iKeyName]) {
		var methods = ZaController.initPopupMenuMethods[this._iKeyName];
		var cnt = methods.length;
		for(var i = 0; i < cnt; i++) {
			if(typeof(methods[i]) == "function") {
				try {
					methods[i].call(this);
				} catch (ex) {
					this._handleException(ex, "ZaController.prototype._initPopupMenu");
				}
			}
		}
	}	
	//Instrumentation code end
}

ZaController.prototype.closeCnfrmDlg = 
function () {
	if(this._app.dialogs["confirmMessageDialog"])
		this._app.dialogs["confirmMessageDialog"].popdown();	
}

/**
* public getToolBar
* @return reference to the toolbar
**/
ZaController.prototype.getToolBar = 
function () {
	if (this._toolbar != null)	
		return this._toolbar;	
	else
		return null;
}

/**
* Adds listener to creation of an ZaAccount 
* @param listener
**/
ZaController.prototype.addCreationListener = 
function(listener) {
	this._evtMgr.addListener(ZaEvent.E_CREATE, listener);
}

/**
* Removes listener to creation of an ZaAccount 
* @param listener
**/
ZaController.prototype.removeCreationListener = 
function(listener) {
	this._evtMgr.removeListener(ZaEvent.E_CREATE, listener);    	
}

/**
* Adds listener to removal of an ZaAccount 
* @param listener
**/
ZaController.prototype.addRemovalListener = 
function(listener) {
	this._evtMgr.addListener(ZaEvent.E_REMOVE, listener);
}

/**
* Removes listener to removal of an ZaAccount 
* @param listener
**/
ZaController.prototype.removeRemovalListener = 
function(listener) {
	this._evtMgr.removeListener(ZaEvent.E_REMOVE, listener);    	
}

/**
* member of ZaXFormViewController
* Adds listener to modifications in the contained ZaAccount 
* @param listener
**/
ZaController.prototype.addChangeListener = 
function(listener) {
	this._evtMgr.addListener(ZaEvent.E_MODIFY, listener);
}

/**
* member of ZaXFormViewController
* Removes listener to modifications in the controlled ZaAccount 
* @param listener
**/
ZaController.prototype.removeChangeListener = 
function(listener) {
	this._evtMgr.removeListener(ZaEvent.E_MODIFY, listener);    	
}


/**
* member of ZaXFormViewController
*	Private method that notifies listeners that a new object is created
* 	@param details
*/
ZaController.prototype.fireCreationEvent =
function(details) {
	try {
		if (this._evtMgr.isListenerRegistered(ZaEvent.E_CREATE)) {
			var evt = new ZaEvent(this.objType);
			evt.set(ZaEvent.E_CREATE, this);
			if(details)
				evt.setDetails(details);
				
			this._evtMgr.notifyListeners(ZaEvent.E_CREATE, evt);
		}
	} catch (ex) {
		this._handleException(ex, "ZaXFormViewController.prototype.fireCreationEvent", details, false);	
	}

}
/**
*	Private method that notifies listeners to that the controlled ZaAccount is (are) removed
* 	@param details
*/
ZaController.prototype.fireRemovalEvent =
function(details) {
	try {
		if (this._evtMgr.isListenerRegistered(ZaEvent.E_REMOVE)) {
			var evt = new ZaEvent(this.objType);
			evt.set(ZaEvent.E_REMOVE, this);
			evt.setDetails(details);
			this._evtMgr.notifyListeners(ZaEvent.E_REMOVE, evt);
		}
	} catch (ex) {
		this._handleException(ex, "ZaController.prototype.fireRemovalEvent", details, false);	
	}
}

/**
* member of ZaXFormViewController
*	Method that notifies listeners to that the controlled object is changed
* 	@param details {String}
*/
ZaController.prototype.fireChangeEvent =
function(details) {
	try {
		if (this._evtMgr.isListenerRegistered(ZaEvent.E_MODIFY)) {
			var evt = new ZaEvent(this.objType);
			evt.set(ZaEvent.E_MODIFY, this);
			if(details)
				evt.setDetails(details);			
			this._evtMgr.notifyListeners(ZaEvent.E_MODIFY, evt);
		}
	} catch (ex) {
		this._handleException(ex, "ZaXFormViewController.prototype.fireChangeEvent", null, false);	
	}
}
//item should be an xform item
ZaController.showTooltip =
function (event, item) {
	var dwtEv = new DwtUiEvent(true);
	dwtEv.setFromDhtmlEvent(event)
	var shell = DwtShell.getShell(window);
	var tooltip = shell.getToolTip();
	tooltip.setContent(item.getInheritedProperty("toolTipContent"));
	tooltip.popup(dwtEv.docX, dwtEv.docY);
}

ZaController.hideTooltip =
function (event) {
	var shell = DwtShell.getShell(window);
	var tooltip = shell.getToolTip();
	tooltip.popdown();
}
