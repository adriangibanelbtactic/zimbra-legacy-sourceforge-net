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
 * Creates a request manager.
 * @constructor
 * @class
 * This class manages the sending of requests to the server, and handles the
 * responses, including refresh blocks and notifications.
 *
 * @author Conrad Damon
 * 
 * @param appCtxt		[ZmAppCtxt]		the app context
 * @param controller	[ZmController]	main controller
 * @param domain		[string]		current domain
 */
ZmRequestMgr = function(appCtxt, controller, domain) {

	this._appCtxt = appCtxt;
	this._controller = controller;
	
	this._appCtxt.setRequestMgr(this);

	ZmCsfeCommand.setServerUri(location.protocol + "//" + domain + appCtxt.get(ZmSetting.CSFE_SERVER_URI));
	var cv = appCtxt.get(ZmSetting.CLIENT_VERSION);
	ZmCsfeCommand.clientVersion = (cv.indexOf('@') == 0) ? "dev build" : cv;
	
	this._shell = appCtxt.getShell();

    this._highestNotifySeen = 0;

	this._cancelActionId = {};
	this._pendingRequests = {};
	this._modifyHandled = {};

	this._useXml = this._appCtxt.get(ZmSetting.USE_XML);
	this._logRequest = this._appCtxt.get(ZmSetting.LOG_REQUEST);
	this._stdTimeout = this._appCtxt.get(ZmSetting.TIMEOUT);

	this._unreadListener = new AjxListener(this, this._unreadChangeListener);
};

// request states
ZmRequestMgr._SENT		= 1;
ZmRequestMgr._RESPONSE	= 2;
ZmRequestMgr._CANCEL	= 3;

ZmRequestMgr._nextReqId = 1;

ZmRequestMgr.getNextReqId =
function() {
	return "Req_" + ZmRequestMgr._nextReqId++;
};

ZmRequestMgr.prototype.toString =
function() {
	return "ZmRequestMgr";
};

/**
* Sends a request to the CSFE and processes the response. Notifications and
* refresh blocks that come in the response header are handled. Also handles
* exceptions by default, though the caller can pass in a special callback to
* run for exceptions. The error callback should return true if it has
* handled the exception, and false if standard exception handling should still
* be performed.
*
* @param soapDoc			[AjxSoapDoc]	SOAP document that represents the request
* @param asyncMode			[boolean]*		if true, request will be made asynchronously
* @param callback			[AjxCallback]*	next callback in chain for async request
* @param errorCallback		[AjxCallback]*	callback to run if there is an exception
* @param execFrame			[AjxCallback]*	the calling method, object, and args
* @param timeout			[int]*			timeout value (in seconds)
* @param noBusyOverlay		[boolean]*		if true, don't use the busy overlay
* @param accountName		[string]*		name of account to execute on behalf of
*/
ZmRequestMgr.prototype.sendRequest =
function(params) {
	var reqId = params.reqId = ZmRequestMgr.getNextReqId();
	var timeout = (params.timeout != null) ? params.timeout : this._stdTimeout;
	if (timeout) timeout = timeout * 1000; // convert seconds to ms
	var asyncCallback = params.asyncMode ? new AjxCallback(this, this._handleResponseSendRequest, [params]) : null;
	var command = new ZmCsfeCommand();
	// bug fix #10652 - dont set change token if accountName is specified
	// (since we're executing on someone else's mbox)
	var accountName = params.accountName || this._appCtxt.getActiveAccountName();
	var changeToken = accountName ? null : this._changeToken;
	var cmdParams = {soapDoc:params.soapDoc, accountName:accountName, useXml:this._useXml,
					 changeToken:changeToken, asyncMode:params.asyncMode, callback:asyncCallback,
					 logRequest:this._logRequest, highestNotifySeen:this._highestNotifySeen};

	DBG.println(AjxDebug.DBG2, "sendRequest(" + reqId + "): " + params.soapDoc._methodEl.nodeName);
	var cancelParams = timeout ? [reqId, params.errorCallback, params.noBusyOverlay] : null;
	if (!params.noBusyOverlay) {
		var cancelCallback = null;
		var showBusyDialog = false;
		if (timeout) {
			DBG.println(AjxDebug.DBG1, "ZmRequestMgr.sendRequest: timeout for " + reqId + " is " + timeout);
			cancelCallback = new AjxCallback(this, this.cancelRequest, cancelParams);
			this._shell.setBusyDialogText(ZmMsg.askCancel);
			showBusyDialog = true;
		}
		// put up busy overlay to block user input
		this._shell.setBusy(true, reqId, showBusyDialog, timeout, cancelCallback);
	} else if (timeout) {
		var action = new AjxTimedAction(this, this.cancelRequest, cancelParams);
		this._cancelActionId[reqId] = AjxTimedAction.scheduleAction(action, timeout);
	}

	this._pendingRequests[reqId] = command;

	try {
		var response = command.invoke(cmdParams);
		command.state = ZmRequestMgr._SENT;
	} catch (ex) {
		this._handleResponseSendRequest(params, new ZmCsfeResult(ex, true));
		return;
	}
	if (params.asyncMode) {
		return reqId;
	} else {
		return this._handleResponseSendRequest(params, response);
	}
};

