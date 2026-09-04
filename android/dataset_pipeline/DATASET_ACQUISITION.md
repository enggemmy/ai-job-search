# Dataset Acquisition Checklist

Everything below was verified live during this session (WebSearch + direct HTTP requests to
`raw.githubusercontent.com`, which is reachable from this sandbox) - not recalled from training
data, not assumed from the integration prompt's own description. Where a fact could not be
verified (mostly: exact license text on Zenodo/Mendeley, which are network-blocked here), that
is stated explicitly rather than guessed.

Run this checklist on a machine with normal internet access. For each dataset: download,
re-verify the license against the primary source (not this document), fill in the real
`commercial_use_allowed` / `redistribution_allowed` / `modification_allowed` fields in
`registry/verified_datasets.py`, then run `python3 cli.py registry` again - the tier will update
automatically once those fields are set.

---

## 1. CUBIT-InSeg — start here, best-verified license

- **Paper:** Zhao et al., "From instance segmentation to physical quantification: High-resolution
  UAV-based dataset for façade defect assessment", *Automation in Construction* 188 (2026) 106980.
  https://doi.org/10.1016/j.autcon.2026.106980
- **Repo:** https://github.com/BenyunZhao/CUBIT-InSeg
- **Download:** Baidu Netdisk (link + password in the repo README) or Google Drive (link in the
  repo README) - both listed at the top of the README, neither reachable from this sandbox.
- **License:** **Confirmed CC BY 4.0**, verbatim from the repo's own README (fetched directly in
  this session): *"The CUBIT-InSeg dataset, including its images, annotations, and metadata
  distributed through the download links above, is licensed under the Creative Commons
  Attribution 4.0 International License (CC BY 4.0)."*
- **Scale:** 6,996 UAV facade images at 4800×3200px, 62,187 instance-segmentation annotations,
  2 classes (crack, spalling).
- **To do:** Download via Google Drive or Baidu Netdisk. Confirm the CC BY 4.0 statement still
  matches on arrival (license pages change). Attribution: cite the paper above. Once confirmed,
  set `commercial_use_allowed=True`, `redistribution_allowed=True`, `modification_allowed=True`
  in the manifest - this is the one dataset here that plausibly reaches TIER_A_PRODUCTION as-is.

## 2. CODEBRIM — real, but explicitly non-commercial

- **Paper:** Mundt et al., "Meta-learning Convolutional Neural Architectures for Multi-target
  Concrete Defect Classification with the COncrete DEfect BRidge IMage Dataset", CVPR 2019.
  https://arxiv.org/abs/1904.08486
- **Download:** https://zenodo.org/records/2620293 (blocked from this sandbox)
- **License:** Non-commercial / educational use only, per the dataset's own license file (found
  via WebSearch summary, not fetched directly - **re-confirm the exact license file on arrival**,
  this is the one fact in this document sourced from a search summary rather than a primary
  fetch).
- **Scale:** 30 unique real bridges, 5 defect classes (crack, spallation, exposed reinforcement
  bar, efflorescence, corrosion).
- **To do:** Download from Zenodo, read the actual `LICENSE` file included in the archive. Given
  the non-commercial signal, expect this to land in `datasets/experimental/` (Tier C) - do not
  promote it to production without a licensing conversation with the dataset authors.

## 3. MBDD2025

- **Paper:** "A dataset of building surface defects collected by UAVs for machine learning-based
  detection", *Scientific Data*, Nov 2025. https://www.nature.com/articles/s41597-025-06318-5
  (blocked from this sandbox - could not read the Data Availability section)
- **Download:** https://zenodo.org/records/15622584 (blocked from this sandbox)
- **License:** **UNKNOWN** - not verified. Read the Zenodo record's license field directly.
- **Scale:** 14,471 images, 6 structure types (steel/RC/wood/brick/masonry/brick-concrete),
  5 defect categories (crack, abscission, leakage, corrosion, bulging), bounding-box annotated,
  pre-split by the dataset's own authors.
- **To do:** Download from Zenodo, read the license field on the record page itself (Zenodo
  records show this explicitly). This is the single highest-value dataset by volume if the
  license turns out to be permissive - prioritize checking this one.

## 4. ConViD

- **Download:** https://data.mendeley.com/datasets/fx3rthfjhy/2 (blocked from this sandbox)
- **License:** **UNKNOWN** - not verified.
- **Scale:** 4 concrete defect classes (honeycombing, voids, cracks, spalling), smartphone-
  captured (12MP, 1:1 JPEG), 3 site visits at different times of day. Exact image count not
  found in the sources searched this session - confirm on the Mendeley page.
- **To do:** Mendeley Data listings typically show a CC BY 4.0 or similar license directly on
  the page - check there first before assuming anything.

## 5. BD3

- **Paper:** "BD3: Building Defects Detection Dataset for Benchmarking Computer Vision Techniques
  for Automated Defect Identification", ACM BuildSys 2024. https://dl.acm.org/doi/10.1145/3671127.3698789
- **Primary repo:** https://github.com/Praveenkottari/BD3-Dataset (README fetched directly this
  session; **no LICENSE file found** on `main` or `master`)
- **Also on Kaggle:** https://www.kaggle.com/datasets/praveenkottari/bd3-dataset-for-building-defect-detection
  (blocked from this sandbox)
- **Related benchmarking repo:** https://github.com/samy101/bd3-building-defects-detection-dataset
  carries an **MIT license** (fetched directly), but this almost certainly covers that repo's
  benchmarking *code*, not a grant of rights over the BD3 *images* themselves - do not treat this
  as clearing the dataset's license. Confirm with the ACM paper's own data-availability terms and/or
  the Kaggle listing's license field.
- **Scale:** 3,965 original images, 6 defect classes (algae, major crack, minor crack, peeling,
  spalling, stain) + normal (non-defective) images; a 14,000-image augmented set also exists.
- **To do:** Check the Kaggle listing's license field (Kaggle requires dataset uploaders to
  declare one) - that's the fastest real signal, faster than emailing the authors.

---

## After acquisition: what to actually run

1. Update the relevant `DatasetManifest` in `registry/verified_datasets.py` with the confirmed
   license fields and real `number_of_images`.
2. Run `python3 cli.py check-images /path/to/downloaded/images` to get corrupt/duplicate/
   resolution rejections before anything else touches the images.
3. Build the `raw_labels: dict[str, str]` mapping (image path -> the dataset's own label) from
   whatever annotation format the dataset ships (CSV, XML, COCO JSON, folder-per-class, etc.) -
   this pipeline doesn't include a per-dataset annotation parser since the five datasets here use
   five different formats; write one small loader per dataset, feeding into
   `ingest.ingest_dataset()`.
4. Run `ingest_dataset(..., dry_run=True)` first, read the `IngestResult` (rejected images,
   unmapped labels, duplicate groups), fix what needs fixing, then re-run with `dry_run=False`.
5. Run `quality_control.class_balance()` across everything imported so far and read the
   `imbalance_warnings` before treating the corpus as training-ready.
