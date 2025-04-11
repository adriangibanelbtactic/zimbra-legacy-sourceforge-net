<%@ tag body-content="empty" %>
<%@ attribute name="context" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.tag.SearchContext"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<app:handleError>
    <app:searchTitle var="title" context="${context}"/>
    <fmt:message key="noSubject" var="noSubject"/>
    <fmt:message var="unknownRecipient" key="unknownRecipient"/>
    <zm:currentResultUrl var="currentUrl" value="/h/search" context="${context}"/>
    <zm:getMailbox var="mailbox"/>
    <c:set var="useTo" value="${context.folder.isSent or context.folder.isDrafts}"/>
    <c:if test="${false and mailbox.prefs.readingPaneEnabled}">
        <zm:getMessage var="msg" id="${not empty param.id ? param.id : context.currentItem.id}" markread="true" neuterimages="${empty param.xim}"/>
        <zm:computeNextPrevItem var="cursor" searchResult="${context.searchResult}" index="${context.currentItemIndex}"/>
        <c:set var="ads" value='${msg.subject} ${msg.fragment}'/>
    </c:if>
</app:handleError>

<app:view title="${title}" context="${context}" selected='mail' folders="true" tags="true" searches="true" keys="true">

<form action="${currentUrl}" method="post" name="zform">
    <table width=100% cellpadding="0" cellspacing="0">
        <tr>
            <td class='TbTop'>
                <app:messageListViewToolbar context="${context}" keys="true"/>
            </td>
        </tr>
        <tr>
            <td class='List'>
                    <table width=100% cellpadding=2 cellspacing=0>
                        <tr>
                            <th class='CB' nowrap><input onClick="checkAll(document.zform.id,this)" type=checkbox name="allids"/>
                            <th class='Img' nowrap><app:img src="tag/FlagRed.gif" altkey="ALT_FLAGGED"/>
                             <c:if test="${mailbox.features.tagging}">
                            <th class='Img' nowrap><app:img src="tag/MiniTagOrange.gif" altkey="ALT_TAG_TAG"/>
                            </c:if>
                            <th class='MsgStatusImg' nowrap>
                            <th width=10%>
                                <zm:newSortUrl var="fromSortUrl" value="/h/search" context="${context}" sort="${context.ss eq 'nameAsc' ? 'nameDesc' : 'nameAsc'}"/>
                            <a href="${fromSortUrl}">
                                <fmt:message key="${useTo ? 'to' : 'from'}"/>
                            </a>
                            <th width=1% nowrap><app:img src="common/Attachment.gif" altkey="ALT_ATTACHMENT"/>
                            <th nowrap>
                                <zm:newSortUrl var="subjectSortUrl" value="/h/search" context="${context}" sort="${context.ss eq 'subjAsc' ? 'subjDesc' : 'subjAsc'}"/>
                            <a href="${subjectSortUrl}">
                                <fmt:message key="subject"/>
                            </a>

                                    <c:if test="${!context.isFolderSearch}">
                            <th width=1% nowrap><fmt:message key="folder"/>
                            </c:if>
                            <th width=1% nowrap><fmt:message key="size"/>
                            <th width=1% nowrap>
                                <zm:newSortUrl var="dateSortUrl" value="/h/search" context="${context}" sort="${(context.ss eq 'dateDesc' or empty context.ss)? 'dateAsc' : 'dateDesc'}"/>
                            <a href="${dateSortUrl}">
                                <fmt:message key="received"/>
                            </a>
                        </tr>
                        <c:set value="${context.searchResult.hits[0].id}" var="cid"/>
                        <c:forEach items="${context.searchResult.hits}" var="hit" varStatus="status">
                            <c:choose>
                                <c:when test="${hit.messageHit.isDraft}">
                                    <zm:currentResultUrl index="${status.index}" var="currentItemUrl" value="/h/search" context="${context}" action="compose" id="${hit.messageHit.id}"/>
                                </c:when>
                                <c:otherwise>
                                    <zm:currentResultUrl index="${status.index}" var="currentItemUrl" value="/h/search" action="view" context="${context}" id="${hit.messageHit.id}"/>
                                </c:otherwise>
                            </c:choose>

                            <tr class='ZhRow ${hit.messageHit.isUnread ? ' Unread':''}${hit.messageHit.id == context.currentItem.id ? ' RowSelected' : ''}'>
                                <td class='CB' nowrap><input type=checkbox name="id" value="${hit.messageHit.id}"></td>
                                <td class='Img'><app:flagImage flagged="${hit.messageHit.isFlagged}"/></td>
                                 <c:if test="${mailbox.features.tagging}">
                                     <td class='Img'><app:miniTagImage ids="${hit.messageHit.tagIds}"/></td>
                                </c:if>
                                <td class='MsgStatusImg' align=center><app:img src="${hit.messageHit.statusImage}" altkey='${hit.messageHit.statusImageAltKey}'/></td>
                                <td><%-- allow wrap --%> <a href="${currentItemUrl}">${fn:escapeXml(empty hit.messageHit.displaySender ? unknownRecipient :  hit.messageHit.displaySender)}</a></td>
                                <td class='Img'><app:attachmentImage attachment="${hit.messageHit.hasAttachment}"/></td>
                                <td > <%-- allow this col to wrap --%>

                                    <a href="${currentItemUrl}" <c:if test="${hit.id == context.currentItem.id}">accesskey='o'</c:if>>
                                        <c:set var="subj" value="${empty hit.messageHit.subject ? noSubject : hit.messageHit.subject}"/>
                                        <c:out value="${subj}"/>
                                        <c:if test="${mailbox.prefs.showFragments and not empty hit.messageHit.fragment and fn:length(subj) lt 90}">
                                            <span class='Fragment'> - <c:out value="${zm:truncate(hit.messageHit.fragment,100-fn:length(subj),true)}"/></span>
                                        </c:if>
                                    </a>
                                    <c:if test="${hit.id == context.currentItem.id}">
                                        <zm:computeNextPrevItem var="cursor" searchResult="${context.searchResult}" index="${context.currentItemIndex}"/>
                                        <c:if test="${cursor.hasPrev}">
                                            <zm:prevItemUrl var="prevItemUrl" value="/h/search" cursor="${cursor}" context="${context}" usecache="true"/>
                                            <a href="${prevItemUrl}" accesskey='k'></a>
                                        </c:if>
                                        <c:if test="${cursor.hasNext}">
                                            <zm:nextItemUrl var="nextItemUrl" value="/h/search" cursor="${cursor}" context="${context}" usecache="true"/>
                                            <a href="${nextItemUrl}" accesskey='j'></a>
                                        </c:if>
                                    </c:if>
                                </td>
                                <c:if test="${!context.isFolderSearch}">
                                    <td nowrap>${fn:escapeXml(zm:getFolderName(pageContext, hit.messageHit.folderId))}</td>
                                </c:if>
                                <td nowrap>${fn:escapeXml(zm:displaySize(hit.messageHit.size))}
                                <td nowrap>${fn:escapeXml(zm:displayMsgDate(pageContext, hit.messageHit.date))}
                            </tr>
                        </c:forEach>
                    </table>
                    <c:if test="${context.searchResult.size == 0}">
                        <div class='NoResults'><fmt:message key="noResultsFound"/></div>
                    </c:if>
            </td>
        </tr>
        <tr>
            <td class='TbBottom'>
                <app:messageListViewToolbar context="${context}" keys="false"/>
            </td>
        </tr>
        <c:if test="${false and mailbox.prefs.readingPaneEnabled and not empty msg}">
            <tr>
                   <td class='ZhAppContent'>
                        <c:set var="extImageUrl" value=""/>
                        <c:if test="${empty param.xim}">
                            <zm:currentResultUrl var="extImageUrl" value="search" action="view" context="${context}" xim="1"/>
                        </c:if>
                        <zm:currentResultUrl var="composeUrl" value="search" context="${context}"
                                             action="compose" paction="view" id="${msg.id}"/>
                       <zm:currentResultUrl var="newWindowUrl" value="message" context="${context}" id="${msg.id}"/>
                       <app:displayMessage mailbox="${mailbox}" message="${msg}"externalImageUrl="${extImageUrl}" showconvlink="true" composeUrl="${composeUrl}" newWindowUrl="${newWindowUrl}"/>
                </td>
            </tr>
        </c:if>
    </table>
    <input type="hidden" name="doMessageAction" value="1"/>
  </form>
</app:view>
