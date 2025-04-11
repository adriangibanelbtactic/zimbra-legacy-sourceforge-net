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

ZmRoster = function(appCtxt, imApp) {
	ZmModel.call(this, ZmEvent.S_ROSTER);

	this._appCtxt = appCtxt;
	this._newRosterItemtoastFormatter = new AjxMessageFormat(ZmMsg.imNewRosterItemToast);
	this._presenceToastFormatter = new AjxMessageFormat(ZmMsg.imStatusToast);
	this._leftChatFormatter = new AjxMessageFormat(ZmMsg.imLeftChat);
	this._imApp = imApp;

	this.reload();
};

ZmRoster.prototype = new ZmModel;
ZmRoster.prototype.constructor = ZmRoster;

ZmRoster.F_PRESENCE = "ZmRoster.presence";

ZmRoster.NOTIFICATION_FOO_TIMEOUT = 10000; // 10 sec.

ZmRoster.prototype.toString =
function() {
	return "ZmRoster";
};

ZmRoster.prototype.getChatList =
function() {
	if (!this._chatList)
		this._chatList = new ZmChatList(this._appCtxt, this);
	return this._chatList;
};

ZmRoster.prototype.getMyAddress =
function() {
    if (this._myAddress == null)
		this._myAddress = this._appCtxt.get(ZmSetting.USERNAME);
    return this._myAddress;
};

ZmRoster.prototype.getRosterItem =
function(addr) {
	return this.getRosterItemList().getByAddr(addr);
};

ZmRoster.prototype.getRosterItemList =
function() {
	if (!this._rosterItemList) {
		this._rosterItemList = new ZmRosterItemList(this._appCtxt);
	}
	return this._rosterItemList;
};

ZmRoster.prototype.getPresence =
function() {
	if (!this._rosterPresence) {
		this._rosterPresence = new ZmRosterPresence();
	}
	return this._rosterPresence;
};

ZmRoster.prototype._notifyPresence =
function() {
	var fields = {};
	fields[ZmRoster.F_PRESENCE] = this.getPresence();
	this._notify(ZmEvent.E_MODIFY, {fields: fields});
	this._imApp.getChatListController().updatePresenceMenu();
};

ZmRoster.prototype.reload =
function() {
	this.getRosterItemList().removeAllItems();
	var soapDoc = AjxSoapDoc.create("IMGetRosterRequest", "urn:zimbraIM");
	var respCallback = new AjxCallback(this, this._handleResponseReload);
	this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true, callback: respCallback});
};

ZmRoster.prototype._handleResponseReload =
function(args) {
	var resp = args.getResponse()
	if (!resp || !resp.IMGetRosterResponse) return;
	var roster = resp.IMGetRosterResponse;
	var list = this.getRosterItemList();
	if (roster.items && roster.items.item) {
		var items = roster.items.item;
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			if (item.subscription == "TO" || item.subscription == "BOTH") {
				var rp = new ZmRosterPresence();
				rp.setFromJS(item.presence);
				var rosterItem = new ZmRosterItem(item.addr, list, this._appCtxt, item.name, rp, item.groups);
				list.addItem(rosterItem);
			}
		}
	}
	if (roster.presence) {
		this.getPresence().setFromJS(roster.presence);
		this._notifyPresence();
	}
	this.__avoidNotifyTimeout = new Date().getTime();
};

/**
 * create item on server.
 */
ZmRoster.prototype.createRosterItem =
function(addr, name, groups) {
    var soapDoc = AjxSoapDoc.create("IMSubscribeRequest", "urn:zimbraIM");
    var method = soapDoc.getMethod();
	method.setAttribute("addr", addr);
	if (name) method.setAttribute("name", name);
	if (groups) method.setAttribute("groups", groups);
	method.setAttribute("op", "add");
	this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true});
};

/**
 * set presence on the server
 */
ZmRoster.prototype.setPresence =
function(show, priority, showStatus) {
	var soapDoc = AjxSoapDoc.create("IMSetPresenceRequest", "urn:zimbraIM");
	var presence = soapDoc.set("presence");
	presence.setAttribute("show", show);
	if (priority) presence.setAttribute("priority", priority);
	if (showStatus) soapDoc.set("status", showStatus, presence);
	this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true});
	this.__avoidNotifyTimeout = new Date().getTime();
};

