"""Per-dataset label mapping tables (spec section 7): original dataset label -> DEFECT VIEW
taxonomy key, with an explicit confidence and human-readable notes for every mapping. Nothing
here is a forced/inaccurate mapping - see MAPPING_NOTES on each entry for the reasoning, and
`docs/DATASET_ACQUISITION.md` for what each dataset actually contains (verified by web research
during this session, not assumed).
"""

from __future__ import annotations

from dataclasses import dataclass

from taxonomy import get_defect_type

# Sentinel for source labels that are explicitly "no defect present" (e.g. BD3's "normal"
# class) - these are real, useful training examples for the Model 1 normal-vs-defective
# classifier (spec section 15) but must never be treated as a taxonomy defect_type.
NON_DEFECT_LABEL = "NOT_A_DEFECT"


@dataclass(frozen=True)
class LabelMapping:
    dataset: str
    original_label: str
    normalized_label: str
    defect_view_label: str
    mapping_confidence: float  # 0..1 - how directly the original label maps to the taxonomy key
    mapping_notes: str

    def validate(self) -> list[str]:
        errors = []
        if self.defect_view_label != NON_DEFECT_LABEL and get_defect_type(self.defect_view_label) is None:
            errors.append(f"{self.defect_view_label!r} is not a registered DEFECT VIEW taxonomy key")
        if not (0.0 <= self.mapping_confidence <= 1.0):
            errors.append(f"mapping_confidence={self.mapping_confidence} must be in 0..1")
        return errors


def _m(dataset: str, original: str, defect_view: str, confidence: float, notes: str) -> LabelMapping:
    return LabelMapping(
        dataset=dataset,
        original_label=original,
        normalized_label=original.strip().lower(),
        defect_view_label=defect_view,
        mapping_confidence=confidence,
        mapping_notes=notes,
    )


# MBDD2025 (Zenodo 15622584) - five defect categories on UAV-captured building surfaces
# (steel/RC/wood/brick/masonry/brick-concrete structures). Category names as given in the
# product spec; not yet independently confirmed against the dataset's own label file since
# Zenodo is unreachable from this sandbox - see DATASET_ACQUISITION.md.
MBDD2025_MAPPINGS = [
    _m("MBDD2025", "crack", "SURFACE_CRACK", 0.7,
       "MBDD2025 spans multiple structure types (steel/RC/wood/brick/masonry), so a bare "
       "'crack' label is mapped to the generic SURFACE_CRACK rather than CONCRETE_CRACK - "
       "re-map to CONCRETE_CRACK per-image once the structure-type field is confirmed."),
    _m("MBDD2025", "abscission", "SURFACE_DETACHMENT", 0.9,
       "Abscission (material separating/falling away from the substrate) maps directly."),
    _m("MBDD2025", "leakage", "WATER_LEAKAGE", 0.9, "Direct mapping."),
    _m("MBDD2025", "corrosion", "CORROSION", 0.9, "Direct mapping."),
    _m("MBDD2025", "bulging", "SURFACE_BULGING", 0.9, "Direct mapping."),
]

# ConViD - Concrete Visual Defect Dataset (Mendeley Data fx3rthfjhy). Four concrete-specific
# defect classes, smartphone-captured under varying illumination.
CONVID_MAPPINGS = [
    _m("ConViD", "spalling", "CONCRETE_SPALLING", 0.95, "Direct mapping, concrete-specific dataset."),
    _m("ConViD", "honeycombing", "CONCRETE_HONEYCOMBING", 0.95, "Direct mapping, concrete-specific dataset."),
    _m("ConViD", "voids", "CONCRETE_VOID", 0.95, "Direct mapping, concrete-specific dataset."),
    _m("ConViD", "cracks", "CONCRETE_CRACK", 0.95, "Direct mapping, concrete-specific dataset."),
]

