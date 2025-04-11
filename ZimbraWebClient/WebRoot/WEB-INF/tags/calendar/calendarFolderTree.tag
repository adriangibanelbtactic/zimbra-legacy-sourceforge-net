<%@ tag body-content="empty" %>
<%@ attribute name="editmode" rtexprvalue="true" required="false" %>
<%@ attribute name="keys" rtexprvalue="true" required="true" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<zm:getMailbox var="mailbox"/>

<jsp:useBean id="expanded" scope="session" class="java.util.HashMap" />
<c:set var="expanded" value="${sessionScope.expanded.calendars ne 'collapse'}"/>

<div class=Tree>
    <table width=100% cellpadding=0 cellspacing=0>
        <tr>
            <c:url var="toggleUrl" value="/h/calendar">
                <c:param name="st" value="contact"/>
                <%--TODO: add context (date, view, etc) --%>
                 <c:param name="${expanded ? 'collapse' : 'expand'}" value="calendars"/>
             </c:url>
             <th style='width:20px'><a href="${toggleUrl}"><app:img altkey="${ expanded ? 'ALT_TREE_EXPANDED' : 'ALT_TREE_COLLAPSED'}" src="${ expanded ? 'dwt/NodeExpanded.gif' : 'dwt/NodeCollapsed.gif'}"/></a></th>
            <th class='Header'><fmt:message key="calendars"/></th>
            <th width='1%' align='right' class='ZhTreeEdit'>
                <c:url value="/h/mcalendars" var="mabUrl"/>
                <a href="${mabUrl}" ><fmt:message key="TREE_EDIT"/></a>
            </th>
        </tr>

        <c:if test="${expanded}">

            <zm:forEachFolder var="folder">
                <c:if test="${folder.isAppointmentView and !folder.isSearchFolder and !folder.isMountPoint}">
                    <app:calendarFolder folder="${folder}"/>
                </c:if>
            </zm:forEachFolder>

            <zm:forEachFolder var="folder">
                <c:if test="${folder.isAppointmentView and !folder.isSearchFolder and folder.isMountPoint}">
                    <app:calendarFolder folder="${folder}"/>
                </c:if>
            </zm:forEachFolder>
        </c:if>
    </table>
</div>
