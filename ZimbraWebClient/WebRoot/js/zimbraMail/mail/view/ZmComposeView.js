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
* Creates a new compose view. The view does not display itself on construction.
* @constructor
* @class        
* This class provides a form for composing a message.
*
* @author Conrad Damon
* @param parent			[DwtControl]		the element that created this view
* @param controller		[ZmController]		controller managing this view
* @param composeMode 	[constant]			passed in so detached window knows which mode to be in on startup
*/
ZmComposeView = function(parent, controller, composeMode) {

	DwtComposite.call(this, parent, "ZmComposeView", Dwt.ABSOLUTE_STYLE);

	ZmComposeView.ADDR_SETTING[AjxEmailAddress.BCC]	= ZmSetting.SHOW_BCC;

	this._onMsgDataChange = new AjxCallback(this, this._onMsgDataChange);

	this._appCtxt = controller._appCtxt;
	this._controller = controller;
	this._contactPickerEnabled = this._appCtxt.get(ZmSetting.CONTACTS_ENABLED) ||
								 this._appCtxt.get(ZmSetting.GAL_ENABLED);
	this._initialize(composeMode);

	// make sure no unnecessary scrollbars show up
	this.getHtmlElement().style.overflow = "hidden";
};

ZmComposeView.prototype = new DwtComposite;
ZmComposeView.prototype.constructor = ZmComposeView;


// Consts

// Consts related to compose fields
ZmComposeView.QUOTED_HDRS = [ZmMailMsg.HDR_FROM, ZmMailMsg.HDR_TO, ZmMailMsg.HDR_CC,
							 ZmMailMsg.HDR_DATE, ZmMailMsg.HDR_SUBJECT];
ZmComposeView.BAD = "_bad_addrs_";

// Message dialog placement
ZmComposeView.DIALOG_X = 50;
ZmComposeView.DIALOG_Y = 100;

// Attachment related
ZmComposeView.UPLOAD_FIELD_NAME	= "attUpload";
ZmComposeView.FORWARD_ATT_NAME	= "ZmComposeView_forAttName";
ZmComposeView.FORWARD_MSG_NAME	= "ZmComposeView_forMsgName";

// max # of attachments to show
ZmComposeView.SHOW_MAX_ATTACHMENTS = AjxEnv.is800x600orLower ? 2 : 3;
ZmComposeView.MAX_ATTACHMENT_HEIGHT = (ZmComposeView.SHOW_MAX_ATTACHMENTS * 23) + "px";

// Reply/forward stuff
ZmComposeView.EMPTY_FORM_RE = /^[\s\|]*$/;
ZmComposeView.SUBJ_PREFIX_RE = new RegExp("^\\s*(" + ZmMsg.re + "|" + ZmMsg.fwd + "|" + ZmMsg.fw + "):" + "\\s*", "i");
ZmComposeView.QUOTED_CONTENT_RE = new RegExp("^----- ", "m");
ZmComposeView.HTML_QUOTED_CONTENT_RE = new RegExp("<br>----- ", "i");
ZmComposeView.REFANG_RE = new RegExp("(<img[^>]*)dfsrc\s*=([^>]*>)", "ig");
ZmComposeView.REFANG_RE_REPLACE = "$1src=$2";

ZmComposeView.ADDR_SETTING = {}; // XXX: may not be necessary anymore?

ZmComposeView.WRAP_LENGTH = 72;

// Public methods

ZmComposeView.prototype.toString =
function() {
	return "ZmComposeView";
};

/**
* Sets the current view, based on the given action. The compose form is
* created and laid out and everything is set up for interaction with the user.
*
* @param action			[constant]		new message, reply, forward, or an invite action
* @param identity		[ZmIdentity]	the identity sending the message
* @param msg			[ZmMailMsg]*	the original message (reply/forward), or address (new message)
* @param toOverride 	[string]*		initial value for To: field
* @param subjOverride 	[string]*		initial value for Subject: field
* @param extraBodyText 	[string]*		canned text to prepend to body (invites)
*/
ZmComposeView.prototype.set =
function(params) {
	var action = this._action = params.action;
	if (this._msg) {
		this._msg.onChange = null;
	}
	var msg = this._msg = params.msg;
	if (msg) {
		msg.onChange = this._onMsgDataChange;
	}
	if (params.identity) {
		this._identitySelect.setSelectedValue(params.identity.id);
	}

	// list of msg Id's to add as attachments
	this._msgIds = params.msgIds;

	this.reset(true);

	// create attc. table EVERY time
	this._createAttachmentsContainer();

	// reset To/Cc/Bcc fields
	this._showAddressField(AjxEmailAddress.TO, true, true, true);
	this._showAddressField(AjxEmailAddress.CC, true, true, true);
	this._showAddressField(AjxEmailAddress.BCC, this._appCtxt.get(ZmSetting.SHOW_BCC), true, true);

	// populate fields based on the action and user prefs
	this._setAddresses(action, params.toOverride);
	this._setSubject(action, msg, params.subjOverride);
	this._setBody(action, msg, params.extraBodyText);
	
	this.getHtmlEditor().moveCaretToTop();

	if (action != ZmOperation.FORWARD_ATT) {
		// save extra mime parts
		var bodyParts = msg ? msg.getBodyParts() : [];
		for (var i = 0; i < bodyParts.length; i++) {
			var bodyPart = bodyParts[i];
			var contentType = bodyPart.ct;
			if (contentType != ZmMimeTable.TEXT_PLAIN && contentType != ZmMimeTable.TEXT_HTML) {
				var mimePart = new ZmMimePart();
				mimePart.setContentType(contentType);
				mimePart.setContent(bodyPart.content);
				this.addMimePart(mimePart);
			}
		}
	}

	// save form state (to check for change later)
	if (this._composeMode == DwtHtmlEditor.HTML) {
		var ta = new AjxTimedAction(this, this._setFormValue);
		AjxTimedAction.scheduleAction(ta, 10);
	} else {
		this._setFormValue();
	}
};

/**
* Called automatically by the attached ZmMailMsg object when data is
* changed, in order to support Zimlets modify subject or other values
* (bug: 10540)
*/
ZmComposeView.prototype._onMsgDataChange =
function(what, val) {
	switch (what) {
	    case "subject":
		this._subjectField.value = val;
		break;
	}
};

ZmComposeView.prototype.getComposeMode =
function() {
	return this._composeMode;
};

ZmComposeView.prototype.getController =
function() {
	return this._controller;
};

ZmComposeView.prototype.getForwardLinkHtml =
function() {
	return this._attcDiv.innerHTML;
};

ZmComposeView.prototype.getHtmlEditor =
function() {
	return this._htmlEditor;
};

ZmComposeView.prototype.getOrigMsg =
function() {
	return this._msg;
};

ZmComposeView.prototype.getTitle =
function() {
	var text;
	if (this._action == ZmOperation.REPLY)
		text = ZmMsg.reply;
	else if (this._action == ZmOperation.FORWARD_INLINE || this._action == ZmOperation.FORWARD_ATT)
		text = ZmMsg.forward;
	else
		text = ZmMsg.compose;
	return [ZmMsg.zimbraTitle, text].join(": ");
};

// returns the field values for each of the addr fields
ZmComposeView.prototype.getRawAddrFields =
function() {
	var addrs = {};
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (this._using[type]) {
			addrs[type] = this._field[type].value;
		}
	}
	return addrs;
};

// returns address fields that are currently visible
ZmComposeView.prototype.getAddrFields =
function() {
	var addrs = [];
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (this._using[type]) {
			addrs.push(this._field[type]);
		}
	}
	return addrs;
};

// returns list of attachment field values (used by detachCompose)
ZmComposeView.prototype.getAttFieldValues =
function() {
	var attList = [];
	var atts = document.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME);

	for (var i = 0; i < atts.length; i++)
		attList.push(atts[i].value);

	return attList;
};

ZmComposeView.prototype.getSendUID =
function() {
	return this._sendUID;
};

ZmComposeView.prototype.setBackupForm =
function() {
	this.backupForm = this._backupForm();
};

/**
* Saves *ALL* form value data to test against whether user made any changes
* since canceling SendMsgRequest. If user attempts to send again, we compare
* form data with this value and if not equal, send a new UID otherwise, re-use.
*/
ZmComposeView.prototype._backupForm =
function() {
	var val = this._formValue(true, true);

	// keep track of attachments as well
	var atts = document.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME);
	for (var i = 0; i < atts.length; i++) {
		if (atts[i].value.length) {
			val += atts[i].value;
		}
	}

	// keep track of "uploaded" attachments as well :/
	val += this._getForwardAttIds(ZmComposeView.FORWARD_ATT_NAME).join("");
	val += this._getForwardAttIds(ZmComposeView.FORWARD_MSG_NAME).join("");

	return val;
};

