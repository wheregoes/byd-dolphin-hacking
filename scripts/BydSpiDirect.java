import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Direct SPI access to the MCU, bypassing the entire Binder/HAL stack.
 * Crafts raw SPI packets and writes them to /dev/spidev_ivi.
 *
 * SPI packet format (from auto.default.so reverse engineering):
 *   [featureId_BE:4][dataLen:1][data:dataLen]
 *   No CRC, no HMAC, no sequence numbers.
 *
 * Response: 260 bytes, first 4 bytes = header (0x9900001D = valid)
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydspi scripts/BydSpiDirect.java
 *   d8 --output /tmp/bydspi /tmp/bydspi/BydSpiDirect.class
 *   adb push /tmp/bydspi/classes.dex /data/local/tmp/bydspi.dex
 *   adb shell "CLASSPATH=/data/local/tmp/bydspi.dex app_process /data/local/tmp BydSpiDirect <command>"
 *
 * Commands:
 *   send_int <fid_hex> <value>     - send int command via raw SPI
 *   send_buf <fid_hex> <hex_data>  - send buffer via raw SPI
 *   query <fid_hex>                - query featureId (dataLen=0)
 *   read_raw                       - read 260 bytes from SPI and dump
 *   scan_unregistered              - test featureIds not in Java config
 *   pcm_stream <fid_hex>           - stream PCM tone via rapid small buffers
 *   wake                           - wake MCU via sysfs
 */
public class BydSpiDirect {

