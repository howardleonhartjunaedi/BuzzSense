"""Build guidance.db, the on-device prevention-guidance corpus read by the app's GuidanceStore.

Inputs (``--sources`` folder):
  *.jsonl  curated passages, one JSON object per line with
           id, source, url, title, lang (en|es), text  [+ topic, region, vector, evidence]
           and an optional sidecar ``<name>.meta.json`` (derived_from, licence).
  *.md     a saved guidance page with front matter between ``---`` lines
           (id_prefix, source, url, title, lang, licence [, topic, region, vector]);
           chunked on headings, then into ~400-token windows with 60 tokens of overlap.

Output: the schema documented on GuidanceStore (chunks, chunks_fts, chunks_vec,
chunk_embeddings, query_embeddings) plus a ``meta`` table. Embeddings use
multilingual-e5-small (ONNX), mean-pooled over the attention mask and L2-normalised,
the same as OnnxE5Embedder in the app. Every query GuidanceQueryBuilder can produce is
embedded too, so the phone needs no tokenizer; GuidanceDbParityTest checks the set.

Usage (from App Folder/tools/guidance):
  .venv/Scripts/python.exe -I build_guidance_db.py --sources sources \
      --model models/multilingual-e5-small --out ../../app/src/main/assets/guidance.db
"""
from __future__ import annotations

import argparse
import itertools
import json
import re
import sqlite3
import struct
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

MODEL_ID = "intfloat/multilingual-e5-small"
MODEL_REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
DIM = 384
CHUNK_TOKENS = 400
OVERLAP_TOKENS = 60
MAX_SEQ = 512
REQUIRED = ("id", "source", "url", "title", "lang", "text")

# Mirror of HealthSafetyValidator.MEDICINE: guidance the LLM paraphrases must not carry
# medicine or dosing, or every alert citing it would fail validation.
MEDICINE = [
    re.compile(r"\b(acetaminophen|paracetamol|ibuprofen|ibuprofeno|aspirin|aspirina|naproxen|tylenol|advil|motrin)\b", re.I),
    re.compile(r"\b(artemether|lumefantrine|artemisinin\w*|artesunate|primaquine|primaquina|chloroquine|cloroquina|atovaquone|proguanil|doxycycline|doxiciclina|mefloquine|mefloquina|tafenoquine|quinine|quinina)\b", re.I),
    re.compile(r"\b(chemo)?prophylaxis\b|\b(quimio)?profilaxis\b", re.I),
    re.compile(r"\b\d+\s?(mg|ml|mcg)\b", re.I),
    re.compile(r"\b(dose|dosage|dosis|tablet|pill|pastilla|antibiotic|antibi[oó]tico|medication|medicamento|medicine|medicina)s?\b", re.I),
]


# --- GuidanceQueryBuilder mirror ---------------------------------------------------------
# Must produce exactly the strings of GuidanceQueryBuilder.build (HybridRetriever.kt).
# GuidanceDbParityTest fails if the two drift apart.

def build_query(aedes_yes: bool, rain: bool, hot: bool, high_risk: bool, es: bool) -> str:
    parts = []
    if aedes_yes:
        parts.append("Aedes aegypti mosquito dentro de la casa dengue prevención" if es
                     else "Aedes aegypti mosquito indoors dengue prevention")
    else:
        parts.append("mosquito dentro de la casa prevención picaduras" if es else "mosquito indoors prevent bites")
    if not aedes_yes:
        parts.append("malaria Anopheles dormir bajo mosquitero tratado de noche prueba de malaria si hay fiebre" if es
                     else "malaria Anopheles sleep under treated bed net at night malaria test for fever")
    parts.append("eliminar criaderos agua estancada recipientes mosquiteros repelente" if es
                 else "eliminate breeding sites standing water containers screens repellent")
    if rain:
        parts.append("después de la lluvia vaciar recipientes" if es else "after rain empty containers")
    if hot:
        parts.append("calor" if es else "hot weather")
    if high_risk:
        parts.append("brote" if es else "outbreak")
    return " ".join(parts)


def all_queries() -> list[str]:
    return sorted({build_query(*flags) for flags in itertools.product([False, True], repeat=5)})


# --- Embedding ---------------------------------------------------------------------------

