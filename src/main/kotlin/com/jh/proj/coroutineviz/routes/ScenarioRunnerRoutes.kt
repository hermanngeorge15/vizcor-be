package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.scenarios.ScenarioConfigRequest
import com.jh.proj.coroutineviz.scenarios.ScenarioExecutionResponse
import com.jh.proj.coroutineviz.scenarios.ScenarioRunner
import com.jh.proj.coroutineviz.scenarios.toScenarioConfig
import com.jh.proj.coroutineviz.session.SessionManager
import com.jh.proj.coroutineviz.session.VizSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerScenarioRunnerRoutes() {
    post("/api/scenarios/nested") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running nested coroutines scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runNestedCoroutines(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Scenario completed. Connect to /api/sessions/${session.sessionId}/stream for live events.",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/parallel") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running parallel execution scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runParallelExecution(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Parallel scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/cancellation") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running cancellation scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runCancellationScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Cancellation scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/deep-nesting") {
        val sessionId = call.request.queryParameters["sessionId"]
        val depth = call.request.queryParameters["depth"]?.toIntOrNull() ?: 5
        val session = getOrCreateSession(sessionId)

        logger.info("Running deep nesting scenario (depth=$depth) in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runDeepNesting(session, depth)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Deep nesting scenario completed (depth=$depth)",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/mixed") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running mixed scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runMixedScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Mixed scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/exception") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running exception scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runExceptionScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Exception scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/custom") {
        logger.info("Received custom scenario configuration request")

        try {
            val request = call.receive<ScenarioConfigRequest>()

            logger.info("Custom scenario: ${request.name}")
            logger.debug("Description: ${request.description}")

            val session = getOrCreateSession(request.sessionId)
            logger.info("Using session: ${session.sessionId}")

            val scenarioConfig = request.toScenarioConfig()
            ScenarioRunner.runCustomScenario(session, scenarioConfig)

            delay(100)

            val response = ScenarioExecutionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Custom scenario '${request.name}' completed successfully",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )

            logger.info(
                "✅ Custom scenario completed successfully | " +
                    "Coroutines: ${response.coroutineCount} | Events: ${response.eventCount}"
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid scenario configuration", e)
            call.respond(
                HttpStatusCode.BadRequest,
                ScenarioExecutionResponse(
                    success = false,
                    sessionId = "",
                    message = "Invalid scenario configuration: ${e.message}",
                    errors = listOf(e.message ?: "Unknown validation error")
                )
            )
        } catch (e: Exception) {
            logger.error("Error running custom scenario", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ScenarioExecutionResponse(
                    success = false,
                    sessionId = "",
                    message = "Error executing scenario: ${e.message}",
                    errors = listOf(e.stackTraceToString())
                )
            )
        }
    }

    get("/api/scenarios") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "scenarios" to listOf(
                    mapOf(
                        "id" to "nested",
                        "name" to "Nested Coroutines",
                        "description" to "Demonstrates parent-child relationships and structured concurrency",
                        "endpoint" to "/api/scenarios/nested"
                    ),
                    mapOf(
                        "id" to "parallel",
                        "name" to "Parallel Execution",
                        "description" to "Multiple coroutines running in parallel",
                        "endpoint" to "/api/scenarios/parallel"
                    ),
                    mapOf(
                        "id" to "cancellation",
                        "name" to "Cancellation",
                        "description" to "Demonstrates coroutine cancellation and cleanup",
                        "endpoint" to "/api/scenarios/cancellation"
                    ),
                    mapOf(
                        "id" to "deep-nesting",
                        "name" to "Deep Nesting",
                        "description" to "Deep hierarchy of nested coroutines (configurable depth)",
                        "endpoint" to "/api/scenarios/deep-nesting?depth=5"
                    ),
                    mapOf(
                        "id" to "mixed",
                        "name" to "Mixed Sequential/Parallel",
                        "description" to "Combination of sequential and parallel execution",
                        "endpoint" to "/api/scenarios/mixed"
                    ),
                    mapOf(
                        "id" to "exception",
                        "name" to "Exception Handling",
                        "description" to "Demonstrates exception tracking and failure states",
                        "endpoint" to "/api/scenarios/exception"
                    )
                )
            )
        )
    }
}

private suspend fun ApplicationCall.runScenarioWithResponse(
    session: VizSession,
    block: suspend () -> ScenarioCompletionResponse
) {
    try {
        val response = block()
        delay(100)
        respond(HttpStatusCode.OK, response)
    } catch (e: Exception) {
        logger.error("Error running scenario", e)
        respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Scenario failed: ${e.message}")
        )
    }
}

private suspend fun getOrCreateSession(sessionId: String?): VizSession {
    return sessionId?.let { SessionManager.getSession(it) }
        ?: SessionManager.createSession("auto-${System.currentTimeMillis()}")
}