    static Object spiFileDescriptor;
    static Method osWrite;
    static Method osRead;
    static Method osClose;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: BydSpiDirect <command> [args...]");
            System.out.println("Commands: send_int, send_buf, query, read_raw, scan_unregistered, pcm_stream, wake");
            return;
        }

        try {
            String cmd = args[0];

            if (cmd.equals("wake")) {
                wakeMcu();
                return;
            }

            openSpi();

            switch (cmd) {
                case "send_int":
                    if (args.length < 3) { System.out.println("Usage: send_int <fid_hex> <value>"); break; }
                    cmdSendInt(parseHex(args[1]), Integer.parseInt(args[2]));
                    break;
                case "send_buf":
                    if (args.length < 3) { System.out.println("Usage: send_buf <fid_hex> <hex_data>"); break; }
                    cmdSendBuf(parseHex(args[1]), hexToBytes(args[2]));
                    break;
                case "query":
                    if (args.length < 2) { System.out.println("Usage: query <fid_hex>"); break; }
                    cmdQuery(parseHex(args[1]));
                    break;
                case "read_raw":
                    cmdReadRaw();
                    break;
                case "scan_unregistered":
                    cmdScanUnregistered();
                    break;
                case "pcm_stream":
                    if (args.length < 2) { System.out.println("Usage: pcm_stream <fid_hex>"); break; }
                    cmdPcmStream(parseHex(args[1]));
                    break;
                case "large_buf":
                    if (args.length < 2) { System.out.println("Usage: large_buf <fid_hex>"); break; }
                    cmdLargeBuf(parseHex(args[1]));
                    break;
                default:
                    System.out.println("Unknown command: " + cmd);
            }

            closeSpi();
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void openSpi() throws Exception {
        Class<?> osClass = Class.forName("android.system.Os");
        Class<?> osConstClass = Class.forName("android.system.OsConstants");
        Class<?> fdClass = Class.forName("java.io.FileDescriptor");

        int O_RDWR = osConstClass.getField("O_RDWR").getInt(null);
        int O_NONBLOCK = osConstClass.getField("O_NONBLOCK").getInt(null);

        Method openMethod = osClass.getMethod("open", String.class, int.class, int.class);
        osWrite = osClass.getMethod("write", fdClass, byte[].class, int.class, int.class);
        osRead = osClass.getMethod("read", fdClass, byte[].class, int.class, int.class);
        osClose = osClass.getMethod("close", fdClass);

        spiFileDescriptor = openMethod.invoke(null, "/dev/spidev_ivi", O_RDWR | O_NONBLOCK, 0);
        System.out.println("[SPI] Opened /dev/spidev_ivi (O_RDWR|O_NONBLOCK)");
    }

    static void closeSpi() throws Exception {
        if (spiFileDescriptor != null) {
            osClose.invoke(null, spiFileDescriptor);
            System.out.println("[SPI] Closed");
        }
    }

    static int spiWrite(byte[] data) throws Exception {
        return (int) osWrite.invoke(null, spiFileDescriptor, data, 0, data.length);
    }

    static byte[] spiRead(int size) throws Exception {
        byte[] buf = new byte[size];
        int n = (int) osRead.invoke(null, spiFileDescriptor, buf, 0, size);
        if (n < 0) return new byte[0];
        if (n < size) {
            byte[] trimmed = new byte[n];
            System.arraycopy(buf, 0, trimmed, 0, n);
            return trimmed;
        }
        return buf;
    }

    // Build SPI packet: [featureId_BE:4][dataLen:1][data:N]
    static byte[] buildPacket(int featureId, byte[] data) {
        int dataLen = (data == null) ? 0 : data.length;
        byte[] pkt = new byte[5 + dataLen];
        pkt[0] = (byte) ((featureId >> 24) & 0xFF);
        pkt[1] = (byte) ((featureId >> 16) & 0xFF);
        pkt[2] = (byte) ((featureId >> 8) & 0xFF);
        pkt[3] = (byte) (featureId & 0xFF);
        pkt[4] = (byte) dataLen;
        if (data != null) System.arraycopy(data, 0, pkt, 5, dataLen);
        return pkt;
    }

    static byte[] buildIntPacket(int featureId, int value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return buildPacket(featureId, data);
    }

    static byte[] buildQueryPacket(int featureId) {
        return buildPacket(featureId, null);
    }

    static void dumpPacket(String label, byte[] pkt) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" [").append(pkt.length).append(" bytes]: ");
        for (int i = 0; i < Math.min(pkt.length, 64); i++) {
            sb.append(String.format("%02x", pkt[i] & 0xFF));
        }
        if (pkt.length > 64) sb.append("...");
        System.out.println(sb.toString());
    }

    static void dumpResponse(byte[] resp) {
        if (resp.length < 4) {
            System.out.println("[SPI] Response too short: " + resp.length + " bytes");
            return;
        }

        int header = ((resp[0] & 0xFF) << 24) | ((resp[1] & 0xFF) << 16) |
                     ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        boolean valid = (header == 0x9900001D);
        System.out.println("[SPI] Response header: 0x" + String.format("%08x", header) +
                           (valid ? " (VALID)" : " (UNKNOWN)"));

        // Parse records after header
        int offset = 4;
        int recordNum = 0;
        while (offset + 5 <= resp.length) {
            int fid = ((resp[offset] & 0xFF) << 24) | ((resp[offset+1] & 0xFF) << 16) |
                      ((resp[offset+2] & 0xFF) << 8) | (resp[offset+3] & 0xFF);
            int dLen = resp[offset + 4] & 0xFF;

            if (fid == 0 && dLen == 0) break; // end marker

            if (offset + 5 + dLen > resp.length) break;

            StringBuilder dataSb = new StringBuilder();
            for (int i = 0; i < dLen && i < 32; i++) {
                dataSb.append(String.format("%02x", resp[offset + 5 + i] & 0xFF));
            }
            if (dLen > 32) dataSb.append("...");

            System.out.println("  Record " + recordNum + ": fid=0x" + String.format("%08x", fid) +
                               " len=" + dLen + " data=" + dataSb.toString());

            // If it looks like an int (4 bytes), also decode as int
            if (dLen == 4) {
                int val = ((resp[offset+5] & 0xFF) << 24) | ((resp[offset+6] & 0xFF) << 16) |
                          ((resp[offset+7] & 0xFF) << 8) | (resp[offset+8] & 0xFF);
                System.out.println("           as int: " + val + " (0x" + String.format("%08x", val) + ")");
            }

            offset += 5 + dLen;
            recordNum++;
        }
        if (recordNum == 0) {
            System.out.println("  (no records parsed, dumping raw hex)");
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 4; i < Math.min(resp.length, 68); i++) {
                sb.append(String.format("%02x", resp[i] & 0xFF));
            }
            if (resp.length > 68) sb.append("...");
            System.out.println(sb.toString());
        }
    }

    // --- Commands ---

    static void cmdSendInt(int fid, int value) throws Exception {
        System.out.println("=== Send Int: fid=0x" + String.format("%08x", fid) +
                           " value=" + value + " ===");
        byte[] pkt = buildIntPacket(fid, value);
        dumpPacket("TX", pkt);

        int written = spiWrite(pkt);
        System.out.println("[SPI] Wrote " + written + " bytes");

        Thread.sleep(100);

        try {
            byte[] resp = spiRead(260);
            System.out.println("[SPI] Read " + resp.length + " bytes");
            dumpResponse(resp);
        } catch (Exception e) {
            System.out.println("[SPI] Read failed (expected with O_NONBLOCK): " + e.getCause());
        }
    }

    static void cmdSendBuf(int fid, byte[] data) throws Exception {
        System.out.println("=== Send Buffer: fid=0x" + String.format("%08x", fid) +
                           " len=" + data.length + " ===");
        byte[] pkt = buildPacket(fid, data);
        dumpPacket("TX", pkt);

        int written = spiWrite(pkt);
        System.out.println("[SPI] Wrote " + written + " bytes");

        Thread.sleep(100);

        try {
            byte[] resp = spiRead(260);
            System.out.println("[SPI] Read " + resp.length + " bytes");
            dumpResponse(resp);
        } catch (Exception e) {
            System.out.println("[SPI] Read: " + e.getCause());
        }
    }

    static void cmdQuery(int fid) throws Exception {
        System.out.println("=== Query: fid=0x" + String.format("%08x", fid) + " ===");
        byte[] pkt = buildQueryPacket(fid);
        dumpPacket("TX", pkt);

        int written = spiWrite(pkt);
        System.out.println("[SPI] Wrote " + written + " bytes");

        // Read multiple times — MCU may need time to respond
        for (int attempt = 0; attempt < 5; attempt++) {
            Thread.sleep(50);
            try {
                byte[] resp = spiRead(260);
                if (resp.length > 0) {
                    System.out.println("[SPI] Read " + resp.length + " bytes (attempt " + attempt + ")");
                    dumpResponse(resp);
                    return;
                }
            } catch (Exception e) {
                // O_NONBLOCK may return EAGAIN
            }
        }
        System.out.println("[SPI] No response after 5 attempts");
    }

    static void cmdReadRaw() throws Exception {
        System.out.println("=== Raw SPI Read (260 bytes) ===");
        try {
            byte[] resp = spiRead(260);
            System.out.println("[SPI] Read " + resp.length + " bytes");
            dumpResponse(resp);

            // Also dump full hex
            StringBuilder sb = new StringBuilder("Full hex: ");
            for (int i = 0; i < resp.length; i++) {
                sb.append(String.format("%02x", resp[i] & 0xFF));
                if (i % 32 == 31) { System.out.println(sb.toString()); sb = new StringBuilder("         "); }
            }
            if (sb.length() > 9) System.out.println(sb.toString());
        } catch (Exception e) {
            System.out.println("[SPI] Read failed: " + e.getCause());
        }
    }

    static void cmdScanUnregistered() throws Exception {
        System.out.println("=== Scan Unregistered FeatureIds via Raw SPI ===\n");
        System.out.println("These featureIds are NOT in the Java FeatureMapper but may be");
        System.out.println("accepted by the MCU firmware directly.\n");

        // FeatureIds to try: ranges around known working ones,
        // plus patterns from other BYD models
        int[] testFids = {
            // AVAH debug range (0x6E99xxxx)
            0x6E990000, 0x6E990001, 0x6E990002, 0x6E990003,
            0x6E990004, 0x6E990005, 0x6E990008, 0x6E990009,
            0x6E990010, 0x6E990011, 0x6E990012, 0x6E990020,
            0x6E990030, 0x6E990040, 0x6E990050,

            // AVAS-related patterns
            0x1B10003E, 0x1B10003F, 0x1B100040, 0x1B100041,
            0x1B100042, 0x1B100044, 0x1B100045,

            // External speaker range
            0x1C10000F, 0x1C100010, 0x1C100011, 0x1C100012,

            // Audio routing range
            0x32B1C040, 0x32B1C041, 0x32B1C043, 0x32B1C044,

            // DSP control range
            0xAA000144, 0xAA000146, 0xAA000147,

            // AVAS amp control (guesses)
            0xAA000104, 0xAA000105, 0xAA000106,
            0xAA000170, 0xAA000171, 0xAA000172,

            // Sound playback (guesses)
            0xAA000150, 0xAA000151, 0xAA000152, 0xAA000153,
            0xAA000154, 0xAA000155,

            // OTA/flash related
            0xAA000223, 0xAA000224, 0xAA000225,

            // DSP debug
            0x6E990100, 0x6E990200, 0x6E990300,
            0x6E9A0010, 0x6E9B0010, 0x6E9C0010,
        };

        for (int fid : testFids) {
            byte[] pkt = buildIntPacket(fid, 1);
            try {
                int written = spiWrite(pkt);
                Thread.sleep(50);
                try {
                    byte[] resp = spiRead(260);
                    if (resp.length >= 4) {
                        int header = ((resp[0] & 0xFF) << 24) | ((resp[1] & 0xFF) << 16) |
                                     ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
                        if (header == 0x9900001D) {
                            System.out.println("  0x" + String.format("%08x", fid) +
                                               " -> VALID response (header=0x9900001D)");
                            // Parse first record
                            if (resp.length > 9) {
                                int respFid = ((resp[4] & 0xFF) << 24) | ((resp[5] & 0xFF) << 16) |
                                              ((resp[6] & 0xFF) << 8) | (resp[7] & 0xFF);
                                int dLen = resp[8] & 0xFF;
                                StringBuilder data = new StringBuilder();
                                for (int i = 0; i < Math.min(dLen, 16); i++) {
                                    data.append(String.format("%02x", resp[9 + i] & 0xFF));
                                }
                                System.out.println("         resp_fid=0x" + String.format("%08x", respFid) +
                                                   " len=" + dLen + " data=" + data.toString());
                            }
                        } else {
                            System.out.println("  0x" + String.format("%08x", fid) +
                                               " -> response header=0x" + String.format("%08x", header));
                        }
                    }
                } catch (Exception e) {
                    // no response = MCU ignored it
                }
            } catch (Exception e) {
                System.out.println("  0x" + String.format("%08x", fid) + " -> write error: " + e.getCause());
            }
        }
        System.out.println("\nDone.");
    }

    static void cmdPcmStream(int fid) throws Exception {
        System.out.println("=== PCM Stream via SPI to fid=0x" + String.format("%08x", fid) + " ===\n");
        System.out.println("Streaming 440Hz PCM in 240-byte chunks directly via SPI.");
        System.out.println("Max record data = 247 bytes (252 write limit - 5 header).\n");

        int sampleRate = 8000;
        int durationMs = 5000;
        int totalSamples = sampleRate * durationMs / 1000;
        int chunkSamples = 120; // 120 samples * 2 bytes = 240 bytes < 247 max
        int chunkBytes = chunkSamples * 2;

        System.out.println("Sample rate: " + sampleRate + " Hz");
        System.out.println("Chunk: " + chunkSamples + " samples = " + chunkBytes + " bytes");
        System.out.println("Duration: " + durationMs + " ms");
        System.out.println("Total chunks: " + (totalSamples / chunkSamples) + "\n");

        long startTime = System.currentTimeMillis();
        int sampleOffset = 0;

        while (sampleOffset < totalSamples) {
            int samplesThisChunk = Math.min(chunkSamples, totalSamples - sampleOffset);
            byte[] pcmData = new byte[samplesThisChunk * 2];

            for (int i = 0; i < samplesThisChunk; i++) {
                int sample = sampleOffset + i;
                short val = (short) (Short.MAX_VALUE * 0.8 * Math.sin(2.0 * Math.PI * 440.0 * sample / sampleRate));
                // Little-endian PCM
                pcmData[i * 2] = (byte) (val & 0xFF);
                pcmData[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
            }

            byte[] pkt = buildPacket(fid, pcmData);
            spiWrite(pkt);

            sampleOffset += samplesThisChunk;

            // Pace to real-time
            long elapsed = System.currentTimeMillis() - startTime;
            long expectedMs = (long) sampleOffset * 1000 / sampleRate;
            long sleepMs = expectedMs - elapsed;
            if (sleepMs > 0) Thread.sleep(sleepMs);

            if (sampleOffset % (chunkSamples * 10) == 0) {
                System.out.println("  Streamed " + sampleOffset + "/" + totalSamples + " samples");
            }
        }

        System.out.println("Stream complete. " + sampleOffset + " samples sent.");
    }

    static void cmdLargeBuf(int fid) throws Exception {
        System.out.println("=== Large Buffer Test: fid=0x" + String.format("%08x", fid) + " ===\n");
        System.out.println("Testing buffer sizes 129-247 bytes (bypasses Java 128-byte limit).\n");

        int[] sizes = {129, 130, 140, 150, 160, 180, 200, 220, 240, 247};
        for (int size : sizes) {
            byte[] data = new byte[size];
            // Fill with ascending pattern
            for (int i = 0; i < size; i++) data[i] = (byte) (i & 0xFF);

            byte[] pkt = buildPacket(fid, data);
            try {
                int written = spiWrite(pkt);
                Thread.sleep(50);
                String result = "wrote " + written + " bytes";

                try {
                    byte[] resp = spiRead(260);
                    if (resp.length >= 4) {
                        int header = ((resp[0] & 0xFF) << 24) | ((resp[1] & 0xFF) << 16) |
                                     ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
                        result += " resp=0x" + String.format("%08x", header);
                    }
                } catch (Exception e) {
                    result += " no_response";
                }

                System.out.println("  " + size + " bytes -> " + result);
            } catch (Exception e) {
                System.out.println("  " + size + " bytes -> ERROR: " + e.getCause());
            }
        }
    }

    // --- Helpers ---

    static int parseHex(String s) {
        s = s.toLowerCase().replace("0x", "");
        return (int) Long.parseLong(s, 16);
    }

    static byte[] hexToBytes(String hex) {
        hex = hex.replace("0x", "").replace(" ", "");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static void wakeMcu() throws Exception {
        System.out.println("=== Waking MCU via sysfs ===");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream("/sys/qc_mcu/qc_wakeup_mcu");
            fos.write("1".getBytes());
            fos.close();
            System.out.println("Wrote '1' to /sys/qc_mcu/qc_wakeup_mcu");
        } catch (Exception e) {
            System.out.println("Wake failed: " + e);
        }
    }
}
