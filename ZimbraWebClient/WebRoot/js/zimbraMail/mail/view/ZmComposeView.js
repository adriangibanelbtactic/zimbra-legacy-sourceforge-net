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
* Creates a new compose view. The view does not display itself on construction.
* @constructor
* @class
* This class provides a form for composing a message.
*
* @author Conrad Damon
* @param parent			[DwtControl]		the element that created this view
* @param className		[string]*			CSS class
* @param posStyle		[constant]*			positioning style (defaults to "absolute")
* @param controller		[ZmController]		controller managing this view
* @param contactPicker	[ZmContactPicker]	handle to a contact picker for selecting addresses
* @param composeMode 	[constant]			passed in so detached window knows which mode to be in on startup
*/
function ZmComposeView(parent, className, posStyle, controller, contactPicker, composeMode) {

	className = className || "ZmComposeView";
	DwtComposite.call(this, parent, className, posStyle);
	
	this._appCtxt = this.shell.getData(ZmAppCtxt.LABEL);
	this._controller = controller;
	this._contactPicker = contactPicker;
	
	// part of bug fix #941 -- attaching an iframe which we'll use
	// to send any upload requests.
	if (AjxEnv.isNav) {
		var iframe = document.createElement('iframe');
		iframe.style.display = 'none';
		this._navIframeId = iframe.id = Dwt.getNextId();
		document.body.appendChild(iframe);
	}

	this._initialize(composeMode);
};

ZmComposeView.prototype = new DwtComposite;
ZmComposeView.prototype.constructor = ZmComposeView;

// Address fields available
ZmComposeView.ADDRS = [ZmEmailAddress.TO, ZmEmailAddress.CC, ZmEmailAddress.BCC];

// Message dialog placement
ZmComposeView.DIALOG_X = 50;
ZmComposeView.DIALOG_Y = 100;

// The iframe holds a form with attachment input fields
ZmComposeView.IFRAME_HEIGHT = 30;
ZmComposeView.UPLOAD_FIELD_NAME = "attUpload";

// Minimum height of compose body textarea
ZmComposeView.MIN_BODY_HEIGHT = 300;

// Data keys
ZmComposeView.BAD = "_bad_addrs_";
ZmComposeView.FORWARD_ATT_NAME = "forAtt---" + Dwt.getNextId();

// Reply/forward stuff
ZmComposeView.EMPTY_FORM_RE = /^[\s\|]*$/;
ZmComposeView.SUBJ_PREFIX_RE = new RegExp("^\\s*(" + ZmMsg.re + "|" + ZmMsg.fwd + "|" + ZmMsg.fw + "):" + "\\s*", "i");
ZmComposeView.QUOTED_CONTENT_RE = new RegExp("^----- ", "m");

ZmComposeView.WRAP_LENGTH = 72;
ZmComposeView.QUOTED_HDRS = [ZmMailMsg.HDR_FROM, ZmMailMsg.HDR_TO, ZmMailMsg.HDR_CC,
							 ZmMailMsg.HDR_DATE, ZmMailMsg.HDR_SUBJECT];

// Public methods

ZmComposeView.prototype.toString = 
function() {
	return "ZmComposeView";
};

ZmComposeView.prototype.getController =
function() {
	return this._controller;
};

/**
* Sets the current view, based on the given action. The compose form is 
* created and laid out and everything is set up for interaction with the user.
*
* @param action			new message, reply, forward, or some variation thereof
*               		REPLY_ACCEPT, REPLY_DECLINE, REPLY_TENTATIVE, REPLY_NEW_TIME.
* @param msg			the original message (reply/forward), or address (new message)
* @param toOverride 	init's To: field w/ given value
* @param subjOverride 	init's Subject field w/ given value
* @param extraBodyText 	body text that gets prepended before real msg body gets addded (invites)
*/
ZmComposeView.prototype.set =
function(action, msg, toOverride, subjOverride, extraBodyText) {

	this._action = action;
	this._msg = msg;
	
	this.reset(true);

	// create iframe EVERY time
	this._iframe = this._createAttachmentsContainer();
	
	// reset To/Cc/Bcc fields
	this._showField(ZmEmailAddress.TO, true);
	this._showField(ZmEmailAddress.CC, false);
	this._showField(ZmEmailAddress.BCC, false);

	// populate fields based on the action and user prefs
	this._setAddresses(action, toOverride);
	this._setSubject(action, msg, subjOverride);
	this._setBody(action, msg, extraBodyText);

	// save form state (to check for change later)
	this._origFormValue = this._formValue();

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
		this._forwardDiv.innerHTML = params.forwardHtml;
};

ZmComposeView.prototype.getComposeMode = 
function() {
	return this._composeMode;
};

ZmComposeView.prototype.setFocus = 
function() {
	// set the cursor to either to To address for new message or a forward
	if (this._action == ZmOperation.NEW_MESSAGE || this._action == ZmOperation.FORWARD) {
		this._field[ZmEmailAddress.TO].focus();
	} else {
		// otherwise set cursor to the beginning of first line
		this._setBodyFieldFocus();
	}
};

// Sets the mode ZmHtmlEditor should be in.
ZmComposeView.prototype.setComposeMode = 
function(composeMode) {
	if (composeMode == DwtHtmlEditor.TEXT || 
		(composeMode == DwtHtmlEditor.HTML && this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)))
	{
		this._composeMode = composeMode;
	
		this._htmlEditor.setMode(composeMode, true);
		// dont forget to reset the body field Id and object ref
		this._bodyFieldId = this._htmlEditor.getBodyFieldId();
		this._bodyField = Dwt.getDomObj(this.getDocument(), this._bodyFieldId);
		if (this._bodyField.disabled)
			this._bodyField.disabled = false;
		
		// for now, always reset message body size
		this._resetBodySize();
	}
};

ZmComposeView.prototype.getOrigMsg = 
function() {
	return this._msg;
};

ZmComposeView.prototype.reEnableDesignMode = 
function() {
	if (this._composeMode == DwtHtmlEditor.HTML)
		this._htmlEditor.reEnableDesignMode();
};

