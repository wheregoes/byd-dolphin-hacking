#!/usr/bin/env python3
"""Probe chrome://flags DOM structure on BYD Chromium 113."""

import websocket
import json
import time
import urllib.request

pages = json.loads(urllib.request.urlopen("http://localhost:9222/json").read())
ws_url = pages[0]["webSocketDebuggerUrl"]
ws = websocket.create_connection(ws_url, suppress_origin=True)
msg_id = 0

def send_cmd(method, params=None):
    global msg_id
    msg_id += 1
    cmd = {"id": msg_id, "method": method}
    if params:
        cmd["params"] = params
    ws.send(json.dumps(cmd))
    return msg_id

def recv_result(expected_id, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        ws.settimeout(timeout)
        try:
            raw = ws.recv()
            msg = json.loads(raw)
            if "id" in msg and msg["id"] == expected_id:
                return msg
        except websocket.WebSocketTimeoutException:
            break
    return None

def evaluate(expression, timeout=15):
    eid = send_cmd("Runtime.evaluate", {
        "expression": expression,
        "returnByValue": True,
        "awaitPromise": False
    })
    result = recv_result(eid, timeout)
    if result and "result" in result:
        r = result["result"].get("result", {})
        return r.get("value", r)
    return result

# Enable domains
send_cmd("Page.enable")
recv_result(msg_id, 3)
send_cmd("Runtime.enable")
recv_result(msg_id, 3)

# Check current page
print("Location:", evaluate("window.location.href"))
print("Title:", evaluate("document.title"))

# Probe DOM structure
print("\n--- DOM structure probes ---")

# Body innerHTML length
print("Body innerHTML length:", evaluate("document.body.innerHTML.length"))

# What top-level elements exist
print("\nBody children tags:", evaluate("""
    Array.from(document.body.children).map(e => e.tagName + '#' + e.id + '.' + e.className).join(', ')
"""))

# Check for experiments container
print("\nExperiment containers:", evaluate("""
    JSON.stringify({
        byClass_experiment: document.querySelectorAll('.experiment').length,
        byClass_experiment_name: document.querySelectorAll('.experiment-name').length,
        byId_experiments: document.getElementById('experiments') ? 'found' : 'not_found',
        byId_tab_content: document.querySelectorAll('[id^="tab-content"]').length,
        byClass_permalink: document.querySelectorAll('.permalink').length,
        byTag_select: document.querySelectorAll('select').length,
        byClass_experiment_default: document.querySelectorAll('.experiment-default').length,
    })
"""))

# Get first 2000 chars of body innerHTML to understand structure
print("\nBody innerHTML (first 2000 chars):")
print(evaluate("document.body.innerHTML.substring(0, 2000)"))

# Check all IDs on page
print("\nAll element IDs (first 100):")
print(evaluate("""
    JSON.stringify(Array.from(document.querySelectorAll('[id]')).slice(0, 100).map(e => e.id))
"""))

ws.close()
