package com.example.videoconverter.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.videoconverter.converter.VideoConverter
import com.example.videoconverter.model.ConversionSettings
import com.example.videoconverter.model.VideoInfo
import com.example.videoconverter.util.MediaStoreSaver
import com.example.videoconverter.util.VideoInfoReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data class Running(val progress: Float) : ConversionStatus
    data class Done(val outputUri: Uri, val inputSize: Long, val outputSize: Long) : ConversionStatus
    data class Failed(val message: String) : ConversionStatus
}

data class UiState(
    val videoUri: Uri? = null,
    val info: VideoInfo? = null,
    val settings: ConversionSettings = ConversionSettings(),
    val status: ConversionStatus = ConversionStatus.Idle
)

@UnstableApi
class ConverterViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val converter = VideoConverter(app)
    private var job: Job? = null

    fun onVideoPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { VideoInfoReader.read(getApplication(), uri) }
            _state.update { UiState(videoUri = uri, info = info, settings = it.settings) }
        }
    }

    fun updateSettings(change: (ConversionSettings) -> ConversionSettings) {
        _state.update { it.copy(settings = change(it.settings)) }
    }

    fun convert() {
        val s = _state.value
        val uri = s.videoUri ?: return
        val info = s.info ?: return

        job = viewModelScope.launch {
            _state.update { it.copy(status = ConversionStatus.Running(0f)) }
            try {
                val file = converter.convert(uri, info, s.settings) { p ->
                    _state.update { it.copy(status = ConversionStatus.Running(p)) }
                }
                val outputSize = file.length()
                val savedUri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.save(getApplication(), file).also { file.delete() }
                }
                _state.update {
                    it.copy(status = ConversionStatus.Done(savedUri, info.sizeBytes, outputSize))
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(status = ConversionStatus.Idle) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(status = ConversionStatus.Failed(e.message ?: "Conversion failed"))
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}