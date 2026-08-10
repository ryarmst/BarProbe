# Signing and publishing to Google Play

The app ID is `ca.ryarmst.barprobe` (permanent — it is the Play identity and cannot be
changed after the first upload).

Key material never lives in this repository. `.gitignore` blocks `*.jks`, `*.keystore`
and `keystore.properties`; the build reads signing config from environment variables or
a gitignored `keystore.properties`, and CI reads it from GitHub Actions secrets.

## 1. Generate the upload key (once)

Do this on your machine. Keep the resulting file **out of the repo**.

```
keytool -genkeypair -v \
  -keystore upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

Choose a strong store password (a separate key password is optional; reuse the store
password if you prefer). Store the passwords in a password manager.

**Back up `upload.jks` somewhere safe and private.** If you lose it you can ask Google to
reset the upload key, but it is a slow support process — losing it is avoidable pain, not
a catastrophe.

## 2. Sign locally (optional)

Only needed if you want a real signed build on your own machine. Create
`keystore.properties` at the repo root (gitignored):

```
storeFile=/absolute/path/to/upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Then `./gradlew bundleRelease` produces a signed AAB at
`app/build/outputs/bundle/release/app-release.aab`. Without this file the build falls
back to the debug key.

## 3. Configure CI secrets (once)

Base64-encode the keystore so it can live in a secret:

```
base64 -w0 upload.jks > upload-key.b64
```

In the GitHub repo: **Settings → Secrets and variables → Actions → New repository
secret**, add four:

- `SIGNING_KEYSTORE_BASE64` — paste the contents of `upload-key.b64`
- `SIGNING_KEYSTORE_PASSWORD` — the store password
- `SIGNING_KEY_ALIAS` — `upload`
- `SIGNING_KEY_PASSWORD` — the key password (same as store if you reused it)

Delete `upload-key.b64` afterwards (it is gitignored, but there is no reason to keep it).

The workflow maps these to the build, decodes the keystore into `RUNNER_TEMP` (outside
the checkout), and signs. On fork PRs the secrets are unavailable and the build falls
back to the debug key, which is expected.

## 4. Cut a release

```
git tag -a vX.Y.Z -m "…" && git push origin vX.Y.Z
```

On green the workflow:

- publishes the sideloadable **APKs** to a public GitHub release, and
- uploads a signed **AAB** as a private workflow artifact (Actions run → Artifacts →
  `aab-<sha>`), which is the file you give to Play.

## 5. Play Console (first time)

1. Register a developer account ($25 one-time) and complete identity verification.
2. Create the app with package `ca.ryarmst.barprobe`.
3. Enrol in **Play App Signing** (the default) — you upload with your upload key, Google
   holds and re-signs with the app-signing key.
4. Complete the listing before you can submit:
   - Privacy policy URL (a short "no data collected, no network access" page suffices —
     it is verifiably true here, as the app requests no `INTERNET` permission).
   - Data safety form: no data collected or shared.
   - Content rating questionnaire.
   - Icon (512×512), feature graphic (1024×500), screenshots.
5. Personal accounts created recently must run **closed testing with 12+ testers for 14
   continuous days** before applying for production. Plan for that timeline.
6. Download `app-release.aab` from the workflow artifact and upload it to the
   closed-testing track; promote to production once testing is complete.

Play installs are signed by Google's app-signing key, so they have a different signature
than the GitHub sideload APKs and the two cannot update each other — uninstall to switch.
