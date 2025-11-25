package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.JobStateChanged
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.session.EventContext
import com.jh.proj.coroutineviz.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext as currentCoroutineContext

/**
 * VizJob wraps a Job to track and visualize job lifecycle operations.
 * 
 * Uses EventContext to eliminate boilerplate when emitting events.
 * 
 * This wrapper intercepts:
 * - cancel() - when a job is explicitly cancelled
 * - join() - when a coroutine waits for this job to complete
 * - cancelAndJoin() - combination of both
 * 
 * All operations emit events to the VizSession for frontend visualization.
 * 
 * @param job The underlying Job to wrap
 * @param session The visualization session for tracking events
 * @param coroutineId The ID of the coroutine this job belongs to
 * @param jobId The unique ID for this job
 * @param parentCoroutineId The parent coroutine ID (if any)
 * @param scopeId The scope this job belongs to
 * @param label Optional label for this job
 */
@OptIn(InternalCoroutinesApi::class, DelicateCoroutinesApi::class)
class VizJob(
    private val job: Job,
    private val session: VizSession,
    private val coroutineId: String,
    private val jobId: String,
    private val parentCoroutineId: String?,
    private val scopeId: String,
    private val label: String?
) : Job by job {

    // Create context once for all event emissions
    private val ctx = EventContext(
        session = session,
        coroutineId = coroutineId,
        jobId = jobId,
        parentCoroutineId = parentCoroutineId,
        scopeId = scopeId,
        label = label
    )

    // Track previous state to detect changes
    private var lastState = JobState(
        isActive = job.isActive,
        isCompleted = job.isCompleted,
        isCancelled = job.isCancelled
    )

    init {
        // Start monitoring job state changes
        startStateMonitoring()
        
        // Emit initial state
        emitStateChange()
    }

    /**
     * Data class to track job state for comparison
     */
    private data class JobState(
        val isActive: Boolean,
        val isCompleted: Boolean,
        val isCancelled: Boolean
    )

    /**
     * Start background monitoring of job state changes.
     * This allows the frontend to see real-time state updates.
     */
    private fun startStateMonitoring() {
        // Launch a monitoring coroutine on the session scope
        session.sessionScope.launch {
            try {
                // Monitor until job completes
                while (!job.isCompleted) {
                    delay(10) // Check every 10ms for responsiveness
                    
                    val currentState = JobState(
                        isActive = job.isActive,
                        isCompleted = job.isCompleted,
                        isCancelled = job.isCancelled
                    )
                    
                    // Only emit if state changed
                    if (currentState != lastState) {
                        lastState = currentState
                        emitStateChange()
                    }
                }
                
                // Emit final state when completed
                emitStateChange()
                
            } catch (e: CancellationException) {
                // Monitor cancelled, this is fine
            } catch (e: Exception) {
                // Log error but don't crash
                println("Error monitoring job state: ${e.message}")
            }
        }
    }

    /**
     * Emit JobStateChanged event with current job state.
     */
    private fun emitStateChange() {
        session.send(
            JobStateChanged(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = coroutineId,
                jobId = jobId,
                parentCoroutineId = parentCoroutineId,
                scopeId = scopeId,
                label = label,
                isActive = job.isActive,
                isCompleted = job.isCompleted,
                isCancelled = job.isCancelled,
                childrenCount = job.children.count()
            )
        )
    }

    /**
     * Cancel this job with an optional cause.
     * Emits JobCancellationRequested event before cancelling.
     */
    override fun cancel(cause: CancellationException?) {
        // ✅ No GlobalScope needed - synchronous event emission
        session.send(
            ctx.jobCancellationRequested(
                requestedBy = null,
                cause = cause?.message
            )
        )
        
        // Perform the actual cancellation
        job.cancel(cause)
    }

    /**
     * Wait for this job to complete.
     * Emits JobJoinRequested before waiting and JobJoinCompleted after.
     */
    override suspend fun join() {
        // Get the coroutine that's calling join
        val callerElement = currentCoroutineContext()[VizCoroutineElement]
        
        // ✅ Clean one-liner
        session.send(ctx.jobJoinRequested(callerElement?.coroutineId))
        
        // Actually wait for the job
        job.join()
        
        // ✅ Clean one-liner
        session.send(ctx.jobJoinCompleted(callerElement?.coroutineId))
    }

    /**
     * Cancel and wait for this job to complete.
     * Combines cancel() and join() with proper event tracking.
     */
    suspend fun cancelAndJoin(cause: CancellationException? = null) {
        cancel(cause)
        join()
    }

    /**
     * Suspending version of cancel with full context tracking.
     * Use this instead of cancel() when called from suspend context
     * to capture the caller's coroutine ID.
     */
    suspend fun cancelTracked(cause: CancellationException? = null) {
        val callerElement = currentCoroutineContext()[VizCoroutineElement]
        
        // ✅ Can capture caller context
        session.send(
            ctx.jobCancellationRequested(
                requestedBy = callerElement?.coroutineId,
                cause = cause?.message
            )
        )
        
        job.cancel(cause)
    }

    // Delegate all other Job methods to the underlying job
    // (already handled by 'Job by job' delegation)
    
    /**
     * Get the underlying Job if needed for advanced operations.
     */
    fun unwrap(): Job = job
    
    override fun toString(): String {
        return "VizJob(jobId=$jobId, coroutineId=$coroutineId, label=$label, " +
               "state=${job.isActive}/${job.isCompleted}/${job.isCancelled})"
    }
}

/**
 * Extension function to wrap a regular Job as a VizJob.
 */
fun Job.toVizJob(
    session: VizSession,
    coroutineId: String,
    jobId: String,
    parentCoroutineId: String?,
    scopeId: String,
    label: String?
): VizJob {
    return VizJob(
        job = this,
        session = session,
        coroutineId = coroutineId,
        jobId = jobId,
        parentCoroutineId = parentCoroutineId,
        scopeId = scopeId,
        label = label
    )
}

