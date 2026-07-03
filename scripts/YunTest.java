import java.lang.reflect.Method;

/**
 * Tests BYDAUTO_DEVICE_YUN (1034) and BYDAUTO_DEVICE_POWER (1005)
 * discovered from cloudmanager binary reverse engineering.
 *
 * cloudmanager uses: setBuffer(1034, 0xAA000005, aesEncrypted, len)
 * cloudmanager uses: setInt(1005, fid, val)
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/yun scripts/YunTest.java
 *   d8 --output /tmp/yun /tmp/yun/YunTest.class
 *   adb push /tmp/yun/classes.dex /data/local/tmp/yun.dex
 *   adb shell "CLASSPATH=/data/local/tmp/yun.dex app_process / YunTest [args]"
 *
 * Usage:
 *   YunTest          — probe YUN device (read states)
 *   YunTest read     — same
 *   YunTest send <hex> — send raw bytes via setBuffer(1034, 0xAA000005, data, len)
 *   YunTest setint <fid> <val> [dev] — setInt on any device
 *   YunTest getint <fid> [dev] — getInt on any device
 *   YunTest sweep    — try various feature IDs on YUN device
 */
public class YunTest {
    static final int DEV_YUN = 1034;
    static final int DEV_POWER = 1005;

    static Method getInt, setInt, setBuffer, getBuffer;
    static Object _mgr;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            _mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (_mgr == null) { System.out.println("ERROR: null mgr"); return; }
            getInt = _mgr.getClass().getMethod("getInt", int.class, int.class);
            setInt = _mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            setBuffer = _mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);
            getBuffer = _mgr.getClass().getMethod("getBuffer", int.class, int.class);

            String cmd = args.length > 0 ? args[0] : "read";

            switch (cmd) {
                case "read": doRead(); break;
                case "send": doSend(args); break;
                case "setint": doSetInt(args); break;
                case "getint": doGetInt(args); break;
                case "sweep": doSweep(); break;
                default:
                    System.out.println("Usage: YunTest [read|send|setint|getint|sweep]");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void doRead() {
        System.out.println("=== YUN Device (1034) Reads ===");
        p(DEV_YUN, 0xAA000005, "YUN_AA000005");
        p(DEV_YUN, 0x99000155, "YUN_99000155 (MCU response)");
        p(DEV_YUN, 0x99000004, "YUN_99000004 (mcu_data_ind)");
        p(DEV_YUN, 0xAA00020F, "YUN_AA00020F (TEST_SIMULATE_DOWN)");
        p(DEV_YUN, 0xAA000104, "YUN_AA000104 (AVAS_SET)");
        p(DEV_YUN, 0xAA000140, "YUN_AA000140 (OTA_SET)");
        p(DEV_YUN, 0x99000214, "YUN_99000214 (532 response?)");

        System.out.println("\n=== POWER Device (1005) Reads ===");
        p(DEV_POWER, 0xAA000005, "POWER_AA000005");
        p(DEV_POWER, 0x99000155, "POWER_99000155");

        System.out.println("\n=== Try getBuffer on YUN ===");
        pb(DEV_YUN, 0x99000155, "YUN_99000155 buf");
        pb(DEV_YUN, 0x99000214, "YUN_99000214 buf");
    }

    static void doSend(String[] args) {
        if (args.length < 2) { System.out.println("Usage: YunTest send <hexbytes>"); return; }
        byte[] data = hexToBytes(args[1]);
        int fid = args.length > 2 ? parseId(args[2]) : 0xAA000005;
        int dev = args.length > 3 ? Integer.parseInt(args[3]) : DEV_YUN;
        try {
            int r = (int) setBuffer.invoke(_mgr, dev, fid, data);
            System.out.println("setBuffer(dev=" + dev + ", fid=0x" + hex(fid) + ", len=" + data.length + ") => result=" + r + (r == 0 ? " (OK!)" : ""));
        } catch (Exception e) {
            System.out.println("setBuffer ERR: " + cause(e));
        }
    }

    static void doSetInt(String[] args) {
        if (args.length < 3) { System.out.println("Usage: YunTest setint <fid> <val> [dev]"); return; }
        int fid = parseId(args[1]);
        int val = Integer.parseInt(args[2]);
        int dev = args.length > 3 ? Integer.parseInt(args[3]) : DEV_YUN;
        try {
            int r = (int) setInt.invoke(_mgr, dev, fid, val);
            System.out.println("setInt(dev=" + dev + ", fid=0x" + hex(fid) + ", val=" + val + ") => result=" + r + (r == 0 ? " (OK!)" : ""));
        } catch (Exception e) {
            System.out.println("setInt ERR: " + cause(e));
        }
    }

    static void doGetInt(String[] args) {
        if (args.length < 2) { System.out.println("Usage: YunTest getint <fid> [dev]"); return; }
        int fid = parseId(args[1]);
        int dev = args.length > 2 ? Integer.parseInt(args[2]) : DEV_YUN;
        p(dev, fid, "GET");
    }

    static void doSweep() {
        System.out.println("=== Sweep: setInt on YUN (1034) with various FIDs, val=1 ===");
        int[] fids = {
            0xAA000005, 0xAA000104, 0xAA000140, 0xAA00020F,
            0xAA00011A, 0xAA000142, 0xAA000148, 0xAA000171,
            0x00000214, 0x00000005, 0x00000001,
            0x99000155, 0x99000214, 0x99000004,
        };
        for (int fid : fids) {
            try {
                int r = (int) setInt.invoke(_mgr, DEV_YUN, fid, 1);
                String tag = r == 0 ? "ACCEPTED" : (r == -2147482648 ? "FAILED" : "rc=" + r);
                System.out.println("setInt(YUN, 0x" + hex(fid) + ", 1) => " + tag);
            } catch (Exception e) {
                System.out.println("setInt(YUN, 0x" + hex(fid) + ", 1) => ERR: " + cause(e));
            }
            sleep(300);
        }

        System.out.println("\n=== Sweep: setInt on POWER (1005) ===");
        for (int fid : fids) {
            try {
                int r = (int) setInt.invoke(_mgr, DEV_POWER, fid, 1);
                String tag = r == 0 ? "ACCEPTED" : (r == -2147482648 ? "FAILED" : "rc=" + r);
                System.out.println("setInt(POWER, 0x" + hex(fid) + ", 1) => " + tag);
            } catch (Exception e) {
                System.out.println("setInt(POWER, 0x" + hex(fid) + ", 1) => ERR: " + cause(e));
            }
            sleep(300);
        }

        System.out.println("\n=== Sweep: setBuffer on YUN (1034) with 1-byte data ===");
        byte[] one = {0x01};
        for (int fid : fids) {
            try {
                int r = (int) setBuffer.invoke(_mgr, DEV_YUN, fid, one);
                String tag = r == 0 ? "ACCEPTED" : (r == -2147482648 ? "FAILED" : "rc=" + r);
                System.out.println("setBuffer(YUN, 0x" + hex(fid) + ", [01], 1) => " + tag);
            } catch (Exception e) {
                System.out.println("setBuffer(YUN, 0x" + hex(fid) + ") => ERR: " + cause(e));
            }
            sleep(300);
        }
    }

    static void p(int dev, int fid, String name) {
        try {
            int v = (int) getInt.invoke(_mgr, dev, fid);
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + "] = " + v);
        } catch (Exception e) {
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + "] = ERR: " + cause(e));
        }
    }

    static void pb(int dev, int fid, String name) {
        try {
            byte[] buf = (byte[]) getBuffer.invoke(_mgr, dev, fid);
            if (buf == null) { System.out.println(name + " = null"); return; }
            StringBuilder sb = new StringBuilder();
            for (byte b : buf) sb.append(String.format("%02x", b));
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + "] = hex:" + sb + " len=" + buf.length);
        } catch (Exception e) {
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + "] = ERR: " + cause(e));
        }
    }

    static void sleep(int ms) { try { Thread.sleep(ms); } catch (Exception ignored) {} }
    static String hex(int v) { return Integer.toHexString(v); }
    static String cause(Exception e) { return e.getCause() != null ? e.getCause().getMessage() : e.getMessage(); }
    static int parseId(String s) { return (s.startsWith("0x") || s.startsWith("0X")) ? Integer.parseUnsignedInt(s.substring(2), 16) : Integer.parseInt(s); }
    static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("[^0-9a-fA-F]", "");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
        return out;
    }
}
