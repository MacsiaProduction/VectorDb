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
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "docker-compose не установлен"
        exit 1
    fi
    
    print_success "Все зависимости установлены"
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
    
    local count=$(echo "$response" | jq -r '.vectorCount // "0"' | tr -d '"')
    
    if ! [[ "$count" =~ ^[0-9]+$ ]]; then
        count="0"
    fi
    
    echo "$count"
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
        docker-compose -f docker-compose.sharded.yml down 2>/dev/null || true
        docker-compose -f docker-compose.sharded.yml up -d
        sleep 60
        ./scripts/init-cluster-config.sh
        sleep 5
    else
        print_info "Контейнеры уже запущены"
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

step1_create_db_and_add_vectors() {
    print_step "Шаг 1: Создание БД и добавление векторов"
    
    print_info "Удаляем старую базу данных (если существует)..."
    curl -s -X DELETE "$MAIN_URL/api/databases/$TEST_DB_ID" > /dev/null 2>&1
    sleep 2
    
    print_info "Создаём базу данных: $TEST_DB_ID"
    response=$(curl -s -w "\n%{http_code}" -X POST "$MAIN_URL/api/databases" \
        -H "Content-Type: application/json" \
        -d "{\"id\":\"$TEST_DB_ID\",\"dimension\":$DIMENSION}")
    
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        print_success "База данных создана"
    else
        print_error "Ошибка создания БД (HTTP $http_code)"
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
    
    if [ $shard1_count -gt 0 ] && [ $shard2_count -gt 0 ]; then
        print_success "Векторы распределены между обоими шардами"
    else
        print_error "Векторы не распределены правильно"
        exit 1
    fi
    
    echo ""
}

# Шаг 3: Запустить 3-й шард
step3_start_third_shard() {
    print_step "Шаг 3: Запуск 3-го шарда"
    
    # Проверить, запущен ли уже
    if curl -s -f "$STORAGE3_URL/api/v1/storage/health" > /dev/null 2>&1; then
        print_success "Storage 3 уже запущен"
    else
        print_info "Запускаем Storage 3..."
        docker-compose -f docker-compose.sharded.yml --profile with-shard3 up -d
        sleep 10
        
        if check_health "$STORAGE3_URL" "Storage 3"; then
            print_success "Storage 3 запущен"
        else
            print_error "Не удалось запустить Storage 3"
            exit 1
        fi
    fi
    
    echo ""
}

step4_update_cluster_config() {
    print_step "Шаг 4: Обновление конфигурации кластера"
    
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
    
    if echo "$response" | grep -q "successfully\|initiated"; then
        print_success "Конфигурация обновлена"
    else
        print_error "Ошибка обновления конфигурации"
        exit 1
    fi
    
    echo ""
}

step5_wait_for_migration() {
    print_step "Шаг 5: Ожидание миграции"
    
    max_attempts=20
    attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        shard3_count=$(get_vector_count "$STORAGE3_URL")
        
        echo -ne "\r   Попытка $attempt/$max_attempts: Shard 3 = $shard3_count векторов"
        
        if [ $shard3_count -gt 0 ]; then
            echo ""
            print_success "Миграция завершена! $shard3_count векторов на Shard 3"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            echo ""
            print_error "Миграция не завершилась"
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
    
    if [ $total -eq $VECTOR_COUNT ]; then
        print_success "Все векторы сохранены"
    else
        print_error "Потеряны векторы! Ожидалось $VECTOR_COUNT, найдено $total"
        exit 1
    fi
    
    if [ $shard1_count -gt 0 ] && [ $shard2_count -gt 0 ] && [ $shard3_count -gt 0 ]; then
        print_success "Векторы распределены между всеми 3 шардами"
    fi
    
    echo ""
}

step7_test_get_operation() {
    print_step "Шаг 7: Проверка GET операции"
    
    vector_id=$(curl -s -X POST "$MAIN_URL/api/vectors/topK" \
        -H "Content-Type: application/json" \
        -d "{\"vector\":[0.0,0.0,0.0],\"k\":1,\"dbId\":\"$TEST_DB_ID\"}" \
        | jq -r '.[0].id')
    
    if [ -z "$vector_id" ] || [ "$vector_id" = "null" ]; then
        print_error "Не удалось получить ID вектора"
        exit 1
    fi
    
    vector_data=$(curl -s "$MAIN_URL/api/vectors/$vector_id?dbId=$TEST_DB_ID")
    
    if echo "$vector_data" | jq -e '.id' > /dev/null 2>&1; then
        print_success "Вектор успешно получен по ID"
    else
        print_error "Не удалось получить вектор по ID"
        exit 1
    fi
    
    echo ""
}

# Шаг 8: Проверить конфигурацию через API
step8_check_config_api() {
    print_step "Шаг 8: Проверка конфигурации через API"
    
    config=$(curl -s "$MAIN_URL/api/admin/cluster/config")
    
    shard_count=$(echo "$config" | jq '.shards | length')
    
    if [ "$shard_count" = "3" ]; then
        print_success "Конфигурация содержит 3 шарда"
        echo "   Шарды: $(echo $config | jq -r '.shards[].shardId' | tr '\n' ' ')"
    else
        print_error "Неправильное количество шардов в конфигурации: $shard_count"
        exit 1
    fi
    
    echo ""
}

# Финальный отчёт
print_final_report() {
    echo "Что было проверено:"
    echo "  ✅ Запуск кластера с 2 шардами"
    echo "  ✅ Создание базы данных"
    echo "  ✅ Добавление $VECTOR_COUNT векторов"
    echo "  ✅ Распределение по 2 шардам"
    echo "  ✅ Запуск 3-го шарда"
    echo "  ✅ Обновление конфигурации через Admin API"
    echo "  ✅ Автоматическая миграция данных"
    echo "  ✅ Перераспределение по 3 шардам"
    echo "  ✅ GET операция после решардинга"
    echo "  ✅ Конфигурация кластера"
    echo ""
    echo "Итоговое распределение:"
    echo "  Shard 1: $(get_vector_count $STORAGE1_URL) векторов"
    echo "  Shard 2: $(get_vector_count $STORAGE2_URL) векторов"
    echo "  Shard 3: $(get_vector_count $STORAGE3_URL) векторов"
    echo "  Всего:   $VECTOR_COUNT векторов"
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
    step1_create_db_and_add_vectors
    step2_check_distribution_2_shards
    step3_start_third_shard
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

