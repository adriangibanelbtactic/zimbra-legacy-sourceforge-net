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
 
function ZmFilterRulesView(parent, appCtxt, controller) {

	DwtTabViewPage.call(this, parent, "ZmFilterRulesView");

	this._appCtxt = appCtxt;
	this._controller = controller;
	this._prefsController = appCtxt.getApp(ZmZimbraMail.PREFERENCES_APP).getPrefController();
	
	this._rules = appCtxt.getApp(ZmZimbraMail.PREFERENCES_APP).getFilterRules();

	this._title = [ZmMsg.zimbraTitle, ZmMsg.options, ZmPrefView.TAB_NAME[ZmPrefView.FILTER_RULES]].join(": ");

	this._rendered = false;
	this._hasRendered = false;
};

ZmFilterRulesView.prototype = new DwtTabViewPage;
ZmFilterRulesView.prototype.constructor = ZmFilterRulesView;

ZmFilterRulesView.prototype.toString =
function() {
	return "ZmFilterRulesView";
};

ZmFilterRulesView.prototype.showMe =
function() {
	Dwt.setTitle(this._title);
	this._prefsController._resetOperations(this._prefsController._toolbar, ZmPrefView.FILTER_RULES);
	if (this._hasRendered) return;

	this._controller._setup();
	this._hasRendered = true;
};

ZmFilterRulesView.prototype.hasRendered =
function () {
	return this._hasRendered;
};

ZmFilterRulesView.prototype.getTitle =
function() {
	return this._title;
};

// View is always in sync with rules
ZmFilterRulesView.prototype.reset = function() {};

/*
* ZmFilterListView
*/
function ZmFilterListView(parent, appCtxt, controller) {
	var headerList = this._getHeaderList();
	DwtListView.call(this, parent, "ZmFilterListView", null, headerList);	

	this._appCtxt = appCtxt;
	this._rules = appCtxt.getApp(ZmZimbraMail.PREFERENCES_APP).getFilterRules();
	
	this._controller = controller;
	this._rules.addChangeListener(new AjxListener(this, this._changeListener));
	this.setMultiSelect(false);	// single selection only
	this._internalId = AjxCore.assignId(this);
};

ZmFilterListView.COL_ACTIVE	= 1;
ZmFilterListView.COL_NAME	= 2;

ZmFilterListView.COL_WIDTH_ACTIVE = 40;

ZmFilterListView.prototype = new DwtListView;
ZmFilterListView.prototype.constructor = ZmFilterListView;

ZmFilterListView.prototype.toString = 
function() {
	return "ZmFilterListView";
};

/**
 * Only show rules that have at least one valid action (eg, if the only action
 * is "tag" and tagging is disabled, don't show the rule).
 */
ZmFilterListView.prototype.set =
function(list) {
	this._checkboxIds = [];
	var list1 = new AjxVector();
	var len = list.size();
	for (var i = 0; i < len; i++) {
		var rule = list.get(i);
		if (rule.hasValidAction(this._appCtxt)) {
			list1.add(rule);
		}
	}
	DwtListView.prototype.set.call(this, list1);
	// can't add handlers until item divs have been added to DOM
	this._addCheckboxHandlers();
};

ZmFilterListView.prototype._getHeaderList =
function() {
	var headerList = [];
	headerList.push(new DwtListHeaderItem(ZmFilterListView.COL_ACTIVE, ZmMsg.active, null, ZmFilterListView.COL_WIDTH_ACTIVE));
	headerList.push(new DwtListHeaderItem(ZmFilterListView.COL_NAME, ZmMsg.filterName));
	return headerList;
};

ZmFilterListView.prototype._createItemHtml =
function(item) {
	var	div = document.createElement("div");
	var base = "Row";
	div[DwtListView._STYLE_CLASS] = base;
	div[DwtListView._SELECTED_STYLE_CLASS] = [base, DwtCssStyle.SELECTED].join("-");	// Row-selected
	div.className = div[DwtListView._STYLE_CLASS];
	this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);

	var html = [];
	var i = 0;

	html[i++] = "<table cellpadding=0 cellspacing=0 border=0 width=100%>";

	html[i++] = "<tr id='" + item.id + "'>";

	var checked = item.isActive() ? "checked": "";
	var inputId = "_ruleCheckbox" + item.id;

	html[i++] = "<td width=" + this._headerList[0]._width + ">";
	html[i++] = "<input type='checkbox' " + checked + " id='" + inputId + "'></td>";
	html[i++] = "<td width=" + this._headerList[1]._width + ">";
	html[i++] = item.getName();
	html[i++] = "</td>";
	html[i++] = "</tr></table>";

	div.innerHTML = html.join("");

	this._checkboxIds.push(inputId);

	return div;
};

/*
* Sets up handlers for the 'active' checkboxes. IE handles checkbox events through the list
* view's _itemClicked() method, since its ONCHANGE responsiveness appears to be flaky.
*/
ZmFilterListView.prototype._addCheckboxHandlers =
function() {
	for (var i = 0; i < this._checkboxIds.length; i++) {
		var id = this._checkboxIds[i];
		var inputEl = document.getElementById(id);
		if (inputEl) {
			inputEl._flvId = this._internalId;
			if (!AjxEnv.isIE)
				Dwt.setHandler(inputEl, DwtEvent.ONCHANGE, ZmFilterListView._activeStateChange);
		}
	}
};

/*
* In general, we just re-display all the rules when anything changes, rather
* than trying to update a particular row.
*/
ZmFilterListView.prototype._changeListener =
function(ev) {
	if (ev.type != ZmEvent.S_FILTER) return;

	DBG.println(AjxDebug.DBG3, "FILTER RULES: change listener");
	if (ev.event == ZmEvent.E_MODIFY) {
		var index = ev.getDetail("index");
		this._controller._setListView();
		var rule = index ? this._rules.getRuleByIndex(index) : null;
		if (rule) {
			this.setSelection(rule);
		}
	}
};

/*
* Handles click of 'active' checkbox by toggling the rule's active state.
*
* @param ev			[DwtEvent]	click event
*/
ZmFilterListView._activeStateChange =
function(ev) {
	var target = DwtUiEvent.getTarget(ev);
	DBG.println(AjxDebug.DBG3, "FILTER RULES: active state change for filter with ID " + target.id);
	var flv = AjxCore.objectWithId(target._flvId);
	var ruleId = target.id.substring(13);
	var rule = flv._rules.getRuleById(ruleId);
	if (rule)
		flv._rules.setActive(rule, !rule.isActive());
};

/*
* Override so that we don't change selection when the 'active' checkbox is clicked.
* Also contains a hack for IE for handling a click of the 'active' checkbox, because
* the ONCHANGE handler was only getting invoked on every other checkbox click for IE.
*
* @param clickedEl	[Element]	list DIV that received the click
* @param ev			[DwtEvent]	click event
* @param button		[constant]	button that was clicked
*/
ZmFilterListView.prototype._allowLeftSelection =
function(clickedEl, ev, button) {
	// We only care about mouse events
	if (!(ev instanceof DwtMouseEvent))
		return true;
		
	var target = DwtUiEvent.getTarget(ev);
	var isInput = (target.id.indexOf("_ruleCheckbox") == 0);
	if (AjxEnv.isIE && isInput)
		ZmFilterListView._activeStateChange(ev);
	
	return !isInput;
};
