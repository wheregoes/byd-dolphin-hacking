# Sound & Theme Customization

## Vehicle Sound Architecture

The BYD Dolphin's sound system communicates via CAN bus (CANFD protocol) through DiCarServer (`com.byd.car.server`).

### Audio Hardware Topology

```
                                    ┌──────────────────────────────────────┐
                                    │              MCU DSP                 │
  SoC (SM6125)                      │                                      │
  ┌──────────┐    I2S    ┌─────┐    │    ┌─────────────────────┐           │
  │ Qualcomm ├──────────►│ MCU ├────┼───►│     A2B Bus         │           │
  │  ADSP    │           │     │    │    │  (Analog Devices     │           │
  └──────────┘           │     │    │    │   Automotive Audio)  │           │
       ▲                 │     │    │    └──┬──────────┬────────┘           │
       │                 └──┬──┘    │       │          │                    │
  SPI (/dev/spidev_ivi)     │       │       ▼          ▼                   │
       │                    │       │   Main Amp    AVAS Amp               │
  ┌────┴─────┐              │       │       │          │                   │
  │BYDAuto   │   CAN bus    │       │       ▼          ▼                   │
  │Manager   ├──────────────┘       │   Cabin      External               │
  │(libbyd   │  commands            │   Speakers   Speaker                 │
  │ auto.so) │                      │              (pedestrian)            │
  └──────────┘                      └──────────────────────────────────────┘
```

The SoC sends audio data to the MCU via I2S. The MCU's DSP is the master of the
A2B (Analog Devices Automotive Audio Bus) and decides all routing to speakers.
There is NO direct audio path from the SoC to the AVAS speaker — it must go
through the MCU's DSP.

### CAN Bus Signal Flow

```
Android App → DiCarServer → BYDAutoManager (JNI → libbydauto.so) → SPI → MCU → A2B → Amplifiers
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
| AMPLIFIER_TYPE | 0x99000214 | 1002 | 0 | Amplifier type |
| OTA_REMOTE_CONFIG_DSP_SOUND_SOURCE_PACKAGE | 0x99000223 | 1002 | ? | DSP sound bank OTA update status |

#### AVAH Test Tones (CONFIRMED WORKING - audible on AVAS speaker)

| Signal | Hex ID | Device | R/W | MCU Result | Notes |
|--------|--------|--------|-----|------------|-------|
| TEST_CMD_TEST_AUDIO_AVAH | 0x6EA70010 | 1002 | R | OK | Returns 65535 (0xFFFF) |
| TEST_CMD_TEST_AUDIO_AVAH_SET | 0x6E970010 | 1002 | W | **SUCCESS** | **CONFIRMED**: 0=stop, 1=1kHz, 2=2kHz, 3=3kHz |

Prerequisite: AVAS must be enabled in Vehicle Settings > Notification. Tone is continuous until stopped.

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

**AVAH test tones — CONFIRMED WORKING**: `TEST_CMD_TEST_AUDIO_AVAH_SET` (0x6E970010) plays test tones on the AVAS external speaker. Both GET and SET have **real non-zero feature IDs** (1855389712 SET, 1856438288 GET) — fully implemented in the HAL. Values: 0=stop, 1=1kHz, 2=2kHz, 3=3kHz. **Tested and verified**: 1kHz tone is audible from outside the car through the AVAS speaker.

**Prerequisites**: AVAS must be **enabled** in Vehicle Settings > Notification. When AVAS is disabled, the amplifier is powered off and no sound is produced even though the MCU accepts the command (returns SUCCESS).

**Behavior**: The tone is continuous until stopped with value 0, or by toggling the AVAS switch off in settings. The `app_process` command takes ~3 seconds to start, so toggling AVAS off in settings is faster for emergency stop.

**Sound source channels**: The DSP manages parallel channels, each with a SET/STATE signal pair. All channels select MCU-stored presets — none routes SoC audio:

| Channel | SET Signal | STATE Signal |
|---------|-----------|-------------|
| Media | 0x1B10001C | 0x4C60000C |
| Radar | 0x1B100025 | 0x4C600015 |
| Navigation | 0x1B10002D | 0x4C60001D |
| ANC | 0x1B100035 | 0x4C600025 |
| **AVAS** | 0x1B10003D | 0x4C60002D |
| INS | 0x1B100045 | 0x4C600035 |
| BD | 0x1C100026 | 0x4FD0001E |

**Navigation I2S path tested**: The SoC has two I2S output buses to the MCU:
1. `TERT_MI2S_RX` — main audio → cabin speakers
2. `QUAT_MI2S_RX` — navigation guidance → cabin speakers (NOT AVAS)

Playing audio with `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` (AudioAttributes usage=12) routes through `QUAT_MI2S_RX`, but the MCU sends it to the cabin speakers only. Tested with 440Hz tone — audible on cabin speakers, silent on AVAS. CAN commands to re-route (`0x32B1C042`, `0x1C10000E`, `0x1B100043`, `0xAA000301`) all return FAILED. `AUDIO_SOC_NOTIFY_MCU_CONTROL_DSP_SET` (0xAA000145) returns SUCCESS but does not change routing.

**Boombox / custom sound verdict**: The only confirmed path to the AVAS speaker is the AVAH test tone (0x6E970010, values 1-3 = sine waves). The MCU generates these internally in its DSP — no Android audio data reaches the AVAS speaker. The A2B bus connecting DSP to amplifiers is MCU-mastered. Without MCU firmware modification, arbitrary audio on the AVAS speaker is not possible.

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
| Custom AVAS / Boombox | Direct routing (0x32B1C042) FAILED, but AVAH test tones (0x6E970010) WORK — partial access confirmed |
| Custom lock chirp | BCM generates lock sound directly — Android only receives lock events, doesn't play the sound |
| Custom power-on sound | MCU firmware doesn't implement (0xAA000243 FAILED) |
| Horn | Physical relay, hardware-controlled |
| Seatbelt warning chime | Safety-critical, BCM/Cluster |
| Boot animation sound | `/system` partition read-only, dm-verity protected |
| Route audio to exterior speaker | A2B bus is MCU-mastered; SoC has no direct path to AVAS amplifier |

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

## Testing Guide

### Audio Routing Test Tool

Build the comprehensive audio routing test tool:
```bash
javac -source 11 -target 11 -d /tmp/bydroute scripts/BydAudioRoutingTest.java
d8 --output /tmp/bydroute /tmp/bydroute/BydAudioRoutingTest.class
adb push /tmp/bydroute/classes.dex /data/local/tmp/bydroute.dex
```

### Priority 0: AVAH Test Tone (PARKED — confirms AVAS hardware works)

**This is the first thing to test.** It plays a test tone directly on the AVAS speaker.
```bash
# Run full diagnostics first (read-only, safe):
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest diag"

