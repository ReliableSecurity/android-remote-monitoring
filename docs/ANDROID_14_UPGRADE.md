# 🚀 Обновление до Android 14 (API 34)

**⚡ Быстрая инструкция по обновлению проекта**

## 📋 Что изменилось

### ✅ Уже обновлено в проекте:

1. **Target SDK 34** - Android 14
2. **Gradle 8.2.0** - последняя версия  
3. **Material Design 3** - современный дизайн
4. **Новые разрешения** - POST_NOTIFICATIONS, READ_MEDIA_*
5. **Foreground Service** - стабильная фоновая работа
6. **Scoped Storage** - безопасная работа с файлами

## 🔧 Системные требования

### Разработка:
- **Android Studio Hedgehog (2023.1.1)+**
- **JDK 17** или новее
- **Android SDK 34** (автоматически установится)

### Проверка версий:
```bash
# Java версия
java -version
# Должна быть 17+

# Android Studio версия  
# Help → About Android Studio
# Должна быть 2023.1.1+

# Gradle Wrapper версия
cd android-app
./gradlew --version
# Должна быть 8.2+
```

## 📱 Новые функции для пользователей

### 🔔 Умные уведомления
- Приложение запросит разрешение на показ уведомлений
- Постоянное уведомление показывает статус подключения
- Можно отключить в настройках Android

### 🖼️ Гранулярные медиа-разрешения
- Отдельные разрешения для фото, видео, аудио
- Больше контроля над доступом к файлам
- Автоматическая миграция со старых разрешений

### 🎨 Material Design 3
- Динамические цвета под системную тему
- Автоматическая ночная тема  
- Современные компоненты UI

### 🔒 Улучшенная безопасность
- Scoped Storage - безопасная работа с файлами
- Конфигурируемые правила бэкапа
- Детальное логирование операций

## ⚠️ Потенциальные проблемы

### 1. Старые устройства
- Минимум: Android 8.0+ (API 26)
- Рекомендуется: Android 11+ (API 30)

### 2. Устаревшие разрешения
- WRITE_EXTERNAL_STORAGE - работает только до API 32
- READ_EXTERNAL_STORAGE - заменено на READ_MEDIA_*

### 3. Foreground Service
- Требует специального типа (dataSync)
- Обязательное уведомление
- Ограничения в фоновом режиме

## 🛠️ Устранение проблем

### Gradle sync failed
```bash
cd android-app
./gradlew clean
./gradlew build
```

### Build failed - Java version
```bash
# Убедитесь в использовании JDK 17+
export JAVA_HOME=/path/to/jdk-17
./gradlew build
```

### Permission denied errors
```bash
# Обновите права на gradlew
chmod +x gradlew
```

### Notification permission
- В Android 13+ уведомления требуют разрешения
- Пользователь должен явно их разрешить
- Проверьте Settings → Apps → Remote Monitor → Notifications

## 📊 Производительность

### Что улучшилось:
- ⚡ Faster Gradle builds (8.2.0)
- 🎨 Hardware-accelerated Material 3 animations
- 💾 Optimized memory usage with Scoped Storage
- 🔋 Better battery management with Foreground Service

### Размер APK:
- Debug: ~15-20 MB (было 10-15 MB)
- Release: ~10-15 MB (было 7-12 MB)
- Увеличение из-за Material 3 библиотек

## 🎯 Рекомендации

### Для разработчиков:
1. **Всегда тестируйте** на реальных устройствах Android 14
2. **Проверяйте разрешения** - новая логика с API 33+
3. **Используйте последние** Android Studio и SDK tools
4. **Следите за логами** - новые предупреждения безопасности

### Для пользователей:
1. **Обновите Android** до версии 11+ для лучшего опыта
2. **Разрешите уведомления** для корректной работы
3. **Проверьте разрешения** в настройках приложения
4. **Используйте WiFi** для стабильного подключения

## 📚 Полезные ссылки

- [Android 14 Developer Guide](https://developer.android.com/about/versions/14)
- [Material Design 3](https://m3.material.io/)
- [Runtime Permissions](https://developer.android.com/training/permissions/requesting)
- [Foreground Services](https://developer.android.com/guide/components/foreground-services)

## 🆘 Получить помощь

Если возникли проблемы:
1. Проверьте [INSTALLATION.md](INSTALLATION.md)
2. Изучите логи: `adb logcat | grep RemoteMonitor`
3. Убедитесь в соответствии системным требованиям
4. Попробуйте пересборку проекта

**Помните: это образовательный проект, используйте только на собственных устройствах!** 🎓