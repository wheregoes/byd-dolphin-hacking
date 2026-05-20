#!/usr/bin/env python3
"""Verify NON-DEFAULT flags and search for filesystem flag IDs. Uses browser ws to create target."""

import websocket
import json
import time
import urllib.request

# Connect to browser-level websocket
browser_ws_url = "ws://localhost:9222/devtools/browser"
ws = websocket.create_connection(browser_ws_url, suppress_origin=True)
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

# Create a new target with chrome://flags
print("Creating new target for chrome://flags...")
create_id = send_cmd("Target.createTarget", {"url": "chrome://flags"})
create_result = recv_result(create_id, 10)
print(f"Create result: {json.dumps(create_result, indent=2)}")

if not create_result or "result" not in create_result:
    print("Failed to create target. Exiting.")
    ws.close()
    exit(1)

target_id = create_result["result"]["targetId"]
print(f"Target ID: {target_id}")

ws.close()

# Wait for the page to load
time.sleep(5)

# Now connect to the page target
pages = json.loads(urllib.request.urlopen("http://localhost:9222/json").read())
print(f"\nAvailable targets: {len(pages)}")
for p in pages:
    print(f"  {p['type']}: {p['title']} - {p['url']}")

# Find our target
page = None
for p in pages:
    if "flags" in p.get("url", ""):
        page = p
        break
if not page:
    page = pages[0] if pages else None

if not page:
    print("No page target found!")
    exit(1)

ws_url = page["webSocketDebuggerUrl"]
print(f"\nConnecting to: {ws_url}")

ws = websocket.create_connection(ws_url, suppress_origin=True)
msg_id = 0

def evaluate(expression, timeout=30):
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

send_cmd("Runtime.enable")
recv_result(msg_id, 3)

# Check page
loc = evaluate("window.location.href")
title = evaluate("document.title")
print(f"Location: {loc}, Title: {title}")

if loc != "chrome://flags/":
    print("Navigating to chrome://flags...")
    send_cmd("Page.enable")
    recv_result(msg_id, 3)
    nav_id = send_cmd("Page.navigate", {"url": "chrome://flags"})
    recv_result(nav_id, 10)
    time.sleep(5)
    print(f"Now at: {evaluate('window.location.href')}")

# 1. Verify NON-DEFAULT flags
print("\n=== NON-DEFAULT FLAG VERIFICATION ===")
non_default_ids = [
    "disable-accelerated-2d-canvas",
    "ui-disable-partial-swap",
    "disable-webrtc-hw-decoding",
    "disable-webrtc-hw-encoding",
    "disable-javascript-harmony-shipping",
    "disable-accelerated-video-decode",
    "disable-accelerated-video-encode",
    "disable-threaded-scrolling",
]

result = evaluate(f"""
JSON.stringify(
    {json.dumps(non_default_ids)}.map(id => {{
        const nameEl = document.getElementById(id + '_name');
        if (!nameEl) return {{ id, found: false }};
        const exp = nameEl.closest('.experiment');
        const selects = Array.from(exp.querySelectorAll('select'));
        return {{
            id,
            found: true,
            className: exp.className,
            selectCount: selects.length,
            selectDetails: selects.map(s => ({{
                idx: s.selectedIndex,
                val: s.value,
                optCount: s.options.length,
                options: Array.from(s.options).map((o, i) => ({{
                    text: o.textContent.trim(),
                    value: o.value,
                    selected: o.selected,
                    index: i
                }}))
            }})),
            enableLinks: Array.from(exp.querySelectorAll('a')).map(a => a.textContent.trim()),
            buttons: Array.from(exp.querySelectorAll('button')).map(b => b.textContent.trim()),
        }};
    }})
)
""", timeout=30)

if result and isinstance(result, str):
    details = json.loads(result)
    for d in details:
        print(f"\n  #{d['id']}: found={d.get('found')}, class='{d.get('className','')}'")
        if d.get("selectCount", 0) > 0:
            for s in d["selectDetails"]:
                print(f"    Select: idx={s['idx']}, val='{s['val']}', opts={s['optCount']}")
                for o in s["options"]:
                    marker = " <-- SELECTED" if o["selected"] else ""
                    print(f"      [{o['index']}] '{o['text']}' (value='{o['value']}'){marker}")
        else:
            print(f"    No selects. Links: {d.get('enableLinks')}, Buttons: {d.get('buttons')}")
else:
    print(f"ERROR: {result}")

# 2. Check for filesystem-related IDs
print("\n\n=== FILESYSTEM FLAG ID SEARCH ===")
fs_result = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('[id]'))
        .map(e => e.id)
        .filter(id => /file|fs|directory|handle|writable|opfs|persistent|origin.private/i.test(id) && !id.endsWith('_name'))
)
""")
print(f"Filesystem-related IDs: {fs_result}")

# 3. Search descriptions for filesystem terms
print("\n=== FLAGS WITH FILESYSTEM TERMS IN TEXT ===")
fs_desc_result = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('.experiment')).filter(exp => {
        const text = exp.innerText.toLowerCase();
        return text.includes('file system') || text.includes('filesystem') ||
               text.includes('file access') || text.includes('directory') ||
               text.includes('writable') || text.includes('opfs') ||
               text.includes('origin private') || text.includes('showsavefile') ||
               text.includes('showdirectory') || text.includes('native file') ||
               text.includes('file api');
    }).map(exp => ({
        id: exp.querySelector('.permalink')?.textContent?.trim() || '',
        name: exp.querySelector('h3')?.textContent?.trim() || '',
        text: exp.innerText.substring(0, 300)
    }))
)
""")
if fs_desc_result and isinstance(fs_desc_result, str):
    fs_flags = json.loads(fs_desc_result)
    print(f"Found {len(fs_flags)} flags with filesystem terms")
    for f in fs_flags:
        print(f"  {f['id']}: {f['text'][:200]}")
else:
    print(f"Result: {fs_desc_result}")

ws.close()
