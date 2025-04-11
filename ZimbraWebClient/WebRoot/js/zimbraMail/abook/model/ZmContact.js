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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

/**
* Creates an empty contact.
* @constructor
* @class
* This class represents a contact (typically a person) with all its associated versions
* of email address, home and work addresses, phone numbers, etc. Contacts can be filed/sorted
* in different ways, with the default being Last, First. A contact is an item, so
* it has tagging and flagging support, and belongs to a list.
* <p>
* Most of a contact's data is kept in attributes. These include name, phone, etc. Meta-data and
* data common to items are not kept in attributes. These include flags, tags, folder, and
* modified/created dates. Since the attribute data for contacts is loaded only once, a contact
* gets its attribute values from that canonical list.</p>
*
* @param appCtxt	[ZmAppCtxt]			the app context
* @param id			[int]*				unique ID
* @param list		[ZmContactList]*	list that contains this contact
* @param type		[constant]*			item type
*/
ZmContact = function(appCtxt, id, list, type) {
	if (arguments.length == 0) return;

	// handle to canonical list (for contacts that are part of search results)
	this.canonicalList = AjxDispatcher.run("GetContacts");

	list = list || this.canonicalList;
	type = type || ZmItem.CONTACT;
	ZmItem.call(this, appCtxt, type, id, list);

	this.attr = {};
	this.isGal = (this.list && this.list.isGal);

	this.participants = new AjxVector(); // XXX: need to populate this guy (see ZmConv)
};

ZmContact.prototype = new ZmItem;
ZmContact.prototype.constructor = ZmContact;

// fields
ZmContact.F_assistantPhone	= "assistantPhone";
ZmContact.F_birthday		= "birthday";
ZmContact.F_callbackPhone	= "callbackPhone";
ZmContact.F_carPhone		= "carPhone";
ZmContact.F_company			= "company";
ZmContact.F_companyPhone	= "companyPhone";
ZmContact.F_description		= "description";
ZmContact.F_email			= "email";
ZmContact.F_email2			= "email2";
ZmContact.F_email3			= "email3";
ZmContact.F_fileAs			= "fileAs";
ZmContact.F_firstName		= "firstName";
ZmContact.F_folderId		= "folderId";
ZmContact.F_homeCity		= "homeCity";
ZmContact.F_homeCountry		= "homeCountry";
ZmContact.F_homeFax			= "homeFax";
ZmContact.F_homePhone		= "homePhone";
ZmContact.F_homePhone2		= "homePhone2";
ZmContact.F_homePostalCode	= "homePostalCode";
ZmContact.F_homeState		= "homeState";
ZmContact.F_homeStreet		= "homeStreet";
ZmContact.F_homeURL			= "homeURL";
ZmContact.F_jobTitle		= "jobTitle";
ZmContact.F_lastName		= "lastName";
ZmContact.F_middleName		= "middleName";
ZmContact.F_mobilePhone		= "mobilePhone";
ZmContact.F_namePrefix		= "namePrefix";
ZmContact.F_nameSuffix		= "nameSuffix";
ZmContact.F_nickname		= "nickname";
ZmContact.F_notes			= "notes";
ZmContact.F_otherCity		= "otherCity";
ZmContact.F_otherCountry	= "otherCountry";
ZmContact.F_otherFax		= "otherFax";
ZmContact.F_otherPhone		= "otherPhone";
ZmContact.F_otherPostalCode	= "otherPostalCode";
ZmContact.F_otherState		= "otherState";
ZmContact.F_otherStreet		= "otherStreet";
ZmContact.F_otherURL		= "otherURL";
ZmContact.F_pager			= "pager";
ZmContact.F_type			= "type";
ZmContact.F_workCity		= "workCity";
ZmContact.F_workCountry		= "workCountry";
ZmContact.F_workFax			= "workFax";
ZmContact.F_workPhone		= "workPhone";
ZmContact.F_workPhone2		= "workPhone2";
ZmContact.F_workPostalCode	= "workPostalCode";
ZmContact.F_workState		= "workState";
ZmContact.F_workStreet		= "workStreet";
ZmContact.F_workURL			= "workURL";

// IM addresses
ZmContact.F_imAddress1 = "imAddress1";
ZmContact.F_imAddress2 = "imAddress2";
ZmContact.F_imAddress3 = "imAddress3";

// Group fields
ZmContact.F_dlist			= "dlist";

// extra fields
ZmContact.X_fileAs			= "fileAs";
ZmContact.X_firstLast		= "firstLast";
ZmContact.X_fullName		= "fullName";

// GAL fields
ZmContact.GAL_MODIFY_TIMESTAMP = "modifyTimeStamp";
ZmContact.GAL_CREATE_TIMESTAMP = "createTimeStamp";

