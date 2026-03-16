package com.jh.proj.coroutineviz.extension

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.JobStateChanged
import com.jh.proj.coroutineviz.events.VizEvent
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

// ============================================================================
// Data Classes
// ============================================================================

data class SplitTimeline(
    val coroutineEvents: List<CoroutineEvent>,
    val jobEvents: List<JobStateChanged>,
    val merged: List<VizEvent>
)

// ============================================================================
// Flow Extensions for VizSession
// ============================================================================

/**
 * Get flow of only coroutine lifecycle events
 * (Created, Started, Suspended, Resumed, Completed, Cancelled, Failed)
 */
fun VizSession.coroutineLifecycleFlow(): Flow<CoroutineEvent> {
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

/**
 * Get flow of only job state change events
 */
fun VizSession.jobStateFlow(): Flow<JobStateChanged> {
    return eventBus.stream()
        .filterIsInstance<JobStateChanged>()
}

/**
 * Get merged flow of coroutine lifecycle + job state events
 */
fun VizSession.mergedTimelineFlow(): Flow<VizEvent> {
    return merge(
        coroutineLifecycleFlow(),
        jobStateFlow()
    )
}


/**
 * Get merged timeline for specific coroutine
 */
fun VizSession.getCoroutineTimeline(
    coroutineId: String,
    newestFirst: Boolean = true
): List<VizEvent> {
    val timeline = getSplitTimeline(coroutineId, newestFirst)
    return timeline.merged
}

// ============================================================================
// Debug Print Extensions
// ============================================================================

/**
 * Print formatted timeline for a coroutine
 */
fun VizSession.printCoroutineTimeline(coroutineId: String) {
    val timeline = getSplitTimeline(coroutineId, newestFirst = true)
    
    println("\n${"=".repeat(80)}")
    println("TIMELINE FOR COROUTINE: $coroutineId")
    println("=".repeat(80))
    
    val startTime = timeline.merged.lastOrNull()?.tsNanos ?: 0L
    
    timeline.merged.forEach { event ->
        val elapsed = (event.tsNanos - startTime) / 1_000_000.0
        val icon = when (event) {
            is CoroutineEvent -> "🔵"
            is JobStateChanged -> "🟢"
            else -> "⚪"
        }
        
        val details = when (event) {
            is CoroutineEvent -> {
                event.label ?: event.coroutineId.take(8)
            }
            is JobStateChanged -> {
                "${event.deriveState()} [A:${event.isActive} C:${event.isCompleted} X:${event.isCancelled} ch:${event.childrenCount}]"
            }
            else -> ""
        }
        
        println(String.format("%s [%8.2fms] %-30s %s", 
            icon, elapsed, event.kind, details))
    }
    println("=".repeat(80))
}

/**
 * Compare job state progression with coroutine lifecycle
 */
fun VizSession.compareJobStateProgression(coroutineId: String) {
    val timeline = getSplitTimeline(coroutineId, newestFirst = false)  // Oldest first
    
    println("\n${"=".repeat(70)}")
    println("JOB STATE & COROUTINE LIFECYCLE COMPARISON")
    println("Coroutine: $coroutineId")
    println("=".repeat(70))
    
    println("\n🟢 JOB STATE PROGRESSION:")
    var previousState: String? = null
    timeline.jobEvents.forEach { event ->
        val currentState = event.deriveState()
        val transition = if (previousState != null) {
            "$previousState → $currentState"
        } else {
            "START → $currentState"
        }
        println("   $transition (children: ${event.childrenCount})")
        previousState = currentState
    }
    
    println("\n🔵 COROUTINE LIFECYCLE:")
    timeline.coroutineEvents.forEach { event ->
        println("   ${event.kind}")
    }
    
    println("\n🔗 INTERLEAVED TIMELINE:")
    timeline.merged.forEach { event ->
        when (event) {
            is CoroutineEvent -> {
                println("   🔵 ${event.kind}")
            }
            is JobStateChanged -> {
                println("   🟢 ${event.deriveState()} (active=${event.isActive}, completed=${event.isCompleted}, cancelled=${event.isCancelled}, children=${event.childrenCount})")
            }

            else -> {
                println("Dunno")
            }
        }
    }
    println("=".repeat(70))
}

/**
 * Analyze job state transitions in detail
 */
fun VizSession.analyzeJobStateTransitions(coroutineId: String) {
    val timeline = getSplitTimeline(coroutineId, newestFirst = false)
    val jobEvents = timeline.jobEvents
    
    println("\n${"=".repeat(70)}")
    println("📊 JOB STATE TRANSITION ANALYSIS")
    println("Coroutine: $coroutineId")
    println("=".repeat(70))
    
    if (jobEvents.isEmpty()) {
        println("No job state events found")
        return
    }
    
    println("\nInitial State: ${jobEvents.first().deriveState()}")
    println()
    
    for (i in 1 until jobEvents.size) {
        val prev = jobEvents[i - 1]
        val curr = jobEvents[i]
        
        val prevState = prev.deriveState()
        val currState = curr.deriveState()
        val timeDelta = (curr.tsNanos - prev.tsNanos) / 1_000_000.0
        
        println("Transition $i:")
        println("  From: $prevState")
        println("    → isActive: ${prev.isActive}")
        println("    → isCompleted: ${prev.isCompleted}")
        println("    → isCancelled: ${prev.isCancelled}")
        println("    → children: ${prev.childrenCount}")
        println("  To:   $currState (+${timeDelta}ms)")
        println("    → isActive: ${curr.isActive}")
        println("    → isCompleted: ${curr.isCompleted}")
        println("    → isCancelled: ${curr.isCancelled}")
        println("    → children: ${curr.childrenCount}")
        
        // Highlight what changed
        val changes = mutableListOf<String>()
        if (prev.isActive != curr.isActive) changes.add("active: ${prev.isActive}→${curr.isActive}")
        if (prev.isCompleted != curr.isCompleted) changes.add("completed: ${prev.isCompleted}→${curr.isCompleted}")
        if (prev.isCancelled != curr.isCancelled) changes.add("cancelled: ${prev.isCancelled}→${curr.isCancelled}")
        if (prev.childrenCount != curr.childrenCount) changes.add("children: ${prev.childrenCount}→${curr.childrenCount}")
        
        if (changes.isNotEmpty()) {
            println("  Changes: ${changes.joinToString(", ")}")
        }
        println()
    }
    
    println("Final State: ${jobEvents.last().deriveState()}")
    println("=".repeat(70))
}

/**
 * Print summary of all coroutines in session
 */
fun VizSession.printSessionSummary() {
    println("\n${"=".repeat(80)}")
    println("SESSION SUMMARY: $sessionId")
    println("=".repeat(80))
    
    val coroutines = snapshot.coroutines.values
    println("Total coroutines: ${coroutines.size}")
    println()
    
    coroutines.forEach { coroutine ->
        println("🔷 ${coroutine.label ?: coroutine.id.take(8)}")
        println("   State: ${coroutine.state}")
        println("   Job: ${coroutine.jobId.take(8)}")
        println("   Parent: ${coroutine.parentId?.take(8) ?: "none"}")
        
        val timeline = getSplitTimeline(coroutine.id, newestFirst = false)
        println("   Events: ${timeline.coroutineEvents.size} coroutine, ${timeline.jobEvents.size} job")
        println()
    }
    println("=".repeat(80))
}

