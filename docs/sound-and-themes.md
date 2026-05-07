# Sound & Theme Customization

## Vehicle Sound Architecture

The BYD Dolphin's sound system communicates via CAN bus (CANFD protocol) through DiCarServer (`com.byd.car.server`).

Signal flow:
```
Android App → DiCarServer → BYDAutoManager (JNI → libbydauto.so) → MCU (CANFD) → BCM/Amplifier/AVAS Speaker
```

DiCarServer uses two ID systems:
- **Framework IDs** (`BYDAutoFeatureIds.Audio`): computed at runtime from `isCanFD`/`isToyota` flags. Used by `BYDAutoManager.getInt/setInt()`.
- **Hex string IDs** (`com.byd.feature.audio.Audio`): hardcoded hex strings. `AudioMapper.transformFeatureId()` tries framework lookup first, falls back to `Integer.valueOf(hex, 16)`.

Vehicle config: Dolphin = vehicleId 218 (from `VehicleCarType.json`).

### CAN Bus Access Tool (BydAudioQuery)

**Direct CAN bus read/write via ADB** using `scripts/BydAudioQuery.java`. Uses `app_process` + reflection to call `BYDAutoManager.getInt/setInt` directly, bypassing permission checks.

Build & run:
```bash
javac -source 11 -target 11 -d /tmp/bydquery scripts/BydAudioQuery.java
d8 --output /tmp/bydquery /tmp/bydquery/BydAudioQuery.class
adb push /tmp/bydquery/classes.dex /data/local/tmp/bydquery.dex

# Full dump
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery"

# Read specific property
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery get 0x4c60002d"

# Write property (device defaults to 1002=audio)
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set <featureId> <value> [deviceType]"
```

### All Verified CAN Bus Property IDs

Source: `com.byd.feature.audio.Audio` and `com.byd.feature.test.Test` classes in DiCarServer.

#### AVAS Signals

| Signal | Hex ID | Device | R/W | MCU Result | Live Value |
|--------|--------|--------|-----|------------|------------|
| AVAS_SOUND_SOURCE_STATE | 0x4C60002D | 1002 | R | OK | 0 |
| AVAS_SOUND_SOURCE_SET_SET | 0x1B10003D | 1002 | W | **SUCCESS** | accepts 0,1 |
| AVAS_SOURCE_TYPE | 0x99000162 | 1002 | R | OK | 0 |
| AVAS_FAULT_STATUS | 0x35201042 | 1002 | R | -10011 | |
| AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET | 0x32B1C042 | 1002 | W | **FAILED** | MCU rejects |
| AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_STATUS | 0x35203032 | 1002 | R | -10011 | |

#### Exterior Speaker Signals

| Signal | Hex ID | Device | R/W | MCU Result |
|--------|--------|--------|-----|------------|
| EXTERIOR_SPEAKER_SWITCH_SET | 0x1C10000E | 1002 | W | **FAILED** |
| EXTERIOR_SPEAKER_SWITCH_STATUS | 0x35201040 | 1002 | R | -10011 |
| EXTERIOR_SPEAKER_CONFIG | 0x35201036 | 1002 | R | -10011 |
| EXTERIOR_PROMPT_TONE_SOURCE_SET | 0x1B100043 | 1002 | W | **FAILED** |
| EXTERIOR_PROMPT_TONE_SOURCE_STATUS | 0x3520103F | 1002 | R | -10011 |

#### Lock/Unlock & Power-On Sounds

| Signal | Hex ID | Device | R/W | MCU Result |
|--------|--------|--------|-----|------------|
| LOCK_CAR_SOUND_EFFECT_PLAYBACK_STATUS_SET | 0xAA000321 | 1002 | W | **FAILED** |
| START_PLAY_POWER_ON_SOUND_SET | 0xAA000243 | 1002 | W | **FAILED** |

#### Engine Simulator

