package com.wheregoes.doorsound;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;

class PatternSelectListener implements AdapterView.OnItemSelectedListener {
    private final SharedPreferences prefs;
    private final String patternKey;

    PatternSelectListener(SharedPreferences prefs, String patternKey) {
        this.prefs = prefs;
        this.patternKey = patternKey;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int pattern = position - 1;
        prefs.edit().putInt(patternKey, pattern).apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
