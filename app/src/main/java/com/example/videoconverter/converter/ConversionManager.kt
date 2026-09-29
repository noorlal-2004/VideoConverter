package com.example.videoconverter.converter

import com.example.videoconverter.model.ConversionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide conversion state, shared by the service and the UI. */
object ConversionManager {
    private val _status = MutableStateFlow<ConversionStatus>(ConversionStatus.Idle)
    val status: StateFlow<ConversionStatus> = _status.asStateFlow()

    fun set(status: ConversionStatus) {
        _status.value = status
    }

    fun reset() = set(ConversionStatus.Idle)
}