# CODEBRIM - COncrete DEfect BRidge IMage dataset (Zenodo 2620293, CVPR'19 Mundt et al.).
# Non-commercial/educational license only - Tier C (EXPERIMENTAL), see license_tiers registry.
CODEBRIM_MAPPINGS = [
    _m("CODEBRIM", "crack", "CONCRETE_CRACK", 0.9, "Bridge concrete surfaces; direct mapping."),
    _m("CODEBRIM", "spallation", "CONCRETE_SPALLING", 0.9, "Direct mapping."),
    _m("CODEBRIM", "exposed reinforcement bar", "CONCRETE_SPALLING", 0.5,
       "No dedicated 'exposed rebar' key exists in the current taxonomy; mapped to "
       "CONCRETE_SPALLING as the closest existing category (rebar exposure is a severe form "
       "of spalling) pending a possible taxonomy extension via register_defect_type."),
    _m("CODEBRIM", "efflorescence", "STAINING", 0.4,
       "Efflorescence (calcium leaching) has no dedicated key; mapped to the closest general "
       "surface-appearance category. Low confidence - flag for manual review before training."),
    _m("CODEBRIM", "corrosion", "CONCRETE_CORROSION", 0.9, "Direct mapping (rebar/metal staining on concrete)."),
]

# BD3 - Building Defects Detection Dataset (github.com/Praveenkottari/BD3-Dataset, ACM 2024).
# Seven classes in the original set: algae, major crack, minor crack, peeling, spalling, stain,
# and normal (non-defective) images.
BD3_MAPPINGS = [
    _m("BD3", "algae", "STAINING", 0.6,
       "No dedicated biological-growth key exists yet; mapped to STAINING as the closest "
       "surface-appearance category. Candidate for a future taxonomy extension (e.g. "
       "BIOLOGICAL_GROWTH) rather than forcing a closer fit that doesn't exist."),
    _m("BD3", "major crack", "WALL_CRACK", 0.6,
       "BD3 images are building facades/walls, not specifically concrete - mapped to the "
       "architectural WALL_CRACK rather than CONCRETE_CRACK; severity ('major') carried "
       "separately in AnnotationRecord.severity, not folded into the defect_type key."),
    _m("BD3", "minor crack", "WALL_CRACK", 0.6, "Same reasoning as 'major crack'."),
    _m("BD3", "peeling", "PAINT_PEELING", 0.7, "Assumes the peeling surface is painted finish - verify per-image."),
    _m("BD3", "spalling", "SURFACE_DETACHMENT", 0.6,
       "BD3's 'spalling' is on building facades broadly, not confirmed concrete-specific like "
       "ConViD/CODEBRIM - mapped to the more general SURFACE_DETACHMENT rather than "
       "CONCRETE_SPALLING pending confirmation of substrate material per image."),
    _m("BD3", "stain", "STAINING", 0.9, "Direct mapping."),
    _m("BD3", "normal", NON_DEFECT_LABEL, 1.0,
       "Not a defect - 'normal' images are non-defective examples, useful for the Model 1 "
       "(normal vs defective) classifier in the progressive AI strategy (spec section 15). "
       "Mapped to the NON_DEFECT_LABEL sentinel, never to a taxonomy defect_type."),
]

# CUBIT-InSeg (github.com/BenyunZhao/CUBIT-InSeg, Automation in Construction 2026). Two
# instance-segmentation classes on UAV facade imagery: crack, spalling.
CUBIT_INSEG_MAPPINGS = [
    _m("CUBIT-InSeg", "crack", "SURFACE_CRACK", 0.7,
       "Facade imagery, substrate not confirmed per-image - mapped to the generic SURFACE_CRACK."),
    _m("CUBIT-InSeg", "spalling", "SURFACE_DETACHMENT", 0.6,
       "Same reasoning as BD3's 'spalling': facade-level, substrate not confirmed per image."),
]


ALL_MAPPINGS: list[LabelMapping] = (
    MBDD2025_MAPPINGS + CONVID_MAPPINGS + CODEBRIM_MAPPINGS + BD3_MAPPINGS + CUBIT_INSEG_MAPPINGS
)

_BY_DATASET_AND_LABEL: dict[tuple[str, str], LabelMapping] = {
    (m.dataset, m.normalized_label): m for m in ALL_MAPPINGS
}


def lookup(dataset: str, original_label: str) -> LabelMapping | None:
    return _BY_DATASET_AND_LABEL.get((dataset, original_label.strip().lower()))


def mappings_for_dataset(dataset: str) -> list[LabelMapping]:
    return [m for m in ALL_MAPPINGS if m.dataset == dataset]
