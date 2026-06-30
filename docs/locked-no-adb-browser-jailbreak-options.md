# Locked BYD (no ADB) Browser-Only Jailbreak Chain Analysis

> ⚠️ **CORRECTION (Jun 2026):** The blob download bypass described below as "the golden ticket" **does NOT work on firmware 13.1.32.2507250.1**. Exhaustive testing confirmed BYD's "Download proibido" policy silently blocks ALL browser downloads (blob, server URL, with/without gesture). No file lands. `navigator.share` is `undefined`. Browser decompilation confirmed no JS interfaces. No browser-only install path exists on the current firmware. The analysis below is retained for historical reference. See `tools/browser-exploit/test-log.md` for the full diagnosis.

**Goal:** User opens attacker URL in stock `com.byd.browser` (Chromium 113) → clicks Start → APK sideloads and installs. Works on units where ADB/TestTools/debug menu is inaccessible (IMEI password not known, wireless adb never enabled).

**Current date context:** Analysis performed live on 192.168.10.10 (DiLink 3.0, Android 10, build 2025-07, country 55 BR, browser 113.1.6.37).

## 1. Confirmed Primitives (Browser Sandbox)

- **Blob download bypass (DOES NOT WORK on current firmware):** `fetch(url_to_apk) → blob() → URL.createObjectURL → <a download=foo.apk>.click()` was previously believed to bypass the BYD DownloadController. **Testing on firmware 13.1.32.2507250.1 confirmed it is silently blocked** — no file lands, no error, no popup. The "Download proibido" policy blocks blob downloads, server URL downloads, and `navigator.share` equally. See test-log for details.
  - Normal `<a href=... download>` and `Page.navigate` hit `onDownloadStarted()` → toast "Download proibido..." (or localized) and cancel (receivedBytes=0).
  - Earlier claims of blob bypass working (electro_.apk, testpm, 52MB binaries) could not be reproduced and may have been from an earlier firmware version or misattributed to ADB-assisted flows.

- Browser has `REQUEST_INSTALL_PACKAGES` (and READ/WRITE_EXTERNAL_STORAGE with installer exemptions).
- `install_non_market_apps` appears enabled by default for aftermarket flows.
- PackageInstaller (`com.android.packageinstaller/.InstallStart`) accepts:
  - `content:` + MIME `application/vnd.android.package-archive`
  - Also `file:` in practice via `am start -a VIEW -t ... -d file:///sdcard/Download/xxx.apk` (resolver chooser appears: PackageInstaller primary, plus GPack agent / microG / Ace if present).
- SELinux enforcing, browser main proc `u:r:untrusted_app:s0:c65,...`, renderers `isolated_app`. No easy root from browser.

- No obvious `@JavascriptInterface` bridges in the BYD Chrome (strings + prior recon negative for vehicle/byd/android/ etc. custom objects beyond standard chrome).

## 2. The Core Blocker for Stock Locked Units

Blob gets the bytes on disk silently. **Triggering the actual install UI (or silent pm) from *within the browser page context* without host ADB `am`/`pm` is the unsolved piece.**

Chromium's `ExternalNavigationHandler` (kw1.java in decompile) sanitizes `intent:` URLs and most navigations. It requires the target activity to declare `BROWSABLE` category + appropriate data filter for the dispatch to leave the browser and reach PackageInstaller.

- `com.android.packageinstaller/.InstallStart` filter: `content:` MIME + `package:` scheme + `INSTALL_PACKAGE`/`VIEW`/`CONFIRM_INSTALL`. **No BROWSABLE**.
- Result from tests: many `intent:#Intent;action=VIEW;type=...;data=file:///sdcard/...apk;end` attempts (see autopm.html, install-vectors.html, chain-test) either do nothing or get blocked before resolver.
- `file:///sdcard/Download/xxx.apk` direct navigation: browser re-enters download path → blocked by the same hook.
- Content URIs from MediaStore (`content://media/external/file/{rowid}` after the blob is indexed): work great *when launched via host adb am*, but hard for the page to discover the exact rowid and hand a grantable URI to the resolver.

