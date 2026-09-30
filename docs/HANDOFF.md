# AstroFixxer Handoff Log

A running log. **Add a new entry at the top every time code is implemented or changed**, using the template at the bottom. Anyone picking up the work (a person or an AI agent) should be able to continue from the latest entry without reading the chat history.

## Where things are

| Item | Location |
|---|---|
| Upstream web app (read-only reference) | `github.com/AadidevRaizada/AstroFixxer`, analysed at commit `6af76ce` |
| This repository (everything) | https://github.com/Zenithquonta/astrofixxer-android, branch `main`. The old planning repo `Zenithquonta/astrofixer-baby` was merged in on 28 Sep 2026 and can be archived |
| Local clone of upstream (not committed, 4.7 GB) | `AstroFixxer/` (gitignored) |
| Stitch UI prompt | `docs/STITCH_UI_PROMPT.md` |
| Implementation plan | `docs/IMPLEMENTATION_PLAN.md` |
| Field test protocol | `docs/FIELD_TEST.md` |
| Fixed web app (deploy root) | `web/` |
| Stellarium importer | `tools/stellarium_import/` |
| Android app | `app/` (Gradle project at the repository root) |
| UI proof suite and repo art | `tools/desktop-check/`, `tools/repo-art/` |
| Design renders from early phases | `docs/design-renders/` |
| Codebase analysis (web page) | https://claude.ai/artifact/SMf5adtidjB5CsJt5ZrxNW |
| Bug-hunt and UI proof report | https://claude.ai/artifact/WxJfBipQQCRwaAE9qAkGYb |

## Current status

| Phase (see plan) | Status |
|---|---|
| 0. Analysis, UI prompt, plan | Done |
| 1. Web app fixes + Stellarium data | Done (`web/`); needs a Vercel project with Root Directory `web` |
| 2. Android project setup | Done; green CI in this repository (build, UI audit, secret scan) |
| 3. Astronomy core | Done: 36 JVM tests pass |
| 4. Stellarium offline data | Done: catalogue, comets/asteroids, meteor showers, constellation artwork |
| 5. Compose UI (Stellarium-style) | Done: Object Info with altitude graph, markings (grid, ecliptic, meridian, IAU boundaries), star colours, eyepiece field, telescope and place & time settings, Tonight card, Free look, sky view, atmosphere, Milky Way, landscapes, light pollution, artwork, alignment, guidance, search, events, lists, onboarding, help, night mode, Hindi. Built and UI-tested in CI |
| 5b. Offline events | Done, including ISS/Tiangong passes and Sun/Moon transits (SGP4). The live TLE download is untested here (CelesTrak blocked) |
| 6. AstroGuide v1 (offline voice) | Done: English and Hindi commands, speech in/out |
| 7b. Telescope setup, alignment, plate solving | In progress on `feature/telescope-setup-alignment-platesolve`: precession fix, plate solver and setup/alignment/guidance merged. Camera flow and in-app updater in progress |
| 7. Release | Prepared: signing via CI secrets, signed-bundle workflow on tags, R8 in CI, launcher icon, privacy policy, store listing, field-test protocol. Needs the owner: upload key, Play account, repo made public, field test, screenshots |


---

## Entries

### 2026-09-30: Phase 7b (in progress): precession fix, plate solver, telescope setup and guided alignment

Branch `feature/telescope-setup-alignment-platesolve`. Sonnet agents wrote the code, each in its own worktree and branch (`feature/tsap-w1` to `w5`). The supervising agent reviewed every diff, re-ran the tests and merged.

**What was done**
- Plan: Phase 7b written with a ponytail review (`docs/IMPLEMENTATION_PLAN.md` section 7b). Decision: Camera2 instead of CameraX (rung 4). Google Maven is blocked here, and Camera2 adds no dependency.
- W1, precession:
  - `Pointing.rayFromPos`/`rayToRaDec` now apply IAU 1976 precession and IAU 1982 GMST, each counted once. The old formula (the Earth rotation angle with J2000 coordinates) already cancelled the precession in RA.
  - Worst error against astropy over 66 cases fell from 801″ to 28.3″.
  - The equatorial grid uses coordinates of date (`rayFromPosOfDate`).