// file as
var i = 1;
ZmContact.FA_LAST_C_FIRST			= i++;
ZmContact.FA_FIRST_LAST 			= i++;
ZmContact.FA_COMPANY 				= i++;
ZmContact.FA_LAST_C_FIRST_COMPANY	= i++;
ZmContact.FA_FIRST_LAST_COMPANY		= i++;
ZmContact.FA_COMPANY_LAST_C_FIRST	= i++;
ZmContact.FA_COMPANY_FIRST_LAST		= i++;
ZmContact.FA_CUSTOM					= i++;

ZmContact.F_EMAIL_FIELDS = [ZmContact.F_email, ZmContact.F_email2, ZmContact.F_email3];
ZmContact.F_PHONE_FIELDS = [
	ZmContact.F_assistantPhone, ZmContact.F_callbackPhone, ZmContact.F_carPhone, ZmContact.F_companyPhone,
	ZmContact.F_homeFax, ZmContact.F_homePhone, ZmContact.F_homePhone2, ZmContact.F_mobilePhone,
	ZmContact.F_otherPhone, ZmContact.F_workPhone, ZmContact.F_workPhone2
];
ZmContact.F_IM_FIELDS = [ ZmContact.F_imAddress1, ZmContact.F_imAddress2, ZmContact.F_imAddress3 ];

ZmContact.prototype.toString =
function() {
//	return "ZmContact: id = " + this.id + " fullName = " + this.getFullName();
	return "ZmContact";
};

// Class methods

/**
* Creates a contact from an XML node.
*
* @param node		a "cn" XML node
* @param args		args to pass to the constructor
*/
ZmContact.createFromDom =
function(node, args) {
	// check global cache for this item first
	var contact = args.appCtxt.cacheGet(node.id);

	// make sure the revision hasnt changed, otherwise contact is out of date
	if (contact == null || (contact && contact.rev != node.rev)) {
		contact = new ZmContact(args.appCtxt, node.id, args.list);
		contact._loadFromDom(node);
	} else {
		contact.list = args.list || AjxDispatcher.run("GetContacts");
	}

	return contact;
};

/**
* Compares two contacts based on how they are filed. Intended for use by
* sort methods.
*
* @param a		[object]		a contact
* @param b		[object]		a contact
*/
ZmContact.compareByFileAs =
function(a, b) {
	var aFileAs = (a instanceof ZmContact) ? a.getFileAs(true) : ZmContact.computeFileAs(a._attrs).toLowerCase();
	var bFileAs = (b instanceof ZmContact) ? b.getFileAs(true) : ZmContact.computeFileAs(b._attrs).toLowerCase();

	if (aFileAs > bFileAs) return 1;
	if (aFileAs < bFileAs) return -1;
	return 0;
};

/**
* Figures out the filing string for the contact according to the chosen method.
*
* @param contact	[hash]		a set of contact attributes
*/
ZmContact.computeFileAs =
function(contact) {
	var attr = (contact instanceof ZmContact) ? contact.getAttrs() : contact;
	if (!attr) return;

	var val = parseInt(attr.fileAs);
	var fa = [];
	var idx = 0;

	switch (val) {
		case ZmContact.FA_LAST_C_FIRST: 										// Last, First
		default: {
			// if GAL contact, use full name instead (bug fix #4850,4009)
			if (contact && contact.isGal)
				return attr.fullName;
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.lastName && attr.firstName) fa[idx++] = ", ";
			if (attr.firstName) fa[idx++] = attr.firstName;
		}
		break;

		case ZmContact.FA_FIRST_LAST: { 										// First Last
			if (attr.firstName) fa[idx++] = attr.firstName;
			if (attr.lastName && attr.firstName) fa[idx++] = " ";
			if (attr.lastName) fa[idx++] = attr.lastName;
		}
		break;

		case ZmContact.FA_COMPANY: {											// Company
			if (attr.company) fa[idx++] = attr.company;
		}
		break;

		case ZmContact.FA_LAST_C_FIRST_COMPANY: {								// Last, First (Company)
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.lastName && attr.firstName) fa[idx++] = ", ";
			if (attr.firstName) fa[idx++] = attr.firstName;
			if (attr.company) {
				if (attr.lastName || attr.firstName) fa[idx++] = " ";
				fa[idx++] = "(";
				fa[idx++] = attr.company;
				fa[idx++] = ")";
			}
		}
		break;

		case ZmContact.FA_FIRST_LAST_COMPANY: {									// First Last (Company)
			if (attr.firstName) fa[idx++] = attr.firstName;
			if (attr.lastName && attr.firstName) fa[idx++] = " ";
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.company) {
				if (attr.lastName || attr.firstName) fa[idx++] = " ";
				fa[idx++] = "(";
				fa[idx++] = attr.company;
				fa[idx++] = ")";
			}
		}
		break;

		case ZmContact.FA_COMPANY_LAST_C_FIRST: {								// Company (Last,  First)
			if (attr.company) fa[idx++] = attr.company;
			if (attr.lastName || attr.firstName) {
				fa[idx++] = " (";
				if (attr.lastName) fa[idx++] = attr.lastName;
				if (attr.lastName && attr.firstName) fa[idx++] = ", ";
				if (attr.firstName) fa[idx++] = attr.firstName;
				fa[idx++] = ")";
			}
		}
		break;

		case ZmContact.FA_COMPANY_FIRST_LAST: {									// Company (First Last)
			if (attr.company) fa[idx++] = attr.company;
			if (attr.lastName || attr.firstName) {
				fa[idx++] = " (";
				if (attr.firstName) fa[idx++] = attr.firstName;
				if (attr.lastName && attr.firstName) fa[idx++] = " ";
				if (attr.lastName) fa[idx++] = attr.lastName;
				fa[idx++] = ")";
			}
		}
		break;

		case ZmContact.FA_CUSTOM: {												// custom looks like this: "8:foobar"
			return attr.fileAs.substring(2);
		}
		break;
	}
	return fa.join("");
};

