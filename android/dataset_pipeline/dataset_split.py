"""Grouped train/validation/test splitting (spec section 9): images from the same inspection
sequence, project, location, or near-duplicate cluster must land in the same split, or the
model effectively gets to see (a near-copy of) its test answers during training.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from enum import Enum


class Split(str, Enum):
    TRAIN = "TRAIN"
    VALIDATION = "VALIDATION"
    TEST = "TEST"


DEFAULT_RATIOS: dict[Split, float] = {
    Split.TRAIN: 0.70,
    Split.VALIDATION: 0.15,
    Split.TEST: 0.15,
}


@dataclass(frozen=True)
class GroupedItem:
    """One image (or annotation), tagged with the group it must not be separated from -
    e.g. f"{project_id}:{location}" for project inspection photos, or a near-duplicate
    cluster id from quality_control.find_near_duplicate_groups for public-dataset images."""
    item_id: str
    group_key: str


def assign_split(group_key: str, ratios: dict[Split, float] = DEFAULT_RATIOS, salt: str = "defect-view-v1") -> Split:
    """Deterministic, stable assignment: the same group_key always lands in the same split,
    across repeated runs and across process restarts, without needing to persist any state -
    it's a hash of the group key, not a random draw.
    """
    total = sum(ratios.values())
    if abs(total - 1.0) > 1e-6:
        raise ValueError(f"split ratios must sum to 1.0, got {total}")

    digest = hashlib.sha256(f"{salt}:{group_key}".encode("utf-8")).digest()
    # Use the first 4 bytes as a uniform-ish integer in [0, 2**32).
    bucket = int.from_bytes(digest[:4], "big") / 2**32

    cumulative = 0.0
    for split in (Split.TRAIN, Split.VALIDATION, Split.TEST):
        cumulative += ratios[split]
        if bucket < cumulative:
            return split
    return Split.TEST  # floating-point edge case at bucket == 1.0


def split_items(items: list[GroupedItem], ratios: dict[Split, float] = DEFAULT_RATIOS) -> dict[Split, list[GroupedItem]]:
    result: dict[Split, list[GroupedItem]] = {s: [] for s in Split}
    for item in items:
        result[assign_split(item.group_key, ratios)].append(item)
    return result


def split_summary(assignment: dict[Split, list[GroupedItem]]) -> dict[str, float]:
    total = sum(len(v) for v in assignment.values())
    if total == 0:
        return {s.value: 0.0 for s in Split}
    return {s.value: len(assignment[s]) / total for s in Split}
