"""Dataset quality score (spec section 19): a composite, explainable score from real inputs -
never a single opaque number. Every sub-score is computed from something this pipeline already
measures elsewhere (image checks, annotation validation, label mapping confidence, license tier),
so the score is reproducible and each component can be shown to a developer on its own.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from label_mapping import LabelMapping
from license_tiers import DatasetTier
from manifest_schema import AnnotationRecord
from quality_control import ImageCheckResult

_LICENSE_CONFIDENCE_BY_TIER: dict[DatasetTier, float] = {
    DatasetTier.PRODUCTION: 1.0,
    DatasetTier.DEVELOPMENT: 0.6,
    DatasetTier.EXPERIMENTAL: 0.3,
    DatasetTier.LICENSE_REVIEW_REQUIRED: 0.0,
}

DEFAULT_WEIGHTS: dict[str, float] = {
    "image_quality": 0.25,
    "annotation_quality": 0.25,
    "defect_diversity": 0.20,
    "license_confidence": 0.20,
    "label_reliability": 0.10,
}


@dataclass
class QualityScoreBreakdown:
    image_quality: float
    annotation_quality: float
    defect_diversity: float
    license_confidence: float
    label_reliability: float

    @property
    def composite(self) -> float:
        weights = DEFAULT_WEIGHTS
        total = (
            self.image_quality * weights["image_quality"]
            + self.annotation_quality * weights["annotation_quality"]
            + self.defect_diversity * weights["defect_diversity"]
            + self.license_confidence * weights["license_confidence"]
            + self.label_reliability * weights["label_reliability"]
        )
        return round(total * 100, 1)

    def as_dict(self) -> dict[str, float]:
        return {
            "image_quality": round(self.image_quality * 100, 1),
            "annotation_quality": round(self.annotation_quality * 100, 1),
            "defect_diversity": round(self.defect_diversity * 100, 1),
            "license_confidence": round(self.license_confidence * 100, 1),
            "label_reliability": round(self.label_reliability * 100, 1),
            "composite": self.composite,
        }


def image_quality_score(checks: list[ImageCheckResult]) -> float:
    """Fraction of candidate images that are actually usable (not corrupt, meets minimum
    resolution) - real-world-conditions and resolution both fold into "usable" already via
    quality_control.check_image."""
    if not checks:
        return 0.0
    usable = sum(1 for c in checks if c.is_usable)
    return usable / len(checks)


def annotation_quality_score(records: list[AnnotationRecord]) -> float:
    if not records:
        return 0.0
    valid = sum(1 for r in records if r.is_valid())
    return valid / len(records)


def defect_diversity_score(class_distribution: dict[str, int]) -> float:
    """Normalized Shannon entropy of the class distribution - 1.0 means every class is equally
    represented, 0.0 means a single class dominates completely. This is what makes a 70%-cracks/
    1%-doors corpus score badly here, independent of the raw imbalance warnings in
    quality_control.class_balance (which flag it in words; this turns it into a comparable number).
    """
    total = sum(class_distribution.values())
    num_classes = len(class_distribution)
    if total == 0 or num_classes <= 1:
        return 0.0

    entropy = 0.0
    for count in class_distribution.values():
        if count == 0:
            continue
        p = count / total
        entropy -= p * math.log2(p)

    max_entropy = math.log2(num_classes)
    return entropy / max_entropy if max_entropy > 0 else 0.0


def license_confidence_score(tier: DatasetTier) -> float:
    return _LICENSE_CONFIDENCE_BY_TIER[tier]


def label_reliability_score(mappings: list[LabelMapping]) -> float:
    if not mappings:
        return 0.0
    return sum(m.mapping_confidence for m in mappings) / len(mappings)


def compute_quality_score(
    image_checks: list[ImageCheckResult],
    annotation_records: list[AnnotationRecord],
    class_distribution: dict[str, int],
    tier: DatasetTier,
    label_mappings: list[LabelMapping],
) -> QualityScoreBreakdown:
    return QualityScoreBreakdown(
        image_quality=image_quality_score(image_checks),
        annotation_quality=annotation_quality_score(annotation_records),
        defect_diversity=defect_diversity_score(class_distribution),
        license_confidence=license_confidence_score(tier),
        label_reliability=label_reliability_score(label_mappings),
    )
