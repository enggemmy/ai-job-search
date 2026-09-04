package com.defectview.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefectIdGeneratorTest {

    @Test
    fun `first defect gets sequence 1`() {
        assertEquals("DV-000001", DefectIdGenerator.next(lastSequence = 0))
    }

    @Test
    fun `next increments from last sequence and pads to six digits`() {
        assertEquals("DV-000153", DefectIdGenerator.next(lastSequence = 152))
    }

    @Test
    fun `format rejects zero and negative sequences`() {
        assertThrows(IllegalArgumentException::class.java) { DefectIdGenerator.format(0) }
    }

    @Test
    fun `next rejects negative lastSequence`() {
        assertThrows(IllegalArgumentException::class.java) { DefectIdGenerator.next(-1) }
    }

    @Test
    fun `parseSequence round-trips a generated id`() {
        val id = DefectIdGenerator.next(lastSequence = 41)
        assertEquals(42L, DefectIdGenerator.parseSequence(id))
    }

    @Test
    fun `parseSequence returns null for malformed input`() {
        assertNull(DefectIdGenerator.parseSequence("XX-000001"))
        assertNull(DefectIdGenerator.parseSequence("not-an-id"))
    }
}
