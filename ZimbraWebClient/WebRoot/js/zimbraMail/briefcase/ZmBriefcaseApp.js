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

ZmBriefcaseApp = function(appCtxt, container, parentController) {

	ZmApp.call(this, ZmApp.BRIEFCASE, appCtxt, container, parentController);
}

// Organizer and item-related constants
ZmEvent.S_PAGE					= "PAGE";
ZmEvent.S_DOCUMENT				= "DOCUMENT";
ZmEvent.S_BRIEFCASE				= "BRIEFCASE";
ZmItem.PAGE						= ZmEvent.S_PAGE;
ZmItem.DOCUMENT					= ZmEvent.S_DOCUMENT;
ZmItem.BRIEFCASE					= ZmEvent.S_BRIEFCASE;
ZmOrganizer.BRIEFCASE			= ZmEvent.S_BRIEFCASE;

// App-related constants
ZmApp.BRIEFCASE					= "Briefcase";
ZmApp.CLASS[ZmApp.BRIEFCASE]		= "ZmBriefcaseApp";
ZmApp.SETTING[ZmApp.BRIEFCASE]	= ZmSetting.BRIEFCASE_ENABLED;
ZmApp.LOAD_SORT[ZmApp.BRIEFCASE]	= 60;
ZmApp.QS_ARG[ZmApp.BRIEFCASE]	= "documents";

ZmBriefcaseApp.prototype = new ZmApp;
ZmBriefcaseApp.prototype.constructor = ZmBriefcaseApp;

ZmBriefcaseApp.prototype.toString = 
function() {
	return "ZmBriefcaseApp";
}

// Constants

// Data

ZmBriefcaseApp.prototype._notebookCache;

// Construction

ZmBriefcaseApp.prototype._defineAPI =
function() {
	AjxDispatcher.setPackageLoadFunction("Briefcase", new AjxCallback(this, this._postLoad, ZmOrganizer.BRIEFCASE));
	AjxDispatcher.registerMethod("GetBriefcaseController", ["BriefcaseCore", "Briefcase"], new AjxCallback(this, this.getBriefcaseController));	
};

ZmBriefcaseApp.prototype._registerOperations =
function() {
	ZmOperation.registerOp("NEW_BRIEFCASEITEM", {textKey:"newBriefcase", image:"NewFolder"});
	ZmOperation.registerOp("NEW_FILE", {textKey:"uploadNewFile", tooltipKey:"newFile", image:"NewPage"});
	ZmOperation.registerOp("SHARE_BRIEFCASE", {textKey:"shareFolder", image:"Folder"}, ZmSetting.SHARING_ENABLED);
	ZmOperation.registerOp("MOUNT_BRIEFCASE", {textKey:"mountBriefcase", image:"Notebook"}, ZmSetting.SHARING_ENABLED);
	ZmOperation.registerOp("OPEN_FILE", {textKey:"openFile", tooltipKey:"openFileTooltip", image:"NewPage"});
	ZmOperation.registerOp("VIEW_FILE_AS_HTML", {textKey:"viewAsHtml", tooltipKey:"viewAsHtml", image:"HtmlDoc"});	
	ZmOperation.registerOp("SEND_FILE", {textKey:"send", tooltipKey:"sendPageTT", image:"Send"});	
};

ZmBriefcaseApp.prototype._registerItems =
function() {

		ZmItem.registerItem(ZmItem.BRIEFCASE,
						{app:			ZmApp.BRIEFCASE,
						 nameKey:		"document",
						 icon:			"GenericDoc",
						 soapCmd:		"ItemAction",
						 itemClass:		"ZmBriefcaseItem",
						 node:			"doc",
						 organizer:		ZmOrganizer.BRIEFCASE,
						 searchType:	"document",
						 resultsList:
						AjxCallback.simpleClosure(function(search) {
						AjxDispatcher.require("BriefcaseCore");
						return new ZmBriefcaseItemList(this._appCtxt, search, ZmItem.BRIEFCASE);
						}, this)
						});

};

ZmBriefcaseApp.prototype._registerOrganizers =
function() {
	
	
	ZmOrganizer.registerOrg(ZmOrganizer.BRIEFCASE,
							{app:				ZmApp.BRIEFCASE,
							 nameKey:			"folders",
							 defaultFolder:		ZmOrganizer.ID_BRIEFCASE,
							 soapCmd:			"FolderAction",
							 firstUserId:		256,
							 orgClass:			"ZmBriefcase",
							 orgPackage:		"BriefcaseCore",
							 treeController:	"ZmBriefcaseTreeController",
							 labelKey:			"folders",
							 itemsKey:			"folders",
							 treeType:			ZmOrganizer.FOLDER,
							 views:				["document"],
							 folderKey:			"briefcase",
							 mountKey:			"mountFolder",
							 createFunc:		"ZmOrganizer.create",
							 compareFunc:		"ZmBriefcase.sortCompare",
							 deferrable:		true
							});
};

