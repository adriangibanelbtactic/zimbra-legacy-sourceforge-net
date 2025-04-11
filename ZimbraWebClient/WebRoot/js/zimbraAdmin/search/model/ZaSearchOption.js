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
* @class ZaSearchOption
* @contructor ZaSearchOption
* Provides the data model and UI items for the advanced search options
* @author Charles Cao
**/
function ZaSearchOption(app) {
	if(app)
		this._app = app;
}

ZaSearchOption.ID = 100;
ZaSearchOption.BASIC_TYPE_ID = ZaSearchOption.ID ++ ;
ZaSearchOption.OBJECT_TYPE_ID = ZaSearchOption.ID ++ ;
ZaSearchOption.DOMAIN_ID = ZaSearchOption.ID ++ ;
ZaSearchOption.SERVER_ID = ZaSearchOption.ID ++ ;
//ZaSearchOption.REMOVE_ID = ZaSearchOption.ID ++ ;

//ZaSearchOption.A_basic_query = ZaSearch.A_query ;
ZaSearchOption.A_basic_uid = ZaAccount.A_uid ;
//ZaSearchOption.A_basic_cn =  "cn" ;
ZaSearchOption.A_basic_sn =  "sn" ;
ZaSearchOption.A_basic_displayName = ZaAccount.A_displayname ;
ZaSearchOption.A_basic_zimbraId = ZaItem.A_zimbraId ;
//ZaSearchOption.A_basic_mail = ZaAccount.A_mail ;
ZaSearchOption.A_basic_status = ZaAccount.A_accountStatus ;

ZaSearchOption.A_objTypeAccount = "option_" + ZaSearch.ACCOUNTS ;
ZaSearchOption.A_objTypeDl = "option_" + ZaSearch.DLS ;
ZaSearchOption.A_objTypeAlias = "option_" + ZaSearch.ALIASES;
ZaSearchOption.A_objTypeResource = "option_" + ZaSearch.RESOURCES;
//ZaSearchOption.A_objTypeDomain = "option_" + ZaSearch.DOMAINS ;

//ZaSearchOption.A_domainAll = "option_domain_all";
ZaSearchOption.A_domainFilter = "option_domain_filter";
ZaSearchOption.A_domainList = "option_domain_list" ;
ZaSearchOption.A_domainListChecked = "option_domain_list_checked";

//ZaSearchOption.A_serverAll = "option_server_all" ;
//ZaSearchOption.A_serverFilter = "option_server_filter";
ZaSearchOption.A_serverList = "option_server_list" ;
ZaSearchOption.A_serverListChecked = "option_server_list_checked";

ZaSearchOption.accountStatusChoices = [
		{value:ZaAccount.ACCOUNT_STATUS_ACTIVE, label:ZaAccount._ACCOUNT_STATUS[ZaAccount.ACCOUNT_STATUS_ACTIVE]}, 
		{value:ZaAccount.ACCOUNT_STATUS_CLOSED, label:ZaAccount._ACCOUNT_STATUS[ZaAccount.ACCOUNT_STATUS_CLOSED]},
		{value:ZaAccount.ACCOUNT_STATUS_LOCKED, label: ZaAccount._ACCOUNT_STATUS[ZaAccount.ACCOUNT_STATUS_LOCKED]},
		{value:ZaAccount.ACCOUNT_STATUS_LOCKOUT, label: ZaAccount._ACCOUNT_STATUS[ZaAccount.ACCOUNT_STATUS_LOCKOUT]},
		{value:ZaAccount.ACCOUNT_STATUS_MAINTENANCE, label:ZaAccount._ACCOUNT_STATUS[ZaAccount.ACCOUNT_STATUS_MAINTENANCE]}
	];
	
