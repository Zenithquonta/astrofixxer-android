# AstroFixxer policy

Effective date: 1 October 2026.

This file explains, in plain words, the terms that go with AstroFixxer besides its licence: warranty, safety, data, contributions and names. It is not legal advice.

In this policy, "the maintainer" means the maintainer of github.com/Zenithquonta/astrofixxer-android, and "AstroFixxer" means the app, its source code, its data files, the `web/` app and the release files in that repository.

## In short

- The code is under the GPLv3. This policy restricts nothing the GPL allows.
- **No warranty and no liability, to the extent the law allows.**
- **Never point a telescope at the Sun without a certified solar filter. The app can be wrong.**
- Nothing you do in the app is sent to the maintainer. The few network requests are listed in section 6.
- Contributions need a sign-off (DCO). You keep your copyright.

## 1. Scope and the GPL

- AstroFixxer's code is free software under the **GNU General Public License version 3** (`LICENSE`). Your rights to use, study, change and share the code come from that licence.
- This policy is not a licence. It adds no term to the GPL and puts no restriction on what the GPL lets you do. If anything here seems to conflict with the GPL, the GPL wins.
- The code is based on AstroHopper by Artyom Beilis (GPLv3). The maintainer does not own that copyright and cannot grant or change rights in it.
- Bundled data sets keep their own licences. They are listed in section 7 and in `NOTICE.md`. The GPL does not relicense them.

## 2. No warranty

The GPL already says this in its section 15. In plain words:

**AstroFixxer is provided "as is", without warranty of any kind, either express or implied. That includes any warranty of merchantability, fitness for a particular purpose, accuracy, availability, or that it is free of errors.**

This applies to the app, the source code, the data sets, the web app, the documentation and every release file, and to what the maintainer says about them in issues, discussions and pull requests. It applies to the extent the law allows. Where a law gives you a right that cannot be waived, that right stays.

## 3. Limitation of liability

The GPL already says this in its section 16. In plain words:

**To the extent the law allows, the maintainer and the contributors are not liable for any loss or damage from using or failing to use AstroFixxer. That includes damage to a telescope, phone or other equipment, a lost observing session, lost data, and any indirect or consequential loss.**

**Nothing in this policy limits liability that the law does not let anyone limit, such as liability for fraud or, in some countries, for death or personal injury caused by negligence.**

## 4. Eye safety

**Never point a telescope, finder scope, binoculars or camera lens at the Sun unless a certified solar filter is fixed over the front of the instrument. Looking at the Sun through unfiltered optics can cause permanent blindness within a moment, and it does not hurt while it happens.**

- AstroFixxer is not a solar safety tool. Its positions, and the times and paths it shows for events, can be wrong. Do not trust it to tell you where the Sun is, or when it is safe to look.
- The app refuses the Sun as an alignment star (`ui/SkyState.kt`, `alignBlocker`) and warns about solar filters in its event texts. That is a help, not a guarantee. It does not check what your telescope is really pointing at.
- Do not use the phone as a finder for the Sun. Use a solar filter made for your telescope, follow its maker's instructions, and check it for holes before every use.
- Take particular care with children and with a telescope left pointing at the sky. A telescope that was aimed at the night sky can end up aimed at the Sun by morning.

## 5. Accuracy and safe use

- The phone's compass and gyroscope drift, and a metal telescope tube or a magnet nearby can throw the compass off. The app asks you to align on a star and to re-align for each target. The arrows it shows are a guide, not a measurement.
- Positions, event times and orbits are computed on the phone with simplified models from bundled data. Satellite orbits can be old when you are offline (the app shows their age). The catalogues can contain errors.
- Do not use AstroFixxer for navigation, or for anything where a wrong answer could hurt someone. It is for looking at the sky.
- Check event times and paths with an authoritative source before you travel or plan around them.
- Mind your surroundings. Do not walk, drive or climb while looking at the phone, and take care in the dark.

## 6. Data handling

The full text is in `PRIVACY.md`, which is the privacy policy. In short:

