package com.wheregoes.doorsound;

class PatternRunner implements Runnable {
    private final AvasPlayer player;
    private final int pattern;

    PatternRunner(AvasPlayer player, int pattern) {
        this.player = player;
        this.pattern = pattern;
    }

    @Override
    public void run() {
        try {
            switch (pattern) {
                case AvasPlayer.PATTERN_DING_DONG:
                    playDingDong();
                    break;
                case AvasPlayer.PATTERN_DONG_DING:
                    playDongDing();
                    break;
                case AvasPlayer.PATTERN_TRIPLE_BEEP:
                    playTripleBeep();
                    break;
                case AvasPlayer.PATTERN_RAPID_ALT:
                    playRapidAlt();
                    break;
                case AvasPlayer.PATTERN_LONG_CHIME:
                    playLongChime();
                    break;
            }
        } catch (InterruptedException ignored) {
        } finally {
            player.fullStop();
            player.setPlayingDone();
        }
    }

    private void playDingDong() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(0xAA000104, 2);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(250);
        player.si(0xAA000104, 1);
        Thread.sleep(400);
    }

    private void playDongDing() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(0xAA000104, 1);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(250);
        player.si(0xAA000104, 2);
        Thread.sleep(400);
    }

    private void playTripleBeep() throws InterruptedException {
        for (int i = 0; i < 3 && player.isPlaying(); i++) {
            player.enable();
            Thread.sleep(50);
            player.si(0xAA000104, 2);
            player.si(AvasPlayer.AVAH, 1);
            Thread.sleep(150);
            player.fullStop();
            Thread.sleep(150);
        }
    }

    private void playRapidAlt() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.AVAH, 1);
        for (int i = 0; i < 6 && player.isPlaying(); i++) {
            player.si(0xAA000104, (i % 2) + 1);
            Thread.sleep(200);
        }
    }

    private void playLongChime() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(0xAA000104, 2);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(500);
        player.si(0xAA000104, 1);
        Thread.sleep(800);
    }
}
