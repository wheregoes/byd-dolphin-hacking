# Camera System

> **Status change (2026-09):** this page previously concluded that camera frame access was
> **not feasible** without root. That is **wrong** and has been corrected below. Live 1280×960
> frames from all four surround cameras can be pulled with **no root**, from a `shell`-uid
> (2000) process. See [Frame access without root](#frame-access-without-root).

## Architecture

The BYD Dolphin has two separate camera APIs:

```
┌──────────────────────────────────────────────────────────┐
│  BYDAutoPanoramaDevice (android.hardware.bydauto.panorama)│
│  Controls: display mode, rotation, transparency           │
│  Permission: BYDAUTO_PANORAMA_GET/SET (server-side!)      │
│  Status: BLOCKED for third-party apps                     │
└──────────────────────────────────────────────────────────┘
                    ↓ (separate from)
┌──────────────────────────────────────────────────────────┐
│  AVMCamera / NormalCamera (android.hardware)              │
│  Source: /system/framework/bmmcamera.jar                  │
│  Controls: camera open/close, preview, frame callback     │
│  JNI: libbmmcamera_jni.so → libbmmcameraservice.so        │
│  Status: REACHABLE from a shell-uid process (see below)   │
└──────────────────────────────────────────────────────────┘
```

`BYDAutoPanoramaDevice` controls the 360 surround view display (mode, rotation, transparency) but does NOT provide direct camera frame access. `AVMCamera` from `bmmcamera.jar` is the actual camera API.

## BYDAutoPanoramaDevice

Permission checks are **server-side** in the IPC service — the `BydPermissionContext` bypass that works for AC and bodywork devices does NOT work here. All GET methods fail with `SecurityException`.

BydCamera app gets these permissions because it runs as `userId=1000` (system shared user `android.uid.system`).

### SET Methods (untested — requires PANORAMA_SET permission)

| Method | Signature |
|---|---|
| `setDisplayMode(int)` | Display layout mode |
| `setPanoOperation(int)` | Panorama operation |
| `setPanoOutputState(int)` | Enable/disable output |
| `setPanoRotation(int)` | View rotation angle |
| `setPanoramaTransparence(int)` | Overlay transparency |
| `setRFCameraSwitchState(int)` | Right-front camera on/off |
| `setPanoRemoteCall(int)` | Remote panorama trigger |
| `setPanoFocusState(int)` | Focus state |
| `setLVDSState(int)` | LVDS interface state |
| `setAPAAvmMode(int)` | APA (auto parking) AVM mode |

### IBYDAutoPanoService (AIDL)

Low-level IPC service behind `BYDAutoPanoramaDevice`:

| Method | Signature |
|---|---|
| `getValue(int)` | Read parameter by ID |
| `setValue(int, int)` | Write parameter by ID |
| `getBuffer(int)` | Read byte array by ID |
| `setBuffer(int, byte[])` | Write byte array by ID |
| `registerUser(listener)` | Register for events |
| `unregisterUser(listener)` | Unregister |

## AVMCamera / NormalCamera (bmmcamera.jar)

### Location

- JAR: `/system/framework/bmmcamera.jar`, mode `0644 root:root`
- **Not** on `BOOTCLASSPATH` — but world-readable, so any process can add it to its own class loader
- Native libs: `/system/lib64/libbmmcamera*.so`
- `libbmmcamera_jni.so` is listed in `/system/etc/public.libraries.txt`, so **any** process may load it

### Camera IDs

Derived from the system property `vehicle.config.cam_sort`.

> **Correction:** this page previously said the property is "not set on Dolphin — uses defaults".
> Measured: a `shell`-uid process reads it as **empty because it is SELinux-denied**, not because it
> is unset. The consequence is the same either way — every tag lookup returns `-1` — but the cause
> matters, because it is what makes `AVMCamera.open()` unusable from shell (below).

| Constant | String ID | Description |
|---|---|---|
| `CAMERA_CAR_FRONT` | "front" | Front bumper camera |
| `CAMERA_CAR_REAR` | "rear" | Rear/backup camera |
| `CAMERA_CAR_PANO_H` | "pano_h" | 360 panorama high-res |
| `CAMERA_CAR_PANO_L` | "pano_l" | 360 panorama low-res |
| `CAMERA_CAR_RF` | "rf" | Right-front camera |
| `CAMERA_CAR_DMS` | "dms" | Driver monitoring system |
| `CAMERA_CAR_FACE` | "face" | Face detection |
| `CAMERA_CAR_CARGO` | "cargo" | Cargo area |
| `CAMERA_CAR_PANO_APA` | "apa" | Automatic parking assist |
| `CAMERA_CAR_RVS` | "rvs" | Reverse camera |

`vehicle.config.cam_sort` also carries `vc0=`..`vc3=` tokens. Those are the four real camera ids
that make up the "pano_h" virtual camera — see
[Preview index semantics](#preview-index-semantics).

### API

```java
AVMCamera camera = AVMCamera.open(cameraId);   // null unless cam_sort is readable — see below
camera.addPreviewSurface(surface, viewMode);
camera.startPreview();
camera.stopPreview();
camera.setPreviewCallback(cb);                 // ByteBuffer frames
camera.setMediaCodec(mediaCodec, format);      // hardware encoding
camera.setPreviewSize(1280, 960);
camera.setCameraFps(fps);
camera.close();
```

### View Modes

| Constant | Value | Description |
|---|---|---|
| `VIEW_CHANNEL_1..4` | 1..4 | Single camera views |
| `VIEW_1_2_H` | 8 | Two cameras horizontal |
| `VIEW_1_2_V` | 14 | Two cameras vertical |
| `VIEW_DECUSSATION` | 5 | Cross/quad view |
| `VIEW_DEFAULT` | 0 | Default layout (4-in-1 composite) |

---

## Frame access without root

**Measured: a uid-2000 (`u:r:shell:s0`) `app_process` receives live camera frames.** No root, no
system signature, no boot-classpath modification.

### Why an *app* cannot do it, but a shell process can

`libbmmcamera.so` resolves the camera server **in the client's own process**:

```c
// libbmmcamera.so @ 0010b844  android::hardware::AVMCamera::assertStateLocked
android::String16::String16(aSStack_70, "bmmcameraserver");
...
iVar5 = getService<android::hardware::IBMMCamServer>(aSStack_70, this + 0x88);
```

So the caller needs `service_manager find` on `bmmcameraserver_service`. SELinux policy grants that
to `bmmcameraserver`, `platform_app` and `system_app` only — `untrusted_app*` is **absent**. But
`shell` holds `(allow shell base_typeattr_477 (service_manager (find)))`, and `base_typeattr_477`
is `service_manager_type` minus a blocklist that does **not** include `bmmcameraserver_service`.

**Consequence:** a normal installed app (`untrusted_app_25`) can never obtain the binder, however
many permissions it is granted. A `shell`-uid process can. Anything that wants camera frames must
therefore run its camera code in a process launched over ADB, not inside the APK.

### The route that works

1. Load the JAR into your own class loader — it is world-readable, just not on `BOOTCLASSPATH`:
   `BaseDexClassLoader.addDexPath("/system/framework/bmmcamera.jar")` on the system class loader.
2. `android.hardware.JNILoader` runs `System.loadLibrary("bmmcamera_jni")` in its static
   initialiser, and that library is in `public.libraries.txt`, so the native side loads cleanly.
3. **Skip `AVMCamera` entirely.** `AVMCamera.open(int)` returns `null` unless
   `BmmCameraInfo.isValidCamera(cameraId)` passes, and `BmmCameraInfo` derives every id from
   `vehicle.config.cam_sort`, which shell cannot read. Go one layer down to
   `android.hardware.JNIBMMCamera` instead.
4. `JNIBMMCamera` is package-private with a package-private `JNIBMMCamera(int cameraId)`
   constructor — reach it with `getDeclaredConstructor(int.class)` + `setAccessible(true)`. All its
   native methods are package-private too:

   ```java
   native boolean nativeOpen(int cameraId);            native boolean nativeClose();
   native boolean nativeStartPreview();                native boolean nativeStopPreview();
   native boolean nativeSetPreviewSize(int w, int h);  native boolean nativeSetCameraFPS(int fps);
   native int     nativeGetPreviewWidth();             native int     nativeGetPreviewHeight();
   ```
5. Run the whole thing as `app_process` over ADB:
   ```bash
   CLASSPATH=/path/to/your.dex app_process /path --nice-name=mydaemon com.example.Main
   ```

### Probe results (measured)

| fact | value |
|---|---|
| `SystemProperties.get("vehicle.config.cam_sort")` as shell | empty (SELinux-denied) |
| `BmmCameraInfo.getCameraNumbers()` as shell | `0` |
| `BmmCameraInfo.getCameraId(<any tag>)` as shell | `-1` |
| `AVMCamera.open(0)` as shell | `null` (property-backed `isValidCamera` gate) |
| `android.hardware.Camera.getNumberOfCameras()` as shell | `0` |
| `JNIBMMCamera` ctor + `nativeOpen(id)` | returns `true` for ids 0..7, but **only id 0** yields `nativeStartPreview()==true` and real frames |
| `nativeSetPreviewSize(1280, 960)` | `true` |
| `nativeSetCameraFPS(10)` | `false` — and not needed |
| observed callback rate | ~10–11 fps per channel |

Because the tag lookup always fails from shell, pass the **numeric** camera id (0) directly; a
`-1` can be handled by scanning ids 0..7 and keeping whichever one starts a preview.

---

## Preview index semantics

`nativeEnablePreviewCallbackWithBuffer(index)` on camera id 0:

| index | constant | delivers |
|---|---|---|
| 0 | `VIEW_DEFAULT` | one **5120×960** 4-in-1 composite, 7 372 800 B |
| 1..4 | `VIEW_CHANNEL_1..4` | one **1280×960** single camera each, 1 843 200 B |

**The index selects a physical camera channel server-side — no client-side cropping is needed**, and
all four channels can run simultaneously off a single open camera object.

Corroborated in the JNI layer, which validates buffer capacity per index
(`libbmmcamera_jni.so @ 00114264 JNIBMMCamera::addPreviewByteBuffer`):

```asm
; index 1..4  (required = w*h*3/8 == a quarter of a full frame)
14388: mul  w8, w4, w5          ; w4=mSrcBufWidth, w5=mSrcBufHeight
1438c: add  w8, w8, w8, lsl #1  ; *3
1439c: asr  w8, w8, #3          ; /8
143a0: cmp  x22, w8, sxtw       ; x22 = ByteBuffer capacity
; index 5 (VIEW_DECUSSATION) and the default branch use asr #1, i.e. w*h*3/2 (full frame)
```

`5120*960*3/8 = 1 843 200` and `5120*960*3/2 = 7 372 800` — exactly the measured sizes.

The "pano_h" camera is a **virtual** camera assembled from four real ones:

```c
// libbmmcamera_utils.so @ 0011cd68  isVC4In1VirtualCamera(int cameraId)
initCameraId();
if ((DAT_0011f038 == param_1) &&                                             /* id == mCamIdPanoH */
    (-1 < (int)(DAT_0011f04c & DAT_0011f048 & DAT_0011f050 & DAT_0011f054))) /* vc0..vc3 all >= 0 */
  return 0;   /* is a 4-in-1 virtual camera */
```

`VC4In1Frame::saveFrame(channel, ...)` (`libbmmcamera_utils.so @ 0011d8fc`) is the server-side
assembler: it rejects `channel >= 4`, keeps one timestamp per channel, interleaves each into a
shared buffer at horizontal offset `channel * width` with stride `channel * 4`, and
`VC4In1Frame::isValid()` requires all four channel timestamps to agree within **16 ms** before the
composite is published.

---

## Pixel format: NV21, not NV12

**The frame callback reports `colorFormat=21` (`COLOR_FormatYUV420SemiPlanar`, i.e. NV12 — Y then
interleaved U,V) but the buffer actually contains NV21 — Y then interleaved V,U.**

This is the single most costly gotcha on this stack. Treat the buffer as NV12 and every colour is
wrong: **blue sky renders orange, green foliage renders cyan-green**. The luma plane is unaffected,
so a monochrome or night-time scene looks completely fine — which is exactly how this survives
casual testing.

> **Verify colour in daylight.** A night frame from these cameras carries almost no chroma
> (measured mean |chroma − 128| ≈ 2.2 at night vs ≈ 4.8 in daylight), so a full Cb/Cr swap is
> invisible in it.

BYD's own JNI library agrees with the measurement — the buffer-capacity log strings in
`libbmmcamera_jni.so` describe the per-channel frame as a quarter of a full **NV21** frame.

### Fixing it for free

Do **not** reorder the bytes in software. The Qualcomm encoder accepts NV21 directly. Advertised
input colour formats for `OMX.qcom.video.encoder.hevc` / `.avc` on this head unit:

```
0x7fa30c06  0x7fa30c04  0x7fa30c00  0x7fa30c09  0x7fa30c0a
0x7fa30c08  0x7fa30c07  0x7f000789  0x7f420888  0x15
```

`0x7FA30C00` is `OMX_QCOM_COLOR_FormatYVU420SemiPlanar` — NV21. Set it as
`MediaFormat.KEY_COLOR_FORMAT` and the camera buffer can be handed to the encoder as a plain bulk
copy.

Measured cost of getting this wrong, four 1280×960 streams encoding simultaneously:

| approach | colours | CPU (of one core, 8 available) |
|---|---|---|
| treat as NV12 | **wrong** | 61 % |
| swap Cb/Cr in Java | correct | **98 %**, with dropped frames |
| tell the encoder `0x7FA30C00` | correct | 61 % |

---

## Channel → direction

The libraries do not name the channels: `vehicle.config.cam_sort` carries only tag→id pairs
(`libbmmcamera_utils.so @ 0011a948 initCameraId` parses `front`, `rear`, `rvs`, `rf`, `pano_h`,
`vc0`..`vc3`), and the stock 360 UI renders the composite through a single
`addPreviewSurface(surface, 0)`. The mapping must therefore be measured per vehicle.

Measured on a Dolphin:

| callback index | composite strip | direction |
|---|---|---|
| 1 | 0 | rear |
| 2 | 1 | left |
| 3 | 2 | right |
| 4 | 3 | front |

### How to verify it on your own car

Use the **camera mount**, not the scene — it is invariant to where the car is parked:

- A **centre-mounted** camera (front in the grille, rear in the tailgate) shows the bumper as a
  **symmetric arc spanning the whole bottom edge** of the frame.
- A **mirror-mounted** camera (left, right) looks down the car's flank, so the bodywork **fills one
  side of the frame and curves away**.

That splits the four channels into {front, rear} and {left, right} immediately. Separate each pair
by driving forward a few metres and checking optical flow, or simply by what the view faces.

> Earlier revisions of this page's source material tried to separate front from right by how
> "straight" the body/ground boundary looked. **That does not work** — both are body creases, and
> how straight one looks depends on where the car is parked. It produced a front/right swap that
> went unnoticed for some time.

---

## Hardware encode

Both `OMX.qcom.video.encoder.hevc` and `OMX.qcom.video.encoder.avc` configure and run from a
uid-2000 `app_process`, at 1280×960, fed directly from the camera callback buffer.

### Colour metadata

The encoder writes `colour_primaries = bt470bg` (BT.601 625-line) but leaves
`matrix_coefficients` **unspecified**, so players guess the YUV→RGB matrix and pick BT.709 at this
resolution. These are BT.601 sensors. Set the colour aspects explicitly and the Qualcomm encoder
honours them, emitting `color_space = smpte170m`:

```java
format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_PAL);
format.setInteger(MediaFormat.KEY_COLOR_RANGE,    MediaFormat.COLOR_RANGE_LIMITED);
format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
```

The visible impact is small at night and matters in daylight, on saturated colour.

### Keyframe interval

`KEY_I_FRAME_INTERVAL` is worth tuning. Measured at 1280×960 / 11 fps / 1.2 Mbps VBR:

| GOP | keyframe share of total bitrate | mean P-frame |
|---|---|---|
| 1 s | **48.4 %** | 7 856 B |
| 2 s | 27.0 % | 10 667 B (**+36 %**) |

An IDR costs ~8.4× an average P-frame here, so a 1-second GOP spends roughly half the entire
bitrate on keyframes.

### Sensor characteristics

These are parking-assist cameras: small optics, no IR illuminator, aggressive noise reduction. In
daylight they are saturated and sharp; at night they fall to roughly half the chroma saturation, and
no encoder setting recovers it. Budget accordingly if you plan to use them for night surveillance.

---

## BYD Camera Apps

Three system camera packages:

| Package | APK Path | Purpose |
|---|---|---|
| `com.byd.bydcamera` | `/system/app/BydCamera/` | Main camera UI (360 view) |
| `com.byd.cameramanager` | `/system/app/BydCameraManager/` | Camera service manager |
| `com.byd.auto_camera` | `/system/app/BydAutoCamera/` | Auto camera (parking/reverse) |

All run as `userId=1000` (system).

## Access summary

| Path | Root needed | Works |
|---|---|---|
| `JNIBMMCamera` from a `shell`-uid `app_process` | **no** | ✅ live frames, all 4 channels |
| `AVMCamera.open()` from a `shell`-uid process | no | ❌ `null` — `cam_sort` unreadable |
| `bmmcamera.jar` from an installed app (`untrusted_app_*`) | no | ❌ SELinux denies the binder lookup |
| `BYDAutoPanoramaDevice` | no | ❌ server-side permission check |
| `android.hardware.Camera` (standard API) | no | ❌ reports 0 cameras |

## Tested On

- BYD Dolphin 2024/2025
- DiLink 3.0, Android 10 (API 29)
- Firmware 13.1.32.2507250.1
