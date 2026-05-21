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
| MCU | `13.5.5.2505300.2` |
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
| **Content providers** | Expose vehicle data (battery, tyre pressure, maintenance, trip consumption) |
| **Sideloading** | USB drive or ADB — [see guide](docs/sideloading-guide.md) |
| **Browser blob download bypass** | `fetch→blob→anchor.click` silently drops files (including APKs) to `/sdcard/Download/` — no user gesture needed (Chromium 113 bug) |

### ⚠️ Partially Working

| Feature | Status |
|---------|--------|
| **Door lock status** | Main doors return INVALID (0), child lock readable. No dedicated `setDoorLockStatus()` — needs generic `set()` with unknown feature IDs |
| **360 camera** | `BYDAutoPanoramaDevice` enforced server-side, bypass fails. `AVMCamera`/`NormalCamera` exist in `bmmcamera.jar` but not loadable by third-party apps |

### ❌ What Doesn't Work

| Feature | Reason |
|---------|--------|
| **Custom AVAS audio (Boombox)** | MCU firmware blocks I2S → AVAS routing |
| **AVAS volume control** | Hardcoded in MCU, no CAN signal changes it |
| **Custom lock/power-on sounds** | MCU firmware rejects the commands |
| **Horn** | Hardware relay, not software controllable |
| **Boot animation** | Needs root to replace (`/system/media/`) |
| **Cabin/inside temperature** | No API found — exhaustive probing confirmed unavailable |

### 🔓 With Root (Magisk)

Root is optional — most features work without it. [Rooting Guide](docs/rooting-guide.md)

| Capability | Details |
|------------|---------|
| Direct SPI access | `/dev/spidev_ivi` — bypass 128-byte Java API limit (up to 247-byte SPI records) |
| ALSA mixer controls | Potential AVAS audio routing via `tinymix` |
| MCU config reset | Direct SPI commands to reset MCU state |
| System partition write | Modify `/system/media/` (boot animation), install system apps |
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
| **Browser blob bypass** | Chromium 113: `fetch()→blob→anchor.click` bypasses BYD's download block from **any web page** — remote exploit, no ADB needed. 52MB APK verified |
| **Port 7000 (CarPlay)** | `carplayserv` runs as **root**, listens on `0.0.0.0` — network-exposed attack surface |
| **IDD-IDPS monitoring** | Intrusion detection on `localhost:12406`, monitors `wlan0`/`rmnet` interfaces. Three root-UID clients |
| **SPI unprotected** | Packet format `[featureId_BE:4][dataLen:1][data:dataLen]` — no CRC, no HMAC |
| **Most BYDAUTO permissions** | `protectionLevel=normal` — any app can request them at install time |

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
| 🔬 [Sideloading Internals](docs/sideloading-internals.md) | Browser exploit chain, blob download bypass, AftermarketInstallTool reverse engineering, country-specific whitelisting |
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
| 🔊 [Sound & Themes](docs/sound-and-themes.md) | Audio hardware topology, 200+ CAN signal IDs, AVAS/AVAH analysis, MCU probe results |
| 📷 [Camera System](docs/camera-system.md) | Dual camera API architecture, 360 view system, permission enforcement analysis |
| 🔄 [OTA System](docs/ota-system.md) | COTA/FOTA/OTG reverse engineering, upgrade_server vulnerability, COTA auth analysis |

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
| `SysMix.java` | System audio mixer queries |

### AVAS & Sound

| Script | Purpose |
|--------|---------|
| `BydAvasPlayer.java` | AVAS melody player (frequency control) |
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

### Vehicle Systems

| Script | Purpose |
|--------|---------|
| `BydNfcKeyProbe.java` | NFC digital key CAN bus scanner |
| `BydCotaProbe.java` | COTA cloud API probe — area resolution, auth testing |
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
    index.html              Main test page — blob download bypass PoC
    autodownload.html       Auto-download trigger test
    install.html            APK install flow test
    pwa.html                PWA install behavior test
    cdp_capability_audit.py CDP protocol capability audit
    cdp_download_test.py    CDP download trigger tests
    cdp_audit_results.json  Full CDP audit results
    serve_https.py          Local HTTPS server for testing
    sideload-test.apk       Mock APK for install chain testing
data/
  apks/                     Extracted system APKs (DiCarServer, CarSetting)
  audio-config/             Audio platform XML configs (I2S, mixer paths)
  car-status/               CarStatusProvider data dumps
  native-libs/              Native shared libraries (auto.default.so, libbydauto.so)
  packages/                 Package lists and service dumps
  permissions/              BYDAUTO permission definitions
  system-properties/        System property dumps and Android settings
  chromium_flags_*.json     Chromium flag analysis data
  mcu-probe-*.txt           MCU probe scan results
apk-analysis/               Vehicle type mappings (VehicleCarType.json, vehicleType.json)
```

Custom Android apps (Door Sound, etc.) live in [byd-apps](https://github.com/wheregoes/byd-apps).

---

## 📄 License

MIT
