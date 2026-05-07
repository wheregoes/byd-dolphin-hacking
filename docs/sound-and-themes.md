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

#### Sound Source Selection Signals (ALL WORKING)

| Signal | Hex ID | Device | MCU Result | Notes |
|--------|--------|--------|------------|-------|
| VEHICLE_PROMPT_SOUND_SOURCE_SET | 0xAA000194 | 1002 | **SUCCESS** | 1=Normal, 2=Tech |
| BD_SOUND_SOURCE_SET_SET | 0x1C100026 | 1002 | **SUCCESS** | Brand sound source |
| INS_SOUND_SOURCE_SET_SET | 0x1B100045 | 1002 | **SUCCESS** | Instrument sound source |
| RADAR_SOUND_SOURCE_SET_SET | 0x1B100025 | 1002 | **SUCCESS** | Radar/parking sound source |

#### DSP Info (Read-Only)

| Signal | Hex ID | Device | Value | Notes |
|--------|--------|--------|-------|-------|
| DSP_TYPE | 0x99000215 | 1002 | 3 | DSP hardware revision |
| OTA_REMOTE_CONFIG_DSP_SOUND_SOURCE_PACKAGE | 0x99000223 | 1002 | ? | DSP sound bank OTA update status |

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
- **Engine simulator hidden presets: MCU accepts values 1-255.** UI only shows 3 (normal/sport/?), but the full range is writable.
- **AVAS source SET: MCU accepts values 0-5+.** UI only shows 2 presets, but higher values are writable. Effect needs testing while driving (0-30 km/h).
- **Vehicle Prompt Sound Source SET (0xAA000194): SUCCESS.** Values: 1=Normal, 2=Tech. Changes the overall vehicle notification sound profile.
- **BD Sound Source SET (0x1C100026): SUCCESS.** Value 1 accepted.
- **INS Sound Source SET (0x1B100045): SUCCESS.** Value 1 accepted.
- **Radar Sound Source SET (0x1B100025): SUCCESS.** Value 1 accepted.
- **TEST_AUDIO_AVAS_SET: SUCCESS.** MCU accepts values 0-3. Effect needs testing while driving.
- **TEST_MCU_AVAS_CONFIGURATION_SET: SUCCESS.** MCU accepts values 0-3.
- **DSP_TYPE [0x99000215]: reads 3.** Identifies the DSP hardware revision.
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

**Hidden presets**: The MCU accepts AVAS source values 0-5+ via `AVAS_SOUND_SOURCE_SET_SET` (0x1B10003D), far beyond the 2 UI options. These may select different AVAS sounds stored in the MCU/DSP firmware. **Must be tested while driving at 0-30 km/h** — AVAS is silent while parked.

**AVAS behavior**: Volume increases 0-20 km/h, decreases 20-30 km/h, stops above 30 km/h. Continuous in reverse.

**Vehicle Prompt Sound Source**: A separate sound profile system accessible via `STATISTICS_SOUND_SOURCE_INFO` (0x99000194 read, 0xAA000194 write). Values: 1=Normal, 2=Tech. When set to "Tech", the engine simulator sub-menu is hidden in Vehicle Settings. This is read as a byte array — byte offset 5 contains the current value.

**Boombox / custom sound verdict**: `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` exists in DiCarServer's code (shared across all BYD models) but the Dolphin's MCU firmware **does not implement it**. The MCU returns FAILED (-2147482648). Android's audio system also does not expose the AVAS speaker as an output device — only "speaker" (cabin speakers) is available.

**DSP OTA sound source**: `OTA_REMOTE_CONFIG_DSP_SOUND_SOURCE_PACKAGE` (0x99000223) is **read-only** (0x99 prefix = MCU→SOC). It reports the current DSP sound package status — there is no corresponding 0xAA write signal. However, the `BYDAutoOtaDevice` class provides `sendOTAData(byte[])` which pushes arbitrary binary data through `setBuffer()` → JNI → `libbydauto.so` → SPI → MCU. The OTA pipeline is: `StartOTA()` → `sendOTAData(byte[])` → `FinishOTA()`. OTA permissions: `android.permission.BYDAUTO_OTA_SET`. The MCU likely validates signatures/checksums.

