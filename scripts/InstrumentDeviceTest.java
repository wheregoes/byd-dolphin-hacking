import java.lang.reflect.Method;

/**
 * Calls BYDAutoInstrumentDevice methods directly via reflection.
 * The device class wraps BYDAutoManager with proper feature IDs baked in.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/instdev scripts/InstrumentDeviceTest.java
 *   d8 --output /tmp/instdev /tmp/instdev/InstrumentDeviceTest.class
 *   adb push /tmp/instdev/classes.dex /data/local/tmp/instdev.dex
 *   adb shell "CLASSPATH=/data/local/tmp/instdev.dex app_process / InstrumentDeviceTest"
 *   adb shell "CLASSPATH=/data/local/tmp/instdev.dex app_process / InstrumentDeviceTest view 1"
 *   adb shell "CLASSPATH=/data/local/tmp/instdev.dex app_process / InstrumentDeviceTest backlight 128"
 */
public class InstrumentDeviceTest {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object ctx = atClass.getMethod("getSystemContext").invoke(thread);

            System.out.println("=== BYDAutoInstrumentDevice Direct Test ===\n");

            Class<?> instrClass = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice");
            Method getInstance = instrClass.getMethod("getInstance", Class.forName("android.content.Context"));
            Object device = getInstance.invoke(null, ctx);
            if (device == null) {
                System.out.println("ERROR: BYDAutoInstrumentDevice.getInstance() returned null");
                return;
            }
            System.out.println("InstrumentDevice: " + device);
            System.out.println("DeviceType: " + instrClass.getMethod("getDevicetype").invoke(device));
            System.out.println();

