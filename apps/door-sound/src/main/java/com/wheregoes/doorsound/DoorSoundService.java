package com.wheregoes.doorsound;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DoorSoundService extends Service {
    private static final String TAG = "DoorSoundService";
    private static final String CHANNEL_ID = "door_sound_service";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESTARTER_JOB_ID = 1001;
    static final String PREF_NAME = "door_sound_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_DOOR_OPEN_PATH = "door_open_path";
    static final String KEY_DOOR_CLOSE_PATH = "door_close_path";
    static final String KEY_DOOR_OPEN_ENABLED = "door_open_enabled";
    static final String KEY_DOOR_CLOSE_ENABLED = "door_close_enabled";
    static final String KEY_DOOR_OPEN_VOLUME = "door_open_volume";
    static final String KEY_DOOR_CLOSE_VOLUME = "door_close_volume";
    static final int DEFAULT_VOLUME = 10;
    static final String KEY_LAST_EVENT = "last_event";

    private static volatile boolean sRunning = false;

    private PowerManager.WakeLock wakeLock;
    private BYDAutoBodyworkDevice bodyworkDevice;
    private BodyworkHandler bodyworkListener;
    private MediaPlayer activePlayer;
    private Handler mainHandler;
    private int savedVolume = -1;

    public static boolean isRunning() {
        return sRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logToFile("=== Service onCreate START ===");
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        registerBodyworkListener();
        scheduleRestarter();
        sRunning = true;
        logToFile("=== Service onCreate COMPLETE ===");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        unregisterBodyworkListener();
        releasePlayer();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Door Sound Service", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Door Sound")
                .setContentText("Listening for door events...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "doorsound:service");
        wakeLock.acquire();
    }

    private void registerBodyworkListener() {
        try {
            logToFile("Getting BYDAutoBodyworkDevice instance...");
            bodyworkDevice = BYDAutoBodyworkDevice.getInstance(new BydPermissionContext(this));
            logToFile("Got instance, power=" + bodyworkDevice.getPowerLevel());
            bodyworkListener = new BodyworkHandler(this);
            bodyworkDevice.registerListener(bodyworkListener);
            logToFile("Listener registered OK");
        } catch (Throwable e) {
            logToFile("FAILED to register: " + e.getClass().getName() + ": " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logToFile(sw.toString());
        }
    }

    private void unregisterBodyworkListener() {
        if (bodyworkDevice != null && bodyworkListener != null) {
            try {
                bodyworkDevice.unregisterListener(bodyworkListener);
            } catch (Exception ignored) {}
        }
    }

    void handleDoorOpen(int area) {
        logToFile("EVENT: Door OPEN area=" + area + " (" + getDoorName(area) + ")");
        setLastEvent(getDoorName(area) + " opened");
        playSoundIfEnabled(KEY_DOOR_OPEN_ENABLED, KEY_DOOR_OPEN_PATH, KEY_DOOR_OPEN_VOLUME);
    }

    void handleDoorClose(int area) {
        logToFile("EVENT: Door CLOSE area=" + area + " (" + getDoorName(area) + ")");
        setLastEvent(getDoorName(area) + " closed");
        playSoundIfEnabled(KEY_DOOR_CLOSE_ENABLED, KEY_DOOR_CLOSE_PATH, KEY_DOOR_CLOSE_VOLUME);
    }

    private void playSoundIfEnabled(String enableKey, String pathKey, String volumeKey) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;
        if (!prefs.getBoolean(enableKey, true)) return;

        String path = prefs.getString(pathKey, null);
        if (path == null || path.isEmpty()) return;

        int volume = prefs.getInt(volumeKey, DEFAULT_VOLUME);
        logToFile("Playing sound: volume=" + volume + " path=" + new File(path).getName());
        mainHandler.post(new SoundPlayer(this, path, volume));
    }

    void releasePlayer() {
        if (activePlayer != null) {
            try {
                activePlayer.release();
            } catch (Exception ignored) {}
            activePlayer = null;
        }
    }

    void setActivePlayer(MediaPlayer mp) {
        activePlayer = mp;
    }

    MediaPlayer getActivePlayer() {
        return activePlayer;
    }

    void saveAndSetVolume(int eventVolume) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (savedVolume == -1) {
                savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, eventVolume, 0);
        } catch (Exception ignored) {}
    }

    void restoreVolume() {
        if (savedVolume != -1) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
            } catch (Exception ignored) {}
            savedVolume = -1;
        }
    }

    private void setLastEvent(String event) {
        String msg = event + " @ " + android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis());
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putString(KEY_LAST_EVENT, msg).apply();
    }

    private String getDoorName(int area) {
        switch (area) {
            case 1: return "Left front door";
            case 2: return "Right front door";
            case 3: return "Left rear door";
            case 4: return "Right rear door";
            case 5: return "Hood";
            case 6: return "Trunk";
            default: return "Door " + area;
        }
    }

    void logToFile(String msg) {
        try {
            File logDir = new File("/sdcard/Download");
            File logFile = new File(logDir, "doorsound_debug.log");
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(ts + " " + msg + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }

    private void scheduleRestarter() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo job = new JobInfo.Builder(RESTARTER_JOB_ID,
                    new ComponentName(this, RestarterJobService.class))
                    .setOverrideDeadline(60000)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {}
    }
}
