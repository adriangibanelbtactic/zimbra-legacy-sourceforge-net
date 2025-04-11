<%@ tag body-content="empty" %>
<%@ attribute name="selected" rtexprvalue="true" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>

<app:handleError>
<zm:getMailbox var="mailbox"/>
<zm:modifyPrefs var="updated">

    <c:choose>
        <%-- GENERAL --%>
        <c:when test="${selected eq 'general'}">
            <zm:pref name="zimbraPrefIncludeSpamInSearch" value="${param.zimbraPrefIncludeSpamInSearch eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefIncludeTrashInSearch" value="${param.zimbraPrefIncludeTrashInSearch eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefShowSearchString" value="${param.zimbraPrefShowSearchString eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefClientType" value="${param.zimbraPrefClientType}"/>
            <c:if test="${mailbox.features.skinChange}">
                <zm:pref name="zimbraPrefSkin" value="${param.zimbraPrefSkin}"/>
            </c:if>
            <zm:pref name="zimbraPrefTimeZoneId" value="${param.zimbraPrefTimeZoneId}"/>
        </c:when>
        <%-- MAIL --%>
        <c:when test="${selected eq 'mail'}">
            <c:if test="${mailbox.features.conversations}">
                <zm:pref name="zimbraPrefGroupMailBy" value="${param.zimbraPrefGroupMailBy}"/>
            </c:if>
            <zm:pref name="zimbraPrefMailItemsPerPage" value="${param.zimbraPrefMailItemsPerPage}"/>
            <zm:pref name="zimbraPrefShowFragments" value="${param.zimbraPrefShowFragments eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <c:if test="${mailbox.features.initialSearchPreference}">
                <zm:pref name="zimbraPrefMailInitialSearch" value="${param.zimbraPrefMailInitialSearch}"/>
            </c:if>

            <c:if test="${mailbox.features.outOfOfficeReply}">
                <zm:pref name="zimbraPrefOutOfOfficeReplyEnabled" value="${param.zimbraPrefOutOfOfficeReplyEnabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
                <zm:pref name="zimbraPrefOutOfOfficeReply" value="${param.zimbraPrefOutOfOfficeReply}"/>
            </c:if>

            <c:if test="${mailbox.features.newMailNotification}">
                <zm:pref name="zimbraPrefNewMailNotificationEnabled" value="${param.zimbraPrefNewMailNotificationEnabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
                <zm:pref name="zimbraPrefNewMailNotificationAddress" value="${param.zimbraPrefNewMailNotificationAddress}"/>
            </c:if>

            <c:if test="${mailbox.features.mailForwarding}">
                <zm:pref name="zimbraPrefMailForwardingAddress" value="${param.FORWARDCHECKED eq 'TRUE' ? param.zimbraPrefMailForwardingAddress : ''}"/>
                <zm:pref name="zimbraPrefMailLocalDeliveryDisabled" value="${param.zimbraPrefMailLocalDeliveryDisabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            </c:if>

            <zm:pref name="zimbraPrefMessageViewHtmlPreferred" value="${param.zimbraPrefMessageViewHtmlPreferred eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefDedupeMessagesSentToSelf" value="${param.zimbraPrefDedupeMessagesSentToSelf}"/>
        </c:when>
        <%-- COMPOSING --%>
        <c:when test="${selected eq 'composing'}">
            <zm:pref name="zimbraPrefReplyIncludeOriginalText" value="${param.zimbraPrefReplyIncludeOriginalText}"/>
            <zm:pref name="zimbraPrefForwardIncludeOriginalText" value="${param.zimbraPrefForwardIncludeOriginalText}"/>
            <zm:pref name="zimbraPrefForwardReplyPrefixChar" value="${param.zimbraPrefForwardReplyPrefixChar}"/>
            <zm:pref name="zimbraPrefSaveToSent" value="${param.zimbraPrefSaveToSent eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
        </c:when>
        <%-- SIGNATURES --%>
        <c:when test="${selected eq 'signatures'}">
            <zm:pref name="zimbraPrefMailSignatureStyle" value="${param.zimbraPrefMailSignatureStyle}"/>
            <zm:pref name="zimbraPrefMailSignatureEnabled" value="${param.zimbraPrefMailSignatureEnabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
        </c:when>
        <%-- ACCOUNTS --%>
        <c:when test="${selected eq 'accounts'}">
            <zm:pref name="zimbraPrefIdentityName" value="${param.zimbraPrefIdentityName}"/>
            <zm:pref name="zimbraPrefFromDisplay" value="${param.zimbraPrefFromDisplay}"/>
            <zm:pref name="zimbraPrefFromAddress" value="${param.zimbraPrefFromAddress}"/>
            <zm:pref name="zimbraPrefReplyToDisplay" value="${param.zimbraPrefReplyToDisplay}"/>
            <zm:pref name="zimbraPrefReplyToAddress" value="${param.zimbraPrefReplyToAddress}"/>
            <zm:pref name="zimbraPrefReplyToEnabled" value="${param.zimbraPrefReplyToEnabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefDefaultSignatureId" value="${param.zimbraPrefDefaultSignatureId}"/>            
        </c:when>
        <%-- ADDRESS BOOK --%>
        <c:when test="${selected eq 'addressbook'}">
            <zm:pref name="zimbraPrefAutoAddAddressEnabled" value="${param.zimbraPrefAutoAddAddressEnabled eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefContactsPerPage" value="${param.zimbraPrefContactsPerPage}"/>
        </c:when>
        <%-- CALENDAR --%>
        <c:when test="${selected eq 'calendar'}">
            <zm:pref name="zimbraPrefUseTimeZoneListInCalendar" value="${param.zimbraPrefUseTimeZoneListInCalendar eq 'TRUE' ? 'TRUE' : 'FALSE'}"/>
            <zm:pref name="zimbraPrefCalendarInitialView" value="${param.zimbraPrefCalendarInitialView}"/>
            <zm:pref name="zimbraPrefCalendarFirstdayOfWeek" value="${param.zimbraPrefCalendarFirstdayOfWeek}"/>
            <zm:pref name="zimbraPrefCalendarDayHourStart" value="${param.zimbraPrefCalendarDayHourStart}"/>
            <zm:pref name="zimbraPrefCalendarDayHourEnd" value="${param.zimbraPrefCalendarDayHourEnd}"/>
        </c:when>
    </c:choose>
