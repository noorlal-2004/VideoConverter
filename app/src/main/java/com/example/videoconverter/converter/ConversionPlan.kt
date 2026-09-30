package com.example.videoconverter.converter

import com.example.videoconverter.model.ConversionSettings
import com.example.videoconverter.model.OutputMode
import com.example.videoconverter.model.VideoInfo

data class ConversionPlan(
    val outWidth: Int,
    val outHeight: Int,
    val videoBitrate: Int,
    val durationMs: Long,
    val audioOnly: Boolean,
    val lowQualityWarning: Boolean
) {
    fun estimatedBytes(): Long {
        val bitrate = if (audioOnly) AUDIO_BITRATE else videoBitrate + AUDIO_BITRATE
        return bitrate * durationMs / 8000
    }

    companion object {
        private const val AUDIO_BITRATE = 128_000L
        private const val MIN_BITRATE = 100_000L
        private const val WARN_BITRATE = 300_000L

        fun create(info: VideoInfo, settings: ConversionSettings): ConversionPlan {
            val durationMs = settings.trimmedDurationMs(info)
            val outHeight = minOf(settings.resolution.height ?: info.height, info.height)
            val outWidth = info.width * outHeight / info.height

            val qualityBitrate = (outWidth * outHeight * 30 * settings.quality.bitsPerPixel)
                .toLong().coerceAtLeast(500_000L)

            var bitrate = qualityBitrate
            var warning = false
            settings.targetSizeMb?.let { mb ->
                // 5% margin for container overhead
                val totalBits = mb * 1024.0 * 1024.0 * 8 * 0.95
                val targetBitrate = (totalBits / (durationMs / 1000.0) - AUDIO_BITRATE).toLong()
                warning = targetBitrate < WARN_BITRATE
                bitrate = minOf(qualityBitrate, targetBitrate).coerceAtLeast(MIN_BITRATE)
            }

            return ConversionPlan(
                outWidth = outWidth,
                outHeight = outHeight,
                videoBitrate = bitrate.toInt(),
                durationMs = durationMs,
                audioOnly = settings.mode == OutputMode.AUDIO,
                lowQualityWarning = warning
            )
        }
    }
}