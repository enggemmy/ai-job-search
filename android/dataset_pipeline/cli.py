"""Command-line entry points for the dataset pipeline (a CLI stand-in for spec section 16's
"Dataset Manager" admin actions - this session builds the pipeline, not an Android admin UI;
see README.md for why).

Usage (from android/dataset_pipeline/):
    python3 cli.py registry                 # list every known dataset manifest and its tier
    python3 cli.py check-images DIR          # run QC over every image file in DIR
    python3 cli.py class-balance LABELS_FILE # one defect_type per line -> imbalance report
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from quality_control import check_image, class_balance, find_exact_duplicates, find_near_duplicate_groups
from registry.verified_datasets import ALL_DATASETS

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"}


def cmd_registry(_: argparse.Namespace) -> int:
    print(f"{'Dataset':<15} {'Tier':<26} {'Images':>8}  Source")
    for m in ALL_DATASETS:
        print(f"{m.dataset_name:<15} {m.dataset_tier.value:<26} {m.number_of_images:>8}  {m.download_source}")
    return 0


def cmd_check_images(args: argparse.Namespace) -> int:
    directory = Path(args.directory)
    paths = sorted(p for p in directory.rglob("*") if p.suffix.lower() in IMAGE_EXTENSIONS)
    if not paths:
        print(f"No images found under {directory}", file=sys.stderr)
        return 1

    results = [check_image(p, args.min_width, args.min_height) for p in paths]
    usable = [r for r in results if r.is_usable]
    print(f"Checked {len(results)} images: {len(usable)} usable, {len(results) - len(usable)} rejected")
    for r in results:
        if not r.is_usable:
            print(f"  REJECT {r.path}: {'; '.join(r.rejection_reasons) if r.rejection_reasons else 'corrupt'}")

    exact = find_exact_duplicates(results)
    if exact:
        print(f"\n{len(exact)} exact-duplicate group(s):")
        for h, group in exact.items():
            print(f"  {h[:12]}...: {[str(p) for p in group]}")

    near = find_near_duplicate_groups(results)
    if near:
        print(f"\n{len(near)} near-duplicate group(s):")
        for group in near:
            print(f"  {[str(p) for p in group]}")

    return 0


def cmd_class_balance(args: argparse.Namespace) -> int:
    labels = [line.strip() for line in Path(args.labels_file).read_text().splitlines() if line.strip()]
    report = class_balance(labels)
    print(f"Total annotations: {report.total}")
    for label, count in sorted(report.counts.items(), key=lambda kv: -kv[1]):
        print(f"  {label:<30} {count:>6}  ({count / report.total:.1%})")
    if report.imbalance_warnings:
        print("\nWARNINGS:")
        for w in report.imbalance_warnings:
            print(f"  - {w}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="DEFECT VIEW dataset pipeline CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    p_registry = sub.add_parser("registry", help="List known dataset manifests and their license tier")
    p_registry.set_defaults(func=cmd_registry)

    p_check = sub.add_parser("check-images", help="Run QC over every image in a directory")
    p_check.add_argument("directory")
    p_check.add_argument("--min-width", type=int, default=256)
    p_check.add_argument("--min-height", type=int, default=256)
    p_check.set_defaults(func=cmd_check_images)

    p_balance = sub.add_parser("class-balance", help="Report class balance from a file of defect_type labels, one per line")
    p_balance.add_argument("labels_file")
    p_balance.set_defaults(func=cmd_class_balance)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
