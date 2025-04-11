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
* @class ZaSearch
* @contructor ZaSearch
* this is a static class taht provides method for searching LDAP
* @author Greg Solovyev
**/
function ZaSearch() {
	this[ZaSearch.A_selected] = null;
	this[ZaSearch.A_query] = "";
	this[ZaSearch.A_fAliases] = "TRUE";
	this[ZaSearch.A_fAccounts] = "TRUE";	
	this[ZaSearch.A_fdistributionlists] = "TRUE";	
	this[ZaSearch.A_pagenum]=1;	
}
ZaSearch.ALIASES = "aliases";
ZaSearch.DLS = "distributionlists";
ZaSearch.ACCOUNTS = "accounts";

ZaSearch.TYPES = new Object();
ZaSearch.TYPES[ZaItem.ALIAS] = ZaSearch.ALIASES;
ZaSearch.TYPES[ZaItem.DL] = ZaSearch.DLS;
ZaSearch.TYPES[ZaItem.ACCOUNT] = ZaSearch.ACCOUNTS;


ZaSearch.A_query = "query";
ZaSearch.A_selected = "selected";
ZaSearch.A_pagenum = "pagenum";
ZaSearch.A_fAliases = "f_aliases";
ZaSearch.A_fAccounts = "f_accounts";
ZaSearch.A_fdistributionlists = "f_distributionlists";

/**
* @param app reference to ZaApp
**/
ZaSearch.getAll =
function(app) {
	return ZaSearch.search("", [ZaSearch.ALIASES,ZaSearch.DLS,ZaSearch.ACCOUNTS], 1, ZaAccount.A_uid, true, app);
}


ZaSearch.standardAttributes = AjxBuffer.concat(ZaAccount.A_displayname,",",
											   ZaItem.A_zimbraId,  "," , 
											   ZaAccount.A_mailHost , "," , 
											   ZaAccount.A_uid ,"," , 
											   ZaAccount.A_accountStatus , "," , 
											   ZaAccount.A_description, ",",
											   ZaDistributionList.A_mailStatus);


ZaSearch.search =
function(query, types, pagenum, orderby, isascending, app, attrs, limit, domainName) {
	if(!orderby) orderby = ZaAccount.A_uid;
	var myisascending = "0";
	
	if(isascending) {
		myisascending = "1";
	} 
	
	limit = (limit != null)? limit: ZaAccount.RESULTSPERPAGE;
	
	var offset = (pagenum-1) * limit;
	attrs = (attrs != null)? attrs: ZaSearch.standardAttributes;
	var soapDoc = AjxSoapDoc.create("SearchAccountsRequest", "urn:zimbraAdmin", null);
	soapDoc.set("query", query);
	if (domainName != null) {
		soapDoc.getMethod().setAttribute("domain", domainName);
	}
	soapDoc.getMethod().setAttribute("offset", offset);
	soapDoc.getMethod().setAttribute("limit", limit);
	soapDoc.getMethod().setAttribute("applyCos", "0");
	soapDoc.getMethod().setAttribute("attrs", attrs);
	soapDoc.getMethod().setAttribute("sortBy", orderby);
	soapDoc.getMethod().setAttribute("sortAscending", myisascending);
	if(types != null && types.length>0) {
		soapDoc.getMethod().setAttribute("types", types.toString());
	}
	var resp = ZmCsfeCommand.invoke(soapDoc, null, null, null, true).firstChild;
	var list = new ZaItemList(null, app);
	list.loadFromDom(resp);
	var searchTotal = resp.getAttribute("searchTotal");
	var numPages = Math.ceil(searchTotal/limit);
	return {"list":list, "numPages":numPages};
}

ZaSearch.searchByDomain = 
function (domainName, types, pagenum, orderby, isascending, app, attrs, limit) {
	return ZaSearch.search("", types, pagesnum, orderby, isascending, app, attrs, limit, domainName);
}


ZaSearch.getSearchByNameQuery =
function(n) {
	if (n == null || n == "") {
		return "";
	} else {
		return ("(|(uid=*"+n+"*)(cn=*"+n+"*)(sn=*"+n+"*)(gn=*"+n+"*)(displayName=*"+n+"*)(zimbraMailAlias=*"+n+"*)(zimbraId="+n+")(zimbraMailAddress=*"+n+"*)(zimbraMailDeliveryAddress=*"+n+"*))");
	}
}

ZaSearch.searchByQueryHolder = 
function (queryHolder, pagenum, orderby, isascending, app) {
	if(queryHolder.isByDomain) {
 		return ZaSearch.searchByDomain(queryHolder.byValAttr, queryHolder.types, pagenum, orderby, isascending, app);
	} else {
		return ZaSearch.search(queryHolder.queryString, queryHolder.types, pagenum, orderby, isascending, app, queryHolder.fetchAttrs,
							   queryHolder.limit);	
	}
}

ZaSearch.getSearchFromQuery = function (query) {
	var searchObj = new ZaSearch();
	searchObj[ZaSearch.A_selected] = null;
	searchObj[ZaSearch.A_query] = query.queryString;
	searchObj[ZaSearch.A_fAliases] = "FALSE";
	searchObj[ZaSearch.A_fAccounts] = "FALSE";
	searchObj[ZaSearch.A_fdistributionlists] = "FALSE";
	
	if (query.types != null) {
		for (var i = 0; i < query.types.length; ++i) {
			if (query.types[i] == ZaSearch.ALIASES){
				searchObj[ZaSearch.A_fAliases] = "TRUE";
			}
			if (query.types[i] == ZaSearch.ACCOUNTS){
				searchObj[ZaSearch.A_fAccounts] = "TRUE";
			}
			if (query.types[i] == ZaSearch.DLS){
				searchObj[ZaSearch.A_fdistributionlists] = "TRUE";
			}
		}
	}
	return searchObj;
};

ZaSearch.myXModel = {
	items: [
		{id:ZaSearch.A_query, type:_STRING_},
		{id:ZaSearch.A_selected, type:_OBJECT_, items:ZaAccount.myXModel},		
		{id:ZaSearch.A_fAliases, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		{id:ZaSearch.A_fdistributionlists, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		{id:ZaSearch.A_fAccounts, type:_ENUM_, choices:ZaModel.BOOLEAN_CHOICES},
		{id:ZaSearch.A_pagenum, type:_NUMBER_}
	]
}

function ZaSearchQuery (queryString, types, byDomain, byVal, attrsCommaSeparatedString, limit) {
	this.queryString = queryString;
	this.isByDomain = byDomain;
	this.byValAttr = byVal;
	this.types = types;
	this.fetchAttrs = (attrsCommaSeparatedString != null)? attrsCommaSeparatedString: ZaSearch.standardAttributes;
	this.limit = (limit != null)? limit: ZaAccount.RESULTSPERPAGE;
}
