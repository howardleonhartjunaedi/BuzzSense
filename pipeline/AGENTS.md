# AGENTS.md (Data Pipeline Leaf)

The code lives here (in git); the data and the venv stay in `O:\Veritas AI\Audio File\` (not in git: `data/raw/` is 9.8 GB, the stores ~1.4 GB). Run every script **from `Audio File/`** with its venv in isolated mode, so `data/...` paths resolve there: `.venv/Scripts/python.exe -I "../App Folder/pipeline/<script>.py" ...`. Set up the venv with `requirements.txt` in this folder.

## Commands (cwd: `Audio File/`)
| Intent | Command | Notes |
|:---|:---|:---|
| Quick smoke build | `.venv/Scripts/python.exe -I "../App Folder/pipeline/build_rag.py" --data data/raw --out data/rag_test --limit 2000` | Minutes; use before any full run |
| Full store build | `.venv/Scripts/python.exe -I "../App Folder/pipeline/build_rag.py" --data data/raw --out data/rag` | Hours, ~365k windows; run in the background |
| Progress dashboard | `.venv/Scripts/python.exe -I "../App Folder/pipeline/dashboard_server.py" 8765 [data/rag]` | Reads `progress.json` etc. from the given folder; localhost only |
| Detector eval | `.venv/Scripts/python.exe -I "../App Folder/pipeline/eval_retrieval.py" --rag data/rag` | kNN vs majority vs f0 rule, per domain; writes `eval_retrieval.json` |
| Kotlin parity fixture | `.venv/Scripts/python.exe -I "../App Folder/pipeline/export_parity_fixture.py" --data data/raw --rag data/rag --out "../App Folder/shared/src/jvmTest/resources/parity_fixture.json"` | Then copy `data/rag/feature_spec.json` next to it and into `app/src/main/assets/` |
| App acoustic store | `.venv/Scripts/python.exe -I "../App Folder/pipeline/export_slim_store.py" --rag data/rag --out data/rag_slim` | Train split only for the app's kNN; checks neighbours match the full store. Copy the result to `App Folder/app/src/main/assets/` (git-ignored, 127 MB) |
| Feature experiments | `.venv/Scripts/python.exe -I "../App Folder/pipeline/experiments.py" --data data/raw --out data/rag` | Leave-one-domain-out tuning, then one test run |
| Full re-scan, hi-res features (v3) | `.venv/Scripts/python.exe -I "../App Folder/pipeline/experiments_v3.py" --data data/raw --out data/rag_v3 --cache <dir on C:>` | ~30 min; every HumBugDB window + 96-band spectrum; dashboard at `data/rag_v3/dashboard.html` (serve that folder). Negative result 2026-11-15: on held-out sites v3 caught 9% of Aedes vs v2's 36% (all variants >5% false alarms), and indexing all HumBugDB windows also hurt, so the protocol kept v2 and the app is unchanged |
| Site-balancing check | `.venv/Scripts/python.exe -I "../App Folder/pipeline/experiments_sitebal.py" --features data/rag/exp_features.npz --out data/rag_sitebal` | Leave-one-site-out on train/val only (~10 min); needs `exp_features.npz` from `experiments.py`. Negative result 2026-11-15: capping or equalising each site's kNN votes did not beat plain k=15, so the app is unchanged (details in the script docstring) |

## local-constraints
- `data/raw/` is read-only input. Scripts read audio straight from the zips (`zip::member` paths); do not extract into `data/raw/` unless asked (`unzip_all.py` exists for that).
- Write experimental builds to a new folder (`data/rag_<name>/`), never over `data/rag/` without asking: the app and the eval read it.
- Embedding normalisation (`mu`, `sd`) uses the train split only. HumBugDB is split per recording by `md5(id) % 5 == 0 → test`; never split windows of one recording across splits.
- Tuning protocol in `experiments.py` is fixed: tune on BioDCASE train+val by leaving out D1, D3 or D4 in turn, pick with Aedes false alarm ≤ 5%, then run the official test once. Transductive variants (e.g. `s1_dom`) are reported, never chosen.
- Any change to `features()`, `_mel_fb()`, `TILT_RESID` or the constants means a new `feature_spec.json` `version` and a matching Kotlin change in `shared/` (see the root contract).
- Keep the pipeline neural-free unless asked; dependencies are pinned in `requirements.txt`. `sqlite-vec` must stay at the version that built the stores and the app's `libvec0.so` (`tools/sqlite-vec`). Add packages to the venv, not globally.
- `progress.json` writes are best-effort (Windows file locks); never let progress or dashboard code fail a build.
- Never commit data, stores, `.venv` or `__pycache__`; this folder holds code and docs only.
