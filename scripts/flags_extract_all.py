#!/usr/bin/env python3
"""Extract ALL chrome://flags from BYD Chromium 113 via CDP - chunked extraction."""

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

# Verify we're still on flags
print("Location:", evaluate("window.location.href"))
print("Version:", evaluate("document.getElementById('version')?.textContent?.trim()"))

# Get total count of experiments
total = evaluate("document.querySelectorAll('.experiment').length")
print(f"\nTotal experiments: {total}")

# Extract in chunks of 50 to avoid serialization issues
all_flags = []
chunk_size = 50

for start in range(0, total, chunk_size):
    end = start + chunk_size
    print(f"Extracting flags {start}-{end}...")

    chunk = evaluate(f"""
        JSON.stringify(
            Array.from(document.querySelectorAll('.experiment')).slice({start}, {end}).map(exp => {{
                const nameEl = exp.querySelector('.experiment-name');
                const id = nameEl ? nameEl.id.replace('_name', '') : '';
                const h3 = nameEl ? nameEl.querySelector('h3') : null;
                const name = h3 ? h3.textContent.trim() : (nameEl ? nameEl.textContent.trim().split('\\n')[0] : '');
                const descEl = exp.querySelector('.experiment-description');
                const desc = descEl ? descEl.textContent.trim() : '';
                const permalink = exp.querySelector('.permalink');
                const flagId = permalink ? permalink.textContent.trim() : id;
                const selects = Array.from(exp.querySelectorAll('select'));
                const options = selects.map(sel => ({{
                    selected: sel.options[sel.selectedIndex]?.textContent?.trim() || '',
                    selectedValue: sel.value,
                    all: Array.from(sel.options).map(o => o.textContent.trim())
                }}));
                const isDefault = selects.every(sel => sel.selectedIndex === 0);
                const tabContent = exp.closest('[id^="tab-content"]');
                const section = tabContent ? tabContent.id : 'unknown';
                return {{ id: flagId, name, desc: desc.substring(0, 300), options, isDefault, section }};
            }})
        )
    """, timeout=30)

    if chunk and isinstance(chunk, str):
        parsed = json.loads(chunk)
        all_flags.extend(parsed)
        print(f"  Got {len(parsed)} flags")
    else:
        print(f"  ERROR: {chunk}")

print(f"\nTotal flags extracted: {len(all_flags)}")

# Save full dump
output_path = "/home/thiagofalcao/Documents/github/byd-dolphin-hacking/data/chromium_flags_full.json"
with open(output_path, "w") as f:
    json.dump(all_flags, f, indent=2)
print(f"Saved to {output_path}")

ws.close()
