#!/bin/bash

# ======================================================================
# 🚀 Скрипт автоматической публикации Android Remote Monitoring System
# 👨‍💻 Автор: ReliableSecurity (https://github.com/ReliableSecurity)
# 📞 Telegram: @ReliableSecurity
# ⚖️ Образовательный проект для изучения Android API
# ======================================================================

set -e  # Остановка при ошибках

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Логотип
echo -e "${PURPLE}"
echo "██████╗ ███████╗██╗     ██╗ █████╗ ██████╗ ██╗     ███████╗"
echo "██╔══██╗██╔════╝██║     ██║██╔══██╗██╔══██╗██║     ██╔════╝"
echo "██████╔╝█████╗  ██║     ██║███████║██████╔╝██║     █████╗  "
echo "██╔══██╗██╔══╝  ██║     ██║██╔══██║██╔══██╗██║     ██╔══╝  "
echo "██║  ██║███████╗███████╗██║██║  ██║██████╔╝███████╗███████╗"
echo "╚═╝  ╚═╝╚══════╝╚══════╝╚═╝╚═╝  ╚═╝╚═════╝ ╚══════╝╚══════╝"
echo -e "${NC}"
echo -e "${CYAN}🔒 Android Remote Monitoring System - GitHub Deploy${NC}"
echo -e "${YELLOW}👨‍💻 Автор: ReliableSecurity | 📞 @ReliableSecurity${NC}"
echo ""

# Проверка окружения
echo -e "${BLUE}📋 Проверка окружения...${NC}"

# Проверка Git
if ! command -v git &> /dev/null; then
    echo -e "${RED}❌ Git не установлен!${NC}"
    exit 1
fi

# Проверка GitHub CLI (gh)
if command -v gh &> /dev/null; then
    echo -e "${GREEN}✅ GitHub CLI найден${NC}"
    GH_CLI_AVAILABLE=true
else
    echo -e "${YELLOW}⚠️ GitHub CLI (gh) не найден - будет использован manual режим${NC}"
    GH_CLI_AVAILABLE=false
fi

# Информация о проекте
PROJECT_NAME="android-remote-monitoring"
REPO_DESCRIPTION="🕵️ Advanced Android Remote Monitoring System with Stealth Mode - Educational Project for Android API Learning (5.0-14 Compatible)"
REPO_TOPICS="android,monitoring,stealth,security,educational,api,java,mobile,surveillance,research"

# Проверка текущего репозитория
if [ ! -d ".git" ]; then
    echo -e "${RED}❌ Текущая директория не является git репозиторием!${NC}"
    exit 1
fi

