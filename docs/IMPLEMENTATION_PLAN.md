# AstroFixxer Implementation Plan

Goal: fix the known bugs in the web app, then rebuild AstroFixxer as a native Android app in Kotlin + Jetpack Compose using the Stitch UI designs (see `STITCH_UI_PROMPT.md`).

Source analysed: `AadidevRaizada/AstroFixxer` at commit `6af76ce` (19 commits). All line numbers refer to `astrofixxer.html` unless stated otherwise.

**Rule for every phase:** each piece of work that changes code ends with a new entry in `docs/HANDOFF.md` (template at the bottom of that file).

**Offline-first rule:** the app must work in the field with no internet. All sky data (stars, ~94k deep-sky objects, constellations, sky cultures, meteor showers) ships inside the app, and planet positions and planet events are computed on the phone. Only two features may use the network, and both must degrade gracefully:
- Wikipedia summaries: cached after the first view.
- Refreshing satellite orbit data: the bundled snapshot is used when offline, with its age shown.

**Data source:** Stellarium's open data files (catalogue, names, sky cultures, meteor showers), converted at build time by `tools/stellarium_import/`. We do **not** call Stellarium's Remote Control API. That needs a computer running Stellarium on the same network, which defeats offline use.

---

## 0. Licensing (read before porting)

The app is a fork of **AstroHopper by Artyom Beilis, GPLv3** (`COPYING.md`, `LICENSE`). A Kotlin port that translates this code is a derivative work, so the Android app **must also be GPLv3**, ship its source, and keep the credits below. Planning to put it on the Play Store is fine; making it closed source is not.

| Component | License | What it means for the port |
|---|---|---|
| App code (AstroHopper) | GPLv3 | Port stays GPLv3; keep copyright notice |
| Constellation data (Atlas of Space) | GPL | Ship as an asset with attribution |
| OpenNGC catalogue | CC-BY-SA-4.0 | Attribute in About screen; share-alike on the data (replaced by Stellarium data, see Phase 4) |
| Stellarium DSO catalogue, DSO names, meteor showers | GPL-2.0-or-later | Compatible with GPLv3; credit Stellarium in About |
| Stellarium sky cultures (modern, indian) text and data | CC BY-SA 4.0 | Attribute; share-alike on the data |
| Stellarium modern constellation illustrations | Free Art License | Attribute if we ship the artwork |
| HYG v3 star database (star positions) | CC BY-SA | Attribute |
| VSOP87 / CPReduce code (Greg Miller) | Public domain | No restriction |
| `images/qs_*.png` | © Maxim Tonkikh | Ask permission or redraw for the Android onboarding |

---

## 1. Phase 1: Fix the web app bugs (1–2 days)

The web app stays the reference implementation while the Android app is built, so fix it first. Its outputs are also used as the golden test values for the Kotlin port (Phase 3).

### 1.1 Verified bugs

