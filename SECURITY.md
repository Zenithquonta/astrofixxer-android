# Security

## Keys and secrets

No key, token or password is stored in this repository. They live only in the repository's **GitHub Secrets**
(Settings → Secrets and variables → Actions) and reach the build as environment variables:

| Secret | Used for |
|---|---|
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Signing Google Play releases (`release.yml`, on `v*` tags) |
| `PREVIEW_KEYSTORE_BASE64`, `PREVIEW_KEYSTORE_PASSWORD` | Signing the downloadable `AstroFixxer.apk` (`android.yml`, on pushes to `main`) |

Without them, builds still work: release builds are unsigned and preview builds use the local debug key. That is why anyone
can build the app, but only this repository can publish updates that install over the official download.

Guard rails:
- `.gitignore` excludes keystores (`*.jks`, `*.keystore`, `*.p12`), `.env` files, `*.pem`/`*.key` files and `google-services.json`.
- Every push runs the **gitleaks** secret scanner (`secret-scan` job) and fails if a secret is committed.
- The app itself uses no API keys. Its network requests are the public CelesTrak orbit file and, only when you tap the update button in the GitHub download, GitHub's public release files. It sends no personal data (see `PRIVACY.md`).

A preview signing key committed on 28 Sep 2026 (`app/preview.keystore`) was retired the same day, before anything signed
with it was published. It must never be used again.

## How the in-app updater keeps updates safe

The GitHub download (the `preview` build, not the Google Play one) can update itself from **Sky & viewing → More → App
updates**. It only runs when you tap a button. What protects you:

1. **HTTPS only**, with timeouts and no token or login. Redirects are followed by hand, at most five, and only to https
   addresses on GitHub's own hosts (`github.com`, `api.github.com`, `*.githubusercontent.com`). The APK itself is fetched by
   Android's DownloadManager, which follows GitHub's redirects on its own, so for the APK it is the SHA-256 (point 5) that
   vouches for the bytes, not the address they came from.
2. **Same-release addresses.** The newest release (`/releases/latest`) lists its files. `update.json` and the APK are used only
   if their address is exactly `https://github.com/Zenithquonta/astrofixxer-android/releases/download/<that release's tag>/<the
   file's own name>`. Another repository, another host (`github.com.evil.com`), `http`, `user@host`, a query, `..` or
   percent-encoding are all refused. The APK is found by name in the same list; `update.json` never supplies an address.
3. **Size limits.** `update.json` is at most 64 KB and the APK at most 200 MB; the APK must be exactly the size the release
   lists and `update.json` states, and the download is stopped if it grows past it.
4. **Right app, newer build.** An update is offered only if `update.json`'s `applicationId` equals this app's and its
   `versionCode` is higher (the version name is for display only).
5. **SHA-256.** The downloaded file is copied into the app's private storage while it is hashed, so the checked bytes are
   the installed bytes and no other app can swap the file in between. A wrong size or hash deletes it and nothing is installed.
6. **Android decides at install time.** The system installer always asks you, and refuses any update signed with a different
   key than the installed app, whatever the updater says. CI signs the download with a permanent key kept in GitHub Secrets.
   If a build was signed with the throwaway debug key (`permanentKey: false` in `update.json`), the app says it cannot install
   over yours and asks for a second tap before downloading it.
7. **Google Play builds cannot do any of this.** `REQUEST_INSTALL_PACKAGES` is declared only in the preview and debug
   manifests, and CI fails if the release APK asks for it.

**Limit:** the checksum comes from the same GitHub release as the APK. Someone who could edit that release (a stolen GitHub
token, or a compromised workflow) could replace both the file and its hash, so the SHA-256 catches corruption, truncation and
a wrong file, not a malicious release. What still protects you then is Android's signature check: a build not signed with the
permanent key will not install over yours, so keeping that key only in GitHub Secrets matters most.

## If a secret leaks

1. Revoke or rotate it first (a new keystore, or a new token), before anything else. Deleting the commit is not enough,
   because git history and forks keep it.
2. Update the GitHub Secret, then remove the file and add its pattern to `.gitignore`.

## Reporting a vulnerability

Please open a GitHub security advisory (Security → Report a vulnerability) rather than a public issue.
