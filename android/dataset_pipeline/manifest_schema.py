"""Per-image annotation record (spec section 8) - normalized coordinates, multiple annotation
types, validated against the taxonomy so a typo or an unmapped label fails loudly instead of
silently entering the dataset.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum

from taxonomy import get_defect_type


class SourceType(str, Enum):
    PUBLIC_DATASET = "PUBLIC_DATASET"
    PROJECT_INSPECTION = "PROJECT_INSPECTION"
    USER_MANUAL_ANNOTATION = "USER_MANUAL_ANNOTATION"


class AnnotationType(str, Enum):
    CLASSIFICATION = "CLASSIFICATION"
    BOUNDING_BOX = "BOUNDING_BOX"
    SEGMENTATION_MASK = "SEGMENTATION_MASK"
    POINT = "POINT"


class VerificationStatus(str, Enum):
    """The four outcomes of user-in-the-loop learning (spec section 11) - an AI prediction is
    never ground truth on its own; only these four states are meaningful for training."""
    UNVERIFIED = "UNVERIFIED"
    VERIFIED_POSITIVE = "VERIFIED_POSITIVE"      # AI predicted, user accepted
    FALSE_POSITIVE = "FALSE_POSITIVE"            # AI predicted, user rejected
    CORRECTED_SAMPLE = "CORRECTED_SAMPLE"        # AI predicted, user changed the label
    MISSED_DEFECT = "MISSED_DEFECT"              # AI missed it, consultant drew it manually


@dataclass
class BoundingBoxGeometry:
    x: float
    y: float
    width: float
    height: float

    def validate(self) -> list[str]:
        errors = []
        for name, value in (("x", self.x), ("y", self.y), ("width", self.width), ("height", self.height)):
            if not (0.0 <= value <= 1.0):
                errors.append(f"{name}={value} is outside the normalized 0..1 range")
        if self.width <= 0 or self.height <= 0:
            errors.append(f"width/height must be positive, got width={self.width} height={self.height}")
        if self.x + self.width > 1.0 + 1e-6:
            errors.append(f"x+width={self.x + self.width} exceeds 1.0")
        if self.y + self.height > 1.0 + 1e-6:
            errors.append(f"y+height={self.y + self.height} exceeds 1.0")
        return errors


@dataclass
class PointGeometry:
    x: float
    y: float

    def validate(self) -> list[str]:
        errors = []
        for name, value in (("x", self.x), ("y", self.y)):
            if not (0.0 <= value <= 1.0):
                errors.append(f"{name}={value} is outside the normalized 0..1 range")
        return errors


@dataclass
class PolygonGeometry:
    """Segmentation mask represented as a normalized polygon - a practical, storage-cheap
    stand-in for a raster mask, matching the spec's normalized-coordinate convention."""
    points: list[tuple[float, float]] = field(default_factory=list)

    def validate(self) -> list[str]:
        errors = []
        if len(self.points) < 3:
            errors.append(f"polygon needs at least 3 points, got {len(self.points)}")
        for i, (x, y) in enumerate(self.points):
            if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
                errors.append(f"point {i} ({x}, {y}) is outside the normalized 0..1 range")
        return errors


Geometry = BoundingBoxGeometry | PointGeometry | PolygonGeometry | None


@dataclass
class AnnotationRecord:
    image_id: str
    dataset: str
    source_type: SourceType
    discipline: str
    category: str
    defect_type: str
    original_label: str
    annotation_type: AnnotationType
    geometry: Geometry
    severity: str = "UNKNOWN"
    confidence: float = 1.0
    element: str = "UNKNOWN"
    area: str = "UNKNOWN"
    stage: str = "UNKNOWN"
    verified: bool = False
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED

    def validate(self) -> list[str]:
        errors: list[str] = []

        if not self.image_id:
            errors.append("image_id is required")
        if not self.dataset:
            errors.append("dataset is required")

        if get_defect_type(self.defect_type) is None:
            errors.append(f"defect_type {self.defect_type!r} is not in the DEFECT VIEW taxonomy")

        if not (0.0 <= self.confidence <= 1.0):
            errors.append(f"confidence={self.confidence} must be in 0..1")

        if self.annotation_type == AnnotationType.CLASSIFICATION and self.geometry is not None:
            errors.append("CLASSIFICATION annotations must not carry geometry")
        elif self.annotation_type == AnnotationType.BOUNDING_BOX:
            if not isinstance(self.geometry, BoundingBoxGeometry):
                errors.append("BOUNDING_BOX annotation requires a BoundingBoxGeometry")
            else:
                errors.extend(self.geometry.validate())
        elif self.annotation_type == AnnotationType.POINT:
            if not isinstance(self.geometry, PointGeometry):
                errors.append("POINT annotation requires a PointGeometry")
            else:
                errors.extend(self.geometry.validate())
        elif self.annotation_type == AnnotationType.SEGMENTATION_MASK:
            if not isinstance(self.geometry, PolygonGeometry):
                errors.append("SEGMENTATION_MASK annotation requires a PolygonGeometry")
            else:
                errors.extend(self.geometry.validate())

        if self.verified and self.verification_status == VerificationStatus.UNVERIFIED:
            errors.append("verified=True is inconsistent with verification_status=UNVERIFIED")

        return errors

    def is_valid(self) -> bool:
        return len(self.validate()) == 0
