# BYD Dolphin OTA Update System

## Overview

The BYD Dolphin has three OTA-related packages that handle firmware and software updates:

| Package | Type | Location | UID | Purpose |
|---------|------|----------|-----|---------|
| `com.byd.cota` | Cloud OTA | `/system/priv-app/Cota` | 1000 (system) | App config updates, cloud resource delivery |
| `com.byd.otaupdate` | Firmware OTA | `/system/app/OTAUpdate` | 1000 (system) | Online firmware updates (SOC, MCU, DSP, ECU) |
| `com.byd.otgupdate` | USB OTA | `/system/app/OTGUpdate` | 1000 (system) | USB drive-based firmware updates |

All three run as UID 1000 (system) via `sharedUser=android.uid.system`.

## System Service: upgrade_server

A system Binder service `upgrade_server` (confirmed running) provides the firmware update API:

```java
// IUpgradeServer interface (from upgradeserver-sdk.jar)
void updateIVI(String filePath, IUpgradeListener listener);  // Update head unit (Android)
void updateMcu(String filePath, IUpgradeListener listener);  // Update MCU firmware
void updateOS(String filePath, IUpgradeListener listener);   // Update OS
```

Module types defined in `UpgradeConst`:
- `"qc"` — Qualcomm/media (head unit Android)
- `"mcu"` — MCU firmware
- `"dsp"` — DSP firmware

The SDK library is at `/system/framework/upgradeserver-sdk.jar` (14KB).

Error codes (`UpgradeResultCode`):
| Code | Meaning |
|------|---------|
| 1 | Package not exist |
| 2 | Copy error |
| 3 | Unzip error |
| 4 | Config not match |
| 5 | Payload content error |
| 6 | Verify package error |
| 11 | MCU upgrade fail |
| 12 | MCU XCD file error |
| 13 | DSP XCD file error |
| 110 | Upgrade in progress |
| 999 | Other error |
| 3000 | Media upgrade success |
| 3001 | Media upgrade fail |

## Current Firmware Versions

| Component | Version | Property/Source |
|-----------|---------|----------------|
| SOC (Android) | `6125f_USER_SIGN_SW264_202507251150_Q2700` | `apps.setting.product.inswver` |
| External SW | `13.1.32.2507250.1` | `apps.setting.product.outswver` |
| Build fingerprint | `BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20250725.152222:user/release-keys` | `ro.build.fingerprint` |
| Build date | Fri Jul 25 15:22:22 CST 2025 | `ro.build.date` |
| MCU firmware | `13.5.2.2312260.1` | CAN bus 0x99000001 |
| DSP firmware | `13.5.5.2505300.2` | CAN bus 0x99000002 |
| Country code | 55 (Brazil) | `sys.byd.countrycode` |
| Last reboot | `recovery-update` | `persist.sys.rebootreason` |

## OTA System Properties

| Property | Value | Meaning |
|----------|-------|---------|
| `persist.service.ota.enable` | 0 | OTA service disabled |
| `persist.sys.byd.otaupdate` | false | OTA update not active |
| `persist.sys.byd.vehicleupdate` | true | Vehicle updates enabled |
| `persist.sys.byd.swcount` | 0 | Cyclic update counter |
| `ro.build.ab_update` | true | A/B partition updates supported |

## USB (OTG) Update — Full Protocol

### File Structure on USB Drive

```
USB Drive Root/
└── BYDUpdatePackage/
    └── msm8953_64/           ← hardcoded path (even for QCM6125 cars!)
        └── UpdateFull.zip    ← signed OTA package
```

Note: The path `msm8953_64` is hardcoded in the OTGUpdate APK. Despite our car using
QCM6125, the update app looks for this exact directory structure.

### Update Package Format

Standard Android OTA zip signed with the platform key. Must contain a `metadata` file
inside the zip with these fields:

```
post-build=BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/build_string:user/release-keys
post-outswver=13.1.32.2507250.1
post-timestamp=1753428142
```

The `post-build` string format: `{brand}/{product}/{device}:{version}/{buildId}/{buildNumber}:{variant}/{tags}`

Parsed fields: `productBrand`, `targetProduct`, `targetDevice`, `platformVersion`,
`buildId`, `buildNumber`, `buildVariant`, `versionTags`

### Verification Flow

