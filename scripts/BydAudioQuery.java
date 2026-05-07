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

            // IDs from com.byd.feature.audio.Audio and com.byd.feature.test.Test

            System.out.println("=== AVAS (read) ===");
            p(getInt, mgr, DEV_AUDIO, 0x4C60002D, "AVAS_SOUND_SOURCE_STATE");
            p(getInt, mgr, DEV_AUDIO, 0x99000162, "AVAS_SOURCE_TYPE");
            p(getInt, mgr, DEV_AUDIO, 0x35201042, "AVAS_FAULT_STATUS");

            System.out.println("\n=== AVAS -> External Speaker ===");
            p(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_EXT_SPEAKER_STATUS");

            System.out.println("\n=== Exterior Speaker ===");
            p(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_SWITCH_STATUS");
            p(getInt, mgr, DEV_AUDIO, 0x35201036, "EXT_SPEAKER_CONFIG");
            p(getInt, mgr, DEV_AUDIO, 0x3520103F, "EXT_PROMPT_TONE_STATUS");

            System.out.println("\n=== Engine Simulator ===");
            p(getInt, mgr, DEV_ENGINE, 0x48F00010, "ENGINE_SIM_SOURCE_TYPE");

            System.out.println("\n=== ESS / ANC ===");
            p(getInt, mgr, DEV_AUDIO, 0x4FD00030, "ESS_AMPLIFIER_CONFIG");
            p(getInt, mgr, DEV_AUDIO, 0x4C600025, "ANC_SOUND_SOURCE_STATE");

            System.out.println("\n=== Test/Diagnostic ===");
            p(getInt, mgr, DEV_AUDIO, 0x6EA70010, "TEST_AUDIO_AVAH");

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
            System.out.println(name + " [0x" + Integer.toHexString(fid) + "] = ERR: " + e.getCause());
        }
    }

    static int parseId(String s) {
        if (s.startsWith("0x") || s.startsWith("0X"))
            return Integer.parseUnsignedInt(s.substring(2), 16);
        return Integer.parseInt(s);
    }
}
