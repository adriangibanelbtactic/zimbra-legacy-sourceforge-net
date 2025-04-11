<%@ page buffer="8kb" autoFlush="true" %>
<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8" %>
<%@ page session="true" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<fmt:setBundle basename="/msgs/ZmMsg" scope="request"/>

<c:catch var="loginException">
    <c:choose>
        <c:when test="${(not empty param.loginNewPassword or not empty param.loginConfirmNewPassword) and (param.loginNewPassword ne param.loginConfirmNewPassword)}">
            <c:set var="errorCode" value="account.CHANGE_PASSWORD"/>
            <fmt:message var="errorMessage" key="bothNewPasswordsMustMatch"/>
        </c:when>
        <c:when test="${param.loginOp eq 'relogin'}">
            <zm:logout/>
            <c:set var="errorCode" value="${param.loginErrorCode}"/>
            <fmt:message var="errorMessage" key="${errorCode}"/>
        </c:when>
        <c:when test="${param.loginOp eq 'logout'}">
            <zm:logout/>
        </c:when>
        <c:when test="${(param.loginOp eq 'login') && !(empty param.username) && !(empty param.password)}">
            <zm:login username="${param.username}" password="${param.password}" varRedirectUrl="postLoginUrl" varAuthResult="authResult"
                      newpassword="${param.loginNewPassword}" rememberme="${param.zrememberme == '1'}"
                      prefs="zimbraPrefSkin"
                      attrs="zimbraFeatureCalendarEnabled,zimbraFeatureContactsEnabled,zimbraFeatureIMEnabled,zimbraFeatureNotebookEnabled,zimbraFeatureOptionsEnabled,zimbraFeaturePortalEnabled,zimbraFeatureTasksEnabled,zimbraFeatureVoiceEnabled,zimbraFeatureBriefcasesEnabled"
                    />
            <c:choose>
                <c:when test="${not empty postLoginUrl}">
                    <c:redirect url="${postLoginUrl}"/>
                </c:when>
                <c:otherwise>
                    <jsp:forward page="/public/launchZCS.jsp"/>
                </c:otherwise>
            </c:choose>
    </c:when>
    <c:otherwise>
        <%-- try and use existing cookie if possible --%>
        <c:set var="authtoken" value="${not empty param.zauthtoken ? param.zauthtoken : cookie.ZM_AUTH_TOKEN.value}"/>
        <c:if test="${not empty authtoken}">
            <zm:login authtoken="${authtoken}" authtokenInUrl="${not empty param.zauthtoken}"
                      varRedirectUrl="postLoginUrl" varAuthResult="authResult"
                      rememberme="${param.zrememberme == '1'}"
                      prefs="zimbraPrefSkin"
                      attrs="zimbraFeatureCalendarEnabled,zimbraFeatureContactsEnabled,zimbraFeatureIMEnabled,zimbraFeatureNotebookEnabled,zimbraFeatureOptionsEnabled,zimbraFeaturePortalEnabled,zimbraFeatureTasksEnabled,zimbraFeatureVoiceEnabled,zimbraFeatureBriefcasesEnabled"
                    />
            <c:choose>
                <c:when test="${not empty postLoginUrl}">
                    <c:redirect url="${postLoginUrl}"/>
                </c:when>
                <c:otherwise>
                    <jsp:forward page="/public/launchZCS.jsp"/>
                </c:otherwise>
            </c:choose>
        </c:if>
    </c:otherwise>
    </c:choose>
</c:catch>

<c:if test="${loginException != null}">
    <zm:getException var="error" exception="${loginException}"/>
    <c:set var="errorCode" value="${error.code}"/>
    <fmt:message var="errorMessage" key="${errorCode}"/>
</c:if>

<c:set var="loginRedirectUrl" value="${zm:getPreLoginRedirectUrl(pageContext, '/public/unifiedLogin.jsp')}"/>
<c:if test="${not empty loginRedirectUrl}">
    <c:redirect url="${loginRedirectUrl}"/>
</c:if>

<c:url var="formActionUrl" value="/public/unifiedLogin.jsp">
    <c:forEach var="p" items="${paramValues}">
        <c:forEach var='value' items='${p.value}'>
            <c:if test="${(not fn:startsWith(p.key, 'login')) and (p.key ne 'username') and (p.key ne 'password')}">
                <c:param name="${p.key}" value='${value}'/>
            </c:if>
        </c:forEach>
    </c:forEach>
</c:url>

<html>

