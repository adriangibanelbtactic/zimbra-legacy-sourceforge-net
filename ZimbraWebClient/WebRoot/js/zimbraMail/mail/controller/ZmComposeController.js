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
* Creates a new compose controller to manage message composition.
* @constructor
* @class
* This class manages message composition.
*
* @author Conrad Damon
* @param appCtxt		the application context
* @param container		the containing element
* @param mailApp		a handle to the mail application
*/
function ZmComposeController(appCtxt, container, mailApp) {

	ZmController.call(this, appCtxt, container, mailApp);

	this._action = null;
	// only add listener if this is not a child window
	if (mailApp._parentController == null)
		this._appCtxt.getSettings().addChangeListener(new AjxListener(this, this._settingsChangeListener));
};

ZmComposeController.prototype = new ZmController();
ZmComposeController.prototype.constructor = ZmComposeController;

ZmComposeController.prototype.toString =
function() {
	return "ZmComposeController";
};

// Public methods

ZmComposeController.prototype.doAction =
function(action, inNewWindow, msg, toOverride, subjOverride, extraBodyText) {
	if (inNewWindow) {
		var newWin = this._appCtxt.getNewWindow();

		// this is how child window knows what to do once loading:
		newWin.command = "compose";
		newWin.args = [action, msg, toOverride, subjOverride, extraBodyText];
	} else {
		this._setView(action, msg, toOverride, subjOverride, extraBodyText);
	}
};

ZmComposeController.prototype.toggleSpellCheckButton = 
function(toggled) {
	var spellCheckButton = this._toolbar.getButton(ZmOperation.SPELL_CHECK);
	spellCheckButton.setToggled((toggled || false));
};

/**
* Detaches compose view to child window
*
* @param msg	the original message
*/
ZmComposeController.prototype.detach =
function() {
	var newWin = this._appCtxt.getNewWindow();

	// this is how child window knows what to do once loading:
	newWin.command = "composeDetach";

	var msg = this._composeView.getOrigMsg();
	var addrs = this._composeView.getRawAddrFields();
	var subj = this._composeView._subjectField.value;
	var forAttHtml = this._composeView.getForwardLinkHtml();
	var body = this._composeView.getHtmlEditor().getContent();
	var composeMode = this._composeView.getComposeMode();

	newWin.args = {action: this._action, msg: msg, addrs: addrs, subj: subj, forwardHtml: forAttHtml, body: body, composeMode: composeMode };
};

ZmComposeController.prototype.popShield =
function() {
	if (!this._composeView.isDirty())
		return true;

	if (!this._popShield) {
		if (this._appCtxt.get(ZmSetting.SAVE_DRAFT_ENABLED)) {
			this._popShield = new DwtMessageDialog(this._shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON, DwtDialog.CANCEL_BUTTON]);
			this._popShield.setMessage(ZmMsg.askSaveDraft, DwtMessageDialog.WARNING_STYLE);
			this._popShield.registerCallback(DwtDialog.YES_BUTTON, this._popShieldYesCallback, this);
			this._popShield.registerCallback(DwtDialog.NO_BUTTON, this._popShieldNoCallback, this);
			this._popShield.registerCallback(DwtDialog.CANCEL_BUTTON, this._popShieldDismissCallback, this);
		} else {
			this._popShield = new DwtMessageDialog(this._shell, null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON]);
			this._popShield.setMessage(ZmMsg.askLeaveCompose, DwtMessageDialog.WARNING_STYLE);
			this._popShield.registerCallback(DwtDialog.YES_BUTTON, this._popShieldYesCallback, this);
			this._popShield.registerCallback(DwtDialog.NO_BUTTON, this._popShieldNoCallback, this);
		}
	}
    this._popShield.popup(this._composeView._getDialogXY());

	return false;
};

ZmComposeController.prototype._postShowCallback = 
function() {
	this._composeView.setFocus();
};

/**
* Sends the message represented by the content of the compose view.
*/
ZmComposeController.prototype.sendMsg =
function(attId, isDraft, callback) {
	var msg = this._composeView.getMsg(attId, isDraft);
	if (!msg) return;

	var contactList = !isDraft
		? this._appCtxt.getApp(ZmZimbraMail.CONTACTS_APP).getContactList() : null;

	var respCallback = new AjxCallback(this, this._handleResponseSendMsg, [isDraft, msg, callback]);
	var errorCallback = new AjxCallback(this, this._handleErrorSendMsg);
	var resp = msg.send(contactList, isDraft, respCallback, errorCallback);

	// XXX: temp bug fix #4325 - if resp returned, we're processing sync request
	//      REVERT this bug fix once mozilla fixes bug #295422!
	if (resp) {
		this._processSendMsg(isDraft, msg, resp);
	}
};