# Статистика проекта
echo -e "${BLUE}📊 Статистика проекта:${NC}"
FILE_COUNT=$(find . -type f | grep -v '\.git' | wc -l)
JAVA_LINES=$(find . -name "*.java" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
COMMIT_COUNT=$(git rev-list --count HEAD)

echo -e "  📁 Файлов: ${GREEN}$FILE_COUNT${NC}"
echo -e "  📝 Строк Java: ${GREEN}$JAVA_LINES${NC}"  
echo -e "  💾 Коммитов: ${GREEN}$COMMIT_COUNT${NC}"
echo ""

# Проверка изменений
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo -e "${YELLOW}⚠️ Обнаружены несохраненные изменения${NC}"
    echo -e "${BLUE}📝 Сохранение изменений...${NC}"
    
    git add .
    git commit -m "🔧 Final preparations for GitHub publication

✅ All components ready for deployment
📊 Statistics: $FILE_COUNT files, $JAVA_LINES lines Java code
🚀 Ready for educational use

👨‍💻 Author: ReliableSecurity
📞 Contact: @ReliableSecurity"
    
    echo -e "${GREEN}✅ Изменения сохранены${NC}"
fi

# Функция создания репозитория через GitHub CLI
create_repo_with_gh() {
    echo -e "${BLUE}🚀 Создание репозитория через GitHub CLI...${NC}"
    
    gh repo create "$PROJECT_NAME" \
        --description "$REPO_DESCRIPTION" \
        --public \
        --clone=false \
        --add-readme=false
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Репозиторий создан успешно${NC}"
        return 0
    else
        echo -e "${RED}❌ Ошибка создания репозитория${NC}"
        return 1
    fi
}

# Функция настройки remote и push
setup_and_push() {
    local github_user
    
    if [ "$GH_CLI_AVAILABLE" = true ]; then
        github_user=$(gh api user --jq .login 2>/dev/null || echo "ReliableSecurity")
    else
        echo -e "${YELLOW}📝 Введите ваш GitHub username:${NC}"
        read -r github_user
        if [ -z "$github_user" ]; then
            github_user="ReliableSecurity"
        fi
    fi
    
    local repo_url="https://github.com/${github_user}/${PROJECT_NAME}.git"
    
    echo -e "${BLUE}🔗 Настройка remote origin...${NC}"
    
    # Удаление существующего origin если есть
    git remote remove origin 2>/dev/null || true
    
    # Добавление нового origin
    git remote add origin "$repo_url"
    
    echo -e "${BLUE}📤 Отправка на GitHub...${NC}"
    
    # Push с установкой upstream
    if git push -u origin main; then
        echo -e "${GREEN}✅ Код успешно отправлен на GitHub!${NC}"
        echo -e "${CYAN}🌐 Репозиторий доступен по адресу:${NC}"
        echo -e "${YELLOW}   $repo_url${NC}"
        return 0
    else
        echo -e "${RED}❌ Ошибка отправки на GitHub${NC}"
        return 1
    fi
}

# Основной процесс
echo -e "${PURPLE}🚀 НАЧИНАЕМ ПУБЛИКАЦИЮ НА GITHUB...${NC}"
echo ""

if [ "$GH_CLI_AVAILABLE" = true ]; then
    # Проверка авторизации
    if ! gh auth status &>/dev/null; then
        echo -e "${YELLOW}🔐 Необходима авторизация в GitHub...${NC}"
        echo -e "${BLUE}Выполните: gh auth login${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ GitHub CLI авторизован${NC}"
    
    # Создание репозитория
    if create_repo_with_gh; then
        setup_and_push
    else
        echo -e "${YELLOW}⚠️ Переход в manual режим...${NC}"
        GH_CLI_AVAILABLE=false
    fi
fi

if [ "$GH_CLI_AVAILABLE" = false ]; then
    echo -e "${YELLOW}📋 MANUAL РЕЖИМ${NC}"
    echo -e "${BLUE}Выполните следующие шаги:${NC}"
    echo ""
    echo -e "${CYAN}1. Создайте репозиторий на GitHub:${NC}"
    echo -e "   🌐 Перейдите на https://github.com/new"
    echo -e "   📝 Имя репозитория: ${YELLOW}$PROJECT_NAME${NC}"
    echo -e "   📄 Описание: ${YELLOW}$REPO_DESCRIPTION${NC}"
    echo -e "   🔓 Сделайте репозиторий публичным"
    echo -e "   ❌ НЕ создавайте README.md (уже есть)"
    echo ""
    echo -e "${CYAN}2. После создания репозитория нажмите Enter для продолжения...${NC}"
    read -r
    
    setup_and_push
fi

# Финальная проверка
echo ""
echo -e "${PURPLE}🎉 ПУБЛИКАЦИЯ ЗАВЕРШЕНА!${NC}"
echo ""
echo -e "${GREEN}✅ Проект успешно опубликован на GitHub${NC}"
echo -e "${BLUE}📊 Статистика:${NC}"
echo -e "  📁 Файлов: $FILE_COUNT"
echo -e "  📝 Строк кода: $JAVA_LINES"
echo -e "  💾 Коммитов: $COMMIT_COUNT"
echo ""
echo -e "${CYAN}🔗 Полезные команды для управления репозиторием:${NC}"
echo -e "${YELLOW}  git status${NC}           - проверка статуса"
echo -e "${YELLOW}  git pull${NC}             - получение обновлений"
echo -e "${YELLOW}  git push${NC}             - отправка изменений"
echo -e "${YELLOW}  gh repo view${NC}         - просмотр репозитория"
echo ""
echo -e "${PURPLE}👨‍💻 Автор: ReliableSecurity${NC}"
echo -e "${PURPLE}📞 Telegram: @ReliableSecurity${NC}"
echo -e "${PURPLE}⚖️ Образовательный проект - используйте этично!${NC}"
echo ""

# Опциональное открытие репозитория в браузере
if [ "$GH_CLI_AVAILABLE" = true ]; then
    echo -e "${YELLOW}🌐 Открыть репозиторий в браузере? (y/n):${NC}"
    read -r open_browser
    if [[ "$open_browser" =~ ^[Yy]$ ]]; then
        gh repo view --web
    fi
fi

echo -e "${GREEN}🎊 Готово! Проект опубликован для образовательного использования.${NC}"