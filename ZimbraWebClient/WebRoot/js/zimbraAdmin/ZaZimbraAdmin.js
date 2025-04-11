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
* Creates a controller to run ZimbraAdmin. Do not call directly, instead use the run()
* factory method.
* @constructor ZimbraAdmin
* @param appCtx
* @class ZimbraAdmin
* This class is responsible for bootstrapping the ZimbraAdmin application.
*/
function ZaZimbraAdmin(appCtxt) {
	ZaZimbraAdmin._instance = this;
	ZaController.call(this, appCtxt, null, null,"ZaZimbraAdmin");

	ZaZimbraAdmin.showSplash(this._shell);
	
	appCtxt.setAppController(this);

		
	// handles to various apps
	this._appFactory = new Object();
	this._appFactory[ZaZimbraAdmin.ADMIN_APP] = ZaApp;
 
 	this.startup();

    this.aboutDialog = new ZaAboutDialog(this._shell,null,ZaMsg.about_title);
}

ZaZimbraAdmin.prototype = new ZaController;
ZaZimbraAdmin.prototype.constructor = ZaZimbraAdmin;
ZaZimbraAdmin._instance = null;

ZaZimbraAdmin.ADMIN_APP = "admin";

ZaZimbraAdmin.VIEW_INDEX = 0;

ZaZimbraAdmin._ADDRESSES = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ACCOUNTS_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ALIASES_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._DISTRIBUTION_LISTS_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._SYS_CONFIG = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._GLOBAL_SETTINGS = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._SERVERS_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._DOMAINS_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._COS_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._MONITORING = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._STATUS = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._STATISTICS = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._STATISTICS_BY_SERVER = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._SEARCH_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._SEARCH_BUILDER_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._SEARCH_BUILDER_TOOLBAR_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ZIMLET_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW = ZaZimbraAdmin.VIEW_INDEX++;

ZaZimbraAdmin._SERVER_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._DOMAIN_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._COS_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ACCOUNT_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ALIAS_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._DL_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._HELP_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._MIGRATION_WIZ_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._POSTQ_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._POSTQ_BY_SERVER_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._RESOURCE_VIEW = ZaZimbraAdmin.VIEW_INDEX++;
ZaZimbraAdmin._ZIMLET_VIEW = ZaZimbraAdmin.VIEW_INDEX++;

ZaZimbraAdmin.MSG_KEY = new Object();
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ACCOUNTS_LIST_VIEW] = "Accounts_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._SEARCH_LIST_VIEW] = "Search_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ACCOUNT_VIEW] = "Accounts_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ALIASES_LIST_VIEW] = "Aliases_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ALIAS_VIEW] = "Aliases_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._DISTRIBUTION_LISTS_LIST_VIEW] = "DL_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._DL_VIEW] = "DL_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._GLOBAL_SETTINGS] = "GlobalConfig_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._SERVERS_LIST_VIEW] = "Servers_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._DOMAINS_LIST_VIEW] = "Domain_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._COS_LIST_VIEW] = "COS_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._STATISTICS] = "GlobalStats_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._STATISTICS_BY_SERVER] = "ServerStats_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._SERVER_VIEW] = "Servers_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._HELP_VIEW] = "Help_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._DOMAIN_VIEW] = "Domain_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._COS_VIEW] = "COS_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._STATUS] = "Status_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._MIGRATION_WIZ_VIEW] = "Migration_wiz_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._POSTQ_VIEW] = "PostQ_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._POSTQ_BY_SERVER_VIEW] = "PostQ_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._RESOURCE_VIEW] = "Resources_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW] = "AdminZimlets_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ZIMLET_LIST_VIEW] = "Zimlets_view_title";
ZaZimbraAdmin.MSG_KEY[ZaZimbraAdmin._ZIMLET_VIEW] = "Zimlets_view_title";
// do not change the name of the cookie! SoapServlet looks for it
ZaZimbraAdmin._COOKIE_NAME = "ZM_ADMIN_AUTH_TOKEN";
	
