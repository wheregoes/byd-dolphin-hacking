#!/usr/bin/env python3
"""Verify NON-DEFAULT flags and check filesystem flag IDs."""

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

# 1. Verify NON-DEFAULT flags
print("=== NON-DEFAULT FLAG VERIFICATION ===")
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
        const enableLinks = Array.from(exp.querySelectorAll('a')).map(a => a.textContent.trim());
        const buttons = Array.from(exp.querySelectorAll('button')).map(b => b.textContent.trim());
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
            enableLinks,
            buttons,
            rawHTML: exp.innerHTML.substring(0, 1000)
        }};
    }})
)
""", timeout=30)

if result and isinstance(result, str):
    details = json.loads(result)
    for d in details:
        print(f"\n  #{d['id']}: found={d.get('found')}")
        if d.get("selectCount", 0) > 0:
            for s in d["selectDetails"]:
                print(f"    Select: idx={s['idx']}, val={s['val']}, opts={len(s['options'])}")
                for o in s["options"]:
                    marker = " <-- SELECTED" if o["selected"] else ""
                    print(f"      [{o['index']}] '{o['text']}' (value='{o['value']}'){marker}")
        else:
            print(f"    No selects! Links: {d.get('enableLinks')}, Buttons: {d.get('buttons')}")
            print(f"    Class: {d.get('className')}")
            # Show raw HTML for these
            print(f"    HTML: {d.get('rawHTML', '')[:500]}")

# 2. Check for filesystem-related IDs
print("\n\n=== FILESYSTEM FLAG ID SEARCH ===")
fs_result = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('[id]'))
        .map(e => e.id)
        .filter(id => /file|fs|directory|handle|writable|opfs|persistent|origin.private/i.test(id) && !id.endsWith('_name'))
)
""")
print(f"Filesystem IDs: {fs_result}")

# 3. Also check descriptions for filesystem terms
print("\n=== FLAGS WITH FILESYSTEM IN DESCRIPTION ===")
fs_desc_result = evaluate("""
JSON.stringify(
    Array.from(document.querySelectorAll('.experiment')).filter(exp => {
        const text = exp.innerText.toLowerCase();
        return text.includes('file system') || text.includes('filesystem') ||
               text.includes('file access') || text.includes('directory') ||
               text.includes('writable') || text.includes('opfs') ||
               text.includes('origin private') || text.includes('showsavefile') ||
               text.includes('showdirectory') || text.includes('native file');
    }).map(exp => ({
        id: exp.querySelector('.permalink')?.textContent?.trim() || '',
        name: exp.querySelector('h3')?.textContent?.trim() || '',
        desc: exp.querySelector('p')?.textContent?.trim()?.substring(0, 300) || '',
        text: exp.innerText.substring(0, 200)
    }))
)
""")
if fs_desc_result and isinstance(fs_desc_result, str):
    fs_flags = json.loads(fs_desc_result)
    print(f"Found {len(fs_flags)} flags with filesystem terms in description")
    for f in fs_flags:
        print(f"  {f['id']}: {f['desc'][:200]}")

ws.close()
