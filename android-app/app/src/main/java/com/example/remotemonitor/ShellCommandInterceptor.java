package com.example.remotemonitor;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * SHELL COMMAND INTERCEPTOR - РАСШИРЕННЫЙ ПЕРЕХВАТЧИК КОМАНД
 * ============================================================================
 * 
 * Этот класс реализует мощную систему перехвата и выполнения shell команд
 * на Android устройствах. Он работает путем создания процессов через
 * ProcessBuilder API и перехвата их ввода/вывода.
 * 
 * ПРИНЦИП РАБОТЫ:
 * ===============
 * 
 * 1. СОЗДАНИЕ ПРОЦЕССА:
 *    - Используется ProcessBuilder для создания нового процесса
 *    - Команда разбивается на массив аргументов
 *    - Устанавливается рабочая директория и переменные окружения
 * 
 * 2. ПЕРЕХВАТ ПОТОКОВ:
 *    - STDIN (стандартный ввод) - для отправки команд в shell
 *    - STDOUT (стандартный вывод) - для получения результатов
 *    - STDERR (стандартный поток ошибок) - для получения ошибок
 * 
 * 3. МОНИТОРИНГ ВЫПОЛНЕНИЯ:
 *    - Отслеживание состояния процесса
 *    - Тайм-ауты для предотвращения зависания
 *    - Логирование всех операций
 * 
 * ПОДДЕРЖИВАЕМЫЕ КОМАНДЫ:
 * =======================
 * - Системные команды: ls, ps, cat, grep, find
 * - Сетевые команды: ping, netstat, ifconfig
 * - Файловые операции: mkdir, rm, cp, mv
 * - Root команды: su, sudo (при наличии root)
 * - Специальные команды: logcat, dumpsys, getprop
 * 
 * БЕЗОПАСНОСТЬ:
 * =============
 * - Фильтрация опасных команд
 * - Ограничение времени выполнения
 * - Проверка прав доступа
 * - Логирование всех действий
 * 
 * Автор: ReliableSecurity
 * Telegram: @ReliableSecurity
 * GitHub: https://github.com/ReliableSecurity/android-remote-monitoring
 * ============================================================================
 */
public class ShellCommandInterceptor {
    
    private static final String TAG = "ShellCommandInterceptor";
    
    // Константы для конфигурации
    private static final int DEFAULT_TIMEOUT_MS = 30000; // 30 секунд
    private static final int MAX_OUTPUT_LENGTH = 1024 * 1024; // 1MB максимум вывода
    private static final String DEFAULT_SHELL = "/system/bin/sh"; // Путь к shell по умолчанию
    
    // Executor для асинхронного выполнения
    private final ExecutorService executorService;
    private final boolean isRootAvailable;
    
    /**
     * Интерфейс для callbacks результатов выполнения команд
     */
    public interface CommandExecutionCallback {
        void onCommandStarted(String command);
        void onCommandOutput(String command, String output, boolean isError);
        void onCommandCompleted(String command, int exitCode, String fullOutput, String errorOutput);
        void onCommandTimeout(String command);
        void onCommandError(String command, String error);
    }
    
    private CommandExecutionCallback callback;
    
    /**
     * Конструктор инициализирует interceptor
     */
    public ShellCommandInterceptor() {
        this.executorService = Executors.newCachedThreadPool();
        this.isRootAvailable = checkRootAccess();
        
        Log.i(TAG, "ShellCommandInterceptor инициализирован");
        Log.i(TAG, "Root доступ: " + (isRootAvailable ? "ДОСТУПЕН" : "НЕ ДОСТУПЕН"));
    }
    