- There are no accounts, ads, analytics, payments or servers of the maintainer's own. Nothing you do in the app is sent to the maintainer.
- **Location.** The app reads the phone's last known approximate location (Android's coarse location permission) to draw your sky. It does not track you. A location you type in yourself is saved on the phone.
- **Microphone.** Used only after you tap Ask. Your phone's speech-recognition service turns speech into text. The app asks for offline recognition, but that is a request, and the service may use the internet under its own provider's privacy policy (usually Google's). The app receives only the text. It does not record or keep audio. Spoken answers come from your phone's text-to-speech engine.
- **Camera.** Versions with camera plate solving ask for the camera permission only when you open the camera. The picture is turned into brightness values in memory to find your position among the stars. It is not saved and not uploaded. A picture you choose from the gallery is handled the same way.
- **Web app.** The `web/` app runs in your browser. It keeps its settings in the browser's local storage, uses the browser's location and sensor features, has no analytics, and its only request to another site is the optional Wikipedia page that opens when you tap the W button. Whoever hosts a copy of it (for example a Vercel project) sees ordinary web-server logs.
- **Internet.** The app fetches the ISS and Tiangong orbit file from celestrak.org, at most once a day, with no personal data in the request. In the GitHub download only, the update button (used only when you tap it) asks api.github.com for the newest release and downloads its files from github.com. Those sites see your IP address, as any website does. Everything else works offline.
- **Stored on your phone.** Your lists and objects, your settings and telescope setup, the alignment, a typed location, the camera offset (versions with camera plate solving), the last orbit file and, in the GitHub download only, an update file while an update is downloaded. Clearing the app's data or uninstalling deletes them. Android backup may copy them if you have backup turned on.
- The maintainer sees only what you choose to post in GitHub issues, and the names and emails in git commits. Do not put private data in a public issue.

## 7. Third-party services and data

Services the app can contact, and what they receive:

| Service | Used for | What it sees |
|---|---|---|
| CelesTrak (celestrak.org) | Orbit file for the ISS and Tiangong | Your IP address and the request |
| GitHub (api.github.com, github.com) | Update check and download, GitHub download only, on your tap | Your IP address, the app's name and version |
| Your phone's speech-recognition and text-to-speech services (Google's, or the one you chose) | AstroGuide voice | Audio, under that provider's privacy policy |
| Android's DownloadManager and package installer | Fetching and installing an update, GitHub download only | Handled by Android; the installer always asks you |

These services have their own terms. The maintainer does not control them and is not responsible for their availability or content.

Data and code bundled with AstroFixxer, and their licences (credits and links are in `NOTICE.md`):

| What | Source | Licence |
|---|---|---|
| App code and pointing maths | AstroHopper, Artyom Beilis | GPLv3 |
| Deep-sky catalogue and names, meteor showers, comet and asteroid orbits | Stellarium | GPL-2.0-or-later |
| Sky cultures (modern, Indian): names and data | Stellarium | CC BY-SA 4.0 |
| Constellation illustrations (modern) | Stellarium | Free Art License |
| Constellation illustrations (Indian) | Stellarium | CC BY-SA 4.0, as stated for that sky culture |
| Deep star list for plate solving | Stellarium's Gaia-based catalogues (ESA Gaia DR3, and Hipparcos) | CC BY-SA 3.0 IGO (Gaia) |
| Star positions and colours | HYG database v3 (astronexus) | CC BY-SA |
| Planet series (VSOP87) and position reduction (CPReduce) | Greg Miller | Public domain |
| Kotlin, AndroidX and Jetpack Compose libraries | Google, JetBrains | Apache-2.0 |

If you share the app or its data, keep these credits and licences.

## 8. Contributions

- Send changes as pull requests on GitHub. `CONTRIBUTING.md` explains how to build and test.
- **Sign off every commit** (`git commit -s`). The `Signed-off-by:` line means you agree to the Developer Certificate of Origin 1.1 (developercertificate.org): you wrote the change or have the right to submit it under the project's licence. The line puts your name and email in the public git history.
- **Inbound equals outbound.** A contribution to the code is licensed under GPLv3, the same licence as the project. Data you contribute keeps its own licence, and you must say what it is.
- **You keep your copyright.** There is no copyright assignment and no contributor licence agreement. The maintainer gets no right to relicense your work under other terms.
- Do not submit code or data you may not license this way, or that comes with terms the GPL does not allow.

## 9. Security reporting

Report a vulnerability privately, through a GitHub private security advisory (the repository's Security tab, "Report a vulnerability"). Do not open a public issue for it. What the project does about keys, secrets and updates is in `SECURITY.md`. Replies are made on a best-effort basis, and no response time is promised.

## 10. Names and trademarks

- "AstroFixxer" is the name of the app, taken from the AstroFixxer web app that this project builds on. The name of the AstroHopper (SkyHopper) app and project belongs to its author, Artyom Beilis.
- Names of other products and services, such as Stellarium, Android, Google Play, GitHub and CelesTrak, belong to their owners. They are used only to say what they are, or what AstroFixxer works with. AstroFixxer is not affiliated with, sponsored by or endorsed by any of them.
- The GPL lets you fork and change AstroFixxer, and this section does not change that. It is only about the name. If you publish a changed version, please say that it is changed and do not present it as the maintainer's official release. If you publish to an app store, please use your own application ID (see `CONTRIBUTING.md`).

## 11. Governing law

- This policy, and the maintainer's statements outside the licence, are governed by the laws of India.
- This does not take away any mandatory right you have under the law of the country where you live, such as consumer-protection law, or data-protection law like the EU and UK GDPR. Where such a law applies and cannot be excluded, it prevails over this clause.
- No court or city is chosen exclusively. A dispute about this policy may be brought before the competent courts of India, or wherever mandatory law gives you the right to sue.
- This clause covers this policy only. It does not change the GPL and adds no restriction to it. Each licensee's rights come from the GPL itself, and no law, court or venue in this policy applies to them.

## 12. Changes and contact

- The maintainer may change this policy. Changes are made in git, so every version stays in the file's history, and the effective date at the top is updated. A change does not reduce the rights that the GPL gives you.
- Contact: open an issue at https://github.com/Zenithquonta/astrofixxer-android/issues. For a vulnerability, use a private security advisory as described in section 9.
