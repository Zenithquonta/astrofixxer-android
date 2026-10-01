# Changelog

Every update and fix to AstroFixxer, newest first. Dates are when the work landed. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Version numbers match `versionName` in `app/build.gradle.kts`.

## [0.1.0] – not yet released

The first Android version: a Kotlin + Jetpack Compose port of the AstroFixxer web app, which grew out of
[AstroHopper / skyhopper](https://github.com/artyom-beilis/skyhopper) by Artyom Beilis.

### Hindi interface switched off for now (1 Oct 2026)

- The Hindi interface is switched off for now and returns in a later release. The app shows English only, the language choice is hidden, and a saved Hindi language loads as English. The Indian (Vedic) sky culture is not affected.

### Telescope setup, alignment and guidance (30 Sep 2026)

- **First-run wizard.** Four to six short steps with pictures: telescope type, mount, where the phone is mounted (flat on the tube, camera facing along it, or on the eyepiece) and the follow-ups that placement needs. "Set up later" keeps the defaults. People who already had the app see the wizard once and keep all their other settings.
- **Sky & viewing → Telescope & orientation** replaces the Telescope tab: the setup, a picture of the arrangement, the eyepiece, and **Check orientation** (which phone axis points along the telescope, from two alignment stars; and which way the eyepiece shows the sky, from two nudges of the telescope).
- **Alignment fixed.** Tapping a star used to align at once, as if the telescope were already centred. Now: Align, tap the star, centre it in the eyepiece, drag the map until it is under the +, Confirm. The result card says how big the correction was, warns above 20° and has Retry. "Align using this star" in the long-press menu and Object Info starts the same flow. The calibration is saved, so the chip can say "Aligned 3 h ago".
- **Manual mode is gone.** Its sideways drag changed the calibration while you browsed. Modes are Compass and Free look, and only the alignment steps let a drag move the calibration map (which also fixes the start azimuth on phones without a compass).
- **Check with another star** (under More) reports how far off the alignment was and refines it with both stars.
- **Guidance** is a big arrow with words ("↗ Up 3.2° · Right 5.1°") and the distance; Close is amber, On target is a filled bullseye. Details, the eyepiece-view controls and the check are behind More.
- **Match eyepiece view** draws the sky map turned and mirrored like the eyepiece; directions never change. The + is now a small marker at the exact centre.

### In-app updates for the GitHub download (30 Sep 2026)

- **Sky & viewing → More → App updates** (the GitHub preview download only): shows the installed version and build, and **Check for updates** compares it with the newest build on GitHub. If there is one, it shows the version, the size and a link to what's new, then **Download and install**: a system download with a notification and progress, a check of the file's SHA-256, and Android's own installer, which always asks you first.
- Nothing is checked in the background. GitHub is contacted only when you tap the button, and nothing about you is sent (see `PRIVACY.md`).
- **Safe by construction.** Only HTTPS addresses inside this repository's own release are used, `update.json` and the APK have size limits, the APK must match the SHA-256 and size in `update.json` (a mismatch deletes it), and Android refuses an update signed with a different key (see `SECURITY.md`).
- **Builds without the permanent signing key** are flagged in the app: they can't install over yours, so the app says so plainly and needs a second tap before downloading one.
- Every failure has its own message: offline, timeout, GitHub's request limit (with when to try again), no release, a release without `update.json`, unreadable data, a release for another app, a full phone, a cancelled or damaged download.
- **Google Play builds have none of this.** Play forbids apps that update themselves, so the screen and the `REQUEST_INSTALL_PACKAGES` permission exist only in the preview and debug builds (`app/src/preview` and `app/src/debug`), and CI fails if the release build asks for the permission.
- **Release metadata.** Every published preview build has a `versionCode` that grows with the build number, and the `latest-build` release carries `update.json` next to `AstroFixxer.apk`, with the release title showing the build number. CI checks the APK's signature and that its versionCode and applicationId match `update.json`. Numbered tag releases are no longer marked Latest, so the updater keeps finding the preview build. Tag releases (the Google Play bundle) now also get a growing `versionCode` from the build number, because Play rejects a repeated one.

### New screens and sky plotting (29 Sep 2026)

Built from the Stitch UI brief and Phase 5 of the implementation plan.

**Sky plotting**
- Stars are drawn in their real colours, from each star's B−V colour index (off in night mode, and there's a switch for it).
- New sky markings, each with a switch under Sky & viewing → Markings:
  - the RA/Dec grid with hour labels;
  - the meridian;
  - the ecliptic;
  - the official IAU constellation boundaries (781 edges from Stellarium, precessed from 1875 to J2000 and checked against astropy to 0.4″).