// Private methods

ZmComposeController.prototype._deleteDraft =
function(delMsg) {

	var list = delMsg.list;
	var mailItem = list && list.type == ZmItem.CONV
		? list.getById(delMsg.getConvId()) : delMsg;

	if (mailItem) {
		list.deleteItems(mailItem, true);
	} else if (delMsg.id) {
		// do a manual delete of the "virtual" conv/msg that was created but
		// never added to our internal list model
		var soapDoc = AjxSoapDoc.create("MsgActionRequest", "urn:zimbraMail");
		var actionNode = soapDoc.set("action");
		actionNode.setAttribute("id", delMsg.id);
		actionNode.setAttribute("op", "delete");
		var ac = this._appCtxt.getAppController();
		var respCallback = new AjxCallback(this, this._handleResponseDeleteDraft);
		ac.sendRequest(soapDoc, true, respCallback);
	}
};

ZmComposeController.prototype._handleResponseDeleteDraft =
function(result) {
	// force a redo Search to refresh the drafts folder
	var search = this._appCtxt.getCurrentSearch();
	if (search.folderId == ZmFolder.ID_DRAFTS)
		this._appCtxt.getSearchController().redoSearch(search);
};

// Creates the compose view based on the mode we're in. Lazily creates the
// compose toolbar, a contact picker, and the compose view itself.
ZmComposeController.prototype._setView =
function(action, msg, toOverride, subjOverride, extraBodyText, composeMode) {

	this._action = action;

	if (!this._toolbar)
		this._createToolBar();
	this._toolbar.enableAll(true);

	var needPicker = this._appCtxt.get(ZmSetting.CONTACTS_ENABLED) || this._appCtxt.get(ZmSetting.GAL_ENABLED);
	if (!this._contactPicker && needPicker) {
		var buttonInfo = [
			{ id: ZmEmailAddress.TO, value: ZmEmailAddress.TYPE_STRING[ZmEmailAddress.TO] },
			{ id: ZmEmailAddress.CC, value: ZmEmailAddress.TYPE_STRING[ZmEmailAddress.CC] },
			{ id: ZmEmailAddress.BCC, value: ZmEmailAddress.TYPE_STRING[ZmEmailAddress.BCC] }];
		this._contactPicker = new ZmContactPicker(this, this._shell, this._appCtxt, buttonInfo);
		this._contactPicker.registerCallback(DwtDialog.OK_BUTTON, this._contactPickerCallback, this);
		this._contactPicker.registerCallback(DwtDialog.CANCEL_BUTTON, this._contactPickerCancel, this);
	}

	if (!this._composeView) {
		this._composeView = new ZmComposeView(this._container, null, Dwt.ABSOLUTE_STYLE, this, this._contactPicker, composeMode);
		var callbacks = new Object();
		callbacks[ZmAppViewMgr.CB_PRE_HIDE] = new AjxCallback(this, this.popShield);
		callbacks[ZmAppViewMgr.CB_POST_SHOW] = new AjxCallback(this, this._postShowCallback);
		var elements = new Object();
		elements[ZmAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;
		elements[ZmAppViewMgr.C_APP_CONTENT] = this._composeView;
	    this._app.createView(ZmController.COMPOSE_VIEW, elements, callbacks);
	}

	// if a compose mode is already supplied, set it
	if (composeMode) {
		this._setFormatBtnItem(composeMode);
	} else {
		// otherwise, figure it out based on the given msg and mode type
		this._setComposeMode(msg);
	}

	this._composeView.set(action, msg, toOverride, subjOverride, extraBodyText);
	this._app.pushView(ZmController.COMPOSE_VIEW, true);
	this._composeView.reEnableDesignMode();
};

ZmComposeController.prototype._createToolBar =
function() {
	var buttons = [ZmOperation.SEND, ZmOperation.CANCEL, ZmOperation.SEP, ZmOperation.ATTACHMENT];

	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		buttons.push(ZmOperation.SEP);
		buttons.push(ZmOperation.COMPOSE_FORMAT);
	}
	if (this._appCtxt.get(ZmSetting.SAVE_DRAFT_ENABLED)) {
		buttons.push(ZmOperation.SAVE_DRAFT);
	}
	var addSig = (!this._appCtxt.get(ZmSetting.SIGNATURE_ENABLED) && this._appCtxt.get(ZmSetting.SIGNATURE));
	if (addSig) {
		buttons.push(ZmOperation.ADD_SIGNATURE);
	}

	buttons.push(ZmOperation.SPELL_CHECK);

	if (!this.isChildWindow) {
		buttons.push(ZmOperation.SEP);
		buttons.push(ZmOperation.DETACH_COMPOSE);
	}

	var className = this.isChildWindow ? "ZmAppToolBar_cw" : "ZmAppToolBar";
	this._toolbar = new ZmButtonToolBar(this._container, buttons, null, Dwt.ABSOLUTE_STYLE, className);
	this._toolbar.addSelectionListener(ZmOperation.SEND, new AjxListener(this, this._sendListener));
	this._toolbar.addSelectionListener(ZmOperation.CANCEL, new AjxListener(this, this._cancelListener));
	this._toolbar.addSelectionListener(ZmOperation.ATTACHMENT, new AjxListener(this, this._attachmentListener));

	// change default button style to toggle for spell check button
	var spellCheckButton = this._toolbar.getButton(ZmOperation.SPELL_CHECK);
	spellCheckButton.setAlign(DwtLabel.IMAGE_LEFT | DwtButton.TOGGLE_STYLE);

	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		var formatButton = this._toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
		var m = new DwtMenu(formatButton);
		formatButton.setMenu(m);

		var mi = new DwtMenuItem(m, DwtMenuItem.RADIO_STYLE);
		mi.setImage("HtmlDoc");
		mi.setText(ZmMsg.htmlDocument);
		mi.setData(ZmHtmlEditor._VALUE, DwtHtmlEditor.HTML);
		mi.addSelectionListener(new AjxListener(this, this._formatListener));

		mi = new DwtMenuItem(m, DwtMenuItem.RADIO_STYLE);
		mi.setImage("GenericDoc");
		mi.setText(ZmMsg.plainText);
		mi.setData(ZmHtmlEditor._VALUE, DwtHtmlEditor.TEXT);
		mi.addSelectionListener(new AjxListener(this, this._formatListener));
	}

	if (!this.isChildWindow)
		this._toolbar.addSelectionListener(ZmOperation.DETACH_COMPOSE, new AjxListener(this, this._detachListener));

	if (this._appCtxt.get(ZmSetting.SAVE_DRAFT_ENABLED))
	  	this._toolbar.addSelectionListener(ZmOperation.SAVE_DRAFT, new AjxListener(this, this._saveDraftListener));

	if (addSig)
	  	this._toolbar.addSelectionListener(ZmOperation.ADD_SIGNATURE, new AjxListener(this, this._addSignatureListener));

	  this._toolbar.addSelectionListener(ZmOperation.SPELL_CHECK, new AjxListener(this, this._spellCheckListener));
};