Ace file manager (`com.ace.ex.file.manager`, user-sideloaded in this workspace) **does** register GLOB `.*\.apk` + BROWSABLE for `file:`, `content:`, `app:` schemes. If present, it can be a proxy opener (user taps Ace → it offers PackageInstaller). On pure stock (no prior USB), `com.byd.filemanager` exists but has no useful launcher/exported APK-handling activities visible.

AftermarketInstallTool (`com.byd.aftermarketinstalltool`, system, has `INSTALL_PACKAGES` + `MANAGE_EXTERNAL_STORAGE`):
- Listens `MEDIA_MOUNTED` (UsbBroadcastReceiver) → scans "Third Party Apps {country}" (55 → BYD6125F passwordless for BR).
- MainActivity exists but strings show mostly Compose/ListItem, no obvious extra VIEW/APK data filters in quick grep. It is the USB path, not easily web-triggerable today.
- Currently often disabled (enabled=0 in package state on some units).

Other potential proxies: gpack.agent AppInstallerActivity (seen in resolver choosers), com.byd.overseaappstore (has INSTALL_PACKAGES + exported service but only for voice nav, no BROWSABLE install), various dev tools (byddevelopmenttools has exported ADBSettings etc but protected flows).

## 3. Exploitation Chain Options (for the exact UX: URL click → sideload)

Ranked by feasibility for **true locked (ADB never enabled) stock units**. "First bootstrap" = one-time USB "Third Party Apps 55" (password BYD6125F, all APKs allowed on BR) to plant a helper.

### Option A: V8 RCE + shellcode (highest potential for pure one-shot magic, actively researched)
- Use the existing `v8exploit.js` (CVE-2023-3079 TheHole + OOB + addr_of/fakeobj + arb RW in sb + WASM escape + real_ep Code overwrite for RX page).
- Current status (from NEXT-TEST + jailbreak-report + session-jun3): p1 hole creation is the gate (very rare on this arm64 113 build even with GC pressure, 50-200 attempts per load, auto-reload + hunter). When p1/p2/p3 hit: sb shift=24 detected, arb works, p4 WASM + sb escape reaches real code exec (X page write confirmed in markers, post-real_ep beacons sometimes missing).
- Shellcode (shellcode.py): clone + child does mkdir/markers + wget/curl the payload (from same origin or hardcoded) → `pm install -r -t /path`.
- Why it fits the UX: the page itself (served over HTTP, no special setup) runs the hunter loop in background (setInterval or dedicated worker). On first good p1 during the visit, sc executes **with no further user action and no host ADB**. Server is pure static file host (or minimal beacon collector).
- Blockers to solve for "users will click start":
  - Make p1 deterministic or high-prob in <30-60s open tab: more hole variants (strict args, large_arr, different maps grooming), heavier/continuous GC in web worker or iframes (if allowed), fresh `the` object each wave, image beacons for "still trying" + auto page reload on sentinel fail.
  - Ensure the sc context (renderer or escaped) can actually reach `pm` or PackageManager binder. Browser UID has REQUEST_INSTALL_PACKAGES; renderer is more restricted (isolated) but sb escape + real_ep is meant to give near-native.
  - Fallback inside sc: if pm denied, write marker + also drop a second "installer trigger" file; or use the already-dropped bytes if page can share the ArrayBuffer into the arb write.
- Evidence of partial success: real_ep writes, T1=66 etc reports, mkdir + printf markers in prior runs.
- Risk: flakiness makes bad UX ("keeps the tab open 5min?"). But retry + "jailbreak running, do not close" banner is acceptable for enthusiasts.
- Artifacts: `tools/browser-exploit/v8exploit.js`, `shellcode.py`, `jailbreak_server.py` (can be stripped of ADB parts), `session-jun3/`.

**Verdict:** The path that actually delivers "visit URL on locked car → background grind → installed" without any host presence. Grind here.

### Option B: Blob + Client-side intent dispatch to resolver / proxy (pure web, no RCE)
- Drop via blob (works).
- Immediately or on button: `location.href = 'intent:#Intent;action=android.intent.action.VIEW;type=application/vnd.android.package-archive;data=file:///sdcard/Download/payload.apk;end';` (see autopm.html).
- Or try content:// variants once MediaStore indexes the drop (hard to know the exact ID from JS; page can try a few recent guesses or use `content://media/external/file` + query if provider allows, unlikely).
- Or target Ace's BROWSABLE activity if we can craft component or the GLOB filter catches a navigation.
- Or launch Aftermarket MainActivity with data or extras pointing at the file (needs manifest inspection; current strings poor).
- Additional vectors already coded in install-vectors.html: many intent schemes, android-app://, byd:// probes, GET_CONTENT, DocumentsUI, etc.
- If any of these pops the **system resolver chooser** ("Open with PackageInstaller"), user taps once → install dialog → done. This is "click start → install" UX.
- Status: mixed in prior sessions (some Flow A claims of success, detailed table says "Fails - no BROWSABLE"). The `am` from host always works; page dispatch is the variable.
- Improvement ideas:
  - Drop the APK, also drop a small `launcher.html` to same dir. Navigate browser to `file:///sdcard/Download/launcher.html` (file origin sometimes has looser external nav rules in old Chrome). The launcher.html then does the intent link or auto-triggers.
  - After drop, use `history.pushState` + hash or a second navigation that forces re-resolution.
  - Disguise payload name/extension that hits a BROWSABLE path in Ace or another app (e.g. .zip → AceZipActivity which may offer "install contained APK").
- If Ace/GPack not present: user sees the file in /Download but may have no stock UI to "open as APK". Instruct "search files for the name" or voice, but poor UX.

**Verdict:** Worth systematic A/B test of every vector in install-vectors.html + autopm variants on a clean stock tab (no CDP). If any variant reliably shows resolver after blob, we have a winner with zero RCE. Low probability given Chromium sanitizers, but the car is old Android + custom browser, worth the grind.

### Option C: Blob + "helper app" first bootstrap (practical today)
- One-time: USB `Third Party Apps 55` → install a capable file manager (Ace ex or equivalent that registers APK + has good UI) + optionally a tiny "jailbreak helper" app that:
  - Listens for a custom scheme or local file drop notification.
  - Or just makes /Download browsable and "open APK" one-tap.
  - Or the helper itself has `INSTALL_PACKAGES` and binds/uses PackageInstaller API directly from a notification or shortcut.
- Then the web page: blob drop the real payload (named predictably), then either auto-intent to the helper's activity with the file path, or `navigator.share` (blocked for binary but text disguise + rename works per docs), or just big UI: "File dropped as X.apk. Tap here to open in File Manager → install".
- After first helper, all future payloads (updates, other apps) are pure web, one or two taps.
- Electro (already present on this car) and similar community apps can serve as the "persistent beachhead".

**Verdict:** This is how most real users will experience it. Document the minimal "bootstrap USB payload" (file manager + perhaps a one-tap "run browser jailbreak helper" shortcut). Matches the sideloading-guide reality.

### Option D: AftermarketInstallTool web activation (speculative but high value if works)
- Drop APK to a path the scanner would like (or /sdcard/Download/ and hope).
- From page: craft intent to `com.byd.aftermarketinstalltool/.MainActivity` (MAIN exported) with extras or data= the APK URI. Or implicit broadcast that its receiver likes.
- If the activity does a scan or accepts `ScanInfoBean` / InstallFileBean with local path, and since it runs as system with the perms, it could drive the passwordless (BR) install flow.
- Also interesting: the tool can be the "installer UI" presented to user instead of raw PackageInstaller.
- Needs: full manifest decompile of Aftermarket (already pulled /tmp/aftermarket.apk), look for all intent filters on MainActivity + any exported services/receivers that take file paths. Then test launches from `am` first, then from page intent://.

### Option E: CDP-from-page (not for locked users)
- Existing `serve_with_cdp_proxy.py` + `cdp-exploit.html`: page talks WS to proxy (origin stripped), proxy talks to chrome_devtools_remote (9222 via adb reverse).
- Gives `Target.createTarget`, `Runtime.evaluate` in any tab, `Browser.setDownloadBehavior`, `Browser.close` (restarts Chrome, flags apply), even `chrome://flags` manipulation from the page.
- Then from CDP you can `am`? No — still host side for shell. But you can navigate tabs, set behavior, inject the blob+intent JS more reliably.
- Requires initial ADB + reverse + the proxy host reachable as "localhost" from car (adb reverse). Fine for research/persistent attacker on same WiFi, useless for one-shot locked strangers.

### Option F: Other surfaces (lower)
- PWA "install" → only creates Chrome shortcut (webapp_source=7), no WebAPK, no native code.
- OPFS (`navigator.storage.getDirectory()`) → writes inside browser private dir, no escape to /sdcard or media for other apps to see.
- IWA/Direct Sockets: requires newer Chromium flags + APIs not in 113/Android.
- Network services from browser (CarPlay 7000 root, IDD 12406, etc.): no JS API to speak raw TCP/Binder in 113. WebTransport/WebRTC not useful for adbd protocol.
- Content providers writable from renderer: none known that would let us plant an APK + get a grant URI. CarStatus etc are read-mostly vehicle data.
- Bluetooth OPP receive: page can't initiate an inbound push easily.
- Dev tools / TestTools / byddevelopmenttools exported activities: most need verification or are not meant for arbitrary install; some may allow enabling ADB (circular for locked).

## 4. Recommended Immediate Actions (ultrathink priority)

1. **V8 reliability push (primary for locked magic link):**
   - Take the current hunter + p3 bootstrap from `v8exploit.js` + session-jun3.
   - Add continuous background pressure (Web Worker doing alloc/gc/typedarray churn while main tries leaks).
   - Multiple hole patterns in parallel (different array sizes, property counts, the.hole sources).
   - When real_ep reached: make the sc also beacon success more robustly (multiple writes, different paths, sync).
   - Test "leave tab open 2min on car" success rate with the existing jailbreak_server.py (even if server has no ADB, the page can still run the JS and sc does the work).
   - Goal: >50% chance of install within 90s of opening the page, on first try, no host.

