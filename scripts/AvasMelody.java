import java.lang.reflect.Method;

/**
 * Plays patterns using TEST_AUDIO_AVAS_SET (0xAA000104) for pitch control.
 * Confirmed: TEST_AVAS=1 → pitch A, TEST_AVAS=2 → pitch B (different).
 * AVAH (0x6E970010) = on/off only (1=on, 0=off), does NOT change pitch.
 *
 * Usage: AvasMelody <pattern>
 *   doorbell  - ding (pitch A) then dong (pitch B)
 *   dongding  - dong then ding
 *   alarm     - alternating A/B rapidly
 *   beckon    - A-A-B-B-A-A (like a shop entrance chime)
 *   fanfare   - A-A-A-B-B-B (rising)
 *   falling   - B-B-B-A-A-A (falling)
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/avm scripts/AvasMelody.java
 *   d8 --output /tmp/avm /tmp/avm/AvasMelody.class
 *   adb push /tmp/avm/classes.dex /data/local/tmp/avm.dex
 */
public class AvasMelody {
    static Object mgr;
    static Method setInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;
    static final int TEST_AVAS = 0xAA000104;
    static final int PITCH_A = 1; // lower tone
    static final int PITCH_B = 2; // higher tone

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Patterns: doorbell, dongding, alarm, beckon, fanfare, falling");
            System.out.println("Listen OUTSIDE the car!");
            return;
        }
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            System.out.println("=== AVAS Melody: " + args[0] + " ===");
            System.out.println("  Listen OUTSIDE the car!\n");

            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, 1); // tone ON

            switch (args[0]) {
                case "doorbell": doorbell(); break;
                case "dongding": dongding(); break;
                case "alarm": alarm(); break;
                case "beckon": beckon(); break;
                case "fanfare": fanfare(); break;
                case "falling": falling(); break;
                default: System.out.println("Unknown: " + args[0]);
            }

            setInt.invoke(mgr, D, AVAH, 0);
            disable();
            System.out.println("\n  Done.");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    // Ding (A) then dong (B) — classic doorbell
    static void doorbell() throws Exception {
        System.out.println("  ding... DONG");
        pitch(PITCH_A, 400);
        pitch(PITCH_B, 600);
    }

    // Dong (B) then ding (A)
    static void dongding() throws Exception {
        System.out.println("  DONG... ding");
        pitch(PITCH_B, 600);
        pitch(PITCH_A, 400);
    }

    // Alternating A/B alarm
    static void alarm() throws Exception {
        System.out.println("  wee-woo x4");
        for (int i = 0; i < 4; i++) {
            pitch(PITCH_A, 300);
            pitch(PITCH_B, 300);
        }
    }

    // Shop entrance chime: A-A-B-B (with rests between)
    static void beckon() throws Exception {
        System.out.println("  ding-ding-dong-dong (shop entrance)");
        pitch(PITCH_A, 200); rest(120);
        pitch(PITCH_A, 200); rest(120);
        pitch(PITCH_B, 300); rest(120);
        pitch(PITCH_B, 400);
    }

    // Rising fanfare: A-A-A-B-B-B
    static void fanfare() throws Exception {
        System.out.println("  rising fanfare");
        for (int i = 0; i < 3; i++) pitch(PITCH_A, 200);
        for (int i = 0; i < 3; i++) pitch(PITCH_B, 200);
    }

    // Falling: B-B-B-A-A-A
    static void falling() throws Exception {
        System.out.println("  falling");
        for (int i = 0; i < 3; i++) pitch(PITCH_B, 200);
        for (int i = 0; i < 3; i++) pitch(PITCH_A, 200);
    }

    // Change pitch without stopping the tone
    static void pitch(int p, int ms) throws Exception {
        setInt.invoke(mgr, D, TEST_AVAS, p);
        Thread.sleep(ms);
    }

    // Brief rest (TEST_AVAS=0 produces silence while AVAH stays on)
    static void rest(int ms) throws Exception {
        setInt.invoke(mgr, D, TEST_AVAS, 0);
        Thread.sleep(ms);
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
