#!/usr/bin/env python3
"""Final verification: NON-DEFAULT flags + filesystem IDs. Navigate target 13 to flags."""

import websocket
import json
import time

ws = websocket.create_connection('ws://localhost:9222/devtools/page/13', suppress_origin=True, timeout=10)
mid = [0]

def cmd(method, params=None):
    mid[0] += 1
    c = {'id': mid[0], 'method': method}
    if params: c['params'] = params
    ws.send(json.dumps(c))
    deadline = time.time() + 10
    while time.time() < deadline:
        ws.settimeout(10)
        try:
            r = json.loads(ws.recv())
            if r.get('id') == mid[0]: return r
        except: break
    return None

def ev(expr):
    r = cmd('Runtime.evaluate', {'expression': expr, 'returnByValue': True})
    if r and 'result' in r:
        return r['result'].get('result', {}).get('value')
    return None

cmd('Page.enable')
cmd('Runtime.enable')

# Navigate
cmd('Page.navigate', {'url': 'chrome://flags'})
time.sleep(6)
print(f'Location: {ev("window.location.href")}')
print(f'Title: {ev("document.title")}')
exp_count = ev('document.querySelectorAll(".experiment").length')
print(f'Experiments: {exp_count}')

# 1. NON-DEFAULT verification
print('\n=== NON-DEFAULT VERIFICATION ===')
r = ev('''JSON.stringify(["disable-accelerated-2d-canvas","ui-disable-partial-swap","disable-webrtc-hw-decoding","disable-webrtc-hw-encoding","disable-javascript-harmony-shipping","disable-accelerated-video-decode","disable-accelerated-video-encode","disable-threaded-scrolling"].map(id => {
    const el = document.getElementById(id + "_name");
    if (!el) return {id, found: false};
    const exp = el.closest(".experiment");
    const sels = Array.from(exp.querySelectorAll("select"));
    return {
        id,
        cls: exp.className,
        selectCount: sels.length,
        details: sels.map(s => ({
            selectedIndex: s.selectedIndex,
            value: s.value,
            optCount: s.options.length,
            opts: Array.from(s.options).map((o,i) => ({i, text: o.textContent.trim(), val: o.value, sel: o.selected}))
        }))
    };
}))''')
if r:
    for d in json.loads(r):
        print(f'\n  #{d["id"]}:')
        print(f'    class: {d.get("cls")}')
        print(f'    selects: {d.get("selectCount")}')
        for s in d.get('details', []):
            print(f'    selectedIndex={s["selectedIndex"]}, value="{s["value"]}", optCount={s["optCount"]}')
            for o in s['opts']:
                m = ' <<<' if o['sel'] else ''
                print(f'      [{o["i"]}] "{o["text"]}" val="{o["val"]}"{m}')

# 2. Filesystem IDs
print('\n=== FILESYSTEM IDS ===')
r2 = ev('''JSON.stringify(
    Array.from(document.querySelectorAll("[id]"))
        .map(e => e.id)
        .filter(id => /file[-_]?sys|file[-_]?access|fs[-_]api|directory|handle|writable|opfs|origin.private|native.file|showsave|showopen|showdir/i.test(id) && !id.endsWith("_name"))
)''')
print(f'Result: {r2}')

# 3. Broader file search
r3 = ev('''JSON.stringify(
    Array.from(document.querySelectorAll("[id]"))
        .map(e => e.id)
        .filter(id => /file/i.test(id) && !id.endsWith("_name"))
)''')
print(f'All file IDs: {r3}')

# 4. Text search in experiments
r4 = ev('''JSON.stringify(
    Array.from(document.querySelectorAll(".experiment")).filter(exp => {
        const t = exp.innerText.toLowerCase();
        return t.includes("file system") || t.includes("filesystem") ||
               t.includes("file access") || t.includes("writable stream") ||
               t.includes("opfs") || t.includes("native file") ||
               t.includes("file api") || t.includes("showsave") || t.includes("showopen");
    }).map(exp => ({
        id: exp.querySelector(".permalink")?.textContent?.trim() || "",
        text: exp.innerText.substring(0, 200)
    }))
)''')
if r4:
    fs = json.loads(r4)
    print(f'\nFilesystem text matches: {len(fs)}')
    for f in fs:
        print(f'  {f["id"]}: {f["text"][:150]}')

# 5. Broker/disk/netlog
r5 = ev('''JSON.stringify(
    Array.from(document.querySelectorAll(".experiment")).filter(exp => {
        const t = exp.innerText.toLowerCase();
        return t.includes("broker") || t.includes("netlog") || t.includes("disk cache") ||
               (t.includes("file") && (t.includes("write") || t.includes("log") || t.includes("operation")));
    }).map(exp => ({
        id: exp.querySelector(".permalink")?.textContent?.trim() || "",
        text: exp.innerText.substring(0, 200)
    }))
)''')
if r5:
    bf = json.loads(r5)
    print(f'\nBroker/disk/file write matches: {len(bf)}')
    for f in bf:
        print(f'  {f["id"]}: {f["text"][:180]}')

ws.close()
print('\nDone.')
