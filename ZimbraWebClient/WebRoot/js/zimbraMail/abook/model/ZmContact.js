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
* @param id			[int]				unique ID
* @param list		[ZmContactList]		list that contains this contact
*/
function ZmContact(appCtxt, id, list) {
	
	var contactList = appCtxt.getApp(ZmZimbraMail.CONTACTS_APP).getContactList();
	list = list ? list : contactList;
	ZmItem.call(this, appCtxt, ZmItem.CONTACT, id, list);

	this.attr = new Object();
	// handle to canonical list (for contacts that are part of search results)
	if (!list.isCanonical && !list.isGal)
		this.canonicalList = contactList;

	this.isGal = this.list.isGal;

	this.participants = new AjxVector(); // XXX: need to populate this guy (see ZmConv)
	this._evt = new ZmEvent(ZmEvent.S_CONTACT);
}

ZmContact.prototype = new ZmItem;
ZmContact.prototype.constructor = ZmContact;

// fields
ZmContact.F_assistantPhone	= "assistantPhone";
ZmContact.F_callbackPhone	= "callbackPhone";
ZmContact.F_carPhone		= "carPhone";
ZmContact.F_company			= "company";
ZmContact.F_companyPhone	= "companyPhone";
ZmContact.F_email			= "email";
ZmContact.F_email2			= "email2";
ZmContact.F_email3			= "email3";
ZmContact.F_fileAs			= "fileAs";
ZmContact.F_firstName		= "firstName";
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
ZmContact.F_workCity		= "workCity";
ZmContact.F_workCountry		= "workCountry";
ZmContact.F_workFax			= "workFax";
ZmContact.F_workPhone		= "workPhone";
ZmContact.F_workPhone2		= "workPhone2";
ZmContact.F_workPostalCode	= "workPostalCode";
ZmContact.F_workState		= "workState";
ZmContact.F_workStreet		= "workStreet";
ZmContact.F_workURL			= "workURL";

// extra fields
ZmContact.X_firstLast		= "firstLast";
ZmContact.X_fullName		= "fullName";

// file as
var i = 1;
ZmContact.FA_LAST_C_FIRST			= i++;
ZmContact.FA_FIRST_LAST 			= i++;
ZmContact.FA_COMPANY 				= i++;
ZmContact.FA_LAST_C_FIRST_COMPANY	= i++;
ZmContact.FA_FIRST_LAST_COMPANY		= i++;
ZmContact.FA_COMPANY_LAST_C_FIRST	= i++;
ZmContact.FA_COMPANY_FIRST_LAST		= i++;

ZmContact.F_EMAIL_FIELDS = [ZmContact.F_email, ZmContact.F_email2, ZmContact.F_email3];

ZmContact.prototype.toString = 
function() {
	return "ZmContact: id = " + this.id + " fullName = " + this.getFullName();
}

// Class methods

/**
* Creates a contact from an XML node.
*
* @param node		a "cn" XML node
* @param args		args to pass to the constructor
*/
ZmContact.createFromDom =
function(node, args) {
	var contact = new ZmContact(args.appCtxt, node.id, args.list);
	contact._loadFromDom(node);
	contact._resetCachedFields();
	args.list._updateEmailHash(contact, true);

	return contact;
}

/**
* Compares two contacts based on how they are filed. Intended for use by
* sort methods.
*
* @param a		a contact
* @param b		a contact
*/
ZmContact.compareByFileAs =
function(a, b) {
	if (a.getFileAs(true) > b.getFileAs(true)) return 1;
	if (a.getFileAs(true) < b.getFileAs(true)) return -1;
	return 0;
}

