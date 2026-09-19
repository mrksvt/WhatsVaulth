package com.mrksvt.waen.xposed.features.voice_tts.core

object VoiceExpression {

    const val NORMAL = "normal"
    const val BAHAGIA = "bahagia"
    const val SEDIH = "sedih"
    const val SEMANGAT = "semangat"
    const val AUTO = "auto"

    val ALL = listOf(NORMAL, BAHAGIA, SEDIH, SEMANGAT)

    fun clampPitch(v: Float) = v.coerceIn(0.6f, 1.6f)
    fun clampRate(v: Float) = v.coerceIn(0.5f, 1.5f)

    // Sidik jari identitas sama untuk semua emosi; emosi hanya menggeser
    // prosodi (pitch/rate) di sekitar baseline hasil pengukuran f0/voiced.
    fun derive(basePitch: Float, baseRate: Float): Map<String, Pair<Float, Float>> = mapOf(
        NORMAL to (clampPitch(basePitch) to clampRate(baseRate)),
        BAHAGIA to (clampPitch(basePitch * 1.08f) to clampRate(baseRate * 1.06f)),
        SEDIH to (clampPitch(basePitch * 0.90f) to clampRate(baseRate * 0.85f)),
        SEMANGAT to (clampPitch(basePitch * 1.04f) to clampRate(baseRate * 1.15f)),
    )

    private val SEDIH_EMOJIS = listOf(
        "\uD83D\uDE22", // 😢
        "\uD83D\uDE2D", // 😭
        "\uD83D\uDE1E", // 😞
        "\uD83D\uDE14", // 😔
        "\uD83D\uDE29", // 😩
        "\uD83D\uDE41", // 🙁
        "\uD83D\uDC94", // 💔
        "\uD83E\uDD7A", // 🥺
        "\u2639",       // ☹
        "\uD83D\uDE3F", // 😿
        "\uD83D\uDE25"  // 😥
    )

    private val SEMANGAT_EMOJIS = listOf(
        "\uD83D\uDD25", // 🔥
        "\uD83D\uDCAA", // 💪
        "\uD83D\uDE80", // 🚀
        "\u26A1",       // ⚡
        "\uD83D\uDE0E", // 😎
        "\uD83C\uDFC6", // 🏆
        "\uD83D\uDC4F"  // 👏
    )

    private val BAHAGIA_EMOJIS = listOf(
        "\uD83D\uDE04", // 😄
        "\uD83D\uDE0A", // 😊
        "\uD83D\uDE00", // 😀
        "\uD83D\uDE01", // 😁
        "\uD83D\uDE06", // 😆
        "\uD83E\uDD23", // 🤣
        "\uD83D\uDE02", // 😂
        "\uD83E\uDD70", // 🥰
        "\uD83D\uDE0D", // 😍
        "\u2764",       // ❤
        "\uD83D\uDC4D", // 👍
        "\uD83C\uDF89"  // 🎉
    )

    fun detect(text: String?): String {
        if (text.isNullOrEmpty()) return NORMAL
        // prioritas: cue terkuat -> paling spesifik dulu
        if (SEDIH_EMOJIS.any { text.contains(it) }) return SEDIH
        if (SEMANGAT_EMOJIS.any { text.contains(it) }) return SEMANGAT
        if (BAHAGIA_EMOJIS.any { text.contains(it) }) return BAHAGIA
        return NORMAL
    }
}
