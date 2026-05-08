# BYD Dolphin NFC Digital Key — Deep Analysis

## Summary

The BYD Dolphin GS 25/26 has full NFC digital key **hardware and software** present but the feature is **locked at the MCU firmware level**. The infotainment app (`com.byd.intelligententry`) has complete NFC key support code for both China and overseas markets, and the cloud activation broadcast mechanism works from ADB shell. However, the MCU reports NFC feature support value `3` while the app only accepts value `1`, creating a firmware-level gate that cannot be bypassed without root or a server-side activation from BYD.

## Hardware Status

| Component | Status | Details |
|-----------|--------|---------|
| NFC Chip | **Present** | NXP, connected via I2C |
| NFC HAL | **Running** | `vendor.nxp.hardware.nfc@1.2-service` (PID 120) |
| Android NFC Service | **NOT running** | `dumpsys nfc` → "Can't find service" |
| Secure Element | **Present** | `secure_element` system service running |
| NFC Device Nodes | **Missing** | No `/dev/nq-nci` or `/dev/nfc*` |
| External NFC (mirror) | **Unknown** | 2024 Brazilian models have it per forum reports |
| Internal NFC (console) | **Unknown** | 2025 models have it per Chinese spec |

System properties:
```
ro.nfc.port = I2C
```

## Software Architecture

### IntelligentEntry App

| Property | Value |
|----------|-------|
| Package | `com.byd.intelligententry` |
| Version | 1.0.0.250711.1 |
| Location | `/system/app/IntelligentEntry/` |
| UID | 1000 (system) |
| Flags | PERSISTENT |
| PID | Running at boot (confirmed PID 3402) |
| Platform | "abroad" flavor, DI3.0 |
| Key types | PHONE, NFC_abroad (overseas); NFC_china, PALM, BLE (China) |

### Supported Key Types (KeyTypeRecyclerView)

| Key | China | Overseas | Description |
|-----|-------|----------|-------------|
| NFC_china | Yes | — | NFC via BYD app (China ecosystem) |
| NFC_abroad | — | Yes | NFC via Apple Wallet / 3rd party |
| PALM | Yes | — | Palm vein recognition |
| BLE | Yes | — | Bluetooth Low Energy |
| PHONE | — | Yes | Phone as key (BLE-based) |

### Cloud Activation Flow

```
BYD Cloud → CloudManager App → broadcast → IntelligentEntry → CAN Bus → MCU
                                  ↑
                                  ↓
                   com.byd.cloudmanager.broadcast.23 (JSON)
                   com.byd.cloudmanager.receiver (response)
```

APPID = 23 (IntelligentEntry)

#### Broadcast Commands

| cmd | Purpose | Data format |
|-----|---------|-------------|
| 1 | Set user switch configs | Hex-encoded byte array (user hash + key types) |
| 2 | Delete specific users | Hex-encoded byte array |
| 3 | Clear all configs | Single byte (0 = clear) |
| 4 | Update operation code | Byte array (physical key op code) |
| 5 | Delete operation code | Byte array (user hashes to delete) |
| 6 | Sync local switch configs | (outgoing to cloud) |
| 7 | Sync physical key configs | (outgoing to cloud) |
| 8 | Set userId | `{"userId": "..."}` |
| 9 | Unlock notification | `{"isUnlock": 1}` |
| 10 | Sync intelligence ability | (outgoing to cloud) |

#### cmd=1 Data Format (Switch Config)

```
Byte 0:     number of user groups
Per group:
  Byte:     padding/unused
  Byte[8]:  userIdHash (8 bytes, hex-encoded in config)
  Byte:     number of keys for this user
  Per key:
    Byte:   keyType (2 = NFC_abroad)
    Byte:   isEnable (1 = enabled, 0 = disabled)
```

Example: `01000102030405060708010201` = 1 user, hash=0102030405060708, 1 key, type=2 (NFC), enabled

## CAN Bus Signals

### NFC Control Signals (DI3.0 / 6125f)