// Public methods

ZaZimbraAdmin.prototype.toString = 
function() {
	return "ZaZimbraAdmin";
}

/**
* Sets up ZimbraMail, and then starts it by calling its constructor. It is assumed that the
* CSFE is on the same host.
*
* @param domain		the host that we're running on
*/
ZaZimbraAdmin.run =
function(domain) {
	if(window._dwtShell)
		return;
	if(!DBG)
		DBG = new AjxDebug(AjxDebug.NONE, null, false);
		
	ZmCsfeCommand.setServerUri(location.protocol+"//" + domain + ZaSettings.CSFE_SERVER_URI);
	ZmCsfeCommand.setCookieName(ZaZimbraAdmin._COOKIE_NAME);
	
	//License information will be load after the login and in the com_zimbra_license.js
	ZaServerVersionInfo.load();
	// Create the global app context
	var appCtxt = new ZaAppCtxt();

	// Create the shell
	var userShell = window.document.getElementById(ZaSettings.get(ZaSettings.SKIN_SHELL_ID));
	var shell = new DwtShell(null, false, null, userShell);
    appCtxt.setShell(shell);    
	
	/* Register our keymap and global key action handler with the shell's keyboard manager 
	 * CURRENTLY use $set: kbnav. 
	 */
	this._kbMgr = shell.getKeyboardMgr();
	this._kbMgr.registerKeyMap(new ZaKeyMap());
	this._kbMgr.pushDefaultHandler(this);
	
    // Go!
    var lm = new ZaZimbraAdmin(appCtxt);
}
ZaZimbraAdmin.prototype.getKeymapNameToUse = function () {
	if (this._app && this._app.getCurrentController()) {
		var c = this._app.getCurrentController();
		if (c && c.handleKeyAction)
			return c.toString();
	}
	return "ZaGlobal";
}

ZaZimbraAdmin.prototype.handleKeyAction = function () {
	switch (actionCode) {
		case ZaKeyMap.DBG_NONE:
			alert("Setting domain search limit to:" + AjxDebug.NONE);
			DBG.setDebugLevel(AjxDebug.NONE);
			break;
			
		case ZaKeyMap.DBG_1:
						alert("Setting domain search limit to:" + AjxDebug.DBG1);
			DBG.setDebugLevel(AjxDebug.DBG1);
			break;
			
		case ZaKeyMap.DBG_2:
			alert("Setting domain search limit to:" + AjxDebug.DBG2);
			DBG.setDebugLevel(AjxDebug.DBG2);
			break;
			
		case ZaKeyMap.DBG_3:
			alert("Setting domain search limit to:" + AjxDebug.DBG3);
			DBG.setDebugLevel(AjxDebug.DBG3);
			break;
			
		default: {
			
			if (this._app && this._app.getCurrentController()) {
				var c = this._app.getCurrentController();
				if (c && c.handleKeyAction)
					return c.handleKeyAction(actionCode, ev);
			} else {
				return false;
			}
			break;
		}
	}
	return true;
}
ZaZimbraAdmin.getInstance = function() {
	if(ZaZimbraAdmin._instance) {
		return ZaZimbraAdmin._instance;
	} else {
		ZaZimbraAdmin.run(document.domain);
		return ZaZimbraAdmin._instance;
	}
}

/**
* Returns a handle to the given app.
*
* @param appName	an app name
*/
ZaZimbraAdmin.prototype.getApp =
function() {
	return this._app;	
}

ZaZimbraAdmin.prototype.getAdminApp = 
function() {
	return this._app;
}

/**
* Returns a handle to the app view manager.
*/
ZaZimbraAdmin.prototype.getAppViewMgr =
function() {
	return this._appViewMgr;
}

/**
* Returns a handle to the overview panel controller.
*/

