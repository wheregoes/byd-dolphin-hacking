import java.lang.reflect.Method;

public class AvahBare {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            Object mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            Method setBuffer = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);

            System.out.println("=== Bare AVAH Test ===");
            System.out.println("No enabler commands - just AVAH alone.\n");

            // Test 1: setInt AVAH=1
            int r = (int) setInt.invoke(mgr, 1002, 0x6E970010, 1);
            System.out.println("Test 1: setInt(AVAH, 1) = " + (r == 0 ? "OK" : r));
            System.out.println(">>> LISTEN 4s for 1kHz <<<");
            Thread.sleep(4000);
            setInt.invoke(mgr, 1002, 0x6E970010, 0);
            Thread.sleep(500);

            // Test 2: setBuffer frequency
            byte[] f880 = {0x03, 0x70}; // 880 Hz
            r = (int) setBuffer.invoke(mgr, 1002, 0x6E970010, f880);
            System.out.println("\nTest 2: setBuffer(880Hz) = " + (r == 0 ? "OK" : r));
            System.out.println(">>> LISTEN 4s for 880Hz (different pitch) <<<");
            Thread.sleep(4000);
            setInt.invoke(mgr, 1002, 0x6E970010, 0);
            Thread.sleep(500);

            // Test 3: Quick melody to confirm frequency control
            System.out.println("\nTest 3: Quick melody (C-E-G-C)");
            int[] notes = {262, 330, 392, 523};
            for (int freq : notes) {
                byte[] buf = {(byte)((freq >> 8) & 0xFF), (byte)(freq & 0xFF)};
                setBuffer.invoke(mgr, 1002, 0x6E970010, buf);
                Thread.sleep(400);
            }
            setInt.invoke(mgr, 1002, 0x6E970010, 0);

            System.out.println("\n=== Done. ===");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
