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

function ZaClusteredServicesListView (parent, app) {
	ZaServicesListView.call(this, parent,app, "ZaClusterServicesListView");
	this.setMultiSelect(false);
}

ZaClusteredServicesListView.prototype = new ZaServicesListView;
ZaClusteredServicesListView.prototype.constructor = ZaClusteredServicesListView;

ZaClusteredServicesListView.prototype._getHeaderList = function() {

	var headerList = [
					  new ZaListHeaderItem(ZaStatus.PRFX_Server, ZaMsg.CSLV_col_prfx_clusterServerName, null, 185, false, null,
										   true, true),

					  new ZaListHeaderItem("ZaStatus.physicalServer",ZaMsg.CSLV_col_prfx_physicalServer, null, 150, true, 
										   "physicalServerName", true, true),

					  new ZaListHeaderItem("ZaStatus.clusterName", ZaMsg.CSLV_col_prfx_clusterName, null, 80, false, null, true, true),

					  new ZaListHeaderItem(ZaStatus.PRFX_Service, ZaMsg.CSLV_col_prfx_service, null, 70, false, null, true, true),

					  new ZaListHeaderItem(ZaStatus.PRFX_Time, ZaMsg.CSLV_col_prfx_lastChecked, null, null, false, null, true, true)
					  ];
	
	return headerList;
};

ZaClusteredServicesListView.STYLE_CLASS = "Row";
ZaClusteredServicesListView.SELECTED_STYLE_CLASS = "Row" + "-" + DwtCssStyle.SELECTED;

ZaClusteredServicesListView.prototype._createItemHtml = function(item, now, isDndIcon, prevItem) {
	var html = new Array(50);
	var	div = this.getDocument().createElement("div");
	this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);

	var idx = 0;
	div._styleClass = ZaClusteredServicesListView.STYLE_CLASS;
	div._selectedStyleClass = ZaClusteredServicesListView.SELECTED_STYLE_CLASS;
	div.className = ZaClusteredServicesListView.STYLE_CLASS;
	
	idx = this._writeElement(html, idx, item, false);
	if (item.services != null) {
		for (var i = 0; i < item.services.length; ++i) {
			idx = this._writeElement(html, idx, item.services[i], true);
		}
	}
	div.innerHTML = html.join("");

	return div;

};

