import java.lang.reflect.Method;

public class AvasVolume2 {
    static Object mgr;
    static Method si, sb;
    static int D = 1002, AVAH = 0x6E970010;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            si = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            sb = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);

            String test = args.length > 0 ? args[0] : "avah_vals";

            if (test.equals("avah_vals")) testAvahValues();
            if (test.equals("buf_vol")) testBufferVolume();
            if (test.equals("config")) readConfig();
            if (test.equals("presets")) testPresets();

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    // Test different AVAH values - maybe higher values = louder
    static void testAvahValues() throws Exception {
        System.out.println("=== AVAH Value Volume Test ===");
        System.out.println("Testing if different AVAH values produce different volumes");

        int[] vals = {1, 2, 3, 4, 5, 10, 50, 100, 200, 255};
        for (int v : vals) {
            System.out.println("AVAH=" + v + " >>> LISTEN <<<");
            enable();
            Thread.sleep(100);
            set(AVAH, v);
            Thread.sleep(2500);
            stop();
            Thread.sleep(1500);
        }
        System.out.println("=== AVAH value test done ===");
    }

    // Test setBuffer on AVAH with volume-encoded data
    static void testBufferVolume() throws Exception {
        System.out.println("=== setBuffer Volume Encoding Test ===");

        System.out.println("Baseline (setInt only)...");
        enable();
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(2000);
        stop();
        Thread.sleep(1500);

        // Try setBuffer with volume byte before starting tone
        byte[][] bufs = {
            {(byte)0xFF},                              // max single byte
            {0x01, (byte)0xFF},                        // cmd + vol
            {0x01, 0x00, (byte)0x64},                 // tone=1, 0, vol=100
            {0x01, (byte)0x0F},                        // tone=1, gain=15
            {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF}, // all max
            {0x01, 0x00, 0x00, (byte)0x0F},           // tone, 0, 0, vol
        };
        String[] desc = {
            "buf=[0xFF]",
            "buf=[0x01,0xFF]",
            "buf=[0x01,0x00,0x64]",
            "buf=[0x01,0x0F]",
            "buf=[FF,FF,FF,FF]",
            "buf=[01,00,00,0F]",
        };

        for (int i = 0; i < bufs.length; i++) {
            System.out.println(desc[i] + " >>> LISTEN <<<");
            enable();
            Thread.sleep(50);
            try { sb.invoke(mgr, D, AVAH, bufs[i]); } catch (Exception e) {}
            Thread.sleep(50);
            set(AVAH, 1);
            Thread.sleep(2000);
            stop();
            Thread.sleep(1500);
        }

        // Also try setBuffer AFTER tone starts
        System.out.println("setBuffer AFTER tone starts...");
        enable();
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(500);
        System.out.println("buf=[0xFF] mid-tone >>> LISTEN for change <<<");
        try { sb.invoke(mgr, D, AVAH, new byte[]{(byte)0xFF}); } catch (Exception e) {}
        Thread.sleep(2000);
        stop();
        Thread.sleep(1000);

        System.out.println("=== Buffer volume test done ===");
    }

    // Read speaker config signals
    static void readConfig() throws Exception {
        System.out.println("=== Reading AVAS/Speaker Config ===");
        Method gi = mgr.getClass().getMethod("getInt", int.class, int.class);
        Method gb = mgr.getClass().getMethod("getBuffer", int.class, int.class);

        int[][] reads = {
            {0x35201036, D},   // EXTERIOR_SPEAKER_CONFIG
            {0x35201040, D},   // EXTERIOR_SPEAKER_SWITCH_STATUS
            {0x35203032, D},   // AVAS_TO_EXT_SPEAKER_STATUS
            {0x4C60002D, D},   // AVAS_SOUND_SOURCE_STATE
            {0x99000162, D},   // AVAS_SOURCE_TYPE
            {0x35201042, D},   // AVAS_FAULT_STATUS
            {0x6EA70010, D},   // AVAH state readback
        };
        String[] names = {
            "EXT_SPEAKER_CONFIG", "EXT_SPEAKER_SWITCH", "AVAS_TO_EXT",
            "AVAS_SOURCE_STATE", "AVAS_SOURCE_TYPE", "AVAS_FAULT", "AVAH_STATE",
        };

        for (int i = 0; i < reads.length; i++) {
            try {
                int val = (int) gi.invoke(mgr, reads[i][1], reads[i][0]);
                System.out.println(names[i] + " [0x" + Integer.toHexString(reads[i][0]) + "] = " + val);
            } catch (Exception e) {
                System.out.println(names[i] + " ERROR: " + e.getMessage());
            }
        }

        // Try getBuffer on config signals
        System.out.println("\n--- Buffer reads ---");
        int[] bufReads = {0x35201036, 0x99000162, 0x6EA70010};
        String[] bufNames = {"EXT_SPEAKER_CONFIG", "AVAS_SOURCE_TYPE", "AVAH_STATE"};
        for (int i = 0; i < bufReads.length; i++) {
            try {
                byte[] buf = (byte[]) gb.invoke(mgr, D, bufReads[i]);
                if (buf != null && buf.length > 0) {
                    StringBuilder sb2 = new StringBuilder();
                    for (byte b : buf) sb2.append(String.format("%02X ", b));
                    System.out.println(bufNames[i] + " buf[" + buf.length + "]: " + sb2.toString().trim());
                } else {
                    System.out.println(bufNames[i] + " buf: null/empty");
                }
            } catch (Exception e) {
                System.out.println(bufNames[i] + " buf ERROR: " + e.getMessage());
            }
        }
        System.out.println("=== Config read done ===");
    }

    // Test AVAS presets combined with AVAH tone
    static void testPresets() throws Exception {
        System.out.println("=== AVAS Preset + AVAH Volume Test ===");
        System.out.println("Different AVAS presets might set different amplifier gain");

        int[] presets = {0, 1, 2, 3, 4, 5};
        for (int p : presets) {
            System.out.println("AVAS_SOURCE=" + p + " + AVAH >>> LISTEN <<<");
            set(0x1B10003D, p);  // set AVAS preset
            Thread.sleep(200);
            enable();
            Thread.sleep(100);
            set(AVAH, 1);
            Thread.sleep(2500);
            stop();
            Thread.sleep(1500);
        }
        set(0x1B10003D, 0);  // reset preset
        System.out.println("=== Preset test done ===");
    }

    static void enable() {
        set(0xAA000148, 1);
        set(0xAA000142, 1);
        set(0xAA00011A, 1);
        set(0xAA000104, 1);
        set(0xAA000171, 1);
        set(0xAA00011E, 0);
    }

    static void stop() {
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
