# Stitch UI Prompt: AstroFixxer (Android, Jetpack Compose)

Paste everything below the line into Google Stitch. It describes every screen and control that already exists in the web app (`astrofixxer.html`), so the redesign keeps all current functionality and fixes the UX problems we already know about.

---

## Product

Design a mobile app called **AstroFixxer** for Android phones (Material 3, built with Jetpack Compose). It is a star-hopping assistant for manual telescopes. The user straps the phone flat onto the telescope tube, with the top edge of the phone pointing where the telescope points. The phone's gyroscope, gravity sensor and compass track where the telescope is aimed. The user "aligns" once on a bright star they can see, then picks a faint target, and the app shows which way and how far to move the telescope until they reach it.

Users are students, school astronomy clubs and first-time observers in India. They work **outdoors at night, in the dark, with cold hands, often wearing gloves, holding a telescope with one hand**. The app must work fully offline.

## Hard design constraints

1. **Dark-sky safe.** Default theme is near-black (#000–#0A0A0A). There must be a **Night (red) mode** where every pixel is deep red (#FF0000 on black, maximum ~60% brightness, no blue or white anywhere, including icons, dialogs, keyboard hint text and toasts). White light ruins the observer's dark adaptation for 20+ minutes. A Night mode toggle must be reachable in one tap from the main screen.
2. **Big touch targets.** Minimum 56dp for primary controls and 48dp for everything else. The current web app uses 10mm buttons with no labels. Replace unlabeled icon buttons with icon + short label.
3. **One-handed, glanceable.** The key numbers (how far to move up/down and left/right) must be readable at arm's length. Use large tabular numerals.
4. **The phone is mounted on a telescope.** It may be upside down or sideways relative to the user. Support portrait and landscape and keep the core controls reachable in both.
5. **Offline-first.** No loading spinners for core features. Show a small offline indicator only where a feature needs the network (Wikipedia info).
6. **Multilingual.** English, Hindi (new), Ukrainian, Hungarian, Russian and Hebrew. Hebrew is right-to-left, so layouts must mirror correctly. Leave room for text that is about 40% longer than English.

## Visual direction

- Deep-space black background, cyan accent (#00BFFF, the current brand color) in normal mode; everything turns red in Night mode.
- Typography: a condensed technical face for numbers and readouts (for example Barlow Condensed or Roboto Condensed), a plain sans for body text.
- Keep chrome minimal. The sky map is the hero and should fill the screen edge to edge.
- Replace the current mix of mm-sized square buttons and hand-drawn PNG icons (compass.png, manual.png, settings.png, search.png, wiki.png) with a consistent Material Symbols icon set.

## Screens

### 1. Sky Map (main screen, full screen)

This is where the user spends 95% of their time. Currently a full-screen canvas with a row of small buttons on top.

**Sky map canvas (full bleed)** shows:
- Stars sized by brightness (magnitude), with optional star names.
- Deep-sky objects drawn with distinct symbols. Keep these standard astronomy symbols:
  - Galaxy: tilted ellipse with a dot
  - Globular cluster: dashed circle with a filled centre dot
  - Open cluster: dashed circle with a dashed inner ring
  - Nebula: S-shaped curve with a dot
  - User-added object: diamond
  - Planets: labelled dots
- Constellation stick-figure lines and constellation name labels.
- A crosshair in the centre showing where the telescope is pointing.
- When a target is selected: a direction line/arrow from the crosshair to the target, and the target highlighted in a target color, with its magnitude (for example "m=6.4"), angular size (for example "12′") and alternate names (for example "Beehive, Praesepe Cluster").
- The alignment star is highlighted in a separate "align" color.
- Gestures: tap an object to select it, pinch to zoom (can be disabled in settings), and drag left/right to rotate the view in Manual mode.

**Top bar (overlaid, translucent):**
- **Alignment status chip**: "Not Aligned" (warning), "Select Star" (waiting for a tap, pulsing), "Aligned ✓" (good). There is also a compact version for small-UI mode.
- **Search field**: "Search celestial objects…". Expands to full width on focus and shows live results as you type. Matches catalogue IDs like "M41", "NGC869", "IC2391" and common names like "Beehive", "Sirius", "Jupiter".
- **Pointing mode toggle** with three states:
  - Compass: the device has an absolute compass.
  - Manual: no compass, the user drags the map to line up the alignment star.
  - No-compass: an indicator that the compass is unavailable.
- **Settings** button.

**Bottom control bar (thumb zone):**
- **Align** (primary, most prominent button). Tapping it puts the app into "Select Star" mode; the user then taps the star the telescope is currently pointed at.
- **FOV − / value / +**: field of view in degrees (for example "30°"). Shown as a stepper.
- **Watch list navigator**: ‹ previous object · object name · next object ›. Steps through the current observing list.
- **Object info (Wiki)** button: enabled only when a target is selected.
- **AstroGuide** mic button (voice assistant, see screen 6). Currently it only shows a "Coming Soon" alert. Design it as a real entry point with a "Beta" badge.

**Target guidance panel (NEW, replaces the tiny `find_status` text line):** a bottom sheet that appears when a target is selected and the app is aligned:
- Target name, type and constellation.
- **ΔAlt** (move up/down) and **ΔAz** (move left/right) in large numerals with direction arrows (↑↓←→) and units in degrees and arcminutes (for example "↑ 2°14′", "← 0°38′").
- A progress ring or bullseye that fills as the telescope approaches the target, and turns into an "On target" state within the eyepiece field of view.
- Hint: "Re-align if the target drifts. Phone gyros drift over time."
- Optional haptic pulse when on target (show a toggle).

### 2. First-run / Sensor permission

- Explains that the app needs motion sensors and location, and why.
- "Enable device orientation" primary button (this exists today for iOS; on Android it becomes the runtime permission step).
- "No gyroscope" and "No geolocation" error states with a clear next step (Manual mode; enter latitude/longitude by hand).

### 3. Quick Start Guide (4-step onboarding carousel)

Existing content. Keep the four steps, add illustrations, and use real Next/Back buttons plus page dots:
1. "Connect the smartphone to the optical tube such that its top points to the viewing direction and it lies flat on the tube."
2. "Point the telescope to an easily identifiable star or planet **nearby** an object you want to find. For example, to find M41 or M47, point the telescope at Sirius. This is the *alignment star*." Then: "When the telescope is pointing at the alignment star, tap **Align** and tap the alignment star on the map. Once aligned, the app tracks the telescope's movement using the phone's sensors."
3. "If you can't see the alignment star on the map, there may be a compass accuracy problem. Switch to Manual mode and scroll the map left or right until you see the alignment star. Then tap Align and tap the star as usual."
4. "Once aligned, tap the object you want to find (for example M41 or M47) and follow the direction line until you reach it. It is recommended to repeat alignment for each new target." Include a link to the video tutorial and to the built-in manual.
- A "Don't show on startup" checkbox.

### 4. Settings (currently one long modal panel; redesign as grouped sections)

**Display**
- Night mode (red)
- Small UI (compact controls)
- Full screen
- Keep screen on
- Pinch zoom
- Font size − / +
- Language picker (English, Hindi, Ukrainian, Hungarian, Russian, Hebrew)

**Sky objects: show or hide each layer (all currently checkboxes)**
- Stars, with a limiting magnitude stepper (− / +)
- Star names
- Deep-sky objects (DSO), with a limiting magnitude stepper (− / +)
- Open clusters
- Globular clusters
- Nebulae
- Galaxies
- Planets
- Constellations
- User objects

**Alignment**
- Align on DSO (allow aligning on a deep-sky object, not only stars)
- Field of view stepper

**Observing lists**
- Watch list selector: ‹ list name ›
- Editable list (text area) with Save / Discard

**User objects**
- Text editor where users paste custom objects as CSV: name, RA, Dec, and optional magnitude. RA accepts "05:35:17", "5h35m17s", "5 35 17" or decimal degrees; Dec accepts "-05:23:28", "-5°23′28″" or decimal. Show inline validation errors per line (currently shown as a numbered error list).
- Save / Discard

**Location**
- Current GPS lat/lon with a refresh button
- GPS override: manual latitude (−90 to 90) and longitude (−180 to 180)

**Wikipedia info**
- "Show object info": Always / Day only / Never (this exists today as a select)

**Other**
- Show tutorial on startup
- Reset all (↻). Needs an in-app confirmation step.
- Help (opens the manual)

### 5. Object Info sheet

- Opens from the info button for the selected target.
- Shows the catalogue data we have offline: name, type, RA/Dec, magnitude, size, alternate names, and current altitude/azimuth.
- An "Open on Wikipedia" section loaded from the network, with an offline placeholder.
- Close button.

### 6. AstroGuide (voice assistant, NEW)

Currently a "Coming Soon" page. Design the real feature:
- Push-to-talk mic button, a live transcript, and a spoken plus written answer.
- Example chips: "What's up now?", "Find Saturn", "Is the ISS visible tonight?", "What is M42?", "Next conjunction?"
- Answers can include an action button such as "Set as target", which selects the object on the sky map.
- Language follows the app language, with Hindi and English first.
- Must obey Night mode.

### 7. Manual / Help

- The existing long manual (Operation, Notes, Installing, Troubleshooting, Controls, Controls in small-screen mode, Wikipedia page info, Watch list, User objects, Equatorial mount users, Known issues, Reporting bugs, Copyrights), shown as a searchable, sectioned help screen with a table of contents.

### 8. Landing / About (optional)

- App name, one-line pitch ("Turn any telescope into a guided instrument"), Smart India Hackathon 2025 credit (problem statements SIH25142, SIH25125), links to the Sky Map, AstroGuide and the 3D sky view, plus license credits: GPLv3 (AstroHopper by Artyom Beilis), OpenNGC (CC-BY-SA-4.0), VSOP87 planetary theory (public domain code by Greg Miller), and the Western Constellations Atlas of Space.

### 9. 3D Sky View (optional)

- An orbit-style 3D celestial sphere showing bright stars and planets at their real current positions. Drag to rotate, pinch to zoom, tap for info.

## States to show for each main screen

- Normal (cyan) and Night (red) variants
- Portrait and landscape
- Not aligned / selecting alignment star / aligned / on target
- No compass (manual mode), no gyroscope, no location
- Hebrew (RTL) sample of the Sky Map top bar and Settings

## Deliverables

High-fidelity mockups for screens 1–7 in both Normal and Night modes, a component sheet (buttons, steppers, chips, toggles, bottom sheets, the guidance panel, object symbols) and a color and type token sheet that can be translated directly to a Compose `MaterialTheme`.
