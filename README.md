# InkVault for Android

Android client for InkVault, a self-hosted password manager with end-to-end encryption and sync over XMPP.

The vault lives on your own XMPP server (for example, Prosody) in the PEP nodes of your account. The server stores only ciphertext, and the master password never leaves the phone. The web and terminal clients use the same format, and changes reach other devices within 1-2 seconds.

## Features

- Create a new vault or sign in to an existing one with a JID, XMPP password and master password.
- Unlock with the master password or a fingerprint.
- Searchable list with "All", "Favorites" and "Trash" filters and a tag tree.
- Records: name, URL, username, password, notes, tags, favorite flag, password history.
- Password generator.
- PIN + one-time code (TOTP) passwords: the full password is computed when shown, with a countdown to the next code.
- Offline mode: the vault opens from an encrypted cache, and changes are sent once the connection is back.
- Backup: encrypted export and import through the system file picker, compatible with the other clients.
- Master password change, emptying the trash, deleting the whole vault.
- Recovery: if the server lost its data or was restored from an old backup, the phone uploads the account and records back to it.
- English and Russian interface. The language follows the system settings, and on Android 13 and newer it can be set per app. Other system languages get English.

On-device protection:

- the app window is excluded from screenshots (`FLAG_SECURE`), and app data backup is disabled;
- the vault locks after 15 minutes without user activity, including while the app is in the background;
- a copied password is marked as sensitive and cleared from the clipboard after 45 seconds;
- the quick unlock key is kept in Android Keystore and requires a fingerprint for every use;
- the XMPP password is encrypted with an Android Keystore key.

Requires Android 8.0 (API 26) or newer on an arm64-v8a or x86_64 device.

## Installation

