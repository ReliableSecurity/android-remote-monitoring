package com.example.remotemonitor;

import android.Manifest;
import android.app.Service;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.List;

/**
 * Сервис для мониторинга устройства и выполнения команд с сервера
 * ОБРАЗОВАТЕЛЬНЫЙ ПРОЕКТ
 */
public class MonitoringService extends Service {
    
    private static final String TAG = "MonitoringService";
    private static final String SERVER_KEY = "educational_project_key_2024";
    private static final String CHANNEL_ID = "monitoring_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int FOREGROUND_SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;
    private Thread connectionThread;
    
    private String serverIP;
    private int serverPort;
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            serverIP = intent.getStringExtra("server_ip");
            serverPort = intent.getIntExtra("server_port", 8443);
            
            Log.i(TAG, "Запуск сервиса мониторинга: " + serverIP + ":" + serverPort);
            
            // Создание уведомления и запуск foreground service
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification("Подключение к серверу..."));
            
            startConnection();
        }
        
        return START_STICKY; // Перезапуск при завершении
    }
    
    private void startConnection() {
        connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectToServer();
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка подключения к серверу: " + e.getMessage());
                }
            }
        });
        connectionThread.start();
    }
    
    private void connectToServer() throws IOException {
        socket = new Socket(serverIP, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        isConnected = true;
        
        Log.i(TAG, "Подключен к серверу");
        updateNotification("Подключен к " + serverIP);
        
        // Обработка аутентификации
        handleAuthentication();
        
        // Основной цикл обработки команд
        handleCommands();
    }
    
    private void handleAuthentication() throws IOException {
        try {
            // Получение challenge от сервера
            String authRequest = in.readLine();
            JSONObject authData = new JSONObject(authRequest);
            
            if ("auth_challenge".equals(authData.getString("type"))) {
                String challenge = authData.getString("challenge");
                
                // Генерация ответа
                String response = generateAuthResponse(challenge);
                
                JSONObject authResponse = new JSONObject();
                authResponse.put("response", response);
                
                out.println(authResponse.toString());
                
                // Получение подтверждения
                String authResult = in.readLine();
                JSONObject resultData = new JSONObject(authResult);
                
                if ("auth_success".equals(resultData.getString("type"))) {
                    Log.i(TAG, "Аутентификация успешна");
                    updateNotification("Аутентифицирован");
                } else {
                    Log.e(TAG, "Аутентификация не удалась");
                    disconnect();
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка аутентификации: " + e.getMessage());
            disconnect();
        }
    }
    
    private String generateAuthResponse(String challenge) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = challenge + SERVER_KEY;
            byte[] hash = digest.digest(combined.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка генерации ответа аутентификации: " + e.getMessage());
            return "";
        }
    }
    
    private void handleCommands() throws IOException {
        String inputLine;
        while (isConnected && (inputLine = in.readLine()) != null) {
            try {
                JSONObject commandData = new JSONObject(inputLine);
                String type = commandData.getString("type");
                
                if ("command_menu".equals(type)) {
                    // Отправляем выбранную команду (для демонстрации берем первую)
                    JSONObject response = new JSONObject();
                    response.put("command", "info");
                    out.println(response.toString());
                    
                } else if ("execute_command".equals(type)) {
                    String command = commandData.getString("command");
                    JSONObject result = executeCommand(command);
                    out.println(result.toString());
                }
                
            } catch (JSONException e) {
                Log.e(TAG, "Ошибка обработки команды: " + e.getMessage());
            }
        }
    }
    
    private JSONObject executeCommand(String command) {
        JSONObject result = new JSONObject();
        
        try {
            result.put("status", "success");
            result.put("timestamp", System.currentTimeMillis() / 1000);
            
            JSONObject data = new JSONObject();
            
            switch (command) {
                case "info":
                    data = getSystemInfo();
                    break;
                case "battery":
                    data = getBatteryInfo();
                    break;
                case "location":
                    data = getLocationInfo();
                    break;
                case "photo":
                    data = takePhoto();
                    break;
                case "network":
                    data = getNetworkInfo();
                    break;
                case "storage":
                    data = getStorageInfo();
                    break;
                case "apps":
                    data = getInstalledApps();
                    break;
                default:
                    result.put("status", "error");
                    data.put("message", "Неизвестная команда");
            }
            
            result.put("data", data);
            
        } catch (JSONException e) {
            try {
                result.put("status", "error");
                result.put("data", new JSONObject().put("message", e.getMessage()));
            } catch (JSONException ex) {
                Log.e(TAG, "Критическая ошибка JSON: " + ex.getMessage());
            }
        }
        
        return result;
    }
    
    private JSONObject getSystemInfo() throws JSONException {
        JSONObject info = new JSONObject();
        
        info.put("device_model", Build.MODEL);
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("android_version", Build.VERSION.RELEASE);
        info.put("api_level", Build.VERSION.SDK_INT);
        info.put("device_id", Build.ID);
        
        return info;
    }
    
    private JSONObject getBatteryInfo() throws JSONException {
        JSONObject battery = new JSONObject();
        
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            
            battery.put("level", batteryLevel);
            battery.put("status", getBatteryStatusString(status));
        }
        
        return battery;
    }
    
    private String getBatteryStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Заряжается";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Разряжается";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Заряжена";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Не заряжается";
            default:
                return "Неизвестно";
        }
    }
    
    private JSONObject getLocationInfo() throws JSONException {
        JSONObject location = new JSONObject();
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            location.put("error", "Нет разрешения на доступ к местоположению");
            return location;
        }
        
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                location.put("latitude", lastLocation.getLatitude());
                location.put("longitude", lastLocation.getLongitude());
                location.put("accuracy", lastLocation.getAccuracy());
                location.put("timestamp", lastLocation.getTime());
            } else {
                location.put("error", "GPS недоступен");
            }
        }
        
        return location;
    }
    
    private JSONObject takePhoto() throws JSONException {
        JSONObject photo = new JSONObject();
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            photo.put("error", "Нет разрешения на доступ к камере");
            return photo;
        }
        
        // Примечание: В реальном приложении здесь была бы сложная логика работы с Camera2 API
        // Для образовательных целей возвращаем заглушку
        photo.put("image_base64", ""); // Пустая строка - камера недоступна в сервисе
        photo.put("note", "Фото с камеры требует UI активности");
        
        return photo;
    }
    
    private JSONObject getNetworkInfo() throws JSONException {
        JSONObject network = new JSONObject();
        
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager != null) {
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            
            network.put("wifi_status", wifiInfo != null && wifiInfo.isConnected() ? "Подключен" : "Отключен");
            network.put("mobile_status", mobileInfo != null && mobileInfo.isConnected() ? "Подключен" : "Отключен");
        }
        
        return network;
    }
    
    private JSONObject getStorageInfo() throws JSONException {
        JSONObject storage = new JSONObject();
        
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        long bytesTotal = stat.getBlockSizeLong() * stat.getBlockCountLong();
        
        storage.put("free_space", bytesAvailable / (1024 * 1024)); // MB
        storage.put("total_space", bytesTotal / (1024 * 1024)); // MB
        
        return storage;
    }
    
    private JSONObject getInstalledApps() throws JSONException {
        JSONObject appsData = new JSONObject();
        JSONArray apps = new JSONArray();
        
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        
        for (ApplicationInfo appInfo : installedApps) {
            JSONObject app = new JSONObject();
            app.put("name", packageManager.getApplicationLabel(appInfo).toString());
            app.put("package", appInfo.packageName);
            apps.put(app);
        }
        
        appsData.put("installed_apps", apps);
        return appsData;
    }
    
    private void disconnect() {
        isConnected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Ошибка при отключении: " + e.getMessage());
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Мониторинг устройства",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Образовательный сервис мониторинга");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private android.app.Notification createNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📱 Remote Monitor")
            .setContentText("⚠️ Образовательный проект: " + status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
    
    private void updateNotification(String status) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(status));
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        Log.i(TAG, "Сервис мониторинга остановлен");
    }
}