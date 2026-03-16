package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.module
import com.jh.proj.coroutineviz.session.SessionManager
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionRoutesTest {

    @BeforeEach
    fun setUp() {
        SessionManager.clearAll()
    }

    @AfterEach
    fun tearDown() {
        SessionManager.clearAll()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    @Test
    fun `GET sessions returns empty list initially`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/api/sessions")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(0, body.size, "Sessions list should be empty initially")
    }

    @Test
    fun `POST sessions creates a session with name`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/api/sessions?name=test-session")
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sessionId = body["sessionId"]?.jsonPrimitive?.content
        assertNotNull(sessionId, "Response should contain sessionId")
        assertTrue(sessionId.startsWith("test-session-"), "Session ID should start with provided name")
        assertEquals("Session created successfully", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST sessions creates a session without name`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/api/sessions")
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sessionId = body["sessionId"]?.jsonPrimitive?.content
        assertNotNull(sessionId, "Response should contain sessionId")
        assertTrue(sessionId.startsWith("session-"), "Session ID should start with 'session-'")
    }

    @Test
    fun `GET session by id returns the created session`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Create a session first
        val createResponse = client.post("/api/sessions?name=lookup-test")
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val sessionId = createBody["sessionId"]!!.jsonPrimitive.content

        // Get session by ID
        val getResponse = client.get("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.OK, getResponse.status)

        val snapshot = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals(sessionId, snapshot["sessionId"]?.jsonPrimitive?.content)
        assertEquals(0, snapshot["coroutineCount"]?.jsonPrimitive?.int)
        assertEquals(0, snapshot["eventCount"]?.jsonPrimitive?.int)
    }

    @Test
    fun `GET sessions returns the created session in the list`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Create a session
        val createResponse = client.post("/api/sessions?name=list-test")
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val sessionId = createBody["sessionId"]!!.jsonPrimitive.content

        // List sessions
        val listResponse = client.get("/api/sessions")
        assertEquals(HttpStatusCode.OK, listResponse.status)

        val sessions = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        assertEquals(1, sessions.size, "Should have exactly one session")

        val session = sessions[0].jsonObject
        assertEquals(sessionId, session["sessionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `DELETE session removes the session`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Create a session
        val createResponse = client.post("/api/sessions?name=delete-test")
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val sessionId = createBody["sessionId"]!!.jsonPrimitive.content

        // Delete it
        val deleteResponse = client.delete("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        val deleteBody = Json.parseToJsonElement(deleteResponse.bodyAsText()).jsonObject
        assertEquals("Session closed", deleteBody["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET session returns 404 after deletion`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Create a session
        val createResponse = client.post("/api/sessions?name=deleted-session")
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val sessionId = createBody["sessionId"]!!.jsonPrimitive.content

        // Delete it
        client.delete("/api/sessions/$sessionId")

        // Try to get it
        val getResponse = client.get("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)

        val errorBody = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals("Session not found", errorBody["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `DELETE non-existent session returns 404`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.delete("/api/sessions/non-existent-id")
        assertEquals(HttpStatusCode.NotFound, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Session not found", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `full session CRUD lifecycle`() = testApplication {
        application { module() }
        val client = jsonClient()

        // 1. List sessions - should be empty
        val emptyList = client.get("/api/sessions")
        assertEquals(HttpStatusCode.OK, emptyList.status)
        assertEquals(0, Json.parseToJsonElement(emptyList.bodyAsText()).jsonArray.size)

        // 2. Create a session
        val createResponse = client.post("/api/sessions?name=lifecycle-test")
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val sessionId = Json.parseToJsonElement(createResponse.bodyAsText())
            .jsonObject["sessionId"]!!.jsonPrimitive.content

        // 3. List sessions - should have one
        val oneList = client.get("/api/sessions")
        assertEquals(1, Json.parseToJsonElement(oneList.bodyAsText()).jsonArray.size)

        // 4. Get session by ID
        val getResponse = client.get("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals(
            sessionId,
            Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject["sessionId"]?.jsonPrimitive?.content
        )

        // 5. Delete session
        val deleteResponse = client.delete("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // 6. List sessions - should be empty again
        val emptyAgain = client.get("/api/sessions")
        assertEquals(0, Json.parseToJsonElement(emptyAgain.bodyAsText()).jsonArray.size)

        // 7. Get deleted session - should be 404
        val notFound = client.get("/api/sessions/$sessionId")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }

    @Test
    fun `GET session events returns empty list for new session`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Create a session
        val createResponse = client.post("/api/sessions?name=events-test")
        val sessionId = Json.parseToJsonElement(createResponse.bodyAsText())
            .jsonObject["sessionId"]!!.jsonPrimitive.content

        // Get events
        val eventsResponse = client.get("/api/sessions/$sessionId/events")
        assertEquals(HttpStatusCode.OK, eventsResponse.status)

        val events = Json.parseToJsonElement(eventsResponse.bodyAsText()).jsonArray
        assertEquals(0, events.size, "New session should have no events")
    }

    @Test
    fun `GET session events returns 404 for non-existent session`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/api/sessions/non-existent/events")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET session hierarchy returns 404 for non-existent session`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/api/sessions/non-existent/hierarchy")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET session threads returns 404 for non-existent session`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/api/sessions/non-existent/threads")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
