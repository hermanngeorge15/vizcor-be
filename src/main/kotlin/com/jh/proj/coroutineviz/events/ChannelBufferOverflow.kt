package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a Channel buffer overflow occurs (for DROP_OLDEST or DROP_LATEST strategies).
 */
@Serializable
data class ChannelBufferOverflow(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val coroutineId: String?,
    val droppedValuePreview: String?,
    val droppedValueType: String?,
    val strategy: String,  // "DROP_OLDEST" or "DROP_LATEST"
    val bufferSize: Int,
    val bufferCapacity: Int,
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelBufferOverflow"
}

