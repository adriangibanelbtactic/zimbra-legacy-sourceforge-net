<%@ tag body-content="empty" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<app:handleError>
<zm:requirePost/>
<zm:getMailbox var="mailbox"/>
<c:set var="ids" value="${fn:join(paramValues.id, ',')}"/>
<c:set var="folderId" value="${not empty paramValues.folderId[0] ? paramValues.folderId[0] : paramValues.folderId[1]}"/>
<c:set var="actionOp" value="${not empty paramValues.actionOp[0] ? paramValues.actionOp[0] :  paramValues.actionOp[1]}"/>
<c:choose>
    <c:when test="${zm:actionSet(param, 'actionCompose')}">
        <jsp:forward page="/h/compose"/>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionMarkTagRead')}">
        <c:set var="tagName" value="${zm:getTagName(pageContext, param.contextTagId)}"/>
        <zm:markTagRead id="${param.contextTagId}"/>
        <app:status>
            <fmt:message key="actionTagMarkRead">
                <fmt:param value="${tagName}"/>
            </fmt:message>
        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionMarkFolderRead')}">
        <c:set var="folderName" value="${zm:getFolderName(pageContext, param.contextFolderId)}"/>
        <zm:markFolderRead id="${param.contextFolderId}"/>
        <app:status>
            <fmt:message key="actionFolderMarkRead">
                <fmt:param value="${folderName}"/>
            </fmt:message>
        </app:status>
    </c:when>
        <c:when test="${zm:actionSet(param, 'actionMarkConvRead')}">
        <zm:markConversationRead read="true" var="result" id="${param.contextConvId}"/>
        <app:status>
            <fmt:message key="actionConvMarkedRead">
                <fmt:param value="${result.idCount}"/>
            </fmt:message>
        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionEmpty') and (param.contextFolderId eq mailbox.trash.id or param.contextFolderId eq mailbox.spam.id)}">
        <zm:emptyFolder id="${param.contextFolderId}"/>
        <app:status>
            <fmt:message key="folderEmptied">
                <fmt:param value="${zm:getFolderName(pageContext, param.contextFolderId)}"/>
            </fmt:message>
        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionLoadFeed')}">
        <zm:syncFolder id="${param.contextFolderId}"/>
        <app:status>
            <fmt:message key="feedLoaded">
                <fmt:param value="${zm:getFolderName(pageContext, param.contextFolderId)}"/>
            </fmt:message>
        </app:status>
    </c:when>
    <c:when test="${zm:actionSet(param, 'actionNewShare')}">
        <c:choose>
            <c:when test="${empty param.newFolderName}">
                <fmt:message key="actionNoNameSpecified"/>
            </c:when>
            <c:otherwise>
                <c:set var="newFlags" value="${param.newFolderView eq 'appointment' ? '#' : ''}"/>
                <zm:createMountpoint var="result" parentid="${param.newFolderParentId}"
                                     name="${param.newFolderName}"
                                     ownerby="BY_ID"
                                     owner="${param.newFolderGrantorId}"
                                     shareditemby="BY_ID"
                                     shareditem="${param.newFolderLinkId}"
                                     color="${param.newFolderColor}"
                                     flags="${newFlags}"
                                     view="${param.newFolderView}"/>
                <app:status>
                    <fmt:message key="shareAccepted"/>
                </app:status>
            </c:otherwise>
        </c:choose>
    </c:when>
    <c:when test="${empty ids}">
        <app:status style="Warning"><fmt:message key="actionNoMessageSelected"/></app:status>
    </c:when>
    <c:otherwise>
        <c:choose>
            <c:when test="${zm:actionSet(param, 'actionSpam')}">
                <zm:markMessageSpam  var="result" id="${ids}" spam="true"/>
                <app:status>
                    <fmt:message key="actionMessageMarkedSpam">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${zm:actionSet(param, 'actionNotSpam')}">
                <zm:markMessageSpam  var="result" id="${ids}" spam="false"/>
                <app:status>
                    <fmt:message key="actionMessageMarkedNotSpam">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${zm:actionSet(param, 'actionDelete')}">
                <zm:trashMessage  var="result" id="${ids}"/>
                <app:status>
                    <fmt:message key="actionMessageMovedTrash">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${zm:actionSet(param, 'actionHardDelete')}">
                <zm:deleteMessage  var="result" id="${ids}"/>
                <app:status>
                    <fmt:message key="actionMessageHardDeleted">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${actionOp eq 'unread' or actionOp eq 'read'}">
                <zm:markMessageRead var="result" id="${ids}" read="${actionOp eq 'read'}"/>
                <app:status>
                    <fmt:message key="${actionOp eq 'read' ? 'actionMessageMarkedRead' : 'actionMessageMarkedUnread'}">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
                <c:if test="${actionOp ne 'read'}">
                    <c:set var="idsMarkedUnread" value="${paramValues.id}" scope="request"/>
                </c:if>
            </c:when>
            <c:when test="${actionOp eq 'flag' or actionOp eq 'unflag'}">
                <zm:flagMessage var="result" id="${ids}" flag="${actionOp eq 'flag'}"/>
                <app:status>
                    <fmt:message key="${actionOp eq 'flag' ? 'actionMessageFlag' : 'actionMessageUnflag'}">
                        <fmt:param value="${result.idCount}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${fn:startsWith(actionOp, 't:') or fn:startsWith(actionOp, 'u:')}">
                <c:set var="tag" value="${fn:startsWith(actionOp, 't')}"/>
                <c:set var="tagid" value="${fn:substring(actionOp, 2, -1)}"/>
                <zm:tagMessage tagid="${tagid}"var="result" id="${ids}" tag="${tag}"/>
                <app:status>
                    <fmt:message key="${tag ? 'actionMessageTag' : 'actionMessageUntag'}">
                        <fmt:param value="${result.idCount}"/>
                        <fmt:param value="${zm:getTagName(pageContext, tagid)}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${fn:startsWith(folderId, 'm:')}">
                <c:set var="folderid" value="${fn:substring(folderId, 2, -1)}"/>
                <zm:moveMessage folderid="${folderid}"var="result" id="${ids}"/>
                <app:status>
                    <fmt:message key="actionMessageMoved">
                        <fmt:param value="${result.idCount}"/>
                        <fmt:param value="${zm:getFolderName(pageContext, folderid)}"/>
                    </fmt:message>
                </app:status>
            </c:when>
            <c:when test="${zm:actionSet(param, 'actionMove')}">
                <app:status style="Warning"><fmt:message key="actionNoFolderSelected"/></app:status>
            </c:when>
            <c:otherwise>
                <app:status style="Warning"><fmt:message key="actionNoActionSelected"/></app:status>
            </c:otherwise>
        </c:choose>
    </c:otherwise>
</c:choose>
</app:handleError>