<%@ tag body-content="empty" %>
<%@ attribute name="mailbox" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZMailboxBean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>


 <table border="0" cellpadding="0" cellspacing="10" width=100%>
     <tr>
        <td colspan="2" class='ZOptionsHeader' >
            <fmt:message key="optionsDisplayingMessages"/>
        </td>
     </tr>
     <tr>
         <td class='ZOptionsTableLabel'>
             <fmt:message key="optionsDisplay"/> :
         </td>
         <td>
             <table border="0" cellpadding="0" cellspacing="0">
                 <tr>
                     <td>
                         <select name="zimbraPrefMailItemsPerPage" id="itemsPP">
                             <c:set var="mailItemsPP" value="${mailbox.prefs.mailItemsPerPage}"/>
                             <option
                                     <c:if test="${mailItemsPP eq 10}"> selected</c:if>
                                     >10
                             </option>
                             <option
                                     <c:if test="${mailItemsPP eq 25}"> selected</c:if>
                                     >25
                             </option>
                             <option
                                     <c:if test="${mailItemsPP eq 50}"> selected</c:if>
                                     >50
                             </option>
                             <option
                                     <c:if test="${mailItemsPP eq 100}"> selected</c:if>
                                     >100
                             </option>
                         </select>
                     </td>
                     <td style='padding-left:5px'>
                         <label for="itemsPP"><fmt:message key="optionsEmailPerPage"/></label>
                     </td>
                 </tr>
             </table>
         </td>
     </tr>
<c:if test="${mailbox.features.conversations}">
     <tr>
         <td class='ZOptionsTableLabel'>
             <label for="groupMailBy"><fmt:message key="groupMailBy"/>
                 :</label>
         </td>
         <td>
            <select name="zimbraPrefGroupMailBy" id="groupMailBy">
                <c:set var="groupMailBy" value="${mailbox.prefs.groupMailBy}"/>
                <option
                        <c:if test="${groupMailBy eq 'conversation'}">selected</c:if> value="conversation">
                    <fmt:message key="conversation"/>
                </option>
                <option
                        <c:if test="${groupMailBy eq 'message'}">selected</c:if> value="message">
                    <fmt:message key="message"/>
                </option>
            </select>
         </td>
     </tr>
</c:if>
     <tr valign="middle">
         <td class='ZOptionsTableLabel'>
             <fmt:message key="optionsDisplayHtml"/>:
         </td>
         <td>
             <table border="0" cellpadding="0" cellspacing="3">
                 <tr>
                     <td>
                         <input id="viewHtml" type="radio" name="zimbraPrefMessageViewHtmlPreferred" value="TRUE" <c:if test="${mailbox.prefs.messageViewHtmlPreferred}">checked</c:if>/>
                     </td>
                     <td>
                         <label for="viewHtml"><fmt:message key="optionsDisplayHtmlAsHtml"/></label>
                     </td>
                     <td>
                         <input id="viewText" type="radio" name="zimbraPrefMessageViewHtmlPreferred" value="FALSE" <c:if test="${not mailbox.prefs.messageViewHtmlPreferred}">checked</c:if>/>
                     </td>
                     <td>
                         <label for="viewText"><fmt:message key="optionsDisplayHtmlAsText"/></label>
                     </td>
                 </tr>
             </table>
         </td>
     </tr>
     <app:optSeparator/>
     <tr>
         <td class='ZOptionsTableLabel'>
             <fmt:message key="optionsMessagePreview"/>:
         </td>
         <td>
             <app:optCheckbox boxfirst="true" label="optionsShowFragments" pref="zimbraPrefShowFragments" checked="${mailbox.prefs.showFragments}"/>
         </td>
     </tr>
     <c:if test="${mailbox.features.initialSearchPreference}">
     <app:optSeparator/>
     <tr>
         <td class='ZOptionsTableLabel'>
             <label for="zimbraPrefMailInitialSearch"><fmt:message key="optionsDefaultMailSearch"/> :</label>
         </td>
         <td>
             <input id="zimbraPrefMailInitialSearch" size="40" type="text" name='zimbraPrefMailInitialSearch' autocomplete='off' value="${fn:escapeXml(mailbox.prefs.mailInitialSearch)}">
         </td>
     </tr>
     </c:if>
     <tr>
        <td colspan="2">
            &nbsp;
        </td>
     </tr>
     <tr>
        <td colspan="2" class='ZOptionsHeader' >
            <fmt:message key="optionsReceivingMessages"/>
        </td>
     </tr>

