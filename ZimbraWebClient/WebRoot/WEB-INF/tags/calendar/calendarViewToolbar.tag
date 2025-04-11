<%@ tag body-content="empty" %>
<%@ attribute name="context" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.tag.SearchContext"%>
<%@ attribute name="keys" rtexprvalue="true" required="true" %>
<%@ attribute name="title" rtexprvalue="true" required="true" %>
<%@ attribute name="today" rtexprvalue="true" required="true" type="java.util.Calendar"%>
<%@ attribute name="date" rtexprvalue="true" required="true" type="java.util.Calendar"%>
<%@ attribute name="timezone" rtexprvalue="true" required="true" type="java.util.TimeZone"%>
<%@ attribute name="nextDate" rtexprvalue="true" required="true" type="java.util.Calendar"%>
<%@ attribute name="prevDate" rtexprvalue="true" required="true" type="java.util.Calendar"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>


<c:if test="${empty requestScope.calViewToolbarCache}">
    <zm:getMailbox var="mailbox"/>
    <c:set var="calViewToolbarCache" scope="request">
        <fmt:formatDate var="dateDf" value="${date.time}" pattern="yyyyMMdd"/>
        <app:calendarUrl var="newApptUrl" date="${dateDf}" action="edit"/>
        <app:calendarUrl var="dayViewUrl" date="${dateDf}" view="day"/>
        <app:calendarUrl var="weekViewUrl" date="${dateDf}" view="week"/>
        <app:calendarUrl var="workWeekViewUrl" date="${dateDf}" view="workWeek"/>
        <app:calendarUrl var="monthViewUrl" date="${dateDf}" view="month"/>
        <app:calendarUrl var="scheduleViewUrl" date="${dateDf}" view="schedule"/>
        <td height=100%>
            <a id="CAL_NEWAPPT" href="${newApptUrl}"><app:img altkey="ALT_CAL_NEW_APPT" src="calendar/NewAppointment.gif"/><span><fmt:message key="new"/></span></a>
        </td>
        <td height=100%><div class='vertSep'/></td>
         <td height=100%>
            <a id="CAL_DAY" href="${dayViewUrl}"><app:img altkey="ALT_CAL_DAY_VIEW" src="calendar/DayView.gif"/><span><fmt:message key="day"/></span></a>
        </td>
        <td height=100%>
            <a id="CAL_WORK" href="${workWeekViewUrl}"><app:img altkey="ALT_CAL_WORKWEEK_VIEW" src="calendar/WorkWeekView.gif"/><span><fmt:message key="workWeek"/></span></a>
        </td>
        <td height=100%>
            <a id="CAL_WEEK" href="${weekViewUrl}"><app:img altkey="ALT_CAL_WEEK_VIEW" src="calendar/WeekView.gif"/><span><fmt:message key="week"/></span></a>
        </td>
        <td height=100%>
            <a id="CAL_MONTH" href="${monthViewUrl}"><app:img altkey="ALT_CAL_MONTH_VIEW" src="calendar/MonthView.gif"/><span><fmt:message key="month"/></span></a>
        </td>
        <td height=100%>
            <a id="CAL_SCHED" href="${scheduleViewUrl}"><app:img altkey="ALT_CAL_SCHEDULE_VIEW" src="calendar/GroupSchedule.gif"/><span><fmt:message key="schedule"/></span></a>
        </td>
        <td height=100%><div class='vertSep'/></td>
        <app:calendarUrl var="todayUrl" nodate="true"/>
        <td height=100%>
            <a id="CAL_TODAY" href="${todayUrl}"><app:img altkey="ALT_CAL_TODAY" src="calendar/Date.gif"/><span><fmt:message key="today"/></span></a>
        </td>
    </c:set>
</c:if>

<table width=100% cellspacing=0 class='Tb'>
    <tr>
        <td align=left class=TbBt id="caltb">
            <table cellpadding="0" cellspacing="0">
                <tr valign="middle">
                    <app:calendarUrl var="refreshUrl" refresh="1"/>
                    <td height=100%>
                        <a href="${refreshUrl}" <c:if test="${keys}"> id="CAL_REFRESH"</c:if>><app:img altkey="ALT_CAL_REFRESH" src="arrows/Refresh.gif"/><span><fmt:message key="refresh"/></span></a>
                    </td>
                    <td>
                        <div class='vertSep'/>
                    </td>
                    ${requestScope.calViewToolbarCache}
                </tr>
            </table>
        </td>
        <td align=right>
            <app:calendarUrl var="prevUrl" rawdate="${prevDate}" timezone="${timezone}"/>
            <app:calendarUrl var="nextUrl" rawdate="${nextDate}" timezone="${timezone}"/>
            <table cellspacing=5 cellpadding=0>
                <tr>
                    <td>
            <a <c:if test="${keys}">id="PREV_PAGE"</c:if> href="${prevUrl}"><app:img altkey="ALT_PAGE_PREVIOUS" src="arrows/LeftArrow.gif" border="0"/></a>
                    </td>
                    <td>
            ${fn:escapeXml(title)}
                    </td>
                    <td>
            <a <c:if test="${keys}">id="NEXT_PAGE"</c:if> href="${nextUrl}"><app:img alt="ALT_PAGE_NEXT" src="arrows/RightArrow.gif" border="0"/></a>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
