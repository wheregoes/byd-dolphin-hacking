# BYDAUTO API Reference

The BYD head unit defines 100+ custom Android permissions that map to vehicle subsystems. These permissions gate access to CAN bus data through the DiCarServer service.

## Permission Naming Convention

```
android.permission.BYDAUTO_{SUBSYSTEM}_{GET|SET|COMMON}
```

- `GET` — read data from the subsystem
- `SET` — write/control the subsystem
- `COMMON` — both read and write

## Vehicle Subsystems

### Powertrain

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_ENGINE_GET/SET` | ICE engine data (RPM, temp, status) |
| `BYDAUTO_MOTOR_GET/SET` | Electric motor data |
| `BYDAUTO_ENERGY_GET/SET/COMMON` | Battery/fuel energy levels |
| `BYDAUTO_CHARGING_GET/SET/COMMON` | Charging status, current, voltage, schedule |
| `BYDAUTO_GEARBOX_GET/COMMON` | Gear position (P/R/N/D) |
| `BYDAUTO_SPEED_GET/SET/COMMON` | Vehicle speed |
| `BYDAUTO_POWER_GET/SET` | Power management, ACC/ON/START states |

### Body & Exterior

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_DOOR_LOCK_GET/SET/COMMON` | Door lock/unlock state |
| `BYDAUTO_LIGHT_GET/SET/COMMON` | Headlights, tail lights, indicators, interior lights |
| `BYDAUTO_BODYWORK_GET/SET/COMMON` | Windows, mirrors, trunk, hood |
| `BYDAUTO_WIPER_GET/SET` | Windshield wipers |
| `BYDAUTO_REAR_VIEW_MIRROR_GET/SET` | Electronic rear view mirror |

### Safety & ADAS

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_ADAS_GET/SET` | Advanced driver assistance systems |
| `BYDAUTO_COLLISION_GET/SET` | Collision detection/avoidance |
| `BYDAUTO_RADAR_GET/SET/COMMON` | Parking radar / ultrasonic sensors |
| `BYDAUTO_SAFETY_BELT_GET/SET/COMMON` | Seatbelt status |
| `BYDAUTO_PANORAMA_GET/SET/COMMON` | 360 surround view cameras |

### Climate & Comfort

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_AC_GET/SET/COMMON` | Air conditioning, heating, ventilation |
| `BYDAUTO_PM2P5_GET/SET/COMMON` | Cabin air quality (PM2.5 sensor) |
| `BYDAUTO_CPUTEMPRATURE_SET` | Head unit CPU temperature management |

### Sensors & Data

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_SENSOR_GET/SET` | Various vehicle sensors |
| `BYDAUTO_TYRE_GET/SET/COMMON` | Tyre pressure monitoring (TPMS) |
| `BYDAUTO_LOCATION_GET/SET` | GPS position data |
| `BYDAUTO_VEHICLE_DATA_GET/SET` | Generic vehicle data |
| `BYDAUTO_DTC_GET/SET` | Diagnostic Trouble Codes (OBD) |
| `BYDAUTO_GB_GET` | Chinese GB standard data (likely GB/T 32960) |
| `BYDAUTO_BIGDATA_GET` | Vehicle big data / analytics |
| `BYDAUTO_STATISTIC_GET/SET/COMMON` | Usage statistics |

### Infotainment

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_AUDIO_GET/SET/COMMON` | Audio routing, volume, source |
| `BYDAUTO_VIDEO_GET/SET` | Video playback control |
| `BYDAUTO_MULTIMEDIA_GET/SET/COMMON` | Media center control |
| `BYDAUTO_RADIO_GET/SET` | FM/AM radio |
| `BYDAUTO_BLUETOOTH_GET` | Bluetooth audio/phone |
| `BYDAUTO_BLUTOOTH_SET` | Bluetooth settings (note: typo in original) |
| `BYDAUTO_PHONE_GET/SET` | Phone call integration |
| `BYDAUTO_AUX_GET/SET` | Auxiliary audio input |
| `BYDAUTO_RSE_GET/SET` | Rear seat entertainment |

