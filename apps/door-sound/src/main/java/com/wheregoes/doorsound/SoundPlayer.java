package com.wheregoes.doorsound;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;

class SoundPlayer implements Runnable {
    private static final String TAG = "DoorSoundPlayer";
    private final DoorSoundService service;
    private final String path;

    SoundPlayer(DoorSoundService service, String path) {
        this.service = service;
        this.path = path;
    }

    @Override
    public void run() {
        try {
            service.releasePlayer();
            File file = new File(path);
            if (!file.exists()) {
                Log.w(TAG, "Sound file missing: " + path);
                return;
            }
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(path);
            mp.setOnCompletionListener(p -> {
                p.release();
                if (service.getActivePlayer() == p) service.setActivePlayer(null);
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + "/" + extra);
                p.release();
                if (service.getActivePlayer() == p) service.setActivePlayer(null);
                return true;
            });
            mp.prepare();
            mp.start();
            service.setActivePlayer(mp);
            Log.i(TAG, "Playing: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound", e);
        }
    }
}
