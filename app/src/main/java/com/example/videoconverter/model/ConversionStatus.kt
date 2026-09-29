package com.example.videoconverter.model
import android.net.Uri

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data class Running(val progress: Float) : ConversionStatus
    data class Done(val outputUri: Uri, val inputSize: Long, val outputSize: Long) : ConversionStatus
    data class Failed(val message: String) : ConversionStatus
}