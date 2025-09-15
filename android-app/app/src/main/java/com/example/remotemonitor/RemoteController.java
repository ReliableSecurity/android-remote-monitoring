package com.example.remotemonitor;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Удаленное управление Android устройством
 * Поддержка Android 5.0-14 с согласием пользователя
 */
public class RemoteController {
    
    private static final String TAG = "RemoteController";
    private Context context;
    private ExecutorService executorService;
    private LocationManager locationManager;
    private LocationListener locationListener;
    
    // Callbacks
    public interface RemoteControlCallback {
        void onLocationUpdate(JSONObject location);
        void onCommandExecuted(String command, boolean success, String message);
        void onDeviceStateChanged(String state, boolean success);
        void onError(String error);
    }
    
    private RemoteControlCallback callback;
    
    public RemoteController(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    public void setCallback(RemoteControlCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Проверка разрешений на местоположение
     */
    public boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Проверка административных прав
     */
    public boolean hasDeviceAdminRights() {
        DevicePolicyManager devicePolicyManager = 
            (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        
        if (devicePolicyManager != null) {
            ComponentName adminComponent = new ComponentName(context, DeviceAdminReceiver.class);
            return devicePolicyManager.isAdminActive(adminComponent);
        }
        
        return false;
    }
    
    /**
     * Получение текущего местоположения
     */
    public void getCurrentLocation() {
        if (!hasLocationPermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на доступ к местоположению");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                if (locationManager == null) {
                    if (callback != null) {
                        callback.onError("LocationManager недоступен");
                    }
                    return;
                }
                
                // Проверка доступности провайдеров
                boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                
                if (!gpsEnabled && !networkEnabled) {
                    if (callback != null) {
                        callback.onError("Службы геолокации отключены");
                    }
                    return;
                }
                
                // Получение последнего известного местоположения
                Location lastLocation = null;
                
                if (gpsEnabled) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                
                if (lastLocation == null && networkEnabled) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastLocation != null) {
                    JSONObject locationData = createLocationObject(lastLocation);
                    if (callback != null) {
                        callback.onLocationUpdate(locationData);
                    }
                } else {
                    // Запрос обновления местоположения
                    requestLocationUpdates();
                }
                
            } catch (SecurityException e) {
                Log.e(TAG, "Ошибка доступа к местоположению: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Нет разрешения на доступ к местоположению");
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения местоположения: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Запрос обновлений местоположения
     */
    private void requestLocationUpdates() {
        try {
            if (locationListener == null) {
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        JSONObject locationData = createLocationObject(location);
                        if (callback != null) {
                            callback.onLocationUpdate(locationData);
                        }
                        // Останавливаем обновления после первого получения
                        stopLocationUpdates();
                    }
                    
                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}
                    
                    @Override
                    public void onProviderEnabled(String provider) {}
                    
                    @Override
                    public void onProviderDisabled(String provider) {}
                };
            }
            
            // Запрос обновлений от лучшего доступного провайдера
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    1000, // 1 секунда
                    0, // 0 метров
                    locationListener
                );
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 
                    1000, 
                    0, 
                    locationListener
                );
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка запроса обновлений местоположения: " + e.getMessage());
        }
    }
    
    /**
     * Остановка обновлений местоположения
     */
    public void stopLocationUpdates() {
        try {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка остановки обновлений местоположения: " + e.getMessage());
        }
    }
    
    /**
     * Блокировка экрана (требуются административные права)
     */
    public void lockScreen() {
        executorService.execute(() -> {
            try {
                if (!hasDeviceAdminRights()) {
                    if (callback != null) {
                        callback.onError("Нет административных прав для блокировки экрана");
                    }
                    return;
                }
                
                DevicePolicyManager devicePolicyManager = 
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                
                if (devicePolicyManager != null) {
                    devicePolicyManager.lockNow();
                    
                    if (callback != null) {
                        callback.onCommandExecuted("lock_screen", true, "Экран заблокирован");
                    }
                    
                    Log.i(TAG, "Экран заблокирован удаленно");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка блокировки экрана: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("lock_screen", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Перезагрузка устройства (требуются root права)
     */
    public void rebootDevice() {
        executorService.execute(() -> {
            try {
                // Предупреждение пользователя
                if (callback != null) {
                    callback.onDeviceStateChanged("preparing_reboot", true);
                }
                
                // Попытка перезагрузки через системные команды
                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
                
                if (callback != null) {
                    callback.onCommandExecuted("reboot", true, "Команда перезагрузки отправлена");
                }
                
                Log.i(TAG, "Команда перезагрузки выполнена");
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка перезагрузки: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("reboot", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Управление WiFi
     */
    public void toggleWifi(boolean enable) {
        executorService.execute(() -> {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
                
                if (wifiManager == null) {
                    if (callback != null) {
                        callback.onError("WiFi Manager недоступен");
                    }
                    return;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ - открытие настроек WiFi
                    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    
                    if (callback != null) {
                        callback.onCommandExecuted("wifi_toggle", true, 
                            "Открыты настройки WiFi (Android 10+)");
                    }
                } else {
                    // Старые версии Android
                    boolean success = wifiManager.setWifiEnabled(enable);
                    
                    if (callback != null) {
                        callback.onCommandExecuted("wifi_toggle", success, 
                            enable ? "WiFi включен" : "WiFi отключен");
                    }
                }
                
                Log.i(TAG, "WiFi " + (enable ? "включен" : "отключен"));
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка управления WiFi: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("wifi_toggle", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Управление звуком
     */
    public void setVolumeLevel(int streamType, int volume) {
        executorService.execute(() -> {
            try {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                
                if (audioManager == null) {
                    if (callback != null) {
                        callback.onError("Audio Manager недоступен");
                    }
                    return;
                }
                
                int maxVolume = audioManager.getStreamMaxVolume(streamType);
                int targetVolume = Math.min(volume, maxVolume);
                
                audioManager.setStreamVolume(streamType, targetVolume, 0);
                
                String streamName = getStreamTypeName(streamType);
                if (callback != null) {
                    callback.onCommandExecuted("set_volume", true, 
                        String.format("Громкость %s установлена: %d/%d", 
                        streamName, targetVolume, maxVolume));
                }
                
                Log.i(TAG, String.format("Громкость %s: %d/%d", streamName, targetVolume, maxVolume));
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка установки громкости: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("set_volume", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Включение беззвучного режима
     */
    public void setSilentMode(boolean enable) {
        executorService.execute(() -> {
            try {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                
                if (audioManager == null) {
                    if (callback != null) {
                        callback.onError("Audio Manager недоступен");
                    }
                    return;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6.0+ - проверка доступа к настройкам "Не беспокоить"
                    if (!Settings.System.canWrite(context)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        
                        if (callback != null) {
                            callback.onCommandExecuted("silent_mode", false, 
                                "Требуется разрешение на изменение системных настроек");
                        }
                        return;
                    }
                }
                
                int mode = enable ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL;
                audioManager.setRingerMode(mode);
                
                if (callback != null) {
                    callback.onCommandExecuted("silent_mode", true, 
                        enable ? "Беззвучный режим включен" : "Беззвучный режим отключен");
                }
                
                Log.i(TAG, "Беззвучный режим " + (enable ? "включен" : "отключен"));
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка установки беззвучного режима: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("silent_mode", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Вибрация устройства
     */
    public void vibrateDevice(long duration) {
        executorService.execute(() -> {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                
                if (vibrator == null || !vibrator.hasVibrator()) {
                    if (callback != null) {
                        callback.onError("Вибрация недоступна на этом устройстве");
                    }
                    return;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0+
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    // Старые версии
                    vibrator.vibrate(duration);
                }
                
                if (callback != null) {
                    callback.onCommandExecuted("vibrate", true, 
                        "Вибрация " + duration + " мс");
                }
                
                Log.i(TAG, "Вибрация " + duration + " мс");
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка вибрации: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("vibrate", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Пробуждение устройства
     */
    public void wakeUpDevice() {
        executorService.execute(() -> {
            try {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                
                if (powerManager == null) {
                    if (callback != null) {
                        callback.onError("Power Manager недоступен");
                    }
                    return;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    if (powerManager.isInteractive()) {
                        if (callback != null) {
                            callback.onCommandExecuted("wake_up", true, "Устройство уже активно");
                        }
                        return;
                    }
                }
                
                // Попытка пробуждения через WakeLock
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "RemoteMonitor:WakeUp");
                
                wakeLock.acquire(5000); // 5 секунд
                
                // Освобождение через короткое время
                executorService.schedule(() -> {
                    if (wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                }, 3, java.util.concurrent.TimeUnit.SECONDS);
                
                if (callback != null) {
                    callback.onCommandExecuted("wake_up", true, "Устройство пробуждено");
                }
                
                Log.i(TAG, "Устройство пробуждено");
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка пробуждения устройства: " + e.getMessage());
                if (callback != null) {
                    callback.onCommandExecuted("wake_up", false, "Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private JSONObject createLocationObject(Location location) {
        JSONObject locationData = new JSONObject();
        
        try {
            locationData.put("latitude", location.getLatitude());
            locationData.put("longitude", location.getLongitude());
            locationData.put("accuracy", location.getAccuracy());
            locationData.put("altitude", location.getAltitude());
            locationData.put("bearing", location.getBearing());
            locationData.put("speed", location.getSpeed());
            locationData.put("provider", location.getProvider());
            locationData.put("time", location.getTime());
            
            // Дополнительная информация для новых версий Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                locationData.put("elapsed_realtime", location.getElapsedRealtimeNanos());
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                locationData.put("vertical_accuracy", location.getVerticalAccuracyMeters());
                locationData.put("speed_accuracy", location.getSpeedAccuracyMetersPerSecond());
                locationData.put("bearing_accuracy", location.getBearingAccuracyDegrees());
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания объекта местоположения: " + e.getMessage());
        }
        
        return locationData;
    }
    
    private String getStreamTypeName(int streamType) {
        switch (streamType) {
            case AudioManager.STREAM_VOICE_CALL: return "Звонки";
            case AudioManager.STREAM_SYSTEM: return "Система";
            case AudioManager.STREAM_RING: return "Звонок";
            case AudioManager.STREAM_MUSIC: return "Медиа";
            case AudioManager.STREAM_ALARM: return "Будильник";
            case AudioManager.STREAM_NOTIFICATION: return "Уведомления";
            case AudioManager.STREAM_DTMF: return "DTMF";
            default: return "Неизвестный";
        }
    }
    
    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        stopLocationUpdates();
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        Log.i(TAG, "RemoteController очищен");
    }
}