<head>
    <title><fmt:message key="zimbraTitle"/></title>
    <c:set var="skin" value="${empty cookie.ZM_SKIN ? 'sand' : cookie.ZM_SKIN.value}"/>
    <c:set var="version" value="${initParam.zimbraCacheBusterVersion}"/>
    <style type="text/css">
         @import url( "<c:url value='/css/common,login,zhtml,skin.css?skin=${skin}&v=${version}'/>" );
    </style>
    <link rel="ICON" type="image/gif" href="<c:url value='/img/loRes/logo/favicon.gif'/>">
    <link rel="SHORTCUT ICON" href="<c:url value='/img/loRes/logo/favicon.ico'/>">
</head>
<body onload="document.loginForm.username.focus();">
<table width=100% height=100%>
    <tr>
        <td align=center valign=middle>
            <div id="ZloginPanel">
                <table width=100%>
                    <tr>
                        <td>
                            <table width=100%>
                                <tr>
                                    <td align=center valign=middle>
                                        <a href="http://www.zimbra.com/" target="_new"><div style='cursor:pointer' class='ImgLoginBanner'></div></a>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div id='ZLoginAppName'><fmt:message key="splashScreenAppName"/></div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        <td>
                    </tr>
                    <tr>
                        <td id='ZloginBodyContainer'>
                            <c:if test="${errorCode != null}">
                                <!-- ${fn:escapeXml(error.stackStrace)} -->
                                <div id='ZloginErrorPanel'>
                                    <table width=100%>
                                        <tr>
                                            <td valign='top' width='40'>
                                                <img alt='<fmt:message key="ALT_ERROR"/>' src="<c:url value='/images/dwt/Critical_32.gif'/>"/>
                                            </td>
                                            <td class='errorText'>
                                                <c:out value="${errorMessage}"/> 
                                            </td>
                                        </tr>
                                    </table>
                                </div>
                            </c:if>

                            <div id='ZloginFormPanel'>
                                <form method='post' name="loginForm" action="${formActionUrl}">
                                    <input type="hidden" name="loginOp" value="login"/>
                                    <table width=100% cellpadding=4>
                                        <tr>
                                            <td class='zLoginLabelContainer'><label for="username"><fmt:message key="username"/>:</label></td>
                                            <td colspan=2 class='zLoginFieldContainer'>
                                                <input id="username" class='zLoginField' name='username' type='text' value='${fn:escapeXml(param.username)}' />
                                            </td>
                                        </tr>
                                        <tr>
                                            <td class='zLoginLabelContainer'><label for="password"><fmt:message key="password"/>:</label></td>
                                            <td colspan=2 class='zLoginFieldContainer'>
                                                <input id="password" class='zLoginField' name='password' type='password' value="${fn:escapeXml(param.password)}"/>
                                            </td>
                                        </tr>
                                        <c:if test="${errorCode eq 'account.CHANGE_PASSWORD' or !empty param.loginNewPassword }">
                                           <tr>
                                               <td class='zLoginLabelContainer'><label for="loginNewPassword"><fmt:message key="newPassword"/>:</label></td>
                                               <td colspan=2 class='zLoginFieldContainer'>
                                                   <input id="loginNewPassword" class='zLoginField' name='loginNewPassword' type='password' value="${fn:escapeXml(param.loginNewPassword)}"/>
                                               </td>
                                           </tr>
                                            <tr>
                                                <td class='zLoginLabelContainer'><label for="confirmNew"><fmt:message key="confirm"/>:</label></td>
                                                <td colspan=2 class='zLoginFieldContainer'>
                                                    <input id="confirmNew" class='zLoginField' name='loginConfirmNewPassword' type='password' value="${fn:escapeXml(param.loginConfirmNewPassword)}"/>
                                                </td>
                                            </tr>
                                        </c:if>
                                        <tr>
                                            <td class='zLoginLabelContainer'></td>
                                            <td>
                                                <table width=100%>
                                                    <tr>
                                                        <td><input id="remember" value="1" type=checkbox name='zrememberme' /></td>
                                                        <td class='zLoginCheckboxLabelContainer'><label for="remember"><fmt:message
                                                                key="rememberMe"/></label></td>
                                                    </tr>
                                                </table>
                                            </td>
                                            <td><input type=submit class='zLoginButton' 
                                                       value="<fmt:message key="login"/>"/></td>
                                        </tr>
                                    </table>
                                    <table width=100%>
                                        <tr>
                                            <td nowrap id='ZloginClientLevelContainer'>
                                                <fmt:message key="advancedClientLoginNotice">
                                                    <fmt:param><c:url value='/h/'/></fmt:param>
                                                </fmt:message>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td nowrap id='ZloginLicenseContainer'>
                                                <fmt:message key="splashScreenCopyright"/>
                                            </td>
                                        </tr>
                                    </table>
                                </form>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
        </td>
    </tr>
</table>
</body>
</html>