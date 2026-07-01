# Vehicle Light Control Research

## Summary

Deep investigation into controlling vehicle lights (turn signals, headlights,
hazard, fog, DRL, interior) via the BYDAuto Android framework and CAN injection.

**Bottom line:** Config-type light settings (DRL auto mode, adaptive beam) are
controllable. Safety-critical light actuation (turn signal blink, hazard flash,
fog on/off, headlight flash) is **MCU-firmware-locked** — the MCU rejects all
software SET commands regardless of caller permissions or injection method.

---

## Architecture

```
App / Shell ──setInt(dev, featureId, val)──→ BYDAutoManager
                                                    ↓
                                            MCU firmware
                                            (feature ID → CAN frame translation)
                                                    ↓
                                            CAN bus → BCM → Lights
```

The Java layer only knows **feature IDs** (e.g. `0x33F0000D`). The MCU translates
these to raw CAN frames internally. There is no CAN arbitration ID mapping in any
decompiled APK — the translation is pure MCU firmware.

---

## Device Manager

| Property | Value |
|----------|-------|
| Class | `android.hardware.bydauto.light.BYDAutoLightDevice` |
| Device type | **1004** |
| Permissions | `BYDAUTO_LIGHT_GET/SET/COMMON` (all `protectionLevel=normal`) |
| Feature ID registry | `com.byd.feature.lights.Lights` (DiCarServer) — 214+ IDs |
| Feature ID mapper | `LightsMapper.java` — string hex → `BYDAutoFeatureIds.Light` int |
| Listener | `AbsBYDAutoLightListener` — events for turn/double-flash/DRL changes |

Framework only exposes 2 direct setters: `setDayTimeLightState()` and
`setAcLightAdbState()`. All other light control goes through
`AbsBYDAutoDevice.set(1004, featureId, value)`.

---

## What Works

### DRL Auto State Toggle — CONFIRMED

```
Feature ID: 0x43100046 (LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE_SET)
Device:     1004
Values:     1=ON, 2=OFF
```

**Verified live:** SET val=1 → result=0 (OK) → readback `0x3AC00024` changed 2→1.
SET val=2 → result=0 (OK) → reset to OFF.

### ADB (Adaptive Driving Beam) State — ACCEPTED

```
Feature ID: 0x4310003E (LIGHT_ADB_STATE_SET)
Device:     1004
Values:     1=OFF, 2=STANDBY, 3=ACTIVATION, 4=WORKING, 5=FAULT
```

MCU accepted SET val=1 (result=0). No visible hardware effect (car may need to
be in READY mode, or Dolphin may lack ADB-capable headlights).

### Light State Reads — ALL WORKING

All major light states are readable via `getInt(1004, featureId)`:

| Feature ID | Name | Sample Value |
|-----------|------|-------------|
| `0x38A00008` | SIDE_LIGHT (position) | 0 = OFF |
| `0x38A0000A` | LOW_BEAM_LIGHT | 0 = OFF |
| `0x38A0000C` | HIGH_BEAM_LIGHT | 0 = OFF |
| `0x38A00018` | EMERGENCY_WARNING (hazard) | 65535 = invalid |
| `0x38A0002C` | TURN_SIGNAL_LIGHT | 1 = not flashing |
| `0x1330000C` | LEFT_TURN_SIGNAL | 0 = OFF |
| `0x1330000D` | RIGHT_TURN_SIGNAL | 0 = OFF |
| `0x1330000E` | FRONT_FOG_LIGHT | 0 = OFF |
| `0x1330000F` | REAR_FOG_LIGHT | 0 = OFF |
| `0x13300023` | LIGHT_KNOB_CURRENT_GEAR | 1 = stalk position |
| `0x3AC00024` | DRL_AUTO_STATE | 2 = OFF (toggleable) |
| `0x39400033` | DOUBLE_FLASH_STATE | 2 = OFF |
| `0x38A00012` | STOP_LIGHT | 3 = OFF |
| `0x38A00014` | REVERSING_LIGHT | 2 = OFF |

