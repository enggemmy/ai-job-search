package com.defectview.domain.learning

import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.Trade
import com.defectview.domain.model.VerificationStatus
import com.defectview.domain.model.VerifiedExample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SimilaritySearchTest {

    private fun example(id: Long, embedding: FloatArray) = VerifiedExample(
        id = id,
        originalImagePath = "/img/$id.jpg",
        croppedImagePath = null,
        aiPrediction = null,
        correction = null,
        finalLabel = "excess_mortar",
        boundingBox = null,
        trade = Trade.MASONRY,
        category = DefectCategory.WORKMANSHIP,
        embedding = embedding,
        verificationStatus = VerificationStatus.APPROVED,
        modelVersion = "v0",
        createdAt = 0L
    )

    @Test
    fun `identical vectors have similarity 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertTrue(abs(1f - SimilaritySearch.cosineSimilarity(v, v)) < 1e-5f)
    }

    @Test
    fun `orthogonal vectors have similarity 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, SimilaritySearch.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `mismatched dimensions throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            SimilaritySearch.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f))
        }
    }

    @Test
    fun `zero vector yields similarity 0 instead of NaN`() {
        val zero = floatArrayOf(0f, 0f)
        val other = floatArrayOf(1f, 1f)
        assertEquals(0f, SimilaritySearch.cosineSimilarity(zero, other))
    }

    @Test
    fun `topMatches ranks closest examples first and respects the limit`() {
        val query = floatArrayOf(1f, 0f)
        val close = example(1, floatArrayOf(0.9f, 0.1f))
        val far = example(2, floatArrayOf(0.1f, 0.9f))
        val exact = example(3, floatArrayOf(1f, 0f))

        val matches = SimilaritySearch.topMatches(query, listOf(far, close, exact), limit = 2, minSimilarity = 0f)

        assertEquals(2, matches.size)
        assertEquals(3L, matches[0].example.id)
        assertEquals(1L, matches[1].example.id)
    }

    @Test
    fun `topMatches filters out anything below minSimilarity`() {
        val query = floatArrayOf(1f, 0f)
        val far = example(2, floatArrayOf(0f, 1f))
        val matches = SimilaritySearch.topMatches(query, listOf(far), minSimilarity = 0.5f)
        assertTrue(matches.isEmpty())
    }
}
