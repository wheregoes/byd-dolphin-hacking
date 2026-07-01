import java.lang.reflect.Method;

/**
 * Hunts for a 3rd pitch by testing TEST_AUDIO_AVAS_SET (0xAA000104) values
 * beyond 1 and 2. Each value plays 1.5s with a rest between.
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/hunt scripts/PitchHunt.java
 *   d8 --output /tmp/hunt /tmp/hunt/PitchHunt.class
 *   adb push /tmp/hunt/classes.dex /data/local/tmp/hunt.dex
 */
public class PitchHunt {
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

            System.out.println("=== Pitch Hunt — TEST_AVAS values 3,6,7,8,10,15,20,50,100,255 ===");
            System.out.println("  1=pitch A, 2=pitch B (known). Looking for pitch C!\n");

            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, 1);

            int[] values = {3, 6, 7, 8, 10, 15, 20, 50, 100, 255};
            for (int v : values) {
                System.out.println("  >>> TEST_AVAS=" + v + " <<<  LISTEN 1.5s!");
                setInt.invoke(mgr, D, TEST_AVAS, v);
                Thread.sleep(1500);
                // Rest between
                setInt.invoke(mgr, D, TEST_AVAS, 0);
                Thread.sleep(400);
            }

            // Finish with known A and B for comparison
            System.out.println("  >>> REFERENCE: TEST_AVAS=1 (pitch A) <<<");
            setInt.invoke(mgr, D, TEST_AVAS, 1);
            Thread.sleep(1000);
            setInt.invoke(mgr, D, TEST_AVAS, 0);
            Thread.sleep(300);

            System.out.println("  >>> REFERENCE: TEST_AVAS=2 (pitch B) <<<");
            setInt.invoke(mgr, D, TEST_AVAS, 2);
            Thread.sleep(1000);

            setInt.invoke(mgr, D, AVAH, 0);
            disable();
            System.out.println("\n  Done. Any values 3-255 sound DIFFERENT from both A and B?");
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
