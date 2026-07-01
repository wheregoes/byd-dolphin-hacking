# Driver Display (Instrument Cluster) Research

Deep research into the BYD Dolphin's driver display (instrument cluster / IC).
Findings from decompiling ClusterDebug.apk, BydThemeStore.apk, framework.jar,
DiCarServer.apk, and live testing on the car.

## Architecture

The Dolphin has **two displays** driven by separate systems:

1. **IVI (In-Vehicle Infotainment)** — 1920×1080 touchscreen, Android 10, DiLink 3.0
   - This is the main head unit we interact with via ADB
2. **Cluster (Instrument Cluster)** — driver display behind the steering wheel
   - Runs a **separate Qt-based OS** (Qt 5.15.10 or Qt 6.5.5)
   - Renders speed, gauges, ADAS, warning lights, themes
   - Communicates with IVI via the **fission/cbox bridge**

### Fission Architecture

BYD uses a "fission" system to manage multiple OS instances:

```
IVI (Android)  ←→  fission_service  ←→  Cluster (Qt OS)
                   /system/bin/fission_service[ivi]
```

**Single-OS vs Dual-OS:**
- `ro.build.system.fission_single_os`:
  - `0` = Dual-OS: IVI and Cluster run as separate fission "cells" (containers)
  - `1` = Single-OS: Everything runs on one kernel, cluster via virtual display

**Our car: `fission_single_os=1` (Single-OS mode)**

This is critical — many cluster APIs check for single-OS mode and refuse to work.

### Fission Tools (shell-accessible)

| Tool | Purpose | Works? |
|------|---------|--------|
| `fission` | Manage cells (create/destroy/list/start/stop) | No cells on single-OS |
| `fission_ps` | List processes in container | Works (shows IVI processes) |
| `fission_screencap -d 1 -p <file>` | **Capture cluster screenshot** | **WORKS!** |
| `fission_screencap -d 0` | Capture IVI screenshot | Works |
| `fission_cbox_disp_mgr <sys_id> <state>` | Control display power | Needs binder callback (fails) |
| `fission_ivi_size <l> <t> <r> <b>` | Set display dimensions | Needs binder callback (fails) |
| `fission_reboot` | Reboot | Segfaults (SIGSEGV) |
| `fission_toolbox` | Unknown | "Fission_Toolbox!" only |
| `fission_corebox` | Unknown | "fission_corebox!" only |

**Cluster screenshot capture works!**
```bash
adb shell "fission_screencap -d 1 -p /data/local/tmp/cluster.png"
adb pull /data/local/tmp/cluster.png
```
Captures 1920×1080 RGBA PNG of the cluster display.

### Cluster Hardware/Firmware

- **Cluster Qt versions**: Qt 5.15.10 AND Qt 6.5.5 (both present in `/vendor/`)
  - `/vendor/FissionCluster_5_15_10/` — Qt 5.15 QML files
  - `/vendor/FissionCluster_6_5_5/` — Qt 6.5 QML files
- **Cluster app library**: `/system/lib64/libBydCluster.so` (31MB)
  - Qt-based, uses FlatBuffers for IVI↔Cluster communication
  - `DataSourceManager` class handles theme/RCC changes
- **Cluster theme resources**: `/system/lib64/cluster_theme1.rcc` (132MB), `cluster_theme2.rcc` (124MB)
  - Qt RCC (Resource Collection) files — compiled Qt resources
  - `/system/lib64/commonTranslate.rcc` — translation resources
- **gbClientVersion**: `dilink3.0_6125f_ivi_cluster_mp0712_dev.202503180`

## AutoContainer Service (IVI → Cluster Bridge)

### IAutoContainer AIDL

Found in `framework.jar` → `android.os.IAutoContainer`:

```java
interface IAutoContainer {
    int sendJson(int type, String json);           // transaction 1
    int sendInfo(int type, int infoInt, String infoStr);  // transaction 2
    int sendInfo2(int type, byte[] data);          // transaction 3
    int registerCallback(IContainerCallback callback);    // transaction 4
    int getProjectionDisplayInfo(ProjectionDisplayInfoParcel info); // transaction 5
}
```

