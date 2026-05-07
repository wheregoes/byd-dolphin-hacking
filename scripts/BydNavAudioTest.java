import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Tests playing audio through the navigation guidance I2S path (QUAT_MI2S_RX).
 * This separate I2S bus goes to the MCU, which controls routing to the AVAS speaker.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydnav scripts/BydNavAudioTest.java
 *   d8 --output /tmp/bydnav /tmp/bydnav/BydNavAudioTest.class
 *   adb push /tmp/bydnav/classes.dex /data/local/tmp/bydnav.dex
 *   adb push test_tone.wav /data/local/tmp/test_tone.wav
 *
 *   # Play through nav guidance path:
 *   adb shell "CLASSPATH=/data/local/tmp/bydnav.dex app_process /data/local/tmp BydNavAudioTest /data/local/tmp/test_tone.wav"
 *
 *   # Generate and play a 440Hz sine wave tone:
 *   adb shell "CLASSPATH=/data/local/tmp/bydnav.dex app_process /data/local/tmp BydNavAudioTest tone"
 *
 *   # Play through nav path while also sending AVAS routing CAN commands:
 *   adb shell "CLASSPATH=/data/local/tmp/bydnav.dex app_process /data/local/tmp BydNavAudioTest tone+route"
 */
public class BydNavAudioTest {

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "tone";

        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);

            if (mode.equals("tone") || mode.equals("tone+route")) {
                playToneNavPath(context, mode.contains("route"));
            } else {
                playFileNavPath(context, mode, false);
            }

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void playToneNavPath(Object context, boolean alsoRoute) throws Exception {
        System.out.println("=== Playing 440Hz tone through NAV GUIDANCE path ===");
        System.out.println("This uses QUAT_MI2S_RX (separate I2S bus to MCU)");
        System.out.println("Listen OUTSIDE the car for the tone on the AVAS speaker.\n");

        // AudioFormat
        Class<?> afClass = Class.forName("android.media.AudioFormat");
        Class<?> afbClass = Class.forName("android.media.AudioFormat$Builder");
        Object afBuilder = afbClass.newInstance();
        // ENCODING_PCM_16BIT = 2, CHANNEL_OUT_MONO = 4
        afbClass.getMethod("setEncoding", int.class).invoke(afBuilder, 2);
        afbClass.getMethod("setChannelMask", int.class).invoke(afBuilder, 4);
        afbClass.getMethod("setSampleRate", int.class).invoke(afBuilder, 44100);
        Object audioFormat = afbClass.getMethod("build").invoke(afBuilder);

        // AudioAttributes with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12
        Class<?> aaClass = Class.forName("android.media.AudioAttributes");
        Class<?> aabClass = Class.forName("android.media.AudioAttributes$Builder");
        Object aaBuilder = aabClass.newInstance();
        aabClass.getMethod("setUsage", int.class).invoke(aaBuilder, 12);
        // CONTENT_TYPE_SONIFICATION = 4
        aabClass.getMethod("setContentType", int.class).invoke(aaBuilder, 4);
        Object audioAttrs = aabClass.getMethod("build").invoke(aaBuilder);

        // AudioTrack
        Class<?> atClass = Class.forName("android.media.AudioTrack");
        int bufSize = (int) atClass.getMethod("getMinBufferSize", int.class, int.class, int.class)
                .invoke(null, 44100, 4, 2);
        bufSize = Math.max(bufSize, 44100 * 2); // at least 1 second

        Class<?> atbClass = Class.forName("android.media.AudioTrack$Builder");
        Object atBuilder = atbClass.newInstance();
        atbClass.getMethod("setAudioAttributes", aaClass).invoke(atBuilder, audioAttrs);
        atbClass.getMethod("setAudioFormat", afClass).invoke(atBuilder, audioFormat);
        atbClass.getMethod("setBufferSizeInBytes", int.class).invoke(atBuilder, bufSize);
        // MODE_STREAM = 1
        atbClass.getMethod("setTransferMode", int.class).invoke(atBuilder, 1);
        Object track = atbClass.getMethod("build").invoke(atBuilder);

        // Generate 440Hz sine wave
        int sampleRate = 44100;
        int durationSec = 10;
        int numSamples = sampleRate * durationSec;
        short[] samples = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            samples[i] = (short) (Short.MAX_VALUE * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate));
        }

        System.out.println("AudioTrack created with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE");
        System.out.println("Buffer size: " + bufSize + " bytes");
        System.out.println("Playing 440Hz for " + durationSec + " seconds...\n");

        // If also routing, send CAN commands
        if (alsoRoute) {
            Object mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (mgr != null) {
                Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
                System.out.println("--- Sending routing CAN commands ---");
                trySet(setInt, mgr, 1002, 0x1B10003D, 1, "AVAS_SOURCE=1");
                trySet(setInt, mgr, 1002, 0xAA000145, 1, "SOC_CONTROL_DSP=1");
                trySet(setInt, mgr, 1002, 0x32B1C042, 1, "AVAS_TO_EXT_SPEAKER=1");
                trySet(setInt, mgr, 1002, 0x1C10000E, 1, "EXT_SPEAKER_SWITCH=ON");
                trySet(setInt, mgr, 1002, 0x1B100043, 1, "EXT_PROMPT_TONE_SOURCE=1");
                trySet(setInt, mgr, 1002, 0xAA000301, 1, "LOOPBACK=ON");
                System.out.println();
            }
        }

        // Play
        atClass.getMethod("play").invoke(track);
        byte[] byteData = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            byteData[i * 2] = (byte) (samples[i] & 0xFF);
            byteData[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        atClass.getMethod("write", byte[].class, int.class, int.class)
                .invoke(track, byteData, 0, byteData.length);

        System.out.println("Tone playing... listen outside the car!");
        Thread.sleep(durationSec * 1000L + 500);

        atClass.getMethod("stop").invoke(track);
        atClass.getMethod("release").invoke(track);
        System.out.println("Done.");
    }

    static void playFileNavPath(Object context, String filePath, boolean alsoRoute) throws Exception {
        System.out.println("=== Playing " + filePath + " through NAV GUIDANCE path ===\n");

        Class<?> mpClass = Class.forName("android.media.MediaPlayer");
        Object mp = mpClass.newInstance();

        // Set audio attributes to NAV GUIDANCE
        Class<?> aaClass = Class.forName("android.media.AudioAttributes");
        Class<?> aabClass = Class.forName("android.media.AudioAttributes$Builder");
        Object aaBuilder = aabClass.newInstance();
        aabClass.getMethod("setUsage", int.class).invoke(aaBuilder, 12); // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        aabClass.getMethod("setContentType", int.class).invoke(aaBuilder, 4); // CONTENT_TYPE_SONIFICATION
        Object audioAttrs = aabClass.getMethod("build").invoke(aaBuilder);

        mpClass.getMethod("setAudioAttributes", aaClass).invoke(mp, audioAttrs);
        mpClass.getMethod("setDataSource", String.class).invoke(mp, filePath);
        mpClass.getMethod("prepare").invoke(mp);

        System.out.println("MediaPlayer created with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE");
        System.out.println("Playing... listen OUTSIDE the car!");

        mpClass.getMethod("start").invoke(mp);

        // Wait for completion
        while ((boolean) mpClass.getMethod("isPlaying").invoke(mp)) {
            Thread.sleep(500);
        }

        mpClass.getMethod("release").invoke(mp);
        System.out.println("Done.");
    }

    static void trySet(Method setInt, Object mgr, int dev, int fid, int val, String name) {
        try {
            int r = (int) setInt.invoke(mgr, dev, fid, val);
            String rs = r == 0 ? "SUCCESS" : (r == -2147482648 ? "FAILED" : String.valueOf(r));
            System.out.println("  " + name + " [0x" + Integer.toHexString(fid) + "] = " + rs);
        } catch (Exception e) {
            System.out.println("  " + name + " = ERR: " + e.getCause());
        }
    }
}
