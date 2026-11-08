# PureHeat – Releases to Google Play

Every tag `vX.Y.Z` builds a signed app bundle in GitHub Actions and uploads it to Google Play
(`.github/workflows/release.yml`). The version comes from the tag: `versionName = X.Y.Z`,
`versionCode = X·10000 + Y·100 + Z` (Y and Z below 100). Local builds use `appVersion` from
`gradle.properties`.

## Making a release

1. Add the user-visible changes under "Unreleased" in `ReleaseNotes.md` (and bring
   `ReleaseNotes.de.md` and the other German docs in line), move them under the new version.
2. Update `distribution/whatsnew/whatsnew-en-US` and `whatsnew-de-DE` (≤ 500 characters each –
   the "What's new" text in the Play Store).
3. Set `appVersion` in `gradle.properties` to the new version (keeps local builds in step), commit.
4. `git tag v0.2.0 && git push origin v0.2.0` – the workflow waits for your approval in GitHub
   (environment `play-release`), then uploads to the track in the variable `PLAY_TRACK`
   (default `internal`).

## Signing: security concept

- **Play App Signing** (mandatory for app bundles): Google keeps the *app signing key*; we only
  hold the *upload key*. If the upload key leaks or is lost, it can be reset in the Play Console
  (Setup → App integrity → request upload key reset) – the app and its updates are not lost.
  That is what makes keeping the upload key in GitHub acceptable.
- The upload key lives **only** in a GitHub *environment* `play-release` (not as a repository
  secret):
  - **Required reviewer: you.** Every upload waits for a click in GitHub, so a leaked token or a
    rogue workflow change can't publish on its own.
  - **Deployment rule: tags `v*` only.** Other branches/workflows can't read the secrets.
  - The repo is public: secrets are never given to workflows from forks; never add a
    `pull_request_target` workflow that checks out PR code.
- In the runner the keystore only exists in `$RUNNER_TEMP` during the build and is deleted
  afterwards; passwords go in as environment variables (`PUREHEAT_KEYSTORE*`), never into files.
- GitHub secrets can't be read back: keep the keystore and its password **also offline**
  (password manager / encrypted backup).
- Google Play API access: a service account in Google Cloud, invited in the Play Console with
  only the rights for this app ("Release apps to testing tracks", plus "Release to production"
  once you want that). Its JSON key is the secret `PLAY_SERVICE_ACCOUNT_JSON`; rotate it if in
  doubt. (Keyless Workload Identity Federation would be even better; check whether the upload
  action supports it before switching.)
- Hardening for later: pin the actions in the workflow to commit SHAs and let Dependabot
  update them.

## One-time setup

1. **Upload key** (in your own terminal – `keytool` hangs in the IDE shell):

   ```
   keytool -genkeypair -v -keystore pureheat-upload.jks -storetype PKCS12 \
     -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

   One strong password (PKCS12 uses it for the key, too). Keep the file outside the repo.
   Local release builds: `keystore.properties` in the project root (in `.gitignore`) with
   `storeFile`, `storePassword`, `keyAlias`.
2. **First upload by hand:** `./gradlew bundleRelease` → upload `app-release.aab` in the Play
   Console (internal testing), opt in to Play App Signing. The API can't create the first release.
3. **In-app product:** Play Console → Monetise → In-app products → create `coffee` (name e.g.
   "Coffee for the developer"), price **1.99 EUR** and **1.99 USD** (other countries converted by
   Play), activate. Only possible after a build with the billing library was uploaded. Testing:
   add your account as *license tester* and install from the internal track.
4. **Service account:** Google Cloud project → enable "Google Play Android Developer API" →
   service account → JSON key; Play Console → Users and permissions → invite the service account
   with the rights above for PureHeat.
5. **GitHub:** Settings → Environments → New `play-release` → required reviewer, deployment tags
   `v*`; environment secrets:
   - `UPLOAD_KEYSTORE_BASE64`: `base64 -i pureheat-upload.jks | pbcopy`
   - `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (`upload`)
   - `PLAY_SERVICE_ACCOUNT_JSON`: the whole JSON file
   - variable `PLAY_TRACK`: `internal` now, later `alpha` (closed test) or `production`.

## RSA or ECC?

Android itself verifies EC (ECDSA P-256) signatures – APK Signature Scheme v2/v3 support them
and our minSdk 26 is far above the limit. For the **upload key** we still use **RSA 4096**: it is
what Google documents for Play App Signing and works with every tool in the chain; it isn't
certain that the Play Console accepts an EC upload key. The security gain of ECC doesn't matter
here – the risk is the key leaking, not breaking the algorithm – and the upload key can be reset.
The *app signing key* is generated and kept by Google anyway.
