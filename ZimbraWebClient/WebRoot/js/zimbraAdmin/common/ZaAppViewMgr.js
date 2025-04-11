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
* Creates a layout manager from the given components.
* @constructor
* @class
* This class manages layout. The layout is divided into the following parts:
* <p><ul>
*  <li>banner: static; has a few account-related buttons</li>
*  <li>search bar: static; has buttons for various ways to search, including browse</li>
*  <li>overview panel: tree view of folders, tags, app links</li>
*  <li>sash: a thin moveable vertical bar for resizing the surrounding elements
*  <li>app container: the most dynamic area; displays app-specific toolbar and content</li>
* </ul></p>
* <p>
* Visibility is managed through Z indexes, which have constants in the following order:</p>
* <p>
* Z_HIDDEN, Z_CURTAIN, Z_VIEW, Z_TOOLTIP, Z_MENU, Z_VEIL, Z_DIALOG, Z_BUSY</p>
* <p>
* Since z-index matters only among peer elements, anything that we manage via z-index has to
* hang off the shell. To manage an app view, we create an app container that hangs off the shell
* and put the app view in there.</p>
* <p>
* The app container lays out the app elements in the desired style, for example, in a vertical
* layout. Different layout styles can be added here, and then specified when the app view is
* created.</p>
* <p>
* Some views are "volatile", which means they trigger browser bugs when we try to hide them. It happens
* with views that contain forms. In IE, SELECT fields don't obey z-index, and in Firefox, the cursor
* bleeds through.
*
* @author Conrad Damon
* @author Ross Dargahi
* @param shell			the outermost containing element
* @param banner			the banner
* @param controller		the app controller
*/
function ZaAppViewMgr(shell, controller, hasSkin) {

	this._shell = shell;
	this._controller = controller;
	this._appCtxt = controller._appCtxt;
	this._shellSz = this._shell.getSize();
	this._shell.addControlListener(new AjxListener(this, this._shellControlListener));
	this._needBannerLayout = false;

/*	this._sash = new DwtSash(this._shell, DwtSash.HORIZONTAL_STYLE, "AppSash-horiz", 5);
	this._sash.registerCallback(this._sashCallback, this);
*/	
	this._currentView = null;			// name of currently visible view
	this._views = new Object();			// hash that gives names to app views
	this._hidden = new Array();			// stack of views that aren't visible
	
	this._layoutStyle = new Object();	// hash matching view to layout style
	this._staleCallback = new Object(); // when topmost view is popped, allow underlying view to cleanup

	this._compList = new Array();		// list of component IDs
	this._components = new Object();	// component objects (widgets)
	this._htmlEl = new Object();		// their HTML elements
	this._containers = new Object();	// containers within the skin
	this._contBounds = new Object();	// bounds for the containers
	
	// view preemption
	this._pushCallback = new AjxCallback(this, this.pushView);
//	this._popCallback = new AjxCallback(this, this.popView);
	
/*	// hash matching layout style to their methods	
	this._layoutMethod = new Object();
	this._layoutMethod[ZaAppViewMgr.LAYOUT_VERTICAL] = this._appLayoutVertical;
*/	
}

ZaAppViewMgr.DEFAULT = -1;

// reasons the layout changes
ZaAppViewMgr.RESIZE = 1;
ZaAppViewMgr.BROWSE = 2;
ZaAppViewMgr.OVERVIEW = 3;

// visible margins (will be shell background color)
ZaAppViewMgr.TOOLBAR_SEPARATION = 0;	// below search bar
ZaAppViewMgr.COMPONENT_SEPARATION = 2;	// in app container

// layout styles
ZaAppViewMgr.LAYOUT_VERTICAL = 1;	// top to bottom, full width, last element gets remaining space

// used when coming back from pop shield callbacks
ZaAppViewMgr.PENDING_VIEW = "ZaAppViewMgr.PENDgING_VIEW";

