"""Dataset ingestion orchestration (spec section 21's pipeline, condensed into one call):

    LICENSE VALIDATION -> IMAGE VALIDATION -> DEDUPLICATION -> LABEL NORMALIZATION ->
    TAXONOMY MAPPING -> QUALITY CONTROL -> gated write into datasets/{tier}/...

No dataset has actually been run through this in production, because no real dataset's images
are reachable from this sandbox (see registry/verified_datasets.py). This module is exercised
end-to-end in tests/test_ingest.py against synthetic, programmatically generated images, which
proves the *logic* is correct without pretending any real corpus has been imported.
"""

from __future__ import annotations

import json
import shutil
from dataclasses import asdict, dataclass
from pathlib import Path

from label_mapping import LabelMapping, lookup, NON_DEFECT_LABEL
from license_tiers import DatasetManifest, DatasetTier
from manifest_schema import AnnotationRecord
from quality_control import ImageCheckResult, check_image, find_exact_duplicates, find_near_duplicate_groups


@dataclass
class IngestResult:
    dataset_name: str
    tier: DatasetTier
    total_candidate_images: int
    accepted_images: int
    rejected_images: dict[str, str]  # path -> reason
    exact_duplicate_groups: int
    near_duplicate_groups: int
    unmapped_labels: set[str]
    written_to: Path | None


def _tier_directory(datasets_root: Path, tier: DatasetTier) -> str:
    return {
        DatasetTier.PRODUCTION: "production",
        DatasetTier.DEVELOPMENT: "development",
        DatasetTier.EXPERIMENTAL: "experimental",
        DatasetTier.LICENSE_REVIEW_REQUIRED: "experimental",  # never production by default
    }[tier]


def ingest_dataset(
    manifest: DatasetManifest,
    image_paths: list[Path],
    raw_labels: dict[str, str],  # image path (as str) -> the dataset's own original label
    datasets_root: Path,
    min_width: int = 256,
    min_height: int = 256,
    dry_run: bool = True,
) -> IngestResult:
    """Runs the full QC/mapping pipeline. With dry_run=True (the default), nothing is written -
    the caller gets a full report to review before committing any files. LICENSE_REVIEW_REQUIRED
    and EXPERIMENTAL datasets are still processed (so the report is useful) but are routed to
    datasets/experimental/ regardless of dry_run, never datasets/production/.
    """
    tier = manifest.dataset_tier

    checks: list[ImageCheckResult] = [check_image(p, min_width, min_height) for p in image_paths]
    rejected: dict[str, str] = {}
    for r in checks:
        if r.is_corrupt:
            rejected[str(r.path)] = "; ".join(r.rejection_reasons) or "corrupt image"
        elif r.rejection_reasons:
            rejected[str(r.path)] = "; ".join(r.rejection_reasons)

    exact_dupes = find_exact_duplicates(checks)
    for group in exact_dupes.values():
        for dup_path in group[1:]:  # keep the first, reject the rest
            rejected.setdefault(str(dup_path), "exact duplicate of another image in this batch")

    near_dupe_groups = find_near_duplicate_groups([c for c in checks if str(c.path) not in rejected])

    unmapped_labels: set[str] = set()
    for path in image_paths:
        if str(path) in rejected:
            continue
        raw_label = raw_labels.get(str(path))
        if raw_label is None:
            rejected[str(path)] = "no label provided"
            continue
        mapping = lookup(manifest.dataset_name, raw_label)
        if mapping is None:
            unmapped_labels.add(raw_label)

    accepted = [p for p in image_paths if str(p) not in rejected]

    written_to: Path | None = None
    if not dry_run and accepted:
        tier_dir = datasets_root / _tier_directory(datasets_root, tier) / manifest.dataset_name
        tier_dir.mkdir(parents=True, exist_ok=True)
        for p in accepted:
            shutil.copy2(p, tier_dir / p.name)
        (tier_dir / "manifest.json").write_text(
            json.dumps(_manifest_to_json(manifest), indent=2, default=str)
        )
        written_to = tier_dir

    return IngestResult(
        dataset_name=manifest.dataset_name,
        tier=tier,
        total_candidate_images=len(image_paths),
        accepted_images=len(accepted),
        rejected_images=rejected,
        exact_duplicate_groups=len(exact_dupes),
        near_duplicate_groups=len(near_dupe_groups),
        unmapped_labels=unmapped_labels,
        written_to=written_to,
    )


def _manifest_to_json(manifest: DatasetManifest) -> dict:
    data = asdict(manifest)
    data["dataset_tier"] = manifest.dataset_tier.value
    return data


def build_annotation_record(
    manifest: DatasetManifest,
    image_id: str,
    raw_label: str,
    discipline: str,
    category: str,
) -> AnnotationRecord | None:
    """Resolves a raw dataset label through label_mapping and builds a classification-only
    AnnotationRecord (geometry is added separately once a real bounding box/mask is available -
    this just proves the label -> taxonomy resolution path end to end)."""
    from manifest_schema import AnnotationType, SourceType

    mapping = lookup(manifest.dataset_name, raw_label)
    if mapping is None or mapping.defect_view_label == NON_DEFECT_LABEL:
        return None

    return AnnotationRecord(
        image_id=image_id,
        dataset=manifest.dataset_name,
        source_type=SourceType.PUBLIC_DATASET,
        discipline=discipline,
        category=category,
        defect_type=mapping.defect_view_label,
        original_label=raw_label,
        annotation_type=AnnotationType.CLASSIFICATION,
        geometry=None,
        confidence=mapping.mapping_confidence,
    )
