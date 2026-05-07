import java.lang.reflect.Method;

public class BydAudioQuery {
    static final int DEV_AUDIO = 1002;
    static final int DEV_ENGINE = 1003;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            Object mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (mgr == null) { System.out.println("ERROR: null mgr"); return; }

            Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);

            if (args.length > 0 && args[0].equals("set")) {
                int dev = args.length > 3 ? Integer.parseInt(args[3]) : DEV_AUDIO;
                int fid = parseId(args[1]);
                int val = Integer.parseInt(args[2]);
                int r = (int) setInt.invoke(mgr, dev, fid, val);
                System.out.println("SET dev=" + dev + " fid=0x" + Integer.toHexString(fid) + " val=" + val + " result=" + r);
                return;
            }

            if (args.length > 0 && args[0].equals("get")) {
                int dev = args.length > 2 ? Integer.parseInt(args[2]) : DEV_AUDIO;
                int fid = parseId(args[1]);
                int val = (int) getInt.invoke(mgr, dev, fid);
                System.out.println("GET dev=" + dev + " fid=0x" + Integer.toHexString(fid) + " val=" + val);
                return;
            }

            // Full audio/sound property dump
            System.out.println("=== AVAS ===");
            p(getInt, mgr, DEV_AUDIO, 1281359917,  "AVAS_SOUND_SOURCE_STATE");
            p(getInt, mgr, DEV_AUDIO, 454033469,   "AVAS_SOUND_SOURCE_SET");
            p(getInt, mgr, DEV_AUDIO, -1728052894, "AVAS_SOURCE_TYPE");

            System.out.println("\n=== Exterior Speaker ===");
            // Try common offsets near known AVAS IDs
            // These are calculated from the CANFD static init pattern
            p(getInt, mgr, DEV_AUDIO, 1281359935, "EXTERIOR_SPEAKER_SWITCH_STATUS (est)");
            p(getInt, mgr, DEV_AUDIO, 454033477,  "EXTERIOR_SPEAKER_SWITCH_SET (est)");

            System.out.println("\n=== Lock Car Sound ===");
            // Search near known audio feature IDs
            p(getInt, mgr, DEV_AUDIO, 454033470,  "LOCK_CAR_SOUND_EFFECT (est1)");
            p(getInt, mgr, DEV_AUDIO, 454033471,  "LOCK_CAR_SOUND_EFFECT (est2)");

            System.out.println("\n=== Engine Simulator ===");
            p(getInt, mgr, DEV_ENGINE, 1223688208, "ENGINE_SIMULATOR_SOURCE_TYPE");
            p(getInt, mgr, DEV_ENGINE, 1043333176, "ENGINE_SIMULATOR_SOURCE_TYPE_SET");
            p(getInt, mgr, DEV_ENGINE, 1043333152, "ENGINE_VOICE_SIMULATOR_STATE_SET");

            System.out.println("\n=== CAR_CONFIG ===");
            p(getInt, mgr, DEV_AUDIO, -1728052894, "AVAS_SOURCE_TYPE (config)");

            System.out.println("\n=== Power-On Sound ===");
            // Try to read power on sound state
            p(getInt, mgr, DEV_AUDIO, 454033472,  "START_PLAY_POWER_ON (est1)");
            p(getInt, mgr, DEV_AUDIO, 454033473,  "START_PLAY_POWER_ON (est2)");

            System.out.println("\n=== ESS ===");
            p(getInt, mgr, DEV_AUDIO, 1339031600, "ESS_AMPLIFIER_CONFIG");

            System.out.println("\n=== ANC ===");
            p(getInt, mgr, DEV_AUDIO, 454033461,  "ANC_SOUND_SOURCE_SET");
            p(getInt, mgr, DEV_AUDIO, 1281359909, "ANC_SOUND_SOURCE_STATE");

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void p(Method m, Object mgr, int dev, int fid, String name) {
        try {
            int v = (int) m.invoke(mgr, dev, fid);
            System.out.println(name + " [0x" + Integer.toHexString(fid) + "] = " + v);
        } catch (Exception e) {
            System.out.println(name + " = ERR");
        }
    }

    static int parseId(String s) {
        if (s.startsWith("0x") || s.startsWith("0X"))
            return Integer.parseUnsignedInt(s.substring(2), 16);
        return Integer.parseInt(s);
    }
}
