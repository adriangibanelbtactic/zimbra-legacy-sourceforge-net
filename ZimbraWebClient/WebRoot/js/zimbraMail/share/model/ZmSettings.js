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
 * Creates a collection of settings with default values. If no app context is given,
 * then this is a skeletal, non-live version of settings which can provide default
 * settings and parse SOAP settings.
 * @constructor
 * @class
 * This class is a collection of various sorts of settings: config values, preferences,
 * and COS features. Each setting has an ID which can be used to retrieve it.
 *
 * @author Conrad Damon
 *
 * @param appCtxt	[ZmAppCtxt]*	the app context
 * @param noInit	[boolean]*		if true, skip initialization
 */
ZmSettings = function(appCtxt, noInit) {

	ZmModel.call(this, ZmEvent.S_SETTING);

	this._appCtxt = appCtxt;
	this._settings = {};	// settings by ID
	this._nameToId = {};	// map to get from server setting name to setting ID
	
	if (!noInit) {
		this.initialize();
	}
};

ZmSettings.prototype = new ZmModel;
ZmSettings.prototype.constructor = ZmSettings;

// we need these IDs available for login and startup
ZmSetting.CALENDAR_ENABLED		= "CALENDAR_ENABLED";
ZmSetting.CONTACTS_ENABLED		= "CONTACTS_ENABLED";
ZmSetting.IM_ENABLED			= "IM_ENABLED";
ZmSetting.MAIL_ENABLED			= "MAIL_ENABLED";
ZmSetting.MIXED_VIEW_ENABLED	= "MIXED_VIEW_ENABLED";
ZmSetting.NOTEBOOK_ENABLED		= "NOTEBOOK_ENABLED";
ZmSetting.BRIEFCASE_ENABLED		= "BRIEFCASE_ENABLED";
ZmSetting.OPTIONS_ENABLED		= "OPTIONS_ENABLED";
ZmSetting.PORTAL_ENABLED		= "PORTAL_ENABLED";
ZmSetting.SKIN_NAME				= "SKIN_NAME";
ZmSetting.TASKS_ENABLED			= "TASKS_ENABLED";
ZmSetting.VOICE_ENABLED			= "VOICE_ENABLED";
ZmSetting.LOCALE_NAME			= "LOCALE_NAME";

/**
 * Creates a new setting and adds it to the settings.
 *
 * @param id			[string]		unique ID of the setting
 * @param params		[hash]*			hash of params:
 *        name			[string]		the name of the pref or attr on the server
 *        type			[constant]		config, pref, or COS
 *        dataType		[constant]		string, int, or boolean (defaults to string)
 *        defaultValue	[any]			default value
 */
ZmSettings.prototype.registerSetting =
function(id, params) {
	ZmSetting[id] = id;
	var setting = this._settings[id] = new ZmSetting(id, params.name, params.type, params.dataType, params.defaultValue);
	if (params.name) {
		this._nameToId[params.name] = id;
	}
	return setting;
};

ZmSettings.prototype.toString =
function() {
	return "ZmSettings";
};

ZmSettings.prototype.initialize =
function() {
	this._initialize();
	this._setDefaults();
	this.userSettingsLoaded = false;
};

/**
* Returns the value of the given setting.
*
* @param id		the numeric ID of the setting
*/
ZmSettings.prototype.get =
function(id, key) {
	return (id && this._settings[id]) ? this._settings[id].getValue(key) : null;
};

/**
* Returns the ZmSetting object for the given setting.
*
* @param id		the numeric ID of the setting
*/
ZmSettings.prototype.getSetting =
function(id) {
	return this._settings[id];
};

/**
* Populates settings values.
*
* @param list		a hash of preference or attribute values
*/
ZmSettings.prototype.createFromJs =
function(list) {
	for (var i in list) {
		var val = list[i];
		var setting = this._settings[this._nameToId[i]];
		if (setting) {
			setting.setValue(val);
		} else {
			DBG.println(AjxDebug.DBG1, "*** Unrecognized setting: " + i);
		}
	}
};

