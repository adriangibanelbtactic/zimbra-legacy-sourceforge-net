<%@ tag body-content="empty" %>
<%@ attribute name="mailbox" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZMailboxBean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>

<zm:getUserAgent var="ua" session="true"/>
<c:set var="suffix" value="${ua.isOsWindows ? '.win' : ua.isOsMac ? '.mac' : ua.isOsLinux ? '.linux' :  ''}"/>
<fmt:bundle basename="/keys/ZhKeys">
    <fmt:message var="sections" key="sections"/>
    <table border="0" cellpadding="0" cellspacing="4" width=100%>
        <tbody>
            <app:optShortcutSection section="global" suffix="${suffix}"/>
            <app:optShortcutSection section="mail" suffix="${suffix}"/>
            <app:optShortcutSection section="compose" suffix="${suffix}"/>
            <c:if test="${mailbox.features.conversations}">
                <app:optShortcutSection section="conversation" suffix="${suffix}"/>
            </c:if>
            <c:if test="${mailbox.features.voice}">
                <app:optShortcutSection section="voicemail" suffix="${suffix}"/>
                <app:optShortcutSection section="call" suffix="${suffix}"/>
            </c:if>
            <c:if test="${mailbox.features.contacts}">
                <app:optShortcutSection section="contacts" suffix="${suffix}"/>
            </c:if>
            <c:if test="${mailbox.features.calendar}">
                <app:optShortcutSection section="calendar" suffix="${suffix}"/>
            </c:if>
            <app:optShortcutSection section="list" suffix="${suffix}"/>
            <app:optShortcutSection section="mfolders" suffix="${suffix}"/>
            <c:if test="${mailbox.features.tagging}">
                <app:optShortcutSection section="mtags" suffix="${suffix}"/>
            </c:if>
            <c:if test="${mailbox.features.contacts}">
                <app:optShortcutSection section="maddrbooks" suffix="${suffix}"/>
            </c:if>
            <c:if test="${mailbox.features.calendar}">
                <app:optShortcutSection section="mcalendars" suffix="${suffix}"/>
            </c:if>
        </tbody>
    </table>
</fmt:bundle>