// triggered every time user saves draft. Here, we reset "dirty-ness"
ZmComposeView.prototype.draftSaved = 
function() {
	// save form state (to check for change later)
	this._origFormValue = this._formValue();
};

// user just saved draft, update compose view as necessary
ZmComposeView.prototype.processMsgDraft = 
function(msgDraft) {
	this._action = ZmOperation.DRAFT;
	this._msg = msgDraft;
	// always redo att links since user couldve removed att before saving draft
	this._iframe = null; // XXX: force iframe to get recreated
	this._showForwardField(msgDraft, ZmOperation.DRAFT);
	this._resetBodySize();
};

ZmComposeView.prototype._isInviteReply =
function(action){
	return (action == ZmOperation.REPLY_ACCEPT || 
			action == ZmOperation.REPLY_DECLINE ||
			action == ZmOperation.REPLY_TENTATIVE ||
			action == ZmOperation.REPLY_NEW_TIME);
};

/*
* Set various address headers based on the original message and the mode we're in.
* Make sure not to duplicate any addresses, even across fields.
*/
ZmComposeView.prototype._setAddresses =
function(action, toOverride) {
	if (this._action == ZmOperation.NEW_MESSAGE && toOverride) {
		this.setAddress(ZmEmailAddress.TO, toOverride);
	} else if (this._action == ZmOperation.REPLY || this._action == ZmOperation.REPLY_ALL ||
			   this._isInviteReply(this._action)) {

		var used = {};
		used[this._appCtxt.get(ZmSetting.USERNAME)] = true; // don't add user addr to To: or Cc:
		if (!this._msg.isSent) {
			var addr = this._getAddrString(this._msg.getReplyAddresses(this._action), used);
			this.setAddress(ZmEmailAddress.TO, addr);
		}

		// reply to all senders if reply all (includes To: and Cc:)
		if (this._action == ZmOperation.REPLY_ALL) {
			var addrs = new AjxVector();
			addrs.addList(this._msg.getAddresses(ZmEmailAddress.CC));
			var toAddrs = this._msg.getAddresses(ZmEmailAddress.TO);
			if (this._msg.isSent) {
				// sent msg replicates To: and Cc: (minus duplicates)
				this.setAddress(ZmEmailAddress.TO, this._getAddrString(toAddrs, used));
			} else {
				addrs.addList(toAddrs);
			}
			this.setAddress(ZmEmailAddress.CC, this._getAddrString(addrs, used));
		}
	} else if (this._action == ZmOperation.DRAFT || this._action == ZmOperation.SHARE) {
		for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
			var addrs = this._msg.getAddresses(ZmComposeView.ADDRS[i]);
			this.setAddress(ZmComposeView.ADDRS[i], addrs.getArray().join(ZmEmailAddress.SEPARATOR));
		}
	}
};

/*
* Creates an address string from the given vector, excluding any that have
* already been used.
*
* @param addrVec	[AjxVector]		vector of ZmEmailAddress
* @param used		[Object]		hash of addresses that have been used
*/
ZmComposeView.prototype._getAddrString =
function(addrVec, used) {
	var a = addrVec.getArray();
	var addrs = [];
	for (var i = 0; i < a.length; i++) {
		var addr = a[i];
		var email = addr.getAddress();
		if (!used[email])
			addrs.push(addr);
		used[email] = true;
	}
	return addrs.join(ZmEmailAddress.SEPARATOR); // calls implicit toString() on each addr object
};

ZmComposeView.prototype.getTitle =
function() {
	var text;
	if (this._action == ZmOperation.REPLY)
		text = ZmMsg.reply;
	else if (this._action == ZmOperation.FORWARD)
		text = ZmMsg.forward;
	else
		text = ZmMsg.compose;
	var title = [ZmMsg.zimbraTitle, text].join(": ");
	return title;
};

ZmComposeView.prototype._setSubject =
function(action, msg, subjOverride) {
	if (action == ZmOperation.NEW_MESSAGE) return;

	var subj = subjOverride || msg.getSubject();

	if (action != ZmOperation.DRAFT && subj) {
		var regex = ZmComposeView.SUBJ_PREFIX_RE;
		while (regex.test(subj))
			subj = subj.replace(regex, "");
	}

	var prefix = "";	
	switch (action) {
		case ZmOperation.REPLY:
		case ZmOperation.REPLY_ALL: 		prefix = ZmMsg.re + ": "; break;
		case ZmOperation.FORWARD: 			prefix = ZmMsg.fwd + ": "; break;
		case ZmOperation.REPLY_ACCEPT:		prefix = ZmMsg.subjectAccept + ": "; break;
		case ZmOperation.REPLY_DECLINE:		prefix = ZmMsg.subjectDecline + ": "; break;
		case ZmOperation.REPLY_TENTATIVE:	prefix = ZmMsg.subjectTentative + ": "; break;
		case ZmOperation.REPLY_NEW_TIME:	prefix = ZmMsg.subjectNewTime + ": "; break;
	}
	this._subjectField.value = prefix + (subj || "");
};

