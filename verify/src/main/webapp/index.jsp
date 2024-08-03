<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>cluster</title>
</head>
<body>
<%
  Object count = session.getAttribute("count");
  if (count == null) {
    count = Integer.valueOf(1); 
  } else {
    count = Integer.valueOf(Integer.parseInt(count.toString()) + 1); 
  }
  session.setAttribute("count", count);
  out.println(String.format("%s %s %s", System.getProperty("jboss.node.name"), session.getId(), session.getAttribute("count")));
%>
</body>
</html>