ZaSearchOption.getObjectTypeXModel = 
function (optionId){
	var xmodel = {
		items: []
	}
	
	var basicItems = [
			//{id: ZaSearchOption.A_basic_query, ref: "options/" + ZaSearchOption.A_basic_query, type: _STRING_},
			{id: ZaSearchOption.A_basic_uid, ref: "options/" + ZaSearchOption.A_basic_uid, type: _STRING_},
			//{id: ZaSearchOption.A_basic_cn, ref: "options/" + ZaSearchOption.A_basic_cn, type: _STRING_},
			{id: ZaSearchOption.A_basic_sn, ref: "options/" + ZaSearchOption.A_basic_sn, type: _STRING_},
			{id: ZaSearchOption.A_basic_displayName, ref: "options/" + ZaSearchOption.A_basic_displayName, type: _STRING_},
			{id: ZaSearchOption.A_basic_zimbraId, ref: "options/" + ZaSearchOption.A_basic_zimbraId, type: _STRING_},
			//{id: ZaSearchOption.A_basic_mail, ref: "options/" + ZaSearchOption.A_basic_mail, type: _STRING_}
			{id: ZaSearchOption.A_basic_status, ref: "options/" + ZaSearchOption.A_basic_status, type: _STRING_}
		];
		
	
	var objTypeItems = [
			{id: ZaSearchOption.A_objTypeAccount, ref: "options/" + ZaSearchOption.A_objTypeAccount, type: _STRING_},
			{id: ZaSearchOption.A_objTypeDl, ref: "options/" + ZaSearchOption.A_objTypeDl, type: _STRING_},
			{id: ZaSearchOption.A_objTypeAlias, ref: "options/" + ZaSearchOption.A_objTypeAlias, type: _STRING_},
			{id: ZaSearchOption.A_objTypeResource, ref: "options/" + ZaSearchOption.A_objTypeResource, type: _STRING_},
			{id: ZaSearchOption.A_objTypeDomain, ref: "options/" + ZaSearchOption.A_objTypeDomain, type: _STRING_}
		
		];
		
	var domainItems = [	
			//{id: ZaSearchOption.A_domainAll, ref: "options/" + ZaSearchOption.A_domainAll, type: _STRING_},
			{id: ZaSearchOption.A_domainFilter, ref: "options/" + ZaSearchOption.A_domainFilter, type: _STRING_},
			{id: ZaSearchOption.A_domainListChecked, ref: "options/" + ZaSearchOption.A_domainListChecked, type:_LIST_},
			{id: ZaSearchOption.A_domainList, ref: "options/" + ZaSearchOption.A_domainList, type:_LIST_}
		];
	
	var serverItems = [
			//{id: ZaSearchOption.A_serverAll, ref: "options/" + ZaSearchOption.A_serverAll, type: _STRING_},
			//{id: ZaSearchOption.A_serverFilter, ref: "options/" + ZaSearchOption.A_serverFilter, type: _STRING_},
			{id: ZaSearchOption.A_serverListChecked, ref: "options/" + ZaSearchOption.A_serverListChecked, type:_LIST_},
			{id: ZaSearchOption.A_serverList, ref: "options/" + ZaSearchOption.A_serverList, type:_LIST_}		
		];
	
	if (optionId == ZaSearchOption.OBJECT_TYPE_ID) { 
		xmodel.items = objTypeItems ; 
	}else if (optionId == ZaSearchOption.DOMAIN_ID) {
		xmodel.items = domainItems;
	}else if (optionId == ZaSearchOption.SERVER_ID) {
		xmodel.items = serverItems;
	}else if (optionId == ZaSearchOption.BASIC_TYPE_ID) {
		xmodel.items = basicItems ;
	}
	
	return xmodel ;
}