### System & Config

| Permission | Description |
|-----------|-------------|
| `BYDAUTO_SETTING_GET/SET/COMMON` | Vehicle settings |
| `BYDAUTO_OTA_GET/SET` | Over-the-air updates |
| `BYDAUTO_VERSION_GET/SET` | Software/firmware versions |
| `BYDAUTO_INSTRUMENT_GET/SET/COMMON` | Instrument cluster data |
| `BYDAUTO_TIME_GET/SET/COMMON` | System time (synced with car) |
| `BYDAUTO_SIGNAL_SET` | Signal/indicator control |
| `BYDAUTO_MQTT_GET/SET` | MQTT messaging (cloud telemetry) |
| `BYDAUTO_YUN_GET` | Cloud/Yun service data |
| `BYDAUTO_SPECIAL_GET` | Special/debug data |
| `BYDAUTO_TEST_GET/SET` | Test/diagnostic mode |
| `BYDAUTO_QCFS_GET/SET` | Qualcomm filesystem operations |
| `BYDAUTO_FUNCNOTICE_GET/SET` | Function notifications |
| `BYDAUTO_REMINDER_GET/SET` | System reminders |
| `BYDAUTO_RESCUE_GET/SET` | Emergency/rescue services |

### Data Collection

| Permission | Description |
|-----------|-------------|
| `BYDACQUISITION_SEND_BUFFER` | Send buffered acquisition data |
| `BYDACQUISITION_SEND_FILE` | Send acquisition data files |
| `BYDDIAGNOSTIC_SEND_BUFFER` | Send diagnostic data |

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Android Apps                     │
│  (Aurora Store, Custom Apps, BYD Apps)           │
├─────────────────────────────────────────────────┤
│           BYDAUTO Permission Layer               │
│  (android.permission.BYDAUTO_*)                  │
├─────────────────────────────────────────────────┤
│              DiCarServer                         │
│  com.byd.car.server (UID 1000)                   │
│  ┌──────────────┬──────────────────────────┐     │
│  │ PropertyHandler │ CarSettingsHandler     │     │
│  │ AudioHandler    │ HalFeatureHandler      │     │
│  │ ExpandHandler   │                        │     │
│  └──────────────┴──────────────────────────┘     │
├─────────────────────────────────────────────────┤
│            CAN Bus / HAL Layer                   │
│  (vendor libs, Qualcomm HALs)                    │
├─────────────────────────────────────────────────┤
│          BCM / MCU / ECUs                        │
│  (Body Control, Motor Control, etc.)             │
└─────────────────────────────────────────────────┘
```

## DiCarServer Property Handlers

Extracted from APK assets:

| Handler Class | Domain |
|--------------|--------|
| `com.byd.audio.AudioFeatureHandler` | Audio routing and control |
| `com.byd.carsettings.CarSettingsHandler` | Vehicle settings |
| `com.byd.auto.HalFeatureHandler` | Hardware abstraction layer |
| `com.byd.car.ExpandFeatureHandler` | Extended/custom features |

## CarSettings Handler Implementations

| Handler | Domain |
|---------|--------|
| `ConfigHandler` | Vehicle configuration |
| `GlobalHandler` | Global system settings |
| `SystemSettingHandler` | Android system settings bridge |
| `UserTableDataHandler` | User preferences |
| `TravelInfoHandler` | Trip/travel data |
| `DiCareRecordHandler` | Maintenance records |

## Content Provider URIs

See [content-providers.md](content-providers.md) for queryable URIs.

## Notes for App Development

1. Third-party apps need to declare BYDAUTO permissions in their AndroidManifest.xml
2. Most BYDAUTO permissions have `protectionLevel=normal`, meaning any app can request them
3. The DiCarServer runs as UID 1000 (system) and acts as a bridge to the CAN bus
4. Data flows: App -> BYDAUTO permission check -> DiCarServer -> CAN bus -> ECU
5. Protobuf (.proto) files in DiCarServer define the CAN message schemas (binary format)