# Play 1kHz test tone on AVAS speaker:
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest avah 1"

# Stop the test tone:
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest avah 0"
```
If you hear a 1kHz tone from outside the car, the AVAS speaker hardware path is confirmed working.

### Priority 1: Audio Routing Combination Attack (PARKED)

Tries multiple signals in sequence to enable external speaker routing:
```bash
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest combo"
```
This enables the exterior speaker, wakes the DSP, enables loopback, unmutes the UE channel, routes AVAS to external, and plays a test tone.

### Priority 2: Individual Route Tests (PARKED)

```bash
# Test exterior speaker enable + source routing:
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest route"

# Test loopback / DSP passthrough:
adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest loopback"
```

### Priority 3: Hidden AVAS Presets (drive at 0-30 km/h)

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

### Priority 4: Test/Diagnostic AVAS (drive at 0-30 km/h)

```bash
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000104 1"  # TEST_AUDIO_AVAS_SET
adb shell "CLASSPATH=/data/local/tmp/bydquery.dex app_process /data/local/tmp BydAudioQuery set 0xAA000171 1"  # TEST_MCU_AVAS_CONFIGURATION_SET
```

### Priority 5: Hidden Engine Simulator Presets (while driving)

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
| **AVAH_SET [0x6E970010]** | **Values 1-3 while parked** | **Plays test tones on AVAS speaker — proves hardware works** |
| EXT_SPEAKER_SWITCH [0x1C10000E] | Value 1 | Enable exterior speaker before routing |
| AVAS_TO_EXT_SPEAKER [0x32B1C042] | Values 0-3 | Route audio source to external speaker |
| LOOPBACK_PASSAGE [0xAA000301] | Values 0-2 | Open loopback/passthrough audio path |
| SOC_CONTROL_DSP [0xAA000145] | Values 0-3 | Put DSP in different modes |
| UE_MUTE [0xAA000346] | Value 0 | Unmute possible external channel |
| AVAS_SOURCE [0x1B10003D] | Values 0-5+ while driving | Hidden presets |
| TEST_AUDIO_AVAS [0xAA000104] | Values 0-3 while driving | Factory AVAS test |
| TEST_MCU_AVAS_CONFIG [0xAA000171] | Values 0-3 while driving | AVAS configuration |
| ENGINE_SIMULATOR [0x3E300038] dev=1003 | Values 4-10 while driving | Hidden presets beyond UI |

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

### Factory Test Signals

| Signal | Hex ID | Direction | Feature ID | Notes |
|--------|--------|-----------|------------|-------|
| TEST_FLASH_MUSIC_VAL_SET | 0xAA000151 | SOC→MCU | -1442840239 | Integer selector for pre-stored sound slot in MCU flash (NOT audio upload) |
| TEST_AUDIO_AVAS_SET | 0xAA000104 | SOC→MCU | -1442840316 | Fully implemented, non-zero feature ID |
| TEST_MCU_AVAS_CONFIGURATION_SET | 0xAA000171 | SOC→MCU | 0 (stub) | Works via hex fallback path |
| TEST_FM_SPEAK_SET | 0xAA00011A | SOC→MCU | -1442840294 | Implemented |
| TEST_MCU_SPEAK_SET | 0xAA000142 | SOC→MCU | 0 (stub) | NOT implemented on Dolphin |
| TEST_PA_CONTROL_SET | 0xAA000148 | SOC→MCU | 0 (stub) | NOT implemented on Dolphin |
| TEST_DSP_STANDBY_STATE_SET | 0xAA000113 | SOC→MCU | ? | DSP standby control |
| TEST_MCU_REPORT_DSP_VERSION_SET | 0xAA000247 | SOC→MCU | ? | Request DSP version |
| CAR_CONFIG_ITEM_AVAS_AUDIO | 0x12020005 | config | ? | AVAS audio vehicle configuration |

Note: Feature ID = 0 means "stub" — the signal is not wired in `BYDAutoFeatureIds` but can still be sent via the hex string fallback in AudioMapper/TestMapper.

### No Audio File Transfer Mechanism

Confirmed: There is NO mechanism in the BYD Dolphin's software to upload custom audio files (WAV, PCM, etc.) to the MCU/DSP. All sounds in the MCU are programmed at the firmware level during manufacturing. The `setBuffer()` API sends raw bytes but is used for metadata (song titles, OTA firmware chunks) — not audio samples. The `TEST_FLASH_MUSIC_VAL_SET` signal is an integer selector for pre-stored sound slots, not a data transfer channel.

### Full SPI Communication Stack

```
App (Java, UID 1000 via app_process)
  → BYDAutoManager.setInt/setBuffer (Binder client proxy)
    → Binder IPC
      → BYDAutoServer / autoservice (PID 77, system binary)
        → libbydautoservice.so (137KB, Binder server stub, permission checks)
          → auto.default.so (1MB, HAL module)
            → MsgCodec: encodes featureId → canid (12-bit) + subid (8-bit)
            → FeatureMapper: maps featureIds to CAN message IDs
            → AutoInterface: SPI transport
              → /dev/spidev_ivi (spidev_full_duplex driver)
                → MCU
