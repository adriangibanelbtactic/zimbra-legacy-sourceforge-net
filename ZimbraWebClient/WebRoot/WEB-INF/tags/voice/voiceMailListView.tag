<%@ tag body-content="empty" %>
<%@ attribute name="context" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.tag.SearchContext"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<app:handleError>
    <zm:getMailbox var="mailbox"/>
    <app:searchTitle var="title" context="${context}"/>
    <c:set var="phone">${zm:getPhoneFromVoiceQuery(context.query)}</c:set>
</app:handleError>
<app:view mailbox="${mailbox}" title="${title}" selected='voice' voice="true" folders="false" tags="false" searches="false" context="${context}" keys="true">
    <zm:currentResultUrl var="currentUrl" value="/h/search" context="${context}"/>
    <form name="zform" action="${currentUrl}" method="post">
        <table width=100% cellpadding="0" cellspacing="0">
            <tr>
                <td class='TbTop'>
                    <app:voiceMailListViewToolbar context="${context}" keys="true"/>
                </td>
            </tr>
            <tr>
                <td class='List'>
                        <table width=100% cellpadding=2 cellspacing=0>
                            <tr class='Header'>
                                <th class='CB' nowrap><input id="CHALL" onClick="checkAll(document.zform.voiceId,this)" type=checkbox name="allids"/>
                                <th class='Img' nowrap><app:img src="tag/FlagRed.gif" altkey="ALT_FLAGGED"/>
                                <th width=10% nowrap><fmt:message key="from"/>
                                <th width=10% nowrap><fmt:message key="message"/></th>
                                <th nowrap>
                                    <zm:newSortUrl var="durSortUrl" value="/h/search" context="${context}" sort="${(context.ss eq 'durDesc' or empty context.ss) ? 'durAsc' : 'durDesc'}"/>
	                                <a href="${durSortUrl}">
                                        <fmt:message key="duration"/>
                                    </a>
                                </th>
                                <th width=1% nowrap>
                                    <zm:newSortUrl var="dateSortUrl" value="/h/search" context="${context}" sort="${(context.ss eq 'dateDesc' or empty context.ss) ? 'dateAsc' : 'dateDesc'}"/>
	                                <a href="${dateSortUrl}">
	                                    <fmt:message key="received"/>
	                                </a>
	                            </th>
                            </tr>

                            <c:forEach items="${context.searchResult.hits}" var="hit" varStatus="status">
                            <tr>
                                <td class='CB' nowrap><input  id="C${status.index}" type=checkbox name="voiceId" value="${hit.voiceMailItemHit.id}"></td>
                                <td class='Img' nowrap><app:flagImage flagged="${hit.voiceMailItemHit.isFlagged}"/></td>
                            	<td nowrap>${hit.voiceMailItemHit.displayCaller}</td>
                                <c:choose>
                                    <c:when test="${!empty hit.voiceMailItemHit.soundUrl}">
                                        <c:url var="url" value="/h/voicemail">
                                            <c:param name="phone" value="${phone}"/>
                                            <c:param name="id" value="${hit.voiceMailItemHit.id}"/>
                                        </c:url>
                                        <td nowrap><a href="${url}"><app:img src="voicemail/PlayMessage.gif" altkey="ALT_FLAGGED"/><u><fmt:message key="listen"/></u></a></td>
                                    </c:when>
                                    <c:otherwise>
                                        <td nowrap>&nbsp;</td>
                                    </c:otherwise>
                                </c:choose>
                                <td nowrap>${fn:escapeXml(zm:displayDuration(pageContext, hit.voiceMailItemHit.duration))}</td>
                                <td nowrap>${fn:escapeXml(zm:displayMsgDate(pageContext, hit.voiceMailItemHit.date))}</td>
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
                    <app:voiceMailListViewToolbar context="${context}" keys="false"/>
                </td>
            </tr>
        </table>
        <input type="hidden" name="doVoiceMailListViewAction" value="1"/>
        <input type="hidden" name="phone" value="${phone}"/>
    </form>

    <SCRIPT TYPE="text/javascript">
        <!--
        var zclick = function(id) { var e2 = document.getElementById(id); if (e2) e2.click(); }
        var zdelete = function() { zclick("SOPDELETE"); }
        var zreply = function() { zclick("SOPREPLYBYEMAIL"); }
        var zforward = function() { zclick("SOPFORWARDBYEMAIL"); }
        var zheard = function() { zclick("SOPHEARD"); }
        var zunheard = function() { zclick("SOPUNHEARD"); }
        var zprint = function() { var e = document.getElementById("OPPRINT"); window.open(e.href, e.target); }
        var zcallManager = function() { var e = document.getElementById("OPCALLMANAGER"); window.location = e.href; }
        //-->
    </SCRIPT>

    <app:keyboard cache="voice.voiceMailListView" globals="true" mailbox="${mailbox}" tags="false" folders="false">
        <zm:bindKey message="voicemail.Delete" func="zdelete"/>
        <zm:bindKey message="voicemail.Reply" func="zreply"/>
        <zm:bindKey message="voicemail.Forward" func="zforward"/>
        <zm:bindKey message="voicemail.MarkHeard" func="zheard"/>
        <zm:bindKey message="voicemail.MarkUnheard" func="zunheard"/>
        <zm:bindKey message="voicemail.Print" func="zprint"/>
        <zm:bindKey message="voicemail.CallManager" func="zcallManager"/>
    </app:keyboard>
</app:view>
