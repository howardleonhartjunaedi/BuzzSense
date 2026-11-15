"""How well do Gemini / Gemma name the mosquito from the raw buzz? A small, capped comparison.

Evaluation only, on public dataset audio (BioDCASE validation split, HumBugDB train recordings).
The app never sends a user's audio off the phone (root AGENTS.md); this only measures whether an
audio LLM would be worth it, next to the v2 kNN detector on the same clips. No test-split data.

Budget: --per-class clips per class (4 classes), --batch clips per request, one request per batch
per model, and a hard --max-requests cap over the whole run. Default 40 clips -> 4 requests/model.
Each clip is a 0.62 s, 8 kHz WAV (about 10 KB, ~20 audio tokens).

Key: GEMINI_API_KEY from the environment (process, else Windows user scope). It goes only in the
x-goog-api-key header, never in a URL, file or log.

Usage (from Audio File/):
  .venv/Scripts/python.exe -I "../App Folder/pipeline/llm_audio_eval.py" --data data/raw \
      --features <v3 cache>/v3_features.npz --out data/rag_llm --models gemini-3.5-flash-lite,gemma-3n-e4b-it
"""
import argparse, base64, io, json, os, re, sys, time, urllib.error, urllib.request, wave, zipfile
from collections import Counter

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rag as B  # noqa: E402
import experiments_v3 as V3  # noqa: E402

API = "https://generativelanguage.googleapis.com/v1beta"
CLASSES = ["aedes", "culex", "other_mosquito", "none"]
PROMPT = """You are an acoustic entomologist. Each numbered audio clip is 0.62 seconds of sound recorded at 8 kHz
(content up to 4 kHz). For each clip decide which ONE label fits best:
- "aedes": an Aedes mosquito (e.g. Aedes aegypti, Aedes albopictus) in flight
- "culex": a Culex mosquito (e.g. Culex quinquefasciatus, Culex pipiens) in flight
- "other_mosquito": another mosquito, e.g. Anopheles
- "none": no mosquito flight tone (background noise, speech, other sounds)
Clues: a mosquito gives a steady tone with harmonics; female Aedes aegypti fundamentals are often ~400-700 Hz,
Culex often ~300-500 Hz, Anopheles vary widely. Answer for every clip, in order."""


def key():
    k = os.environ.get("GEMINI_API_KEY", "").strip()
    if not k and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as h:
                k = str(winreg.QueryValueEx(h, "GEMINI_API_KEY")[0]).strip()
        except OSError:
            pass
    if not k:
        sys.exit("GEMINI_API_KEY is not set (process or user environment)")
    return k


def redact(s, k):
    return s.replace(k, "***")


