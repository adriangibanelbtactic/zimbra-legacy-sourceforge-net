<%@ tag body-content="empty" %>
<%@ attribute name="appt" rtexprvalue="true" required="true" type="com.zimbra.cs.zclient.ZAppointmentHit" %>
<%@ attribute name="start" rtexprvalue="true" required="true"%>
<%@ attribute name="end" rtexprvalue="true" required="true"%>
<%@ attribute name="selected" rtexprvalue="true" required="false"%>
<%@ attribute name="timezone" rtexprvalue="true" required="true" type="java.util.TimeZone"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<fmt:setTimeZone value="${timezone}"/>
<c:set var="color" value="${zm:getFolder(pageContext,appt.folderId).styleColor}"/>
<c:set var="needsAction" value="${appt.partStatusNeedsAction}"/>
<fmt:message var="noSubject" key="noSubject"/>
<c:set var="subject" value="${empty appt.name ? noSubject : appt.name}"/>
<app:calendarUrl appt="${appt}" var="apptUrl"/>
<c:if test="${selected}">
    <table width="100%" height="100%" border="0" cellspacing="0" cellpadding="0">
        <tr>
        <td class='ZhApptSel'>
</c:if>
<c:set var="needImages" value="${appt.otherAttendees or appt.exception or appt.hasTags or appt.isFlagged}"/>

<c:choose>
    <c:when test="${appt.allDay}">
        <c:if test="${appt.startTime lt start}"><c:set var="bleft" value='border-left:none;'/></c:if>
        <c:if test="${appt.endTime gt end}"><c:set var="bright" value='border-right:none;'/></c:if>

        <table <c:if test="${not empty bleft or not empty bright}">style="${bleft}${bright}"</c:if>
                class='ZhCalDayAllDayAppt${needsAction ? 'New ' : ' '} ${color}${needsAction ? 'Dark' : 'Light'}'

                width="100%" height="100%" border="0" cellspacing="0" cellpadding="1">
            <tr>
                <td>
                    <a href="${apptUrl}">${fn:escapeXml(subject)}</a>
                </td>
                <c:if test="${needImages}">
                    <td width=1% align=right>
                        <table border="0" cellspacing="0" cellpadding="0">
                            <tr>
                                <c:if test="${appt.otherAttendees}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptMeeting.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${appt.exception}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptException.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${not empty appt.tagIds}">
                                    <td><app:miniTagImage ids="${appt.tagIds}"/></td>
                                </c:if>
                                <c:if test="${not empty appt.isFlagged}">
                                    <td><app:flagImage flagged="${appt.isFlagged}"/></td>
                                </c:if>
                            </tr>
                        </table>
                    </td>
                </c:if>
            </tr>
        </table>
    </c:when>
    <c:when test="${appt.duration gt 1000*60*15}">
        <table class='ZhCalDayAppt${needsAction ? 'New' : ''}' width=100% height=100% border=0 cellspacing=0 cellpadding="2">
            <tr>
                <td colspan="${needImages ? 1 : 2}" nowrap class='${color}${appt.partStatusNeedsAction ? 'Dark' : 'Light'}' valign=top>
                    <c:choose>
                        <c:when test="${appt.startTime lt start}">
                            <fmt:formatDate value="${appt.startDate}" type="both" timeStyle="short" dateStyle="short"/>
                        </c:when>
                        <c:otherwise>
                            <fmt:formatDate value="${appt.startDate}" type="time" timeStyle="short"/>
                        </c:otherwise>
                    </c:choose>
                </td>
                <c:if test="${needImages}">
                    <td width=1% align=right class='${color}${appt.partStatusNeedsAction ? 'Dark' : 'Light'}'>
                        <table border="0" cellspacing="0" cellpadding="0">
                            <tr>
                                <c:if test="${appt.otherAttendees}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptMeeting.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${appt.exception}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptException.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${not empty appt.tagIds}">
                                    <td><app:miniTagImage ids="${appt.tagIds}"/></td>
                                </c:if>
                                <c:if test="${not empty appt.isFlagged}">
                                    <td><app:flagImage flagged="${appt.isFlagged}"/></td>
                                </c:if>
                            </tr>
                        </table>
                    </td>
                </c:if>
            </tr>
            <tr>
                <td colspan=2 height=100% class='${color}${needsAction ? '' : 'Bg'}' valign=top>
                    <a href="${apptUrl}">${fn:escapeXml(subject)}</a>
                </td>
            </tr>
            <c:if test="${appt.duration gt zm:MSECS_PER_HOUR()}">
            <tr>
                <td colspan=2 align=left valign=bottom height=1% class='ZhCalDayApptEnd ${color}${needsAction ? '' : 'Bg'}'>
                    <c:choose>
                        <c:when test="${appt.endTime gt end}">
                            <fmt:formatDate value="${appt.endDate}" type="both" timeStyle="short" dateStyle="short"/>
                        </c:when>
                        <c:otherwise>
                            <fmt:formatDate value="${appt.endDate}" type="time" timeStyle="short"/>
                        </c:otherwise>
                    </c:choose>                    
                </td>
            </tr>
            </c:if>
        </table>
    </c:when>
    <c:otherwise>
        <table class='ZhCalDayAppt' width=100% height=100% border=0 cellspacing=0 cellpadding="2">
            <tr>
                <td class='${color}${needsAction ? 'Dark' : 'Light'}' valign=top>
                    <fmt:formatDate value="${appt.startDate}" type="time" timeStyle="short"/>
                     &nbsp; <a href="${apptUrl}">${fn:escapeXml(subject)}</a>
                </td>
                <c:if test="${needImages}">
                    <td valign='top' width=1% align=right class='${color}${needsAction ? 'Dark' : 'Light'}'>
                        <table border="0" cellspacing="0" cellpadding="0">
                            <tr>
                                <c:if test="${appt.otherAttendees}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptMeeting.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${appt.exception}">
                                    <td valign='top'>
                                        <app:img src="calendar/ApptException.gif"/>
                                    </td>
                                </c:if>
                                <c:if test="${not empty appt.tagIds}">
                                    <td><app:miniTagImage ids="${appt.tagIds}"/></td>
                                </c:if>
                                <c:if test="${not empty appt.isFlagged}">
                                    <td><app:flagImage flagged="${appt.isFlagged}"/></td>
                                </c:if>                                
                            </tr>
                        </table>
                    </td>
                </c:if>
            </tr>
        </table>
    </c:otherwise>
</c:choose>
<c:if test="${selected}">
    </td>
    </tr>
    </table>
</c:if>