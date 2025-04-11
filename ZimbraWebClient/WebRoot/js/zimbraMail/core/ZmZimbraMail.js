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
 * Creates a controller to run ZimbraMail. Do not call directly, instead use the run()
 * factory method.
 * @constructor
 * @class
 * This class is the "ubercontroller", as it manages all the apps as well as bootstrapping
 * the ZimbraMail application.
 *
 * @param appCtxt	[ZmAppCtxt]		the app context (global storage)
 * @param params	[hash]			hash of params:
 *        app		[constant]		starting app
 *        userShell	[Element]		top-level skin container
 */
ZmZimbraMail = function(appCtxt, params) {

	ZmController.call(this, appCtxt);

	this._userShell = params.userShell;
	this._requestMgr = new ZmRequestMgr(appCtxt, this);

	// ALWAYS set back reference into our world (also used by unload handler)
	window._zimbraMail = this;
	
	// setup history support
	window.historyMgr = appCtxt.getHistoryMgr();
	
	// settings structure and defaults
	this._settings = appCtxt.getSettings();
	var branch = appCtxt.get(ZmSetting.BRANCH);
    if (!DBG.isDisabled()) {
		DBG.setTitle("Debug (" + branch + ")");
    }
    var listener = new AjxListener(this, this._settingsChangeListener);
	this._settings.getSetting(ZmSetting.QUOTA_USED).addChangeListener(listener);
	this._settings.getSetting(ZmSetting.POLLING_INTERVAL).addChangeListener(listener);
	this._settings.getSetting(ZmSetting.SKIN_NAME).addChangeListener(listener);
	this._settings.getSetting(ZmSetting.LOCALE_NAME).addChangeListener(listener);
	this._settings.getSetting(ZmSetting.SHORTCUTS).addChangeListener(listener);

	appCtxt.setAppController(this);

	this._shell = appCtxt.getShell();

	if (location.search && (location.search.indexOf("nss=1") != -1)) {
		this._splashScreen = null;
	} else {
   	    this._splashScreen = new ZmSplashScreen(this._shell, "SplashScreen");
	}

	this._apps = {};
	this._upsellView = {};
	this._activeApp = null;
	this._sessionTimer = new AjxTimedAction(null, ZmZimbraMail.logOff);
	this._sessionTimerId = -1;
	this._pollActionId = null;	// AjaxTimedAction ID of timer counting down to next poll time
	this._pollRequest = null;	// HTTP request of poll we've sent to server
	this._pollInstantNotifications = false; // if TRUE, we're in "instant notification" mode

//	if (this._appCtxt.get(ZmSetting.OFFLINE)) {
//		this._pollInstantNotifications = true;
//	}

	this.setPollInterval();

	AjxDispatcher.setPackageLoadFunction("Zimlet", new AjxCallback(this, this._postLoadZimlet));

	AjxDispatcher.setPreLoadFunction(new AjxCallback(this, function() {
		this._appViewMgr.pushView(ZmController.LOADING_VIEW);
	}));
	AjxDispatcher.setPostLoadFunction(new AjxCallback(this, function() {
		this._appViewMgr._toRemove.push(ZmController.LOADING_VIEW);
	}));

	for (var i in ZmApp.QS_ARG) {
		ZmApp.QS_ARG_R[ZmApp.QS_ARG[i]] = i;
	}

	this.startup(params);
};

ZmZimbraMail.prototype = new ZmController;
ZmZimbraMail.prototype.constructor = ZmZimbraMail;

// REVISIT: This is done so that we when we switch from being "beta"
//          to production, we don't have to ensure that all of the
//          translations are changed at the same time. We can simply
//          remove the beta suffix from the app name.
ZmMsg.BETA_documents = [ZmMsg.documents, ZmMsg.beta].join(" ");

ZmZimbraMail._PREFS_ID	= 1;
ZmZimbraMail._HELP_ID	= 2;
ZmZimbraMail._LOGOFF_ID	= 3;

// Public methods

ZmZimbraMail.prototype.toString =
function() {
	return "ZmZimbraMail";
};

/**
 * Sets up ZimbraMail, and then starts it by calling its constructor. It is assumed that the
 * CSFE is on the same host.
 *
 * @param params		[hash]			hash of params:
 *        app			[constant]*		starting app
 *        offlineMode	[boolean]*		if true, this is the offline client
 *        devMode		[boolean]*		if true, we are in development environment
 *        settings		[hash]*			server prefs/attrs
 *        protocolMode	[constant]*		http, https, or mixed
 */
ZmZimbraMail.run =
function(params) {
	
	// Create the global app context
	var appCtxt = new ZmAppCtxt();
	appCtxt.setRememberMe(false);

	// Create and initialize settings
	var settings = new ZmSettings(appCtxt);
	appCtxt.setSettings(settings);
	
	if (params.settings) {
		for (var name in params.settings) {
			var id = settings.getSettingByName(name);
			if (id) {
				settings.getSetting(id).setValue(params.settings[name]);
			}
		}
	} else {
		// Note: removing cookie support may affect offline.jsp
		var apps = AjxCookie.getCookie(document, ZmLogin.APPS_COOKIE);
		DBG.println(AjxDebug.DBG1, "apps: " + apps);
		if (apps) {
			for (var setting in ZmLogin.APP_LETTER) {
				var letter = ZmLogin.APP_LETTER[setting];
				if (apps.indexOf(letter) != -1) {
					appCtxt.set(setting, true);
				}
			}
		}
	}

	// Create generic operations
	ZmOperation.initialize();
	ZmApp.initialize();

	// Handle offline mode
    if (params.offlineMode) {
    	DBG.println(AjxDebug.DBG1, "OFFLINE MODE");
    	appCtxt.set(ZmSetting.OFFLINE, true);
    	appCtxt.set(ZmSetting.POLLING_INTERVAL, 60);
    }
    
    // Handle dev mode
    if (params.devMode) {
    	DBG.println(AjxDebug.DBG1, "DEV MODE");
    	appCtxt.set(ZmSetting.DEV, true);
    	appCtxt.set(ZmSetting.POLLING_INTERVAL, 0);
    }

	if (params.protocolMode) {
		appCtxt.set(ZmSetting.PROTOCOL_MODE, params.protocolMode);
	}

	// Create the shell
	var userShell = params.userShell = window.document.getElementById(settings.get(ZmSetting.SKIN_SHELL_ID));
	if (!userShell) {
		alert("Could not get user shell - skin file did not load properly");
	}
	var shell = new DwtShell({userShell:userShell, docBodyScrollable:false});
	appCtxt.setShell(shell);

	appCtxt.setItemCache(new AjxCache());

	// Go!
	new ZmZimbraMail(appCtxt, params);
};

/**
* Allows parent window to walk list of open child windows and either nuke them
* or "disable" them.
*/
ZmZimbraMail.unload =
function() {
	var childWinList = window._zimbraMail ? window._zimbraMail._childWinList : null;
	if (childWinList) {
		// close all child windows
		for (var i = 0; i < childWinList.size(); i++) {
			var childWin = childWinList.get(i);
			childWin.win.onbeforeunload = null;
			childWin.win.parentController = null;
			childWin.win.close();
		}
	}
	window._zimbraMail = window.onload = window.onresize = window.document.onkeypress = null;
};

