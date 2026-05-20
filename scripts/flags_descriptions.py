#!/usr/bin/env python3
"""Extract detailed info for high-value flags including full descriptions."""

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

# High-value flag IDs to get full details
target_flags = [
    "enable-isolated-web-app-dev-mode",
    "enable-isolated-web-apps",
    "enable-iwa-controlled-frame",
    "install-isolated-web-app-from-url",
    "enable-experimental-web-platform-features",
    "block-insecure-downloads",
    "enable-parallel-downloading",
    "clipboard-unsanitized-content",
    "enable-web-usb-on-extension-service-worker",
    "unsafely-treat-insecure-origin-as-secure",
    "allow-insecure-localhost",
    "block-insecure-private-network-requests",
    "ignore-csp-in-web-payment-api",
    "site-isolation-trial-opt-out",
    "enable-site-per-process",
    "network-service-in-process",
    "enable-network-logging-to-file",
    "storage-access-api",
    "storage-buckets",
    "web-sql-access",
    "enable-generic-sensor-extra-classes",
    "broker-file-operations-on-disk-cache-in-network-service",
    "enable-storage-pressure-event",
    "third-party-storage-partitioning",
    "strict-origin-isolation",
    "enable-restricted-web-apis",
    "policy-logs-page-android",
    "policy-merge-multi-source",
    "enable-machine-learning-model-loader-web-platform-api",
    "debug-packed-apps",
    "external-navigation-debug-logs",
    "enable-local-web-approvals",
    "enable-desktop-pwas-detailed-install-dialog",
    "messages-for-android-pwa-install",
    "cert-dual-verification-enabled",
    "use-sha1-server-handshakes",
    "omit-cors-client-cert",
    "private-network-access-preflight-short-timeout",
    "private-network-access-respect-preflight-results",
    "incognito-downloads-warning",
]

# Get full innerHTML for each target flag's experiment block
print("Extracting detailed flag info...")

result = evaluate(f"""
JSON.stringify(
    {json.dumps(target_flags)}.map(flagId => {{
        const nameEl = document.getElementById(flagId + '_name');
        if (!nameEl) return {{ id: flagId, found: false }};
        const exp = nameEl.closest('.experiment');
        if (!exp) return {{ id: flagId, found: false, nameOnly: true }};
        const h3 = exp.querySelector('h3');
        const descP = exp.querySelector('p');
        const selects = Array.from(exp.querySelectorAll('select'));
        const permalink = exp.querySelector('.permalink');
        const supportedPlatforms = exp.querySelector('.platforms span');
        return {{
            id: flagId,
            found: true,
            name: h3 ? h3.textContent.trim() : '',
            desc: descP ? descP.textContent.trim() : '',
            flagString: permalink ? permalink.textContent.trim() : '',
            platforms: supportedPlatforms ? supportedPlatforms.textContent.trim() : '',
            options: selects.map(s => ({{
                selectedIdx: s.selectedIndex,
                selected: s.options[s.selectedIndex]?.textContent?.trim() || '',
                all: Array.from(s.options).map(o => o.textContent.trim())
            }})),
            isDefault: selects.every(s => s.selectedIndex === 0),
            section: exp.closest('[id^="tab-content"]')?.id || 'unknown',
            fullText: exp.innerText.substring(0, 500)
        }};
    }})
)
""", timeout=30)

if result and isinstance(result, str):
    details = json.loads(result)

    # Save to file
    output_path = "/home/thiagofalcao/Documents/github/byd-dolphin-hacking/data/chromium_flags_details.json"
    with open(output_path, "w") as f:
        json.dump(details, f, indent=2)
    print(f"Saved to {output_path}")

    print(f"\nExtracted {len(details)} flag details\n")
    for d in details:
        if not d.get("found"):
            print(f"  {d['id']}: NOT FOUND ON PAGE")
            continue
        opts = d.get("options", [{}])
        opt0 = opts[0] if opts else {}
        status = "DEFAULT" if d.get("isDefault") else f"SET TO: {opt0.get('selected', '?')}"
        section = "available" if "unavailable" not in d.get("section", "") else "UNAVAILABLE"
        print(f"  #{d['id']} [{section}] ({status})")
        print(f"    Name: {d.get('name', '')}")
        if d.get("desc"):
            print(f"    Desc: {d['desc'][:200]}")
        if d.get("flagString"):
            print(f"    Flag: {d['flagString']}")
        if d.get("platforms"):
            print(f"    Platforms: {d['platforms']}")
        print(f"    Options: {opt0.get('all', [])}")
        print()
else:
    print(f"ERROR: {result}")

ws.close()
