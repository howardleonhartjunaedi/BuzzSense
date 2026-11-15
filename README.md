# BuzzSense
**Track:** Health
**Team:** Howard Leonhart Junaedi (team lead), Jansen Thoe, Travis Ainsley Wilvicious

> This project is a prototype and is not medical advice.

> **Status: in active development.** The detector, the agent and all 13 app screens work end to end on an Android emulator. Field testing, the dengue guidance pages and the review/share screen are still to come (see [Current limitations](#current-limitations) and [What we'd do next](#what-wed-do-next)).

## The problem
Mosquito-borne diseases kill more than 700,000 people a year, and more than 4 billion people live at risk of dengue, mostly in tropical countries such as Brazil, the Philippines and Indonesia. Indonesia recorded about 161,000 dengue cases and more than 600 deaths in 2025, and the Ministry of Health aims for zero dengue deaths by 2030. Spotting high-risk mosquitoes early is how outbreaks are stopped. Today that work relies on *jumantik* (larvae-monitoring) volunteers and puskesmas (community health centre) staff, and families have no easy way to tell whether the mosquito buzzing in their house carries disease or is harmless.

## What we built
An Android app (Kotlin Multiplatform + Jetpack Compose) that:

- **Input:** a few seconds of a mosquito's buzz recorded on the phone's microphone.
- **Output:** a verdict, *likely Aedes*, *other mosquito*, *no mosquito* or *Unsure*, plus a prevention checklist where every step cites a passage from the app's local guidance library. The app can write the checklist in English or Spanish, but the library is English-only for now and mostly about malaria; the WHO/CDC/PAHO dengue pages are being added.
- **Use:** tap record and hold the phone near the buzz. The app shows the result and a "what to do now" checklist. The plan is for a parent to review and edit it before sharing it with family or neighbours, but the review/share screen isn't built yet.

Privacy: the audio never leaves the phone. It stays in memory and is wiped after analysis. Only the label, scores, weather and the retrieved text go to the LLM.

## How it works
**Data**
- **HumBugDB** (wild and lab recordings from Kenya, Tanzania, Thailand, the UK and the USA) and **BioDCASE mosquito** (domains D1–D5).
- Audio is resampled to 8 kHz and cut into 0.62 s windows: **365,555 windows** in total (aedes 101k, culex 119k, other mosquito 110k, no mosquito 35k).
- Splits are made **per recording or domain, never per window**: HumBugDB by `md5(id) % 5`, BioDCASE by its official train/val/test. Train is 289k windows, val 31k, test 46k. BioDCASE domain D2 was kept out of tuning.
- Cleaning: species were mapped to 4 classes. A cubic "mic tone" fit is removed from the spectrum so different phone and recorder microphones look alike. Features are z-scored using train statistics only.

**Why sound works, and why pitch alone is not enough**
Phones can record mosquito wingbeats well enough to identify species (Mukundarajan et al., 2017). But wingbeat pitches overlap: female *Ae. aegypti* (the ones that bite) buzz at about 460–664 Hz and males at about 711–982 Hz, which overlaps with *Ae. albopictus* and *Cx. quinquefasciatus*. Heat also raises wingbeat pitch, so a pitch cut-off that works in a lab drifts in a hot Jakarta house. That is why we compare against a single-pitch rule and use the whole spectrum shape instead.

**Local fit (Jakarta)**
The classes follow household vector ecology in Jakarta: *Ae. aegypti* and *Ae. albopictus* are grouped together as dengue risk, *Culex* (mainly *Cx. quinquefasciatus*) is the common nuisance mosquito, and the rest is other mosquitoes or background.

**Model**
1. **Detector (on device, no neural net):** an 80-dim embedding (40 log-mel band means after mic-tone removal, plus 40 band std-devs) and **k-nearest-neighbours (k = 15)** over a 127 MB SQLite + sqlite-vec store of training windows. A speech gate and an *Unsure* band stop it from guessing. We chose kNN because it is small, explainable ("these 15 known recordings sound like yours"), runs offline, and needs no GPU training. The feature spec is shared in `feature_spec.json` (v2-tilt), and a parity test checks that the Kotlin and Python features match.
2. **Guidance (RAG):** a local `guidance.db` of public health passages, searched with hybrid FTS5 + e5 vector search (RRF).
3. **Agent:** a fixed [Koog 1.3.0](https://github.com/JetBrains/koog) graph runs *detect → context → retrieve → compose → validate*. The LLM (Gemini Flash-Lite, with Claude/OpenAI and on-device Gemma as fallbacks) is used only to write the checklist from the retrieved passages. The validator rejects answers with missing citations, diagnoses, medicine or dosing. If every model fails, the app shows a fixed fallback checklist.

## Results
Held-out test set (46,367 windows, scored per 0.62 s window, not per recording; the official test was run once, after the variant was picked on leave-one-domain-out validation):

| Method | Aedes recall | Aedes false alarms | 4-class accuracy |
|:---|---:|---:|---:|
| Majority class (always "culex") | 0.0% | 0.0% | 29.6% |
| f0 band rule (425–809 Hz = Aedes) | 76.6% | 44.2% | 17.0% |
| **Our kNN (k = 15)** | **73.5%** | **3.5%** | **81.1%** |

The kNN catches about as many Aedes as the pitch rule, with **12× fewer false alarms**.

**Where it fails**
- **New recording setups.** Averaged per domain, Aedes recall drops to about 46%. On the reserved BioDCASE D2 it is 9.5%, on D4 it is 0%, and on HumBugDB Tanzania about 9%. The overall number is lifted by the big D5 domain (78%). We think the model partly learns *the microphone and room*, not just the wingbeat. Mic-tone removal helped (D3: 0 → 100% in validation) but did not fix this.
- **Aedes vs Culex confusion.** 11% of Aedes windows are called Culex, because their wingbeat pitches overlap, especially for females.
- **Things we tried that didn't help:** higher-resolution 96-band features (v3) caught only 9% of Aedes on held-out sites, and balancing kNN votes per site did not beat plain k = 15.
- **Not tested in the field.** It has run on an emulator with real clips, but not yet on a phone near a live mosquito, and not against real household noise.
- **No Indonesian recordings.** None of the training data comes from Indonesia or Jakarta, so the local fit above is in the class design only, not in the data yet.

## Current limitations
We are still building BuzzSense. Besides the detector weaknesses above, these parts are unfinished:
- **Decision thresholds** (when to say *Unsure*) are placeholders and have not yet been picked on validation data.
- **Speech gate** (stops clips with people talking) is tuned on dataset clips and synthetic speech only, not real household conversation.
- **Guidance library** has 56 English passages, mostly about malaria; only a few cover dengue, and none are in Spanish or Indonesian. The offline fallback checklist uses placeholder citations until the dengue pages are added.
- **LLM fallbacks:** the Gemini tier is tested live; the Claude/OpenAI tier has no key screen or live test yet, and the on-device Gemma tier has never run on a phone.
- **Missing screens:** parent review and sharing, a "How it decided" panel, history, and file upload.
- **Accuracy is per window.** Combining all windows in a recording into one verdict, and scoring it that way, is not done yet.


## What we'd do next
1. **Collect Jakarta field recordings.** Work with puskesmas and jumantik teams in areas like Tomang to record native Aedes and Culex on mid-range Android phones, at real household temperatures and with real background noise (KRL trains, traffic, fans, rain, adzan). Domain shift is the biggest weakness, and data that matches real use is what's missing.
2. **Community vector-risk map.** Add a lightweight dashboard that collects anonymous volunteer detections (label and rough area only, never audio) so puskesmas can target larvae eradication and fogging before outbreaks.
3. **Finish the dengue guidance corpus:** add the WHO/CDC/PAHO/PR Health dengue pages in English, Spanish and Bahasa Indonesia (it is malaria-heavy today) and evaluate citation accuracy end to end.
4. **Finish the app:** pick the *Unsure* thresholds on validation data, build the review/share screen, test on real phones, and add a Bahasa Indonesia interface for jumantik volunteers.

## Sources
**Datasets**
- HumBugDB: Kiskin et al., 2021, https://github.com/HumBug-Mosquito/HumBugDB (Zenodo: https://zenodo.org/record/4904800)
- BioDCASE mosquito task: https://biodcase.github.io/
- Guidance library today: a malaria-in-Indonesia review plus curated public-health passages (`tools/guidance/sources/`)
- Planned: WHO dengue (https://www.who.int/news-room/fact-sheets/detail/dengue-and-severe-dengue), CDC (https://www.cdc.gov/dengue/), PAHO (https://www.paho.org/en/topics/dengue), Puerto Rico Department of Health pages, and DengAI (DrivenData, https://www.drivendata.org/competitions/44/dengai-predicting-disease-spread/) for a dengue risk forecast (interface stubbed, not yet used)

**Research**
- Mukundarajan et al. (2017), *Using mobile phones as acoustic sensors for high-throughput mosquito surveillance*, eLife: https://elifesciences.org/articles/27854
- Cator et al. (2022), *Male and Female Mosquito Attraction to Sound*, Annals of the Entomological Society of America: https://doi.org/10.1093/aesa/saab045
- Fernandes et al. (2020), *Detecting Aedes aegypti mosquitoes through audio classification with convolutional neural networks*, Computers in Biology and Medicine: https://doi.org/10.1016/j.compbiomed.2020.104152
- Abuzz dataset (Stanford / Dryad): mobile-phone wingbeat recordings of 20 species, a candidate test set of real phone audio for the next step

**Public health statistics**
- WHO and PAHO (2026): Global Dengue Surveillance Dashboard and Weekly Epidemiological Record (Oct 2026), for the ~4 billion people at risk
- Kementerian Kesehatan RI / InfoPublik (2026): Indonesia's 2025 dengue figures and the 2030 zero-death target
- BPJS Kesehatan dengue spending, Jan–Aug 2025 (via detikHealth); CNN Indonesia (2025) on children's share of deaths; Universitas Tarumanagara larvae survey in Tomang, Jakarta

**Pretrained models and libraries**
- Gemini Flash-Lite (Google AI Studio), Claude (Anthropic), OpenAI as fallbacks, Gemma via LiteRT on device
- multilingual-e5-small embedder (intfloat, MIT) and Silero VAD (ONNX Runtime)
- Koog 1.3.0 (JetBrains), sqlite-vec 0.1.9, SQLite FTS5, numpy, scipy, soundfile
- UI designed in Google Stitch; colours, fonts and spacing rules in `DESIGN.md` and the design tokens

**AI tools**
- Claude Code (Anthropic) helped write code, run experiments and write documentation.
