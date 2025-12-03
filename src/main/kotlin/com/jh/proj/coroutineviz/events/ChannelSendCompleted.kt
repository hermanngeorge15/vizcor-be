package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a send operation completes on a Channel.
 */
@Serializable
data class ChannelSendCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val senderId: String,
    val coroutineId: String,
    val valuePreview: String,
    val durationNanos: Long,  // Time from SendStarted to SendCompleted
    val wasSuspended: Boolean,  // Whether the send had to suspend
    val bufferSize: Int,  // Current buffer size after send
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelSendCompleted"
}

