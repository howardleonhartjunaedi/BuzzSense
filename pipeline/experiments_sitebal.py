"""Step 4: does balancing kNN neighbours across recording sites help on a site the index never saw?

Question: an Aedes clip from HumBugDB Tanzania (Tascam) is voted "other_mosquito", and an Anopheles clip
from HumBugDB Kenya (phone) is voted "none", because most neighbours come from the same site and that
site is mostly another class. Would capping or equalising each site's say fix it?

Protocol (train/val only; the test split and D2 queries are never used):
  * Leave one site (= `domain`) out: queries are that site's train+val windows (up to 3000, seeded),
    the index is every other site's train+val windows. mu/sd are refit on the index each fold.
  * Embedding = shipped v2-tilt (TILT_RESID @ log-mel mean, log-mel std), from exp_features.npz.
  * Decision = the app's rule (WingbeatDetector): mosquito if 1 - share(none) >= 0.6, then Aedes if
    share(aedes) / share(mosquito) >= 0.5, else the largest mosquito class.

Variants
  base k15            current app: 15 nearest neighbours vote
  capN/site           15 nearest, but at most N from any one site
  site-vote mM        every site's M nearest vote with equal total weight
  site-vote mM near   same, only sites whose best neighbour is within 0.15 cosine of the overall best

Result (2026-11-15, average balanced recall over 9 held-out sites): base 0.41, cap5 0.37,
site-vote m3 near 0.38, others lower. Balancing raised mosquito recall on Kenya (0.14 -> up to 0.93)
but background false alarms jumped (Thailand 0.54 -> 0.8-1.0), because BioDCASE sites hold no `none`
clips. Aedes recall with D5 held out stayed ~0-3% for every variant: the features carry the setup,
not the species, so re-weighting votes cannot recover it. Not adopted; the app is unchanged.
Full table: data/rag_sitebal/sitebal.json.

Usage (from Audio File/):
  python -I "../App Folder/pipeline/experiments_sitebal.py" --features data/rag/exp_features.npz --out data/rag_sitebal
"""
import argparse, json, os, sys
from collections import Counter

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402

KD = 15          # neighbours kept per site (enough to rebuild the global top-15 exactly)
QMAX = 3000      # queries per held-out site
NEAR = 0.15      # cosine margin for "near" sites
FOLDS = ["D1", "D3", "D4", "D5", "HB_Tanzania", "HB_USA", "HB_Kenya", "HB_Thailand", "HB_UK"]
CL = ["aedes", "culex", "other_mosquito", "none"]
METRICS = ("aedes_recall", "aedes_false_alarm", "mosquito_recall", "bg_false_alarm", "balanced")


def decide(sh):
    m = 1 - sh.get("none", 0)
    if m < 0.6:
        return "none"
    if sh.get("aedes", 0) / m >= 0.5:
        return "aedes"
    return max([c for c in CL if c != "none"], key=lambda c: sh.get(c, 0))


def variants(cand):
    """cand: (similarity, label, site) for the top-KD neighbours of every index site."""
    allc = sorted(cand, key=lambda t: -t[0])
    out = {"base k15": Counter(l for _, l, _ in allc[:15])}
    for m in (1, 2, 3, 5):
        took, per = [], Counter()
        for _, l, d in allc:
            if per[d] < m:
                took.append(l); per[d] += 1
            if len(took) == 15:
                break
        out[f"cap{m}/site"] = Counter(took)
    byd = {}
    for s, l, d in allc:
        byd.setdefault(d, []).append((s, l))
    best = allc[0][0]
    for m in (3, 5):
        for name, ds in ((f"site-vote m{m}", list(byd)),
                         (f"site-vote m{m} near", [d for d in byd if byd[d][0][0] >= best - NEAR])):
            c = Counter()
            for d in ds:
                for _, l in byd[d][:m]:
                    c[l] += 1 / m
            out[name] = c
    return {k: {c: v / sum(cc.values()) for c, v in cc.items()} for k, cc in out.items()}


def score(t, p):
    pos, mos = t == "aedes", t != "none"
    r = dict(n=int(len(t)),
             aedes_recall=float((p[pos] == "aedes").mean()) if pos.any() else None,
             aedes_false_alarm=float((p[~pos] == "aedes").mean()) if (~pos).any() else None,
             mosquito_recall=float((p[mos] != "none").mean()) if mos.any() else None,
             bg_false_alarm=float((p[~mos] != "none").mean()) if (~mos).any() else None,
             balanced=float(np.mean([(p[t == c] == c).mean() for c in CL if (t == c).any()])))
    return {k: (round(v, 3) if isinstance(v, float) else v) for k, v in r.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--features", default="data/rag/exp_features.npz")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    z = np.load(a.features, allow_pickle=True)
    F, y, dom, split = (z[k] for k in ("F", "y", "dom", "split"))
    X = np.hstack([F[:, :40] @ B.TILT_RESID.T, F[:, 40:80]]).astype(np.float32)
    dev = np.isin(split, ["train", "val"])
    rng = np.random.default_rng(0)

    res = {}
    for fold in FOLDS:
        q = np.where(dev & (dom == fold))[0]
        if len(q) > QMAX:
            q = rng.choice(q, QMAX, replace=False)
        idx = np.where(dev & (dom != fold))[0]
        mu, sd = X[idx].mean(0), X[idx].std(0) + 1e-6
        E = (X - mu) / sd
        E = (E / (np.linalg.norm(E, axis=1, keepdims=True) + 1e-9)).astype(np.float32)
        sub = {d: idx[dom[idx] == d] for d in sorted(set(dom[idx]))}
        preds = {}
        for s in range(0, len(q), 128):
            Q = E[q[s:s + 128]]
            cands = [[] for _ in range(len(Q))]
            for d, ids in sub.items():
                S = Q @ E[ids].T
                k = min(KD, S.shape[1])
                top = np.argpartition(-S, k - 1, axis=1)[:, :k]
                for j in range(len(Q)):
                    cands[j] += [(float(S[j, t]), y[ids[t]], d) for t in top[j]]
            for c in cands:
                for name, sh in variants(c).items():
                    preds.setdefault(name, []).append(decide(sh))
        for name, p in preds.items():
            res.setdefault(name, {})[fold] = score(y[q], np.array(p))
        print(fold, len(q), "done", flush=True)

    avg = {name: round(float(np.mean([per[f]["balanced"] for f in FOLDS])), 3) for name, per in res.items()}
    os.makedirs(a.out, exist_ok=True)
    json.dump(dict(folds=FOLDS, balanced_recall_avg=avg, per_site=res), open(os.path.join(a.out, "sitebal.json"), "w"), indent=1)

    print(f"{'variant':22s}" + "".join(f"{f[:10]:>12s}" for f in FOLDS))
    for key in METRICS:
        print("--", key)
        for name, per in res.items():
            print(f"{name:22s}" + "".join(f"{'' if per[f][key] is None else per[f][key]:>12}" for f in FOLDS))
    print("-- balanced recall, average over sites:", avg)


if __name__ == "__main__":
    main()
