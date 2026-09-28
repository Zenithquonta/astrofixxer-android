# AstroFixxer for Android

Offline star-hopping guide for manual telescopes and a Stellarium-style planetarium, in Kotlin + Jetpack Compose.
Port of the AstroFixxer web app (Smart India Hackathon 2025), itself a fork of AstroHopper by Artyom Beilis.

- Build: `./gradlew test assembleDebug` (JDK 17, Android SDK 35). CI builds a debug APK on every push.
- Plan, UI brief and the handoff log live in `Zenithquonta/astrofixer-baby` under `docs/`.

## Credits and licence

GPLv3 (see `LICENSE`), as required by AstroHopper.

- Planet series: VSOP87 via vsop87-multilang, and the position reduction (CPReduce), by Greg Miller, public domain (`app/src/main/java/org/astrofixxer/astro/vsop87/`).
- Golden test values in `app/src/test/resources/golden.json` are generated from the web app's own code.
