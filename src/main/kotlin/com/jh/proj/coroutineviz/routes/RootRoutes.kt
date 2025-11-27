package com.jh.proj.coroutineviz.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerRootRoutes() {
    get("/") {
        logger.info("Received request to root endpoint")
        call.respondText("Hello World!")
    }

    sse("/hello") {
        logger.info("SSE connection established at /hello")
        send(ServerSentEvent("world"))
    }
}

