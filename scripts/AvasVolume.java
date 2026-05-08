import java.lang.reflect.Method;

public class AvasVolume {
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

            String test = args.length > 0 ? args[0] : "all";

            if (test.equals("fm_vol") || test.equals("all")) testFmVolume();
            if (test.equals("pa_gain") || test.equals("all")) testPaGain();
            if (test.equals("enabler_gain") || test.equals("all")) testEnablerGain();
            if (test.equals("combined") || test.equals("all")) testCombinedMax();

            System.out.println("\n=== All tests done ===");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    // Test 1: AUDIO_SUB_FM_VOLUME_SET (0xAA000156) - confirmed working signal
    static void testFmVolume() throws Exception {
        System.out.println("\n=== TEST 1: FM Volume (0xAA000156) ===");
        System.out.println("Baseline first (no FM vol set)...");
        playTone(2000);
        Thread.sleep(2000);

        int[] volumes = {5, 10, 15, 20, 30, 50, 100};
        for (int vol : volumes) {
            System.out.println("FM_VOLUME=" + vol + " >>> LISTEN <<<");
            set(0xAA000156, vol);
            Thread.sleep(200);
            playTone(2000);
            Thread.sleep(1500);
        }
        set(0xAA000156, 0);
        System.out.println("FM volume test done.\n");
    }

    // Test 2: PA_CONTROL_SET (0xAA000148) with values > 1
    static void testPaGain() throws Exception {
        System.out.println("\n=== TEST 2: PA Control Gain (0xAA000148) ===");
        System.out.println("PA values: 1 (baseline), then 2, 3, 5, 10, 15");

        int[] paVals = {1, 2, 3, 5, 10, 15};
        for (int pa : paVals) {
            System.out.println("PA=" + pa + " >>> LISTEN <<<");
            set(0xAA000148, pa);
            set(0xAA000142, 1);
            set(0xAA00011A, 1);
            set(0xAA000104, 1);
            set(0xAA000171, 1);
            set(0xAA00011E, 0);
            Thread.sleep(100);
            set(AVAH, 1);
            Thread.sleep(2000);
            stop();
            Thread.sleep(1500);
        }
        System.out.println("PA gain test done.\n");
    }

    // Test 3: Each enabler with higher values
    static void testEnablerGain() throws Exception {
        System.out.println("\n=== TEST 3: Enabler Gain Values ===");

        // 3a: MCU_SPEAK (0xAA000142) values
        System.out.println("--- MCU_SPEAK values ---");
        for (int v : new int[]{1, 5, 10, 15}) {
            System.out.println("MCU_SPEAK=" + v + " >>> LISTEN <<<");
            set(0xAA000148, 1);
            set(0xAA000142, v);
            set(0xAA00011A, 1);
            set(0xAA000104, 1);
            set(0xAA000171, 1);
            set(0xAA00011E, 0);
            Thread.sleep(100);
            set(AVAH, 1);
            Thread.sleep(2000);
            stop();
            Thread.sleep(1500);
        }

        // 3b: FM_SPEAK (0xAA00011A) values
        System.out.println("--- FM_SPEAK values ---");
        for (int v : new int[]{1, 5, 10, 15}) {
            System.out.println("FM_SPEAK=" + v + " >>> LISTEN <<<");
            set(0xAA000148, 1);
            set(0xAA000142, 1);
            set(0xAA00011A, v);
            set(0xAA000104, 1);
            set(0xAA000171, 1);
            set(0xAA00011E, 0);
            Thread.sleep(100);
            set(AVAH, 1);
            Thread.sleep(2000);
            stop();
            Thread.sleep(1500);
        }

        // 3c: AVAS_CFG (0xAA000171) values
        System.out.println("--- AVAS_CFG values ---");
        for (int v : new int[]{1, 2, 3, 5, 10}) {
            System.out.println("AVAS_CFG=" + v + " >>> LISTEN <<<");
            set(0xAA000148, 1);
            set(0xAA000142, 1);
            set(0xAA00011A, 1);
            set(0xAA000104, 1);
            set(0xAA000171, v);
            set(0xAA00011E, 0);
            Thread.sleep(100);
            set(AVAH, 1);
            Thread.sleep(2000);
            stop();
            Thread.sleep(1500);
        }
        System.out.println("Enabler gain test done.\n");
    }

    // Test 4: All enablers at max + FM volume
    static void testCombinedMax() throws Exception {
        System.out.println("\n=== TEST 4: Combined Maximum ===");

        System.out.println("Baseline (all enablers=1)...");
        playTone(2000);
        Thread.sleep(2000);

        System.out.println("All enablers=15 + FM_VOL=100 >>> LISTEN <<<");
        set(0xAA000156, 100);
        set(0xAA000148, 15);
        set(0xAA000142, 15);
        set(0xAA00011A, 15);
        set(0xAA000104, 1);  // keep pitch at 1
        set(0xAA000171, 15);
        set(0xAA00011E, 0);
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(3000);
        stop();
        set(0xAA000156, 0);

        System.out.println("Combined max test done.\n");
    }

    static void playTone(int ms) throws Exception {
        set(0xAA000148, 1);
        set(0xAA000142, 1);
        set(0xAA00011A, 1);
        set(0xAA000104, 1);
        set(0xAA000171, 1);
        set(0xAA00011E, 0);
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(ms);
        stop();
    }

    static void stop() throws Exception {
        set(0xAA000148, 0);
        set(0xAA000142, 0);
        set(0xAA00011A, 0);
        set(0xAA000104, 0);
        set(0xAA000171, 0);
        set(AVAH, 0);
    }

    static void set(int fid, int val) {
        try { si.invoke(mgr, D, fid, val); } catch (Exception e) {}
    }
}
