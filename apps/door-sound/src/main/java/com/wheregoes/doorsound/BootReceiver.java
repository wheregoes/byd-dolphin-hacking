package com.wheregoes.doorsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i("DoorSoundBoot", "Boot received: " + action);

        SharedPreferences prefs = context.getSharedPreferences(
                DoorSoundService.PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(DoorSoundService.KEY_ENABLED, false)) {
            Intent svc = new Intent(context, DoorSoundService.class);
            context.startForegroundService(svc);
            Log.i("DoorSoundBoot", "Service started on boot");
        }
    }
}
