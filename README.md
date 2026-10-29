# PlaylisX

Lightweight Android IPTV playlist editor. Black and yellow UI, built with Kotlin + Jetpack Compose.

**Features:** fetch playlist from URL or import a local file, find & replace (plain or regex) to rebrand names/groups/logos,
custom TVG settings (tvg-name, tvg-id, logo base URL, group prefix/override, EPG url), smart cleanup presets
(Light / Standard / Deep), playlist validator + optional stream test, preview before every change with undo/redo,
multiple export profiles (built-in + your own), copy / share / save.

## Get the APK from GitHub
1. Create a new GitHub repo and push this folder to the `main` branch.
2. Open the **Actions** tab. The **Build APK** workflow runs on every push. Download `PlaylisX-apk` from the run's artifacts.
3. For a public download link, push a version tag. The APK is attached to a GitHub Release:
   ```
   git tag v1.0.0
   git push origin v1.0.0
   ```

## Stable signing (recommended)
Without secrets the APK is signed with a debug key (it installs fine, but a later build signed with a different key
cannot update it in place). To keep one key:
```
keytool -genkey -v -keystore release.jks -alias playlisx -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # copy the output
```
Add these repository secrets: `KEYSTORE_B64` (that base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Keep `release.jks` private and backed up.

## Build locally
Open the folder in Android Studio (Koala or newer) and press Run, or install Gradle 8.7 and run `gradle assembleRelease`.
