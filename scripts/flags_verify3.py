#!/usr/bin/env python3
"""Verify NON-DEFAULT flags and filesystem IDs. Navigate existing tab to flags."""

import websocket
import json
import time
import urllib.request

pages = json.loads(urllib.request.urlopen("http://localhost:9222/json").read())
# Use the empty-title tab (id=2) which might be the new tab we opened
target = None
for p in pages:
    if p.get("url") == "chrome://flags/" or p.get("title") == "Experiments":
        target = p
        break
if not target:
    # Use empty tab or first page tab
    for p in pages:
        if p["type"] == "page" and (not p.get("title") or p.get("title") == ""):
            target = p
            break
if not target:
    target = pages[0]

print(f"Using target: {target['id']} - {target['title']} - {target['url']}")
ws_url = target["webSocketDebuggerUrl"]
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

# Enable and navigate
send_cmd("Page.enable")
recv_result(msg_id, 3)
send_cmd("Runtime.enable")
recv_result(msg_id, 3)

loc = evaluate("window.location.href")
print(f"Current location: {loc}")

if loc != "chrome://flags/" and loc != "chrome://flags":
    print("Navigating to chrome://flags...")
    nav_id = send_cmd("Page.navigate", {"url": "chrome://flags"})
    nav_result = recv_result(nav_id, 10)
    print(f"Nav result: {nav_result}")
    time.sleep(5)
    print(f"Now at: {evaluate('window.location.href')}")
    print(f"Title: {evaluate('document.title')}")

# Wait for page rendering
time.sleep(2)

# Verify experiments exist
exp_count = evaluate("document.querySelectorAll('.experiment').length")
print(f"Experiments on page: {exp_count}")

if not exp_count or exp_count == 0:
    print("No experiments found, page may not have loaded. Waiting more...")
    time.sleep(5)
    exp_count = evaluate("document.querySelectorAll('.experiment').length")
    print(f"Experiments after wait: {exp_count}")

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
            print(f"    No selects found in this experiment block")
else:
    print(f"ERROR: {result}")

# 2. Filesystem ID search
print("\n\n=== FILESYSTEM FLAG ID SEARCH ===")
fs_result = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('[id]'))
        .map(e => e.id)
        .filter(id => /file|fs|directory|handle|writable|opfs|persistent|origin.private/i.test(id) && !id.endsWith('_name'))
)
""")
print(f"Filesystem-related IDs: {fs_result}")

# 3. Text search in descriptions
print("\n=== FILESYSTEM TEXT SEARCH IN DESCRIPTIONS ===")
fs_desc = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('.experiment')).filter(exp => {
        const text = exp.innerText.toLowerCase();
        return text.includes('file system') || text.includes('filesystem') ||
               text.includes('file access') || text.includes('writable') ||
               text.includes('opfs') || text.includes('origin private') ||
               text.includes('native file') || text.includes('file api') ||
               text.includes('showsavefile') || text.includes('showdirectory');
    }).map(exp => ({
        id: exp.querySelector('.permalink')?.textContent?.trim() || '',
        text: exp.innerText.substring(0, 300)
    }))
)
""")
if fs_desc and isinstance(fs_desc, str):
    fs_flags = json.loads(fs_desc)
    print(f"Found {len(fs_flags)} flags with filesystem terms")
    for f in fs_flags:
        print(f"  {f['id']}: {f['text'][:200]}")
else:
    print(f"Result: {fs_desc}")

# 4. Also check for the "broker-file" flag text more carefully
print("\n=== BROKER/FILE/DISK FLAGS ===")
bf = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('.experiment')).filter(exp => {
        const text = exp.innerText.toLowerCase();
        return text.includes('broker') || text.includes('disk') ||
               (text.includes('file') && !text.includes('profile'));
    }).map(exp => ({
        id: exp.querySelector('.permalink')?.textContent?.trim() || '',
        text: exp.innerText.substring(0, 300)
    }))
)
""")
if bf and isinstance(bf, str):
    bf_flags = json.loads(bf)
    print(f"Found {len(bf_flags)} flags")
    for f in bf_flags:
        print(f"  {f['id']}: {f['text'][:200]}")

ws.close()