<c:set var="messageArrives"><fmt:message key="optionsWhenAMessageArrives"/> : </c:set>
<c:if test="${mailbox.features.mailForwarding}">
     <tr>
         <td class='ZOptionsTableLabel'>
             ${messageArrives}
             <c:set var="messageArrives" value="&nbsp;"/>
         </td>
         <td>
             <table cellspacing="0" cellpadding="0">
                 <tr>
                     <td><input type="checkbox" id="FORWARDCHECKED" name='FORWARDCHECKED' value="TRUE" <c:if test="${not empty mailbox.prefs.mailForwardingAddress}">checked</c:if>></td>
                     <td style='padding-left:5px' nowrap align=right><label for="FORWARDCHECKED"><fmt:message key="optionsForwardAcopyTo"/>:</label></td>
                 </tr>
             </table>
         </td>
     </tr>
     <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td style='padding-left:20px'>
             <input id="zimbraPrefMailForwardingAddress" size="40" type="text" name='zimbraPrefMailForwardingAddress' autocomplete='off' value="${fn:escapeXml(mailbox.prefs.mailForwardingAddress)}">
             <span style='padding-left:5px' class='ZOptionsHint'><fmt:message key="optionsEmailAddressHint"/></span>
         </td>
     </tr>
     <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td style='padding-left:20px'>
                 <app:optCheckbox boxfirst="true" label="mailDeliveryDisabled" pref="zimbraPrefMailLocalDeliveryDisabled"
                     checked="${mailbox.prefs.mailLocalDeliveryDisabled}"/>
         </td>
     </tr>
      <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td>
             <hr>
         </td>
     </tr>
</c:if>
<c:if test="${mailbox.features.newMailNotification}">
     <tr>
         <td class='ZOptionsTableLabel'>
             ${messageArrives}
             <c:set var="messageArrives" value="&nbsp;"/>     
         </td>
         <td>
             <app:optCheckbox boxfirst="true" trailingcolon="true" label="mailNotifEnabled" pref="zimbraPrefNewMailNotificationEnabled"
                              checked="${mailbox.prefs.newMailNotificationsEnabled}"/>
         </td>
     </tr>
      <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td style='padding-left:20px'>
             <input id="zimbraPrefNewMailNotificationAddress" size="40" type="text" name='zimbraPrefNewMailNotificationAddress' autocomplete='off' value="${fn:escapeXml(mailbox.prefs.newMailNotificationAddress)}">
             <span style='padding-left:5px' class='ZOptionsHint'><fmt:message key="optionsEmailAddressHint"/></span>
         </td>
     </tr>
     <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td>
             <hr>
         </td>
     </tr>
</c:if>
<c:if test="${mailbox.features.outOfOfficeReply}">
       <tr>
         <td class='ZOptionsTableLabel'>
                 ${messageArrives}
             <c:set var="messageArrives" value="&nbsp;"/>
         </td>
         <td>
             <app:optCheckbox boxfirst="true" trailingcolon="true" label="awayMessageEnabled" pref="zimbraPrefOutOfOfficeReplyEnabled"
                              checked="${mailbox.prefs.outOfOfficeReplyEnabled}"/>
         </td>
     </tr>
      <tr>
         <td class='ZOptionsTableLabel'>
             &nbsp;
         </td>
         <td style='padding-left:20px'>
             <textarea id="zimbraPrefOutOfOfficeReply" name='zimbraPrefOutOfOfficeReply' cols='60' rows='4'>${fn:escapeXml(mailbox.prefs.outOfOfficeReply)}</textarea>
         </td>
     </tr>
</c:if>
     <app:optSeparator/>
     <tr>
         <td class='ZOptionsTableLabel'>
              <fmt:message key="optionsMessagesFromMe"/> :
         </td>
         <td>
             <fmt:message key="removeDupesToSelf"/>
                 :
         </td>
     </tr>
     <tr>
         <td class='ZOptionsTableLabel'>
              &nbsp;
         </td>
         <td>
             <table border="0" cellpadding="0" cellspacing="3">
                 <tr>
                     <c:set var="dedupe" value="${mailbox.prefs.dedupeMessagesSentToSelf}"/>
                     <td>
                         <input id="dedupeNone" type="radio" name="zimbraPrefDedupeMessagesSentToSelf" value="dedupeNone" <c:if test="${dedupe eq 'dedupeNone'}">checked</c:if>/>
                     </td>
                     <td>
                         <label for="dedupeNone"><fmt:message key="optionsDedupeNone"/></label>
                     </td>
                     <td>
                         <input id="secondCopy" type="radio" name="zimbraPrefDedupeMessagesSentToSelf" value="secondCopyifOnToOrCC" <c:if test="${dedupe eq 'secondCopyifOnToOrCC'}">checked</c:if>/>
                     </td>
                     <td>
                         <label for="secondCopy"><fmt:message key="optionsDedupeSecondCopy"/></label>
                     </td>
                     <td>
                         <input id="dedupeall" type="radio" name="zimbraPrefDedupeMessagesSentToSelf" value="dedupeAll" <c:if test="${dedupe eq 'dedupeAll'}">checked</c:if>/>
                     </td>
                     <td>
                         <label for="dedupeall"><fmt:message key="optionsDedupeAll"/></label>
                     </td>
                 </tr>
             </table>
         </td>
     </tr>
      <tr>
        <td colspan="2">
            &nbsp;
        </td>
     </tr>
</table>
