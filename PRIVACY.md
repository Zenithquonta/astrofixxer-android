# AstroFixxer privacy policy

AstroFixxer has no accounts, no ads, no analytics and no server of its own. Nothing you do in the app is sent to the developers.

## What the app uses, and why

| Permission | Used for | Where it goes |
|---|---|---|
| Approximate location | Drawing the sky for your place and time, and working out rise/set times, occultations and satellite passes. | Stays on the phone. It is read each time the app starts and is not saved. |
| Microphone | AstroGuide voice commands, only while you hold the Ask button. | Handled by your phone's speech-recognition service. The app asks for offline recognition, but if the service works online, its provider (usually Google) processes the audio under its own privacy policy. The app itself doesn't record or keep audio. |
| Internet | Downloading space-station orbits (ISS, Tiangong) from celestrak.org at most once a day. | The request contains no personal data. CelesTrak sees your IP address, as with any website. Everything else works offline. |
| Internet, for updates (GitHub download only) | **Check for updates** (Sky & viewing → More → App updates) asks api.github.com for the newest release and downloads its small `update.json` from github.com. **Download and install** then downloads the new app from github.com. | Only when you tap those buttons: the app never checks by itself. GitHub sees your IP address, as with any website, and the app's name and version in the request. Nothing else is sent: no account, no device ID, no location. The Google Play version has no update feature and never contacts GitHub. |

## What is stored on the phone

Your observing lists, your own objects and whether you have seen the introduction are kept in the app's private storage, along with the last downloaded orbit file. Uninstalling the app or clearing its data deletes them. Android's backup may include them if you have backup turned on.

## Contact

Open an issue at https://github.com/Zenithquonta/astrofixxer-android.
