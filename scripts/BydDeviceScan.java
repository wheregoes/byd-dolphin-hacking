import java.lang.reflect.Method;

/**
 * Scans BYDAutoManager device types and feature IDs to discover readable signals.
 * Uses app_process to run as system UID, bypassing permission checks.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydscan scripts/BydDeviceScan.java
 *   d8 --output /tmp/bydscan /tmp/bydscan/BydDeviceScan.class
 *   adb push /tmp/bydscan/classes.dex /data/local/tmp/bydscan.dex
 *   adb shell "CLASSPATH=/data/local/tmp/bydscan.dex app_process /data/local/tmp BydDeviceScan"
 */
public class BydDeviceScan {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            Object mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (mgr == null) { System.out.println("ERROR: null mgr"); return; }

            Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            Method getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);

            if (args.length > 0 && args[0].equals("bodywork")) {
                scanBodywork(getInt, mgr);
                return;
            }
            if (args.length > 0 && args[0].equals("doorlock")) {
                scanDoorLock(getInt, mgr);
                return;
            }
            if (args.length > 0 && args[0].equals("ota")) {
                scanOta(getInt, getBuffer, mgr);
                return;
            }
            if (args.length > 0 && args[0].equals("test")) {
                scanTestSignals(getInt, mgr);
                return;
            }

            System.out.println("Usage: BydDeviceScan [bodywork|doorlock|ota|test]");
            System.out.println("  bodywork - scan bodywork/lock signals (dev=1001)");
            System.out.println("  doorlock - scan door lock signals (dev=1041)");
            System.out.println("  ota      - scan OTA/DSP signals (dev=1032)");
            System.out.println("  test     - scan test/diagnostic signals (dev=1022)");
            System.out.println("\nRunning all scans...\n");

            scanBodywork(getInt, mgr);
            System.out.println();
            scanDoorLock(getInt, mgr);
            System.out.println();
            scanOta(getInt, getBuffer, mgr);
            System.out.println();
            scanTestSignals(getInt, mgr);

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void scanBodywork(Method getInt, Object mgr) {
        int dev = 1001; // bodywork
        System.out.println("=== Bodywork Device (1001) ===");
        p(getInt, mgr, dev, 0x18000009, "REMOTE_CONTROL_LOCK");
        p(getInt, mgr, dev, 0x18000008, "REMOTE_CONTROL_UNLOCK");
        p(getInt, mgr, dev, 0x38400032, "WHOLE_VEHICLE_LOCK_TYPE");
        p(getInt, mgr, dev, 0x1D30401A, "FOUR_DOOR_LOCK_EXECUTE_ATOM");
        p(getInt, mgr, dev, 0x0780A038, "ATOM_DOOR_LOCK_SET");
        p(getInt, mgr, dev, 0x4070000A, "RF_DOOR_LOCK_STATUS");
        p(getInt, mgr, dev, 0x4070000C, "LR_DOOR_LOCK_STATUS");
        p(getInt, mgr, dev, 0x4070000E, "RR_DOOR_LOCK_STATUS");
        p(getInt, mgr, dev, 0x3E805000, "BT_UNLOCK");
        p(getInt, mgr, dev, 0x1D304022, "UNLOCK_WELCOME_EXECUTE_ATOM");
        // Auto system state
        p(getInt, mgr, dev, 80037, "AUTO_SYSTEM_STATE (fwk ID)");
        // Door lock prompt
        p(getInt, mgr, dev, 0x2D000102, "DOOR_LOCK_PROMPT_STATUS");
    }

    static void scanDoorLock(Method getInt, Object mgr) {
        int dev = 1041; // door lock
        System.out.println("=== Door Lock Device (1041) ===");
        p(getInt, mgr, dev, 960495668, "LF_DOOR_LOCK (fwk)");
        p(getInt, mgr, dev, 960495670, "RF_DOOR_LOCK (fwk)");
        p(getInt, mgr, dev, 960495672, "LR_DOOR_LOCK (fwk)");
        p(getInt, mgr, dev, 960495674, "RR_DOOR_LOCK (fwk)");
        p(getInt, mgr, dev, 960495676, "BACK_LOCK (fwk)");
    }

    static void scanOta(Method getInt, Method getBuffer, Object mgr) {
        int dev = 1032; // OTA
        System.out.println("=== OTA Device (1032) ===");
        p(getInt, mgr, dev, 0x99000223, "OTA_DSP_SOUND_SOURCE_PACKAGE");
        p(getInt, mgr, dev, 0x9900003D, "OTA_CMD_MCU_DATA");
        p(getInt, mgr, dev, 0x99000141, "OTA_MULTI_FRAME_ACK");
        // Try reading MCU version as buffer
        pb(getBuffer, mgr, dev, 157571, "ECU_VERSION (fwk)");
        pb(getBuffer, mgr, dev, 256763, "ECU_SOFTCODE (fwk)");
    }

    static void scanTestSignals(Method getInt, Object mgr) {
        int dev = 1002; // test signals go through audio device
        System.out.println("=== Test/Diagnostic Signals (via audio dev 1002) ===");
        p(getInt, mgr, dev, 0xAA000151, "TEST_FLASH_MUSIC_VAL");
        p(getInt, mgr, dev, 0xAA000113, "TEST_DSP_STANDBY_STATE");
        p(getInt, mgr, dev, 0xAA000247, "TEST_MCU_REPORT_DSP_VERSION");
        p(getInt, mgr, dev, 0x12020005, "CAR_CONFIG_ITEM_AVAS_AUDIO");
        p(getInt, mgr, dev, 0x99000364, "DSP_READY");
        p(getInt, mgr, dev, 0x99000214, "AMPLIFIER_TYPE");
        // Prompt volume
        p(getInt, mgr, dev, 0x99000307, "PROMPT_VOLUME_LEVEL_STATUS");
        p(getInt, mgr, dev, 0x99000162, "AVAS_SOURCE_TYPE");
    }

    static void p(Method m, Object mgr, int dev, int fid, String name) {
        try {
            int v = (int) m.invoke(mgr, dev, fid);
            System.out.println(name + " [dev=" + dev + " fid=0x" + Integer.toHexString(fid) + "] = " + v);
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            System.out.println(name + " [dev=" + dev + " fid=0x" + Integer.toHexString(fid) + "] = ERR: " + cause);
        }
    }

    static void pb(Method m, Object mgr, int dev, int fid, String name) {
        try {
            byte[] buf = (byte[]) m.invoke(mgr, dev, fid);
            if (buf == null) {
                System.out.println(name + " [dev=" + dev + " fid=0x" + Integer.toHexString(fid) + "] = null");
                return;
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : buf) hex.append(String.format("%02x", b));
            System.out.println(name + " [dev=" + dev + " fid=0x" + Integer.toHexString(fid) + "] = hex:" + hex + " len=" + buf.length);
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            System.out.println(name + " [dev=" + dev + " fid=0x" + Integer.toHexString(fid) + "] = ERR: " + cause);
        }
    }
}
