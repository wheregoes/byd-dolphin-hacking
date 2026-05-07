import java.lang.reflect.Method;

/**
 * MCU command processing probe — tests setBuffer, getBuffer, and extreme value
 * behavior on the BYD Dolphin head unit to characterize MCU command dispatch.
 *
 * This is authorized security research on an owned vehicle.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydprobe scripts/BydMcuProbe.java
 *   d8 --output /tmp/bydprobe /tmp/bydprobe/BydMcuProbe.class
 *   adb push /tmp/bydprobe/classes.dex /data/local/tmp/bydprobe.dex
 *   adb shell "CLASSPATH=/data/local/tmp/bydprobe.dex app_process /data/local/tmp BydMcuProbe <command>"
 *
 * Commands:
 *   buffer_avah     - test setBuffer on AVAH featureId with various payloads
 *   buffer_route    - test setBuffer on routing featureIds
 *   getbuf_scan     - scan getBuffer on known and sequential featureIds
 *   extreme_values  - test extreme int values on AVAH
 *   scan_nearby     - scan featureIds in 0x6E97xxxx range
 *   scan_test       - scan test featureId range 0xAA0001xx through 0xAA0003xx
 *   all             - run all probes sequentially
 */
public class BydMcuProbe {
    static final int DEV_AUDIO = 1002;
    static final int DEV_ENGINE = 1003;

    // Known featureIds
    static final int FID_AVAH_SET = 0x6E970010;       // AVAH test tone (write)
    static final int FID_AVAH_READ = 0x6EA70010;      // AVAH test tone (read)
    static final int FID_AVAS_SOURCE = 0x1B10003D;    // AVAS source type
    static final int FID_AVAS_TO_EXT = 0x32B1C042;    // Route AVAS to ext speaker
    static final int FID_EXT_SPEAKER = 0x1C10000E;    // Ext speaker switch
    static final int FID_EXT_PROMPT = 0x1B100043;     // Ext prompt tone source
    static final int FID_DSP_TYPE = 0x99000215;       // DSP type
    static final int FID_AVAS_TYPE = 0x99000162;      // AVAS source type (read)
    static final int FID_DSP_READY = 0x99000364;      // DSP ready
    static final int FID_AMP_TYPE = 0x99000214;       // Amplifier type
    static final int FID_SOUND_INFO = 0x99000194;     // Vehicle prompt sound source info
    static final int FID_LOOPBACK = 0xAA000301;       // Loopback passage control
    static final int FID_DSP_CONTROL = 0xAA000145;    // SOC notify MCU control DSP

    public static void main(String[] args) {
        try {
            // Bootstrap Android runtime
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            Object mgr = context.getClass().getMethod("getSystemService", String.class)
                    .invoke(context, "auto");
            if (mgr == null) {
                System.out.println("FATAL: BYDAutoManager is null");
                return;
            }

            // Reflect all methods we need
            Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
            Method setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            Method getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);

            // setBuffer may or may not exist — discover via reflection
            Method setBuffer = null;
            try {
                setBuffer = mgr.getClass().getMethod("setBuffer",
                        int.class, int.class, byte[].class);
                System.out.println("[init] setBuffer method found: " + setBuffer);
            } catch (NoSuchMethodException e) {
                System.out.println("[init] setBuffer(int,int,byte[]) NOT FOUND, trying variants...");
                // Try alternate signatures
                for (Method m : mgr.getClass().getMethods()) {
                    if (m.getName().equals("setBuffer") || m.getName().contains("setBuffer")
                            || m.getName().contains("SetBuffer")) {
                        System.out.println("  Found candidate: " + m);
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length >= 3) {
                            setBuffer = m;
                            System.out.println("  Using: " + m);
                            break;
                        }
                    }
                }
                if (setBuffer == null) {
                    System.out.println("[init] WARNING: No setBuffer method found at all");
                    System.out.println("[init] Available methods on BYDAutoManager:");
                    for (Method m : mgr.getClass().getDeclaredMethods()) {
                        System.out.println("  " + m.getName() + "("
                                + formatParams(m.getParameterTypes()) + ") -> "
                                + m.getReturnType().getSimpleName());
                    }
                }
            }

            String cmd = args.length > 0 ? args[0] : "help";