/**
 * Returns sort order using a and b as keys into given hash.
 * 
 * @param hash		[hash]		hash with sort values
 * @param a			[string]	key into hash
 * @param b			[string]	key into hash
 */
ZmZimbraMail.hashSortCompare =
function(hash, a, b) {
	var appA = a ? Number(hash[a]) : 0;
	var appB = b ? Number(hash[b]) : 0;
	if (appA > appB) { return 1; }
	if (appA < appB) { return -1; }
	return 0;
};

/**
 * Loads the app and presents the initial view. First, it gets the user's preferences.
 * Next, it launches the start app (which defaults to mail) and shows the results to
 * the user. Finally, we load contacts in the background.
 *
 * @param params		[hash]			hash of params:
 *        app			[constant]*		starting app
 *        isRelogin		[boolean]*		user has re-authenticated after session timeout
 *        settings		[hash]*			settings overrides
 */
ZmZimbraMail.prototype.startup =
function(params) {
	
	this._appCtxt.inStartup = true;
	if (typeof(skin) == "undefined") {
		DBG.println(AjxDebug.DBG1, "No skin!");
		var url = AjxUtil.formatUrl({path:"/public/skinError.jsp", qsArgs:{skin:appCurrentSkin}, qsReset:true});
		ZmZimbraMail.sendRedirect(url);
        return;
    }

	if (!this._appViewMgr) {
		this._appViewMgr = new ZmAppViewMgr(this._shell, this, false, true);
		this._loadingView = new DwtControl(this._shell, "DwtListView", Dwt.ABSOLUTE_STYLE);
		var el = this._loadingView.getHtmlElement();
		var htmlArr = [];
		var idx = 0;
		htmlArr[idx++] = "<table width='100%' cellspacing='0' cellpadding='1'><tr><td class='NoResults'><br>";
		htmlArr[idx++] = ZmMsg.loading;
		htmlArr[idx++] = "</td></tr></table>";
		el.innerHTML = htmlArr.join("");
		var elements = {};
		elements[ZmAppViewMgr.C_APP_CONTENT] = this._loadingView;
		this._appViewMgr.createView(ZmController.LOADING_VIEW, null, elements);
	}

	skin.show("skin", true);
	var hint = this._appCtxt.get(ZmSetting.SKIN_HINTS, "app_chooser.style");
	this._TAB_SKIN_ENABLED = (hint == "tabs");
	if (!this._components) {
		this._components = {};
		this._components[ZmAppViewMgr.C_SASH] = new DwtSash(this._shell, DwtSash.HORIZONTAL_STYLE, "console_inset_app_l", 20);
		this._components[ZmAppViewMgr.C_BANNER] = this._createBanner();
		this._components[ZmAppViewMgr.C_USER_INFO] = this._userNameField = this._createUserInfo("BannerTextUser", ZmAppViewMgr.C_USER_INFO);
		this._components[ZmAppViewMgr.C_QUOTA_INFO] = this._usedQuotaField = this._createUserInfo("BannerTextQuota", ZmAppViewMgr.C_QUOTA_INFO);
		var currentAppToolbar = new ZmCurrentAppToolBar(this._shell, this._appCtxt);
		this._appCtxt.setCurrentAppToolbar(currentAppToolbar);
		this._components[ZmAppViewMgr.C_CURRENT_APP] = currentAppToolbar;
		this._components[ZmAppViewMgr.C_STATUS] = this._statusView = new ZmStatusView(this._shell, "ZmStatus", Dwt.ABSOLUTE_STYLE);
	}

	this._createEnabledApps();
	this._registerOrganizers();

	// set up map of search types to item types
	for (var i in ZmSearch.TYPE) {
		ZmSearch.TYPE_MAP[ZmSearch.TYPE[i]] = i;
	}
	// organizer types based on view
	for (var i in ZmOrganizer.VIEWS) {
		var list = ZmOrganizer.VIEWS[i];
		for (var j = 0; j < list.length; j++) {
			ZmOrganizer.TYPE[list[j]] = i;
		}
	}

	var respCallback = new AjxCallback(this, this._handleResponseStartup, params);
	this._errorCallback = new AjxCallback(this, this._handleErrorStartup, params);
	this._settings.loadUserSettings(respCallback, this._errorCallback); // load user prefs and COS data
};

ZmZimbraMail.prototype._handleErrorStartup =
function(params, ex) {
	this._killSplash();
	this._appCtxt.inStartup = false;
	return false;
};

/**
 * Startup: part 2
 * Creates components which have dependencies on the settings, including the overview.
 *
 * @param params			[hash]			hash of params:
 *        app				[constant]		starting app
 *        settingOverrides	[Object]		hash of overrides of user settings
 * @param result			[ZmCsfeResult]	result object from load of user settings
 */
ZmZimbraMail.prototype._handleResponseStartup =
function(params, result) {

	if (params && params.settingOverrides) {
		this._needOverviewLayout = true;
		for (var id in params.settingOverrides) {
			var setting = this._settings.getSetting(id);
			if (setting) {
				setting.setValue(params.settingOverrides[id]);
			}
		}
	}
	
	if (!this._appCtxt.get(ZmSetting.DEV) && this._appCtxt.get(ZmSetting.WARN_ON_EXIT)) {
		window.onbeforeunload = ZmZimbraMail._confirmExitMethod;
	}	

	// run any app-requested startup routines
	this.runAppFunction("startup", result);

	// determine default starting app
	for (var app in ZmApp.DEFAULT_SORT) {
		ZmApp.DEFAULT_APPS.push(app);
	}
	ZmApp.DEFAULT_APPS.sort(function(a, b) {
		return ZmZimbraMail.hashSortCompare(ZmApp.DEFAULT_SORT, a, b);
	});
	for (var i = 0; i < ZmApp.DEFAULT_APPS.length; i++) {
		var app = ZmApp.DEFAULT_APPS[i];
		var setting = ZmApp.SETTING[app];
		if (!setting || this._appCtxt.get(setting)) {
			this._defaultStartApp = app;
			break;
		}
	}

	this._setUserInfo();

	if (this._appCtxt.get(ZmSetting.SEARCH_ENABLED)) {
		this._components[ZmAppViewMgr.C_SEARCH] = this._appCtxt.getSearchController().getSearchPanel();
	}

	if (!this._components[ZmAppViewMgr.C_APP_CHOOSER]) {
		this._components[ZmAppViewMgr.C_APP_CHOOSER] = this._appChooser = this._createAppChooser();
	}

	var kbMgr = this._appCtxt.getKeyboardMgr();
	if (this._appCtxt.get(ZmSetting.USE_KEYBOARD_SHORTCUTS)) {
		// Register our keymap and global key action handler with the shell's keyboard manager
		kbMgr.enable(true);
		kbMgr.registerKeyMap(new ZmKeyMap(this._appCtxt));
		kbMgr.pushDefaultHandler(this);

		DBG.println(AjxDebug.DBG2, "SETTING SEARCH CONTROLLER TAB GROUP");
		var rootTg = this._appCtxt.getRootTabGroup();
		rootTg.addMember(this._appCtxt.getSearchController().getTabGroup());
		var appChooserTg = new DwtTabGroup("ZmAppChooser");
		appChooserTg.addMember(this._components[ZmAppViewMgr.C_APP_CHOOSER]);
		if (this._TAB_SKIN_ENABLED) {
			rootTg.addMember(appChooserTg);
		}
		// Add dummy app view tab group. This will get replaced right away when the
		// app view comes into play
		var dummyTg = new DwtTabGroup("DUMMY APPVIEW");
		ZmController._setCurrentAppViewTabGroup(dummyTg);
		rootTg.addMember(dummyTg);
		if (!this._TAB_SKIN_ENABLED) {
			rootTg.addMember(appChooserTg);
		}
		kbMgr.setTabGroup(rootTg);
		
		this._settings._loadShortcuts();
	} else {
		kbMgr.enable(false);
	}

	this._handleResponseStartup1(params);
};

