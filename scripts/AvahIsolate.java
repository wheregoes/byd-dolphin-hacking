import java.lang.reflect.Method;

public class AvahIsolate {
    static Object mgr;
    static Method setInt;
    static final int D = 1002;
    static final int AVAH = 0x6E970010;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            System.out.println("=== AVAH Enabler Isolation ===");
            System.out.println("Testing each command individually.\n");

            // First reset everything
            reset();
            Thread.sleep(500);

            // Test A: PA CONTROL (0xAA000148)
            test("A", "PA_CONTROL", 0xAA000148, new int[]{1,2,3});

            // Test B: MCU SPEAK (0xAA000142)
            test("B", "MCU_SPEAK", 0xAA000142, new int[]{1,2,3});

            // Test C: FM SPEAK (0xAA00011A)
            test("C", "FM_SPEAK", 0xAA00011A, new int[]{1,2,3});

            // Test D: TEST_AUDIO_AVAS (0xAA000104)
            test("D", "TEST_AVAS", 0xAA000104, new int[]{1,2,3});

            // Test E: TEST_MCU_AVAS_CONFIG (0xAA000171)
            test("E", "AVAS_CFG", 0xAA000171, new int[]{1,2,3});

            // Test F: CHANNEL MUTE (0xAA00011E)
            test("F", "CHANNEL_UNMUTE", 0xAA00011E, new int[]{0,1});

            // Cleanup
            reset();
            System.out.println("\n=== Done. Which test letter produced sound? ===");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void reset() throws Exception {
        setInt.invoke(mgr, D, AVAH, 0);
        setInt.invoke(mgr, D, 0xAA000148, 0);
        setInt.invoke(mgr, D, 0xAA000142, 0);
        setInt.invoke(mgr, D, 0xAA00011A, 0);
        setInt.invoke(mgr, D, 0xAA000104, 0);
        setInt.invoke(mgr, D, 0xAA000171, 0);
        setInt.invoke(mgr, D, 0xAA00011E, 0);
    }

    static void test(String id, String name, int enablerFid, int[] vals) throws Exception {
        for (int val : vals) {
            // Reset everything first
            reset();
            Thread.sleep(300);

            // Set ONLY this enabler
            int r = (int) setInt.invoke(mgr, D, enablerFid, val);
            Thread.sleep(200);

            // Try AVAH
            setInt.invoke(mgr, D, AVAH, 1);
            System.out.println("Test " + id + ": " + name + "=" + val
                + " (set=" + (r == 0 ? "OK" : r) + ") + AVAH=1 >>> LISTEN 3s <<<");
            Thread.sleep(3000);
            setInt.invoke(mgr, D, AVAH, 0);
            Thread.sleep(300);
        }
        System.out.println();
    }
}
