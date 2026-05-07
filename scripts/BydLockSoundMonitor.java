import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.io.File;

/**
 * Monitors car lock/unlock events and plays a custom sound through cabin speakers.
 * Uses app_process to run as system UID, bypassing BYDAUTO permission checks.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydlock scripts/BydLockSoundMonitor.java
 *   d8 --output /tmp/bydlock /tmp/bydlock/BydLockSoundMonitor.class
 *   adb push /tmp/bydlock/classes.dex /data/local/tmp/bydlock.dex
 *   adb push my_lock_sound.ogg /data/local/tmp/lock_sound.ogg
 *
 *   # Run (stays alive, monitoring lock events):
 *   adb shell "CLASSPATH=/data/local/tmp/bydlock.dex app_process /data/local/tmp BydLockSoundMonitor /data/local/tmp/lock_sound.ogg"
 *
 *   # Run in background (survives ADB disconnect):
 *   adb shell "nohup sh -c 'CLASSPATH=/data/local/tmp/bydlock.dex app_process /data/local/tmp BydLockSoundMonitor /data/local/tmp/lock_sound.ogg' > /data/local/tmp/lockmon.log 2>&1 &"
 */
public class BydLockSoundMonitor {
    static volatile boolean running = true;

    public static void main(String[] args) {
        String soundFile = args.length > 0 ? args[0] : "/data/local/tmp/lock_sound.ogg";

        if (!new File(soundFile).exists()) {
            System.out.println("ERROR: Sound file not found: " + soundFile);
            System.out.println("Push a sound file first: adb push my_sound.ogg /data/local/tmp/lock_sound.ogg");
            return;
        }

        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);
            Object mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
            if (mgr == null) { System.out.println("ERROR: null BYDAutoManager"); return; }

            Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);

            System.out.println("BydLockSoundMonitor started");
            System.out.println("Sound file: " + soundFile);
            System.out.println("Polling BODYWORK_AUTO_SYSTEM_STATE every 500ms...");
            System.out.println("Press Ctrl+C to stop");

            int lastState = -1;
            int pollCount = 0;

            while (running) {
                try {
                    // BODYWORK_AUTO_SYSTEM_STATE: 0=normal, 1=arming, 2=armed/locked
                    // Device type 1001 = bodywork
                    // Feature ID 0x18000009 = BODYWORK_REMOTE_CONTROL_LOCK (CAN signal)
                    // But we'll try reading the auto system state instead
                    // Using the bodywork device (1001) with various lock-related feature IDs

                    // Try reading lock state via bodywork device
                    int lockState = safeGetInt(getInt, mgr, 1001, 0x18000009);
                    int systemState = safeGetInt(getInt, mgr, 1001, 0x38400032);

                    if (pollCount % 20 == 0) {
                        System.out.println("[heartbeat] lock=" + lockState + " system=" + systemState);
                    }

                    if (lockState != lastState && lastState != -1 && lockState > 0) {
                        System.out.println("*** LOCK EVENT DETECTED! state=" + lockState + " ***");
                        playSound(soundFile);
                    }

                    lastState = lockState;
                    pollCount++;
                    Thread.sleep(500);

                } catch (InterruptedException e) {
                    running = false;
                } catch (Exception e) {
                    System.out.println("Poll error: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static int safeGetInt(Method getInt, Object mgr, int dev, int fid) {
        try {
            return (int) getInt.invoke(mgr, dev, fid);
        } catch (Exception e) {
            return -99999;
        }
    }

    static void playSound(String path) {
        try {
            Class<?> mpClass = Class.forName("android.media.MediaPlayer");
            Object mp = mpClass.newInstance();
            mpClass.getMethod("setDataSource", String.class).invoke(mp, path);
            mpClass.getMethod("prepare").invoke(mp);
            mpClass.getMethod("start").invoke(mp);

            // Set completion listener to release
            Class<?> listenerClass = Class.forName("android.media.MediaPlayer$OnCompletionListener");
            // Simple approach: just let it play and release after a delay
            new Thread(() -> {
                try {
                    Thread.sleep(10000); // max 10 seconds
                    try {
                        mpClass.getMethod("release").invoke(mp);
                    } catch (Exception ignored) {}
                } catch (InterruptedException ignored) {}
            }).start();

            System.out.println("Playing sound: " + path);
        } catch (Exception e) {
            System.out.println("Sound playback error: " + e);
        }
    }
}
