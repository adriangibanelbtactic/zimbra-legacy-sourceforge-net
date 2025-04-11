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
* Creates a new search with the given properties.
* @constructor
* @class
* This class represents a search to be performed on the server. It has properties for
* the different search parameters that may be used. It can be used for a regular search,
* or to search within a conv. The results are returned via a callback.
*
* @param appCtxt					[ZmAppCtxt]		the app context
* @param query						[string]		query string
* @param types						[AjxVector]		item types to search for
* @param sortBy						[constant]*		sort order
* @param offset						[int]*			starting point within result set
* @param limit						[int]*			number of results to return
* @param contactSource				[constant]*		where to search for contacts (GAL or personal)
* @param isGalAutocompleteSearch	[boolean]*		if true, autocomplete against GAL
* @param lastId						[int]*			ID of last item displayed (for pagination)
* @param lastSortVal				[string]*		value of sort field for above item
* @param fetch						[boolean]*		if true, fetch first hit message
* @param searchId					[int]*			ID of owning search folder (if any)
* @param conds						[array]*		list of search conditions (SearchCalendarResourcesRequest)
* @param attrs						[array]*		list of attributes to return (SearchCalendarResourcesRequest)
* @param field						[string]*		field to search within (instead of default)
* @param soapInfo					[object]*		object with method, namespace, and response fields for creating soap doc
*/
ZmSearch = function(appCtxt, params) {

	this._appCtxt = appCtxt;

	if (params) {
		this.query						= params.query;
		this.types						= params.types;
		this.sortBy						= params.sortBy;
		this.offset						= params.offset;
		this.limit						= params.limit;
		this.contactSource				= params.contactSource;
		this.isGalAutocompleteSearch	= params.isGalAutocompleteSearch;
		this.lastId						= params.lastId;
		this.lastSortVal				= params.lastSortVal;
		this.endSortVal					= params.endSortVal;
		this.fetch 						= params.fetch;
		this.markRead       			= params.markRead;
		this.searchId					= params.searchId;
		this.galType					= params.galType ? params.galType : ZmSearch.GAL_ACCOUNT;
		this.conds						= params.conds;
		this.join						= params.join ? params.join : ZmSearch.JOIN_AND;
		this.attrs						= params.attrs;
		this.userText					= params.userText;
		this.field						= params.field;
		this.soapInfo					= params.soapInfo;
		
		if (this.query)
			this._parseQuery();
	}
	this.isGalSearch = false;
	this.isCalResSearch = false;
};

// Search types
ZmSearch.TYPE = {};
ZmSearch.TYPE[ZmItem.NOTE]		= "note";
ZmSearch.TYPE_ANY				= "any";

ZmSearch.GAL_ACCOUNT	= "account";
ZmSearch.GAL_RESOURCE	= "resource";
ZmSearch.GAL_ALL		= "";

ZmSearch.JOIN_AND	= 1;
ZmSearch.JOIN_OR	= 2;

ZmSearch.TYPE_MAP = {};

// Sort By
var i = 1;
ZmSearch.DATE_DESC 	= i++;
ZmSearch.DATE_ASC 	= i++;
ZmSearch.SUBJ_DESC 	= i++;
ZmSearch.SUBJ_ASC 	= i++;
ZmSearch.NAME_DESC 	= i++;
ZmSearch.NAME_ASC 	= i++;
ZmSearch.SCORE_DESC = i++;
ZmSearch.DURATION_DESC	= i++; 
ZmSearch.DURATION_ASC	= i++;

ZmSearch.SORT_BY = {};
ZmSearch.SORT_BY[ZmSearch.DATE_DESC] 	= "dateDesc";
ZmSearch.SORT_BY[ZmSearch.DATE_ASC] 	= "dateAsc";
ZmSearch.SORT_BY[ZmSearch.SUBJ_DESC] 	= "subjDesc";
ZmSearch.SORT_BY[ZmSearch.SUBJ_ASC] 	= "subjAsc";
ZmSearch.SORT_BY[ZmSearch.NAME_DESC] 	= "nameDesc";
ZmSearch.SORT_BY[ZmSearch.NAME_ASC] 	= "nameAsc";
ZmSearch.SORT_BY[ZmSearch.SCORE_DESC]	= "scoreDesc";
ZmSearch.SORT_BY[ZmSearch.DURATION_DESC]= "durDesc";
ZmSearch.SORT_BY[ZmSearch.DURATION_ASC]	= "durAsc";

