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
 * Portions created by Zimbra are Copyright (C) 2007 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

ZmPersona = function(appCtxt, identity, list) {
	if (arguments.length == 0) return;
	ZmAccount.call(this, appCtxt, ZmAccount.PERSONA, identity.id, null, list);
	identity.sendFromDisplay = identity.sendFromDisplay || appCtxt.get(ZmSetting.DISPLAY_NAME);
	identity.sendFromAddress = identity.sendFromAddress || appCtxt.get(ZmSetting.USERNAME);
	this.identity = identity;
};
ZmPersona.prototype = new ZmAccount;
ZmPersona.prototype.constructor = ZmPersona;

ZmPersona.prototype.toString = function() {
	return "ZmPersona";
};

//
// Constants
//

ZmAccount.PERSONA = "PERSONA";

//
// Public methods
//

ZmPersona.prototype.setName = function(name) {
	this.getIdentity().name = name;
};

ZmPersona.prototype.getName = function() {
	return this.getIdentity().name;
};

ZmPersona.prototype.setEmail = function(email) {
	this.getIdentity().sendFromAddress = email;
};

ZmPersona.prototype.getEmail = function() {
	return this.getIdentity().sendFromAddress;
};

ZmPersona.prototype.getIdentity = function() {
	return this.identity;
};

ZmPersona.prototype.create = function(callback, errorCallback, batchCmd) {
	return this.getIdentity().create(callback, errorCallback, batchCmd);
};

ZmPersona.prototype.save = function(callback, errorCallback, batchCmd) {
	return this.getIdentity().save(callback, errorCallback, batchCmd);
};

ZmPersona.prototype.doDelete = function(callback, errorCallback, batchCmd) {
	return this.getIdentity().doDelete(callback, errorCallback, batchCmd);
};
