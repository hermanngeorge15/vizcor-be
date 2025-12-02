package com.jh.proj.coroutineviz.extension

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.JobStateChanged
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger

fun CoroutineScope.logAllContext(logger: Logger) {
    val ctx = coroutineContext
    ctx.fold("") { acc, element -> "$acc$element " }
        .also {
            logger.info("coroutine-context: $it")
        }
}

// Helper extension to derive state string from flags
fun JobStateChanged.deriveState(): String {
    return when {
        isCompleted && isCancelled -> "CANCELLED"
        isCompleted && !isCancelled -> "COMPLETED"
        isActive -> "ACTIVE"
        else -> "NEW"
    }
}

// Helper to format state change display
fun JobStateChanged.formatStateInfo(): String {
    return "${deriveState()} (active=$isActive, completed=$isCompleted, cancelled=$isCancelled, children=$childrenCount)"
}

//fun VizSession.analyzeJobStateTransitions(coroutineId: String) {
//    val timeline = getSplitTimeline(coroutineId, newestFirst = false)
//    val jobEvents = timeline.jobEvents
//
//    println("\n📊 JOB STATE TRANSITION ANALYSIS:")
//    println("=".repeat(60))
//
//    if (jobEvents.isEmpty()) {
//        println("No job state events found")
//        return
//    }
//
//    println("Initial State: ${jobEvents.first().deriveState()}")
//    println()
//
//    for (i in 1 until jobEvents.size) {
//        val prev = jobEvents[i - 1]
//        val curr = jobEvents[i]
//
//        val prevState = prev.deriveState()
//        val currState = curr.deriveState()
//        val timeDelta = (curr.tsNanos - prev.tsNanos) / 1_000_000.0
//
//        println("Transition ${i}:")
//        println("  From: $prevState")
//        println("    → isActive: ${prev.isActive}")
//        println("    → isCompleted: ${prev.isCompleted}")
//        println("    → isCancelled: ${prev.isCancelled}")
//        println("    → children: ${prev.childrenCount}")
//        println("  To:   $currState (+${timeDelta}ms)")
//        println("    → isActive: ${curr.isActive}")
//        println("    → isCompleted: ${curr.isCompleted}")
//        println("    → isCancelled: ${curr.isCancelled}")
//        println("    → children: ${curr.childrenCount}")
//
//        // Highlight what changed
//        val changes = mutableListOf<String>()
//        if (prev.isActive != curr.isActive) changes.add("active: ${prev.isActive}→${curr.isActive}")
//        if (prev.isCompleted != curr.isCompleted) changes.add("completed: ${prev.isCompleted}→${curr.isCompleted}")
//        if (prev.isCancelled != curr.isCancelled) changes.add("cancelled: ${prev.isCancelled}→${curr.isCancelled}")
//        if (prev.childrenCount != curr.childrenCount) changes.add("children: ${prev.childrenCount}→${curr.childrenCount}")
//
//        if (changes.isNotEmpty()) {
//            println("  Changes: ${changes.joinToString(", ")}")
//        }
//        println()
//    }
//
//    println("Final State: ${jobEvents.last().deriveState()}")
//    println("=".repeat(60))
//}