ZmBriefcaseApp.prototype._setupSearchToolbar =
function() {
	//TODO:search for page alone
/*	ZmSearchToolBar.addMenuItem(ZmItem.PAGE,
								{msgKey:		"searchNotebooks",
								 tooltipKey:	"searchForPages",
								 icon:			"SearchNotes"
								});*/
};

ZmBriefcaseApp.prototype._registerApp =
function() {
	var newItemOps = {};
	newItemOps[ZmOperation.NEW_FILE] = "file";

	var newOrgOps = {};
	newOrgOps[ZmOperation.NEW_BRIEFCASEITEM] = "folder";

	var actionCodes = {};
	actionCodes[ZmKeyMap.NEW_FILE]		= ZmOperation.NEW_FILE;
	actionCodes[ZmKeyMap.NEW_BRIEFCASEITEM]	= ZmOperation.NEW_BRIEFCASEITEM;

	ZmApp.registerApp(ZmApp.BRIEFCASE,
							 {mainPkg:				"Briefcase",
							  nameKey:				"briefcase",
							  icon:					"Folder",
							  chooserTooltipKey:	"gotoBriefcase",
							  defaultSearch:		ZmItem.PAGE,
							  organizer:			ZmOrganizer.BRIEFCASE,
							  overviewTrees:		[ZmOrganizer.BRIEFCASE,ZmOrganizer.TAG],
							  showZimlets:			true,
							  searchTypes:			[ZmItem.PAGE, ZmItem.DOCUMENT],
							  newItemOps:			newItemOps,
							  newOrgOps:			newOrgOps,
							  actionCodes:			actionCodes,
							  gotoActionCode:		ZmKeyMap.GOTO_NOTEBOOK,
							  newActionCode:		ZmKeyMap.NEW_FILE,
							  chooserSort:			70,
							  defaultSort:			60
							  });
};

// App API

ZmBriefcaseApp.prototype.deleteNotify =
function(ids, force) {

	if (!force && this._deferNotifications("delete", ids)) { return; }
	
	var nextData = null;
	var idStr = ids.join(",")+",";
	var folderInUse = false;
	var briefcaseController = AjxDispatcher.run("GetBriefcaseController");
	var shownFolder = briefcaseController._object;
	var overviewController = this._appCtxt.getOverviewController();
	var treeController = overviewController.getTreeController(ZmOrganizer.BRIEFCASE);
	var treeView = treeController.getTreeView(this.getOverviewId());
	
	if(!treeView){
		return;
	}
	
	for (var i = 0; i < ids.length; i++) {
			var tmp = treeView.getNextData(ids[i]);
			//next node might also be in the delete list : parent deleted
			if(tmp && idStr.indexOf(tmp.id+",")<0){
				nextData = tmp;
			}
			if (shownFolder && shownFolder == ids[i]) {
				folderInUse = true;				
			}
	}	
				
	for (var i = 0; i < ids.length; i++) {
	
		briefcaseController.removeItem({id:ids[i]});
		this._appCtxt.cacheRemove(ids[i]);
	}
	
	if(nextData && folderInUse){
	briefcaseController.show(nextData.id);
	}else{
	//handled in delete callback : currently we dont get notification
	//for the op in remote folders, so handled differently
	//briefcaseController.show(shownFolder);
	}
		
	for (var i = 0; i < ids.length; i++) {
		var tmp1 = treeView.getTreeItemById(ids[i]);
		if(tmp1){
			tmp1.dispose();
		}
		ids[i] = null;			
	}
	
};

/**
 * Checks for the creation of a notebook or a mount point to one, or of a page
 * or document.
 * 
 * @param creates	[hash]		hash of create notifications
 */
ZmBriefcaseApp.prototype.createNotify =
function(creates, force) {

	if (!creates["folder"] && !creates["doc"] && !creates["link"]) { return; }
	
	if (!force && !this._noDefer && this._deferNotifications("create", creates)) { return; }

	var bcController = AjxDispatcher.run("GetBriefcaseController");

	for (var name in creates) {
		var list = creates[name];
		for (var i = 0; i < list.length; i++) {
			var create = list[i];
			if (this._appCtxt.cacheGet(create.id)) { continue; }	
			if (name == "folder") {				
				this._handleCreateFolder(create, ZmOrganizer.BRIEFCASE);		
			}else if (name == "link") {
				this._handleCreateLink(create, ZmOrganizer.BRIEFCASE);
			}else if (name == "doc") {				
				//DBG.println(AjxDebug.DBG1, "ZmBriefcaseApp: handling CREATE for node: " + name);
				// REVISIT: use app context item cache				
				//var doc = new ZmBriefcaseItem(this._appCtxt);
				//doc.set(create);
				//bcController.putItem(doc);
			}
		}
	}
	
	//bcController.refreshFolder();
};

