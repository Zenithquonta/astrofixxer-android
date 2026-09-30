# AstroFixxer Implementation Plan

Goal: fix the web app, then rebuild AstroFixxer as a native Android app in Kotlin + Jetpack Compose, with a Stellarium-style UI (see `STITCH_UI_PROMPT.md`) and offline data from Stellarium.

Source analysed: `AadidevRaizada/AstroFixxer` at commit `6af76ce`. Line numbers refer to the original `astrofixxer.html` unless stated otherwise.

**Rules for every phase**
- Each piece of work that changes code ends with a new entry in `docs/HANDOFF.md`.
- **Ponytail method** ([DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)): before writing code, climb the ladder and stop at the first rung that holds:
  1. Does it need to exist?
  2. Is it already in the codebase?
  3. Does the standard library do it?
  4. Does a native platform feature cover it?
  5. Does an already-installed dependency solve it?
  6. Can it be one line?
  7. Only then, write the minimum code that works.
  
  Plan changes that come out of a ponytail review are shown to the owner for approval first. The 2026-09-28 review was approved in full.
- **Offline first:** everything works in the field with no internet. Sky data, events data and comet orbits ship inside the app, and positions and events are computed on the phone. Only these may use the network, and each must degrade gracefully:
  - Wikipedia summaries: cached after first view.
  - Refreshing satellite orbits and comet elements: the bundled snapshot is used offline, and its age is shown.
- **Data source:** Stellarium's open data files, converted at build time by `tools/stellarium_import/`. We don't call Stellarium's Remote Control API: it needs Stellarium running on a computer on the same network.

---

## 0. Licensing

The app is a fork of **AstroHopper by Artyom Beilis, GPLv3**. The Android port is a derivative work, so it **must be GPLv3**, publish its source, and keep these credits.

| Component | License | What it means |
|---|---|---|
| App code (AstroHopper) | GPLv3 | Port stays GPLv3; keep copyright notice |
| Stellarium DSO catalogue, names, meteor showers, minor-body orbits | GPL-2.0-or-later | Compatible with GPLv3; credit Stellarium |
| Stellarium sky cultures (modern, indian) | CC BY-SA 4.0 | Attribute; share-alike on the data |
| Stellarium modern constellation illustrations | Free Art License | Attribute |
| HYG v3 star database | CC BY-SA | Attribute |
| VSOP87 Java series + CPReduce (Greg Miller) | Public domain | No restriction |
| NASA eclipse tables (Espenak) | Public domain | Credit NASA GSFC |
| `images/qs_*.png` | © Maxim Tonkikh | Ask permission or redraw for Android onboarding |

---

## 1. Phase 1: Web app fixes (done)

The web app now lives in `web/`, copied from upstream `6af76ce`. The original repo isn't touched.

| # | Status | What was done |
|---|---|---|
| B1 | Fixed | Service worker version stamped by the Vercel build (`sed` in `vercel.json`, commit SHA). Precaches page, landing page, manifest and icon. One `activate` handler. Registered as plain `sw.js`. |
| B2 | Deleted | `pyserver.py` and empty `cert.pem` not copied. Vercel serves HTTPS, and desktop browsers treat `localhost` as secure. |
| B3 | Fixed | `<!DOCTYPE html>`. The canvas is now sized from the viewport (`documentElement.client*`); quirks mode used to provide that implicitly, and without the change the map collapsed. |
| B4 | Fixed | `max="90"` / `max="180"`. The range was already validated in JS. |
| B5 | Fixed | `htmlEscape` escapes `&` first. |
| B6, B7 | Fixed | No implicit globals; correct `arc()` arguments. |
| B8 | Deleted | AstroGuide "Coming Soon" alert button. |
| B9 | Deleted | `celestial-3d.html` not copied. |
| B10 | Deleted | `__pycache__` not copied. |
| B11 | Kept | `UT1-UTC = 0` is negligible for visual use. |
| B12 | Deleted | Google Analytics. |
| B13 | Later | Dead i18n strings are dropped when strings move to Android resources. |
| Data | Done | Stellarium data embedded: 10,438 objects vs 9,759 before, with Indian star names searchable. |

