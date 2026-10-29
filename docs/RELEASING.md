# Releasing Taskfern

Taskfern is distributed only through GitHub Releases as a signed APK. There is no Google Play build.

## What happens on every push to `main`

The [Release workflow](../.github/workflows/release.yml) runs two jobs:

1. **Test, lint and build** — unit tests, Android Lint, the offline-manifest check, debug and instrumented-test builds.
2. **Sign and publish** (only on `main` of this repository, never for forks or pull requests):
   1. work out the version (below);
   2. check existing releases (idempotency and downgrade protection, below);
   3. decode the keystore from secrets into the runner's temporary directory and check alias and passwords;
   4. build the release APK with R8 and resource shrinking, signed with the release key;
   5. verify the APK: signature scheme v2+, not a debug certificate, application id `com.riyadmondol2006.taskfern`,
      expected `versionName`/`versionCode`, and no `INTERNET` permission;
   6. compute the SHA-256, write release notes from the commits since the previous tag;
   7. create the tag and GitHub Release with `Taskfern-vX.Y.N-release.apk` and its `.sha256` file;
   8. delete the keystore from the runner.

Pull requests and other branches run [CI](../.github/workflows/ci.yml) without any secrets.

## Versioning

| | Value | Example |
| --- | --- | --- |
| versionCode | `VERSION_CODE_OFFSET + github.run_number` of the Release workflow | `42` |
| versionName | `MAJOR.MINOR.versionCode` (`1.0` today) | `1.0.42` |
| Tag | `v` + versionName | `v1.0.42` |
| APK | `Taskfern-<tag>-release.apk` | `Taskfern-v1.0.42-release.apk` |

- The run number grows by one for every new run of the Release workflow, so each push to `main` gets a new, higher
  version and Android can always update.
- **Reruns are idempotent.** Re-running a run keeps its run number, so it produces the same tag. If that release
  already has its APK and checksum, the job ends successfully without publishing; if a previous attempt failed after
  creating the release, the missing files are uploaded again.
- **No downgrades.** If a release with a higher version code already exists (for example an old run re-run days later),
  the job ends without publishing.
- **No version-bump commits.** Nothing is committed back to the repository, so there are no release loops.
- `versionCode` is checked to stay within Android's limit (≤ 2,100,000,000).
- Local builds need no CI variables: they are `1.0.0-dev` with versionCode `1`. You can override with
  `-Ptaskfern.versionCode=… -Ptaskfern.versionName=…` or the `TASKFERN_VERSION_CODE`/`TASKFERN_VERSION_NAME`
  environment variables. `./gradlew printVersion` shows the values.
- To start a new major or minor line, change `TASKFERN_MAJOR`/`TASKFERN_MINOR` in the workflow's environment (they
  default to 1 and 0). If the workflow file is ever renamed or recreated (run numbers restart at 1), set
  `VERSION_CODE_OFFSET` to the last published versionCode so codes keep increasing.

## Signing

Required repository secrets (Settings → Secrets and variables → Actions):

| Secret | Content |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the release keystore, Base64 encoded (`base64 -w0 release.jks`) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

- Secrets are passed only to the steps that need them and are never printed.
- The workflow fails if any secret is missing or the key cannot be used, so an unsigned or debug-signed APK is never
  published.
- Keep the keystore backed up offline. If it is lost, existing users cannot update and would have to uninstall
  (losing data unless they exported a backup).

### Signing a release locally (optional)

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

then run `./gradlew :app:assembleRelease`.

## Concurrency

Release runs share one concurrency group per branch with `cancel-in-progress: false`: a release in progress always
finishes. GitHub keeps at most one run waiting; if several pushes arrive during a release, only the newest waits and
the intermediate ones are skipped. That newest run builds a commit that contains all earlier ones, so nothing is lost.

## Checking a release

```bash
sha256sum -c Taskfern-v1.0.42-release.apk.sha256
apksigner verify --print-certs Taskfern-v1.0.42-release.apk   # compare the certificate SHA-256 with the release notes
```
