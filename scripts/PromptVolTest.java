import java.lang.reflect.Method;

/**
 * Tests whether PROMPT_VOLUME_LEVEL affects AVAS/AVAH speaker volume.
 * Plays the same AVAH tone at PROMPT_VOLUME=3 (high) then PROMPT_VOLUME=1 (low).
 * User listens for volume difference.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/pvol scripts/PromptVolTest.java
 *   d8 --output /tmp/pvol /tmp/pvol/PromptVolTest.class
 *   adb push /tmp/pvol/classes.dex /data/local/tmp/pvol.dex
 *   adb shell "CLASSPATH=/data/local/tmp/pvol.dex app_process / PromptVolTest"
 */
public class PromptVolTest {
    static Object mgr;
    static Method setInt, getInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;
    static final int PROMPT_VOL_SET = 0xAA000299;
    static final int PROMPT_VOL_STATUS = 0x99000307;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);

            System.out.println("=== PROMPT_VOLUME vs AVAH Volume Test ===");
            System.out.println("Listen for volume difference between HIGH and LOW!\n");

            // Phase 1: PROMPT_VOLUME = 3 (HIGH)
            System.out.println("--- Phase 1: PROMPT_VOLUME = 3 (HIGH) ---");
            setInt.invoke(mgr, D, PROMPT_VOL_SET, 3);
            Thread.sleep(200);
            int readback = (int) getInt.invoke(mgr, D, PROMPT_VOL_STATUS);
            System.out.println("  Prompt volume = " + readback + " (expect 3)");
            
            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, 1); // 1kHz tone
            System.out.println("  >>> LISTEN 4s — AVAH at PROMPT_VOLUME=HIGH <<<");
            Thread.sleep(4000);
            setInt.invoke(mgr, D, AVAH, 0);
            disable();
            Thread.sleep(500);

            // Phase 2: PROMPT_VOLUME = 1 (LOW)
            System.out.println("\n--- Phase 2: PROMPT_VOLUME = 1 (LOW) ---");
            setInt.invoke(mgr, D, PROMPT_VOL_SET, 1);
            Thread.sleep(200);
            readback = (int) getInt.invoke(mgr, D, PROMPT_VOL_STATUS);
            System.out.println("  Prompt volume = " + readback + " (expect 1)");
            
            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, 1); // 1kHz tone
            System.out.println("  >>> LISTEN 4s — AVAH at PROMPT_VOLUME=LOW <<<");
            Thread.sleep(4000);
            setInt.invoke(mgr, D, AVAH, 0);
            disable();
            Thread.sleep(500);

            // Phase 3: PROMPT_VOLUME = 2 (MID) — for completeness
            System.out.println("\n--- Phase 3: PROMPT_VOLUME = 2 (MID) ---");
            setInt.invoke(mgr, D, PROMPT_VOL_SET, 2);
            Thread.sleep(200);
            readback = (int) getInt.invoke(mgr, D, PROMPT_VOL_STATUS);
            System.out.println("  Prompt volume = " + readback + " (expect 2)");
            
            enable();
            Thread.sleep(100);
            setInt.invoke(mgr, D, AVAH, 1);
            System.out.println("  >>> LISTEN 4s — AVAH at PROMPT_VOLUME=MID <<<");
            Thread.sleep(4000);
            setInt.invoke(mgr, D, AVAH, 0);
            disable();

            System.out.println("\n=== Done ===");
            System.out.println("Was there ANY volume difference between HIGH/MID/LOW?");
            System.out.println("If yes — PROMPT_VOLUME_LEVEL controls AVAS volume!");
            System.out.println("If no  — AVAS volume is still hardcoded (as before).");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void enable() throws Exception {
        setInt.invoke(mgr, D, 0xAA000148, 1); // PA on
        setInt.invoke(mgr, D, 0xAA000142, 1); // MCU speak
        setInt.invoke(mgr, D, 0xAA00011A, 1); // FM speak
        setInt.invoke(mgr, D, 0xAA000104, 1); // Test AVAS
        setInt.invoke(mgr, D, 0xAA000171, 1); // AVAS config
        setInt.invoke(mgr, D, 0xAA00011E, 0); // Unmute
    }

    static void disable() throws Exception {
        setInt.invoke(mgr, D, 0xAA000148, 0);
        setInt.invoke(mgr, D, 0xAA000142, 0);
        setInt.invoke(mgr, D, 0xAA00011A, 0);
        setInt.invoke(mgr, D, 0xAA000104, 0);
        setInt.invoke(mgr, D, 0xAA000171, 0);
    }
}
