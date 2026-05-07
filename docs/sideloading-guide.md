# APK Sideloading Guide

## Prerequisites

1. Enable wireless ADB on the BYD head unit (Developer Settings)
2. Install ADB on your computer: `sudo apt install adb` (Linux) or via Android SDK
3. Connect your computer to the car's WiFi network

## Connect

```bash
adb connect 192.168.10.10:5555
```

First connection requires approval on the car's screen — tap "Allow" on the RSA key fingerprint dialog.

Verify connection:
```bash
adb devices
```

## Install an APK

```bash
# Fresh install
adb install /path/to/app.apk

# Update/reinstall (keep data)
adb install -r /path/to/app.apk
```

## Uninstall

### User-installed apps
```bash
adb uninstall com.example.app
```

### System apps (cannot uninstall, only disable)
```bash
# Disable the app
adb shell pm disable-user --user 0 com.example.systemapp

# Re-enable if needed
adb shell pm enable com.example.systemapp

# Restart launcher to update app drawer
adb shell am force-stop com.android.launcher3
```

## Useful Commands

```bash
# List all packages
adb shell pm list packages

# Find a specific package
adb shell pm list packages | grep spotify

# Get package path
adb shell pm path com.example.app

# Pull an APK from the car
adb pull /system/app/SomeApp/SomeApp.apk ./

# Take a screenshot
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./

# Open an app
adb shell am start -n com.example.app/.MainActivity

# Force stop an app
adb shell am force-stop com.example.app
```

## Recommended Apps

| App | Purpose | Source |
|-----|---------|--------|
| Aurora Store | Alternative app store (no Google account needed) | auroraoss.com |
| MicroG | Google Play Services replacement | microg.org |
| PackageInstaller | On-device APK installer UI | Various |

## Notes

- APKs must be ARM64 compatible (aarch64)
- Target SDK should be <=33 for best compatibility
- The car runs Android 10 (API 29), so minSdk should be <=29
- System partition is read-only — you can only install to /data
- Some apps may not render correctly on the car's widescreen display
