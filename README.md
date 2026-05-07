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
└── scripts/                       # Utility scripts
    └── car-telemetry.py           # Car data polling and logging
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
- **Lock/unlock/AVAS sounds** are NOT controlled by the head unit (BCM/MCU domain)
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
- Lock/unlock/horn/AVAS sounds controlled by BCM, not Android
- Some content providers require system-level signing to query