```

**Key security findings:**
- No per-packet cryptographic authentication on regular commands (MD5 only for OTA)
- `/dev/spidev_ivi` permissions: `system:system rw-rw----` — accessible by app_process (UID 1000)
- Raw SPI access possible by bypassing entire Binder/HAL stack
- Native libraries pulled to `data/native-libs/` for analysis

### MCU Probe Results (BydMcuProbe.java)

Comprehensive MCU command testing performed with `scripts/BydMcuProbe.java`:

**BYDAutoManager Full API:**
- `setInt(dev, fid, val)` / `getInt(dev, fid)` — single integer
- `setBuffer(dev, fid, byte[])` / `getBuffer(dev, fid)` — binary data
- `setIntArray(dev, fid[], val[])` / `getIntArray(dev, fid[])` — batch int
- `setDouble(dev, fid, val)` / `getDouble(dev, fid)` — floating point
- `setDoubleArray` / `getDoubleArray` — batch double
- `enableDevice(dev)` / `disableDevice(dev)` — device control
- `registerListener` / `unregisterListener` — event callbacks

**setBuffer on AVAH (0x6E970010):**
- Buffers 1-128 bytes → SUCCESS (MCU accepts)
- Buffers 256+ bytes → MCU_FAILED (-2147482648)
- **Buffer size limit: 128 bytes** per SPI frame
- PCM audio injection (16000 bytes) → MCU_FAILED (too large for single frame)
- No echo: getBuffer after setBuffer returns all zeros
- Empty buffer → MCU_FAILED

**Extreme values on AVAH:**
- MCU accepts ALL int values (-2147483648 to 2147483647) — never errors
- AVAH_STATE readback (0x6EA70010) always returns 65535 regardless of value sent
- MCU truncates internally; known working tones are values 1 (1kHz), 2 (2kHz), 3 (3kHz)

**Nearby featureId scan (0x6E97xxxx):**
- ONLY 0x6E970010 accepts setInt in the entire 0x6E970000-0x6E97001F range
- ONLY 0x6EA70010 returns data for getInt in 0x6EA70000-0x6EA7001F
- **NEW: 0x6E990010 also accepts setInt** — in the audio debug range (0x6E99xxxx)
- AVAH works on ALL device types (1001-1041) — not device-restricted

**Audio debug mode (0x6E990008):**
- `setInt(1002, 0x6E990008, 1)` → SUCCESS (enters debug mode)
- Routing commands (0x32B1C042, 0xAA000301, 0x1C10000E) still FAIL in debug mode
- Debug mode does NOT unlock routing — MCU firmware hardcodes the rejection

### SPI Packet Format (from auto.default.so reverse engineering)

```
[featureId_BE:4][dataLen:1][data:dataLen]
```

No CRC, no HMAC, no sequence numbers. Completely unprotected.

| Function | dataLen | Layout | Total |
|----------|---------|--------|-------|
| Query | 0 | `[fid:4][0x00]` | 5 |
| Set char | 1 | `[fid:4][0x01][char]` | 6 |
| Set int | 4 | `[fid:4][0x04][value_BE:4]` | 9 |
| Set string | N (max 248) | `[fid:4][N][string:N]` | 5+N |

- Max write per syscall: 252 bytes. Larger payloads chunked automatically.
- Response: 260 bytes per SPI poll. Header `0x9900001D` = valid.
- Records parsed with same `[fid:4][len:1][data:len]` format.
- Device: `/dev/spidev_ivi` opened with `O_RDWR | O_NONBLOCK`
- MCU wake: `/sys/qc_mcu/qc_wakeup_mcu`
- MD5 only used for version hashing (salt: `tFjx4#Gyn!5ZbKC6u3lh3Izu%P5i25w%`), not packet auth.
- The 128-byte setBuffer limit seen via Java API is artificial — SPI supports up to 247 bytes/record.
- Direct SPI access requires UID 1000 (system) — ADB shell (UID 2000) gets EACCES.