/*
* Startup: part 3
* Launches the starting application.
*
* @param app		[constant]		starting app
*/
ZmZimbraMail.prototype._handleResponseStartup1 =
function(params) {
	
	var respCallback = new AjxCallback(this, this._handleResponseStartup2);
	var startApp;
	if (params && params.app) {
		startApp = ZmApp.QS_ARG_R[params.app.toLowerCase()];
	}
	if (!startApp) {
		startApp = (params && params.isRelogin && this._activeApp) ? this._activeApp : this._defaultStartApp;
	}

	// check for jump to compose page or msg view
	var checkQS = false;
	if (startApp == ZmApp.CALENDAR) {
		// let calendar check for jumps
		checkQS = true;
	} else {
		var match;
		if (location.search && (match = location.search.match(/\bview=(\w+)\b/))) {
			var view = match[1];
			startApp = this._defaultStartApp;
			if (ZmApp.QS_VIEWS[view]) {
				startApp = ZmApp.QS_VIEWS[view];
				checkQS = true;
			}
		}
	}
	this.activateApp(startApp, false, respCallback, this._errorCallback, checkQS);
};

/*
* Startup: part 4
* Kills the splash screen, checks license
*/
ZmZimbraMail.prototype._handleResponseStartup2 =
function() {
	this.setSessionTimer(true);
	this._killSplash();
	// next line makes the UI appear
	this._appViewMgr.addComponents(this._components, true);

	if (this._appCtxt.get(ZmSetting.LICENSE_STATUS) != ZmSetting.LICENSE_GOOD) {
		var dlg = this._appCtxt.getMsgDialog();
		dlg.reset();
		dlg.setMessage(ZmMsg.licenseExpired, DwtMessageDialog.WARNING_STYLE);
		dlg.popup();
	}
	AjxDispatcher.enableLoadFunctions(true);
	this._appCtxt.inStartup = false;
};

/**
* Performs a 'running restart' of the app by clearing state and calling the startup method.
* This method is run after a logoff, or a change in what's supported.
*/
ZmZimbraMail.prototype.restart =
function(settings) {
	// need to decide what to clean up, what to have startup load lazily
	// could have each app do shutdown()
	DBG.println(AjxDebug.DBG1, "RESTARTING APP");
	this.reset();
   	this.startup({settingOverrides:settings});
};

ZmZimbraMail.prototype.reset =
function() {
	ZmCsfeCommand.setSessionId(null);	// so we get a refresh block
    this._highestNotifySeen = 0; 		// we have a new session

	var accts = this._appCtxt.getZimbraAccounts();
	for (var id in accts) {
		var trees = accts[id].trees;
	    for (var type in trees) {
	    	var tree = trees[type];
	    	if (tree && tree.reset) {
	    		tree.reset();
	    	}
	 	}
	}

	if (!this._appCtxt.rememberMe()) {
		this._appCtxt.getLoginDialog().clearAll();
	}
	for (var app in this._apps) {
		this._apps[app] = null;
	}
	this._activeApp = null;
	this._appViewMgr.reset();
};

ZmZimbraMail.prototype.sendRequest =
function(params) {
	return this._requestMgr.sendRequest(params);
};

/**
 * Runs the given function for all enabled apps, passing args.
 * 
 * @param funcName		[string]	function name
 */
ZmZimbraMail.prototype.runAppFunction =
function(funcName) {
	var args = [];
	for (var i = 1; i < arguments.length; i++) {
		args.push(arguments[i]);
	}
	for (var i = 0; i < ZmApp.APPS.length; i++) {
		var appName = ZmApp.APPS[i];
		var setting = ZmApp.SETTING[appName];
		if (!setting || this._appCtxt.get(setting)) {
			var app = this._appCtxt.getApp(appName);
			var func = app[funcName];
			if (func && (typeof(func) == "function")) {
				func.apply(app, args);
			}
		}
	}
};

/**
 * Instantiates enabled apps. An optional argument may be given limiting the set
 * of apps that may be created.
 * 
 * @param apps	[hash]*		the set of apps to create
 */
ZmZimbraMail.prototype._createEnabledApps =
function(apps) {
	for (var app in ZmApp.CLASS) {
		if (!apps || apps[app]) {
			ZmApp.APPS.push(app);
		}
	}
	ZmApp.APPS.sort(function(a, b) {
		return ZmZimbraMail.hashSortCompare(ZmApp.LOAD_SORT, a, b);
	});
	
	// Instantiate enabled apps, which will invoke app registration.
	// We also create "upsell" apps, which will only show the content of a URL in an iframe,
	// to encourage the user to upgrade.
	for (var i = 0; i < ZmApp.APPS.length; i++) {
		var app = ZmApp.APPS[i];
		var setting = ZmApp.SETTING[app];
		var upsellSetting = ZmApp.UPSELL_SETTING[app];
		if ((setting && this._appCtxt.get(setting)) || (upsellSetting && this._appCtxt.get(upsellSetting))) {
			this._createApp(app);
		}
	}
};

/**
 * Send a NoOpRequest to the server.  Used for '$set:noop'
 */
ZmZimbraMail.prototype.sendNoOp =
function() {
    var soapDoc = AjxSoapDoc.create("NoOpRequest", "urn:zimbraMail");
    this.sendRequest({soapDoc:soapDoc, asyncMode:true, noBusyOverlay:true});
};

ZmZimbraMail.prototype.sendSync =
function(callback) {
    var soapDoc = AjxSoapDoc.create("SyncRequest", "urn:zimbraOffline");
    this.sendRequest({soapDoc:soapDoc, asyncMode:true, noBusyOverlay:true, callback:callback});
};

/**
 * Put the client into "instant notifications" mode.
 */
ZmZimbraMail.prototype.setInstantNotify =
function(on) {
    if (on) {
        this._pollInstantNotifications = true;
        // set a nonzero poll interval so that we cannot ever get into a
		// full-speed request loop
        this._pollInterval = 100;
        if (this._pollActionId) {
            AjxTimedAction.cancelAction(this._pollActionId);
            this._pollActionId = null;
        }
        this._kickPolling(true);
    } else {
        this.setPollInterval(true);
    }
}

/**
 * Resets the interval between poll requests, based on what's in the settings,
 * only if we are not in instant notify mode.
 *
 * @param kickMe	[boolean]*		if true, start the poll timer
 */
