# Release & signing

How the APKs are versioned, signed, and distributed — and what still needs a
human (audit-100 Fase 4).

## Channels

| Channel | Content | Signing | Updates in place? |
|---|---|---|---|
| `debug-latest` (GitHub release, prerelease) | `assembleDebug` APKs (arm64-v8a + x86_64) + GPL source tarballs | Shared debug key (below) | Yes — same key + rising `versionCode` |
| `release-latest` (GitHub release, prerelease) | `assembleRelease` APKs (R8 + shrinkResources, not debuggable) | Release keystore from secrets | Yes, once the secret exists |
| Workflow artifact `release-apk` | `assembleRelease` APKs, kept 30 days | Unsigned until the secret exists | No (unsigned APKs cannot install) |

The debug channel stays the default distribution. Switching it to the release
build would break in-place updates: the release key differs from the debug key,
and Android refuses an update signed with a different key
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

## Debug signing — the committed keystore

`android/debug.keystore` is committed to the repo on purpose. It signs **every**
debug APK — CI and local builds alike — with one stable key, so `debug-latest`
can be installed over the previous build instead of forcing an uninstall
(which wipes sessions and engine config).

- Alias `androiddebugkey`, store/key passwords `android`, RSA 2048, valid
  10,000 days from 2026-11-13.
- Certificate SHA-256 fingerprint:
  `7D:1D:2E:34:A0:2B:14:BC:CA:33:3D:08:F5:94:92:DB:01:94:4F:F2:3F:7F:1E:39:C0:C3:6B:FE:CC:D6:4C:4D`
  (check with `keytool -list -keystore android/debug.keystore -storepass android`).
- This is a **debug-only** key, same trust level as Android's public default
  debug key. It must never sign a release build; the release signing config is
  separate and keystore-backed. Anyone with repo access can sign a debug APK
  with it — that is the point, and the reason it is not a secret.

## Release signing — needs the maintainer

A signed release APK requires a release keystore that only the maintainer can
create. Until then CI builds the release APK unsigned and keeps it as a
workflow artifact; the `release-latest` channel stays empty (the workflow says
so in its log instead of failing silently).

To enable it:

1. Create the keystore once (keep the file and passwords outside the repo):
   `keytool -genkeypair -keystore release.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. In the GitHub repo, add these Actions secrets:
   - `RELEASE_KEYSTORE_B64` — `base64 -w0 release.keystore`
   - `RELEASE_KEYSTORE_PASSWORD` — the keystore password
   - `RELEASE_KEY_ALIAS` — `upload` (or the alias you chose)
   - `RELEASE_KEY_PASSWORD` — the key password
3. The next push to `main` restores the keystore in CI, signs `assembleRelease`,
   and publishes the APKs to `release-latest`.

Locally, the same flow works with `android/keystore.properties`
(gitignored):
`storeFile=<path>`, `storePassword=…`, `keyAlias=…`, `keyPassword=…`.

**Back the keystore up.** If it is lost, the release channel can never be
updated in place again — a new key means every user must uninstall first.

## Versioning

- `versionCode` is the commit count on `main` (`git rev-list --count HEAD`).
  Every merged PR raises it, so Obtainium and the package manager always see
  the new build as an update. Precedence in `app/build.gradle.kts`:
  `-PversionCode` > `VERSION_CODE` env > git count > `2` (fallback for
  tarball checkouts without git history — never distribute a fallback build).
  CI checks out with `fetch-depth: 0` so the count is real.
- `versionName` is the app's own `0.1.1` plus a per-build suffix:
  `0.1.1+<short SHA>` in CI, `0.1.1+local` for tarball builds. The `0.1.1`
  is the app's version; it happens to equal the engine's upstream tag
  (`UPSTREAM_TAG: v0.1.1` in `ci.yml`) but the two are unrelated.

## GPL source in every release

git (GPL-2.0), GNU tar and binutils (GPL-3.0) ship inside the APK. CI
downloads their pinned source tarballs, verifies SHA-256 (hashes in
`THIRD_PARTY_NOTICES.md` and `ci.yml`), and attaches them to the
`debug-latest` release next to the APKs. Licence texts live in `licenses/`
and are also bundled in the APK at `assets/licenses/`; the written offer for
Corresponding Source is in `THIRD_PARTY_NOTICES.md`.

**Keep the copies in sync:** `android/app/src/main/assets/THIRD_PARTY_NOTICES.md`
and `android/app/src/main/assets/licenses/` are static copies of the repo-root
files. When `THIRD_PARTY_NOTICES.md` or a file in `licenses/` changes, copy it
into `assets/` in the same commit.
