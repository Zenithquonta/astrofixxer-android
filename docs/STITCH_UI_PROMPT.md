# Stitch UI Prompt: AstroFixxer (Android, Jetpack Compose), Stellarium-style

Paste everything below the line into Google Stitch. It keeps every feature the current web app (`astrofixxer.html`) has, and takes its look and interaction model from **Stellarium**: a realistic full-screen planetarium sky with slide-out toolbars.

---

## Product

Design a mobile app called **AstroFixxer** for Android phones (Material 3, built with Jetpack Compose). It is a planetarium and a star-hopping assistant for manual telescopes. The user straps the phone flat onto the telescope tube, with the top edge of the phone pointing where the telescope points. The phone's gyroscope, gravity sensor and compass track where the telescope is aimed. The user "aligns" once on a bright star they can see, then picks a faint target, and the app shows which way and how far to move the telescope until they reach it.

Users are students, school astronomy clubs and first-time observers in India. They work **outdoors at night, often far from mobile coverage, in the dark, with cold hands, holding a telescope with one hand**.

## Look and feel: like Stellarium

Design it to feel like the Stellarium planetarium (desktop and Stellarium Mobile), adapted for a phone mounted on a telescope. Use the interaction model and visual style only. **Do not copy Stellarium's logo, name or icons.**

- **The sky is the whole screen.** A realistic planetarium view, edge to edge, with no permanent chrome except a thin info overlay. Controls slide in when needed and hide again after a few seconds.
- **Realistic sky rendering:**
  - Stars drawn as soft glowing points, sized and coloured by brightness and colour (blue-white to orange-red), with a subtle twinkle.
  - A Milky Way band.
  - Atmosphere: sky colour follows the real sun position (day blue → twilight gradient → night black). It can be toggled off.
  - **Ground / landscape**: a dark horizon silhouette (for example a low hill line with trees) covering everything below the horizon, with **cardinal points N, NE, E, SE, S, SW, W, NW** on the horizon line in red.
  - Constellation stick figures, names, faint boundary lines, and optional **constellation artwork**: faint line illustrations over the stars.
  - Grids: an altitude/azimuth grid, an equatorial (RA/Dec) grid, and the horizon, meridian and ecliptic lines, each toggled separately.
  - Planets with correct phases and relative brightness, and the Moon with its phase.
  - Deep-sky objects shown with standard symbols until zoomed in:
    - Galaxy: red ellipse
    - Open cluster: yellow dashed circle
    - Globular cluster: circle with a cross
    - Nebula: green square or cloud
    - Planetary nebula: circle with rays
    - User-added object: diamond
- **Info overlay (top-left, text only, no card background)**, exactly like Stellarium's object info. When an object is selected, show a stack of small text lines:
  - Name (large), plus other catalogue IDs (for example "M42 · NGC 1976 · Great Orion Nebula")
  - Type, magnitude, angular size
  - RA/Dec (J2000 and of date), Alt/Az, hour angle
  - Rise / transit / set times, and the constellation it's in
  - Distance where known