ZmSearch.SORT_BY_MAP = {};
for (var i in ZmSearch.SORT_BY)
	ZmSearch.SORT_BY_MAP[ZmSearch.SORT_BY[i]] = i;

ZmSearch.FOLDER_QUERY_RE = new RegExp('^in:\\s*"?(' + ZmOrganizer.VALID_PATH_CHARS + '+)"?\\s*$', "i");
ZmSearch.TAG_QUERY_RE = new RegExp('^tag:\\s*"?(' + ZmOrganizer.VALID_NAME_CHARS + '+)"?\\s*$', "i");
ZmSearch.UNREAD_QUERY_RE = new RegExp('\\bis:\\s*(un)?read\\b', "i");
ZmSearch.IS_ANYWHERE_QUERY_RE = new RegExp('\\bis:\\s*anywhere\\b', "i");

ZmSearch.prototype.toString =
function() {
	return "ZmSearch";
};

/**
* Creates a SOAP request that represents this search and sends it to the server.
*
* @param callback		[AjxCallback]*		callback to run when response is received
* @param errorCallback	[AjxCallback]*		callback to run if there is an exception
* @param batchCmd		[ZmBatchCommand]*	batch command that contains this request
* @param timeout		[int]*				timeout value (in seconds)
* @param noBusyOverlay	[boolean]*			if true, don't use the busy overlay
*/
ZmSearch.prototype.execute =
function(params) {

	this.isGalSearch = (this.contactSource && (this.contactSource == ZmSearchToolBar.FOR_GAL_MI));
	this.isCalResSearch = (this.conds != null);
	if (!this.query && !this.isCalResSearch) return;

	var soapDoc;
	if (this.isGalSearch) {
		soapDoc = AjxSoapDoc.create("SearchGalRequest", "urn:zimbraAccount");
		var method = soapDoc.getMethod();
		if (this.galType) {
			method.setAttribute("type", this.galType);
		}
		soapDoc.set("name", this.query);
	} else if (this.isGalAutocompleteSearch) {
		soapDoc = AjxSoapDoc.create("AutoCompleteGalRequest", "urn:zimbraAccount");
		var method = soapDoc.getMethod();
		method.setAttribute("limit", ZmContactList.AC_MAX);
		soapDoc.set("name", this.query);
	} else if (this.isCalResSearch) {
		soapDoc = AjxSoapDoc.create("SearchCalendarResourcesRequest", "urn:zimbraAccount");
		var method = soapDoc.getMethod();
		if (this.attrs)
			method.setAttribute("attrs", this.attrs.join(","));
		var searchFilterEl = soapDoc.set("searchFilter");
		if (this.conds && this.conds.length) {
			var condsEl = soapDoc.set("conds", null, searchFilterEl);
			if (this.join == ZmSearch.JOIN_OR) {
				condsEl.setAttribute("or", 1);
			}
			for (var i = 0; i < this.conds.length; i++) {
				var cond = this.conds[i];
				var condEl = soapDoc.set("cond", null, condsEl);
				condEl.setAttribute("attr", cond.attr);
				condEl.setAttribute("op", cond.op);
				condEl.setAttribute("value", cond.value);
			}
		}
	} else {
		if (this.soapInfo) {
			soapDoc = AjxSoapDoc.create(this.soapInfo.method, this.soapInfo.namespace);
		} else {
			soapDoc = AjxSoapDoc.create("SearchRequest", "urn:zimbraMail");
		}
		var method = this._getStandardMethod(soapDoc);
		if (this.types) {
			var a = this.types.getArray();
			if (a.length) {
				var typeStr = [];
				for (var i = 0; i < a.length; i++)
					typeStr.push(ZmSearch.TYPE[a[i]]);
				method.setAttribute("types", typeStr.join(","));
				// special handling for showing participants ("To" instead of "From")
				if (this.folderId == ZmFolder.ID_SENT || this.folderId == ZmFolder.ID_DRAFTS || this.folderId == ZmFolder.ID_OUTBOX)
					method.setAttribute("recip", "1");
				// if we're prefetching the first hit message, also mark it as read
				if (this.fetch)
					method.setAttribute("fetch", "1");
				if (this.markRead)
					method.setAttribute("read", "1");
			}
		}
	}
	
	var respCallback = new AjxCallback(this, this._handleResponseExecute,
						[this.isGalSearch, this.isGalAutocompleteSearch, this.isCalResSearch, params.callback]);
	
	var execFrame = new AjxCallback(this, this.execute, params);
	if (params.batchCmd) {
		params.batchCmd.addRequestParams(soapDoc, respCallback, execFrame);
	} else {
		this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true, callback: respCallback,
													  errorCallback: params.errorCallback, execFrame: execFrame,
													  timeout: params.timeout, noBusyOverlay: params.noBusyOverlay});
	}
};

