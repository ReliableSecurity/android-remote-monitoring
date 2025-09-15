package com.example.remotemonitor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Система аудио мониторинга для Android 5.0+
 * Запись микрофона, окружающих звуков, двусторонняя связь
 */
public class AudioManager {
    
    private static final String TAG = "AudioManager";
    private Context context;
    
    // Настройки аудио
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 2;
    
    // Состояние
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isStreaming = new AtomicBoolean(false);
    
    // Callbacks
    public interface AudioCallback {
        void onAudioData(String base64Audio, int sampleRate, int channels);
        void onAudioLevel(int level); // 0-100
        void onError(String error);
        void onRecordingStarted();
        void onRecordingStopped();
    }
    
    private AudioCallback callback;
    
    public AudioManager(Context context) {
        this.context = context;
    }
    
    public void setCallback(AudioCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Проверка разрешения на запись аудио
     */
    public boolean hasAudioPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Получение информации об аудио системе
     */
    public JSONObject getAudioInfo() {
        JSONObject info = new JSONObject();
        
        try {
            info.put("has_microphone", context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
            info.put("sample_rate", SAMPLE_RATE);
            info.put("channel_config", "MONO");
            info.put("audio_format", "PCM_16BIT");
            info.put("sdk_version", Build.VERSION.SDK_INT);
            info.put("is_recording", isRecording.get());
            info.put("is_streaming", isStreaming.get());
            
            // Проверка минимального размера буфера
            int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            info.put("min_buffer_size", minBufferSize);
            info.put("recommended_buffer_size", minBufferSize * BUFFER_SIZE_FACTOR);
            
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания аудио информации: " + e.getMessage());
        }
        
        return info;
    }
    
    /**
     * Начало записи аудио
     */
    public boolean startRecording() {
        if (!hasAudioPermission()) {
            if (callback != null) {
                callback.onError("Нет разрешения на запись аудио");
            }
            return false;
        }
        
        if (isRecording.get()) {
            Log.w(TAG, "Запись уже идет");
            return true;
        }
        
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || 
                minBufferSize == AudioRecord.ERROR) {
                if (callback != null) {
                    callback.onError("Неподдерживаемые параметры аудио");
                }
                return false;
            }
            
            int bufferSize = minBufferSize * BUFFER_SIZE_FACTOR;
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                if (callback != null) {
                    callback.onError("Не удалось инициализировать AudioRecord");
                }
                return false;
            }
            
            audioRecord.startRecording();
            isRecording.set(true);
            
            // Запуск потока записи
            recordingThread = new Thread(this::recordingLoop);
            recordingThread.start();
            
            if (callback != null) {
                callback.onRecordingStarted();
            }
            
            Log.i(TAG, "Запись аудио начата");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка начала записи: " + e.getMessage());
            if (callback != null) {
                callback.onError("Ошибка начала записи: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Остановка записи аудио
     */
    public void stopRecording() {
        if (!isRecording.get()) {
            return;
        }
        
        isRecording.set(false);
        isStreaming.set(false);
        
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            
            if (recordingThread != null) {
                recordingThread.interrupt();
                recordingThread = null;
            }
            
            if (callback != null) {
                callback.onRecordingStopped();
            }
            
            Log.i(TAG, "Запись аудио остановлена");
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка остановки записи: " + e.getMessage());
        }
    }
    
    /**
     * Включение/выключение стриминга аудио
     */
    public void setStreaming(boolean streaming) {
        isStreaming.set(streaming);
        Log.i(TAG, "Стриминг аудио: " + (streaming ? "включен" : "выключен"));
    }
    
    /**
     * Основной цикл записи аудио
     */
    private void recordingLoop() {
        int bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;
        byte[] audioBuffer = new byte[bufferSize];
        
        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        long lastStreamTime = 0;
        final long STREAM_INTERVAL = 500; // 500ms между отправками
        
        while (isRecording.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                
                if (bytesRead > 0) {
                    // Вычисление уровня аудио
                    int audioLevel = calculateAudioLevel(audioBuffer, bytesRead);
                    
                    if (callback != null) {
                        callback.onAudioLevel(audioLevel);
                    }
                    
                    // Накопление данных для стриминга
                    if (isStreaming.get()) {
                        audioStream.write(audioBuffer, 0, bytesRead);
                        
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastStreamTime >= STREAM_INTERVAL) {
                            // Отправка накопленных данных
                            byte[] streamData = audioStream.toByteArray();
                            if (streamData.length > 0) {
                                String base64Audio = Base64.encodeToString(streamData, Base64.DEFAULT);
                                
                                if (callback != null) {
                                    callback.onAudioData(base64Audio, SAMPLE_RATE, 1);
                                }
                            }
                            
                            audioStream.reset();
                            lastStreamTime = currentTime;
                        }
                    }
                    
                } else {
                    Log.w(TAG, "Ошибка чтения аудио: " + bytesRead);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка в цикле записи: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка записи аудио: " + e.getMessage());
                }
                break;
            }
        }
        
        Log.i(TAG, "Цикл записи завершен");
    }
    