| Signal | Direction | Purpose | Our Value |
|--------|-----------|---------|-----------|
| `0x43600028` | MCU→SOC | NFC feature support (1=yes) | **3** (app needs 1) |
| `0x2EF0002C` | SOC→MCU | NFC switch status (1=on, 2=off) | Writable (writes accepted) |
| `0x2EF0002A` | SOC→MCU | NFC switch flag | Writable |
| `0x43F04028` | SOC→MCU | Physical key init (3=init, 1=on, 2=off) | Writable |
| `0x38400046` | MCU→SOC | Physical key opcode switch read | 0 |
| `0x2F4001C4` | MCU→SOC | NFC support check 2 | 0 |
| `0x40334013` | MCU→SOC | NFC key state (1=active) | 0 |
| `0x4036B020` | MCU→SOC | NFC unlock source | 0xFFFF (unset) |
| `0x4036B030` | MCU→SOC | NFC execute result | 0xFFFF (unset) |
| `0x3E805010` | MCU→SOC | Bluetooth unlock listener | 0xFFFF |
| `0x99000037` | MCU→SOC | Screen on/off for IE | 1 (on) |

### Apple NFC Signals

| Signal | Purpose |
|--------|---------|
| `0x18C36010` | NFC unlock start time set |
| `0x18C36013` | Start check Apple key |
| `0x18C36015` | Register Apple key |
| `0x4360000A` | Apple NFC configuration |

### Operation Code Verification

| Signal | Purpose |
|--------|---------|
| `0xAA000319` | Request random number (write userIdHash) |
| `0xAA000320` | Verify code hash |
| `0x1A6FE018` | Checksum response |
| `0x1A6FE020` | Command response |
| `0x1A6FE028` | Result response |
| `0x1A6FE030` | Random data buffer → returns `MCU_OFFLINE` |

### Cloud (Yun) NFC Signals

| Signal | Purpose |
|--------|---------|
| `0x99000155` | NFC key separate data (read) |
| `0x99000159` | NFC short reply (read) |
| `0xAA000155` | NFC key separate data (write) |
| `0xAA000159` | NFC data diagnosis (write) |

### NFC Key Serial Number

| Signal | Purpose |
|--------|---------|
| `0x99000168` | NFC key serial number |

## Probe Results (2026-05-07)

### Cloud Broadcast Test — WORKS

The broadcast `com.byd.cloudmanager.broadcast.23` is **receivable from ADB shell**.
IntelligentEntry processes all commands:

```
# Tested cmd=1 (set user config)
$ am broadcast -a com.byd.cloudmanager.broadcast.23 \
    --es json '{"cmd":1,"data":"01000102030405060708010201"}'

# Result: IntelligentEntry parsed config, stored it, sent CAN bus commands
# Logcat: "byteArrayToConfigList: list=[{isEnable:true, keyType:2, userIdHash:0102030405060708}]"
# Logcat: "sendCommandResponse: response=CommandResponse(cmd=1, result=true)"
```

```
# Tested cmd=9 (unlock notification)
$ am broadcast -a com.byd.cloudmanager.broadcast.23 \
    --es json '{"cmd":9,"data":"{\"isUnlock\":1}"}'

# Result: keyType set to 2 (NFC_abroad), physical key init sent to MCU
# Logcat: "onGetKeyType: 获取到解锁钥匙类型，keyType=2"
# CAN: 0x43F04028 = 3 (init) → success
```

### NFC Feature Support — VALUE MISMATCH

```
0x43600028 = 3   (MCU reports 3)
IntelligentEntry checks: value == 1 → isSupport = false
Result: NFC switch set to 0 (disabled)
```

The MCU's support value `3` is not recognized by the IntelligentEntry app as "supported"
(only value `1` triggers `isSupport=true`). This is the primary blocker.

Writing `0x43600028 = 1` from ADB: **REJECTED** (error -2147482648, read-only signal).

### Operation Code Buffer — MCU_OFFLINE

```
0x1A6FE030 = 000000004D43555F4F46464C494E45
           = \x00\x00\x00\x00MCU_OFFLINE
```

The NFC operation code subsystem reports "MCU_OFFLINE" — the NFC module hasn't been initialized.

### CAN Bus Write — ACCEPTED

```
0x2EF0002C = 1 → write result: 0 (success)
0x2EF0002A = 0 → write result: 0 (success)
0x43F04028 = 3 → write result: 0 (success)
```

The MCU accepts CAN bus writes to NFC control signals, but without `isSupport=true`
in the IntelligentEntry app, these writes are immediately overridden to 0 by the app.

## Activation Analysis

### Why NFC Isn't Working