/**
* Basically prepends "8:" to the given custom fileAs str
*
* @param contact	[hash]		a set of contact attributes
*/
ZmContact.computeCustomFileAs =
function(customFileAs) {
	return [ZmContact.FA_CUSTOM, ":", customFileAs].join("");
};

/* These next few static methods handle a contact that is either an anonymous
*  object or an actual ZmContact. The former is used to optimize loading. The
*  anonymous object is upgraded to a ZmContact when needed. */
ZmContact.getAttr =
function(contact, attr) {
	return (contact instanceof ZmContact)
		? contact.getAttr(attr)
		: (contact && contact._attrs) ? contact._attrs[attr] : null;
};

ZmContact.setAttr =
function(contact, attr, value) {
	if (contact instanceof ZmContact)
		contact.setAttr(attr, value)
	else
		contact._attrs[attr] = value;
};

ZmContact.isInTrash =
function(contact) {
	var folderId = (contact instanceof ZmContact) ? contact.folderId : contact.l;
	return (folderId == ZmFolder.ID_TRASH);
};

ZmContact.prototype.load =
function(callback, errorCallback, batchCmd) {
	var soapDoc = AjxSoapDoc.create("GetContactsRequest", "urn:zimbraMail");
	var msgNode = soapDoc.set("cn");
	msgNode.setAttribute("id", this.id);

	var respCallback = new AjxCallback(this, this._handleLoadResponse, [callback]);

	if (batchCmd) {
		batchCmd.addRequestParams(soapDoc, respCallback, errorCallback);
	} else {
		this._appCtxt.getAppController().sendRequest({soapDoc: soapDoc,
													  asyncMode: true,
													  callback: respCallback,
													  errorCallback: errorCallback});
	}
};

ZmContact.prototype._handleLoadResponse =
function(callback, result) {
	var resp = result.getResponse().GetContactsResponse;

	// for now, we just assume only one contact was requested at a time
	this.attr = resp.cn[0]._attrs;
	this.isLoaded = true;

	if (callback)
		callback.run(resp.cn[0], this);
};

ZmContact.prototype.isEmpty =
function() {
	var isEmpty = true;
	for (var i in this.attr) {
		isEmpty = false;
		break;
	}
	return isEmpty;
};

ZmContact.prototype.isShared =
function() {
	return this.addrbook && this.addrbook.link;
};

ZmContact.prototype.isReadOnly =
function() {
	if (this.isGal) return true;

	return this.isShared()
		? this.addrbook && this.addrbook.isReadOnly()
		: false;
};

ZmContact.prototype.isGroup =
function() {
	return (this.getAttr(ZmContact.F_dlist) != null || this.type == ZmItem.GROUP);
};

// parses "dlist" attr into AjxEmailAddress objects stored in 3 vectors (all, good, and bad)
ZmContact.prototype.getGroupMembers =
function() {
	return this.isGroup()
		? AjxEmailAddress.parseEmailString(this.getAttr(ZmContact.F_dlist))
		: null;
};

ZmContact.prototype.getSortVal =
function(sortBy) {
	return this.sf;
};

ZmContact.prototype.getIcon =
function() {
	var icon;
	if (this.isGal)				icon = "GALContact";
	else if (this.isShared())	icon = "SharedContact";
	else if (this.isGroup())	icon = "Group";
	else 						icon = "Contact";

	return icon;
};

ZmContact.prototype.getFolderId =
function() {
	return this.isShared()
		? this.folderId.split(":")[0]
		: this.folderId;
};

ZmContact.prototype.getAttr =
function(name) {
	if (!this.list) return null;

	if (this.list.isCanonical || this.list.isGal || this.isShared()) {
		return this.attr[name];
	} else {
		var contact = this.canonicalList.getById(this.id);
		return contact ? contact.attr[name] : null;
	}
};

ZmContact.prototype.setAttr =
function(name, value) {
	if (!this.list) return;

	if (this.list.isCanonical || this.list.isGal || this.isShared()) {
		this.attr[name] = value;
	} else {
		var contact = this.canonicalList.getById(this.id);
		if (contact) {
			contact.attr[name] = value;
		}
	}
};

