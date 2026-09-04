# DEFECT VIEW Dataset Pipeline

Offline dataset ingestion, license control, taxonomy mapping, quality control, and
train/validation/test splitting for the DEFECT VIEW local AI engine (see the master dataset
integration prompt). Python, not Kotlin - dataset engineering (image hashing, dedup, split
logic) is standard, mature tooling in Python, and keeping it separate from `android/app` and
`android/domain` means it never touches the Android build.

**This has been run, for real, in this sandbox** - `python3 -m unittest discover -s tests -t .`
passes 67 tests, including end-to-end ingestion tests against synthetic (programmatically
generated) images. That is a materially different, stronger kind of verification than the rest
of this repo's `android/app` module, which has never compiled (see `android/NETWORK_LIMITATIONS.md`).

## Why there's no real dataset in `android/datasets/` yet

Zenodo, Mendeley Data, Kaggle, and Hugging Face - the actual hosts for every dataset named in
the integration prompt - are all blocked by this sandbox's network egress policy (verified with
live requests, `connect_rejected` / `EGRESS_BLOCKED`, not assumed). `nature.com` is blocked too.
`raw.githubusercontent.com` is reachable, which is how the facts in `registry/verified_datasets.py`
and `DATASET_ACQUISITION.md` were actually confirmed (GitHub README/LICENSE files fetched live
during this session), but no image-hosting platform for bulk dataset downloads is reachable.

**What this means concretely:** this pipeline's *logic* is real and tested. No dataset's actual
images have been imported here, and `android/datasets/production|development|experimental/` are
empty except for `.gitkeep`. See `DATASET_ACQUISITION.md` for exactly what to run, and where,
to actually pull each dataset.

## Layout

- `taxonomy.py` - the 87-class DEFECT VIEW taxonomy (spec section 6), as an extensible registry
  (`register_defect_type()`), not a closed enum.
- `license_tiers.py` - `DatasetManifest` (spec section 4's required fields) and the Tier A/B/C/
  LICENSE_REVIEW_REQUIRED classification rule. Unverified license terms never default to a
  permissive tier.
- `manifest_schema.py` - `AnnotationRecord` (spec section 8): classification / bounding-box /
  point / segmentation-polygon geometry, all normalized 0..1, validated against the taxonomy.
- `label_mapping.py` - original-label -> DEFECT VIEW taxonomy mappings for the 5 datasets
  researched this session, each with an explicit `mapping_confidence` and `mapping_notes` -
  including the low-confidence ones (e.g. CODEBRIM's "efflorescence" -> `STAINING` at 0.4
  confidence), never silently forced.
- `quality_control.py` - corrupt-image detection, exact duplicate (sha256) and near-duplicate
  (hand-rolled average-hash) detection, resolution validation, label normalization, class-balance
  analysis with real imbalance warnings.
- `dataset_split.py` - deterministic, grouped 70/15/15 train/validation/test split (a group -
  project+location, inspection sequence, or near-duplicate cluster - never gets split across sets).
- `ingest.py` - orchestrates the above into one call; gates writes so nothing ever lands in
  `datasets/production/` unless its manifest's tier is genuinely `TIER_A_PRODUCTION`.
- `cli.py` - `registry`, `check-images DIR`, `class-balance LABELS_FILE`.
- `registry/verified_datasets.py` - the 5 real, session-researched dataset manifests (MBDD2025,
  ConViD, CODEBRIM, BD3, CUBIT-InSeg) with their actual verified (or honestly unverified) license
  status.
- `tests/` - 67 unit/integration tests, all passing (`python3 -m unittest discover -s tests -t .`).

## Running it

```
pip install -r requirements.txt
python3 -m unittest discover -s tests -t .   # 67 tests
python3 cli.py registry                       # dataset manifests + license tiers
python3 cli.py check-images /path/to/photos    # QC over real images once you have some
```

## What's genuinely NOT built here

- **No real dataset has been imported.** See above.
- **No Android "Dataset Manager" admin UI** (spec section 16) - this session built the pipeline
  the UI would eventually call, not the UI itself. The Kotlin `android/app` module has no
  knowledge of this pipeline yet; wiring them together (e.g. a Room table mirroring
  `AnnotationRecord`, an import screen) is a distinct, unstarted piece of work.
- **No model training.** Sections 14/15/21's progressive model strategy (Model 1 through 6)
  needs a GPU, a training framework (PyTorch/TensorFlow), and real labeled data - none of which
  exist in this sandbox. `manifest_schema.py`'s `AnnotationRecord` and `dataset_split.py`'s
  grouped splits are exactly what a training script would consume once one exists.
- **Dataset versioning (spec section 18)** and the **dataset quality score (spec section 19)**
  are not implemented - both are real, buildable features, just not built in this pass.
- **Project data isolation (spec section 17)** is partially covered by `dataset_split.py`'s
  grouping (a project's images can be grouped so they never leak across train/test) but there's
  no enforcement yet that a project's inspection images stay out of the *global* training set
  without explicit administrator approval.