- The eyepiece circle shows the true field of your telescope and eyepiece around the crosshair.
- The guidance panel has a bullseye that fills in when the target is inside the eyepiece field. With an equatorial mount, directions are in RA/Dec.
- Wherever you are in the sky, the app can name the constellation.

**Object Info** (tap the target card, a search result's Info button, or long-press an object)
- Names, type, magnitude, size, constellation, RA/Dec and where it is now.
- Tonight: rise, highest point and set times, with an altitude graph from 16:00 to 08:00. The graph shades twilight and dark sky, draws the Moon's path and marks the time now.
- "In the eyepiece": the object drawn to scale in your eyepiece's field.

**New screens**
- Find has three tabs:
  - Object: constellations are now searchable, and each result says "Up · 45°" or "Rises 21:10";
  - Position: go to an RA/Dec;
  - Lists: Messier, Caldwell, bright stars, Indian constellations, My objects and watch lists.
- Sky & viewing has eight tabs: Sky, Deep-sky (filter by type), Markings, Culture, Landscape, Telescope, Place & time, and More.
- Telescope settings: telescope focal length, eyepiece focal length and apparent field give the true field and magnification. Also here: mount type and vibration on target.
- Place & time:
  - choose from 58 cities, type coordinates, or go back to GPS;
  - set any date and time.
- The Tonight card in Events shows:
  - sunset and sunrise, and the fully dark window;
  - Moon rise and set, and how much of it is lit;
  - which planets are well placed.

  Events can be filtered to this week or this month.
- Long-press any object for a quick menu: target, align on it, add to a list, info.
- Free look: a third pointing mode where you drag the sky freely, with no compass.
- AstroGuide suggests questions to tap when there is no microphone or you'd rather not speak.
- Settings are now remembered between launches.
- A two-step "Reset all" returns every setting to its default.
- Everything new is translated into Hindi.

**Data**
- Stars now carry their B−V colour index, and the constellation boundaries are included.
- M40 (Winnecke 4) added, because Stellarium's deep-sky list lacks it, so the Messier list now has all 110.

**Fixed while building it**
- The Find tabs crashed on opening: a self-sizing label can't be measured inside a tab row.
- The Tonight card corrupted the screen state and crashed: it returned early from inside the layout.
- The guidance readouts wrapped or ran together once the bullseye was added.
- Panels were see-through, so the sky showed behind the text. They are now opaque.
- The UI audit found these, and they are fixed:
  - search result details were cut off on 360 dp phones;
  - "Reset all" was a 40 dp touch target.
- The "Nakshatras" list also held the 12 rashis. It is now called "Indian constellations".

**Proof** (`tools/desktop-check`)
- 38 tests pass:
  - new suites: `PlottingTest`, `ObjectInfoTest` and `NewScreensTest`;
  - the Sun is on the drawn ecliptic all year to within 0.05°;
  - Vega is inside Lyra's boundary;
  - Vega's maximum altitude matches 90° − |latitude − declination|;
  - Polaris is always up and σ Octantis never rises from Delhi;
  - Delhi's dark window matches the almanac.
- The audit now checks 192 screen variants, with no findings. The app's 41 unit tests and 12 importer tests pass.

### Download and build (28 Sep 2026)

- Changed: one repository for everything. The planning repository (web app, data tools, docs) was merged into this one with its history, so the Android app, web app, importer and docs now live together.

- Added: anyone can download `AstroFixxer.apk` from GitHub Releases. Every push to `main` refreshes the `latest-build` release, and version tags publish a signed release.
- Added: a `preview` build type that anyone can build with no keys or accounts (`./gradlew installPreview`). It has its own ID (`org.astrofixxer.preview`).
- Security: the preview signing key is no longer in the repository. Official downloads are signed with a key in GitHub Secrets, and other builds use the local debug key. The briefly committed `app/preview.keystore` was retired before anything signed with it was published.
- Security: `.gitignore` now blocks keystores, `.env` and credential files, every push runs the gitleaks secret scanner, and a new `SECURITY.md` explains how keys are handled.
- Added: "Build it yourself" and "Make it your own" (GPLv3 terms for forks) in the README, `CONTRIBUTING.md`, and this changelog.
- Changed: credits now link AstroHopper's source, [github.com/artyom-beilis/skyhopper](https://github.com/artyom-beilis/skyhopper), in the README, the in-app licences and the store listing.

### Fixed after a full bug hunt (28 Sep 2026)

Each fix is covered by a test in `app/src/test` or `tools/desktop-check`.

**Crashes**
- On a real phone the app would crash at launch: Android's packager unzips `.gz` assets and renames them, so `sky_catalog.json.gz` could not be found. Found by inspecting the first APK built on GitHub; the catalogue now loads either way, and CI checks every APK carries its sky data.
- Typing an over-long number as a coordinate in My objects (for example `99999999999:00`) closed the app on Save. Declinations past the pole (`90:30`) were also accepted.
- Zoomed in, labels of stars far off the screen broke text layout and closed the app.
- A constellation picture that failed to load closed the app.

**Broken flows**
- The phone's Back button closed the app instead of the open panel, the tutorial, star picking or the AstroGuide bubble.
- Tapping Align by mistake threw away a good alignment. Picking a star now keeps the old alignment until a new one is made, and it can be cancelled.
- Choosing a target before aligning showed no directions and no hint. The app now explains how to align.
- A target just beyond the edge of the screen showed neither its ring nor the arrow pointing to it.
- In Manual mode the sky moved at about half the finger's speed, and slower still when looking high.
- A location typed in Settings was replaced by GPS whenever the app came back to the front. It is now kept and remembered.
- Occultations and space-station passes stayed computed for the default location (New Delhi) and for the day the app opened.
- Time travel could only be reached through Events, and the screen never showed which time it was drawing.
- AstroGuide: "आज रात क्या दिखेगा" (what's up tonight) switched night mode on instead of answering, and spoken "Messier 42" or "N G C 7000" was not understood.
- A watch list named with spaces ("Autumn galaxies:") was split into a list called "galaxies" and an item called "Autumn".

**Polish**
- The "Compass" and "My objects & lists" labels were cut off on 360 dp phones. Button labels now shrink to fit, and "My objects & lists" has its own row, because in Hindi it was still cut off in some fonts (caught by the UI audit on GitHub's machines).
- Settings switches had 32 dp touch targets. Whole rows are now 56 dp switches.
- Night-mode text was too dim (1.9–2.9:1 contrast). It is now at least 3:1 and still pure red.
- The target card listed catalogue codes such as "PGC3517795 · PK063+13.1". It now shows the name ("Ring Nebula") and "55° up · W".
- Unfound watch-list items showed as a cryptic "? M99", and list comments lost their commas.
- The alignment status chip stayed in English when Hindi was selected.
- The Find box was blank until you typed. It now suggests what's visible now, and Enter picks the best match.

### Added in the same round (28 Sep 2026)

- Time travel from the clock chip: −1 day, −1 hour, +1 hour, +1 day and Now. The chip turns pink and shows the date while you are away from the present.
- A Cancel button while picking an alignment star.
- A proof suite that drives the real screens (`tools/desktop-check`):
  - 11 user journeys;
  - an audit of 96 screen variants for touch size, clipped or overlapping text, and contrast;
  - stress tests with about 231,000 random inputs and 160 random skies.
- CI now runs it on every push.
- The repo's new look: Hop the pixel-frog mascot, an animated banner, and a map of all 93,997 bundled deep-sky objects (`tools/repo-art`).

### Release preparation (28 Sep 2026)

- Release signing from CI secrets, and a workflow that builds a signed App Bundle for Google Play on version tags.
- R8 shrinking and release lint run on every push.
- An adaptive launcher icon and a 512 px store icon.
- A privacy policy (`PRIVACY.md`), a store listing draft (`store/listing.md`), and the source link in the in-app licences.
- Replaced the deprecated `URL(String)` in the orbit download.

### AstroGuide and Hindi (28 Sep 2026)

- An offline voice assistant in English and Hindi: "find Jupiter", "what is M31", "what's up tonight", "next meteor shower", "align", "night mode on", "मंगल कहाँ है".
- A full Hindi interface, including event titles, switchable in Settings.

### Events, satellites and rare sky events (28 Sep 2026)

- Worked out on the phone for the next 60 days:
  - moon phases, lunar and solar eclipses, equinoxes and solstices, meteor-shower peaks;
  - planet conjunctions, Moon–planet pairings, supermoons, planet gatherings;
  - Mercury and Venus transits, lunar occultations of bright stars, bright comets.
- Visible ISS and Tiangong passes, and ISS crossings of the Sun and Moon. This uses SGP4 orbits that are refreshed from CelesTrak at most once a day; everything else works offline.
- Tapping an event shows the sky at that time.

### Sky view and data (28 Sep 2026)

- A Stellarium-style sky:
  - atmosphere and twilight colours, the Milky Way, five landscapes;
  - a light-pollution (Bortle) slider, an alt/az grid;
  - Western and Indian (Vedic) constellations, with artwork.
- About 100,000 built-in objects from Stellarium's catalogues and the HYG star database, plus comets and asteroids from Stellarium's orbit elements.
- Search by catalogue number (Messier, NGC, IC, Caldwell and more) or by name.
- Push-to guidance with one-star alignment, Compass and Manual modes, and night mode.
- Your own objects by RA/Dec, and watch lists to step through with ‹ and ›.
- A quick-start tutorial and Help.

### Astronomy core (28 Sep 2026)

- VSOP87 planets and a full apparent-place reduction (light time, aberration, precession, nutation, topocentric correction), ported from the web app.
- These match the web app's own results to 1e-9 in golden tests.
- Checked against 2026 events:

  | Event | Error |
  |---|---|
  | Full moon | +0.2 min |
  | Lunar eclipse maximum | +0.9 min |
  | Equinox | −5.8 min |
  | Delhi sunrise | −0.5 min |

- The coordinate parser now accepts the Unicode minus sign (−), which the web app rejected.

## Web app (`web/`)

### Fixed (28 Sep 2026)

- **Service worker:**
  - its version now comes from the build commit;
  - it pre-caches the page, landing page, manifest and icon, so the app works offline;
  - it has one `activate` handler;
  - it is registered as `sw.js`.
- **Page layout:** added the missing doctype, and the star map is sized from the viewport, so it no longer collapses.
- **Coordinate inputs:** capped at 90° and 180°.
- **HTML escaping:** `&` is escaped first, so names containing it display correctly.
- **Code errors:** implicit global variables and wrong `arc()` arguments.
- **Removed:**
  - the "Coming Soon" AstroGuide alert button;
  - Google Analytics;
  - the local Python test server with its empty certificate;
  - an unused 3D page;
  - `__pycache__` folders.
- **Data:** the Stellarium catalogue is now embedded, with 10,438 objects (up from 9,759), and Indian star names are searchable.