ZmContact.prototype.removeAttr =
function(name) {
	if (!this.list) return;

	if (this.list.isCanonical || this.list.isGal || this.isShared()) {
		delete this.attr[name];
	} else {
		var contact = this.canonicalList.getById(this.id);
		if (contact) {
			delete contact.attr[name];
		}
	}
};

ZmContact.prototype.getAttrs =
function() {
	if (this.canonicalList && !this.isShared() && !this.list.isGal) {
		var contact = this.canonicalList.getById(this.id);
		return contact ? contact.attr : this.attr;
	} else {
		return this.attr;
	}
};

ZmContact.prototype.clear =
function() {
	// if this contact is in the canonical list, dont clear it!
	var contact = this.canonicalList.getById(this.id);
	if (contact) return;

	ZmItem.prototype.clear.call(this);
};

/**
* Creates a contact from the given set of attributes. Used to create contacts on
* the fly (rather than by loading them). This method is called by a list's create()
* method; in our case that list is the canonical list of contacts.
* <p>
* If this is a GAL contact, we assume it is being added to the contact list.</p>
*
* @param attr	[hash]		attr/value pairs for this contact
*/
ZmContact.prototype.create =
function(attr, batchCmd) {
	DBG.println(AjxDebug.DBG1, "ZmContact.create");

	var soapDoc = AjxSoapDoc.create("CreateContactRequest", "urn:zimbraMail");
	var cn = soapDoc.set("cn");

	var folderId = attr[ZmContact.F_folderId] || ZmFolder.ID_CONTACTS;
	cn.setAttribute("l", folderId);

	for (var name in attr) {
		if (name == ZmContact.F_folderId)
			continue;
		var a = soapDoc.set("a", attr[name], cn);
		a.setAttribute("n", name);
	}

	var respCallback = new AjxCallback(this, this._handleResponseCreate, [attr, batchCmd != null]);
	// TODO - handle errors(?) for create?

	if (batchCmd) {
		// TODO - add execFrame when ZmBatchCommand API is ready
		batchCmd.addRequestParams(soapDoc, respCallback);
	} else {
		var execFrame = new AjxCallback(this, this.create, [attr]);
		this._appCtxt.getAppController().sendRequest({soapDoc:soapDoc, asyncMode:true, callback:respCallback, execFrame:execFrame});
	}
};

ZmContact.prototype._handleResponseCreate =
function(attr, isBatchMode, result) {
	// dont bother processing creates when in batch mode (just let create
	// notifications handle them)
	if (isBatchMode) return;

	var resp = result.getResponse().CreateContactResponse;
	cn = resp ? resp.cn[0] : null;
	var id = cn ? cn.id : null;
	if (id) {
		this._fileAs = null;
		this._fullName = null;
		this.id = id;
		this.modified = cn.md;
		this.folderId = cn.l || ZmOrganizer.ID_ADDRBOOK;
		for (var a in attr) {
			if (!(attr[a] == undefined || attr[a] == ''))
				this.setAttr(a, attr[a]);
		}
		var msg = this.isGroup() ? ZmMsg.groupCreated : ZmMsg.contactCreated;
		this._appCtxt.getAppController().setStatusMsg(msg);
	} else {
		var msg = this.isGroup() ? ZmMsg.errorCreateGroup : ZmMsg.errorCreateContact;
		var detail = ZmMsg.errorTryAgain + "\n" + ZmMsg.errorContact;
		this._appCtxt.getAppController().setStatusMsg(msg, ZmStatusView.LEVEL_CRITICAL, detail);
	}
};

ZmContact.prototype.createFromVCard =
function(msgId, vcardPartId) {
	var soapDoc = AjxSoapDoc.create("CreateContactRequest", "urn:zimbraMail");
	var cn = soapDoc.set("cn");
	cn.setAttribute("l", ZmFolder.ID_CONTACTS);
	var vcard = soapDoc.set("vcard", null, cn);
	vcard.setAttribute("mid", msgId);
	vcard.setAttribute("part", vcardPartId);

	var respCallback = new AjxCallback(this, this._handleResponseCreateVCard);
	var errorCallback = new AjxCallback(this, this._handleErrorCreateVCard);
	var execFrame = new AjxCallback(this, this.create, [msgId, vcardPartId]);

	this._appCtxt.getAppController().sendRequest({soapDoc:soapDoc, asyncMode:true,
												callback:respCallback,
												errorCallback:errorCallback,
												execFrame:execFrame});
};

ZmContact.prototype._handleResponseCreateVCard =
function(result) {
	this._appCtxt.getAppController().setStatusMsg(ZmMsg.contactCreated);
};

ZmContact.prototype._handleErrorCreateVCard =
function(ex) {
	this._appCtxt.getAppController().setStatusMsg(ZmMsg.errorCreateContact, ZmStatusView.LEVEL_CRITICAL);
};