/**
* Returns the message from the form, after some basic input validation.
*/
ZmComposeView.prototype.getMsg =
function(attId, isDraft) {
	//Check destination addresses.
	var addrs = this._collectAddrs();

	// Any addresses at all provided? If not, bail.
	if (!isDraft && !addrs.gotAddress) {
		this.enableInputs(false);
    	this._msgDialog.setMessage(ZmMsg.noAddresses, DwtMessageDialog.CRITICAL_STYLE);
	    this._msgDialog.popup(this._getDialogXY());
	    this._msgDialog.registerCallback(DwtDialog.OK_BUTTON, this._okCallback, this);
		this.enableInputs(true);
	    return;
	}

	var cd = this._confirmDialog = this._appCtxt.getOkCancelMsgDialog();
	cd.reset();

	// Is there a subject? If not, ask the user if they want to send anyway.
	var subject = AjxStringUtil.trim(this._subjectField.value);
	if (!isDraft && subject.length == 0 && !this._noSubjectOkay) {
		this.enableInputs(false);
    	cd.setMessage(ZmMsg.compSubjectMissing, DwtMessageDialog.WARNING_STYLE);
		cd.registerCallback(DwtDialog.OK_BUTTON, this._noSubjectOkCallback, this);
		cd.registerCallback(DwtDialog.CANCEL_BUTTON, this._noSubjectCancelCallback, this);
	    cd.popup(this._getDialogXY());
		return;
	}

	// Any bad addresses?  If there are bad ones, ask the user if they want to send anyway.
	if (!isDraft && addrs[ZmComposeView.BAD].size() && !this._badAddrsOkay) {
		this.enableInputs(false);
	    var bad = AjxStringUtil.htmlEncode(addrs[ZmComposeView.BAD].toString(AjxEmailAddress.SEPARATOR));
	    var msg = AjxMessageFormat.format(ZmMsg.compBadAddresses, bad);
    	cd.setMessage(msg, DwtMessageDialog.WARNING_STYLE);
		cd.registerCallback(DwtDialog.OK_BUTTON, this._badAddrsOkCallback, this);
		cd.registerCallback(DwtDialog.CANCEL_BUTTON, this._badAddrsCancelCallback, this, addrs.badType);
		cd.setVisible(true); // per fix for bug 3209
		cd.popup(this._getDialogXY());
		return;
	} else {
		this._badAddrsOkay = false;
	}

	// Handle any attachments
	if (!attId && this._gotAttachments()) {
		this._submitAttachments(isDraft);
		return;
	}

	// check if this is a resend
	if (this._sendUID && this.backupForm) {
		// if so, check if user changed anything since canceling the send
		if (isDraft || this._backupForm() != this.backupForm) {
			this._sendUID = (new Date()).getTime();
		}
	} else {
		this._sendUID = (new Date()).getTime();
	}

	// get list of message part id's for any forwarded attachements
	var forwardAttIds = this._getForwardAttIds(ZmComposeView.FORWARD_ATT_NAME);
	var forwardMsgIds = this._getForwardAttIds(ZmComposeView.FORWARD_MSG_NAME);

	// --------------------------------------------
	// Passed validation checks, message ok to send
	// --------------------------------------------

	// set up message parts as necessary
	var top = new ZmMimePart();

	if (this._composeMode == DwtHtmlEditor.HTML) {
		top.setContentType(ZmMimeTable.MULTI_ALT);

		// create two more mp's for text and html content types
		var textPart = new ZmMimePart();
		textPart.setContentType(ZmMimeTable.TEXT_PLAIN);
		textPart.setContent(this._htmlEditor.getTextVersion());
		top.children.add(textPart);

		var htmlPart = new ZmMimePart();
		htmlPart.setContentType(ZmMimeTable.TEXT_HTML);
		var defangedContent = this._htmlEditor.getContent(true);
		var refangedContent = defangedContent.replace(ZmComposeView.REFANG_RE, ZmComposeView.REFANG_RE_REPLACE);
		htmlPart.setContent(refangedContent);
		top.children.add(htmlPart);
	} else {
		var textPart = this._extraParts ? new ZmMimePart() : top;
		textPart.setContentType(ZmMimeTable.TEXT_PLAIN);
		textPart.setContent(this._htmlEditor.getContent());

		if (this._extraParts) {
			top.setContentType(ZmMimeTable.MULTI_ALT);
			top.children.add(textPart);
		}
	}

	// add extra message parts
	if (this._extraParts) {
		for (var i = 0; i < this._extraParts.length; i++) {
			var mimePart = this._extraParts[i];
			top.children.add(mimePart);
		}
	}

	var msg = new ZmMailMsg(this._appCtxt);
	msg.setTopPart(top);
	msg.setSubject(subject);
	msg.setForwardAttIds(forwardAttIds);
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (addrs[type] && addrs[type].all.size() > 0)
			msg.setAddresses(type, addrs[type].all);
	}
	msg.identity = this.getIdentity();
	msg.sendUID = this._sendUID;

	// save a reference to the original message
	msg._origMsg = this._msg;
	if (this._msg && this._msg._instanceDate) {
		msg._instanceDate = this._msg._instanceDate;
	}

	if (this._action != ZmOperation.NEW_MESSAGE && !this._msgIds)
	{
		var isInviteReply = this._isInviteReply(this._action);
		if (this._action == ZmOperation.DRAFT) {
			msg.isReplied = this._msg.rt == "r";
			msg.isForwarded = this._msg.rt == "w";
			msg.isDraft = this._msg.isDraft;
			// check if we're resaving a draft that was originally a reply/forward
			if (msg.isDraft) {
				// if so, set both origId and the draft id
				msg.origId = msg.isReplied || msg.isForwarded ? this._msg.nId : null;
				msg.id = this._msg.id;
			}
		} else {
			msg.isReplied = this._action == ZmOperation.REPLY || this._action == ZmOperation.REPLY_ALL || isInviteReply;
			msg.isForwarded = this._action == ZmOperation.FORWARD_INLINE || this._action == ZmOperation.FORWARD_ATT;
			msg.origId = this._msg.nId;
		}
		msg.isInviteReply = isInviteReply;
		msg.inviteMode = isInviteReply ? this._action : null;
		msg.irtMessageId = this._msg.messageId;
		msg.folderId = this._msg.folderId;
	}

	if (attId) {
		msg.addAttachmentId(attId);
	}

	if (this._msg) {
		// replied/forw msg or draft shouldn't have att ID (a repl/forw voicemail mail msg may)
		var msgAttId = this._msg.getAttachmentId();
		if (msgAttId) {
			msg.addAttachmentId(msgAttId);
		}
	}

	if (this._msgAttId) {
		forwardMsgIds.push(this._msgAttId);
	}
	msg.setMessageAttachmentId(forwardMsgIds);

	return msg;
};

/**
* Sets an address field.
*
* @param type	the address type
* @param addr	the address string
*
* XXX: if addr empty, check if should hide field
*/
ZmComposeView.prototype.setAddress =
function(type, addr) {
	addr = addr ? addr : "";
	if (addr.length && !this._using[type]) {
		this._using[type] = true;
		this._showAddressField(type, true);
	}
	this._field[type].value = addr;
	this._adjustAddrHeight(this._field[type]);
};

// Sets the mode ZmHtmlEditor should be in.
ZmComposeView.prototype.setComposeMode =
function(composeMode) {
	if (composeMode == DwtHtmlEditor.TEXT ||
		(composeMode == DwtHtmlEditor.HTML && this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED))) {

		var curMember = (this._composeMode == DwtHtmlEditor.TEXT) ? this._bodyField : this._htmlEditor;

		this._composeMode = composeMode;

		this._htmlEditor.setMode(composeMode, true);
		// reset the body field Id and object ref
		this._bodyFieldId = this._htmlEditor.getBodyFieldId();
		this._bodyField = document.getElementById(this._bodyFieldId);
		if (this._bodyField.disabled) {
			this._bodyField.disabled = false;
		}

		// for now, always reset message body size
		this._resetBodySize();
		// recalculate form value since HTML mode inserts HTML tags
		this._origFormValue = this._formValue();

		// swap new body field into tab group
		var newMember = (composeMode == DwtHtmlEditor.TEXT) ? this._bodyField : this._htmlEditor;
		if (curMember && newMember && (curMember != newMember) && this._controller._tabGroup) {
			this._controller._tabGroup.replaceMember(curMember, newMember);
			// focus via replaceMember() doesn't take, try again
			if (composeMode == DwtHtmlEditor.HTML) {
				this._retryHtmlEditorFocus();
			}
		}
	}
};

ZmComposeView.prototype._retryHtmlEditorFocus =
function() {
	if (this._htmlEditor.hasFocus()) {
		var ta = new AjxTimedAction(this, this._focusHtmlEditor);
		AjxTimedAction.scheduleAction(ta, 10);
	}
};