/**
* Figures out the filing string for the contact according to the chosen method.
*
* @param attr		a set of contact attributes
*/
ZmContact.computeFileAs =
function(contact) {
	var attr = contact.getAttrs ? contact.getAttrs() : contact;
	var val = parseInt(attr.fileAs);

	var fa = new Array();
	var idx = 0;

	switch (val) {
		case ZmContact.FA_LAST_C_FIRST: /* Last, First */
		default:
			// if full name is provided (i.e. GAL contacts) then use it 
			if (attr.fullName)
				return attr.fullName;
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.lastName && attr.firstName) fa[idx++] = ", ";
			if (attr.firstName) fa[idx++] = attr.firstName;
			break;
		case ZmContact.FA_FIRST_LAST: /* First Last */
			if (attr.firstName) fa[idx++] = attr.firstName;
			if (attr.lastName && attr.firstName) fa[idx++] = " ";
			if (attr.lastName) fa[idx++] = attr.lastName;
			break;
		case ZmContact.FA_COMPANY: /* Company */
			if (attr.company) fa[idx++] = attr.company;
			break;
		case ZmContact.FA_LAST_C_FIRST_COMPANY: /* Last, First (Company) */
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.lastName && attr.firstName) fa[idx++] = ", ";
			if (attr.firstName) fa[idx++] = attr.firstName;
			if (attr.company) {
				if (attr.lastName || attr.firstName) fa[idx++] = " ";
				fa[idx++] = "(";
				fa[idx++] = attr.company;
				fa[idx++] = ")";
			}
			break;
		case ZmContact.FA_FIRST_LAST_COMPANY: /* First Last (Company) */
			if (attr.firstName) fa[idx++] = attr.firstName;		
			if (attr.lastName && attr.firstName) fa[idx++] = " ";
			if (attr.lastName) fa[idx++] = attr.lastName;
			if (attr.company) {
				if (attr.lastName || attr.firstName) fa[idx++] = " ";
				fa[idx++] = "(";
				fa[idx++] = attr.company;
				fa[idx++] = ")";
			}			
			break;
		case ZmContact.FA_COMPANY_LAST_C_FIRST: /* Company (Last,  First) */
			if (attr.company) fa[idx++] = attr.company;
			if (attr.lastName || attr.firstName) {
				fa[idx++] = " (";
				if (attr.lastName) fa[idx++] = attr.lastName;
				if (attr.lastName && attr.firstName) fa[idx++] = ", ";				
				if (attr.firstName) fa[idx++] = attr.firstName;
				fa[idx++] = ")";
			}
			break;
		case ZmContact.FA_COMPANY_FIRST_LAST: /* Company (First Last) */
			if (attr.company) fa[idx++] = attr.company;
			if (attr.lastName || attr.firstName) {
				fa[idx++] = " (";
				if (attr.firstName) fa[idx++] = attr.firstName;				
				if (attr.lastName && attr.firstName) fa[idx++] = " ";
				if (attr.lastName) fa[idx++] = attr.lastName;
				fa[idx++] = ")";
			}
			break;
	}
	return fa.join("");
}

// Public methods

ZmContact.prototype.getAttr =
function(name) {
	if (!this.list) return null;

	if (this.list.isCanonical || this.list.isGal) {
		return this.attr[name];
	} else {
		return this.canonicalList.getById(this.id).attr[name];
	}
}

ZmContact.prototype.setAttr =
function(name, value) {
	if (!this.list) return;

	if (this.list.isCanonical || this.list.isGal) {
		this.attr[name] = value;
	} else {
		this.canonicalList.getById(this.id).attr[name] = value;
	}
}

ZmContact.prototype.removeAttr =
function(name) {
	if (!this.list) return;

	if (this.list.isCanonical || this.list.isGal) {
		delete this.attr[name];
	} else {
		delete this.canonicalList.getById(this.id).attr[name];
	}
}

ZmContact.prototype.getAttrs =
function() {
	return this.canonicalList ? this.canonicalList.getById(this.id).attr : this.attr;
}

