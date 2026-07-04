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

