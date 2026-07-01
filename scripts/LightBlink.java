import java.lang.reflect.Method;

/**
 * Vehicle light control probe — read states + blink/flash commands.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/lb scripts/LightBlink.java
 *   d8 --output /tmp/lb /tmp/lb/LightBlink.class
 *   adb push /tmp/lb/classes.dex /data/local/tmp/light.dex
 *   adb shell "CLASSPATH=/data/local/tmp/light.dex app_process / LightBlink [args]"
 *
 * Usage:
 *   LightBlink              — read all light states (safe)
 *   LightBlink read         — same
 *   LightBlink turn <v>     — turn signal: 1=left 2=right 6=danger 7=emergency 1=off-ish
 *   LightBlink hazard       — hazard/double-flash ON
 *   LightBlink hazardoff    — hazard OFF
 *   LightBlink flash        — flash-to-pass (low beam blink)
 *   LightBlink fog f on     — front fog ON
 *   LightBlink fog r on     — rear fog ON
 *   LightBlink fog f off    — front fog OFF
 *   LightBlink drl on       — DRL ON
 *   LightBlink flashhorn    — combined flash lights + horn (panic)
 *   LightBlink welcome      — welcome light atom (via bodywork 1001)
 *   LightBlink sweep        — try all SET IDs, report MCU accept/reject
 *   LightBlink get <hexid>  — read single feature ID
 *   LightBlink set <hexid> <val> — set single feature ID
 */
public class LightBlink {
    static final int DEV_LIGHT = 1004;
    static final int DEV_BODY = 1001;

    static Method getInt, setInt;
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

            String cmd = args.length > 0 ? args[0] : "read";

