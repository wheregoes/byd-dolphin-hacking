import java.lang.reflect.Method;

public class AvahCycle {
    static Object mgr;
    static Method si;
    static int D = 1002, AVAH = 0x6E970010;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            si = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            System.out.println("=== AVAH Start/Stop Cycle Test ===\n");

            // Cycle 1: Start -> 3s -> Stop (reset enablers first, then AVAH=0)
            System.out.println("--- Cycle 1 (3s beep) ---");
            start();
            System.out.println("  >>> LISTEN 3s <<<");
            Thread.sleep(3000);
            stop();
            System.out.println("  >>> 3s silence <<<");
            Thread.sleep(3000);

            // Cycle 2: Short 1s beep
            System.out.println("--- Cycle 2 (1s beep) ---");
            start();
            System.out.println("  >>> LISTEN 1s <<<");
            Thread.sleep(1000);
            stop();
            System.out.println("  >>> 3s silence <<<");
            Thread.sleep(3000);

            // Cycle 3: Very short 0.5s beep
            System.out.println("--- Cycle 3 (0.5s beep) ---");
            start();
            System.out.println("  >>> LISTEN 0.5s <<<");
            Thread.sleep(500);
            stop();
            System.out.println("  >>> 3s silence <<<");
            Thread.sleep(3000);

            // Cycle 4: 3 rapid beeps
            System.out.println("--- Cycle 4 (3 rapid beeps) ---");
            for (int i = 0; i < 3; i++) {
                start();
                Thread.sleep(300);
                stop();
                Thread.sleep(300);
            }
            System.out.println("  >>> 3s silence <<<");
            Thread.sleep(3000);

            // Cycle 5: Long 5s tone
            System.out.println("--- Cycle 5 (5s tone) ---");
            start();
            System.out.println("  >>> LISTEN 5s <<<");
            Thread.sleep(5000);
            stop();

            System.out.println("\n=== Done. Did all 5 cycles start AND stop properly? ===");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void start() throws Exception {
        si.invoke(mgr, D, 0xAA000148, 1); // PA
        si.invoke(mgr, D, 0xAA000142, 1); // MCU_SPEAK
        si.invoke(mgr, D, 0xAA00011A, 1); // FM_SPEAK
        si.invoke(mgr, D, 0xAA000104, 1); // TEST_AVAS
        si.invoke(mgr, D, 0xAA000171, 1); // AVAS_CFG
        si.invoke(mgr, D, 0xAA00011E, 0); // UNMUTE
        Thread.sleep(100);
        si.invoke(mgr, D, AVAH, 1);
    }

    static void stop() throws Exception {
        // Reset ALL enablers first
        si.invoke(mgr, D, 0xAA000148, 0);
        si.invoke(mgr, D, 0xAA000142, 0);
        si.invoke(mgr, D, 0xAA00011A, 0);
        si.invoke(mgr, D, 0xAA000104, 0);
        si.invoke(mgr, D, 0xAA000171, 0);
        // Then stop AVAH
        si.invoke(mgr, D, AVAH, 0);
    }
}
