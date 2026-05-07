import java.lang.reflect.Method;

public class AvahFreq {
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

            System.out.println("=== AVAH Frequency Control Test ===\n");

            // Enable all
            enable();

            // Test 1: setInt values 1, 2, 3 (known 1/2/3 kHz)
            System.out.println("--- Test 1: setInt tones ---");
            for (int v = 1; v <= 3; v++) {
                si.invoke(mgr, D, AVAH, v);
                System.out.println("  setInt=" + v + " >>> LISTEN 2s <<<");
                Thread.sleep(2000);
                si.invoke(mgr, D, AVAH, 0);
                Thread.sleep(300);
            }

            // Test 2: setBuffer frequency (2 bytes, big-endian Hz)
            System.out.println("\n--- Test 2: setBuffer frequencies ---");
            int[] freqs = {200, 300, 440, 500, 660, 880, 1000, 1500, 2000, 3000};
            for (int f : freqs) {
                byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
                int r = (int) sb.invoke(mgr, D, AVAH, buf);
                System.out.println("  " + f + "Hz -> " + (r == 0 ? "OK" : r) + " >>> LISTEN 1.5s <<<");
                Thread.sleep(1500);
            }
            si.invoke(mgr, D, AVAH, 0);
            Thread.sleep(500);

            // Test 3: Siren sweep
            System.out.println("\n--- Test 3: Siren sweep ---");
            enable();
            Thread.sleep(100);
            si.invoke(mgr, D, AVAH, 1); // start with setInt first
            Thread.sleep(500);
            System.out.println("  >>> LISTEN for siren <<<");
            for (int c = 0; c < 2; c++) {
                for (int f = 300; f <= 2000; f += 25) {
                    byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
                    sb.invoke(mgr, D, AVAH, buf);
                    Thread.sleep(8);
                }
                for (int f = 2000; f >= 300; f -= 25) {
                    byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
                    sb.invoke(mgr, D, AVAH, buf);
                    Thread.sleep(8);
                }
            }

            // Test 4: Musical scale
            System.out.println("\n--- Test 4: Musical scale C4-C5 ---");
            int[] scale = {262, 294, 330, 349, 392, 440, 494, 523};
            for (int f : scale) {
                byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
                sb.invoke(mgr, D, AVAH, buf);
                System.out.println("  " + f + "Hz");
                Thread.sleep(400);
                si.invoke(mgr, D, AVAH, 0);
                Thread.sleep(50);
            }

            // Stop everything
            disable();
            si.invoke(mgr, D, AVAH, 0);

            System.out.println("\n=== Done. ===");
            System.out.println("Tests: 1=setInt tones, 2=setBuffer freqs, 3=siren, 4=scale");
            System.out.println("Which tests produced sound?");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void enable() throws Exception {
        si.invoke(mgr, D, 0xAA000148, 1);
        si.invoke(mgr, D, 0xAA000142, 1);
        si.invoke(mgr, D, 0xAA00011A, 1);
        si.invoke(mgr, D, 0xAA000104, 1);
        si.invoke(mgr, D, 0xAA000171, 1);
        si.invoke(mgr, D, 0xAA00011E, 0);
    }

    static void disable() throws Exception {
        si.invoke(mgr, D, 0xAA000148, 0);
        si.invoke(mgr, D, 0xAA000142, 0);
        si.invoke(mgr, D, 0xAA00011A, 0);
        si.invoke(mgr, D, 0xAA000104, 0);
        si.invoke(mgr, D, 0xAA000171, 0);
    }
}
