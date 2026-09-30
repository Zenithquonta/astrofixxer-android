# AstroFixxer privacy policy

AstroFixxer has no accounts, no ads, no analytics and no server of its own. Nothing you do in the app is sent to the developers.

## What the app uses, and why

| Permission | Used for | Where it goes |
|---|---|---|
| Approximate location | Drawing the sky for your place and time, and working out rise/set times, occultations and satellite passes. | Stays on the phone. It is read each time the app starts and is not saved. |
| Microphone | AstroGuide voice commands, only while you hold the Ask button. | Handled by your phone's speech-recognition service. The app asks for offline recognition, but if the service works online, its provider (usually Google) processes the audio under its own privacy policy. The app itself doesn't record or keep audio. |
| Camera | "Solve with camera": taking a photo of the stars to work out where the telescope points. Asked for only when the live camera view opens. | Processed on the phone and not saved or sent anywhere: see "Camera pictures" below. |
| Internet | Downloading space-station orbits (ISS, Tiangong) from celestrak.org at most once a day. | The request contains no personal data. CelesTrak sees your IP address, as with any website. Everything else works offline. |

## Camera pictures

Plate solving works out where the telescope points by matching the stars in a photo to a star list that is inside the app. It runs entirely on the phone:

- Pictures are held in memory while they are solved and then discarded. They are not saved to the phone's storage or gallery, not shared, and not uploaded. The app has no feature that sends an image anywhere.
- The camera permission is used only for this: the live view and the photo taken for plate solving. The camera is closed when you leave the live view or the app.
- A picture chosen from the gallery is read through Android's photo picker (the app sees only the picture you choose) and treated the same way.
- If you refuse the permission, everything else still works, and you can solve a gallery photo instead.

## What is stored on the phone

Your observing lists, your own objects, your telescope setup (including the camera offset, two numbers that say where the telescope is in the camera's picture), the alignment and whether you have seen the introduction are kept in the app's private storage, along with the last downloaded orbit file. Uninstalling the app or clearing its data deletes them. Android's backup may include them if you have backup turned on.

## Contact

Open an issue at https://github.com/Zenithquonta/astrofixxer-android.
