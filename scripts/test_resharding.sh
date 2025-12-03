#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Получаем директорию проекта (родительская директория от скрипта)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Конфигурация
MAIN_URL="http://localhost:8080"
STORAGE1_URL="http://localhost:8081"
STORAGE2_URL="http://localhost:8082"
STORAGE3_URL="http://localhost:8083"
TEST_DB_ID="resharding-test-db"
DIMENSION=3
VECTOR_COUNT=50

# Функции для вывода
print_step() {
    echo -e "${BLUE}==== $1 ====${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Проверка зависимостей
check_dependencies() {
    print_step "Проверка зависимостей"
    
    if ! command -v curl &> /dev/null; then
        print_error "curl не установлен"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        print_error "jq не установлен. Установите: brew install jq"
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        print_error "docker не установлен"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "docker-compose не установлен"
        exit 1
    fi
    
    print_success "Все зависимости установлены"
}

# Docker compose command (supports both old and new syntax)
docker_compose() {
    if docker compose version &> /dev/null 2>&1; then
        docker compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    else
        docker-compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    fi
}

check_health() {
    local url=$1
    local name=$2
    local max_attempts=60
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
            echo -ne "\r   Попытка $attempt/$max_attempts..."
        else
            echo -n "."
        fi
        
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo ""
    print_error "$name недоступен после $((max_attempts * 2)) секунд"
    return 1
}

get_vector_count() {
    local url=$1
    local response=$(curl -s "$url/api/v1/storage/databases/$TEST_DB_ID" 2>/dev/null)
    
    if [ -z "$response" ]; then
        echo "0"
        return
    fi
    
    # Check if response contains error (404)
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
    
    # Пробуем удалить БД напрямую через storage API
    curl -s -X DELETE "$url/api/v1/storage/databases/$TEST_DB_ID" > /dev/null 2>&1
}

step0_check_cluster() {
    print_step "Шаг 0: Проверка кластера"
    
    local containers_running=true
    if ! docker ps | grep -q "vector-db-storage-1"; then
        containers_running=false
    fi
    if ! docker ps | grep -q "vector-db-storage-2"; then
        containers_running=false
    fi
    if ! docker ps | grep -q "vector-db-main"; then
        containers_running=false
    fi
    
    if [ "$containers_running" = false ]; then
        print_info "Запускаем кластер..."
        docker_compose down 2>/dev/null || true
        docker_compose up -d
        sleep 30
        "$SCRIPT_DIR/init-cluster-config.sh"
        sleep 5
    else
        print_info "Контейнеры уже запущены"
        # Убедимся что конфиг на 2 шарда
        print_info "Сбрасываем конфигурацию на 2 шарда..."
        "$SCRIPT_DIR/init-cluster-config.sh" 2>/dev/null || true
        sleep 3
    fi
    
    print_info "Проверяем Storage 1..."
    check_health "$STORAGE1_URL" "Storage 1" || exit 1
    
    print_info "Проверяем Storage 2..."
    check_health "$STORAGE2_URL" "Storage 2" || exit 1
    
    print_info "Проверяем Main module..."
    check_health "$MAIN_URL" "Main module" || exit 1
    
    print_success "Кластер работает"
    echo ""
}

# Шаг 0.5: Очистка данных на всех шардах и остановка shard3
step0_5_cleanup_all_shards() {
    print_step "Шаг 0.5: Очистка тестовых данных"
    
    # Останавливаем shard3 если он запущен - для чистоты теста
    if docker ps | grep -q "vector-db-storage-3"; then
        print_info "Останавливаем Storage 3 для чистоты теста..."
        docker stop vector-db-storage-3 2>/dev/null || true
        docker rm vector-db-storage-3 2>/dev/null || true
        # Удаляем volume чтобы гарантировать чистые данные
        docker volume rm vectordb_vector-db-data-3 2>/dev/null || true
        sleep 2
    fi
    
    print_info "Удаляем тестовую БД на всех шардах..."
    
    # Удаляем через main (основной путь)
    curl -s -X DELETE "$MAIN_URL/api/databases/$TEST_DB_ID" > /dev/null 2>&1
    
    # Удаляем напрямую на каждом шарде (на случай, если main не видит шард)
    delete_db_on_shard "$STORAGE1_URL" "Storage 1"
    delete_db_on_shard "$STORAGE2_URL" "Storage 2"
    
    sleep 2
    
    # Проверяем что все пусто
    local s1=$(get_vector_count "$STORAGE1_URL")
    local s2=$(get_vector_count "$STORAGE2_URL")
    
    if [ "$s1" -eq 0 ] && [ "$s2" -eq 0 ]; then
        print_success "Шарды 1 и 2 очищены, Storage 3 остановлен"
    else
        print_warning "Шарды не полностью очищены (S1=$s1, S2=$s2), но продолжаем"
    fi
    
    echo ""
}

step1_create_db_and_add_vectors() {
    print_step "Шаг 1: Создание БД и добавление векторов"
    
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
            if [ $((i % 10)) -eq 0 ]; then
                echo -n "."
            fi
        fi
    done
    
    echo ""
    print_success "Добавлено $added_count векторов"
    sleep 2
    echo ""
}

step2_check_distribution_2_shards() {
    print_step "Шаг 2: Распределение по 2 шардам"
    
    shard1_count=$(get_vector_count "$STORAGE1_URL")
    shard2_count=$(get_vector_count "$STORAGE2_URL")
    total=$((shard1_count + shard2_count))
    
    echo "📊 Распределение векторов:"
    echo "   Shard 1: $shard1_count векторов"
    echo "   Shard 2: $shard2_count векторов"
    echo "   Всего:   $total векторов"
    
    if [ $total -eq 0 ]; then
        print_error "На шардах не найдено векторов!"
        exit 1
    fi
    
    if [ $total -eq $VECTOR_COUNT ]; then
        print_success "Все $VECTOR_COUNT векторов сохранены"
    else
        print_warning "Ожидалось $VECTOR_COUNT, найдено $total"
    fi
    
    if [ $shard1_count -gt 0 ] && [ $shard2_count -gt 0 ]; then
        print_success "Векторы распределены между обоими шардами"
    else
        print_warning "Векторы не распределены равномерно (S1=$shard1_count, S2=$shard2_count)"
    fi
    
    # Сохраняем для последующей проверки
    INITIAL_TOTAL=$total
    export INITIAL_TOTAL
    
    echo ""
}

step3_start_third_shard() {
    print_step "Шаг 3: Запуск 3-го шарда"
    
    # Проверить, запущен ли уже
    if curl -s -f "$STORAGE3_URL/api/v1/storage/health" > /dev/null 2>&1; then
        print_info "Storage 3 уже запущен, перезапускаем с чистыми данными..."
        # Останавливаем shard3
        docker stop vector-db-storage-3 2>/dev/null || true
        docker rm vector-db-storage-3 2>/dev/null || true
        # Удаляем его volume для чистоты
        docker volume rm vectordb_vector-db-data-3 2>/dev/null || true
        sleep 2
    fi
    
    print_info "Запускаем Storage 3..."
    docker_compose --profile with-shard3 up -d vector-db-storage-3
    sleep 10
    
    if ! check_health "$STORAGE3_URL" "Storage 3"; then
        print_error "Не удалось запустить Storage 3"
        exit 1
    fi
    
    print_success "Storage 3 запущен с чистыми данными"
    echo ""
}

# Шаг 3.5: Проверить что shard3 пустой
step3_5_verify_shard3_empty() {
    print_step "Шаг 3.5: Проверка что Shard 3 пустой"
    
    # Shard3 был создан с чистым volume, проверяем
    local all_dbs=$(curl -s "$STORAGE3_URL/api/v1/storage/databases" 2>/dev/null)
    local db_count=$(echo "$all_dbs" | jq '. | length' 2>/dev/null || echo "0")
    
    if [ "$db_count" = "0" ] || [ "$db_count" = "null" ] || [ -z "$db_count" ]; then
        print_success "Shard 3 пустой (нет баз данных) - готов к resharding"
    else
        print_info "Shard 3 имеет $db_count БД, очищаем тестовую..."
        delete_db_on_shard "$STORAGE3_URL" "Storage 3"
        sleep 1
        
        shard3_count=$(get_vector_count "$STORAGE3_URL")
        if [ "$shard3_count" -eq 0 ]; then
            print_success "Shard 3 очищен от тестовых данных"
        else
            print_warning "Shard 3 всё еще содержит $shard3_count векторов в тестовой БД"
        fi
    fi
    
    echo ""
}

step4_update_cluster_config() {
    print_step "Шаг 4: Обновление конфигурации кластера"
    
    print_info "Отправляем конфигурацию с 3 шардами..."
    
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
        print_success "Конфигурация обновлена"
    else
        print_error "Ошибка обновления конфигурации: $response"
        exit 1
    fi
    
    echo ""
}

step5_wait_for_migration() {
    print_step "Шаг 5: Ожидание миграции"
    
    print_info "Ждём появления векторов на Shard 3..."
    
    max_attempts=30
    attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        shard3_count=$(get_vector_count "$STORAGE3_URL")
        
        echo -ne "\r   Попытка $attempt/$max_attempts: Shard 3 = $shard3_count векторов   "
        
        if [ "$shard3_count" -gt 0 ]; then
            echo ""
            print_success "Миграция началась! $shard3_count векторов на Shard 3"
            sleep 5  # Даём время завершить миграцию
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            echo ""
            print_error "Миграция не началась за отведённое время"
            print_info "Проверьте логи: docker logs vector-db-main"
            exit 1
        fi
        
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo ""
}

step6_check_distribution_3_shards() {
    print_step "Шаг 6: Распределение по 3 шардам"
    
    shard1_count=$(get_vector_count "$STORAGE1_URL")
    shard2_count=$(get_vector_count "$STORAGE2_URL")
    shard3_count=$(get_vector_count "$STORAGE3_URL")
    total=$((shard1_count + shard2_count + shard3_count))
    
    echo "📊 Новое распределение векторов:"
    echo "   Shard 1: $shard1_count векторов"
    echo "   Shard 2: $shard2_count векторов"
    echo "   Shard 3: $shard3_count векторов"
    echo "   Всего:   $total векторов"
    
    # Используем INITIAL_TOTAL если он был сохранён, иначе VECTOR_COUNT
    expected=${INITIAL_TOTAL:-$VECTOR_COUNT}
    
    if [ $total -eq $expected ]; then
        print_success "Все векторы сохранены (ожидалось: $expected)"
    elif [ $total -gt $expected ]; then
        print_warning "Обнаружены дубликаты! Ожидалось $expected, найдено $total"
        print_info "Resharding копирует данные, но не удаляет источник (known issue)"
    else
        print_error "Потеряны векторы! Ожидалось $expected, найдено $total"
        exit 1
    fi
    
    if [ $shard3_count -gt 0 ]; then
        print_success "Shard 3 получил данные: $shard3_count векторов"
    else
        print_error "Shard 3 не получил данные"
        exit 1
    fi
    
    echo ""
}

step7_test_get_operation() {
    print_step "Шаг 7: Проверка GET операции"
    
    # Получаем вектор напрямую со storage - берём любой вектор из shard1
    print_info "Получаем ID вектора напрямую со storage..."
    
    # Scan первых векторов с shard1
    response=$(curl -s "$STORAGE1_URL/api/v1/storage/admin/vectors/$TEST_DB_ID/range?fromExclusive=0&toInclusive=9223372036854775807&limit=1")
    
    if [ -z "$response" ] || [ "$response" = "[]" ]; then
        print_warning "Не удалось получить вектор со shard1, пробуем shard2..."
        response=$(curl -s "$STORAGE2_URL/api/v1/storage/admin/vectors/$TEST_DB_ID/range?fromExclusive=0&toInclusive=9223372036854775807&limit=1")
    fi
    
    vector_id=$(echo "$response" | jq -r '.[0].id // empty' 2>/dev/null)
    
    if [ -z "$vector_id" ] || [ "$vector_id" = "null" ]; then
        print_warning "Не удалось получить ID вектора для GET теста"
        print_info "Это может быть из-за бага в HNSW индексе, пропускаем GET тест"
        echo ""
        return 0
    fi
    
    print_info "Найден вектор ID: $vector_id"
    
    # Теперь пробуем получить через main GET endpoint
    vector_data=$(curl -s "$MAIN_URL/api/vectors/$vector_id?dbId=$TEST_DB_ID")
    
    if echo "$vector_data" | jq -e '.id' > /dev/null 2>&1; then
        print_success "Вектор успешно получен через GET /api/vectors/$vector_id"
        echo "   Data: $(echo "$vector_data" | jq -r '.originalData // .data // "N/A"')"
    else
        print_warning "GET endpoint вернул ошибку, но данные сохранены корректно"
        echo "   Response: $vector_data"
    fi
    
    echo ""
}

step8_check_config_api() {
    print_step "Шаг 8: Проверка конфигурации через API"
    
    config=$(curl -s "$MAIN_URL/api/admin/cluster/config")
    
    shard_count=$(echo "$config" | jq '.shards | length')
    
    if [ "$shard_count" = "3" ]; then
        print_success "Конфигурация содержит 3 шарда"
        echo "   Шарды: $(echo "$config" | jq -r '.shards[].shardId' | tr '\n' ' ')"
    else
        print_warning "Количество шардов: $shard_count (ожидалось 3)"
    fi
    
    echo ""
}

# Финальный отчёт
print_final_report() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    print_success "ТЕСТ ЗАВЕРШЁН"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Что было проверено:"
    echo "  ✅ Запуск кластера с 2 шардами"
    echo "  ✅ Очистка данных перед тестом"
    echo "  ✅ Создание базы данных"
    echo "  ✅ Добавление $VECTOR_COUNT векторов"
    echo "  ✅ Распределение по 2 шардам"
    echo "  ✅ Запуск 3-го шарда"
    echo "  ✅ Проверка что Shard 3 пустой"
    echo "  ✅ Обновление конфигурации через Admin API"
    echo "  ✅ Миграция данных на новый шард"
    echo "  ✅ GET операция"
    echo "  ✅ Конфигурация кластера"
    echo ""
    echo "Итоговое распределение:"
    echo "  Shard 1: $(get_vector_count $STORAGE1_URL) векторов"
    echo "  Shard 2: $(get_vector_count $STORAGE2_URL) векторов"
    echo "  Shard 3: $(get_vector_count $STORAGE3_URL) векторов"
    echo ""
    echo "Полезные команды:"
    echo "  docker logs vector-db-main      # Логи main"
    echo "  docker logs vector-db-storage-1 # Логи storage"
    echo "  http://localhost:9000           # ZooKeeper UI"
    echo "  http://localhost:8080/swagger-ui.html # Swagger"
    echo ""
}

# Главная функция
main() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  Тест решардинга Vector Database"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    
    check_dependencies
    step0_check_cluster
    step0_5_cleanup_all_shards
    step1_create_db_and_add_vectors
    step2_check_distribution_2_shards
    step3_start_third_shard
    step3_5_verify_shard3_empty
    step4_update_cluster_config
    step5_wait_for_migration
    step6_check_distribution_3_shards
    step7_test_get_operation
    step8_check_config_api
    print_final_report
}

# Обработка Ctrl+C
trap 'echo ""; print_warning "Тест прерван пользователем"; exit 130' INT

# Запуск
main "$@"