**TEST_FLASH_MUSIC_VAL_SET (0xAA000151)**: "Flash music value to MCU" — potentially writes sound data to MCU's flash storage. This is a writable test signal that may allow pushing audio data. Needs testing.

**DSP control signals**:
- `AUDIO_SOC_NOTIFY_MCU_CONTROL_DSP_SET` (0xAA000145): SOC tells MCU to control DSP — a direct command path
- `TEST_DSP_STANDBY_STATE_SET` (0xAA000113): Set DSP standby state
- `TEST_MCU_REPORT_DSP_VERSION_SET` (0xAA000247): Request DSP version from MCU
- `AUDIO_DSP_READY` (0x99000364): DSP ready notification
- `AUDIO_DSP_TYPE` (0x99000215): reads 3 on Dolphin

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

### AVAS Preset Selection (Hidden Presets)
Via Vehicle Settings > Notification (2 presets), or programmatically (0-5+ hidden presets):
```bash
# Select AVAS sound source — UI shows 0,1 but MCU accepts 0-5+
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 2"
# Must test while driving at 0-30 km/h (AVAS is silent when parked)
```

### Engine Simulator Sound (Hidden Presets)
```bash
# UI shows 1-3, but MCU accepts 1-255
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 4 1003"
# Reset to normal
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 1 1003"
```

### Vehicle Prompt Sound Profile
```bash
# 1=Normal, 2=Tech (Tech hides engine simulator sub-menu)
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000194 2"
```

### Turn Signal Sound
Changeable to different presets through infotainment settings.

## What CANNOT Be Changed (Directly)

| Sound | Reason |
|-------|--------|
| Custom AVAS / Boombox | MCU firmware doesn't implement external speaker routing (0x32B1C042 FAILED) |
| Custom lock chirp | BCM generates lock sound directly — Android only receives lock events, doesn't play the sound |
| Custom power-on sound | MCU firmware doesn't implement (0xAA000243 FAILED) |
| Horn | Physical relay, hardware-controlled |
| Seatbelt warning chime | Safety-critical, BCM/Cluster |
| Boot animation sound | `/system` partition read-only, dm-verity protected |
| Route audio to exterior speaker | No external speaker in Android audio device list; MCU rejects routing commands |

### Lock Sound Architecture

The lock/unlock sound is generated **entirely by the BCM** (Body Control Module), not by Android:
```
[Key Fob] --> [BCM/PEPS Module] --> Lock/Unlock command
                    |
                    +--> BCM generates chirp directly (hardware-level)
                    |
                    +--> CAN bus notification --> [MCU] --> [IVI/Android]
                         (Android only RECEIVES the event via BODYWORK_REMOTE_CONTROL_LOCK 0x18000009)
```

The `AUDIO_LOCK_CAR_SOUND_EFFECT_PLAYBACK_STATUS_SET` (0xAA000321) has its feature ID mapped to 0 in the framework — it is a placeholder, not implemented on the Dolphin.

### Workaround: Supplementary Lock Sound App

An Android app could listen for lock/unlock CAN bus events and play a **supplementary** sound through the car's cabin speakers, layered on top of the BCM's native chirp:
- Listen for `BODYWORK_REMOTE_CONTROL_LOCK` (0x18000009) via `BYDAutoBodyworkDevice`
- Play custom audio through Android's `MediaPlayer` / `AudioTrack`
- Cannot REPLACE the BCM chirp, only ADD a sound on top of it

## Tesla vs BYD Comparison

| Feature | Tesla | BYD Dolphin |
|---------|-------|-------------|
| Custom AVAS | Yes (USB upload, "Boombox") | No — MCU doesn't implement routing |
| Custom horn | Yes (via Boombox) | No — hardware relay |
| External speaker | General-purpose, user-programmable | AVAS speaker exists but MCU-locked to presets |
| Custom lock sound | No | No — MCU rejects command |
| Upload mechanism | USB drive + folder structure | N/A |
| Engine sound | Limited | 3 UI presets, MCU accepts 1-255 via CAN |
| Sound profile | N/A | 2 profiles (Normal/Tech) via Vehicle Prompt Sound Source |

## Testing Guide (While Driving)

### Priority 1: Hidden AVAS Presets (drive at 0-30 km/h)

