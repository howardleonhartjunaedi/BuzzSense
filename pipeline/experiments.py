"""Steps 1-3 to make retrieval hold up on recording setups it never saw.

Protocol (so the D2 number stays honest):
  * TUNE  = leave-one-domain-out on BioDCASE train+val only. Hide D1, D3, D4 in turn
            (D5 is the bulk of the index, D2 is reserved). Score = average over held-out domains.
  * PICK  = best variant by that score, with Aedes false alarms <= 5%. Chosen BEFORE the test is run.
  * TEST  = official test split, run once, baseline vs chosen. Per-domain table, D2 called out.

Variants
  base     current store: log-mel mean+std per clip, z-scored, cosine
  s1_tilt  step 1, deployable: remove each clip's smooth spectral envelope (cubic fit over mel bands),
           keeping peaks. A mic's colouration is smooth; wingbeat harmonics are narrow peaks.
  s1_dom   step 1, transductive reference: subtract each domain's mean log-mel. Needs many clips from
           the same setup, so it cannot run in the app on one recording. Reported, never chosen.
  s2_harm  step 2: levels at 2..8 x f0 relative to f0, plus log f0 (invariant to a smooth channel)
  s12      s1_tilt + s2_harm concatenated
  +bal     step 3 (applied to every variant above as a second row): cap each species x domain at
           CAP clips in the index and weight votes by 1 / class share, so D5 cannot outvote everything.

Usage (from Audio File/): python -I "../App Folder/pipeline/experiments.py" --data data/raw --out <rag dir> [--limit N]
"""
import argparse, json, os, sys, time
from collections import Counter
from concurrent.futures import ProcessPoolExecutor

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402

K, CAP, H = 15, 3000, 8
CLASSES = ["aedes", "culex", "none", "other_mosquito"]


# ----------------------------------------------------------------- features
def feat(x):
    x = x - x.mean()
    frames = np.lib.stride_tricks.sliding_window_view(x, B.N_FFT)[::B.HOP] * B.WIN
    P = np.abs(np.fft.rfft(frames, axis=1)) ** 2
    lm = np.log(P @ B.MEL.T + 1e-10)
    lm -= lm.mean()
    S = np.abs(np.fft.rfft(x * np.hanning(len(x)), 16384)) ** 2
    f = np.fft.rfftfreq(16384, 1 / B.SR)
    score = sum(np.interp(B.F0_GRID * h, f, S) for h in (1, 2, 3, 4))
    f0 = float(B.F0_GRID[int(np.argmax(score))])
    lv = []
    for h in range(1, H + 1):
        c = h * f0
        if c * 1.03 >= B.SR / 2:
            lv.append(np.nan); continue
        m = (f >= c * 0.97) & (f <= c * 1.03)
        lv.append(np.log(S[m].max() + 1e-12))
    lv = np.array(lv)
    harm = np.nan_to_num(lv[1:] - lv[0], nan=-10.0)          # 7 dims, relative to fundamental
    return np.concatenate([lm.mean(0), lm.std(0), harm, [np.log(f0)]]).astype(np.float32)


def job_b(args):
    path, stem, split = args
    import re
    m = re.match(r"S_(\d+)_D_(\d+)_", stem)
    try:
        x = B.load(path); x = np.pad(x, (0, max(0, B.CLIP - len(x))))[:B.CLIP]
        v = feat(x)
    except Exception:
        return []
    sp = B.BIODCASE_SPECIES[int(m[1])]
    return [(v, "biodcase", sp, B.class3(sp), "D" + m[2], split)]


def job_h(args):
    path, row = args
    import hashlib
    split = "test" if int(hashlib.md5(row["id"].encode()).hexdigest(), 16) % 5 == 0 else "train"
    try:
        wins = list(B._windows(path))
    except Exception:
        return []
    sp = row["species"].strip()
    c3 = B.class3(sp) if row["sound_type"] == "mosquito" else "none"
    return [(feat(w), "humbugdb", sp or row["sound_type"], c3, "HB_" + (row["country"] or "unknown"), split)
            for _, w in wins if np.abs(w).max() >= 1e-5]


# ----------------------------------------------------------------- variants
def tilt(lm_mean):
    i = np.arange(lm_mean.shape[1], dtype=np.float64)
    V = np.vander((i - i.mean()) / i.std(), 4)               # cubic
    coef, *_ = np.linalg.lstsq(V, lm_mean.T, rcond=None)
    return (lm_mean - (V @ coef).T).astype(np.float32)


