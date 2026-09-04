package com.defectview.domain.learning

import com.defectview.domain.model.VerifiedExample
import kotlin.math.sqrt

data class SimilarityMatch(val example: VerifiedExample, val score: Float)

/**
 * Retrieves verified examples whose feature embedding is closest (cosine similarity) to a query
 * embedding. This is explicitly NOT neural-network retraining - it is nearest-neighbor lookup
 * over examples an inspector has already verified, used to surface "cases like this one" and to
 * bias suggestions before enough data exists to justify a real training run (spec section 4C).
 */
object SimilaritySearch {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embeddings must have the same dimensionality (${a.size} vs ${b.size})" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
    }

    /**
     * Returns up to [limit] verified examples most similar to [queryEmbedding], sorted
     * descending by similarity, filtering out anything below [minSimilarity].
     */
    fun topMatches(
        queryEmbedding: FloatArray,
        candidates: List<VerifiedExample>,
        limit: Int = 5,
        minSimilarity: Float = 0.5f
    ): List<SimilarityMatch> {
        require(limit > 0) { "limit must be positive" }
        return candidates
            .asSequence()
            .map { SimilarityMatch(it, cosineSimilarity(queryEmbedding, it.embedding)) }
            .filter { it.score >= minSimilarity }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }
}
