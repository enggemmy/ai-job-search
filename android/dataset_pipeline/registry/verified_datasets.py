"""Manifests for the five public datasets researched during this session (web search + direct
HTTP checks against Zenodo/GitHub/Mendeley/nature.com, run live, not recalled from memory).

Every field below is either a fact I directly verified (cited in `notes`) or explicitly left
`None`/`"UNKNOWN"` where I could not verify it - Zenodo, Mendeley Data, Kaggle, and Hugging Face
are all blocked by this sandbox's network policy, so several license texts could not be read
firsthand. See DATASET_ACQUISITION.md for exactly what a follow-up session (with normal
internet access) needs to check to close each LICENSE_REVIEW_REQUIRED gap.

This file is data, not code to execute against real images - `number_of_images` reflects the
published dataset size, not anything actually imported by this sandbox (see the module
docstring in ingest.py for why no real import has happened here).
"""

from __future__ import annotations

from datetime import date

from license_tiers import DatasetManifest

MBDD2025 = DatasetManifest(
    dataset_name="MBDD2025",
    official_source="Nature Scientific Data - 'A dataset of building surface defects collected "
                     "by UAVs for machine learning-based detection' (Nov 2025), "
                     "https://www.nature.com/articles/s41597-025-06318-5",
    download_source="https://zenodo.org/records/15622584",
    version="2025",
    license="UNKNOWN",
    commercial_use_allowed=None,
    redistribution_allowed=None,
    modification_allowed=None,
    attribution_required=True,   # safe default until verified - always cite the source paper
    citation_required=True,
    number_of_images=14471,
    annotation_type="BOUNDING_BOX",
    date_imported=date(1970, 1, 1),  # not actually imported - see module docstring
    notes=(
        "Verified real via WebSearch (peer-reviewed, Scientific Data, Nov 2025) and confirmed "
        "hosted on Zenodo via WebSearch. Could not read the Zenodo record's license field or "
        "the paper's Data Availability statement - both nature.com and zenodo.org are blocked "
        "by this sandbox's network egress policy (curl: connect_rejected / EGRESS_BLOCKED). "
        "14,471 images across 6 structure types (steel/RC/wood/brick/masonry/brick-concrete), "
        "5 defect categories (crack, abscission, leakage, corrosion, bulging), bounding-box "
        "annotated, pre-split into train/val/test by the dataset authors themselves."
    ),
)

CONVID = DatasetManifest(
    dataset_name="ConViD",
    official_source="Mendeley Data - 'ConViD — Concrete Visual Defect Dataset'",
    download_source="https://data.mendeley.com/datasets/fx3rthfjhy/2",
    version="2",
    license="UNKNOWN",
    commercial_use_allowed=None,
    redistribution_allowed=None,
    modification_allowed=None,
    attribution_required=True,
    citation_required=True,
    number_of_images=0,  # not stated in the sources found; verify at acquisition time
    annotation_type="CLASSIFICATION",
    date_imported=date(1970, 1, 1),
    notes=(
        "Verified real via WebSearch (Mendeley Data record found directly). Four concrete "
        "defect classes: honeycombing, voids, cracks, spalling; smartphone-captured (12MP, "
        "1:1 JPEG) across 3 site visits at different times of day. Mendeley Data is blocked by "
        "this sandbox's network policy - license and exact image count NOT verified."
    ),
)

CODEBRIM = DatasetManifest(
    dataset_name="CODEBRIM",
    official_source="CVPR 2019 - Mundt et al., 'Meta-learning Convolutional Neural "
                     "Architectures for Multi-target Concrete Defect Classification with the "
                     "COncrete DEfect BRidge IMage Dataset', https://arxiv.org/abs/1904.08486",
    download_source="https://zenodo.org/records/2620293",
    version="1",
    license="Non-commercial / educational use only (per dataset license file, confirmed via WebSearch)",
    commercial_use_allowed=False,
    redistribution_allowed=False,
    modification_allowed=True,
    attribution_required=True,
    citation_required=True,
    number_of_images=0,  # exact count not confirmed; 30 unique bridges, multi-class multi-target
    annotation_type="CLASSIFICATION",
    date_imported=date(1970, 1, 1),
    notes=(
        "Verified real and explicitly non-commercial via WebSearch summarizing the dataset's "
        "own license file. Five defect categories: crack, spallation, exposed reinforcement "
        "bar, efflorescence, corrosion - found across 30 unique real bridges. Zenodo itself is "
        "blocked from this sandbox, so the license text was read via search-engine summary, "
        "not fetched directly - re-confirm the exact license file before relying on this."
    ),
)