ZmComposeView.prototype._setBody =
function(action, msg, extraBodyText) {
	
	if (action == ZmOperation.NEW_MESSAGE) {
		if (this._appCtxt.get(ZmSetting.SIGNATURE_ENABLED))
			this.addSignature();
		return;
	}

	var composingHtml = this._composeMode == DwtHtmlEditor.HTML;
	
	// XXX: consolidate this code later.
	if (action == ZmOperation.DRAFT || action == ZmOperation.SHARE) {
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
		return;
	}

	var sigStyle = this._appCtxt.get(ZmSetting.SIGNATURE_ENABLED) && this._appCtxt.get(ZmSetting.SIGNATURE)
		? this._appCtxt.get(ZmSetting.SIGNATURE_STYLE)
		: null;

	if (sigStyle == ZmSetting.SIG_OUTLOOK)
		this.addSignature();

	var value = "";
	var pref = this._appCtxt.get((action == ZmOperation.FORWARD) ? ZmSetting.FORWARD_INCLUDE_ORIG : ZmSetting.REPLY_INCLUDE_ORIG);
	if (pref == ZmSetting.INCLUDE_NONE) {
		if (extraBodyText)
			value += extraBodyText;
	} else if (pref == ZmSetting.INCLUDE_ATTACH) {
		this._msgAttId = this._msg.id;
	} else {
		var crlf = composingHtml ? "<br>" : ZmMsg.CRLF;
		var crlf2 = composingHtml ? "<br><br>" : ZmMsg.CRLF2;
		var leadingText = extraBodyText ? extraBodyText + crlf : crlf;
		var body = null;
		if (composingHtml) {
			body = msg.getBodyPart(ZmMimeTable.TEXT_HTML);
			if (body) {
				body = body.content;
			} else {
				// if no html part exists, just grab the text 
				var bodyPart = msg.getBodyPart();
				body = bodyPart ? this._getTextPart(bodyPart, true) : null;
			}
		} else {
			// grab text part out of the body part
			var bodyPart = msg.getBodyPart(ZmMimeTable.TEXT_PLAIN);
			body = bodyPart ? this._getTextPart(bodyPart) : null;
		}
		
		body = body || ""; // prevent from printing "null" if no body found
		
		// bug fix# 3215 - dont allow prefixing for html msgs
		if (pref == ZmSetting.INCLUDE || composingHtml) {
			var msgText = (action == ZmOperation.FORWARD) ? ZmMsg.forwardedMessage : ZmMsg.origMsg;
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
			var from = msg.getAddress(ZmEmailAddress.FROM);
			if (!from && msg.isSent)
				from = this._appCtxt.get(ZmSetting.USERNAME);
			var preface = "";
			if (from)
				preface = ZmMsg.DASHES + " " + from.toString() + " " + ZmMsg.wrote + ":" + crlf;
			var prefix = this._appCtxt.get(ZmSetting.REPLY_PREFIX);
			if (pref == ZmSetting.INCLUDE_PREFIX) {
				value += leadingText + preface + AjxStringUtil.wordWrap(body, ZmComposeView.WRAP_LENGTH, prefix + " ");
			} else if (pref == ZmSetting.INCLUDE_SMART) {
				var chunks = AjxStringUtil.getTopLevel(body);
				for (var i = 0; i < chunks.length; i++)
					chunks[i] = AjxStringUtil.wordWrap(chunks[i], ZmComposeView.WRAP_LENGTH, prefix + " ");
				var text = chunks.length ? chunks.join('\n\n') : body;
				value += leadingText + preface + text;
			}
		}
	}
	
	// bug fix #2684 - if we inserted a signature, lets preserve it!
	var sig = this._htmlEditor.getContent();
	value = sig ? (sig + value) : value;

	this._htmlEditor.setContent(value);
	
	if (sigStyle == ZmSetting.SIG_INTERNET)
		this.addSignature();

	this._showForwardField(msg, action, pref);
};

// returns the text part given a body part 
// (if body part is HTML, converts it to text)
ZmComposeView.prototype._getTextPart =
function(bodyPart, encodeSpace) {
	// if the only content type returned is html, convert to text
	return bodyPart.ct == ZmMimeTable.TEXT_HTML
		? AjxStringUtil.convertHtml2Text(Dwt.parseHtmlFragment("<div>" + bodyPart.content + "</div>"))
		: (encodeSpace ? AjxStringUtil.convertToHtml(bodyPart.content) : bodyPart.content);
};

/**
* Revert compose view to a clean state (usually called before popping compose view)
*/
ZmComposeView.prototype.reset =
function(bEnableInputs) {
	// reset autocomplete list
	if (this._acAddrSelectList) {
		this._acAddrSelectList.reset();
		this._acAddrSelectList.show(false);
	}
	
	// reset To/CC/BCC fields
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++)
		this._field[ZmComposeView.ADDRS[i]].value = "";
		
	// reset subject / body fields
	this._subjectField.value = "";
	if (this._initHtmlEditor) {
		this._htmlEditor.clear();
	} else {
		this._initHtmlEditor = true;
	}
	
	// the div that holds the iframe and null out innerHTML
	this._iframe = null;
	this._iframeDiv.innerHTML = "";
	
	this._resetBodySize();
	
	// remove any forward att rows...
	this._forwardDiv.innerHTML = "";
	this._msgAttId = null;
	
	// reset form value
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
* Sets an address field.
*
* @param type	the address type
* @param addr	the address string
*/
ZmComposeView.prototype.setAddress =
function(type, addr, bDontClear) {
	addr = addr || "";
	if (addr.length && !this._using[type]) {
		this._using[type] = true;
		this._showField(type, true);
	}
	
	this._field[type].value = bDontClear ? this._field[type].value + addr : addr;
	if (this._using[type] && 
		this._action != ZmOperation.REPLY && 
		this._action != ZmOperation.REPLY_ALL)
	{
		this._field[type].focus()
	}
};

/** 
 * Adds an extra MIME part to the message. The extra parts will be
 * added, in order, to the end of the parts after the primary message
 * part.
 */
ZmComposeView.prototype.addMimePart = function(mimePart) {
	if (!this._extraParts) {
		this._extraParts = [];
	}
	this._extraParts.push(mimePart);
};

