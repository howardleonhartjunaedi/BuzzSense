"""Screen pretrained audio embeddings against v2 on a sample, before any full run.

Same protocol as experiments.py / experiments_v3.py, on a stratified sample:
  queries  up to --q windows each from BioDCASE train+val domains D1, D3, D4 (held out in turn)
  index    a stratified sample of the other BioDCASE train+val windows + HumBugDB train (v2 cap)
Every method sees exactly the same windows, so the comparison is like for like. No test data.

Methods (embeddings, then the same cosine kNN k=15):
  v2       the app's features today (from the v3 feature cache)
  yamnet   YAMNet (Google, AudioSet), 1024-d, 16 kHz, window looped to 1.0 s, frame mean
  mn10     EfficientAT mn10_as (AudioSet), 960-d pooled layer, 32 kHz, window looped to 1.0 s
  perch    Perch v2 backbone (Google bioacoustics), 1536-d, 32 kHz, window looped to 5.0 s
  <m>+v2   both, each half L2-normalised then concatenated
Each 0.62 s window is RMS-normalised before the model (gain invariance, like v2).

Usage (from Audio File/):
  .venv/Scripts/python.exe -I "../App Folder/pipeline/pretrained_screen.py" --data data/raw \
      --features <v3 cache>/v3_features.npz --models models/pretrained --out data/rag_pretrained
"""
import argparse, json, os, sys, time, zipfile
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from scipy.signal import resample_poly

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402
import experiments_v3 as V3  # noqa: E402

MODELS = {
    "yamnet": dict(file="niobures__YAMNet/yamnetonnx/yamnet.onnx", sr=16000, sec=1.0, inp="waveform", out="output_1", batch=False),
    "mn10": dict(file="Chris-Kode__efficientat-mn10-as-onnx/mn10_with_embedding.onnx", sr=32000, sec=1.0, inp="samples", out="view_10", batch=True),
    "perch": dict(file="biodiversica__Perch-onnx-backbone/perch_v2_backbone.onnx", sr=32000, sec=5.0, inp="inputs", out="embedding", batch=True),
}
_S = {}


