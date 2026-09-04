import unittest

from label_mapping import ALL_MAPPINGS, NON_DEFECT_LABEL, lookup, mappings_for_dataset


class LabelMappingTest(unittest.TestCase):
    def test_every_registered_mapping_is_internally_valid(self) -> None:
        errors = []
        for m in ALL_MAPPINGS:
            errors.extend(m.validate())
        self.assertEqual([], errors)

    def test_lookup_is_case_and_whitespace_insensitive(self) -> None:
        m = lookup("ConViD", "  Spalling  ")
        self.assertIsNotNone(m)
        self.assertEqual("CONCRETE_SPALLING", m.defect_view_label)

    def test_lookup_unknown_label_returns_none(self) -> None:
        self.assertIsNone(lookup("ConViD", "not a real label"))

    def test_lookup_wrong_dataset_returns_none(self) -> None:
        # "spalling" exists for ConViD but not for a dataset that never registered it.
        self.assertIsNone(lookup("NoSuchDataset", "spalling"))

    def test_bd3_normal_maps_to_non_defect_sentinel_not_a_taxonomy_key(self) -> None:
        m = lookup("BD3", "normal")
        self.assertEqual(NON_DEFECT_LABEL, m.defect_view_label)

    def test_mappings_for_dataset_returns_only_that_dataset(self) -> None:
        convid = mappings_for_dataset("ConViD")
        self.assertTrue(all(m.dataset == "ConViD" for m in convid))
        self.assertEqual(4, len(convid))

    def test_low_confidence_mappings_are_flagged_in_notes_not_hidden(self) -> None:
        efflorescence = lookup("CODEBRIM", "efflorescence")
        self.assertLess(efflorescence.mapping_confidence, 0.5)
        self.assertIn("review", efflorescence.mapping_notes.lower())


if __name__ == "__main__":
    unittest.main()