2. **Client intent grind (parallel, high ROI if one works):**
   - On a clean car session (no extra file managers), load variants of autopm.html + install-vectors.html (host the page over the car's WiFi from laptop IP, or use adb reverse + localhost URL after setting up a small server).
   - After confirmed blob drop (watch `ls /sdcard/Download/` + logcat DownloadController), observe whether *any* of the 20+ vectors causes the resolver or install dialog to appear on the car screen.
   - Especially: content://media after drop (parse recent MediaStore? or hardcode high recent IDs), component= targeting Ace (if temporarily installed for test) or gpack installer, Aftermarket MainActivity with data.
   - If a vector pops the chooser: that + blob = "visit + (auto or one click) → install dialog". Ship it. Update the "Flow A" claim with exact working intent string.

3. **Aftermarket + Ace decompile:**
   - Use `apktool d /tmp/aftermarket.apk` or `jadx` / manual on the pulled APKs.
   - Map exact intent filters + onCreate/onNewIntent handling for file paths.
   - Same for Ace's AceFileTransferActivity / main open activities.
   - Add a test page that tries `intent:#Intent;component=com.byd.aftermarketinstalltool/.MainActivity;...` with S.extra or data.

4. **Pure "dumb server" page:**
   - Create/simplify a `locked-jailbreak.html` that:
     - On load / big Start button: does the blob fetch (same-origin APK served as /payload.apk).
     - Then tries the best-known client trigger (from step 2).
     - Shows clear status + "if nothing happens after 10s, the file is at Downloads/NAME.apk — open it via your file app or USB".
     - Embeds or loads the V8 hunter in an iframe/worker as background escalation.
   - Host it with a trivial python http.server (no CDP, no ADB code in the serve path). This is what a locked-car user can actually use.

5. **Persist + share:**
   - Every successful drop, every new intent string that works, every p1 hit string, every new component discovered → commit here.
   - Update this doc + sideloading-internals.md with the exact working minimal snippet.

## 5. Why Not "Just Use USB" Forever?

USB is reliable and password is public (BYD6125F for 55). But the point of this research is the **remote/web vector for users who cannot or will not use USB** (rental cars, company fleets, users scared of "void warranty", regions with different folder/password, post-OTA lock changes, or simply convenience: "send a link").

The blob primitive already gives us the "write file from anywhere" half. Closing the "execute install" half from the same sandbox is the only remaining gate for a true browser jailbreak.

## Appendix: Quick Snippets

**Minimal blob drop (works today, any page):**
```js
(async () => {
  const r = await fetch('payload.apk'); // same origin or CORS
  const b = await r.blob();
  const u = URL.createObjectURL(b);
  const a = document.createElement('a');
  a.href = u; a.download = 'myapp.apk';
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(u);
})();
```

**Autopm-style trigger (test this family first):**
```js
setTimeout(() => {
  location.href = 'intent:#Intent;action=android.intent.action.VIEW;type=application/vnd.android.package-archive;data=file:///sdcard/Download/myapp.apk;end';
}, 800);
```

See `tools/browser-exploit/{autopm.html,install-vectors.html,jailbreak.html,v8exploit.js,shellcode.py}` for the full current harness.

---

**Next commit will include:** results of the intent vector sweep + any p1 reliability patches + the dumb locked-jailbreak.html page + decompile findings on Aftermarket/Ace.

## Live Test Result (2026-06-10, this session)

Tested the exact autopm vector that previous docs claimed as "Flow A" (auto blob + intent file://):

- Used adb reverse tcp:8088 + am start to http://127.0.0.1:8088/autopm.html (simulates opening the page; JS is pure client).
- Server log: GET /autopm.html 200, GET /payload.apk 200 (the fetch + top-level script in autopm.html executed, blob created in memory).
- 5 polls: NO /sdcard/Download/testpm.apk landed. No new package. mResumedActivity stayed com.byd.browser ChromeTabbedActivity entire time. No "Starting Intent ... package-archive" or installer activity in recent logs.
- Conclusion: the a[download].click() step after blob did **not** persist the file this run. Auto onload / script-top-level write is fragile or blocked without user gesture / CDP setDownloadBehavior / specific state.

This matches scattered notes in sideloading-internals.md about "in this run not producing" and "RCE/sc mandatory for reliable auto sideload on locked."

**For the requested UX ("user access url > click in start > install") the auto version is insufficient. A real tap is likely required to activate the download write + external navigation.**

## New Client-Side Deliverable

Created `tools/browser-exploit/locked-clickstart.html`:

- Dark monospace UI, one big "START - DROP & INSTALL APK" button.
- onclick (real user gesture): fetch(/payload.apk) → blob → createObjectURL → a.download("byd-sideload.apk").click() → short delay → location + anchor for the two intent variants (VIEW + INSTALL_PACKAGE with file:///sdcard/Download/...).
- Status log + fallback instructions (if nothing pops: look in Downloads/, or do the one-time USB "Third Party Apps 55" + file manager bootstrap).
- Host your target sideload APK as `payload.apk` next to the html (or edit the fetch). Serve with `python -m http.server` (or phone hotspot + termux, or any static host). Car browser opens the URL, user taps the button exactly as specified.

This is the closest pure-web, no-ADB, no-prior-apps match to the flow. Gesture on the button should give the activation the auto version lacked.

If even the onclick version fails to write the file or dispatch the intent (ExternalNavigationHandler still eats it), then V8 RCE (Option A) becomes the only client-side path that can do arbitrary writes + am/pm from escaped context.

## Commit

This update + the new locked-clickstart.html + the live test data committed/pushed. All per CLAUDE.md. No findings left only in chat.
