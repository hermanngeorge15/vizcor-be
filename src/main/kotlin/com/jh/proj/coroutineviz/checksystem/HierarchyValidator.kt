package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.session.VizSession

/**
 * Validates coroutine hierarchy (parent-child tree structure).
 *
 * Use cases:
 * - Verify parent-child relationships
 * - Validate tree depth
 * - Check sibling relationships
 * - Verify scope isolation
 *
 * Usage:
 * ```
 * val validator = HierarchyValidator(session, recorder)
 * validator.validateParentChild("parent", "child-1").assertSuccess()
 * validator.validateSiblings("child-1", "child-2").assertSuccess()
 * validator.validateTreeDepth("grandchild", 3).assertSuccess()
 * ```
 */
class HierarchyValidator(
    private val session: VizSession,
    private val recorder: EventRecorder
) {

    /**
     * Validates that a coroutine is the parent of another.
     */
    fun validateParentChild(parentLabel: String, childLabel: String): OrderResult {
        val childCreated = recorder.findAll(
            EventSelector.labeled(childLabel, "CoroutineCreated")
        ).filterIsInstance<CoroutineEvent>().firstOrNull()
            ?: return OrderResult.Failure(
                message = "Child coroutine not found",
                context = mapOf("childLabel" to childLabel)
            )

        val parentCreated = recorder.findAll(
            EventSelector.labeled(parentLabel, "CoroutineCreated")
        ).filterIsInstance<CoroutineEvent>().firstOrNull()
            ?: return OrderResult.Failure(
                message = "Parent coroutine not found",
                context = mapOf("parentLabel" to parentLabel)
            )

        // Check parentCoroutineId matches
        return if (childCreated.parentCoroutineId == parentCreated.coroutineId) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Parent-child relationship not found",
                expected = "child.parentCoroutineId == parent.coroutineId",
                actual = "child.parentCoroutineId=${childCreated.parentCoroutineId}, parent.coroutineId=${parentCreated.coroutineId}",
                context = mapOf(
                    "parent" to parentLabel,
                    "child" to childLabel
                )
            )
        }
    }

    /**
     * Validates that multiple coroutines are siblings (same parent).
     */
    fun validateSiblings(vararg siblingLabels: String): OrderResult {
        if (siblingLabels.size < 2) {
            return OrderResult.Failure("Need at least 2 siblings to validate")
        }

        val siblings = siblingLabels.map { label ->
            recorder.findAll(EventSelector.labeled(label, "CoroutineCreated"))
                .filterIsInstance<CoroutineEvent>()
                .firstOrNull()
                ?: return OrderResult.Failure(
                    message = "Sibling coroutine not found",
                    context = mapOf("label" to label)
                )
        }

        val parentIds = siblings.map { it.parentCoroutineId }.toSet()

        return if (parentIds.size == 1) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutines are not siblings (different parents)",
                expected = "All siblings have same parentCoroutineId",
                actual = "Found ${parentIds.size} different parents: $parentIds",
                context = mapOf("siblings" to siblingLabels.toList())
            )
        }
    }

    /**
     * Validates that a coroutine has no parent (is a root coroutine).
     */
    fun validateIsRoot(label: String): OrderResult {
        val created = recorder.findAll(EventSelector.labeled(label, "CoroutineCreated"))
            .filterIsInstance<CoroutineEvent>()
            .firstOrNull()
            ?: return OrderResult.Failure(
                message = "Coroutine not found",
                context = mapOf("label" to label)
            )

        return if (created.parentCoroutineId == null) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine is not a root (has parent)",
                expected = "parentCoroutineId == null",
                actual = "parentCoroutineId=${created.parentCoroutineId}",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates the expected number of children for a parent.
     */
    fun validateChildCount(parentLabel: String, expectedCount: Int): OrderResult {
        val parentCreated = recorder.findAll(
            EventSelector.labeled(parentLabel, "CoroutineCreated")
        ).filterIsInstance<CoroutineEvent>().firstOrNull()
            ?: return OrderResult.Failure(
                message = "Parent coroutine not found",
                context = mapOf("parentLabel" to parentLabel)
            )

        val children = recorder.all()
            .filterIsInstance<CoroutineEvent>()
            .filter { it.kind == "CoroutineCreated" && it.parentCoroutineId == parentCreated.coroutineId }

        return if (children.size == expectedCount) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Unexpected child count for '$parentLabel'",
                expected = expectedCount,
                actual = children.size,
                context = mapOf(
                    "parent" to parentLabel,
                    "childLabels" to children.mapNotNull { it.label }
                )
            )
        }
    }

    /**
     * Validates the depth of a coroutine in the tree (1 = root).
     */
    fun validateTreeDepth(label: String, expectedDepth: Int): OrderResult {
        val actualDepth = calculateDepth(label)
            ?: return OrderResult.Failure(
                message = "Could not calculate depth for coroutine",
                context = mapOf("label" to label)
            )

        return if (actualDepth == expectedDepth) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Unexpected tree depth for '$label'",
                expected = expectedDepth,
                actual = actualDepth,
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that all coroutines belong to the same scope.
     */
    fun validateSameScope(vararg labels: String): OrderResult {
        val coroutines = labels.map { label ->
            recorder.findAll(EventSelector.labeled(label, "CoroutineCreated"))
                .filterIsInstance<CoroutineEvent>()
                .firstOrNull()
                ?: return OrderResult.Failure(
                    message = "Coroutine not found",
                    context = mapOf("label" to label)
                )
        }

        val scopeIds = coroutines.map { it.scopeId }.toSet()

        return if (scopeIds.size == 1) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutines belong to different scopes",
                expected = "All coroutines have same scopeId",
                actual = "Found ${scopeIds.size} different scopes: $scopeIds",
                context = mapOf("labels" to labels.toList())
            )
        }
    }

    /**
     * Gets all children of a parent coroutine.
     */
    fun getChildren(parentLabel: String): List<String> {
        val parentCreated = recorder.findAll(
            EventSelector.labeled(parentLabel, "CoroutineCreated")
        ).filterIsInstance<CoroutineEvent>().firstOrNull() ?: return emptyList()

        return recorder.all()
            .filterIsInstance<CoroutineEvent>()
            .filter { it.kind == "CoroutineCreated" && it.parentCoroutineId == parentCreated.coroutineId }
            .mapNotNull { it.label }
    }

    /**
     * Gets all descendants (children, grandchildren, etc.) of a parent.
     */
    fun getAllDescendants(parentLabel: String): List<String> {
        val result = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(parentLabel)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = getChildren(current)
            result.addAll(children)
            queue.addAll(children)
        }

        return result
    }

    /**
     * Validates the complete hierarchy tree structure.
     */
    fun validateHierarchy(
        rootLabel: String,
        expectedStructure: Map<String, List<String>> // parent -> children
    ): OrderResult {
        for ((parent, expectedChildren) in expectedStructure) {
            val actualChildren = getChildren(parent).toSet()
            val expected = expectedChildren.toSet()

            if (actualChildren != expected) {
                return OrderResult.Failure(
                    message = "Hierarchy mismatch for '$parent'",
                    expected = expectedChildren,
                    actual = actualChildren.toList(),
                    context = mapOf(
                        "parent" to parent,
                        "missing" to (expected - actualChildren),
                        "extra" to (actualChildren - expected)
                    )
                )
            }
        }

        return OrderResult.Success
    }

    private fun calculateDepth(label: String): Int? {
        var depth = 1
        var current = recorder.findAll(EventSelector.labeled(label, "CoroutineCreated"))
            .filterIsInstance<CoroutineEvent>()
            .firstOrNull() ?: return null

        while (current.parentCoroutineId != null) {
            depth++
            val parentId = current.parentCoroutineId!!
            current = recorder.all()
                .filterIsInstance<CoroutineEvent>()
                .find { it.kind == "CoroutineCreated" && it.coroutineId == parentId }
                ?: break
        }

        return depth
    }
}