/*
* Convert the SOAP response into a ZmSearchResult and pass it along.
*/
ZmSearch.prototype._handleResponseExecute = 
function(isGalSearch, isGalAutocompleteSearch, isCalResSearch, callback, result) {
	var response = result.getResponse();
	if (isGalSearch) {
		response = response.SearchGalResponse;
	} else if (isCalResSearch) {
		response = response.SearchCalendarResourcesResponse;
	} else if (isGalAutocompleteSearch) {
		response = response.AutoCompleteGalResponse;
	} else if (this.soapInfo) {
		response = response[this.soapInfo.response];
	} else {
		response = response.SearchResponse;
	}
	var searchResult = new ZmSearchResult(this._appCtxt, this);
	searchResult.set(response, this.contactSource);
	result.set(searchResult);
	
	if (callback) {
		callback.run(result);
	}
};

// searching w/in a conv (to get its messages) has its own special command
ZmSearch.prototype.getConv = 
function(cid, callback, getFirstMsg) {
	if (!this.query || !cid) return;

	var soapDoc = AjxSoapDoc.create("SearchConvRequest", "urn:zimbraMail");
	var method = this._getStandardMethod(soapDoc);
	method.setAttribute("cid", cid);
	if (getFirstMsg !== false) {
		method.setAttribute("fetch", "1");	// fetch content of first msg
		method.setAttribute("read", "1");	// mark that msg read
		if (this._appCtxt.get(ZmSetting.VIEW_AS_HTML)) {
			method.setAttribute("html", "1");
		}
	}
	var respCallback = new AjxCallback(this, this._handleResponseGetConv, callback);
	this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc, asyncMode: true, callback: respCallback});
};

ZmSearch.prototype._handleResponseGetConv = 
function(callback, result) {
	response = result.getResponse().SearchConvResponse;
	var searchResult = new ZmSearchResult(this._appCtxt, this);
	searchResult.set(response, null, true);
	result.set(searchResult);
	
	if (callback) {
		callback.run(result);
	}
};

/**
* Returns a title that summarizes this search.
*/
ZmSearch.prototype.getTitle =
function() {
	var where = null;
	if (this.folderId) {
		var folder = this._appCtxt.getById(this.folderId);
		if (folder)
			where = folder.getName(true, ZmOrganizer.MAX_DISPLAY_NAME_LENGTH, true);
	} else if (this.tagId) {
			where = this._appCtxt.getById(this.tagId).getName(true, ZmOrganizer.MAX_DISPLAY_NAME_LENGTH, true);
	}
	var title = where ? [ZmMsg.zimbraTitle, where].join(": ") : 
						[ZmMsg.zimbraTitle, ZmMsg.searchResults].join(": ");
	return title;
};

