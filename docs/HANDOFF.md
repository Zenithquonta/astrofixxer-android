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
| Fixed web app (deploy root) | `web/` |
| Stellarium importer | `tools/stellarium_import/` |
| Android app | `Zenithquonta/astrofixxer-android` (not created yet); until then the repo is saved as `handoff/astrofixxer-android.bundle` |
| Codebase analysis (web page) | https://claude.ai/artifact/SMf5adtidjB5CsJt5ZrxNW |

## Current status

| Phase (see plan) | Status |
|---|---|
| 0. Analysis, UI prompt, plan | Done |
| 1. Web app fixes + Stellarium data | Done (`web/`); needs a Vercel project with root `web` |
| 2. Android project setup | Done locally; waiting for the GitHub repo to push and run CI |
| 3. Astronomy core | Done: reduction, pointing, alignment, parser; golden tests pass on the JVM |
| 4. Stellarium offline data | Done except constellation artwork: importer, comets/asteroids, Android catalogue loader with grid index and search |
| 5. Compose UI (Stellarium-style) | v1 done: sky view, alignment, guidance, search, events, night mode. Missing: atmosphere, Milky Way, landscapes, light-pollution slider, constellation art, settings, onboarding, help, localisation |
| 5b. Offline events | Moon phases, eclipses, equinoxes/solstices, rise/set, conjunctions, meteor peaks, bright comets done. Missing: occultations, transits, ISS passes, supermoons, planet gatherings |
| 6. AstroGuide v1 (offline voice) | Not started |
| 7. Release | Not started |

---

## Entries

### 2026-09-28: Phases 4-5 v1 (catalogue, comets, events, sky view)

**What was done** (all in the Android repo, saved as `handoff/astrofixxer-android.bundle`, `main` at `98906c5`)
- **Comets and asteroids.**
  - Importer: `minor_bodies.json` has 461 bodies (115 comets) from Stellarium `data/ssystem_minor.ini`.
  - Kotlin: `MinorBody` solves elliptic, parabolic and hyperbolic two-body orbits, with light time. Magnitudes use Stellarium's comet formula and the asteroid H–G system.
- **Offline events** (`Events.kt`): moon phases, lunar/solar eclipses, equinoxes/solstices (of date), J2000 solar longitude (meteors), rise/set, conjunctions.
- **Catalogue** (`Catalog.kt`): loads `sky_catalog.json.gz` (102,907 objects) with a 10°×10° grid for field-of-view queries and a name index. Uses the same normalisation as the web app, plus Indian names and HIP numbers.
- **Sky view** (`ui/`, no Android imports):
  - Stellarium-style canvas: zoom-dependent magnitude limits, deep-sky symbols, constellation lines and names (modern or Indian), Sun/Moon/planets, comets brighter than mag 11, horizon, cardinal points, optional alt/az grid.
  - Pinch zoom, tap to target, Manual-mode drag.
  - One-star alignment and the ΔAlt/ΔAz guidance panel.
  - Info overlay, Find, Events list (tap an event to jump the sky to that time; "Now" returns), red-only Night mode.
