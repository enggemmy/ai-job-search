# Defect View — Offline AI Construction Inspection & Defect Management

Native Android app (Kotlin, Jetpack Compose, Room, CameraX) for offline construction defect
inspection. See the top of this session's task for the full product spec; this file tracks
what's actually built versus what's still ahead, and how to build it.

## Module layout

- `domain/` — pure Kotlin/JVM module. Models, the defect ID generator, the defect taxonomy, the
  confidence classifier, the rule-based reasoning engine, the learning-queue state machine, and
  cosine-similarity search. No Android dependency, so it builds and its tests run anywhere with a
  JDK — this is the part of the app that has been genuinely compiled and tested in CI-less
  environments (see NETWORK_LIMITATIONS.md).
- `app/` — the Android application: Compose UI, Room database, CameraX capture, repositories,
  navigation. Depends on `domain`.

## Building

```
./gradlew :domain:test        # runs today, anywhere with JDK 17+
./gradlew :app:assembleDebug  # requires the Android SDK - see NETWORK_LIMITATIONS.md
```

If you don't have a `gradlew` wrapper jar checked in yet, run `gradle wrapper` once with a local
Gradle install, or open the project in Android Studio, which provisions the SDK and wrapper
automatically.

## Phase status (spec section 14)

| Phase | Scope | Status |
|---|---|---|
| 1 | Project scaffold, navigation, theme, Room schema, Projects, Inspections, CameraX capture, image storage | Implemented, not compiled (see limitations) |
| 2 | Defect View annotation editor, defect records, status management, before/after | Not started |
| 3 | Local vision engine, AI result interface, confidence handling | Interface + models done (`domain/vision`); classical-CV implementation not started |
| 4 | Reasoning engine, inspection templates, project knowledge | Reasoning engine done and tested (`domain/reasoning`); templates/knowledge store not started |
| 5 | Verified learning, similarity search, Learning Center, model versioning | Domain logic done and tested (`domain/learning`, `ModelVersion`); Room tables + Learning Center UI not started |
| 6 | PDF reports, dashboard, search/filter/export | Dashboard stats screen done; PDF reports not started |
| 7 | Tests, offline validation, production packaging | Domain unit tests real and passing; app-module instrumented tests not started; no APK has been produced or run |

Nothing in this table is a placeholder button — every "not started" item is either absent from
the nav graph or, where the nav destination exists (Learning Center, Reports, Settings), it shows
an explicit "not yet implemented, here's what's planned" screen rather than a fake control.
