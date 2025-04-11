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

ZmSignaturesPage = function(parent, appCtxt, section, controller) {
	if (arguments.length == 0) return;
	ZmPreferencesPage.call(this, parent, appCtxt, section, controller);

	this._signatureComps = {};
	this._deletedSignatures = {};

	this._minEntries = appCtxt.get(ZmSetting.SIGNATURES_MIN);
	this._maxEntries = appCtxt.get(ZmSetting.SIGNATURES_MAX);
};
ZmSignaturesPage.prototype = new ZmPreferencesPage;
ZmSignaturesPage.prototype.constructor = ZmSignaturesPage;

ZmSignaturesPage.prototype.toString = function() {
	return "ZmSignaturesPage";
};

//
// Data
//

ZmSignaturesPage.prototype.SIGNATURE_TEMPLATE = "zimbraMail.prefs.templates.Pages#Signature";

//
// Public methods
//

ZmSignaturesPage.prototype.getAllSignatures = function(includeEmpty, includeNonModified) {
	return [].concat(
		this.getNewSignatures(includeEmpty),
		this.getModifiedSignatures(includeNonModified)
	);
};

ZmSignaturesPage.prototype.getNewSignatures = function(includeEmpty) {
	var array = [];
	for (var id in this._signatureComps) {
		var comps = this._signatureComps[id];
		var signature = comps.signature;
		if (!signature._new) continue;

		var hasName = comps.name.getValue().replace(/\s*/g,"") != "";
		var hasValue = comps.value.getValue().replace(/\s*/g,"") != "";
		if (includeEmpty || hasName || hasValue) {
			array.push(signature);
		}
	}
	return array;
};

ZmSignaturesPage.prototype.getDeletedSignatures = function() {
	var array = [];
	for (var id in this._deletedSignatures) {
		var signature = this._deletedSignatures[id];
		array.push(signature);
	}
	return array;
};

ZmSignaturesPage.prototype.getModifiedSignatures = function(includeNonModified) {
	var array = [];
	for (var id in this._signatureComps) {
		var comps = this._signatureComps[id];
		var signature = comps.signature;
		if (signature._new) continue;

		var name = comps.name && comps.name.getValue();
		var value = comps.value && comps.value.getValue();
		var modified = includeNonModified ||
					   (name != null && name != signature.name) ||
					   (value != null && value != signature.value);
		if (modified) {
			array.push(signature);
		}
	}
	return array;
};

ZmSignaturesPage.prototype.reset = function(useDefaults) {
	ZmPreferencesPage.prototype.reset.apply(this, arguments);
	var signatures = this._appCtxt.get(ZmSetting.SIGNATURES);
	for (var id in signatures) {
		this._resetSignature(signatures[id]);//, true);
	}
};

// saving

ZmSignaturesPage.prototype.isDirty = function() {
	return	this.getNewSignatures().length > 0 ||
			this.getDeletedSignatures().length > 0 ||
			this.getModifiedSignatures().length > 0;
};

ZmSignaturesPage.prototype.validate = function() {
	var signatures = this.getAllSignatures();
	for (var i = 0; i < signatures.length; i++) {
		var id = signatures[i]._htmlElId;
		var comps = this._signatureComps[id];
		if (comps.name.getValue().replace(/\s*/g,"") == "") {
			this._errorMsg = ZmMsg.errorMissingRequired;
			return false;
		}
	}
	return true;
};

ZmSignaturesPage.prototype.getErrorMessage = function() {
	return this._errorMsg;
};

