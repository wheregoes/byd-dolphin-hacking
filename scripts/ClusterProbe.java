/**
 * Probes the BYD instrument cluster (driver display) via BYDAutoManager.
 *
 * The instrument cluster is device type 3 (INSTRUMENT) in the BYDAuto API.
 * This script tests:
 *   - getInstrumentScreenType()
 *   - getInstrumentView() / setViewSwitch()
 *   - getBacklightBrightness() / setBacklightBrightness()
 *   - getBacklightModeState() / setBacklightModeState()
 *   - getUnit() / setUnit()
 *   - getMileageUnit() / getSpeedUnit() / getPowerUnit()
 *   - getViewStatus()
 *   - Various view constants (DRIVING=1, MENU=2, FAULT=3, etc.)
 *
 * Uses pure reflection (no android imports) — compiles without android.jar.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/clusterprobe scripts/ClusterProbe.java
 *   d8 --output /tmp/clusterprobe /tmp/clusterprobe/ClusterProbe.class
 *   adb push /tmp/clusterprobe/classes.dex /data/local/tmp/clusterprobe.dex
 *
 *   adb shell "CLASSPATH=/data/local/tmp/clusterprobe.dex app_process / ClusterProbe"
 *   adb shell "CLASSPATH=/data/local/tmp/clusterprobe.dex app_process / ClusterProbe view 1"
 *   adb shell "CLASSPATH=/data/local/tmp/clusterprobe.dex app_process / ClusterProbe backlight 128"
 */
import java.lang.reflect.Method;

public class ClusterProbe {
    static final int DEV_INSTRUMENT = 3;

    static Object ctx;
    static Object mgr;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            ctx = atClass.getMethod("getSystemContext").invoke(thread);

            System.out.println("=== BYD Instrument Cluster Probe ===");
            System.out.println("Device type: " + DEV_INSTRUMENT + " (INSTRUMENT)");
            System.out.println();

            Object mgrObj = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            if (mgrObj == null) {
                System.out.println("ERROR: BYDAutoManager is null (no auto service?)");
                return;
            }
            mgr = mgrObj;
            System.out.println("BYDAutoManager: " + mgr);
            System.out.println();

