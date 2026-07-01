import java.lang.reflect.Method;

/**
 * Single-phase AVAH test — plays one tone at a given prompt volume.
 * Usage: PromptVolSingle <phase>
 *   phase=1: PROMPT_VOLUME=3 (HIGH) + 1kHz tone
 *   phase=2: PROMPT_VOLUME=1 (LOW)  + 1kHz tone
 *   phase=3: PROMPT_VOLUME=2 (MID)  + 1kHz tone
 *   phase=4: Imperial March at current prompt volume
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/pvs scripts/PromptVolSingle.java
 *   d8 --output /tmp/pvs /tmp/pvs/PromptVolSingle.class
 *   adb push /tmp/pvs/classes.dex /data/local/tmp/pvs.dex
 */
public class PromptVolSingle {
    static Object mgr;
    static Method setInt, getInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;

    static final int C4 = 262, D4 = 294, E4 = 330, F4 = 349, G4 = 392;
    static final int A4 = 440, B4 = 494, C5 = 523, D5 = 587, E5 = 659;
    static final int F5 = 698, G5 = 784, A5 = 880, REST = 0;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: PromptVolSingle <1|2|3|4>");
            System.out.println("  1 = PROMPT_VOLUME=HIGH + 1kHz tone (4s)");
            System.out.println("  2 = PROMPT_VOLUME=LOW  + 1kHz tone (4s)");
            System.out.println("  3 = PROMPT_VOLUME=MID  + 1kHz tone (4s)");
            System.out.println("  4 = Imperial March at current volume");
            return;
        }
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);

            int phase = Integer.parseInt(args[0]);
            switch (phase) {
                case 1: playToneAtVolume(3, "HIGH"); break;
                case 2: playToneAtVolume(1, "LOW"); break;
                case 3: playToneAtVolume(2, "MID"); break;
                case 4: playImperialMarch(); break;
                default: System.out.println("Invalid phase: " + phase);
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void playToneAtVolume(int vol, String label) throws Exception {
        System.out.println("=== Phase: PROMPT_VOLUME = " + vol + " (" + label + ") ===");
        setInt.invoke(mgr, D, 0xAA000299, vol);
        Thread.sleep(200);
        int readback = (int) getInt.invoke(mgr, D, 0x99000307);
        System.out.println("  Prompt volume readback = " + readback + " (expect " + vol + ")");

        enable();
        Thread.sleep(100);
        setInt.invoke(mgr, D, AVAH, 1); // 1kHz
        System.out.println("  >>> LISTEN 4s — 1kHz tone at PROMPT_VOLUME=" + label + " <<<");
        Thread.sleep(4000);
        setInt.invoke(mgr, D, AVAH, 0);
        disable();
        System.out.println("  Tone stopped.");
    }

    static void playImperialMarch() throws Exception {
        System.out.println("=== Imperial March on AVAS ===");
        System.out.println("  Listen OUTSIDE the car!");
        enable();
        Thread.sleep(100);
        setInt.invoke(mgr, D, AVAH, 1);

        int Eb4 = 311, Bb3 = 233, Ab4 = 415, Gb4 = 370, Bb4 = 466;
        int Eb5 = 622, Ab3 = 208, G5 = 784, F5 = 698;

        // Simplified Imperial March (freq via setBuffer)
        java.lang.reflect.Method setBuffer = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);

        // Main theme: G G G Eb-Bb G Eb-Bb G
        note(setBuffer, G4, 500); note(setBuffer, G4, 500); note(setBuffer, G4, 500);
        note(setBuffer, Eb4, 350); note(setBuffer, Bb4, 150);
        note(setBuffer, G4, 500); note(setBuffer, Eb4, 350); note(setBuffer, Bb4, 150);
        note(setBuffer, G4, 1000); rest(setBuffer, 200);

        // Second: D5 D5 D5 Eb5-Bb4 Gb4 Eb4 Bb4 G4
        note(setBuffer, D5, 500); note(setBuffer, D5, 500); note(setBuffer, D5, 500);
        note(setBuffer, Eb5, 350); note(setBuffer, Bb4, 150);
        note(setBuffer, Gb4, 500); note(setBuffer, Eb4, 350); note(setBuffer, Bb4, 150);
        note(setBuffer, G4, 1000); rest(setBuffer, 200);

        setInt.invoke(mgr, D, AVAH, 0);
        disable();
        System.out.println("  Imperial March done.");
    }

    static void note(java.lang.reflect.Method setBuffer, int freq, int ms) throws Exception {
        byte[] buf = {(byte)((freq >> 8) & 0xFF), (byte)(freq & 0xFF)};
        setBuffer.invoke(mgr, D, AVAH, buf);
        Thread.sleep(ms);
        setInt.invoke(mgr, D, AVAH, 0);
        Thread.sleep(30);
    }

    static void rest(java.lang.reflect.Method setBuffer, int ms) throws Exception {
        setInt.invoke(mgr, D, AVAH, 0);
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
