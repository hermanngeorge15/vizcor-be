package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.events.coroutine.CoroutineCompleted
import com.jh.proj.coroutineviz.events.coroutine.CoroutineCreated
import com.jh.proj.coroutineviz.events.coroutine.CoroutineStarted
import com.jh.proj.coroutineviz.examples.dispatcherExampleScenario
import com.jh.proj.coroutineviz.examples.VizEventMain
import com.jh.proj.coroutineviz.session.VizSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerVizScenarioRoutes() {
    get("/api/viz/nested-launch-scenario") {
        call.runVizEventMainScenario("Nested Launch Scenario") {
            exampleOfNestedLaunches()
        }
    }

    get("/api/viz/one-launch-scenario") {
        call.runVizEventMainScenario("Single Launch Scenario") {
            exampleSimpleLaunchVisualization()
        }
    }

    get("/api/viz/two-launch-scenario") {
        call.runVizEventMainScenario("Two Launch Scenario") {
            exampleOfTwoLanuches()
        }
    }

    get("/api/viz/run-scenario-with-data") {
        val sessionId = "api-session-${System.currentTimeMillis()}"
        logger.info("┌─────────────────────────────────────────┐")
        logger.info("│  Starting Scenario with Data Capture   │")
        logger.info("│  Session: $sessionId".padEnd(42) + "│")
        logger.info("└─────────────────────────────────────────┘")

        try {
            val session = VizSession(sessionId = sessionId)
            logger.debug("✓ VizSession created with sessionId: $sessionId")

            logger.debug("⚙️  Executing scenario and capturing data...")
            val result = runScenarioAndCaptureData(session)

            logger.info("✅ Scenario completed | Events: ${result.eventCount} | Coroutines: ${result.coroutines.size}")
            logger.debug("📋 Coroutines in snapshot: ${result.coroutines.map { it.id }}")

            call.respond(HttpStatusCode.OK, result)
        } catch (e: Exception) {
            logger.error("❌ Error executing scenario with data capture for session: $sessionId", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ScenarioResponse(
                    success = false,
                    message = "Error executing scenario: ${e.message}"
                )
            )
        }
    }

    get("/api/examples/dispatcher-scenario") {
        logger.info("Running Example: Dispatcher Scenario")
        try {
            dispatcherExampleScenario()
            call.respond(
                HttpStatusCode.OK,
                ScenarioResponse(
                    success = true,
                    message = "✅ Dispatcher example completed! Check logs for details."
                )
            )
        } catch (e: Exception) {
            logger.error("Dispatcher example failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ScenarioResponse(
                    success = false,
                    message = "❌ Dispatcher example failed: ${e.message}"
                )
            )
        }
    }
}

private suspend fun ApplicationCall.runVizEventMainScenario(
    label: String,
    scenario: suspend VizEventMain.() -> Unit
) {
    logger.info("┌─────────────────────────────────────────┐")
    logger.info("│  Starting VizEventMain Scenario        │")
    logger.info("│  Scenario: $label".padEnd(42) + "│")
    logger.info("└─────────────────────────────────────────┘")

    try {
        val vizMain = VizEventMain()

        logger.debug("VizEventMain instance created, executing scenario: $label")
        vizMain.scenario()

        logger.info("✅ VizEventMain scenario '$label' completed successfully")

        respond(
            HttpStatusCode.OK,
            ScenarioResponse(
                success = true,
                message = "Scenario executed successfully. Check server logs for output."
            )
        )
    } catch (e: Exception) {
        logger.error("❌ Error executing VizEventMain scenario '$label'", e)
        respond(
            HttpStatusCode.InternalServerError,
            ScenarioResponse(
                success = false,
                message = "Error executing scenario: ${e.message}"
            )
        )
    }
}

/**
 * Run the scenario and capture all events and snapshot data
 */
private suspend fun runScenarioAndCaptureData(session: VizSession): ScenarioResultData {
    logger.debug("runScenarioAndCaptureData started for session: ${session.sessionId}")

    val cid = "coro-1"
    val jid = "job-1"
    val parent: String? = null
    val scopeId = "scope-1"
    val label = "root"

    fun nowSeq() = session.nextSeq()
    fun nowTs() = System.nanoTime()

    logger.debug("Sending CoroutineCreated event for coroutineId: $cid")
    session.sent(
        CoroutineCreated(
            sessionId = session.sessionId,
            seq = nowSeq(),
            tsNanos = nowTs(),
            coroutineId = cid,
            jobId = jid,
            parentCoroutineId = parent,
            scopeId = scopeId,
            label = label
        )
    )

    logger.debug("Sending CoroutineStarted event for coroutineId: $cid")
    session.sent(
        CoroutineStarted(
            sessionId = session.sessionId,
            seq = nowSeq(),
            tsNanos = nowTs(),
            coroutineId = cid,
            jobId = jid,
            parentCoroutineId = parent,
            scopeId = scopeId,
            label = label
        )
    )

    logger.debug("Sending CoroutineCompleted event for coroutineId: $cid")
    session.sent(
        CoroutineCompleted(
            sessionId = session.sessionId,
            seq = nowSeq(),
            tsNanos = nowTs(),
            coroutineId = cid,
            jobId = jid,
            parentCoroutineId = parent,
            scopeId = scopeId,
            label = label
        )
    )

    logger.debug("Events sent, waiting for processing...")
    delay(100)

    logger.debug("Capturing snapshot data...")
    val coroutineNodes = session.snapshot.coroutines.values.map { node ->
        logger.trace("Processing coroutine node: id=${node.id}, state=${node.state}")
        CoroutineNodeDto(
            id = node.id,
            jobId = node.jobId,
            parentId = node.parentId,
            scopeId = node.scopeId,
            label = node.label,
            state = node.state.toString()
        )
    }

    val allEvents = session.store.all()
    logger.info("Scenario data capture complete: ${allEvents.size} events, ${coroutineNodes.size} coroutines")

    return ScenarioResultData(
        success = true,
        sessionId = session.sessionId,
        events = allEvents,
        coroutines = coroutineNodes,
        eventCount = allEvents.size
    )
}

