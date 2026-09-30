package com.example.videoconverter.model

import android.net.Uri

/** One video in the queue. `status` Idle means "waiting". */
data class QueueItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val info: VideoInfo,
    val settings: ConversionSettings,
    val status: ConversionStatus = ConversionStatus.Idle
)