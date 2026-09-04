# Build environment constraints in this sandbox

This is a factual record of what was and wasn't verifiable while this project was built, so
nobody mistakes "written" for "confirmed working."

## What's blocked

The sandbox's outbound network policy blocks `dl.google.com`. `maven.google.com` (Gradle's
`google()` repository) redirects every artifact request to `dl.google.com`:

```
$ curl -sSI https://maven.google.com/androidx/core/core/1.13.1/core-1.13.1.pom
location: https://dl.google.com/dl/android/maven2/androidx/core/core/1.13.1/core-1.13.1.pom
$ curl -sS https://dl.google.com/...
curl: (56) CONNECT tunnel failed, response 403
```

Consequences:
- The Android Gradle Plugin itself cannot be downloaded (it's published on `maven.google.com`).
- AndroidX, Jetpack Compose, Room, CameraX, Navigation-Compose — everything under
  `androidx.*` and `com.google.android.*` — cannot be downloaded.
- The Android SDK (platform `android.jar`, build-tools, `aapt2`, `d8`) cannot be downloaded via
  `sdkmanager`, which also pulls from `dl.google.com`.
- No Android SDK is pre-installed in this environment.

Maven Central, the Gradle Plugin Portal, and (separately) plain HTTPS to `maven.google.com`'s
non-redirected paths are reachable, which is why the `domain` module - deliberately kept free of
any Android/Google dependency - builds and its tests run here.

## What this means concretely

- `./gradlew :app:compileDebugKotlin`, `:app:assembleDebug`, or any other `:app:*` task **cannot
  be run to completion in this environment**. This was verified, not assumed - see the actual
  failed `:domain:test` run against a shared root `build.gradle.kts` earlier in this project's
  history, which is why the root build file was emptied and each module now declares its own
  plugin versions.
- No APK has been built, installed, or run. No emulator is available either.
- The `:domain` module - the defect ID generator, the taxonomy, the confidence classifier, the
  rule-based reasoning engine, the learning-queue state machine, and the cosine-similarity search
  - has been compiled and its unit tests have been run for real, repeatedly, in this sandbox:

  ```
  $ gradle :domain:test
  BUILD SUCCESSFUL
  ```

  41 tests, 0 failures, across 7 test classes (see `domain/build/test-results/test/*.xml`).

## What to do next (outside this sandbox)

Open `android/` in Android Studio, or run `./gradlew :app:assembleDebug` on a machine with normal
internet access. Everything in `app/` was written carefully against the declared dependency
versions (Compose BOM 2024.09.02, Room 2.6.1, CameraX 1.3.4, Navigation-Compose 2.8.0) and
reviewed by hand for API correctness, but until it's compiled somewhere with SDK access, treat it
as "should work, not yet proven" rather than "done."