/**
* Returns the message from the form, after some basic input validation.
*/
ZmComposeView.prototype.getMsg =
function(attId, isDraft) {
	// Check destination addresses.
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

	// Is there a subject? If not, ask the user if they want to send anyway.
	var subject = AjxStringUtil.trim(this._subjectField.value);
	if (!isDraft && subject.length == 0 && !this._noSubjectOkay) {
		this.enableInputs(false);
    	this._confirmDialog.setMessage(ZmMsg.compSubjectMissing, DwtMessageDialog.WARNING_STYLE);
		this._confirmDialog.registerCallback(DwtDialog.OK_BUTTON, this._noSubjectOkCallback, this);
		this._confirmDialog.registerCallback(DwtDialog.CANCEL_BUTTON, this._noSubjectCancelCallback, this);
	    this._confirmDialog.popup(this._getDialogXY());
		return;
	}

	// Any bad addresses?  If there are bad ones, ask the user if they want to send anyway.
	if (!isDraft && addrs[ZmComposeView.BAD].size() && !this._badAddrsOkay) {
		this.enableInputs(false);
	    var bad = AjxStringUtil.htmlEncode(addrs[ZmComposeView.BAD].toString(ZmEmailAddress.SEPARATOR));
	    var msg = AjxStringUtil.resolve(ZmMsg.compBadAddresses, bad);
    	this._confirmDialog.setMessage(msg, DwtMessageDialog.WARNING_STYLE);
		this._confirmDialog.registerCallback(DwtDialog.OK_BUTTON, this._badAddrsOkCallback, this);
		this._confirmDialog.registerCallback(DwtDialog.CANCEL_BUTTON, this._badAddrsCancelCallback, this, addrs.badType);
	    this._confirmDialog.popup(this._getDialogXY());
		return;
	} else {
		this._badAddrsOkay = false;
	}
	
	// Handle any attachments
	if (!attId && this._gotAttachments()) {
		this._submitAttachments(isDraft);
		return;
	}
	
	// get list of message part id's for any forwarded attachements
	var forwardAttIds = this._getForwardAttIds();
	
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
		htmlPart.setContent(this._htmlEditor.getContent());
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
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		if (addrs[type] && addrs[type].all.size() > 0)
			msg.setAddresses(type, addrs[type].all);
	}
	
	// save a reference to the original message
	msg._origMsg = this._msg;

	if (this._action != ZmOperation.NEW_MESSAGE) {
		var isInviteReply = this._isInviteReply(this._action);
		if (this._action == ZmOperation.DRAFT) {
			msg.isReplied = this._msg.rt == "r";
			msg.isForwarded = this._msg.rt == "w";
			msg.isDraft = this._msg.isDraft;
			// check if we're resaving a draft that was originally a reply/forward
			if (msg.isDraft) {
				// if so, set both origId and the draft id
				msg.origId = msg.isReplied || msg.isForwarded ? this._msg.origId : null;
				msg.id = this._msg.id;
			}
		} else {
			msg.isReplied = this._action == ZmOperation.REPLY || this._action == ZmOperation.REPLY_ALL || isInviteReply;
			msg.isForwarded = this._action == ZmOperation.FORWARD;
			msg.origId = this._msg.id;
		}
		msg.isInviteReply = isInviteReply;
		msg.inviteMode = isInviteReply ? this._action : null;
		msg.irtMessageId = this._msg.messageId;
	}
	
	if (attId)
		msg.setAttachmentId(attId);
	
	if (this._msgAttId)
		msg.setMessageAttachmentId(this._msgAttId);

	return msg;
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

/**
* Adds the user's signature to the message body. An "internet" style signature
* is prefixed by a special line and added to the bottom. An "outlook" style
* signature is added before the quoted content.
*/
ZmComposeView.prototype.addSignature =
function() {
	var sig = this._appCtxt.get(ZmSetting.SIGNATURE);
	if (!sig) return;

	var sigStyle = this._appCtxt.get(ZmSetting.SIGNATURE_STYLE);

	var newLine = (this._composeMode == DwtHtmlEditor.HTML) ? "<br>" : "\n";

	if (this._composeMode == DwtHtmlEditor.HTML)
		sig = AjxStringUtil.htmlEncodeSpace(sig);
	sig = sig + newLine;
	
	var sep = newLine + newLine;
	if (sigStyle == ZmSetting.SIG_INTERNET)
		sep = sep + "-- " + newLine;

	var content = this._htmlEditor.getContent();
	if (sigStyle == ZmSetting.SIG_OUTLOOK) {
		if (content.match(ZmComposeView.QUOTED_CONTENT_RE))
			content = content.replace(ZmComposeView.QUOTED_CONTENT_RE, [sep, sig, newLine, "----- "].join(""));
		else
			content = [content, sep, sig].join("");
	} else {
		content = [content, sep, sig].join("");
	}

	this._htmlEditor.setContent(content);
};

// ------------------------------------------------------------------
// attachment methods
// ------------------------------------------------------------------
ZmComposeView.prototype._getAttachmentTable =
function() {
	var attTable = null;

	if (!this._iframe)
		this._iframe = this._createAttachmentsContainer();

	if (AjxEnv.isIE) {
		// get iframe doc (if doesnt exist, create new iframe)
		var iframeDoc = this._getIframeDocument();
		if (!iframeDoc)	return;
	
		attTable = Dwt.getDomObj(iframeDoc, this._attachmentTableId);

	} else {
		attTable = Dwt.getDomObj(document, this._attachmentTableId);
	}
	return attTable;
};

ZmComposeView.prototype._setAttachmentsContainerHeight =
function(add) {
	if (AjxEnv.isIE) {
		var height = parseInt(this._iframe.style.height);
		if (add) {
			height += ZmComposeView.IFRAME_HEIGHT;
		} else {
			height -= ZmComposeView.IFRAME_HEIGHT;
		}
		this._iframe.style.height = height
	}
};

ZmComposeView.prototype._submitAttachments =
function(isDraft) {
	var callback = new AjxCallback(this, this._attsDoneCallback, [isDraft]);
	var um = this._appCtxt.getUploadManager();
	window._uploadManager = um;
	var attCon = null;
	if (AjxEnv.isIE) {
		attCon = this._iframe;
	} else {
		var iframe = document.getElementById(this._navIframeId);
		iframe.style.display = "block";
		var uploadForm = document.getElementById(this._uploadFormId);
		var idoc = Dwt.getIframeDoc(iframe);
		idoc.body.appendChild(uploadForm);
		attCon = iframe;
	}
	um.execute(attCon, callback, this._uploadFormId);
};