ZmSearch.prototype._getStandardMethod = 
function(soapDoc) {

	var method = soapDoc.getMethod();

	if (this.sortBy)
		method.setAttribute("sortBy", ZmSearch.SORT_BY[this.sortBy]);

	// bug 5771: add timezone and locale info
	ZmTimezone.set(soapDoc, AjxTimezone.DEFAULT, null);
	soapDoc.set("locale", AjxEnv.DEFAULT_LOCALE, null);

	if (this.lastId != null && this.lastSortVal) {
		// cursor is used for paginated searches
		var cursor = soapDoc.set("cursor");
		cursor.setAttribute("id", this.lastId);
		cursor.setAttribute("sortVal", this.lastSortVal);
		if (this.endSortVal)
			cursor.setAttribute("endSortVal", this.endSortVal);
	}

	this.offset = this.offset || 0;
	method.setAttribute("offset", this.offset);

	// always set limit (init to user pref for page size if not provided)
	this.limit = this.limit || ((this.contactSource && this.types.size() == 1)
			? this._appCtxt.get(ZmSetting.CONTACTS_PER_PAGE)
			: this._appCtxt.get(ZmSetting.PAGE_SIZE));
	method.setAttribute("limit", this.limit);

	// and of course, always set the query
	soapDoc.set("query", this.query);

	// set search field if provided
	if (this.field)
		method.setAttribute("field", this.field);

	return method;
};

/**
* Parse simple queries so we can do basic matching on new items (determine whether
* they match this search query). The following types of queries are handled:
*
*    in:foo
*    tag:foo
*
* which may result in this.folderId or this.tagId getting set.
*/
ZmSearch.prototype._parseQuery =
function() {
	var results = this.query.match(ZmSearch.FOLDER_QUERY_RE);
	if (results) {
		var path = results[1].toLowerCase();
		// first check if it's a system folder (name in query string may not match actual name)
		for (var id in ZmFolder.QUERY_NAME) {
			if (ZmFolder.QUERY_NAME[id] == path) {
				this.folderId = id;
			}
		}
		// now check all folders by name
		if (!this.folderId) {
			var folders = this._appCtxt.getFolderTree();
			var folder = folders ? folders.getByPath(path) : null;
			if (folder) {
				this.folderId = folder.id;
			}
		}
	}
	results = this.query.match(ZmSearch.TAG_QUERY_RE);
	if (results) {
		var name = results[1].toLowerCase();
		var tag = this._appCtxt.getTagTree().getByName(name);
		if (tag) {
			this.tagId = tag.id;
		}
	}
	this.hasUnreadTerm = ZmSearch.UNREAD_QUERY_RE.test(this.query);
	this.isAnywhere = ZmSearch.IS_ANYWHERE_QUERY_RE.test(this.query);
};

ZmSearch.prototype.hasFolderTerm =
function(path) {
	if (!path) return false;
	var regEx = new RegExp('\\s*in:\\s*"?(' + AjxStringUtil.regExEscape(path) + ')"?\\s*', "i");
	var regExNot = new RegExp('(-|not)\\s*in:\\s*"?(' + AjxStringUtil.regExEscape(path) + ')"?\\s*', "i");
	return (regEx.test(this.query) && !regExNot.test(this.query));
};

ZmSearch.prototype.replaceFolderTerm =
function(oldPath, newPath) {
	if (!(oldPath && newPath)) return;
	var regEx = new RegExp('(\\s*in:\\s*"?)(' + AjxStringUtil.regExEscape(oldPath) + ')("?\\s*)', "gi");
	this.query = this.query.replace(regEx, "$1" + newPath + "$3");
};

ZmSearch.prototype.hasTagTerm =
function(name) {
	if (!name) return false;
	var regEx = new RegExp('\\s*tag:\\s*"?(' + AjxStringUtil.regExEscape(name) + ')"?\\s*', "i");
	var regExNot = new RegExp('(-|not)\\s*tag:\\s*"?(' + AjxStringUtil.regExEscape(name) + ')"?\\s*', "i");
	return (regEx.test(this.query) && !regExNot.test(this.query));
};

ZmSearch.prototype.replaceTagTerm =
function(oldName, newName) {
	if (!(oldName && newName)) return;
	var regEx = new RegExp('(\\s*tag:\\s*"?)(' + AjxStringUtil.regExEscape(oldName) + ')("?\\s*)', "gi");
	this.query = this.query.replace(regEx, "$1" + newName + "$3");
};