/**
 * Returns the ID of the setting that is associated with the given server-side setting, if any.
 * 
 * @param name	[string]	server-side setting name, eg "zimbraFeatureContactsEnabled"
 */
ZmSettings.prototype.getSettingByName =
function(name) {
	return this._nameToId[name];
};

/**
* Retrieves the preferences, COS settings, and metadata for the current user.
* All the data gets stored into the settings collection.
*
* @param callback 		[AjxCallback]*		callback to run after response is received
* @param errorCallback 	[AjxCallback]*		callback to run error is received
* @param accountName	[String]*			name of account to load settings for
*/
ZmSettings.prototype.loadUserSettings =
function(callback, errorCallback, accountName) {
	var params = {
		soapDoc: AjxSoapDoc.create("GetInfoRequest", "urn:zimbraAccount"),
		accountName: accountName,
		asyncMode: true,
		callback: new AjxCallback(this, this._handleResponseLoadUserSettings, [callback, accountName]),
		errorCallback: errorCallback
	};
	this._appCtxt.getAppController().sendRequest(params);
};

ZmSettings.prototype._handleResponseLoadUserSettings =
function(callback, accountName, result) {
	var response = result.getResponse();
	var obj = response.GetInfoResponse;
	if (obj.name) {
		this._settings[ZmSetting.USERNAME].setValue(obj.name);
	}
	if (obj.lifetime) {
		this._settings[ZmSetting.TOKEN_LIFETIME].setValue(obj.lifetime);
	}
	if (obj.used) {
		this._settings[ZmSetting.QUOTA_USED].setValue(obj.used);
	}
	if (obj.prefs && obj.prefs._attrs) {
		this.createFromJs(obj.prefs._attrs);
	}
	if (obj.attrs && obj.attrs._attrs) {
		this.createFromJs(obj.attrs._attrs);
	}
	if (obj.license) {
		this._settings[ZmSetting.LICENSE_STATUS].setValue(obj.license.status);
	}

	// Create the main account. In the normal case, that is the only account, and
	// represents the user who logged in. If family mailbox is enabled, that account
	// is a parent account with dominion over child accounts.
	if (!accountName) {
		var mainAcct = this._appCtxt.getMainAccount();
		mainAcct.id = obj.id;
		mainAcct.name = obj.name;
		mainAcct.visible = true;
		mainAcct.isMain = true;
		mainAcct.loaded = true;
		mainAcct.settings = this;
		// replace dummy account with this one
		if (this._appCtxt._accounts[ZmAccount.DEFAULT_ID]) {
			this._appCtxt._accounts[mainAcct.id] = mainAcct;
			delete this._appCtxt._accounts[ZmAccount.DEFAULT_ID];
		}
		this._appCtxt.setActiveAccount(mainAcct);
	}
	
	var accounts = obj.childAccounts ? obj.childAccounts.childAccount : null;
	if (accounts) {
		// create a ZmAccount for each child account
		for (var i = 0; i < accounts.length; i++) {
			var acct = ZmAccount.createFromDom(accounts[i], this._appCtxt);
			this._appCtxt.setAccount(acct);
			if (acct.visible) {
				this._appCtxt.multiAccounts = true;
			}
		}
	}

	// handle settings whose values may depend on other settings
	var setting = this._settings[ZmSetting.GROUP_MAIL_BY];
	if (setting) {
		setting.setValue(this.get(ZmSetting.INITIAL_GROUP_MAIL_BY), null, true);
		if ((this.get(ZmSetting.GROUP_MAIL_BY) == ZmSetting.GROUP_BY_CONV) && !this.get(ZmSetting.CONVERSATIONS_ENABLED)) {
			setting.setValue(ZmSetting.GROUP_BY_MESSAGE, null, true);
		}
	}
	setting = this._settings[ZmSetting.REPLY_TO_ADDRESS];
	if (setting) {
		setting.defaultValue = this.get(ZmSetting.USERNAME);
	}
	if (!this.get(ZmSetting.SEARCH_ENABLED)) {
		setting = this._settings[ZmSetting.BROWSE_ENABLED];
		if (setting) {
			setting.setValue(false, null, true);
		}
	}
	if (this.get(ZmSetting.FORCE_CAL_OFF)) {
		setting = this._settings[ZmSetting.CALENDAR_ENABLED];
		if (setting) {
			setting.setValue(false, null, true);
		}
	}

	// bug fix #6787 - disable HTML compose in Safari until design mode is more stable
	if (AjxEnv.isSafari && !AjxEnv.isSafariNightly) {
		setting = this._settings[ZmSetting.HTML_COMPOSE_ENABLED];
		if (setting) {
			setting.setValue(false);
		}
	}
	// load Zimlets
	// NOTE: only load zimlets if main account
	if (!accountName && obj.zimlets && obj.zimlets.zimlet) {
		DBG.println(AjxDebug.DBG1, "Zimlets - Loading " + obj.zimlets.zimlet.length + " Zimlets");
		var zimletsCallback = new AjxCallback(this, this._loadZimlets, [obj.zimlets.zimlet, obj.props.prop]);
		AjxDispatcher.require("Zimlet", true, zimletsCallback);
	}

    this.userSettingsLoaded = true;
	
	if (callback) {
		callback.run(result);
	}
};

