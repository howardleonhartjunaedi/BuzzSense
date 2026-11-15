"""Turn BioDCASE + HumBugDB audio into a retrievable store for the Koog agent.

No neural model. Each 0.62 s clip (8 kHz mono) becomes:
  * an 80-d acoustic embedding (log-mel mean + std, gain-normalised, z-scored, L2-normed)
    -> sqlite-vec table, searched by cosine kNN with a new recording's embedding
  * scalar wingbeat features (f0, centroid, rolloff, flatness, loudness) -> columns
  * a one-line text description -> FTS5, so the agent can quote evidence
Plus per-species/per-domain "profile" documents (plain text) for text RAG.

Usage:
  python -I "../App Folder/pipeline/build_rag.py" --data data/raw --out data/rag [--limit N]   (run from Audio File/)
"""
import argparse, csv, hashlib, json, os, re, sqlite3, struct, sys, time
from concurrent.futures import ProcessPoolExecutor
from math import gcd

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

SR = 8000
CLIP = 4960                 # 0.62 s, the BioDCASE clip length
N_FFT, HOP, N_MELS = 512, 256, 40
FMIN, FMAX = 50.0, 4000.0
F0_LO, F0_HI = 150.0, 1200.0
HUMBUG_WINDOWS = 20         # max windows taken per HumBugDB recording

BIODCASE_SPECIES = {        # id -> name, from metadata/split_summary.json (counts match)
    1: "Aedes aegypti", 2: "Aedes albopictus", 3: "Culex quinquefasciatus",
    4: "Anopheles gambiae", 5: "Anopheles arabiensis", 6: "Anopheles dirus",
    7: "Culex pipiens", 8: "Anopheles minimus", 9: "Anopheles stephensi",
}


def class3(species):
    s = (species or "").lower()
    if not s:
        return "none"
    if s.startswith(("aedes", "ae ")):
        return "aedes"
    if s.startswith(("culex", "cx ")):
        return "culex"
    return "other_mosquito"


# ---------------------------------------------------------------- features
def _mel_fb():
    hz2mel = lambda f: 2595 * np.log10(1 + f / 700)
    mel2hz = lambda m: 700 * (10 ** (m / 2595) - 1)
    pts = mel2hz(np.linspace(hz2mel(FMIN), hz2mel(FMAX), N_MELS + 2))
    bins = np.fft.rfftfreq(N_FFT, 1 / SR)
    fb = np.zeros((N_MELS, len(bins)), np.float32)
    for i in range(N_MELS):
        l, c, r = pts[i:i + 3]
        fb[i] = np.clip(np.minimum((bins - l) / (c - l), (r - bins) / (r - c)), 0, None)
    return fb


MEL = _mel_fb()
WIN = np.hanning(N_FFT).astype(np.float32)
F0_GRID = np.arange(F0_LO, F0_HI, 1.0)

# Mic-tone removal: fit a cubic across the 40 mel-band means of each clip and keep the residual.
# A microphone/setup colours the whole spectrum smoothly; the wingbeat harmonics are narrow peaks,
# so they survive. Chosen by leave-one-domain-out tuning (experiments.py, variant s1_tilt).
_i = np.arange(N_MELS, dtype=np.float64)
_V = np.vander((_i - _i.mean()) / _i.std(), 4)
TILT_RESID = (np.eye(N_MELS) - _V @ np.linalg.pinv(_V)).astype(np.float32)   # (40, 40) projector


def features(x):
    """x: float32 mono at 8 kHz, length CLIP. Returns (raw80, scalars dict)."""
    x = x - x.mean()
    rms = float(np.sqrt(np.mean(x ** 2)) + 1e-9)
    frames = np.lib.stride_tricks.sliding_window_view(x, N_FFT)[::HOP] * WIN
    P = np.abs(np.fft.rfft(frames, axis=1)) ** 2
    lm = np.log(P @ MEL.T + 1e-10)
    lm -= lm.mean()                                  # gain invariance
    raw = np.concatenate([TILT_RESID @ lm.mean(0), lm.std(0)]).astype(np.float32)

    # fine spectrum of the whole clip for wingbeat f0 (harmonic sum)
    S = np.abs(np.fft.rfft(x * np.hanning(len(x)), 16384)) ** 2
    f = np.fft.rfftfreq(16384, 1 / SR)
    score = sum(np.interp(F0_GRID * h, f, S) for h in (1, 2, 3, 4))
    f0 = float(F0_GRID[int(np.argmax(score))])
    Pm = P.mean(0); fr = np.fft.rfftfreq(N_FFT, 1 / SR)
    cen = float((fr * Pm).sum() / (Pm.sum() + 1e-12))
    roll = float(fr[np.searchsorted(np.cumsum(Pm), 0.85 * Pm.sum())])
    flat = float(np.exp(np.mean(np.log(Pm + 1e-12))) / (Pm.mean() + 1e-12))
    return raw, dict(f0_hz=f0, centroid_hz=cen, rolloff_hz=roll, flatness=flat,
                     rms_db=20 * np.log10(rms))