ZaSearchOption.getObjectTypeXForm = 
function (optionId, height){
	var xform = {
			numCols:2, width: 150, cssClass: "ZaSearchOptionOverview",
			cssStyle: "margin-top: 30px;", 
			items: []
	}
	
	var basicItems = [
		/*{ type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_query,
			label: ZaMsg.search_option_query, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions
		 },*/
		 { type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_uid,
			label: ZaMsg.search_option_uid, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions,
		  	toolTipContent: ZaMsg.tt_search_option_uid
		 },
		 /*
		 { type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_cn,
			label: ZaMsg.search_option_cn, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions
		 },*/
		 { type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_sn,
			label: ZaMsg.search_option_sn, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_displayName,
			label: ZaMsg.search_option_displayName, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _TEXTFIELD_, ref:  ZaSearchOption.A_basic_zimbraId,
			label: ZaMsg.search_option_zimbraId, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.handleOptions
			
		 },
		 { type:_OSELECT1_, ref:ZaSearchOption.A_basic_status, editable:false, 
		 	msgName:ZaMsg.NAD_AccountStatus,label:ZaMsg.NAD_AccountStatus, 
		 	labelLocation:_LEFT_, choices:ZaSearchOption.accountStatusChoices,
		 	onChange: ZaSearchBuilderController.handleOptions
		 }
	
	];
	
	var objTypeItems = [
		{ type: _CHECKBOX_, ref:  ZaSearchOption.A_objTypeAccount,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.SearchFilter_Accounts, 
			align: _LEFT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _CHECKBOX_, ref:  ZaSearchOption.A_objTypeDl,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.SearchFilter_DLs, 
			align: _LEFT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _CHECKBOX_, ref:  ZaSearchOption.A_objTypeAlias,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.SearchFilter_Aliases, 
			align: _LEFT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _CHECKBOX_, ref:  ZaSearchOption.A_objTypeResource,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.SearchFilter_Resources, 
			align: _RIGHT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 }/** Hide the domain search for now,
		 { type: _CHECKBOX_, ref:  ZaSearchOption.A_objTypeDomain,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.SearchFilter_Domains, 
			align: _RIGHT_, labelLocation:_RIGHT_, 
			relevant : "form.parent._controller._numberOfDomainOptions <= 0 ",
			//relevantBehavior : _DISABLE_,
			onChange: ZaSearchBuilderController.handleOptions
		 } **/
	] ;
	
	var domainItems = [
	/*
		{ type: _CHECKBOX_, ref:  ZaSearchOption.A_domainAll,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.search_option_all_domain, 
			align: _RIGHT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		{ type: _SEPARATOR_ , width: 150 },*/
		{ type: _TEXTFIELD_, ref:  ZaSearchOption.A_domainFilter,
			label: ZaMsg.search_option_filter, align: _LEFT_, width: ZaSearchOptionView.WIDTH - 50, 
		  	toolTipContent: ZaMsg.tt_domain_search_option_filter,
			onChange: ZaSearchBuilderController.filterDomains
		 },
		 {type: _GROUP_, width: ZaSearchOptionView.WIDTH, colSpan: "*", height: height - 30 - 25, 
		 	cssStyle: "overflow:auto; position:absolute;",
		 	items :[
				 {type: _DWT_LIST_, ref: ZaSearchOption.A_domainList,  width: ZaSearchOptionView.WIDTH - 2, height: height - 30 - 25,  
					 forceUpdate: true, widgetClass: ZaOptionList, 
					 multiselect: true, preserveSelection: true, 
					 onSelection: ZaSearchBuilderController.filterSelectionListener
				 }
		 	]
		 }
	];
		
	var serverItems = [
	/*
		{ type: _CHECKBOX_, ref:  ZaSearchOption.A_serverAll,
			trueValue:"TRUE", falseValue:"FALSE",
			label: ZaMsg.search_option_all_server, 
			align: _RIGHT_, labelLocation:_RIGHT_, 
			onChange: ZaSearchBuilderController.handleOptions
		 },
		 { type: _SEPARATOR_ , width: 150 },
		{ type: _TEXTFIELD_, ref:  ZaSearchOption.A_serverFilter,
			label: ZaMsg.search_option_filter, align: _LEFT_, width: 100, 
			onChange: ZaSearchBuilderController.filterServers
		 },*/
		 
		 {type: _GROUP_, width: ZaSearchOptionView.WIDTH, colSpan: "*", height: height - 30, 
		 	cssStyle: "overflow:auto; position:absolute;",
		 	items :[
				 {type: _DWT_LIST_, ref: ZaSearchOption.A_serverList,  width: ZaSearchOptionView.WIDTH - 2, height: height - 30,  	
					 forceUpdate: true, widgetClass: ZaOptionList, 
					 multiselect: true, preserveSelection: true, 
					 onSelection: ZaSearchBuilderController.filterSelectionListener
				 }
		 	]
		 }
	];
	
	
	if (optionId == ZaSearchOption.OBJECT_TYPE_ID) { 
		xform.items = objTypeItems ; 
	}else if (optionId == ZaSearchOption.DOMAIN_ID) {
		xform.items = domainItems;
	}else if (optionId == ZaSearchOption.SERVER_ID) {
		xform.items = serverItems;
	}else if (optionId == ZaSearchOption.BASIC_TYPE_ID) {
		xform.items = basicItems ;
		xform.width = ZaSearchOptionView.BASIC_OPTION_WIDTH ;
	}
	
	return xform ;
}