ZmComposeView.prototype.setDetach =
function(params) {

	this._action = params.action;
	this._msg = params.msg;

	// set the addr fields as populated
	for (var i in params.addrs) {
		this.setAddress(i, params.addrs[i]);
	}

	this._subjectField.value = params.subj || "";
	this._htmlEditor.setContent(params.body || "");

	if (params.forwardHtml)
		this._attcDiv.innerHTML = params.forwardHtml;
	if (params.identityId) {
		this._identitySelect.setSelectedValue(params.identityId);
	}

	this.backupForm = params.backupForm;
	this._sendUID = params.sendUID;

	// bug 14322 -- in Windows Firefox, DEL/BACKSPACE don't work
	// when composing in new window until we (1) enter some text
	// or (2) resize the window (!).  I chose the latter.
	if (AjxEnv.isGeckoBased && AjxEnv.isWindows)
		window.resizeBy(1, 1);
};

ZmComposeView.prototype.setFocus =
function() {
	// set the cursor to either to To address for new message or a forward
	if (this._action == ZmOperation.NEW_MESSAGE || 
		this._action == ZmOperation.FORWARD_INLINE || 
		this._action == ZmOperation.FORWARD_ATT) {

		this._appCtxt.getKeyboardMgr().grabFocus(this._field[AjxEmailAddress.TO]);
	} else {
		// otherwise set cursor to the beginning of first line
		this._setBodyFieldFocus();
	}
};

ZmComposeView.prototype.reEnableDesignMode =
function() {
	if (this._composeMode == DwtHtmlEditor.HTML)
		this._htmlEditor.reEnableDesignMode();
};

// user just saved draft, update compose view as necessary
ZmComposeView.prototype.processMsgDraft =
function(msgDraft) {
	this.reEnableDesignMode();
	this._action = ZmOperation.DRAFT;
	this._msg = msgDraft;
	this._msgAttId = null;
	// always redo att links since user couldve removed att before saving draft
	this.cleanupAttachments(true);
	this._showForwardField(msgDraft, ZmOperation.DRAFT);
	this._resetBodySize();
	// save form state (to check for change later)
	this._origFormValue = this._formValue();
};

/**
* Revert compose view to a clean state (usually called before popping compose view)
*/
ZmComposeView.prototype.reset =
function(bEnableInputs) {
	this.backupForm = null;
	this._sendUID = null;

	// reset autocomplete list
	if (this._acAddrSelectList) {
		this._acAddrSelectList.reset();
		this._acAddrSelectList.show(false);
	}

	// reset To/CC/BCC fields
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var textarea = this._field[ZmMailMsg.COMPOSE_ADDRS[i]];
		textarea.value = "";
		this._adjustAddrHeight(textarea, true);
	}

	// reset subject / body fields
	this._subjectField.value = "";
	this._htmlEditor.clear();

	// the div that holds the attc.table and null out innerHTML
	this.cleanupAttachments(true);

	this._resetBodySize();

	this._msgAttId = null;
	this._origFormValue = null;

	// reset dirty shields
	this._noSubjectOkay = this._badAddrsOkay = false;

	// remove extra mime parts
	this._extraParts = null;

	// enable/disable input fields
	this.enableInputs(bEnableInputs);

	// reset state of the spell check button
	this._controller.toggleSpellCheckButton(false);
};

/**
* Adds an attachment file upload field to the compose form.
*/
ZmComposeView.prototype.addAttachmentField =
function() {

	var attTable = this._getAttachmentTable();

	if (this._attachCount == ZmComposeView.SHOW_MAX_ATTACHMENTS) {
		this._attcDiv.style.height = ZmComposeView.MAX_ATTACHMENT_HEIGHT;
		this._attcDiv.style.overflow = "auto";
	}

	this._attachCount++;

	// add new row
	var row = attTable.insertRow(-1);
	var attId = "_att_" + Dwt.getNextId();
	var attRemoveId = attId + "_r";
	var attInputId = attId + "_i";
	row.id = attId;

	// add new cell and build html for inserting file upload input element
	var	cell = row.insertCell(-1);
	var html = [];
	var idx = 0;
	html[idx++] = "<table cellspacing=2 cellpadding=0 border=0><tr><td><div class='attachText'>";
	html[idx++] = ZmMsg.attachFile;
	html[idx++] = ":</div></td><td class='nobreak'><input id='";
	html[idx++] = attInputId;
	html[idx++] = "' type='file' name='";
	html[idx++] = ZmComposeView.UPLOAD_FIELD_NAME;
	html[idx++] = "' size=40>&nbsp;<span id='";
	html[idx++] = attRemoveId;
	html[idx++] = "' onmouseover='this.style.cursor=\"pointer\"' onmouseout='this.style.cursor=\"default\"' style='color:blue;text-decoration:underline;'>";
	html[idx++] = ZmMsg.remove;
	html[idx++] = "</span></td></tr></table>";
	cell.innerHTML = html.join("");

	this._setEventHandler(attRemoveId, "onClick", null);
	// trap key presses in IE for input field so we can ignore ENTER key (bug 961)
	if (AjxEnv.isIE)
		this._setEventHandler(attInputId, "onKeyDown", null);
	this._resetBodySize();

	this._attcDiv.scrollTop = this._attcDiv.scrollHeight;
};

ZmComposeView.prototype.enableInputs =
function(bEnable) {
	// disable input elements so they dont bleed into top zindex'd view
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++)
		this._field[ZmMailMsg.COMPOSE_ADDRS[i]].disabled = !bEnable;

	this._subjectField.disabled = this._bodyField.disabled = !bEnable;
};

/**
 * Adds an extra MIME part to the message. The extra parts will be
 * added, in order, to the end of the parts after the primary message
 * part.
 */
ZmComposeView.prototype.addMimePart =
function(mimePart) {
	if (!this._extraParts) {
		this._extraParts = [];
	}
	this._extraParts.push(mimePart);
};

/**
* Adds the user's signature to the message body. An "internet" style signature
* is prefixed by a special line and added to the bottom. An "outlook" style
* signature is added before quoted content.
* 
* @content 			optional content to use
*/
ZmComposeView.prototype.addSignature =
function(content) {
	// bug fix #6821 - we need to pass in "content" param
	// since HTML composing in new window doesnt guarantee the html editor
	// widget will be initialized when this code is running.
	var content = content || "";
	var sig = this._getSignature();
	var sep = this._getSignatureSeparator();
	var newLine = this._getSignatureNewLine();
	var identity = this.getIdentity();
	if (identity.getSignatureStyle() == ZmSetting.SIG_OUTLOOK) {
		var regexp = ZmComposeView.QUOTED_CONTENT_RE;
		var repl = "----- ";

		if (this._composeMode == DwtHtmlEditor.HTML) {
			regexp = ZmComposeView.HTML_QUOTED_CONTENT_RE;
			repl = "<br>----- ";
		}

		if (content.match(regexp))
			content = content.replace(regexp, [sep, sig, newLine, repl].join(""));
		else
			content = [content, sep, sig].join("");
	} else {
		content = [content, sep, sig].join("");
	}

	this._htmlEditor.setContent(content);
};

ZmComposeView.prototype._dispose =
function() {
	if (this._identityChangeListenerObj) {
		var identityCollection = AjxDispatcher.run("GetIdentityCollection");
		identityCollection.removeChangeListener(this._identityChangeListenerObj);
	}
};

ZmComposeView.prototype._getSignature =
function() {
	var identity = this.getIdentity();
	
	var sig = identity.signature;
	if (!sig) return;

	var newLine = this._getSignatureNewLine();
	if (this._composeMode == DwtHtmlEditor.HTML)
		sig = AjxStringUtil.htmlEncodeSpace(sig);

	return sig + newLine;
};

ZmComposeView.prototype._getSignatureSeparator =
function() {
	var newLine = this._getSignatureNewLine();
	var sep = newLine + newLine;
	if (this.getIdentity().getSignatureStyle() == ZmSetting.SIG_INTERNET)
		sep = sep + "-- " + newLine;
		
	return sep;
};

ZmComposeView.prototype._getSignatureNewLine =
function() {
	return (this._composeMode == DwtHtmlEditor.HTML) ? "<br>" : "\n";
};

/**
* Returns true if form contents have changed, or if they are empty.
*
* @param incAddrs		takes addresses into consideration
* @param incSubject		takes subject into consideration
*/
ZmComposeView.prototype.isDirty =
function(incAddrs, incSubject) {
	// any attachment activity => dirty
	if (this._gotAttachments())
		return true;
	// reply/forward and empty body => not dirty
	if ((this._action != ZmOperation.NEW_MESSAGE) && (this._htmlEditor.getContent().match(ZmComposeView.EMPTY_FORM_RE)))
		return false;
	var curFormValue = this._formValue(incAddrs, incSubject);
	// empty subject and body => not dirty
	if (curFormValue.match(ZmComposeView.EMPTY_FORM_RE))
		return false;
	// subject or body has changed => dirty
	return (curFormValue != this._origFormValue);
};