```bash
# Try each value, listen for different AVAS sounds while driving slowly
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 0"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 1"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 2"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 3"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 4"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x1B10003D 5"
# Reset to default: value 0 or 1
```

### Priority 2: Test/Diagnostic AVAS (drive at 0-30 km/h)

```bash
# These may trigger or reconfigure AVAS playback
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000104 1"  # TEST_AUDIO_AVAS_SET
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000171 1"  # TEST_MCU_AVAS_CONFIGURATION_SET
```

### Priority 3: Speaker/Amplifier Tests (parked, listen for sounds)

```bash
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000142 1"  # TEST_MCU_SPEAK_SET
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000148 1"  # TEST_PA_CONTROL_SET
```

### Priority 4: Hidden Engine Simulator Presets (while driving)

```bash
# UI shows 1-3, but MCU accepts 1-255. Try values 4-10 to find hidden sounds
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 4 1003"
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 5 1003"
# Reset to normal: value 1
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0x3E300038 1 1003"
```

### Summary Table

| Signal | What to Try | Why |
|--------|-------------|-----|
| AVAS_SOUND_SOURCE_SET_SET [0x1B10003D] | Values 0-5+ while driving 0-30 km/h | Hidden presets — MCU accepts far more than UI shows |
| TEST_AUDIO_AVAS_SET [0xAA000104] | Values 0-3 while driving | MCU accepts; may trigger/change AVAS playback |
| TEST_MCU_AVAS_CONFIGURATION_SET [0xAA000171] | Values 0-3 while driving | MCU accepts; may reconfigure AVAS behavior |
| TEST_MCU_SPEAK_SET [0xAA000142] | Value 1 while parked | MCU accepts; might trigger audible test |
| TEST_PA_CONTROL_SET [0xAA000148] | Value 1 while parked | MCU accepts; power amplifier control |
| ENGINE_SIMULATOR [0x3E300038] dev=1003 | Values 4-10 while driving | Hidden presets beyond UI's 3 options |

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

### Binary Data Path (setBuffer)

```
App → AbsBYDAutoDevice.set(deviceType, featureId, byte[])
    → BYDAutoDeviceManager.setBuffer()
    → BYDAutoManager.setBuffer()
    → native nativeSetBuffer() → JNI → libbydauto.so → SPI (/dev/spidev_ivi) → MCU
```

No content validation in Java layer — raw bytes pass through. MCU validates on its end.

### OTA Data Transfer Pipeline

`BYDAutoOtaDevice` (device type 1032) provides firmware update transport:
- `StartOTA()` → `sendOTAData(byte[])` → `FinishOTA()`
- `sendOTAData(byte[])` sends arbitrary binary via `setBuffer()` with featureId 979098
- Multi-frame protocol: `OTA_MULTI_FRAME_SET` (0xAA000140) sends, `OTA_MULTI_FRAME_ACK` (0x99000141) acknowledges
- Permissions: `android.permission.BYDAUTO_OTA_SET` / `BYDAUTO_OTA_GET`
- MCU likely validates OTA package signatures/checksums

### Signal Direction Convention

| Prefix | Direction | Purpose |
|--------|-----------|---------|
| 0x99 | MCU → SOC | Read / event notification |
| 0xAA | SOC → MCU | Write / command |
| 0x1B, 0x1C, 0x32 | SOC → MCU | Audio control writes |
| 0x4C, 0x4F, 0x35 | MCU → SOC | Audio status reads |
| 0x48 | MCU → SOC | Engine device reads |
| 0x3E | SOC → MCU | Engine device writes |

### Unexplored Test Signals

| Signal | Hex ID | Direction | Notes |
|--------|--------|-----------|-------|
| TEST_FLASH_MUSIC_VAL_SET | 0xAA000151 | SOC→MCU | "Flash music value" — may write sound data to MCU flash |
| TEST_DSP_STANDBY_STATE_SET | 0xAA000113 | SOC→MCU | DSP standby control |
| TEST_MCU_REPORT_DSP_VERSION_SET | 0xAA000247 | SOC→MCU | Request DSP version |
| CAR_CONFIG_ITEM_AVAS_AUDIO | 0x12020005 | config | AVAS audio vehicle configuration |

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
