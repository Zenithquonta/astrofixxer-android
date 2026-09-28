# Field test protocol (Phase 7)

The goal is to find out, before release, whether a person can actually get a target into the eyepiece with AstroFixxer, and how long it takes.

## Setup

- **Phones (3):** one with a gyroscope and compass, one with no compass (Manual mode), and one older or low-end phone (Android 8-10).
- **Telescopes (2):** a Dobsonian and a small alt-az refractor or reflector, with a low-power eyepiece (about 1°).
- A dark or suburban site, at least 1 hour after sunset. Note the Bortle class.
- Install the CI build (`app-debug.apk` from the Android workflow). On first launch, allow location. Turn on night mode.

## For each target

1. Choose an alignment star 5-20° from the target (the app shows bright stars; Vega, Altair, Deneb, Arcturus and Capella work well in their seasons).
2. Centre that star in the eyepiece, tap **Align**, then tap the star on screen. Start the timer.
3. Pick the target in **Find**, and follow the arrows until both numbers are near zero.
4. Look in the eyepiece. Stop the timer when the target is in view, or at 5 minutes (a miss).

Targets: 10 Messier objects across the sky, for example M13, M57, M27, M31, M11, M8, M22, M15, M2 and M92 (Northern autumn), or M42, M45, M35, M37, M36, M38, M41, M44, M1 and M81 (winter).

## Record sheet

| Phone | Scope | Mode (compass/manual) | Align star | Target | Distance (°) | Time (s) | Found? | Offset in eyepiece | Notes |
|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | |

## Also check

- [ ] The sky matches the real sky with the phone held up (compass mode): planets and bright stars are within a few degrees.
- [ ] The events list shows tonight's Moon phase and any meteor-shower peak. An ISS pass, if one is listed, appears at the stated time and direction.
- [ ] AstroGuide: "find Jupiter", "what is M31" and "मंगल कहाँ है" in a quiet place with the phone offline.
- [ ] Airplane mode: everything except the orbit refresh still works.
- [ ] Battery drain over 1 hour of use (the screen at night brightness).
- [ ] Take the Play Store screenshots on the way (sky view, guidance, events, night mode, AstroGuide).

## Pass criteria

- At least 8 of 10 targets found within 2 minutes on the phone with a compass.
- No crashes. File each problem in the Android repo with phone model, Android version and steps.
