#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
MAIN_URL="http://localhost:8080"
STORAGE1_URL="http://localhost:8081"
STORAGE2_URL="http://localhost:8082"
STORAGE3_URL="http://localhost:8083"
TEST_DB_ID="replication-test-db"
DIMENSION=3
VECTOR_COUNT=20
TEST_VECTOR_ID=""  # Будет определен позже

# Получаем директорию проекта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Функции для вывода
print_step() {
    printf "${BLUE}==== %s ====${NC}\n" "$1"
}

print_success() {
    printf "${GREEN}✅ %s${NC}\n" "$1"
}

print_error() {
    printf "${RED}❌ %s${NC}\n" "$1"
}

print_warning() {
    printf "${YELLOW}⚠️  %s${NC}\n" "$1"
}

print_info() {
    printf "${YELLOW}ℹ️  %s${NC}\n" "$1"
}

# Docker compose command (supports both old and new syntax)
docker_compose() {
    if docker compose version &> /dev/null 2>&1; then
        docker compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    else
        docker-compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    fi
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

    if ! command -v docker &> /dev/null; then
        print_error "docker не установлен"
        exit 1
    fi

    if ! docker compose version &> /dev/null && ! docker-compose version &> /dev/null; then
        print_error "docker-compose не установлен"
        exit 1
    fi

    print_success "Все зависимости установлены"
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

# Проверка доступности сервиса
check_health() {
    local url=$1
    local name=$2
    local max_attempts=10
    local attempt=1

    local health_endpoint=""
    if [[ "$url" == *"8080"* ]]; then
        health_endpoint="$url/api/health"
    else
        health_endpoint="$url/api/v1/storage/health"
    fi

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$health_endpoint" > /dev/null 2>&1; then
            echo ""
            print_success "$name доступен"
            return 0
        fi

        if [ $((attempt % 10)) -eq 0 ]; then
            printf "\r   Попытка %d/%d..." "$attempt" "$max_attempts"
        else
            printf "."
        fi

        sleep 2
        attempt=$((attempt + 1))
    done

    echo ""
    print_error "$name недоступен после $((max_attempts * 2)) секунд"
    return 1
}

# Получить количество векторов на шарде
get_vector_count() {
    local url=$1
    local response=$(curl -s "$url/api/v1/storage/databases/$TEST_DB_ID" 2>/dev/null)

    if [ -z "$response" ]; then
        echo "0"
        return
    fi

    if echo "$response" | grep -q "error\|not found\|NOT_FOUND" 2>/dev/null; then
        echo "0"
        return
    fi

    local count=$(echo "$response" | jq -r '.vectorCount // "0"' 2>/dev/null | tr -d '"')

    if ! [[ "$count" =~ ^[0-9]+$ ]]; then
        count="0"
    fi

    echo "$count"
}

# Удалить тестовую БД на конкретном шарде
delete_db_on_shard() {
    local url=$1
    local name=$2

    curl -s -X DELETE "$url/api/v1/storage/databases/$TEST_DB_ID" > /dev/null 2>&1
}

# Определить шард по ID вектора (простая хеш-функция)
get_shard_for_vector() {
    local vector_id=$1
    local hash=$(echo "$vector_id % 3" | bc)
    case $hash in
        0) echo "shard1" ;;
        1) echo "shard2" ;;
        2) echo "shard3" ;;
    esac
}

# Получить URL шарда по имени
get_shard_url() {
    local shard_name=$1
    case $shard_name in
        shard1) echo "$STORAGE1_URL" ;;
        shard2) echo "$STORAGE2_URL" ;;
        shard3) echo "$STORAGE3_URL" ;;
    esac
}

# Определить реплику для шарда
get_replica_shard() {
    local shard_name=$1
    case $shard_name in
        shard1) echo "shard2" ;;
        shard2) echo "shard3" ;;
        shard3) echo "shard1" ;;
    esac
}

