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
* @constructor
* @class ZaPosixGroupListController
* This is a singleton object that controls all the user interaction with the list of ZaPosixGroup objects
**/
function ZaPosixGroupListController(appCtxt, container, app) {
	ZaListViewController.call(this, appCtxt, container, app,"ZaPosixGroupListController");
}

ZaPosixGroupListController.prototype = new ZaListViewController();
ZaPosixGroupListController.prototype.constructor = ZaPosixGroupListController;
ZaController.initToolbarMethods["ZaPosixGroupListController"] = new Array();
ZaController.initPopupMenuMethods["ZaPosixGroupListController"] = new Array();


ZaPosixGroupListController.prototype.show = 
function(list) {
    if (!this._UICreated) {
		this._createUI();
	} 	
	if (list != null)
		this._contentView.set(list.getVector());
	
	this._app.pushView(ZaZimbraAdmin._POSIX_GROUP_LIST);			

	this._removeList = new Array();
	if (list != null)
		this._list = list;
		
	this._changeActionsState();		
}

ZaPosixGroupListController.initToolbarMethod =
function () {
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.NEW, ZaMsg.TBB_New, ZaMsg.SERTBB_new_tt, "NewCOS", "NewCOSDis", new AjxListener(this, this._newButtonListener)));    		
   	this._toolbarOperations.push(new ZaOperation(ZaOperation.EDIT, ZaMsg.TBB_Edit, ZaMsg.SERTBB_Edit_tt, "Properties", "PropertiesDis", new AjxListener(this, this._editButtonListener)));    	
    this._toolbarOperations.push(new ZaOperation(ZaOperation.DELETE, ZaMsg.TBB_Delete, ZaMsg.SERTBB_Delete_tt, "Delete", "DeleteDis", new AjxListener(this, this._deleteButtonListener)));    	    	
	this._toolbarOperations.push(new ZaOperation(ZaOperation.NONE));
	this._toolbarOperations.push(new ZaOperation(ZaOperation.HELP, ZaMsg.TBB_Help, ZaMsg.TBB_Help_tt, "Help", "Help", new AjxListener(this, this._helpButtonListener)));				
}
ZaController.initToolbarMethods["ZaPosixGroupListController"].push(ZaPosixGroupListController.initToolbarMethod);

ZaPosixGroupListController.initPopupMenuMethod =
function () {
   	this._popupOperations.push(new ZaOperation(ZaOperation.EDIT, ZaMsg.TBB_Edit, ZaMsg.SERTBB_Edit_tt, "Properties", "PropertiesDis", new AjxListener(this, ZaPosixGroupListController.prototype._editButtonListener)));    	
   	this._popupOperations.push(new ZaOperation(ZaOperation.DELETE, ZaMsg.TBB_Delete, ZaMsg.SERTBB_Delete_tt, "Delete", "DeleteDis", new AjxListener(this, ZaPosixGroupListController.prototype._deleteButtonListener)));    	    	
}
ZaController.initPopupMenuMethods["ZaPosixGroupListController"].push(ZaPosixGroupListController.initPopupMenuMethod);


ZaPosixGroupListController.prototype._createUI = function () {
	try {
		var elements = new Object();
		this._contentView = new ZaPosixGroupListView(this._container);
		this._initToolbar();
		if(this._toolbarOperations && this._toolbarOperations.length) {
			this._toolbar = new ZaToolBar(this._container, this._toolbarOperations); 
			elements[ZaAppViewMgr.C_TOOLBAR_TOP] = this._toolbar;
		}
		this._initPopupMenu();
		if(this._popupOperations && this._popupOperations.length) {
			this._actionMenu =  new ZaPopupMenu(this._contentView, "ActionMenu", null, this._popupOperations);
		}
		elements[ZaAppViewMgr.C_APP_CONTENT] = this._contentView;
		this._app.createView(ZaZimbraAdmin._POSIX_GROUP_LIST, elements);

		this._contentView.addSelectionListener(new AjxListener(this, this._listSelectionListener));
		this._contentView.addActionListener(new AjxListener(this, this._listActionListener));			
		this._removeConfirmMessageDialog = this._app.dialogs["removeConfirmMessageDialog"] = new ZaMsgDialog(this._app.getAppCtxt().getShell(), null, [DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON], this._app);									
		this._UICreated = true;
	} catch (ex) {
		this._handleException(ex, "ZaPosixGroupListController.prototype._createUI", null, false);
		return;
	}	
}


ZaPosixGroupListController.prototype.set = 
function(PosixGroupList) {
	this.show(PosixGroupList);
}


// new button was pressed
ZaPosixGroupListController.prototype._newButtonListener =
function(ev) {
	var newPosixGroup = new ZaPosixGroup(this._app);
	this._app.getPosixGroupController().show(newPosixGroup);
}

/**
* This listener is called when the item in the list is double clicked. It call ZaPosixGroupController.show method
* in order to display the Server View
**/
ZaPosixGroupListController.prototype._listSelectionListener =
function(ev) {
	if (ev.detail == DwtListView.ITEM_DBL_CLICKED) {
		if(ev.item) {
			this._selectedItem = ev.item;
			this._app.getPosixGroupController().show(ev.item);
		}
	} else {
		this._changeActionsState();	
	}
}

