# Camera System

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
│  Status: BLOCKED (not on boot classpath)                  │
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

- JAR: `/system/framework/bmmcamera.jar`
- NOT on boot classpath — third-party apps cannot load these classes
- Native libs: `/system/lib64/libbmmcamera*.so`

### Camera IDs

Configured via system property `vehicle.config.cam_sort` (not set on Dolphin — uses defaults).

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

### API

```java
// Open camera (returns null if camera not available)
AVMCamera camera = AVMCamera.open(cameraId);

// Add preview surface
camera.addPreviewSurface(surface, viewMode);

// Start/stop preview
camera.startPreview();
camera.stopPreview();

// Frame callback
camera.setPreviewCallback(new AVMCamera.IPreviewCallback() {
    void onPreview(AVMCamera cam, ByteBuffer data, int w, int h, ...) { }
});

// Hardware encoding
camera.setMediaCodec(mediaCodec, format);

// Configuration
camera.setPreviewSize(1280, 960);  // default resolution
camera.setCameraFps(fps);
camera.setDisplayOrientation(surface, orientation);

// Cleanup
camera.close();
```

### View Modes

AVMCamera supports multi-camera view layouts:

| Constant | Value | Description |
|---|---|---|
| `VIEW_CHANNEL_1..4` | | Single camera views |
| `VIEW_1_2_H` | 8 | Two cameras horizontal |
| `VIEW_1_2_V` | 14 | Two cameras vertical |
| `VIEW_DECUSSATION` | | Cross/quad view |
| `VIEW_DEFAULT` | | Default layout |

## BYD Camera Apps

Three system camera packages:

| Package | APK Path | Purpose |
|---|---|---|
| `com.byd.bydcamera` | `/system/app/BydCamera/` | Main camera UI (360 view) |
| `com.byd.cameramanager` | `/system/app/BydCameraManager/` | Camera service manager |
| `com.byd.auto_camera` | `/system/app/BydAutoCamera/` | Auto camera (parking/reverse) |

All run as `userId=1000` (system).

## Third-Party Access Assessment

**Camera access from third-party apps: NOT FEASIBLE**

1. `BYDAutoPanoramaDevice` — server-side permission enforcement, bypass fails
2. `bmmcamera.jar` — not on boot classpath, classes not loadable
3. Native camera service — requires system uid for IPC
4. BYD camera apps run as system user

Possible paths (all require root or system access):
- Root + add `bmmcamera.jar` to boot classpath
- Root + clone system app signature
- Bind to camera AIDL service directly (if service allows non-system callers)
- Use Android standard Camera2 API (may access front/rear but not surround cameras)

## Tested On

- BYD Dolphin 2024/2025
- DiLink 3.0, Android 10 (API 29)
- Firmware 13.1.32.2507250.1