1. **Media mounted** → `OTGUpdateReceiver` triggers (only for `device_type=usbotg`)
2. **Scan** for `UpdateFull.zip` at the hardcoded USB path
3. **Fingerprint check** — parse `metadata` from zip, verify `post-build` has all 8 fields
4. **Target device check** — compare package's `targetDevice` with device's `ro.build.fingerprint`
5. **Package verification** — `RecoverySystem.verifyPackage()` (standard Android OTA signature)
6. **Version check** — compare package version vs device version (same version = warn, downgrade = confirm)
7. **Branch check** — compare `post-outswver` branch number (3rd segment) with device's
8. **Online auth** (for sold vehicles in non-whitelist countries) — `BYDAuthManager.requestAuth(1001, ...)`
9. **Copy** to `/data/ota_package/otg/UpdateFull.zip`
10. **Reboot into recovery** — `RecoverySystem.installPackage(context, file, "udisk", !isUpgrade, "have-backlight")`

### WhiteList Countries (Skip Online Auth)

Country codes that bypass online authentication for USB updates:
```
856, 43, 32, 359, 357, 385, 420, 45, 372, 358, 33, 49, 30, 36, 353, 39,
371, 370, 352, 356, 31, 48, 351, 40, 421, 386, 34, 46, 354, 47, 41, 423,
377, 379, 44, 972, 90, 81, 82
```

These are European countries + Israel + Turkey + Japan + South Korea.
**Brazil (55) is NOT whitelisted** — sold vehicles require online auth.

Exception: vehicles with < 60 km total mileage skip online auth regardless of country.

### Vehicle Sold Detection

File `/collect2/cloudservice/salesMark.dat` containing `di_cloud_sales_m` marks vehicle as sold.
This file does not exist on our car (directory `/collect2/` not accessible).

### OTA Data Directories

| Path | Purpose |
|------|---------|
| `/data/ota_package/ota/` | Online OTA download staging |
| `/data/ota_package/otg/` | USB OTG update staging |
| `/data/ota_package/intent` | Intent file |
| `/data/ota_package/recovery/last_install` | Last install result |

These directories are not accessible from ADB shell (UID 2000).

### Cyclic Update (Factory Mode)

Property `persist.sys.byd.cyclicupdate` enables cyclic testing mode where the car
automatically re-applies updates from USB on each reboot. Counter tracked in
`persist.sys.byd.swcount`.

### Partition Hash Verification

USB drives can also contain `PartitionVerifyInfo_{swver}.txt` files for verifying
partition integrity without performing an update. The file name must match
`apps.setting.product.inswver`.

## Online Firmware OTA (com.byd.otaupdate)

### API Server

```
Production: https://fota-vehicle-global.iov.byd.auto:6113/
Reports:    https://fota-vehicle-global.iov.byd.auto:5113/
Test:       http://fota.iov.byd.com:16112/
```

Uses mutual TLS (client certificate derived from IMEI).

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `ota/otaService/register` | POST | Register device for OTA |
| `ota/otaService/loginIn` | POST | Login / authenticate |
| `ota/otaService/checkVersion` | POST | Check for available updates |
| `ota/otaService/getEcuList` | POST | Get list of ECUs |
| `ota/otaService/downloadReport` | POST | Report download status |
| `ota/otaService/submitReport` | POST | Submit update result |
| `ota/otaService/versionSync` | POST | Sync version info |
| `ota/otaService/monitorDataReport` | POST | Monitor data reporting |
| `ota/otaService/getSignature` | POST | Get signature for ECU update |
| `ota/checkVersion` | GET | Quick version check |
| `ota/ecuFileTransfer` | POST | ECU firmware transfer |
| `ota/getEcuRandom` | GET | Get random for ECU auth |
| `ota/requestReprogram` | POST | Request ECU reprogram |
| `ota/requestProgress` | GET | Get update progress |
| `ota/requestLogs` | POST | Request ECU logs |
| `ota/upgradeIfoTransfer` | POST | Transfer upgrade info |
| `api/bydota-msgreport/otaService/messageReport` | POST | Message reporting |

### Broadcast Intents

| Intent | Purpose |
|--------|---------|
| `com.byd.padota.broadcast.automatic` | Trigger automatic OTA check |
| `com.byd.carsetting.broadcast` | Car settings change (triggers OTA UI) |
| `com.byd.otaupdate.broadcast.APPOINTMENT_TIME_ARRIVING` | Scheduled update time |
| `com.byd.otaupdate.broadcast.otg` | OTG update notification |

