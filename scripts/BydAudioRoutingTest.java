import java.lang.reflect.Method;

/**
 * Comprehensive audio routing test — attempts every known path to route
 * audio to the AVAS external speaker.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydroute scripts/BydAudioRoutingTest.java
 *   d8 --output /tmp/bydroute /tmp/bydroute/BydAudioRoutingTest.class
 *   adb push /tmp/bydroute/classes.dex /data/local/tmp/bydroute.dex
 *
 *   # Full diagnostic read (safe, no writes):
 *   adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest diag"
 *
 *   # AVAH test tones (plays 1/2/3 kHz on AVAS speaker):
 *   adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest avah 1"
 *   adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest avah 0"  # stop
 *
 *   # Test external speaker enable + source routing:
 *   adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest route"
 *
 *   # Try loopback / DSP passthrough:
 *   adb shell "CLASSPATH=/data/local/tmp/bydroute.dex app_process /data/local/tmp BydAudioRoutingTest loopback"
 */
public class BydAudioRoutingTest {
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
            Method getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);

            String cmd = args.length > 0 ? args[0] : "diag";

            switch (cmd) {
                case "diag":
                    runDiagnostics(getInt, getBuffer, mgr);
                    break;
                case "avah":
                    int tone = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    runAvahTest(setInt, getInt, mgr, tone);
                    break;
                case "route":
                    runRouteTest(setInt, getInt, mgr);
                    break;
                case "loopback":
                    runLoopbackTest(setInt, getInt, mgr);
                    break;
                case "combo":
                    runComboAttack(setInt, getInt, mgr);
                    break;
                default:
                    System.out.println("Commands: diag, avah [0-3], route, loopback, combo");
            }

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void runDiagnostics(Method getInt, Method getBuffer, Object mgr) {
        System.out.println("========================================");
        System.out.println("   AVAS / External Speaker Diagnostics");
        System.out.println("========================================\n");

        System.out.println("--- Hardware Info ---");
        p(getInt, mgr, DEV_AUDIO, 0x99000215, "DSP_TYPE");
        p(getInt, mgr, DEV_AUDIO, 0x99000364, "DSP_READY");
        p(getInt, mgr, DEV_AUDIO, 0x99000214, "AMPLIFIER_TYPE");
        p(getInt, mgr, DEV_AUDIO, 0x12020005, "CAR_CONFIG_ITEM_AVAS_AUDIO");
        p(getInt, mgr, DEV_AUDIO, 0x99000266, "SUPPORT_VARIABLE_SOUND_SOURCE");

        System.out.println("\n--- AVAS State ---");
        p(getInt, mgr, DEV_AUDIO, 0x4C60002D, "AVAS_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x99000162, "AVAS_SOURCE_TYPE");
        p(getInt, mgr, DEV_AUDIO, 0x35201042, "AVAS_FAULT_STATUS");

        System.out.println("\n--- External Speaker Routing ---");
        p(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_TO_EXT_SPEAKER_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_SWITCH_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35201036, "EXT_SPEAKER_CONFIG");
        p(getInt, mgr, DEV_AUDIO, 0x3520103F, "EXT_PROMPT_TONE_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x3520103E, "3D_PROMPT_TONE_STATUS");

        System.out.println("\n--- Amplifier / A2B Status ---");
        p(getInt, mgr, DEV_AUDIO, 0x4FD00030, "AMPLIFIER_CONFIG");
        p(getInt, mgr, DEV_AUDIO, 0x4FD00040, "ESS_AMPLIFIER_CONFIG");
        p(getInt, mgr, DEV_AUDIO, 0x4FD00045, "ANC_AMPLIFIER_CONFIG");
        p(getInt, mgr, DEV_AUDIO, 0x99000246, "FAULT_TYPE_PA");
        p(getInt, mgr, DEV_AUDIO, 0x99000247, "FAULT_TYPE_A2B");
        p(getInt, mgr, DEV_AUDIO, 0x99000248, "FAULT_TYPE_DSP");
        p(getInt, mgr, DEV_AUDIO, 0x99000249, "FAULT_TYPE_PAD_INIT");
        p(getInt, mgr, DEV_AUDIO, 0x4AB00044, "MAIN_AMP_HANDSHAKE");
        p(getInt, mgr, DEV_AUDIO, 0x4AB00046, "SECONDARY_AMP_HANDSHAKE");

        System.out.println("\n--- Sound Sources ---");
        p(getInt, mgr, DEV_AUDIO, 0x4C60000C, "MEDIA_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4C600015, "RADAR_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4C60001D, "NAVI_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4C600025, "ANC_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4C60002D, "AVAS_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4C600035, "INS_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x4FD0001E, "BD_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x35201010, "KEY_SOUND_SOURCE_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35202028, "UE_BROADCAST_STATUS");

        System.out.println("\n--- AVAH Test ---");
        p(getInt, mgr, DEV_AUDIO, 0x6EA70010, "TEST_AUDIO_AVAH");

        System.out.println("\n--- Engine Simulator ---");
        p(getInt, mgr, DEV_ENGINE, 0x48F00010, "ENGINE_SIM_SOURCE_TYPE");
        p(getInt, mgr, DEV_AUDIO, 0x4FD0003A, "ESS_SETTING_STATUS");

        System.out.println("\n--- Prompt Volume ---");
        p(getInt, mgr, DEV_AUDIO, 0x99000307, "PROMPT_VOLUME_STATUS");

        System.out.println("\n--- Vehicle Prompt Sound Source (buffer) ---");
        pb(getBuffer, mgr, DEV_AUDIO, 0x99000194, "SOUND_SOURCE_INFO");
    }

    static void runAvahTest(Method setInt, Method getInt, Object mgr, int tone) {
        System.out.println("=== AVAH Test Tone ===");
        System.out.println("Playing tone " + tone + " on AVAS speaker");
        System.out.println("Values: 0=stop, 1=1kHz, 2=2kHz, 3=3kHz");

        p(getInt, mgr, DEV_AUDIO, 0x6EA70010, "AVAH_STATE (before)");

        // TEST_CMD_TEST_AUDIO_AVAH_SET (0x6E970010)
        int r = s(setInt, mgr, DEV_AUDIO, 0x6E970010, tone, "AVAH_SET");
        System.out.println("Result: " + resultStr(r));

        try { Thread.sleep(500); } catch (Exception ignored) {}
        p(getInt, mgr, DEV_AUDIO, 0x6EA70010, "AVAH_STATE (after)");

        if (tone > 0) {
            System.out.println("\nListening? If you hear a tone from the AVAS speaker, the hardware path works!");
            System.out.println("Stop with: BydAudioRoutingTest avah 0");
        }
    }

    static void runRouteTest(Method setInt, Method getInt, Object mgr) {
        System.out.println("=== External Speaker Routing Test ===");
        System.out.println("Attempting to enable exterior speaker and route audio to it.\n");

        // Read current state
        System.out.println("--- Before ---");
        p(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_SWITCH_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_TO_EXT_SPEAKER_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x3520103F, "EXT_PROMPT_TONE_STATUS");

        // Step 1: Enable exterior speaker switch
        System.out.println("\nStep 1: Enable exterior speaker (0x1C10000E = 1)");
        s(setInt, mgr, DEV_AUDIO, 0x1C10000E, 1, "EXT_SPEAKER_SWITCH_SET");

        // Step 2: Set exterior prompt tone source
        System.out.println("\nStep 2: Set exterior prompt tone source");
        for (int v = 0; v <= 3; v++) {
            int r = s(setInt, mgr, DEV_AUDIO, 0x1B100043, v, "EXT_PROMPT_TONE_SOURCE_SET val=" + v);
            if (r == 0) System.out.println("  >>> VALUE " + v + " ACCEPTED! <<<");
        }

        // Step 3: Route AVAS audio to external speaker
        System.out.println("\nStep 3: Route audio source to external speaker");
        for (int v = 0; v <= 3; v++) {
            int r = s(setInt, mgr, DEV_AUDIO, 0x32B1C042, v, "AVAS_TO_EXT_SPEAKER_SET val=" + v);
            if (r == 0) System.out.println("  >>> VALUE " + v + " ACCEPTED! <<<");
        }

        // Step 4: Set AVAS source
        System.out.println("\nStep 4: Try different AVAS sources");
        for (int v = 0; v <= 5; v++) {
            int r = s(setInt, mgr, DEV_AUDIO, 0x1B10003D, v, "AVAS_SOURCE_SET val=" + v);
            if (r == 0) System.out.println("  >>> VALUE " + v + " ACCEPTED! <<<");
        }

        // Step 5: Set 3D prompt tone source
        System.out.println("\nStep 5: Set 3D prompt tone source");
        for (int v = 0; v <= 3; v++) {
            int r = s(setInt, mgr, DEV_AUDIO, 0x1B100042, v, "3D_PROMPT_TONE_SOURCE_SET val=" + v);
            if (r == 0) System.out.println("  >>> VALUE " + v + " ACCEPTED! <<<");
        }

        // Read state after
        System.out.println("\n--- After ---");
        p(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_SWITCH_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_TO_EXT_SPEAKER_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x3520103F, "EXT_PROMPT_TONE_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x4C60002D, "AVAS_SOUND_SOURCE_STATE");
    }

    static void runLoopbackTest(Method setInt, Method getInt, Object mgr) {
        System.out.println("=== Loopback / DSP Passthrough Test ===\n");

        // Loopback passage control
        System.out.println("Test 1: Main driver loopback passage control");
        for (int v = 0; v <= 2; v++) {
            s(setInt, mgr, DEV_AUDIO, 0xAA000301, v, "LOOPBACK_PASSAGE_CONTROL val=" + v);
        }

        // DSP control
        System.out.println("\nTest 2: SOC notify MCU control DSP");
        for (int v = 0; v <= 3; v++) {
            s(setInt, mgr, DEV_AUDIO, 0xAA000145, v, "SOC_NOTIFY_MCU_CONTROL_DSP val=" + v);
        }

        // DSP standby
        System.out.println("\nTest 3: DSP standby state");
        for (int v = 0; v <= 1; v++) {
            s(setInt, mgr, DEV_AUDIO, 0xAA000113, v, "DSP_STANDBY_STATE val=" + v);
        }

        // Audio output device change
        System.out.println("\nTest 4: Audio output device change");
        for (int v = 0; v <= 3; v++) {
            s(setInt, mgr, DEV_AUDIO, 0xAA000250, v, "OUT_DEVICE_CHANGE val=" + v);
        }

        // DPIN output
        System.out.println("\nTest 5: DPIN output status");
        for (int v = 0; v <= 1; v++) {
            s(setInt, mgr, DEV_AUDIO, 0xAA000264, v, "DPIN_OUTPUT val=" + v);
        }

        // Non-branded amp UE controls
        System.out.println("\nTest 6: Non-branded amp UE (external?) controls");
        s(setInt, mgr, DEV_AUDIO, 0xAA000346, 0, "UE_MUTE_SET val=0 (unmute)");
        s(setInt, mgr, DEV_AUDIO, 0xAA000332, 50, "UE_VOLUME_SET val=50");
    }

    static void runComboAttack(Method setInt, Method getInt, Object mgr) {
        System.out.println("=== Combination Attack ===");
        System.out.println("Trying multiple signals in sequence to enable external speaker routing.\n");

        // Phase 1: Enable everything
        System.out.println("Phase 1: Enable exterior speaker");
        s(setInt, mgr, DEV_AUDIO, 0x1C10000E, 1, "EXT_SPEAKER_SWITCH = ON");

        System.out.println("\nPhase 2: Wake up DSP");
        s(setInt, mgr, DEV_AUDIO, 0xAA000113, 0, "DSP_STANDBY = OFF");
        s(setInt, mgr, DEV_AUDIO, 0xAA000145, 1, "SOC_CONTROL_DSP = 1");

        System.out.println("\nPhase 3: Enable loopback");
        s(setInt, mgr, DEV_AUDIO, 0xAA000301, 1, "LOOPBACK = ON");

        System.out.println("\nPhase 4: Unmute UE channel");
        s(setInt, mgr, DEV_AUDIO, 0xAA000346, 0, "UE_MUTE = OFF");
        s(setInt, mgr, DEV_AUDIO, 0xAA000332, 80, "UE_VOLUME = 80");

        System.out.println("\nPhase 5: Route AVAS to external speaker");
        s(setInt, mgr, DEV_AUDIO, 0x32B1C042, 1, "AVAS_TO_EXT_SPEAKER = 1");

        System.out.println("\nPhase 6: Set AVAS source");
        s(setInt, mgr, DEV_AUDIO, 0x1B10003D, 1, "AVAS_SOURCE = 1");

        System.out.println("\nPhase 7: Set exterior prompt tone");
        s(setInt, mgr, DEV_AUDIO, 0x1B100043, 1, "EXT_PROMPT_TONE_SOURCE = 1");

        System.out.println("\nPhase 8: Play AVAH test tone to verify");
        s(setInt, mgr, DEV_AUDIO, 0x6E970010, 1, "AVAH_TEST_1KHZ");

        System.out.println("\n--- Result check ---");
        p(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_SWITCH_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_TO_EXT_SPEAKER_STATUS");
        p(getInt, mgr, DEV_AUDIO, 0x4C60002D, "AVAS_SOUND_SOURCE_STATE");
        p(getInt, mgr, DEV_AUDIO, 0x6EA70010, "AVAH_STATE");

        System.out.println("\nListening? Check for:");
        System.out.println("  1. A 1kHz tone from the AVAS/external speaker");
        System.out.println("  2. Any change in cabin speaker behavior");
        System.out.println("\nTo stop AVAH tone: BydAudioRoutingTest avah 0");
    }

    static void p(Method m, Object mgr, int dev, int fid, String name) {
        try {
            int v = (int) m.invoke(mgr, dev, fid);
            System.out.println("  " + name + " [0x" + Integer.toHexString(fid) + "] = " + v + resultHint(v));
        } catch (Exception e) {
            System.out.println("  " + name + " [0x" + Integer.toHexString(fid) + "] = ERR: " + e.getCause());
        }
    }

    static void pb(Method m, Object mgr, int dev, int fid, String name) {
        try {
            byte[] buf = (byte[]) m.invoke(mgr, dev, fid);
            if (buf == null) { System.out.println("  " + name + " = null"); return; }
            StringBuilder hex = new StringBuilder();
            for (byte b : buf) hex.append(String.format("%02x", b));
            System.out.println("  " + name + " [0x" + Integer.toHexString(fid) + "] = hex:" + hex + " len=" + buf.length);
        } catch (Exception e) {
            System.out.println("  " + name + " [0x" + Integer.toHexString(fid) + "] = ERR: " + e.getCause());
        }
    }

    static int s(Method m, Object mgr, int dev, int fid, int val, String name) {
        try {
            int r = (int) m.invoke(mgr, dev, fid, val);
            System.out.println("  SET " + name + " [0x" + Integer.toHexString(fid) + "] = " + resultStr(r));
            return r;
        } catch (Exception e) {
            System.out.println("  SET " + name + " [0x" + Integer.toHexString(fid) + "] = ERR: " + e.getCause());
            return -99999;
        }
    }

    static String resultStr(int r) {
        switch (r) {
            case 0: return "SUCCESS";
            case -10011: return "NOT_REGISTERED (-10011)";
            case -10013: return "NOT_AVAILABLE (-10013)";
            case -2147482648: return "MCU_FAILED (-2147482648)";
            case -2147482647: return "MCU_BUSY (-2147482647)";
            case -2147482646: return "MCU_TIMEOUT (-2147482646)";
            case -2147482645: return "INVALID_VALUE (-2147482645)";
            default: return String.valueOf(r);
        }
    }

    static String resultHint(int v) {
        switch (v) {
            case -10011: return " (not registered)";
            case -10013: return " (not available)";
            case -2147482648: return " (failed)";
            default: return "";
        }
    }

    static int parseId(String s) {
        if (s.startsWith("0x") || s.startsWith("0X"))
            return Integer.parseUnsignedInt(s.substring(2), 16);
        return Integer.parseInt(s);
    }
}
