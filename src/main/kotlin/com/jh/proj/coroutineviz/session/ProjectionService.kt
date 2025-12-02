package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ProjectionService(
    private val session: VizSession
) {
    // In-memory state
    private val coroutines = ConcurrentHashMap<String, HierarchyNode>()
    private val threadActivity = ConcurrentHashMap<String, MutableList<ThreadEvent>>()

    init {
        // Subscribe to event bus
        session.sessionScope.launch {
            session.eventBus.stream().collect { event ->
                processEvent(event)
            }
        }
    }

    private fun processEvent(event: VizEvent) {
        when (event) {
            is CoroutineCreated -> {
                coroutines[event.coroutineId] = HierarchyNode(
                    id = event.coroutineId,
                    parentId = event.parentCoroutineId,
                    name = event.label ?: event.coroutineId,
                    scopeId = event.scopeId,
                    state = "CREATED",
                    createdAtNanos = event.tsNanos,
                    jobId = event.jobId
                )

                // Add to parent's children list
                event.parentCoroutineId?.let { parentId ->
                    coroutines[parentId]?.let { parent ->
                        coroutines[parentId] = parent.copy(
                            children = parent.children + event.coroutineId
                        )
                    }
                }
            }

            is CoroutineStarted -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(state = "RUNNING")
                }
            }

            is ThreadAssigned -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        currentThreadId = event.threadId,
                        currentThreadName = event.threadName
                    )
                }

                // Track thread activity
                threadActivity.getOrPut(event.threadId.toString()) { mutableListOf() }
                    .add(ThreadEvent(
                        coroutineId = event.coroutineId,
                        threadId = event.threadId,
                        threadName = event.threadName,
                        timestamp = event.tsNanos,
                        eventType = "ASSIGNED",
                        dispatcherName = event.dispatcherName
                    ))
            }

            is CoroutineSuspended -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(state = "SUSPENDED")
                }
            }

            is CoroutineCompleted -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        state = "COMPLETED",
                        completedAtNanos = event.tsNanos
                    )
                }
            }

            is CoroutineCancelled -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        state = "CANCELLED",
                        completedAtNanos = event.tsNanos
                    )
                }
            }

            is CoroutineFailed -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        state = "FAILED",
                        completedAtNanos = event.tsNanos
                    )
                }
            }

            is CoroutineResumed -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(state = "RUNNING")
                }
            }

            is CoroutineBodyCompleted -> {
                // Body finished, but coroutine may still be waiting for children
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(state = "WAITING_FOR_CHILDREN")
                }
            }

            is DispatcherSelected -> {
                // Track dispatcher information
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        dispatcherId = event.dispatcherId,
                        dispatcherName = event.dispatcherName
                    )
                }
            }

            is WaitingForChildren -> {
                coroutines[event.coroutineId]?.let { node ->
                    coroutines[event.coroutineId] = node.copy(
                        state = "WAITING_FOR_CHILDREN",
                        activeChildrenIds = event.activeChildrenIds,      // ✨ NEW
                        activeChildrenCount = event.activeChildrenCount   // ✨ NEW
                    )
                }
            }

            is JobStateChanged -> {
                coroutines[event.coroutineId]?.let { node ->
                    val newState = when {
                        event.isCancelled -> "CANCELLED"
                        event.isCompleted -> "COMPLETED"
                        !event.isActive && event.childrenCount > 0 -> "WAITING_FOR_CHILDREN"
                        event.isActive -> "ACTIVE"
                        else -> node.state
                    }

                    coroutines[event.coroutineId] = node.copy(
                        state = newState
                    )
                }
            }

            // Ignore other event types (job operations, deferred, etc.)
            else -> { /* Ignore events we don't track in hierarchy */ }
        }
    }

    /**
     * Get hierarchy tree, optionally filtered by scopeId
     */
    fun getHierarchyTree(scopeId: String? = null): List<HierarchyNode> {
        val filtered = coroutines.values
            .filter { scopeId == null || it.scopeId == scopeId }

        // Find root nodes (no parent)
        val roots = filtered.filter { it.parentId == null }

        // Return full tree (children are referenced by IDs)
        return buildTree(roots, filtered)
    }

    private fun buildTree(roots: List<HierarchyNode>, allNodes: List<HierarchyNode>): List<HierarchyNode> {
        // Could return flat list or nested structure
        // Frontend can rebuild tree from parent/children IDs
        return allNodes.sortedBy { it.createdAtNanos }
    }

    /**
     * Get thread activity timeline
     */
    fun getThreadActivity(): Map<String, List<ThreadEvent>> {
        return threadActivity.mapValues { (_, events) ->
            events.sortedBy { it.timestamp }
        }
    }

    /**
     * Get timeline for specific coroutine
     */
    fun getCoroutineTimeline(coroutineId: String): CoroutineTimeline? {
        val node = coroutines[coroutineId] ?: return null

        // Could aggregate events for this coroutine
        // Return computed timeline with durations
        return CoroutineTimeline(
            coroutineId = coroutineId,
            name = node.name,
            state = node.state,
            totalDuration = node.completedAtNanos?.let { it - node.createdAtNanos },
            // ... more computed fields ...
        )
    }

    /**
     * Get all active children of a coroutine
     */
    fun getActiveChildren(coroutineId: String): List<HierarchyNode> {
        val parent = coroutines[coroutineId] ?: return emptyList()
        return parent.children.mapNotNull { childId ->
            coroutines[childId]?.takeIf {
                it.state in listOf("ACTIVE", "RUNNING", "SUSPENDED")
            }
        }
    }

    /**
     * Check if coroutine is waiting for children
     */
    fun isWaitingForChildren(coroutineId: String): Boolean {
        val node = coroutines[coroutineId] ?: return false
        return node.state == "WAITING_FOR_CHILDREN" ||
                (node.state == "BODY_COMPLETED" && getActiveChildren(coroutineId).isNotEmpty())
    }
}