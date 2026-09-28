# AstroFixxer for Android

Offline star-hopping guide for manual telescopes and a Stellarium-style planetarium, in Kotlin + Jetpack Compose.
Port of the AstroFixxer web app (Smart India Hackathon 2025), itself a fork of AstroHopper by Artyom Beilis.

- Build: `./gradlew test assembleDebug` (JDK 17, Android SDK 35). CI builds a debug APK on every push.
- Plan, UI brief and the handoff log live in `Zenithquonta/astrofixer-baby` under `docs/`.

## Releasing to Google Play

1. Make an upload keystore once: `keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Keep it and its passwords out of git.
2. Add repository secrets: `KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Bump `versionCode`/`versionName` in `app/build.gradle.kts`, then push a tag such as `v0.1.0`. The Release workflow uploads the signed `app-release.aab`.
4. Make this repository public first (or otherwise offer the source): the GPL requires it, and the in-app licence text links to it.
5. Store text, data-safety answers and the 512 px icon are in `store/`. The privacy policy is `PRIVACY.md`.

Local signed build: set `ASTROFIXXER_KEYSTORE`, `ASTROFIXXER_KEYSTORE_PASSWORD`, `ASTROFIXXER_KEY_ALIAS` and `ASTROFIXXER_KEY_PASSWORD`, then run `./gradlew bundleRelease`.

## Credits and licence

GPLv3 (see `LICENSE`), as required by AstroHopper.

- Planet series: VSOP87 via vsop87-multilang, and the position reduction (CPReduce), by Greg Miller, public domain (`app/src/main/java/org/astrofixxer/astro/vsop87/`).
- Golden test values in `app/src/test/resources/golden.json` are generated from the web app's own code.