ZmSignaturesPage.prototype.addCommand = function(batchCommand) {
	// delete signatures
	var deletedSigs = this.getDeletedSignatures();
	for (var i = 0; i < deletedSigs.length; i++) {
		var signature = deletedSigs[i];
		var callback = new AjxCallback(this, this._handleDeleteResponse, [signature]); 
		signature.doDelete(callback, null, batchCommand);
	}

	// modify signatures
	var modifiedSigs = this.getModifiedSignatures();
	for (var i = 0; i < modifiedSigs.length; i++) {
		var signature = modifiedSigs[i];
		var comps = this._signatureComps[signature._htmlElId];

		var proxy = AjxUtil.createProxy(signature);
		if (comps.name) proxy.name = comps.name.getValue();
		if (comps.value) proxy.value = comps.value.getValue();

		var callback = new AjxCallback(this, this._handleModifyResponse, [signature]);
		proxy.save(callback, null, batchCommand);
	}

	// add signatures
	var newSigs = this.getNewSignatures();
	for (var i = 0; i < newSigs.length; i++) {
		var signature = newSigs[i];
		var comps = this._signatureComps[signature._htmlElId];
		if (comps.name) signature.name = comps.name.getValue();
		if (comps.value) signature.value = comps.value.getValue();

		var callback = new AjxCallback(this, this._handleNewResponse, [signature]);
		signature.create(callback, null, batchCommand);
	}

};

//
// Protected methods
//

ZmSignaturesPage.prototype._setupCustom = function(id, setup, value) {
	if (id == ZmSetting.SIGNATURES) {
		// create container control
		var container = new DwtComposite(this);
		this.setFormObject(id, container);

		// create radio group for defaults
		this._defaultRadioGroup = new DwtRadioButtonGroup();

		// populate signatures
		var signatures = this._appCtxt.getSignatureCollection().getSignatures();
		var sigNames = AjxUtil.keys(signatures).sort();
		var lessThanEqual = sigNames.length <= this._maxEntries;
		var count = lessThanEqual ? sigNames.length : this._maxEntries;
		for (var i = 0; i < count; i++) {
			this._addSignature(signatures[sigNames[i]]);
		}
		for (var i = count; i < this._minEntries; i++) {
			this._addNewSignature();
		}
		this._resetDeleteButtons();

		// add button
		var buttonEl = document.getElementById(this._htmlElId+"_ADD_SIGNATURE");
		if (buttonEl) {
			var button = new DwtButton(this);
			button.setText(ZmMsg.addSignature);
			button.setEnabled(lessThanEqual);
			button.addSelectionListener(new AjxListener(this, this._handleAddButton));
			button.replaceElement(buttonEl);

			this._addSignatureButton = button;
		}

		return container;
	}

	return ZmPreferencesPage.prototype._setupCustom.apply(this, arguments);
};

ZmSignaturesPage.prototype._addNewSignature = function(skipControls) {
	// add signature
	var signature = new ZmSignature(null);
	signature._new = true;
	if (!skipControls) {
		this._addSignature(signature);
	}

	return signature;
};

ZmSignaturesPage.prototype._addSignature = function(signature) {
	signature._htmlElId = [this._htmlElId, Dwt.getNextId()].join("_");

	// create html
	var data = { id: signature._htmlElId };
	var html = AjxTemplate.expand(this.SIGNATURE_TEMPLATE, data);
	var el = Dwt.parseHtmlFragment(html);

	var listComp = this.getFormObject(ZmSetting.SIGNATURES); 
	var listEl = listComp.getHtmlElement();
	listEl.appendChild(el);

	// instantiate controls
	var comps = this._signatureComps[signature._htmlElId] = {
		signature: signature
	};
	var nameEl = document.getElementById(signature._htmlElId+"_SIGNATURE_NAME");
	if (nameEl) {
		var params = {
			parent: listComp,
			validationStyle: DwtInputField.CONTINUAL_VALIDATION,
			validator: AjxCallback.simpleClosure(this._validateName, this, signature._htmlElId)
		};
		var input = new DwtInputField(params);
		input.replaceElement(nameEl);

		comps.name = input;
	}

	var defaultEl = document.getElementById(signature._htmlElId+"_SIGNATURE_DEFAULT");
	if (defaultEl) {
		var name = this._htmlElId;
		var isDefault = false; // TODO
		var radio = new DwtRadioButton(listComp, null, name, isDefault);
		radio.setText(ZmMsg.def);
		radio.replaceElement(defaultEl);

		var id = radio._htmlElId;
		var value = signature._htmlElId;
		this._defaultRadioGroup.addRadio(id, value, isDefault);

		comps.isDefault = radio;
	}

	var deleteEl = document.getElementById(signature._htmlElId+"_SIGNATURE_DELETE");
	if (deleteEl) {
		var button = new DwtButton(listComp);
		button.addSelectionListener(new AjxListener(this, this._handleDeleteButton, [signature._htmlElId]));
		button.replaceElement(deleteEl);

		comps.doDelete = button;
	}

	var valueEl = document.getElementById(signature._htmlElId+"_SIGNATURE");
	if (valueEl) {
		var textarea = new DwtInputField({parent:listComp,rows:valueEl.rows||3,size:valueEl.cols});
		textarea.replaceElement(valueEl);

		comps.value = textarea;
	}

	// initialize state
	this._resetSignature(signature);

	// can we add any more?
	this._resetAddButton();

	return comps;
};