ZmBriefcaseApp.prototype.modifyNotify =
function(modifies, force) {
	if (!modifies["doc"]) { return; }
	var list = modifies[name];
	//TODO: implement modified notification
	if (!force && !this._noDefer && this._deferNotifications("modify", modifies)) { return; }

	var briefcaseController = this.getBriefcaseController();

	for (var name in modifies) {
		var list = modifies[name];
		for (var i = 0; i < list.length; i++) {
			var mod = list[i];
			var id = mod.id;
			if (!id) { continue; }
	
			 if (name == "doc") {
				DBG.println(AjxDebug.DBG2, "ZmBriefcaseApp: handling modified notif for ID " + id + ", node type = " + name);
				// REVISIT: Use app context item cache
				var doc = briefcaseController.getItemById(id);
				if (!doc) {
					doc = new ZmBriefcaseItem(this._appCtxt);
					doc.set(mod);
				}
				else {
					doc.notifyModify(mod);
					doc.set(mod);
				}
				//briefcaseController.putItem(doc);
				mod._handled = true;
			}
		}
	}
};

ZmBriefcaseApp.prototype.handleOp =
function(op) {
	switch (op) {
		case ZmOperation.NEW_FILE: {
			var loadCallback = new AjxCallback(this, this._handleNewItem);
			AjxDispatcher.require(["BriefcaseCore", "Briefcase"], false, loadCallback, null, true);
			break;
		}
		case ZmOperation.NEW_BRIEFCASEITEM: {
			var loadCallback = new AjxCallback(this, this._handleLoadNewBriefcaseItem);
			AjxDispatcher.require(["BriefcaseCore", "Briefcase"], false, loadCallback, null, true);
			break;
		}
	}
};

ZmBriefcaseApp.prototype._handleNewItem =
function() {
	var briefcaseController = this.getBriefcaseController();
	var callback  =new AjxCallback(this,this._handleUploadNewItem);
	briefcaseController.__popupUploadDialog(callback,ZmMsg.uploadFileToBriefcase);	
};

ZmBriefcaseApp.prototype._handleUploadNewItem = 
function(folder,filenames) {
	var briefcaseController = this.getBriefcaseController();
	briefcaseController.removeCachedFolderItems();
	briefcaseController.refreshFolder();
};

ZmBriefcaseApp.prototype._handleLoadNewBriefcaseItem =
function() {
	this._appCtxt.getAppViewMgr().popView(true, ZmController.LOADING_VIEW);	// pop "Loading..." page
	var dialog = this._appCtxt.getNewBriefcaseDialog();
	if (!this._newNotebookCb) {
		this._newNotebookCb = new AjxCallback(this, this._newBriefcaseCallback);
	}
	ZmController.showDialog(dialog, this._newNotebookCb);
};

// Public methods

ZmBriefcaseApp.prototype.launch =
function(callback) {
	var loadCallback = new AjxCallback(this, this._handleLoadLaunch, [callback]);
	AjxDispatcher.require(["BriefcaseCore","Briefcase"], true, loadCallback, null, true);
};

ZmBriefcaseApp.prototype._handleLoadLaunch =
function(callback) {
	var briefcaseController = this.getBriefcaseController();
	briefcaseController.show(null,true);

	if (callback) {
		callback.run();
	}
};

ZmBriefcaseApp.prototype.showSearchResults =
function(results, callback) {
	var loadCallback = new AjxCallback(this, this._handleLoadShowSearchResults, [results, callback]);
	AjxDispatcher.require(["NotebookCore", "Notebook"], false, loadCallback, null, true);
};

ZmBriefcaseApp.prototype._handleLoadShowSearchResults =
function(results, callback) {

//	this.getFileController().show(results, true);
	if (callback) {
		callback.run();
	}
};

ZmBriefcaseApp.prototype.setActive =
function(active) {
	/***
	if (active) {
		var briefcaseController = AjxDispatcher.run("GetBriefcaseController");
		briefcaseController.show();
	}
	/***/
};

ZmBriefcaseApp.prototype.getFileController = function() {
	if (!this._fileController) {
		//TODO:search controller for briefcase
	//	this._fileController = new ZmBriefcaseFileController(this._appCtxt, this._container, this);
	}
	return this._fileController;
};


ZmBriefcaseApp.prototype._newBriefcaseCallback =
function(parent, name, color) {
	var dialog = this._appCtxt.getNewBriefcaseDialog();
	dialog.popdown();
	var oc = this._appCtxt.getOverviewController();
	oc.getTreeController(ZmOrganizer.BRIEFCASE)._doCreate(parent, name, color);
};

ZmBriefcaseApp.prototype.getBriefcaseController = function() {
	if (!this._briefcaseController) {
		this._briefcaseController = new ZmBriefcaseController(this._appCtxt, this._container, this);		
	}
	return this._briefcaseController;
};