ZmComposeController.prototype._setComposeMode =
function(msg) {
	// depending on COS/user preference set compose format
	var composeMode = DwtHtmlEditor.TEXT;

	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		var bComposeSameFormat = this._appCtxt.get(ZmSetting.COMPOSE_SAME_FORMAT);
		var bComposeAsFormat = this._appCtxt.get(ZmSetting.COMPOSE_AS_FORMAT);

		if (this._action == ZmOperation.REPLY ||
			this._action == ZmOperation.REPLY_ALL ||
			this._action == ZmOperation.FORWARD)
		{
			if ((!bComposeSameFormat && bComposeAsFormat == ZmSetting.COMPOSE_HTML) ||
			    (bComposeSameFormat && msg.isHtmlMail()))
			{
				composeMode = DwtHtmlEditor.HTML;
			}
		}
		else if (this._action == ZmOperation.NEW_MESSAGE)
		{
			if (bComposeAsFormat == ZmSetting.COMPOSE_HTML)
				composeMode = DwtHtmlEditor.HTML;
		}
		else if (this._action == ZmOperation.DRAFT)
		{
			if (msg.isHtmlMail())
				composeMode = DwtHtmlEditor.HTML;
		}
	}

	this._composeView.setComposeMode(composeMode);

	// dont forget to set the checked format type per compose mode
	this._setFormatBtnItem(composeMode);
};

// sets the check mark for the appropriate menu item depending on the compose mode
ZmComposeController.prototype._setFormatBtnItem =
function(composeMode) {
	if (this._appCtxt.get(ZmSetting.HTML_COMPOSE_ENABLED)) {
		var formatBtn = this._toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
		formatBtn.getMenu().checkItem(ZmHtmlEditor._VALUE, composeMode);
	}
};

