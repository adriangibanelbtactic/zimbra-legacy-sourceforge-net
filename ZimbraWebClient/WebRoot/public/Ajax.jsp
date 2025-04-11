<!--
***** BEGIN LICENSE BLOCK *****
Version: ZPL 1.1

The contents of this file are subject to the Zimbra Public License
Version 1.1 ("License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.zimbra.com/license

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
the License for the specific language governing rights and limitations
under the License.

The Original Code is: Zimbra Collaboration Suite Web Client

The Initial Developer of the Original Code is Zimbra, Inc.
Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
All Rights Reserved.

Contributor(s):

***** END LICENSE BLOCK *****
-->
<% 
   String vers = (String)request.getAttribute("version");
   String ext = (String)request.getAttribute("fileExtension");
   String contextPath = (String)request.getContextPath(); 
   if (vers == null){
      vers = "";
   }
   if (ext == null){
      ext = "";
   }
%>
<!-- BEGIN SCRIPT BLOCK -->
<!-- WARNING: Order matters.  Don't re-order these unless you know what you're doing! -->

<!-- AJAX utility classes -->
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxCore.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxEnv.js<%=ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxUtil.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxException.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxCookie.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/soap/AjxSoapException.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/soap/AjxSoapFault.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/soap/AjxSoapDoc.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/net/AjxRpcRequest.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/net/AjxRpc.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxWindowOpener.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxVector.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxStringUtil.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/debug/AjxDebug.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/debug/AjxDebugXmlDocument.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/xml/AjxXmlDoc.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/config/data/AjxConfig.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxEnv.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxImg.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/core/AjxException.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxTimedAction.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/events/AjxEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/events/AjxEventMgr.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxCallback.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/events/AjxListener.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxText.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxDateUtil.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxStringUtil.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxVector.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxSelectionManager.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/net/AjxPost.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxBuffer.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxCache.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/util/AjxTimezone.js<%= ext %>?v=<%= vers %>"></script>

<!-- DWT classes -->
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/core/DwtImg.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/core/Dwt.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/core/DwtException.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/core/DwtDraggable.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/graphics/DwtCssStyle.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/graphics/DwtPoint.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/graphics/DwtRectangle.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/graphics/DwtUnits.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtEventManager.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtDateRangeEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtDisposeEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtUiEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtControlEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtKeyEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtMouseEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtMouseEventCapture.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtListViewActionEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtSelectionEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtHtmlEditorStateEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtTreeEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtHoverEvent.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/dnd/DwtDragEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/dnd/DwtDragSource.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/dnd/DwtDropEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/dnd/DwtDropTarget.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtHoverMgr.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtControl.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtComposite.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtShell.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtColorPicker.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtBaseDialog.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtDialog.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtLabel.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtListView.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtButton.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtMenuItem.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtMenu.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtMessageDialog.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtHtmlEditor.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtSash.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtToolBar.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/graphics/DwtBorder.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtToolTip.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtStickyToolTip.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtTreeItem.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtTree.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtCalendar.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtPropertyPage.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtTabView.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtWizardDialog.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtSelect.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtAddRemove.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtAlert.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtText.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtIframe.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtXFormDialog.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtPropertySheet.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtGrouper.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/widgets/DwtProgressBar.js<%= ext %>?v=<%= vers %>"></script>

<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/events/DwtXFormsEvent.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XFormGlobal.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XModel.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XModelItem.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XForm.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XFormItem.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/XFormChoices.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/OSelect_XFormItem.js<%= ext %>?v=<%= vers %>"></script>
<script type="text/javascript" src="<%= contextPath %>/js/ajax/dwt/xforms/ButtonGrid.js<%= ext %>?v=<%= vers %>"></script>
<!-- END SCRIPT BLOCK -->
