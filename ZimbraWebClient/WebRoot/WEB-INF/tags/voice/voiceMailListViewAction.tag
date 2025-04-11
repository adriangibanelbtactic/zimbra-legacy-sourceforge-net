<%@ tag body-content="empty" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<app:handleError>
<zm:getMailbox var="mailbox"/>
<c:set var="ids" value="${fn:join(paramValues.voiceId, ',')}"/>
<c:set var="phone" value="${fn:join(paramValues.phone, ',')}"/>
<c:set var="folderId" value="${not empty paramValues.folderId[0] ? paramValues.folderId[0] : paramValues.folderId[1]}"/>
<c:set var="actionOp" value="${not empty paramValues.actionOp[0] ? paramValues.actionOp[0] :  paramValues.actionOp[1]}"/>

<c:choose>
    <c:when test="${zm:actionSet(param, 'actionDelete')}">
        <zm:trashVoiceMail var="result" phone="${phone}" id="${ids}"/>
        <app:status>
            <fmt:message key="actionVoiceMailMovedTrash">
                <fmt:param value="${result.idCount}"/>
            </fmt:message>
        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionReplyByEmail') or zm:actionSet(param, 'actionForwardByEmail')}">
        <c:choose>
            <c:when test="${empty paramValues.voiceId}">
                <app:status style="Warning">
                    <fmt:message key="actionNoVoiceMailMessageSelected"/>
                </app:status>
            </c:when>
            <c:otherwise>
                <zm:uploadVoiceMail var="uploadId" phone="${phone}" id="${ids}"/>
                <c:choose>
                    <c:when test="${zm:actionSet(param, 'actionReplyByEmail')}">
                        <fmt:message key="voiceMailReplySubject" var="subject"/>
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="voiceMailForwardSubject" var="subject"/>
                    </c:otherwise>
                </c:choose>
                <jsp:forward page="/h/compose">
                    <jsp:param name="subject" value="${subject}"/>
                    <jsp:param name="body" value=""/>
                    <jsp:param name="attachId" value="${uploadId}"/>
                    <jsp:param name="attachName" value="voicemail.wav"/>
                    <jsp:param name="attachUrl" value="/service/extension/velodrome/voice/~/voicemail?phone=${phone}&id=${ids}"/>
                </jsp:forward>
            </c:otherwise>
        </c:choose>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionMarkHeard')}">
        <zm:markVoiceMailHeard var="result" phone="${phone}" id="${ids}" heard="true"/>
        <app:status>
            <fmt:message key="actionVoiceMailMarkedHeard">
                <fmt:param value="${result.idCount}"/>
            </fmt:message>

        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionMarkUnheard')}">
        <zm:markVoiceMailHeard var="result" phone="${phone}" id="${ids}" heard="false"/>
        <app:status>
            <fmt:message key="actionVoiceMailMarkedUnheard">
                <fmt:param value="${result.idCount}"/>
            </fmt:message>
        </app:status>
    </c:when>
</c:choose>

</app:handleError>