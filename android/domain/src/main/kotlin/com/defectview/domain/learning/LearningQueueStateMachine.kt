package com.defectview.domain.learning

import com.defectview.domain.model.LearningQueueStatus

/**
 * Legal transitions for a [com.defectview.domain.model.LearningQueueEntry]. Enforced centrally
 * so no screen or repository can push an entry through an invalid state (e.g. straight from
 * DISCARDED back to INCLUDED_IN_MODEL).
 */
object LearningQueueStateMachine {

    private val allowedTransitions: Map<LearningQueueStatus, Set<LearningQueueStatus>> = mapOf(
        LearningQueueStatus.PENDING to setOf(LearningQueueStatus.QUEUED_FOR_TRAINING, LearningQueueStatus.DISCARDED),
        LearningQueueStatus.QUEUED_FOR_TRAINING to setOf(LearningQueueStatus.INCLUDED_IN_MODEL, LearningQueueStatus.DISCARDED),
        LearningQueueStatus.INCLUDED_IN_MODEL to emptySet(),
        LearningQueueStatus.DISCARDED to emptySet()
    )

    fun canTransition(from: LearningQueueStatus, to: LearningQueueStatus): Boolean =
        to in (allowedTransitions[from] ?: emptySet())

    /** @throws IllegalStateException if the transition is not legal. */
    fun transition(from: LearningQueueStatus, to: LearningQueueStatus): LearningQueueStatus {
        check(canTransition(from, to)) { "Illegal learning queue transition: $from -> $to" }
        return to
    }
}
