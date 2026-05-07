# Sound & Theme Customization

## Vehicle Sound Architecture

The BYD Dolphin's sound system is more complex than initially documented. The Android head unit communicates with the vehicle's sound hardware via CAN bus (CANFD protocol) through the DiCarServer (`com.byd.car.server`).

Signal flow:
```
Android App → DiCarServer → BYDAutoAudioDevice HAL → MCU (CANFD) → BCM/Amplifier/External Speaker
```

### AVAS (Acoustic Vehicle Alerting System)

The AVAS is **partially controlled from the Android head unit**, not solely by a standalone ECU as previously documented. DiCarServer contains these CAN bus signals:

| Signal | R/W | Description |
|--------|-----|-------------|
| `AUDIO_AVAS_SOUND_SOURCE_SET_SET` | Write | Select AVAS sound source |
| `AUDIO_AVAS_SOUND_SOURCE_STATE` | Read | Current AVAS sound source state |
| `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` | Write | Route audio to external speaker |
| `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_STATUS` | Read | External speaker routing status |
| `AUDIO_AVAS_SOURCE_TYPE` | Config | AVAS source type |
| `AUDIO_AVAS_FAULT_STATUS` | Read | AVAS fault monitoring |
| `CAR_CONFIG_ITEM_AVAS_AUDIO` | Config | Vehicle AVAS audio capability flag |
| `TEST_AUDIO_AVAS_SET` | Write | Test/diagnostic AVAS command |
| `TEST_MCU_AVAS_CONFIGURATION_SET` | Write | MCU AVAS config test |

**Official options**: 2 presets via Vehicle Settings > Notification: "standard" and "brand" (with sub-options "standard" and "dynamic" that change pitch).

**AVAS behavior**: Volume increases 0-20 km/h, decreases 20-30 km/h, stops above 30 km/h. Continuous in reverse.

**Custom sound potential**: `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` suggests the head unit can route its own audio to the external AVAS speaker — similar to Tesla's Boombox approach. Needs further investigation via DiCarServer decompilation.

### External Speaker System

| Signal | R/W | Description |
|--------|-----|-------------|
| `AUDIO_EXTERIOR_SPEAKER_SWITCH_SET` | Write | External speaker on/off |
| `AUDIO_EXTERIOR_SPEAKER_SWITCH_STATUS` | Read | External speaker status |
| `AUDIO_EXTERIOR_SPEAKER_CONFIG` | Config | External speaker configuration |
| `AUDIO_EXTERIOR_PROMPT_TONE_SOURCE_SET` | Write | Set exterior prompt tone source |
| `AUDIO_EXTERIOR_PROMPT_TONE_SOURCE_STATUS` | Read | Exterior prompt tone status |

### Lock/Unlock & Power-On Sounds

| Signal | R/W | Description |
|--------|-----|-------------|
| `AUDIO_LOCK_CAR_SOUND_EFFECT_PLAYBACK_STATUS_SET` | Write | Lock car sound effect playback |
| `AUDIO_START_PLAY_POWER_ON_SOUND_SET` | Write | Power-on sound control |

The lock sound can be toggled on/off via Settings > Sound options, but customization beyond on/off requires understanding the signal values.

### ESS (External Sound System) — Amplifier Control

| Signal | R/W | Description |
|--------|-----|-------------|
| `AUDIO_ESS_AMPLIFIER_CONFIGURATION` | Config | ESS amplifier config |
| `AUDIO_ESS_AUDIO_SOURCE_PREVIEW_1B1_SET` | Write | Audio source preview |
| `AUDIO_ESS_SETTING_STATUS` | Read | ESS setting status |
| `AUDIO_ESS_VOLUME_GEAR_CONFIG` | Config | ESS volume gear config |
| `AUDIO_ESS_VOLUME_SETTING_SET` | Write | ESS volume setting |

### Radar/Parking Sensor Sounds (Controlled from Android)

Contrary to initial findings, parking radar sounds are controlled from Android:

| Signal | R/W | Description |
|--------|-----|-------------|
| `AUDIO_RADAR_SOUND_LF/LR/RF/RR` | Read | Per-corner radar sound |
| `AUDIO_RADAR_SOUND_SOURCE` | Config | Radar sound source |
| `AUDIO_RADAR_SOUND_VOLUME` | Config | Radar sound volume |

### Cabin Audio Processing

The head unit controls extensive audio processing:
- 5-band equalizer
- Bass, midrange, treble controls
- Sound field focus (X/Y positioning)
- Front/rear, left/right balance
- Devialet sound processing (50Hz–8000Hz)
- 3D / Space sound effects
- ANC (Active Noise Cancellation) configuration
- Loudness control

### BYD-Specific Audio Streams