| # | Severity | Where | Problem | Fix |
|---|---|---|---|---|
| B1 | High | `sw.js:23`, `astrofixxer.html:10549` | Service worker cache name is the literal `"astrofixxer-VERSION"` unless `deploy.py` rewrites it, and the fetch handler is cache-first with `ignoreSearch`. Users stay on the first cached version forever. The worker also caches only `astrofixxer.html`, so the manifest, icons and other pages don't work offline. Registering `sw.js?` + `Date.now()` creates a new worker URL on every page load. | Inject a real build hash into `version` during the Vercel build. Precache the manifest, icons and `index.html`. Use stale-while-revalidate for the HTML. Register plain `sw.js`. Merge the two `activate` listeners (`sw.js:36` and `sw.js:40`) into one `waitUntil`. |
| B2 | High | `pyserver.py:9-10`, `cert.pem` | `cert.pem` is an empty 2-byte file and `astrofixxer_deploy.html` doesn't exist in the repo, so the local HTTPS dev server crashes on start. (HTTPS is needed on a phone to get sensor access.) | Generate a self-signed cert on first run (or document the `openssl req -x509 ...` command), add `*.pem` to `.gitignore`, and serve `astrofixxer.html` when the deploy file is missing. |
| B3 | Medium | line 1 | `<!DOCTYPE>` is invalid, which puts the page in quirks mode (different box-model and layout rules on every browser). | `<!DOCTYPE html>` and `<html lang="en">`. Re-check layout afterwards. |
| B4 | Medium | lines 675–676 | `max=90"` and `max=180"` are malformed attributes, so the GPS-override lat/lon limits aren't enforced. | `max="90"`, `max="180"`, and validate the range in `updateManualLatLon()`. |
| B5 | Medium | line 9972 | `htmlEscape` replaces `&` **after** `<` and `>`, so `<` becomes `&amp;lt;`. User-object error messages containing `<`, `>` or quotes display garbled. | Replace `&` first. |
| B6 | Low | lines 9190, 9317 | `result` (in `plotStar`) and `line` (in `plotLines`) are implicit globals, assigned on every frame for every object. | Declare them with `const`/`let`. Add `"use strict"` to the app script to catch others. |
| B7 | Low | lines 9209, 9220 | `context.arc(x, y, r, 2*Math.PI, false)` is missing an argument. It only draws a full circle by coincidence (start=2π, end=0). | `context.arc(x, y, r, 0, 2*Math.PI)`. |
| B8 | Low | line 10543 | AstroGuide button calls `alert()` with a "Coming Soon" message. | Hide the button behind a feature flag until Phase 5, or link to `astroguide.html`. |
| B9 | Low | `celestial-3d.html:70-86` | Planet positions are placed on a fixed circle by array index, not calculated. Five stars only. | Either compute positions with `CPReduce.reduce()` or remove the page from navigation until it's real. |
| B10 | Low | repo | `__pycache__/*.pyc` (3 files) is committed. | `git rm -r --cached`, add to `.gitignore`. |
| B11 | Info | line 7902 | `UT1-UTC` is hard-coded to 0 (`TODO`). The error is under 1 s of time, well below what visual star-hopping can notice. | Leave as is; note it in the code. |
| B12 | Info | lines 14–21 | Google Analytics loads in an app meant to run offline in the field. | Make it opt-in or remove. It must not be in the Android app. |
| B13 | Info | i18n dicts (lines ~7625–7862) | Unused leftover strings inherited from upstream AstroHopper. | Remove dead keys when migrating strings to Android resources. |

### 1.2 Acceptance

- Install the PWA, go offline, reload: the app, manifest and icons all load.
- Deploy a new version: users get it on the next launch.
- `python3 pyserver.py` starts on a fresh clone.
- A user-object line containing `<` shows the error text correctly.

---

## 2. Phase 2: Android project setup (2 days)

### 2.1 Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose, Material 3 |
| Min / target SDK | minSdk 26 (Android 8.0), targetSdk latest |
| Architecture | Single activity, MVVM + unidirectional data flow (`StateFlow<UiState>`) |
| DI | Hilt |
| Navigation | Navigation Compose |
| Settings | DataStore (Preferences) |
| User objects & watch lists | Room |
| Sky rendering | Compose `Canvas` (`DrawScope`) |
| Location | Fused Location Provider, plus manual override |
| Sensors | `SensorManager`: `TYPE_ROTATION_VECTOR` (absolute, uses compass), `TYPE_GAME_ROTATION_VECTOR` (gyro + gravity, no compass) |
| Networking (Wikipedia, AstroGuide) | Ktor client or Retrofit + kotlinx.serialization |
| Tests | JUnit5 + Truth for the astronomy core, Compose UI tests, Paparazzi screenshot tests |
| CI | GitHub Actions: `./gradlew lint test assembleDebug` |

### 2.2 Module layout