/**
* Adds an attachment file upload field to the compose form.
*/
ZmComposeView.prototype.addAttachmentField =
function() {

	// just in case... iframes are tempermental
	var attTable = this._getAttachmentTable();

	if (!attTable) return;
	
	// add new row
	var	row = attTable.insertRow(-1);
	var attId = "_att_" + Dwt.getNextId();
	var attRemoveId = attId + "_r";
	var attInputId = attId + "_i";
	row.id = attId;
	row.style.height = ZmComposeView.IFRAME_HEIGHT;

	// add new cell and build html for inserting file upload input element
	var	cell = row.insertCell(-1);
	var html = new Array();
	var idx = 0;
	html[idx++] = "<table cellspacing=4 cellpadding=0 border=0><tr>";
	html[idx++] = "<td><div class='attachText'>" + ZmMsg.attachFile + ":</div></td>";
	html[idx++] = "<td class='nobreak'>";
	html[idx++] = "<input id='" + attInputId + "' type='file' name='" + ZmComposeView.UPLOAD_FIELD_NAME + "' size=40>&nbsp;";
	html[idx++] = "<span id='" + attRemoveId + "'";
	html[idx++] = " onmouseover='this.style.cursor=\"pointer\"' onmouseout='this.style.cursor=\"default\"' style='color:blue;text-decoration:underline;'";
	html[idx++] = ">" + ZmMsg.remove + "</span>";
	html[idx++] = "</td></tr></table>";
	cell.innerHTML = html.join("");
	
	this._setEventHandler(attRemoveId, "onClick", null, !AjxEnv.isNav);
	// trap key presses in IE for input field so we can ignore ENTER key (bug 961)
	if (AjxEnv.isIE)
		this._setEventHandler(attInputId, "onKeyDown", null, !AjxEnv.isNav);
	this._setAttachmentsContainerHeight(true);
	this._resetBodySize();
};

ZmComposeView.prototype.enableInputs = 
function(bEnable) {
	// disable input elements so they dont bleed into top zindex'd view
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++)
		this._field[ZmComposeView.ADDRS[i]].disabled = !bEnable;
	
	this._subjectField.disabled = this._bodyField.disabled = !bEnable;
};

ZmComposeView.prototype.getHtmlEditor = 
function() {
	return this._htmlEditor;
};

// Private methods

/**
* This should be called only once for when compose view loads first time around
*/
ZmComposeView.prototype._initialize = 
function(composeMode) {
	// init address field objects
	this._divId = new Object();
	this._buttonTdId = new Object();
	this._fieldId = new Object();
	this._using = new Object();
	this._addLinkId = new Object();
	this._button = new Object();
	this._field = new Object();
	this._internalId = AjxCore.assignId(this);
	// init element IDs for address fields
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		this._divId[type] = Dwt.getNextId();
		this._buttonTdId[type] = Dwt.getNextId();
		this._fieldId[type] = Dwt.getNextId();
		this._addLinkId[type] = Dwt.getNextId();
	}

	// init element IDs
	this._subjectFieldId = Dwt.getNextId();
	this._forwardDivId = Dwt.getNextId();
	this._attachmentTableId = Dwt.getNextId();
	this._iframeDivId = Dwt.getNextId();
	this._iframeId = Dwt.getNextId();
	this._uploadFormId = Dwt.getNextId();

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
	this._htmlEditor.addEventCallback(new AjxCallback(this, this._htmlEditorEventCallback));
	this._bodyFieldId = this._htmlEditor.getBodyFieldId();
	
	var doc = this.getDocument();

	// save references to dom objects per Ids.
	this._subjectField = Dwt.getDomObj(doc, this._subjectFieldId);
	this._bodyField = Dwt.getDomObj(doc, this._bodyFieldId);
	this._forwardDiv = Dwt.getDomObj(doc, this._forwardDivId);
	this._iframeDiv = Dwt.getDomObj(doc, this._iframeDivId);

	// misc. inits
	this._confirmDialog = new DwtMessageDialog(this.shell, null, [DwtDialog.OK_BUTTON, DwtDialog.CANCEL_BUTTON]);
	this._msgDialog = this._appCtxt.getMsgDialog();
	this.setScrollStyle(DwtControl.SCROLL);
	
	// init listeners
	this.addControlListener(new AjxListener(this, ZmComposeView.prototype._controlListener));

	// init autocomplete list
	// TODO: add option to match against GAL contacts (based on pref? form field?)
	if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
		var contactsClass = this._appCtxt.getApp(ZmZimbraMail.CONTACTS_APP);
		var contactsLoader = contactsClass.getContactList;
		var locCallback = new AjxCallback(this, this._getAcListLoc, this);
		var params = {parent: this, dataClass: contactsClass, dataLoader: contactsLoader, 
					  matchValue: ZmContactList.AC_VALUE_FULL, locCallback: locCallback};
		this._acAddrSelectList = new ZmAutocompleteListView(params);
	}

	// init To/CC/BCC buttons and their event handlers
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		if (this._contactPicker) {
			this._button[type] = new DwtButton(this, null, "DwtButton contrast");
			var typeStr = ZmEmailAddress.TYPE_STRING[type];
			this._button[type].setText(ZmMsg[typeStr] + ":");
		
			var buttonTd = Dwt.getDomObj(doc, this._buttonTdId[type]);
			buttonTd.appendChild(this._button[type].getHtmlElement());
			buttonTd.addrType = type;
		
			this._button[type].addSelectionListener(new AjxListener(this, this._addressButtonListener));
			this._button[type].addrType = type;
		}
		
		this._field[type] = Dwt.getDomObj(doc, this._fieldId[type]);
		this._field[type].addrType = type;
		
		// autocomplete-related handlers
		if (this._appCtxt.get(ZmSetting.CONTACTS_ENABLED)) {
			this._acAddrSelectList.handle(this._field[type]);
			this._setEventHandler(this._fieldId[type], "onClick");
		}
	}

	// init event handlers for add cc/bcc links
	this._setEventHandler(this._addLinkId[ZmEmailAddress.CC], "onClick", ZmEmailAddress.CC);
	this._setEventHandler(this._addLinkId[ZmEmailAddress.BCC], "onClick", ZmEmailAddress.BCC);
};