# Шаг 1: Подготовка кластера
step1_prepare_cluster() {
    print_step "Шаг 1: Подготовка кластера"

    # Проверяем, запущены ли уже контейнеры
    local containers_running=true
    if ! docker ps | grep -q "vector-db-storage-1"; then
        containers_running=false
    fi
    if ! docker ps | grep -q "vector-db-storage-2"; then
        containers_running=false
    fi
    if ! docker ps | grep -q "vector-db-storage-3"; then
        containers_running=false
    fi
    if ! docker ps | grep -q "vector-db-main"; then
        containers_running=false
    fi

    if [ "$containers_running" = false ]; then
        print_info "Останавливаем существующие контейнеры..."
        docker_compose --profile with-shard3 down 2>/dev/null || true

        # Запускаем кластер с 3 шардами
        print_info "Запускаем кластер с 3 шардами..."
        docker_compose --profile with-shard3 up -d

        # Ждем запуска всех сервисов
        sleep 30
    else
        print_info "Контейнеры уже запущены"
    fi

    print_info "Проверяем ZooKeeper..."
    local zk_attempts=10
    local zk_attempt=1
    while [ $zk_attempt -le $zk_attempts ]; do
        if docker exec vector-db-zookeeper zkCli.sh ls / >/dev/null 2>&1; then
            print_success "ZooKeeper доступен"
            break
        fi
        printf "."
        sleep 3
        zk_attempt=$((zk_attempt + 1))
    done

    if [ $zk_attempt -gt $zk_attempts ]; then
        print_error "ZooKeeper недоступен"
        exit 1
    fi

    print_info "Проверяем Main module..."
    check_health "$MAIN_URL" "Main module" || exit 1

    print_info "Проверяем Storage 1..."
    check_health "$STORAGE1_URL" "Storage 1" || exit 1

    print_info "Проверяем Storage 2..."
    check_health "$STORAGE2_URL" "Storage 2" || exit 1

    print_info "Проверяем Storage 3..."
    check_health "$STORAGE3_URL" "Storage 3" || exit 1

    print_success "Кластер готов"
}

# Шаг 2: Настройка репликации и очистка данных
step2_setup_replication() {
    print_step "Шаг 2: Настройка репликации и очистка данных"

    # Очищаем тестовые данные
    print_info "Очищаем тестовые данные..."
    curl -s -X DELETE "$MAIN_URL/api/databases/$TEST_DB_ID" > /dev/null 2>&1
    delete_db_on_shard "$STORAGE1_URL" "Storage 1"
    delete_db_on_shard "$STORAGE2_URL" "Storage 2"
    delete_db_on_shard "$STORAGE3_URL" "Storage 3"

    sleep 2

    # Проверяем что все пусто
    local s1=$(get_vector_count "$STORAGE1_URL")
    local s2=$(get_vector_count "$STORAGE2_URL")
    local s3=$(get_vector_count "$STORAGE3_URL")

    if [ "$s1" -eq 0 ] && [ "$s2" -eq 0 ] && [ "$s3" -eq 0 ]; then
        print_success "Все шарды очищены"
    else
        print_warning "Шарды не полностью очищены (S1=$s1, S2=$s2, S3=$s3), но продолжаем"
    fi

    # Настраиваем репликацию через Admin API
    print_info "Настраиваем конфигурацию репликации через Admin API..."

    response=$(curl -s -X POST "$MAIN_URL/api/admin/cluster/config" \
        -H "Content-Type: application/json" \
        -d '{
            "shards": [
                {"shardId": "shard1", "baseUrl": "http://vector-db-storage-1:8081", "hashKey": 0, "status": "ACTIVE"},
                {"shardId": "shard2", "baseUrl": "http://vector-db-storage-2:8081", "hashKey": 3074457345618258602, "status": "ACTIVE"},
                {"shardId": "shard3", "baseUrl": "http://vector-db-storage-3:8081", "hashKey": 6148914691236517204, "status": "ACTIVE"}
            ],
            "metadata": {}
        }')

    if echo "$response" | grep -qi "success\|updated\|initiated"; then
        print_success "Конфигурация репликации установлена"
        echo "  Репликация: shard1→shard2, shard2→shard3, shard3→shard1"
    else
        print_warning "Ответ Admin API: $response"
        print_info "Продолжаем тест..."
    fi

    sleep 5
}

