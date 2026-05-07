package com.wheregoes.doorsound;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

class BydPermissionContext extends ContextWrapper {

    BydPermissionContext(Context base) {
        super(base);
    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {
        if (permission != null && permission.startsWith("android.permission.BYDAUTO_")) {
            return;
        }
        super.enforceCallingOrSelfPermission(permission, message);
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        if (permission != null && permission.startsWith("android.permission.BYDAUTO_")) {
            return PackageManager.PERMISSION_GRANTED;
        }
        return super.checkCallingOrSelfPermission(permission);
    }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {
        if (permission != null && permission.startsWith("android.permission.BYDAUTO_")) {
            return;
        }
        super.enforcePermission(permission, pid, uid, message);
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        if (permission != null && permission.startsWith("android.permission.BYDAUTO_")) {
            return PackageManager.PERMISSION_GRANTED;
        }
        return super.checkPermission(permission, pid, uid);
    }
}
