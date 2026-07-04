# BYD Dolphin Jailbreak Analysis — PS4-Style Escalation Path

## Goal

Escalate from app/browser context (unprivileged) to root, bypassing the MCU GUID gate.
The PS4 jailbreak model: browser exploit → kernel/system exploit → root.

## Kernel Analysis

### Kernel Info
- **Version:** Linux 4.14.117-perf (SMP PREEMPT)
- **Build date:** Fri Jul 25 16:13:23 CST 2025
- **Compiler:** Clang 8.0.12 (Android NDK)
- **Platform:** Qualcomm msm8953 (Snapdragon 665 / DiLink 50)
- **Build path:** `Di3.0_repo_dilink3.0_6125f_mp250603_hotfx_canfd_single_214/AP/kernel/msm-4.14`
- **Android security patch:** 2023-02-05

### Compiled-in Features (exploit relevance)
| Feature | Status | Exploit Potential |
|---------|--------|-------------------|
| OverlayFS | ✅ Compiled in | **HIGH** — GameOver(lay) CVE-2023-2640/CVE-2023-32629 (patched Aug 2023, car has Feb 2023) |
| User namespaces | ⚠️ CONFIG_USER_NS=y in strings, but `unshare -U` returns EINVAL from shell | Required for GameOver(lay) — need to test from app context |
| Binder | ✅ 205 refs | Medium — UAF exploits (CVE-2020-0041 etc.) |
| KGSL (Qualcomm GPU) | ✅ 336 refs | **HIGH** — CVE-2021-1905, CVE-2022-22057, etc. |
| userfaultfd | ❌ Not compiled | Eliminates many UAF exploitation techniques |
| nf_tables | ❌ Not compiled | Eliminates netfilter exploits |
| io_uring | ❌ Not compiled | Eliminates io_uring exploits |
| SELinux | ✅ Enforcing | `selinux_enforcing` symbol present |

### Kernel Exploit Candidates (for 4.14.117, patch 2023-02-05)
1. **GameOver(lay)** (CVE-2023-2640 + CVE-2023-32629) — OverlayFS privilege escalation. Patched Aug 2023. Car vulnerable IF user namespaces work from app context.
2. **CVE-2022-22057** — Qualcomm KGSL SLAB UAF. Affects msm-4.14 with older security patches.
3. **CVE-2020-0041** — Binder UAF. May be patched in this kernel build.
4. **Qualcomm-specific** — msm-4.14 has many vendor-specific CVEs.

## Binder Attack Surface

### Root-Running BYD Services (custom SELinux domains)
These services run as **root** with custom SELinux domains and expose binder interfaces:

| Service | Interface | PID | SELinux Domain |
|---------|-----------|-----|----------------|
| cloudmanager | `android.os.ICloudRemoteControlService` | 5158 | `cloudmanager:s0` |
| cloudctrlserv | `android.os.IMqttCloudControlServ` | 242 | `cloudctrlserv:s0` |
| carplayserv | `android.os.ICarplayServer` | 22221 | `carplayserv:s0` |
| carpadinfosrv | (no AIDL type) | 244 | `carpadinfosrv:s0` |
| mqttserv | `android.os.IBYDCloudMqttServer` | 241 | root |
| gbacqservice | `android.os.IBYDGBAcqServer` | 235 | root |
| gbdataservice | `android.os.IGbDataService` | 240 | root |
| deservice | `android.os.IDataExtractService` | 237 | root |
| stateservice | `android.stateservice.IStateService` | 233 | root |
| strategyservice | `android.strategyservice.IStrategyManagerService` | 234 | root |
| diagnosticsrv | `android.gui.IBYDDiagnosticService` | 175 | root |
| crypto.key.service | `android.ICryptoKeyService` | 307 | root |

### Key Finding: Shell CAN call cloudmanager binder service
`service call cloudmanager <tx>` from shell UID 2000 returns `Parcel(NULL)` — NOT an SELinux denial.
This means shell context is **allowed** to transact with cloudmanager's binder interface.

### ICloudRemoteControlService Methods (39 transactions)
Key methods for our goals:
- **tx 3**: `registerListener(ICloudAlarmListener)` — Register callback for alarm events
- **tx 7**: `getTCPStatus()` — Returns TCP connection status to cloud
- **tx 10**: `setControlConfigure(byte[], int)` — Set control configuration
- **tx 23**: `systemInfoInd(byte[], int)` — System info indication

### ICloudAlarmListener Callback Interface
When registered, cloudmanager calls back:
- **tx 1**: `alarm_start(int type)` — Alarm started (type=0 standard, type=3 find car)
- **tx 2**: `alarm_stop(int type)` — Alarm stopped
- **tx 3**: `set_alarm_interval(int type, int interval)`
- **tx 4**: `vin_ready()` — VIN is ready
- **tx 5**: `notify_tcp_status(int status)` — TCP status change
- **tx 6**: `notify_reboot_qualcomm()` — Reboot request