- Service name: `AutoContainer` (service #23)
- Manager: `android.os.AutoContainerManager`
- Accessible from shell: `adb shell service call AutoContainer ...`

### Single-OS Block

**All AutoContainer methods throw `IllegalStateException: no AutoContainerNative`**

The service binder is registered but the native implementation (`AutoContainerNative`)
is not initialized in single-OS mode. The cluster bridge requires dual-OS fission mode.

Tested:
```bash
adb shell service call AutoContainer 2 i32 1000 i32 14 s16 " "  # sendInfo → "no AutoContainerNative"
adb shell service call AutoContainer 1 i32 0 s16 test            # sendJson → "no AutoContainerNative"
adb shell service call AutoContainer 3 i32 8                     # sendInfo2 → "no AutoContainerNative"
```

Java test (`ClusterCmd.java`):
```
adb shell "CLASSPATH=/data/local/tmp/clustercmd.dex app_process / ClusterCmd 14"
→ ERROR: java.lang.IllegalStateException: no AutoContainerNative
```

### ClusterDebug.apk Command Table

Decompiled `com.byd.clusterdebug` (ClusterDebug.apk, `/system/priv-app/ClusterDebug/`).

The app sends `AutoContainerManager.sendInfo(1000, cmdId, "")` to the cluster.

**Full command list (from SecondActivity.infoListInit):**

| Cmd | Description (Chinese) | English |
|-----|----------------------|---------|
| 0 | 主机恢复仪表视频流 | Resume cluster video stream |
| 1 | 主机断开仪表视频流 | Disconnect cluster video stream |
| 2 | 所有警告灯点亮 | All warning lights ON |
| 3 | 所有警告灯熄灭 | All warning lights OFF |
| 4 | 所有警告灯4HZ闪烁 | All warning lights 4Hz flash |
| 5 | 所有警告灯根据实际报文显示 | Warning lights per actual CAN |
| **6** | **白天模式** | **Day mode** |
| **7** | **黑夜模式** | **Night mode** |
| **8** | **经典模式** | **Classic mode (dashboard)** |
| **9** | **科技模式** | **Tech mode (dashboard)** |
| 10 | 表盘100ms自动刷新测试模式开启 | 100ms auto-refresh test ON |
| 11 | 表盘自动刷新测试模式关闭 | Auto-refresh test OFF |
| 12 | 显示Adas | Show ADAS |
| 13 | 关闭Adas | Hide ADAS |
| 14 | FPS显示 | FPS display ON |
| 15 | FPS关闭 | FPS display OFF |
| 16 | 全屏投屏开启 | Full-screen cast ON |
| 17 | 半屏投屏开启 | Half-screen cast ON |
| 18 | 投屏关闭 | Cast OFF |
| 19 | osd序列帧开启 | OSD sequence frames ON |
| 20 | osd序列帧关闭 | OSD sequence frames OFF |
| 21 | 车辆类型纯电 | Vehicle type: pure electric |
| 22 | 车辆类型混动 | Vehicle type: hybrid |
| 23 | 车辆类型燃油 | Vehicle type: fuel |
| 24-28 | 表盘XXms自动刷新 | Various refresh rates |
| 29 | 切换到8.8寸屏 | Switch to 8.8" screen |
| 30 | 切换到12.3寸屏 | Switch to 12.3" screen |
| 31 | 切换到10.25寸屏 | Switch to 10.25" screen |
| 32 | 3d adas自刷新开启 | 3D ADAS auto-refresh ON |
| 33 | 3d adas自刷新关闭 | 3D ADAS auto-refresh OFF |
| 34 | Di3.0 | Di3.0 mode |
| 35 | Di4.0 | Di4.0 mode |
| 36 | Qt截屏/adb截屏 | Qt/ADB screenshot |
| 37 | 设置Log等级为DEBUG | Log level DEBUG |
| 38 | 设置Log等级为INFO | Log level INFO |
| 39 | 简易导航 | Simple navigation |
| 40 | dump some info | Dump info |
| 41 | 压力测试开 | Stress test ON (DO NOT USE!) |
| 42 | 压力测试关 | Stress test OFF |
| 74 | 不加载皮肤 | No skin |
| 75 | 加载皮肤1 | Load skin 1 |
| 76 | 加载皮肤2 | Load skin 2 |
| 83 | 强制显示腾势UI | Force Denza UI |
| 84 | 强制显示王朝UI | Force Dynasty UI |
| 85 | 根据车型显示UI | Show UI by car model |
| 88 | 不加载车体图 | No car body image |
| 89 | 加载车体图 | Load car body image |
| 90 | 显示海洋经典模式表盘 | Ocean classic mode dashboard |
| 218 | 仪表录屏功能开启 | Cluster screen recording ON |
| 219 | 仪表录屏功能关闭 | Cluster screen recording OFF |
| 257 | 关闭灯光类指示灯 | Light indicators OFF |
| 258 | 开启灯光类指示灯 | Light indicators ON |
| 259 | 关闭ADAS指示灯 | ADAS indicators OFF |
| 260 | 开启ADAS指示灯 | ADAS indicators ON |
| 278 | 显示R4原有竞速模式 | R4 original racing mode |
| 279 | 显示R4纽北样式竞速模式 | R4 Nürburgring racing mode |

**None of these commands work on our car** — all fail with `no AutoContainerNative`.

### MainActivity Commands (DiLink 3.0 specific)

From `MainActivity.infoListInit`:
- Commands 0, 1, 14, 15, 19, 20, 36, 200, 201, 212, 218, 219, 257-268, 278, 279
- DiLink 6.0 only: 49, 50, 86, 87, 222, 223
- DiLink 5.0 only: 108, 109 (force new/old UI for Dynasty/Ocean)

## BydThemeStore — Cluster Skin/Theme API

Decompiled `com.byd.automultipletheme` (BydThemeStore.apk, `/system/app/BydThemeStore/`).

### ClusterApiManager (`spi/x3.java`)

Manages cluster theme/skin/carbody/NFC/translation resources.

**Protocol**: IVI sends FlatBuffer `PadToClusterReq` to cluster via `sendInfo2(8, flatBufferBytes)`.
Cluster responds via `IContainerCallback.receivedInfo2()`.

**PadToClusterReq FlatBuffer schema (13 fields):**
```
field 0: cmdId (int)    — 1=QUERY, 2=SET_SKIN, 3=SET_CAR_SKIN
field 1: subId (int)    — 0=default, 1=query skin, 2=query car info
field 2: intParam1 (int) — 1=filename, 0=path
field 3: intParam2 (int)
field 4: intParam3 (int)
field 5: intParam4 (int)
field 6: intParam5 (int)
field 7: strParam1 (String) — filename/resource path
field 8: strParam2 (String)
field 9: strParam3 (String)
field 10: strParam4 (String)
field 11: strParam5 (String)
field 12: arrayParam (byte[]) — array data
```

**Commands:**
| cmdId | subId | Description |
|-------|-------|-------------|
| 1 | 0 | Query (deprecated: all info) |
| 1 | 1 | Query skin info |
| 1 | 2 | Query car body info |
| 1 | 3 | Query NFC resource info |
| 1 | 4 | Query translation resource info |
| 2 | 0 | Set cluster skin (by filename or path) |
| 3 | 0 | Set car body image (same-skin color) |

**Response (ClusterToPadRes):**
- `cmdId`, `subId`, `intParam1` (status), `intParam2` (theme mode), `intParam3` (skin type), `intParam4` (carbody type), `intParam5` (unused)
- `strParam1` (UI version), `strParam2` (resource path), etc.

### Theme/Mode List

From `y3.B` (theme mode constants):
| ID | Theme |
|----|-------|
| 0 | Default only |
| 1 | Di5 Denza UI Minimal (old UI) |
| 2 | Di5 Denza UI Fashion (old UI) |
| 17 | Di5 Dynasty UI Minimal (old UI) |
| 18 | Di5 Dynasty UI Tech (old UI) |
| 33 | Di5 Ocean UI Minimal (old UI) |
| 34 | Di5 Ocean UI Classic (old UI) |
| 35 | Di4 12.3" Classic (old UI) |
| 36 | Di4 12.3" Fashion (old UI) |
| 37 | Di4 10.25/8.8" Minimal (old UI) |
| 38 | Di4 10.25/8.8" Classic (old UI) |
| 39 | Di5/Di5.1 Dynasty 12.3" Qt default |
| 42 | Di5/Di5.1 Ocean 10.25" Qt default |
| 45 | Di5/Di5.1 Ocean 12.3" Qt default |
| 49 | Di5/Di5.1 Enthusiast default |
| 50 | Di5/Di5.1 Enthusiast simple |
| 64 | Di5/Di5.1 Denza UXE 10.25" Kanzi default |
| 67-69 | Di5/Di5.1 Denza HT 13.2" Kanzi |
| 80 | Di6 R1 23.6" Kanzi default |
| 83 | Di6 R2 23.6" Kanzi default |
| 86 | Di6 R3 23.6" Kanzi default |
| 89 | Di6 R4 10.25" Kanzi default |
| 92 | Di50L 10.25" Qt default |
| 95 | Di50L 10.25" odd-shaped Qt default |
| 98 | Di5/Di5.1 Denza EX default |

### Single-OS Block (Again)

`ClusterApiManager.init()` checks:
```java
if (dVar2 != DILINK_HOST_DiLink100F && !fission_single_os.equals("0")) {
    Log.i("[Cluster]-IntercomSdk", "init fail, it's single os");
    return 1;
}
```

Our car: `fission_single_os=1`, `inswver=6125f...` → `DILINK_HOST_3_0_6125F`
→ **init fails, skin API unavailable**.

### DiLink Host Types

```
DILINK_HOST_INVALID
DILINK_HOST_3_0          (inswver starts with "Di3.0")
DILINK_HOST_3_0_6125F    (inswver starts with "6125f") ← OUR CAR
DILINK_HOST_3_0_DiLink50F_LC  (inswver starts with "DiLink50F_LC")
DILINK_HOST_4_0          (inswver starts with "Di4.0")
DILINK_HOST_5_0          (inswver starts with "Di5.0")
DILINK_HOST_5_1          (inswver starts with "Di5.1")
DILINK_HOST_6            (inswver starts with "Di6.0")
DILINK_HOST_DiLink100F   (inswver starts with "DiLink100f")
```

### Theme Set Error Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 9001 | Directory doesn't exist |
| 9002 | Directory is empty |
| 9003 | Multiple files in directory |
| 9004 | No resource files in directory |
| 9005 | UI version mismatch |
| 9006 | Resource file load failure |
| 9007 | Input is empty |
| 9008 | Duplicate setting |
| 9009 | File doesn't exist |
| 9010 | Not in P gear |
| 9011 | Switching too frequent |
| 9012 | No such default/preset skin ID |

## BYDAuto Instrument Device API

### BYDAutoInstrumentDevice (`android.hardware.bydauto.instrument`)

Device type: **3 (INSTRUMENT)**

**Key methods (from decompiled stubs):**
- `getInstrumentScreenType()` — returns screen type
- `getInstrumentView()` / `setViewSwitch(int)` — current/switch view
- `getViewStatus()` — view status
- `getBacklightBrightness()` / `setBacklightBrightness(int)` — backlight
- `getBacklightModeState(int)` / `setBacklightModeState(int, int)` — backlight mode
- `getUnit(int)` / `setUnit(int, int)` — measurement units
- `getMileageUnit()`, `getSpeedUnit()`, `getPowerUnit()` — specific units
- `getAverageSpeed()` — avg speed
- `getMalfunctionInfo(int)` / `getMalfunctionList()` — DTC info
- `sendMusicState(int)`, `sendMusicInfo(byte[])` — media info to cluster
- `sendCallState(int)`, `sendCallInfo(byte[])` — call info to cluster
- `sendRadioState(int)`, `sendRadioInfo(byte[])` — radio info to cluster
- `sendSimpleGuidanceInfo(int, int)` — navigation guidance
- `sendCameraGuidanceInfo(int, int, int)` — camera guidance
- `sendSafeGuidanceInfo(int, int, int)` — safety guidance
- `sendRestRouteInfo(int, int, long)` — remaining route
- `sendThreeLineLyrics(String, String, String)` — lyrics display
- `setClearFault(int)` — clear fault codes
- `setDrivingInfoSwitch(int)` — driving info toggle

**View constants:**
```
INSTRUMET_INVALID_VIEW = 0
INSTRUMET_DRIVING_VIEW = 1
INSTRUMET_MENU_VIEW = 2
INSTRUMET_FAULT_VIEW = 3
INSTRUMET_CHARGE_VIEW = 4
INSTRUMET_DISCHARGE_VIEW = 5
INSTRUMET_ADAS_VIEW = 6
INSTRUMET_TRAVEL_VIEW = 7
INSTRUMET_ACCELEROMETER_VIEW = 8
```

### Permissions Required

- `android.permission.BYDAUTO_INSTRUMENT_COMMON` — for getInstance()
- `android.permission.BYDAUTO_INSTRUMENT_GET` — for get methods
- `android.permission.BYDAUTO_INSTRUMENT_SET` — for set methods

**Shell UID 2000 does NOT have these permissions.**

Test:
```
adb shell "CLASSPATH=/data/local/tmp/instdev.dex app_process / InstrumentDeviceTest"
→ SecurityException: Neither user 2000 nor current process has
  android.permission.BYDAUTO_INSTRUMENT_COMMON.
```

### BYDAutoManager Direct Access (Partial)

BYDAutoManager's `getInt(devType, featureId)` works for SOME instrument features
without the INSTRUMENT permissions (it uses the BYDAUTO_COMMON permission).

**Live test results (ClusterProbe.java):**

| Feature ID | Name | Value | Status |
|-----------|------|-------|--------|
| 0x4A50B01E | InstrumentScreenType | -10011 | NOT_REGISTERED |
| 0x4A50B020 | InstrumentView | -10011 | NOT_REGISTERED |
| 0x4A50B024 | ViewStatus | -10011 | NOT_REGISTERED |
| 0x4A50B026 | ViewSwitch | -10011 | NOT_REGISTERED |
| 0x4A50B028 | BacklightBrightness | -10011 | NOT_REGISTERED |
| 0x4A50B02A | BacklightAutoModeState | -10011 | NOT_REGISTERED |
| 0x4A50B02C | BacklightLinkModeState | -10011 | NOT_REGISTERED |
| 0x4BF0002D | BacklightCtlType | **1** | **OK** |
| 0x49C00028 | InsThemeValue | **0** | **OK** |
| 0x40C0B010 | InstrumentTheme | -10011 | NOT_REGISTERED |
| 0x28C02021 | InstrumentThemeStatus | -10011 | NOT_REGISTERED |
| 0x28C0201B | InstrumentThemeVersion | -10011 | NOT_REGISTERED |
| 0x30100024 | ModeSwitchConfigStatus | -10011 | NOT_REGISTERED |
| 0x3150102C | RiesChildModeSwitch | -10011 | NOT_REGISTERED |
| 0x4A50B030 | MileageUnit | -10011 | NOT_REGISTERED |
| 0x4A50B032 | SpeedUnit | -10011 | NOT_REGISTERED |
| 0x4A50B034 | PowerUnit | **1** | **OK** |
| 0x4A50B036 | TempUnit | **1** | **OK** |
| 0x4A50B038 | PressUnit | -10013 | INVALID_PARAM |
| 0x4A50B03A | ConDisUnit | -10011 | NOT_REGISTERED |
| 0x4C130041 | NavigationStyle | -10011 | NOT_REGISTERED |
| 0x4EF53010 | MenuDisplaySettings | -10011 | NOT_REGISTERED |
| 0x4EF53012 | ThemeDisplaySettings | -10011 | NOT_REGISTERED |

**Error codes:**
- `-10011` = NOT_REGISTERED (feature ID not in MCU's registered list)
- `-10013` = INVALID_PARAM
- `-2147482648` (0x80000008) = MCU_FAILED

### SET Attempts

All SET attempts on instrument features return `MCU_FAILED` or hang:
```
SET InsThemeValue(0x49C00028, 0-5) → MCU_FAILED (-2147482648)
SET PowerUnit(0x4A50B034, 2) → MCU_FAILED
SET TempUnit(0x4A50B036, 0) → TIMEOUT (hangs)
SET BacklightCtlType(0x4BF0002D, 0) → TIMEOUT (hangs)
```

The MCU accepts the request but rejects the write — likely because these are
read-only status signals, not settable properties on the Dolphin.

## CAN Broadcast Path (com.byd.cluster.spi)

ClusterDebug.apk registers a broadcast receiver for `com.byd.cluster.spi`:
- Receives CAN frame data as hex string in `normal` or `wholeFrame` extra
- Parses to byte array
- Injects via `BYDAutoTestDevice.set(TEST_SIMULATE_DOWN_SET, bufferDataValue)`

This simulates CAN messages to the cluster. Requires:
- `BYDAUTO_TEST_SET` permission
- `ClusterDebugService` running (must launch MainActivity first)

Test:
```bash
adb shell "am start -n com.byd.clusterdebug/.MainActivity"  # start the app
adb shell "am broadcast -a com.byd.cluster.spi --es normal '00,01,02'"
```
No visible effect in logs — the receiver may need the service registered first,
or the CAN simulation may not affect the cluster in single-OS mode.

## What Works / What Doesn't

### ✅ Works
- **Cluster screenshot capture**: `fission_screencap -d 1 -p <file>`
- **Reading some instrument values via BYDAutoManager**: BacklightCtlType=1, InsThemeValue=0, PowerUnit=1, TempUnit=1
- **ClusterDebug app launch**: MainActivity starts (but SecondActivity not exported)
- **BydThemeStore app**: present, decompiled, reveals full cluster theme protocol

### ❌ Blocked
- **AutoContainer service**: "no AutoContainerNative" in single-OS mode
- **Cluster theme/skin changes**: ClusterApiManager refuses init on single-OS
- **Cluster debug commands** (day/night, classic/tech, FPS, etc.): require AutoContainer
- **BYDAutoInstrumentDevice API**: requires BYDAUTO_INSTRUMENT_COMMON/SET permissions
- **DiCar ContentProvider**: UID check blocks shell (package "android" ≠ uid 2000)
- **Setting instrument values via BYDAutoManager**: MCU_FAILED or timeout

### ⚠️ Theoretically Possible (Not Yet Tested)
- **Custom RCC theme file**: push to `/data/` or `/sdcard/` and reference via skin path
  - But `sendInfo2(8, ...)` is blocked, so can't tell cluster to load it
- **CAN simulation via broadcast**: `com.byd.cluster.spi` receiver
  - Needs proper CAN frame format for cluster theme change
  - Needs `BYDAUTO_TEST_SET` permission (shell doesn't have it)
- **Root + remount /system**: replace `cluster_theme1.rcc`/`cluster_theme2.rcc`
  - Requires root (user chose not to root)

## Conclusion

The driver display (instrument cluster) on the BYD Dolphin DiLink 3.0 is
**heavily locked down in single-OS mode**. The primary control path
(AutoContainer service) is a stub — the native implementation only exists
in dual-OS (fission cell) mode, which our car doesn't use.

The cluster Qt app runs independently and receives data via:
1. CAN bus (speed, gauges, warning lights) — handled by MCU firmware
2. IVI bridge (themes, skins, media info, navigation) — via AutoContainer (blocked)

**What we CAN do:**
- Capture cluster screenshots for monitoring/debugging
- Read a few instrument status values (PowerUnit, TempUnit, BacklightCtlType, InsThemeValue)

**What we CANNOT do without root:**
- Change cluster theme/skin
- Switch dashboard modes (classic/tech/day/night)
- Toggle warning lights/ADAS display
- Send custom media/navigation info to cluster
- Load custom RCC theme files

**Root would enable:**
- Replacing `cluster_theme*.rcc` files in `/system/lib64/`
- Patching `libBydCluster.so` to bypass single-OS check
- Granting `BYDAUTO_INSTRUMENT_SET` to shell
- Directly modifying AutoContainer service implementation

## Scripts Created

- `scripts/ClusterCmd.java` — Send AutoContainer debug commands (blocked by single-OS)
- `scripts/ClusterProbe.java` — Probe instrument feature IDs via BYDAutoManager
- `scripts/ClusterTest.java` — Targeted test of working instrument features
- `scripts/InstrumentDeviceTest.java` — Direct BYDAutoInstrumentDevice API test (blocked by permission)

## Decompiled APKs

- `apk-analysis/ClusterDebug_decompiled/` — Cluster debug command app
- `apk-analysis/BydThemeStore_decompiled/` — Theme store with cluster skin API
- `apk-analysis/framework_decompiled/` — framework.jar (IAutoContainer AIDL)
- `apk-analysis/services_decompiled/` — services.jar
- `apk-analysis/ext_decompiled/` — ext.jar
- `apk-analysis/DiCarServer_decompiled/` — DiCarServer (BYDAutoInstrumentDevice)

## CAN Bus Injection (VCDS-Style) — WORKS!

### The CAN Injection Path

The `com.byd.cluster.spi` broadcast → `BYDAutoTestDevice.TEST_SIMULATE_DOWN_SET`
path is **functional** on our car! This is the equivalent of VCDS for BYD.

**How to use it:**

1. Start the ClusterDebug app + service:
```bash
adb shell "am start -n com.byd.clusterdebug/.MainActivity"
adb shell "am startservice -n com.byd.clusterdebug/.ClusterDebugService"
```

2. Send CAN frames via broadcast:
```bash
# "normal" extra — raw data bytes
adb shell "am broadcast -a com.byd.cluster.spi --es normal 'FF,FF,FF,FF,FF,FF,FF,FF'"

# "wholeFrame" extra — complete CAN frame (includes CAN ID)
adb shell "am broadcast -a com.byd.cluster.spi --es wholeFrame '28,C0,00,00,00,00,00,00,00'"
```

3. Verify in logcat:
```
ClusterDebugService: receive normal: FF,FF,FF,FF,FF,FF,FF,FF
AbsBYDAutoDevice: set featureID is aa00020f bufferDataValue is [-1, -1, -1, -1, -1, -1, -1, -1]
```

The bytes are injected via `BYDAutoTestDevice.set({0xAA00020F}, bufferDataValue)`
which simulates CAN frames on the bus. The cluster (and other ECUs) receive
these as if they came from the actual CAN bus.

**Permissions**: The ClusterDebug app has `BYDAUTO_TEST_SET` permission.
Shell (UID 2000) triggers the broadcast, and the app's receiver does the
actual injection with its own permissions. **This works without root!**

### OBD Diagnostic Protocol (UDS)

Decompiled `BydDevelopmentTools.apk` reveals the full BYD OBD diagnostic system:

**DiagnoseManager (`a.a.a.k0.a`)** — UDS (ISO 14229) diagnostic communication:

- **Send OBD data**: `BYDAutoOtaDevice.set({0xAA000140}, eventValue)` — sends UDS frames
- **Receive OBD responses**: `BYDAutoOtaDevice.registerListener(listener, {0x99000140})` — listens for responses
- **Configure MCU OBD monitor**: `BYDAutoSettingDevice.set({0xAA000241}, ...)` — sets receive ID

**Frame format** (from `DiagnoseManager.c()`):
```
[checksum_hi, checksum_lo,  # 2 bytes: checksum of payload
 total_packets,              # 1 byte: total number of packets
 packet_num,                 # 1 byte: current packet number
 data_len,                   # 1 byte: payload length
 0x00, 0x03, 0xE8,          # 3 bytes: protocol header
 0x00, 0x01,                # 2 bytes: constant
 request_id_hi, request_id_lo, # 2 bytes: sender CAN ID
 0x01,                       # 1 byte: constant
 receive_id_hi, receive_id_lo, # 2 bytes: target CAN ID
 ... payload bytes ...]      # UDS diagnostic data
```

**UDS commands found:**
- `3E 80` = Tester Present (keep-alive/handshake)
- `10 05` = Diagnostic Session Control → extended diagnostic session

**CAN domains (networks):**
1. 智能进入网 (Smart Entry)
2. 车身网 (Body)
3. 能量网 (Energy)
4. 底盘网 (Chassis)
5. ADAS网 (ADAS)
6. 车身网2 (Body 2)

**CAN IDs:**
- Left domain: requestId=1824 (0x720), receiveId=1832 (0x728)
- Right domain: requestId=1863 (0x747), receiveId=1871 (0x74F)
- IPB (Parking Brake): requestId=1922 (0x782), receiveId=1930 (0x78A)

### What's Needed to Enable Hidden Features

To do "VCDS-style" feature coding, we need:

1. **CAN injection** — ✅ WORKS via `com.byd.cluster.spi` broadcast
2. **UDS diagnostic protocol** — known (frame format documented above)
3. **CAN IDs for target ECUs** — partially known (6 networks, specific IDs)
4. **UDS service IDs for feature coding** — NOT YET KNOWN

The missing piece: **which UDS services/identifiers control specific features**.
BYD uses standard UDS (0x10=session, 0x22=read by ID, 0x2E=write by ID, 0x3E=tester present),
but the coding data identifiers for each feature are proprietary.

**Possible approaches:**
- Sniff CAN bus traffic while using the official BYD diagnostic tool
- Reverse engineer the `BydHealthDiagnostic.apk` for more UDS identifiers
- Try standard UDS sequences (session control → security access → read/write by ID)
- Use the CAN injection path to send UDS frames to specific ECUs

### Comparison: VCDS vs BYD CAN Coding

| Aspect | VAG (VCDS) | BYD |
|--------|-----------|-----|
| Protocol | UDS/KWP2000 over CAN | UDS over CAN |
| Tool | External OBD2 dongle | Built-in Android app |
| Access | Plug into OBD2 port | ADB broadcast (no hardware) |
| Permissions | None (hardware access) | `BYDAUTO_TEST_SET` (via ClusterDebug) |
| Feature coding | Change ECU coding values | Write UDS identifiers |
| Security | PIN/SKC for some features | Security access seed/key exchange |
