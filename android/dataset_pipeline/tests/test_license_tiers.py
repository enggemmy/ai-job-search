import unittest
from datetime import date

from license_tiers import DatasetManifest, DatasetTier, can_enter_production_training_package, classify_tier


class ClassifyTierTest(unittest.TestCase):
    def test_unknown_commercial_use_requires_review(self) -> None:
        self.assertEqual(
            DatasetTier.LICENSE_REVIEW_REQUIRED,
            classify_tier(commercial_use_allowed=None, redistribution_allowed=True, modification_allowed=True),
        )

    def test_unknown_modification_requires_review(self) -> None:
        self.assertEqual(
            DatasetTier.LICENSE_REVIEW_REQUIRED,
            classify_tier(commercial_use_allowed=True, redistribution_allowed=True, modification_allowed=None),
        )

    def test_non_commercial_is_experimental(self) -> None:
        self.assertEqual(
            DatasetTier.EXPERIMENTAL,
            classify_tier(commercial_use_allowed=False, redistribution_allowed=False, modification_allowed=True),
        )

    def test_commercial_but_no_modification_is_development(self) -> None:
        self.assertEqual(
            DatasetTier.DEVELOPMENT,
            classify_tier(commercial_use_allowed=True, redistribution_allowed=True, modification_allowed=False),
        )

    def test_commercial_and_modifiable_is_production(self) -> None:
        self.assertEqual(
            DatasetTier.PRODUCTION,
            classify_tier(commercial_use_allowed=True, redistribution_allowed=False, modification_allowed=True),
        )

    def test_redistribution_alone_does_not_gate_production(self) -> None:
        # Training doesn't require redistributing the dataset - see the docstring in
        # license_tiers.py for why redistribution is tracked but not required for Tier A.
        tier = classify_tier(commercial_use_allowed=True, redistribution_allowed=None, modification_allowed=True)
        self.assertEqual(DatasetTier.PRODUCTION, tier)


class ManifestGateTest(unittest.TestCase):
    def _manifest(self, **overrides) -> DatasetManifest:
        defaults = dict(
            dataset_name="Test",
            official_source="https://example.com",
            download_source="https://example.com/data",
            version="1",
            license="Test License",
            commercial_use_allowed=True,
            redistribution_allowed=True,
            modification_allowed=True,
            attribution_required=True,
            citation_required=True,
            number_of_images=10,
            annotation_type="BOUNDING_BOX",
            date_imported=date(2026, 1, 1),
        )
        defaults.update(overrides)
        return DatasetManifest(**defaults)

    def test_production_manifest_can_enter_production_package(self) -> None:
        self.assertTrue(can_enter_production_training_package(self._manifest()))

    def test_review_required_manifest_cannot(self) -> None:
        m = self._manifest(commercial_use_allowed=None)
        self.assertFalse(can_enter_production_training_package(m))

    def test_experimental_manifest_cannot(self) -> None:
        m = self._manifest(commercial_use_allowed=False)
        self.assertFalse(can_enter_production_training_package(m))


if __name__ == "__main__":
    unittest.main()