| Signal | Hex ID | Device | R/W | MCU Result | Live Value |
|--------|--------|--------|-----|------------|------------|
| ENGINE_SIMULATOR_SOURCE_TYPE | 0x48F00010 | 1003 | R | OK | 1 |
| ENGINE_SIMULATOR_SOURCE_TYPE_SET | 0x3E300038 | 1003 | W | **SUCCESS** | 1=normal, 2=sport, 3=? |
| ENGINE_VOICE_SIMULATOR_STATE_SET | 0x3E300020 | 1003 | W | -10011 (write-only) | |

#### Test/Diagnostic AVAS Signals (MCU ACCEPTS)

| Signal | Hex ID | Device | R/W | MCU Result | Notes |
|--------|--------|--------|-----|------------|-------|
| TEST_AUDIO_AVAS_SET | 0xAA000104 | 1002 | W | **SUCCESS** | Accepts 0-3 |
| TEST_MCU_AVAS_CONFIGURATION_SET | 0xAA000171 | 1002 | W | **SUCCESS** | Accepts 0-3 |
| TEST_MCU_SPEAK_SET | 0xAA000142 | 1002 | W | **SUCCESS** | MCU speaker test |
| TEST_FM_SPEAK_SET | 0xAA00011A | 1002 | W | **SUCCESS** | FM speaker test |
| TEST_PA_CONTROL_SET | 0xAA000148 | 1002 | W | **SUCCESS** | Power amplifier control |
| TEST_CMD_TEST_AUDIO_AVAH | 0x6EA70010 | 1002 | R | OK | Returns 65535 (0xFFFF) |

#### Other Working Audio Signals

| Signal | Hex ID | MCU Result |
|--------|--------|------------|
| AUDIO_MUSIC_CHANGE_SOURCE_SET | 0xAA000043 | SUCCESS |
| AUDIO_CHANNLE_WITH_MUTE_STATE_SET | 0xAA00011E | SUCCESS |
| AUDIO_SOC_NOTIFY_MCU_CONTROL_DSP_SET | 0xAA000145 | SUCCESS |
| AUDIO_SUB_FM_VOLUME_SET | 0xAA000156 | SUCCESS |
| AUDIO_DMS_ALERT_SET | 0xAA000158 | SUCCESS |
| AUDIO_MIC_GAIN_REFERENCE_SET | 0xAA00020B | SUCCESS |
| AUDIO_NAVIGATION_AND_MEDIA_CHANNEL_STATE_SET | 0xAA000221 | SUCCESS |

#### ESS / ANC

| Signal | Hex ID | R/W | Live Value |
|--------|--------|-----|------------|
| ESS_AMPLIFIER_CONFIG | 0x4FD00030 | R | 0 |
| ANC_SOUND_SOURCE_STATE | 0x4C600025 | R | 0 |
| ANC_SOUND_SOURCE_SET | 0x1B100035 | W | -10011 (write-only) |

### CAN Bus Write Test Results

- **Engine simulator source: CONFIRMED WORKING.** Changed 1→2→1, verified via CAN bus read and content provider.
- **AVAS source SET: SUCCESS but state reads 0.** MCU accepts but AVAS may only update while driving (0-30 km/h).
- **TEST_AUDIO_AVAS_SET: SUCCESS.** MCU accepts values 0-3. Effect needs testing while driving.
- **TEST_MCU_AVAS_CONFIGURATION_SET: SUCCESS.** MCU accepts values 0-3.
- **External speaker routing: FAILED.** MCU firmware does not implement `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` on the Dolphin.
- **Lock/power-on sounds: FAILED.** MCU firmware does not implement these signals on the Dolphin.

### MCU Return Codes

| Value | Meaning |
|-------|---------|
| 0 | SUCCESS |
| -10011 | Feature not registered (write-only signals return this on read) |
| -10013 | Feature not available (different from -10011) |
| -2147482648 | BYDAUTO_COMMAND_RESULT_FAILED (MCU rejects, feature not implemented) |
| -2147482647 | BYDAUTO_COMMAND_RESULT_BUSY |
| -2147482646 | BYDAUTO_COMMAND_RESULT_TIMEOUT |
| -2147482645 | BYDAUTO_COMMAND_RESULT_INVALID_VALUE |