def post(model, body, k, timeout=120):
    req = urllib.request.Request(f"{API}/models/{model}:generateContent", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "x-goog-api-key": k}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def wav_bytes(w):
    buf = io.BytesIO()
    pcm = np.clip(w / (np.abs(w).max() + 1e-9) * 0.9, -1, 1)
    with wave.open(buf, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(B.SR)
        f.writeframes((pcm * 32767).astype("<i2").tobytes())
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--features", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--models", default="gemini-3.5-flash-lite")
    ap.add_argument("--per-class", type=int, default=10); ap.add_argument("--batch", type=int, default=10)
    ap.add_argument("--max-requests", type=int, default=12)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    k = key()
    models = [m.strip() for m in a.models.split(",") if m.strip()]

    z = np.load(a.features)
    F, src, y, dom, split, rec, win = (z[n] for n in ("F", "src", "y", "dom", "split", "rec", "win"))
    rng = np.random.default_rng(7)
    # clips: BioDCASE validation (mosquito classes) and HumBugDB train recordings (background = none),
    # one window per recording so no recording is counted twice
    pick = []
    for c in CLASSES:
        cand = np.where((y == c) & (((src == "biodcase") & (split == "val")) | ((src == "humbugdb") & (split == "train"))))[0]
        _, first = np.unique(rec[cand], return_index=True)
        cand = cand[first]
        pick += list(rng.choice(cand, min(a.per_class, len(cand)), replace=False))
    pick = np.array(rng.permutation(pick))

    # v2 kNN on the same clips (index = train split, like the app)
    tr = np.where(split == "train")[0]
    tr = tr[~np.isin(rec[tr], rec[pick])]
    allidx = np.concatenate([tr, pick])
    X = V3.VARIANTS["v2 (current app)"](F[allidx].astype(np.float32))
    mu, sd = X[:len(tr)].mean(0), X[:len(tr)].std(0) + 1e-6
    E = (X - mu) / sd; E /= np.linalg.norm(E, axis=1, keepdims=True) + 1e-9
    knn = V3.knn(E.astype(np.float32), np.arange(len(tr), len(allidx)), np.arange(len(tr)), y[allidx])

    # audio for the picked clips
    bzip = os.path.join(a.data, "biodcase", "Development_data.zip")
    bpath = {n.rsplit("/", 1)[1][:-4]: f"{bzip}::{n}" for n in zipfile.ZipFile(bzip).namelist() if n.endswith(".wav")}
    hpath = {}
    for i in range(1, 5):
        hz = os.path.join(a.data, "humbugdb", f"humbugdb_neurips_2021_{i}.zip")
        for n in zipfile.ZipFile(hz).namelist():
            if n.endswith(".wav"): hpath["HB_" + n.rsplit("/", 1)[-1][:-4]] = f"{hz}::{n}"
    audio = []
    for i in pick:
        x = B.load(bpath[rec[i]] if src[i] == "biodcase" else hpath[rec[i]])
        w = x[int(win[i]) * B.CLIP:(int(win[i]) + 1) * B.CLIP]
        audio.append(wav_bytes(np.pad(w, (0, B.CLIP - len(w)))))

    schema = {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
        "clip": {"type": "INTEGER"}, "label": {"type": "STRING", "enum": CLASSES}}, "required": ["clip", "label"]}}
    out = dict(clips=[dict(i=int(i), truth=str(y[i]), domain=str(dom[i]), source=str(src[i]), v2=str(p))
                      for i, p in zip(pick, knn)], models={}, requests=0)
    requests = 0
    for m in models:
        preds, errors = {}, []
        for s in range(0, len(pick), a.batch):
            if requests >= a.max_requests:
                errors.append("stopped: --max-requests reached"); break
            parts = [{"text": PROMPT}]
            for j in range(s, min(s + a.batch, len(pick))):
                parts += [{"text": f"Clip {j + 1}:"}, {"inline_data": {"mime_type": "audio/wav", "data": base64.b64encode(audio[j]).decode()}}]
            body = {"contents": [{"role": "user", "parts": parts}], "generationConfig": {"temperature": 0}}
            if not m.startswith("gemma"):        # Gemma on the API has no JSON mode: parse the text instead
                body["generationConfig"] |= {"responseMimeType": "application/json", "responseSchema": schema}
            else:
                parts[0]["text"] += '\nReply with only a JSON array like [{"clip": 1, "label": "aedes"}, ...].'
            requests += 1
            try:
                r = post(m, body, k)
                txt = "".join(p.get("text", "") for p in r["candidates"][0]["content"]["parts"])
                txt = re.sub(r"^```(?:json)?|```$", "", txt.strip(), flags=re.M).strip()
                for item in json.loads(txt):
                    if str(item.get("label")) in CLASSES:
                        preds[int(item["clip"]) - 1] = item["label"]
                usage = r.get("usageMetadata", {})
                print(f"{m}: batch {s // a.batch + 1} ok, tokens in {usage.get('promptTokenCount')} out {usage.get('candidatesTokenCount')}", flush=True)
            except urllib.error.HTTPError as e:
                msg = redact(e.read().decode(errors="replace")[:300], k)
                errors.append(f"HTTP {e.code}: {msg}"); print(f"{m}: HTTP {e.code} {msg}", flush=True)
                if e.code in (400, 401, 403, 404): break           # wrong model / no audio support: stop this model
            except Exception as e:
                errors.append(redact(f"{type(e).__name__}: {e}", k)); print(f"{m}: {errors[-1]}", flush=True)
            time.sleep(4)                                            # stay far below free-tier rate limits
        t = np.array([c["truth"] for c in out["clips"]])
        p = np.array([preds.get(j, "no answer") for j in range(len(pick))])
        answered = np.array([j in preds for j in range(len(pick))])
        out["models"][m] = dict(scores=V3.r3(V3.scores(t, p)), answered=int(answered.sum()), errors=errors,
                                confusion={c: dict(Counter(p[t == c])) for c in CLASSES})
        for j, c in enumerate(out["clips"]): c[m] = str(p[j])
    t = np.array([c["truth"] for c in out["clips"]])
    out["v2_knn"] = dict(scores=V3.r3(V3.scores(t, np.array([c["v2"] for c in out["clips"]]))),
                         confusion={c: dict(Counter(np.array([x["v2"] for x in out["clips"]])[t == c])) for c in CLASSES})
    out["requests"] = requests
    json.dump(out, open(os.path.join(a.out, "llm_eval.json"), "w", encoding="utf-8"), indent=1)
    print(json.dumps({"v2 kNN": out["v2_knn"]["scores"], **{m: v["scores"] | {"answered": v["answered"]} for m, v in out["models"].items()},
                      "requests": requests}, indent=1))


if __name__ == "__main__":
    main()