/**
 * Pass in an array of SUBSCRIBED items
 */
ZmRoster.prototype.handleSubscribedRosterItems =
function(subscribed) {
	for (var i=0; i < subscribed.length; i++) {
		var sub = subscribed[i];
		if (sub.to) {
			var list = this.getRosterItemList();
			var item = list.getByAddr(sub.to);
			if (item) {
				if (sub.groups) item._notifySetGroups(sub.groups); // should optimize
				if (sub.name && sub.name != item.getName()) item._notifySetName(sub.name);
				// mod
			} else {
				// create
				var item = new ZmRosterItem(sub.to, list, this._appCtxt, sub.name, null, sub.groups);
				list.addItem(item);
				var toast = this._newRosterItemtoastFormatter.format([item.getDisplayName()]);
				this._appCtxt.setStatusMsg(toast, null, null, null, ZmStatusView.TRANSITION_SLIDE_LEFT);
			}
		}
	}
};

/**
 * handle async notifications. we might need to queue this with timed action and return
 * immediately, since this is happening as a result of a notify header in a response, and
 * we probably don't want to trigger more requests while handling a response.
 */
ZmRoster.prototype.handleNotification =
function(im) {
	if (im.n) {
		var notifications = !this.__avoidNotifyTimeout ||
			(new Date().getTime() - this.__avoidNotifyTimeout > ZmRoster.NOTIFICATION_FOO_TIMEOUT);
		var cl = this.getChatList();
		for (var curNot=0; curNot < im.n.length; curNot++) {
			var not = im.n[curNot];
			if (not.type == "roster") {
				this.getRosterItemList().removeAllItems();
				var list = this.getRosterItemList();
				if (not.n) {
					for (var rosterNum=0; rosterNum < not.n.length; rosterNum++) {
						var rosterItem = not.n[rosterNum];
						if (rosterItem.type == "subscribed") {
							var item = new ZmRosterItem(rosterItem.to, list, this._appCtxt, rosterItem.name, null, rosterItem.groups);
							list.addItem(item);
						}
					}
				}
				// ignore unsubscribed entries for now (TODO FIXME)
			} else if (not.type == "subscribe") {
				var view = ZmChatMultiWindowView.getInstance();
				// it should always be instantiated by this time, but whatever.
				if (view) {
					new ZmImSubscribeAuth(view.getActiveWM(), not.from).popup();
				}
			} else if (not.type == "subscribed") {
				var sub = not;
				if (sub.to) {
					var list = this.getRosterItemList();
					var item = list.getByAddr(sub.to);
					if (item) {
						if (sub.groups) item._notifySetGroups(sub.groups); // should optimize
						if (sub.name && sub.name != item.getName()) item._notifySetName(sub.name);
						// mod
					} else {
						// create
						var item = new ZmRosterItem(sub.to, list, this._appCtxt, sub.name, null, sub.groups);
						list.addItem(item);
						if (notifications) {
							var toast = this._newRosterItemtoastFormatter.format([item.getDisplayName()]);
							this._appCtxt.setStatusMsg(toast, null, null, null, ZmStatusView.TRANSITION_SLIDE_LEFT);
						}
					}
				} else if (sub.from) {
				    // toast, should we user if they want to add user if they aren't in buddy list?
				}
			} else if (not.type == "unsubscribed") {
				var unsub = not;
				if (unsub.to) {
					var list = this.getRosterItemList();
					var item = list.getByAddr(unsub.to);
					if (item) list.removeItem(item);
				}
			} else if (not.type == "presence") {
				var p = not;
				if (p.from == this.getMyAddress()) {
					if (this.getPresence().setFromJS(p))
						this._notifyPresence();
				}
				var ri = this.getRosterItemList().getByAddr(p.from);
				if (ri) {
					var old_pres = ri.getPresence().getShow();
					if (ri.getPresence().setFromJS(p)) {
						ri._notifyPresence();
						var toast = this._presenceToastFormatter.format([ri.getDisplayName(), ri.getPresence().getShowText()]);
						var is_status = old_pres == ri.getPresence().getShow();
						if (notifications && ( (!is_status && this._appCtxt.get(ZmSetting.IM_PREF_NOTIFY_PRESENCE)) ||
								       (is_status && this._appCtxt.get(ZmSetting.IM_PREF_NOTIFY_STATUS)) ) ) {
							this._appCtxt.setStatusMsg(toast, null, null, null, ZmStatusView.TRANSITION_SLIDE_LEFT);
							var chat = cl.getChatByRosterAddr(p.from);
							if (chat)
								chat.addMessage(ZmChatMessage.system(toast));
						}
					}
				}
			} else if (not.type == "message") {
				this._appCtxt.getApp(ZmApp.IM).prepareVisuals();
				var msg = not;
				var buddy = this.getRosterItem(msg.from);
				if (msg.body == null || msg.body.length == 0) {
					// typing notification
					if (buddy)
						buddy._notifyTyping(msg.typing);
				} else {
					// clear any previous typing notification, since it looks
					// like we don't receive this when a message gets in.
					if (buddy)
						buddy._notifyTyping(false);

					var chatMessage = new ZmChatMessage(msg, msg.from == this.getMyAddress());
					var chat = cl.getChatByThread(chatMessage.thread);
					if (chat == null) {
						if (!chatMessage.fromMe) {
							chat = cl.getChatByRosterAddr(chatMessage.from, true);
						} else {
							chat = cl.getChatByRosterAddr(chatMessage.to, false);
						}
						if (chat) chat.setThread(chatMessage.thread);
					}
					if (chat) {
						if (!this._imApp.isActive()) {
							this._appCtxt.setStatusIconVisible(ZmStatusView.ICON_IM, true);
							this.startFlashingIcon();
						}
						chat.addMessage(chatMessage);
					}
				}
			} else if (not.type == "leftchat") {
				this._appCtxt.getApp(ZmApp.IM).prepareVisuals(); // not sure we want this here but whatever
				var lc = not;
				var chat = this.getChatList().getChatByThread(lc.thread);
				if (chat) {
					chat.addMessage(ZmChatMessage.system(this._leftChatFormatter.format([lc.addr])));
					chat.setThread(null);
				}
			} else if (not.type == "otherLocation") {
				var gw = this.getGatewayByType(not.service);
				gw.setState(not.username, ZmImGateway.STATE.BOOTED_BY_OTHER_LOGIN);
			} else if (not.type == "gwStatus") {
				var gw = this.getGatewayByType(not.service);
				gw.setState(not.name || null, not.state);
				if (not.state == ZmImGateway.STATE.BAD_AUTH) {
					this._appCtxt.setStatusMsg(ZmMsg.errorNotAuthenticated, ZmStatusView.LEVEL_WARNING);
				}
			}
		}
	}

};

