package com.wheregoes.doorsound;

import android.content.SharedPreferences;
import android.widget.SeekBar;
import android.widget.TextView;

class VolumeChangeListener implements SeekBar.OnSeekBarChangeListener {
    private final TextView label;
    private final String prefKey;
    private final SharedPreferences prefs;

    VolumeChangeListener(TextView label, String prefKey, SharedPreferences prefs) {
        this.label = label;
        this.prefKey = prefKey;
        this.prefs = prefs;
    }

    @Override
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        label.setText(String.valueOf(progress));
        if (fromUser) {
            prefs.edit().putInt(prefKey, progress).apply();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar sb) {}

    @Override
    public void onStopTrackingTouch(SeekBar sb) {}
}
