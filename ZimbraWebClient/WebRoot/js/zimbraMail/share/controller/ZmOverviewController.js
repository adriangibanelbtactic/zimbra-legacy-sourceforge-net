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
 * Creates an overview controller.
 * @constructor
 * @class
 * This singleton class manages overviews, each of which has a unique ID.
 * An overview is a set of tree views. When the overview is created, various
 * characteristics of its tree views can be provided. Each type of tree view
 * has a corresponding tree controller (also a singleton), which is lazily
 * created.
 *
 * @author Conrad Damon
 * 
 * @param appCtxt	[ZmAppCtxt]		app context
 * @param container	[DwtControl]	top-level container
 */
ZmOverviewController = function(appCtxt, container) {

	ZmController.call(this, appCtxt, container);
	
	this._overview = {};
	this._accordion = {};
	this._controller = {};
	this._treeIds = {};
	this._treeIdHash = {};
};

// Controller for given org type
ZmOverviewController.CONTROLLER = {};

ZmOverviewController.DEFAULT_FOLDER_ID = ZmFolder.ID_INBOX;

ZmOverviewController.prototype = new ZmController;
ZmOverviewController.prototype.constructor = ZmOverviewController;

ZmOverviewController.prototype.toString = 
function() {
	return "ZmOverviewController";
};

/**
 * Creates a new accordion.
 *
 * @param accordionId	[constant]		overview ID
 * @param parent		[DwtControl]*	containing widget
 */
ZmOverviewController.prototype.createAccordion =
function(params) {
	var accordion = this._accordion[params.accordionId] = new DwtAccordion(this._shell);
	accordion.id = params.accordionId;
	accordion.setScrollStyle(Dwt.CLIP);
	
	return accordion;
};

/**
 * Returns the accordion with the given ID.
 *
 * @param accordionId		[constant]	accordion ID
 */
ZmOverviewController.prototype.getAccordion =
function(accordionId) {
	return this._accordion[accordionId];
};

/**
 * Creates a new overview with the given options.
 *
 * @param params	hash of params (see ZmOverview)
 */
ZmOverviewController.prototype.createOverview =
function(params) {
	params.parent = params.parent || this._shell;
	var overview = this._overview[params.overviewId] = new ZmOverview(params, this, this._appCtxt);

	return overview;
};

/**
 * Returns the overview with the given ID.
 *
 * @param overviewId		[constant]	overview ID
 */
ZmOverviewController.prototype.getOverview =
function(overviewId) {
	return this._overview[overviewId];
};

/**
 * Returns the given tree controller.
 *
 * @param treeId		[constant]		organizer type
 */
ZmOverviewController.prototype.getTreeController =
function(treeId) {
	if (!treeId) { return null; }
	if (!this._controller[treeId]) {
		var treeControllerCtor = eval(ZmOverviewController.CONTROLLER[treeId]);
		this._controller[treeId] = new treeControllerCtor(this._appCtxt);
	}
	return this._controller[treeId];
};

/**
 * Returns the tree data (ZmTree) for the given organizer type.
 *
 * @param treeId		[constant]		organizer type
 */
ZmOverviewController.prototype.getTreeData =
function(treeId) {
	return treeId ? this._appCtxt.getTree(treeId) : null;
};

/**
 * Returns the given tree view in the given overview.
 *
 * @param overviewId		[constant]	overview ID
 * @param treeId			[constant]	organizer type
 */
ZmOverviewController.prototype.getTreeView =
function(overviewId, treeId) {
	if (!overviewId || !treeId) { return null; }
	return this.getOverview(overviewId).getTreeView(treeId);
};
