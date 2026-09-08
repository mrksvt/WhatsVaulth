package com.mrksvt.waen.xposed.features.voice_tts.core

/**
 * Generic synthetic bubble model - not hardcoded for translation only.
 * Every bubble refers ONLY to its originalMessageId; no parent-child chaining.
 */
data class SyntheticBubble(
    val bubbleId: String,
    val originalMessageId: String,
    val type: BubbleType,
    var state: BubbleState,
    var content: String,
    val createdAt: Long
)

enum class BubbleType {
    TRANSLATION,
    TTS
}

enum class BubbleState {
    LOADING,
    READY,
    ERROR
}