/**
* Updates contact attributes.
*
* @param attr		[hash]			set of attributes and their new values
* @param callback	[AjxCallback]	callback
*/
ZmContact.prototype.modify =
function(attr, callback) {
	DBG.println(AjxDebug.DBG1, "ZmContact.modify");
	if (this.list.isGal) {
		DBG.println(AjxDebug.DBG1, "Cannot modify GAL contact");
		return;
	}

	var soapDoc = AjxSoapDoc.create("ModifyContactRequest", "urn:zimbraMail");
	soapDoc.getMethod().setAttribute("replace", "0");
	// change force to 0 and put up dialog if we get a MODIFY_CONFLICT fault?
	soapDoc.getMethod().setAttribute("force", "1");
	var cn = soapDoc.set("cn");
	cn.setAttribute("id", this.id);

	var continueRequest = false;

	for (var name in attr) {
		if (name == ZmContact.F_folderId)
			continue;
		var a = soapDoc.set("a", attr[name], cn);
		a.setAttribute("n", name);
		continueRequest = true;
	}

	if (continueRequest) {
		var respCallback = new AjxCallback(this, this._handleResponseModify, [attr, callback]);
		var execFrame = new AjxCallback(this, this.modify, [attr]);
		this._appCtxt.getAppController().sendRequest({soapDoc:soapDoc, asyncMode:true, callback:respCallback, execFrame:execFrame});
	} else {
		if (attr[ZmContact.F_folderId]) {
			this._setFolder(attr[ZmContact.F_folderId]);
		}
	}
};

ZmContact.prototype._handleResponseModify =
function(attr, callback, result) {
	var resp = result.getResponse().ModifyContactResponse;
	var cn = resp ? resp.cn[0] : null;
	var id = cn ? cn.id : null;

	if (id && id == this.id) {
		this._appCtxt.setStatusMsg(this.isGroup() ? ZmMsg.groupSaved : ZmMsg.contactSaved);
		// the revision for this contact has changed -- we should refetch it
		// ONLY DO THIS FOR SHARED CONTACT since normal contacts are handled by notifications
		if (cn.rev && cn.rev != this.rev && this.isShared()) {
			var respCallback = new AjxCallback(this, this._handleResponseLoad, [true]);
			this.load(respCallback);
		} else {
			if (attr[ZmContact.F_folderId])
				this._setFolder(attr[ZmContact.F_folderId]);
		}
	} else {
		var msg = ZmMsg.errorModifyContact;
        var detail = ZmMsg.errorTryAgain + "\n" + ZmMsg.errorContact;
        this._appCtxt.getAppController().setStatusMsg(msg, ZmStatusView.LEVEL_CRITICAL, detail);
	}
	// NOTE: we no longer process callbacks here since notification handling
	//       takes care of everything
};

ZmContact.prototype._handleResponseLoad =
function(updatingShare, resp) {
	if (updatingShare) {
		this._resetCachedFields();

		this._fileAs = ZmContact.computeFileAs(this);
		if (this._fileAs)
			this._fileAsLC = this._fileAs.toLowerCase();

		// update this contact's list per old/new attrs
		var details = {attr:resp._attrs, oldAttr:null,
					   fullNameChanged:true,
					   fileAsChanged:true,
					   contact:this};
		this.list.modifyLocal(resp, details);
	}

	this._notify(ZmEvent.E_MODIFY, resp);
};

ZmContact.prototype._setFolder =
function(newFolderId) {
	if (this.folderId == newFolderId) return;

	// moving out of a share or into one is handled differently (create then hard delete)
	var newFolder = this._appCtxt.getById(newFolderId)
	if (this.isShared() || (newFolder && newFolder.link)) {
		if (this.list) {
			this.list.moveItems(this, newFolder);
		}
	} else {
		var soapDoc = AjxSoapDoc.create("ContactActionRequest", "urn:zimbraMail");
		var cn = soapDoc.set("action");
		cn.setAttribute("id", this.id);
		cn.setAttribute("op", "move");
		cn.setAttribute("l", newFolderId);

		var respCallback = new AjxCallback(this, this._handleResponseLoad, [false]);
		this._appCtxt.getAppController().sendRequest({soapDoc:soapDoc, asyncMode:true, callback:respCallback});
	}
};