/**
* Creates a contact from the given set of attributes. Used to create contacts on
* the fly (rather than by loading them). This method is called by a list's create()
* method; in our case that list is the canonical list of contacts.
*
* If this is a GAL contact, we assume it is being added to the contact list.
*
* @param attr		attr/value pairs for this contact
*/
ZmContact.prototype.create =
function(attr) {
	DBG.println(AjxDebug.DBG1, "ZmContact.create");

	var soapDoc = AjxSoapDoc.create("CreateContactRequest", "urn:zimbraMail");
	var cn = soapDoc.set("cn");
	
	for (var name in attr) {
		var a = soapDoc.set("a", attr[name], cn);
		a.setAttribute("n", name);
	}
	
	var ac = this._appCtxt.getAppController();
	var resp = ac.sendRequest(soapDoc).CreateContactResponse;
	cn = resp ? resp.cn[0] : null;
	var id = cn ? cn.id : null;
	if (id) {
		this._fileAs = null;
		this._fullName = null;
		this.id = id;
		this.modified = cn.md;
		this.folderId = ZmFolder.ID_CONTACTS;
		for (var a in attr) {
			if (!(attr[a] == undefined || attr[a] == ''))
				this.setAttr(a, attr[a]);
		}
		
		ac.setStatusMsg(ZmMsg.contactCreated);
	} else {
		var msg = ZmMsg.errorCreateContact + " " + ZmMsg.errorTryAgain + "\n" + ZmMsg.errorContact;
		ac.setStatusMsg(msg, ZmStatusView.LEVEL_CRITICAL);
	}
}

/**
* Updates contact attributes.
*
* @param attr		set of attributes and their new values
*/
ZmContact.prototype.modify =
function(attr) {
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
	
	for (var name in attr) {
		var a = soapDoc.set("a", attr[name], cn);
		a.setAttribute("n", name);
	}		
	
	var ac = this._appCtxt.getAppController();
//	ac.setActionedIds([this.id]);
	var resp = ac.sendRequest(soapDoc).ModifyContactResponse;
	cn = resp ? resp.cn[0] : null;
	var id = cn ? cn.id : null;
	var details = null;
	
	if (id && id == this.id) {
		this.modified = cn.md;
		var oldFileAs = this.getFileAs();
		var oldFullName = this.getFullName();
		this._resetCachedFields();
		var oldAttr = new Object();
		for (var a in attr) {
			oldAttr[a] = this.getAttr(a);
			if (attr[a] == undefined || attr[a] == '') {
				this.removeAttr(a);
			} else {
				this.setAttr(a, attr[a]);
			}
		}
		details = {attr: attr, oldAttr: oldAttr, fullNameChanged: this.getFullName() != oldFullName,
					   fileAsChanged: this.getFileAs() != oldFileAs, contact: this};

		ac.setStatusMsg(ZmMsg.contactModify);
	} else {
		var msg = ZmMsg.errorModifyContact + " " + ZmMsg.errorTryAgain + "\n" + ZmMsg.errorContact;
		ac.setStatusMsg(msg, ZmStatusView.LEVEL_CRITICAL);
	}
	
	return details;
}

/**
* Sets this contacts email address.
*
* @param email		an ZmEmailAddress, or an email string
*/
ZmContact.prototype.initFromEmail =
function(email) {
	if (email instanceof ZmEmailAddress) {
		this.setAttr(ZmContact.F_email, email.getAddress());
		this._initFullName(email);
	} else {
		this.setAttr(ZmContact.F_email, email);
	}
}

ZmContact.prototype.initFromPhone = 
function(phone) {
	this.setAttr(ZmContact.F_companyPhone, phone);
}

ZmContact.prototype.getEmail =
function() {
	for (var i = 0; i < ZmContact.F_EMAIL_FIELDS.length; i++) {
		var value = this.getAttr(ZmContact.F_EMAIL_FIELDS[i]);
		if (value)
			return value;
	}
	return null;
}

// returns a list (array) of all valid emails for this contact
ZmContact.prototype.getEmails = 
function() {
	var emails = new Array();
	for (var i = 0; i < ZmContact.F_EMAIL_FIELDS.length; i++) {
		var value = this.getAttr(ZmContact.F_EMAIL_FIELDS[i]);
		if (value)
			emails.push(value);
	}
	return emails;
}

