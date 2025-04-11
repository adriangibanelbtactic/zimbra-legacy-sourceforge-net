/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.1
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.1 ("License"); you may not use this file except in
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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function ZmCalViewMgr(parent, controller, dropTgt) {

	DwtComposite.call(this, parent, "ZmCalViewMgr", Dwt.ABSOLUTE_STYLE);
	this.addControlListener(new AjxListener(this, this._controlListener));

	this._controller = controller;
	this._dropTgt = dropTgt;	

	// View array. Holds the various views e.g. day, month, week, etc...
	this._views = new Object();
	this._date = new Date();
	this._viewFactory = new Object();
	this._viewFactory[ZmController.CAL_DAY_VIEW] = ZmCalDayView;
	this._viewFactory[ZmController.CAL_WORK_WEEK_VIEW] = ZmCalWorkWeekView;
	this._viewFactory[ZmController.CAL_WEEK_VIEW] = ZmCalWeekView;
	this._viewFactory[ZmController.CAL_MONTH_VIEW] = ZmCalMonthView;
	this._viewFactory[ZmController.CAL_SCHEDULE_VIEW] = ZmCalScheduleView;	
}

ZmCalViewMgr.prototype = new DwtComposite;
ZmCalViewMgr.prototype.constructor = ZmCalViewMgr;

ZmCalViewMgr._SEP = 5;

ZmCalViewMgr.prototype.toString = 
function() {
	return "ZmCalViewMgr";
}

ZmCalViewMgr.prototype.getController =
function() {
	return this._controller;
}

// sets need refresh on all views
ZmCalViewMgr.prototype.setNeedsRefresh = 
function() {
	for (var name in this._views) {
		this._views[name].setNeedsRefresh(true);
	}
}

ZmCalViewMgr.prototype.getCurrentView =
function() {
	return this._views[this._currentViewName];
}

ZmCalViewMgr.prototype.getCurrentViewName =
function() {
	return this._currentViewName;
}

ZmCalViewMgr.prototype.getView =
function(viewName) {
	return this._views[viewName];
}

ZmCalViewMgr.prototype.getTitle =
function() {
	return this.getCurrentView().getTitle();
}

ZmCalViewMgr.prototype.getDate =
function() {
	return this._date;
}

ZmCalViewMgr.prototype.setDate =
function(date, duration, roll) {
//DBG.println("ZmCalViewMgr.setDate = "+date);
	this._date = new Date(date.getTime());
	this._duration = duration;
	if (this._currentViewName != null) {
		var view = this._views[this._currentViewName];
		view.setDate(date, duration, roll);
	}
}

ZmCalViewMgr.prototype.createView =
function(viewName) {
//DBG.println("ZmCalViewMgr.prototype.createView: " + viewName);
	view = new this._viewFactory[viewName](this, DwtControl.ABSOLUTE_STYLE, this._controller, this._dropTgt);
	view.setDragSource(this._dragSrc);
	view.addTimeSelectionListener(new AjxListener(this, this._viewTimeSelectionListener));	
	view.addDateRangeListener(new AjxListener(this, this._viewDateRangeListener));
	view.addViewActionListener(new AjxListener(this, this._viewActionListener));	
	this._views[viewName] = view;
	return view;
}

ZmCalViewMgr.prototype.addViewActionListener = 
function(listener) {
	this.addListener(ZmCalBaseView.VIEW_ACTION, listener);
}

ZmCalViewMgr.prototype.removeViewActionListener = 
function(listener) {
	this.removeListener(ZmCalBaseView.VIEW_ACTION, listener);
}

ZmCalViewMgr.prototype.addTimeSelectionListener = 
function(listener) {
	this.addListener(ZmCalBaseView.TIME_SELECTION, listener);
}

ZmCalViewMgr.prototype.removeTimeSelectionListener = 
function(listener) {
	this.removeListener(ZmCalBaseView.TIME_SELECTION, listener);
}

ZmCalViewMgr.prototype.addDateRangeListener = 
function(listener) {
	this.addListener(DwtEvent.DATE_RANGE, listener);
}

ZmCalViewMgr.prototype.removeDateRangeListener = 
function(listener) {
	this.removeListener(DwtEvent.DATE_RANGE, listener);	
}

ZmCalViewMgr.prototype.setView =
function(viewName) {
//DBG.println("ZmCalViewMgr.prototype.setView: " + viewName);
	if (viewName != this._currentViewName) {
		if (this._currentViewName) {
			//this._views[this._currentViewName].setVisible(false);
			this._views[this._currentViewName].setLocation(Dwt.LOC_NOWHERE, Dwt.LOC_NOWHERE);
		}
		var view = this._views[viewName];
		//view.setVisible(true);
		this._currentViewName = viewName;

		var vd = view.getDate();
		
		if (vd == null || (view.getDate().getTime() != this._date.getTime())) {
			view.setDate(this._date, this._duration, true);
		}
		this._layout();
	}
}

ZmCalViewMgr.getPrintHtml = function (mgr) {
	var currentView = mgr.getCurrentView();
	return currentView.getPrintHtml();
};

ZmCalViewMgr.prototype._layout =
function() {
	var mySz = this.getSize();
//DBG.println("_layout");
//DBG.dumpObj(mySz);
	if (mySz.x == 0 || mySz.y == 0)
		return;
	var view = this._views[this._currentViewName];
	var width = mySz.x - ZmCalViewMgr._SEP;
	var height = mySz.y;
	var viewSz = view.getSize();
	if (viewSz.x == width && viewSz.y == height)
		view.setLocation(0, 0);
	else
		view.setBounds(0, 0, width, height);	
}

ZmCalViewMgr.prototype._controlListener =
function(ev) {
//DBG.println("ZmCalViewMgr._controlListener!!! this._oldHeight="+this._oldHeight+" this._oldWidth="+this._oldWidth);
//DBG.dumpObj(ev);
	if (ev.oldHeight != ev.newHeight
		|| ev.oldWidth != ev.newWidth) {
		this._layout();
	}	
}

ZmCalViewMgr.prototype._viewTimeSelectionListener =
function(ev) {
	//DBG.println("ZmCalViewMgr: VTS LISTENER: " + ev.detail);
	this.notifyListeners(ZmCalBaseView.TIME_SELECTION, ev);
}


ZmCalViewMgr.prototype._viewActionListener =
function(ev) {
	//DBG.println("ZmCalViewMgr: VA LISTENER: " + ev.detail);
	this.notifyListeners(ZmCalBaseView.VIEW_ACTION, ev);
}

ZmCalViewMgr.prototype._viewSelectionListener =
function(ev) {
//	DBG.println("ZmCalViewMgr: VS LISTENER: " + ev.detail);
	//this.notifyListeners(ZmCalBaseView.TIME_SELECTION, ev);
}

ZmCalViewMgr.prototype._viewDateRangeListener =
function(ev) {
	//DBG.println("viewRangeListener!!!");
	//DBG.dumpObj(ev);
	// Notify any listeners
	if (!this.isListenerRegistered(DwtEvent.DATE_RANGE))
		return;
	this.notifyListeners(DwtEvent.DATE_RANGE, ev);
}