### CAN Signal for USB Update Status

`OTA_MULTIMEDIA_USB_UPGRADE_SET` via `BYDAutoOtaDevice`:
- Value 0: default
- Value 1: finished
- Value 2: updating

## Cloud OTA / COTA (com.byd.cota)

COTA handles over-the-air configuration and resource updates (app configs, not firmware).

### API Hosts

| Brand | URL |
|-------|-----|
| Dynasty/Ocean | `https://idilink.bydauto.com.cn/` |
| Denza | `https://idilink-cn.denzacloud.com/` |
| Yangwang | `https://idilink-cn.yangwangcloud.com/` |
| F Series | `https://idilink-cn.fangchengbaocloud.com/` |
| Overseas | `https://idilink-{area}.byd.auto/` |
| Area Lookup | `http://idilink-private-global.iov.byd.auto/` |

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `vehicle-data-api/data/groupConfigs` | POST | Query app configuration updates |
| `apis/config/getEnvConfig` | GET | Get region/environment config |

### Authentication

- APP_ID: `39701099963858720`
- Signature: HMAC-SHA256 of `{secret}{nonce}{timestamp}` with secret key
- Headers include: VIN, ICCID, MCU version, SOC version, country, car series, COTA version, vehicle project ID, device type, media type, resolution, UI generation

### Cloud Config Data Paths

```
/collect2/cota/apps/{appKey}/current/params/params.json
/cust_overlay/cota/apps/{appKey}/current/params/params.json
/{custom_root}/cota/apps/{appKey}/current/params/params.json
```

### External Storage

`/sdcard/Android/data/com.byd.cota/files/`:
- `downloads/` — downloaded resources (currently empty)
- `cota-serial/` — serial data (currently empty)

## Key Permissions

### com.byd.cota (COTA)

Critical permissions granted:
- `BYDAUTO_OTA_GET` / `BYDAUTO_OTA_SET` — full OTA access
- `INSTALL_PACKAGES` — can install APKs
- `RECOVERY` — can trigger recovery mode
- `MASTER_CLEAR` — can factory reset
- `MODIFY_AUDIO_ROUTING` — can modify audio paths
- `WRITE_SECURE_SETTINGS` — can change system settings
- `SHUTDOWN` — can shut down the device
- `MANAGE_DYNAMIC_SYSTEM` — can manage DSU/GSI

### com.byd.otaupdate

- Has `com.byd.otaupdate.permission.VEHICLE_OTA_UPDATE` custom permission
- Listens for boot, shutdown, appointment, and OTG broadcasts

### com.byd.otgupdate

- `REBOOT` / `SHUTDOWN` — can reboot into recovery
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` — USB drive access
- Uses `com.byd.upgradesdk` library
- `BYDAUTO_STATISTIC_GET` — reads vehicle mileage for auth bypass check

## Decompiled Source

| APK | Size | Decompiled to |
|-----|------|---------------|
| Cota.apk | 16MB | `/tmp/cota-decompiled/` |
| OTAUpdate.apk | 6MB | `/tmp/otaupdate-decompiled/` |
| OTGUpdate.apk | 280KB | `/tmp/otgupdate-decompiled/` |
| upgradeserver-sdk.jar | 15KB | `/tmp/upgradeserver-decompiled/` |

## What Can Be Done Without Root

1. **Read OTA status** via system properties (`persist.sys.byd.otaupdate`, etc.)
2. **View build/version info** to understand current firmware state
3. **Access COTA external storage** at `/sdcard/Android/data/com.byd.cota/files/`
4. **Send OTA broadcast intents** (but actions require system-level processing)
5. **Decompile and analyze** all OTA APKs (pulled from `/system/`)
6. **Monitor OTA activity** via `logcat` filtering for OTA-related tags

## What Requires Root

1. **Access `/data/ota_package/`** — see download cache and install history
2. **Access COTA private data** — databases, shared preferences, cached configs
3. **Call `upgrade_server` methods** — `updateMcu()`, `updateIVI()`, `updateOS()`
4. **Trigger USB update manually** — requires system-level broadcast sender
5. **Read OTA certificates** — TLS client certs derived from IMEI
6. **Modify OTA properties** — `persist.sys.byd.otaupdate`, `persist.sys.byd.cyclicupdate`
