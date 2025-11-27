#!/usr/bin/env python3
"""Benchmark: RocksDB vs LMDB vs SQLite vs Redis for VectorEntry data."""

import argparse, os, shutil, sqlite3, struct, tempfile, time, random
from dataclasses import dataclass
import numpy as np

RESULTS = []
WARMUP_READS = 1000

@dataclass
class VectorEntry:
    id: int
    embedding: np.ndarray
    original_data: str
    database_id: str
    created_at: int

    def serialize(self) -> bytes:
        emb = self.embedding.tobytes()
        data = self.original_data.encode()
        db_id = self.database_id.encode()
        return struct.pack(f"<q{len(emb)}sI{len(data)}sI{len(db_id)}sq",
                           self.id, emb, len(data), data, len(db_id), db_id, self.created_at)

def generate_entries(n: int, dim: int) -> list[VectorEntry]:
    ts = int(time.time() * 1000)
    return [VectorEntry(i, np.random.rand(dim).astype(np.float32), f"data_{i}", "bench_db", ts) for i in range(n)]

def dir_size(path: str) -> int:
    return sum(os.path.getsize(os.path.join(d, f)) for d, _, files in os.walk(path) for f in files)

def human_size(b: int) -> str:
    for u in ("B", "KB", "MB", "GB"):
        if b < 1024: return f"{b:.1f} {u}"
        b /= 1024
    return f"{b:.1f} TB"

# ── RocksDB ──────────────────────────────────────────────────────────────────
def bench_rocksdb(entries, tmp):
    try:
        from rocksdict import Rdict
    except ImportError:
        print("  [skip] rocksdict"); return

    path = os.path.join(tmp, "rocksdb")
    db = Rdict(path)
    t0 = time.perf_counter()
    for e in entries: db[struct.pack("<q", e.id)] = e.serialize()
    db.flush()
    write_sec = time.perf_counter() - t0
    db.close()
    size = dir_size(path)

    db = Rdict(path)
    # warmup
    for i in random.sample(range(len(entries)), min(WARMUP_READS, len(entries))):
        _ = db[struct.pack("<q", entries[i].id)]
    t0 = time.perf_counter()
    for e in entries: _ = db[struct.pack("<q", e.id)]
    read_sec = time.perf_counter() - t0
    db.close()
    RESULTS.append(("RocksDB", write_sec, read_sec, size))

# ── LMDB ─────────────────────────────────────────────────────────────────────
def bench_lmdb(entries, tmp):
    try:
        import lmdb
    except ImportError:
        print("  [skip] lmdb"); return

    path = os.path.join(tmp, "lmdb")
    os.makedirs(path)
    env = lmdb.open(path, map_size=10 * 1024**3)

    t0 = time.perf_counter()
    with env.begin(write=True) as txn:
        for e in entries: txn.put(struct.pack("<q", e.id), e.serialize())
    write_sec = time.perf_counter() - t0
    env.sync()
    size = dir_size(path)

    # warmup
    with env.begin() as txn:
        for i in random.sample(range(len(entries)), min(WARMUP_READS, len(entries))):
            _ = txn.get(struct.pack("<q", entries[i].id))
    t0 = time.perf_counter()
    with env.begin() as txn:
        for e in entries: _ = txn.get(struct.pack("<q", e.id))
    read_sec = time.perf_counter() - t0
    env.close()
    RESULTS.append(("LMDB", write_sec, read_sec, size))

