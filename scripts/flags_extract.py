#!/usr/bin/env python3
"""Extract all chrome://flags from BYD car's Chromium 113 via CDP."""

import websocket
import json
import time
import urllib.request
import sys

# Get CDP target
pages = json.loads(urllib.request.urlopen("http://localhost:9222/json").read())
ws_url = pages[0]["webSocketDebuggerUrl"]
print(f"Target: {pages[0]['type']} - {pages[0]['title']}")
print(f"WS URL: {ws_url}")

# Connect with origin suppression
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
    """Wait for result matching expected_id, collecting events."""
    deadline = time.time() + timeout
    events = []
    while time.time() < deadline:
        ws.settimeout(timeout)
        try:
            raw = ws.recv()
            msg = json.loads(raw)
            if "id" in msg and msg["id"] == expected_id:
                return msg, events
            if "method" in msg:
                events.append(msg)
        except websocket.WebSocketTimeoutException:
            break
    return None, events

def evaluate(expression, timeout=15):
    """Run JS via Runtime.evaluate, return result."""
    eid = send_cmd("Runtime.evaluate", {
        "expression": expression,
        "returnByValue": True,
        "awaitPromise": False
    })
    result, _ = recv_result(eid, timeout)
    if result and "result" in result:
        return result["result"].get("result", {})
    return result

def wait_for_event(event_name, timeout=10):
    """Wait for a specific CDP event."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        ws.settimeout(timeout)
        try:
            raw = ws.recv()
            msg = json.loads(raw)
            if msg.get("method") == event_name:
                return msg
        except websocket.WebSocketTimeoutException:
            break
    return None

# Enable domains
print("\n--- Enabling domains ---")
send_cmd("Page.enable")
recv_result(msg_id, 3)
send_cmd("Runtime.enable")
recv_result(msg_id, 3)

# Navigate to chrome://flags
print("\n--- Navigating to chrome://flags ---")
nav_id = send_cmd("Page.navigate", {"url": "chrome://flags"})
nav_result, nav_events = recv_result(nav_id, 10)
print(f"Navigate result: {json.dumps(nav_result, indent=2)}")

# Wait for load
print("\n--- Waiting for page load ---")
load_event = wait_for_event("Page.loadEventFired", timeout=10)
if load_event:
    print("Page.loadEventFired received")
else:
    print("No loadEventFired, waiting 5s fallback...")
    time.sleep(5)

# Extra settle time for JS rendering
time.sleep(3)

# Probe: check what loaded
print("\n--- Probing page state ---")
title = evaluate("document.title")
print(f"Title: {title}")

href = evaluate("window.location.href")
print(f"Location: {href}")

# Check for shadow DOM (Polymer flags page)
shadow_probe = evaluate("document.querySelector('flags-app')?.shadowRoot ? 'shadow_ok' : 'no_shadow'")
print(f"Shadow DOM probe: {shadow_probe}")

# Check loadTimeData availability
ltd_probe = evaluate("typeof loadTimeData !== 'undefined' ? Object.keys(loadTimeData.data_).length : 'no_loadTimeData'")
print(f"loadTimeData probe: {ltd_probe}")

# Try getting loadTimeData keys
ltd_keys = evaluate("typeof loadTimeData !== 'undefined' ? JSON.stringify(Object.keys(loadTimeData.data_).slice(0, 50)) : 'N/A'")
print(f"loadTimeData keys (first 50): {ltd_keys}")

# Check chrome://about to see available WebUIs
print("\n--- Checking available internal pages ---")
about_probe = evaluate("""
(async () => {
    try {
        const r = await fetch('chrome://about/');
        return await r.text();
    } catch(e) {
        return 'fetch_failed: ' + e.message;
    }
})()
""")
# This likely won't work, but worth a shot

print(f"About probe: {str(about_probe)[:500]}")

ws.close()
print("\n--- Phase 1 probe complete ---")
