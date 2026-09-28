<p align="center">
  <img src="docs/art/hero.gif" width="800" alt="Pixel-art banner: Hop the frog lines up on a bright star, then hops along a dotted guide line to the Andromeda galaxy">
</p>

<h1 align="center">AstroFixxer</h1>

<p align="center">
  <b>Strap your phone to a telescope. Tap a star. Follow the arrows. Find anything.</b><br>
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

## Download

1. On your Android phone (Android 8 or newer), tap **[AstroFixxer.apk](../../releases/download/latest-build/AstroFixxer.apk)**. It is always the newest build of `main`.
2. Open the downloaded file. If Android asks, allow your browser or Files app to **install unknown apps**.
3. Open AstroFixxer and allow location, so the sky matches where you are.

New builds install over the old one and keep your lists. Numbered versions are on the [Releases](../../releases) page.

What changed in each update, including every bug fix, is in **[CHANGELOG.md](CHANGELOG.md)**.

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## How it works

Most telescopes have no motors and no computer. You push them around by hand, and finding a faint galaxy means
**star-hopping**: start at a star you can see, then hop from star to star until you land on the target.
AstroFixxer does the hopping maths for you.

| 1. Point at a bright star, tap it | 2. Follow the arrows | 3. You're on target |
|:---:|:---:|:---:|
| <img src="docs/screens/pick-star.png" width="250" alt="Picking Vega as the alignment star"> | <img src="docs/screens/guidance.png" width="250" alt="Guidance panel: move up and left to M57"> | <img src="docs/screens/on-target.png" width="250" alt="On target: M57"> |
| The phone's sensors know roughly where it points. Tapping the star the telescope is really on fixes the rest. | Up/down and left/right, in degrees, until both numbers are near zero. | The Ring Nebula is in the eyepiece. |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## What's inside

<p align="center"><img src="docs/art/planets.gif" width="720" alt="Pixel planets: the Moon cycling through its phases, Jupiter turning with its Great Red Spot, Saturn and its rings, and Mars"></p>

- 🌌 **About 100,000 objects that need no internet**: stars, galaxies, nebulae, clusters, planets, comets and asteroids, built from [Stellarium](https://stellarium.org)'s open catalogues and the HYG star database.
- 🔭 **Push-to guidance** for manual telescopes, with one-star alignment, a Compass mode, and a Manual mode for phones without a compass.
- 🗓️ **Events for the next 60 days, all worked out on the phone**: eclipses, meteor showers, conjunctions, supermoons, planet gatherings, Mercury and Venus transits, occultations of bright stars by the Moon, bright comets, visible passes of the ISS and the Tiangong space station, and ISS crossings of the Sun and Moon.
- ⏳ **Time travel**: step the sky by hours or days, or jump straight to any event.
- 🏞️ **A Stellarium-style sky**: twilight colours, the Milky Way, landscapes, a light-pollution slider, and Western and Indian (Vedic) constellations with artwork.
- 🎙️ **AstroGuide**, a voice assistant in English and Hindi: *"find Jupiter"*, *"what is M31"*, *"मंगल कहाँ है"*.
- 🔴 **Night mode**: everything turns red, so your eyes stay dark-adapted.

| Events | Time travel | Night mode | हिन्दी |
|:---:|:---:|:---:|:---:|
| <img src="docs/screens/events.png" width="190" alt="Events list"> | <img src="docs/screens/time-travel.png" width="190" alt="Time-travel bar"> | <img src="docs/screens/night.png" width="190" alt="Night mode"> | <img src="docs/screens/hindi.png" width="190" alt="Hindi interface"> |

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## A galaxy in your pocket

<img align="left" src="docs/art/galaxy.gif" width="240" alt="A pixel spiral galaxy, tilted like Andromeda, slowly turning">

**M31, the Andromeda Galaxy**, is about 2.5 million light-years away: the most distant thing most people can see
with their own eyes. Its light set out before there were humans to look at it.

In a dark sky it's a faint smudge that is easy to miss. AstroFixxer finds it in three steps:
1. Tap **Find** and type *M31* (or *Andromeda*).
2. **Align** on a bright star nearby, such as Mirach in Andromeda.
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
| `./gradlew test` (app) | Astronomy maths matches the original web app to 1e-9, SGP4 matches Vallado's published test case, and 2026's eclipses, equinoxes and sunrises land within minutes of published times. |
| `tools/desktop-check` journeys | The real Compose screens, driven by simulated taps: tutorial, find, align, guide, cancel, Back button, time travel, events, night mode, Hindi, lists, manual location. |
| `tools/desktop-check` audit | Every screen in English and Hindi, day and night, on 360 dp and 411 dp phones: controls at least 48 dp, no clipped or overlapping text, and colour contrast of at least 4.5:1 by day (3:1 for night mode's dim red). |

The screenshots in this README are produced by those tests.

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
next to a Play Store copy instead of replacing it. Your own build is signed with your computer's debug key. The official
downloads are signed with a key kept in GitHub Secrets, so only this repository can publish updates to them (see `SECURITY.md`).

## Make it your own

AstroFixxer is free software under the GNU GPL v3. You may download it, study it, change it, and share your own version, including
selling it, as long as your version is also GPLv3 with its source available, and it keeps the credits below. Fork this repository,
change what you like, and your fork's Actions build and publish its own `AstroFixxer.apk` automatically. See `CONTRIBUTING.md` to
send changes back.

The planning docs, the Stitch UI brief and the handoff log live in `Zenithquonta/astrofixer-baby` under `docs/`.

## Releasing to Google Play

1. Make an upload keystore once: `keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Keep it and its passwords out of git.
2. Add these repository secrets: `KEYSTORE_BASE64` (from `base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then push a tag such as `v0.1.0`. The Release workflow uploads the signed `app-release.aab`.
4. Make this repository public first (or otherwise offer the source). The GPL requires it, and the in-app licence text links here.
5. The store text, data-safety answers and 512 px icon are in `store/`. The privacy policy is `PRIVACY.md`.

For a signed build on your own machine, set `ASTROFIXXER_KEYSTORE`, `ASTROFIXXER_KEYSTORE_PASSWORD`, `ASTROFIXXER_KEY_ALIAS` and `ASTROFIXXER_KEY_PASSWORD`, then run `./gradlew bundleRelease`.

<p align="center"><img src="docs/art/divider.gif" width="800" alt=""></p>

## Credits and licence

GPLv3 (see `LICENSE`), as AstroHopper requires. Made for Smart India Hackathon 2025.

- Based on **AstroHopper** by Artyom Beilis (GPLv3): source at [github.com/artyom-beilis/skyhopper](https://github.com/artyom-beilis/skyhopper), app at [artyom-beilis.github.io/astrohopper.html](https://artyom-beilis.github.io/astrohopper.html). AstroFixxer started as a fork of it for Smart India Hackathon 2025, and the pointing, alignment and position maths follow its design.
- Deep-sky catalogue, names, meteor showers and comet orbits come from Stellarium (GPL-2.0-or-later). The sky cultures come from Stellarium (CC BY-SA 4.0), and the constellation artwork is under the Free Art License. Star positions come from the HYG database (CC BY-SA).
- The planet series (VSOP87, via vsop87-multilang) and the position reduction (CPReduce) are by Greg Miller and are in the public domain (`app/src/main/java/org/astrofixxer/astro/vsop87/`).
- The golden test values in `app/src/test/resources/golden.json` were generated from the web app's own code.
- Hop and the pixel art are original, drawn in code in `tools/repo-art/`.
