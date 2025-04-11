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
*	_COS_STRING_ model item type
**/

Cos_String_XModelItem = function () {}
XModelItemFactory.createItemType("_COS_STRING_", "cos_string", Cos_String_XModelItem);


Cos_String_XModelItem.prototype.dataType = _STRING_;

Cos_String_XModelItem.prototype.getter = "getValue";
Cos_String_XModelItem.prototype.getterScope = _MODELITEM_;
Cos_String_XModelItem.prototype.setter = "setLocalValue";
Cos_String_XModelItem.prototype.setterScope = _MODELITEM_;

Cos_String_XModelItem.prototype.setValueAt = function (instance, val, ref) {
	var pathParts = new Array();
	if(ref.indexOf(".") >= 0) {
		pathParts = ref.split(".");
	} else if (ref.indexOf("/") >=0) {
		pathParts = ref.split("/");
	} else {
		instance[ref] = val
		return val;
	}
	var cnt = pathParts.length-1;
	var obj = instance[pathParts[0]];
	for(var ix=1; ix<cnt; ix++) {
		obj = obj[pathParts[ix]];
	}
	obj[pathParts[cnt]] = val;
}

Cos_String_XModelItem.prototype.getValue = function(instance, current, ref) {
	var value = this.getLocalValue(instance);
	if (value == null) value = this.getSuperValue(instance);
	return value;
}
Cos_String_XModelItem.prototype.getSuperValue = function(instance) {
	if(!instance || !instance.cos)
		return null;
	var _ref = this.ref.replace("/", ".");
	return eval("instance.cos." + _ref);
}
Cos_String_XModelItem.prototype.getLocalValue = function(instance) {
	if(!instance)
		return null;
	var _ref = this.ref.replace("/", ".");
	return eval("instance." + _ref);
}

Cos_String_XModelItem.prototype.setLocalValue = function(value, instance, current, ref) {
	this.setValueAt(instance, value, ref);
}

Cos_String_XModelItem.prototype.validateType = XModelItem.prototype.validateString;



/**
*	_COS_NUMBER_ model item type
**/
Cos_Number_XModelItem = function (){}
XModelItemFactory.createItemType("_COS_NUMBER_", "cos_number", Cos_Number_XModelItem, Cos_String_XModelItem);
Cos_Number_XModelItem.prototype.validateType = XModelItem.prototype.validateNumber;


Cos_Enum_XModelItem = function (){}
XModelItemFactory.createItemType("_COS_ENUM_", "cos_enum", Cos_Enum_XModelItem, Cos_String_XModelItem);
Cos_Enum_XModelItem.prototype.getDefaultValue = function () {	return this.choices[0]; };

Cos_Enum_XModelItem.prototype.getChoices = function()		 {		return this.choices;		}
Cos_Enum_XModelItem.prototype.getSelection = function() 	{		return this.selection;		}

Cos_Enum_XModelItem.prototype.validateType = function (value) {
	// if the selection is open, they can enter any value they want
	var selectionIsOpen = this.getSelection() == _OPEN_;
	if (selectionIsOpen) return value;
	
	// selection is not open: it must be one of the supplied choices
	var choices = this.getChoices();
	for (var i = 0; i < choices.length; i++) {
		var choice = choices[i];
		if (AjxUtil.isInstance(choice, Object)) {
			if (choice.value == value) return value;
		} else {
			if (choice == value) return value;
		}
	}
	
	// if we get here, we didn't match any of the choices
	throw this.getModel().getErrorMessage("didNotMatchChoice", value);
}

/**
* _COS_MAILQUOTA_ XModel item type
**/
Cos_MailQuota_XModelItem = function (){}
XModelItemFactory.createItemType("_COS_MAILQUOTA_", "cos_mailquota", Cos_MailQuota_XModelItem, Cos_Number_XModelItem);
Cos_MailQuota_XModelItem.prototype.getterScope = _MODELITEM_;
Cos_MailQuota_XModelItem.prototype.setterScope = _MODELITEM_;
Cos_MailQuota_XModelItem.prototype.getter = "getValue";
Cos_MailQuota_XModelItem.prototype.setter = "setLocalValue";
Cos_MailQuota_XModelItem.prototype.maxInclusive = 2047;
Cos_MailQuota_XModelItem.prototype.minInclusive = 0;