ZaZimbraAdmin.prototype.getOverviewPanelController =
function() {
	if (this._overviewPanelController == null)
		this._overviewPanelController = new ZaOverviewPanelController(this._appCtxt, this._shell, this._app);
	return this._overviewPanelController;
}

/**
* Sets the name of the currently active app. Done so we can figure out when an
* app needs to be launched.
*
* @param appName	the app
*/
ZaZimbraAdmin.prototype.setActiveApp =
function(appName) {
//	this._activeApp = appName;
}

ZaZimbraAdmin.logOff =
function() {
	ZmCsfeCommand.clearAuthToken();
	window.onbeforeunload = null;
	
	// NOTE: Mozilla sometimes handles UI events while the page is
	//       unloading which references classes and objects that no
	//       longer exist. So we put up the busy veil and reload
	//       after a short delay.
	var shell = DwtShell.getShell(window);
	shell.setBusy(true);
	
	var locationStr = location.protocol + "//" + location.hostname + ((location.port == '80')? "" : ":" +location.port) + "/zimbraAdmin";
	var act = new AjxTimedAction(null, ZaZimbraAdmin.redir, [locationStr]);
	AjxTimedAction.scheduleAction(act, 100);
}

ZaZimbraAdmin.redir =
function(locationStr){
	window.location = locationStr;
}


// Start up the ZimbraMail application
ZaZimbraAdmin.prototype.startup =
function() {

	this._appViewMgr = new ZaAppViewMgr(this._shell, this, true);
								        
	try {
		//if we're not logged in we will be thrown out here
		var soapDoc = AjxSoapDoc.create("GetInfoRequest", "urn:zimbraAccount", null);	
		var command = new ZmCsfeCommand();
		var params = new Object();
		params.soapDoc = soapDoc;	
		params.noSession = true;
		var resp = command.invoke(params);
		//var resp = ZmCsfeCommand.invoke(soapDoc, null, null, null, false);		
		//initialize my rights
		//ZaZimbraAdmin.initInfo (resp);
		if(!ZaSettings.initialized)
			ZaSettings.init();
		else
			ZaZimbraAdmin._killSplash();
		
	} catch (ex) {

		if(ex && ex.code != ZmCsfeException.NO_AUTH_TOKEN && ex.code != ZmCsfeException.SVC_AUTH_EXPIRED) {
			if(!ZaSettings.initialized)
				ZaSettings.init();
			else
				ZaZimbraAdmin._killSplash();
		}	
					
		this._handleException(ex, "ZaZimbraAdmin.prototype.startup", null, true);
	}
}

//process the GetInfoRequest response to set the domainAdminMaxMailQuota value in MB
/*
ZaZimbraAdmin.initInfo =
function (resp) {
	if (resp && resp.Body && resp.Body.GetInfoResponse && resp.Body.GetInfoResponse.attrs && resp.Body.GetInfoResponse.attrs.attr){
		var attrsArr = resp.Body.GetInfoResponse.attrs.attr ;
		for ( var i=0; i < attrsArr.length; i ++) {
			if (attrsArr[i].name == "zimbraDomainAdminMaxMailQuota") {
				var v = attrsArr[i]._content ;
				if (v != null && v.length > 0) {
					v = v / 1048576 ;
					if(v != Math.round(v)) {
						v= Number(v).toFixed(2);
	  				}
				}
				ZaZimbraAdmin.domainAdminMaxMailQuota = v ;
			}
		}
	}
}*/

ZaZimbraAdmin.prototype._setLicenseStatusMessage = function () {
	if ((typeof ZaLicense == "function") && (ZaSettings.LICENSE_ENABLED)){
		ZaLicense.setLicenseStatus(this);
	}
};