Beyond standard Android audio:
- `STREAM_FM` — FM radio
- `STREAM_AUX` — auxiliary input
- `STREAM_NAVI` — navigation
- `STREAM_MUTE` — mute control
- `STREAM_TTS` — text-to-speech

## What CAN Be Changed (Head Unit)

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
adb shell settings put system sound_effects_enabled 1       # UI touch sounds
adb shell settings put system charging_sounds_enabled 1      # Charging connection sound
adb shell settings put system lockscreen_sounds_enabled 1    # Lock screen sounds
adb shell settings put system dtmf_tone 1                    # DTMF tones
```

### Custom Notification Sound
```bash
adb push my_sound.ogg /sdcard/Notifications/
```

### AVAS Preset Selection
Via Vehicle Settings > Notification, choose between "standard" and "brand" with sub-options.

### Simulated Sound Wave / Engine Sound
Toggle on/off via Vehicle Settings.

### Turn Signal Sound
Changeable to different presets through infotainment settings.

## What Remains Unknown / Needs Investigation

| Sound | Status | Next Step |
|-------|--------|-----------|
| Custom AVAS sound | Promising — writable CAN signals exist | Decompile DiCarServer to find accepted values |
| Custom lock chirp | Writable signal exists | Investigate `AUDIO_LOCK_CAR_SOUND_EFFECT_PLAYBACK_STATUS_SET` values |
| Route audio to external speaker | Signal exists | Test `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET` |
| Power-on sound | Writable signal exists | Test `AUDIO_START_PLAY_POWER_ON_SOUND_SET` |
| Custom radar sounds | Configurable source/volume | Test `AUDIO_RADAR_SOUND_SOURCE` values |

## What CANNOT Be Changed

| Sound | Reason |
|-------|--------|
| Horn | Physical relay, hardware-controlled (volume adjustable via knob below steering wheel) |
| Seatbelt warning chime | Safety-critical, BCM/Cluster |
| Boot animation sound | `/system` partition read-only, dm-verity protected |

## Tesla vs BYD Comparison

| Feature | Tesla | BYD Dolphin |
|---------|-------|-------------|
| Custom AVAS | Yes (USB upload, "Boombox") | 2 presets, but CAN signals suggest routing possible |
| Custom horn | Yes (via Boombox) | No — hardware relay |
| External speaker | General-purpose, user-programmable | Exists, CAN-controlled, locked to presets |
| Custom lock sound | No | Unknown — writable signal exists |
| Upload mechanism | USB drive + folder structure | None in UI — would need app/CAN-level access |

## DiCarServer Analysis

The DiCarServer APK has been extracted to `data/apks/DiCarServer_extracted/`.

Key files:
- `classes.dex` — main code with signal name constants and AudioFeatureHandler
- `config_1.bin` (69KB), `config_2.bin` (32KB), `config_3.bin` (6KB) — compiled protobuf vehicle configs
- `.proto` files in assets — compiled binary protobuf data (not text definitions)
- Text `.proto` files at root — Google Maps/ADAS related, not BYD signal definitions

Relevant classes:
- `com.byd.audio.AudioFeatureHandler` — primary audio property handler
- `BYDAutoAudioDevice` (`android.hardware.bydauto.audio`) — HAL bridge to CAN bus
- `BYDAutoBodyworkDevice` — door/lock operations (separate from sound)

Permissions held:
- `BYDAUTO_AUDIO_GET`, `BYDAUTO_AUDIO_SET`, `BYDAUTO_AUDIO_COMMON`
- `MODIFY_AUDIO_SETTINGS`, `MODIFY_AUDIO_ROUTING`

## Theme System

### BYD Theme Store
- Package: `com.byd.automultipletheme`
- Visual themes only — no sound theming found
- Permission: `com.android.permission.CHANGE_BYD_APP_THEME`

### Wallpaper
- Package: `com.byd.wallpaperhome`
- Permissions: `com.byd.wallpaper.permission.READ_SETTINGS`, `WRITE_SETTINGS`, `RECEIVE_WALLPAPER_BROADCASTS`

### Quick Settings Panel
Currently selected tiles:
```
simulator, data, connectdevice, wire_charge, energy_recycle, esp
```

Available but not shown:
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
- `/system/media/bootanimation.zip` (28MB, image sequence)
- `/system/media/bootanimation_720p.zip` (16MB, 720p variant)
- `/system/media/video/bootanimation.mp4` (8.9MB, video)
- `/system/media/bootanimation_porth.zip` (18MB, portrait)

Format: Standard Android boot animation (ZIP containing PNG frames + `desc.txt`).

**Cannot be replaced without root** — system partition is read-only and dm-verity protected.

```bash
adb pull /system/media/bootanimation.zip ./
unzip bootanimation.zip -d bootanimation/
cat bootanimation/desc.txt
```
