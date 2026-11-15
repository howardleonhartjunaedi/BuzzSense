"""Build guidance.db, the on-device prevention-guidance corpus read by the app's GuidanceStore.

Inputs (``--sources`` folder):
  *.jsonl  curated passages, one JSON object per line with
           id, source, url, title, lang (en|es), text  [+ topic, region, vector, evidence]
           and an optional sidecar ``<name>.meta.json`` (derived_from, licence).
  *.md     a saved guidance page or review with front matter between ``---`` lines
           (id_prefix, source, title, lang, licence [, url, topic, region, vector, evidence,
           derived_from, skip_sections, skip_starts, exclude_pattern]). Chunked by its own structure, see
           ``chunk_markdown``: never inside a sentence, one paragraph / list / table row per
           chunk, each linked to the primary source it cites.

Output: the schema documented on GuidanceStore (chunks, chunks_fts, chunks_vec,
chunk_embeddings, query_embeddings) plus a ``meta`` table and a ``cites`` column. Embeddings
use multilingual-e5-small (ONNX), mean-pooled over the attention mask and L2-normalised, the
same as OnnxE5Embedder in the app. Every query GuidanceQueryBuilder can produce is embedded
too, so the phone needs no tokenizer; GuidanceDbParityTest checks the set.

Progress goes to ``.build/progress.json``; open ``dashboard.html`` (served from this folder)
to watch it and to inspect every chunk, exclusion and the coverage check.

Usage (from App Folder/tools/guidance):
  .venv/Scripts/python.exe -I build_guidance_db.py --sources sources \
      --model models/multilingual-e5-small --out ../../app/src/main/assets/guidance.db
"""
from __future__ import annotations

import argparse
import itertools
import json
import os
import re
import sqlite3
import struct
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

MODEL_ID = "intfloat/multilingual-e5-small"
MODEL_REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
DIM = 384
MAX_SEQ = 512
# A block (paragraph, list, table row) up to CHUNK_MAX tokens stays whole. Only a longer one is
# split, at sentence boundaries, into parts of about CHUNK_TARGET tokens with one sentence of
# overlap. Well under MAX_SEQ, so the embedder never truncates (lint checks it).
CHUNK_TARGET = 260
CHUNK_MAX = 380
# A paragraph or list item that loses more than half its sentences to the medicine filter is
# dropped whole: what is left would be a fragment without its context.
MIN_KEPT_FRACTION = 0.5
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


# --- Progress (read by dashboard.html) ---------------------------------------------------

class Progress:
    STAGES = ["load model", "read sources", "chunk", "lint", "embed chunks", "embed queries", "write db", "retrieval check"]

    def __init__(self, path: Path):
        self.path = path
        self.state = {"started": time.time(), "updated": time.time(), "status": "running",
                      "stages": {s: "pending" for s in self.STAGES}, "current": None,
                      "embed": {"done": 0, "total": 0}, "queries": {"done": 0, "total": 0},
                      "sources": [], "chunks": [], "excluded": [], "skipped": [], "coverage": [],
                      "retrieval": [], "never_retrieved": [], "log": [], "result": None}
        self._last = 0.0
        self.flush(force=True)

    def stage(self, name: str, status: str = "running") -> None:
        self.state["stages"][name] = status
        self.state["current"] = name if status == "running" else self.state["current"]
        self.log(f"{name}: {status}")

    def log(self, msg: str) -> None:
        self.state["log"].append({"t": round(time.time() - self.state["started"], 2), "msg": msg})
        print(msg, flush=True)
        self.flush(force=True)

    def flush(self, force: bool = False) -> None:
        now = time.time()
        if not force and now - self._last < 0.25:
            return
        self._last, self.state["updated"] = now, now
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.state, ensure_ascii=False), encoding="utf-8")
        for attempt in range(20):                         # Windows: the dashboard may be reading it
            try:
                os.replace(tmp, self.path)
                return
            except PermissionError:
                time.sleep(0.02 * (attempt + 1))
        if force:
            raise PermissionError(f"could not update {self.path}")

    def fail(self, msg: str) -> None:
        self.state["status"] = "failed"
        if self.state["current"]:
            self.state["stages"][self.state["current"]] = "failed"
        self.log(msg)
        sys.exit(msg)


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