ZmComposeView.prototype.cleanupAttachments = 
function(all) {
	if (this._uploadForm && this._uploadForm.parentNode) {
		this._uploadForm.parentNode.removeChild(this._uploadForm);
	}
	this._attachmentTable = this._uploadForm = null;

	if (all) {
		this._attcDiv.innerHTML = "";
		this._attcDiv.style.height = "";
		this._attachCount = 0;
	}
	
	// make sure att IDs don't get reused
	if (this._msg) {
		this._msg._attId = null;
	}
};

// Private / protected methods

ZmComposeView.prototype._isInviteReply =
function(action){
	return (action == ZmOperation.REPLY_ACCEPT ||
			action == ZmOperation.REPLY_CANCEL ||
			action == ZmOperation.REPLY_DECLINE ||
			action == ZmOperation.REPLY_TENTATIVE ||
			action == ZmOperation.REPLY_MODIFY ||
			action == ZmOperation.REPLY_NEW_TIME);
};

/*
* Creates an address string from the given vector, excluding any that have
* already been used.
*
* @param addrVec	[AjxVector]		vector of AjxEmailAddress
* @param used		[Object]		hash of addresses that have been used
*/
ZmComposeView.prototype._getAddrString =
function(addrVec, used) {
	var a = addrVec.getArray();
	var addrs = [];
	for (var i = 0; i < a.length; i++) {
		var addr = a[i];
		var email = addr ? addr.getAddress() : null;
		if (!email) { continue; }
		email = email.toLowerCase();
		if (!used[email])
			addrs.push(addr);
		used[email] = true;
	}
	return addrs.join(AjxEmailAddress.SEPARATOR); // calls implicit toString() on each addr object
};

// returns the text part given a body part (if body part is HTML, converts it to text)
ZmComposeView.prototype._getTextPart =
function(bodyPart, encodeSpace) {
	var text = "";
	// if the only content type returned is html, convert to text
	if (bodyPart.ct == ZmMimeTable.TEXT_HTML) {
		// create a temp iframe to create a proper DOM tree
		var params = {parent: this, hidden: true, html: bodyPart.content};
		var dwtIframe = new DwtIframe(params);
		if (dwtIframe) {
			text = AjxStringUtil.convertHtml2Text(dwtIframe.getDocument().body);
			delete dwtIframe;
		}
	} else {
		text = encodeSpace 
			? AjxStringUtil.convertToHtml(bodyPart.content) 
			: bodyPart.content;
	}

	return text;
};

ZmComposeView.prototype._getAttachmentTable =
function() {
	if (!this._attachmentTable)
		this._attachmentTable = this._createAttachmentsContainer();
	return this._attachmentTable;
};

// Consistent spot to locate various dialogs
ZmComposeView.prototype._getDialogXY =
function() {
	var loc = Dwt.toWindow(this.getHtmlElement(), 0, 0);
	return new DwtPoint(loc.x + ZmComposeView.DIALOG_X, loc.y + ZmComposeView.DIALOG_Y);
};

ZmComposeView.prototype._getForwardAttIds =
function(name) {
	var forAttIds = [];
	var forAttList = document.getElementsByName(name);

	// walk collection of input elements
	for (var i = 0; i < forAttList.length; i++) {
		if (forAttList[i].checked)
			forAttIds.push(forAttList[i].id);
	}

	return forAttIds;
};

// Returns the location where the autocomplete list should be positioned. Run as a callback.
ZmComposeView.prototype._getAcListLoc =
function(cv, ev) {
	var element = ev.element;
	var field = document.getElementById(cv._divId[element.addrType]);
	var offset = Dwt.getLocation(field).y - this.getLocation().y

	return new DwtPoint(75, offset + Dwt.getSize(element).y + 6);
};

ZmComposeView.prototype._acCompHandler =
function(text, el, match) {
	this._adjustAddrHeight(el);
};

ZmComposeView.prototype._acKeyupHandler =
function(ev, acListView, result) {
	var key = DwtKeyEvent.getCharCode(ev);
	// process any printable character or enter/backspace/delete keys
	if (result && AjxStringUtil.isPrintKey(key) ||
		key == 3 || key == 13 || key == 8 || key == 46)
	{
		this._adjustAddrHeight(DwtUiEvent.getTargetWithProp(ev, "id"));
	}
};

ZmComposeView.prototype._adjustAddrHeight =
function(textarea, skipResetBodySize) {
	if (AjxEnv.isSafari && !AjxEnv.isSafariNightly)
		return;

	if (textarea.value.length == 0) {
		textarea.style.height = "21px";

		if (AjxEnv.isIE) // for IE use overflow-y
			textarea.style.overflowY = "hidden";
		else
			textarea.style.overflow = "hidden";

		if (!skipResetBodySize)
			this._resetBodySize();

		return;
	}

	if (textarea.scrollHeight > textarea.clientHeight) {
		var taHeight = parseInt(textarea.style.height) || 0;
		if (taHeight <= 65) {
			var sh = textarea.scrollHeight;
			if (textarea.scrollHeight >= 65) {
				sh = 65;
				if (AjxEnv.isIE)
					textarea.style.overflowY = "scroll";
				else
					textarea.style.overflow = "auto";
			}
			textarea.style.height = sh + 13;
			this._resetBodySize();
		} else {
			if (AjxEnv.isIE) // for IE use overflow-y
				textarea.style.overflowY = "scroll";
			else
				textarea.style.overflow = "auto";

			textarea.scrollTop = textarea.scrollHeight;
		}
	}
};

/*
* Set various address headers based on the original message and the mode we're in.
* Make sure not to duplicate any addresses, even across fields.
*/
ZmComposeView.prototype._setAddresses =
function(action, toOverride) {
	this._action = action;

	if (action == ZmOperation.NEW_MESSAGE &&
		toOverride)
	{
		this.setAddress(AjxEmailAddress.TO, toOverride);
	}
	else if (action == ZmOperation.REPLY ||
			 action == ZmOperation.REPLY_ALL ||
			 this._isInviteReply(action))
	{
		// Prevent user's login name and aliases from going into To: or Cc:
		var used = {};
		var uname = this._appCtxt.get(ZmSetting.USERNAME);
		if (uname) {
			used[uname.toLowerCase()] = true;
		}
		var aliases = this._appCtxt.get(ZmSetting.MAIL_ALIASES);
		for (var i = 0, count = aliases.length; i < count; i++) {
			used[aliases[i].toLowerCase()] = true;
		}
		
		if (!this._msg.isSent) {
			var addr = this._getAddrString(this._msg.getReplyAddresses(action), {});
			this.setAddress(AjxEmailAddress.TO, addr);
		} else if (action == ZmOperation.REPLY) {
			var toAddrs = this._msg.getAddresses(AjxEmailAddress.TO);
			this.setAddress(AjxEmailAddress.TO, this._getAddrString(toAddrs, {}));
		}

		// reply to all senders if reply all (includes To: and Cc:)
		if (action == ZmOperation.REPLY) {
			this.setAddress(AjxEmailAddress.CC, "");
		} else if (action == ZmOperation.REPLY_ALL) {
			var addrs = new AjxVector();
			addrs.addList(this._msg.getAddresses(AjxEmailAddress.CC));
			var toAddrs = this._msg.getAddresses(AjxEmailAddress.TO);
			if (this._msg.isSent) {
				// sent msg replicates To: and Cc: (minus duplicates)
				this.setAddress(AjxEmailAddress.TO, this._getAddrString(toAddrs, used));
			} else {
				addrs.addList(toAddrs);
			}
			this.setAddress(AjxEmailAddress.CC, this._getAddrString(addrs, used));
		}
	}
	else if (action == ZmOperation.DRAFT ||
			 action == ZmOperation.SHARE)
	{
		for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
			var addrs = this._msg.getAddresses(ZmMailMsg.COMPOSE_ADDRS[i]);
			this.setAddress(ZmMailMsg.COMPOSE_ADDRS[i], addrs.getArray().join(AjxEmailAddress.SEPARATOR));
		}
	}
};

ZmComposeView.prototype._setSubject =
function(action, msg, subjOverride) {
	if (!msg || (action == ZmOperation.NEW_MESSAGE && subjOverride == null)) {
		return;
	}

	var subj = subjOverride || msg.subject;

	if (action != ZmOperation.DRAFT && subj) {
		var regex = ZmComposeView.SUBJ_PREFIX_RE;
		while (regex.test(subj))
			subj = subj.replace(regex, "");
	}

	var prefix = "";
	switch (action) {
		case ZmOperation.REPLY:
		case ZmOperation.REPLY_ALL: 		prefix = ZmMsg.re + ": "; break;
		case ZmOperation.REPLY_CANCEL: 		prefix = ZmMsg.cancelled + ": "; break;
		case ZmOperation.FORWARD_INLINE:
		case ZmOperation.FORWARD_ATT: 		prefix = ZmMsg.fwd + ": "; break;
		case ZmOperation.REPLY_ACCEPT:		prefix = ZmMsg.subjectAccept + ": "; break;
		case ZmOperation.REPLY_DECLINE:		prefix = ZmMsg.subjectDecline + ": "; break;
		case ZmOperation.REPLY_TENTATIVE:	prefix = ZmMsg.subjectTentative + ": "; break;
		case ZmOperation.REPLY_NEW_TIME:	prefix = ZmMsg.subjectNewTime + ": "; break;
	}
	this._subjectField.value = prefix + (subj || "");
};