```
android/
├── app/                      Activity, navigation, DI wiring
├── core/astro/               Pure Kotlin (no Android deps): VSOP87, CPReduce, JulianDate,
│                             coordinate transforms, RA/Dec parsers, alignment math
├── core/catalog/             Loads the star/DSO/constellation assets, search index
├── core/sensors/             Orientation provider (rotation-vector → quaternion/matrix), drift handling
├── core/data/                DataStore settings, Room (user objects, watch lists)
├── core/designsystem/        Theme tokens from Stitch (Normal + Night red), typography, components
├── feature/skymap/           Sky map canvas, top bar, bottom controls, guidance panel
├── feature/settings/
├── feature/onboarding/       Permissions + 4-step quick start
├── feature/objectinfo/
├── feature/help/
└── feature/astroguide/       Phase 5
```

`core/astro` is pure Kotlin so it can be unit-tested on the JVM and reused later (Kotlin Multiplatform for iOS is an option but not in scope).

---

## 3. Phase 3: Port the astronomy core (4–5 days)

This is the riskiest part: a wrong sign or unit here sends the telescope to the wrong place. Port it first and test it hard.

### 3.1 What to port

| Web source | Kotlin target | Notes |
|---|---|---|
| `class vsop87a_xsmall` (lines 1218–7383) | `Vsop87` | ~6,000 lines of generated cosine terms. **Don't hand-translate.** Write a small generator (Python or Kotlin script) that reads `vsop87-multilang/Languages/JSON` (or the JS source) and emits Kotlin, or load the coefficients from a binary asset at startup. Generated Kotlin methods may hit the 64 KB JVM method size limit, so split per planet/axis/power. |
| `class vsop87a_milli_velocities` (7384–7622) | `Vsop87Velocities` | Same generator. |
| `CPReduce` (7876–8329) | `ApparentPosition.reduce(body, jdUtc, observer)` | Light-time, precession, nutation, topocentric. Returns RA/Dec J2000, RA/Dec of date, Alt, Az. |
| `Vec` (8330–8462) | `Vec3`, `Mat3` value classes | |
| `JulianDate` (8463–8506) | `JulianDate` | Use `java.time.Instant` as input. |
| Projection: `cameraBearing`, `xyzTo2d`, `projectToCamera` (~9106–9139), `getFOV` | `SkyProjection` | Keep both projection modes (camera and flat). |
| `align()` and alignment matrices (~8861–8960) | `Alignment` | Records the device rotation at the alignment star; outputs the correction applied to later readings. |
| `parseRA` / `parseDEC` (9898–9954) | `CoordinateParser` | Keep every accepted format (decimal, `h:m:s`, `5h35m17s`, space-separated, Unicode minus and primes). |
| `parseUserDSO` | `UserObjectParser` | Returns per-line errors. |

### 3.2 Test strategy (the gate for this phase)