# --- Sources: curated JSONL ----------------------------------------------------------------

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
        r.setdefault("cites", json.dumps([r["url"]]))
        r["origin"] = path.name
        rows.append(r)
    return rows


# --- Sources: structure-aware Markdown chunking ---------------------------------------------

FRONT = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.S)
HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
LIST_ITEM = re.compile(r"^\s*(?:[-*]|\d+\.)\s+")
CITE = re.compile(r"\\\[(\d+)\\\]")                      # \[7\] as written in the review
REF_LINE = re.compile(r"^(\d+)\.\s+\[(.+?)\]\((?:<(.+?)>|(\S+))\)\s*$")
LEAD = re.compile(r"^\*\*(.+?)\*\*\s*(.*)$", re.S)
# Sentence end: . ! or ? (+ closing quote/bracket, + citation markers), space, then a capital,
# digit, quote or bracket. Abbreviations below never end a sentence.
SENT_END = re.compile(r"[.!?][\"”’)]?(?:\\\[\d+\\\])*\s+(?=[A-Z0-9\"“(*'\\])")
ABBREV = ("e.g.", "i.e.", "et al.", "vs.", "An.", "P.", "Dr.", "No.", "approx.", "cf.")


def split_sentences(text: str) -> list[str]:
    out, start = [], 0
    for m in SENT_END.finditer(text):
        head = text[start:m.start() + 1]
        if any(head.endswith(a) for a in ABBREV):
            continue
        out.append(text[start:m.end()].strip())
        start = m.end()
    if text[start:].strip():
        out.append(text[start:].strip())
    return out


def strip_emphasis(text: str) -> str:
    """Remove ** and * emphasis from a whole block (italics can span several sentences)."""
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text, flags=re.S)
    return re.sub(r"(?<![\w*])\*(?=\S)(.+?)(?<=\S)\*(?![\w*])", r"\1", text, flags=re.S)


def clean(text: str) -> str:
    """Markdown → plain text. Only markup goes: citation markers and emphasis."""
    text = strip_emphasis(CITE.sub("", text))
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r" +([.,;:)])", r"\1", text)
    return text.strip()


def slug(s: str) -> str:
    s = re.sub(r"^\d+\.\s*", "", s.lower())
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")[:32] or "section"


def parse_front(path: Path, raw: str) -> tuple[dict, str]:
    m = FRONT.match(raw)
    if not m:
        sys.exit(f"{path.name}: needs front matter (id_prefix, source, title, lang, licence)")
    meta = {}
    for line in m.group(1).splitlines():
        if ":" in line:
            k, v = line.split(":", 1)
            meta[k.strip()] = v.strip()
    for k in ("id_prefix", "source", "title", "lang", "licence"):
        if not meta.get(k):
            sys.exit(f"{path.name}: front matter missing {k}")
    return meta, raw[m.end():]


def parse_blocks(body: str):
    """Yield (heading_path, kind, payload) with kind in para | list | table."""
    path: list[tuple[int, str]] = []
    buf: list[str] = []
    kind = None

    def flush():
        nonlocal buf, kind
        if buf:
            yield ([t for _, t in path], kind, buf)
        buf, kind = [], None

    for line in body.splitlines():
        h = HEADING.match(line)
        if h:
            yield from flush()
            level = len(h.group(1))
            path = [(lv, t) for lv, t in path if lv < level] + [(level, h.group(2).strip())]
            continue
        if not line.strip():
            yield from flush()
            continue
        k = "table" if line.lstrip().startswith("|") else "list" if LIST_ITEM.match(line) else "para"
        if kind == "list" and k == "para" and line.startswith((" ", "\t")):
            buf[-1] += " " + line.strip()                 # wrapped list item
            continue
        if kind and k != kind:
            yield from flush()
        kind = k
        if k == "list":
            buf.append(LIST_ITEM.sub("", line, count=1).strip())
        elif k == "table":
            buf.append(line.strip())
        else:
            buf.append(line.strip())
    yield from flush()