Cos_MailQuota_XModelItem.prototype.getValue = function(instance, current, ref) {
	var value = this.getLocalValue(instance, current, ref);
	if (value == null) value = this.getSuperValue(instance, current, ref);
	return value;
}

Cos_MailQuota_XModelItem.prototype.getSuperValue = function(instance) {
	if(!instance)
		return null;
	var _ref = this.ref.replace("/", ".");
	var value = 0;
	if((eval("instance.cos." + _ref) != null) && (eval("instance.cos." + _ref) != 0)) {
		value = (eval("instance.cos." + _ref) / 1048576);
		if(value != Math.round(value)) {
			value = Number(value).toFixed(2);
	  	}
	} 	
//	var value = (eval("instance.cos." + _ref) != null) ? Number(eval("instance.cos." + _ref) / 1048576).toFixed(0) : 0;
	return value;
}
Cos_MailQuota_XModelItem.prototype.getLocalValue = function(instance) {
	if(!instance)
		return null;	
	var _ref = this.ref.replace("/", ".");
	var value = null;
	if(eval("instance." + _ref) != null) {
		value = (eval("instance." + _ref) / 1048576);
		if(value != Math.round(value)) {
			value = Number(value).toFixed(2);
	  	}
	} 
	return value;
}

Cos_MailQuota_XModelItem.prototype.setLocalValue = function(value, instance, current, ref) {
	var val = (value != null) ? Math.round(value * 1048576) : null;
	this.setValueAt(instance, val, ref);	
}

/**
* COS_MLIFETIME XModelItem
**/

Cos_MLifetime_XModelItem = function () {}
XModelItemFactory.createItemType("_COS_MLIFETIME_", "cos_mlifetime", Cos_MLifetime_XModelItem, Cos_String_XModelItem);
Cos_MLifetime_XModelItem.prototype.validateType = function (value) {
	var val = "1";
	if(value != null && value.length >0) {
		if(value.length > 1) {
			val = value.substr(0, value.length-1);				
		} else {
			if(value == "0") {
				val = "0";
			} else {
				val = "1";
			}
		}
	}
	
	val =  XModelItem.prototype.validateNumber.call(this, val);
	return value;
}


/**
*	COS_TEXTFIELD form item type
**/
Cos_Textfield_XFormItem = function () {}
XFormItemFactory.createItemType("_COS_TEXTFIELD_", "cos_textfield", Cos_Textfield_XFormItem, Composite_XFormItem);

Cos_Textfield_XFormItem.prototype.useParentTable = true;
Cos_Textfield_XFormItem.prototype.numCols = 3;
Cos_Textfield_XFormItem.prototype.valueLabel = null;
Cos_Textfield_XFormItem.prototype.items = [
	{	type:_TEXTFIELD_, ref:".", width:100,
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		elementChanged: function(elementValue,instanceValue, event) {
			this.getForm().itemChanged(this, elementValue, event);
		},
		onChange:Composite_XFormItem.onFieldChange,
	},
	{	type:_CHECKBOX_, ref:".",
		getDisplayValue: function () {	
			return (this.getModelItem().getLocalValue(this.getInstance()) != null);
		},
		elementChanged: function(elementValue,instanceValue, event) {
			if (elementValue == false) {
				this.getForm().itemChanged(this.getParentItem(), null, event);
			} else {
				var modelItem = this.getModelItem();
				var cosValue = modelItem.getSuperValue(this.getInstance());
				this.getForm().itemChanged(this, cosValue, event);
			}
		},onChange:Composite_XFormItem.onFieldChange
	},
	{	
		type:_OUTPUT_, ref:".", width:100,
		getDisplayValue:function() {
			return this.getParentItem().getInheritedProperty("checkBoxLabel");
		}
	}
];

/**
*	COS_CHECKBOX form item type
**/
Cos_Checkbox_XFormItem = function () {}
XFormItemFactory.createItemType("_COS_CHECKBOX_", "cos_checkbox", Cos_Checkbox_XFormItem, Composite_XFormItem);


