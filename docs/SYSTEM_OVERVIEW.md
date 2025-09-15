# 🔒 Android Remote Monitoring System - Полное руководство

## 📋 Обзор системы

**Android Remote Monitoring System** — это образовательный проект для изучения технологий мониторинга Android устройств. Система включает в себя мощные компоненты для выполнения shell команд, управления сетевыми подключениями и автоматизированной сборки APK файлов.

---

## 🎯 Основные компоненты

### 1. **ShellCommandInterceptor** - Система выполнения shell команд
- ✅ Выполнение любых shell команд через ProcessBuilder
- ✅ Асинхронное чтение STDOUT/STDERR потоков
- ✅ Поддержка root команд и pipe операций
- ✅ Безопасная фильтрация опасных команд
- ✅ Контроль timeout и размера вывода

### 2. **NetworkConfig** - Централизованное управление сетью
- ✅ Поддержка конфигурации времени сборки (BuildConfig)
- ✅ Runtime настройки через SharedPreferences
- ✅ Валидация IP адресов и портов
- ✅ Автоматическое переключение протоколов

### 3. **APK Builder Script** - Автоматизированная сборка
- ✅ Интерактивная настройка IP и порта
- ✅ Автоматическое обновление BuildConfig
- ✅ Поддержка debug и release сборок
- ✅ Генерация информационных файлов

---

## 🛠️ Как работает перехват shell команд

### Принцип работы ProcessBuilder:

```mermaid
graph TD
    A[Команда] --> B[Парсинг аргументов]
    B --> C[ProcessBuilder.start()]
    C --> D[Новый процесс]
    D --> E[STDIN/STDOUT/STDERR]
    E --> F[Асинхронное чтение]
    F --> G[Результат в JSON]
```

### Пример выполнения команды:

```java
// 1. Создание interceptor
ShellCommandInterceptor interceptor = new ShellCommandInterceptor();

// 2. Настройка callback
interceptor.setCallback(new CommandExecutionCallback() {
    @Override
    public void onCommandCompleted(String command, int exitCode, 
                                  String output, String errorOutput) {
        Log.i(TAG, "Результат: " + output);
    }
});

// 3. Выполнение команды
interceptor.executeCommand("ps | grep android");
```

### Что происходит внутри:

1. **Парсинг команды**: `"ps | grep android"` → `["/system/bin/sh", "-c", "ps | grep android"]`
2. **Создание процесса**: `ProcessBuilder` запускает shell с командой
3. **Перехват потоков**: Создаются отдельные потоки для чтения STDOUT и STDERR
4. **Асинхронное чтение**: Данные читаются построчно без блокировки
5. **Timeout контроль**: Процесс принудительно завершается при превышении времени
6. **Результат**: Возвращается JSON с выводом, ошибками и кодом завершения

---

## 🔧 Сборка APK с настройками

### Использование скрипта build_apk.sh:

```bash
# Интерактивный режим
./scripts/build_apk.sh

# С параметрами
./scripts/build_apk.sh 192.168.1.50 9999 release
```

### Что делает скрипт:

1. **Проверка окружения**: Gradle, Android SDK
2. **Сбор параметров**: IP, порт, тип сборки
3. **Обновление конфигурации**: Изменение BuildConfig в build.gradle
4. **Сборка APK**: Выполнение gradle assembleDebug/Release
5. **Создание артефактов**: APK файл + информационный файл

### Структура BuildConfig:

```java
public final class BuildConfig {
    public static final String SERVER_IP = "192.168.1.50";  // Из build_apk.sh
    public static final int SERVER_PORT = 9999;             // Из build_apk.sh  
    public static final String BUILD_TIMESTAMP = "2024-01-15 15:30:45";
    public static final boolean IS_DEBUG_BUILD = false;
}
```

---

## 📡 Работа с сетевыми настройками

