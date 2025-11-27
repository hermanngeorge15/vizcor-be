package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.session.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerSessionRoutes() {
    post("/api/sessions") {
        val name = call.request.queryParameters["name"]
        val session = SessionManager.createSession(name)

        logger.info("Created new session via API: ${session.sessionId}")

        call.respond(
            HttpStatusCode.Created,
            mapOf(
                "sessionId" to session.sessionId,
                "message" to "Session created successfully"
            )
        )
    }

    get("/api/sessions") {
        val sessions = SessionManager.listSessions()
        logger.debug("Listing sessions: ${sessions.size} active")
        call.respond(HttpStatusCode.OK, sessions)
    }

    get("/api/sessions/{id}") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@get
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            return@get
        }

        val snapshot = SessionSnapshotResponse(
            sessionId = session.sessionId,
            coroutineCount = session.snapshot.coroutines.size,
            eventCount = session.store.all().size,
            coroutines = session.snapshot.coroutines.values.map { node ->
                CoroutineNodeDto(
                    id = node.id,
                    jobId = node.jobId,
                    parentId = node.parentId,
                    scopeId = node.scopeId,
                    label = node.label,
                    state = node.state.toString()
                )
            }
        )

        call.respond(HttpStatusCode.OK, snapshot)
    }

    delete("/api/sessions/{id}") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@delete
        }

        val success = SessionManager.closeSession(sessionId)
        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Session closed"))
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
        }
    }

    get("/api/sessions/{id}/events") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@get
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            return@get
        }

        val events = session.store.all()
        call.respond(HttpStatusCode.OK, events)
    }

    get("/api/sessions/{id}/hierarchy") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@get
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            return@get
        }

        val scopeId = call.request.queryParameters["scopeId"]
        val tree = session.projectionService.getHierarchyTree(scopeId)

        call.respond(HttpStatusCode.OK, tree)
    }

    get("/api/sessions/{id}/threads") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@get
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            return@get
        }

        val activity = session.projectionService.getThreadActivity()

        call.respond(HttpStatusCode.OK, activity)
    }

    get("/api/sessions/{id}/coroutines/{coroutineId}/timeline") {
        val sessionId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
            return@get
        }

        val coroutineId = call.parameters["coroutineId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing coroutine ID"))
            return@get
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            return@get
        }

        val timeline = session.projectionService.getCoroutineTimeline(coroutineId)
        if (timeline == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Coroutine not found"))
            return@get
        }

        call.respond(HttpStatusCode.OK, timeline)
    }

    sse("/api/sessions/{id}/stream") {
        val sessionId = call.parameters["id"] ?: run {
            logger.warn("SSE connection attempted without session ID")
            return@sse
        }

        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            logger.warn("SSE connection attempted for non-existent session: $sessionId")
            send(
                ServerSentEvent(
                    data = """{"error": "Session not found"}""",
                    event = "error"
                )
            )
            return@sse
        }

        logger.info("SSE stream started for session: $sessionId")

        try {
            session.bus.stream().collect { event ->
                send(
                    ServerSentEvent(
                        data = Json.encodeToString(event),
                        event = event.kind,
                        id = "${event.sessionId}-${event.seq}"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Error in SSE stream for session $sessionId", e)
        } finally {
            logger.info("SSE stream ended for session: $sessionId")
        }
    }
}