// Listeners

// Address buttons invoke contact picker
ZmComposeView.prototype._addressButtonListener =
function(ev) {
	var obj = DwtUiEvent.getDwtObjFromEvent(ev);
	this.enableInputs(false);
	this._contactPicker.popup(obj.addrType);
};

// Click event will either be on an add address link, or in an address textarea field.
// If the former, show the address element. If the latter, hide the autocomplete list.
ZmComposeView._onClick =
function(ev) {
	// IE doesn't pass event, might have to go fishing in iframe for it
	if (AjxEnv.isIE && !window.event)
		ev = parent.window.frames[this._iframeId].event;

	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	var id = element.id;
	var cv = AjxCore.objectWithId(element._composeView);

	if (id.indexOf("_att_") == 0) {
		// click on attachment remove link, get att div id
		var attId = id.slice(0, -2);
		var doc = cv._getAttachmentsDocument();
		var row = Dwt.getDomObj(doc, attId);
		var table = Dwt.getDomObj(doc, cv._attachmentTableId);
		table.deleteRow(row.rowIndex);
		cv._setAttachmentsContainerHeight(false);
		cv._resetBodySize();
		return false; // disable following of link
	}

	switch (id) {
		case cv._addLinkId[ZmEmailAddress.TO]:
		case cv._addLinkId[ZmEmailAddress.CC]:
		case cv._addLinkId[ZmEmailAddress.BCC]:
			cv._showField(element._addrType, !cv._using[element._addrType]);
			return false; // disable following of link
	}
};

ZmComposeView._onKeyDown =
function(ev) {
	DBG.println(AjxDebug.DBG3, "onKeyDown");

	// IE doesn't pass event, might have to go fishing in iframe for it
	if (AjxEnv.isIE && !window.event)
		ev = parent.window.frames[this._iframeId].event;
	var element = DwtUiEvent.getTargetWithProp(ev, "id");
	var id = element.id;
	var key = DwtKeyEvent.getCharCode(ev);
	// ignore return in attachment input field (bug 961)
	if (id.indexOf("_att_") == 0) 
		return (key != DwtKeyEvent.KEY_ENTER && key != DwtKeyEvent.KEY_END_OF_TEXT);
};

// this callback is triggered when an event occurs inside the html editor (when in HTML mode)
// it is used to set focus to the To: field when user hits the TAB key
ZmComposeView.prototype._htmlEditorEventCallback = 
function(args) {
	var rv = true;	
	if (args.type == "keydown") {
		var key = DwtKeyEvent.getCharCode(args);
		if (key == DwtKeyEvent.KEY_TAB) {
			var toField = Dwt.getDomObj(this.getDocument(), this._fieldId[ZmEmailAddress.TO]);
			if (toField)
				toField.focus();
			rv = false;
		}
	}
	return rv;
};

ZmComposeView.prototype._createHtml =
function() {

	var div = this.getDocument().createElement("div");

	var html = new Array();
	var idx = 0;
	
	html[idx++] = "<table border=0 cellpadding=0 cellspacing=0 width=100%>";

	// create address elements
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		html[idx++] = "<tr><td><div id='" + this._divId[type] + "'";
		html[idx++] = (type != ZmEmailAddress.TO) ? " style='display: none;'>" : ">";
		html[idx++] = "<table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
		if (this._contactPicker) {
			html[idx++] = "<td valign=top width=60 id='" + this._buttonTdId[type] + "'></td>";
		} else {
			var typeStr = ZmEmailAddress.TYPE_STRING[type];
			var addrStr = ZmMsg[typeStr] + ":";
			html[idx++] = "<td width=60 align='right' valign='top' id='" + this._buttonTdId[type] + "'>" + addrStr + "</td>";
		}
		html[idx++] = "<td><textarea id='" + this._fieldId[type] + "' rows=2 class='addresses'></textarea></td>";
		html[idx++] = "</tr></table></div></td></tr>";
	}

	// create element for adding address fields
	html[idx++] = "<tr><td><table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
	html[idx++] = "<td width=60></td><td class='nobreak'>";
	// create a fake link so they dont get focus when tabbing..
	var fakeLinkStyle = "onmouseover='this.style.cursor=\"pointer\"' onmouseout='this.style.cursor=\"default\"' style='color:blue;text-decoration:underline;'";
	html[idx++] = "<span " + fakeLinkStyle + " id='" + this._addLinkId[ZmEmailAddress.CC] + "'>" + ZmMsg.addCc + "</span>";
	html[idx++] = " | <span " + fakeLinkStyle + " id='" + this._addLinkId[ZmEmailAddress.BCC] + "'>" + ZmMsg.addBcc + "</span></td>";
	html[idx++] = "</tr></table></td></tr>";

	// create subject field
	html[idx++] = "<tr><td><table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
	html[idx++] = "<td width=60 align='right'>" + ZmMsg.subject + ":</td>";
	html[idx++] = "<td><input type='text' id='" + this._subjectFieldId + "' class='subjectField'></td>";
	html[idx++] = "</tr></table></td></tr>";

	// create area to show forwarded attachment(s)
	html[idx++] = "<tr><td><div id='" + this._forwardDivId + "' /></td></tr>";
	
	// create element for adding attachments
	html[idx++] = "<tr><td><div id='" + this._iframeDivId + "' /></td></tr>";
	
	html[idx++] = "</table>";
	
	div.innerHTML = html.join("");
	this.getHtmlElement().appendChild(div);
};