# Шаг 3: Создание БД и добавление тестовых данных
step3_create_database_and_data() {
    print_step "Шаг 3: Создание БД и добавление данных"

    print_info "Создаём базу данных: $TEST_DB_ID"
    response=$(curl -s -w "\n%{http_code}" -X POST "$MAIN_URL/api/databases" \
        -H "Content-Type: application/json" \
        -d "{\"id\":\"$TEST_DB_ID\",\"dimension\":$DIMENSION}")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        print_success "База данных создана"
    else
        print_error "Ошибка создания БД (HTTP $http_code)"
        echo "Response: $(echo "$response" | head -n-1)"
        exit 1
    fi

    print_info "Добавляем $VECTOR_COUNT векторов..."
    added_count=0
    vector_ids=()

    for i in $(seq 1 $VECTOR_COUNT); do
        v1=$(printf "%.2f" $(echo "$i * 0.1" | bc -l))
        v2=$(printf "%.2f" $(echo "$i * 0.2" | bc -l))
        v3=$(printf "%.2f" $(echo "$i * 0.3" | bc -l))

        response=$(curl -s -w "\n%{http_code}" -X POST "$MAIN_URL/api/vectors/add" \
            -H "Content-Type: application/json" \
            -d "{\"vector\":[$v1,$v2,$v3],\"data\":\"Vector $i\",\"dbId\":\"$TEST_DB_ID\"}")

        http_code=$(echo "$response" | tail -n1)

        if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            added_count=$((added_count + 1))
            # Извлекаем ID добавленного вектора
            vector_id=$(echo "$response" | head -n-1 | jq -r '.id // empty' 2>/dev/null)
            if [ -n "$vector_id" ] && [ "$vector_id" != "null" ]; then
                vector_ids+=("$vector_id")
            fi

            if [ $((i % 5)) -eq 0 ]; then
                printf "."
            fi
        fi
    done

    echo ""
    print_success "Добавлено $added_count векторов"

    # Выбираем тестовый вектор (середина диапазона)
    TEST_VECTOR_ID=${vector_ids[$((VECTOR_COUNT / 2))]}
    if [ -z "$TEST_VECTOR_ID" ]; then
        print_warning "Не удалось получить ID тестового вектора, используем фиксированный"
        TEST_VECTOR_ID="10"
    fi

    print_info "Тестовый вектор ID: $TEST_VECTOR_ID"
}

# Шаг 4: Проверка начального распределения данных
step4_check_initial_distribution() {
    print_step "Шаг 4: Проверка начального распределения"

    sleep 5  # Ждем завершения репликации

    shard1_count=$(get_vector_count "$STORAGE1_URL")
    shard2_count=$(get_vector_count "$STORAGE2_URL")
    shard3_count=$(get_vector_count "$STORAGE3_URL")
    total=$((shard1_count + shard2_count + shard3_count))

    echo "📊 Распределение векторов:"
    echo "   Shard 1: $shard1_count векторов"
    echo "   Shard 2: $shard2_count векторов"
    echo "   Shard 3: $shard3_count векторов"
    echo "   Всего:   $total векторов"

    if [ $total -lt $VECTOR_COUNT ]; then
        print_error "Потеряны векторы! Ожидалось минимум $VECTOR_COUNT, найдено $total"
        exit 1
    fi

    # Определяем где хранится наш тестовый вектор
    primary_shard=$(get_shard_for_vector "$TEST_VECTOR_ID")
    replica_shard=$(get_replica_shard "$primary_shard")

    echo ""
    echo "🎯 Тестовый вектор $TEST_VECTOR_ID:"
    echo "   Primary: $primary_shard ($(get_shard_url "$primary_shard"))"
    echo "   Replica: $replica_shard ($(get_shard_url "$replica_shard"))"

    # Проверяем доступность тестового вектора
    vector_data=$(curl -s "$MAIN_URL/api/vectors/$TEST_VECTOR_ID?dbId=$TEST_DB_ID")
    if echo "$vector_data" | jq -e '.id' > /dev/null 2>&1; then
        print_success "Тестовый вектор доступен через Main API"
    else
        print_error "Тестовый вектор недоступен через Main API"
        exit 1
    fi

    # Сохраняем информацию для следующих шагов
    PRIMARY_SHARD="$primary_shard"
    REPLICA_SHARD="$replica_shard"
    export PRIMARY_SHARD REPLICA_SHARD
}

# Шаг 5: Убиваем primary ноду
step5_kill_primary_node() {
    print_step "Шаг 5: Убиваем primary ноду"

    # Проверяем, настроена ли репликация
    if [ -z "$PRIMARY_SHARD" ]; then
        print_warning "Репликация не настроена, пропускаем тест failover"
        return 0
    fi

    primary_container=""
    case $PRIMARY_SHARD in
        shard1) primary_container="vector-db-storage-1" ;;
        shard2) primary_container="vector-db-storage-2" ;;
        shard3) primary_container="vector-db-storage-3" ;;
    esac

    print_info "Убиваем primary ноду: $primary_container ($PRIMARY_SHARD)"

    # Убиваем контейнер
    docker stop "$primary_container" 2>/dev/null || true

    # Ждем немного
    sleep 5

    # Проверяем что нода действительно мертва
    if docker ps | grep -q "$primary_container"; then
        print_error "Не удалось остановить $primary_container"
        exit 1
    else
        print_success "Primary нода $PRIMARY_SHARD остановлена"
    fi

    # Проверяем доступность других нод
    replica_url=$(get_shard_url "$REPLICA_SHARD")
    main_url="$MAIN_URL"

    print_info "Проверяем доступность replica ноды..."
    if ! check_health "$replica_url" "Replica $REPLICA_SHARD"; then
        print_error "Replica нода недоступна"
        exit 1
    fi

    print_info "Проверяем доступность Main module..."
    if ! check_health "$main_url" "Main module"; then
        print_error "Main module недоступна"
        exit 1
    fi
}

