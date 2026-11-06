# Unified Streaming Hub (proof of concept)

Native Android (Kotlin, Jetpack Compose) PoC for testing how Netflix, Disney+, Prime Video and Apple TV
title links hand off to the installed apps on a Samsung phone.

**Status: Phase 1 of 2 — provider integration test screen.** TMDB metadata and Saudi Arabia
watch-provider availability (Phase 2) are not built yet; see [Roadmap](#roadmap).

## What Phase 1 does

For each provider the screen has:

| Control | Behaviour |
|---|---|
| Test title URL field | Free text, `https://` only, persisted per provider. **Empty by default.** |
| Detect app | Asks Android which apps can handle that URL and splits them into browsers vs non-browser handlers. |
| Open Title | Sends `ACTION_VIEW` for the URL and logs the classified outcome. |
| Result log | Newest first. Each entry shows outcome, provider, time, URL, and the reasoning. |

### Outcome classes

| Log label | Meaning | What it does **not** mean |
|---|---|---|
| **App launch** | A non-browser activity accepted the intent (`FLAG_ACTIVITY_REQUIRE_NON_BROWSER` succeeded). | Not proof it is the provider's app, that the title opened, or that playback started. |
| **Browser fallback** | No non-browser handler; the URL went to a browser. | Cannot tell "app not installed" from "app installed but does not claim this link" (unverified App Link, or link handling switched off in system settings). |
| **Missing app** | Neither an app nor a browser could handle the URL (`ActivityNotFoundException` on both attempts). | — |
| **Unknown** | Unexpected exception (e.g. `SecurityException`). | — |
| **Not attempted** | Input rejected (empty, malformed, not `https`). No intent was sent. | — |

`startActivity()` returning normally means the system accepted the request. Nothing in this app, or
in any public Android API, can confirm playback began inside another app.

### What is deliberately not in the code

No provider URLs, hosts, package identifiers or custom URL schemes are hard-coded anywhere.

* **Title URLs** come from you: copy a share/title link from the provider's own official site or app.
* **App detection** uses scoped package visibility: the manifest declares one `<queries>` intent
  (`VIEW` + `BROWSABLE` + `https`), so `PackageManager.queryIntentActivities()` can see which apps
  claim a link. No `QUERY_ALL_PACKAGES`, no package names.
* Browsers are identified by resolving a neutral probe, `https://example.com/` (reserved by RFC 2606),
  and treating those packages as "browsers".

## Requirements

* JDK 17 or newer (built here with 21)
* Android SDK with platform `android-36` and build-tools `36.0.0`
* Gradle is provided by the wrapper (8.14.3); no separate install
* Target device: Android 11+ (minSdk 30). Samsung One UI on Android 12+ gets Material You colours.

Pinned versions: AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.06.01 (newer Compose 1.12 needs AGP 9.1+
and compileSdk 37; deliberately not used), compileSdk/targetSdk 36.

## Setup

```bash
cd unified-streaming-hub

# 1. Point Gradle at your SDK (Android Studio does this for you)
echo "sdk.dir=/path/to/Android/sdk" > local.properties      # or export ANDROID_HOME

# 2. If the SDK pieces are missing
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Or just open the `unified-streaming-hub` folder in Android Studio and let it sync.

## Build

```bash
./gradlew :app:testDebugUnitTest          # 20 JVM unit tests
./gradlew :app:assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease            # app/build/outputs/apk/release/app-release.apk
```

The release APK is R8-shrunk (~2 MB) and signed with the **debug key** purely so it installs for
testing. Create a real keystore before distributing anything.

## Install on a Samsung phone

1. Settings → About phone → Software information → tap **Build number** 7 times; then
   Settings → Developer options → enable **USB debugging**.
2. Connect by USB and accept the RSA prompt.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`

(Or copy the APK to the phone and open it; allow "install unknown apps" for your file manager.)

## Manual test procedure (needs a physical phone)

Do this per provider, with the official app installed and signed in.

1. Copy an official title link from the provider's site or app share sheet. Paste it in the field.
2. **Detect app** — expect "Non-browser app can handle this URL" with a package name if the app claims the link.
3. **Open Title** — read the log entry. Then check by eye whether the correct title screen actually appeared.
4. Repeat with the app **uninstalled** (or disabled): expect Browser fallback.
5. Repeat after Settings → Apps → *provider* → Open by default → turn off supported links: expect Browser fallback
   even though the app is installed. This shows why Browser fallback cannot prove "app missing".

Record observed behaviour per provider; that is the real output of this PoC.

## Tests

`app/src/test` (JVM only, no device needed):

* `ProviderLauncherTest` — 12 cases: detection classes, app launch, browser fallback, missing app,
  unknown (both stages), rejected input, prediction mismatch, log metadata.
* `UrlValidatorTest` — 5 cases: https-only, whitespace, custom schemes, `intent:`/`javascript:`, missing host.
* `ProviderTestViewModelTest` — 3 cases: persistence, stale-detection reset, log ordering/clear.

The Android framework is hidden behind `IntentGateway`, so classification logic is tested with a scripted
fake. **Not covered:** real `PackageManager`/`startActivity` behaviour (needs instrumented tests on a device)
and Compose UI tests.

## Layout

```
app/src/main/java/com/unifiedstreaminghub/poc/
  domain/   Provider, UrlValidator, IntentGateway, ProviderLauncher, LaunchModels   (pure Kotlin)
  data/     AndroidIntentGateway (PackageManager + startActivity), UrlStore
  ui/       ProviderTestScreen, ProviderTestViewModel, theme/
```

## Known limitations

* **Not run on a device.** The build and JVM tests pass; behaviour on a real Samsung handset is unverified.
* Android 12+ only hands https links to an app if the app's links are verified or you enabled them.
  Many providers' links may therefore open in the browser. That is a platform rule, not a bug here.
* The package manager reports apps that *can* handle a URL; the Samsung/default-app choice at launch time may differ.
* Chooser dialogs (several browsers or handlers) still count as "started".

## Roadmap

**Phase 2 (not started)**

1. TMDB client for metadata and `/watch/providers` filtered to `SA`, behind a repository interface, with
   mock data until `TMDB_API_KEY` (or read-access token) is supplied via `local.properties`/`BuildConfig`.
2. Search/detail UI listing Saudi Arabia availability (flatrate/rent/buy) per title.
3. A separate playback link resolver interface. TMDB's provider data carries a JustWatch-sourced
   *landing page* link, not per-title app deep links, so availability and playback links stay decoupled.
4. Tests with a mocked HTTP layer and fixtures.

Attribution: when TMDB watch-provider data is used, TMDB requires attribution, and the data is
supplied by JustWatch. Verify current terms at developer.themoviedb.org before release.
