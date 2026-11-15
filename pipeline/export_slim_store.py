"""Export the app's on-device acoustic store: the train split of jumantik_rag.sqlite only.

The app's kNN (AcousticStore.nearest) searches ``split = 'train'`` and reads only
clip_id, class3 and species, so this store gives the same neighbours as the full one
while dropping the val/test clips, the FTS tables and the profiles.

Usage (from Audio File/):
  .venv/Scripts/python.exe -I "../App Folder/pipeline/export_slim_store.py" --rag data/rag --out data/rag_slim
Then copy data/rag_slim/jumantik_rag.sqlite to App Folder/app/src/main/assets/.
"""
from __future__ import annotations

import argparse
import sqlite3
import struct
import sys
from pathlib import Path

import numpy as np
import sqlite_vec


def connect(path: Path) -> sqlite3.Connection:
    db = sqlite3.connect(path)
    db.enable_load_extension(True)
    sqlite_vec.load(db)
    db.enable_load_extension(False)
    return db


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rag", type=Path, default=Path("data/rag"))
    ap.add_argument("--out", type=Path, default=Path("data/rag_slim"))
    ap.add_argument("--split", default="train")
    args = ap.parse_args()

    src_path = args.rag / "jumantik_rag.sqlite"
    if not src_path.exists():
        sys.exit(f"missing {src_path}")
    args.out.mkdir(parents=True, exist_ok=True)
    out_path = args.out / "jumantik_rag.sqlite"
    if args.out.resolve() == args.rag.resolve():
        sys.exit("--out must differ from --rag (the full store is read by the eval)")
    tmp = out_path.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)

    src = connect(src_path)
    dim = len(src.execute("SELECT embedding FROM clips_vec LIMIT 1").fetchone()[0]) // 4
    dst = connect(tmp)
    dst.executescript("""
        CREATE TABLE clips(id INTEGER PRIMARY KEY, clip_id TEXT, species TEXT, class3 TEXT, split TEXT);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    """)
    dst.execute(f"CREATE VIRTUAL TABLE clips_vec USING vec0(id INTEGER PRIMARY KEY, "
                f"embedding float[{dim}] distance_metric=cosine, class3 TEXT, split TEXT)")

    rows = src.execute(
        "SELECT c.id, c.clip_id, c.species, c.class3, c.split, v.embedding "
        "FROM clips c JOIN clips_vec v ON v.id = c.id WHERE c.split = ? ORDER BY c.id", (args.split,))
    n = 0
    batch: list[tuple] = []
    for cid, clip_id, species, class3, split, emb in rows:
        batch.append((cid, clip_id, species, class3, split, emb))
        if len(batch) == 5000:
            n += flush(dst, batch)
    n += flush(dst, batch)

    expected = src.execute("SELECT count(*) FROM clips WHERE split = ?", (args.split,)).fetchone()[0]
    if n != expected:
        sys.exit(f"exported {n} rows, expected {expected}")
    vec_version = src.execute("SELECT vec_version()").fetchone()[0]
    dst.executemany("INSERT INTO meta(key, value) VALUES (?, ?)", [
        ("source", str(src_path)), ("split", args.split), ("rows", str(n)),
        ("dimensions", str(dim)), ("sqlite_vec", vec_version),
    ])
    dst.commit()
    check_same_neighbours(src, dst, args.split)
    dst.execute("VACUUM")
    dst.close()
    src.close()
    tmp.replace(out_path)
    print(f"wrote {out_path}: {n} {args.split} clips, {out_path.stat().st_size / 1e6:.1f} MB (sqlite-vec {vec_version})")


def flush(dst: sqlite3.Connection, batch: list[tuple]) -> int:
    dst.executemany("INSERT INTO clips(id, clip_id, species, class3, split) VALUES (?,?,?,?,?)", [b[:5] for b in batch])
    dst.executemany("INSERT INTO clips_vec(id, embedding, class3, split) VALUES (?,?,?,?)",
                    [(b[0], b[5], b[3], b[4]) for b in batch])
    k = len(batch)
    batch.clear()
    return k


def check_same_neighbours(src: sqlite3.Connection, dst: sqlite3.Connection, split: str, probes: int = 25, k: int = 15) -> None:
    """The app's query must return identical neighbours from both stores."""
    q = ("SELECT v.id FROM clips_vec v WHERE v.embedding MATCH ? AND k = ? AND v.split = ? ORDER BY v.distance")
    rng = np.random.default_rng(0)
    for (emb,) in src.execute("SELECT embedding FROM clips_vec WHERE split != ? ORDER BY random() LIMIT ?", (split, probes)):
        v = np.frombuffer(emb, dtype="<f4") + rng.normal(0, 1e-3, size=len(emb) // 4).astype("<f4")
        blob = struct.pack(f"<{len(v)}f", *v.tolist())
        a = [r[0] for r in src.execute(q, (blob, k, split))]
        b = [r[0] for r in dst.execute(q, (blob, k, split))]
        if a != b:
            sys.exit(f"neighbour mismatch: {a[:5]} vs {b[:5]}")
    print(f"same {k} neighbours as the full store for {probes} probe clips")


if __name__ == "__main__":
    main()
