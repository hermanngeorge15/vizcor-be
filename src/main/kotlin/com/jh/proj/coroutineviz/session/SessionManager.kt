package com.jh.proj.coroutineviz.session

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active visualization sessions.
 * 
 * Sessions are stored in memory and can be accessed across multiple API calls.
 * This allows clients to:
 * - Create a session
 * - Run scenarios in that session
 * - Stream events from that session via SSE
 * - Query the session snapshot
 */
object SessionManager {
    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, VizSession>()
    
    /**
     * Create a new visualization session.
     */
    fun createSession(name: String? = null): VizSession {
        val sessionId = name?.let { "$it-${System.currentTimeMillis()}" }
            ?: "session-${System.currentTimeMillis()}"
        
        val session = VizSession(sessionId)
        sessions[sessionId] = session
        
        logger.info("Created session: $sessionId")
        return session
    }
    
    /**
     * Get an existing session by ID.
     */
    fun getSession(sessionId: String): VizSession? {
        return sessions[sessionId]
    }
    
    /**
     * List all active sessions.
     */
    fun listSessions(): List<SessionInfo> {
        return sessions.values.map { session ->
            SessionInfo(
                sessionId = session.sessionId,
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }
    
    /**
     * Close and remove a session.
     */
    fun closeSession(sessionId: String): Boolean {
        val removed = sessions.remove(sessionId)
        if (removed != null) {
            removed.close()  // Clean up session resources
            logger.info("Closed session: $sessionId")
            return true
        }
        return false
    }
    
    /**
     * Clear all sessions (useful for testing).
     */
    fun clearAll() {
        val count = sessions.size
        sessions.clear()
        logger.info("Cleared all sessions: $count removed")
    }
}

@Serializable
data class SessionInfo(
    val sessionId: String,
    val coroutineCount: Int,
    val eventCount: Int
)

