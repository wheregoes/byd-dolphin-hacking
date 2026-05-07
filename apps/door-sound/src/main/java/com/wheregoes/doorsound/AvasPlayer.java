package com.wheregoes.doorsound;

import android.content.Context;
import java.lang.reflect.Method;

public class AvasPlayer {
    public static final int PATTERN_NONE = -1;
    public static final int PATTERN_DING_DONG = 0;
    public static final int PATTERN_DONG_DING = 1;
    public static final int PATTERN_TRIPLE_BEEP = 2;
    public static final int PATTERN_RAPID_ALT = 3;
    public static final int PATTERN_LONG_CHIME = 4;

    static final int D = 1002;
    static final int AVAH = 0x6E970010;

    private Object mgr;
    private Method setIntMethod;
    private volatile boolean playing;
    private Thread playThread;

    public AvasPlayer(Context context) {
        try {
            mgr = context.getSystemService("auto");
            if (mgr != null) {
                setIntMethod = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            }
        } catch (Exception ignored) {}
    }

    public boolean isAvailable() {
        return mgr != null && setIntMethod != null;
    }

    public void play(int pattern) {
        stop();
        if (pattern < 0 || !isAvailable()) return;
        playing = true;
        playThread = new Thread(new PatternRunner(this, pattern));
        playThread.start();
    }

    public void stop() {
        playing = false;
        if (playThread != null) {
            playThread.interrupt();
            try { playThread.join(500); } catch (InterruptedException ignored) {}
            playThread = null;
        }
        if (isAvailable()) fullStop();
    }

    public boolean isPlaying() {
        return playing;
    }

    void setPlayingDone() {
        playing = false;
        playThread = null;
    }

    void enable() {
        si(0xAA000148, 1);
        si(0xAA000142, 1);
        si(0xAA00011A, 1);
        si(0xAA000104, 1);
        si(0xAA000171, 1);
        si(0xAA00011E, 0);
    }

    void fullStop() {
        si(0xAA000148, 0);
        si(0xAA000142, 0);
        si(0xAA00011A, 0);
        si(0xAA000104, 0);
        si(0xAA000171, 0);
        si(AVAH, 0);
    }

    void si(int fid, int val) {
        try {
            if (setIntMethod != null) setIntMethod.invoke(mgr, D, fid, val);
        } catch (Exception ignored) {}
    }
}
