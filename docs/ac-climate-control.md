# AC & Climate Control

## Overview

The BYD Dolphin's climate system is accessible through `BYDAutoAcDevice` (device type 1000). The AC subsystem provides temperature reading, AC state control, and fan/mode management.

Permissions: `BYDAUTO_AC_GET`, `BYDAUTO_AC_SET`, `BYDAUTO_AC_COMMON`

## API Access

### BYDAutoAcDevice

```java
import android.hardware.bydauto.ac.BYDAutoAcDevice;

BYDAutoAcDevice acDevice = BYDAutoAcDevice.getInstance(context);
```

Device type: **1000** (confirmed via `getDevicetype()`).

### Temperature Reading — getTemprature(zone)

The primary method for reading temperatures. Note the **typo in the API name** — it's `getTemprature`, not `getTemperature`.

```java
int cabinTemp = acDevice.getTemprature(4);  // inside/cabin temperature
int driverSet = acDevice.getTemprature(1);  // driver set temperature
int passengerSet = acDevice.getTemprature(2); // passenger set temperature
```

**Zone mapping (BYD Dolphin):**

| Zone | Value | Description |
|------|-------|-------------|
| 0 | -2147482645 (error) | Unknown / not mapped |
| 1 | e.g. 25 | Driver set temperature (°C) |
| 2 | e.g. 25 | Passenger set temperature (°C) |
| 3 | 65535 (N/A) | Rear AC set temp (not available on Dolphin) |
| 4 | e.g. 27 | **Cabin inside temperature (°C)** |
| 5-10 | -2147482645 (error) | Not mapped |

Error codes:
- `-2147482645` — zone not available / not mapped
- `65535` (0xFFFF) — feature not available on this vehicle
- `-1` — general error / permission denied

This method **does not require special permissions** — it works from any app with the BYDAutoAcDevice instance.

### AC State Getters

All no-arg getters work without permission checks. Full list with sample values:

| Method | Returns | Sample | Description |
|--------|---------|--------|-------------|
| `getAcStartState()` | int | 1 | AC on (1) / off (0) |
| `getAcOnlineState()` | int | 1 | AC system online |
| `getAcControlMode()` | int | 0 | 0=auto, other=manual |
| `getAcCycleMode()` | int | 1 | 0=fresh air, 1=recirculation |
| `getAcWindLevel()` | int | 1 | Fan speed level |
| `getAcWindMode()` | int | 1 | Vent direction mode |
| `getAcWindModeNum()` | int | 2 | Number of wind modes |
| `getAcCompressorMode()` | int | 1 | Compressor mode |
| `getAcCompressorManualSign()` | int | 0 | Compressor manual override |
| `getAcMaxCoolingState()` | int | 0 | Max cooling active |
| `getAcVentilationState()` | int | 0 | Ventilation only (no cooling) |
| `getAcWarmState()` | int | 65535 | Heating state (N/A on Dolphin) |
| `getAcWarmTypeOnlineState()` | int | 6 | Heating type |
| `getAcDefrostOnlineState()` | int | 1 | Defrost available |
| `getAcTemperatureControlMode()` | int | 3 | Temp control mode |
| `getTemperatureUnit()` | int | 1 | 1=Celsius |
| `getAcType()` | int | 1 | AC system type |
| `getAcKeyActionState()` | int | 3 | Key action state |
| `getAcRemoteCtrlTime()` | int | 5 | Remote AC run time (minutes) |
| `getAcSubBatteryTemperature()` | int | 65535 | Sub-battery temp (N/A) |
| `getAcPtcPreheatSignal()` | int | 2 | PTC preheater signal |
| `getAutoCleanAirState()` | int | 2 | Auto air purification |
| `getQuickCleanAirState()` | int | 0 | Quick clean air active |
| `getQuickCleanTip()` | int | 0 | Quick clean tip shown |
| `getHighTempAntivirusState()` | int | 0 | High-temp antivirus mode |
| `getHighTempAntivirusCountDown()` | int | 0 | Countdown timer |
| `getRearAcStartState()` | int | 65535 | Rear AC (N/A on Dolphin) |
| `getRearAcControlMode()` | int | 65535 | Rear AC mode (N/A) |
| `getRearAcLockState()` | int | 1 | Rear AC panel locked |
| `getRearAcMaxWindLevel()` | int | 2 | Rear AC max wind |
| `getRearAcWindLevel()` | int | 0 | Rear AC wind level |
| `getRearAcWindMode()` | int | 0 | Rear AC wind mode |
| `getDefrostRearConfig()` | int | 2 | Rear defrost config |
| `getVoiceCmdResult()` | int | 0 | Voice command result |

### AC Control Methods (SET)

These require `BYDAUTO_AC_SET` permission:

| Method | Parameters | Description |
|--------|-----------|-------------|
| `start(int)` | mode | Start AC |
| `stop(int)` | mode | Stop AC |
| `setAcTemperature(int, int, int, int)` | zone, temp, ?, ? | Set temperature |
| `setAcWindLevel(int, int)` | level, ? | Set fan speed |
| `setAcWindMode(int, int)` | mode, ? | Set vent direction |
| `setAcCycleMode(int, int)` | mode, ? | Set air recirculation |
| `setAcControlMode(int, int)` | mode, ? | Set auto/manual |
| `setAcCompressorMode(int, int)` | mode, ? | Set compressor |
| `setAcMaxCoolingState(int)` | state | Toggle max cool |
| `setAcVentilationState(int, int)` | state, ? | Toggle ventilation |
| `setAcWarmState(int)` | state | Toggle heating |
| `setAcDefrostState(int, int, int)` | zone, state, ? | Toggle defrost |
| `setAcTemperatureControlMode(int, int)` | mode, ? | Set temp ctrl mode |
| `setAutoCleanAirState(int)` | state | Toggle auto purification |
| `setQuickCleanAirState(int)` | state | Toggle quick clean |
| `setAcRemoteCtrlTime(int)` | minutes | Set remote AC timer |
| `setAcRearPanelLockState(int)` | state | Lock rear panel |
| `feelColdHot(int, int)` | ?, ? | Comfort adjustment |

### Event Listener

Register for real-time AC state changes:

```java
import android.hardware.bydauto.ac.AbsBYDAutoAcListener;
import android.hardware.bydauto.BYDAutoEventValue;

AbsBYDAutoAcListener listener = new AbsBYDAutoAcListener() {
    @Override
    public void onAcStarted() { /* AC turned on */ }

    @Override
    public void onAcStoped() { /* AC turned off (note BYD typo) */ }

    @Override
    public void onDataEventChanged(int featureId, BYDAutoEventValue value) {
        int val = value.intValue;
        // featureId identifies what changed
    }
};

int[] featureIds = {0x3D800030, 0x1DE00030, 0x1DE00010, 0x1DE00018};
acDevice.registerListener(listener, featureIds);
```

Listener registration succeeds and uses device-level permission check only (no per-feature check). Events fire when values actually change.

## Feature IDs (CAN Bus)

Known AC-related feature IDs:

| Feature ID | Description |
|-----------|-------------|
| `0x3D800030` | AC_TEMP_INSIDE_FILTERING (filtered cabin temp) |
| `0x1DE00030` | AC_TEMP_INSIDE (raw cabin temp) |
| `0x1DE00010` | AC_SET_TEMP_DRIVER |
| `0x1DE00018` | AC_SET_TEMP_PASSENGER |
| `0x1DE00008` | Unknown AC feature |
| `0x1DE00012` | Unknown AC feature |
| `0x1DE00020` | Unknown AC feature |
| `0x1DE00022` | Unknown AC feature |
| `0x1DE00024` | Unknown AC feature |
| `0x1DE00028` | Unknown AC feature |

Note: Direct `get(int[], Class)` is blocked by per-feature permission checks (`AutoApiBlack`). Use `getTemprature(zone)` instead — it bypasses this check.

## Permission Architecture

```
┌─────────────────────────────────────────────────┐
│          BYDAutoAcDevice                         │
│                                                  │
│  getTemprature(zone) ──── NO permission check ──►│── works from any app
│  getAcStartState()  ──── NO permission check ──►│── works from any app
│  get(int[], Class)  ──── PER-FEATURE check ────►│── blocked for most apps
│  registerListener() ──── DEVICE-LEVEL check ───►│── works with BydPermissionContext
│                                                  │
│       ┌──── mDeviceManager (reflection) ────┐    │
│       │  BYDAutoDeviceManager$Impl           │    │
│       │  getInt(1000, featureId) ── works ──►│── bypasses per-feature check
│       └─────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### BYDAutoDeviceManager Bypass

The underlying manager can be accessed via reflection for direct CAN bus reads:

```java
Field mgrField = acDevice.getClass().getSuperclass().getDeclaredField("mDeviceManager");
mgrField.setAccessible(true);
Object manager = mgrField.get(acDevice);
// manager is BYDAutoDeviceManager$BYDAutoDeviceManagerImpl

Method getInt = manager.getClass().getMethod("getInt", int.class, int.class);
int value = (int) getInt.invoke(manager, 1000, 0x1DE00030); // deviceType=1000, featureId
```

Manager methods:

| Method | Signature | Description |
|--------|-----------|-------------|
| `getInt` | `(int deviceType, int featureId)` | Read int value |
| `getDouble` | `(int deviceType, int featureId)` | Read double value |
| `getBuffer` | `(int deviceType, int featureId)` | Read byte array |
| `getIntArray` | `(int deviceType, int[] featureIds)` | Read multiple ints |
| `getDoubleArray` | `(int deviceType, int[] featureIds)` | Read multiple doubles |
| `setInt` | `(int deviceType, int featureId, int value)` | Write int |
| `setDouble` | `(int deviceType, int featureId, double value)` | Write double |
| `setBuffer` | `(int deviceType, int featureId, byte[] data)` | Write bytes |
| `setIntArray` | `(int deviceType, int[] featureIds, int[] values)` | Write multiple ints |
| `enableDevice` | `(IBYDAutoDevice device)` | Enable device |
| `disableDevice` | `(IBYDAutoDevice device)` | Disable device |

## Ambient Temperature Sensor

The head unit has a Bosch `smi230-acc-temp-iner` sensor accessible via Android SensorManager:

```java
Sensor ambientSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
```

**Important:** This reads the **SoC chip temperature** (~36-38°C), NOT the cabin temperature. Do not use this for cabin temperature display.

## Tested On

- BYD Dolphin 2025
- DiLink 3.0, Android 10 (API 29)
- Firmware 13.1.32.2507250.1