ZmComposeView.prototype._setBody =
function(action, msg, extraBodyText, incOption) {
	var composingHtml = this._composeMode == DwtHtmlEditor.HTML;

	// XXX: consolidate this code later.
	var isDraft = action == ZmOperation.DRAFT;
	var isShare = action == ZmOperation.SHARE;
	var isInviteReply = action == ZmOperation.REPLY_ACCEPT ||
						action == ZmOperation.REPLY_DECLINE ||
						action == ZmOperation.REPLY_TENTATIVE ||
						action == ZmOperation.REPLY_NEW_TIME;
	if (isDraft || isShare || isInviteReply) {
		var body = "";
		if (composingHtml) {
			body = msg.getBodyPart(ZmMimeTable.TEXT_HTML);
			// if no html part exists, just grab the text
			// (but make sure to preserve whitespace and newlines!)
			if (body) {
				body = body.content;
			} else {
				var bodyPart = msg.getBodyPart();
				body = bodyPart ? (AjxStringUtil.convertToHtml(bodyPart.content)) : null;
			}
		} else {
			var bodyPart = msg.getBodyPart();
			body = bodyPart ? bodyPart.content : null;
		}
		this._htmlEditor.setContent(body);
		this._showForwardField(msg, action);
		if (!isInviteReply) {
			return;
		}
	}

	var identity = this.getIdentity();
	var sigStyle = identity.signatureEnabled && identity.signature ? identity.getSignatureStyle() : null;

	var value = sigStyle == ZmSetting.SIG_OUTLOOK
		? (this._getSignatureSeparator() + this._getSignature())
		: "";

	// get reply/forward prefs as necessary
	if (!incOption) {
		var isReply = action == ZmOperation.REPLY || action == ZmOperation.REPLY_ALL;
		if (isReply || isInviteReply) {
			incOption = identity.getReplyOption();
		} else if (action == ZmOperation.FORWARD_INLINE) {
			incOption = identity.getForwardOption();
			if (incOption == ZmSetting.INCLUDE_ATTACH) {
				incOption = ZmSetting.INCLUDE;
			}
		} else if (action == ZmOperation.FORWARD_ATT) {
			incOption = ZmSetting.INCLUDE_ATTACH;
		}
	}

	if (incOption == ZmSetting.INCLUDE_NONE ||
		action == ZmOperation.NEW_MESSAGE)
	{
		if (extraBodyText)
			value = extraBodyText + value;
	}
	else if (incOption == ZmSetting.INCLUDE_ATTACH && this._msg)
	{
		this._msgAttId = this._msg.id;
	}
	else if (!this._msgIds)
	{
		var crlf = composingHtml ? "<br>" : ZmMsg.CRLF;
		var crlf2 = composingHtml ? "<br><br>" : ZmMsg.CRLF2;
		var leadingText = extraBodyText ? extraBodyText + crlf : crlf;
		var body = null;
		if (composingHtml) {
			body = msg.getBodyPart(ZmMimeTable.TEXT_HTML);
			if (body) {
				body = AjxUtil.isString(body) ? body : body.content;
			} else {
				// if no html part exists, just grab the text
				var bodyPart = msg.getBodyPart();
				body = bodyPart ? this._getTextPart(bodyPart, true) : null;
			}
		} else {
			// grab text part out of the body part
			var bodyPart = msg.getBodyPart(ZmMimeTable.TEXT_PLAIN) || msg.getBodyPart(ZmMimeTable.TEXT_HTML);
			body = bodyPart ? this._getTextPart(bodyPart) : null;
		}

		body = body || ""; // prevent from printing "null" if no body found

		// Bug 7160: Strip off the ~*~*~*~ from invite replies.
		if (isInviteReply) {
			body = body.replace(ZmCalItem.NOTES_SEPARATOR, "");
		}

		// bug fix# 3215 - dont allow prefixing for html msgs
		if ((action == ZmOperation.FORWARD_INLINE && incOption != ZmSetting.INCLUDE_PREFIX) || 
			 incOption == ZmSetting.INCLUDE || composingHtml) 
		{
			var msgText = (action == ZmOperation.FORWARD_INLINE) ? ZmMsg.forwardedMessage : ZmMsg.origMsg;
			var text = ZmMsg.DASHES + " " + msgText + " " + ZmMsg.DASHES + crlf;
			for (var i = 0; i < ZmComposeView.QUOTED_HDRS.length; i++) {
				var hdr = msg.getHeaderStr(ZmComposeView.QUOTED_HDRS[i]);
				if (hdr) {
					// bugfix: htmlescape the headers if we're composing in HTML mode.
					if (composingHtml)
						hdr = AjxStringUtil.convertToHtml(hdr);
					text = text + hdr + crlf;
				}
			}
			body = text + crlf + body;
			value += leadingText + body;
		} else {
			var from = msg.getAddress(AjxEmailAddress.FROM);
			if (!from && msg.isSent)
				from = this._appCtxt.get(ZmSetting.USERNAME);
			var preface = "";
			if (from) {
				if (!ZmComposeView._replyPrefixFormatter) {
					ZmComposeView._replyPrefixFormatter = new AjxMessageFormat(ZmMsg.replyPrefix);
				}
				preface = ZmComposeView._replyPrefixFormatter.format(from.toString());
			}
			var prefix = identity.getPrefix();
			if (incOption == ZmSetting.INCLUDE_PREFIX) {
				value += leadingText + preface + AjxStringUtil.wordWrap(body, ZmComposeView.WRAP_LENGTH, prefix + " ");
			}
			else if (incOption == ZmSetting.INCLUDE_SMART)
			{
				var chunks = AjxStringUtil.getTopLevel(body);
				for (var i = 0; i < chunks.length; i++)
					chunks[i] = AjxStringUtil.wordWrap(chunks[i], ZmComposeView.WRAP_LENGTH, prefix + " ");
				var text = chunks.length ? chunks.join('\n\n') : body;
				value += leadingText + preface + text;
			} else if (action == ZmOperation.REPLY_ACCEPT ||
						action == ZmOperation.REPLY_DECLINE ||
						action == ZmOperation.REPLY_TENTATIVE)
			{
				var notes;
		
				var bodyPart = msg.getBodyPart(ZmMimeTable.TEXT_PLAIN);
				var body = bodyPart ? bodyPart.content : "";
				body = body.replace(/\r\n/g, "\n");
		
				// bug 5122: always show original meeting details
				value = preface + AjxStringUtil.wordWrap(body, ZmComposeView.WRAP_LENGTH, prefix + " ");
			}
		}
	}

	if (sigStyle == ZmSetting.SIG_INTERNET) {
		this.addSignature(value);
	} else {
		this._htmlEditor.setContent(value);
	}
	
	this._showForwardField(msg, action, incOption);
};

ZmComposeView.prototype.resetBody =
function(action, msg, extraBodyText, incOption) {
	this.cleanupAttachments(true);
	this._setBody(action, msg, extraBodyText, incOption);
	this._origFormValue = this._formValue();
};

// Generic routine for attaching an event handler to a field. Since "this" for the handlers is
// the incoming event, we need a way to get at ZmComposeView, so it's added to the event target.
ZmComposeView.prototype._setEventHandler =
function(id, event, addrType) {
	var field = document.getElementById(id);
	field._composeView = this._internalId;
	if (addrType)
		field._addrType = addrType;
	var lcEvent = event.toLowerCase();
	field[lcEvent] = ZmComposeView["_" + event];
};

ZmComposeView.prototype._setBodyFieldCursor =
function(extraBodyText) {
	if (this._composeMode == DwtHtmlEditor.HTML) {
		return;
	}

	// this code moves the cursor to the beginning of the body
	if (AjxEnv.isIE) {
		var tr = this._bodyField.createTextRange();
		if (extraBodyText) {
			tr.move('character', extraBodyText.length + 1);
		} else {
			tr.collapse(true);
		}
		tr.select();
	} else if (!AjxEnv.isSafari) {
		var index = extraBodyText ? (extraBodyText.length + 1) : 0;
		this._bodyField.setSelectionRange(index, index);
	}
};

