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
* @class ZaApp
* @constructor ZaApp
* @param appCtxt instance of ZaAppCtxt
* @param container
* @author Greg Solovyev
**/
ZaApp = function(appCtxt, container) {
	if (arguments.length == 0) return;
	this._name = ZaZimbraAdmin.ADMIN_APP;
	this._appCtxt = appCtxt;
	this._appViewMgr = appCtxt.getAppViewMgr();
	this._container = container;
	this._currentController = null;
	this._currentViewId = null;
	this._cosListChoices = null;//new XFormChoices([], XFormChoices.OBJECT_LIST, "id", "name");	
	this._domainListChoices = null;//new XFormChoices([], XFormChoices.OBJECT_LIST, "name", "name");	
	this._serverChoices = null; 
	this._serverIdChoices = null;
	this._serverMap = null;
	this._controllers = new Object();
	this.dialogs = {};
	this._tabGroup = null ;
}

ZaApp.prototype.constructor = ZaApp;

ZaApp.prototype.toString = 
function() {
	return "ZaApp";
}

ZaApp.prototype.launch =
function(appCtxt) {
	if(ZaSettings.STATUS_ENABLED) {
		var ctl = this._appCtxt.getAppController().getOverviewPanelController();
		ctl.getOverviewPanel().getFolderTree().setSelection(ctl.statusTi);
		//this.getStatusViewController().show(false);
	} else if(ZaSettings.ADDRESSES_ENABLED) {
		var ctl = this._appCtxt.getAppController().getOverviewPanelController();
		ctl.getOverviewPanel().getFolderTree().setSelection(ctl.accountTi);		
		//this._appCtxt.getAppController()._showAccountsView(ZaItem.ACCOUNT,null);
	}

	if(ZaSettings.DOMAINS_ENABLED) {
		this.searchDomains("");
	}
}

ZaApp.prototype.setActive =
function(active) {
	if (active) {
		if(ZaSettings.STATUS_ENABLED) {
			var ctl = this._appCtxt.getAppController().getOverviewPanelController();
			ctl.getOverviewPanel().getFolderTree().setSelection(ctl.statusTi);

			//this.getStatusViewController().show();	
		} else if(ZaSettings.ADDRESSES_ENABLED) {
			var ctl = this._appCtxt.getAppController().getOverviewPanelController();
			ctl.getOverviewPanel().getFolderTree().setSelection(ctl.accountTi);		

			//this._appCtxt.getAppController()._showAccountsView(ZaItem.ACCOUNT,null);
		}
	}
}

ZaApp.prototype.getAppCtxt = 
function() {
	return this._appCtxt;
}

ZaApp.prototype.getCurrentController = 
function(ctrlr) {
	return this._controllers[this._currentViewId];
}

ZaApp.prototype.getControllerById =
function (id) {
	return this._controllers[id] ;
}

/**
* View controllers
**/
ZaApp.prototype.getStatusViewController =
function(viewId) {
	if(!viewId)
		viewId = ZaZimbraAdmin._STATUS;
			
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c  = this._controllers[viewId] = new ZaStatusViewController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.getServerStatsController =
function(viewId) {

	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c  = this._controllers[viewId] = new ZaServerStatsController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.getGlobalStatsController =
function(viewId) {
	if(!viewId)
		viewId = ZaZimbraAdmin._STATISTICS;
		
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaGlobalStatsController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.getGlobalConfigViewController =
function(viewId) {
	if(!viewId)
		viewId = ZaZimbraAdmin._GLOBAL_SETTINGS;
	
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c  = this._controllers[viewId] = new ZaGlobalConfigViewController(this._appCtxt, this._container, this);
		c.addSettingsChangeListener(new AjxListener(this, ZaApp.prototype.handleSettingsChange));
		return c ;
	}
}

ZaApp.prototype.getSearchListController =
function() {
	if (this._controllers[ZaZimbraAdmin._SEARCH_LIST_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._SEARCH_LIST_VIEW] = new ZaSearchListController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._SEARCH_LIST_VIEW].addRemovalListener(new AjxListener(this.getSearchListController(), this.getSearchListController().handleRemoval));							
	}
	return this._controllers[ZaZimbraAdmin._SEARCH_LIST_VIEW] ;
}

ZaApp.prototype.getSearchBuilderController =
function() {
	if (this._controllers[ZaZimbraAdmin._SEARCH_BUILDER_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._SEARCH_BUILDER_VIEW] = new ZaSearchBuilderController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._SEARCH_BUILDER_VIEW].addRemovalListener(new AjxListener(this.getSearchBuilderController(), this.getSearchBuilderController().handleRemoval));							
	}
	return this._controllers[ZaZimbraAdmin._SEARCH_BUILDER_VIEW] ;
}

