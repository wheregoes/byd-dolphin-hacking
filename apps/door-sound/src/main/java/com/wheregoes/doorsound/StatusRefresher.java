package com.wheregoes.doorsound;

class StatusRefresher implements Runnable {
    private final MainActivity activity;

    StatusRefresher(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void run() {
        activity.updateStatus();
        activity.getWindow().getDecorView().postDelayed(this, 2000);
    }
}