            if (args.length == 0) {
                runReads(device, instrClass);
            } else {
                String cmd = args[0];
                if (cmd.equals("view") && args.length >= 2) {
                    int val = Integer.parseInt(args[1]);
                    callWithTimeout(device, "setViewSwitch", new Class[]{int.class}, new Object[]{val}, 5000);
                } else if (cmd.equals("backlight") && args.length >= 2) {
                    int val = Integer.parseInt(args[1]);
                    callWithTimeout(device, "setBacklightBrightness", new Class[]{int.class}, new Object[]{val}, 5000);
                } else if (cmd.equals("backlightmode") && args.length >= 3) {
                    int a = Integer.parseInt(args[1]);
                    int b = Integer.parseInt(args[2]);
                    callWithTimeout(device, "setBacklightModeState", new Class[]{int.class, int.class}, new Object[]{a, b}, 5000);
                } else if (cmd.equals("unit") && args.length >= 3) {
                    int type = Integer.parseInt(args[1]);
                    int val = Integer.parseInt(args[2]);
                    callWithTimeout(device, "setUnit", new Class[]{int.class, int.class}, new Object[]{type, val}, 5000);
                } else if (cmd.equals("clearfault") && args.length >= 2) {
                    int val = Integer.parseInt(args[1]);
                    callWithTimeout(device, "setClearFault", new Class[]{int.class}, new Object[]{val}, 5000);
                } else if (cmd.equals("drivinginfo") && args.length >= 2) {
                    int val = Integer.parseInt(args[1]);
                    callWithTimeout(device, "setDrivingInfoSwitch", new Class[]{int.class}, new Object[]{val}, 5000);
                } else {
                    System.out.println("Commands: view <1-8>, backlight <0-255>, backlightmode <mode> <param>,");
                    System.out.println("          unit <type> <val>, clearfault <val>, drivinginfo <val>");
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void runReads(Object device, Class<?> cls) throws Exception {
        String[][] reads = {
            {"getInstrumentScreenType", "ScreenType"},
            {"getInstrumentView", "View"},
            {"getViewStatus", "ViewStatus"},
            {"getBacklightBrightness", "BacklightBrightness"},
            {"getBacklightModeState", "BacklightModeState(0)"},
            {"getAverageSpeed", "AverageSpeed"},
            {"getMileageUnit", "MileageUnit"},
            {"getSpeedUnit", "SpeedUnit"},
            {"getPowerUnit", "PowerUnit"},
            {"getMalfunctionList", "MalfunctionList"},
            {"getAlarmBuzzleState", "AlarmBuzzleState"},
            {"getPowerOnErrInfo", "PowerOnErrInfo"},
            {"getPowerOffErrInfo", "PowerOffErrInfo"},
            {"getRemoteDrivingReminder", "RemoteDrivingReminder"},
            {"getKeyDetectionReminder", "KeyDetectionReminder"},
            {"getViewStatus", "ViewStatus"},
            {"getFirstMenu", "FirstMenu"},
            {"get2in1MenuState", "2in1MenuState"},
            {"getAppointmentHour", "AppointmentHour"},
            {"getAppointmentMinute", "AppointmentMinute"},
        };

        for (String[] r : reads) {
            try {
                Method m = cls.getMethod(r[0]);
                final Object[] result = new Object[1];
                Thread t = new Thread(() -> {
                    try { result[0] = m.invoke(device); }
                    catch (Exception e) { result[0] = e.getCause() != null ? e.getCause() : e; }
                });
                t.setDaemon(true);
                t.start();
                t.join(5000);
                if (t.isAlive()) {
                    System.out.println("  " + r[1] + " = TIMEOUT");
                } else {
                    System.out.println("  " + r[1] + " = " + result[0]);
                }
            } catch (NoSuchMethodException e) {
                System.out.println("  " + r[1] + " = (no such method)");
            }
        }

        System.out.println("\n--- BacklightModeState with params ---");
        for (int i = 0; i <= 3; i++) {
            final int idx = i;
            final Object[] result = new Object[1];
            try {
                Method m = cls.getMethod("getBacklightModeState", int.class);
                Thread t = new Thread(() -> {
                    try { result[0] = m.invoke(device, idx); }
                    catch (Exception e) { result[0] = e.getCause() != null ? e.getCause() : e; }
                });
                t.setDaemon(true);
                t.start();
                t.join(5000);
                if (t.isAlive()) {
                    System.out.println("  BacklightModeState[" + idx + "] = TIMEOUT");
                } else {
                    System.out.println("  BacklightModeState[" + idx + "] = " + result[0]);
                }
            } catch (NoSuchMethodException e) {
                System.out.println("  BacklightModeState[" + idx + "] = (no such method)");
            }
        }

        System.out.println("\n--- Unit with params ---");
        for (int i = 0; i <= 5; i++) {
            final int idx = i;
            final Object[] result = new Object[1];
            try {
                Method m = cls.getMethod("getUnit", int.class);
                Thread t = new Thread(() -> {
                    try { result[0] = m.invoke(device, idx); }
                    catch (Exception e) { result[0] = e.getCause() != null ? e.getCause() : e; }
                });
                t.setDaemon(true);
                t.start();
                t.join(5000);
                if (t.isAlive()) {
                    System.out.println("  Unit[" + idx + "] = TIMEOUT");
                } else {
                    System.out.println("  Unit[" + idx + "] = " + result[0]);
                }
            } catch (NoSuchMethodException e) {
                System.out.println("  Unit[" + idx + "] = (no such method)");
            }
        }
    }

    static void callWithTimeout(Object device, String methodName, Class<?>[] paramTypes, Object[] args, int timeoutMs) throws Exception {
        Method m = device.getClass().getMethod(methodName, paramTypes);
        final Object[] result = new Object[1];
        Thread t = new Thread(() -> {
            try { result[0] = m.invoke(device, args); }
            catch (Exception e) { result[0] = e.getCause() != null ? e.getCause() : e; }
        });
        t.setDaemon(true);
        t.start();
        t.join(timeoutMs);
        if (t.isAlive()) {
            System.out.println(methodName + "(" + argsToString(args) + ") → TIMEOUT (" + timeoutMs + "ms)");
        } else {
            int r = (Integer) result[0] instanceof Integer ? (Integer) result[0] : -999;
            System.out.println(methodName + "(" + argsToString(args) + ") → " + result[0] +
                ((Integer)result[0] == 0 ? " (OK)" : " (FAILED)"));
        }
    }

    static String argsToString(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