ZaZimbraAdmin.prototype.setStatusMsg = 
function(msg, clear) {
	this._statusBox.setText(msg);
	
	//HC: Why it has the ZmZimbraMail reference? Somebody please remove it.
	if (msg && clear) {
		var act = new AjxTimedAction(null, ZmZimbraMail._clearStatus, [this._statusBox]);
		AjxTimedAction.scheduleAction(act, ZmZimbraMail.STATUS_LIFE);
	}
}

ZaZimbraAdmin._clearStatus = 
function(statusBox) {
	statusBox.setText("");
	statusBox.getHtmlElement().className = "statusBox";
}


ZaZimbraAdmin.prototype._createAppChooser =
function() {
	var buttons = new Array();
	
	if (ZaSettings.ADDRESSES_ENABLED)
		buttons.push(ZaAppChooser.B_ADDRESSES);
	if (ZaSettings.SYSTEM_CONFIG_ENABLED)
		buttons.push(ZaAppChooser.B_SYSTEM_CONFIG);
	if (ZaSettings.MONITORING_ENABLED)
		buttons.push(ZaAppChooser.B_MONITORING);

		
	buttons.push(ZaAppChooser.SEP, ZaAppChooser.B_HELP,ZaAppChooser.B_MIGRATION_WIZ, ZaAppChooser.B_LOGOUT);
	var appChooser = new ZaAppChooser(this._shell, null, buttons);
	
	var buttonListener = new AjxListener(this, this._appButtonListener);
	for (var i = 0; i < buttons.length; i++) {
		var id = buttons[i];
		if (id == ZaAppChooser.SEP) continue;
		var b = appChooser.getButton(id);
		b.addSelectionListener(buttonListener);
	}

	return appChooser;
}

ZaZimbraAdmin.prototype._createBanner =
function() {
	// The LogoContainer style centers the logo
	var banner = new DwtComposite(this._shell, "LogoContainer", Dwt.ABSOLUTE_STYLE);
	var html = new Array();
	var i = 0;
	html[i++] = "<a href='";
	html[i++] = ZaSettings.LOGO_URI;
	html[i++] = "' target='_blank'><div class='"+AjxImg.getClassForImage("AppBanner")+"'></div></a>";
	banner.getHtmlElement().innerHTML = html.join("");
	return banner;
}
// Private methods

ZaZimbraAdmin._killSplash =
function() {
	if(ZaZimbraAdmin._splashScreen)
		ZaZimbraAdmin._splashScreen.setVisible(false);
}

ZaZimbraAdmin.showSplash =
function(shell) {
	if(ZaZimbraAdmin._splashScreen)
		ZaZimbraAdmin._splashScreen.setVisible(true);
	else {
		ZaZimbraAdmin._splashScreen = new ZaSplashScreen(shell);
	}
}
ZaZimbraAdmin.prototype._appButtonListener =
function(ev) {
	//var searchController = this._appCtxt.getSearchController();
	var id = ev.item.getData(Dwt.KEY_ID);
	switch (id) {
		case ZaAppChooser.B_MONITORING:

			if(this._app.getCurrentController()) {
				this._app.getCurrentController().switchToNextView(this._app.getStatusViewController(),ZaStatusViewController.prototype.show, null);
			} else {					
				this._app.getStatusViewController().show();
			}
			break;		
		case ZaAppChooser.B_SYSTEM_CONFIG:
			if(this._app.getCurrentController()) {
				this._app.getCurrentController().switchToNextView(this._app.getServerListController(), ZaServerListController.prototype.show, ZaServer.getAll(this._app));
			} else {					
				this._app.getServerListController().show(ZaServer.getAll(this._app));
			}
			break;		
		case ZaAppChooser.B_ADDRESSES:
			this._showAccountsView([ZaItem.ACCOUNT,ZaItem.DL,ZaItem.ALIAS, ZaItem.RESOURCE],ev);
			break;	
		case ZaAppChooser.B_HELP:
			if(this._app.getCurrentController()) {
				this._app.getCurrentController().switchToNextView(this._app.getHelpViewController(), ZaHelpViewController.prototype.show, null);
			} else {					
				this._app.getHelpViewController().show();
			}
			break;	
		case ZaAppChooser.B_MIGRATION_WIZ:
			if(this._app.getCurrentController()) {
				this._app.getCurrentController().switchToNextView(this._app.getMigrationWizController(), ZaMigrationWizController.prototype.show, null);
			} else {					
				this._app.getMigrationWizController().show();
			}
			break;	
							
		case ZaAppChooser.B_LOGOUT:
			ZaZimbraAdmin.logOff();
			break;
	}
}

