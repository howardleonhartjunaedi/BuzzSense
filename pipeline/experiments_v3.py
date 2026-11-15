"""v3: re-scan ALL raw audio and keep far more of each window (high-resolution spectrum).

Why: the v2 store (build_rag.py) took at most 20 windows per HumBugDB recording and squeezed each
0.62 s window into 80 numbers (40 broad mel bands, mean + std). Aedes and Culex wingbeats differ by
tens of Hz, and broad bands blur that.

What v3 extracts per 0.62 s window (8 kHz), all from the raw zips:
  m40   the v2 features (40 mel bands 50-4000 Hz, n_fft 512): mean + std          -> 80
  hr    96 log-spaced bands 100-3200 Hz (~3.7% wide), 1024-sample frames
        zero-padded to 2048 (3.9 Hz bins), hop 256: mean, std, 90th percentile     -> 288
  harm  levels at 2..8 x f0 relative to f0 (fine 16k FFT), plus log f0          -> 8
HumBugDB: EVERY non-overlapping window of every recording (v2 took <= 20 per recording);
each window remembers whether v2 would have taken it, so both index sizes are compared.

Protocol (unchanged from experiments.py, see the pipeline AGENTS.md):
  TUNE  leave-one-domain-out on BioDCASE train+val (hide D1, D3, D4 in turn) + HumBugDB train.
  PICK  best balanced recall among variants with Aedes false alarms <= 5%, before any test run.
  TEST  official test split once: v2 (current app) vs the chosen variant, with the majority and
        f0-band baselines, Aedes recall and false alarms, per domain, and per HumBugDB recording.

Writes <out>/v3.json (progress + results) and <out>/dashboard.html. Feature cache goes to --cache.
Usage (from Audio File/):
  .venv/Scripts/python.exe -I "../App Folder/pipeline/experiments_v3.py" --data data/raw --out data/rag_v3 --cache <dir>
"""
import argparse, csv, hashlib, json, os, re, shutil, sys, time, zipfile
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402

K = 15
CLASSES = ["aedes", "culex", "none", "other_mosquito"]
HR_N, HR_LO, HR_HI, HR_FRAME, HR_NFFT, HR_HOP = 96, 100.0, 3200.0, 1024, 2048, 256
H = 8
COLS = dict(m40_mean=(0, 40), m40_std=(40, 80), hr_mean=(80, 176), hr_std=(176, 272), hr_p90=(272, 368),
            harm=(368, 375), logf0=(375, 376))
DIM = 376


def _hr_fb():
    edges = np.geomspace(HR_LO * 0.965, HR_HI * 1.035, HR_N + 2)
    f = np.fft.rfftfreq(HR_NFFT, 1 / B.SR)
    fb = np.zeros((HR_N, len(f)), np.float32)
    for i in range(HR_N):
        l, c, r = edges[i:i + 3]
        w = np.clip(np.minimum((f - l) / (c - l), (r - f) / (r - c)), 0, None)
        if w.sum() == 0:
            w[np.argmin(np.abs(f - c))] = 1.0
        fb[i] = w / w.sum()
    return fb


HR_FB = _hr_fb()
HR_WIN = np.hanning(HR_FRAME).astype(np.float32)


