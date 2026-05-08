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
├── apps/                          # Custom Android apps
│   └── door-sound/                # Door event sound player app
├── docs/                          # Documentation
│   ├── system-overview.md         # Hardware, partitions, architecture
│   ├── bydauto-api.md             # BYDAUTO permission/API reference
│   ├── content-providers.md       # Content provider URIs and schemas
│   ├── ota-system.md              # OTA update system (COTA/FOTA/OTG) reverse engineering
│   ├── rooting-guide.md           # Magisk root via fastboot guide
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
│   ├── BydAudioRoutingTest.java   # AVAS external speaker routing test tool
│   ├── BydDeviceScan.java         # Multi-device signal scanner (bodywork, doorlock, OTA, test)
│   ├── BydMcuProbe.java           # MCU security probe (setBuffer, featureId scanning, extreme values)
│   ├── BydNavAudioTest.java       # Navigation I2S path audio test (QUAT_MI2S_RX)
│   ├── BydAvasPlayer.java         # AVAS melody player (frequency control via setBuffer)
│   ├── BydBufferProbe.java        # Structured buffer data tests on AVAH
│   ├── BydDebugProbe.java         # Debug range scanner, setDouble/intArray tests
│   ├── BydSpiDirect.java          # Direct SPI access tool (needs root)
│   ├── BydLockSoundMonitor.java   # Supplementary lock sound player (prototype)
│   ├── AvasRoute.java             # I2S + CAN combination attack test (QUAT_MI2S_RX → AVAS)
│   ├── AvasVolume.java            # AVAS volume control probe (FM vol, PA gain, enabler gain)
│   ├── AvasVolume2.java           # AVAS volume probe v2 (AVAH values, setBuffer encoding, presets)
│   ├── SysMix.java                # tinymix wrapper for app_process (needs root)
│   └── car-telemetry.py           # Car data polling and logging
├── data/native-libs/              # Pulled native libraries for analysis
│   ├── auto.default.so            # HAL module (MsgCodec, SPI protocol, 1MB)
│   ├── libbydautoservice.so       # Binder server stub (permission checks)
│   └── libbydauto.so              # Binder client proxy
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
- **AVAH test tones** — working on AVAS speaker (0x6E970010) with enabler commands, plays 1/2/3kHz tones
- **A2B bus architecture** confirmed: SoC → I2S → MCU DSP → A2B bus → amplifiers
- **Full SPI stack mapped**: App → BYDAutoManager → Binder → autoservice → auto.default.so (HAL) → /dev/spidev_ivi → MCU
- **No per-packet signing** on regular commands — only MD5 for OTA. Direct SPI access possible
- **setBuffer accepts 1-128 bytes** on AVAH featureId — MCU processes small binary buffers
- **0x6E990010 (debug AVAH)** discovered — new command in audio debug range, accepts setInt
- **Audio debug mode** (0x6E990008=1) does NOT unlock routing — MCU firmware hardcodes rejection
- **MCU accepts ALL int values** on AVAH without error — truncates internally
- **Tesla Boombox equivalent** — MCU rejects direct routing (0x32B1C042), debug mode doesn't help
- **AVAS volume is fixed** — tested FM vol, PA gain, enabler gain, AVAH values 1-255, setBuffer encoding, AVAS presets. All produce identical volume. Hardcoded in MCU firmware
- **I2S audio cannot reach AVAS** — dual I2S buses (TERT+QUAT) both route to cabin only. Tested AVAH enablers + AudioTrack (QUAT_MI2S_RX), UE channel, kitchen sink, PCM streaming. MCU has hard separation between I2S input and AVAS tone generator
- **BYD custom audio flags** — `AUDIO_OUTPUT_FLAG_NAVI|UE|GAODE` found in audio policy config. "UE" matches CAN signal UE_MUTE but unmuting does not enable external speaker routing
- **tinymix blocked without root** — app_process runs as UID 2000, ALSA devices require UID 1000/audio group
- **Magisk is the only root option** — KernelSU requires GKI kernel 5.10+ (ours is 4.14 non-GKI). Magisk v25210 recommended for BYD
- **No matching stock firmware found** — GitHub firmware is for msm8953, car uses QCM6125 (13.5.x)
- **OTA system fully mapped** — 3 packages (COTA/FOTA/OTG), upgrade_server Binder service, USB update path hardcoded to `/BYDUpdatePackage/msm8953_64/UpdateFull.zip`
- **FOTA API identified** — `fota-vehicle-global.iov.byd.auto:6113` with mutual TLS (IMEI-derived cert), 17 endpoints mapped
- **COTA cloud API** — `idilink-{area}.byd.auto` with HMAC-SHA256 auth, APP_ID `39701099963858720`
- **USB update requires online auth** — Brazil (55) not whitelisted; vehicles <60km mileage bypass auth
- **Car was recently OTA-updated** — `persist.sys.rebootreason=recovery-update`, build date Jul 25 2025
- **Custom lock/power-on sounds NOT possible** — MCU firmware rejects (0xAA000321, 0xAA000243)
- **Test/diagnostic AVAS signals work** — MCU accepts TEST_AUDIO_AVAS_SET and TEST_MCU_AVAS_CONFIGURATION_SET
- **Horn** is hardware-controlled (physical relay, not software)
- **Bootloader is UNLOCKED** — `ro.boot.flash.locked=0`, `verifiedbootstate=orange`, Magisk root viable via fastboot
- **Security patch level 2023-02-05** — 3+ years behind, but kernel exploits blocked by SELinux from shell
- **KGSL GPU driver accessible** — /dev/kgsl-3d0 world-writable, Adreno 610 ioctls respond, but context creation blocked
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

## Root Status

**Bootloader is UNLOCKED** but root was **not pursued**. Magisk via fastboot is viable
if needed in the future. KernelSU is NOT compatible (kernel 4.14 is non-GKI).
No matching stock firmware exists online — boot image must be extracted from device first.

See `docs/rooting-guide.md` for full procedure and `docs/sound-and-themes.md` →
"Privilege Escalation Assessment" for security analysis.

## Limitations (No Root)

- Cannot write to `/system` partition (read-only, dm-verity)
- Cannot modify boot animation without root
- Cannot install GApps (system partition 100% full)
- No custom AVAS / Boombox — MCU has hard separation between I2S audio and AVAS tone generator
- AVAS volume is fixed in MCU firmware — no CAN signal changes it
- No custom lock/power-on sounds — MCU firmware rejects these commands
- Horn is hardware-controlled (physical relay)
- Some content providers require system-level signing to query
- tinymix/ALSA mixer inaccessible without root (UID 2000 vs system:audio)
