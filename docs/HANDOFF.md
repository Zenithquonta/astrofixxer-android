# AstroFixxer Handoff Log

A running log. **Add a new entry at the top every time code is implemented or changed**, using the template at the bottom. Anyone picking up the work (a person or an AI agent) should be able to continue from the latest entry without reading the chat history.

## Where things are

| Item | Location |
|---|---|
| Upstream web app (read-only reference) | `github.com/AadidevRaizada/AstroFixxer`, analysed at commit `6af76ce` |
| This planning repo | `zenithquonta/astrofixer-baby`, branch `claude/astrofixxter-analysis-xouhoi` |
| Local clone of upstream (not committed, 4.7 GB) | `AstroFixxer/` (gitignored) |
| Stitch UI prompt | `docs/STITCH_UI_PROMPT.md` |
| Implementation plan | `docs/IMPLEMENTATION_PLAN.md` |
| Codebase analysis (web page) | https://claude.ai/artifact/SMf5adtidjB5CsJt5ZrxNW |

## Current status

| Phase (see plan) | Status |
|---|---|
| 0. Analysis, UI prompt, plan | Done |
| 1. Web app bug fixes (B1–B13) | Not started |
| 2. Android project setup | Not started |
| 3. Astronomy core port | Not started |
| 4. Stellarium offline data | Importer done and tested; Android loader and web swap not started |
| 5. Compose UI (Stellarium-style) | Waiting on Stitch designs |
| 5b. Offline events | Meteor showers done (data); planet events and ISS not started |
| 6. AstroGuide | Not started |
| 7. Release | Not started |

---

## Entries

### 2026-09-24: Stellarium offline data importer + Stellarium-style UI brief

**What was done**
- The user asked for Stellarium data for all celestial objects and events, working offline, and a Stellarium-like UI.
- Decided **not** to use Stellarium's Remote Control API: it needs Stellarium desktop running on the same network and only serves plain HTTP, which an HTTPS web app can't call. Instead, Stellarium's open data files are converted at build time and bundled, so nothing needs the internet in the field.
- Added `tools/stellarium_import/`:
  - `fetch_stellarium.sh`: sparse checkout of Stellarium at pinned commit `9910a2f`.
  - `build_sky_data.py`: converts the deep-sky catalogue, names, sky cultures (modern + Indian) and meteor showers into:
    - `data/web/jsdb_stellarium.js`: drop-in data for the web app.
    - `data/android/sky_catalog.json.gz`: full catalogue.
    - `data/events/meteor_showers.json`: 2026–2028.
  - `test_build_sky_data.py`: 7 stdlib `unittest` tests.
- Rewrote `docs/STITCH_UI_PROMPT.md` around a Stellarium-style planetarium UI: full-screen realistic sky, slide-out toolbars, info overlay, time travel, Events screen, Western/Indian sky cultures, offline states. It keeps all AstroFixxer telescope features (Align, guidance panel, watch lists, user objects, Night mode).
- Updated `docs/IMPLEMENTATION_PLAN.md`:
  - Offline-first rule.
  - New licence rows.
  - Phase 4 rewritten around the importer.
  - New Phase 5b (offline events: meteor showers, planet events, ISS via bundled TLE + SGP4).
  - Stellarium-style rendering work in Phase 5.
  - New timeline (6–7 weeks without AstroGuide).

**Files changed**
- `tools/stellarium_import/fetch_stellarium.sh`, `build_sky_data.py`, `test_build_sky_data.py` (new)
- `data/web/jsdb_stellarium.js`, `data/android/sky_catalog.json.gz`, `data/events/meteor_showers.json` (new, generated)
- `docs/STITCH_UI_PROMPT.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/HANDOFF.md`
- `.gitignore`: ignore `.cache/` and `__pycache__/`

**How to verify**
```bash
tools/stellarium_import/fetch_stellarium.sh .cache/stellarium
python3 tools/stellarium_import/build_sky_data.py --stellarium .cache/stellarium \
  --hyg <AstroFixxer>/western_constellations_atlas_of_space/data/hygdata_v3/hygdata_v3.csv \
  --out data --years 2026-2028 --apply-to <AstroFixxer>/astrofixxer.html --apply-out /tmp/astrofixxer_stellarium.html
STELLARIUM_DIR=.cache/stellarium python3 -m unittest tools/stellarium_import/test_build_sky_data.py
```
Results in this session:
- The build takes ~3 s.
- Web data: 18,842 objects vs 9,759 before.
- Android catalogue: 93,997 deep-sky objects, 8,912 stars.
- 129 meteor-shower entries.
- 7/7 tests pass.
- The patched `astrofixxer.html` loads in headless Chromium, and search resolves M31, Andromeda Galaxy, Orion Nebula, NGC7000, Pleiades, Trifid, Cr399, Jupiter and Lubdhaka (Sirius). The only console error is the service worker refusing `file://`, which is expected.

