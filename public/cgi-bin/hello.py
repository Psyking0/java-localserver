#!/usr/bin/env python3
import os
import datetime

method = os.environ.get("REQUEST_METHOD", "GET")
path   = os.environ.get("PATH_INFO", "/")
query  = os.environ.get("QUERY_STRING", "")
now    = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

print("Content-Type: text/html; charset=utf-8")
print()
print(f"""<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>CGI Hello</title>
<style>
  body{{font-family:monospace;background:#0d1117;color:#e6edf3;padding:2rem}}
  table{{border-collapse:collapse;margin-top:1rem}}
  td,th{{border:1px solid #30363d;padding:6px 14px;text-align:left}}
  th{{background:#161b22}}
</style>
</head>
<body>
  <h2>&#127881; CGI Script Running</h2>
  <p>Time: {now}</p>
  <table>
    <tr><th>Variable</th><th>Value</th></tr>
    <tr><td>REQUEST_METHOD</td><td>{method}</td></tr>
    <tr><td>PATH_INFO</td><td>{path}</td></tr>
    <tr><td>QUERY_STRING</td><td>{query or "(empty)"}</td></tr>
    <tr><td>SERVER_SOFTWARE</td><td>{os.environ.get("SERVER_SOFTWARE","")}</td></tr>
    <tr><td>GATEWAY_INTERFACE</td><td>{os.environ.get("GATEWAY_INTERFACE","")}</td></tr>
  </table>
  <p style="margin-top:1rem"><a href="/" style="color:#58a6ff">← Back</a></p>
</body>
</html>
""")