- W2, plate solver:
  - The importer decodes Stellarium's Gaia DR3/Hipparcos star files. The new asset `solver_stars.bin` holds 579,984 stars to V 10.5 (2.94 MB, 5 bytes per star, a cell index).
  - Pure-Kotlin `StarDetector`, `PlateSolver` and `SolverStars`: triangle matching at a known scale, then a least-squares similarity fit with parity.
  - Acceptance: at least 6 matches, stars spread over at least a quarter of the frame, scale inside the hint, and false-alarm probability × hypotheses < 1e-6.
- W3, setup, alignment and guidance:
  - `astro/Mounting.kt`: phone axis per placement, axis check, nudge → view orientation, TRIAD.
  - First-run setup wizard, and the Telescope & orientation tab with a preview, an orientation check, and rotate/mirror.
  - New alignment flow: PICK_STAR → CENTER_STAR → drag the map under the fixed + → Confirm → result card with Retry. The calibration is computed exactly from the star and verified to land within 0.01°.
  - Drags outside alignment never change the calibration. Manual mode is removed.
  - Checking with a second star refines the alignment (TRIAD).
  - Next-star guidance: arrow, distance, Close/On target, and an expandable More section.
  - `Pointing.alignMatrix` now uses atan2. The old asin was wrong for azimuth differences over 90°.
  - Setup and calibration are saved (`setupDone`, `mountType`, `placement`, …, `alignMatrix`, `alignStar`, `alignedAt`). The old `equatorial` key is migrated.

**Dependencies added**
- None. The new asset is `app/src/main/assets/solver_stars.bin`.

**How to verify (results on the merged branch, commit 5511495)**
- JVM unit tests (`astro/`): 85 tests, 0 failures. They include PrecessionTest 7, PlateSolverTest 19, SolverStarsTest 7 and MountingTest 12.
- Importer: 19 tests OK (`HYG_CSV=... STELLARIUM_DIR=... python3 -m unittest test_build_sky_data`).
- Desktop UI suite (W3 branch before the merge): 82 tests, 0 failures. The audit checked 440 screen variants with no findings. The re-run on the merged branch is in progress.
- Android compile check (W3 branch): clean.

**W6, legal policy (merged)**
- New `POLICY.md`: scope and relationship to GPLv3 (adds no restriction), no warranty, limitation of liability, eye safety, accuracy, data handling, third-party services and data licences, contributions (DCO sign-off, inbound=outbound GPLv3, no CLA), security reporting, names, governing law and contact.
- Governing law, per the owner: Indian law for the policy only; the user's mandatory home-country rights prevail; no exclusive court; the GPL is not modified.
- New `NOTICE.md` (attributions). `README.md` has a footer linking LICENSE, POLICY, NOTICE, PRIVACY, SECURITY and CONTRIBUTING.
- Review: every data-flow claim was checked against `MainActivity.kt`, the manifests and the W4/W5 branches.
- Found and queued for the docs pass after W4/W5 merge:
  - `PRIVACY.md` is wrong on two points: a typed location is saved, and Ask is tap-to-start, not hold. Its storage list is incomplete, and camera, TTS and the update check are missing.
  - `CONTRIBUTING.md` needs the DCO.
  - The in-app licence text needs the Gaia/CelesTrak credits and a privacy link. Only the modern art is Free Art License; the Indian art is CC BY-SA.
  - The plan lists NASA eclipse tables that nothing uses: eclipses are computed in `Events.kt`.
- For a lawyer: liability limits for personal injury (eye damage); Gaia CC BY-SA 3.0 IGO next to GPLv3 (treated as separate data); ePrivacy/DPDP for on-device-only processing; the "AstroFixxer" name (upstream web app); `web/images/qs_*.png` © Maxim Tonkikh.