### Debug Range Scan (0x6E99xxxx)

Three writable debug featureIds found:

| FeatureId | Purpose | Notes |
|-----------|---------|-------|
| 0x6E990008 | Audio debug mode | Accepts setInt, does NOT unlock routing |
| 0x6E990010 | Debug AVAH | Accepts all values (0-255), mirrors regular AVAH |
| 0x6E990040 | Unknown debug function | Accepts setInt and setBuffer, purpose unknown |

### 0xAA000xxx Test Range Scan

**63 writable featureIds** found in 0xAA000100-0xAA000303 range. All return SUCCESS but
produce no observable state changes — likely gated behind factory test mode.

Accepted IDs: 0xAA000101-106, 108-10A, 10D-114, 11A, 11E-11F, 121-124, 140, 142-143,
145, 148-149, 151, 153, 156-158, 161, 165-16A, 170-171, 173-178, 182-183, 194,
206, 20B, 20F-210, 221, 241, 244, 255, 286, 299, 303.

### setDouble / setIntArray Tests

- `setDouble` on AVAH: ALL values MCU_FAILED — MCU only accepts int on these featureIds
- `setIntArray`: method call fails with null — likely unsupported or different signature

### setBuffer Structured Data Tests

All buffer formats accepted on AVAH (SUCCESS returned) but **unclear if buffer content
affects the generated tone**. MCU may ignore buffer data and only respect setInt value.