---

## What Doesn't Work (MCU-Blocked)

All return **result=-2147482648** (`LIGHT_COMMAND_FAILED` = `0x80000000`):

| Feature ID | Name | Value Tried |
|-----------|------|------------|
| `0x33F0000D` | LIGHT_TURN_SIGNAL_LIGHT_SET | 1 |
| `0x1460002E` | LIGHT_TURN_SIGNAL_CONTROL_COMMAND | 1 |
| `0x32B11010` | SECURITY_HIGH_RISK_DOUBLE_FLASH | 1 |
| `0x3E800008` | LIGHTS_FLASHING_LIGHT_AND_HORN | 1 |
| `0x33F00013` | LIGHT_FRONT_FOG_LIGHT_SET | 1 |
| `0x33F0000B` | LIGHT_REAR_FOG_LIGHT_SET | 1 |
| `0x3E700028` | LIGHT_LOW_BEAM_BLINK_STATE | 1 |
| `0x4C130018` | LIGHTS_TURN_INDICATOR_CONTROL_SET | 1 |
| `0x4C130012` | LIGHTS_BRAKE_LIGHT_CONTROL_SET | 1 |
| `0x4C130014` | LIGHTS_REVERSING_LIGHT_CONTROL_SET | 1 |
| `0x0780A044` | LIGHT_ATOM_FRONT_HEADLIGHT_SET | 1 |
| `0x0780A03E` | LIGHT_ATOM_READ_LIGHT_SET | 1 |
| `0x0780A040` | LIGHT_ATOM_ATMOSPHERE_LIGHT_SWITCH | 1 |
| `0x0780B010` | LIGHT_ATOM_UNLOCK_WELCOME_SET | 1 |
| `0x1D30402C` | BODYWORK_PIXEL_HEADLIGHT_FIND_CAR (dev=1001) | 1 |
| `0x1D304030` | BODYWORK_PIXEL_HEADLIGHT_UNLOCK_WELCOME (dev=1001) | 1 |

### Dolphin Hardware Limitations

From `data/system-properties/android-settings.txt`:
- `EXIST_ATMOSPHERE_LIGHT=0` — no ambient/atmosphere light equipped
- `EXIST_MULTICOLOR_LIGHT=0` — no multicolor ambient
- `EXIST_READING_LIGHT=0` — no reading lights equipped

These would return MCU_FAILED regardless.

---

## CAN Injection Path

`TEST_SIMULATE_DOWN_SET` (`0xAA00020F`) exists alongside
`TEST_SIMULATE_UP_SET` (`0xAA000210`) in `BYDAutoFeatureIds.Test`.

### Broadcast injection (com.byd.cluster.spi)

```
ClusterDebugService → BroadcastReceiverCAN → BYDAutoTestDevice.set(
    TEST_SIMULATE_DOWN_SET, bufferData
)
```

Bytes are delivered to the MCU. **However:**
- The byte format expected by `TEST_SIMULATE_DOWN_SET` is a **raw CAN frame**
  (arbitration ID + data bytes), NOT a BYDAuto feature ID
- The CAN arbitration IDs for light control are **proprietary** — not present
  in any decompiled APK (translation happens in MCU firmware)
- Without knowing the raw CAN frame format, injection produces no effect
- The "down" direction (0xAA prefix = SOC→MCU) suggests actuation intent, but
  the MCU likely filters simulated frames for safety-critical signals

### How the BYD phone app flashes lights

```
Phone app → HTTPS → BYD Cloud → MQTT → cloudmanager (native) → MCU (authenticated)
```

The cloud path carries server-side authorization. The MCU trusts cloud-originated
commands but rejects locally-originated software commands for safety lights.
This is a deliberate security design.

---

## Light Feature ID Reference

