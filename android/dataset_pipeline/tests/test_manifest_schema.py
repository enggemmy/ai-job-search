import unittest

from manifest_schema import (
    AnnotationRecord,
    AnnotationType,
    BoundingBoxGeometry,
    PointGeometry,
    PolygonGeometry,
    SourceType,
    VerificationStatus,
)


def _base_record(**overrides) -> AnnotationRecord:
    defaults = dict(
        image_id="IMG_000001",
        dataset="MBDD2025",
        source_type=SourceType.PUBLIC_DATASET,
        discipline="CIVIL_STRUCTURAL",
        category="BUILDING_SURFACE",
        defect_type="SURFACE_CRACK",
        original_label="crack",
        annotation_type=AnnotationType.BOUNDING_BOX,
        geometry=BoundingBoxGeometry(x=0.42, y=0.61, width=0.18, height=0.09),
    )
    defaults.update(overrides)
    return AnnotationRecord(**defaults)


class BoundingBoxGeometryTest(unittest.TestCase):
    def test_valid_box_has_no_errors(self) -> None:
        self.assertEqual([], BoundingBoxGeometry(0.1, 0.1, 0.2, 0.2).validate())

    def test_negative_coordinate_is_rejected(self) -> None:
        self.assertTrue(BoundingBoxGeometry(-0.1, 0.1, 0.2, 0.2).validate())

    def test_zero_width_is_rejected(self) -> None:
        self.assertTrue(BoundingBoxGeometry(0.1, 0.1, 0.0, 0.2).validate())

    def test_box_extending_past_the_image_is_rejected(self) -> None:
        self.assertTrue(BoundingBoxGeometry(0.9, 0.1, 0.5, 0.2).validate())


class PolygonGeometryTest(unittest.TestCase):
    def test_triangle_is_valid(self) -> None:
        self.assertEqual([], PolygonGeometry([(0.1, 0.1), (0.5, 0.1), (0.3, 0.4)]).validate())

    def test_two_points_is_invalid(self) -> None:
        self.assertTrue(PolygonGeometry([(0.1, 0.1), (0.5, 0.1)]).validate())

    def test_out_of_range_point_is_invalid(self) -> None:
        self.assertTrue(PolygonGeometry([(1.5, 0.1), (0.5, 0.1), (0.3, 0.4)]).validate())


class AnnotationRecordTest(unittest.TestCase):
    def test_well_formed_bounding_box_record_is_valid(self) -> None:
        record = _base_record()
        self.assertEqual([], record.validate())
        self.assertTrue(record.is_valid())

    def test_unknown_defect_type_is_rejected(self) -> None:
        record = _base_record(defect_type="NOT_A_REAL_DEFECT")
        self.assertFalse(record.is_valid())
        self.assertTrue(any("taxonomy" in e for e in record.validate()))

    def test_classification_record_must_not_carry_geometry(self) -> None:
        record = _base_record(annotation_type=AnnotationType.CLASSIFICATION, geometry=BoundingBoxGeometry(0.1, 0.1, 0.2, 0.2))
        self.assertFalse(record.is_valid())

    def test_classification_record_without_geometry_is_valid(self) -> None:
        record = _base_record(annotation_type=AnnotationType.CLASSIFICATION, geometry=None)
        self.assertTrue(record.is_valid())

    def test_bounding_box_type_requires_bounding_box_geometry(self) -> None:
        record = _base_record(geometry=PointGeometry(0.5, 0.5))
        self.assertFalse(record.is_valid())

    def test_confidence_out_of_range_is_rejected(self) -> None:
        record = _base_record(confidence=1.5)
        self.assertFalse(record.is_valid())

    def test_verified_true_requires_a_real_verification_status(self) -> None:
        record = _base_record(verified=True, verification_status=VerificationStatus.UNVERIFIED)
        self.assertFalse(record.is_valid())

    def test_verified_true_with_verified_positive_is_valid(self) -> None:
        record = _base_record(verified=True, verification_status=VerificationStatus.VERIFIED_POSITIVE)
        self.assertTrue(record.is_valid())


if __name__ == "__main__":
    unittest.main()
