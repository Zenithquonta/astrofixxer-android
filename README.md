<p align="center">
  <img src="docs/art/hero.gif" width="800" alt="Pixel-art banner: Hop the frog lines up on a bright star, then hops along a dotted guide line to the Andromeda galaxy">
</p>

<h1 align="center">AstroFixxer</h1>

<p align="center">
  <b>Strap your phone to a telescope. Line up on a star. Follow the arrows. Find anything.</b><br>
  An offline star-hopping guide and Stellarium-style planetarium for Android.
</p>

<p align="center">
  <img alt="Works offline" src="https://img.shields.io/badge/works-offline-0B1026?style=for-the-badge&labelColor=E8A33D">
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-0B1026?style=for-the-badge&logo=android&logoColor=3DDC84">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Compose-0B1026?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="Licence GPLv3" src="https://img.shields.io/badge/licence-GPLv3-0B1026?style=for-the-badge">
  <a href="../../actions/workflows/android.yml"><img alt="Build" src="../../actions/workflows/android.yml/badge.svg"></a>
</p>

<p align="center">
  <a href="../../releases/download/latest-build/AstroFixxer.apk"><img alt="Download the APK" src="https://img.shields.io/badge/Download-AstroFixxer.apk-E8A33D?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

> **Never point a telescope at the Sun without a certified solar filter fixed over the front. The app can be wrong, and it is not a solar safety tool.**
> It refuses the Sun as an alignment star, but it cannot see what your telescope is really aimed at. Never use the phone as a finder for the Sun. See [POLICY.md](POLICY.md), section 4.

## Download

1. On your Android phone (Android 8 or newer), tap **[AstroFixxer.apk](../../releases/download/latest-build/AstroFixxer.apk)**. It is always the newest build of `main`.
2. Open the downloaded file. If Android asks, allow your browser or Files app to **install unknown apps**.
3. Open AstroFixxer and allow location, so the sky matches where you are.

If a newer build won't install over the old one, uninstall the old AstroFixxer first. Numbered versions are on the [Releases](../../releases) page.

What changed in each update, including every bug fix, is in **[CHANGELOG.md](CHANGELOG.md)**.

New to telescopes, or testing the beta? **[docs/BETA_GUIDE.md](docs/BETA_GUIDE.md)** walks through installing safely, mounting the phone and finding your first target, with a picture of every screen ([Word copy](docs/AstroFixxer-Beta-Tester-Guide.docx)).

### Updating

<img align="right" src="docs/screens/new/updates-available.png" width="220" alt="Sky and viewing, More tab: App updates says an update is available (0.2.0-preview, build 15), with a Download and install button">

The GitHub download can update itself: open **Sky & viewing → More → App updates** and tap **Check for updates**. If a newer
build is out, the app shows its version, size and what's new, then downloads it, checks the file's SHA-256 and hands it to
Android's installer, which asks you to confirm. Allow **install unknown apps** for AstroFixxer when Android asks, then come back.

- It only checks when you tap the button; nothing runs in the background and nothing about you is sent (see [PRIVACY.md](PRIVACY.md)).
- Builds signed with this repository's permanent key (kept in its GitHub Secrets) install over each other and keep your lists. If a build was signed with a one-off debug key instead (for example when that secret is missing, or in a fork), it can't be installed over yours: the app says so before downloading and asks for a second tap. Uninstalling AstroFixxer first would install it but erase your saved lists.
- The SHA-256 catches a damaged or wrong file. It comes from the same GitHub release as the APK, so it does not stop a tampered release; Android's signature check does (see [SECURITY.md](SECURITY.md)).
- The Google Play version has no updater (Play forbids it); Play updates it for you.

<br clear="right">

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## How it works

Most telescopes have no motors and no computer. You push them around by hand, and finding a faint galaxy means
**star-hopping**: start at a star you can see, then hop from star to star until you land on the target.
AstroFixxer does the hopping maths for you.