### Приоритет конфигурации:
1. **Runtime настройки** (SharedPreferences) - высший приоритет
2. **Build-time настройки** (BuildConfig) - средний приоритет  
3. **Значения по умолчанию** - низший приоритет

### Пример использования NetworkConfig:

```java
// Инициализация
NetworkConfig networkConfig = new NetworkConfig(context);

// Получение настроек
String serverIP = networkConfig.getServerIP();      // "192.168.1.50"
int serverPort = networkConfig.getServerPort();     // 9999
String serverURL = networkConfig.getServerURL();    // "https://192.168.1.50:9999"

// Runtime изменения
networkConfig.setServerIP("10.0.0.1");
networkConfig.setServerPort(8080);

// Проверка доступности
boolean reachable = networkConfig.isServerReachable();
```

---

## 🔒 Безопасность и фильтрация

### Опасные команды блокируются:

```java
String[] dangerousCommands = {
    "rm -rf /",        // Удаление файловой системы
    "format",          // Форматирование
    "dd if=/dev/zero", // Затирание данных
    ":(){ :|:& };:"    // Fork bomb
};
```

### Ограничения выполнения:
- **Timeout**: 30 секунд по умолчанию
- **Memory limit**: 1MB максимум вывода
- **Access control**: Проверка root прав
- **Audit logging**: Запись всех операций

---

## 📊 Примеры команд и их результатов

### 1. Системная информация:
```bash
Команда: "cat /proc/version"
Результат: "Linux version 5.4.0-android12..."
```

### 2. Список процессов:
```bash  
Команда: "ps -A | head -10"
Результат:
PID   PPID  PGID  SID  TTY  TPGID  STAT  CMD
1     0     1     1    ?    -1     S     init
2     0     0     0    ?    -1     S     [kthreadd]
...
```

### 3. Сетевая информация:
```bash
Команда: "ip addr show | grep inet"
Результат:
inet 127.0.0.1/8 scope host lo
inet 192.168.1.45/24 brd 192.168.1.255 scope global wlan0
...
```

### 4. Root команды:
```bash
Команда: "su -c 'cat /data/system/packages.xml | head -5'"
Результат: (требует root доступ)
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<packages>
...
```

---

## ⚡ Производительность и оптимизация

### Рекомендации по командам:

```bash
# ❌ Плохо - много вывода
cat /proc/meminfo

# ✅ Хорошо - ограниченный вывод  
cat /proc/meminfo | head -10

# ❌ Плохо - долгое выполнение
find / -name "*.apk"

# ✅ Хорошо - ограниченный поиск
find /system -maxdepth 2 -name "*.apk"
```

### Мониторинг ресурсов:
- Каждая команда создает новый процесс
- Процессы выполняются асинхронно  
- ExecutorService управляет потоками
- Обязательно вызывать `shutdown()` при завершении

---

## 🐛 Диагностика и отладка

### Логирование в Android Studio:

```bash
# Фильтр логов приложения
adb logcat | grep "RemoteMonitor\|ShellCommand\|NetworkConfig"

# Детальные логи выполнения команд
adb logcat -v time | grep "ShellCommandInterceptor"
```

### Типичные ошибки:

#### 1. Команда не найдена
```
STDERR: /system/bin/sh: invalid_command: not found
Решение: Проверить правильность написания команды
```

#### 2. Нет прав доступа  
```
STDERR: Permission denied
Решение: Использовать root права или изменить права файла
```

#### 3. Timeout команды
```
LOG: Command timeout after 30000ms  
Решение: Увеличить timeout или оптимизировать команду
```

### Debug через код:

```java
// Получение статистики
JSONObject stats = interceptor.getStats();
Log.i(TAG, "Root доступ: " + stats.getBoolean("root_available"));

// Проверка безопасности команды
boolean safe = interceptor.isCommandSafe("rm -rf /");
Log.w(TAG, "Команда безопасна: " + safe);

// Информация о конфигурации
String configInfo = networkConfig.getConfigString();
Log.d(TAG, configInfo);
```

