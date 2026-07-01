import java.lang.reflect.Method;

/**
 * Tests newly discovered CAN bus signals from CarSetting.apk decompilation.
 * These signals were found in com.byd.feature.audio.Audio.java (599 lines)
 * but were NOT tested in the original BydAudioQuery/BydMcuProbe scripts.
 *
 * Key new signal groups:
 *   - UE_BROADCAST (User External speaker broadcast) — may route audio to AVAS
 *   - HW_L1/L2/L3_SOUNDING_DIRECTION — hardware sounding direction control
 *   - KEY_SOUND_SOURCE — key sound source routing
 *   - PROMPT_VOLUME_LEVEL — prompt volume (1=low, 2=mid, 3=high)
 *   - NON_BRANDED_AMP_UE — non-branded amplifier UE channel controls
 *   - OPEN_DOOR_LOW_MEDIA_SOUND — open door media sound behavior
 *   - AUDIO_DYNA_REWORK_SOUND_EFFECT — Dynaudio rework sound effect
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/byddeep scripts/BydAvasDeepProbe.java
 *   d8 --output /tmp/byddeep /tmp/byddeep/BydAvasDeepProbe.class
 *   adb push /tmp/byddeep/classes.dex /data/local/tmp/byddeep.dex
 *
 *   adb shell "CLASSPATH=/data/local/tmp/byddeep.dex app_process / BydAvasDeepProbe"
 *   adb shell "CLASSPATH=/data/local/tmp/byddeep.dex app_process / BydAvasDeepProbe read"
 *   adb shell "CLASSPATH=/data/local/tmp/byddeep.dex app_process / BydAvasDeepProbe set <hexId> <value>"
 *   adb shell "CLASSPATH=/data/local/tmp/byddeep.dex app_process / BydAvasDeepProbe uetest"
 */
