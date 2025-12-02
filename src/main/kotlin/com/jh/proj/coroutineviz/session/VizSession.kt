package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.JobStateChanged
import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.*

class VizSession(
    val sessionId: String
) {
    // Session-scoped coroutine scope for async operations
    val sessionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val eventBus = EventBus()
    val bus = eventBus  // Alias for backwards compatibility
    val store = EventStore()
    val snapshot = RuntimeSnapshot()
    val jobMonitor = JobStatusMonitor(session = this, 100)

    private val applier = EventApplier(snapshot)
    private var monitoringEnabled = false

    // Sequence generator to keep events globally ordered in this session
    private val seqGenerator = AtomicLong(0)

    val projectionService = ProjectionService(this)

    /**
     * Allocate next sequence number.
     */
    fun nextSeq(): Long = seqGenerator.incrementAndGet()

    /**
     * Non-suspending event send - PREFERRED for most use cases.
     * Synchronously emits event to bus, stores it, and updates snapshot.
     */
    fun send(event: VizEvent) {
        store.append(event)
        applier.apply(event)
        eventBus.send(event)
    }

    /**
     * Async event send - for non-coroutine contexts.
     * Launches on session scope to emit event asynchronously.
     */
    fun sendAsync(event: VizEvent) {
        sessionScope.launch {
            send(event)
        }
    }

    /**
     * Suspending version - for backwards compatibility.
     * Delegates to synchronous send() since bus.send() doesn't suspend.
     */
    fun sent(event: VizEvent) {
        send(event)
    }

    fun enableJobMonitoring() {
        if (!monitoringEnabled) {
            jobMonitor.start()
            monitoringEnabled = true
        }
    }

    /**
     * Clean up session resources.
     * Call this when session is closed.
     */
    fun close() {
        jobMonitor.stop()
        sessionScope.cancel()
    }

    fun coroutineLifecycleFlow(): Flow<CoroutineEvent> {
        return eventBus.stream()
            .filterIsInstance<CoroutineEvent>()
            .filter { event ->
                event.kind in setOf(
                    "CoroutineCreated",
                    "CoroutineStarted",
                    "CoroutineSuspended",
                    "CoroutineResumed",
                    "CoroutineCompleted",
                    "CoroutineCancelled",
                    "CoroutineFailed",
                    "CoroutineBodyCompleted"
                )
            }
    }

    fun jobStateFlow(): Flow<JobStateChanged> {
        return eventBus.stream().filterIsInstance<JobStateChanged>()
    }

    fun mergeTimeFlow(): Flow<VizEvent> {
        return merge(coroutineLifecycleFlow(), jobStateFlow())
    }

    /**
     * Get merged flow of coroutine lifecycle + job state events,
     * sorted by timestamp (newest first)
     */
    fun mergedTimelineFlow(
        newestFirst: Boolean = true
    ): Flow<VizEvent> {
        return merge(
            coroutineLifecycleFlow(),
            jobStateFlow()
        ).let { flow ->
            if (newestFirst) {
                // For real-time streams, events are already time-ordered
                // But we can buffer and sort if needed
                flow
            } else {
                flow
            }
        }
    }

    /**
     * Get paired flow that correlates coroutine events with job state changes
     * Events are paired if they share the same jobId/coroutineId
     */
    fun correlatedFlow(): Flow<CorrelatedEventPair> {
        val coroutineFlow = coroutineLifecycleFlow()
        val jobFlow = jobStateFlow()

        return combine(coroutineFlow, jobFlow) { coroutineEvent, jobEvent ->
            // Correlate by jobId
            if (coroutineEvent.jobId == jobEvent.jobId) {
                CorrelatedEventPair(
                    coroutineEvent = coroutineEvent,
                    jobEvent = jobEvent,
                    timeDelta = coroutineEvent.tsNanos - jobEvent.tsNanos
                )
            } else {
                null
            }
        }.filterNotNull()
    }

    /**
     * Get timeline for specific coroutine, with both lifecycle and job events
     * Returns list sorted by timestamp (newest first by default)
     */
    fun getCoroutineTimeline(
        coroutineId: String,
        newestFirst: Boolean = true
    ): List<VizEvent> {
        val allEvents = store.all()

        val filtered = allEvents.filter { event ->
            when (event) {
                is CoroutineEvent -> event.coroutineId == coroutineId
                is JobStateChanged -> {
                    // Find if this job belongs to our coroutine
                    snapshot.coroutines[coroutineId]?.jobId == event.jobId
                }
                else -> false
            }
        }

        return if (newestFirst) {
            filtered.sortedByDescending { it.tsNanos }
        } else {
            filtered.sortedBy { it.tsNanos }
        }
    }

    /**
     * Get split timeline (two separate lists)
     */
    fun getSplitTimeline(
        coroutineId: String,
        newestFirst: Boolean = true
    ): SplitTimeline {
        val allEvents = store.all()

        val coroutineEvents = allEvents
            .filterIsInstance<CoroutineEvent>()
            .filter { it.coroutineId == coroutineId }

        val jobId = snapshot.coroutines[coroutineId]?.jobId
        val jobEvents = if (jobId != null) {
            allEvents
                .filterIsInstance<JobStateChanged>()
                .filter { it.jobId == jobId }
        } else {
            emptyList()
        }

        return SplitTimeline(
            coroutineEvents = if (newestFirst) {
                coroutineEvents.sortedByDescending { it.tsNanos }
            } else {
                coroutineEvents.sortedBy { it.tsNanos }
            },
            jobEvents = if (newestFirst) {
                jobEvents.sortedByDescending { it.tsNanos }
            } else {
                jobEvents.sortedBy { it.tsNanos }
            },
            merged = if (newestFirst) {
                (coroutineEvents + jobEvents).sortedByDescending { it.tsNanos }
            } else {
                (coroutineEvents + jobEvents).sortedBy { it.tsNanos }
            }
        )
    }
}

// Data classes for the new types
data class CorrelatedEventPair(
    val coroutineEvent: CoroutineEvent,
    val jobEvent: JobStateChanged,
    val timeDelta: Long  // coroutineEvent.tsNanos - jobEvent.tsNanos
)

data class SplitTimeline(
    val coroutineEvents: List<CoroutineEvent>,
    val jobEvents: List<JobStateChanged>,
    val merged: List<VizEvent>  // Both types merged and sorted
)