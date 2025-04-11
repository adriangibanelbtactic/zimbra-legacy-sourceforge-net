<%@ tag body-content="empty" dynamic-attributes="dynattrs" %>
<%@ attribute name="section" rtexprvalue="true" required="true" %>
<%@ attribute name="suffix" rtexprvalue="true" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>

<fmt:bundle basename="/keys/ZhKeys">
<tr>
    <td>
        <table class='shortcutList' cellspacing=0 cellpadding=0>
            <tr>
                <td class='shortcutListHeader' colspan=2>
                    <div class='PanelHead'>
                        <fmt:message var="desc" key="${section}.description"/>
                        <c:out value="${desc}"/>
                    </div>
                </td>
            </tr>
            <fmt:message var="keys" key="${section}.keys"/>
            <c:forEach var="msgkey" items="${fn:split(keys,',')}">
                <app:optShortcutKey msgkey="${msgkey}" suffix="${suffix}"/>
            </c:forEach>
        </table>
    </td>
</tr>
</fmt:bundle>
