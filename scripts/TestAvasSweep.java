import java.lang.reflect.Method;

/**
 * Identifies which TEST_AUDIO_AVAS_SET (0xAA000104) values produce which pitches.
 * Plays each value 1-5 separately with full disable between, 2s each.
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/tav scripts/TestAvasSweep.java
 *   d8 --output /tmp/tav /tmp/tav/TestAvasSweep.class
 *   adb push /tmp/tav/classes.dex /data/local/tmp/tav.dex
 */
public class TestAvasSweep {
    static Object mgr;
    static Method setInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;
    static final int TEST_AVAS = 0xAA000104;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            System.out.println("=== TEST_AUDIO_AVAS Pitch Sweep ===");
            System.out.println("  Each value plays 2s with gap between.\n");

            int[] values = {1, 2, 3, 4, 5, 0};
            for (int v : values) {
                System.out.println("  >>> TEST_AVAS=" + v + " <<<  LISTEN 2s!");
                enable();
                Thread.sleep(100);
                setInt.invoke(mgr, D, TEST_AVAS, v);
                Thread.sleep(50);
                setInt.invoke(mgr, D, AVAH, 1);
                Thread.sleep(2000);
                setInt.invoke(mgr, D, AVAH, 0);
                disable();
                Thread.sleep(800);
            }
            System.out.println("  Done. Which values produced sound? Were any different pitch?");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void enable() throws Exception {
        setInt.invoke(mgr, D, 0xAA000148, 1);
        setInt.invoke(mgr, D, 0xAA000142, 1);
        setInt.invoke(mgr, D, 0xAA00011A, 1);
        setInt.invoke(mgr, D, 0xAA000104, 1);
        setInt.invoke(mgr, D, 0xAA000171, 1);
        setInt.invoke(mgr, D, 0xAA00011E, 0);
    }

    static void disable() throws Exception {
        setInt.invoke(mgr, D, 0xAA000148, 0);
        setInt.invoke(mgr, D, 0xAA000142, 0);
        setInt.invoke(mgr, D, 0xAA00011A, 0);
        setInt.invoke(mgr, D, 0xAA000104, 0);
        setInt.invoke(mgr, D, 0xAA000171, 0);
    }
}