1. **MCU firmware gate**: `0x43600028` returns `3` (hardware present but feature locked). App needs `1` (feature enabled).
2. **Android NFC service not started**: The NFC Android framework service isn't running despite NFC HAL being active.
3. **No NFC device nodes**: `/dev/nq-nci` doesn't exist, suggesting the kernel driver isn't loading.
4. **Operation code subsystem offline**: MCU reports "MCU_OFFLINE" for the NFC verification subsystem.

### What BYD's Server-Side Activation Likely Does

Based on forum reports (dolphinbyd.com.br) where emailing db@byd.com activates the feature within 1 day:

1. **MCU firmware update** — Changes `0x43600028` from `3` → `1`, enabling NFC support
2. **And/or COTA config push** — Pushes cloud activation config via CloudManager → IntelligentEntry
3. **And/or Android NFC service enable** — Starts the NFC Android service (possibly via system property change)

The full activation requires MCU cooperation — it's not just a software toggle.

### Possible Self-Activation Paths

| Path | Feasibility | Risk | Notes |
|------|-------------|------|-------|
| Email BYD China (db@byd.com) | **HIGH** | None | Proven to work within 1 day per forum reports |
| Patch IntelligentEntry APK | Medium | Low | Change `== 1` to `>= 1` check, but requires system-signed APK or root |
| COTA config push | Low | Medium | We have API auth but don't know the config key for NFC enable |
| MCU firmware modify | Very Low | **HIGH** | Would require extracting, patching, and flashing MCU firmware |
| Direct CAN bus write | Low | Low | Writes accepted but MCU doesn't respond to NFC without activation |

### Recommended Approach

**Email BYD China at db@byd.com** requesting NFC digital key activation. Include:
- VIN (or device ID: `[YOUR_DEVICE_ID]`)
- Vehicle model: Dolphin GS 25/26
- Country: Brazil
- Firmware version: `13.1.32.2507250.1`
- Request: Enable NFC digital key (Apple Wallet / phone key)

This has been confirmed working by multiple Brazilian Dolphin owners on the forum within ~1 day.

## Decompiled Source Reference

| APK | Size | Decompiled to | Purpose |
|-----|------|---------------|---------|
| IntelligentEntry.apk | 5.4MB | `/tmp/intelligententry-decompiled/` | NFC key management |
| CloudServiceApp.apk | 4.8MB | `/tmp/cloudservice-decompiled/` | Cloud service framework |
| BluetoothRemoteProvider.apk | 11KB | `/tmp/bluetoothprovider-decompiled/` | BT remote key provider |
| ClientConfigurationService.apk | 1.2MB | `/tmp/clientconfig-decompiled/` | Client config service |

### Key Source Files (IntelligentEntry, obfuscated)

| File | Class | Purpose |
|------|-------|---------|
| `spi/bi.java` | FlavorManager | Platform detection (abroad/china, DI3.0/6.0) |
| `spi/eu.java` | PlatformAdapterImplDi3 | DI3.0 CAN bus adapter |
| `spi/tm.java` | IntelligenceControlImpl | CAN bus callback registration |
| `spi/pm.java` | IntelligenceControlManager | Feature support + verification |
| `spi/b00.java` | SwitchConfigManager | User key config management |
| `spi/r5.java` | CarCloudServiceImpl | Cloud broadcast receiver |
| `spi/c00.java` | (anonymous) | Cloud command handler |
| `spi/py.java` | (utils) | SharedPrefs + collect2 persistence |
| `spi/s5.java` | CarPropertyManagerImpl | CAN bus property manager |
| `spi/px.java` | (Setting mapper) | Hex ID → BYDAutoFeatureIds.Setting |
| `spi/a60.java` | (Yun mapper) | Hex ID → BYDAutoFeatureIds.Yun |
| `spi/qh.java` | (Dialog) | Key type selection dialog |
| `spi/p0.java` | (Dialog) | Forgot password / key type dialog |
| `widgets/KeyTypeRecyclerView.java` | KeyTypeRecyclerView | Key type display |
| `UserSwitchConfig.java` | UserSwitchConfig | Data class (userIdHash, keyType, isEnable) |

## Persistent Data Locations

| Path | Purpose |
|------|---------|
| `/collect2/intelligententry` | Binary key-value config (operation codes, error counts) |
| SharedPrefs `sp_app` → `KEY_USER_SWITCH_CONFIGS` | JSON of active user switch configs |