BD3 = DatasetManifest(
    dataset_name="BD3",
    official_source="ACM BuildSys 2024 - 'BD3: Building Defects Detection Dataset for "
                     "Benchmarking Computer Vision Techniques for Automated Defect "
                     "Identification', https://dl.acm.org/doi/10.1145/3671127.3698789",
    download_source="https://github.com/Praveenkottari/BD3-Dataset ; also on Kaggle "
                     "(https://www.kaggle.com/datasets/praveenkottari/bd3-dataset-for-building-defect-detection)",
    version="1",
    license="UNKNOWN",
    commercial_use_allowed=None,
    redistribution_allowed=None,
    modification_allowed=None,
    attribution_required=True,
    citation_required=True,
    number_of_images=3965,  # original set; a 14,000-image augmented set also exists
    annotation_type="CLASSIFICATION",
    date_imported=date(1970, 1, 1),
    notes=(
        "Verified real via WebSearch + direct fetch of the GitHub repo README (raw."
        "githubusercontent.com, reachable from this sandbox). 3,965 original images across 6 "
        "defect classes (algae, major crack, minor crack, peeling, spalling, stain) plus normal "
        "(non-defective) images; a 14,000-image augmented set also exists. Checked for a "
        "LICENSE file on the primary Praveenkottari/BD3-Dataset repo - none found on main or "
        "master. A related benchmarking repo (samy101/bd3-building-defects-detection-dataset) "
        "carries an MIT license, but that almost certainly covers the benchmarking CODE, not "
        "necessarily a grant of rights over the dataset images themselves - do not treat that "
        "MIT license as covering the images without confirming with the dataset authors or the "
        "ACM paper's own terms."
    ),
)

CUBIT_INSEG = DatasetManifest(
    dataset_name="CUBIT-InSeg",
    official_source="Automation in Construction 2026 - Zhao et al., 'From instance segmentation "
                     "to physical quantification: High-resolution UAV-based dataset for facade "
                     "defect assessment', https://doi.org/10.1016/j.autcon.2026.106980",
    download_source="Baidu Netdisk and Google Drive links listed in "
                     "https://github.com/BenyunZhao/CUBIT-InSeg (README)",
    version="1",
    license="CC BY 4.0 (Creative Commons Attribution 4.0 International)",
    commercial_use_allowed=True,
    redistribution_allowed=True,
    modification_allowed=True,
    attribution_required=True,
    citation_required=True,
    number_of_images=6996,
    annotation_type="SEGMENTATION_MASK",
    date_imported=date(1970, 1, 1),
    notes=(
        "The best-verified license of the five: the GitHub repo's own README states, "
        "verbatim, 'The CUBIT-InSeg dataset, including its images, annotations, and metadata "
        "distributed through the download links above, is licensed under the Creative Commons "
        "Attribution 4.0 International License (CC BY 4.0)' - fetched directly (raw."
        "githubusercontent.com/BenyunZhao/CUBIT-InSeg/master/README.md) in this session, not "
        "inferred. 6,996 UAV facade images at 4800x3200px, 62,187 instance-segmentation "
        "annotations across 2 classes (crack, spalling). This is the one dataset in this batch "
        "that plausibly reaches TIER_A once actually acquired - see classify_tier()."
    ),
)


ALL_DATASETS: list[DatasetManifest] = [MBDD2025, CONVID, CODEBRIM, BD3, CUBIT_INSEG]
