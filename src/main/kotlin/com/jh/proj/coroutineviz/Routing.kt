package com.jh.proj.coroutineviz

import com.jh.proj.coroutineviz.routes.registerRootRoutes
import com.jh.proj.coroutineviz.routes.registerScenarioRunnerRoutes
import com.jh.proj.coroutineviz.routes.registerSessionRoutes
import com.jh.proj.coroutineviz.routes.registerSyncScenarioRoutes
import com.jh.proj.coroutineviz.routes.registerTestRoutes
import com.jh.proj.coroutineviz.routes.registerVizScenarioRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*

fun Application.configureRouting() {
    install(SSE)
    routing {
        registerRootRoutes()
        registerVizScenarioRoutes()
        registerSyncScenarioRoutes()
        registerTestRoutes()
        registerSessionRoutes()
        registerScenarioRunnerRoutes()
    }
}
