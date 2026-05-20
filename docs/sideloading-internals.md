# Sideloading Internals

Deep dive into how BYD DiLink 3.0 handles third-party app installation. Reverse-engineered from `AftermarketInstallTool.apk` (`/system/app/AftermarketInstallTool/`).

## AftermarketInstallTool

System app (Kotlin/Compose) that handles all third-party APK installation from USB drives. Package: `com.byd.aftermarketinstalltool`.

### Verification Paths

Three verification modes, selected by `scanDevice` field in `ScanInfoBean`:

| scanDevice | Constant | Method | Password Required |
|-----------|----------|--------|-------------------|
| 0 | `VERIFY_SCAN_TYPE_PHONE` | Phone scan — `initFreeApks()` | **NO — all APKs approved** |
| 1 | `VERIFY_SCAN_TYPE_VDS` | Online cloud verification | No (cloud decides) |
| 2 | `VERIFY_SCAN_TYPE_PW` | Local password (SHA1) | Yes |

### Country-Based Configuration

The folder name is `"Third Party Apps " + countryCode`. Country code comes from `sys.byd.countrycode` system property.

| Country | Code | Folder Name | Password | APK Whitelist |
|---------|------|-------------|----------|---------------|
| Brazil | 55 | `Third Party Apps 55` | `BYD6125F` | All allowed |
| Mexico | 52 | `Third Party Apps 52` | `BYD6125F` | All allowed |
| Indonesia | 62 | `Third Party Apps 62` | `BYD6125F` | All allowed |
| Thailand | 66 | `Third Party Apps 66` | `BYD6125F` | All allowed |
| Saudi Arabia | 966 | `Third Party Apps 966` | `BYD6125F` | All allowed |
| UAE | 971 | `Third Party Apps 971` | `BYD6125F` | All allowed |
| Uzbekistan | 998 | `Third Party Apps 998` | `BYD6125F` | All allowed |
| Kazakhstan | 997 | `Third Party Apps 997` | `BYD6125F` | **Restricted** — only Yandex, Telegram, WhatsApp, Zoom, etc. |
| India | 91 | `Third Party Apps 91` | `130820` | **Restricted** — only `com.mappls.auto.bydznav23` |
| Europe, AU, JP, etc. | varies | `Application Installation {code}` | None (online) | Cloud-verified |

Password is stored as SHA1 hash for comparison: `SHA1("BYD6125F")`.

### Strategy Configuration

The `StrategyManager` service provides an `ApkInstallConfig` strategy value that overrides default behavior:

| Config | Meaning |
|--------|---------|
| `0` (default) | Use country-based rules above |
| `1` | QR code disabled — local mode with APK whitelist set to `{"disable"}` (blocks all installs) |
| `2` | QR code enabled — allows QR-based verification |

Format: `{config}_{password}` (e.g., `0_BYD6125F`). Parsed by splitting on `_`.

### Online Verification API

For countries without local passwords (Europe, Australia, Japan, etc.), APKs are verified against BYD's cloud.

**Regional data centers:**

| Region | Endpoint |
|--------|----------|
| Europe | `apr-eu.byd.auto` |
| Brazil | `apr-br.byd.auto` |
| Singapore/APAC | `apr-sg.byd.auto` |
| Australia | `apr-au.byd.auto` |
| Japan | `apr-jp.byd.auto` |
| Mexico/LATAM | `apr-mx.byd.auto` |
| Middle East/Africa | `apr-no.byd.auto` |
| Uzbekistan | `apr-uz.byd.auto` |

**Verification endpoint:**

```
POST https://{datacenter}/authentication/whitelist-verify
Content-Type: application/json

{
  "countryCode": "55",
  "source": 1,
  "vid": "vehicle_identifier",
  "taskId": "unique_task_id",
  "verifyTime": 1716163200000,
  "packageList": [
    {
      "packageName": "com.example.app",
      "appVersion": "1.0.0",
      "deviceType": "IVI"
    }
  ]
}
```

**Response:**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "packageName": "com.example.app",
      "deviceType": "IVI",
      "appVersion": "1.0.0",
      "verifyResult": true,
      "verifyMessage": "approved"
    }
  ]
}
```

Error codes: 0=success, 1=parameter null, 2=parameter error, 3=authority exception (rate limited), 4=QR not scanned, 5=business exception.

**Device types:** IVI (main head unit), FSE, LEFT_RSE, RIGHT_RSE, RCS, RIES, REAR_OVERHEAD, TV.

### Kazakhstan APK Whitelist

Only these packages can be installed on Kazakhstan units:

- `ru.dublgis.dgismobile` (2GIS Maps)
- `ru.yandex.yandexnavi` (Yandex Navigator)
- `ru.yandex.music` (Yandex Music)
- `com.streema.simpleradio` (Simple Radio)
- `com.uma.musicvk` (VK Music)
- `ru.yandex.weatherplugin` (Yandex Weather)
- `com.originalapp.uzbekistanweather`
- `com.apalon.weatherlive.free`
- `com.bugunuz.mobile`
- `uz.muloqot.daryo`
- `org.telegram.messenger` (Telegram)
- `com.whatsapp` (WhatsApp)
- `us.zoom.videomeetings` (Zoom)
- `com.gpack.agent`

### USB Detection Flow

`UsbBroadcastReceiver` listens for USB mount events. When a USB drive is inserted:

1. Scans for folder matching `"Third Party Apps " + countryCode` (local) or `"Application Installation " + countryCode` (online)
2. Reads APK files from the folder
3. Depending on country configuration:
   - **Local (password):** Shows password dialog → SHA1 comparison → installs approved APKs
   - **Online (QR/cloud):** Sends APK list to BYD cloud → installs approved APKs
   - **Phone scan:** Approves all APKs immediately

## Browser Download Block

BYD's Chromium browser (`com.byd.browser`) has download functionality completely disabled at the Java level.

**Source:** `org.chromium.chrome.browser.download.DownloadController`

```java
@CalledByNative
public static void onDownloadStarted() {
    // BYD replaced actual download logic with a toast
    Toast.makeText(context, R$string.forbid_downloading, Toast.LENGTH_LONG).show();
}

