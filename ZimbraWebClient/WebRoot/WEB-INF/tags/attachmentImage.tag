<%@ tag body-content="empty" %>
<%@ attribute name="attachment" rtexprvalue="true" required="true" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<c:choose><c:when test="${attachment}"><app:img altkey="ALT_ATTACHMENT" src="common/Attachment.gif" /></c:when><c:otherwise>&nbsp;</c:otherwise></c:choose>
