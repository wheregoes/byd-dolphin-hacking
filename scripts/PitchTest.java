import java.lang.reflect.Method;

/**
 * Tests pitch control via TEST_AUDIO_AVAS_SET (0xAA000104) — the approach
 * the door-sound app uses. AVAH (0x6E970010) may just be on/off (1=on, 0=off),
 * while TEST_AUDIO_AVAS selects the actual pitch (1/2/3).
 *
 * Usage: PitchTest <mode>
 *   1 = Three tones with FULL disable between (AVAH=1,2,3)
 *   2 = Three tones changing TEST_AUDIO_AVAS while AVAH stays on
 *   3 = Three tones changing AVAH while AVAH stays on (no stop)
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/pt scripts/PitchTest.java
 *   d8 --output /tmp/pt /tmp/pt/PitchTest.class
 *   adb push /tmp/pt/classes.dex /data/local/tmp/pt.dex
 */
public class PitchTest {
    static Object mgr;
    static Method setInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;
    static final int TEST_AVAS = 0xAA000104;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: PitchTest <1|2|3>");
            return;
        }
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            int mode = Integer.parseInt(args[0]);
            switch (mode) {
                case 1: testFullDisable(); break;
                case 2: testTestAvasChange(); break;
                case 3: testAvasChange(); break;
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    // Mode 1: Full disable/re-enable between each AVAH value
    static void testFullDisable() throws Exception {
        System.out.println("=== Mode 1: Full disable between, AVAH=1,2,3 ===");
        System.out.println("  Listen for 3 DIFFERENT pitches\n");

        for (int v = 1; v <= 3; v++) {
            System.out.println("  >>> Tone " + v + ": AVAH=" + v + " <<<");
            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, v);
            Thread.sleep(800);
            setInt.invoke(mgr, D, AVAH, 0);
            disable();
            Thread.sleep(400);
        }
        System.out.println("  Done.");
    }

    // Mode 2: AVAH stays on (1), change TEST_AUDIO_AVAS (0xAA000104) between 1/2/3
    static void testTestAvasChange() throws Exception {
        System.out.println("=== Mode 2: AVAH=1 constant, TEST_AUDIO_AVAS=1,2,3 ===");
        System.out.println("  Listen for pitch changes without stopping\n");

        enable();
        Thread.sleep(100);
        setInt.invoke(mgr, D, TEST_AVAS, 1);
        setInt.invoke(mgr, D, AVAH, 1);
        System.out.println("  >>> TEST_AVAS=1 (1kHz?) for 1s <<<");
        Thread.sleep(1000);

        System.out.println("  >>> Changing TEST_AVAS=2 <<<");
        setInt.invoke(mgr, D, TEST_AVAS, 2);
        Thread.sleep(1000);

        System.out.println("  >>> Changing TEST_AVAS=3 <<<");
        setInt.invoke(mgr, D, TEST_AVAS, 3);
        Thread.sleep(1000);

        setInt.invoke(mgr, D, AVAH, 0);
        disable();
        System.out.println("  Done.");
    }

    // Mode 3: AVAH stays on, change AVAH value between 1/2/3 (no stop)
    static void testAvasChange() throws Exception {
        System.out.println("=== Mode 3: Change AVAH=1,2,3 while playing (no stop) ===");
        System.out.println("  Listen for pitch changes without stopping\n");

        enable();
        Thread.sleep(100);
        setInt.invoke(mgr, D, AVAH, 1);
        System.out.println("  >>> AVAH=1 for 1s <<<");
        Thread.sleep(1000);

        System.out.println("  >>> Changing AVAH=2 <<<");
        setInt.invoke(mgr, D, AVAH, 2);
        Thread.sleep(1000);

        System.out.println("  >>> Changing AVAH=3 <<<");
        setInt.invoke(mgr, D, AVAH, 3);
        Thread.sleep(1000);

        setInt.invoke(mgr, D, AVAH, 0);
        disable();
        System.out.println("  Done.");
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