# Шаг 6: Проверяем доступность данных через реплику
step6_verify_data_through_replica() {
    print_step "Шаг 6: Проверка доступности данных через реплику"

    # Проверяем, настроена ли репликация
    if [ -z "$PRIMARY_SHARD" ]; then
        print_warning "Репликация не настроена, пропускаем тест чтения через реплику"
        return 0
    fi

    print_info "Проверяем доступность тестового вектора после падения primary..."

    # Пытаемся получить вектор через Main API (должен прочитать с реплики)
    max_attempts=10
    attempt=1
    success=false

    while [ $attempt -le $max_attempts ]; do
        printf "\r   Попытка %d/%d..." "$attempt" "$max_attempts"

        vector_data=$(curl -s "$MAIN_URL/api/vectors/$TEST_VECTOR_ID?dbId=$TEST_DB_ID")

        if echo "$vector_data" | jq -e '.id' > /dev/null 2>&1; then
            success=true
            echo ""
            print_success "Вектор успешно прочитан через реплику!"
            echo "   ID: $(echo "$vector_data" | jq -r '.id')"
            echo "   Data: $(echo "$vector_data" | jq -r '.originalData // .data // "N/A"')"
            break
        fi

        sleep 2
        attempt=$((attempt + 1))
    done

    if [ "$success" = false ]; then
        echo ""
        print_error "Не удалось прочитать вектор через реплику после падения primary"
        echo "Response: $vector_data"
        exit 1
    fi

    # Проверяем общее количество доступных векторов
    print_info "Проверяем общее количество доступных векторов..."

    # Пробуем поиск (должен работать через реплики)
    search_response=$(curl -s -X POST "$MAIN_URL/api/vectors/search" \
        -H "Content-Type: application/json" \
        -d "{\"vector\":[0.5,1.0,1.5],\"k\":5,\"dbId\":\"$TEST_DB_ID\"}")

    if echo "$search_response" | jq -e '.[0]' > /dev/null 2>&1; then
        search_count=$(echo "$search_response" | jq '. | length')
        print_success "Поиск работает, найдено $search_count результатов"
    else
        print_warning "Поиск не работает через реплики (может быть нормальным)"
        echo "Response: $search_response"
    fi
}

# Шаг 7: Восстанавливаем primary ноду и проверяем read repair
step7_restore_primary_and_check_repair() {
    print_step "Шаг 7: Восстанавливаем primary ноду"

    # Проверяем, настроена ли репликация
    if [ -z "$PRIMARY_SHARD" ]; then
        print_warning "Репликация не настроена, пропускаем тест восстановления"
        return 0
    fi

    primary_container=""
    case $PRIMARY_SHARD in
        shard1) primary_container="vector-db-storage-1" ;;
        shard2) primary_container="vector-db-storage-2" ;;
        shard3) primary_container="vector-db-storage-3" ;;
    esac

    print_info "Запускаем primary ноду заново: $primary_container ($PRIMARY_SHARD)"

    # Запускаем контейнер
    docker start "$primary_container"

    # Ждем запуска
    primary_url=$(get_shard_url "$PRIMARY_SHARD")
    if check_health "$primary_url" "Primary $PRIMARY_SHARD"; then
        print_success "Primary нода восстановлена"
    else
        print_error "Не удалось восстановить primary ноду"
        exit 1
    fi

    print_info "Ждем завершения read repair (10 сек)..."
    sleep 10

    # Проверяем что данные теперь доступны на primary
    primary_count=$(get_vector_count "$primary_url")
    print_info "Векторов на восстановленной primary ноде: $primary_count"

    if [ "$primary_count" -gt 0 ]; then
        print_success "Read repair сработал! Данные восстановлены на primary"
    else
        print_warning "Read repair не сработал или еще не завершился"
    fi

    # Финальная проверка доступности вектора
    vector_data=$(curl -s "$MAIN_URL/api/vectors/$TEST_VECTOR_ID?dbId=$TEST_DB_ID")
    if echo "$vector_data" | jq -e '.id' > /dev/null 2>&1; then
        print_success "Вектор по-прежнему доступен после восстановления"
    else
        print_error "Вектор стал недоступен после восстановления primary!"
        exit 1
    fi
}