ZmContact.prototype.notifyModify =
function(obj) {
	ZmItem.prototype.notifyModify.call(this, obj);

	// cache old fileAs/fullName before resetting them
	var oldFileAs = this.getFileAs();
	var oldFullName = this.getFullName();
	this._resetCachedFields();

	var oldAttrCache = {};
	if (obj._attrs) {
		// remove attrs that were not returned back from the server
		var oldAttrs = this.getAttrs();
		for (var a in oldAttrs) {
			oldAttrCache[a] = oldAttrs[a];
			if (obj._attrs[a] == null)
				this.removeAttr(a);
		}

		// set attrs returned by server
		for (var a in obj._attrs)
			this.setAttr(a, obj._attrs[a]);
	}

	var details = {attr:obj._attrs, oldAttr:oldAttrCache,
				   fullNameChanged:this.getFullName() != oldFullName,
				   fileAsChanged:this.getFileAs() != oldFileAs,
				   contact:this};

	// update this contact's list per old/new attrs
	this.list.modifyLocal(obj, details);
	this._notify(ZmEvent.E_MODIFY, obj);

	var buddy = this.getBuddy();
	if (buddy) {
		// trigger a refresh
		buddy._notifySetName(buddy.name);
	}
};

/**
* Sets this contacts email address.
*
* @param email		[object]		an AjxEmailAddress, or an email string
* @param strictName	[boolean]*		if true, don't try to set name from user portion of address
*/
ZmContact.prototype.initFromEmail =
function(email, strictName) {
	if (email instanceof AjxEmailAddress) {
		this.setAttr(ZmContact.F_email, email.getAddress());
		this._initFullName(email, strictName);
	} else {
		this.setAttr(ZmContact.F_email, email);
	}
};

ZmContact.prototype.initFromPhone =
function(phone) {
	this.setAttr(ZmContact.F_companyPhone, phone);
};

ZmContact.prototype.getEmail =
function() {
	return (this.getAttr(ZmContact.F_email) ||
			this.getAttr(ZmContact.F_email2) ||
			this.getAttr(ZmContact.F_email3));
};

ZmContact.prototype.getIMAddress = function() {
	return this.getAttr(ZmContact.F_imAddress1) ||
		this.getAttr(ZmContact.F_imAddress2) ||
		this.getAttr(ZmContact.F_imAddress3);
};

ZmContact.prototype.getBuddy = function() {
	var buddy = null;
	if (this._appCtxt.get(ZmSetting.IM_ENABLED)) {
		var roster = AjxDispatcher.run("GetRoster");
		buddy = roster.getRosterItem(this.getIMAddress());
	}
	return buddy;
};

// returns a list (array) of all valid emails for this contact
ZmContact.prototype.getEmails =
function() {
	var emails = [];
	for (var i = 0; i < ZmContact.F_EMAIL_FIELDS.length; i++) {
		var value = this.getAttr(ZmContact.F_EMAIL_FIELDS[i]);
		if (value)
			emails.push(value);
	}
	return emails;
};

/**
* Returns the full name.
*/
ZmContact.prototype.getFullName =
function() {
	if (!this._fullName) {
		var fullName = this.getAttr(ZmContact.X_fullName); // present if GAL contact
		if (fullName) {
			this._fullName = fullName;
		} else {
			var fn = [];
			var idx = 0;
			var first = this.getAttr(ZmContact.F_firstName);
			var middle = this.getAttr(ZmContact.F_middleName);
			var last = this.getAttr(ZmContact.F_lastName);
			if (first) fn[idx++] = first;
			if (middle) fn[idx++] = middle;
			if (last) fn[idx++] = last;
			this._fullName = fn.join(" ");
		}
	}

	// as a last resort, set it to fileAs
	if (!this._fullName)
		this._fullName = this.getFileAs();

	return this._fullName;
};

/**
* Returns HTML for a tool tip for this contact.
*/
ZmContact.prototype.getToolTip =
function(email, isGal) {
	// update/null if modified
//	if (!this._toolTip || this._toolTipEmail != email) {

	// IM status can change anytime so let's always rebuild the tooltip
	var buddy = this.getBuddy();
	var subs = { contact	: this,
		     entryTitle	: this.getFileAs(),
		     buddy	: buddy };
	this._toolTip = AjxTemplate.expand("zimbraMail.abook.templates.Contacts#Tooltip", subs);
	this._toolTipEmail = email;
//	}
	return this._toolTip;
};

/**
* Returns the filing string for this contact, computing it if necessary.
*/
ZmContact.prototype.getFileAs =
function(lower) {
	// update/null if modified
	if (!this._fileAs) {
		this._fileAs = ZmContact.computeFileAs(this);
		this._fileAsLC = this._fileAs ? this._fileAs.toLowerCase() : null;
	}
	return lower ? this._fileAsLC : this._fileAs;
};

ZmContact.prototype.getHeader =
function() {
	return this.id ? this.getFileAs() : ZmMsg.newContact;
};