class E5:
    def __init__(self, model_dir: Path):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        onnx = model_dir / "onnx" / "model.onnx"
        tok = model_dir / "onnx" / "tokenizer.json"
        for f in (onnx, tok):
            if not f.exists():
                sys.exit(f"missing {f}: download {MODEL_ID}@{MODEL_REVISION[:7]} (see README.md)")
        self.tok = Tokenizer.from_file(str(tok))
        self.tok.enable_truncation(MAX_SEQ)
        self.sess = ort.InferenceSession(str(onnx), providers=["CPUExecutionProvider"])
        self.inputs = {i.name for i in self.sess.get_inputs()}

    def count_tokens(self, text: str) -> int:
        return len(self.tok.encode(text, add_special_tokens=False).ids)

    def token_windows(self, text: str, size: int, overlap: int) -> list[str]:
        enc = self.tok.encode(text, add_special_tokens=False)
        if len(enc.ids) <= size:
            return [text]
        out, start = [], 0
        while start < len(enc.ids):
            end = min(start + size, len(enc.ids))
            a, b = enc.offsets[start][0], enc.offsets[end - 1][1]
            out.append(text[a:b].strip())
            if end == len(enc.ids):
                break
            start = end - overlap
        return out

    def embed(self, text: str, is_query: bool) -> np.ndarray:
        enc = self.tok.encode(("query: " if is_query else "passage: ") + text)
        ids = np.array([enc.ids], dtype=np.int64)
        mask = np.array([enc.attention_mask], dtype=np.int64)
        feed = {"input_ids": ids, "attention_mask": mask}
        if "token_type_ids" in self.inputs:
            feed["token_type_ids"] = np.zeros_like(ids)
        hidden = self.sess.run(None, feed)[0][0]          # [seq, 384]
        m = mask[0].astype(np.float32)[:, None]
        pooled = (hidden * m).sum(0) / max(m.sum(), 1.0)
        return (pooled / max(np.linalg.norm(pooled), 1e-12)).astype(np.float32)


def f32le(v: np.ndarray) -> bytes:
    return struct.pack(f"<{len(v)}f", *v.tolist())


# --- Sources -----------------------------------------------------------------------------

def load_jsonl(path: Path) -> list[dict]:
    meta_path = path.with_suffix(".meta.json")
    meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
    rows = []
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        r = json.loads(line)
        missing = [k for k in REQUIRED if not str(r.get(k, "")).strip()]
        if missing:
            sys.exit(f"{path.name}:{n}: missing {missing}")
        r.setdefault("derived_from", meta.get("derived_from", ""))
        r.setdefault("licence", meta.get("licence", ""))
        r["origin"] = path.name
        rows.append(r)
    return rows


FRONT = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.S)
HEADING = re.compile(r"^#{1,6}\s+(.*)$", re.M)


def load_markdown(path: Path, e5: E5) -> list[dict]:
    raw = path.read_text(encoding="utf-8")
    m = FRONT.match(raw)
    if not m:
        sys.exit(f"{path.name}: needs front matter (id_prefix, source, url, title, lang, licence)")
    meta = dict(line.split(":", 1) for line in m.group(1).splitlines() if ":" in line)
    meta = {k.strip(): v.strip() for k, v in meta.items()}
    for k in ("id_prefix", "source", "url", "title", "lang", "licence"):
        if not meta.get(k):
            sys.exit(f"{path.name}: front matter missing {k}")
    body = raw[m.end():]
    # Split on headings; each section keeps its heading as the chunk title.
    pieces, last, title = [], 0, meta["title"]
    for h in HEADING.finditer(body):
        pieces.append((title, body[last:h.start()]))
        title, last = h.group(1).strip(), h.end()
    pieces.append((title, body[last:]))
    rows, n = [], 0
    for sec_title, text in pieces:
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) < 40:
            continue
        for window in e5.token_windows(text, CHUNK_TOKENS, OVERLAP_TOKENS):
            n += 1
            rows.append({
                "id": f"{meta['id_prefix']}-{n:02d}", "source": meta["source"], "url": meta["url"],
                "title": sec_title, "lang": meta["lang"], "text": window, "licence": meta["licence"],
                "topic": meta.get("topic", ""), "region": meta.get("region", ""), "vector": meta.get("vector", ""),
                "evidence": meta.get("evidence", ""), "derived_from": meta.get("derived_from", ""), "origin": path.name,
            })
    return rows


def lint(rows: list[dict], e5: E5) -> None:
    ids, problems = set(), []
    for r in rows:
        if r["id"] in ids:
            problems.append(f"duplicate id {r['id']}")
        ids.add(r["id"])
        if r["lang"] not in ("en", "es"):
            problems.append(f"{r['id']}: lang must be en or es")
        if not r["url"].startswith(("http://", "https://")):
            problems.append(f"{r['id']}: url must be http(s)")
        for rx in MEDICINE:
            if hit := rx.search(r["title"] + " " + r["text"]):
                problems.append(f"{r['id']}: medicine/dosing text '{hit.group(0)}' (not allowed in guidance)")
        if (t := e5.count_tokens(r["text"])) > MAX_SEQ - 16:
            problems.append(f"{r['id']}: {t} tokens, over the embedder limit")
    if problems:
        sys.exit("corpus lint failed:\n  " + "\n  ".join(problems))


# --- Database ----------------------------------------------------------------------------

SCHEMA = """
CREATE TABLE chunks(rowid INTEGER PRIMARY KEY, id TEXT UNIQUE, source TEXT, url TEXT,
                    title TEXT, lang TEXT, text TEXT,
                    licence TEXT, topic TEXT, region TEXT, vector TEXT, evidence TEXT,
                    derived_from TEXT, origin TEXT);
CREATE VIRTUAL TABLE chunks_fts USING fts5(title, text, content='chunks', content_rowid='rowid');
CREATE TABLE chunk_embeddings(rowid INTEGER PRIMARY KEY, embedding BLOB);
CREATE TABLE query_embeddings(query TEXT PRIMARY KEY, embedding BLOB);
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
"""


