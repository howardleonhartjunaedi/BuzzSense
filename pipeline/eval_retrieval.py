"""Does nearest-neighbour retrieval over the store recognise Aedes on clips it never indexed?

Index = train split. Queries = test split (BioDCASE official test incl. unseen domains, HumBugDB held-out recordings).
Prediction = majority class3 among k nearest (cosine). Baselines: majority class, f0 band rule fit on train.
Usage: python -I "../App Folder/pipeline/eval_retrieval.py" --rag data/rag   (run from Audio File/)
"""
import argparse, csv, json, os
from collections import Counter
import numpy as np

ap = argparse.ArgumentParser(); ap.add_argument("--rag", required=True); ap.add_argument("--k", type=int, default=15)
a = ap.parse_args()
E = np.load(os.path.join(a.rag, "embeddings.npy"))
M = list(csv.DictReader(open(os.path.join(a.rag, "manifest.csv"), encoding="utf-8")))
y = np.array([m["class3"] for m in M]); sp = np.array([m["split"] for m in M])
dom = np.array([m["domain"] for m in M]); f0 = np.array([float(m["f0_hz"]) for m in M])
tr, te = np.where(sp == "train")[0], np.where(sp == "test")[0]
C = sorted(set(y))

pred = np.empty(len(te), dtype=object)
Etr, ytr, B = E[tr], y[tr], 256          # small chunks: ~0.3 GB per step
for s in range(0, len(te), B):
    q = te[s:s + B]
    S = E[q] @ Etr.T
    nn = np.argpartition(-S, a.k, axis=1)[:, :a.k]
    del S
    for j, row in enumerate(nn):
        pred[s + j] = Counter(ytr[row]).most_common(1)[0][0]

maj = Counter(y[tr]).most_common(1)[0][0]
aed = f0[tr][y[tr] == "aedes"]; lo, hi = np.percentile(aed, [10, 90])
rule = np.where((f0[te] >= lo) & (f0[te] <= hi), "aedes", "other")


def report(name, p, mask=slice(None)):
    t = y[te][mask]; p = np.asarray(p)[mask]
    pos = t == "aedes"; neg = ~pos
    rec = (p[pos] == "aedes").mean() if pos.any() else float("nan")
    fa = (p[neg] == "aedes").mean() if neg.any() else float("nan")
    acc = (p == t).mean()
    return dict(method=name, n=int(len(t)), aedes_recall=round(float(rec), 3),
                aedes_false_alarm=round(float(fa), 3), class3_acc=round(float(acc), 3))


out = [report(f"kNN k={a.k}", pred), report(f"majority ({maj})", [maj] * len(te)),
       report(f"f0 rule {lo:.0f}-{hi:.0f} Hz", rule)]
for d in sorted(set(dom[te])):
    m = dom[te] == d
    r = report(f"kNN on {d}", pred, m); out.append(r)
print(json.dumps(out, indent=1))
labels = C
cm = {t: Counter(pred[y[te] == t]) for t in labels}
print("confusion (true -> predicted):")
for t in labels:
    print(f"  {t:15s}", {p: cm[t][p] for p in labels})
json.dump(out, open(os.path.join(a.rag, "eval_retrieval.json"), "w"), indent=1)
