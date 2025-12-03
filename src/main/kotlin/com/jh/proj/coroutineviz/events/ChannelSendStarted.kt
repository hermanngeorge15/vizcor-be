package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a send operation starts on a Channel.
 */
@Serializable
data class ChannelSendStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val senderId: String,  // Unique ID for this send operation
    val coroutineId: String,
    val valuePreview: String,
    val valueType: String,
    val bufferSize: Int,  // Current buffer size before send
    val bufferCapacity: Int,
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelSendStarted"
}

