import java.lang.reflect.Method;

public class AvahStop {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            Object mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            Method si = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            int D = 1002, AVAH = 0x6E970010;
            int r;

            String cmd = args.length > 0 ? args[0] : "stop";

            if (cmd.equals("start")) {
                System.out.println("=== Starting AVAH tone ===");
                // Set all enablers
                si.invoke(mgr, D, 0xAA000148, 1); // PA
                si.invoke(mgr, D, 0xAA000142, 1); // MCU_SPEAK
                si.invoke(mgr, D, 0xAA00011A, 1); // FM_SPEAK
                si.invoke(mgr, D, 0xAA000104, 1); // TEST_AVAS
                si.invoke(mgr, D, 0xAA000171, 1); // AVAS_CFG
                si.invoke(mgr, D, 0xAA00011E, 0); // UNMUTE
                Thread.sleep(500);
                si.invoke(mgr, D, AVAH, 1);
                System.out.println("Tone started. Listen outside.");
                System.out.println("Use 'stop' command to try stopping methods.");
                return;
            }

            System.out.println("=== Trying ALL stop methods ===\n");

            // Method 1: Standard AVAH=0
            r=(int)si.invoke(mgr, D, AVAH, 0);
            System.out.println("1. AVAH=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 2: Disable PA
            r=(int)si.invoke(mgr, D, 0xAA000148, 0);
            System.out.println("2. PA_CONTROL=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 3: Disable MCU_SPEAK
            r=(int)si.invoke(mgr, D, 0xAA000142, 0);
            System.out.println("3. MCU_SPEAK=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 4: Disable FM_SPEAK
            r=(int)si.invoke(mgr, D, 0xAA00011A, 0);
            System.out.println("4. FM_SPEAK=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 5: Disable TEST_AVAS
            r=(int)si.invoke(mgr, D, 0xAA000104, 0);
            System.out.println("5. TEST_AVAS=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 6: Disable AVAS_CFG
            r=(int)si.invoke(mgr, D, 0xAA000171, 0);
            System.out.println("6. AVAS_CFG=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 7: Mute channel
            r=(int)si.invoke(mgr, D, 0xAA00011E, 1);
            System.out.println("7. MUTE=1 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 8: AVAS source = 0
            r=(int)si.invoke(mgr, D, 0x1B10003D, 0);
            System.out.println("8. AVAS_SOURCE=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 9: DSP standby
            r=(int)si.invoke(mgr, D, 0xAA000113, 1);
            System.out.println("9. DSP_STANDBY=1 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 10: SOC control DSP = 0
            r=(int)si.invoke(mgr, D, 0xAA000145, 0);
            System.out.println("10. SOC_DSP=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 11: AVAH negative value
            r=(int)si.invoke(mgr, D, AVAH, -1);
            System.out.println("11. AVAH=-1 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 12: Debug AVAH = 0
            r=(int)si.invoke(mgr, D, 0x6E990010, 0);
            System.out.println("12. DBG_AVAH=0 -> "+(r==0?"OK":r));
            Thread.sleep(1000);

            // Method 13: Debug mode ON then AVAH=0
            si.invoke(mgr, D, 0x6E990008, 1);
            Thread.sleep(200);
            r=(int)si.invoke(mgr, D, AVAH, 0);
            System.out.println("13. DEBUG_ON + AVAH=0 -> "+(r==0?"OK":r));
            si.invoke(mgr, D, 0x6E990008, 0);

            System.out.println("\n=== Did the tone stop at any point? ===");
            System.out.println("Note which method number stopped it.");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