public class BydAvasDeepProbe {
    static Object mgr;
    static Method setInt, getInt;
    static final int D = 1002; // audio device
    static final int D_ENGINE = 1003; // engine device

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);

            System.out.println("=== BYD AVAS Deep Probe — New Signals from CarSetting Decompilation ===\n");

            if (args.length == 0) {
                runFullProbe();
            } else if (args[0].equals("read")) {
                readAllSignals();
            } else if (args[0].equals("set") && args.length >= 3) {
                int fid = (int) Long.parseLong(args[1].replace("0x", ""), 16);
                int val = Integer.parseInt(args[2]);
                int dev = args.length >= 4 ? Integer.parseInt(args[3]) : D;
                int r = (int) setInt.invoke(mgr, dev, fid, val);
                System.out.println("setInt(" + args[1] + ", " + val + ", dev=" + dev + ") → " + r +
                    formatResult(r));
            } else if (args[0].equals("uetest")) {
                testUeBroadcast();
            } else if (args[0].equals("hwsounding")) {
                testHwSounding();
            } else {
                System.out.println("Usage: BydAvasDeepProbe [read | set <hexId> <value> [dev] | uetest | hwsounding]");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void runFullProbe() throws Exception {
        readAllSignals();
        System.out.println("\n--- UE Broadcast Write Tests ---");
        testUeBroadcast();
        System.out.println("\n--- HW Sounding Direction Tests ---");
        testHwSounding();
        System.out.println("\n--- Prompt Volume Level Tests ---");
        testPromptVolume();
        System.out.println("\n--- Key Sound Source Tests ---");
        testKeySoundSource();
    }

    static void readAllSignals() throws Exception {
        System.out.println("--- Reading all newly discovered signals ---\n");

        String[][] signals = {
            // UE Broadcast (User External)
            {"0x35202028", "UE_BROADCAST_SOUND_SOURCE", "1002"},
            {"0x35202010", "UE_BROADCAST_VOLUME", "1002"},
            {"0x35201046", "UE_BROADCAST_TRIGGERS_LOW_PRESSURE", "1002"},

            // HW Sounding Direction
            {"0x35202020", "HW_L1_SOUNDING_DIRECTION_STATUS", "1002"},
            {"0x3520201C", "HW_L2_SILENCE_STATUS", "1002"},
            {"0x3520201E", "HW_L3_SILENCE_STATUS", "1002"},
            {"0x35202024", "HW_MDC_L2_LOWER_STATUS", "1002"},

            // Key Sound Source
            {"0x35201010", "KEY_SOUND_SOURCE_STATUS", "1002"},

            // Prompt Volume
            {"0x99000307", "PROMPT_VOLUME_LEVEL_STATUS", "1002"},

            // Open Door Low Media Sound
            {"0x35202016", "OPEN_DOOR_LOW_MEDIA_SOUND_STATUS", "1002"},
            {"0x99000317", "OPEN_DOOR_LOW_MEDIA_SOUND_SET_STATUS", "1002"},

            // Dyna Rework Sound Effect
            {"0x99000233", "DYNA_REWORK_SOUND_EFFECT_CONFIG", "1002"},
            {"0x99000234", "DYNA_REWORK_SOUND_EFFECT_STYLE", "1002"},

            // DSP / Fault Status (NEW)
            {"0x99000247", "FAULT_TYPE_A2B_STATUS", "1002"},
            {"0x99000248", "FAULT_TYPE_DSP_STATUS", "1002"},
            {"0x99000249", "FAULT_TYPE_PAD_INIT_STATUS", "1002"},
            {"0x99000246", "FAULT_TYPE_PA_STATUS", "1002"},

            // Sound Shield
            {"0x35202034", "SOUND_SHIELD_CONFIGURATION", "1002"},
            {"0x3520202E", "SOUND_SHIELD_STATUS", "1002"},

            // Speaker Config
            {"0x35A000D8", "SPEAKER_FLIP_COVER_CONFIG", "1002"},
            {"0x35A000DA", "SPEAKER_FLIP_SETTING_STATUS", "1002"},

            // Existing AVAS (for comparison)
            {"0x4C60002D", "AVAS_SOUND_SOURCE_STATE", "1002"},
            {"0x99000162", "AVAS_SOURCE_TYPE", "1002"},
            {"0x35201036", "EXTERIOR_SPEAKER_CONFIG", "1002"},
            {"0x35201040", "EXTERIOR_SPEAKER_SWITCH_STATUS", "1002"},
            {"0x3520103F", "EXTERIOR_PROMPT_TONE_SOURCE_STATUS", "1002"},
            {"0x35201042", "AVAS_FAULT_STATUS", "1002"},

            // Amplifier
            {"0x4FD00030", "AMPLIFIER_CONFIG", "1002"},
            {"0x4FD00045", "AMPLIFIER_ANC_CONFIG", "1002"},
            {"0x4FD00046", "RNC_CONFIG", "1002"},

            // DSP Info
            {"0x99000215", "DSP_TYPE", "1002"},
            {"0x99000214", "AMPLIFIER_TYPE", "1002"},
            {"0x99000223", "OTA_DSP_SOUND_SOURCE_PACKAGE", "1002"},
            {"0x99000266", "SUPPORT_VARIABLE_SOUND_SOURCE", "1002"},

            // 3D Sound
            {"0x4AB0001D", "3D_SOUND_EFFECT_CONFIG", "1002"},
            {"0x4AB0000D", "3D_SOUND_EFFECT_STATUS", "1002"},

            // Initialization
            {"0x4C60003E", "INITIALIZATION_STATUS", "1002"},
            {"0x99000364", "AUDIO_DSP_READY", "1002"},

            // Speaker numbers
            {"0x4FD00008", "SPEAKERS_NUMBER", "1002"},
            {"0x35202038", "SPEAKERS_NUMBER_2", "1002"},
        };

        for (String[] sig : signals) {
            int fid = (int) Long.parseLong(sig[0].replace("0x", ""), 16);
            int dev = Integer.parseInt(sig[2]);
            int val = (int) getInt.invoke(mgr, dev, fid);
            System.out.println("  " + sig[0] + " [" + sig[1] + "] = " + val + formatResult(val));
        }
    }

    static void testUeBroadcast() throws Exception {
        System.out.println("Testing UE (User External) Broadcast signals...\n");

        // UE_BROADCAST_SOUND_SOURCE_SET = 0x32B1C028
        System.out.println("  UE_BROADCAST_SOUND_SOURCE_SET (0x32B1C028):");
        for (int v = 0; v <= 3; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x32B1C028, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(300);
        }

        // UE_BROADCAST_VOLUME_SET = 0x1A900040
        System.out.println("  UE_BROADCAST_VOLUME_SET (0x1A900040):");
        for (int v = 0; v <= 15; v += 5) {
            int r = (int) setInt.invoke(mgr, D, 0x1A900040, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // UE_BROADCAST_TRIGGERS_LOW_PRESSURE_SET = 0x1A90001E
        System.out.println("  UE_BROADCAST_TRIGGERS_LOW_PRESSURE_SET (0x1A90001E):");
        for (int v = 0; v <= 2; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x1A90001E, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // NON_BRANDED_AMP_UE_MUTE_SET = 0xAA000346
        System.out.println("  NON_BRANDED_AMP_UE_MUTE_SET (0xAA000346):");
        for (int v = 0; v <= 1; v++) {
            int r = (int) setInt.invoke(mgr, D, 0xAA000346, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // NON_BRANDED_AMP_UE_VOLUME_SET = 0xAA000332
        System.out.println("  NON_BRANDED_AMP_UE_VOLUME_SET (0xAA000332):");
        for (int v = 0; v <= 15; v += 5) {
            int r = (int) setInt.invoke(mgr, D, 0xAA000332, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // NON_BRANDED_AMP_UE_DUCK_MEDIA_SET = 0xAA000334
        System.out.println("  NON_BRANDED_AMP_UE_DUCK_MEDIA_SET (0xAA000334):");
        int r = (int) setInt.invoke(mgr, D, 0xAA000334, 1);
        System.out.println("    set=1 → " + r + formatResult(r));
    }

    static void testHwSounding() throws Exception {
        System.out.println("Testing HW Sounding Direction signals...\n");

        // HW_L1_SOUNDING_DIRECTION_SET = 0x32B1C020
        System.out.println("  HW_L1_SOUNDING_DIRECTION_SET (0x32B1C020):");
        for (int v = 0; v <= 3; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x32B1C020, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(300);
        }

        // HW_L2_SILENCE_SET = 0x32B1C01C
        System.out.println("  HW_L2_SILENCE_SET (0x32B1C01C):");
        for (int v = 0; v <= 1; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x32B1C01C, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // HW_L3_SILENCE_SET = 0x32B1C01E
        System.out.println("  HW_L3_SILENCE_SET (0x32B1C01E):");
        for (int v = 0; v <= 1; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x32B1C01E, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(200);
        }

        // HW_MDC_L2_LOWER_SET = 0x32B1C024
        System.out.println("  HW_MDC_L2_LOWER_SET (0x32B1C024):");
        int r = (int) setInt.invoke(mgr, D, 0x32B1C024, 1);
        System.out.println("    set=1 → " + r + formatResult(r));
    }

    static void testPromptVolume() throws Exception {
        System.out.println("Testing Prompt Volume Level...\n");

        // PROMPT_VOLUME_LEVEL_SET = 0xAA000299
        System.out.println("  PROMPT_VOLUME_LEVEL_SET (0xAA000299):");
        for (int v = 1; v <= 3; v++) {
            int r = (int) setInt.invoke(mgr, D, 0xAA000299, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(500);
            int readback = (int) getInt.invoke(mgr, D, 0x99000307);
            System.out.println("    readback STATUS (0x99000307) = " + readback);
        }
    }

    static void testKeySoundSource() throws Exception {
        System.out.println("Testing Key Sound Source...\n");

        // KEY_SOUND_SOURCE_SET = 0x32B1C010
        System.out.println("  KEY_SOUND_SOURCE_SET (0x32B1C010):");
        for (int v = 0; v <= 3; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x32B1C010, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(300);
        }

        // KEY_TONE_SET = 0x1B10000E
        System.out.println("  KEY_TONE_SET (0x1B10000E):");
        for (int v = 0; v <= 3; v++) {
            int r = (int) setInt.invoke(mgr, D, 0x1B10000E, v);
            System.out.println("    set=" + v + " → " + r + formatResult(r));
            Thread.sleep(300);
        }
    }

    static String formatResult(int r) {
        if (r == 0) return " (SUCCESS)";
        if (r == -10011) return " (NOT_REGISTERED/write-only)";
        if (r == -10013) return " (NOT_AVAILABLE)";
        if (r == -2147482648) return " (FAILED)";
        if (r == -2147482647) return " (BUSY)";
        if (r == -2147482646) return " (TIMEOUT)";
        if (r == -2147482645) return " (INVALID_VALUE)";
        return "";
    }
}
