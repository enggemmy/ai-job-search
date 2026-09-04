package com.defectview.domain.taxonomy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefectTaxonomyTest {

    @Test
    fun `spec-mandated masonry defect types are all present`() {
        val keys = DefectTaxonomy.masonry.map { it.key }.toSet()
        assertEquals(
            setOf(
                "excess_mortar", "uneven_plaster", "poor_finishing", "cracks",
                "honeycombing", "damaged_blockwork", "missing_plaster", "poor_cleanliness"
            ),
            keys
        )
    }

    @Test
    fun `spec-mandated finishes defect types are all present`() {
        val keys = DefectTaxonomy.architecturalFinishes.map { it.key }.toSet()
        assertEquals(
            setOf(
                "tile_damage", "tile_misalignment", "uneven_grout", "missing_sealant",
                "paint_defects", "marble_defects", "porcelain_defects", "skirting_defects"
            ),
            keys
        )
    }

    @Test
    fun `every defect type has a non-fabricated recommendation`() {
        DefectTaxonomy.all.forEach { type ->
            assertTrue(type.defaultRecommendation.isNotBlank(), "recommendation missing for ${type.key}")
        }
    }

    @Test
    fun `find resolves a known key and returns null for unknown`() {
        assertNotNull(DefectTaxonomy.find("excess_mortar"))
        assertNull(DefectTaxonomy.find("does_not_exist"))
    }

    @Test
    fun `all list has no duplicate keys`() {
        val keys = DefectTaxonomy.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
