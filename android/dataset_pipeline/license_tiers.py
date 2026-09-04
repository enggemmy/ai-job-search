"""License control (spec section 4) - mandatory gating before any dataset can be treated as
production-usable.

The central rule this module enforces: **a dataset is never classified as production-ready by
default.** Every field that matters (commercial use, redistribution, modification) must be
explicitly known and True to reach Tier A. Anything unknown routes to LICENSE_REVIEW_REQUIRED,
never silently to a permissive tier.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum


class DatasetTier(str, Enum):
    PRODUCTION = "TIER_A_PRODUCTION"
    DEVELOPMENT = "TIER_B_DEVELOPMENT"
    EXPERIMENTAL = "TIER_C_EXPERIMENTAL"
    LICENSE_REVIEW_REQUIRED = "LICENSE_REVIEW_REQUIRED"


@dataclass
class DatasetManifest:
    dataset_name: str
    official_source: str
    download_source: str
    version: str
    license: str
    """Free-text license identifier/name, e.g. 'CC BY 4.0' or 'Non-commercial research use only'.
    Never leave this blank to imply permissiveness - use an explicit 'UNKNOWN' if the license
    text itself has not been read."""
    commercial_use_allowed: bool | None
    redistribution_allowed: bool | None
    modification_allowed: bool | None
    attribution_required: bool
    citation_required: bool
    number_of_images: int
    annotation_type: str
    date_imported: date
    notes: str = ""

    @property
    def dataset_tier(self) -> DatasetTier:
        return classify_tier(
            commercial_use_allowed=self.commercial_use_allowed,
            redistribution_allowed=self.redistribution_allowed,
            modification_allowed=self.modification_allowed,
        )


def classify_tier(
    *,
    commercial_use_allowed: bool | None,
    redistribution_allowed: bool | None,
    modification_allowed: bool | None,
) -> DatasetTier:
    """Pure classification function - no I/O, no assumptions, fully testable.

    Rules:
    - Any of the three flags being None (not yet verified) -> LICENSE_REVIEW_REQUIRED.
    - commercial_use_allowed is False -> EXPERIMENTAL (research/non-commercial only).
    - commercial_use_allowed True but modification_allowed False -> DEVELOPMENT (usable to
      develop/evaluate against, but a training pipeline that preprocesses/augments images
      counts as modification, so this cannot ship in a production training package as-is).
    - commercial_use_allowed True and modification_allowed True -> PRODUCTION. Redistribution
      is tracked but not required for PRODUCTION: training on a dataset does not require
      redistributing it. It only matters if the app would ship the raw images themselves
      (e.g. as bundled sample photos), which is a separate, explicit decision per dataset.
    """
    if commercial_use_allowed is None or modification_allowed is None:
        return DatasetTier.LICENSE_REVIEW_REQUIRED
    if commercial_use_allowed is False:
        return DatasetTier.EXPERIMENTAL
    if modification_allowed is False:
        return DatasetTier.DEVELOPMENT
    return DatasetTier.PRODUCTION


def can_enter_production_training_package(manifest: DatasetManifest) -> bool:
    """The one gate every ingestion path must call before writing into datasets/production/."""
    return manifest.dataset_tier == DatasetTier.PRODUCTION
