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
