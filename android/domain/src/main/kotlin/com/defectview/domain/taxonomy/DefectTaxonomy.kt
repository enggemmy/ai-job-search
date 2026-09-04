package com.defectview.domain.taxonomy

import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.Trade

/**
 * A defect type the vision/reasoning engines can reference. [key] is a stable identifier used
 * as the [com.defectview.domain.model.DefectDetection.defectCategoryHint] value; [displayName]
 * is what an inspector sees.
 */
data class DefectTypeDefinition(
    val key: String,
    val displayName: String,
    val trade: Trade,
    val defaultCategory: DefectCategory,
    val defaultRecommendation: String
)

/** The initial supported defect categories, as specified in the product brief (section 4A). */
object DefectTaxonomy {

    private const val VERIFY_SPEC = "Verify against the approved project specification and method statement."

    val masonry: List<DefectTypeDefinition> = listOf(
        DefectTypeDefinition("excess_mortar", "Excess Mortar", Trade.MASONRY, DefectCategory.WORKMANSHIP,
            "Remove excess mortar, clean the affected area, and reapply plaster in accordance with the approved project specification."),
        DefectTypeDefinition("uneven_plaster", "Uneven Plaster", Trade.MASONRY, DefectCategory.WORKMANSHIP,
            "Rectify plaster flatness/level to the tolerance in the approved specification. $VERIFY_SPEC"),
        DefectTypeDefinition("poor_finishing", "Poor Finishing", Trade.MASONRY, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("cracks", "Cracks", Trade.MASONRY, DefectCategory.WORKMANSHIP,
            "Investigate crack cause (structural vs. surface) before repair. $VERIFY_SPEC"),
        DefectTypeDefinition("honeycombing", "Honeycombing", Trade.MASONRY, DefectCategory.WORKMANSHIP,
            "Assess structural significance; repair per approved concrete repair method statement. $VERIFY_SPEC"),
        DefectTypeDefinition("damaged_blockwork", "Damaged Blockwork", Trade.MASONRY, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("missing_plaster", "Missing Plaster", Trade.MASONRY, DefectCategory.INCOMPLETE_WORK, VERIFY_SPEC),
        DefectTypeDefinition("poor_cleanliness", "Poor Cleanliness", Trade.MASONRY, DefectCategory.CLEANLINESS,
            "Clean work area and remove construction debris/residue.")
    )

    val architecturalFinishes: List<DefectTypeDefinition> = listOf(
        DefectTypeDefinition("tile_damage", "Tile Damage", Trade.ARCHITECTURAL_FINISHES, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("tile_misalignment", "Tile Misalignment", Trade.ARCHITECTURAL_FINISHES, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("uneven_grout", "Uneven Grout", Trade.ARCHITECTURAL_FINISHES, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("missing_sealant", "Missing Sealant", Trade.ARCHITECTURAL_FINISHES, DefectCategory.INCOMPLETE_WORK, VERIFY_SPEC),
        DefectTypeDefinition("paint_defects", "Paint Defects", Trade.ARCHITECTURAL_FINISHES, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("marble_defects", "Marble Defects", Trade.ARCHITECTURAL_FINISHES, DefectCategory.MATERIAL, VERIFY_SPEC),
        DefectTypeDefinition("porcelain_defects", "Porcelain Defects", Trade.ARCHITECTURAL_FINISHES, DefectCategory.MATERIAL, VERIFY_SPEC),
        DefectTypeDefinition("skirting_defects", "Skirting Defects", Trade.ARCHITECTURAL_FINISHES, DefectCategory.WORKMANSHIP, VERIFY_SPEC)
    )

    val general: List<DefectTypeDefinition> = listOf(
        DefectTypeDefinition("incomplete_work", "Incomplete Work", Trade.GENERAL, DefectCategory.INCOMPLETE_WORK, VERIFY_SPEC),
        DefectTypeDefinition("visible_damage", "Visible Damage", Trade.GENERAL, DefectCategory.OTHER, VERIFY_SPEC),
        DefectTypeDefinition("possible_installation_issue", "Possible Installation Issue", Trade.GENERAL, DefectCategory.WORKMANSHIP, VERIFY_SPEC),
        DefectTypeDefinition("possible_workmanship_issue", "Possible Workmanship Issue", Trade.GENERAL, DefectCategory.WORKMANSHIP, VERIFY_SPEC)
    )

    val all: List<DefectTypeDefinition> = masonry + architecturalFinishes + general

    private val byKey: Map<String, DefectTypeDefinition> = all.associateBy { it.key }

    fun find(key: String): DefectTypeDefinition? = byKey[key]
}
