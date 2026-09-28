# Changelog

Every update and fix to AstroFixxer, newest first. Dates are when the work landed. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Version numbers match `versionName` in `app/build.gradle.kts`.

## [0.1.0] – not yet released

The first Android version: a Kotlin + Jetpack Compose port of the AstroFixxer web app, which grew out of
[AstroHopper / skyhopper](https://github.com/artyom-beilis/skyhopper) by Artyom Beilis.

### Download and build (28 Sep 2026)

- Added: anyone can download `AstroFixxer.apk` from GitHub Releases. Every push to `main` refreshes the `latest-build` release, and version tags publish a signed release.
- Added: a `preview` build type that anyone can build with no keys or accounts (`./gradlew installPreview`). It has its own ID (`org.astrofixxer.preview`).
- Security: the preview signing key is no longer in the repository. Official downloads are signed with a key in GitHub Secrets, and other builds use the local debug key. The briefly committed `app/preview.keystore` was retired before anything signed with it was published.
- Security: `.gitignore` now blocks keystores, `.env` and credential files, every push runs the gitleaks secret scanner, and a new `SECURITY.md` explains how keys are handled.
- Added: "Build it yourself" and "Make it your own" (GPLv3 terms for forks) in the README, `CONTRIBUTING.md`, and this changelog.
- Changed: credits now link AstroHopper's source, [github.com/artyom-beilis/skyhopper](https://github.com/artyom-beilis/skyhopper), in the README, the in-app licences and the store listing.

### Fixed after a full bug hunt (28 Sep 2026)

Each fix is covered by a test in `app/src/test` or `tools/desktop-check`.

**Crashes**
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
- The "Compass" and "My objects & lists" labels were cut off on 360 dp phones. Button labels now shrink to fit.
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

## Web app (`web/` in the planning repository)

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
