package com.example.videoconverter.converter


import com.example.videoconverter.model.ConversionSettings
import com.example.videoconverter.model.VideoInfo

data class ConversionPlan(val outWidth: Int, val outHeight: Int, val videoBitrate: Int) {

    fun estimatedBytes(durationMs: Long): Long =
        (videoBitrate + AUDIO_BITRATE) * durationMs / 8000

    companion object {
        private const val AUDIO_BITRATE = 128_000L

        fun create(info: VideoInfo, settings: ConversionSettings): ConversionPlan {
            val outHeight = minOf(settings.resolution.height ?: info.height, info.height)
            val outWidth = info.width * outHeight / info.height
            val bitrate = (outWidth * outHeight * 30 * settings.quality.bitsPerPixel)
                .toInt().coerceAtLeast(500_000)
            return ConversionPlan(outWidth, outHeight, bitrate)
        }
    }
}