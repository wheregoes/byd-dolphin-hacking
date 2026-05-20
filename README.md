# BYD Dolphin Head Unit — Research & Reverse Engineering

> **Disclaimer:** This is an unofficial, community-driven project with no affiliation,
> endorsement, or sponsorship from BYD or any of its subsidiaries. It involves reverse
> engineering of BYD's internal Android services for educational and interoperability
> purposes only. Use at your own risk — modifying vehicle software may void your warranty
> or violate BYD's terms of service. The authors assume no liability for any damage to
> your vehicle, software, or data.

Reverse engineering, documentation, and tooling for the BYD Dolphin 25/26 infotainment system (DiLink 3, Android 10).

Everything here was discovered through ADB exploration, APK decompilation, and CAN bus probing — no proprietary documentation was used.

## Head Unit Specs

| Property | Value |
|----------|-------|
| Platform | DiLink 3.0 |
| Android | 10 (API 29) |
| SoC | Qualcomm QCM6125 (SM6125) |
| Architecture | ARM64, 8 cores |
| RAM | ~3.5 GB |
| ADB | WiFi, port 5555 |
| Head Unit IP | 192.168.10.10 (car WiFi) |
| Firmware | 13.1.32.2507250.1 (Jul 25 2025) |
| MCU | 13.5.5.2505300.2 |

> **Tested on firmware 13.1.32.2507250.1.** Older versions likely work. Newer firmware updates from BYD may change or break things — no guarantees.

## Getting Started

**New here?** Start with the [Sideloading Guide](docs/sideloading-guide.md) to install apps on your BYD — no root needed.

