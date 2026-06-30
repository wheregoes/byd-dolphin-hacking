# Decompiled APKs - Install & Trigger Vectors (2026-06-10)

Pulled + analyzed with aapt (manifests), dexdump (code), strings, unzip.

Key APKs (focus: anything that can turn a browser blob-dropped APK on /sdcard into an installed app without host ADB or pre-installed helpers):

- aftermarket: com.byd.aftermarketinstalltool (system)
- oversea: com.byd.overseaappstore (system)
- devtools: com.byd.byddevelopmenttools (priv-app)
- browser: com.byd.browser (user, the Chromium 113 we attack from)
- Ace (com.ace.ex.file.manager): **not present on device in this session** (pm path empty, old /data/app/... hash gone). From prior dumpsys it had strong BROWSABLE GLOB `.*\.apk` handlers. Not a stock component — requires bootstrap USB install.

## AftermarketInstallTool (the "Third Party Apps 55" handler)

**Manifest (aapt xmltree + badging):**
- MainActivity: exported=true, only MAIN action. No VIEW, no data scheme, no mime for apk.
- UsbBroadcastReceiver: exported=true, listens android.intent.action.MEDIA_MOUNTED with scheme="file".
- Uses INSTALL_PACKAGES.
- Other: androidx.startup provider (not exported), ProfileInstallReceiver (DUMP perm).

**Code / strings (dexdump + strings on classes.dex/classes2.dex):**
- Beans: ScanInfoBean, InstallFileBean, MainViewModel.
- Uses modern android.content.pm.PackageInstaller (SessionParams, SessionCallback) — not raw `pm install` or simple startActivity.
- MainActivity$onCreate$1, CommonUtils$Companion, UsbBroadcastReceiver.
- Strings contain references to "Third Party", "install", "apk", "path", "file", "Scan", "country", profile installer artifacts.
- No obvious "BYD6125" or hardcoded password in the short greps (logic is dynamic from sys.byd.countrycode + StrategyManager as documented earlier).
- No direct "getIntent().getData()" or extra "path" handling visible in the keyword hits (the dexdump was noisy with 3rd-party "verify" methods from okhttp).

