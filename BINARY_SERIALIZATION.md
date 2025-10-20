# Бинарная сериализация SearchResult

## Проблема
Передача данных между storage-module и main-module через JSON неэффективна для больших списков векторов.

## Решение
Реализована бинарная сериализация с использованием ByteBuffer и varint-кодирования.
Endpoint `/search` поддерживает content negotiation: возвращает JSON или бинарные данные в зависимости от `Accept` header.

## Формат
```
[размер массива (varint)]
Для каждого SearchResult:
  [distance (8 байт double)] [similarity (8 байт double)]
  [id (varint)] [createdAt (8 байт long millis)]
  [размерность embedding (varint)] [floats (размерность * 4 байт)]
  [длина databaseId (varint)] [databaseId UTF-8]
  [длина originalData (varint)] [originalData UTF-8]
```

## Использование

### Storage Module
Endpoint `/api/v1/storage/search` автоматически выбирает формат ответа:
- `Accept: application/json` → JSON
- `Accept: application/octet-stream` → бинарный формат

### Main Module  
`StorageClient.searchVectors()` автоматически использует бинарный формат через `Accept: application/octet-stream`.

## Производительность

### 100 сущностей с embedding размерностью 384

**Размеры:**
- Несжатый бинарный: 159 КБ
- Сжатый GZIP: 143 КБ (экономия 10%)
- JSON несжатый: 437 КБ
- JSON сжатый: 181 КБ

**Время (среднее на 1000 итераций):**
- Сериализация: 53 мкс
- Десериализация: 59 мкс
- Сжатие GZIP: 6,450 мкс
- Разжатие GZIP: 803 мкс

**Сравнение с Protobuf**
- Бинарный формат тратит в среднем на 80% меньше времени на сериализацию
- Бинарный формат тратит в среднем на 68% меньше времени на десериализацию
- Общий размер сообщения бинарного формата в среднем меньше на ~1%

  <img width="830" height="686" alt="image" src="https://github.com/user-attachments/assets/3d5f37a0-3467-45e3-9b1e-fa76c9675897" />

  <img width="789" height="621" alt="image" src="https://github.com/user-attachments/assets/89282791-5db2-4e45-bb2c-d63c1863885c" />

  <img width="791" height="618" alt="image" src="https://github.com/user-attachments/assets/ae579dcd-aba7-4f56-988e-802f1da02c8d" />



**Вывод:**
- Бинарный формат на **64% компактнее** JSON
- Сжатие GZIP **невыгодно** - экономит 10% размера, но добавляет ~7 мс CPU-времени
- Использование собственного бинарного формата оптимальнее использования Protobuf для решения поставленной задачи в первую очередь по времени сериализации/десериализации