// components
ZaAppViewMgr.C_BANNER					= "BANNER";
ZaAppViewMgr.C_USER_INFO				= "USER INFO";
ZaAppViewMgr.C_SEARCH					= "SEARCH";
ZaAppViewMgr.C_SEARCH_BUILDER			= "SEARCH BUILDER";
ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR	= "SEARCH BUILDER TOOLBAR";
ZaAppViewMgr.C_CURRENT_APP				= "CURRENT APP";
ZaAppViewMgr.C_APP_CHOOSER				= "APP CHOOSER";
ZaAppViewMgr.C_TREE						= "TREE";
ZaAppViewMgr.C_TREE_FOOTER				= "TREE FOOTER";
ZaAppViewMgr.C_TOOLBAR_TOP				= "TOP TOOLBAR";
ZaAppViewMgr.C_TOOLBAR_BOTTOM			= "BOTTOM TOOLBAR";
ZaAppViewMgr.C_APP_CONTENT				= "APP CONTENT";
ZaAppViewMgr.C_STATUS					= "STATUS";
ZaAppViewMgr.C_SASH						= "SASH";

// keys for getting container IDs
ZaAppViewMgr.CONT_ID_KEY = new Object();
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_BANNER]					= ZaSettings.SKIN_LOGO_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_USER_INFO]				= ZaSettings.SKIN_USER_INFO_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_SEARCH]					= ZaSettings.SKIN_SEARCH_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_SEARCH_BUILDER]			= ZaSettings.SKIN_SEARCH_BUILDER_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR]	= ZaSettings.SKIN_SEARCH_BUILDER_TOOLBAR_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_CURRENT_APP]			= ZaSettings.SKIN_CURRENT_APP_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_APP_CHOOSER]			= ZaSettings.SKIN_APP_CHOOSER_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_TREE]					= ZaSettings.SKIN_TREE_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_TREE_FOOTER]			= ZaSettings.SKIN_TREE_FOOTER_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_TOOLBAR_TOP]			= ZaSettings.SKIN_APP_TOP_TOOLBAR_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_TOOLBAR_BOTTOM]			= ZaSettings.SKIN_APP_BOTTOM_TOOLBAR_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_APP_CONTENT]			= ZaSettings.SKIN_APP_MAIN_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_STATUS]					= ZaSettings.SKIN_STATUS_ID;
ZaAppViewMgr.CONT_ID_KEY[ZaAppViewMgr.C_SASH]					= ZaSettings.SKIN_SASH_ID;

// Public methods

ZaAppViewMgr.prototype.toString = 
function() {
	return "ZaAppViewMgr";
}

ZaAppViewMgr.prototype.getShell = 
function() {
	return this._shell;
}

/**
* Returns the name of the app view currently being displayed.
*/
ZaAppViewMgr.prototype.getCurrentView =
function() {
	return this._currentView;
}


/**
* Creates an app view from the given components and puts it in an app container.
*
* @param viewName		the name of the view
* @param appName		the name of the owning app
* @param elements		an array of elements to display
* @return				the app view
*/
ZaAppViewMgr.prototype.createView =
function(viewId, elements) {
	this._views[viewId] = elements;
	this.addComponents(elements, false, true);
}

/**
* Makes the given view visible, pushing the previously visible one to the top of the
* hidden stack.
*
* @param viewId	the name of the app view to push
* @return			the id of the view that is displayed
*/
ZaAppViewMgr.prototype.pushView =
function(viewId) {
	// if same view, no need to go through hide/show
	if (viewId == this._currentView) {
		this._setTitle(viewId);
		return viewId;
	}

	this._setViewVisible(this._currentView, false);
	if (this._currentView && (this._currentView != viewId))
		this._hidden.push(this._currentView);

	this._removeFromHidden(viewId);
	var temp = this._lastView;
	this._lastView = this._currentView;
	this._currentView = viewId;

	this._setViewVisible(viewId, true);

	return viewId;
}

/**
* Hides the currently visible view, and makes the view on top of the hidden stack visible.
*
* @return		the id of the view that is displayed
*/
ZaAppViewMgr.prototype.popView =
function() {
	if (!this._currentView)
		throw new AjxException("no view to pop");

	this._setViewVisible(this._currentView,false);

	this._lastView = this._currentView;
	this._currentView = this._hidden.pop();

	if (!this._currentView)
		throw new AjxException("no view to show");
		
	this._removeFromHidden(this._currentView);

	this._setViewVisible(this._currentView, true);
	return this._currentView;
}