### Attack Strategy: Register Fake Listener
1. Write app_process Java program implementing `ICloudAlarmListener.Stub`
2. Call `registerListener` (tx 3) on cloudmanager service
3. When phone app triggers "find car", cloudmanager calls our `alarm_start(3)`
4. Observe timing, type values, and correlate with logcat
5. **Does NOT give us the GUID** — but confirms the alarm path is active

## system_app BYD Services (system UID — one step from root)
| Service | Package | PID |
|---------|---------|-----|
| BYDAutoServer | `com.byd.car.server` | 1800 |
| ICloudServiceApp | `com.byd.cloudserviceapp` | 3580 |
| IBYDAuthService | `com.byd.authservice` | 1770 |
| BYDMgmtService | system_server | 692 |
| AutoContainer | system_server | 692 |
| IAirConditioningService | system_server | 692 |
| IBootBusinessService | system_server | 692 |

## auto.default.so Analysis (Ghidra)

### HAL Architecture
```
Java BYDAutoManager → libbydauto.so (JNI, client) 
→ binder IPC → system_server → libbydautoservice.so 
→ AutoInterface (auto.default.so) → SPI/MCU
```

### Key Functions
- `AutoInterface::writeDevice(byte* data, uint* len)` at 0x124184 — writes to SPI device
- `AutoInterface::readDevice(char* buf, int len)` at 0x12450c — reads from SPI device
- `AutoInterface::openDevice()` at 0x124030 — opens `/dev/byd_auto` device
- `FunctionTable` — 200+ conversion functions for feature IDs

### writeDevice Vulnerability Analysis
- Data chunked to max 0xfd (253) bytes per SPI write
- Chunk size check: `param_2[4] + 5 > 0xfc` → reject (prevents integer overflow)
- Hex dump limited to 0x36 (54) bytes in 184-byte stack buffer (safe)
- **No stack buffer overflow found** — data passed directly to `write()` syscall
- Not directly exploitable from app context

### readDevice Vulnerability Analysis
- Simple wrapper around `read(fd, buf, len)` syscall
- **No stack buffer overflow** — caller controls buffer size

## Escalation Chain Summary

```
┌─────────────────────────────────────────────────────┐
│  Browser (app UID)                                  │
│  CVE-2023-3420 (V8 OOB) → code execution            │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  System UID (system_server / system_app)             │
│  Binder exploit on BYD services                      │
│  OR auto.default.so overflow (not found yet)         │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  Root (cloudmanager / carplayserv / etc.)            │
│  Binder exploit on root BYD services                 │
│  OR kernel exploit (GameOver(lay) / KGSL)           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  GOAL: Read cloudmanager memory → extract GUID      │
│  OR: Call remoteControltoMcu() directly             │
│  OR: Patch SELinux → disable enforcing              │
└─────────────────────────────────────────────────────┘
```

## carplayserv Analysis (ROOT, network-exposed) — CRITICAL

### Architecture
- **Binary:** `/system/bin/carplayserv` (2.2MB ARM64 ELF, stripped)
- **Shared lib:** `/system/lib64/libcarplayserv.so` (43KB) — binder interface
- **Runs as:** root (UID 0), SELinux domain `carplayserv:s0`
- **Listens on:** `0.0.0.0:7000` (TCP + IPv6) — ANY device on car WiFi can connect
- **Protocol:** AirPlay/450.14 + CarPlay + iAP2 + HomeKit pairing
- **Binder service:** `carplayserv` → `android.os.ICarplayServer`

### AirPlay Version & CVEs
- **Version:** AirPlay/450.14 (APT — AirPlay Protocol Toolkit, ~2021 era)
- **Security patch:** 2023-02-05 (pre-dates AirPlay SDK fixes by 2+ years)
- **SDK fixed:** AirPlay audio SDK 2.7.1 / video SDK 3.6.0.126 (March 31, 2025)

| CVE | Description | Impact | Status |
|-----|-------------|--------|--------|
| **CVE-2025-24132** | Memory handling in AirPlay SDK | **ROOT RCE** — "allows for remote code execution (RCE) with root privileges" (Oligo Security) | **VULNERABLE** — car SDK predates fix by years |
| **CVE-2025-30422** | Buffer overflow in AirPlay SDK | App termination / potential RCE | **VULNERABLE** |
| **CVE-2025-24271** | AirPlay access bypass | Unauthenticated AirPlay commands without pairing | **VULNERABLE** — no fix until iOS 18.4 (Mar 2025) |
| CVE-2024-37602 | Mercedes NTG6 AirPlay NULL deref | DoS (needs physical Ethernet access) | N/A — different platform |

