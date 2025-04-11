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
 * Portions created by Zimbra are Copyright (C) 2007 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

ZmPortalController = function(appCtxt, container, app) {
	if (arguments.length == 0) return;
	ZmListController.call(this, appCtxt, container, app);

    // TODO: Where does this really belong?
    ZmOperation.registerOp("PAUSE_TOGGLE", {textKey:"pause", image:"Pause", style: DwtButton.TOGGLE_STYLE});

    this._listeners[ZmOperation.REFRESH] = new AjxListener(this, this._refreshListener);
    this._listeners[ZmOperation.PAUSE_TOGGLE] = new AjxListener(this, this._pauseListener);
}
ZmPortalController.prototype = new ZmListController;
ZmPortalController.prototype.constructor = ZmPortalController;

ZmPortalController.prototype.toString = function() {
	return "ZmPortalController";
};

//
// Public methods
//

ZmPortalController.prototype.show = function() {
	ZmListController.prototype.show.call(this);
	this._setup(this._currentView);

	var elements = new Object();
	elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar[this._currentView];
	elements[ZmAppViewMgr.C_APP_CONTENT_FULL] = this._listView[this._currentView];
	this._setView(this._currentView, elements, true);
};

ZmPortalController.prototype.setPaused = function(paused) {
    var view = this._listView[this._currentView];
    var portletIds = view && view.getPortletIds();
    if (portletIds && portletIds.length > 0) {
        var portletMgr = this._appCtxt.getApp(ZmApp.PORTAL).getPortletMgr();
        for (var i = 0; i < portletIds.length; i++) {
            var portlet = portletMgr.getPortletById(portletIds[i]);
            portlet.setPaused(paused);
        }
    }
};

//
// Protected methods
//

ZmPortalController.prototype._defaultView = function() {
	return ZmController.PORTAL_VIEW;
};

ZmPortalController.prototype._getToolBarOps = function() {
	return [ ZmOperation.REFRESH /*, ZmOperation.PAUSE_TOGGLE*/ ];
};

ZmPortalController.prototype._createNewView = function(view) {
	return new ZmPortalView(this._container, this._appCtxt, this._dropTgt);
};

// listeners

ZmPortalController.prototype._refreshListener = function() {
    this._app.refreshPortlets();
};

ZmPortalController.prototype._pauseListener = function(event) {
    var toolbar = this._toolbar[this._currentView];

    // en/disable refresh button
    var button = toolbar && toolbar.getButton(ZmOperation.REFRESH);
    var paused = event.item.isToggled();
    if (button) {
        button.setEnabled(!paused);
    }

    // pause portlets appearing on portal page
    this.setPaused(paused);
};

ZmPortalController.prototype._resetOperations = function(parent, num) {
//    ZmListController.prototype._resetOperations.call(parent, num);
    parent.enable(this._getToolBarOps(), true);
};