**Owner action:** point a Vercel project at this repo with **Root Directory = `web`**.

---

## 2. Phase 2: Android project setup (0.5 day)

**The Android app lives in a new repository** (owner decision). Name and visibility are to be confirmed; see HANDOFF.

| Concern | Choice | Ponytail note |
|---|---|---|
| Language / UI | Kotlin 2.x, Jetpack Compose, Material 3 | |
| SDK | minSdk 26, targetSdk latest | |
| Structure | **One `app` module**. Astronomy code in `app/src/main/.../astro`, tested on the JVM from `src/test`. | 12 modules cut |
| State | One `ViewModel` per screen, `StateFlow<UiState>`, built directly | Hilt cut |
| Navigation | Screen state + `BackHandler`, bottom sheets | Navigation Compose cut |
| Storage | DataStore for settings, user objects and watch lists (small text lists, like the web app's localStorage) | Room cut |
| Network | `HttpURLConnection` (Wikipedia, orbit refresh) | Ktor/Retrofit cut |
| JSON | `android.util.JsonReader` + `org.json` | kotlinx.serialization cut |
| Sky rendering | Compose `Canvas` | OpenGL only if profiling demands it |
| Location | Fused Location Provider + manual override + offline city list | |
| Sensors | `TYPE_ROTATION_VECTOR` (compass), `TYPE_GAME_ROTATION_VECTOR` (no compass) | |
| Tests | JUnit4 | Truth, Paparazzi, Compose UI tests cut |
| CI | GitHub Actions: `./gradlew test assembleDebug` | |

---

## 3. Phase 3: Astronomy core (2–3 days)

| Web source | Kotlin target | Notes |
|---|---|---|
| `vsop87a_xsmall` + velocities (lines 1218–7622) | **Copy** `vsop87a_xsmall*.java` from `vsop87-multilang/Languages/Java` | Same series as the web app, public domain, called from Kotlin as-is. No port, no generator. |
| `CPReduce` (7876–8329) | `ApparentPosition.reduce(body, jdUtc, observer)` | Light-time, precession, nutation, topocentric. ~450 lines. |
| `Vec`, `JulianDate` (8330–8506) | `Vec3`/`Mat3`, `JulianDate` | ~170 lines. |
| Moon position (`getSolarSystemObject('Moon')`) | `Moon` | Port whatever theory the web app uses. Check its accuracy against Horizons first: occultations (Phase 5b) need ~1′. |
| Projection (`cameraBearing`, `xyzTo2d`, `getFOV`) | `SkyProjection` | Both projection modes. |
| `align()` (~8861–8960) | `Alignment` | Keep sensor smoothing and drift thresholds adjustable (real sensors drift). |
| `parseRA`/`parseDEC`, `parseUserDSO` | `CoordinateParser` | All accepted formats, per-line errors. |

**Gate:**
- Golden values from the JS (8 planets + Moon × 5 dates × 3 locations: Delhi, Bengaluru, Leh) match to 1e-9 rad.
- Spot-check against JPL Horizons.
- Parser table test.
- Alignment round-trip test.

---

## 4. Phase 4: Offline data from Stellarium (importer done; 1–2 days remaining)

**Done** (`tools/stellarium_import/`, pinned Stellarium commit `9910a2f`):

| Output | Contents |
|---|---|
| web data (embedded in `web/astrofixxer.html`) | 10,438 objects, only those named in commonly searched catalogues |
| `data/android/sky_catalog.json.gz` (2.4 MB) | 93,997 deep-sky objects with all IDs, names and dark-nebula opacity; 8,913 stars (8,912 from HYG plus M40, which Stellarium's deep-sky list lacks) with Western + Indian names and B−V colour; modern + Indian constellations; 781 IAU boundary edges precessed from B1875 to J2000 |
| `data/events/meteor_showers.json` | 43 showers × 2026–2028 |

8 importer tests pass.

**Remaining:**
- **Comets and bright asteroids:** import `data/ssystem_minor.ini` (115 comets + asteroids, orbital elements with epoch) into `data/events/minor_bodies.json`.
- **Constellation artwork:** import `skycultures/modern/illustrations` + anchor stars (owner kept this feature).
- **Android loader:** load the gzipped JSON in the background. Use a 10°×10° RA/Dec grid (648 cells) to pick objects in view. `ponytail:` upgrade to HEALPix or a binary format only if profiling shows the need.
- Deleted from the plan: faint-star import (Stellarium `stars_1`). Add it when someone asks for stars below mag 6.

---

## 5. Phase 5: Compose UI, Stellarium-style (9–10 days)

Build from the Stitch designs (`STITCH_UI_PROMPT.md`).

- **Theme:** `AstroTheme(Normal | Night)`. Night mode is a full red colour scheme plus a brightness cap. Condensed tabular numerals for readouts.
- **Sky View:**
  - Star glow by magnitude and colour, Milky Way, atmosphere from the sun's altitude.
  - **Several landscape silhouettes** (owner kept this).
  - Cardinal points, grids, ecliptic/meridian lines.
  - Constellation lines, names, boundaries and **artwork**.
  - **Light-pollution (Bortle) slider** (owner kept this).
  - Pinch zoom with a zoom-dependent magnitude limit. Tap to select; long-press menu.
- **Time travel** (owner kept this): displayed time ≠ now; rewind/forward controls. Used by "Show in sky" on events.
- **Telescope layer:** crosshair, eyepiece circle, Align, pointing modes (Compass / Manual / Free look), the guidance panel (ΔAlt/ΔAz, bullseye, haptics), and the watch-list navigator.
- **Other screens:**
  - Slide-out toolbars
  - Info overlay
  - Search (Object / Position / Lists)
  - Events
  - Sky & Viewing options
  - Location (with offline city list)
  - Date & Time
  - Object info (Wikipedia, cached)
  - Telescope settings
  - Settings (user objects, watch lists, data age, reset with confirmation)
  - Onboarding
  - Help
- **Sensors:** rotation-vector sensors, `remapCoordinateSystem`, adjustable low-pass filter, drift hint, `FLAG_KEEP_SCREEN_ON`.
- **Localisation:** move `i18n_dicts` / `po/` to `values-*/strings.xml` (`uk`, `hu`, `ru`, `iw`, new `hi`). Test RTL with Hebrew.

**Status (29 Sep 2026): built.**

- Sky View, done:
  - star colours from B−V, the Milky Way, and the atmosphere;
  - several landscapes, cardinal points, the Alt/Az and RA/Dec grids, and the meridian and ecliptic;
  - constellation lines, names, IAU boundaries and artwork, and the Bortle slider;
  - pinch zoom with a zoom-dependent limit, tap to select, and a long-press menu (target, align, add to list, info).
- Time travel, done: the clock chip with ±1 h and ±1 d, a date and time picker, and events that jump to their time.
- Telescope layer, done:
  - the crosshair, the eyepiece circle from focal lengths, and Align;
  - Compass / Manual / Free look;
  - guidance with ΔAlt/ΔAz (or ΔRA/ΔDec for equatorial mounts), a bullseye and a haptic pulse;
  - the watch-list navigator.
- Other screens, done:
  - Search (Object / Position / Lists: Messier, Caldwell, bright stars, Indian constellations (nakshatras and rashis), my objects, watch lists);
  - Events, with the Tonight card and week/month filters;
  - Sky & Viewing tabs (Sky, Deep-sky, Markings, Culture, Landscape, Telescope, Place & time, More);
  - Location, with an offline list of 58 cities, and Date & Time;
  - Object Info: constellation, rise/transit/set, the altitude-over-tonight graph and an eyepiece preview;
  - Telescope settings and general settings (data info, reset with confirmation);
  - Onboarding, and Help with search;
  - AstroGuide suggestion chips.
- Left out on purpose (`ponytail:`):
  - Wikipedia summaries: online only, against the offline rule;
  - `values-*/strings.xml`: the in-code `I18n` table works in the desktop tests and on Android alike;
  - uk/hu/ru/he: these need translators;
  - the twinkle, font-size, compact-toolbar and brightness-cap controls;
  - the adjustable sensor filter and the drift hint: tune them in the field test.
- Proof: `tools/desktop-check`. Journeys drive every new screen, and the audit covers every tab in English and Hindi, day and night, at 360 and 411 dp.

---


## 5b. Phase 5b: Offline events (7–9 days)

Every event is computed or bundled on the phone.

| Event | How |
|---|---|
| Meteor showers | Bundled JSON. Beyond 2028, convert solar longitudes on the phone (`jd_for_solar_longitude`). Radiant with drift. |
| Sun/Moon rise/set, twilights, moon phase | `core/astro`. |
| Conjunctions, Moon–planet approaches, oppositions, greatest elongations | Sample every 6 h over the next 60 days, find separation minima or elongation extremes, refine by bisection. |
| Rise/transit/set for any object | Hour-angle formula. |
| **Transit tracker** (new, owner request) | **Planet transits of the Sun** (Mercury, Venus): inferior conjunction with separation < Sun radius, from VSOP87. The next is Mercury on 2032-11-13, so it's usually a countdown. **ISS transits of the Sun/Moon**: SGP4 ground track vs the Sun/Moon disc for the observer; needs orbit data under ~2 days old. When the data is older, show "Refresh orbit data when online to predict transits". |
| **Occultations** (new, owner request) | **Moon occulting bright stars (mag ≤ 6 from the bundled catalogue) and planets**: topocentric Moon position, separation < Moon's apparent radius, with disappearance/reappearance times. Needs ~1′ Moon accuracy (see Phase 3). Asteroid occultations: `ponytail:` skipped; they need precise external predictions. |
| **Comets** (new, owner request) | Bundled elements from Stellarium `ssystem_minor.ini`. Two-body Kepler orbit + the existing Earth position gives RA/Dec. Brightness from the comet magnitude model (H, G/k). Show on the sky and in a "Comets visible tonight" list with an "elements from <date>" note. New comets appear often, so refresh elements from the MPC when online, with the bundled data as the offline fallback. |
| **Rare celestial events** (new, owner request) | **Solar and lunar eclipses**: bundled NASA eclipse table (2026–2040, public domain) plus local visibility and times computed on the phone. **Also:** supermoons (full moon within ~360,000 km), planet gatherings (≥ 4 planets within a 30° span), great conjunctions, planet transits (above), and **meteor outbursts** (Stellarium's year-specific ZHR entries). Rare events get a highlighted card and optional reminders (`AlarmManager` local notification; no server). |
| ISS and bright satellite passes | Bundled TLE snapshot + SGP4 on the phone. Refreshed when the app opens and is online (`ponytail:` no background WorkManager job). Shows the data age and hides passes when TLEs are more than 30 days old. The CelesTrak download must be tested outside this sandbox. |
| "Tonight" summary | Darkest window, planets up, moon, next meteor peak, next ISS pass, and any rare event this week. |

**Gate:** check against published values:
- Perseid/Geminid peaks: ±1 day
- a 2026 conjunction: ±1 h
- the 2026-08-12 total solar eclipse and 2026-03-03 total lunar eclipse from the NASA tables: contact times ±2 min
- one listed lunar occultation: ±2 min
- one comet position vs JPL Horizons: ±5′ near its element epoch

---

## 6. Phase 6: AstroGuide v1, offline voice (2 days)

- `SpeechRecognizer` (offline for downloaded languages; Hindi and English) → the existing search and events → `TextToSpeech`.
- Commands: "find X", "what is X", "align", "what's up tonight", "next meteor shower", "next eclipse".
- `ponytail:` the LLM backend is skipped; add it when free-form questions are actually needed.

---

## 7. Phase 7: Release (1 day)

- Signing, R8 rules.
- "Licenses & source" screen linking the GPL repo.
- Store screenshots taken by hand on a phone.
- Field test: 3 phones (with/without magnetometer), 2 telescopes, 10 Messier objects from alignment stars 5–20° away. Record time-to-target and misses.

---

## 7b. Phase 7b: Telescope setup, alignment and plate solving (owner request, 30 Sep 2026)

Branch: `feature/telescope-setup-alignment-platesolve`. Sonnet agents write the code; each workstream is reviewed, re-tested and merged by the supervising agent.

### Ponytail review

Each request was taken up the ladder until a rung held.

| Request | Rung that holds | Decision |
|---|---|---|
| Precession fix | 2 (already in the codebase) | Rewrite `Pointing.rayFromPos`/`rayToRaDec` using the IAU 1976 precession the importer already has (ported, 20 lines). **Not applied twice:** the current code uses the Earth rotation angle (ERA) with J2000 coordinates, which already cancels the RA part of precession. Measured against astropy it is off by 0.10–0.18° (not the 0.36° first estimated). The fix precesses J2000 → date and switches ERA to GMST, so each term is counted exactly once. Target: < 1′ against astropy. `ponytail:` nutation (≤ 17″) and aberration (≤ 20″) are left out; the eyepiece field is ≥ 15′. Planets already arrive as J2000 (`raJ2000`), so they go through the same path; nothing else changes. |
| Setup wizard and saved setup | 7 (new, small) | One `TelescopeSetup` data class (type, mount, placement, phone edge, eyepiece angle, erecting prism, view rotation and mirror) saved with the existing `s.` settings keys. `setupDone` is a separate key, so existing users get the wizard once and keep everything else. Illustrations are Compose `Canvas` drawings, not image files. |
| Phone placement → pointing | 2 | `Pointing.cameraRays` already takes the phone's top edge (+Y) as the telescope axis, which is "flat on the tube, top edge forward". Placement just picks the axis: ±Y, ±X or −Z. Phone on the eyepiece: the phone still moves rigidly with the tube, so the sensors stay valid. Straight-through → −Z; right angle (diagonal or Newtonian) → ask which edge points to the front of the telescope. |
| Check orientation | 7 | Two checks, both pure maths with tests. (a) **Phone axis:** centre two stars in turn. The angle between them is known, and only the right phone axis reproduces it (a compass error can't change it), so the app reports the best axis and its error. (b) **Eyepiece view:** "Nudge the telescope up / right: which way did the star move?" gives the rotation and mirror. |
| Rotation and mirroring | 7 | Applied only to drawings of the eyepiece view (the eyepiece preview, and the sky map when "Match eyepiece view" is on). Movement directions are computed from the telescope's pointing, never from the drawn image, so a mirrored view can't flip an instruction. |
| Settings that break alignment | 7 | Changing placement, phone edge or eyepiece angle clears the alignment and says why, with a Realign button. Telescope type, mount, rotation and mirror don't affect alignment, and the UI says so. |
| Alignment flow | 2 | The app keeps `Pointing.alignMatrix`. Fixed: tapping a star while picking **used to align immediately**, as if the telescope were already centred; "Align on this" did the same. New states: `PICK_STAR` → `CENTER_STAR` (star chosen; centre it in the eyepiece, then drag the map to put it under the +) → Confirm → a result card (the correction in degrees; a warning above 20°) with Retry. Only stars and planets above the horizon can be picked. |
| Drag-to-align and the + marker | 2 | Drag-to-align replaces the old Manual mode's sideways drag, which also changed the calibration during ordinary browsing. `ponytail:` Manual mode is removed. Modes are now Compass and Free look, and dragging calibrates only during `CENTER_STAR`. The calibration is saved as the alignment rotation; catalogue coordinates are never changed. The + is small, with a gap in the middle so the star stays visible. It is red in night mode. |
| Multi-star alignment | 7 | There was none. Added: "Check with another star" after aligning refines the alignment with two stars (TRIAD) and reports the error, without adding steps to basic alignment. |
| Next-star guidance | 2 | Reuses `guidance()`/`guidanceEquatorial()`. A big arrow shows the telescope movement (up/down, left/right; RA east/west and Dec north/south on equatorial mounts), with the remaining degrees. Close = within 3 eyepiece fields; On target = within half a field. The numbers, alignment age, orientation controls and camera solve go behind an expandable "More". |
| Plate-solving engine | 7 (no library fits) | astrometry.net is native C with large index files, and tetra3 is Python, so this is a small pure-Kotlin solver in `astro/`. Star detection (background, threshold, centroids), then pattern matching at a scale known from the setup, verified by projecting all stars. It must refuse a noise-only image. No uploads: `ponytail:` the online astrometry.net option is dropped, so no consent flow or API key is needed. |
| Deep star data | 2 | Stellarium's Gaia-based `stars_0..3` files to magnitude 10.5 (~590k stars) are already in the importer's Stellarium checkout. Wide camera fields solve with the bundled 8,913 stars; a 1° eyepiece field needs the deep file (~11 stars per field on average). Packed binary asset of about 5 MB. |
| Camera | 5 → CameraX | CameraX (Jetpack) for the live preview and capture, plus the Android photo picker for pictures taken with the phone's own night mode. The camera permission is asked for only when the camera opens. If it is refused, the photo picker still works. |
| Camera beside the tube | 7 | Its own camera-to-telescope offset: centre a star in the eyepiece, take a photo, and the offset is saved. Until then a solve says where the *camera* points, and "Apply to alignment" is disabled with the reason shown. Flat on the tube: the camera faces the tube, so solving is explained as unavailable. |

### Acceptance

- Precession: < 1′ against astropy for 6 stars × 3 dates; ERA and precession counted once (a test fails if either is applied twice).
- Wizard for new users and for existing users with missing setup keys; other settings survive.
- Alignment: a tap never aligns by itself. The flow is tap → Align using this star → centre → drag under + → Confirm. Reset adjustment works, drags after Confirm don't change the calibration, and Retry works.
- Plate solve on synthetic star fields (wide and 1°, rotated, mirrored, noisy, hot pixels): the centre is within 2′ (wide) or 20″ (1°). Noise, blank and saturated images fail with a helpful message.
- Every new screen is in the UI audit (touch size, clipping, contrast), in English and Hindi, day and night.
- Not testable here and flagged for the field test: real sensors, real camera capture and exposure, solving real sky photos, and accuracy on a real telescope.

## 8. Timeline

| Phase | Effort | Depends on |
|---|---|---|
| 1. Web fixes + Stellarium data | done | |
| 2. Android setup (new repo) | 0.5 d | repo created |
| 3. Astronomy core | 2–3 d | 2 |
| 4. Remaining data (comets, artwork, loader) | 1–2 d | 2 |
| 5. Compose UI, Stellarium-style | 9–10 d | 3, 4, Stitch designs |
| 5b. Offline events incl. transits, occultations, comets, rare events | 7–9 d | 3, 4 |
| 6. AstroGuide v1 (offline voice) | 2 d | 5, 5b |
| 7. Release | 1 d | 5 |

About 5 weeks for one developer. The ponytail cuts saved about 2 weeks, and the four new event features added about 1 week.

## 9. Risks

| Risk | Mitigation |
|---|---|
| Compass error near a metal tube | Manual mode when compass accuracy is low. |
| Gyro drift | Re-align per target; show time since alignment. |
| Astronomy port mistakes | Golden tests against the JS are a merge gate. |
| Moon theory too coarse for occultations | Measure in Phase 3; port a fuller lunar series only if > 1′. |
| 94k objects slow the sky view | Grid index + magnitude culling; OpenGL only if measured. |
| Stale satellite/comet data offline | Show data age; hide ISS transits when orbit data is > 2 days old, and passes when > 30 days old. |
| Stellarium data format changes | Pinned commit; importer tests fail on a bump. |
| GPL obligations missed | Licence screen + public source before release. |