def dom_mean(lm_mean, dom):
    out = lm_mean.copy()
    for d in np.unique(dom):
        m = dom == d
        out[m] -= lm_mean[m].mean(0)
    return out


def embed(name, F, dom):
    lmm, lms, harm = F[:, :40], F[:, 40:80], F[:, 80:]
    if name == "base":
        X = np.hstack([lmm, lms])
    elif name == "s1_tilt":
        X = np.hstack([tilt(lmm), lms])
    elif name == "s1_dom":
        X = np.hstack([dom_mean(lmm, dom), lms])
    elif name == "s2_harm":
        X = harm
    elif name == "s12":
        X = np.hstack([tilt(lmm), lms, harm * np.sqrt(80 / harm.shape[1])])  # equal weight to the two halves
    return X


def normalise(X, fit_mask):
    mu, sd = X[fit_mask].mean(0), X[fit_mask].std(0) + 1e-6
    E = (X - mu) / sd
    return (E / (np.linalg.norm(E, axis=1, keepdims=True) + 1e-9)).astype(np.float32)


def balanced_index(idx, y, sp, dom, rng):
    keep = []
    groups = {}
    for i in idx:
        groups.setdefault((sp[i], dom[i]), []).append(i)
    for g in groups.values():
        keep += g if len(g) <= CAP else list(rng.choice(g, CAP, replace=False))
    keep = np.array(sorted(keep))
    share = Counter(y[keep]); w = {c: len(keep) / (len(share) * n) for c, n in share.items()}
    return keep, w


def knn(E, q, idx, y, weights=None):
    out = np.empty(len(q), dtype=object)
    Ei, yi = E[idx], y[idx]
    for s in range(0, len(q), 1024):
        S = E[q[s:s + 1024]] @ Ei.T
        nn = np.argpartition(-S, K, axis=1)[:, :K]
        for j, row in enumerate(nn):
            c = Counter()
            for lab in yi[row]:
                c[lab] += weights[lab] if weights else 1
            out[s + j] = c.most_common(1)[0][0]
    return out


def scores(t, p):
    pos = t == "aedes"; neg = ~pos
    rec = float((p[pos] == "aedes").mean()) if pos.any() else np.nan
    fa = float((p[neg] == "aedes").mean()) if neg.any() else np.nan
    bal = float(np.mean([(p[t == c] == c).mean() for c in CLASSES if (t == c).any()]))
    return dict(n=int(len(t)), aedes_recall=rec, aedes_false_alarm=fa, balanced_recall=bal)


def r3(d):
    return {k: (round(v, 3) if isinstance(v, float) and not np.isnan(v) else (None if isinstance(v, float) else v))
            for k, v in d.items()}