ZaApp.prototype.getSearchBuilderToolbarController = ZaApp.prototype.getSearchBuilderController ;


ZaApp.prototype.getAccountListController =
function(viewId, newController) {
	if(!viewId)
		viewId = ZaZimbraAdmin._ACCOUNTS_LIST_VIEW;
			
	//this is used by SearchListController to associate its view with a new 
	//account list controller
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else if (viewId || newController) {
		var c = this._controllers[viewId] = new ZaAccountListController(this._appCtxt, this._container, this);
		c.addRemovalListener(new AjxListener(c, c.handleRemoval));							
		c.addCreationListener(new AjxListener(c, c.handleCreation));									
		return c ;
	}

}

ZaApp.prototype.getAccountViewController =
function(isAlias) {
	var c = new ZaAccountViewController(this._appCtxt, this._container, this);
	var viewId = ZaZimbraAdmin._ACCOUNTS_LIST_VIEW ;
	if (isAlias) {
		viewId = ZaZimbraAdmin._ALIASES_LIST_VIEW ;
	}
	c.addChangeListener(new AjxListener(this.getAccountListController(viewId), ZaAccountListController.prototype.handleChange));
	c.addCreationListener(new AjxListener(this.getAccountListController(viewId), ZaAccountListController.prototype.handleCreation));	
	c.addRemovalListener(new AjxListener(this.getAccountListController(viewId), ZaAccountListController.prototype.handleRemoval));			
	return c ;
}

ZaApp.prototype.getAdminExtListController = 
function() {
	if (this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW] = new ZaAdminExtListController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].addRemovalListener(new AjxListener(this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].handleRemoval));							
		this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].addCreationListener(new AjxListener(this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].handleCreation));			
		this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].addChangeListener(new AjxListener(this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW].handleChange));
	}
	
	return this._controllers[ZaZimbraAdmin._ADMIN_ZIMLET_LIST_VIEW]
}

ZaApp.prototype.getZimletListController =
function() {
	if (this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW] = new ZaZimletListController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].addRemovalListener(new AjxListener(this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].handleRemoval));							
		this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].addCreationListener(new AjxListener(this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].handleCreation));			
		this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].addChangeListener(new AjxListener(this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW], this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW].handleChange));
	}
	
	return this._controllers[ZaZimbraAdmin._ZIMLET_LIST_VIEW]
}

ZaApp.prototype.getZimletController =
function(viewId) {
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c  = new ZaZimletViewController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.getDistributionListController = 
function (viewId) {
		if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = new ZaDLController(this._appCtxt, this._container, this);
		c.addCreationListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._DISTRIBUTION_LISTS_LIST_VIEW), ZaAccountListController.prototype.handleCreation));			
		c.addRemovalListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._DISTRIBUTION_LISTS_LIST_VIEW), ZaAccountListController.prototype.handleRemoval));			
		c.addChangeListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._DISTRIBUTION_LISTS_LIST_VIEW), ZaAccountListController.prototype.handleChange));
		return c ;
	}
	
};

ZaApp.prototype.getResourceController = 
function (viewId) {
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = new ZaResourceController(this._appCtxt, this._container, this);
		c.addCreationListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._RESOURCE_LIST_VIEW), ZaAccountListController.prototype.handleCreation));			
		c.addRemovalListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._RESOURCE_LIST_VIEW), ZaAccountListController.prototype.handleRemoval));			
		c.addChangeListener(new AjxListener(this.getAccountListController(ZaZimbraAdmin._RESOURCE_LIST_VIEW), ZaAccountListController.prototype.handleChange));
		return c ;
	}
};

ZaApp.prototype.getDomainListController =
function(viewId, newController) {
	if(!viewId)
		viewId = ZaZimbraAdmin._DOMAINS_LIST_VIEW;
			
	//this is used by SearchListController to associate its view with a new 
	//domain list controller
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else if (viewId || newController) {
		var c = this._controllers[viewId] = new ZaDomainListController(this._appCtxt, this._container, this);
		c.addCreationListener(new AjxListener(this, ZaApp.prototype.handleDomainCreation));					
		c.addRemovalListener(new AjxListener(this, ZaApp.prototype.handleDomainRemoval));							
		return c ;
	}
}

