# AstroFixxer privacy policy

Effective date: 1 October 2026.

AstroFixxer has no accounts, no ads, no analytics, no payments and no server of its own. Nothing you do in the app is sent to the maintainer. This file is the privacy policy that `POLICY.md` (section 6) refers to.

## What the app uses, and why

| Permission | Used for | Where it goes |
|---|---|---|
| Approximate location (Android's coarse location) | Drawing the sky for your place and time, and working out rise/set times, occultations and satellite passes. | Stays on the phone. The app reads the last location Android already knows, when it starts and when you come back to it. It does not track you or ask for live updates. A location from the phone is not saved. A location you type in yourself is: see below. |
| Microphone | AstroGuide voice commands. Used only after you tap **Ask**. | Handled by your phone's speech-recognition service, which turns speech into text. The app asks for offline recognition, but that is only a request: the service may use the internet, and then its provider (usually Google) handles the audio under its own privacy policy. The app receives only the text. It does not record or keep audio. Spoken answers come from your phone's text-to-speech engine. |
| Camera (versions with camera plate solving) | "Solve with camera": a photo of the stars, to work out where the telescope points. Asked for only when the live camera view opens. | Processed on the phone. Not saved and not uploaded: see "Camera pictures" below. |
| Internet | Downloading the ISS and Tiangong orbit file from celestrak.org, at most once a day. | The request contains no personal data. CelesTrak sees your IP address, as any website does. Everything else works offline. |
| Internet, for updates (GitHub download only) | **Check for updates** (Sky & viewing → More → App updates) asks api.github.com for the newest release and downloads its small `update.json` from github.com. **Download and install** then downloads the new app from github.com. | Only when you tap those buttons: the app never checks by itself. GitHub sees your IP address, as any website does, and the app's name and version in the request. Nothing else is sent: no account, no device ID, no location. Android's own installer always asks you before anything is installed. |

The update feature exists only in the preview and debug builds, which are the ones you download from GitHub. The release build for Google Play does not contain it and never contacts GitHub.

## A location you type in

If you type in a location instead of using the phone's, the app saves it on the phone, so that it is still there next time. Choosing "Use GPS" again deletes it. It is never sent anywhere.

## Camera pictures

This applies to versions that have camera plate solving.

- The camera permission is asked for only when you open the camera or live view. If you refuse, everything else still works.
- Plate solving works out where the telescope points by matching the stars in a photo to a star list that is inside the app. It runs entirely on the phone.
- Each picture is turned into brightness values in memory and then discarded. It is not saved to the phone's storage or gallery, not shared, and not uploaded. The app has no feature that sends a picture anywhere.
- A picture you choose from the gallery is read through Android's photo picker, so the app sees only that picture. It is handled the same way.
- The camera is closed when you leave the live view or the app.

## What is stored on the phone

These are kept in the app's private storage on the phone:

- your observing lists and your own objects;
- your settings (what the sky shows, sky culture, eyepiece and telescope numbers) and your telescope setup;
- the alignment: the calibration numbers, the name of the star you aligned on, and when you did it;
- the camera offset, two numbers that say where the telescope is in the camera's picture, if you calibrated it (versions with camera plate solving);
- a location you typed in, if any;
- whether you have seen the introduction;
- the last orbit file downloaded from CelesTrak;
- in the GitHub download only, an update file while an update is being downloaded and installed. The app deletes old update files the next time it starts.

Nothing else is stored. In particular the app stores no camera pictures, no audio and no location taken from the phone.

Clearing the app's data in Android's settings, or uninstalling the app, deletes all of this.

**Android backup.** The app does not turn Android's backup off. If backup is switched on, Android may copy the app's stored data to your backup service (usually Google's) and restore it on a new phone. Android makes that copy, not AstroFixxer, and that service's own policy covers it. To stop it, turn off backup in your phone's settings.

## The web app

The `web/` app runs in your browser. It keeps your settings, lists and typed location in the browser's local storage, and uses the browser's location and sensor features. It has no analytics. Its only request to another site is the optional Wikipedia page for an object, which opens in a frame when you tap the W button. Whoever hosts a copy of the web app sees ordinary web-server logs.

## What the maintainer sees

Only what you choose to post in GitHub issues, and the names and emails in git commits. Do not put private data in a public issue.

## Changes

Changes are made in git, so every version of this file stays in its history, and the effective date above is updated.

## Contact

Open an issue at https://github.com/Zenithquonta/astrofixxer-android/issues.
