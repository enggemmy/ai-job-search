import tempfile
import unittest
from datetime import date
from pathlib import Path

from dataset_version import DatasetVersion, DatasetVersionRegistry


def _version(number: str, **overrides) -> DatasetVersion:
    defaults = dict(
        dataset_version=number,
        datasets_included=["CUBIT-InSeg"],
        number_of_images=6996,
        number_of_annotations=62187,
        class_distribution={"SURFACE_CRACK": 40000, "SURFACE_DETACHMENT": 22187},
        training_date=date(2026, 1, 1),
        model_version="classical-cv-1.0.0",
        validation_accuracy=0.0,
        test_results={},
        notes="Initial snapshot",
    )
    defaults.update(overrides)
    return DatasetVersion(**defaults)


class DatasetVersionTest(unittest.TestCase):
    def test_version_label_derives_from_dataset_version(self) -> None:
        self.assertEqual("DEFECT_VIEW_DATASET_v0.1", _version("0.1").version_label)

    def test_json_round_trip_preserves_all_fields(self) -> None:
        v = _version("0.3", validation_accuracy=0.82, test_results={"f1": 0.79})
        restored = DatasetVersion.from_json_dict(v.to_json_dict())
        self.assertEqual(v, restored)


class DatasetVersionRegistryTest(unittest.TestCase):
    def test_next_dataset_version_number_increments(self) -> None:
        registry = DatasetVersionRegistry()
        self.assertEqual("0.1", registry.next_dataset_version_number())
        registry.record(_version(registry.next_dataset_version_number()))
        self.assertEqual("0.2", registry.next_dataset_version_number())

    def test_recording_a_duplicate_label_raises(self) -> None:
        registry = DatasetVersionRegistry()
        registry.record(_version("0.1"))
        with self.assertRaises(ValueError):
            registry.record(_version("0.1"))

    def test_latest_returns_the_most_recently_recorded_version(self) -> None:
        registry = DatasetVersionRegistry()
        registry.record(_version("0.1"))
        registry.record(_version("0.2"))
        self.assertEqual("DEFECT_VIEW_DATASET_v0.2", registry.latest().version_label)

    def test_recording_v2_does_not_alter_v1(self) -> None:
        registry = DatasetVersionRegistry()
        v1 = registry.record(_version("0.1", number_of_images=100))
        registry.record(_version("0.2", number_of_images=200))
        self.assertEqual(100, registry.get("DEFECT_VIEW_DATASET_v0.1").number_of_images)
        self.assertEqual(v1, registry.get("DEFECT_VIEW_DATASET_v0.1"))

    def test_save_and_load_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "versions.json"
            registry = DatasetVersionRegistry()
            registry.record(_version("0.1"))
            registry.record(_version("0.2"))
            registry.save_to_file(path)

            reloaded = DatasetVersionRegistry.load_from_file(path)
            self.assertEqual(2, len(reloaded.all_versions()))
            self.assertEqual("DEFECT_VIEW_DATASET_v0.2", reloaded.latest().version_label)

    def test_load_from_missing_file_returns_empty_registry(self) -> None:
        registry = DatasetVersionRegistry.load_from_file(Path("/nonexistent/path/versions.json"))
        self.assertEqual([], registry.all_versions())


if __name__ == "__main__":
    unittest.main()