### AVAS (Acoustic Vehicle Alerting System)

The AVAS is **partially controlled from the Android head unit**. DiCarServer has full signal definitions, but the Dolphin's MCU firmware only implements a subset.

**Official options**: 2 presets via Vehicle Settings > Notification: "standard" and "brand" (with sub-options "standard" and "dynamic").

**AVAS behavior**: Volume increases 0-20 km/h, decreases 20-30 km/h, stops above 30 km/h. Continuous in reverse.

**Boombox / custom sound verdict**: `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` exists in DiCarServer's code (shared across all BYD models) but the Dolphin's MCU firmware **does not implement it**. The MCU returns FAILED (-2147482648). Android's audio system also does not expose the AVAS speaker as an output device — only "speaker" (cabin speakers) is available.

### Radar/Parking Sensor Sounds

Controlled from Android:

| Signal | Hex ID | R/W | Description |
|--------|--------|-----|-------------|
| AUDIO_RADAR_SOUND_LF/LR/RF/RR | various | R | Per-corner radar sound |
| AUDIO_RADAR_SOUND_SOURCE | config | R | Radar sound source |
| AUDIO_RADAR_SOUND_VOLUME | config | R | Radar sound volume |

### Cabin Audio Processing

The head unit controls:
- 5-band equalizer
- Bass, midrange, treble controls
- Sound field focus (X/Y positioning)
- Front/rear, left/right balance
- Devialet sound processing (50Hz-8000Hz)
- 3D / Space sound effects
- ANC (Active Noise Cancellation)
- Loudness control

### BYD-Specific Audio Streams

All route to "speaker" (cabin speakers only):
- `STREAM_FM`, `STREAM_AUX`, `STREAM_NAVI`, `STREAM_MUTE`, `STREAM_TTS`

## What CAN Be Changed

### Notification Sound
```bash
adb shell "ls /system/product/media/audio/notifications/"
adb shell settings put system notification_sound "content://media/internal/audio/media/46?title=Pixie%20Dust&canonical=1"
```

### Ringtone
```bash
adb shell "ls /system/product/media/audio/ringtones/"
adb shell settings put system ringtone "content://media/internal/audio/media/165?title=Flutey%20Phone&canonical=1"
```

### System Sound Toggles
```bash
adb shell settings put system sound_effects_enabled 1
adb shell settings put system charging_sounds_enabled 1
adb shell settings put system lockscreen_sounds_enabled 1
adb shell settings put system dtmf_tone 1
```

### Custom Notification Sound
```bash
adb push my_sound.ogg /sdcard/Notifications/
```

### AVAS Preset Selection
Via Vehicle Settings > Notification, or programmatically:
```bash
# Select AVAS sound source (0 or 1)
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 1"
```

### Engine Simulator Sound
```bash
# 1=normal, 2=sport, 3=?
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 2 1003"
```

### Turn Signal Sound
Changeable to different presets through infotainment settings.

## What CANNOT Be Changed

| Sound | Reason |
|-------|--------|
| Custom AVAS / Boombox | MCU firmware doesn't implement external speaker routing (0x32B1C042 FAILED) |
| Custom lock chirp | MCU firmware doesn't implement (0xAA000321 FAILED) |
| Custom power-on sound | MCU firmware doesn't implement (0xAA000243 FAILED) |
| Horn | Physical relay, hardware-controlled |
| Seatbelt warning chime | Safety-critical, BCM/Cluster |
| Boot animation sound | `/system` partition read-only, dm-verity protected |
| Route audio to exterior speaker | No external speaker in Android audio device list; MCU rejects routing commands |

## Tesla vs BYD Comparison