ZmComposeView.prototype._createAttachmentsContainer =
function() {
	var container = null;
	var doc = this.getDocument();
	var uri = location.protocol + "//" + doc.domain + this._appCtxt.get(ZmSetting.CSFE_UPLOAD_URI);
	if (AjxEnv.isIE) {
	
		// remove old iframe if it exists
		this._iframeDiv = Dwt.getDomObj(doc, this._iframeDivId);
		this._iframeDiv.innerHTML = "";
		
		// create a brand new iframe
		var iframe = container = doc.createElement("iframe");
		iframe.id = this._iframeId;
 		if (AjxEnv.isIE && location.protocol == "https:") {
			iframe.src = "'/zimbra/public/blank.html'";
		}
		iframe.name = this._iframeId;
		iframe.frameBorder = iframe.vspace = iframe.hspace = iframe.marginWidth = iframe.marginHeight = 0;
		iframe.width = "100%";
		iframe.scrolling = "no";
		iframe.style.overflowX = iframe.style.overflowY = "visible";
		iframe.style.height = "0px";
		iframe.tabIndex = -1; // dont let iframe get focus
		this._iframeDiv.appendChild(iframe);
		
		var idoc = Dwt.getIframeDoc(iframe);
		idoc.open();
		var html = new Array();
		var idx = 0;
		html[idx++] = "<html><head><style type='text/css'>";
		html[idx++] = "P, TD, DIV, SPAN, SELECT, INPUT, TEXTAREA, BUTTON { font-family: Tahoma, Arial, Helvetica, sans-serif;	font-size:11px; }";
		html[idx++] = ".attachText { width:60px; text-align:right; white-space:nowrap; overflow:hidden; }";
		html[idx++] = "</style></head>";
		html[idx++] = "<body scroll=no bgcolor='#EEEEEE'>";
		html[idx++] = "<form method='POST' action='" + uri + "' id='" + this._uploadFormId + "' enctype='multipart/form-data'>";
		html[idx++] = "<table id='" + this._attachmentTableId + "' cellspacing=0 cellpadding=0 border=0 class='iframeTable'></table>";
		html[idx++] = "</form>";
		html[idx++] = "</body></html>";
		idoc.write(html.join(""));
		idoc.close();
	} else {
		var html = new Array();
		var idx = 0;
		html[idx++] = "<div style='overflow:visible'>";
		html[idx++] = "<form method='POST' action='" + uri + "' id='" + this._uploadFormId + "' enctype='multipart/form-data'>";
		html[idx++] = "<table id='" + this._attachmentTableId + "' cellspacing=0 cellpadding=0 border=0 class='iframeTable'></table>";
		html[idx++] = "</form>";
		html[idx++] = "</div>";
		this._iframeDiv = Dwt.getDomObj(document, this._iframeDivId);
		this._iframeDiv.innerHTML = html.join("");
		container = this._iframeDiv.firstChild;
	}
	return container;
};

ZmComposeView.prototype._getAttachmentsDocument =
function () {
	var doc = this.getDocument();
	if (AjxEnv.isIE){
		var iframe = doc.getElementById(this._iframeId);
		return Dwt.getIframeDoc(iframe);
	} else {
		return doc;
	}
};

ZmComposeView.prototype._showForwardField =
function(msg, action, pref) {
	var subj = msg.getSubject() || AjxStringUtil.htmlEncode(ZmMsg.noSubject);
	var html = new Array();
	var idx = 0;
	
	if (pref == ZmSetting.INCLUDE_ATTACH) 
	{
		html[idx++] = "<table cellspacing=4 cellpadding=0 border=0 width=100%><tr>";
		html[idx++] = "<td width=60 align=right>";
		html[idx++] = AjxImg.getImageHtml("Attachment");
		html[idx++] = "</td>";
		html[idx++] = "<td><b>" + subj + "</b></td>";
		html[idx++] = "</tr></table>";
	} 
	else if (msg && 
			((msg.hasAttach && action == ZmOperation.FORWARD) || 
			  action == ZmOperation.DRAFT)) 
	{
		var attLinks = msg.buildAttachLinks(false, this.getDocument().domain);
		if (attLinks.length > 0) {
			html[idx++] = "<table cellspacing=0 cellpadding=0 border=0 width=100%>";
			for (var i = 0; i < attLinks.length; i++) {
				html[idx++] = "<tr><td width=65 align=right>";
				if (i == 0) // only add icon for first attachment(?)
					html[idx++] = AjxImg.getImageHtml("Attachment");
				html[idx++] = "</td><td width=1%><input name='" + ZmComposeView.FORWARD_ATT_NAME + "' type='checkbox' id='" + attLinks[i].mpId + "' CHECKED></td>";
				html[idx++] = "<td valign=top class='nobreak'>" + attLinks[i].html + "</td></tr>";
			}
			html[idx++] = "</table>";
		}
	}

	this._forwardDiv.innerHTML = html.join("");
};

ZmComposeView.prototype.getForwardLinkHtml = 
function() {
	return this._forwardDiv.innerHTML;
};

ZmComposeView.prototype._controlListener = 
function() {
	this._resetBodySize();
};

// Miscellaneous methods
ZmComposeView.prototype._resetBodySize = 
function() {
	var size = this.getSize();
	if (size.x <= 0 || size.y <= 0)
		return;
	
	var height = size.y - Dwt.getSize(this.getHtmlElement().firstChild).y;
	if (height < ZmComposeView.MIN_BODY_HEIGHT)
		height = ZmComposeView.MIN_BODY_HEIGHT;
	
	this._htmlEditor.setSize(size.x, height);

	// reset scrollbars (in FF sometimes they will stay on even if it's not the case)
	var el = this.getHtmlElement();
	el.style.overflow = "hidden";
	if (height == ZmComposeView.MIN_BODY_HEIGHT)
		el.style.overflow = "auto";
};

// Consistent spot to locate various dialogs
ZmComposeView.prototype._getDialogXY =
function() {
	var loc = Dwt.toWindow(this.getHtmlElement(), 0, 0);
	return new DwtPoint(loc.x + ZmComposeView.DIALOG_X, loc.y + ZmComposeView.DIALOG_Y);
};

ZmComposeView.prototype._getForwardAttIds = 
function() {
	var forAttIds = new Array();
	// XXX: should getElementsByName be added to dwt?
	var forAttList = this.getDocument().getElementsByName(ZmComposeView.FORWARD_ATT_NAME);
	
	// walk collection of input elements
	for (var i = 0; i < forAttList.length; i++) {
		if (forAttList[i].checked)
			forAttIds.push(forAttList[i].id);
	}
	
	return forAttIds;
};

