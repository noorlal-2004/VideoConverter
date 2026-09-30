package com.example.videoconverter.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.example.videoconverter.converter.ConversionErrors
import com.example.videoconverter.converter.ConversionManager
import com.example.videoconverter.converter.VideoConverter
import com.example.videoconverter.model.*
import com.example.videoconverter.util.MediaStoreSaver
import kotlinx.coroutines.*

@OptIn(UnstableApi::class)
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private lateinit var converter: VideoConverter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        converter = VideoConverter(this)
        ConversionNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startConversion(intent)
            ACTION_CANCEL -> job?.cancel()
        }
        return START_NOT_STICKY
    }

    private fun startConversion(intent: Intent) {
        ServiceCompat.startForeground(
            this, ConversionNotifications.ID_PROGRESS,
            ConversionNotifications.progress(this, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (job?.isActive == true) return
        ConversionNotifications.clearResult(this)

        val uri = Uri.parse(intent.getStringExtra(EXTRA_URI))
        val info = VideoInfo(
            width = intent.getIntExtra(EXTRA_WIDTH, 0),
            height = intent.getIntExtra(EXTRA_HEIGHT, 0),
            durationMs = intent.getLongExtra(EXTRA_DURATION, 0),
            sizeBytes = intent.getLongExtra(EXTRA_SIZE, 0)
        )
        val settings = ConversionSettings(
            mode = OutputMode.valueOf(intent.getStringExtra(EXTRA_MODE)!!),
            resolution = Resolution.valueOf(intent.getStringExtra(EXTRA_RESOLUTION)!!),
            quality = Quality.valueOf(intent.getStringExtra(EXTRA_QUALITY)!!),
            codec = Codec.valueOf(intent.getStringExtra(EXTRA_CODEC)!!),
            trimStartMs = intent.getLongExtra(EXTRA_TRIM_START, 0L),
            trimEndMs = intent.getLongExtra(EXTRA_TRIM_END, 0L),
            targetSizeMb = intent.getIntExtra(EXTRA_TARGET_MB, 0).takeIf { it > 0 }
        )
        val audioOnly = settings.mode == OutputMode.AUDIO

        job = scope.launch {
            var lastPercent = -1
            try {
                ConversionManager.set(ConversionStatus.Running(0f))
                val file = converter.convert(uri, info, settings) { p ->
                    ConversionManager.set(ConversionStatus.Running(p))
                    val percent = (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        ConversionNotifications.updateProgress(this@ConversionService, percent)
                    }
                }
                val outputSize = file.length()
                val savedUri = withContext(Dispatchers.IO) {
                    MediaStoreSaver.save(this@ConversionService, file, audioOnly)
                        .also { file.delete() }
                }
                ConversionManager.set(
                    ConversionStatus.Done(savedUri, info.sizeBytes, outputSize, audioOnly)
                )
                ConversionNotifications.showResult(
                    this@ConversionService,
                    "Conversion complete",
                    if (audioOnly) "Saved to Music/VideoConverter" else "Saved to Movies/VideoConverter"
                )
            } catch (e: CancellationException) {
                ConversionManager.reset()
                throw e
            } catch (e: Exception) {
                val message = ConversionErrors.toMessage(e)
                ConversionManager.set(ConversionStatus.Failed(message))
                ConversionNotifications.showResult(this@ConversionService, "Conversion failed", message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.videoconverter.START"
        const val ACTION_CANCEL = "com.example.videoconverter.CANCEL"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_SIZE = "size"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_RESOLUTION = "resolution"
        private const val EXTRA_QUALITY = "quality"
        private const val EXTRA_CODEC = "codec"
        private const val EXTRA_TRIM_START = "trim_start"
        private const val EXTRA_TRIM_END = "trim_end"
        private const val EXTRA_TARGET_MB = "target_mb"

        fun start(context: Context, uri: Uri, info: VideoInfo, settings: ConversionSettings) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_WIDTH, info.width)
                putExtra(EXTRA_HEIGHT, info.height)
                putExtra(EXTRA_DURATION, info.durationMs)
                putExtra(EXTRA_SIZE, info.sizeBytes)
                putExtra(EXTRA_MODE, settings.mode.name)
                putExtra(EXTRA_RESOLUTION, settings.resolution.name)
                putExtra(EXTRA_QUALITY, settings.quality.name)
                putExtra(EXTRA_CODEC, settings.codec.name)
                putExtra(EXTRA_TRIM_START, settings.trimStartMs)
                putExtra(EXTRA_TRIM_END, settings.trimEndMs)
                putExtra(EXTRA_TARGET_MB, settings.targetSizeMb ?: 0)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }}