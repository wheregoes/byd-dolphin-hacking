# BYD USB Sideloader Prep (for normal humans)

**Goal**: the absolute easiest way to get apps on a stock DiLink 3 BYD (Dolphin etc.) when the car does **not** have ADB/WiFi debug turned on.

This uses the built-in `AftermarketInstallTool` (the official "Third Party Apps" flow). No exploit, no root, no browser required for the first app.

## The current easiest chain for end users (no ADB car)

1. **One-time bootstrap (USB magic folder)**  
   Run the prep tool (or do it by hand).  
   Plug the USB into the car → enter the short known password → install whatever you bundled (start with a good file manager).

2. **All future installs become easy**  
   Once you have any app with install rights (or just the file manager), you can:
   - Drop APKs from the car browser using a blob page (lands in Downloads automatically).
   - Copy APKs to any USB and open with the file manager.
   - No more special folder name or password.

This is already "USB with files > plug > (one password you only do once or remember) > apps".

True "any file on root of USB → auto installs with zero prompt" is not known yet. We would need a bug in AftermarketInstallTool (phone-scan mode 0 without pw, or path that forces free approve) or in OTGUpdate. Those are researched but not public bypasses today.

## Quick start (recommended)

```bash
# 1. Plug a FAT32/exFAT USB stick into your computer
# 2. (optional) connect a car via adb so it can read the real country code
adb connect 192.168.10.10:5555

# 3. Prep with your APKs (repeat --apk as needed). Defaults to country 55.
python3 tools/usb-prep/prep.py --apk myfilemanager.apk --apk cooltool.apk

# Or target the mounted USB directly:
python3 tools/usb-prep/prep.py --target /media/you/MYUSB --apk ...

# With auto country from a connected car:
python3 tools/usb-prep/prep.py --auto-country --apk ...
```

The tool creates:
- `Third Party Apps 55/` (or your country) with your APKs inside
- `BYD-INSTALL-INSTRUCTIONS.txt` at USB root with the exact password and steps
- Small README inside the folder too

Eject, take to car, plug, enter pw (BYD6125F for 55 and most markets), install.

## Passwords by country (from AftermarketInstallTool reverse)

See `prep.py` — most use `BYD6125F`. India and some restricted markets are different + whitelist only certain apps.

## After first app (the real "easy mode")

Install a capable file manager via the USB method above.

Then:
- Use any of the blob-download pages in `tools/browser-exploit/` (or a hosted one).
- The page does `fetch(...apk)` → blob → hidden `<a download>` and writes straight to `/sdcard/Download/` (bypasses BYD's crippled DownloadManager).
- On the car, open your file manager → Downloads → tap the APK → choose PackageInstaller (or the microG/GPack one if present) → Install.

No magic folder, no password, works from a phone hotspot page if you want remote drops.

See `tools/browser-exploit/index.html`, `autodownload.html`, `autopm.html` for working examples. Some auto-fire on load.

## Why not pure "plug any USB and it just installs"?

- AftermarketInstallTool only looks in the country-specific `Third Party Apps XX` folder (or the online "Application Installation XX" variant).
- It shows a password dialog for most configurations (even if it then allows all APKs for BR etc.).
- OTGUpdate (the other USB handler) is for signed full firmware zips in a hardcoded old path and does heavy verification + recovery reboot. Not usable for normal user APKs.
- No autorun / magic filename at USB root that we have found that bypasses the above.

If a vuln is found (e.g. StrategyManager config file on USB that forces scanDevice=0 "phone scan" free approve, or intent that makes Aftermarket scan an arbitrary dir), we will add it here immediately.

## Browser chain reality check (PS4-like / V8)

The browser (`com.byd.browser`, Chromium 113) has its normal download path completely neutered at the Java level.

**Good news**: `fetch()` + Blob + `<a download>` completely bypasses it and can silently (or on click) write APKs (and any other files) to `/sdcard/Download/`. This works today, from HTTP or HTTPS pages, even autofire on some loads. No ADB or CDP required for the drop.

**Current limitation for "visit one page = app installed" on locked stock car**:
- The V8 RCE (CVE-2023-3079 TheHole chain in `v8exploit.js` + shellcode for `pm install`) exists and the primitives (p1-p4 + sb arb write to JIT X page) have been reached in testing.
- On this specific arm64 Chrome 113 build the initial hole leak (p1) is very rare. Sustained tests with hundreds of attempts + GC + reloads often get zero good holes in a session. When a hole appears, later beacons (real_ep, written, sc markers, pkg) frequently don't confirm.
- Therefore the "PS4-like" full zero-click RCE + native pm install path is not reliable enough for normal users yet. It is a powerful dev/hacker/research tool when you can leave a tab open and watch the report.

We are **not** giving up on the browser vector — the file drop primitive is excellent and will be part of the "subsequent installs" story. We are just being honest that for the absolute first app on a completely locked no-ADB car, the USB magic-folder method (with this prep tool) is currently the path that "just works for grandma".

When the V8 hit rate or an alternative RCE (other 113 CVEs, better hole variants, etc.) becomes consistent, the story becomes: "join the phone hotspot or visit attacker page in car browser → leave tab → app appears". Very elegant.

## Files in this dir

- `prep.py` — the tool
- This README

## Related (in the repo)

- `docs/sideloading-guide.md` — user instructions (USB + ADB)
- `docs/sideloading-internals.md` — deep reverse of Aftermarket + browser bypass details + all the verified blob + intent flows
- `tools/browser-exploit/` — all the drop pages, V8 chain, servers, test APKs, CDP stuff
- `tools/installer/installer.py` — nice GUI for when ADB *is* enabled (scans, uploads, installs)

## Contributing findings

Per the root CLAUDE.md: every new discovery about install vectors, new bypasses, Strategy config files, OTG tricks, etc. gets committed here with a dated note or updated dump.

Pull requests or issues with new "magic USB filename that triggers free install" or reliable one-tap intent from blob etc. are very welcome.

Current best user experience = this prep tool + one bootstrap file manager via the official Aftermarket path.

Everything after that can be browser drops or plain file copies.
