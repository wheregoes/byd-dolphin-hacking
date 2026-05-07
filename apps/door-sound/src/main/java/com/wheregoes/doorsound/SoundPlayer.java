package com.wheregoes.doorsound;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.File;

class SoundPlayer implements Runnable {
    private final DoorSoundService service;
    private final String path;
    private final int volume;

    SoundPlayer(DoorSoundService service, String path, int volume) {
        this.service = service;
        this.path = path;
        this.volume = volume;
    }

    @Override
    public void run() {
        try {
            service.logToFile("SoundPlayer: playing " + path + " at volume " + volume);
            service.releasePlayer();
            File file = new File(path);
            if (!file.exists()) {
                service.logToFile("SoundPlayer: file NOT FOUND: " + path);
                return;
            }
            service.saveAndSetVolume(volume);
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(path);
            mp.setOnCompletionListener(p -> {
                service.logToFile("SoundPlayer: playback completed");
                service.restoreVolume();
                p.release();
                if (service.getActivePlayer() == p) service.setActivePlayer(null);
            });
            mp.setOnErrorListener((p, what, extra) -> {
                service.logToFile("SoundPlayer: ERROR what=" + what + " extra=" + extra);
                service.restoreVolume();
                p.release();
                if (service.getActivePlayer() == p) service.setActivePlayer(null);
                return true;
            });
            mp.prepare();
            mp.start();
            service.setActivePlayer(mp);
            service.logToFile("SoundPlayer: started OK");
        } catch (Exception e) {
            service.logToFile("SoundPlayer: EXCEPTION " + e.getClass().getName() + ": " + e.getMessage());
            service.restoreVolume();
        }
    }
}
