package com.example.videoconverter.model

enum class Preset(
    val label: String,
    val description: String,
    val resolution: Resolution,
    val quality: Quality,
    val codec: Codec,
    val targetSizeMb: Int? = null
) {
    HIGH_QUALITY("High quality", "Best picture, bigger file", Resolution.ORIGINAL, Quality.HIGH, Codec.H264),
    BALANCED("Balanced", "Great for most videos", Resolution.P720, Quality.MEDIUM, Codec.H264),
    SMALL("Small size", "Saves the most space", Resolution.P480, Quality.LOW, Codec.H264),
    UNDER_25("Under 25 MB", "Easy to email or share", Resolution.P720, Quality.MEDIUM, Codec.H264, targetSizeMb = 25);

    fun applyTo(s: ConversionSettings): ConversionSettings = s.copy(
        resolution = resolution,
        quality = quality,
        codec = codec,
        targetSizeMb = targetSizeMb
    )

    fun matches(s: ConversionSettings): Boolean =
        s.resolution == resolution && s.quality == quality &&
                s.codec == codec && s.targetSizeMb == targetSizeMb
}