Formats tested:
- 1-byte command codes: all accepted
- 2-byte frequency encoding (16-bit BE): all accepted
- 4-byte LE/BE integers: all accepted
- 6-byte [freq:2][dur:2][vol:1][wave:1]: all accepted
- 128-byte PCM: accepted
- setBuffer on routing FIDs: still FAIL even in debug mode

### Command Sequence Tests

Tried multiple sequences to unlock AVAS routing — ALL FAILED:
1. Debug mode → DSP control → routing → tone: routing still rejected
2. TEST_AUDIO_AVAS → TEST_MCU_AVAS_CONFIG → tone: routing still rejected
3. 0x6E990040 (unknown debug) → debug mode → routing: still rejected
4. setBuffer on AVAS_SOURCE (0x1B10003D): accepts values 0-255 via buffer

**Conclusion: AVAS routing is hardcoded FAIL in MCU firmware. No command sequence,
debug mode, or buffer format unlocks it.**

### OTA Pipeline

- `enableDevice(1032)` → SUCCESS — OTA device accessible
- `setBuffer` on 0xAA000140 (OTA_MULTI_FRAME_SET) dev=1032 → SUCCESS
- OTA_MULTI_FRAME_ACK (0x99000141) returns frame count 7
- 0xAA000223 (DSP sound source package) setBuffer → MCU_FAILED
- OTA write commands (0xAA000206, 221, 241, 244) all accept on dev 1032

### MCU Buffer Data (getBuffer scan)

49 readable buffers found in 0x99000xxx range. Key decoded values:

| FeatureId | Data | Meaning |
|-----------|------|---------|
| 0x99000001 | `13.5.2.2312260.1` | MCU firmware version (Dec 2023) |
| 0x99000002 | `13.5.5.2505300.2` | DSP firmware version (May 2025) |
| 0x99000035 | `[YOUR_DEVICE_ID]` | Device ID |
| 0x9900021a | `[YOUR_VIN]` | VIN / part number |
| 0x99000118 | `[18485702]RESF=0x201;rsfSt=6...` | RF status (244 bytes) |
| 0x9900010a | (244 bytes binary) | Large config buffer |
| 0x9900011c | `2504183` | Serial / date code |

Many signals return `MCU_OFFLINE` (hex `4d43555f4f46464c494e45`).

### AVAH Tone Reliability Issue

**CRITICAL**: After extensive testing with debug/test commands, the AVAH test tone
stopped producing audible sound. MCU still returns SUCCESS (0) but no tone plays.
The issue persists through:
- AVAS toggle off/on in Vehicle Settings
- Full car power cycle (ignition off/on)
- enableDevice(1002) call
- All values (1, 2, 3) and all device types tested

Normal AVAS driving sound (pedestrian warning < 30 km/h) continues to work normally,
confirming the speaker and amplifier are functional. Only the diagnostic AVAH test
tone path is broken.

**Suspected cause**: One or more of the 0xAA000xxx test commands (particularly
0xAA000104, 0xAA000171, 0xAA000145, 0xAA000113) may have persistently changed MCU
EEPROM/flash configuration that controls the diagnostic test tone subsystem.

### Privilege Escalation Assessment

#### Bootloader Status (CRITICAL FINDING)

**The bootloader is UNLOCKED.** This is the simplest path to root.

| Property | Value |
|----------|-------|
| `ro.boot.flash.locked` | **0** (unlocked) |
| `ro.boot.verifiedbootstate` | **orange** (custom boot allowed) |
| `ro.oem_unlock_supported` | 1 |
| `ro.debuggable` | 0 |
| `ro.secure` | 1 |
| Boot slot | `_b` (A/B partitions) |
| `boot_b` | `/dev/block/sde30` (root-only: `brw-------`) |
| `recovery_b` | `/dev/block/sda8` |
| `vbmeta_b` | `/dev/block/sde35` |

**Root via Magisk**: Extract boot image → patch with Magisk → flash via fastboot.
Blocker: boot partition is root-only from ADB shell. Need USB fastboot access to
extract and flash boot image.

#### Kernel Exploit Assessment

Kernel: `4.14.117-perf` (aarch64, clang 8.0.12, built Jul 2025)
Security patch level: **2023-02-05** (3+ years behind)
SELinux: Enforcing, `u:r:shell:s0`