**Decisions**
- Stellarium's catalogue uses SIMBAD-style type codes (`Gx`, `AGx`, `RNe`, `Cl`, ...) that aren't listed in its file header. Missing them silently dropped M31 in the first run; the type map now covers them, and a test checks that every Messier object except M40/M73 has a type.
- Clusters with nebulosity (`C+N`: Eagle, Trifid) are drawn as nebulae. The Pleiades are overridden to open cluster.
- Star positions still come from HYG v3. Stellarium's star catalogues are binary downloads and are deferred (Plan 4.2).
- Generated outputs are committed, following the upstream repo's practice of committing `jsdb.js`, so the app builds without network.

**Known issues / not done**
- The web app still embeds the old OpenNGC data. The swap is one command (`--apply-to`) but changes the upstream file; waiting on open question 1.
- 141 globular clusters vs 191 before: Stellarium has no magnitude for some faint globulars, and objects without a magnitude are left out of the web build (they are in the Android catalogue).
- ISS/satellite passes: CelesTrak is blocked in this sandbox, so TLE download and SGP4 are planned (Phase 5b) and untested.
- Meteor peak times are the typical solar longitude converted with a low-precision Sun formula (~0.01°). They are accurate to within hours, and outbursts in special years aren't modelled beyond what Stellarium lists.

**Next step**
- Run the Stitch prompt; save designs under `docs/design/`.
- Answer the open questions below. The Android project setup (Phase 2) and the astronomy core port (Phase 3) are unblocked.

### 2026-09-24: Analysis and planning (no code changes)

**What was done**
- Cloned `AadidevRaizada/AstroFixxer` and analysed it: a single 10,557-line `astrofixxer.html` PWA forked from AstroHopper (GPLv3), with a Python build step (`create_data.py`, `po/tojson.py`) deployed on Vercel, and three data submodules (OpenNGC, vsop87-multilang, western_constellations_atlas_of_space).
- Wrote `docs/STITCH_UI_PROMPT.md`, which lists every existing screen and control (sky map, align flow, FOV, watch list, search, settings layers, user objects, GPS override, quick start, manual) plus the new guidance panel and AstroGuide screen, with Night-mode and RTL requirements.
- Wrote `docs/IMPLEMENTATION_PLAN.md`: 13 verified bugs (B1–B13) with fixes, and a 7-phase plan for the Kotlin/Compose port.
- Added `.gitignore` so the 4.7 GB upstream clone is never committed.

**Corrections to the earlier analysis page**
- The analysis said `cert.pem` was a committed certificate. It is actually an **empty 2-byte file**, so nothing is leaked, but it makes `pyserver.py` fail (bug B2).
- The analysis cited the `UT1-UTC` TODO at line 9102. The correct line is **7902**.

**Files changed**
- `docs/STITCH_UI_PROMPT.md` (new)
- `docs/IMPLEMENTATION_PLAN.md` (new)
- `docs/HANDOFF.md` (new)
- `.gitignore` (new)

**How to verify**
- Read the three docs. No build or tests exist yet.

**Decisions**
- Android port stays GPLv3 (required by the upstream license).
- VSOP87 tables will be generated into Kotlin (or loaded from an asset), not hand-translated.
- Golden-value tests from the existing JS are the merge gate for the astronomy port.
- AstroGuide proposal: LLM with tool calling behind a small backend, instead of Rasa. This still needs the team's agreement.

**Open questions for the team**
1. Do you want the web app fixes (Phase 1) sent as a PR to `AadidevRaizada/AstroFixxer`, or only applied in the new repo?
2. Where should the Android project live: in this repo under `android/`, or a new repo?
3. AstroGuide: LLM API or Rasa as originally planned?
4. Permission to reuse `images/qs_*.png` (© Maxim Tonkikh), or should onboarding be redrawn?

**Next step**
- Run the Stitch prompt and save the exported designs under `docs/design/`.
- Start Phase 1 (bug B1, service worker caching, has the most user impact) and Phase 2 in parallel.

---

## Entry template

```markdown
### YYYY-MM-DD: <short title>

**What was done**
- ...

**Files changed**
- `path/to/file`: what changed and why

**How to verify**
- Commands run and their results (tests, build, manual checks on a device)

**Decisions**
- ...

**Known issues / not done**
- ...

**Next step**
- ...
```
