# System Overview - BYD Dolphin 25/26

## Hardware

- **SoC**: Qualcomm Trinket (SM6125 / QM215)
- **Architecture**: ARM64 (aarch64), 8 cores
- **RAM**: ~3.5 GB usable
- **Display**: Landscape, rotatable (supports portrait via `BydAutoPhoto`)
- **Connectivity**: WiFi (STA + AP), Bluetooth, 4G/LTE, GPS, NFC
- **Cameras**: Reverse camera, surround view (panorama)
- **Instrument Cluster**: Separate QML/Qt-based system (`FissionCluster`) communicating via IPC

## Software

- **Android Version**: 10 (API 29)
- **Build Type**: user (production)
- **Security Patch**: Varies by OTA
- **Launcher**: `com.android.launcher3` (customized)
- **Input Method**: `com.android.inputmethod.latin` (BydLatinIME)
- **Voice Assistant**: `com.byd.vrassistant` (BydVoice) with Mandarin, Cantonese, English support

## Partition Layout

| Partition | Mount | Filesystem | Notes |
|-----------|-------|------------|-------|
| /system | /system | ext4 | Read-only, dm-verity, ~100% full |
| /vendor | /vendor | ext4 | Read-only, Qualcomm HALs |
| /data | /data | ext4/f2fs | Read-write, user data |
| /product | /product | ext4 | Read-only, additional media/apps |
| /oem | /oem | ext4 | Read-only, OEM customizations |

## Key System Services

### DiCarServer (`com.byd.car.server`)
- **UID**: 1000 (system)
- **Flags**: SYSTEM, PERSISTENT
- Central hub for all vehicle data
- Bridges Android apps to CAN bus via property handlers
- Contains protobuf definitions for CAN message parsing
- Exposes content providers for vehicle data

### CarStatusProvider (`com.byd.providers.carstatus`)
- **Authority**: `com.byd.carStatusProvider`
- Stores and serves vehicle status data
- Queryable via `content://com.byd.carStatusProvider/car_status`

### CarSettingsProvider (`com.byd.providers.carsettings`)
- **Authority**: `com.byd.providers.carsettings`
- Vehicle configuration and user preferences
- Handlers: Config, Global, SystemSetting, UserTableData, TravelInfo, DiCareRecord

### CanDataCollect (`com.byd.CanDataCollect`)
- CAN bus raw data collection service
- Has `BYDAUTO_BODYWORK_COMMON` and `BYDAUTO_POWER_GET` permissions
- Sends data via network (has INTERNET permission)

### CustomServer (`com.byd.customserver`)
- Privileged service with REBOOT, SET_TIME, FORCE_STOP_PACKAGES
- Handles system-level operations

## Instrument Cluster

The instrument cluster runs a separate Qt/QML application stack:
- Located at `/vendor/FissionCluster_5_15_10/` and `/vendor/FissionCluster_6_5_5/`
- Uses Qt 5.15.10 and 6.5.5 (two versions present)
- QML modules: QtQuick, QtCharts, QtDataVisualization, Qt3D, QtGraphicalEffects
- Communicates with head unit via IPC

## Network

- **Car WiFi**: Head unit acts as AP (192.168.10.x subnet)
- **Head Unit IP**: 192.168.10.10
- **ADB Port**: 5555 (wireless)
- **Cloud Services**: `com.byd.cloudserviceapp` for BYD cloud
- **MQTT**: Has `BYDAUTO_MQTT_GET/SET` permissions (likely for remote telemetry)

### TCP Services

| Port | Bind Address | UID | Process | Purpose |
|------|-------------|-----|---------|---------|
| 5555 | all (tcp6) | shell | adbd | ADB wireless debug |
| 7000 | 0.0.0.0 + :: | 0 (root) | carplayserv | Apple CarPlay wireless (IAP2 over TCP) |
| 8191 | 127.0.0.1 | 2000 (shell) | hbs | Local HTTP server (toolkit) |
| 9191 | 127.0.0.1 | 2000 (shell) | - | HTTPS test server (user-deployed) |
| 12406 | 127.0.0.1 | 1000 (system) | idd-idps-server | IDD Intrusion Detection/Prevention |
| 14002 | :: (tcp6) | - | - | BYD service (heavy traffic, many connections) |
| 14003 | :: (tcp6) | - | - | BYD service |
| 14004 | :: (tcp6) | - | - | BYD service |
| 14006 | :: (tcp6) | shell | hbs | HBS service |
| 14041 | :: (tcp6) | - | - | BYD service |
| 43609 | :: (tcp6) | - | - | Dynamic port |

**Port 7000 (CarPlay)**: Runs as root, listens on ALL interfaces. Started via `carplay.rc` when `sys.carplay.support=1`. Binary at `/system/bin/carplayserv`. CarPlay state tracked via `sys.carplay.*` properties. Currently not connected (`sys.carplay.connected=0`). Potential attack surface as it's network-exposed.

**Port 12406 (IDD-IDPS)**: BYD's Intelligent Driving Data - Intrusion Detection & Prevention System. Localhost only. Server binary at `/system/bin/idd-idps-server` (UID system, groups: system inet media_rw). Three root-UID clients connect to it: `idd-metrics` (PID 322), `idd-nidps-engine` (PID 326, monitors wlan0/rmnet interfaces), `idd-nidps-manage` (PID 3452). Data stored at `/data/idd/`. Config at `/etc/idd/rule/idd-nidps-engine/conf/idd-idps.yaml`.

## Voice Assistant

BYD's custom voice assistant (`BydVoice`) with:
- Wake word detection (MVW - Multi Voice Wakeup)
- Speech recognition (SR)
- Sound wave frontend (SWF)
- Language packs at `/system/etc/BydVoice/data/`
- Supports: Mandarin, Cantonese, English
