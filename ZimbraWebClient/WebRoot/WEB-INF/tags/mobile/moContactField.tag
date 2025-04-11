<%@ tag body-content="empty" %>
<%@ attribute name="label" rtexprvalue="true" required="true" %>
<%@ attribute name="value" rtexprvalue="true" required="false" %>
<%@ attribute name="isphone" rtexprvalue="true" required="false" %>
<%@ attribute name="isurl" rtexprvalue="true" required="false" %>
<%@ attribute name="isemail" rtexprvalue="true" required="false" %>
<%@ attribute name="isaddress" rtexprvalue="true" required="false" %>
<%@ attribute name="street" rtexprvalue="true" required="false" %>
<%@ attribute name="city" rtexprvalue="true" required="false" %>
<%@ attribute name="state" rtexprvalue="true" required="false" %>
<%@ attribute name="postalcode" rtexprvalue="true" required="false" %>
<%@ attribute name="country" rtexprvalue="true" required="false" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<c:if test="${(not empty value) or isaddress}">
    <fmt:message key="${label}" var="label"/>
    <tr>
        <td <c:if test="${isaddress}">valign=top</c:if> class='zo_cv_fname'>${fn:escapeXml(label)}</td>
        <td class='zo_cv_fval'>
            <c:choose>
                <c:when test="${isurl}">
                    <c:set var="prefix" value="${fn:contains(value,'//') ? '' : 'http://'}"/>
                    <a target=_new href="<c:url value="${prefix}${value}"/>">${fn:escapeXml(value)}</a>
                </c:when>
                <c:when test="${isaddress}">
                    <c:url var="gmaps" value="http://maps.google.com/maps">
                        <c:param name="q" value="${street} ${city} ${state} ${postalcode} ${country}"/>
                    </c:url>
                    <a target=_new href="${gmaps}">
                        <c:if test="${not empty street}">${fn:escapeXml(street)}<br></c:if>
                        <c:if test="${not empty city}">
                            ${fn:escapeXml(city)} <c:if test="${not empty state or not empty postalcode}">,</c:if>
                        </c:if>
                        <c:if test="${not empty state}">${fn:escapeXml(state)}</c:if>
                        <c:if test="${not empty postalcode}">${fn:escapeXml(postalcode)}</c:if>
                        <c:if test="${not (empty state and empty street and empty postalcode)}"><br></c:if>
                        <c:if test="${not empty country}">${fn:escapeXml(country)}</c:if>
                    </a>
                </c:when>
                <c:when test="${isphone}">
                    <c:url var="url" value="tel:${value}"/>
                    <a href="${url}">${fn:escapeXml(value)}</a>
                </c:when>
                <c:when test="${isemail}">
                    <c:url var="url" value="mailto:${value}"/>
                    <a href="${url}">${fn:escapeXml(value)}</a>
                </c:when>
                <c:otherwise>
                    ${fn:escapeXml(value)}
                </c:otherwise>
            </c:choose>
        </td>
    </tr>
</c:if>
