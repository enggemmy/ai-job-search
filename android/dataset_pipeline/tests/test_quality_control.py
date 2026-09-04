import tempfile
import unittest
from pathlib import Path

from PIL import Image

from quality_control import (
    check_image,
    class_balance,
    find_exact_duplicates,
    find_near_duplicate_groups,
    hamming_distance,
    is_near_duplicate,
    normalize_label,
)


def _solid_image(path: Path, size=(300, 300), color=(128, 128, 128)) -> None:
    Image.new("RGB", size, color).save(path)


def _checkerboard_image(path: Path, size=(300, 300), cell=10) -> None:
    img = Image.new("RGB", size)
    pixels = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            on = ((x // cell) + (y // cell)) % 2 == 0
            pixels[x, y] = (10, 10, 10) if on else (245, 245, 245)
    img.save(path)


class CheckImageTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmpdir.cleanup)
        self.root = Path(self.tmpdir.name)

    def test_valid_image_is_usable(self) -> None:
        path = self.root / "a.jpg"
        _solid_image(path)
        result = check_image(path)
        self.assertTrue(result.is_usable)
        self.assertEqual((300, 300), (result.width, result.height))
        self.assertIsNotNone(result.sha256)
        self.assertIsNotNone(result.average_hash)

    def test_missing_file_is_rejected(self) -> None:
        result = check_image(self.root / "does_not_exist.jpg")
        self.assertTrue(result.is_corrupt)

    def test_corrupt_file_is_rejected(self) -> None:
        path = self.root / "corrupt.jpg"
        path.write_bytes(b"this is not a real image file")
        result = check_image(path)
        self.assertTrue(result.is_corrupt)

    def test_undersized_image_is_rejected_but_not_marked_corrupt(self) -> None:
        path = self.root / "tiny.jpg"
        _solid_image(path, size=(50, 50))
        result = check_image(path, min_width=256, min_height=256)
        self.assertFalse(result.is_corrupt)
        self.assertFalse(result.is_usable)
        self.assertTrue(any("resolution" in r for r in result.rejection_reasons))

    def test_identical_images_have_identical_hashes(self) -> None:
        a, b = self.root / "a.jpg", self.root / "b.jpg"
        _solid_image(a)
        _solid_image(b)
        ra, rb = check_image(a), check_image(b)
        self.assertEqual(ra.sha256, rb.sha256)
        self.assertEqual(ra.average_hash, rb.average_hash)

    def test_different_images_have_different_average_hashes(self) -> None:
        a, b = self.root / "solid.jpg", self.root / "checker.jpg"
        _solid_image(a)
        _checkerboard_image(b)
        ra, rb = check_image(a), check_image(b)
        self.assertFalse(is_near_duplicate(ra.average_hash, rb.average_hash))


class DuplicateDetectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmpdir.cleanup)
        self.root = Path(self.tmpdir.name)

    def test_exact_duplicates_are_grouped(self) -> None:
        a, b, c = self.root / "a.jpg", self.root / "b.jpg", self.root / "c.jpg"
        _solid_image(a, color=(100, 100, 100))
        _solid_image(b, color=(100, 100, 100))
        _solid_image(c, color=(200, 50, 50))
        results = [check_image(a), check_image(b), check_image(c)]
        groups = find_exact_duplicates(results)
        self.assertEqual(1, len(groups))
        self.assertEqual(2, len(next(iter(groups.values()))))

    def test_near_duplicates_are_grouped_by_perceptual_hash(self) -> None:
        a, b = self.root / "a.jpg", self.root / "b.jpg"
        # Same checkerboard, one very slightly perturbed - should still hash near-identical
        # at the 8x8 average-hash resolution used here.
        _checkerboard_image(a, size=(300, 300), cell=10)
        _checkerboard_image(b, size=(302, 298), cell=10)
        results = [check_image(a), check_image(b)]
        groups = find_near_duplicate_groups(results)
        self.assertEqual(1, len(groups))

    def test_hamming_distance_zero_for_identical_hashes(self) -> None:
        self.assertEqual(0, hamming_distance(0b10101, 0b10101))

    def test_hamming_distance_counts_differing_bits(self) -> None:
        self.assertEqual(2, hamming_distance(0b1100, 0b0110))


class NormalizeLabelTest(unittest.TestCase):
    def test_strips_and_lowercases(self) -> None:
        self.assertEqual("major crack", normalize_label("  Major   Crack \n"))


class ClassBalanceTest(unittest.TestCase):
    def test_dominant_class_triggers_over_representation_warning(self) -> None:
        labels = ["CONCRETE_CRACK"] * 90 + ["CONCRETE_VOID"] * 10
        report = class_balance(labels)
        self.assertEqual(100, report.total)
        self.assertTrue(any("over-representation" in w for w in report.imbalance_warnings))

    def test_rare_class_triggers_too_few_warning(self) -> None:
        labels = ["CONCRETE_CRACK"] * 50 + ["DOOR_DAMAGE"] * 2
        report = class_balance(labels, warn_above_fraction=0.9, warn_below_count=5)
        self.assertTrue(any("too few" in w for w in report.imbalance_warnings))

    def test_balanced_classes_produce_no_warnings(self) -> None:
        labels = ["A"] * 30 + ["B"] * 30 + ["C"] * 30
        report = class_balance(labels, warn_above_fraction=0.5, warn_below_count=5)
        self.assertEqual([], report.imbalance_warnings)


if __name__ == "__main__":
    unittest.main()
