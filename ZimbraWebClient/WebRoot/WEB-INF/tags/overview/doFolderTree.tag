<%@ tag body-content="empty" %>
<%@ attribute name="parentid" rtexprvalue="true" required="false" type="java.lang.String" %>
<%@ attribute name="skiproot" rtexprvalue="true" required="true" %>
<%@ attribute name="skipsystem" rtexprvalue="true" required="true" %>
<%@ attribute name="skiptopsearch" rtexprvalue="true" required="false" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<zm:forEachFolder var="folder" skiproot="${skiproot}" parentid="${parentid}" skipsystem="${skipsystem}" expanded="${sessionScope.expanded}" skiptopsearch="${skiptopsearch}">
    <c:if test="${!folder.isSystemFolder and (folder.isNullView or folder.isMessageView or folder.isConversationView)}">
        <c:if test="${!folder.isSearchFolder}">
            <app:overviewFolder folder="${folder}"/>
        </c:if>
        <c:if test="${folder.isSearchFolder and folder.depth gt 0}">
            <app:overviewSearchFolder folder="${folder}"/>
        </c:if>
    </c:if>
</zm:forEachFolder>