For custom apps built on this research, see [byd-apps](https://github.com/wheregoes/byd-apps).

### Quick ADB Connection

```bash
adb connect 192.168.10.10:5555
```

## Documentation

| Doc | What's Inside |
|-----|---------------|
| [Sideloading Guide](docs/sideloading-guide.md) | Install third-party apps via USB or ADB (no root) |
| [Sideloading Internals](docs/sideloading-internals.md) | Browser exploit research: blob download bypass, install chain analysis, Chromium 113 internals |
| [System Overview](docs/system-overview.md) | Hardware, partitions, architecture |
| [BYD Auto API](docs/bydauto-api.md) | BYDAUTO permissions and API reference |
| [Content Providers](docs/content-providers.md) | Content provider URIs and schemas |
| [AC & Climate Control](docs/ac-climate-control.md) | AC temperature reading, zone mapping, climate API, permission bypass |
| [Sound & Themes](docs/sound-and-themes.md) | Audio architecture, CAN bus signals, what can/cannot be customized |
| [NFC Digital Key](docs/nfc-digital-key.md) | NFC key reverse engineering and activation analysis |
| [NFC Activation Email](docs/byd-nfc-activation-email.md) | Email templates for requesting NFC activation from BYD |
| [Camera System](docs/camera-system.md) | Camera architecture, AVMCamera/bmmcamera API, 360 view system |
| [OTA System](docs/ota-system.md) | OTA update system (COTA/FOTA/OTG) reverse engineering |
| [Rooting Guide](docs/rooting-guide.md) | Magisk root via fastboot (optional, not required for apps) |

## Repository Structure

```
scripts/              On-device tools (CAN bus queries, MCU probes, AVAS player)
docs/                 Documentation and guides
data/                 Raw data dumps (system properties, packages, audio config, Chromium flags)
tools/browser-exploit/ Browser-based sideloading research (test pages, CDP audit, mock APK)
apk-analysis/         Extracted APK assets (vehicle type mappings)
```

Custom Android apps (Door Sound, etc.) live in [byd-apps](https://github.com/wheregoes/byd-apps).

## Key Findings

### What Works (No Root)

- **AC temperature reading** — `getTemprature(zone)` reads outside temp (zone 4), set temp (zone 1/2), no permission check needed
- **Full AC control** — 40+ getter methods + SET methods (start/stop, temperature, fan, wind mode) accessible via permission bypass
- **AC remote control** — `hasFeature("ACRemoteControl") = 1`, remote AC supported with 10-30min timer
- **Permission bypass** — `BydPermissionContext` (ContextWrapper) bypasses signature-level `BYDAUTO_*` permissions client-side
- **CAN bus read/write** via ADB using `app_process` + reflection
- **75+ BYD packages** with CAN bus access, 100+ custom BYDAUTO permissions
- **Engine simulator sound** is CAN-writable — UI shows 3 presets but MCU accepts 1-255
- **AVAS preset selection** is CAN-writable — UI shows 2 but MCU accepts 0-5+
- **AVAH test tones** play on AVAS external speaker using factory diagnostic signals
- **Content providers** expose vehicle data (battery, tyre pressure, maintenance)
- **Sideloading** works via USB drive or ADB (see guide above)
- **Browser blob download bypass** — any web page can silently drop files (including APKs) to `/sdcard/Download/` via `fetch→blob→anchor.click`, no user gesture needed (Chromium 113 bug)

### Partially Working

- **Door lock status** — main doors return INVALID (0), child lock readable. No dedicated `setDoorLockStatus()` method — generic `set()` with unknown feature IDs needed
- **360 camera (Panorama)** — `BYDAutoPanoramaDevice` permissions enforced server-side, `BydPermissionContext` bypass fails. Cameras accessible via separate `AVMCamera`/`NormalCamera` from `bmmcamera.jar` (boot classpath)

### What Doesn't Work

- **Custom AVAS audio (Boombox)** — MCU firmware blocks I2S → AVAS routing
- **AVAS volume control** — hardcoded in MCU, no CAN signal changes it
- **Custom lock/power-on sounds** — MCU firmware rejects the commands
- **Horn** — hardware relay, not software controllable
- **Boot animation** — needs root to replace (`/system/media/`)

### Architecture Discoveries

- **Permission bypass**: `BydPermissionContext extends ContextWrapper` overrides `enforceCallingOrSelfPermission()` → auto-grants `BYDAUTO_*` permissions. Works for AC, DoorLock, Bodywork. Fails for Panorama (server-side IPC check)
- **Camera system**: `AVMCamera`/`NormalCamera` in `bmmcamera.jar` (boot classpath), camera IDs: FRONT, REAR, PANO_H, PANO_L, RF, DMS, FACE, CARGO, BYD_APA. Separate from `BYDAutoPanoramaDevice`
- **Cloud control flow**: BYD App → HTTPS → BYD Cloud → MQTT → native `cloudmanager` → CAN bus
- **Full SPI stack**: App → BYDAutoManager → Binder → autoservice → auto.default.so → /dev/spidev_ivi → MCU
- **A2B bus**: SoC → I2S → MCU DSP → A2B bus → amplifiers/AVAS
- **OTA system**: COTA (cloud) + FOTA (firmware) + OTG (USB), upgrade_server has no permission check
- **NFC digital key**: fully reverse-engineered but blocked by MCU firmware support flag
- **Bootloader unlocked**: `ro.boot.flash.locked=0`, Magisk root viable

## Scripts

All scripts run on-device via `app_process`. Push and execute:

```bash
adb push BydAudioQuery.java /data/local/tmp/
adb shell "cd /data/local/tmp && app_process -Djava.class.path=. / BydAudioQuery read 0x1B10003D"
```

| Script | Purpose |
|--------|---------|
| `BydAudioQuery.java` | CAN bus read/write tool |
| `BydAvasPlayer.java` | AVAS melody player (frequency control) |
| `BydMcuProbe.java` | MCU security probe (feature scanning) |
| `BydDeviceScan.java` | Multi-device signal scanner |
| `BydNfcKeyProbe.java` | NFC digital key CAN bus scanner |
| `BydCotaProbe.java` | COTA cloud API probe |
| `car-telemetry.py` | Car data polling and logging |

See `scripts/` for the full list.

## License

MIT
