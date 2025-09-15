# 🏗️ Архитектура системы

## 📊 Общая архитектура

```
┌─────────────────┐    TCP/IP    ┌─────────────────┐
│   Python Server │◄────────────►│  Android Client │
│                 │   Port 8443  │                 │
│  - Commands     │              │  - Monitoring   │
│  - Data Display │              │  - Data Collect │
│  - Auth         │              │  - Permissions  │
│  - Logging      │              │  - UI/Consent   │
└─────────────────┘              └─────────────────┘
```

## 🐍 Серверная часть (Python)

### Основные компоненты:
1. **MonitoringServer** - главный класс сервера
2. **Socket Handler** - обработка TCP соединений
3. **Authentication** - проверка подлинности клиентов
4. **Command Processor** - обработка команд и результатов
5. **Logger** - логирование всех операций

### Архитектурные решения:

#### Многопоточность
```python
# Каждый клиент обрабатывается в отдельном потоке
client_thread = threading.Thread(
    target=self.handle_client,
    args=(client_socket, address)
)
```

#### Аутентификация
```python
# Challenge-Response аутентификация
challenge = hashlib.sha256((server_key + timestamp).encode()).hexdigest()
expected = hashlib.sha256((challenge + server_key).encode()).hexdigest()
```

#### Протокол обмена данными
- Формат: JSON over TCP
- Структура сообщений: `{"type": "...", "data": {...}}`
- Команды: info, battery, location, photo, network, storage, apps

## 📱 Android приложение

### Архитектура приложения:

```
MainActivity
├── ConsentActivity (согласие пользователя)
├── MonitoringService (фоновый сервис)
├── PermissionManager (управление разрешениями)
└── NetworkClient (сетевое взаимодействие)
```

### Основные компоненты:

#### 1. MainActivity
- Пользовательский интерфейс
- Настройки подключения
- Управление сервисом
- Проверка разрешений

#### 2. ConsentActivity  
- Отображение соглашения
- Получение явного согласия
- Проверка всех пунктов
- Сохранение статуса согласия

#### 3. MonitoringService
- Фоновая работа
- Подключение к серверу
- Выполнение команд
- Сбор данных устройства

#### 4. Сбор данных устройства
```java
// Системная информация
Build.MODEL, Build.MANUFACTURER, Build.VERSION.RELEASE

// Батарея
BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)

// Местоположение  
LocationManager.getLastKnownLocation()

// Сеть
ConnectivityManager.getNetworkInfo()

// Хранилище
StatFs.getAvailableBlocksLong()

// Приложения
PackageManager.getInstalledApplications()
```

## 🔄 Протокол взаимодействия

### Последовательность подключения:

1. **Установка соединения**
   ```
   Client → Server: TCP connect
   ```

2. **Аутентификация**
   ```
   Server → Client: {"type": "auth_challenge", "challenge": "...", "timestamp": "..."}
   Client → Server: {"response": "sha256_hash"}
   Server → Client: {"type": "auth_success"} или {"type": "auth_error"}
   ```

3. **Командный цикл**
   ```
   Server → Client: {"type": "command_menu", "commands": [...]}
   Client → Server: {"command": "info"}
   Server → Client: {"type": "execute_command", "command": "info"}
   Client → Server: {"status": "success", "data": {...}}
   ```

### Формат команд:

#### Запрос команды:
```json
{
  "type": "execute_command",
  "command": "info",
  "timestamp": 1642248645
}
```

#### Ответ с данными:
```json
{
  "status": "success",
  "timestamp": 1642248645,
  "data": {
    "device_model": "SM-G973F",
    "manufacturer": "samsung",
    "android_version": "11"
  }
}
```

## 🔐 Безопасность

### Уровни защиты:

1. **Аутентификация**
   - Challenge-Response протокол
   - SHA-256 хеширование
   - Временные метки

2. **Согласие пользователя**
   - Явное согласие на каждую функцию
   - Подробное описание действий
   - Возможность отзыва согласия

3. **Разрешения Android**
   - Runtime permissions
   - Минимальные необходимые разрешения
   - Проверка перед каждым действием

4. **Логирование**
   - Все операции записываются
   - Временные метки
   - Идентификация клиентов

### Ограничения безопасности:

⚠️ **Важные ограничения:**
- Нет шифрования трафика (данные передаются открытым текстом)
- Простая аутентификация (не устойчива к атакам повторного воспроизведения)
- Отсутствует авторизация (все аутентифицированные клиенты имеют полный доступ)
- Нет защиты от DoS атак

## 📊 Потоки данных

### Сбор системной информации:
```
Android APIs → MonitoringService → JSON → Socket → Python Server → Console
```

### Обработка команд:
```
Server Console → Command → Socket → MonitoringService → Android APIs → Response
```

### Логирование:
```
All Operations → Logger → File (monitoring_server.log) → Analysis
```

## 🔧 Конфигурация

### Настройки сервера:
```python
DEFAULT_HOST = "0.0.0.0"        # Все интерфейсы
DEFAULT_PORT = 8443             # Порт сервера  
SERVER_KEY = "educational_..."   # Ключ аутентификации
LOG_LEVEL = logging.INFO        # Уровень логирования
```

### Настройки Android:
```java
MIN_SDK_VERSION = 24            // Android 7.0+
TARGET_SDK_VERSION = 33         // Android 13
PERMISSIONS = {                 // Необходимые разрешения
    CAMERA, ACCESS_FINE_LOCATION, 
    WRITE_EXTERNAL_STORAGE, ...
}
```

## 🚀 Масштабируемость

### Текущие ограничения:
- Обработка клиентов: ~10-50 одновременно (зависит от ресурсов)
- Пропускная способность: ограничена сетью
- Хранение: логи накапливаются без ротации

### Возможности улучшения:
1. **Асинхронное программирование** (asyncio для Python)
2. **База данных** для хранения данных и логов
3. **Load balancer** для распределения нагрузки
4. **Кеширование** результатов команд
5. **REST API** для веб интерфейса

## 📈 Мониторинг и метрики

### Доступные метрики:
- Количество подключенных клиентов
- Время выполнения команд
- Частота ошибок
- Объем переданных данных

### Логируемые события:
- Подключения/отключения
- Успешная/неудачная аутентификация
- Выполнение команд
- Ошибки и исключения

## 🔄 Жизненный цикл

### Сервер:
1. Запуск → Инициализация → Привязка к порту
2. Ожидание подключений → Создание потоков
3. Обработка клиентов → Аутентификация → Команды
4. Завершение → Закрытие соединений → Очистка

### Android приложение:
1. Запуск → Проверка согласия → UI
2. Подключение → Аутентификация → Сервис
3. Фоновая работа → Выполнение команд
4. Отключение → Остановка сервиса → Очистка

Эта архитектура обеспечивает баланс между функциональностью, безопасностью и образовательной ценностью проекта.