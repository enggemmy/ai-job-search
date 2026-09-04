import tempfile
import unittest
from datetime import date
from pathlib import Path

from PIL import Image

from ingest import build_annotation_record, ingest_dataset
from license_tiers import DatasetManifest, DatasetTier
from manifest_schema import AnnotationType


def _solid_image(path: Path, size=(300, 300), color=(128, 128, 128)) -> None:
    Image.new("RGB", size, color).save(path)


def _manifest(**overrides) -> DatasetManifest:
    defaults = dict(
        dataset_name="ConViD",
        official_source="https://data.mendeley.com/datasets/fx3rthfjhy/2",
        download_source="https://data.mendeley.com/datasets/fx3rthfjhy/2",
        version="2",
        license="Test",
        commercial_use_allowed=True,
        redistribution_allowed=True,
        modification_allowed=True,
        attribution_required=True,
        citation_required=True,
        number_of_images=0,
        annotation_type="CLASSIFICATION",
        date_imported=date(2026, 1, 1),
    )
    defaults.update(overrides)
    return DatasetManifest(**defaults)


class IngestDatasetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmpdir.cleanup)
        self.root = Path(self.tmpdir.name)
        self.datasets_root = self.root / "datasets"

    def test_end_to_end_synthetic_batch_accepts_valid_unique_images(self) -> None:
        paths = []
        for i, color in enumerate([(10, 10, 10), (50, 60, 70), (200, 200, 200)]):
            p = self.root / f"img_{i}.jpg"
            _solid_image(p, color=color)
            paths.append(p)

        raw_labels = {str(p): "spalling" for p in paths}
        result = ingest_dataset(_manifest(), paths, raw_labels, self.datasets_root, dry_run=True)

        self.assertEqual(3, result.total_candidate_images)
        self.assertEqual(3, result.accepted_images)
        self.assertEqual({}, result.rejected_images)
        self.assertEqual(DatasetTier.PRODUCTION, result.tier)
        self.assertIsNone(result.written_to)  # dry_run

    def test_corrupt_image_is_rejected_not_silently_dropped(self) -> None:
        good = self.root / "good.jpg"
        bad = self.root / "bad.jpg"
        _solid_image(good)
        bad.write_bytes(b"not an image")

        raw_labels = {str(good): "cracks", str(bad): "cracks"}
        result = ingest_dataset(_manifest(), [good, bad], raw_labels, self.datasets_root, dry_run=True)

        self.assertEqual(1, result.accepted_images)
        self.assertIn(str(bad), result.rejected_images)

    def test_exact_duplicate_is_rejected(self) -> None:
        a, b = self.root / "a.jpg", self.root / "b.jpg"
        _solid_image(a, color=(77, 77, 77))
        _solid_image(b, color=(77, 77, 77))

        raw_labels = {str(a): "voids", str(b): "voids"}
        result = ingest_dataset(_manifest(), [a, b], raw_labels, self.datasets_root, dry_run=True)

        self.assertEqual(1, result.accepted_images)
        self.assertEqual(1, result.exact_duplicate_groups)

    def test_missing_label_is_rejected(self) -> None:
        p = self.root / "unlabeled.jpg"
        _solid_image(p)
        result = ingest_dataset(_manifest(), [p], {}, self.datasets_root, dry_run=True)
        self.assertEqual(0, result.accepted_images)
        self.assertIn("no label", result.rejected_images[str(p)])

    def test_unmapped_label_is_flagged_not_silently_forced(self) -> None:
        p = self.root / "img.jpg"
        _solid_image(p)
        raw_labels = {str(p): "some_label_not_in_the_mapping_table"}
        result = ingest_dataset(_manifest(), [p], raw_labels, self.datasets_root, dry_run=True)
        self.assertIn("some_label_not_in_the_mapping_table", result.unmapped_labels)

    def test_review_required_dataset_still_processes_but_never_targets_production(self) -> None:
        p = self.root / "img.jpg"
        _solid_image(p)
        manifest = _manifest(commercial_use_allowed=None, modification_allowed=None)
        result = ingest_dataset(manifest, [p], {str(p): "spalling"}, self.datasets_root, dry_run=False)
        self.assertEqual(DatasetTier.LICENSE_REVIEW_REQUIRED, result.tier)
        self.assertIsNotNone(result.written_to)
        self.assertIn("experimental", str(result.written_to))
        self.assertNotIn("production", str(result.written_to))

    def test_non_dry_run_actually_writes_accepted_images_and_manifest(self) -> None:
        p = self.root / "img.jpg"
        _solid_image(p)
        result = ingest_dataset(_manifest(), [p], {str(p): "spalling"}, self.datasets_root, dry_run=False)
        self.assertIsNotNone(result.written_to)
        self.assertTrue((result.written_to / "img.jpg").exists())
        self.assertTrue((result.written_to / "manifest.json").exists())
        self.assertIn("production", str(result.written_to))


class BuildAnnotationRecordTest(unittest.TestCase):
    def test_resolves_a_mapped_label_into_a_valid_record(self) -> None:
        record = build_annotation_record(_manifest(), "IMG_1", "spalling", "CIVIL_STRUCTURAL", "CONCRETE")
        self.assertIsNotNone(record)
        self.assertEqual("CONCRETE_SPALLING", record.defect_type)
        self.assertEqual(AnnotationType.CLASSIFICATION, record.annotation_type)
        self.assertTrue(record.is_valid())

    def test_non_defect_label_returns_none(self) -> None:
        manifest = _manifest(dataset_name="BD3")
        record = build_annotation_record(manifest, "IMG_2", "normal", "ARCHITECTURAL", "WALL")
        self.assertIsNone(record)

    def test_unmapped_label_returns_none(self) -> None:
        record = build_annotation_record(_manifest(), "IMG_3", "totally_unknown_label", "CIVIL_STRUCTURAL", "CONCRETE")
        self.assertIsNone(record)


if __name__ == "__main__":
    unittest.main()
