package com.wheregoes.doorsound;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

public class DoorSoundService extends Service {
    private static final String TAG = "DoorSoundService";
    private static final String CHANNEL_ID = "door_sound_service";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESTARTER_JOB_ID = 1001;
    static final String PREF_NAME = "door_sound_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_DOOR_OPEN_PATH = "door_open_path";
    static final String KEY_LOCK_PATH = "lock_path";
    static final String KEY_UNLOCK_PATH = "unlock_path";
    static final String KEY_LAST_EVENT = "last_event";

    private static volatile boolean sRunning = false;

    private PowerManager.WakeLock wakeLock;
    private BYDAutoBodyworkDevice bodyworkDevice;
    private BodyworkHandler bodyworkListener;
    private MediaPlayer activePlayer;
    private Handler mainHandler;
    private int lastSystemState = -1;

    public static boolean isRunning() {
        return sRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        registerBodyworkListener();
        scheduleRestarter();
        sRunning = true;
        Log.i(TAG, "Service created");
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
        Log.i(TAG, "Service destroyed");
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
            bodyworkDevice = BYDAutoBodyworkDevice.getInstance(this);
            bodyworkListener = new BodyworkHandler(this);
            bodyworkDevice.registerListener(bodyworkListener);
            Log.i(TAG, "Bodywork listener registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register bodywork listener", e);
        }
    }

    private void unregisterBodyworkListener() {
        if (bodyworkDevice != null && bodyworkListener != null) {
            try {
                bodyworkDevice.unregisterListener(bodyworkListener);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister listener", e);
            }
        }
    }

    void handleDoorOpen(int area) {
        setLastEvent(getDoorName(area) + " opened");
        playSound(KEY_DOOR_OPEN_PATH);
    }

    void handleLock() {
        setLastEvent("Car locked");
        playSound(KEY_LOCK_PATH);
    }

    void handleUnlock() {
        setLastEvent("Car unlocked");
        playSound(KEY_UNLOCK_PATH);
    }

    int getLastSystemState() {
        return lastSystemState;
    }

    void setLastSystemState(int state) {
        lastSystemState = state;
    }

    private void playSound(String prefKey) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;

        String path = prefs.getString(prefKey, null);
        if (path == null || path.isEmpty()) return;

        mainHandler.post(new SoundPlayer(this, path));
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

    Handler getMainHandler() {
        return mainHandler;
    }

    private void setLastEvent(String event) {
        String msg = event + " @ " + android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis());
        Log.i(TAG, msg);
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
            case 7: return "Fuel cap";
            default: return "Door " + area;
        }
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restarter", e);
        }
    }
}