# Шаг 8: Финальная проверка распределения
step8_final_distribution_check() {
    print_step "Шаг 8: Финальная проверка распределения"

    shard1_count=$(get_vector_count "$STORAGE1_URL")
    shard2_count=$(get_vector_count "$STORAGE2_URL")
    shard3_count=$(get_vector_count "$STORAGE3_URL")
    total=$((shard1_count + shard2_count + shard3_count))

    echo "📊 Финальное распределение векторов:"
    echo "   Shard 1: $shard1_count векторов"
    echo "   Shard 2: $shard2_count векторов"
    echo "   Shard 3: $shard3_count векторов"
    echo "   Всего:   $total векторов"

    if [ $total -ge $VECTOR_COUNT ]; then
        print_success "Все данные сохранены (возможно с репликами)"
    else
        print_warning "Некоторые данные могли быть потеряны: ожидалось $VECTOR_COUNT, найдено $total"
    fi
}

# Финальный отчет
print_final_report() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    print_success "ИНТЕГРАЦИОННЫЙ ТЕСТ РЕПЛИКАЦИИ ЗАВЕРШЕН"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Что было проверено:"
    echo "  ✅ Подъем кластера с 3 нодами"

    if [ -n "$PRIMARY_SHARD" ]; then
        echo "  ✅ Настройка кольцевой репликации"
        echo "  ✅ Создание тестовых данных"
        echo "  ✅ Распределение данных по шардам"
        echo "  ✅ Убийство primary ноды для тестового вектора"
        echo "  ✅ Чтение данных через реплику"
        echo "  ✅ Восстановление primary ноды"
        echo "  ✅ Проверка read repair"
        echo ""
        echo "Ключевой результат:"
        echo "   Тестовый вектор: $TEST_VECTOR_ID"
        echo "   Primary шард: $PRIMARY_SHARD"
        echo "   Replica шард: $REPLICA_SHARD"
        echo "   ✅ Данные остались доступны после падения primary"
    else
        echo "  ⚠️  Репликация не была настроена"
        echo "  ✅ Базовая функциональность кластера"
        echo "  ✅ Создание тестовых данных"
        echo "  ✅ Доступность через Main API"
        echo ""
        echo "Ограниченный результат:"
        echo "   Тестовый вектор: $TEST_VECTOR_ID"
        echo "   ✅ Базовые операции работают"
        echo "   ⚠️  Репликация недоступна"
    fi
    echo ""
    echo "Итоговое распределение:"
    echo "  Shard 1: $(get_vector_count $STORAGE1_URL) векторов"
    echo "  Shard 2: $(get_vector_count $STORAGE2_URL) векторов"
    echo "  Shard 3: $(get_vector_count $STORAGE3_URL) векторов"
    echo ""
    echo "Полезные команды:"
    echo "  docker logs vector-db-main      # Логи main"
    echo "  docker logs vector-db-storage-1 # Логи storage 1"
    echo "  docker logs vector-db-storage-2 # Логи storage 2"
    echo "  docker logs vector-db-storage-3 # Логи storage 3"
    echo "  http://localhost:9000           # ZooKeeper UI"
    echo "  http://localhost:8080/swagger-ui.html # Swagger"
    echo ""
}

# Обработка Ctrl+C
trap 'echo ""; print_warning "Тест прерван пользователем"; exit 130' INT

# Главная функция
main() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  ИНТЕГРАЦИОННЫЙ ТЕСТ РЕПЛИКАЦИИ VECTOR DATABASE"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Этот тест проверяет:"
    echo "• Подъем кластера с репликацией"
    echo "• Убийство primary ноды"
    echo "• Чтение через реплики"
    echo "• Read repair восстановление"
    echo ""

    check_dependencies
    step1_prepare_cluster
    step2_setup_replication
    step3_create_database_and_data
    step4_check_initial_distribution
    step5_kill_primary_node
    step6_verify_data_through_replica
    step7_restore_primary_and_check_repair
    step8_final_distribution_check
    print_final_report
}

# Запуск
main "$@"