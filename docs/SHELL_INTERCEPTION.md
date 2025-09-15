# МЕХАНИЗМ ПЕРЕХВАТА SHELL КОМАНД

## 📋 Содержание
1. [Введение](#введение)
2. [Принцип работы](#принцип-работы)
3. [Техническая реализация](#техническая-реализация)
4. [Процесс выполнения команды](#процесс-выполнения-команды)
5. [Обработка потоков ввода-вывода](#обработка-потоков-ввода-вывода)
6. [Root доступ и привилегии](#root-доступ-и-привилегии)
7. [Безопасность](#безопасность)
8. [Примеры использования](#примеры-использования)
9. [Диагностика и отладка](#диагностика-и-отладка)

---

## 🎯 Введение

**ShellCommandInterceptor** — это мощная система для выполнения и мониторинга shell команд на Android устройствах. Класс использует Java ProcessBuilder API для создания процессов и перехвата их потоков ввода-вывода.

### Основные возможности:
- ✅ Выполнение любых shell команд
- ✅ Перехват вывода в реальном времени
- ✅ Поддержка root команд
- ✅ Обработка pipe операций (`|`, `&&`, `||`)
- ✅ Контроль времени выполнения (timeouts)
- ✅ Безопасная фильтрация опасных команд
- ✅ Асинхронное выполнение

---

## ⚙️ Принцип работы

### 1. Архитектура системы

```
┌─────────────────────────────────────────────────────────────┐
│                  ShellCommandInterceptor                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐ │
│  │   Command   │───▶│ ProcessBuilder│───▶│   New Process   │ │
│  │   Parser    │    │              │    │                 │ │
│  └─────────────┘    └──────────────┘    └─────────────────┘ │
│                                                             │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐ │
│  │   STDOUT    │◀───│  Stream      │◀───│     Shell       │ │
│  │   Reader    │    │  Interceptor │    │     Process     │ │
│  └─────────────┘    └──────────────┘    └─────────────────┘ │
│                                                             │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐ │
│  │   STDERR    │◀───│  Async       │◀───│     Output      │ │
│  │   Reader    │    │  Reader      │    │     Streams     │ │
│  └─────────────┘    └──────────────┘    └─────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2. Жизненный цикл команды

```
Команда → Парсинг → Создание процесса → Перехват потоков → Выполнение → Результат
   │         │            │                │               │           │
   ▼         ▼            ▼                ▼               ▼           ▼
"ls -la" → ["ls","-la"] → ProcessBuilder → STDIN/STDOUT → Async Read → JSON Result
```

---

## 🔧 Техническая реализация

### 1. ProcessBuilder - основа системы

ProcessBuilder — это Java класс для создания процессов операционной системы:

```java
// Создание процесса
ProcessBuilder pb = new ProcessBuilder("ls", "-la", "/system");
pb.directory(new File("/"));  // Рабочая директория
pb.redirectErrorStream(false); // Раздельные потоки STDOUT/STDERR
Process process = pb.start(); // Запуск процесса
```

### 2. Потоки ввода-вывода процесса

Каждый процесс имеет три основных потока:

```java
// Получение потоков процесса
InputStream stdout = process.getInputStream();   // Вывод программы
InputStream stderr = process.getErrorStream();  // Ошибки программы  
OutputStream stdin = process.getOutputStream(); // Ввод в программу
```

### 3. Асинхронное чтение потоков

```java
// Создание читателей
BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));

// Запуск в отдельных потоках
Thread stdoutThread = new Thread(() -> readStream(stdoutReader, outputBuilder, false));
Thread stderrThread = new Thread(() -> readStream(stderrReader, errorBuilder, true));

stdoutThread.start();
stderrThread.start();
```

---

## 🔄 Процесс выполнения команды

### Шаг 1: Парсинг команды

```java
private ProcessBuilder createProcessBuilder(String command) {
    List<String> commandArgs = new ArrayList<>();
    
    if (needsRootAccess(command) && isRootAvailable) {
        // Root команда: su -c "command"
        commandArgs.add("su");
        commandArgs.add("-c");
        commandArgs.add(command);
    } else if (command.contains("|") || command.contains("&&")) {
        // Pipe команда: /system/bin/sh -c "command"
        commandArgs.add("/system/bin/sh");
        commandArgs.add("-c");
        commandArgs.add(command);
    } else {
        // Простая команда: ["ls", "-la", "/system"]
        String[] parts = command.split("\\s+");
        commandArgs.addAll(Arrays.asList(parts));
    }
    
    return new ProcessBuilder(commandArgs);
}
```

### Шаг 2: Создание и запуск процесса

```java
ProcessBuilder processBuilder = createProcessBuilder(command);

// Настройка окружения
processBuilder.environment().put("PATH", System.getenv("PATH"));
processBuilder.environment().put("ANDROID_ROOT", "/system");

// Запуск процесса
Process process = processBuilder.start();
```

### Шаг 3: Мониторинг выполнения

```java
// Ожидание завершения с timeout
boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

if (finished) {
    int exitCode = process.exitValue(); // Код завершения
    // Обработка результата
} else {
    process.destroyForcibly(); // Принудительное завершение
    // Обработка timeout
}
```

---

## 📡 Обработка потоков ввода-вывода

### Почему нужно асинхронное чтение?

**Проблема**: Если не читать потоки вывода, они могут переполниться и процесс зависнет.

**Решение**: Читаем STDOUT и STDERR в отдельных потоках параллельно.

### Реализация чтения потока:

```java
private void readStream(BufferedReader reader, StringBuilder output, 
                       String command, boolean isErrorStream) {
    try {
        String line;
        int lineCount = 0;
        
        while ((line = reader.readLine()) != null) {
            lineCount++;
            
            // Накапливаем полный вывод
            output.append(line).append("\n");
            
            // Отправляем строку через callback
            if (callback != null) {
                callback.onCommandOutput(command, line, isErrorStream);
            }
            
            // Защита от переполнения памяти
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output.append("\n... [ОБРЕЗАНО]");
                break;
            }
        }
    } catch (IOException e) {
        output.append("\n[ОШИБКА ЧТЕНИЯ]: ").append(e.getMessage());
    }
}
```

### Пример работы с потоками:

```
Команда: "ps | grep com.example"

ProcessBuilder создает: ["/system/bin/sh", "-c", "ps | grep com.example"]

Процесс запускается → Shell выполняет команду

STDOUT поток:
├─ "1234 u0_a123 com.example.app"
├─ "5678 u0_a123 com.example.service"  
└─ [EOF]

STDERR поток:
└─ [Пустой или ошибки]

Результат:
{
  "command": "ps | grep com.example",
  "exit_code": 0,
  "output": "1234 u0_a123 com.example.app\n5678 u0_a123 com.example.service\n",
  "error_output": "",
  "success": true
}
```

---

## 🔑 Root доступ и привилегии

### Проверка доступности root:

```java
private boolean checkRootAccess() {
    try {
        Process process = Runtime.getRuntime().exec("su -c 'id'");
        boolean finished = process.waitFor(3, TimeUnit.SECONDS);
        
        return finished && process.exitValue() == 0;
    } catch (Exception e) {
        return false;
    }
}
```

### Выполнение root команд:

```java
// Обычная команда
["ls", "-la"]

// Root команда  
["su", "-c", "ls -la /data/data"]
```

### Команды, требующие root:

- `su`, `mount`, `umount`
- `reboot`, `shutdown`
- `pm install`, `pm uninstall`
- Работа с `/system/`, `/data/data/`
- `iptables`, системные настройки

---

## 🔒 Безопасность

### 1. Фильтрация опасных команд:

```java
public boolean isCommandSafe(String command) {
    String[] dangerousCommands = {
        "rm -rf /",        // Удаление корневой ФС
        "format",          // Форматирование
        "dd if=/dev/zero", // Затирание данных
        ":(){ :|:& };:"    // Fork bomb
    };
    
    for (String dangerous : dangerousCommands) {
        if (command.toLowerCase().contains(dangerous)) {
            Log.w(TAG, "ОПАСНАЯ КОМАНДА ЗАБЛОКИРОВАНА: " + command);
            return false;
        }
    }
    return true;
}
```

### 2. Ограничения выполнения:

- **Timeout**: Максимальное время выполнения команды
- **Memory limit**: Ограничение размера вывода
- **Access control**: Проверка прав доступа

### 3. Аудит и логирование:

```java
Log.d(TAG, "=== НАЧАЛО ВЫПОЛНЕНИЯ КОМАНДЫ ===");
Log.d(TAG, "Команда: " + command);
Log.d(TAG, "Root доступ: " + (needsRoot ? "ДА" : "НЕТ"));
Log.d(TAG, "Код завершения: " + exitCode);
Log.d(TAG, "=== КОНЕЦ ВЫПОЛНЕНИЯ КОМАНДЫ ===");
```

---

## 💻 Примеры использования

### 1. Простая команда:

```java
ShellCommandInterceptor interceptor = new ShellCommandInterceptor();
interceptor.setCallback(new CommandExecutionCallback() {
    @Override
    public void onCommandCompleted(String command, int exitCode, 
                                  String output, String errorOutput) {
        Log.i(TAG, "Команда завершена: " + command);
        Log.i(TAG, "Результат: " + output);
    }
});

interceptor.executeCommand("ls -la /system/bin");
```

### 2. Root команда:

```java
interceptor.executeCommand("su -c 'cat /data/system/packages.xml'");
```

### 3. Pipe команда:

```java
interceptor.executeCommand("ps | grep com.android | wc -l");
```

### 4. Команда с timeout:

```java
interceptor.executeCommand("ping -c 10 google.com", 15000); // 15 секунд
```

### 5. Предустановленные команды:

```java
interceptor.getProcessList();        // ps -A
interceptor.getSystemInfo();         // cat /proc/version
interceptor.getNetworkInfo();        // ip addr show  
interceptor.pingHost("8.8.8.8");     // ping -c 4 8.8.8.8
```

---

## 🐛 Диагностика и отладка

### Логирование процесса выполнения:

```
D/ShellCommandInterceptor: === НАЧАЛО ВЫПОЛНЕНИЯ КОМАНДЫ ===
D/ShellCommandInterceptor: Команда: ls -la /system
D/ShellCommandInterceptor: Тайм-аут: 30000 мс
D/ShellCommandInterceptor: ProcessBuilder создан:
D/ShellCommandInterceptor: - Команда: [ls, -la, /system]
D/ShellCommandInterceptor: - Рабочая директория: null
D/ShellCommandInterceptor: Процесс запущен с PID: 12345678
D/ShellCommandInterceptor: Потоки ввода/вывода настроены
D/ShellCommandInterceptor: Потоки чтения запущены
D/ShellCommandInterceptor: Начало чтения потока STDOUT
D/ShellCommandInterceptor: Начало чтения потока STDERR
D/ShellCommandInterceptor: Процесс завершился с кодом: 0
D/ShellCommandInterceptor: STDOUT завершен. Всего строк: 25
D/ShellCommandInterceptor: STDERR завершен. Всего строк: 0
D/ShellCommandInterceptor: === РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===
D/ShellCommandInterceptor: Код завершения: 0
D/ShellCommandInterceptor: Размер вывода: 1543 символов
D/ShellCommandInterceptor: Размер ошибок: 0 символов
D/ShellCommandInterceptor: Ресурсы очищены
D/ShellCommandInterceptor: === КОНЕЦ ВЫПОЛНЕНИЯ КОМАНДЫ ===
```

### Типичные ошибки и решения:

#### 1. Команда не найдена

```
Ошибка: /system/bin/sh: invalid_command: not found
Решение: Проверить правильность написания команды
```

#### 2. Нет прав доступа

```
Ошибка: Permission denied
Решение: Использовать root права или изменить права файла
```

#### 3. Timeout команды

```
Ошибка: Command timeout after 30000ms
Решение: Увеличить timeout или оптимизировать команду
```

#### 4. Переполнение буфера

```
Предупреждение: Вывод обрезан до 1048576 символов
Решение: Использовать фильтрацию (head, tail, grep)
```

---

## 📊 Мониторинг производительности

### Статистика использования:

```java
JSONObject stats = interceptor.getStats();
// Результат:
{
  "root_available": true,
  "max_output_length": 1048576,
  "default_timeout_ms": 30000,
  "default_shell": "/system/bin/sh"
}
```

### Оптимизация команд:

```bash
# Плохо - много вывода
cat /proc/meminfo

# Хорошо - ограниченный вывод  
cat /proc/meminfo | head -10

# Плохо - долгое выполнение
find / -name "*.apk" 

# Хорошо - ограниченный поиск
find /system -maxdepth 2 -name "*.apk"
```

---

## 🔧 Настройка и конфигурация

### Константы конфигурации:

```java
private static final int DEFAULT_TIMEOUT_MS = 30000;      // 30 секунд
private static final int MAX_OUTPUT_LENGTH = 1024 * 1024; // 1MB
private static final String DEFAULT_SHELL = "/system/bin/sh";
```

### Переменные окружения:

```java
pb.environment().put("PATH", System.getenv("PATH"));
pb.environment().put("ANDROID_ROOT", "/system");
pb.environment().put("ANDROID_DATA", "/data");
```

---

## ⚠️ Важные замечания

### 1. Использование ресурсов
- Каждая команда создает новый процесс
- Процессы выполняются асинхронно
- Необходимо вызывать `shutdown()` для очистки

### 2. Безопасность
- Всегда проверяйте команды на безопасность
- Не выполняйте команды от непроверенных источников
- Логируйте все операции для аудита

### 3. Производительность
- Избегайте частого выполнения тяжелых команд
- Используйте фильтрацию вывода
- Устанавливайте разумные timeout'ы

---

**Автор**: ReliableSecurity  
**Telegram**: @ReliableSecurity  
**GitHub**: https://github.com/ReliableSecurity/android-remote-monitoring

**Помните**: Используйте только в образовательных целях и с согласия владельца устройства!