package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

/**
 * Summary information about a visualization session.
 *
 * Used by [SessionManager] to provide a lightweight overview of sessions
 * without exposing full session state.
 *
 * @property sessionId Unique session identifier
 * @property coroutineCount Number of coroutines tracked in this session
 * @property eventCount Total number of events recorded
 */
@Serializable
data class SessionInfo(
    val sessionId: String,
    val coroutineCount: Int,
    val eventCount: Int
)
