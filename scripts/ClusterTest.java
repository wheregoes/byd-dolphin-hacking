import java.lang.reflect.Method;

/**
 * Quick targeted test of instrument cluster properties that returned non-NOT_REGISTERED values.
 * Tests: BacklightCtlType, InsThemeValue, PowerUnit, TempUnit
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/clustertest scripts/ClusterTest.java
 *   d8 --output /tmp/clustertest /tmp/clustertest/ClusterTest.class
 *   adb push /tmp/clustertest/classes.dex /data/local/tmp/clustertest.dex
 *   adb shell "CLASSPATH=/data/local/tmp/clustertest.dex app_process / ClusterTest"
 *   adb shell "CLASSPATH=/data/local/tmp/clustertest.dex app_process / ClusterTest set 0x49C00028 1"
 */
public class ClusterTest {
    static final int DEV_INSTRUMENT = 3;
    static Object mgr;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object ctx = atClass.getMethod("getSystemContext").invoke(thread);

            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            if (mgr == null) {
                System.out.println("ERROR: BYDAutoManager is null");
                return;
            }

            Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            if (args.length >= 3 && args[0].equals("set")) {
                int fid = (int) Long.parseLong(args[1].replace("0x", ""), 16);
                int val = Integer.parseInt(args[2]);
                System.out.println("SET dev=" + DEV_INSTRUMENT + " fid=0x" + Integer.toHexString(fid).toUpperCase() + " val=" + val);
                Thread t = new Thread(() -> {
                    try {
                        int r = (int) setInt.invoke(mgr, DEV_INSTRUMENT, fid, val);
                        System.out.println("  result=" + r + (r == 0 ? " (OK)" : " (FAILED)"));
                    } catch (Exception e) {
                        System.out.println("  ERROR: " + e.getCause());
                    }
                });
                t.setDaemon(true);
                t.start();
                t.join(5000);
                if (t.isAlive()) {
                    System.out.println("  TIMEOUT (5s) — MCU didn't respond");
                }
                return;
            }

            System.out.println("=== Targeted Instrument Probe ===\n");

            int[][] probes = {
                {0x4BF0002D},
                {0x49C00028},
                {0x4A50B034},
                {0x4A50B036},
            };
            String[] names = {"BacklightCtlType", "InsThemeValue", "PowerUnit", "TempUnit"};

            for (int i = 0; i < probes.length; i++) {
                int[] p = probes[i];
                try {
                    int[] result = new int[1];
                    Thread t = new Thread(() -> {
                        try {
                            result[0] = (int) getInt.invoke(mgr, DEV_INSTRUMENT, p[0]);
                        } catch (Exception e) {
                            result[0] = -999;
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                    t.join(5000);
                    if (t.isAlive()) {
                        System.out.println("  " + names[i] + " (0x" + Integer.toHexString(p[0]).toUpperCase() + ") = TIMEOUT");
                    } else {
                        System.out.println("  " + names[i] + " (0x" + Integer.toHexString(p[0]).toUpperCase() + ") = " + result[0]);
                    }
                } catch (Exception e) {
                    System.out.println("  " + p[1] + " = ERROR: " + e.getMessage());
                }
            }

            System.out.println("\n=== Testing SET on InsThemeValue (0x49C00028) ===");
            System.out.println("Trying values 0-5...");
            for (int v = 0; v <= 5; v++) {
                final int val = v;
                final int[] result = new int[1];
                Thread t = new Thread(() -> {
                    try {
                        result[0] = (int) setInt.invoke(mgr, DEV_INSTRUMENT, 0x49C00028, val);
                    } catch (Exception e) {
                        result[0] = -999;
                    }
                });
                t.setDaemon(true);
                t.start();
                try { t.join(3000); } catch (InterruptedException ignored) {}
                if (t.isAlive()) {
                    System.out.println("  val=" + val + " → TIMEOUT");
                } else {
                    System.out.println("  val=" + val + " → result=" + result[0] + (result[0] == 0 ? " (OK)" : ""));
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