# ----------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--limit", type=int, default=0)
    a = ap.parse_args()
    t0 = time.time()
    res_path = os.path.join(a.out, "experiments.json")
    state = dict(stage="extracting features", lodo=[], test=[], chosen=None, notes=[])

    def save(**kw):
        state.update(kw, elapsed_s=round(time.time() - t0), updated=time.strftime("%H:%M:%S"))
        tmp = res_path + ".tmp"
        json.dump(state, open(tmp, "w"), indent=1)
        for _ in range(5):
            try:
                os.replace(tmp, res_path); return
            except PermissionError:
                time.sleep(0.05)

    cache = os.path.join(a.out, f"exp_features{'_lim' + str(a.limit) if a.limit else ''}.npz")
    if os.path.exists(cache):
        z = np.load(cache, allow_pickle=True)
        F, src, sp, y, dom, split = (z[k] for k in ("F", "src", "sp", "y", "dom", "split"))
    else:
        import csv, zipfile
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
        hjobs = [(files[r["id"] + ".wav"], r) for r in
                 csv.DictReader(open(os.path.join(a.data, "humbugdb", "neurips_2021_zenodo_0_0_1.csv"), encoding="utf-8"))
                 if r["id"] + ".wav" in files]
        if a.limit:
            rng = np.random.default_rng(0)
            bjobs = [bjobs[i] for i in rng.choice(len(bjobs), a.limit, replace=False)]
            hjobs = [hjobs[i] for i in rng.choice(len(hjobs), max(1, a.limit // 10), replace=False)]
        rows = []
        with ProcessPoolExecutor(max(1, (os.cpu_count() or 2) - 2)) as ex:
            for k, r in enumerate(ex.map(job_b, bjobs, chunksize=256), 1):
                rows += r
                if k % 5000 == 0: save(stage=f"extracting features: BioDCASE {k:,}/{len(bjobs):,}")
        with ProcessPoolExecutor(6) as ex:
            for k, r in enumerate(ex.map(job_h, hjobs, chunksize=8), 1):
                rows += r
                if k % 200 == 0: save(stage=f"extracting features: HumBugDB {k:,}/{len(hjobs):,}")
        F = np.stack([r[0] for r in rows])
        src, sp, y, dom, split = (np.array([r[i] for r in rows]) for i in range(1, 6))
        np.savez(cache, F=F, src=src, sp=sp, y=y, dom=dom, split=split)
    print(f"features {F.shape}  {time.time()-t0:.0f}s", flush=True)

    rng = np.random.default_rng(0)
    variants = ["base", "s1_tilt", "s1_dom", "s2_harm", "s12"]
    is_b = src == "biodcase"
    pool = np.where(is_b & np.isin(split, ["train", "val"]))[0]
    hb_tr = np.where(~is_b & (split == "train"))[0]
    hold = [d for d in ("D1", "D3", "D4") if (dom[pool] == d).sum() >= 20]

    # ---------------- TUNE: leave-one-domain-out inside train+val
    lodo = []
    for v in variants:
        Xall = embed(v, F, dom)
        for bal in (False, True):
            name = v + ("+bal" if bal else "")
            save(stage=f"tuning (leave-one-domain-out): {name}")
            per = []
            for d in hold:
                q = pool[dom[pool] == d]
                idx = np.concatenate([pool[dom[pool] != d], hb_tr])
                E = normalise(Xall, np.isin(np.arange(len(F)), idx))
                w = None
                if bal:
                    idx, w = balanced_index(idx, y, sp, dom, rng)
                per.append(scores(y[q], knn(E, q, idx, y, w)) | {"domain": d})
            avg = {k: float(np.nanmean([p[k] for p in per])) for k in ("aedes_recall", "aedes_false_alarm", "balanced_recall")}
            lodo.append(r3(dict(variant=name, **avg)) | {"per_domain": [r3(p) for p in per]})
            print(name, r3(avg), flush=True)
            save(lodo=lodo)

    # ---------------- PICK (deployable variants only, false alarms <= 5%)
    deployable = [r for r in lodo if not r["variant"].startswith("s1_dom")]
    ok = [r for r in deployable if (r["aedes_false_alarm"] or 0) <= 0.05]
    chosen = max(ok or deployable, key=lambda r: r["balanced_recall"])["variant"]
    if not ok:
        state["notes"].append("no deployable variant kept Aedes false alarms <= 5% in tuning; picked best balanced recall")
    save(chosen=chosen, notes=state["notes"] + [f"chosen on leave-one-domain-out before test: {chosen}"])
    print("chosen:", chosen, flush=True)

    # ---------------- TEST once: official test split, baseline vs chosen
    idx_all = np.concatenate([pool, hb_tr])
    q_all = np.where(split == "test")[0]
    test = []
    for name in dict.fromkeys(["base", chosen]):
        save(stage=f"final test: {name}")
        v, bal = name.replace("+bal", ""), name.endswith("+bal")
        E = normalise(embed(v, F, dom), np.isin(np.arange(len(F)), idx_all))
        idx, w = (balanced_index(idx_all, y, sp, dom, rng) if bal else (idx_all, None))
        p = knn(E, q_all, idx, y, w)
        overall = r3(scores(y[q_all], p))
        per = [r3(scores(y[q_all][dom[q_all] == d], p[dom[q_all] == d]) | {"domain": d})
               for d in sorted(set(dom[q_all]))]
        macro = r3({k: float(np.nanmean([x[k] if x[k] is not None else np.nan for x in per if x["domain"].startswith("D")]))
                    for k in ("aedes_recall", "aedes_false_alarm", "balanced_recall")})
        test.append(dict(variant=name, overall=overall, domain_average=macro, per_domain=per))
        print(name, overall, "domain avg", macro, flush=True)
        save(test=test)
    save(stage="done")


if __name__ == "__main__":
    main()
