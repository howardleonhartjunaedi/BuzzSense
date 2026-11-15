# guidance.db builder

Builds `app/src/main/assets/guidance.db`, the prevention-guidance corpus the app's
`GuidanceStore` searches (FTS5 + sqlite-vec, fused with RRF). The schema is documented
on `GuidanceStore`; this script adds metadata columns (`licence`, `topic`, `region`,
`vector`, `evidence`, `derived_from`, `origin`) and a `meta` table.

## Setup (once)

```bash
cd "App Folder/tools/guidance"
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
.venv/Scripts/python.exe -I -c "from huggingface_hub import hf_hub_download as d; [d('intfloat/multilingual-e5-small', f, revision='614241f622f53c4eeff9890bdc4f31cfecc418b3', local_dir='models/multilingual-e5-small') for f in ('onnx/model.onnx', 'onnx/tokenizer.json')]"
```

The model is `intfloat/multilingual-e5-small` (MIT), about 490 MB, pinned to revision
`614241f`. `.venv/` and `models/` are git-ignored.

## Build

```bash
.venv/Scripts/python.exe -I build_guidance_db.py
```

The script lints the corpus first (required fields, http(s) URLs, unique ids, and no
medicine or dosing text, using the same patterns as `HealthSafetyValidator`). It then
embeds every chunk (`passage:`) and all 32 agent queries (`query:`), writes the DB and
prints the top 5 hybrid hits per query, plus any chunk that is never retrieved.
Afterwards, run `./gradlew :shared:jvmTest`. `GuidanceDbParityTest` opens the DB through
the app's own code and fails if the query templates in this script and
`GuidanceQueryBuilder` have drifted apart.

## Adding guidance

- **Curated passages:** add a `.jsonl` file to `sources/`, one object per line, with
  `id, source, url, title, lang (en|es), text` and optionally `topic, region, vector,
  evidence`. An optional `<name>.meta.json` sidecar supplies `derived_from` and `licence`.
  Ids are cited by the LLM and by `FallbackChecklist`, so never rename a shipped id.
- **Saved pages and reviews:** add a `.md` file with front matter (`id_prefix, source, title,
  lang, licence`, optional `url, topic, region, vector, evidence, derived_from`). It is
  chunked by its own structure, never inside a sentence:
  - a paragraph is one chunk; a bold lead (`**House screening.**`) becomes part of the title;
  - a list stays with the paragraph or bold label that introduces it; a list with no intro
    gives one chunk per item;
  - a ranked table gives one chunk per row (`Header: value; …`); a comparison table (empty
    first header cell) stays whole;
  - only a block over 380 tokens is split, at sentence boundaries (~260 tokens, one
    sentence of overlap).

  Only markup is removed (citation markers, bold/italic). Each chunk's `url` is the first
  source it cites (numbered `\[n\]` markers resolved against the file's `## Sources` list);
  a chunk that cites nothing takes the last citation in its section, and the build fails if
  there is none. All cited links go in the `cites` column. Front matter can leave text out:
  `skip_sections` (headings or bold leads), `skip_starts` (paragraphs or sentences starting
  with a phrase) and `exclude_pattern` (a regex, on top of the medicine filter). A paragraph
  or item that loses more than half its sentences is dropped whole, never left as a
  fragment.

## Progress dashboard

The build writes `.build/progress.json` (git-ignored). To watch it, serve this folder and
open the dashboard:

```bash
.venv/Scripts/python.exe -I -m http.server 8765 --bind 127.0.0.1   # then open http://127.0.0.1:8765/dashboard.html
```

It shows the stages, the embedding progress, every chunk with its tokens and source links,
everything left out and why, the top 5 per agent query, and a coverage check. That check
fails loudly if any sentence of a `.md` source is neither in a chunk word for word nor
listed as left out.

## Current corpus

- `sources/malaria_indonesia_review.md`: the team's full malaria evidence review (Indonesia
  focus), 39 chunks. 159 of its 195 sentences are in chunks word for word. 36 are left out
  on purpose: medicines, chemoprophylaxis, SMC, vaccines, the two disclaimers and the
  hackathon notes; the reference lists are skipped too. Citation markers were added to the
  lines that cited nothing (listed in its front matter), and no text was changed.
- `sources/malaria_indonesia_review.jsonl`: the 17 earlier hand-written passages from the
  same review. They are kept because their ids are shipped citations.

Only the 3M Plus passage and the malaria-vs-dengue rows cover dengue, so the San Juan
dengue alert still needs WHO, CDC, PAHO and PR Health dengue pages in English and Spanish.

This project is a prototype and is not medical advice.