Cos_Checkbox_XFormItem.prototype.useParentTable = true;
Cos_Checkbox_XFormItem.prototype.numCols = 3;
Cos_Checkbox_XFormItem.prototype.colSizes = ["40px","40px","100px"];
Cos_Checkbox_XFormItem.prototype.valueLabel = "";
Cos_Checkbox_XFormItem.prototype.items = [
	{	type:_CHECKBOX_, ref:".", align:_LEFT_,
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		trueValue:"TRUE", falseValue:"FALSE", onChange:Composite_XFormItem.onFieldChange
	},
	{	type:_CHECKBOX_, ref:".",
		getDisplayValue: function () {	
			return (this.getModelItem().getLocalValue(this.getInstance()) != null);
		},
		elementChanged: function(value, event) {
			if (value == false) {
				this.getForm().itemChanged(this.getParentItem(), null, event);
			} else {
				var modelItem = this.getModelItem();
				var cosValue = modelItem.getSuperValue(this.getInstance());
				this.getForm().itemChanged(this, cosValue, event);
			}
		},onChange:Composite_XFormItem.onFieldChange
	},
	{	type:_OUTPUT_, ref:".", width:100,
		getDisplayValue:function() {
			return this.getParentItem().getInheritedProperty("checkBoxLabel")
		}
	}
];

/**
*	COS_SELECT1 form item type
**/
Cos_Select1_XFormItem = function () {}
XFormItemFactory.createItemType("_COS_SELECT1_", "cos_select1", Cos_Select1_XFormItem, Composite_XFormItem);


Cos_Select1_XFormItem.prototype.useParentTable = true;
Cos_Select1_XFormItem.prototype.numCols = 3;

Cos_Select1_XFormItem.prototype.valueLabel = "";

Cos_Select1_XFormItem.prototype.items = [
	{	type:_OSELECT1_, ref:".",
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		trueValue:"TRUE", falseValue:"FALSE", onChange:Composite_XFormItem.onFieldChange
	},
	{	type:_CHECKBOX_, ref:".",
		getDisplayValue: function () {	
			return (this.getModelItem().getLocalValue(this.getInstance()) != null);
		},
		elementChanged: function(value, event) {
			if (value == false) {
				this.getForm().itemChanged(this.getParentItem(), null, event);
			} else {
				var modelItem = this.getModelItem();
				var cosValue = modelItem.getSuperValue(this.getInstance());
				this.getForm().itemChanged(this.getParentItem(), cosValue, event);
			}
		}
	},
	{	type:_OUTPUT_, ref:".", width:100,
		getDisplayValue:function() {
			return this.getParentItem().getInheritedProperty("checkBoxLabel")
		}
	}
];
/**
* _COS_LIFETIME_ XForm item type
**/

function Cos_Lifetime_XFormItem() {}
XFormItemFactory.createItemType("_COS_LIFETIME_", "cos_lifetime", Cos_Lifetime_XFormItem, Lifetime_XFormItem);
Cos_Lifetime_XFormItem.prototype.numCols = 4;
Cos_Lifetime_XFormItem.prototype.TIME_CHOICES = [
 				{value:"d", label:"Days"},
				{value:"h", label:"Hours"},
				{value:"m", label:"Minutes"},
				{value:"s", label:"Seconds"}
];



