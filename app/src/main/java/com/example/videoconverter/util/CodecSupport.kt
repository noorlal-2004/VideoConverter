package com.example.videoconverter.util

import android.media.MediaCodecList

object CodecSupport {
    fun hasEncoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
}