            if (args.length == 0) {
                runFullProbe();
            } else {
                String cmd = args[0];
                if (cmd.equals("view") && args.length >= 2) {
                    int view = Integer.parseInt(args[1]);
                    setViewSwitch(view);
                } else if (cmd.equals("backlight") && args.length >= 2) {
                    int val = Integer.parseInt(args[1]);
                    setBacklightBrightness(val);
                } else if (cmd.equals("backlightmode") && args.length >= 2) {
                    int mode = Integer.parseInt(args[1]);
                    int param = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
                    setBacklightModeState(mode, param);
                } else if (cmd.equals("unit") && args.length >= 3) {
                    int unitType = Integer.parseInt(args[1]);
                    int value = Integer.parseInt(args[2]);
                    setUnit(unitType, value);
                } else {
                    System.out.println("Usage: ClusterProbe [view <1-8> | backlight <0-255> | backlightmode <mode> [param] | unit <type> <val>]");
                    System.out.println("  view: 1=Driving, 2=Menu, 3=Fault, 4=Charge, 5=Discharge, 6=ADAS, 7=Travel, 8=Accelerometer");
                    System.out.println("  backlight: 0-255 brightness");
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void runFullProbe() throws Exception {
        Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
        Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

        System.out.println("--- Instrument Screen Info ---");
        probe(getInt, "InstrumentScreenType", DEV_INSTRUMENT, 0x4A50B01E);
        probe(getInt, "InstrumentView", DEV_INSTRUMENT, 0x4A50B020);
        probe(getInt, "ViewStatus", DEV_INSTRUMENT, 0x4A50B024);
        probe(getInt, "ViewSwitch", DEV_INSTRUMENT, 0x4A50B026);

        System.out.println("\n--- Backlight ---");
        probe(getInt, "BacklightBrightness", DEV_INSTRUMENT, 0x4A50B028);
        probe(getInt, "BacklightAutoModeState", DEV_INSTRUMENT, 0x4A50B02A);
        probe(getInt, "BacklightLinkModeState", DEV_INSTRUMENT, 0x4A50B02C);
        probe(getInt, "BacklightCtlType", DEV_INSTRUMENT, 0x4BF0002D);

        System.out.println("\n--- Theme ---");
        probe(getInt, "InsThemeValue", DEV_INSTRUMENT, 0x49C00028);
        probe(getInt, "InstrumentTheme", DEV_INSTRUMENT, 0x40C0B010);
        probe(getInt, "InstrumentThemeStatus", DEV_INSTRUMENT, 0x28C02021);
        probe(getInt, "InstrumentThemeVersion", DEV_INSTRUMENT, 0x28C0201B);
        probe(getInt, "InstrumentThemeContent", DEV_INSTRUMENT, 0x28C0201E);

        System.out.println("\n--- Mode Switch ---");
        probe(getInt, "ModeSwitchConfigStatus", DEV_INSTRUMENT, 0x30100024);
        probe(getInt, "RiesChildModeSwitch", DEV_INSTRUMENT, 0x3150102C);

        System.out.println("\n--- Units ---");
        probe(getInt, "MileageUnit", DEV_INSTRUMENT, 0x4A50B030);
        probe(getInt, "SpeedUnit", DEV_INSTRUMENT, 0x4A50B032);
        probe(getInt, "PowerUnit", DEV_INSTRUMENT, 0x4A50B034);
        probe(getInt, "TempUnit", DEV_INSTRUMENT, 0x4A50B036);
        probe(getInt, "PressUnit", DEV_INSTRUMENT, 0x4A50B038);
        probe(getInt, "ConDisUnit", DEV_INSTRUMENT, 0x4A50B03A);

        System.out.println("\n--- Navigation ---");
        probe(getInt, "NavigationStyle", DEV_INSTRUMENT, 0x4C130041);

        System.out.println("\n--- Menu Display ---");
        probe(getInt, "MenuDisplaySettings", DEV_INSTRUMENT, 0x4EF53010);
        probe(getInt, "ThemeDisplaySettings", DEV_INSTRUMENT, 0x4EF53012);

        System.out.println("\n--- Other ---");
        probe(getInt, "AverageSpeed", DEV_INSTRUMENT, 0x4A50B040);
        probe(getInt, "PowerOnErrInfo", DEV_INSTRUMENT, 0x4A50B042);
        probe(getInt, "PowerOffErrInfo", DEV_INSTRUMENT, 0x4A50B044);

        System.out.println("\n--- 2IN1 View Constants ---");
        System.out.println("  DRIVING=1, MENU=2, FAULT=3, CHARGE=4, DISCHARGE=5");
        System.out.println("  ADAS=6, TRAVEL=7, ACCELEROMETER=8");
        System.out.println("  ViewSwitch: WIN_CLOSE=1, WIN_OPEN=2");
    }

    static void probe(Method getInt, String name, int dev, int fid) throws Exception {
        try {
            int val = (int) getInt.invoke(mgr, dev, fid);
            System.out.println("  " + name + " (0x" + Integer.toHexString(fid).toUpperCase() + ") = " + val);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            System.out.println("  " + name + " (0x" + Integer.toHexString(fid).toUpperCase() + ") = ERROR: " + msg);
        }
    }

    static void setViewSwitch(int view) throws Exception {
        Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
        int r = (int) setInt.invoke(mgr, DEV_INSTRUMENT, 0x4A50B026, view);
        System.out.println("setViewSwitch(" + view + ") → result=" + r +
            (r == 0 ? " (OK)" : " (FAILED)"));
    }

    static void setBacklightBrightness(int val) throws Exception {
        Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
        int r = (int) setInt.invoke(mgr, DEV_INSTRUMENT, 0x4A50B028, val);
        System.out.println("setBacklightBrightness(" + val + ") → result=" + r +
            (r == 0 ? " (OK)" : " (FAILED)"));
    }

    static void setBacklightModeState(int mode, int param) throws Exception {
        Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
        int r = (int) setInt.invoke(mgr, DEV_INSTRUMENT, 0x4A50B02A, mode);
        System.out.println("setBacklightModeState(" + mode + ") → result=" + r +
            (r == 0 ? " (OK)" : " (FAILED)"));
    }

    static void setUnit(int unitType, int value) throws Exception {
        Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
        int[] unitFids = {0x4A50B030, 0x4A50B032, 0x4A50B034, 0x4A50B036, 0x4A50B038, 0x4A50B03A};
        String[] unitNames = {"Mileage", "Speed", "Power", "Temp", "Press", "ConDis"};
        if (unitType < 0 || unitType >= unitFids.length) {
            System.out.println("Invalid unit type. 0=Mileage, 1=Speed, 2=Power, 3=Temp, 4=Press, 5=ConDis");
            return;
        }
        int r = (int) setInt.invoke(mgr, DEV_INSTRUMENT, unitFids[unitType], value);
        System.out.println("set" + unitNames[unitType] + "Unit(" + value + ") → result=" + r +
            (r == 0 ? " (OK)" : " (FAILED)"));
    }

    static int parseId(String s) {
        return (int) Long.parseLong(s.replace("0x", ""), 16);
    }
}