ZmSettings.prototype._loadZimlets =
function(zimlets, props) {
    var appCtxt = this._appCtxt;
    appCtxt.getZimletMgr().loadZimlets(zimlets, props);

    if (zimlets && zimlets.length) {
        // update overview tree
        var activeApp = appCtxt.getCurrentApp();
        var overview = activeApp ? activeApp.getOverview() : null;
        if (overview && overview.getTreeView(ZmOrganizer.ZIMLET)) {
        	overview.setTreeView(ZmOrganizer.ZIMLET);
        }

        // create global portlets
        if (this._appCtxt.get(ZmSetting.PORTAL_ENABLED)) {
            var portletMgr = this._appCtxt.getApp(ZmApp.PORTAL).getPortletMgr();
            var portletIds = portletMgr.createPortlets(true);
        }
    }
};

ZmSettings.prototype.loadSkinsAndLocales =
function(callback) {
	var command = new ZmBatchCommand(this._appCtxt);

	var skinDoc = AjxSoapDoc.create("GetAvailableSkinsRequest", "urn:zimbraAccount");
	var skinCallback = new AjxCallback(this, this._handleResponseLoadAvailableSkins);
	command.addNewRequestParams(skinDoc, skinCallback);

	var localeDoc = AjxSoapDoc.create("GetAllLocalesRequest", "urn:zimbraAccount");
	var localeCallback = new AjxCallback(this, this._handleResponseGetAllLocales);
	command.addNewRequestParams(localeDoc, localeCallback);

	command.run(callback);
};

ZmSettings.prototype._handleResponseLoadAvailableSkins =
function(result) {
	var resp = result.getResponse().GetAvailableSkinsResponse;
	var skins = resp.skin;
	if (skins && skins.length) {
		for (var i = 0; i < skins.length; i++) {
			var name = skins[i].name;
			this._settings[ZmSetting.AVAILABLE_SKINS].setValue(name);
		}
	}
};

ZmSettings.prototype._handleResponseGetAllLocales =
function(response) {
	var locales = response._data.GetAllLocalesResponse.locale;
	if (locales && locales.length) {
		var setting = this._settings[ZmSetting.LOCALES];
		for (var i = 0, count = locales.length; i < count; i++) {
			var locale = locales[i];
			setting.setValue({ name: locale.name, id: locale.id });
		}
		this.getSetting(ZmSetting.LOCALE_CHANGE_ENABLED).setValue(count > 0);
	}
};

