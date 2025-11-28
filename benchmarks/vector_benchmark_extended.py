#!/usr/bin/env python3
"""Extended vector storage benchmark with sequential read speed and latency."""

from __future__ import annotations

import argparse
import gc
import os
import shutil
import sqlite3
import struct
import tempfile
import time
from dataclasses import dataclass
from typing import Callable, Iterable, List

try:  # Local execution
    from .vector_benchmark import (
        VectorEntry,
        dir_size,
        generate_entries,
        human_size,
        measure_read,
    )
except ImportError:  # Script execution
    from vector_benchmark import VectorEntry, dir_size, generate_entries, human_size, measure_read


@dataclass
class RawBenchmarkResult:
    name: str
    write_sec: float
    read_sec: float
    size_bytes: int


@dataclass
class ExtendedBenchmarkResult(RawBenchmarkResult):
    seq_read_mb_s: float
    latency_ms: float


@dataclass
class BenchmarkDefinition:
    name: str
    runner: Callable[[List[VectorEntry], str, str], RawBenchmarkResult]
    requires_redis: bool = False


BENCHMARKS: list[BenchmarkDefinition] = []


def register_benchmark(definition: BenchmarkDefinition) -> None:
    BENCHMARKS.append(definition)


# ── Benchmark implementations ──────────────────────────────────────────────────
def bench_rocksdb(entries: list[VectorEntry], tmp_dir: str, _: str) -> RawBenchmarkResult:
    try:
        from rocksdict import Rdict
    except ImportError as exc:
        raise RuntimeError("rocksdict not installed") from exc

    path = os.path.join(tmp_dir, "rocksdb")
    db = Rdict(path)
    t0 = time.perf_counter()
    for e in entries:
        db[struct.pack("<q", e.id)] = e.serialize()
    db.flush()
    write_sec = time.perf_counter() - t0

    size = dir_size(path)

    def read_all(targets: Iterable[VectorEntry]) -> None:
        for e in targets:
            _ = db[struct.pack("<q", e.id)]

    read_sec = measure_read(read_all, entries)
    db.close()
    return RawBenchmarkResult("RocksDB", write_sec, read_sec, size)


def bench_lmdb(entries: list[VectorEntry], tmp_dir: str, _: str) -> RawBenchmarkResult:
    try:
        import lmdb
    except ImportError as exc:
        raise RuntimeError("lmdb not installed") from exc

    path = os.path.join(tmp_dir, "lmdb")
    os.makedirs(path, exist_ok=True)
    env = lmdb.open(path, map_size=10 * 1024**3)

    t0 = time.perf_counter()
    with env.begin(write=True) as txn:
        for e in entries:
            txn.put(struct.pack("<q", e.id), e.serialize())
    write_sec = time.perf_counter() - t0
    env.sync()
    size = dir_size(path)

    def read_all(targets: Iterable[VectorEntry]) -> None:
        with env.begin() as txn:
            for e in targets:
                _ = txn.get(struct.pack("<q", e.id))

    read_sec = measure_read(read_all, entries)
    env.close()
    return RawBenchmarkResult("LMDB", write_sec, read_sec, size)


def bench_sqlite(entries: list[VectorEntry], tmp_dir: str, _: str) -> RawBenchmarkResult:
    path = os.path.join(tmp_dir, "sqlite.db")
    conn = sqlite3.connect(path)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("CREATE TABLE vectors (id INTEGER PRIMARY KEY, data BLOB)")

    t0 = time.perf_counter()
    conn.executemany(
        "INSERT INTO vectors VALUES (?, ?)",
        [(e.id, e.serialize()) for e in entries],
    )
    conn.commit()
    write_sec = time.perf_counter() - t0
    size = os.path.getsize(path)

    cur = conn.cursor()

    def read_all(targets: Iterable[VectorEntry]) -> None:
        for e in targets:
            cur.execute("SELECT data FROM vectors WHERE id=?", (e.id,))
            cur.fetchone()

    read_sec = measure_read(read_all, entries)
    conn.close()
    return RawBenchmarkResult("SQLite", write_sec, read_sec, size)


def bench_redis(entries: list[VectorEntry], _: str, redis_url: str) -> RawBenchmarkResult:
    try:
        import redis
    except ImportError as exc:
        raise RuntimeError("redis-py not installed") from exc

    r = redis.from_url(redis_url)
    r.ping()
    key_prefix = "vbench_ext:"
    r.flushdb()

    t0 = time.perf_counter()
    pipe = r.pipeline()
    for e in entries:
        pipe.set(f"{key_prefix}{e.id}", e.serialize())
    pipe.execute()
    write_sec = time.perf_counter() - t0

    info = r.info("memory")
    size = info.get("used_memory_dataset", info.get("used_memory", 0))

    def read_all(targets: Iterable[VectorEntry]) -> None:
        pipe = r.pipeline()
        for e in targets:
            pipe.get(f"{key_prefix}{e.id}")
        pipe.execute()

    read_sec = measure_read(read_all, entries)
    r.flushdb()
    return RawBenchmarkResult("Redis", write_sec, read_sec, size)