// company field has a getter b/c fileAs may be the Company name so
// company field should return "last, first" name instead *or*
// prepend the title if fileAs is not Company (assuming it exists)
ZmContact.prototype.getCompanyField =
function() {

	var attrs = this.getAttrs();
	if (attrs == null) return null;

	var fa = parseInt(attrs.fileAs);
	var val = [];
	var idx = 0;

	if (fa == ZmContact.FA_LAST_C_FIRST || fa == ZmContact.FA_FIRST_LAST) {
		// return the title, company name
		if (attrs.jobTitle) {
			val[idx++] = attrs.jobTitle;
			if (attrs.company)
				val[idx++] = ", ";
		}
		if (attrs.company)
			val[idx++] = attrs.company;

	} else if (fa == ZmContact.FA_COMPANY) {
		// return the first/last name
		if (attrs.firstName) {
			val[idx++] = attrs.firstName;
			if (attrs.lastName)
				val[idx++] = ", ";
		}

		if (attrs.lastName)
			val[idx++] = attrs.lastName;

		if (attrs.jobTitle)
			val[idx++] = " (" + attrs.jobTitle + ")";

	} else {
		// just return the title
		if (attrs.jobTitle) {
			val[idx++] = attrs.jobTitle;
			// and/or company name if applicable
			if (attrs.company && (attrs.fileAs == null || fa == ZmContact.FA_LAST_C_FIRST || fa == ZmContact.FA_FIRST_LAST))
				val[idx++] = ", ";
		}
		if (attrs.company && (attrs.fileAs == null || fa == ZmContact.FA_LAST_C_FIRST || fa == ZmContact.FA_FIRST_LAST))
			 val[idx++] = attrs.company;
	}
	if (val.length == 0) return null;
	return val.join("");
};

ZmContact.prototype.getWorkAddrField =
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.workStreet, attrs.workCity, attrs.workState, attrs.workPostalCode, attrs.workCountry);
};

ZmContact.prototype.getHomeAddrField =
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.homeStreet, attrs.homeCity, attrs.homeState, attrs.homePostalCode, attrs.homeCountry);
};

ZmContact.prototype.getOtherAddrField =
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.otherStreet, attrs.otherCity, attrs.otherState, attrs.otherPostalCode, attrs.otherCountry);
};

ZmContact.prototype._getAddressField =
function(street, city, state, zipcode, country) {
	if (street == null && city == null && state == null && zipcode == null && country == null) return null;

	var html = [];
	var idx = 0;

	if (street) {
		html[idx++] = street;
		if (city || state || zipcode)
			html[idx++] = "\n";
	}

	if (city) {
		html[idx++] = city;
		if (state)
			html[idx++] = ", ";
		else if (zipcode)
			html[idx++] = " ";
	}

	if (state) {
		html[idx++] = state;
		if (zipcode)
			html[idx++] = " ";
	}

	if (zipcode)
		html[idx++] = zipcode;

	if (country)
		html[idx++] = "\n" + country;

	return html.join("");
};

// Sets the full name based on an email address.
ZmContact.prototype._initFullName =
function(email, strictName) {
	var name = email.getName();
	name = AjxStringUtil.trim(name.replace(AjxEmailAddress.commentPat, '')); // strip comment (text in parens)

	if (name && name.length) {
		this._setFullName(name, [" "]);
	} else if (!strictName) {
		name = email.getAddress();
		if (name && name.length) {
			var i = name.indexOf("@");
			if (i == -1) return;
			name = name.substr(0, i);
			this._setFullName(name, [".", "_"]);
		}
	}
};

// Tries to extract a set of name components from the given text, with the
// given list of possible delimiters. The first delimiter contained in the
// text will be used. If none are found, the first delimiter in the list is
// used.
ZmContact.prototype._setFullName =
function(text, delims) {
	var delim = delims[0];
	for (var i = 0; i < delims.length; i++) {
		if (text.indexOf(delims[i]) != -1) {
			delim = delims[i];
			break;
		}
	}
	var parts = text.split(delim, 3);
	this.setAttr(ZmContact.F_firstName, parts[0]);
	if (parts.length == 2) {
		this.setAttr(ZmContact.F_lastName, parts[1]);
	} else if (parts.length == 3) {
		this.setAttr(ZmContact.F_middleName, parts[1]);
		this.setAttr(ZmContact.F_lastName, parts[2]);
	}
};

// Reset computed fields.
ZmContact.prototype._resetCachedFields =
function() {
	this._fileAs = this._fileAsLC = this._fullName = this._toolTip = null;
};

// Parse contact node. A contact will only have attr values if its in canonical list.
ZmContact.prototype._loadFromDom =
function(node) {
	this.isLoaded = true;
	this.rev = node.rev;
	this.sf = node.sf;
	this.folderId = node.l;
	this.created = node.cd;
	this.modified = node.md;

	if (node.fileAsStr) {
		this._fileAs = node.fileAsStr;
		this._fileAsLC = node.fileAsStr.toLowerCase();
	}

	this.attr = node._attrs || {};

	// for shared contacts, we get these fields outside of the attr part
	if (node.email) this.attr[ZmContact.F_email] = node.email;
	if (node.email2) this.attr[ZmContact.F_email2] = node.email2;
	if (node.email3) this.attr[ZmContact.F_email3] = node.email3;

	this.type = this.attr[ZmContact.F_dlist] != null
		? ZmItem.GROUP : ZmItem.CONTACT;

	// check if the folderId is found in our address book (otherwise, we assume 
	// this contact to be a shared contact)
	this.addrbook = this._appCtxt.getById(this.folderId);

	// dont process tags/flags for shared contacts until we get server support
	if (!this.isShared()) {
		this._parseFlags(node.f);
		this._parseTags(node.t);
	} else {
		// shared contacts are never fully loaded since we never cache them
		this.isLoaded = false;
	}
};

