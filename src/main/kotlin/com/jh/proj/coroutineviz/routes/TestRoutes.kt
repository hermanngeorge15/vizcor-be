package com.jh.proj.coroutineviz.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.registerTestRoutes() {
    get("/api/tests/run-all") {
        call.respond(HttpStatusCode.OK, ScenarioResponse(success = true, message = "Migrated to /api/scenarios"))
    }
}