def feat(x):
    """x: float32, 4960 samples at 8 kHz -> float32[376] (see COLS)."""
    x = x - x.mean()
    # v2 features, exactly as build_rag.features (tilt applied later, in embed)
    fr = np.lib.stride_tricks.sliding_window_view(x, B.N_FFT)[::B.HOP] * B.WIN
    P = np.abs(np.fft.rfft(fr, axis=1)) ** 2
    lm = np.log(P @ B.MEL.T + 1e-10); lm -= lm.mean()
    # high-resolution log bands
    fr2 = np.lib.stride_tricks.sliding_window_view(x, HR_FRAME)[::HR_HOP] * HR_WIN
    P2 = np.abs(np.fft.rfft(fr2, HR_NFFT, axis=1)) ** 2
    L = np.log(P2 @ HR_FB.T + 1e-10); L -= L.mean()
    # f0 and harmonic profile from one fine spectrum of the whole window
    S = np.abs(np.fft.rfft(x * np.hanning(len(x)), 16384)) ** 2
    f = np.fft.rfftfreq(16384, 1 / B.SR)
    f0 = float(B.F0_GRID[int(np.argmax(sum(np.interp(B.F0_GRID * h, f, S) for h in (1, 2, 3, 4))))])
    lv = []
    for h in range(1, H + 1):
        c = h * f0
        if c * 1.03 >= B.SR / 2:
            lv.append(np.nan); continue
        m = (f >= c * 0.97) & (f <= c * 1.03)
        lv.append(np.log(S[m].max() + 1e-12))
    lv = np.array(lv)
    harm = np.nan_to_num(lv[1:] - lv[0], nan=-10.0)
    return np.concatenate([lm.mean(0), lm.std(0), L.mean(0), L.std(0), np.percentile(L, 90, axis=0),
                           harm, [np.log(f0)]]).astype(np.float32)


# ----------------------------------------------------------------- extraction jobs
def job_b(args):
    path, stem, split = args
    m = re.match(r"S_(\d+)_D_(\d+)_", stem)
    try:
        x = B.load(path); x = np.pad(x, (0, max(0, B.CLIP - len(x))))[:B.CLIP]
        v = feat(x)
    except Exception as e:
        return None, f"{path}\t{e}"
    sp = B.BIODCASE_SPECIES[int(m[1])]
    return (v[None], [("biodcase", sp, B.class3(sp), "D" + m[2], split, stem, 0, True)]), None


def job_h(args):
    """Every non-overlapping 0.62 s window of the whole recording."""
    path, row = args
    split = "test" if int(hashlib.md5(row["id"].encode()).hexdigest(), 16) % 5 == 0 else "train"
    try:
        x = B.load(path)
    except Exception as e:
        return None, f"{path}\t{type(e).__name__}: {e}"
    n = len(x) // B.CLIP
    if n == 0:
        return (np.zeros((0, DIM), np.float32), []), None
    cap20 = set(np.unique(np.linspace(0, n - 1, min(n, B.HUMBUG_WINDOWS)).astype(int)).tolist())
    sp = row["species"].strip()
    c3 = B.class3(sp) if row["sound_type"] == "mosquito" else "none"
    dom = "HB_" + (row["country"] or "unknown")
    feats, meta = [], []
    for i in range(n):
        w = x[i * B.CLIP:(i + 1) * B.CLIP]
        if np.abs(w).max() < 1e-5:
            continue
        feats.append(feat(w))
        meta.append(("humbugdb", sp or row["sound_type"], c3, dom, split, "HB_" + row["id"], i, i in cap20))
    return (np.stack(feats) if feats else np.zeros((0, DIM), np.float32), meta), None


# ----------------------------------------------------------------- embeddings
def _tilt_proj(n):
    i = np.arange(n, dtype=np.float64)
    V = np.vander((i - i.mean()) / i.std(), 4)
    return (np.eye(n) - V @ np.linalg.pinv(V)).astype(np.float32)


T40, T96 = _tilt_proj(40), _tilt_proj(HR_N)


def col(F, name):
    a, b = COLS[name]
    return F[:, a:b].astype(np.float32)


VARIANTS = {
    "v2 (current app)": lambda F: np.hstack([col(F, "m40_mean") @ T40.T, col(F, "m40_std")]),
    "v3 hi-res":        lambda F: np.hstack([col(F, "hr_mean") @ T96.T, col(F, "hr_std")]),
    "v3 hi-res + p90":  lambda F: np.hstack([col(F, "hr_mean") @ T96.T, col(F, "hr_std"), col(F, "hr_p90") @ T96.T]),
    "v3 hi-res + harm": lambda F: np.hstack([col(F, "hr_mean") @ T96.T, col(F, "hr_std"),
                                             np.hstack([col(F, "harm"), col(F, "logf0")]) * np.sqrt(2 * HR_N / 8)]),
    "v3 hi-res + v2":   lambda F: np.hstack([col(F, "m40_mean") @ T40.T, col(F, "m40_std"),
                                             col(F, "hr_mean") @ T96.T, col(F, "hr_std")]),
}


