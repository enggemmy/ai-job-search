package com.defectview.domain.learning

import com.defectview.domain.model.LearningQueueStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LearningQueueStateMachineTest {

    @Test
    fun `pending can move to queued or discarded`() {
        assertTrue(LearningQueueStateMachine.canTransition(LearningQueueStatus.PENDING, LearningQueueStatus.QUEUED_FOR_TRAINING))
        assertTrue(LearningQueueStateMachine.canTransition(LearningQueueStatus.PENDING, LearningQueueStatus.DISCARDED))
    }

    @Test
    fun `pending cannot jump directly to included in model`() {
        assertFalse(LearningQueueStateMachine.canTransition(LearningQueueStatus.PENDING, LearningQueueStatus.INCLUDED_IN_MODEL))
    }

    @Test
    fun `terminal states have no outgoing transitions`() {
        assertFalse(LearningQueueStateMachine.canTransition(LearningQueueStatus.INCLUDED_IN_MODEL, LearningQueueStatus.DISCARDED))
        assertFalse(LearningQueueStateMachine.canTransition(LearningQueueStatus.DISCARDED, LearningQueueStatus.QUEUED_FOR_TRAINING))
    }

    @Test
    fun `transition throws on an illegal move`() {
        assertThrows(IllegalStateException::class.java) {
            LearningQueueStateMachine.transition(LearningQueueStatus.DISCARDED, LearningQueueStatus.PENDING)
        }
    }

    @Test
    fun `transition returns the new state on a legal move`() {
        val result = LearningQueueStateMachine.transition(LearningQueueStatus.QUEUED_FOR_TRAINING, LearningQueueStatus.INCLUDED_IN_MODEL)
        assertEquals(LearningQueueStatus.INCLUDED_IN_MODEL, result)
    }
}
