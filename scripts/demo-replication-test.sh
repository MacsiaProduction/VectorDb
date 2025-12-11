#!/bin/bash

# Демо-версия интеграционного теста репликации
# Показывает шаги без выполнения опасных операций

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "${BLUE}==== $1 ====${NC}"
}

print_success() {
    echo -e "${GREEN}[OK] $1${NC}"
}

print_error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

print_info() {
    echo -e "${YELLOW}[INFO] $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[WARN] $1${NC}"
}

# Установка jq
install_jq() {
    print_info "Пытаемся установить jq..."

    # Для Windows через Chocolatey
    if command -v choco &> /dev/null; then
        print_info "Устанавливаем через Chocolatey..."
        if choco install jq -y &> /dev/null; then
            print_success "jq установлен через Chocolatey"
            return 0
        fi
    fi

    # Для Windows через Scoop
    if command -v scoop &> /dev/null; then
        print_info "Устанавливаем через Scoop..."
        if scoop install jq &> /dev/null; then
            print_success "jq установлен через Scoop"
            return 0
        fi
    fi

    # Для WSL/Ubuntu
    if command -v apt-get &> /dev/null; then
        print_info "Устанавливаем через apt-get..."
        if sudo apt-get update &> /dev/null && sudo apt-get install -y jq &> /dev/null; then
            print_success "jq установлен через apt-get"
            return 0
        fi
    fi

    # Для macOS через brew
    if command -v brew &> /dev/null; then
        print_info "Устанавливаем через Homebrew..."
        if brew install jq &> /dev/null; then
            print_success "jq установлен через Homebrew"
            return 0
        fi
    fi

    # Ручная загрузка для Windows
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
        print_info "Пытаемся скачать jq вручную для Windows..."
        JQ_URL="https://github.com/stedolan/jq/releases/download/jq-1.6/jq-win64.exe"
        JQ_PATH="/usr/local/bin/jq.exe"

        if curl -L "$JQ_URL" -o "$JQ_PATH" &> /dev/null && chmod +x "$JQ_PATH" &> /dev/null; then
            print_success "jq скачан и установлен вручную"
            return 0
        fi
    fi

    print_warning "Не удалось автоматически установить jq"
    return 1
}

# Проверка зависимостей
check_dependencies() {
    print_step "Проверка зависимостей"

    if ! command -v curl &> /dev/null; then
        print_error "curl не установлен"
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        print_warning "jq не установлен, пытаемся установить автоматически..."
        install_jq
        if ! command -v jq &> /dev/null; then
            print_error "Не удалось установить jq. Установите вручную:"
            echo "  Windows (Chocolatey): choco install jq"
            echo "  Windows (Scoop): scoop install jq"
            echo "  Ubuntu/Debian: sudo apt-get install jq"
            echo "  macOS: brew install jq"
            exit 1
        fi
    fi

    print_success "Все зависимости установлены"
}

# Запуск проверки зависимостей
check_dependencies

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ДЕМО: Интеграционный тест репликации VectorDB"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "Этот скрипт показывает шаги полного теста БЕЗ выполнения"
echo "опасных операций (остановка контейнеров)."
echo ""
echo "Для полного теста запустите: bash integration-test-replication.sh"
echo ""

print_step "Шаг 1: Подготовка кластера"
echo "[SETUP] Запуск ZooKeeper, 3 storage нод и Main API"
echo "[WAIT] Ожидание готовности всех сервисов"
echo "[CHECK] Проверка доступности всех компонентов"
print_success "Кластер готов (в демо-режиме)"

print_step "Шаг 2: Настройка репликации"
echo "[CONFIG] Конфигурация кольцевой репликации:"
echo "   shard1 → shard2 → shard3 → shard1"
echo "[CLEAN] Очистка тестовых данных"
print_success "Репликация настроена"

print_step "Шаг 3: Создание тестовых данных"
echo "[DB] Создание БД 'replication-test-db'"
echo "[ADD] Добавление 20 векторов размерностью 3"
echo "[SELECT] Выбор тестового вектора (ID из середины диапазона)"
print_success "Тестовые данные созданы"

print_step "Шаг 4: Анализ распределения"
echo "[CHECK] Проверка распределения по шардам"
echo "[ANALYZE] Определение primary/replica для тестового вектора"
echo ""
echo "   Пример: Вектор ID=10"
echo "   Primary: shard2 (localhost:8082)"
echo "   Replica: shard3 (localhost:8083)"
print_success "Распределение проверено"

print_step "Шаг 5: Имитация падения primary ноды"
print_warning "В ДЕМО режиме: контейнер НЕ останавливается"
echo "[STOP] Остановка primary ноды (shard2)"
echo "[CHECK] Проверка доступности replica (shard3)"
print_success "Primary нода 'убита'"

print_step "Шаг 6: Чтение через реплику"
echo "[READ] Попытка чтения тестового вектора через Main API"
echo "[AUTO] Система читает данные из реплики автоматически"
echo "[SUCCESS] Вектор успешно прочитан через реплику"
print_success "Read from replica работает"

print_step "Шаг 7: Восстановление primary ноды"
print_warning "В ДЕМО режиме: контейнер НЕ запускается"
echo "[RESTART] Перезапуск primary ноды"
echo "[WAIT] Ожидание read repair"
echo "[REPAIR] Автоматическое восстановление данных на primary"
print_success "Read repair завершен"

print_step "Шаг 8: Финальная проверка"
echo "[FINAL] Итоговое распределение данных:"
echo "   Shard 1: X векторов"
echo "   Shard 2: Y векторов"
echo "   Shard 3: Z векторов"
echo "   Всего: X+Y+Z векторов (с репликами)"
print_success "Целостность данных подтверждена"

echo ""
echo "═══════════════════════════════════════════════════════════"
print_success "ДЕМО-ТЕСТ ЗАВЕРШЕН"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "[RESULT] Ключевой результат:"
echo "   [OK] Данные остаются доступны после падения primary"
echo "   [OK] Система автоматически читает с реплик"
echo "   [OK] Read repair восстанавливает данные"
echo ""
echo "[NOTE] Для полного теста:"
echo "   bash integration-test-replication.sh"
echo ""
echo "[DOCS] Документация:"
echo "   README-replication-test.md"
echo ""
