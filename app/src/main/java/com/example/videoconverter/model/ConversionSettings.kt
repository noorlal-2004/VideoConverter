package com.example.videoconverter.model

import androidx.media3.common.MimeTypes

enum class Resolution(val label: String, val height: Int?) {
    ORIGINAL("Original", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480)
}

enum class Quality(val label: String, val bitsPerPixel: Double) {
    HIGH("High", 0.10),
    MEDIUM("Medium", 0.06),
    LOW("Low", 0.035)
}

enum class Codec(val label: String, val mime: String) {
    H264("H.264", MimeTypes.VIDEO_H264),
    H265("H.265", MimeTypes.VIDEO_H265)
}

data class ConversionSettings(
    val resolution: Resolution = Resolution.P720,
    val quality: Quality = Quality.MEDIUM,
    val codec: Codec = Codec.H264
)