/**
* Returns display text for an attendee. Prefers name over email.
*
* @param type		[constant]		attendee type
* @param shortForm	[boolean]*		if true, return only name or email
*/
ZmContact.prototype.getAttendeeText =
function(type, shortForm) {
	var text = "";
	var name = this.getFullName();
	var email = this.getEmail();
	if (type == ZmCalItem.PERSON && !shortForm) {
		var e = new AjxEmailAddress(email, null, name);
		text = e.toString();
	} else {
		text = name ? name : email ? email : "";
	}

	return text;
};

ZmContact.prototype.getPrintHtml =
function(preferHtml, callback) {
	return this.isGroup() ? ZmGroupView.getPrintHtml(this, false, this._appCtxt) :
							ZmContactView.getPrintHtml(this, false, this._appCtxt);
};

// these need to be kept in sync with ZmContact.F_*
ZmContact._AB_FIELD = {
	// file as info
	firstName: ZmMsg.AB_FIELD_firstName,
	lastName: ZmMsg.AB_FIELD_lastName,
	middleName: ZmMsg.AB_FIELD_middleName,
	fullName: ZmMsg.AB_FIELD_fullName,
	jobTitle: ZmMsg.AB_FIELD_jobTitle,
	company: ZmMsg.AB_FIELD_company,

	// email addresses
	email: ZmMsg.AB_FIELD_email,
	email2: ZmMsg.AB_FIELD_email2,
	email3: ZmMsg.AB_FIELD_email3,

	// IM addresses
	imAddress1: ZmMsg.AB_FIELD_imAddress1,
	imAddress2: ZmMsg.AB_FIELD_imAddress2,
	imAddress3: ZmMsg.AB_FIELD_imAddress3,

	// work address
	workStreet: ZmMsg.AB_FIELD_street,
	workCity: ZmMsg.AB_FIELD_city,
	workState: ZmMsg.AB_FIELD_state,
	workPostalCode: ZmMsg.AB_FIELD_postalCode,
	workCountry: ZmMsg.AB_FIELD_country,
	workURL: ZmMsg.AB_FIELD_URL,
	workPhone: ZmMsg.AB_FIELD_workPhone,
	workPhone2: ZmMsg.AB_FIELD_workPhone2,
	workFax: ZmMsg.AB_FIELD_workFax,
	assistantPhone: ZmMsg.AB_FIELD_assistantPhone,
	companyPhone: ZmMsg.AB_FIELD_companyPhone,
	callbackPhone: ZmMsg.AB_FIELD_callbackPhone,

	// home
	homeStreet: ZmMsg.AB_FIELD_street,
	homeCity: ZmMsg.AB_FIELD_city,
	homeState: ZmMsg.AB_FIELD_state,
	homePostalCode: ZmMsg.AB_FIELD_postalCode,
	homeCountry: ZmMsg.AB_FIELD_country,
	homeURL: ZmMsg.AB_FIELD_URL,
	homePhone: ZmMsg.AB_FIELD_homePhone,
	homePhone2: ZmMsg.AB_FIELD_homePhone2,
	homeFax: ZmMsg.AB_FIELD_homeFax,
	mobilePhone: ZmMsg.AB_FIELD_mobilePhone,
	pager: ZmMsg.AB_FIELD_pager,
	carPhone: ZmMsg.AB_FIELD_carPhone,

	// other
	otherStreet: ZmMsg.AB_FIELD_street,
	otherCity: ZmMsg.AB_FIELD_city,
	otherState: ZmMsg.AB_FIELD_state,
	otherPostalCode: ZmMsg.AB_FIELD_postalCode,
	otherCountry: ZmMsg.AB_FIELD_country,
	otherURL: ZmMsg.AB_FIELD_URL,
	otherPhone: ZmMsg.AB_FIELD_otherPhone,
	otherFax: ZmMsg.AB_FIELD_otherFax,

	// misc fields
	notes: ZmMsg.notes,
	birthday: ZmMsg.AB_FIELD_birthday
};

ZmContact._AB_FILE_AS = {
	1: ZmMsg.AB_FILE_AS_lastFirst,
	2: ZmMsg.AB_FILE_AS_firstLast,
	3: ZmMsg.AB_FILE_AS_company,
	4: ZmMsg.AB_FILE_AS_lastFirstCompany,
	5: ZmMsg.AB_FILE_AS_firstLastCompany,
	6: ZmMsg.AB_FILE_AS_companyLastFirst,
	7: ZmMsg.AB_FILE_AS_companyFirstLast
};
