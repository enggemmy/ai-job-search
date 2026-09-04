import unittest

from dataset_split import GroupedItem, Split, assign_split, split_items, split_summary


class AssignSplitTest(unittest.TestCase):
    def test_same_group_key_always_gets_the_same_split(self) -> None:
        first = assign_split("project-42:level-3")
        for _ in range(20):
            self.assertEqual(first, assign_split("project-42:level-3"))

    def test_invalid_ratios_raise(self) -> None:
        with self.assertRaises(ValueError):
            assign_split("x", ratios={Split.TRAIN: 0.5, Split.VALIDATION: 0.5, Split.TEST: 0.5})


class SplitItemsTest(unittest.TestCase):
    def test_grouped_items_never_split_across_train_and_test(self) -> None:
        # Ten images from the SAME inspection sequence (same group_key) must all land together.
        items = [GroupedItem(item_id=f"img_{i}", group_key="sequence-A") for i in range(10)]
        assignment = split_items(items)
        # Exactly one split should contain all 10; the others should contain zero.
        non_empty = [s for s, v in assignment.items() if v]
        self.assertEqual(1, len(non_empty))
        self.assertEqual(10, len(assignment[non_empty[0]]))

    def test_roughly_matches_the_70_15_15_target_over_many_groups(self) -> None:
        items = [GroupedItem(item_id=f"img_{i}", group_key=f"group-{i}") for i in range(3000)]
        assignment = split_items(items)
        summary = split_summary(assignment)
        self.assertAlmostEqual(0.70, summary[Split.TRAIN.value], delta=0.03)
        self.assertAlmostEqual(0.15, summary[Split.VALIDATION.value], delta=0.03)
        self.assertAlmostEqual(0.15, summary[Split.TEST.value], delta=0.03)

    def test_empty_input_has_zeroed_summary(self) -> None:
        summary = split_summary(split_items([]))
        self.assertEqual({s.value: 0.0 for s in Split}, summary)


if __name__ == "__main__":
    unittest.main()
