package com.example.remotemonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ============================================================================
 * NETWORK CONFIGURATION MANAGER
 * ============================================================================
 * 
 * Класс для централизованного управления настройками сети.
 * Поддерживает:
 * - Настройка через gradle properties (время сборки)
 * - Настройка через SharedPreferences (время выполнения)
 * - Значения по умолчанию
 * - Валидация параметров
 * 
 * Автор: ReliableSecurity
 * Telegram: @ReliableSecurity
 * GitHub: https://github.com/ReliableSecurity/android-remote-monitoring
 * ============================================================================
 */
public class NetworkConfig {
    
    private static final String TAG = "NetworkConfig";
    private static final String PREFS_NAME = "NetworkConfigPrefs";
    
    // ============================================================================
    // КОНСТАНТЫ ПО УМОЛЧАНИЮ
    // ============================================================================
    
    /**
     * IP адрес сервера по умолчанию
     * Может быть переопределен через gradle properties или runtime настройки
     */
    public static final String DEFAULT_SERVER_IP = "192.168.1.100";
    
    /**
     * Порт сервера по умолчанию
     * Может быть переопределен через gradle properties или runtime настройки
     */
    public static final int DEFAULT_SERVER_PORT = 8080;
    
    /**
     * Протокол по умолчанию (HTTP/HTTPS)
     */
    public static final boolean DEFAULT_USE_HTTPS = true;
    
    /**
     * Таймаут соединения по умолчанию (мс)
     */
    public static final int DEFAULT_CONNECTION_TIMEOUT = 30000; // 30 секунд
    
    /**
     * Таймаут чтения по умолчанию (мс)
     */
    public static final int DEFAULT_READ_TIMEOUT = 60000; // 60 секунд
    
    /**
     * Интервал отправки heartbeat (мс)
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30000; // 30 секунд
    
    // ============================================================================
    // НАСТРОЙКИ ВРЕМЕНИ СБОРКИ (GRADLE PROPERTIES)
    // ============================================================================
    
    /**
     * IP адрес сервера из BuildConfig (устанавливается при сборке APK)
     * Эти значения перезаписываются скриптом build_apk.sh
     */
    public static final String SERVER_IP = BuildConfig.SERVER_IP != null ? 
        BuildConfig.SERVER_IP : DEFAULT_SERVER_IP;
    
    /**
     * Порт сервера из BuildConfig (устанавливается при сборке APK)
     */
    public static final int SERVER_PORT = BuildConfig.SERVER_PORT != 0 ? 
        BuildConfig.SERVER_PORT : DEFAULT_SERVER_PORT;
    
    // ============================================================================
    // RUNTIME НАСТРОЙКИ
    // ============================================================================
    
    private Context context;
    private SharedPreferences preferences;
    
    // Кешированные значения
    private String cachedServerIP;
    private int cachedServerPort;
    private boolean cachedUseHttps;
    private int cachedConnectionTimeout;
    private int cachedReadTimeout;
    private int cachedHeartbeatInterval;
    
    /**
     * Конструктор
     */
    public NetworkConfig(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Инициализация кешированных значений
        loadCachedValues();
        
        Log.i(TAG, "NetworkConfig инициализирован");
        Log.i(TAG, "Сервер: " + getServerIP() + ":" + getServerPort());
        Log.i(TAG, "Протокол: " + (getUseHttps() ? "HTTPS" : "HTTP"));
    }
    
    /**
     * Загрузка кешированных значений
     */
    private void loadCachedValues() {
        // IP адрес: сначала из runtime настроек, затем из BuildConfig, затем по умолчанию
        cachedServerIP = preferences.getString("server_ip", SERVER_IP);
        
        // Порт: сначала из runtime настроек, затем из BuildConfig, затем по умолчанию
        cachedServerPort = preferences.getInt("server_port", SERVER_PORT);
        
        // Остальные настройки из SharedPreferences или по умолчанию
        cachedUseHttps = preferences.getBoolean("use_https", DEFAULT_USE_HTTPS);
        cachedConnectionTimeout = preferences.getInt("connection_timeout", DEFAULT_CONNECTION_TIMEOUT);
        cachedReadTimeout = preferences.getInt("read_timeout", DEFAULT_READ_TIMEOUT);
        cachedHeartbeatInterval = preferences.getInt("heartbeat_interval", DEFAULT_HEARTBEAT_INTERVAL);
    }
    
    // ============================================================================
    // GETTERS
    // ============================================================================
    
    /**
     * Получить IP адрес сервера
     */
    public String getServerIP() {
        return cachedServerIP;
    }
    
    /**
     * Получить порт сервера
     */
    public int getServerPort() {
        return cachedServerPort;
    }
    
    /**
     * Использовать HTTPS?
     */
    public boolean getUseHttps() {
        return cachedUseHttps;
    }
    
    /**
     * Получить таймаут соединения
     */
    public int getConnectionTimeout() {
        return cachedConnectionTimeout;
    }
    
    /**
     * Получить таймаут чтения
     */
    public int getReadTimeout() {
        return cachedReadTimeout;
    }
    
    /**
     * Получить интервал heartbeat
     */
    public int getHeartbeatInterval() {
        return cachedHeartbeatInterval;
    }
    
    /**
     * Получить полный URL сервера
     */
    public String getServerURL() {
        String protocol = getUseHttps() ? "https" : "http";
        return protocol + "://" + getServerIP() + ":" + getServerPort();
    }
    
