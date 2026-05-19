# BYD Dolphin Head Unit — Research & Reverse Engineering

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
| [System Overview](docs/system-overview.md) | Hardware, partitions, architecture |
| [BYD Auto API](docs/bydauto-api.md) | BYDAUTO permissions and API reference |
| [Content Providers](docs/content-providers.md) | Content provider URIs and schemas |
| [Sound & Themes](docs/sound-and-themes.md) | Audio architecture, CAN bus signals, what can/cannot be customized |
| [NFC Digital Key](docs/nfc-digital-key.md) | NFC key reverse engineering and activation analysis |
| [NFC Activation Email](docs/byd-nfc-activation-email.md) | Email templates for requesting NFC activation from BYD |
| [OTA System](docs/ota-system.md) | OTA update system (COTA/FOTA/OTG) reverse engineering |
| [Rooting Guide](docs/rooting-guide.md) | Magisk root via fastboot (optional, not required for apps) |

## Repository Structure

```
scripts/       On-device tools (CAN bus queries, MCU probes, AVAS player)
apps/          Custom Android apps (Door Sound)
docs/          Documentation and guides
data/          Raw data dumps (system properties, packages, audio config)
apk-analysis/  Extracted APK assets (vehicle type mappings)
```

## Key Findings

### What Works (No Root)

- **CAN bus read/write** via ADB using `app_process` + reflection
- **75+ BYD packages** with CAN bus access, 100+ custom BYDAUTO permissions
- **Engine simulator sound** is CAN-writable — UI shows 3 presets but MCU accepts 1-255
- **AVAS preset selection** is CAN-writable — UI shows 2 but MCU accepts 0-5+
- **AVAH test tones** play on AVAS external speaker using factory diagnostic signals
- **Content providers** expose vehicle data (battery, tyre pressure, maintenance)
- **Sideloading** works via USB drive or ADB (see guide above)

### What Doesn't Work

- **Custom AVAS audio (Boombox)** — MCU firmware blocks I2S → AVAS routing
- **AVAS volume control** — hardcoded in MCU, no CAN signal changes it
- **Custom lock/power-on sounds** — MCU firmware rejects the commands
- **Horn** — hardware relay, not software controllable
- **Boot animation** — needs root to replace (`/system/media/`)

### Architecture Discoveries

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
