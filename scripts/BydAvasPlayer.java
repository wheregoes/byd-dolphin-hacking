import java.lang.reflect.Method;

/**
 * Plays melodies and audio on the AVAS external speaker via frequency control.
 * Uses setBuffer on AVAH (0x6E970010) with frequency-encoded data.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydplay scripts/BydAvasPlayer.java
 *   d8 --output /tmp/bydplay /tmp/bydplay/BydAvasPlayer.class
 *   adb push /tmp/bydplay/classes.dex /data/local/tmp/bydplay.dex
 *   adb shell "CLASSPATH=/data/local/tmp/bydplay.dex app_process /data/local/tmp BydAvasPlayer <command>"
 *
 * Commands:
 *   melody           - play a recognizable melody
 *   scale            - play a musical scale (C4 to C5)
 *   siren            - play a siren sweep
 *   imperial         - play Imperial March
 *   pcm_stream       - attempt PCM chunk streaming
 *   freq_test        - sweep frequencies to map exact range
 *   stop             - stop any playing tone
 */
public class BydAvasPlayer {
    static Object mgr;
    static Method setInt, setBuffer;
    static final int DEV = 1002;
    static final int AVAH = 0x6E970010;

    // Musical note frequencies (Hz)
    static final int C4 = 262, D4 = 294, E4 = 330, F4 = 349, G4 = 392;
    static final int A4 = 440, B4 = 494, C5 = 523, D5 = 587, E5 = 659;
    static final int F5 = 698, G5 = 784, A5 = 880, B5 = 988, C6 = 1047;
    static final int REST = 0;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Commands: melody, scale, siren, imperial, pcm_stream, freq_test, stop");
            return;
        }
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            setInt = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            setBuffer = mgr.getClass().getMethod("setBuffer", int.class, int.class, byte[].class);

            switch (args[0]) {
                case "melody": playMelody(); break;
                case "scale": playScale(); break;
                case "siren": playSiren(); break;
                case "imperial": playImperial(); break;
                case "pcm_stream": pcmStream(); break;
                case "freq_test": freqTest(); break;
                case "stop": stop(); break;
                default: System.out.println("Unknown: " + args[0]);
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void setFreq(int freqHz) throws Exception {
        if (freqHz <= 0) {
            setInt.invoke(mgr, DEV, AVAH, 0);
            return;
        }
        byte[] buf = {(byte)((freqHz >> 8) & 0xFF), (byte)(freqHz & 0xFF)};
        setBuffer.invoke(mgr, DEV, AVAH, buf);
    }

    static void playNote(int freqHz, int durationMs) throws Exception {
        setFreq(freqHz);
        Thread.sleep(durationMs);
    }

    static void playNotes(int[][] notes) throws Exception {
        for (int[] note : notes) {
            int freq = note[0];
            int dur = note[1];
            if (freq == REST) {
                setFreq(0);
                Thread.sleep(dur);
            } else {
                setFreq(freq);
                Thread.sleep(dur);
                // Brief gap between notes for articulation
                setFreq(0);
                Thread.sleep(30);
            }
        }
        setFreq(0);
    }

    static void stop() throws Exception {
        setInt.invoke(mgr, DEV, AVAH, 0);
        System.out.println("Stopped.");
    }

    // --- Melodies ---

    static void playScale() throws Exception {
        System.out.println("=== Musical Scale C4 to C5 ===");
        System.out.println("Listen outside the car!\n");

        int[] scale = {C4, D4, E4, F4, G4, A4, B4, C5};
        for (int freq : scale) {
            System.out.println("  " + freq + " Hz");
            playNote(freq, 400);
            setFreq(0);
            Thread.sleep(50);
        }
        // Descending
        for (int i = scale.length - 1; i >= 0; i--) {
            System.out.println("  " + scale[i] + " Hz");
            playNote(scale[i], 400);
            setFreq(0);
            Thread.sleep(50);
        }
        setFreq(0);
        System.out.println("Done.");
    }

    static void playMelody() throws Exception {
        System.out.println("=== Twinkle Twinkle Little Star ===");
        System.out.println("Listen outside the car!\n");

        int[][] notes = {
            {C4, 400}, {C4, 400}, {G4, 400}, {G4, 400},
            {A4, 400}, {A4, 400}, {G4, 800},
            {F4, 400}, {F4, 400}, {E4, 400}, {E4, 400},
            {D4, 400}, {D4, 400}, {C4, 800},
            {G4, 400}, {G4, 400}, {F4, 400}, {F4, 400},
            {E4, 400}, {E4, 400}, {D4, 800},
            {G4, 400}, {G4, 400}, {F4, 400}, {F4, 400},
            {E4, 400}, {E4, 400}, {D4, 800},
            {C4, 400}, {C4, 400}, {G4, 400}, {G4, 400},
            {A4, 400}, {A4, 400}, {G4, 800},
            {F4, 400}, {F4, 400}, {E4, 400}, {E4, 400},
            {D4, 400}, {D4, 400}, {C4, 800},
        };
        playNotes(notes);
        System.out.println("Done.");
    }

    static void playImperial() throws Exception {
        System.out.println("=== Imperial March ===");
        System.out.println("Listen outside the car!\n");

        int Eb4 = 311, Bb3 = 233, Ab4 = 415, Gb4 = 370, Bb4 = 466;
        int Eb5 = 622, Ab3 = 208;

        int[][] notes = {
            // Main theme
            {G4, 500}, {G4, 500}, {G4, 500},
            {Eb4, 350}, {Bb4, 150},
            {G4, 500}, {Eb4, 350}, {Bb4, 150},
            {G4, 1000},
            {REST, 200},
            // Second phrase
            {D5, 500}, {D5, 500}, {D5, 500},
            {Eb5, 350}, {Bb4, 150},
            {Gb4, 500}, {Eb4, 350}, {Bb4, 150},
            {G4, 1000},
            {REST, 200},
            // Third phrase
            {G5, 500}, {G4, 350}, {G4, 150},
            {G5, 500}, {F5, 250}, {E5, 250},
            {E5, 125}, {D5, 125}, {REST, 150},
            {Ab4, 250}, {REST, 50},
            {C5, 500}, {B4, 250}, {B4, 250},
            {A4, 125}, {A4, 125}, {REST, 150},
        };
        playNotes(notes);
        System.out.println("Done.");
    }

    static void playSiren() throws Exception {
        System.out.println("=== Siren Sweep ===");
        System.out.println("Sweeping 300-2000 Hz, 3 cycles\n");

        for (int cycle = 0; cycle < 3; cycle++) {
            // Sweep up
            for (int f = 300; f <= 2000; f += 20) {
                setFreq(f);
                Thread.sleep(10);
            }
            // Sweep down
            for (int f = 2000; f >= 300; f -= 20) {
                setFreq(f);
                Thread.sleep(10);
            }
        }
        setFreq(0);
        System.out.println("Done.");
    }

    // --- PCM Streaming ---

    static void pcmStream() throws Exception {
        System.out.println("=== PCM Chunk Streaming Test ===\n");
        System.out.println("Sending 128-byte PCM chunks rapidly to AVAH.");
        System.out.println("If MCU plays each chunk, you'll hear a 440Hz tone.\n");

        int sampleRate = 8000;
        int samplesPerChunk = 64; // 64 * 2 bytes = 128 bytes
        int totalChunks = 100;    // ~800ms of audio
        int sampleOffset = 0;

        // First enable the AVAH tone (may prime the audio path)
        setInt.invoke(mgr, DEV, AVAH, 1);
        Thread.sleep(500);

        System.out.println("Streaming " + totalChunks + " chunks...");
        long start = System.currentTimeMillis();

        for (int chunk = 0; chunk < totalChunks; chunk++) {
            byte[] pcm = new byte[128];
            for (int i = 0; i < samplesPerChunk; i++) {
                int s = sampleOffset + i;
                short val = (short)(Short.MAX_VALUE * 0.8 *
                    Math.sin(2.0 * Math.PI * 440.0 * s / sampleRate));
                pcm[i * 2] = (byte)(val & 0xFF);
                pcm[i * 2 + 1] = (byte)((val >> 8) & 0xFF);
            }
            setBuffer.invoke(mgr, DEV, AVAH, pcm);
            sampleOffset += samplesPerChunk;

            // Pace to roughly real-time
            long elapsed = System.currentTimeMillis() - start;
            long expected = (long)sampleOffset * 1000 / sampleRate;
            long sleep = expected - elapsed;
            if (sleep > 0) Thread.sleep(sleep);
        }

        long totalMs = System.currentTimeMillis() - start;
        System.out.println("Sent " + totalChunks + " chunks in " + totalMs + "ms");
        System.out.println("Audio duration: " + (sampleOffset * 1000 / sampleRate) + "ms");

        Thread.sleep(500);
        setInt.invoke(mgr, DEV, AVAH, 0);
        System.out.println("Done.");
    }

    static void freqTest() throws Exception {
        System.out.println("=== Frequency Range Test ===");
        System.out.println("Sweeping to find exact min/max frequency the speaker produces.\n");

        System.out.println("--- Low frequencies (50-500 Hz, step 50) ---");
        for (int f = 50; f <= 500; f += 50) {
            setFreq(f);
            Thread.sleep(800);
            System.out.println("  " + f + " Hz");
        }

        System.out.println("\n--- High frequencies (1000-8000 Hz, step 500) ---");
        for (int f = 1000; f <= 8000; f += 500) {
            setFreq(f);
            Thread.sleep(800);
            System.out.println("  " + f + " Hz");
        }

        setFreq(0);
        System.out.println("\nDone. Note which frequencies were audible.");
    }
}