ZmSignaturesPage.prototype._resetSignature = function(signature, clear) {
	// remove recently added signature
	if (clear && signature._new) {
		var sigEl = document.getElementById(signature._htmlElId);
		sigEl.parentNode.removeChild(sigEl);
		delete this._signatureComps[signature._htmlElId];
		return;
	}

	// reset signature values back to original values
	var comps = this._signatureComps[signature._htmlElId] || {};
	if (comps.name) {
		comps.name.setValue(signature.name);
		comps.name.setEnabled(true);
	}
	if (comps.isDefault) {
		for (var id in this._signatureComps) {
			var sigComps = this._signatureComps[id];
			if (sigComps.isDefault) {
				sigComps.isDefault.setEnabled(false);
			}
		}
		// TODO: Select this one if it's the default; and remember it
	}
	if (comps.value) {
		comps.value.setValue(signature.value);
		comps.value.setEnabled(true);
	}
};

ZmSignaturesPage.prototype._resetAddButton = function() {
	if (this._addSignatureButton) {
		var more = this.getAllSignatures(true, true).length < this._maxEntries;
		this._addSignatureButton.setEnabled(more);
	}
};

ZmSignaturesPage.prototype._resetDeleteButtons = function() {
	this._deleteState = this.getAllSignatures(true, true).length > this._minEntries; 
	var text = this._deleteState ? ZmMsg.del : ZmMsg.clear;
	for (var id in this._signatureComps) {
		var comps = this._signatureComps[id];
		if (comps.doDelete) {
			comps.doDelete.setText(text);
		}
	}
};

// buttons

ZmSignaturesPage.prototype._handleAddButton = function(evt) {
	var signature = this._addNewSignature();

	// focus input
	var comps = this._signatureComps[signature._htmlElId];
	if (comps.name) {
		comps.name.focus();
	}
	else if (comps.value) {
		comps.value.focus();
	}

	this._resetDeleteButtons();
};

ZmSignaturesPage.prototype._handleDeleteButton = function(id, evt) {
	var signature = this._signatureComps[id].signature;

	// update controls
	if (this._deleteState) {
		var el = document.getElementById(id);
		el.parentNode.removeChild(el);

		delete this._signatureComps[id];
	}
	else {
		var newSignature = this._addNewSignature(true);
		newSignature._htmlElId = id;
		var comps = this._signatureComps[id];
		comps.signature = newSignature;
		if (comps.name) comps.name.setValue("");
		if (comps.isDefault) comps.isDefault.setSelected(false);
		if (comps.value) comps.value.setValue("");
	}

	// clean-up state
	if (!signature._new) {
		this._deletedSignatures[id] = signature;
	}

	// reset buttons
	this._resetDeleteButtons();
	this._resetAddButton();
};

// saving

ZmSignaturesPage.prototype._handleDeleteResponse = function(signature, resp) {
	var id = signature._htmlElId;
	delete this._deletedSignatures[id];
};

ZmSignaturesPage.prototype._handleModifyResponse = function(signature, resp) {
	// Is there anything to do?
};

ZmSignaturesPage.prototype._handleNewResponse = function(signature, resp) {
	delete signature._new;
	this._resetSignature(signature);
};

// validation

ZmSignaturesPage.prototype._validateName = function(id, value) {
	var comps = this._signatureComps[id];
	if (comps.name.getValue().replace(/\s*/g,"") == "" &&
		comps.value.getValue().replace(/\s*/g,"") != "") {
		throw ZmMsg.errorMissingRequired;
	}
	return value;
};