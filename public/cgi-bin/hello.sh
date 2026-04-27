#!/bin/bash
# hello.sh — Shell CGI script (second CGI handler for audit)

DATE=$(date "+%Y-%m-%d %H:%M:%S")
HOSTNAME=$(hostname)
UPTIME=$(uptime -p 2>/dev/null || uptime)

echo "Content-Type: text/html; charset=utf-8"
echo ""
cat << HTML
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Shell CGI</title>
<style>
  body{font-family:monospace;background:#0d1117;color:#e6edf3;padding:2rem}
  table{border-collapse:collapse;margin-top:1rem}
  td,th{border:1px solid #30363d;padding:6px 14px;text-align:left}
  th{background:#161b22}
  .tag{background:#1f6feb;color:#fff;padding:2px 8px;border-radius:10px;font-size:0.8em}
</style>
</head>
<body>
  <h2>&#128032; Shell CGI Script <span class="tag">bash</span></h2>
  <p>Time: ${DATE}</p>
  <table>
    <tr><th>Variable</th><th>Value</th></tr>
    <tr><td>REQUEST_METHOD</td><td>${REQUEST_METHOD}</td></tr>
    <tr><td>PATH_INFO</td><td>${PATH_INFO}</td></tr>
    <tr><td>QUERY_STRING</td><td>${QUERY_STRING:-"(empty)"}</td></tr>
    <tr><td>SERVER_SOFTWARE</td><td>${SERVER_SOFTWARE}</td></tr>
    <tr><td>HOSTNAME</td><td>${HOSTNAME}</td></tr>
    <tr><td>UPTIME</td><td>${UPTIME}</td></tr>
    <tr><td>GATEWAY_INTERFACE</td><td>${GATEWAY_INTERFACE}</td></tr>
  </table>
  <p style="margin-top:1rem"><a href="/" style="color:#58a6ff">&#8592; Back</a></p>
</body>
</html>
HTML