def normalise(X, fit_idx):
    mu, sd = X[fit_idx].mean(0), X[fit_idx].std(0) + 1e-6
    E = (X - mu) / sd
    return (E / (np.linalg.norm(E, axis=1, keepdims=True) + 1e-9)).astype(np.float32)


def knn(E, q, idx, y, batch=384):
    out = np.empty(len(q), dtype=object)
    Ei, yi = E[idx], y[idx]
    for s in range(0, len(q), batch):
        S = E[q[s:s + batch]] @ Ei.T
        nn = np.argpartition(-S, K, axis=1)[:, :K]
        for j, row in enumerate(nn):
            out[s + j] = Counter(yi[row]).most_common(1)[0][0]
    return out


def scores(t, p):
    t, p = np.asarray(t), np.asarray(p)
    pos = t == "aedes"; neg = ~pos
    return dict(n=int(len(t)),
                aedes_recall=float((p[pos] == "aedes").mean()) if pos.any() else None,
                aedes_false_alarm=float((p[neg] == "aedes").mean()) if neg.any() else None,
                class3_acc=float((p == t).mean()) if len(t) else None,
                balanced_recall=float(np.mean([(p[t == c] == c).mean() for c in CLASSES if (t == c).any()])))


def r3(d):
    return {k: (round(v, 3) if isinstance(v, float) else v) for k, v in d.items()}


