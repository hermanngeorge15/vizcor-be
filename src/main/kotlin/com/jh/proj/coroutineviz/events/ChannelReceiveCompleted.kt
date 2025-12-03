package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a receive operation completes on a Channel.
 */
@Serializable
data class ChannelReceiveCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val receiverId: String,
    val coroutineId: String,
    val valuePreview: String,
    val valueType: String,
    val durationNanos: Long,  // Time from ReceiveStarted to ReceiveCompleted
    val wasSuspended: Boolean,  // Whether the receive had to suspend
    val bufferSize: Int,  // Current buffer size after receive
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelReceiveCompleted"
}