ZmRoster.prototype.startFlashingIcon = function() {
	this._imApp.startFlashingIcon();
};

ZmRoster.prototype.stopFlashingIcon = function() {
	this._imApp.stopFlashingIcon();
};

ZmRoster.prototype.sendSubscribeAuthorization = function(accept, add, addr) {
	var sd = AjxSoapDoc.create("IMAuthorizeSubscribeRequest", "urn:zimbraIM");
	var method = sd.getMethod();
	method.setAttribute("addr", addr);
	method.setAttribute("authorized", accept ? "true" : "false");
	method.setAttribute("add", add ? "true" : "false");
	this._appCtxt.getAppController().sendRequest({ soapDoc: sd, asyncMode: true });
};

ZmRoster.prototype.reconnectGateway = function(gw) {
	var sd = AjxSoapDoc.create("IMGatewayRegisterRequest", "urn:zimbraIM");
	var method = sd.getMethod();
	method.setAttribute("op", "reconnect");
	method.setAttribute("service", gw.type);
	this._appCtxt.getAppController().sendRequest({ soapDoc: sd, asyncMode: true });
	this.__avoidNotifyTimeout = new Date().getTime();
};

ZmRoster.prototype.unregisterGateway = function(service, screenName) {
	var sd = AjxSoapDoc.create("IMGatewayRegisterRequest", "urn:zimbraIM");
	var method = sd.getMethod();
	method.setAttribute("op", "unreg");
	method.setAttribute("service", service);
	this._appCtxt.getAppController().sendRequest({ soapDoc	 : sd,
						       asyncMode : true });
	this.__avoidNotifyTimeout = new Date().getTime();
};