1. **Golden values from the web app.** Run the existing JS in Node (headless) for a fixed set of inputs: 8 planets × 5 dates × 3 observer locations (for example Delhi, Bengaluru, Leh). Save the RA/Dec/Alt/Az outputs to `core/astro/src/test/resources/golden.json`. The Kotlin port must match to within 1e-9 rad.
2. **External check.** Spot-check a handful of the same cases against JPL Horizons. Expect agreement within about 1 arcminute (the "xsmall" VSOP87 series is truncated, so it won't be exact).
3. **Parser tests.** Table-driven tests for every RA/Dec format, including invalid input (h ≥ 24, m ≥ 60, |dec| > 90).
4. **Alignment round-trip.** Synthetic test: pick an orientation, align on star A, rotate by a known amount, and check that star B's predicted screen position and ΔAlt/ΔAz are correct.

---

## 4. Phase 4: Offline sky data from Stellarium (2–3 days; importer done)

Today `create_data.py` builds the data from OpenNGC (about 4,100 deep-sky objects) and HYG, and writes it as a JS literal into the HTML. We replace the deep-sky source, names and constellations with Stellarium's data, which is larger, better cross-referenced and maintained.

### 4.1 Done: `tools/stellarium_import/`

- `fetch_stellarium.sh` sparse-checks-out only the needed Stellarium data at a **pinned commit** (`9910a2f`), so builds are reproducible.
- `build_sky_data.py` reads:
  - `nebulae/default/catalog.txt`: 94,899 deep-sky objects with 29 cross-index catalogues
  - `nebulae/default/names.dat`: common names
  - `skycultures/modern` and `skycultures/indian`: constellation lines, star names, Indian names in Devanagari
  - `plugins/MeteorShowers/resources/MeteorShowers.json`
  - HYG v3 for star positions (Stellarium's own star catalogues are separate binary downloads)
- It writes:

| Output | Size | Contents |
|---|---|---|
| `data/web/jsdb_stellarium.js` | 1.6 MB | Drop-in replacement for the web app's data block. 18,842 objects: 807 open clusters, 9,999 galaxies, 2,731 nebulae, 141 globulars (mag ≤ 14), 8 planets, 88 constellations, 5,068 stars (mag ≤ 6). 16,846 search keys. `--apply-to astrofixxer.html` patches a copy of the app. |
| `data/android/sky_catalog.json.gz` | 2.5 MB | Full catalogue for Android: 93,997 deep-sky objects (all IDs and names), 8,912 stars with Western and Indian names, modern + Indian constellations. |
| `data/events/meteor_showers.json` | 62 KB | 43 showers × 2026–2028: peak / start / end in UTC, ZHR, radiant and drift, speed, parent body. |

- Checked:
  - The patched web app loads in headless Chromium with no new errors.
  - Search finds M31, "Andromeda Galaxy", "Orion Nebula", NGC7000, "Pleiades", "Trifid", Cr399, Jupiter, and the Indian star name "Lubdhaka" → Sirius.
  - 7 unit tests pass: Messier types/names, all Messier present, M42 position, meteor peak dates (Perseids 13 Aug, Geminids 14 Dec 2026, Quadrantids activity wraps the new year), and name normalisation identical to the web app.

### 4.2 Remaining

- **Web**: decide whether to replace the embedded data in `astrofixxer.html` (see HANDOFF open question 1). The page grows by about 0.8 MB. The render loop is safe because each type block is sorted by magnitude and skipped at the limit.
- **Android `core/catalog`**:
  - Load `sky_catalog.json.gz` once in the background; convert to a compact binary format if parsing is slow on low-end phones.
  - Build a **spatial index** (RA/Dec grid or HEALPix, level ~4), so only objects in the current field of view and above the magnitude limit are drawn. 94k objects is too many to scan every frame.
  - Search index with the same `normalize_name` rule. Prefix search over names and all catalogue IDs.
- **Stars fainter than mag 6**: optional later step. Parse Stellarium's `stars_0`/`stars_1` binary catalogues (mag ≤ 7.5) for a deeper star field when zoomed in.
- **Constellation artwork**: the modern illustrations (Free Art License) are in `skycultures/modern/illustrations`, with anchor stars in `index.json`. Import them if Stitch keeps the "constellation art" toggle.
- Run the importer in Gradle (or CI) and fail the build if the committed outputs are out of date.
- To update the data later: bump `STELLARIUM_COMMIT`, re-run, review the diff, commit. Users get new data with the next app update, and the app never needs the internet for it.

---

## 5. Phase 5: Build the Compose UI from the Stitch designs (6–8 days)

Feed `STITCH_UI_PROMPT.md` to Stitch, export the designs, and convert the token sheet into `core/designsystem`.

### 5.1 Theme

- `AstroTheme(mode = Normal | Night)`. Night mode is a full color scheme where every role maps to shades of red on black; it isn't an overlay.
- Also dim the window: `WindowManager.LayoutParams.screenBrightness` gets a user-set cap in Night mode.
- Typography: condensed face for readouts with `fontFeatureSettings = "tnum"`.

### 5.2 Screens (mapping from the web app)

| Screen | Replaces (web) | Key work |
|---|---|---|
| Sky View (Stellarium-style) | `#myCanvas` + top buttons + `#status` + `find_status` | Full-screen planetarium: star glow sized by magnitude and coloured by B−V, Milky Way texture, atmosphere colour from the sun's altitude, horizon silhouette + cardinal points, grids, ecliptic/meridian lines, constellation lines/names/boundaries/art. Start with Compose `Canvas` + the spatial index. If it can't hold 60 fps on a mid-range phone, move the sky layer to OpenGL ES (`GLSurfaceView` in `AndroidView`) and keep overlays in Compose. Hit-testing for taps. Pinch zoom → FOV with zoom-dependent magnitude limit. Drag → pan (Free look / Manual). Time travel (display time ≠ now). |
| Slide-out toolbars | settings buttons | Left bar (Location, Date & Time, Sky options, Search, Settings, Help, Events) and bottom toggle bar with auto-hide. |
| Info overlay | target label on canvas | Top-left text stack: IDs, type, mag, size, RA/Dec, Alt/Az, rise/transit/set. |
| Search window | `search_field_main` | Tabs: Object / Position / Lists. Results show "Up now" or rise time. |
| Events | none (new) | See Phase 5b. |
| Guidance panel | the `find_status` text line | New: ΔAlt/ΔAz with arrows, progress ring, on-target haptics. |
| Onboarding & permissions | `qs_1`–`qs_4`, "Enable Device Orientation" | Runtime permissions for location; sensor availability check (rotation-vector present? compass present?). |
| Settings | config panel | Grouped sections backed by DataStore. "Reset all" with an in-app confirmation. |
| Object info | wiki iframe (`showWiki`) | Offline catalogue data + Wikipedia REST summary API (`/api/rest_v1/page/summary/{title}`) with caching; honour Always / Day only / Never. |
| Watch lists & user objects | text areas | Room-backed lists. Keep the paste-CSV editor for power users and add a form. |
| Help | embedded manual HTML | Convert the manual to Markdown resources per language. |

### 5.3 Sensors

- Prefer `TYPE_ROTATION_VECTOR` when a magnetometer exists ("Compass" mode). Fall back to `TYPE_GAME_ROTATION_VECTOR` ("Manual" mode, where the user drags to set azimuth), which is what the web app does with `deviceorientation` without `absolute`.
- Map the phone axes to the telescope axis: the phone lies flat on the tube with its top edge pointing at the sky, so the pointing direction is the device +Y axis. Handle display rotation with `SensorManager.remapCoordinateSystem`.
- Low-pass filter the output (the web app does little smoothing). Sample at `SENSOR_DELAY_GAME` and collect only while the Sky Map is on screen (`repeatOnLifecycle(STARTED)`).
- Show a drift hint when time since alignment exceeds N minutes or the accumulated gyro rotation exceeds X degrees.
- Keep-screen-on via `FLAG_KEEP_SCREEN_ON` (replaces the web Wake Lock).

### 5.4 Localisation

- Move the `i18n_dicts` entries and `po/` files into `values-*/strings.xml` (`uk`, `hu`, `ru`, `iw`/`he`, plus new `hi`).
- Test RTL with Hebrew.

---

## 5b. Phase 5b: Offline events (4–5 days)

Every event source works without internet:

| Event | Source | How |
|---|---|---|
| Meteor showers | `data/events/meteor_showers.json` (from Stellarium) | Bundled. For years beyond the file, the app converts the solar longitudes itself; the algorithm is in `build_sky_data.py` (`jd_for_solar_longitude`). Show the radiant on the sky with drift applied (drift is in degrees per degree of solar longitude). |
| Sunrise/sunset, twilights, moonrise/set, moon phase | `core/astro` | Computed on device from the VSOP87 Sun and the Moon theory already in the web app (`getSolarSystemObject('Moon')`). |
| Conjunctions, Moon–planet approaches | `core/astro` | Sample planet positions every 6 h over the next 60 days, find minima of angular separation < 5°, refine by bisection. |
| Oppositions, greatest elongations | `core/astro` | Find where elongation from the Sun is 180° (outer planets) or at a maximum (Mercury, Venus). |
| Rise/transit/set for any object | `core/astro` | Standard hour-angle calculation. |
| ISS and bright satellite passes | Bundled TLE snapshot + SGP4 on device | The build step downloads current TLEs from CelesTrak (ISS, Hubble, Tiangong, the brightest satellites) into `data/events/tle.txt`. The app runs SGP4 (port or small library) to predict visible passes: satellite sunlit, observer in darkness, altitude > 10°. **TLEs age**: accuracy is good for about 1–2 weeks. The app shows "Orbit data N days old", greys predictions out after 30 days, and refreshes from CelesTrak only when online (WorkManager, unmetered network). CelesTrak is blocked in this build sandbox, so this download must be tested locally or in CI. |
| "Tonight" summary | all of the above | Darkest window, planets up, moon, next meteor peak, next ISS pass. |

Tests: compare 10 known 2026 events (for example the Perseid and Geminid peaks, and a known Venus–Jupiter conjunction) against published almanac values: within ±1 day for meteor peaks and ±1 hour for conjunctions.

---

## 6. Phase 6: AstroGuide voice assistant (4–6 days, after the core app ships)

The README plans Rasa. A simpler route with better multilingual support:

- Speech in: Android `SpeechRecognizer` (works offline for downloaded languages, including Hindi and English).
- Understanding: send the transcript plus current sky context (time, location, alignment state, visible bright objects, current target) to an LLM API with **tool calling**. Tools map to app actions: `set_target(name)`, `list_visible(type, min_alt)`, `object_info(name)`, `next_events()`.
- Speech out: Android `TextToSpeech`.
- **Offline first**: an on-device intent matcher handles "find X", "what is X", "align" and "what's up tonight", answered from the bundled catalogue and the Phase 5b events. The LLM is used only for free-form questions when online.
- Keep API keys off the device: route through a tiny backend (for example a Vercel function).

---

## 7. Phase 7: Release (2 days)

- Signing config, R8/ProGuard rules (keep kotlinx.serialization models).
- In-app "Licenses & source" screen with a link to the GPL source repo.
- Play Store listing screenshots from Paparazzi / the Stitch designs.
- Manual field test checklist: 3 phones (with/without magnetometer), 2 telescopes (Dobsonian, small refractor), find 10 Messier objects from alignment stars 5–20° away, and record time-to-target and misses.

---

## 8. Timeline summary

| Phase | Effort | Depends on |
|---|---|---|
| 1. Web bug fixes | 1–2 d | none |
| 2. Android setup | 2 d | none |
| 3. Astronomy core port + tests | 4–5 d | 2 |
| 4. Stellarium offline data (importer done) | 2–3 d remaining | 2 |
| 5. Compose UI, Stellarium-style | 10–12 d | 3, 4, Stitch designs |
| 5b. Offline events | 4–5 d | 3, 4 |
| 6. AstroGuide | 4–6 d | 5, 5b |
| 7. Release | 2 d | 5 |

Roughly 6–7 weeks for one developer to reach a releasable app without AstroGuide, and 7–8 weeks with it. The Stellarium-style rendering (atmosphere, Milky Way, landscape, art) adds about 4 days over the plain sky map.

## 9. Risks

| Risk | Mitigation |
|---|---|
| Compass error near a metal telescope tube | Manual mode is the default when the compass reports low accuracy; show `SENSOR_STATUS_ACCURACY_*`. |
| Gyro drift | Prompt to re-align per target, as the web app already advises. Show time since alignment. |
| VSOP87 port mistakes | Golden tests against the JS app (Phase 3.2) are a merge gate. |
| Generated Kotlin exceeds JVM method size | Split generated code or load coefficients from an asset. |
| GPL obligations missed | License screen + public source repo before first release. |
| 94k objects make the sky view slow | Spatial index + magnitude culling; fall back to OpenGL ES for the sky layer. |
| Stale satellite predictions offline | Show data age; hide passes when TLEs are older than 30 days. |
| Stellarium data format changes | Pinned commit; importer tests fail loudly on a bump. |
