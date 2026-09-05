# Defect View — Offline AI Construction Inspection & Defect Management

Native Android app (Kotlin, Jetpack Compose, Room, CameraX) for offline construction defect
inspection. See the top of this session's task for the full product spec; this file tracks
what's actually built versus what's still ahead, and how to build it.

## Module layout

- `domain/` — pure Kotlin/JVM module. Models, the defect ID generator, the defect taxonomy, the
  confidence classifier, the rule-based reasoning engine, the learning-queue state machine,
  cosine-similarity search, the annotation-editor geometry (hit-testing, move, resize, path
  length), `ClassicalVisionEngine` - a real, deterministic classical-CV defect-signal detector
  (edge-density and color-variance outlier tiles), not a trained model and never presented as
  one - and `SimpleFeatureExtractor`, a hand-crafted 8-dimensional image feature vector (not a
  learned embedding) used for the verified-example similarity search. No Android dependency, so
  it builds and its tests run anywhere with a JDK — this is the part of the app that has been
  genuinely compiled and tested in CI-less environments (see NETWORK_LIMITATIONS.md).
- `app/` — the Android application: Compose UI, Room database, CameraX capture, repositories,
  navigation. Depends on `domain`.
- `dataset_pipeline/` — offline, Python dataset ingestion/license-control/taxonomy-mapping/QC
  pipeline feeding the future local AI model (67 tests, genuinely run and passing - see its own
  README.md and DATASET_ACQUISITION.md). Independent of the Android build; no real dataset has
  been imported yet because every dataset host is network-blocked from this sandbox.

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
| 2 | Defect View annotation editor, defect records, status management, before/after | Implemented, not compiled - see gaps below |
| 3 | Local vision engine, AI result interface, confidence handling | Done and tested: `ClassicalVisionEngine` (`domain/vision`), `AIAnalysis`/`AIDetection` persistence, "Analyze image" wired into New Inspection with spec-safe confidence language, AI detections now pre-populate the editor (see Phase 4) |
| 4 | Reasoning engine, inspection templates, project knowledge | Reasoning engine done and tested (`domain/reasoning`); AI detections seed editable annotations and prefill the Defect Form via the reasoning engine; `ProjectKnowledgeEntity`/DAO/Repository is a real, working data layer wired as an optional reasoning input - no management UI yet; inspection templates not started - see gaps below |
| 5 | Verified learning, similarity search, Learning Center, model versioning | Done and tested at the domain level (`domain/learning`, `SimpleFeatureExtractor`); app-level wiring complete: saving a defect from an AI suggestion records a `VerifiedExampleEntity` (APPROVED/CORRECTED) and enqueues it, the Learning Center screen shows stats/queue/version history and can run a local learning update and roll back - see gaps below |
| 6 | PDF reports, dashboard, search/filter/export | Dashboard stats screen done (Phase 1); 2 of 6 PDF report types implemented with `android.graphics.pdf.PdfDocument` (Individual Defect View, Defect Register), both generated from live Room records and shareable via the standard Android share sheet; search/filter (query + status) added to the Defects list - see gaps below |
| 7 | Tests, offline validation, production packaging | Domain: 64 unit tests, genuinely run, 0 failures. App: one instrumented Room test class added (`DefectDaoTest` - real SQLite round-trips, enum converters, cascade delete, count queries) but **not run** - no emulator/device in this sandbox, see below. No APK has ever been produced. Offline-by-construction verified by inspection (see below), not by running the app |

Nothing in this table is a placeholder button — every "not started" item is either absent from
the nav graph or, where the nav destination exists (Learning Center, Reports, Settings), it shows
an explicit "not yet implemented, here's what's planned" screen rather than a fake control.

## Overall status - read this before trusting any "done" above

This is a large, multi-thousand-line codebase built across seven phases in one sitting, in a
sandbox that cannot compile the `app` module at all (see NETWORK_LIMITATIONS.md) and has no
Android emulator. Two different kinds of confidence are mixed together in this file, and they
should not be treated the same:

- **`domain/` is genuinely verified.** 64 unit tests, run repeatedly in this sandbox via
  `gradle :domain:test`, currently 0 failures. Every domain-level claim in this README ("real
  classical-CV signal, not random," "confidence never implies certainty," "learning-queue
  transitions are enforced," etc.) is backed by a test that actually executed.
- **`app/` is careful, reviewed-by-hand Kotlin/Compose/Room code that has never compiled.**
  Every file was written against the pinned library versions and re-read for API correctness
  (several real bugs were caught this way and are visible in the git history - a wrong Compose
  extension-function import, an illegal non-local `return` through a non-inline lambda, a
  `List.single()` name collision with the Kotlin stdlib, a redirect-following network check that
  changed the whole project's architecture). That process catches real classes of mistakes; it
  does not catch everything a compiler does. Treat every `app/`-module claim in the phase table
  as "should work, not yet proven" until it's opened in Android Studio or built on a machine with
  normal internet access.

**Offline-by-construction, verified without running the app:** the manifest declares only the
`CAMERA` permission (no `INTERNET`, so the OS itself blocks any network call even if code tried
to make one); `app/build.gradle.kts` has no HTTP/network/cloud dependency (no Retrofit, OkHttp,
Firebase, etc.); and a full-source grep for URL/socket/HTTP APIs across `app/src` returns
nothing. This is a structural guarantee, not a promise - the app cannot phone home even by
accident.

**What a from-scratch verification pass should do, in order:** open `android/` in Android
Studio, let it sync (this is where AndroidX/Compose/Room/CameraX/AGP actually get resolved and
compiled for the first time), fix whatever the compiler finds, run `:app:assembleDebug`, install
the APK, and manually walk the golden path (create a project → new inspection → capture photo →
analyze → editor → defect form → detail → generate PDF → Learning Center). Every phase's "known
gaps" section above says exactly what won't work yet when you get there.

### Phase 2 known gaps

- **Resize handles aren't wired to a gesture.** `AnnotationGeometry.resizeCorner` and
  `AnnotationEditorViewModel.resizeSelected` are implemented and tested, but the editor's
  pointer-input code doesn't yet detect a drag starting on a shape's corner handle - today you
  can move and delete a selected circle/rectangle, but not resize it by dragging a corner.
- **Revisiting a previously saved inspection to add another defect isn't wired.**
  `InspectionListScreen.onInspectionClick` is a documented no-op. The only working path to the
  Defect View editor right now is capture-photo → save a *new* inspection → editor → form.
- **"Import photo" is a documented no-op** (`NewInspectionScreen.onImportPhoto`) - only in-app
  CameraX capture is wired end-to-end.
- The annotated-photo composite (`AnnotationRenderer`) and the live Compose canvas
  (`DefectViewEditorScreen`) share drawing logic but are two separate implementations (one
  `android.graphics.Canvas`-based for the saved file, one Compose `DrawScope`-based for the live
  preview) - they were written to match, but nothing has verified they render identically.

### Phase 3 known gaps

- The classical-CV engine is a coarse, explainable heuristic (locally outlying edge density /
  color variance), not a trained defect classifier - see the doc comment on `ClassicalVisionEngine`
  for exactly what it does and doesn't claim. Swapping in a real LiteRT/ONNX model later only
  requires a new `VisionEngine` implementation at the single `AppContainer.visionEngine` wire-up
  point.

### Phase 4 known gaps

- **AI hand-off is now wired end to end**: "Analyze image" → detections seeded as editable
  `createdByAi = true` rectangle annotations in the Defect View editor (a distinct blue so
  they're visually identifiable, but freely movable/resizable/deletable like any annotation) →
  the highest-confidence detection is run through `InspectionReasoningEngine` to prefill the
  Defect Form's title/description/category/trade/severity/recommendation, with an explicit "AI
  suggestion (<confidence text>) — review and correct before saving" banner and
  `severityIsAiSuggested = true` recorded on the saved defect.
- **Project Knowledge now feeds the Defect Form's prefill.** The Defect Form composable in
  `DefectViewNavHost` loads `ProjectKnowledgeRepository.loadForReasoning(projectId)` via
  `produceState` and gates the form behind a brief loading state until that real DB read (plus
  the reasoning call) resolves - avoiding the earlier race where a `remember`-once seed would
  never see project knowledge loaded after first composition. A manual (no-AI) defect skips the
  load entirely, so it never waits on it.
- **Project Knowledge still has no management UI.** `ProjectKnowledgeEntity`, its DAO, and
  `ProjectKnowledgeRepository` are real and now actually consulted - but nothing in the app lets
  an inspector add a knowledge entry yet, so in practice every project currently has none and the
  reasoning engine still falls back to its generic "verify against the approved project
  specification and method statement" text. The plumbing works; there's just no data source
  feeding it yet.
- **Inspection templates are not implemented.** Spec section 10 also asks for reusable
  inspection templates (checklists per trade/area); nothing in this codebase covers that yet.

### Phase 5 known gaps

- **"Reject" isn't a distinct recorded action.** Spec section 8 lists approve/correct/reject as
  three inspector actions on an AI suggestion. Approve and correct both happen naturally (save a
  defect with the AI trade kept vs. changed); reject has no explicit UI path - an inspector who
  disagrees with a suggestion just doesn't create a defect from it, so no record of "this
  specific AI suggestion was looked at and dismissed" exists anywhere (not in `AIAnalysis`,
  which is written unconditionally when "Analyze image" runs, and not in `VerifiedExample`,
  which is only ever written on save).
- **Similarity search isn't surfaced anywhere yet.** `SimilaritySearch.topMatches` (tested) and
  `VerifiedExampleRepository.allForSimilaritySearch()` both exist, but nothing in the UI calls
  them - there's no "cases like this one" panel during defect creation.
- **No manual add / export / import for the verified dataset**, and no per-example "AI accuracy
  feedback" view - the Learning Center shows aggregate approved/corrected counts, not a
  browsable per-example history.
- The approve-vs-corrected judgment in `DefectFormViewModel.recordVerifiedExample` is a rough
  proxy (whether the saved trade differs from the AI's) - it doesn't consider whether title,
  category, or severity were also changed.

### Phase 6 known gaps

- **4 of the spec's 6 report types are not implemented**: Daily Inspection Report, Open Defects
  Report, Before/After Report, Project Quality Summary. `DefectViewPdfGenerator` (per-defect) and
  `DefectRegisterPdfGenerator` (per-project table) are real and complete; the other four would
  follow the same `PdfDocument` + `StaticLayout` pattern but nothing has been written for them.
- **Search/filter only covers the Defects list.** Projects and Inspections both already expose a
  DAO-level `search(query)` (Phase 1), but no screen calls it - Project and Inspection lists are
  still unfiltered.
- **"Export" is PDF sharing only**, via the standard Android share sheet
  (`Intent.ACTION_SEND` + `FileProvider`) - there is no CSV/JSON data export of the underlying
  records, and no bulk "export all reports" action.
- No project logo is drawn in either PDF - `Project.logoPath` exists in the schema but nothing
  in the app lets an inspector set one yet, so the reports always render without one.
