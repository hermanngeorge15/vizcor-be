package com.jh.proj.coroutineviz

import com.asyncapi.kotlinasyncapi.context.service.AsyncApiExtension
import com.asyncapi.kotlinasyncapi.ktor.AsyncApiPlugin
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.micrometer.prometheus.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("HTTP")

fun Application.configureHTTP() {
    // CORS Configuration for frontend access
    install(CORS) {
        // Allow requests from frontend dev server
        allowHost("localhost:3000")
        allowHost("127.0.0.1:3000")
        
        // Allow production origins (add your production domains here)
        // allowHost("yourdomain.com", schemes = listOf("https"))
        
        // Allow all HTTP methods
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        
        // Allow common headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)
        
        // Required for SSE (Server-Sent Events)
        allowHeader(HttpHeaders.CacheControl)
        allowHeader(HttpHeaders.Connection)
        
        // Allow credentials (cookies, authorization headers)
        allowCredentials = true
        
        // Set max age for preflight requests cache
        maxAgeInSeconds = 3600
        
        logger.info("CORS configured: allowing localhost:3000 and 127.0.0.1:3000")
    }
    
    install(AsyncApiPlugin) {
        extension = AsyncApiExtension.builder {
            info {
                title("Coroutine Visualizer API")
                version("1.0.0")
            }
        }
    }
    routing {
        swaggerUI(path = "openapi")
    }
    routing {
        openAPI(path = "openapi")
    }
}
