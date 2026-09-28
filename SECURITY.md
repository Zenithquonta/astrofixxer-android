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
- The app itself uses no API keys. Its only network request is the public CelesTrak orbit file, and it sends no personal data (see `PRIVACY.md`).

A preview signing key committed on 28 Sep 2026 (`app/preview.keystore`) was retired the same day, before anything signed
with it was published. It must never be used again.

## If a secret leaks

1. Revoke or rotate it first (a new keystore, or a new token), before anything else. Deleting the commit is not enough,
   because git history and forks keep it.
2. Update the GitHub Secret, then remove the file and add its pattern to `.gitignore`.

## Reporting a vulnerability

Please open a GitHub security advisory (Security → Report a vulnerability) rather than a public issue.