ZaApp.prototype.getDomainController =
function(viewId) {
	
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaDomainController(this._appCtxt, this._container, this);
		//since we are creating the account controller now - register all the interested listeners with it
		c.addChangeListener(new AjxListener(this.getDomainListController(), ZaDomainListController.prototype.handleDomainChange));
		c.addCreationListener(new AjxListener(this, ZaApp.prototype.handleDomainCreation));					
		c.addCreationListener(new AjxListener(this.getDomainListController(), ZaDomainListController.prototype.handleCreation));	
		c.addRemovalListener(new AjxListener(this.getDomainListController(), this.getDomainListController().handleRemoval));			
		c.addRemovalListener(new AjxListener(this, ZaApp.prototype.handleDomainRemoval));							

		return c ;
	}
}

ZaApp.prototype.getMTAListController =
function () {
	if (this._controllers[ZaZimbraAdmin._POSTQ_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._POSTQ_VIEW] = new ZaMTAListController(this._appCtxt, this._container, this);
	}
	return this._controllers[ZaZimbraAdmin._POSTQ_VIEW];
}

ZaApp.prototype.getMTAController =
function (viewId) {

	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaMTAController(this._appCtxt, this._container, this);
		c.addChangeListener(new AjxListener(this.getMTAListController(), ZaMTAListController.prototype.handleMTAChange));		
		c.addChangeListener(new AjxListener(c, ZaMTAController.prototype.handleMTAChange));				
		return c ;
	}
}

ZaApp.prototype.getServerListController =
function() {
	if (this._controllers[ZaZimbraAdmin._SERVERS_LIST_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._SERVERS_LIST_VIEW] = new ZaServerListController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._SERVERS_LIST_VIEW].addServerRemovalListener(new AjxListener(this, ZaApp.prototype.handleServerRemoval));	
		this._controllers[ZaZimbraAdmin._SERVERS_LIST_VIEW].addServerRemovalListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleServerRemoval));							
	}
	return this._controllers[ZaZimbraAdmin._SERVERS_LIST_VIEW];
}

ZaApp.prototype.getServerController =
function(viewId) {

	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaServerController(this._appCtxt, this._container, this);
		c.addServerChangeListener(new AjxListener(this, ZaApp.prototype.handleServerChange));		
		c.addServerChangeListener(new AjxListener(this.getServerListController(), ZaServerListController.prototype.handleServerChange));		
		c.addServerChangeListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleServerChange));									
		return c ;
	}
	
}

ZaApp.prototype.getCosListController =
function() {
	if (this._controllers[ZaZimbraAdmin._COS_LIST_VIEW] == null) {
		this._controllers[ZaZimbraAdmin._COS_LIST_VIEW] = new ZaCosListController(this._appCtxt, this._container, this);
		this._controllers[ZaZimbraAdmin._COS_LIST_VIEW].addCosRemovalListener(new AjxListener(this, ZaApp.prototype.handleCosRemoval));			
		this._controllers[ZaZimbraAdmin._COS_LIST_VIEW].addCosRemovalListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleCosRemoval));									
	}
	return this._controllers[ZaZimbraAdmin._COS_LIST_VIEW];
}


ZaApp.prototype.getCosController =
function() {
	var c = new ZaCosController(this._appCtxt, this._container, this);
	c.addChangeListener(new AjxListener(this, ZaApp.prototype.handleCosChange));			
	c.addChangeListener(new AjxListener(this.getCosListController(), ZaCosListController.prototype.handleCosChange));
	c.addChangeListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleCosChange));						

	c.addCosCreationListener(new AjxListener(this.getCosListController(), ZaCosListController.prototype.handleCosCreation));	
	c.addCosCreationListener(new AjxListener(this, ZaApp.prototype.handleCosCreation));			
	c.addCosCreationListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleCosCreation));				
	
	c.addCosRemovalListener(new AjxListener(this, ZaApp.prototype.handleCosRemoval));			
	c.addCosRemovalListener(new AjxListener(this.getCosListController(), ZaCosListController.prototype.handleCosRemoval));			
	c.addCosRemovalListener(new AjxListener(this._appCtxt.getAppController().getOverviewPanelController(), ZaOverviewPanelController.prototype.handleCosRemoval));						
	return c ;

}

