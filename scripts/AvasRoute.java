import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class AvasRoute {
    static Object mgr;
    static Method si, sbuf;
    static int D = 1002, AVAH = 0x6E970010;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(t);
            mgr = ctx.getClass().getMethod("getSystemService", String.class).invoke(ctx, "auto");
            si = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            sbuf = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);

            String test = args.length > 0 ? args[0] : "combo";
            switch (test) {
                case "combo": testCombo(); break;
                case "ue": testUE(); break;
                case "kitchen": testKitchenSink(); break;
                case "pcm": testPcmStream(); break;
                case "all":
                    testCombo();
                    testUE();
                    testKitchenSink();
                    testPcmStream();
                    break;
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void testCombo() throws Exception {
        System.out.println("\n=== TEST 1: AVAH Enablers + QUAT Audio ===");

        System.out.println("--- A: Audio only (no enablers) ---");
        System.out.println(">>> LISTEN outside for 440Hz <<<");
        playNaviSine(440, 4000);
        Thread.sleep(2000);

        System.out.println("--- B: Enablers + AVAH + Audio ---");
        System.out.println(">>> LISTEN outside (440Hz + 1kHz test tone?) <<<");
        enable();
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(500);
        playNaviSine(440, 4000);
        fullStop();
        Thread.sleep(2000);

        System.out.println("--- C: Enablers (no AVAH) + Audio ---");
        System.out.println(">>> LISTEN outside for 440Hz <<<");
        enable();
        Thread.sleep(100);
        playNaviSine(440, 4000);
        fullStop();
        Thread.sleep(2000);

        System.out.println("--- D: AVAH first, audio, then stop AVAH ---");
        enable();
        set(AVAH, 1);
        Thread.sleep(1000);
        System.out.println(">>> LISTEN: playing audio while AVAH is on <<<");
        Object track = makeNaviTrack(440);
        track.getClass().getMethod("play").invoke(track);
        Thread.sleep(2000);
        fullStop();
        System.out.println("  AVAH stopped, audio still playing...");
        System.out.println(">>> Does 440Hz continue on ext speaker? <<<");
        Thread.sleep(3000);
        track.getClass().getMethod("stop").invoke(track);
        track.getClass().getMethod("release").invoke(track);
        Thread.sleep(1000);

        System.out.println("=== Test 1 done ===\n");
    }

    static void testUE() throws Exception {
        System.out.println("\n=== TEST 2: UE Channel Attack ===");

        System.out.println("--- A: UE unmute + audio ---");
        set(0xAA000346, 0);
        Thread.sleep(200);
        System.out.println(">>> LISTEN outside <<<");
        playNaviSine(440, 4000);
        set(0xAA000346, 1);
        Thread.sleep(1500);

        System.out.println("--- B: UE + DSP control + audio ---");
        set(0xAA000346, 0);
        set(0xAA000145, 1);
        Thread.sleep(200);
        System.out.println(">>> LISTEN outside <<<");
        playNaviSine(440, 4000);
        set(0xAA000346, 1);
        set(0xAA000145, 0);
        Thread.sleep(1500);

        System.out.println("--- C: UE + enablers + audio ---");
        set(0xAA000346, 0);
        enable();
        Thread.sleep(200);
        System.out.println(">>> LISTEN outside <<<");
        playNaviSine(440, 4000);
        fullStop();
        set(0xAA000346, 1);
        Thread.sleep(1500);

        System.out.println("--- D: UE + enablers + AVAH + audio ---");
        set(0xAA000346, 0);
        enable();
        set(AVAH, 1);
        Thread.sleep(200);
        System.out.println(">>> LISTEN: 440Hz + test tone? <<<");
        playNaviSine(440, 4000);
        fullStop();
        set(0xAA000346, 1);
        Thread.sleep(1000);

        System.out.println("=== Test 2 done ===\n");
    }

    static void testKitchenSink() throws Exception {
        System.out.println("\n=== TEST 3: Kitchen Sink ===");
        System.out.println("Enabling EVERYTHING...");

        set(0xAA000346, 0);  // UE unmute
        set(0xAA000145, 1);  // SOC control DSP
        set(0x6E990008, 1);  // debug mode
        set(0xAA000301, 1);  // loopback
        set(0x1C10000E, 1);  // ext speaker switch
        set(0x32B1C042, 1);  // AVAS to external
        set(0x1B10003D, 1);  // AVAS preset 1
        enable();
        Thread.sleep(300);

        System.out.println(">>> LISTEN outside for 440Hz (no AVAH) <<<");
        playNaviSine(440, 5000);

        System.out.println(">>> Adding AVAH tone... <<<");
        set(AVAH, 1);
        playNaviSine(440, 5000);

        // Cleanup everything
        fullStop();
        set(0xAA000346, 1);
        set(0xAA000145, 0);
        set(0x6E990008, 0);
        set(0xAA000301, 0);
        set(0x1C10000E, 0);
        set(0x32B1C042, 0);
        set(0x1B10003D, 0);
        Thread.sleep(1000);

        System.out.println("=== Test 3 done ===\n");
    }

    static void testPcmStream() throws Exception {
        System.out.println("\n=== TEST 4: PCM Streaming via setBuffer ===");

        enable();
        Thread.sleep(100);
        set(AVAH, 1);
        Thread.sleep(500);

        int sampleRate = 8000;
        double freq = 440.0;
        int bufSize = 128;
        int totalBuffers = 250;

        System.out.println("Streaming " + totalBuffers + " PCM buffers...");
        System.out.println(">>> LISTEN for tone modulation on ext speaker <<<");

        byte[] buf = new byte[bufSize];
        for (int b = 0; b < totalBuffers; b++) {
            for (int i = 0; i < bufSize; i++) {
                int idx = b * bufSize + i;
                double t = (double) idx / sampleRate;
                buf[i] = (byte)(128 + (int)(127 * Math.sin(2 * Math.PI * freq * t)));
            }
            try { sbuf.invoke(mgr, D, AVAH, buf); } catch (Exception e) {}
            Thread.sleep(8);
        }

        Thread.sleep(500);
        fullStop();
        System.out.println("=== Test 4 done ===\n");
    }

    static void playNaviSine(int freqHz, int durationMs) throws Exception {
        Object track = makeNaviTrack(freqHz);
        track.getClass().getMethod("play").invoke(track);
        Thread.sleep(durationMs);
        track.getClass().getMethod("stop").invoke(track);
        track.getClass().getMethod("release").invoke(track);
    }

    static Object makeNaviTrack(int freqHz) throws Exception {
        int sampleRate = 48000;
        int bufSamples = sampleRate * 2;

        // AudioAttributes.Builder
        Class<?> aab = Class.forName("android.media.AudioAttributes$Builder");
        Object ab = aab.newInstance();
        // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12
        ab = aab.getMethod("setUsage", int.class).invoke(ab, 12);
        // CONTENT_TYPE_SONIFICATION = 4
        ab = aab.getMethod("setContentType", int.class).invoke(ab, 4);
        Object attrs = aab.getMethod("build").invoke(ab);

        // AudioFormat.Builder
        Class<?> afb = Class.forName("android.media.AudioFormat$Builder");
        Object fb = afb.newInstance();
        fb = afb.getMethod("setSampleRate", int.class).invoke(fb, sampleRate);
        // ENCODING_PCM_16BIT = 2
        fb = afb.getMethod("setEncoding", int.class).invoke(fb, 2);
        // CHANNEL_OUT_STEREO = 12
        fb = afb.getMethod("setChannelMask", int.class).invoke(fb, 12);
        Object format = afb.getMethod("build").invoke(fb);

        // AudioTrack constructor
        Class<?> atc = Class.forName("android.media.AudioTrack");
        Class<?> aac = Class.forName("android.media.AudioAttributes");
        Class<?> afc = Class.forName("android.media.AudioFormat");
        Constructor<?> ctor = atc.getConstructor(aac, afc, int.class, int.class, int.class);
        // MODE_STREAM=1, AUDIO_SESSION_ID_GENERATE=0
        Object track = ctor.newInstance(attrs, format, bufSamples * 4, 1, 0);

        // Generate sine wave (stereo interleaved)
        short[] samples = new short[bufSamples * 2];
        for (int i = 0; i < bufSamples; i++) {
            double t = (double) i / sampleRate;
            short val = (short)(Short.MAX_VALUE * 0.9 * Math.sin(2 * Math.PI * freqHz * t));
            samples[i * 2] = val;
            samples[i * 2 + 1] = val;
        }

        // Write audio data
        Method writeM = atc.getMethod("write", short[].class, int.class, int.class);
        for (int w = 0; w < 6; w++) {
            writeM.invoke(track, samples, 0, samples.length);
        }

        return track;
    }

    static void enable() {
        set(0xAA000148, 1);
        set(0xAA000142, 1);
        set(0xAA00011A, 1);
        set(0xAA000104, 1);
        set(0xAA000171, 1);
        set(0xAA00011E, 0);
    }

    static void fullStop() {
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