/**
* This should be called only once for when compose view loads first time around
*/
ZmComposeView.prototype._initialize =
function(composeMode) {
	// init address field objects
	this._divId = {};
	this._buttonTdId = {};
	this._fieldId = {};
	this._using = {};
	this._button = {};
	this._field = {};
	this._internalId = AjxCore.assignId(this);
	// init element IDs for address fields
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		this._divId[type] = Dwt.getNextId();
		this._buttonTdId[type] = Dwt.getNextId();
		this._fieldId[type] = Dwt.getNextId();
	}

	// init html
	this._createHtml();

	// init compose view w/ based on user prefs
	var bComposeEnabled = this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED);
	var composeFormat = this._appCtxt.get(ZmSetting.COMPOSE_AS_FORMAT);
	var defaultCompMode = bComposeEnabled && composeFormat == ZmSetting.COMPOSE_HTML
		? DwtHtmlEditor.HTML : DwtHtmlEditor.TEXT;
	this._composeMode = composeMode || defaultCompMode;

	// init html editor
	this._htmlEditor = new ZmHtmlEditor(this, DwtControl.RELATIVE_STYLE, null, this._composeMode, this._appCtxt);
//	this._htmlEditor.addEventCallback(new AjxCallback(this, this._htmlEditorEventCallback));
	this._bodyFieldId = this._htmlEditor.getBodyFieldId();
	this._bodyField = document.getElementById(this._bodyFieldId);

	// misc. inits
	this._msgDialog = this._appCtxt.getMsgDialog();
	this.setScrollStyle(DwtControl.SCROLL);
	this._attachCount = 0;

	// init listeners
	this.addControlListener(new AjxListener(this, ZmComposeView.prototype._controlListener));

	// init autocomplete list
	if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
		var contactsClass = this._appCtxt.getApp(ZmApp.CONTACTS);
		var contactsLoader = contactsClass.getContactList;
		var locCallback = new AjxCallback(this, this._getAcListLoc, [this]);
		var compCallback = (!AjxEnv.isSafari || AjxEnv.isSafariNightly) ? (new AjxCallback(this, this._acCompHandler)) : null;
		var keyupCallback = (!AjxEnv.isSafari || AjxEnv.isSafariNightly) ? (new AjxCallback(this, this._acKeyupHandler)) : null;
		var params = {parent: this, dataClass: contactsClass, dataLoader: contactsLoader,
					  matchValue: ZmContactsApp.AC_VALUE_FULL, locCallback: locCallback,
					  compCallback:compCallback, keyUpCallback: keyupCallback};
		this._acAddrSelectList = new ZmAutocompleteListView(params);
	}

	// init To/CC/BCC buttons and their event handlers
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (this._contactPickerEnabled) {
			this._button[type] = new DwtButton(this);
			var typeStr = AjxEmailAddress.TYPE_STRING[type];
			this._button[type].setText(ZmMsg[typeStr] + ":");

			var buttonTd = document.getElementById(this._buttonTdId[type]);
			buttonTd.appendChild(this._button[type].getHtmlElement());
			buttonTd.addrType = type;

			this._button[type].addSelectionListener(new AjxListener(this, this._addressButtonListener));
			this._button[type].addrType = type;
		}

		this._field[type] = document.getElementById(this._fieldId[type]);
		this._field[type].addrType = type;

		// autocomplete-related handlers
		if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
			this._acAddrSelectList.handle(this._field[type]);
		} else {
			if (!AjxEnv.isSafari || AjxEnv.isSafariNightly)
				this._setEventHandler(this._fieldId[type], "onKeyUp");
		}
	}
};

ZmComposeView.prototype._createHtml =
function() {
	var subjectFieldId = Dwt.getNextId();
	var attcDivId = Dwt.getNextId();
	this._identityDivId = Dwt.getNextId();
	var identityCellId = Dwt.getNextId();
	var div = document.createElement("div");

	var html = [];
	var idx = 0;

	html[idx++] = "<table border=0 cellpadding=0 cellspacing=0 width=100%>";

	// create identity selector
	html[idx++] = "<tr><td><div id='";
	html[idx++] = this._identityDivId;
	html[idx++] = "'><table cellspacing=4 cellpadding=0 border=0 width=100%><tr><td width=";
	html[idx++] = AjxEnv.isIE ? "60" : "64";
	html[idx++] = " align=right>";
	html[idx++] = ZmMsg.from;
	html[idx++] = ":</td><td id='";
	html[idx++] = identityCellId;
	html[idx++] = "'></td></tr></div></table></div></td></tr>";

	// create address elements
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		html[idx++] = "<tr><td><div id='";
		html[idx++] = this._divId[type];
		html[idx++] = "'";
		html[idx++] = (type != AjxEmailAddress.TO) ? " style='display: none;'>" : ">";
		html[idx++] = "<table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
		if (this._contactPickerEnabled) {
			html[idx++] = "<td valign=top width=60 id='";
			html[idx++] = this._buttonTdId[type];
			html[idx++] = "'></td>";
		} else {
			var typeStr = AjxEmailAddress.TYPE_STRING[type];
			var addrStr = ZmMsg[typeStr] + ":";
			html[idx++] = "<td width=60 align='right' valign='top' id='";
			html[idx++] = this._buttonTdId[type];
			html[idx++] = "'>";
			html[idx++] = addrStr;
			html[idx++] = "</td>";
		}
		html[idx++] = "<td><textarea id='";
		html[idx++] = this._fieldId[type];
		html[idx++] = "' rows=1 class='addresses' style='";
		html[idx++] = AjxEnv.isSafari && !AjxEnv.isSafariNightly
			? "height:52px;" : "height:21px; overflow:hidden";
		html[idx++] = "'></textarea></td>";
		html[idx++] = "</tr></table></div></td></tr>";
	}

	// create subject field
	html[idx++] = "<tr><td><table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
	html[idx++] = "<td width=";
	html[idx++] = AjxEnv.isIE ? "60" : "64";
	html[idx++] = " align='right'>";
	html[idx++] = ZmMsg.subject;
	html[idx++] = ":</td>";
	html[idx++] = "<td><table cellspacing=0 cellpadding=0 border=0 width=100%><tr>";
	html[idx++] = "<td><input autocomplete='off' type='text' id='";
	html[idx++] = subjectFieldId;
	html[idx++] = "' class='subjectField'></td>";
	html[idx++] = "</tr></table></td>";
	html[idx++] = "</tr></table></td></tr>";

	// create element for adding attachments
	html[idx++] = "<tr><td><div id='";
	html[idx++] = attcDivId;
	html[idx++] = "' /></td></tr>";

	html[idx++] = "</table>";

	div.innerHTML = html.join("");
	this.getHtmlElement().appendChild(div);

	// save reference to DOM objects per ID's
	this._subjectField = document.getElementById(subjectFieldId);
//	this._subjectField.onkeydown = AjxCallback.simpleClosure(this.__checkTabInSubject, this);
	this._attcDiv = document.getElementById(attcDivId);
	this._identityCell = document.getElementById(identityCellId);
	
	var options = this._getIdentityOptions();
	this._identitySelect = new DwtSelect(this, options);
	this._identitySelect.setToolTipContent(ZmMsg.chooseIdentity);
	this._identitySelect.getHtmlElement().style.width='100%';
	this._identitySelect.reparentHtmlElement(this._identityCell);
	var identityCollection = AjxDispatcher.run("GetIdentityCollection");
	if (!this._identityChangeListenerObj) {
		this._identityChangeListenerObj = new AjxListener(this, this._identityChangeListener);
	}
	identityCollection.addChangeListener(this._identityChangeListenerObj);
	this._setIdentityVisibility();
};

ZmComposeView.prototype._getIdentityOptions =
function() {
	var options = [];
	var identityCollection = AjxDispatcher.run("GetIdentityCollection");
	var identities = identityCollection.getIdentities();
	for (var i = 0, count = identities.length; i < count; i++) {
		var identity = identities[i];
		var text = this._getIdentityText(identity);
		options[i] = new DwtSelectOptionData(identity.id, text);
	}
	return options;
};

ZmComposeView.prototype._getIdentityText =
function(identity) {
	if (identity.sendFromDisplay) {
		return [identity.name,  ' ("', identity.sendFromDisplay, '" <', identity.sendFromAddress, '>)'].join("");
	} else {
		return [identity.name,  ' (', identity.sendFromAddress, ')'].join("");
	}
};

ZmComposeView.prototype._identityChangeListener =
function(ev) {
	if (ev.event == ZmEvent.E_CREATE) {
		this._setIdentityVisibility();
		var identity = ev.getDetail("item");
		var text = this._getIdentityText(identity);
		var option = new DwtSelectOptionData(identity.id, text);
		this._identitySelect.addOption(option);
	} else if (ev.event == ZmEvent.E_DELETE) {
		// DwtSelect doesn't support removing an option, so recreate the whole thing.		
		this._identitySelect.clearOptions();
		var options = this._getIdentityOptions();
		for (var i = 0, count = options.length; i < count; i++)	 {
			this._identitySelect.addOption(options[i]);
		}
		this._setIdentityVisibility();
	} else if (ev.event == ZmEvent.E_MODIFY) {
		var identity = ev.getDetail("item");
		var text = this._getIdentityText(identity);
		this._identitySelect.rename(identity.id, text);
	}
};