ZmComposeController.prototype._processSendMsg = 
function(isDraft, msg, resp) {
	if (!isDraft) {
		if (this.isChildWindow && window.parentController) {
			window.parentController.setStatusMsg(ZmMsg.messageSent);
		} else {
			this._appCtxt.setStatusMsg(ZmMsg.messageSent);
		}

		if (resp || !this._appCtxt.get(ZmSetting.SAVE_TO_SENT)) {
			this._composeView.reset(false);

			// if the original message was a draft, we need to nuke it
			var origMsg = msg._origMsg;
			if (origMsg && origMsg.isDraft && origMsg.list)
				this._deleteDraft(origMsg);

			this._app.popView(true);
		}
	} else {
		// TODO - disable save draft button indicating a draft was saved
		//        ** new UI will show in toaster section
		if (this.isChildWindow && window.parentController) {
			window.parentController.setStatusMsg(ZmMsg.draftSaved);
		} else {
			this._appCtxt.setStatusMsg(ZmMsg.draftSaved);
		}
		this._composeView.reEnableDesignMode();
		// save message draft so it can be reused if user saves draft again
		this._composeView.processMsgDraft(msg);
	}
};


// Listeners

// Send button was pressed
ZmComposeController.prototype._sendListener =
function(ev) {
	this._toolbar.enableAll(false); // thwart multiple clicks on Send button
	this.sendMsg();
};

// Cancel button was pressed
ZmComposeController.prototype._cancelListener =
function(ev) {

	var dirty = this._composeView.isDirty();
	if (!dirty) {
		this._composeView.reset(true);
	} else {
		this._composeView.enableInputs(false);
	}
	this._composeView.reEnableDesignMode();
	this._app.popView(!dirty);
};

// Attachment button was pressed
ZmComposeController.prototype._attachmentListener =
function(ev) {

	if (!this._detachOkCancel) {
		// detach ok/cancel dialog is only necessary if user clicked on the add attachments button
		this._detachOkCancel = new DwtMessageDialog(this._shell, null, [DwtDialog.OK_BUTTON, DwtDialog.CANCEL_BUTTON]);
		this._detachOkCancel.setMessage(ZmMsg.detachAnyway, DwtMessageDialog.WARNING_STYLE);
		this._detachOkCancel.registerCallback(DwtDialog.OK_BUTTON, this._detachCallback, this);
	}

	this._composeView.addAttachmentField();
};

ZmComposeController.prototype._formatListener =
function(ev) {

	if (!ev.item.getChecked())
		return;

	var mode = ev.item.getData(ZmHtmlEditor._VALUE);
	if (mode == this._composeView.getComposeMode())
		return;

	if (mode == DwtHtmlEditor.TEXT) {
		// if formatting from html to text, confirm w/ user!
		if (!this._textModeOkCancel) {
			this._textModeOkCancel = new DwtMessageDialog(this._shell, null, [DwtDialog.OK_BUTTON, DwtDialog.CANCEL_BUTTON]);
			this._textModeOkCancel.setMessage(ZmMsg.switchToText, DwtMessageDialog.WARNING_STYLE);
			this._textModeOkCancel.registerCallback(DwtDialog.OK_BUTTON, this._textModeOkCallback, this);
			this._textModeOkCancel.registerCallback(DwtDialog.CANCEL_BUTTON, this._textModeCancelCallback, this);
		}
		this._textModeOkCancel.popup(this._composeView._getDialogXY());
	} else {
		this._composeView.setComposeMode(mode);
	}
};

ZmComposeController.prototype._detachListener =
function(ev) {
	var atts = this._composeView.getAttFieldValues();
	if (atts.length) {
		this._detachOkCancel.popup(this._composeView._getDialogXY());
	} else {
		this.detach();
		this._composeView.reset(true);
		this._app.popView(true);
	}
};

// Save Draft button was pressed
ZmComposeController.prototype._saveDraftListener =
function(ev) {
	var respCallback = new AjxCallback(this, this._handleResponseSaveDraftListener);
	this.sendMsg(null, true, respCallback);
};

ZmComposeController.prototype._handleResponseSaveDraftListener =
function(args) {
	this._composeView.draftSaved();
};

ZmComposeController.prototype._addSignatureListener =
function(ev) {
	this._composeView.addSignature();
};

ZmComposeController.prototype._spellCheckListener = 
function(ev) {
	var spellCheckButton = this._toolbar.getButton(ZmOperation.SPELL_CHECK);
	var htmlEditor = this._composeView.getHtmlEditor();

	if (spellCheckButton.isToggled()) {
		var callback = new AjxCallback(this, this.toggleSpellCheckButton)
		if (!htmlEditor.spellCheck(callback))
			this.toggleSpellCheckButton(false);
	} else {
		htmlEditor.discardMisspelledWords();
	}
};

