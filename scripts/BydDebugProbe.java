import java.lang.reflect.Method;

/**
 * Targeted probe of debug commands and alternative API methods.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/byddbg scripts/BydDebugProbe.java
 *   d8 --output /tmp/byddbg /tmp/byddbg/BydDebugProbe.class
 *   adb push /tmp/byddbg/classes.dex /data/local/tmp/byddbg.dex
 *   adb shell "CLASSPATH=/data/local/tmp/byddbg.dex app_process /data/local/tmp BydDebugProbe <command>"
 *
 * Commands:
 *   debug_scan     - scan 0x6E99xxxx debug write range
 *   debug_avah     - test 0x6E990010 with values 0-10 (listen for sounds)
 *   double_avah    - test setDouble on AVAH (frequency specification?)
 *   intarray       - test setIntArray for batch commands
 *   avas_commands  - test AVAS-related 0xAA000xxx commands systematically
 *   ota_probe      - probe OTA pipeline entry points
 */
public class BydDebugProbe {
    static Object mgr;
    static Method setInt, getInt, setDouble, getDouble;
    static Method setBuffer, getBuffer;
    static Method setIntArray, getIntArray;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Commands: debug_scan, debug_avah, double_avah, intarray, avas_commands, ota_probe");
            return;
        }
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");

            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            setDouble = mgr.getClass().getMethod("setDouble", int.class, int.class, double.class);
            getDouble = mgr.getClass().getMethod("getDouble", int.class, int.class);
            setBuffer = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);
            getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);
            setIntArray = mgr.getClass().getMethod("setIntArray", int.class, int[].class, int[].class);
            getIntArray = mgr.getClass().getMethod("getIntArray", int.class, int[].class);

            switch (args[0]) {
                case "debug_scan": debugScan(); break;
                case "debug_avah": debugAvah(); break;
                case "double_avah": doubleAvah(); break;
                case "intarray": intArrayTest(); break;
                case "avas_commands": avasCommands(); break;
                case "ota_probe": otaProbe(); break;
                default: System.out.println("Unknown: " + args[0]);
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static int doSet(int dev, int fid, int val) throws Exception {
        return (int) setInt.invoke(mgr, dev, fid, val);
    }

    static int doGet(int dev, int fid) throws Exception {
        return (int) getInt.invoke(mgr, dev, fid);
    }

    static int doSetDouble(int dev, int fid, double val) throws Exception {
        return (int) setDouble.invoke(mgr, dev, fid, val);
    }

    static int doSetBuf(int dev, int fid, byte[] data) throws Exception {
        return (int) setBuffer.invoke(mgr, dev, fid, data);
    }

    static String resultStr(int r) {
        if (r == 0) return "SUCCESS";
        if (r == -2147482648) return "MCU_FAILED";
        if (r == -10011) return "NOT_REGISTERED";
        return String.valueOf(r);
    }

    static void debugScan() throws Exception {
        System.out.println("=== Scanning 0x6E99xxxx debug write range ===\n");

        int found = 0;
        for (int sub = 0; sub <= 0xFF; sub++) {
            int fid = 0x6E990000 | sub;
            int r = doSet(1002, fid, 1);
            if (r == 0) {
                System.out.println("  ACCEPTED: 0x" + Integer.toHexString(fid));
                doSet(1002, fid, 0); // stop
                found++;
            }
        }
        System.out.println("\nFound: " + found + " writable in 0x6E99xxxx");

        // Also scan 0x6E9A-0x6E9F
        System.out.println("\n--- Extended: 0x6E9A-0x6E9F x 0010 ---");
        for (int prefix = 0x6E9A; prefix <= 0x6E9F; prefix++) {
            int fid = (prefix << 16) | 0x0010;
            int r = doSet(1002, fid, 1);
            if (r == 0) {
                System.out.println("  ACCEPTED: 0x" + Integer.toHexString(fid));
                doSet(1002, fid, 0);
            }
        }
    }

    static void debugAvah() throws Exception {
        System.out.println("=== Testing 0x6E990010 (debug AVAH) values ===");
        System.out.println("Listen OUTSIDE the car for each value.\n");

        int fid = 0x6E990010;
        for (int val = 0; val <= 10; val++) {
            int r = doSet(1002, fid, val);
            System.out.println("  val=" + val + " -> " + resultStr(r));
            if (val > 0 && r == 0) {
                Thread.sleep(3000); // listen for 3 seconds
                doSet(1002, fid, 0); // stop
                Thread.sleep(500);
            }
        }

        // Also try higher values
        System.out.println("\n--- Higher values ---");
        int[] highVals = {16, 32, 64, 100, 128, 200, 255};
        for (int val : highVals) {
            int r = doSet(1002, fid, val);
            System.out.println("  val=" + val + " -> " + resultStr(r));
            if (r == 0) {
                Thread.sleep(2000);
                doSet(1002, fid, 0);
                Thread.sleep(500);
            }
        }

        // Compare with regular AVAH
        System.out.println("\n--- Regular AVAH (0x6E970010) for comparison ---");
        for (int val = 1; val <= 3; val++) {
            int r = doSet(1002, 0x6E970010, val);
            System.out.println("  val=" + val + " -> " + resultStr(r));
            Thread.sleep(2000);
            doSet(1002, 0x6E970010, 0);
            Thread.sleep(500);
        }

        doSet(1002, fid, 0);
        doSet(1002, 0x6E970010, 0);
        System.out.println("\nDone. All tones stopped.");
    }

    static void doubleAvah() throws Exception {
        System.out.println("=== Testing setDouble on AVAH ===");
        System.out.println("Theory: MCU may interpret double as frequency (Hz).\n");

        int fid = 0x6E970010;
        double[] freqs = {440.0, 880.0, 1000.0, 1500.0, 2000.0, 3000.0, 4000.0};

        for (double freq : freqs) {
            int r = doSetDouble(1002, fid, freq);
            System.out.println("  setDouble(" + freq + " Hz) -> " + resultStr(r));
            if (r == 0) {
                Thread.sleep(2000);
            }
        }

        // Also try on debug AVAH
        System.out.println("\n--- setDouble on 0x6E990010 ---");
        for (double freq : freqs) {
            int r = doSetDouble(1002, 0x6E990010, freq);
            System.out.println("  setDouble(" + freq + " Hz) -> " + resultStr(r));
            if (r == 0) {
                Thread.sleep(2000);
            }
        }

        // Try small values (as if selecting preset)
        System.out.println("\n--- setDouble with small values ---");
        double[] smalls = {1.0, 2.0, 3.0, 4.0, 5.0, 0.0};
        for (double v : smalls) {
            int r = doSetDouble(1002, fid, v);
            System.out.println("  setDouble(" + v + ") -> " + resultStr(r));
            if (r == 0 && v > 0) {
                Thread.sleep(2000);
                doSetDouble(1002, fid, 0.0);
                Thread.sleep(300);
            }
        }

        doSet(1002, fid, 0);
        doSet(1002, 0x6E990010, 0);
        System.out.println("\nDone.");
    }

    static void intArrayTest() throws Exception {
        System.out.println("=== Testing setIntArray ===");
        System.out.println("Theory: batch multiple commands in one SPI frame.\n");

        // Try sending AVAH + routing in a single batch
        System.out.println("--- Batch: AVAH=1 + AVAS_TO_EXT=1 + LOOPBACK=1 ---");
        int[] fids = {0x6E970010, 0x32B1C042, 0xAA000301};
        int[] vals = {1, 1, 1};
        try {
            int[] results = (int[]) setIntArray.invoke(mgr, 1002, fids, vals);
            for (int i = 0; i < fids.length; i++) {
                System.out.println("  0x" + Integer.toHexString(fids[i]) + " = " +
                                   resultStr(results != null && i < results.length ? results[i] : -999));
            }
        } catch (Exception e) {
            System.out.println("  setIntArray failed: " + e.getCause());
        }

        Thread.sleep(2000);

        // Batch: enable debug mode + AVAH + routing
        System.out.println("\n--- Batch: debug_mode=1 + AVAH=1 + routing=1 ---");
        int[] fids2 = {0x6E990008, 0x6E970010, 0x32B1C042, 0x1C10000E};
        int[] vals2 = {1, 1, 1, 1};
        try {
            int[] results = (int[]) setIntArray.invoke(mgr, 1002, fids2, vals2);
            for (int i = 0; i < fids2.length; i++) {
                System.out.println("  0x" + Integer.toHexString(fids2[i]) + " = " +
                                   resultStr(results != null && i < results.length ? results[i] : -999));
            }
        } catch (Exception e) {
            System.out.println("  setIntArray failed: " + e.getCause());
        }

        Thread.sleep(3000);

        // Stop everything
        doSet(1002, 0x6E970010, 0);
        doSet(1002, 0x6E990010, 0);
        System.out.println("\nDone.");
    }

    static void avasCommands() throws Exception {
        System.out.println("=== Testing AVAS-related 0xAA000xxx commands ===");
        System.out.println("Reading state before and after each command.\n");

        // First read AVAS-related state
        System.out.println("--- Baseline state ---");
        int[][] readSignals = {
            {0x99000162, 1002}, // AVAS source type
            {0x4C60002D, 1002}, // AVAS volume
            {0x4FD00030, 1002}, // External speaker status
            {0x4FD00040, 1002}, // External amplifier status
            {0x35201040, 1002}, // Audio routing status
            {0x35203032, 1002}, // Audio channel status
        };
        for (int[] sig : readSignals) {
            int val = doGet(sig[1], sig[0]);
            System.out.println("  0x" + Integer.toHexString(sig[0]) + " = " + val);
        }

        // AVAS-related write commands from the 63 accepted list
        System.out.println("\n--- AVAS-adjacent test commands ---");
        int[][] testCmds = {
            {0xAA000104, 1},  // TEST_AUDIO_AVAS_SET
            {0xAA000104, 2},
            {0xAA000104, 3},
            {0xAA000171, 1},  // TEST_MCU_AVAS_CONFIGURATION_SET
            {0xAA000171, 2},
            {0xAA000171, 3},
            {0xAA000170, 1},  // nearby AVAS config
            {0xAA000170, 2},
            {0xAA000151, 1},  // TEST_FLASH_MUSIC_VAL_SET
            {0xAA000151, 2},
            {0xAA000151, 3},
            {0xAA000153, 1},  // unknown, accepted in scan
            {0xAA000153, 2},
        };

        int avahFid = 0x6E970010;
        for (int[] cmd : testCmds) {
            // Set the command
            int r = doSet(1002, cmd[0], cmd[1]);
            System.out.println("  SET 0x" + Integer.toHexString(cmd[0]) + "=" + cmd[1] +
                               " -> " + resultStr(r));

            if (r == 0) {
                // Check if AVAS state changed
                int avasType = doGet(1002, 0x99000162);
                int avahState = doGet(1002, 0x6EA70010);
                if (avasType != -10011 || avahState != 65535) {
                    System.out.println("    AVAS_TYPE=" + avasType + " AVAH_STATE=" + avahState);
                }
                Thread.sleep(500);
            }
        }

        // Now try the DSP-related commands
        System.out.println("\n--- DSP/amplifier control ---");
        int[][] dspCmds = {
            {0xAA000145, 1},  // SOC_CONTROL_DSP = take control
            {0xAA000145, 2},
            {0xAA000145, 3},
            {0xAA000113, 1},  // TEST_DSP_STANDBY_STATE_SET
            {0xAA000113, 0},
            {0xAA000112, 1},  // nearby DSP command
            {0xAA000114, 1},  // nearby DSP command
        };

        for (int[] cmd : dspCmds) {
            int r = doSet(1002, cmd[0], cmd[1]);
            System.out.println("  SET 0x" + Integer.toHexString(cmd[0]) + "=" + cmd[1] +
                               " -> " + resultStr(r));
            Thread.sleep(200);
        }

        // Read state after DSP changes
        System.out.println("\n--- State after DSP changes ---");
        for (int[] sig : readSignals) {
            int val = doGet(sig[1], sig[0]);
            System.out.println("  0x" + Integer.toHexString(sig[0]) + " = " + val);
        }

        // Test sound source switching commands
        System.out.println("\n--- Sound source commands ---");
        int[][] srcCmds = {
            {0xAA000194, 1},  // Vehicle Prompt Sound Source (Normal)
            {0xAA000194, 2},  // Vehicle Prompt Sound Source (Tech)
            {0xAA000194, 3},  // Unknown
            {0xAA000161, 1},  // AVAS-adjacent
            {0xAA000165, 1},
            {0xAA000166, 1},
            {0xAA000167, 1},
            {0xAA000168, 1},
            {0xAA000169, 1},
        };

        for (int[] cmd : srcCmds) {
            int r = doSet(1002, cmd[0], cmd[1]);
            System.out.println("  SET 0x" + Integer.toHexString(cmd[0]) + "=" + cmd[1] +
                               " -> " + resultStr(r));
            Thread.sleep(200);
        }

        // Reset
        doSet(1002, 0xAA000194, 1); // back to Normal
        System.out.println("\nDone.");
    }

    static void otaProbe() throws Exception {
        System.out.println("=== Probing OTA Pipeline Entry Points ===\n");

        // Try to access BYDAutoOtaDevice
        System.out.println("--- Checking OTA device access ---");
        try {
            // enableDevice for OTA device type 1032
            Method enableDev = mgr.getClass().getMethod("enableDevice", int.class);
            int r = (int) enableDev.invoke(mgr, 1032);
            System.out.println("enableDevice(1032) = " + resultStr(r));
        } catch (Exception e) {
            System.out.println("enableDevice(1032) failed: " + e.getCause());
        }

        // Read OTA-related signals
        System.out.println("\n--- OTA status signals ---");
        int[][] otaSignals = {
            {0x99000223, 1032}, // DSP sound source package
            {0x99000141, 1032}, // OTA_MULTI_FRAME_ACK
            {0x99000144, 1032}, // OTA status
            {0x99000140, 1032}, // OTA multi-frame
        };
        for (int[] sig : otaSignals) {
            int val = doGet(sig[1], sig[0]);
            byte[] buf = (byte[]) getBuffer.invoke(mgr, sig[1], sig[0]);
            String bufHex = "null";
            if (buf != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(buf.length, 32); i++)
                    sb.append(String.format("%02x", buf[i] & 0xFF));
                bufHex = sb.toString() + " (len=" + buf.length + ")";
            }
            System.out.println("  0x" + Integer.toHexString(sig[0]) + " int=" + val + " buf=" + bufHex);
        }

        // Try OTA write signals
        System.out.println("\n--- OTA write commands ---");
        int[][] otaWrites = {
            {0xAA000140, 1032}, // OTA_MULTI_FRAME_SET
            {0xAA000206, 1032}, // nearby OTA
            {0xAA000221, 1032}, // nearby OTA
            {0xAA000241, 1032}, // nearby OTA
            {0xAA000244, 1032}, // nearby OTA
        };
        for (int[] cmd : otaWrites) {
            int r = doSet(cmd[1], cmd[0], 0); // query-like: value 0
            System.out.println("  SET 0x" + Integer.toHexString(cmd[0]) + " dev=" + cmd[1] +
                               " val=0 -> " + resultStr(r));
        }

        // Try setBuffer on OTA featureId (small probe, not actual OTA data)
        System.out.println("\n--- setBuffer on OTA featureIds ---");
        byte[] probe = {0x00, 0x00, 0x00, 0x01}; // minimal 4-byte probe
        int[][] bufFids = {
            {0xAA000140, 1032},
            {0xAA000223, 1002},
            {0xAA000223, 1032},
        };
        for (int[] cmd : bufFids) {
            int r = doSetBuf(cmd[1], cmd[0], probe);
            System.out.println("  setBuffer 0x" + Integer.toHexString(cmd[0]) + " dev=" + cmd[1] +
                               " -> " + resultStr(r));
        }

        System.out.println("\nDone.");
    }
}
