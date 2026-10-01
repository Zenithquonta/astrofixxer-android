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
  `tools/stellarium_import` (output in `data/`, copied to `app/src/main/assets`).
- **`ui/` and `astro/` stay free of Android imports**, so the desktop check can run the real screens and maths.
- **Every text a user sees goes through `t("…")`**, with a Hindi entry in `ui/I18n.kt`. Keep controls at least 48 dp; the audit checks this.
- **Keep it simple.** Prefer the platform and the standard library over new dependencies, and explain any shortcut in a
  `ponytail:` comment.
- Match the surrounding code's style and comment density.

## Contributions and licensing

This follows section 8 of `POLICY.md`.

- **Sign off every commit** with `git commit -s`. This adds a `Signed-off-by: Your Name <you@example.com>` line. It means you agree to the [Developer Certificate of Origin 1.1](https://developercertificate.org): you wrote the change, or you have the right to submit it under the project's licence. The line puts your name and email in the public git history, and that history cannot be rewritten later without breaking everyone's copy. If you forgot, `git commit --amend -s` fixes the last commit.
- **Inbound equals outbound.** Code you contribute is licensed under the GPLv3, the same licence as the project.
- **Data keeps its own licence.** If you add data (a catalogue, a picture, a list), say where it comes from and what licence it has, in the pull request and in `NOTICE.md`. It must be a licence the GPL can be combined with.
- **You keep your copyright.** There is no contributor licence agreement and no copyright assignment. The maintainer gets no right to relicense your work under other terms.
- **Do not send code or data you may not license this way**, or anything that comes with terms the GPL does not allow.

## Your own version

You can publish your own fork. The GPL asks that it stays GPLv3, that you offer its source, and that you keep the credits
(AstroHopper/skyhopper by Artyom Beilis, Stellarium, the HYG database, VSOP87 by Greg Miller). Change the application ID in
`app/build.gradle.kts` if you publish to an app store, so your app doesn't clash with this one.
