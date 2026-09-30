package com.example.videoconverter.viewmodel

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoconverter.converter.ConversionPlan
import com.example.videoconverter.converter.QueueManager
import com.example.videoconverter.model.*
import com.example.videoconverter.service.ConversionService
import com.example.videoconverter.util.CodecSupport
import com.example.videoconverter.util.StorageChecker
import com.example.videoconverter.util.VideoInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedVideo(val uri: Uri, val name: String, val info: VideoInfo)

data class UiState(
    val picked: List<PickedVideo> = emptyList(),
    val settings: ConversionSettings = ConversionSettings(),
    val queue: List<QueueItem> = emptyList(),
    val message: String? = null
)

class ConverterViewModel(app: Application) : AndroidViewModel(app) {

    // The `queue` field of this flow is unused; the real queue comes from QueueManager.
    private val local = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(local, QueueManager.items) { l, queue ->
        l.copy(queue = queue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val h265Supported: Boolean = CodecSupport.hasEncoder(Codec.H265.mime)
    // One-time messages for the UI (snackbar)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun dismissMessage() {
        local.update { it.copy(message = null) }
    }

    fun clearSelection() {
        local.update { it.copy(picked = emptyList(), message = null) }
    }
    init {
        if (!h265Supported) {
            local.update { it.copy(settings = it.settings.copy(codec = Codec.H264)) }
        }
    }

    fun onVideosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val info = runCatching { VideoInfoReader.read(app, uri) }.getOrNull()
                    if (info == null || info.width <= 0 || info.height <= 0 || info.durationMs <= 0) {
                        null
                    } else {
                        PickedVideo(uri, VideoInfoReader.displayName(app, uri), info)
                    }
                }
            }
            val skipped = uris.size - loaded.size

            local.update { s ->
                if (loaded.isEmpty()) {
                    s.copy(message = "These files couldn't be read as videos. Try others.")
                } else {
                    // Trim only makes sense for a single video; batches use the full length
                    val trimEnd = if (loaded.size == 1) loaded[0].info.durationMs else 0L
                    s.copy(
                        picked = loaded,
                        settings = s.settings.copy(trimStartMs = 0L, trimEndMs = trimEnd),
                        message = if (skipped > 0) {
                            "$skipped file(s) couldn't be read and were skipped."
                        } else null
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
        val s = local.value
        val app = getApplication<Application>()
        if (s.picked.isEmpty()) return

        val settings = s.settings
        val audioOnly = settings.mode == OutputMode.AUDIO

        if (!audioOnly && settings.codec == Codec.H265 && !h265Supported) {
            setMessage("This phone can't encode H.265. Choose H.264.")
            return
        }

        val usable = if (audioOnly) s.picked.filter { it.info.hasAudio } else s.picked
        val noAudio = s.picked.size - usable.size
        if (usable.isEmpty()) {
            setMessage(
                if (s.picked.size == 1) "This video has no audio track to extract."
                else "None of the selected videos has an audio track."
            )
            return
        }

        // Storage: all final outputs plus the largest temporary file, plus a margin
        val estimates = usable.map { ConversionPlan.create(it.info, settings).estimatedBytes() }
        val needed = estimates.sum() + (estimates.maxOrNull() ?: 0L) + 50L * 1024 * 1024
        val available = StorageChecker.availableBytes(app)
        if (available < needed) {
            setMessage(
                "Not enough storage. About ${Formatter.formatShortFileSize(app, needed)} " +
                        "needed, ${Formatter.formatShortFileSize(app, available)} free."
            )
            return
        }

        QueueManager.add(
            usable.map {
                QueueItem(
                    id = QueueManager.newId(),
                    uri = it.uri,
                    name = it.name,
                    info = it.info,
                    settings = settings
                )
            }
        )
        local.update {
            it.copy(
                picked = emptyList(),
                message = if (noAudio > 0) "$noAudio video(s) skipped: no audio track." else null
            )
        }
        ConversionService.start(app)
        _events.tryEmit(
            if (usable.size == 1) "Added to queue" else "Added ${usable.size} videos to queue"
        )
    }

    fun cancelAll() = ConversionService.cancelAll(getApplication())
    fun cancelCurrent() = ConversionService.cancelCurrent(getApplication())
    fun removeItem(id: Long) = QueueManager.remove(id)
    fun clearFinished() = QueueManager.clearFinished()

    private fun setMessage(text: String) {
        local.update { it.copy(message = text) }
    }

    private companion object {
        const val MIN_TRIM_MS = 1_000L
    }
}