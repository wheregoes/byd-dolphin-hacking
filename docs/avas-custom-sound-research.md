# Custom AVAS Sound Research

## Goal
Upload custom sounds (like Tesla Boombox) to the AVAS (pedestrian warning) speaker.

## Architecture
- **AVAS speaker** = MCU-controlled, NOT in Android audio HAL
- Android → `auto` service → `setInt(D, featureId, value)` → CAN bus → MCU → AVAS speaker
- AVAS sounds stored in MCU flash memory (not accessible from Android)
- No SocketCAN interface on head unit — all CAN handled by MCU

## Feature IDs Discovered

### Exterior Speaker (NOT SUPPORTED on Dolphin)
From `Audio.java` in DiCarServer/CarSetting decompiled code:
- `AUDIO_EXTERIOR_SPEAKER_CONFIG (0x35201036)` — Returns -10011 (not supported)
- `AUDIO_EXTERIOR_SPEAKER_SWITCH_SET (0x1C10000E)` — Not accepted
- `AUDIO_EXTERIOR_SPEAKER_SWITCH_STATUS (0x35201040)` — Returns -10011
- `AUDIO_AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET (0x32B1C042)` — Not supported

**Conclusion**: Higher-end BYD models (Han, Tang, Seal) have an exterior speaker
feature that routes Android audio to the AVAS speaker. The Dolphin does NOT support this.

### Test Commands (WORKING — 2 pitches only)
- `TEST_AUDIO_AVAS_SET (0xAA000104)` — Pitch: 1=A (lower), 2=B (higher), 0=silence
- `TEST_MCU_SPEAK_SET (0xAA000142)` — Enable MCU speaker path
- `TEST_PA_CONTROL_SET (0xAA000148)` — Enable PA amplifier
- `TEST_FM_SPEAK_SET (0xAA00011A)` — Enable FM speak path
- `TEST_MCU_AVAS_CONFIGURATION_SET (0xAA000171)` — AVAS configuration
- `TEST_FLASH_MUSIC_VAL_SET (0xAA000151)` — Flash music value (untested)
- `TEST_CMD_TEST_AUDIO_AVAH_SET (0x6E970010)` — AVAH test tone

### Other Relevant IDs
- `AUDIO_EXTERIOR_PROMPT_TONE_SOURCE_SET (0x1B100043)` — Not supported
- `CAR_CONFIG_ITEM_AVAS_AUDIO (0x12020005)` — Returns -10011

## HAL Analysis (auto.default.so)
- `byd_auto_hal::FunctionTable::avasmatch` — Matches vehicle speed to AVAS sound
- `byd_auto_hal::FunctionTable::audioroute` — Audio routing control
- `byd_auto_hal::FunctionTable::c_soundfield` — Sound field control
- `BYDAutoDeviceManager.setBuffer(int, int, byte[])` — Can send byte arrays to MCU
- Binary is stripped, analysis limited without Ghidra

## Test Results
Test APK (`com.wheregoes.avastest`) built and deployed:
- Auto service: **CONNECTED** (`android.hardware.BYDAutoManager`)
- All exterior speaker features: **-10011** (NOT SUPPORTED)
- Test commands (setInt): **WORKING** (setInt succeeds even though getInt returns -10011)
- AVAS 2-pitch playback: **CONFIRMED** (same as door-sound app)

## Options for Custom AVAS Sound

### Option 1: MCU Firmware Modification
- Extract MCU firmware via UDS diagnostic (Service 0x34/0x36/0x37)
- Replace sound data in MCU flash
- Reflash via UDS or OTA
- **Risk**: Brick the MCU, void warranty

### Option 2: Hardware Approach
- Tap into AVAS speaker wires (find connector near front bumper)
- Add ESP32 + amplifier + audio file storage
- Control via WiFi from Android app
- **Pros**: No firmware risk, full custom audio
- **Cons**: Hardware modification, additional power needed

### Option 3: CAN Bus PCM Streaming
- Research if MCU accepts PCM audio data via CAN messages
- Use `setBuffer` to send audio frames to MCU
- **Unknown**: Whether MCU has this capability

### Option 4: setBuffer Data Injection
- Test `setBuffer(1034, featureId, audioData)` with various feature IDs
- Some feature IDs might accept raw audio data
- **Untested**: Need to identify correct feature ID

## Relevant Files
- `apk-analysis/DiCarServer_decompiled/sources/com/byd/feature/audio/Audio.java` — All audio feature IDs
- `apk-analysis/CarSetting_decompiled/sources/com/byd/ccs/impl/server/sound/Sound00300200190000.java` — Exterior speaker implementation
- `apk-analysis/CarSetting_decompiled/sources/com/byd/dilink/view/sound/views/ExternalSpeakerCommon.java` — Exterior speaker UI
- `data/native-libs/auto.default.so` — BYD auto HAL with avasmatch/audioroute functions