ZmComposeView.prototype._setIdentityVisibility =
function() {
	var identityCount = AjxDispatcher.run("GetIdentityCollection").getSize();
	var div = document.getElementById(this._identityDivId);
	var visible = Dwt.getVisible(div);
	if (visible) {
		if ((identityCount < 2) || !this._appCtxt.get(ZmSetting.IDENTITIES_ENABLED)) {
			Dwt.setVisible(div, false);
		}
	} else {
		if (identityCount >= 2) {
			Dwt.setVisible(div, true);
		}
	}
};

ZmComposeView.prototype.getIdentitySelect =
function() {
	return this._identitySelect;
};

ZmComposeView.prototype.getIdentity =
function() {
	var identityCollection = AjxDispatcher.run("GetIdentityCollection");
	var id = this._identitySelect.getValue();
	var result = identityCollection.getById(id);
	return result ? result : identityCollection.defaultIdentity;
};

/*
ZmComposeView.prototype.__checkTabInSubject = function(ev) {
	if (AjxEnv.isIE)
		ev = window.event;
	if (ev.keyCode == 9) {
		this._htmlEditor.focus();
		setTimeout(AjxCallback.simpleClosure(this._htmlEditor.focus, this._htmlEditor), 10);
		return false;
	}
};
*/

ZmComposeView.prototype._submitAttachments =
function(isDraft) {
	var callback = new AjxCallback(this, this._attsDoneCallback, [isDraft]);
	var um = this._appCtxt.getUploadManager();
	window._uploadManager = um;
	try {
		um.execute(callback, this._uploadForm);
	} catch (ex) {
		callback.run();
	}
};

ZmComposeView.prototype._createAttachmentsContainer =
function() {
	var attachmentTableId = Dwt.getNextId();
	var uploadFormId = Dwt.getNextId();
	var uri = location.protocol + "//" + document.domain + this._appCtxt.get(ZmSetting.CSFE_UPLOAD_URI);

	var html = [];
	var idx = 0;
	html[idx++] = "<div style='overflow:auto'><form accept-charset='utf-8' method='POST' action='";
	html[idx++] = uri;
	html[idx++] = "' id='";
	html[idx++] = uploadFormId;
	html[idx++] = "' enctype='multipart/form-data'><input type='hidden' name='_charset_'/><table id='";
	html[idx++] = attachmentTableId;
	html[idx++] = "' cellspacing=0 cellpadding=0 border=0 class='iframeTable'></table>";
	html[idx++] = "</form></div>";

	if (this._attcDiv.innerHTML.length) {
		this._attcDiv.appendChild(Dwt.parseHtmlFragment(html.join("")));
	} else {
		this._attcDiv.innerHTML = html.join("");
	}

	// save reference to upload form
	this._uploadForm = document.getElementById(uploadFormId);
	// return reference to newly create attachment table
	return document.getElementById(attachmentTableId);
};

ZmComposeView.prototype._showForwardField =
function(msg, action, replyPref) {
	var html = [];
	var idx = 0;

	if (this._msgIds && this._msgIds.length) {
		html[idx++] = "<table cellspacing=0 cellpadding=0 border=0 width=100%>";
		for (var i = 0; i < this._msgIds.length; i++) {
			var id = this._msgIds[i];
			var appCtxt = window.parentController 
				? window.parentController._appCtxt
				: this._appCtxt;
			var attMsg = appCtxt.cacheGet(id);
			if (!attMsg) { continue; }

			html[idx++] = "<tr><td width=65 align=right>";
			html[idx++] = AjxImg.getImageHtml("Message");
			html[idx++] = "</td><td width=1%><input name='";
			html[idx++] = ZmComposeView.FORWARD_MSG_NAME;
			html[idx++] = "' type='checkbox' checked='CHECKED' id='";
			html[idx++] = id;
			html[idx++] = "'></td><td class='nobreak'></td><td><b>";
			html[idx++] = (attMsg.subject || AjxStringUtil.htmlEncode(ZmMsg.noSubject));
			html[idx++] = "</b> <span class='ZmConvListFragment'>"
			html[idx++] = attMsg.getFragment(35);
			html[idx++] = "</span></td></tr>";
		}
		html[idx++] = "</table>";

		if (this._msgIds.length >= ZmComposeView.SHOW_MAX_ATTACHMENTS) {
			this._attcDiv.style.height = ZmComposeView.MAX_ATTACHMENT_HEIGHT;
			this._attcDiv.style.overflow = "auto";
		}
		this._attachCount = this._msgIds.length;
	}
	else if (replyPref == ZmSetting.INCLUDE_ATTACH ||
			 action == ZmOperation.FORWARD_ATT)
	{
		html[idx++] = "<table cellspacing=4 cellpadding=0 border=0 width=100%><tr><td width=60 align=right>";
		html[idx++] = AjxImg.getImageHtml("Attachment");
		html[idx++] = "</td><td><b>";
		html[idx++] = ((msg ? msg.subject : null) || AjxStringUtil.htmlEncode(ZmMsg.noSubject));
		html[idx++] = "</b></td></tr></table>";

		this._attachCount = 1;
	}
	else if (msg && msg.hasAttach)
	{
		var attLinks = msg.getAttachmentLinks();
		if (attLinks.length > 0) {
			html[idx++] = "<table cellspacing=0 cellpadding=0 border=0 width=100%>";
			for (var i = 0; i < attLinks.length; i++) {
				var att = attLinks[i];
				html[idx++] = "<tr><td width=65 align=right>";
				// only add icon for first attachment
				if (i == 0) {
					html[idx++] = AjxImg.getImageHtml("Attachment");
				}
				html[idx++] = "</td><td width=1%>";
				if (action != ZmOperation.NEW_MESSAGE) { // Disallow unchecking of attachments inserted automatically.
					html[idx++] = "<input name='";
					html[idx++] = ZmComposeView.FORWARD_ATT_NAME;
					html[idx++] = "' type='checkbox'";
					if (action == ZmOperation.FORWARD || 
						action == ZmOperation.FORWARD_INLINE || 
						action == ZmOperation.DRAFT)
					{
						html[idx++] = " CHECKED";
					}
					html[idx++] = " id='";
					html[idx++] = att.part;
					html[idx++] = "'>";
				}
				html[idx++] = "</td><td class='nobreak'>";
				html[idx++] = att.link;
				html[idx++] = AjxStringUtil.htmlEncode(att.label);
				html[idx++] = "</a>";
				if (att.size) {
					html[idx++] = "&nbsp;(";
					html[idx++] = att.size;
					html[idx++] = ")";
				}
				html[idx++] = "</td></tr>";
			}
			html[idx++] = "</table>";

			if (attLinks.length >= ZmComposeView.SHOW_MAX_ATTACHMENTS) {
				this._attcDiv.style.height = ZmComposeView.MAX_ATTACHMENT_HEIGHT;
				this._attcDiv.style.overflow = "auto";
			}

			this._attachCount = attLinks.length;
		}
	}

	this._attcDiv.innerHTML = html.join("");
};

// Miscellaneous methods
ZmComposeView.prototype._resetBodySize =
function() {
	var size = this.getSize();
	if (size.x <= 0 || size.y <= 0)
		return;

	var height = size.y - Dwt.getSize(this.getHtmlElement().firstChild).y;
	this._htmlEditor.setSize(size.x, height);
};

// Show address field
ZmComposeView.prototype._showAddressField =
function(type, show, skipNotify, skipFocus) {
	this._using[type] = show;
	Dwt.setVisible(document.getElementById(this._divId[type]), show);
	this._field[type].value = ""; // bug fix #750 and #3680
	this._field[type].noTab = !show;
	var setting = ZmComposeView.ADDR_SETTING[type];
	if (setting) {
		this._appCtxt.set(setting, show, null, false, skipNotify);
	}
	this._resetBodySize();
};

// Grab the addresses out of the form. Optionally, they can be returned broken out into good and
// bad addresses, with an aggregate list of the bad ones also returned. If the field is hidden,
// its contents are ignored.
ZmComposeView.prototype._collectAddrs =
function() {
	var addrs = {};
	addrs[ZmComposeView.BAD] = new AjxVector();
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (!this._using[type]) continue;
		var val = AjxStringUtil.trim(this._field[type].value);
		if (val.length == 0) continue;
		addrs.gotAddress = true;
		var result = AjxEmailAddress.parseEmailString(val, type, false);
		addrs[type] = result;
		if (result.bad.size()) {
			addrs[ZmComposeView.BAD].addList(result.bad);
			if (!addrs.badType)
				addrs.badType = type;
		}
	}
	return addrs;
};

// Returns a string representing the form content
ZmComposeView.prototype._formValue =
function(incAddrs, incSubject) {
	var vals = [];
	if (incAddrs) {
		for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
			var type = ZmMailMsg.COMPOSE_ADDRS[i];
			if (this._using[type])
				vals.push(this._field[type].value);
		}
	}
	if (incSubject)
		vals.push(this._subjectField.value);
	vals.push(this._htmlEditor.getContent());
	var str = vals.join("|");
	str = str.replace(/\|+/, "|");
	return str;
};