def prep(w, sr, sec):
    w = w - w.mean()
    w = w / (np.sqrt(np.mean(w ** 2)) + 1e-9) * 0.05            # same loudness for every window
    up = resample_poly(w, sr // B.SR, 1).astype(np.float32)
    n = int(sr * sec)
    return np.tile(up, int(np.ceil(n / len(up))))[:n]            # loop the buzz to the model's length


def embed_windows(ws, models_dir, names):
    import onnxruntime as ort
    out = {}
    for name in names:
        cfg = MODELS[name]
        if name not in _S:
            so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.inter_op_num_threads = 1
            _S[name] = ort.InferenceSession(os.path.join(models_dir, cfg["file"]), so, providers=["CPUExecutionProvider"])
        s = _S[name]
        X = [prep(w, cfg["sr"], cfg["sec"]) for w in ws]
        if cfg["batch"]:
            E = np.concatenate([s.run([cfg["out"]], {cfg["inp"]: np.stack(X[i:i + 16])})[0] for i in range(0, len(X), 16)])
        else:
            E = np.stack([s.run([cfg["out"]], {cfg["inp"]: x})[0].mean(0) for x in X])
        out[name] = E.reshape(len(ws), -1).astype(np.float32)
    return out


def job(args):
    """One source file: load once, cut the requested windows, embed with every model."""
    path, wins, models_dir, names = args
    try:
        x = B.load(path)
    except Exception as e:
        return None, f"{path}\t{e}"
    ws = []
    for i in wins:
        w = x[i * B.CLIP:(i + 1) * B.CLIP]
        ws.append(np.pad(w, (0, B.CLIP - len(w))))
    return embed_windows(ws, models_dir, names), None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--features", required=True)
    ap.add_argument("--models", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--q", type=int, default=1500); ap.add_argument("--index", type=int, default=24000)
    ap.add_argument("--hb", type=int, default=8000); ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--methods", default="yamnet,mn10,perch")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    names = a.methods.split(",")
    t0 = time.time()
    state = dict(status="running", stage="sampling", done=0, total=0, results=[], log=[], methods=names)
    res = os.path.join(a.out, "screen.json")

    def save(**kw):
        state.update(kw, elapsed_s=round(time.time() - t0), updated=time.strftime("%H:%M:%S"))
        try:
            with open(res + ".tmp", "w", encoding="utf-8") as fh:
                json.dump(state, fh, indent=1)
            for i in range(10):
                try:
                    os.replace(res + ".tmp", res); return
                except PermissionError:
                    time.sleep(0.05 * (i + 1))
        except OSError:
            pass

    def log(m):
        state["log"].append(f"{time.time() - t0:6.0f}s  {m}"); print(m, flush=True); save()

    z = np.load(a.features)
    F, src, y, dom, split, rec, win, cap20 = (z[k] for k in ("F", "src", "y", "dom", "split", "rec", "win", "cap20"))
    sp = z["sp"]
    rng = np.random.default_rng(0)
    is_b = src == "biodcase"
    pool = np.where(is_b & np.isin(split, ["train", "val"]))[0]
    hold = ["D1", "D3", "D4"]
    queries = {d: rng.choice(pool[dom[pool] == d], min(a.q, int((dom[pool] == d).sum())), replace=False) for d in hold}
    rest = pool[~np.isin(dom[pool], hold)]
    # stratified index sample: equal share per (species, domain) group
    groups = defaultdict(list)
    for i in rest: groups[(sp[i], dom[i])].append(i)
    per = max(1, a.index // len(groups))
    idx_b = np.concatenate([rng.choice(g, min(per, len(g)), replace=False) for g in groups.values()])
    # the held-out domains' other windows also join the index when they are not the query domain
    other_hold = {d: np.setdiff1d(pool[dom[pool] == d], queries[d]) for d in hold}
    hold_extra = {d: rng.choice(other_hold[d], min(len(other_hold[d]), per * 4), replace=False) for d in hold}
    hb = np.where(~is_b & (split == "train") & cap20)[0]
    idx_h = rng.choice(hb, min(a.hb, len(hb)), replace=False)
    sample = np.unique(np.concatenate([idx_b, idx_h, *queries.values(), *hold_extra.values()]))
    log(f"sample: {len(sample):,} windows ({len(idx_b):,} BioDCASE index, {len(idx_h):,} HumBugDB, "
        f"queries {', '.join(f'{d} {len(q)}' for d, q in queries.items())})")

    # source paths for the sampled windows
    bzip = os.path.join(a.data, "biodcase", "Development_data.zip")
    bpath = {n.rsplit("/", 1)[1][:-4]: f"{bzip}::{n}" for n in zipfile.ZipFile(bzip).namelist() if n.endswith(".wav")}
    hpath = {}
    for i in range(1, 5):
        hz = os.path.join(a.data, "humbugdb", f"humbugdb_neurips_2021_{i}.zip")
        for n in zipfile.ZipFile(hz).namelist():
            if n.endswith(".wav"): hpath["HB_" + n.rsplit("/", 1)[-1][:-4]] = f"{hz}::{n}"
    by_file = defaultdict(list)
    for i in sample:
        by_file[bpath[rec[i]] if is_b[i] else hpath[rec[i]]].append(int(i))
    jobs = [(p, [int(win[i]) for i in ii], a.models, names) for p, ii in by_file.items()]
    order = [ii for ii in by_file.values()]
    save(stage="embedding sample windows", total=len(sample))
    E = {n: np.zeros((len(F), 0), np.float32) for n in names}
    got = {n: {} for n in names}
    done, errs = 0, []
    with ProcessPoolExecutor(a.workers) as ex:
        for ii, (out, err) in zip(order, ex.map(job, jobs, chunksize=16)):
            if err: errs.append(err); continue
            for n in names:
                for k, i in enumerate(ii): got[n][i] = out[n][k]
            done += len(ii)
            if done % 500 < len(ii): save(done=done)
    keep = np.array([i for i in sample if all(i in got[n] for n in names)])
    log(f"embedded {len(keep):,} windows, {len(errs)} unreadable files")

    def mat(name):
        if name == "v2":
            return V3.VARIANTS["v2 (current app)"](F[keep].astype(np.float32))
        return np.stack([got[name][i] for i in keep])

    def unit(X, fit):
        mu, sd = X[fit].mean(0), X[fit].std(0) + 1e-6
        X = (X - mu) / sd
        return X / (np.linalg.norm(X, axis=1, keepdims=True) + 1e-9)

    pos = {int(i): k for k, i in enumerate(keep)}
    yk, domk = y[keep], dom[keep]
    qset = {d: np.array([pos[i] for i in queries[d] if i in pos]) for d in hold}
    methods = ["v2"] + names + [f"{n}+v2" for n in names]
    for m in methods:
        save(stage=f"kNN, leave one site out: {m}")
        per_d = []
        for d in hold:
            q = qset[d]
            idx = np.setdiff1d(np.arange(len(keep)), np.concatenate([q, np.where(domk == d)[0]]))
            parts = [m] if "+" not in m else m.split("+")
            X = np.hstack([unit(mat(p), idx) for p in parts]).astype(np.float32)
            X /= np.linalg.norm(X, axis=1, keepdims=True) + 1e-9
            p = V3.knn(X, q, idx, yk)
            per_d.append(V3.r3(V3.scores(yk[q], p)) | {"domain": d})
        avg = {k: round(float(np.mean([r[k] for r in per_d if r[k] is not None])), 3)
               for k in ("aedes_recall", "aedes_false_alarm", "class3_acc", "balanced_recall")}
        state["results"].append(dict(method=m, dims=int(sum(mat(p).shape[1] for p in m.split("+"))), **avg, per_domain=per_d))
        log(f"{m}: {avg}")
    save(status="done", stage="done")


if __name__ == "__main__":
    main()
