# Unified Streaming Hub (proof of concept)

Native Android (Kotlin, Jetpack Compose) PoC that checks, on a real Samsung phone, how Netflix, Disney+,
Prime Video and Apple TV react to an official title link: is the app installed, does it accept the URL,
or does the link fall back to the browser.

**Status: Phase 1B, real-device validation build.** Built and unit-tested; **not yet run on a physical
phone**. Nothing here claims playback works. TMDB metadata and Saudi Arabia watch-provider availability
(Phase 2) and any catalog are not started.

## What the app does

Two tabs.

**Providers**: one card per provider:

| Element | Behaviour |
|---|---|
| Status | **Installed** / **Not installed** / **Unknown**, with package ID and version. Re-checked every time the app resumes. |
| Launch application | Starts the app's launcher activity (no title). Disabled when not installed. |
| Official title URL field | Free text, `https://` only, saved per provider. **Empty by default**; nothing is pre-filled or generated. |
| Open specific title | Sends `ACTION_VIEW` for your URL (routes below) and logs the classified outcome. |
| Check URL handlers | Shows which non-browser apps Android says can handle that URL. |
| Last title test | Per-provider record of the most recent title attempt on this device. |
| Fallback panel | Appears when a title could not be opened in the app: states that title-specific launch is unsupported for that link and points to Launch application. |

**Log**: every attempt, newest first, with an ordered step trail; **Copy log** puts a plain-text report
(device model, Android version, every step) on the clipboard. The same steps go to Logcat:
`adb logcat -s UnifiedHub`.

### Status meanings

* **Installed**: `PackageManager` found the verified package ID (may carry a "disabled" note).
* **Not installed**: package not found. Caveat: apps in another profile (Samsung Secure Folder, work
  profile) are invisible to this app and also read as "not installed".
* **Unknown**: not checked yet, or the lookup threw an unexpected exception.

### How a title is opened (`ProviderLauncher.open`)

1. `ACTION_VIEW` restricted to the provider's verified package. Success = that exact app has an activity for this URL.
2. `ACTION_VIEW` with `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`. Success = some *other* app took it (logged as such, not as the provider).
3. Plain `ACTION_VIEW` → browser. Logged as **Browser fallback**.

Outcome labels: **App launch**, **Browser fallback**, **Missing app**, **Unknown**, **Not attempted** (input rejected).

> Every outcome means only "Android accepted or refused the launch request". No public Android API can
> confirm that the title screen appeared or that playback started inside another app. Only a person
> looking at the phone can. **Playback is untested.**

### Deep links: what is and is not validated

* The app builds, guesses and hard-codes **no** deep links, hosts or custom schemes. It uses only the
  `https://` URL you paste.
* Whether each provider's app accepts a given link is **validated per provider, on your phone, at
  runtime** (route 1 above). That result is shown on the card and recorded in the log. It has **not**
  been validated by me for any provider: this build environment could not reach the providers' domains
  and the request to allow them was declined, so their published `assetlinks.json` files were not read.
* A provider's app may accept a URL by explicit package yet not be the default handler for it
  (unverified App Link), so Open specific title can succeed in the app while a link tapped elsewhere
  opens the browser. The log trail shows both signals.

## Verified package identifiers

Checked on 2026-11-06 against each provider's own Google Play listing
(`https://play.google.com/store/apps/details?id=<id>`): page loads, developer matches, **Phone** device chip present.

| Provider | Package ID | Play developer |
|---|---|---|
| Netflix | `com.netflix.mediaclient` | Netflix, Inc. |
| Disney+ | `com.disney.disneyplus` | Disney |
| Prime Video | `com.amazon.avod.thirdpartyclient` | Amazon Mobile LLC |
| Apple TV | `com.apple.atve.androidtv.appletv` | Apple |

Excluded after the same check (no Phone chip): `com.amazon.amazonvideo.livingroom` (Prime Video TV),
`com.apple.atve.sony.appletv` (Sony TV variant).

Not verified: Galaxy Store, regional or OEM builds may use other IDs. The Apple ID contains "androidtv"
although its listing shows Phone/Tablet, so confirm its behaviour on the phone. If a status shows
**Not installed** while the app is on the phone, that ID is the first suspect: tell me the real one.

Only these four IDs are in the manifest `<queries>`, with one `https` VIEW intent. A unit test fails
the build if the manifest and `Provider.kt` ever differ. No `QUERY_ALL_PACKAGES`, no root, no accessibility
service; the only permission is `INTERNET` (unused until Phase 2).

