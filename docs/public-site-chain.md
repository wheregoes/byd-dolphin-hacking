# Public Website Deployment Analysis (jailbreakmybyd.com style)

## Current Working Chain (what just succeeded locally)

1. Host machine (your laptop) runs three things on the same WiFi as the car:
   - `python3 tools/browser-exploit/serve.py` (or equiv) → serves the special two-phase HTML at `/` on port 8080. Binds beacons at `/beacon`.
   - `python3 /tmp/beacon_watcher.py` → tails `/tmp/jb_server.log`, on `download_triggered` from 192.168.10.10 does `adb push testpm.apk /sdcard/Download/byd-sideload.bin`.
   - `/tmp/monitor_chain.sh` (background) → polls car's Downloads via adb every ~3s. On .bin: `mv` to .apk, `am broadcast` media scan, `content query` for content:// URI, `am start` VIEW (often fails "unable to resolve" because PackageInstaller activity is not BROWSABLE + ExternalNavigationHandler), then always `cp` to `/data/local/tmp/` + `pm install -r`.

2. Car (locked DiLink 3, Chromium 113, BYD browser with custom DownloadController + ExternalNavigationHandler) is on the host's hotspot or same L2.

3. User opens the host IP (or mDNS) :8080 in the car's browser (exact root, no path).

4. Page JS:
   - Sends `page_load` + prefetches /payload.apk (warm).
   - Big TAP HERE (phase 1, provides initial user gesture + shows the visible link).
   - Visible green link (phase 2). User physical finger tap on car screen → `fetch` blob, `createObjectURL`, create `<a download="byd-sideload.bin">`, `.click()`.
   - Fires `download_triggered` beacon (plus the side devtools/oversea probes for possible side effects).

5. Watcher sees the beacon line → adb push (the real file write).

6. Monitor sees the file → renames, force chooser (fails), safe pm install.

7. User sees:
   - File appear in the car's Downloads (user eyes).
   - App icon in launcher.
   - Can open the sideloaded app.

This is why "I runned again and it works" — the beacon + host assist made the file land and install even when pure blob sometimes hits the forbid_downloading hook.

## What Changes on a Public Website (jailbreakmybyd.com)

Users type `https://jailbreakmybyd.com` in the car browser.

### What still works (client side)
- The HTML/JS page can be served from anywhere on the internet.
- Car browser can load it if the car has a route (cellular data on the head unit, or the hotspot is shared through a phone that has mobile data).
- The two taps still work.
- All beacons (`/beacon?msg=page_load&...`, `phase*`, `download_triggered`, etc.) will be sent to your public origin.
- The page can still try the blob + visible `<a download>` (the gesture part).

### What breaks (the actual sideload)
- **No ADB path from your public server to the user's car.**
  - The watcher cannot do `adb -s <random-car>:5555 push`.
  - ADB (even wireless) requires the device to have previously done `adb tcpip 5555` (or USB), and the client must be on a network path that can reach the car's debug port. A random internet server has neither.
  - Locked cars usually have wireless ADB off until the user enables it (which itself often requires the dev menu or USB bootstrap).

- **No per-user file write.**
  - Your public server seeing a beacon from IP X does not mean it can write bytes into that car's `/sdcard/Download/`.
  - The only thing that wrote the APK in all successful runs was the local host doing `adb push`.

- **No per-user `pm install`.**
  - The monitor's `cp /data/local/tmp + pm install -r` is executed on the host via adb shell. Public server cannot do that.

- **Network reachability for beacons vs. control**
  - Beacons are just HTTP GETs — they can cross the internet.
  - File write + package install require local privileged access to the head unit (the adb channel we had throughout testing).

### Result for random visitors
- They load the pretty page.
- They do the two taps on the car screen.
- Your server logs the beacons (you see "someone in another city just tapped").
- Nothing happens on their car. No file appears in Downloads. No app installs.
- They get the download alert if the blob path hits the BYD DownloadController hook, or the link just does nothing visible.

Exactly the same problem we had in every early iteration before the host watcher + monitor were running locally with adb attached.

## What Would Be Required for "Works for Everyone" on a Public Site

Option A — pure client-side (the hard but correct path)
- The page (or a WASM/JS payload it loads) must itself cause the APK bytes to land on the car's storage in a location the system PackageInstaller can see.
- Then trigger the install in a way the browser's ExternalNavigationHandler + the BYD components allow (or guide the user to a file manager they already have).
- This is why the V8 RCE work (cve_2023_4863, TheHole, shellcode for write + pm) and the various download bypass tests existed in the repo.
- If you had a reliable renderer RCE + native write primitive that survives the custom BYD browser build, you could host the page + the payload bytes publicly and it would be "one tap from any car with internet".

Option B — out-of-band + user bootstrap
- The public site only gives instructions + the signed APK.
- User must first get a file manager on the car (the "Third Party Apps 55" + BYD6125F trick, or one-time USB, or Ace, etc.).
- Then the site can try to serve a direct blob that the user saves via the file manager, or use a custom scheme that an already-installed helper app handles.
- Still not zero-touch for a completely locked car.

Option C — hybrid with user-run companion (what actually worked here)
- The public site serves only the pretty HTML + beacons.
- The user must download and run a small desktop/mobile "host companion" (the serve.py + watcher + monitor) on a machine they control.
- The companion tells them the LAN IP or sets up mDNS/zeroconf.
- Car browser hits the LAN IP (or the companion can even do a quick local tunnel if the car has no easy LAN reach).
- This is exactly the setup that made "it works" repeatable.
- You can publish the page + the companion binary + clear instructions ("run this on a laptop on the same WiFi as your car, enable wireless adb once via USB if needed, then visit the LAN address from the car screen").

## Practical / Safety Notes for Public Hosting

- The current two-phase visible-link page is the best UX we have for the assisted path (the second tap gives a strong user gesture that sometimes helps the blob path, and the beacons give perfect diagnostics).
- Every successful install we saw still went through the host `pm install` path (the content URI attempts failed to resolve because of the non-BROWSABLE component + navigation handler).
- If you host the page publicly, add very prominent text:
  "This page by itself does not install anything. You must also run the host scripts on a computer that has ADB access to your car."
- The APK you serve must be one that the car's PackageInstaller will accept (the test one we used was fine for demo; real apps need proper signing or the car's allow-unknown-sources policy).
- Monitor/watcher must be per-car and exit after success (they do). A public server cannot run one global monitor.

## Summary — Will It Work for Everyone If You Just Put the Page on jailbreakmybyd.com?

No.

The page + taps + beacons will work from any car that can reach your domain.

The actual file appearing in Downloads and the app installing will only happen for cars where someone is running the watcher + monitor + has ADB to that car at the same time the beacons arrive.

That is exactly the lab/demo setup that made the last runs succeed.

To turn it into something a random owner can use without you having their car on ADB:
- Ship a user-run companion (best short-term).
- Or finish a reliable pure browser write + install primitive (the long-term real remote jailbreak).

The current artifacts (the two-phase page, the beacon protocol, the safe-install monitor logic, the visible-link UX) are reusable building blocks for either path.

