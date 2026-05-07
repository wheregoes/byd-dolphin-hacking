package com.wheregoes.doorsound;

import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.util.Log;

public class BodyworkHandler extends AbsBYDAutoBodyworkListener {
    private static final String TAG = "DoorSoundHandler";
    private final DoorSoundService service;

    BodyworkHandler(DoorSoundService service) {
        this.service = service;
    }

    @Override
    public void onDoorStateChanged(int area, int state) {
        if (area >= 1 && area <= 4) {
            if (state == BYDAutoBodyworkDevice.BODYWORK_STATE_OPEN) {
                service.handleDoorOpen(area);
            } else if (state == BYDAutoBodyworkDevice.BODYWORK_STATE_CLOSED) {
                service.handleDoorClose(area);
            }
        }
    }

    @Override
    public void onAutoSystemStateChanged(int state) {
        int prevState = service.getLastSystemState();
        if (state == prevState) return;
        service.setLastSystemState(state);

        if (state == BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE) {
            service.handleLock();
        } else if (state == BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_NORMAL
                && prevState == BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE) {
            service.handleUnlock();
        }
    }

    @Override
    public void onPowerLevelChanged(int level) {
        Log.d(TAG, "Power level: " + level);
    }
}