_ZIPS = {}


def load(path):
    """path is 'zipfile::member' (read straight from the archive) or a plain file path."""
    if "::" in path:
        import io, zipfile
        zp, member = path.split("::", 1)
        if zp not in _ZIPS:
            _ZIPS[zp] = zipfile.ZipFile(zp)
        path = io.BytesIO(_ZIPS[zp].read(member))
    x, sr = sf.read(path, dtype="float32", always_2d=True)
    x = x.mean(1)
    return to8k(x, sr) if sr != SR else x


def to8k(x, sr):
    g = gcd(int(sr), SR)
    up, down = SR // g, int(sr) // g
    if up > 1000:            # odd rate (e.g. 44101 Hz): polyphase filter would be enormous
        from scipy.signal import resample
        return resample(x, int(round(len(x) * SR / sr))).astype(np.float32)
    return resample_poly(x, up, down).astype(np.float32)


# ---------------------------------------------------------------- jobs
def job_biodcase(args):
    path, stem, split = args
    m = re.match(r"S_(\d+)_D_(\d+)_", stem)
    try:
        x = load(path)
        x = np.pad(x, (0, max(0, CLIP - len(x))))[:CLIP]
        raw, sc = features(x)
    except Exception as e:
        return [("ERR", path, str(e))]
    sp = BIODCASE_SPECIES[int(m[1])]
    return [dict(clip_id=stem, source="biodcase", path=path, offset_s=0.0,
                 species=sp, class3=class3(sp), domain="D" + m[2], split=split,
                 device="", country="", raw=raw, **sc)]


def _windows(path):
    """Yield (index, 8 kHz window) for up to HUMBUG_WINDOWS evenly spaced windows,
    seeking inside the file so long recordings are never fully decoded."""
    import io, zipfile
    src = path
    if "::" in path:
        zp, member = path.split("::", 1)
        if zp not in _ZIPS:
            _ZIPS[zp] = zipfile.ZipFile(zp)
        info = _ZIPS[zp].getinfo(member)
        # stored (uncompressed) members are seekable; otherwise read bytes
        src = _ZIPS[zp].open(member) if info.compress_type == 0 else io.BytesIO(_ZIPS[zp].read(member))
    with sf.SoundFile(src) as f:
        sr = f.samplerate
        span = int(round(CLIP * sr / SR))
        n = f.frames // span
        if n == 0:
            return
        g = gcd(sr, SR)
        for i in np.unique(np.linspace(0, n - 1, min(n, HUMBUG_WINDOWS)).astype(int)):
            f.seek(int(i) * span)
            w = f.read(span, dtype="float32", always_2d=True).mean(1)
            if sr != SR:
                w = to8k(w, sr)
            yield int(i), np.pad(w, (0, max(0, CLIP - len(w))))[:CLIP]


def job_humbug(args):
    path, row = args
    # split by recording, never by window
    split = "test" if int(hashlib.md5(row["id"].encode()).hexdigest(), 16) % 5 == 0 else "train"
    out = []
    try:
        wins = list(_windows(path))
    except Exception as e:
        return [("ERR", path, f"{type(e).__name__}: {e}")]
    for i, w in wins:
        if np.abs(w).max() < 1e-5:
            continue
        raw, sc = features(w)
        sp = row["species"].strip()
        out.append(dict(clip_id=f"HB_{row['id']}_{i}", source="humbugdb", path=path,
                        offset_s=round(i * CLIP / SR, 2), species=sp or row["sound_type"],
                        class3=class3(sp) if row["sound_type"] == "mosquito" else "none",
                        domain="HB_" + (row["country"] or "unknown"), split=split,
                        device=row["device_type"], country=row["country"], raw=raw, **sc))
    return out


def describe(r):
    tag = {"aedes": "Aedes (dengue vector)", "culex": "Culex (nuisance / filariasis)",
           "other_mosquito": "other mosquito", "none": "no mosquito (background noise)"}[r["class3"]]
    harm = "" if r["class3"] == "none" else \
        f", harmonics near {2*r['f0_hz']:.0f} and {3*r['f0_hz']:.0f} Hz"
    return (f"{r['clip_id']}: {r['species']}, class {tag}. Source {r['source']}, domain {r['domain']}"
            f"{', recorded on ' + r['device'] if r['device'] else ''}"
            f"{', ' + r['country'] if r['country'] else ''}. "
            f"Wingbeat fundamental about {r['f0_hz']:.0f} Hz{harm}. Spectral centroid {r['centroid_hz']:.0f} Hz, "
            f"85% rolloff {r['rolloff_hz']:.0f} Hz, flatness {r['flatness']:.2f}, level {r['rms_db']:.0f} dBFS. "
            f"Split {r['split']}.")


# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--limit", type=int, default=0, help="per-source cap for a quick test")
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 2))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    import zipfile
    bzip = os.path.join(a.data, "biodcase", "Development_data.zip")
    bz = zipfile.ZipFile(bzip)
    split_of = {}
    for name, sp in (("Training_ids.txt", "train"), ("Validation_ids.txt", "val"), ("Test_ids.txt", "test")):
        for line in bz.read("Development_data/metadata/" + name).decode().splitlines():
            if line.strip():
                split_of[line.strip()] = sp
    bjobs = []
    for n in sorted(bz.namelist()):
        if n.startswith("Development_data/raw_audio/") and n.endswith(".wav"):
            stem = n.rsplit("/", 1)[1][:-4]
            bjobs.append((f"{bzip}::{n}", stem, split_of.get(stem, "unlisted")))

    files = {}
    for i in range(1, 5):
        hz = os.path.join(a.data, "humbugdb", f"humbugdb_neurips_2021_{i}.zip")
        for n in zipfile.ZipFile(hz).namelist():
            if n.endswith(".wav"):
                files[n.rsplit("/", 1)[-1]] = f"{hz}::{n}"
    hjobs = []
    for row in csv.DictReader(open(os.path.join(a.data, "humbugdb", "neurips_2021_zenodo_0_0_1.csv"), encoding="utf-8")):
        p = files.get(row["id"] + ".wav")
        if p:
            hjobs.append((p, row))
    if a.limit:
        rng = np.random.default_rng(0)
        bjobs = [bjobs[i] for i in rng.choice(len(bjobs), min(a.limit, len(bjobs)), replace=False)]
        hjobs = [hjobs[i] for i in rng.choice(len(hjobs), min(a.limit // 10 or 1, len(hjobs)), replace=False)]
    print(f"biodcase clips {len(bjobs)}, humbugdb recordings {len(hjobs)} (csv rows matched)", flush=True)

    rows, errs, t = [], [], time.time()
    pfile = os.path.join(a.out, "progress.json")

    def prog(stage, done, total):
        tmp = pfile + ".tmp"
        json.dump(dict(stage=stage, done=done, total=total, windows=len(rows), errors=len(errs),
                       elapsed_s=round(time.time() - t), updated=time.strftime("%H:%M:%S"),
                       biodcase_total=len(bjobs), humbug_total=len(hjobs)), open(tmp, "w"))
        for _ in range(5):              # dashboard may be reading it; Windows then refuses the swap
            try:
                os.replace(tmp, pfile)
                return
            except PermissionError:
                time.sleep(0.05)        # progress is cosmetic: never let it kill the build

    prog("biodcase features", 0, len(bjobs))
    with ProcessPoolExecutor(a.workers) as ex:
        for k, res in enumerate(ex.map(job_biodcase, bjobs, chunksize=256), 1):
            for r in res:
                (errs if isinstance(r, tuple) else rows).append(r)
            if k % 2000 == 0 or k == len(bjobs):
                prog("biodcase features", k, len(bjobs))
            if k % 20000 == 0:
                print(f"  biodcase {k}/{len(bjobs)}  {time.time()-t:.0f}s", flush=True)
    with ProcessPoolExecutor(min(a.workers, 6)) as ex:   # long recordings: fewer workers
        for k, res in enumerate(ex.map(job_humbug, hjobs, chunksize=8), 1):
            for r in res:
                (errs if isinstance(r, tuple) else rows).append(r)
            if k % 100 == 0 or k == len(hjobs):
                prog("humbugdb features", k, len(hjobs))
    prog("writing database", 0, len(rows))
    print(f"windows {len(rows)}, errors {len(errs)}, {time.time()-t:.0f}s", flush=True)

    # normalise embeddings with train-split statistics only
    X = np.stack([r["raw"] for r in rows])
    tr = np.array([r["split"] == "train" for r in rows])
    mu, sd = X[tr].mean(0), X[tr].std(0) + 1e-6
    E = (X - mu) / sd
    E /= np.linalg.norm(E, axis=1, keepdims=True) + 1e-9
    E = E.astype(np.float32)
    json.dump(dict(sr=SR, clip_samples=CLIP, n_fft=N_FFT, hop=HOP, n_mels=N_MELS, fmin=FMIN, fmax=FMAX,
                   window="hann", mel="htk-triangular", log_eps=1e-10,
                   embedding="concat(TILT_RESID @ mean_t(logmel - mean(logmel)), std_t(...)); z-score with mu/sd; L2",
                   mic_tone_removal="cubic fit over the 40 mel-band means (band index standardised, np.vander deg 3), "
                                    "residual kept; apply tilt_resid (40x40, row-major) to the mean half only",
                   tilt_resid=TILT_RESID.tolist(), version="v2-tilt",
                   mu=mu.tolist(), sd=sd.tolist()),
              open(os.path.join(a.out, "feature_spec.json"), "w"), indent=1)
    np.save(os.path.join(a.out, "embeddings.npy"), E)

    db = os.path.join(a.out, "jumantik_rag.sqlite")
    if os.path.exists(db):
        os.remove(db)
    import sqlite_vec
    c = sqlite3.connect(db)
    c.enable_load_extension(True); sqlite_vec.load(c); c.enable_load_extension(False)
    c.executescript(f"""
    CREATE TABLE clips(id INTEGER PRIMARY KEY, clip_id TEXT UNIQUE, source TEXT, path TEXT, offset_s REAL,
      species TEXT, class3 TEXT, domain TEXT, split TEXT, device TEXT, country TEXT,
      f0_hz REAL, centroid_hz REAL, rolloff_hz REAL, flatness REAL, rms_db REAL, description TEXT);
    CREATE VIRTUAL TABLE clips_fts USING fts5(description, content='clips', content_rowid='id');
    CREATE VIRTUAL TABLE clips_vec USING vec0(id INTEGER PRIMARY KEY, embedding float[{E.shape[1]}] distance_metric=cosine,
      class3 TEXT, split TEXT);
    CREATE TABLE profiles(id INTEGER PRIMARY KEY, doc_id TEXT UNIQUE, title TEXT, body TEXT);
    CREATE VIRTUAL TABLE profiles_fts USING fts5(title, body, content='profiles', content_rowid='id');
    """)
    cols = ["clip_id", "source", "path", "offset_s", "species", "class3", "domain", "split", "device", "country",
            "f0_hz", "centroid_hz", "rolloff_hz", "flatness", "rms_db"]
    with c:
        for i, r in enumerate(rows, 1):
            zp, _, member = r["path"].partition("::")
            r["path"] = os.path.relpath(zp, a.data).replace("\\", "/") + ("::" + member if member else "")
            c.execute(f"INSERT INTO clips(id,{','.join(cols)},description) VALUES (?{',?'*len(cols)},?)",
                      [i] + [r[k] for k in cols] + [describe(r)])
            c.execute("INSERT INTO clips_vec(id,embedding,class3,split) VALUES (?,?,?,?)",
                      (i, E[i - 1].tobytes(), r["class3"], r["split"]))
            if i % 10000 == 0:
                prog("writing database", i, len(rows))
        c.execute("INSERT INTO clips_fts(clips_fts) VALUES('rebuild')")

        # profile documents: one per (source, species, domain) group with >= 20 windows
        groups = {}
        for r in rows:
            groups.setdefault((r["source"], r["species"], r["class3"], r["domain"]), []).append(r)
        for (src, sp, c3, dom), g in sorted(groups.items()):
            if len(g) < 20:
                continue
            f0 = np.array([x["f0_hz"] for x in g]); cen = np.array([x["centroid_hz"] for x in g])
            q = lambda v: np.percentile(v, [10, 50, 90])
            body = (f"{sp} ({c3}) in {src} domain {dom}: {len(g)} clips of 0.62 s at 8 kHz. "
                    f"Wingbeat fundamental median {q(f0)[1]:.0f} Hz (10th-90th percentile {q(f0)[0]:.0f}-{q(f0)[2]:.0f} Hz). "
                    f"Spectral centroid median {q(cen)[1]:.0f} Hz ({q(cen)[0]:.0f}-{q(cen)[2]:.0f} Hz). "
                    f"Splits: " + ", ".join(f"{s} {sum(x['split']==s for x in g)}" for s in ("train", "val", "test"))
                    + ". Note: wingbeat frequency overlaps between species and shifts with temperature, body size "
                      "and sex (females lower), so frequency alone is not a reliable species label.")
            c.execute("INSERT INTO profiles(doc_id,title,body) VALUES (?,?,?)",
                      (f"{src}:{sp}:{dom}", f"Acoustic profile: {sp}, {src} {dom}", body))
        c.execute("INSERT INTO profiles_fts(profiles_fts) VALUES('rebuild')")
    c.execute("VACUUM"); c.close()

    with open(os.path.join(a.out, "manifest.csv"), "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh); w.writerow(["row"] + cols)
        for i, r in enumerate(rows):
            w.writerow([i] + [r[k] for k in cols])
    if errs:
        with open(os.path.join(a.out, "errors.txt"), "w", encoding="utf-8") as fh:
            for e in errs:
                fh.write(f"{e[1]}\t{e[2]}\n")
    prog("build done", len(rows), len(rows))
    print("done:", db, f"{os.path.getsize(db)/1e6:.0f} MB", flush=True)


if __name__ == "__main__":
    main()
