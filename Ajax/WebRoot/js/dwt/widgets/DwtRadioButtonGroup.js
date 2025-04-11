/*
 * Copyright (C) 2006, The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * This class implements a group of radio buttons
 *
 * @param {Object} radios A hash whose keys are the ids of the radio button elements
 * 		and whose values are the values associated with those buttons. (Optional)
 * @param {String} selectedId The id of the button to select initially. (Optional)
 */
DwtRadioButtonGroup = function(radios, selectedId) {
	this._radios = {};
	this._radioButtons = {};
	this._values = {};
	this._value2id = {};
	this._eventMgr = new AjxEventMgr();
	
	for (var id in radios) {
		this.addRadio(id, radios[id], id == selectedId);
	}
	if (selectedId) {
		this.setSelectedId(selectedId, true);
	}
};

DwtRadioButtonGroup.toString =
function() {
	return "DwtRadioButtonGroup";
};

//
// Public methods
//

DwtRadioButtonGroup.prototype.addSelectionListener = function(listener) {
	return this._eventMgr.addListener(DwtEvent.SELECTION, listener);
};

DwtRadioButtonGroup.prototype.removeSelectionListener = function(listener) {
	return this._eventMgr.removeListener(DwtEvent.SELECTION, listener);
};

DwtRadioButtonGroup.prototype.setEnabled = function(enabled) {
	for (var id in this._radios) {
		this._radios[id].disabled = !enabled;
	}
};

DwtRadioButtonGroup.prototype.addRadio =
function(id, radioButtonOrValue, selected) {
	var isRadioButton = radioButtonOrValue instanceof DwtRadioButton;
	var radioButton = isRadioButton ? radioButtonOrValue : null;
	var value = radioButton ? radioButton.getValue() : radioButtonOrValue;

	this._values[id] = value;
	this._value2id[value] = id;
	var element = document.getElementById(id);
	this._radios[id] = element;
	this._radioButtons[id] = radioButton;
	var handler = AjxCallback.simpleClosure(this._handleClick, this);
	Dwt.setHandler(element, DwtEvent.ONCLICK, handler);
    Dwt.associateElementWithObject(element, this);
   	element.checked = selected;
    if (selected) {
    	this._selectedId = id;
    }
};

DwtRadioButtonGroup.prototype.getRadioByValue = function(value) {
	var id = this._value2id[value];
	return this._radios[id];
};

DwtRadioButtonGroup.prototype.getRadioButtonByValue = function(value) {
	var id = this._value2id[value];
	return this._radioButtons[id];
};

DwtRadioButtonGroup.prototype.setSelectedId =
function(id, skipNotify) {
	if (id != this._selectedId) {
		document.getElementById(id).checked = true;
		this._selectedId = id;
		if (!skipNotify) {
			var selEv = DwtShell.selectionEvent;
			selEv.reset();
			this._notifySelection(selEv);
		}
	}
};

DwtRadioButtonGroup.prototype.setSelectedValue =
function(value, skipNotify) {
	var id = this._valueToId(value);
	this.setSelectedId(id, skipNotify);
};

DwtRadioButtonGroup.prototype.getSelectedId =
function() {
	return this._selectedId;
};

DwtRadioButtonGroup.prototype.getSelectedValue =
function() {
	return this._values[this._selectedId];
};

//
// Protected methods
//

DwtRadioButtonGroup.prototype._valueToId =
function(value) {
	for (var id in this._values) {
		if (this._values[id] == value) {
			return id;
		}
	}
	return null;
};

DwtRadioButtonGroup.prototype._notifySelection = 
function(selEv) {
    selEv.item = this;
    selEv.detail = { id: this._selectedId, value: this._values[this._selectedId] };
    this._eventMgr.notifyListeners(DwtEvent.SELECTION, selEv);
};

DwtRadioButtonGroup.prototype._handleClick = 
function(event) {
	event = DwtUiEvent.getEvent(event);

	var target = DwtUiEvent.getTarget(event);
	if (target && target.nodeName.match(/label/i)) {
		target = document.getElementById(target.getAttribute("for"));
	}

	var id = target.id;
	if (id != this._selectedId) {
		this._selectedId = id;
	    var selEv = DwtShell.selectionEvent;
	    DwtUiEvent.copy(selEv, event);
		this._notifySelection(selEv);
	}
};
