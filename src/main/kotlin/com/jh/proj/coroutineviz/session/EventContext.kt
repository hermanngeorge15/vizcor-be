package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.SuspensionPoint
import com.jh.proj.coroutineviz.events.coroutine.*
import com.jh.proj.coroutineviz.events.dispatcher.ThreadAssigned
import com.jh.proj.coroutineviz.events.job.*

/**
 * EventContext carries common coroutine metadata for event creation.
 * 
 * This eliminates repetitive boilerplate when emitting events by providing
 * extension functions that automatically fill in common fields.
 * 
 * Usage:
 * ```kotlin
 * val ctx = EventContext(session, coroutineId, jobId, parentId, scopeId, label)
 * session.send(ctx.coroutineStarted())
 * session.send(ctx.jobJoinRequested(waitingCoroutineId))
 * ```
 */
data class EventContext(
    val session: VizSession,
    val coroutineId: String,
    val jobId: String,
    val parentCoroutineId: String?,
    val scopeId: String,
    val label: String?
) {
    // Common fields derived from context
    val sessionId: String get() = session.sessionId
    
    // Helper methods
    fun nextSeq(): Long = session.nextSeq()
    fun timestamp(): Long = System.nanoTime()
}

// ============================================================================
// Coroutine Lifecycle Events
// ============================================================================

fun EventContext.coroutineCreated(): CoroutineCreated = CoroutineCreated(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label
)

fun EventContext.coroutineStarted(): CoroutineStarted = CoroutineStarted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label
)

fun EventContext.coroutineBodyCompleted(): CoroutineBodyCompleted = CoroutineBodyCompleted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label
)

fun EventContext.coroutineCompleted(): CoroutineCompleted = CoroutineCompleted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label
)

fun EventContext.coroutineCancelled(cause: String?): CoroutineCancelled = CoroutineCancelled(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    cause = cause
)

fun EventContext.coroutineFailed(
    exceptionType: String?,
    message: String?
): CoroutineFailed = CoroutineFailed(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    exceptionType = exceptionType,
    stackTrace = emptyList(),
    message = message,
)

// ============================================================================
// Suspension & Resumption Events
// ============================================================================

fun EventContext.coroutineSuspended(
    reason: String,
    durationMillis: Long? = null,
    suspensionPoint: SuspensionPoint? = null,
): CoroutineSuspended = CoroutineSuspended(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    reason = reason,
    durationMillis = durationMillis,
    suspensionPoint = suspensionPoint,
)

fun EventContext.coroutineResumed(): CoroutineResumed = CoroutineResumed(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label
)

// ============================================================================
// Job Operation Events
// ============================================================================

fun EventContext.jobJoinRequested(
    waitingCoroutineId: String?
): JobJoinRequested = JobJoinRequested(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    waitingCoroutineId = waitingCoroutineId
)

fun EventContext.jobJoinCompleted(
    waitingCoroutineId: String?
): JobJoinCompleted = JobJoinCompleted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    waitingCoroutineId = waitingCoroutineId
)

fun EventContext.jobCancellationRequested(
    requestedBy: String?,
    cause: String?
): JobCancellationRequested = JobCancellationRequested(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    requestedBy = requestedBy,
    cause = cause
)

fun EventContext.jobStateChanged(
    isActive: Boolean,
    isCompleted: Boolean,
    isCancelled: Boolean,
    childrenCount: Int = 0
): JobStateChanged = JobStateChanged(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    isActive = isActive,
    isCompleted = isCompleted,
    isCancelled = isCancelled,
    childrenCount = childrenCount
)

// ============================================================================
// Thread Assignment Events
// ============================================================================

fun EventContext.threadAssigned(
    threadId: Long,
    threadName: String,
    dispatcherName: String?
): ThreadAssigned = ThreadAssigned(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId,
    jobId = jobId,
    parentCoroutineId = parentCoroutineId,
    scopeId = scopeId,
    label = label,
    threadId = threadId,
    threadName = threadName,
    dispatcherName = dispatcherName
)