ZmZimbraMail.prototype.setPollInterval =
function(kickMe) {
    if (!this._pollInstantNotifications) {
        this._pollInterval = this._appCtxt.get(ZmSetting.POLLING_INTERVAL) * 1000;
        DBG.println(AjxDebug.DBG1, "poll interval = " + this._pollInterval + "ms");

        if (this._pollInterval) {
        	if (kickMe) {
	            this._kickPolling(true);
	        }
        } else {
            // cancel pending request if there is one
            if (this._pollRequest) {
                this._requestMgr.cancelRequest(this._pollRequest);
                this._pollRequest = null;
            }
            // cancel timer if it is waiting...
            if (this._pollActionId) {
                AjxTimedAction.cancelAction(this._pollActionId);
                this._pollActionId = null;
            }
        }
        return true;
    } else {
        this._pollInterval = 100;
        DBG.println(AjxDebug.DBG1, "Ignoring Poll Interval (in instant-notify mode)");
        return false;
    }
};

/*
 * Make sure the polling loop is running.  Basic flow:
 *
 *       1) kickPolling():
 *             - cancel any existing timers
 *             - set a timer for _pollInterval time
 *             - call execPoll() when the timer goes off
 *
 *       2) execPoll():
 *             - make the NoOp request, if we're in "instant notifications"
 *               mode, this request will hang on the server until there is more data,
 *               otherwise it will return immediately.  Call into a handle() func below
 *
 *       3) handleDoPollXXXX():
 *             - call back to kickPolling() above
 *
 * resetBackoff = TRUE e.g. if we've just received a successful
 * response from the server, or if the user just changed our
 * polling settings and we want to start in fast mode
 */
ZmZimbraMail.prototype._kickPolling =
function(resetBackoff) {
    DBG.println(AjxDebug.DBG2, "ZmZimbraMail._kickPolling(1) "+
                               this._pollInterval + ", "+ this._pollActionId+", "+
                               (this._pollRequest ? "request_pending" : "no_request_pending"));

    // reset the polling timeout
    if (this._pollActionId) {
        AjxTimedAction.cancelAction(this._pollActionId);
        this._pollActionId = null;
    }

    if (resetBackoff && this._pollInstantNotifications) {
        if (this._pollInterval > 100) {
            // we *were* backed off -- reset the delay back to 1s fastness
            this._pollInterval = 100;
            // need to kick the timer if it is waiting -- it might be waiting a long time
            // and we want the change to take place immediately
            if (this._pollActionId) {
                AjxTimedAction.cancelAction(this._pollActionId);
                this._pollActionId = null;
            }
        }
    }

    if (this._pollInterval && !this._pollActionId && !this._pollRequest) {
        try {
            var pollAction = new AjxTimedAction(this, this._execPoll);
            this._pollActionId = AjxTimedAction.scheduleAction(pollAction, this._pollInterval);
        } catch (ex) {
            this._pollActionId = null;
            DBG.println(AjxDebug.DBG1, "Caught exception in ZmZimbraMail._kickPolling.  Polling chain broken!");
        }
    }
  };
  
/*
 * We've finished waiting, do the actual poll itself
 */
ZmZimbraMail.prototype._execPoll =
function() {
    this._pollActionId = null;

    // It'd be more efficient to make these instance variables, but for some
    // reason that breaks polling in IE.
    var soapDoc = AjxSoapDoc.create("NoOpRequest", "urn:zimbraMail");
    try {
        if (this._pollInstantNotifications) {
            var method = soapDoc.getMethod();
            method.setAttribute("wait", 1);
        }
        var responseCallback = new AjxCallback(this, this._handleResponseDoPoll);
        var errorCallback = new AjxCallback(this, this._handleErrorDoPoll);

        this._pollRequest = this.sendRequest({soapDoc:soapDoc, asyncMode:true, callback:responseCallback, errorCallback:errorCallback,
        									  noBusyOverlay:true, timeout:this._appCtxt.get(ZmSetting.INSTANT_NOTIFY_TIMEOUT)});
    } catch (ex) {
        // oops!
        this._handleErrorDoPoll(ex);
    }
}


ZmZimbraMail.prototype._handleErrorDoPoll =
function(ex) {
    this._pollRequest = null;

    if (this._pollInstantNotifications) {
        // very simpleminded exponential backoff
        this._pollInterval *= 2;
        if (this._pollInterval > (1000 * 60 * 2)) {
            this._pollInterval = 1000 * 60 * 2;
        }
    }
    // restart poll timer if we didn't get an exception
    this._kickPolling(false);

    return (ex.code != ZmCsfeException.SVC_AUTH_EXPIRED &&
  			ex.code != ZmCsfeException.SVC_AUTH_REQUIRED &&
  			ex.code != ZmCsfeException.NO_AUTH_TOKEN);
  };
  
ZmZimbraMail.prototype._handleResponseDoPoll =
function(ex) {
    this._pollRequest = null;
    // restart poll timer if we didn't get an exception

   	this._kickPolling(true);
};

ZmZimbraMail.prototype._handleResponseDoPoll =
function(ex) {
    this._pollRequest = null;
    // restart poll timer if we didn't get an exception
   	this._kickPolling(true);
};

ZmZimbraMail.prototype._registerOrganizers =
function() {
	ZmOrganizer.registerOrg(ZmOrganizer.FOLDER,
							{nameKey:			"folder",
							 defaultFolder:		ZmOrganizer.ID_INBOX,
							 soapCmd:			"FolderAction",
							 firstUserId:		256,
							 orgClass:			"ZmFolder",
							 treeController:	"ZmFolderTreeController",
							 labelKey:			"folders",
							 itemsKey:			"messages",
							 hasColor:			true,
							 defaultColor:		ZmOrganizer.C_NONE,
							 treeType:			ZmOrganizer.FOLDER,
							 views:				["message", "conversation"],
							 folderKey:			"mailFolder",
							 mountKey:			"mountFolder",
							 createFunc:		"ZmOrganizer.create",
							 compareFunc:		"ZmFolder.sortCompare",
							 shortcutKey:		"F"
							});

	ZmOrganizer.registerOrg(ZmOrganizer.SEARCH,
							{nameKey:			"savedSearch",
							 precondition:		ZmSetting.SAVED_SEARCHES_ENABLED,
							 soapCmd:			"FolderAction",
							 firstUserId:		256,
							 orgClass:			"ZmSearchFolder",
							 treeController:	"ZmSearchTreeController",
							 labelKey:			"searches",
							 treeType:			ZmOrganizer.FOLDER,
							 createFunc:		"ZmSearchFolder.create",
							 compareFunc:		"ZmFolder.sortCompare",
							 shortcutKey:		"S"
							});

	ZmOrganizer.registerOrg(ZmOrganizer.TAG,
							{nameKey:			"tag",
							 precondition:		ZmSetting.TAGGING_ENABLED,
							 soapCmd:			"TagAction",
							 firstUserId:		64,
							 orgClass:			"ZmTag",
							 treeController:	"ZmTagTreeController",
							 hasColor:			true,
							 defaultColor:		ZmOrganizer.C_ORANGE,
							 labelKey:			"tags",
							 treeType:			ZmOrganizer.TAG,
							 createFunc:		"ZmTag.create",
							 compareFunc:		"ZmTag.sortCompare",
							 shortcutKey:		"T"
							});
};

