package com.jh.proj.coroutineviz.scenarios

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for scenario configuration API.
 * These are serializable for JSON transport from frontend.
 */

@Serializable
data class ActionDTO(
    val type: String, // "delay", "throw", "log", "custom"
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class CoroutineConfigDTO(
    val id: String,
    val label: String,
    val parentId: String? = null,
    val actions: List<ActionDTO> = emptyList(),
    val children: List<CoroutineConfigDTO> = emptyList()
)

@Serializable
data class ScenarioConfigRequest(
    val name: String,
    val description: String? = null,
    val sessionId: String? = null, // Optional - will create new session if not provided
    val root: CoroutineConfigDTO
)

@Serializable
data class ScenarioExecutionResponse(
    val success: Boolean,
    val sessionId: String,
    val message: String,
    val coroutineCount: Int = 0,
    val eventCount: Int = 0,
    val errors: List<String>? = null
)

/**
 * Convert DTO to internal configuration model
 */
fun ActionDTO.toAction(): CoroutineAction {
    return when (type.lowercase()) {
        "delay" -> {
            val duration = params["durationMs"]?.toLongOrNull() 
                ?: params["duration"]?.toLongOrNull() 
                ?: 1000L
            CoroutineAction.Delay(duration)
        }
        "throw", "exception" -> {
            val exceptionType = params["exceptionType"] ?: params["type"] ?: "RuntimeException"
            val message = params["message"] ?: "Test exception"
            CoroutineAction.ThrowException(exceptionType, message)
        }
        "log" -> {
            val message = params["message"] ?: ""
            CoroutineAction.Log(message)
        }
        "custom" -> {
            val code = params["code"] ?: ""
            CoroutineAction.CustomCode(code)
        }
        else -> throw IllegalArgumentException("Unknown action type: $type")
    }
}

fun CoroutineConfigDTO.toConfig(): CoroutineConfig {
    return CoroutineConfig(
        id = id,
        label = label,
        parentId = parentId,
        actions = actions.map { it.toAction() },
        children = children.map { it.toConfig() }
    )
}

fun ScenarioConfigRequest.toScenarioConfig(): ScenarioConfig {
    return ScenarioConfig(
        name = name,
        description = description,
        root = root.toConfig()
    )
}

