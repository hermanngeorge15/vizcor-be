package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class SuspensionPoint(
    val function: String,           // Method name
    val fileName: String? = null,   // Source file
    val lineNumber: Int? = null,    // Line number
    val reason: String              // "delay", "withContext", "join", "await"
) {
    companion object {
        // Helper to capture from stack trace
        fun capture(reason: String, skipFrames: Int = 2): SuspensionPoint {
            val stackTrace = Throwable().stackTrace

            // Find first non-coroutines-infrastructure frame
            val relevantFrame = stackTrace
                .drop(skipFrames)
                .firstOrNull { frame ->
                    !frame.className.startsWith("kotlinx.coroutines") &&
                            !frame.className.contains("VizScope")
                }

            return SuspensionPoint(
                function = relevantFrame?.methodName ?: "unknown",
                fileName = relevantFrame?.fileName,
                lineNumber = relevantFrame?.lineNumber?.takeIf { it >= 0 },
                reason = reason
            )
        }
    }
}