/**
* Returns a handle to the given app.
*
* @param appName	an app name
*/
ZmZimbraMail.prototype.getApp =
function(appName) {
	if (!this._apps[appName]) {
		this._createApp(appName);
	}
	return this._apps[appName];
};

/**
* Returns a handle to the app view manager.
*/
ZmZimbraMail.prototype.getAppViewMgr =
function() {
	return this._appViewMgr;
};

ZmZimbraMail.prototype.getActiveApp =
function() {
	return this._activeApp;
};

ZmZimbraMail.prototype.getPreviousApp =
function() {
	return this._previousApp;
};

/**
* Activates the given app.
*
* @param appName		[constant]		application
* @param force			[boolean]*		if true, launch the app
* @param callback		[AjxCallback]*	callback
* @param errorCallback	[AjxCallback]*	error callback
* @param checkQS		[boolean]*		if true, check query string for launch args
*/
ZmZimbraMail.prototype.activateApp =
function(appName, force, callback, errorCallback, checkQS) {
    DBG.println(AjxDebug.DBG1, "activateApp: " + appName + ", current app = " + this._activeApp);

    var view = this._appViewMgr.getAppView(appName);
    if (view && !force) {
    	// if the app has been launched, make its view the current one
	    DBG.println(AjxDebug.DBG3, "activateApp, current " + appName + " view: " + view);
		if (this._appViewMgr.pushView(view)) {
		    this._appViewMgr.setAppView(appName, view);
		}
		if (callback) {
			callback.run();
		}
	} else {
    	// launch the app
    	if (!this._apps[appName]) {
			this._createApp(appName);
    	}
    	var appEnabled = this._appCtxt.get(ZmApp.SETTING[appName]);
		var upsellEnabled = this._appCtxt.get(ZmApp.UPSELL_SETTING[appName]);
		if (!appEnabled && upsellEnabled) {
			this._createUpsellView(appName);
		} else {
			DBG.println(AjxDebug.DBG1, "Launching app " + appName);
			var respCallback = new AjxCallback(this, this._handleResponseActivateApp, [callback]);
			this._apps[appName].launch(respCallback, checkQS);
		}
    }
};

ZmZimbraMail.prototype._handleResponseActivateApp =
function(callback) {
	if (callback) {
		callback.run();
	}
};

/**
* Handles a change in which app is current. The change will be reflected in the
* current app toolbar and the overview. The previous and newly current apps are
* notified of the change. This method is called after a new view is pushed.
*
* @param appName	[constant]	the newly current app
* @param view		[constant]	the newly current view
*/
ZmZimbraMail.prototype.setActiveApp =
function(appName, view) {

	// update app chooser
	this._components[ZmAppViewMgr.C_APP_CHOOSER].setSelected(appName);

	// app not actually enabled if this is result of upsell view push
   	var appEnabled = this._appCtxt.get(ZmApp.SETTING[appName]);

	// update current app toolbar
	var toolbar = appEnabled ? this._appCtxt.getCurrentAppToolbar() : null;
	if (toolbar) {
		toolbar.setupView(appName);
	}

	if (this._activeApp != appName) {

		// deactivate previous app
	    if (this._activeApp) {
			// some views are not stored in _apps collection, so check if it exists.
			var app = this._apps[this._activeApp];
			if (app) {
				app.activate(false, view);
			}
			this._previousApp = this._activeApp;
	    }

	    // switch app
		this._activeApp = appName;
		if (appEnabled) {
			if (ZmApp.DEFAULT_SEARCH[appName]) {
				this._appCtxt.getSearchController().setDefaultSearchType(ZmApp.DEFAULT_SEARCH[appName], true);
			}
			
			// set search string value to match current app's last search, if applicable		
			var app = this._apps[this._activeApp];
			this._appCtxt.getSearchController().getSearchToolbar().setSearchFieldValue(app.currentQuery ? app.currentQuery : "");
	
			// activate current app
			if (app) {
				app.activate(true, view);
			}
		}
	}
};

ZmZimbraMail.prototype.getAppChooserButton =
function(id) {
	return this._components[ZmAppViewMgr.C_APP_CHOOSER].getButton(id);
};

ZmZimbraMail.prototype.isChildWindow =
function() {
	return false;
};

// Private methods

ZmZimbraMail.prototype._killSplash =
function() {
	if (this._splashScreen)	{
		this._splashScreen.setVisible(false);
	}
};

// Creates an app object, which doesn't necessarily do anything just yet.
ZmZimbraMail.prototype._createApp =
function(appName) {
	if (!appName || this._apps[appName]) return;
	DBG.println(AjxDebug.DBG1, "Creating app " + appName);
	var appClass = eval(ZmApp.CLASS[appName]);
	this._apps[appName] = new appClass(this._appCtxt, this._shell);
};