            switch (cmd) {
                case "read": doRead(); break;
                case "turn": doSet(DEV_LIGHT, 0x33F0000D, "LIGHT_TURN_SIGNAL_LIGHT_SET", parseIntArg(args, 1, 1)); break;
                case "hazard": doSet(DEV_LIGHT, 0x32B11010, "SECURITY_HIGH_RISK_DOUBLE_FLASH", 1); break;
                case "hazardoff": doSet(DEV_LIGHT, 0x32B11010, "SECURITY_HIGH_RISK_DOUBLE_FLASH", 0); break;
                case "flash": doSet(DEV_LIGHT, 0x3E700028, "LOW_BEAM_BLINK_STATE", 1); break;
                case "fog":
                    handleFog(args); break;
                case "drl": doSet(DEV_LIGHT, 0x43100046, "DRL_AUTO_STATE_SET", "off".equals(args.length > 1 ? args[1] : "") ? 2 : 1); break;
                case "flashhorn": doSet(DEV_LIGHT, 0x3E800008, "FLASHING_LIGHT_AND_HORN", 1); break;
                case "welcome": doSet(DEV_BODY, 0x1D304022, "UNLOCK_WELCOME_EXECUTE_ATOM", 1); break;
                case "sweep": doSweep(); break;
                case "get": p(parseId(args[1]), "GET"); break;
                case "set":
                    int dev = args.length > 3 ? Integer.parseInt(args[3]) : DEV_LIGHT;
                    doSet(dev, parseId(args[1]), "SET", Integer.parseInt(args[2]));
                    break;
                default:
                    System.out.println("Unknown cmd: " + cmd + " (try: read, turn, hazard, flash, fog, drl, flashhorn, welcome, sweep, get, set)");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void handleFog(String[] args) {
        String which = args.length > 1 ? args[1] : "f";
        String state = args.length > 2 ? args[2] : "on";
        int val = "on".equals(state) ? 1 : 0;
        if (which.startsWith("r")) {
            doSet(DEV_LIGHT, 0x33F0000B, "REAR_FOG_LIGHT_SET", val);
        } else {
            doSet(DEV_LIGHT, 0x33F00013, "FRONT_FOG_LIGHT_SET", val);
        }
    }

    static void doRead() {
        System.out.println("=== Light State Reads (dev=1004) ===");
        p(0x38A00008, "SIDE_LIGHT (position)");
        p(0x38A0000A, "LOW_BEAM_LIGHT");
        p(0x38A0000C, "HIGH_BEAM_LIGHT");
        p(0x38A00018, "EMERGENCY_WARNING (hazard)");
        p(0x38A0002C, "TURN_SIGNAL_LIGHT");
        p(0x1330000C, "LEFT_TURN_SIGNAL");
        p(0x1330000D, "RIGHT_TURN_SIGNAL");
        p(0x1330000E, "FRONT_FOG_LIGHT");
        p(0x1330000F, "REAR_FOG_LIGHT");
        p(0x13300023, "LIGHT_KNOB_CURRENT_GEAR");
        p(0x13300030, "LIGHT_AUTO_SWITCH");
        p(0x3AC00024, "DRL_AUTO_STATE");
        p(0x3E700028, "LOW_BEAM_BLINK_STATE");
        p(0x39400033, "DOUBLE_FLASH_STATE");
        p(0x38A00012, "STOP_LIGHT");
        p(0x38A00014, "REVERSING_LIGHT");
        p(0x3B400046, "SEQUENTIAL_TURN_STATE");
        System.out.println("\n=== Bodywork Light Atoms (dev=1001) ===");
        p(DEV_BODY, 0x1D304022, "UNLOCK_WELCOME_ATOM");
        p(DEV_BODY, 0x1D304028, "LEAVE_CAR_SEND_OFF_ATOM");
    }

    static void doSweep() {
        System.out.println("=== Sweep: trying all light SET IDs (val=1) ===");
        Object[][] ids = {
            {0x33F0000D, "TURN_SIGNAL_LIGHT_SET"},
            {0x1460002E, "TURN_SIGNAL_CONTROL_COMMAND"},
            {0x32B11010, "SECURITY_DOUBLE_FLASH"},
            {0x3E800008, "FLASHING_LIGHT_AND_HORN"},
            {0x33F00013, "FRONT_FOG_LIGHT_SET"},
            {0x33F0000B, "REAR_FOG_LIGHT_SET"},
            {0x43100046, "DRL_AUTO_STATE_SET"},
            {0x4310003E, "ADB_STATE_SET"},
            {0x3E700028, "LOW_BEAM_BLINK_STATE"},
            {0x4C130018, "TURN_INDICATOR_CONTROL_SET"},
            {0x4C130012, "BRAKE_LIGHT_CONTROL_SET"},
            {0x4C130014, "REVERSING_LIGHT_CONTROL_SET"},
            {0x0780A044, "ATOM_FRONT_HEADLIGHT_SET"},
            {0x0780A03E, "ATOM_READ_LIGHT_SET"},
            {0x0780A040, "ATOM_ATMOSPHERE_LIGHT_SWITCH"},
            {0x0780B010, "ATOM_UNLOCK_WELCOME_SET"},
        };
        for (Object[] e : ids) {
            sweepSet((int) e[0], (String) e[1]);
            sleep(400);
        }
        System.out.println("\n=== Sweep complete. 0=success, negative=MCU rejected. ===");
    }

    static void sweepSet(int fid, String name) {
        try {
            int r = (int) setInt.invoke(_mgr, DEV_LIGHT, fid, 1);
            String tag = r == 0 ? "ACCEPTED" : (r == -2147482648 ? "FAILED" : "rc=" + r);
            System.out.println(name + " [0x" + hex(fid) + "] val=1 => " + tag);
        } catch (Exception e) {
            System.out.println(name + " [0x" + hex(fid) + "] => ERR: " + cause(e));
        }
    }

    static void doSet(int dev, int fid, String name, int val) {
        try {
            int r = (int) setInt.invoke(_mgr, dev, fid, val);
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + " val=" + val + "] => result=" + r + (r == 0 ? " (OK)" : ""));
        } catch (Exception e) {
            System.out.println(name + " [dev=" + dev + " fid=0x" + hex(fid) + "] => ERR: " + cause(e));
        }
    }

    static void p(int fid, String name) { p(DEV_LIGHT, fid, name); }

    static void p(int dev, int fid, String name) {
        try {
            int v = (int) getInt.invoke(_mgr, dev, fid);
            System.out.println(name + " [0x" + hex(fid) + "] = " + v);
        } catch (Exception e) {
            System.out.println(name + " [0x" + hex(fid) + "] = ERR: " + cause(e));
        }
    }

    static void sleep(int ms) { try { Thread.sleep(ms); } catch (Exception ignored) {} }
    static String hex(int v) { return Integer.toHexString(v); }
    static String cause(Exception e) { return e.getCause() != null ? e.getCause().getMessage() : e.getMessage(); }
    static int parseIntArg(String[] a, int i, int def) { return a.length > i ? Integer.parseInt(a[i]) : def; }
    static int parseId(String s) { return (s.startsWith("0x") || s.startsWith("0X")) ? Integer.parseUnsignedInt(s.substring(2), 16) : Integer.parseInt(s); }
}
