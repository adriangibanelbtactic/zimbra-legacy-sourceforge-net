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
 * The Original Code is: Zimbra Collaboration Suite.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function _initWiki() {
	var wiki = new Object();
	wiki.label = "Wikipedia";
	wiki.id = "wikipedia";
	wiki.icon = "Wiki-panelIcon";
	wiki.xsl = "wiki/mediawiki.xsl";
	wiki.getRequest = 
		function (ctxt, q) {
			return {"url":ctxt.getConfig("wikipediaUrl") + AjxStringUtil.urlEncode(q), "req":null}
		};
		
	Com_Zimbra_Xslt.registerService(wiki);

	wiki = new Object();
	wiki.label = "Wiktionary";
	wiki.id = "wiktionary";
	wiki.icon = "Wiki-panelIcon";
	wiki.xsl = "wiki/mediawiki.xsl";
	wiki.getRequest = 
		function (ctxt, q) {
			return {"url":ctxt.getConfig("wiktionaryUrl") + AjxStringUtil.urlEncode(q), "req":null}
		};
		
	Com_Zimbra_Xslt.registerService(wiki);
};

_initWiki();