ZmZimbraMail.prototype._setUserInfo = 
function() {
	if (this._TAB_SKIN_ENABLED) {
		var hideIcon = this._appCtxt.get(ZmSetting.SKIN_HINTS, "help_button.hideIcon");
		this._setUserInfoLink("ZmZimbraMail.helpLinkCallback();", "Help", ZmMsg.help, "skin_container_help", hideIcon);
		hideIcon = this._appCtxt.get(ZmSetting.SKIN_HINTS, "logout_button.hideIcon");
		var text = this._appCtxt.get(ZmSetting.OFFLINE) ? ZmMsg.setup : ZmMsg.logOff;
		this._setUserInfoLink("ZmZimbraMail.conditionalLogOff();", "Logoff", text, "skin_container_logoff", hideIcon);
	}

	var data = {};

	var login = this._appCtxt.get(ZmSetting.USERNAME);
	var username = (this._appCtxt.get(ZmSetting.DISPLAY_NAME)) || login;
	if (username) {
		this._userNameField.getHtmlElement().innerHTML = username;
		if (AjxEnv.isLinux) {	// bug fix #3355
			this._userNameField.getHtmlElement().style.lineHeight = "13px";
		}
	}

	var userTooltip = (username != login) ? login : null;
	var quota = this._appCtxt.get(ZmSetting.QUOTA);
	var usedQuota = (this._appCtxt.get(ZmSetting.QUOTA_USED)) || 0;
	var size = AjxUtil.formatSize(usedQuota, false, 1);
	var quotaTooltip = null;

	data['login'] = login;
	data['username'] = username;
	data['quota'] = quota;
	data['usedQuota'] = usedQuota;
	data['size'] = size;


	/*
	var html = [];
	var idx = 0;
	html[idx++] = "<center><table border=0 cellpadding=0 cellspacing=0 class='BannerBar'><tr";
	html[idx++] = AjxEnv.isLinux ? " style='line-height: 13px'" : ""; // bug #3355;
	html[idx++] = "><td class='BannerTextQuota'>";
	html[idx++] = ZmMsg.quota;
	html[idx++] = ": </td>";
	if (quota) {
		var limit = AjxUtil.formatSize(quota, false, 1);
		var percent = Math.min(Math.round((usedQuota / quota) * 100), 100);
		
		// set background color based on percent used
		var progressClassName = "quotaUsed";
		if (percent < 85 && percent > 65)
			progressClassName = "quotaWarning";
		else if (percent >= 85)
			progressClassName = "quotaCritical";

		html[idx++] = "<td><div class='quotabar'><div style='width: ";
		html[idx++] = percent;
		html[idx++] = ";' class='";
		html[idx++] = progressClassName;
		html[idx++] = "'></div></div></td>";

		var desc = AjxMessageFormat.format(ZmMsg.quotaDescLimited, [size, limit]);
		quotaTooltip = [ZmMsg.quota, ": ", percent, "% ", desc].join("");
	} else {
		var desc = AjxMessageFormat.format(ZmMsg.quotaDescUnlimited, [size]);
		html[idx++] = "<td class='BannerTextQuota'>";
		html[idx++] = desc;
		html[idx++] = "</td>";
	}
	html[idx++] = "</tr></table></center>";
	*/
	var quotaTemplateId;
	if (quota) {
		quotaTemplateId = 'UsedLimited';
		data['limit'] = AjxUtil.formatSize(data.quota, false, 1);
		data['percent'] = Math.min(Math.round((data.usedQuota / data.quota) * 100), 100);
		data['desc'] = AjxMessageFormat.format(ZmMsg.quotaDescLimited, [data.percent+'%', data.limit]);
		var tooltipDesc = AjxMessageFormat.format(ZmMsg.quotaDescLimited, [data.size, data.limit]);
		quotaTooltip = [ZmMsg.quota, ": ", data.percent, "% ", '('+tooltipDesc+')'].join("");
	}
	else {
		data['desc'] = AjxMessageFormat.format(ZmMsg.quotaDescUnlimited, [size]);
		quotaTemplateId = 'UsedUnlimited';
	}

//	if (!this._appCtxt.get(ZmSetting.SKIN_HINTS, "help_button.hideIcon")) {
		//this._usedQuotaField.getHtmlElement().innerHTML = html.join("");
//	}
	data['id'] = this._usedQuotaField._htmlElId;
	this._usedQuotaField.getHtmlElement().innerHTML =
		AjxTemplate.expand('zimbraMail.share.templates.Quota#'+quotaTemplateId,
							data)

	if (userTooltip || quotaTooltip) {
		var tooltip = [];
		idx = 0;
		tooltip[idx++] = "<table>";
		if (userTooltip) {
			tooltip[idx++] = "<tr><td>";
			tooltip[idx++] = userTooltip;
			tooltip[idx++] = "</td></tr>";
		}
		if (quotaTooltip) {
			tooltip[idx++] = "<tr><td><center>";
			tooltip[idx++] = quotaTooltip;
			tooltip[idx++] = "</center></td></tr>";
		}
		tooltip[idx++] = "</table>";
		this._components[ZmAppViewMgr.C_USER_INFO].setToolTipContent(tooltip.join(""));
		this._components[ZmAppViewMgr.C_QUOTA_INFO].setToolTipContent(tooltip.join(""));
	}
};

ZmZimbraMail.prototype._setUserInfoLink =
function(staticFunc, icon, lbl, id, hideIcon) {
	var html = [];
	var i = 0;
	html[i++] = "<table border=0 cellpadding=1 cellspacing=1 align=right width=1%><tr>";
	if (!hideIcon) {
		html[i++] = "<td align=right><a href='javascript:;' onclick='";
		html[i++] = staticFunc;
		html[i++] = "'>";
		html[i++] = AjxImg.getImageHtml(icon, null, "border=0");
		html[i++] = "</a></td>";
	}
	html[i++] = "<td width=1% align=right style='white-space:nowrap; font-weight:bold'><a href='javascript:;' onclick='";
	html[i++] = staticFunc;
	html[i++] = "'>";
	html[i++] = lbl;
	html[i++] = "</a></td></tr></table>";

	var cell = document.getElementById(id);
	if (cell) cell.innerHTML = html.join("");
};

// Listeners

ZmZimbraMail.logOff =
function() {

	// stop keeping track of user input (if applicable)
	if (window._zimbraMail) {
		window._zimbraMail.setSessionTimer(false);
	}

	ZmCsfeCommand.clearAuthToken();
	
	window.onbeforeunload = null;
	
	var url = window._zimbraMail ? window._zimbraMail._appCtxt.get(ZmSetting.LOGOUT_URL) : null;
	if (!url) {
		url = AjxUtil.formatUrl({path:appContextPath});
	}
	ZmZimbraMail.sendRedirect(url);
};

ZmZimbraMail._logOffListener = new AjxListener(null, ZmZimbraMail.logOff);

ZmZimbraMail.conditionalLogOff =
function() {
	if (window._zimbraMail && !window._zimbraMail._appViewMgr.isOkToLogOff(ZmZimbraMail._logOffListener)) {
		return;
	}
	ZmZimbraMail.logOff();
};

ZmZimbraMail.helpLinkCallback =
function() {
	ZmZimbraMail.unloadHackCallback();

	var appCtxt = window.parentController
		? window.parentController._appCtxt
		: window._zimbraMail._appCtxt;

	var url;
	try { url = skin.hints.help_button.url; } catch (e) { /* ignore */ }
	window.open(url || appCtxt.get(ZmSetting.HELP_URI));
};

ZmZimbraMail.sendRedirect =
function(locationStr) {
	// not sure why IE doesn't allow this to process immediately, but since
	// it does not, we'll set up a timed action.
	if (AjxEnv.isIE) {
		var act = new AjxTimedAction(null, ZmZimbraMail.redir, [locationStr]);
		AjxTimedAction.scheduleAction(act, 1);
	} else {
		ZmZimbraMail.redir(locationStr);
	}
};

ZmZimbraMail.redir =
function(locationStr){
	window.location = locationStr;
};

ZmZimbraMail.prototype.setSessionTimer =
function(bStartTimer) {

	// if no timeout value, user's client never times out from inactivity	
	var timeout = this._appCtxt.get(ZmSetting.IDLE_SESSION_TIMEOUT) * 1000;
	if (timeout <= 0)
		return;

	if (bStartTimer) {
		DBG.println(AjxDebug.DBG3, "INACTIVITY TIMER SET (" + (new Date()).toLocaleString() + ")");
		this._sessionTimerId = AjxTimedAction.scheduleAction(this._sessionTimer, timeout);
		
		DwtEventManager.addListener(DwtEvent.ONMOUSEUP, ZmZimbraMail._userEventHdlr);
		this._shell.setHandler(DwtEvent.ONMOUSEUP, ZmZimbraMail._userEventHdlr);
		if (AjxEnv.isIE)
			this._shell.setHandler(DwtEvent.ONMOUSEDOWN, ZmZimbraMail._userEventHdlr);
		else
			window.onkeydown = ZmZimbraMail._userEventHdlr;
	} else {
		DBG.println(AjxDebug.DBG3, "INACTIVITY TIMER CANCELED (" + (new Date()).toLocaleString() + ")");
		
		AjxTimedAction.cancelAction(this._sessionTimerId);
		this._sessionTimerId = -1;

		DwtEventManager.removeListener(DwtEvent.ONMOUSEUP, ZmZimbraMail._userEventHdlr);
		this._shell.clearHandler(DwtEvent.ONMOUSEUP);
		if (AjxEnv.isIE)
			this._shell.clearHandler(DwtEvent.ONMOUSEDOWN);
		else
			window.onkeydown = null;
	}
};