def table_rows(lines: list[str]) -> list[str]:
    cells = [[c.strip() for c in l.strip().strip("|").split("|")] for l in lines]
    header, rows = cells[0], [r for r in cells[2:] if any(r)]
    out = []
    for r in rows:
        if not header[0]:                                 # comparison table: first cell is the row label
            out.append(f"{r[0]}: " + "; ".join(f"{h} — {c}" for h, c in zip(header[1:], r[1:])) + ".")
        else:
            out.append("; ".join(f"{h}: {c}" for h, c in zip(header, r)) + ".")
    return out


class MarkdownChunker:
    def __init__(self, path: Path, e5: E5, progress: Progress):
        self.path, self.e5, self.progress = path, e5, progress
        raw = path.read_text(encoding="utf-8")
        self.meta, body = parse_front(path, raw)
        self.skip = {s.strip().lower() for s in self.meta.get("skip_sections", "").split("|") if s.strip()}
        self.skip_starts = [s.strip() for s in self.meta.get("skip_starts", "").split("|") if s.strip()]
        pat = self.meta.get("exclude_pattern", "").replace("\\\\", "\\")
        self.exclude = re.compile(pat) if pat else None
        self.refs, body = self._take_refs(body)
        self.body = body
        self.rows: list[dict] = []
        self._skipped_text: list[str] = []
        self.counter: dict[str, int] = {}
        self.sentences_in = 0
        self.kept_sentences: list[str] = []

    def _take_refs(self, body: str) -> tuple[dict[int, str], str]:
        refs = {}
        for line in body.splitlines():
            if m := REF_LINE.match(line.strip()):
                refs[int(m.group(1))] = m.group(3) or m.group(4)
        return refs, body

    def _excluded_reason(self, sentence: str) -> str | None:
        plain = clean(sentence)
        for rx in MEDICINE:
            if hit := rx.search(plain):
                return f"medicine/dosing ('{hit.group(0)}')"
        if self.exclude and (hit := self.exclude.search(plain)):
            return f"excluded topic ('{hit.group(0)}')"
        return None

    def _filter(self, where: str, sentences: list[str]) -> list[str]:
        """Drop medicine sentences and skip_starts sentences; drop the whole unit if most of it goes."""
        self.sentences_in += len(sentences)
        reasons = [self._excluded_reason(s) for s in sentences]
        reasons = [r or ("left out on purpose (front matter skip_starts; not guidance)"
                         if any(clean(s).startswith(p) for p in self.skip_starts) else None)
                   for s, r in zip(sentences, reasons)]
        kept = [s for s, r in zip(sentences, reasons) if r is None]
        if sentences and len(kept) / len(sentences) < MIN_KEPT_FRACTION:
            for s, r in zip(sentences, reasons):
                self.progress.state["excluded"].append({"where": where, "text": clean(s),
                                                        "reason": r or "rest of a mostly-medicine block (would be a fragment)"})
            return []
        for s, r in zip(sentences, reasons):
            if r:
                self.progress.state["excluded"].append({"where": where, "text": clean(s), "reason": r})
        return kept

    def _emit(self, path: list[str], subtitle: str | None, parts: list[str], last_cite: list[int]) -> None:
        raw = " ".join(parts) if not any(p.startswith("\n") for p in parts) else "".join(parts)
        cites = [int(n) for n in CITE.findall(raw)]
        text = "\n".join(clean(l) for l in raw.split("\n") if clean(l))
        if not text:
            return
        if cites:
            last_cite[:] = cites
        nums = cites or last_cite
        if not nums or nums[0] not in self.refs:
            self.progress.fail(f"{self.path.name}: chunk in {' > '.join(path)} cites no known source: {text[:80]}")
        urls = list(dict.fromkeys(self.refs[n] for n in nums if n in self.refs))
        section = slug(path[-1]) if len(path) > 1 else "intro"
        self.counter[section] = self.counter.get(section, 0) + 1
        title = " › ".join([re.sub(r"^\d+\.\s*", "", p) for p in path[1:]] + ([subtitle] if subtitle else [])) or self.meta["title"]
        m = self.meta
        self.rows.append({
            "id": f"{m['id_prefix']}-{section}-{self.counter[section]:02d}", "source": m["source"], "url": urls[0],
            "title": title, "lang": m["lang"], "text": text, "licence": m["licence"],
            "topic": m.get("topic", ""), "region": m.get("region", ""), "vector": m.get("vector", ""),
            "evidence": m.get("evidence", ""), "derived_from": m.get("derived_from", ""), "origin": self.path.name,
            "cites": json.dumps(urls), "inherited_citation": not cites,
        })

    def _pack(self, sentences: list[str]) -> list[list[str]]:
        """Whole if it fits; else sentence windows of ~CHUNK_TARGET tokens, one sentence overlap."""
        if self.e5.count_tokens(clean(" ".join(sentences))) <= CHUNK_MAX:
            return [sentences]
        out, cur = [], []
        for s in sentences:
            if cur and self.e5.count_tokens(clean(" ".join(cur + [s]))) > CHUNK_TARGET:
                out.append(cur)
                cur = [cur[-1]]
            cur.append(s)
        if cur and (not out or cur != [out[-1][-1]]):
            out.append(cur)
        return out

    def chunk(self) -> list[dict]:
        blocks = list(parse_blocks(self.body))
        intro: tuple[str, str] | None = None             # (lead/subtitle, intro sentence or "")
        last_cite: list[int] = []
        prev_section = None
        for i, (heading_path, kind, lines) in enumerate(blocks):
            where = " › ".join(heading_path)
            next_kind = blocks[i + 1][1] if i + 1 < len(blocks) and blocks[i + 1][0] == heading_path else None
            if heading_path != prev_section:
                last_cite, intro, prev_section = [], None, heading_path
            if any(h.lower() in self.skip for h in heading_path):
                self._skip(where, "section not guidance (reference list)", lines)
                continue
            if kind == "para":
                text = " ".join(lines)
                if any(clean(text).startswith(p) for p in self.skip_starts):
                    self._skip(where, "left out on purpose (front matter skip_starts; not guidance)", lines)
                    continue
                lead_m = LEAD.match(text)
                lead = clean(lead_m.group(1)).rstrip(".:") if lead_m else None
                if lead and lead.lower() in self.skip:
                    intro = ("__skip__", "")
                    self._skip(where + f" › {lead}", "left out on purpose (front matter skip_sections)", lines)
                    continue
                rest = lead_m.group(2) if lead_m else text
                if lead and not rest.strip():             # label only, e.g. "**Households:**"
                    intro = (lead, "")
                    continue
                sentences = split_sentences(strip_emphasis(text))
                if next_kind == "list" and (text.rstrip().endswith(":") or lead):
                    intro = (lead, text)                   # introduces the list that follows
                    continue
                intro = None
                kept = self._filter(where, sentences)
                for part in self._pack(kept):
                    self._emit(heading_path, lead, part, last_cite)
            elif kind == "list":
                if intro and intro[0] == "__skip__":
                    self._skip(where, "list of a skipped block", lines)
                    intro = None
                    continue
                lead, intro_text = intro if intro else (None, "")
                items = []
                for item in lines:
                    item_lead = LEAD.match(item)
                    kept = self._filter(where + (f" › {lead}" if lead else ""), split_sentences(strip_emphasis(item)))
                    if kept:
                        items.append((clean(item_lead.group(1)).rstrip(".:") if item_lead else None, " ".join(kept)))
                if not intro:                              # independent points: one chunk each
                    for item_lead, it in items:
                        self._emit(heading_path, item_lead, [it], last_cite)
                else:                                      # items under one intro: keep together
                    for group in self._pack_items(strip_emphasis(intro_text), [it for _, it in items]):
                        self._emit(heading_path, lead, group, last_cite)
                intro = None
            elif kind == "table":
                lead = intro[0] if intro and intro[0] != "__skip__" else None
                rows = [r for r in table_rows(lines) if self._filter(where + " › table", [strip_emphasis(r)])]
                comparison = not lines[0].strip().strip("|").split("|")[0].strip()
                whole = [strip_emphasis(r) for r in rows]
                if comparison and self.e5.count_tokens(clean("\n".join(whole))) <= CHUNK_MAX:
                    # A comparison table is one idea: keep its rows together.
                    self._emit(heading_path, lead, ["\n- " + r for r in whole], last_cite)
                else:                                      # ranked/record table: one row per chunk
                    for r in whole:
                        self._emit(heading_path, lead, [r], last_cite)
                intro = None
        self._coverage()
        return self.rows

    def _pack_items(self, intro_text: str, items: list[str]) -> list[list[str]]:
        head = [intro_text] if intro_text else []
        out, cur = [], []
        for it in items:
            line = "\n- " + it
            if cur and self.e5.count_tokens(clean(" ".join(head + cur + [it]))) > CHUNK_MAX:
                out.append(head + cur)
                cur = []
            cur.append(line)
        if cur:
            out.append(head + cur)
        return out

    def _skip(self, where: str, reason: str, lines: list[str]) -> None:
        self.progress.state["skipped"].append({"where": where, "reason": reason, "lines": len(lines),
                                               "text": clean(" ".join(lines))[:400]})
        self._skipped_text.append(clean(" ".join(lines)))

    def _coverage(self) -> None:
        """Every sentence of the source must be in a chunk word for word, or be listed as left out."""
        norm = lambda s: re.sub(r"\s+", " ", s).strip()
        kept = norm(" ".join(r["title"] + " " + r["text"].replace("\n- ", " ") for r in self.rows))
        left_out = norm(" ".join(e["text"] for e in self.progress.state["excluded"]) + " " + " ".join(self._skipped_text))
        found = excluded = 0
        missing = []
        for heading_path, kind, lines in parse_blocks(self.body):
            if any(h.lower() in self.skip for h in heading_path):
                continue                                  # reference lists: listed under skipped
            units = table_rows(lines) if kind == "table" else lines if kind == "list" else [" ".join(lines)]
            for u in units:
                lead_m = LEAD.match(u)
                if lead_m and not lead_m.group(2).strip():
                    continue                              # label only: it is in the chunk titles
                pieces = [u] if kind == "table" else split_sentences(strip_emphasis(u))
                for s in pieces:
                    c = norm(clean(s))
                    if not c:
                        continue
                    if c in kept:
                        found += 1
                    elif c in left_out:
                        excluded += 1
                    else:
                        missing.append(c)
        self.progress.state["coverage"].append({
            "source": self.path.name, "sentences": found + excluded + len(missing), "in_chunks": found,
            "left_out": excluded, "missing": missing, "chunks": len(self.rows)})