**Known issues / not done**
- Nothing here has run on a phone or telescope: sensors, the camera, real star photos and alignment accuracy are all untested. The solver has only seen synthetic images.
- W4 (Camera2 plate-solve flow) and W5 (in-app GitHub updater) are in progress.

**Next step**
- Merge W4 and W5, run the full suites, get CI green, then merge to `main`.

### 2026-09-29: New UI and sky plotting (Stitch brief, Phase 5)

**What was done**
- Importer:
  - star B−V colours from HYG;
  - IAU constellation boundaries from Stellarium's modern sky culture, precessed B1875 → J2000 (IAU 1976);
  - M40 added.
- Catalogue: 93,997 deep-sky objects, 8,913 stars and 781 boundary edges.
- Sky plotting:
  - star colours;
  - RA/Dec grid, meridian and ecliptic;
  - boundaries;
  - the eyepiece circle;
  - the bullseye in guidance;
  - RA/Dec guidance for equatorial mounts.
- Object Info sheet: facts, constellation, tonight's rise/transit/set, altitude graph with twilight and the Moon, and an eyepiece preview.
- New screens:
  - Find with Object, Position and Lists tabs;
  - Sky & viewing with 8 tabs, including Telescope and Place & time (58 cities, date and time);
  - the Tonight card, and Events filters;
  - the long-press quick menu;
  - Free look mode;
  - AstroGuide question chips;
  - settings saved between launches, and Reset all.

**Files changed**
- `tools/stellarium_import/build_sky_data.py` and its tests: `bv`, `boundaries`, `precess`, `EXTRA_STARS`.
- `app/src/main/assets/sky_catalog.json.gz`: regenerated.
- `astro/Catalog.kt`: `bv`, `Boundary`, `constellationAt`, `constellationName`; loads gzip or plain.
- New UI files:
  - `ui/SkyMarkings.kt`: markings, star colours, eyepiece circle;
  - `ui/Visibility.kt`: altitude samples, rise/set, the Tonight summary;
  - `ui/ObjectInfo.kt`;
  - `ui/Sheets.kt`: Find, Sky & viewing, Tonight, quick menu, chips.
- `ui/SkyCanvas.kt`, `ui/SkyState.kt`, `ui/SkyScreen.kt`, `ui/I18n.kt` and `MainActivity.kt`: wiring, new state and settings persistence, Hindi.
- `tools/desktop-check`: `PlottingTest`, `ObjectInfoTest`, `NewScreensTest`, and new audit screens.

**How to verify**
- `cd tools/desktop-check && ./gradlew test`:
  - 38 tests pass;
  - `build/screens/audit.txt` reads "Checked 192 screen variants. No findings.";
  - screenshots are in `build/screens/`.
- App unit tests: 41 pass. Importer: `STELLARIUM_DIR=<stellarium> python3 -m pytest tools/stellarium_import` (12 pass).

**Decisions**
- Left out on purpose (see the plan's Phase 5 status):
  - Wikipedia summaries;
  - moving strings to `strings.xml`;
  - Ukrainian, Hungarian, Russian and Hebrew;
  - twinkle, font size, compact toolbars and brightness cap;
  - the adjustable sensor filter.
- The "Nakshatras" list holds 49 entries (nakshatras and rashis), so it is named "Indian constellations".

**Known issues / not done**
- Compose 1.5's test tree keeps recycled lazy-list rows (outside the list's visible area), so tests check `assertIsNotDisplayed`, not `assertDoesNotExist`, for rows that should be gone.
- Not yet tried on a real phone; the field test (`docs/FIELD_TEST.md`) covers it.

**Next step**
- Owner:
  1. Install the latest `AstroFixxer.apk` from Releases and try Object Info, Free look and the Telescope tab outside.
  2. The repository steps from the entry below still stand: make it public, archive `astrofixer-baby`, and add the preview signing secrets.

### 2026-09-28: One repository

