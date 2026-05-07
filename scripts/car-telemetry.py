#!/usr/bin/env python3
"""
BYD Dolphin Car Telemetry Logger

Connects to the car via ADB and polls vehicle data from content providers.
Logs data to JSON files for analysis.

Usage:
    python3 car-telemetry.py                    # Single snapshot
    python3 car-telemetry.py --watch            # Continuous polling (30s interval)
    python3 car-telemetry.py --watch --interval 10  # Custom interval
    python3 car-telemetry.py --output data.json # Custom output file
"""

import subprocess
import json
import re
import sys
import time
import argparse
from datetime import datetime
from pathlib import Path

CAR_IP = "192.168.10.10"
CAR_PORT = "5555"
CAR_STATUS_URI = "content://com.byd.carStatusProvider/car_status"


def adb_connect():
    result = subprocess.run(
        ["adb", "connect", f"{CAR_IP}:{CAR_PORT}"],
        capture_output=True, text=True, timeout=10
    )
    output = result.stdout.strip()
    if "connected" not in output and "already" not in output:
        print(f"Failed to connect: {output}")
        sys.exit(1)
    print(f"Connected to {CAR_IP}:{CAR_PORT}")


def adb_shell(command):
    result = subprocess.run(
        ["adb", "shell", command],
        capture_output=True, text=True, timeout=15
    )
    return result.stdout.strip()


def parse_content_query(raw):
    rows = {}
    for line in raw.split("\n"):
        match = re.match(r"Row: \d+ id=\d+, key=(.+?), value=(.*)", line)
        if match:
            rows[match.group(1)] = match.group(2)
    return rows


def parse_energy_points(raw_value):
    if not raw_value or raw_value == "#":
        return []
    segments = raw_value.strip("#").split("#")
    trips = []
    current_trip = []
    for seg in segments:
        try:
            val = int(seg)
        except ValueError:
            continue
        if val == 1347:
            if current_trip:
                trips.append(current_trip)
                current_trip = []
        else:
            current_trip.append(val)
    if current_trip:
        trips.append(current_trip)
    return trips


def get_car_status():
    raw = adb_shell(f'content query --uri {CAR_STATUS_URI}')
    return parse_content_query(raw)


def get_system_settings():
    raw = adb_shell("settings list system")
    settings = {}
    for line in raw.split("\n"):
        if "=" in line:
            key, _, value = line.partition("=")
            if any(k in key.lower() for k in ["byd", "car", "vehicle", "charging", "sound"]):
                settings[key] = value
    return settings


def get_device_info():
    return {
        "model": adb_shell("getprop ro.product.model"),
        "android_version": adb_shell("getprop ro.build.version.release"),
        "build_id": adb_shell("getprop ro.build.display.id"),
        "serial": adb_shell("getprop ro.serialno"),
        "uptime": adb_shell("uptime"),
    }


def snapshot():
    ts = datetime.now().isoformat()
    print(f"[{ts}] Polling car data...")

    car_status = get_car_status()
    system_settings = get_system_settings()

    maintenance_time = int(car_status.get("car_status_maintenance_time", -1))
    maintenance_mile = int(car_status.get("car_status_maintenance_mile", -1))
    issues = int(car_status.get("car_status_issue_num", 0))
    elec_trips = parse_energy_points(car_status.get("travel_points_elec", ""))

    data = {
        "timestamp": ts,
        "maintenance": {
            "days_remaining": maintenance_time,
            "km_remaining": maintenance_mile,
            "interval_days": int(car_status.get("set_car_status_maintenance_time", 0)),
            "interval_km": int(car_status.get("set_car_status_maintenance_mile", 0)),
        },
        "issues": {
            "count": issues,
            "details": car_status.get("car_status_issue", ""),
        },
        "fluids": {
            "engine_oil_ok": car_status.get("dicare_engine_oil_no_prompt") == "0",
            "at_fluid_ok": car_status.get("dicare_at_fluid_no_prompt") == "0",
            "brake_fluid_ok": car_status.get("dicare_brake_fluid_no_prompt") == "0",
            "battery_coolant_ok": car_status.get("dicare_battery_coolant_no_prompt") == "0",
            "motor_coolant_ok": car_status.get("dicare_motor_coolant_no_prompt") == "0",
        },
        "energy": {
            "trip_count": len(elec_trips),
            "trips": elec_trips,
        },
        "vehicle_type": system_settings.get("KEY_VEHICLE_TYPE", "unknown"),
        "device_id": system_settings.get("KEY_DEVICE_ID", "unknown"),
        "raw_car_status": car_status,
        "raw_settings": system_settings,
    }

    print(f"  Maintenance: {maintenance_time} days / {maintenance_mile} km remaining")
    print(f"  Issues: {issues}")
    print(f"  Electric trips recorded: {len(elec_trips)}")

    return data


def main():
    parser = argparse.ArgumentParser(description="BYD Dolphin Car Telemetry Logger")
    parser.add_argument("--watch", action="store_true", help="Continuous polling mode")
    parser.add_argument("--interval", type=int, default=30, help="Polling interval in seconds (default: 30)")
    parser.add_argument("--output", type=str, default=None, help="Output JSON file path")
    parser.add_argument("--info", action="store_true", help="Show device info and exit")
    args = parser.parse_args()

    adb_connect()

    if args.info:
        info = get_device_info()
        print(json.dumps(info, indent=2))
        return

    if args.watch:
        output_dir = Path("telemetry-logs")
        output_dir.mkdir(exist_ok=True)
        print(f"Watching every {args.interval}s. Ctrl+C to stop.")
        snapshots = []
        try:
            while True:
                data = snapshot()
                snapshots.append(data)
                log_file = output_dir / f"telemetry-{datetime.now().strftime('%Y%m%d')}.json"
                log_file.write_text(json.dumps(snapshots, indent=2))
                print(f"  Saved to {log_file} ({len(snapshots)} records)")
                time.sleep(args.interval)
        except KeyboardInterrupt:
            print(f"\nStopped. {len(snapshots)} records saved.")
    else:
        data = snapshot()
        if args.output:
            Path(args.output).write_text(json.dumps(data, indent=2))
            print(f"Saved to {args.output}")
        else:
            print(json.dumps(data, indent=2))


if __name__ == "__main__":
    main()
