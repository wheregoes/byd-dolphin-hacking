# Sideloading Apps on BYD Head Units

How to install third-party apps on BYD vehicles with DiLink 3 (global version). No root required.

Two methods available: USB drive (easiest) or ADB over WiFi (more flexible).

## Compatibility

Works on all global-version BYD vehicles running DiLink 3:
- Dolphin, Seal, Atto 3, Seal U, King, Shark, etc.
- Android 10 (API 29), ARM64 architecture

## Method 1: USB Drive (Easiest)

No computer needed. Works with any USB drive formatted as FAT32 or exFAT.

### Steps

1. On your USB drive, create a folder named exactly: `Third Party Apps 55`
2. Copy the APK file(s) into that folder
3. Plug the USB drive into the car's USB port
4. Wait a few seconds — a password prompt will appear
5. Enter the password: `BYD6125F`
6. A file browser will appear showing the APKs — tap to install

### Notes

- The folder name must be exactly `Third Party Apps 55` (with spaces)
- The password `BYD6125F` is the same for all DiLink 3 vehicles
- You can put multiple APKs in the folder and install them one by one

## Method 2: ADB over WiFi

More powerful — allows installing, uninstalling, debugging, and running scripts on the head unit. Requires enabling USB debugging first.

### Step 1: Enable USB Debugging

The debug menu is password-protected. The password is derived from your head unit's IMEI.

1. Find your IMEI: go to **Settings > System > About** on the head unit and note the IMEI number
2. Go to the Electro password generator at `electro.app.br/usb` and enter the **last 6 digits** of your IMEI to generate the password
3. Open the USB settings screen on the head unit (accessible via the Electro app's "Open USB Settings" button, or through the hidden engineering menu)
4. Enter the generated password in the password field (labeled in Chinese) and tap the gray button
5. Tap **TestTools**
6. Enable **Wireless adb debug switch** and **Debug mode when USB is connected**

### Step 2: Connect via ADB

Install ADB on your computer:
```bash
# Linux
sudo apt install adb

# macOS
brew install android-platform-tools

# Windows: download from developer.android.com/tools/releases/platform-tools
```

Connect your computer to the car's WiFi network, then:

```bash
adb connect 192.168.10.10:5555
```

First connection requires approval on the car's screen — tap "Allow" on the RSA key fingerprint dialog.

Verify:
```bash
adb devices
```

### Step 3: Install APKs

```bash
# Install
adb install /path/to/app.apk

# Update (keep data)
adb install -r /path/to/app.apk
```

### Uninstall Apps

```bash
# User-installed apps
adb uninstall com.example.app

# System apps (disable only — cannot fully uninstall)
adb shell pm disable-user --user 0 com.example.systemapp

# Re-enable a disabled system app
adb shell pm enable com.example.systemapp

# Restart launcher to update app drawer
adb shell am force-stop com.android.launcher3
```

## Useful ADB Commands

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
adb shell screencap /sdcard/screenshot.png && adb pull /sdcard/screenshot.png ./

# Open an app
adb shell am start -n com.example.app/.MainActivity

# Force stop an app
adb shell am force-stop com.example.app

# Query car status via content provider
adb shell "content query --uri content://com.byd.carStatusProvider/car_status"
```

## Recommended Apps

| App | Purpose | Source |
|-----|---------|--------|
| Electro | Vehicle telemetry, cameras, trip history | electro.app.br |
| Aurora Store | Alternative app store (no Google account) | auroraoss.com |
| MicroG | Google Play Services replacement | microg.org |
| PackageInstaller | On-device APK installer UI | Various |

## APK Compatibility Notes

- Must be ARM64 (aarch64) — x86/ARM32 APKs won't work
- Target SDK should be <=33 for best compatibility
- minSdk must be <=29 (Android 10)
- System partition is read-only — apps install to /data only
- Some apps may not render correctly on the widescreen display (1920x720)