ZmZimbraMail.prototype.addChildWindow = 
function(childWin) {
	if (this._childWinList == null)
		this._childWinList = new AjxVector();

	// NOTE: we now save childWin w/in Object so other params can be added to it.
	// Otherwise, Safari breaks (see http://bugs.webkit.org/show_bug.cgi?id=7162)
	var newWinObj = new Object();
	newWinObj.win = childWin;

	this._childWinList.add(newWinObj);

	return newWinObj;
};

ZmZimbraMail.prototype.getChildWindow = 
function(childWin) {
	if (this._childWinList) {
		for (var i = 0; i < this._childWinList.size(); i++) {
			if (childWin == this._childWinList.get(i).win) {
				return this._childWinList.get(i);
			}
		}
	}
	return null;
};

ZmZimbraMail.prototype.removeChildWindow =
function(childWin) {
	if (this._childWinList) {
		for (var i = 0; i < this._childWinList.size(); i++) {
			if (childWin == this._childWinList.get(i).win) {
				this._childWinList.removeAt(i);
				break;
			}
		}
	}
};

/*
* Common exception handling entry point.
*
* @param ex	[Object]		the exception
* 
*/
ZmZimbraMail.prototype._handleException =
function(ex, method, params, restartOnError, obj) {
	var handled = false;
	if (ex.code == ZmCsfeException.MAIL_NO_SUCH_FOLDER) {
		var organizerTypes = [ZmOrganizer.CALENDAR, ZmOrganizer.NOTEBOOK, ZmOrganizer.ADDRBOOK];
		if (ex.data.itemId && ex.data.itemId.length) {
			var itemId = ex.data.itemId[0];
			var index = itemId.lastIndexOf(':');
			var zid = itemId.substring(0, index);
			var rid = itemId.substring(index + 1, itemId.length);
			var ft = this._appCtxt.getFolderTree();
			for (var type = 0; type < organizerTypes.length; type++) {
				handled |= ft.handleNoSuchFolderError(organizerTypes[type], zid, rid, true);
			}
		}
	}
	if (!handled) {
		ZmController.prototype._handleException.call(this, ex, method, params, restartOnError, obj);
	}
};

// This method is called by the window.onbeforeunload method.
ZmZimbraMail._confirmExitMethod =
function() {
	return ZmMsg.appExitWarning;
};

ZmZimbraMail.unloadHackCallback =
function() {
	window.onbeforeunload = null;
	var f = function() { window.onbeforeunload = ZmZimbraMail._confirmExitMethod; };
	var t = new AjxTimedAction(null, f);
	AjxTimedAction.scheduleAction(t, 3000);
};

ZmZimbraMail._userEventHdlr =
function(ev) {
	var zm = window._zimbraMail;
	if (zm) {
		// cancel old timer and start a new one
		AjxTimedAction.cancelAction(zm._sessionTimerId);
		var timeout = zm._appCtxt.get(ZmSetting.IDLE_SESSION_TIMEOUT) * 1000;
		zm._sessionTimerId = AjxTimedAction.scheduleAction(zm._sessionTimer, timeout);
	}
	DBG.println(AjxDebug.DBG3, "INACTIVITY TIMER RESET (" + (new Date()).toLocaleString() + ")");
};

ZmZimbraMail.prototype._settingsChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_SETTING) return;
	
	var id = ev.source.id;
	if (id == ZmSetting.QUOTA_USED) {
		this._setUserInfo();
	} else if (id == ZmSetting.POLLING_INTERVAL) {
		this.setPollInterval();
	} else if (id == ZmSetting.SKIN_NAME) {
		var cd = this._confirmDialog = this._appCtxt.getYesNoMsgDialog();
		cd.reset();
		var skin = ev.source.getValue();
		cd.registerCallback(DwtDialog.YES_BUTTON, this._newSkinYesCallback, this, [skin]);
		cd.setMessage(ZmMsg.skinChangeRestart, DwtMessageDialog.WARNING_STYLE);
		cd.popup();
	} else if (id == ZmSetting.LOCALE_NAME) {
		var cd = this._confirmDialog = this._appCtxt.getYesNoMsgDialog();
		cd.reset();
		var skin = ev.source.getValue();
		cd.registerCallback(DwtDialog.YES_BUTTON, this._newLocaleYesCallback, this);
		cd.setMessage(ZmMsg.localeChangeRestart, DwtMessageDialog.WARNING_STYLE);
		cd.popup();
	} else if (id == ZmSetting.SHORTCUTS) {
		this._appCtxt.getKeyboardMgr().registerKeyMap(new ZmKeyMap(this._appCtxt));
		this._settings._loadShortcuts();
	}
};

ZmZimbraMail.prototype._newSkinYesCallback =
function(skin) {
	this._confirmDialog.popdown();
    window.onbeforeunload = null;
    var url = AjxUtil.formatUrl({qsArgs:{skin:skin}});
	DBG.println(AjxDebug.DBG1, "skin change, redirect to: " + url);
    ZmZimbraMail.sendRedirect(url); // redirect to self to force reload
};

ZmZimbraMail.prototype._newLocaleYesCallback =
function(skin) {
	this._confirmDialog.popdown();
    window.onbeforeunload = null;
    var url = AjxUtil.formatUrl();
	DBG.println(AjxDebug.DBG1, "skin change, redirect to: " + url);
    ZmZimbraMail.sendRedirect(url); // redirect to self to force reload
};

ZmZimbraMail.prototype._createBanner =
function() {
	// The LogoContainer style centers the logo
	var banner = new DwtComposite(this._shell, null, Dwt.ABSOLUTE_STYLE);
	var logoUrl = this._appCtxt.get(ZmSetting.SKIN_HINTS, "logo.url") || this._appCtxt.get(ZmSetting.LOGO_URI);
	var html = [];
	var i = 0;
	html[i++] = "<table border=0 cellpadding=0 cellspacing=0 style='width:100%;height:100%'>";
	html[i++] = "<tr><td align='center' valign='middle'>";
	html[i++] = "<a href='";
	html[i++] = logoUrl;
	html[i++] = "' target='_blank'><div class='";
	html[i++] = AjxImg.getClassForImage("AppBanner");
	html[i++] = "'></div></a></td></tr></table>";
	banner.getHtmlElement().innerHTML = html.join("");
	return banner;
};

ZmZimbraMail.prototype._createUserInfo =
function(className, cid) {
    var position = this._appCtxt.get(ZmSetting.SKIN_HINTS, [cid, "position"].join("."));
    var posStyle = position || Dwt.ABSOLUTE_STYLE;
    var ui = new DwtComposite(this._shell, className, posStyle);
    if (posStyle == Dwt.ABSOLUTE_STYLE) {
        ui.setScrollStyle(Dwt.CLIP);
    }
    ui._setMouseEventHdlrs();
	return ui;
};

