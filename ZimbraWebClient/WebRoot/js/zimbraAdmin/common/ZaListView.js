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

/**
* @class ZaListView
* @constructor ZaListView
* @param parent
* @ className
* @ posStyle
* @ headerList
* Abstract class list views. All the List views in the Admin panel extend this class.
* @author Greg Solovyev
**/
function ZaListView(parent, className, posStyle, headerList) {
	if (arguments.length == 0) return;
	DwtListView.call(this, parent, className, posStyle, headerList);

}

ZaListView.prototype = new DwtListView;
ZaListView.prototype.constructor = ZaListView;

ZaListView.prototype.toString = 
function() {
	return "ZaListView";
}

ZaListView.ITEM_FLAG_CLICKED = DwtListView._LAST_REASON + 1;

// abstract methods
ZaListView.prototype._createItemHtml = function(item) {}

ZaListView.prototype._mouseOverAction =
function(ev, div) {
	if (div._type == DwtListView.TYPE_HEADER_ITEM) {
		if(this._headerList[div._itemIndex]._sortable) {
			div.className = "DwtListView-Column DwtListView-ColumnHover";		
			this.setToolTipContent(ZaMsg.LST_ClickToSort_tt + this._headerList[div._itemIndex].getLabel());	
		} else {
			this.setToolTipContent(null);
		}
	} else if (div._type == DwtListView.TYPE_HEADER_SASH) {
		div.style.cursor = AjxEnv.isIE ? "col-resize" : "e-resize";
    } else if (div._type == DwtListView.TYPE_LIST_ITEM){
		var item = this.getItemFromElement(div);
		if (item && item.getToolTip)
			this.setToolTipContent(item.getToolTip());
	}
}

ZaListView.prototype._mouseOutAction = 
function(mouseEv, div) {
	if (div._type == DwtListView.TYPE_HEADER_ITEM) {
		if(this._headerList[div._itemIndex]._sortable) {
			div.className = div.id != this._currentColId ? "DwtListView-Column" : "DwtListView-Column DwtListView-ColumnActive"
		}
	}else if (div._type == DwtListView.TYPE_HEADER_SASH) {
		div.style.cursor = "auto";
	}
	return true;
}

ZaListView.prototype._mouseUpAction =
function(ev, div) {
	if (ev.button == DwtMouseEvent.LEFT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.SELECTION)) {
			this._selEv.field = ev.target.id.substring(0, 3);
			this._evtMgr.notifyListeners(DwtEvent.SELECTION, this._selEv);
		}
	} else if (ev.button == DwtMouseEvent.RIGHT) {
		if (this._evtMgr.isListenerRegistered(DwtEvent.ACTION)) {
			this._actionEv.field = ev.target.id.substring(0, 3);
			this._evtMgr.notifyListeners(DwtEvent.ACTION, this._actionEv);
		}
	}
	return true;
}

ZaListView.prototype._sortColumn = 
function(columnItem, bSortAsc) {
	if (bSortAsc) {
		this._list.sort(ZaItem.compareNamesAsc);
	} else {
    	this._list.sort(ZaItem.compareNamesDesc);
	}
	this.setUI();
}

ZaListView.prototype._getFieldId =
function(item, field, prfx) {
	return item ? (this._getViewPrefix() + prfx + item.id) : "";
}

ZaListView.prototype._columnClicked =
function(clickedCol, ev) {
	
	if (this.getList().size() > 0) {
		if (this.getList().size() > 1 && this._headerList[clickedCol._itemIndex]._sortable==true) {
			var item = this._headerList[clickedCol._itemIndex];		
			// reset order by sorting preference
			this._bSortAsc = (item._id == this._currentColId) ? !this._bSortAsc : this._getDefaultSortbyForCol(item);		
			this._setSortedColStyle(item._id);
			this._sortColumn(item, this._bSortAsc);
			this._currentColId = item._id;			
		}
	}
}

ZaListView.prototype._getParentForColResize = 
function() {
	// overload me to return a higher inheritance chain parent
	return this.parent;
}

function ZaListHeaderItem(idPrefix, label, iconInfo, width, sortable, sortField, resizeable, visible) {
	DwtListHeaderItem.call(this, idPrefix, label, iconInfo, width, sortable, resizeable, visible);
	this._sortField = sortField;	
	this._initialized = false;
}

ZaListHeaderItem.prototype = new DwtListHeaderItem;
ZaListHeaderItem.prototype.constructor = ZaListHeaderItem;


ZaListHeaderItem.prototype.getSortField = 
function() {
	return this._sortField;
}

ZaListHeaderItem.prototype.getLabel = 
function () {
	return this._label;
}
