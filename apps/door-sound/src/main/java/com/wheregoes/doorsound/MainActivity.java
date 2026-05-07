package com.wheregoes.doorsound;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_DOOR_OPEN = 1;
    private static final int REQ_LOCK = 2;
    private static final int REQ_UNLOCK = 3;
    private static final int REQ_PERMISSION = 100;

    private Switch switchEnabled;
    private TextView textDoorOpenFile;
    private TextView textLockFile;
    private TextView textUnlockFile;
    private TextView textServiceStatus;
    private TextView textLastEvent;
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchEnabled = findViewById(R.id.switch_enabled);
        textDoorOpenFile = findViewById(R.id.text_door_open_file);
        textLockFile = findViewById(R.id.text_lock_file);
        textUnlockFile = findViewById(R.id.text_unlock_file);
        textServiceStatus = findViewById(R.id.text_service_status);
        textLastEvent = findViewById(R.id.text_last_event);

        SharedPreferences prefs = getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE);

        switchEnabled.setChecked(prefs.getBoolean(DoorSoundService.KEY_ENABLED, false));
        switchEnabled.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(DoorSoundService.KEY_ENABLED, checked).apply();
            if (checked) {
                startService(new Intent(this, DoorSoundService.class));
            } else {
                stopService(new Intent(this, DoorSoundService.class));
            }
            updateStatus();
        });

        setupSoundButton(R.id.btn_door_open_select, REQ_DOOR_OPEN);
        setupSoundButton(R.id.btn_lock_select, REQ_LOCK);
        setupSoundButton(R.id.btn_unlock_select, REQ_UNLOCK);

        setupClearButton(R.id.btn_door_open_clear, DoorSoundService.KEY_DOOR_OPEN_PATH, textDoorOpenFile);
        setupClearButton(R.id.btn_lock_clear, DoorSoundService.KEY_LOCK_PATH, textLockFile);
        setupClearButton(R.id.btn_unlock_clear, DoorSoundService.KEY_UNLOCK_PATH, textUnlockFile);

        requestStoragePermission();
        updateFileLabels();
        updateStatus();

        refreshHandler = new Handler();
        refreshRunnable = () -> {
            updateStatus();
            refreshHandler.postDelayed(refreshRunnable, 2000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFileLabels();
        updateStatus();
        refreshHandler.postDelayed(refreshRunnable, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void setupSoundButton(int buttonId, int requestCode) {
        findViewById(buttonId).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select audio file"), requestCode);
        });
    }

    private void setupClearButton(int buttonId, String prefKey, TextView label) {
        findViewById(buttonId).setOnClickListener(v -> {
            getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE)
                    .edit().remove(prefKey).apply();
            label.setText(R.string.no_file_selected);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        String prefKey;
        switch (requestCode) {
            case REQ_DOOR_OPEN: prefKey = DoorSoundService.KEY_DOOR_OPEN_PATH; break;
            case REQ_LOCK:      prefKey = DoorSoundService.KEY_LOCK_PATH; break;
            case REQ_UNLOCK:    prefKey = DoorSoundService.KEY_UNLOCK_PATH; break;
            default: return;
        }

        String localPath = copyToLocal(uri, prefKey);
        if (localPath != null) {
            getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE)
                    .edit().putString(prefKey, localPath).apply();
        }
        updateFileLabels();
    }

    private String copyToLocal(Uri uri, String key) {
        try {
            File dir = new File(getFilesDir(), "sounds");
            dir.mkdirs();
            String filename = getFileName(uri);
            if (filename == null) filename = key + ".audio";
            File dest = new File(dir, filename);

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            out.close();
            in.close();
            return dest.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
                c.close();
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    private void updateFileLabels() {
        SharedPreferences prefs = getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE);
        setFileLabel(textDoorOpenFile, prefs.getString(DoorSoundService.KEY_DOOR_OPEN_PATH, null));
        setFileLabel(textLockFile, prefs.getString(DoorSoundService.KEY_LOCK_PATH, null));
        setFileLabel(textUnlockFile, prefs.getString(DoorSoundService.KEY_UNLOCK_PATH, null));
    }

    private void setFileLabel(TextView tv, String path) {
        if (path == null || path.isEmpty()) {
            tv.setText(R.string.no_file_selected);
        } else {
            tv.setText(new File(path).getName());
        }
    }

    private void updateStatus() {
        boolean running = DoorSoundService.isRunning();
        textServiceStatus.setText(running ? "Running" : "Stopped");
        textServiceStatus.setTextColor(running ? 0xFF4CAF50 : 0xFFFF5252);

        SharedPreferences prefs = getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE);
        String lastEvent = prefs.getString(DoorSoundService.KEY_LAST_EVENT, null);
        textLastEvent.setText(lastEvent != null ? "Last: " + lastEvent : "No events yet");
    }

    private void requestStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMISSION);
        }
    }
}