| 1. Align on a bright star | 2. Follow the arrows | 3. You're on target |
|:---:|:---:|:---:|
| <img src="docs/screens/new/align-1-pick.png" width="250" alt="Align mode on the sky map: Tap the star you will centre in the telescope, with Vega shown beside the + marker"> | <img src="docs/screens/new/guide-1-arrow.png" width="250" alt="Guidance panel: a big arrow and Up 1.0 degrees, Left 12.0 degrees to M57"> | <img src="docs/screens/new/guide-3-on-target.png" width="250" alt="Guidance panel: On target, M57, 0.2 degrees to go"> |
| The phone's sensors know roughly where it points. Centre a bright star in the eyepiece, then drag the map until that star is under the +. | Up/down and left/right, in degrees, until both numbers are near zero. | The Ring Nebula is in the eyepiece. |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## The telescope tools, step by step

Every screenshot below is a real screen of the app, drawn by the desktop test harness (see [Proof it works](#proof-it-works)); the pixel animations are drawings.
The sensors, the camera and the alignment have not been tried on a real phone or telescope yet, see [Tested and not yet tested](#tested-and-not-yet-tested).

### 1. Set it up once

On the first launch a short wizard (up to six steps, with pictures) asks what kind of telescope you have, what its mount is, where the phone
is fixed (flat on the tube, camera facing along it, or on the eyepiece) and the few follow-ups that placement needs.
**Set up later** keeps the defaults. You can change everything in **Sky & viewing → Telescope & orientation**.

| Telescope | Mount | Phone | Summary |
|:---:|:---:|:---:|:---:|
| <img src="docs/screens/new/setup-type.png" width="180" alt="Setup wizard step 1: What kind of telescope is it? Refractor, Reflector (Newtonian) or Something else"> | <img src="docs/screens/new/setup-mount.png" width="180" alt="Setup wizard step 2: What is the mount? Alt-azimuth, Equatorial or Other"> | <img src="docs/screens/new/setup-placement.png" width="180" alt="Setup wizard step 3: Where is your phone mounted? Flat against the tube, camera facing along the telescope, or attached to the eyepiece"> | <img src="docs/screens/new/setup-summary.png" width="180" alt="Setup wizard last step: All set, with a drawing of the phone on the tube and a summary of the choices"> |

### 2. Align on a star

Alignment tells the phone where the telescope really points, so the map and the arrows agree with the sky.

1. Tap **Align**, then tap a bright star, a planet, the Moon or any other object you can centre, well above the horizon. (Pressing and holding an object and choosing **Align on this object** starts the same thing.)
2. **Centre that star in the eyepiece** by moving the telescope.
3. **Drag the map** until the star is under the +. This step only lines up the map on screen; it never moves the telescope.
4. Tap **Confirm alignment**. The result card says how big the correction was and warns if it is large. **Reset adjustment** undoes your dragging; **Retry** does the same star again.

Phones with no compass work too: dragging the map in step 3 also fixes the start direction.

<p align="center"><img src="docs/art/align-how.gif" width="640" alt="Pixel-art animation: the telescope is moved until Vega is centred in the eyepiece, then a finger drags the map until Vega sits under the + and the alignment is confirmed"></p>

| Tap a star | Centre it | Drag the map | Aligned |
|:---:|:---:|:---:|:---:|
| <img src="docs/screens/new/align-1-pick.png" width="180" alt="Align mode: Tap the star you will centre in the telescope"> | <img src="docs/screens/new/align-2-centre.png" width="180" alt="Vega selected: step 1 centre Vega in the eyepiece, step 2 drag the map to place Vega under the +, with a Confirm alignment button"> | <img src="docs/screens/new/align-3-dragged.png" width="180" alt="The map has been dragged so that Vega is under the + marker"> | <img src="docs/screens/new/align-4-result.png" width="180" alt="Result card: Aligned on Vega, Correction 5.0 degrees, with Retry and Done buttons"> |

### 3. Next-star guidance

Pick a target in **Find** and the guidance panel shows one big arrow, the move in words, and the distance left. It turns amber when you are close
and shows a filled bullseye on target. **More** has the exact numbers, **Check with another star**, and controls that turn or mirror the view to match your eyepiece.

<p align="center"><img src="docs/art/next-star.gif" width="640" alt="Pixel-art animation: an arrow points from Vega toward M57, turns amber as the distance shrinks and ends on On target"></p>

| Follow the arrow | Close | On target |
|:---:|:---:|:---:|
| <img src="docs/screens/new/guide-1-arrow.png" width="220" alt="Guidance panel: Move to M57, Up 1.0 degrees and Left 12.0 degrees, 12.0 degrees to go"> | <img src="docs/screens/new/guide-2-close.png" width="220" alt="Guidance panel in amber: Close to M57, Down 0.1 degrees and Left 2.0 degrees"> | <img src="docs/screens/new/guide-3-on-target.png" width="220" alt="Guidance panel: On target, M57, 0.2 degrees to go"> |

### 4. Align with a photo (camera plate solve)

Not sure the alignment is right, or the compass is off near the metal tube? Take a photo of the stars and let the phone work out where the telescope points.
Open it from the alignment panel (**Align with a photo**) or from **Sky & viewing → Telescope & orientation → Solve with camera**.

<p align="center"><img src="docs/art/solve-how.gif" width="800" alt="Pixel-art animation in five steps: the phone sits on the eyepiece; it takes a photo of 1 to 4 seconds; the app matches triangles of stars in the photo against its star list, offline; it finds where it points; and Apply to alignment works only when the phone did not move"></p>

| Arrangement | Live view | Solved | Aligned |
|:---:|:---:|:---:|:---:|
| <img src="docs/screens/new/camera-1-arrangement.png" width="180" alt="Solve with camera, first step: the phone is on the eyepiece, 125 mm telescope and 25 mm eyepiece, magnification x5"> | <img src="docs/screens/new/camera-2-live-eyepiece.png" width="180" alt="Live camera view of a star field with a + in the middle, exposure choices Auto, 1 s, 2 s and 4 s, and a Take photo button"> | <img src="docs/screens/new/camera-3-result.png" width="180" alt="Result: Solved, the camera pointed at RA 18h36m56s Dec +38 47, in the constellation Lyra, 35 stars matched, with Apply to alignment, Show on map and Retry buttons"> | <img src="docs/screens/new/camera-4-aligned.png" width="180" alt="Back on the sky map after Apply: the result card says Aligned from a photo, with the size of the correction"> |

| When it fails | Camera beside the tube | Beside the tube, live |
|:---:|:---:|:---:|
| <img src="docs/screens/new/camera-5-failed.png" width="180" alt="This photo could not be solved: advice for the cause, and Nothing was changed, the alignment and the map are as they were"> | <img src="docs/screens/new/camera-beside-1-arrangement.png" width="180" alt="Arrangement for a camera beside the tube: Camera offset not calibrated, with a Calibrate camera offset button"> | <img src="docs/screens/new/camera-beside-2-live.png" width="180" alt="Live view for a camera beside the tube, with the telescope marker at the calibrated spot"> |

What it does, and what it does not:

- **It runs offline, on the phone.** The app matches the star pattern in your photo against a star list stored inside the app. No internet, no server.
- **Photos are never saved or sent.** They stay in memory while the app works on them ([PRIVACY.md](PRIVACY.md)). The camera permission is asked for only when the live view opens. If you refuse it, you can still solve a photo from the gallery, but that cannot align the telescope.
- **Photos take 1 to 4 seconds.** The app offers Auto, 1 s, 2 s and 4 s where the phone allows it. Hold the phone still and do not touch the telescope.
- **It aligns only if the phone did not move.** If the phone moved more than 0.3 degrees during the photo, **Apply to alignment** is refused and the app asks for another photo.
- **Phone on the eyepiece:** the middle of the photo is where the telescope points.
- **Camera beside the tube:** the camera and the telescope do not point exactly the same way, so calibrate the offset once (pick a star, centre it in the eyepiece, take a photo, **Save camera offset**). Until then the app says where the camera points and **Apply to alignment** stays off, with the reason.
- **Phone flat on the tube:** the camera faces the tube and cannot see the sky, so it cannot solve. The app offers to change the phone placement.
- **A failed solve changes nothing.** You get advice for the likely cause (too few stars, too bright, trailed stars, no match) and the alignment and map stay as they were.

It has been tested on synthetic star photos, not on a real sky yet (see [Tested and not yet tested](#tested-and-not-yet-tested)).

### 5. Check the eyepiece orientation

Directions seem wrong, or the picture in the eyepiece is a mirror image? **Telescope & orientation → Check orientation** asks two quick things:
which way the phone points along the telescope (from two alignment stars), and which way your eyepiece shows the sky (nudge the telescope up and right, and say which way the star moved).
**Match eyepiece view**, **Rotate view 90°** and **Mirror view** under **More** then turn the map to look like your eyepiece.

| Two checks | Nudge and answer | Result |
|:---:|:---:|:---:|
| <img src="docs/screens/new/orient-1-menu.png" width="200" alt="Check orientation menu with two buttons: Check the phone position and Check the eyepiece view"> | <img src="docs/screens/new/orient-2-question.png" width="200" alt="Check the eyepiece view: nudge the telescope up a little, which way did the star move in the eyepiece? Buttons Up, Down, Left, Right"> | <img src="docs/screens/new/orient-3-result.png" width="200" alt="Your eyepiece view is: upright, with Use this view and Try again buttons"> |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## What's inside

<p align="center"><img src="docs/art/planets.gif" width="720" alt="Pixel planets: the Moon cycling through its phases, Jupiter turning with its Great Red Spot, Saturn and its rings, and Mars"></p>

- 🌌 **About 100,000 objects that need no internet**: stars, galaxies, nebulae, clusters, planets, comets and asteroids, built from [Stellarium](https://stellarium.org)'s open catalogues and the HYG star database.
- 🔭 **Push-to guidance** for manual telescopes, with a first-run setup wizard, guided one-star alignment (centre the star, then drag the map under the +, which also serves phones without a compass), next-star guidance, a Compass mode and a Free look mode.
- 📷 **Camera plate solve**: take a photo of the stars and the phone works out where the telescope points, offline. It is new and not yet tested on a real sky.
- 🗓️ **Events for the next 60 days, all worked out on the phone**: eclipses, meteor showers, conjunctions, supermoons, planet gatherings, Mercury and Venus transits, occultations of bright stars by the Moon, bright comets, visible passes of the ISS and the Tiangong space station, and ISS crossings of the Sun and Moon.
- ⏳ **Time travel**: step the sky by hours or days, or jump straight to any event.
- 🏞️ **A Stellarium-style sky**: twilight colours, the Milky Way, landscapes, a light-pollution slider, and Western and Indian (Vedic) constellations with artwork (the modern illustrations are under the Free Art License, the Indian sky culture and its illustrations under CC BY-SA 4.0).
- 🎙️ **AstroGuide**, an offline voice assistant: *"find Jupiter"*, *"what is M31"*.
- 🔴 **Night mode**: everything turns red, so your eyes stay dark-adapted.
- 🇮🇳 **A Hindi interface** is coming in a later release. The app is English-only for now.

| Events | Time travel | Night mode |
|:---:|:---:|:---:|
| <img src="docs/screens/new/events.png" width="190" alt="Events list: tonight's summary, then the Full Moon, the Orionids peak and the Moon covering Antares"> | <img src="docs/screens/new/time-travel.png" width="190" alt="Time-travel bar with the clock set a day ahead, and the Now button to come back"> | <img src="docs/screens/new/night.png" width="190" alt="Night mode: the whole screen in red, with the guidance arrow to M57"> |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## A galaxy in your pocket

<img align="left" src="docs/art/galaxy.gif" width="240" alt="A pixel spiral galaxy, tilted like Andromeda, slowly turning">

**M31, the Andromeda Galaxy**, is about 2.5 million light-years away: the most distant thing most people can see
with their own eyes. Its light set out before there were humans to look at it.

In a dark sky it's a faint smudge that is easy to miss. AstroFixxer finds it in three steps:
1. Tap **Find** and type *M31* (or *Andromeda*).
2. **Align** on a bright star nearby, such as Mirach in Andromeda: centre it in the eyepiece, drag the map under the +, confirm.
3. Follow the arrows until both read zero.

The same works for about 94,000 other galaxies, nebulae and star clusters, all stored on the phone.

<br clear="left">

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Every dot is something it can find

<p align="center"><img src="docs/art/every-object.png" width="800" alt="All-sky map of 94,000 galaxies, nebulae and clusters"></p>

That's all **93,997 deep-sky objects** in the app, plotted on one map of the whole sky:
<span>🔵 galaxies</span>, <span>🩷 nebulae</span> and <span>🟡 star clusters</span>.

<details>
<summary><b>Why is there an empty arc through the galaxies?</b></summary>

<br>That arc is our own galaxy. The Milky Way's disc is full of dust, and the dust hides the galaxies behind it.
Astronomers call this the *zone of avoidance*. The nebulae and star clusters crowd along the same arc for the
opposite reason: they are *part* of the Milky Way. The bright clump at the bottom is the Large Magellanic Cloud, a
neighbouring galaxy full of its own clusters.

No one drew that arc. It appears by itself when you plot the catalogue (`tools/repo-art/sky_map.py`).
</details>

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Meet Hop

<img align="right" src="docs/art/hop.gif" width="192" alt="Hop the frog blinking on a moon rock">

**Hop** is AstroFixxer's mascot, a star-hopper by trade.

Look closely at the forehead: that's a **red headlamp**. Real astronomers read their charts by red light because it
doesn't undo the half-hour your eyes need to adapt to the dark. That's also why the app has a night mode.

In the banner, Hop does what the app does: lines up on a bright guide star (ringed in pink), follows the dotted guide
line from star to star, and ends up under a faint galaxy while the reticle locks on.

<br clear="right">

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Proof it works

Every push runs:

| Check | What it proves |
|---|---|
| `./gradlew test` (app) | Astronomy maths matches the original web app to 1e-9, SGP4 matches Vallado's published test case, 2026's eclipses, equinoxes and sunrises land within minutes of published times, and the plate solver finds the right sky position in synthetic star photos. |
| `tools/desktop-check` journeys | The real Compose screens, driven by simulated taps: tutorial, find, align, guide, cancel, Back button, time travel, events, night mode, lists, manual location, the setup wizard, the camera solve flow and the update panel. |
| `tools/desktop-check` audit | Every screen, including every step of the camera flow, in English (Hindi returns with its interface), day and night, on 360 dp and 411 dp phones: controls at least 48 dp, no clipped or overlapping text, and colour contrast of at least 4.5:1 by day (3:1 for night mode's dim red). |

The screenshots in this README are produced by those tests: `./gradlew test --offline --tests ReadmeShotsTest --tests ReadmeCameraShotsTest -PreadmeShots` in `tools/desktop-check`, then `python3 tools/repo-art/readme_screens.py`. The README tests are skipped unless `-PreadmeShots` is given.

### Tested and not yet tested

| Tested | Not yet tested on a real phone or telescope |
|---|---|
| Unit tests for the astronomy, the catalogue, the events and the plate solver | Real phone sensors (compass, gyroscope) and how well the alignment holds up |
| Synthetic star photos made from the app's own catalogue, solved by the real solver | Camera2 capture, manual exposure and the camera's reported field of view |
| The real screens on a desktop test harness, and the screen audit | Real star photos, with real noise, haze and light pollution |
| The build and tests on GitHub's machines (CI) | Alignment accuracy at the eyepiece |
| | Android's DownloadManager and installer, and the live updater against a real release |

None of the right-hand column has been tried on a real phone or telescope yet. How it will be tried is in **[docs/FIELD_TEST.md](docs/FIELD_TEST.md)**.

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## What's in this repository

| Folder | What it is |
|---|---|
| `app/` | The Android app (Kotlin + Jetpack Compose). The Gradle project is at the repository root. |
| `web/` | The AstroFixxer web app (PWA) with its bug fixes. Deploy it with Vercel and *Root Directory* set to `web`. |
| `tools/stellarium_import/` | Builds the offline sky data from Stellarium and the HYG database. It writes to `data/`, which is not tracked; copy the results into `app/src/main/assets/`. |
| `tools/desktop-check/` | Runs the real screens on the desktop for the user journeys, the UI audit and the stress tests. |
| `tools/repo-art/` | Draws Hop and the README's pixel art. |
| `tools/golden/` | Makes golden test values from the web app's own code. |
| `docs/` | The implementation plan, the handoff log, the Stitch UI brief, the field test, screenshots and art. |
| `store/` | The Google Play listing draft and the 512 px icon. |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Build it yourself

You don't need any accounts, keys or secrets.

**With Android Studio (easiest):** install [Android Studio](https://developer.android.com/studio), choose *File → New → Project from
Version Control*, paste this repository's URL, and press ▶ Run with your phone plugged in (USB debugging on) or an emulator.

**From the command line:** you need JDK 17 and the Android SDK (Android Studio installs both; otherwise set `ANDROID_HOME`).

```sh
git clone <this repository's URL> astrofixxer-android
cd astrofixxer-android
./gradlew assemblePreview    # installable APK: app/build/outputs/apk/preview/app-preview.apk
./gradlew installPreview     # or put it straight onto a connected phone
./gradlew test               # astronomy and data tests
(cd tools/desktop-check && ./gradlew test)   # UI journeys, audit and stress tests; screenshots in build/screens
python3 tools/repo-art/make_art.py           # regenerates the pixel art (needs Pillow)
```

The `preview` build is optimised like a release and has its own application ID (`org.astrofixxer.preview`), so it sits
next to a Play Store copy instead of replacing it. Your own build is signed with your computer's debug key. A build signed with this repository's permanent key, kept in GitHub Secrets, installs over the previous official download; when that secret is missing (a fork, for example) CI signs with a throwaway debug key, and the app says such a build can't be installed over yours (see `SECURITY.md`). The downloadable build's `versionCode` is the CI run number plus 1000 (`VERSION_CODE_OFFSET` in `android.yml`); a local build has `versionCode` 1.

## Make it your own

AstroFixxer is free software under the GNU GPL v3. You may download it, study it, change it, and share your own version, including
selling it, as long as your version is also GPLv3 with its source available, and it keeps the credits below. Fork this repository,
change what you like, and your fork's Actions build and publish its own `AstroFixxer.apk` automatically. See `CONTRIBUTING.md` to
send changes back.


## Releasing to Google Play

1. Make an upload keystore once: `keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Keep it and its passwords out of git.
2. Add these repository secrets: `KEYSTORE_BASE64` (from `base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
3. Set `versionName` in `app/build.gradle.kts` and add the release to `CHANGELOG.md`, then push a tag such as `v0.1.0`. `versionCode` is not edited by hand: the workflow sets it to the run number plus an offset of 1000 (`VERSION_CODE_OFFSET` in `release.yml`), so it only grows. The Release workflow uploads the signed `app-release.aab`.
4. Make this repository public first (or otherwise offer the source). The GPL requires it, and the in-app licence text links here.
5. The store text, data-safety answers and 512 px icon are in `store/`. The privacy policy is `PRIVACY.md`.

For a signed build on your own machine, set `ASTROFIXXER_KEYSTORE`, `ASTROFIXXER_KEYSTORE_PASSWORD`, `ASTROFIXXER_KEY_ALIAS` and `ASTROFIXXER_KEY_PASSWORD`, then run `./gradlew bundleRelease`.

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Credits and licence

GPLv3 (see `LICENSE`), as AstroHopper requires. Made for Smart India Hackathon 2025.

- Based on **AstroHopper** by Artyom Beilis (GPLv3): source at [github.com/artyom-beilis/skyhopper](https://github.com/artyom-beilis/skyhopper), app at [artyom-beilis.github.io/astrohopper.html](https://artyom-beilis.github.io/astrohopper.html). AstroFixxer started as a fork of it for Smart India Hackathon 2025, and the pointing, alignment and position maths follow its design.
- Deep-sky catalogue, names, meteor showers and comet orbits come from Stellarium (GPL-2.0-or-later). The sky cultures come from Stellarium (CC BY-SA 4.0). The modern constellation illustrations are under the Free Art License; the Indian sky culture and its illustrations are CC BY-SA 4.0. The deep star list for plate solving is made from Stellarium's Gaia DR3 and Hipparcos catalogues (CC BY-SA 3.0 IGO). Full credits: [NOTICE.md](NOTICE.md). Star positions come from the HYG database (CC BY-SA).
- The planet series (VSOP87, via vsop87-multilang) and the position reduction (CPReduce) are by Greg Miller and are in the public domain (`app/src/main/java/org/astrofixxer/astro/vsop87/`).
- The golden test values in `app/src/test/resources/golden.json` were generated from the web app's own code.
- Hop and the pixel art are original, drawn in code in `tools/repo-art/`.

---

[LICENSE](LICENSE) · [POLICY.md](POLICY.md) · [NOTICE.md](NOTICE.md) · [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) · [CONTRIBUTING.md](CONTRIBUTING.md)

No warranty. **Never point a telescope at the Sun without a certified solar filter; the app can be wrong.** See [POLICY.md](POLICY.md).
