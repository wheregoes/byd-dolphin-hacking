# Sound & Theme Customization

## What CAN Be Changed (Head Unit)

### Notification Sound
```bash
# List available sounds
adb shell "ls /system/product/media/audio/notifications/"

# Change notification sound
adb shell settings put system notification_sound "content://media/internal/audio/media/46?title=Pixie%20Dust&canonical=1"
```

### Ringtone
```bash
adb shell "ls /system/product/media/audio/ringtones/"
adb shell settings put system ringtone "content://media/internal/audio/media/165?title=Flutey%20Phone&canonical=1"
```

### System Sound Toggles
```bash
# Enable/disable UI touch sounds
adb shell settings put system sound_effects_enabled 1  # or 0

# Enable/disable charging connection sound
adb shell settings put system charging_sounds_enabled 1  # or 0

# Enable/disable lock screen sounds
adb shell settings put system lockscreen_sounds_enabled 1  # or 0

# Enable/disable DTMF tones
adb shell settings put system dtmf_tone 1  # or 0
```

### Custom Notification Sound
You can push a custom sound file and set it:
```bash
# Push to device
adb push my_sound.ogg /sdcard/Notifications/

# Then set via Android media scanner or settings app
```

## What CANNOT Be Changed (Without Root / ECU Access)

| Sound | Controlled By | Reason |
|-------|--------------|--------|
| Lock/Unlock chirp | BCM (Body Control Module) | Hardcoded in BCM firmware |
| Horn | BCM | Physical relay, not software |
| Turn signal click | BCM/Instrument Cluster | Separate ECU |
| AVAS (pedestrian warning) | Dedicated AVAS ECU | Safety-critical, legally mandated |
| Seatbelt warning chime | BCM/Cluster | Safety-critical |
| Parking sensor beeps | Radar ECU | Separate system |
| Boot sound | System partition | Read-only without root |

## Theme System

### BYD Theme Store
- Package: `com.byd.automultipletheme`
- Supports theme switching via `com.android.permission.CHANGE_BYD_APP_THEME`
- Has a `ChangeReceiver` that responds to theme change broadcasts

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

You can pull and examine the current animation:
```bash
adb pull /system/media/bootanimation.zip ./
unzip bootanimation.zip -d bootanimation/
cat bootanimation/desc.txt
```
