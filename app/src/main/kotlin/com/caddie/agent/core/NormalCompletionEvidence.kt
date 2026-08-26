package com.caddie.agent.core

/** Requires a successful fresh observation after the most recent local Android mutation. */
object NormalCompletionEvidence {
    fun isReady(snapshot: RunSnapshot): Boolean {
        val calls = snapshot.messages.mapNotNull { it.toolCall }
        val lastCallId = calls.lastOrNull()?.id
        if (lastCallId != null && lastCallId in snapshot.failedToolCallIds) return false
        val lastMutation = calls.indexOfLast {
            it.name.startsWith("android.") && it.name != "android.observe"
        }
        if (lastMutation < 0) return true

        val observation = calls.drop(lastMutation + 1).lastOrNull()
            ?.takeIf { it.name == "android.observe" }
            ?: return false
        return observation.id !in snapshot.failedToolCallIds
    }
}