// Show address field
ZmComposeView.prototype._showField =
function(type, show) {
	var doc = this.getDocument();
	
	this._using[type] = show;
	Dwt.setVisible(Dwt.getDomObj(doc, this._divId[type]), show);
	this._field[type].value = ""; // bug fix #750 and #3680
	if (show)
		this._field[type].focus();
	var link = Dwt.getDomObj(doc, this._addLinkId[type]);
	if (link) {
		link.innerHTML = show 
			? ZmMsg.remove + " " + ZmEmailAddress.TYPE_STRING[type].toUpperCase() 
			: "Add " + ZmEmailAddress.TYPE_STRING[type].toUpperCase();
	}
	this._resetBodySize();
};

// Generic routine for attaching an event handler to a field. Since "this" for the handlers is
// the incoming event, we need a way to get at ZmComposeView, so it's added to the event target.
ZmComposeView.prototype._setEventHandler = 
function(id, event, addrType, isIframe) {
	var doc = isIframe ? this._getIframeDocument() : this.getDocument();
	var field = Dwt.getDomObj(doc, id);
	field._composeView = this._internalId;
	if (isIframe) field._iframeId = this._iframeId;
	if (addrType)
		field._addrType = addrType;
	var lcEvent = event.toLowerCase();
	field[lcEvent] = ZmComposeView["_" + event];
};

// Grab the addresses out of the form. Optionally, they can be returned broken out into good and 
// bad addresses, with an aggregate list of the bad ones also returned. If the field is hidden,
// its contents are ignored.
ZmComposeView.prototype._collectAddrs =
function() {
	var addrs = new Object();
	addrs[ZmComposeView.BAD] = new AjxVector();
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		if (!this._using[type]) continue;
		var val = AjxStringUtil.trim(this._field[type].value);
		if (val.length == 0) continue;
		addrs.gotAddress = true;
		var result = ZmEmailAddress.parseEmailString(val, type, false);
		addrs[type] = result;
		if (result.bad.size()) {
			addrs[ZmComposeView.BAD].addList(result.bad);
			if (!addrs.badType)
				addrs.badType = type;
		}
	}
	return addrs;
};

// returns the field values for each of the addr fields
ZmComposeView.prototype.getRawAddrFields = 
function() {
	var addrs = new Object();
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		if (!this._using[type])
			continue;
		addrs[type] = this._field[type].value;
	}
	return addrs;
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
 	if (AjxEnv.isIE)
		this._confirmDialog.popdown();
	// bug fix# 3209 
	// - hide the dialog instead of popdown (since window will go away anyway)
	if (AjxEnv.isNav && this._controller.isChildWindow)
		this._confirmDialog.setVisible(false);
	
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
	this._subjectField.focus();
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
	if (this._using[type])
		this._field[type].focus()
	this._controller._toolbar.enableAll(true);
	this.reEnableDesignMode();
};

// Files have been uploaded, re-initiate the send with an attachment ID.
// TODO: error handling
ZmComposeView.prototype._attsDoneCallback =
function(args) {
	if (AjxEnv.isNav){
		var iframe = top.document.getElementById(this._navIframeId);
		iframe.style.display = 'none';
	}
	DBG.println(AjxDebug.DBG1, "Attachments: isDraft = " + args[0] + ", status = " + args[1] + ", attId = " + args[2]);
	var status = args[1];
	if (status == 200) {
		var attId = args[2];
		this._controller.sendMsg(attId, args[0]);
	} else {
		DBG.println(AjxDebug.DBG1, "attachment error: " + status);
	}
};

// Returns a string representing the form content
ZmComposeView.prototype._formValue =
function(incAddrs, incSubject) {
	var vals = new Array();
	if (incAddrs) {
		for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
			var type = ZmComposeView.ADDRS[i];
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

// Gets the iframe's document whether we're IE or Moz
ZmComposeView.prototype._getIframeDocument =
function() {
	return Dwt.getIframeDoc(this._iframe);
	// OLD: return AjxEnv.isIE ? this._iframe.Document : this._iframe.contentDocument;
};

// Returns true if any of the attachment fields is populated
ZmComposeView.prototype._gotAttachments =
function() {
	var atts;
	if (AjxEnv.isIE) {
		var iframeDoc = this._getIframeDocument();
		atts = iframeDoc ? iframeDoc.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME) : [];
	} else {
		atts = document.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME);
	}
	for (var i = 0; i < atts.length; i++)
		if (atts[i].value.length)
			return true;

	return false;
};

ZmComposeView.prototype._setBodyFieldFocus = 
function(extraBodyText) {
	if (this._composeMode == DwtHtmlEditor.HTML) {
		this._htmlEditor.focus();
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

    this._bodyField.focus();
};

// returns list of attachment field values (used by detachCompose)
ZmComposeView.prototype.getAttFieldValues = 
function() {
	var attList = new Array();
	var atts = AjxEnv.isIE
		? this._getIframeDocument().getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME)
		: document.getElementsByName(ZmComposeView.UPLOAD_FIELD_NAME);

	for (var i = 0; i < atts.length; i++)
		attList.push(atts[i].value);
	
	return attList;
};

// Returns the location where the autocomplete list should be positioned. Run as a callback.
ZmComposeView.prototype._getAcListLoc =
function(args) {
	var cv = args[0];
	var ev = args[1];
	var element = ev.element;
	var id = element.id;

	// Figure out proper location for autocomplete list. A bit hacky since the address fields are
	// statically positioned within tables (Dwt.getLocation() returns offset from window).
	var type = element.addrType;
	var field = Dwt.getDomObj(cv.getDocument(), cv._divId[type]);

	// find out how many address fields visible above this one
	var num = 0;
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var t = ZmComposeView.ADDRS[i];
		if (cv._using[t] && t < type)
			num++;
	}
	var size = Dwt.getSize(field);
	// 70 = button width + 2 borders + 2 cell spacing
	// 54 = textarea height + 1 cell spacing
	return new DwtPoint(70, 54 + (num * size.y));
};
