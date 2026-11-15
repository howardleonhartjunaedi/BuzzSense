"""Export a parity fixture so the Kotlin port of features() can be checked against Python.

For a few test-split clips (BioDCASE + HumBugDB) it writes the 8 kHz window, the raw 80-d
features, the normalised embedding (with feature_spec mu/sd), the f0, and the embedding stored in
the sqlite-vec store. Read-only on data/raw and data/rag.

Usage (from Audio File/): python -I "../App Folder/pipeline/export_parity_fixture.py" --data data/raw --rag data/rag --out <fixture.json> [--n 6]
"""
import argparse, json, os, sqlite3, sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("--data", required=True)
ap.add_argument("--rag", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--n", type=int, default=6)
a = ap.parse_args()

spec = json.load(open(os.path.join(a.rag, "feature_spec.json")))
mu, sd = np.array(spec["mu"], np.float32), np.array(spec["sd"], np.float32)

import sqlite_vec  # noqa: E402
c = sqlite3.connect(f"file:{os.path.join(a.rag, 'jumantik_rag.sqlite')}?mode=ro", uri=True)
c.enable_load_extension(True); sqlite_vec.load(c); c.enable_load_extension(False)

picked = []
for src in ("biodcase", "humbugdb"):
    for cls in ("aedes", "culex", "none", "other_mosquito"):
        row = c.execute("SELECT id, clip_id, path, offset_s, class3 FROM clips WHERE source=? AND class3=? AND split='test' "
                        "ORDER BY id LIMIT 1", (src, cls)).fetchone()
        if row:
            picked.append(row)
picked = picked[: a.n]

cases = []
for rid, clip_id, path, offset_s, cls in picked:
    zp, _, member = path.partition("::")
    full = os.path.join(a.data, zp) + ("::" + member if member else "")
    if clip_id.startswith("HB_"):
        idx = int(clip_id.rsplit("_", 1)[1])
        x = dict(B._windows(full))[idx]
    else:
        x = B.load(full)
        x = np.pad(x, (0, max(0, B.CLIP - len(x))))[: B.CLIP]
    raw, sc = B.features(x)
    e = (raw - mu) / sd
    e = e / (np.linalg.norm(e) + 1e-9)
    stored = np.frombuffer(c.execute("SELECT embedding FROM clips_vec WHERE id=?", (rid,)).fetchone()[0], np.float32)
    cases.append(dict(clip_id=clip_id, class3=cls, samples=x.astype(np.float32).tolist(), raw=raw.tolist(),
                      embedding=e.astype(np.float32).tolist(), stored_embedding=stored.tolist(), f0_hz=sc["f0_hz"]))
    print(f"{clip_id:28s} {cls:15s} f0={sc['f0_hz']:.0f}  |e-stored|={np.abs(e - stored).max():.2e}")

os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
json.dump(dict(spec_version=spec.get("version"), cases=cases), open(a.out, "w"))
print("wrote", a.out, len(cases), "cases")