Complete registry in `apk-analysis/CarSetting_decompiled/sources/com/byd/feature/lights/Lights.java`.

### Key SET (write) IDs

| Hex ID | Constant | Direction |
|--------|----------|-----------|
| `0x33F0000D` | LIGHT_TURN_SIGNAL_LIGHT_SET | SOC→MCU |
| `0x1460002E` | LIGHT_TURN_SIGNAL_CONTROL_COMMAND | SOC→MCU |
| `0x33F00013` | LIGHT_FRONT_FOG_LIGHT_SET | SOC→MCU |
| `0x33F0000B` | LIGHT_REAR_FOG_LIGHT_SET | SOC→MCU |
| `0x43100046` | LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE_SET | SOC→MCU ✓ |
| `0x4310003E` | LIGHT_ADB_STATE_SET | SOC→MCU ✓ |
| `0x32B11010` | SECURITY_HIGH_RISK_DOUBLE_FLASH | SOC→MCU |
| `0x3E800008` | LIGHTS_FLASHING_LIGHT_AND_HORN | SOC→MCU |
| `0x3E700028` | LIGHT_LOW_BEAM_BLINK_STATE | SOC→MCU |
| `0x4C130018` | LIGHTS_TURN_INDICATOR_CONTROL_SET | SOC→MCU |
| `0x4C130012` | LIGHTS_BRAKE_LIGHT_CONTROL_SET | SOC→MCU |
| `0x4C130014` | LIGHTS_REVERSING_LIGHT_CONTROL_SET | SOC→MCU |

### Turn Signal Enum Values (from BYDAutoLightDevice)

| Value | Meaning |
|-------|---------|
| 1 | LEFT_TURN_LIGHT |
| 2 | RIGHT_TURN_LIGHT |
| 2 | TURN_LIGHT_LEFT_FLASH_NORMAL |
| 3 | TURN_LIGHT_LEFT_FLASH_FAST |
| 4 | TURN_LIGHT_RIGHT_FLASH_NORMAL |
| 5 | TURN_LIGHT_RIGHT_FLASH_FAULT |
| 6 | TURN_LIGHT_FLASH_DANGER |
| 7 | TURN_LIGHT_FLASH_EMERG |

---

## Tool

`scripts/LightBlink.java` — probe and control lights via app_process.

```bash
# Build
javac -source 11 -target 11 -d /tmp/lb scripts/LightBlink.java
d8 --output /tmp/lb /tmp/lb/LightBlink.class
adb push /tmp/lb/classes.dex /data/local/tmp/light.dex

# Read all states (safe)
adb shell "CLASSPATH=/data/local/tmp/light.dex app_process / LightBlink read"

# Toggle DRL
adb shell "CLASSPATH=/data/local/tmp/light.dex app_process / LightBlink drl on"
adb shell "CLASSPATH=/data/local/tmp/light.dex app_process / LightBlink drl off"

# Try all SET IDs (research sweep)
adb shell "CLASSPATH=/data/local/tmp/light.dex app_process / LightBlink sweep"
```

---

## Conclusion

BYD's MCU implements **firmware-level access control** for safety-critical
light actuation. The Android framework exposes all 214+ feature IDs and all
permissions are `protectionLevel=normal`, but the MCU itself rejects SET
commands for turn signals, hazard, fog, headlights, and horn combo.

Only **configuration settings** (DRL auto mode, adaptive beam mode) pass
through. Direct actuation requires either:
1. **Cloud authorization** (BYD app path — server-authenticated MQTT)
2. **Physical stalk input** (hardware CAN from the light switch)
3. **Raw CAN injection** with proprietary frame format (unknown)
4. **UDS diagnostic security access** (not yet explored)

This is a well-designed security boundary: the attack surface is large
(214+ feature IDs, normal permissions, CAN injection path), but the MCU
enforces actuation control at the lowest practical level.
