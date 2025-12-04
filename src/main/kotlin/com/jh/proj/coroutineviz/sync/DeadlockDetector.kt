package com.jh.proj.coroutineviz.sync

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizMutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Deadlock detector for coroutine synchronization primitives.
 * 
 * This detector monitors mutex acquisition patterns and identifies:
 * 1. Actual deadlocks (circular wait conditions)
 * 2. Potential deadlock risks (inconsistent lock ordering)
 * 
 * The detector uses a wait-for graph algorithm to detect cycles.
 * 
 * Real-world applications:
 * - Database transaction deadlock prevention
 * - Resource allocation monitoring
 * - Multi-threaded application debugging
 */
class DeadlockDetector(
    private val session: VizSession
) {
    // Track what each coroutine is waiting for
    private val waitingFor = ConcurrentHashMap<String, MutexWait>()
    
    // Track what mutexes each coroutine holds
    private val holding = ConcurrentHashMap<String, MutableSet<MutexHold>>()
    
    // Track mutex ownership
    private val mutexOwners = ConcurrentHashMap<String, OwnerInfo>()
    
    data class MutexWait(
        val coroutineId: String,
        val coroutineLabel: String?,
        val mutexId: String,
        val mutexLabel: String?,
        val timestamp: Long
    )
    
    data class MutexHold(
        val mutexId: String,
        val mutexLabel: String?,
        val acquiredAt: Long
    )
    
    data class OwnerInfo(
        val coroutineId: String,
        val coroutineLabel: String?
    )
    
    /**
     * Record that a coroutine is waiting for a mutex
     */
    fun recordWaiting(
        coroutineId: String,
        coroutineLabel: String?,
        mutexId: String,
        mutexLabel: String?
    ) {
        waitingFor[coroutineId] = MutexWait(
            coroutineId, coroutineLabel, mutexId, mutexLabel, System.nanoTime()
        )
        
        // Check for deadlock after recording
        val result = detectDeadlock()
        handleDeadlockResult(result)
    }
    
    /**
     * Record that a coroutine has acquired a mutex
     */
    fun recordAcquired(
        coroutineId: String,
        coroutineLabel: String?,
        mutexId: String,
        mutexLabel: String?
    ) {
        // Remove from waiting
        waitingFor.remove(coroutineId)
        
        // Add to holding
        holding.getOrPut(coroutineId) { mutableSetOf() }
            .add(MutexHold(mutexId, mutexLabel, System.nanoTime()))
        
        // Update ownership
        mutexOwners[mutexId] = OwnerInfo(coroutineId, coroutineLabel)
    }
    
    /**
     * Record that a coroutine has released a mutex
     */
    fun recordReleased(
        coroutineId: String,
        mutexId: String
    ) {
        holding[coroutineId]?.removeIf { it.mutexId == mutexId }
        mutexOwners.remove(mutexId)
    }
    
    /**
     * Detect deadlock using cycle detection in wait-for graph
     */
    fun detectDeadlock(): DeadlockAnalysisResult {
        // Build the wait-for graph
        // Node: coroutineId
        // Edge: coroutineA -> coroutineB means A is waiting for a mutex held by B
        
        val waitForGraph = mutableMapOf<String, String>()
        
        for ((waitingCoroutine, waitInfo) in waitingFor) {
            val owner = mutexOwners[waitInfo.mutexId]
            if (owner != null && owner.coroutineId != waitingCoroutine) {
                waitForGraph[waitingCoroutine] = owner.coroutineId
            }
        }
        
        // Detect cycle using DFS
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        
        for (node in waitForGraph.keys) {
            val cycle = detectCycleDFS(node, waitForGraph, visited, recursionStack, path)
            if (cycle != null) {
                return buildDeadlockResult(cycle)
            }
        }
        
        // Check for potential deadlock patterns (inconsistent lock ordering)
        return checkPotentialDeadlock()
    }
    
    private fun detectCycleDFS(
        node: String,
        graph: Map<String, String>,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        path: MutableList<String>
    ): List<String>? {
        if (recursionStack.contains(node)) {
            // Found cycle - extract it from path
            val cycleStart = path.indexOf(node)
            return if (cycleStart >= 0) {
                path.subList(cycleStart, path.size) + node
            } else {
                listOf(node)
            }
        }
        
        if (visited.contains(node)) {
            return null
        }
        
        visited.add(node)
        recursionStack.add(node)
        path.add(node)
        
        val next = graph[node]
        if (next != null) {
            val cycle = detectCycleDFS(next, graph, visited, recursionStack, path)
            if (cycle != null) {
                return cycle
            }
        }
        
        recursionStack.remove(node)
        path.removeAt(path.size - 1)
        return null
    }
    
    private fun buildDeadlockResult(cycle: List<String>): DeadlockAnalysisResult.DeadlockFound {
        val cycleLabels = cycle.map { coroutineId ->
            waitingFor[coroutineId]?.coroutineLabel 
                ?: holding[coroutineId]?.firstOrNull()?.mutexLabel
        }
        
        val involvedMutexes = mutableSetOf<String>()
        val mutexLabels = mutableListOf<String?>()
        
        for (coroutineId in cycle) {
            waitingFor[coroutineId]?.let { 
                involvedMutexes.add(it.mutexId)
                mutexLabels.add(it.mutexLabel)
            }
            holding[coroutineId]?.forEach { 
                involvedMutexes.add(it.mutexId)
                mutexLabels.add(it.mutexLabel)
            }
        }
        
        return DeadlockAnalysisResult.DeadlockFound(
            cycle = cycle,
            cycleLabels = cycleLabels,
            involvedMutexes = involvedMutexes.toList(),
            mutexLabels = mutexLabels.distinct()
        )
    }
    
    private fun checkPotentialDeadlock(): DeadlockAnalysisResult {
        // Check for lock ordering violations
        // If a coroutine holds mutex A and requests mutex B,
        // and another coroutine ever held B then A (in that order),
        // we have a potential deadlock risk
        
        for ((coroutineId, waitInfo) in waitingFor) {
            val heldMutexes = holding[coroutineId] ?: continue
            
            for (held in heldMutexes) {
                // Check if any other coroutine has acquired these in opposite order
                for ((otherId, otherHeld) in holding) {
                    if (otherId == coroutineId) continue
                    
                    val holdsRequestedMutex = otherHeld.any { it.mutexId == waitInfo.mutexId }
                    val otherWaiting = waitingFor[otherId]
                    val waitingForHeldMutex = otherWaiting?.mutexId == held.mutexId
                    
                    if (holdsRequestedMutex && waitingForHeldMutex) {
                        return DeadlockAnalysisResult.PotentialDeadlock(
                            coroutineId = coroutineId,
                            holdingMutex = held.mutexId,
                            requestingMutex = waitInfo.mutexId,
                            reason = "Lock ordering violation: $coroutineId holds ${held.mutexId} and requests ${waitInfo.mutexId}, " +
                                    "but $otherId holds ${waitInfo.mutexId} and requests ${held.mutexId}"
                        )
                    }
                }
            }
        }
        
        return DeadlockAnalysisResult.NoDeadlock
    }
    
    private fun handleDeadlockResult(result: DeadlockAnalysisResult) {
        when (result) {
            is DeadlockAnalysisResult.DeadlockFound -> {
                // Build wait and hold graphs for the event
                val waitGraph = mutableMapOf<String, String>()
                val holdGraph = mutableMapOf<String, String>()
                
                for (coroutineId in result.cycle) {
                    waitingFor[coroutineId]?.let { 
                        waitGraph[coroutineId] = it.mutexId 
                    }
                    holding[coroutineId]?.forEach { held ->
                        holdGraph[held.mutexId] = coroutineId
                    }
                }
                
                session.send(
                    DeadlockDetected(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        involvedCoroutines = result.cycle,
                        involvedCoroutineLabels = result.cycleLabels,
                        involvedMutexes = result.involvedMutexes,
                        involvedMutexLabels = result.mutexLabels,
                        waitGraph = waitGraph,
                        holdGraph = holdGraph,
                        cycleDescription = buildCycleDescription(result)
                    )
                )
            }
            
            is DeadlockAnalysisResult.PotentialDeadlock -> {
                val waitInfo = waitingFor[result.coroutineId]
                val holdInfo = holding[result.coroutineId]?.find { it.mutexId == result.holdingMutex }
                
                session.send(
                    PotentialDeadlockWarning(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        coroutineId = result.coroutineId,
                        coroutineLabel = waitInfo?.coroutineLabel,
                        holdingMutex = result.holdingMutex,
                        holdingMutexLabel = holdInfo?.mutexLabel,
                        requestingMutex = result.requestingMutex,
                        requestingMutexLabel = waitInfo?.mutexLabel,
                        recommendation = "Consider acquiring locks in a consistent order (e.g., alphabetically by mutex label)"
                    )
                )
            }
            
            DeadlockAnalysisResult.NoDeadlock -> {
                // No action needed
            }
        }
    }
    
    private fun buildCycleDescription(result: DeadlockAnalysisResult.DeadlockFound): String {
        val parts = mutableListOf<String>()
        for (i in result.cycle.indices) {
            val coroutine = result.cycle[i]
            val label = result.cycleLabels.getOrNull(i) ?: coroutine
            val waitInfo = waitingFor[coroutine]
            val heldMutexes = holding[coroutine]?.map { it.mutexId } ?: emptyList()
            
            parts.add("$label holds [${heldMutexes.joinToString()}] and waits for [${waitInfo?.mutexId ?: "?"}]")
        }
        return parts.joinToString(" → ")
    }
    
    /**
     * Clear all tracking data
     */
    fun reset() {
        waitingFor.clear()
        holding.clear()
        mutexOwners.clear()
    }
}

