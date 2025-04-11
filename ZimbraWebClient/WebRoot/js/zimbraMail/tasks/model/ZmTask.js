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
*
* @constructor
* @class
*
* @author Parag Shah
*
* @param id			[int]			numeric ID
* @param name		[string]		name
*/
ZmTask = function(appCtxt, list, id, folderId) {
	ZmCalItem.call(this, appCtxt, ZmItem.TASK, list, id, folderId);

	this.priority = ZmCalItem.PRIORITY_NORMAL;
	this.pComplete = 0;
	this.status = ZmCalItem.STATUS_NEED;
};

ZmTask.prototype = new ZmCalItem;
ZmTask.prototype.constructor = ZmTask;


// Consts
ZmTask.PCOMPLETE_INT = 10;

/**
* Used to make our own copy because the form will modify the date object by
* calling its setters instead of replacing it with a new date object.
*/
ZmTaskClone = function() { };
ZmTask.quickClone =
function(task) {
	ZmTaskClone.prototype = task;

	var newTask = new ZmTaskClone();
	newTask.startDate = task.startDate ? (new Date(task.startDate.getTime())) : null;
	newTask.endDate = task.endDate ? (new Date(task.endDate.getTime())) : null;

	if (!newTask._orig)
		newTask._orig = task;

	newTask.type = ZmItem.TASK;

	return newTask;
};

// XXX: set start/end intervals for instNode
ZmTask.createFromDom =
function(taskNode, args, instNode) {
	// NOTE: passing ID implies this item should get cached!
	var task = new ZmTask(args.appCtxt, args.list, taskNode.id);
	task._loadFromDom(taskNode, instNode);

	return task;
};


// Public Methods

ZmTask.prototype.toString =
function() {
	return "ZmTask";
};

// Getters
ZmTask.prototype.getIcon				= function() { return "Task"; };
ZmTask.prototype.getLocation			= function() { return this.location || ""; };
ZmTask.prototype.getFolder =
function() {
	return this._appCtxt.getById(this.folderId);
};

ZmCalItem.prototype.getSummary =
function(isHtml) {
	// TODO
};

/**
* Returns HTML for a tool tip for this appt.
*/
ZmTask.prototype.getToolTip =
function(controller) {
	// TODO
	DBG.println("------------ TODO: getTooltip! --------------");
};

ZmTask.prototype.getPrintHtml =
function(preferHtml, callback) {
	this.getDetails(ZmCalItem.MODE_EDIT, new AjxCallback(null, ZmTaskView.getPrintHtml, [this, preferHtml, callback]));
};

ZmTask.prototype.notifyModify =
function(obj) {
	ZmItem.prototype.notifyModify.call(this, obj);

	this._loadFromDom(obj);

	// update this tasks's list and notify
	this.list.modifyLocal(obj, {task:this});
	this._notify(ZmEvent.E_MODIFY, obj);
};

ZmTask.prototype.isPastDue =
function() {
	return (this.endDate && ((new Date()).getTime() > this.endDate.getTime()));
};

ZmTask.prototype.isComplete =
function() {
	return (this.pComplete == 100) || (this.status == ZmCalItem.STATUS_COMP);
};


// Private/protected methods

ZmTask.prototype._getDefaultFolderId =
function() {
	return ZmOrganizer.ID_TASKS;
};

ZmTask.prototype._loadFromDom =
function(node, instNode) {
	var inv = node.inv ? node.inv[0] : null
	var comp = inv ? inv.comp[0] : null;

	if (!node.id) this.id = node.id;
	if (!this.invId) this.invId = (node.invId) || (inv ? [node.id, inv.id].join("-") : null);
	if (!node.uid) this.uid = node.uid;				// XXX: what is this?

	if (node.l) this.folderId = node.l;
	if (node.s) this.size = node.s;					// XXX: do we care?
	if (node.sf) this.sf = node.sf;
	if (node.dueDate) {
		this.endDate = new Date(parseInt(node.dueDate,10));
	} else {
		var part = this._getPart(node, comp, "e");
		var ed = (part && part.length) ? part[0].d : null;
		this.endDate = ed ? AjxDateUtil.parseServerDateTime(ed) : null;
		if (this.endDate)
			this.endDate.setHours(0,0,0);
	}

	if (node.name || comp) this.name = this._getPart(node, comp, "name");
	if (node.loc || comp) this.location = this._getPart(node, comp, "loc");
	if (node.allDay || comp) this.setAllDayEvent(this._getPart(node, comp, "allDay"));
	if (node.priority || comp) this.priority = parseInt(this._getPart(node, comp, "priority"));
	if (node.percentComplete || comp) this.pComplete = parseInt(this._getPart(node, comp, "percentComplete"));
	if (node.status || comp) this.status = this._getPart(node, comp, "status");
	if (node.isOrg || comp) this.isOrg = this._getPart(node, comp, "isOrg");
	if (node.or || comp) this.organizer = node.or ? node.or.a : (comp.or ? comp.or.a : null);
	if (node.ptst || comp) this.ptst = this._getPart(node, comp, "ptst");
	if (node.compNum != null) this.compNum = (this._getPart(node, comp, "compNum") || "0");

	if (node.f)	this._parseFlags(node.f);
	if (node.t)	this._parseTags(node.t);
};

ZmTask.prototype._getPart =
function(node, comp, name) {
	if (node[name] != null) return node[name];
	if (comp) return comp[name];
	return null;
};

ZmTask.prototype._setExtrasFromMessage =
function(message) {
	this.location = message.invite.getLocation();
};

ZmTask.prototype._getSoapForMode =
function(mode, isException) {
	switch (mode) {
		case ZmCalItem.MODE_NEW:
			return "CreateTaskRequest";

		case ZmCalItem.MODE_EDIT_SINGLE_INSTANCE:
			return !isException
				? "CreateTaskExceptionRequest"
				: "ModifyTaskRequest";

		case ZmCalItem.MODE_EDIT:
		case ZmCalItem.MODE_EDIT_SERIES:
			return "ModifyTaskRequest";

		case ZmCalItem.MODE_DELETE:
		case ZmCalItem.MODE_DELETE_SERIES:
		case ZmCalItem.MODE_DELETE_INSTANCE:
			return "CancelTaskRequest";

		case ZmCalItem.MODE_GET:
			return "GetTaskRequest";
	}

	return null;
};

ZmTask.prototype._addExtrasToSoap =
function(soapDoc, inv, comp) {
	ZmCalItem.prototype._addExtrasToSoap.call(this, soapDoc, inv, comp);

	comp.setAttribute("percentComplete", this.pComplete);

	// TODO - set "completed" if applicable
};

ZmTask.prototype._addLocationToSoap =
function(inv) {
	inv.setAttribute("loc", this.location);
};

ZmTask.prototype._getInviteFromError =
function(result) {
	return (result._data.GetTaskResponse.task[0].inv[0]);
};
