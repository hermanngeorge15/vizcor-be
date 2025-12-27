package com.jh.proj.coroutineviz.scenarios

import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.Job

/**
 * DSL for building dynamic coroutine scenarios from configuration.
 * 
 * This allows creating complex coroutine hierarchies with delays, exceptions,
 * and custom actions based on JSON/data configuration from the frontend.
 */

/**
 * Action types that can be performed in a coroutine
 */
sealed class CoroutineAction {
    data class Delay(val durationMs: Long) : CoroutineAction()
    data class ThrowException(val exceptionType: String, val message: String) : CoroutineAction()
    data class Log(val message: String) : CoroutineAction()
    data class CustomCode(val code: String) : CoroutineAction() // For future extension
}

/**
 * Configuration for a single coroutine in the scenario
 */
data class CoroutineConfig(
    val id: String,
    val label: String,
    val parentId: String? = null,
    val actions: List<CoroutineAction> = emptyList(),
    val children: List<CoroutineConfig> = emptyList()
)

/**
 * Root scenario configuration
 */
data class ScenarioConfig(
    val name: String,
    val description: String? = null,
    val root: CoroutineConfig
)

/**
 * DSL Builder for creating scenarios programmatically
 */
@DslMarker
annotation class ScenarioDslMarker

@ScenarioDslMarker
class CoroutineBuilder(
    var id: String = "",
    var label: String = ""
) {
    private val actions = mutableListOf<CoroutineAction>()
    private val children = mutableListOf<CoroutineConfig>()

    fun delay(durationMs: Long) {
        actions.add(CoroutineAction.Delay(durationMs))
    }

    fun throwException(type: String = "RuntimeException", message: String = "Test exception") {
        actions.add(CoroutineAction.ThrowException(type, message))
    }

    fun log(message: String) {
        actions.add(CoroutineAction.Log(message))
    }

    fun child(id: String, label: String, block: CoroutineBuilder.() -> Unit) {
        val builder = CoroutineBuilder(id, label)
        builder.block()
        children.add(builder.build(id))
    }

    fun build(parentId: String? = null): CoroutineConfig {
        return CoroutineConfig(
            id = id,
            label = label,
            parentId = parentId,
            actions = actions.toList(),
            children = children.toList()
        )
    }
}

@ScenarioDslMarker
class ScenarioBuilder(
    var name: String = "",
    var description: String? = null
) {
    private var rootConfig: CoroutineConfig? = null

    fun root(id: String, label: String, block: CoroutineBuilder.() -> Unit) {
        val builder = CoroutineBuilder(id, label)
        builder.block()
        rootConfig = builder.build()
    }

    fun build(): ScenarioConfig {
        return ScenarioConfig(
            name = name,
            description = description,
            root = rootConfig ?: throw IllegalStateException("Root coroutine not defined")
        )
    }
}

/**
 * DSL entry point for creating scenarios
 */
fun scenario(name: String, block: ScenarioBuilder.() -> Unit): ScenarioConfig {
    val builder = ScenarioBuilder(name = name)
    builder.block()
    return builder.build()
}

/**
 * Execute a coroutine configuration recursively
 */
suspend fun VizScope.executeCoroutineConfig(config: CoroutineConfig): Job {
    return vizLaunch(config.label) {
        // Execute all actions in sequence
        for (action in config.actions) {
            when (action) {
                is CoroutineAction.Delay -> {
                    vizDelay(action.durationMs)
                }
                is CoroutineAction.ThrowException -> {
                    throw createException(action.exceptionType, action.message)
                }
                is CoroutineAction.Log -> {
                    // Log action - events are already tracked by VizScope
                    println("[${config.label}] ${action.message}")
                }
                is CoroutineAction.CustomCode -> {
                    // For future extension - could evaluate Kotlin scripts
                    println("[${config.label}] Custom code execution not yet implemented: ${action.code}")
                }
            }
        }
    }
}

/**
 * Create an exception instance from a string type
 */
private fun createException(type: String, message: String): Exception {
    return when (type.lowercase()) {
        "illegalstateexception", "illegalstate" -> IllegalStateException(message)
        "illegalargumentexception", "illegalargument" -> IllegalArgumentException(message)
        "nullpointerexception", "nullpointer" -> NullPointerException(message)
        "unsupportedoperationexception", "unsupportedoperation" -> UnsupportedOperationException(message)
        "indexoutofboundsexception", "indexoutofbounds" -> IndexOutOfBoundsException(message)
        else -> RuntimeException(message)
    }
}

