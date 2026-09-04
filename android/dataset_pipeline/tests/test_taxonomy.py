import unittest

import taxonomy


class TaxonomyTest(unittest.TestCase):
    def test_seed_matches_spec_counts(self) -> None:
        # 34 architectural + 34 MEP + 11 civil/structural + 8 general = 87 (POOR_WORKMANSHIP
        # deduplicated across ARCHITECTURAL/GENERAL - see taxonomy.py comment).
        self.assertEqual(87, len(taxonomy.all_defect_types()))
        self.assertEqual(34, len(taxonomy.defect_types_for(taxonomy.Discipline.ARCHITECTURAL)))
        self.assertEqual(34, len(taxonomy.defect_types_for(taxonomy.Discipline.MEP)))
        self.assertEqual(11, len(taxonomy.defect_types_for(taxonomy.Discipline.CIVIL_STRUCTURAL)))
        self.assertEqual(8, len(taxonomy.defect_types_for(taxonomy.Discipline.GENERAL)))

    def test_all_keys_are_unique(self) -> None:
        keys = [d.key for d in taxonomy.all_defect_types()]
        self.assertEqual(len(keys), len(set(keys)))

    def test_known_keys_resolve(self) -> None:
        for key in ("CONCRETE_CRACK", "TILE_LIPPAGE", "PIPE_LEAKAGE", "UNKNOWN_ANOMALY"):
            self.assertIsNotNone(taxonomy.get_defect_type(key), key)

    def test_unknown_key_returns_none(self) -> None:
        self.assertIsNone(taxonomy.get_defect_type("NOT_A_REAL_DEFECT"))

    def test_register_new_defect_type_does_not_require_code_changes(self) -> None:
        self.addCleanup(taxonomy._REGISTRY.pop, "BIOLOGICAL_GROWTH", None)
        defect = taxonomy.register_defect_type("BIOLOGICAL_GROWTH", taxonomy.Discipline.ARCHITECTURAL)
        self.assertEqual("Biological Growth", defect.display_name)
        self.assertIn(defect, taxonomy.defect_types_for(taxonomy.Discipline.ARCHITECTURAL))

    def test_reregistering_same_key_under_different_discipline_raises(self) -> None:
        self.addCleanup(taxonomy._REGISTRY.pop, "TEST_DUPLICATE_KEY", None)
        taxonomy.register_defect_type("TEST_DUPLICATE_KEY", taxonomy.Discipline.MEP)
        with self.assertRaises(ValueError):
            taxonomy.register_defect_type("TEST_DUPLICATE_KEY", taxonomy.Discipline.GENERAL)

    def test_lowercase_key_rejected(self) -> None:
        with self.assertRaises(ValueError):
            taxonomy.DefectType(key="lowercase_key", discipline=taxonomy.Discipline.GENERAL)


if __name__ == "__main__":
    unittest.main()
