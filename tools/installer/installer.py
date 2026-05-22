#!/usr/bin/env python3
"""BYD Dolphin APK Installer — simple GUI for non-technical users.

One-file server: open browser → follow steps → APK installed on car.

Steps:
  1. Enable ADB over WiFi (if not already done)
  2. Find car on network
  3. Select APK
  4. Install

Usage:
  python3 installer.py
  python3 installer.py --port 9090
"""
import http.server
import json
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
import webbrowser
from pathlib import Path
from urllib.parse import parse_qs, urlparse

PORT = 8080
ADB = shutil.which("adb")

HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BYD APK Installer</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
:root{--bg:#0f1117;--card:#1a1d27;--border:#2a2d3a;--text:#e0e4f0;--dim:#8890a4;--accent:#3b82f6;--green:#22c55e;--red:#ef4444;--yellow:#eab308;--radius:10px}
body{background:var(--bg);color:var(--text);font:16px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;min-height:100vh}
.container{max-width:700px;margin:0 auto;padding:24px 20px}
h1{font-size:28px;font-weight:700;margin-bottom:4px;display:flex;align-items:center;gap:10px}
h1 .logo{font-size:32px}
.subtitle{color:var(--dim);font-size:14px;margin-bottom:28px}

.steps{display:flex;gap:4px;margin-bottom:24px}
.step-dot{flex:1;height:4px;border-radius:2px;background:var(--border);transition:background 0.3s}
.step-dot.active{background:var(--accent)}
.step-dot.done{background:var(--green)}

.card{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:20px 24px;margin-bottom:16px}
.card h2{font-size:18px;margin-bottom:12px;display:flex;align-items:center;gap:8px}
.card p{color:var(--dim);font-size:14px;margin-bottom:12px;line-height:1.6}

.step-section{display:none}
.step-section.visible{display:block}

.instruction{background:#1e2130;border-left:3px solid var(--accent);padding:12px 16px;margin:10px 0;border-radius:0 var(--radius) var(--radius) 0;font-size:14px}
.instruction strong{color:var(--accent)}
.instruction code{background:#252840;padding:2px 6px;border-radius:4px;font-family:'SF Mono',Consolas,monospace;font-size:13px}

.input-group{margin:12px 0}
.input-group label{display:block;font-size:13px;color:var(--dim);margin-bottom:4px}
.input-row{display:flex;gap:8px}
input[type="text"],input[type="number"]{background:#12141c;border:1px solid var(--border);color:var(--text);padding:10px 14px;border-radius:var(--radius);font-size:15px;width:100%;font-family:inherit}
input:focus{outline:none;border-color:var(--accent)}

button{background:var(--accent);color:#fff;border:none;padding:12px 24px;border-radius:var(--radius);font-size:15px;font-weight:600;cursor:pointer;transition:all 0.2s;font-family:inherit}
button:hover{filter:brightness(1.1)}
button:disabled{opacity:0.4;cursor:not-allowed}
button.secondary{background:transparent;border:1px solid var(--border);color:var(--text)}
button.secondary:hover{border-color:var(--dim)}
button.success{background:var(--green)}
button.danger{background:var(--red)}
.btn-row{display:flex;gap:8px;margin-top:16px;flex-wrap:wrap}

.status{display:flex;align-items:center;gap:8px;padding:10px 14px;border-radius:var(--radius);font-size:14px;margin:10px 0}
.status.info{background:#1e2a4a;border:1px solid #2d4a8a;color:#60a5fa}
.status.ok{background:#14291a;border:1px solid #22633a;color:var(--green)}
.status.warn{background:#2a2510;border:1px solid #5a4a10;color:var(--yellow)}
.status.err{background:#2a1414;border:1px solid #5a2222;color:var(--red)}
.status .dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.status.info .dot{background:#60a5fa}
.status.ok .dot{background:var(--green)}
.status.warn .dot{background:var(--yellow)}
.status.err .dot{background:var(--red)}

.device-card{background:#12141c;border:1px solid var(--border);border-radius:var(--radius);padding:14px 18px;margin:8px 0;cursor:pointer;transition:all 0.2s}
.device-card:hover{border-color:var(--accent)}
.device-card.selected{border-color:var(--green);background:#14291a}
.device-card .ip{font-size:18px;font-weight:600;font-family:'SF Mono',Consolas,monospace}
.device-card .label{font-size:12px;color:var(--dim)}

.file-drop{border:2px dashed var(--border);border-radius:var(--radius);padding:40px 20px;text-align:center;cursor:pointer;transition:all 0.2s;margin:12px 0}
.file-drop:hover,.file-drop.dragover{border-color:var(--accent);background:#1a1e30}
.file-drop p{color:var(--dim);font-size:14px}
.file-drop .icon{font-size:36px;margin-bottom:8px}

.log{background:#0a0c12;border:1px solid var(--border);border-radius:var(--radius);padding:12px;font-family:'SF Mono',Consolas,monospace;font-size:12px;max-height:200px;overflow-y:auto;white-space:pre-wrap;margin:12px 0;line-height:1.5}
.log .ok{color:var(--green)}.log .err{color:var(--red)}.log .warn{color:var(--yellow)}.log .info{color:#60a5fa}

.spinner{display:inline-block;width:16px;height:16px;border:2px solid var(--border);border-top-color:var(--accent);border-radius:50%;animation:spin 0.6s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}

.password-result{background:#14291a;border:1px solid #22633a;border-radius:var(--radius);padding:16px;margin:12px 0}
.password-result .pw{font-size:24px;font-weight:700;font-family:'SF Mono',Consolas,monospace;color:var(--green);letter-spacing:2px;text-align:center;padding:8px 0;user-select:all}
.password-result .hint{font-size:12px;color:var(--dim);text-align:center}

.complete-banner{text-align:center;padding:40px 20px}
.complete-banner .check{font-size:64px;margin-bottom:16px}
.complete-banner h2{font-size:24px;color:var(--green);margin-bottom:8px}
</style>
</head>
<body>
<div class="container">
<h1><span class="logo">&#x1f697;</span> BYD APK Installer</h1>
<div class="subtitle">Install any app on your BYD Dolphin — no technical knowledge needed</div>

<div class="steps" id="step-dots">
<div class="step-dot" data-step="1"></div>
<div class="step-dot" data-step="2"></div>
<div class="step-dot" data-step="3"></div>
<div class="step-dot" data-step="4"></div>
</div>

<!-- STEP 1: Enable ADB -->
<div class="step-section visible" id="step1">
<div class="card">
<h2>&#x1f511; Step 1: Enable ADB on your car</h2>
<p>Your BYD needs ADB (developer access) enabled over WiFi. If you already did this, skip to step 2.</p>

<div class="instruction">
<strong>1.</strong> On the car screen, open the <strong>Phone</strong> app (dialer)<br>
<strong>2.</strong> Dial: <code>*#91532547#*</code><br>
<strong>3.</strong> A screen will show your <strong>IMEI number</strong> — write down the <strong>last 6 digits</strong>
</div>

<div class="input-group">
<label>Last 6 digits of IMEI</label>
<div class="input-row">
<input type="text" id="imei-input" placeholder="e.g. 123456" maxlength="6" pattern="[0-9]{6}">
<button onclick="generatePassword()">Get Password</button>
</div>
</div>

<div id="password-area"></div>

<div class="instruction" id="adb-steps" style="display:none">
<strong>4.</strong> On the IMEI screen, enter the password shown above<br>
<strong>5.</strong> Go to <strong>TestTools</strong> menu<br>
<strong>6.</strong> Enable: <strong>"Wireless adb debug switch"</strong><br>
<strong>7.</strong> Enable: <strong>"Install unknown source switch"</strong><br>
<strong>8.</strong> Done! Your car now accepts wireless connections.
</div>
</div>

<div class="btn-row">
<button onclick="goStep(2)">Next: Find Car</button>
<button class="secondary" onclick="goStep(2)">Skip (already enabled)</button>
</div>
</div>

<!-- STEP 2: Find car -->
<div class="step-section" id="step2">
<div class="card">
<h2>&#x1f50d; Step 2: Find your car on the network</h2>
<p>Make sure your computer and car are on the <strong>same WiFi network</strong>.</p>

<div id="scan-status"></div>
<div id="device-list"></div>

<div class="input-group">
<label>Or enter car IP manually</label>
<div class="input-row">
<input type="text" id="manual-ip" placeholder="192.168.1.xxx">
<button class="secondary" onclick="connectManual()">Connect</button>
</div>
</div>
</div>

<div class="btn-row">
<button onclick="scanNetwork()" id="scan-btn">Scan Network</button>
<button class="secondary" onclick="goStep(1)">Back</button>
</div>
</div>

<!-- STEP 3: Select APK -->
<div class="step-section" id="step3">
<div class="card">
<h2>&#x1f4e6; Step 3: Select APK to install</h2>
<p>Choose the Android app file (.apk) you want to install on your car.</p>

<div id="connection-info"></div>

<div class="file-drop" id="file-drop" onclick="document.getElementById('file-input').click()">
<div class="icon">&#x1f4c1;</div>
<p>Click to select APK file<br><span style="font-size:12px">or drag and drop here</span></p>
</div>
<input type="file" id="file-input" accept=".apk" style="display:none" onchange="handleFile(this.files[0])">

<div id="file-info" style="display:none"></div>
</div>

<div class="btn-row">
<button onclick="startInstall()" id="install-btn" disabled>Install on Car</button>
<button class="secondary" onclick="goStep(2)">Back</button>
</div>
</div>

<!-- STEP 4: Installing -->
<div class="step-section" id="step4">
<div class="card">
<h2>&#x1f680; Step 4: Installing</h2>
<div id="install-progress"></div>
<div class="log" id="install-log"></div>
</div>

<div id="complete-area" style="display:none">
<div class="complete-banner">
<div class="check">&#x2705;</div>
<h2>Installation Complete!</h2>
<p style="color:var(--dim)">The app has been installed on your car.</p>
</div>
<div class="btn-row" style="justify-content:center">
<button onclick="resetAll()">Install Another</button>
</div>
</div>

<div class="btn-row" id="step4-back">
<button class="secondary" onclick="goStep(3)">Back</button>
</div>
</div>
</div>

<script>
var SERVER = location.origin;
var currentStep = 1;
var connectedTarget = null;
var selectedFile = null;
var uploadedFilename = null;

var makeStatus = function(cls, text) {
    var el = document.createElement('div');
    el.className = 'status ' + cls;
    var dot = document.createElement('span');
    dot.className = 'dot';
    el.appendChild(dot);
    var span = document.createElement('span');
    span.textContent = text;
    el.appendChild(span);
    return el;
};

var makeStatusWithSpinner = function(cls, text) {
    var el = document.createElement('div');
    el.className = 'status ' + cls;
    var dot = document.createElement('span');
    dot.className = 'dot';
    el.appendChild(dot);
    var spinner = document.createElement('span');
    spinner.className = 'spinner';
    el.appendChild(spinner);
    var span = document.createElement('span');
    span.textContent = '  ' + text;
    el.appendChild(span);
    return el;
};

var clearEl = function(el) { while (el.firstChild) el.removeChild(el.firstChild); };

var goStep = function(n) {
    currentStep = n;
    document.querySelectorAll('.step-section').forEach(function(el) { el.classList.remove('visible'); });
    document.getElementById('step' + n).classList.add('visible');
    document.querySelectorAll('.step-dot').forEach(function(dot) {
        var s = parseInt(dot.dataset.step);
        dot.className = 'step-dot' + (s < n ? ' done' : s === n ? ' active' : '');
    });
};

async function generatePassword() {
    var input = document.getElementById('imei-input').value.trim();
    var area = document.getElementById('password-area');
    clearEl(area);
    if (input.length !== 6 || !/^\d{6}$/.test(input)) {
        area.appendChild(makeStatus('err', 'Enter exactly 6 digits'));
        return;
    }
    area.appendChild(makeStatusWithSpinner('info', 'Generating password...'));
    try {
        var resp = await fetch(SERVER + '/api/password?imei=' + encodeURIComponent(input));
        var data = await resp.json();
        clearEl(area);
        if (data.ok) {
            var container = document.createElement('div');
            container.className = 'password-result';
            var hint1 = document.createElement('div');
            hint1.className = 'hint';
            hint1.textContent = 'Enter this password on the car screen:';
            container.appendChild(hint1);
            data.passwords.forEach(function(pw) {
                var pwEl = document.createElement('div');
                pwEl.className = 'pw';
                pwEl.textContent = pw;
                container.appendChild(pwEl);
            });
            if (data.passwords.length > 1) {
                var hint2 = document.createElement('div');
                hint2.className = 'hint';
                hint2.textContent = 'Try each password — one will work';
                container.appendChild(hint2);
            }
            area.appendChild(container);
            document.getElementById('adb-steps').style.display = 'block';
        } else {
            area.appendChild(makeStatus('err', data.error || 'Failed to generate password'));
        }
    } catch(e) {
        clearEl(area);
        area.appendChild(makeStatus('err', 'Server error: ' + e.message));
    }
}

async function scanNetwork() {
    var btn = document.getElementById('scan-btn');
    btn.disabled = true;
    btn.textContent = 'Scanning...';
    var statusEl = document.getElementById('scan-status');
    var listEl = document.getElementById('device-list');
    clearEl(statusEl);
    clearEl(listEl);
    statusEl.appendChild(makeStatusWithSpinner('info', 'Scanning network for BYD devices (takes ~10 seconds)...'));

    try {
        var resp = await fetch(SERVER + '/api/scan');
        var data = await resp.json();
        clearEl(statusEl);
        clearEl(listEl);
        if (data.devices && data.devices.length > 0) {
            statusEl.appendChild(makeStatus('ok', 'Found ' + data.devices.length + ' device(s) with ADB enabled'));
            data.devices.forEach(function(ip) {
                if (!/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(ip)) return;
                var card = document.createElement('div');
                card.className = 'device-card';
                card.id = 'dev-' + ip.replace(/\./g, '-');
                card.onclick = function() { selectDevice(ip); };
                var ipEl = document.createElement('div');
                ipEl.className = 'ip';
                ipEl.textContent = ip;
                card.appendChild(ipEl);
                var label = document.createElement('div');
                label.className = 'label';
                label.textContent = 'ADB port 5555 open';
                card.appendChild(label);
                listEl.appendChild(card);
            });
        } else {
            statusEl.appendChild(makeStatus('warn', 'No devices found. Is ADB enabled? Are you on the same WiFi?'));
        }
    } catch(e) {
        clearEl(statusEl);
        statusEl.appendChild(makeStatus('err', 'Scan failed: ' + e.message));
    }
    btn.disabled = false;
    btn.textContent = 'Scan Again';
}

async function selectDevice(ip) {
    document.querySelectorAll('.device-card').forEach(function(c) { c.classList.remove('selected'); });
    var el = document.getElementById('dev-' + ip.replace(/\./g, '-'));
    if (el) el.classList.add('selected');
    await connectToDevice(ip);
}

async function connectManual() {
    var ip = document.getElementById('manual-ip').value.trim();
    if (!ip) return;
    await connectToDevice(ip);
}

async function connectToDevice(ip) {
    var statusEl = document.getElementById('scan-status');
    clearEl(statusEl);
    statusEl.appendChild(makeStatusWithSpinner('info', 'Connecting to ' + ip + '...'));
    try {
        var resp = await fetch(SERVER + '/api/connect', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ip: ip})
        });
        var data = await resp.json();
        clearEl(statusEl);
        if (data.ok) {
            connectedTarget = data.target;
            var msg = 'Connected to ' + data.target;
            if (data.model) msg += ' (' + data.model + ')';
            statusEl.appendChild(makeStatus('ok', msg));
            setTimeout(function() { goStep(3); updateConnectionInfo(); }, 800);
        } else {
            statusEl.appendChild(makeStatus('err', 'Connection failed: ' + data.error));
        }
    } catch(e) {
        clearEl(statusEl);
        statusEl.appendChild(makeStatus('err', 'Error: ' + e.message));
    }
}

var updateConnectionInfo = function() {
    var el = document.getElementById('connection-info');
    clearEl(el);
    if (connectedTarget) {
        el.appendChild(makeStatus('ok', 'Connected to ' + connectedTarget));
    }
};

var handleFile = function(file) {
    if (!file) return;
    selectedFile = file;
    var sizeMB = (file.size / 1024 / 1024).toFixed(1);
    var infoEl = document.getElementById('file-info');
    infoEl.style.display = 'block';
    clearEl(infoEl);
    infoEl.appendChild(makeStatus('ok', file.name + ' (' + sizeMB + ' MB)'));
    document.getElementById('install-btn').disabled = false;
};

var drop = document.getElementById('file-drop');
drop.ondragover = function(e) { e.preventDefault(); drop.classList.add('dragover'); };
drop.ondragleave = function() { drop.classList.remove('dragover'); };
drop.ondrop = function(e) { e.preventDefault(); drop.classList.remove('dragover'); handleFile(e.dataTransfer.files[0]); };

async function startInstall() {
    if (!selectedFile || !connectedTarget) return;
    goStep(4);
    var log = document.getElementById('install-log');
    clearEl(log);

    var addLog = function(msg, cls) {
        var d = document.createElement('div');
        d.className = cls || 'info';
        d.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg;
        log.appendChild(d);
        log.scrollTop = log.scrollHeight;
    };

    var progressEl = document.getElementById('install-progress');
    clearEl(progressEl);
    progressEl.appendChild(makeStatusWithSpinner('info', 'Uploading APK to server...'));
    addLog('Uploading ' + selectedFile.name + ' (' + (selectedFile.size / 1024 / 1024).toFixed(1) + ' MB)...', 'info');

    try {
        var formData = new FormData();
        formData.append('apk', selectedFile);
        var uploadResp = await fetch(SERVER + '/api/upload', {method: 'POST', body: formData});
        var uploadData = await uploadResp.json();
        if (!uploadData.ok) throw new Error(uploadData.error || 'Upload failed');
        uploadedFilename = uploadData.filename;
        addLog('Uploaded: ' + uploadData.filename, 'ok');

        clearEl(progressEl);
        progressEl.appendChild(makeStatusWithSpinner('info', 'Pushing APK to car...'));
        addLog('Pushing to car via ADB...', 'info');

        var pushResp = await fetch(SERVER + '/api/push-install', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({filename: uploadData.filename, target: connectedTarget})
        });
        var pushData = await pushResp.json();
        clearEl(progressEl);
        if (pushData.ok) {
            addLog('Install result: ' + pushData.stdout, 'ok');
            addLog('', 'info');
            addLog('=== INSTALLATION COMPLETE ===', 'ok');
            progressEl.appendChild(makeStatus('ok', 'APK installed successfully!'));
            document.getElementById('complete-area').style.display = 'block';
            document.getElementById('step4-back').style.display = 'none';
        } else {
            addLog('Install failed: ' + (pushData.error || pushData.stderr), 'err');
            progressEl.appendChild(makeStatus('err', 'Installation failed — see log below'));
        }
    } catch(e) {
        addLog('Error: ' + e.message, 'err');
        clearEl(progressEl);
        progressEl.appendChild(makeStatus('err', e.message));
    }
}

var resetAll = function() {
    selectedFile = null;
    uploadedFilename = null;
    document.getElementById('file-input').value = '';
    var infoEl = document.getElementById('file-info');
    infoEl.style.display = 'none';
    clearEl(infoEl);
    document.getElementById('install-btn').disabled = true;
    document.getElementById('complete-area').style.display = 'none';
    document.getElementById('step4-back').style.display = '';
    goStep(3);
    updateConnectionInfo();
};

document.getElementById('imei-input').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') generatePassword();
});
</script>
</body>
</html>"""


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def scan_for_adb(timeout=0.3):
    local_ip = get_local_ip()
    subnet = ".".join(local_ip.split(".")[:3])
    found = []

    def check(ip):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(timeout)
            if s.connect_ex((ip, 5555)) == 0:
                found.append(ip)
            s.close()
        except Exception:
            pass

    threads = []
    for i in range(1, 255):
        t = threading.Thread(target=check, args=(f"{subnet}.{i}",))
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
    return found


def adb_run(args, timeout=15):
    result = subprocess.run(
        [ADB] + args, capture_output=True, text=True, timeout=timeout
    )
    return result


UPLOAD_DIR = Path(__file__).parent / "uploads"


class InstallerHandler(http.server.BaseHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self._serve_html()
            return
        if self.path.startswith("/api/password"):
            self._password()
            return
        if self.path == "/api/scan":
            self._scan()
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        if self.path == "/api/connect":
            self._connect()
            return
        if self.path == "/api/upload":
            self._upload()
            return
        if self.path == "/api/push-install":
            self._push_install()
            return
        self.send_response(404)
        self.end_headers()

    def _serve_html(self):
        data = HTML.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length)

    def _password(self):
        params = parse_qs(urlparse(self.path).query)
        imei6 = params.get("imei", [""])[0]
        if len(imei6) != 6 or not imei6.isdigit():
            self._json({"ok": False, "error": "Need exactly 6 digits"})
            return
        try:
            import urllib.request
            url = f"https://api.goodview-moving.com/api/gen-secret/{imei6}"
            req = urllib.request.Request(url, headers={"User-Agent": "BYDInstaller/1.0"})
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode())
                if isinstance(data, list):
                    passwords = [str(p) for p in data]
                else:
                    passwords = [str(data)]
                self._json({"ok": True, "passwords": passwords})
        except Exception as e:
            self._json({"ok": False, "error": str(e)})

    def _scan(self):
        devices = scan_for_adb()
        self._json({"devices": devices, "local_ip": get_local_ip()})

    def _connect(self):
        body = json.loads(self._read_body())
        ip = body.get("ip", "")
        if not ip:
            self._json({"ok": False, "error": "No IP provided"})
            return
        if not ADB:
            self._json({"ok": False, "error": "adb not found in PATH. Install Android SDK platform-tools."})
            return
        target = f"{ip}:5555"
        try:
            result = adb_run(["connect", target], timeout=10)
            if "connected" in result.stdout.lower():
                model = ""
                try:
                    m = adb_run(["-s", target, "shell", "getprop", "ro.product.model"], timeout=5)
                    model = m.stdout.strip()
                except Exception:
                    pass
                self._json({"ok": True, "target": target, "model": model})
            else:
                msg = (result.stdout + result.stderr).strip()
                self._json({"ok": False, "error": msg or "Connection refused"})
        except subprocess.TimeoutExpired:
            self._json({"ok": False, "error": "Connection timed out"})
        except Exception as e:
            self._json({"ok": False, "error": str(e)})

    def _upload(self):
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            self._json({"ok": False, "error": "Expected multipart upload"})
            return

        boundary = content_type.split("boundary=")[1].encode()
        body = self._read_body()

        parts = body.split(b"--" + boundary)
        for part in parts:
            if b'name="apk"' in part:
                header_end = part.find(b"\r\n\r\n")
                if header_end < 0:
                    continue
                file_data = part[header_end + 4:]
                if file_data.endswith(b"\r\n"):
                    file_data = file_data[:-2]

                header_section = part[:header_end].decode("utf-8", errors="replace")
                filename = "app.apk"
                if 'filename="' in header_section:
                    filename = header_section.split('filename="')[1].split('"')[0]
                safe = "".join(c for c in Path(filename).name if c.isalnum() or c in ".-_")
                if not safe.endswith(".apk"):
                    safe += ".apk"

                UPLOAD_DIR.mkdir(exist_ok=True)
                dest = UPLOAD_DIR / safe
                dest.write_bytes(file_data)
                self._json({"ok": True, "filename": safe, "size": len(file_data)})
                return
        self._json({"ok": False, "error": "No APK file in upload"})

    def _push_install(self):
        body = json.loads(self._read_body())
        filename = body.get("filename", "")
        target = body.get("target", "")
        if not filename or not target:
            self._json({"ok": False, "error": "Missing filename or target"})
            return
        if not ADB:
            self._json({"ok": False, "error": "adb not found"})
            return

        safe = "".join(c for c in filename if c.isalnum() or c in ".-_")
        local_apk = UPLOAD_DIR / safe
        if not local_apk.exists():
            self._json({"ok": False, "error": "APK not found on server"})
            return

        try:
            push = adb_run(["-s", target, "push", str(local_apk), f"/data/local/tmp/{safe}"], timeout=120)
            if push.returncode != 0:
                self._json({"ok": False, "error": f"Push failed: {push.stderr.strip()}", "stderr": push.stderr.strip()})
                return

            install = adb_run(["-s", target, "shell", f"pm install -r /data/local/tmp/{safe}"], timeout=60)
            adb_run(["-s", target, "shell", f"rm /data/local/tmp/{safe}"], timeout=5)
            local_apk.unlink(missing_ok=True)

            success = "success" in install.stdout.lower()
            self._json({
                "ok": success,
                "stdout": install.stdout.strip(),
                "stderr": install.stderr.strip(),
            })
        except subprocess.TimeoutExpired:
            self._json({"ok": False, "error": "Installation timed out"})
        except Exception as e:
            self._json({"ok": False, "error": str(e)})

    def log_message(self, fmt, *args):
        path = args[0] if args else ""
        if path.startswith("GET /api") or path.startswith("POST /api"):
            print(f"[api] {path}")


def main():
    import argparse
    parser = argparse.ArgumentParser(description="BYD APK Installer")
    parser.add_argument("--port", type=int, default=PORT)
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()

    if not ADB:
        print("[!] WARNING: adb not found in PATH")
        print("[!] Install Android SDK platform-tools")
        print("[!] Server will start but install will fail\n")

    local_ip = get_local_ip()
    print("=" * 50)
    print("  BYD APK Installer")
    print("=" * 50)
    print(f"\n  Open: http://localhost:{args.port}")
    print(f"  Network: http://{local_ip}:{args.port}\n")

    if not args.no_browser:
        threading.Timer(0.5, lambda: webbrowser.open(f"http://localhost:{args.port}")).start()

    server = http.server.HTTPServer(("0.0.0.0", args.port), InstallerHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[server] Stopped")


if __name__ == "__main__":
    main()
