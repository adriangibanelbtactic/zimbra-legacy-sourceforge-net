<%@ tag body-content="scriptless" %>
<%@ attribute name="title" rtexprvalue="true" required="false" %>
<%@ attribute name="mailbox" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZMailboxBean"%>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<head>
    <title>
        <c:if test="${empty title}"><fmt:message key="zimbraTitle"/></c:if>
        <c:if test="${!empty title}"><fmt:message key="zimbraTitle"/>: ${fn:escapeXml(title)}</c:if>
    </title>
    <c:if test="${not empty param.skin}">
        <c:set var="skin" value="${param.skin}" scope="session"/>
    </c:if>
    <c:set var="skin" value="${not empty sessionScope.skin ? sessionScope.skin : (not empty mailbox.prefs.skin ? mailbox.prefs.skin : 'sand')}"/>
    <c:set var="version" value="${initParam.zimbraCacheBusterVersion}"/>
    <!-- skin is ${skin} -->
    <style type="text/css">
       @import url( "<c:url value='/css/common,login,${skin}_imgs,skin.css?client=standard&skin=${skin}&v=${version}'/>" );
    </style>
    <link rel="ICON" type="image/gif" href="<c:url value='/img/loRes/logo/favicon.gif'/>">
    <link rel="SHORTCUT ICON" href="<c:url value='/img/loRes/logo/favicon.ico'/>">
    <jsp:doBody/>
    <script type="text/javascript">
        
        function checkAll(cb, allbox) {
            if (cb.length)
                for (i = 0; i < cb.length; i++)
                    cb[i].checked = allbox.checked;
            else
                cb.checked = allbox.checked;
        }
        
</script>
</head>
<c:import url="/h/${skin}"/>
<c:if test="${empty iconPath}">
  <c:set value="/images/" var="iconPath" scope="request"/>
</c:if>