## Requirements

* JDK 17+ (built with 21); Android SDK platform `android-36`, build-tools `36.0.0`; Gradle via wrapper (8.14.3)
* Phone: Android 11+ (minSdk 30). Pinned: AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.06.01
  (newer Compose 1.12 needs AGP 9.1+/compileSdk 37, deliberately not used)

## Build

```bash
cd unified-streaming-hub
echo "sdk.dir=/path/to/Android/sdk" > local.properties     # or export ANDROID_HOME
sdkmanager "platforms;android-36" "build-tools;36.0.0"      # if missing

./gradlew :app:testDebugUnitTest      # 37 JVM unit tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # R8-shrunk, signed with the DEBUG key, for testing only
```

If Maven Central answers HTTP 429 (rate limit), just re-run; Gradle caches what it already fetched.

## Install on a Samsung phone

**Simplest: send the APK to the phone and open it.**

1. Get `app-debug.apk` onto the phone: download it from the Claude app/session, or email/Drive/Quick Share it to yourself.
2. Open it from **My Files → Downloads** (or the download notification) and tap **Install**.
3. First time only, Android asks to allow installs from that app (Chrome / My Files / Drive): enable
   *Allow from this source*, go back, tap **Install**.
4. If Google Play Protect warns about an unknown developer, choose **Install anyway** (the APK is debug-signed, not from the Play Store).
5. If the install is blocked outright, Samsung **Auto Blocker** may be on: Settings → Security and privacy → Auto Blocker
   (menu names vary by One UI version), turn it off while installing, then back on.

**Alternative: USB with adb.** Enable Developer options (tap *Build number* 7× under Settings → About phone →
Software information), turn on USB debugging, connect, accept the prompt, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s UnifiedHub        # live diagnostics
```

## On-device test procedure

Install and sign in to each provider app first. For every provider, copy an **official title link** from the
provider's own site or its app's Share menu, and paste it into that card.

1. Open the app, **Providers** tab. Each card should read **Installed** with a version. Record any that does not.
2. Tap **Launch application**. The provider app should open. Log entry: *App launch*.
3. Tap **Check URL handlers** (shows what Android thinks), then **Open specific title**.
4. **Look at the phone.** Did the provider app come up on the right title screen? Log says *App launch* only if
   Android accepted; the screen is the real evidence. Do not start playback expecting this app to do it.
5. Failure cases worth trying once: link with the app force-stopped; app uninstalled (expect *Not installed* +
   *Browser fallback*); Settings → Apps → *provider* → Open by default → Open supported links off.
6. **Log** tab → **Copy log**, paste it into a message to me. Report per provider: status, outcome, and what you saw on screen.

## Tests

`app/src/test` (JVM, no device): `ProviderLauncherTest` (22: detection, package route, other-app route, browser
fallback, missing, unknown at each stage, app launch, diagnostic trail), `ProviderTestViewModelTest` (5),
`UrlValidatorTest` (5), `ManifestQueriesTest` (3: manifest vs `Provider` IDs, no broad permissions, no service),
`LogFormatterTest` (2). The Android framework sits behind `IntentGateway`, so classification is tested with a
scripted fake. **Not covered:** real `PackageManager` / `startActivity` behaviour and Compose UI (need a device).

## Layout

```
app/src/main/java/com/unifiedstreaminghub/poc/
  domain/   Provider, UrlValidator, IntentGateway, ProviderLauncher, LaunchModels, LogFormatter   (pure Kotlin)
  data/     AndroidIntentGateway + Diagnostics (PackageManager, startActivity, Logcat), UrlStore
  ui/       HubApp (Providers + Log tabs), ProviderTestViewModel, theme/
```

## Known limitations

* **Never run on a device.** Build, tests and manifest are verified; handset behaviour is not.
* Title-link support per provider is unknown until you run the procedure above.
* "Not installed" is unreliable for apps in Secure Folder / work profiles.
* Chooser dialogs (several handlers) still count as "started".
* Debug-signed APK only; no Play Store distribution, no update path.
* Launcher icon is a placeholder; not visually checked on a device.

## Roadmap (not started)

**Phase 2**: TMDB client for metadata and `/watch/providers` filtered to `SA`, mock data until a credential is
supplied; a playback-link resolver kept separate from availability. TMDB watch-provider data is supplied by
JustWatch and requires attribution; verify current terms at developer.themoviedb.org before release.
