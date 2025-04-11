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
* XFormItem class: LDAP URL
* this item is used in the Admin UI to display LDAP URL fields like LDAP URL for GAL Search and LDAP URL  for Authentication
* @class LDAPURL_XFormItem
* @constructor LDAPURL_XFormItem
* @author Greg Solovyev
**/
function LDAPURL_XFormItem() {}
XFormItemFactory.createItemType("_LDAPURL_", "ldapurl", LDAPURL_XFormItem, Composite_XFormItem);
LDAPURL_XFormItem.prototype.numCols = 5;
LDAPURL_XFormItem.prototype.nowrap = true;
LDAPURL_XFormItem.prototype._protocolPart = "ldap://";
LDAPURL_XFormItem.prototype._serverPart = "";
LDAPURL_XFormItem.prototype._portPart = "389";

LDAPURL_XFormItem.prototype.items = [
	{type:_OUTPUT_, width:"35px", ref:".", labelLocation:_NONE_, label:null,relevantBehavior:_PARENT_,
		getDisplayValue:function(itemVal) {
			var val = "ldap://";
			if(itemVal!=null && itemVal.length>0) {
				var URLChunks = itemVal.split(/(:\/\/)/);
				if(URLChunks.length==3) {
					val = URLChunks[0] + URLChunks[1];
				}
			}
			this.getParentItem()._protocolPart = val;
			return val;
		}
	},
	{type:_TEXTFIELD_, width:"200px", forceUpdate:true, ref:".", labelLocation:_NONE_, label:null,relevantBehavior:_PARENT_,
		required:true,
		getDisplayValue:function (itemVal) {
			var val = "";
			if(itemVal) {
				var URLChunks = itemVal.split(/[:\/]/);
				if(URLChunks.length >= 4) {
					val = URLChunks[3];
				} 
				this.getParentItem()._serverPart = val;
			} 
			return val;	
		},
		elementChanged:function(serverPart, instanceValue, event) {
			this.getParentItem()._serverPart = serverPart;
			var val = this.getParentItem()._protocolPart + serverPart+ ":" + this.getParentItem()._portPart;
			this.getForm().itemChanged(this.getParentItem(), val, event);
		}
	},
	{type:_OUTPUT_, width:"5px", labelLocation:_NONE_, label:null,relevantBehavior:_PARENT_,value:":"},
	{type:_TEXTFIELD_,width:"40px",forceUpdate:true, ref:".", labelLocation:_NONE_, label:null, relevantBehavior:_PARENT_, 
		getDisplayValue:function (itemVal) {
			var val = "389";
			if(itemVal) {
				var URLChunks = itemVal.split(/[:\/]/);
				if(URLChunks.length == 5) {
					val = URLChunks[4];
				} 
				this.getParentItem()._portPart = val;
			} 
			return val;	
		},
		elementChanged:function(portPart, instanceValue, event) {
			this.getParentItem()._portPart = portPart;
			var val = this.getParentItem()._protocolPart + this.getParentItem()._serverPart+ ":" + portPart;
			this.getForm().itemChanged(this.getParentItem(), val, event);
		}
	},
	{type:_CHECKBOX_,width:"40px",forceUpdate:true, ref:".", labelLocation:_NONE_, label:null, relevantBehavior:_PARENT_,
		getDisplayValue:function (itemVal) {
			var val = false;
			var protocol = "ldap://";
			if(itemVal!=null && itemVal.length>0) {
				var URLChunks = itemVal.split(/(:\/\/)/);
				if(URLChunks.length==3) {
					protocol = URLChunks[0] + URLChunks[1];
				}
			}
			this.getParentItem()._protocolPart = protocol;
			if(protocol.length==8) {
				val = true;
			}
			return val;			
		},
		elementChanged:function(isChecked, instanceValue, event) {
			if(isChecked) {
				this.getParentItem()._protocolPart = "ldaps://";
				this.getParentItem()._portPart = 636;
			} else {
				this.getParentItem()._protocolPart = "ldap://";
				this.getParentItem()._portPart = 389;
			}
			var val = this.getParentItem()._protocolPart + this.getParentItem()._serverPart+ ":" + this.getParentItem()._portPart;
			this.getForm().itemChanged(this.getParentItem(), val, event);
		}
	}
];

