<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<HTML>
<HEAD>
	<!--
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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */
 -->
<jsp:include page="Messages.jsp"/>
<jsp:include page="Boot.jsp"/>
<script type="text/javascript">
// NOTE: Force load of single source file regardless of prod mode
AjxPackage.setExtension(".js");
AjxPackage.require("ajax.util.AjxText");
</script>
	<SCRIPT type="text/javascript">
		function onLoad() {
			var skin;
			if (location.search && (location.search.indexOf("skin=") != -1)) {
				var m = location.search.match(/skin=(\w+)/);
				if (m && m.length)
					skin = m[1];
			}
			document.title = ZmMsg.skinDeletedErrorTitle;
			var htmlArr = [];
			var idx = 0;
			htmlArr[idx++] = "<br/><br/><center>"
			htmlArr[idx++] = AjxMessageFormat.format(ZmMsg.skinDeletedError, [skin]);
			htmlArr[idx++] = "</center>"
			document.body.innerHTML = htmlArr.join("");
		}
	</SCRIPT>
<BODY ONLOAD='onLoad()'></BODY>
</HTML>