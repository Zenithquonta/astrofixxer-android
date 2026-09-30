# Notices and credits

AstroFixxer's code is free software under the GNU GPL version 3 (`LICENSE`). It contains, or is built from, the works below. Each keeps its own licence, and the GPL does not relicense it. This file gives the credits those licences ask for. The short version shown in the app is under **Licences and source**. See also `POLICY.md`.

## Code

**AstroHopper** (formerly SkyHopper), copyright 2022 Artyom Beilis, GPLv3. Source: https://github.com/artyom-beilis/skyhopper. AstroFixxer started as a fork of it, through the **AstroFixxer web app** (https://github.com/AadidevRaizada/AstroFixxer, commit `6af76ce`, copied into `web/`). The pointing, alignment and position maths follow AstroHopper's design.

Changes: the app was rewritten in Kotlin with Jetpack Compose for Android, and given offline Stellarium-based data, events, a telescope setup wizard, guided alignment and plate solving. The web app in `web/` has bug fixes (see `docs/IMPLEMENTATION_PLAN.md`, section 1).

**VSOP87 planet series and CPReduce** (`app/src/main/java/org/astrofixxer/astro/vsop87/`, and the Kotlin port of CPReduce): Greg Miller, https://github.com/gmiller123456/vsop87-multilang. Public domain.

**SGP4** (`astro/Sgp4.kt`): a port of the near-Earth model from Vallado, Crawford, Hujsak and Kelso, "Revisiting Spacetrack Report #3" (2006).

**Service worker** (`web/sw.js`): adapted from Google's AirHorner, copyright 2015 Google Inc., Apache License 2.0 (header kept in the file).

**Libraries** (Kotlin standard library, kotlinx.coroutines, AndroidX, Jetpack Compose and Material 3): Google and JetBrains, Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0. Test-only libraries are not shipped in the app.

## Data bundled in the app

Built by `tools/stellarium_import/` from Stellarium at the commit named in `fetch_stellarium.sh`, and from the HYG database. Data files are separate from the code and keep the licences below. Where a licence asks for a note of changes: the data was converted to compact file formats and filtered, and the plate-solving star list has its positions moved to J2000.

| Data | Author and source | Licence |
|---|---|---|
| Deep-sky catalogue and names, meteor showers, comet and asteroid orbits | Stellarium, https://stellarium.org, https://github.com/Stellarium/stellarium. Stellarium's own credits (`CREDITS.md`) list the catalogues behind the deep-sky data, such as SIMBAD, NED and VizieR. | GPL-2.0-or-later |
| Sky cultures (modern and Indian): star names, constellation lines and boundaries, descriptions | Stellarium. The Indian sky culture is by Tanmoy Saha and contributors from the sanskrit-coders community. | CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/ |
| Constellation illustrations, modern | Stellarium | Free Art License, https://artlibre.org/licence/lal/en/ |
| Constellation illustrations, Indian | Stellarium | CC BY-SA 4.0, as stated for the Indian sky culture (check the source before changing this line) |
| Deep star list for plate solving (about 580,000 stars to V 10.5) | Stellarium's `stars/hip_gaia3` catalogues, made from ESA Gaia DR3 and Hipparcos | CC BY-SA 3.0 IGO (Gaia data) |
| Star positions and colours | HYG database v3, astronexus, https://codeberg.org/astronexus/hyg. It combines Hipparcos, the Yale Bright Star Catalog and the Gliese catalogue. | CC BY-SA (version as stated by the project) |

Acknowledgement for Gaia data: This work has made use of data from the European Space Agency (ESA) mission Gaia (https://www.cosmos.esa.int/gaia), processed by the Gaia Data Processing and Analysis Consortium (DPAC, https://www.cosmos.esa.int/web/gaia/dpac/consortium). Hipparcos: ESA, 1997, The Hipparcos and Tycho Catalogues, ESA SP-1200.

## Data fetched at run time

**Space-station orbits** (ISS, Tiangong) come from CelesTrak (https://celestrak.org) when you are online. The app keeps a copy on your phone and does not redistribute it.

## The `web/` app

`web/COPYING.md` (from AstroHopper) lists its own credits: `jsdb.js` derived from the Western Constellations Atlas of Space by Eleanor Lutz (GPL) and OpenNGC by Mattia Verga (CC BY-SA), planet calculations by Greg Miller (public domain), and `images/qs_*.png`, which are copyright Maxim Tonkikh and are not covered by the GPL.

## Original art

Hop the frog and the pixel art are original works drawn in code in `tools/repo-art/`.
