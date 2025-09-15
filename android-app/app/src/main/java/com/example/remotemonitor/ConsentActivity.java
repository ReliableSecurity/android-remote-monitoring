package com.example.remotemonitor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Активность для получения явного согласия пользователя
 * на мониторинг устройства
 */
public class ConsentActivity extends AppCompatActivity {
    
    private CheckBox checkboxConsent;
    private CheckBox checkboxEducational;
    private CheckBox checkboxOwnDevice;
    private CheckBox checkboxNoMalicious;
    private Button btnAccept;
    private Button btnDecline;
    private TextView textConsentDetails;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);
        
        initializeViews();
        setupConsentText();
        setupButtonListeners();
        updateAcceptButtonState();
    }
    
    private void initializeViews() {
        checkboxConsent = findViewById(R.id.checkbox_consent);
        checkboxEducational = findViewById(R.id.checkbox_educational);
        checkboxOwnDevice = findViewById(R.id.checkbox_own_device);
        checkboxNoMalicious = findViewById(R.id.checkbox_no_malicious);
        btnAccept = findViewById(R.id.btn_accept);
        btnDecline = findViewById(R.id.btn_decline);
        textConsentDetails = findViewById(R.id.text_consent_details);
    }
    
    private void setupConsentText() {
        String consentText = "<h2>🔒 СОГЛАШЕНИЕ О МОНИТОРИНГЕ УСТРОЙСТВА</h2>" +
                "<p><b>⚠️ ОБРАЗОВАТЕЛЬНЫЙ ПРОЕКТ</b></p>" +
                "<p>Данное приложение предназначено <u>ТОЛЬКО</u> для образовательных целей и изучения технологий Android.</p>" +
                "<br><h3>Функции приложения:</h3>" +
                "<ul>" +
                "<li>📱 Получение системной информации (модель, версия Android)</li>" +
                "<li>🔋 Информация о батарее (уровень заряда, статус)</li>" +
                "<li>📶 Информация о сети (WiFi, мобильная связь)</li>" +
                "<li>💾 Информация о хранилище (свободное место)</li>" +
                "<li>📷 Фото с камеры (ТОЛЬКО с вашего разрешения)</li>" +
                "<li>📍 GPS координаты (ТОЛЬКО с вашего разрешения)</li>" +
                "<li>📱 Список установленных приложений</li>" +
                "</ul>" +
                "<br><h3>🔒 Меры безопасности:</h3>" +
                "<ul>" +
                "<li>Все операции записываются в лог</li>" +
                "<li>Вы можете отключить мониторинг в любое время</li>" +
                "<li>Запрашивается явное разрешение на каждое действие</li>" +
                "<li>Данные передаются с аутентификацией</li>" +
                "</ul>" +
                "<br><h3>❌ Что приложение НЕ ДЕЛАЕТ:</h3>" +
                "<ul>" +
                "<li>Не читает SMS или звонки</li>" +
                "<li>Не получает доступ к контактам</li>" +
                "<li>Не записывает аудио без разрешения</li>" +
                "<li>Не крадет личные данные</li>" +
                "<li>Не устанавливает вредоносное ПО</li>" +
                "</ul>" +
                "<br><p><b>🚨 ВАЖНО:</b> Используйте это приложение ТОЛЬКО:</p>" +
                "<ul>" +
                "<li>На собственных устройствах</li>" +
                "<li>Для образовательных целей</li>" +
                "<li>С соблюдением местных законов</li>" +
                "<li>Без нарушения чужой приватности</li>" +
                "</ul>"
        
        textConsentDetails.setText(Html.fromHtml(consentText, Html.FROM_HTML_MODE_COMPACT));
    }
    
    private void setupButtonListeners() {
        // Слушатели для чекбоксов
        View.OnClickListener checkboxListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateAcceptButtonState();
            }
        };
        
        checkboxConsent.setOnClickListener(checkboxListener);
        checkboxEducational.setOnClickListener(checkboxListener);
        checkboxOwnDevice.setOnClickListener(checkboxListener);
        checkboxNoMalicious.setOnClickListener(checkboxListener);
        
        // Кнопка принятия
        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (allCheckboxesChecked()) {
                    // Согласие получено
                    Intent result = new Intent();
                    result.putExtra("consent_granted", true);
                    result.putExtra("timestamp", System.currentTimeMillis());
                    setResult(Activity.RESULT_OK, result);
                    finish();
                }
            }
        });
        
        // Кнопка отклонения
        btnDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Согласие не получено
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }
    
    private void updateAcceptButtonState() {
        boolean allChecked = allCheckboxesChecked();
        btnAccept.setEnabled(allChecked);
        
        if (allChecked) {
            btnAccept.setAlpha(1.0f);
            btnAccept.setText("✅ ПРИНИМАЮ УСЛОВИЯ");
        } else {
            btnAccept.setAlpha(0.5f);
            btnAccept.setText("Отметьте все пункты");
        }
    }
    
    private boolean allCheckboxesChecked() {
        return checkboxConsent.isChecked() && 
               checkboxEducational.isChecked() && 
               checkboxOwnDevice.isChecked() && 
               checkboxNoMalicious.isChecked();
    }
    
    @Override
    public void onBackPressed() {
        // Если пользователь нажал назад, считаем что согласие не дано
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }
}