# Vector Database

Распределенная векторная база данных с поддержкой шардирования и репликации, построенная на Java 23 и Spring Boot.

## Архитектура

### Модули

- **common** - Общие интерфейсы, модели данных и конфигурации
- **main-module** - Главный модуль для обработки пользовательских запросов и координации работы с шардами
- **storage-module** - Модуль хранения данных с KV хранилищем (RocksDB) и KNN поиском

### Компоненты

- **Основной сервис (Main)** - принимает REST API запросы и распределяет их по шардам
- **Шарды хранения (Storage)** - содержат векторы и выполняют поиск по локальным данным
- **KV хранилище** - RocksDB для персистентного хранения векторов и метаданных
- **KNN поиск** - алгоритм поиска ближайших соседей (планируется замена на более эффективные индексы)

## Сборка и запуск

### Требования

- Java 23
- Docker и Docker Compose
- Gradle

### Сборка

```bash
./build.sh
```

### Запуск кластера

```bash
# Запуск в фоновом режиме
docker-compose up -d

# Просмотр логов
docker-compose logs -f

# Остановка
docker-compose down
```

## API

### Главный сервис (порт 8080)

**Управление базами данных:**
- `POST /api/v1/databases` - создать базу данных
- `GET /api/v1/databases` - список баз данных
- `GET /api/v1/databases/{id}` - информация о базе данных
- `DELETE /api/v1/databases/{id}` - удалить базу данных

**Работа с векторами:**
- `POST /api/v1/vectors/{databaseId}` - добавить вектор
- `GET /api/v1/vectors/{databaseId}/{id}` - получить вектор
- `DELETE /api/v1/vectors/{databaseId}/{id}` - удалить вектор
- `POST /api/v1/vectors/{databaseId}/search` - поиск похожих векторов

**Мониторинг:**
- `GET /api/v1/health` - состояние кластера

### Шарды хранения (порты 8081, 8082)

- `POST /api/v1/storage/vectors/{databaseId}` - добавить вектор
- `GET /api/v1/storage/vectors/{databaseId}/{id}` - получить вектор
- `DELETE /api/v1/storage/vectors/{databaseId}/{id}` - удалить вектор
- `POST /api/v1/storage/search` - поиск векторов
- `GET /api/v1/storage/health` - состояние шарда

## Примеры использования

### Создание базы данных

```bash
curl -X POST http://localhost:8080/api/v1/databases \
  -H "Content-Type: application/json" \
  -d '{
    "id": "embeddings",
    "name": "Text Embeddings"
  }'
```

### Добавление вектора

```bash
curl -X POST http://localhost:8080/api/v1/vectors/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "embedding": [0.1, 0.2, 0.3, ...],
    "originalData": "This is the original text that was converted to embedding"
  }'
```

### Поиск похожих векторов

```bash
# Простой поиск топ-K
curl -X POST http://localhost:8080/api/v1/vectors/embeddings/search \
  -H "Content-Type: application/json" \
  -d '{
    "embedding": [0.1, 0.2, 0.3, ...],
    "k": 5
  }'

# Поиск с порогом схожести  
curl -X POST http://localhost:8080/api/v1/vectors/embeddings/search \
  -H "Content-Type: application/json" \
  -d '{
    "embedding": [0.1, 0.2, 0.3, ...],
    "k": 10,
    "threshold": 0.8
  }'
```

## Конфигурация

### Шардирование

В файле `main-module/src/main/resources/application.yml` настраиваются параметры кластера:

```yaml
vector-db:
  cluster:
    shards:
      - shard-id: shard-1
        host: localhost
        port: 8081
        is-active: true
```

## Ключевые особенности v2.0

### 🔧 **Улучшенная архитектура**
- **Автогенерация ID**: система автоматически генерирует ID векторов для равномерного распределения по шардам
- **Убрана фиксированная размерность**: базы данных больше не ограничены одной размерностью векторов
- **Исходные данные**: сохранение исходного содержимого, из которого был создан эмбединг
- **Lombok integration**: уменьшение boilerplate кода с помощью аннотаций

### 🏗️ **Подготовка к масштабированию** 
- **Абстракция StorageEngine**: готовность к замене RocksDB на собственное решение
- **Интерфейс Serializer**: подготовлена архитектура для кастомной сериализации
- **Поиск с порогом**: поддержка поиска не только по топ-K, но и по порогу схожести

## Будущие улучшения

1. **Собственная БД**: замена RocksDB на оптимизированное под векторы хранилище
2. **Кастомный сериализатор**: эффективная сериализация векторных данных
3. **Индексация**: замена KNN на более эффективные алгоритмы (HNSW, IVF)
4. **Репликация**: добавление реплик для обеспечения отказоустойчивости
5. **Автоматическое шардирование**: динамическое распределение данных
6. **Мониторинг**: расширенные метрики и дашборды
