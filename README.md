# BYD Dolphin Android Head Unit - Research & Hacking

Research, reverse engineering, and tooling for the BYD Dolphin 25/26 infotainment system (DiLink).

## Vehicle Info

| Property | Value |
|----------|-------|
| Model | BYD Dolphin 25/26 |
| Vehicle Type ID | 127 |
| Body Type | CAR |
| Android Version | 10 (API 29) |
| SoC | Qualcomm Trinket / SM6125 |
| Architecture | ARM64 (aarch64), 8 cores |
| RAM | ~3.5 GB |
| System Partition | Read-only, dm-verity protected |
| ADB | Wireless, port 5555 |
| Head Unit IP | 192.168.10.10 (on car WiFi) |
| Device ID | [YOUR_DEVICE_ID] |

## Repository Structure

```
.
├── docs/                          # Documentation
│   ├── system-overview.md         # Hardware, partitions, architecture
│   ├── bydauto-api.md             # BYDAUTO permission/API reference
│   ├── content-providers.md       # Content provider URIs and schemas
│   ├── sideloading-guide.md       # APK installation guide
│   └── sound-and-themes.md        # What can/cannot be customized
├── data/                          # Raw data dumps from the car
│   ├── car-status/                # CarStatusProvider data
│   ├── permissions/               # BYDAUTO permission list
│   ├── packages/                  # Installed packages and dumps
│   └── system-properties/        # Android system properties and settings
├── apk-analysis/                  # Extracted APK assets
│   ├── VehicleCarType.json        # Vehicle model ID mapping
│   └── vehicleType.json           # Vehicle body type mapping
├── scripts/                       # Utility scripts
│   ├── BydAudioQuery.java         # CAN bus read/write tool (runs on-device via app_process)
│   └── car-telemetry.py           # Car data polling and logging
└── data/apks/                     # Extracted APKs from system
    └── DiCarServer_extracted/     # DiCarServer assets (config protos, vehicle types)
```

## Quick Start

### Connect via ADB
```bash
adb connect 192.168.10.10:5555
```
You'll need to approve the connection on the car's screen on first connect.

### Install an APK
```bash
adb install ~/Downloads/SomeApp.apk
```

### Query Car Status
```bash
adb shell "content query --uri content://com.byd.carStatusProvider/car_status"
```

### Disable a System App
```bash
adb shell pm disable-user --user 0 com.example.unwantedapp
# Restart launcher to reflect changes:
adb shell am force-stop com.android.launcher3
```

## Key Discoveries

- **75+ BYD packages** pre-installed, many with CAN bus access
- **100+ custom BYDAUTO permissions** covering every vehicle subsystem
- **DiCarServer** (`com.byd.car.server`) is the central car service hub, runs as system UID 1000
- **Content providers** expose vehicle data (maintenance, energy consumption, tyre pressure)
- **Protobuf definitions** in DiCarServer define CAN bus message schemas
- **CAN bus read/write** works via ADB (`scripts/BydAudioQuery.java` using `app_process` + reflection)
- **Engine simulator sound** is CAN-writable: UI shows 3 presets but MCU accepts 1-255
- **AVAS preset selection** is CAN-writable (0x1B10003D): UI shows 2 presets but MCU accepts 0-5+
- **Vehicle Prompt Sound Source** switchable between Normal (1) and Tech (2) profiles via 0xAA000194
- **Multiple sound sources writable**: BD, INS, Radar sound sources all accept CAN writes
- **DSP OTA sound package** mechanism exists (0x99000223) — potential vector for custom sounds
- **Tesla Boombox equivalent NOT possible** — MCU firmware rejects external speaker routing (0x32B1C042)
- **Custom lock/power-on sounds NOT possible** — MCU firmware rejects (0xAA000321, 0xAA000243)
- **Test/diagnostic AVAS signals work** — MCU accepts TEST_AUDIO_AVAS_SET and TEST_MCU_AVAS_CONFIGURATION_SET
- **Horn** is hardware-controlled (physical relay, not software)
- **Boot animation** exists at `/system/media/` but requires root to replace
- **Theme system** exists via `com.byd.automultipletheme` with wallpaper/theme APIs

## Installed Modifications

| App | Purpose | Status |
|-----|---------|--------|
| PackageInstaller | APK installer UI | Installed |
| GPack Beta | BYD system update | Updated |
| Aurora Store | Alternative app store | Installed |
| MicroG GmsCore | Google Play Services replacement | Installed |
| MicroG Vending | Lightweight Play Store | Installed |
| Spotify | Pre-installed bloatware | Uninstalled (user 0) |
| Guard Manager | Car security/guard app | Installed |

## Limitations (No Root)

- Cannot write to `/system` partition (read-only, dm-verity)
- Cannot modify boot animation without root
- Cannot install GApps (system partition 100% full)
- No custom AVAS / Boombox — MCU firmware rejects external speaker routing commands
- No custom lock/power-on sounds — MCU firmware rejects these commands
- Horn is hardware-controlled (physical relay)
- Some content providers require system-level signing to query