**What was done**
- Merged the planning repository `Zenithquonta/astrofixer-baby` into this one, with its history. Everything now lives here:
  - the web app (`web/`) and the importer (`tools/stellarium_import/`), plus `tools/golden/`;
  - the plan, the handoff log, the Stitch brief and the field test (`docs/`), and the early renders (`docs/design-renders/`).
- Left out: the Android backup bundle (the app is here now), the importer's generated `data/` (now gitignored; its output is already in `app/src/main/assets/`), and the duplicate README images.
- The README has a "What's in this repository" table.
- This repository's workflow now publishes `AstroFixxer.apk` as the Latest release on every push to `main`, the same way the old repository did, so there is a direct download here.
- Checked: `tools/golden` run from the new layout gives output byte-identical to `app/src/test/resources/golden.json`.

**Next step**
- Owner:
  1. Make this repository **public** (Settings → General → Danger zone → Change visibility), so anyone can download and build it; the GPL also needs the source to be public.
  2. Archive `astrofixer-baby` (Settings → Archive this repository).
  3. Add the two `PREVIEW_KEYSTORE_*` secrets here.

### 2026-09-28: Android repository live with green CI

**What was done** (Android `main` at `3b00f88`)
- Pushed the app to https://github.com/Zenithquonta/astrofixxer-android. The repository's starter README commit was merged in, not overwritten.
- First CI runs there:
  - `build` passed: tests, debug, release (R8 and lint), preview APK and the sky-data check.
  - `ui-check` found a real bug: in Hindi, "My objects & lists" was cut off at 360 dp with the runner's Devanagari font. That button now has its own row.
  - `secret-scan` broke on the gitleaks action (it fails on a push that includes the first commit). It now runs the pinned, checksummed gitleaks program over the full history, in both repos.