/**
* Saves one or more settings.
*
* @param list			[array]				a list of ZmSetting
* @param batchCommand	[ZmBatchCommand]	Batch command. Optional
*/
ZmSettings.prototype.save =
function(list, callback, batchCommand) {
    if (!(list && list.length)) return;
    
    var soapDoc = AjxSoapDoc.create("ModifyPrefsRequest", "urn:zimbraAccount");
    var gotOne = false;
	for (var i = 0; i < list.length; i++) {
		var setting = list[i];
		if (setting.type != ZmSetting.T_PREF) {
			DBG.println(AjxDebug.DBG1, "*** Attempt to modify non-pref: " + setting.id + " / " + setting.name);
			continue;
		}
		if (!setting.name) {
			DBG.println(AjxDebug.DBG2, "Modify internal pref: " + setting.id);
			continue;
		}
		var value = setting.getValue();
		if (setting.dataType == ZmSetting.D_BOOLEAN) {
			value = value ? "TRUE" : "FALSE";
		}
		var node = soapDoc.set("pref", value);
		node.setAttribute("name", setting.name);
		gotOne = true;
	}

	if (gotOne) {
		var respCallback = new AjxCallback(this, this._handleResponseSave, [list, callback]);
		if (batchCommand) {
			batchCommand.addNewRequestParams(soapDoc, respCallback, null, "ModifyPrefsRequest");
		} else {
			this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true, callback: respCallback});
		}
	}
};

ZmSettings.prototype._handleResponseSave =
function(list, callback, result) {
	var resp = result.getResponse();
	if (resp.ModifyPrefsResponse) {
		// notify each changed setting's listeners
		for (var i = 0; i < list.length; i++) {
			var setting = list[i];
			setting.origValue = setting.value;
			if (setting.id == ZmSetting.SKIN_NAME) {
				ZmLogin.setCookie(ZmLogin.SKIN_COOKIE, setting.getValue());
			}
			setting._notify(ZmEvent.E_MODIFY);
		}
	}
	
	if (callback) callback.run(result);
};

// Set defaults which are determined dynamically (which can't be set in static code).
ZmSettings.prototype._setDefaults =
function() {
	var value;

	var noPort = (location.port == "" || location.port == "80");
    var portPrefix = noPort ? "" : ":" + location.port;

	// CSFE_SERVER_URI
	value = portPrefix + "/service/soap/";
	if (location.search && location.search.indexOf("host=") != -1) {
		value += location.search;
	}
	this._settings[ZmSetting.CSFE_SERVER_URI].setValue(value, null, false, true);

	// CSFE_MSG_FETCHER_URI
	value = portPrefix + "/service/home/~/?auth=co&";
	this._settings[ZmSetting.CSFE_MSG_FETCHER_URI].setValue(value, null, false, true);
	
	// CSFE_UPLOAD_URI
	value = portPrefix + "/service/upload";
	this._settings[ZmSetting.CSFE_UPLOAD_URI].setValue(value, null, false, true);
	
	// CSFE EXPORT URI
	value = portPrefix + "/service/home/~/?auth=co&id={0}&fmt=csv";
	this._settings[ZmSetting.CSFE_EXPORT_URI].setValue(value, null, false, true);
	
	// default sorting preferences
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.DATE_DESC, ZmController.CONVLIST_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.DATE_DESC, ZmController.CONV_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.DATE_DESC, ZmController.TRAD_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.NAME_ASC, ZmController.CONTACT_SRC_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.NAME_ASC, ZmController.CONTACT_TGT_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.NAME_ASC, ZmController.CONTACT_SIMPLE_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.NAME_ASC, ZmController.CONTACT_CARDS_VIEW, true, true);
	this._settings[ZmSetting.SORTING_PREF].setValue(ZmSearch.DUE_DATE_DESC, ZmController.TASKLIST_VIEW, true, true);
};

/**
 * Loads the user's custom shortcuts, which consist of key bindings for organizers
 * that have aliases.
 */
ZmSettings.prototype._loadShortcuts =
function() {
	var kbm = this._appCtxt.getKeyboardMgr();
	var kmm = kbm.__keyMapMgr;
	var scString = this.get(ZmSetting.SHORTCUTS);
	if (!scString || !kmm) { return; }
	var shortcuts = ZmShortcut.parse(scString, kmm);
	var maps = {};
	for (var i = 0, count = shortcuts.length; i < count; i++) {
		var sc = shortcuts[i];
		kmm.setMapping(sc.mapName, sc.keySequence, sc.action);
		kmm.setArg(sc.mapName, sc.action, sc.arg);
		maps[sc.mapName] = true;
	}

	for (var map in maps) {
		kmm.reloadMap(map);
	}
};

