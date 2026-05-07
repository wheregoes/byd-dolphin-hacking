package com.wheregoes.doorsound;

import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;

public class BodyworkHandler extends AbsBYDAutoBodyworkListener {
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
    public void onAutoSystemStateChanged(int state) {}

    @Override
    public void onPowerLevelChanged(int level) {}
}
