package com.wheregoes.doorsound;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class RestarterJobService extends JobService {
    private static final String TAG = "DoorSoundRestarter";
    private static final int JOB_ID = 1001;

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = getSharedPreferences(
                DoorSoundService.PREF_NAME, MODE_PRIVATE);

        if (prefs.getBoolean(DoorSoundService.KEY_ENABLED, false)
                && !DoorSoundService.isRunning()) {
            Log.i(TAG, "Restarting door sound service");
            startForegroundService(new Intent(this, DoorSoundService.class));
        }

        reschedule();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void reschedule() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(this, RestarterJobService.class))
                    .setOverrideDeadline(60000)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (Exception e) {
            Log.e(TAG, "Failed to reschedule", e);
        }
    }
}
