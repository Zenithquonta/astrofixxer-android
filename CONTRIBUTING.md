# Contributing to AstroFixxer

Thanks for helping. Anyone can build, change and share AstroFixxer. It is GPLv3 and based on
[AstroHopper / skyhopper](https://github.com/artyom-beilis/skyhopper) by Artyom Beilis.

## Build and run

See **Build it yourself** in the README. You need no keys or accounts: `./gradlew installPreview` puts the app on a connected phone.

## Before you open a pull request

```sh
./gradlew test                                # astronomy, catalogue, lists and parser tests
(cd tools/desktop-check && ./gradlew test)    # UI journeys, layout/accessibility audit and stress tests
```

Both run in CI on every push and pull request. If you change a screen, look at the screenshots the UI check saves
(`tools/desktop-check/build/screens`, or the `ui-screens` artifact in the Actions run).

## House rules

- **Offline first.** Everything except the daily satellite-orbit refresh must work without internet. Sky data is built in by
  `tools/stellarium_import` in the planning repository and ships in `app/src/main/assets`.
- **`ui/` and `astro/` stay free of Android imports**, so the desktop check can run the real screens and maths.
- **Every text a user sees goes through `t("…")`**, with a Hindi entry in `ui/I18n.kt`. Keep controls at least 48 dp; the audit checks this.
- **Keep it simple.** Prefer the platform and the standard library over new dependencies, and explain any shortcut in a
  `ponytail:` comment.
- Match the surrounding code's style and comment density.

## Your own version

You can publish your own fork. The GPL asks that it stays GPLv3, that you offer its source, and that you keep the credits
(AstroHopper/skyhopper by Artyom Beilis, Stellarium, the HYG database, VSOP87 by Greg Miller). Change the application ID in
`app/build.gradle.kts` if you publish to an app store, so your app doesn't clash with this one.
