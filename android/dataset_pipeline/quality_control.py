"""Automated dataset quality control (spec section 9): corrupt/duplicate/near-duplicate
detection, resolution validation, label normalization, and class-balance analysis.

Image I/O uses Pillow (see requirements.txt). Nothing here silently drops a bad image - every
check returns a reason string so a rejected image is traceable, not just missing.
"""

from __future__ import annotations

import hashlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, UnidentifiedImageError


@dataclass
class ImageCheckResult:
    path: Path
    is_corrupt: bool
    width: int | None
    height: int | None
    sha256: str | None
    average_hash: int | None
    rejection_reasons: list[str]

    @property
    def is_usable(self) -> bool:
        return not self.is_corrupt and not self.rejection_reasons


def check_image(path: Path, min_width: int = 256, min_height: int = 256) -> ImageCheckResult:
    """Runs corrupt-detection, resolution validation, and computes both an exact-duplicate
    hash (sha256 of file bytes) and a perceptual hash (for near-duplicate detection) in one pass.
    """
    reasons: list[str] = []

    if not path.exists():
        return ImageCheckResult(path, True, None, None, None, None, ["file does not exist"])

    sha256 = hashlib.sha256(path.read_bytes()).hexdigest()

    try:
        with Image.open(path) as img:
            img.verify()
        # verify() invalidates the file handle; reopen to actually read pixel data for the hash.
        with Image.open(path) as img:
            width, height = img.size
            ahash = _average_hash(img)
    except (UnidentifiedImageError, OSError) as exc:
        return ImageCheckResult(path, True, None, None, sha256, None, [f"corrupt or unreadable image: {exc}"])

    if width < min_width or height < min_height:
        reasons.append(f"resolution {width}x{height} is below the minimum {min_width}x{min_height}")

    return ImageCheckResult(path, False, width, height, sha256, ahash, reasons)


def _average_hash(img: Image.Image, hash_size: int = 8) -> int:
    """A hand-rolled average hash (aHash): resize small, grayscale, threshold against the mean.
    Deliberately simple and dependency-free (no extra pip package beyond Pillow) - good enough
    to catch near-duplicates (recompressed/resized copies of the same photo), not meant to be a
    state-of-the-art perceptual hash.
    """
    small = img.convert("L").resize((hash_size, hash_size), Image.Resampling.LANCZOS)
    pixels = list(small.getdata())
    mean = sum(pixels) / len(pixels)
    bits = 0
    for i, p in enumerate(pixels):
        if p >= mean:
            bits |= 1 << i
    return bits


def hamming_distance(hash_a: int, hash_b: int) -> int:
    return bin(hash_a ^ hash_b).count("1")


def is_near_duplicate(hash_a: int, hash_b: int, threshold: int = 5) -> bool:
    return hamming_distance(hash_a, hash_b) <= threshold


def find_exact_duplicates(results: list[ImageCheckResult]) -> dict[str, list[Path]]:
    """Groups usable images by exact sha256 match. Returns only groups with 2+ members."""
    by_hash: dict[str, list[Path]] = {}
    for r in results:
        if r.is_usable and r.sha256:
            by_hash.setdefault(r.sha256, []).append(r.path)
    return {h: paths for h, paths in by_hash.items() if len(paths) > 1}


def find_near_duplicate_groups(results: list[ImageCheckResult], threshold: int = 5) -> list[list[Path]]:
    """Naive O(n^2) near-duplicate clustering via union-find over the average hash. Fine for a
    per-dataset ingestion batch; not intended for the full multi-hundred-thousand-image corpus
    in one call (see dataset_split.py's grouping, which reuses these clusters)."""
    usable = [r for r in results if r.is_usable and r.average_hash is not None]
    parent = {r.path: r.path for r in usable}

    def find(p: Path) -> Path:
        while parent[p] != p:
            parent[p] = parent[parent[p]]
            p = parent[p]
        return p

    def union(a: Path, b: Path) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for i in range(len(usable)):
        for j in range(i + 1, len(usable)):
            if is_near_duplicate(usable[i].average_hash, usable[j].average_hash, threshold):
                union(usable[i].path, usable[j].path)

    groups: dict[Path, list[Path]] = {}
    for r in usable:
        groups.setdefault(find(r.path), []).append(r.path)
    return [g for g in groups.values() if len(g) > 1]


def normalize_label(raw_label: str) -> str:
    return " ".join(raw_label.strip().lower().split())


@dataclass
class ClassBalanceReport:
    counts: dict[str, int]
    total: int
    imbalance_warnings: list[str]


def class_balance(defect_types: list[str], warn_above_fraction: float = 0.5, warn_below_count: int = 5) -> ClassBalanceReport:
    """Real, actionable imbalance detection (spec section 10) - not just a count dump.

    Flags any class that dominates the dataset (> warn_above_fraction of all annotations) and
    any class that's nearly absent (< warn_below_count examples), both of which bias a trained
    model the same way the spec's own example describes (70% cracks, 1% doors/tiles/HVAC).
    """
    counts = Counter(defect_types)
    total = sum(counts.values())
    warnings: list[str] = []

    for label, count in counts.items():
        fraction = count / total if total else 0
        if fraction > warn_above_fraction:
            warnings.append(f"{label} is {fraction:.0%} of all annotations ({count}/{total}) - severe over-representation")
        if 0 < count < warn_below_count:
            warnings.append(f"{label} has only {count} annotation(s) - too few to train or validate on reliably")

    return ClassBalanceReport(counts=dict(counts), total=total, imbalance_warnings=warnings)
