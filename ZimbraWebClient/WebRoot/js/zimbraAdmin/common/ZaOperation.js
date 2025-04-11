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
* @class ZaOperation
* @contructor
* simplified version of ZmOperation
* This class encapsulates the properties of an action that can be taken on some item: image, caption, description, AjxListener
* @param caption string
* @param tt string
* @param img string path to image
* @param lsnr AjxListener
**/

ZaOperation = function(id, caption, tooltip, imgId, disImgId, lsnr, type, menuOpList, className, labelId) {
	this.id = id;
	this.caption = caption;
	this.tt = tooltip;
	this.listener = lsnr;
	this.imageId = imgId;
	this.disImageId = disImgId;
	this.type = (type == null)? ZaOperation.TYPE_BUTTON: type;
	this.menuOpList = menuOpList;
	this.className = className;	
	this.labelId = labelId;
}

ZaOperation.prototype.toString = 
function() {
		return "ZaOperation";
}

// types
ZaOperation.TYPE_BUTTON = 1;
ZaOperation.TYPE_MENU = 2;
ZA_OP_INDEX = 0;

// Operations
ZaOperation.NONE = ++ZA_OP_INDEX;		// no operations or menu items
ZaOperation.SEP = ++ZA_OP_INDEX;		// separator
ZaOperation.NEW = ++ZA_OP_INDEX;
ZaOperation.DELETE = ++ZA_OP_INDEX;
ZaOperation.REFRESH = ++ZA_OP_INDEX;
ZaOperation.EDIT = ++ZA_OP_INDEX;
ZaOperation.CHNG_PWD = ++ZA_OP_INDEX;
ZaOperation.CLOSE = ++ZA_OP_INDEX;
ZaOperation.SAVE = ++ZA_OP_INDEX;
ZaOperation.NEW_WIZARD = ++ZA_OP_INDEX;
ZaOperation.PAGE_FORWARD = ++ZA_OP_INDEX;
ZaOperation.PAGE_BACK = ++ZA_OP_INDEX;
ZaOperation.DUPLICATE = ++ZA_OP_INDEX;
ZaOperation.GAL_WIZARD = ++ZA_OP_INDEX;
ZaOperation.AUTH_WIZARD =++ZA_OP_INDEX;
ZaOperation.VIEW_MAIL =++ZA_OP_INDEX;
ZaOperation.MOVE_ALIAS = ++ZA_OP_INDEX;
ZaOperation.NEW_MENU = ++ZA_OP_INDEX;
ZaOperation.HELP = ++ZA_OP_INDEX;
ZaOperation.REINDEX_MAILBOX = ++ZA_OP_INDEX;
ZaOperation.LABEL = ++ZA_OP_INDEX;
ZaOperation.VIEW = ++ZA_OP_INDEX;
ZaOperation.SEARCH_ACCOUNTS = ++ZA_OP_INDEX;
ZaOperation.SEARCH_ALIASES = ++ZA_OP_INDEX;
ZaOperation.SEARCH_DLS = ++ZA_OP_INDEX;
ZaOperation.SEARCH_DOMAINS = ++ZA_OP_INDEX;
ZaOperation.SEARCH_RESOURCES = ++ZA_OP_INDEX;
ZaOperation.SEARCH_COSES = ++ZA_OP_INDEX;
ZaOperation.SEARCH_ALL = ++ZA_OP_INDEX;
ZaOperation.FLUSH = ++ZA_OP_INDEX;
ZaOperation.HOLD = ++ZA_OP_INDEX;
ZaOperation.REQUEUE = ++ZA_OP_INDEX;
ZaOperation.RELEASE = ++ZA_OP_INDEX;
ZaOperation.INIT_NOTEBOOK = ++ZA_OP_INDEX;
ZaOperation.SEARCH_RESULT_COUNT = ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_ADDESS_TYPE = ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_DOMAIN = ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_SERVER = ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_BASIC =  ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_REMOVE_ALL =  ++ZA_OP_INDEX;
ZaOperation.SEARCH_BY_ADVANCED = ++ZA_OP_INDEX;
ZaOperation.DEPLOY_ZIMLET =  ++ZA_OP_INDEX;
//ZaOperation.ENABLE_ZIMLET =  ++ZA_OP_INDEX;
//ZaOperation.DISABLE_ZIMLET =  ++ZA_OP_INDEX;
ZaOperation.CLOSE_TAB = ++ZA_OP_INDEX;
ZaOperation.CLOSE_OTHER_TAB = ++ZA_OP_INDEX;
ZaOperation.CLOSE_ALL_TAB = ++ZA_OP_INDEX;
ZaOperation.TOGGLE = ++ZA_OP_INDEX;