def write_db(out: Path, rows: list[dict], e5: E5) -> None:
    import sqlite_vec

    tmp = out.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    out.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(tmp)
    db.enable_load_extension(True)
    sqlite_vec.load(db)
    db.enable_load_extension(False)
    db.executescript(SCHEMA)
    db.execute(f"CREATE VIRTUAL TABLE chunks_vec USING vec0(embedding float[{DIM}])")

    cols = ("id", "source", "url", "title", "lang", "text", "licence", "topic", "region",
            "vector", "evidence", "derived_from", "origin")
    for rowid, r in enumerate(rows, 1):
        db.execute(f"INSERT INTO chunks(rowid,{','.join(cols)}) VALUES (?{',?' * len(cols)})",
                   (rowid, *[r.get(c, "") for c in cols]))
        v = f32le(e5.embed(f"{r['title']}\n{r['text']}", is_query=False))
        db.execute("INSERT INTO chunk_embeddings(rowid, embedding) VALUES (?,?)", (rowid, v))
        db.execute("INSERT INTO chunks_vec(rowid, embedding) VALUES (?,?)", (rowid, v))
    db.execute("INSERT INTO chunks_fts(chunks_fts) VALUES ('rebuild')")

    queries = all_queries()
    for q in queries:
        db.execute("INSERT INTO query_embeddings(query, embedding) VALUES (?,?)", (q, f32le(e5.embed(q, is_query=True))))

    meta = {
        "model": f"{MODEL_ID}@{MODEL_REVISION}", "dimensions": str(DIM), "pooling": "mean, L2-normalised",
        "prefixes": "passage: / query:", "chunking": f"headings, {CHUNK_TOKENS} tokens, {OVERLAP_TOKENS} overlap",
        "chunks": str(len(rows)), "queries": str(len(queries)),
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    db.executemany("INSERT INTO meta(key, value) VALUES (?,?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(out)


# --- Retrieval check (same steps as HybridRetriever) --------------------------------------

def fts_query(q: str) -> str | None:
    terms = []
    for t in re.findall(r"[^\W_]+", q.lower()):
        if len(t) > 1 and t not in terms:
            terms.append(t)
    return " OR ".join(f'"{t}"' for t in terms) if terms else None


def report(out: Path, k: int = 5, candidates: int = 30, rrf_k: int = 60) -> None:
    db = sqlite3.connect(out)
    embs = {rid: np.frombuffer(b, dtype="<f4") for rid, b in db.execute("SELECT rowid, embedding FROM chunk_embeddings")}
    ids = dict(db.execute("SELECT rowid, id FROM chunks"))
    seen: dict[str, int] = {}
    print(f"\nRetrieval check: top {k} per query (hybrid, RRF k={rrf_k})")
    for q, qv in db.execute("SELECT query, embedding FROM query_embeddings ORDER BY query"):
        qv = np.frombuffer(qv, dtype="<f4")
        kw = [ids[r] for (r,) in db.execute(
            "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT ?", (fts_query(q), candidates))]
        vec = [ids[r] for r, _ in sorted(embs.items(), key=lambda kv: -float(kv[1] @ qv))[:candidates]]
        score: dict[str, float] = {}
        for hits in (kw, vec):
            for rank, cid in enumerate(hits, 1):
                score[cid] = score.get(cid, 0.0) + 1.0 / (rrf_k + rank)
        top = [c for c, _ in sorted(score.items(), key=lambda kv: (-kv[1], kv[0]))[:k]]
        for c in top:
            seen[c] = seen.get(c, 0) + 1
        print(f"  {q[:70]:<70} -> {', '.join(top)}")
    never = sorted(set(ids.values()) - set(seen))
    print(f"\nChunks never in any top {k}: {len(never)}" + (f" ({', '.join(never)})" if never else ""))


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sources", type=Path, default=Path("sources"))
    ap.add_argument("--model", type=Path, default=Path("models/multilingual-e5-small"))
    ap.add_argument("--out", type=Path, default=Path("../../app/src/main/assets/guidance.db"))
    args = ap.parse_args()

    e5 = E5(args.model)
    rows: list[dict] = []
    for p in sorted(args.sources.glob("*.jsonl")):
        rows += load_jsonl(p)
    for p in sorted(args.sources.glob("*.md")):
        rows += load_markdown(p, e5)
    if not rows:
        sys.exit(f"no sources in {args.sources}")
    lint(rows, e5)
    write_db(args.out, rows, e5)
    langs = {l: sum(r["lang"] == l for r in rows) for l in ("en", "es")}
    print(f"wrote {args.out} ({args.out.stat().st_size // 1024} KB): {len(rows)} chunks {langs}, {len(all_queries())} queries")
    report(args.out)


if __name__ == "__main__":
    main()
