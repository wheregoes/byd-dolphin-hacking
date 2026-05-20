#!/usr/bin/env python3
"""Dump complete list of ALL 475 flag IDs from the extracted data."""

import json

with open("/home/thiagofalcao/Documents/github/byd-dolphin-hacking/data/chromium_flags_full.json") as f:
    flags = json.load(f)

# Group by section
available = [f for f in flags if "unavailable" not in f["section"]]
unavailable = [f for f in flags if "unavailable" in f["section"]]

print(f"TOTAL FLAGS: {len(flags)}")
print(f"  Available: {len(available)}")
print(f"  Unavailable: {len(unavailable)}")
print()

for section_name, section_flags in [("AVAILABLE", available), ("UNAVAILABLE", unavailable)]:
    print(f"=== {section_name} FLAGS ({len(section_flags)}) ===")
    for f in section_flags:
        opts = f["options"][0] if f["options"] else {}
        selected = opts.get("selected", "?")
        all_opts = opts.get("all", [])
        default_mark = "" if f["isDefault"] else " ***NON-DEFAULT***"
        print(f"  {f['id']}{default_mark}")
        if f["name"]:
            print(f"    {f['name'][:100]}")
        if not f["isDefault"]:
            print(f"    Current: {selected}")
        if len(all_opts) > 1:
            print(f"    Options: {all_opts}")
    print()