# ── SQLite ───────────────────────────────────────────────────────────────────
def bench_sqlite(entries, tmp):
    path = os.path.join(tmp, "sqlite.db")
    conn = sqlite3.connect(path)
    conn.execute("CREATE TABLE vectors (id INTEGER PRIMARY KEY, data BLOB)")

    t0 = time.perf_counter()
    conn.executemany("INSERT INTO vectors VALUES (?, ?)", [(e.id, e.serialize()) for e in entries])
    conn.commit()
    write_sec = time.perf_counter() - t0
    conn.close()
    size = os.path.getsize(path)

    conn = sqlite3.connect(path)
    cur = conn.cursor()
    # warmup
    for i in random.sample(range(len(entries)), min(WARMUP_READS, len(entries))):
        cur.execute("SELECT data FROM vectors WHERE id=?", (entries[i].id,))
        cur.fetchone()
    t0 = time.perf_counter()
    for e in entries:
        cur.execute("SELECT data FROM vectors WHERE id=?", (e.id,))
        cur.fetchone()
    read_sec = time.perf_counter() - t0
    conn.close()
    RESULTS.append(("SQLite", write_sec, read_sec, size))

# ── Redis ────────────────────────────────────────────────────────────────────
def bench_redis(entries, url):
    try:
        import redis
        r = redis.from_url(url)
        r.ping()
        key_prefix = "vbench:"
        r.flushdb()

        t0 = time.perf_counter()
        pipe = r.pipeline()
        for e in entries: pipe.set(f"{key_prefix}{e.id}", e.serialize())
        pipe.execute()
        write_sec = time.perf_counter() - t0

        info = r.info("memory")
        size = info.get("used_memory_dataset", info.get("used_memory", 0))

        # warmup
        pipe = r.pipeline()
        for i in random.sample(range(len(entries)), min(WARMUP_READS, len(entries))):
            pipe.get(f"{key_prefix}{entries[i].id}")
        pipe.execute()

        t0 = time.perf_counter()
        pipe = r.pipeline()
        for e in entries: pipe.get(f"{key_prefix}{e.id}")
        pipe.execute()
        read_sec = time.perf_counter() - t0

        r.flushdb()
        RESULTS.append(("Redis", write_sec, read_sec, size))
    except Exception as e:
        print(f"  [skip] Redis: {e}")

# ── Main ─────────────────────────────────────────────────────────────────────
def run_single(n, dim, redis_url, no_redis):
    global RESULTS
    RESULTS = []
    entries = generate_entries(n, dim)
    tmp = tempfile.mkdtemp(prefix="vbench_")
    try:
        bench_rocksdb(entries, tmp)
        bench_lmdb(entries, tmp)
        bench_sqlite(entries, tmp)
        if not no_redis: bench_redis(entries, redis_url)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return RESULTS.copy()

def main():
    p = argparse.ArgumentParser()
    p.add_argument("-n", type=int, default=None, help="single run with N entries")
    p.add_argument("-d", "--dim", type=int, default=None, help="single run with DIM")
    p.add_argument("--redis-url", default="redis://localhost:6379/0")
    p.add_argument("--no-redis", action="store_true")
    p.add_argument("--full", action="store_true", help="run full matrix benchmark")
    args = p.parse_args()

    if args.full:
        ns = [100_000, 200_000, 500_000, 1_000_000]
        dims = [128, 256]
        all_results = []
        for dim in dims:
            for n in ns:
                print(f"\n=== n={n:,} dim={dim} ===")
                results = run_single(n, dim, args.redis_url, args.no_redis)
                for name, w, r, s in results:
                    all_results.append((n, dim, name, w, r, s))
        
        print("\n\n## Full Results Matrix\n")
        print("| N | Dim | Storage | Write (s) | Read (s) | Size |")
        print("|---|-----|---------|-----------|----------|------|")
        for n, dim, name, w, r, s in all_results:
            print(f"| {n//1000}k | {dim} | {name} | {w:.2f} | {r:.2f} | {human_size(s)} |")
    else:
        n = args.n or 100_000
        dim = args.dim or 128
        print(f"Generating {n:,} entries (dim={dim})...")
        results = run_single(n, dim, args.redis_url, args.no_redis)
        print("\n## Results\n")
        print("| Storage | Write (s) | Read (s) | Size |")
        print("|---------|-----------|----------|------|")
        for name, w, r, s in results:
            print(f"| {name} | {w:.2f} | {r:.2f} | {human_size(s)} |")

if __name__ == "__main__":
    main()