@CalledByNative
public static void onDownloadUpdated(DownloadInfo downloadInfo) {
    Toast.makeText(context, "禁止->updated", Toast.LENGTH_LONG).show();
}
```

- ALL downloads blocked — not just APKs
- Block is in Java code, called from native Chromium C++ via `@CalledByNative`
- Cannot be bypassed via settings or permissions
- Toast message: "Download proibido: O download pode representar uma ameaça à segurança e estabilidade do sistema do veículo"
- Resource: `R$string.forbid_downloading` (ID: `0x7f1404e1`)

## Other Install Vectors

### File Manager (ACE Ex File Manager)

`com.ace.ex.file.manager` is **not pre-installed** — must be sideloaded first via ADB or the `Third Party Apps` USB method. Once installed, it has `REQUEST_INSTALL_PACKAGES` permission, can browse USB drives, and install APKs directly — bypassing the `Third Party Apps` folder requirement and password for subsequent installs.

### Package Installer Intent

Direct install from `/sdcard/` works:

```bash
adb shell am start -a android.intent.action.VIEW \
  -t "application/vnd.android.package-archive" \
  -d "file:///sdcard/Download/app.apk"
```

### Bluetooth OPP

`BluetoothOppLauncherActivity` is registered with multiple intent filters. The car can receive files via Bluetooth Object Push Profile. Untested for APK transfer.

### System Settings

- `install_non_market_apps = 1` (unknown sources enabled)
- No device policy restrictions on app installation
- Per-app install permission: browser and file manager both have `REQUEST_INSTALL_PACKAGES`

## Browser Exploit: fetch() + Web Share Bypass

**Status:** Confirmed working (requires HTTPS + file manager app installed)

BYD's download block is only at `DownloadController.onDownloadStarted()` — a single Java entry point called from native Chromium. The `fetch()` API operates entirely in the renderer process and never touches the download manager.

### Browser: Chromium 113

```
versionName=113.1.6.37
targetSdk=33
```

Channel 4 (stable). Modern enough that memory-corruption exploits are not viable.

### API Availability (HTTP vs HTTPS)

| API | HTTP | HTTPS (self-signed) |
|-----|------|---------------------|
| `isSecureContext` | false | **true** |
| `fetch()` | yes | yes |
| `Blob` / `createObjectURL` | yes | yes |
| `navigator.share()` | no | **yes** |
| `navigator.canShare({files})` | no | **yes (returns true for APK)** |
| `ServiceWorker` | no | **yes** |
| `caches` (Cache API) | no | **yes** |
| `showSaveFilePicker` | no | no |
| `clipboard` | no | **yes** |

### Exploit Chain

1. Host page over **HTTPS** (self-signed cert OK — user accepts warning)
2. User taps "Install" button (real user gesture required)
3. JavaScript `fetch('app.apk')` downloads APK into memory — **download manager never involved**
4. `navigator.share({files: [new File([blob], 'app.txt', {type: 'text/plain'})]})` — disguise as text file
5. Share sheet opens → user picks file manager → saves to `/sdcard/Download/`
6. Rename `app.txt` → `app.apk`
7. Open with package installer → Install

### MIME Type Restriction

`navigator.share()` with files works for `text/plain` but **blocks binary MIME types** (`application/vnd.android.package-archive`, `application/octet-stream`, `application/zip`). The APK binary must be disguised as text.

`navigator.canShare()` returns `true` for all types but the actual share call gets `NotAllowedError: Permission denied` for non-text types.

### Stock Unit Limitation

On a stock BYD (no third-party apps), the only share target for files is **Bluetooth OPP** — which sends files to another device, not save locally. The native `com.byd.filemanager` does not register for `ACTION_SEND` intents.

**Practical strategy:**
- First install: USB `Third Party Apps 55` + password `BYD6125F`
- After file manager installed: browser exploit becomes viable for future installs
- App self-updates: built-in HTTP download + PackageInstaller API (no browser needed)

### Other Browser Findings

- `intent://` URLs are parsed by `ExternalNavigationHandler` but require user gesture
- `blob:` URLs can be navigated to (URL bar shows blob URL) but don't trigger package installer
- `<a download>` with blob URLs silently blocked (goes through `DownloadController`)
- Chrome DevTools remote debugging available via `localabstract:chrome_devtools_remote`
- Browser has BYD car APIs baked in (`com.byd.car.*` packages in browser APK)

### Test Harness

The `tools/browser-exploit/` directory contains the test page used for this research:
- `index.html` — test page with 10 bypass vectors
- `sw.js` — service worker for cache API testing
- `serve_https.py` — HTTPS server with self-signed cert

## Tested On

- BYD Dolphin 2025
- DiLink 3.0, Android 10 (API 29)
- Firmware 13.1.32.2507250.1
- Country code: 55 (Brazil)
