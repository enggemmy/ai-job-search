package com.defectview.domain.id

/**
 * Generates the human-facing defect identifier, e.g. DV-000152.
 *
 * This is a pure function over the highest sequence number issued so far - the caller (the
 * repository, which has access to Room) is responsible for reading that number transactionally
 * so two concurrent inserts never collide.
 */
object DefectIdGenerator {
    private const val PREFIX = "DV-"
    private const val PAD_LENGTH = 6

    fun next(lastSequence: Long): String {
        require(lastSequence >= 0) { "lastSequence must not be negative" }
        return format(lastSequence + 1)
    }

    fun format(sequence: Long): String {
        require(sequence in 1..999_999) { "sequence must be between 1 and 999999" }
        return PREFIX + sequence.toString().padStart(PAD_LENGTH, '0')
    }

    /** Parses a previously generated ID back into its sequence number, or null if malformed. */
    fun parseSequence(defectId: String): Long? {
        if (!defectId.startsWith(PREFIX)) return null
        return defectId.removePrefix(PREFIX).toLongOrNull()
    }
}
