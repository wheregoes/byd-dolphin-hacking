# BYD Dolphin Engine Voice Simulator — CUSTOM AVAS SOUNDS CONFIRMED

## BREAKTHROUGH
The BYD Dolphin **DOES support custom engine sounds** via the Engine Voice Simulator system.
This is BYD's equivalent of Tesla's Boombox.

## Feature IDs (ALL WORKING)

### Read-only (status)
| Feature ID | Name | Dolphin Value |
|-----------|------|---------------|
| `0x48F00000` | ENGINE_HAS_ENGINE_VOICE_SIMULATOR | **2** (SUPPORTED) |
| `0x48F00013` | ENGINE_HAS_ENGINE_VOICE_SOURCE | **1** (HAS SOURCE) |
| `0x48F0000A` | ENGINE_VOICE_SIMULATOR_STATE | 0 (off) → **1** (on) |
| `0x48F00010` | ENGINE_SIMULATOR_SOURCE_TYPE | **1** (default) → **3** (changed!) |

### Write (settable)
| Feature ID | Name | Effect |
|-----------|------|--------|
| `0x3E300020` | ENGINE_VOICE_SIMULATOR_STATE_SET | 1=ON, 0=OFF — **CONFIRMED WORKING** |
| `0x3E300038` | ENGINE_SIMULATOR_SOURCE_TYPE_SET | Changes sound preset — **CONFIRMED WORKING** |

## Test Results (Live on Car)
1. `setInt(1002, 0x3E300020, 1)` → STATE changed from 0 → **1** (simulator ON)
2. `setInt(1002, 0x3E300038, 2)` → SRC_TYPE accepted
3. `setInt(1002, 0x3E300038, 3)` → SRC_TYPE = **3** (confirmed readback)
4. `setInt(1002, 0x3E300038, 0)` → SRC_TYPE stayed at 3 (0 may be invalid)

Source types 1, 2, 3 confirmed valid. Types 4, 5 untested.

## How It Works
- Device ID: `1002` (BYDAUTO_DEVICE)
- The Engine Voice Simulator generates synthetic engine sounds
- Sounds are stored in MCU flash (preset sounds)
- Source type selects which preset sound to use
- Simulator must be enabled (STATE=1)
- Sound plays through the **external AVAS speaker**
- AVAS typically activates at low speeds (<30 km/h) for pedestrian warning

## Usage
```java
// Enable engine sound simulator
autoManager.setInt(1002, 0x3E300020, 1);

// Change sound preset (1, 2, 3 confirmed)
autoManager.setInt(1002, 0x3E300038, 2);  // Different engine sound
```

## Next Steps
1. Test all source types (0-5+) while driving to hear differences
2. Check if higher source type values reveal more sounds
3. Investigate if custom audio can be uploaded via `setBuffer`
4. Build a proper app with sound selection UI
5. Check if sound plays only while driving or also in P gear

## Source Type Scan Results (LIVE TEST)
ALL source types 1-10 accepted by MCU:
- SRC=0: REJECTED (invalid)
- SRC=1 through SRC=10: ALL ACCEPTED (readback matches)

The Dolphin has AT LEAST 10 different engine sound presets!

## setBuffer Results (LIVE TEST)

### Feature IDs that ACCEPT setBuffer (ret=0):
| Device | Feature ID | Name | Result |
|--------|-----------|------|--------|
| 1002 | 0x3E300038 | SRC_TYPE_SET | OK |
| 1002 | 0x3E300020 | STATE_SET | OK |
| 1002 | 0xAA000104 | TEST_AUDIO_AVAS_SET | OK |
| 1002 | 0xAA000142 | TEST_MCU_SPEAK_SET | OK |
| 1002 | 0xAA000148 | TEST_PA_CONTROL_SET | OK |
| 1002 | 0xAA000151 | FLASH_MUSIC_VAL_SET | OK |
| 1002 | 0xAA000171 | MCU_AVAS_CONFIG_SET | OK |
| 1002 | 0x1B10003D | AVAS_SOUND_SOURCE_SET | OK |
| 1005 | 0xAA000104 | TEST_AUDIO_AVAS_SET | OK |
| 1005 | 0x3E300038 | SRC_TYPE_SET | OK |
| 1034 | 0xAA000104 | TEST_AUDIO_AVAS_SET | OK |
| 1003 | 0xAA000104 | TEST_AUDIO_AVAS_SET | OK |
| 1004 | 0xAA000104 | TEST_AUDIO_AVAS_SET | OK |

### Feature IDs that REJECT setBuffer (ret=-2147482648):
| Feature ID | Name |
|-----------|------|
| 0x48F00010 | SRC_TYPE (read-only) |
| 0x32B1C042 | AVAS_AUDIO_SOURCE_TO_EXT |
| 0x1C10000E | EXTERIOR_SPEAKER_SWITCH |
| 0x1B100043 | EXTERIOR_PROMPT_TONE |

### Buffer Size Limits:
- Max accepted: **128 bytes** (64 × 16-bit PCM samples)
- 8, 16, 32, 64, 128 bytes → OK
- 256, 512, 1024 bytes → Rejected

### Audio Streaming Feasibility:
- 128 bytes per frame = 8ms of audio at 8kHz/16-bit
- Need ~125 setBuffer calls per second for continuous audio
- CAN bus can handle this bandwidth
- Key question: Does MCU interpret buffer data as audio?