/**
* Returns the full name.
*/
ZmContact.prototype.getFullName =
function() {
	// update/null if modified
	if (!this._fullName) {
		var fn = new Array();
		var idx = 0;
		var first = this.getAttr(ZmContact.F_firstName);
		var middle = this.getAttr(ZmContact.F_middleName);
		var last = this.getAttr(ZmContact.F_lastName);
		if (first) fn[idx++] = first;
		if (middle) fn[idx++] = middle;
		if (last) fn[idx++] = last;
		this._fullName = fn.join(" ");
	}
	return this._fullName;
}

/**
* Returns HTML for a tool tip for this contact.
*/
ZmContact.prototype.getToolTip =
function(email) {
	// update/null if modified
	if (!this._toolTip || this._toolTipEmail != email) {
		var html = new Array();
		var idx = 0;
		var entryTitle = this.getFileAs();
		html[idx++] = "<table cellpadding=0 cellspacing=0 border=0>";
		html[idx++] = "<tr><td colspan=2 valign=top>";
		html[idx++] = "<div style='border-bottom: 1px solid black;'>";
		html[idx++] = "<table cellpadding=0 cellspacing=0 border=0 width=100%>";
		html[idx++] = "<tr valign='center'>";
		html[idx++] = "<td><b>" + AjxStringUtil.htmlEncode(entryTitle) + "</b></td>";
		html[idx++] = "<td align='right'>";
		html[idx++] = AjxImg.getImageHtml("Contact"); // could use different icon if GAL
		html[idx++] = "</td>";
		html[idx++] = "</table></div>";
		html[idx++] = "</td></tr>";
		idx = this._addEntryRow("fullName", null, html, idx);
		idx = this._addEntryRow("jobTitle", null, html, idx);
		idx = this._addEntryRow("company", null, html, idx);
		idx = this._addEntryRow("workPhone", null, html, idx);	
		idx = this._addEntryRow("email", email, html, idx);
		html[idx++] = "</table>";
		this._toolTip = html.join("");
		this._toolTipEmail = email;
	}
	return this._toolTip;
}

/**
* Returns the filing string for this contact, computing it if necessary.
*/
ZmContact.prototype.getFileAs =
function(lower) {
	// update/null if modified
	if (!this._fileAs) {
		this._fileAs = ZmContact.computeFileAs(this);
		this._fileAsLC = this._fileAs.toLowerCase();
	}
	return lower === true ? this._fileAsLC : this._fileAs;
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
	var fa = parseInt(attrs.fileAs);
	
	var val = new Array();
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
}

ZmContact.prototype.getWorkAddrField = 
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.workStreet, attrs.workCity, attrs.workState, attrs.workPostalCode, attrs.workCountry);
}

ZmContact.prototype.getHomeAddrField = 
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.homeStreet, attrs.homeCity, attrs.homeState, attrs.homePostalCode, attrs.homeCountry);
}

ZmContact.prototype.getOtherAddrField = 
function(instance) {
	var attrs = this.getAttrs();
	return this._getAddressField(attrs.otherStreet, attrs.otherCity, attrs.otherState, attrs.otherPostalCode, attrs.otherCountry);
}

ZmContact.prototype._getAddressField = 
function(street, city, state, zipcode, country) {
	if (street == null && city == null && state == null && zipcode == null && country == null) return null;
	
	var html = new Array();
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
}

// IM presence
ZmContact.prototype.hasIMProfile =
function() {
	return (this.id % 3) > 0;
}

// IM presence
ZmContact.prototype.isIMAvailable =
function() {
	return (this.id % 3) == 2;
}

// Sets the full name based on an email address.
ZmContact.prototype._initFullName =
function(email) {
	var name = email.getName();
	if (name && name.length) {
		this._setFullName(name, [" "]);
	} else {
		name = email.getAddress();
		if (name && name.length) {
			var i = name.indexOf("@");
			if (i == -1) return;
			name = name.substr(0, i);
			this._setFullName(name, [".", "_"]);
		}
	}
}

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
}