Cos_Lifetime_XFormItem.prototype.items = [
	{type:_TEXTFIELD_, ref:".", labelLocation:_NONE_,relevantBehavior:_PARENT_, cssClass:"admin_xform_number_input", 
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		getDisplayValue:function (itemVal) {
			var val = "1";
			if(itemVal != null && itemVal.length >0) {
				if(itemVal.length > 1) {
					val = itemVal.substr(0, itemVal.length-1);				
				} else {
					if(itemVal == "0") {
						val = "0";
					} else {
						val = "1";
					}
				}
			}
			this.getParentItem()._numericPart = val;
			return val;	
		},
		elementChanged:function(numericPart, instanceValue, event) {
			var val = numericPart + this.getParentItem()._stringPart;
			this.getForm().itemChanged(this, val, event);
		},onChange:Composite_XFormItem.onFieldChange
	},
	{type:_OSELECT1_, ref:".", labelLocation:_NONE_, relevantBehavior:_PARENT_, choices:Cos_Lifetime_XFormItem.prototype.TIME_CHOICES,
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		getDisplayValue:function (itemVal){
			var val = "d";
			if(itemVal != null && itemVal.length >0) {
				if(itemVal.length > 1) {
					val = itemVal.substr(itemVal.length-1, 1);
				} else if (itemVal != "0") {
					val = (itemVal == "d" || itemVal == "h" || itemVal== "m" || itemVal == "s") ? itemVal : "d";
				}
			}
			this.getParentItem()._stringPart = val;
			return val;
		},
		elementChanged:function(stringPart,instanceValue, event) {
			var val = this.getParentItem()._numericPart + stringPart;
			this.getForm().itemChanged(this.getParentItem(), val, event);
		}
	},
	{	type:_CHECKBOX_, ref:".",
		getDisplayValue: function () {	
			return (this.getModelItem().getLocalValue(this.getInstance()) != null);
		},
		elementChanged: function(value, event) {
			if (value == false) {
				this.getForm().itemChanged(this.getParentItem(), null, event);
			} else {
				var modelItem = this.getModelItem();
				var cosValue = modelItem.getSuperValue(this.getInstance());
				this.getForm().itemChanged(this.getParentItem(), cosValue, event);
			}
		}
	},
	{	type:_OUTPUT_, ref:".", width:100,
		getDisplayValue:function() {
			return this.getParentItem().getInheritedProperty("checkBoxLabel")
		}
	}
];

function Cos_Lifetime1_XFormItem() {}
XFormItemFactory.createItemType("_COS_LIFETIME1_", "cos_lifetime1", Cos_Lifetime1_XFormItem, Lifetime_XFormItem);
Cos_Lifetime1_XFormItem.prototype.numCols = 4;
Cos_Lifetime1_XFormItem.prototype.TIME_CHOICES = [
 				{value:"d", label:"Days"},
				{value:"h", label:"Hours"}
];

Cos_Lifetime1_XFormItem.prototype.items = [
	{type:_TEXTFIELD_, ref:".", labelLocation:_NONE_,relevantBehavior:_PARENT_, cssClass:"admin_xform_number_input", 
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		getDisplayValue:function (itemVal) {
			var val = "1";
			if(itemVal != null && itemVal.length >0) {
				if(itemVal.length > 1) {
					val = itemVal.substr(0, itemVal.length-1);				
				} else {
					if(itemVal == "0") {
						val = "0";
					} else {
						val = "1";
					}
				}
			}
			this.getParentItem()._numericPart = val;
			return val;	
		},
		elementChanged:function(numericPart, instanceValue, event) {
			var val = numericPart + this.getParentItem()._stringPart;
			this.getForm().itemChanged(this, val, event);
		},onChange:Composite_XFormItem.onFieldChange
	},
	{type:_OSELECT1_, ref:".", labelLocation:_NONE_, relevantBehavior:_PARENT_, choices:Cos_Lifetime1_XFormItem.prototype.TIME_CHOICES,
		relevant:"item.getModelItem().getLocalValue(instance) != null", relevantBehavior:_DISABLE_,
		getDisplayValue:function (itemVal){
			var val = "d";
			if(itemVal != null && itemVal.length >0) {
				if(itemVal.length > 1) {
					val = itemVal.substr(itemVal.length-1, 1);
				} else if (itemVal != "0") {
					val = (itemVal == "d" || itemVal == "h" || itemVal== "m" || itemVal == "s") ? itemVal : "d";
				}
			}
			this.getParentItem()._stringPart = val;
			return val;
		},
		elementChanged:function(stringPart,instanceValue, event) {
			var val = this.getParentItem()._numericPart + stringPart;
			this.getForm().itemChanged(this.getParentItem(), val, event);
		}
	},
	{	type:_CHECKBOX_, ref:".",
		getDisplayValue: function () {	
			return (this.getModelItem().getLocalValue(this.getInstance()) != null);
		},
		elementChanged: function(value, event) {
			if (value == false) {
				this.getForm().itemChanged(this.getParentItem(), null, event);
			} else {
				var modelItem = this.getModelItem();
				var cosValue = modelItem.getSuperValue(this.getInstance());
				this.getForm().itemChanged(this.getParentItem(), cosValue, event);
			}
		}
	},
	{	type:_OUTPUT_, ref:".", width:100,
		getDisplayValue:function() {
			return this.getParentItem().getInheritedProperty("checkBoxLabel")
		}
	}
];
