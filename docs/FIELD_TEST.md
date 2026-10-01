# Field test protocol (Phase 7)

The goal is to find out, before release, whether a person can actually get a target into the eyepiece with AstroFixxer, and how long it takes.

## Setup

- **Phones (3):** one with a gyroscope and compass, one with no compass (fix the azimuth by dragging while aligning), and one older or low-end phone (Android 8-10).
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

## Camera plate solve

Nothing about the camera path has been run on a device yet: real Camera2 capture, manual exposure, the reported field of view, real star photos and the permission dialogs are all untested. This section is where that gets tested. It needs the deep star list (`solver_stars.bin`) in the build.

Do it twice per telescope: once with the phone on the eyepiece (Settings → Telescope & orientation → "Attached to the eyepiece") and once with the phone beside the tube, camera along the telescope ("Camera facing along the telescope"). Use a star bright enough to identify, well above the horizon, with the mount steady.

For each try (10 tries per arrangement, over at least 3 different parts of the sky):

1. Centre a **known star** (Vega, Capella, Arcturus...) in the eyepiece. Note its name.
2. More → **Solve with camera** (or Sky → Telescope & orientation → Solve with camera). Confirm the arrangement, then take a photo with **Auto** and, if offered, with 1 s, 2 s and 4 s. Note which exposure was used.
3. Record: solved or not (and the failure message if not), the time from "Take photo" to the result (stopwatch), the matched stars, and the **error**: the angle between the solved position (the camera's, or for the beside-the-tube case the telescope's, at the +) and the known star's position (Object Info shows its RA/Dec). Anything over 0.2° at the eyepiece, or 1° beside the tube, is a bad result.
4. Press **Apply to alignment**, then look at the guidance for that star: it should read "On target" (within half an eyepiece field). Note whether it did.

Beside the tube only, first: pick a star, centre it in the eyepiece, take the photo and press **Save camera offset**. Check that the + on the live view then sits where the telescope actually points (centre a star in the eyepiece; it should be at the +). Re-do the offset if the phone is moved on the tube.

| Phone | Arrangement | Star | Exposure | Solved? | Time (s) | Matched | Error (°) | On target after Apply? | Notes |
|---|---|---|---|---|---|---|---|---|---|
| | eyepiece / beside tube | | Auto / 1 / 2 / 4 s | | | | | | |

Also check on the phone:

- [ ] The camera permission dialog appears only when the live view opens; refusing it shows the gallery button and "Open app settings"; refusing twice says it is blocked; allowing it in the settings and coming back shows the live view.
- [ ] The live view is upright and shows the whole picture; the + is where the telescope points (eyepiece: the centre).
- [ ] The camera field of view the app reports ("Camera field of view: …°") is close to the real one (photograph a ruler or a known scene). Note it per phone.
- [ ] Manual exposures are offered only when the phone supports them, and a 4 s photo of stars shows dots, not streaks, on a steady mount.
- [ ] A photo from the gallery (taken with the phone's own night mode) is solved and says the field of view was assumed; Apply is disabled for it.
- [ ] Pressing the home button with the live view open closes the camera (the green camera dot goes out), and returning opens it again.
- [ ] Airplane mode: solving works, and no network traffic is caused by it.
- [ ] Nothing is written to the gallery or storage by taking or solving a photo.

Pass criteria: the solve rate over the good-sky tries is at least 8 in 10 for each arrangement, the median time is under 30 s, and the error is within the limits above in every solved try. Every wrong "Solved" (error over the limits) is a bug in the solver, not a bad photo; file it with the photo settings.

## Pass criteria

- At least 8 of 10 targets found within 2 minutes on the phone with a compass.
- No crashes. File each problem in the Android repo with phone model, Android version and steps.