ZaApp.prototype.getHelpViewController =
function(viewId) {

	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaHelpViewController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.getMigrationWizController = 
function(viewId) {
	
	if (viewId && this._controllers[viewId] != null) {
		return this._controllers[viewId];
	}else{
		var c = this._controllers[viewId] = new ZaMigrationWizController(this._appCtxt, this._container, this);
		return c ;
	}
}

ZaApp.prototype.searchDomains = function(query) {
	var callback = new AjxCallback(this, this.domainSearchCallback, null);
	var searchParams = {
			query:query, 
			types:[ZaSearch.DOMAINS],
			sortBy:ZaDomain.A_domainName,
			offset:"0",
			sortAscending:"1",
			limit:ZaDomain.MAXSEARCHRESULTS,
			callback:callback,
			controller: this.getCurrentController()
	}
	ZaSearch.searchDirectory(searchParams);
}

ZaApp.prototype.scheduledSearchDomains = function(domainItem) {
	var callback = new AjxCallback(this, this.domainSearchCallback, domainItem);
	var searchParams = {
			query: this._domainQuery, 
			types:[ZaSearch.DOMAINS],
			sortBy:ZaDomain.A_domainName,
			offset:"0",
			sortAscending:"1",
			limit:ZaDomain.MAXSEARCHRESULTS,
			callback:callback,
			controller: this.getCurrentController()
	}
	ZaSearch.searchDirectory(searchParams);
}

ZaApp.prototype.domainSearchCallback = 
function (domainItem, resp) {
	try {
		if(!resp) {
			throw(new AjxException(ZaMsg.ERROR_EMPTY_RESPONSE_ARG, AjxException.UNKNOWN, "ZaListViewController.prototype.searchCallback"));
		}
		if(resp.isException()) {
			//throw(resp.getException());
			ZaSearch.handleTooManyResultsException(resp.getException(), "ZaApp.prototype.domainSearchCallback");
		} else {
			ZaSearch.TOO_MANY_RESULTS_FLAG = false ;
			var response = resp.getResponse().Body.SearchDirectoryResponse;
			this._domainList = new ZaItemList(ZaDomain, this);	
			this._domainList.loadFromJS(response);
			this._appCtxt.getAppController().getOverviewPanelController().updateDomainList(this._domainList);				
			EmailAddr_XFormItem.domainChoices.setChoices(this._domainList.getArray());
			EmailAddr_XFormItem.domainChoices.dirtyChoices();
			
			if (domainItem != null && domainItem instanceof XFormItem && this._domainList.size() <= 0) {
				domainItem.setError(ZaMsg.ERROR_NO_SUCH_DOMAIN) ;
				var event = new DwtXFormsEvent(this, domainItem, domainItem.getInstanceValue());
				domainItem.getForm().notifyListeners(DwtEvent.XFORMS_VALUE_ERROR, event);
			}
		}
	} catch (ex) {
		if (ex.code != ZmCsfeException.MAIL_QUERY_PARSE_ERROR) {
			this.getCurrentController()._handleException(ex, "ZaApp.prototype.domainSearchCallback");	
		} else {
			this.getCurrentController().popupErrorDialog(ZaMsg.queryParseError, ex);
		}		
	}
}
ZaApp.prototype.getDomainList =
function(refresh) {
	if (refresh || this._domainList == null) {
		this._domainList = ZaDomain.getAll(this);
		/*EmailAddr_XFormItem.domainChoices.setChoices(this._domainList.getArray());
		EmailAddr_XFormItem.domainChoices.dirtyChoices();*/
	}
	return this._domainList;	
}

ZaApp.prototype.getSavedSearchList =
function (refresh) {
	if (refresh || ZaSearch.SAVED_SEARCHES.length <=0) {
		ZaSearch.updateSavedSearch (ZaSearch.getSavedSearches()) ;
	}
	
	return ZaSearch.SAVED_SEARCHES ;
}

ZaApp.prototype.getDomainListChoices =
function(refresh) {
	if (refresh || this._domainList == null) {
		this._domainList = ZaDomain.getAll(this);
	}
	if(refresh || this._domainListChoices == null) {
		if(this._domainListChoices == null)
			this._domainListChoices = new XFormChoices([], XFormChoices.OBJECT_LIST, "name", "name");	

		this._domainListChoices.setChoices(this._domainList.getArray());
		this._domainListChoices.dirtyChoices();

	}
	return this._domainListChoices;	
}

ZaApp.prototype.getServerByName =
function(serverName) {
	if (this._serverList == null) {
//		DBG.println(AjxDebug.DBG1, "ZaApp.prototype.getServerByName :: this._serverList is null ");
		this._serverList = ZaServer.getAll(this);
	}
	var cnt = this._serverList.getArray().length;
	var myServer = new ZaServer(this);
	for(var i = 0; i < cnt; i++) {
		if(this._serverList.getArray()[i].attrs[ZaServer.A_ServiceHostname] == serverName)
			return this._serverList.getArray()[i];
	}
	if(i == cnt) {
		myServer.load("name", serverName);
	}
	return myServer;	
}

ZaApp.prototype.getServerList =
function(refresh) {
	if (refresh || this._serverList == null) {
		this._serverList = ZaServer.getAll(this);
	}
	return this._serverList;	
}

ZaApp.prototype.getPostQList = 
function (refresh) {
	if (refresh || this._postqList == null) {
		this._postqList = ZaMTA.getAll(this);
	}
	return this._postqList;	
}

ZaApp.prototype.getMailServers =
function(refresh) {
	if (refresh || this._serverList == null) {
		this._serverList = ZaServer.getAll(this);
	}
	var resArray = new Array();
	var tmpArray = this._serverList.getArray();
	var cnt = tmpArray.length;
	for(var i = 0; i < cnt; i++) {
		if(tmpArray[i].attrs[ZaServer.A_zimbraMailboxServiceEnabled]) {
			resArray.push(tmpArray[i]);
		}
	}
	return resArray;
}



ZaApp.prototype.getClusterServerChoices = 
function(refresh){
	if (refresh || this._clusterServerList == null) {
		this._clusterServerList = ZaClusterStatus.getServerList();
	}
	if (refresh || this._clusterServerChoices == null) {
		if (this._clusterServerChoices == null ) {
			this._clusterServerChoices = new XFormChoices(this._clusterServerList, XFormChoices.OBJECT_LIST, "name", "name");
		} else {
			this._clusterServerChoices.setChoices(this._clusterServerList);
			this._clusterServerChoices.dirtyChoices();
		}
	}
	return this._clusterServerChoices;
};

ZaApp.prototype.getServerListChoices =
function(refresh) {
	if (refresh || this._serverList == null) {
		this._serverList = ZaServer.getAll(this);
	}
	if(refresh || this._serverChoices == null) {
		var hashMap = this._serverList.getIdHash();
		var mailServerArr = [];
		for (var i in hashMap) {
			if (hashMap[i].attrs[ZaServer.A_zimbraMailboxServiceEnabled]){
				mailServerArr.push(hashMap[i]);
			}
		}
		if(this._serverChoices == null) {
			this._serverChoices = new XFormChoices(mailServerArr, XFormChoices.OBJECT_LIST, ZaServer.A_ServiceHostname, ZaServer.A_ServiceHostname);
		} else {	
			this._serverChoices.setChoices(mailServerArr);
			this._serverChoices.dirtyChoices();
		}
	}
	return this._serverChoices;	
}

ZaApp.prototype.getServerIdListChoices =
function(refresh) {
	if (refresh || this._serverList == null) {
		this._serverList = ZaServer.getAll(this);
	}
	if(refresh || this._serverIdChoices == null) {
		var hashMap = this._serverList.getIdHash();
		var mailServerArr = [];
		for (var i in hashMap) {
			if (hashMap[i].attrs[ZaServer.A_zimbraMailboxServiceEnabled]){
				var obj = new Object();
				obj[ZaServer.A_ServiceHostname] = hashMap[i].attrs[ZaServer.A_ServiceHostname];
				obj.id = hashMap[i].id;
				mailServerArr.push(obj);
			}
		}
		if(this._serverIdChoices == null) {
			this._serverIdChoices = new XFormChoices(mailServerArr, XFormChoices.OBJECT_LIST, "id", ZaServer.A_ServiceHostname);
		} else {	
			this._serverIdChoices.setChoices(mailServerArr);
			this._serverIdChoices.dirtyChoices();
		}
	}
	return this._serverIdChoices;	
}

ZaApp.prototype.getServerMap =
function(refresh) {
	if(refresh || this._serverList == null) {
//		DBG.println(AjxDebug.DBG1, "ZaApp.prototype.getServerMap :: this._serverList is null ");						
		this._serverList = ZaServer.getAll(this);
	}
	if(refresh || this._serverMap == null) {
		this._serverMap = new Object();
		var cnt = this._serverList.getArray().length;
		for (var i = 0; i < cnt; i ++) {
			this._serverMap[this._serverList.getArray()[i].id] = this._serverList.getArray()[i];
		}
	}
	return this._serverMap;
}

ZaApp.prototype.getCosList =
function(refresh) {
	if (refresh || !this._cosList) {
		if(!this._cosList)
			this._cosList = new ZaItemList(ZaCos, this);
			
		ZaCos.loadAll(this,this._cosList);
	}
	return this._cosList;	
}

ZaApp.prototype.getCosListChoices =
function(refresh) {
	if (refresh || this._cosList == null) {
		this._cosList = ZaCos.getAll(this);
	}
	if(refresh || this._cosListChoices == null) {
		if(this._cosListChoices == null)
			this._cosListChoices = new XFormChoices([], XFormChoices.OBJECT_LIST, "id", "name");	

		this._cosListChoices.setChoices(this._cosList.getArray());
		this._cosListChoices.dirtyChoices();

	}
	return this._cosListChoices;	
}

/*
ZaApp.prototype.getStatusList =
function(refresh) {
	if (refresh || this._statusList == null) {
		this._statusList = ZaStatus.loadStatusTable();
	}
	return this._statusList;	
}
*/
/*
ZaApp.prototype.getAccountList =
function(refresh) {
	if (refresh || this._accountList == null) {
		this._accountList = ZaSearch.getAll(this).list;
	}
	return this._accountList;	
}*/

ZaApp.prototype.getGlobalConfig =
function(refresh) {
	if (refresh || this._globalConfig == null) {
		this._globalConfig = new ZaGlobalConfig(this);
	}
	return this._globalConfig;	
}

ZaApp.prototype.getInstalledSkins = 
function(refresh) {
	return this.getGlobalConfig(refresh).attrs[ZaGlobalConfig.A_zimbraInstalledSkin];
}

/**
* @param ev
* This listener is invoked by any controller that can create an ZaDomain object
**/
ZaApp.prototype.handleDomainCreation = 
function (ev) {
	if(ev) {
		this.searchDomains();
	}
}

/**
* @param ev
* This listener is invoked by any controller that can create an ZaCos object
**/
ZaApp.prototype.handleCosCreation = 
function (ev) {
	if(ev) {
		//add the new ZaCos to the controlled list
		if(ev.getDetails()) {
			if(!this._cosList) {
				this._cosList=ZaCos.getAll(this);
			} else {
				this._cosList.add(ev.getDetails());
			}
			if(this._cosListChoices == null) {
				this._cosListChoices = new XFormChoices(this._cosList.getArray(), XFormChoices.OBJECT_LIST, "id", "name");	
			} else {
				this._cosListChoices.setChoices(this._cosList.getArray());
				this._cosListChoices.dirtyChoices();			
			}
		}
	}
}

/**
* @param ev
* This listener is invoked by any controller that can change an ZaCos object
**/
ZaApp.prototype.handleCosChange = 
function (ev) {
	if(ev) {
		//add the new ZaCos to the controlled list
		if(ev.getDetails()) {
			if(!this._cosList) {
				this._cosList=ZaCos.getAll(this);
			} else {
				//find the modified COS 
				this._cosList.replaceItem(ev.getDetails());
			}
			
			if(this._cosListChoices == null) {
				this._cosListChoices = new XFormChoices(this._cosList.getArray(), XFormChoices.OBJECT_LIST, "id", "name");	
			} else {
				this._cosListChoices.setChoices(this._cosList.getArray());
				this._cosListChoices.dirtyChoices();			
			}
		}
	}
}
/**
* @param ev
* This listener is invoked by any controller that can create an ZaAccount object
**/
/*
ZaApp.prototype.handleAccountCreation = 
function (ev) {
	if(ev) {
		//add the new ZaAccount to the controlled list
		if(ev.getDetails()) {
			if(!this._accountList) {
				this._accountList=ZaSearch.getAll().list;
			} else {
				this._accountList.add(ev.getDetails());
			}
		}
	}
}
*/
/**
* @param ev
* This listener is invoked by ZaAccountViewController or any other controller that can remove an ZaAccount object
**/
/*
ZaApp.prototype.handleAccountRemoval = 
function (ev) {
	if(ev) {
		if(!this._accountList) {
			this._accountList=ZaSearch.getAll().list;
		} else {
			//remove the ZaAccount from the controlled list
			var detls = ev.getDetails();
			if(detls && (detls instanceof Array)) {
				for (var key in detls) {
					this._accountList.remove(detls[key]);
				}
			} else if(detls && (detls instanceof ZaAccount)) {
				this._accountList.remove(ev.getDetails());
			}
		}
	}
}
*/
/**
* @param ev
* This listener is invoked by ZaCosController or any other controller that can remove an ZaCos object
**/
ZaApp.prototype.handleCosRemoval = 
function (ev) {
	if(ev) {
		if(!this._cosList) {
			this._cosList=ZaCos.getAll(this);
		} else {
			//remove the ZaCos from the controlled list
			var detls = ev.getDetails();
			if(detls && (detls instanceof Array)) {
				for (var key in detls) {
					this._cosList.remove(detls[key]);
				}
			} else if(detls && (detls instanceof ZaCos)) {
				this._cosList.remove(ev.getDetails());
			}
		}
		if(this._cosListChoices == null) {
			this._cosListChoices = new XFormChoices(this._cosList.getArray(), XFormChoices.OBJECT_LIST, "id", "name");	
		} else {
			this._cosListChoices.setChoices(this._cosList.getArray());
			this._cosListChoices.dirtyChoices();			
		}
	}
}

ZaApp.prototype.handleServerChange = 
function (ev) {
	if(ev) {
		if(this._serverList) {
			this._serverList=ZaServer.getAll(this);
			if(this._serverChoices == null) {
				this._serverChoices = new XFormChoices(this._serverList.getArray(), XFormChoices.OBJECT_LIST, ZaServer.A_ServiceHostname, ZaServer.A_ServiceHostname);
			} else {	
				this._serverChoices.setChoices(this._serverList.getArray());
				this._serverChoices.dirtyChoices();
			}

			this._serverMap = new Object();
			var cnt = this._serverList.getArray().length;
			for (var i = 0; i < cnt; i ++) {
				this._serverMap[this._serverList.getArray()[i].id] = this._serverList.getArray()[i];
			}						
		} 
	}
}

/**
* @param ev
* This listener is invoked by any controller that can remove an ZaServer object
**/
ZaApp.prototype.handleServerRemoval = 
function (ev) {
	if(ev) {
		if(!this._serverList) {
			this._serverList=ZaServer.getAll(this);
		} else {
			//remove the ZaCos from the controlled list
			var detls = ev.getDetails();
			if(detls && (detls instanceof Array)) {
				for (var key in detls) {
					this._serverList.remove(detls[key]);
				}
			} else if(detls && (detls instanceof ZaServer)) {
				this._serverList.remove(ev.getDetails());
			}
		}
		if(this._serverChoices == null) {
			this._serverChoices = new XFormChoices(this._serverList.getArray(), XFormChoices.OBJECT_LIST, ZaServer.A_ServiceHostname, ZaServer.A_ServiceHostname);
		} else {	
			this._serverChoices.setChoices(this._serverList.getArray());
			this._serverChoices.dirtyChoices();
		}		
		
		this._serverMap = new Object();
		var cnt = this._serverList.getArray().length;
		for (var i = 0; i < cnt; i ++) {
			this._serverMap[this._serverList.getArray()[i].id] = this._serverList.getArray()[i];
		}		
	}
}
/**
* @param ev
* This listener is invoked by ZaDomainController or any other controller that can remove an ZaDomain object
**/
ZaApp.prototype.handleDomainRemoval = 
function (ev) {
	if(ev) {
		this.searchDomains();
	}
}

/**
* @param ev
* This listener is invoked by ZaDomainController or any other controller that can remove an ZaDomain object
**/
ZaApp.prototype.handleDomainChange = 
function (ev) {
	if(ev) {
		this.searchDomains();
	}
}

ZaApp.prototype.handleSettingsChange = 
function(ev) {
	if(ev) {
		this._globalConfig = new ZaGlobalConfig(this);
	}
}

/**
* Returns the app's name.
*/
ZaApp.prototype.getName =
function() {
	return this._name;
}

/**
* Returns the app view manager.
*/
ZaApp.prototype.getAppViewMgr = 
function() {
	return this._appViewMgr;
}

ZaApp.prototype.createView =
function(viewName, elements, tabParams) {
	this._appViewMgr.createView(viewName, elements);
	
	//create new tabs or modify tab
	/* tabParams {
	 * 	openInNewTab: true/false,
	 *  tabId: The tabId which will be either set for new Tab or the updating tab
	 *  tab: the tab to be updated
	 *  view: 
	 * }
	 */
	
	if (tabParams.openInNewTab) {
		this.createTab (tabParams.tabId);
	}else{
		this.updateTab (tabParams.tab, tabParams.tabId);
	}
	
}

ZaApp.prototype.createTab =
function (tabId) {
	var tabGroup = this.getTabGroup() ;
	var appView = this.getViewById(tabId) [ZaAppViewMgr.C_APP_CONTENT] ;
	var params = {
		id: tabId ,
		icon: appView.getTabIcon (),
		label: appView.getTabTitle () ,
		toolTip: appView.getTabToolTip () || appView.getTabTitle () ,
		closable: true ,
		selected: true
	}
	
	var tab = new ZaAppTab (tabGroup, this, params );
	/*
				entry.name, entry.getTabIcon() , null, null, 
				true, true, this._app._currentViewId) ;
	tab.setToolTipContent( entry.getTabToolTip()) ; */
}

/**
 * tab: the tab to be updated
 * tabId: the new id associated with the tab
 */
ZaApp.prototype.updateTab =
function ( tab, tabId ) {
	
	var tabGroup = this.getTabGroup() ;
	if (tabGroup._searchTab && tabGroup._searchTab == tab) {
		this.updateSearchTab() ;
	}else{	
		var appView = this.getViewById(tabId)[ZaAppViewMgr.C_APP_CONTENT];
		var icon = appView.getTabIcon (); //the view class should implement the getTabIcon () function
		var titleLabel = appView.getTabTitle () ; //the view class should implement the getTabTitle () function
	
		tab.setToolTipContent (appView.getTabToolTip() || appView.getTabTitle ()) ;
		tab.resetLabel (titleLabel) ;
		tab.setImage (icon) ;
	}
	
	tab.setTabId (tabId) ; //set the new tabId to the existing tab
	
	if (! tab.isSelected()) {
		tabGroup.selectTab(tab);
	}
}

ZaApp.prototype.updateSearchTab =
function () {
	var searchTab = this.getTabGroup().getSearchTab() ;
	searchTab.setImage (ZaSearchListView.prototype.getTabIcon()) ;
	searchTab.resetLabel (ZaSearchListView.prototype.getTabTitle()) ;
	searchTab.setToolTipContent (
		ZaSearchListView.prototype.getTabToolTip.call(this._controllers[searchTab.getTabId()])) ;
}

ZaApp.prototype.pushView =
function(name, openInNewTab, openInSearchTab) {
	this._currentViewId = this._appViewMgr.pushView(name);
	//may need to select the corresponding tab, but will cause deadlock
	/* 
	var tabGroup = this.getTabGroup () ;
	tabGroup.selectTab (tabGroup.getTabById(this._currentViewId)) ;
	*/
	//check if there is a tab associated with the view
	var tabGroup = this.getTabGroup () ;
	var cTab = tabGroup.getTabById(this._currentViewId)
	if (cTab) {
		this.updateTab (cTab, this._currentViewId) ;
	}else if (openInNewTab) {
		this.createTab (this._currentViewId) ;
	}else if (openInSearchTab) {
		this.updateTab (tabGroup.getSearchTab(), this._currentViewId) ; 
	}else {
		this.updateTab (tabGroup.getMainTab(), this._currentViewId) ; 
	}
}

ZaApp.prototype.popView =
function() {
	var oldCurrentViewId = this._currentViewId ;
	this._currentViewId = this._appViewMgr.popView();
	this.getTabGroup().removeCurrentTab(true) ;
	//dispose the view and remove the controller
	this.disposeView (oldCurrentViewId);
	
}

ZaApp.prototype.disposeView =
function (viewId) {
	
	var view = this.getViewById (viewId) ;
	for (var n in view) {
		if (view[n] instanceof DwtComposite) {
			view[n].dispose () ;
		}else{
			view[n] = null ;
		}
	} 
	
	//destroy the controller also
	if (this._controllers[viewId] != null) {
		this._controllers[viewId] = null ;
	} 
}

ZaApp.prototype.setView =
function(name, force) {
	return this._appViewMgr.setView(name, force);
}

ZaApp.prototype.getViewById =
function (id) {
	return	this.getAppViewMgr()._views[id] ;
}
// Abstract methods


/**
* Clears an app's state.
*/
ZaApp.prototype.reset =
function(active) {
}

ZaApp.prototype.setTabGroup =
function (tabGroup) {
	this._tabGroup = tabGroup ;	
}

ZaApp.prototype.getTabGroup =
function () {
	return this._tabGroup ;	
	
}
