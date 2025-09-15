package com.example.remotemonitor;

import android.app.ActivityManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Мониторинг системы Android устройства
 * Поддержка Android 5.0-14 с детальной информацией о системе
 */
public class SystemMonitor {
    
    private static final String TAG = "SystemMonitor";
    private Context context;
    private ExecutorService executorService;
    
    // Callbacks
    public interface SystemCallback {
        void onSystemInfo(JSONObject systemInfo);
        void onMemoryInfo(JSONObject memoryInfo);
        void onStorageInfo(JSONObject storageInfo);
        void onBatteryInfo(JSONObject batteryInfo);
        void onNetworkInfo(JSONObject networkInfo);
        void onProcessList(JSONArray processes);
        void onInstalledApps(JSONArray apps);
        void onUsageStats(JSONArray usageStats);
        void onNetworkUsage(JSONObject networkUsage);
        void onError(String error);
    }
    
    private SystemCallback callback;
    
    public SystemMonitor(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public void setCallback(SystemCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Получение общей информации о системе
     */
    public void getSystemInfo() {
        executorService.execute(() -> {
            try {
                JSONObject info = new JSONObject();
                
                // Основная информация о системе
                info.put("device_model", Build.MODEL);
                info.put("device_brand", Build.BRAND);
                info.put("device_manufacturer", Build.MANUFACTURER);
                info.put("device_board", Build.BOARD);
                info.put("device_hardware", Build.HARDWARE);
                info.put("device_product", Build.PRODUCT);
                info.put("device_id", Build.ID);
                
                // Информация об Android
                info.put("android_version", Build.VERSION.RELEASE);
                info.put("android_sdk", Build.VERSION.SDK_INT);
                info.put("android_codename", Build.VERSION.CODENAME);
                info.put("android_incremental", Build.VERSION.INCREMENTAL);
                info.put("android_security_patch", Build.VERSION.SECURITY_PATCH);
                
                // Информация о сборке
                info.put("build_time", new Date(Build.TIME).toString());
                info.put("build_type", Build.TYPE);
                info.put("build_user", Build.USER);
                info.put("build_host", Build.HOST);
                info.put("build_fingerprint", Build.FINGERPRINT);
                
                // Информация о CPU
                JSONObject cpuInfo = getCpuInfo();
                info.put("cpu_info", cpuInfo);
                
                // Дисплей
                JSONObject displayInfo = getDisplayInfo();
                info.put("display_info", displayInfo);
                
                // Память
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(memInfo);
                
                info.put("total_memory", memInfo.totalMem);
                info.put("available_memory", memInfo.availMem);
                info.put("low_memory", memInfo.lowMemory);
                info.put("memory_threshold", memInfo.threshold);
                
                // Время работы
                info.put("uptime", getUptime());
                info.put("current_time", System.currentTimeMillis());
                
                if (callback != null) {
                    callback.onSystemInfo(info);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения системной информации: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Детальная информация о памяти
     */
    public void getMemoryInfo() {
        executorService.execute(() -> {
            try {
                JSONObject memInfo = new JSONObject();
                
                // Информация о памяти системы
                ActivityManager.MemoryInfo systemMemInfo = new ActivityManager.MemoryInfo();
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(systemMemInfo);
                
                memInfo.put("total_memory", systemMemInfo.totalMem);
                memInfo.put("available_memory", systemMemInfo.availMem);
                memInfo.put("used_memory", systemMemInfo.totalMem - systemMemInfo.availMem);
                memInfo.put("low_memory", systemMemInfo.lowMemory);
                memInfo.put("threshold", systemMemInfo.threshold);
                
                // Информация о памяти процесса
                Debug.MemoryInfo processMemInfo = new Debug.MemoryInfo();
                Debug.getMemoryInfo(processMemInfo);
                
                memInfo.put("process_pss", processMemInfo.getTotalPss() * 1024);
                memInfo.put("process_private_dirty", processMemInfo.getTotalPrivateDirty() * 1024);
                memInfo.put("process_shared_dirty", processMemInfo.getTotalSharedDirty() * 1024);
                memInfo.put("process_private_clean", processMemInfo.getTotalPrivateClean() * 1024);
                memInfo.put("process_shared_clean", processMemInfo.getTotalSharedClean() * 1024);
                
                // Информация из /proc/meminfo
                JSONObject procMemInfo = getProcMemInfo();
                memInfo.put("proc_memory", procMemInfo);
                
                if (callback != null) {
                    callback.onMemoryInfo(memInfo);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения информации о памяти: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Информация о хранилище
     */
    public void getStorageInfo() {
        executorService.execute(() -> {
            try {
                JSONObject storageInfo = new JSONObject();
                JSONArray storageList = new JSONArray();
                
                // Внутреннее хранилище
                File internalStorage = Environment.getDataDirectory();
                if (internalStorage.exists()) {
                    JSONObject internal = getStorageDetails(internalStorage, "internal");
                    storageList.put(internal);
                }
                
                // Внешнее хранилище
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    File externalStorage = Environment.getExternalStorageDirectory();
                    if (externalStorage.exists()) {
                        JSONObject external = getStorageDetails(externalStorage, "external");
                        storageList.put(external);
                    }
                }
                
                // Дополнительные внешние хранилища
                File[] externalDirs = context.getExternalFilesDirs(null);
                for (int i = 1; i < externalDirs.length; i++) {
                    if (externalDirs[i] != null && externalDirs[i].exists()) {
                        JSONObject removable = getStorageDetails(externalDirs[i], "removable");
                        storageList.put(removable);
                    }
                }
                
                storageInfo.put("storage_devices", storageList);
                storageInfo.put("external_storage_state", Environment.getExternalStorageState());
                storageInfo.put("is_external_storage_emulated", Environment.isExternalStorageEmulated());
                storageInfo.put("is_external_storage_removable", Environment.isExternalStorageRemovable());
                
                if (callback != null) {
                    callback.onStorageInfo(storageInfo);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения информации о хранилище: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Информация о батарее
     */
    public void getBatteryInfo() {
        executorService.execute(() -> {
            try {
                JSONObject batteryInfo = new JSONObject();
                
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, filter);
                
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                    int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    boolean present = batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);
                    String technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                    int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    
                    float batteryPercent = (level * 100.0f) / scale;
                    
                    batteryInfo.put("level", level);
                    batteryInfo.put("scale", scale);
                    batteryInfo.put("percentage", Math.round(batteryPercent));
                    batteryInfo.put("status", getBatteryStatus(status));
                    batteryInfo.put("health", getBatteryHealth(health));
                    batteryInfo.put("plugged", getPluggedStatus(plugged));
                    batteryInfo.put("present", present);
                    batteryInfo.put("technology", technology != null ? technology : "Unknown");
                    batteryInfo.put("temperature", temperature / 10.0f); // Celsius
                    batteryInfo.put("voltage", voltage / 1000.0f); // Volts
                    
                    // Является ли заряжается
                    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                       status == BatteryManager.BATTERY_STATUS_FULL;
                    batteryInfo.put("is_charging", isCharging);
                    
                    // Подключен ли к USB или AC
                    boolean usbCharge = plugged == BatteryManager.BATTERY_PLUGGED_USB;
                    boolean acCharge = plugged == BatteryManager.BATTERY_PLUGGED_AC;
                    boolean wirelessCharge = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                    
                    batteryInfo.put("usb_charging", usbCharge);
                    batteryInfo.put("ac_charging", acCharge);
                    batteryInfo.put("wireless_charging", wirelessCharge);
                }
                
                if (callback != null) {
                    callback.onBatteryInfo(batteryInfo);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения информации о батарее: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Информация о сети
     */
    public void getNetworkInfo() {
        executorService.execute(() -> {
            try {
                JSONObject networkInfo = new JSONObject();
                
                ConnectivityManager connectivityManager = 
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                
                if (connectivityManager != null) {
                    NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                    
                    if (activeNetwork != null) {
                        networkInfo.put("is_connected", activeNetwork.isConnected());
                        networkInfo.put("connection_type", activeNetwork.getTypeName());
                        networkInfo.put("connection_subtype", activeNetwork.getSubtypeName());
                        networkInfo.put("is_roaming", activeNetwork.isRoaming());
                        networkInfo.put("is_available", activeNetwork.isAvailable());
                        networkInfo.put("extra_info", activeNetwork.getExtraInfo());
                        networkInfo.put("reason", activeNetwork.getReason());
                    } else {
                        networkInfo.put("is_connected", false);
                        networkInfo.put("connection_type", "none");
                    }
                    
                    // Информация о всех сетях
                    JSONArray allNetworks = new JSONArray();
                    NetworkInfo[] allNetworkInfo = connectivityManager.getAllNetworkInfo();
                    
                    if (allNetworkInfo != null) {
                        for (NetworkInfo network : allNetworkInfo) {
                            JSONObject netInfo = new JSONObject();
                            netInfo.put("type", network.getTypeName());
                            netInfo.put("subtype", network.getSubtypeName());
                            netInfo.put("state", network.getState().toString());
                            netInfo.put("detailed_state", network.getDetailedState().toString());
                            netInfo.put("is_available", network.isAvailable());
                            netInfo.put("is_connected", network.isConnected());
                            netInfo.put("is_roaming", network.isRoaming());
                            allNetworks.put(netInfo);
                        }
                    }
                    
                    networkInfo.put("all_networks", allNetworks);
                }
                
                if (callback != null) {
                    callback.onNetworkInfo(networkInfo);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения информации о сети: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Список процессов
     */
    public void getProcessList() {
        executorService.execute(() -> {
            try {
                JSONArray processArray = new JSONArray();
                
                ActivityManager activityManager = 
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                
                if (activityManager != null) {
                    List<ActivityManager.RunningAppProcessInfo> processes = 
                        activityManager.getRunningAppProcesses();
                    
                    if (processes != null) {
                        for (ActivityManager.RunningAppProcessInfo process : processes) {
                            JSONObject processInfo = new JSONObject();
                            processInfo.put("pid", process.pid);
                            processInfo.put("process_name", process.processName);
                            processInfo.put("uid", process.uid);
                            processInfo.put("importance", getImportanceLevel(process.importance));
                            processInfo.put("packages", new JSONArray(process.pkgList));
                            
                            // Получение информации о памяти процесса
                            int[] pids = {process.pid};
                            Debug.MemoryInfo[] memInfos = activityManager.getProcessMemoryInfo(pids);
                            if (memInfos.length > 0) {
                                Debug.MemoryInfo memInfo = memInfos[0];
                                processInfo.put("memory_pss", memInfo.getTotalPss() * 1024);
                                processInfo.put("memory_private_dirty", memInfo.getTotalPrivateDirty() * 1024);
                                processInfo.put("memory_shared_dirty", memInfo.getTotalSharedDirty() * 1024);
                            }
                            
                            processArray.put(processInfo);
                        }
                    }
                }
                
                if (callback != null) {
                    callback.onProcessList(processArray);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения списка процессов: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Список установленных приложений
     */
    public void getInstalledApps() {
        executorService.execute(() -> {
            try {
                JSONArray appsArray = new JSONArray();
                
                PackageManager packageManager = context.getPackageManager();
                List<PackageInfo> packages = packageManager.getInstalledPackages(0);
                
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                
                for (PackageInfo packageInfo : packages) {
                    JSONObject appInfo = new JSONObject();
                    
                    appInfo.put("package_name", packageInfo.packageName);
                    appInfo.put("version_name", packageInfo.versionName);
                    appInfo.put("version_code", packageInfo.versionCode);
                    appInfo.put("install_time", dateFormat.format(new Date(packageInfo.firstInstallTime)));
                    appInfo.put("update_time", dateFormat.format(new Date(packageInfo.lastUpdateTime)));
                    
                    try {
                        ApplicationInfo appInfoDetails = packageInfo.applicationInfo;
                        String appName = (String) packageManager.getApplicationLabel(appInfoDetails);
                        appInfo.put("app_name", appName);
                        appInfo.put("is_system_app", (appInfoDetails.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                        appInfo.put("enabled", appInfoDetails.enabled);
                        appInfo.put("target_sdk", appInfoDetails.targetSdkVersion);
                        
                        File apkFile = new File(appInfoDetails.sourceDir);
                        appInfo.put("apk_size", apkFile.exists() ? apkFile.length() : 0);
                        
                    } catch (Exception e) {
                        appInfo.put("app_name", packageInfo.packageName);
                        appInfo.put("is_system_app", false);
                    }
                    
                    appsArray.put(appInfo);
                }
                
                if (callback != null) {
                    callback.onInstalledApps(appsArray);
                }
                
                Log.i(TAG, "Найдено установленных приложений: " + appsArray.length());
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения списка приложений: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private JSONObject getCpuInfo() {
        JSONObject cpuInfo = new JSONObject();
        
        try {
            // Количество ядер процессора
            cpuInfo.put("cpu_cores", Runtime.getRuntime().availableProcessors());
            
            // Информация из /proc/cpuinfo
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            JSONArray processors = new JSONArray();
            JSONObject currentProcessor = new JSONObject();
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (currentProcessor.length() > 0) {
                        processors.put(currentProcessor);
                        currentProcessor = new JSONObject();
                    }
                    continue;
                }
                
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    currentProcessor.put(key.replace(" ", "_"), value);
                }
            }
            
            if (currentProcessor.length() > 0) {
                processors.put(currentProcessor);
            }
            
            reader.close();
            cpuInfo.put("processors", processors);
            
        } catch (Exception e) {
            Log.w(TAG, "Ошибка чтения CPU info: " + e.getMessage());
        }
        
        return cpuInfo;
    }
    
    private JSONObject getDisplayInfo() {
        JSONObject displayInfo = new JSONObject();
        
        try {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            displayInfo.put("width_pixels", metrics.widthPixels);
            displayInfo.put("height_pixels", metrics.heightPixels);
            displayInfo.put("density", metrics.density);
            displayInfo.put("density_dpi", metrics.densityDpi);
            displayInfo.put("scaled_density", metrics.scaledDensity);
            displayInfo.put("xdpi", metrics.xdpi);
            displayInfo.put("ydpi", metrics.ydpi);
            
        } catch (Exception e) {
            Log.w(TAG, "Ошибка получения информации о дисплее: " + e.getMessage());
        }
        
        return displayInfo;
    }
    
    private JSONObject getProcMemInfo() {
        JSONObject memInfo = new JSONObject();
        
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("\\s+kB", "");
                    
                    try {
                        long bytes = Long.parseLong(value) * 1024; // Конвертируем kB в байты
                        memInfo.put(key.toLowerCase().replace(" ", "_"), bytes);
                    } catch (NumberFormatException e) {
                        // Пропускаем строки, которые не являются числами
                    }
                }
            }
            
            reader.close();
            
        } catch (Exception e) {
            Log.w(TAG, "Ошибка чтения /proc/meminfo: " + e.getMessage());
        }
        
        return memInfo;
    }
    
    private JSONObject getStorageDetails(File storage, String type) {
        JSONObject storageDetails = new JSONObject();
        
        try {
            StatFs stat = new StatFs(storage.getPath());
            
            long totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
            long freeBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long usedBytes = totalBytes - freeBytes;
            
            storageDetails.put("path", storage.getAbsolutePath());
            storageDetails.put("type", type);
            storageDetails.put("total_space", totalBytes);
            storageDetails.put("free_space", freeBytes);
            storageDetails.put("used_space", usedBytes);
            storageDetails.put("available_space", stat.getAvailableBlocksLong() * stat.getBlockSizeLong());
            storageDetails.put("block_size", stat.getBlockSizeLong());
            storageDetails.put("block_count", stat.getBlockCountLong());
            storageDetails.put("free_blocks", stat.getFreeBlocksLong());
            storageDetails.put("available_blocks", stat.getAvailableBlocksLong());
            storageDetails.put("usage_percentage", Math.round(((double) usedBytes / totalBytes) * 100));
            
        } catch (Exception e) {
            Log.w(TAG, "Ошибка получения информации о хранилище: " + e.getMessage());
        }
        
        return storageDetails;
    }
    
    private long getUptime() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/uptime"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length > 0) {
                    return (long) (Double.parseDouble(parts[0]) * 1000); // Конвертируем в миллисекунды
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Ошибка чтения uptime: " + e.getMessage());
        }
        
        return System.currentTimeMillis(); // Fallback
    }
    
    private String getBatteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not_charging";
            case BatteryManager.BATTERY_STATUS_UNKNOWN: return "unknown";
            default: return "unknown";
        }
    }
    
    private String getBatteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "unspecified_failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN: return "unknown";
            default: return "unknown";
        }
    }
    
    private String getPluggedStatus(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "ac";
            case BatteryManager.BATTERY_PLUGGED_USB: return "usb";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "wireless";
            case 0: return "unplugged";
            default: return "unknown";
        }
    }
    
    private String getImportanceLevel(int importance) {
        switch (importance) {
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND: return "foreground";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE: return "foreground_service";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING: return "top_sleeping";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE: return "visible";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE: return "perceptible";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE: return "cant_save_state";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE: return "service";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED: return "cached";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE: return "gone";
            default: return "unknown";
        }
    }
    
    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.i(TAG, "SystemMonitor очищен");
    }
}