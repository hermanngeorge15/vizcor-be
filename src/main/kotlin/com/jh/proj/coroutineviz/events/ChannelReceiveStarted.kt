package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a receive operation starts on a Channel.
 */
@Serializable
data class ChannelReceiveStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val receiverId: String,  // Unique ID for this receive operation
    val coroutineId: String,
    val bufferSize: Int,  // Current buffer size before receive
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelReceiveStarted"
}

