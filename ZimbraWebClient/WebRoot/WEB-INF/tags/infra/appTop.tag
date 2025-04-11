<%@ tag body-content="scriptless" %>
<%@ attribute name="query" rtexprvalue="true" required="false" %>
<%@ attribute name="keys" rtexprvalue="true" required="true" %>
<%@ attribute name="calendars" rtexprvalue="true" required="false" %>
<%@ attribute name="voice" rtexprvalue="true" required="false" %>
<%@ attribute name="mailbox" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZMailboxBean"%>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<table width=100% cellspacing=0>
<tr height=35 >
    <td style='width:80%' height=25 nowrap class='SearchBar'>
        <c:choose>
            <c:when test="${calendars}">
                <app:calendarUrl var="searchUrl"/>
            </c:when>
            <c:otherwise>
                <c:url var="searchUrl" value="/h/search"/>
            </c:otherwise>
            </c:choose>
        <form method="get" action="${searchUrl}">
            <c:set var="query">${fn:escapeXml((!empty query and mailbox.prefs.showSearchString) ? query : param.sq)}</c:set>
            <c:if test="${voice}">
                <c:set var="query"></c:set>
            </c:if>
            &nbsp;<label for="searchField"><fmt:message key="find"/> :</label>
            <input id="searchField" class="searchField" style='width:50%' maxlength=2048 name=sq value="${query}">
            &nbsp;<fmt:message key="in"/>&nbsp;
            <c:choose>
                <c:when test="${param.st eq 'contact'}"><c:set var="isContact" value="${true}"/></c:when>
                <c:otherwise><c:set var="isMail" value="${true}"/></c:otherwise>
            </c:choose>
            <select name="st">
                <option <c:if test="${isMail}">selected </c:if>value="${mailbox.features.conversations ? mailbox.prefs.groupMailBy : 'message'}"/><fmt:message key="searchMail"/>
                <c:if test="${mailbox.features.contacts}">
                    <option <c:if test="${isContact}">selected </c:if>value="contact"/><fmt:message key="searchPersonalContacts"/>
                </c:if>
                <c:if test="${mailbox.features.calendar}">
                    <option <c:if test="${calendars}">selected </c:if> value="appointment"/><fmt:message key="searchPersonalCalendars"/>
                </c:if>
            </select>
            <input class="SearchButton" type=submit name=search value="<fmt:message key="search"/>">
            <c:if test="${calendars}">
                <c:if test="${not empty param.tz}"><input type="hidden" name="tz" value='${param.tz}'/></c:if>
                <c:if test="${not empty param.date}"><input type="hidden" name="date" value='${param.date}'/></c:if>
                <c:if test="${not empty param.view}"><input type="hidden" name="view" value='${param.view}'/></c:if>
            </c:if>
        </form>
    </td>
    <td>
        <c:set var="max" value="${mailbox.attrs.zimbraMailQuota[0]}"/>
        <table cellpadding=2 cellspacing=0 width=100%>
            <tr>
                <td align=center>
                    <b>${fn:escapeXml(mailbox.name)}</b>
                </td>
            </tr>
            <tr>
                <td align=center>
                    <fmt:message var="unlimited" key="unlimited"/>
                    <fmt:message key="quotaUsage">
                        <fmt:param value="${zm:displaySizeFractions(mailbox.size,2)}"/>
                        <fmt:param value="${max==0 ? unlimited : zm:displaySizeFractions(max,2)}"/>
                    </fmt:message>
                </td>
            </tr>
        </table>
    </td>
</tr>
</table>

