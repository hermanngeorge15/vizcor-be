package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.events.coroutine.CoroutineCompleted
import com.jh.proj.coroutineviz.events.coroutine.CoroutineCreated
import com.jh.proj.coroutineviz.events.coroutine.CoroutineStarted
import com.jh.proj.coroutineviz.examples.dispatcherExampleScenario
import com.jh.proj.coroutineviz.session.VizSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay

fun Route.registerVizScenarioRoutes() {
    get("/api/viz/run-scenario-with-data") {
        val session = VizSession("api-session-${System.currentTimeMillis()}")
        val cid = "coro-1"; val jid = "job-1"; val scopeId = "scope-1"
        session.sent(CoroutineCreated(session.sessionId, session.nextSeq(), System.nanoTime(), cid, jid, null, scopeId, "root"))
        session.sent(CoroutineStarted(session.sessionId, session.nextSeq(), System.nanoTime(), cid, jid, null, scopeId, "root"))
        session.sent(CoroutineCompleted(session.sessionId, session.nextSeq(), System.nanoTime(), cid, jid, null, scopeId, "root"))
        delay(100)
        val nodes = session.snapshot.coroutines.values.map { CoroutineNodeDto(it.id, it.jobId, it.parentId, it.scopeId, it.label, it.state.toString()) }
        call.respond(HttpStatusCode.OK, ScenarioResultData(true, session.sessionId, session.store.all(), nodes, session.store.all().size))
    }
    get("/api/examples/dispatcher-scenario") {
        dispatcherExampleScenario()
        call.respond(HttpStatusCode.OK, ScenarioResponse(success = true, message = "Done"))
    }
}
