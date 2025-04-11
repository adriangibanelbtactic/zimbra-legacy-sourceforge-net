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
* @class ZaMigrationWizController 
* @contructor ZaMigrationWizController
* @param appCtxt
* @param container
* @param app
* @author Greg Solovyev
**/
function ZaMigrationWizController(appCtxt, container, app) {

	ZaController.call(this, appCtxt, container, app);
}

ZaMigrationWizController.prototype = new ZaController();
ZaMigrationWizController.prototype.constructor = ZaMigrationWizController;


ZaMigrationWizController.prototype.show = 
function() {
    if (!this._contentView) {
		var elements = new Object();
		this._contentView = new ZaMigrationWizView(this._container, this._app);
		elements[ZaAppViewMgr.C_APP_CONTENT] = this._contentView;
		this._app.createView(ZaZimbraAdmin._MIGRATION_WIZ_VIEW, elements);
	}
	this._app.pushView(ZaZimbraAdmin._MIGRATION_WIZ_VIEW);
};
