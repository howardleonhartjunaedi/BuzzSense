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
- **Saved pages:** add a `.md` file with front matter (`id_prefix, source, url, title,
  lang, licence`). It is chunked on headings, then into windows of about 400 tokens with
  60 tokens of overlap.

## Current corpus

`sources/malaria_indonesia_review.jsonl` has 17 English passages paraphrased from the
team's malaria evidence review (Indonesia focus). Each passage cites the primary source
behind its finding (Cochrane, WHO, Kemenkes, CDC and others). Medicines, prophylaxis
regimens, vaccines and SMC are left out on purpose. Only one passage is about dengue
(3M Plus), so the San Juan dengue alert still needs WHO, CDC, PAHO and PR Health dengue
pages, in English and Spanish.

For a mosquito that is not Aedes, `GuidanceQueryBuilder` adds malaria terms, so those
queries retrieve the net, biting-time and house-screening passages. Five passages are
still never in a top 5: the Sumba trial, travel, indoor spraying, net use and the
Indonesia burden passage. The fever-test passage is retrieved only for Spanish Aedes
queries.

This project is a prototype and is not medical advice.
