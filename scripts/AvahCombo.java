import java.lang.reflect.Method;

public class AvahCombo {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            Object mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            Method si = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            Method sb = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);
            int D = 1002, AVAH = 0x6E970010;

            System.out.println("=== Full Combo Enabler Test ===\n");

            // Reset
            si.invoke(mgr, D, AVAH, 0);
            Thread.sleep(500);

            // Set ALL enablers
            System.out.println("Setting ALL enablers:");
            int r;
            r=(int)si.invoke(mgr, D, 0xAA000148, 1); System.out.println("  PA_CONTROL=1 -> "+(r==0?"OK":r));
            r=(int)si.invoke(mgr, D, 0xAA000142, 1); System.out.println("  MCU_SPEAK=1 -> "+(r==0?"OK":r));
            r=(int)si.invoke(mgr, D, 0xAA00011A, 1); System.out.println("  FM_SPEAK=1 -> "+(r==0?"OK":r));
            r=(int)si.invoke(mgr, D, 0xAA000104, 1); System.out.println("  TEST_AVAS=1 -> "+(r==0?"OK":r));
            r=(int)si.invoke(mgr, D, 0xAA000171, 1); System.out.println("  AVAS_CFG=1 -> "+(r==0?"OK":r));
            r=(int)si.invoke(mgr, D, 0xAA00011E, 0); System.out.println("  UNMUTE=0 -> "+(r==0?"OK":r));
            Thread.sleep(500);

            // Play 1kHz
            r=(int)si.invoke(mgr, D, AVAH, 1); System.out.println("\nAVAH=1 -> "+(r==0?"OK":r));
            System.out.println(">>> LISTEN 8s for 1kHz tone <<<");
            Thread.sleep(8000);
            si.invoke(mgr, D, AVAH, 0);
            Thread.sleep(500);

            // Play 440Hz via setBuffer
            byte[] f440 = {0x01, (byte)0xB8};
            r=(int)sb.invoke(mgr, D, AVAH, f440); System.out.println("setBuffer(440Hz) -> "+(r==0?"OK":r));
            System.out.println(">>> LISTEN 8s for 440Hz <<<");
            Thread.sleep(8000);
            si.invoke(mgr, D, AVAH, 0);
            Thread.sleep(500);

            // Siren
            System.out.println(">>> SIREN (10s) <<<");
            for (int c = 0; c < 4; c++) {
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
            si.invoke(mgr, D, AVAH, 0);

            // Leave all enablers ON
            System.out.println("\n=== Done. All enablers left ON. ===");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