**Exploitation implications:**
- Designed purely around USB mount event → scan specific folder name per countrycode → (password or cloud verify) → PackageInstaller session.
- MainActivity is UI shell for that flow. Launching it via intent://component=com.byd.aftermarketinstalltool/.MainActivity from browser may pop the USB install UI, but without a real mount or pre-placed files in the magic folder it probably just shows empty or waits for USB.
- To weaponize from web: 
  1. Blob-drop the APK into /sdcard/Download/ or try to name it so it looks like the watched folder.
  2. Fire intent to the receiver or MainActivity with crafted extras that mimic ScanInfoBean (unlikely to work without reverse of the ViewModel).
  3. Or send a fake MEDIA_MOUNTED broadcast (browser can't; would need native code or system app).
- Verdict: not a great direct vector from browser. Good post-bootstrap (a sideloaded app can perhaps drive it or bind if there are services). Confirms the USB method is the "official" aftermarket path and is intentionally not easily scriptable from web.

## OverseaAppStore

**Manifest:**
- MainActivity: exported, MAIN+LAUNCHER + VIEW + DEFAULT + custom scheme (resource reference, historically `voice_to_overseaappstore://voice`).
- FileProvider: `com.byd.overseaappstore.FileProvider`, grantUriPermissions=true, not exported, with FILE_PROVIDER_PATHS meta.
- RemoteOverseaAppStoreService: exported=true, directBootAware, action `com.byd.overseaappstoripc.action.RemoteOverseaAppStoreService` (AIDL IPC).
- SoftwareRemoveBroadcastReceiver: exported, PACKAGE_FULLY_REMOVED (package: scheme).
- Permissions: INSTALL_PACKAGES + REQUEST_INSTALL_PACKAGES.

**Strings / other:**
- Custom scheme confirmed in prior work as voice-triggered.
- FileProvider paths likely include app-private or external (strings hit "Download", "sdcard", "external", "apk" in context of resources).

**Exploitation implications (from decomp + prior IPC analysis):**
- The exported service is the interesting part: no permission guard. A sideloaded app (post-bootstrap) can bind and call the AIDL (onSendVoiceSoftware etc.) to drive app store navigation.
- For pure browser: can try intent:// with the custom scheme + data pointing at an "app" or our dropped APK. It will likely just open the store UI or a detail page, not perform a raw sideload of an arbitrary APK (the service is for voice nav + cloud whitelist verify in some regions).
- FileProvider + grantUriPermissions: if we can obtain a content:// URI from this provider that points to our blob-dropped APK (or if it exposes broad paths), we could feed a content: URI to PackageInstaller (preferred over file:). Hard to obtain the URI purely from the attacked browser tab without additional primitives.
- Verdict: useful for post-sideload persistence / features, not a primary one-shot install vector from the browser page. The cloud verify endpoint (apr-*.byd.auto) is a separate attack surface.

## BydDevelopmentTools

**Manifest (many activities, most exported=false):**
- Several with custom BYD actions + DEFAULT (not BROWSABLE):
  - JumpActivity ← android.byd.intent.action.VERIFICATION
  - ScanCodeActivity ← android.byd.intent.action.SCAN_CODE_VERIFICATION
  - VersionMsgActivity ← android.byd.intent.action.VERSION_MSG
  - DevelopmentSettingsActivity ← android.byd.intent.action.DEVELOPMENT_SETTINGS
  - ADBSettingsActivity ← android.byd.intent.action.ADB_SETTINGS
  - VerificationActivity ← android.byd.intent.action.INPUT_CODE_VERIFICATION
- RepairModeActivity, RollbenchModeActivity, MappingActivity, ObdDataActivity etc. — mostly not exported or excludeFromRecents.
- Services: RepairModeService, RollbenchModeService (some permissioned).

**Strings:** confirmed the action names above.

**Exploitation implications:**
- These are the engineering / test menus (ADB enable, development settings, verification flows, scan code for factory, repair/rollbench modes).
- Custom actions mean they are reachable via explicit intent or if the browser/system allows firing `action=...` + component.
- From browser `intent:` URLs: possible to try `intent:#Intent;action=android.byd.intent.action.ADB_SETTINGS;component=com.byd.byddevelopmenttools/.ADBSettingsActivity;end` etc.
- Most flows go through VerificationActivity or INPUT_CODE_VERIFICATION — likely require entering a code derived from IMEI / fixed secret / hash (the "Electro password generator" style we already use for TestTools).
- If any screen can be reached without the code (or the code is weak / default), we could pop the "enable wireless adb" UI from the page. User could then enable it, after which a cooperating host (or the page via future IWA/WS) could do pm install. But for truly locked (no IMEI access) this may just show a locked screen.
- JumpActivity / ScanCode may be factory entry points.
- Verdict: interesting for "pop debug UI" side effects. Not a direct APK installer. Could be part of a multi-step social-engineering or "enable adb then reload page" flow. Worth testing the intent URLs from the gesture page.

## com.byd.browser (the attack surface itself)

**Manifest (key parts):**
- Heavy use of com.google.android.apps.chrome.IntentDispatcher alias (exported) + many VIEW filters for http/https/about/file/content/googlechrome + specific mimes (text/html, application/vnd.android.package-archive not directly, but file/content for *).
- DownloadFileProvider, ChromeFileProvider (internal).
- Lots of media launcher activities for file/content audio/video/image.
- WebAPK, CustomTabs, etc. support.

**Code/strings (dexdump + strings):**
- ExternalNavigationHandler.java (kw1 class): adds BROWSABLE category, does ResolveInfo lookup, logs "BrowserServices.BrowsableIntentCheck", "Package does not handle Browsable Intents for the origin.", shouldOverrideUrlLoading, UrlHandler.
- DownloadController references + the resource string "forbid_downloading".
- FileProvider classes (own + androidx).
- Confirms the two layers we already knew:
  1. Java DownloadController.onDownloadStarted() → toast with forbid_downloading (the "native code excluded" patch the user described).
  2. Standard Chromium ExternalNavigationHandler that enforces BROWSABLE for dangerous dispatches (PackageInstaller lacks it → blocked or only resolver in some cases).

**Implications:**
- The download block is a single @CalledByNative hook — the blob path (renderer fetch + URL.createObjectURL + a.download) was previously believed to bypass it, but **testing on firmware 13.1.32.2507250.1 confirmed the blob path is also silently blocked**. No file lands. The fetch succeeds but the blob-to-disk write is intercepted by the "Download proibido" policy.
- Intent dispatch to PackageInstaller is intentionally hard (BROWSABLE requirement + origin checks). This is why Ace (when present) or content: URIs from MediaStore were interesting — they sometimes route differently.
- No obvious addJavascriptInterface or BYD JS bridge exposed to every page.

## Summary for the Locked Browser Chain

Decomp confirms there is **no obvious exported, loosely-filtered activity or service** on stock that a browser page can hit with a crafted intent:// (or googlechrome:// or file:// navigation) that will directly consume a /sdcard/Download/xxx.apk and install it.

- Aftermarket is mount-driven + session-based, UI-only entrypoint.
- Oversea has nice exported service + FileProvider + scheme, but the functionality is "open store page / voice nav", not raw sideload.
- Devtools has juicy custom actions for ADB/Dev settings, protected by verification flows.
- Stock file handlers (com.byd.filemanager) are weak or not exposed.
- Ace (strong .apk BROWSABLE handler) is not stock.

**Therefore the two realistic paths for the exact UX remain:**

1. **Gesture page we shipped** (locked-clickstart.html): user tap on START provides the activation for blob write + best-effort intent. If it pops the resolver or PackageInstaller dialog, user taps Install. Works for users who can host/serve the page+APK on the car's WiFi.

2. **V8 RCE path**: only way to get reliable native write + `pm install` / direct PackageManager call or am start from inside the renderer/escaped context, without relying on the sanitized intent path or gesture for the download step.

**"Anything else better" suggestions from decomp:**
- Test explicit component + custom action intents from the gesture page (ADBSettingsActivity etc.) — if any pop useful screens without code, document the exact intent URL.
- After a successful blob, try several content://media/external/file/{recent high IDs} in the intent variants (MediaStore tracks the drops).
- Post-bootstrap: a small helper APK can bind the oversea service or drive Aftermarket more directly.
- Look at the actual FILE_PROVIDER_PATHS xml (extract from oversea/browser resources.arsc or decompile further with jadx on host) to see if broad external paths are granted — could give us a content: URI we control.
- Continue grinding the V8 hunter (p1 reliability) + improve the shellcode to also handle the case where the blob write succeeded but install intent failed (sc can do the am start or PackageInstaller session itself).

All pulled APKs + decomp artifacts are in /tmp/decomp/* on the analysis machine. Full dexdump outputs are large (noisy with libs); the targeted greps + manifests + strings above are the actionable parts.

Update the locked analysis with these findings. Commit everything.