ZmRequestMgr.prototype._handleResponseSendRequest =
function(params, result) {
	if (!this._pendingRequests[params.reqId]) {
		DBG.println(AjxDebug.DBG2, "ZmRequestMgr.handleResponseSendRequest no pendingRequest entry for " + params.reqId);
		return;
	}
	if (this._pendingRequests[params.reqId].state == ZmRequestMgr._CANCEL) {
		DBG.println(AjxDebug.DBG2, "ZmRequestMgr.handleResponseSendRequest state=CANCEL for " + params.reqId);
		return;
	}

	this._pendingRequests[params.reqId].state = ZmRequestMgr._RESPONSE;

	if (!params.noBusyOverlay) {
		this._shell.setBusy(false, params.reqId); // remove busy overlay
	} else if (params.timeout) {
		AjxTimedAction.cancelAction(this._cancelActionId[params.reqId]);
		this._cancelActionId[params.reqId] = -1;
	}

	var response;
	try {
		if (params.asyncMode) {
			response = result.getResponse(); // may throw exception
		} else {
			// for sync responses, manually throw exception if necessary
			if (result._isException)
				throw result._data;
			else
				response = result;
		}
		this._handleHeader(response.Header);
	} catch (ex) {
		DBG.println(AjxDebug.DBG2, "Request " + params.reqId + " got an exception");
		if (params.errorCallback) {
			var handled = params.errorCallback.run(ex);
			if (!handled) {
				this._controller._handleException(ex, params.execFrame);
			}
		} else {
			this._controller._handleException(ex, params.execFrame);
		}
		var hdr = result.getHeader();
		this._handleHeader(hdr);
		this._handleNotifications(hdr);
		return;
	}

	if (params.asyncMode) {
		result.set(response.Body);
	}

    // if we didn't get an exception, then we should make sure that the
    // poll timer is running (just in case it got an exception and stopped)
    this._controller._kickPolling(true);

	this._clearPendingRequest(params.reqId);

	if (params.asyncMode && params.callback) {
		params.callback.run(result);
	}

	this._handleNotifications(response.Header);

	if (!params.asyncMode) {
		return response.Body;
	}
};

ZmRequestMgr.prototype.cancelRequest =
function(reqId, errorCallback, noBusyOverlay) {
	if (!this._pendingRequests[reqId]) return;
	if (this._pendingRequests[reqId].state == ZmRequestMgr._RESPONSE) return;

	this._pendingRequests[reqId].state = ZmRequestMgr._CANCEL;
	if (!noBusyOverlay) {
		this._shell.setBusy(false, reqId);
	}
	DBG.println(AjxDebug.DBG1, "ZmRequestMgr.cancelRequest: " + reqId);
	this._pendingRequests[reqId].cancel();
	if (errorCallback) {
		var ex = new AjxException("Request canceled", AjxException.CANCELED, "ZmRequestMgr.prototype.cancelRequest");
		errorCallback.run(ex);
	}
	this._clearPendingRequest(reqId);
};

ZmRequestMgr.prototype._clearPendingRequest =
function(reqId) {
	if (this._pendingRequests[reqId]) {
		delete this._pendingRequests[reqId];
	}
};

/**
 * Handles a response's SOAP header, except for notifications. Updates our
 * change token, and processes a <refresh> block if there is one (happens
 * when a new session is created on the server).
 *
 * @param hdr	[object]	a SOAP header
 */
ZmRequestMgr.prototype._handleHeader =
function(hdr) {
	if (!hdr) return;

	// update change token if we got one
	if (hdr && hdr.context && hdr.context.change) {
		this._changeToken = hdr.context.change.token;
	}

	// refresh block causes the overview panel to get updated
	if (hdr && hdr.context && hdr.context.refresh) {
		this._highestNotifySeen = 0;
		var callback = new AjxCallback(this, this._handleHeaderResponse);
		this._refreshHandler(hdr.context.refresh, callback);
	}
};

