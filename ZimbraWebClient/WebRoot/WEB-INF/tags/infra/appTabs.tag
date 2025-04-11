<%@ tag body-content="empty" %>
<%@ attribute name="selected" rtexprvalue="true" required="false" %>
<%@ attribute name="keys" rtexprvalue="true" required="true" %>
<%@ attribute name="mailbox" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZMailboxBean"%>
<%@ attribute name="context" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.tag.SearchContext"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<table cellpadding=0 cellspacing=0>
    <tr class='Tabs'>
        <td class='TabSpacer'/>
        <td class='TabSpacer'/>        
        <td class='Tab ${selected=='mail' ? 'TabSelected' :'TabNormal'}'>
            <a href="<c:url value="/h/search"/>" <c:if test="${keys}">accesskey="m"</c:if>>
                <app:img src="mail/MailApp.gif" altkey='ALT_APP_MAIL'/>
                <span><fmt:message key="mail"/></span>
            </a>
        </td>
        <c:if test="${mailbox.features.contacts}">
            <td class='TabSpacer'/>
            <td class='Tab ${selected=='contacts' ? 'TabSelected' :'TabNormal'}'>
                <a href="<c:url value="/h/search?st=contact"/>" <c:if test="${keys}">accesskey="c"</c:if>><app:img src="contacts/Contact.gif" altkey='ALT_APP_CONTACTS'/><span><fmt:message
                        key="addressBook"/></span></a>
            </td>
        </c:if>             
        <td class='TabSpacer'/>
        <td class='Tab ${selected=='options' ? 'TabSelected' :'TabNormal'}'>
            <a href="<c:url value="/h/options"/>" <c:if test="${keys}">accesskey="y"</c:if>><app:img src="common/Preferences.gif" altkey='ALT_APP_OPTIONS'/><span><fmt:message
                    key="options"/></span></a>
        </td>
        <td class='TabSpacer'/>
        <td class='Tab ${selected=='compose' ? 'TabSelected' :'TabNormal'}'>
            <c:choose>
                <c:when test="${not empty context}">
                    <zm:currentResultUrl var="composeUrl" value="/h/search" context="${context}" paction="${param.action}" action="compose"/>
                </c:when>
                <c:otherwise>
                    <c:url var="composeUrl" value="/h/search?action=compose"/>
                </c:otherwise>
            </c:choose>
            <a href="${composeUrl}" <c:if test="${keys}">accesskey="e"</c:if>><app:img src="mail/NewMessage.gif" altkey='ALT_APP_COMPOSE'/><span><fmt:message
                    key="compose"/></span></a>
        </td>
        <td class='TabSpacer'/>
        <c:choose>
            <c:when test="${selected =='managetags'}">
                <td class='Tab TabSelected'>
                    <app:img src="tag/Tag.gif" altkey='ALT_APP_MANAGE_TAGS'/><span><fmt:message key="tags"/></span>
                </td>
                <td class='TabSpacer'/>
            </c:when>
            <c:when test="${selected =='managefolders'}">
                <td class='Tab TabSelected'>
                    <app:img src="common/Folder.gif" altkey='ALT_APP_MANAGE_FOLDERS'/><span><fmt:message key="folders"/></span>
                </td>
                <td class='TabSpacer'/>
            </c:when>
            <c:when test="${selected =='managesearches'}">
                <td class='Tab TabSelected'>
                    <app:img src="common/SearchFolder.gif" altkey='ALT_APP_MANAGE_SEARCHES'/><span><fmt:message key="searches"/></span>
                </td>
                <td class='TabSpacer'/>
            </c:when>
            <c:when test="${selected =='manageaddressbooks'}">
                <td class='Tab TabSelected'>
                    <app:img src="contacts/ContactsFolder.gif" altkey='ALT_APP_MANAGE_ADDRESS_BOOKS'/><span><fmt:message key="addressBooks"/></span>
                </td>
                <td class='TabSpacer'/>
            </c:when>
        </c:choose>
        <%--
        <td class='Tab ${selected=='calendar' ? ' TabSelected' :' TabNormal'}'>
            <app:img src="calendar/CalendarApp.gif"/>
            <span><fmt:message key="calendar"/></span>
        </td>
        --%>
        <td class='TabFiller'>
            &nbsp;
        </td>
    </tr>
</table>