- **`MainActivity`**: rotation-vector sensor (game rotation vector plus Manual mode when there's no compass), smoothed with an adjustable factor. Also last known location with a permission request, background asset loading, a 1 s clock, portrait only.
- **Importer**: the gzip output is now reproducible (mtime 0).

**How to verify**
- JVM harness (astro package + tests), **24 tests pass**:
  - golden values vs the web app
  - parser
  - orbits vs VSOP87 using JPL J2000 elements
  - events vs published 2026 dates
  - catalogue search and `near()`
- Measured errors against published 2026 values:

  | Event | Error |
  |---|---|
  | Full moon | +0.2 min |
  | Lunar eclipse maximum | +0.9 min |
  | New moon | +5.6 min |
  | Equinox | −5.8 min |
  | Delhi sunrise / sunset | −0.5 / +2.9 min |
  | Lunar eclipse umbral magnitude | 1.136 (published 1.151) |
- UI: `ui/` + `astro/` were compiled against **Compose Multiplatform 1.5.12 (desktop)** and rendered headlessly with `ImageComposeScene`. Screens are in `docs/screens/`.
  - The first render caught a real crash: `drawText` throws for labels past the right edge. Fixed with `safeText`.
  - Renders also showed wrapped toolbar labels, clutter from obscure IDs, and grey outlines in Night mode. All fixed.
- Catalogue load time: 2.6 s on this machine's JVM (cold). It loads in the background on the phone; measure on a device.
- **Not compiled here:** `MainActivity` and the Gradle Android build (Google Maven blocked). They were reviewed by hand. First real build: GitHub Actions after the repo is pushed.

**Decisions**
- Kept (ponytail):
  - `LocationManager` last-known fix instead of Play Services.
  - Magnetic declination not applied; alignment absorbs it.
  - Element-wise sensor smoothing.
  - Events computed once after loading.
- Deep-sky labels only for M/NGC/IC/Caldwell objects. All objects are still drawn and searchable.

**Known issues / not done**
- Rendering: atmosphere, Milky Way, landscapes, light-pollution slider, constellation artwork.
- Screens: settings (user objects, watch lists, manual location), onboarding, help, Hindi and other translations.
- Events: occultations, transits (Mercury/Venus, ISS), ISS passes (needs TLE download; CelesTrak blocked here), supermoons, planet gatherings.
- `String.format` uses the default locale; check digits for Hindi.
- The repo `Zenithquonta/astrofixxer-android` still doesn't exist, so CI hasn't run.

**Next step**
- Owner: create the repo (see the previous entry for the push commands).
- Me: occultations/transits/rare events (testable on the JVM), then the remaining Stellarium rendering features, checked with desktop renders.

### 2026-09-28: Phase 2 (Android skeleton) and Phase 3 (astronomy core)

**What was done**
- Tried to create `Zenithquonta/astrofixxer-android` (private) as approved. The session's GitHub integration can't create repositories (403 "Resource not accessible by integration"), so the owner must create it. Until then, the repo is saved as a git bundle in this branch: `handoff/astrofixxer-android.bundle` (branch `main`, commit `758dd8d`).
- Android project (ponytail stack):
  - One `app` module, Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01, minSdk 26 / target 35, JUnit4.
  - No Hilt, Room, Navigation, Ktor or kotlinx.serialization.
  - Gradle wrapper 8.11.1, GPLv3 `LICENSE`, README, CI workflow `.github/workflows/android.yml` (`./gradlew test assembleDebug`, uploads the debug APK).
  - `MainActivity` is a placeholder screen with live Alt/Az for the Sun, Moon and planets over New Delhi, proving the core runs on device. It gets replaced by the sky view in Phase 5.
- Astronomy core in `app/src/main/java/org/astrofixxer/astro/`:
  - `ApparentPosition` (`CPReduce` port) and `JulianDate`.
  - `Pointing`: `rayFromPos`, W3C rotation matrix, camera rays, one-star `alignMatrix`, `bearing`, plus new `deltaAltAz`.
  - `CoordinateParser`.
- VSOP87: copied the public-domain Java series from `vsop87-multilang` (`package` line added).
  - Planets use `xsmall`, as in the web app.
  - **Earth and the Earth–Moon barycentre use `large`**. Checked by term count (earth_x 417, emb_x 398, earth_z 85), so the embedded "xsmall" class in the web app is actually a mix. A small public adapter `Vsop87LargeEarth.java` exposes the package-private `large` methods.
  - Earth velocity uses `milli_velocities`.
- Golden values: `tools/golden/golden_from_web.js` runs the web app's own JS in Node and writes `app/src/test/resources/golden.json`:
  - 135 reductions: 9 bodies × 5 dates × Delhi/Bengaluru/Leh
  - 15 star rays
  - 4 rotation matrices
  - 3 full alignment flows: Vega→M57, Altair→M11, Deneb→NGC 7000, with the phone 5–12° off the star

**Files changed**
- New repo (bundle): `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew*`, `gradle/wrapper/*`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `MainActivity.kt`, `astro/*.kt`, `astro/vsop87/*.java` (22 files), `app/src/test/...`, `.github/workflows/android.yml`, `README.md`, `LICENSE`, `.gitignore`
- This repo: `tools/golden/golden_from_web.js`, `handoff/astrofixxer-android.bundle`, `.gitignore`, `docs/HANDOFF.md`

**How to verify**
- This sandbox can't build Android: Google's Maven repository (`dl.google.com`) is blocked and there's no Android SDK. The astro package and its tests were compiled and run on the JVM with a throwaway Kotlin/JVM Gradle project over the same source files: **8/8 tests pass**.
  - All 135 reductions match the web app to 1e-9 rad.
  - Rays, rotations and alignment match to 1e-12.
  - After alignment the star bearing is exactly [0, 0, 1].
  - Parser table tests pass.
- The first full Android build (`./gradlew test assembleDebug`) will run in GitHub Actions after the push.

**Decisions**
- Default location before GPS is New Delhi (the web app defaults to 31.9°N 34.8°E, inherited from upstream).
- Pre-1972 fractional leap-second formulas were dropped from `leapSeconds` (`ponytail:` comment); the golden dates are 2024–2030.

**Known issues / not done**
- Found, not fixed, in the web app:
  - Decimal Dec with a Unicode minus ("−5.3") parses to NaN, because `[+-−]` is a character range. Fixed in the Kotlin parser.
  - `CPReduce`'s doc comment lists outputs as Dec, RA, …, Alt, Az; the code actually returns RA, Dec, …, Az, Alt. The web app uses them correctly; the Kotlin `Result` names the fields.
- The web app uses J2000 star positions with a plain sidereal time (no precession), about 0.36° off in 2026. The effect largely cancels with one-star alignment near the target, and the Kotlin port keeps the same behaviour for now. Revisit in Phase 5.
- JPL Horizons spot-check not done (network).

**Next step**
- Owner: create the empty private repo `Zenithquonta/astrofixxer-android`. Then:
  ```bash
  git clone handoff/astrofixxer-android.bundle astrofixxer-android
  cd astrofixxer-android
  git remote set-url origin git@github.com:Zenithquonta/astrofixxer-android.git
  git push -u origin main
  ```
  Or add the repo to this session and I'll push it.
- Then Phase 4 remaining (comets/minor bodies import, constellation art, Android catalogue loader) and the Phase 5b event calculations in `core/astro`.

### 2026-09-28: Ponytail review approved; Phase 1 done (web fixes + Stellarium data)

**What was done**
- Reviewed the plan with the ponytail method ([DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)). The owner approved all cuts:
  - 1 Android module instead of 12.
  - No Hilt, Navigation Compose, Room, Ktor/Retrofit, kotlinx.serialization or Paparazzi.
  - Copy the upstream VSOP87 Java files instead of porting ~6,000 lines.
  - Grid index instead of HEALPix.
  - No OpenGL fallback unless measured.
  - Offline-only AstroGuide v1.
- The owner kept four UI features: constellation art, several landscapes, the light-pollution slider and time travel.
- The owner added four features: transit tracker, occultations, comets and rare celestial events. All are in plan Phase 5b.
- Copied the web app into `web/` (commit `89d082d`, verbatim), then fixed it:
  - Commit `4599a30`: B1 service worker versioning/precache, B3 doctype + viewport-sized canvas, B4, B5, B6, B7, B8 (AstroGuide alert removed), B12 (Analytics removed).
  - Not copied: B2 `pyserver.py`/`cert.pem`, B9 `celestial-3d.html`, B10 `__pycache__`.
- Embedded Stellarium data in `web/astrofixxer.html` (commit `93159d4`).
- Importer fixes:
  - Dark nebulae store an opacity class in the magnitude column, which put ~1,900 dark clouds under the default DSO limit and cluttered the map. They now have no magnitude; opacity is kept as its own field.
  - Web build keeps only objects named in commonly searched catalogues.
  - The swap preserves the file's CRLF line endings.
- Ponytail cuts in the importer: removed the `--no-indian-star-names` flag and the duplicate `mag` field in the Android output.
- Rewrote `docs/IMPLEMENTATION_PLAN.md` with the approved cuts, the new features and the new-repo decision.

**Files changed**
- `web/` (new): `astrofixxer.html`, `index.html`, `astroguide.html`, `sw.js`, `manifest.json`, `vercel.json`, `sitemap.xml`, `images/`, `LICENSE`, `COPYING.md`
- `tools/stellarium_import/build_sky_data.py`, `test_build_sky_data.py`
- `data/android/sky_catalog.json.gz` (regenerated); `data/web/` is now untracked (its content is embedded in `web/astrofixxer.html`)
- `docs/IMPLEMENTATION_PLAN.md`, `docs/HANDOFF.md`, `.gitignore`

**How to verify**
- `STELLARIUM_DIR=.cache/stellarium python3 -m unittest tools/stellarium_import/test_build_sky_data.py`: 8 tests pass.
- Build-stamped copy served on `localhost` in headless Chromium:
  - Service worker cache `astrofixxer-<sha>` created, and the page is controlled.
  - Offline reload works, and the manifest loads offline.
  - Standards mode (`CSS1Compat`).
  - Search finds M31, Pleiades → M45, Lubdhaka → Sirius, Jupiter.
  - `htmlEscape('<b>&"\'')` gives the correct entities; no leaked globals.
  - User-object error shows "Invalid RA value 99:99:99".
  - No AstroGuide button; no console errors.
- Phone-size (412×860) screenshot matches the original layout, with Stellarium star names and no label clutter.

**Decisions**
- The web data only includes objects whose main ID is from M, NGC, IC, Caldwell, Barnard, Sharpless, Collinder, Melotte, Trumpler, Stock, Ruprecht, Arp, Abell or HCG. The full set stays in the Android catalogue.
- Service worker versioning uses a one-line `sed` of `VERCEL_GIT_COMMIT_SHA` instead of upstream's `deploy.py`, which needs the OpenNGC submodule and `markdown`.

**Known issues / not done**
- Globular clusters in the web build: 130 (was 191 from OpenNGC). Some Stellarium globulars have no magnitude or have a main ID outside the searched catalogues.
- `web/` isn't deployed. Someone with Vercel access must create a project with Root Directory `web`.
- `astroguide.html` (the "Coming Soon" page) is still linked from `index.html`; only the in-app alert button was approved for removal.
- Stellarium has comet orbits in `data/ssystem_minor.ini` (115 comets + asteroids); not imported yet.

**Next step**
- Owner: create the Android repo (or approve me creating `Zenithquonta/astrofixxer-android`, private) and add it to this session.
- Then Phase 2 (project skeleton) and Phase 3 (astronomy core, starting with copying the VSOP87 Java files and building the golden-value tests).

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
