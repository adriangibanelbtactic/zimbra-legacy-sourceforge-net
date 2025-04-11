<%@ tag body-content="empty" %>
<%@ attribute name="ids" rtexprvalue="true" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="mo" uri="com.zimbra.mobileclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<c:set var="tags" value="${zm:getTags(pageContext, ids)}"/>
<c:set var="tagNames" value="${fn:escapeXml(zm:getTagNames(pageContext, ids))}"/>
<c:if test="${fn:length(tags) eq 0}">&nbsp;</c:if>
<c:if test="${fn:length(tags) eq 1}"><mo:img src="${tags[0].miniImage}"/></c:if>
<c:if test="${fn:length(tags) gt 1}"><mo:img src="tag/MiniTagStack.gif"/></c:if>