ZmRequestMgr.prototype._handleHeaderResponse =
function(ev) {
	this._controller._checkOverviewLayout();
};

/**
 * Handles the <notify> block of a response's SOAP header.
 *
 * @param hdr	[object]	a SOAP header
 */
ZmRequestMgr.prototype._handleNotifications =
function(hdr) {
	if (hdr && hdr.context && hdr.context.notify) {
        for(i = 0; i < hdr.context.notify.length; i++) {
        	var notify = hdr.context.notify[i];
        	var seq = notify.seq;
            // BUG?  What if the array isn't in sequence-order?  Could we miss notifications?
            if (notify.seq > this._highestNotifySeen) {
                DBG.println(AjxDebug.DBG1, "Handling notification[" + i + "] seq=" + seq);
                this._highestNotifySeen = seq;
                this._notifyHandler(notify);
            } else {
            	DBG.println(AjxDebug.DBG1, "SKIPPING notification[" + i + "] seq=" + seq + " highestNotifySeen=" + this._highestNotifySeen);
	      	}
    	}
	}
};

// A <refresh> block is returned in a SOAP response any time the session ID has 
// changed. It always happens on the first SOAP command (GetInfoRequest).
// After that, it happens after a session timeout.
ZmRequestMgr.prototype._refreshHandler =
function(refresh, callback) {
	DBG.println(AjxDebug.DBG1, "Handling REFRESH");
	this._controller.runAppFunction("_clearDeferredFolders");

	var unread = {};
	this._loadTree(ZmOrganizer.TAG, unread, refresh.tags);
	this._loadTree(ZmOrganizer.FOLDER, unread, refresh.folder[0], "folder");
	this._controller._needOverviewLayout = true;
	
	var inbox = this._appCtxt.getById(ZmFolder.ID_INBOX);
	if (inbox) {
		this._controller._statusView.setIconVisible(ZmStatusView.ICON_INBOX, inbox.numUnread > 0);
	}

	// XXX: temp, get additional share info (see bug #4434)
	if (refresh.folder) {
		var respCallback = new AjxCallback(this, this._handleRefreshHandler, [refresh, callback]);
		this._appCtxt.getFolderTree().getPermissions(null, respCallback, true);
	} else {
		// Run any app-requested refresh routines
		this._controller.runAppFunction("refresh", refresh);
	}
};

ZmRequestMgr.prototype._handleRefreshHandler =
function(refresh, callback) {
	// Run any app-requested refresh routines
	this._controller.runAppFunction("refresh", refresh);

	if (callback) callback.run();
};

ZmRequestMgr.prototype._loadTree =
function(type, unread, obj, objType) {
	var isTag = (type == ZmOrganizer.TAG);
	var tree = isTag ? this._appCtxt.getTagTree() : this._appCtxt.getFolderTree();
	if (!tree) {
		tree = isTag ? new ZmTagTree(this._appCtxt) : new ZmFolderTree(this._appCtxt);
	}
	isTag ? this._appCtxt.setTagTree(tree) : this._appCtxt.setFolderTree(tree);
	tree.addChangeListener(this._unreadListener);
	tree.getUnreadHash(unread);
	tree.reset();
	if (isTag) {
		tree.createRoot(); // tag tree root not in the DOM
	}
	tree.loadFromJs(obj, objType);
};

// To handle notifications, we keep track of all the models in use. A model could
// be an item, a list of items, or an organizer tree. Currently we never get an
// organizer by itself.
ZmRequestMgr.prototype._notifyHandler =
function(notify) {
	DBG.println(AjxDebug.DBG2, "Handling NOTIFY");
	this._controller.runAppFunction("preNotify", notify);
	try {
		if (notify.deleted && notify.deleted.id) {
			this._handleDeletes(notify.deleted);
		}
		if (notify.created) {
			this._handleCreates(notify.created);
		}
		if (notify.modified) {
			this._handleModifies(notify.modified);
		}
		this._controller.runAppFunction("postNotify", notify);
	} catch (ex) {
		this._controller._handleException(ex, this._notifyHandler, notify, false);
	}
};

/**
 * A delete notification hands us a list of IDs which could be anything. First, we
 * run any app delete handlers. Any IDs which have been handled by an app will
 * be nulled out. The generic handling here will be applied to the rest - the item is
 * retrieved from the item cache and told it has been deleted.
 *
 * @param deletes	[object]	node containing all 'deleted' notifications
 */