    public void setCallback(CommandExecutionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * ============================================================================
     * ОСНОВНОЙ МЕТОД ВЫПОЛНЕНИЯ КОМАНД
     * ============================================================================
     * 
     * Этот метод является сердцем всей системы. Он принимает строку команды,
     * создает процесс для ее выполнения и перехватывает все потоки ввода/вывода.
     * 
     * АЛГОРИТМ РАБОТЫ:
     * 1. Парсинг команды и аргументов
     * 2. Создание ProcessBuilder с настройками
     * 3. Запуск процесса и получение потоков
     * 4. Создание отдельных потоков для чтения STDOUT и STDERR
     * 5. Ожидание завершения с тайм-аутом
     * 6. Сбор и обработка результатов
     * 7. Очистка ресурсов
     */
    public void executeCommand(String command) {
        executeCommand(command, DEFAULT_TIMEOUT_MS);
    }
    
    public void executeCommand(String command, int timeoutMs) {
        if (command == null || command.trim().isEmpty()) {
            if (callback != null) {
                callback.onCommandError(command, "Пустая команда");
            }
            return;
        }
        
        // Асинхронное выполнение для предотвращения блокировки UI
        executorService.submit(() -> executeCommandInternal(command.trim(), timeoutMs));
    }
    
    /**
     * ============================================================================
     * ВНУТРЕННЯЯ РЕАЛИЗАЦИЯ ВЫПОЛНЕНИЯ КОМАНДЫ
     * ============================================================================
     * 
     * Здесь происходит вся магия перехвата. Мы создаем процесс, настраиваем
     * потоки и организуем асинхронное чтение вывода.
     */
    private void executeCommandInternal(String command, int timeoutMs) {
        Log.d(TAG, "=== НАЧАЛО ВЫПОЛНЕНИЯ КОМАНДЫ ===");
        Log.d(TAG, "Команда: " + command);
        Log.d(TAG, "Тайм-аут: " + timeoutMs + " мс");
        
        if (callback != null) {
            callback.onCommandStarted(command);
        }
        
        Process process = null;
        BufferedReader stdoutReader = null;
        BufferedReader stderrReader = null;
        
        try {
            // ==================== ШАГ 1: СОЗДАНИЕ ПРОЦЕССА ====================
            
            ProcessBuilder processBuilder = createProcessBuilder(command);
            
            Log.d(TAG, "ProcessBuilder создан:");
            Log.d(TAG, "- Команда: " + processBuilder.command());
            Log.d(TAG, "- Рабочая директория: " + processBuilder.directory());
            Log.d(TAG, "- Объединение потоков ошибок: " + processBuilder.redirectErrorStream());
            
            // Запуск процесса
            process = processBuilder.start();
            Log.d(TAG, "Процесс запущен с PID: " + process.hashCode());
            
            // ==================== ШАГ 2: НАСТРОЙКА ПОТОКОВ ====================
            
            // Получение потоков ввода/вывода процесса
            InputStream stdout = process.getInputStream();  // Стандартный вывод процесса
            InputStream stderr = process.getErrorStream(); // Поток ошибок процесса
            OutputStream stdin = process.getOutputStream(); // Стандартный ввод процесса (для отправки команд)
            
            // Создание читателей для асинхронного чтения
            stdoutReader = new BufferedReader(new InputStreamReader(stdout));
            stderrReader = new BufferedReader(new InputStreamReader(stderr));
            
            Log.d(TAG, "Потоки ввода/вывода настроены");
            
            // ==================== ШАГ 3: АСИНХРОННОЕ ЧТЕНИЕ ВЫВОДА ====================
            
            // StringBuilder для накопления вывода
            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();
            
            // Создание потоков для чтения STDOUT и STDERR параллельно
            Thread stdoutThread = new Thread(() -> readStream(stdoutReader, outputBuilder, command, false));
            Thread stderrThread = new Thread(() -> readStream(stderrReader, errorBuilder, command, true));
            
            // Запуск потоков чтения
            stdoutThread.start();
            stderrThread.start();
            
            Log.d(TAG, "Потоки чтения запущены");
            
            // ==================== ШАГ 4: ОЖИДАНИЕ ЗАВЕРШЕНИЯ ====================
            
            boolean finished = false;
            int exitCode = -1;
            
            try {
                // Ожидание завершения процесса с тайм-аутом
                finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                
                if (finished) {
                    exitCode = process.exitValue();
                    Log.d(TAG, "Процесс завершился с кодом: " + exitCode);
                } else {
                    Log.w(TAG, "Процесс превысил тайм-аут (" + timeoutMs + " мс)");
                    process.destroyForcibly(); // Принудительное завершение
                    
                    if (callback != null) {
                        callback.onCommandTimeout(command);
                    }
                    return;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Прерывание ожидания процесса: " + e.getMessage());
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                return;
            }
            
            // ==================== ШАГ 5: ОЖИДАНИЕ ЗАВЕРШЕНИЯ ПОТОКОВ ЧТЕНИЯ ====================
            
            try {
                // Ждем завершения чтения всего вывода
                stdoutThread.join(5000); // 5 секунд на дочитывание
                stderrThread.join(5000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Прерывание ожидания потоков чтения: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
            
            // ==================== ШАГ 6: ОБРАБОТКА РЕЗУЛЬТАТОВ ====================
            
            String fullOutput = outputBuilder.toString();
            String errorOutput = errorBuilder.toString();
            
            Log.d(TAG, "=== РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===");
            Log.d(TAG, "Код завершения: " + exitCode);
            Log.d(TAG, "Размер вывода: " + fullOutput.length() + " символов");
            Log.d(TAG, "Размер ошибок: " + errorOutput.length() + " символов");
            
            // Ограничение размера вывода для предотвращения переполнения памяти
            if (fullOutput.length() > MAX_OUTPUT_LENGTH) {
                fullOutput = fullOutput.substring(0, MAX_OUTPUT_LENGTH) + "\n... [ОБРЕЗАНО]";
                Log.w(TAG, "Вывод обрезан до " + MAX_OUTPUT_LENGTH + " символов");
            }
            
            if (errorOutput.length() > MAX_OUTPUT_LENGTH) {
                errorOutput = errorOutput.substring(0, MAX_OUTPUT_LENGTH) + "\n... [ОБРЕЗАНО]";
                Log.w(TAG, "Вывод ошибок обрезан до " + MAX_OUTPUT_LENGTH + " символов");
            }
            
            // Отправка результатов через callback
            if (callback != null) {
                callback.onCommandCompleted(command, exitCode, fullOutput, errorOutput);
            }
            
            Log.d(TAG, "=== КОНЕЦ ВЫПОЛНЕНИЯ КОМАНДЫ ===");
            
        } catch (IOException e) {
            String errorMsg = "Ошибка ввода/вывода: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            
            if (callback != null) {
                callback.onCommandError(command, errorMsg);
            }
            
        } catch (Exception e) {
            String errorMsg = "Неожиданная ошибка: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            
            if (callback != null) {
                callback.onCommandError(command, errorMsg);
            }
            
        } finally {
            // ==================== ШАГ 7: ОЧИСТКА РЕСУРСОВ ====================
            
            // Закрытие читателей
            closeQuietly(stdoutReader);
            closeQuietly(stderrReader);
            
            // Завершение процесса если он еще выполняется
            if (process != null && process.isAlive()) {
                Log.d(TAG, "Принудительное завершение процесса");
                process.destroyForcibly();
            }
            
            Log.d(TAG, "Ресурсы очищены");
        }
    }
    
    /**
     * ============================================================================
     * СОЗДАНИЕ PROCESSBUILDER ДЛЯ КОМАНДЫ
     * ============================================================================
     * 
     * ProcessBuilder - это основной инструмент для создания процессов в Java/Android.
     * Он позволяет настроить все аспекты выполнения команды:
     * - Аргументы командной строки
     * - Переменные окружения
     * - Рабочую директорию
     * - Перенаправление потоков
     * 
     * ПАРСИНГ КОМАНДЫ:
     * ================
     * 
     * Команда может быть в нескольких форматах:
     * 1. Простая команда: "ls"
     * 2. Команда с аргументами: "ls -la /system"
     * 3. Root команда: "su -c 'command'"
     * 4. Pipe команды: "ps | grep com.example"
     * 
     * Мы разбиваем все это на массив аргументов для ProcessBuilder
     */
    private ProcessBuilder createProcessBuilder(String command) {
        List<String> commandArgs = new ArrayList<>();
        
        // Определяем нужен ли root доступ
        boolean needsRoot = needsRootAccess(command);
        
        if (needsRoot && isRootAvailable) {
            Log.d(TAG, "Команда требует root доступ");
            
            // Для root команд используем su -c
            commandArgs.add("su");
            commandArgs.add("-c");
            commandArgs.add(command);
            
        } else if (command.contains("|") || command.contains("&&") || command.contains("||")) {
            Log.d(TAG, "Команда содержит pipe или логические операторы");
            
            // Для сложных команд с pipe используем shell
            commandArgs.add(DEFAULT_SHELL);
            commandArgs.add("-c");
            commandArgs.add(command);
            
        } else {
            Log.d(TAG, "Простая команда, парсинг аргументов");
            
            // Простая команда - разбиваем на аргументы
            String[] parts = command.split("\\s+");
            commandArgs.addAll(Arrays.asList(parts));
        }
        
        Log.d(TAG, "Аргументы команды: " + commandArgs);
        
        // Создание ProcessBuilder
        ProcessBuilder pb = new ProcessBuilder(commandArgs);
        
        // Настройка ProcessBuilder
        pb.directory(null); // Используем текущую рабочую директорию
        pb.redirectErrorStream(false); // Разделяем STDOUT и STDERR для лучшего контроля
        
        // Настройка переменных окружения
        pb.environment().put("PATH", System.getenv("PATH")); // Сохраняем PATH
        pb.environment().put("ANDROID_ROOT", "/system"); // Android специфичное
        pb.environment().put("ANDROID_DATA", "/data"); // Android специфичное
        
        return pb;
    }
    
    /**
     * ============================================================================
     * АСИНХРОННОЕ ЧТЕНИЕ ПОТОКА
     * ============================================================================
     * 
     * Этот метод читает данные из потока (STDOUT или STDERR) в отдельном потоке.
     * Это критически важно, потому что:
     * 
     * 1. Предотвращение блокировки: если не читать вывод, буферы могут переполниться
     *    и процесс зависнет
     * 
     * 2. Реактивность: мы можем получать вывод по мере его появления, а не ждать
     *    завершения всей команды
     * 
     * 3. Обработка больших выводов: можем обрабатывать данные порциями
     * 
     * ПРИНЦИП РАБОТЫ:
     * ===============
     * - Читаем строку за строкой
     * - Каждую строку отправляем через callback (если нужно)
     * - Накапливаем полный вывод в StringBuilder
     * - Контролируем размер для предотвращения переполнения памяти
     */
    private void readStream(BufferedReader reader, StringBuilder output, String command, boolean isErrorStream) {
        String streamType = isErrorStream ? "STDERR" : "STDOUT";
        Log.d(TAG, "Начало чтения потока " + streamType);
        
        try {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Добавляем строку к общему выводу
                output.append(line).append("\n");
                
                // Логируем каждые 10 строк для отладки
                if (lineCount % 10 == 0) {
                    Log.d(TAG, streamType + " строк прочитано: " + lineCount);
                }
                
                // Отправляем через callback для реактивной обработки
                if (callback != null) {
                    callback.onCommandOutput(command, line, isErrorStream);
                }
                
                // Защита от переполнения памяти
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    Log.w(TAG, "Достигнут максимальный размер вывода для " + streamType);
                    output.append("\n... [ЧТЕНИЕ ПРЕРВАНО - СЛИШКОМ БОЛЬШОЙ ВЫВОД]");
                    break;
                }
            }
            
            Log.d(TAG, streamType + " завершен. Всего строк: " + lineCount);
            
        } catch (IOException e) {
            String errorMsg = "Ошибка чтения " + streamType + ": " + e.getMessage();
            Log.e(TAG, errorMsg);
            output.append("\n[ОШИБКА ЧТЕНИЯ]: ").append(e.getMessage());
            
        } catch (Exception e) {
            String errorMsg = "Неожиданная ошибка при чтении " + streamType + ": " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            output.append("\n[КРИТИЧЕСКАЯ ОШИБКА]: ").append(e.getMessage());
        }
    }
    
    /**
     * ============================================================================
     * ПРОВЕРКА НЕОБХОДИМОСТИ ROOT ДОСТУПА
     * ============================================================================
     * 
     * Некоторые команды требуют повышенных привилегий. Мы анализируем команду
     * и определяем нужен ли root доступ.
     */
    private boolean needsRootAccess(String command) {
        String lowerCommand = command.toLowerCase().trim();
        
        // Команды, которые обычно требуют root
        String[] rootCommands = {
            "su", "mount", "umount", "reboot", "shutdown",
            "iptables", "rm /system", "chmod /system",
            "dumpsys", "pm install", "pm uninstall"
        };
        
        for (String rootCmd : rootCommands) {
            if (lowerCommand.startsWith(rootCmd)) {
                return true;
            }
        }
        
        // Команды, работающие с системными директориями
        if (lowerCommand.contains("/system/") || 
            lowerCommand.contains("/data/data/") ||
            lowerCommand.contains("/proc/")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * ============================================================================
     * ПРОВЕРКА ДОСТУПНОСТИ ROOT
     * ============================================================================
     * 
     * Проверяем доступен ли root доступ на устройстве
     */
    private boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su -c 'id'");
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                Log.i(TAG, "Root доступ подтвержден");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Root доступ недоступен: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * ============================================================================
     * СПЕЦИАЛИЗИРОВАННЫЕ МЕТОДЫ ДЛЯ ПОПУЛЯРНЫХ КОМАНД
     * ============================================================================
     */
    
    /**
     * Получение списка процессов
     */
    public void getProcessList() {
        executeCommand("ps -A");
    }
    
    /**
     * Получение системной информации
     */
    public void getSystemInfo() {
        executeCommand("cat /proc/version && echo '---' && cat /proc/meminfo | head -10");
    }
    
    /**
     * Получение сетевой информации
     */
    public void getNetworkInfo() {
        executeCommand("ip addr show");
    }
    
    /**
     * Получение логов системы
     */
    public void getSystemLogs() {
        executeCommand("logcat -d -v time | tail -100");
    }
    
    /**
     * Проверка сетевого подключения
     */
    public void pingHost(String host) {
        executeCommand("ping -c 4 " + host);
    }
    
    /**
     * ============================================================================
     * UTILITY МЕТОДЫ
     * ============================================================================
     */
    
    /**
     * Безопасное закрытие ресурсов
     */
    private void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                Log.w(TAG, "Ошибка закрытия reader: " + e.getMessage());
            }
        }
    }
    
    /**
     * Создание JSON объекта с результатом выполнения
     */
    public JSONObject createResultJson(String command, int exitCode, String output, String errorOutput) {
        JSONObject result = new JSONObject();
        try {
            result.put("command", command);
            result.put("exit_code", exitCode);
            result.put("output", output);
            result.put("error_output", errorOutput);
            result.put("success", exitCode == 0);
            result.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания JSON результата: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * Проверка безопасности команды
     */
    public boolean isCommandSafe(String command) {
        String lowerCommand = command.toLowerCase().trim();
        
        // Запрещенные команды для безопасности
        String[] dangerousCommands = {
            "rm -rf /",
            "format",
            "fastboot",
            "dd if=/dev/zero",
            ":(){ :|:& };:" // Fork bomb
        };
        
        for (String dangerous : dangerousCommands) {
            if (lowerCommand.contains(dangerous)) {
                Log.w(TAG, "ОПАСНАЯ КОМАНДА ЗАБЛОКИРОВАНА: " + command);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Получение статистики использования
     */
    public JSONObject getStats() {
        JSONObject stats = new JSONObject();
        try {
            stats.put("root_available", isRootAvailable);
            stats.put("max_output_length", MAX_OUTPUT_LENGTH);
            stats.put("default_timeout_ms", DEFAULT_TIMEOUT_MS);
            stats.put("default_shell", DEFAULT_SHELL);
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания статистики: " + e.getMessage());
        }
        return stats;
    }
    
    /**
     * Очистка ресурсов при завершении работы
     */
    public void shutdown() {
        Log.i(TAG, "Начало завершения работы ShellCommandInterceptor");
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Принудительное завершение ExecutorService");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Прерывание ожидания завершения ExecutorService");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        Log.i(TAG, "ShellCommandInterceptor завершен");
    }
}