ZaClusteredServicesListView.prototype._writeElement = function (html, idx, item, onlyServiceInfo) {
	html[idx++] = "<table ";
	if (onlyServiceInfo) {
		html[idx++] = "class='ZaClusterServicesListView_table'";
	} else {
		html[idx++] = "class='ZaClusterServicesListView_server_table'";
	}

	html[idx++] = "_serviceInfo=";
	html[idx++] = onlyServiceInfo;
	html[idx++] = ">";
	html[idx++] = "<tr>";
	var cnt = this._headerList.length;
	for(var i = 0; i < cnt; i++) {
		var id = this._headerList[i]._id;
		if (id.indexOf("ZaStatus.physicalServer")==0){
			if (onlyServiceInfo) {
				html[idx++] = "<td width=";
				html[idx++] = this._headerList[i]._width;
				html[idx++] = " aligh=left>";

				html[idx++] = AjxStringUtil.htmlEncode(" ");
				html[idx++] = "</td>";
			} else {
				html[idx++] = "<td width=";
				html[idx++] = (this._headerList[i]._width);
				html[idx++] = "><table cellpadding=0 cellspacing=0 border=0 style='table-layout:fixed;'>";
				html[idx++] = "<tr>";

				html[idx++] = "<td align=left width=20>"
				if(item.nodeStatus == 1) {
					html[idx++] = AjxImg.getImageHtml("Check");
				} else if (item.nodeStatus == 0){
					html[idx++] = AjxImg.getImageHtml("Cancel");
				} else {
					html[idx++] = "&nbsp;";
				}
				html[idx++] = "</td>";


				html[idx++] = "<td width=";
				html[idx++] = this._headerList[i]._width-12;
				html[idx++] = " aligh=left>";
				html[idx++] = AjxStringUtil.htmlEncode(item.physicalServerName);
				html[idx++] = "</td>";
				html[idx++] = "</tr></table></td>";
			}
		} else if(id.indexOf(ZaStatus.PRFX_Server)==0) {
			if (!onlyServiceInfo) {
				html[idx++] = "<td width=";
				html[idx++] = (this._headerList[i]._width);
				html[idx++] = "><table cellpadding=0 cellspacing=0 border=0 style='table-layout:fixed;'>";
				html[idx++] = "<tr>";

				html[idx++] = "<td width=\"12px\" aligh=left onclick=\'javascript:AjxCore.objectWithId(";
				html[idx++] = this.__internalId;
				html[idx++] = ")._expand(event, this)\'>";
				html[idx++] = AjxImg.getImageHtml("NodeExpanded");
				html[idx++] = "</td>";
				html[idx++] = "<td>";
				if (item.serverName != ZaClusterStatus.NOT_APPLICABLE) {
					html[idx++] = AjxStringUtil.htmlEncode(item.serverName);
					if (item.clusterStatus != null && item.clusterStatus != "started") {
						html[idx++] = "&nbsp;(";
						html[idx++] = item.clusterStatus;
						html[idx++] = ")";
					}
				} else {
					html[idx++] = AjxStringUtil.htmlEncode(item.physicalServerName);
				}
				html[idx++] = "</td>";
				html[idx++] = "</tr></table></td>";
			} else {
				html[idx++] = "<td width=";
				html[idx++] = (this._headerList[i]._width);
				html[idx++] = " aligh=left>";
				html[idx++] = AjxStringUtil.htmlEncode(" ");
				html[idx++] = "</td>";
			}

		} else if (id.indexOf("ZaStatus.clusterName")==0) {
			html[idx++] = "<td width=";
			html[idx++] = this._headerList[i]._width;
			html[idx++] = " aligh=left>"
			if (onlyServiceInfo) {
				html[idx++] = "&nbsp;";
			} else {
				html[idx++] = AjxStringUtil.htmlEncode(item.clusterName);
			}
			html[idx++] = "</td>";
		} else if(id.indexOf(ZaStatus.PRFX_Service)==0) {
			if (onlyServiceInfo) {
				html[idx++] = "<td width=";
				html[idx++] = this._headerList[i]._width;
				html[idx++] = " ><table cellpadding=0 cellspacing=0 border=0><tr><td width=20>";
				if(item.status==1) {
					html[idx++] = AjxImg.getImageHtml("Check");
				} else {
					html[idx++] = AjxImg.getImageHtml("Cancel");
				}				
				html[idx++] = "</td><td>";
				html[idx++] = AjxStringUtil.htmlEncode(item.serviceName);
				html[idx++] = "</td></tr></table></td>";
			} else {
				html[idx++] = "<td width=";
				html[idx++] = this._headerList[i]._width;
				html[idx++] = " aligh=left>";

				html[idx++] = AjxStringUtil.htmlEncode(" ");
				html[idx++] = "</td>";
			}

		} else if(id.indexOf(ZaStatus.PRFX_Time)==0) {
			html[idx++] = "<td width=";
			html[idx++] = this._headerList[i]._width;
			html[idx++] = " aligh=left>";
			if (onlyServiceInfo){
				html[idx++] = AjxStringUtil.htmlEncode(item.time);
			} else {
				html[idx++] = AjxStringUtil.htmlEncode(" ");
			}
			html[idx++] = "</td>";
		}
	}
	html[idx++] = "</tr></table>";
	return idx;
};

ZaClusteredServicesListView.prototype._expand = function (event, domObj) {
	var ev = DwtUiEvent.getEvent(event);
	var htmlEl = DwtUiEvent.getTarget(ev);
	var table = htmlEl;
	while (table != null){
		if (table.getAttribute("_serviceInfo") != null) {
			break;
		}
		table = table.parentNode;
	}
	var sibling = table.nextSibling;
	var collapse = true;
	if (sibling != null) {
		if (sibling.style.display == "none"){
			domObj.firstChild.className = AjxImg.getClassForImage("NodeExpanded");
			collapse = false;
		} else {
			domObj.firstChild.className = AjxImg.getClassForImage("NodeCollapsed");
		}
		while (sibling != null && sibling.getAttribute("_serviceInfo") == "true") {
			if (collapse){
				sibling.style.display = "none";
			} else {
				sibling.style.display = "";
			}
			sibling = sibling.nextSibling;
		}
	} else {
		domObj.firstChild.className = "";
	}
	
};
