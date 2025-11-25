package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.CoroutineBodyCompleted
import com.jh.proj.coroutineviz.events.CoroutineCancelled
import com.jh.proj.coroutineviz.events.CoroutineCompleted
import com.jh.proj.coroutineviz.events.CoroutineCreated
import com.jh.proj.coroutineviz.events.CoroutineFailed
import com.jh.proj.coroutineviz.events.CoroutineResumed
import com.jh.proj.coroutineviz.events.CoroutineStarted
import com.jh.proj.coroutineviz.events.CoroutineSuspended
import com.jh.proj.coroutineviz.events.ThreadAssigned
import com.jh.proj.coroutineviz.events.VizEvent
import com.jh.proj.coroutineviz.session.VizEventMain.Companion.logger
import org.slf4j.LoggerFactory

class EventApplier(
    private val snapshot: RuntimeSnapshot
) {

    fun apply(event: VizEvent) {
        when (event) {
            is CoroutineCreated -> handleCreated(event)
            is CoroutineStarted -> handleStarted(event)
            is CoroutineBodyCompleted -> handleBodyCompleted(event)
            is CoroutineCompleted -> handleCompleted(event)
            is CoroutineCancelled -> handleCancelled(event)
            is CoroutineSuspended -> handleSuspended(event)
            is CoroutineResumed -> handleResumed(event)
            is ThreadAssigned -> handleThreadAssigned(event)
            is CoroutineFailed -> handleFailed(event)
            // Later: DispatcherSelected, ThreadAssigned, Flow events, etc.
            else -> {
                // For now we ignore other event types
            }
        }
    }

    private fun handleCreated(e: CoroutineCreated) {
        snapshot.coroutines[e.coroutineId] = CoroutineNode(
            id = e.coroutineId,
            jobId = e.jobId,
            parentId = e.parentCoroutineId,
            scopeId = e.scopeId,
            label = e.label,
            state = CoroutineState.CREATED
        )
    }

    private fun handleStarted(e: CoroutineStarted) {
        val node = snapshot.coroutines[e.coroutineId]

        if (node == null) {
            logger.warn("Received CoroutineStarted for unknown coroutine: ${e.coroutineId}")
            return
        }

        if (node.state != CoroutineState.CREATED) {
            logger.warn("Invalid state transition: ${node.state} -> ACTIVE for ${e.coroutineId}")
        }

        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.ACTIVE
    }

    private fun handleBodyCompleted(e: CoroutineBodyCompleted) {
        val node = snapshot.coroutines[e.coroutineId]

        if (node == null) {
            logger.warn("Received CoroutineBodyCompleted for unknown coroutine: ${e.coroutineId}")
            return
        }

        // Transition to WAITING_FOR_CHILDREN state
        // This indicates the coroutine's code has finished but it's waiting for children
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.WAITING_FOR_CHILDREN
    }

    private fun handleCompleted(e: CoroutineCompleted) {
        // Final completion - all children have also completed
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.COMPLETED
    }

    private fun handleCancelled(e: CoroutineCancelled) {
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.CANCELLED
    }

    private fun handleSuspended(e: CoroutineSuspended) {
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.SUSPENDED
    }

    private fun handleResumed(e: CoroutineResumed) {
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.ACTIVE
    }

    private fun handleThreadAssigned(e: ThreadAssigned) {
        snapshot.coroutines[e.coroutineId]?.apply {
            threadId = e.threadId
            threadName = e.threadName
            dispatcherName = e.dispatcherName
        }
    }

    private fun handleFailed(e: CoroutineFailed) {
        snapshot.coroutines[e.coroutineId]?.state = CoroutineState.FAILED
    }

    companion object{
        private val logger = LoggerFactory.getLogger(EventApplier::class.java)
    }
}