package com.example.remotemonitor;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Device Admin Receiver для получения административных прав
 * Используется для удаленного управления устройством
 */
public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    
    private static final String TAG = "DeviceAdminReceiver";
    
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Административные права активированы");
    }
    
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "Административные права деактивированы");
    }
    
    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
        Log.i(TAG, "Пароль устройства изменен");
    }
    
    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.i(TAG, "Неверный пароль устройства");
    }
    
    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.i(TAG, "Пароль устройства введен верно");
    }
}