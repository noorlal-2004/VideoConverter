package com.example.videoconverter.viewmodel

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoconverter.converter.ConversionManager
import com.example.videoconverter.converter.ConversionPlan
import com.example.videoconverter.model.*
import com.example.videoconverter.service.ConversionService
import com.example.videoconverter.util.CodecSupport
import com.example.videoconverter.util.StorageChecker
import com.example.videoconverter.util.VideoInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val videoUri: Uri? = null,
    val info: VideoInfo? = null,
    val settings: ConversionSettings = ConversionSettings(),
    val status: ConversionStatus = ConversionStatus.Idle,
    val pickError: String? = null
)

class ConverterViewModel(app: Application) : AndroidViewModel(app) {

    // The `status` field of this flow is unused; the real status comes from ConversionManager.
    private val local = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(local, ConversionManager.status) { l, status ->
        l.copy(status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val h265Supported: Boolean = CodecSupport.hasEncoder(Codec.H265.mime)

    init {
        if (!h265Supported) {
            local.update { it.copy(settings = it.settings.copy(codec = Codec.H264)) }
        }
    }

    fun onVideoPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { VideoInfoReader.read(getApplication(), uri) }
            }
            val info = result.getOrNull()
            if (info == null || info.width <= 0 || info.height <= 0 || info.durationMs <= 0) {
                local.update {
                    it.copy(
                        videoUri = null, info = null,
                        pickError = "This file couldn't be read as a video. Try another one."
                    )
                }
            } else {
                ConversionManager.reset()
                local.update {
                    it.copy(
                        videoUri = uri,
                        info = info,
                        pickError = null,
                        // A new video starts with no trim
                        settings = it.settings.copy(trimStartMs = 0L, trimEndMs = info.durationMs)
                    )
                }
            }
        }
    }

    fun updateSettings(change: (ConversionSettings) -> ConversionSettings) {
        local.update { it.copy(settings = change(it.settings)) }
    }

    fun updateTrim(startMs: Long, endMs: Long) {
        if (endMs - startMs < MIN_TRIM_MS) return
        local.update {
            it.copy(settings = it.settings.copy(trimStartMs = startMs, trimEndMs = endMs))
        }
    }

    fun convert() {
        val s = state.value
        val uri = s.videoUri ?: return
        val info = s.info ?: return
        val app = getApplication<Application>()
        val audioOnly = s.settings.mode == OutputMode.AUDIO

        if (audioOnly && !info.hasAudio) {
            ConversionManager.set(ConversionStatus.Failed("This video has no audio track to extract."))
            return
        }
        if (!audioOnly && s.settings.codec == Codec.H265 && !h265Supported) {
            ConversionManager.set(ConversionStatus.Failed("This phone can't encode H.265. Choose H.264."))
            return
        }

        // Free-space check: cache file + final copy, plus a safety margin
        val plan = ConversionPlan.create(info, s.settings)
        val needed = plan.estimatedBytes() * 2 + 50L * 1024 * 1024
        val available = StorageChecker.availableBytes(app)
        if (available < needed) {
            ConversionManager.set(
                ConversionStatus.Failed(
                    "Not enough storage. About ${Formatter.formatShortFileSize(app, needed)} " +
                            "needed, ${Formatter.formatShortFileSize(app, available)} free."
                )
            )
            return
        }

        ConversionManager.set(ConversionStatus.Running(0f))
        ConversionService.start(app, uri, info, s.settings)
    }

    fun cancel() = ConversionService.cancel(getApplication())

    private companion object {
        const val MIN_TRIM_MS = 1_000L
    }
}