ZmRequestMgr.prototype._handleDeletes =
function(deletes) {
	var ids = deletes.id.split(",");
	this._controller.runAppFunction("deleteNotify", ids);

	for (var i = 0; i < ids.length; i++) {
		var id = ids[i];
		if (!id) { continue; }
		var item = this._appCtxt.cacheGet(id);
		DBG.println(AjxDebug.DBG2, "ZmRequestMgr: handling delete notif for ID " + id);
		if (item) {
			item.notifyDelete();
			this._appCtxt.cacheRemove(id);
			item = null;
		}
	}
};

/**
 * Create notifications hand us full XML nodes. First, we run any app
 * create handlers, which will mark any create nodes that they handle. Remaining
 * creates are handled here.
 * 
 * @param creates	[object]	node containing all 'created' notifications
 */
ZmRequestMgr.prototype._handleCreates =
function(creates) {
	this._controller.runAppFunction("createNotify", creates);

	for (var name in creates) {
		var list = creates[name];
		for (var i = 0; i < list.length; i++) {
			var create = list[i];
			if (create._handled) { continue; }
			// ignore create notif for item we already have (except tags, which can reuse IDs)
			if (this._appCtxt.cacheGet(create.id) && name != "tag") { continue; }
	
			DBG.println(AjxDebug.DBG1, "ZmRequestMgr: handling CREATE for node: " + name);
			if (name == "tag") {
				this._appCtxt.getTagTree().root.notifyCreate(create);
			} else if (name == "folder" || name == "search") {
				var parentId = create.l;
				var parent = this._appCtxt.getById(parentId);
				if (parent) {
					parent.notifyCreate(create, (name == "search"));
				}
			}
		}
	}
};

/**
 * First, we run any app modify handlers, which will mark any nodes that
 * they handle. Remaining modify notifications are handled here.
 * 
 * @param modifies	[object]	node containing all 'modified' notifications
 */
ZmRequestMgr.prototype._handleModifies =
function(modifies) {

	// for tracking what has been handled across multiple change listeners;
	// clients can set this directly with the ID of the item as the key;
	// clear it out before handling a set of notifications.
	this._modifyHandled = {};
	
	this._controller.runAppFunction("modifyNotify", modifies);

	for (var name in modifies) {
		var list = modifies[name];
		for (var i = 0; i < list.length; i++) {
			var mod = list[i];
			if (mod._handled || this._modifyHandled[mod.id]) { continue; }
			DBG.println(AjxDebug.DBG2, "ZmRequestMgr: handling modified notif for ID " + mod.id + ", node type = " + name);
			if (name == "mbx") {
				var setting = this._controller._settings.getSetting(ZmSetting.QUOTA_USED);
				setting.notifyModify(mod);
				continue;
			} else {
				var item = this._appCtxt.cacheGet(mod.id);
				if (item) {
					item.notifyModify(mod);
				}
			}
		}
	}
};

/**
 * Returns a list of objects that have the given parent, flattening child
 * arrays in the process. It also saves each child's name into it.
 *
 * @param parent	[object]	notification subnode
 *
 * TODO: remove this func (still used by ZmMailApp::_adjustNotifies)
 */
ZmRequestMgr._getObjList =
function(parent) {
	var list = [];
	for (var name in parent) {
		var obj = parent[name];
		if (obj instanceof Array) {
			for (var i = 0; i < obj.length; i++) {
				obj[i]._name = name;
				list.push(obj[i]);
			}
		} else {
			obj._name = name;
			list.push(obj);
		}
	}
	return list;
};

/*
* Changes the browser title if it's a folder or tag whose unread
* count just changed.
*/
ZmRequestMgr.prototype._unreadChangeListener =
function(ev) {
	if (ev.event == ZmEvent.E_MODIFY) {
		var fields = ev.getDetail("fields");
		var organizers = ev.getDetail("organizers");
		var organizer = organizers ? organizers[0] : null;
		var id = organizer ? organizer.id : null;
		if (fields && fields[ZmOrganizer.F_UNREAD]) {
			var search = this._appCtxt.getCurrentSearch();
			if (search && id && (id == search.folderId || id == search.tagId))
				Dwt.setTitle(search.getTitle());
			if (id == ZmFolder.ID_INBOX) {
				this._controller._statusView.setIconVisible(ZmStatusView.ICON_INBOX,  organizer.numUnread > 0);
			}
		}		
	}
};