ZaZimbraAdmin.prototype._showAccountsView = function (defaultType, ev){
/*	var queryHldr = this._getCurrentQueryHolder();
	queryHldr.isByDomain = false;
	queryHldr.byValAttr = false;
	queryHldr.queryString = "";
	queryHldr.types = new Array();
	if(typeof(defaultType) == 'object' && defaultType.length) {
		for(var i = 0; i < defaultType.length; i++) {
			queryHldr.types[i] = ZaSearch.TYPES[defaultType[i]];
		}
	} else {
		queryHldr.types = [ZaSearch.TYPES[defaultType]];
	}
	var acctListController = this._app.getAccountListController();
	acctListController.setPageNum(1);
	queryHldr.fetchAttrs = ZaSearch.standardAttributes;
	
	if(this._app.getCurrentController()) {
		this._app.getCurrentController().switchToNextView(acctListController, ZaAccountListController.prototype.search,queryHldr);
	} else {					
		acctListController.search(queryHldr);
	}*/

	var acctListController = this._app.getAccountListController();
	acctListController.setPageNum(1);	
	acctListController.setQuery("");
	acctListController.setSortOrder("1");
	acctListController.setSortField(ZaAccount.A_name);
	var types = [];
	if(typeof(defaultType) == 'object' && defaultType.length) {
		for(var i = 0; i < defaultType.length; i++) {
			types.push(ZaSearch.TYPES[defaultType[i]]);
		}
	} else {
		types.push(ZaSearch.TYPES[defaultType]);
	}	
	
	acctListController.setSearchTypes(types);

	if(defaultType == ZaItem.DL) {
		acctListController.setFetchAttrs(ZaDistributionList.searchAttributes);
	} else if (defaultType == ZaItem.RESOURCE){
		acctListController.setFetchAttrs(ZaResource.searchAttributes);
	} else {
		acctListController.setFetchAttrs(ZaSearch.standardAttributes);
	}	
	
	if(this._app.getCurrentController()) {
		this._app.getCurrentController().switchToNextView(acctListController, ZaAccountListController.prototype.show,true);
	} else {					
		acctListController.show(true);
	}
};

ZaZimbraAdmin.prototype._getCurrentQueryHolder = 
function () {
	var srchField = this._app.getAccountListController()._searchField;
	var curQuery = new ZaSearchQuery("", ZaZimbraAdmin._accountTypesArray, false, "");							
	if(srchField) {
		var obj = srchField.getObject();
		if(obj) {
			curQuery.types = new Array();
			if(obj[ZaSearch.A_fAliases]=="TRUE") {
				curQuery.types.push(ZaSearch.ALIASES);
			}
			if(obj[ZaSearch.A_fdistributionlists]=="TRUE") {
				curQuery.types.push(ZaSearch.DLS);
			}			
			if(obj[ZaSearch.A_fAccounts]=="TRUE") {
				curQuery.types.push(ZaSearch.ACCOUNTS);
			}			
		}
	}
	return curQuery;
}
/**
* Creates an app object, which doesn't necessarily do anything just yet.
**/
ZaZimbraAdmin.prototype._createApp =
function() {
	this._app = new ZaApp(this._appCtxt, this._shell);	
}