// Adds a row to the tool tip.
ZmContact.prototype._addEntryRow =
function(field, data, html, idx) {
	if (data == null) {
		data = field == "fullName" ? this.getFullName() : this.getAttr(field);	
	}
	if (data != null && data != "") {
		html[idx++] = "<tr valign=top>";
		html[idx++] = "<td align=right style='white-space:nowrap; padding-right:5px;'><b>";
		html[idx++] = AjxStringUtil.htmlEncode(ZmContact._AB_FIELD[field]) + ":";
		html[idx++] = "</b></td>";
		html[idx++] = "<td style='white-space:nowrap;'>";
		html[idx++] = AjxStringUtil.htmlEncode(data);
		html[idx++] = "</td>";
		html[idx++] = "</tr>";
	}
	return idx;
}

// Reset computed fields.
ZmContact.prototype._resetCachedFields =
function() {
	this._fileAs = null;
	this._fullName = null;
	this._toolTip = null;
}

// Parse a contact node. A contact will only have attribute values if it is in the canonical list.
ZmContact.prototype._loadFromDom =
function(node) {
	this.created = node.cd;
	this.modified = node.md;
	this.folderId = node.l;
	this._parseFlags(node.f);
	this._parseTags(node.t);
	this.attr = node._attrs;
}

// these need to be kept in sync with ZmContact.F_*
ZmContact._AB_FIELD = {
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

	// work address
	workStreet: ZmMsg.AB_FIELD_workStreet,
	workCity: ZmMsg.AB_FIELD_workCity,
	workState: ZmMsg.AB_FIELD_workState,
	workPostalCode: ZmMsg.AB_FIELD_workPostalCode,
	workCountry: ZmMsg.AB_FIELD_workCountry,
	workURL: ZmMsg.AB_FIELD_workURL,

	// work phone numbers
	workPhone: ZmMsg.AB_FIELD_workPhone,
	workPhone2: ZmMsg.AB_FIELD_workPhone2,
	workFax: ZmMsg.AB_FIELD_workFax,	
	assistantPhone: ZmMsg.AB_FIELD_assistantPhone,
	companyPhone: ZmMsg.AB_FIELD_companyPhone,
	callbackPhone: ZmMsg.AB_FIELD_callbackPhone,
	
	// home address
	homeStreet: ZmMsg.AB_FIELD_homeStreet,
	homeCity: ZmMsg.AB_FIELD_homeCity,
	homeState: ZmMsg.AB_FIELD_homeState,
	homePostalCode: ZmMsg.AB_FIELD_homePostalCode,
	homeCountry: ZmMsg.AB_FIELD_homeCountry,
	homeURL: ZmMsg.AB_FIELD_homeURL,

	// home phone numbers
	homePhone: ZmMsg.AB_FIELD_homePhone,
	homePhone2: ZmMsg.AB_FIELD_homePhone2,
	homeFax: ZmMsg.AB_FIELD_homeFax,
	mobilePhone: ZmMsg.AB_FIELD_mobilePhone,
	pager: ZmMsg.AB_FIELD_pager,
	carPhone: ZmMsg.AB_FIELD_carPhone,
	
	// other address
	otherStreet: ZmMsg.AB_FIELD_otherStreet,
	otherCity: ZmMsg.AB_FIELD_otherCity,
	otherState: ZmMsg.AB_FIELD_otherState,
	otherPostalCode: ZmMsg.AB_FIELD_otherPostalCode,
	otherCountry: ZmMsg.AB_FIELD_otherCountry,
	otherURL: ZmMsg.AB_FIELD_otherURL,
	
	// other phone numbers
	otherPhone: ZmMsg.AB_FIELD_otherPhone,
	otherFax: ZmMsg.AB_FIELD_otherFax
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
