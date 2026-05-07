import java.lang.reflect.Method;

/**
 * Tests structured setBuffer data on AVAH and other featureIds.
 * The MCU accepts 1-128 byte buffers on AVAH — this probes what data format it expects.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydbuf scripts/BydBufferProbe.java
 *   d8 --output /tmp/bydbuf /tmp/bydbuf/BydBufferProbe.class
 *   adb push /tmp/bydbuf/classes.dex /data/local/tmp/bydbuf.dex
 *   adb shell "CLASSPATH=/data/local/tmp/bydbuf.dex app_process /data/local/tmp BydBufferProbe"
 */
public class BydBufferProbe {
    static Object mgr;
    static Method setInt, getInt, setBuffer, getBuffer;
    static final int DEV = 1002;
    static final int AVAH = 0x6E970010;
    static final int AVAH_DBG = 0x6E990010;
    static final int AVAH_DBG2 = 0x6E990040;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");

            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            setBuffer = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);
            getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);

            testStructuredBuffers();
            testFrequencyEncoding();
            testDebugBuffers();
            testCommandSequences();

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static int doSetBuf(int dev, int fid, byte[] data) throws Exception {
        return (int) setBuffer.invoke(mgr, dev, fid, data);
    }

    static int doSet(int dev, int fid, int val) throws Exception {
        return (int) setInt.invoke(mgr, dev, fid, val);
    }

    static String r(int result) {
        if (result == 0) return "OK";
        if (result == -2147482648) return "FAIL";
        return String.valueOf(result);
    }

    static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    static void testStructuredBuffers() throws Exception {
        System.out.println("=== Structured Buffer Tests on AVAH ===\n");

        // Theory 1: buffer = [command:1][params:N]
        System.out.println("--- Theory 1: [cmd:1][params] ---");
        byte[][] cmdBufs = {
            {0x01},                         // cmd=play
            {0x01, 0x01},                   // cmd=play, param=1kHz
            {0x01, 0x02},                   // cmd=play, param=2kHz
            {0x01, 0x03},                   // cmd=play, param=3kHz
            {0x02},                         // cmd=stop
            {0x03},                         // cmd=configure
            {0x04},                         // cmd=query
            {(byte)0xFF},                   // cmd=max
        };
        for (byte[] buf : cmdBufs) {
            int r = doSetBuf(DEV, AVAH, buf);
            System.out.println("  " + hex(buf) + " -> " + r(r));
            if (r == 0) Thread.sleep(1500);
        }
        doSet(DEV, AVAH, 0);

        // Theory 2: buffer = [freq_BE:2][duration_BE:2][volume:1][waveform:1]
        System.out.println("\n--- Theory 2: [freq:2][dur:2][vol:1][wave:1] ---");
        byte[][] paramBufs = {
            {0x01, (byte)0xF4, 0x03, (byte)0xE8, 0x64, 0x01}, // 500Hz, 1000ms, vol=100, sine
            {0x03, (byte)0xE8, 0x07, (byte)0xD0, 0x64, 0x01}, // 1000Hz, 2000ms, vol=100, sine
            {0x07, (byte)0xD0, 0x0B, (byte)0xB8, 0x64, 0x01}, // 2000Hz, 3000ms, vol=100, sine
            {0x0B, (byte)0xB8, 0x07, (byte)0xD0, 0x64, 0x01}, // 3000Hz, 2000ms, vol=100, sine
            {0x11, (byte)0x70, 0x07, (byte)0xD0, 0x64, 0x01}, // 4464Hz, 2000ms, vol=100, sine
        };
        for (byte[] buf : paramBufs) {
            int r = doSetBuf(DEV, AVAH, buf);
            int freq = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
            System.out.println("  freq=" + freq + "Hz " + hex(buf) + " -> " + r(r));
            if (r == 0) Thread.sleep(2500);
        }
        doSet(DEV, AVAH, 0);

        // Theory 3: buffer = [value_LE:4] (same as setInt but via buffer path)
        System.out.println("\n--- Theory 3: [value_LE:4] (like setInt via buffer) ---");
        byte[][] intBufs = {
            {0x01, 0x00, 0x00, 0x00}, // val=1 LE
            {0x02, 0x00, 0x00, 0x00}, // val=2 LE
            {0x03, 0x00, 0x00, 0x00}, // val=3 LE
            {0x00, 0x00, 0x00, 0x01}, // val=1 BE
            {0x00, 0x00, 0x00, 0x02}, // val=2 BE
            {0x00, 0x00, 0x00, 0x03}, // val=3 BE
        };
        for (byte[] buf : intBufs) {
            int r = doSetBuf(DEV, AVAH, buf);
            System.out.println("  " + hex(buf) + " -> " + r(r));
            if (r == 0) Thread.sleep(1500);
        }
        doSet(DEV, AVAH, 0);
    }

    static void testFrequencyEncoding() throws Exception {
        System.out.println("\n=== Frequency Encoding Tests ===\n");

        // Try setBuffer with frequency as various numeric formats
        System.out.println("--- Frequency as 16-bit BE in 2-byte buffer ---");
        int[] freqs = {200, 300, 400, 440, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000, 4000, 5000};
        for (int f : freqs) {
            byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
            int r = doSetBuf(DEV, AVAH, buf);
            if (r == 0) {
                System.out.println("  " + f + "Hz [" + hex(buf) + "] -> OK ***LISTEN***");
                Thread.sleep(2000);
                doSet(DEV, AVAH, 0);
                Thread.sleep(300);
            }
        }

        // Also try on debug AVAH
        System.out.println("\n--- Same on debug AVAH (0x6E990010) ---");
        for (int f : new int[]{440, 1000, 2000, 3000}) {
            byte[] buf = {(byte)((f >> 8) & 0xFF), (byte)(f & 0xFF)};
            int r = doSetBuf(DEV, AVAH_DBG, buf);
            if (r == 0) {
                System.out.println("  " + f + "Hz [" + hex(buf) + "] -> OK ***LISTEN***");
                Thread.sleep(2000);
                doSet(DEV, AVAH_DBG, 0);
                Thread.sleep(300);
            } else {
                System.out.println("  " + f + "Hz -> " + r(r));
            }
        }

        // Try on new debug command 0x6E990040
        System.out.println("\n--- setBuffer on 0x6E990040 ---");
        byte[][] testBufs = {
            {0x01},
            {0x01, 0x00},
            {0x01, 0x00, 0x00, 0x00},
            {0x03, (byte)0xE8},  // 1000 as 16-bit BE
        };
        for (byte[] buf : testBufs) {
            int r = doSetBuf(DEV, AVAH_DBG2, buf);
            System.out.println("  0x6E990040 buf=" + hex(buf) + " -> " + r(r));
            if (r == 0) Thread.sleep(1500);
        }

        doSet(DEV, AVAH, 0);
        doSet(DEV, AVAH_DBG, 0);
        doSet(DEV, AVAH_DBG2, 0);
    }

    static void testDebugBuffers() throws Exception {
        System.out.println("\n=== Debug Mode Buffer Tests ===\n");

        // Enter debug mode first
        doSet(DEV, 0x6E990008, 1);
        System.out.println("Debug mode enabled (0x6E990008=1)");

        // Now try setBuffer on routing featureIds that normally fail
        System.out.println("\n--- setBuffer on routing FIDs (with debug mode) ---");
        int[] routeFids = {
            0x32B1C042,  // AVAS_TO_EXT_SPEAKER
            0x1C10000E,  // EXT_SPEAKER_SWITCH
            0x1B100043,  // EXT_PROMPT_TONE_SOURCE
            0xAA000301,  // LOOPBACK
            0xAA000145,  // SOC_CONTROL_DSP
        };
        byte[] enableBuf = {0x01, 0x00, 0x00, 0x00};
        for (int fid : routeFids) {
            int rInt = doSet(DEV, fid, 1);
            int rBuf = doSetBuf(DEV, fid, enableBuf);
            System.out.println("  0x" + Integer.toHexString(fid) +
                               " setInt=" + r(rInt) + " setBuf=" + r(rBuf));
        }

        // Try setBuffer with larger data on AVAH while in debug mode
        System.out.println("\n--- Large buffers on AVAH in debug mode ---");
        for (int size : new int[]{64, 100, 120, 128}) {
            byte[] data = new byte[size];
            // 440Hz PCM at 8kHz, 16-bit LE
            for (int i = 0; i < size / 2; i++) {
                short val = (short)(Short.MAX_VALUE * 0.8 * Math.sin(2.0 * Math.PI * 440.0 * i / 8000.0));
                data[i * 2] = (byte)(val & 0xFF);
                data[i * 2 + 1] = (byte)((val >> 8) & 0xFF);
            }
            int r = doSetBuf(DEV, AVAH, data);
            System.out.println("  " + size + " bytes PCM -> " + r(r));
            if (r == 0) Thread.sleep(1000);
        }

        // Turn off debug mode
        doSet(DEV, 0x6E990008, 0);
        doSet(DEV, AVAH, 0);
        System.out.println("\nDebug mode disabled. Done.");
    }

    static void testCommandSequences() throws Exception {
        System.out.println("\n=== Command Sequence Tests ===\n");
        System.out.println("Theory: specific sequences may unlock AVAS routing.\n");

        // Sequence 1: enable debug → set DSP control → enable AVAS routing → play tone
        System.out.println("--- Seq 1: debug + DSP + routing + tone ---");
        doSet(DEV, 0x6E990008, 1);  // debug mode
        Thread.sleep(100);
        doSet(DEV, 0xAA000145, 1);  // SOC control DSP
        Thread.sleep(100);
        doSet(DEV, 0xAA000113, 0);  // DSP standby off
        Thread.sleep(100);
        doSet(DEV, 0x1C10000E, 1);  // ext speaker switch
        int r1 = doSet(DEV, 0x32B1C042, 1); // AVAS to ext
        System.out.println("  Routing after sequence: " + r(r1));
        doSet(DEV, AVAH, 1); // play tone
        Thread.sleep(3000);
        doSet(DEV, AVAH, 0);

        // Sequence 2: TEST_AUDIO_AVAS + TEST_MCU_AVAS_CONFIG + tone
        System.out.println("\n--- Seq 2: test AVAS + config + tone ---");
        doSet(DEV, 0xAA000104, 1);  // TEST_AUDIO_AVAS_SET
        Thread.sleep(100);
        doSet(DEV, 0xAA000171, 1);  // TEST_MCU_AVAS_CONFIGURATION_SET
        Thread.sleep(100);
        doSet(DEV, 0x1B10003D, 3);  // AVAS source = 3 (non-standard)
        Thread.sleep(100);
        r1 = doSet(DEV, 0x32B1C042, 1);
        System.out.println("  Routing after test sequence: " + r(r1));
        doSet(DEV, AVAH, 2); // play tone
        Thread.sleep(3000);
        doSet(DEV, AVAH, 0);

        // Sequence 3: use debug command 0x6E990040 as possible factory test enabler
        System.out.println("\n--- Seq 3: 0x6E990040 as factory test gate ---");
        doSet(DEV, 0x6E990040, 1);  // unknown debug command
        Thread.sleep(200);
        doSet(DEV, 0x6E990008, 1);  // debug mode
        Thread.sleep(100);
        r1 = doSet(DEV, 0x32B1C042, 1); // try routing again
        int r2 = doSet(DEV, 0x1C10000E, 1); // try ext speaker
        int r3 = doSet(DEV, 0xAA000301, 1); // try loopback
        System.out.println("  routing=" + r(r1) + " ext_speaker=" + r(r2) + " loopback=" + r(r3));
        doSet(DEV, AVAH, 1);
        Thread.sleep(3000);
        doSet(DEV, AVAH, 0);

        // Sequence 4: set AVAS source via buffer instead of int
        System.out.println("\n--- Seq 4: setBuffer on AVAS_SOURCE (0x1B10003D) ---");
        byte[][] srcBufs = {
            {0x01, 0x00, 0x00, 0x00},
            {0x03, 0x00, 0x00, 0x00},
            {0x04, 0x00, 0x00, 0x00},
            {(byte)0xFF, 0x00, 0x00, 0x00},
        };
        for (byte[] buf : srcBufs) {
            int r = doSetBuf(DEV, 0x1B10003D, buf);
            System.out.println("  AVAS_SOURCE buf=" + hex(buf) + " -> " + r(r));
        }

        // Clean up
        doSet(DEV, AVAH, 0);
        doSet(DEV, 0x6E990008, 0);
        doSet(DEV, 0x6E990040, 0);
        doSet(DEV, 0x1B10003D, 1); // reset to default AVAS source
        System.out.println("\nDone. All reset.");
    }
}
