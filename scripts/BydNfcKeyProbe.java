import java.lang.reflect.Method;

public class BydNfcKeyProbe {
    static Object mgr;
    static Method getInt;
    static Method getBuffer;
    static Method setInt;

    static final int[] DEVICE_IDS = {1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010,
                                      1011, 1012, 1013, 1014, 1015, 1020, 1030, 1032, 1035};

    static final String[][] NFC_SIGNALS = {
        {"0x43600028", "NFC_FEATURE_SUPPORT (1=yes)"},
        {"0x2EF0002C", "NFC_SWITCH_STATUS (1=on, 2=off)"},
        {"0x2EF0002A", "NFC_SWITCH_FLAG"},
        {"0x38400046", "PHYS_KEY_OPCODE_SWITCH (1=on)"},
        {"0x2F4001C4", "NFC_SUPPORT_CHECK_2 (1=yes)"},
        {"0x3DD00028", "PALM_SUPPORT (1=yes)"},
        {"0x40334013", "NFC_KEY_STATE (1=active)"},
        {"0x4036B030", "NFC_EXECUTE_RESULT"},
        {"0x4036B020", "NFC_UNLOCK_SOURCE"},
        {"0x43F04028", "PHYS_KEY_OPCODE_SET"},
        {"0x18000008", "SMART_ENTRY_WAKEUP"},
        {"0x18C36010", "NFC_UNLOCK_START_TIME"},
        {"0x18C36013", "CHECK_APPLE_KEY"},
        {"0x18C36015", "REGISTER_APPLE_KEY"},
        {"0x4360000A", "APPLE_NFC_CONFIG"},
        {"0x99000168", "NFC_KEY_SERIAL_NO"},
        {"0x99000155", "YUN_NFC_KEY_DATA"},
        {"0x99000159", "YUN_NFC_SHORT_REPLY"},
        {"0x1A6FE018", "OPCODE_CHECKSUM"},
        {"0x1A6FE020", "OPCODE_COMMAND"},
        {"0x1A6FE028", "OPCODE_RESULT"},
        {"0x3E805010", "BT_UNLOCK_LISTENER"},
        {"0x0950000F", "KEY_DIALOG_1"},
        {"0x09500022", "KEY_DIALOG_2"},
        {"0x99000037", "SCREEN_ONOFF_IE"},
        {"0x19707016", "FORGOT_PWD_1"},
        {"0x19707018", "FORGOT_PWD_2"},
        {"0x1970701A", "REPORT_ERROR"},
    };

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (mgr == null) { System.out.println("ERROR: null BYDAutoManager"); return; }
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
        } catch (Exception e) {
            System.out.println("INIT ERROR: " + e);
            return;
        }

        System.out.println("=== BYD NFC Digital Key Probe ===");
        printUid();
        System.out.println();

        if (args.length > 0 && args[0].equals("scan")) {
            scanDevices();
            return;
        }

        if (args.length > 0 && args[0].equals("get")) {
            int dev = args.length > 2 ? Integer.parseInt(args[2]) : 1007;
            int fid = parseId(args[1]);
            try {
                int val = (int) getInt.invoke(mgr, dev, fid);
                System.out.println("GET dev=" + dev + " fid=0x" + Integer.toHexString(fid) + " val=" + val);
            } catch (Exception e) { System.out.println("ERROR: " + unwrap(e)); }
            return;
        }

        if (args.length > 0 && args[0].equals("set")) {
            int dev = args.length > 3 ? Integer.parseInt(args[3]) : 1007;
            int fid = parseId(args[1]);
            int val = Integer.parseInt(args[2]);
            try {
                int r = (int) setInt.invoke(mgr, dev, fid, val);
                System.out.println("SET dev=" + dev + " fid=0x" + Integer.toHexString(fid) + " val=" + val + " result=" + r);
            } catch (Exception e) { System.out.println("ERROR: " + unwrap(e)); }
            return;
        }

        System.out.println("--- Scanning all NFC signals across device IDs ---");
        System.out.println("(Testing each signal on devices 1002-1015, 1020, 1030, 1032, 1035)");
        System.out.println();

        for (String[] sig : NFC_SIGNALS) {
            int fid = parseId(sig[0]);
            String name = sig[1];
            boolean found = false;

            for (int dev : DEVICE_IDS) {
                try {
                    int val = (int) getInt.invoke(mgr, dev, fid);
                    System.out.println("  " + sig[0] + " " + name + " = " + val + "  (dev=" + dev + ")");
                    found = true;
                    break;
                } catch (Exception e) {
                    // try next device
                }
            }
            if (!found) {
                System.out.println("  " + sig[0] + " " + name + " = NOT FOUND on any device");
            }
        }

        System.out.println();
        System.out.println("--- Buffer reads ---");
        for (int dev : DEVICE_IDS) {
            try {
                int fid = parseId("0x1A6FE030");
                byte[] buf = (byte[]) getBuffer.invoke(mgr, dev, fid);
                if (buf != null && buf.length > 0) {
                    System.out.print("  0x1A6FE030 OPCODE_RANDOM_DATA = ");
                    for (byte b : buf) System.out.printf("%02X", b & 0xFF);
                    System.out.println(" (dev=" + dev + ", " + buf.length + " bytes)");
                    break;
                }
            } catch (Exception e) {}
        }

        System.out.println();
        System.out.println("--- Platform info ---");
        checkPlatform();

        System.out.println();
        System.out.println("--- NFC Android service ---");
        checkNfcService();

        System.out.println();
        System.out.println("=== Probe Complete ===");
    }

    static void scanDevices() {
        System.out.println("--- Scanning device IDs for NFC_FEATURE_SUPPORT (0x43600028) ---");
        int fid = parseId("0x43600028");
        for (int dev : DEVICE_IDS) {
            try {
                int val = (int) getInt.invoke(mgr, dev, fid);
                System.out.println("  dev=" + dev + " val=" + val + " OK");
            } catch (Exception e) {
                System.out.println("  dev=" + dev + " " + unwrap(e));
            }
        }

        System.out.println();
        System.out.println("--- Scanning device IDs for NFC_SWITCH_STATUS (0x2EF0002C) ---");
        fid = parseId("0x2EF0002C");
        for (int dev : DEVICE_IDS) {
            try {
                int val = (int) getInt.invoke(mgr, dev, fid);
                System.out.println("  dev=" + dev + " val=" + val + " OK");
            } catch (Exception e) {
                System.out.println("  dev=" + dev + " " + unwrap(e));
            }
        }
    }

    static void printUid() {
        try {
            Class<?> processClass = Class.forName("android.os.Process");
            int uid = (int) processClass.getMethod("myUid").invoke(null);
            int pid = (int) processClass.getMethod("myPid").invoke(null);
            System.out.println("UID: " + uid + " PID: " + pid);
        } catch (Exception e) {}
    }

    static void checkPlatform() {
        try {
            Class<?> cabinClass = Class.forName("com.byd.car.ICarCabinManager");
            Class<?> dicarClass = Class.forName("com.byd.car.DiCar");
            Object dicar = dicarClass.getMethod("getInstance").invoke(null);
            Object cabinMgr = dicarClass.getMethod("getCarCabinManager").invoke(dicar);
            int pi = (int) cabinClass.getMethod("getPlatformInfo").invoke(cabinMgr);
            System.out.println("  platformInfo = " + pi + " (0x" + Integer.toHexString(pi) + ")");
            System.out.println("  DI3.0 (bit0): " + ((pi & 1) != 0));
            System.out.println("  DI6.0 (bit4): " + ((pi & 16) != 0));
        } catch (Exception e) {
            System.out.println("  ERROR: " + unwrap(e));
        }
    }

    static void checkNfcService() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Object nfcBinder = smClass.getMethod("getService", String.class).invoke(null, "nfc");
            System.out.println("  nfc service: " + (nfcBinder != null ? "RUNNING" : "NOT RUNNING"));

            String[] all = (String[]) smClass.getMethod("listServices").invoke(null);
            for (String s : all) {
                String lower = s.toLowerCase();
                if (lower.contains("nfc") || lower.contains("secure_element") ||
                    lower.contains("smartcard") || lower.contains("intelligent")) {
                    System.out.println("  found: " + s);
                }
            }
        } catch (Exception e) {
            System.out.println("  ERROR: " + unwrap(e));
        }

        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            String[] props = {"ro.nfc.port", "ro.hardware.nfc_nci", "persist.nfc.debug_enabled",
                              "nfc.initialized", "ro.boot.hardware.nfc"};
            for (String p : props) {
                String v = (String) get.invoke(null, p, "");
                if (!v.isEmpty()) System.out.println("  " + p + " = " + v);
            }
        } catch (Exception e) {}
    }

    static int parseId(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return (int) Long.parseLong(s.substring(2), 16);
        }
        return Integer.parseInt(s);
    }

    static String unwrap(Exception e) {
        Throwable c = e.getCause();
        if (c != null) return c.getClass().getSimpleName() + ": " + c.getMessage();
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
