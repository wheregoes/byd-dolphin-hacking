import java.lang.reflect.Method;

/**
 * Plays recognizable patterns on AVAS using ONLY the 3 confirmed working tones
 * (setInt 1=1kHz, 2=2kHz, 3=3kHz) + timing. setBuffer frequency control does
 * NOT work — MCU ignores buffer content.
 *
 * This is the Tesla-Boombox-equivalent for BYD Dolphin: 3 tones + timing = patterns.
 *
 * Usage: AvasPattern <pattern>
 *   doorbell    - classic ding-dong (1kHz then 2kHz)
 *   alarm       - alternating 1kHz/3kHz (urgent)
 *   jingle      - 1-2-3-2-1 ascending then descending
 *   triple      - three 1kHz beeps
 *   fanfare     - 1-1-1-2-3 (charge!)
 *   tetris      - Korobeiniki theme approximated
 *   charge      - cavalry charge fanfare
 *
 * Build:
 *   javac -source 11 -target 11 -d /tmp/avp scripts/AvasPattern.java
 *   d8 --output /tmp/avp /tmp/avp/AvasPattern.class
 *   adb push /tmp/avp/classes.dex /data/local/tmp/avp.dex
 */
public class AvasPattern {
    static Object mgr;
    static Method setInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;
    static final int T1 = 1; // 1kHz
    static final int T2 = 2; // 2kHz
    static final int T3 = 3; // 3kHz
    static final int OFF = 0;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Patterns: doorbell, alarm, jingle, triple, fanfare, tetris, charge");
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

            System.out.println("=== AVAS Pattern: " + args[0] + " ===");
            System.out.println("  Listen OUTSIDE the car!\n");

            switch (args[0]) {
                case "doorbell": doorbell(); break;
                case "alarm": alarm(); break;
                case "jingle": jingle(); break;
                case "triple": triple(); break;
                case "fanfare": fanfare(); break;
                case "tetris": tetris(); break;
                case "charge": charge(); break;
                default: System.out.println("Unknown pattern: " + args[0]);
            }

            setInt.invoke(mgr, D, AVAH, OFF);
            disable();
            System.out.println("\n  Pattern done.");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    // --- Patterns ---

    // Classic doorbell: low ding (1kHz) then higher dong (2kHz)
    static void doorbell() throws Exception {
        System.out.println("  doorbell: ding... DONG");
        tone(T1, 400); gap(150);
        tone(T2, 600); gap(200);
    }

    // Urgent alarm: alternating 1kHz / 3kHz
    static void alarm() throws Exception {
        System.out.println("  alarm: wee-woo x4");
        for (int i = 0; i < 4; i++) {
            tone(T1, 300); gap(100);
            tone(T3, 300); gap(100);
        }
    }

    // Ascending/descending jingle: 1-2-3-2-1
    static void jingle() throws Exception {
        System.out.println("  jingle: 1-2-3-2-1");
        int[] notes = {T1, T2, T3, T2, T1};
        for (int n : notes) {
            tone(n, 250);
            gap(80);
        }
    }

    // Three beeps (like a truck reversing)
    static void triple() throws Exception {
        System.out.println("  triple beep");
        for (int i = 0; i < 3; i++) {
            tone(T1, 200);
            gap(200);
        }
    }

    // Fanfare: 1-1-1-2-3 (like a cavalry charge)
    static void fanfare() throws Exception {
        System.out.println("  fanfare: da-da-da-DA-DAAA!");
        tone(T1, 150); gap(80);
        tone(T1, 150); gap(80);
        tone(T1, 150); gap(80);
        tone(T2, 300); gap(100);
        tone(T3, 600); gap(200);
    }

    // Tetris Korobeiniki theme (approximated with 3 tones)
    // E-B-C-E-G-C-E-G-A (simplified)
    static void tetris() throws Exception {
        System.out.println("  tetris: Korobeiniki");
        // Using 1kHz as "E", 2kHz as "G", 3kHz as "B" (rough)
        tone(T1, 200); gap(50);  // E
        tone(T3, 200); gap(50);  // B (high)
        tone(T2, 200); gap(50);  // C
        tone(T1, 200); gap(50);  // E
        tone(T2, 200); gap(50);  // G
        tone(T1, 200); gap(50);  // C
        tone(T1, 400); gap(80);  // E (long)
        tone(T2, 200); gap(50);  // G
        tone(T3, 400); gap(200); // A (long, high)
        gap(200);
        // Second phrase
        tone(T3, 200); gap(50);
        tone(T2, 200); gap(50);
        tone(T1, 200); gap(50);
        tone(T2, 200); gap(50);
        tone(T1, 200); gap(50);
        tone(T3, 200); gap(50);
        tone(T1, 400); gap(200);
    }

    // Cavalry charge: rapid ascending triplets
    static void charge() throws Exception {
        System.out.println("  charge!");
        for (int rep = 0; rep < 3; rep++) {
            tone(T1, 100); gap(40);
            tone(T2, 100); gap(40);
            tone(T3, 100); gap(40);
        }
        tone(T3, 500); gap(200);
    }

    // --- Helpers ---

    static void tone(int t, int ms) throws Exception {
        enable();
        Thread.sleep(50);
        setInt.invoke(mgr, D, AVAH, t);
        Thread.sleep(ms);
    }

    static void gap(int ms) throws Exception {
        setInt.invoke(mgr, D, AVAH, OFF);
        disable(); // MUST disable enablers or tone stays stuck
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