| Feature | Tesla | BYD Dolphin |
|---------|-------|-------------|
| Custom AVAS | Yes (USB upload, "Boombox") | No — MCU doesn't implement routing |
| Custom horn | Yes (via Boombox) | No — hardware relay |
| External speaker | General-purpose, user-programmable | AVAS speaker exists but MCU-locked to presets |
| Custom lock sound | No | No — MCU rejects command |
| Upload mechanism | USB drive + folder structure | N/A |
| Engine sound | Limited | 3 presets (normal/sport/?), CAN-writable |

## Still Worth Testing (While Driving)

| Signal | What to Try | Why |
|--------|-------------|-----|
| TEST_AUDIO_AVAS_SET [0xAA000104] | Values 0-3 while car is moving 0-30 km/h | MCU accepts; may trigger/change AVAS playback |
| TEST_MCU_AVAS_CONFIGURATION_SET [0xAA000171] | Values 0-3 while driving | MCU accepts; may reconfigure AVAS behavior |
| AVAS_SOUND_SOURCE_SET_SET [0x1B10003D] | Values 0,1 while driving 0-30 km/h | Accepted but state didn't change while parked |
| TEST_MCU_SPEAK_SET [0xAA000142] | Value 1 while parked, listen for sound | MCU accepts; might trigger audible test |
| TEST_PA_CONTROL_SET [0xAA000148] | Value 1, listen for amplifier state change | MCU accepts; power amplifier control |

## DiCarServer Analysis

Extracted to `data/apks/DiCarServer_extracted/`. Decompiled to `/tmp/dicarserver_decompiled/`.

### Key Classes

| Class | Purpose |
|-------|---------|
| `com.byd.feature.audio.Audio` | **All hex property IDs** — the authoritative source for CAN bus signal IDs |
| `com.byd.feature.audio.AudioMapper` | Maps hex IDs → `BYDAutoFeatureIds.Audio` fields (reflection), fallback to hex parsing |
| `com.byd.feature.test.Test` | Test/diagnostic signal IDs (0xAA prefix) |
| `com.byd.audio.AudioFeatureHandler` | Thin wrapper, only handles DAB |
| `com.byd.feature.bydauto.BydAutoMapping` | Maps module indices to mappers (23=Audio, 37=Test) |

### ID Resolution Flow
```
Audio.java hex string "0x1B10003D"
    → AudioMapper.transformFeatureId()
        → try: BYDAutoFeatureIds.Audio.AUDIO_AVAS_SOUND_SOURCE_SET_SET (framework reflection)
        → catch NoSuchFieldException: Integer.valueOf("1B10003D", 16) = 454033469
    → BYDAutoManager.setInt(1002, 454033469, value)
        → nativeSetInt() → JNI → libbydauto.so → MCU (CANFD)
```

### Config Protobuf Files
- `config_1.bin` (69KB) — read/status signal registry (field1=ID, field2=module)
- `config_2.bin` (32KB) — write/SET signal registry (field1=ID, field2=module, field3=1)
- `config_3.bin` (6KB) — additional config
- No vehicle-specific gating in config; MCU firmware decides what to accept

## Theme System

### BYD Theme Store
- Package: `com.byd.automultipletheme`
- Visual themes only — no sound theming
- Permission: `com.android.permission.CHANGE_BYD_APP_THEME`

### Wallpaper
- Package: `com.byd.wallpaperhome`

### Quick Settings Panel
Currently selected:
```
simulator, data, connectdevice, wire_charge, energy_recycle, esp
```

Available but hidden:
```
rotationlock, electric_defrosting, door
```

Enable hidden tiles:
```bash
adb shell settings put system qs_panel_new_vehicle_sel_items "simulator,data,connectdevice,wire_charge,energy_recycle,esp,door"
adb shell settings put system qs_panel_new_vehicle_un_sel_items "rotationlock,electric_defrosting"
```

## Boot Animation

Located at:
- `/system/media/bootanimation.zip` (28MB)
- `/system/media/bootanimation_720p.zip` (16MB)
- `/system/media/video/bootanimation.mp4` (8.9MB)
- `/system/media/bootanimation_porth.zip` (18MB)

**Cannot be replaced** — system partition is read-only and dm-verity protected.