ZaPosixGroupListController.prototype._listActionListener =
function (ev) {
	this._changeActionsState();
	this._actionMenu.popup(0, ev.docX, ev.docY);
}
/**
* This listener is called when the Edit button is clicked. 
* It call ZaPosixGroupController.show method
* in order to display the Server View
**/
ZaPosixGroupListController.prototype._editButtonListener =
function(ev) {
	if(this._contentView.getSelectionCount() == 1) {
		var item = this._contentView.getSelection()[0];
		this._app.getPosixGroupController().show(item);
	}
}

ZaPosixGroupListController.prototype._changeActionsState = 
function () {
	var cnt = this._contentView.getSelectionCount();
	if(cnt == 1) {
		var opsArray = [ZaOperation.EDIT];
		this._toolbar.enable(opsArray, true);
		this._actionMenu.enable(opsArray, true);
	} else if (cnt > 1){
		var opsArray1 = [ZaOperation.EDIT];
		this._toolbar.enable(opsArray1, false);
		this._actionMenu.enable(opsArray1, false);
	} else {
		var opsArray = [ZaOperation.EDIT];
		this._toolbar.enable(opsArray, false);
		this._actionMenu.enable(opsArray, false);
	}
}

/**
* @param ev
* This listener is invoked by ZaPosixGroupController or any other controller that can create a ZaPosixGroup object
**/
ZaPosixGroupListController.prototype.handleCreation = 
function (ev) {
	if(ev) {
		if(ev.getDetails() && this._list) {
			if (this._list) this._list.add(ev.getDetails());
			if (this._contentView) this._contentView.setUI();
			if(this._app.getCurrentController() == this) {
				this.show();			
			}
		}
	}
}

/**
* @param ev
* This listener is invoked by ZaPosixGroupController or any other controller that can remove a ZaPosixGroup object
**/
ZaPosixGroupListController.prototype.handleRemoval = 
function (ev) {
	if(ev) {
		if(ev.getDetails() && this._list) {
			if (this._list) this._list.remove(ev.getDetails());
			if (this._contentView) this._contentView.setUI();
			if(this._app.getCurrentController() == this) {
				this.show();			
			}
		}
	}
}
/**
* @param ev
* This listener is invoked by ZaPosixGroupController or any other controller that can modify a ZaPosixGroup object
**/
ZaPosixGroupListController.prototype.handleChange =
function (ev) {
	//if any of the data that is currently visible has changed - update the view
	if(ev) {
		var details = ev.getDetails();
		if (details) {
			if (this._list) {
				this._list.replace(details);
			}
			if (this._contentView) this._contentView.setUI();
			if(this._app.getCurrentController() == this) {
				this.show();			
			}
		}
	}
}

/**
* This listener is called when the Delete button is clicked. 
**/
ZaPosixGroupListController.prototype._deleteButtonListener =
function(ev) {
	this._removeList = new Array();
	if(this._contentView.getSelectionCount() > 0) {
		var arrItems = this._contentView.getSelection();
		var cnt = arrItems.length;
		for(var key =0; key < cnt; key++) {
			if(arrItems[key]) {
				this._removeList.push(arrItems[key]);
			}
		}
	}
	if(this._removeList.length) {
		dlgMsg = "Are you sure you want to delete the following group(s)?";
		dlgMsg +=  "<br><ul>";
		var i=0;
		for(var key in this._removeList) {
			if(i > 19) {
				dlgMsg += "<li>...</li>";
				break;
			}
			dlgMsg += "<li>";
			if(this._removeList[key].name.length > 50) {
				//split it
				var endIx = 49;
				var beginIx = 0; //
				while(endIx < this._removeList[key].name.length) { //
					dlgMsg +=  this._removeList[key].name.slice(beginIx, endIx); //
					beginIx = endIx + 1; //
					if(beginIx >= (this._removeList[key].name.length) ) //
						break;
					
					endIx = ( this._removeList[key].name.length <= (endIx + 50) ) ? this._removeList[key].name.length-1 : (endIx + 50);
					dlgMsg +=  "<br>";	
				}
			} else {
				dlgMsg += this._removeList[key].name;
			}
			dlgMsg += "</li>";
			i++;
		}
		dlgMsg += "</ul>";
		this._removeConfirmMessageDialog.setMessage(dlgMsg, DwtMessageDialog.INFO_STYLE);
		this._removeConfirmMessageDialog.registerCallback(DwtDialog.YES_BUTTON, this._deleteCallback, this);
		this._removeConfirmMessageDialog.registerCallback(DwtDialog.NO_BUTTON, this._donotDeleteCallback, this);		
		this._removeConfirmMessageDialog.popup();
	}
}

ZaPosixGroupListController.prototype._deleteCallback = 
function () {
	var successRemList=new Array();
	for(var key in this._removeList) {
		if(this._removeList[key]) {
			try {
				this._removeList[key].remove();
				successRemList.push(this._removeList[key]);				
			} catch (ex) {
				this._removeConfirmMessageDialog.popdown();
				this._handleException(ex, "ZaPosixGroupListController.prototype._deleteCallback", null, false);
				return;
			}
		}
		if (this._list) this._list.remove(this._removeList[key]); //remove from the list
	}
	this.fireRemovalEvent(successRemList); 	
	this._removeConfirmMessageDialog.popdown();
	if (this._contentView) this._contentView.setUI();
	this.show();
}

ZaPosixGroupListController.prototype._donotDeleteCallback = 
function () {
	this._removeList = new Array();
	this._removeConfirmMessageDialog.popdown();
}