/**
 * Loads the standard settings and their default values.
 */
ZmSettings.prototype._initialize =
function() {
	// CONFIG SETTINGS
	this.registerSetting("AC_TIMER_INTERVAL",				{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_INT, defaultValue:300});
	this.registerSetting("ASYNC_MODE",						{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("BRANCH",							{type:ZmSetting.T_CONFIG, defaultValue:"main"});
	// next 3 are replaced during deployment
	this.registerSetting("CLIENT_DATETIME",					{type:ZmSetting.T_CONFIG, defaultValue:"@buildDateTime@"});
	this.registerSetting("CLIENT_RELEASE",					{type:ZmSetting.T_CONFIG, defaultValue:"@buildRelease@"});
	this.registerSetting("CLIENT_VERSION",					{type:ZmSetting.T_CONFIG, defaultValue:"@buildVersion@"});
	this.registerSetting("CONFIG_PATH",						{type:ZmSetting.T_CONFIG, defaultValue:appContextPath + "/js/zimbraMail/config"});
	this.registerSetting("CSFE_EXPORT_URI",					{type:ZmSetting.T_CONFIG});
	this.registerSetting("CSFE_MSG_FETCHER_URI",			{type:ZmSetting.T_CONFIG});
	this.registerSetting("CSFE_SERVER_URI",					{type:ZmSetting.T_CONFIG});
	this.registerSetting("CSFE_UPLOAD_URI",					{type:ZmSetting.T_CONFIG});
	this.registerSetting("DEV",								{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("FORCE_CAL_OFF",					{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("HELP_URI",						{type:ZmSetting.T_CONFIG, defaultValue:appContextPath + "/help/Zimbra_User_Help.htm"});
	this.registerSetting("INSTANT_NOTIFY_TIMEOUT",			{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_INT, defaultValue:300}); // seconds
	this.registerSetting("LOG_REQUEST",						{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("LOGO_URI",						{type:ZmSetting.T_CONFIG, defaultValue:"http://www.zimbra.com"});
	this.registerSetting("OFFLINE",							{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("TIMEOUT",							{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_INT, defaultValue:30}); // seconds
	this.registerSetting("USE_XML",							{type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	
	// IDs FOR HTML COMPONENTS IN THE SKIN
	this.registerSetting("SKIN_APP_BOTTOM_TOOLBAR_ID",		{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_app_bottom_toolbar"});
	this.registerSetting("SKIN_APP_CHOOSER_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_app_chooser"});
	this.registerSetting("SKIN_APP_MAIN_FULL_ID",			{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_app_main_full"});
	this.registerSetting("SKIN_APP_MAIN_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_app_main"});
	this.registerSetting("SKIN_APP_MAIN_ROW_FULL_ID",		{type:ZmSetting.T_CONFIG, defaultValue:"skin_tr_main_full"});
	this.registerSetting("SKIN_APP_MAIN_ROW_ID",			{type:ZmSetting.T_CONFIG, defaultValue:"skin_tr_main"});
	this.registerSetting("SKIN_APP_TOP_TOOLBAR_ID",			{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_app_top_toolbar"});
	this.registerSetting("SKIN_CURRENT_APP_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_current_app"});
	this.registerSetting("SKIN_LOGO_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_logo"});
	this.registerSetting("SKIN_QUOTA_INFO_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_quota"});
	this.registerSetting("SKIN_SASH_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_tree_app_sash"});
	this.registerSetting("SKIN_SEARCH_BUILDER_ID",			{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_search_builder"});
	this.registerSetting("SKIN_SEARCH_BUILDER_TOOLBAR_ID",	{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_search_builder_toolbar"});
	this.registerSetting("SKIN_SEARCH_BUILDER_TR_ID",		{type:ZmSetting.T_CONFIG, defaultValue:"skin_tr_search_builder"});
	this.registerSetting("SKIN_SEARCH_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_search"});
	this.registerSetting("SKIN_SHELL_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_outer"});
	this.registerSetting("SKIN_STATUS_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_status"});
	this.registerSetting("SKIN_STATUS_ROW_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_tr_status"});
	this.registerSetting("SKIN_TREE_FOOTER_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_tree_footer"});
	this.registerSetting("SKIN_TREE_ID",					{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_tree"});
	this.registerSetting("SKIN_USER_INFO_ID",				{type:ZmSetting.T_CONFIG, defaultValue:"skin_container_username"});
	
	// COS SETTINGS
	this.registerSetting("AVAILABLE_SKINS",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_LIST});
	this.registerSetting("BROWSE_ENABLED",					{name:"zimbraFeatureAdvancedSearchEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("CALENDAR_ENABLED",				{name:"zimbraFeatureCalendarEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("CHANGE_PASSWORD_ENABLED",			{name:"zimbraFeatureChangePasswordEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("CONTACTS_ENABLED",				{name:"zimbraFeatureContactsEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("DISPLAY_NAME",					{name:"displayName", type:ZmSetting.T_COS});
	this.registerSetting("FLAGGING_ENABLED",				{name:"zimbraFeatureFlaggingEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("GAL_AUTOCOMPLETE_ENABLED",		{name:"zimbraFeatureGalAutoCompleteEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN,	defaultValue:false});
	this.registerSetting("GAL_ENABLED",						{name:"zimbraFeatureGalEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN,	defaultValue:true});
	this.registerSetting("GROUP_CALENDAR_ENABLED",			{name:"zimbraFeatureGroupCalendarEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("HTML_COMPOSE_ENABLED",			{name:"zimbraFeatureHtmlComposeEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("IDLE_SESSION_TIMEOUT",			{name:"zimbraMailIdleSessionTimeout", type:ZmSetting.T_COS, dataType:ZmSetting.D_LDAP_TIME, defaultValue:0});
	this.registerSetting("IM_ENABLED",						{name:"zimbraFeatureIMEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("LOCALE_CHANGE_ENABLED",			{name:"zimbraFeatureLocaleChangeEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("LOCALES",							{type:ZmSetting.T_COS, dataType:ZmSetting.D_LIST});
	this.registerSetting("LOGIN_URL",						{name:"zimbraWebClientLoginURL", type:ZmSetting.T_COS});
	this.registerSetting("LOGOUT_URL",						{name:"zimbraWebClientLogoutURL", type:ZmSetting.T_COS});
	this.registerSetting("MAIL_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("MIN_POLLING_INTERVAL",			{name:"zimbraMailMinPollingInterval", type:ZmSetting.T_COS, dataType:ZmSetting.D_LDAP_TIME, defaultValue:120});
	this.registerSetting("NOTEBOOK_ENABLED",				{name:"zimbraFeatureNotebookEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("BRIEFCASE_ENABLED",				{name:"zimbraFeatureBriefcasesEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("OPTIONS_ENABLED",					{name:"zimbraFeatureOptionsEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("POP_ACCOUNTS_ENABLED",			{name:"zimbraFeaturePop3DataSourceEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("PORTAL_ENABLED",					{name:"zimbraFeaturePortalEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("PORTAL_NAME",						{name:"zimbraPortalName", type:ZmSetting.T_COS, defaultValue:"example"});
	this.registerSetting("PWD_MAX_LENGTH",					{name:"zimbraPasswordMaxLength", type:ZmSetting.T_COS, dataType:ZmSetting.D_INT, defaultValue:64});
	this.registerSetting("PWD_MIN_LENGTH",					{name:"zimbraPasswordMinLength", type:ZmSetting.T_COS, dataType:ZmSetting.D_INT, defaultValue:6});
	this.registerSetting("QUOTA",							{name:"zimbraMailQuota", type:ZmSetting.T_COS, dataType:ZmSetting.D_INT, defaultValue:0});
	this.registerSetting("SAVED_SEARCHES_ENABLED",			{name:"zimbraFeatureSavedSearchesEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("SHARING_ENABLED",					{name:"zimbraFeatureSharingEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("SHORTCUT_ALIASES_ENABLED",		{name:"zimbraFeatureShortcutAliasesEnabled", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("SKIN_CHANGE_ENABLED",				{name:"zimbraFeatureSkinChangeEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("TAGGING_ENABLED",					{name:"zimbraFeatureTaggingEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("TASKS_ENABLED",					{name:"zimbraFeatureTasksEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("VIEW_ATTACHMENT_AS_HTML",			{name:"zimbraFeatureViewInHtmlEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("VOICE_ENABLED",					{name:"zimbraFeatureVoiceEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});	
	// user metadata (included with COS since the user can't change them)
	this.registerSetting("LICENSE_STATUS",					{type:ZmSetting.T_COS, defaultValue:ZmSetting.LICENSE_GOOD});
	this.registerSetting("QUOTA_USED",						{type:ZmSetting.T_COS, dataType:ZmSetting.D_INT});
	this.registerSetting("TOKEN_LIFETIME",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_INT});
	this.registerSetting("USERID",							{name:"zimbraId", type:ZmSetting.T_COS});
	this.registerSetting("USERNAME",						{type:ZmSetting.T_COS});
	this.registerSetting("CN",								{name:"cn", type:ZmSetting.T_COS});
	
	// CLIENT SIDE FEATURE SUPPORT
	this.registerSetting("ATTACHMENT_ENABLED",				{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("ATT_VIEW_ENABLED",				{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("EVAL_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("FEED_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("HELP_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("MIXED_VIEW_ENABLED",				{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("NOTES_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("PRINT_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("SEARCH_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("SKI_HACK_ENABLED",				{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	
	// USER PREFERENCES (mutable)
	
	// general preferences
	this.registerSetting("ACCOUNTS",						{type: ZmSetting.T_PREF, dataType: ZmSetting.D_HASH});
	this.registerSetting("COMPOSE_AS_FORMAT",				{name:"zimbraPrefComposeFormat", type:ZmSetting.T_PREF, defaultValue:ZmSetting.COMPOSE_TEXT});
	this.registerSetting("COMPOSE_INIT_FONT_COLOR",			{name:"zimbraPrefHtmlEditorDefaultFontColor", type:ZmSetting.T_PREF, defaultValue:ZmSetting.COMPOSE_FONT_COLOR});
	this.registerSetting("COMPOSE_INIT_FONT_FAMILY",		{name:"zimbraPrefHtmlEditorDefaultFontFamily", type:ZmSetting.T_PREF, defaultValue:ZmSetting.COMPOSE_FONT_FAM});
	this.registerSetting("COMPOSE_INIT_FONT_SIZE",			{name:"zimbraPrefHtmlEditorDefaultFontSize", type:ZmSetting.T_PREF, defaultValue:ZmSetting.COMPOSE_FONT_SIZE});
	this.registerSetting("LOCALE_NAME",						{name:"zimbraPrefLocale", type:ZmSetting.T_PREF, defaultValue:"en_US"});
	this.registerSetting("SHOW_SELECTION_CHECKBOX",			{name:"zimbraPrefShowSelectionCheckbox", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("PASSWORD",						{type:ZmSetting.T_PREF, dataType:ZmSetting.D_NONE});
	this.registerSetting("POLLING_INTERVAL",				{name:"zimbraPrefMailPollingInterval", type:ZmSetting.T_PREF, dataType:ZmSetting.D_LDAP_TIME, defaultValue:300});
	this.registerSetting("SEARCH_INCLUDES_SPAM",			{name:"zimbraPrefIncludeSpamInSearch", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("SEARCH_INCLUDES_TRASH",			{name:"zimbraPrefIncludeTrashInSearch", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("SHORTCUTS",						{name:"zimbraPrefShortcuts", type:ZmSetting.T_PREF});
	this.registerSetting("SHOW_SEARCH_STRING",				{name:"zimbraPrefShowSearchString", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("SKIN_NAME",						{name:"zimbraPrefSkin", type:ZmSetting.T_PREF, defaultValue:"skin"});
	this.registerSetting("SORTING_PREF",					{type:ZmSetting.T_PREF, dataType:ZmSetting.D_HASH});
	this.registerSetting("USE_KEYBOARD_SHORTCUTS",			{name:"zimbraPrefUseKeyboardShortcuts", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("VIEW_AS_HTML",					{name:"zimbraPrefMessageViewHtmlPreferred", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("WARN_ON_EXIT",					{name:"zimbraPrefWarnOnExit", type:ZmSetting.T_PREF, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});

	this._registerSkinHints();
	this.registerSetting("FILTERS",							{type: ZmSetting.T_PREF, dataType: ZmSetting.D_HASH});
	this.registerSetting("IDENTITIES",						{type: ZmSetting.T_PREF, dataType: ZmSetting.D_HASH});
	this.registerSetting("VOICE_ACCOUNTS",					{type: ZmSetting.T_PREF, dataType: ZmSetting.D_HASH});
};

ZmSettings.prototype._initializeLoginSettings =
function() {
	this.registerSetting("CALENDAR_ENABLED",				{name:"zimbraFeatureCalendarEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("CONTACTS_ENABLED",				{name:"zimbraFeatureContactsEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("IM_ENABLED",						{name:"zimbraFeatureIMEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("MAIL_ENABLED",					{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("MIXED_VIEW_ENABLED",				{type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("NOTEBOOK_ENABLED",				{name:"zimbraFeatureNotebookEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("BRIEFCASE_ENABLED",				{name:"zimbraFeatureBriefcasesEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("OPTIONS_ENABLED",					{name:"zimbraFeatureOptionsEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:true});
	this.registerSetting("PORTAL_ENABLED",					{name:"zimbraFeaturePortalEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("SKIN_NAME",						{name:"zimbraPrefSkin", type:ZmSetting.T_PREF, defaultValue:"skin"});
	this.registerSetting("LOCALE_NAME",						{name:"zimbraPrefLocale", type:ZmSetting.T_PREF, defaultValue:"en_US"});
	this.registerSetting("TASKS_ENABLED",					{name:"zimbraFeatureTasksEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
	this.registerSetting("VOICE_ENABLED",					{name:"zimbraFeatureVoiceEnabled", type:ZmSetting.T_COS, dataType:ZmSetting.D_BOOLEAN, defaultValue:false});
};

/**
 * Provide a settings interface to the global skin.hints.* values. The hints are
 * folded into a hash-type setting. The key is the part that comes after
 * "skin.hints.". For example, to get the value of
 * 
 *     skin.hints.app_chooser.style
 * 
 * just ask for
 * 
 *     appCtxt.get(ZmSetting.SKIN_HINTS, "app_chooser.style")
 * 
 * The main reason for doing this is to centralize the knowledge of the various
 * skin hints.
 */
ZmSettings.prototype._registerSkinHints =
function() {

	var shSetting = this.registerSetting("SKIN_HINTS", {type:ZmSetting.T_CONFIG, dataType:ZmSetting.D_HASH});
	
	var hints = ["app_chooser.style",
				 "app_chooser.fullWidth",
				 "help_button.hideIcon",
				 "help_button.style",
				 "logo.url",
				 "logout_button.hideIcon",
				 "logout_button.style",
				 "noOverviewHeaders",
				 "toast.location",
				 "toast.transitions"];

	for (var i = 0, count = hints.length; i < count; i++) {
		var hint = hints[i];
		try {
			// if we get an exception doing the eval, ignore it - that hint won't get a value
			var value = eval(["skin.hints", hint].join("."));
			shSetting.setValue(value, hint, true, true);
		} catch(e) {}
	}
	
	// skin.hints.[container ID].position
	for (var i = 0, count = ZmAppViewMgr.ALL_COMPONENTS.length; i < count; i++) {
		var cid = ZmAppViewMgr.ALL_COMPONENTS[i];
		var test = skin.hints[cid];
		if (test && test.position) {
			var key = [cid, "position"].join(".");
			shSetting.setValue(test.position, key, true, true);
		}
	}
};