ZmRoster.prototype.registerGateway = function(service, screenName, password) {
	var sd = AjxSoapDoc.create("IMGatewayRegisterRequest", "urn:zimbraIM");
	var method = sd.getMethod();
	method.setAttribute("op", "reg");
	method.setAttribute("service", service);
	method.setAttribute("name", screenName);
	method.setAttribute("password", password);
	this._appCtxt.getAppController().sendRequest({ soapDoc	 : sd,
						       asyncMode : true
						     });
	this.__avoidNotifyTimeout = new Date().getTime();
	// since it's not returned by a gwStatus notification, let's
	// set a nick here so the icon becomes "online" if a
	// corresponding gwStatus notification gets in.
	this.getGatewayByType(service).nick = screenName;
};

ZmRoster.prototype._requestGateways = function() {
	var sd = AjxSoapDoc.create("IMGatewayListRequest", "urn:zimbraIM");
	this._appCtxt.getAppController().sendRequest(
		{ soapDoc   : sd,
		  asyncMode : true,
		  callback  : new AjxCallback(this, this._handleRequestGateways)
		}
	);
};

// {"IMGatewayListResponse": {"service": [
// 				   {"type":"msn","domain":"msn.ibm"},
// 				   {"type":"aol","domain":"aol.ibm"},
// 				   {"registration": [
// 					    {"state":"online","name":"mihai_bazon2"}
// 				    ],
// 				    "type":"yahoo",
// 				    "domain":"yahoo.ibm"
// 				   }],
// 			   "_jsns":"urn:zimbraIM"}
// };

ZmRoster.prototype._handleRequestGateways = function(args) {
	var resp = args.getResponse();
	if (!resp || !resp.IMGatewayListResponse)
		return;
	var a = resp.IMGatewayListResponse.service;
	if (a && a.length) {
		var defaultGateway = { type   : "XMPP",
				       domain : "XMPP" };
		a.unshift(defaultGateway);
		var byService = {};
		var byDomain = {};
		for (var i = 0; i < a.length; ++i) {
			var gw = a[i] = new ZmImGateway(a[i]);
			byService[a[i].type.toLowerCase()] = gw;
			byDomain[a[i].domain.toLowerCase()] = gw;
		}
		this._gateways = { byService : byService,
				   byDomain  : byDomain,
				   array     : a
				 };
	}
};

ZmRoster.prototype.getGatewayByType = function(type) {
	return this._gateways.byService[type.toLowerCase()];
};

ZmRoster.prototype.getGatewayByDomain = function(domain) {
	return this._gateways.byDomain[domain.toLowerCase()];
};

ZmRoster.prototype.getGateways = function() {
	return this._gateways.array;
};

ZmRoster.prototype.makeServerAddress = function(addr, type) {
	if (type == null || /^xmpp$/i.test(type))
		return addr;
	return addr + "@" + this.getGatewayByType(type).domain;
};

ZmRoster.prototype.breakDownAddress = function(addr) {
	var re = /@(.*)$/;
	var m = re.exec(addr);
	if (m) {
		var gw = this.getGatewayByDomain(m[1]);
		if (gw) {
			return { type	 : gw.type,
				 addr	 : addr.substr(0, m.index),
				 gateway : gw
			       };
		}
	}
	return { type: "XMPP",
		 addr: addr };
};

ZmRoster.prototype.getGroups = function() {
	return AjxVector.fromArray(this.getRosterItemList().getGroupsArray());
};




//------------------------------------------
// for autocomplete
//------------------------------------------

ZmRosterTreeGroups = function(roster) {
	this._groups = roster.getGroups();
};

ZmRosterTreeGroups.prototype.constructor = ZmRosterTreeGroups;

/**
 * Returns a list of matching groups for a given string
 */
ZmRosterTreeGroups.prototype.autocompleteMatch = function(str, callback) {
	str = str.toLowerCase();
	var result = [];

	var a = this._groups;
	var sz = a.size();
	for (var i = 0; i < sz; i++) {
		var g = a.get(i);
		if (g.toLowerCase().indexOf(str) == 0)
			result.push({ data: g, text: g });
	}
	callback.run(result);
};

ZmRosterTreeGroups.prototype.isUniqueValue =
function(str) {
	return false;
};