### CVE-2025-24132 — Attack Chain (confirmed by Oligo Security, Dark Reading Sep 2025)

**Researcher:** Uri Katz, Oligo Security (disclosed April 29, 2025)
**Apple advisory:** https://support.apple.com/en-us/122403
**CVSS:** 6.5 (CVSS:3.1/AV:A/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H) — NVD rates as DoS, but Oligo confirms **ROOT RCE**

**Attack flow (as described by Oligo Security):**
1. **Entry:** Connect to car WiFi (we already have ADB — bypass this step)
2. **iAP2 protocol:** iAP2 only authenticates ONE WAY — car doesn't verify the connecting device. Attacker impersonates an iPhone.
3. **AirPlay SDK trigger:** Send crafted AirPlay protocol message to port 7000
4. **Memory corruption:** CVE-2025-24132 triggers memory corruption in AirPlay handler
5. **Root RCE:** Code execution with root privileges in carplayserv

**Why the car IS vulnerable:**
- carplayserv uses AirPlay/450.14 (ancient SDK)
- Fix requires AirPlay audio SDK 2.7.1 / video SDK 3.6.0.126
- BYD hasn't updated (as of firmware 13.1.32.2507250.1, July 2025)
- Dark Reading (Sep 2025): "no car manufacturers have actually fixed their systems"

**Our advantage:** We already have ADB WiFi access to the car's network (192.168.10.10). We don't need Bluetooth or physical access — we can probe port 7000 directly.

### AirPlay Request Handlers (root, network-reachable)
```
_requestProcessAuthSetup(connection, httpMsg)     — auth setup
_requestProcessSetProperty(connection, httpMsg)   — property setting
_requestProcessPairSetupHomeKit(connection, msg)  — HomeKit pairing setup
_requestProcessPairVerifyHomeKit(connection, msg) — HomeKit pairing verify
_GeneralAudioProcessPacket(session, ctx, buf, sz) — audio packet processing
APSRTPPacketHandler                                — RTP packet handler
_SetupClientExchange(session, in, inLen, out, sz) — crypto exchange
_VerifyPairingClientExchange(...)                   — crypto verify
```

### Binder Interface (BnCarplayServer::onTransact)
Analyzed via Ghidra decompilation of libcarplayserv.so:

| TX | Method | Data Pattern |
|----|--------|-------------|
| 1 | SendNMEAData(byte[], int) | VLA stack alloc — safe (dynamic size) |
| 2 | SendUISource(byte[], int) | VLA stack alloc — safe |
| 3 | SendWireLessMsg(byte[], int) | VLA stack alloc — safe |
| 4 | SendPhoneManager(byte[], int) | VLA stack alloc — safe |
| 5 | RegisterListener(listener, int, byte[], int) | VLA + binder |
| 6 | UnRegisterListener(int, listener) | Standard |

The binder onTransact uses VLA (alloca-style) for data buffers — no fixed-size overflow.
**The vulnerability is in the AirPlay protocol handlers**, not the binder interface.

### Live Port 7000 Probe Results (tested via ADB port forwarding + on-car curl)

**Confirmed:** TCP connections to port 7000 ARE accepted. HTTP server IS running.
**Blocked by:** `[HTTPServer] link stoped, do not start carplay session`

The carplayserv HTTP server checks CarPlay link state BEFORE processing any HTTP request. When no iPhone is connected (USB or wireless), the link is "stopped" and all HTTP connections are immediately closed with an empty reply.

**Link state properties (all read-only from shell):**
- `sys.carplay.connected=0` — not connected
- `sys.carplay.transport=-1` — no transport
- `sys.carplay.connecting=0` — not connecting
- `sys.carplay.support=1` — CarPlay IS supported
- `sys.carplay.uuid=F209634D-A589-45BC-B3BD-5C075410A8B4`

**Endpoints probed (all returned empty reply):**
`/info`, `/server-info`, `/pair-setup`, `/pair-verify`, `/auth-setup`, `/feedback`, `/stream`, `/reverse`

**To reach the AirPlay handler, we need to establish a CarPlay link first:**
1. **iAP2 protocol simulation** — send iAP2 handshake over TCP to establish wireless link
2. **Real iPhone** — connect an iPhone briefly to establish link, then probe while active
3. **Property bypass** — find a way to set `sys.carplay.connected=1` (requires root or system UID)
4. **Binary analysis** — find the link check in Ghidra, understand if it can be bypassed via crafted packet

**Other listening ports:**
- Port 7000 (0x1B58) — carplayserv (0.0.0.0) — gated by link check
- Port 12406 (0x3076) — IDD-IDPS (localhost only)