    /**
     * Вычисление уровня аудио (0-100)
     */
    private int calculateAudioLevel(byte[] audioData, int length) {
        if (length == 0) return 0;
        
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            if (i + 1 < length) {
                // Конвертация в 16-bit значение
                short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
                sum += Math.abs(sample);
            }
        }
        
        // Среднее значение
        double average = sum / (double) (length / 2);
        
        // Нормализация к 0-100
        int level = (int) (average / 327.67); // 32767 / 100
        return Math.min(100, Math.max(0, level));
    }
    
    /**
     * Запись одиночного аудио семпла
     */
    public void captureAudioSample(int durationMs) {
        if (!hasAudioPermission()) {
            if (callback != null) {
                callback.onError("Нет разрешения на запись аудио");
            }
            return;
        }
        
        new Thread(() -> {
            AudioRecord tempAudioRecord = null;
            
            try {
                int minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                int bufferSize = minBufferSize * BUFFER_SIZE_FACTOR;
                
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                
                tempAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                );
                
                if (tempAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    if (callback != null) {
                        callback.onError("Не удалось инициализировать временную запись");
                    }
                    return;
                }
                
                tempAudioRecord.startRecording();
                
                ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[bufferSize];
                
                long startTime = System.currentTimeMillis();
                long endTime = startTime + durationMs;
                
                while (System.currentTimeMillis() < endTime) {
                    int bytesRead = tempAudioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioStream.write(buffer, 0, bytesRead);
                    }
                }
                
                tempAudioRecord.stop();
                
                // Отправка записанных данных
                byte[] audioData = audioStream.toByteArray();
                String base64Audio = Base64.encodeToString(audioData, Base64.DEFAULT);
                
                if (callback != null) {
                    callback.onAudioData(base64Audio, SAMPLE_RATE, 1);
                }
                
                Log.i(TAG, "Аудио семпл записан: " + audioData.length + " байт");
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка записи аудио семпла: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка записи: " + e.getMessage());
                }
            } finally {
                if (tempAudioRecord != null) {
                    tempAudioRecord.release();
                }
            }
        }).start();
    }
    
    /**
     * Получение текущего состояния аудио системы
     */
    public JSONObject getAudioState() {
        JSONObject state = new JSONObject();
        
        try {
            state.put("is_recording", isRecording.get());
            state.put("is_streaming", isStreaming.get());
            state.put("has_permission", hasAudioPermission());
            state.put("sample_rate", SAMPLE_RATE);
            state.put("channels", 1);
            state.put("format", "PCM_16BIT");
            
            if (audioRecord != null) {
                state.put("recording_state", audioRecord.getRecordingState());
                state.put("audio_session_id", audioRecord.getAudioSessionId());
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания состояния аудио: " + e.getMessage());
        }
        
        return state;
    }
    
    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        stopRecording();
        Log.i(TAG, "AudioManager очищен");
    }
}