ZaSearchOption.getDefaultInstance =
function (optionId) {
	var optionInstance = {} ;
	optionInstance["options"] = {} ;
	
	if (optionId == ZaSearchOption.OBJECT_TYPE_ID) { 
		optionInstance["options"][ZaSearchOption.A_objTypeAccount] = "TRUE" ;
		optionInstance["options"][ZaSearchOption.A_objTypeAlias] = "TRUE" ;
		optionInstance["options"][ZaSearchOption.A_objTypeDl] = "TRUE" ;
		optionInstance["options"][ZaSearchOption.A_objTypeResource] = "TRUE" ;
		//optionInstance["options"][ZaSearchOption.A_objTypeDomain] = "FALSE" ;
	}else if (optionId == ZaSearchOption.DOMAIN_ID) {
		//optionInstance["options"][ZaSearchOption.A_domainAll] = "TRUE" ;
	}else if (optionId == ZaSearchOption.SERVER_ID) {
		//optionInstance["options"][ZaSearchOption.A_serverAll] = "TRUE" ;
	}else if (optionId == ZaSearchOption.BASIC_TYPE_ID) {
		//no default value
	}
	
	return optionInstance ;
}

ZaSearchOption.getDefaultObjectTypes =
function () {
	var searchTypes = [];
	searchTypes[0]= [ZaSearch.ACCOUNTS, ZaSearch.ALIASES, ZaSearch.DLS,  ZaSearch.RESOURCES]
	return searchTypes ;
}

/////////////////////////////////////////////////////////////////////////////////////
//the list view for the domain and server filter
function ZaOptionList (parent, app, className) {
	//DwtListView.call(this, parent, null, Dwt.STATIC_STYLE);
	DwtListView.call(this, parent, null, Dwt.ABSOLUTE_STYLE);
}

ZaOptionList.prototype = new DwtListView;
ZaOptionList.prototype.constructor = ZaOptionList;

ZaOptionList.prototype.toString = 
function() {
	return "ZaOptionList";
}

ZaOptionList.prototype._createItemHtml =
function(item) {
	var html = new Array(10);
	var	div = document.createElement("div");
	div[DwtListView._STYLE_CLASS] = "Row";
	div[DwtListView._SELECTED_STYLE_CLASS] = div[DwtListView._STYLE_CLASS] + "-" + DwtCssStyle.SELECTED;
	div.className = div[DwtListView._STYLE_CLASS];
	this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);
	
	var idx = 0;
	html[idx++] = "<table width='100%' cellspacing='0' cellpadding='0'><tr><td width=20>"
	html[idx++] = "<input type=checkbox value='" + item + "' /></td>" ;
	html[idx++] = "<td>"+ item + "</td></tr></table>";
	
	div.innerHTML = html.join("");
	return div;
}

