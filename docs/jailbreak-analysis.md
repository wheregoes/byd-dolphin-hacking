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

## Most Promising Paths

### 1. GameOver(lay) — Kernel exploit (HIGHEST priority)
- OverlayFS compiled in, kernel < 5.11, security patch before Aug 2023
- Need to verify user namespace creation from APP context (not shell)
- If it works: instant root from browser exploit chain
- Test: write APK that calls `unshare(CLONE_NEWUSER)`

### 2. KGSL kernel exploit
- Qualcomm GPU driver present (336 refs)
- Multiple known CVEs for msm-4.14 KGSL
- Requires GPU access from browser (WebGL)

### 3. Binder exploit on root BYD services
- cloudmanager, carplayserv, etc. accept binder from shell
- Need to find vulnerability in their binder transaction handlers
- Analyze vendor binaries (cloudctrlserv, carplayserv) in Ghidra

### 4. Direct cloudmanager memory read
- `/proc/5158/mem` — root-only, but if we get system UID first...
- `/proc/5158/maps` — might be readable from shell

## Firmware Resources
- boot.img → kernel at `/tmp/opencode/byd-kernel-decompressed` (37MB)
- vendor.img → at `/tmp/opencode/byd-all-partitions/vendor.img`
- system.img → at `/tmp/opencode/byd-all-partitions/system.img` (5GB)
- cloudmanager binary → `data/firmware-binaries/cloudmanager` (346KB)
