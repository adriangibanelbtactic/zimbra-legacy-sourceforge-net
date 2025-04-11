<%@ tag body-content="empty" %>
<%@ attribute name="searchResult" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZSearchResultBean"%>
<%@ attribute name="searchGalResult" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZSearchGalResultBean"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>


<table width=100% cellpadding=2 cellspacing=0>
    <tr valign='top'>
        <th width=1%>&nbsp;
        <th width=2%><fmt:message key="to"/>:
        <th width=2%><fmt:message key="cc"/>:
        <th width=2%><fmt:message key="bcc"/>:
        <th width=1%>&nbsp;
        <th width=1%>&nbsp;
        <th width=1%>&nbsp;
        <c:choose>
        <c:when test="${not empty searchGalResult}">
        <th nowrap><fmt:message key="email"/>
        </c:when>
        <c:otherwise>
        <th width=20% nowrap><fmt:message key="name"/>
        <th ><fmt:message key="email"/>
        </c:otherwise>
        </c:choose>
    </tr>
    <c:forEach items="${searchResult.hits}" var="hit" varStatus="status">
        <c:if test="${not empty hit.contactHit.displayEmail or hit.contactHit.isGroup}">
        <tr>
            <td width=1%>&nbsp;</td>
            <td width=2% nowrap><input type=checkbox  name="addTo" value="${fn:escapeXml(hit.contactHit.fullAddress)}"></td>
            <td width=2% nowrap><input type=checkbox name="addCc" value="${fn:escapeXml(hit.contactHit.fullAddress)}"></td>
            <td width=2% nowrap><input type=checkbox  name="addBcc" value="${fn:escapeXml(hit.contactHit.fullAddress)}"></td>
            <td width=1%><app:miniTagImage ids="${hit.contactHit.tagIds}"/></td>
            <td width=1%><app:img src="${hit.contactHit.image}" altkey="${hit.contactHit.imageAltKey}"/></td>
            <td width=1%>&nbsp;</td>
            <td width=20%>
                    ${fn:escapeXml(empty hit.contactHit.fileAsStr ? '' : hit.contactHit.fileAsStr)}
            </td>
            <td >&nbsp;${fn:escapeXml(hit.contactHit.displayEmail)}</td>
        </tr>
        </c:if>
    </c:forEach>
    <c:forEach items="${searchGalResult.contacts}" var="contact" varStatus="status">
        <c:if test="${not empty contact.displayEmail}">
        <tr>
            <td width=1%>&nbsp;</td>
            <td width=2% nowrap><input type=checkbox  name="addTo" value="${fn:escapeXml(contact.galFullAddress)}"></td>
            <td width=2% nowrap><input type=checkbox name="addCc" value="${fn:escapeXml(contact.galFullAddress)}"></td>
            <td width=2% nowrap><input type=checkbox  name="addBcc" value="${fn:escapeXml(contact.galFullAddress)}"></td>
            <td width=1%><app:miniTagImage ids="${contact.tagIds}"/></td>
            <td width=1%><app:img src="${contact.image}" altkey="${contact.imageAltKey}"/></td>
            <td width=1%>&nbsp;</td>
            <td >
                    ${fn:escapeXml(contact.galFullAddress)}
            </td>
        </tr>
        </c:if>
    </c:forEach>
</table>
<c:choose>
    <c:when test="${searchResult eq null and searchGalResult eq null}">
        <div class='InitialContactSearch'><fmt:message key="enterContactToSearchFor"/></div>
    </c:when>
    <c:when test="${(searchResult ne null and searchResult.size eq 0) or (searchGalResult ne null and searchGalResult.size eq 0)}">
        <div class='NoResults'><fmt:message key="noResultsFound"/></div>
    </c:when>
</c:choose>