**Kernel hardening:**
- KASLR enabled (`CONFIG_RANDOMIZE_BASE=y`)
- Stack protector strong (`CONFIG_CC_STACKPROTECTOR_STRONG=y`)
- Hardened usercopy (`CONFIG_HARDENED_USERCOPY=y`)
- Slab freelist hardened (`CONFIG_SLAB_FREELIST_HARDENED=y`)
- kallsyms blocked (0 lines readable)
- `/proc/timer_list`, `/proc/sched_debug`, `/proc/iomem`: Permission denied
- dmesg: blocked by SELinux

**Disabled mitigations:**
- CONFIG_USER_NS is not set
- CONFIG_USERFAULTFD is not set
- CONFIG_BPF_JIT is not set
- CONFIG_KASAN is not set

**Attack surface probed:**

| Vector | Device | Access | Result |
|--------|--------|--------|--------|
| `/dev/binder` | crw-rw-rw- | World writable | BINDER_THREAD_EXIT returns EINVAL — CVE-2019-2215 likely patched |
| `/dev/kgsl-3d0` | crw-rw-rw- | World writable | Properties/alloc work, but DRAWCTXT_CREATE returns EINVAL on ALL flags |
| BPF syscall | CONFIG_BPF_SYSCALL=y | — | EPERM — blocked by SELinux/capabilities |
| perf_event_open | CONFIG_PERF_EVENTS=y | — | EPERM |
| userfaultfd | not compiled | — | ENOSYS |
| CLONE_NEWUSER | not compiled | — | EINVAL |
| `/dev/snd/*` | system:audio | Not in audio group | Can't access ALSA devices |
| `/dev/adsprpc-smd` | system:system rw-r-- | Read only | Can't write to ADSP |

**KGSL ioctl map** (Adreno 610, chip ID 0x6010000):

Ioctls that return SUCCESS with empty data: `0x38, 0x39, 0x3a, 0x40, 0x41, 0x45`
Ioctls that exist but need valid args: `0x02, 0x07, 0x10, 0x13, 0x14, 0x16, 0x17,
0x21, 0x24, 0x2f, 0x32-0x37, 0x3b-0x3d, 0x42, 0x43, 0x46, 0x47, 0x4a, 0x4c`
EFAULT (reads user mem): `0x49`
ENOTSUP: `0x15, 0x20, 0x4b`

**CVE candidates assessed:**

| CVE | Type | Viable? | Notes |
|-----|------|---------|-------|
| CVE-2019-2215 | Binder UAF | **No** | BINDER_THREAD_EXIT returns EINVAL — patched |
| CVE-2023-33106 | KGSL AUX OOB | **Maybe** | ioctl 0x41 responds, but no GPU context possible |
| CVE-2023-33107 | KGSL integer overflow | **Maybe** | GPUOBJ_IMPORT exists but context creation blocked |
| CVE-2024-43047 | ADSP UAF | **No** | /dev/adsprpc-smd not writable from shell |
| CVE-2023-0266 | ALSA UAF | **No** | /dev/snd/* not accessible from shell |
| CVE-2026-31431 | kernel crypto (Copy Fail) | **No** | No Python, no setuid, SELinux blocks AF_ALG |

**Conclusion**: Kernel exploits are blocked by SELinux restricting device ioctls
from shell context. The unlocked bootloader + Magisk via fastboot is the viable path.

#### What Root Enables

- Direct `/dev/spidev_ivi` access (bypass 128-byte Java limit, send 247-byte SPI records)
- Read/write ALSA mixer controls (potential AVAS audio routing)
- Modify `/system` partition (custom boot animation, preinstalled apps)
- Read `/proc/kallsyms`, dmesg, full kernel state
- Potentially restore AVAH test tone by resetting MCU config directly via SPI
- Install system-level apps with BYDAUTO permissions

### Pending Investigation

| Test | Status | Notes |
|------|--------|-------|
| Diagnose AVAH tone failure | **CRITICAL** | Tone stopped working after test commands, survives power cycle |
| **Root via Magisk + fastboot** | **READY** | Bootloader unlocked; need USB access for fastboot |
| Reverse EEPROM config change | Not started | Need root + direct SPI to reset MCU config |
| Direct /dev/spidev_ivi access | Blocked | Needs root (UID 1000+), ADB shell is UID 2000 |
| OTA pipeline for DSP sound package | Partially probed | Data path works but DSP sound source rejects buffer |
| AVAS presets while driving (0x1B10003D) | Not tested | Values 0-5+ — must test at low speed |
| PCM streaming via rapid setBuffer | Inconclusive | Buffers accepted but unclear if content affects output |

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
