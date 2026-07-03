# 🚗 BYD Dolphin Head Unit — Research & Reverse Engineering

Reverse engineering, documentation, and tooling for the **BYD Dolphin 25/26** infotainment system (DiLink 3, Android 10).

Everything here was discovered through ADB exploration, APK decompilation, and CAN bus probing — no proprietary documentation was used.

> ⚠️ **Disclaimer:** Unofficial, community-driven project — no affiliation with BYD.
> Reverse engineering of BYD's internal Android services for **educational and interoperability purposes only**.
> Use at your own risk — modifying vehicle software may void your warranty or violate BYD's terms of service.
> The authors assume no liability for any damage to your vehicle, software, or data.

---

## 📋 Table of Contents

- [Head Unit Specs](#-head-unit-specs)
- [Getting Started](#-getting-started)
- [Key Findings](#-key-findings)
- [Security Findings](#-security-findings)
- [Architecture](#-architecture)
- [Documentation](#-documentation)
- [Scripts](#-scripts)
- [Repository Structure](#-repository-structure)
- [Firmware Resources & References](#-firmware-resources--references)
- [License](#-license)

---

## 🖥️ Head Unit Specs

| Property | Value |
|----------|-------|
| Platform | DiLink 3.0 |
| Android | 10 (API 29) |
| SoC | Qualcomm QCM6125 (SM6125 Trinket) |
| Architecture | ARM64, 8 cores |
| RAM | ~3.5 GB |
| Kernel | 4.14.117-perf |
| ADB | WiFi, port 5555 |
| Head Unit IP | `192.168.10.10` (car WiFi) |
| Firmware | `13.1.32.2507250.1` (Jul 25 2025) |
| MCU | `13.5.2.2312260.1` |
| DSP | `13.5.5.2505300.2` |
| Bootloader | **Unlocked** (`ro.boot.flash.locked=0`) |
| Verified boot | Orange (unlocked) |
| Instrument Cluster | Separate Qt/QML system (Qt 5.15.10 / 6.5.5) |

> **Tested on firmware `13.1.32.2507250.1`.** Older versions likely work. Newer firmware updates from BYD may change or break things — no guarantees.

---

## 🚀 Getting Started

**New here?** Start with the [Sideloading Guide](docs/sideloading-guide.md) to install apps on your BYD — no root needed.

For custom apps built on this research, see [byd-apps](https://github.com/wheregoes/byd-apps).

### Quick ADB Connection

```bash
adb connect 192.168.10.10:5555
```

### Sideloading Quick Facts

- **USB method** — drop APKs in `Third Party Apps XX` folder (country code suffix), plug into car
- **Master password** — `BYD6125F` (universal across DiLink 3)
- **APK requirements** — ARM64, `targetSdk ≤ 33`, `minSdk ≤ 29`
- **Country-specific restrictions** — Kazakhstan (14-app whitelist), India (Mappls only), Europe/Japan/Australia (online verification)

### CAN Bus Injection (VCDS-Style Coding)

Inject CAN frames from ADB — no root, no OBD2 dongle:

```bash
# 1. Start the ClusterDebug app + service (acts as privileged proxy)
adb shell "am start -n com.byd.clusterdebug/.MainActivity"
adb shell "am startservice -n com.byd.clusterdebug/.ClusterDebugService"

# 2. Inject CAN frames
adb shell "am broadcast -a com.byd.cluster.spi --es normal 'FF,FF,FF,FF,FF,FF,FF,FF'"

# 3. Capture cluster screenshot
adb shell "fission_screencap -d 1 -p /data/local/tmp/cluster.png"
adb pull /data/local/tmp/cluster.png
```

See [Driver Display](docs/driver-display.md) for the full UDS diagnostic protocol, CAN frame format, and ECU network topology.

---

## 🔬 Key Findings

### ✅ What Works (No Root)

| Feature | Details |
|---------|---------|
| **AC temperature reading** | `getTemprature(zone)` — zone 1/2 = set temp, zone 4 = outside/ambient |
| **Full AC control** | 40+ getter + SET methods (start/stop, temp, fan, wind mode) via permission bypass |
| **AC remote control** | `hasFeature("ACRemoteControl") = 1`, supports 10–30min timer |
| **Permission bypass** | `BydPermissionContext` (ContextWrapper) auto-grants `BYDAUTO_*` permissions client-side |
| **CAN bus read/write** | Via ADB using `app_process` + reflection |
| **75+ BYD packages** | With CAN bus access, 100+ custom `BYDAUTO_*` permissions |
| **Engine simulator sound** | CAN-writable — UI shows 3 presets but MCU accepts 1–255 |
| **AVAS preset selection** | CAN-writable — UI shows 2 but MCU accepts 0–5+ |
| **AVAH test tones** | Play on AVAS external speaker using factory diagnostic signals (`0x6E970010`) |
| **AVAS melody patterns** | 8 working patterns (doorbell, shop chime, alarm, fanfare, etc.) via `TEST_AUDIO_AVAS_SET` pitch control — [Door Sound app](https://github.com/wheregoes/byd-apps) |
| **CAN bus injection** | **VCDS-style feature coding possible** — inject CAN frames via `com.byd.cluster.spi` broadcast, no root needed |
| **Cluster screenshot** | Capture driver display via `fission_screencap -d 1 -p <file>` |
| **Instrument cluster reads** | PowerUnit, TempUnit, BacklightCtlType, InsThemeValue via BYDAutoManager |
| **DRL toggle** | `setInt(1004, 0x43100046, 1/2)` — DRL auto mode ON/OFF, confirmed by readback |
| **YUN device (1034)** | **ALL `0xAA` feature IDs accepted by MCU** — `setBuffer(1034, fid, data)` and `setInt(1034, fid, val)` return 0 (OK). Cloudmanager's private MCU channel. Needs AES encryption for actuation. |
| **Content providers** | Expose vehicle data (battery, tyre pressure, maintenance, trip consumption) |
| **Sideloading** | USB drive or ADB — [see guide](docs/sideloading-guide.md) |
### ⚠️ Partially Working

| Feature | Status |
|---------|--------|
| **Door lock status** | Main doors return INVALID (0), child lock readable. No dedicated `setDoorLockStatus()` — needs generic `set()` with unknown feature IDs |
| **360 camera** | `BYDAutoPanoramaDevice` enforced server-side, bypass fails. `AVMCamera`/`NormalCamera` exist in `bmmcamera.jar` but not loadable by third-party apps |

### ❌ What Doesn't Work

| Feature | Reason |
|---------|--------|
| **Custom AVAS audio (Boombox)** | MCU firmware blocks I2S → AVAS routing. Only 2 pitches available (TEST_AVAS 1=A, 2=B) |
| **AVAS volume control** | Hardcoded in MCU, PROMPT_VOLUME_LEVEL doesn't affect it |
| **Cluster theme/skin changes** | AutoContainer service throws "no AutoContainerNative" in single-OS mode (`fission_single_os=1`) |
| **Cluster debug commands** | Day/night, classic/tech, FPS, skins — all require AutoContainer (blocked) |
| **BYDAutoInstrumentDevice API** | Requires `BYDAUTO_INSTRUMENT_COMMON/SET` permissions — shell UID 2000 blocked |
| **Custom lock/power-on sounds** | MCU firmware rejects the commands |
| **Horn** | Hardware relay, not software controllable |
| **Hazard light flash** | MCU rejects `setInt(1004, ...)` for turn/hazard/fog. Cloud path (`server_data_to_mcu(532)`) works but needs AES encryption. YUN device (1034) accepts transport but needs encrypted data. |
| **Boot animation** | Needs root to replace (`/system/media/`) |
| **Cabin/inside temperature** | No API found — exhaustive probing confirmed unavailable |
| **Browser blob download bypass** | `fetch→blob→anchor.click` is **silently blocked** by BYD's "Download proibido" policy on firmware 13.1.32.2507250.1. No file lands, no error, no popup. Earlier claims of this working were inaccurate — see [test-log](tools/browser-exploit/test-log.md) for the full diagnosis. `navigator.share` is also `undefined` in this build. |

### 🔓 With Root (Magisk)

Root is optional — most features work without it. [Rooting Guide](docs/rooting-guide.md)

| Capability | Details |
|------------|---------|
| Direct SPI access | `/dev/spidev_ivi` — bypass 128-byte Java API limit (up to 247-byte SPI records) |
| ALSA mixer controls | Potential AVAS audio routing via `tinymix` |
| MCU config reset | Direct SPI commands to reset MCU state |
| System partition write | Modify `/system/media/` (boot animation), install system apps, replace `cluster_theme*.rcc` |
| Cluster theme replacement | Replace 132MB/124MB RCC files in `/system/lib64/`, patch `libBydCluster.so` single-OS check |
| OBD diagnostic send | `BYDAUTO_OTA_SET` + `BYDAUTO_SETTING_COMMON` permissions for full UDS coding |
| Kernel symbols | `/proc/kallsyms` access, `dmesg` |
| KernelSU | **Not viable** — requires GKI kernel 5.10+, device runs 4.14.117 (non-GKI) |

---

## 🔒 Security Findings

Key security-relevant discoveries for researchers:

| Finding | Impact |
|---------|--------|
| **Bootloader unlocked** | `ro.boot.flash.locked=0`, orange verified boot — Magisk root viable via fastboot |
| **Permission bypass** | `BydPermissionContext` overrides `enforceCallingOrSelfPermission()` — auto-grants all `BYDAUTO_*` permissions. Works for AC, DoorLock, Bodywork. Fails for Panorama (server-side IPC check) |
| **upgrade_server — no permission check** | Binder service accepts calls from UID 2000 (shell) with no SecurityException. Firmware updates could be triggered from `adb shell` with a valid signed package |
| **COTA auth cracked** | HMAC-SHA256 with character-shifted secret key. Area resolution API confirmed working (HTTP 200) |
| **Browser download block ("Download proibido")** | BYD deliberately blocks ALL browser-initiated downloads (blob, server URL, with/without user gesture) via a policy in `DownloadController`. Decompiled `com.byd.browser` confirms: no `addJavascriptInterface`/JS bridges, no bypass path. `navigator.share` is `undefined`. This prevents any browser-only APK sideloading chain. See [test-log](tools/browser-exploit/test-log.md) for full diagnosis. |
| **Port 7000 (CarPlay)** | `carplayserv` runs as **root**, listens on `0.0.0.0` — network-exposed attack surface |
| **IDD-IDPS monitoring** | Intrusion detection on `localhost:12406`, monitors `wlan0`/`rmnet` interfaces. Three root-UID clients |
| **SPI unprotected** | Packet format `[featureId_BE:4][dataLen:1][data:dataLen]` — no CRC, no HMAC |
| **CAN bus injection from shell** | `com.byd.cluster.spi` broadcast → `BYDAutoTestDevice.TEST_SIMULATE_DOWN_SET` (0xAA00020F) injects raw CAN frames. No root, no hardware — just ADB. ClusterDebug app acts as privileged proxy. VCDS-style ECU coding possible. |
| **UDS diagnostic protocol exposed** | BydDevelopmentTools.apk (`sharedUserId=android.uid.system`) contains full UDS (ISO 14229) stack — frame format, CAN domain IDs, session control, security access. Send via `BYDAutoOtaDevice.set({0xAA000140}, ...)`. |
| **Most BYDAUTO permissions** | `protectionLevel=normal` — any app can request them at install time |
| **BYDAUTO_DEVICE_YUN (1034)** | Cloud device type accepts ALL `0xAA` feature IDs via `setBuffer`/`setInt` — no MCU rejection. Cloudmanager uses this as private MCU channel. Data needs AES encryption for actuation. |
| **Cloud command path traced** | Phone app → BYD Cloud → AES TCP → `cloudmanager` → `server_data_to_mcu(532)` → `setBuffer(1034, 0xAA000005, encrypted)` → MCU → hazard lights. FuncNum 532 = find car / flash. Full protocol captured via logcat sniff. |
| **cloudmanager binary extracted** | Pulled from user's own firmware (`13.1.32.2507250.1`). Contains AES S-Box, `server_data_to_mcu` function, 532 handler with UHT/battery/speed validation. See `data/firmware-binaries/`. |

---

## 🏗️ Architecture

### Communication Stack

```
App → BYDAutoManager → Binder → DiCarServer (UID 1000) → auto.default.so → /dev/spidev_ivi → MCU
```

### Audio / AVAS Path

```
SoC → I2S → MCU DSP → A2B bus → Amplifiers / AVAS speaker
```

### Cloud Control Flow

```
BYD App → HTTPS → BYD Cloud → MQTT → cloudmanager (native) → CAN bus
```

### Cloud Command Path (Find Car / Flash Hazard)

```
Phone app → HTTPS → BYD Cloud → AES-encrypted TCP (10.168.126.25:5002)
→ cloudmanager (PID 5158, root) → server_data_to_mcu(FuncNum=532)
→ AES encrypt → setBuffer(1034, 0xAA000005, encrypted) → MCU → hazard lights
```

- **FuncNum 532** (0x0214), cmd 0x16 = find car / flash hazard
- **Device 1034** = `BYDAUTO_DEVICE_YUN` — cloud device, accepts all commands
- **Device 1005** = `BYDAUTO_DEVICE_POWER` — power domain
- AES S-Box at offset `0xc0a0` in cloudmanager binary (standard AES)
- 532 handler validates: ACC on + speed > 1 → reject, UUID dedup, UHT auth, battery < 25% → reject
- Cloud TCP server: `10.168.126.25:5002` (private APN)

### Instrument Cluster (Driver Display)

```
IVI (Android) ──fission/cbox bridge──→ Cluster (Qt OS, Qt 5.15.10 / 6.5.5)
                                              ↓
                                    libBydCluster.so (31MB)
                                    cluster_theme1.rcc (132MB)
                                    cluster_theme2.rcc (124MB)
```

- **Single-OS mode** (`fission_single_os=1`): AutoContainer service is a stub — "no AutoContainerNative"
- **Dual-OS mode** (`fission_single_os=0`): full cluster theme/skin API via `sendInfo2(8, flatBuffer)`
- **CAN injection** works in both modes: `com.byd.cluster.spi` broadcast → MCU → cluster

### CAN Bus Injection Path

```
ADB shell → am broadcast → ClusterDebug app (BYDAUTO_TEST_SET) → TEST_SIMULATE_DOWN_SET → MCU → CAN bus
```

No root, no OBD2 dongle — just ADB WiFi.

### OTA Update Paths

| Path | Method |
|------|--------|
| **COTA** | Cloud OTA — `com.byd.cota` (app config/resource updates) |
| **FOTA** | Firmware OTA — requires mutual TLS with IMEI-derived certs |
| **OTG** | USB update — looks for `msm8953_64` path (legacy, despite QCM6125 SoC) |

### Network Topology

```
Car WiFi: 192.168.10.x
Head Unit: 192.168.10.10
ADB: port 5555
CarPlay: port 7000 (root, 0.0.0.0)
IDD-IDPS: port 12406 (localhost)
```

---

## 📚 Documentation

### Guides

| Doc | Description |
|-----|-------------|
| 📱 [Sideloading Guide](docs/sideloading-guide.md) | Install apps via USB or ADB — no root needed |
| 🔬 [Sideloading Internals](docs/sideloading-internals.md) | Browser download block analysis, AftermarketInstallTool reverse engineering, country-specific whitelisting |
| 🔓 [Rooting Guide](docs/rooting-guide.md) | Magisk root via fastboot — A/B slot safety, recovery procedures |

### System Deep Dives

| Doc | Description |
|-----|-------------|
| 🖥️ [System Overview](docs/system-overview.md) | Hardware, partitions, services, network topology, open ports |
| 🔌 [BYD Auto API](docs/bydauto-api.md) | 100+ `BYDAUTO_*` permissions, device types, handler classes, protobuf schemas |
| 📊 [Content Providers](docs/content-providers.md) | CarStatusProvider URIs, schemas, consumption telemetry data format |

### Vehicle Features

| Doc | Description |
|-----|-------------|
| ❄️ [AC & Climate Control](docs/ac-climate-control.md) | Temperature zones, AC state getters/setters, encoding quirks, permission bypass code |
| 🔊 [Sound & Themes](docs/sound-and-themes.md) | Audio hardware topology, 200+ CAN signal IDs, AVAS/AVAH analysis, MCU probe results, 8 working melody patterns |
| 🖥️ [Driver Display](docs/driver-display.md) | Instrument cluster reverse engineering — Qt OS, AutoContainer bridge, CAN injection, UDS diagnostics, VCDS-style coding |
| 💡 [Light Control](docs/light-control.md) | 214 light feature IDs, DRL toggle confirmed, MCU-locked actuation, cloud command path traced, YUN device (1034), AES encryption analysis |
| 📷 [Camera System](docs/camera-system.md) | Dual camera API architecture, 360 view system, permission enforcement analysis |
| 🔄 [OTA System](docs/ota-system.md) | COTA/FOTA/OTG reverse engineering, upgrade_server vulnerability, COTA auth analysis |
| 🧪 [Decompiled APKs & Install Vectors](docs/decompiled-apks-install-vectors.md) | APK internals, install surfaces, sideload vectors |
| 🔓 [Locked/ADB-less Jailbreak Options](docs/locked-no-adb-browser-jailbreak-options.md) | Non-ADB attack surface, browser jailbreaks |
| 🌐 [Public Site Chain](docs/public-site-chain.md) | Remote/public web exploit chain research |

### NFC Digital Key

| Doc | Description |
|-----|-------------|
| 🔑 [NFC Digital Key](docs/nfc-digital-key.md) | Hardware analysis, firmware lock, IntelligentEntry app reverse engineering, CAN signals |
| ✉️ [NFC Activation Email](docs/byd-nfc-activation-email.md) | Email templates to request NFC activation from BYD — proven <24h activation |
| | → [English template](docs/nfc-activation-email-en.md) · [Português](docs/nfc-activation-email-ptbr.md) · [中文](docs/nfc-activation-email-zh.md) |

---

## 🛠️ Scripts

All scripts run on-device via `app_process`. Push and execute:

```bash
adb push scripts/BydAudioQuery.java /data/local/tmp/
adb shell "cd /data/local/tmp && app_process -Djava.class.path=. / BydAudioQuery read 0x1B10003D"
```

### CAN Bus & Audio

| Script | Purpose |
|--------|---------|
| `BydAudioQuery.java` | CAN bus read/write tool — direct signal access |
| `BydAudioRoutingTest.java` | Audio routing tests (I2S, AVAS paths) |
| `BydNavAudioTest.java` | Navigation audio channel testing |
| `BydDeviceScan.java` | Multi-device signal scanner |
| `BydAvasPlayer.java` | AVAS melody player |
| `BydCarPropertyTest.java` | DiCar/ICarPropertyService API probe (second MCU path via ContentProvider) |
| `BydAvasDeepProbe.java` | Tests newly discovered signals from CarSetting decompilation (UE_BROADCAST, HW_L1, PROMPT_VOLUME, etc.) |
| `AvasPattern.java` / `AvasMelody.java` | AVAS melody pattern players (doorbell, shop chime, alarm, fanfare) |
| `PitchTest.java` / `PitchHunt.java` / `TestAvasSweep.java` | AVAS pitch discovery — confirmed only 2 pitches (1=A, 2=B) |
| `PromptVolTest.java` / `PromptVolSingle.java` | PROMPT_VOLUME_LEVEL tests (accepted but no AVAS volume change) |
| `ClusterCmd.java` | Send AutoContainer cluster debug commands (blocked by single-OS) |
| `ClusterProbe.java` | Probe instrument cluster feature IDs via BYDAutoManager |
| `ClusterTest.java` | Targeted test of working instrument features (BacklightCtlType, InsThemeValue, PowerUnit, TempUnit) |
| `InstrumentDeviceTest.java` | Direct BYDAutoInstrumentDevice API test (blocked by permission) |
| `LightBlink.java` | Light control probe — read all states, sweep SET IDs, DRL toggle confirmed working |
| `CloudFlash.java` | Call ICloudServiceApp.sendMsg + ICloudRemoteControlService.setControlConfigure (cloud command bridge) |
| `YunTest.java` | **YUN device (1034) testing** — all 0xAA feature IDs ACCEPTED by MCU. Read/sweep/send/setint/getint. |
| `SysMix.java` | System audio mixer queries |

### AVAS & Sound

| Script | Purpose |
|--------|---------|
| `AvasRoute.java` | AVAS audio routing experiments |
| `AvasVolume.java` / `AvasVolume2.java` | AVAS volume control attempts |
| `AvahFreq.java` | AVAH frequency sweep testing |
| `AvahBare.java` | Minimal AVAH test tone trigger |
| `AvahCombo.java` | AVAH combination signal testing |
| `AvahCycle.java` | AVAH cyclic test patterns |
| `AvahIsolate.java` | AVAH signal isolation tests |
| `AvahStop.java` | AVAH signal stop/cleanup |

### MCU & System Probes

| Script | Purpose |
|--------|---------|
| `BydMcuProbe.java` | MCU security probe — feature scanning, extreme values |
| `BydBufferProbe.java` | MCU buffer overflow / boundary testing |
| `BydSpiDirect.java` | Direct SPI communication (requires root) |
| `BydDebugProbe.java` | Debug interface / hidden feature discovery |
| `BydLockSoundMonitor.java` | Door lock sound event monitoring |
| `BydCotaProbe.java` | COTA cloud API probe |

### Vehicle Systems

| Script | Purpose |
|--------|---------|
| `BydNfcKeyProbe.java` | NFC digital key CAN bus scanner |
| `BydUpgradeProbe.java` | upgrade_server Binder service probe |
| `car-telemetry.py` | Car data polling and logging (Python) |

### Chromium Flags Analysis

| Script | Purpose |
|--------|---------|
| `flags_extract.py` / `flags_extract_all.py` | Extract Chromium flags from head unit browser |
| `flags_descriptions.py` | Map flag names to descriptions |
| `flags_verify.py` / `flags_verify2.py` / `flags_verify3.py` / `flags_verify_final.py` | Verify flag states and behaviors |
| `flags_full_list.py` | Complete flag enumeration |
| `flags_probe_dom.py` | DOM-based flag probing |

---

## 📁 Repository Structure

```
docs/                       Guides and deep-dive documentation
scripts/                    On-device tools (CAN bus, MCU probes, AVAS, Chromium)
tools/
  browser-exploit/          Browser sideloading research
    index.html              Main test page — blob download bypass attempt (blocked by BYD policy)
    autodownload.html       Auto-download trigger test
    install.html            APK install flow test
    pwa.html                PWA install behavior test
    cdp_capability_audit.py CDP protocol capability audit
    cdp_download_test.py    CDP download trigger tests
    cdp_audit_results.json  Full CDP audit results
    serve_https.py          Local HTTPS server for testing
    sideload-test.apk       Mock APK for install chain testing
data/
  apks/                     Extracted system APKs (DiCarServer, CarSetting, ClusterDebug, BydThemeStore, devtools)
  audio-config/             Audio platform XML configs (I2S, mixer paths)
  car-status/               CarStatusProvider data dumps
  cluster_libs/             Cluster native libs (libBydCluster.so) — gitignored, regeneratable
  firmware-binaries/        Extracted from user's firmware (13.1.32.2507250.1) — cloudmanager, mqttserv, cloudctrlserv, libmqttserv.so
  framework/                Pulled framework.jar, services.jar, ext.jar — gitignored
  native-libs/              Native shared libraries (auto.default.so, libbydauto.so)
  packages/                 Package lists and service dumps
  permissions/              BYDAUTO permission definitions
  system-properties/        System property dumps and Android settings
  chromium_flags_*.json     Chromium flag analysis data
  mcu-probe-*.txt           MCU probe scan results
apk-analysis/               Decompiled APKs (regeneratable via jadx) + vehicle type mappings
```

Custom Android apps (Door Sound, etc.) live in [byd-apps](https://github.com/wheregoes/byd-apps).

---

## 🔧 Firmware Resources & References

### BYD Factory Images & Repair Manuals

Factory images (full system flash packages) and repair manuals for BYD models are shared by the community at:

| Resource | URL |
|----------|-----|
| Global factory images (ATTO 3) | [github.com/BYDcar/BYDGlobalFactoryImages1](https://github.com/BYDcar/BYDGlobalFactoryImages1) |
| Flash packages by chip (Di1–Di5) | [github.com/BYDcar/BYDPackagesByChip1](https://github.com/BYDcar/BYDPackagesByChip1) · [Chip2](https://github.com/BYDcar/BYDPackagesByChip2) · [Chip3](https://github.com/BYDcar/BYDPackagesByChip3) |
| Repair manuals & dashboard firmware | [github.com/BYDcar/BYDRepairManual](https://github.com/BYDcar/BYDRepairManual) |

These images contain the full Android system partition, including native binaries like `cloudmanager`, `mqttserv`, and MCU firmware — essential for offline reverse engineering when on-device extraction is blocked by SELinux.

The original sources are BYD's after-sales portals:
- Repair manuals: `http://lms.bydauto.com.cn/`
- Factory images: `http://yunpan.byd.com.cn/`

Both require a dealer or repair shop account. The GitHub repos above were sourced from Taobao/Xianyu sellers and shared freely.

> **Note:** Firmware versions vary by model, region, and trim. The Dolphin (DiLink 50, Qualcomm 665, branch `13.1.32`) shares a platform with ATTO 3, Seagull, and Yuan PLUS — but binaries may differ between versions. Always verify compatibility before flashing.

### Community

| Resource | URL |
|----------|-----|
| BYD Owners Forum (firmware tracking) | [byd.forum/dilink-versions.html](https://byd.forum/dilink-versions.html) |
| Telegram (BYD community) | [t.me/just_byd](https://t.me/just_byd) |
| XDA: BYD DiLink TWRP / root threads | [xdaforums.com](https://xdaforums.com/t/byd-song-plus-ev-twrp-needed.4586857/) |

### DiLink Platform Reference

| DiLink Version | Controller | CPU | Android | Branch | Common Models |
|---------------|------------|-----|---------|--------|---------------|
| **DiLink 50** | 13 | Qualcomm 665 | 10 | **13.1.32** | **Dolphin**, ATTO 3, Seagull, Yuan PLUS, e2 |
| DiLink 50P | 13 | Qualcomm 665 | 10 | 13.1.33 | Seal, Han, Tang |
| DiLink 100 | 23 | Qualcomm 778G | 12 | 23.1.x | N7, D9, Leopard 5, Seal |
| DiLink 150 | 34 | BYD 9000 | 13 | 34.1.x | Xia, Han L, Tang L, D9 |

Full version table at [byd.forum/dilink-versions.html](https://byd.forum/dilink-versions.html).

### USB Flashing Method

```
1. Download firmware ZIP, rename to UpdateFull.zip
2. Format USB as FAT32, create folder structure:
   BYDUpdatePackage/msm8953_64/UpdateFull.zip
3. Plug into car's USB data port while system is running
4. Auto-detects (~10 min), or force: hold "Previous track" steering button + volume wheel until screen goes black
```

---

## 📄 License

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
