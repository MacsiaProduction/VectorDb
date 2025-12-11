# Интеграционный тест репликации VectorDB

Скрипт (`integration-test-replication.sh`) выполняет полноценный интеграционный тест репликации VectorDB, проверяя поведение системы при падении нод и восстановлении данных.

## Что проверяет тест

Тест имитирует сценарий работы распределенной базы данных:

1. **Подъем кластера** с 3 нодами хранения данных
2. **Настройка репликации** (кольцевая: shard1→shard2→shard3→shard1)
3. **Создание тестовых данных** (20 векторов)
4. **Определение primary/replica** локаций для тестового вектора
5. **Убийство primary ноды** для выбранного вектора
6. **Проверка доступности данных** через реплику
7. **Восстановление primary ноды**
8. **Проверка read repair** (автоматическое восстановление данных)

## Архитектура репликации

```
Shard 1 (localhost:8081) → реплики на Shard 2
Shard 2 (localhost:8082) → реплики на Shard 3
Shard 3 (localhost:8083) → реплики на Shard 1
```

## Запуск теста

### Автоматическая установка зависимостей

Скрипт автоматически проверяет и устанавливает необходимые зависимости:
- **jq** - устанавливается автоматически через Chocolatey/Scoop/apt/brew
- **curl** - должен быть установлен заранее
- **docker & docker-compose** - должны быть установлены заранее

### Быстрый запуск

```bash
cd scripts
bash integration-test-replication.sh
```

### Что происходит на каждом шаге

#### Шаг 1: Подготовка кластера
- Останавливает существующие контейнеры
- Запускает ZooKeeper, 3 storage ноды и main API
- Ждет готовности всех сервисов

#### Шаг 2: Настройка репликации
- Конфигурирует кластер через Admin API
- Устанавливает кольцевую репликацию
- Очищает тестовые данные

#### Шаг 3: Создание данных
- Создает тестовую базу данных
- Добавляет 20 векторов размерностью 3
- Определяет ID тестового вектора (из середины диапазона)

#### Шаг 4: Проверка распределения
- Проверяет, что данные распределены по шардам
- Определяет primary и replica шарды для тестового вектора

#### Шаг 5: Убийство primary ноды
- Останавливает контейнер primary шарда
- Проверяет доступность replica шардов

#### Шаг 6: Чтение через реплику
- Пытается прочитать тестовый вектор через Main API
- Система должна автоматически прочитать данные с реплики

#### Шаг 7: Восстановление и read repair
- Запускает primary ноду заново
- Ждет завершения read repair
- Проверяет восстановление данных

#### Шаг 8: Финальная проверка
- Проверяет итоговое распределение данных
- Подтверждает целостность

## Ожидаемые результаты

**Успешный тест** показывает:
- Данные остаются доступны после падения primary ноды
- Система читает с реплик автоматически
- Read repair восстанавливает данные на primary после перезапуска
- Общая целостность данных сохраняется

## Возможные проблемы

### Порт 9000 занят
ZooKeeper UI может не запуститься, если порт 9000 занят. Это не критично для теста.

### Storage 3 не запускается
Убедитесь, что запускаете с профилем:
```bash
docker-compose --profile with-shard3 up -d vector-db-storage-3
```

### Тест падает на проверке данных
- Проверьте логи: `docker logs vector-db-main`
- Убедитесь, что репликация настроена корректно
- Проверьте состояние ZooKeeper: `docker logs vector-db-zookeeper`

## Отладка

### Логи компонентов
```bash
# Main API логи
docker logs -f vector-db-main

# Storage логи
docker logs -f vector-db-storage-1
docker logs -f vector-db-storage-2
docker logs -f vector-db-storage-3

# ZooKeeper
docker logs -f vector-db-zookeeper
```

### API endpoints для проверки
```bash
# Health checks
curl http://localhost:8080/api/health
curl http://localhost:8081/api/v1/storage/health
curl http://localhost:8082/api/v1/storage/health
curl http://localhost:8083/api/v1/storage/health

# Cluster config
curl http://localhost:8080/api/admin/cluster/config

# Database info
curl http://localhost:8081/api/v1/storage/databases/replication-test-db
```

### Ручная проверка репликации
```bash
# Создать тестовый вектор
curl -X POST http://localhost:8080/api/vectors/add \
  -H "Content-Type: application/json" \
  -d '{"vector":[1.0,2.0,3.0],"data":"test","dbId":"replication-test-db"}'

# Проверить на всех шардах
curl http://localhost:8081/api/v1/storage/databases/replication-test-db
curl http://localhost:8082/api/v1/storage/databases/replication-test-db
curl http://localhost:8083/api/v1/storage/databases/replication-test-db
```

## Сравнение с unit-тестами

Этот интеграционный тест проверяет **реальное поведение** системы в отличие от unit-тестов:

| Aspect | Unit Tests (`ShardRebalancerTest.java`) | Integration Test |
|--------|---------------------------------------|------------------|
| Репликация | Моки всех компонентов | Реальные HTTP вызовы |
| Сеть | Нет | Docker контейнеры |
| Состояние | Изолированные тесты | Полная система |
| Read Repair | Моки | Реальная логика |
| Failover | Симуляция | Настоящее падение нод |

## Чистка после теста

```bash
# Остановить все
docker-compose -f docker-compose.sharded.yml down

# Очистить volumes (если нужно)
docker volume rm $(docker volume ls -q | grep vectordb)
```