// Returns true if any of the attachment fields is populated
ZmComposeView.prototype._gotAttachments =
function() {
	var atts = document.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME);
	for (var i = 0; i < atts.length; i++)
		if (atts[i].value.length)
			return true;
	return false;
};


// Listeners

// Address buttons invoke contact picker
ZmComposeView.prototype._addressButtonListener =
function(ev, addrType) {
	var obj = ev ? DwtUiEvent.getDwtObjFromEvent(ev) : null;
	this.enableInputs(false);

	if (!this._contactPicker) {
		AjxDispatcher.require("ContactsCore");
		var buttonInfo = [
			{ id: AjxEmailAddress.TO,	label: ZmMsg[AjxEmailAddress.TYPE_STRING[AjxEmailAddress.TO]] },
			{ id: AjxEmailAddress.CC,	label: ZmMsg[AjxEmailAddress.TYPE_STRING[AjxEmailAddress.CC]] },
			{ id: AjxEmailAddress.BCC,	label: ZmMsg[AjxEmailAddress.TYPE_STRING[AjxEmailAddress.BCC]] }];
		this._contactPicker = new ZmContactPicker(this._appCtxt, buttonInfo);
		this._contactPicker.registerCallback(DwtDialog.OK_BUTTON, this._contactPickerOkCallback, this);
		this._contactPicker.registerCallback(DwtDialog.CANCEL_BUTTON, this._contactPickerCancelCallback, this);
	}

	var curType = obj ? obj.addrType : addrType;
	var a = {};
	var addrs = this._collectAddrs();
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		if (addrs[type]) {
			a[type] = addrs[type].good.getArray();
		}
	}
	this._contactPicker.addPopdownListener(this._controller._dialogPopdownListener);
	var str = (this._field[curType].value && !(a[curType] && a[curType].length)) ? this._field[curType].value : "";
	this._contactPicker.popup(curType, a, str);
};

ZmComposeView.prototype._controlListener =
function() {
	this._resetBodySize();
};


// Callbacks

// Transfers addresses from the contact picker to the compose view.
ZmComposeView.prototype._contactPickerOkCallback =
function(addrs) {
	this.enableInputs(true);
	for (var i = 0; i < ZmMailMsg.COMPOSE_ADDRS.length; i++) {
		var type = ZmMailMsg.COMPOSE_ADDRS[i];
		var vec = addrs[type];
		var addr = (vec.size() > 0) ? vec.toString(AjxEmailAddress.SEPARATOR) + AjxEmailAddress.SEPARATOR : "";
		this.setAddress(ZmMailMsg.COMPOSE_ADDRS[i], addr);
	}
	this._contactPicker.removePopdownListener(this._controller._dialogPopdownListener);
	this._contactPicker.popdown();
	this.reEnableDesignMode();
};

ZmComposeView.prototype._contactPickerCancelCallback =
function() {
	this.enableInputs(true);
	this.reEnableDesignMode();
};

// this callback is triggered when an event occurs inside the html editor (when in HTML mode)
// it is used to set focus to the To: field when user hits the TAB key
ZmComposeView.prototype._htmlEditorEventCallback =
function(args) {
	var rv = true;
	if (args.type == "keydown") {
		var key = DwtKeyEvent.getCharCode(args);
		if (key == DwtKeyEvent.KEY_TAB) {
			var toField = document.getElementById(this._fieldId[AjxEmailAddress.TO]);
			if (toField) {
				this._appCtxt.getKeyboardMgr().grabFocus(toField);
			}
			rv = false;
		}
	}
	return rv;
};

// needed to reset design mode when in html compose format for gecko
ZmComposeView.prototype._okCallback =
function() {
	this._msgDialog.popdown();
	this._controller._toolbar.enableAll(true);
	this.reEnableDesignMode();
};

// User has agreed to send message without a subject
ZmComposeView.prototype._noSubjectOkCallback =
function() {
	this._noSubjectOkay = true;
	// not sure why: popdown (in FF) seems to create a race condition,
	// we can't get the attachments from the document anymore.
	// W/in debugger, it looks fine, but remove the debugger and any
	// alerts, and gotAttachments will return false after the popdown call.

 	if (AjxEnv.isIE) {
		this._confirmDialog.popdown();
 	}
	// bug fix# 3209
	// - hide the dialog instead of popdown (since window will go away anyway)
	if (AjxEnv.isNav && this._controller.isChildWindow) {
		this._confirmDialog.setVisible(false);
	}

	// dont make any calls after sendMsg if child window since window gets destroyed
	if (this._controller.isChildWindow && !AjxEnv.isNav) {
		this._controller.sendMsg();
	} else {
		// bug fix #3251 - call popdown BEFORE sendMsg
		this._confirmDialog.popdown();
		this._controller.sendMsg();
	}
};

// User has canceled sending message without a subject
ZmComposeView.prototype._noSubjectCancelCallback =
function() {
	this.enableInputs(true);
	this._confirmDialog.popdown();
	this._appCtxt.getKeyboardMgr().grabFocus(this._subjectField);
	this._controller._toolbar.enableAll(true);
	this.reEnableDesignMode();
};

// User has agreed to send message with bad addresses
ZmComposeView.prototype._badAddrsOkCallback =
function() {
	this.enableInputs(true);
	this._badAddrsOkay = true;
	this._confirmDialog.popdown();
	this._controller.sendMsg();
};

// User has declined to send message with bad addresses - set focus to bad field
ZmComposeView.prototype._badAddrsCancelCallback =
function(type) {
	this.enableInputs(true);
	this._badAddrsOkay = false;
	this._confirmDialog.popdown();
	if (this._using[type]) {
		this._appCtxt.getKeyboardMgr().grabFocus(this._field[type]);
	}
	this._controller._toolbar.enableAll(true);
	this.reEnableDesignMode();
};

// Files have been uploaded, re-initiate the send with an attachment ID.
ZmComposeView.prototype._attsDoneCallback =
function(isDraft, status, attId) {
	DBG.println(AjxDebug.DBG1, "Attachments: isDraft = " + isDraft + ", status = " + status + ", attId = " + attId);
	if (status == AjxPost.SC_OK) {
		this._controller.sendMsg(attId, isDraft);
	} else if (status == AjxPost.SC_UNAUTHORIZED) {
		// auth failed during att upload - let user relogin, continue with compose action
		var ex = new AjxException("401 response during attachment upload", ZmCsfeException.SVC_AUTH_EXPIRED);
		var execFrame = new AjxCallback(this._controller,
							isDraft ? this._controller._saveDraft : this._controller._send);
		this._controller._handleException(ex, execFrame);
	} else {
		// bug fix #2131 - handle errors during attachment upload.
		var msg = AjxMessageFormat.format(ZmMsg.errorAttachment, (status || AjxPost.SC_NO_CONTENT));
		switch (status) {
			// add other error codes/message here as necessary
			case AjxPost.SC_REQUEST_ENTITY_TOO_LARGE: 	msg += " " + ZmMsg.errorAttachmentTooBig + "<br><br>"; break;
			default: 									msg += " "; break;
		}

		this._controller.popupErrorDialog(msg + ZmMsg.errorTryAgain, null, null, true);
		this._controller._toolbar.enableAll(true);
	}
};

ZmComposeView.prototype._setFormValue =
function() {
	this._origFormValue = this._formValue();
};

ZmComposeView.prototype._focusHtmlEditor =
function() {
	this._htmlEditor.focus();
};


// Static methods

ZmComposeView._onClick =
function(ev) {
	ev || (ev = window.event);

	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	var id = element ? element.id : null;

	// if clicked on remove attachment link
	if (id && id.indexOf("_att_") == 0) {
		var cv = AjxCore.objectWithId(element._composeView);
		var attId = id.slice(0, -2);
		var row = document.getElementById(attId);

		cv._attachmentTable.deleteRow(row.rowIndex);
		if (--cv._attachCount < ZmComposeView.SHOW_MAX_ATTACHMENTS) {
			cv._attcDiv.style.overflow = "";
			cv._attcDiv.style.height = "";
			if (cv._attachCount == 0) {
				cv._attachmentTable = null;
				cv._attcDiv.innerHTML = "";
			}
		}
		cv._resetBodySize();
		return false; // disables following of link
	}

	return true;
};

ZmComposeView._onKeyDown =
function(ev) {
	ev || (ev = window.event);

	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	if (!element) return true;

	var id = element.id;
	var key = DwtKeyEvent.getCharCode(ev);
	// ignore return in attachment input field (bug 961)
	if (id.indexOf("_att_") == 0)
		return (key != DwtKeyEvent.KEY_ENTER && key != DwtKeyEvent.KEY_END_OF_TEXT);
};

// NOTE: this handler should only get triggered if/when contacts are DISABLED!
ZmComposeView._onKeyUp =
function(ev) {
	ev || (ev = window.event);

	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	if (!element) return true;

	var cv = AjxCore.objectWithId(element._composeView);
	cv._adjustAddrHeight(element);
};
