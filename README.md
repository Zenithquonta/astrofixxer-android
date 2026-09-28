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

---

## How it works

Most telescopes have no motors and no computer. You push them around by hand, and finding a faint galaxy means
**star-hopping**: start at a star you can see, then hop from star to star until you land on the target.
AstroFixxer does the hopping maths for you.

| 1. Point at a bright star, tap it | 2. Follow the arrows | 3. You're on target |
|:---:|:---:|:---:|
| <img src="docs/screens/pick-star.png" width="250" alt="Picking Vega as the alignment star"> | <img src="docs/screens/guidance.png" width="250" alt="Guidance panel: move up and left to M57"> | <img src="docs/screens/on-target.png" width="250" alt="On target: M57"> |
| The phone's sensors know roughly where it points. Tapping the star the telescope is really on fixes the rest. | Up/down and left/right, in degrees, until both numbers are near zero. | The Ring Nebula is in the eyepiece. |

## What's inside

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

## Meet Hop

<img align="right" src="docs/art/hop.gif" width="192" alt="Hop the frog blinking on a moon rock">

**Hop** is AstroFixxer's mascot, a star-hopper by trade.

Look closely at the forehead: that's a **red headlamp**. Real astronomers read their charts by red light because it
doesn't undo the half-hour your eyes need to adapt to the dark. That's also why the app has a night mode.

In the banner, Hop does what the app does: lines up on a bright guide star (ringed in pink), follows the dotted guide
line from star to star, and ends up under a faint galaxy while the reticle locks on.

<br clear="right">

## Proof it works

Every push runs:

| Check | What it proves |
|---|---|
| `./gradlew test` (app) | Astronomy maths matches the original web app to 1e-9, SGP4 matches Vallado's published test case, and 2026's eclipses, equinoxes and sunrises land within minutes of published times. |
| `tools/desktop-check` journeys | The real Compose screens, driven by simulated taps: tutorial, find, align, guide, cancel, Back button, time travel, events, night mode, Hindi, lists, manual location. |
| `tools/desktop-check` audit | Every screen in English and Hindi, day and night, on 360 dp and 411 dp phones: controls at least 48 dp, no clipped or overlapping text, and colour contrast of at least 4.5:1 by day (3:1 for night mode's dim red). |

The screenshots in this README are produced by those tests.

## Build

```sh
./gradlew test assembleDebug                 # JDK 17 and Android SDK 35; APK in app/build/outputs/apk/debug
(cd tools/desktop-check && ./gradlew test)   # UI journeys and audit; screenshots in tools/desktop-check/build/screens
python3 tools/repo-art/make_art.py           # regenerates the pixel art (needs Pillow)
```

The planning docs, the Stitch UI brief and the handoff log live in `Zenithquonta/astrofixer-baby` under `docs/`.

## Releasing to Google Play

1. Make an upload keystore once: `keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Keep it and its passwords out of git.
2. Add these repository secrets: `KEYSTORE_BASE64` (from `base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then push a tag such as `v0.1.0`. The Release workflow uploads the signed `app-release.aab`.
4. Make this repository public first (or otherwise offer the source). The GPL requires it, and the in-app licence text links here.
5. The store text, data-safety answers and 512 px icon are in `store/`. The privacy policy is `PRIVACY.md`.

For a signed build on your own machine, set `ASTROFIXXER_KEYSTORE`, `ASTROFIXXER_KEYSTORE_PASSWORD`, `ASTROFIXXER_KEY_ALIAS` and `ASTROFIXXER_KEY_PASSWORD`, then run `./gradlew bundleRelease`.

## Credits and licence

GPLv3 (see `LICENSE`), as AstroHopper requires. Made for Smart India Hackathon 2025.

- Based on [AstroHopper](https://artyom-beilis.github.io/astrohopper.html) by Artyom Beilis (GPLv3), by way of the AstroFixxer web app.
- Deep-sky catalogue, names, meteor showers and comet orbits come from Stellarium (GPL-2.0-or-later). The sky cultures come from Stellarium (CC BY-SA 4.0), and the constellation artwork is under the Free Art License. Star positions come from the HYG database (CC BY-SA).
- The planet series (VSOP87, via vsop87-multilang) and the position reduction (CPReduce) are by Greg Miller and are in the public domain (`app/src/main/java/org/astrofixxer/astro/vsop87/`).
- The golden test values in `app/src/test/resources/golden.json` were generated from the web app's own code.
- Hop and the pixel art are original, drawn in code in `tools/repo-art/`.