Prebuilt APKs are on the [releases](https://github.com/tzx1z/inkvault-android/releases) page. Building from source is described below.

Releases are signed with a certificate with this SHA-256 fingerprint:

```
6A:F0:58:1D:98:BF:5D:1C:0E:7C:46:B5:AF:B9:16:25:07:B3:B4:32:4A:7D:67:93:E5:05:CB:D4:F5:FD:83:B8
```

To verify a downloaded APK, run `apksigner verify --print-certs inkvault-android-release.apk` and compare the `certificate SHA-256 digest` line.

## Server requirements

Prosody 13 or another XMPP server with PEP (XEP-0163), `publish-options` and Stream Management (XEP-0198).

The client works with the default Prosody settings: a new vault is created with a limit of 256 records, which is the default `pep_max_items`. Records in the trash count toward the limit. For vaults larger than 256 records, raise the limit on the server:

```lua
pep_max_items = 10000
```

After that, raise the vault node limit in the app under "More" - "Item limit". The new value applies immediately on all devices, since the other clients read the node limit from the server. The server rejects a limit above `pep_max_items`, and the client does not allow a limit below the current number of records: lowering it would make the server delete the oldest records.

## Building

Requirements:

- JDK 17 (`./gradlew` downloads Gradle 9.7.1 on first run, the distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`);
- Android SDK with platform 37, path in `local.properties` (`sdk.dir=...`) or in `ANDROID_HOME`.

The app depends on the AAR `dev.inkov.inkvault:inkvault-core-android` from [inkvault-core](https://github.com/tzx1z/inkvault-core). Its version is set in `build.gradle.kts`. By default Gradle fetches it from the project's GitLab package registry, which requires an access token (see below). Without one, build the core from source and publish it to the local Maven repository. This needs the Rust and Android NDK setup from the core README. Check out the core tag that matches the version in `build.gradle.kts`:

```sh
git clone https://github.com/tzx1z/inkvault-core.git
git clone https://github.com/tzx1z/inkvault-android.git

(cd inkvault-core && git checkout v0.2.1 && bindings/build.sh android \
    && cd bindings/uniffi/android && ./gradlew publishToMavenLocal)

cd inkvault-android
./gradlew assembleDebug -PinkvaultCoreLocal      # build/outputs/apk/debug/inkvault-android-debug.apk
adb install build/outputs/apk/debug/inkvault-android-debug.apk
```

With `-PinkvaultCoreLocal` the core is taken only from `mavenLocal()`.

GitLab package registry access: a personal access token with the `read_api` scope or a deploy token with the `read_package_registry` scope in `~/.gradle/gradle.properties`. In GitLab CI the job token is used instead.

```
gitlabToken=<token>
```

### Release build

The release APK is signed with the key from `signing.properties` in the repository root. The file is listed in `.gitignore`, and the key itself is stored outside the repository:

```
keystore=/home/<user>/.keystores/inkvault-release.p12
keystore.password=<keystore and key password>
keystore.alias=inkvault
```

The key path must be absolute, `~` is not expanded. In PKCS12 the keystore and key passwords are the same. Without `signing.properties` the release APK is unsigned, and an incomplete file stops the build with an error. Keep the file at mode `600`.

```sh
./gradlew assembleRelease                  # build/outputs/apk/release/inkvault-android-release.apk
apksigner verify --print-certs build/outputs/apk/release/inkvault-android-release.apk
```

`apksigner` is in `$ANDROID_HOME/build-tools/<version>/`.

Before a release, increase `versionCode` and `versionName` in `build.gradle.kts`: Android does not install an APK with a lower `versionCode` over the current version.

An installed app can only be updated with an APK signed by the same key, so the key file and its password must be backed up outside the build machine. If the key is lost, installed copies cannot receive updates. For the same reason, a debug build cannot be updated to a release build: the app has to be uninstalled together with its local data (vault cache, quick unlock key, XMPP password). The vault on the server is kept, and the user signs in again.

The release key was created with:

```sh
keytool -genkeypair -keystore ~/.keystores/inkvault-release.p12 -storetype PKCS12 -alias inkvault \
    -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
    -dname "CN=Evgeny Inkov, EMAILADDRESS=me@inkov.dev, O=InkVault"
```

## Tests

The end-to-end test drives the app on a phone over USB with adb and uiautomator: vault creation, sync with another client, PIN + code, export and import, server recovery, master password change. The test server runs in podman, and the phone reaches it through `adb reverse`.

The test uses `@inkov-dev/inkvault-testkit`, the core WebAssembly build for Node and the `registry.gitlab.com/inkvault/inkvault-core/test-prosody` image of the same version. All three come from the GitLab registries of `inkvault-core` and require a token with the `read_api` and `read_registry` scopes.

```sh
podman login registry.gitlab.com     # once
npm config set -- //gitlab.com/api/v4/projects/inkvault%2Finkvault-core/packages/npm/:_authToken <token>   # once
(cd tests && npm ci)
./gradlew assembleDebug
tests/run.sh                         # --biometric also tests fingerprint unlock
```

Requires podman and Node.js 22. The test clears the app data on the phone. The scenario checks Russian UI strings: on Android 13 and newer `run.sh` sets Russian as the app language, older versions need Russian as the system language.

Dependency license check for the APK:

```sh
python3 -I tools/licenses.py
```

## Layout

| Path | Contents |
|------|----------|
| `src/main/kotlin/dev/inkov/inkvault/android/` | the app: Jetpack Compose screens, vault controller, biometrics, clipboard |
| `src/main/kotlin/dev/inkov/inkvault/pep/` | PEP adapter for the core sync engine, built on Smack |
| `gradlew`, `gradle/wrapper/` | Gradle Wrapper |
| `tests/` | end-to-end test on a phone |
| `tools/licenses.py` | dependency license check |
| `art/` | icon and logo sources in SVG |

## License

[GNU AGPL v3.0](LICENSE) (`AGPL-3.0-only`). Copyright (C) 2026 Evgeny Inkov.

## Contact

Evgeny Inkov: [me@inkov.dev](mailto:me@inkov.dev), XMPP [tzx1z@inkov.dev](xmpp:tzx1z@inkov.dev), [inkov.dev](https://inkov.dev)
