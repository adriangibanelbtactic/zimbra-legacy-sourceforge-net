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
* @class ZaGlobalStatsController 
* @contructor ZaGlobalStatsController
* @param appCtxt
* @param container
* @param app
* @author Greg Solovyev
**/
ZaGlobalStatsController = function(appCtxt, container, app) {

	ZaController.call(this, appCtxt, container, app,"ZaGlobalStatsController");
	this._helpURL = location.pathname + "adminhelp/html/WebHelp/monitoring/checking_usage_statistics.htm";
	this.tabConstructor = ZaGlobalStatsView;		
}

ZaGlobalStatsController.prototype = new ZaController();
ZaGlobalStatsController.prototype.constructor = ZaGlobalStatsController;
ZaController.setViewMethods["ZaGlobalStatsController"] = [];
//ZaGlobalStatsController.STATUS_VIEW = "ZaGlobalStatsController.STATUS_VIEW";

ZaGlobalStatsController.prototype.show = 
function() {
	this._setView();
	this._app.pushView(this.getContentViewId());
	var item=new Object();
	try {		
		item[ZaModel.currentTab] = "1"
		this._contentView.setObject(item);
	} catch (ex) {
		this._handleException(ex, "ZaGlobalConfigViewController.prototype.show", null, false);
	}
	this._currentObject = item;	
}


ZaGlobalStatsController.setViewMethod =
function() {	
    if (!this._contentView) {
		this._contentView  = new this.tabConstructor(this._container, this._app);
		var elements = new Object();
		this._ops = new Array();
		this._ops = new Array();
		this._ops.push(new ZaOperation(ZaOperation.REFRESH, ZaMsg.TBB_Refresh, ZaMsg.TBB_Refresh_tt, "Refresh", "Refresh", new AjxListener(this, this.refreshListener)));
		this._ops.push(new ZaOperation(ZaOperation.NONE));
		this._ops.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));				
		this._toolbar = new ZaToolBar(this._container, this._ops);    		
		
		elements[ZaAppViewMgr.C_APP_CONTENT] = this._contentView;
		elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;		
		var tabParams = {
			openInNewTab: false,
			tabId: this.getContentViewId(),
			tab: this.getMainTab()
		}
		this._app.createView(this.getContentViewId(), elements, tabParams) ;
		this._UICreated = true;
		this._app._controllers[this.getContentViewId ()] = this ;		
	}
}
ZaController.setViewMethods["ZaGlobalStatsController"].push(ZaGlobalStatsController.setViewMethod);


ZaGlobalStatsController.prototype.refreshListener =
function (ev) {
	var currentTabView = this._contentView._tabs[this._contentView._currentTabKey]["view"];
	if (currentTabView && currentTabView.showMe) {
		currentTabView.showMe(2) ; //force server side cache to be refreshed.
	}
}