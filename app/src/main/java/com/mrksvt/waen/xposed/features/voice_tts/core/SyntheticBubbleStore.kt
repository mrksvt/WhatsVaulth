package com.mrksvt.waen.xposed.features.voice_tts.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Holds synthetic bubbles per original message id and produces the flattened
 * adapter index (original messages + synthetic bubbles).
 *
 * Invariants (unit-tested in SyntheticBubbleStoreTest):
 *  - bubbles for one message live in a MutableList, newest always appended at
 *    the end; never inserted in the middle, never reordered by type
 *  - deleting a bubble in the middle shifts the following ones up automatically
 *    because the flattened list is regenerated from scratch
 *  - no bubble references another bubble; each only knows its originalMessageId
 *
 * Thread-safety: all public methods synchronize on this instance. Callers that
 * mutate from a background thread must refresh the adapter on the main thread.
 */
class SyntheticBubbleStore {

    private val bubblesByMessage = LinkedHashMap<String, MutableList<SyntheticBubble>>()
    private val bubbleSeq = AtomicLong(0)

    fun addBubble(
        originalMessageId: String,
        type: BubbleType,
        state: BubbleState,
        content: String = ""
    ): SyntheticBubble {
        val bubble = SyntheticBubble(
            bubbleId = "${type.name}-${originalMessageId}-${bubbleSeq.incrementAndGet()}",
            originalMessageId = originalMessageId,
            type = type,
            state = state,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        synchronized(this) {
            bubblesByMessage.getOrPut(originalMessageId) { ArrayList(1) }.add(bubble)
        }
        return bubble
    }

    /**
     * Update state/content of an existing bubble (matched by bubbleId).
     * Returns false when the bubble is gone (e.g. original message revoked,
     * or conversation cleared before the IPC callback landed).
     */
    fun updateBubble(bubbleId: String, state: BubbleState, content: String): Boolean {
        synchronized(this) {
            for (list in bubblesByMessage.values) {
                val bubble = list.firstOrNull { it.bubbleId == bubbleId }
                if (bubble != null) {
                    bubble.state = state
                    bubble.content = content
                    return true
                }
            }
        }
        return false
    }

    fun removeBubble(bubbleId: String): Boolean {
        synchronized(this) {
            for ((messageId, list) in bubblesByMessage) {
                if (list.removeAll { it.bubbleId == bubbleId }) {
                    if (list.isEmpty()) bubblesByMessage.remove(messageId)
                    return true
                }
            }
        }
        return false
    }

    fun removeBubblesForMessage(originalMessageId: String) {
        synchronized(this) {
            bubblesByMessage.remove(originalMessageId)
        }
    }

    fun bubblesFor(originalMessageId: String): List<SyntheticBubble> {
        synchronized(this) {
            return bubblesByMessage[originalMessageId]?.toList() ?: emptyList()
        }
    }

    fun hasBubbleFor(originalMessageId: String, type: BubbleType): Boolean {
        synchronized(this) {
            return bubblesByMessage[originalMessageId]?.any { it.type == type } == true
        }
    }

    fun snapshot(): List<SyntheticBubble> {
        synchronized(this) {
            return bubblesByMessage.values.flatten()
        }
    }

    fun clear() {
        synchronized(this) {
            bubblesByMessage.clear()
        }
    }

    /**
     * Regenerate the flattened index bound to the WhatsApp list adapter.
     *
     * [originalMessageIds] must be ordered exactly like the underlying real
     * adapter (oldest -> newest). For every original message the result keeps
     * the message itself first, then its synthetic bubbles in insertion order.
     * Bubbles whose original message disappeared from the list are dropped
     * from the output (and pruned from the store) so a revoked message never
     * leaves an orphan bubble behind.
     */
    fun buildFlattenedIndex(originalMessageIds: List<String>): List<FlattenedEntry> {
        val snapshot: Map<String, List<SyntheticBubble>>
        synchronized(this) {
            val known = bubblesByMessage.keys.toSet()
            val alive = originalMessageIds.toHashSet()
            (known - alive).forEach { bubblesByMessage.remove(it) }
            snapshot = bubblesByMessage.mapValues { it.value.toList() }
        }
        val result = ArrayList<FlattenedEntry>(originalMessageIds.size + snapshot.size)
        for (messageId in originalMessageIds) {
            result.add(FlattenedEntry.Original(messageId))
            snapshot[messageId]?.forEach { bubble ->
                result.add(FlattenedEntry.Synthetic(messageId, bubble))
            }
        }
        return result
    }

    sealed class FlattenedEntry {
        data class Original(val messageId: String) : FlattenedEntry()
        data class Synthetic(val originalMessageId: String, val bubble: SyntheticBubble) :
            FlattenedEntry()
    }
}
