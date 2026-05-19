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
int outsideTemp = acDevice.getTemprature(4);  // outside/ambient temperature
int driverSet = acDevice.getTemprature(1);    // driver set temperature
int passengerSet = acDevice.getTemprature(2); // passenger set temperature
```

**Zone mapping (BYD Dolphin):**

| Zone | Value | Description |
|------|-------|-------------|
| 0 | -2147482645 (error) | Unknown / not mapped |
| 1 | e.g. 25 | Driver set temperature (°C) |
| 2 | e.g. 25 | Passenger set temperature (°C) |
| 3 | 65535 (N/A) | Rear AC set temp (not available on Dolphin) |
| 4 | e.g. 27 | **Outside/ambient temperature (°C)** — confirmed via `BYDAutoInstrumentDevice.getOutCarTemperature()` returning same value |
| 5-10 | -2147482645 (error) | Not mapped |

Error codes:
- `-2147482645` — zone not available / not mapped
- `65535` (0xFFFF) — feature not available on this vehicle
- `-1` — general error / permission denied

This method **does not require special permissions** — it works from any app with the BYDAutoAcDevice instance.

**Cabin/inside temperature is NOT available** — exhaustive probing (zones 0-100, 512 Manager feature IDs, InstrumentDevice, SensorDevice, EnergyDevice, PM2.5 device) found no cabin temperature API. The physical sensor exists in the AC hardware (for auto mode regulation) but BYD does not expose it to the Android layer. `BYDAutoInstrumentDevice` has `getOutCarTemperature()` but no `getInCarTemperature()`.

**Manager.getInt returns -10011** for all AC feature IDs (0x1DE00000-0x1DE000FF, 0x3D800000-0x3D8000FF) when called from app context, even with `BydPermissionContext`. The bypass only works from system-level processes.

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

These require `BYDAUTO_AC_SET` permission (signature-level, bypassable via `BydPermissionContext`).

**Permission bypass:** Create a `ContextWrapper` that overrides `enforceCallingOrSelfPermission()` to auto-grant any `android.permission.BYDAUTO_*` permission. Pass to `getInstance()`:

```java
BYDAutoAcDevice acDevice = BYDAutoAcDevice.getInstance(new BydPermissionContext(context));
acDevice.start(0);  // AC on (source=0 = UI_KEY)
```

| Method | Signature | Parameters | Description |
|--------|-----------|-----------|-------------|
| `start(int)` | `(int) → int` | source: 0=UI, 1=voice | Turn AC on |
| `stop(int)` | `(int) → int` | source | Turn AC off |
| `setAcTemperature(int, int, int, int)` | `(int,int,int,int) → int` | zone, temp, source, ? | Set temp (half-degree: 34=17°C, 66=33°C) |
| `setAcWindLevel(int, int)` | `(int,int) → int` | level (0-7), source | Set fan speed |
| `setAcWindMode(int, int)` | `(int,int) → int` | mode, source | Set vent direction (1=face, 5=foot, 0=defrost) |
| `setAcCycleMode(int, int)` | `(int,int) → int` | mode, source | 0=recirculate, 1=outside air |
| `setAcControlMode(int, int)` | `(int,int) → int` | mode, source | 0=auto, 1=manual |
| `setAcCompressorMode(int, int)` | `(int,int) → int` | mode, source | Compressor on/off |
| `setAcMaxCoolingState(int)` | `(int) → int` | state | Toggle max cool |
| `setAcVentilationState(int, int)` | `(int,int) → int` | state, source | Toggle ventilation |
| `setAcDefrostState(int, int, int)` | `(int,int,int) → int` | area, state, source | Toggle defrost |
| `setAcTemperatureControlMode(int, int)` | `(int,int) → int` | mode, source | Set temp ctrl mode |
| `setAutoCleanAirState(int)` | `(int) → int` | state | Toggle auto purification |
| `setQuickCleanAirState(int)` | `(int) → int` | state | Toggle quick clean |
| `setAcRemoteCtrlTime(int)` | `(int) → int` | 1-5 = 10/15/20/25/30 min | Set remote AC timer |
| `setAcRearPanelLockState(int)` | `(int) → int` | state | Lock rear panel |
| `feelColdHot(int, int)` | `(int,int) → int` | ?, ? | Comfort adjustment |
| `setFragrance(String, int)` | `(String,int) → int` | name, intensity | Fragrance system |

### Temperature Encoding

Two encoding schemes:
- **GET (getTemprature):** Direct Celsius — `getTemprature(1)` returns 26 = 26°C
- **SET (setAcTemperature):** Half-degree encoding — values 34-66 map to 17.0°C - 33.0°C (value ÷ 2)

Example: set 22°C on main zone → `setAcTemperature(1, 44, 0, 0)` (44/2 = 22°C)

### Remote Control

`hasFeature("ACRemoteControl") = 1` — remote AC control confirmed supported. Timer values: 1-5 map to 10/15/20/25/30 minutes.

### Verified SET Calls (tested on car)

| Action | Call | Status |
|--------|------|--------|
| AC on | `start(0)` | **WORKS** |
| AC off | `stop(0)` | **WORKS** |
| Set temp | `setAcTemperature(1, tempCelsius, 1, 1)` | **WORKS** — direct Celsius, source=1, param4=1 |
| Set fan | `set(1000, 0x1DE00030, level)` via base class | **WORKS** — level 0-7 |
| Set wind mode | `setAcWindMode(mode, 1)` | **WORKS** — source=1 |
| Set cycle mode | `setAcCycleMode(mode, 0)` | **WORKS** — source=0 or 1 |
| Set control mode | `setAcControlMode(mode, 1)` | **WORKS** — 0=auto, 1=manual |

**Important quirks:**
- `setAcTemperature` requires `source=1` (voice) and `param4=1`. With `source=0` (UI_KEY) it returns INVALID_VALUE
- `setAcWindLevel(level, source)` is **broken** — returns INVALID_VALUE for all source values. Must use base class `set(1000, 0x1DE00030, level)` instead
- Temperature uses **direct Celsius** (not half-degree encoding) for the SET call
- `getTemprature()` also returns direct Celsius, so GET and SET use the same encoding

### AC CAN Bus Feature IDs (device type 1000)

| Feature ID | Writable | Purpose |
|-----------|----------|---------|
| `0x1DE00008` | YES (returns 0) | Unknown — be careful |
| `0x1DE00010` | no | |
| `0x1DE00028` | YES (returns 0) | Unknown — affected wind level unexpectedly |
| `0x1DE00030` | YES | **Wind/fan level** (0-7) |
| `0x3D800030` | no | AC_TEMP_INSIDE_FILTERING (read-only) |

### Return Codes

| Value | Constant | Meaning |
|-------|----------|---------|
| 0 | `AC_COMMAND_SUCCESS` | Command accepted |
| -2147482648 | `AC_COMMAND_FAILED` | Command rejected |
| -2147482647 | `AC_COMMAND_BUSY` | CAN bus busy |
| -2147482646 | `AC_COMMAND_TIMEOUT` | Command timed out |
| -2147482645 | `AC_COMMAND_INVALID_VALUE` | Invalid parameters |

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