- **Selected object marker**: a thin animated reticle around the object (four corner brackets that pulse slowly).
- **Bottom-left slide-out toolbars** (Stellarium's two bars), shown by tapping the screen edge or a small handle:
  - **Vertical left bar (windows):** Location · Date & Time · Sky & Viewing Options · Search · Settings · Help · Events (new).
  - **Horizontal bottom bar (quick toggles):**
    - Constellation lines, constellation names, constellation art, constellation boundaries
    - Alt/Az grid, equatorial grid
    - Ground, cardinal points, atmosphere
    - Deep-sky objects, planet labels
    - Night mode
    - Center on selected, sensor/compass tracking
    - Time controls: fast rewind, rewind, real time (now), forward, fast forward
    - Full screen
- **Time travel.** Time controls change the displayed time, and the sky animates. Show the current display date/time and location small in the bottom-right, like Stellarium. Tapping it opens Date & Time. A "Now" button returns to real time.
- **Zoom**: pinch zoom from a 180° all-sky view down to a 1° eyepiece field. Faint stars and deep-sky objects fade in as you zoom (magnitude-limited by zoom level, like Stellarium). Show the current field of view in degrees in the bottom-right.
- **Colours**: near-black night sky, light-grey-blue text in the overlays, accent cyan (#00BFFF, the current brand colour) for AstroFixxer's own telescope controls, so they stand apart from the planetarium chrome.
- **Typography**: a clean condensed sans for overlays and readouts (Stellarium-like small text), with tabular numerals.

## Hard constraints (these override the Stellarium look)

1. **Fully offline.** Every screen works with no internet: the star, deep-sky, constellation and event data ship inside the app. Only two things may use the network, and both must show a clear offline state:
   - Wikipedia summaries (optional)
   - Refreshing satellite (ISS) orbit data
   
   Never show a loading spinner for sky data.
2. **Dark-sky safe Night mode.** One tap turns every pixel deep red (#FF0000 on black, brightness capped), including the sky rendering, overlays, dialogs, icons and toasts. No white or blue anywhere. Stellarium has the same red "night mode" button in its bottom bar; keep it there and also add it to the telescope controls.
3. **Big touch targets for gloves.** 56dp for primary telescope controls and 48dp minimum everywhere else. The Stellarium toolbars must use these sizes on the phone, with icon + short label in the expanded state.
4. **Glanceable telescope guidance.** The ΔAlt/ΔAz numbers must be readable at arm's length.
5. **Mounted phone.** It may be sideways or upside down relative to the user. Support portrait and landscape.
6. **Multilingual.** English, Hindi, Ukrainian, Hungarian, Russian and Hebrew (right-to-left). Allow for text about 40% longer than English.

## Data the UI must handle (all bundled offline)

- About **94,000 deep-sky objects** from Stellarium's catalogue, with cross-IDs (M, NGC, IC, Caldwell, Barnard, Sharpless, Collinder, Melotte, Trumpler, Abell, Arp, PGC, UGC and more) and common names.
- About **9,000 named and bright stars**, with Western names and **Indian (Vedic) names** in Devanagari and transliteration (for example Sirius = लुब्धकः, Lubdhaka).
- Two **sky cultures**: Western (88 IAU constellations) and **Indian Vedic** (49 constellations, including the 27 nakshatras, in Devanagari). The user can switch between them.
- **Meteor showers** (43 showers): peak date, active period, ZHR, radiant, parent comet.
- Planets, Moon and Sun, computed on the phone.
- Planet events computed on the phone: conjunctions, oppositions, greatest elongations, Moon phases.
- ISS and bright satellite passes from bundled orbit data. Show how old the data is (for example "Orbit data 9 days old · refresh when online").

## Screens

### 1. Sky View (main screen)

The Stellarium-style full-screen sky described above, plus AstroFixxer's telescope layer:

- **Alignment status chip** (top-right): "Not aligned" (warning), "Tap the alignment star" (pulsing), "Aligned ✓" (good), and the time since alignment (for example "Aligned 6 min ago"). The phone's gyroscope drifts, so show a subtle hint after about 10 minutes.
- **Telescope pointer**: a cyan crosshair showing where the telescope points. Around it, a circle for the current **eyepiece field of view**, set in settings from focal lengths or entered in degrees.
- **Align button**: large, cyan, bottom-right thumb zone. Tap Align, then tap the star the telescope is pointed at.
- **Pointing mode toggle**:
  - Compass: absolute tracking.
  - Manual: no compass; drag the sky left/right until the alignment star is under the crosshair.
  - Free look: the sky doesn't follow sensors; normal planetarium panning.
- **Target guidance panel** (bottom sheet, appears when a target is selected and the app is aligned):
  - Target name and type.
  - **ΔAlt** (move up/down) and **ΔAz** (move left/right) in large numerals, with arrows (↑↓←→) and units in degrees and arcminutes (for example "↑ 2°14′", "← 0°38′").
  - A bullseye that fills as the telescope approaches the target and switches to "On target" when the target is inside the eyepiece circle, with an optional haptic pulse.
  - A line on the sky from the crosshair to the target.
- **Watch list navigator**: ‹ previous · object name · next ›, for stepping through tonight's observing list.
- Gestures:
  - Tap an object to select it.
  - Long-press an object for a quick menu: Set as target · Align on this · Add to list · Info.
  - Pinch to zoom.
  - Drag to pan (Free look / Manual modes).

### 2. Search (Stellarium-style search window)

- One search field with live results as you type. It matches catalogue IDs in any spacing ("M 42", "NGC1976", "Cr 399"), common names ("Orion Nebula", "Beehive"), star names and Indian names ("Lubdhaka"), planets, and constellations.
- Each result shows name, type icon, magnitude, and whether it's above the horizon now ("Up · 43° alt" / "Rises 21:40").
- Tabs like Stellarium's search window: **Object** · **Position** (enter RA/Dec) · **Lists** (Messier, Caldwell, Bright stars, Double stars, Nakshatras, My objects).
- Selecting a result centres the sky on it. A second tap on "Set target" starts telescope guidance.

### 3. Events ("Tonight" and upcoming, new)

A panel from the left bar, and a small badge on the Sky View when something is happening tonight.

- **Tonight card**:
  - Sunset, astronomical twilight and moonrise/moonset.
  - Moon phase and illumination.
  - Which planets are up, and when.
  - The darkest window for deep-sky observing.
- **Meteor showers**:
  - List with name, peak date (countdown "in 5 days"), active period bar, ZHR (or range, for example "40–85"), and radiant constellation.
  - Tap to show the radiant on the sky and when it's highest.
- **Planet events**: conjunctions (for example "Venus 1.2° from Jupiter"), oppositions, greatest elongations, and Moon–planet close approaches, with date/time.
- **ISS and satellites**:
  - Next visible passes: start time, max altitude, direction (for example "SW → NE"), brightness.
  - The data-age indicator and a "Refresh when online" button.
- Each event has "Show in sky": it jumps the sky view to that date and time, with time travel.
- Filters: This week / This month / All; types.

### 4. Sky & Viewing Options (Stellarium-style tabbed window)

- **Sky tab**:
  - Star magnitude limit and brightness
  - Milky Way brightness
  - Atmosphere on/off
  - Light pollution (Bortle 1–9 slider)
  - Twinkle on/off
- **Deep-sky tab**:
  - Object types to show: galaxies, open clusters, globular clusters, nebulae, planetary nebulae, dark nebulae, clusters of galaxies, user objects
  - DSO magnitude limit
  - Catalogues to show: Messier, NGC, IC, Caldwell, Barnard, Sharpless and so on
  - Labels on/off
- **Markings tab**:
  - Grids (Alt/Az, equatorial)
  - Horizon, meridian and ecliptic lines
  - Cardinal points
  - Constellation lines, names, art and boundaries
- **Sky culture tab**: Western / Indian (Vedic), with a short description and a preview thumbnail.
- **Landscape tab**: pick a horizon silhouette (Plain, Hills, Trees, Observatory dome) or none.

### 5. Location and Date & Time

- **Location**:
  - Current GPS latitude/longitude with a refresh button.
  - Manual override (latitude −90 to 90, longitude −180 to 180).
  - An **offline city list** of Indian and major world cities.
  - A small world map with a pin (bundled offline map, low-detail).
- **Date & Time**: a date and time picker, a "Now" button, and a time-rate display. This mirrors Stellarium's Date/Time window.

### 6. Object Info (expanded)

Opens from the info overlay. It's full catalogue data from the bundled data:
- All IDs and names, type and morphology.
- Magnitude and size, and distance.
- Coordinates.
- Rise/transit/set times, and an altitude-over-tonight graph.
- An eyepiece preview circle.
- Optional "Wikipedia summary" section. It loads only when online and is cached for offline use afterwards.

### 7. Telescope settings (AstroFixxer-specific)

- Telescope focal length and eyepiece focal lengths/field stops, which give the eyepiece circle size.
- Align on deep-sky objects (allowed/not).
- Keep screen on.
- Haptics when on target.
- Mount type: Alt-Az / Equatorial. This changes the guidance arrows to RA/Dec for equatorial users.

### 8. Settings (general)

- **Night mode**, with a brightness cap slider.
- Small UI / compact toolbars.
- Font size.
- Language.
- Show tutorial on startup.
- **User objects**: a paste-CSV editor with inline per-line validation. RA accepts "05:35:17", "5h35m17s", "5 35 17" or decimal; Dec accepts "-05:23:28", "-5°23′28″" or decimal. Also a simple add form.
- **Observing lists** (watch lists): create, rename, reorder, Save / Discard.
- Wikipedia info: Always / Day only / Never.
- **Data**: shows the bundled data version ("Stellarium catalogue 2026-09, 93,997 deep-sky objects") and the age of the satellite orbit data.
- Reset all, with an in-app confirmation step.
- About & licenses: GPLv3 source link, Stellarium data (GPL-2.0-or-later / CC BY-SA 4.0), HYG star database, OpenNGC, VSOP87.

### 9. First run: permissions and quick start

- **Permissions**:
  - Motion sensors and location, and why they're needed.
  - Error states for no gyroscope (go to Manual mode) and no location (pick a city offline).
- **Quick Start**: a 4-step carousel with illustrations, page dots and Back/Next:
  1. "Attach the phone flat on the telescope tube, top edge pointing where the telescope points."
  2. "Point the telescope at a bright star or planet **near** what you want to find. To find M41 or M47, use Sirius. This is your *alignment star*. Tap **Align**, then tap that star on the screen."
  3. "Can't see the alignment star on screen? The compass may be off. Switch to Manual mode and drag the sky until the star is under the crosshair, then Align."
  4. "Tap your target and follow the arrows until they reach zero. Re-align for each new target."
  - "Don't show again" checkbox.

### 10. AstroGuide (voice assistant, Beta)

- Push-to-talk mic, a live transcript, and spoken plus written answers.
- Suggestion chips: "What's up now?", "Find Saturn", "When is the next meteor shower?", "Is the ISS visible tonight?", "What is M42?"
- Answers can carry action buttons such as "Set as target" or "Show in sky".
- **Offline behaviour**: basic commands ("find X", "what is X", "align", "what's up tonight") work offline from the bundled data. Free-form questions show "Needs internet".
- Follows the app language, with Hindi and English first. Obeys Night mode.

### 11. Help

- A searchable manual with a table of contents: Operation, Installing, Troubleshooting, Controls, Watch lists, User objects, Equatorial mount users, Known issues. Bundled offline.

## States to show

- Normal and **Night (red)** variants of screens 1, 2, 3 and 4.
- Day, twilight and night sky backgrounds on the Sky View.
- Portrait and landscape of the Sky View.
- Telescope states: not aligned / tap alignment star / aligned / on target.
- No compass (Manual mode), no gyroscope, no location, offline.
- Western and Indian sky culture on the same part of the sky.
- Hebrew (RTL) sample of Search and Settings.

## Deliverables

High-fidelity mockups for screens 1–11 (Normal and Night), the two slide-out toolbars in collapsed and expanded states, a component sheet (toolbar buttons, toggles, steppers, sliders, chips, bottom sheets, guidance panel, deep-sky symbols, info overlay text styles, the selection reticle), and a colour and type token sheet that maps directly to a Compose `MaterialTheme`, with a separate Night (red) scheme.