/**
* Makes the given view visible, and clears the hidden stack.
*
* @param viewName	the name of a view
* @return			true if the view was set
*/
ZaAppViewMgr.prototype.setView =
function(viewName) {
//	DBG.println(AjxDebug.DBG1, "setView: " + viewName);
	var result = this.pushView(viewName);
    if (result)
		this._hidden = new Array();
	return result;
}

ZaAppViewMgr.prototype.addComponents =
function(components, doFit, noSetZ) {
	var list = new Array();
	for (var cid in components) {
		this._compList.push(cid);
		var comp = components[cid];
		this._components[cid] = comp;
		var htmlEl = comp.getHtmlElement();
		this._htmlEl[cid] = htmlEl;
		var contId = ZaSettings.get(ZaAppViewMgr.CONT_ID_KEY[cid]);
		var contEl = document.getElementById(contId);
		this._containers[cid] = contEl;
		if (Dwt.contains(contEl, htmlEl))
			throw new AjxException("element already added to container: " + cid);		
		Dwt.removeChildren(contEl);
		
		list.push(cid);
		
		if (!noSetZ)
			comp.zShow(true);
		
		if (cid == ZaAppViewMgr.C_SEARCH_BUILDER  || cid == ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR ) {
			//this._components[ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR].setLocation(Dwt.LOC_NOWHERE, Dwt.LOC_NOWHERE);
			DBG.println(AjxDebug.DBG1, "Enforce Z-index to hidden " + cid) ;
			comp.zShow(false);
		}

		if (cid == ZaAppViewMgr.C_SASH)
			comp.setCursor("default");
	}
	if (doFit)
		this._stickToGrid(list);
		
	
}
ZaAppViewMgr.prototype.showSearchBuilder =
function(visible) {
	DBG.println(AjxDebug.DBG1, "show search builder: " + visible);
	skin.showSearchBuilder(visible);
	this._components[ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR].zShow(visible);
	this._components[ZaAppViewMgr.C_SEARCH_BUILDER].zShow(visible);
	var list = [ZaAppViewMgr.C_SEARCH_BUILDER, ZaAppViewMgr.C_SEARCH_BUILDER_TOOLBAR,
				ZaAppViewMgr.C_CURRENT_APP, ZaAppViewMgr.C_APP_CHOOSER, ZaAppViewMgr.C_TREE,
				ZaAppViewMgr.C_TREE_FOOTER, ZaAppViewMgr.C_TOOLBAR_TOP, ZaAppViewMgr.C_APP_CONTENT];
	this._stickToGrid(list);
	// search builder contains forms, and browsers have quirks around form fields and z-index
	if (!visible) {
		this._components[ZaAppViewMgr.C_SEARCH_BUILDER].setLocation(Dwt.LOC_NOWHERE, Dwt.LOC_NOWHERE);
	}
};
// Private methods

ZaAppViewMgr.prototype._stickToGrid = 
function(components) {
	for (var i = 0; i < components.length; i++) {
		var cid = components[i];
		// don't resize logo image (it will tile) or reposition it (centered via style)
		if (cid == ZaAppViewMgr.C_BANNER) continue;
		//DBG.println(AjxDebug.DBG3, "fitting to container: " + cid);
		var cont = this._containers[cid];
		if (cont) {
			var contBds = Dwt.getBounds(cont);
			var comp = this._components[cid];
			if (cid == ZaAppViewMgr.C_APP_CONTENT || 
				cid == ZaAppViewMgr.C_TOOLBAR_TOP ||
				cid == ZaAppViewMgr.C_TOOLBAR_BOTTOM ) {
				// make sure we fit the component that's current
				var elements = this._views[this._currentView];
				comp = elements[cid];
			}
			if (comp && (comp.getZIndex() != Dwt.Z_HIDDEN)) {
				comp.setBounds(contBds.x, contBds.y, contBds.width, contBds.height);
				this._contBounds[cid] = contBds;
			}
		}
	}
	//this._debugShowMetrics(components);
}


