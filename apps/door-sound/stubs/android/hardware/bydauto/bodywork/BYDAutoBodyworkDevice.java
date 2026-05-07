package android.hardware.bydauto.bodywork;

import android.content.Context;
import android.hardware.bydauto.AbsBYDAutoDevice;

public class BYDAutoBodyworkDevice extends AbsBYDAutoDevice {
    public static final int BODYWORK_STATE_CLOSED = 0;
    public static final int BODYWORK_STATE_OPEN = 1;
    public static final int BODYWORK_STATE_UNDEFINED = 255;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_NORMAL = 0;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE = 1;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_START_SECURE = 2;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_UNDEFINED = 255;
    public static final int BODYWORK_POWER_LEVEL_OFF = 0;
    public static final int BODYWORK_POWER_LEVEL_ACC = 1;
    public static final int BODYWORK_POWER_LEVEL_ON = 2;
    public static final int BODYWORK_POWER_LEVEL_OK = 3;
    public static final int BODYWORK_POWER_LEVEL_FAKE_OK = 4;
    public static final int BODYWORK_CMD_DOOR_LEFT_FRONT = 1;
    public static final int BODYWORK_CMD_DOOR_RIGHT_FRONT = 2;
    public static final int BODYWORK_CMD_DOOR_LEFT_REAR = 3;
    public static final int BODYWORK_CMD_DOOR_RIGHT_REAR = 4;
    public static final int BODYWORK_CMD_DOOR_HOOD = 5;
    public static final int BODYWORK_CMD_DOOR_LUGGAGE_DOOR = 6;
    public static final int BODYWORK_CMD_DOOR_FUEL_TANK_CAP = 7;

    private static BYDAutoBodyworkDevice mInstance;

    private BYDAutoBodyworkDevice(Context context) { super(context); }

    public static synchronized BYDAutoBodyworkDevice getInstance(Context context) {
        synchronized (BYDAutoBodyworkDevice.class) {
            if (mInstance == null) mInstance = new BYDAutoBodyworkDevice(context);
        }
        return mInstance;
    }

    public void registerListener(AbsBYDAutoBodyworkListener listener) {}
    public void unregisterListener(AbsBYDAutoBodyworkListener listener) {}
    public int getAutoSystemState() { return 0; }
    public int getPowerLevel() { return 0; }
    public String getAutoVIN() { return ""; }
}
