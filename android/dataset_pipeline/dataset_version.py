"""Dataset versioning (spec section 18): every training run gets an immutable, named snapshot
- DEFECT_VIEW_DATASET_v0.1, v0.2, ... - recording exactly what went into it. Versions are
append-only: recording v0.2 never touches or hides v0.1.

This is deliberately a different concept from the Android app's own `ModelVersionEntity`
(android/app - see Phase 5 of the app's README): that table versions the *on-device
similarity-search snapshot* an inspector's phone is currently using. This module versions the
*training dataset composition* one level upstream - which public/project datasets went into a
given training run, how many images/annotations, and what came out of evaluating it. A real
training pipeline would produce one of these per run and hand the resulting model version to the
app's own ModelVersion table.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path


@dataclass(frozen=True)
class DatasetVersion:
    dataset_version: str  # e.g. "0.1" - the number; version_label derives from this
    datasets_included: list[str]
    number_of_images: int
    number_of_annotations: int
    class_distribution: dict[str, int]
    training_date: date | None = None
    model_version: str | None = None
    validation_accuracy: float | None = None
    test_results: dict[str, float] = field(default_factory=dict)
    notes: str = ""

    @property
    def version_label(self) -> str:
        return f"DEFECT_VIEW_DATASET_v{self.dataset_version}"

    def to_json_dict(self) -> dict:
        data = asdict(self)
        data["version_label"] = self.version_label
        data["training_date"] = self.training_date.isoformat() if self.training_date else None
        return data

    @staticmethod
    def from_json_dict(data: dict) -> "DatasetVersion":
        training_date = date.fromisoformat(data["training_date"]) if data.get("training_date") else None
        return DatasetVersion(
            dataset_version=data["dataset_version"],
            datasets_included=list(data["datasets_included"]),
            number_of_images=data["number_of_images"],
            number_of_annotations=data["number_of_annotations"],
            class_distribution=dict(data["class_distribution"]),
            training_date=training_date,
            model_version=data.get("model_version"),
            validation_accuracy=data.get("validation_accuracy"),
            test_results=dict(data.get("test_results", {})),
            notes=data.get("notes", ""),
        )


class DatasetVersionRegistry:
    """Append-only registry of DatasetVersion snapshots. Recording a version whose label already
    exists is an error, never a silent overwrite - that's the whole point of section 18."""

    def __init__(self) -> None:
        self._versions: dict[str, DatasetVersion] = {}
        self._order: list[str] = []

    def next_dataset_version_number(self) -> str:
        return f"0.{len(self._order) + 1}"

    def record(self, version: DatasetVersion) -> DatasetVersion:
        if version.version_label in self._versions:
            raise ValueError(
                f"{version.version_label} has already been recorded - dataset versions are "
                f"append-only and never overwritten (spec section 18)"
            )
        self._versions[version.version_label] = version
        self._order.append(version.version_label)
        return version

    def get(self, version_label: str) -> DatasetVersion | None:
        return self._versions.get(version_label)

    def latest(self) -> DatasetVersion | None:
        return self._versions[self._order[-1]] if self._order else None

    def all_versions(self) -> list[DatasetVersion]:
        return [self._versions[label] for label in self._order]

    def save_to_file(self, path: Path) -> None:
        path.write_text(json.dumps([v.to_json_dict() for v in self.all_versions()], indent=2))

    @staticmethod
    def load_from_file(path: Path) -> "DatasetVersionRegistry":
        registry = DatasetVersionRegistry()
        if not path.exists():
            return registry
        for entry in json.loads(path.read_text()):
            version = DatasetVersion.from_json_dict(entry)
            registry._versions[version.version_label] = version
            registry._order.append(version.version_label)
        return registry