            switch (cmd) {
                case "buffer_avah":
                    probeBufferAvah(setBuffer, setInt, getInt, getBuffer, mgr);
                    break;
                case "buffer_route":
                    probeBufferRoute(setBuffer, getInt, mgr);
                    break;
                case "getbuf_scan":
                    probeGetBufferScan(getBuffer, mgr);
                    break;
                case "extreme_values":
                    probeExtremeValues(setInt, getInt, mgr);
                    break;
                case "scan_nearby":
                    probeScanNearby(setInt, getInt, mgr);
                    break;
                case "scan_test":
                    probeScanTest(setInt, getInt, mgr);
                    break;
                case "methods":
                    dumpMethods(mgr);
                    break;
                case "all":
                    System.out.println("========================================");
                    System.out.println("  BYD MCU Probe — Full Scan");
                    System.out.println("========================================\n");
                    probeBufferAvah(setBuffer, setInt, getInt, getBuffer, mgr);
                    System.out.println("\n" + "=".repeat(60) + "\n");
                    probeBufferRoute(setBuffer, getInt, mgr);
                    System.out.println("\n" + "=".repeat(60) + "\n");
                    probeGetBufferScan(getBuffer, mgr);
                    System.out.println("\n" + "=".repeat(60) + "\n");
                    probeExtremeValues(setInt, getInt, mgr);
                    System.out.println("\n" + "=".repeat(60) + "\n");
                    probeScanNearby(setInt, getInt, mgr);
                    System.out.println("\n" + "=".repeat(60) + "\n");
                    probeScanTest(setInt, getInt, mgr);
                    break;
                default:
                    printUsage();
            }

        } catch (Exception e) {
            System.out.println("FATAL: " + e);
            e.printStackTrace();
        }
    }

    // =========================================================================
    //  Probe 1: setBuffer on AVAH featureId
    // =========================================================================

    static void probeBufferAvah(Method setBuffer, Method setInt, Method getInt,
                                Method getBuffer, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: setBuffer on AVAH (0x6E970010)");
        System.out.println("========================================\n");

        // Read baseline state
        System.out.println("--- Baseline state ---");
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "AVAH_STATE");
        readBuf(getBuffer, mgr, DEV_AUDIO, FID_AVAH_READ, "AVAH_STATE_BUF");
        readBuf(getBuffer, mgr, DEV_AUDIO, FID_AVAH_SET, "AVAH_SET_BUF");

        if (setBuffer == null) {
            System.out.println("\n[SKIP] setBuffer not available — cannot test buffer payloads");
            System.out.println("[INFO] Testing setInt fallback instead\n");

            // At least test setInt with value 1 to confirm baseline
            System.out.println("--- setInt baseline test ---");
            int r = writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 1, "AVAH_SET tone=1");
            pause(300);
            readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "AVAH_STATE (after tone 1)");
            writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "AVAH_SET tone=0 (stop)");
            return;
        }

        // Test 1: Minimal buffers — does setBuffer accept them?
        System.out.println("\n--- Test 1: Small buffers (type detection) ---");
        System.out.println("Theory: MCU may read first bytes as int, ignore rest,");
        System.out.println("        or reject buffer-type calls entirely.\n");

        // Single byte: value 1 (should play tone if interpreted as int)
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{0x01},
                "1 byte: {0x01}");

        pause(500);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_STATE after {0x01}");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop tone)");

        // Two bytes: little-endian int 1
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{0x01, 0x00},
                "2 bytes: {0x01, 0x00}");
        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_STATE after 2-byte");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop tone)");

        // Four bytes: little-endian int 1 (same size as int)
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "4 bytes: {0x01, 0x00, 0x00, 0x00} (LE int 1)");
        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_STATE after 4-byte");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop tone)");

        // Four bytes: big-endian int 1
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{0x00, 0x00, 0x00, 0x01},
                "4 bytes: {0x00, 0x00, 0x00, 0x01} (BE int 1)");
        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_STATE after BE 4-byte");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop tone)");

        // Test 2: 440Hz sine wave as 16-bit PCM (audio injection test)
        System.out.println("\n--- Test 2: 440Hz PCM audio injection ---");
        System.out.println("Theory: If MCU interprets buffer as raw PCM data,");
        System.out.println("        the AVAS speaker may play a 440Hz tone.\n");

        byte[] pcm440 = generate440HzPCM(8000, 1); // 1 second at 8kHz = 16KB
        System.out.println("Generated PCM: " + pcm440.length + " bytes (8kHz 16-bit mono, 1s)");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET, pcm440,
                "440Hz PCM (8kHz/16bit/1s = " + pcm440.length + " bytes)");
        pause(1500);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_STATE after PCM inject");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop)");

        // Test 3: Known pattern for data echo detection
        System.out.println("\n--- Test 3: Known pattern (echo detection) ---");
        System.out.println("Theory: If MCU echoes buffer data back via getBuffer,");
        System.out.println("        we can detect memory read/write behavior.\n");

        byte[] pattern = new byte[64];
        for (int i = 0; i < pattern.length; i += 2) {
            pattern[i] = (byte) 0xAA;
            if (i + 1 < pattern.length) pattern[i + 1] = 0x55;
        }
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET, pattern,
                "64-byte pattern: 0xAA55 repeated");

        pause(300);
        // Check if the pattern is echoed back
        System.out.println("  Checking if MCU echoes the pattern...");
        readBuf(getBuffer, mgr, DEV_AUDIO, FID_AVAH_READ, "  AVAH_READ_BUF (echo check)");
        readBuf(getBuffer, mgr, DEV_AUDIO, FID_AVAH_SET, "  AVAH_SET_BUF (echo check)");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop)");

        // Test 4: Incrementing sizes to find buffer limit
        System.out.println("\n--- Test 4: Buffer size limit detection ---");
        System.out.println("Theory: Find maximum accepted buffer size.\n");

        int[] sizes = {8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536};
        for (int size : sizes) {
            byte[] buf = new byte[size];
            // Fill with ascending pattern
            for (int i = 0; i < size; i++) buf[i] = (byte) (i & 0xFF);
            testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET, buf,
                    size + "-byte ascending pattern");
            pause(100);
        }
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "(cleanup: stop tone)");

        // Test 5: Empty buffer
        System.out.println("\n--- Test 5: Edge cases ---");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[0], "0 bytes: empty buffer");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF},
                "4 bytes: all 0xFF");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAH_SET,
                new byte[]{0x00, 0x00, 0x00, 0x00},
                "4 bytes: all 0x00");
    }

    // =========================================================================
    //  Probe 2: setBuffer on routing featureIds
    // =========================================================================

    static void probeBufferRoute(Method setBuffer, Method getInt, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: setBuffer on Routing FeatureIds");
        System.out.println("========================================\n");

        if (setBuffer == null) {
            System.out.println("[SKIP] setBuffer not available");
            return;
        }

        System.out.println("Theory: 0x32B1C042 (AVAS_TO_EXT_SPEAKER) fails with setInt.");
        System.out.println("Maybe it expects a buffer payload (multi-field command).\n");

        // AVAS_TO_EXT_SPEAKER_SET — try various buffer formats
        System.out.println("--- 0x32B1C042: AVAS_TO_EXT_SPEAKER ---");

        // Try as single-byte enable
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_TO_EXT,
                new byte[]{0x01},
                "AVAS_TO_EXT: {0x01}");

        // Try as 4-byte int (LE)
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_TO_EXT,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "AVAS_TO_EXT: 4-byte LE int 1");

        // Try as structured command: [source_id, dest_id, enable, 0]
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_TO_EXT,
                new byte[]{0x01, 0x01, 0x01, 0x00},
                "AVAS_TO_EXT: {src=1, dst=1, en=1, 0}");

        // Try with 8 bytes — might be a structured routing command
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_TO_EXT,
                new byte[]{0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00},
                "AVAS_TO_EXT: 8-byte (two LE ints: 1, 1)");

        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, 0x35203032, "AVAS_TO_EXT_STATUS (after buffer)");

        // EXT_SPEAKER_SWITCH — might also need buffer
        System.out.println("\n--- 0x1C10000E: EXT_SPEAKER_SWITCH ---");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_EXT_SPEAKER,
                new byte[]{0x01},
                "EXT_SPEAKER: {0x01}");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_EXT_SPEAKER,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "EXT_SPEAKER: 4-byte LE int 1");

        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, 0x35201040, "EXT_SPEAKER_STATUS (after buffer)");

        // AVAS_SOURCE — known to work with setInt, test buffer behavior difference
        System.out.println("\n--- 0x1B10003D: AVAS_SOURCE (normally works with setInt) ---");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_SOURCE,
                new byte[]{0x01},
                "AVAS_SOURCE: {0x01}");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_SOURCE,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "AVAS_SOURCE: 4-byte LE int 1");
        // Structured: maybe [source_type, volume, ?, ?]
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_AVAS_SOURCE,
                new byte[]{0x01, 0x50, 0x00, 0x00},
                "AVAS_SOURCE: {type=1, vol=0x50, 0, 0}");

        pause(300);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAS_TYPE, "AVAS_TYPE (after buffer)");

        // EXT_PROMPT_TONE_SOURCE
        System.out.println("\n--- 0x1B100043: EXT_PROMPT_TONE_SOURCE ---");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_EXT_PROMPT,
                new byte[]{0x01},
                "EXT_PROMPT: {0x01}");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_EXT_PROMPT,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "EXT_PROMPT: 4-byte LE int 1");

        // Loopback control
        System.out.println("\n--- 0xAA000301: LOOPBACK_PASSAGE_CONTROL ---");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_LOOPBACK,
                new byte[]{0x01},
                "LOOPBACK: {0x01}");
        testSetBuffer(setBuffer, mgr, DEV_AUDIO, FID_LOOPBACK,
                new byte[]{0x01, 0x00, 0x00, 0x00},
                "LOOPBACK: 4-byte LE int 1");
    }

    // =========================================================================
    //  Probe 3: getBuffer scan
    // =========================================================================

    static void probeGetBufferScan(Method getBuffer, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: getBuffer Scan (MCU Memory Read)");
        System.out.println("========================================\n");

        // Known featureIds — try reading as buffers
        System.out.println("--- Known FeatureIds via getBuffer ---");
        int[][] knownIds = {
            {FID_AVAH_SET,    DEV_AUDIO},
            {FID_AVAH_READ,   DEV_AUDIO},
            {FID_DSP_TYPE,    DEV_AUDIO},
            {FID_AVAS_TYPE,   DEV_AUDIO},
            {FID_DSP_READY,   DEV_AUDIO},
            {FID_AMP_TYPE,    DEV_AUDIO},
            {FID_SOUND_INFO,  DEV_AUDIO},
            {FID_AVAS_SOURCE, DEV_AUDIO},
            {FID_AVAS_TO_EXT, DEV_AUDIO},
            {FID_EXT_SPEAKER, DEV_AUDIO},
            {FID_EXT_PROMPT,  DEV_AUDIO},
            {FID_LOOPBACK,    DEV_AUDIO},
            {FID_DSP_CONTROL, DEV_AUDIO},
            {0x35201040, DEV_AUDIO},  // EXT_SPEAKER_SWITCH_STATUS
            {0x35203032, DEV_AUDIO},  // AVAS_TO_EXT_SPEAKER_STATUS
            {0x3520103F, DEV_AUDIO},  // EXT_PROMPT_TONE_STATUS
            {0x4C60002D, DEV_AUDIO},  // AVAS_SOUND_SOURCE_STATE
            {0x4FD00030, DEV_AUDIO},  // AMPLIFIER_CONFIG
            {0x4FD00040, DEV_AUDIO},  // ESS_AMPLIFIER_CONFIG
            {0x48F00010, DEV_ENGINE}, // ENGINE_SIM_SOURCE_TYPE
        };
        for (int[] id : knownIds) {
            readBuf(getBuffer, mgr, id[1], id[0], "fid=0x" + Integer.toHexString(id[0]));
        }

        // Sequential scan: 0x99000000 through 0x990002FF
        System.out.println("\n--- Sequential Scan: 0x99000000 - 0x990002FF ---");
        System.out.println("Looking for undocumented readable buffers...\n");
        int found = 0;
        for (int fid = 0x99000000; fid <= 0x990002FF; fid++) {
            byte[] buf = safeGetBuffer(getBuffer, mgr, DEV_AUDIO, fid);
            if (buf != null && buf.length > 0) {
                boolean nonZero = false;
                for (byte b : buf) {
                    if (b != 0) { nonZero = true; break; }
                }
                if (nonZero || buf.length > 4) {
                    System.out.print("  FOUND: 0x" + Integer.toHexString(fid)
                            + " len=" + buf.length + " hex=");
                    printHex(buf, 32);
                    System.out.println();
                    found++;
                }
            }
        }
        System.out.println("Sequential scan complete: " + found + " non-trivial buffers found");

        // Try reading AVAH-adjacent featureIds as buffers
        System.out.println("\n--- AVAH-adjacent getBuffer: 0x6EA700xx ---");
        found = 0;
        for (int fid = 0x6EA70000; fid <= 0x6EA7001F; fid++) {
            byte[] buf = safeGetBuffer(getBuffer, mgr, DEV_AUDIO, fid);
            if (buf != null && buf.length > 0) {
                System.out.print("  FOUND: 0x" + Integer.toHexString(fid)
                        + " len=" + buf.length + " hex=");
                printHex(buf, 32);
                System.out.println();
                found++;
            }
        }
        System.out.println("AVAH-adjacent scan: " + found + " readable buffers");

        // Try 0x6E97xxxx range as buffers too
        System.out.println("\n--- AVAH write-range getBuffer: 0x6E9700xx ---");
        found = 0;
        for (int fid = 0x6E970000; fid <= 0x6E97001F; fid++) {
            byte[] buf = safeGetBuffer(getBuffer, mgr, DEV_AUDIO, fid);
            if (buf != null && buf.length > 0) {
                System.out.print("  FOUND: 0x" + Integer.toHexString(fid)
                        + " len=" + buf.length + " hex=");
                printHex(buf, 32);
                System.out.println();
                found++;
            }
        }
        System.out.println("AVAH write-range scan: " + found + " readable buffers");
    }

    // =========================================================================
    //  Probe 4: Extreme int values on AVAH
    // =========================================================================

    static void probeExtremeValues(Method setInt, Method getInt, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: Extreme Values on AVAH");
        System.out.println("========================================\n");

        System.out.println("Theory: If MCU does not validate the value range,");
        System.out.println("extreme values may cause unexpected behavior.\n");

        // Read baseline
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "AVAH_STATE (baseline)");

        // Known good values for reference
        System.out.println("\n--- Known values (reference) ---");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "value=0 (stop)");
        pause(200);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  state after 0");

        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 1, "value=1 (1kHz)");
        pause(200);
        readInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ, "  state after 1");
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop)");

        // Boundary values
        System.out.println("\n--- Boundary values ---");
        int[] testValues = {
            -1,            // 0xFFFFFFFF as signed
            4,             // One above documented max (0-3)
            5, 10, 15, 16,
            127,           // Max signed byte
            128,           // Min unsigned byte + 1
            255,           // Max unsigned byte
            256,           // One above byte range
            0x7FFF,        // Max signed short
            0xFFFF,        // Max unsigned short
            0x7FFFFFFF,    // Max signed int (Integer.MAX_VALUE)
            0x7FFFFFFE,    // MAX_VALUE - 1
        };

        for (int val : testValues) {
            int r = writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, val,
                    "value=" + val + " (0x" + Integer.toHexString(val) + ")");
            pause(200);
            int state = safeGetInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ);
            System.out.println("  -> AVAH_STATE = " + state);
            // Stop any playing tone
            writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop)");
            pause(100);
        }

        // Special: Integer.MIN_VALUE and near it (cannot pass 0x80000000
        // directly as literal in Java int, but we can construct it)
        System.out.println("\n--- Negative / overflow values ---");
        int[] negValues = {
            Integer.MIN_VALUE,       // 0x80000000
            Integer.MIN_VALUE + 1,   // 0x80000001
            -2,                      // 0xFFFFFFFE
            -10011,                  // NOT_REGISTERED error code
            -2147482648,             // MCU_FAILED error code
        };

        for (int val : negValues) {
            int r = writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, val,
                    "value=" + val + " (0x" + Integer.toHexString(val) + ")");
            pause(200);
            int state = safeGetInt(getInt, mgr, DEV_AUDIO, FID_AVAH_READ);
            System.out.println("  -> AVAH_STATE = " + state);
            writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "  (stop)");
            pause(100);
        }

        // Final cleanup
        writeInt(setInt, mgr, DEV_AUDIO, FID_AVAH_SET, 0, "FINAL CLEANUP: stop tone");
    }

    // =========================================================================
    //  Probe 5: Scan featureIds near AVAH (0x6E97xxxx)
    // =========================================================================

    static void probeScanNearby(Method setInt, Method getInt, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: Scan FeatureIds Near AVAH");
        System.out.println("========================================\n");

        // 0x6E97xxxx: AVAH write range
        System.out.println("--- Write range: 0x6E970000 - 0x6E97001F ---");
        System.out.println("Sending setInt(1002, fid, 1) for each...\n");
        int accepted = 0;
        for (int fid = 0x6E970000; fid <= 0x6E97001F; fid++) {
            int r = safeSetInt(setInt, mgr, DEV_AUDIO, fid, 1);
            String tag = (fid == FID_AVAH_SET) ? " <-- KNOWN AVAH" : "";
            System.out.println("  0x" + Integer.toHexString(fid)
                    + " setInt(1) = " + resultStr(r) + tag);
            if (r == 0) {
                accepted++;
                // Stop any tone we may have started
                safeSetInt(setInt, mgr, DEV_AUDIO, fid, 0);
            }
            pause(50);
        }
        System.out.println("Accepted: " + accepted + " / 32");

        // 0x6EA7xxxx: AVAH read range
        System.out.println("\n--- Read range: 0x6EA70000 - 0x6EA7001F ---");
        System.out.println("Reading getInt(1002, fid) for each...\n");
        int readable = 0;
        for (int fid = 0x6EA70000; fid <= 0x6EA7001F; fid++) {
            int val = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
            String tag = (fid == FID_AVAH_READ) ? " <-- KNOWN AVAH_READ" : "";
            if (val != -10011 && val != -10013) {
                System.out.println("  0x" + Integer.toHexString(fid) + " = " + val + tag);
                readable++;
            } else {
                System.out.println("  0x" + Integer.toHexString(fid)
                        + " = " + resultStr(val) + tag);
            }
        }
        System.out.println("Readable: " + readable + " / 32");

        // Wider scan: other 0x6Exx0010 patterns — maybe other test commands
        System.out.println("\n--- Pattern scan: 0x6Exx0010 (test command pattern) ---");
        System.out.println("The AVAH featureId is 0x6E970010. Trying nearby prefixes...\n");
        for (int prefix = 0x6E90; prefix <= 0x6EA0; prefix++) {
            int fid = (prefix << 16) | 0x0010;
            int rSet = safeSetInt(setInt, mgr, DEV_AUDIO, fid, 1);
            int rGet = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
            if (rSet == 0 || (rGet != -10011 && rGet != -10013)) {
                System.out.println("  FOUND: 0x" + Integer.toHexString(fid)
                        + " set=" + resultStr(rSet) + " get=" + rGet);
                if (rSet == 0) safeSetInt(setInt, mgr, DEV_AUDIO, fid, 0);
            }
        }

        // Try different device types for AVAH featureId
        System.out.println("\n--- AVAH on different device types ---");
        int[] devices = {1001, 1002, 1003, 1004, 1005, 1010, 1011, 1020, 1022, 1032, 1041};
        for (int dev : devices) {
            int rSet = safeSetInt(setInt, mgr, dev, FID_AVAH_SET, 1);
            int rGet = safeGetInt(getInt, mgr, dev, FID_AVAH_READ);
            System.out.println("  dev=" + dev + " set=" + resultStr(rSet) + " get=" + rGet);
            if (rSet == 0) safeSetInt(setInt, mgr, dev, FID_AVAH_SET, 0);
        }
    }

    // =========================================================================
    //  Probe 6: Scan test featureId range (0xAA0001xx - 0xAA0003xx)
    // =========================================================================

    static void probeScanTest(Method setInt, Method getInt, Object mgr) {
        System.out.println("========================================");
        System.out.println("  Probe: Scan Test FeatureId Range");
        System.out.println("========================================\n");

        // 0xAA0001xx: known to have some test commands
        System.out.println("--- Range 0xAA000100 - 0xAA0001FF (test read) ---");
        int found = 0;
        for (int fid = 0xAA000100; fid <= 0xAA0001FF; fid++) {
            int val = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
            if (val != -10011 && val != -10013) {
                System.out.println("  0x" + Integer.toHexString(fid) + " getInt = " + val);
                found++;
            }
        }
        System.out.println("Found: " + found + " readable in 0xAA0001xx\n");

        // 0xAA0002xx
        System.out.println("--- Range 0xAA000200 - 0xAA0002FF (test commands) ---");
        found = 0;
        for (int fid = 0xAA000200; fid <= 0xAA0002FF; fid++) {
            int val = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
            if (val != -10011 && val != -10013) {
                System.out.println("  0x" + Integer.toHexString(fid) + " getInt = " + val);
                found++;
            }
        }
        System.out.println("Found: " + found + " readable in 0xAA0002xx\n");

        // 0xAA0003xx
        System.out.println("--- Range 0xAA000300 - 0xAA0003FF (test controls) ---");
        found = 0;
        for (int fid = 0xAA000300; fid <= 0xAA0003FF; fid++) {
            int val = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
            if (val != -10011 && val != -10013) {
                System.out.println("  0x" + Integer.toHexString(fid) + " getInt = " + val);
                found++;
            }
        }
        System.out.println("Found: " + found + " readable in 0xAA0003xx\n");

        // Try writing to discovered readable ones in 0xAA range
        System.out.println("--- Write scan: 0xAA000100 - 0xAA0003FF ---");
        System.out.println("Trying setInt(1002, fid, 1) for 0xAA range...\n");
        found = 0;
        for (int fid = 0xAA000100; fid <= 0xAA0003FF; fid++) {
            int r = safeSetInt(setInt, mgr, DEV_AUDIO, fid, 1);
            if (r == 0) {
                System.out.println("  ACCEPTED: 0x" + Integer.toHexString(fid)
                        + " setInt(1) = SUCCESS");
                found++;
                // Read back to see effect
                int readback = safeGetInt(getInt, mgr, DEV_AUDIO, fid);
                System.out.println("    readback: " + readback);
                // Reset to 0
                safeSetInt(setInt, mgr, DEV_AUDIO, fid, 0);
            }
        }
        System.out.println("Write-accepted: " + found + " in 0xAA range");
    }

    // =========================================================================
    //  Method dump
    // =========================================================================

    static void dumpMethods(Object mgr) {
        System.out.println("========================================");
        System.out.println("  BYDAutoManager Method Dump");
        System.out.println("========================================\n");

        System.out.println("Class: " + mgr.getClass().getName());
        System.out.println("Superclass: " + mgr.getClass().getSuperclass().getName());
        System.out.println();

        System.out.println("--- Declared Methods ---");
        for (Method m : mgr.getClass().getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getSimpleName() + " "
                    + m.getName() + "(" + formatParams(m.getParameterTypes()) + ")");
        }

        System.out.println("\n--- All Public Methods (excluding Object) ---");
        for (Method m : mgr.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            System.out.println("  " + m.getReturnType().getSimpleName() + " "
                    + m.getName() + "(" + formatParams(m.getParameterTypes()) + ")");
        }
    }

    // =========================================================================
    //  Helper methods
    // =========================================================================

    static void testSetBuffer(Method setBuffer, Object mgr, int dev, int fid,
                              byte[] data, String label) {
        try {
            Object result = setBuffer.invoke(mgr, dev, fid, data);
            System.out.println("  setBuffer " + label + " -> " + resultStr(result));
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().toString() : e.toString();
            System.out.println("  setBuffer " + label + " -> EXCEPTION: " + cause);
        }
    }

    static int writeInt(Method setInt, Object mgr, int dev, int fid, int val, String label) {
        try {
            int r = (int) setInt.invoke(mgr, dev, fid, val);
            System.out.println("  setInt " + label + " [0x" + Integer.toHexString(fid)
                    + "] = " + resultStr(r));
            return r;
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().toString() : e.toString();
            System.out.println("  setInt " + label + " [0x" + Integer.toHexString(fid)
                    + "] = EXCEPTION: " + cause);
            return -99999;
        }
    }

    static void readInt(Method getInt, Object mgr, int dev, int fid, String label) {
        try {
            int v = (int) getInt.invoke(mgr, dev, fid);
            System.out.println("  getInt " + label + " [0x" + Integer.toHexString(fid)
                    + "] = " + v + resultHint(v));
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().toString() : e.toString();
            System.out.println("  getInt " + label + " [0x" + Integer.toHexString(fid)
                    + "] = EXCEPTION: " + cause);
        }
    }

    static void readBuf(Method getBuffer, Object mgr, int dev, int fid, String label) {
        try {
            byte[] buf = (byte[]) getBuffer.invoke(mgr, dev, fid);
            if (buf == null) {
                System.out.println("  getBuffer " + label + " [0x" + Integer.toHexString(fid)
                        + "] = null");
                return;
            }
            System.out.print("  getBuffer " + label + " [0x" + Integer.toHexString(fid)
                    + "] len=" + buf.length + " hex=");
            printHex(buf, 64);
            System.out.println();
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().toString() : e.toString();
            System.out.println("  getBuffer " + label + " [0x" + Integer.toHexString(fid)
                    + "] = EXCEPTION: " + cause);
        }
    }

    static int safeSetInt(Method setInt, Object mgr, int dev, int fid, int val) {
        try {
            return (int) setInt.invoke(mgr, dev, fid, val);
        } catch (Exception e) {
            return -99999;
        }
    }

    static int safeGetInt(Method getInt, Object mgr, int dev, int fid) {
        try {
            return (int) getInt.invoke(mgr, dev, fid);
        } catch (Exception e) {
            return -99999;
        }
    }

    static byte[] safeGetBuffer(Method getBuffer, Object mgr, int dev, int fid) {
        try {
            return (byte[]) getBuffer.invoke(mgr, dev, fid);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] generate440HzPCM(int sampleRate, int durationSec) {
        int numSamples = sampleRate * durationSec;
        byte[] pcm = new byte[numSamples * 2]; // 16-bit samples
        for (int i = 0; i < numSamples; i++) {
            short sample = (short) (Short.MAX_VALUE * 0.8
                    * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate));
            // Little-endian 16-bit
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }

    static void printHex(byte[] buf, int maxBytes) {
        int limit = Math.min(buf.length, maxBytes);
        for (int i = 0; i < limit; i++) {
            System.out.printf("%02x", buf[i]);
        }
        if (buf.length > maxBytes) {
            System.out.print("... (" + buf.length + " total)");
        }
    }

    static String resultStr(Object r) {
        if (r == null) return "null";
        int v;
        try {
            v = (int) r;
        } catch (ClassCastException e) {
            return r.toString();
        }
        switch (v) {
            case 0: return "SUCCESS (0)";
            case -10011: return "NOT_REGISTERED (-10011)";
            case -10013: return "NOT_AVAILABLE (-10013)";
            case -2147482648: return "MCU_FAILED (-2147482648)";
            case -2147482647: return "MCU_BUSY (-2147482647)";
            case -2147482646: return "MCU_TIMEOUT (-2147482646)";
            case -2147482645: return "INVALID_VALUE (-2147482645)";
            default: return String.valueOf(v);
        }
    }

    static String resultHint(int v) {
        switch (v) {
            case -10011: return " (not registered)";
            case -10013: return " (not available)";
            case -2147482648: return " (MCU failed)";
            case -2147482647: return " (MCU busy)";
            case -2147482646: return " (MCU timeout)";
            case -2147482645: return " (invalid value)";
            default: return "";
        }
    }

    static String formatParams(Class<?>[] params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getSimpleName());
        }
        return sb.toString();
    }

    static void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    static void printUsage() {
        System.out.println("BydMcuProbe — MCU command processing probe");
        System.out.println();
        System.out.println("Usage: BydMcuProbe <command>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  buffer_avah     Test setBuffer on AVAH featureId (0x6E970010)");
        System.out.println("                  - Small buffers, PCM audio injection, pattern echo");
        System.out.println("                  - Buffer size limit detection");
        System.out.println();
        System.out.println("  buffer_route    Test setBuffer on routing featureIds");
        System.out.println("                  - 0x32B1C042 (AVAS_TO_EXT_SPEAKER) — fails w/ setInt");
        System.out.println("                  - 0x1C10000E, 0x1B10003D, 0x1B100043, 0xAA000301");
        System.out.println();
        System.out.println("  getbuf_scan     Scan getBuffer on known + sequential featureIds");
        System.out.println("                  - 0x99000000 - 0x990002FF");
        System.out.println("                  - 0x6EA700xx, 0x6E9700xx");
        System.out.println();
        System.out.println("  extreme_values  Test extreme int values on AVAH");
        System.out.println("                  - -1, MAX_INT, MIN_INT, error codes as values");
        System.out.println();
        System.out.println("  scan_nearby     Scan featureIds near AVAH (0x6E97xxxx range)");
        System.out.println("                  - Write scan, read scan, pattern scan, device scan");
        System.out.println();
        System.out.println("  scan_test       Scan test featureId range (0xAA0001xx - 0xAA0003FF)");
        System.out.println("                  - Read scan, write scan with readback");
        System.out.println();
        System.out.println("  methods         Dump all BYDAutoManager methods");
        System.out.println();
        System.out.println("  all             Run all probes sequentially");
        System.out.println();
        System.out.println("Build:");
        System.out.println("  javac -source 11 -target 11 -d /tmp/bydprobe scripts/BydMcuProbe.java");
        System.out.println("  d8 --output /tmp/bydprobe /tmp/bydprobe/BydMcuProbe.class");
        System.out.println("  adb push /tmp/bydprobe/classes.dex /data/local/tmp/bydprobe.dex");
        System.out.println("  adb shell \"CLASSPATH=/data/local/tmp/bydprobe.dex app_process /data/local/tmp BydMcuProbe <command>\"");
    }
}