/**
* Launching an app causes it to create a view (if necessary) and display it. The view that is created is up to the app.
* Since most apps schedule an action as part of their launch, a call to this function should not be
* followed by any code that depends on it (ie, it should be a leaf action).
**/
ZaZimbraAdmin.prototype._launchApp =
function() {
	if (!this._app)
		this._createApp();

    this._appCtxt.setClientCmdHdlr(new ZaClientCmdHandler(this._app));
    //draw stuff
	var elements = new Object();
	elements[ZaAppViewMgr.C_SASH] = new DwtSash(this._shell, DwtSash.HORIZONTAL_STYLE,"console_inset_app_l", 20);
	elements[ZaAppViewMgr.C_BANNER] = this._createBanner();		
	elements[ZaAppViewMgr.C_APP_CHOOSER] = this._createAppChooser();
	elements[ZaAppViewMgr.C_STATUS] = this._statusBox = new DwtText(this._shell, "statusBox", Dwt.ABSOLUTE_STYLE);
	this._statusBox.setScrollStyle(Dwt.CLIP);
	this._setLicenseStatusMessage();
	// the outer element of the entire skin is hidden until this point
	// so that the skin won't flash (become briefly visible) during app loading
	if (skin && skin.showSkin)
		skin.showSkin(true);		
	this._appViewMgr.addComponents(elements, true);

	var elements = new Object();
	elements[ZaAppViewMgr.C_TREE] = this.getOverviewPanelController().getOverviewPanel();
	elements[ZaAppViewMgr.C_SEARCH] = this._app.getSearchListController().getSearchPanel();		
	elements[ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR] = this._app.getSearchBuilderToolbarController ().getSearchBuilderTBPanel();
	elements[ZaAppViewMgr.C_SEARCH_BUILDER] = this._app.getSearchBuilderController().getSearchBuilderPanel();
	elements[ZaAppViewMgr.C_CURRENT_APP] = new ZaCurrentAppToolBar(this._shell);
	this._appViewMgr.addComponents(elements, true);

	this._app.launch();


	ZaZimbraAdmin._killSplash();
};

// Listeners

// Banner button mouseover/mouseout handlers
ZaZimbraAdmin._bannerBarMouseHdlr =
function(ev) {
	window.status = ZaMsg.done;
	return true;
}

// This method is called by the window.onbeforeunload method.
ZaZimbraAdmin._confirmExitMethod =
function() {
	return ZaMsg.appExitWarning;
}

ZaZimbraAdmin.setOnbeforeunload = 
function(msg) {
	if (msg){
		window.onbeforeunload = msg;
	}else{
		window.onbeforeunload = null;
	}
};
function ZaAboutDialog(parent, className, title, w, h) {
	if (arguments.length == 0) return;
 	var clsName = className || "DwtDialog";
 	DwtDialog.call(this, parent, clsName,  ZaMsg.about_title, [DwtDialog.OK_BUTTON]);
}

ZaAboutDialog.prototype = new DwtDialog;
ZaAboutDialog.prototype.constructor = ZaAboutDialog;

ZaAboutDialog.prototype.popup = function () {
	// Set the content of the dialog before popping it up.
	// This is done here because of the global IDs used by ZLoginFactory.
	var date = AjxDateFormat.getDateInstance().format(ZaServerVersionInfo.buildDate);
    var params = ZLoginFactory.copyDefaultParams(ZaMsg);
	params.showAbout = true,
	params.showPanelBorder = false;
	params.longVersion = AjxBuffer.concat(ZaMsg.splashScreenVersion, " ", ZaServerVersionInfo.version , " " , date);
    var html = ZLoginFactory.getLoginDialogHTML(params);
    this.setContent(html);

 	DwtBaseDialog.prototype.popup.call(this);
};

ZaAboutDialog.prototype.popdown =
function() {
 	DwtBaseDialog.prototype.popdown.call(this);
    this.setContent("");
};

AjxEnv.hasFirebug = (typeof (console) != typeof (_UNDEFINED_)) ; 