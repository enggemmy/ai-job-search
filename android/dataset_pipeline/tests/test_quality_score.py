import tempfile
import unittest
from pathlib import Path

from PIL import Image

from label_mapping import mappings_for_dataset
from license_tiers import DatasetTier
from manifest_schema import AnnotationRecord, AnnotationType, SourceType
from quality_control import check_image
from quality_score import (
    annotation_quality_score,
    compute_quality_score,
    defect_diversity_score,
    image_quality_score,
    label_reliability_score,
    license_confidence_score,
)


def _solid_image(path: Path, size=(300, 300), color=(128, 128, 128)) -> None:
    Image.new("RGB", size, color).save(path)


def _record(defect_type: str, valid: bool = True) -> AnnotationRecord:
    return AnnotationRecord(
        image_id="IMG_1",
        dataset="ConViD",
        source_type=SourceType.PUBLIC_DATASET,
        discipline="CIVIL_STRUCTURAL",
        category="CONCRETE",
        defect_type=defect_type if valid else "NOT_A_REAL_DEFECT",
        original_label="spalling",
        annotation_type=AnnotationType.CLASSIFICATION,
        geometry=None,
    )


class ImageQualityScoreTest(unittest.TestCase):
    def test_all_usable_images_score_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            paths = [Path(tmp) / f"{i}.jpg" for i in range(3)]
            for p in paths:
                _solid_image(p)
            checks = [check_image(p) for p in paths]
            self.assertEqual(1.0, image_quality_score(checks))

    def test_empty_input_scores_zero(self) -> None:
        self.assertEqual(0.0, image_quality_score([]))

    def test_half_corrupt_scores_half(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            good = Path(tmp) / "good.jpg"
            bad = Path(tmp) / "bad.jpg"
            _solid_image(good)
            bad.write_bytes(b"not an image")
            checks = [check_image(good), check_image(bad)]
            self.assertEqual(0.5, image_quality_score(checks))


class AnnotationQualityScoreTest(unittest.TestCase):
    def test_all_valid_scores_one(self) -> None:
        records = [_record("CONCRETE_SPALLING") for _ in range(4)]
        self.assertEqual(1.0, annotation_quality_score(records))

    def test_invalid_records_lower_the_score(self) -> None:
        records = [_record("CONCRETE_SPALLING"), _record("x", valid=False)]
        self.assertEqual(0.5, annotation_quality_score(records))


class DefectDiversityScoreTest(unittest.TestCase):
    def test_single_class_scores_zero(self) -> None:
        self.assertEqual(0.0, defect_diversity_score({"CONCRETE_CRACK": 100}))

    def test_perfectly_balanced_classes_score_near_one(self) -> None:
        dist = {"A": 25, "B": 25, "C": 25, "D": 25}
        self.assertAlmostEqual(1.0, defect_diversity_score(dist), places=6)

    def test_skewed_distribution_scores_between_zero_and_one(self) -> None:
        dist = {"CONCRETE_CRACK": 970, "DOOR_DAMAGE": 10, "TILE_LIPPAGE": 10, "PIPE_LEAKAGE": 10}
        score = defect_diversity_score(dist)
        self.assertGreater(score, 0.0)
        self.assertLess(score, 0.5)

    def test_empty_distribution_scores_zero(self) -> None:
        self.assertEqual(0.0, defect_diversity_score({}))


class LicenseConfidenceScoreTest(unittest.TestCase):
    def test_production_scores_highest(self) -> None:
        self.assertEqual(1.0, license_confidence_score(DatasetTier.PRODUCTION))

    def test_review_required_scores_zero(self) -> None:
        self.assertEqual(0.0, license_confidence_score(DatasetTier.LICENSE_REVIEW_REQUIRED))

    def test_tiers_are_strictly_ordered(self) -> None:
        a = license_confidence_score(DatasetTier.PRODUCTION)
        b = license_confidence_score(DatasetTier.DEVELOPMENT)
        c = license_confidence_score(DatasetTier.EXPERIMENTAL)
        d = license_confidence_score(DatasetTier.LICENSE_REVIEW_REQUIRED)
        self.assertGreater(a, b)
        self.assertGreater(b, c)
        self.assertGreater(c, d)


class LabelReliabilityScoreTest(unittest.TestCase):
    def test_convid_mappings_are_all_high_confidence(self) -> None:
        score = label_reliability_score(mappings_for_dataset("ConViD"))
        self.assertGreaterEqual(score, 0.9)

    def test_codebrim_mappings_are_dragged_down_by_low_confidence_entries(self) -> None:
        score = label_reliability_score(mappings_for_dataset("CODEBRIM"))
        self.assertLess(score, 0.9)

    def test_empty_mapping_list_scores_zero(self) -> None:
        self.assertEqual(0.0, label_reliability_score([]))


class ComputeQualityScoreTest(unittest.TestCase):
    def test_composite_is_a_weighted_percentage(self) -> None:
        breakdown = compute_quality_score(
            image_checks=[],
            annotation_records=[],
            class_distribution={},
            tier=DatasetTier.LICENSE_REVIEW_REQUIRED,
            label_mappings=[],
        )
        # every sub-score is 0 for empty inputs -> composite must be 0.
        self.assertEqual(0.0, breakdown.composite)
        self.assertIn("composite", breakdown.as_dict())

    def test_production_tier_with_good_data_scores_well(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            paths = [Path(tmp) / f"{i}.jpg" for i in range(4)]
            for p in paths:
                _solid_image(p)
            checks = [check_image(p) for p in paths]
        records = [_record("CONCRETE_SPALLING"), _record("CONCRETE_VOID")]
        breakdown = compute_quality_score(
            image_checks=checks,
            annotation_records=records,
            class_distribution={"CONCRETE_SPALLING": 50, "CONCRETE_VOID": 50},
            tier=DatasetTier.PRODUCTION,
            label_mappings=mappings_for_dataset("ConViD"),
        )
        self.assertGreater(breakdown.composite, 80.0)


if __name__ == "__main__":
    unittest.main()
