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
ZmBriefcaseView = function(parent, appCtxt, controller, dropTgt) {
	// call super constructor
	var headerList = null;//no headers for this view
	var view = ZmController.BRIEFCASE_VIEW;
	ZmListView.call(this, parent, "ZmBriefcaseView", DwtControl.ABSOLUTE_STYLE, view, ZmItem.DOCUMENT, controller, headerList, dropTgt);
	
	this._appCtxt = appCtxt;
	this._controller = controller;

	this._USE_IFRAME = true;

	this._setMouseEventHdlrs(); // needed by object manager
	this._setAllowSelection();
	
	this.setDropTarget(dropTgt);
//	this._dragSrc = new DwtDragSource(Dwt.DND_DROP_MOVE);
//	this.setDragSource(this._dragSrc);
}
ZmBriefcaseView.prototype = new ZmListView;
ZmBriefcaseView.prototype.constructor = ZmBriefcaseView;

ZmBriefcaseView.prototype.toString =
function() {
	return "ZmBriefcaseView";
};

// Data

ZmBriefcaseView.prototype._appCtxt;
ZmBriefcaseView.prototype._controller;

// Public methods

ZmBriefcaseView.prototype.getController =
function() {
	return this._controller;
};

ZmBriefcaseView.FILE_EXT = {
	ppt: "ImgMSPowerpointDoc_48",
	doc: "ImgMSWordDoc_48",
	xls: "ImgMSExcelDoc_48",
	zip: "ImgZipDoc_48",
	pdf: "ImgPDFDoc_48",
	exe: "ImgExeDoc_48"
};

ZmBriefcaseView.prototype.getContentTypeIcon = 
function(name){
	var icon =  "ImgUnknownDoc_48";	
	var idx = name.indexOf(".");
	if(idx>0){
		var ext = name.substring(idx+1);	
		var tmpIcon = ZmBriefcaseView.FILE_EXT[ext];
			if(tmpIcon){
			icon = tmpIcon;
			}
	}
	return icon;
};

ZmBriefcaseView.prototype._createItemHtml =
function(item, params) {
	
	var name = item.name;
	var icon =  this.getContentTypeIcon(name);
	
	if(name.length>14){
		name = name.substring(0,14)+"...";
	}
	
	var div = document.createElement("div");
	div.className = "ZmBriefcaseItem";
	
	var div1 = document.createElement("div");
	div1.className = "ZmThumbnailItem";
	
	var div2 = document.createElement("div");
	div2.className = icon+" ZmThumbnailIcon";
	
	div1.appendChild(div2);
	div.appendChild(div1);
	
	var div2 = document.createElement("div");
	div2.className = "ZmThumbnailName";
	
	var span = document.createElement("span");
	
	if(item instanceof ZmBriefcaseItem){
		span.innerHTML = ["<a href='",item.restUrl,"' target='_blank'>",name,"</a>"].join("");
	}else{
		span.innerHTML = item;
	}
	
	div2.appendChild(span);
	div.appendChild(div2);
	
	if (params.isDnDIcon) {
		Dwt.setPosition(div, Dwt.ABSOLUTE_STYLE);
	}
	
	this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);
	return div;
};

ZmBriefcaseView.prototype._itemClicked =
function(clickedEl, ev) {
	
	this._selectedClass = "ZmBriefcaseItemSelected";
	this._kbFocusClass = "ZmBriefcaseItemFocused";
	this._normalClass = "ZmBriefcaseItem";
	this._disabledSelectedClass = "ZmBriefcaseItemDisabledSelect";
	this._rightClickClass = "ZmBriefcaseItemSelected";
	this._styleRe = new RegExp(
        "\\b(" +
        [   this._disabledSelectedClass,
            this._selectedClass,
            this._kbFocusClass,
            this._dndClass,
            this._rightClickClass,
            this._normalClass
        ].join("|") +
        ")\\b", "g"
    );
    
	DwtListView.prototype._itemClicked.call(this,clickedEl,ev);
	return;
};


ZmBriefcaseView.prototype.setSelectedItems =
function(selectedArray) {
	this.deselectAll();
	var sz = selectedArray.length;
	for (var i = 0; i < sz; ++i) {
		var el = this._getElFromItem(selectedArray[i]);
		if (el) {
			this._selectedItems.add(el);
		}
	}
};

ZmBriefcaseView.prototype.set =
function(folderId) {
	var element = this.getHtmlElement();
	var items = this._controller.getItemsInFolderFromCache(folderId);

	var list = new AjxVector();
	for(var i in items){
		list.add(items[i]);
	}
	DwtListView.prototype.set.call(this,list);	
	
};

ZmBriefcaseView.prototype.getTitle =
function() {
	//TODO: title is the name of the current folder
	return [ZmMsg.zimbraTitle].join(": ");
};

ZmBriefcaseView.prototype.getContent =
function() {
	return this.getHtmlElement().innerHTML;
};

ZmBriefcaseView.prototype.setBounds =
function(x, y, width, height) {
	ZmListView.prototype.setBounds.call(this, x, y, width, height);	
};

// Protected methods

ZmBriefcaseView.prototype._createHtml = function() {
	var element = this.getHtmlElement();
	Dwt.setScrollStyle(element, Dwt.SCROLL);
};

ZmBriefcaseView.prototype.enableToolbar = function(enable){
	var toolbar = this._controller._toolbar[view._controller._currentView];
	toolbar.enable([ZmOperation.TAG_MENU, ZmOperation.DELETE], enable);
};

ZmBriefcaseView.prototype.onDelete = function(){

	var controller = this._controller;
	var object = controller._object;

};


ZmBriefcaseView.prototype.refresh = function(restUrl){
};

ZmBriefcaseView.prototype._getToolTip =
function(field, item, ev, div, match) {
	if (!item) { return; }
	var tooltip = item.name;	
	return tooltip;
};


ZmBriefcaseView.prototype._mouseOverAction =
function(ev, div) {
	DwtListView.prototype._mouseOverAction.call(this, ev, div);
	var id = ev.target.id || div.id;
	if (!id) return true;
	
	var match = this._parseId(id);
	if (match) {
		var item = this.getItemFromElement(div);
		if(item){
		this.setToolTipContent(this._getToolTip(match.field, item, ev, div, match));
		}
	}		
	return true;
};

ZmBriefcaseView.prototype._mouseDownListener =
function(ev) {
	DwtListView.prototype._mouseDownListener.call(this,ev);	
	if(this._dndSelection==null){
	this.deselectAll();	
	this._controller._resetOpForCurrentView();
	}
};

ZmBriefcaseView.prototype._updateDragSelection =
function(row, select) {
    // TODO: new style to mark drop target  
};