</zm:modifyPrefs>

<c:if test="${selected eq 'signatures'}">
    <c:forEach var="i" begin="0" end="${param.numSignatures}">
        <c:set var="origSignatureNameKey" value="origSignatureName${i}"/>
        <c:set var="signatureNameKey" value="signatureName${i}"/>
        <c:set var="origSignatureValueKey" value="origSignatureValue${i}"/>
        <c:set var="signatureValueKey" value="signatureValue${i}"/>
        <c:if test="${(param[origSignatureNameKey] ne param[signatureNameKey]) or
                (param[origSignatureValueKey] ne param[signtureValueKey])}">
            <c:set var="modSignatureWarning" value="${true}" scope="request"/>
            <c:choose>
                <c:when test="${empty param[signatureNameKey]}">
                    <app:status style="Warning"><fmt:message key="optionsNoSignatureName"/></app:status>
                </c:when>
                <c:when test="${empty param[signatureValueKey]}">
                    <app:status style="Warning"><fmt:message key="optionsNoSignatureValue"/></app:status>
                </c:when>
                <c:otherwise>
                    <c:set var="signatureIdKey" value="signatureId${i}"/>
                    <zm:modifySiganture id="${param[signatureIdKey]}"
                                        name="${param[signatureNameKey]}" value="${param[signatureValueKey]}"/>
                    <c:set var="signatureUpdated" value="${true}"/>
                    <c:set var="modSignatureWarning" value="${false}" scope="request"/>
                </c:otherwise>
            </c:choose>
        </c:if>
    </c:forEach>
</c:if>

<c:if test="${selected eq 'signatures' and not empty param.newSignature}">
    <c:set var="newSignatureWarning" value="${true}" scope="request"/>
    <c:choose>
        <c:when test="${empty param.newSignatureName}">
            <app:status style="Warning"><fmt:message key="optionsNoSignatureName"/></app:status>
        </c:when>
        <c:when test="${empty param.newSignatureValue}">
            <app:status style="Warning"><fmt:message key="optionsNoSignatureValue"/></app:status>
        </c:when>
        <c:otherwise>
            <zm:createSiganture var="sigId" name="${param.newSignatureName}" value="${param.newSignatureValue}"/>
            <c:set var="updated" value="${true}"/>
            <c:set var="newSignatureWarning" value="${false}" scope="request"/>
        </c:otherwise>
    </c:choose>
</c:if>

<c:choose>
    <c:when test="${newSignatureWarning or modSignatureWarning}">
        <%-- do nothing --%>
    </c:when>
    <c:when test="${updated or signatureUpdated}">
        <zm:getMailbox var="mailbox" refreshaccount="${true}"/>
        <app:status><fmt:message key="optionsSaved"/></app:status>
    </c:when>
    <c:otherwise>
        <app:status><fmt:message key="noOptionsChanged"/></app:status>        
    </c:otherwise>
</c:choose>
</app:handleError>