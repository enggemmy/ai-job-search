"""The DEFECT VIEW unified defect taxonomy (spec section 6).

This is a registry, not a closed enum: :func:`register_defect_type` lets a caller add
categories at runtime (e.g. a genuinely new defect class discovered during real inspections)
without touching this file or the application architecture around it. The seed list below is
exactly the taxonomy given in the product spec - nothing invented, nothing dropped.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Discipline(str, Enum):
    ARCHITECTURAL = "ARCHITECTURAL"
    MEP = "MEP"
    CIVIL_STRUCTURAL = "CIVIL_STRUCTURAL"
    GENERAL = "GENERAL"


@dataclass(frozen=True)
class DefectType:
    key: str
    discipline: Discipline
    display_name: str = field(default="")

    def __post_init__(self) -> None:
        if not self.key or self.key != self.key.upper():
            raise ValueError(f"Defect type key must be UPPER_SNAKE_CASE, got {self.key!r}")
        if not self.display_name:
            object.__setattr__(self, "display_name", self.key.replace("_", " ").title())


_REGISTRY: dict[str, DefectType] = {}


def register_defect_type(key: str, discipline: Discipline, display_name: str = "") -> DefectType:
    """Registers (or re-registers, idempotently, if identical) a defect type.

    Raises if the key already exists with a *different* discipline - that's almost always a
    mistake (two different concepts colliding on one key) rather than an intentional edit.
    """
    existing = _REGISTRY.get(key)
    defect_type = DefectType(key=key, discipline=discipline, display_name=display_name)
    if existing is not None and existing.discipline != defect_type.discipline:
        raise ValueError(
            f"{key!r} is already registered under {existing.discipline}, cannot re-register under {defect_type.discipline}"
        )
    _REGISTRY[key] = defect_type
    return defect_type


def get_defect_type(key: str) -> DefectType | None:
    return _REGISTRY.get(key)


def all_defect_types() -> list[DefectType]:
    return list(_REGISTRY.values())


def defect_types_for(discipline: Discipline) -> list[DefectType]:
    return [d for d in _REGISTRY.values() if d.discipline == discipline]


_ARCHITECTURAL_KEYS = [
    "CRACKED_TILE", "BROKEN_TILE", "CHIPPED_TILE", "TILE_LIPPAGE", "HOLLOW_TILE",
    "MISSING_GROUT", "POOR_GROUT", "MARBLE_CRACK", "MARBLE_DAMAGE", "STONE_DAMAGE",
    "STONE_COLOR_MISMATCH", "MATERIAL_COLOR_MISMATCH", "STAINING", "WALL_CRACK",
    "PLASTER_DAMAGE", "UNEVENT_SURFACE", "PAINT_PEELING", "PAINT_CRACKING",
    "PAINT_DISCOLORATION", "POOR_PAINT_FINISH", "CEILING_DAMAGE", "CEILING_CRACK",
    "DOOR_MISALIGNMENT", "DOOR_DAMAGE", "FRAME_DAMAGE", "EXCESSIVE_GAP",
    "SEALANT_FAILURE", "WINDOW_DAMAGE", "SCRATCHED_GLASS", "JOINERY_DAMAGE",
    "LAMINATE_PEELING", "INCOMPLETE_INSTALLATION", "INCORRECT_MATERIAL",
    "POOR_ALIGNMENT",
    # POOR_WORKMANSHIP is listed under both ARCHITECTURAL and GENERAL in the spec; kept once,
    # under GENERAL, since a single defectType key must resolve to exactly one discipline.
]

_MEP_KEYS = [
    "PIPE_LEAKAGE", "PIPE_CORROSION", "PIPE_DAMAGE", "MISSING_PIPE_SUPPORT",
    "INCORRECT_PIPE_SUPPORT", "POOR_PIPE_ALIGNMENT", "DAMAGED_INSULATION",
    "MISSING_INSULATION", "EXPOSED_INSULATION", "DUCT_DAMAGE", "DUCT_LEAKAGE",
    "POOR_DUCT_JOINT", "MISSING_DUCT_INSULATION", "DAMAGED_DUCT_INSULATION",
    "MISALIGNED_DIFFUSER", "INCORRECT_DIFFUSER", "GRILLE_DEFECT",
    "MISSING_ACCESS_PANEL", "EXPOSED_CABLE", "UNSUPPORTED_CABLE", "DAMAGED_CABLE",
    "DAMAGED_CONDUIT", "MISSING_CONDUIT", "INCORRECT_CONDUIT_ROUTING",
    "CABLE_TRAY_DAMAGE", "MISSING_TRAY_COVER", "POOR_CABLE_TRAY_ALIGNMENT",
    "FIRESTOPPING_DEFECT", "SPRINKLER_OBSTRUCTION", "SPRINKLER_MISALIGNMENT",
    "INCORRECT_VALVE", "MISSING_VALVE", "EQUIPMENT_CLEARANCE_ISSUE",
    "INCOMPLETE_MEP_INSTALLATION",
]

_CIVIL_STRUCTURAL_KEYS = [
    "CONCRETE_CRACK", "CONCRETE_SPALLING", "CONCRETE_HONEYCOMBING", "CONCRETE_VOID",
    "CONCRETE_CORROSION", "SURFACE_CRACK", "SURFACE_DETACHMENT", "SURFACE_BULGING",
    "WATER_LEAKAGE", "CORROSION", "MATERIAL_DETERIORATION",
]

_GENERAL_KEYS = [
    "MISSING_COMPONENT", "INCORRECT_INSTALLATION", "INCOMPLETE_WORK",
    "POOR_WORKMANSHIP", "DAMAGED_MATERIAL", "WRONG_MATERIAL", "ALIGNMENT_DEFECT",
    "UNKNOWN_ANOMALY",
]


def _seed() -> None:
    for key in _ARCHITECTURAL_KEYS:
        register_defect_type(key, Discipline.ARCHITECTURAL)
    for key in _MEP_KEYS:
        register_defect_type(key, Discipline.MEP)
    for key in _CIVIL_STRUCTURAL_KEYS:
        register_defect_type(key, Discipline.CIVIL_STRUCTURAL)
    for key in _GENERAL_KEYS:
        register_defect_type(key, Discipline.GENERAL)


_seed()