# Register default benchmarks
register_benchmark(BenchmarkDefinition("RocksDB", bench_rocksdb))
register_benchmark(BenchmarkDefinition("LMDB", bench_lmdb))
register_benchmark(BenchmarkDefinition("SQLite", bench_sqlite))
register_benchmark(BenchmarkDefinition("Redis", bench_redis, requires_redis=True))


# ── Helpers ────────────────────────────────────────────────────────────────────
def extend_metrics(result: RawBenchmarkResult, entry_count: int, payload_bytes: int) -> ExtendedBenchmarkResult:
    seq_speed = float("inf") if result.read_sec == 0 else (payload_bytes / result.read_sec) / (1024**2)
    latency = 0.0 if entry_count == 0 else (result.read_sec / entry_count) * 1000
    return ExtendedBenchmarkResult(
        name=result.name,
        write_sec=result.write_sec,
        read_sec=result.read_sec,
        size_bytes=result.size_bytes,
        seq_read_mb_s=seq_speed,
        latency_ms=latency,
    )


def summarize_payload(entries: list[VectorEntry]) -> int:
    total = 0
    for e in entries:
        total += len(e.serialize())
    return total


def run_extended_single(n: int, dim: int, redis_url: str, no_redis: bool) -> list[ExtendedBenchmarkResult]:
    gc.collect()
    entries = generate_entries(n, dim)
    payload_bytes = summarize_payload(entries)
    tmp = tempfile.mkdtemp(prefix="vbench_ext_")
    results: list[ExtendedBenchmarkResult] = []

    try:
        for definition in BENCHMARKS:
            if definition.requires_redis and no_redis:
                print(f"  [skip] {definition.name}: redis disabled")
                continue
            gc.collect()
            try:
                raw = definition.runner(entries, tmp, redis_url)
            except RuntimeError as exc:
                print(f"  [skip] {definition.name}: {exc}")
                continue
            extended = extend_metrics(raw, len(entries), payload_bytes)
            results.append(extended)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return results


def print_results_table(rows: list[tuple[int | None, int | None, ExtendedBenchmarkResult]]) -> None:
    print("| N | Dim | Storage | Write (s) | Read (s) | Seq Read (MB/s) | Latency (ms/op) | Size |")
    print("|---|-----|---------|-----------|----------|-----------------|-----------------|------|")
    for n_val, dim_val, res in rows:
        n_str = f"{n_val//1000}k" if n_val else "-"
        dim_str = str(dim_val) if dim_val else "-"
        size_h = human_size(res.size_bytes)
        seq_speed = "∞" if res.seq_read_mb_s == float("inf") else f"{res.seq_read_mb_s:.2f}"
        print(
            f"| {n_str} | {dim_str} | {res.name} | {res.write_sec:.2f} | {res.read_sec:.2f} | "
            f"{seq_speed} | {res.latency_ms:.3f} | {size_h} |"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Extended vector DB benchmark suite.")
    parser.add_argument("-n", type=int, default=None, help="single run with N entries")
    parser.add_argument("-d", "--dim", type=int, default=None, help="single run with DIM")
    parser.add_argument("--redis-url", default="redis://localhost:6379/0")
    parser.add_argument("--no-redis", action="store_true")
    parser.add_argument("--full", action="store_true", help="run full matrix benchmark")
    args = parser.parse_args()

    if args.full:
        ns = [100_000, 200_000, 500_000, 1_000_000]
        dims = [128, 256]
        matrix: list[tuple[int, int, ExtendedBenchmarkResult]] = []
        for dim in dims:
            for n in ns:
                print(f"\n=== n={n:,} dim={dim} ===")
                results = run_extended_single(n, dim, args.redis_url, args.no_redis)
                for res in results:
                    matrix.append((n, dim, res))

        print("\n\n## Full Results Matrix\n")
        print_results_table(matrix)
    else:
        n = args.n or 100_000
        dim = args.dim or 128
        print(f"Generating {n:,} entries (dim={dim})...")
        results = run_extended_single(n, dim, args.redis_url, args.no_redis)
        print("\n## Results\n")
        print_results_table([(n, dim, res) for res in results])


if __name__ == "__main__":
    main()


