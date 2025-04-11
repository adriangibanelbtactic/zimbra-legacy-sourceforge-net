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

ZmPortalApp = function(appCtxt, container, parentController) {
	ZmApp.call(this, ZmApp.PORTAL, appCtxt, container, parentController);
}

ZmPortalApp.prototype = new ZmApp;
ZmPortalApp.prototype.constructor = ZmPortalApp;

ZmPortalApp.prototype.toString = function() {
	return "ZmPortalApp";
};

// Construction

ZmPortalApp.prototype._registerApp = function() {
    ZmApp.registerApp(ZmApp.PORTAL, {
        nameKey: "portal",
        icon: "Globe",
        chooserTooltipKey:	"goToPortal",
        button: ZmAppChooser.B_PORTAL,
        chooserSort: 1,
        defaultSort: 1
    });
};

//
// Constants
//

ZmApp.PORTAL                    = "Portal";
ZmApp.CLASS[ZmApp.PORTAL]		= "ZmPortalApp";
ZmApp.SETTING[ZmApp.PORTAL]	= ZmSetting.PORTAL_ENABLED;
ZmApp.LOAD_SORT[ZmApp.PORTAL]	= 1;

ZmEvent.S_PORTLET   = "PORTLET";
ZmItem.PORTLET      = ZmEvent.S_PORTLET;

ZmPortalApp.__PORTLET_ID = 0;

//
// Public methods
//

ZmPortalApp.prototype.refreshPortlets = function() {
    var mgr = this.getPortletMgr();
    var portlets = mgr.getPortlets();
    for (var id in portlets) {
        portlets[id].refresh();
    }
};

ZmPortalApp.prototype.launch = function(callback) {
    var loadCallback = new AjxCallback(this, this._handleLoadLaunch, [callback]);
	AjxDispatcher.require("Portal", true, loadCallback, null, true);
};

ZmPortalApp.prototype._handleLoadLaunch = function(callback) {
	var controller = this.getPortalController();
	controller.show();
    ZmApp.prototype.launch.call(this, callback);
};

ZmPortalApp.prototype.activate = function(active) {
	var controller = this.getPortalController();
	controller.setPaused(!active);
	ZmApp.prototype.activate.call(this, active);
};

ZmPortalApp.prototype.getManifest = function(callback) {
    if (!this._manifest) {
        // load the portal manifest
        var portalName = this._appCtxt.get(ZmSetting.PORTAL_NAME);
        if (portalName) {
            var timestamp = new Date().getTime(); 
            var params = {
                url: [ window.appContextPath,"/portals/",portalName,"/manifest.xml?v=",timestamp ].join(""),
                callback: callback ? new AjxCallback(this, this._handleLoadManifest, [callback]) : null
            };
            var req = AjxLoader.load(params);
            if (!callback) {
                this._handleLoadManifest(callback, req);
            }
        }
    }
	else if (callback) {
		callback.run(this._manifest);
	}
	return this._manifest;
};

ZmPortalApp.prototype._handleLoadManifest = function(callback, req) {
    var e;
    if (req.status == 200 && req.responseXML) {
        try {
            // serialize manifest into JSON and evaluate
            var json = new AjxJsonSerializer(true).serialize(req.responseXML);
            eval("this._manifest = "+json);

            // further minimize the object structure
            var portalDef = this._manifest.portal ;
            var portletsDef = portalDef && portalDef.portlets;
            if (portletsDef && !(portletsDef.portlet instanceof Array)) {
                portletsDef.portlet = [ portletsDef.portlet ];
            }
            portalDef.portlets = portletsDef.portlet;

            if (portalDef.portlets) {
                for (var i = 0; i < portalDef.portlets.length; i++) {
                    var portletDef = portalDef.portlets[i];
                    var propertyDef = portletDef.property;
                    if (propertyDef && !(propertyDef instanceof Array)) {
                        propertyDef = [ propertyDef ];
                    }
                    portletDef.properties = propertyDef;
                    delete portletDef.property;
                }
            }
        }
        catch (e) {
            DBG.println(e);
        }
    }
    else {
        e = ""
    }

    if (!this._manifest) {
        this._manifest = { error: e };
    }
    
    // callback
    if (callback) {
        callback.run(this._manifest);
    }
};

ZmPortalApp.prototype.getPortalController = function() {
	if (!this._portalController) {
		this._portalController = new ZmPortalController(this._appCtxt, this._container, this);
	}
	return this._portalController;
};

ZmPortalApp.prototype.getPortletMgr = function() {
    if (!this._portletMgr) {
        this._portletMgr = new ZmPortletMgr(this._appCtxt);
    }
    return this._portletMgr;
};

//
// Protected methods
//

ZmPortalApp.prototype._getOverviewTrees =
function() {
	var apps = [];
	for (var name in ZmApp.CHOOSER_SORT) {
		apps.push({ name: name, sort: ZmApp.CHOOSER_SORT[name] });
	}
	apps.sort(ZmPortalApp.__BY_SORT);

	var appName = null;
	for (var i = 0; i < apps.length; i++) {
		var app = apps[i];
		if (app.name == this._name) continue;

		appName = app.name;
		break; 
	}
	if (appName) {
		var app = this._appCtxt.getApp(appName);
		return app._getOverviewTrees();
	}
	return null;
};

//
// Private functions
//

ZmPortalApp.__BY_SORT = function(a, b) {
	return a.sort - b.sort;
};