ZmComposeController.prototype._settingsChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_SETTING) return;

	var setting = ev.source;
	if (setting.id != ZmSetting.SIGNATURE_ENABLED && setting.id != ZmSetting.SIGNATURE) return;

	var sigButton = this._toolbar.getOp(ZmOperation.ADD_SIGNATURE);
	var haveSigButton = (sigButton != null);
	var needSigButton = (!this._appCtxt.get(ZmSetting.SIGNATURE_ENABLED) && this._appCtxt.get(ZmSetting.SIGNATURE));
	if (haveSigButton && !needSigButton) {
		this._toolbar.removeOp(ZmOperation.ADD_SIGNATURE);
	} else if (!haveSigButton && needSigButton) {
		this._toolbar.addOp(ZmOperation.ADD_SIGNATURE);
	  	this._toolbar.addSelectionListener(ZmOperation.ADD_SIGNATURE, new AjxListener(this, this._addSignatureListener));
	}
};


// Callbacks

ZmComposeController.prototype._handleResponseSendMsg =
function(args) {
	var isDraft		= args[0];
	var msg			= args[1];
	var callback	= args[2];
	var result		= args[3];

	var resp = result.getResponse();
	this._processSendMsg(isDraft, msg, resp);

	if (callback) callback.run(result);
};

ZmComposeController.prototype._handleErrorSendMsg =
function(ex) {
	this._toolbar.enableAll(true);
	return false;
};

ZmComposeController.prototype._detachCallback =
function() {
	this._detachOkCancel.popdown();
	this.detach();
	this._composeView.reset(true);
	this._app.popView(true);
};

ZmComposeController.prototype._textModeOkCallback =
function(ev) {
	this._textModeOkCancel.popdown();
	this._composeView.setComposeMode(DwtHtmlEditor.TEXT);
};

ZmComposeController.prototype._textModeCancelCallback =
function(ev) {
	this._textModeOkCancel.popdown();
	// reset the radio button for the format button menu
	var formatBtn = this._toolbar.getButton(ZmOperation.COMPOSE_FORMAT);
	formatBtn.getMenu().checkItem(ZmHtmlEditor._VALUE, DwtHtmlEditor.HTML, true);
	this._composeView.reEnableDesignMode();
};

ZmComposeController.prototype._draftSavedCallback =
function(ev) {
	this._draftSavedDialog.popdown();
	this._composeView.reEnableDesignMode();
};

// Transfers addresses from the contact picker to the compose view.
ZmComposeController.prototype._contactPickerCallback =
function(args) {
	var addrs = args[0];
	this._composeView.enableInputs(true);
	for (var i = 0; i < ZmComposeView.ADDRS.length; i++) {
		var type = ZmComposeView.ADDRS[i];
		var vec = addrs[type];
		var addr = vec.size() ? vec.toString(ZmEmailAddress.SEPARATOR) + ZmEmailAddress.SEPARATOR : "";
		this._composeView.setAddress(type, addr, true);
	}
	this._contactPicker.popdown();
	this._composeView.reEnableDesignMode();
};

ZmComposeController.prototype._contactPickerCancel =
function(args) {
	this._composeView.enableInputs(true);
	this._composeView.reEnableDesignMode();
};

// Called as: Yes, save as draft
//			  Yes, go ahead and cancel
ZmComposeController.prototype._popShieldYesCallback =
function() {
	this._popShield.popdown();
	if (this._appCtxt.get(ZmSetting.SAVE_DRAFT_ENABLED)) {
		// save as draft
		var respCallback = new AjxCallback(this, this._handleResponsePopShieldYesCallback);
		this.sendMsg(null, true, respCallback);
	} else {
		// cancel
		this._composeView.reset(false);
	}
	this._app.getAppViewMgr().showPendingView(true);
};

ZmComposeController.prototype._handleResponsePopShieldYesCallback =
function(args) {
	this._composeView.draftSaved();
};

// Called as: No, don't save as draft
//			  No, don't cancel
ZmComposeController.prototype._popShieldNoCallback =
function() {
	this._popShield.popdown();
	if (this._appCtxt.get(ZmSetting.SAVE_DRAFT_ENABLED)) {
		this._app.getAppViewMgr().showPendingView(true);
		this._composeView.reset(false);
	} else {
		this._composeView.enableInputs(true);
		this._app.getAppViewMgr().showPendingView(false);
		this._composeView.reEnableDesignMode();
	}
};

// Called as: Don't save as draft or cancel
ZmComposeController.prototype._popShieldDismissCallback =
function() {
	this._composeView.enableInputs(true);
	this._popShield.popdown();
	this._app.getAppViewMgr().showPendingView(false);
	this._composeView.reEnableDesignMode();
};