# --- Lint ---------------------------------------------------------------------------------

def lint(rows: list[dict], e5: E5, progress: Progress) -> None:
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
        r["tokens"] = e5.count_tokens(f"{r['title']}\n{r['text']}")
        if r["tokens"] > MAX_SEQ - 16:
            problems.append(f"{r['id']}: {r['tokens']} tokens, over the embedder limit (would be truncated)")
    if problems:
        progress.fail("corpus lint failed:\n  " + "\n  ".join(problems))


# --- Database ----------------------------------------------------------------------------

SCHEMA = """
CREATE TABLE chunks(rowid INTEGER PRIMARY KEY, id TEXT UNIQUE, source TEXT, url TEXT,
                    title TEXT, lang TEXT, text TEXT,
                    licence TEXT, topic TEXT, region TEXT, vector TEXT, evidence TEXT,
                    derived_from TEXT, origin TEXT, cites TEXT);
CREATE VIRTUAL TABLE chunks_fts USING fts5(title, text, content='chunks', content_rowid='rowid');
CREATE TABLE chunk_embeddings(rowid INTEGER PRIMARY KEY, embedding BLOB);
CREATE TABLE query_embeddings(query TEXT PRIMARY KEY, embedding BLOB);
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
"""


def write_db(out: Path, rows: list[dict], e5: E5, progress: Progress) -> None:
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
            "vector", "evidence", "derived_from", "origin", "cites")
    progress.stage("embed chunks")
    progress.state["embed"] = {"done": 0, "total": len(rows)}
    for rowid, r in enumerate(rows, 1):
        db.execute(f"INSERT INTO chunks(rowid,{','.join(cols)}) VALUES (?{',?' * len(cols)})",
                   (rowid, *[r.get(c, "") for c in cols]))
        v = f32le(e5.embed(f"{r['title']}\n{r['text']}", is_query=False))
        db.execute("INSERT INTO chunk_embeddings(rowid, embedding) VALUES (?,?)", (rowid, v))
        db.execute("INSERT INTO chunks_vec(rowid, embedding) VALUES (?,?)", (rowid, v))
        progress.state["embed"]["done"] = rowid
        progress.flush()
    progress.stage("embed chunks", "done")
    db.execute("INSERT INTO chunks_fts(chunks_fts) VALUES ('rebuild')")

    progress.stage("embed queries")
    queries = all_queries()
    progress.state["queries"] = {"done": 0, "total": len(queries)}
    for i, q in enumerate(queries, 1):
        db.execute("INSERT INTO query_embeddings(query, embedding) VALUES (?,?)", (q, f32le(e5.embed(q, is_query=True))))
        progress.state["queries"]["done"] = i
        progress.flush()
    progress.stage("embed queries", "done")

    progress.stage("write db")
    meta = {
        "model": f"{MODEL_ID}@{MODEL_REVISION}", "dimensions": str(DIM), "pooling": "mean, L2-normalised",
        "prefixes": "passage: / query:",
        "chunking": f"structure-aware: one paragraph / list / table row per chunk, never inside a sentence; "
                    f"blocks over {CHUNK_MAX} tokens split at sentences (~{CHUNK_TARGET}, 1-sentence overlap)",
        "chunks": str(len(rows)), "queries": str(len(queries)),
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    db.executemany("INSERT INTO meta(key, value) VALUES (?,?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(out)
    progress.stage("write db", "done")


# --- Retrieval check (same steps as HybridRetriever) --------------------------------------

def fts_query(q: str) -> str | None:
    terms = []
    for t in re.findall(r"[^\W_]+", q.lower()):
        if len(t) > 1 and t not in terms:
            terms.append(t)
    return " OR ".join(f'"{t}"' for t in terms) if terms else None


def report(out: Path, progress: Progress, k: int = 5, candidates: int = 30, rrf_k: int = 60) -> None:
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
        progress.state["retrieval"].append({"query": q, "top": top})
        print(f"  {q[:70]:<70} -> {', '.join(top)}")
    never = sorted(set(ids.values()) - set(seen))
    progress.state["never_retrieved"] = never
    progress.state["retrieved_counts"] = seen
    print(f"\nChunks never in any top {k}: {len(never)}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sources", type=Path, default=Path("sources"))
    ap.add_argument("--model", type=Path, default=Path("models/multilingual-e5-small"))
    ap.add_argument("--out", type=Path, default=Path("../../app/src/main/assets/guidance.db"))
    ap.add_argument("--progress", type=Path, default=Path(".build/progress.json"))
    args = ap.parse_args()

    progress = Progress(args.progress)
    progress.stage("load model")
    e5 = E5(args.model)
    progress.stage("load model", "done")

    progress.stage("read sources")
    jsonl, md = sorted(args.sources.glob("*.jsonl")), sorted(args.sources.glob("*.md"))
    progress.state["sources"] = [{"file": p.name, "kind": p.suffix[1:], "bytes": p.stat().st_size} for p in jsonl + md]
    progress.stage("read sources", "done")

    progress.stage("chunk")
    rows: list[dict] = []
    for p in jsonl:
        got = load_jsonl(p)
        rows += got
        progress.log(f"{p.name}: {len(got)} curated passages (kept as written)")
    for p in md:
        got = MarkdownChunker(p, e5, progress).chunk()
        rows += got
        progress.log(f"{p.name}: {len(got)} chunks")
    if not rows:
        progress.fail(f"no sources in {args.sources}")
    progress.stage("chunk", "done")

    progress.stage("lint")
    lint(rows, e5, progress)
    progress.state["chunks"] = [{k: r.get(k) for k in ("id", "title", "url", "text", "origin", "tokens", "inherited_citation")}
                                | {"cites": json.loads(r["cites"])} for r in rows]
    progress.stage("lint", "done")

    write_db(args.out, rows, e5, progress)

    progress.stage("retrieval check")
    report(args.out, progress)
    progress.stage("retrieval check", "done")
    langs = {l: sum(r["lang"] == l for r in rows) for l in ("en", "es")}
    progress.state["result"] = {"out": str(args.out), "kb": args.out.stat().st_size // 1024, "chunks": len(rows),
                                "langs": langs, "queries": len(all_queries())}
    progress.state["status"] = "done"
    progress.log(f"wrote {args.out} ({args.out.stat().st_size // 1024} KB): {len(rows)} chunks {langs}, {len(all_queries())} queries")


if __name__ == "__main__":
    main()