- Run #3 is green on all three jobs: https://github.com/Zenithquonta/astrofixxer-android/actions/runs/36420235146
- The public download in this repository was rebuilt with the fix (run #7, green).

**Next step**
- Owner: add `PREVIEW_KEYSTORE_BASE64` and `PREVIEW_KEYSTORE_PASSWORD` (values in the session scratchpad `signing/`) to both repositories' Actions secrets, so downloads update in place. Then test the app on a phone with `docs/FIELD_TEST.md`.

### 2026-09-28: Direct APK download live; launch crash fixed; README animations

**What was done** (Android `main` at `3cca8cb`, saved in `handoff/astrofixxer-android.bundle`)
- **Direct download:** the "Android APK" workflow now publishes `AstroFixxer.apk` as this repository's *Latest* GitHub Release on every push.
  - Link: https://github.com/Zenithquonta/astrofixer-baby/releases/download/android-latest/AstroFixxer.apk
  - Verified by downloading it anonymously: HTTP 200, a signed APK with package `org.astrofixxer.preview`, version `0.1.0-preview`.
  - Signing: with no preview secrets set, each build is signed with that run's throwaway debug key (private and never reused), so installing a newer build means uninstalling the old one. Adding the secrets switches to the permanent key.
- **Launch crash fixed:**
  - Found by inspecting that APK: Android's packager unzips `.gz` assets and drops the extension, so `assets.open("sky_catalog.json.gz")` would have crashed every phone at startup.
  - Fix: `Catalog.load` now sniffs gzip or plain JSON, and `MainActivity` opens whichever name exists. There is a new unit test (39 in total).
  - CI in both repositories now fails if the APK is missing its sky data.
- **README:** new pixel animations from `tools/repo-art/cosmos.py`, used in both READMEs:
  - a turning spiral galaxy, in a new "A galaxy in your pocket" section;
  - a planets strip: Moon phases, Jupiter's Great Red Spot, Saturn's rings, and Mars;
  - twinkling star dividers between sections.

### 2026-09-28: First real Android build passes on GitHub

**What was done**
- Actions run #2 of "Android APK" in this repository (https://github.com/Zenithquonta/astrofixer-baby/actions/runs/36414493679) built the app from `handoff/astrofixxer-android.bundle` with the real Android SDK and Gradle plugin.
  - Result: `./gradlew test assemblePreview` gave BUILD SUCCESSFUL (99 tasks). The unit tests pass, and the R8-optimised preview APK (about 6.5 MB) is kept as the `AstroFixxer-apk` artifact.
  - The `secret-scan` (gitleaks) job passed.
  - Publishing was skipped as designed, because the preview signing secrets are not set yet.
- This is the first time the app has built outside the sandbox. It clears the "never built for real" caveat for the build, but not for testing on a phone.

**Next step**
- Owner: add `PREVIEW_KEYSTORE_BASE64` and `PREVIEW_KEYSTORE_PASSWORD` to this repository's Actions secrets to publish the public `AstroFixxer.apk`, then install it on a phone and run `docs/FIELD_TEST.md`.

### 2026-09-28: Keys secured; changelog

**What was done** (Android `main` at `fc996f3`, saved in `handoff/astrofixxer-android.bundle`)
- **Secret scan:** gitleaks, detect-secrets and a regex sweep over the full history of both repos found no API keys, tokens or passwords.
  - The Google Analytics ID in old web-app commits (`G-5L31YEPT3E`) is public by design and was already removed.
- **Retired the preview signing key** that had been committed an hour earlier (`app/preview.keystore`), and cancelled the build that would have published an APK signed with it.
  - Preview signing now comes from the secrets `PREVIEW_KEYSTORE_BASE64` and `PREVIEW_KEYSTORE_PASSWORD`.
  - Without the secrets, the app still builds and tests, but nothing is published.
- **Guard rails (both repos):**
  - `.gitignore` for keystores, `.env` and credential files;
  - a `secret-scan` job (gitleaks) in CI;
  - `SECURITY.md` in the Android repo.
- **`CHANGELOG.md`** in both repos lists every update and fix, and is linked from the READMEs.

**Next step**
- Owner: add the two preview secrets to this repository (and later to `astrofixxer-android`) under Settings → Secrets and variables → Actions. The values were generated in the session scratchpad (`signing/`), not in any repo. Then run the "Android APK" workflow again to publish `AstroFixxer.apk`.

### 2026-09-28: Anyone can download, build and fork; credit to skyhopper

**What was done** (Android `main` at `e5b9079`, saved in `handoff/astrofixxer-android.bundle`)
- **Downloads:**
  - Android repo: every push to `main` publishes `AstroFixxer.apk` to a rolling `latest-build` release; `v*` tags publish a release with the signed APK.
  - This repo: `.github/workflows/android-apk.yml` builds the app from the bundle and publishes `AstroFixxer.apk` as the `android-latest` release, so there is a public download link now.
- **Preview build type:** optimised like a release, signed with the public `app/preview.keystore`, application ID `org.astrofixxer.preview`. Anyone can build it with no secrets, and updates install over each other.
- **Docs:**
  - READMEs (both repos): Download section with a direct link, "Build it yourself", and the GPL terms for forks.
  - `CONTRIBUTING.md` in the Android repo.
- **Credits:** AstroHopper's source, github.com/artyom-beilis/skyhopper, is credited in both READMEs, the in-app licences and the store listing.

**How to verify**
- The Actions run "Android APK" in this repository is the first real Android build. It runs the app's tests and `assemblePreview`.

**Known issues / not done**
- The Android repo is private and still unreachable from this session, so its own release links only work once it is public and pushed.

### 2026-09-28: README with the new design, published in this repository

**What was done**
- Added a root `README.md` here with the Android README's design: Hop's hero animation, how it works, features, the all-sky map and Meet Hop. The images are in `docs/readme/`, and a "Where things are" section covers this repository.
- Why: the Claude GitHub app still can't reach `Zenithquonta/astrofixxer-android` (`add_repo` says "not found"), so the Android repository's README can't be pushed yet.

**Next step**
- Owner: give the Claude GitHub app access to `astrofixxer-android` (github.com/apps/claude/installations/select_target → Repository access). Then push `main` from `handoff/astrofixxer-android.bundle`.

### 2026-09-28: Bug hunt, UI proven by tests, repo redesign (Hop the mascot)

**What was done** (Android `main` at `c4b194a`, saved in `handoff/astrofixxer-android.bundle`)
- **Bug hunt:** read the whole app and fixed 22 bugs, each covered by a test. The full list is in the Android commit message and the proof report.
  - Crashes (3): long numbers in RA/Dec; far off-screen labels when zoomed in; constellation art that fails to decode.
  - Broken flows:
    - The Back button closed the app.
    - An accidental Align lost the alignment.
    - No hint appeared when a target was picked before aligning.
    - A target beside the view showed no arrow.
    - Manual drag ran at half speed.
    - GPS overwrote a typed location.
    - Events stayed stuck on the startup location and day.
    - Time travel had no entry point of its own.
    - Two AstroGuide misunderstandings.
    - Watch-list names with spaces broke the list.
  - Polish: clipped labels, 32 dp switches, night-mode contrast, catalogue codes on the target card, cryptic "?" items, an untranslated chip, an empty Find box.
- **New UI:**
  - A clock chip opens time travel (−1 d, −1 h, +1 h, +1 d, Now) and shows the displayed date when not live.
  - Cancel while picking an alignment star.
  - An align-first hint.
  - "Visible now" suggestions in Find, and Enter picks the first result.
  - Button labels shrink to fit.
  - The target card shows the friendly name and "55° up · W".
- **Proof suite** in `tools/desktop-check`: the real Compose screens on the desktop JVM (Compose Multiplatform 1.5.12), added to CI as the `ui-check` job, which keeps the screenshots as an artifact.
  - `JourneyTest`: 11 user flows driven by simulated finger taps.
  - `AuditTest`: 96 variants (English and Hindi × day and night × 360 dp and 411 dp × 12 screens) checked for 48 dp targets, no clipped, off-screen or overlapping text, and contrast of 4.5:1 by day and 3:1 at night. No findings.
  - `StressTest`: about 231k random inputs, `near()` checked against brute force, 160 random renders, and events at the poles.
- **Repo design:**
  - Hop the pixel frog mascot and an animated hero GIF, drawn by `tools/repo-art/make_art.py`.
  - An all-sky map of all 93,997 bundled deep-sky objects (`sky_map.py`), which shows the zone of avoidance.
  - A new README with screenshots taken by the tests.

**How to verify**
- `./gradlew test`: 38 app tests pass.
- `cd tools/desktop-check && ./gradlew test`: 19 tests pass, and `build/screens/audit.txt` reads "No findings".
- `MainActivity` compiles against the Android 14 classes (a stub-based check).
- Proof report: https://claude.ai/artifact/WxJfBipQQCRwaAE9qAkGYb

**Known issues / not done**
- No real Android build has run yet (Google Maven is blocked here), and nothing has been tested on a real phone.
- The desktop test harness uses Compose 1.5; Android uses the 2024.12 BOM, so small spacing differences are possible.
- On the desktop, `performClick()` is a mouse click, and a hovering mouse swallows the next touch. Tests press buttons with a simulated finger through `tap()`. This is not an app bug.

**Next step**
- Owner: approve the report, then create the empty private repo `Zenithquonta/astrofixxer-android`. I'll push and fix whatever the first CI run reports.

### 2026-09-28: Phase 7 release preparation

**What was done** (Android `main` at `e2f786d`, saved in `handoff/astrofixxer-android.bundle`)
- **Signing:**
  - `app/build.gradle.kts` reads the upload key from `ASTROFIXXER_KEYSTORE`, `ASTROFIXXER_KEYSTORE_PASSWORD`, `ASTROFIXXER_KEY_ALIAS` and `ASTROFIXXER_KEY_PASSWORD`. Without them, release builds are unsigned. No key is in git.
  - `.github/workflows/release.yml` builds a signed `app-release.aab` when a `v*` tag is pushed, using the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
- **R8:** CI now also runs `assembleRelease`, so R8 and release lint run on every push. No keep rules were needed: there is no reflection or serialization, `org.json` is part of the platform, and Compose and Kotlin ship their own rules. Resource shrinking is on.
- **Launcher icon** (there was none): an adaptive vector icon (a finder reticle around a star, with a themed monochrome layer) and `store/icon-512.png` for Play.
- **Licence screen:** the Help text now gives the source URL (`SOURCE_URL` in `SkyScreen.kt`). The GPL requires offering the source, so **the repo must be public before publishing**.
- **`PRIVACY.md`:**
  - Covers location (used on the phone, not saved), the microphone (the phone's speech service, which may be online) and the internet (the CelesTrak download only).
  - States that there are no accounts, ads or analytics.
- **`store/listing.md`:** short and full description (each feature claim checked against the code), category, data-safety answers and the graphics still needed.
- **Planning repo:** `docs/FIELD_TEST.md` gives the protocol, record sheet and pass criteria for the plan's field test.
- **README:** added the release steps.

**How to verify**
- `MainActivity` still compiles against the Android 14 classes, and the UI render tests pass.
- The Gradle signing block and both workflows have not run yet (the Android plugin comes from Google Maven, which is blocked here). The first CI run checks them.

**Known issues / not done**
- There is no feature graphic and there are no screenshots: they need a real phone at night.
- `versionCode` has to be bumped by hand for each release.

**Next step**
- Owner: create the repo, then generate the upload key (README, "Releasing to Google Play") and add the 4 secrets. Run the field test using `docs/FIELD_TEST.md`.

### 2026-09-28: MainActivity checked against the Android framework

**What was done** (Android `main` at `4e45381`, saved in `handoff/astrofixxer-android.bundle`)
- The Android SDK can't be downloaded here (Google Maven is blocked), so I compiled all of `app/src/main/java` (`MainActivity`, `ui/`, `astro/`) against Robolectric's `android-all:14` jar (the real Android 14 framework classes, from Maven Central). The few `androidx.activity` calls (`ComponentActivity`, `setContent`, the permission launcher, `asImageBitmap`) came from small stubs.
- The result was a clean build. The only warning was the deprecated `URL(String)` in the CelesTrak download, which now uses `URI(...).toURL()`.
- Removed the stale duplicate status table from this file.

**How to verify**
- Run the first GitHub Actions build (`./gradlew test assembleDebug`) once the repo exists.

**Known issues / not done**
- It is still not a real Android build: resources, the manifest merge, AGP and dexing have never run.

**Next step**
- Owner: create `Zenithquonta/astrofixxer-android` (private, empty). I'll push `main` and fix whatever CI reports.

### 2026-09-28: ISS passes and transits; event titles in Hindi

**What was done** (Android `main` at `b279c17`, saved in `handoff/astrofixxer-android.bundle`)
- **`Sgp4.kt`: near-Earth SGP4** (Vallado 2006, WGS-72), plus TLE parsing and GMST. Deep-space SDP4 is not ported (`ponytail:`); satellites with periods of 225 min or more are skipped.
- **`Satellites.kt`:**
  - Observer geometry, TEME→Earth-fixed, alt/az, and a cylindrical Earth-shadow test.
  - Passes above 10°. A pass is *visible* when the satellite is sunlit and the Sun is below −6°.
  - Sun/Moon transits: closest approach refined by golden section.
- **Events:**
  - Visible ISS and Tiangong passes for 3 days, with max height, directions and orbit-data age.
  - Transits only when the data is under 2 days old; nothing when it's over 30 days old.
  - An info item appears when no orbit data has been downloaded yet.
- **`MainActivity`:** fetches CelesTrak `GROUP=stations` at most daily when online, caches it in `filesDir/tle.txt`, and adds the `INTERNET` permission (only for this).
- **Event titles and details** are now English templates plus arguments, translated at display time (Hindi table added). AstroGuide matches events by template key, not by English text.

**How to verify**
- `Sgp4Test`:
  - Vanguard 1 (00005) matches Vallado's published r/v at t = 0 and t = 360 min to 1 m and 1 mm/s.
  - The 2008 ISS TLE gives 320-380 km altitude and 7.5-7.9 km/s.
  - A point 400 km overhead is at the zenith.
  - 2-8 ISS passes per day over Delhi, each under 8 min.
- Events with the 2008 ISS TLE list a visible pass on 2008-09-20 13:42 UTC (19:12 IST), 23° high, W to N.
- 36 JVM tests pass. The UI compiles with Compose Desktop, and all renders still work.

**Known issues / not done**
- The CelesTrak download and the Android build have never run here (both hosts blocked).
- Transits need an accurate location; the ground strip is a few km wide.

**Next step**
- Owner: create `Zenithquonta/astrofixxer-android`. I'll push, then fix whatever CI reports.

### 2026-09-28: Phases 5-6 completed (scenery, artwork, rare events, lists, AstroGuide, Hindi)

**What was done** (Android repo `main` at `40299ec`, saved in `handoff/astrofixxer-android.bundle`)
- **Rare events:**
  - Mercury/Venus transits (tested: 2032-11-13 Mercury transit within 1 h; none in 2026)
  - supermoons (tested: all 2026 full moons 355-407 thousand km; 2026 has 13 full moons including the 31 May blue moon)
  - planet gatherings
  - lunar occultations of stars brighter than mag 3.5 for the observer (synthetic test: a star on the Moon's path is occulted for 40-120 min)
  - All appear in Events with ★.
- **Scenery:**
  - Atmosphere colour from the Sun's altitude, with stars fading in twilight.
  - Milky Way band along the galactic plane (conversion tested: galactic centre at RA 266.40°, Dec −28.94°).
  - Five landscapes; Bortle 1-9 light pollution.
  - Stellarium constellation artwork: 85 modern + 27 Indian, placed by three anchor stars (affine solve tested). Decoded at half size, only for the chosen culture.
- **Lists:** user objects and watch lists in the web app's formats (parsers tested), a ‹ › navigator, saved with SharedPreferences.
- **Onboarding and help:** 4-step onboarding on first launch; Help includes the GPL/data licences.
- **AstroGuide v1:** offline keyword understanding in English and Hindi, answers from the catalogue and events, platform speech recognition (prefers offline) and text-to-speech.
- **Hindi UI:** translation table keyed by English text, following the phone language, with a switch in Sky & viewing.

**How to verify**
- 31 JVM tests pass for `astro/`.
- The `ui/` package compiles against Compose Multiplatform 1.5.12 (desktop). Headless renders are in `docs/design-renders/` (then `docs/screens/` in the planning repo): guidance, night, wide, Milky Way, dusk/city, constellation art, watch list, onboarding, AstroGuide, Hindi.

**Known issues / not done**
- **Android build never run**: Google Maven is blocked here. `MainActivity` (sensors, location, speech, art loading) is reviewed by hand only. Expect to fix small compile issues on the first CI run.
- ISS and satellite passes, and ISS transits: need a TLE download plus SGP4. Not started.
- Events titles are still English in Hindi mode. Ukrainian, Hungarian, Russian and Hebrew UI translations (the web app had them) are not ported yet.
- Release signing, the Play listing and the device field test need the owner.
- A commit mistake happened this session: `d7f8d6d` did not compile (a command chain committed despite a compile error). It was fixed in the next commit `40299ec`. Later commits only happen after the checks pass.

**Next step**
- Owner: create `Zenithquonta/astrofixxer-android` (private, empty) and say so. I'll push `main`, then fix whatever the first CI build reports.
- Then: ISS passes (TLE + SGP4), remaining translations, device testing.

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
