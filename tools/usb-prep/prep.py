#!/usr/bin/env python3
"""
BYD Magic USB Prep — ultra simple sideloader for normal users (no ADB on target car needed).

Prepares a FAT32 USB so that on plug-in the car (DiLink 3):
- Shows the AftermarketInstallTool password prompt (known pw per country)
- Lets user install any APKs you bundled.

Usage (easiest):
  python3 prep.py --apk myapp.apk --apk another.apk
  # or point at a mounted USB
  python3 prep.py --target /media/user/MYUSB --apk ...

It creates the exact "Third Party Apps XX" folder for the country.
Default 55 (Brazil / most tested). Use --country 52 for Mexico etc.

After prep: safely eject USB, plug into car USB port, enter pw (BYD6125F for 55), install.

Once you have ONE app installed (e.g. a file manager), future APKs can be dropped
via browser blob to /sdcard/Download and installed without magic folder or pw.

See README.md in this dir for the full user story + current best chain.
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List, Optional

# From reverse of AftermarketInstallTool + real car tests
COUNTRY_PWS = {
    55: "BYD6125F",   # Brazil (primary tested)
    52: "BYD6125F",   # Mexico
    62: "BYD6125F",   # Indonesia
    66: "BYD6125F",   # Thailand
    966: "BYD6125F",  # Saudi
    971: "BYD6125F",  # UAE
    998: "BYD6125F",  # Uzbekistan
    997: "BYD6125F",  # Kazakhstan (but whitelist restricted)
    91: "130820",     # India (restricted)
}

FOLDER_PREFIX = "Third Party Apps"

README_TEMPLATE = """BYD DiLink 3 — Third Party App Install (USB)

Prepared for country code: {country}
Folder: {folder}
Password: {pw}

HOW TO USE ON THE CAR (no computer, no ADB):
1. Plug this USB into any USB port on the car (data port, not just charge).
2. Wait 5-10 seconds. A password screen from "Third Party Apps" should appear.
3. Enter the password above exactly.
4. A list of APKs will show. Tap each one -> Install.
5. After install, you may need to restart the launcher or car for icons to appear:
   - Settings > Apps > See all > Launcher (or com.android.launcher3) > Force stop
   - Or just reboot the head unit (hold power or use dev menu).

IMPORTANT:
- Folder name must stay EXACT (including spaces): {folder}
- Works on global DiLink 3 cars (Dolphin etc). Country code from sys.byd.countrycode.
- All APKs in the folder are offered (for 55 and most markets; some countries have whitelists).
- APKs must be arm64, targetSdk <= ~33, minSdk <=29 for best results.

ONCE YOU HAVE ONE APP (bootstrap):
Install a good file manager first via this USB (e.g. FX File Explorer, Solid Explorer, or any with REQUEST_INSTALL_PACKAGES).
After that:
- You can drop future APKs via the car browser (blob download pages work — they land in Download).
- Or just copy APKs to the root of any USB and use the file manager to install directly.
- No more magic folder or password needed for subsequent apps.

This is currently the easiest path for cars without ADB/WiFi debug enabled.

Prepared by byd-dolphin-hacking tools/usb-prep.
"""

def get_country_folder(country: int) -> str:
    return f"{FOLDER_PREFIX} {country}"

def get_pw(country: int) -> str:
    return COUNTRY_PWS.get(country, "BYD6125F")

def find_adb() -> Optional[str]:
    return shutil.which("adb")

def query_car_countrycode(adb: str) -> Optional[int]:
    """If adb available and car connected, ask the real country code."""
    try:
        out = subprocess.check_output(
            [adb, "shell", "getprop", "sys.byd.countrycode"],
            text=True, timeout=5
        ).strip()
        if out.isdigit():
            return int(out)
    except Exception:
        pass
    return None

def prepare_usb(target_dir: Path, apks: List[Path], country: int, adb: Optional[str] = None) -> None:
    folder_name = get_country_folder(country)
    dest = target_dir / folder_name
    dest.mkdir(parents=True, exist_ok=True)

    copied = []
    for apk in apks:
        if not apk.exists():
            print(f"[!] Skip missing: {apk}")
            continue
        safe_name = "".join(c for c in apk.name if c.isalnum() or c in "._-")
        if not safe_name.lower().endswith(".apk"):
            safe_name += ".apk"
        shutil.copy2(apk, dest / safe_name)
        copied.append(safe_name)

    # Write per-prep README with exact pw + instructions
    pw = get_pw(country)
    readme = README_TEMPLATE.format(
        country=country,
        folder=folder_name,
        pw=pw
    )
    (target_dir / "BYD-INSTALL-INSTRUCTIONS.txt").write_text(readme, encoding="utf-8")

    # Also drop a tiny helper note in the magic folder
    (dest / "README-install.txt").write_text(
        f"Password for this folder: {pw}\n"
        "Tap the APKs to install after the prompt.\n",
        encoding="utf-8"
    )

    print(f"[+] Prepared magic folder: {dest}")
    print(f"[+] Password for country {country}: {pw}")
    if copied:
        print(f"[+] Bundled APKs: {', '.join(copied)}")
    else:
        print("[!] No APKs copied (you can add them manually to the folder)")

    print("\nNext:")
    print("  1. Safely eject the USB.")
    print("  2. Plug into the BYD.")
    print(f"  3. Enter password: {pw}")
    print("  4. Install the APKs shown.")

def main():
    parser = argparse.ArgumentParser(
        description="Prepare a USB for trivial third-party APK install on BYD DiLink 3 (AftermarketInstallTool path)."
    )
    parser.add_argument("--apk", action="append", default=[], help="APK file to bundle (repeatable)")
    parser.add_argument("--country", type=int, default=55, help="Country code (default 55=Brazil). See docs for others.")
    parser.add_argument("--target", type=Path, default=None,
                        help="Target dir (mounted USB root). If omitted, creates ./byd-usb-<country> here.")
    parser.add_argument("--auto-country", action="store_true",
                        help="If adb connected, query the car's real sys.byd.countrycode and use it.")
    args = parser.parse_args()

    apks = [Path(p) for p in args.apk]

    adb = find_adb()
    country = args.country
    if args.auto_country and adb:
        real = query_car_countrycode(adb)
        if real:
            print(f"[*] Queried car: sys.byd.countrycode = {real}")
            country = real
        else:
            print("[!] Could not query car countrycode, using --country / default")

    if args.target:
        target = args.target.expanduser().resolve()
        if not target.exists():
            print(f"[!] Target {target} does not exist — create/mount the USB first.")
            sys.exit(1)
    else:
        target = Path.cwd() / f"byd-usb-{country}"
        target.mkdir(exist_ok=True)
        print(f"[*] Using local dir as target: {target}")
        print("    (copy the contents of this dir to a real FAT32 USB when ready)")

    prepare_usb(target, apks, country, adb)

if __name__ == "__main__":
    main()