### Network Topology
```
Car WiFi: 192.168.10.x
  ├── Head Unit: 192.168.10.10
  │     ├── :5555  — ADB (IPv6 only)
  │     ├── :7000  — carplayserv/AirPlay (0.0.0.0!) ← ATTACK TARGET
  │     ├── :12406 — IDD-IDPS (localhost only)
  │     ├── :14002-14041 — Various BYD services (IPv6)
  │     └── :14006 — hbs (IPv6)
  └── Phone/laptop: 192.168.10.X (attacker)
```

## Extracted Root Service Binaries

All extracted from user's firmware (`13.1.32.2507250.1`) via 7z on system.img:

| Binary | Size | SELinux Domain | Binder Interface | Analysis Status |
|--------|------|----------------|------------------|-----------------|
| **carplayserv** | 2.2MB | `carplayserv:s0` | ICarplayServer | AirPlay/450.14 — CVE-2024-44189 likely |
| **strategyservice** | 209KB | root | IStrategyManagerService | Pending |
| **gbacqservice** | 199KB | root | IBYDGBAcqServer | Pending |
| **gbdataservice** | 120KB | root | IGbDataService | Pending |
| **deservice** | 61KB | root | IDataExtractService | Has AES/RSA + network reply |
| **cloudctrlserv** | 55KB | `cloudctrlserv:s0` | IMqttCloudControlServ | Already analyzed |
| **cloudmanager** | 346KB | `cloudmanager:s0` | ICloudRemoteControlService | Fully analyzed (Ghidra) |
| **mqttserv** | 178KB | root | IBYDCloudMqttServer | Pending |
| **stateservice** | 26KB | root | IStateService | Pending |
| **diagnosticsrv** | 11KB | root | IBYDDiagnosticService | Pending |
| **cryptokeyserver** | 11KB | root | ICryptoKeyService | Pending |
| **carpadinfosrv** | 11KB | root | (no AIDL) | Pending |
| **detectionservice** | 11KB | root | IDetectionService | Pending |

### deservice (DataExtractService) — Notable
- Has `wifi_link_connect_status(int, void*, int)` — processes WiFi data
- Has `aes_cbc_pcsk5_encrypt`, `encryptByPrikeyString` — RSA/AES crypto
- Has `replyClient(char*, int, uint, uint)` — sends data to network clients
- Has `packData(char*, uint, uint, uint, uchar*)` — packs data into buffer
- Has `random_uuid(char*)` — UUID generation

## Most Promising Paths

### 1. CVE-2025-24132 — AirPlay ROOT RCE on carplayserv (HIGHEST priority)
- carplayserv runs AirPlay/450.14 as root on 0.0.0.0:7000
- Confirmed by Oligo Security as **ROOT RCE** (not just DoS as NVD states)
- Fixed in AirPlay audio SDK 2.7.1 (March 2025) — car has 2021-era SDK
- Dark Reading (Sep 2025): "no car manufacturers have actually fixed their systems"
- **Attack vector:** Connect to car WiFi → send crafted AirPlay to port 7000 → root RCE
- **Our advantage:** Already on car WiFi via ADB, no Bluetooth/iAP2 step needed
- **Next:** Analyze carplayserv AirPlay handlers in Ghidra to find trigger point
- **PoC status:** Oligo Security still concealing technical details — need to reverse engineer from binary

### 2. GameOver(lay) — Kernel exploit
- OverlayFS compiled in, kernel < 5.11, security patch before Aug 2023
- Need to verify user namespace creation from APP context (not shell)
- If it works: instant root from browser exploit chain

### 3. KGSL kernel exploit
- Qualcomm GPU driver present (336 refs)
- Multiple known CVEs for msm-4.14 KGSL
- Requires GPU access from browser (WebGL)

### 4. Binder exploit on root BYD services
- cloudmanager, carplayserv, etc. accept binder from shell
- Need to find vulnerability in their binder transaction handlers
- carplayserv binder handler uses VLA (safe) — but AirPlay protocol handlers untested

## Firmware Resources
- boot.img → kernel at `/tmp/opencode/byd-kernel-decompressed` (37MB, 4.14.117)
- vendor.img → at `/tmp/opencode/byd-all-partitions/vendor.img` (877MB)
- system.img → at `/tmp/opencode/byd-payload-out/system.img` (4.8GB)
- Root service binaries → `/tmp/opencode/byd-root-services/` (15 binaries, 3.2MB total)
- cloudmanager binary → `data/firmware-binaries/cloudmanager` (346KB)
- carplayserv binary → `/tmp/opencode/byd-root-services/carplayserv` (2.2MB)
- libcarplayserv.so → `/tmp/opencode/byd-root-services/libcarplayserv.so` (43KB)
