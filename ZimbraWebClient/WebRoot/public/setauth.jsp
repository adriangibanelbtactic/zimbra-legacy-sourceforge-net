<%
  String authToken = request.getParameter("authToken");
  if (authToken != null) {
    Cookie c = new Cookie("ZM_AUTH_TOKEN", request.getParameter("authToken"));
    c.setMaxAge(30*24*60*60);  // 30 days
    c.setPath("/");
    response.addCookie(c);
  }
%>
<html><body></body></html>