ZmZimbraMail.prototype._createAppChooser =
function() {
	
	var buttons = [];
	for (var id in ZmApp.CHOOSER_SORT) {
		if (this._TAB_SKIN_ENABLED && (id == ZmAppChooser.SPACER || id == ZmAppChooser.B_HELP || id == ZmAppChooser.B_LOGOUT)) {
			continue;
		}

		var setting = ZmApp.SETTING[id];
		var upsellSetting = ZmApp.UPSELL_SETTING[id];
		if ((setting && this._appCtxt.get(setting)) || (upsellSetting && this._appCtxt.get(upsellSetting))) {
			buttons.push(id);
		}
	}
	buttons.sort(function(a, b) {
		return ZmZimbraMail.hashSortCompare(ZmApp.CHOOSER_SORT, a, b);
	});

	var appChooser = new ZmAppChooser(this._shell, null, buttons, this._TAB_SKIN_ENABLED, this._appCtxt);
	
	var buttonListener = new AjxListener(this, this._appButtonListener);
	for (var i = 0; i < buttons.length; i++) {
		var id = buttons[i];
		if (id == ZmAppChooser.SPACER) continue;
		var b = appChooser.getButton(id);
		b.addSelectionListener(buttonListener);
	}

	return appChooser;
};

ZmZimbraMail.prototype._appButtonListener =
function(ev) {
	try {
		var id = ev.item.getData(Dwt.KEY_ID);
		DBG.println(AjxDebug.DBG1, "ZmZimbraMail button press: " + id);
		if (id == ZmAppChooser.B_HELP) {
			window.open(this._appCtxt.get(ZmSetting.HELP_URI));
		} else if (id == ZmAppChooser.B_LOGOUT) {
			ZmZimbraMail.conditionalLogOff();
		} else if (id) {
			this.activateApp(id);
		}
	} catch (ex) {
		this._handleException(ex, this._appButtonListener, ev, false);
	}
};

ZmZimbraMail.prototype.getAppChooser =
function() {
	return this._appChooser;
};

ZmZimbraMail.prototype.setStatusMsg =
function(msg, level, detail) {
	this._statusView.setStatusMsg(msg, level, detail);
};

ZmZimbraMail.prototype.getKeyMapName =
function() {
	var ctlr = this._appCtxt.getCurrentController();
	if (ctlr && ctlr.getKeyMapName) {
		return ctlr.getKeyMapName();
	}
	return "Global";
};

ZmZimbraMail.prototype.handleKeyAction =
function(actionCode, ev) {

	var app = ZmApp.GOTO_ACTION_CODE_R[actionCode];
	if (app) {
		if (app == this.getActiveApp()) { return false; }
		this.activateApp(app);
		return true;
	}

	// don't honor plain Enter in an input field as an app shortcut, since it often
	// equates to button press in that situation
	if (ev && (ev.keyCode == 13 || ev.keyCode == 3) && 
		!(ev.altKey || ev.ctrlKey || ev.metaKey || ev.shiftKey) &&
		 ev.target && (ev.target.id != DwtKeyboardMgr.FOCUS_FIELD_ID)) { return false; }

	switch (actionCode) {
		case ZmKeyMap.DBG_NONE:
			this._appCtxt.setStatusMsg("Setting Debug Level To: " + AjxDebug.NONE);
			DBG.setDebugLevel(AjxDebug.NONE);
			break;
			
		case ZmKeyMap.DBG_1:
			this._appCtxt.setStatusMsg("Setting Debug Level To: " + AjxDebug.DBG1);
			DBG.setDebugLevel(AjxDebug.DBG1);
			break;
			
		case ZmKeyMap.DBG_2:
			this._appCtxt.setStatusMsg("Setting Debug Level To: " + AjxDebug.DBG2);
			DBG.setDebugLevel(AjxDebug.DBG2);
			break;
			
		case ZmKeyMap.DBG_3:
			this._appCtxt.setStatusMsg("Setting Debug Level To: " + AjxDebug.DBG3);
			DBG.setDebugLevel(AjxDebug.DBG3);
			break;
			
		case ZmKeyMap.DBG_TIMING: {
			var on = DBG._showTiming;
			var newState = on ? "off" : "on";
			this._appCtxt.setStatusMsg("Turning Timing Info " + newState);
			DBG.showTiming(!on);
			break;
		}
		
		case ZmKeyMap.ASSISTANT: {
			if (!this._assistantDialog) {
				AjxDispatcher.require("Assistant");
				this._assistantDialog = new ZmAssistantDialog(this._appCtxt);
			}
			this._assistantDialog.popup();
			break;
		}
		
		case ZmKeyMap.LOGOFF: {
			ZmZimbraMail.conditionalLogOff();
			break;
		}
		
		case ZmKeyMap.FOCUS_SEARCH_BOX: {
			var searchBox = this._appCtxt.getSearchController().getSearchToolbar().getSearchField();
			this._appCtxt.getKeyboardMgr().grabFocus(searchBox);
			break;
		}

		case ZmKeyMap.FOCUS_CONTENT_PANE: {
			this.focusContentPane();
			break;
		}
			
		default: {
			var ctlr = this._appCtxt.getCurrentController();
			return (ctlr && ctlr.handleKeyAction)
				? ctlr.handleKeyAction(actionCode, ev)
				: false;
		}
	}
	return true;
};

ZmZimbraMail.prototype.focusContentPane =
function() {
	// Set focus to the list view that's in the content pane. If there is no
	// list view in the content pane, nothing happens. The list view will be
	// found in the root tab group hierarchy.
	var ctlr = this._appCtxt.getCurrentController();
	var content = ctlr ? ctlr.getCurrentView() : null;
	if (content) {
		this._appCtxt.getKeyboardMgr().grabFocus(content);
	}
};

/**
 * Creates an "upsell view", which is a placeholder view for an app that's not
 * enabled but which has a button so that it can be promoted. The app will have
 * a URL for its upsell content, which we put into an IFRAME.
 * 
 * @param appName	[constant]		name of app
 */
ZmZimbraMail.prototype._createUpsellView =
function(appName) {
	var upsellView = this._upsellView[appName] = new DwtControl(this._shell, null, Dwt.ABSOLUTE_STYLE);
	var upsellUrl = this._appCtxt.get(ZmApp.UPSELL_URL[appName]);
	var el = upsellView.getHtmlElement();
	var htmlArr = [];
	var idx = 0;
	htmlArr[idx++] = "<iframe width='100%' height='100%' src='";
	htmlArr[idx++] = upsellUrl;
	htmlArr[idx++] = "'>";
	el.innerHTML = htmlArr.join("");
	var elements = {};
	elements[ZmAppViewMgr.C_APP_CONTENT] = upsellView;
	var viewName = [appName, "upsell"].join("_");
	this._appViewMgr.createView(viewName, appName, elements, null, true);
	this._appViewMgr.pushView(viewName);
};

/**
 * Sets up Zimlet organizer type. This is run if we get zimlets in the
 * GetInfoResponse. Note that this will run before apps are instantiated,
 * which is necessary because they depend on knowing whether there are zimlets.
 */
ZmZimbraMail.prototype._postLoadZimlet =
function() {
	this._appCtxt.setZimletsPresent(true);
	ZmOrganizer.registerOrg(ZmOrganizer.ZIMLET,
							{orgClass:			"ZmZimlet",
							 treeController:	"ZmZimletTreeController",
							 labelKey:			"zimlets",
							 compareFunc:		"ZmZimlet.sortCompare"
							});
	for (var app in ZmApp.SHOW_ZIMLETS) {
		var trees = ZmApp.OVERVIEW_TREES[app] || [];
		trees.push(ZmOrganizer.ZIMLET);
	}
};
// YUCK:
ZmOrganizer.ZIMLET = "Zimlet";