# ----------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--cache", required=True); ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 2))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True); os.makedirs(a.cache, exist_ok=True)
    shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard_v3.html"),
                os.path.join(a.out, "dashboard.html"))
    t0 = time.time()
    state = dict(status="running", stage="listing raw files", extract=dict(), lodo=[], chosen=None, test=None,
                 baselines=None, notes=[], log=[], started=time.strftime("%H:%M:%S"))
    res = os.path.join(a.out, "v3.json")

    def save(**kw):
        state.update(kw, elapsed_s=round(time.time() - t0), updated=time.strftime("%H:%M:%S"))
        tmp = res + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as fh:
                json.dump(state, fh, indent=1, default=str)
            for i in range(10):
                try:
                    os.replace(tmp, res); return
                except PermissionError:
                    time.sleep(0.05 * (i + 1))
        except OSError:
            pass                                    # progress is cosmetic: never fail the run

    def log(msg):
        state["log"].append(f"{time.time() - t0:7.0f}s  {msg}")
        print(msg, flush=True); save()

    cache = os.path.join(a.cache, f"v3_features{'_lim' + str(a.limit) if a.limit else ''}.npz")
    if os.path.exists(cache):
        z = np.load(cache, allow_pickle=False)
        F = z["F"]; meta = {k: z[k] for k in z.files if k != "F"}
        log(f"loaded cached features {F.shape} from {cache}")
    else:
        bzip = os.path.join(a.data, "biodcase", "Development_data.zip"); bz = zipfile.ZipFile(bzip)
        split_of = {}
        for name, s in (("Training_ids.txt", "train"), ("Validation_ids.txt", "val"), ("Test_ids.txt", "test")):
            for line in bz.read("Development_data/metadata/" + name).decode().splitlines():
                if line.strip(): split_of[line.strip()] = s
        bjobs = [(f"{bzip}::{n}", n.rsplit("/", 1)[1][:-4], split_of.get(n.rsplit("/", 1)[1][:-4], "unlisted"))
                 for n in sorted(bz.namelist()) if n.startswith("Development_data/raw_audio/") and n.endswith(".wav")]
        files = {}
        for i in range(1, 5):
            hz = os.path.join(a.data, "humbugdb", f"humbugdb_neurips_2021_{i}.zip")
            for n in zipfile.ZipFile(hz).namelist():
                if n.endswith(".wav"): files[n.rsplit("/", 1)[-1]] = f"{hz}::{n}"
        rows = list(csv.DictReader(open(os.path.join(a.data, "humbugdb", "neurips_2021_zenodo_0_0_1.csv"), encoding="utf-8")))
        hjobs = [(files[r["id"] + ".wav"], r) for r in rows if r["id"] + ".wav" in files]
        hjobs.sort(key=lambda j: -float(j[1].get("length") or 0))      # long recordings first: better load balance
        if a.limit:
            rng = np.random.default_rng(0)
            bjobs = [bjobs[i] for i in rng.choice(len(bjobs), a.limit, replace=False)]
            hjobs = [hjobs[i] for i in rng.choice(len(hjobs), max(1, a.limit // 10), replace=False)]
        hours = sum(float(r.get("length") or 0) for _, r in hjobs) / 3600
        state["extract"] = dict(biodcase_total=len(bjobs), biodcase_done=0, humbug_total=len(hjobs), humbug_done=0,
                                humbug_hours=round(hours, 1), windows=0, errors=0)
        log(f"raw files: {len(bjobs):,} BioDCASE clips, {len(hjobs):,} HumBugDB recordings ({hours:.1f} h), {a.workers} workers")
        feats, metas, errs = [], [], []
        ex_state = state["extract"]
        save(stage="scanning BioDCASE clips")
        with ProcessPoolExecutor(a.workers) as ex:
            for k, (out, err) in enumerate(ex.map(job_b, bjobs, chunksize=256), 1):
                if err: errs.append(err)
                else: feats.append(out[0]); metas += out[1]
                if k % 2000 == 0 or k == len(bjobs):
                    ex_state.update(biodcase_done=k, windows=len(metas), errors=len(errs)); save()
        log(f"BioDCASE done: {len(metas):,} windows")
        save(stage="scanning every HumBugDB window")
        with ProcessPoolExecutor(a.workers) as ex:
            for k, (out, err) in enumerate(ex.map(job_h, hjobs, chunksize=2), 1):
                if err: errs.append(err)
                else: feats.append(out[0]); metas += out[1]
                if k % 25 == 0 or k == len(hjobs):
                    ex_state.update(humbug_done=k, windows=len(metas), errors=len(errs)); save()
        F = np.concatenate(feats).astype(np.float16)
        cols = list(zip(*metas))
        meta = dict(src=np.array(cols[0]), sp=np.array(cols[1]), y=np.array(cols[2]), dom=np.array(cols[3]),
                    split=np.array(cols[4]), rec=np.array(cols[5]), win=np.array(cols[6], dtype=np.int32),
                    cap20=np.array(cols[7], dtype=bool))
        np.savez(cache, F=F, **meta)
        if errs:
            with open(os.path.join(a.out, "errors.txt"), "w", encoding="utf-8") as fh:
                fh.write("\n".join(errs))
        log(f"features {F.shape}, {len(errs)} unreadable files, cached to {cache}")

    src, y, dom, split, rec, cap20 = (meta[k] for k in ("src", "y", "dom", "split", "rec", "cap20"))
    is_b, is_h = src == "biodcase", src == "humbugdb"
    state["counts"] = dict(
        windows=int(len(y)), biodcase=int(is_b.sum()), humbug_all=int(is_h.sum()), humbug_v2_cap=int((is_h & cap20).sum()),
        by_class={c: int((y == c).sum()) for c in CLASSES},
        by_split={s: int((split == s).sum()) for s in ("train", "val", "test", "unlisted")})
    save()

    # ---------------- TUNE: leave-one-domain-out (BioDCASE train+val; HumBugDB train in the index)
    pool = np.where(is_b & np.isin(split, ["train", "val"]))[0]
    hold = [d for d in ("D1", "D3", "D4") if (dom[pool] == d).sum() >= 20]
    lodo = []
    for vname, fn in VARIANTS.items():
        X = fn(F)
        for hb in ("v2 cap (20/recording)", "all windows"):
            name = f"{vname} | HumBugDB {hb}"
            hb_tr = np.where(is_h & (split == "train") & (cap20 if hb.startswith("v2") else True))[0]
            save(stage=f"tuning: {name}")
            per = []
            for d in hold:
                q = pool[dom[pool] == d]
                idx = np.concatenate([pool[dom[pool] != d], hb_tr])
                E = normalise(X, idx)
                per.append(r3(scores(y[q], knn(E, q, idx, y))) | {"domain": d})
            avg = {k: float(np.mean([p[k] for p in per if p[k] is not None]))
                   for k in ("aedes_recall", "aedes_false_alarm", "class3_acc", "balanced_recall")}
            lodo.append(r3(dict(variant=name, features=vname, humbug=hb, dims=int(X.shape[1]), **avg)) | {"per_domain": per})
            log(f"tune {name}: {r3(avg)}")
            save(lodo=lodo)

    ok = [r for r in lodo if r["aedes_false_alarm"] <= 0.05]
    chosen = max(ok or lodo, key=lambda r: r["balanced_recall"])
    if not ok:
        state["notes"].append("no variant kept Aedes false alarms <= 5% in tuning; picked best balanced recall")
    state["notes"].append(f"chosen on leave-one-domain-out, before the test run: {chosen['variant']}")
    save(chosen=chosen["variant"])
    log(f"chosen: {chosen['variant']}")

    # ---------------- TEST once: official test split
    q = np.where(split == "test")[0]
    test = []
    runs = [("v2 (current app)", "v2 cap (20/recording)"), (chosen["features"], chosen["humbug"])]
    for vname, hb in dict.fromkeys(runs):
        name = f"{vname} | HumBugDB {hb}"
        save(stage=f"final test: {name}")
        hb_tr = np.where(is_h & (split == "train") & (cap20 if hb.startswith("v2") else True))[0]
        idx = np.concatenate([np.where(is_b & (split == "train"))[0], hb_tr])   # index = train split, as the app
        qq = q if not hb.startswith("v2") else q[~is_h[q] | cap20[q]]           # v2 never had the other windows
        E = normalise(VARIANTS[vname](F), idx)
        p = knn(E, qq, idx, y)
        per = [r3(scores(y[qq][dom[qq] == d], p[dom[qq] == d])) | {"domain": d} for d in sorted(set(dom[qq]))]
        # per HumBugDB recording: majority vote of its windows (what the app does with one recording)
        votes = defaultdict(Counter)
        for i, pred in zip(qq, p):
            if is_h[i]: votes[rec[i]][pred] += 1
        truth = {rec[i]: y[i] for i in qq if is_h[i]}
        rk = sorted(votes)
        recording = r3(scores([truth[r] for r in rk], [votes[r].most_common(1)[0][0] for r in rk]))
        # the same windows scored on the queries v2 can see, so the two rows compare like with like
        same = np.isin(qq, q[~is_h[q] | cap20[q]])
        test.append(dict(variant=name, index_size=int(len(idx)), dims=int(E.shape[1]),
                         overall=r3(scores(y[qq], p)), on_v2_queries=r3(scores(y[qq][same], p[same])),
                         recording_level=recording, per_domain=per))
        log(f"test {name}: {test[-1]['overall']}  per recording: {recording}")
        save(test=test)

    # baselines on the full test set
    tr = np.where(split == "train")[0]
    maj = Counter(y[tr]).most_common(1)[0][0]
    f0 = np.exp(col(F, "logf0")[:, 0])
    lo, hi = np.percentile(f0[tr][y[tr] == "aedes"], [10, 90])
    rule = np.where((f0[q] >= lo) & (f0[q] <= hi), "aedes", "other")
    save(baselines=[dict(method=f"majority class ({maj})", **r3(scores(y[q], np.full(len(q), maj)))),
                    dict(method=f"f0 band rule {lo:.0f}-{hi:.0f} Hz", **r3(scores(y[q], rule)))])
    save(status="done", stage="done")
    log("done")


if __name__ == "__main__":
    main()