---

## 🚀 Развертывание и использование

### 1. Подготовка окружения:
```bash
# Установка Android SDK
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Проверка Gradle
gradle --version
```

### 2. Сборка APK:
```bash
cd /home/mans/remote-monitoring-system
./scripts/build_apk.sh 192.168.1.100 8080 release
```

### 3. Установка на устройство:
```bash
# Через ADB
adb install builds/RemoteMonitoring-192_168_1_100-8080-release.apk

# Или скопировать APK на устройство и установить вручную
```

### 4. Проверка работы:
```bash
# Проверка установки
adb shell pm list packages | grep monitoring

# Просмотр логов
adb logcat | grep RemoteMonitor

# Проверка сетевого подключения
adb shell netstat -a | grep 8080
```

---

## ⚠️ Важные замечания

### Образовательное использование:
- ✅ Изучение технологий Android
- ✅ Понимание принципов мониторинга
- ✅ Исследование безопасности систем
- ❌ Несанкционированное использование
- ❌ Нарушение приватности  
- ❌ Незаконное проникновение

### Требования к устройству:
- Android 5.0+ (API level 21+)
- Разрешения: камера, микрофон, местоположение, хранилище
- Root доступ (опционально, для расширенных возможностей)
- Сетевое подключение

### Совместимость:
- ✅ Stock Android 5.0-14
- ✅ Samsung One UI
- ✅ Xiaomi MIUI  
- ✅ Custom ROM (LineageOS, etc.)
- ⚠️  Некоторые функции могут быть ограничены на разных прошивках

---

## 📚 Дополнительные ресурсы

### Документация:
- [SHELL_INTERCEPTION.md](SHELL_INTERCEPTION.md) - Подробное описание механизма перехвата
- [ARCHITECTURE.md](ARCHITECTURE.md) - Архитектура системы
- [DEPLOYMENT.md](DEPLOYMENT.md) - Инструкции по развертыванию

### Исходный код:
- **ShellCommandInterceptor.java** - Основной класс для выполнения команд
- **NetworkConfig.java** - Управление сетевыми настройками
- **build_apk.sh** - Скрипт сборки APK
- **deploy_to_github.sh** - Скрипт публикации на GitHub

### Полезные команды:
```bash
# Статистика проекта
find . -name "*.java" | xargs wc -l
git log --oneline | wc -l
du -sh android-app/

# Анализ APK
aapt dump badging builds/RemoteMonitoring-*.apk
apktool d builds/RemoteMonitoring-*.apk

# Мониторинг системы
adb shell top | head -20
adb shell dumpsys meminfo | head -20
```

---

## 👨‍💻 Контакты и поддержка

**Автор**: ReliableSecurity  
**GitHub**: https://github.com/ReliableSecurity/android-remote-monitoring  
**Telegram**: @ReliableSecurity

### Сообщество:
- 🐛 **Issues**: Сообщения об ошибках и предложения
- 💡 **Discussions**: Обсуждение функций и идей  
- 📖 **Wiki**: Дополнительная документация и примеры
- ⭐ **Stars**: Поддержите проект звездочкой!

---

## ⚖️ Лицензия и ответственность

**Образовательная лицензия** - проект предназначен исключительно для изучения технологий Android и понимания принципов работы систем мониторинга.

### Использование разрешено:
- ✅ Изучение кода и архитектуры
- ✅ Образовательные цели и исследования  
- ✅ Тестирование на собственных устройствах
- ✅ Модификация для учебных проектов

### Использование запрещено:
- ❌ Несанкционированное проникновение
- ❌ Нарушение приватности третьих лиц
- ❌ Коммерческое использование без разрешения
- ❌ Любая незаконная деятельность

**Помните**: Всегда соблюдайте законы о приватности и получайте согласие владельцев устройств!

---

*Этот проект создан с целью образования и повышения осведомленности о безопасности Android устройств. Используйте знания ответственно!*