    /**
     * Получить URL для конкретного endpoint
     */
    public String getEndpointURL(String endpoint) {
        return getServerURL() + "/" + endpoint.replaceFirst("^/", "");
    }
    
    // ============================================================================
    // SETTERS (С ВАЛИДАЦИЕЙ И СОХРАНЕНИЕМ)
    // ============================================================================
    
    /**
     * Установить IP адрес сервера
     */
    public boolean setServerIP(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            Log.w(TAG, "Попытка установить пустой IP адрес");
            return false;
        }
        
        // Простая валидация IP адреса
        if (!isValidIPAddress(ip.trim())) {
            Log.w(TAG, "Некорректный IP адрес: " + ip);
            return false;
        }
        
        cachedServerIP = ip.trim();
        preferences.edit().putString("server_ip", cachedServerIP).apply();
        
        Log.i(TAG, "IP адрес сервера изменен на: " + cachedServerIP);
        return true;
    }
    
    /**
     * Установить порт сервера
     */
    public boolean setServerPort(int port) {
        if (port <= 0 || port > 65535) {
            Log.w(TAG, "Некорректный порт: " + port);
            return false;
        }
        
        cachedServerPort = port;
        preferences.edit().putInt("server_port", cachedServerPort).apply();
        
        Log.i(TAG, "Порт сервера изменен на: " + cachedServerPort);
        return true;
    }
    
    /**
     * Установить использование HTTPS
     */
    public void setUseHttps(boolean useHttps) {
        cachedUseHttps = useHttps;
        preferences.edit().putBoolean("use_https", cachedUseHttps).apply();
        
        Log.i(TAG, "Протокол изменен на: " + (cachedUseHttps ? "HTTPS" : "HTTP"));
    }
    
    /**
     * Установить таймаут соединения
     */
    public boolean setConnectionTimeout(int timeout) {
        if (timeout < 1000 || timeout > 300000) { // От 1 секунды до 5 минут
            Log.w(TAG, "Некорректный таймаут соединения: " + timeout);
            return false;
        }
        
        cachedConnectionTimeout = timeout;
        preferences.edit().putInt("connection_timeout", cachedConnectionTimeout).apply();
        
        Log.i(TAG, "Таймаут соединения изменен на: " + cachedConnectionTimeout + " мс");
        return true;
    }
    
    /**
     * Установить таймаут чтения
     */
    public boolean setReadTimeout(int timeout) {
        if (timeout < 1000 || timeout > 600000) { // От 1 секунды до 10 минут
            Log.w(TAG, "Некорректный таймаут чтения: " + timeout);
            return false;
        }
        
        cachedReadTimeout = timeout;
        preferences.edit().putInt("read_timeout", cachedReadTimeout).apply();
        
        Log.i(TAG, "Таймаут чтения изменен на: " + cachedReadTimeout + " мс");
        return true;
    }
    
    /**
     * Установить интервал heartbeat
     */
    public boolean setHeartbeatInterval(int interval) {
        if (interval < 5000 || interval > 300000) { // От 5 секунд до 5 минут
            Log.w(TAG, "Некорректный интервал heartbeat: " + interval);
            return false;
        }
        
        cachedHeartbeatInterval = interval;
        preferences.edit().putInt("heartbeat_interval", cachedHeartbeatInterval).apply();
        
        Log.i(TAG, "Интервал heartbeat изменен на: " + cachedHeartbeatInterval + " мс");
        return true;
    }
    
    // ============================================================================
    // UTILITY МЕТОДЫ
    // ============================================================================
    
    /**
     * Валидация IP адреса (простая)
     */
    private boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Проверка на localhost и доменные имена
        if (ip.equals("localhost") || ip.matches("^[a-zA-Z0-9.-]+$")) {
            return true;
        }
        
        // Простая проверка IPv4
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Проверка доступности сервера (ping)
     */
    public boolean isServerReachable() {
        try {
            Process process = Runtime.getRuntime().exec("ping -c 1 -W 3000 " + getServerIP());
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.d(TAG, "Ошибка ping сервера: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Сброс настроек к значениям по умолчанию
     */
    public void resetToDefaults() {
        preferences.edit().clear().apply();
        loadCachedValues();
        
        Log.i(TAG, "Настройки сброшены к значениям по умолчанию");
    }
    
    /**
     * Получение всех настроек в виде строки для отладки
     */
    public String getConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NetworkConfig Settings:\n");
        sb.append("  Server IP: ").append(getServerIP()).append("\n");
        sb.append("  Server Port: ").append(getServerPort()).append("\n");
        sb.append("  Use HTTPS: ").append(getUseHttps()).append("\n");
        sb.append("  Connection Timeout: ").append(getConnectionTimeout()).append(" ms\n");
        sb.append("  Read Timeout: ").append(getReadTimeout()).append(" ms\n");
        sb.append("  Heartbeat Interval: ").append(getHeartbeatInterval()).append(" ms\n");
        sb.append("  Server URL: ").append(getServerURL()).append("\n");
        return sb.toString();
    }
    
    /**
     * Проверка, изменились ли настройки с момента сборки
     */
    public boolean hasRuntimeChanges() {
        return !getServerIP().equals(SERVER_IP) || getServerPort() != SERVER_PORT;
    }
    
    /**
     * Получение информации о источнике настроек
     */
    public String getConfigSource() {
        if (hasRuntimeChanges()) {
            return "Runtime (SharedPreferences)";
        } else {
            return "Build-time (gradle.properties)";
        }
    }
}