// Removes a view from the hidden stack.
ZaAppViewMgr.prototype._removeFromHidden =
function(view) {
	var newHidden = new Array();
	for (var i = 0; i < this._hidden.length; i++)
		if (this._hidden[i] != view)
			newHidden.push(this._hidden[i]);
	this._hidden = newHidden;
}

// Listeners

// Handles shell resizing event.
ZaAppViewMgr.prototype._shellControlListener =
function(ev) {
	if (ev.oldWidth != ev.newWidth || ev.oldHeight != ev.newHeight) {
		this._shellSz.x = ev.newWidth;
		this._shellSz.y = ev.newHeight;
		var deltaWidth = ev.newWidth - ev.oldWidth;
		var deltaHeight = ev.newHeight - ev.oldHeight;
		DBG.println(AjxDebug.DBG1, "shell control event: dW = " + deltaWidth + ", dH = " + deltaHeight);
		if (this._isNewWindow) {
			// reset width of top toolbar
			var topToolbar = this._views[this._currentView][ZaAppViewMgr.C_TOOLBAR_TOP];
			if (topToolbar)
				topToolbar.setSize(ev.newWidth, Dwt.DEFAULT);
				
			var bottomToolbar = this._views[this._currentView][ZaAppViewMgr.C_TOOLBAR_BOTTOM];
			if (bottomToolbar)
				bottomToolbar.setSize(ev.newWidth, Dwt.DEFAULT);
				
			// make sure to remove height of top toolbar for height of app content
			var appContent = this._views[this._currentView][ZaAppViewMgr.C_APP_CONTENT];
			if (appContent)
				appContent.setSize(ev.newWidth, ev.newHeight - topToolbar.getH());
		} else {
			if (deltaHeight) {
				var list = [ZaAppViewMgr.C_APP_CHOOSER, ZaAppViewMgr.C_SASH, ZaAppViewMgr.C_APP_CONTENT,ZaAppViewMgr.C_TREE, ZaAppViewMgr.C_STATUS];
				this._stickToGrid(list);
			}
			if (deltaWidth) {
				var list = [ZaAppViewMgr.C_BANNER, ZaAppViewMgr.C_TOOLBAR_TOP, ZaAppViewMgr.C_APP_CONTENT, ZaAppViewMgr.C_TOOLBAR_BOTTOM,ZaAppViewMgr.C_SEARCH];
				this._stickToGrid(list);
			}
		}
	}
}


// Makes elements visible/hidden by locating them off- or onscreen and setting
// their z-index.
ZaAppViewMgr.prototype._setViewVisible =
function(viewId, show) {
	var elements = this._views[viewId];
	if (show) {
		var list = new Array();
		for (var cid in elements) {
			list.push(cid);
			elements[cid].zShow(true);
		}
		this._stickToGrid(list);
		this._setTitle(viewId);
	} else {
		for (var cid in elements) {
			elements[cid].setLocation(Dwt.LOC_NOWHERE, Dwt.LOC_NOWHERE);
			elements[cid].zShow(false);
		}
	}
}

ZaAppViewMgr.prototype._setTitle =
function(viewId) {
	var elements = this._views[viewId];
	var content = elements[ZaAppViewMgr.C_APP_CONTENT];
/*	if (content && content.getTitle) {
		var title = content.getTitle();
		Dwt.setTitle(title ? title : ZaMsg.zimbraTitle);
	}
*/
	if(this._components[ZaAppViewMgr.C_CURRENT_APP] && this._components[ZaAppViewMgr.C_CURRENT_APP].setCurrentView) {
		this._components[ZaAppViewMgr.C_CURRENT_APP].setCurrentView(viewId);		
	}

	if(ZaZimbraAdmin.MSG_KEY[viewId] && ZaMsg[ZaZimbraAdmin.MSG_KEY[viewId]]) {
		Dwt.setTitle(ZaMsg[ZaZimbraAdmin.MSG_KEY[viewId]]);
	} else {
		Dwt.setTitle(ZaMsg.zimbraTitle);	
	}
}